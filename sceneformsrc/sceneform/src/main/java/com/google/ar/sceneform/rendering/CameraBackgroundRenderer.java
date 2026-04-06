package com.google.ar.sceneform.rendering;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.util.Log;

import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Renders the ARCore camera feed directly to the current framebuffer using
 * raw OpenGL ES calls. This bypasses Filament's importTexture() which does
 * not display the camera feed on some device/driver/Filament version combos.
 *
 * Called as a post-render callback inside Filament's beginFrame/endFrame block
 * where the correct EGL context is guaranteed to be active.
 *
 * @hide
 */
public class CameraBackgroundRenderer {
    private static final String TAG = "CamBgRenderer";

    private static final int VERTEX_COUNT = 3;

    private int program = 0;
    private int aPosition;
    private int aTexCoord;
    private int uTexture;
    private boolean initialized = false;
    private boolean initFailed = false;

    private FloatBuffer vertexBuf;
    private FloatBuffer texCoordBuf;

    // Oversized triangle matching CameraStream's vertex layout.
    // Positions are 2D clip-space (z is set in the vertex shader).
    private static final float[] QUAD_VERTS = {
        -1f,  1f,   // top-left
        -1f, -3f,   // below bottom-left (oversized)
         3f,  1f    // right of top-right (oversized)
    };

    // Canonical UVs matching CameraStream.CAMERA_UVS.
    // These are passed into frame.transformDisplayUvCoords().
    private static final float[] CAMERA_UVS = {
        0f, 0f,
        0f, 2f,
        2f, 0f
    };

    // Direct buffers required by ARCore's transformDisplayUvCoords.
    private final FloatBuffer uvInput;
    private final FloatBuffer uvOutput;

    private static final String VERTEX_SHADER =
        "attribute vec2 aPosition;\n" +
        "attribute vec2 aTexCoord;\n" +
        "varying vec2 vTexCoord;\n" +
        "void main() {\n" +
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
        "  vTexCoord = aTexCoord;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#extension GL_OES_EGL_image_external_essl3 : require\n" +
        "precision mediump float;\n" +
        "uniform samplerExternalOES uTexture;\n" +
        "varying vec2 vTexCoord;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
        "}\n";

    public CameraBackgroundRenderer() {
        vertexBuf = createDirectFloatBuffer(QUAD_VERTS);

        uvInput = createDirectFloatBuffer(CAMERA_UVS);
        uvOutput = createDirectFloatBuffer(CAMERA_UVS);  // same size, will be overwritten
        texCoordBuf = uvOutput;
    }

    private void init() {
        int vs = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) {
            Log.e(TAG, "Shader compilation failed");
            initFailed = true;
            return;
        }
        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vs);
        GLES30.glAttachShader(program, fs);
        GLES30.glLinkProgram(program);

        int[] linked = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Log.e(TAG, "Program link failed: " + GLES30.glGetProgramInfoLog(program));
            GLES30.glDeleteProgram(program);
            program = 0;
            initFailed = true;
            return;
        }

        aPosition = GLES30.glGetAttribLocation(program, "aPosition");
        aTexCoord = GLES30.glGetAttribLocation(program, "aTexCoord");
        uTexture  = GLES30.glGetUniformLocation(program, "uTexture");

        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);

        initialized = true;
        Log.e(TAG, "GL camera shader initialized OK, program=" + program);
    }

    /**
     * Transforms the canonical camera UVs for the current display rotation.
     * Must be called on the main thread whenever display geometry changes.
     */
    public void updateUvs(Frame frame) {
        uvInput.rewind();
        uvOutput.rewind();
        frame.transformDisplayUvCoords(uvInput, uvOutput);
        // Flip V for OpenGL (same as CameraStream.adjustCameraUvsForOpenGL)
        for (int i = 1; i < VERTEX_COUNT * 2; i += 2) {
            uvOutput.put(i, 1.0f - uvOutput.get(i));
        }
        uvOutput.rewind();
        texCoordBuf = uvOutput;
    }

    /**
     * Renders the camera texture onto the current framebuffer.
     * Must be called while Filament's GL context is active (inside
     * beginFrame/endFrame).
     */
    public void draw(int cameraTextureId) {
        if (initFailed) return;
        if (!initialized) {
            init();
            if (!initialized) return;
        }

        // Save GL state we modify
        int[] prevProgram = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_CURRENT_PROGRAM, prevProgram, 0);
        int[] prevActiveTexture = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_ACTIVE_TEXTURE, prevActiveTexture, 0);
        boolean depthTestWas = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
        boolean blendWas = GLES30.glIsEnabled(GLES30.GL_BLEND);
        boolean cullWas = GLES30.glIsEnabled(GLES30.GL_CULL_FACE);
        boolean scissorWas = GLES30.glIsEnabled(GLES30.GL_SCISSOR_TEST);
        int[] prevDepthFunc = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_DEPTH_FUNC, prevDepthFunc, 0);
        boolean[] prevDepthMask = new boolean[1];
        GLES30.glGetBooleanv(GLES30.GL_DEPTH_WRITEMASK, prevDepthMask, 0);

        // Configure for full-screen background draw
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDepthMask(false);

        GLES30.glUseProgram(program);

        // Bind camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES30.glUniform1i(uTexture, 0);

        // Vertex positions
        vertexBuf.position(0);
        GLES30.glEnableVertexAttribArray(aPosition);
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, 0, vertexBuf);

        // Tex coords
        texCoordBuf.position(0);
        GLES30.glEnableVertexAttribArray(aTexCoord);
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 0, texCoordBuf);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, VERTEX_COUNT);

        // Restore GL state
        GLES30.glDisableVertexAttribArray(aPosition);
        GLES30.glDisableVertexAttribArray(aTexCoord);
        GLES30.glDepthMask(prevDepthMask[0]);
        if (depthTestWas) GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        if (blendWas) GLES30.glEnable(GLES30.GL_BLEND);
        if (cullWas) GLES30.glEnable(GLES30.GL_CULL_FACE);
        if (scissorWas) GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDepthFunc(prevDepthFunc[0]);
        GLES30.glActiveTexture(prevActiveTexture[0]);
        GLES30.glUseProgram(prevProgram[0]);

        int err = GLES30.glGetError();
        if (err != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL error after camera draw: 0x" + Integer.toHexString(err));
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile error: " + GLES30.glGetShaderInfoLog(shader));
            GLES30.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static FloatBuffer createDirectFloatBuffer(float[] data) {
        FloatBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(data);
        buf.rewind();
        return buf;
    }
}
