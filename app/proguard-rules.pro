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
#
# Any class Gson reads back and that holds a COLLECTION field must be listed here. Without a keep
# rule, R8 (full mode) rewrites the declared type of such a field -- `List<SiteHsEntity>` became a
# raw `ArrayList` in 2.0.11 -- so Gson loses the element type, fills the list with LinkedTreeMap,
# and the first read crashes with a ClassCastException.
#
# Deliberately NOT listed, because keeping a class also freezes its field names and would make the
# files already on devices unreadable once: ShareHistoryEntry, NotificationHistoryEntry,
# ExternalPhotoUploadHistoryEntry and WidgetSiteData. They are flat (no collection field) and
# therefore safe as they stand -- but add a List/Map/Set field to any of them and it MUST be kept
# here, along with a migration for the entries users would otherwise lose.
-keep class fr.geotower.data.api.** { *; }
-keep class fr.geotower.data.models.** { *; }
-keep class fr.geotower.data.outages.CachedOutages { *; }
-keep class fr.geotower.data.outages.CachedServerOutages { *; }
-keep class fr.geotower.data.config.RemoteHomeAnnouncement* { *; }
-keep class fr.geotower.data.config.RemoteFeatureFlagConfig { *; }
-keep class fr.geotower.data.upload.SignalQuestUploadManifest { *; }
-keep class fr.geotower.data.upload.SignalQuestUploadFile { *; }

# Trajets enregistres : TripPlan porte `steps`, `legs` et `reminderOffsetsMinutes`, donc la regle
# est obligatoire (voir plus haut). Poser cette regle des la premiere version qui ecrit le fichier :
# la garder fige les noms de champs, et l'ajouter apres coup rendrait illisibles les trajets deja
# enregistres sur les telephones.
-keep class fr.geotower.data.trip.TripPlan { *; }
-keep class fr.geotower.data.trip.TripStep { *; }
-keep class fr.geotower.data.trip.TripLeg { *; }
-keep class fr.geotower.data.trip.TripManeuver { *; }

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
