package com.google.ar.sceneform.rendering;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import androidx.annotation.Nullable;
import android.view.Surface;
import com.google.android.filament.Stream;
import com.google.ar.core.Frame;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

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
   * Uses GPU blit to copy ARCore camera texture to a SurfaceTexture via an
   * EGL window surface. The SurfaceTexture feeds Filament's Stream.
   */
  @SuppressWarnings("initialization")
  ExternalTexture(int textureId) {
    SurfaceTexture surfaceTexture = new SurfaceTexture(0);
    surfaceTexture.detachFromGLContext();
    // Set buffer size to common camera resolution; will be resized when actual
    // camera dimensions are known from the first frame.
    surfaceTexture.setDefaultBufferSize(1920, 1080);
    this.surfaceTexture = surfaceTexture;

    this.surface = new Surface(surfaceTexture);

    IEngine engine = EngineInstance.getEngine();

    Stream stream =
        new Stream.Builder()
            .stream(surfaceTexture)
            .build(engine.getFilamentEngine());

    initialize(stream);

    this.arCoreTextureId = textureId;

    Log.e(TAG, "Camera ExternalTexture (GPU blit): arCoreTexId=" + textureId
        + " filament=" + (filamentTexture != null)
        + " stream=" + (filamentStream != null));
  }

  private int arCoreTextureId = 0;
  private long lastFrameTimestamp = 0;
  private boolean bufferSizeSet = false;

  // GPU blit fields
  private EGLSurface blitEglSurface;
  private EGLDisplay blitEglDisplay;
  private int blitProgram = -1;
  private boolean blitInitialized = false;
  private FloatBuffer blitVertexBuf;
  private FloatBuffer blitTexCoordBuf;

  public void updateCameraTexture() {
    // No-op
  }

  /**
   * Initializes the EGL surface and shader program for GPU blit.
   */
  private boolean initBlit() {
    try {
      blitEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);

      int[] configAttribs = {
          EGL14.EGL_RENDERABLE_TYPE, 0x40 /* ES3 */,
          EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
          EGL14.EGL_RED_SIZE, 8,
          EGL14.EGL_GREEN_SIZE, 8,
          EGL14.EGL_BLUE_SIZE, 8,
          EGL14.EGL_NONE
      };
      EGLConfig[] configs = new EGLConfig[1];
      int[] numConfig = new int[1];
      EGL14.eglChooseConfig(blitEglDisplay, configAttribs, 0, configs, 0, 1, numConfig, 0);
      if (numConfig[0] == 0) {
        Log.e(TAG, "initBlit: no suitable EGL config");
        return false;
      }

      blitEglSurface = EGL14.eglCreateWindowSurface(
          blitEglDisplay, configs[0], surface, new int[]{EGL14.EGL_NONE}, 0);
      if (blitEglSurface == EGL14.EGL_NO_SURFACE) {
        Log.e(TAG, "initBlit: failed to create EGL window surface, err=0x"
            + Integer.toHexString(EGL14.eglGetError()));
        return false;
      }

      // Use current context (shared with Filament, holds ARCore texture)
      EGLContext ctx = EGL14.eglGetCurrentContext();
      EGLSurface oldDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
      EGLSurface oldRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);

      EGL14.eglMakeCurrent(blitEglDisplay, blitEglSurface, blitEglSurface, ctx);

      String vertSrc =
          "attribute vec4 aPosition;\n" +
          "attribute vec2 aTexCoord;\n" +
          "varying vec2 vTexCoord;\n" +
          "void main() {\n" +
          "  gl_Position = aPosition;\n" +
          "  vTexCoord = aTexCoord;\n" +
          "}\n";

      String fragSrc =
          "#extension GL_OES_EGL_image_external : require\n" +
          "precision mediump float;\n" +
          "varying vec2 vTexCoord;\n" +
          "uniform samplerExternalOES uTexture;\n" +
          "void main() {\n" +
          "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
          "}\n";

      int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
      GLES20.glShaderSource(vs, vertSrc);
      GLES20.glCompileShader(vs);
      int[] status = new int[1];
      GLES20.glGetShaderiv(vs, GLES20.GL_COMPILE_STATUS, status, 0);
      if (status[0] == 0) {
        Log.e(TAG, "Vertex shader error: " + GLES20.glGetShaderInfoLog(vs));
        GLES20.glDeleteShader(vs);
        EGL14.eglMakeCurrent(blitEglDisplay, oldDraw, oldRead, ctx);
        return false;
      }

      int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
      GLES20.glShaderSource(fs, fragSrc);
      GLES20.glCompileShader(fs);
      GLES20.glGetShaderiv(fs, GLES20.GL_COMPILE_STATUS, status, 0);
      if (status[0] == 0) {
        Log.e(TAG, "Fragment shader error: " + GLES20.glGetShaderInfoLog(fs));
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        EGL14.eglMakeCurrent(blitEglDisplay, oldDraw, oldRead, ctx);
        return false;
      }

      blitProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(blitProgram, vs);
      GLES20.glAttachShader(blitProgram, fs);
      GLES20.glLinkProgram(blitProgram);
      GLES20.glGetProgramiv(blitProgram, GLES20.GL_LINK_STATUS, status, 0);
      if (status[0] == 0) {
        Log.e(TAG, "Program link error: " + GLES20.glGetProgramInfoLog(blitProgram));
        GLES20.glDeleteProgram(blitProgram);
        blitProgram = -1;
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        EGL14.eglMakeCurrent(blitEglDisplay, oldDraw, oldRead, ctx);
        return false;
      }
      GLES20.glDeleteShader(vs);
      GLES20.glDeleteShader(fs);

      float[] verts = { -1f, -1f,  1f, -1f,  -1f, 1f,  1f, 1f };
      blitVertexBuf = ByteBuffer.allocateDirect(verts.length * 4)
          .order(ByteOrder.nativeOrder()).asFloatBuffer();
      blitVertexBuf.put(verts).position(0);

      // Flip Y to match OpenGL convention (ARCore OES is top-down)
      float[] texCoords = { 0f, 1f,  1f, 1f,  0f, 0f,  1f, 0f };
      blitTexCoordBuf = ByteBuffer.allocateDirect(texCoords.length * 4)
          .order(ByteOrder.nativeOrder()).asFloatBuffer();
      blitTexCoordBuf.put(texCoords).position(0);

      EGL14.eglMakeCurrent(blitEglDisplay, oldDraw, oldRead, ctx);

      blitInitialized = true;
      Log.e(TAG, "GPU blit initialized: program=" + blitProgram);
      return true;
    } catch (Exception e) {
      Log.e(TAG, "initBlit failed", e);
      return false;
    }
  }

  /**
   * Copies the ARCore camera texture to the SurfaceTexture via GPU OpenGL blit.
   */
  public void updateCameraFrame(Frame frame) {
    if (surface == null || !surface.isValid() || frame == null) return;
    if (arCoreTextureId <= 0) return;

    long timestamp = frame.getTimestamp();
    if (timestamp == lastFrameTimestamp) return;
    lastFrameTimestamp = timestamp;

    // Resize the SurfaceTexture buffer to match actual camera dimensions on first frame.
    if (!bufferSizeSet && surfaceTexture != null) {
      try {
        android.media.Image img = frame.acquireCameraImage();
        int camW = img.getWidth();
        int camH = img.getHeight();
        img.close();
        surfaceTexture.setDefaultBufferSize(camW, camH);
        bufferSizeSet = true;
        // Must re-create the EGL surface for the new buffer size.
        if (blitInitialized && blitEglSurface != null) {
          EGL14.eglDestroySurface(blitEglDisplay, blitEglSurface);
          blitInitialized = false;
        }
        Log.e(TAG, "Camera buffer resized to " + camW + "x" + camH);
      } catch (Exception e) {
        // Camera image not yet available; will retry next frame.
      }
    }

    if (!blitInitialized && !initBlit()) return;

    EGLContext ctx = EGL14.eglGetCurrentContext();
    EGLSurface oldDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
    EGLSurface oldRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);

    try {
      if (!EGL14.eglMakeCurrent(blitEglDisplay, blitEglSurface, blitEglSurface, ctx)) {
        return;
      }

      int[] dims = new int[1];
      EGL14.eglQuerySurface(blitEglDisplay, blitEglSurface, EGL14.EGL_WIDTH, dims, 0);
      int w = dims[0];
      EGL14.eglQuerySurface(blitEglDisplay, blitEglSurface, EGL14.EGL_HEIGHT, dims, 0);
      int h = dims[0];

      GLES20.glViewport(0, 0, w, h);
      GLES20.glDisable(GLES20.GL_DEPTH_TEST);
      GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

      GLES20.glUseProgram(blitProgram);

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, arCoreTextureId);
      GLES20.glUniform1i(GLES20.glGetUniformLocation(blitProgram, "uTexture"), 0);

      int posLoc = GLES20.glGetAttribLocation(blitProgram, "aPosition");
      int tcLoc = GLES20.glGetAttribLocation(blitProgram, "aTexCoord");

      GLES20.glEnableVertexAttribArray(posLoc);
      GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, blitVertexBuf);
      GLES20.glEnableVertexAttribArray(tcLoc);
      GLES20.glVertexAttribPointer(tcLoc, 2, GLES20.GL_FLOAT, false, 0, blitTexCoordBuf);

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

      GLES20.glDisableVertexAttribArray(posLoc);
      GLES20.glDisableVertexAttribArray(tcLoc);

      // Flush all draw commands to the GPU, then present the buffer.
      // glFlush is lighter than glFinish but sufficient for buffer queue delivery.
      GLES20.glFlush();
      if (!EGL14.eglSwapBuffers(blitEglDisplay, blitEglSurface)) {
        Log.e(TAG, "eglSwapBuffers failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
      }
    } catch (Exception e) {
      Log.e(TAG, "GPU blit frame error", e);
    } finally {
      EGL14.eglMakeCurrent(blitEglDisplay, oldDraw, oldRead, ctx);
    }
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

  Stream getFilamentStream() {
    return Preconditions.checkNotNull(filamentStream);
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
