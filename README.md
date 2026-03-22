# Volver — AR Tour Guide for Intramuros, Manila

Volver is an Android augmented-reality tour guide app for exploring **Intramuros, the Walled City of Manila**. Users log in, browse 5 location-based AR missions, walk to each historic landmark, and interact with 3D character models in the real world. Completing all 5 missions unlocks a **Polygon ERC-721 NFT passport** — a permanent on-chain record of the journey.

---

## Table of Contents

1. [Features](#features)
2. [Architecture Overview](#architecture-overview)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Getting Started](#getting-started)
6. [Configuration Reference](#configuration-reference)
7. [Firestore Data Model](#firestore-data-model)
8. [Security Standards](#security-standards)
9. [Performance Standards](#performance-standards)
10. [Code Conventions](#code-conventions)
11. [Adding New Missions](#adding-new-missions)
12. [Adding New 3D Models](#adding-new-3d-models)
13. [Deployment Checklist](#deployment-checklist)
14. [Troubleshooting](#troubleshooting)
15. [License](#license)

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
| Wallet setup | In-app embedded keypair (AES-256-GCM encrypted) or external wallet via QR scan |
| NFT minting | Polygon smart contract via Web3j (embedded) or MetaMask deep link (external) |
| Blockchain whitelisting | Firebase Cloud Function verifies missions → calls `whitelistAddress()` on-chain |

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                        Android App                           │
│                                                              │
│  LoginActivity ──► HomeActivity ──► ARActivity               │
│       │                │                  │                  │
│       │           SettingActivity    MissionCompletionHelper  │
│       │           AccountSetting        │                    │
│       │                              Firestore               │
│       │                                                      │
│  WalletSetupActivity ──► NFTClaimActivity                    │
│       │                        │                             │
│  WalletManager            PolygonService                     │
│  (AES-256-GCM)           (Web3j / MetaMask)                  │
└──────────────────┬───────────────────┬───────────────────────┘
                   │                   │
         ┌─────────▼─────────┐   ┌─────▼──────────────┐
         │   Firebase Cloud   │   │  Polygon Blockchain │
         │                    │   │                     │
         │  Auth (email/pwd)  │   │  IntramurosPassport │
         │  Firestore (data)  │   │  (ERC-721 NFT)      │
         │  Cloud Functions   │───│  whitelistAddress()  │
         │  (whitelistWallet) │   │  claimPassport()     │
         └────────────────────┘   └─────────────────────┘
```

### User Flow

1. **Login/Register** — Firebase Auth signs user in; session cached in SharedPreferences.
2. **Home** — Displays 5 missions with progress counter. Dashboard opens Settings/About.
3. **AR Mission** — User walks to the landmark. ARCore Geospatial API checks proximity (≤ 50m). User taps a detected plane to place the character model. Tapping the model triggers TTS dialogue. Mission is recorded in Firestore.
4. **All 5 Complete** — Home screen shows "Claim NFT" banner.
5. **Wallet Setup** — User either connects an external Polygon wallet (paste/scan QR) or generates an embedded keypair (encrypted via Android Keystore AES-256-GCM).
6. **NFT Claim** — App calls the `whitelistWallet` Cloud Function (which verifies missions and calls the smart contract), then the user mints via Web3j or MetaMask deep link.

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
| Auth & Database | Firebase BOM | 34.10.0 |
| Blockchain SDK | Web3j | 4.9.8 |
| Smart Contract | Solidity (OpenZeppelin) | ^0.8.20 |
| QR Scanning | ZXing | 3.5.3 |
| Location | Play Services Location | 21.3.0 |
| UI | Material Components | 1.12.0 |
| JSON | Gson | 2.11.0 |
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
│       │   ├── NFTClaimActivity.java       # NFT minting screen
│       │   ├── WalletManager.java          # Wallet state + AES-256-GCM encryption
│       │   ├── PolygonService.java         # Web3j transaction building + MetaMask deep link
│       │   ├── MissionCompletionHelper.java# Firestore mission tracking + whitelist requests
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
│   ├── index.js                  # whitelistWallet callable function
│   └── package.json              # Node.js 24, ethers 6.16, firebase-admin 13.6
├── firestore.rules               # Firestore security rules
├── firebase.json                 # Firebase project config
├── sceneformsrc/                 # Sceneform runtime module (Filament/GLB)
├── sceneformux/                  # Sceneform UX module (ArFragment, TransformableNode)
├── BLOCKCHAIN_SETUP.md           # Step-by-step deployment guide
├── FIREBASE_SECURITY_NOTES.md    # Security considerations for Firestore rules
└── .gitignore                    # Excludes secrets, build artifacts, google-services.json
```

### Java Source Files — Quick Reference

| Class | Purpose | Key Dependencies |
|-------|---------|-----------------|
| `LoginActivity` | App entry; Firebase email/password auth | FirebaseAuth, SharedPreferences |
| `RegisterActivity` | Account creation + Firestore profile | FirebaseAuth, FirebaseFirestore |
| `HomeActivity` | Mission list, progress tracking, NFT banner | ARAdapter, MissionCompletionHelper, WalletManager |
| `ARActivity` | AR session, geospatial proximity, model placement, TTS | ARCore, Sceneform, FusedLocationProvider, TextToSpeech |
| `ARHelper` | Data class: mission name, coordinates, character info, model filename | — |
| `ARAdapter` | RecyclerView adapter with stable IDs and mission images | ShapeableImageView, RecyclerView.Adapter |
| `SettingActivity` | User settings, logout | FirebaseAuth, FirebaseFirestore |
| `AccountSettingActivity` | Edit name and password | FirebaseAuth, FirebaseFirestore |
| `AboutUsActivity` | Static info screen | — |
| `WalletSetupActivity` | Multi-step wallet setup (connect or create) | WalletManager, MissionCompletionHelper, ZXing |
| `NFTClaimActivity` | NFT minting UI | PolygonService, WalletManager |
| `WalletManager` | Singleton; generates/stores/encrypts wallet keypairs | Android Keystore, Web3j ECKeyPair |
| `PolygonService` | Builds + sends Polygon transactions | Web3j, BuildConfig |
| `MissionCompletionHelper` | Firestore CRUD for missions, wallet, whitelist requests | FirebaseFirestore, FirebaseFunctions |
| `FirebaseConfig` | Centralized Firebase instance access + field constants | FirebaseAuth, Firestore, Functions |

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

```bash
# Deploy security rules
firebase deploy --only firestore:rules

# Deploy Cloud Functions
cd functions && npm install && cd ..
firebase deploy --only functions
```

### Step 4 — Configure Secrets

Set blockchain secrets for the Cloud Function:

```bash
firebase functions:config:set \
  polygon.owner_key="0xYOUR_OWNER_PRIVATE_KEY" \
  polygon.contract_address="0xYOUR_CONTRACT_ADDRESS" \
  polygon.rpc_url="https://rpc-amoy.polygon.technology"
```

Set build properties in `gradle.properties` (or as environment variables):

```properties
NFT_CONTRACT_ADDRESS=0xYOUR_CONTRACT_ADDRESS
POLYGON_RPC_URL=https://rpc-amoy.polygon.technology
POLYGON_CHAIN_ID=80002
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

### Step 5 — Deploy Smart Contract

See [BLOCKCHAIN_SETUP.md](BLOCKCHAIN_SETUP.md) for the complete step-by-step guide covering MetaMask setup, Polygon Amoy testnet, Remix IDE deployment, IPFS metadata upload, and mainnet migration.

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
| NFT contract address | `gradle.properties` → `NFT_CONTRACT_ADDRESS` | `0x0000...` (placeholder) |
| Polygon RPC URL | `gradle.properties` → `POLYGON_RPC_URL` | `https://rpc-amoy.polygon.technology` |
| Polygon chain ID | `gradle.properties` → `POLYGON_CHAIN_ID` | `80002` (Amoy testnet) |
| Google Maps API key | `gradle.properties` → `MAPS_API_KEY` | `""` |
| Activation radius | `ARActivity.java` → `ACTIVATION_RADIUS_METERS` | `50.0f` meters |
| Location check interval | `ARActivity.java` → `LOCATION_CHECK_INTERVAL` | `10,000` ms |
| Total missions required | `MissionCompletionHelper.java` → `TOTAL_LANDMARKS` | `5` |
| Cloud Function config | `firebase functions:config:set polygon.*` | — |
| Firebase project | `app/google-services.json` | — (gitignored) |
| Session storage | SharedPreferences `"Volver"`, key `"username"` | — |
| Wallet storage | SharedPreferences `"wallet_prefs"` (encrypted) | — |

All blockchain-related values are injected via `BuildConfig` fields at build time, not hardcoded in source. The Cloud Function reads its own secrets from `functions.config().polygon`.

---

## Firestore Data Model

```
users/{uid}                         ← User profile document
├── username       : string         ← 3–30 chars, alphanumeric + underscore
├── firstName      : string
├── lastName       : string
├── email          : string         ← Immutable after creation
├── createdAt      : timestamp      ← Immutable after creation
├── walletAddress  : string         ← Polygon wallet address (optional)
├── allComplete    : boolean        ← True when all 5 missions done
├── whitelisted    : boolean        ← True after Cloud Function whitelists
├── whitelistedAt  : timestamp      ← When whitelisting occurred
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
- Mission creates require `completed == true`, `missionId is string`, `completedAt is timestamp`
- Updates are restricted to: `username`, `firstName`, `lastName`, `walletAddress`, `allComplete`, `whitelisted`, `whitelistedAt`

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
- Session stored in SharedPreferences (cleared on logout + `FirebaseAuth.signOut()`)

### Wallet & Key Security
- Private keys encrypted with **AES-256-GCM** using **Android Keystore** (hardware-backed when available)
- `FLAG_SECURE` on `WalletSetupActivity` and `NFTClaimActivity` (prevents screenshots)
- Clipboard auto-cleared 30 seconds after copying private key
- Handler leak prevented: clipboard runnable is a class field, canceled in `onDestroy()`

### Firestore Rules
- Owner-only access (UID-matched)
- Type validation on create (string lengths, required fields)
- Field-level allowlist on update via `diff().affectedKeys().hasOnly()`
- Immutable fields: `email`, `createdAt`
- Append-only missions (no update/delete)
- Timestamp validation on mission creates

### Cloud Function Security
- Requires Firebase Auth (`context.auth` check)
- UID mismatch check (`data.uid !== context.auth.uid`)
- Wallet address validated via `ethers.isAddress()`
- Double verification: checks `allComplete` flag AND queries mission subcollection count
- Secrets stored in Firebase environment config (never in source)

### Build & Release
- **R8 minification** enabled for release builds (`minifyEnabled true`, `shrinkResources true`)
- **ProGuard rules** strip `Log.d()` and `Log.v()` in release
- Sensitive config injected via `BuildConfig` fields from `gradle.properties`
- `google-services.json` is gitignored
- Error messages sanitized — no internal exception details shown to users

### Smart Contract
- `onlyOwner` modifier on `whitelistAddress()` and `whitelistBatch()`
- `hasMinted` mapping prevents double-minting
- `isWhitelisted` check before `claimPassport()` — only verified users can mint
- Batch whitelist capped at 100 addresses per call (gas protection)

---

## Performance Standards

All performance optimizations applied to this project:

### Memory & Allocation
- `ARActivity` reuses a `float[] distanceResults` class field instead of allocating per location check
- `ARActivity` location polling stops (`Handler.removeCallbacks`) once target is reached
- `WalletManager` uses `getApplicationContext()` to prevent activity context leaks
- `WalletSetupActivity` clipboard Handler extracted to class field, cleaned up in `onDestroy()`

### Threading
- `PolygonService` uses `ExecutorService.newSingleThreadExecutor()` (not raw `Thread()`)
- All Firebase callbacks dispatch to `runOnUiThread()` for UI updates
- `WalletManager.getInstance()` is `synchronized` for thread-safe lazy initialization

### RecyclerView
- `setHasFixedSize(true)` — layout size doesn't change with adapter content
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
| Methods | camelCase | `loadMissionProgress()`, `buildClaimPassportData()` |

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

### File naming

```
rizal_character.glb       ← Fort Santiago (José Rizal)
sedeno_character.glb      ← Baluarte de San Diego (Antonio Sedeño)
marcos_character.glb      ← Casa Manila (Imelda Marcos)
tinio_character.glb       ← Museo de Intramuros (Martin Tinio Jr.)
ignatius_character.glb    ← Centro de Turismo (St. Ignatius of Loyola)
```

Place in `app/src/main/res/raw/`. The app detects them by filename automatically — `ARActivity.preloadCharacterModel()` uses `getResources().getIdentifier()` and falls back to `san_bartolome_church.glb` if the mission-specific model isn't found.

---

## Deployment Checklist

### Before Release Build

- [ ] Replace `PASSPORT_URI` in `IntramurosNFT.sol` with real IPFS metadata CID
- [ ] Deploy smart contract to **Polygon Mainnet** (chain 137)
- [ ] Update `gradle.properties`:
  ```
  NFT_CONTRACT_ADDRESS=0xMainnetContractAddress
  POLYGON_RPC_URL=https://polygon-rpc.com
  POLYGON_CHAIN_ID=137
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
- [ ] Run release build:
  ```bash
  ./gradlew :app:assembleRelease
  ```
- [ ] Verify R8 is stripping `Log.d()` / `Log.v()` (check ProGuard mapping)
- [ ] Test full flow: register → 5 missions → wallet setup → mint NFT

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
| Cloud Function fails | Check `firebase functions:log`. Verify config is set (`firebase functions:config:get`) |
| NFT mint reverts | Verify wallet is whitelisted on-chain. Check Polygonscan for the contract |
| MetaMask doesn't open | MetaMask mobile app must be installed. Deep link format: `metamask.app.link/send/...` |

### Testing Tips

- **Skip location requirement**: Temporarily set `ACTIVATION_RADIUS_METERS = 50000.0f` in `ARActivity.java` to test AR anywhere
- **Check Firestore data**: Firebase Console → Firestore → `users/{uid}/missions/` should show 5 documents
- **Check whitelist**: Search your contract on amoy.polygonscan.com → Events tab → `AddressWhitelisted`

---

## License

See [LICENSE](LICENSE) (MIT).
