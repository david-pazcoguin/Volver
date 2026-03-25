package com.wheic.arapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Centralised accessor for the app's encrypted session preferences.
 *
 * Every call-site that previously used
 *   getSharedPreferences("Volver", Context.MODE_PRIVATE)
 * should now use
 *   SecurePrefs.get(context)
 */
public final class SecurePrefs {

    private static final String FILE_NAME = "Volver";
    private static final String MASTER_KEY_ALIAS = "volver_session_key";

    private SecurePrefs() { /* utility class */ }

    public static SharedPreferences get(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context, MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Keystore corrupted — wipe the broken prefs file and retry once
            android.util.Log.e("SecurePrefs", "Encrypted prefs corrupted, resetting.", e);
            try {
                context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply();
                MasterKey masterKey = new MasterKey.Builder(context, MASTER_KEY_ALIAS)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                return EncryptedSharedPreferences.create(
                        context,
                        FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException retryEx) {
                throw new RuntimeException("Failed to create encrypted preferences after reset", retryEx);
            }
        }
    }
}
