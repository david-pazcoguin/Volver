# Volver

Volver is an Android augmented-reality tour guide app. Users sign in, browse location-based AR missions, and—when physically near a target—place and interact with 3D models in the real world. The app uses **ARCore** (with **Geospatial API**), **Sceneform** (Filament/GLB), and **Firebase** (Auth, Firestore, Cloud Functions) for the backend.

---

## Features

- **User authentication** — Register and log in via Firebase Auth; session stored in app (SharedPreferences).
- **Location-based AR** — AR is enabled only when the device is within a configurable radius (default 50 m) of the mission's coordinates, verified via ARCore Geospatial API.
- **AR model viewer** — Tap on a detected plane to place a 3D model; tap the model to hear a spoken description (Text-to-Speech).
- **Mission list** — Home screen shows missions (e.g. Fort Santiago, San Agustin Church) with name and coordinates; tapping one opens the AR screen.
- **Dashboard** — Quick access to Settings and About Us.
- **Account management** — Update first name, last name, and password via Firebase; logout clears local session and signs out of Firebase Auth.
- **Blockchain NFT reward** — Complete all 5 missions to earn a Polygon NFT passport via smart contract whitelisting.

---

## How It Works

### App flow

1. **LoginActivity** (launcher) — Login via Firebase Auth (`signInWithEmailAndPassword` with `username@volver.app`). On success, username is saved in SharedPreferences and the app navigates to Home.
2. **HomeActivity** — Lists AR missions (from `ARHelper`). Dashboard icon opens a bottom sheet: Settings, About Us.
3. **ARActivity** — Uses ARCore Geospatial API to check proximity to the target; if within radius, user can tap a plane to place the GLB model via Earth anchor. Tapping the model triggers TTS for the description.
4. **SettingActivity** — Shows user info (from Firestore `users/{uid}`), My Account (AccountSettingActivity), and Logout (signs out Firebase Auth + clears SharedPreferences).
5. **AccountSettingActivity** — Loads profile from Firestore, then updates first name, last name, and password via Firestore + Firebase Auth `updatePassword()`.
6. **AboutUsActivity** — Static “About Us” screen.

### AR implementation (ARActivity)

- **AR runtime:** ARCore `Session` + Sceneform UX `ArFragment` (camera, plane detection, tap handling).
- **Geospatial API:** `Config.GeospatialMode.ENABLED` for precise outdoor positioning. `GeospatialPose` provides lat/lng/accuracy for proximity checks.
- **Proximity check:** `Earth.getCameraGeospatialPose()` compares to target coordinates; if distance ≤ `ACTIVATION_RADIUS_METERS` (50 m) and accuracy < 10 m, placement is allowed. Falls back to `FusedLocationProviderClient` when geospatial is unavailable.
- **Earth anchors:** Models are placed using `Earth.createAnchor()` for world-locked positioning.
- **Model:** GLB loaded with `ModelRenderable.builder()`, Filament GLB mode.
- **Text-to-Speech:** TTS speaks the `description` string when the placed model is tapped.
- **System checks:** `checkSystemSupport()` ensures OpenGL ES ≥ 3.0 and Android API level supports it.

### Data and backend

- **Local:** Session stored in `SharedPreferences` with name `"Volver"`; key `"username"` holds the logged-in username.
- **Firebase Auth:** Email/password authentication using `username@volver.app` pattern.
- **Firestore:** User profiles at `users/{uid}`, mission completions at `users/{uid}/missions/{missionId}`.
- **Cloud Functions:** `whitelistWallet` callable function handles blockchain whitelisting (verifies auth, missions, then calls the Polygon smart contract via ethers.js).

---

## Backend API (Firebase)

All backend operations use Firebase SDK calls. Configuration is centralized in `FirebaseConfig.java`.

| Operation | Firebase Service | Details |
|-----------|-----------------|--------|
| Login | Firebase Auth | `signInWithEmailAndPassword(username@volver.app, password)` |
| Register | Firebase Auth + Firestore | `createUserWithEmailAndPassword()` + create `users/{uid}` document |
| Check username | Firestore | Query `users` collection where `username == input` |
| Get profile | Firestore | Read `users/{uid}` document |
| Update profile | Firestore + Auth | Update `users/{uid}` fields + `updatePassword()` |
| Complete mission | Firestore | Transaction write to `users/{uid}/missions/{missionId}` |
| Get progress | Firestore | Query `users/{uid}/missions` where `completed == true` |
| Save wallet | Firestore | Update `users/{uid}.walletAddress` |
| Whitelist wallet | Cloud Functions | Call `whitelistWallet` callable function |

---

## Project structure

- **`app/`** — Main Android application (package `com.wheic.arapp`).
  - Activities: Login, Register, Home, ARActivity, Setting, AccountSetting, AboutUs, WalletSetup, NFTClaim.
  - **ARHelper** — Data class for a mission: `MissionName`, `Content`, `Latitude`, `Longitude`, `Model` (resource id).
  - **ARAdapter** — RecyclerView adapter for the mission list; starts ARActivity with mission data in the intent bundle.
  - **FirebaseConfig** — Central Firebase utility (Firestore, Auth, Functions singletons + collection/field constants).
  - **MissionCompletionHelper** — Mission tracking, wallet saving, and whitelist requests via Firebase.
  - **WalletManager** — Polygon wallet management (external or embedded keypair).
- **`functions/`** — Firebase Cloud Functions (Node.js). Contains `whitelistWallet` callable.
- **`contracts/`** — Solidity smart contract (`IntramurosNFT.sol`).
- **`sceneform/`** — Sceneform runtime (from `sceneformsrc/sceneform`): ARCore + Filament/gltfio, scene graph, rendering.
- **`sceneformux/`** — Sceneform UX (from `sceneformux/ux`): ArFragment, TransformableNode, plane discovery UI.

Dependencies (app): Firebase (Auth, Firestore, Functions), Play Services Location, ARCore, Sceneform UX, Web3j, ZXing, Gson. Min SDK 24; OpenGL ES 3.0 and ARCore required.

---

## Setup

1. **Device:** Use an Android device that supports [ARCore](https://developers.google.com/ar/devices).
2. **Android Studio:** Open the project (root directory). Sync Gradle; ensure `sceneform` and `sceneformux` modules resolve (see `settings.gradle`).
3. **Firebase:** Create a Firebase project, enable Email/Password auth, and create a Firestore database. Download `google-services.json` into `app/`. See `BLOCKCHAIN_SETUP.md` Part 1 for full details.
4. **Cloud Functions:** Deploy with `firebase deploy --only functions`. Set Polygon secrets with `firebase functions:config:set`. See `BLOCKCHAIN_SETUP.md` Part 1.
5. **Security rules:** Deploy with `firebase deploy --only firestore:rules`.
6. **AR targets:** To use per-mission coordinates and descriptions, in **ARActivity** read the intent extras (`MissionName`, `Content`, `Latitude`, `Longitude`) from the bundle and set `TARGET_LATITUDE`, `TARGET_LONGITUDE`, `description`, and optionally the model resource from `ARHelper.getModel()`.
7. **Optional:** Add more missions in **HomeActivity** (list of `ARHelper`) and corresponding GLB assets in `res/raw/`.

---

## Configuration summary

| What | Where |
|------|--------|
| Firebase project config | `app/google-services.json` |
| Firebase utility class | `FirebaseConfig.java` (collections, fields, singletons) |
| Cloud Function secrets | `firebase functions:config:set polygon.*` |
| Firestore security rules | `firestore.rules` |
| Default AR target coordinates | `ARActivity.java` (`TARGET_LATITUDE`, `TARGET_LONGITUDE`) |
| Activation radius (meters) | `ARActivity.java` (`ACTIVATION_RADIUS_METERS`, 50f) |
| Geospatial accuracy threshold | `ARActivity.java` (10 m) |
| Mission list (name, lat, long, model) | `HomeActivity.java` (`arHelpers` list) |
| Session (logged-in user) | SharedPreferences `"Volver"`, key `"username"` |

---

## License

See [LICENSE](LICENSE) (MIT).
