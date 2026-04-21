package com.wheic.arapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

/** Small helper for connectivity checks that works on API 24+ without deprecated calls. */
public final class NetworkUtils {

    private NetworkUtils() { /* utility */ }

    /** Returns true when the device has a usable internet connection. */
    public static boolean isConnected(Context context) {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        //noinspection deprecation
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        //noinspection deprecation
        return info != null && info.isConnected();
    }
}
