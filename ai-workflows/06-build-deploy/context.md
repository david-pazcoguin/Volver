# Context — Build & Deploy

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
| Smart Contract | Solidity (OpenZeppelin) | ^0.8.20 |
| QR Scanning | ZXing | 3.5.3 |
| Location | Play Services Location | 21.3.0 |
| UI | Material Components | 1.13.0 |
| Cloud Functions | Node.js + ethers.js | 24 / 6.16.0 |

## Module Structure

```
settings.gradle:
  :app                    → app/
  :sceneform              → sceneformsrc/sceneform/
  :sceneformux            → sceneformux/ux/
```

## Build Commands

### Debug Build
```powershell
.\gradlew :app:assembleDebug
```
Output: `app\build\outputs\apk\debug\app-debug.apk`

### Clean + Build (required after Sceneform changes)
```powershell
.\gradlew :sceneform:clean :app:clean :app:assembleDebug
```

### Release Build
```powershell
.\gradlew :app:assembleRelease
```
Requires signing config. R8 minification enabled.

### Install
```powershell
# Via Gradle
.\gradlew :app:installDebug

# Via ADB
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```

## ADB Commands

- **ADB path**: `C:\Users\dbenj\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Package**: `com.wheic.arapp`
- **Launcher**: `com.wheic.arapp/.LoginActivity`

```powershell
# Check devices
adb devices

# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Force stop + launch
adb shell am force-stop com.wheic.arapp
adb shell am start -n com.wheic.arapp/.LoginActivity

# Logcat (camera/rendering)
adb logcat -s "ExternalTexture:*" "CameraStream:*" "SceneView:*" "Renderer:*"

# Logcat (all app)
adb logcat | Select-String "com.wheic.arapp"

# GPU timing
adb shell dumpsys gfxinfo com.wheic.arapp

# ARCore version
adb shell dumpsys package com.google.ar.core | Select-String "versionName"

# Uninstall (if signature mismatch)
adb uninstall com.wheic.arapp
```

## Material Compilation

matc location: `$env:TEMP\filament-1.32\bin\matc.exe`

```powershell
& "$env:TEMP\filament-1.32\bin\matc.exe" -p mobile -a opengl -o output.matc input.mat
```

7 materials in `sceneformsrc/sceneform/src/main/res/raw/`:
- `sceneform_camera_material.matc`
- `sceneform_opaque_colored_material.matc`
- `sceneform_transparent_colored_material.matc`
- `sceneform_opaque_textured_material.matc`
- `sceneform_transparent_textured_material.matc`
- `sceneform_plane_material.matc`
- `sceneform_plane_shadow_material.matc`

## Configuration

### gradle.properties (BuildConfig injection)
```properties
NFT_CONTRACT_ADDRESS=0x...
POLYGON_RPC_URL=https://rpc-amoy.polygon.technology
POLYGON_CHAIN_ID=80002
MAPS_API_KEY=...
org.gradle.jvmargs=-Xmx4096m
org.gradle.parallel=true
org.gradle.caching=true
```

### local.properties (not committed)
```properties
sdk.dir=C\:\\Users\\dbenj\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=your_key
```

## ProGuard / R8

- Release builds: `minifyEnabled true`, `shrinkResources true`
- Strips `Log.d()` and `Log.v()` in release
- Keep rules for: Web3j, Firebase, ARCore, Filament, Gson
- Config: `app/proguard-rules.pro`

## Firebase Deployment

```powershell
# Security rules
firebase deploy --only firestore:rules

# Cloud Functions
cd functions; npm install; cd ..
firebase deploy --only functions

# Both
firebase deploy --only firestore:rules,functions
```

## Common Build Errors

| Error | Fix |
|-------|-----|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.wheic.arapp` then reinstall |
| `Unresolved reference: sceneform` | Check `settings.gradle` includes `:sceneform` |
| `META-INF duplicate` | Already handled in `app/build.gradle` excludes |
| `Namespace not specified` | AGP 9.0 requires `namespace` in each module's `build.gradle` |
| Out of memory | Increase `org.gradle.jvmargs=-Xmx6144m` |

## Performance Measurement

```powershell
# GPU rendering stats
adb shell dumpsys gfxinfo com.wheic.arapp

# Frame timing
adb shell dumpsys SurfaceFlinger --latency "SurfaceView[com.wheic.arapp/com.wheic.arapp.ARActivity]"

# Display refresh rate
adb shell dumpsys SurfaceFlinger | Select-String "refresh-rate"
```

## Debugging Tips

- **Oppo ColorOS**: Only `Log.e()` and native `ALOGE` visible in logcat
- **Samsung One UI**: Standard logcat filtering works normally
- **Black camera screen**: Check AR_CAMERA docs — likely stale frame issue
- **Material load failure**: Recompile `.matc` with matching Filament matc version

## Prerequisites

- Android Studio Ladybug or later
- Java 11
- Android SDK Platform 35
- ARCore-compatible device (emulator does NOT support Geospatial)
- USB debugging enabled
- Firebase CLI: `npm install -g firebase-tools`
- Node.js 24 for Cloud Functions
