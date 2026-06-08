package com.wheic.arapp;

import java.util.Collections;
import java.util.List;

/**
 * Bundles the leaderboard screen data for one board load.
 */
public final class LeaderboardLoadResult {

    private final List<LeaderboardEntry> topEntries;
    private final LeaderboardEntry currentUserEntry;
    private final String visibilityMode;
    private final boolean fromCache;

    public LeaderboardLoadResult(List<LeaderboardEntry> topEntries,
                                 LeaderboardEntry currentUserEntry,
                                 String visibilityMode,
                                 boolean fromCache) {
        this.topEntries = topEntries == null ? Collections.emptyList() : topEntries;
        this.currentUserEntry = currentUserEntry;
        this.visibilityMode = visibilityMode == null ? "public" : visibilityMode;
        this.fromCache = fromCache;
    }

    public List<LeaderboardEntry> getTopEntries() {
        return topEntries;
    }

    public LeaderboardEntry getCurrentUserEntry() {
        return currentUserEntry;
    }

    public String getVisibilityMode() {
        return visibilityMode;
    }

    public boolean isFromCache() {
        return fromCache;
    }
}
