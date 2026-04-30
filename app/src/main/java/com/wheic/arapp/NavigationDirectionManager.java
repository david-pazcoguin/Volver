package com.wheic.arapp;

import android.app.Activity;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.bonuspack.routing.RoadNode;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages turn-by-turn walking directions using OSRM routing data.
 * Fetches routes with debouncing, tracks the current maneuver step,
 * and notifies a listener with display-ready direction data.
 */
public class NavigationDirectionManager {

    private static final String TAG = "NavDirectionMgr";
    private static final long ROUTE_FETCH_DEBOUNCE_MS = 15_000L;
    private static final float STEP_PASSED_RADIUS_M = 15f;

    // ── Callback ──────────────────────────────────────────────────────
    public interface DirectionListener {
        void onDirectionUpdate(DirectionStep step);
        void onRouteError();
    }

    // ── DirectionStep POJO ────────────────────────────────────────────
    public static class DirectionStep {
        public final String icon;
        public final int arrowRotation; // degrees to rotate the up-arrow; -1 = use icon text instead
        public final String label;
        public final String distanceText;
        public final int maneuverType;

        public DirectionStep(String icon, int arrowRotation, String label, String distanceText, int maneuverType) {
            this.icon = icon;
            this.arrowRotation = arrowRotation;
            this.label = label;
            this.distanceText = distanceText;
            this.maneuverType = maneuverType;
        }
    }

    // ── State ─────────────────────────────────────────────────────────
    private final Activity activity;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private DirectionListener listener;

    private double targetLat, targetLon;
    private String missionName = "";

    private volatile List<RoadNode> routeNodes;
    private long lastFetchTime;
    private volatile boolean destroyed;

    private DirectionStep lastStep;
    private final float[] distBuf = new float[2];

    // Minimum distance to a turn node before showing the actual turn instruction.
    // Beyond this, we show "Go straight" so the user isn't confused by far-off turns.
    private static final float TURN_ANNOUNCEMENT_RADIUS_M = 60f;

    // ── Public API ────────────────────────────────────────────────────

    public NavigationDirectionManager(Activity activity) {
        this.activity = activity;
    }

    public void setTarget(double lat, double lon, String name) {
        this.targetLat = lat;
        this.targetLon = lon;
        this.missionName = name != null ? name : "";
    }

    public void setListener(DirectionListener listener) {
        this.listener = listener;
    }

    /**
     * Called on every location update (~3 s). Triggers a debounced route
     * re-fetch and emits the current direction step.
     */
    public void onLocationUpdate(double lat, double lon) {
        if (destroyed) return;

        // Debounced route fetch
        long now = System.currentTimeMillis();
        if (now - lastFetchTime >= ROUTE_FETCH_DEBOUNCE_MS) {
            lastFetchTime = now;
            GeoPoint userPoint = new GeoPoint(lat, lon);
            fetchRoute(userPoint);
        }

        // Emit direction from cached route (or fallback)
        DirectionStep step = findCurrentStep(lat, lon);
        if (step != null) {
            lastStep = step;
            if (listener != null) {
                uiHandler.post(() -> {
                    if (!destroyed && listener != null) listener.onDirectionUpdate(step);
                });
            }
        }
    }

    /** Returns the most recently computed step (for toggle refresh). */
    public DirectionStep getLastStep() {
        return lastStep;
    }

    public void destroy() {
        destroyed = true;
        listener = null;
    }

    // ── Route fetching ────────────────────────────────────────────────

    private void fetchRoute(GeoPoint userPoint) {
        GeoPoint target = new GeoPoint(targetLat, targetLon);
        new Thread(() -> {
            if (destroyed) return;
            try {
                RoadManager roadManager = new OSRMRoadManager(activity, activity.getPackageName());
                ((OSRMRoadManager) roadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT);

                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(userPoint);
                waypoints.add(target);

                Road road = roadManager.getRoad(waypoints);
                if (destroyed) return;

                if (road != null && road.mStatus == Road.STATUS_OK && road.mNodes != null) {
                    // Thread-safe handoff: copy to UI thread
                    List<RoadNode> nodes = new ArrayList<>(road.mNodes);
                    uiHandler.post(() -> {
                        if (destroyed) return;
                        routeNodes = nodes;
                        Log.e(TAG, "Route loaded: " + nodes.size() + " nodes");
                    });
                } else {
                    Log.w(TAG, "Route fetch returned no valid data");
                    uiHandler.post(() -> {
                        if (!destroyed && listener != null) listener.onRouteError();
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Route fetch failed: " + e.getMessage());
                uiHandler.post(() -> {
                    if (!destroyed && listener != null) listener.onRouteError();
                });
            }
        }).start();
    }

    // ── Step finding ──────────────────────────────────────────────────

    private DirectionStep findCurrentStep(double lat, double lon) {
        List<RoadNode> nodes = routeNodes; // snapshot for thread safety

        // Compute total distance to the mission target.
        float distToTarget = distanceBetween(lat, lon, targetLat, targetLon);

        // If the user is still far from the destination, always show "Go straight".
        // This prevents OSRM from announcing a turn at a nearby street corner when
        // the destination itself is hundreds of metres away.
        if (distToTarget > TURN_ANNOUNCEMENT_RADIUS_M) {
            // Only switch to OSRM maneuvers when close to the destination.
            if (nodes == null || nodes.size() <= 2) {
                return buildFallbackStep(lat, lon);
            }
            // Walk route nodes looking for a turn that is ALSO close to the user.
            for (int i = 1; i < nodes.size(); i++) {
                RoadNode node = nodes.get(i);
                if (node.mLocation == null) continue;
                float distToNode = distanceBetween(lat, lon,
                        node.mLocation.getLatitude(), node.mLocation.getLongitude());
                if (distToNode > STEP_PASSED_RADIUS_M) {
                    boolean isTurn = node.mManeuverType != 0 && node.mManeuverType != 1
                            && node.mManeuverType != 2 && node.mManeuverType != 24;
                    // Only announce a turn if the turn node itself is within 60 m.
                    if (isTurn && distToNode <= TURN_ANNOUNCEMENT_RADIUS_M) {
                        String distText = formatDistance(distToNode);
                        return mapManeuver(node.mManeuverType, distText, node.mInstructions);
                    }
                    // Turn is far away (or it's a straight step) — keep showing go straight.
                    return new DirectionStep(null, 0, "Go straight", formatDistance(distToTarget), 1);
                }
            }
            return new DirectionStep(null, 0, "Go straight", formatDistance(distToTarget), 1);
        }

        // Close to destination — use full OSRM step logic.
        if (nodes == null || nodes.size() <= 1) {
            return buildFallbackStep(lat, lon);
        }
        if (nodes.size() == 2) {
            return buildFallbackStep(lat, lon);
        }

        for (int i = 1; i < nodes.size(); i++) {
            RoadNode node = nodes.get(i);
            if (node.mLocation == null) continue;
            float distToNode = distanceBetween(lat, lon,
                    node.mLocation.getLatitude(), node.mLocation.getLongitude());
            if (distToNode > STEP_PASSED_RADIUS_M) {
                String distText = formatDistance(distToNode);
                return mapManeuver(node.mManeuverType, distText, node.mInstructions);
            }
        }

        return new DirectionStep("📍", -1, "Arrive at " + missionName,
                formatDistance(distToTarget), 24);
    }

    private DirectionStep buildFallbackStep(double lat, double lon) {
        float distToTarget = distanceBetween(lat, lon, targetLat, targetLon);
        return new DirectionStep(null, 0, "Head toward " + missionName,
                formatDistance(distToTarget), 0);
    }

    // ── Maneuver mapping ──────────────────────────────────────────────

    private DirectionStep mapManeuver(int type, String distText, String rawInstruction) {
        String icon;
        int arrowRotation;
        String label;

        switch (type) {
            case 1:  icon = null; arrowRotation =    0; label = "Go straight";      break;
            case 2:  icon = null; arrowRotation =    0; label = "Continue";          break;
            case 3:  icon = null; arrowRotation =  -45; label = "Bear left";         break;
            case 4:  icon = null; arrowRotation =  -90; label = "Turn left";         break;
            case 5:  icon = null; arrowRotation = -135; label = "Sharp left";        break;
            case 6:  icon = null; arrowRotation =   45; label = "Bear right";        break;
            case 7:  icon = null; arrowRotation =   90; label = "Turn right";        break;
            case 8:  icon = null; arrowRotation =  135; label = "Sharp right";       break;
            case 12: icon = null; arrowRotation =  180; label = "Make a U-turn";     break;
            case 24: icon = "📍";  arrowRotation =   -1; label = "Arrive at " + missionName; break;
            case 27: case 28: case 29: case 30:
            case 31: case 32: case 33: case 34:
                     icon = "↻";  arrowRotation =   -1; label = "Take the roundabout"; break;
            default: icon = null; arrowRotation =    0; label = "Continue";          break;
        }

        // Append street name from raw instruction if available
        if (rawInstruction != null && !rawInstruction.isEmpty()) {
            // OSRM instructions look like "Turn left on General Luna Street"
            int onIdx = rawInstruction.indexOf(" on ");
            if (onIdx > 0 && onIdx + 4 < rawInstruction.length()) {
                label += " on " + rawInstruction.substring(onIdx + 4);
            }
        }

        return new DirectionStep(icon, arrowRotation, label, distText, type);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        Location.distanceBetween(lat1, lon1, lat2, lon2, distBuf);
        return distBuf[0];
    }

    private static String formatDistance(float meters) {
        if (meters >= 1000) {
            return String.format(Locale.US, "in %.1f km", meters / 1000f);
        }
        return String.format(Locale.US, "in %d m", (int) meters);
    }
}
