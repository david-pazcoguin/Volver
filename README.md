# Volver

Volver is an Android augmented-reality tour guide app. Users sign in, browse location-based AR missions, and—when physically near a target—place and interact with 3D models in the real world. The app uses **ARCore**, **Sceneform** (Filament/GLB), and a PHP backend for auth and account data.

---

## Features

- **User authentication** — Register and log in; session stored in app (SharedPreferences).
- **Location-based AR** — AR is enabled only when the device is within a configurable radius (default 50 m) of the mission’s coordinates.
- **AR model viewer** — Tap on a detected plane to place a 3D model; tap the model to hear a spoken description (Text-to-Speech).
- **Mission list** — Home screen shows missions (e.g. Fort Santiago, San Agustin Church) with name and coordinates; tapping one opens the AR screen.
- **Dashboard** — Quick access to Settings and About Us.
- **Account management** — Update first name, last name, and password via backend; logout clears local session.

---

## How It Works

### App flow

1. **LoginActivity** (launcher) — Login or go to Register. On success, username is saved in SharedPreferences under the key `"Volver"` and the app navigates to Home.
2. **HomeActivity** — Lists AR missions (from `ARHelper`). Dashboard icon opens a bottom sheet: Settings, About Us.
3. **ARActivity** — Requests location, checks distance to the target; if within radius, user can tap a plane to place the GLB model. Tapping the model triggers TTS for the description.
4. **SettingActivity** — Shows user info (from backend), My Account (AccountSettingActivity), and Logout (clears SharedPreferences and returns to Login).
5. **AccountSettingActivity** — Loads profile via `URL_HOME`, then updates first name, last name, and password via `URL_ACCOUNT_SETTING_UPDATE`.
6. **AboutUsActivity** — Static “About Us” screen.

### AR implementation (ARActivity)

- **AR runtime:** ARCore `Session` + Sceneform UX `ArFragment` (camera, plane detection, tap handling).
- **Plane tap:** `setOnTapArPlaneListener` — on tap, creates an ARCore `Anchor`, then adds a Sceneform `AnchorNode` and `TransformableNode` with the model.
- **Model:** Single GLB loaded with `ModelRenderable.builder()` from `R.raw.san_bartolome_church`, Filament GLB mode.
- **Location gating:** `FusedLocationProviderClient` gets last location; `Location.distanceBetween()` compares to target lat/long; if distance ≤ `ACTIVATION_RADIUS_METERS` (50 m), placement is allowed.
- **Target coordinates:** Currently hardcoded in `ARActivity` (`TARGET_LATITUDE`, `TARGET_LONGITUDE`). The list item (ARAdapter) passes `MissionName`, `Content`, `Latitude`, `Longitude` in the intent, but ARActivity does not yet read them—so all missions use the same coordinates and model until wired up.
- **Text-to-Speech:** TTS speaks the `description` string when the placed model is tapped; `description` is not yet set from the intent `Content`.
- **System checks:** `checkSystemSupport()` ensures OpenGL ES ≥ 3.0 and Android API level supports it.

### Data and backend

- **Local:** Session stored in `SharedPreferences` with name `"Volver"`; key `"username"` holds the logged-in username. Logout must clear this same preferences name (see note in SettingActivity).
- **Backend base URL:** All endpoints live under `https://jstnagls.shop/volver/` (see **URLDatabase** below). Requests are POST, `application/x-www-form-urlencoded`; Volley is used for HTTP.

---

## Backend API (URLDatabase)

All URLs are defined in `com.wheic.arapp.URLDatabase`. Configure your server and paths there.

| Constant | URL | Purpose |
|----------|-----|--------|
| `URL_LOGIN` | `.../URL_LOGIN.php` | Login. Params: `username`, `password`. Expected JSON: `user_id` (non-empty string on success). |
| `URL_CHECK_ACCOUNT` | `.../URL_CHECK_ACCOUNT.php` | Check if username exists (e.g. before register). Params: `username`. Expected JSON: `user_id` (empty/null = available). |
| `URL_REGISTER` | `.../URL_REGISTER.php` | Register. Params: `username`, `password`, `first_name`, `last_name`. |
| `URL_HOME` | `.../URL_HOME.php` | Get current user profile. Params: `username`. Expected JSON: `{ "data": [ { "first_name", "last_name", "password" } ] }`. |
| `URL_ACCOUNT_SETTING_UPDATE` | `.../URL_ACCOUNT_SETTING_UPDATE.php` | Update profile. Params: `username`, `first_name`, `last_name`, `password`. |

---

## Project structure

- **`app/`** — Main Android application (package `com.wheic.arapp`).
  - Activities: Login, Register, Home, ARActivity, Setting, AccountSetting, AboutUs.
  - **ARHelper** — Data class for a mission: `MissionName`, `Content`, `Latitude`, `Longitude`, `Model` (resource id).
  - **ARAdapter** — RecyclerView adapter for the mission list; starts ARActivity with mission data in the intent bundle.
  - **URLDatabase** — Static backend URLs.
- **`sceneform/`** — Sceneform runtime (from `sceneformsrc/sceneform`): ARCore + Filament/gltfio, scene graph, rendering.
- **`sceneformux/`** — Sceneform UX (from `sceneformux/ux`): ArFragment, TransformableNode, plane discovery UI.

Dependencies (app): Volley, Play Services Location, Sceneform UX (which pulls in Sceneform and ARCore). Min SDK 24; OpenGL ES 3.0 and ARCore required.

---

## Setup

1. **Device:** Use an Android device that supports [ARCore](https://developers.google.com/ar/devices).
2. **Android Studio:** Open the project (root directory). Sync Gradle; ensure `sceneform` and `sceneformux` modules resolve (see `settings.gradle`).
3. **Backend:** Deploy the PHP scripts expected by `URLDatabase` (login, register, check account, home/profile, account update). Point `URLDatabase` to your base URL if different from `https://jstnagls.shop/volver/`.
4. **AR targets:** To use per-mission coordinates and descriptions, in **ARActivity** read the intent extras (`MissionName`, `Content`, `Latitude`, `Longitude`) from the bundle and set `TARGET_LATITUDE`, `TARGET_LONGITUDE`, `description`, and optionally the model resource from `ARHelper.getModel()`.
5. **Optional:** Add more missions in **HomeActivity** (list of `ARHelper`) and corresponding GLB assets in `res/raw/`.

---

## Configuration summary

| What | Where |
|------|--------|
| Backend base URL and endpoints | `app/.../URLDatabase.java` |
| Default AR target coordinates | `ARActivity.java` (`TARGET_LATITUDE`, `TARGET_LONGITUDE`) |
| Activation radius (meters) | `ARActivity.java` (`ACTIVATION_RADIUS_METERS`, 50f) |
| Mission list (name, lat, long, model) | `HomeActivity.java` (`arHelpers` list) |
| Session (logged-in user) | SharedPreferences `"Volver"`, key `"username"` |

---

## License

See [LICENSE](LICENSE) (MIT).
