---
description: "Use when editing blockchain, NFT, wallet, or Polygon code: IntramurosNFT.sol, PolygonService, WalletManager, NFTClaimActivity, WalletSetupActivity."
applyTo: ["contracts/**", "**/PolygonService.java", "**/WalletManager.java", "**/NFTClaimActivity.java"]
---
# Blockchain & NFT Instructions

Read `ai-workflows/04-blockchain-nft/context.md` for full deployment and architecture details.

## Critical Constraints

- **Two wallet modes**: External (address only, MetaMask deep link) and Embedded (AES-256-GCM encrypted keypair)
- **All config from BuildConfig** — contract address, RPC URL, chain ID from `gradle.properties`
- **Private keys encrypted** with Android Keystore AES-256-GCM via `WalletManager`
- **`FLAG_SECURE`** on wallet and NFT screens — no screenshots
- **Clipboard auto-clear** after 30 seconds when copying private key
- **Button debounce** (2s) on wallet confirm and NFT mint
- **Web3j timeouts**: 15s connect / 30s read+write
- **Never hardcode** contract addresses or RPC URLs in Java source
- **Never expose** private keys in logs or error messages
