# Add rules that OEM-specific and library code won't be stripped
-keep class com.omnisolve.overlay.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Keep accessibility service metadata
-keep class com.omnisolve.overlay.service.AutoClickAccessibilityService { *; }
-keep class com.omnisolve.overlay.service.OverlayService { *; }



# Samsung, OPPO, Realme, OnePlus, Xiaomi OEM compatibility
# Some OEMs use reflection to read app metadata
-keep class android.** { *; }
-dontwarn android.**
-keepattributes SourceFile,LineNumberTable

# Strip unused debug logs & assert statements in production for size and speed
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
}
