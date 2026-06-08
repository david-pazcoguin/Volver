package com.wheic.arapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/**
 * NFT claim screen — shown after all missions are complete and the wallet is set up.
 *
 * Path A (current): mint is performed server-side by the mintSouvenir Cloud Function,
 * which signs the transaction with the owner wallet. The user pays zero gas and never
 * submits a transaction.
 *
 * Path B (future): replace WalletManager with Thirdweb In-App Wallet (Google sign-in),
 * and either keep this CF path or switch to Thirdweb Engine gasless mint directly.
 */
public class NFTClaimActivity extends AppCompatActivity {

    private static final long DEBOUNCE_MILLIS = 2000;

    private TextView    tvWalletAddress, tvMintStatus;
    private Button      btnMintNFT;
    private ProgressBar progressMint;
    private LinearLayout layoutMintActions;
    private Button       btnViewTx, btnViewWallet, btnViewOpenSea;

    private WalletManager walletManager;
    private long lastMintClickTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Prevent screenshots (wallet address and transaction data visible)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.nft_claim_activity);

        walletManager = WalletManager.getInstance(this);

        ImageView imgBack = findViewById(R.id.imgBack);
        imgBack.setOnClickListener(v -> finish());

        tvWalletAddress = findViewById(R.id.tvWalletAddress);
        tvMintStatus    = findViewById(R.id.tvMintStatus);
        btnMintNFT      = findViewById(R.id.btnMintNFT);
        progressMint    = findViewById(R.id.progressMint);
        layoutMintActions = findViewById(R.id.layoutMintActions);
        btnViewTx       = findViewById(R.id.btnViewTx);
        btnViewWallet   = findViewById(R.id.btnViewWallet);
        btnViewOpenSea  = findViewById(R.id.btnViewOpenSea);

        // Wallet-level links work even before we know the tx hash.
        String wallet = walletManager.getWalletAddress();
        btnViewWallet.setOnClickListener(v ->
                openUrl(PolygonService.getPolygonScanAddressUrl(wallet)));
        btnViewOpenSea.setOnClickListener(v ->
                openUrl(PolygonService.getOpenSeaUrl(wallet)));

        // Guard: wallet must be set up before reaching this screen
        if (!walletManager.hasWallet()) {
            startActivity(new Intent(this, WalletSetupActivity.class));
            finish();
            return;
        }

        tvWalletAddress.setText(walletManager.getWalletAddress());

        // If we've already claimed locally, reflect that immediately.
        if (SecurePrefs.get(this).getBoolean("nft_claimed", false)) {
            btnMintNFT.setEnabled(false);
            btnMintNFT.setText("Minted ✓");
            btnMintNFT.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF2E7D32));
            tvMintStatus.setVisibility(View.VISIBLE);
            tvMintStatus.setText("Your Volver Heritage Souvenir has already been minted to this wallet.");
            // Show wallet-level links (tx hash is not persisted across launches).
            btnViewTx.setVisibility(View.GONE);
            layoutMintActions.setVisibility(View.VISIBLE);
        }

        btnMintNFT.setOnClickListener(v -> startMinting());
    }

    // ──────────────────────────────────────────────────────────────
    // Minting logic — Cloud Function sponsored mint (gasless to user)
    // ──────────────────────────────────────────────────────────────

    private void startMinting() {
        long now = System.currentTimeMillis();
        if (now - lastMintClickTime < DEBOUNCE_MILLIS) return;
        lastMintClickTime = now;

        if (!NetworkUtils.isConnected(this)) {
            Toast.makeText(this, "NFT minting requires an internet connection.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("Please sign in again before claiming your souvenir.");
            return;
        }

        String address = walletManager.getWalletAddress();
        if (address == null || address.isEmpty()) {
            showError("Wallet not found. Please complete wallet setup.");
            return;
        }

        btnMintNFT.setEnabled(false);
        progressMint.setVisibility(View.VISIBLE);
        tvMintStatus.setVisibility(View.VISIBLE);
        tvMintStatus.setText("Saving wallet...");

        // Ensure the server-side profile has our walletAddress before minting.
        // The CF rejects the mint if the stored walletAddress is missing or
        // mismatched, so we always refresh it right before the call.
        MissionCompletionHelper.saveWalletAddress(this, address,
                new MissionCompletionHelper.CompletionCallback() {
                    @Override public void onSuccess() { callMintFunction(user.getUid(), address); }
                    @Override public void onError(String msg) {
                        // Not fatal — try minting anyway; server will return a clear error if it mismatches.
                        callMintFunction(user.getUid(), address);
                    }
                });
    }

    private void callMintFunction(String uid, String address) {
        tvMintStatus.setText("Minting your souvenir on Polygon...");

        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);
        payload.put("walletAddress", address);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("mintSouvenir")
                .call(payload)
                .addOnSuccessListener(result -> {
                    String txHash = null;
                    Object data = result.getData();
                    if (data instanceof Map) {
                        Object hash = ((Map<?, ?>) data).get("txHash");
                        if (hash instanceof String) txHash = (String) hash;
                    }
                    showSuccess(txHash);
                })
                .addOnFailureListener(e -> {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e);
                    String msg = (e.getMessage() != null) ? e.getMessage() : "Unknown error";
                    // Detect "already minted" server response and sync local flag.
                    if (msg.toLowerCase().contains("already")) {
                        SecurePrefs.get(NFTClaimActivity.this).edit()
                                .putBoolean("nft_claimed", true)
                                .apply();
                        showError("This wallet has already claimed its Volver Heritage Souvenir.");
                    } else {
                        showError("Minting failed: " + msg);
                    }
                });
    }

    // ──────────────────────────────────────────────────────────────
    // Result helpers
    // ──────────────────────────────────────────────────────────────

    private void showSuccess(String txHash) {
        progressMint.setVisibility(View.GONE);
        btnMintNFT.setEnabled(false);
        btnMintNFT.setText("Minted ✓");
        btnMintNFT.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF2E7D32)); // green

        // Persist claim state so the treasure chest on HomeActivity switches to the
        // "opened" variant and can be dismissed.
        SecurePrefs.get(this).edit()
                .putBoolean("nft_claimed", true)
                .putBoolean("chest_dismissed", false)
                .apply();

        StringBuilder msg = new StringBuilder("Your Volver Heritage Souvenir has been minted!");
        if (txHash != null && !txHash.isEmpty()) {
            msg.append("\n\nTransaction:\n").append(txHash);
            final String txUrl = PolygonService.getPolygonScanTxUrl(txHash);
            btnViewTx.setVisibility(View.VISIBLE);
            btnViewTx.setOnClickListener(v -> openUrl(txUrl));
        } else {
            btnViewTx.setVisibility(View.GONE);
        }
        tvMintStatus.setText(msg.toString());
        tvMintStatus.setVisibility(View.VISIBLE);
        layoutMintActions.setVisibility(View.VISIBLE);

        Toast.makeText(this, "NFT minted successfully!", Toast.LENGTH_LONG).show();
    }

    /** Opens a URL in the user's default browser. Safe if no browser is available. */
    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No browser available to open link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        progressMint.setVisibility(View.GONE);
        btnMintNFT.setEnabled(true);
        tvMintStatus.setText(message);
        tvMintStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

