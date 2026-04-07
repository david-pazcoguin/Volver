# Checklist — ARCore Geospatial Tasks

## Adding a New Landmark

1. Add coordinates and character info in `HomeActivity.java` → `buildMissionList()`:
   ```java
   arHelpers.add(new ARHelper("Name", "Description", lat, lon, "mission_id", "Character", "Dialogue", "model_filename"));
   ```
2. Add mission image in `app/src/main/res/drawable/` matching the mission ID
3. Add image case in `ARAdapter.java` → `getMissionImageResource()`
4. Place the `.glb` model in `app/src/main/res/raw/` (exact filename from step 1)
5. Update `TOTAL_LANDMARKS` in `MissionCompletionHelper.java`
6. Update `REQUIRED_MISSIONS` in `functions/index.js`
7. Deploy Cloud Function: `firebase deploy --only functions`
8. Build and test: `.\gradlew :app:assembleDebug`

## Adjusting Activation Radius

1. Open `ARActivity.java`
2. Change `ACTIVATION_RADIUS_METERS` (default: 50.0f)
3. For testing anywhere: set to `50000.0f`
4. For production: keep at `50.0f` or lower

## Debugging Geospatial Accuracy

1. Check ARCore version: `adb shell dumpsys package com.google.ar.core | Select-String "versionName"`
2. Ensure clear sky visibility (GPS + VPS need it)
3. Check that `MAPS_API_KEY` is set in `local.properties`
4. Monitor distance logs in ARActivity
5. If altitude is NaN, the app handles it — check the distance calculation code

## Restoring Plane Detection (After Camera Fix)

Currently `PlaneFindingMode.DISABLED` for debugging. To restore:
1. Open `ArFragment.java` → `getSessionConfiguration()`
2. Change `PlaneFindingMode.DISABLED` → `PlaneFindingMode.HORIZONTAL`
3. Rebuild and test model placement
