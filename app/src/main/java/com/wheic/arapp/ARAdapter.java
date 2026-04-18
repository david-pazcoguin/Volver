package com.wheic.arapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ARAdapter extends RecyclerView.Adapter<ARAdapter.ViewHolder>
{
    Context context;
    private List<ARHelper> mAR;

    public ARAdapter(List<ARHelper> ARs, Context context2)
    {
        mAR = ARs;
        context = context2;
        setHasStableIds(true);
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
        Glide.with(context)
                .load(imageResId)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(holder.imgView);

        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, ARActivity.class);

                Bundle bundle = new Bundle();
                bundle.putString("MissionName",      ARHelper.getMissionName());
                bundle.putString("Content",          ARHelper.getContent());
                bundle.putDouble("Latitude",         ARHelper.getLatitude());
                bundle.putDouble("Longitude",        ARHelper.getLongitude());
                bundle.putString("MissionId",        ARHelper.getMissionId());
                bundle.putString("CharacterName",    ARHelper.getCharacterName());
                bundle.putString("CharacterDialogue",ARHelper.getCharacterDialogue());
                bundle.putString("ModelFileName",    ARHelper.getModelFileName());

                intent.putExtras(bundle);
                ((Activity) context).startActivity(intent);
            }
        });
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

        public ViewHolder(View itemView) {
            super(itemView);

            tvMissionName = itemView.findViewById(R.id.tvMissionName);
            cardView = itemView.findViewById(R.id.cardView);
            imgView = itemView.findViewById(R.id.imgView);
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
            case "lyceum_philippines":
                return R.drawable.mission_icon;
            default:
                return R.drawable.mission_icon;
        }
    }
}
