# Volver — AR Tour Guide for Intramuros, Manila

Volver is an Android augmented-reality tour guide app for exploring **Intramuros, the Walled City of Manila**. Users log in, browse 5 location-based AR missions, walk to each historic landmark, and interact with 3D character models in the real world. Completing all 5 missions unlocks a **Polygon ERC-721 NFT souvenir** — a permanent on-chain record of the journey, minted gaslessly by the app on the user's behalf.

---

## Table of Contents

1. [Features](#features)
2. [Architecture Overview](#architecture-overview)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Sceneform / Filament Modifications](#sceneform--filament-modifications)
6. [Getting Started](#getting-started)
7. [Configuration Reference](#configuration-reference)
8. [Firestore Data Model](#firestore-data-model)
9. [Security Standards](#security-standards)
10. [Performance Standards](#performance-standards)
11. [Code Conventions](#code-conventions)
12. [Adding New Missions](#adding-new-missions)
13. [Adding New 3D Models](#adding-new-3d-models)
14. [Deployment Checklist](#deployment-checklist)
15. [Troubleshooting](#troubleshooting)
16. [Future Roadmap](#future-roadmap)
17. [Further Documentation](#further-documentation)
18. [License](#license)

---

## Features

| Feature | Implementation |
|---------|---------------|
| User authentication | Firebase Auth (email/password) with `username@volver.app` pattern |
| Location-based AR | ARCore Geospatial API — AR activates within 50m of target |
| 3D model placement | Sceneform + Filament (GLB models via Earth Anchors) |
| Character dialogue | Android TextToSpeech reads historical narration aloud |
| Mission tracking | Firestore subcollection `users/{uid}/missions/{missionId}` |
| Progress UI | Real-time counter + NFT claim banner on home screen |
| Personalized greeting | First name fetched from Firestore, cached in SecurePrefs, displayed on HomeActivity |
| Completed-mission indicator | Mission tile desaturated + dimmed with a green ✓ badge once Firestore records completion |
| Treasure chest reward | Animated chest reveals on HomeActivity after all 5 missions; opens NFT claim flow |
| Wallet setup | In-app embedded keypair (AES-256-GCM encrypted, BouncyCastle-registered) or external wallet via QR scan |
| Gasless NFT minting | `mintSouvenir` Cloud Function signs and broadcasts `adminMintTo(userAddress)` — user pays zero gas |
| Android 15 edge-to-edge | `VolverApplication` applies `WindowInsetsCompat` padding on every non-AR activity |
| Debug testing helper | Long-press greeting in DEBUG builds auto-completes 4 of 5 missions |

---

## Architecture Overview

```
┌───────────────────────────────────────────────────────────────┐
│                          Android App                          │
│                                                               │
│  LoginActivity ──► HomeActivity ──► ARActivity                │
│                         │                                     │
│                         ▼                                     │
│  WalletSetupActivity ──► NFTClaimActivity                     │
│       │                        │                              │
│  WalletManager           (calls mintSouvenir CF)             │
│  (AES-256-GCM +          via FirebaseFunctions                │
│   BouncyCastle)                                               │
└──────────────────┬───────────────────┬────────────────────────┘
                   │                   │
         ┌─────────▼──────────┐  ┌─────▼───────────────┐
         │   Firebase Cloud   │  │  Polygon Blockchain │
         │                    │  │                     │
         │  Auth (email/pwd)  │  │  IntramurosSouvenir │
         │  Firestore (data)  │──│  (ERC-721 NFT)      │
         │  Cloud Functions   │  │  adminMintTo()      │
         │  (mintSouvenir)    │  │                     │
         └────────────────────┘  └─────────────────────┘
```

### User Flow

1. **Login/Register** — Firebase Auth signs user in; first name cached in SecurePrefs so the personalized greeting on Home renders instantly.
2. **Home** — Displays 5 missions with progress counter. Completed missions appear desaturated with a green ✓ badge. Dashboard opens Settings/About.
3. **AR Mission** — User walks to the landmark. ARCore Geospatial API checks proximity (≤ 50 m). User taps a detected plane to place the character model. Tapping the model triggers TTS dialogue. After the mission is recorded in Firestore, the Congratulations dialog offers **Return to Home** and the activity finishes automatically.
4. **All 5 Complete** — A treasure chest reveal appears on HomeActivity with a pulse animation.
5. **Wallet Setup** — User either connects an external Polygon wallet (paste/scan QR) or generates an embedded keypair (encrypted with AES-256-GCM via Android Keystore; BouncyCastle provider registered at runtime so key generation works on all Android ROMs).
6. **NFT Claim** — NFTClaimActivity invokes the `mintSouvenir` Cloud Function. The server-side owner wallet verifies 5 missions in Firestore, calls `adminMintTo(userAddress)` on the contract, and pays all gas. The user pays nothing and never submits a transaction.

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 11 |
| Build | Gradle | 9.1.0 |
| Android Gradle Plugin | AGP | 9.0.0 |
| Target SDK | Android | 35 (min 24) |
| AR Runtime | ARCore | 1.44.0 |
| 3D Rendering | Sceneform + Filament | 1.32.0 |
| Auth & Database | Firebase BOM | 34.11.0 |
| Blockchain SDK | Web3j | 4.9.8 |
| Smart Contract | Solidity (OpenZeppelin ERC-721 + Ownable) | ^0.8.20 |
| Deployed Contract | Polygon Amoy testnet | `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8` |
| Embedded Wallet Crypto | BouncyCastle (bcprov-jdk18on, via Web3j) | 1.79 |
| QR Scanning | ZXing | 3.5.3 |
| Location | Play Services Location | 21.3.0 |
| UI | Material Components | 1.13.0 |
| JSON | Gson | 2.13.2 |
| Cloud Functions | Node.js + ethers.js | 24 / 6.16.0 |

---

## Project Structure

```
Volver/
├── app/                          # Android application module
│   ├── build.gradle              # App-level Gradle config (deps, SDK, BuildConfig fields)
│   ├── proguard-rules.pro        # R8/ProGuard rules (log stripping, keep rules)
│   ├── google-services.json      # Firebase config (gitignored)
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions, activities, meta-data
│       ├── java/com/wheic/arapp/
│       │   ├── LoginActivity.java          # Entry point — Firebase Auth login
│       │   ├── RegisterActivity.java       # User registration + Firestore profile
│       │   ├── HomeActivity.java           # Mission list + progress + NFT banner
│       │   ├── ARActivity.java             # AR session, geospatial, model placement, TTS
│       │   ├── ARHelper.java               # Mission data transfer object
│       │   ├── ARAdapter.java              # RecyclerView adapter for mission list
│       │   ├── SettingActivity.java        # Settings screen + logout
│       │   ├── AccountSettingActivity.java # Edit profile (name, password)
│       │   ├── AboutUsActivity.java        # Static about screen
│       │   ├── WalletSetupActivity.java    # Wallet setup (connect or create)
│       │   ├── NFTClaimActivity.java       # NFT minting screen (calls mintSouvenir CF)
│       │   ├── WalletManager.java          # Wallet state + AES-256-GCM + BouncyCastle provider
│       │   ├── PolygonService.java         # Web3j helpers (tx URLs, BuildConfig wiring)
│       │   ├── MissionCompletionHelper.java# Firestore mission tracking + wallet save
│       │   ├── VolverApplication.java      # Firebase init + edge-to-edge insets for Android 15
│       │   ├── SecurePrefs.java            # SharedPreferences wrapper (cached profile + flags)
│       │   └── FirebaseConfig.java         # Firebase singletons + collection/field constants
│       ├── res/
│       │   ├── layout/           # 14 XML layout files
│       │   ├── drawable/         # Mission images, icons, logos
│       │   ├── raw/              # GLB 3D models (san_bartolome_church.glb + per-mission)
│       │   ├── font/             # Montserrat, Aclonica, BalooiBhai, Cabin
│       │   ├── values/           # colors.xml, strings.xml, themes.xml
│       │   └── xml/              # network_security_config.xml
│       └── ...
├── contracts/                    # Solidity smart contract
│   └── IntramurosNFT.sol         # ERC-721 NFT (OpenZeppelin)
├── functions/                    # Firebase Cloud Functions
│   ├── index.js                  # mintSouvenir callable function
│   └── package.json              # Node.js 24, ethers 6.16, firebase-admin 13.6
├── firestore.rules               # Firestore security rules
├── firebase.json                 # Firebase project config
├── sceneformsrc/                 # Sceneform runtime module (Filament/GLB)
├── sceneformux/                  # Sceneform UX module (ArFragment, TransformableNode)
├── ai-workflows/                 # AI workflow folders (domain-specific docs)
│   ├── 01-ar-camera/             # Camera pipeline, Filament, rendering
│   ├── 02-arcore-geospatial/     # Location, AR session, model placement
│   ├── 03-firebase/              # Auth, Firestore, Cloud Functions, security
│   ├── 04-blockchain-nft/        # Smart contract, Web3j, wallets, minting
│   ├── 05-android-ui/            # Activities, navigation, layouts
│   └── 06-build-deploy/          # Build, deploy, ProGuard, debugging
└── .gitignore                    # Excludes secrets, build artifacts, google-services.json
```

### Java Source Files — Quick Reference

| Class | Purpose | Key Dependencies |
|-------|---------|-----------------|
| `LoginActivity` | App entry; Firebase email/password auth | FirebaseAuth, SharedPreferences |
| `RegisterActivity` | User registration + Firestore profile | FirebaseAuth, FirebaseFirestore |
| `HomeActivity` | Mission list + progress + NFT banner | FirebaseFirestore, ARAdapter, SecurePrefs |
| `ARActivity` | AR session, geospatial, model placement, TTS | ARCore, Sceneform, TextToSpeech |
| `ARHelper` | Mission data transfer object | — |
| `ARAdapter` | RecyclerView adapter with stable IDs and mission images | ShapeableImageView, RecyclerView.Adapter |
| `SettingActivity` | User settings, logout | FirebaseAuth, FirebaseFirestore |
| `AccountSettingActivity` | Edit name and password | FirebaseAuth, FirebaseFirestore |
| `AboutUsActivity` | Static info screen | — |
| `WalletSetupActivity` | Multi-step wallet setup (connect or create) | WalletManager, MissionCompletionHelper, ZXing |
| `NFTClaimActivity` | Calls `mintSouvenir` Cloud Function; shows tx result | FirebaseFunctions, WalletManager, SecurePrefs |
| `WalletManager` | Singleton; generates/stores/encrypts wallet keypairs; registers BouncyCastle provider | Android Keystore, Web3j ECKeyPair, BouncyCastle |
| `PolygonService` | Blockchain config + explorer URL helpers (BuildConfig consumer) | Web3j, BuildConfig |
| `MissionCompletionHelper` | Firestore CRUD for missions and wallet address | FirebaseFirestore, FirebaseFunctions |
| `VolverApplication` | Firebase init + Android 15 edge-to-edge insets via ActivityLifecycleCallbacks | FirebaseApp, WindowInsetsCompat |
| `SecurePrefs` | Thin wrapper over SharedPreferences for cached first name, chest/NFT flags | SharedPreferences |
| `FirebaseConfig` | Centralized Firebase instance access + field constants | FirebaseAuth, Firestore, Functions |

---

## Sceneform / Filament Modifications

This project uses a **heavily modified fork of Google Sceneform** upgraded from Filament ~1.4 to **Filament 1.32.0**. The original Sceneform 1.15 SDK is no longer maintained by Google. These modifications are critical for the AR camera to work.

> **⚠️ Read [ai-workflows/01-ar-camera/context.md](ai-workflows/01-ar-camera/context.md) before modifying any rendering code.**

### What Was Changed and Why

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

### Camera Material

The camera background uses a custom Filament material compiled with `matc` v1.32.0. See [ai-workflows/01-ar-camera/context.md](ai-workflows/01-ar-camera/context.md) for material details.

### Key Rule

**Never change `onBeginFrame()` to `return true`.** This causes the camera to go black because `doUpdate()` runs on stale ARCore frames. The `renderOnly` flag is the correct solution for 60fps rendering.

---

## Getting Started

### Prerequisites

- **Android Studio** Ladybug or later (with Gradle 9.1+ support)
- **Android device** with [ARCore support](https://developers.google.com/ar/devices) (emulator does not support geospatial)
- **Firebase project** with Email/Password Auth enabled + Firestore database
- **Node.js 24** for Cloud Functions deployment
- **Firebase CLI** (`npm install -g firebase-tools`)

### Step 1 — Clone and Open

```bash
git clone <repository-url>
```

Open the root `Volver/` folder in Android Studio. Let Gradle sync complete.

### Step 2 — Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Register Android app with package name `com.wheic.arapp`
3. Download `google-services.json` → place in `app/`
4. Enable **Email/Password** under Authentication → Sign-in method
5. Create **Firestore** database in production mode (rules are pre-written)

### Step 3 — Deploy Firebase Services

Deploy the rules and Cloud Function, then set the Cloud Function config (the owner wallet pays gas for every mint):

```bash
firebase deploy --only firestore:rules,functions

firebase functions:config:set \
  polygon.owner_key="0xYOUR_OWNER_PRIVATE_KEY" \
  polygon.contract_address="0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8" \
  polygon.rpc_url="https://rpc-amoy.polygon.technology"
```

### Step 4 — Configure Build Properties

Set build properties in `gradle.properties` (or as environment variables):

```properties
NFT_CONTRACT_ADDRESS=0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8
POLYGON_RPC_URL=https://rpc-amoy.polygon.technology
POLYGON_CHAIN_ID=80002L
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY

# Reserved for Path B (Thirdweb In-App Wallet migration — not yet consumed):
THIRDWEB_CLIENT_ID=14483272c69d9087fc22542a79294900
```

### Step 5 — Deploy Smart Contract

See [ai-workflows/04-blockchain-nft/context.md](ai-workflows/04-blockchain-nft/context.md) for the complete deployment guide covering MetaMask setup, Polygon Amoy testnet, Remix IDE deployment, IPFS metadata upload, and mainnet migration.

### Step 6 — Build and Run

```bash
./gradlew :app:assembleDebug
# or
./gradlew :app:installDebug
```

---

## Configuration Reference

| Setting | Location | Default |
|---------|----------|---------|
| NFT contract address | `gradle.properties` → `NFT_CONTRACT_ADDRESS` | `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8` (Amoy) |
| Polygon RPC URL | `gradle.properties` → `POLYGON_RPC_URL` | `https://rpc-amoy.polygon.technology` |
| Polygon chain ID | `gradle.properties` → `POLYGON_CHAIN_ID` | `80002L` (Amoy testnet) |
| Thirdweb Client ID (Path B) | `gradle.properties` → `THIRDWEB_CLIENT_ID` | `14483272c69d9087fc22542a79294900` |
| Google Maps API key | `gradle.properties` → `MAPS_API_KEY` | `""` |
| Activation radius | `ARActivity.java` → `ACTIVATION_RADIUS_METERS` | `50.0f` meters |
| Total landmarks | `MissionCompletionHelper.java` → `TOTAL_LANDMARKS` | `5` |

---

## Firestore Data Model

```
users/{uid}
├── username        : string        ← 3–30 chars, alphanumeric + underscore
├── firstName       : string
├── lastName        : string
├── email           : string        ← Immutable after creation
├── createdAt       : timestamp     ← Immutable after creation
├── walletAddress   : string        ← Polygon wallet address (optional)
├── allComplete     : boolean       ← True when all 5 missions done
├── souvenirMinted  : boolean       ← Server-set by mintSouvenir CF on success
├── souvenirTxHash  : string        ← Server-set — mint transaction hash
├── souvenirTokenId : string        ← Server-set — ERC-721 token ID (from event log)
├── souvenirMintedAt: timestamp     ← Server-set — when mint completed
│
└── missions/{missionId}            ← Mission completion subcollection
    ├── completed    : boolean      ← Always true (append-only)
    ├── completedAt  : timestamp    ← Server timestamp, validated in rules
    └── missionId    : string       ← Matches document ID

Mission IDs: fort_santiago, baluarte_san_diego, casa_manila,
             museo_intramuros, centro_turismo
```

### Firestore Rules Summary

| Path | Read | Create | Update | Delete |
|------|------|--------|--------|--------|
| `users/{uid}` | Owner only | Owner + type validation | Owner + field allowlist | Denied |
| `users/{uid}/missions/{id}` | Owner only | Owner + schema validation | Denied | Denied |
| Everything else | Denied | Denied | Denied | Denied |

**Key rule behaviors:**
- `email` and `createdAt` are immutable (excluded from update allowlist)
- Mission creates require `completed == true`, `missionId is string`, `completedAt is timestamp`, and `missionId` is one of the 5 known landmarks
- Client updates are restricted to: `username`, `firstName`, `lastName`, `walletAddress`, `allComplete`
- Souvenir fields (`souvenirMinted`, `souvenirTxHash`, `souvenirTokenId`, `souvenirMintedAt`) are written **server-side only** by `mintSouvenir` via the admin SDK, which bypasses rules. They are deliberately absent from the client update allowlist.

---

## Security Standards

All security measures applied to this project:

### Network & Transport
- **HTTPS-only** — `network_security_config.xml` blocks all cleartext HTTP traffic
- **No `usesCleartextTraffic`** in manifest
- **`allowBackup="false"`** — prevents ADB backup of app data

### Authentication & Session
- Firebase Auth with email/password (server-managed)
- Username validation: regex `^[a-zA-Z0-9_]{3,30}$` enforced on both Login and Register
- Password minimum: 6 characters (Firebase Auth minimum)
- Name fields: max 50 characters
- Session stored in SharedPreferences

### Cloud Function (`mintSouvenir`)
- Requires Firebase Auth (`context.auth` check)
- UID mismatch check (`data.uid !== context.auth.uid`)
- Wallet address validated via `ethers.isAddress()` on both the submitted value and the Firestore-stored value
- Uses the **server-side stored wallet**, not the client-supplied one — client cannot redirect the mint to another address
- Triple verification before on-chain call: `allComplete == true` AND mission subcollection count ≥ 5 AND `souvenirMinted != true`
- Owner private key stored in Firebase functions config / secrets (never in source)
- Error responses sanitized — internal exception details only go to server logs + Crashlytics

### Client / App Hardening
- Handler leak prevented: clipboard runnable is a class field, canceled in `onDestroy()`
- **Button debounce** (2 s cooldown) on wallet confirm and NFT mint buttons
- **Web3j HTTP timeouts**: 15 s connect / 30 s read+write to prevent hung transactions

### Firestore Rules
- Owner-only access (UID-matched)
- Type validation on create (string lengths, required fields)
- Field-level allowlist on update via `diff().affectedKeys().hasOnly()`
- Immutable fields: `email`, `createdAt`
- Append-only missions (no updates or deletes)

### Build & Release
- **R8 minification** enabled for release builds (`minifyEnabled true`, `shrinkResources true`)
- **ProGuard rules** strip `Log.d()` and `Log.v()` in release
- Sensitive config injected via `BuildConfig` fields from `gradle.properties`
- `google-services.json` is gitignored
- Error messages sanitized — no internal exception details shown to users

### Smart Contract
- `onlyOwner` modifier on `adminMintTo()` and `setTokenUri()` — only the server wallet can mint or change metadata
- Server-side double check: `mintSouvenir` Cloud Function verifies Firestore `allComplete` AND queries the mission subcollection count AND rejects if `souvenirMinted == true` — three independent gates before any on-chain call
- Minimal attack surface: contract has no public mint, no whitelist mapping, no claim function — less surface to audit

---

## Performance Standards

All performance optimizations applied to this project. For detailed explanations, see [ai-workflows/01-ar-camera/context.md](ai-workflows/01-ar-camera/context.md) and [ai-workflows/06-build-deploy/context.md](ai-workflows/06-build-deploy/context.md).

### AR Rendering (Sceneform / Filament)
- **Filament renderer**: MSAA off, post-processing off, dithering off, shadows off, HDR quality LOW
- **ARCore config**: Depth disabled, light estimation disabled, horizontal planes only
- **Plane rendering**: Fully disabled (visuals only — detection active for hit-testing)
- **Polygon caching**: PlaneVisualizer skips geometry rebuild when plane boundary unchanged
- **Frame dedup**: BaseArFragment skips `getUpdatedTrackables()` when frame timestamp unchanged
- **Render-only passes**: Re-present camera frames at 60fps without scene graph traversal (via `renderOnly` flag)

### Memory & Allocation
- `ARActivity` reuses a `float[] distanceResults` class field instead of allocating per location check
- `ARActivity` location polling stops (`Handler.removeCallbacks`) once target is reached
- `ARActivity` location handler also cleaned up in `onDestroy()` to prevent leaked callbacks
- `ARActivity` TTS `onInit()` guarded by `isDestroyed()` check — prevents use-after-destroy crashes
- `ARActivity` validates latitude/longitude bounds on intent extras (rejects invalid coordinates)
- `WalletManager` uses `getApplicationContext()` to prevent activity context leaks
- `WalletSetupActivity` clipboard Handler extracted to class field, cleaned up in `onDestroy()`

### Firestore
- `MissionCompletionHelper.getMissionProgress()` uses `.limit(TOTAL_LANDMARKS)` to cap query reads

### Threading
- `PolygonService` uses `ExecutorService.newSingleThreadExecutor()` (not raw `Thread()`)
- All Firebase callbacks dispatch to `runOnUiThread()` for UI updates
- `WalletManager.getInstance()` is `synchronized` for thread-safe lazy initialization

### RecyclerView
- `setItemViewCacheSize(arHelpers.size())` — caches all 5 items (avoids rebinding)
- `setHasStableIds(true)` + `getItemId()` per mission — enables efficient item animations

### Layout
- Mission list no longer wrapped in `NestedScrollView` (which disables RecyclerView recycling)
- `ar_item_layout.xml` uses `ShapeableImageView` with `CircleImageStyle` instead of nested `CardView` for circular images (fewer layers, less overdraw)

### Build
- Gradle JVM heap: `4096m`
- Parallel builds enabled (`org.gradle.parallel=true`)
- Build caching enabled (`org.gradle.caching=true`)

### UI Efficiency
- Shared `TextWatcher` instances per activity (one watcher for all fields instead of per-field duplicates)
- TextWatchers only respond in `afterTextChanged` (not all 3 callbacks)
- `FirebaseUser` cached as local variable where used multiple times

---

## Code Conventions

### Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Activities | PascalCase + `Activity` suffix | `HomeActivity`, `ARActivity` |
| Helpers/Services | PascalCase + purpose suffix | `MissionCompletionHelper`, `PolygonService` |
| Data classes | PascalCase + `Helper` suffix | `ARHelper` |
| Layout files | snake_case + `_activity` or `_layout` | `home_activity.xml`, `ar_item_layout.xml` |
| View IDs | camelCase with type prefix | `tvMissionName`, `cardViewLogin`, `txtPassword` |
| Constants | UPPER_SNAKE_CASE | `ACTIVATION_RADIUS_METERS`, `TOTAL_LANDMARKS` |
| Methods | camelCase | `loadMissionProgress()`, `saveWalletAddress()` |

### Patterns

- **Firebase access**: Use `FirebaseConfig` for all Firestore/Auth/Functions instances and collection/field name constants. Never hardcode collection names.
- **Mission operations**: Use `MissionCompletionHelper` for all Firestore mission reads/writes and whitelist requests. Activities should not call Firestore directly for mission data.
- **Wallet operations**: Use `WalletManager` singleton for all wallet state. Never access SharedPreferences for wallet data directly.
- **Blockchain operations**: Use `PolygonService` for all transaction building. Contract address, RPC URL, and chain ID come from `BuildConfig`.
- **Error messages**: Never expose exception details (`e.getMessage()`, `e.toString()`) in user-facing Toasts. Use generic messages like "Failed to update profile."
- **Callbacks**: Use the `CompletionCallback` (onSuccess/onError) and `ProgressCallback` (onResult/onError) interfaces defined in `MissionCompletionHelper`.

### File Organization

- One activity per file, one helper/service per file
- Activities handle UI binding and navigation only; business logic goes in helpers/services
- Constants defined as `private static final` in the class that owns them
- No utility classes with static methods unless they serve multiple callers (e.g., `MissionCompletionHelper`)

---

## Adding New Missions

To add a 6th (or more) mission:

### 1. Define the mission in HomeActivity

In `HomeActivity.java` → `buildMissionList()`, add a new `ARHelper`:

```java
arHelpers.add(new ARHelper(
    "Mission Display Name",
    "Description text shown in the content view.",
    14.589000,                    // Latitude of the landmark
    120.975000,                   // Longitude of the landmark
    "unique_mission_id",          // Firestore document ID (snake_case)
    "Character Name",             // Shown in AR overlay
    "Character's spoken dialogue. This will be read aloud by TTS.",
    "model_filename"              // Must match a .glb file in res/raw/ (without extension)
));
```

### 2. Add the mission image

Place a JPEG/PNG in `app/src/main/res/drawable/` named to match the mission ID (e.g., `new_landmark.jpg`).

Then add a case in `ARAdapter.java` → `getMissionImageResource()`:

```java
case "unique_mission_id":
    return R.drawable.new_landmark;
```

### 3. Add the 3D model

Place the GLB file in `app/src/main/res/raw/` named exactly as specified in `modelFileName` (e.g., `model_filename.glb`).

### 4. Update the mission count

In `MissionCompletionHelper.java`, update:

```java
private static final int TOTAL_LANDMARKS = 6;  // was 5
```

In `functions/index.js`, update:

```javascript
const REQUIRED_MISSIONS = 6;  // was 5
```

### 5. Deploy

```bash
firebase deploy --only functions
```

Build and test:

```bash
./gradlew :app:assembleDebug
```

---

## Adding New 3D Models

### Source

Download humanoid characters with idle animations from [Mixamo](https://mixamo.com) (free with Adobe account). Export as FBX.

### Convert FBX to GLB

Option A — Online: Use an FBX-to-GLB converter such as Aspose 3D conversion.

Option B — Blender: File → Import FBX → File → Export glTF 2.0 → Format: glTF Binary (.glb)

### Place the Model

Drop the exported `.glb` into `app/src/main/res/raw/`, using the exact filename referenced by the mission's `modelFileName` (lowercase, no extension in code). The app loads a fallback model if the file is missing, so verify the filename matches before building.

---

## Deployment Checklist

### Mainnet / Production Release

- [ ] Upload final souvenir artwork to IPFS (Pinata/NFT.Storage) and note the metadata CID
- [ ] Call `setTokenUri("ipfs://<cid>")` on the deployed contract (owner-only, from Remix or a script) to swap the placeholder for real metadata
- [ ] Deploy a fresh `IntramurosSouvenir` to **Polygon Mainnet** (chain 137) using the same `IntramurosNFT.sol`
- [ ] Transfer ownership of the mainnet contract to a dedicated mint-only wallet (not your personal MetaMask)
- [ ] Fund the mint-only owner wallet with enough POL for expected mint volume (~0.01 POL per mint at current gas)
- [ ] Update `gradle.properties`:
  ```
  NFT_CONTRACT_ADDRESS=0xMainnetContractAddress
  POLYGON_RPC_URL=https://polygon-rpc.com
  POLYGON_CHAIN_ID=137L
  ```
- [ ] Update Cloud Function config for mainnet:
  ```bash
  firebase functions:config:set \
    polygon.contract_address="0xMainnetAddress" \
    polygon.rpc_url="https://polygon-rpc.com" \
    polygon.owner_key="0xMainnetOwnerKey"
  firebase deploy --only functions
  ```
- [ ] Verify `google-services.json` is NOT committed (check `.gitignore`)
- [ ] Verify `local.properties` is NOT committed
- [ ] Verify `gradle.properties` owner keys / client secrets are NOT committed (only the Thirdweb Client ID is safe to commit — it's a public identifier)
- [ ] Run release build:
  ```bash
  ./gradlew :app:assembleRelease
  ```
- [ ] Verify R8 is stripping `Log.d()` / `Log.v()` (check ProGuard mapping)
- [ ] Test full flow: register → 5 missions → wallet setup → mint souvenir (gasless)

### Firebase Deployment

```bash
# Rules
firebase deploy --only firestore:rules

# Cloud Functions
firebase deploy --only functions

# Both
firebase deploy --only firestore:rules,functions
```

---

## Troubleshooting

### Build Issues

| Symptom | Solution |
|---------|----------|
| `Unresolved reference: sceneform` | Ensure `sceneformsrc/` and `sceneformux/` exist and `settings.gradle` includes them |
| `META-INF duplicate` errors | Already handled in `app/build.gradle` `packaging.resources.excludes` |
| `Namespace not specified` | AGP 9.0 requires `namespace` in each module's `build.gradle` |
| Out of memory during build | `gradle.properties` sets `Xmx4096m`; increase if needed |

### Runtime Issues

| Symptom | Solution |
|---------|----------|
| AR doesn't activate at location | Check that device supports ARCore Geospatial. Needs GPS + clear sky. Increase `ACTIVATION_RADIUS_METERS` for testing |
| "Model not found" fallback | Place the correct `.glb` file in `res/raw/` with exact filename from `HomeActivity` |
| Firebase Auth fails | Verify `google-services.json` is in `app/` and Email/Password provider is enabled |
| Cloud Function fails | Check `firebase functions:log`. Verify config is set (`firebase functions:config:get`). Ensure the owner wallet has enough POL for gas |
| `mintSouvenir` returns `already-exists` | Wallet already minted. `souvenirMinted == true` in Firestore and contract holds a token — expected for replays |
| `mintSouvenir` returns `failed-precondition` | Either `allComplete != true`, fewer than 5 mission docs, or wallet mismatch between submitted address and Firestore `walletAddress` |
| "Failed to generate wallet" | Old issue fixed by registering BouncyCastle provider in `WalletManager.generateEmbeddedWallet()`. Rebuild + reinstall if regressed |
| Top UI clipped under status bar on Android 15 | `VolverApplication` applies `WindowInsetsCompat` automatically. If regressed, ensure the activity isn't excluded from the lifecycle callback allowlist |

### AR Camera Issues

| Symptom | Solution |
|---------|----------|
| Camera shows black screen | Do NOT return `true` from `onBeginFrame()`. Check that the `renderOnly` flag is working. See [ai-workflows/01-ar-camera/context.md](ai-workflows/01-ar-camera/context.md) |
| Camera renders at ~30fps (judder) | The `renderOnly` flag may not be working. Check `SceneView.doFrameNoRepost()` has the `else if (renderOnly)` branch |
| Material fails to load | Recompile `.matc` with matching Filament version. See [ai-workflows/06-build-deploy/context.md](ai-workflows/06-build-deploy/context.md) |
| App crashes on `setExternalImage()` | This API was removed in Filament 1.32. Use `importTexture()` instead |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old APK first: `adb uninstall com.wheic.arapp` |
| Log.d() not visible in logcat | Some ROMs (Oppo ColorOS) suppress user-app `Log.d()`/`Log.w()`. Use `Log.e()` for debugging |

### Testing Tips

- **Skip location requirement**: Temporarily set `ACTIVATION_RADIUS_METERS = 50000.0f` in `ARActivity.java` to test AR anywhere
- **Complete 4 of 5 missions fast**: In DEBUG builds, long-press the greeting on HomeActivity to mark 4 missions complete, then walk/drive to finish Casa Manila (or whichever you left out)
- **Check Firestore data**: Firebase Console → Firestore → `users/{uid}/missions/` should show 5 documents; `souvenirMinted` should flip to `true` after a successful mint
- **Check on-chain mint**: Search the deployed contract on [amoy.polygonscan.com](https://amoy.polygonscan.com/address/0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8) → Events tab → `SouvenirMinted`

---

## Future Roadmap

The current shipping architecture (**Path A**) keeps Firebase email/password auth and a locally-generated embedded wallet, and mints gaslessly via the `mintSouvenir` Cloud Function. It is production-ready for a pilot tour. Two tracks are already staged for future versions:

### Path B — Thirdweb In-App Wallet (planned v2)

Replace the email/password + embedded wallet flow with **Thirdweb In-App Wallet** so tourists can sign in with Google or email OTP and never see a seed phrase or wallet UI.

- Client ID is already saved in `gradle.properties` as `THIRDWEB_CLIENT_ID` but not yet consumed.
- Gas Sponsorship is already enabled on the Thirdweb dashboard for the owner project — all policy toggles are permissive while we're on Amoy.
- Migration will:
  - Add the `com.thirdweb:android` SDK to `app/build.gradle`
  - Replace `LoginActivity` / `RegisterActivity` with a Thirdweb connect screen (Google + email OTP recommended for tourist audience)
  - Retire `WalletManager.generateEmbeddedWallet` and Step 2b of `WalletSetupActivity`
  - Optionally route mint calls through Thirdweb Engine instead of the direct Cloud Function path so Thirdweb's paymaster, not our owner wallet, absorbs gas
  - Migrate existing Firebase user docs by keying them to the Thirdweb user identifier while preserving `missions` subcollections and personalization data

### Path C — Mainnet cutover

- Deploy `IntramurosSouvenir` to Polygon mainnet (chain 137)
- Flip `gradle.properties` chain values
- Repoint Cloud Function config to mainnet RPC and a dedicated owner wallet
- Apply the mainnet certificate pin in `network_security_config.xml`
- Publish Play Store release build

### Smaller follow-ups already considered

- Move `allComplete` to server-managed (harden rules so the client can't toggle it — current Cloud Function triple-check already compensates)
- Optional biometric gate before revealing the embedded wallet private key
- BIP-39 mnemonic + mandatory backup verification for the embedded wallet path (only relevant if Path B is deferred)
- Rate limiting on `mintSouvenir` (e.g. App Check + Cloud Armor) once public release is near

---

## Further Documentation

All detailed documentation lives in `ai-workflows/`, organized by domain:

| Domain | Folder | What's Inside |
|--------|--------|---------------|
| AR Camera & Filament | [ai-workflows/01-ar-camera/](ai-workflows/01-ar-camera/) | Camera pipeline, ExternalTexture, renderOnly flag, material compilation, performance settings |
| ARCore & Geospatial | [ai-workflows/02-arcore-geospatial/](ai-workflows/02-arcore-geospatial/) | Location activation, model placement, landmark coordinates, TTS |
| Firebase | [ai-workflows/03-firebase/](ai-workflows/03-firebase/) | Auth, Firestore data model, security rules, Cloud Functions, deployment |
| Blockchain & NFT | [ai-workflows/04-blockchain-nft/](ai-workflows/04-blockchain-nft/) | Smart contract deployment, Web3j, wallet encryption, IPFS metadata, minting flow |
| Android UI | [ai-workflows/05-android-ui/](ai-workflows/05-android-ui/) | Activities, navigation flow, layouts, form validation, naming conventions |
| Build & Deploy | [ai-workflows/06-build-deploy/](ai-workflows/06-build-deploy/) | Build commands, ADB, material compilation, ProGuard, deployment checklist |

Each folder contains `role.md` (AI persona), `context.md` (architecture & constraints), and `checklist.md` (step-by-step procedures).

Additional references:
- [intramuros_3d_character.md](intramuros_3d_character.md) — Character definitions for the 5 AR missions
- [ai-workflows/TODO_FIXES.md](ai-workflows/TODO_FIXES.md) — Remaining manual setup tasks

---

## License

See [LICENSE](LICENSE) (MIT).
