# Architecture

## System Overview

```
┌──────────────────────────────────────────────────────────────┐
│                        Android App                           │
│                                                              │
│  LoginActivity ──► HomeActivity ──► ARActivity               │
│                        │   (missions, coins, relics)         │
│                        │                                     │
│  WalletSetupActivity ──► NFTClaimActivity                    │
│       │                        │                             │
│  WalletManager           calls mintSouvenir CF               │
│  (AES-256-GCM +          via FirebaseFunctions               │
│   BouncyCastle)                                              │
└──────────────────┬───────────────────┬───────────────────────┘
                   │                   │
         ┌─────────▼──────────┐  ┌─────▼──────────────┐
         │   Firebase Cloud   │  │ Polygon Blockchain │
         │                    │  │                    │
         │  Auth (email/pwd)  │  │ IntramurosSouvenir │
         │  Firestore (data)  │──│ (ERC-721 NFT)      │
         │  Cloud Functions   │  │ adminMintTo()      │
         │  (mintSouvenir,    │  │                    │
         │   leaderboards)    │  │                    │
         └────────────────────┘  └────────────────────┘
```

## User Flow

1. **Login/Register** — Firebase Auth signs the user in; the first name is cached in `SecurePrefs` so the personalized greeting on Home renders instantly.
2. **Home** — Displays the AR missions with a progress counter. Completed missions appear desaturated with a green ✓ badge. The dashboard opens Settings, About, and the Hall of Explorers leaderboard.
3. **AR Mission** — The user walks to the landmark; turn-by-turn walking directions are provided via OSRM routing (`NavigationDirectionManager`). The ARCore Geospatial API checks proximity, then Intramuros coins and relic collectibles (period artifacts such as the farol de aceite, peineta, pocket watch, and salakot) spawn at fixed GPS positions for the user to find and collect through the AR camera. Collected relics can be inspected in a 3D viewer with spin, pinch-to-resize, and model-switch controls.
4. **All Missions Complete** — A treasure chest reveal appears on `HomeActivity` with a pulse animation.
5. **Wallet Setup** — The user either connects an external Polygon wallet (paste/scan QR) or generates an embedded keypair (encrypted with AES-256-GCM via Android Keystore; the BouncyCastle provider is registered at runtime so key generation works on all Android ROMs).
6. **NFT Claim** — `NFTClaimActivity` invokes the `mintSouvenir` Cloud Function. The server-side owner wallet verifies mission completion in Firestore, calls `adminMintTo(userAddress)` on the contract, and pays all gas. The user pays nothing and never submits a transaction.

## Project Structure

```
Volver/
├── app/                          # Android application module
│   ├── build.gradle              # App-level Gradle config (deps, SDK, BuildConfig fields)
│   ├── proguard-rules.pro        # R8/ProGuard rules (log stripping, keep rules)
│   ├── google-services.json      # Firebase config (gitignored — supply your own)
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions, activities, meta-data
│       ├── java/com/wheic/arapp/ # All application classes (see reference below)
│       └── res/
│           ├── layout/           # XML layouts
│           ├── drawable/         # Mission images, icons, logos
│           ├── raw/              # GLB 3D models (relics, coins)
│           ├── font/             # Montserrat, Aclonica, BalooiBhai, Cabin
│           ├── values/           # colors.xml, strings.xml, themes.xml
│           └── xml/              # network_security_config.xml
├── contracts/                    # Solidity smart contract
│   └── IntramurosNFT.sol         # ERC-721 NFT (OpenZeppelin)
├── functions/                    # Firebase Cloud Functions
│   ├── index.js                  # mintSouvenir callable function
│   ├── leaderboards.js           # Hall of Explorers aggregation
│   └── package.json              # Node.js 24, ethers 6, firebase-admin
├── firestore.rules               # Firestore security rules
├── firebase.json                 # Firebase project config
├── sceneformsrc/                 # Modified Sceneform runtime (Filament/GLB)
├── sceneformux/                  # Sceneform UX module (ArFragment, TransformableNode)
├── ai-workflows/                 # Domain-specific development docs
└── docs/                         # Project documentation (this folder)
```

## Java Class Reference

| Class | Purpose | Key Dependencies |
|-------|---------|-----------------|
| `LoginActivity` | App entry; Firebase email/password auth | FirebaseAuth, SecurePrefs |
| `RegisterActivity` | User registration + Firestore profile | FirebaseAuth, FirebaseFirestore |
| `HomeActivity` | Mission list + progress + NFT banner + chest reveal | MissionCompletionHelper, ARAdapter |
| `ARActivity` | AR session, geospatial anchoring, coin/relic spawns and collection | ARCore, Sceneform |
| `ARHelper` | Mission data transfer object | — |
| `ARAdapter` | RecyclerView adapter with stable IDs and mission images | RecyclerView |
| `DemoARActivity` / `DemoArFragment` | Try-AR-anywhere demo mode (no location gate) | Sceneform |
| `NavigationDirectionManager` | Turn-by-turn walking directions via OSRM routing, with debounced route fetches | OkHttp/OSRM |
| `HallOfExplorersActivity` | Leaderboard screen | LeaderboardRepository |
| `LeaderboardRepository` / `LeaderboardEntry` / `LeaderboardLoadResult` | Firestore-backed leaderboard data layer | FirebaseFirestore |
| `ExplorerRankingAdapter` | Leaderboard RecyclerView adapter | RecyclerView |
| `CollectibleItem` / `CollectiblesAdapter` / `RelicModelProfile` | Relic collectible model, gallery adapter, and per-relic AR profiles | RecyclerView |
| `UserProgressStore` | User-scoped local progress for relic slots and collectible counts | SharedPreferences |
| `WalletSetupActivity` | Multi-step wallet setup (connect or create) | WalletManager, ZXing |
| `NFTClaimActivity` | Calls `mintSouvenir` Cloud Function; shows tx result | FirebaseFunctions, WalletManager |
| `WalletManager` | Singleton; generates/stores/encrypts wallet keypairs; registers BouncyCastle provider | Android Keystore, Web3j |
| `PolygonService` | Blockchain config + explorer URL helpers (BuildConfig consumer) | Web3j, BuildConfig |
| `MissionCompletionHelper` | Firestore CRUD for missions and wallet address | FirebaseFirestore, FirebaseFunctions |
| `MissionBypassState` | Debug-build mission bypass state | — |
| `VolverApplication` | Firebase init + Android 15 edge-to-edge insets via ActivityLifecycleCallbacks | FirebaseApp, WindowInsetsCompat |
| `SecurePrefs` | SharedPreferences wrapper (cached profile + flags) | SharedPreferences |
| `FirebaseConfig` | Centralized Firebase instance access + collection/field constants | FirebaseAuth, Firestore, Functions |
| `SettingActivity` / `AccountSettingActivity` / `AboutUsActivity` | Settings, profile editing, about screen | FirebaseAuth, FirebaseFirestore |
| `PasswordToggleHelper` / `NetworkUtils` / `ZoomableImageView` | UI and utility helpers | — |
