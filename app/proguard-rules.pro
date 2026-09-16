# ProGuard / R8 Rules for Calculator (Parental Monitoring App)

# ============================
# Firebase Realtime Database
# ============================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep all Firebase data model classes (used with RTDB getValue())
-keep class com.example.authapp.data.** { *; }
-keep class com.google.firebase.database.** { *; }
-dontwarn com.google.firebase.database.**

# ============================
# Firebase Auth
# ============================
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ============================
# Firebase Cloud Messaging (FCM)
# ============================
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.messaging.**

# ============================
# Firebase Crashlytics
# ============================
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ============================
# Firebase Storage
# ============================
-keep class com.google.firebase.storage.** { *; }
-dontwarn com.google.firebase.storage.**

# ============================
# WebRTC (io.getstream:stream-webrtc-android)
# ============================
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.getstream.webrtc.** { *; }
-keepclassmembers class io.getstream.webrtc.** { *; }
-dontwarn io.getstream.webrtc.**

# WebRTC JNI native bindings - must never be stripped
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================
# Kotlin Serialization / Coroutines
# ============================
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# ============================
# Jetpack Compose
# ============================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============================
# Coil (Image Loading)
# ============================
-keep class coil.** { *; }
-dontwarn coil.**

# ============================
# OkHttp (used by Coil/Firebase)
# ============================
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================
# Google Maps / Location
# ============================
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ============================
# AndroidX / Lifecycle
# ============================
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ============================
# MediaRecorder / Camera2 APIs (used for call recording & snapshots)
# ============================
-keep class android.media.MediaRecorder { *; }
-keep class android.hardware.camera2.** { *; }

# ============================
# BroadcastReceivers, Services, Activities
# ============================
-keep class com.example.authapp.receiver.** { *; }
-keep class com.example.authapp.service.** { *; }
-keep class com.example.authapp.scheduler.** { *; }

# ============================
# Remove Log calls in release
# ============================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
