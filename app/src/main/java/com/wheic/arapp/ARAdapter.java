package com.wheic.arapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                Intent intent= new Intent(context, ARActivity.class);

                Bundle bundle = new Bundle();
                bundle.putString("MissionName", ARHelper.getMissionName());
                bundle.putString("Content", ARHelper.getContent());
                bundle.putDouble("Latitude", ARHelper.getLatitude());
                bundle.putDouble("Longitude", ARHelper.getLongitude());

                intent.putExtras(bundle);

                ((Activity)context).startActivity(intent);
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
