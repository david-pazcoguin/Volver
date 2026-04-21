package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
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
import com.google.firebase.firestore.FieldValue;

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

        PasswordToggleHelper.attach(tvShowHide, txtPassword);
        PasswordToggleHelper.attach(tvConfirmShowHide, txtConfirmPassword);

        linearLayoutBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        TextWatcher registerWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                RegisterButtonWatcher();
            }
        };

        txtFirstName.addTextChangedListener(registerWatcher);
        txtLastName.addTextChangedListener(registerWatcher);
        txtUsername.addTextChangedListener(registerWatcher);
        txtPassword.addTextChangedListener(registerWatcher);
        txtConfirmPassword.addTextChangedListener(registerWatcher);

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
        String password = txtPassword.getText().toString();
        String firstName = txtFirstName.getText().toString().trim();
        String lastName = txtLastName.getText().toString().trim();

        // Username validation: 3-30 alphanumeric/underscore characters
        if (!username.matches("^[a-zA-Z0-9_]{3,30}$")) {
            Toast.makeText(this, "Username must be 3–30 characters (letters, numbers, underscore).", Toast.LENGTH_SHORT).show();
            return;
        }

        // Password strength: minimum 6 characters (Firebase Auth minimum)
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Name length limits
        if (firstName.length() > 50 || lastName.length() > 50) {
            Toast.makeText(this, "Name fields must be 50 characters or fewer.", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = username + "@volver.app";

        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                        if (user == null) {
                            Toast.makeText(RegisterActivity.this, "Registration failed. Please try again.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("username", username);
                        userData.put("firstName", firstName);
                        userData.put("lastName", lastName);
                        userData.put("email", email);
                        userData.put("createdAt", FieldValue.serverTimestamp());

                        FirebaseConfig.getFirestore()
                                .collection("users")
                                .document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(unused -> finish())
                                .addOnFailureListener(e -> Toast.makeText(RegisterActivity.this, "Failed to save profile.", Toast.LENGTH_SHORT).show());
                    } else {
                        Exception exception = task.getException();
                        String msg = "Registration failed.";
                        if (exception != null && exception.getMessage() != null
                                && exception.getMessage().contains("already in use")) {
                            msg = "Username is already taken.";
                        }
                        Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}