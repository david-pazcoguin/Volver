package com.wheic.arapp;

import android.content.Context;

/**
 * Shared storage for the hidden mission bypass state used during on-device testing.
 */
public final class MissionBypassState {

    public static final String PREF_MISSION_BYPASS_STATE = "mission_bypass_state";
    private static final String PREF_RESET_COMPLETED_IDS = "mission_reset_completed_ids";
    public static final String BYPASS_STATE_COMPLETE = "complete";
    public static final String BYPASS_STATE_RESET = "reset";

    private MissionBypassState() {
    }

    public static String getState(Context context) {
        if (context == null) {
            return "";
        }
        return SecurePrefs.get(context).getString(PREF_MISSION_BYPASS_STATE, "");
    }

    public static void setState(Context context, String state) {
        if (context == null) {
            return;
        }
        SecurePrefs.get(context).edit()
                .putString(PREF_MISSION_BYPASS_STATE, state != null ? state : "")
                .apply();
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        SecurePrefs.get(context).edit()
                .remove(PREF_MISSION_BYPASS_STATE)
                .apply();
    }

    public static java.util.Set<String> getResetCompletedIds(Context context) {
        if (context == null) {
            return new java.util.HashSet<>();
        }
        java.util.Set<String> stored = SecurePrefs.get(context)
                .getStringSet(PREF_RESET_COMPLETED_IDS, java.util.Collections.emptySet());
        return new java.util.HashSet<>(stored);
    }

    public static void addResetCompletedMission(Context context, String missionId) {
        if (context == null || missionId == null || missionId.isEmpty()) {
            return;
        }
        java.util.Set<String> ids = getResetCompletedIds(context);
        ids.add(missionId);
        SecurePrefs.get(context).edit()
                .putStringSet(PREF_RESET_COMPLETED_IDS, ids)
                .apply();
    }

    public static void clearResetCompletedMissions(Context context) {
        if (context == null) {
            return;
        }
        SecurePrefs.get(context).edit()
                .remove(PREF_RESET_COMPLETED_IDS)
                .apply();
    }
}
