package com.wheic.arapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

/**
 * Wallet setup screen shown before a user can mint their NFT.
 *
 * Flow:
 *   Step 1 — Choose: "I already have a wallet" OR "Create a new wallet"
 *   Step 2a — Connect: Enter / scan a Polygon address
 *   Step 2b — Create:  Display generated address + private key; require acknowledgement
 *
 * After setup, goes directly to NFTClaimActivity.
 */
public class WalletSetupActivity extends AppCompatActivity {

    // Views — Step 1 (choose)
    private LinearLayout layoutChoose;
    private CardView cardConnectWallet, cardCreateWallet;

    // Views — Step 2a (connect existing)
    private LinearLayout layoutConnectForm;
    private EditText etWalletAddress;
    private Button btnScanQR, btnConfirmAddress, btnBackFromConnect;

    // Views — Step 2b (create embedded)
    private LinearLayout layoutCreateWallet;
    private TextView tvGeneratedAddress, tvPrivateKey;
    private Button btnCopyPrivKey, btnConfirmCreated, btnBackFromCreate;
    private CheckBox checkSavedKey;

    private WalletManager walletManager;
    private String username;
    private long lastConfirmClickTime = 0;
    private static final long DEBOUNCE_MILLIS = 2000;
    private final Handler clipboardHandler = new Handler(Looper.getMainLooper());
    private final Runnable clipboardClearRunnable = () -> {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("", ""));
    };

    // ZXing QR scanner launcher
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    etWalletAddress.setText(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Prevent screenshots and screen recording on this screen (private key visible)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.wallet_setup_activity);

        walletManager = WalletManager.getInstance(this);
        username = SecurePrefs.get(this).getString("username", "");

        bindViews();
        setupListeners();

        // If wallet already set up, skip straight to NFT claim
        if (walletManager.hasWallet()) {
            openNFTClaim();
            finish();
            return;
        }

        showStep(Step.CHOOSE);
    }

    // ──────────────────────────────────────────────────────────────
    // View binding
    // ──────────────────────────────────────────────────────────────

    private void bindViews() {
        ImageView imgBack = findViewById(R.id.imgBack);
        imgBack.setOnClickListener(v -> finish());

        layoutChoose       = findViewById(R.id.layoutChoose);
        cardConnectWallet  = findViewById(R.id.cardConnectWallet);
        cardCreateWallet   = findViewById(R.id.cardCreateWallet);

        layoutConnectForm  = findViewById(R.id.layoutConnectForm);
        etWalletAddress    = findViewById(R.id.etWalletAddress);
        btnScanQR          = findViewById(R.id.btnScanQR);
        btnConfirmAddress  = findViewById(R.id.btnConfirmAddress);
        btnBackFromConnect = findViewById(R.id.btnBackFromConnect);

        layoutCreateWallet  = findViewById(R.id.layoutCreateWallet);
        tvGeneratedAddress  = findViewById(R.id.tvGeneratedAddress);
        tvPrivateKey        = findViewById(R.id.tvPrivateKey);
        btnCopyPrivKey      = findViewById(R.id.btnCopyPrivKey);
        btnConfirmCreated   = findViewById(R.id.btnConfirmCreated);
        btnBackFromCreate   = findViewById(R.id.btnBackFromCreate);
        checkSavedKey       = findViewById(R.id.checkSavedKey);
    }

    // ──────────────────────────────────────────────────────────────
    // Listeners
    // ──────────────────────────────────────────────────────────────

    private void setupListeners() {
        // Step 1 choices
        cardConnectWallet.setOnClickListener(v -> showStep(Step.CONNECT));
        cardCreateWallet.setOnClickListener(v -> {
            generateAndShowEmbeddedWallet();
            showStep(Step.CREATE);
        });

        // Step 2a — connect existing
        btnScanQR.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan your Polygon wallet QR code");
            options.setBeepEnabled(false);
            qrLauncher.launch(options);
        });

        btnConfirmAddress.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastConfirmClickTime < DEBOUNCE_MILLIS) return;
            lastConfirmClickTime = now;

            String address = etWalletAddress.getText().toString().trim();
            if (!WalletManager.isValidAddress(address)) {
                Toast.makeText(this, "Please enter a valid Polygon address (0x...)",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            walletManager.saveExternalWallet(address);
            saveWalletToServer(address);
        });

        btnBackFromConnect.setOnClickListener(v -> showStep(Step.CHOOSE));

        // Step 2b — create embedded
        checkSavedKey.setOnCheckedChangeListener((btn, checked) ->
                btnConfirmCreated.setEnabled(checked));

        btnCopyPrivKey.setOnClickListener(v -> {
            String key = tvPrivateKey.getText().toString();
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Private Key", key));
            Toast.makeText(this, "Copied — clipboard will be cleared in 30 seconds", Toast.LENGTH_SHORT).show();
            // Auto-clear clipboard after 30 seconds to prevent key leakage
            clipboardHandler.removeCallbacks(clipboardClearRunnable);
            clipboardHandler.postDelayed(clipboardClearRunnable, 30_000);
        });

        btnConfirmCreated.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastConfirmClickTime < DEBOUNCE_MILLIS) return;
            lastConfirmClickTime = now;

            String address = walletManager.getWalletAddress();
            saveWalletToServer(address);
        });

        btnBackFromCreate.setOnClickListener(v -> showStep(Step.CHOOSE));
    }

    @Override
    protected void onDestroy() {
        // Clear clipboard immediately if private key was copied and timer hasn't fired yet
        clipboardClearRunnable.run();
        clipboardHandler.removeCallbacks(clipboardClearRunnable);
        super.onDestroy();
    }

    // ──────────────────────────────────────────────────────────────
    // Embedded wallet generation
    // ──────────────────────────────────────────────────────────────

    private void generateAndShowEmbeddedWallet() {
        WalletManager.EmbeddedWallet wallet = walletManager.generateEmbeddedWallet();
        if (wallet == null) {
            Toast.makeText(this, "Failed to generate wallet. Please try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        tvGeneratedAddress.setText(wallet.address);
        tvPrivateKey.setText(wallet.privateKey);
        checkSavedKey.setChecked(false);
        btnConfirmCreated.setEnabled(false);
    }

    // ──────────────────────────────────────────────────────────────
    // Server sync
    // ──────────────────────────────────────────────────────────────

    private void saveWalletToServer(String address) {
        MissionCompletionHelper.saveWalletAddress(this, address,
                new MissionCompletionHelper.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        // Also request whitelist if all missions are already complete
                        MissionCompletionHelper.requestWhitelist(
                                WalletSetupActivity.this, address,
                                new MissionCompletionHelper.CompletionCallback() {
                                    @Override public void onSuccess() { proceedToNFTClaim(); }
                                    @Override public void onError(String msg) { proceedToNFTClaim(); }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        // Still proceed locally even if server call fails
                        Toast.makeText(WalletSetupActivity.this,
                                "Wallet saved locally. Server sync failed.",
                                Toast.LENGTH_SHORT).show();
                        proceedToNFTClaim();
                    }
                });
    }

    private void proceedToNFTClaim() {
        openNFTClaim();
        finish();
    }

    private void openNFTClaim() {
        startActivity(new Intent(this, NFTClaimActivity.class));
    }

    // ──────────────────────────────────────────────────────────────
    // Step visibility
    // ──────────────────────────────────────────────────────────────

    private enum Step { CHOOSE, CONNECT, CREATE }

    private void showStep(Step step) {
        layoutChoose.setVisibility(step == Step.CHOOSE  ? View.VISIBLE : View.GONE);
        layoutConnectForm.setVisibility(step == Step.CONNECT ? View.VISIBLE : View.GONE);
        layoutCreateWallet.setVisibility(step == Step.CREATE  ? View.VISIBLE : View.GONE);
    }
}
