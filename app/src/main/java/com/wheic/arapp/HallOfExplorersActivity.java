package com.wheic.arapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public final class HallOfExplorersActivity extends AppCompatActivity {

    private static final String MODE_OVERALL = "overall";
    private static final String MODE_MISSIONS = "missions";

    private LinearLayout linearLayoutBack;
    private Chip chipOverall;
    private Chip chipMissionRankings;
    private ChipGroup chipGroupMissionBoards;
    private TextView tvCachedBanner;
    private ProgressBar progressHall;
    private RecyclerView recyclerRankings;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyTitle;
    private TextView tvEmptyBody;
    private LinearLayout layoutError;
    private TextView tvRetry;
    private LinearLayout layoutCurrentUserCard;
    private TextView tvCurrentUserRank;
    private TextView tvCurrentUserAvatar;
    private TextView tvCurrentUserName;
    private TextView tvCurrentUserDetail;
    private TextView tvCurrentUserSouvenir;
    private LinearLayout layoutHiddenNotice;
    private TextView btnManageVisibility;

    private ExplorerRankingAdapter adapter;
    private LeaderboardRepository repository;
    private String selectedMode = MODE_OVERALL;
    private String selectedBoardId = LeaderboardRepository.BOARD_OVERALL_INTRAMUROS;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.hall_of_explorers_activity);

        currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
        repository = new LeaderboardRepository();

        linearLayoutBack = findViewById(R.id.linearLayoutBack);
        chipOverall = findViewById(R.id.chipOverall);
        chipMissionRankings = findViewById(R.id.chipMissionRankings);
        chipGroupMissionBoards = findViewById(R.id.chipGroupMissionBoards);
        tvCachedBanner = findViewById(R.id.tvCachedBanner);
        progressHall = findViewById(R.id.progressHall);
        recyclerRankings = findViewById(R.id.recyclerRankings);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyBody = findViewById(R.id.tvEmptyBody);
        layoutError = findViewById(R.id.layoutError);
        tvRetry = findViewById(R.id.tvRetry);
        layoutCurrentUserCard = findViewById(R.id.layoutCurrentUserCard);
        tvCurrentUserRank = findViewById(R.id.tvCurrentUserRank);
        tvCurrentUserAvatar = findViewById(R.id.tvCurrentUserAvatar);
        tvCurrentUserName = findViewById(R.id.tvCurrentUserName);
        tvCurrentUserDetail = findViewById(R.id.tvCurrentUserDetail);
        tvCurrentUserSouvenir = findViewById(R.id.tvCurrentUserSouvenir);
        layoutHiddenNotice = findViewById(R.id.layoutHiddenNotice);
        btnManageVisibility = findViewById(R.id.btnManageVisibility);

        recyclerRankings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExplorerRankingAdapter(this, currentUid, this::detailForCurrentMode);
        recyclerRankings.setAdapter(adapter);

        linearLayoutBack.setOnClickListener(v -> finish());
        chipOverall.setOnClickListener(v -> switchMode(MODE_OVERALL));
        chipMissionRankings.setOnClickListener(v -> switchMode(MODE_MISSIONS));
        tvRetry.setOnClickListener(v -> loadCurrentBoard());
        btnManageVisibility.setOnClickListener(v ->
                startActivity(new Intent(this, SettingActivity.class)));

        bindMissionChips();
        switchMode(MODE_OVERALL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentBoard();
    }

    private void bindMissionChips() {
        setMissionChip(R.id.chipFortSantiago, LeaderboardRepository.BOARD_MISSION_FORT_SANTIAGO);
        setMissionChip(R.id.chipBaluarte, LeaderboardRepository.BOARD_MISSION_BALUARTE_DE_SAN_DIEGO);
        setMissionChip(R.id.chipCasaManila, LeaderboardRepository.BOARD_MISSION_CASA_MANILA);
        setMissionChip(R.id.chipMuseo, LeaderboardRepository.BOARD_MISSION_MUSEO_INTRAMUROS);
        setMissionChip(R.id.chipCentro, LeaderboardRepository.BOARD_MISSION_CENTRO_DE_TURISMO);
    }

    private void setMissionChip(int chipId, String boardId) {
        Chip chip = findViewById(chipId);
        chip.setOnClickListener(v -> {
            selectedBoardId = boardId;
            chip.setChecked(true);
            loadCurrentBoard();
        });
    }

    private void switchMode(String mode) {
        selectedMode = mode;
        boolean missionsMode = MODE_MISSIONS.equals(mode);
        chipOverall.setChecked(!missionsMode);
        chipMissionRankings.setChecked(missionsMode);
        chipGroupMissionBoards.setVisibility(missionsMode ? View.VISIBLE : View.GONE);
        if (!missionsMode) {
            selectedBoardId = LeaderboardRepository.BOARD_OVERALL_INTRAMUROS;
        } else if (selectedBoardId.equals(LeaderboardRepository.BOARD_OVERALL_INTRAMUROS)) {
            selectedBoardId = LeaderboardRepository.BOARD_MISSION_FORT_SANTIAGO;
            Chip firstChip = findViewById(R.id.chipFortSantiago);
            firstChip.setChecked(true);
        }
        loadCurrentBoard();
    }

    private void loadCurrentBoard() {
        setLoading(true);
        repository.loadBoard(selectedBoardId, new LeaderboardRepository.LoadCallback() {
            @Override
            public void onSuccess(LeaderboardLoadResult result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                showData(result);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                showError();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressHall.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            recyclerRankings.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);
            layoutCurrentUserCard.setVisibility(View.GONE);
            layoutHiddenNotice.setVisibility(View.GONE);
            tvCachedBanner.setVisibility(View.GONE);
        }
    }

    private void showData(LeaderboardLoadResult result) {
        adapter.submit(result.getTopEntries());
        recyclerRankings.setVisibility(result.getTopEntries().isEmpty() ? View.GONE : View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        tvCachedBanner.setVisibility(result.isFromCache() ? View.VISIBLE : View.GONE);

        if (LeaderboardRepository.VISIBILITY_HIDDEN.equals(result.getVisibilityMode())) {
            layoutHiddenNotice.setVisibility(View.VISIBLE);
            layoutCurrentUserCard.setVisibility(View.GONE);
        } else {
            layoutHiddenNotice.setVisibility(View.GONE);
            bindCurrentUserCard(result);
        }

        if (result.getTopEntries().isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            if (result.getCurrentUserEntry() == null && !LeaderboardRepository.VISIBILITY_HIDDEN.equals(result.getVisibilityMode())) {
                tvEmptyTitle.setText("Complete your first mission");
                tvEmptyBody.setText("Finish an Intramuros mission to appear in the Hall of Explorers.");
            } else {
                tvEmptyTitle.setText("No explorers yet");
                tvEmptyBody.setText("No verified completions have been recorded for this ranking yet.");
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    private void bindCurrentUserCard(LeaderboardLoadResult result) {
        LeaderboardEntry currentEntry = result.getCurrentUserEntry();
        if (currentEntry == null) {
            layoutCurrentUserCard.setVisibility(View.GONE);
            return;
        }

        List<LeaderboardEntry> topEntries = result.getTopEntries();
        boolean inTopTen = false;
        for (LeaderboardEntry entry : topEntries) {
            if (entry.getUid().equals(currentEntry.getUid())) {
                inTopTen = true;
                break;
            }
        }
        if (inTopTen) {
            layoutCurrentUserCard.setVisibility(View.GONE);
            return;
        }

        layoutCurrentUserCard.setVisibility(View.VISIBLE);
        tvCurrentUserRank.setText("#" + currentEntry.getRankPosition());
        tvCurrentUserAvatar.setText(currentEntry.getAvatarInitial());
        tvCurrentUserName.setText(currentEntry.getDisplayNamePublic());
        tvCurrentUserDetail.setText(detailForCurrentMode(currentEntry));
        tvCurrentUserSouvenir.setVisibility(currentEntry.isSouvenirMinted() ? View.VISIBLE : View.GONE);
    }

    private void showError() {
        recyclerRankings.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        layoutCurrentUserCard.setVisibility(View.GONE);
        layoutHiddenNotice.setVisibility(View.GONE);
        tvCachedBanner.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
    }

    private String detailForCurrentMode(LeaderboardEntry entry) {
        if (MODE_MISSIONS.equals(selectedMode)) {
            return ExplorerRankingAdapter.formatMissionDate(entry.getMissionCompletedAt());
        }
        if (entry.isAllIntramurosComplete()) {
            return "Completed all 5 Intramuros missions";
        }
        return entry.getIntramurosMissionCount() + " of 5 missions complete";
    }
}
