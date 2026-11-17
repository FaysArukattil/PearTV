-dontwarn okio.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**
-dontwarn kotlin.**

# ── R8 Size & Performance Optimizations ─────────────────────────────────────
# Run multiple optimization passes to maximize dead code elimination and inlining
-optimizationpasses 5

# Allow R8 to widen method/field access permissions so it can inline more code
-allowaccessmodification

# Repackage all classes into the root namespace to shorten package descriptors in DEX
-repackageclasses ''

# Overload method names aggressively for smaller DEX size
-overloadaggressively

# Merge interfaces aggressively to reduce DEX class table overhead
-mergeinterfacesaggressively

# Strip source file attributes but keep line numbers for Play Console mapping
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

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
