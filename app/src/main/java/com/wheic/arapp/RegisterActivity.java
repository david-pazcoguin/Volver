package com.wheic.arapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private CardView cardViewRegister;
    private TextInputEditText txtFirstName, txtLastName, txtEmail, txtUsername,
                               txtPassword, txtConfirmPassword;
    private TextView tvLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_activity);

        cardViewRegister  = findViewById(R.id.cardViewRegister);
        txtFirstName      = findViewById(R.id.txtFirstName);
        txtLastName       = findViewById(R.id.txtLastName);
        txtEmail          = findViewById(R.id.txtEmail);
        txtUsername       = findViewById(R.id.txtUsername);
        txtPassword       = findViewById(R.id.txtPassword);
        txtConfirmPassword = findViewById(R.id.txtConfirmPassword);
        tvLogin           = findViewById(R.id.tvLogin);

        // Back button in header
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvLogin.setOnClickListener(v -> finish());

        setRegisterEnabled(false);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override
            public void afterTextChanged(Editable e) {
                setRegisterEnabled(allFieldsFilled());
            }
        };
        txtFirstName.addTextChangedListener(watcher);
        txtLastName.addTextChangedListener(watcher);
        txtEmail.addTextChangedListener(watcher);
        txtUsername.addTextChangedListener(watcher);
        txtPassword.addTextChangedListener(watcher);
        txtConfirmPassword.addTextChangedListener(watcher);

        cardViewRegister.setOnClickListener(v -> {
            String pw = txtPassword.getText() != null ? txtPassword.getText().toString() : "";
            String cpw = txtConfirmPassword.getText() != null ? txtConfirmPassword.getText().toString() : "";
            if (!pw.equals(cpw)) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
                return;
            }
            register();
        });
    }

    private boolean allFieldsFilled() {
        return nonEmpty(txtFirstName) && nonEmpty(txtLastName) && nonEmpty(txtEmail)
                && nonEmpty(txtUsername) && nonEmpty(txtPassword) && nonEmpty(txtConfirmPassword);
    }

    private boolean nonEmpty(TextInputEditText et) {
        return et.getText() != null && !et.getText().toString().isEmpty();
    }

    private void setRegisterEnabled(boolean enabled) {
        cardViewRegister.setAlpha(enabled ? 1f : 0.35f);
        cardViewRegister.setClickable(enabled);
        cardViewRegister.setFocusable(enabled);
        cardViewRegister.setEnabled(enabled);
    }

    private void register() {
        String firstName = txtFirstName.getText().toString().trim();
        String lastName  = txtLastName.getText().toString().trim();
        String email     = txtEmail.getText().toString().trim();
        String username  = txtUsername.getText().toString().trim();
        String password  = txtPassword.getText().toString();

        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email address.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate username
        if (!username.matches("^[a-zA-Z0-9_]{3,30}$")) {
            Toast.makeText(this, "Username must be 3-30 characters (letters, numbers, underscore).", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate password strength
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Name length
        if (firstName.length() > 50 || lastName.length() > 50) {
            Toast.makeText(this, "Name fields must be 50 characters or fewer.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Register with the real email (not the fake @volver.app one)
        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        String msg = "Registration failed.";
                        if (ex != null && ex.getMessage() != null
                                && ex.getMessage().contains("already in use")) {
                            msg = "That email is already registered.";
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                    if (user == null) {
                        Toast.makeText(this, "Registration failed. Please try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Save profile to Firestore
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("username", username);
                    userData.put("firstName", firstName);
                    userData.put("lastName", lastName);
                    userData.put("email", email);
                    userData.put("createdAt", FieldValue.serverTimestamp());
                    userData.put(FirebaseConfig.FIELD_LEADERBOARD_VISIBILITY,
                            LeaderboardRepository.VISIBILITY_PUBLIC);

                    FirebaseConfig.getFirestore()
                            .collection("users")
                            .document(user.getUid())
                            .set(userData)
                            .addOnSuccessListener(unused -> {
                                SecurePrefs.get(this).edit()
                                        .putString("firstName", firstName)
                                        .apply();
                                // Send verification email
                                user.sendEmailVerification()
                                        .addOnSuccessListener(verifyTask -> showVerificationDialog(email))
                                        .addOnFailureListener(e -> showVerificationFailureDialog(user, email));
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to save profile.", Toast.LENGTH_SHORT).show());
                });
    }

    private void showVerificationDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Verify your email")
                .setMessage("We sent a verification link to:\n\n" + email
                        + "\n\nPlease check your inbox and verify before logging in.")
                .setPositiveButton("OK", (d, w) -> {
                    // Sign out so they are forced to log in after verifying
                    FirebaseAuth.getInstance().signOut();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showVerificationFailureDialog(FirebaseUser user, String email) {
        new AlertDialog.Builder(this)
                .setTitle("Verification email not sent")
                .setMessage("We created your account, but Firebase could not send the verification link yet. Please try sending it again.")
                .setPositiveButton("Resend", (d, w) -> user.sendEmailVerification()
                        .addOnSuccessListener(unused -> showVerificationDialog(email))
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Still could not send the email. Please try again later.", Toast.LENGTH_LONG).show()))
                .setNegativeButton("Back to login", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
