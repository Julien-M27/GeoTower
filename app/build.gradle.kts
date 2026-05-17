import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
// --- BLOC UNIQUE DE LECTURE ---
val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(FileInputStream(localPropertiesFile))
}
val sqApiKey: String = properties.getProperty("SQ_API_KEY") ?: ""
// ------------------------------
android {
    namespace = "fr.geotower"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.geotower"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.9.9.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2. Créer la variable accessible dans le code Kotlin [cite: 2]
        buildConfigField("String", "SQ_API_KEY", "\"$sqApiKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Carte OSM
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // --- AJOUT POUR LES CLUSTERS (REGROUPEMENT D'ANTENNES) ---
    implementation("com.github.MKergall:osmbonuspack:6.9.0")
    // ---------------------------------------------------------

    // --- AJOUT POUR LE VECTORIEL (Mapsforge) ---
    // Le pont entre Osmdroid et Mapsforge
    implementation("org.osmdroid:osmdroid-mapsforge:6.1.18")
    // Le moteur Mapsforge
    implementation("org.mapsforge:mapsforge-map-android:0.20.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.20.0")
    implementation("org.mapsforge:mapsforge-themes:0.20.0")
    // --- ROOM (BASE DE DONNÉES) ---
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Pour parler à l'API (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // --- AJOUT COIL POUR LES IMAGES ---
    implementation("io.coil-kt:coil-compose:2.6.0")
    // ✅ NOUVEAU : Librairie ZXing pour générer le QR Code
    implementation("com.google.zxing:core:3.5.3")
    // INDISPENSABLE pour le GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // INDISPENSABLE pour .await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
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
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // WorkManager (Le seul outil autorisé par Google pour l'arrière-plan, min 15 min)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // WorkManager conversion photo
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
