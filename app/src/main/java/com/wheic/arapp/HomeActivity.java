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
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    ImageView imgDashboard;
    List<ARHelper> arHelpers;
    ARAdapter arAdapter;
    RecyclerView recyclerView;

    // Mission progress UI
    TextView tvProgressLabel;
    CardView cardNFTClaim;
    TextView tvNFTClaimStatus;

    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        SharedPreferences sh = getSharedPreferences("Volver", Context.MODE_PRIVATE);
        username = sh.getString("username", "");

        imgDashboard     = findViewById(R.id.imgDashboard);
        recyclerView     = findViewById(R.id.recyclerView);
        tvProgressLabel  = findViewById(R.id.tvProgressLabel);
        cardNFTClaim     = findViewById(R.id.cardNFTClaim);
        tvNFTClaimStatus = findViewById(R.id.tvNFTClaimStatus);

        imgDashboard.setOnClickListener(v -> showDashboard());

        // NFT claim card tap
        cardNFTClaim.setOnClickListener(v -> {
            WalletManager wm = WalletManager.getInstance(this);
            if (wm.hasWallet()) {
                startActivity(new Intent(this, NFTClaimActivity.class));
            } else {
                startActivity(new Intent(this, WalletSetupActivity.class));
            }
        });

        buildMissionList();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh progress every time the user returns to Home
        loadMissionProgress();
    }

    // ──────────────────────────────────────────────────────────────
    // Mission list
    // ──────────────────────────────────────────────────────────────

    private void buildMissionList() {
        arHelpers = new ArrayList<>();

        arHelpers.add(new ARHelper(
                "Fort Santiago",
                "",
                14.6367, 121.0028,
                "fort_santiago",
                "José Rizal",
                "In this cell, my thoughts turned to freedom. I leave behind my last poem, " +
                "hidden within these walls. Seek it, and understand what we fought for.",
                "rizal_character"
        ));

        arHelpers.add(new ARHelper(
                "Baluarte de San Diego",
                "",
                14.587778, 120.971667,
                "baluarte_san_diego",
                "Antonio Sedeño",
                "From this tower, we watched the galleons approach across Manila Bay. " +
                "Help me raise these walls higher — the city's defence depends on us.",
                "sedeno_character"
        ));

        arHelpers.add(new ARHelper(
                "Casa Manila",
                "",
                14.590000, 120.975278,
                "casa_manila",
                "Imelda Marcos",
                "This home revives our bahay na bato legacy. Every room tells a story " +
                "of the merchant families who shaped colonial Manila. Let me show you.",
                "marcos_character"
        ));

        arHelpers.add(new ARHelper(
                "Museo de Intramuros",
                "",
                14.5898, 120.9734,
                "museo_intramuros",
                "Martin Tinio Jr.",
                "These stones whisper Manila's four-hundred-year saga. " +
                "Match the artifacts to their era and unlock the city's buried secrets.",
                "tinio_character"
        ));

        arHelpers.add(new ARHelper(
                "Centro de Turismo",
                "",
                14.5912, 120.9756,
                "centro_turismo",
                "St. Ignatius of Loyola",
                "From ruins, renewal rises. You have walked the length of the Walled City " +
                "and kept the flame of memory alive. The Intramuros Passport is yours.",
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
                        runOnUiThread(() -> updateProgressUI(completedIds.size(), allComplete));
                    }

                    @Override
                    public void onError(String message) {
                        // Silently fail — progress UI stays hidden rather than showing an error
                    }
                });
    }

    private void updateProgressUI(int completedCount, boolean allComplete) {
        int total = arHelpers.size();

        if (tvProgressLabel != null) {
            tvProgressLabel.setText("Missions: " + completedCount + " / " + total + " complete");
            tvProgressLabel.setVisibility(View.VISIBLE);
        }

        if (cardNFTClaim != null) {
            if (allComplete) {
                cardNFTClaim.setVisibility(View.VISIBLE);
                WalletManager wm = WalletManager.getInstance(this);
                if (tvNFTClaimStatus != null) {
                    tvNFTClaimStatus.setText(wm.hasWallet()
                            ? "Tap to mint your Walled City Key NFT →"
                            : "Set up your wallet to claim your NFT →");
                }
            } else {
                cardNFTClaim.setVisibility(View.GONE);
            }
        }
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
