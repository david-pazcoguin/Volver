# Context — Blockchain & NFT (Polygon)

## Full Flow (current — Path A)

```
User completes 5 AR missions
         ↓
App writes each completion to Firestore (users/{uid}/missions/{missionId})
         ↓
HomeActivity detects allComplete → reveals treasure chest
         ↓
User sets up a Polygon wallet (in-app embedded keypair OR external address)
         ↓
NFTClaimActivity calls the mintSouvenir Cloud Function
         ↓
Cloud Function verifies: auth + 5 missions + wallet matches + souvenirMinted!=true
         ↓
Owner wallet signs adminMintTo(userAddress) on the IntramurosSouvenir contract
         ↓
Transaction confirmed → Firestore updated (souvenirMinted, souvenirTxHash, souvenirTokenId)
         ↓
NFT appears in the user's wallet. User paid zero gas. Owner wallet paid.
```

## Smart Contract

**File**: `contracts/IntramurosNFT.sol` (contract class `IntramurosSouvenir`)

**Deployed (Amoy testnet)**: `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8`

- ERC-721 + Ownable (OpenZeppelin ^0.8.20)
- `adminMintTo(address to)` — `onlyOwner`, mints next token ID to `to`, emits `SouvenirMinted`
- `setTokenUri(string)` — `onlyOwner`, swaps the shared metadata URI (all tokens resolve to the same URI)
- `totalMinted()` view for analytics
- No whitelist, no per-user claim, no per-token URI storage — minimal bytecode so deploy fits in <0.05 POL

## Key Files

| File | Path | Purpose |
|------|------|---------|
| IntramurosNFT.sol | `contracts/IntramurosNFT.sol` | ERC-721 smart contract (`IntramurosSouvenir`) |
| PolygonService.java | `app/src/main/java/com/wheic/arapp/PolygonService.java` | BuildConfig wiring + Polygonscan URL helper |
| WalletManager.java | `app/src/main/java/com/wheic/arapp/WalletManager.java` | Wallet state + AES-256-GCM encryption + BouncyCastle provider registration |
| WalletSetupActivity.java | `app/src/main/java/com/wheic/arapp/WalletSetupActivity.java` | Multi-step wallet setup UI |
| NFTClaimActivity.java | `app/src/main/java/com/wheic/arapp/NFTClaimActivity.java` | Calls `mintSouvenir` and renders result |
| SecurePrefs.java | `app/src/main/java/com/wheic/arapp/SecurePrefs.java` | Stores `nft_claimed`, `chest_dismissed` flags |
| index.js | `functions/index.js` | Cloud Function: `mintSouvenir` (also retains `whitelistWallet` stub returning `unavailable`) |

## Wallet Modes

### Embedded Wallet (recommended for Path A)
- App generates an `ECKeyPair` via Web3j
- BouncyCastle provider registered at position 1 in `WalletManager.generateEmbeddedWallet()` — required on Android ROMs that ship a stripped BC
- Private key encrypted with AES-256-GCM using Android Keystore (hardware-backed when available)
- Stored in SharedPreferences (`wallet_prefs`)
- Minting is handled server-side — the private key never signs a mint transaction; it only proves ownership of the destination address

### External Wallet
- User pastes or scans (QR) their existing Polygon wallet address
- No private key stored locally
- Mint still happens server-side via `mintSouvenir` using the pasted address as the destination

## Security

- `FLAG_SECURE` on `WalletSetupActivity` and `NFTClaimActivity`
- Clipboard auto-cleared 30 seconds after copying an embedded private key
- Handler leak prevented: clipboard runnable is a class field, canceled in `onDestroy()`
- Button debounce (2s cooldown) on wallet confirm and NFT mint buttons
- Owner private key stored in Firebase functions config/secrets (never in app or source)
- Cloud Function triple-checks: `allComplete` flag, mission subcollection count, and `souvenirMinted != true`
- Destination address on-chain is the **server-side stored** `walletAddress`, not the client-supplied one

## Configuration

All Android-side blockchain values injected via `BuildConfig` from `gradle.properties`:

| Property | gradle.properties Key | Value |
|----------|----------------------|---------|
| Contract address | `NFT_CONTRACT_ADDRESS` | `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8` |
| RPC URL | `POLYGON_RPC_URL` | `https://rpc-amoy.polygon.technology` |
| Chain ID | `POLYGON_CHAIN_ID` | `80002L` (Amoy testnet) |
| Thirdweb Client ID (Path B, unused) | `THIRDWEB_CLIENT_ID` | `14483272c69d9087fc22542a79294900` |

Cloud Function secrets (via `firebase functions:config:set`):
- `polygon.owner_key` — owner wallet private key (pays all mint gas)
- `polygon.contract_address` — deployed contract address
- `polygon.rpc_url` — RPC endpoint

## NFT Metadata (IPFS — optional before launch)

Contract currently returns `ipfs://placeholder` for every token. To ship real artwork:

```json
{
  "name": "Intramuros Souvenir — Walled City Key",
  "description": "Awarded to explorers who completed all 5 Intramuros AR missions.",
  "image": "ipfs://QmXyz123...",
  "attributes": [
    { "trait_type": "Edition", "value": "Founding Explorer" },
    { "trait_type": "Year", "value": "2026" },
    { "trait_type": "Missions", "value": "5 of 5 Complete" },
    { "trait_type": "Network", "value": "Polygon" }
  ]
}
```

Upload image + JSON to Pinata. Then call `setTokenUri("ipfs://<metadataCid>")` on the contract (owner-only, from Remix). Because the contract stores a single shared URI, all previously minted tokens update automatically.

## Deploying the Contract

1. Install MetaMask browser extension and fund it with ~0.1 POL on Polygon Amoy (faucet: `https://faucet.polygon.technology` — X.com verification works)
2. Add Polygon Amoy to MetaMask manually if needed (Chain ID 80002, RPC `https://rpc-amoy.polygon.technology`)
3. Open Remix IDE (`https://remix.ethereum.org`)
4. Paste `contracts/IntramurosNFT.sol`, compile with Solidity 0.8.20+
5. Deploy & Run Transactions tab → Environment → **MetaMask** (shown as "Browser Extension" → MetaMask in newer Remix)
6. Constructor arg `initialUri`: pass `ipfs://placeholder` (or real CID if already uploaded)
7. Click **Deploy** → confirm in MetaMask
8. Copy the deployed address from Remix "Deployed Contracts"
9. Update `gradle.properties` and Cloud Function config

## Switching to Mainnet (Path C — future)

1. Switch MetaMask to Polygon Mainnet (Chain ID 137, RPC `https://polygon-rpc.com`)
2. Re-deploy contract on mainnet (same source)
3. Update `gradle.properties`: `POLYGON_CHAIN_ID=137L`, `POLYGON_RPC_URL=https://polygon-rpc.com`, new `NFT_CONTRACT_ADDRESS`
4. Set Cloud Function config: `firebase functions:config:set polygon.contract_address="0x..." polygon.rpc_url="https://polygon-rpc.com" polygon.owner_key="0x..."`
5. Update network security config certificate pin for mainnet RPC
6. Fund the mainnet owner wallet — each mint costs ~0.01 POL; budget accordingly
