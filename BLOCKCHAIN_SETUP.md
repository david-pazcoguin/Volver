# Blockchain Setup Guide — Intramuros Passport NFT

This guide walks you through everything needed to make the blockchain and NFT system work.
No prior blockchain experience required.

---

## Overview of the Full Flow

```
User completes 5 AR missions
         ↓
App writes each completion to Firestore (users/{uid}/missions/{missionId})
         ↓
User sets up their Polygon wallet (in-app)
         ↓
App calls the whitelistWallet Cloud Function
         ↓
Cloud Function verifies all 5 missions → calls the smart contract → whitelists the wallet
         ↓
User taps "Mint NFT" → pays a tiny gas fee (~$0.01) → NFT appears in their wallet
```

---

## PART 1 — Set Up the Firebase Backend

The app uses **Firebase** for all backend services. No PHP or SQL is needed.

### Step 1.1 — Create a Firebase project

1. Go to https://console.firebase.google.com
2. Click **Add project** and follow the wizard
3. Enable **Google Analytics** if desired

### Step 1.2 — Register the Android app

1. In your Firebase project, click **Add app** → **Android**
2. Package name: `com.wheic.arapp`
3. Download the generated `google-services.json` and place it in `app/`

### Step 1.3 — Enable Firebase Authentication

1. In the Firebase console, go to **Authentication** → **Sign-in method**
2. Enable **Email/Password** provider
3. The app uses the pattern `username@volver.app` as the email for each user

### Step 1.4 — Create the Firestore database

1. Go to **Firestore Database** → **Create database**
2. Choose a location close to your users (e.g. `asia-southeast1` for Philippines)
3. Start in **production mode** — the security rules are already defined in `firestore.rules`

Firestore data model:

```
users/{uid}
  ├─ username       (string)
  ├─ firstName      (string)
  ├─ lastName       (string)
  ├─ email          (string)
  ├─ walletAddress  (string, optional)
  ├─ allComplete    (boolean)
  ├─ whitelisted    (boolean)
  └─ createdAt      (timestamp)
  └── missions/{missionId}
        ├─ completed   (boolean)
        ├─ completedAt (timestamp)
        └─ missionId   (string)
```

### Step 1.5 — Deploy Firestore security rules

```bash
firebase deploy --only firestore:rules
```

The rules in `firestore.rules` enforce:
- Users can only read/write their own profile and missions
- Mission documents are append-only (no edits or deletes)
- Only allowed fields can be updated on the user profile

### Step 1.6 — Deploy the Cloud Function

The `whitelistWallet` Cloud Function handles blockchain whitelisting.

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

### Step 1.7 — Set Cloud Function secrets

The Cloud Function needs your Polygon contract owner key and contract address:

```bash
firebase functions:config:set \
  polygon.owner_key="0xYOUR_OWNER_PRIVATE_KEY" \
  polygon.contract_address="0xYOUR_CONTRACT_ADDRESS" \
  polygon.rpc_url="https://rpc-amoy.polygon.technology"
```

> ⚠️ **IMPORTANT SECURITY NOTE**
> Never commit these secrets to source control.
> `functions.config()` stores them securely in the Firebase environment.

---

## PART 2 — Deploy the Smart Contract

The smart contract is the on-chain program that issues the NFT.
You deploy it once and it lives on the Polygon blockchain forever.

### Step 2.1 — Install MetaMask

1. Go to https://metamask.io and install the browser extension (Chrome or Firefox).
2. Create a new wallet and **write down your seed phrase** — store it somewhere safe offline.
3. You now have a wallet address that looks like: `0xAbc123...`

### Step 2.2 — Switch MetaMask to Polygon Amoy Testnet

The testnet is a safe practice environment — tokens here have no real value.

1. Open MetaMask → click the network name at the top (it says "Ethereum Mainnet")
2. Click **"Add Network"** → **"Add a network manually"**
3. Fill in:

| Field | Value |
|---|---|
| Network Name | Polygon Amoy Testnet |
| New RPC URL | `https://rpc-amoy.polygon.technology` |
| Chain ID | `80002` |
| Currency Symbol | `MATIC` |
| Block Explorer URL | `https://amoy.polygonscan.com` |

4. Click **Save**.

### Step 2.3 — Get free test MATIC (for paying deployment gas)

You need a tiny amount of MATIC to deploy the contract. On testnet it's free.

1. Copy your MetaMask wallet address (click your account name to copy it)
2. Go to the Polygon Amoy faucet: https://faucet.polygon.technology
3. Paste your address and request test MATIC
4. Wait ~30 seconds — you'll see MATIC appear in MetaMask

### Step 2.4 — Open Remix IDE

Remix is a free browser-based tool for deploying Solidity contracts.

1. Go to https://remix.ethereum.org
2. In the left sidebar, click the **"File Explorer"** icon (📄)
3. Click **"New File"** and name it `IntramurosNFT.sol`
4. Copy the entire contents of `contracts/IntramurosNFT.sol` from this project and paste it in

### Step 2.5 — Install OpenZeppelin in Remix

The contract uses OpenZeppelin — a trusted library of NFT standards.

1. In Remix, click the **"Plugin Manager"** icon in the left sidebar (🔌)
2. Search for **"OpenZeppelin"** and activate it
3. Alternatively, Remix will auto-download OpenZeppelin imports when you compile — just let it run

### Step 2.6 — Compile the contract

1. Click the **"Solidity Compiler"** icon in the left sidebar (⚙️)
2. Set compiler version to **0.8.20**
3. Click **"Compile IntramurosNFT.sol"**
4. If it shows green checkmarks — success. If there are errors, check that you copied the full file.

### Step 2.7 — Deploy the contract

1. Click the **"Deploy & Run Transactions"** icon (🚀)
2. Under **"Environment"**, select **"Injected Provider - MetaMask"**
   - MetaMask will pop up asking to connect — approve it
   - Make sure MetaMask is on **Polygon Amoy Testnet** (from Step 2.2)
3. Under **"Contract"**, select **IntramurosPassport**
4. Click the orange **"Deploy"** button
5. MetaMask will pop up with a transaction — click **Confirm**
6. Wait ~10 seconds for the transaction to confirm

### Step 2.8 — Copy your contract address

1. In Remix, under **"Deployed Contracts"**, you'll see your contract appear
2. Click the copy icon next to the contract address — it looks like `0xAbCd...`
3. **Save this address** — you'll need it in the next steps

### Step 2.9 — Update the Android app with the contract address

Open `gradle.properties` in the project root and set:

```properties
NFT_CONTRACT_ADDRESS=0xAbCd...  # your actual deployed contract address
```

The app reads this value at build time via `BuildConfig.NFT_CONTRACT_ADDRESS` in `PolygonService.java`. You do **not** need to edit any Java source files — all blockchain config is injected from `gradle.properties`.

### Step 2.10 — Update the Cloud Function with the contract address

After deployment, set the contract address in your Firebase config:

```bash
firebase functions:config:set polygon.contract_address="0xAbCd..."
```

### Step 2.11 — Set your owner private key in Firebase config

The Cloud Function needs to sign transactions as the contract owner to whitelist users.

1. Open MetaMask
2. Click the three dots next to your account → **"Account Details"**
3. Click **"Show Private Key"** → enter your password → copy the key
4. Set it in Firebase config:

```bash
firebase functions:config:set polygon.owner_key="0xYourPrivateKey"
```

> ⚠️ **IMPORTANT SECURITY NOTE**
> Never commit your private key to GitHub or share it publicly.
> `functions.config()` stores secrets securely in the Firebase environment.
> Re-deploy functions after changing config: `firebase deploy --only functions`

---

## PART 3 — Create and Upload the NFT Artwork

The NFT needs an image and a metadata file stored on IPFS
(a decentralized file storage network — free to use).

### Step 3.1 — Create the NFT image

Design an image for the "Walled City Key" NFT.
Recommended: 1000×1000 px PNG, depicting the Intramuros walls or a passport stamp design.

You can use Canva, Photoshop, or any design tool.

### Step 3.2 — Upload the image to Pinata (free IPFS hosting)

1. Go to https://pinata.cloud and create a free account
2. Click **"Upload"** → **"File"** → select your NFT image
3. After upload, you'll see a **CID** (Content Identifier) — looks like `QmXyz123...`
4. Your image is now at: `ipfs://QmXyz123...`

### Step 3.3 — Create the metadata JSON file

Create a file called `metadata.json` on your computer with this content:

```json
{
  "name": "Intramuros Passport — Walled City Key",
  "description": "Awarded to explorers who completed all 5 Intramuros AR missions. A record of your journey through Manila's Walled City.",
  "image": "ipfs://QmXyz123...",
  "attributes": [
    { "trait_type": "Edition",    "value": "Founding Explorer" },
    { "trait_type": "Year",       "value": "2025" },
    { "trait_type": "Missions",   "value": "5 of 5 Complete" },
    { "trait_type": "Network",    "value": "Polygon" }
  ]
}
```

Replace `ipfs://QmXyz123...` with the actual image CID from Step 3.2.

### Step 3.4 — Upload the metadata JSON to Pinata

1. Back in Pinata, upload `metadata.json` the same way
2. Copy the new CID — looks like `QmAbc456...`

### Step 3.5 — Update the smart contract with the metadata CID

Open `contracts/IntramurosNFT.sol` and replace:

```solidity
string public constant PASSPORT_URI = "ipfs://YOUR_METADATA_CID_HERE";
```

with:

```solidity
string public constant PASSPORT_URI = "ipfs://QmAbc456...";
```

> ⚠️ You need to do this **before deploying** the contract (Step 2.7), or re-deploy
> if you've already deployed. Once deployed, the URI is locked in the contract.

---

## PART 4 — Add the 3D Character Models

Since no 3D assets exist yet, each mission currently uses the fallback church model.
Here's how to add the proper character models.

### Step 4.1 — Download base models from Mixamo (free)

1. Go to https://mixamo.com and sign in with a free Adobe account
2. For each character, search by name or find a suitable humanoid:

| Mission | Character | Suggested Mixamo search |
|---|---|---|
| Fort Santiago | José Rizal | "Formal Male", "Scholar" |
| Baluarte de San Diego | Antonio Sedeño | "Monk", "Priest" |
| Casa Manila | Imelda Marcos | "Formal Female", "Elegant" |
| Museo de Intramuros | Martin Tinio Jr. | "Professor", "Historian" |
| Centro de Turismo | St. Ignatius of Loyola | "Robe", "Monk" |

3. Select a character → choose an **idle animation** (e.g. "Idle", "Breathing Idle")
4. Click **Download**:
   - Format: **FBX for Unity** (we'll convert it)
   - Skin: **With Skin**

### Step 4.2 — Convert FBX to GLB

The app uses `.glb` format. Convert using the free online tool:

1. Go to https://products.aspose.app/3d/conversion/fbx-to-glb
2. Upload your `.fbx` file → Convert → Download the `.glb` file

Or use Blender (free):
1. Open Blender → **File → Import → FBX**
2. Select your file
3. **File → Export → glTF 2.0 (.glb/.gltf)**
4. Set Format to **glTF Binary (.glb)** → Export

### Step 4.3 — Name the files exactly right

Rename each `.glb` file to match exactly:

```
rizal_character.glb       ← Fort Santiago
sedeno_character.glb      ← Baluarte de San Diego
marcos_character.glb      ← Casa Manila
tinio_character.glb       ← Museo de Intramuros
ignatius_character.glb    ← Centro de Turismo
```

### Step 4.4 — Place them in the Android project

Copy all five `.glb` files into:

```
app/src/main/res/raw/
```

The app will automatically detect them by name — no code changes needed.

---

## PART 5 — Switch to Mainnet (When Ready for Public Release)

Once everything is tested on testnet, switch to real Polygon:

### Step 5.1 — Get real MATIC

Buy MATIC on any exchange (Binance, Coinbase, etc.) and send it to your MetaMask.
You need about $1–2 worth for deployment + whitelist transactions.

### Step 5.2 — Switch MetaMask to Polygon Mainnet

1. In MetaMask, switch network to **Polygon Mainnet**
   - This is usually pre-installed. If not, add it:
   - RPC: `https://polygon-rpc.com`
   - Chain ID: `137`
   - Symbol: `MATIC`
   - Explorer: `https://polygonscan.com`

### Step 5.3 — Re-deploy the contract on mainnet

Repeat Steps 2.4–2.8 with MetaMask on **Polygon Mainnet** instead of testnet.

### Step 5.4 — Update PolygonService.java for mainnet

```java
// In PolygonService.java, comment out testnet and uncomment mainnet:

// Testnet (comment this out):
// public static final String  RPC_URL  = "https://rpc-amoy.polygon.technology";
// public static final long    CHAIN_ID = 80002L;

// Mainnet (uncomment this):
public static final String  RPC_URL  = "https://polygon-rpc.com";
public static final long    CHAIN_ID = 137L;
```

### Step 5.5 — Update Cloud Function config for mainnet

```bash
firebase functions:config:set polygon.rpc_url="https://polygon-rpc.com"
firebase deploy --only functions
```

---

## PART 6 — Test Everything End-to-End

Before publishing the app, test the full flow:

1. **Log in** to the app with a test account
2. **Visit all 5 AR locations** (or temporarily lower `ACTIVATION_RADIUS_METERS` in `ARActivity.java` to `10000.0f` so any location triggers it during testing)
3. **Place each character model** — check that Firestore records the completion
4. **Set up a wallet** — try both "paste address" and "create new wallet"
5. **Tap "Claim NFT"** on the home screen
6. **Mint the NFT** — check the transaction on https://amoy.polygonscan.com

To verify mission completions were saved, check **Firestore** in the Firebase console:
```
users/{uid}/missions/ → should show 5 documents with completed: true
```

To verify the whitelist transaction worked, go to Polygonscan, search your contract address,
and check the **"Events"** tab for an `AddressWhitelisted` event.

---

## Quick Reference — Files You Need to Edit

| File | What to fill in |
|---|---|
| `app/google-services.json` | Firebase project config (download from Firebase console) |
| `contracts/IntramurosNFT.sol` | `PASSPORT_URI` (IPFS metadata CID) — before deploying |
| `PolygonService.java` | `NFT_CONTRACT_ADDRESS` after deploying the contract |
| Firebase config (CLI) | `polygon.owner_key`, `polygon.contract_address`, `polygon.rpc_url` |

---

## Common Issues

**"Model not found — using fallback"** in Android logs
→ The `.glb` file isn't in `res/raw/` or the filename doesn't match exactly.

**"Complete all 5 Intramuros missions first" when minting**
→ The wallet isn't whitelisted yet. Check that the `whitelistWallet` Cloud Function
ran successfully and that all 5 missions exist in `users/{uid}/missions/` in Firestore.

**MetaMask doesn't open when tapping Mint (external wallet)**
→ MetaMask isn't installed on the device. The app will show the deep link as text instead.

**Cloud Function returns "Transaction signing failed"**
→ Double-check `polygon.owner_key` starts with `0x` and has 66 characters total.
Also confirm `polygon.contract_address` is the correct deployed address.
Verify with: `firebase functions:config:get`

**Gas price too low / transaction stuck**
→ This is handled automatically by ethers.js in the Cloud Function.
If needed, you can adjust gas settings in `functions/index.js`.

---

## Related Documentation

- [README.md](README.md) — Full project overview, architecture, and all configuration
- [FIREBASE_SECURITY_NOTES.md](FIREBASE_SECURITY_NOTES.md) — Firestore security rules details
- [BUILD_AND_DEPLOY.md](BUILD_AND_DEPLOY.md) — Build commands and Firebase deployment
