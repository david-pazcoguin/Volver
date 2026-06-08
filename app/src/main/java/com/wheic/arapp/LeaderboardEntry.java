package com.wheic.arapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Public-safe leaderboard row mirrored by server-side aggregation.
 */
public final class LeaderboardEntry {

    private final String uid;
    private final String displayNamePublic;
    private final String avatarInitial;
    private final String visibilityMode;
    private final int intramurosMissionCount;
    private final boolean allIntramurosComplete;
    private final boolean souvenirMinted;
    private final int rankPosition;
    private final String missionId;
    private final Timestamp missionCompletedAt;
    private final Timestamp sortCompletedAt;

    public LeaderboardEntry(String uid,
                            String displayNamePublic,
                            String avatarInitial,
                            String visibilityMode,
                            int intramurosMissionCount,
                            boolean allIntramurosComplete,
                            boolean souvenirMinted,
                            int rankPosition,
                            String missionId,
                            Timestamp missionCompletedAt,
                            Timestamp sortCompletedAt) {
        this.uid = uid;
        this.displayNamePublic = displayNamePublic;
        this.avatarInitial = avatarInitial;
        this.visibilityMode = visibilityMode;
        this.intramurosMissionCount = intramurosMissionCount;
        this.allIntramurosComplete = allIntramurosComplete;
        this.souvenirMinted = souvenirMinted;
        this.rankPosition = rankPosition;
        this.missionId = missionId;
        this.missionCompletedAt = missionCompletedAt;
        this.sortCompletedAt = sortCompletedAt;
    }

    public static LeaderboardEntry from(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }
        String uid = doc.getString("uid");
        if (uid == null || uid.trim().isEmpty()) {
            uid = doc.getId();
        }
        return new LeaderboardEntry(
                uid,
                safe(doc.getString(FirebaseConfig.FIELD_DISPLAY_NAME_PUBLIC), "Explorer"),
                safe(doc.getString(FirebaseConfig.FIELD_AVATAR_INITIAL), "?"),
                safe(doc.getString(FirebaseConfig.FIELD_VISIBILITY_MODE), "public"),
                number(doc.getLong(FirebaseConfig.FIELD_INTRAMUROS_MISSION_COUNT)),
                Boolean.TRUE.equals(doc.getBoolean(FirebaseConfig.FIELD_ALL_INTRAMUROS_COMPLETE)),
                Boolean.TRUE.equals(doc.getBoolean(FirebaseConfig.FIELD_SOUVENIR_MINTED)),
                number(doc.getLong(FirebaseConfig.FIELD_RANK_POSITION)),
                doc.getString(FirebaseConfig.FIELD_MISSION_ID),
                doc.getTimestamp("missionCompletedAt"),
                doc.getTimestamp(FirebaseConfig.FIELD_SORT_COMPLETED_AT));
    }

    private static int number(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value;
    }

    public String getUid() {
        return uid;
    }

    public String getDisplayNamePublic() {
        return displayNamePublic;
    }

    public String getAvatarInitial() {
        return avatarInitial;
    }

    public String getVisibilityMode() {
        return visibilityMode;
    }

    public int getIntramurosMissionCount() {
        return intramurosMissionCount;
    }

    public boolean isAllIntramurosComplete() {
        return allIntramurosComplete;
    }

    public boolean isSouvenirMinted() {
        return souvenirMinted;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public String getMissionId() {
        return missionId;
    }

    public Timestamp getMissionCompletedAt() {
        return missionCompletedAt;
    }

    public Timestamp getSortCompletedAt() {
        return sortCompletedAt;
    }
}
