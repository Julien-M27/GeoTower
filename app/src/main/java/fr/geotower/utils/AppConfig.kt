package fr.geotower.utils

import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import fr.geotower.data.db.GeoTowerDatabaseValidator

object AppConfig {
    const val PREF_COLOR_PALETTE = "color_palette"
    const val DEFAULT_COLOR_PALETTE = "dynamic"
    const val PREF_SELECTED_OPERATORS = "selected_operator_keys"
    const val PREF_UI_MODE = "ui_mode"
    const val PREF_SHOW_MAP_LOCATION_MARKER = "show_map_location_marker"
    const val PREF_SHOW_AZIMUTH_LINES = "show_azimuths"
    const val PREF_SHOW_AZIMUTH_CONES = "show_azimuths_cone"
    const val DEFAULT_SHOW_AZIMUTH_LINES = true
    const val DEFAULT_SHOW_AZIMUTH_CONES = false

    // --- Apparence ---
    var themeMode = mutableIntStateOf(0)
    var isOledMode = mutableStateOf(true)
    var isBlurEnabled = mutableStateOf(true)
    var colorPalette = mutableStateOf(DEFAULT_COLOR_PALETTE)
    // 0 = Plein écran, 1 = Fractionné
    var displayStyle = mutableIntStateOf(0)

    var menuSize = mutableStateOf("normal")

    // État validé une fois au démarrage/onboarding, réutilisé par l'accueil pour éviter les faux bandeaux.
    var localDatabaseState = mutableStateOf<GeoTowerDatabaseValidator.LocalDatabaseState?>(null)

    //Notification de téléchargement
    var enableUpdateNotifications = mutableStateOf(true) // Désactivé par défaut

    // Statut de l'antenne
    var siteShowStatus = mutableStateOf(true)
    var siteShowPhotos = mutableStateOf(true)
    var shareSiteStatus = mutableStateOf(true)
    var shareSiteSpeedtest = mutableStateOf(true) // 🚨 NEW

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
    var showMapLocationMarker = mutableStateOf(true)

    // --- FILTRES : OPÉRATEURS ---
    var showOrange = mutableStateOf(true)
    var showSfr = mutableStateOf(true)
    var showBouygues = mutableStateOf(true)
    var showFree = mutableStateOf(true)
    var selectedOperatorKeys = mutableStateOf(OperatorColors.defaultVisibleKeys)

    // --- FILTRES : AZIMUTS ---
    var showAzimuths = mutableStateOf(DEFAULT_SHOW_AZIMUTH_LINES)

    var showAzimuthsCone = mutableStateOf(DEFAULT_SHOW_AZIMUTH_CONES)

    // --- FILTRES : AFFICHAGE DES SITES ---
    var showSitesInService = mutableStateOf(true)
    var showSitesOutOfService = mutableStateOf(true)

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

    var siteFreqGridDisplay = mutableStateOf(false)

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

    // Variable globale pour l'opérateur par défaut
    val defaultOperator = mutableStateOf("Aucun")

    // Mode de navigation (0 = Défilant, 1 = Pages)
    var navMode = mutableIntStateOf(0)

    // --- MODE D'AFFICHAGE ---
    var uiMode = mutableStateOf(AppUiMode.Auto)
    val useOneUiDesign: Boolean
        get() = uiMode.value.usesOneUi()

    //Masquer les boutons sur l'écran d'accueil

    val showNearbyPage = mutableStateOf(true)
    val showMapPage = mutableStateOf(true)
    val showCompassPage = mutableStateOf(true)
    val showStatsPage = mutableStateOf(true)

    // --- Capteurs matériels ---
    var hasCompass = mutableStateOf(true) // Vrai par défaut, vérifié au lancement

    // --- AFFICHAGE DES SPEEDTESTS
    var siteShowSpeedtest = mutableStateOf(true)

    // --- FILTRES : PHOTOS & SCHÉMAS ---
    var siteShowCellularFrPhotos = mutableStateOf(true)
    var siteShowSignalQuestPhotos = mutableStateOf(true)
    var siteShowSchemes = mutableStateOf(true)

    // --- FONCTION POUR CHARGER LA MÉMOIRE AU DÉMARRAGE ---
    fun loadSavedFilters(prefs: android.content.SharedPreferences) {

        //Notification de téléchargement
        enableUpdateNotifications.value = prefs.getBoolean("enable_update_notifications", true)
        colorPalette.value = prefs.getString(PREF_COLOR_PALETTE, DEFAULT_COLOR_PALETTE) ?: DEFAULT_COLOR_PALETTE
        val model = DeviceProfile.model
        val device = DeviceProfile.device

        AppLogger.d("GeoTower", "Fold device detection model=$model device=$device")

        //Statut
        shareSiteStatus.value = prefs.getBoolean("share_site_status", true)
        shareSiteSpeedtest.value = prefs.getBoolean("share_site_speedtest", true) // 🚨 NEW

        // Si c'est un Z Fold OU un Pixel Fold, la valeur par défaut est 1 (Fractionné), sinon 0 (Plein écran)
        val defaultDisplayStyle = if (DeviceProfile.prefersSplitDisplay) 1 else 0

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
        selectedOperatorKeys.value = loadSelectedOperatorKeys(prefs)

        showAzimuths.value = prefs.getBoolean(PREF_SHOW_AZIMUTH_LINES, DEFAULT_SHOW_AZIMUTH_LINES)
        showAzimuthsCone.value = prefs.getBoolean(PREF_SHOW_AZIMUTH_CONES, DEFAULT_SHOW_AZIMUTH_CONES)
        showMapLocationMarker.value = prefs.getBoolean(PREF_SHOW_MAP_LOCATION_MARKER, true)

        showSitesInService.value = prefs.getBoolean("show_sites_in_service", true)
        showSitesOutOfService.value = prefs.getBoolean("show_sites_out_of_service", true)

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

        //AFFICHAGE DES FREQUENCES EN GRILLE
        siteFreqGridDisplay.value = prefs.getBoolean("site_freq_grid_display", false)

        // --- CHARGEMENT SPECTRE ---
        siteShowSpectrum.value = prefs.getBoolean("site_show_spectrum", true)
        siteShowSpectrumBand.value = prefs.getBoolean("site_show_spectrum_band", true)
        siteShowSpectrumTotal.value = prefs.getBoolean("site_show_spectrum_total", true)

        // AFFICHAGE DU STATUT
        siteShowStatus.value = prefs.getBoolean("site_show_status", true)
        siteShowPhotos.value = prefs.getBoolean("page_site_photos", true)

        // --- AFFICHAGE DES SPEEDTESTS
        siteShowSpeedtest.value = prefs.getBoolean("site_show_speedtest", true)

        // --- AFFICHAGE DES PHOTOS & SCHÉMAS ---
        siteShowCellularFrPhotos.value = prefs.getBoolean("site_show_cellularfr_photos", true)
        siteShowSignalQuestPhotos.value = prefs.getBoolean("site_show_signalquest_photos", true)
        siteShowSchemes.value = prefs.getBoolean("site_show_schemes", true)

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

    fun setSelectedOperatorKeys(keys: Set<String>): Set<String> {
        val normalizedKeys = keys
            .mapNotNull { OperatorColors.specForKey(it)?.key }
            .toSet()

        selectedOperatorKeys.value = normalizedKeys
        showOrange.value = OperatorColors.ORANGE_KEY in normalizedKeys
        showSfr.value = OperatorColors.SFR_KEY in normalizedKeys
        showBouygues.value = OperatorColors.BOUYGUES_KEY in normalizedKeys
        showFree.value = OperatorColors.FREE_KEY in normalizedKeys

        return normalizedKeys
    }

    fun saveSelectedOperatorKeys(prefs: SharedPreferences, keys: Set<String>) {
        val normalizedKeys = setSelectedOperatorKeys(keys)

        prefs.edit()
            .putStringSet(PREF_SELECTED_OPERATORS, normalizedKeys)
            .putBoolean("show_orange", showOrange.value)
            .putBoolean("show_sfr", showSfr.value)
            .putBoolean("show_bouygues", showBouygues.value)
            .putBoolean("show_free", showFree.value)
            .apply()
    }

    private fun loadSelectedOperatorKeys(prefs: android.content.SharedPreferences): Set<String> {
        val savedKeys = prefs.getStringSet(PREF_SELECTED_OPERATORS, null)
            ?.mapNotNull { OperatorColors.specForKey(it)?.key }
            ?.toSet()
        if (!savedKeys.isNullOrEmpty()) return savedKeys

        return buildSet {
            if (showOrange.value) add(OperatorColors.ORANGE_KEY)
            if (showSfr.value) add(OperatorColors.SFR_KEY)
            if (showBouygues.value) add(OperatorColors.BOUYGUES_KEY)
            if (showFree.value) add(OperatorColors.FREE_KEY)
            addAll(OperatorColors.overseas.map { it.key })
        }
    }
}
