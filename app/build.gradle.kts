plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

val defaultManifestPublicKeys =
    "geotower-prod-2026-01:MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELaFBNviqR+Ja4TUXuLBLafOrhyLk8W34heF1+pm+XHRHJhCoCQHWhWZK1j8aXNxbYFpge62oMuwNIGB6ZHV6yw=="

android {
    namespace = "fr.geotower"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.geotower"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.9.9.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val manifestPublicKeys = providers
            .gradleProperty("GEOTOWER_MANIFEST_PUBLIC_KEYS")
            .orElse(providers.environmentVariable("GEOTOWER_MANIFEST_PUBLIC_KEYS"))
            .orElse(defaultManifestPublicKeys)
            .get()
        buildConfigField("String", "GEOTOWER_MANIFEST_PUBLIC_KEYS", manifestPublicKeys.asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.preference.ktx)

    // Carte OSM
    implementation(libs.osmdroid.android)
    // CLUSTERS D'ANTENNES
    implementation(libs.osmbonuspack)
    // Pont Osmdroid Mapsforge
    implementation(libs.osmdroid.mapsforge)
    // Moteur Mapsforge
    implementation(libs.mapsforge.map.android)
    implementation(libs.mapsforge.map.reader)
    implementation(libs.mapsforge.themes)
    // ROOM
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // API (Retrofit)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    // COIL POUR LES IMAGES
    implementation(libs.coil.compose)
    // Librairie ZXing pour générer le QR Code
    implementation(libs.zxing.core)
    // INDISPENSABLE pour le GPS
    implementation(libs.play.services.location)

    implementation(libs.kotlinx.coroutines.play.services)
    // Net
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- WIDGET & TÂCHES EN ARRIÈRE-PLAN ---
    // Jetpack Glance (Pour dessiner le widget comme on dessine du Compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // WorkManager conversion photo
    implementation(libs.androidx.exifinterface)
}
