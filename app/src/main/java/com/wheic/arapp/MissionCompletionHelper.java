package com.wheic.arapp;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handles all server-side mission completion tracking and wallet registration.
 *
 * Required PHP scripts (see backend/ folder for implementation):
 *   - complete_mission.php
 *   - get_missions.php
 *   - save_wallet.php
 *   - whitelist_wallet.php
 */
public class MissionCompletionHelper {

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
    // Mission tracking
    // ──────────────────────────────────────────────────────────────

    /** Mark a single mission as complete for the current user. */
    public static void completeMission(Context context, String username, String missionId,
                                       CompletionCallback callback) {
        postRequest(context, URLDatabase.URL_COMPLETE_MISSION, callback, params -> {
            params.put("username",   username);
            params.put("mission_id", missionId);
        });
    }

    /** Fetch which missions the user has already finished. */
    public static void getMissionProgress(Context context, String username,
                                          ProgressCallback callback) {
        RequestQueue queue = Volley.newRequestQueue(context);

        StringRequest req = new StringRequest(Request.Method.POST,
                URLDatabase.URL_GET_MISSIONS,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if ("success".equals(json.getString("status"))) {
                            Set<String> completed = new HashSet<>();
                            JSONArray arr = json.getJSONArray("completed_missions");
                            for (int i = 0; i < arr.length(); i++) {
                                completed.add(arr.getString(i));
                            }
                            boolean allComplete = json.optBoolean("all_complete", false);
                            callback.onResult(completed, allComplete);
                        } else {
                            callback.onError(json.optString("message", "Unknown error"));
                        }
                    } catch (JSONException e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> callback.onError("Network error")) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("username", username);
                return p;
            }
        };

        queue.add(req);
    }

    // ──────────────────────────────────────────────────────────────
    // Wallet registration
    // ──────────────────────────────────────────────────────────────

    /** Store the user's Polygon wallet address on the server. */
    public static void saveWalletAddress(Context context, String username, String walletAddress,
                                         CompletionCallback callback) {
        postRequest(context, URLDatabase.URL_SAVE_WALLET, callback, params -> {
            params.put("username",       username);
            params.put("wallet_address", walletAddress);
        });
    }

    /**
     * Tells the backend to whitelist this wallet on the smart contract.
     * Should be called after verifying all missions are complete.
     * The backend wallet (owner) pays the tiny gas for the whitelist transaction.
     */
    public static void requestWhitelist(Context context, String username, String walletAddress,
                                        CompletionCallback callback) {
        postRequest(context, URLDatabase.URL_WHITELIST_WALLET, callback, params -> {
            params.put("username",       username);
            params.put("wallet_address", walletAddress);
        });
    }

    // ──────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────

    interface ParamsFiller {
        void fill(Map<String, String> params);
    }

    private static void postRequest(Context context, String url,
                                    CompletionCallback callback, ParamsFiller filler) {
        RequestQueue queue = Volley.newRequestQueue(context);

        StringRequest req = new StringRequest(Request.Method.POST, url,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if ("success".equals(json.getString("status"))) {
                            callback.onSuccess();
                        } else {
                            callback.onError(json.optString("message", "Unknown error"));
                        }
                    } catch (JSONException e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> callback.onError("Network error")) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                filler.fill(params);
                return params;
            }
        };

        queue.add(req);
    }
}
