# Checklist — Android UI Tasks

## Adding a New Activity

1. Create the Activity class in `app/src/main/java/com/wheic/arapp/`
2. Create the layout XML in `app/src/main/res/layout/`
3. Register in `AndroidManifest.xml`
4. Add navigation from the calling Activity via `Intent`
5. Follow the pattern: UI binding + navigation in Activity, business logic in helpers

## Modifying the Mission List

1. Mission data defined in `HomeActivity.java` → `buildMissionList()`
2. Layout: `ar_item_layout.xml`
3. Adapter: `ARAdapter.java`
4. Images: `ARAdapter.getMissionImageResource()` — add a case for new mission IDs
5. Add drawable resource for the mission image

## Adding a New Screen to the Settings Flow

1. Create Activity + layout
2. Register in `AndroidManifest.xml`
3. Add navigation button/item in `SettingActivity`
4. If it accesses Firebase, use `FirebaseConfig` constants

## Modifying Form Validation

1. Validation happens via `TextWatcher` in each Activity
2. Username regex defined in `LoginActivity` and `RegisterActivity`
3. Firebase Auth enforces 6-char minimum password independently
4. Never show `e.getMessage()` in Toasts — use generic error messages

## Testing UI Changes

1. Build: `.\gradlew :app:assembleDebug`
2. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
3. Test navigation flow: Login → Home → Settings → Account → Back
4. Test mission flow: Home → tap mission → AR camera
5. Test wallet flow: Home → NFT banner → Wallet Setup → NFT Claim
