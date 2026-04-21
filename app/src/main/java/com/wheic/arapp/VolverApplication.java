package com.wheic.arapp;

import android.app.Application;

import com.google.firebase.FirebaseApp;

/**
 * App-wide initialization.
 *
 * Keeps one-time setup out of Activities so it can't race on cold start.
 * Runs before any Activity's onCreate().
 */
public class VolverApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        // Warm up the Firestore cache settings so the first call site doesn't pay the cost.
        FirebaseConfig.getFirestore();
        // Warm up the wallet manager singleton.
        WalletManager.getInstance(this);
    }
}
