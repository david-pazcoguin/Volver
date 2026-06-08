package com.wheic.arapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.imageview.ShapeableImageView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ARAdapter extends RecyclerView.Adapter<ARAdapter.ViewHolder>
{
    Context context;
    private List<ARHelper> mAR;
    private Set<String> completedIds = Collections.emptySet();

    public ARAdapter(List<ARHelper> ARs, Context context2)
    {
        mAR = ARs;
        context = context2;
        setHasStableIds(true);
    }

    /** Update which missions should render in the "completed" style. */
    public void setCompletedMissions(Set<String> ids) {
        this.completedIds = (ids != null) ? new HashSet<>(ids) : Collections.emptySet();
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return mAR.get(position).getMissionId().hashCode();
    }

    @Override
    public void onBindViewHolder(ARAdapter.ViewHolder holder, int position)
    {
        ARHelper ARHelper = mAR.get(position);

        holder.tvMissionName.setText(ARHelper.getMissionName());

        // Set mission image based on mission ID
        int imageResId = getMissionImageResource(ARHelper.getMissionId());
        holder.imgView.setImageResource(imageResId);

        boolean completed = completedIds.contains(ARHelper.getMissionId());
        applyCompletedStyle(holder, completed);

        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, ARActivity.class);

                Bundle bundle = new Bundle();
                bundle.putString("MissionName",      ARHelper.getMissionName());
                bundle.putString("Content",          ARHelper.getContent());
                bundle.putDouble("Latitude",         ARHelper.getLatitude());
                bundle.putDouble("Longitude",        ARHelper.getLongitude());
                bundle.putDoubleArray("RelicLatitudes",  ARHelper.getRelicLatitudes());
                bundle.putDoubleArray("RelicLongitudes", ARHelper.getRelicLongitudes());
                bundle.putStringArray("RelicIds",        ARHelper.getRelicIds());
                bundle.putString("CollectibleId",    ARHelper.getCollectibleId());
                bundle.putString("MissionId",        ARHelper.getMissionId());

                intent.putExtras(bundle);
                ((Activity) context).startActivity(intent);
            }
        });
    }

    /** Darken the image + desaturate and show the check badge when completed. */
    private void applyCompletedStyle(ViewHolder holder, boolean completed) {
        if (completed) {
            // Desaturate 60% and dim 30%
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0.4f);
            ColorMatrix dim = new ColorMatrix(new float[]{
                    0.7f, 0f,    0f,    0f, 0f,
                    0f,    0.7f, 0f,    0f, 0f,
                    0f,    0f,    0.7f, 0f, 0f,
                    0f,    0f,    0f,    1f, 0f
            });
            cm.postConcat(dim);
            holder.imgView.setColorFilter(new ColorMatrixColorFilter(cm));
            holder.imgView.setAlpha(0.75f);
            holder.tvCompletedBadge.setVisibility(View.VISIBLE);
            holder.tvMissionName.setAlpha(0.7f);
        } else {
            holder.imgView.clearColorFilter();
            holder.imgView.setAlpha(1f);
            holder.tvCompletedBadge.setVisibility(View.GONE);
            holder.tvMissionName.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() {
        return mAR.size();
    }

    @Override
    public ARAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        View contactView = inflater.inflate(R.layout.ar_item_layout, parent, false);

        ViewHolder viewHolder = new ViewHolder(contactView);

        return viewHolder;
    }

    public class ViewHolder extends RecyclerView.ViewHolder
    {
        TextView tvMissionName;
        CardView cardView;
        ShapeableImageView imgView;
        TextView tvCompletedBadge;

        public ViewHolder(View itemView) {
            super(itemView);

            tvMissionName    = itemView.findViewById(R.id.tvMissionName);
            cardView         = itemView.findViewById(R.id.cardView);
            imgView          = itemView.findViewById(R.id.imgView);
            tvCompletedBadge = itemView.findViewById(R.id.tvCompletedBadge);
        }
    }

    /**
     * Maps mission IDs to their corresponding drawable resources.
     */
    private int getMissionImageResource(String missionId) {
        switch (missionId) {
            case "fort_santiago":
                return R.drawable.fort_santiago;
            case "baluarte_san_diego":
                return R.drawable.baluarte_san_diego;
            case "casa_manila":
                return R.drawable.casa_manila;
            case "museo_intramuros":
                return R.drawable.museo_intramuros;
            case "centro_turismo":
                return R.drawable.centro_turismo;
            case "lpu":
                return R.drawable.lpu;
            default:
                return R.drawable.mission_icon;
        }
    }
}
