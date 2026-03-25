# AR Camera & Rendering Architecture

This document explains the custom Sceneform/Filament modifications that make the AR camera work. Future developers **must** understand this before touching any rendering code — changes here can easily break the camera.

---

## Table of Contents

1. [How the Camera Renders](#how-the-camera-renders)
2. [Frame Pipeline](#frame-pipeline)
3. [Modified Files Summary](#modified-files-summary)
4. [ExternalTexture — importTexture()](#externaltexture--importtexture)
5. [Camera Material (.matc)](#camera-material-matc)
6. [The renderOnly Flag](#the-renderonly-flag)
7. [CameraStream Initialization](#camerastream-initialization)
8. [Known Pitfalls](#known-pitfalls)
9. [File-by-File Reference](#file-by-file-reference)

---

## How the Camera Renders

The AR camera feed is displayed as a **fullscreen quad** behind all 3D content. The pipeline:

1. **ARCore** captures camera frames and provides a GL texture ID (`cameraTextureId`)
2. **Filament** wraps that GL texture via `Texture.Builder.importTexture()` — this replaced the deprecated `StreamType.TEXTURE_ID` from older Filament versions
3. A **custom material** (compiled with `matc`) samples this external texture and draws it as the background
4. **SceneView** drives the Choreographer loop, calling `onBeginFrame()` → `doUpdate()` → `doRender()` each frame

### Why It's Custom

Google's original Sceneform 1.15 targeted Filament ~1.4. This project uses **Filament 1.32**, which removed several APIs:

- `StreamType.TEXTURE_ID` → replaced with `importTexture()`
- `setExternalImage()` → removed (caused SIGSEGV)
- Material format changed → requires recompilation with Filament 1.32's `matc`

---

## Frame Pipeline

```
Choreographer.doFrame(frameTimeNanos)
       │
       ▼
SceneView.doFrameNoRepost()
       │
       ├── onBeginFrame(frameTimeNanos)     ← ArSceneView overrides this
       │       │
       │       ├── session.update()          ← Gets latest ARCore frame
       │       ├── cameraStream.recalculateCameraUvs()
       │       ├── updated = (new timestamp != old timestamp)
       │       │
       │       ├── if (!updated) renderOnly = true
       │       └── return updated
       │
       ├── if (returned true)
       │       ├── doUpdate(frameTimeNanos)  ← Scene graph traversal, plane updates
       │       └── doRender()                ← Filament render + swap
       │
       └── else if (renderOnly)
               ├── doRender()                ← Re-present same frame, no scene update
               └── renderOnly = false
```

### Key Concept: `updated` vs `renderOnly`

- ARCore's `session.update()` with `LATEST_CAMERA_IMAGE` is non-blocking
- When no new camera frame is available, `updated` is `false`
- **Returning `false` from `onBeginFrame()`** would skip rendering entirely → drops to ~30fps on a 60Hz display
- **Returning `true` always** feeds stale frames into `doUpdate()` → breaks the camera texture on some devices (black screen)
- **The `renderOnly` flag** is the solution: when `!updated`, we skip `doUpdate()` but still call `doRender()` to re-present the last valid frame at the display's refresh rate

---

## Modified Files Summary

| File | What Changed | Why |
|------|-------------|-----|
| `ExternalTexture.java` | New constructor using `importTexture()` | Filament 1.32 removed `StreamType.TEXTURE_ID` |
| `CameraStream.java` | Texture created in constructor | Eliminated race condition with async material loading |
| `ArSceneView.java` | `renderOnly` flag, disabled plane rendering, performance mode | 60fps rendering + performance |
| `SceneView.java` | `renderOnly` field + render-only branch in `doFrameNoRepost()` | Skip scene traversal on stale frames |
| `Renderer.java` | `enablePerformanceMode()`, disabled MSAA/post-processing/shadows | Mid-range device performance |
| `PlaneRenderer.java` | Early returns when disabled | Avoid wasted work |
| `PlaneVisualizer.java` | Polygon caching | Skip geometry rebuild on unchanged planes |
| `ArFragment.java` | Disabled depth, light estimation; horizontal planes only | ARCore ML feature overhead |
| `BaseArFragment.java` | Frame timestamp dedup | Skip duplicate `getUpdatedTrackables()` calls |

---

## ExternalTexture — importTexture()

**File:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/ExternalTexture.java`

The critical constructor that wraps ARCore's GL texture for Filament:

```java
ExternalTexture(int textureId) {
    this.surfaceTexture = null;
    this.surface = null;

    IEngine engine = EngineInstance.getEngine();
    filamentTexture = new com.google.android.filament.Texture.Builder()
        .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
        .format(Texture.InternalFormat.RGB16F)
        .importTexture((long) textureId)
        .build(engine.getFilamentEngine());

    this.filamentStream = null;
    // ... cleanup registration
}
```

**Why these specific settings:**
- `SAMPLER_EXTERNAL` — required for GL_TEXTURE_EXTERNAL_OES (what ARCore provides)
- `RGB16F` — format for HDR camera data
- `importTexture((long) textureId)` — tells Filament to use an existing GL texture instead of creating its own
- No width/height needed — Filament reads dimensions from the EGLImage

---

## Camera Material (.matc)

The camera background material must be compiled with the **same version** of `matc` as your Filament runtime.

### matc Compiler Location

```
%TEMP%\filament-matc\win\bin\matc.exe
```

This is the Filament v1.32.0 `matc` binary.

### Vertex Shader — The Key Trick

```glsl
material.worldPosition = inverse(getClipFromWorldMatrix()) * getPosition();
```

**What this does:** Filament applies a View-Projection transform to all vertices. For a fullscreen background quad, we need the vertices to stay in clip space (fill the screen). This line **undoes** Filament's VP transform by multiplying with its inverse, so the quad vertices map directly to screen corners.

Without this, the camera quad would be positioned in world space and appear as a tiny floating rectangle.

### Fragment Shader

```glsl
material.baseColor = texture(materialParams_cameraTexture, getUV0());
```

Samples the ARCore camera texture using UV coordinates that ARCore provides and `CameraStream.recalculateCameraUvs()` maps to the quad.

### Material Properties

```
material {
    name : "Camera Stream",
    parameters : [
        { type : samplerExternal, name : cameraTexture }
    ],
    requires : [ uv0 ],
    shadingModel : unlit,
    depthWrite : false,
    depthCulling : false,
    doubleSided : true
}
```

- `samplerExternal` — matches `SAMPLER_EXTERNAL` in ExternalTexture
- `unlit` — no lighting calculations needed for camera feed
- `depthWrite: false` / `depthCulling: false` — always draws behind everything

### Recompiling the Material

If you ever need to modify the camera material:

```powershell
# From project root
& "$env:TEMP\filament-matc\win\bin\matc.exe" `
    -o app/src/main/res/raw/camera_stream_material.matc `
    -p mobile `
    camera_material.mat
```

> **WARNING:** Using a different version of `matc` than your Filament runtime will crash the app silently. Always use the matc binary that matches your Filament version (1.32.0).

---

## The renderOnly Flag

### The Problem

ARCore delivers camera frames at ~30fps (camera hardware limitation). The display runs at 60Hz. This means every other Choreographer callback, `session.update()` returns the same frame (`updated = false`).

**Approach 1 — Skip rendering when `!updated`** (original Sceneform behavior):
- Result: Filament only renders at ~30fps → visible judder

**Approach 2 — Always return `true` from `onBeginFrame()`**:
- Result: `doUpdate()` processes stale ARCore frames → camera texture goes black on some devices

**Approach 3 — The `renderOnly` flag** (current solution):
- When `!updated`: set `renderOnly = true`, return `false` from `onBeginFrame()`
- `doFrameNoRepost()` checks `renderOnly` and calls `doRender()` without `doUpdate()`
- Result: 60fps display, no scene graph processing on stale frames, camera stays valid

### Implementation

In **ArSceneView.java** `onBeginFrame()`:
```java
if (!updated) {
    renderOnly = true;
}
return updated;
```

In **SceneView.java** `doFrameNoRepost()`:
```java
if (onBeginFrame(frameTimeNanos)) {
    doUpdate(frameTimeNanos);
    doRender();
} else if (renderOnly) {
    doRender();
    renderOnly = false;
}
```

---

## CameraStream Initialization

**File:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/CameraStream.java`

The texture is created **immediately in the constructor**, not lazily:

```java
public CameraStream(int cameraTextureId, Renderer renderer) {
    // ...
    cameraTexture = new ExternalTexture(cameraTextureId);
    isTextureInitialized = true;
    // ...
}
```

**Why:** The old lazy initialization (`initializeTexture()` called from `onBeginFrame()`) had a race condition — the material could load before the texture was ready, or vice versa. Creating the texture immediately eliminates this.

The `initializeTexture(Frame frame)` method is now a no-op stub kept for API compatibility.

---

## Known Pitfalls

### 1. Never run `doUpdate()` on stale ARCore frames
Calling `doUpdate()` when `session.update()` returned an unchanged frame causes the camera to go black on some devices. This is why the `renderOnly` flag exists. **Do not** change `onBeginFrame()` to `return true`.

### 2. matc version must match Filament version
The `.matc` binary material file is version-specific. Compiling with Filament 1.4's matc and running on 1.32's runtime will crash. Always use `%TEMP%\filament-matc\win\bin\matc.exe` (v1.32.0).

### 3. importTexture() replaces StreamType.TEXTURE_ID
Filament 1.32 removed the old Stream-based external texture API. If you see references to `Stream`, `StreamType`, or `setExternalImage()` in old Sceneform tutorials — those don't work anymore.

### 4. Log.e() calls are intentional
Several `Log.e()` calls exist in CameraStream, ExternalTexture, and ArSceneView for debugging camera initialization. These are at ERROR level because on some vendor ROMs (e.g., Oppo ColorOS), only `Log.e()` and native `ALOGE` are visible in logcat. `Log.d()` and `Log.w()` from user apps are silently suppressed.

### 5. Plane renderer is disabled but plane detection is active
`PlaneRenderer.setEnabled(false)` disables the visual overlay. Plane detection remains active in ARCore for hit-testing (user taps a plane to place a character). Don't confuse these — removing plane detection would break model placement.

---

## File-by-File Reference

### ExternalTexture.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/ExternalTexture.java`
- Added `ExternalTexture(int textureId)` constructor
- Uses `importTexture()` + `SAMPLER_EXTERNAL` + `RGB16F`
- Original `ExternalTexture()` constructor (SurfaceTexture-based) still exists for non-camera use

### CameraStream.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/CameraStream.java`
- Texture created in constructor (not lazily)
- `initializeTexture()` is now a no-op
- `setCameraMaterial()` has diagnostic `Log.e()` calls
- Screen quad geometry (vertex/index buffers) built in constructor

### ArSceneView.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/ArSceneView.java`
- `lightEstimationEnabled = false` (default was `true`)
- Both constructors call `renderer.enablePerformanceMode()`
- `initializePlaneRenderer()` disables all plane visuals
- `onBeginFrame()` sets `renderOnly = true` when frame not updated
- `firstFrameLogged` flag for one-time diagnostics

### SceneView.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/SceneView.java`
- Added `protected boolean renderOnly = false` field
- `doFrameNoRepost()` has render-only branch: `doRender()` without `doUpdate()`

### Renderer.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/Renderer.java`
- `enablePerformanceMode()` — disables MSAA, post-processing, dithering, shadowing, low quality HDR
- `setPostProcessingEnabled()` / `setRenderQuality()` — Filament view wrappers
- `addModelInstanceInternal()` / `removeModelInstanceInternal()` — empty stubs (disabled)

### PlaneRenderer.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/PlaneRenderer.java`
- `update()` returns early when `!isEnabled`
- Skips `getFocusPoint()` when `!isVisible`

### PlaneVisualizer.java
**Path:** `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/PlaneVisualizer.java`
- Caches `lastPolygon` FloatBuffer
- `updateRenderableDefinitionForPlane()` compares polygon boundaries before rebuilding geometry

### ArFragment.java
**Path:** `sceneformux/ux/src/main/java/com/google/ar/sceneform/ux/ArFragment.java`
- `getSessionConfiguration()` sets: `DepthMode.DISABLED`, `LightEstimationMode.DISABLED`, `PlaneFindingMode.HORIZONTAL`

### BaseArFragment.java
**Path:** `sceneformux/ux/src/main/java/com/google/ar/sceneform/ux/BaseArFragment.java`
- Tracks `lastOnUpdateTimestamp` to skip `getUpdatedTrackables()` on duplicate frames
