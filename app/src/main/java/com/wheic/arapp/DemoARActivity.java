package com.wheic.arapp;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.concurrent.CompletableFuture;

public class DemoARActivity extends AppCompatActivity {

    private static final String TAG = "DemoARActivity";
    public static final String EXTRA_RELIC_ID = "relic_id";

    private static final String[] RELIC_IDS = {
        "intramuros_coin", "peineta", "salakot_elite", "farol_de_aceite", "pocket_watch"
    };
    private static final int[] RELIC_THUMBS = {
        R.drawable.render_coin, R.drawable.render_peineta, R.drawable.render_salakot,
        R.drawable.render_farol, R.drawable.render_pocket_watch
    };
    private static final String[] RELIC_NAMES = {
        "Coin", "Peineta", "Salakot", "Farol", "Pocket Watch"
    };

    private ArFragment arFragment;
    private String relicId;
    private CompletableFuture<ModelRenderable> modelFuture;
    private boolean hasPlaced = false;
    private boolean modelReady = false;
    private boolean planeFound = false;
    private boolean planeDetectionEnabled = false;

    private TextView tvHint;
    private TextView tvSelectedName;
    private LinearLayout carouselContainer;
    private final View[] carouselFrames = new View[5];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_ar);

        relicId = getIntent().getStringExtra(EXTRA_RELIC_ID);
        if (relicId == null) relicId = "intramuros_coin";

        tvHint         = findViewById(R.id.tvDemoHint);
        tvSelectedName = findViewById(R.id.tvSelectedName);
        carouselContainer = findViewById(R.id.carouselContainer);

        // Back button
        View btnBack = findViewById(R.id.btnDemoBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        buildCarousel();
        updateCarouselSelection(relicId);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.demoArFragment);

        if (arFragment != null) {
            arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
                if (planeFound) return;
                com.google.ar.core.Session session = arFragment.getArSceneView().getSession();
                if (session == null) return;
                if (!session.getAllTrackables(com.google.ar.core.Plane.class).isEmpty()) {
                    planeFound = true;
                    runOnUiThread(this::updateHint);
                }
            });
        }

        loadModel();
        setupArTap();
    }

    private void loadModel() {
        hasPlaced = false;
        modelReady = false;
        planeFound = false;
        updateHint();

        Integer rawResId = resourceForRelic(relicId);
        if (rawResId == null) rawResId = R.raw.intramuros_coin;

        modelFuture = ModelRenderable.builder()
                .setSource(this, rawResId)
                .setIsFilamentGltf(true)
                .build();

        modelFuture.thenRun(() -> {
            modelReady = true;
            runOnUiThread(this::updateHint);
        });
        modelFuture.exceptionally(t -> {
            Log.e(TAG, "Model load failed: " + t.getMessage());
            runOnUiThread(() -> Toast.makeText(this, "Failed to load model.", Toast.LENGTH_LONG).show());
            return null;
        });
    }

    private void updateHint() {
        if (modelReady && planeFound) {
            tvHint.setText("Tap the floor to place " + displayName(relicId));
        } else if (modelReady) {
            tvHint.setText("Point camera at floor\u2026 scanning for surface");
        } else if (planeFound) {
            tvHint.setText("Loading model\u2026");
        } else {
            tvHint.setText("Scanning for a surface\u2026 point camera at floor or table");
        }
    }

    private void setupArTap() {
        if (arFragment == null) return;
        arFragment.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
            if (hasPlaced) return;
            if (modelFuture == null || !modelFuture.isDone() || modelFuture.isCompletedExceptionally()) {
                Toast.makeText(this, "Model still loading\u2026 try again in a moment.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Anchor anchor = hitResult.createAnchor();
                ModelRenderable renderable = modelFuture.get();

                AnchorNode anchorNode = new AnchorNode(anchor);
                anchorNode.setParent(arFragment.getArSceneView().getScene());

                TransformableNode node = new TransformableNode(arFragment.getTransformationSystem());
                node.setParent(anchorNode);
                node.setRenderable(renderable);
                RelicModelProfile.Transform relicTransform = RelicModelProfile.transformFor(relicId);
                float s = relicTransform.visualScale;
                // Start demo at the same shared profile size used by mission mode.
                node.getScaleController().setMinScale(s);
                node.getScaleController().setMaxScale(s * 4.0f);
                node.setLocalScale(new Vector3(s, s, s));
                // Per-relic orientation and ground clearance
                node.setLocalRotation(rotationForRelic(relicId));
                node.setLocalPosition(new Vector3(0f, relicTransform.localYOffset, 0f));

                hasPlaced = true;
                tvHint.setText("Tap the model to spin it \u2022 pinch to resize \u2022 tap \u201cSwitch\u201d to try another");

            } catch (Exception e) {
                Log.e(TAG, "Place error", e);
                Toast.makeText(this, "Could not place model. Try tapping again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Carousel ─────────────────────────────────────────────────────

    private void buildCarousel() {
        if (carouselContainer == null) return;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < RELIC_IDS.length; i++) {
            final String id = RELIC_IDS[i];
            View item = inflater.inflate(R.layout.item_demo_carousel, carouselContainer, false);
            ImageView img = item.findViewById(R.id.imgCarouselItem);
            TextView lbl = item.findViewById(R.id.tvCarouselLabel);
            img.setImageResource(RELIC_THUMBS[i]);
            lbl.setText(RELIC_NAMES[i]);
            carouselFrames[i] = item.findViewById(R.id.imgCarouselItem);
            item.setOnClickListener(v -> {
                if (id.equals(relicId)) return; // already selected
                relicId = id;
                clearScene();
                loadModel();
                updateCarouselSelection(id);
            });
            carouselContainer.addView(item);
        }
    }

    private void updateCarouselSelection(String activeId) {
        if (tvSelectedName != null)
            tvSelectedName.setText(displayName(activeId));
        for (int i = 0; i < RELIC_IDS.length; i++) {
            if (carouselFrames[i] != null)
                carouselFrames[i].setActivated(RELIC_IDS[i].equals(activeId));
        }
    }

    // ── Scene helpers ────────────────────────────────────────────────

    private void clearScene() {
        if (arFragment == null) return;
        try {
            com.google.ar.sceneform.Scene scene = arFragment.getArSceneView().getScene();
            // Remove all anchor nodes (children of the scene root)
            for (com.google.ar.sceneform.Node child : new java.util.ArrayList<>(scene.getChildren())) {
                if (child instanceof AnchorNode) {
                    child.setParent(null);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "clearScene: " + e.getMessage());
        }
        hasPlaced = false;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Integer resourceForRelic(String id) {
        if (id == null) return null;
        switch (id) {
            case "intramuros_coin": return R.raw.intramuros_coin;
            case "peineta":         return R.raw.peineta;
            case "salakot_elite":   return R.raw.salakot_elite;
            case "farol_de_aceite": return R.raw.farol_de_aceite;
            case "pocket_watch":    return R.raw.pocket_watch;
            default: return null;
        }
    }

    private Quaternion rotationForRelic(String id) {
        return Quaternion.identity();
    }

    private String displayName(String id) {
        if (id == null) return "item";
        switch (id) {
            case "intramuros_coin": return "Intramuros Coin";
            case "peineta":         return "Peineta";
            case "salakot_elite":   return "Salakot";
            case "farol_de_aceite": return "Farol de Aceite";
            case "pocket_watch":    return "Antique Pocket Watch";
            default: return id;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
