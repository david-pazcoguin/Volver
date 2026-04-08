package com.google.ar.sceneform.rendering;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.util.Log;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.filament.Stream;
import com.google.android.filament.android.TextureHelper;
import com.google.ar.core.Frame;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;

import java.nio.ByteBuffer;

/**
 * Creates an Android {@link SurfaceTexture} and {@link Surface} that can be displayed by Sceneform.
 * Useful for displaying video, or anything else that can be drawn to a {@link SurfaceTexture}.
 *
 * <p>The getFilamentEngine OpenGL ES texture is automatically created by Sceneform. Also, {@link
 * SurfaceTexture#updateTexImage()} is automatically called and should not be called manually.
 *
 * <p>Call {@link Material#setExternalTexture(String, ExternalTexture)} to use an ExternalTexture.
 * The material parameter MUST be of type 'samplerExternal'.
 */
public class ExternalTexture {
  private static final String TAG = ExternalTexture.class.getSimpleName();

  @Nullable private final SurfaceTexture surfaceTexture;
  @Nullable private final Surface surface;

  @Nullable private com.google.android.filament.Texture filamentTexture;
  @Nullable private Stream filamentStream;

  /** Creates an ExternalTexture with a new Android {@link SurfaceTexture} and {@link Surface}. */
  @SuppressWarnings("initialization")
  public ExternalTexture() {
    // Create the Android surface texture.
    SurfaceTexture surfaceTexture = new SurfaceTexture(0);
    surfaceTexture.detachFromGLContext();
    this.surfaceTexture = surfaceTexture;

    // Create the Android surface.
    surface = new Surface(surfaceTexture);

    // Create the filament stream.
    Stream stream =
        new Stream.Builder()
            .stream(surfaceTexture).build(EngineInstance.getEngine().getFilamentEngine());

    initialize(stream);
  }

  /**
   * Creates an ExternalTexture for the AR camera stream.
   * Bypasses SurfaceTexture/Stream/OES entirely — uses a regular SAMPLER_2D
   * Filament Texture with direct bitmap upload via TextureHelper.setBitmap().
   * This eliminates the SurfaceTexture BufferQueue + OES texture pipeline
   * that caused flickering on Mali GPUs.
   */
  @SuppressWarnings("initialization")
  ExternalTexture(int textureId) {
    // No SurfaceTexture or Surface needed — we upload directly to Filament
    this.surfaceTexture = null;
    this.surface = null;
    this.arCoreTextureId = textureId;
    this.useDirectUpload = true;

    // Texture will be created on first frame when we know the camera dimensions
    this.filamentTexture = null;
    this.filamentStream = null;

    Log.e(TAG, "Camera ExternalTexture (DIRECT UPLOAD path): arCoreTexId=" + textureId);
  }

  private int arCoreTextureId = 0;
  private long lastFrameTimestamp = 0;
  private boolean bufferSizeSet = false;
  private Bitmap cameraBitmap;
  private int[] cameraPixels;
  private boolean hasBitmap = false;
  private int frameCount = 0;
  private int postCount = 0;
  private int failCount = 0;
  private boolean useDirectUpload = false;

  public void updateCameraTexture() {
    // No-op
  }

  /**
   * Acquires the camera image from ARCore, converts YUV→RGB on CPU,
   * and pushes it to the SurfaceTexture via Canvas (no GL operations at all).
   * Always re-posts the last good bitmap even when no new camera image is
   * available, so Filament's Stream always has fresh data.
   */
  public void updateCameraFrame(Frame frame) {
    frameCount++;
    if (frame == null) return;
    if (arCoreTextureId <= 0) return;

    long timestamp = frame.getTimestamp();
    boolean newFrame = (timestamp != lastFrameTimestamp);

    if (frameCount <= 10 || frameCount % 60 == 0) {
      Log.e(TAG, "updateCameraFrame: frame#" + frameCount + " newFrame=" + newFrame
          + " ts=" + timestamp + " posts=" + postCount + " fails=" + failCount
          + " direct=" + useDirectUpload);
    }

    if (!newFrame) return;
    lastFrameTimestamp = timestamp;

    Image cameraImage = null;
    try {
      cameraImage = frame.acquireCameraImage();
      int w = cameraImage.getWidth();
      int h = cameraImage.getHeight();

      if (!bufferSizeSet || cameraBitmap == null
          || cameraBitmap.getWidth() != w || cameraBitmap.getHeight() != h) {
        cameraBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        cameraPixels = new int[w * h];
        bufferSizeSet = true;
        Log.e(TAG, "Camera buffer sized: " + w + "x" + h);

        if (useDirectUpload) {
          // Create the Filament 2D texture now that we know dimensions
          IEngine engine = EngineInstance.getEngine();
          if (filamentTexture != null) {
            engine.destroyTexture(filamentTexture);
          }
          filamentTexture = new com.google.android.filament.Texture.Builder()
              .width(w)
              .height(h)
              .levels(1)
              .sampler(com.google.android.filament.Texture.Sampler.SAMPLER_2D)
              .format(com.google.android.filament.Texture.InternalFormat.RGBA8)
              .build(engine.getFilamentEngine());
          Log.e(TAG, "Created SAMPLER_2D Filament texture: " + w + "x" + h);
        }
      }

      // Convert YUV_420_888 → ARGB pixels
      yuvToArgb(cameraImage, cameraPixels, w, h);
      cameraBitmap.setPixels(cameraPixels, 0, w, 0, 0, w, h);
      hasBitmap = true;

      if (useDirectUpload && filamentTexture != null) {
        // Upload bitmap directly to Filament texture — no SurfaceTexture involved
        IEngine engine = EngineInstance.getEngine();
        TextureHelper.setBitmap(engine.getFilamentEngine(), filamentTexture, 0, cameraBitmap);
        postCount++;
      } else if (surface != null && surface.isValid()) {
        // Legacy Canvas path
        android.graphics.Canvas canvas = surface.lockCanvas(null);
        canvas.drawBitmap(cameraBitmap, 0, 0, null);
        surface.unlockCanvasAndPost(canvas);
        postCount++;
      }

    } catch (Exception e) {
      failCount++;
      Log.e(TAG, "updateCameraFrame FAILED frame#" + frameCount + ": " + e.getMessage());
    } finally {
      if (cameraImage != null) {
        cameraImage.close();
      }
    }
  }

  /**
   * Convert YUV_420_888 (from ARCore camera) to ARGB int array.
   * Optimized: bulk row reads into byte[] instead of per-pixel ByteBuffer.get().
   */
  private byte[] yRow;
  private byte[] uvRow;

  private void yuvToArgb(Image image, int[] out, int w, int h) {
    Image.Plane yPlane = image.getPlanes()[0];
    Image.Plane uPlane = image.getPlanes()[1];
    Image.Plane vPlane = image.getPlanes()[2];

    ByteBuffer yBuf = yPlane.getBuffer();
    ByteBuffer uBuf = uPlane.getBuffer();
    ByteBuffer vBuf = vPlane.getBuffer();

    int yRowStride = yPlane.getRowStride();
    int uvRowStride = uPlane.getRowStride();
    int uvPixelStride = uPlane.getPixelStride();

    // Allocate row buffers once
    if (yRow == null || yRow.length < yRowStride) {
      yRow = new byte[yRowStride];
    }
    if (uvRow == null || uvRow.length < uvRowStride) {
      uvRow = new byte[uvRowStride];
    }

    byte[] uRowBuf = new byte[uvRowStride];
    byte[] vRowBuf = new byte[uvRowStride];

    int prevUvRow = -1;

    for (int row = 0; row < h; row++) {
      // Bulk read Y row
      yBuf.position(row * yRowStride);
      yBuf.get(yRow, 0, Math.min(yRowStride, yBuf.remaining()));

      int uvRowIdx = row >> 1;
      // Only re-read UV rows when we move to a new chroma row
      if (uvRowIdx != prevUvRow) {
        int uvPos = uvRowIdx * uvRowStride;
        uBuf.position(uvPos);
        uBuf.get(uRowBuf, 0, Math.min(uvRowStride, uBuf.remaining()));
        vBuf.position(uvPos);
        vBuf.get(vRowBuf, 0, Math.min(uvRowStride, vBuf.remaining()));
        prevUvRow = uvRowIdx;
      }

      for (int col = 0; col < w; col++) {
        int y = yRow[col] & 0xFF;
        int uvCol = col >> 1;
        int uvOffset = uvCol * uvPixelStride;
        int u = uRowBuf[uvOffset] & 0xFF;
        int v = vRowBuf[uvOffset] & 0xFF;

        int yVal = y - 16;
        int uVal = u - 128;
        int vVal = v - 128;

        int r = clamp((298 * yVal + 409 * vVal + 128) >> 8);
        int g = clamp((298 * yVal - 100 * uVal - 208 * vVal + 128) >> 8);
        int b = clamp((298 * yVal + 516 * uVal + 128) >> 8);

        out[row * w + col] = 0xFF000000 | (r << 16) | (g << 8) | b;
      }
    }
  }

  private static int clamp(int val) {
    return val < 0 ? 0 : (val > 255 ? 255 : val);
  }

  /** Gets the surface texture created for this ExternalTexture. */
  public SurfaceTexture getSurfaceTexture() {
    return Preconditions.checkNotNull(surfaceTexture);
  }

  /**
   * Gets the surface created for this ExternalTexture that draws to {@link #getSurfaceTexture()}
   */
  public Surface getSurface() {
    return Preconditions.checkNotNull(surface);
  }

  com.google.android.filament.Texture getFilamentTexture() {
    return Preconditions.checkNotNull(filamentTexture);
  }

  boolean isTextureReady() {
    return filamentTexture != null;
  }

  boolean isDirectUpload() {
    return useDirectUpload;
  }

  int getCameraWidth() {
    return cameraBitmap != null ? cameraBitmap.getWidth() : 0;
  }

  int getCameraHeight() {
    return cameraBitmap != null ? cameraBitmap.getHeight() : 0;
  }

  Stream getFilamentStream() {
    return filamentStream;
  }

  @SuppressWarnings("initialization")
  private void initialize(Stream filamentStream) {
    if (filamentTexture != null) {
      throw new AssertionError("Stream was initialized twice");
    }

    // Create the filament stream.
    IEngine engine = EngineInstance.getEngine();
    this.filamentStream = filamentStream;

    // Create the filament texture.
    final com.google.android.filament.Texture.Sampler textureSampler =
        com.google.android.filament.Texture.Sampler.SAMPLER_EXTERNAL;
    final com.google.android.filament.Texture.InternalFormat textureInternalFormat =
        com.google.android.filament.Texture.InternalFormat.RGB8;

    filamentTexture =
        new com.google.android.filament.Texture.Builder()
            .sampler(textureSampler)
            .format(textureInternalFormat)
            .build(engine.getFilamentEngine());

    filamentTexture.setExternalStream(engine.getFilamentEngine(), filamentStream);
    ResourceManager.getInstance()
        .getExternalTextureCleanupRegistry()
        .register(this, new CleanupCallback(filamentTexture, filamentStream));
  }

  /**
   * Registers cleanup for direct-upload textures (no stream).
   * Must be called after the texture is created in updateCameraFrame.
   */
  void registerCleanup() {
    if (filamentTexture != null) {
      ResourceManager.getInstance()
          .getExternalTextureCleanupRegistry()
          .register(this, new CleanupCallback(filamentTexture, null));
    }
  }

  /** Cleanup filament objects after garbage collection */
  private static final class CleanupCallback implements Runnable {
    @Nullable private final com.google.android.filament.Texture filamentTexture;
    @Nullable private final Stream filamentStream;

    CleanupCallback(com.google.android.filament.Texture filamentTexture, Stream filamentStream) {
      this.filamentTexture = filamentTexture;
      this.filamentStream = filamentStream;
    }

    @Override
    public void run() {
      AndroidPreconditions.checkUiThread();

      IEngine engine = EngineInstance.getEngine();
      if (engine == null || !engine.isValid()) {
        return;
      }
      if (filamentTexture != null) {
        engine.destroyTexture(filamentTexture);
      }

      if (filamentStream != null) {
        engine.destroyStream(filamentStream);
      }
    }
  }
}
