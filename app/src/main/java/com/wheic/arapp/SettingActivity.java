package com.wheic.arapp;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

import androidx.cardview.widget.CardView;

public class SettingActivity extends AppCompatActivity {

    private LinearLayout linearLayoutBack;
    private MaterialCardView cardViewLogout;
    private CardView cardViewUpdate;
    private TextInputEditText txtFirstName, txtLastName, txtPassword, txtConfirmPassword;
    private TextView tvFullName, tvUsernameLabel, tvAvatarInitial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting_activity);

        linearLayoutBack   = findViewById(R.id.linearLayoutBack);
        cardViewLogout     = findViewById(R.id.cardViewLogout);
        cardViewUpdate     = findViewById(R.id.cardViewUpdate);
        txtFirstName       = findViewById(R.id.txtFirstName);
        txtLastName        = findViewById(R.id.txtLastName);
        txtPassword        = findViewById(R.id.txtPassword);
        txtConfirmPassword = findViewById(R.id.txtConfirmPassword);
        tvFullName         = findViewById(R.id.tvFullName);
        tvUsernameLabel    = findViewById(R.id.tvUsernameLabel);
        tvAvatarInitial    = findViewById(R.id.tvAvatarInitial);

        // Disable update button initially — enabled only when name fields have content
        setUpdateEnabled(false);

        linearLayoutBack.setOnClickListener(v -> finish());

        cardViewLogout.setOnClickListener(v -> showLogoutDialog());

        cardViewUpdate.setOnClickListener(v -> performUpdate());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable e) { refreshUpdateButton(); }
        };
        txtFirstName.addTextChangedListener(watcher);
        txtLastName.addTextChangedListener(watcher);
        txtPassword.addTextChangedListener(watcher);
        txtConfirmPassword.addTextChangedListener(watcher);

        loadAccount();
    }

    // ── Load profile from Firestore ───────────────────────────
    private void loadAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseConfig.getFirestore()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    String firstName = doc.getString("firstName");
                    String lastName  = doc.getString("lastName");
                    String username  = doc.getString("username");

                    if (firstName == null) firstName = "";
                    if (lastName  == null) lastName  = "";

                    txtFirstName.setText(firstName);
                    txtLastName.setText(lastName);

                    String fullName = (firstName + " " + lastName).trim();
                    tvFullName.setText(fullName.isEmpty() ? "My Profile" : fullName);

                    if (username != null && !username.isEmpty()) {
                        tvUsernameLabel.setText("@" + username);
                    }

                    // Avatar initial
                    String initial = firstName.isEmpty() ? "?" : String.valueOf(firstName.charAt(0)).toUpperCase();
                    tvAvatarInitial.setText(initial);

                    refreshUpdateButton();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show());
    }

    // ── Save name + optional password ────────────────────────
    private void performUpdate() {
        String first   = txtFirstName.getText() != null ? txtFirstName.getText().toString().trim() : "";
        String last    = txtLastName.getText()  != null ? txtLastName.getText().toString().trim()  : "";
        String pass    = txtPassword.getText()  != null ? txtPassword.getText().toString()         : "";
        String confirm = txtConfirmPassword.getText() != null ? txtConfirmPassword.getText().toString() : "";

        if (first.isEmpty() || last.isEmpty()) {
            Toast.makeText(this, "First and last name are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean changePassword = !pass.isEmpty();
        if (changePassword && !pass.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (changePassword && pass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        setUpdateEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", first);
        updates.put("lastName", last);

        FirebaseConfig.getFirestore()
                .collection("users")
                .document(user.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    if (changePassword) {
                        user.updatePassword(pass)
                                .addOnSuccessListener(unused2 -> onUpdateSuccess())
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Profile saved, but password update failed.", Toast.LENGTH_LONG).show();
                                    setUpdateEnabled(true);
                                });
                    } else {
                        onUpdateSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update profile.", Toast.LENGTH_SHORT).show();
                    setUpdateEnabled(true);
                });
    }

    private void onUpdateSuccess() {
        Toast.makeText(this, "Profile updated.", Toast.LENGTH_SHORT).show();
        txtPassword.setText("");
        txtConfirmPassword.setText("");
        loadAccount();
    }

    // ── Logout ────────────────────────────────────────────────
    private void showLogoutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(R.layout.logout_confirmation_layout);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        int dialogWidth = (int) Math.min(
                getResources().getDisplayMetrics().widthPixels * 0.9f,
                getResources().getDisplayMetrics().density * 420f);
        dialog.getWindow().setLayout(dialogWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams wlp = dialog.getWindow().getAttributes();
        wlp.gravity = Gravity.CENTER;
        dialog.getWindow().setAttributes(wlp);

        dialog.findViewById(R.id.cardViewNo).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.cardViewYes).setOnClickListener(v -> {
            dialog.dismiss();
            performLogout();
        });
        dialog.show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        SecurePrefs.get(this).edit()
                .remove("username")
                .remove("firstName")
                .apply();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignIn.getClient(this, gso).signOut()
                .addOnCompleteListener(task -> {
                    finishAffinity();
                    startActivity(new Intent(this, LoginActivity.class));
                });
    }

    // ── Helper ────────────────────────────────────────────────
    private void refreshUpdateButton() {
        String first = txtFirstName.getText() != null ? txtFirstName.getText().toString().trim() : "";
        String last  = txtLastName.getText()  != null ? txtLastName.getText().toString().trim()  : "";
        setUpdateEnabled(!first.isEmpty() && !last.isEmpty());
    }

    private void setUpdateEnabled(boolean enabled) {
        cardViewUpdate.setAlpha(enabled ? 1f : 0.4f);
        cardViewUpdate.setClickable(enabled);
        cardViewUpdate.setFocusable(enabled);
    }
}
