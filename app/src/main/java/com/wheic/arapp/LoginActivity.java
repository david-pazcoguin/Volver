package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    EditText txtUsername, txtPassword;
    CardView cardViewLogin;
    TextView tvRegister;
    TextView tvShowHide;
    String Username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        SharedPreferences sh = getSharedPreferences("Volver", Context.MODE_PRIVATE);

        Username = sh.getString("username", "");

        if(!Username.isEmpty())
        {
            Intent intent= new Intent(LoginActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        }

        txtUsername = findViewById(R.id.txtUsername);
        txtPassword = findViewById(R.id.txtPassword);
        cardViewLogin = findViewById(R.id.cardViewLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvShowHide = findViewById(R.id.tvShowHide);

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

        txtUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                LoginChecker();
            }
        });

        txtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                LoginChecker();
            }

            @Override
            public void afterTextChanged(Editable editable) {
                LoginChecker();
            }
        });

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent= new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        cardViewLogin.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                String usernameInput = txtUsername.getText().toString().trim();
                String authEmail = usernameInput + "@volver.app";
                String password = txtPassword.getText().toString();

                FirebaseAuth.getInstance()
                        .signInWithEmailAndPassword(authEmail, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                SharedPreferences sharedPreferences = getSharedPreferences("Volver", MODE_PRIVATE);
                                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                                myEdit.putString("username", txtUsername.getText().toString());
                                myEdit.commit();

                                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                                startActivity(intent);
                            } else {
                                Exception exception = task.getException();
                                if (exception instanceof FirebaseAuthInvalidUserException
                                        || exception instanceof FirebaseAuthInvalidCredentialsException) {
                                    Toast.makeText(LoginActivity.this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                                } else if (exception != null) {
                                    Toast.makeText(LoginActivity.this, exception.toString(), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(LoginActivity.this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }

    void LoginChecker()
    {
        if(txtPassword.getText().toString().isEmpty() || txtUsername.getText().toString().isEmpty())
        {
            cardViewLogin.setAlpha(0.2f);
            cardViewLogin.setClickable(false);
            cardViewLogin.setFocusable(false);
        }
        else
        {
            cardViewLogin.setAlpha(1);
            cardViewLogin.setClickable(true);
            cardViewLogin.setFocusable(true);
        }
    }
}