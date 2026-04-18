package com.wheic.arapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

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
 *   - Whitelisting is requested via the callable Cloud Function "whitelistWallet".
 */
public class MissionCompletionHelper {

    private static final int TOTAL_LANDMARKS = 5;

    public interface CompletionCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface ProgressCallback {
        /**
         * @param completedIds  Set of mission IDs the user has finished.
         * @param allComplete   True when all 5 missions are done.
         */
        void onResult(Set<String> completedIds, boolean allComplete);
        void onError(String message);
    }

    // ──────────────────────────────────────────────────────────────
    // Connectivity helper
    // ──────────────────────────────────────────────────────────────

    private static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
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

        if (!isConnected(context)) {
            Toast.makeText(context,
                    "No internet connection. Progress will sync when you reconnect.",
                    Toast.LENGTH_LONG).show();
            callback.onError("No internet connection.");
            return;
        }

        FirebaseFirestore db = FirebaseConfig.getFirestore();
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentReference missionRef = db
                    .collection("users")
                    .document(user.getUid())
                    .collection("missions")
                    .document(missionId);

            com.google.firebase.firestore.DocumentSnapshot existing = transaction.get(missionRef);
            Boolean alreadyCompleted = existing.getBoolean("completed");

            if (existing.exists() && Boolean.TRUE.equals(alreadyCompleted)) {
                return null;
            }

            Map<String, Object> missionData = new HashMap<>();
            missionData.put("completed", true);
            missionData.put("completedAt", FieldValue.serverTimestamp());
            missionData.put("missionId", missionId);
            transaction.set(missionRef, missionData);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Network error"));
    }

    /** Fetch which missions the user has already finished. */
    public static void getMissionProgress(Context context,
                                          ProgressCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in first.");
            return;
        }

        if (!isConnected(context)) {
            Toast.makeText(context,
                    "No internet connection. Progress will sync when you reconnect.",
                    Toast.LENGTH_LONG).show();
            callback.onError("No internet connection.");
            return;
        }

        FirebaseFirestore db = FirebaseConfig.getFirestore();
        db.collection("users")
                .document(user.getUid())
                .collection("missions")
                .whereEqualTo("completed", true)
                .limit(TOTAL_LANDMARKS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Set<String> completed = new HashSet<>();
                    querySnapshot.getDocuments().forEach(doc -> {
                        String id = doc.getString("missionId");
                        completed.add(id != null ? id : doc.getId());
                    });

                    boolean allComplete = completed.size() == TOTAL_LANDMARKS;
                    if (allComplete) {
                        db.collection("users")
                                .document(user.getUid())
                                .update("allComplete", true)
                                .addOnSuccessListener(unused -> callback.onResult(completed, true))
                                .addOnFailureListener(e -> callback.onError("Network error"));
                    } else {
                        callback.onResult(completed, false);
                    }
                })
                .addOnFailureListener(e -> callback.onError("Network error"));
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

        if (!isConnected(context)) {
            Toast.makeText(context,
                    "No internet connection. Progress will sync when you reconnect.",
                    Toast.LENGTH_LONG).show();
            callback.onError("No internet connection.");
            return;
        }

        FirebaseConfig.getFirestore()
                .collection("users")
                .document(user.getUid())
                .update("walletAddress", walletAddress)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Network error"));
    }

    /**
     * Tells the backend to whitelist this wallet on the smart contract.
     * Should be called after verifying all missions are complete.
     * The backend wallet (owner) pays the tiny gas for the whitelist transaction.
     */
    public static void requestWhitelist(Context context, String walletAddress,
                                        CompletionCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("Please sign in first.");
            return;
        }

        if (!isConnected(context)) {
            callback.onError("Whitelisting requires an internet connection.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("walletAddress", walletAddress);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("whitelistWallet")
                .call(data)
                .addOnSuccessListener(httpsCallableResult -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Network error"));
    }
}
