"use strict";

const admin = require("firebase-admin");
const { rerankAllBoards, syncUserPublicEntries } = require("./leaderboards");

if (!admin.apps.length) {
  admin.initializeApp();
}

async function main() {
  const db = admin.firestore();
  const usersSnap = await db.collection("users").get();

  for (const userDoc of usersSnap.docs) {
    await syncUserPublicEntries(db, userDoc.id, { rerank: false });
  }

  await rerankAllBoards(db);
  console.log(`Backfilled Hall of Explorers for ${usersSnap.size} users.`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Failed to backfill Hall of Explorers.", error);
    process.exit(1);
  });
