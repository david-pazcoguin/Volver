# Volver — Project Instructions

## Project Overview

Volver is an Android AR tour guide app for **Intramuros, Manila**. Users complete 5 location-based AR missions and mint a **Polygon ERC-721 NFT passport**.

## Quick Reference

- **Package**: `com.wheic.arapp`
- **Launcher**: `com.wheic.arapp/.LoginActivity`
- **ADB**: `C:\Users\dbenj\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Build Commands

```powershell
# Standard debug build
.\gradlew :app:assembleDebug

# Clean build (REQUIRED after Sceneform/rendering changes)
.\gradlew :sceneform:clean :app:clean :app:assembleDebug

# Install + launch
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.wheic.arapp
adb shell am start -n com.wheic.arapp/.LoginActivity
```

## Critical Rules

1. **Filament 1.32 version lock** — all `.matc` materials must be compiled with matching `matc` binary. Mismatched versions crash silently.
2. **Never return `true` from `onBeginFrame()`** in ArSceneView without the `renderOnly` flag pattern — causes black camera.
3. **Always clean Sceneform module** after rendering changes — Gradle incremental build misses `sceneformsrc/` changes.
4. **Never expose exception details** in user-facing messages — use generic error text.
5. **Use `FirebaseConfig` constants** for all Firestore collection/field names — never hardcode.
6. **Blockchain config via BuildConfig** — all values injected from `gradle.properties`, not hardcoded in source.

## Domain-Specific Context

For detailed context on any domain, read the corresponding folder in `ai-workflows/`:

| Domain | Folder | When to Read |
|--------|--------|-------------|
| AR Camera & Filament | `ai-workflows/01-ar-camera/` | Touching `sceneformsrc/` or `sceneformux/` |
| ARCore & Geospatial | `ai-workflows/02-arcore-geospatial/` | Location, AR session, model placement |
| Firebase | `ai-workflows/03-firebase/` | Auth, Firestore, Cloud Functions |
| Blockchain & NFT | `ai-workflows/04-blockchain-nft/` | Contracts, Web3j, wallets, minting |
| Android UI | `ai-workflows/05-android-ui/` | Activities, layouts, navigation |
| Build & Deploy | `ai-workflows/06-build-deploy/` | Build errors, deployment, ProGuard |

Each folder contains:
- `role.md` — AI persona and expertise for that domain
- `context.md` — Key files, architecture, constraints, gotchas
- `checklist.md` — Step-by-step procedures for common tasks

## Code Conventions

- One activity per file; business logic in helpers/services
- `MissionCompletionHelper` for all Firestore mission CRUD
- `WalletManager` singleton for all wallet state
- `PolygonService` for all blockchain transactions
- Button debounce (2s) on sensitive operations
- `Log.e()` for debugging — some ROMs suppress `Log.d()`/`Log.w()`

## Legacy

`backend/` contains deprecated PHP files. Do not extend or modify — all backend logic is now in Firebase.
