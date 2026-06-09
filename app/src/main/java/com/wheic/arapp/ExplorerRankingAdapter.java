package com.wheic.arapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExplorerRankingAdapter extends RecyclerView.Adapter<ExplorerRankingAdapter.ViewHolder> {

    public interface DetailFormatter {
        String format(LeaderboardEntry entry);
    }

    private final LayoutInflater inflater;
    private final String currentUid;
    private final DetailFormatter detailFormatter;
    private final List<LeaderboardEntry> entries = new ArrayList<>();

    public ExplorerRankingAdapter(Context context, String currentUid, DetailFormatter detailFormatter) {
        this.inflater = LayoutInflater.from(context);
        this.currentUid = currentUid;
        this.detailFormatter = detailFormatter;
        setHasStableIds(true);
    }

    public void submit(List<LeaderboardEntry> newEntries) {
        entries.clear();
        if (newEntries != null) {
            entries.addAll(newEntries);
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).getUid().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.item_explorer_ranking, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        LeaderboardEntry entry = entries.get(position);
        boolean isCurrentUser = entry.getUid().equals(currentUid);

        holder.tvRank.setText("#" + entry.getRankPosition());
        holder.tvAvatarInitial.setText(entry.getAvatarInitial());
        holder.tvName.setText(entry.getDisplayNamePublic());
        holder.tvDetail.setText(detailFormatter.format(entry));
        holder.tvPlacement.setText(placementLabel(entry.getRankPosition()));
        holder.tvYouBadge.setVisibility(isCurrentUser ? View.VISIBLE : View.GONE);
        holder.tvSouvenirBadge.setVisibility(entry.isSouvenirMinted() ? View.VISIBLE : View.GONE);
        applyRankStyling(context, holder, entry.getRankPosition(), isCurrentUser);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static String formatMissionDate(Timestamp timestamp) {
        if (timestamp == null) {
            return "Completed recently";
        }
        return "Completed on " + new SimpleDateFormat("MMM d, yyyy", Locale.US)
                .format(timestamp.toDate());
    }

    private void applyRankStyling(Context context, ViewHolder holder, int rankPosition, boolean isCurrentUser) {
        int cardColor = ContextCompat.getColor(context, isCurrentUser ? R.color.bg_light_gray : R.color.bg_card);
        int strokeColor = ContextCompat.getColor(context, R.color.bg_separator);
        int rankTextColor = ContextCompat.getColor(context, R.color.text_primary);
        int placementTextColor = ContextCompat.getColor(context, R.color.text_primary);
        int rankBackground = R.drawable.bg_rank_pill_default;

        if (rankPosition == 1) {
            cardColor = ContextCompat.getColor(context, R.color.bg_gold_pale);
            strokeColor = ContextCompat.getColor(context, R.color.gold_accent);
            rankTextColor = ContextCompat.getColor(context, R.color.hero_bg);
            rankBackground = R.drawable.bg_rank_pill_first;
        } else if (rankPosition == 2) {
            strokeColor = ContextCompat.getColor(context, R.color.silver);
            rankTextColor = ContextCompat.getColor(context, R.color.hero_bg);
            rankBackground = R.drawable.bg_rank_pill_second;
        } else if (rankPosition == 3) {
            strokeColor = ContextCompat.getColor(context, R.color.text_brown_warm);
            rankBackground = R.drawable.bg_rank_pill_third;
        }

        holder.cardView.setCardBackgroundColor(cardColor);
        holder.cardView.setStrokeColor(strokeColor);
        holder.cardView.setCardElevation(isCurrentUser ? 5f : 1f);
        holder.tvPlacement.setBackgroundResource(rankBackground);
        holder.tvPlacement.setTextColor(rankTextColor);
        holder.tvRank.setBackgroundResource(R.drawable.bg_leaderboard_badge);
        holder.tvRank.setTextColor(placementTextColor);
    }

    private String placementLabel(int rankPosition) {
        if (rankPosition == 1) return "Champion";
        if (rankPosition == 2) return "Runner-up";
        if (rankPosition == 3) return "Third Place";
        return "Ranked Explorer";
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView tvRank;
        final TextView tvPlacement;
        final TextView tvAvatarInitial;
        final TextView tvName;
        final TextView tvDetail;
        final TextView tvYouBadge;
        final TextView tvSouvenirBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardExplorerRow);
            tvPlacement = itemView.findViewById(R.id.tvExplorerPlacement);
            tvRank = itemView.findViewById(R.id.tvExplorerRank);
            tvAvatarInitial = itemView.findViewById(R.id.tvExplorerAvatar);
            tvName = itemView.findViewById(R.id.tvExplorerName);
            tvDetail = itemView.findViewById(R.id.tvExplorerDetail);
            tvYouBadge = itemView.findViewById(R.id.tvExplorerYouBadge);
            tvSouvenirBadge = itemView.findViewById(R.id.tvExplorerSouvenirBadge);
        }
    }
}
