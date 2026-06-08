package com.wheic.arapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

        // Apply system-bar + display-cutout insets as top/bottom padding to every
        // Activity's root view. Needed because targetSdk 35 draws edge-to-edge on
        // Android 15+, which otherwise lets the status bar and front-camera cutout
        // cover tappable UI at the top of the screen.
        registerActivityLifecycleCallbacks(new InsetsCallbacks());
    }

    /** Applies window insets as padding to non-AR activity roots. */
    private static final class InsetsCallbacks implements ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            // AR / camera activity must remain truly fullscreen.
            if (activity instanceof ARActivity) return;

            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout()
                                | WindowInsetsCompat.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
            // Trigger the listener in case the view is already laid out.
            ViewCompat.requestApplyInsets(root);
        }

        @Override public void onActivityStarted(@NonNull Activity activity) {}
        @Override public void onActivityResumed(@NonNull Activity activity) {}
        @Override public void onActivityPaused(@NonNull Activity activity) {}
        @Override public void onActivityStopped(@NonNull Activity activity) {}
        @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        @Override public void onActivityDestroyed(@NonNull Activity activity) {}
    }
}
