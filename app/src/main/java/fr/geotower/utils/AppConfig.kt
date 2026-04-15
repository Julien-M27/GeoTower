package fr.geotower.utils

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import android.os.Build

object AppConfig {
    // --- Apparence ---
    var themeMode = mutableIntStateOf(0)
    var isOledMode = mutableStateOf(true)
    var isBlurEnabled = mutableStateOf(true)
    // 0 = Plein écran, 1 = Fractionné
    var displayStyle = mutableIntStateOf(0)

    var menuSize = mutableStateOf("normal")

    //Notification de téléchargement
    var enableUpdateNotifications = mutableStateOf(true) // Désactivé par défaut

    // Statut de l'antenne
    var siteShowStatus = mutableStateOf(true)
    var shareSiteStatus = mutableStateOf(true) // 🚨 AJOUT DE LA VARIABLE MANQUANTE

    // Langue globale de l'application (Système par défaut au 1er lancement)
    val appLanguage = mutableStateOf("Système")

    //Notifications live
    var enableLiveNotifications = mutableStateOf(false)

    // --- UNITÉS DE MESURE ---
    // 0 = Kilomètres (km), 1 = Miles (mi)
    var distanceUnit = mutableIntStateOf(0)
    // 0 = km/h, 1 = mph
    var speedUnit = mutableIntStateOf(0)

    // --- Carte ---
    var mapProvider = mutableIntStateOf(0)
    var ignStyle = mutableIntStateOf(0)

    var showSpeedometer = mutableStateOf(true)

    // --- FILTRES : OPÉRATEURS ---
    var showOrange = mutableStateOf(true)
    var showSfr = mutableStateOf(true)
    var showBouygues = mutableStateOf(true)
    var showFree = mutableStateOf(true)

    // --- FILTRES : AZIMUTS ---
    var showAzimuths = mutableStateOf(true)

    // --- FILTRES : TECHNOLOGIES ---
    var showTechno2G = mutableStateOf(true)
    var showTechno3G = mutableStateOf(true)
    var showTechno4G = mutableStateOf(true)
    var showTechno5G = mutableStateOf(true)
    var showTechnoFH = mutableStateOf(true)

    // --- FILTRES : FRÉQUENCES ---

    // 2G GSM
    var f2G_900 = mutableStateOf(true)
    var f2G_1800 = mutableStateOf(true)

    // 3G UMTS
    var f3G_900 = mutableStateOf(true)
    var f3G_2100 = mutableStateOf(true)

    // 4G LTE
    var f4G_700 = mutableStateOf(true)
    var f4G_800 = mutableStateOf(true)
    var f4G_900 = mutableStateOf(true)
    var f4G_1800 = mutableStateOf(true)
    var f4G_2100 = mutableStateOf(true)
    var f4G_2600 = mutableStateOf(true)

    // 5G NR
    var f5G_700 = mutableStateOf(true)
    var f5G_2100 = mutableStateOf(true)
    var f5G_3500 = mutableStateOf(true)
    var f5G_26000 = mutableStateOf(true)

    // --- FILTRES : FRÉQUENCES (DÉTAILS DU SITE) ---
    var siteShowTechno2G = mutableStateOf(true)
    var siteShowTechno3G = mutableStateOf(true)
    var siteShowTechno4G = mutableStateOf(true)
    var siteShowTechno5G = mutableStateOf(true)
    var siteShowTechnoFH = mutableStateOf(true)

    var siteF2G_900 = mutableStateOf(true)
    var siteF2G_1800 = mutableStateOf(true)
    var siteF3G_900 = mutableStateOf(true)
    var siteF3G_2100 = mutableStateOf(true)
    var siteF4G_700 = mutableStateOf(true)
    var siteF4G_800 = mutableStateOf(true)
    var siteF4G_900 = mutableStateOf(true)
    var siteF4G_1800 = mutableStateOf(true)
    var siteF4G_2100 = mutableStateOf(true)
    var siteF4G_2600 = mutableStateOf(true)
    var siteF5G_700 = mutableStateOf(true)
    var siteF5G_2100 = mutableStateOf(true)
    var siteF5G_3500 = mutableStateOf(true)
    var siteF5G_26000 = mutableStateOf(true)

    // --- FILTRES : SPECTRE (DÉTAILS DU SITE) ---
    var siteShowSpectrum = mutableStateOf(true) // Switch principal maître
    var siteShowSpectrumBand = mutableStateOf(true)
    var siteShowSpectrumTotal = mutableStateOf(true)

    // --- ORDRE DES TECHNOLOGIES ET FRÉQUENCES (SITE) ---
    var siteTechnoOrder = mutableStateOf(listOf("5G", "4G", "3G", "2G", "FH"))

    var siteFreqOrder5G = mutableStateOf(listOf("3500", "2100", "700"))
    var siteFreqOrder4G = mutableStateOf(listOf("2600", "2100", "1800", "900", "800", "700"))
    var siteFreqOrder3G = mutableStateOf(listOf("2100", "900"))
    var siteFreqOrder2G = mutableStateOf(listOf("1800", "900"))

    // --- PARTAGE PAR DÉFAUT (CARTE) ---
    var shareMapAzimuths = mutableStateOf(true)
    var shareMapSpeedometer = mutableStateOf(true)
    var shareMapScale = mutableStateOf(true)
    var shareMapAttribution = mutableStateOf(true)
    var shareMapConfidential = mutableStateOf(false)

    // Autres
    var useOfflineMap = mutableStateOf(false)

    // Variable globale pour l'opérateur par défaut
    val defaultOperator = mutableStateOf("Aucun")

    // Mode de navigation (0 = Défilant, 1 = Pages)
    var navMode = mutableIntStateOf(0)

    // --- DÉTECTION ET THÈME ONE UI ---

    // Détecte si le téléphone est un Samsung
    val isSamsungDevice = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    // État pour le bouton manuel dans les paramètres
    var forceOneUiTheme = mutableStateOf(false)

    // Propriété dynamique : Vrai si c'est un Samsung OU si l'utilisateur l'a forcé
    val useOneUiDesign: Boolean
        get() = isSamsungDevice || forceOneUiTheme.value

    //Masquer les boutons sur l'écran d'accueil

    val showNearbyPage = mutableStateOf(true)
    val showMapPage = mutableStateOf(true)
    val showCompassPage = mutableStateOf(true)
    val showStatsPage = mutableStateOf(true)

    // --- Capteurs matériels ---
    var hasCompass = mutableStateOf(true) // Vrai par défaut, vérifié au lancement

    // --- FONCTION POUR CHARGER LA MÉMOIRE AU DÉMARRAGE ---
    fun loadSavedFilters(prefs: android.content.SharedPreferences) {

        //Notification de téléchargement
        enableUpdateNotifications.value = prefs.getBoolean("enable_update_notifications", true)
        val model = android.os.Build.MODEL
        val device = android.os.Build.DEVICE

        // 🛠️ ASTUCE DÉBOGAGE : Regarde dans le Logcat d'Android Studio pour voir ces valeurs !
        android.util.Log.d("GeoTower_Fold", "Modèle (MODEL) : $model | Appareil (DEVICE) : $device")

        //Statut
        shareSiteStatus.value = prefs.getBoolean("share_site_status", true)

        // ✅ DÉTECTION GALAXY Z FOLD (Samsung)
        val isGalaxyZFold = model.startsWith("SM-F9", ignoreCase = true)

        // ✅ DÉTECTION PIXEL FOLD (Google)
        val pixelFoldModels = listOf("GQK96", "GGH2X", "G9FNL")
        val pixelFoldDevices = listOf("felix", "comet") // Noms de code internes : Fold 1 = felix, Fold 9 Pro = comet

        val isGoogleFold = model.contains("Fold", ignoreCase = true) ||
                pixelFoldModels.contains(model.uppercase()) ||
                pixelFoldDevices.contains(device.lowercase())

        // Si c'est un Z Fold OU un Pixel Fold, la valeur par défaut est 1 (Fractionné), sinon 0 (Plein écran)
        val defaultDisplayStyle = if (isGalaxyZFold || isGoogleFold) 1 else 0

        //Notifications live
        enableLiveNotifications.value = prefs.getBoolean("enable_live_notifications", false)

        // ✅ CHARGEMENT DU STYLE D'AFFICHAGE (avec la nouvelle valeur par défaut)
        displayStyle.intValue = prefs.getInt("display_style", defaultDisplayStyle)

        // --- CHARGEMENT DES UNITÉS ---
        distanceUnit.intValue = prefs.getInt("distance_unit", 0)
        speedUnit.intValue = prefs.getInt("speed_unit", 0)

        showOrange.value = prefs.getBoolean("show_orange", true)
        showSfr.value = prefs.getBoolean("show_sfr", true)
        showBouygues.value = prefs.getBoolean("show_bouygues", true)
        showFree.value = prefs.getBoolean("show_free", true)

        showTechno2G.value = prefs.getBoolean("show_techno_2g", true)
        showTechno3G.value = prefs.getBoolean("show_techno_3g", true)
        showTechno4G.value = prefs.getBoolean("show_techno_4g", true)
        showTechno5G.value = prefs.getBoolean("show_techno_5g", true)
        showTechnoFH.value = prefs.getBoolean("show_techno_fh", true)

        f2G_900.value = prefs.getBoolean("f2g_900", true)
        f2G_1800.value = prefs.getBoolean("f2g_1800", true)

        f3G_900.value = prefs.getBoolean("f3g_900", true)
        f3G_2100.value = prefs.getBoolean("f3g_2100", true)

        f4G_700.value = prefs.getBoolean("f4g_700", true)
        f4G_800.value = prefs.getBoolean("f4g_800", true)
        f4G_900.value = prefs.getBoolean("f4g_900", true)
        f4G_1800.value = prefs.getBoolean("f4g_1800", true)
        f4G_2100.value = prefs.getBoolean("f4g_2100", true)
        f4G_2600.value = prefs.getBoolean("f4g_2600", true)

        f5G_700.value = prefs.getBoolean("f5g_700", true)
        f5G_2100.value = prefs.getBoolean("f5g_2100", true)
        f5G_3500.value = prefs.getBoolean("f5g_3500", true)
        f5G_26000.value = prefs.getBoolean("f5g_26000", true)

        siteShowTechno2G.value = prefs.getBoolean("site_show_techno_2g", true)
        siteShowTechno3G.value = prefs.getBoolean("site_show_techno_3g", true)
        siteShowTechno4G.value = prefs.getBoolean("site_show_techno_4g", true)
        siteShowTechno5G.value = prefs.getBoolean("site_show_techno_5g", true)
        siteShowTechnoFH.value = prefs.getBoolean("site_show_techno_fh", true)

        siteF2G_900.value = prefs.getBoolean("site_f2g_900", true)
        siteF2G_1800.value = prefs.getBoolean("site_f2g_1800", true)
        siteF3G_900.value = prefs.getBoolean("site_f3g_900", true)
        siteF3G_2100.value = prefs.getBoolean("site_f3g_2100", true)
        siteF4G_700.value = prefs.getBoolean("site_f4g_700", true)
        siteF4G_800.value = prefs.getBoolean("site_f4g_800", true)
        siteF4G_900.value = prefs.getBoolean("site_f4g_900", true)
        siteF4G_1800.value = prefs.getBoolean("site_f4g_1800", true)
        siteF4G_2100.value = prefs.getBoolean("site_f4g_2100", true)
        siteF4G_2600.value = prefs.getBoolean("site_f4g_2600", true)
        siteF5G_700.value = prefs.getBoolean("site_f5g_700", true)
        siteF5G_2100.value = prefs.getBoolean("site_f5g_2100", true)
        siteF5G_3500.value = prefs.getBoolean("site_f5g_3500", true)
        siteF5G_26000.value = prefs.getBoolean("site_f5g_26000", true)

        // --- CHARGEMENT SPECTRE ---
        siteShowSpectrum.value = prefs.getBoolean("site_show_spectrum", true)
        siteShowSpectrumBand.value = prefs.getBoolean("site_show_spectrum_band", true)
        siteShowSpectrumTotal.value = prefs.getBoolean("site_show_spectrum_total", true)

        // 🚨 AJOUTEZ CE BLOC ICI :
        // Chargement de la visibilité du nouveau bloc Statut
        siteShowStatus.value = prefs.getBoolean("site_show_status", true)

        // Chargement de l'ordre des blocs (incluant 'status' par défaut)
        val pageOrder = prefs.getString("page_support_order", "map,status,details,photos,nav,share,operators")
        // Note : Si vous utilisez une variable globale pour stocker cet ordre, assurez-vous de la mettre à jour ici.

        // --- CHARGEMENT DE L'ORDRE DES TECHNOLOGIES ET FRÉQUENCES ---
        val tOrder = prefs.getString("site_techno_order", "5G,4G,3G,2G,FH")
        siteTechnoOrder.value = tOrder?.split(",") ?: listOf("5G", "4G", "3G", "2G", "FH")

        val f5gOrder = prefs.getString("site_freq_5g_order", "3500,2100,700")
        siteFreqOrder5G.value = f5gOrder?.split(",") ?: listOf("3500", "2100", "700")

        val f4gOrder = prefs.getString("site_freq_4g_order", "2600,2100,1800,900,800,700")
        siteFreqOrder4G.value = f4gOrder?.split(",") ?: listOf("2600", "2100", "1800", "900", "800", "700")

        val f3gOrder = prefs.getString("site_freq_3g_order", "2100,900")
        siteFreqOrder3G.value = f3gOrder?.split(",") ?: listOf("2100", "900")

        val f2gOrder = prefs.getString("site_freq_2g_order", "1800,900")
        siteFreqOrder2G.value = f2gOrder?.split(",") ?: listOf("1800", "900")

        // --- CHARGEMENT PARTAGE DE LA CARTE ---
        shareMapAzimuths.value = prefs.getBoolean("share_map_azimuths", true)
        shareMapSpeedometer.value = prefs.getBoolean("share_map_speedometer", true)
        shareMapScale.value = prefs.getBoolean("share_map_scale", true)
        shareMapAttribution.value = prefs.getBoolean("share_map_attribution", true)
        shareMapConfidential.value = prefs.getBoolean("share_map_confidential", false)
    }

    // --- MISE À JOUR BASE DE DONNÉES ---
    var isDbUpdateAvailable = mutableStateOf(false)
}