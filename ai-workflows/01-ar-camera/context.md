# Context — AR Camera & Rendering Pipeline

## Architecture Overview

The AR camera feed is displayed as a **fullscreen quad** behind all 3D content:

1. **ARCore** captures camera frames and provides a GL texture ID (`cameraTextureId`)
2. **ExternalTexture** wraps the camera data for Filament consumption
3. A **custom material** (`sceneform_camera_material.matc`) samples the texture and draws the background
4. **SceneView** drives the Choreographer loop: `onBeginFrame()` → `doUpdate()` → `doRender()` each frame

### Why It's Custom

Google's original Sceneform 1.15 targeted Filament ~1.4. This project uses **Filament 1.32**, which removed several APIs:
- `StreamType.TEXTURE_ID` → replaced with `importTexture()`
- `setExternalImage()` → removed (caused SIGSEGV)
- Material format changed → requires recompilation with Filament 1.32's `matc`

## Current Camera Pipeline (Direct SAMPLER_2D Upload)

The current implementation **completely bypasses SurfaceTexture/Stream/OES** to avoid flickering on Mali-G68:

```
frame.acquireCameraImage()
       ↓
CPU YUV→ARGB conversion (yuvToArgb)
       ↓
Bitmap (640×480 ARGB_8888)
       ↓
TextureHelper.setBitmap(engine, filamentTexture, level=0, bitmap)
       ↓
Filament Texture (SAMPLER_2D, SRGB8_A8)
       ↓
Camera Material (sampler2d cameraTexture)
       ↓
CameraStream fullscreen renderable
```

**Zero SurfaceTexture, zero Stream, zero OES textures.**

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

### The `renderOnly` Flag

- ARCore delivers camera frames at ~30fps; display runs at 60Hz
- When no new camera frame is available, `updated` is `false`
- The `renderOnly` flag calls `doRender()` without `doUpdate()` — re-presents the last valid frame at 60fps without traversing the scene graph

## Key Files

| File | Path | Purpose |
|------|------|---------|
| ExternalTexture.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/ExternalTexture.java` | Wraps camera feed for Filament. Direct upload path: creates SAMPLER_2D texture, uses `TextureHelper.setBitmap()` |
| CameraStream.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/CameraStream.java` | Manages fullscreen camera renderable + material binding. `bindTextureToMaterial()` for SAMPLER_2D path |
| ArSceneView.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/ArSceneView.java` | `onBeginFrame()` returns true always (current), plane renderer disabled, performance mode |
| SceneView.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/SceneView.java` | Frame loop with `renderOnly` branch, Choreographer-driven |
| Renderer.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/Renderer.java` | `enablePerformanceMode()` — MSAA off, post-processing off, shadows off |
| PlaneRenderer.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/PlaneRenderer.java` | Early returns when disabled |
| PlaneVisualizer.java | `sceneformsrc/sceneform/src/main/java/com/google/ar/sceneform/rendering/PlaneVisualizer.java` | Polygon boundary caching |
| ArFragment.java | `sceneformux/ux/src/main/java/com/google/ar/sceneform/ux/ArFragment.java` | Depth disabled, light estimation disabled, PlaneFindingMode config |
| BaseArFragment.java | `sceneformux/ux/src/main/java/com/google/ar/sceneform/ux/BaseArFragment.java` | Frame timestamp deduplication |

## Camera Material

**Source**: `sceneformsrc/sceneform/src/main/res/raw/camera_material_2d.mat`
**Compiled**: `sceneformsrc/sceneform/src/main/res/raw/sceneform_camera_material.matc` (18082 bytes)

```
material {
    name : "Camera Material 2D",
    parameters : [
        { type : sampler2d, name : cameraTexture }
    ],
    requires : [ uv0 ],
    vertexDomain : device,
    depthWrite : false,
    shadingModel : unlit,
    doubleSided : true,
    culling : none
}

fragment {
    void material(inout MaterialInputs material) {
        prepareMaterial(material);
        material.baseColor = texture(materialParams_cameraTexture, getUV0());
    }
}
```

Uses `sampler2d` (not `samplerExternal`) to match the direct SAMPLER_2D texture upload path.

### Compiling Materials

```powershell
& "$env:TEMP\filament-1.32\bin\matc.exe" -p mobile -a opengl -o output.matc input.mat
```

All 7 materials in `sceneformsrc/sceneform/src/main/res/raw/`:
- `sceneform_camera_material.matc` — camera background (sampler2d)
- `sceneform_opaque_colored_material.matc`
- `sceneform_transparent_colored_material.matc`
- `sceneform_opaque_textured_material.matc`
- `sceneform_transparent_textured_material.matc`
- `sceneform_plane_material.matc`
- `sceneform_plane_shadow_material.matc`

## Renderer Performance Settings

| Setting | Value | Default | Impact |
|---------|-------|---------|--------|
| Anti-aliasing (MSAA) | `NONE` | `FXAA` | Saves fill-rate on GPU |
| Post-processing | `false` | `true` | Disables tone mapping, bloom, FXAA pass |
| Dithering | `NONE` | `TEMPORAL` | Saves a post-process pass |
| Shadowing | `false` | `true` | Eliminates shadow map rendering |
| HDR color buffer quality | `LOW` | `MEDIUM` | Smaller render targets |

## ARCore Session Configuration

| Feature | Setting | Why |
|---------|---------|-----|
| Depth mode | `DISABLED` | Depth estimation uses ML inference every frame |
| Light estimation | `DISABLED` | Environmental HDR runs ML models |
| Plane finding | `HORIZONTAL` only | Reduces ML workload. Vertical planes not needed |

## Known Pitfalls

1. **Never run `doUpdate()` on stale ARCore frames** — causes black camera on some devices
2. **matc version must match Filament version** — silent crash otherwise
3. **`importTexture()` replaces `StreamType.TEXTURE_ID`** — old Sceneform tutorials are wrong
4. **`Log.e()` calls are intentional** — on Oppo ColorOS, only `Log.e()` is visible in logcat
5. **Plane renderer is disabled but plane detection is active** — needed for hit-testing
6. **SurfaceTexture pipeline flickers on Mali-G68** — use direct SAMPLER_2D upload instead

## Flickering Investigation History

Over 17 phases of debugging confirmed:
- Flickering is NOT from GL state corruption (CPU Canvas path still flickers)
- NOT from Filament beginFrame skipping
- NOT from plane detection/rendering
- NOT from dynamic resolution
- NOT from the renderOnly path
- NOT from Canvas BufferQueue issues
- Root cause: **SurfaceTexture → Filament Stream → SAMPLER_EXTERNAL → OES** pipeline on Mali-G68
- Fix: Direct SAMPLER_2D texture upload with recompiled `sampler2d` material

## Device Info

- **Target GPU**: ARM Mali-G68, OpenGL ES 3.2 v1.r38p1
- **Camera resolution**: 640×480 YUV_420_888
- **Filament workaround active**: `vao_doesnt_store_element_array_buffer_binding`
