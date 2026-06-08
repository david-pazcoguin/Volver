package com.wheic.arapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.functions.FirebaseFunctions;

public final class FirebaseConfig {
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_MISSIONS = "missions";
    public static final String COLLECTION_LEADERBOARDS = "leaderboards";
    public static final String FIELD_WALLET = "walletAddress";
    public static final String FIELD_ALL_COMPLETE = "allComplete";
    public static final String FIELD_WHITELISTED = "whitelisted";
    public static final String FIELD_MISSION_ID = "missionId";
    public static final String FIELD_COMPLETED = "completed";
    public static final String FIELD_COMPLETED_AT = "completedAt";
    public static final String FIELD_LEADERBOARD_VISIBILITY = "leaderboardVisibility";
    public static final String FIELD_DISPLAY_NAME_PUBLIC = "displayNamePublic";
    public static final String FIELD_AVATAR_INITIAL = "avatarInitial";
    public static final String FIELD_VISIBILITY_MODE = "visibilityMode";
    public static final String FIELD_INTRAMUROS_MISSION_COUNT = "intramurosMissionCount";
    public static final String FIELD_ALL_INTRAMUROS_COMPLETE = "allIntramurosComplete";
    public static final String FIELD_SORT_COMPLETED_AT = "sortCompletedAt";
    public static final String FIELD_RANK_POSITION = "rankPosition";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_SOUVENIR_MINTED = "souvenirMinted";
    public static final String FIELD_BOARD_TYPE = "boardType";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_PARTICIPANT_COUNT = "participantCount";

    private static volatile FirebaseFirestore firestore;

    private FirebaseConfig() { /* utility class */ }

    public static FirebaseFirestore getFirestore() {
        FirebaseFirestore local = firestore;
        if (local == null) {
            synchronized (FirebaseConfig.class) {
                local = firestore;
                if (local == null) {
                    local = FirebaseFirestore.getInstance();
                    FirebaseFirestoreSettings settings =
                            new FirebaseFirestoreSettings.Builder()
                                    .setLocalCacheSettings(
                                            PersistentCacheSettings.newBuilder()
                                                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                                    .build())
                                    .build();
                    local.setFirestoreSettings(settings);
                    firestore = local;
                }
            }
        }
        return local;
    }

    public static FirebaseAuth getAuth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFunctions getFunctions() {
        return FirebaseFunctions.getInstance();
    }
}
