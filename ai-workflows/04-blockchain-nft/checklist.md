# Checklist — Blockchain & NFT Tasks

## Deploying the Smart Contract (Testnet)

`IntramurosSouvenir` is already deployed on Amoy at `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8`. Use these steps only when re-deploying (e.g. breaking contract change or mainnet migration).

1. Fund MetaMask with Polygon Amoy POL (faucet: `https://faucet.polygon.technology`; expect to need ~0.05 POL for deploy)
2. Open Remix IDE (`https://remix.ethereum.org`) → create file `IntramurosNFT.sol` → paste project contents
3. Solidity Compiler tab → compile with 0.8.20 or later
4. Deploy & Run Transactions tab → Environment → **MetaMask** (or "Browser Extension") → confirm connection
5. Contract dropdown → `IntramurosSouvenir` → expand constructor arrow → `initialUri` = `ipfs://placeholder`
6. Deploy → confirm MetaMask popup (real gas fee indicates real chain)
7. Copy deployed address from "Deployed Contracts" panel
8. Update `gradle.properties`: `NFT_CONTRACT_ADDRESS=0x...`
9. Update Cloud Function config:
   ```powershell
   firebase functions:config:set `
     polygon.contract_address="0x..."
   firebase deploy --only functions
   ```
10. Rebuild app: `.\gradlew :app:assembleDebug`

## Uploading Real Metadata (optional)

1. Create souvenir artwork (PNG/JPG) → upload to Pinata → copy image CID
2. Create `metadata.json` referencing the image CID → upload to Pinata → copy metadata CID
3. From Remix (connected to Amoy, owner wallet): call `setTokenUri("ipfs://<metadataCid>")` on the deployed contract
4. Verify on OpenSea testnet (`https://testnets.opensea.io/collection/...`) or directly via `tokenURI(1)` read call

## Testing the Full Mint Flow

1. Complete all 5 missions (in DEBUG builds, long-press the HomeActivity greeting to auto-complete 4, then finish the 5th on location)
2. Set up a wallet (test both external paste and embedded generation)
3. Treasure chest appears on Home → tap it → NFTClaimActivity
4. Tap **Mint NFT** — CF call should succeed within 5–15 s
5. Verify:
   - Tx hash on `https://amoy.polygonscan.com`
   - `SouvenirMinted` event in Events tab of the contract
   - Firestore `users/{uid}` has `souvenirMinted: true`, `souvenirTxHash`, `souvenirTokenId`, `souvenirMintedAt`
   - HomeActivity treasure chest switches to the opened variant
   - Second tap on Mint NFT returns `already-exists` (expected)

## Modifying Wallet Encryption

1. Changes go in `WalletManager.java`
2. Uses Android Keystore alias for AES-256-GCM key
3. BouncyCastle provider is registered at position 1 inside `generateEmbeddedWallet()` — keep this call or key generation will fail on some ROMs
4. Never store the raw private key — always encrypt first
5. Test: create wallet → close app → reopen → verify wallet still accessible

## Updating Gas Settings

1. Server-side mint uses `ethers.js` auto gas estimation inside `mintSouvenir`
2. Owner wallet must stay funded on whichever chain is active — monitor balance on Polygonscan
3. If mints stall, check Polygonscan for pending nonce and consider forcing a higher gas via ethers `{ maxFeePerGas, maxPriorityFeePerGas }` in `mintSouvenir` (currently defaults)

## Common Issues

- **"Transaction signing failed" in CF logs** → Check `polygon.owner_key` starts with `0x` and is 66 chars total
- **`failed-precondition: Missing polygon config`** → Run `firebase functions:config:get` — ensure `polygon.owner_key`, `polygon.contract_address`, `polygon.rpc_url` are set, then redeploy
- **`failed-precondition: Only N of 5 missions are completed`** → User has not finished all 5 Firestore mission docs — not a blockchain issue
- **`already-exists`** → `souvenirMinted == true` in Firestore; if contract actually has no token for that address, manually reset the Firestore flag and retry
- **"Failed to generate wallet"** on device → Ensure `WalletManager.generateEmbeddedWallet()` still registers BouncyCastle before `Keys.createEcKeyPair()`
- **Double-mint attempt succeeds on-chain** → Intentional: the new contract does NOT track `hasMinted`. Dedup lives in Firestore (`souvenirMinted`) + CF check. Do not re-add on-chain tracking unless you accept the larger bytecode.
