# Volver — Remaining Manual Fixes

> All code fixes have been applied. These items require external assets, configuration, or manual deploy steps.

## 1. Missing GLB Character Models
Place these files in `app/src/main/res/raw/`:
- `rizal_character.glb` — Jose Rizal (Fort Santiago)
- `sedeno_character.glb` — Antonio Sedeno (Baluarte de San Diego)
- `marcos_character.glb` — Imelda Marcos (Casa Manila)
- `tinio_character.glb` — Martin Tinio Jr. (Museo de Intramuros)
- `ignatius_character.glb` — St. Ignatius of Loyola (Centro de Turismo)

**Tip:** Keep each model under 1 MB for smooth AR loading. The current fallback model (2.2 MB) works but loads slowly.

## 2. NFT Contract Deployment — DONE (testnet)
`IntramurosSouvenir` is deployed on **Polygon Amoy** at `0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8` and wired into `gradle.properties`. Mainnet re-deploy is tracked in README *Future Roadmap → Path C*.

## 3. IPFS Metadata CID (optional pre-launch)
The contract currently serves `ipfs://placeholder` for `tokenURI`. To ship real souvenir art:
1. Create NFT metadata JSON with image + traits
2. Upload to Pinata or NFT.Storage → note the metadata CID
3. Call `setTokenUri("ipfs://<cid>")` on the deployed contract (owner-only, via Remix or a script)

Because `tokenURI` is a single shared string, all previously minted tokens update automatically.

## 4. Firebase Cloud Function Secrets — REQUIRED
The `mintSouvenir` Cloud Function must have the owner wallet key set before it can mint.

From `Volver/`:

```powershell
firebase functions:config:set `
  polygon.owner_key="0xYOUR_OWNER_PRIVATE_KEY" `
  polygon.contract_address="0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8" `
  polygon.rpc_url="https://rpc-amoy.polygon.technology"

firebase deploy --only functions
```

Verify with `firebase functions:config:get` and a test mint on device.

## 5. Mainnet Certificate Pin (before production)
Get the real SHA-256 pin for your mainnet RPC endpoint and replace the placeholder in
`app/src/main/res/xml/network_security_config.xml`.

## 6. MAPS_API_KEY
Ensure your Google Maps API key is set in `local.properties`:
```
MAPS_API_KEY=your_actual_key
```
This is used for geospatial AR features. Without it, geospatial anchoring may fail silently.

## 7. Path B — Thirdweb In-App Wallet (future)
`THIRDWEB_CLIENT_ID=14483272c69d9087fc22542a79294900` is already saved in `gradle.properties`. Gas Sponsorship is configured on the Thirdweb dashboard. Integration (adding the Android SDK, replacing Login/Register, retiring `WalletManager.generateEmbeddedWallet`) is deferred — see README *Future Roadmap → Path B*
```
MAPS_API_KEY=your_actual_key
```
This is used for geospatial AR features. Without it, geospatial anchoring may fail silently.

## 7. Path B — Thirdweb In-App Wallet (future)
`THIRDWEB_CLIENT_ID=14483272c69d9087fc22542a79294900` is already saved in `gradle.properties`. Gas Sponsorship is configured on the Thirdweb dashboard. Integration (adding the Android SDK, replacing Login/Register, retiring `WalletManager.generateEmbeddedWallet`) is deferred — see README *Future Roadmap → Path B*.
