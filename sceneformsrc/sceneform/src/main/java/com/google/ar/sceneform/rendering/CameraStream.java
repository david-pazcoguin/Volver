package com.google.ar.sceneform.rendering;

import androidx.annotation.Nullable;
import android.util.Log;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.IndexBuffer.Builder.IndexType;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.VertexBuffer.Builder;
import com.google.android.filament.VertexBuffer.VertexAttribute;
import com.google.ar.core.Frame;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Displays the Camera stream using Filament.
 *
 * @hide Note: The class is hidden because it should only be used by the Filament Renderer and does
 *     not expose a user facing API.
 */
@SuppressWarnings("AndroidApiChecker") // CompletableFuture
public class CameraStream {
  public static final String MATERIAL_CAMERA_TEXTURE = "cameraTexture";

  private static final String TAG = CameraStream.class.getSimpleName();
  private static final short[] CAMERA_INDICES = new short[] {0, 1, 2};
  private static final int VERTEX_COUNT = 3;
  private static final int POSITION_BUFFER_INDEX = 0;
  private static final int UV_BUFFER_INDEX = 1;
  private static final int FLOAT_SIZE_IN_BYTES = Float.SIZE / 8;

  private static final float[] CAMERA_VERTICES =
      new float[] {-1.0f, 1.0f, 1.0f, -1.0f, -3.0f, 1.0f, 3.0f, 1.0f, 1.0f};
  private static final float[] CAMERA_UVS = new float[] {0.0f, 0.0f, 0.0f, 2.0f, 2.0f, 0.0f};

  private static final int UNINITIALIZED_FILAMENT_RENDERABLE = -1;

  private final Scene scene;
  private int cameraTextureId;

  private int cameraStreamRenderable = UNINITIALIZED_FILAMENT_RENDERABLE;

  private final IndexBuffer cameraIndexBuffer;
  private final VertexBuffer cameraVertexBuffer;
  private final FloatBuffer cameraUvCoords;
  private final FloatBuffer transformedCameraUvCoords;

  @Nullable private ExternalTexture cameraTexture;

  @Nullable private Material defaultCameraMaterial = null;
  @Nullable private Material cameraMaterial = null;

  private int renderablePriority = Renderable.RENDER_PRIORITY_FIRST;

  private boolean isTextureInitialized = false;
  private boolean materialLoadFailed = false;

  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored", "initialization"})
  public CameraStream(int cameraTextureId, Renderer renderer) {
    scene = renderer.getFilamentScene();
    this.cameraTextureId = cameraTextureId;

    IEngine engine = EngineInstance.getEngine();

    // If cameraTextureId == 0, defer ExternalTexture creation to
    // reinitializeTexture() which runs on the first frame when the
    // correct EGL context is guaranteed to be active.
    if (cameraTextureId != 0) {
      cameraTexture = new ExternalTexture(cameraTextureId);
      isTextureInitialized = true;
      Log.e(TAG, "CameraStream: created ExternalTexture for texId=" + cameraTextureId);
    } else {
      isTextureInitialized = false;
      Log.e(TAG, "CameraStream: deferred ExternalTexture creation (texId=0)");
    }

    // create screen quad geometry to camera stream to
    ShortBuffer indexBufferData = ShortBuffer.allocate(CAMERA_INDICES.length);
    indexBufferData.put(CAMERA_INDICES);
    final int indexCount = indexBufferData.capacity();
    cameraIndexBuffer =
        new IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexType.USHORT)
            .build(engine.getFilamentEngine());
    indexBufferData.rewind();
    Preconditions.checkNotNull(cameraIndexBuffer)
        .setBuffer(engine.getFilamentEngine(), indexBufferData);

    // Note: ARCore expects the UV buffers to be direct or will assert in transformDisplayUvCoords.
    cameraUvCoords = createCameraUVBuffer();
    transformedCameraUvCoords = createCameraUVBuffer();

    FloatBuffer vertexBufferData = FloatBuffer.allocate(CAMERA_VERTICES.length);
    vertexBufferData.put(CAMERA_VERTICES);

    cameraVertexBuffer =
        new Builder()
            .vertexCount(VERTEX_COUNT)
            .bufferCount(2)
            .attribute(
                VertexAttribute.POSITION,
                0,
                VertexBuffer.AttributeType.FLOAT3,
                0,
                (CAMERA_VERTICES.length / VERTEX_COUNT) * FLOAT_SIZE_IN_BYTES)
            .attribute(
                VertexAttribute.UV0,
                1,
                VertexBuffer.AttributeType.FLOAT2,
                0,
                (CAMERA_UVS.length / VERTEX_COUNT) * FLOAT_SIZE_IN_BYTES)
            .build(engine.getFilamentEngine());

    vertexBufferData.rewind();
    Preconditions.checkNotNull(cameraVertexBuffer)
        .setBufferAt(engine.getFilamentEngine(), POSITION_BUFFER_INDEX, vertexBufferData);

    adjustCameraUvsForOpenGL();
    cameraVertexBuffer.setBufferAt(
        engine.getFilamentEngine(), UV_BUFFER_INDEX, transformedCameraUvCoords);

    CompletableFuture<Material> materialFuture =
        Material.builder()
            .setSource(
                renderer.getContext(),
                RenderingResources.GetSceneformResource(
                    renderer.getContext(), RenderingResources.Resource.CAMERA_MATERIAL))
            .build();

    materialFuture
        .thenAccept(
            material -> {
              defaultCameraMaterial = material;
              Log.e(TAG, "Camera material loaded successfully");

              // Only set the camera material if it hasn't already been set to a custom material.
              if (cameraMaterial == null) {
                setCameraMaterial(defaultCameraMaterial);
              }
            })
        .exceptionally(
            throwable -> {
              materialLoadFailed = true;
              Log.e(TAG, "CRITICAL: Unable to load camera stream materials. "
                  + "Camera feed will NOT render (black screen). "
                  + "Likely cause: sceneform_camera_material.matc is incompatible "
                  + "with this Filament version. Recompile with matching matc tool.",
                  throwable);
              return null;
            });
  }

  public boolean isTextureInitialized() {
    return isTextureInitialized;
  }

  public boolean isMaterialLoaded() {
    return defaultCameraMaterial != null && !materialLoadFailed;
  }

  public boolean isMaterialLoadFailed() {
    return materialLoadFailed;
  }

  public boolean isRenderableInitialized() {
    return cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE;
  }

  /** Returns true if the camera stream pipeline is fully set up and should produce visible output. */
  public boolean isHealthy() {
    return isTextureInitialized && !materialLoadFailed
        && cameraMaterial != null
        && cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE;
  }

  public void initializeTexture(Frame frame) {
    if (isTextureInitialized()) {
      return;
    }
    // If texture not yet created (deferred mode), create it now.
    if (cameraTextureId != 0) {
      cameraTexture = new ExternalTexture(cameraTextureId);
      isTextureInitialized = true;
      Log.e(TAG, "initializeTexture: created ExternalTexture for texId=" + cameraTextureId);
      // If the material was already loaded, bind it now
      if (defaultCameraMaterial != null && cameraMaterial == null) {
        setCameraMaterial(defaultCameraMaterial);
      }
    }
  }

  /**
   * Recreates the ExternalTexture with a new GL texture ID.
   * Called by ArSceneView when the camera texture needs to be rebound
   * in a different EGL context (fixes black-camera on context mismatch).
   */
  public void reinitializeTexture(int newTextureId) {
    this.cameraTextureId = newTextureId;
    cameraTexture = new ExternalTexture(newTextureId);
    isTextureInitialized = true;
    Log.e(TAG, "reinitializeTexture: created ExternalTexture for texId=" + newTextureId);

    // If material is already loaded, rebind the new texture to it.
    // Must also recreate the Filament renderable since the texture changed.
    if (defaultCameraMaterial != null) {
      // Reset renderable so setCameraMaterial creates a new one
      if (cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE) {
        scene.remove(cameraStreamRenderable);
        cameraStreamRenderable = UNINITIALIZED_FILAMENT_RENDERABLE;
      }
      cameraMaterial = null;
      setCameraMaterial(defaultCameraMaterial);
    }
  }

  public void recalculateCameraUvs(Frame frame) {
    // Pull the latest camera frame into the ExternalTexture's SurfaceTexture.
    if (cameraTexture != null) {
      cameraTexture.updateCameraTexture();
    }

    IEngine engine = EngineInstance.getEngine();

    FloatBuffer cameraUvCoords = this.cameraUvCoords;
    FloatBuffer transformedCameraUvCoords = this.transformedCameraUvCoords;
    VertexBuffer cameraVertexBuffer = this.cameraVertexBuffer;
    frame.transformDisplayUvCoords(cameraUvCoords, transformedCameraUvCoords);
    adjustCameraUvsForOpenGL();
    cameraVertexBuffer.setBufferAt(
        engine.getFilamentEngine(), UV_BUFFER_INDEX, transformedCameraUvCoords);
  }

  private boolean directUploadUvsLogged = false;
  private boolean directUploadUvsComputed = false;

  /**
   * Computes correct UVs for the direct bitmap upload path.
   * ARCore's transformDisplayUvCoords is designed for its GPU camera texture
   * which may have a different aspect ratio than the CPU image from acquireCameraImage().
   * This method computes fill-mode UVs based on the actual camera image and screen dimensions.
   */
  public void recalculateCameraUvsForDirectUpload(int screenW, int screenH) {
    if (cameraTexture == null) return;
    int camW = cameraTexture.getCameraWidth();   // landscape width (e.g. 640)
    int camH = cameraTexture.getCameraHeight();  // landscape height (e.g. 480)
    if (camW == 0 || camH == 0 || screenW == 0 || screenH == 0) return;

    // Camera is landscape, display is portrait (90° CW rotation).
    // After rotation: portrait camera = camH(w) × camW(h)
    float camPortraitW = (float) camH;
    float camPortraitH = (float) camW;

    // Fill mode: scale uniformly to fill entire screen, crop excess
    float fillScale = Math.max((float) screenW / camPortraitW,
                               (float) screenH / camPortraitH);

    // Visible camera area in portrait pixels
    float visibleW = (float) screenW / fillScale;
    float visibleH = (float) screenH / fillScale;

    // Map to landscape texture UV:
    //   Screen horizontal → texture V (landscape Y), with Y-flip
    //   Screen vertical   → texture U (landscape X)
    float cropMarginV = (camH - visibleW) / (2.0f * camH);
    float vMin = cropMarginV;           // post Y-flip: same as pre-flip for symmetric crop
    float vMax = 1.0f - cropMarginV;

    float cropMarginU = (camW - visibleH) / (2.0f * camW);
    float uMin = cropMarginU;
    float uMax = 1.0f - cropMarginU;

    // Oversized triangle UVs (post Y-flip):
    //   v0 = screen TL → (uMin, vMin)
    //   v1 = extrapolated below BL → (2*uMax - uMin, vMin)
    //   v2 = extrapolated right of TR → (uMin, 2*vMax - vMin)
    transformedCameraUvCoords.rewind();
    transformedCameraUvCoords.put(uMin);
    transformedCameraUvCoords.put(vMin);
    transformedCameraUvCoords.put(2.0f * uMax - uMin);
    transformedCameraUvCoords.put(vMin);
    transformedCameraUvCoords.put(uMin);
    transformedCameraUvCoords.put(2.0f * vMax - vMin);
    transformedCameraUvCoords.rewind();

    if (!directUploadUvsLogged) {
      Log.e(TAG, String.format("DirectUpload UVs: cam=%dx%d screen=%dx%d "
          + "V=[%.3f,%.3f] U=[%.3f,%.3f] fill=%.3f",
          camW, camH, screenW, screenH, vMin, vMax, uMin, uMax, fillScale));
      directUploadUvsLogged = true;
    }

    directUploadUvsComputed = true;

    IEngine engine = EngineInstance.getEngine();
    cameraVertexBuffer.setBufferAt(
        engine.getFilamentEngine(), UV_BUFFER_INDEX, transformedCameraUvCoords);
  }

  public boolean isDirectUpload() {
    return cameraTexture != null && cameraTexture.isDirectUpload();
  }

  public boolean needsDirectUploadUvs() {
    return isDirectUpload() && !directUploadUvsComputed;
  }

  /**
   * Updates the camera texture content from the ARCore Frame.
   * Must be called EVERY frame (not just when display geometry changes).
   */
  public void updateCameraFrame(Frame frame) {
    if (cameraTexture != null) {
      boolean wasReady = cameraTexture.isTextureReady();
      cameraTexture.updateCameraFrame(frame);

      // For direct upload: the Filament texture is created lazily on first frame.
      // Once it's ready, bind it to the material.
      if (!wasReady && cameraTexture.isTextureReady() && cameraMaterial != null) {
        Log.e(TAG, "Direct upload texture now ready — binding to material");
        bindTextureToMaterial(cameraMaterial);
        cameraTexture.registerCleanup();
      }
    }
  }

  private void bindTextureToMaterial(Material material) {
    if (cameraTexture == null) return;

    if (cameraTexture.isDirectUpload()) {
      // For direct SAMPLER_2D upload, use setTexture to bypass samplerExternal.
      // The material's GLSL uses samplerExternalOES, but on Mali GPUs
      // GL_TEXTURE_2D bound to samplerExternalOES works correctly.
      com.google.android.filament.TextureSampler sampler =
          new com.google.android.filament.TextureSampler();
      sampler.setMinFilter(com.google.android.filament.TextureSampler.MinFilter.LINEAR);
      sampler.setMagFilter(com.google.android.filament.TextureSampler.MagFilter.LINEAR);
      sampler.setWrapModeS(com.google.android.filament.TextureSampler.WrapMode.CLAMP_TO_EDGE);
      sampler.setWrapModeT(com.google.android.filament.TextureSampler.WrapMode.CLAMP_TO_EDGE);
      material.getFilamentMaterialInstance().setParameter(
          MATERIAL_CAMERA_TEXTURE, cameraTexture.getFilamentTexture(), sampler);
      Log.e(TAG, "bindTextureToMaterial: set SAMPLER_2D texture directly on material");
    } else {
      material.setExternalTexture(MATERIAL_CAMERA_TEXTURE,
          Preconditions.checkNotNull(cameraTexture));
      Log.e(TAG, "bindTextureToMaterial: set SAMPLER_EXTERNAL texture on material");
    }
  }

  public void setCameraMaterial(Material material) {
    cameraMaterial = material;
    Log.e(TAG, "setCameraMaterial called, isTextureInitialized=" + isTextureInitialized()
        + ", renderable=" + cameraStreamRenderable);

    if (!isTextureInitialized()) {
      return;
    }

    // For direct upload, the texture might not be ready yet (created on first camera frame).
    // Bind it if ready; otherwise updateCameraFrame will bind it when ready.
    if (cameraTexture != null && cameraTexture.isTextureReady()) {
      bindTextureToMaterial(material);
    } else if (cameraTexture != null && cameraTexture.isDirectUpload()) {
      Log.e(TAG, "setCameraMaterial: direct upload texture not ready yet, deferring bind");
    } else {
      material.setExternalTexture(MATERIAL_CAMERA_TEXTURE, Preconditions.checkNotNull(cameraTexture));
      Log.e(TAG, "setCameraMaterial: external texture set on material");
    }

    if (cameraStreamRenderable == UNINITIALIZED_FILAMENT_RENDERABLE) {
      initializeFilamentRenderable();
      Log.e(TAG, "setCameraMaterial: renderable initialized, entity=" + cameraStreamRenderable
          + ", vertexBuffer=" + cameraVertexBuffer + ", indexBuffer=" + cameraIndexBuffer);
    } else {
      RenderableManager renderableManager = EngineInstance.getEngine().getRenderableManager();
      int renderableInstance = renderableManager.getInstance(cameraStreamRenderable);
      renderableManager.setMaterialInstanceAt(
          renderableInstance, 0, material.getFilamentMaterialInstance());
    }
  }

  public void setCameraMaterialToDefault() {
    if (defaultCameraMaterial != null) {
      setCameraMaterial(defaultCameraMaterial);
    } else {
      // Default camera material hasn't been loaded yet, so just remove any custom material
      // that has been set.
      cameraMaterial = null;
    }
  }

  public void setRenderPriority(int priority) {
    renderablePriority = priority;
    if (cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE) {
      RenderableManager renderableManager = EngineInstance.getEngine().getRenderableManager();
      int renderableInstance = renderableManager.getInstance(cameraStreamRenderable);
      renderableManager.setPriority(renderableInstance, renderablePriority);
    }
  }

  public int getRenderPriority() {
    return renderablePriority;
  }

  private void adjustCameraUvsForOpenGL() {
    // Correct for vertical coordinates to match OpenGL
    for (int i = 1; i < VERTEX_COUNT * 2; i += 2) {
      transformedCameraUvCoords.put(i, 1.0f - transformedCameraUvCoords.get(i));
    }
  }

  private static FloatBuffer createCameraUVBuffer() {
    FloatBuffer buffer =
        ByteBuffer.allocateDirect(CAMERA_UVS.length * FLOAT_SIZE_IN_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    buffer.put(CAMERA_UVS);
    buffer.rewind();

    return buffer;
  }

  private void initializeFilamentRenderable() {
    // create entity id
    cameraStreamRenderable = EntityManager.get().create();

    // create the quad renderable (leave off the aabb)
    RenderableManager.Builder builder = new RenderableManager.Builder(1);
    builder
        .castShadows(false)
        .receiveShadows(false)
        .culling(false)
        // Always draw the camera feed last to avoid overdraw
        .priority(renderablePriority)
        .geometry(
            0, RenderableManager.PrimitiveType.TRIANGLES, cameraVertexBuffer, cameraIndexBuffer)
        .material(0, Preconditions.checkNotNull(cameraMaterial).getFilamentMaterialInstance())
        .build(EngineInstance.getEngine().getFilamentEngine(), cameraStreamRenderable);

    // add to the scene
    scene.addEntity(cameraStreamRenderable);

    ResourceManager.getInstance()
        .getCameraStreamCleanupRegistry()
        .register(
            this,
            new CleanupCallback(
                scene, cameraStreamRenderable, cameraIndexBuffer, cameraVertexBuffer));
  }

  /** Cleanup filament objects after garbage collection */
  private static final class CleanupCallback implements Runnable {
    private final Scene scene;
    private final int cameraStreamRenderable;
    private final IndexBuffer cameraIndexBuffer;
    private final VertexBuffer cameraVertexBuffer;

    CleanupCallback(
        Scene scene,
        int cameraStreamRenderable,
        IndexBuffer cameraIndexBuffer,
        VertexBuffer cameraVertexBuffer) {
      this.scene = scene;
      this.cameraStreamRenderable = cameraStreamRenderable;
      this.cameraIndexBuffer = cameraIndexBuffer;
      this.cameraVertexBuffer = cameraVertexBuffer;
    }

    @Override
    public void run() {
      AndroidPreconditions.checkUiThread();

      IEngine engine = EngineInstance.getEngine();
      if (engine == null && !engine.isValid()) {
        return;
      }

      if (cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE) {
        scene.remove(cameraStreamRenderable);
      }

      engine.destroyIndexBuffer(cameraIndexBuffer);
      engine.destroyVertexBuffer(cameraVertexBuffer);
    }
  }
}
