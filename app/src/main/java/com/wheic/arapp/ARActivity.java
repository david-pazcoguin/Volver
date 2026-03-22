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
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ARActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "ARActivityTag";

    // ── AR ──────────────────────────────────────────────────────────
    private ArFragment arCam;
    private boolean hasPlacedModel = false;
    private CompletableFuture<ModelRenderable> modelFuture;

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

    // ── UI ──────────────────────────────────────────────────────────
    private TextView tvCharacterName;
    private TextView tvCharacterHint;

    // ── Periodic location check ──────────────────────────────────────
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private final Runnable locationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTargetReached) {
                getLocationAndCheckTarget();
            }
            locationHandler.postDelayed(this, LOCATION_CHECK_INTERVAL);
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

        username = getSharedPreferences("Volver", Context.MODE_PRIVATE)
                           .getString("username", "");

        // Bind character overlay views (defined in ar_activity.xml)
        tvCharacterName = findViewById(R.id.tvCharacterName);
        tvCharacterHint = findViewById(R.id.tvCharacterHint);

        arCam = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arCameraArea);
        ttsEngine = new TextToSpeech(this, this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        requestLocationPermission();
        preloadCharacterModel();
        setupTapListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureGeospatialMode();
        // Start periodic location check when screen is visible
        locationHandler.post(locationRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationHandler.removeCallbacks(locationRunnable);
        if (ttsEngine != null) ttsEngine.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsEngine != null) ttsEngine.shutdown();
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
            Session session = arCam != null ? arCam.getArSceneView().getSession() : null;
            Earth earth = session != null ? session.getEarth() : null;

            if (earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
                GeospatialPose cameraPose = earth.getCameraGeospatialPose();
                if (cameraPose.getHorizontalAccuracy() < 10f) {
                    float[] results = new float[1];
                    Location.distanceBetween(
                            cameraPose.getLatitude(), cameraPose.getLongitude(),
                            targetLatitude, targetLongitude, results);
                    float distance = results[0];

                    if (distance <= ACTIVATION_RADIUS_METERS) {
                        onTargetReached();
                    } else {
                        String msg = String.format("Move closer to %s. You are %dm away.",
                                characterName, (int) distance);
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                Toast.makeText(this, "Waiting for precise geospatial accuracy...", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Waiting for geospatial tracking...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fallback: keep legacy fused location check when geospatial is unavailable.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) return;

            float[] results = new float[1];
            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    targetLatitude, targetLongitude, results);
            float distance = results[0];

            if (distance <= ACTIVATION_RADIUS_METERS) {
                onTargetReached();
            } else {
                String msg = String.format("Move closer to %s. You are %dm away.",
                        characterName, (int) distance);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Called the first time the user enters the 50 m activation radius.
     * Automatically greets them with the character dialogue via TTS.
     */
    private void onTargetReached() {
        isTargetReached = true;

        // Show character name overlay
        if (tvCharacterName != null) {
            tvCharacterName.setText(characterName + " appears...");
            tvCharacterName.setVisibility(View.VISIBLE);
        }
        if (tvCharacterHint != null) {
            tvCharacterHint.setText("Tap a flat surface to place the character");
            tvCharacterHint.setVisibility(View.VISIBLE);
        }

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
            if (modelFuture.isDone() && !modelFuture.isCompletedExceptionally()) {
                try {
                    Anchor anchor = createMissionAnchor();
                    if (anchor == null) {
                        Toast.makeText(this, "Geospatial anchor is not ready yet.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    placeModel(anchor, modelFuture.get());
                    hasPlacedModel = true;
                    onMissionModelPlaced();
                } catch (Exception e) {
                    Toast.makeText(this, "Error placing model.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Model still loading — please wait.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configureGeospatialMode() {
        if (geospatialConfigured || arCam == null || arCam.getArSceneView() == null) {
            return;
        }

        Session session = arCam.getArSceneView().getSession();
        if (session == null || !isGeospatialAvailable()) {
            return;
        }

        Config config = session.getConfig();
        config.setGeospatialMode(Config.GeospatialMode.ENABLED);
        session.configure(config);
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
            return session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED);
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
        return earth.createAnchor(targetLatitude, targetLongitude, altitude, 0f, 0f, 0f, 1f);
    }

    private void placeModel(Anchor anchor, ModelRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arCam.getArSceneView().getScene());

        TransformableNode model = new TransformableNode(arCam.getTransformationSystem());
        model.setParent(anchorNode);
        model.setRenderable(renderable);
        model.setLocalScale(new Vector3(0.01f, 0.01f, 0.01f));
        model.select();

        // Tap the placed model to replay the character dialogue
        model.setOnTapListener((hitResult, node) -> speakText(characterDialogue));

        if (tvCharacterHint != null) {
            tvCharacterHint.setText("Tap " + characterName + " to hear the story again");
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

        MissionCompletionHelper.completeMission(this, username, missionId,
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
        MissionCompletionHelper.getMissionProgress(this, username,
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
        if (ttsEngine != null && ttsReady && text != null && !text.isEmpty()) {
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "character_dialogue");
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
