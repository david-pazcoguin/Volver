---
description: "Use when editing Sceneform or Filament rendering code: ExternalTexture, CameraStream, ArSceneView, SceneView, Renderer, PlaneRenderer, PlaneVisualizer, camera materials, matc compilation."
applyTo: "sceneformsrc/**"
---
# AR Rendering Instructions

Read `ai-workflows/01-ar-camera/context.md` for full architecture details.

## Critical Constraints

- **Filament 1.32** — all materials compiled with matching `matc` binary
- **Direct SAMPLER_2D upload** — current camera pipeline bypasses SurfaceTexture entirely (Mali-G68 flickering fix)
- **`TextureHelper.setBitmap()`** for camera frame upload — no Stream, no OES
- **Camera material uses `sampler2d`** (not `samplerExternal`) — see `camera_material_2d.mat`
- **Always clean build** after changes: `.\gradlew :sceneform:clean :app:clean :app:assembleDebug`
- **renderOnly flag** — never call `doUpdate()` on stale ARCore frames

## Key Files

- `ExternalTexture.java` — camera texture wrapping (direct upload path)
- `CameraStream.java` — fullscreen renderable + material binding
- `ArSceneView.java` — frame loop, session.update(), renderOnly flag
- `SceneView.java` — Choreographer loop, render-only branch
- `Renderer.java` — performance mode (MSAA off, post-processing off)
- `sceneform_camera_material.matc` — compiled camera material (sampler2d)
