package com.wheic.arapp;

import android.content.Context;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handles all server-side mission completion tracking and wallet registration.
 *
 * Firebase implementation details:
 *   - Mission completion/progress are stored in Firestore under users/{uid}/missions.
 *   - Wallet address is stored on the users/{uid} document.
 *   - NFT minting is performed by the "mintSouvenir" Cloud Function (invoked from
 *     {@link NFTClaimActivity}); this helper no longer triggers whitelisting.
 *
 * Offline behavior:
 *   Firestore's local persistence (enabled in {@link FirebaseConfig}) queues
 *   writes until the network returns. The success callback fires on the local
 *   commit, so the user sees immediate feedback and the server syncs later.
 */
public class MissionCompletionHelper {

    private static final int TOTAL_LANDMARKS = 6;

    /**
     * Whitelist of valid mission IDs. Must stay in sync with the list in
     * {@code firestore.rules}. Client-side validation prevents doomed
     * round-trips when a caller passes a typo or a legacy ID.
     */
    public static final Set<String> ALLOWED_MISSION_IDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "fort_santiago",
                    "baluarte_san_diego",
                    "casa_manila",
                    "museo_intramuros",
                    "centro_turismo",
                    "lpu")));

    public interface CompletionCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface ProgressCallback {
        /**
         * @param completedIds  Set of mission IDs the user has finished.
         * @param allComplete   True when all 6 missions are done.
         */
        void onResult(Set<String> completedIds, boolean allComplete);
        void onError(String message);
    }

    // ──────────────────────────────────────────────────────────────
    // Mission tracking
    // ──────────────────────────────────────────────────────────────

    /** Mark a single mission as complete for the current user. */
    public static void completeMission(Context context, String missionId,
                                       CompletionCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in first.");
            return;
        }

        if (missionId == null || !ALLOWED_MISSION_IDS.contains(missionId)) {
            callback.onError("Unknown mission.");
            return;
        }

        FirebaseFirestore db = FirebaseConfig.getFirestore();
        DocumentReference missionRef = db
                .collection(FirebaseConfig.COLLECTION_USERS)
                .document(user.getUid())
                .collection(FirebaseConfig.COLLECTION_MISSIONS)
                .document(missionId);

        // Read from the local cache first so the check works offline. The rules
        // forbid update-on-missions, so we must only write if the doc doesn't
        // already exist. Firestore's offline queue takes care of the rest.
        missionRef.get(Source.CACHE)
                .addOnSuccessListener(snap -> maybeWriteCompletion(missionRef, snap, missionId, callback))
                .addOnFailureListener(cacheMiss ->
                        missionRef.get(Source.SERVER)
                                .addOnSuccessListener(snap -> maybeWriteCompletion(missionRef, snap, missionId, callback))
                                .addOnFailureListener(e -> {
                                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e);
                                    // Could not confirm existence. Attempt a blind write — rules will
                                    // reject the second one if it ever lands as an update.
                                    writeCompletion(missionRef, missionId, callback);
                                }));
    }

    private static void maybeWriteCompletion(DocumentReference ref,
                                             DocumentSnapshot existing,
                                             String missionId,
                                             CompletionCallback callback) {
        if (existing != null && existing.exists()
                && Boolean.TRUE.equals(existing.getBoolean(FirebaseConfig.FIELD_COMPLETED))) {
            callback.onSuccess();
            return;
        }
        writeCompletion(ref, missionId, callback);
    }

    private static void writeCompletion(DocumentReference ref,
                                        String missionId,
                                        CompletionCallback callback) {
        Map<String, Object> missionData = new HashMap<>();
        missionData.put(FirebaseConfig.FIELD_COMPLETED, true);
        missionData.put(FirebaseConfig.FIELD_COMPLETED_AT, FieldValue.serverTimestamp());
        missionData.put(FirebaseConfig.FIELD_MISSION_ID, missionId);
        ref.set(missionData)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e);
                    callback.onError("Network error");
                });
    }

    /** Fetch which missions the user has already finished. */
    public static void getMissionProgress(Context context,
                                          ProgressCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in first.");
            return;
        }

        FirebaseFirestore db = FirebaseConfig.getFirestore();
        db.collection(FirebaseConfig.COLLECTION_USERS)
                .document(user.getUid())
                .collection(FirebaseConfig.COLLECTION_MISSIONS)
                .whereEqualTo(FirebaseConfig.FIELD_COMPLETED, true)
                .limit(TOTAL_LANDMARKS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Set<String> completed = new HashSet<>();
                    querySnapshot.getDocuments().forEach(doc -> {
                        String id = doc.getString(FirebaseConfig.FIELD_MISSION_ID);
                        completed.add(id != null ? id : doc.getId());
                    });

                    boolean allComplete = completed.size() == TOTAL_LANDMARKS;
                    if (allComplete) {
                        db.collection(FirebaseConfig.COLLECTION_USERS)
                                .document(user.getUid())
                                .update(FirebaseConfig.FIELD_ALL_COMPLETE, true)
                                .addOnSuccessListener(unused -> callback.onResult(completed, true))
                                .addOnFailureListener(e -> { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e); callback.onError("Network error"); });
                    } else {
                        callback.onResult(completed, false);
                    }
                })
                .addOnFailureListener(e -> { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e); callback.onError("Network error"); });
    }

    // ──────────────────────────────────────────────────────────────
    // Wallet registration
    // ──────────────────────────────────────────────────────────────

    /** Store the user's Polygon wallet address on the server. */
    public static void saveWalletAddress(Context context, String walletAddress,
                                         CompletionCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in first.");
            return;
        }

        if (!NetworkUtils.isConnected(context)) {
            Toast.makeText(context,
                    "No internet connection. Progress will sync when you reconnect.",
                    Toast.LENGTH_LONG).show();
            callback.onError("No internet connection.");
            return;
        }

        FirebaseConfig.getFirestore()
                .collection(FirebaseConfig.COLLECTION_USERS)
                .document(user.getUid())
                .update(FirebaseConfig.FIELD_WALLET, walletAddress)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e); callback.onError("Network error"); });
    }
}
