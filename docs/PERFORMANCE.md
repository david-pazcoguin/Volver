# Performance Standards

For detailed explanations, see [ai-workflows/01-ar-camera/context.md](../ai-workflows/01-ar-camera/context.md) and [ai-workflows/06-build-deploy/context.md](../ai-workflows/06-build-deploy/context.md).

## AR Rendering (Sceneform / Filament)

- **Filament renderer**: MSAA off, post-processing off, dithering off, shadows off, HDR quality LOW
- **ARCore config**: depth disabled, light estimation disabled, horizontal planes only
- **Plane rendering**: visuals fully disabled (detection stays active for hit-testing)
- **Polygon caching**: `PlaneVisualizer` skips geometry rebuild when the plane boundary is unchanged
- **Frame dedup**: `BaseArFragment` skips `getUpdatedTrackables()` when the frame timestamp is unchanged
- **Render-only passes**: re-present camera frames at 60fps without scene graph traversal (via the `renderOnly` flag)

## Memory & Allocation

- `ARActivity` reuses a `float[] distanceResults` class field instead of allocating per location check
- `ARActivity` location polling stops (`Handler.removeCallbacks`) once the target is reached, and the handler is cleaned up in `onDestroy()`
- `ARActivity` TTS `onInit()` guarded by an `isDestroyed()` check — prevents use-after-destroy crashes
- `ARActivity` validates latitude/longitude bounds on intent extras
- `WalletManager` uses `getApplicationContext()` to prevent activity context leaks
- `WalletSetupActivity` clipboard Handler extracted to a class field, cleaned up in `onDestroy()`

## Firestore

- `MissionCompletionHelper.getMissionProgress()` uses `.limit(TOTAL_LANDMARKS)` to cap query reads

## Threading

- `PolygonService` uses `ExecutorService.newSingleThreadExecutor()` (not raw `Thread()`)
- All Firebase callbacks dispatch to `runOnUiThread()` for UI updates
- `WalletManager.getInstance()` is `synchronized` for thread-safe lazy initialization

## RecyclerView & Layout

- Stable IDs (`setHasStableIds(true)` + per-mission `getItemId()`) for efficient item animations
- Mission list not wrapped in `NestedScrollView` (which disables RecyclerView recycling)
- `ar_item_layout.xml` uses `ShapeableImageView` instead of nested `CardView` for circular images (fewer layers, less overdraw)

## Build

- Gradle JVM heap: `4096m`; parallel builds and build caching enabled

## UI Efficiency

- Shared `TextWatcher` instances per activity; watchers only respond in `afterTextChanged`
- `FirebaseUser` cached as a local variable where used multiple times
