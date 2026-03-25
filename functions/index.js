// SECURITY: Never commit real values of owner_key to source control
// Set via: firebase functions:config:set polygon.owner_key="YOUR_KEY"

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { ethers } = require("ethers");

if (!admin.apps.length) {
  admin.initializeApp();
}

const REQUIRED_MISSIONS = 5;

exports.whitelistWallet = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required."
    );
  }

  const uid = data && data.uid;
  const walletAddress = data && data.walletAddress;

  if (typeof uid !== "string" || uid.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "uid must be a non-empty string."
    );
  }

  if (typeof walletAddress !== "string" || walletAddress.trim().length === 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "walletAddress must be a non-empty string."
    );
  }

  if (uid !== context.auth.uid) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "UID mismatch for authenticated caller."
    );
  }

  if (!ethers.isAddress(walletAddress)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "walletAddress is not a valid address."
    );
  }

  const db = admin.firestore();
  const userRef = db.collection("users").doc(uid);

  const userSnap = await userRef.get();
  if (!userSnap.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "User profile not found."
    );
  }

  const userData = userSnap.data() || {};
  if (userData.allComplete !== true) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "All missions must be completed before whitelisting."
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

  const polygonConfig = functions.config().polygon || {};
  const ownerKey = polygonConfig.owner_key;
  const contractAddress = polygonConfig.contract_address;
  const rpcUrl = polygonConfig.rpc_url;

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
    const signer = new ethers.Wallet(ownerKey, provider);
    const abi = ["function whitelistAddress(address wallet) external"];
    const contract = new ethers.Contract(contractAddress, abi, signer);

    const tx = await contract.whitelistAddress(walletAddress);
    await tx.wait();

    await userRef.update({
      whitelisted: true,
      whitelistedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true };
  } catch (error) {
    console.error("whitelistWallet failed", { uid, error: error && error.message ? error.message : error });
    throw new functions.https.HttpsError(
      "internal",
      "Failed to whitelist wallet on-chain."
    );
  }
});