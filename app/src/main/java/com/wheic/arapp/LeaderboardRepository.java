package com.wheic.arapp;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads public Hall of Explorers data from Firestore, with cache fallback.
 */
public final class LeaderboardRepository {

    public interface LoadCallback {
        void onSuccess(LeaderboardLoadResult result);
        void onError(String message);
    }

    public static final String BOARD_OVERALL_INTRAMUROS = "overall_intramuros";
    public static final String BOARD_MISSION_FORT_SANTIAGO = "mission_fort_santiago";
    public static final String BOARD_MISSION_BALUARTE_DE_SAN_DIEGO = "mission_baluarte_de_san_diego";
    public static final String BOARD_MISSION_CASA_MANILA = "mission_casa_manila";
    public static final String BOARD_MISSION_MUSEO_INTRAMUROS = "mission_museo_intramuros";
    public static final String BOARD_MISSION_CENTRO_DE_TURISMO = "mission_centro_de_turismo";
    public static final String BOARD_MISSION_SAN_AGUSTIN_CHURCH = "mission_san_agustin_church";
    public static final String BOARD_MISSION_MANILA_CATHEDRAL = "mission_manila_cathedral";
    public static final int PUBLIC_INTRAMUROS_MISSION_COUNT = 7;

    public static final String VISIBILITY_PUBLIC = "public";
    public static final String VISIBILITY_ANONYMOUS = "anonymous";
    public static final String VISIBILITY_HIDDEN = "hidden";
    private static final int DISPLAY_ENTRY_COUNT = 10;

    private final FirebaseFirestore firestore;

    public LeaderboardRepository() {
        this(FirebaseConfig.getFirestore());
    }

    LeaderboardRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void loadBoard(String boardId, LoadCallback callback) {
        if (callback == null) {
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in first.");
            return;
        }

        loadBoardFromSource(boardId, user.getUid(), Source.SERVER)
                .addOnSuccessListener(result -> callback.onSuccess(result))
                .addOnFailureListener(serverError ->
                        loadBoardFromSource(boardId, user.getUid(), Source.CACHE)
                                .addOnSuccessListener(result -> callback.onSuccess(result))
                                .addOnFailureListener(cacheError -> callback.onError("Unable to load rankings.")));
    }

    private Task<LeaderboardLoadResult> loadBoardFromSource(String boardId, String uid, Source source) {
        Query topQuery = boardEntries(boardId)
                .orderBy(FirebaseConfig.FIELD_RANK_POSITION, Query.Direction.ASCENDING)
                .limit(DISPLAY_ENTRY_COUNT);
        Task<QuerySnapshot> topTask = topQuery.get(source);
        Task<DocumentSnapshot> currentEntryTask = boardEntries(boardId).document(uid).get(source);
        Task<DocumentSnapshot> userTask = userDoc(uid).get(source);

        return Tasks.whenAllSuccess(topTask, currentEntryTask, userTask)
                .continueWith(task -> {
                    QuerySnapshot topSnapshot = (QuerySnapshot) task.getResult().get(0);
                    DocumentSnapshot currentEntrySnapshot = (DocumentSnapshot) task.getResult().get(1);
                    DocumentSnapshot userSnapshot = (DocumentSnapshot) task.getResult().get(2);

                    List<LeaderboardEntry> topEntries = new ArrayList<>();
                    boolean fromCache = false;
                    if (topSnapshot != null) {
                        fromCache = topSnapshot.getMetadata().isFromCache();
                        for (DocumentSnapshot doc : topSnapshot.getDocuments()) {
                            LeaderboardEntry entry = LeaderboardEntry.from(doc);
                            if (entry != null) {
                                topEntries.add(entry);
                            }
                        }
                    }

                    LeaderboardEntry currentUserEntry = LeaderboardEntry.from(currentEntrySnapshot);
                    String visibility = VISIBILITY_PUBLIC;
                    if (userSnapshot != null && userSnapshot.exists()) {
                        String stored = userSnapshot.getString(FirebaseConfig.FIELD_LEADERBOARD_VISIBILITY);
                        if (stored != null && !stored.trim().isEmpty()) {
                            visibility = stored;
                        }
                        fromCache = fromCache || userSnapshot.getMetadata().isFromCache();
                    }
                    if (currentEntrySnapshot != null && currentEntrySnapshot.exists()) {
                        fromCache = fromCache || currentEntrySnapshot.getMetadata().isFromCache();
                    }

                    return new LeaderboardLoadResult(
                            withSyntheticEntries(boardId, topEntries),
                            currentUserEntry,
                            visibility,
                            fromCache);
                });
    }

    private List<LeaderboardEntry> withSyntheticEntries(String boardId, List<LeaderboardEntry> realEntries) {
        List<LeaderboardEntry> combined = new ArrayList<>();
        if (realEntries != null) {
            combined.addAll(realEntries);
        }
        if (combined.size() >= DISPLAY_ENTRY_COUNT) {
            return combined;
        }

        int nextRank = combined.size() + 1;
        for (SyntheticSeed seed : syntheticSeedsForBoard(boardId)) {
            if (combined.size() >= DISPLAY_ENTRY_COUNT) {
                break;
            }
            combined.add(seed.toEntry(boardId, nextRank++));
        }
        return combined;
    }

    private List<SyntheticSeed> syntheticSeedsForBoard(String boardId) {
        List<SyntheticSeed> seeds = new ArrayList<>();
        if (BOARD_OVERALL_INTRAMUROS.equals(boardId)) {
            seeds.add(new SyntheticSeed("Citadel Sprint", "C", "Overall pace - 15m 44s"));
            seeds.add(new SyntheticSeed("Wall Circuit", "W", "Overall pace - 16m 18s"));
            seeds.add(new SyntheticSeed("Heritage Dash", "H", "Overall pace - 17m 09s"));
            seeds.add(new SyntheticSeed("Arcade Track", "A", "Overall pace - 18m 37s"));
        } else if (BOARD_MISSION_FORT_SANTIAGO.equals(boardId)) {
            seeds.add(new SyntheticSeed("Bastion Bolt", "B", "Clear time - 15m 12s"));
            seeds.add(new SyntheticSeed("Gate Sprinters", "G", "Clear time - 16m 01s"));
            seeds.add(new SyntheticSeed("Cannon Crew", "C", "Clear time - 17m 28s"));
            seeds.add(new SyntheticSeed("Moat Dash", "M", "Clear time - 19m 04s"));
        } else if (BOARD_MISSION_BALUARTE_DE_SAN_DIEGO.equals(boardId)) {
            seeds.add(new SyntheticSeed("Rampart Rush", "R", "Clear time - 15m 26s"));
            seeds.add(new SyntheticSeed("Diego Dashers", "D", "Clear time - 16m 33s"));
            seeds.add(new SyntheticSeed("Wallwalk Club", "W", "Clear time - 18m 02s"));
            seeds.add(new SyntheticSeed("Baluarte Beat", "B", "Clear time - 19m 18s"));
        } else if (BOARD_MISSION_CASA_MANILA.equals(boardId)) {
            seeds.add(new SyntheticSeed("Patio Pace", "P", "Clear time - 15m 40s"));
            seeds.add(new SyntheticSeed("Heritage Steps", "H", "Clear time - 16m 48s"));
            seeds.add(new SyntheticSeed("Casa Clockers", "C", "Clear time - 17m 36s"));
            seeds.add(new SyntheticSeed("Balcony Stride", "B", "Clear time - 19m 11s"));
        } else if (BOARD_MISSION_MUSEO_INTRAMUROS.equals(boardId)) {
            seeds.add(new SyntheticSeed("Gallery Glide", "G", "Clear time - 15m 21s"));
            seeds.add(new SyntheticSeed("Relic Relay", "R", "Clear time - 16m 17s"));
            seeds.add(new SyntheticSeed("Curator Crew", "C", "Clear time - 17m 50s"));
            seeds.add(new SyntheticSeed("Vault Velocity", "V", "Clear time - 19m 07s"));
        } else if (BOARD_MISSION_CENTRO_DE_TURISMO.equals(boardId)) {
            seeds.add(new SyntheticSeed("Plaza Pulse", "P", "Clear time - 15m 34s"));
            seeds.add(new SyntheticSeed("Guidepost Crew", "G", "Clear time - 16m 29s"));
            seeds.add(new SyntheticSeed("Compass Lane", "C", "Clear time - 17m 42s"));
            seeds.add(new SyntheticSeed("Turismo Track", "T", "Clear time - 18m 58s"));
        } else if (BOARD_MISSION_SAN_AGUSTIN_CHURCH.equals(boardId)) {
            seeds.add(new SyntheticSeed("Bell Tower Run", "B", "Clear time - 15m 55s"));
            seeds.add(new SyntheticSeed("Augustine Pace", "A", "Clear time - 16m 37s"));
            seeds.add(new SyntheticSeed("Stone Nave", "S", "Clear time - 17m 24s"));
            seeds.add(new SyntheticSeed("Choir Lane", "C", "Clear time - 19m 16s"));
        } else if (BOARD_MISSION_MANILA_CATHEDRAL.equals(boardId)) {
            seeds.add(new SyntheticSeed("Cathedral Circuit", "C", "Clear time - 15m 47s"));
            seeds.add(new SyntheticSeed("Rose Window Dash", "R", "Clear time - 16m 56s"));
            seeds.add(new SyntheticSeed("Sanctuary Sprint", "S", "Clear time - 17m 39s"));
            seeds.add(new SyntheticSeed("Belfry Route", "B", "Clear time - 19m 12s"));
        }
        return seeds;
    }

    private static final class SyntheticSeed {
        private final String name;
        private final String avatar;
        private final String detail;

        SyntheticSeed(String name, String avatar, String detail) {
            this.name = name;
            this.avatar = avatar;
            this.detail = detail;
        }

        LeaderboardEntry toEntry(String boardId, int rankPosition) {
            boolean overall = BOARD_OVERALL_INTRAMUROS.equals(boardId);
            return new LeaderboardEntry(
                    "synthetic_" + boardId + "_" + rankPosition,
                    name,
                    avatar,
                    VISIBILITY_PUBLIC,
                    overall ? PUBLIC_INTRAMUROS_MISSION_COUNT : 0,
                    overall,
                    false,
                    rankPosition,
                    overall ? null : missionIdForBoard(boardId),
                    syntheticTimestamp(rankPosition),
                    syntheticTimestamp(rankPosition),
                    true,
                    detail);
        }

        private static Timestamp syntheticTimestamp(int rankPosition) {
            long baseMillis = 1_893_456_000_000L;
            long offsetMillis = rankPosition * 86_400_000L;
            return new Timestamp(new java.util.Date(baseMillis + offsetMillis));
        }

        private static String missionIdForBoard(String boardId) {
            switch (boardId) {
                case BOARD_MISSION_FORT_SANTIAGO:
                    return "fort_santiago";
                case BOARD_MISSION_BALUARTE_DE_SAN_DIEGO:
                    return "baluarte_san_diego";
                case BOARD_MISSION_CASA_MANILA:
                    return "casa_manila";
                case BOARD_MISSION_MUSEO_INTRAMUROS:
                    return "museo_intramuros";
                case BOARD_MISSION_CENTRO_DE_TURISMO:
                    return "centro_turismo";
                case BOARD_MISSION_SAN_AGUSTIN_CHURCH:
                    return "san_agustin_church";
                case BOARD_MISSION_MANILA_CATHEDRAL:
                    return "manila_cathedral";
                default:
                    return null;
            }
        }
    }

    private DocumentReference userDoc(String uid) {
        return firestore.collection(FirebaseConfig.COLLECTION_USERS).document(uid);
    }

    private com.google.firebase.firestore.CollectionReference boardEntries(String boardId) {
        return firestore.collection(FirebaseConfig.COLLECTION_LEADERBOARDS)
                .document(boardId)
                .collection("entries");
    }
}
