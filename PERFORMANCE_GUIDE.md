# Performance Guide

This document covers every performance optimization in the Volver app and explains the reasoning behind each one. The optimizations target **mid-range Android devices** (e.g., Samsung A35, Oppo A78) running ARCore.

---

## Table of Contents

1. [Filament Renderer Settings](#filament-renderer-settings)
2. [ARCore Session Configuration](#arcore-session-configuration)
3. [Plane Rendering Optimizations](#plane-rendering-optimizations)
4. [Frame Update Deduplication](#frame-update-deduplication)
5. [Render-Only Passes (60fps)](#render-only-passes-60fps)
6. [App-Level Optimizations](#app-level-optimizations)
7. [Build Configuration](#build-configuration)
8. [Measuring Performance](#measuring-performance)

---

## Filament Renderer Settings

**File:** `Renderer.java` → `enablePerformanceMode()`  
**Called from:** `ArSceneView` constructors

| Setting | Value | Default | Impact |
|---------|-------|---------|--------|
| Anti-aliasing (MSAA) | `NONE` | `FXAA` | Saves fill-rate on GPU |
| Post-processing | `false` | `true` | Disables tone mapping, bloom, FXAA pass |
| Dithering | `NONE` | `TEMPORAL` | Saves a post-process pass |
| Shadowing | `false` | `true` | Eliminates shadow map rendering |
| HDR color buffer quality | `LOW` | `MEDIUM` | Smaller render targets |

```java
public void enablePerformanceMode() {
    view.setAntiAliasing(View.AntiAliasing.NONE);
    view.setPostProcessingEnabled(false);
    view.setDithering(View.Dithering.NONE);
    view.setShadowingEnabled(false);
    View.RenderQuality rq = new View.RenderQuality();
    rq.hdrColorBuffer = View.QualityLevel.LOW;
    view.setRenderQuality(rq);
}
```

**Trade-off:** The AR scene looks slightly less polished (no anti-aliasing, no tone mapping). For a tour guide app with small character models, this is acceptable. If visual quality becomes a priority, re-enable post-processing first — it has the biggest visual impact for a moderate performance cost.

---

## ARCore Session Configuration

**File:** `ArFragment.java` → `getSessionConfiguration()`

```java
config.setDepthMode(Config.DepthMode.DISABLED);
config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
```

| Feature | Setting | Why |
|---------|---------|-----|
| Depth mode | `DISABLED` | Depth estimation uses ML inference every frame — heavy on CPU/GPU. Not needed for placing models on planes. |
| Light estimation | `DISABLED` | Environmental HDR runs ML models to estimate lighting. Not needed — our models use unlit/simple shading. |
| Plane finding | `HORIZONTAL` only | Reduces ML workload. Vertical planes aren't needed — characters stand on the ground. |

**Impact on logcat:** Disabling depth mode eliminates the ~103 "ML depth provider" error messages that flood logcat every session.

---

## Plane Rendering Optimizations

### PlaneRenderer (Disabled)

**File:** `ArSceneView.java` → `initializePlaneRenderer()`

```java
planeRenderer.setVisible(false);
planeRenderer.setShadowReceiver(false);
planeRenderer.setEnabled(false);
```

Plane detection is still **active** (needed for hit-testing when users tap to place models), but the visual overlay showing detected planes is completely disabled.

**File:** `PlaneRenderer.java` → `update()`

Early returns added:
- If `!isEnabled` → return immediately (skip all plane processing)
- If `!isVisible` → skip `getFocusPoint()` calculation

### PlaneVisualizer (Polygon Caching)

**File:** `PlaneVisualizer.java` → `updateRenderableDefinitionForPlane()`

Caches the last polygon boundary in a `FloatBuffer`. On each update, compares the new polygon with the cached one element-by-element. If unchanged, skips the expensive geometry rebuild (vertex generation, triangulation, buffer upload).

**Why it matters:** ARCore updates plane boundaries frequently even when the polygon hasn't actually changed shape. Without caching, geometry is rebuilt every frame for every tracked plane.

---

## Frame Update Deduplication

### BaseArFragment Timestamp Check

**File:** `BaseArFragment.java` → `onUpdate()`

```java
long ts = frame.getTimestamp();
if (ts == lastOnUpdateTimestamp) {
    return;  // Same frame, skip
}
lastOnUpdateTimestamp = ts;
```

`onUpdate()` is called every Choreographer frame, but ARCore only delivers new camera frames at ~30fps. This check skips the `getUpdatedTrackables()` call when the frame hasn't changed, avoiding redundant work.

### ArSceneView Light Estimation Skip

Light estimation is disabled at the ARCore config level (`LightEstimationMode.DISABLED`), and the `lightEstimationEnabled` flag is set to `false` in ArSceneView. This skips the entire light probe / directional light estimation block in `onBeginFrame()`.

---

## Render-Only Passes (60fps)

This is the most important performance optimization. See [AR_CAMERA_ARCHITECTURE.md](AR_CAMERA_ARCHITECTURE.md#the-renderonly-flag) for full details.

**Summary:** On frames where ARCore has no new camera data (`!updated`), we call `doRender()` without `doUpdate()`. This re-presents the last valid camera frame to the display at 60fps without traversing the scene graph, updating planes, or processing trackables.

**Files involved:**
- `ArSceneView.java` → sets `renderOnly = true` when `!updated`
- `SceneView.java` → checks `renderOnly` in `doFrameNoRepost()`, calls `doRender()` alone

---

## App-Level Optimizations

### Memory & Allocation
- `ARActivity` reuses a `float[] distanceResults` field instead of allocating per location check
- Location polling stops (`Handler.removeCallbacks`) once the target landmark is reached
- `WalletManager` uses `getApplicationContext()` to prevent activity context leaks
- Clipboard cleanup Handler extracted to class field, canceled in `onDestroy()`

### Firestore
- `getMissionProgress()` uses `.limit(TOTAL_LANDMARKS)` to cap query reads at 5

### Threading
- `PolygonService` uses `ExecutorService.newSingleThreadExecutor()` (not raw `Thread()`)
- All Firebase callbacks dispatch to `runOnUiThread()` for UI updates
- `WalletManager.getInstance()` is `synchronized` for thread-safe lazy init

### RecyclerView
- `setHasFixedSize(true)` — layout doesn't change with adapter content
- `setItemViewCacheSize(arHelpers.size())` — caches all 5 items
- `setHasStableIds(true)` + `getItemId()` — enables efficient animations

### Layout
- Mission list not wrapped in `NestedScrollView` (which disables recycling)
- `ShapeableImageView` with circle style instead of nested `CardView`

---

## Build Configuration

**File:** `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4096m
org.gradle.parallel=true
org.gradle.caching=true
```

**Release builds:**
- R8 minification enabled (`minifyEnabled true`, `shrinkResources true`)
- ProGuard strips `Log.d()` and `Log.v()` calls

---

## Measuring Performance

### GPU Rendering (from adb)

```powershell
adb shell dumpsys gfxinfo com.wheic.arapp
```

Look for:
- **Janky frames** — should be < 5%
- **50th/90th/95th/99th percentile** frame times

### SurfaceFlinger Frame Timing

```powershell
adb shell dumpsys SurfaceFlinger --latency "SurfaceView[com.wheic.arapp/com.wheic.arapp.ARActivity]"
```

Shows per-frame timestamps. Calculate deltas to verify ~16.7ms (60fps) cadence.

### Display Refresh Rate

```powershell
adb shell dumpsys SurfaceFlinger | Select-String "refresh-rate"
```

### Logcat Filtering

```powershell
adb logcat -s "ArSceneView" "CameraStream" "ExternalTexture" "Renderer"
```

Note: On some vendor ROMs (Oppo ColorOS, etc.), only `Log.e()` level messages are visible from user-space apps. This is why our debug logging uses `Log.e()`.

---

## What NOT to Change

1. **Do NOT change `onBeginFrame()` to `return true`** — this breaks the camera on multiple devices. Use the `renderOnly` flag instead. See [AR_CAMERA_ARCHITECTURE.md](AR_CAMERA_ARCHITECTURE.md#known-pitfalls).

2. **Do NOT re-enable depth mode** unless targeting high-end devices only. The ML depth provider floods logcat with errors and adds significant CPU overhead.

3. **Do NOT enable FrameRateOptions or DisplayInfo** on Filament's Renderer. These were tested and caused rendering issues. The `renderOnly` flag achieves 60fps without them.

4. **Do NOT remove the plane detection** (only the plane *rendering* is disabled). Plane detection is required for hit-testing when users tap to place character models.
