package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingActivity extends AppCompatActivity {

    LinearLayout linearLayoutBack, linearLayoutLogout, linearLayoutMyAccount;
    String Username;
    TextView tvFullName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_activity);

        linearLayoutBack = findViewById(R.id.linearLayoutBack);
        tvFullName = findViewById(R.id.tvFullName);
        linearLayoutLogout = findViewById(R.id.linearLayoutLogout);
        linearLayoutMyAccount = findViewById(R.id.linearLayoutMyAccount);

        SharedPreferences sh = getSharedPreferences("Volver", Context.MODE_PRIVATE);

        Username = sh.getString("username", "");

        linearLayoutBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        linearLayoutLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog dialog = new Dialog(SettingActivity.this);

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setContentView(R.layout.logout_confirmation_layout);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                Window window = dialog.getWindow();
                WindowManager.LayoutParams wlp = window.getAttributes();

                wlp.gravity = Gravity.BOTTOM;

                window.setAttributes(wlp);

                CardView cardViewNo, cardViewYes;

                cardViewNo = dialog.findViewById(R.id.cardViewNo);
                cardViewYes = dialog.findViewById(R.id.cardViewYes);

                cardViewNo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

                cardViewYes.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();

                        FirebaseAuth.getInstance().signOut();

                        SharedPreferences settings = getSharedPreferences("Volver", Context.MODE_PRIVATE);
                        settings.edit().clear().commit();

                        finishAffinity();

                        Intent intent= new Intent(SettingActivity.this, LoginActivity.class);
                        startActivity(intent);
                    }
                });
                dialog.show();
            }
        });

        linearLayoutMyAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(SettingActivity.this, AccountSettingActivity.class);
                startActivity(intent);
            }
        });

        LoadAccount();
    }

    void LoadAccount()
    {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(SettingActivity.this, "User session expired.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String username = documentSnapshot.getString("username");
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");

                        if (username != null) {
                            Username = username;
                        }

                        if (firstName == null) {
                            firstName = "";
                        }
                        if (lastName == null) {
                            lastName = "";
                        }

                        tvFullName.setText((firstName + " " + lastName).trim());
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(SettingActivity.this, e.toString(), Toast.LENGTH_SHORT).show());
    }
}