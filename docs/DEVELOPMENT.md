# Development Guide

## Code Conventions

### Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Activities | PascalCase + `Activity` suffix | `HomeActivity`, `ARActivity` |
| Helpers/Services | PascalCase + purpose suffix | `MissionCompletionHelper`, `PolygonService` |
| Layout files | snake_case + `_activity` or `_layout` | `home_activity.xml`, `ar_item_layout.xml` |
| View IDs | camelCase with type prefix | `tvMissionName`, `cardViewLogin`, `txtPassword` |
| Constants | UPPER_SNAKE_CASE | `ACTIVATION_RADIUS_METERS`, `TOTAL_LANDMARKS` |
| Methods | camelCase | `loadMissionProgress()`, `saveWalletAddress()` |

### Patterns

- **Firebase access**: use `FirebaseConfig` for all Firestore/Auth/Functions instances and collection/field name constants. Never hardcode collection names.
- **Mission operations**: use `MissionCompletionHelper` for all Firestore mission reads/writes. Activities should not call Firestore directly for mission data.
- **Wallet operations**: use the `WalletManager` singleton for all wallet state.
- **Blockchain operations**: use `PolygonService` for transaction building. Contract address, RPC URL, and chain ID come from `BuildConfig`.
- **Error messages**: never expose exception details (`e.getMessage()`) in user-facing Toasts.
- **Callbacks**: use the `CompletionCallback` (onSuccess/onError) and `ProgressCallback` (onResult/onError) interfaces defined in `MissionCompletionHelper`.

### File Organization

- One activity per file, one helper/service per file
- Activities handle UI binding and navigation only; business logic goes in helpers/services
- Constants defined as `private static final` in the class that owns them

## Adding a New Mission

1. **Define the mission** in `HomeActivity.buildMissionList()`:

```java
arHelpers.add(new ARHelper(
    "Mission Display Name",
    "Description text shown in the content view.",
    14.589000,                    // Latitude of the landmark
    120.975000,                   // Longitude of the landmark
    "unique_mission_id",          // Firestore document ID (snake_case)
    "Character Name",             // Shown in AR overlay
    "Character's spoken dialogue. Read aloud by TTS.",
    "model_filename"              // Must match a .glb file in res/raw/
));
```

2. **Add the mission image**: place a JPEG/PNG in `app/src/main/res/drawable/` and add a case in `ARAdapter.getMissionImageResource()`.
3. **Add the 3D model**: place the GLB in `app/src/main/res/raw/` named exactly as `modelFileName`.
4. **Update the mission count**: `MissionCompletionHelper.TOTAL_LANDMARKS` and `REQUIRED_MISSIONS` in `functions/index.js`, then `firebase deploy --only functions`.

## Adding New 3D Models

- Download humanoid characters with idle animations from [Mixamo](https://mixamo.com) (free with an Adobe account). Export as FBX.
- Convert FBX → GLB via Blender (File → Import FBX → Export glTF 2.0 → glTF Binary) or an online converter.
- Relic/prop models are authored in Blender (source `.blend` files live in `3d-assets/` where applicable).

## Deployment Checklist

- [ ] Upload final souvenir artwork to IPFS (Pinata/NFT.Storage) and note the metadata CID
- [ ] Call `setTokenUri("ipfs://<cid>")` on the deployed contract (owner-only)
- [ ] Verify `gradle.properties` chain values match the target network (Amoy `80002L` / mainnet `137L`)
- [ ] Update Cloud Function config for the target network (`polygon.contract_address`, `polygon.rpc_url`, `polygon.owner_key`) and `firebase deploy --only functions`
- [ ] Fund the owner wallet with enough POL for expected mint volume (~0.01 POL per mint)
- [ ] Verify `google-services.json` and `local.properties` are NOT committed
- [ ] Run the release build: `./gradlew :app:assembleRelease`
- [ ] Verify R8 is stripping `Log.d()` / `Log.v()` (check the ProGuard mapping)
- [ ] Test the full flow: register → complete all missions → wallet setup → mint souvenir (gasless)

### Firebase Deployment

```bash
firebase deploy --only firestore:rules   # Rules
firebase deploy --only functions         # Cloud Functions
```

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
| AR doesn't activate at location | Check the device supports ARCore Geospatial. Needs GPS + clear sky. Increase `ACTIVATION_RADIUS_METERS` for testing |
| "Model not found" fallback | Place the correct `.glb` in `res/raw/` with the exact filename from `HomeActivity` |
| Firebase Auth fails | Verify `google-services.json` is in `app/` and the Email/Password provider is enabled |
| Cloud Function fails | Check `firebase functions:log`; verify config is set and the owner wallet has POL for gas |
| `mintSouvenir` returns `already-exists` | Wallet already minted — expected for replays |
| `mintSouvenir` returns `failed-precondition` | `allComplete != true`, missing mission docs, or wallet mismatch |
| "Failed to generate wallet" | Fixed by registering the BouncyCastle provider in `WalletManager.generateEmbeddedWallet()` |
| Top UI clipped under status bar on Android 15 | `VolverApplication` applies `WindowInsetsCompat` automatically |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old APK first: `adb uninstall com.wheic.arapp` |
| `Log.d()` not visible in logcat | Some ROMs (Oppo ColorOS) suppress user-app `Log.d()`. Use `Log.e()` for debugging |

For AR camera issues, see [SCENEFORM_MODS.md](SCENEFORM_MODS.md).

### Testing Tips

- **Skip the location requirement**: temporarily raise `ACTIVATION_RADIUS_METERS` in `ARActivity.java` to test AR anywhere
- **Fast-complete missions**: in DEBUG builds, long-press the greeting on `HomeActivity` to auto-complete missions
- **Check Firestore data**: Firebase Console → Firestore → `users/{uid}/missions/`; `souvenirMinted` flips to `true` after a successful mint
- **Check the on-chain mint**: look up the deployed contract on Polygonscan → Events → `SouvenirMinted`

## Future Roadmap

### Path B — Thirdweb In-App Wallet

Replace the email/password + embedded wallet flow with Thirdweb In-App Wallet so tourists can sign in with Google or email OTP and never see a seed phrase. The client ID is already staged in `gradle.properties` (`THIRDWEB_CLIENT_ID` — a public identifier) but not yet consumed.

### Smaller follow-ups

- Move `allComplete` to server-managed (the Cloud Function triple-check already compensates)
- Optional biometric gate before revealing the embedded wallet private key
- BIP-39 mnemonic + backup verification for the embedded wallet path
- Rate limiting on `mintSouvenir` (App Check + Cloud Armor) as public release nears
