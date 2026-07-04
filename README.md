# Volver — AR Heritage Tourism for Intramuros, Manila

**An Android augmented-reality tour guide for Intramuros, the Walled City of Manila.** Walk to historic landmarks, meet 3D historical characters anchored in the real world via the ARCore Geospatial API, collect period relics in AR — and finish the journey with an on-chain NFT souvenir, minted gaslessly on Polygon.

<!--
SCREENSHOTS — drop 3–4 images into docs/media/ and uncomment:
<p align="center">
  <img src="docs/media/home.png" width="24%" alt="Mission list" />
  <img src="docs/media/ar-character.png" width="24%" alt="AR character at landmark" />
  <img src="docs/media/relic.png" width="24%" alt="Relic collectible in AR" />
  <img src="docs/media/nft-claim.png" width="24%" alt="NFT souvenir claim" />
</p>
-->

## Features

- **Location-gated AR missions** at 8 sites — Fort Santiago, Baluarte de San Diego, Casa Manila, Museo de Intramuros, Centro de Turismo, San Agustin Church, Manila Cathedral, and an on-campus test site — activated by proximity via the **ARCore Geospatial API**
- **3D historical characters** placed on real-world planes (Sceneform + Filament), with narration read aloud through Android TextToSpeech
- **Collectible relics** — period artifacts (farol de aceite, peineta, pocket watch, salakot) spawn at fixed GPS positions for players to find and collect in AR
- **Turn-by-turn walking navigation** to each landmark using OSRM routing
- **Hall of Explorers** — a Firestore-backed leaderboard aggregated by Cloud Functions
- **Gasless NFT souvenir** — completing all missions unlocks an ERC-721 mint on Polygon; a Cloud Function signs and pays for the transaction, so users never touch gas or seed phrases
- **In-app wallet** — embedded keypair encrypted with AES-256-GCM via Android Keystore, or connect an external wallet by QR scan
- **Demo AR mode** — try the AR experience anywhere, no travel required

## How It Works

```
Android app ──► Firebase Auth + Firestore (missions, profiles, leaderboard)
     │                        │
     │                        ▼
     └──► mintSouvenir Cloud Function ──► IntramurosSouvenir (ERC-721, Polygon)
          verifies completion server-side,     adminMintTo(userAddress)
          signs with owner wallet, pays gas
```

The client never holds minting authority: Firestore security rules keep souvenir fields server-only, and the Cloud Function independently re-verifies mission completion before any on-chain call. Full details in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SECURITY.md](docs/SECURITY.md).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Platform | Android (Java 11), min SDK 24 / target SDK 35 |
| AR | ARCore 1.44 Geospatial API + a [modified Sceneform fork](docs/SCENEFORM_MODS.md) upgraded to Filament 1.32 |
| Backend | Firebase Auth, Firestore, Cloud Functions (Node.js 24 + ethers.js 6) |
| Blockchain | Solidity ^0.8.20 (OpenZeppelin ERC-721) on Polygon, Web3j 4.9.8 on-device |
| Navigation | OSRM walking routes, Play Services Location |
| Build | Gradle 9.1, AGP 9.0, R8 minification |

## Getting Started

**Prerequisites:** Android Studio (Gradle 9.1+), an [ARCore-supported device](https://developers.google.com/ar/devices) (the Geospatial API does not work on emulators), a Firebase project, and Node.js 24 for Cloud Functions.

```bash
git clone https://github.com/david-pazcoguin/Volver.git
```

1. **Firebase** — create a project at [console.firebase.google.com](https://console.firebase.google.com), register the package `com.wheic.arapp`, enable Email/Password auth and Firestore, then download **your own** `google-services.json` into `app/` (it is gitignored — never committed).
2. **API key** — add `MAPS_API_KEY=<your key>` to `gradle.properties` (a Google Cloud key with the ARCore API enabled, restricted to your package + signing certificate).
3. **Blockchain config** — set `NFT_CONTRACT_ADDRESS`, `POLYGON_RPC_URL`, and `POLYGON_CHAIN_ID` in `gradle.properties`; store the contract owner key in Firebase Functions secrets (never in the repo).
4. **Deploy** — `firebase deploy --only firestore:rules,functions`, then `./gradlew :app:installDebug`.

Full setup, deployment checklist, and troubleshooting: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Documentation

| Topic | Where |
|-------|-------|
| Architecture, user flow, class reference | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Sceneform/Filament fork modifications | [docs/SCENEFORM_MODS.md](docs/SCENEFORM_MODS.md) |
| Firestore data model & security rules | [docs/DATA_MODEL.md](docs/DATA_MODEL.md) |
| Security standards | [docs/SECURITY.md](docs/SECURITY.md) |
| Performance optimizations | [docs/PERFORMANCE.md](docs/PERFORMANCE.md) |
| Development guide & troubleshooting | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Domain deep-dives (AR camera, geospatial, Firebase, blockchain, UI, build) | [ai-workflows/](ai-workflows/) |

## Credits

Built as a capstone project at **Lyceum of the Philippines University** by
**David Pazcoguin** and **Emanuel Manguera** (co-developers), advised by **Dr. Caballero**.

## License

[MIT](LICENSE)
