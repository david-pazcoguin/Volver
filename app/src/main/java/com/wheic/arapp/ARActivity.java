    package com.wheic.arapp;

    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.cardview.widget.CardView;
    import androidx.core.app.ActivityCompat;
    import androidx.core.content.ContextCompat;

    import android.Manifest;
    import android.app.Activity;
    import android.app.ActivityManager;
    import android.app.AlertDialog;
    import android.content.Context;
    import android.content.pm.PackageManager;
    import android.location.Address;
    import android.location.Geocoder;
    import android.location.Location;
    import android.os.Build;
    import android.os.Bundle;
    import android.speech.tts.TextToSpeech;
    import android.util.Log;
    import android.view.MotionEvent;
    import android.view.View;
    import android.widget.Toast;

    import com.google.android.gms.location.FusedLocationProviderClient;
    import com.google.android.gms.location.LocationServices;
    import com.google.android.gms.tasks.OnSuccessListener;
    import com.google.ar.core.Anchor;
    import com.google.ar.core.Config;
    import com.google.ar.core.HitResult; // Added for Tap to Place
    import com.google.ar.core.Plane; // Added for Tap to Place
    import com.google.ar.core.Frame;
    import com.google.ar.core.Session;
    // Tinanggal: AugmentedImage, AugmentedImageDatabase, TrackingState

    import com.google.ar.sceneform.AnchorNode;
    import com.google.ar.sceneform.FrameTime;
    import com.google.ar.sceneform.math.Vector3;
    import com.google.ar.sceneform.rendering.ModelRenderable;
    import com.google.ar.sceneform.ux.ArFragment; // BINAGO! Ginagamit na natin ang standard ArFragment

    import com.google.ar.sceneform.ux.TransformableNode;

    import java.io.IOException;
    import java.io.InputStream;
    import java.util.List;
    import java.util.Locale;
    import java.util.Objects;
    import java.util.concurrent.CompletableFuture; // Added for Model Loading

    public class ARActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

        private static final String TAG = "ARActivityTag";
        private ArFragment arCam;
        private boolean hasPlacedModel = false;

        private FusedLocationProviderClient fusedLocationClient;
        private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

        double TARGET_LATITUDE = 14.6495872;
        double TARGET_LONGITUDE = 121.0032413;
        private static final float ACTIVATION_RADIUS_METERS = 50.0f;
        private boolean isTargetReached = false;

        private TextToSpeech textToSpeechEngine;
        String description = "";

        private CompletableFuture<ModelRenderable> modelFuture;


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.ar_activity);


            if (!checkSystemSupport(this)) {
                return;
            }

            arCam = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arCameraArea);

            textToSpeechEngine = new TextToSpeech(this, this);

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            requestLocationPermission();

            modelFuture = ModelRenderable.builder()
                    .setSource(this, R.raw.san_bartolome_church)
                    .setIsFilamentGltf(true)
                    .build();

            setupTapListener();
        }

        private void requestLocationPermission() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                getLocationAndCheckTarget();
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocationAndCheckTarget();
                } else {
                    Toast.makeText(this, "Location permission is required to activate AR models!", Toast.LENGTH_LONG).show();
                }
            }
        }

        private void getLocationAndCheckTarget() {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location)
                        {
                            String currentAddress = getAddressFromLocation(location.getLatitude(), location.getLongitude());
                            if (location != null) {

                                Toast.makeText(ARActivity.this, "Current Location: " + currentAddress, Toast.LENGTH_LONG).show();

                                float[] results = new float[1];
                                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                                        TARGET_LATITUDE, TARGET_LONGITUDE, results);
                                float distanceInMeters = results[0];

                                if (distanceInMeters <= ACTIVATION_RADIUS_METERS) {
                                    isTargetReached = true;
                                    Toast.makeText(ARActivity.this, "Target Location Reached! Tap on a flat surface to place the model.", Toast.LENGTH_LONG).show();
                                } else {
                                    isTargetReached = false;
                                    String message = String.format("Too far. Move closer to the target location. You are %d meters away.", (int) distanceInMeters);
                                    Toast.makeText(ARActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Log.w(TAG, "onSuccess: Last known location is null.");
                                Toast.makeText(ARActivity.this, "Current Location: " + currentAddress, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        }

        private String getAddressFromLocation(double latitude, double longitude) {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            String addressText = "Address not found";

            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        sb.append(address.getAddressLine(i));
                        if (i < address.getMaxAddressLineIndex()) {
                            sb.append(", ");
                        }
                    }
                    addressText = sb.toString();
                }
            } catch (IOException e) {
                addressText = "Geocoder failed (Network Error)";
            } catch (IllegalArgumentException e) {
            }
            return addressText;
        }

        private void setupTapListener() {
            if (arCam == null) return;

            arCam.setOnTapArPlaneListener(
                    (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                        if (!isTargetReached) {
                            Toast.makeText(this, "You must reach the target location first!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (hasPlacedModel) {
                            Toast.makeText(this, "Model is already placed.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (modelFuture.isDone() && !modelFuture.isCompletedExceptionally()) {
                            try {
                                Anchor anchor = hitResult.createAnchor();
                                addModel(anchor, modelFuture.get());
                                hasPlacedModel = true;
                            } catch (Exception e) {
                                Toast.makeText(this, "Error placing model.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(this, "Model is still loading, please wait.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        private void addModel(Anchor anchor, ModelRenderable modelRenderable) {
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arCam.getArSceneView().getScene());
            TransformableNode model = new TransformableNode(arCam.getTransformationSystem());
            model.setParent(anchorNode);
            model.setRenderable(modelRenderable);

            float desiredScale = 0.01f;
            model.setLocalScale(new Vector3(desiredScale, desiredScale, desiredScale));
            Toast.makeText(this, "Model Placed! Tap the model to hear description.", Toast.LENGTH_SHORT).show();
            model.select();

            model.setOnTapListener((hitResult, node) -> {
                speakDescription(description);
            });
        }
        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeechEngine.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                } else {
                }
            } else {
            }
        }

        private void speakDescription(String text) {
            if (textToSpeechEngine != null && !text.isEmpty()) {
                textToSpeechEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "model_description_id");
            }
        }

        @Override
        protected void onPause() {
            super.onPause();
            if (textToSpeechEngine != null) {
                textToSpeechEngine.stop();
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (textToSpeechEngine != null) {
                textToSpeechEngine.shutdown();
            }
        }
        public static boolean checkSystemSupport(Activity activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String openGlVersion = ((ActivityManager) Objects.requireNonNull(activity.getSystemService(Context.ACTIVITY_SERVICE))).getDeviceConfigurationInfo().getGlEsVersion();
                if (Double.parseDouble(openGlVersion) >= 3.0) {
                    return true;
                } else {
                    Toast.makeText(activity, "App needs OpenGl Version 3.0 or later", Toast.LENGTH_SHORT).show();
                    activity.finish();
                    return false;
                }
            } else {
                Toast.makeText(activity, "App does not support required Build Version", Toast.LENGTH_SHORT).show();
                activity.finish();
                return false;
            }
        }
    }