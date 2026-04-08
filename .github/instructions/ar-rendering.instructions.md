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
- **Texture format `RGBA8`** — NOT `SRGB8_A8` (causes double gamma darkening with post-processing off)
- **Camera material uses `sampler2d`** (not `samplerExternal`) — see `camera_material_2d.mat`
- **Custom UV mapping** — `recalculateCameraUvsForDirectUpload()` computes fill-mode UVs; ARCore's `transformDisplayUvCoords()` is for GPU texture only
- **`needsDirectUploadUvs` flag** — retries UV computation until camera bitmap dimensions are available (race condition fix)
- **Always clean build** after changes: `.\gradlew :sceneform:clean :app:clean :app:assembleDebug`
- **renderOnly flag** — never call `doUpdate()` on stale ARCore frames

## Key Files

- `ExternalTexture.java` — camera texture wrapping (direct upload, RGBA8, optimized YUV→ARGB with bulk row reads)
- `CameraStream.java` — fullscreen renderable, material binding, `recalculateCameraUvsForDirectUpload()`
- `ArSceneView.java` — frame loop, session.update(), renderOnly flag, direct upload UV dispatch
- `ArFragment.java` (sceneformux) — ARCore session config, `selectHighResCameraConfig()` targeting ~1280×720
- `SceneView.java` — Choreographer loop, render-only branch
- `Renderer.java` — performance mode (MSAA off, post-processing off)
- `sceneform_camera_material.matc` — compiled camera material (sampler2d)

## Camera Resolution

- Default ARCore resolution is 640×480 — too blurry for production
- `ArFragment.selectHighResCameraConfig()` selects config closest to 1280×720 via `CameraConfigFilter`
- 1920×1080 available but too expensive for per-frame CPU YUV→ARGB conversion
- YUV conversion uses bulk `ByteBuffer.get(byte[], offset, length)` row reads + UV row caching
