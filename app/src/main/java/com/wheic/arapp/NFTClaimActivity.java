package com.wheic.arapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * NFT claim screen — shown after all 5 missions are complete and the wallet is set up.
 *
 * Two minting paths:
 *   • Embedded wallet — signs and broadcasts the transaction directly via Web3j.
 *   • External wallet — opens a MetaMask deep link; user approves and pays gas there.
 *
 * BEFORE RELEASE:
 *   1. Deploy IntramurosNFT.sol to Polygon Amoy (testnet) or mainnet.
 *   2. Set PolygonService.NFT_CONTRACT_ADDRESS to the deployed address.
 *   3. Switch PolygonService.RPC_URL / CHAIN_ID to mainnet when ready.
 */
public class NFTClaimActivity extends AppCompatActivity {

    private TextView  tvWalletAddress, tvMintStatus;
    private Button    btnMintNFT;
    private ProgressBar progressMint;

    private WalletManager walletManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nft_claim_activity);

        walletManager = WalletManager.getInstance(this);

        ImageView imgBack = findViewById(R.id.imgBack);
        imgBack.setOnClickListener(v -> finish());

        tvWalletAddress = findViewById(R.id.tvWalletAddress);
        tvMintStatus    = findViewById(R.id.tvMintStatus);
        btnMintNFT      = findViewById(R.id.btnMintNFT);
        progressMint    = findViewById(R.id.progressMint);

        // Guard: wallet must be set up before reaching this screen
        if (!walletManager.hasWallet()) {
            startActivity(new Intent(this, WalletSetupActivity.class));
            finish();
            return;
        }

        tvWalletAddress.setText(walletManager.getWalletAddress());

        btnMintNFT.setOnClickListener(v -> startMinting());
    }

    // ──────────────────────────────────────────────────────────────
    // Minting logic
    // ──────────────────────────────────────────────────────────────

    private void startMinting() {
        btnMintNFT.setEnabled(false);
        progressMint.setVisibility(View.VISIBLE);
        tvMintStatus.setVisibility(View.VISIBLE);
        tvMintStatus.setText("Preparing transaction...");

        if (walletManager.isEmbeddedWallet()) {
            mintWithEmbeddedWallet();
        } else {
            mintWithExternalWallet();
        }
    }

    /** Embedded wallet path: sign and broadcast via Web3j. */
    private void mintWithEmbeddedWallet() {
        tvMintStatus.setText("Signing and broadcasting on Polygon...");

        String privateKey = walletManager.getPrivateKey();
        if (privateKey == null) {
            showError("Private key not found. Please set up your wallet again.");
            return;
        }

        PolygonService.mintWithEmbeddedWallet(privateKey, new PolygonService.TxCallback() {
            @Override
            public void onSuccess(String txHash) {
                runOnUiThread(() -> showSuccess(txHash));
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showError("Minting failed: " + errorMessage));
            }
        });
    }

    /**
     * External wallet path: open MetaMask with pre-filled transaction.
     * The user approves the transaction and pays gas inside MetaMask.
     */
    private void mintWithExternalWallet() {
        tvMintStatus.setText("Opening wallet app...");

        String deepLink = PolygonService.buildMetaMaskDeepLink();
        Intent intent   = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            // Restore UI state — transaction outcome is handled externally
            btnMintNFT.setEnabled(true);
            progressMint.setVisibility(View.GONE);
            tvMintStatus.setText("Complete the transaction in your wallet app, then return here.");
        } else {
            // MetaMask not installed — show the deep link as a fallback
            showError("No wallet app found. Install MetaMask from the Play Store, then try again.");
            tvMintStatus.setText("Deep link: " + deepLink);
            btnMintNFT.setEnabled(true);
            progressMint.setVisibility(View.GONE);
        }
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

        tvMintStatus.setText("Your Intramuros Passport has been minted!\n\nTransaction:\n" + txHash
                + "\n\nView on PolygonScan: https://amoy.polygonscan.com/tx/" + txHash);
        tvMintStatus.setVisibility(View.VISIBLE);

        Toast.makeText(this, "NFT minted successfully!", Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        progressMint.setVisibility(View.GONE);
        btnMintNFT.setEnabled(true);
        tvMintStatus.setText(message);
        tvMintStatus.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
