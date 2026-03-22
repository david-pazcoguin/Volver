package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * This activity handles user registration.
 * It allows new users to create an account by providing their first name, last name, a unique username, and a password.
 * The activity performs validation to ensure that all fields are filled and that the entered passwords match.
 * It also communicates with a server to verify the uniqueness of the username and to submit the registration details.
 */
public class RegisterActivity extends AppCompatActivity {

    LinearLayout linearLayoutBack;
    CardView cardViewRegister;

    EditText txtFirstName, txtLastName, txtUsername, txtPassword, txtConfirmPassword;
    TextView tvShowHide, tvConfirmShowHide;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_activity);

        linearLayoutBack = findViewById(R.id.linearLayoutBack);
        cardViewRegister = findViewById(R.id.cardViewRegister);
        txtFirstName = findViewById(R.id.txtFirstName);
        txtLastName = findViewById(R.id.txtLastName);
        txtUsername = findViewById(R.id.txtUsername);
        txtPassword = findViewById(R.id.txtPassword);
        txtConfirmPassword = findViewById(R.id.txtConfirmPassword);
        tvShowHide = findViewById(R.id.tvShowHide);
        tvConfirmShowHide = findViewById(R.id.tvConfirmShowHide);

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
        txtUsername.addTextChangedListener(new TextWatcher() {
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

        cardViewRegister.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(txtPassword.getText().toString().equals(txtConfirmPassword.getText().toString()))
                {
                    Register();
                }
                else
                {
                    Toast.makeText(RegisterActivity.this, "Password and Confirm Password must be the same.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * This method monitors the input fields for the registration form.
     * It enables or disables the register button based on whether all the required fields have been filled.
     * If any field is empty, the register button is made inactive.
     */
    void RegisterButtonWatcher()
    {
        if(txtUsername.getText().toString().isEmpty() ||
                txtFirstName.getText().toString().isEmpty() ||
                txtLastName.getText().toString().isEmpty() ||
                txtPassword.getText().toString().isEmpty() ||
                txtConfirmPassword.getText().toString().isEmpty())
        {
            cardViewRegister.setAlpha(0.2f);
            cardViewRegister.setFocusable(false);
            cardViewRegister.setClickable(false);
            cardViewRegister.setEnabled(false);
        }
        else
        {
            cardViewRegister.setAlpha(1);
            cardViewRegister.setFocusable(true);
            cardViewRegister.setClickable(true);
            cardViewRegister.setEnabled(true);
        }
    }

    /**
     * This method handles the actual registration process.
     * It sends the user's registration details (username, password, first name, last name) to the server.
     * Upon a successful response from the server, it closes the registration activity.
     */
    void Register()
    {
        String username = txtUsername.getText().toString().trim();
        String email = username + "@volver.app";
        String password = txtPassword.getText().toString();

        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                        if (user == null) {
                            Toast.makeText(RegisterActivity.this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("username", username);
                        userData.put("firstName", txtFirstName.getText().toString());
                        userData.put("lastName", txtLastName.getText().toString());
                        userData.put("email", email);
                        userData.put("createdAt", FieldValue.serverTimestamp());

                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(unused -> finish())
                                .addOnFailureListener(e -> Toast.makeText(RegisterActivity.this, e.toString(), Toast.LENGTH_SHORT).show());
                    } else {
                        Exception exception = task.getException();
                        if (exception != null) {
                            Toast.makeText(RegisterActivity.this, exception.toString(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}