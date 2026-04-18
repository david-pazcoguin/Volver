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
import com.google.android.gms.location.LocationServices;
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
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
    private static final int  LOCATION_PERM_CODE       = 1001;
    private static final float ACTIVATION_RADIUS_METERS = 50.0f;
    private static final long  LOCATION_CHECK_INTERVAL  = 10_000L; // 10 seconds

    private double targetLatitude;
    private double targetLongitude;
    private double targetAltitude;
    private boolean isTargetReached = false;
    private boolean hasGreeted      = false; // play dialogue only once per visit
    private boolean geospatialConfigured = false;

    // ── Mission / character data ────────────────────────────────────
    private String missionId;
    private String characterName;
    private String characterDialogue;
    private String modelFileName;
    private String username;

    // ── TTS ─────────────────────────────────────────────────────────
    private TextToSpeech ttsEngine;
    private boolean ttsReady = false;

    // ── Coins ────────────────────────────────────────────────────────
    private CompletableFuture<ModelRenderable> coinModelFuture;
    private LinearLayout coinCounterLayout;
    private TextView tvCoinCount;

    // ── UI ──────────────────────────────────────────────────────────
    private TextView tvCharacterName;
    private TextView tvCharacterHint;
    private NavigationCompassView navigationCompass;

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
        setContentView(R.layout.ar_activity);

        if (!checkSystemSupport(this)) return;

        // Unpack intent extras
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            targetLatitude   = extras.getDouble("Latitude",  14.6495872);
            targetLongitude  = extras.getDouble("Longitude", 121.0032413);
            targetAltitude   = extras.getDouble("Altitude", Double.NaN);
            missionId        = extras.getString("MissionId",        "unknown");
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
        navigationCompass = findViewById(R.id.navigationCompass);
        navigationCompass.setTargetLocation(targetLatitude, targetLongitude);

        arCam = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arCameraArea);
        ttsEngine = new TextToSpeech(this, this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Configure geospatial mode as soon as the session is created
        if (arCam != null) {
            arCam.setOnSessionInitializationListener(this::onSessionCreated);
            // Subscribe to per-frame updates to retry geospatial config if needed
        }

        coinCounterLayout = findViewById(R.id.coinCounterLayout);
        tvCoinCount = findViewById(R.id.tvCoinCount);

        requestLocationPermission();
        preloadCharacterModel();
        preloadCoinModel();
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
        if (navigationCompass != null) navigationCompass.startSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationHandler.removeCallbacks(locationRunnable);
        if (navigationCompass != null) navigationCompass.stopSensors();
        if (arCam != null && arCam.getArSceneView() != null) {
            arCam.getArSceneView().getScene().removeOnUpdateListener(sceneUpdateListener);
        }
        if (ttsEngine != null) ttsEngine.stop();
    }

    @Override
    protected void onDestroy() {
        locationHandler.removeCallbacks(locationRunnable);
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

    private void preloadCoinModel() {
        int resId = getResources().getIdentifier("intramuros_coin", "raw", getPackageName());
        if (resId == 0) {
            Log.w(TAG, "intramuros_coin.glb not found — coins will not appear.");
            return;
        }
        coinModelFuture = ModelRenderable.builder()
                .setSource(this, resId)
                .setIsFilamentGltf(true)
                .build();
        coinModelFuture.exceptionally(t -> {
            Log.e(TAG, "Failed to load coin model: " + t.getMessage());
            coinModelFuture = null;
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
            getLocationAndCheckTarget();
        } else {
            Toast.makeText(this, "Location permission required to activate AR models.",
                    Toast.LENGTH_LONG).show();
        }
    }

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

                    if (navigationCompass != null) {
                        navigationCompass.updateCurrentLocation(
                                cameraPose.getLatitude(), cameraPose.getLongitude(), distance);
                    }
                    if (distance <= ACTIVATION_RADIUS_METERS) {
                        onTargetReached();
                    } else {
                        updateHint(String.format("Move closer to %s. You are %dm away.",
                                characterName, (int) distance));
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

            if (navigationCompass != null) {
                navigationCompass.updateCurrentLocation(
                        location.getLatitude(), location.getLongitude(), distance);
            }

            if (distance <= ACTIVATION_RADIUS_METERS) {
                onTargetReached();
            } else {
                updateHint(String.format("Move closer to %s. You are %dm away.",
                        characterName, (int) distance));
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

        // Hide navigation compass — user has arrived
        if (navigationCompass != null) {
            navigationCompass.setVisibility(View.GONE);
            navigationCompass.stopSensors();
        }

        // Show character name overlay
        if (tvCharacterName != null) {
            tvCharacterName.setText(characterName + " appears...");
            tvCharacterName.setVisibility(View.VISIBLE);
        }
        updateHint("Tap a flat surface to place the character");

        // Auto-speak the greeting (once per visit)
        if (!hasGreeted) {
            hasGreeted = true;
            if (ttsReady) {
                speakText(characterDialogue);
            }
            // If TTS isn't ready yet, onInit() will call speakText when it initialises
        }

        Toast.makeText(this, characterName + " has arrived! Tap a surface to place.",
                Toast.LENGTH_LONG).show();
    }

    private void updateHint(String text) {
        if (tvCharacterHint != null) {
            tvCharacterHint.setText(text);
            tvCharacterHint.setVisibility(View.VISIBLE);
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

        updateHint("Tap " + characterName + " to hear the story again");

        spawnCoins(anchorNode);
    }

    private void spawnCoins(AnchorNode anchorNode) {
        if (coinModelFuture == null || !coinModelFuture.isDone()
                || coinModelFuture.isCompletedExceptionally()) return;

        List<CoinManager.CoinSpawn> spawns = CoinManager.loadSpawns(this, missionId);
        Set<String> collectable = CoinManager.getCollectableToday(this, missionId, spawns);

        if (collectable.isEmpty()) {
            Toast.makeText(this, "All coins collected today! Come back tomorrow.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ModelRenderable coinRenderable = coinModelFuture.get();
            for (CoinManager.CoinSpawn spawn : spawns) {
                if (!collectable.contains(spawn.id)) continue;

                com.google.ar.sceneform.Node coinNode = new com.google.ar.sceneform.Node();
                coinNode.setParent(anchorNode);
                coinNode.setLocalPosition(new Vector3(spawn.x, spawn.y + 0.1f, spawn.z));
                coinNode.setLocalScale(new Vector3(0.05f, 0.05f, 0.05f));
                coinNode.setRenderable(coinRenderable);

                String coinId = spawn.id;
                coinNode.setOnTapListener((hit, node) -> collectCoin(coinNode, coinId));
            }

            showCoinCounter();
        } catch (Exception e) {
            Log.e(TAG, "Error spawning coins", e);
        }
    }

    private void collectCoin(com.google.ar.sceneform.Node coinNode, String coinId) {
        boolean collected = CoinManager.collectCoin(this, coinId);
        if (!collected) return;

        coinNode.setParent(null);
        int total = CoinManager.getTotalCoins(this);
        runOnUiThread(() -> {
            Toast.makeText(this, "Coin collected! Total: " + total, Toast.LENGTH_SHORT).show();
            updateCoinCounterUI(total);
        });
    }

    private void showCoinCounter() {
        if (coinCounterLayout == null) return;
        int total = CoinManager.getTotalCoins(this);
        updateCoinCounterUI(total);
        coinCounterLayout.setVisibility(android.view.View.VISIBLE);
    }

    private void updateCoinCounterUI(int total) {
        if (tvCoinCount != null) {
            tvCoinCount.setText(total + " coins");
        }
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
                .setMessage("You have visited all 6 sites. Return to the home screen to claim your Walled City Key NFT.")
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
