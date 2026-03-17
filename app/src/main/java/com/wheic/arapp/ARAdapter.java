package com.wheic.arapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(ARAdapter.ViewHolder holder, int position)
    {
        ARHelper ARHelper = mAR.get(position);

        holder.tvMissionName.setText(ARHelper.getMissionName());

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
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
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

        public ViewHolder(View itemView) {
            super(itemView);

            tvMissionName = itemView.findViewById(R.id.tvMissionName);
            cardView = itemView.findViewById(R.id.cardView);

        }
    }
}
