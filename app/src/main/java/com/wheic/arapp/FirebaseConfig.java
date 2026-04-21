package com.wheic.arapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.functions.FirebaseFunctions;

public final class FirebaseConfig {
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_MISSIONS = "missions";
    public static final String FIELD_WALLET = "walletAddress";
    public static final String FIELD_ALL_COMPLETE = "allComplete";
    public static final String FIELD_WHITELISTED = "whitelisted";
    public static final String FIELD_MISSION_ID = "missionId";
    public static final String FIELD_COMPLETED = "completed";
    public static final String FIELD_COMPLETED_AT = "completedAt";

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