package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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

        SharedPreferences sh = getSharedPreferences("Volver", Context.MODE_PRIVATE);

        Username = sh.getString("username", "");

        linearLayoutBack = findViewById(R.id.linearLayoutBack);
        txtFirstName = findViewById(R.id.txtFirstName);
        txtLastName = findViewById(R.id.txtLastName);
        txtPassword = findViewById(R.id.txtPassword);
        txtConfirmPassword = findViewById(R.id.txtConfirmPassword);
        tvShowHide = findViewById(R.id.tvShowHide);
        tvConfirmShowHide = findViewById(R.id.tvConfirmShowHide);
        cardViewUpdate = findViewById(R.id.cardViewUpdate);

        tvShowHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(tvShowHide.getText().toString().equals("SHOW"))
                {
                    tvShowHide.setText("HIDE");
                    txtPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
                else
                {
                    tvShowHide.setText("SHOW");
                    txtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
            }
        });

        tvConfirmShowHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(tvConfirmShowHide.getText().toString().equals("SHOW"))
                {
                    tvConfirmShowHide.setText("HIDE");
                    txtConfirmPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
                else
                {
                    tvConfirmShowHide.setText("SHOW");
                    txtConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
            }
        });

        linearLayoutBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        txtFirstName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                RegisterButtonWatcher();
            }
        });
        txtLastName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                RegisterButtonWatcher();
            }
        });
        txtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                RegisterButtonWatcher();
            }
        });
        txtConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                RegisterButtonWatcher();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                RegisterButtonWatcher();
            }
        });

        cardViewUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(txtPassword.getText().toString().equals(txtConfirmPassword.getText().toString()))
                {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) {
                        Toast.makeText(AccountSettingActivity.this, new IllegalStateException("No authenticated user").toString(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> profileUpdates = new HashMap<>();
                    profileUpdates.put("firstName", txtFirstName.getText().toString());
                    profileUpdates.put("lastName", txtLastName.getText().toString());

                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(user.getUid())
                            .update(profileUpdates)
                            .addOnSuccessListener(unused -> user.updatePassword(txtPassword.getText().toString())
                                    .addOnSuccessListener(unused2 -> {
                                        Intent intent= new Intent(AccountSettingActivity.this, HomeActivity.class);
                                        startActivity(intent);
                                        finishAffinity();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(AccountSettingActivity.this, e.toString(), Toast.LENGTH_SHORT).show()))
                            .addOnFailureListener(e -> Toast.makeText(AccountSettingActivity.this, e.toString(), Toast.LENGTH_SHORT).show());
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
            Toast.makeText(AccountSettingActivity.this, new IllegalStateException("No authenticated user").toString(), Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
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
                .addOnFailureListener(e -> Toast.makeText(AccountSettingActivity.this, e.toString(), Toast.LENGTH_SHORT).show());
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