# Context — Blockchain & NFT (Polygon)

## Full Flow

```
User completes 5 AR missions
         ↓
App writes each completion to Firestore (users/{uid}/missions/{missionId})
         ↓
User sets up their Polygon wallet (in-app)
         ↓
App calls the whitelistWallet Cloud Function
         ↓
Cloud Function verifies all 5 missions → calls smart contract → whitelists the wallet
         ↓
User taps "Mint NFT" → pays a tiny gas fee (~$0.01) → NFT appears in their wallet
```

## Smart Contract

**File**: `contracts/IntramurosNFT.sol`

- ERC-721 (OpenZeppelin ^0.8.20)
- `onlyOwner` modifier on `whitelistAddress()` and `whitelistBatch()`
- `hasMinted` mapping prevents double-minting
- `isWhitelisted` check before `claimPassport()`
- Batch whitelist capped at 100 addresses per call
- `PASSPORT_URI` constant — set before deployment, immutable after

## Key Files

| File | Path | Purpose |
|------|------|---------|
| IntramurosNFT.sol | `contracts/IntramurosNFT.sol` | ERC-721 smart contract |
| PolygonService.java | `app/src/main/java/com/wheic/arapp/PolygonService.java` | Web3j transaction building + MetaMask deep link |
| WalletManager.java | `app/src/main/java/com/wheic/arapp/WalletManager.java` | Wallet state + AES-256-GCM encryption |
| WalletSetupActivity.java | `app/src/main/java/com/wheic/arapp/WalletSetupActivity.java` | Multi-step wallet setup UI |
| NFTClaimActivity.java | `app/src/main/java/com/wheic/arapp/NFTClaimActivity.java` | NFT minting UI |
| index.js | `functions/index.js` | Cloud Function: whitelistWallet |

## Wallet Modes

### External Wallet
- User pastes or scans (QR) their existing Polygon wallet address
- App stores the address only — no private key
- Minting happens via MetaMask deep link

### Embedded Wallet
- App generates an `ECKeyPair` via Web3j
- Private key encrypted with AES-256-GCM using Android Keystore
- Stored in SharedPreferences (`wallet_prefs`)
- Minting happens directly via Web3j transaction

## Security

- `FLAG_SECURE` on `WalletSetupActivity` and `NFTClaimActivity` (prevents screenshots)
- Clipboard auto-cleared 30 seconds after copying private key
- Handler leak prevented: clipboard runnable is a class field, canceled in `onDestroy()`
- Button debounce (2s cooldown) on wallet confirm and NFT mint buttons

## Configuration

All blockchain values injected via `BuildConfig` from `gradle.properties`:

| Property | gradle.properties Key | Default |
|----------|----------------------|---------|
| Contract address | `NFT_CONTRACT_ADDRESS` | `0x0000...` (placeholder) |
| RPC URL | `POLYGON_RPC_URL` | `https://rpc-amoy.polygon.technology` |
| Chain ID | `POLYGON_CHAIN_ID` | `80002` (Amoy testnet) |

Cloud Function secrets (via `firebase functions:config:set`):
- `polygon.owner_key` — contract owner private key
- `polygon.contract_address` — deployed contract address
- `polygon.rpc_url` — RPC endpoint

## NFT Metadata (IPFS)

```json
{
  "name": "Intramuros Passport — Walled City Key",
  "description": "Awarded to explorers who completed all 5 Intramuros AR missions.",
  "image": "ipfs://QmXyz123...",
  "attributes": [
    { "trait_type": "Edition", "value": "Founding Explorer" },
    { "trait_type": "Year", "value": "2025" },
    { "trait_type": "Missions", "value": "5 of 5 Complete" },
    { "trait_type": "Network", "value": "Polygon" }
  ]
}
```

Upload image and metadata JSON to Pinata (free IPFS hosting). Set the metadata CID in `IntramurosNFT.sol` → `PASSPORT_URI` before deploying.

## Deploying the Contract

1. Install MetaMask browser extension
2. Switch to Polygon Amoy Testnet (Chain ID 80002, RPC `https://rpc-amoy.polygon.technology`)
3. Get free test MATIC from faucet: `https://faucet.polygon.technology`
4. Open Remix IDE (`https://remix.ethereum.org`)
5. Paste `IntramurosNFT.sol`, compile with Solidity 0.8.20
6. Deploy via "Injected Provider - MetaMask"
7. Copy the deployed contract address
8. Update `gradle.properties` and Cloud Function config

## Switching to Mainnet

1. Switch MetaMask to Polygon Mainnet (Chain ID 137, RPC `https://polygon-rpc.com`)
2. Re-deploy contract on mainnet
3. Update `gradle.properties`: `POLYGON_CHAIN_ID=137`, `POLYGON_RPC_URL=https://polygon-rpc.com`
4. Update Cloud Function config: `firebase functions:config:set polygon.rpc_url="https://polygon-rpc.com"`
5. Update network security config certificate pin for mainnet RPC
