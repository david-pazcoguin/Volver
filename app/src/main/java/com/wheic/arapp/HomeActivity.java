package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    ImageView imgDashboard;
    List<ARHelper> arHelpers;
    ARAdapter arAdapter;

    RecyclerView recyclerView;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        imgDashboard = findViewById(R.id.imgDashboard);

        imgDashboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog dialog = new Dialog(HomeActivity.this);

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setContentView(R.layout.dashboard_layout);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                Window window = dialog.getWindow();
                WindowManager.LayoutParams wlp = window.getAttributes();

                wlp.gravity = Gravity.BOTTOM;

                window.setAttributes(wlp);

                LinearLayout linearLayoutSetting, linearLayoutAboutUs;

                linearLayoutSetting = dialog.findViewById(R.id.linearLayoutSetting);
                linearLayoutAboutUs = dialog.findViewById(R.id.linearLayoutAboutUs);

                linearLayoutSetting.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent= new Intent(HomeActivity.this, SettingActivity.class);
                        startActivity(intent);
                    }
                });
                linearLayoutAboutUs.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent= new Intent(HomeActivity.this, AboutUsActivity.class);
                        startActivity(intent);
                    }
                });

                dialog.show();
            }
        });

        arHelpers = new ArrayList<>();
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setHasFixedSize(true);

        arAdapter = new ARAdapter(arHelpers, this);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(linearLayoutManager);

        arHelpers.add(new ARHelper("Fort Santiago","",14.6367, 121.0028, R.raw.san_bartolome_church));
        arHelpers.add(new ARHelper("San Agustin Church","",14.590278, 120.975556, R.raw.san_bartolome_church));
        arHelpers.add(new ARHelper("Manila Cathedral","",14.592222, 120.973611, R.raw.san_bartolome_church));
        arHelpers.add(new ARHelper("Casa Manila Museum","",14.590000, 120.975278, R.raw.san_bartolome_church));
        arHelpers.add(new ARHelper("Baluarte de San Diego","",14.587778, 120.971667, R.raw.san_bartolome_church));

        recyclerView.setAdapter(arAdapter);
    }
}