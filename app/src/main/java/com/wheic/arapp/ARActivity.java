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
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
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
    private static final int  LOCATION_PERM_CODE       = 1001;
    private static final float ACTIVATION_RADIUS_METERS = 20.0f;
    private static final float OUT_OF_BOUNDS_RADIUS     = 50.0f;
    private static final long  LOCATION_CHECK_INTERVAL  = 10_000L; // 10 seconds
    private long lastOutOfBoundsWarnMs = 0;

    private double targetLatitude;
    private double targetLongitude;
    private double targetAltitude;
    private boolean isTargetReached = false;
    private boolean hasGreeted      = false; // play dialogue only once per visit
    private boolean geospatialConfigured = false;

    // ── Mission / character data ────────────────────────────────────
    private String missionId;
    private String missionName;   // location name e.g. "Fort Santiago"
    private String characterName;
    private String characterDialogue;
    private String modelFileName;
    private String username;

    // ── TTS ─────────────────────────────────────────────────────────
    private TextToSpeech ttsEngine;
    private boolean ttsReady = false;

    // ── UI ──────────────────────────────────────────────────────────
    private TextView tvCharacterName;
    private TextView tvCharacterHint;

    // ── Turn-by-turn directions ──────────────────────────────────────
    private NavigationDirectionManager navManager;
    private ARNavigationOverlay arNavOverlay;
    private LinearLayout directionBanner;
    private TextView tvDirectionIcon;
    private TextView tvDirectionText;
    private TextView tvDirectionDistance;
    private View btnToggleNav;
    private boolean useArOverlay = false;

    // ── Minimap ─────────────────────────────────────────────────────
    private MapView minimap;
    private View minimapContainer;
    private Marker userMarker;
    private Marker targetMarker;
    private Polyline routeOverlay;

    // ── Scattered coins ─────────────────────────────────────────────
    private CompletableFuture<ModelRenderable> coinModelFuture;
    private final List<Marker> coinMapMarkers = new ArrayList<>();
    private final List<AnchorNode> coinAnchorNodes = new ArrayList<>();
    private final List<Node> coinNodes = new ArrayList<>();
    private boolean coinsSpawned = false;
    private int coinsCollected = 0;
    private float coinCollectedValue = 0f;
    private float coinRotationAngle = 0f;
    private static final int TOTAL_COINS = 1;
    private static final float COIN_SCALE = 0.15f;
    private static final float COIN_VALUE = 0.10f;

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
        // osmdroid needs a user-agent before any MapView is inflated
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.ar_activity);

        if (!checkSystemSupport(this)) return;

        // Unpack intent extras
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            targetLatitude   = extras.getDouble("Latitude",  14.6495872);
            targetLongitude  = extras.getDouble("Longitude", 121.0032413);
            targetAltitude   = extras.getDouble("Altitude", Double.NaN);
            missionId        = extras.getString("MissionId",        "unknown");
            missionName      = extras.getString("MissionName",      "Mission");
            characterName    = extras.getString("CharacterName",    "Guide");
            characterDialogue= extras.getString("CharacterDialogue","Welcome.");
            modelFileName    = extras.getString("ModelFileName",    "san_bartolome_church");
        }

        // Validate coordinates
        if (targetLatitude < -90 || targetLatitude > 90
                || targetLongitude < -180 || targetLongitude > 180) {
            Toast.makeText(this, "Invalid mission coordinates.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        username = SecurePrefs.get(this).getString("username", "");

        // Bind character overlay views (defined in ar_activity.xml)
        tvCharacterName = findViewById(R.id.tvCharacterName);
        tvCharacterHint = findViewById(R.id.tvCharacterHint);

        // Turn-by-turn direction views
        arNavOverlay      = findViewById(R.id.arNavOverlay);
        directionBanner    = findViewById(R.id.directionBanner);
        tvDirectionIcon    = findViewById(R.id.tvDirectionIcon);
        tvDirectionText    = findViewById(R.id.tvDirectionText);
        tvDirectionDistance = findViewById(R.id.tvDirectionDistance);
        btnToggleNav       = findViewById(R.id.btnToggleNav);

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

        // Set AR overlay target (always points at final destination)
        if (arNavOverlay != null) {
            arNavOverlay.setTargetLocation(targetLatitude, targetLongitude, missionName);
        }

        // Toggle button: switch between text banner and AR compass overlay
        if (btnToggleNav != null) {
            btnToggleNav.setOnClickListener(v -> toggleNavMode());
        }

        // Minimap kept in layout but hidden (minimapContainer visibility=gone in XML)
        minimapContainer = findViewById(R.id.minimapContainer);
        minimap = findViewById(R.id.minimap);

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
                if (location == null) return;

                Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        targetLatitude, targetLongitude, distanceResults);
                float distance = distanceResults[0];

                if (!isTargetReached) {
                    // Feed navigation direction manager
                    if (navManager != null) {
                        navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
                    }
                    if (arNavOverlay != null) {
                        arNavOverlay.updateCurrentLocation(
                                location.getLatitude(), location.getLongitude(), distance);
                    }
                    if (distance <= ACTIVATION_RADIUS_METERS) {
                        onTargetReached();
                    }
                } else {
                    // Already in mission — warn if user wanders out of bounds
                    if (distance > OUT_OF_BOUNDS_RADIUS) {
                        warnOutOfBounds();
                    }
                }
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
        if (minimap != null) minimap.onResume();
        // Keep direction UI above the AR SurfaceView
        if (arNavOverlay != null) arNavOverlay.bringToFront();
        if (directionBanner != null) directionBanner.bringToFront();
        if (btnToggleNav != null) btnToggleNav.bringToFront();
        if (tvCharacterName != null) tvCharacterName.bringToFront();
        if (tvCharacterHint != null) tvCharacterHint.bringToFront();
        // Start AR overlay sensors if in AR mode
        if (arNavOverlay != null && useArOverlay) arNavOverlay.startSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationHandler.removeCallbacks(locationRunnable);
        stopLocationUpdates();
        if (minimap != null) minimap.onPause();
        if (arNavOverlay != null) arNavOverlay.stopSensors();
        if (arCam != null && arCam.getArSceneView() != null) {
            arCam.getArSceneView().getScene().removeOnUpdateListener(sceneUpdateListener);
        }
        if (ttsEngine != null) ttsEngine.stop();
    }

    @Override
    protected void onDestroy() {
        locationHandler.removeCallbacks(locationRunnable);
        if (navManager != null) navManager.destroy();
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
                if (f != null) cam = f.getCamera();
            } catch (Exception e) { /* ignore */ }
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
            runOnUiThread(() ->
                    Toast.makeText(this, "Failed to load 3D model. Please restart.",
                            Toast.LENGTH_LONG).show());
            return null;
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // Location
    // ──────────────────────────────────────────────────────────────────

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        // Force an immediate fresh fix so the compass is correct the moment the mission opens
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null && !isTargetReached) {
                        Location.distanceBetween(
                                location.getLatitude(), location.getLongitude(),
                                targetLatitude, targetLongitude, distanceResults);
                        float distance = distanceResults[0];
                        // Feed navigation direction manager
                        if (navManager != null) {
                            navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
                        }
                        if (arNavOverlay != null) {
                            arNavOverlay.updateCurrentLocation(
                                    location.getLatitude(), location.getLongitude(), distance);
                        }
                        if (distance <= ACTIVATION_RADIUS_METERS) {
                            onTargetReached();
                        }
                    }
                });

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
                    ? arCam.getArSceneView().getSession() : null;
            Earth earth = session != null ? session.getEarth() : null;

            if (earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
                GeospatialPose cameraPose = earth.getCameraGeospatialPose();
                if (cameraPose.getHorizontalAccuracy() < 10f) {
                    Location.distanceBetween(
                            cameraPose.getLatitude(), cameraPose.getLongitude(),
                            targetLatitude, targetLongitude, distanceResults);
                    float distance = distanceResults[0];

                    if (navManager != null) {
                        navManager.onLocationUpdate(cameraPose.getLatitude(), cameraPose.getLongitude());
                    }
                    if (arNavOverlay != null) {
                        arNavOverlay.updateCurrentLocation(
                                cameraPose.getLatitude(), cameraPose.getLongitude(), distance);
                    }

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) return;

            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    targetLatitude, targetLongitude, distanceResults);
            float distance = distanceResults[0];

            if (navManager != null) {
                navManager.onLocationUpdate(location.getLatitude(), location.getLongitude());
            }
            if (arNavOverlay != null) {
                arNavOverlay.updateCurrentLocation(
                        location.getLatitude(), location.getLongitude(), distance);
            }

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
        if (isTargetReached) return; // already triggered
        isTargetReached = true;

        // Hide all direction UI
        if (directionBanner != null) directionBanner.setVisibility(View.GONE);
        if (arNavOverlay != null) {
            arNavOverlay.setVisibility(View.GONE);
            arNavOverlay.stopSensors();
        }
        if (btnToggleNav != null) btnToggleNav.setVisibility(View.GONE);

        // Show minimap so player can see coin location
        if (minimapContainer != null) minimapContainer.setVisibility(View.VISIBLE);

        if (tvCharacterName != null) {
            tvCharacterName.setText("A coin is appearing nearby!");
            tvCharacterName.setVisibility(View.VISIBLE);
        }
        updateHint("Find and tap the floating coin to complete the mission!");

        // Auto-speak the mission dialogue (once per visit)
        if (!hasGreeted) {
            hasGreeted = true;
            if (ttsReady) speakText(characterDialogue);
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

    private void handleDirectionUpdate(NavigationDirectionManager.DirectionStep step) {
        if (isTargetReached || step == null) return;

        if (useArOverlay) {
            // AR overlay mode: show compass arrows + instruction in HUD pill
            if (arNavOverlay != null) {
                arNavOverlay.setCurrentInstruction(step.label, step.distanceText);
                arNavOverlay.setVisibility(View.VISIBLE);
            }
            if (directionBanner != null) directionBanner.setVisibility(View.GONE);
        } else {
            // Text banner mode
            if (tvDirectionIcon != null) tvDirectionIcon.setText(step.icon);
            if (tvDirectionText != null) tvDirectionText.setText(step.label);
            if (tvDirectionDistance != null) tvDirectionDistance.setText(step.distanceText);
            if (directionBanner != null) directionBanner.setVisibility(View.VISIBLE);
            if (arNavOverlay != null) arNavOverlay.setVisibility(View.GONE);
        }
    }

    private void toggleNavMode() {
        useArOverlay = !useArOverlay;

        if (useArOverlay) {
            if (arNavOverlay != null) {
                arNavOverlay.startSensors();
                arNavOverlay.setVisibility(View.VISIBLE);
            }
            if (directionBanner != null) directionBanner.setVisibility(View.GONE);
            if (btnToggleNav instanceof TextView) ((TextView) btnToggleNav).setText("📝");
        } else {
            if (arNavOverlay != null) {
                arNavOverlay.stopSensors();
                arNavOverlay.setVisibility(View.GONE);
            }
            if (directionBanner != null) directionBanner.setVisibility(View.VISIBLE);
            if (btnToggleNav instanceof TextView) ((TextView) btnToggleNav).setText("🧭");
        }

        // Refresh display with latest step
        if (navManager != null) {
            NavigationDirectionManager.DirectionStep lastStep = navManager.getLastStep();
            if (lastStep != null) handleDirectionUpdate(lastStep);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Tap-to-place
    // ──────────────────────────────────────────────────────────────────

    private void setupTapListener() {
        if (arCam == null) return;

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

        updateHint("Tap the coin to hear the story. Collect the 10 scattered coins!");
    }

    // ──────────────────────────────────────────────────────────────────
    // Mission completion
    // ──────────────────────────────────────────────────────────────────

    /**
     * Called once the character model is successfully placed.
     * Reports completion to the backend and checks whether all missions are done.
     */
    private void onMissionModelPlaced() {
        if (username.isEmpty() || missionId.equals("unknown")) return;

        MissionCompletionHelper.completeMission(this, missionId,
                new MissionCompletionHelper.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        checkForAllMissionsComplete();
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Mission completion sync failed: " + message);
                        // The user's progress is safe on the server for future syncs
                    }
                });
    }

    private void checkForAllMissionsComplete() {
        MissionCompletionHelper.getMissionProgress(this,
                new MissionCompletionHelper.ProgressCallback() {
                    @Override
                    public void onResult(java.util.Set<String> completedIds, boolean allComplete) {
                        if (allComplete) {
                            runOnUiThread(() -> showNFTUnlockedDialog());
                        }
                    }

                    @Override
                    public void onError(String message) { /* ignore */ }
                });
    }

    private void showNFTUnlockedDialog() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Intramuros Passport Complete!")
                .setMessage("You have visited all 5 sites. Return to the home screen to claim your Walled City Key NFT.")
                .setPositiveButton("Go to Home", (d, w) -> finish())
                .setNegativeButton("Stay in AR", null)
                .show();
    }

    // ──────────────────────────────────────────────────────────────────
    // TTS
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void onInit(int status) {
        if (isFinishing() || isDestroyed()) return;
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
    // Out-of-bounds warning
    // ──────────────────────────────────────────────────────────────────

    private void warnOutOfBounds() {
        long now = System.currentTimeMillis();
        if (now - lastOutOfBoundsWarnMs < 20_000L) return;
        lastOutOfBoundsWarnMs = now;
        Toast.makeText(this,
                "⚠️ You are leaving the mission area! Return to collect coins.",
                Toast.LENGTH_LONG).show();
        updateHint("⚠️ Out of bounds — come back to the mission area!");
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
    }

    // ──────────────────────────────────────────────────────────────────
    // Minimap setup
    // ──────────────────────────────────────────────────────────────────

    private void setupMinimap() {
        if (minimap == null) return;
        minimap.setTileSource(TileSourceFactory.MAPNIK);
        minimap.setMultiTouchControls(false);
        minimap.setClickable(false);
        minimap.getController().setZoom(18.5);
        minimap.getController().setCenter(new GeoPoint(targetLatitude, targetLongitude));

        // Blue dot for mission center
        targetMarker = new Marker(minimap);
        targetMarker.setPosition(new GeoPoint(targetLatitude, targetLongitude));
        targetMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        targetMarker.setIcon(createDotDrawable(Color.parseColor("#2196F3"), 28));
        targetMarker.setTitle("Mission");
        minimap.getOverlays().add(targetMarker);
    }

    private void addCoinMarkerToMinimap(GeoPoint point) {
        if (minimap == null) return;
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
                || coinModelFuture.isCompletedExceptionally()) return;
        if (arCam == null || arCam.getArSceneView() == null) return;

        if (coinAnchorNodes.size() >= TOTAL_COINS) {
            coinsSpawned = true;
            return;
        }

        Frame frame = arCam.getArSceneView().getArFrame();
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) return;

        Session session = arCam.getArSceneView().getSession();
        if (session == null) return;

        ModelRenderable coinRenderable;
        try {
            coinRenderable = coinModelFuture.get();
        } catch (Exception e) { return; }
        if (coinRenderable == null) return;

        Pose cam = frame.getCamera().getPose();
        float cx = cam.tx(), cy = cam.ty(), cz = cam.tz();

        // Random angle and random distance within mission radius (6–12 m)
        Random rng = new Random();
        float angleDeg = rng.nextFloat() * 360f;
        float radius   = 6f + rng.nextFloat() * 6f; // 6–12 m
        float dx = (float) (radius * Math.sin(Math.toRadians(angleDeg)));
        float dz = (float) (-radius * Math.cos(Math.toRadians(angleDeg)));
        float dy = -0.3f; // chest height

        try {
            Pose coinPose = Pose.makeTranslation(cx + dx, cy + dy, cz + dz);
            Anchor anchor = session.createAnchor(coinPose);

            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arCam.getArSceneView().getScene());

            Node coinNode = new Node();
            coinNode.setParent(anchorNode);
            coinNode.setRenderable(coinRenderable);
            coinNode.setLocalScale(new Vector3(COIN_SCALE, COIN_SCALE, COIN_SCALE));
            coinNode.setOnTapListener((hr, nd) -> collectCoin(0, anchorNode));

            coinAnchorNodes.add(anchorNode);
            coinNodes.add(coinNode);

            // Minimap red dot
            double latOff = dz / 111111.0;
            double lngOff = dx / (111111.0 * Math.cos(Math.toRadians(targetLatitude)));
            GeoPoint gp = new GeoPoint(targetLatitude + latOff, targetLongitude + lngOff);
            runOnUiThread(() -> addCoinMarkerToMinimap(gp));

            coinsSpawned = true;
            runOnUiThread(() -> {
                Toast.makeText(this,
                        "A coin is floating nearby! Find and tap it!",
                        Toast.LENGTH_LONG).show();
                updateHint("Find the floating coin and tap it to complete the mission!");
                if (tvCharacterName != null) tvCharacterName.setVisibility(View.GONE);
            });

        } catch (Exception e) {
            Log.w(TAG, "Coin placement failed: " + e.getMessage());
        }
    }

    private void animateCoins() {
        coinRotationAngle = (coinRotationAngle + 1.2f) % 360f;
        float bobY = (float) (Math.sin(System.currentTimeMillis() / 700.0) * 0.06f);

        for (int i = 0; i < coinNodes.size(); i++) {
            Node node = coinNodes.get(i);
            if (node == null || node.getParent() == null) continue;
            // Each coin rotates at same speed but with a slight phase offset
            float angle = (coinRotationAngle + i * 36f) % 360f;
            node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), angle));
            // Bob up and down with individual phase offset
            float bob = (float) (Math.sin(System.currentTimeMillis() / 700.0 + i * 0.6) * 0.06f);
            node.setLocalPosition(new Vector3(0f, bob, 0f));
        }
    }

    private void collectCoin(int index, AnchorNode anchorNode) {
        if (anchorNode == null || anchorNode.getParent() == null) return;

        // Detach from scene
        anchorNode.setParent(null);
        if (index < coinAnchorNodes.size()) coinAnchorNodes.set(index, null);
        if (index < coinNodes.size())       coinNodes.set(index, null);

        coinsCollected++;
        coinCollectedValue += COIN_VALUE;

        // Remove red dot from minimap
        if (minimap != null && index < coinMapMarkers.size()
                && coinMapMarkers.get(index) != null) {
            minimap.getOverlays().remove(coinMapMarkers.get(index));
            coinMapMarkers.set(index, null);
            runOnUiThread(() -> minimap.invalidate());
        }

        // Mark mission complete and show congratulation dialog
        hasPlacedModel = true;
        onMissionModelPlaced();

        runOnUiThread(() -> {
            updateHint("Mission complete! Coin collected.");
            if (!isFinishing() && !isDestroyed()) {
                new AlertDialog.Builder(this)
                        .setTitle("Congratulations!")
                        .setMessage(String.format(Locale.US,
                                "You collected the Intramuros Coin at %s!\n\nCoin value: ₱%.2f",
                                missionName, coinCollectedValue))
                        .setPositiveButton("Awesome!", (d, w) -> d.dismiss())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────
    // System check
    // ──────────────────────────────────────────────────────────────────

    public static boolean checkSystemSupport(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String glVer = ((ActivityManager) Objects.requireNonNull(
                    activity.getSystemService(Context.ACTIVITY_SERVICE)))
                    .getDeviceConfigurationInfo().getGlEsVersion();
            if (Double.parseDouble(glVer) >= 3.0) return true;
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
