// SECURITY: Never commit real values of owner_key to source control.
// Set via one of:
//   firebase functions:secrets:set POLYGON_OWNER_KEY
//   firebase functions:config:set polygon.owner_key="0x..."
//
// Path A flow (current):
//   1. Client calls mintSouvenir({ uid, walletAddress }).
//   2. CF verifies auth, 8 missions complete, wallet matches stored profile.
//   3. CF uses owner key to call adminMintTo(walletAddress) on Polygon.
//   4. Owner wallet pays gas; user pays nothing.
//   5. Returns tx hash + tokenId.

const admin = require("firebase-admin");
const { ethers } = require("ethers");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const {
  MISSION_MAP,
  didLeaderboardProfileChange,
  syncUserPublicEntries,
} = require("./leaderboards");

if (!admin.apps.length) {
  admin.initializeApp();
}

const REQUIRED_MISSIONS = 8;

function getPolygonConfig() {
  // Prefer env vars for Gen 2, but keep Runtime Config fallback until migrated.
  let cfg = {};
  try {
    // eslint-disable-next-line global-require
    const legacyFunctions = require("firebase-functions/v1");
    cfg = (typeof legacyFunctions.config === "function" ? legacyFunctions.config().polygon : null) || {};
  } catch (_) { /* ignore */ }
  return {
    ownerKey:        cfg.owner_key        || process.env.POLYGON_OWNER_KEY,
    contractAddress: cfg.contract_address || process.env.POLYGON_CONTRACT_ADDRESS,
    rpcUrl:          cfg.rpc_url          || process.env.POLYGON_RPC_URL,
  };
}

async function assertEligibleForMint(uid, walletAddress, auth) {
  if (!auth) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  if (typeof uid !== "string" || uid.trim().length === 0) {
    throw new HttpsError("invalid-argument", "uid must be a non-empty string.");
  }
  if (typeof walletAddress !== "string" || walletAddress.trim().length === 0) {
    throw new HttpsError("invalid-argument", "walletAddress must be a non-empty string.");
  }
  if (uid !== auth.uid) {
    throw new HttpsError("permission-denied", "UID mismatch for authenticated caller.");
  }
  if (!ethers.isAddress(walletAddress)) {
    throw new HttpsError("invalid-argument", "walletAddress is not a valid address.");
  }

  const db = admin.firestore();
  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  if (!userSnap.exists) {
    throw new HttpsError("not-found", "User profile not found.");
  }

  const userData = userSnap.data() || {};

  // Use server-side stored wallet, not the one from the client payload.
  const storedWallet = userData.walletAddress;
  if (typeof storedWallet !== "string" || !ethers.isAddress(storedWallet)) {
    throw new HttpsError(
      "failed-precondition",
      "No valid wallet address on file. Save your wallet first."
    );
  }
  if (storedWallet.toLowerCase() !== walletAddress.toLowerCase()) {
    throw new HttpsError(
      "permission-denied",
      "Wallet address does not match the one saved to your profile."
    );
  }

  if (userData.allComplete !== true) {
    throw new HttpsError(
      "failed-precondition",
      "All missions must be completed before minting."
    );
  }

  const missionsSnap = await userRef
    .collection("missions")
    .where("completed", "==", true)
    .get();

  if (missionsSnap.size < REQUIRED_MISSIONS) {
    throw new HttpsError(
      "failed-precondition",
      `Only ${missionsSnap.size} of ${REQUIRED_MISSIONS} missions are completed.`
    );
  }

  if (userData.souvenirMinted === true) {
    throw new HttpsError(
      "already-exists",
      "Souvenir has already been minted for this account."
    );
  }

  return { userRef, storedWallet };
}

/**
 * Mints the Volver Heritage Souvenir NFT to the user's wallet.
 * Gas is paid by the owner wallet configured in server secrets.
 */
exports.mintSouvenir = onCall(async (request) => {
  const data = request.data || {};
  const auth = request.auth;
  const uid = data.uid;
  const walletAddress = data.walletAddress;

  const { userRef, storedWallet } = await assertEligibleForMint(uid, walletAddress, auth);

  const { ownerKey, contractAddress, rpcUrl } = getPolygonConfig();
  if (!ownerKey || !contractAddress || !rpcUrl) {
    throw new HttpsError(
      "failed-precondition",
      "Missing polygon config. Set owner_key, contract_address, and rpc_url."
    );
  }
  if (!ethers.isAddress(contractAddress)) {
    throw new HttpsError(
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
    logger.error("mintSouvenir failed", {
      uid,
      error: error && error.message ? error.message : error,
    });
    throw new HttpsError(
      "internal",
      "Failed to mint souvenir on-chain."
    );
  }
});

exports.resetMissionProgress = onCall(async (request) => {
  const auth = request.auth;
  if (!auth || !auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const db = admin.firestore();
  const uid = auth.uid;
  const userRef = db.collection("users").doc(uid);
  const missionsSnap = await userRef.collection("missions").get();

  const batch = db.batch();
  missionsSnap.forEach((doc) => batch.delete(doc.ref));
  batch.set(userRef, {
    allComplete: false,
  }, { merge: true });
  await batch.commit();

  await syncUserPublicEntries(db, uid);

  logger.info("resetMissionProgress completed", {
    uid,
    deletedMissionCount: missionsSnap.size,
  });

  return {
    success: true,
    deletedMissionCount: missionsSnap.size,
  };
});

exports.syncHallOfExplorersOnMissionComplete = onDocumentCreated("users/{uid}/missions/{missionId}", async (event) => {
    const missionId = event.params.missionId;
    if (!MISSION_MAP.has(missionId)) {
      return null;
    }

    await syncUserPublicEntries(admin.firestore(), event.params.uid);
    return null;
  });

exports.syncHallOfExplorersOnUserUpdate = onDocumentUpdated("users/{uid}", async (event) => {
    const before = (event.data && event.data.before ? event.data.before.data() : null) || {};
    const after = (event.data && event.data.after ? event.data.after.data() : null) || {};
    if (!didLeaderboardProfileChange(before, after)) {
      return null;
    }

    await syncUserPublicEntries(admin.firestore(), event.params.uid);
    return null;
  });
