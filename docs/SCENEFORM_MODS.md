# Sceneform / Filament Modifications

This project uses a **heavily modified fork of Google Sceneform** upgraded from Filament ~1.4 to **Filament 1.32.0**. The original Sceneform 1.15 SDK is no longer maintained by Google. These modifications are critical for the AR camera to work.

> ⚠️ Read [ai-workflows/01-ar-camera/context.md](../ai-workflows/01-ar-camera/context.md) before modifying any rendering code.

## What Was Changed and Why

| File | Change | Reason |
|------|--------|--------|
| `ExternalTexture.java` | New constructor using `importTexture()` | Filament 1.32 removed `StreamType.TEXTURE_ID` |
| `CameraStream.java` | Texture created eagerly in constructor | Eliminates race condition with async material loading |
| `ArSceneView.java` | `renderOnly` flag + disabled plane visuals | 60fps rendering without breaking camera |
| `SceneView.java` | Render-only branch in frame loop | Re-present frames without scene graph traversal |
| `Renderer.java` | `enablePerformanceMode()` | Disable MSAA, post-processing, shadows for mid-range devices |
| `PlaneRenderer.java` | Early returns when disabled | Skip unnecessary plane overlay processing |
| `PlaneVisualizer.java` | Polygon boundary caching | Skip geometry rebuild when plane shape unchanged |
| `ArFragment.java` | Disabled depth/light estimation, horizontal-only planes | Reduce ARCore ML overhead |
| `BaseArFragment.java` | Frame timestamp deduplication | Skip redundant `getUpdatedTrackables()` calls |

## Camera Material

The camera background uses a custom Filament material compiled with `matc` v1.32.0. See [ai-workflows/01-ar-camera/context.md](../ai-workflows/01-ar-camera/context.md) for material details.

## Key Rule

**Never change `onBeginFrame()` to `return true`.** This causes the camera to go black because `doUpdate()` runs on stale ARCore frames. The `renderOnly` flag is the correct solution for 60fps rendering.

## Troubleshooting the AR Camera

| Symptom | Solution |
|---------|----------|
| Camera shows black screen | Do NOT return `true` from `onBeginFrame()`. Check that the `renderOnly` flag is working |
| Camera renders at ~30fps (judder) | The `renderOnly` flag may not be working. Check `SceneView.doFrameNoRepost()` has the `else if (renderOnly)` branch |
| Material fails to load | Recompile `.matc` with matching Filament version. See [ai-workflows/06-build-deploy/context.md](../ai-workflows/06-build-deploy/context.md) |
| App crashes on `setExternalImage()` | This API was removed in Filament 1.32. Use `importTexture()` instead |
