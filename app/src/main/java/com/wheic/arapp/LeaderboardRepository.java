package com.wheic.arapp;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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

    public static final String VISIBILITY_PUBLIC = "public";
    public static final String VISIBILITY_ANONYMOUS = "anonymous";
    public static final String VISIBILITY_HIDDEN = "hidden";

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
                .limit(10);
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

                    return new LeaderboardLoadResult(topEntries, currentUserEntry, visibility, fromCache);
                });
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
