# Checklist — Build & Deploy Tasks

## Debug Build + Install

1. Build: `.\gradlew :app:assembleDebug`
2. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
3. Launch: `adb shell am start -n com.wheic.arapp/.LoginActivity`

## After Sceneform/Rendering Changes

1. Clean build required:
   ```powershell
   .\gradlew :sceneform:clean :app:clean :app:assembleDebug
   ```
2. Install and test on device
3. Check logcat for rendering errors

## Release Build

1. Verify signing config is set in `app/build.gradle`
2. Build: `.\gradlew :app:assembleRelease`
3. Verify R8 is stripping debug logs (check ProGuard mapping)
4. Test the release APK on device (behavior can differ from debug)

## Deployment Checklist (Before Production)

- [ ] Replace `PASSPORT_URI` in `IntramurosNFT.sol` with real IPFS metadata CID
- [ ] Deploy smart contract to Polygon Mainnet (chain 137)
- [ ] Update `gradle.properties`: contract address, mainnet RPC, chain ID 137
- [ ] Update Cloud Function config for mainnet
- [ ] Verify `google-services.json` is NOT committed
- [ ] Verify `local.properties` is NOT committed
- [ ] Update network security config certificate pin for mainnet RPC
- [ ] Run release build
- [ ] Test full flow: register → 5 missions → wallet setup → mint NFT

## Firebase Deployment

1. Rules: `firebase deploy --only firestore:rules`
2. Functions: `cd functions && npm install && cd .. && firebase deploy --only functions`
3. Verify: `firebase functions:config:get`

## Adding a New Gradle Dependency

1. Add to `app/build.gradle` → `dependencies` block
2. Sync Gradle
3. If it conflicts with existing deps, check for exclusions needed
4. Add ProGuard keep rules in `proguard-rules.pro` if the library uses reflection

## Troubleshooting Build Issues

1. If Gradle sync fails: check `settings.gradle` includes all modules
2. If incremental build misses changes: clean the affected module first
3. If `META-INF` duplicates: add to `packaging.resources.excludes` in `app/build.gradle`
4. If out of memory: increase `org.gradle.jvmargs` in `gradle.properties`
