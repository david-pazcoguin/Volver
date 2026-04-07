# Context — ARCore Geospatial & Location

## Key File

**`app/src/main/java/com/wheic/arapp/ARActivity.java`** — The main AR session Activity.

## How Location Activation Works

1. `ARActivity` receives landmark coordinates via Intent extras (`latitude`, `longitude`)
2. A `Handler` polls `FusedLocationProviderClient` every **10 seconds** (`LOCATION_CHECK_INTERVAL`)
3. Distance is calculated with `Location.distanceBetween()` using a reusable `float[] distanceResults` field
4. When distance ≤ `ACTIVATION_RADIUS_METERS` (50m), the AR experience activates
5. Once activated, location polling stops (`Handler.removeCallbacks`)

## AR Session Flow

1. User taps a mission in `HomeActivity` → `ARActivity` launches with intent extras
2. `ArFragment` creates the ARCore session with the configuration from `getSessionConfiguration()`
3. The session runs, detecting horizontal planes
4. User taps a detected plane → character model is placed as an Earth Anchor
5. User taps the character → TTS reads the historical dialogue
6. Mission is recorded in Firestore via `MissionCompletionHelper`

## Configuration (ArFragment)

```java
config.setDepthMode(Config.DepthMode.DISABLED);
config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
```

**Note**: `PlaneFindingMode` is currently set to `DISABLED` for camera flickering debugging. It needs to be restored to `HORIZONTAL` for production.

## 3D Models

- Models are `.glb` files in `app/src/main/res/raw/`
- `ARActivity.preloadCharacterModel()` uses `getResources().getIdentifier()` to find by filename
- Falls back to `san_bartolome_church.glb` if mission-specific model isn't found
- Target size: under 1MB per model for smooth loading

## The 5 Landmarks

| Mission ID | Location | Character | Model File |
|-----------|----------|-----------|------------|
| `fort_santiago` | Fort Santiago | José Rizal | `rizal_character.glb` |
| `baluarte_san_diego` | Baluarte de San Diego | Antonio Sedeño | `sedeno_character.glb` |
| `casa_manila` | Casa Manila | Imelda Marcos | `marcos_character.glb` |
| `museo_intramuros` | Museo de Intramuros | Martin Tinio Jr. | `tinio_character.glb` |
| `centro_turismo` | Centro de Turismo | St. Ignatius of Loyola | `ignatius_character.glb` |

## Configuration Constants

| Constant | Value | File |
|----------|-------|------|
| `ACTIVATION_RADIUS_METERS` | `50.0f` | `ARActivity.java` |
| `LOCATION_CHECK_INTERVAL` | `10000` ms | `ARActivity.java` |
| `TOTAL_LANDMARKS` | `5` | `MissionCompletionHelper.java` |

## Memory & Lifecycle

- `distanceResults` float array is reused (not allocated per check)
- Location polling stops via `Handler.removeCallbacks` once target is reached
- Location handler cleaned up in `onDestroy()`
- TTS `onInit()` guarded by `isDestroyed()` check to prevent use-after-destroy
- Latitude/longitude bounds validated on intent extras
