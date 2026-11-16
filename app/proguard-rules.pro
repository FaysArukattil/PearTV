-dontwarn okio.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**
-dontwarn kotlin.**

# ── R8 Size & Performance Optimizations ─────────────────────────────────────
# Allow R8 to widen method/field access permissions so it can inline more code
-allowaccessmodification

# Repackage all classes into the root namespace to shorten package descriptors in DEX
-repackageclasses ''

# Eliminate verbose, debug, and info logs in production release builds to strip string constants and call overhead
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── Required Keeps for Reflection / Serialization ───────────────────────────
# Protobuf Lite message fields
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Shizuku Binder IPC
-keep class rikka.shizuku.** { *; }
