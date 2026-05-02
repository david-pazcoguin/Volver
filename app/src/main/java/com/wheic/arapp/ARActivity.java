package com.wheic.arapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
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

public class ARActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "ARActivityTag";

    // ── AR ──────────────────────────────────────────────────────────
    private ArFragment arCam;
    private boolean hasPlacedModel = false;
    private CompletableFuture<ModelRenderable> modelFuture;
    private boolean modelLoadFailed = false;
    private final Scene.OnUpdateListener sceneUpdateListener = this::onSceneUpdate;
    private int diagnosticFrameCount = 0;
    private boolean cameraStreamDiagLogged = false;

    // ── Location ────────────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private static final int LOCATION_PERM_CODE = 1001;
    private static final float ACTIVATION_RADIUS_METERS = 30.0f;
    private static final long LOCATION_CHECK_INTERVAL = 10_000L; // when searching for target
    private static final long LOCATION_CHECK_INTERVAL_IDLE = 30_000L; // after target reached

    private double targetLatitude;
    private double targetLongitude;
    private double targetAltitude;
    private boolean isTargetReached = false;

    // GPS positions for each coin (one entry per coin slot)
    private double[] coinLatitudes;
    private double[] coinLongitudes;
    // Optional: parallel array of relic IDs (one per coin slot). When non-null,
    // each coin spawns with that relic's GLB model and credits that relic on tap.
    // When null, every coin uses the default coin model and the mission's
    // single `collectibleId`. Used for the Casa Manila staged mission.
    private String[] coinRelicIds;

    // Last GPS fix — used to compute bearing toward the coin spot
    private double lastUserLat = Double.NaN;
    private double lastUserLng = Double.NaN;

    // Compass heading (degrees, 0 = north, clockwise) from SensorManager
    private float deviceAzimuthDeg = 0f;
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
            deviceAzimuthDeg = (float) Math.toDegrees(orientation[0]);
            if (deviceAzimuthDeg < 0)
                deviceAzimuthDeg += 360f;
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
    private boolean hasGreeted = false; // play dialogue only once per visit
    private boolean geospatialConfigured = false;

    // ── Mission / character data ────────────────────────────────────
    private String missionId;
    private String missionName; // location name e.g. "Fort Santiago"
    private String characterName;
    private String characterDialogue;
    private String modelFileName;
    private String collectibleId; // SharedPreferences key for awarding collectible items
    private String username;

    // ── TTS ─────────────────────────────────────────────────────────
    private TextToSpeech ttsEngine;
    private boolean ttsReady = false;

    // ── UI ──────────────────────────────────────────────────────────
    private View locateLabelContainer;
    private TextView tvLocateLabel; // mission name inside the pill
    private TextView tvLocateDistance; // distance line inside the pill
    private TextView tvCharacterName;
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
    // Per-relic GLB renderables for staged missions (e.g. Casa Manila).
    private final Map<String, CompletableFuture<ModelRenderable>> relicModelFutures = new HashMap<>();
    private final List<Marker> coinMapMarkers = new ArrayList<>();
    private final List<AnchorNode> coinAnchorNodes = new ArrayList<>();
    private final List<Node> coinNodes = new ArrayList<>();
    private boolean coinsSpawned = false;
    private int coinsCollected = 0;
    private float coinCollectedValue = 0f;
    private float coinRotationAngle = 0f;
    // Frames spent waiting for a floor plane before giving up and using fallback
    // height
    private int floorSearchFrames = 0;
    private static final int FLOOR_SEARCH_TIMEOUT_FRAMES = 180; // ~6 s @ 30 fps
    private static final float COIN_SCALE = 0.25f;
    private static final float COIN_VALUE = 0.10f;
    // Max distance (metres) from a relic at which the user can tap to collect it.
    // Kept large because urban-canyon GPS drift in Intramuros can be 5-10 m even
    // with a clean fix. The real proximity check is the spawn radius below — if
    // the relic is visible in AR, the user is close enough to collect it.
    private static final float RELIC_COLLECT_RADIUS_M = 25.0f;
    // Distance at which a staged relic spawns into the scene.
    private static final float RELIC_SPAWN_RADIUS_M = 15.0f;
    // Distance at which a staged relic is despawned again (hysteresis to avoid
    // flicker).
    private static final float RELIC_DESPAWN_RADIUS_M = 20.0f;

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
        double[] cl = extras.getDoubleArray("CoinLatitudes");
        double[] cn = extras.getDoubleArray("CoinLongitudes");
        coinLatitudes = (cl != null && cl.length > 0) ? cl : new double[] { targetLatitude };
        coinLongitudes = (cn != null && cn.length > 0) ? cn : new double[] { targetLongitude };
        String[] cri = extras.getStringArray("CoinRelicIds");
        if (cri != null && cri.length == coinLatitudes.length) {
            coinRelicIds = cri;
        } else {
            coinRelicIds = null;
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        missionId = extras.getString("MissionId", "unknown");
        missionName = extras.getString("MissionName", "Mission");
        characterName = extras.getString("CharacterName", "Guide");
        characterDialogue = extras.getString("CharacterDialogue", "Welcome.");
        modelFileName = extras.getString("ModelFileName", "san_bartolome_church");
        collectibleId = extras.getString("CollectibleId", missionId);

        // Validate coordinates
        if (targetLatitude < -90 || targetLatitude > 90
                || targetLongitude < -180 || targetLongitude > 180) {
            Toast.makeText(this, "Invalid mission coordinates.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        username = SecurePrefs.get(this).getString("username", "");

        // Bind character overlay views (defined in ar_activity.xml)
        locateLabelContainer = findViewById(R.id.locateLabelContainer);
        tvLocateLabel = findViewById(R.id.tvLocateLabel);
        tvLocateDistance = findViewById(R.id.tvLocateDistance);
        tvCharacterName = findViewById(R.id.tvCharacterName);
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

        ttsEngine = new TextToSpeech(this, this);
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

                lastUserLat = location.getLatitude();
                lastUserLng = location.getLongitude();

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
                    if (distance <= ACTIVATION_RADIUS_METERS) {
                        onTargetReached();
                    }
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
        preloadCharacterModel();
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
        if (tvCharacterName != null)
            tvCharacterName.bringToFront();
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
        if (ttsEngine != null)
            ttsEngine.stop();
    }

    @Override
    protected void onDestroy() {
        locationHandler.removeCallbacks(locationRunnable);
        if (navManager != null)
            navManager.destroy();
        if (ttsEngine != null) {
            ttsEngine.shutdown();
            ttsEngine = null;
        }
        super.onDestroy();
    }

    // ──────────────────────────────────────────────────────────────────
    // Session & scene callbacks
    // ──────────────────────────────────────────────────────────────────

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
        if (isTargetReached && !coinsSpawned) {
            tryAutoSpawnCoins();
        }
        if (isTargetReached) {
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

    /**
     * Tries to load the character-specific GLB (e.g. rizal_character.glb).
     * Falls back to san_bartolome_church.glb if the file is not in res/raw/ yet.
     *
     * To add a real model: place the .glb file in app/src/main/res/raw/
     * using the exact filename assigned in HomeActivity (e.g. rizal_character.glb).
     */
    private void preloadCharacterModel() {
        int resId = getResources().getIdentifier(modelFileName, "raw", getPackageName());
        if (resId == 0) {
            Log.w(TAG, "Model '" + modelFileName + "' not found — using fallback model.");
            resId = R.raw.san_bartolome_church;
        }

        modelFuture = ModelRenderable.builder()
                .setSource(this, resId)
                .setIsFilamentGltf(true)
                .build();

        modelFuture.exceptionally(throwable -> {
            Log.e(TAG, "Failed to load model: " + throwable.getMessage());
            modelLoadFailed = true;
            runOnUiThread(() -> Toast.makeText(this, "Failed to load 3D model. Please restart.",
                    Toast.LENGTH_LONG).show());
            return null;
        });
    }

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
                        lastUserLat = location.getLatitude();
                        lastUserLng = location.getLongitude();
                        if (!isTargetReached) {
                            Location.distanceBetween(
                                    location.getLatitude(), location.getLongitude(),
                                    targetLatitude, targetLongitude, distanceResults);
                            float distance = distanceResults[0];
                            updateDistanceUI(distance);
                            if (navManager != null) {
                                navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
                            }
                            if (distance <= ACTIVATION_RADIUS_METERS) {
                                onTargetReached();
                            }
                        }
                        updateMinimapUser();
                        updateRelicHud();
                    }
                })
                .addOnFailureListener(this, e -> Log.e(TAG, "getCurrentLocation failed: " + e.getMessage()));

        // Then keep updating continuously every 3 seconds
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
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
                    lastUserLat = cameraPose.getLatitude();
                    lastUserLng = cameraPose.getLongitude();
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

                    if (distance <= ACTIVATION_RADIUS_METERS) {
                        onTargetReached();
                    }
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

            lastUserLat = location.getLatitude();
            lastUserLng = location.getLongitude();

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

            if (distance <= ACTIVATION_RADIUS_METERS) {
                onTargetReached();
            }
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

        // Hide direction UI
        if (locateLabelContainer != null)
            locateLabelContainer.setVisibility(View.GONE);
        if (directionBanner != null)
            directionBanner.setVisibility(View.GONE);
        if (btnCompass != null)
            btnCompass.setVisibility(View.GONE);
        if (compassOverlay != null)
            compassOverlay.setVisibility(View.GONE);

        // Show minimap so player can see coin location
        if (minimapContainer != null)
            minimapContainer.setVisibility(View.VISIBLE);
        if (btnMapCompassToggle != null) {
            btnMapCompassToggle.setVisibility(View.VISIBLE);
            if (tvMapCompassIcon != null)
                tvMapCompassIcon.setText("🧭");
        }

        // Hide the legacy character-name banner permanently—its position
        // collides with the camera notch and the relic HUD already conveys
        // everything the user needs.
        if (tvCharacterName != null)
            tvCharacterName.setVisibility(View.GONE);
        updateHint("Find and tap the floating coin to complete the mission!");

        // Auto-speak the mission dialogue (once per visit)
        if (!hasGreeted) {
            hasGreeted = true;
            if (ttsReady)
                speakText(characterDialogue);
        }

        Toast.makeText(this, "You reached the mission! Find the floating coin!",
                Toast.LENGTH_LONG).show();
    }

    private void updateHint(String text) {
        if (tvCharacterHint != null) {
            tvCharacterHint.setText(text);
            tvCharacterHint.setVisibility(View.VISIBLE);
        }
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
        // arrived and relics are being hunted, aim at the current relic's GPS.
        // currentRelicSlot() returns 0 when coinAnchorNodes is empty (pre-spawn),
        // so we must guard with isTargetReached to avoid pointing at relic[0]
        // instead of the mission entrance during the approach.
        double aimLat = targetLatitude;
        double aimLng = targetLongitude;
        if (isTargetReached) {
            int slot = currentRelicSlot();
            if (slot >= 0 && coinLatitudes != null && slot < coinLatitudes.length) {
                aimLat = coinLatitudes[slot];
                aimLng = coinLongitudes[slot];
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
                : deviceAzimuthDeg;
        float relative = bearingDeg - headingDeg;
        compassArrow.setRotation(relative);

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
        if (arCam == null)
            return;

        // Scene-level fallback: a peek-touch listener fires on EVERY touch,
        // even ones that hit a node. Sceneform's per-node tap can miss small
        // or angled renderables — this guarantees a tap anywhere on the AR
        // view collects the currently-spawned relic.
        if (arCam.getArSceneView() != null && arCam.getArSceneView().getScene() != null) {
            arCam.getArSceneView().getScene().addOnPeekTouchListener((hitTestResult, motionEvent) -> {
                if (motionEvent.getAction() != MotionEvent.ACTION_UP)
                    return;
                int slot = currentRelicSlot();
                if (slot < 0 || slot >= coinAnchorNodes.size())
                    return;
                AnchorNode anchorNode = coinAnchorNodes.get(slot);
                if (anchorNode == null)
                    return;
                // Debounce: collectCoin nulls the anchor slot, so a duplicate
                // peek + node tap on the same frame is naturally guarded.
                String relicId = (coinRelicIds != null && slot < coinRelicIds.length)
                        ? coinRelicIds[slot]
                        : null;
                collectCoin(slot, anchorNode, relicId);
            });
        }

        arCam.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent event) -> {
            if (!isTargetReached) {
                Toast.makeText(this, "Reach the mission location first!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (hasPlacedModel) {
                Toast.makeText(this, "Character already placed.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (modelLoadFailed) {
                Toast.makeText(this, "Model failed to load. Please restart.", Toast.LENGTH_LONG).show();
                return;
            }
            if (modelFuture == null || !modelFuture.isDone() || modelFuture.isCompletedExceptionally()) {
                Toast.makeText(this, "Model still loading — please wait.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Try geospatial anchor first, fall back to plane hit-result anchor
                Anchor anchor = createMissionAnchor();
                if (anchor == null) {
                    anchor = hitResult.createAnchor();
                }
                placeModel(anchor, modelFuture.get());
                hasPlacedModel = true;
                onMissionModelPlaced();
            } catch (Exception e) {
                Log.e(TAG, "Error placing model", e);
                Toast.makeText(this, "Error placing model. Try tapping again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void configureGeospatialMode() {
        if (geospatialConfigured || arCam == null || arCam.getArSceneView() == null) {
            return;
        }

        Session session = arCam.getArSceneView().getSession();
        if (session == null) {
            return;
        }

        // Geospatial mode is disabled to prevent a native SIGABRT crash in
        // ConfigureRuntimeSensors on some devices (e.g. SM-A356E).
        // The app falls back to fused location for distance calculation.
        Log.w(TAG, "Geospatial mode disabled (sensor config crash workaround). Using fused location.");
        geospatialConfigured = true;
    }

    private boolean isGeospatialAvailable() {
        if (arCam == null || arCam.getArSceneView() == null) {
            return false;
        }

        Session session = arCam.getArSceneView().getSession();
        if (session == null) {
            return false;
        }

        try {
            return geospatialConfigured
                    && session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED);
        } catch (Exception e) {
            return false;
        }
    }

    private Anchor createMissionAnchor() {
        if (!isGeospatialAvailable()) {
            return null;
        }

        Session session = arCam.getArSceneView().getSession();
        Earth earth = session != null ? session.getEarth() : null;
        if (earth == null || earth.getTrackingState() != TrackingState.TRACKING) {
            return null;
        }

        GeospatialPose cameraPose = earth.getCameraGeospatialPose();
        if (cameraPose.getHorizontalAccuracy() >= 10f) {
            return null;
        }

        double altitude = Double.isNaN(targetAltitude) ? cameraPose.getAltitude() : targetAltitude;
        try {
            return earth.createAnchor(targetLatitude, targetLongitude, altitude, 0f, 0f, 0f, 1f);
        } catch (Exception e) {
            Log.w(TAG, "Failed to create geospatial anchor, will use plane anchor.", e);
            return null;
        }
    }

    private void placeModel(Anchor anchor, ModelRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arCam.getArSceneView().getScene());

        TransformableNode model = new TransformableNode(arCam.getTransformationSystem());
        model.setParent(anchorNode);
        model.setRenderable(renderable);
        model.setLocalScale(new Vector3(0.01f, 0.01f, 0.01f));
        model.select();

        model.setOnTapListener((hitResult, node) -> speakText(characterDialogue));

        updateHint("Tap the coin to hear the story. Collect the coin to complete this mission!");
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

    /** Fire-and-forget overload for the legacy plane-tap placement flow. */
    private void onMissionModelPlaced() {
        onMissionModelPlaced(null, null);
    }

    private void showNFTUnlockedDialog() {
        if (isFinishing() || isDestroyed())
            return;
        new AlertDialog.Builder(this)
                .setTitle("Intramuros Souvenir Complete!")
                .setMessage(
                        "You have visited all 5 sites. Return to the home screen to claim your Walled City Key NFT.")
                .setPositiveButton("Go to Home", (d, w) -> finish())
                .setNegativeButton("Stay in AR", null)
                .show();
    }

    // ──────────────────────────────────────────────────────────────────
    // TTS
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void onInit(int status) {
        if (isFinishing() || isDestroyed())
            return;
        if (status == TextToSpeech.SUCCESS) {
            ttsEngine.setLanguage(Locale.US);
            ttsReady = true;
            // If the user arrived at the location before TTS was ready, speak now
            if (isTargetReached && hasGreeted) {
                speakText(characterDialogue);
            }
        }
    }

    private void speakText(String text) {
        try {
            if (ttsEngine != null && ttsReady && text != null && !text.isEmpty()) {
                ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "character_dialogue");
            }
        } catch (Exception e) {
            // TTS may have been shutdown between the null-check and speak() call
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Coin model preload
    // ──────────────────────────────────────────────────────────────────

    private void preloadCoinModel() {
        coinModelFuture = ModelRenderable.builder()
                .setSource(this, R.raw.intramuros_coin)
                .setIsFilamentGltf(true)
                .build();
        coinModelFuture.exceptionally(t -> {
            Log.e(TAG, "Failed to load coin model: " + t.getMessage());
            return null;
        });

        // For staged-relic missions (e.g. Casa Manila), preload one renderable
        // per unique relic id so each spawn uses the correct GLB.
        if (coinRelicIds != null) {
            Set<String> unique = new HashSet<>();
            for (String id : coinRelicIds)
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
                fut.exceptionally(t -> {
                    Log.e(TAG, "Failed to load relic model " + relicId + ": " + t.getMessage());
                    return null;
                });
                relicModelFutures.put(relicId, fut);
            }
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
                return "Collectable";
            case "peineta":
                return "Peineta";
            case "salakot_elite":
                return "Salakot Elite";
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
        // indices, then show ONLY the first relic's red dot. Subsequent dots
        // are added in collectCoin() once the previous relic is collected.
        if (coinLatitudes != null && coinLongitudes != null) {
            for (int i = 0; i < coinLatitudes.length; i++)
                coinMapMarkers.add(null);
            if (coinLatitudes.length > 0) {
                showRelicDot(0);
            }
        }
    }

    /** Adds the red “relic” dot for the given index to the minimap. */
    private void showRelicDot(int index) {
        if (minimap == null)
            return;
        if (coinLatitudes == null || index < 0 || index >= coinLatitudes.length)
            return;
        if (index < coinMapMarkers.size() && coinMapMarkers.get(index) != null)
            return;
        Marker m = new Marker(minimap);
        m.setPosition(new GeoPoint(coinLatitudes[index], coinLongitudes[index]));
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
        boolean showingMap = minimapContainer.getVisibility() == View.VISIBLE;
        if (showingMap) {
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
        if (coinLatitudes == null || coinLatitudes.length == 0)
            return -1;
        if (!coinAnchorNodes.isEmpty()) {
            int lastIdx = coinAnchorNodes.size() - 1;
            if (coinAnchorNodes.get(lastIdx) != null)
                return lastIdx;
            if (coinAnchorNodes.size() < coinLatitudes.length)
                return coinAnchorNodes.size();
            return -1; // all collected
        }
        return 0;
    }

    private void updateRelicHud() {
        if (!isTargetReached)
            return;
        if (coinRelicIds == null || coinLatitudes == null)
            return;
        if (Double.isNaN(lastUserLat) || Double.isNaN(lastUserLng))
            return;

        int slot = currentRelicSlot();
        if (slot < 0 || slot >= coinLatitudes.length)
            return;

        String relicName = displayNameForRelic(coinRelicIds[slot]);
        float[] d = new float[1];
        Location.distanceBetween(lastUserLat, lastUserLng,
                coinLatitudes[slot], coinLongitudes[slot], d);
        int meters = Math.round(d[0]);
        int oneBased = slot + 1;
        String suffix;
        if (slot < coinAnchorNodes.size() && coinAnchorNodes.get(slot) != null) {
            suffix = "tap to collect";
        } else if (meters <= RELIC_SPAWN_RADIUS_M) {
            suffix = "spawning…";
        } else {
            suffix = "walk closer";
        }
        final String hint = ordinal(oneBased) + " " + relicName + " — " + meters + " m away · " + suffix;
        runOnUiThread(() -> updateHint(hint));
    }

    /**
     * Removes a staged relic from the scene/minimap if the user has walked beyond
     * the despawn radius, so it re-spawns when they return.
     */
    private void enforceRelicSpawnRadius() {
        if (coinRelicIds == null)
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
                coinLatitudes[lastIdx], coinLongitudes[lastIdx], d);
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
        floorSearchFrames = 0;
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
        if (coinModelFuture == null || !coinModelFuture.isDone()
                || coinModelFuture.isCompletedExceptionally())
            return;
        if (arCam == null || arCam.getArSceneView() == null)
            return;

        if (coinAnchorNodes.size() >= coinLatitudes.length) {
            coinsSpawned = true;
            return;
        }

        // Index of the coin we are about to place
        int coinIdx = coinAnchorNodes.size();

        // Staged-relic missions: only spawn the next coin AFTER the previous
        // one has been collected (its slot was nulled out in collectCoin()).
        // For legacy missions (no relic array) we keep the original behaviour
        // of spawning all coins as fast as plane detection allows.
        if (coinRelicIds != null && coinIdx > 0) {
            AnchorNode prev = coinAnchorNodes.get(coinIdx - 1);
            if (prev != null) {
                // Previous relic still on the scene — wait for it to be collected.
                return;
            }
        }

        // Staged relics only spawn when the user is within the spawn radius
        // of the target GPS spot, so the user has to walk to each relic.
        if (coinRelicIds != null
                && !Double.isNaN(lastUserLat) && !Double.isNaN(lastUserLng)
                && coinIdx < coinLatitudes.length) {
            float[] dGate = new float[1];
            Location.distanceBetween(lastUserLat, lastUserLng,
                    coinLatitudes[coinIdx], coinLongitudes[coinIdx], dGate);
            if (dGate[0] > RELIC_SPAWN_RADIUS_M) {
                return;
            }
        }

        // Pick the right renderable: per-relic for staged missions, else default.
        ModelRenderable coinRenderable = null;
        String relicIdForThisCoin = (coinRelicIds != null) ? coinRelicIds[coinIdx] : null;
        if (relicIdForThisCoin != null) {
            CompletableFuture<ModelRenderable> fut = relicModelFutures.get(relicIdForThisCoin);
            if (fut == null || !fut.isDone() || fut.isCompletedExceptionally()) {
                // Relic-specific model not loaded yet — fall back to coin while waiting.
                try {
                    coinRenderable = coinModelFuture.get();
                } catch (Exception e) {
                    return;
                }
            } else {
                try {
                    coinRenderable = fut.get();
                } catch (Exception e) {
                    return;
                }
            }
        } else {
            try {
                coinRenderable = coinModelFuture.get();
            } catch (Exception e) {
                return;
            }
        }
        if (coinRenderable == null)
            return;

        Frame frame = arCam.getArSceneView().getArFrame();
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING)
            return;

        Session session = arCam.getArSceneView().getSession();
        if (session == null)
            return;

        Pose cam = frame.getCamera().getPose();
        float cx = cam.tx(), cy = cam.ty(), cz = cam.tz();

        // ── Bearing-based coin placement ──────────────────────────────
        // Direction: from user's last GPS fix toward the coin's target GPS spot,
        // expressed in ARCore world space using the device compass heading.
        float dx, dz;
        if (!Double.isNaN(lastUserLat) && !Double.isNaN(lastUserLng)) {
            float[] bearingResult = new float[2];
            Location.distanceBetween(lastUserLat, lastUserLng,
                    coinLatitudes[coinIdx], coinLongitudes[coinIdx], bearingResult);
            float gpsBearing = bearingResult[1]; // degrees, clockwise from north
            // Use the same heading source the compass overlay uses so the
            // bearing-based fallback placement agrees with the on-screen arrow.
            float placementHeading = !Float.isNaN(geospatialHeadingDeg)
                    ? geospatialHeadingDeg
                    : deviceAzimuthDeg;
            float relativeRad = (float) Math.toRadians(gpsBearing - placementHeading);

            // Camera forward (-Z) and right (+X) in ARCore world space (horizontal plane)
            float[] zAxis = cam.getZAxis();
            float[] xAxis = cam.getXAxis();
            float fwdX = -zAxis[0], fwdZ = -zAxis[2];
            float rightX = xAxis[0], rightZ = xAxis[2];
            float fwdLen = (float) Math.sqrt(fwdX * fwdX + fwdZ * fwdZ);
            float rightLen = (float) Math.sqrt(rightX * rightX + rightZ * rightZ);
            if (fwdLen > 0.001f) {
                fwdX /= fwdLen;
                fwdZ /= fwdLen;
            }
            if (rightLen > 0.001f) {
                rightX /= rightLen;
                rightZ /= rightLen;
            }

            // Place coin at GPS distance (clamped 3–8 m) in the coin's direction
            float coinDist = Math.max(3f, Math.min(bearingResult[0], 8f));
            float cosA = (float) Math.cos(relativeRad);
            float sinA = (float) Math.sin(relativeRad);
            dx = fwdX * cosA * coinDist + rightX * sinA * coinDist;
            dz = fwdZ * cosA * coinDist + rightZ * sinA * coinDist;
        } else {
            // Fallback: place 5 m directly in front if GPS not yet available
            float[] zAxis = cam.getZAxis();
            dx = -zAxis[0] * 5f;
            dz = -zAxis[2] * 5f;
        }

        // ── Floor snapping ────────────────────────────────────────────
        // Cast a ray straight down from above the target XZ to find the
        // nearest tracked horizontal surface. This makes the coin sit on
        // whichever floor the user is standing on (ground floor, 5th
        // floor, etc.) because ARCore's plane detector only sees surfaces
        // around the user — it does not know about other storeys.
        float dy;
        try {
            float[] rayOrigin = new float[] { cx + dx, cy + 1.0f, cz + dz };
            float[] rayDirection = new float[] { 0f, -1f, 0f };
            List<HitResult> hits = frame.hitTest(rayOrigin, 0, rayDirection, 0);
            Float floorY = null;
            for (HitResult hit : hits) {
                com.google.ar.core.Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane) {
                    Plane plane = (Plane) trackable;
                    if (plane.getTrackingState() == TrackingState.TRACKING
                            && plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                            && plane.getSubsumedBy() == null) {
                        floorY = hit.getHitPose().ty();
                        break; // hits are returned in distance order — first match wins
                    }
                }
            }
            if (floorY != null) {
                // 5 cm above the detected floor so the coin doesn't z-fight with it
                dy = (floorY - cy) + 0.05f;
                floorSearchFrames = 0;
            } else if (floorSearchFrames++ < FLOOR_SEARCH_TIMEOUT_FRAMES) {
                // No floor detected yet — wait for plane detection rather than
                // spawning the coin floating awkwardly in mid-air.
                return;
            } else {
                // Timed out waiting for plane detection (user may not have panned
                // the camera at the floor). Fall back to a reasonable floor estimate.
                dy = -1.0f;
                Log.w(TAG, "Floor plane not detected within timeout; using fallback height");
            }
        } catch (Exception e) {
            Log.w(TAG, "Floor hitTest failed, using fallback height: " + e.getMessage());
            dy = -1.0f; // ~1 m below camera (approx. floor for a standing user)
        }

        try {
            // Prefer a precise GPS-locked Earth anchor at the EXACT coordinates
            // configured for this relic. This guarantees the coin never drifts
            // around as the user walks (which the bearing+distance fallback
            // does because magnetometer/fused-GPS jitter changes the math
            // every frame). The fallback below is only used when ARCore
            // Geospatial isn't yet tracking with sufficient accuracy.
            Anchor anchor = null;
            if (isGeospatialAvailable()) {
                Earth earth = session.getEarth();
                if (earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
                    GeospatialPose camPose = earth.getCameraGeospatialPose();
                    if (camPose.getHorizontalAccuracy() < 10f) {
                        // Floor altitude: use the detected floor's altitude when
                        // we have one, otherwise put the coin at camera-eye - 1m.
                        double anchorAltitude;
                        if (dy > -10f && Float.isFinite(dy)) {
                            // dy is offset from camera y; convert to absolute alt.
                            // camera altitude (from camPose) is approximately the
                            // user's eye height; (cy + dy) is the floor in AR-world,
                            // so altitude = camAlt + (dy - 0) since cy is camera y.
                            anchorAltitude = camPose.getAltitude() + dy;
                        } else {
                            anchorAltitude = camPose.getAltitude() - 1.5;
                        }
                        try {
                            anchor = earth.createAnchor(
                                    coinLatitudes[coinIdx],
                                    coinLongitudes[coinIdx],
                                    anchorAltitude,
                                    0f, 0f, 0f, 1f);
                        } catch (Exception ge) {
                            Log.w(TAG, "Earth.createAnchor for coin failed: " + ge.getMessage());
                            anchor = null;
                        }
                    }
                }
            }
            if (anchor == null) {
                // Geospatial is disabled on this device — use bearing-based placement
                // for all missions (staged and legacy alike).
                Pose coinPose = Pose.makeTranslation(cx + dx, cy + dy, cz + dz);
                anchor = session.createAnchor(coinPose);
            }

            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arCam.getArSceneView().getScene());

            Node coinNode = new Node();
            coinNode.setParent(anchorNode);
            coinNode.setRenderable(coinRenderable);
            coinNode.setLocalScale(new Vector3(COIN_SCALE, COIN_SCALE, COIN_SCALE));
            final int capturedIdx = coinIdx;
            final String capturedRelicId = relicIdForThisCoin;
            coinNode.setOnTapListener((hr, nd) -> {
                // No GPS gate on tap — if the relic is visible in AR, the user
                // is close enough. (Urban-canyon GPS drift can falsely report
                // 5-10 m even when the user is standing on top of the relic.)
                collectCoin(capturedIdx, anchorNode, capturedRelicId);
            });

            coinAnchorNodes.add(anchorNode);
            coinNodes.add(coinNode);

            // Minimap red dots are pre-populated in setupMinimap(), so no need
            // to add them here on spawn.

            // Banner / hint when this coin spawns.
            if (coinRelicIds != null) {
                // Staged-relic mission — name what they're hunting and how many remain.
                final String relicName = displayNameForRelic(capturedRelicId);
                final int totalRelics = coinLatitudes.length;
                final int slot = capturedIdx + 1; // 1-based for the user
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Find the " + relicName + " (" + slot + " of " + totalRelics + ")",
                            Toast.LENGTH_LONG).show();
                    updateHint("Find the " + relicName + " — tap it to collect.");
                    if (tvCharacterName != null)
                        tvCharacterName.setVisibility(View.GONE);
                });
            } else if (coinAnchorNodes.size() == 1) {
                // Legacy missions — show generic toast only on the first coin.
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            coinLatitudes.length > 1
                                    ? "Coins are floating nearby! Find and tap them!"
                                    : "A coin is floating nearby! Find and tap it!",
                            Toast.LENGTH_LONG).show();
                    updateHint(coinLatitudes.length > 1
                            ? "Find the floating coins and tap them to complete the mission!"
                            : "Find the floating coin and tap it to complete the mission!");
                    if (tvCharacterName != null)
                        tvCharacterName.setVisibility(View.GONE);
                });
            }

        } catch (Exception e) {
            Log.w(TAG, "Coin placement failed: " + e.getMessage());
        }
    }

    private void animateCoins() {
        coinRotationAngle = (coinRotationAngle + 1.2f) % 360f;
        float bobY = (float) (Math.sin(System.currentTimeMillis() / 700.0) * 0.06f);

        for (int i = 0; i < coinNodes.size(); i++) {
            Node node = coinNodes.get(i);
            if (node == null || node.getParent() == null)
                continue;
            // Each coin rotates at same speed but with a slight phase offset
            float angle = (coinRotationAngle + i * 36f) % 360f;
            node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), angle));
            // Bob upward only (coin sits on a floor — never dip below it)
            float bob = (float) ((Math.sin(System.currentTimeMillis() / 700.0 + i * 0.6) + 1.0) * 0.06f);
            node.setLocalPosition(new Vector3(0f, bob, 0f));
        }
    }

    private void collectCoin(int index, AnchorNode anchorNode, String relicIdOverride) {
        if (anchorNode == null || anchorNode.getParent() == null)
            return;

        // Detach from scene
        anchorNode.setParent(null);
        if (index < coinAnchorNodes.size())
            coinAnchorNodes.set(index, null);
        if (index < coinNodes.size())
            coinNodes.set(index, null);

        coinsCollected++;
        coinCollectedValue += COIN_VALUE;

        // Award the collectible item for this slot (capped at maxCount=10).
        // For staged-relic missions the per-slot relic id wins; otherwise
        // every coin credits the mission's single `collectibleId`.
        String idToAward = (relicIdOverride != null && !relicIdOverride.isEmpty())
                ? relicIdOverride
                : collectibleId;
        if (idToAward != null && !idToAward.isEmpty()) {
            android.content.SharedPreferences prefs = SecurePrefs.get(this);
            String key = "collectible_" + idToAward + "_count";
            int current = prefs.getInt(key, 0);
            if (current < 12) {
                prefs.edit().putInt(key, current + 1).apply();
            }
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
        if (coinLatitudes != null && nextIdx < coinLatitudes.length) {
            runOnUiThread(() -> showRelicDot(nextIdx));
        }

        // If this mission has multiple coins, only complete after the LAST one is
        // tapped.
        int totalCoins = (coinLatitudes != null) ? coinLatitudes.length : 1;
        if (coinsCollected < totalCoins) {
            int remaining = totalCoins - coinsCollected;
            final String collectedName = (relicIdOverride != null)
                    ? displayNameForRelic(relicIdOverride)
                    : "Coin";
            // For staged missions, hint at the next relic so the user knows what to look
            // for.
            final String nextHint;
            if (coinRelicIds != null && coinsCollected < coinRelicIds.length) {
                nextHint = "Now find the " + displayNameForRelic(coinRelicIds[coinsCollected]) + ".";
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

        // Optimistic UI — instant feedback, but the final dialog is deferred
        // until Firestore (including its offline queue) confirms the write.
        hasPlacedModel = true;
        updateHint("Saving your progress...");
        boolean offline = !NetworkUtils.isConnected(this);

        onMissionModelPlaced(allComplete -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed())
                return;
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
