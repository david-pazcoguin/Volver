package com.wheic.arapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

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
        holder.tvYouBadge.setVisibility(isCurrentUser ? View.VISIBLE : View.GONE);
        holder.tvSouvenirBadge.setVisibility(entry.isSouvenirMinted() ? View.VISIBLE : View.GONE);
        holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context,
                isCurrentUser ? R.color.bg_light_gray : R.color.bg_card));
        holder.cardView.setCardElevation(isCurrentUser ? 4f : 1f);
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

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final CardView cardView;
        final TextView tvRank;
        final TextView tvAvatarInitial;
        final TextView tvName;
        final TextView tvDetail;
        final TextView tvYouBadge;
        final TextView tvSouvenirBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardExplorerRow);
            tvRank = itemView.findViewById(R.id.tvExplorerRank);
            tvAvatarInitial = itemView.findViewById(R.id.tvExplorerAvatar);
            tvName = itemView.findViewById(R.id.tvExplorerName);
            tvDetail = itemView.findViewById(R.id.tvExplorerDetail);
            tvYouBadge = itemView.findViewById(R.id.tvExplorerYouBadge);
            tvSouvenirBadge = itemView.findViewById(R.id.tvExplorerSouvenirBadge);
        }
    }
}
