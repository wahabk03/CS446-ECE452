# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# App data classes are accessed via explicit map conversion, not reflection,
# but keep them for safe measure and to preserve crash stack traces.
-keep class com.example.graphicaltimeplanner.** { *; }

# Firebase / GMS – the SDKs bundle consumer rules, but these catch any gaps.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# OkHttp / Okio (used internally by Firebase)
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin metadata (required for coroutines and reflection)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**