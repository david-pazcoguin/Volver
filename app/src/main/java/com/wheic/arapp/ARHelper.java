package com.wheic.arapp;

public class ARHelper {
    String MissionName, Content;
    double Latitude, Longitude;

    // GPS positions where relics should appear (one per relic slot)
    double[] relicLatitudes;
    double[] relicLongitudes;

    // Optional: parallel array of collectible IDs (one per relic slot).
    // When non-null and same length as relicLatitudes, each relic spawns with
    // its own 3D model and credits that relic on tap. Used for the
    // Casa Manila staged "find the relics" mission. When null, every slot
    // uses the default coin model and credits the mission's `collectibleId`.
    String[] relicIds;

    // Collectible item ID awarded when a relic from this mission is tapped.
    // Must match a CollectibleItem id in HomeActivity.buildCollectiblesList().
    String collectibleId;

    // Unique ID used to track mission completion on the server
    String missionId;

    public String getMissionName()        { return MissionName; }
    public String getContent()            { return Content; }
    public double getLatitude()           { return Latitude; }
    public double getLongitude()          { return Longitude; }
    public double[] getRelicLatitudes()   { return relicLatitudes; }
    public double[] getRelicLongitudes()  { return relicLongitudes; }
    public String[] getRelicIds()         { return relicIds; }
    public String getCollectibleId()      { return collectibleId; }
    public String getMissionId()          { return missionId; }

    /** Full constructor with per-relic coordinate arrays and collectible item ID. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, double[] relicLatitudes, double[] relicLongitudes,
                    String collectibleId) {
        MissionName           = missionName;
        Content               = content;
        Latitude              = latitude;
        Longitude             = longitude;
        this.missionId        = missionId;
        this.relicLatitudes   = relicLatitudes;
        this.relicLongitudes  = relicLongitudes;
        this.collectibleId    = collectibleId;
    }

    /** Constructor with per-relic coordinate arrays — collectibleId defaults to missionId. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, double[] relicLatitudes, double[] relicLongitudes) {
        this(missionName, content, latitude, longitude,
             missionId, relicLatitudes, relicLongitudes, missionId);
    }

    /** Convenience constructor for a single relic at a specific GPS spot. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, double relicLatitude, double relicLongitude) {
        this(missionName, content, latitude, longitude,
             missionId, new double[]{relicLatitude}, new double[]{relicLongitude}, missionId);
    }

    /** Backward-compatible constructor — relic defaults to mission center. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId) {
        this(missionName, content, latitude, longitude,
             missionId, new double[]{latitude}, new double[]{longitude}, missionId);
    }

    /**
     * Staged-relic constructor (Casa Manila / LPU). The {@code relicIds} array
     * MUST be the same length as {@code relicLatitudes}. Each slot will spawn
     * with that relic's 3D model and credit that relic on tap.
     */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, double[] relicLatitudes, double[] relicLongitudes,
                    String[] relicIds) {
        this(missionName, content, latitude, longitude,
             missionId, relicLatitudes, relicLongitudes, missionId);
        this.relicIds = relicIds;
    }
}
