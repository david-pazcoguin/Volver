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
import android.widget.ProgressBar;
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

    // ── Auto-carousel & auto-fact ────────────────────────
    private static final long CAROUSEL_INTERVAL_MS = 4000L;
    private static final long FACT_INTERVAL_MS     = 8000L;
    private int carouselIndex = 0;
    private Set<String> lastCompletedIds = null;
    private final Runnable carouselRunnable = this::advanceCarousel;
    private final Runnable factRunnable     = this::rotateFactAuto;

    // ── Collectibles tab ────────────────────────────────
    private LinearLayout layoutMissions;
    private View layoutHome;
    private TextView tvHomeProgress;
    private ProgressBar progressHome;
    private TextView btnContinueQuest;
    private TextView tvHomeNFTStatus;
    private TextView tvHomeFact;
    private TextView tvHomeGreeting;
    private TextView tvHomeTagline;
    private TextView tvHomeProgressDetail;
    private TextView tvStatMissions;
    private TextView tvStatRelics;
    private TextView tvStatNFT;
    private View cardFeaturedMission;
    private android.widget.ImageView imgFeaturedMission;
    private TextView tvFeaturedLabel;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedSubtitle;
    private TextView btnNextFact;
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

        // One-time migration: wipe collectible counts that were written before the
        // pendingCollectibleAwards buffer was introduced. Counts are now only written
        // after a mission is fully completed, so any partial values are incorrect.
        if (!sh.getBoolean("migration_collectibles_cleared_v1", false)) {
            SharedPreferences.Editor wipe = sh.edit();
            for (String key : sh.getAll().keySet()) {
                if (key.startsWith("collectible_") && key.endsWith("_count")) {
                    wipe.remove(key);
                }
            }
            wipe.putBoolean("migration_collectibles_cleared_v1", true).apply();
        }

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
        layoutHome         = findViewById(R.id.layoutHome);
        tvHomeProgress     = findViewById(R.id.tvHomeProgress);
        progressHome       = findViewById(R.id.progressHome);
        btnContinueQuest   = findViewById(R.id.btnContinueQuest);
        tvHomeNFTStatus    = findViewById(R.id.tvHomeNFTStatus);
        tvHomeFact         = findViewById(R.id.tvHomeFact);
        tvHomeGreeting       = findViewById(R.id.tvHomeGreeting);
        tvHomeTagline        = findViewById(R.id.tvHomeTagline);
        tvHomeProgressDetail = findViewById(R.id.tvHomeProgressDetail);
        tvStatMissions       = findViewById(R.id.tvStatMissions);
        tvStatRelics         = findViewById(R.id.tvStatRelics);
        tvStatNFT            = findViewById(R.id.tvStatNFT);
        cardFeaturedMission  = findViewById(R.id.cardFeaturedMission);
        imgFeaturedMission   = findViewById(R.id.imgFeaturedMission);
        tvFeaturedLabel      = findViewById(R.id.tvFeaturedLabel);
        tvFeaturedTitle      = findViewById(R.id.tvFeaturedTitle);
        tvFeaturedSubtitle   = findViewById(R.id.tvFeaturedSubtitle);
        btnNextFact          = findViewById(R.id.btnNextFact);
        layoutCollectibles = findViewById(R.id.layoutCollectibles);
        recyclerCollectibles = findViewById(R.id.recyclerCollectibles);
        tvCollectiblesTotal  = findViewById(R.id.tvCollectiblesTotal);
        tvTotalBadge         = findViewById(R.id.tvTotalBadge);
        bottomNav            = findViewById(R.id.bottomNav);

        treasureChestContainer.setOnClickListener(v -> onTreasureChestTapped());
        if (btnContinueQuest != null) {
            btnContinueQuest.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_missions));
        }
        if (cardFeaturedMission != null) {
            cardFeaturedMission.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_missions));
        }
        View btnHeroExplore = findViewById(R.id.btnHeroExplore);
        if (btnHeroExplore != null) {
            btnHeroExplore.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_missions));
        }
        if (btnNextFact != null) {
            btnNextFact.setOnClickListener(v -> showRandomFact());
        }
        showRandomFact();

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
        uiHandler.removeCallbacks(carouselRunnable);
        uiHandler.removeCallbacks(factRunnable);
        uiHandler.postDelayed(carouselRunnable, CAROUSEL_INTERVAL_MS);
        uiHandler.postDelayed(factRunnable, FACT_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(carouselRunnable);
        uiHandler.removeCallbacks(factRunnable);
    }

    // ──────────────────────────────────────────────────────────────
    // Bottom navigation
    // ──────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                layoutHome.setVisibility(View.VISIBLE);
                layoutMissions.setVisibility(View.GONE);
                layoutCollectibles.setVisibility(View.GONE);
                showRandomFact();
                return true;
            } else if (id == R.id.nav_missions) {
                layoutHome.setVisibility(View.GONE);
                layoutMissions.setVisibility(View.VISIBLE);
                layoutCollectibles.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_collectibles) {
                layoutHome.setVisibility(View.GONE);
                layoutMissions.setVisibility(View.GONE);
                layoutCollectibles.setVisibility(View.VISIBLE);
                refreshCollectibleCounts();
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, SettingActivity.class));
                return true;
            }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    // ───────────────────────────────────────────────────────────────
    // Home tab content
    // ───────────────────────────────────────────────────────────────

    private static final String[] INTRAMUROS_FACTS = new String[] {
        "‘Intramuros’ is Spanish for ‘within the walls.’ The walled city was founded in 1571 by Spanish conquistador Miguel López de Legazpi.",
        "Fort Santiago, at the northwest tip of Intramuros, served as the headquarters of the Spanish military for over 300 years.",
        "Manila Cathedral, founded in 1571, has been destroyed and rebuilt eight times. The current structure dates to 1958.",
        "San Agustín Church (1607) is the oldest stone church in the Philippines and a UNESCO World Heritage Site.",
        "Casa Manila is a faithful reproduction of a 19th-century Spanish colonial home, showcasing Filipino-Hispanic life.",
        "José Rizal, the Philippine national hero, was imprisoned at Fort Santiago before his execution on December 30, 1896.",
        "The walls of Intramuros stretch about 4.5 km, average 3 m thick, and were originally surrounded by a moat.",
        "During World War II, Intramuros was almost completely destroyed in the 1945 Battle of Manila — only San Agustín Church survived intact."
    };

    private void showRandomFact() {
        if (tvHomeFact == null) return;
        int idx = (int) (Math.random() * INTRAMUROS_FACTS.length);
        tvHomeFact.setText(INTRAMUROS_FACTS[idx]);
    }

    /** Automatically fades to the next fact every FACT_INTERVAL_MS. */
    private void rotateFactAuto() {
        if (tvHomeFact == null) return;
        tvHomeFact.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            showRandomFact();
            tvHomeFact.animate().alpha(1f).setDuration(400).start();
        }).start();
        uiHandler.postDelayed(factRunnable, FACT_INTERVAL_MS);
    }

    /** Advances the featured-mission banner through all 6 locations with a crossfade. */
    private void advanceCarousel() {
        if (arHelpers == null || arHelpers.isEmpty() || cardFeaturedMission == null) return;
        carouselIndex = (carouselIndex + 1) % arHelpers.size();
        ARHelper mission = arHelpers.get(carouselIndex);
        boolean completed = lastCompletedIds != null
                && lastCompletedIds.contains(mission.getMissionId());

        cardFeaturedMission.animate().alpha(0.45f).setDuration(300).withEndAction(() -> {
            if (imgFeaturedMission != null)
                imgFeaturedMission.setImageResource(featuredImageFor(mission.getMissionId()));
            if (tvFeaturedLabel != null)
                tvFeaturedLabel.setText(completed ? "✓ COMPLETED" : "EXPLORE");
            if (tvFeaturedTitle != null)
                tvFeaturedTitle.setText(mission.getMissionName());
            if (tvFeaturedSubtitle != null)
                tvFeaturedSubtitle.setText(completed
                        ? "Mission accomplished!"
                        : "Tap to view mission details");
            cardFeaturedMission.animate().alpha(1f).setDuration(300).start();
        }).start();

        uiHandler.postDelayed(carouselRunnable, CAROUSEL_INTERVAL_MS);
    }

    private void updateHomeProgressUI(Set<String> completedIds, int completedCount, int total, boolean allComplete) {
        lastCompletedIds = completedIds;
        if (tvHomeProgress != null) {
            tvHomeProgress.setText(completedCount + " of " + total);
        }
        if (progressHome != null) {
            int pct = total > 0 ? (int) Math.round(100.0 * completedCount / total) : 0;
            progressHome.setProgress(pct);
        }
        if (btnContinueQuest != null) {
            btnContinueQuest.setText(allComplete ? "View Missions" : (completedCount == 0 ? "Start Quest" : "Continue Quest"));
        }
        if (tvStatMissions != null) {
            tvStatMissions.setText(completedCount + "/" + total);
        }
        if (tvStatRelics != null) {
            tvStatRelics.setText(getTotalRelicCount() + "/60");
        }
        boolean nftClaimed = SecurePrefs.get(this).getBoolean(PREF_NFT_CLAIMED, false);
        if (tvStatNFT != null) {
            tvStatNFT.setText(nftClaimed ? "✓" : (allComplete ? "!" : "—"));
        }
        if (tvHomeProgressDetail != null) {
            if (allComplete) {
                tvHomeProgressDetail.setText("All missions complete — claim your souvenir!");
            } else if (completedCount == 0) {
                tvHomeProgressDetail.setText("Begin your journey through the Walled City.");
            } else {
                int remaining = total - completedCount;
                tvHomeProgressDetail.setText(remaining + " mission" + (remaining == 1 ? "" : "s") + " remaining on your quest.");
            }
        }
        if (tvHomeNFTStatus != null) {
            if (nftClaimed) {
                tvHomeNFTStatus.setText("✨ Claimed — your souvenir is on Polygon");
            } else if (allComplete) {
                tvHomeNFTStatus.setText("Ready to claim! Tap the treasure chest in Missions.");
            } else {
                int remaining = total - completedCount;
                tvHomeNFTStatus.setText("Complete " + remaining + " more mission" + (remaining == 1 ? "" : "s") + " to earn");
            }
        }
        updateFeaturedMission(completedIds, allComplete, nftClaimed);
    }

    private int getTotalRelicCount() {
        if (collectibleItems == null) return 0;
        SharedPreferences sh = SecurePrefs.get(this);
        int total = 0;
        for (CollectibleItem item : collectibleItems) {
            total += sh.getInt("collectible_" + item.getId() + "_count", 0);
        }
        return total;
    }

    private void updateFeaturedMission(Set<String> completedIds, boolean allComplete, boolean nftClaimed) {
        if (cardFeaturedMission == null || arHelpers == null || arHelpers.isEmpty()) return;

        if (allComplete) {
            if (tvFeaturedLabel != null) tvFeaturedLabel.setText(nftClaimed ? "QUEST COMPLETE" : "READY TO CLAIM");
            if (tvFeaturedTitle != null) tvFeaturedTitle.setText(nftClaimed ? "Quest Complete!" : "Claim Your Souvenir");
            if (tvFeaturedSubtitle != null)
                tvFeaturedSubtitle.setText(nftClaimed
                        ? "Your NFT is minted on Polygon"
                        : "Tap the treasure chest in Missions");
            if (imgFeaturedMission != null) imgFeaturedMission.setImageResource(R.drawable.centro_turismo);
            return;
        }

        ARHelper next = null;
        for (ARHelper h : arHelpers) {
            if (completedIds == null || !completedIds.contains(h.getMissionId())) {
                next = h;
                break;
            }
        }
        if (next == null) return;

        // Anchor the carousel to the first incomplete mission so the auto-slide
        // starts from the right position each time progress is loaded.
        for (int i = 0; i < arHelpers.size(); i++) {
            if (arHelpers.get(i) == next) { carouselIndex = i; break; }
        }

        if (tvFeaturedLabel != null) {
            tvFeaturedLabel.setText(completedIds == null || completedIds.isEmpty() ? "BEGIN YOUR QUEST" : "NEXT MISSION");
        }
        if (tvFeaturedTitle != null) tvFeaturedTitle.setText(next.getMissionName());
        if (tvFeaturedSubtitle != null) tvFeaturedSubtitle.setText("Tap to view mission details");
        if (imgFeaturedMission != null) {
            imgFeaturedMission.setImageResource(featuredImageFor(next.getMissionId()));
        }
    }

    private int featuredImageFor(String missionId) {
        if (missionId == null) return R.drawable.fort_santiago;
        switch (missionId) {
            case "fort_santiago":      return R.drawable.fort_santiago;
            case "baluarte_san_diego": return R.drawable.baluarte_san_diego;
            case "casa_manila":        return R.drawable.casa_manila;
            case "museo_intramuros":   return R.drawable.museo_intramuros;
            case "centro_turismo":     return R.drawable.centro_turismo;
            case "lpu":                return R.drawable.lpu;
            default:                   return R.drawable.fort_santiago;
        }
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
                R.drawable.ic_coin, 0, 12));

        collectibleItems.add(new CollectibleItem(
                "peineta",
                "Peineta",
                "An ornate Spanish hair comb worn by Filipina women during the colonial era. " +
                "A symbol of elegance, identity, and the blending of cultures.",
                R.drawable.ic_peineta, 0, 12));

        collectibleItems.add(new CollectibleItem(
                "salakot_elite",
                "Salakot Elite",
                "A ceremonial salakot adorned with fine gold engravings, worn by the principalia " +
                "during official colonial gatherings and religious processions.",
                R.drawable.ic_salakot, 0, 12));

        collectibleItems.add(new CollectibleItem(
                "farol_de_aceite",
                "Farol de Aceite",
                "An oil lantern that lit the cobblestone streets of Intramuros for centuries. " +
                "Its warm glow guided merchants, soldiers, and friars through the Walled City.",
                R.drawable.ic_lantern, 0, 12));

        collectibleItems.add(new CollectibleItem(
                "pocket_watch",
                "Antique Pocket Watch",
                "A tarnished brass pocket watch with Roman numerals and a matching chain. " +
                "The signature accessory of an educated Ilustrado gentleman.",
                R.drawable.ic_pocket_watch, 0, 12));

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

        int maxTotal = collectibleItems.size() * 12;
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
        if (!cached.isEmpty()) {
            tvFullName.setText(capitalize(cached));
            if (tvHomeGreeting != null)
                tvHomeGreeting.setText("Mabuhay, " + capitalize(cached) + "!");
        }

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
                        tvFullName.setText(capitalize(trimmed));
                    if (tvHomeGreeting != null)
                        tvHomeGreeting.setText("Mabuhay, " + capitalize(trimmed) + "!");
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
        Toast.makeText(this, "Debug: completing all 6 missions…", Toast.LENGTH_SHORT).show();
        String[] ids = {"fort_santiago", "baluarte_san_diego", "casa_manila", "museo_intramuros", "centro_turismo", "lpu"};
        int[] remaining = {ids.length};
        for (String id : ids) {
            MissionCompletionHelper.completeMission(this, id,
                    new MissionCompletionHelper.CompletionCallback() {
                        @Override public void onSuccess() {
                            if (--remaining[0] == 0) runOnUiThread(() -> {
                                Toast.makeText(HomeActivity.this,
                                        "All 6 missions complete!", Toast.LENGTH_LONG).show();
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
                "rizal_character",
                new double[]{14.594230, 14.594275}, new double[]{120.970460, 120.970507},
                "intramuros_coin"));
        arHelpers.add(new ARHelper("Baluarte de San Diego", "", 14.585491, 120.975702,
                "baluarte_san_diego", "Antonio Sedeño",
                "From this tower, we watched the galleons approach across Manila Bay. " +
                "Help me raise these walls higher — the city's defence depends on us.",
                "sedeno_character",
                new double[]{14.585520, 14.585565}, new double[]{120.975730, 120.975683},
                "farol_de_aceite"));
        arHelpers.add(new ARHelper("Casa Manila", "", 14.589630881841018, 120.97515722599451,
                "casa_manila", "Imelda Marcos",
                "This home revives our bahay na bato legacy. Every room tells a story " +
                "of the merchant families who shaped colonial Manila. Let me show you.",
                "marcos_character",
                // 5 relic stages × 2 coins each, in order. The user must collect
                // each relic pair before the next pair appears.
                new double[]{
                    // Stage 1: Intramuros Coin
                    14.589663203491886, 14.589618427314848,
                    // Stage 2: Peineta
                    14.589609963023207, 14.58966382422264,
                    // Stage 3: Salakot Elite
                    14.589598931209652, 14.58964370739066,
                    // Stage 4: Farol de Aceite
                    14.589579685619842, 14.589676987413734,
                    // Stage 5: Antique Pocket Watch
                    14.589638138561947, 14.589585575216757
                },
                new double[]{
                    120.97526796641772, 120.97521968665782,
                    120.97526797947917, 120.97523914573368,
                    120.97518617210824, 120.97530754206021,
                    120.9752383013906,  120.97527811405568,
                    120.9752492421618,  120.975198280193
                },
                new String[]{
                    "intramuros_coin", "intramuros_coin",
                    "peineta",         "peineta",
                    "salakot_elite",   "salakot_elite",
                    "farol_de_aceite", "farol_de_aceite",
                    "pocket_watch",    "pocket_watch"
                }));
        arHelpers.add(new ARHelper("Museo de Intramuros", "", 14.589853, 120.973438,
                "museo_intramuros", "Martin Tinio Jr.",
                "These stones whisper Manila's four-hundred-year saga. " +
                "Match the artifacts to their era and unlock the city's buried secrets.",
                "tinio_character",
                new double[]{14.589880, 14.589925}, new double[]{120.973410, 120.973457},
                "salakot_elite"));
        arHelpers.add(new ARHelper("Centro de Turismo", "", 14.590135, 120.973367,
                "centro_turismo", "St. Ignatius of Loyola",
                "From ruins, renewal rises. You have walked the length of the Walled City " +
                "and kept the flame of memory alive. The Intramuros Souvenir is yours.",
                "ignatius_character",
                new double[]{14.590160, 14.590115}, new double[]{120.973395, 120.973442},
                "pocket_watch"));
        arHelpers.add(new ARHelper("Lyceum of the Philippines University", "", 14.591600276085643, 120.97778918301911,
                "lpu", "José P. Laurel",
                "This university was born from the dream that education could rebuild a nation. " +
                "Every relic you find here carries the spirit of those who shaped modern Manila.",
                "san_bartolome_church",
                new double[]{
                    // Stage 1: Intramuros Coin
                    14.59158019920811, 14.591682008790869,
                    // Stage 2: Peineta
                    14.591680062018614, 14.591570393820067,
                    // Stage 3: Salakot Elite
                    14.591444502447876, 14.591534054049932,
                    // Stage 4: Farol de Aceite
                    14.591722242080426, 14.59153600082347,
                    // Stage 5: Antique Pocket Watch
                    14.591527564804622, 14.59166254106748
                },
                new double[]{
                    120.97776136748872, 120.9778522561306,
                    120.97762695058442, 120.97777380152078,
                    120.97783348066842, 120.97797228497814,
                    120.97775971992415, 120.97784353895175,
                    120.97799240154475, 120.97761957450999
                },
                new String[]{
                    "intramuros_coin", "intramuros_coin",
                    "peineta",         "peineta",
                    "salakot_elite",   "salakot_elite",
                    "farol_de_aceite", "farol_de_aceite",
                    "pocket_watch",    "pocket_watch"
                }));
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
        updateHomeProgressUI(completedIds, completedCount, total, allComplete);
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
        uiHandler.removeCallbacks(carouselRunnable);
        uiHandler.removeCallbacks(factRunnable);
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
