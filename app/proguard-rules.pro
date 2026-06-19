# ── Volver ProGuard / R8 rules ──────────────────────────────────────────

# Strip debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Keep line numbers for crash reports while obfuscating source file name
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase ────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ── Web3j ───────────────────────────────────────────────────────────────
-keep class org.web3j.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.web3j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ── ARCore / Sceneform ──────────────────────────────────────────────────
-keep class com.google.ar.** { *; }
-keep class com.google.android.filament.** { *; }

# ── ZXing barcode ───────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }
