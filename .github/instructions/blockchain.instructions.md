---
description: "Use when editing blockchain, NFT, wallet, or Polygon code: IntramurosNFT.sol, PolygonService, WalletManager, NFTClaimActivity, WalletSetupActivity."
applyTo: ["contracts/**", "**/PolygonService.java", "**/WalletManager.java", "**/NFTClaimActivity.java"]
---
# Blockchain & NFT Instructions

Read `ai-workflows/04-blockchain-nft/context.md` for full deployment and architecture details.

## Critical Constraints

- **Gasless Path A** — user pays zero. The `mintSouvenir` Cloud Function signs `adminMintTo` from the owner wallet. Do not reintroduce client-side minting.
- **Deployed contract (Amoy)** — `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8` (`IntramurosSouvenir`, ERC-721 + Ownable)
- **Two wallet modes** — External (address only, pasted/QR) and Embedded (AES-256-GCM encrypted keypair, BouncyCastle provider registered in `WalletManager.generateEmbeddedWallet()`)
- **All Android config from BuildConfig** — contract address, RPC URL, chain ID come from `gradle.properties`
- **Owner private key** — lives in Firebase functions config/secrets (`polygon.owner_key`); never in the app, never in source, never in logs
- **`FLAG_SECURE`** on `WalletSetupActivity` and `NFTClaimActivity`
- **Clipboard auto-clear** after 30 seconds when copying an embedded private key
- **Button debounce** (2s) on wallet confirm and NFT mint
- **Dedup** lives in Firestore (`souvenirMinted`) + Cloud Function triple-check, not in the contract. Do not add `hasMinted` back to the contract unless deliberately reverting Path A.
- **Thirdweb Client ID** (`THIRDWEB_CLIENT_ID` in `gradle.properties`) is reserved for Path B In-App Wallet migration — do not wire it into Path A code paths.
