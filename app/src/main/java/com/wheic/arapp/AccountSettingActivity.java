package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class AccountSettingActivity extends AppCompatActivity {

    LinearLayout linearLayoutBack;
    EditText txtFirstName, txtLastName, txtPassword, txtConfirmPassword;

    TextView tvShowHide, tvConfirmShowHide;
    CardView cardViewUpdate;
    String Username;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setting_activity);

        SharedPreferences sh = SecurePrefs.get(this);

        Username = sh.getString("username", "");

        linearLayoutBack = findViewById(R.id.linearLayoutBack);
        txtFirstName = findViewById(R.id.txtFirstName);
        txtLastName = findViewById(R.id.txtLastName);
        txtPassword = findViewById(R.id.txtPassword);
        txtConfirmPassword = findViewById(R.id.txtConfirmPassword);
        tvShowHide = findViewById(R.id.tvShowHide);
        tvConfirmShowHide = findViewById(R.id.tvConfirmShowHide);
        cardViewUpdate = findViewById(R.id.cardViewUpdate);

        PasswordToggleHelper.attach(tvShowHide, txtPassword);
        PasswordToggleHelper.attach(tvConfirmShowHide, txtConfirmPassword);

        linearLayoutBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        TextWatcher updateWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                RegisterButtonWatcher();
            }
        };

        txtFirstName.addTextChangedListener(updateWatcher);
        txtLastName.addTextChangedListener(updateWatcher);
        txtPassword.addTextChangedListener(updateWatcher);
        txtConfirmPassword.addTextChangedListener(updateWatcher);

        cardViewUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(txtPassword.getText().toString().equals(txtConfirmPassword.getText().toString()))
                {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) {
                        Toast.makeText(AccountSettingActivity.this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> profileUpdates = new HashMap<>();
                    profileUpdates.put("firstName", txtFirstName.getText().toString());
                    profileUpdates.put("lastName", txtLastName.getText().toString());

                    FirebaseConfig.getFirestore()
                            .collection("users")
                            .document(user.getUid())
                            .update(profileUpdates)
                            .addOnSuccessListener(unused -> user.updatePassword(txtPassword.getText().toString())
                                    .addOnSuccessListener(unused2 -> {
                                        Intent intent= new Intent(AccountSettingActivity.this, HomeActivity.class);
                                        startActivity(intent);
                                        finishAffinity();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(AccountSettingActivity.this, "Failed to update password.", Toast.LENGTH_SHORT).show()))
                            .addOnFailureListener(e -> Toast.makeText(AccountSettingActivity.this, "Failed to update profile.", Toast.LENGTH_SHORT).show());
                }
                else
                {
                    Toast.makeText(AccountSettingActivity.this, "Password and Confirm Password must be the same.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        LoadAccount();
    }

    void LoadAccount()
    {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(AccountSettingActivity.this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseConfig.getFirestore()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String username = documentSnapshot.getString("username");
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");

                        if (username != null) {
                            Username = username;
                        }

                        txtFirstName.setText(firstName != null ? firstName : "");
                        txtLastName.setText(lastName != null ? lastName : "");
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(AccountSettingActivity.this, "Failed to load account.", Toast.LENGTH_SHORT).show());
    }

    void RegisterButtonWatcher()
    {
        if(txtFirstName.getText().toString().isEmpty() ||
                txtLastName.getText().toString().isEmpty() ||
                txtPassword.getText().toString().isEmpty() ||
                txtConfirmPassword.getText().toString().isEmpty())
        {
            cardViewUpdate.setAlpha(0.2f);
            cardViewUpdate.setFocusable(false);
            cardViewUpdate.setClickable(false);
        }
        else
        {
            cardViewUpdate.setAlpha(1);
            cardViewUpdate.setFocusable(true);
            cardViewUpdate.setClickable(true);
        }
    }
}