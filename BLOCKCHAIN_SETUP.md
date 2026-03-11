# Blockchain Setup Guide — Intramuros Passport NFT

This guide walks you through everything needed to make the blockchain and NFT system work.
No prior blockchain experience required.

---

## Overview of the Full Flow

```
User completes 5 AR missions
         ↓
App reports each completion to your PHP server
         ↓
User sets up their Polygon wallet (in-app)
         ↓
App tells server: "this wallet finished all missions"
         ↓
Server calls the smart contract: "whitelist this wallet"
         ↓
User taps "Mint NFT" → pays a tiny gas fee (~$0.01) → NFT appears in their wallet
```

---

## PART 1 — Set Up the PHP Backend

These PHP scripts need to be uploaded to your server at `https://jstnagls.shop/volver/`.

### Step 1.1 — Add the database table for mission tracking

Log into your server's phpMyAdmin (or MySQL client) and run this SQL:

```sql
-- Table that records which missions each user has completed
CREATE TABLE mission_completions (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(100) NOT NULL,
    mission_id   VARCHAR(50)  NOT NULL,
    completed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_completion (username, mission_id)
);
```

The `UNIQUE KEY` makes it safe to call `complete_mission.php` multiple times —
it will never double-count a mission.

### Step 1.2 — Add the wallet address column to your users table

```sql
-- Adds a column to store each user's Polygon wallet address
ALTER TABLE users
ADD COLUMN wallet_address VARCHAR(42) DEFAULT NULL;
```

> If your users table has a different name, replace `users` with the correct name.

### Step 1.3 — Open each PHP file and fill in your database credentials

All four PHP files have these four lines near the top. Fill them in:

```php
$host = 'localhost';
$db   = 'your_database_name';   // ← the name of your MySQL database
$user = 'your_db_user';         // ← your MySQL username
$pass = 'your_db_password';     // ← your MySQL password
```

The four files are:
- `backend/complete_mission.php`
- `backend/get_missions.php`
- `backend/save_wallet.php`
- `backend/whitelist_wallet.php`

### Step 1.4 — Upload the PHP files to your server

Upload all four files from the `backend/` folder to:

```
https://jstnagls.shop/volver/
```

After uploading, the URLs should be accessible:
- `https://jstnagls.shop/volver/complete_mission.php`
- `https://jstnagls.shop/volver/get_missions.php`
- `https://jstnagls.shop/volver/save_wallet.php`
- `https://jstnagls.shop/volver/whitelist_wallet.php`

### Step 1.5 — Install web3.php on your server (for whitelist_wallet.php)

`whitelist_wallet.php` needs a PHP library to talk to the blockchain.
Run this on your server via SSH:

```bash
cd /path/to/your/volver/folder
composer require sc0vu/web3.php
```

> If you don't have Composer, install it first: https://getcomposer.org/download/
> If your host doesn't allow SSH, ask your hosting provider for help running Composer.

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

Open `app/src/main/java/com/wheic/arapp/PolygonService.java` and replace:

```java
public static final String NFT_CONTRACT_ADDRESS = "0xYOUR_CONTRACT_ADDRESS_HERE";
```

with:

```java
public static final String NFT_CONTRACT_ADDRESS = "0xAbCd..."; // your actual address
```

### Step 2.10 — Update whitelist_wallet.php with the contract address

Open `backend/whitelist_wallet.php` and fill in:

```php
define('CONTRACT_ADDRESS',  '0xAbCd...');          // ← your contract address from Step 2.8
define('OWNER_PRIVATE_KEY', '0xYourPrivateKey');   // ← see Step 2.11 below
```

### Step 2.11 — Get your owner private key (for the backend)

The backend needs to sign transactions as the contract owner to whitelist users.

1. Open MetaMask
2. Click the three dots next to your account → **"Account Details"**
3. Click **"Show Private Key"** → enter your password → copy the key
4. Paste it into `whitelist_wallet.php` as `OWNER_PRIVATE_KEY`

> ⚠️ **IMPORTANT SECURITY NOTE**
> Never commit your private key to GitHub or share it publicly.
> For production, store it in a `.env` file:
> ```
> OWNER_PRIVATE_KEY=0xYourKeyHere
> ```
> And read it in PHP with `getenv('OWNER_PRIVATE_KEY')`.
> Add `.env` to your `.gitignore`.

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

### Step 5.5 — Update whitelist_wallet.php for mainnet

```php
define('RPC_URL',  'https://polygon-rpc.com');
define('CHAIN_ID', 137);
```

---

## PART 6 — Test Everything End-to-End

Before publishing the app, test the full flow:

1. **Log in** to the app with a test account
2. **Visit all 5 AR locations** (or temporarily lower `ACTIVATION_RADIUS_METERS` in `ARActivity.java` to `10000.0f` so any location triggers it during testing)
3. **Place each character model** — check that the server records the completion
4. **Set up a wallet** — try both "paste address" and "create new wallet"
5. **Tap "Claim NFT"** on the home screen
6. **Mint the NFT** — check the transaction on https://amoy.polygonscan.com

To verify mission completions were saved, run this SQL on your server:
```sql
SELECT * FROM mission_completions WHERE username = 'your_test_username';
```

To verify the whitelist transaction worked, go to Polygonscan, search your contract address,
and check the **"Events"** tab for an `AddressWhitelisted` event.

---

## Quick Reference — Files You Need to Edit

| File | What to fill in |
|---|---|
| `backend/complete_mission.php` | DB host, name, user, password |
| `backend/get_missions.php` | DB host, name, user, password |
| `backend/save_wallet.php` | DB host, name, user, password |
| `backend/whitelist_wallet.php` | DB credentials + contract address + owner private key |
| `contracts/IntramurosNFT.sol` | `PASSPORT_URI` (IPFS metadata CID) — before deploying |
| `PolygonService.java` | `NFT_CONTRACT_ADDRESS` after deploying the contract |

---

## Common Issues

**"Model not found — using fallback"** in Android logs
→ The `.glb` file isn't in `res/raw/` or the filename doesn't match exactly.

**"Complete all 5 Intramuros missions first" when minting**
→ The wallet isn't whitelisted yet. Check that `whitelist_wallet.php` ran successfully
and that all 5 missions are in the `mission_completions` table.

**MetaMask doesn't open when tapping Mint (external wallet)**
→ MetaMask isn't installed on the device. The app will show the deep link as text instead.

**`whitelist_wallet.php` returns "Transaction signing failed"**
→ Double-check `OWNER_PRIVATE_KEY` starts with `0x` and has 66 characters total.
Also confirm `CONTRACT_ADDRESS` is the correct deployed address.

**Gas price too low / transaction stuck**
→ In `whitelist_wallet.php`, increase the gasPrice value:
`'gasPrice' => '0x' . dechex(50000000000)` (50 gwei instead of 30)
