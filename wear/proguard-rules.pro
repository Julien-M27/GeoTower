# Wear OS Proguard rules
-keep class com.google.android.gms.wearable.** { *; }

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Conserver les modèles de données et DTOs pour la désérialisation Gson / Retrofit
-keep class fr.geotower.wear.data.model.** { *; }
-keep class fr.geotower.wear.data.network.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
