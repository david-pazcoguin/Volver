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

  /**
   * Updates the camera texture content from the ARCore Frame.
   * Must be called EVERY frame (not just when display geometry changes).
   */
  public void updateCameraFrame(Frame frame) {
    if (cameraTexture != null) {
      cameraTexture.updateCameraFrame(frame);
    }
  }

  public void setCameraMaterial(Material material) {
    cameraMaterial = material;
    Log.e(TAG, "setCameraMaterial called, isTextureInitialized=" + isTextureInitialized()
        + ", renderable=" + cameraStreamRenderable);

    // The ExternalTexture can't be created until we receive the first AR Core Frame so that we
    // can access the width and height of the camera texture. Return early if the ExternalTexture
    // hasn't been created yet so we don't start rendering until we have a valid texture. This will
    // be called again when the ExternalTexture is created.
    if (!isTextureInitialized()) {
      return;
    }

    material.setExternalTexture(MATERIAL_CAMERA_TEXTURE, Preconditions.checkNotNull(cameraTexture));
    Log.e(TAG, "setCameraMaterial: external texture set on material");

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
