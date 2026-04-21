package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    ImageView imgDashboard;
    List<ARHelper> arHelpers;
    ARAdapter arAdapter;
    RecyclerView recyclerView;

    // Greeting
    private TextView tvFullName;

    // Mission progress UI
    TextView tvProgressLabel;
    CardView cardNFTClaim;
    TextView tvNFTClaimStatus;

    // Treasure chest UI
    private LinearLayout treasureChestContainer;
    private ImageView imgTreasureChest;
    private TextView tvTreasureCaption;
    private TextView tvTreasureHint;
    private boolean chestAnimatedIn = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingChestReveal;

    private static final String PREF_NFT_CLAIMED   = "nft_claimed";
    private static final String PREF_CHEST_DISMISS = "chest_dismissed";
    private static final long   CHEST_REVEAL_DELAY_MS = 1500L;

    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        SharedPreferences sh = SecurePrefs.get(this);
        username = sh.getString("username", "");

        imgDashboard     = findViewById(R.id.imgDashboard);
        recyclerView     = findViewById(R.id.recyclerView);
        tvFullName       = findViewById(R.id.tvFullName);
        tvProgressLabel  = findViewById(R.id.tvProgressLabel);
        cardNFTClaim     = findViewById(R.id.cardNFTClaim);
        tvNFTClaimStatus = findViewById(R.id.tvNFTClaimStatus);

        treasureChestContainer = findViewById(R.id.treasureChestContainer);
        imgTreasureChest       = findViewById(R.id.imgTreasureChest);
        tvTreasureCaption      = findViewById(R.id.tvTreasureCaption);
        tvTreasureHint         = findViewById(R.id.tvTreasureHint);

        imgDashboard.setOnClickListener(v -> showDashboard());

        treasureChestContainer.setOnClickListener(v -> onTreasureChestTapped());

        // Debug-only: long-press the greeting to auto-complete 4 of 5 missions
        // (everything except Casa Manila) so the treasure chest flow can be tested
        // without physically walking to each landmark.
        if (BuildConfig.DEBUG && tvFullName != null) {
            tvFullName.setOnLongClickListener(v -> {
                debugCompleteFourMissions();
                return true;
            });
        }

        buildMissionList();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGreeting();
        loadMissionProgress();
    }

    // ──────────────────────────────────────────────────────────────
    // Greeting
    // ──────────────────────────────────────────────────────────────

    private void loadGreeting() {
        if (tvFullName == null) return;

        // 1. Show cached name immediately so the UI never flashes "Hello User!"
        SharedPreferences sh = SecurePrefs.get(this);
        String cached = sh.getString("firstName", "");
        if (!cached.isEmpty()) {
            tvFullName.setText("Hello, " + capitalize(cached) + "!");
        }

        // 2. Fetch the latest first name from Firestore and cache it.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseConfig.getFirestore()
                .collection(FirebaseConfig.COLLECTION_USERS)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed() || doc == null || !doc.exists()) return;
                    String firstName = doc.getString("firstName");
                    if (firstName == null || firstName.trim().isEmpty()) return;
                    String trimmed = firstName.trim();
                    SecurePrefs.get(this).edit().putString("firstName", trimmed).apply();
                    if (tvFullName != null) {
                        tvFullName.setText("Hello, " + capitalize(trimmed) + "!");
                    }
                });
    }

    /** Uppercases the first letter of {@code name}; leaves the rest untouched. */
    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        char first = name.charAt(0);
        if (Character.isUpperCase(first)) return name;
        return Character.toUpperCase(first) + name.substring(1);
    }

    // ──────────────────────────────────────────────────────────────
    // Debug helpers (stripped from release builds)
    // ──────────────────────────────────────────────────────────────

    /**
     * Marks 4 of 5 missions complete (all except Casa Manila) for the signed-in
     * user. Triggered by long-pressing the greeting text in debug builds.
     */
    private void debugCompleteFourMissions() {
        if (!BuildConfig.DEBUG) return;
        Toast.makeText(this, "Debug: completing 4 missions…", Toast.LENGTH_SHORT).show();

        String[] ids = {"fort_santiago", "baluarte_san_diego", "museo_intramuros", "centro_turismo"};
        int[] remaining = { ids.length };

        for (String id : ids) {
            MissionCompletionHelper.completeMission(this, id,
                    new MissionCompletionHelper.CompletionCallback() {
                        @Override public void onSuccess() {
                            if (--remaining[0] == 0) runOnUiThread(() -> {
                                Toast.makeText(HomeActivity.this,
                                        "4 missions complete. Finish Casa Manila to unlock the chest!",
                                        Toast.LENGTH_LONG).show();
                                loadMissionProgress();
                            });
                        }
                        @Override public void onError(String message) {
                            if (--remaining[0] == 0) runOnUiThread(() -> loadMissionProgress());
                            runOnUiThread(() -> Toast.makeText(HomeActivity.this,
                                    "Debug mission write failed: " + message,
                                    Toast.LENGTH_SHORT).show());
                        }
                    });
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Mission list
    // ──────────────────────────────────────────────────────────────

    private void buildMissionList() {
        arHelpers = new ArrayList<>();

        arHelpers.add(new ARHelper(
                "Fort Santiago",
                "",
                14.594265, 120.970425,
                "fort_santiago",
                "José Rizal",
                "In this cell, my thoughts turned to freedom. I leave behind my last poem, " +
                "hidden within these walls. Seek it, and understand what we fought for.",
                "rizal_character"
        ));

        arHelpers.add(new ARHelper(
                "Baluarte de San Diego",
                "",
                14.585491, 120.975702,
                "baluarte_san_diego",
                "Antonio Sedeño",
                "From this tower, we watched the galleons approach across Manila Bay. " +
                "Help me raise these walls higher — the city's defence depends on us.",
                "sedeno_character"
        ));

        arHelpers.add(new ARHelper(
                "Casa Manila",
                "",
                14.589622, 120.975129,
                "casa_manila",
                "Imelda Marcos",
                "This home revives our bahay na bato legacy. Every room tells a story " +
                "of the merchant families who shaped colonial Manila. Let me show you.",
                "marcos_character"
        ));

        arHelpers.add(new ARHelper(
                "Museo de Intramuros",
                "",
                14.589853, 120.973438,
                "museo_intramuros",
                "Martin Tinio Jr.",
                "These stones whisper Manila's four-hundred-year saga. " +
                "Match the artifacts to their era and unlock the city's buried secrets.",
                "tinio_character"
        ));

        arHelpers.add(new ARHelper(
                "Centro de Turismo",
                "",
                14.590135, 120.973367,
                "centro_turismo",
                "St. Ignatius of Loyola",
                "From ruins, renewal rises. You have walked the length of the Walled City " +
                "and kept the flame of memory alive. The Intramuros Souvenir is yours.",
                "ignatius_character"
        ));
    }

    private void setupRecyclerView() {
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(arHelpers.size());
        arAdapter = new ARAdapter(arHelpers, this);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(arAdapter);
    }

    // ──────────────────────────────────────────────────────────────
    // Mission progress
    // ──────────────────────────────────────────────────────────────

    private void loadMissionProgress() {
        if (username.isEmpty()) return;

        MissionCompletionHelper.getMissionProgress(this,
                new MissionCompletionHelper.ProgressCallback() {
                    @Override
                    public void onResult(Set<String> completedIds, boolean allComplete) {
                        if (!isFinishing() && !isDestroyed()) {
                            runOnUiThread(() -> updateProgressUI(completedIds, allComplete));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isFinishing() && !isDestroyed()) {
                            runOnUiThread(() -> {
                                if (tvProgressLabel != null) {
                                    tvProgressLabel.setText("Unable to load progress — check your connection");
                                    tvProgressLabel.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    }
                });
    }

    private void updateProgressUI(Set<String> completedIds, boolean allComplete) {
        int total = arHelpers.size();
        int completedCount = completedIds != null ? completedIds.size() : 0;

        if (tvProgressLabel != null) {
            tvProgressLabel.setText("Missions: " + completedCount + " / " + total + " complete");
            tvProgressLabel.setVisibility(View.VISIBLE);
        }

        if (arAdapter != null) {
            arAdapter.setCompletedMissions(completedIds);
        }

        // Legacy card always stays hidden now (replaced by the treasure chest)
        if (cardNFTClaim != null) cardNFTClaim.setVisibility(View.GONE);

        updateTreasureChest(allComplete);
    }

    // ──────────────────────────────────────────────────────────────
    // Treasure chest reward
    // ──────────────────────────────────────────────────────────────

    private void updateTreasureChest(boolean allComplete) {
        if (treasureChestContainer == null) return;

        if (!allComplete) {
            cancelPendingChestReveal();
            treasureChestContainer.setVisibility(View.GONE);
            chestAnimatedIn = false;
            return;
        }

        SharedPreferences sh = SecurePrefs.get(this);
        boolean claimed   = sh.getBoolean(PREF_NFT_CLAIMED, false);

        if (claimed) {
            // Show open chest permanently — tap to view the minted NFT.
            imgTreasureChest.setImageResource(R.drawable.treasure_chest_open);
            tvTreasureCaption.setText("Your Intramuros Souvenir");
            tvTreasureHint.setText("Tap to view");
            revealChestIfNeeded();
        } else {
            // Show closed chest — tap to open & claim
            imgTreasureChest.setImageResource(R.drawable.treasure_chest_closed);
            tvTreasureCaption.setText("A treasure has appeared…");
            tvTreasureHint.setText("Tap to open");
            revealChestIfNeeded();
        }
    }

    private void revealChestIfNeeded() {
        if (chestAnimatedIn) {
            treasureChestContainer.setVisibility(View.VISIBLE);
            return;
        }
        cancelPendingChestReveal();
        // Keep hidden initially; reveal with a short delay for dramatic effect
        treasureChestContainer.setVisibility(View.INVISIBLE);
        pendingChestReveal = () -> {
            if (isFinishing() || isDestroyed() || treasureChestContainer == null) return;
            treasureChestContainer.setVisibility(View.VISIBLE);
            treasureChestContainer.setAlpha(0f);
            treasureChestContainer.setScaleX(0.6f);
            treasureChestContainer.setScaleY(0.6f);
            treasureChestContainer.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(this::startChestPulse)
                    .start();
            chestAnimatedIn = true;
        };
        uiHandler.postDelayed(pendingChestReveal, CHEST_REVEAL_DELAY_MS);
    }

    private void startChestPulse() {
        if (imgTreasureChest == null || isFinishing() || isDestroyed()) return;
        // Gentle pulse only for unopened chest
        SharedPreferences sh = SecurePrefs.get(this);
        if (sh.getBoolean(PREF_NFT_CLAIMED, false)) return;
        imgTreasureChest.animate()
                .scaleX(1.08f).scaleY(1.08f)
                .setDuration(700)
                .withEndAction(() -> {
                    if (imgTreasureChest == null) return;
                    imgTreasureChest.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(700)
                            .withEndAction(this::startChestPulse)
                            .start();
                })
                .start();
    }

    private void cancelPendingChestReveal() {
        if (pendingChestReveal != null) {
            uiHandler.removeCallbacks(pendingChestReveal);
            pendingChestReveal = null;
        }
    }

    private void onTreasureChestTapped() {
        SharedPreferences sh = SecurePrefs.get(this);
        boolean claimed = sh.getBoolean(PREF_NFT_CLAIMED, false);

        if (claimed) {
            // Already claimed — jump straight to the NFT view so the user can
            // re-open the PolygonScan / OpenSea links any time.
            launchNFTClaimFlow();
            return;
        }

        // Play open animation, then route to NFT claim
        treasureChestContainer.setClickable(false);
        imgTreasureChest.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(250)
                .withEndAction(() -> {
                    if (imgTreasureChest == null) return;
                    imgTreasureChest.setImageResource(R.drawable.treasure_chest_open);
                    tvTreasureCaption.setText("Your Intramuros Souvenir!");
                    tvTreasureHint.setText("Opening…");
                    imgTreasureChest.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(250)
                            .withEndAction(() -> uiHandler.postDelayed(this::launchNFTClaimFlow, 450))
                            .start();
                })
                .start();
    }

    private void launchNFTClaimFlow() {
        if (isFinishing() || isDestroyed()) return;
        WalletManager wm = WalletManager.getInstance(this);
        Intent intent = wm.hasWallet()
                ? new Intent(this, NFTClaimActivity.class)
                : new Intent(this, WalletSetupActivity.class);
        startActivity(intent);
        // Re-enable the container for subsequent taps after claim
        treasureChestContainer.setClickable(true);
    }

    @Override
    protected void onDestroy() {
        cancelPendingChestReveal();
        super.onDestroy();
    }

    // ──────────────────────────────────────────────────────────────
    // Dashboard bottom sheet
    // ──────────────────────────────────────────────────────────────

    private void showDashboard() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(R.layout.dashboard_layout);
        dialog.getWindow().setBackgroundDrawable(
                new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        WindowManager.LayoutParams wlp = dialog.getWindow().getAttributes();
        wlp.gravity = Gravity.BOTTOM;
        dialog.getWindow().setAttributes(wlp);

        dialog.findViewById(R.id.linearLayoutSetting).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingActivity.class));
        });
        dialog.findViewById(R.id.linearLayoutAboutUs).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutUsActivity.class));
        });

        dialog.show();
    }
}
