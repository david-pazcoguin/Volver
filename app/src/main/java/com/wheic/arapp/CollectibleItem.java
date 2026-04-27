package com.wheic.arapp;

public class CollectibleItem {

    private final String id;
    private final String title;
    private final String description;
    private final int thumbResId;
    private int count;
    private final int maxCount;

    public CollectibleItem(String id, String title, String description,
                           int thumbResId, int count, int maxCount) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.thumbResId  = thumbResId;
        this.count       = count;
        this.maxCount    = maxCount;
    }

    public String getId()          { return id; }
    public String getTitle()       { return title; }
    public String getDescription() { return description; }
    public int    getThumbResId()  { return thumbResId; }
    public int    getCount()       { return count; }
    public int    getMaxCount()    { return maxCount; }

    public void setCount(int count) { this.count = count; }

    public boolean isComplete() { return count >= maxCount; }
}
