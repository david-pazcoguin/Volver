package com.wheic.arapp;

public class ARHelper
{
    String MissionName, Content;

    double Latitude, Longitude;
    int Model;

    public String getMissionName() {
        return MissionName;
    }

    public String getContent() {
        return Content;
    }

    public double getLatitude() {
        return Latitude;
    }

    public double getLongitude() {
        return Longitude;
    }

    public int getModel() {
        return Model;
    }

    public ARHelper(String missionName, String content, double latitude, double longitude, int model) {
        MissionName = missionName;
        Content = content;
        Latitude = latitude;
        Longitude = longitude;
        Model = model;
    }
}
