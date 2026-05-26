# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Retrofit/Gson DTOs depend on reflective field access and generic signatures.
-keep class fr.geotower.data.api.** { *; }
-keep class fr.geotower.data.models.** { *; }
-keep class fr.geotower.data.config.RemoteHomeAnnouncement* { *; }
-keep class fr.geotower.data.config.RemoteFeatureFlagConfig { *; }
-keep class fr.geotower.data.upload.SignalQuestUploadManifest { *; }
-keep class fr.geotower.data.upload.SignalQuestUploadFile { *; }

# Room entities and generated database glue.
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Android Auto entry points are discovered through app/service metadata.
-keep class fr.geotower.services.** { *; }
-keep class fr.geotower.ui.screens.car.** { *; }

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
