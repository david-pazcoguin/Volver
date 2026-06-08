package com.wheic.arapp;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText txtUsername, txtPassword;
    private CardView cardViewLogin, cardViewGoogle;
    private TextView tvRegister, tvForgotPassword;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::handleGoogleSignInResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        // Auto-login if session exists
        SharedPreferences sh = SecurePrefs.get(this);
        if (FirebaseAuth.getInstance().getCurrentUser() != null
                && !sh.getString("username", "").isEmpty()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null
                && !sh.getString("username", "").isEmpty()) {
            sh.edit().remove("username").remove("firstName").apply();
        }

        txtUsername = findViewById(R.id.txtUsername);
        txtPassword = findViewById(R.id.txtPassword);
        cardViewLogin = findViewById(R.id.cardViewLogin);
        cardViewGoogle = findViewById(R.id.cardViewGoogle);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // Start disabled
        setLoginEnabled(false);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable e) {
                setLoginEnabled(!txtUsername.getText().toString().isEmpty()
                        && !txtPassword.getText().toString().isEmpty());
            }
        };
        txtUsername.addTextChangedListener(watcher);
        txtPassword.addTextChangedListener(watcher);

        cardViewLogin.setOnClickListener(v -> attemptLogin());
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        cardViewGoogle.setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });
    }

    private void setLoginEnabled(boolean enabled) {
        cardViewLogin.setAlpha(enabled ? 1f : 0.35f);
        cardViewLogin.setClickable(enabled);
        cardViewLogin.setFocusable(enabled);
    }

    private void attemptLogin() {
        String accountInput = txtUsername.getText().toString().trim();
        boolean isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(accountInput).matches();
        if (!isEmail && !accountInput.matches("^[a-zA-Z0-9_]{3,30}$")) {
            Toast.makeText(this, "Enter a valid email or username.", Toast.LENGTH_SHORT).show();
            return;
        }
        String authEmail = isEmail ? accountInput : accountInput + "@volver.app";
        String password = txtPassword.getText().toString();

        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(authEmail, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null && isPasswordUser(user) && !user.isEmailVerified()) {
                            resendVerificationAndSignOut(user);
                            return;
                        }
                        loadProfileAndEnter(user, isEmail ? accountInput : accountInput);
                    } else {
                        Exception ex = task.getException();
                        if (ex instanceof FirebaseAuthInvalidUserException
                                || ex instanceof FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                        } else if (ex != null) {
                            String msg = ex.getMessage() != null ? ex.getMessage() : "";
                            if (msg.contains("network") || msg.contains("NETWORK")
                                    || msg.contains("connect") || msg.contains("timeout")) {
                                Toast.makeText(this, "Network error. Check your connection.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Invalid credentials.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private boolean isPasswordUser(FirebaseUser user) {
        if (user == null || user.getProviderData() == null) return false;
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    private void resendVerificationAndSignOut(FirebaseUser user) {
        String email = user.getEmail() != null ? user.getEmail() : "your email";
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    FirebaseAuth.getInstance().signOut();
                    SecurePrefs.get(this).edit().remove("username").remove("firstName").apply();
                    String message = task.isSuccessful()
                            ? "Please verify your email first. We sent a new verification link to " + email + "."
                            : "Please verify your email first. We could not resend the link right now.";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
    }

    private void loadProfileAndEnter(FirebaseUser user, String fallbackUsername) {
        if (user == null) {
            Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseConfig.getFirestore()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String username = doc != null ? doc.getString("username") : null;
                    String firstName = doc != null ? doc.getString("firstName") : null;
                    if (username == null || username.trim().isEmpty()) {
                        if (fallbackUsername != null && fallbackUsername.contains("@")) {
                            username = fallbackUsername.split("@")[0];
                        } else {
                            username = fallbackUsername != null ? fallbackUsername : "user";
                        }
                    }
                    SharedPreferences.Editor ed = SecurePrefs.get(this).edit()
                            .putString("username", username);
                    if (firstName != null) ed.putString("firstName", firstName);
                    ed.apply();
                    onLoginSuccess(username);
                })
                .addOnFailureListener(e -> onLoginSuccess(
                        fallbackUsername != null && fallbackUsername.contains("@")
                                ? fallbackUsername.split("@")[0]
                                : fallbackUsername));
    }

    private void handleGoogleSignInResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) return;
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Toast.makeText(this, "Google sign-in failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) return;
                    boolean isNewUser = task.getResult().getAdditionalUserInfo() != null
                            && task.getResult().getAdditionalUserInfo().isNewUser();
                    if (isNewUser) {
                        // Create Firestore profile for first-time Google users.
                        // Username = first part of their Google email (before @).
                        String googleEmail = user.getEmail() != null ? user.getEmail() : "";
                        String derivedUsername = googleEmail.contains("@")
                                ? googleEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_")
                                : "user_" + user.getUid().substring(0, 6);
                        String displayName = user.getDisplayName() != null ? user.getDisplayName() : derivedUsername;
                        String[] nameParts = displayName.split(" ", 2);
                        String firstName = nameParts[0];
                        String lastName = nameParts.length > 1 ? nameParts[1] : "";

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("username", derivedUsername);
                        userData.put("firstName", firstName);
                        userData.put("lastName", lastName);
                        userData.put("email", googleEmail);
                        userData.put("createdAt", FieldValue.serverTimestamp());
                        userData.put(FirebaseConfig.FIELD_LEADERBOARD_VISIBILITY,
                                LeaderboardRepository.VISIBILITY_PUBLIC);

                        FirebaseConfig.getFirestore()
                                .collection("users")
                                .document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(unused -> {
                                    SecurePrefs.get(this).edit()
                                            .putString("username", derivedUsername)
                                            .putString("firstName", firstName)
                                            .apply();
                                    onLoginSuccess(derivedUsername);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed to save profile.", Toast.LENGTH_SHORT).show());
                    } else {
                        // Returning Google user - fetch username from Firestore
                        loadProfileAndEnter(user, user.getEmail());
                    }
                });
    }

    private void showForgotPasswordDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        com.google.android.material.textfield.TextInputEditText etEmail =
                dialogView.findViewById(R.id.etResetEmail);

        android.app.Dialog dialog = new android.app.Dialog(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog);
        dialog.setContentView(dialogView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialogView.findViewById(R.id.btnSendReset).setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Enter a valid email address.");
                return;
            }
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> {
                        dialog.dismiss();
                        Toast.makeText(this, "Reset link sent! Check your inbox.", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Could not send reset email. Check the address and try again.",
                                    Toast.LENGTH_SHORT).show());
        });

        dialogView.findViewById(R.id.btnCancelReset).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void onLoginSuccess(String username) {
        if (username == null || username.trim().isEmpty()) {
            username = "user";
        }
        SecurePrefs.get(this).edit().putString("username", username).apply();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
