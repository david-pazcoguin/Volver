# Checklist — AR Camera & Rendering Tasks

## Modifying the Camera Pipeline

1. Read `ExternalTexture.java` — understand the direct upload path (`useDirectUpload`, `TextureHelper.setBitmap()`, optimized `yuvToArgb()` with bulk row reads)
2. Read `CameraStream.java` — understand `bindTextureToMaterial()`, `recalculateCameraUvsForDirectUpload()`, and `needsDirectUploadUvs` flag
3. Read `ArFragment.java` — understand `selectHighResCameraConfig()` (targets 1280×720 CPU image)
4. Make your changes
5. Clean and rebuild: `.\gradlew :sceneform:clean :app:clean :app:assembleDebug`
6. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
7. Force stop and relaunch: `adb shell am force-stop com.wheic.arapp && adb shell am start -n com.wheic.arapp/.LoginActivity`
8. Check logs: `adb logcat -s "ExternalTexture:*" "CameraStream:*" "StandardArFragment:*" "SceneView:*" "Renderer:*"`

## Recompiling a Material

1. Edit the `.mat` source file
2. Compile with matc 1.32.0:
   ```powershell
   & "$env:TEMP\filament-1.32\bin\matc.exe" -p mobile -a opengl -o sceneformsrc/sceneform/src/main/res/raw/output.matc input.mat
   ```
3. Verify the output file size is reasonable (camera material is ~18KB)
4. Clean build (material changes require clean): `.\gradlew :sceneform:clean :app:clean :app:assembleDebug`

## Getting the matc Compiler

If `%TEMP%\filament-1.32\bin\matc.exe` doesn't exist:
1. Download from GitHub: `https://github.com/google/filament/releases/download/v1.32.0/filament-v1.32.0-windows.tgz` (~508MB)
2. Extract: `tar -xzf filament-v1.32.0-windows.tgz -C $env:TEMP\filament-1.32`
3. matc will be at: `$env:TEMP\filament-1.32\bin\matc.exe`

## Debugging Camera Issues

1. Check logcat for ExternalTexture frame counts:
   ```
   adb logcat -s "ExternalTexture:*"
   ```
   Look for: `frame#N newFrame=true ts=... posts=N fails=N direct=true`
2. If `fails` count is growing → `acquireCameraImage()` is returning null (camera not ready)
3. If `posts` count stops growing → texture upload is stalled
4. If frames post but camera is black → material parameter binding issue (check `CameraStream.bindTextureToMaterial()`)
5. GPU frame timing: `adb shell dumpsys gfxinfo com.wheic.arapp`

## Testing on Device

- **ADB path**: `C:\Users\dbenj\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Package**: `com.wheic.arapp`
- **Launcher**: `com.wheic.arapp/.LoginActivity`
- Navigate: Login → Home → tap a mission → AR camera opens
- Wait at least 10 seconds to check if flickering occurs (it typically starts at ~3s)

## Adding a New Filament Feature

1. Check if the feature requires a material change (e.g., new sampler, new parameter)
2. If yes → edit the `.mat` source and recompile with matc
3. Check if the feature requires a Filament API that exists in 1.32.0
4. Verify the API exists: extract `classes.jar` from `filament-android-1.32.0.aar` in Gradle caches, use `javap` to inspect
5. Test on Mali-G68 specifically — it has known quirks with GL state and OES textures
