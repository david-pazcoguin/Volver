package com.wheic.arapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.functions.FirebaseFunctions;

public final class FirebaseConfig {
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_MISSIONS = "missions";
    public static final String FIELD_WALLET = "walletAddress";
    public static final String FIELD_ALL_COMPLETE = "allComplete";
    public static final String FIELD_WHITELISTED = "whitelisted";

    private static volatile boolean firestoreInitialized = false;

    private FirebaseConfig() {
        // Utility class
    }

    public static synchronized FirebaseFirestore getFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (!firestoreInitialized) {
            FirebaseFirestoreSettings settings =
                    new FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(true)
                            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build();
            db.setFirestoreSettings(settings);
            firestoreInitialized = true;
        }
        return db;
    }

    public static FirebaseAuth getAuth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFunctions getFunctions() {
        return FirebaseFunctions.getInstance();
    }
}