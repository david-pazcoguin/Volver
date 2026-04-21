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
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

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

        SharedPreferences sh = SecurePrefs.get(this);

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

        PasswordToggleHelper.attach(tvShowHide, txtPassword);

        TextWatcher loginWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                LoginChecker();
            }
        };

        txtUsername.addTextChangedListener(loginWatcher);
        txtPassword.addTextChangedListener(loginWatcher);

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
                if (!usernameInput.matches("^[a-zA-Z0-9_]{3,30}$")) {
                    Toast.makeText(LoginActivity.this, "Invalid username format.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String authEmail = usernameInput + "@volver.app";
                String password = txtPassword.getText().toString();

                FirebaseAuth.getInstance()
                        .signInWithEmailAndPassword(authEmail, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                SharedPreferences sharedPreferences = SecurePrefs.get(LoginActivity.this);
                                SharedPreferences.Editor myEdit = sharedPreferences.edit();
                                myEdit.putString("username", txtUsername.getText().toString());
                                myEdit.apply();

                                Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                Exception exception = task.getException();
                                if (exception instanceof FirebaseAuthInvalidUserException
                                        || exception instanceof FirebaseAuthInvalidCredentialsException) {
                                    Toast.makeText(LoginActivity.this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                                } else if (exception != null) {
                                    String msg = exception.getMessage();
                                    if (msg != null && (msg.contains("network") || msg.contains("NETWORK")
                                            || msg.contains("connect") || msg.contains("timeout"))) {
                                        Toast.makeText(LoginActivity.this, "Network error. Check your connection.", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(LoginActivity.this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
                                    }
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