// SECURITY: Never commit real values of owner_key to source control.
// Set via one of:
//   firebase functions:secrets:set POLYGON_OWNER_KEY
//   firebase functions:config:set polygon.owner_key="0x..."
//
// Path A flow (current):
//   1. Client calls mintSouvenir({ uid, walletAddress }).
//   2. CF verifies auth, 5 missions complete, wallet matches stored profile.
//   3. CF uses owner key to call adminMintTo(walletAddress) on Polygon.
//   4. Owner wallet pays gas; user pays nothing.
//   5. Returns tx hash + tokenId.

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { ethers } = require("ethers");

if (!admin.apps.length) {
  admin.initializeApp();
}

const REQUIRED_MISSIONS = 5;

function getPolygonConfig() {
  const cfg = (functions.config && functions.config().polygon) || {};
  return {
    ownerKey:        cfg.owner_key        || process.env.POLYGON_OWNER_KEY,
    contractAddress: cfg.contract_address || process.env.POLYGON_CONTRACT_ADDRESS,
    rpcUrl:          cfg.rpc_url          || process.env.POLYGON_RPC_URL,
  };
}

async function assertEligibleForMint(uid, walletAddress, context) {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  if (typeof uid !== "string" || uid.trim().length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "uid must be a non-empty string.");
  }
  if (typeof walletAddress !== "string" || walletAddress.trim().length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "walletAddress must be a non-empty string.");
  }
  if (uid !== context.auth.uid) {
    throw new functions.https.HttpsError("permission-denied", "UID mismatch for authenticated caller.");
  }
  if (!ethers.isAddress(walletAddress)) {
    throw new functions.https.HttpsError("invalid-argument", "walletAddress is not a valid address.");
  }

  const db = admin.firestore();
  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  if (!userSnap.exists) {
    throw new functions.https.HttpsError("not-found", "User profile not found.");
  }

  const userData = userSnap.data() || {};

  // Use server-side stored wallet, not the one from the client payload.
  const storedWallet = userData.walletAddress;
  if (typeof storedWallet !== "string" || !ethers.isAddress(storedWallet)) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "No valid wallet address on file. Save your wallet first."
    );
  }
  if (storedWallet.toLowerCase() !== walletAddress.toLowerCase()) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Wallet address does not match the one saved to your profile."
    );
  }

  if (userData.allComplete !== true) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "All missions must be completed before minting."
    );
  }

  const missionsSnap = await userRef
    .collection("missions")
    .where("completed", "==", true)
    .get();

  if (missionsSnap.size < REQUIRED_MISSIONS) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      `Only ${missionsSnap.size} of ${REQUIRED_MISSIONS} missions are completed.`
    );
  }

  if (userData.souvenirMinted === true) {
    throw new functions.https.HttpsError(
      "already-exists",
      "Souvenir has already been minted for this account."
    );
  }

  return { userRef, storedWallet };
}

/**
 * Mints the Intramuros Souvenir NFT to the user's wallet.
 * Gas is paid by the owner wallet configured in server secrets.
 */
exports.mintSouvenir = functions.https.onCall(async (data, context) => {
  const uid = data && data.uid;
  const walletAddress = data && data.walletAddress;

  const { userRef, storedWallet } = await assertEligibleForMint(uid, walletAddress, context);

  const { ownerKey, contractAddress, rpcUrl } = getPolygonConfig();
  if (!ownerKey || !contractAddress || !rpcUrl) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Missing polygon config. Set owner_key, contract_address, and rpc_url."
    );
  }
  if (!ethers.isAddress(contractAddress)) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Configured contract_address is invalid."
    );
  }

  try {
    const provider = new ethers.JsonRpcProvider(rpcUrl);
    const signer   = new ethers.Wallet(ownerKey, provider);
    const abi = [
      "function adminMintTo(address to) external returns (uint256)",
      "event SouvenirMinted(address indexed user, uint256 tokenId)",
    ];
    const contract = new ethers.Contract(contractAddress, abi, signer);

    const tx = await contract.adminMintTo(storedWallet);
    const receipt = await tx.wait();

    // Extract tokenId from emitted event (best-effort).
    let tokenId = null;
    try {
      for (const log of receipt.logs || []) {
        const parsed = contract.interface.parseLog(log);
        if (parsed && parsed.name === "SouvenirMinted") {
          tokenId = parsed.args.tokenId.toString();
          break;
        }
      }
    } catch (_) { /* ignore parse failures */ }

    await userRef.update({
      souvenirMinted: true,
      souvenirTxHash: tx.hash,
      souvenirTokenId: tokenId,
      souvenirMintedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true, txHash: tx.hash, tokenId };
  } catch (error) {
    console.error("mintSouvenir failed", {
      uid,
      error: error && error.message ? error.message : error,
    });
    throw new functions.https.HttpsError(
      "internal",
      "Failed to mint souvenir on-chain."
    );
  }
});

/**
 * Legacy whitelist function — retained for backward compatibility during
 * migration. Safe to remove once no client version calls it.
 * (Currently disabled: the new shrunk contract has no whitelistAddress.)
 */
exports.whitelistWallet = functions.https.onCall(async () => {
  throw new functions.https.HttpsError(
    "unavailable",
    "whitelistWallet has been replaced by mintSouvenir. Update the app."
  );
});
