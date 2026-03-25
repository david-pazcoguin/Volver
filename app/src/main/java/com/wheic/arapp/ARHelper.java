package com.wheic.arapp;

public class ARHelper {
    String MissionName, Content;
    double Latitude, Longitude;

    // Unique ID used to track mission completion on the server
    String missionId;

    // AR character name shown as an overlay when the user arrives at the location
    String characterName;

    // Dialogue the character speaks via TTS when the user enters the 50m radius
    String characterDialogue;

    // GLB filename (without extension) in res/raw/ — e.g. "rizal_character"
    // Falls back to san_bartolome_church if the file doesn't exist yet
    String modelFileName;

    public String getMissionName()       { return MissionName; }
    public String getContent()           { return Content; }
    public double getLatitude()          { return Latitude; }
    public double getLongitude()         { return Longitude; }
    public String getMissionId()         { return missionId; }
    public String getCharacterName()     { return characterName; }
    public String getCharacterDialogue() { return characterDialogue; }
    public String getModelFileName()     { return modelFileName; }

    public ARHelper(String missionName, String content, double latitude, double longitude,
                    String missionId, String characterName, String characterDialogue,
                    String modelFileName) {
        MissionName      = missionName;
        Content          = content;
        Latitude         = latitude;
        Longitude        = longitude;
        this.missionId         = missionId;
        this.characterName     = characterName;
        this.characterDialogue = characterDialogue;
        this.modelFileName     = modelFileName;
    }
}
