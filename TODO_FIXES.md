# Volver — Remaining Manual Fixes

> All code fixes have been applied. These items require external assets or configuration.

## 1. Missing GLB Character Models
Place these files in `app/src/main/res/raw/`:
- `rizal_character.glb` — Jose Rizal (Fort Santiago)
- `sedeno_character.glb` — Antonio Sedeno (Baluarte de San Diego)
- `marcos_character.glb` — Imelda Marcos (Casa Manila)
- `tinio_character.glb` — Martin Tinio Jr. (Museo de Intramuros)
- `ignatius_character.glb` — St. Ignatius of Loyola (Centro de Turismo)

**Tip:** Keep each model under 1 MB for smooth AR loading. The current fallback model (2.2 MB) works but loads slowly.

## 2. NFT Contract Deployment
After deploying `contracts/IntramurosNFT.sol`, add to `gradle.properties`:
```
NFT_CONTRACT_ADDRESS=0xYOUR_DEPLOYED_CONTRACT_ADDRESS
```
Without this, all minting transactions will fail (defaults to zero address).

## 3. IPFS Metadata CID
1. Create NFT metadata JSON with image and traits
2. Upload to Pinata or NFT.Storage
3. In `contracts/IntramurosNFT.sol` line 44, replace:
   ```
   "ipfs://YOUR_METADATA_CID_HERE"
   ```
   with the actual CID.

## 4. Firebase Cloud Function
Deploy the `whitelistWallet` Cloud Function that MissionCompletionHelper calls.
It should:
1. Verify the user completed all 5 missions in Firestore
2. Call `whitelistAddress(walletAddress)` on the NFT contract using the owner key
See `functions/index.js` for the scaffold.

## 5. Mainnet Certificate Pin (before production)
Get the real SHA-256 pin for your mainnet RPC endpoint and replace the placeholder in
`app/src/main/res/xml/network_security_config.xml`.

## 6. MAPS_API_KEY
Ensure your Google Maps API key is set in `local.properties`:
```
MAPS_API_KEY=your_actual_key
```
This is used for geospatial AR features. Without it, geospatial anchoring may fail silently.
