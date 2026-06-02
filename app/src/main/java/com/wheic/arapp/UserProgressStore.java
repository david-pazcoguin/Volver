package com.wheic.arapp;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * User-scoped local progress for relic slots and collectible counts.
 */
public final class UserProgressStore {

    private static final int DEFAULT_COLLECTIBLE_MAX = 12;
    private static final String LEGACY_COLLECTIBLE_PREFIX = "collectible_";
    private static final String COLLECTIBLE_PREFIX = "user_collectible_";
    private static final String MISSION_PREFIX = "user_mission_relics_";

    private UserProgressStore() {
    }

    public static boolean[] loadCollectedSlots(Context context, String missionId, int totalSlots) {
        boolean[] slots = new boolean[Math.max(0, totalSlots)];
        if (context == null || missionId == null || missionId.isEmpty() || totalSlots <= 0) {
            return slots;
        }

        String saved = SecurePrefs.get(context).getString(missionProgressKey(missionId), "");
        if (saved.length() != totalSlots) {
            return slots;
        }

        int limit = Math.min(saved.length(), slots.length);
        for (int i = 0; i < limit; i++) {
            slots[i] = saved.charAt(i) == '1';
        }
        return slots;
    }

    public static boolean markRelicSlotCollected(Context context, String missionId, int slot, int totalSlots) {
        if (context == null || missionId == null || missionId.isEmpty()
                || slot < 0 || slot >= totalSlots) {
            return false;
        }

        SharedPreferences prefs = SecurePrefs.get(context);
        String key = missionProgressKey(missionId);
        boolean[] slots = loadCollectedSlots(context, missionId, totalSlots);
        boolean firstCollection = !slots[slot];
        slots[slot] = true;
        prefs.edit().putString(key, encodeSlots(slots)).apply();
        return firstCollection;
    }

    public static void removeMissionProgress(Context context, SharedPreferences.Editor editor, String missionId) {
        if (context == null || editor == null || missionId == null || missionId.isEmpty()) {
            return;
        }
        editor.remove(missionProgressKey(missionId));
    }

    public static int getCollectibleCount(Context context, String collectibleId) {
        if (context == null || collectibleId == null || collectibleId.isEmpty()) {
            return 0;
        }
        SharedPreferences prefs = SecurePrefs.get(context);
        int scoped = prefs.getInt(collectibleKey(collectibleId), -1);
        int legacy = prefs.getInt(legacyCollectibleKey(collectibleId), 0);
        return Math.max(scoped, legacy);
    }

    public static void incrementCollectibleCount(Context context, String collectibleId) {
        incrementCollectibleCount(context, collectibleId, DEFAULT_COLLECTIBLE_MAX);
    }

    public static void incrementCollectibleCount(Context context, String collectibleId, int maxCount) {
        if (context == null || collectibleId == null || collectibleId.isEmpty()) {
            return;
        }
        int current = getCollectibleCount(context, collectibleId);
        if (current >= maxCount) {
            return;
        }
        SecurePrefs.get(context).edit()
                .putInt(collectibleKey(collectibleId), current + 1)
                .apply();
    }

    public static void putCollectibleCount(Context context, SharedPreferences.Editor editor,
                                           String collectibleId, int count) {
        if (context == null || editor == null || collectibleId == null || collectibleId.isEmpty()) {
            return;
        }
        editor.putInt(collectibleKey(collectibleId), Math.max(0, count));
        editor.remove(legacyCollectibleKey(collectibleId));
    }

    private static String encodeSlots(boolean[] slots) {
        StringBuilder sb = new StringBuilder(slots.length);
        for (boolean collected : slots) {
            sb.append(collected ? '1' : '0');
        }
        return sb.toString();
    }

    private static String missionProgressKey(String missionId) {
        return MISSION_PREFIX + userScope() + "_" + missionId;
    }

    private static String collectibleKey(String collectibleId) {
        return COLLECTIBLE_PREFIX + userScope() + "_" + collectibleId + "_count";
    }

    private static String legacyCollectibleKey(String collectibleId) {
        return LEGACY_COLLECTIBLE_PREFIX + collectibleId + "_count";
    }

    private static String userScope() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid() != null && !user.getUid().isEmpty()) {
            return user.getUid();
        }
        return "signed_out";
    }
}
