package com.wheic.arapp;

public class ARHelper {
    String MissionName, Content;
    double Latitude, Longitude;

    // GPS positions where coins should appear (one per coin slot)
    double[] coinLatitudes;
    double[] coinLongitudes;

    // Optional: parallel array of collectible IDs (one per coin slot).
    // When non-null and same length as coinLatitudes, each coin spawns with
    // that relic's 3D model and credits that relic on tap. Used for the
    // Casa Manila staged "find the relics" mission. When null, every coin
    // uses the default coin model and credits the mission's `collectibleId`.
    String[] coinRelicIds;

    // Collectible item ID awarded when a coin from this mission is tapped.
    // Must match a CollectibleItem id in HomeActivity.buildCollectiblesList().
    String collectibleId;

    // Unique ID used to track mission completion on the server
    String missionId;

    // AR character name shown as an overlay when the user arrives at the location
    String characterName;

    // Dialogue the character speaks via TTS when the user enters the 10m radius
    String characterDialogue;

    // GLB filename (without extension) in res/raw/ — e.g. "rizal_character"
    // Falls back to san_bartolome_church if the file doesn't exist yet
    String modelFileName;

    public String getMissionName()        { return MissionName; }
    public String getContent()            { return Content; }
    public double getLatitude()           { return Latitude; }
    public double getLongitude()          { return Longitude; }
    public double[] getCoinLatitudes()    { return coinLatitudes; }
    public double[] getCoinLongitudes()   { return coinLongitudes; }
    public String[] getCoinRelicIds()     { return coinRelicIds; }
    public String getCollectibleId()      { return collectibleId; }
    public String getMissionId()          { return missionId; }
    public String getCharacterName()      { return characterName; }
    public String getCharacterDialogue()  { return characterDialogue; }
    public String getModelFileName()      { return modelFileName; }

    /** Full constructor with per-coin coordinate arrays and collectible item ID. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, String characterName, String characterDialogue,
                    String modelFileName, double[] coinLatitudes, double[] coinLongitudes,
                    String collectibleId) {
        MissionName      = missionName;
        Content          = content;
        Latitude         = latitude;
        Longitude        = longitude;
        this.missionId         = missionId;
        this.characterName     = characterName;
        this.characterDialogue = characterDialogue;
        this.modelFileName     = modelFileName;
        this.coinLatitudes     = coinLatitudes;
        this.coinLongitudes    = coinLongitudes;
        this.collectibleId     = collectibleId;
    }

    /** Constructor with per-coin coordinate arrays — collectibleId defaults to missionId. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, String characterName, String characterDialogue,
                    String modelFileName, double[] coinLatitudes, double[] coinLongitudes) {
        this(missionName, content, latitude, longitude,
             missionId, characterName, characterDialogue, modelFileName,
             coinLatitudes, coinLongitudes, missionId);
    }

    /** Convenience constructor for a single coin at a specific GPS spot. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, String characterName, String characterDialogue,
                    String modelFileName, double coinLatitude, double coinLongitude) {
        this(missionName, content, latitude, longitude,
             missionId, characterName, characterDialogue, modelFileName,
             new double[]{coinLatitude}, new double[]{coinLongitude}, missionId);
    }

    /** Backward-compatible constructor — coin defaults to mission center. */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, String characterName, String characterDialogue,
                    String modelFileName) {
        this(missionName, content, latitude, longitude,
             missionId, characterName, characterDialogue, modelFileName,
             new double[]{latitude}, new double[]{longitude}, missionId);
    }

    /**
     * Staged-relic constructor (Casa Manila). The {@code coinRelicIds} array
     * MUST be the same length as {@code coinLatitudes}. Each coin will spawn
     * with that relic's 3D model and credit that relic on tap.
     */
    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, String characterName, String characterDialogue,
                    String modelFileName, double[] coinLatitudes, double[] coinLongitudes,
                    String[] coinRelicIds) {
        this(missionName, content, latitude, longitude,
             missionId, characterName, characterDialogue, modelFileName,
             coinLatitudes, coinLongitudes, missionId);
        this.coinRelicIds = coinRelicIds;
    }
}
