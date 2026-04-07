# Checklist — Blockchain & NFT Tasks

## Deploying the Smart Contract (Testnet)

1. Ensure NFT image is uploaded to Pinata → get image CID
2. Create `metadata.json` with image CID → upload to Pinata → get metadata CID
3. Update `PASSPORT_URI` in `contracts/IntramurosNFT.sol` with metadata CID
4. Open Remix IDE → paste contract → compile with Solidity 0.8.20
5. Connect MetaMask (Polygon Amoy Testnet) → deploy
6. Copy contract address
7. Update `gradle.properties`: `NFT_CONTRACT_ADDRESS=0x...`
8. Update Cloud Function: `firebase functions:config:set polygon.contract_address="0x..."`
9. Deploy Cloud Function: `firebase deploy --only functions`
10. Build app: `.\gradlew :app:assembleDebug`

## Testing the Full Mint Flow

1. Complete all 5 missions (or set `ACTIVATION_RADIUS_METERS=50000.0f` for testing)
2. Set up a wallet (test both external and embedded modes)
3. Tap "Claim NFT" on home screen
4. Mint the NFT
5. Verify on `https://amoy.polygonscan.com` — search contract address → Events tab

## Modifying Wallet Encryption

1. Changes go in `WalletManager.java`
2. Uses Android Keystore alias for AES-256-GCM key
3. Never store raw private key — always encrypt first
4. Test: create wallet → close app → reopen → verify wallet still accessible

## Updating Gas Settings

1. Gas estimation is handled by Web3j in `PolygonService.java`
2. Cloud Function uses ethers.js auto gas estimation
3. If transactions are stuck, check gas price on Polygonscan
4. Adjust timeout settings in `PolygonService.java` if needed (currently 15s/30s)

## Common Issues

- **"Transaction signing failed"** → Check `polygon.owner_key` starts with `0x` and has 66 chars
- **"Complete all 5 missions first"** → Wallet not whitelisted. Check Cloud Function logs
- **MetaMask doesn't open** → App needs MetaMask mobile installed. Falls back to text display
- **Double-mint attempt** → `hasMinted` mapping prevents this at contract level
