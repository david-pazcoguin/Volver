"use strict";

const admin = require("firebase-admin");

const COLLECTION_USERS = "users";
const COLLECTION_MISSIONS = "missions";
const COLLECTION_LEADERBOARDS = "leaderboards";
const COLLECTION_ENTRIES = "entries";

const VISIBILITY_PUBLIC = "public";
const VISIBILITY_ANONYMOUS = "anonymous";
const VISIBILITY_HIDDEN = "hidden";

const OVERALL_BOARD_ID = "overall_intramuros";
const PUBLIC_MISSIONS = [
  { missionId: "fort_santiago", boardId: "mission_fort_santiago", title: "Fort Santiago" },
  { missionId: "baluarte_san_diego", boardId: "mission_baluarte_de_san_diego", title: "Baluarte de San Diego" },
  { missionId: "casa_manila", boardId: "mission_casa_manila", title: "Casa Manila" },
  { missionId: "museo_intramuros", boardId: "mission_museo_intramuros", title: "Museo de Intramuros" },
  { missionId: "centro_turismo", boardId: "mission_centro_de_turismo", title: "Centro de Turismo" },
];

const MISSION_MAP = new Map(PUBLIC_MISSIONS.map((mission) => [mission.missionId, mission]));
const BOARD_META = new Map([
  [OVERALL_BOARD_ID, { boardType: "overall", title: "Overall Top Explorers", missionId: null }],
  ...PUBLIC_MISSIONS.map((mission) => [
    mission.boardId,
    { boardType: "mission", title: mission.title, missionId: mission.missionId },
  ]),
]);

function normalizeVisibility(value) {
  if (value === VISIBILITY_ANONYMOUS) {
    return VISIBILITY_ANONYMOUS;
  }
  if (value === VISIBILITY_HIDDEN) {
    return VISIBILITY_HIDDEN;
  }
  return VISIBILITY_PUBLIC;
}

function buildPublicIdentity(userData = {}) {
  const visibilityMode = normalizeVisibility(userData.leaderboardVisibility);
  if (visibilityMode === VISIBILITY_HIDDEN) {
    return null;
  }

  const username = typeof userData.username === "string" ? userData.username.trim() : "";
  const firstName = typeof userData.firstName === "string" ? userData.firstName.trim() : "";
  const lastName = typeof userData.lastName === "string" ? userData.lastName.trim() : "";
  const fullName = `${firstName} ${lastName}`.trim();

  if (visibilityMode === VISIBILITY_ANONYMOUS) {
    return {
      visibilityMode,
      displayNamePublic: "Anonymous Explorer",
      avatarInitial: "A",
    };
  }

  const displayNamePublic = username
    ? `@${username}`
    : (fullName || "Explorer");
  const avatarSource = firstName || username || "Explorer";
  return {
    visibilityMode,
    displayNamePublic,
    avatarInitial: avatarSource.substring(0, 1).toUpperCase(),
  };
}

async function getPublicMissionCompletionMap(db, uid) {
  const snap = await db.collection(COLLECTION_USERS)
    .doc(uid)
    .collection(COLLECTION_MISSIONS)
    .where("completed", "==", true)
    .get();

  const completions = new Map();
  snap.forEach((doc) => {
    const data = doc.data() || {};
    const missionId = data.missionId || doc.id;
    if (!MISSION_MAP.has(missionId)) {
      return;
    }
    const completedAt = data.completedAt || null;
    if (completedAt) {
      completions.set(missionId, completedAt);
    }
  });
  return completions;
}

async function deleteEntry(db, boardId, uid) {
  await db.collection(COLLECTION_LEADERBOARDS)
    .doc(boardId)
    .collection(COLLECTION_ENTRIES)
    .doc(uid)
    .delete();
}

async function setBoardDoc(db, boardId) {
  const meta = BOARD_META.get(boardId);
  if (!meta) {
    return;
  }
  await db.collection(COLLECTION_LEADERBOARDS)
    .doc(boardId)
    .set({
      boardType: meta.boardType,
      title: meta.title,
      missionId: meta.missionId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
}

async function upsertMissionEntry(db, uid, userData, missionId, completedAt) {
  const identity = buildPublicIdentity(userData);
  const mission = MISSION_MAP.get(missionId);
  if (!identity || !mission || !completedAt) {
    return;
  }
  await setBoardDoc(db, mission.boardId);
  await db.collection(COLLECTION_LEADERBOARDS)
    .doc(mission.boardId)
    .collection(COLLECTION_ENTRIES)
    .doc(uid)
    .set({
      uid,
      displayNamePublic: identity.displayNamePublic,
      avatarInitial: identity.avatarInitial,
      visibilityMode: identity.visibilityMode,
      missionId,
      missionCompletedAt: completedAt,
      souvenirMinted: userData.souvenirMinted === true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
}

async function upsertOverallEntry(db, uid, userData, completionMap) {
  const identity = buildPublicIdentity(userData);
  if (!identity || completionMap.size === 0) {
    return;
  }

  let sortCompletedAt = null;
  completionMap.forEach((timestamp) => {
    if (!sortCompletedAt || timestamp.toMillis() > sortCompletedAt.toMillis()) {
      sortCompletedAt = timestamp;
    }
  });

  const allIntramurosComplete = completionMap.size === PUBLIC_MISSIONS.length;

  await setBoardDoc(db, OVERALL_BOARD_ID);
  await db.collection(COLLECTION_LEADERBOARDS)
    .doc(OVERALL_BOARD_ID)
    .collection(COLLECTION_ENTRIES)
    .doc(uid)
    .set({
      uid,
      displayNamePublic: identity.displayNamePublic,
      avatarInitial: identity.avatarInitial,
      visibilityMode: identity.visibilityMode,
      intramurosMissionCount: completionMap.size,
      allIntramurosComplete,
      sortCompletedAt,
      allIntramurosCompletedAt: allIntramurosComplete ? sortCompletedAt : null,
      souvenirMinted: userData.souvenirMinted === true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
}

function compareTimestamps(a, b) {
  const aMillis = a ? a.toMillis() : Number.MAX_SAFE_INTEGER;
  const bMillis = b ? b.toMillis() : Number.MAX_SAFE_INTEGER;
  return aMillis - bMillis;
}

function compareOverallEntries(a, b) {
  const aComplete = a.allIntramurosComplete === true ? 1 : 0;
  const bComplete = b.allIntramurosComplete === true ? 1 : 0;
  if (aComplete !== bComplete) {
    return bComplete - aComplete;
  }

  const aCount = Number(a.intramurosMissionCount || 0);
  const bCount = Number(b.intramurosMissionCount || 0);
  if (aCount !== bCount) {
    return bCount - aCount;
  }

  const timeDiff = compareTimestamps(a.sortCompletedAt || null, b.sortCompletedAt || null);
  if (timeDiff !== 0) {
    return timeDiff;
  }

  return String(a.uid || "").localeCompare(String(b.uid || ""));
}

function compareMissionEntries(a, b) {
  const timeDiff = compareTimestamps(a.missionCompletedAt || null, b.missionCompletedAt || null);
  if (timeDiff !== 0) {
    return timeDiff;
  }
  return String(a.uid || "").localeCompare(String(b.uid || ""));
}

async function rerankBoard(db, boardId) {
  const meta = BOARD_META.get(boardId);
  if (!meta) {
    return;
  }

  const entriesRef = db.collection(COLLECTION_LEADERBOARDS)
    .doc(boardId)
    .collection(COLLECTION_ENTRIES);
  const snap = await entriesRef.get();
  const entries = snap.docs.map((doc) => ({ id: doc.id, ref: doc.ref, ...doc.data() }));
  entries.sort(meta.boardType === "overall" ? compareOverallEntries : compareMissionEntries);

  const batch = db.batch();
  entries.forEach((entry, index) => {
    batch.set(entry.ref, {
      rankPosition: index + 1,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  });

  batch.set(db.collection(COLLECTION_LEADERBOARDS).doc(boardId), {
    boardType: meta.boardType,
    title: meta.title,
    missionId: meta.missionId,
    participantCount: entries.length,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });

  await batch.commit();
}

async function rerankAllBoards(db) {
  await rerankBoard(db, OVERALL_BOARD_ID);
  for (const mission of PUBLIC_MISSIONS) {
    await rerankBoard(db, mission.boardId);
  }
}

async function syncUserPublicEntries(db, uid, options = {}) {
  const rerank = options.rerank !== false;
  const userSnap = await db.collection(COLLECTION_USERS).doc(uid).get();
  if (!userSnap.exists) {
    return;
  }

  const userData = userSnap.data() || {};
  const completionMap = await getPublicMissionCompletionMap(db, uid);
  const identity = buildPublicIdentity(userData);

  if (!identity) {
    await deleteEntry(db, OVERALL_BOARD_ID, uid);
    for (const mission of PUBLIC_MISSIONS) {
      await deleteEntry(db, mission.boardId, uid);
    }
  } else {
    for (const mission of PUBLIC_MISSIONS) {
      const completedAt = completionMap.get(mission.missionId) || null;
      if (completedAt) {
        await upsertMissionEntry(db, uid, userData, mission.missionId, completedAt);
      } else {
        await deleteEntry(db, mission.boardId, uid);
      }
    }

    if (completionMap.size > 0) {
      await upsertOverallEntry(db, uid, userData, completionMap);
    } else {
      await deleteEntry(db, OVERALL_BOARD_ID, uid);
    }
  }

  if (rerank) {
    await rerankAllBoards(db);
  }
}

function didLeaderboardProfileChange(before = {}, after = {}) {
  return before.username !== after.username
    || before.firstName !== after.firstName
    || before.lastName !== after.lastName
    || before.souvenirMinted !== after.souvenirMinted
    || normalizeVisibility(before.leaderboardVisibility) !== normalizeVisibility(after.leaderboardVisibility);
}

module.exports = {
  OVERALL_BOARD_ID,
  PUBLIC_MISSIONS,
  VISIBILITY_PUBLIC,
  VISIBILITY_ANONYMOUS,
  VISIBILITY_HIDDEN,
  MISSION_MAP,
  didLeaderboardProfileChange,
  rerankAllBoards,
  syncUserPublicEntries,
};
