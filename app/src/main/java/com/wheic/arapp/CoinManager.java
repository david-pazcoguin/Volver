package com.wheic.arapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CoinManager {

    private static final String PREFS_NAME       = "coin_prefs";
    private static final String KEY_TOTAL_COINS  = "coins_total";
    private static final String KEY_RESET_DATE   = "coins_date";
    private static final String KEY_TODAY_COINS  = "coins_collected_today";

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static class CoinSpawn {
        public final String id;
        public final float x, y, z;
        public CoinSpawn(String id, float x, float y, float z) {
            this.id = id; this.x = x; this.y = y; this.z = z;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String today() {
        return DATE_FMT.format(new Date());
    }

    /** Reset today's collected set if calendar date has changed (midnight rollover). */
    private static void maybeResetDaily(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        String storedDate = sp.getString(KEY_RESET_DATE, "");
        if (!storedDate.equals(today())) {
            sp.edit()
              .putString(KEY_RESET_DATE, today())
              .putString(KEY_TODAY_COINS, new JSONArray().toString())
              .apply();
        }
    }

    /** Returns the set of coin IDs already collected today. */
    private static Set<String> todayCollected(Context ctx) {
        maybeResetDaily(ctx);
        String raw = prefs(ctx).getString(KEY_TODAY_COINS, "[]");
        Set<String> set = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
        } catch (JSONException ignored) {}
        return set;
    }

    /** Attempt to collect a coin. Returns true if coin was not yet collected today. */
    public static boolean collectCoin(Context ctx, String coinId) {
        Set<String> collected = todayCollected(ctx);
        if (collected.contains(coinId)) return false;

        collected.add(coinId);
        JSONArray arr = new JSONArray(collected);

        SharedPreferences sp = prefs(ctx);
        int total = sp.getInt(KEY_TOTAL_COINS, 0) + 1;
        sp.edit()
          .putString(KEY_TODAY_COINS, arr.toString())
          .putInt(KEY_TOTAL_COINS, total)
          .apply();
        return true;
    }

    /** Returns the lifetime total coins collected by this user. */
    public static int getTotalCoins(Context ctx) {
        maybeResetDaily(ctx);
        return prefs(ctx).getInt(KEY_TOTAL_COINS, 0);
    }

    /** Returns which coin IDs from the given mission are still collectable today. */
    public static Set<String> getCollectableToday(Context ctx, String missionId,
                                                   List<CoinSpawn> spawns) {
        Set<String> collected = todayCollected(ctx);
        Set<String> collectable = new HashSet<>();
        for (CoinSpawn s : spawns) {
            if (!collected.contains(s.id)) collectable.add(s.id);
        }
        return collectable;
    }

    /** Reads coin_spawns.json from assets and returns spawns for the given mission. */
    public static List<CoinSpawn> loadSpawns(Context ctx, String missionId) {
        List<CoinSpawn> result = new ArrayList<>();
        try {
            InputStream is = ctx.getAssets().open("coin_spawns.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();

            JSONObject root     = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            JSONObject missions = root.getJSONObject("missions");
            if (!missions.has(missionId)) return result;

            JSONArray coins = missions.getJSONObject(missionId).getJSONArray("coins");
            for (int i = 0; i < coins.length(); i++) {
                JSONObject c = coins.getJSONObject(i);
                result.add(new CoinSpawn(
                        c.getString("id"),
                        (float) c.getDouble("x"),
                        (float) c.getDouble("y"),
                        (float) c.getDouble("z")
                ));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return result;
    }
}
