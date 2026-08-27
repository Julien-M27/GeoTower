import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

// Signature de release. Le keystore et ses mots de passe vivent dans keystore.properties, non
// versionne : sans ce fichier la configuration est simplement absente et `assembleRelease` produit
// un binaire non signe, ce qui laisse les builds de CI et les checkouts propres fonctionner.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Decalage du versionCode de la variante Android Automotive OS (cf. productFlavors).
val AUTOMOTIVE_VERSION_CODE_OFFSET = 10_000

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
        // Google Play ne compare QUE versionCode : il doit augmenter a chaque envoi et un numero
        // deja envoye ne peut jamais etre reutilise. versionName n'est qu'un libelle d'affichage.
        versionCode = 37
        versionName = "2.0.35"

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

    // Telephone et Android Automotive OS ne peuvent pas tenir dans un seul artefact : le format
    // voiture doit declarer <uses-feature android.hardware.type.automotive> et exposer
    // CarAppActivity en LAUNCHER, la ou le telephone expose ses alias d'icone. Les deux manifestes
    // vivent donc dans src/mobile et src/automotive, et src/main garde tout le commun.
    //
    // Android Auto (projection) n'est PAS concerne : il reste servi par la variante mobile, via le
    // CarAppService declare dans src/main.
    flavorDimensions += "platform"
    productFlavors {
        create("mobile") {
            dimension = "platform"
            isDefault = true
        }
        create("automotive") {
            dimension = "platform"
            // androidx.car.app:app-automotive exige API 29 ; le telephone reste a 24.
            minSdk = 29
            // Google Play refuse deux artefacts partageant un versionCode. Le decalage garde les
            // deux series lisibles (mobile 16, automotive 10016) et evite d'avoir a penser a une
            // seconde numerotation a chaque publication.
            versionCode = (defaultConfig.versionCode ?: 1) + AUTOMOTIVE_VERSION_CODE_OFFSET
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
    // Le coeur de la bibliotheque (CarAppService, templates) est partage ; seul l'hote change.
    implementation(libs.androidx.car.app)
    // Android Auto : l'app est projetee depuis le telephone.
    "mobileImplementation"(libs.androidx.car.app.projected)
    // Android Automotive OS : l'app tourne sur la voiture, et fournit CarAppActivity.
    "automotiveImplementation"(libs.androidx.car.app.automotive)
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
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.json)
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
