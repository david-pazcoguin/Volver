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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    // ── Missions tab ────────────────────────────────────
    ImageView imgDashboard;
    List<ARHelper> arHelpers;
    ARAdapter arAdapter;
    RecyclerView recyclerView;
    private TextView tvFullName;
    TextView tvProgressLabel;
    CardView cardNFTClaim;
    TextView tvNFTClaimStatus;
    private LinearLayout treasureChestContainer;
    private ImageView imgTreasureChest;
    private TextView tvTreasureCaption;
    private TextView tvTreasureHint;
    private boolean chestAnimatedIn = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingChestReveal;

    // ── Collectibles tab ────────────────────────────────
    private LinearLayout layoutMissions;
    private LinearLayout layoutCollectibles;
    private RecyclerView recyclerCollectibles;
    private CollectiblesAdapter collectiblesAdapter;
    private List<CollectibleItem> collectibleItems;
    private TextView tvCollectiblesTotal;
    private TextView tvTotalBadge;

    // ── Nav ─────────────────────────────────────────────
    private BottomNavigationView bottomNav;

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

        layoutMissions     = findViewById(R.id.layoutMissions);
        layoutCollectibles = findViewById(R.id.layoutCollectibles);
        recyclerCollectibles = findViewById(R.id.recyclerCollectibles);
        tvCollectiblesTotal  = findViewById(R.id.tvCollectiblesTotal);
        tvTotalBadge         = findViewById(R.id.tvTotalBadge);
        bottomNav            = findViewById(R.id.bottomNav);

        imgDashboard.setOnClickListener(v -> showDashboard());
        treasureChestContainer.setOnClickListener(v -> onTreasureChestTapped());

        if (BuildConfig.DEBUG && tvFullName != null) {
            tvFullName.setOnLongClickListener(v -> {
                debugCompleteAllMissions();
                return true;
            });
        }

        buildMissionList();
        setupRecyclerView();
        buildCollectiblesList();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGreeting();
        loadMissionProgress();
        refreshCollectibleCounts();
    }

    // ──────────────────────────────────────────────────────────────
    // Bottom navigation
    // ──────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_missions) {
                layoutMissions.setVisibility(View.VISIBLE);
                layoutCollectibles.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_collectibles) {
                layoutMissions.setVisibility(View.GONE);
                layoutCollectibles.setVisibility(View.VISIBLE);
                refreshCollectibleCounts();
                return true;
            }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_missions);
    }

    // ──────────────────────────────────────────────────────────────
    // Collectibles
    // ──────────────────────────────────────────────────────────────

    private void buildCollectiblesList() {
        collectibleItems = new ArrayList<>();
        collectibleItems.add(new CollectibleItem(
                "intramuros_coin",
                "Intramuros Coin",
                "An 8-reales (peso) silver coin minted during the Spanish Colonial period. " +
                "The currency carried by merchants and Ilustrados through the gates of Intramuros.",
                R.drawable.ic_coin, 0, 2));

        collectibleItems.add(new CollectibleItem(
                "peineta",
                "Peineta",
                "An ornate Spanish hair comb worn by Filipina women during the colonial era. " +
                "A symbol of elegance, identity, and the blending of cultures.",
                R.drawable.ic_peineta, 0, 2));

        collectibleItems.add(new CollectibleItem(
                "salakot_elite",
                "Salakot Elite",
                "A ceremonial salakot adorned with fine gold engravings, worn by the principalia " +
                "during official colonial gatherings and religious processions.",
                R.drawable.ic_salakot, 0, 2));

        collectibleItems.add(new CollectibleItem(
                "farol_de_aceite",
                "Farol de Aceite",
                "An oil lantern that lit the cobblestone streets of Intramuros for centuries. " +
                "Its warm glow guided merchants, soldiers, and friars through the Walled City.",
                R.drawable.ic_lantern, 0, 2));

        collectibleItems.add(new CollectibleItem(
                "pocket_watch",
                "Antique Pocket Watch",
                "A tarnished brass pocket watch with Roman numerals and a matching chain. " +
                "The signature accessory of an educated Ilustrado gentleman.",
                R.drawable.ic_pocket_watch, 0, 2));

        collectiblesAdapter = new CollectiblesAdapter(collectibleItems);
        recyclerCollectibles.setHasFixedSize(false);
        recyclerCollectibles.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerCollectibles.setAdapter(collectiblesAdapter);

        refreshCollectibleCounts();
    }

    private void refreshCollectibleCounts() {
        if (collectibleItems == null || collectiblesAdapter == null) return;
        SharedPreferences sh = SecurePrefs.get(this);
        int total = 0;
        for (CollectibleItem item : collectibleItems) {
            int count = sh.getInt("collectible_" + item.getId() + "_count", 0);
            item.setCount(count);
            total += count;
        }
        collectiblesAdapter.notifyDataSetChanged();

        int maxTotal = collectibleItems.size() * 2;
        if (tvCollectiblesTotal != null)
            tvCollectiblesTotal.setText(total + " / " + maxTotal + " collected");
        if (tvTotalBadge != null)
            tvTotalBadge.setText(total + "/" + maxTotal);
    }

    // ──────────────────────────────────────────────────────────────
    // Greeting
    // ──────────────────────────────────────────────────────────────

    private void loadGreeting() {
        if (tvFullName == null) return;
        SharedPreferences sh = SecurePrefs.get(this);
        String cached = sh.getString("firstName", "");
        if (!cached.isEmpty()) tvFullName.setText("Hello, " + capitalize(cached) + "!");

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
                    if (tvFullName != null)
                        tvFullName.setText("Hello, " + capitalize(trimmed) + "!");
                });
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        char first = name.charAt(0);
        return Character.isUpperCase(first) ? name : Character.toUpperCase(first) + name.substring(1);
    }

    // ──────────────────────────────────────────────────────────────
    // Debug helpers
    // ──────────────────────────────────────────────────────────────

    private void debugCompleteAllMissions() {
        if (!BuildConfig.DEBUG) return;
        Toast.makeText(this, "Debug: completing all 5 missions…", Toast.LENGTH_SHORT).show();
        String[] ids = {"fort_santiago", "baluarte_san_diego", "casa_manila", "museo_intramuros", "centro_turismo"};
        int[] remaining = {ids.length};
        for (String id : ids) {
            MissionCompletionHelper.completeMission(this, id,
                    new MissionCompletionHelper.CompletionCallback() {
                        @Override public void onSuccess() {
                            if (--remaining[0] == 0) runOnUiThread(() -> {
                                Toast.makeText(HomeActivity.this,
                                        "All 5 missions complete!", Toast.LENGTH_LONG).show();
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
        arHelpers.add(new ARHelper("Fort Santiago", "", 14.594265, 120.970425,
                "fort_santiago", "José Rizal",
                "In this cell, my thoughts turned to freedom. I leave behind my last poem, " +
                "hidden within these walls. Seek it, and understand what we fought for.",
                "rizal_character"));
        arHelpers.add(new ARHelper("Baluarte de San Diego", "", 14.585491, 120.975702,
                "baluarte_san_diego", "Antonio Sedeño",
                "From this tower, we watched the galleons approach across Manila Bay. " +
                "Help me raise these walls higher — the city's defence depends on us.",
                "sedeno_character"));
        arHelpers.add(new ARHelper("Casa Manila", "", 14.589622, 120.975129,
                "casa_manila", "Imelda Marcos",
                "This home revives our bahay na bato legacy. Every room tells a story " +
                "of the merchant families who shaped colonial Manila. Let me show you.",
                "marcos_character"));
        arHelpers.add(new ARHelper("Museo de Intramuros", "", 14.589853, 120.973438,
                "museo_intramuros", "Martin Tinio Jr.",
                "These stones whisper Manila's four-hundred-year saga. " +
                "Match the artifacts to their era and unlock the city's buried secrets.",
                "tinio_character"));
        arHelpers.add(new ARHelper("Centro de Turismo", "", 14.590135, 120.973367,
                "centro_turismo", "St. Ignatius of Loyola",
                "From ruins, renewal rises. You have walked the length of the Walled City " +
                "and kept the flame of memory alive. The Intramuros Souvenir is yours.",
                "ignatius_character"));
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
                    @Override public void onResult(Set<String> completedIds, boolean allComplete) {
                        if (!isFinishing() && !isDestroyed())
                            runOnUiThread(() -> updateProgressUI(completedIds, allComplete));
                    }
                    @Override public void onError(String message) {
                        if (!isFinishing() && !isDestroyed()) runOnUiThread(() -> {
                            if (tvProgressLabel != null) {
                                tvProgressLabel.setText("Unable to load progress — check your connection");
                                tvProgressLabel.setVisibility(View.VISIBLE);
                            }
                        });
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
        if (arAdapter != null) arAdapter.setCompletedMissions(completedIds);
        if (cardNFTClaim != null) cardNFTClaim.setVisibility(View.GONE);
        updateTreasureChest(allComplete);
    }

    // ──────────────────────────────────────────────────────────────
    // Treasure chest
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
        boolean claimed = sh.getBoolean(PREF_NFT_CLAIMED, false);
        if (claimed) {
            imgTreasureChest.setImageResource(R.drawable.treasure_chest_open);
            tvTreasureCaption.setText("Your Intramuros Souvenir");
            tvTreasureHint.setText("Tap to view");
        } else {
            imgTreasureChest.setImageResource(R.drawable.treasure_chest_closed);
            tvTreasureCaption.setText("A treasure has appeared…");
            tvTreasureHint.setText("Tap to open");
        }
        revealChestIfNeeded();
    }

    private void revealChestIfNeeded() {
        if (chestAnimatedIn) { treasureChestContainer.setVisibility(View.VISIBLE); return; }
        cancelPendingChestReveal();
        treasureChestContainer.setVisibility(View.INVISIBLE);
        pendingChestReveal = () -> {
            if (isFinishing() || isDestroyed() || treasureChestContainer == null) return;
            treasureChestContainer.setVisibility(View.VISIBLE);
            treasureChestContainer.setAlpha(0f);
            treasureChestContainer.setScaleX(0.6f);
            treasureChestContainer.setScaleY(0.6f);
            treasureChestContainer.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).setDuration(500)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(this::startChestPulse).start();
            chestAnimatedIn = true;
        };
        uiHandler.postDelayed(pendingChestReveal, CHEST_REVEAL_DELAY_MS);
    }

    private void startChestPulse() {
        if (imgTreasureChest == null || isFinishing() || isDestroyed()) return;
        if (SecurePrefs.get(this).getBoolean(PREF_NFT_CLAIMED, false)) return;
        imgTreasureChest.animate().scaleX(1.08f).scaleY(1.08f).setDuration(700)
                .withEndAction(() -> {
                    if (imgTreasureChest == null) return;
                    imgTreasureChest.animate().scaleX(1.0f).scaleY(1.0f).setDuration(700)
                            .withEndAction(this::startChestPulse).start();
                }).start();
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
        if (claimed) { launchNFTClaimFlow(); return; }
        treasureChestContainer.setClickable(false);
        imgTreasureChest.animate().scaleX(1.2f).scaleY(1.2f).setDuration(250)
                .withEndAction(() -> {
                    if (imgTreasureChest == null) return;
                    imgTreasureChest.setImageResource(R.drawable.treasure_chest_open);
                    tvTreasureCaption.setText("Your Intramuros Souvenir!");
                    tvTreasureHint.setText("Opening…");
                    imgTreasureChest.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250)
                            .withEndAction(() -> uiHandler.postDelayed(this::launchNFTClaimFlow, 450))
                            .start();
                }).start();
    }

    private void launchNFTClaimFlow() {
        if (isFinishing() || isDestroyed()) return;
        WalletManager wm = WalletManager.getInstance(this);
        Intent intent = wm.hasWallet()
                ? new Intent(this, NFTClaimActivity.class)
                : new Intent(this, WalletSetupActivity.class);
        startActivity(intent);
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
        dialog.findViewById(R.id.linearLayoutSetting).setOnClickListener(v ->
                startActivity(new Intent(this, SettingActivity.class)));
        dialog.findViewById(R.id.linearLayoutAboutUs).setOnClickListener(v ->
                startActivity(new Intent(this, AboutUsActivity.class)));
        dialog.show();
    }
}
