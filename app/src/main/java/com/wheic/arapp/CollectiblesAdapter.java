package com.wheic.arapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CollectiblesAdapter extends RecyclerView.Adapter<CollectiblesAdapter.ViewHolder> {

    private final List<CollectibleItem> items;

    public CollectiblesAdapter(List<CollectibleItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collectible_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        CollectibleItem item = items.get(position);

        h.tvTitle.setText(item.getTitle());
        h.tvDesc.setText(item.getDescription());
        h.tvCount.setText(item.getCount() + "/" + item.getMaxCount());
        h.progress.setMax(item.getMaxCount());
        h.progress.setProgress(item.getCount());

        if (item.getThumbResId() != 0) {
            h.imgThumb.setImageResource(item.getThumbResId());
        }

        // Dim card if not yet collected
        h.itemView.setAlpha(item.getCount() > 0 ? 1.0f : 0.65f);
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void updateCounts(List<CollectibleItem> updated) {
        for (int i = 0; i < items.size() && i < updated.size(); i++) {
            items.get(i).setCount(updated.get(i).getCount());
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView   imgThumb;
        TextView    tvTitle, tvDesc, tvCount;
        ProgressBar progress;

        ViewHolder(@NonNull View v) {
            super(v);
            imgThumb = v.findViewById(R.id.imgCollectibleThumb);
            tvTitle  = v.findViewById(R.id.tvCollectibleTitle);
            tvDesc   = v.findViewById(R.id.tvCollectibleDesc);
            tvCount  = v.findViewById(R.id.tvCollectibleCount);
            progress = v.findViewById(R.id.progressCollectible);
        }
    }
}
