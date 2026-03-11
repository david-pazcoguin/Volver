package com.wheic.arapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/**
 * Singleton managing the user's Polygon wallet state.
 *
 * Two wallet modes:
 *   1. External  — user pastes their own Polygon address (MetaMask, etc.)
 *   2. Embedded  — app generates a keypair locally; user must back up the private key.
 *
 * The private key is stored in SharedPreferences. For a production release, migrate
 * this to Android Keystore encryption for stronger security.
 */
public class WalletManager {

    private static final String PREFS_NAME  = "wallet_prefs";
    private static final String KEY_ADDRESS = "wallet_address";
    private static final String KEY_PRIVKEY = "private_key";
    private static final String KEY_EMBEDDED = "is_embedded";

    private static WalletManager instance;
    private final SharedPreferences prefs;

    private WalletManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized WalletManager getInstance(Context context) {
        if (instance == null) instance = new WalletManager(context);
        return instance;
    }

    // ──────────────────────────────────────────────────────────────
    // State queries
    // ──────────────────────────────────────────────────────────────

    public boolean hasWallet() {
        String addr = prefs.getString(KEY_ADDRESS, "");
        return addr != null && !addr.isEmpty();
    }

    public String getWalletAddress() {
        return prefs.getString(KEY_ADDRESS, "");
    }

    public boolean isEmbeddedWallet() {
        return prefs.getBoolean(KEY_EMBEDDED, false);
    }

    /** Only meaningful for embedded wallets. Returns null for external wallets. */
    public String getPrivateKey() {
        if (!isEmbeddedWallet()) return null;
        return prefs.getString(KEY_PRIVKEY, null);
    }

    // ──────────────────────────────────────────────────────────────
    // Wallet operations
    // ──────────────────────────────────────────────────────────────

    /** Save an externally supplied Polygon address (no private key stored). */
    public void saveExternalWallet(String address) {
        prefs.edit()
             .putString(KEY_ADDRESS, address.toLowerCase())
             .putBoolean(KEY_EMBEDDED, false)
             .remove(KEY_PRIVKEY)
             .apply();
    }

    /**
     * Generate a new Ethereum/Polygon keypair and store it locally.
     *
     * @return EmbeddedWallet with address + private key, or null on failure.
     */
    public EmbeddedWallet generateEmbeddedWallet() {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            String privateKey = Numeric.toHexStringWithPrefix(keyPair.getPrivateKey());
            String address    = "0x" + Keys.getAddress(keyPair);

            prefs.edit()
                 .putString(KEY_ADDRESS, address)
                 .putString(KEY_PRIVKEY, privateKey)
                 .putBoolean(KEY_EMBEDDED, true)
                 .apply();

            return new EmbeddedWallet(address, privateKey);
        } catch (Exception e) {
            return null;
        }
    }

    public void clearWallet() {
        prefs.edit().clear().apply();
    }

    // ──────────────────────────────────────────────────────────────
    // Validation
    // ──────────────────────────────────────────────────────────────

    /** Returns true if the string looks like a valid Ethereum/Polygon address. */
    public static boolean isValidAddress(String address) {
        return address != null && address.matches("^0x[0-9a-fA-F]{40}$");
    }

    // ──────────────────────────────────────────────────────────────
    // Inner types
    // ──────────────────────────────────────────────────────────────

    public static class EmbeddedWallet {
        public final String address;
        public final String privateKey;

        EmbeddedWallet(String address, String privateKey) {
            this.address    = address;
            this.privateKey = privateKey;
        }
    }
}
