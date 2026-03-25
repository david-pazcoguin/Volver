# Build & Deploy Guide

How to build, install, and debug the Volver app on Android devices.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Building the APK](#building-the-apk)
3. [Installing on a Device](#installing-on-a-device)
4. [Multi-Device Setup](#multi-device-setup)
5. [Material Compilation](#material-compilation)
6. [Common Build Errors](#common-build-errors)
7. [Debugging](#debugging)
8. [Firebase Deployment](#firebase-deployment)

---

## Prerequisites

- **Android Studio** Ladybug or later (Gradle 9.1+ support)
- **Java 11** (set in `compileOptions` in `app/build.gradle`)
- **Android SDK** Platform 35, Build Tools matching AGP 9.0.0
- **ARCore-compatible device** — emulator does NOT support Geospatial API
- **USB debugging** enabled on device (Settings → Developer Options → USB Debugging)

### SDK Path

The `local.properties` file should contain your SDK path:

```properties
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
```

---

## Building the APK

### Debug Build

```powershell
.\gradlew :app:assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`

### Clean + Build (recommended after Sceneform changes)

```powershell
.\gradlew :sceneform:clean :app:clean :app:assembleDebug
```

Always clean the Sceneform module when you modify files in `sceneformsrc/` or `sceneformux/` — Gradle's incremental build doesn't always pick up changes in these modules.

### Release Build

```powershell
.\gradlew :app:assembleRelease
```

Requires a signing config in `app/build.gradle`. R8 minification is enabled for release.

---

## Installing on a Device

### Via Gradle (automatic)

```powershell
.\gradlew :app:installDebug
```

### Via ADB (manual)

```powershell
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```

The `-r` flag replaces an existing installation (same signature required).

### Check Connected Devices

```powershell
adb devices
```

Expected output:
```
List of devices attached
SERIAL123    device
```

If the device shows `unauthorized`, accept the USB debugging prompt on the phone.

---

## Multi-Device Setup

When multiple devices are connected, target a specific one:

```powershell
# List devices
adb devices

# Install to a specific device
adb -s <SERIAL_NUMBER> install -r "app\build\outputs\apk\debug\app-debug.apk"
```

### New Device Checklist

1. Enable **Developer Options** (tap Build Number 7 times in Settings → About Phone)
2. Enable **USB Debugging** in Developer Options
3. Connect via USB cable
4. Accept the USB debugging prompt on the phone
5. Verify with `adb devices` — should show `device` (not `unauthorized`)
6. Install the APK

---

## Material Compilation

The camera background uses a custom Filament material that must be compiled with the **exact same version** of `matc` as the Filament runtime (1.32.0).

### matc Location

```
%TEMP%\filament-matc\win\bin\matc.exe
```

### Compiling a Material

```powershell
& "$env:TEMP\filament-matc\win\bin\matc.exe" `
    -o app/src/main/res/raw/camera_stream_material.matc `
    -p mobile `
    camera_material.mat
```

### When to Recompile

- After changing the vertex or fragment shader in the `.mat` source
- After upgrading the Filament version (must use matching `matc`)
- **Never** after a normal code change — the compiled `.matc` is checked into `res/raw/`

> **WARNING:** Using a mismatched `matc` version will silently crash the app at runtime. The material will fail to load and the camera background will be black or missing.

---

## Common Build Errors

### INSTALL_FAILED_UPDATE_INCOMPATIBLE

```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match newer version]
```

**Cause:** A previous APK on the device was signed with a different key (e.g., built on another machine).

**Fix:**
```powershell
adb uninstall com.wheic.arapp
adb install "app\build\outputs\apk\debug\app-debug.apk"
```

### Unresolved reference: sceneform

**Cause:** Sceneform modules not included in the build.

**Fix:** Verify `settings.gradle` includes:
```groovy
include ':sceneform'
project(':sceneform').projectDir = new File('sceneformsrc/sceneform')
include ':ux'
project(':ux').projectDir = new File('sceneformux/ux')
```

### META-INF duplicate errors

Already handled in `app/build.gradle` → `packaging.resources.excludes`. If new duplicates appear, add them to the excludes list.

### Namespace not specified

AGP 9.0 requires a `namespace` declaration in each module's `build.gradle`. Check that all modules (`app`, `sceneform`, `ux`) have it set.

### Out of memory during build

Increase heap in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx6144m
```

---

## Debugging

### Logcat — Camera/Renderer

```powershell
adb logcat -s "ArSceneView" "CameraStream" "ExternalTexture"
```

These tags use `Log.e()` level (see [PERFORMANCE_GUIDE.md](PERFORMANCE_GUIDE.md#measuring-performance) for why).

### Logcat — All App Logs

```powershell
adb logcat | Select-String "com.wheic.arapp"
```

### GPU Frame Timing

```powershell
adb shell dumpsys gfxinfo com.wheic.arapp
```

### Check ARCore Version

```powershell
adb shell dumpsys package com.google.ar.core | Select-String "versionName"
```

### Remote Debugging Tips

- On **Oppo ColorOS**: Only `Log.e()` and native `ALOGE` are visible in logcat. `Log.d()`/`Log.w()` from user apps are suppressed by the ROM.
- On **Samsung One UI**: Standard logcat filtering works normally.
- If the camera shows **black screen**: Check [AR_CAMERA_ARCHITECTURE.md](AR_CAMERA_ARCHITECTURE.md#known-pitfalls) — likely a `doUpdate()` on stale frames issue.

---

## Firebase Deployment

### Firestore Security Rules

```powershell
firebase deploy --only firestore:rules
```

### Cloud Functions

```powershell
cd functions; npm install; cd ..
firebase deploy --only functions
```

### Both at Once

```powershell
firebase deploy --only firestore:rules,functions
```

### Check Cloud Function Logs

```powershell
firebase functions:log
```

### Verify Function Config

```powershell
firebase functions:config:get
```

See [BLOCKCHAIN_SETUP.md](BLOCKCHAIN_SETUP.md) for setting up secrets and deploying the smart contract.
