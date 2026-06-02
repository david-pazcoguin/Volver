package com.wheic.arapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Earth;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.views.overlay.Polyline;

import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.math.Quaternion;

public class ARActivity extends AppCompatActivity {

    private static final String TAG = "ARActivityTag";

    // ── AR ──────────────────────────────────────────────────────────
    private ArFragment arCam;
    private final Scene.OnUpdateListener sceneUpdateListener = this::onSceneUpdate;
    private int diagnosticFrameCount = 0;
    private boolean cameraStreamDiagLogged = false;
    private Dialog exitGameDialog;

    // ── Location ────────────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final int LOCATION_PERM_CODE = 1001;
    private static final float ACTIVATION_RADIUS_METERS = 30.0f;
    private static final float MAX_ACTIVATION_ACCURACY_METERS = 25.0f;
    private static final int REQUIRED_CONSECUTIVE_ACTIVATION_FIXES = 2;
    private static final long ACTIVATION_CONFIRMATION_WINDOW_MS = 12_000L;
    private static final long LOCATION_CHECK_INTERVAL = 10_000L; // when searching for target
    private static final long LOCATION_CHECK_INTERVAL_IDLE = 30_000L; // after target reached

    private double targetLatitude;
    private double targetLongitude;
    private double targetAltitude;
    private boolean isTargetReached = false;
    private int activationConfirmationCount = 0;
    private long lastActivationConfirmationElapsedMs = 0L;

    // GPS positions for each relic (one entry per relic slot)
    private double[] relicLatitudes;
    private double[] relicLongitudes;
    // Optional: parallel array of relic IDs (one per relic slot). When non-null,
    // each slot spawns with its own GLB model and credits that relic on tap.
    // When null, every slot uses the default coin model and the mission's
    // single `collectibleId`. Used for the Casa Manila staged mission.
    private String[] relicIds;

    // Last GPS fix — used to compute bearing toward the coin spot
    private double lastUserLat = Double.NaN;
    private double lastUserLng = Double.NaN;
    private float lastUserAccuracyMeters = Float.NaN;
    private long lastUserFixElapsedRealtimeMs = 0L;

    // Compass heading (degrees, 0 = north, clockwise) from SensorManager.
    // Smoothed via a low-pass filter to prevent jitter.
    private float deviceAzimuthDeg = 0f;
    private float smoothedAzimuthDeg = 0f;
    private static final float COMPASS_ALPHA = 0.15f; // lower = smoother, higher = faster
    // ARCore geospatial heading (degrees CW from true north). Preferred over
    // the magnetometer when available because it shares its reference frame
    // with the AR relic anchors, so the compass always agrees with the scene.
    private float geospatialHeadingDeg = Float.NaN;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private final SensorEventListener compassListener = new SensorEventListener() {
        private final float[] rotMatrix = new float[9];
        private final float[] remappedMatrix = new float[9];
        private final float[] orientation = new float[3];

        @Override
        public void onSensorChanged(SensorEvent event) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);
            // Remap axes for portrait (phone held upright): screen-Z becomes the
            // "up" reference so azimuth is correct when the phone is vertical.
            SensorManager.remapCoordinateSystem(
                    rotMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedMatrix);
            SensorManager.getOrientation(remappedMatrix, orientation);
            float rawAzimuth = (float) Math.toDegrees(orientation[0]);
            if (rawAzimuth < 0) rawAzimuth += 360f;
            deviceAzimuthDeg = rawAzimuth;
            // Low-pass filter — interpolate across the shortest circular arc
            float delta = rawAzimuth - smoothedAzimuthDeg;
            if (delta > 180f) delta -= 360f;
            if (delta < -180f) delta += 360f;
            smoothedAzimuthDeg = (smoothedAzimuthDeg + COMPASS_ALPHA * delta + 360f) % 360f;
            updateCompassArrow();
            // Keep the on-screen distance hint in sync with the compass —
            // otherwise the HUD only refreshes on the (slow) GPS callback
            // and disagrees with the compass for several seconds at a time.
            updateRelicHud();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private boolean geospatialConfigured = false;

    // ── Mission data ────────────────────────────────────────────────
    private String missionId;
    private String missionName;
    private String collectibleId;
    private String username;

    // ── UI ──────────────────────────────────────────────────────────
    private View locateLabelContainer;
    private TextView tvLocateLabel; // mission name inside the pill
    private TextView tvLocateDistance; // distance line inside the pill
    private TextView tvCharacterHint;

    // ── Turn-by-turn directions ──────────────────────────────────────
    private NavigationDirectionManager navManager;
    private LinearLayout directionBanner;
    private android.widget.ImageView ivDirectionArrow;
    private TextView tvDirectionIcon;
    private TextView tvDirectionText;
    private TextView tvDirectionDistance;
    private View btnCompass;
    private View compassOverlay;
    private android.widget.ImageView compassArrow;
    private TextView compassDistance;
    private TextView tvCompassIcon;

    // ── Collect button ───────────────────────────────────────────────
    private View collectButtonContainer;
    private TextView tvCollectLabel;
    private TextView tvCollectedFeedback;
    private long lastCollectedTimeMs = 0;
    // True while a collect animation is in progress — prevents double-tap on button
    private boolean isCollecting = false;
    // True when the user has toggled the bottom-right slot to the compass view
    private boolean isCompassToggled = false;

    // ── Minimap ─────────────────────────────────────────────────────
    private MapView minimap;
    private View minimapContainer;
    private View btnMapCompassToggle;
    private TextView tvMapCompassIcon;
    private Marker userMarker;
    private Marker targetMarker;
    private Polyline routeOverlay;

    // ── Scattered coins ─────────────────────────────────────────────
    private CompletableFuture<ModelRenderable> coinModelFuture;
    // Resolved renderable cached once the future completes — read from render thread without blocking.
    private ModelRenderable resolvedCoinRenderable;
    // Per-relic GLB renderables for staged missions (e.g. Casa Manila).
    private final Map<String, CompletableFuture<ModelRenderable>> relicModelFutures = new HashMap<>();
    private final Map<String, ModelRenderable> resolvedRelicRenderables = new HashMap<>();
    private final List<Marker> coinMapMarkers = new ArrayList<>();
    private final List<AnchorNode> coinAnchorNodes = new ArrayList<>();
    private final List<Node> coinNodes = new ArrayList<>();
    /**
     * Authoritative collection state per slot. Independent of
     * {@link #coinAnchorNodes} so we can reason about progress even if a
     * spawn/detach race ever mutates the anchor list unexpectedly.
     */
    private boolean[] coinSlotCollected;
    private boolean coinsSpawned = false;
    private int coinsCollected = 0;
    private float coinCollectedValue = 0f;
    private float coinRotationAngle = 0f;
    // Tracks which relic slots have an in-flight terrain-anchor request so we
    // don't fire duplicates while the async response is pending (or retry after
    // the slot is cleared by collection / anchor loss).
    private final java.util.Map<Integer, Boolean> terrainAnchorPending = new java.util.HashMap<>();
    // Pre-resolved terrain anchors for UPCOMING slots (slot N+1 resolved while
    // the user is still hunting slot N). Keyed by slot index. Consumed by
    // tryAutoSpawnCoins() the instant the slot becomes active — zero latency.
    private final java.util.Map<Integer, Anchor> preResolvedAnchors = new java.util.HashMap<>();
    private final java.util.Map<Integer, ModelRenderable> preResolvedRenderables = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> preResolvedRelicIds = new java.util.HashMap<>();
    // Tracks in-flight next-slot pre-resolve requests (distinct from terrainAnchorPending
    // which is for spawn-targeted requests — keeping them separate prevents the spawn
    // loop from blocking on a pre-warm it isn't expecting to consume immediately).
    private final java.util.Map<Integer, Boolean> nextSlotPreWarmPending = new java.util.HashMap<>();
    // Timestamp (ms) when we first saw geospatial not-yet-tracking for the
    // current slot. After 15 s we show a "point camera toward buildings" hint.
    // Bearing fallback has been removed — items always wait for geospatial.
    private long geospatialWaitStartMs = 0;
    private static final float COIN_SCALE = 0.25f;
    private static final float COIN_VALUE = 0.10f;
    // Max distance (metres) from a relic at which the user can tap to collect it.
    // Kept large because urban-canyon GPS drift in Intramuros can be 5-10 m even
    // with a clean fix. The real proximity check is the spawn radius below — if
    // the relic is visible in AR, the user is close enough to collect it.
    private static final float RELIC_COLLECT_RADIUS_M = 25.0f;
    // Distance at which a staged relic spawns into the scene. 12 m matches the
    // user-visible "walk closer" threshold and is large enough that urban-canyon
    // GPS drift (±5-10 m) never prevents a relic from appearing in range.
    private static final float RELIC_SPAWN_RADIUS_M = 12.0f;
    // Distance (metres) within which the COLLECT button is shown.
    // Keeps the button hidden until the user is genuinely close.
    private static final float RELIC_COLLECT_BUTTON_M = 5.0f;
    private static final float MAX_RELIC_GATING_ACCURACY_METERS = 25.0f;
    // Distance at which a staged relic is despawned again (hysteresis to avoid
    // flicker).
    private static final float RELIC_DESPAWN_RADIUS_M = 20.0f;
    private static final long MAX_LOCATION_FIX_AGE_MS = 8_000L;
    // ── Periodic location check ──────────────────────────────────────
    private final float[] distanceResults = new float[1];
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private final Runnable locationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTargetReached) {
                getLocationAndCheckTarget();
                locationHandler.postDelayed(this, LOCATION_CHECK_INTERVAL);
            }
        }
    };

    // ──────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!checkSystemSupport(this))
            return;

        // Auth guard — every activity that writes to Firestore must ensure
        // the user is signed in. Without this, a coin tap would silently fail.
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to play missions.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }

        // osmdroid needs a user-agent before any MapView is inflated
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.ar_activity);

        // Unpack intent extras — fail closed if no mission was supplied
        Bundle extras = getIntent().getExtras();
        if (extras == null
                || !extras.containsKey("Latitude")
                || !extras.containsKey("Longitude")
                || !extras.containsKey("MissionId")) {
            Toast.makeText(this, "Mission data missing. Please open a mission from the home screen.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        targetLatitude = extras.getDouble("Latitude");
        targetLongitude = extras.getDouble("Longitude");
        targetAltitude = extras.getDouble("Altitude", Double.NaN);
        double[] cl = extras.getDoubleArray("RelicLatitudes");
        double[] cn = extras.getDoubleArray("RelicLongitudes");
        relicLatitudes = (cl != null && cl.length > 0) ? cl : new double[] { targetLatitude };
        relicLongitudes = (cn != null && cn.length > 0) ? cn : new double[] { targetLongitude };
        coinSlotCollected = new boolean[relicLatitudes.length];
        String[] cri = extras.getStringArray("RelicIds");
        if (cri != null && cri.length == relicLatitudes.length) {
            relicIds = cri;
        } else {
            relicIds = null;
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        missionId = extras.getString("MissionId", "unknown");
        missionName = extras.getString("MissionName", "Mission");
        collectibleId = extras.getString("CollectibleId", missionId);
        restoreSavedRelicProgress();

        // Validate coordinates
        if (targetLatitude < -90 || targetLatitude > 90
                || targetLongitude < -180 || targetLongitude > 180) {
            Toast.makeText(this, "Invalid mission coordinates.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        username = SecurePrefs.get(this).getString("username", "");

        locateLabelContainer = findViewById(R.id.locateLabelContainer);
        tvLocateLabel = findViewById(R.id.tvLocateLabel);
        tvLocateDistance = findViewById(R.id.tvLocateDistance);
        tvCharacterHint = findViewById(R.id.tvCharacterHint);

        // Show locate pill during navigation phase
        if (tvLocateLabel != null)
            tvLocateLabel.setText(missionName);
        if (tvLocateDistance != null)
            tvLocateDistance.setText("Locating\u2026");
        if (locateLabelContainer != null)
            locateLabelContainer.setVisibility(View.VISIBLE);

        // Turn-by-turn direction views
        directionBanner = findViewById(R.id.directionBanner);
        ivDirectionArrow = findViewById(R.id.ivDirectionArrow);
        tvDirectionIcon = findViewById(R.id.tvDirectionIcon);
        tvDirectionText = findViewById(R.id.tvDirectionText);
        tvDirectionDistance = findViewById(R.id.tvDirectionDistance);
        btnCompass = findViewById(R.id.btnCompass);
        compassOverlay = findViewById(R.id.compassOverlay);
        compassArrow = findViewById(R.id.compassArrow);
        compassDistance = findViewById(R.id.compassDistance);
        tvCompassIcon = findViewById(R.id.tvCompassIcon);

        collectButtonContainer = findViewById(R.id.collectButtonContainer);
        tvCollectLabel = findViewById(R.id.tvCollectLabel);
        tvCollectedFeedback = findViewById(R.id.tvCollectedFeedback);
        if (collectButtonContainer != null) {
            collectButtonContainer.setOnClickListener(v -> collectCurrentRelic());
        }

        if (btnCompass != null) {
            btnCompass.setOnClickListener(v -> toggleCompassOverlay());
        }
        if (compassOverlay != null) {
            // Tapping the overlay itself also closes it
            compassOverlay.setOnClickListener(v -> {
                compassOverlay.setVisibility(View.GONE);
                if (btnCompass != null)
                    btnCompass.setSelected(false);
            });
        }

        // Show the direction banner immediately with a placeholder so the user
        // always sees something while waiting for the first GPS fix.
        if (directionBanner != null)
            directionBanner.setVisibility(View.VISIBLE);
        if (btnCompass != null)
            btnCompass.setVisibility(View.VISIBLE);
        if (ivDirectionArrow != null) {
            ivDirectionArrow.setRotation(0f);
            ivDirectionArrow.setVisibility(View.VISIBLE);
        }
        if (tvDirectionIcon != null)
            tvDirectionIcon.setVisibility(View.GONE);
        if (tvDirectionText != null)
            tvDirectionText.setText("Head toward " + missionName);
        if (tvDirectionDistance != null)
            tvDirectionDistance.setText("Locating…");

        // Set up NavigationDirectionManager
        navManager = new NavigationDirectionManager(this);
        navManager.setTarget(targetLatitude, targetLongitude, missionName);
        navManager.setListener(new NavigationDirectionManager.DirectionListener() {
            @Override
            public void onDirectionUpdate(NavigationDirectionManager.DirectionStep step) {
                handleDirectionUpdate(step);
            }

            @Override
            public void onRouteError() {
                // Keep showing last known direction or fallback
                Log.w(TAG, "Route error — keeping current direction display");
            }
        });

        // Minimap kept in layout but hidden (minimapContainer visibility=gone in XML)
        minimapContainer = findViewById(R.id.minimapContainer);
        minimap = findViewById(R.id.minimap);
        btnMapCompassToggle = findViewById(R.id.btnMapCompassToggle);
        tvMapCompassIcon = findViewById(R.id.tvMapCompassIcon);
        if (btnMapCompassToggle != null) {
            btnMapCompassToggle.setOnClickListener(v -> toggleMapCompass());
        }

        arCam = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arCameraArea);

        // Show the default "scanning hand" for 5 seconds, then hide it permanently
        if (arCam != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (arCam != null) {
                    arCam.getPlaneDiscoveryController().hide();
                    arCam.getPlaneDiscoveryController().setInstructionView(null);
                }
            }, 5000);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null) {
                    Log.e(TAG, "onLocationResult: null location");
                    return;
                }
                Log.e(TAG, "onLocationResult: " + location.getLatitude() + "," + location.getLongitude());

                updateUserFixFromLocation(location);

                Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        targetLatitude, targetLongitude, distanceResults);
                float distance = distanceResults[0];

                if (!isTargetReached) {
                    // Directly update distance UI — never depends on NavManager or network
                    updateDistanceUI(distance);

                    if (navManager != null) {
                        navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
                    }
                    maybeActivateTarget(distance, lastUserAccuracyMeters, "locationCallback");
                }
                updateMinimapUser();
                updateRelicHud();
            }
        };

        // Configure geospatial mode as soon as the session is created
        if (arCam != null) {
            arCam.setOnSessionInitializationListener(this::onSessionCreated);
            // Subscribe to per-frame updates to retry geospatial config if needed
        }

        requestLocationPermission();
        preloadCoinModel();
        setupMinimap();
        setupTapListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-attach scene listener (removed in onPause to prevent leak)
        if (arCam != null && arCam.getArSceneView() != null) {
            arCam.getArSceneView().getScene().addOnUpdateListener(sceneUpdateListener);
            // ENVIRONMENTAL_HDR handles lighting automatically — do not override
            // the intensity here. Forcing 100,000 f every resume overwrites ARCore's
            // estimated lighting and washes out metallic/textured material colors.
        }
        // Start periodic location check when screen is visible
        locationHandler.post(locationRunnable);
        startLocationUpdates();
        if (sensorManager != null && rotationVectorSensor != null) {
            sensorManager.registerListener(compassListener, rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_UI);
        }
        if (minimap != null)
            minimap.onResume();
        // Keep direction UI above the AR SurfaceView
        if (locateLabelContainer != null)
            locateLabelContainer.bringToFront();
        if (directionBanner != null)
            directionBanner.bringToFront();
        if (tvCharacterHint != null)
            tvCharacterHint.bringToFront();
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationHandler.removeCallbacks(locationRunnable);
        stopLocationUpdates();
        if (sensorManager != null)
            sensorManager.unregisterListener(compassListener);
        if (minimap != null)
            minimap.onPause();
        if (arCam != null && arCam.getArSceneView() != null) {
            arCam.getArSceneView().getScene().removeOnUpdateListener(sceneUpdateListener);
        }
    }

    @Override
    protected void onDestroy() {
        locationHandler.removeCallbacks(locationRunnable);
        if (navManager != null)
            navManager.destroy();
        if (exitGameDialog != null && exitGameDialog.isShowing()) {
            exitGameDialog.dismiss();
        }
        super.onDestroy();
    }

    // ──────────────────────────────────────────────────────────────────
    // Session & scene callbacks
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        showExitGameDialog();
    }

    private void showExitGameDialog() {
        if (exitGameDialog != null && exitGameDialog.isShowing()) {
            return;
        }

        exitGameDialog = new Dialog(this);
        exitGameDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        exitGameDialog.setCancelable(false);
        exitGameDialog.setCanceledOnTouchOutside(false);
        exitGameDialog.setContentView(R.layout.dialog_exit_game);

        Window window = exitGameDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            int dialogWidth = (int) Math.min(
                    getResources().getDisplayMetrics().widthPixels * 0.9f,
                    getResources().getDisplayMetrics().density * 420f);
            window.setLayout(dialogWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.CENTER;
            window.setAttributes(wlp);
        }

        exitGameDialog.findViewById(R.id.cardViewCancelExit).setOnClickListener(v -> exitGameDialog.dismiss());
        exitGameDialog.findViewById(R.id.cardViewConfirmExit).setOnClickListener(v -> {
            exitGameDialog.dismiss();
            finish();
        });
        exitGameDialog.setOnDismissListener(d -> exitGameDialog = null);
        exitGameDialog.show();
    }

    /** Called once when the ARCore session is first created. */
    private void onSessionCreated(Session session) {
        Log.d(TAG, "onSessionCreated: session=" + (session != null));
        // Geospatial configuration is deferred to onSceneUpdate because
        // setupSession() has not been called yet at this point.
    }

    /** Called every frame — used to retry geospatial config and update status. */
    private void onSceneUpdate(FrameTime frameTime) {
        if (!geospatialConfigured) {
            configureGeospatialMode();
        }
        // Lighting is managed by ENVIRONMENTAL_HDR — no manual override needed.

        if (isTargetReached) {
            // Upcoming-slot anchors are safe to pre-resolve now because they
            // remain detached until their slot becomes active.
            preResolveUpcomingSlotAnchors();
            // Recover any permanently-lost anchors BEFORE trying to spawn so the
            // cleared slot is immediately eligible for re-spawn in the same frame.
            checkAndRespawnLostAnchor();
            if (!coinsSpawned) {
                tryAutoSpawnCoins();
            }
            enforceRelicSpawnRadius();
        }
        if (!coinNodes.isEmpty()) {
            animateCoins();
        }

        // One-time diagnostic: after a few frames, check if camera stream is healthy
        diagnosticFrameCount++;
        if (!cameraStreamDiagLogged && diagnosticFrameCount == 30) {
            cameraStreamDiagLogged = true;
            logCameraStreamDiagnostics();
        }
    }

    private void logCameraStreamDiagnostics() {
        if (arCam == null || arCam.getArSceneView() == null) {
            Log.e(TAG, "DIAG: ArFragment or ArSceneView is null!");
            return;
        }
        com.google.ar.sceneform.ArSceneView arView = arCam.getArSceneView();
        Session session = arView.getSession();
        Log.e(TAG, "DIAG: session=" + (session != null)
                + ", frame=" + (arView.getArFrame() != null)
                + ", cameraTextureId=" + arView.getCameraTextureId()
                + ", cameraStreamHealthy=" + arView.isCameraStreamHealthy());
        if (session != null) {
            com.google.ar.core.Camera cam = null;
            try {
                com.google.ar.core.Frame f = arView.getArFrame();
                if (f != null)
                    cam = f.getCamera();
            } catch (Exception e) {
                /* ignore */ }
            Log.e(TAG, "DIAG: ARCore camera tracking=" + (cam != null ? cam.getTrackingState() : "null"));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Model loading
    // ──────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────
    // Location
    // ──────────────────────────────────────────────────────────────────

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERM_CODE);
        } else {
            getLocationAndCheckTarget();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERM_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
            getLocationAndCheckTarget();
        } else {
            Toast.makeText(this, "Location permission required to activate AR models.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        // Force an immediate fresh fix so the compass is correct the moment the mission
        // opens
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    Log.e(TAG, "getCurrentLocation result: " + (location != null
                            ? location.getLatitude() + "," + location.getLongitude()
                            : "null"));
                    if (location != null) {
                        updateUserFixFromLocation(location);
                        if (!isTargetReached) {
                            Location.distanceBetween(
                                    location.getLatitude(), location.getLongitude(),
                                    targetLatitude, targetLongitude, distanceResults);
                            float distance = distanceResults[0];
                            updateDistanceUI(distance);
                            if (navManager != null) {
                                navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
                            }
                            maybeActivateTarget(distance, lastUserAccuracyMeters, "getCurrentLocation");
                        }
                        updateMinimapUser();
                        updateRelicHud();
                    }
                })
                .addOnFailureListener(this, e -> Log.e(TAG, "getCurrentLocation failed: " + e.getMessage()));

        // Keep updating every 2 seconds; reject cached fixes older than 5 s so
        // that re-entering the activity after moving shows the new position
        // immediately rather than a stale "20 m away" reading.
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateAgeMillis(5_000)
                .build();
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // Route fetching is now handled by NavigationDirectionManager

    private void getLocationAndCheckTarget() {
        if (isGeospatialAvailable()) {
            Session session = (arCam != null && arCam.getArSceneView() != null)
                    ? arCam.getArSceneView().getSession()
                    : null;
            Earth earth = session != null ? session.getEarth() : null;

            if (earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
                GeospatialPose cameraPose = earth.getCameraGeospatialPose();
                if (cameraPose.getHorizontalAccuracy() < 10f) {
                    // Use the precise geospatial fix as the authoritative user
                    // position so the compass + minimap + HUD agree with where
                    // the AR relic is actually anchored. Falling back to fused
                    // GPS while the AR uses geospatial caused the compass to
                    // point the wrong direction.
                    updateUserFixFromGeospatial(cameraPose);
                    // Capture the precise AR-aligned heading too.
                    try {
                        geospatialHeadingDeg = (float) cameraPose.getHeading();
                    } catch (Throwable t) {
                        geospatialHeadingDeg = Float.NaN;
                    }

                    Location.distanceBetween(
                            cameraPose.getLatitude(), cameraPose.getLongitude(),
                            targetLatitude, targetLongitude, distanceResults);
                    float distance = distanceResults[0];

                    if (navManager != null) {
                        navManager.onLocationUpdate(cameraPose.getLatitude(), cameraPose.getLongitude());
                    }

                    if (!isTargetReached)
                        updateDistanceUI(distance);
                    updateMinimapUser();
                    updateRelicHud();
                    updateCompassArrow();

                    maybeActivateTarget(distance, (float) cameraPose.getHorizontalAccuracy(), "geospatial");
                    return;
                }
                updateHint("Improving GPS accuracy...");
                return;
            }
            // Geospatial is available but not yet tracking — fall through to fused location
        }

        // Fallback: fused location when geospatial is unavailable or not yet tracking.
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) {
                Log.e(TAG, "getLastLocation returned null \u2014 waiting for fresh fix");
                if (tvDirectionDistance != null && !isTargetReached) {
                    tvDirectionDistance.setText("Searching for GPS\u2026");
                }
                return;
            }

            if (!isLocationFresh(location)) {
                Log.d(TAG, "getLastLocation: ignoring stale fix ageMs=" + getLocationAgeMs(location));
                if (tvDirectionDistance != null && !isTargetReached) {
                    tvDirectionDistance.setText("Searching for GPS\u2026");
                }
                return;
            }

            updateUserFixFromLocation(location);

            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    targetLatitude, targetLongitude, distanceResults);
            float distance = distanceResults[0];

            if (!isTargetReached)
                updateDistanceUI(distance);

            if (navManager != null) {
                navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
            }

            updateMinimapUser();
            updateRelicHud();

            maybeActivateTarget(distance, lastUserAccuracyMeters, "getLastLocation");
        });
    }

    /**
     * Called the first time the user enters the 50 m activation radius.
     * Automatically greets them with the character dialogue via TTS.
     */
    private void onTargetReached() {
        if (isTargetReached)
            return; // already triggered
        isTargetReached = true;
        resetActivationConfirmation();

        // Hide direction UI
        if (locateLabelContainer != null)
            locateLabelContainer.setVisibility(View.GONE);
        if (directionBanner != null)
            directionBanner.setVisibility(View.GONE);
        if (btnCompass != null)
            btnCompass.setVisibility(View.GONE);
        if (compassOverlay != null)
            compassOverlay.setVisibility(View.GONE);

        // Reset geospatial wait state so each mission entry starts fresh.
        geospatialWaitStartMs = 0;

        // Show minimap so player can see coin location (reset any previous compass toggle)
        isCompassToggled = false;
        if (minimapContainer != null)
            minimapContainer.setVisibility(View.VISIBLE);
        if (compassOverlay != null)
            compassOverlay.setVisibility(View.GONE);
        if (btnMapCompassToggle != null) {
            btnMapCompassToggle.setVisibility(View.VISIBLE);
            if (tvMapCompassIcon != null)
                tvMapCompassIcon.setText("🧭");
        }

        updateHint("Find and tap the floating coin to complete the mission!");

        Toast.makeText(this, "You reached the mission! Find the floating coin!",
                Toast.LENGTH_LONG).show();
    }

    private void updateHint(String text) {
        if (tvCharacterHint != null) {
            tvCharacterHint.setText(text);
            tvCharacterHint.setVisibility(View.VISIBLE);
        }
    }

    private void maybeActivateTarget(float distanceMeters, float accuracyMeters, String source) {
        if (isTargetReached) {
            return;
        }

        boolean reliable = !Float.isNaN(accuracyMeters) && accuracyMeters <= MAX_ACTIVATION_ACCURACY_METERS;
        boolean insideActivationRadius = distanceMeters <= ACTIVATION_RADIUS_METERS;
        if (!reliable || !insideActivationRadius) {
            resetActivationConfirmation();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (lastActivationConfirmationElapsedMs <= 0L
                || now - lastActivationConfirmationElapsedMs > ACTIVATION_CONFIRMATION_WINDOW_MS) {
            activationConfirmationCount = 0;
        }
        lastActivationConfirmationElapsedMs = now;
        activationConfirmationCount++;
        Log.d(TAG, "maybeActivateTarget: source=" + source
                + " distance=" + distanceMeters
                + " accuracy=" + accuracyMeters
                + " count=" + activationConfirmationCount);
        if (activationConfirmationCount >= REQUIRED_CONSECUTIVE_ACTIVATION_FIXES) {
            onTargetReached();
        }
    }

    private void resetActivationConfirmation() {
        activationConfirmationCount = 0;
        lastActivationConfirmationElapsedMs = 0L;
    }

    private void updateUserFixFromLocation(@NonNull Location location) {
        lastUserLat = location.getLatitude();
        lastUserLng = location.getLongitude();
        lastUserAccuracyMeters = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        lastUserFixElapsedRealtimeMs = getLocationElapsedRealtimeMs(location);
    }

    private void updateUserFixFromGeospatial(@NonNull GeospatialPose cameraPose) {
        lastUserLat = cameraPose.getLatitude();
        lastUserLng = cameraPose.getLongitude();
        lastUserAccuracyMeters = (float) cameraPose.getHorizontalAccuracy();
        lastUserFixElapsedRealtimeMs = SystemClock.elapsedRealtime();
    }

    private boolean isLocationFresh(@NonNull Location location) {
        return getLocationAgeMs(location) <= MAX_LOCATION_FIX_AGE_MS;
    }

    private long getLocationAgeMs(@NonNull Location location) {
        long elapsedRealtimeMs = getLocationElapsedRealtimeMs(location);
        return Math.max(0L, SystemClock.elapsedRealtime() - elapsedRealtimeMs);
    }

    private long getLocationElapsedRealtimeMs(@NonNull Location location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && location.getElapsedRealtimeNanos() > 0L) {
            return location.getElapsedRealtimeNanos() / 1_000_000L;
        }
        return SystemClock.elapsedRealtime();
    }

    private boolean hasReliableRelicGateFix() {
        if (Double.isNaN(lastUserLat) || Double.isNaN(lastUserLng)) {
            return false;
        }
        if (Float.isNaN(lastUserAccuracyMeters) || lastUserAccuracyMeters > MAX_RELIC_GATING_ACCURACY_METERS) {
            return false;
        }
        return lastUserFixElapsedRealtimeMs > 0L
                && SystemClock.elapsedRealtime() - lastUserFixElapsedRealtimeMs <= MAX_LOCATION_FIX_AGE_MS;
    }

    private boolean isWithinReliableRelicSpawnWindow(int slot) {
        if (!isTargetReached) {
            return false;
        }
        if (slot < 0 || relicLatitudes == null || slot >= relicLatitudes.length) {
            return false;
        }
        if (slot != currentRelicSlot()) {
            return false;
        }
        if (!hasReliableRelicGateFix()) {
            return false;
        }
        Location.distanceBetween(lastUserLat, lastUserLng,
                relicLatitudes[slot], relicLongitudes[slot], distanceResults);
        return distanceResults[0] <= RELIC_SPAWN_RADIUS_M;
    }

    // ──────────────────────────────────────────────────────────────────
    // Turn-by-turn direction display
    // ──────────────────────────────────────────────────────────────────

    /**
     * Updates the bottom banner and top label with the straight-line distance
     * to the mission target. Called directly from location callbacks so it
     * never depends on the routing network being available.
     */
    private void updateDistanceUI(float distanceMeters) {
        String distText = distanceMeters >= 1000f
                ? String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000f)
                : String.format(java.util.Locale.US, "%d m", (int) distanceMeters);
        if (tvDirectionDistance != null)
            tvDirectionDistance.setText(distText + " away");
        if (tvLocateLabel != null)
            tvLocateLabel.setText(missionName);
        if (tvLocateDistance != null)
            tvLocateDistance.setText("\uD83D\uDCCD " + distText + " away");
    }

    private void handleDirectionUpdate(NavigationDirectionManager.DirectionStep step) {
        if (isTargetReached || step == null)
            return;
        // Update the arrow and instruction text from the router.
        // Distance is handled by updateDistanceUI() so we skip step.distanceText here.
        if (step.icon == null) {
            // Use the rotating vector arrow
            if (ivDirectionArrow != null) {
                ivDirectionArrow.setRotation(step.arrowRotation);
                ivDirectionArrow.setVisibility(View.VISIBLE);
            }
            if (tvDirectionIcon != null)
                tvDirectionIcon.setVisibility(View.GONE);
        } else {
            // Special icon (📍 arrive, ↻ roundabout) — use the text fallback
            if (tvDirectionIcon != null) {
                tvDirectionIcon.setText(step.icon);
                tvDirectionIcon.setVisibility(View.VISIBLE);
            }
            if (ivDirectionArrow != null)
                ivDirectionArrow.setVisibility(View.GONE);
        }
        if (tvDirectionText != null)
            tvDirectionText.setText(step.label);
        if (directionBanner != null)
            directionBanner.setVisibility(View.VISIBLE);
    }

    // ──────────────────────────────────────────────────────────────────
    // Compass overlay — directional pointer toward the mission
    // ──────────────────────────────────────────────────────────────────

    private void toggleCompassOverlay() {
        if (compassOverlay == null)
            return;
        if (compassOverlay.getVisibility() == View.VISIBLE) {
            compassOverlay.setVisibility(View.GONE);
            if (btnCompass != null)
                btnCompass.setSelected(false);
        } else {
            // Navigation phase: raise the compass above the direction banner at the bottom
            setCompassMarginBottom(120);
            compassOverlay.setVisibility(View.VISIBLE);
            compassOverlay.bringToFront();
            if (btnCompass != null)
                btnCompass.setSelected(true);
            updateCompassArrow();
        }
    }

    /** Sets the compassOverlay bottom margin in dp, repositioning it on screen. */
    private void setCompassMarginBottom(int dp) {
        if (compassOverlay == null)
            return;
        float px = dp * getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) compassOverlay.getLayoutParams();
        lp.bottomMargin = Math.round(px);
        compassOverlay.setLayoutParams(lp);
    }

    /**
     * Rotates the compass arrow to point toward the mission target.
     * Called on every sensor update so the arrow tracks the device heading
     * smoothly. Uses straight-line bearing from current GPS to target.
     */
    private void updateCompassArrow() {
        if (compassOverlay == null || compassOverlay.getVisibility() != View.VISIBLE)
            return;
        if (compassArrow == null)
            return;
        if (Double.isNaN(lastUserLat) || Double.isNaN(lastUserLng)) {
            compassArrow.setRotation(0f);
            if (compassDistance != null)
                compassDistance.setText("Searching GPS…");
            return;
        }

        // During navigation phase aim at the mission centre; once the user has
        // arrived and relics are being hunted, aim at the current relic.
        // When the relic is actually spawned in the AR scene, prefer its real
        // world position over the configured GPS — the relic may have been
        // placed by the directional fallback and not exactly at the configured
        // coord, so we point the compass at where it actually is.
        int slot = isTargetReached ? currentRelicSlot() : -1;
        if (slot >= 0 && slot < coinAnchorNodes.size() && coinAnchorNodes.get(slot) != null
                && arCam != null && arCam.getArSceneView() != null
                && arCam.getArSceneView().getScene() != null) {
            AnchorNode an = coinAnchorNodes.get(slot);
            com.google.ar.sceneform.Camera sceneCam =
                    arCam.getArSceneView().getScene().getCamera();
            Vector3 relicWorld = an.getWorldPosition();
            Vector3 camWorld = sceneCam.getWorldPosition();
            float dxw = relicWorld.x - camWorld.x;
            float dzw = relicWorld.z - camWorld.z;
            float distArMeters = (float) Math.sqrt(dxw * dxw + dzw * dzw);
            // Direction from camera to relic, expressed in camera-local frame
            // so the compass arrow can rotate relative to where the device is
            // currently pointing. Camera local: +X right, +Y up, -Z forward.
            Vector3 deltaLocal = sceneCam.worldToLocalDirection(
                    new Vector3(dxw, 0f, dzw));
            float relativeDeg = (float) Math.toDegrees(
                    Math.atan2(deltaLocal.x, -deltaLocal.z));
            while (relativeDeg > 180f)  relativeDeg -= 360f;
            while (relativeDeg < -180f) relativeDeg += 360f;
            compassArrow.animate().rotation(relativeDeg).setDuration(200)
                    .setInterpolator(new android.view.animation.LinearInterpolator()).start();
            String distText = distArMeters >= 1000f
                    ? String.format(java.util.Locale.US, "%.1f km", distArMeters / 1000f)
                    : String.format(java.util.Locale.US, "%d m", (int) distArMeters);
            if (compassDistance != null)
                compassDistance.setText(distText);
            return;
        }

        // Pre-spawn (or pre-arrival) — fall back to GPS-bearing math.
        double aimLat = targetLatitude;
        double aimLng = targetLongitude;
        if (isTargetReached) {
            if (slot >= 0 && relicLatitudes != null && slot < relicLatitudes.length) {
                aimLat = relicLatitudes[slot];
                aimLng = relicLongitudes[slot];
            }
        }

        // Bearing (degrees, 0=N) from user to target
        double dLon = Math.toRadians(aimLng - lastUserLng);
        double lat1 = Math.toRadians(lastUserLat);
        double lat2 = Math.toRadians(aimLat);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        float bearingDeg = (float) Math.toDegrees(Math.atan2(y, x));
        if (bearingDeg < 0)
            bearingDeg += 360f;

        // Arrow rotation = bearing relative to device heading.
        // ic_compass_arrow already points up (north), so no offset needed.
        // Prefer the AR-aligned heading from ARCore Geospatial when available
        // — magnetometer drifts indoors and near metal, which causes the
        // compass to disagree with where the AR relic actually is.
        float headingDeg = !Float.isNaN(geospatialHeadingDeg)
                ? geospatialHeadingDeg
                : smoothedAzimuthDeg;
        float relative = bearingDeg - headingDeg;
        // Normalise to (-180, 180] so the animation always takes the short arc
        while (relative > 180f)  relative -= 360f;
        while (relative < -180f) relative += 360f;
        compassArrow.animate().rotation(relative).setDuration(200)
                .setInterpolator(new android.view.animation.LinearInterpolator()).start();

        // Distance label
        Location.distanceBetween(lastUserLat, lastUserLng,
                aimLat, aimLng, distanceResults);
        float distance = distanceResults[0];
        String distText = distance >= 1000f
                ? String.format(java.util.Locale.US, "%.1f km", distance / 1000f)
                : String.format(java.util.Locale.US, "%d m", (int) distance);
        if (compassDistance != null)
            compassDistance.setText(distText);
    }

    // ──────────────────────────────────────────────────────────────────
    // Tap-to-place
    // ──────────────────────────────────────────────────────────────────

    private void setupTapListener() {
        // Collection is button-only — no tap listener needed.
    }

    /**
     * Returns false for devices known to crash (SIGABRT in native sensor config)
     * when geospatial mode is enabled. All other devices are assumed compatible.
     */
    private boolean isDeviceGeospatialCompatible() {
        // SM-A356E (Galaxy A35 5G) causes a native SIGABRT in ConfigureRuntimeSensors.
        String model = android.os.Build.MODEL;
        return model == null || !model.equals("SM-A356E");
    }

    private void configureGeospatialMode() {
        if (geospatialConfigured || arCam == null || arCam.getArSceneView() == null) return;
        Session session = arCam.getArSceneView().getSession();
        if (session == null) return;

        if (!isDeviceGeospatialCompatible()) {
            Log.w(TAG, "Geospatial mode skipped — device excluded: " + android.os.Build.MODEL);
            geospatialConfigured = true;
            return;
        }

        try {
            Config config = session.getConfig();
            config.setGeospatialMode(Config.GeospatialMode.ENABLED);
            // ENVIRONMENTAL_HDR enables proper PBR material rendering: metallic/glossy
            // materials (peineta, farol, pocket watch) get an HDR environment map to
            // reflect, so they show correct colors instead of flat white.
            config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
            session.configure(config);
            geospatialConfigured = true;
            Log.d(TAG, "Geospatial + ENVIRONMENTAL_HDR enabled on " + android.os.Build.MODEL);
        } catch (Exception e) {
            // Device may not support geospatial; fall back to bearing-based placement.
            Log.w(TAG, "Geospatial mode failed to enable: " + e.getMessage());
            geospatialConfigured = true;
        }
    }

    private boolean isGeospatialAvailable() {
        if (!isDeviceGeospatialCompatible()) return false;
        if (arCam == null || arCam.getArSceneView() == null) return false;
        Session session = arCam.getArSceneView().getSession();
        if (session == null) return false;
        Earth earth = session.getEarth();
        return earth != null && earth.getTrackingState() == TrackingState.TRACKING;
    }



    /**
     * Fires an async terrain-anchor request for the given slot.
     * Uses ARCore's resolveAnchorOnTerrainAsync which queries Google Maps elevation
     * to place the item at EXACTLY the lat/lng and 1.5 m above ground — far more
     * accurate than earth.createAnchor() where we must guess the WGS84 altitude.
     * On success, calls spawnRelicNode() directly from the callback.
     * On failure, clears the pending flag so the next frame retries.
     */
    private void requestTerrainAnchorForSlot(int coinIdx, ModelRenderable renderable, String relicId) {
        if (!isGeospatialAvailable()) {
            terrainAnchorPending.put(coinIdx, false);
            return;
        }
        Session session = arCam.getArSceneView().getSession();
        Earth earth = session.getEarth();
        Log.d(TAG, "requestTerrainAnchorForSlot: starting terrain anchor for slot " + coinIdx);
        try {
            // ARCore 1.31+ signature: (lat, lng, altAboveTerrain, qx, qy, qz, qw, BiConsumer)
            // Quaternion is 4 individual floats (identity = 0,0,0,1).
            // Callback is invoked on the AR GL thread — safe for Sceneform node ops.
            earth.resolveAnchorOnTerrainAsync(
                    relicLatitudes[coinIdx], relicLongitudes[coinIdx],
                    1.6,  // 1.6 m above terrain ≈ eye/chest level; Google Maps provides ground truth
                    0f, 0f, 0f, 1f,
                    (anchor, state) -> {
                        if (state == Anchor.TerrainAnchorState.SUCCESS) {
                            Log.d(TAG, "Terrain anchor SUCCESS for slot " + coinIdx);
                            spawnRelicNode(coinIdx, anchor, renderable, relicId);
                        } else {
                            // No fallback. Clear the pending flag so the next
                            // frame retries the terrain-anchor request — the only
                            // way to place the relic at the exact lat/lng.
                            Log.w(TAG, "Terrain anchor failed slot " + coinIdx + ": " + state
                                    + " — will retry next frame");
                            terrainAnchorPending.put(coinIdx, false);
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "requestTerrainAnchorForSlot: exception slot " + coinIdx + ": " + e.getMessage());
            terrainAnchorPending.put(coinIdx, false);
        }
    }

    /**
     * Attaches a Sceneform Node to the given anchor and registers it in the
     * coinAnchorNodes / coinNodes lists for the given slot.
     * Must be called on the main thread.
     */
    private void spawnRelicNode(int coinIdx, Anchor anchor, ModelRenderable coinRenderable, String relicIdForThisCoin) {
        // Guard: slot may have been collected while the async anchor was pending.
        if (coinSlotCollected != null && coinIdx < coinSlotCollected.length
                && coinSlotCollected[coinIdx]) {
            try { anchor.detach(); } catch (Exception ignored) {}
            return;
        }
        // Strong gate: async terrain callbacks are not allowed to surface a
        // relic unless the player is confirmed inside the spawn window for the
        // active slot right now.
        if (!isWithinReliableRelicSpawnWindow(coinIdx)) {
            Log.d(TAG, "spawnRelicNode: rejected slot=" + coinIdx
                    + " reached=" + isTargetReached
                    + " accuracy=" + lastUserAccuracyMeters);
            terrainAnchorPending.remove(coinIdx);
            try { anchor.detach(); } catch (Exception ignored) {}
            return;
        }
        // Guard: node already in slot (concurrent callback + frame loop).
        while (coinAnchorNodes.size() <= coinIdx) coinAnchorNodes.add(null);
        while (coinNodes.size() <= coinIdx) coinNodes.add(null);
        if (coinAnchorNodes.get(coinIdx) != null) {
            try { anchor.detach(); } catch (Exception ignored) {}
            return;
        }
        try {
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arCam.getArSceneView().getScene());

            Node coinNode = new Node();
            coinNode.setParent(anchorNode);
            coinNode.setRenderable(coinRenderable);
            float relicScale = scaleForRelic(relicIdForThisCoin);
            coinNode.setLocalScale(new Vector3(relicScale, relicScale, relicScale));
            coinNode.setOnTapListener(null);
            final int capturedIdx = coinIdx;
            final String capturedRelicId = relicIdForThisCoin;

            coinAnchorNodes.set(coinIdx, anchorNode);
            coinNodes.set(coinIdx, coinNode);
            Log.e(TAG, "spawnRelicNode: spawned slot " + coinIdx
                    + " relic=" + relicIdForThisCoin
                    + " geospatial=" + isGeospatialAvailable());

            if (relicIds != null) {
                final String relicName = displayNameForRelic(capturedRelicId);
                final int totalRelics = relicLatitudes.length;
                final int slot = capturedIdx + 1;
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Find the " + relicName + " (" + slot + " of " + totalRelics + ")",
                            Toast.LENGTH_LONG).show();
                    updateHint("Find the " + relicName + " — walk close and press Collect.");
                });
            } else if (coinAnchorNodes.size() == 1) {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            relicLatitudes.length > 1
                                    ? "Coins are floating nearby! Find and tap them!"
                                    : "A coin is floating nearby! Find and tap it!",
                            Toast.LENGTH_LONG).show();
                    updateHint(relicLatitudes.length > 1
                            ? "Find the floating coins and tap them to complete the mission!"
                            : "Find the floating coin and tap it to complete the mission!");
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "spawnRelicNode: failed for slot " + coinIdx + ": " + e.getMessage());
            // Reset the pending flag so the spawn loop retries on the next frame
            // rather than blocking permanently on a slot that failed to attach.
            terrainAnchorPending.put(coinIdx, false);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Mission completion
    // ──────────────────────────────────────────────────────────────────

    /**
     * Called once the mission objective is met (coin tapped, or fallback
     * model placed). Reports completion to the backend, then fetches the
     * overall progress so the caller can branch on the all-missions-complete
     * outcome without racing a second dialog.
     *
     * Both callbacks may be null — pass null when the caller only needs the
     * fire-and-forget behavior of the legacy plane-tap flow.
     *
     * @param onSuccess receives {@code true} when this mission completed the
     *                  full set of 5, {@code false} otherwise.
     */
    private void onMissionModelPlaced(java.util.function.Consumer<Boolean> onSuccess,
            java.util.function.Consumer<String> onError) {
        if (username.isEmpty() || missionId.equals("unknown")) {
            if (onError != null)
                onError.accept("Mission data missing.");
            return;
        }

        MissionCompletionHelper.completeMission(this, missionId,
                new MissionCompletionHelper.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        MissionCompletionHelper.getMissionProgress(ARActivity.this,
                                new MissionCompletionHelper.ProgressCallback() {
                                    @Override
                                    public void onResult(java.util.Set<String> ids, boolean allComplete) {
                                        if (onSuccess != null)
                                            onSuccess.accept(allComplete);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // Completion itself succeeded — treat as "not all complete"
                                        // rather than blocking the user with an error dialog.
                                        Log.w(TAG, "Progress check failed: " + message);
                                        if (onSuccess != null)
                                            onSuccess.accept(false);
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Mission completion sync failed: " + message);
                        if (onError != null)
                            onError.accept(message);
                    }
                });
    }

    private void showNFTUnlockedDialog() {
        if (isFinishing() || isDestroyed())
            return;
        new AlertDialog.Builder(this)
                .setTitle("Volver Heritage Souvenir Complete!")
                .setMessage(
                        "You have completed the Volver mission list. Return to the home screen to claim your heritage NFT.")
                .setPositiveButton("Go to Home", (d, w) -> finish())
                .setNegativeButton("Stay in AR", null)
                .show();
    }

    // ──────────────────────────────────────────────────────────────────
    // ──────────────────────────────────────────────────────────────────
    // Coin model preload
    // ──────────────────────────────────────────────────────────────────

    private void preloadCoinModel() {
        coinModelFuture = ModelRenderable.builder()
                .setSource(this, R.raw.intramuros_coin)
                .setIsFilamentGltf(true)
                .build();
        coinModelFuture.thenAccept(r -> resolvedCoinRenderable = r);
        coinModelFuture.exceptionally(t -> {
            Log.e(TAG, "Failed to load coin model: " + t.getMessage());
            return null;
        });

        // For staged-relic missions (e.g. Casa Manila), preload one renderable
        // per unique relic id so each spawn uses the correct GLB.
        if (relicIds != null) {
            Set<String> unique = new HashSet<>();
            for (String id : relicIds)
                if (id != null)
                    unique.add(id);
            for (String relicId : unique) {
                Integer rawRes = resourceForRelic(relicId);
                if (rawRes == null)
                    continue;
                CompletableFuture<ModelRenderable> fut = ModelRenderable.builder()
                        .setSource(this, rawRes)
                        .setIsFilamentGltf(true)
                        .build();
                fut.thenAccept(r -> { if (r != null) resolvedRelicRenderables.put(relicId, r); });
                fut.exceptionally(t -> {
                    Log.e(TAG, "Failed to load relic model " + relicId + ": " + t.getMessage());
                    return null;
                });
                relicModelFutures.put(relicId, fut);
            }
        }
    }

    /**
     * Display scale for each relic model. The coin was tuned at 0.25; the other
     * artefact GLBs are modelled at a smaller native size and need a larger scale
     * to be clearly visible to the user.
     */
    private float scaleForRelic(String relicId) {
        if (relicId == null)
            return COIN_SCALE;
        switch (relicId) {
            case "intramuros_coin":
                return 0.25f;
            case "peineta":
                return 0.90f;
            case "salakot_elite":
                return 0.55f;
            case "farol_de_aceite":
                return 1.00f;
            case "pocket_watch":
                return 2.75f;
            default:
                return 0.50f;
        }
    }

    /** Maps a relic id to its raw GLB resource. Returns null for unknown ids. */
    private Integer resourceForRelic(String relicId) {
        if (relicId == null)
            return null;
        switch (relicId) {
            case "intramuros_coin":
                return R.raw.intramuros_coin;
            case "peineta":
                return R.raw.peineta;
            case "salakot_elite":
                return R.raw.salakot_elite;
            case "farol_de_aceite":
                return R.raw.farol_de_aceite;
            case "pocket_watch":
                return R.raw.pocket_watch;
            default:
                return null;
        }
    }

    /** Display name for a relic, used in toasts and hints. */
    private String displayNameForRelic(String relicId) {
        if (relicId == null)
            return "Coin";
        switch (relicId) {
            case "intramuros_coin":
                return "Intramuros Coin";
            case "peineta":
                return "Peineta";
            case "salakot_elite":
                return "Salakot";
            case "farol_de_aceite":
                return "Farol de Aceite";
            case "pocket_watch":
                return "Antique Pocket Watch";
            default:
                return "Relic";
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Minimap setup
    // ──────────────────────────────────────────────────────────────────

    private void setupMinimap() {
        if (minimap == null)
            return;
        minimap.setTileSource(TileSourceFactory.MAPNIK);
        minimap.setMultiTouchControls(false);
        minimap.setClickable(false);
        minimap.getController().setZoom(18.5);
        minimap.getController().setCenter(new GeoPoint(targetLatitude, targetLongitude));

        // Green dot for user — created on first GPS update via updateMinimapUser()
        if (!Double.isNaN(lastUserLat) && !Double.isNaN(lastUserLng)) {
            updateMinimapUser();
        }

        // Pre-fill the marker list with nulls so indices align with relic
        // indices, then show only the next uncollected relic's red dot.
        if (relicLatitudes != null && relicLongitudes != null) {
            for (int i = 0; i < relicLatitudes.length; i++)
                coinMapMarkers.add(null);
            int nextSlot = currentRelicSlot();
            if (nextSlot >= 0) {
                showRelicDot(nextSlot);
            }
        }
    }

    /** Adds the red “relic” dot for the given index to the minimap. */
    private void showRelicDot(int index) {
        if (minimap == null)
            return;
        if (relicLatitudes == null || index < 0 || index >= relicLatitudes.length)
            return;
        if (index < coinMapMarkers.size() && coinMapMarkers.get(index) != null)
            return;
        Marker m = new Marker(minimap);
        m.setPosition(new GeoPoint(relicLatitudes[index], relicLongitudes[index]));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setIcon(createDotDrawable(Color.parseColor("#E53935"), 18));
        m.setTitle("Relic " + (index + 1));
        minimap.getOverlays().add(m);
        if (index < coinMapMarkers.size()) {
            coinMapMarkers.set(index, m);
        } else {
            coinMapMarkers.add(m);
        }
        minimap.invalidate();
    }

    /** Swaps the minimap and compass overlay in the bottom-right slot. */
    private void toggleMapCompass() {
        if (minimapContainer == null || compassOverlay == null)
            return;
        // Toggle the user's preference rather than reading current visibility —
        // updateRelicHud() runs every frame and may have temporarily forced
        // either view, so reading visibility here can give the wrong answer.
        isCompassToggled = !isCompassToggled;
        if (isCompassToggled) {
            minimapContainer.setVisibility(View.GONE);
            // Relic phase: compass sits in the minimap slot — restore original bottom
            // margin
            setCompassMarginBottom(64);
            compassOverlay.setVisibility(View.VISIBLE);
            compassOverlay.bringToFront();
            if (btnMapCompassToggle != null)
                btnMapCompassToggle.bringToFront();
            if (tvMapCompassIcon != null)
                tvMapCompassIcon.setText("🗺️");
            updateCompassArrow();
        } else {
            compassOverlay.setVisibility(View.GONE);
            minimapContainer.setVisibility(View.VISIBLE);
            minimapContainer.bringToFront();
            if (btnMapCompassToggle != null)
                btnMapCompassToggle.bringToFront();
            if (tvMapCompassIcon != null)
                tvMapCompassIcon.setText("🧭");
        }
    }

    /** Adds or moves the user's "you-are-here" marker on the minimap. */
    private void updateMinimapUser() {
        if (minimap == null)
            return;
        if (Double.isNaN(lastUserLat) || Double.isNaN(lastUserLng))
            return;
        GeoPoint pos = new GeoPoint(lastUserLat, lastUserLng);
        if (userMarker == null) {
            userMarker = new Marker(minimap);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            userMarker.setIcon(createDotDrawable(Color.parseColor("#4CAF50"), 24));
            userMarker.setTitle("You");
            minimap.getOverlays().add(userMarker);
        }
        userMarker.setPosition(pos);
        // Keep the user centred so the map visibly tracks them.
        minimap.getController().setCenter(pos);
        minimap.invalidate();
    }

    /**
     * Updates the on-screen relic-hunt hint with live distance to the next relic.
     */
    /**
     * Returns the index of the relic the user is currently hunting (spawned
     * or next staged), or -1 if none. Shared by HUD + compass + minimap.
     */
    private int currentRelicSlot() {
        if (relicLatitudes == null || relicLatitudes.length == 0)
            return -1;
        if (coinSlotCollected == null || coinSlotCollected.length != relicLatitudes.length)
            return -1;
        // First slot that hasn't been collected yet is the active one.
        for (int i = 0; i < coinSlotCollected.length; i++) {
            if (!coinSlotCollected[i])
                return i;
        }
        return -1; // all done
    }

    private void restoreSavedRelicProgress() {
        if (relicLatitudes == null || coinSlotCollected == null
                || coinSlotCollected.length != relicLatitudes.length) {
            return;
        }
        boolean[] savedSlots = UserProgressStore.loadCollectedSlots(
                this, missionId, relicLatitudes.length);
        int restored = 0;
        for (int i = 0; i < savedSlots.length; i++) {
            if (savedSlots[i]) {
                coinSlotCollected[i] = true;
                restored++;
            }
        }
        coinsCollected = restored;
        coinCollectedValue = restored * COIN_VALUE;
        if (restored > 0) {
            Log.d(TAG, "restoreSavedRelicProgress: " + restored
                    + "/" + relicLatitudes.length + " slots restored for " + missionId);
        }
    }

    private void updateRelicHud() {
        if (!isTargetReached)
            return;
        if (relicIds == null || relicLatitudes == null)
            return;
        if (Double.isNaN(lastUserLat) || Double.isNaN(lastUserLng))
            return;

        int slot = currentRelicSlot();
        if (slot < 0 || slot >= relicLatitudes.length) {
            runOnUiThread(() -> {
                if (collectButtonContainer != null)
                    collectButtonContainer.setVisibility(View.GONE);
                // Respect the user's map/compass toggle when all relics done.
                if (minimapContainer != null)
                    minimapContainer.setVisibility(isCompassToggled ? View.GONE : View.VISIBLE);
                if (compassOverlay != null)
                    compassOverlay.setVisibility(isCompassToggled ? View.VISIBLE : View.GONE);
                if (btnMapCompassToggle != null)
                    btnMapCompassToggle.setVisibility(View.VISIBLE);
            });
            return;
        }

        String relicName = displayNameForRelic(relicIds[slot]);
        boolean relicSpawned = slot < coinAnchorNodes.size() && coinAnchorNodes.get(slot) != null;

        // Distance: when the relic is spawned in the scene we use the actual
        // AR-world distance from camera to anchor (so the 5 m collect gate
        // matches what the user sees), else fall back to GPS distance for the
        // pre-spawn "walk closer" hint.
        float distMeters;
        if (relicSpawned && arCam != null && arCam.getArSceneView() != null
                && arCam.getArSceneView().getScene() != null) {
            AnchorNode an = coinAnchorNodes.get(slot);
            Vector3 rp = an.getWorldPosition();
            Vector3 cp = arCam.getArSceneView().getScene().getCamera().getWorldPosition();
            float dxw = rp.x - cp.x;
            float dzw = rp.z - cp.z;
            distMeters = (float) Math.sqrt(dxw * dxw + dzw * dzw);
        } else {
            float[] d = new float[1];
            Location.distanceBetween(lastUserLat, lastUserLng,
                    relicLatitudes[slot], relicLongitudes[slot], d);
            distMeters = d[0];
        }
        int meters = Math.round(distMeters);
        int oneBased = slot + 1;
        // COLLECT button only appears when the user is genuinely close (5 m or less).
        boolean withinCollectRange = relicSpawned && meters <= RELIC_COLLECT_BUTTON_M;
        String suffix;
        if (withinCollectRange) {
            suffix = "press Collect";
        } else if (relicSpawned) {
            suffix = "walk closer to collect";
        } else {
            suffix = meters <= RELIC_SPAWN_RADIUS_M ? "spawning…" : "walk closer";
        }
        final String hint = ordinal(oneBased) + " " + relicName + " — " + meters + " m away · " + suffix;
        final String buttonText = "Collect " + relicName;
        runOnUiThread(() -> {
            updateHint(hint);
            // COLLECT button shown only when within collect range AND not already animating.
            if (collectButtonContainer != null && !isCollecting) {
                collectButtonContainer.setVisibility(withinCollectRange ? View.VISIBLE : View.GONE);
                if (withinCollectRange && tvCollectLabel != null)
                    tvCollectLabel.setText(buttonText);
            }
            if (withinCollectRange) {
                // Button is showing — hide both map/compass to keep the layout clean.
                if (minimapContainer != null)
                    minimapContainer.setVisibility(View.GONE);
                if (compassOverlay != null)
                    compassOverlay.setVisibility(View.GONE);
            } else {
                if (minimapContainer != null)
                    minimapContainer.setVisibility(isCompassToggled ? View.GONE : View.VISIBLE);
                if (compassOverlay != null)
                    compassOverlay.setVisibility(isCompassToggled ? View.VISIBLE : View.GONE);
            }
            if (btnMapCompassToggle != null)
                btnMapCompassToggle.setVisibility(withinCollectRange ? View.GONE : View.VISIBLE);
        });
    }

    /** Collects the currently active relic — called by the COLLECT button. */
    private float distanceToAnchorMeters(AnchorNode anchorNode) {
        if (anchorNode == null || arCam == null || arCam.getArSceneView() == null
                || arCam.getArSceneView().getScene() == null) {
            return Float.MAX_VALUE;
        }
        Vector3 relicWorld = anchorNode.getWorldPosition();
        Vector3 cameraWorld = arCam.getArSceneView().getScene().getCamera().getWorldPosition();
        float dx = relicWorld.x - cameraWorld.x;
        float dz = relicWorld.z - cameraWorld.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void collectCurrentRelic() {
        // Debounce: ignore taps while a previous collect animation is in flight
        // so the user can't double-credit a relic or fire stale taps after the
        // anchor has been detached.
        if (isCollecting)
            return;
        int slot = currentRelicSlot();
        if (slot < 0 || slot >= coinAnchorNodes.size())
            return;
        AnchorNode anchorNode = coinAnchorNodes.get(slot);
        if (anchorNode == null)
            return;
        if (distanceToAnchorMeters(anchorNode) > RELIC_COLLECT_BUTTON_M) {
            updateRelicHud();
            return;
        }
        isCollecting = true;
        // Hide the button immediately so it can't be tapped again during the
        // bounce animation. playCollectAnimation() still runs its scale anim
        // on the (now-invisible) container and resets state at the end.
        if (collectButtonContainer != null) {
            collectButtonContainer.setClickable(false);
            collectButtonContainer.setVisibility(View.GONE);
        }
        String relicId = (relicIds != null && slot < relicIds.length)
                ? relicIds[slot] : null;
        String relicName = displayNameForRelic(relicId);
        playCollectAnimation(relicName);
        collectCoin(slot, anchorNode, relicId);
    }

    /**
     * Button bounce + floating "✓ Collected!" text that slides up and fades out.
     * Called on the UI thread before collectCoin() removes the model.
     */
    private void playCollectAnimation(String relicName) {
        // Bounce the button: scale up slightly then shrink to nothing
        if (collectButtonContainer != null) {
            collectButtonContainer.animate().cancel();
            collectButtonContainer.setScaleX(1f);
            collectButtonContainer.setScaleY(1f);
            ObjectAnimator sx = ObjectAnimator.ofFloat(collectButtonContainer, "scaleX", 1f, 1.2f, 0f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(collectButtonContainer, "scaleY", 1f, 1.2f, 0f);
            sx.setDuration(350);
            sy.setDuration(350);
            AnimatorSet btnAnim = new AnimatorSet();
            btnAnim.playTogether(sx, sy);
            btnAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (collectButtonContainer != null) {
                        collectButtonContainer.setVisibility(View.GONE);
                        collectButtonContainer.setScaleX(1f);
                        collectButtonContainer.setScaleY(1f);
                    }
                }
            });
            btnAnim.start();
        }

        // Floating "✓ Relic Collected!" label — slides up and fades out
        if (tvCollectedFeedback != null) {
            tvCollectedFeedback.setText("✓ Relic Collected!");
            tvCollectedFeedback.setVisibility(View.VISIBLE);
            tvCollectedFeedback.setAlpha(1f);
            tvCollectedFeedback.setTranslationY(0f);

            ObjectAnimator moveUp = ObjectAnimator.ofFloat(tvCollectedFeedback, "translationY", 0f, -180f);
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(tvCollectedFeedback, "alpha", 1f, 0f);
            moveUp.setDuration(1400);
            fadeOut.setDuration(900);
            fadeOut.setStartDelay(500);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(moveUp, fadeOut);
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (tvCollectedFeedback != null) {
                        tvCollectedFeedback.setVisibility(View.GONE);
                        tvCollectedFeedback.setAlpha(1f);
                        tvCollectedFeedback.setTranslationY(0f);
                    }
                    // Re-arm the COLLECT button for the next relic.
                    isCollecting = false;
                    if (collectButtonContainer != null)
                        collectButtonContainer.setClickable(true);
                }
            });
            set.start();
        } else {
            // No feedback view — re-arm immediately so we don't lock the user out.
            isCollecting = false;
            if (collectButtonContainer != null)
                collectButtonContainer.setClickable(true);
        }
    }

    /**
     * Removes a staged relic from the scene/minimap if the user has walked beyond
     * the despawn radius, so it re-spawns when they return.
     */
    private void enforceRelicSpawnRadius() {
        // DISABLED: a relic must NEVER move once it has been placed at its
        // configured GPS coordinate. The previous despawn/respawn behaviour
        // (when the user wandered beyond RELIC_DESPAWN_RADIUS_M) caused the
        // relic to be re-anchored at a slightly different camera-relative
        // position on return, which the player perceived as the relic
        // "moving". Leaving this method as a no-op keeps the original anchor
        // for the entire mission so the relic stays exactly where it spawned.
        if (true)
            return;
        // ── unreachable legacy logic, retained for reference ──
        if (!isGeospatialAvailable())
            return;
        if (relicIds == null)
            return;
        if (coinAnchorNodes.isEmpty())
            return;
        if (Double.isNaN(lastUserLat) || Double.isNaN(lastUserLng))
            return;
        int lastIdx = coinAnchorNodes.size() - 1;
        AnchorNode last = coinAnchorNodes.get(lastIdx);
        if (last == null)
            return; // already collected
        float[] d = new float[1];
        Location.distanceBetween(lastUserLat, lastUserLng,
                relicLatitudes[lastIdx], relicLongitudes[lastIdx], d);
        if (d[0] <= RELIC_DESPAWN_RADIUS_M)
            return;

        // Detach from scene
        last.setParent(null);
        if (last.getAnchor() != null) {
            try {
                last.getAnchor().detach();
            } catch (Exception ignored) {
            }
        }
        coinAnchorNodes.remove(lastIdx);
        if (lastIdx < coinNodes.size())
            coinNodes.remove(lastIdx);

        // NOTE: leave coinMapMarkers alone \u2014 the red dot on the map should
        // persist even when the AR relic despawns (so the user can still see
        // where to walk back to). The dot is only removed on collection.
        coinsSpawned = false;
    }

    /**
     * Detects when the active relic's ARCore anchor has permanently lost tracking
     * (TrackingState.STOPPED) and clears the slot so tryAutoSpawnCoins() will
     * re-create it on the next frame. This handles the "item disappears when
     * looking away" case: ARCore VIO resets the anchor, Sceneform stops rendering
     * it, and this method makes it reappear by re-spawning at the same GPS direction.
     */
    private void checkAndRespawnLostAnchor() {
        int slot = currentRelicSlot();
        if (slot < 0 || slot >= coinAnchorNodes.size()) return;
        AnchorNode anchorNode = coinAnchorNodes.get(slot);
        if (anchorNode == null) return;
        Anchor anchor = anchorNode.getAnchor();
        if (anchor == null || anchor.getTrackingState() != TrackingState.STOPPED) return;
        Log.w(TAG, "checkAndRespawnLostAnchor: slot " + slot + " anchor STOPPED — clearing for re-spawn");
        try { anchorNode.setParent(null); } catch (Exception ignored) {}
        try { anchor.detach(); } catch (Exception ignored) {}
        coinAnchorNodes.set(slot, null);
        if (slot < coinNodes.size()) coinNodes.set(slot, null);
        terrainAnchorPending.remove(slot); // allow a fresh terrain-anchor request
        // Also discard any pre-resolved anchor for this slot — it came from the same
        // lost session state and cannot be trusted for re-spawn.
        preResolvedAnchors.remove(slot);
        nextSlotPreWarmPending.remove(slot);
        preResolvedRenderables.remove(slot);
        preResolvedRelicIds.remove(slot);
        // coinsSpawned is already false (never set true mid-mission),
        // so tryAutoSpawnCoins() will re-spawn this slot next frame.
    }

    /**
     * Fires a terrain anchor request for the current uncollected slot as soon
     * as geospatial is tracking — BEFORE the user reaches the relic location.
     * By the time the user arrives, the anchor is already resolved so
     * spawnRelicNode() is called instantly from the callback with no perceptible
     * delay.  Safe to call every frame; the terrainAnchorPending guard prevents
     * duplicate requests.
     */
    private void preWarmCurrentSlotTerrainAnchor() {
        if (!isGeospatialAvailable()) return;
        int slot = currentRelicSlot();
        if (slot < 0) return;
        if (Boolean.TRUE.equals(terrainAnchorPending.get(slot))) return;
        // If preResolveNextSlotAnchor() already has a resolved anchor for this slot,
        // do NOT fire a duplicate requestTerrainAnchorForSlot() — that would set
        // terrainAnchorPending[slot]=true and cause the routing block in
        // tryAutoSpawnCoins() to return early, bypassing the pre-resolved path
        // entirely so the anchor leaks and spawn takes 1-3s instead of 0s.
        if (preResolvedAnchors.containsKey(slot)) return;
        // Ensure list is padded so the duplicate-spawn guard in spawnRelicNode works.
        while (coinAnchorNodes.size() <= slot) coinAnchorNodes.add(null);
        if (coinAnchorNodes.get(slot) != null) return; // already spawned
        // Only pre-warm when the model is already in memory.
        String relicId = (relicIds != null && slot < relicIds.length)
                ? relicIds[slot] : null;
        ModelRenderable renderable;
        if (relicId != null) {
            renderable = resolvedRelicRenderables.get(relicId);
            if (renderable == null) return;
        } else {
            renderable = resolvedCoinRenderable;
            if (renderable == null) return;
        }
        terrainAnchorPending.put(slot, true);
        requestTerrainAnchorForSlot(slot, renderable, relicId);
        Log.d(TAG, "preWarmCurrentSlotTerrainAnchor: fired for slot=" + slot);
    }

    /**
     * Calls preResolveNextSlotAnchor for every uncollected slot beyond the current one.
     * Called every frame from onSceneUpdate so failures are retried automatically and
     * ALL upcoming slots are pre-resolved concurrently (not just the next one).
     */
    private void preResolveUpcomingSlotAnchors() {
        if (!isGeospatialAvailable()) return;
        if (relicLatitudes == null) return;
        int current = currentRelicSlot();
        if (current < 0) return;
        // Pre-resolve every slot after the current active one.
        for (int s = current + 1; s < relicLatitudes.length; s++) {
            if (coinSlotCollected != null && s < coinSlotCollected.length && coinSlotCollected[s]) continue;
            preResolveNextSlotAnchor(s);
        }
    }

    /**
     * Resolves a terrain anchor for an UPCOMING slot (slot N+1) WITHOUT
     * attaching any Sceneform node. The resolved anchor is stored in
     * {@link #preResolvedAnchors} and consumed by tryAutoSpawnCoins() the
     * instant that slot becomes the active one — giving zero latency after
     * each collection instead of the usual 1–3 s terrain-anchor round-trip.
     *
     * Uses {@link #nextSlotPreWarmPending} (not terrainAnchorPending) so the
     * spawn-loop guards for the current slot are unaffected.
     */
    private void preResolveNextSlotAnchor(int slot) {
        if (!isGeospatialAvailable()) return;
        if (relicLatitudes == null || slot >= relicLatitudes.length) return;
        if (preResolvedAnchors.containsKey(slot)) return;             // already resolved
        if (Boolean.TRUE.equals(nextSlotPreWarmPending.get(slot))) return; // in-flight
        if (Boolean.TRUE.equals(terrainAnchorPending.get(slot))) return;   // spawn req in-flight

        String relicId = (relicIds != null && slot < relicIds.length)
                ? relicIds[slot] : null;
        ModelRenderable renderable;
        if (relicId != null) {
            renderable = resolvedRelicRenderables.get(relicId);
            if (renderable == null) return;
        } else {
            renderable = resolvedCoinRenderable;
            if (renderable == null) return;
        }

        Session session = arCam.getArSceneView().getSession();
        Earth earth = session.getEarth();
        nextSlotPreWarmPending.put(slot, true);
        preResolvedRenderables.put(slot, renderable);
        preResolvedRelicIds.put(slot, relicId);
        Log.d(TAG, "preResolveNextSlotAnchor: firing for slot=" + slot);
        try {
            earth.resolveAnchorOnTerrainAsync(
                    relicLatitudes[slot], relicLongitudes[slot],
                    1.6, 0f, 0f, 0f, 1f,
                    (anchor, state) -> {
                        if (state == Anchor.TerrainAnchorState.SUCCESS) {
                            preResolvedAnchors.put(slot, anchor);
                            Log.d(TAG, "preResolveNextSlotAnchor: SUCCESS for slot=" + slot);
                        } else {
                            Log.w(TAG, "preResolveNextSlotAnchor: FAILED slot=" + slot + " state=" + state);
                            nextSlotPreWarmPending.put(slot, false);
                            preResolvedRenderables.remove(slot);
                            preResolvedRelicIds.remove(slot);
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "preResolveNextSlotAnchor: exception slot=" + slot + ": " + e.getMessage());
            nextSlotPreWarmPending.put(slot, false);
            preResolvedRenderables.remove(slot);
            preResolvedRelicIds.remove(slot);
        }
    }

    private static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13)
            return n + "th";
        switch (n % 10) {
            case 1:
                return n + "st";
            case 2:
                return n + "nd";
            case 3:
                return n + "rd";
            default:
                return n + "th";
        }
    }

    private void addCoinMarkerToMinimap(GeoPoint point) {
        if (minimap == null)
            return;
        Marker m = new Marker(minimap);
        m.setPosition(point);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setIcon(createDotDrawable(Color.RED, 20));
        m.setTitle("Coin");
        minimap.getOverlays().add(m);
        coinMapMarkers.add(m);
        minimap.invalidate();
    }

    private BitmapDrawable createDotDrawable(int color, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1, paint);
        return new BitmapDrawable(getResources(), bmp);
    }

    // ──────────────────────────────────────────────────────────────────
    // Scattered coin spawning
    // ──────────────────────────────────────────────────────────────────

    private void tryAutoSpawnCoins() {
        if (resolvedCoinRenderable == null)
            return;
        if (arCam == null || arCam.getArSceneView() == null)
            return;

        // Use the authoritative collected-flag array to decide which slot is
        // next, NOT coinAnchorNodes.size(). The list grows by one on every
        // spawn and never shrinks; combined with idempotent collectCoin() this
        // keeps list-index == slot-index, but if the two ever diverge we'd
        // rather trust the explicit flag and skip already-collected slots.
        int coinIdx = currentRelicSlot();
        if (coinIdx < 0) {
            coinsSpawned = true;
            return;
        }

        // Pad the anchor / node lists so list-index == slot-index. Using set()
        // (instead of add()) for the actual spawn below makes it impossible
        // for a stray spawn to land in the wrong slot.
        while (coinAnchorNodes.size() <= coinIdx)
            coinAnchorNodes.add(null);
        while (coinNodes.size() <= coinIdx)
            coinNodes.add(null);

        // If the slot already has a live anchor (e.g. spawn ran twice in the
        // same frame for some reason), don't replace it.
        if (coinAnchorNodes.get(coinIdx) != null)
            return;

        // (currentRelicSlot already skips collected slots, so no need for the
        // legacy prev-relic gate any more — the slot we are about to spawn is
        // by definition the next uncollected one.)

        // Brief delay after collection so the old relic's disappear animation
        // finishes before the next one spawns in the same direction.
        if (relicIds != null && System.currentTimeMillis() - lastCollectedTimeMs < 1500L)
            return;

        // ── Spawn-radius gate (applies to ALL devices) ────────────────────────────
        // The relic only spawns once the user is within RELIC_SPAWN_RADIUS_M of
        // the configured GPS coordinate. Beyond that the HUD shows "walk closer".
        if (!isWithinReliableRelicSpawnWindow(coinIdx))
            return;

        // ── Routing ───────────────────────────────────────────────────────────────
        // Strategy: place the relic AS CLOSE TO the configured GPS coordinate as
        // possible. Two paths, both unconditional — we never block the user on
        // VPS lock:
        //   1. PREFERRED — terrain anchor (exact lat/lng). Used only when Earth
        //      is TRACKING with horizontal accuracy ≤ 5 m. We also use a
        //      pre-resolved anchor from the previous slot if one is ready.
        //   2. FALLBACK — directional camera spawn. Project the configured
        //      coordinate from the user's current GPS using the device heading,
        //      and anchor that pose in the AR session. The relic ends up in
        //      roughly the right direction at roughly the right distance, then
        //      the compass + distance HUD read the anchor's actual world
        //      position so the user can walk straight to it.
        if (Boolean.TRUE.equals(terrainAnchorPending.get(coinIdx))) {
            return; // a terrain-anchor request is in flight — its callback will spawn
        }

        // Pick the right renderable: per-relic for staged missions, else default.
        ModelRenderable coinRenderable = null;
        String relicIdForThisCoin = (relicIds != null) ? relicIds[coinIdx] : null;
        if (relicIdForThisCoin != null) {
            CompletableFuture<ModelRenderable> fut = relicModelFutures.get(relicIdForThisCoin);
            if (fut == null || !fut.isDone()) {
                // Model still loading — wait for the correct model rather than
                // falling back to coin. A fallback placed here would occupy the
                // slot permanently, preventing the real relic from ever appearing.
                return;
            }
            // Use the pre-resolved cached renderable — avoids blocking on get()
            // from the render thread which causes a visible camera freeze.
            coinRenderable = resolvedRelicRenderables.get(relicIdForThisCoin);
            if (coinRenderable == null) {
                if (fut.isCompletedExceptionally()) {
                    // Model failed — fall back to coin renderable
                    Log.w(TAG, "Relic model failed to load for " + relicIdForThisCoin + ", using coin fallback");
                    coinRenderable = resolvedCoinRenderable;
                } else {
                    // Future done but cache miss — shouldn't happen, guard anyway
                    return;
                }
            }
        } else {
            coinRenderable = resolvedCoinRenderable;
        }
        if (coinRenderable == null)
            return;

        Frame frame = arCam.getArSceneView().getArFrame();
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING)
            return;

        // ── Anchor: prefer pre-resolved terrain anchor → live terrain anchor → directional spawn ──
        // Pre-resolved (exact lat/lng) — fastest and most accurate.
        Anchor preResolved = preResolvedAnchors.remove(coinIdx);
        if (preResolved != null) {
            nextSlotPreWarmPending.remove(coinIdx);
            preResolvedRenderables.remove(coinIdx);
            preResolvedRelicIds.remove(coinIdx);
            if (preResolved.getTrackingState() != TrackingState.STOPPED) {
                Log.d(TAG, "tryAutoSpawnCoins: using pre-resolved anchor for slot=" + coinIdx);
                spawnRelicNode(coinIdx, preResolved, coinRenderable, relicIdForThisCoin);
                return;
            }
            try { preResolved.detach(); } catch (Exception ignored) {}
        }

        // Live terrain anchor only when Earth is TRACKING with usable accuracy.
        boolean geoReady = false;
        if (isDeviceGeospatialCompatible() && isGeospatialAvailable()) {
            try {
                Session s2 = arCam.getArSceneView().getSession();
                Earth e2 = s2 != null ? s2.getEarth() : null;
                if (e2 != null && e2.getCameraGeospatialPose().getHorizontalAccuracy() <= 5.0) {
                    geoReady = true;
                }
            } catch (Exception ignored) {}
        }
        if (geoReady) {
            terrainAnchorPending.put(coinIdx, true);
            requestTerrainAnchorForSlot(coinIdx, coinRenderable, relicIdForThisCoin);
            return; // spawnRelicNode() fires from the terrain anchor callback
        }

        // Fallback: project the configured GPS coord from the user's current GPS
        // using device heading, anchor it in the AR session at the resulting
        // direction + distance. The relic appears "near" the configured coord —
        // not pixel-perfect without VPS, but reliably in the correct direction
        // and within walking range. Compass + HUD then track the actual anchor
        // pose (see updateCompassArrow / updateRelicHud), so the user can walk
        // straight to whatever lands and collect it.
        try {
            Session sess = arCam.getArSceneView().getSession();
            if (sess == null) return;

            // GPS bearing + distance from user to the configured coord.
            float[] geo = new float[2];
            Location.distanceBetween(lastUserLat, lastUserLng,
                    relicLatitudes[coinIdx], relicLongitudes[coinIdx], geo);
            float gpsDistance = geo[0];                  // metres
            float gpsBearingDeg = geo[1];                // 0=N, +clockwise, range -180..180

            // Heading the device believes is north. Geospatial heading (when
            // Earth is at least horizontal-tracking) is more stable than the
            // raw magnetometer; otherwise fall back to the smoothed compass.
            float headingDeg = !Float.isNaN(geospatialHeadingDeg)
                    ? geospatialHeadingDeg : smoothedAzimuthDeg;
            float relativeBearingDeg = gpsBearingDeg - headingDeg;
            double relRad = Math.toRadians(relativeBearingDeg);

            // Clamp distance: never spawn at the user's feet (< 2 m) and never
            // beyond a comfortable visual range (4 m) so the relic is always
            // visible AND reachable in tight courtyards / indoor spaces. The
            // user may be inside a building with a wall a few metres ahead;
            // capping at 4 m keeps the relic on the same side of typical walls.
            float dist = Math.max(2.0f, Math.min(gpsDistance, 4.0f));

            // Build a "yaw-only" pose at the camera's world position so the
            // relic stays at chest height regardless of how the user is
            // tilting the device.
            Pose camPose = frame.getCamera().getPose();
            float[] camT = camPose.getTranslation();
            float[] q = camPose.getRotationQuaternion(); // x,y,z,w
            double yaw = Math.atan2(2.0 * (q[3] * q[1] + q[0] * q[2]),
                    1.0 - 2.0 * (q[1] * q[1] + q[2] * q[2]));
            float[] yawQ = {0f, (float) Math.sin(yaw / 2.0), 0f, (float) Math.cos(yaw / 2.0)};
            Pose flatPose = new Pose(camT, yawQ);

            // Camera-local frame: +X right, +Y up, -Z forward. relativeBearing 0
            // = straight ahead, +90 = right, -90 = left.
            float dx = (float) (Math.sin(relRad) * dist);
            float dz = (float) (-Math.cos(relRad) * dist);
            // -1.3 m below the camera ≈ ground level (eye height ~1.6 m).
            Pose spawnPose = flatPose.compose(Pose.makeTranslation(dx, -1.3f, dz));
            Anchor anchor = sess.createAnchor(spawnPose);

            Log.e(TAG, "tryAutoSpawnCoins: directional spawn slot=" + coinIdx
                    + " gpsDist=" + gpsDistance + "m"
                    + " gpsBearing=" + gpsBearingDeg
                    + " heading=" + headingDeg
                    + " rel=" + relativeBearingDeg
                    + " usedDist=" + dist);
            spawnRelicNode(coinIdx, anchor, coinRenderable, relicIdForThisCoin);
        } catch (Exception e) {
            Log.w(TAG, "tryAutoSpawnCoins: directional spawn failed slot " + coinIdx
                    + ": " + e.getMessage());
        }
    }

    private void animateCoins() {
        coinRotationAngle = (coinRotationAngle + 1.2f) % 360f;

        for (int i = 0; i < coinNodes.size(); i++) {
            Node node = coinNodes.get(i);
            if (node == null || node.getParent() == null)
                continue;
            // Slow rotation only — no vertical movement so the item stays
            // at the exact world position where it was anchored.
            float angle = (coinRotationAngle + i * 36f) % 360f;
            node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), angle));
        }
    }

    private void collectCoin(int index, AnchorNode anchorNode, String relicIdOverride) {
        // Bounds + idempotency guards. We *intentionally* tolerate a null /
        // already-detached anchor here — what matters is that the per-slot
        // collected flag advances so the HUD/spawn loop can move on. The old
        // implementation returned early when getParent() was null, which left
        // the slot eternally "active" if any path detached the node first.
        if (index < 0 || coinSlotCollected == null
                || index >= coinSlotCollected.length) {
            Log.e(TAG, "collectCoin: ignoring out-of-range index=" + index);
            return;
        }
        if (coinSlotCollected[index]) {
            Log.e(TAG, "collectCoin: slot " + index + " already collected — ignoring duplicate tap");
            return;
        }
        Log.e(TAG, "collectCoin: collecting slot " + index + " relic=" + relicIdOverride);
        coinSlotCollected[index] = true;

        // Reset geospatial + terrain-anchor wait state for the next slot.
        geospatialWaitStartMs = 0;
        terrainAnchorPending.remove(index);

        // Record collection time — prevents the next relic from spawning instantly
        // in the same spot before the user sees this one disappear.
        lastCollectedTimeMs = System.currentTimeMillis();

        // Detach from scene + release the underlying ARCore anchor. Done in a
        // try/catch because either side may already be torn down.
        if (anchorNode != null) {
            try {
                anchorNode.setParent(null);
            } catch (Exception ignored) {
            }
            try {
                Anchor a = anchorNode.getAnchor();
                if (a != null)
                    a.detach();
            } catch (Exception ignored) {
            }
        }
        if (index < coinAnchorNodes.size())
            coinAnchorNodes.set(index, null);
        if (index < coinNodes.size())
            coinNodes.set(index, null);
        // Allow the spawn loop to immediately consider the next slot.
        coinsSpawned = false;

        coinsCollected++;
        coinCollectedValue += COIN_VALUE;
        int totalCoins = (relicLatitudes != null) ? relicLatitudes.length : 1;

        // Award the collectible item for this slot.
        // For staged-relic missions the per-slot relic id wins; otherwise
        // every coin credits the mission's single `collectibleId`.
        String idToAward = (relicIdOverride != null && !relicIdOverride.isEmpty())
                ? relicIdOverride
                : collectibleId;
        boolean firstSavedCollection = UserProgressStore.markRelicSlotCollected(
                this, missionId, index, totalCoins);
        if (firstSavedCollection && idToAward != null && !idToAward.isEmpty()) {
            UserProgressStore.incrementCollectibleCount(this, idToAward);
        }

        // Remove red dot from minimap
        if (minimap != null && index < coinMapMarkers.size()
                && coinMapMarkers.get(index) != null) {
            minimap.getOverlays().remove(coinMapMarkers.get(index));
            coinMapMarkers.set(index, null);
            runOnUiThread(() -> minimap.invalidate());
        }

        // Reveal the next relic’s red dot now that this one is collected.
        final int nextIdx = index + 1;
        if (relicLatitudes != null && nextIdx < relicLatitudes.length) {
            runOnUiThread(() -> showRelicDot(nextIdx));
        }

        // If this mission has multiple coins, only complete after the LAST one is
        // tapped.
        if (coinsCollected < totalCoins) {
            int remaining = totalCoins - coinsCollected;
            final String collectedName = (relicIdOverride != null)
                    ? displayNameForRelic(relicIdOverride)
                    : "Coin";
            // For staged missions, hint at the next relic so the user knows what to look
            // for.
            final String nextHint;
            if (relicIds != null && coinsCollected < relicIds.length) {
                nextHint = "Now find the " + displayNameForRelic(relicIds[coinsCollected]) + ".";
            } else {
                nextHint = "Find the next one!";
            }
            runOnUiThread(() -> {
                Toast.makeText(this,
                        collectedName + " collected! " + remaining + " more to go.",
                        Toast.LENGTH_SHORT).show();
                updateHint(collectedName + " " + coinsCollected + " of " + totalCoins
                        + " collected. " + nextHint);
            });
            return;
        }

        updateHint("Saving your progress...");
        boolean offline = !NetworkUtils.isConnected(this);

        onMissionModelPlaced(allComplete -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed())
                return;
            flushPendingCollectibleAwards();
            updateHint("Mission complete! Coin collected.");
            if (allComplete) {
                showNFTUnlockedDialog();
            } else {
                showMissionCompleteDialog(offline);
            }
        }), errorMessage -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed())
                return;
            updateHint("Couldn't save your progress. Please try again when online.");
            new AlertDialog.Builder(this)
                    .setTitle("Progress not saved")
                    .setMessage("We collected the coin, but couldn't record your mission:\n\n"
                            + errorMessage
                            + "\n\nPlease check your connection and tap 'Retry'.")
                    .setPositiveButton("Retry", (d, w) -> retryMissionCompletion())
                    .setNegativeButton("Close", null)
                    .show();
        }));
    }

    /** Standard per-mission completion dialog. Auto-returns to Home on dismiss. */
    private void showMissionCompleteDialog(boolean offline) {
        String body = String.format(Locale.US,
                "You collected the Intramuros Coin at %s!\n\nCoin value: ₱%.2f%s",
                missionName, coinCollectedValue,
                offline
                        ? "\n\nYou're offline — your progress will sync automatically."
                        : "");
        new AlertDialog.Builder(this)
                .setTitle("Congratulations!")
                .setMessage(body)
                .setPositiveButton("Return to Home", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    /** Retries the server-side completion write after a previous failure. */
    private void retryMissionCompletion() {
        updateHint("Saving your progress...");
        boolean offline = !NetworkUtils.isConnected(this);
        onMissionModelPlaced(allComplete -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed())
                return;
            flushPendingCollectibleAwards();
            updateHint("Mission complete! Progress synced.");
            if (allComplete) {
                showNFTUnlockedDialog();
            } else {
                showMissionCompleteDialog(offline);
            }
        }), errorMessage -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed())
                return;
            Toast.makeText(this, "Still can't sync: " + errorMessage, Toast.LENGTH_LONG).show();
        }));
    }

    // ──────────────────────────────────────────────────────────────────
    // Collectible award helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Kept for older completion paths. Relic awards are now committed at the
     * moment each slot is collected so partial runs survive app exits.
     */
    private void flushPendingCollectibleAwards() {
    }

    // ──────────────────────────────────────────────────────────────────
    // System check
    // ──────────────────────────────────────────────────────────────────

    public static boolean checkSystemSupport(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String glVer = ((ActivityManager) Objects.requireNonNull(
                    activity.getSystemService(Context.ACTIVITY_SERVICE)))
                    .getDeviceConfigurationInfo().getGlEsVersion();
            if (Double.parseDouble(glVer) >= 3.0)
                return true;
            Toast.makeText(activity, "App requires OpenGL ES 3.0 or later.",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "App requires Android 7.0 or later.",
                    Toast.LENGTH_SHORT).show();
        }
        activity.finish();
        return false;
    }
}
