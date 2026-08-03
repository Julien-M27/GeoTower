package fr.geotower.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import fr.geotower.data.build.LocalBuildCapability
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.models.RadioMapCategoryMasks
import fr.geotower.data.outages.OutageLocalConfig
import fr.geotower.data.workers.OutageBackgroundScheduler

object AppConfig {
    const val PREF_COLOR_PALETTE = "color_palette"
    const val DEFAULT_COLOR_PALETTE = "dynamic"
    const val PREF_SELECTED_OPERATORS = "selected_operator_keys"
    const val PREF_UI_MODE = "ui_mode"
    const val PREF_UI_SCALE_PERCENT = "ui_scale_percent"
    const val PREF_LEGACY_MENU_SIZE = "menuSize"
    const val PREF_SHOW_MAP_LOCATION_MARKER = "show_map_location_marker"
    const val PREF_SHOW_AZIMUTH_LINES = "show_azimuths"
    const val PREF_SHOW_AZIMUTH_CONES = "show_azimuths_cone"
    const val PREF_SHOW_RADIO_SITES = "show_radio_sites"
    const val PREF_SHOW_RADIO_TV = "show_radio_tv"
    const val PREF_SHOW_RADIO_BROADCAST = "show_radio_broadcast"
    const val PREF_SHOW_RADIO_PRIVATE_MOBILE = "show_radio_private_mobile"
    const val PREF_SHOW_RADIO_FH = "show_radio_fh"
    const val PREF_SHOW_RADIO_OTHER = "show_radio_other"
    const val PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS = "show_signalquest_coverage_points"
    const val PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS = "signalquest_coverage_operator_keys"
    const val PREF_HIDE_UNDERGROUND_SITES = "hide_underground_sites"
    const val PREF_SHOW_ONLY_ZB_SITES = "show_only_zb_sites"
    const val PREF_SHOW_PROJECT_SITES = "show_project_sites"
    const val DEFAULT_SHOW_AZIMUTH_LINES = true
    const val DEFAULT_SHOW_AZIMUTH_CONES = false
    const val PREF_LOW_POWER_LEVEL = "low_power_level"
    const val PREF_LOW_POWER_FOLLOW_SYSTEM = "low_power_follow_system"

    // Suivi live : clé historique (« notifications live ») gardée pour ne pas réinitialiser le
    // réglage des utilisateurs. Elle ne pilote que le service de suivi, pas le droit de notifier.
    const val PREF_ENABLE_LIVE_TRACKING = "enable_live_notifications"

    // Mode « traitement local » : 0 Serveur / 1 Sites HS / 2 Base+sites / 3 Autonomie max.
    const val PREF_LOCAL_MODE_LEVEL = "local_mode_level"

    // Mode simplifié : carte au lancement, tiroir latéral à la place de l'accueil, fiche
    // support et fiches opérateurs fusionnées. Un seul interrupteur pilote l'ensemble.
    const val PREF_SIMPLE_MODE = "simple_mode"

    // --- Apparence ---
    var themeMode = mutableIntStateOf(0)
    var isOledMode = mutableStateOf(true)
    var isBlurEnabled = mutableStateOf(true)
    var colorPalette = mutableStateOf(DEFAULT_COLOR_PALETTE)
    var appLogoDrawingChoice = mutableStateOf(AppLogoDrawingResources.AUTO)
    // 0 = Plein écran, 1 = Fractionné
    var displayStyle = mutableIntStateOf(0)

    // Taille de l'interface, en pourcentage (100 = rendu de reference "normal" historique).
    // Le facteur d'ecran est applique par-dessus dans GeoTowerUiStyle (dimensionnement adaptatif).
    var uiScalePercent = mutableIntStateOf(100)

    // État validé une fois au démarrage/onboarding, réutilisé par l'accueil pour éviter les faux bandeaux.
    var localDatabaseState = mutableStateOf<GeoTowerDatabaseValidator.LocalDatabaseState?>(null)

    // Interrupteur maître des notifications de l'app (voir AppNotifications) : séparé du suivi live,
    // il permet d'avoir les notifications sans le tracking.
    var enableAppNotifications = mutableStateOf(AppNotifications.DEFAULT_ENABLED)

    //Notification de téléchargement
    var enableUpdateNotifications = mutableStateOf(true) // Désactivé par défaut

    // Statut de l'antenne
    var siteShowStatus = mutableStateOf(true)
    var siteShowPhotos = mutableStateOf(true)
    var shareSiteStatus = mutableStateOf(true)
    var shareSiteSpeedtest = mutableStateOf(true) // 🚨 NEW

    // Langue globale de l'application (Système par défaut au 1er lancement)
    val appLanguage = mutableStateOf("Système")

    // Suivi live (service GPS de premier plan). Le nom de la clé reste « enable_live_notifications »
    // pour ne pas perdre le réglage des utilisateurs, mais ce n'est PAS un droit de notifier :
    // celui-là appartient à AppNotifications.PREF_ENABLED, indépendant.
    var enableLiveTracking = mutableStateOf(false)

    // --- Mode faible consommation ---
    // 0 = Normal, 1 = Éco (équilibré), 2 = Éco+ (agressif). Le niveau effectif est calculé par PowerProfile.
    var lowPowerLevel = mutableIntStateOf(0)
    // Aligne le niveau sur l'économie d'énergie d'Android (force au moins Éco quand le système est en éco).
    var lowPowerFollowSystem = mutableStateOf(false)

    // --- Mode « traitement local » (autonomie serveur) ---
    // Niveau choisi : 0 Serveur / 1 Sites HS local / 2 Base+sites local / 3 Autonomie max.
    var localModeLevel = mutableIntStateOf(0)
    // Éligibilité de l'appareil à la génération locale de la base (RAM/stockage), évaluée au runtime.
    var localBuildEligible = mutableStateOf(false)

    // --- Mode simplifié ---
    // Choix de l'utilisateur. Passer par [simpleModeActive] pour l'appliquer : le kill-switch
    // distant peut le neutraliser sans effacer la préférence.
    var simpleMode = mutableStateOf(false)

    // --- UNITÉS DE MESURE ---
    // 0 = Kilomètres (km), 1 = Miles (mi)
    var distanceUnit = mutableIntStateOf(0)
    // 0 = km/h, 1 = mph
    var speedUnit = mutableIntStateOf(0)

    // --- Carte ---
    var mapProvider = mutableIntStateOf(0)
    var ignStyle = mutableIntStateOf(0)

    val signalQuestCoverageOperatorKeys = setOf(
        OperatorColors.ORANGE_KEY,
        OperatorColors.SFR_KEY,
        OperatorColors.BOUYGUES_KEY,
        OperatorColors.FREE_KEY
    )
    // Un seul opérateur peut être affiché à la fois pour la couverture SignalQuest.
    val defaultSignalQuestCoverageOperatorKeys = setOf(OperatorColors.ORANGE_KEY)

    var showSpeedometer = mutableStateOf(true)
    // Outil de mesure : true = supprimer un trait reconnecte la chaîne (recalcule avec le point
    // précédent) ; false = les autres traits ne bougent pas (comportement par défaut).
    var measureReconnectOnDelete = mutableStateOf(false)
    var showMapLocationMarker = mutableStateOf(true)
    var showRadioSites = mutableStateOf(false)
    var showRadioTv = mutableStateOf(false)
    var showRadioBroadcast = mutableStateOf(false)
    var showRadioPrivateMobile = mutableStateOf(false)
    var showRadioFh = mutableStateOf(false)
    var showRadioOther = mutableStateOf(false)
    var showSignalQuestCoveragePoints = mutableStateOf(false)
    var selectedSignalQuestCoverageOperatorKeys = mutableStateOf(defaultSignalQuestCoverageOperatorKeys)

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

    // Troisieme statut, a cote de « En service » / « En Panne » : les sites « totalement en
    // projet », dont aucune emission n'est en service (has_active = 0 et statut non actif,
    // cf. LocalisationEntity.isDeclaredActive()). Un site qui n'a que quelques nouvelles
    // frequences en projet reste, lui, dans le statut « En service ».
    var showProjectSites = mutableStateOf(true)

    var hideUndergroundSites = mutableStateOf(false)
    var showOnlyZbSites = mutableStateOf(false)

    // --- SLIDER TEMPOREL (apparition des sites par date de mise en service) ---
    // Ephemere : non persiste (l'app ne doit pas rouvrir bloquee dans le passe).
    // Lu par le MapViewModel pour forcer le chargement detaille (pas de clustering) quand actif.
    var timeSliderActive = mutableStateOf(false)

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
    var f5G_1400 = mutableStateOf(true)
    var f5G_2100 = mutableStateOf(true)
    var f5G_3500 = mutableStateOf(true)
    var f5G_4200 = mutableStateOf(true)
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
    var siteF5G_1400 = mutableStateOf(true)
    var siteF5G_2100 = mutableStateOf(true)
    var siteF5G_3500 = mutableStateOf(true)
    var siteF5G_4200 = mutableStateOf(true)
    var siteF5G_26000 = mutableStateOf(true)

    var siteFreqGridDisplay = mutableStateOf(false)

    // --- FILTRES : LARGEUR DE BANDE ET SPECTRE (DÉTAILS DU SITE) ---
    // Les clés de préférences gardent leur nom historique « site_show_spectrum* » : ces trois
    // interrupteurs pilotent en réalité la largeur de bande (anciennement libellée « Spectre »).
    var siteShowBandwidth = mutableStateOf(true) // Switch principal maître
    var siteShowBandwidthPerRange = mutableStateOf(true)
    var siteShowBandwidthTotal = mutableStateOf(true)

    /** Spectre occupé (« 708-718 MHz ») : colonne du tableau des antennes et lignes de la fiche. */
    var siteShowSpectrumRanges = mutableStateOf(true)

    // --- ORDRE DES TECHNOLOGIES ET FRÉQUENCES (SITE) ---
    var siteTechnoOrder = mutableStateOf(listOf("5G", "4G", "3G", "2G", "FH"))

    var siteFreqOrder5G = mutableStateOf(listOf("26000", "4200", "3500", "2100", "1400", "700"))
    var siteFreqOrder4G = mutableStateOf(listOf("2600", "2100", "1800", "900", "800", "700"))
    var siteFreqOrder3G = mutableStateOf(listOf("2100", "900"))
    var siteFreqOrder2G = mutableStateOf(listOf("1800", "900"))

    // --- PARTAGE PAR DÉFAUT (CARTE) ---
    var shareMapAzimuths = mutableStateOf(true)
    var shareMapSpeedometer = mutableStateOf(false)
    var shareMapScale = mutableStateOf(true)
    var shareMapAttribution = mutableStateOf(true)
    var shareMapConfidential = mutableStateOf(false)

    // Variable globale pour l'opérateur par défaut
    val defaultOperator = mutableStateOf("Aucun")

    // Mode de navigation des réglages sur GRAND ÉCRAN (0 = défilement continu, 1 = pages).
    // Défaut 1 : comme sur téléphone, on arrive sur l'accueil par sections. Inerte sur téléphone,
    // où c'est settingsSectionsMode qui décide.
    const val PREF_NAV_MODE = "nav_mode"
    const val DEFAULT_NAV_MODE = 1
    var navMode = mutableIntStateOf(DEFAULT_NAV_MODE)

    // Téléphone : accueil des réglages par sections (true) ou tout sur une seule page (false).
    // Le mode « pages » des grands écrans reste piloté par navMode (il a sa barre latérale).
    const val PREF_SETTINGS_SECTIONS_MODE = "settings_sections_mode"
    var settingsSectionsMode = mutableStateOf(true)

    // --- MODE D'AFFICHAGE ---
    var uiMode = mutableStateOf(AppUiMode.Auto)
    val useOneUiDesign: Boolean
        get() = uiMode.value.usesOneUi()

    //Masquer les boutons sur l'écran d'accueil

    val showNearbyPage = mutableStateOf(true)
    val showMapPage = mutableStateOf(true)
    val showCompassPage = mutableStateOf(true)
    val showStatsPage = mutableStateOf(true)
    var statsDisplayMode = mutableStateOf(StatsDisplayMode.Both)

    // --- Capteurs matériels ---
    var hasCompass = mutableStateOf(true) // Vrai par défaut, vérifié au lancement

    // --- AFFICHAGE DES SPEEDTESTS
    var siteShowSpeedtest = mutableStateOf(true)

    // --- FILTRES : PHOTOS & SCHÉMAS ---
    var siteShowCellularFrPhotos = mutableStateOf(true)
    var siteShowSignalQuestPhotos = mutableStateOf(true)
    var siteShowSchemes = mutableStateOf(true)
    var siteShowPhotoExif = mutableStateOf(true)

    fun loadMapDisplayPreferences(prefs: SharedPreferences) {
        defaultOperator.value = MapDisplayPrefs.defaultOperator.read(prefs)

        showOrange.value = MapDisplayPrefs.showOrange.read(prefs)
        showSfr.value = MapDisplayPrefs.showSfr.read(prefs)
        showBouygues.value = MapDisplayPrefs.showBouygues.read(prefs)
        showFree.value = MapDisplayPrefs.showFree.read(prefs)
        setSelectedOperatorKeys(loadSelectedOperatorKeys(prefs))

        showAzimuths.value = MapDisplayPrefs.showAzimuthLines.read(prefs)
        showAzimuthsCone.value = MapDisplayPrefs.showAzimuthCones.read(prefs)
        showMapLocationMarker.value = MapDisplayPrefs.showLocationMarker.read(prefs)
        val legacyShowRadioSites = MapDisplayPrefs.showRadioSites.read(prefs)
        showRadioTv.value = prefs.getBoolean(PREF_SHOW_RADIO_TV, legacyShowRadioSites)
        showRadioBroadcast.value = prefs.getBoolean(PREF_SHOW_RADIO_BROADCAST, legacyShowRadioSites)
        showRadioPrivateMobile.value = prefs.getBoolean(PREF_SHOW_RADIO_PRIVATE_MOBILE, legacyShowRadioSites)
        showRadioFh.value = prefs.getBoolean(PREF_SHOW_RADIO_FH, legacyShowRadioSites)
        showRadioOther.value = prefs.getBoolean(PREF_SHOW_RADIO_OTHER, legacyShowRadioSites)
        updateShowRadioSitesFromCategoryFilters()
        showSignalQuestCoveragePoints.value = prefs.getBoolean(PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS, false)
        selectedSignalQuestCoverageOperatorKeys.value = loadSignalQuestCoverageOperatorKeys(prefs)
        showSpeedometer.value = MapDisplayPrefs.showSpeedometer.read(prefs)
        measureReconnectOnDelete.value = MapDisplayPrefs.measureReconnectOnDelete.read(prefs)

        showSitesInService.value = MapDisplayPrefs.showSitesInService.read(prefs)
        showSitesOutOfService.value = MapDisplayPrefs.showSitesOutOfService.read(prefs)
        hideUndergroundSites.value = MapDisplayPrefs.hideUndergroundSites.read(prefs)
        showOnlyZbSites.value = MapDisplayPrefs.showOnlyZbSites.read(prefs)
        showProjectSites.value = MapDisplayPrefs.showProjectSites.read(prefs)

        showTechno2G.value = MapDisplayPrefs.showTechno2G.read(prefs)
        showTechno3G.value = MapDisplayPrefs.showTechno3G.read(prefs)
        showTechno4G.value = MapDisplayPrefs.showTechno4G.read(prefs)
        showTechno5G.value = MapDisplayPrefs.showTechno5G.read(prefs)
        showTechnoFH.value = MapDisplayPrefs.showTechnoFh.read(prefs)

        f2G_900.value = MapDisplayPrefs.f2G900.read(prefs)
        f2G_1800.value = MapDisplayPrefs.f2G1800.read(prefs)

        f3G_900.value = MapDisplayPrefs.f3G900.read(prefs)
        f3G_2100.value = MapDisplayPrefs.f3G2100.read(prefs)

        f4G_700.value = MapDisplayPrefs.f4G700.read(prefs)
        f4G_800.value = MapDisplayPrefs.f4G800.read(prefs)
        f4G_900.value = MapDisplayPrefs.f4G900.read(prefs)
        f4G_1800.value = MapDisplayPrefs.f4G1800.read(prefs)
        f4G_2100.value = MapDisplayPrefs.f4G2100.read(prefs)
        f4G_2600.value = MapDisplayPrefs.f4G2600.read(prefs)

        f5G_700.value = MapDisplayPrefs.f5G700.read(prefs)
        f5G_1400.value = MapDisplayPrefs.f5G1400.read(prefs)
        f5G_2100.value = MapDisplayPrefs.f5G2100.read(prefs)
        f5G_3500.value = MapDisplayPrefs.f5G3500.read(prefs)
        f5G_4200.value = MapDisplayPrefs.f5G4200.read(prefs)
        f5G_26000.value = MapDisplayPrefs.f5G26000.read(prefs)
    }

    // --- Mode « traitement local » : niveau effectif + dérivés ---
    /** Niveau effectif : le kill-switch distant [RemoteFeatureFlags.Features.LOCAL_MODE_ENABLED] peut forcer 0. */
    fun effectiveLocalModeLevel(): Int =
        if (RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LOCAL_MODE_ENABLED)) {
            localModeLevel.intValue
        } else {
            0
        }

    /**
     * Niveau ≥ 1 : les sites en panne sont récupérés/localisés sur l'appareil.
     *
     * SEULE vérité sur la source des pannes : il n'existe plus de réglage concurrent. Tout ce qui
     * décide « local ou serveur » (repository, planification en fond, worker, écrans) doit passer
     * par ici, sinon on retombe sur l'incohérence d'avant (un planificateur qui lance un worker
     * qui refuse de travailler).
     */
    fun outagesLocal(): Boolean = effectiveLocalModeLevel() >= 1

    /** Niveau ≥ 2 ET appareil éligible : la base est générée localement (bloque le téléchargement serveur). */
    fun dbForcedLocal(): Boolean = effectiveLocalModeLevel() >= 2 && localBuildEligible.value

    /** Niveau ≥ 3 : coupe SignalQuest (photos + envoi), la vérif de mise à jour et l'API live. */
    fun blockCommunityAndUpdates(): Boolean = effectiveLocalModeLevel() >= 3

    /** Applique un niveau : persiste, recalcule l'éligibilité et réconcilie la planif des pannes. */
    fun setLocalModeLevel(context: Context, level: Int) {
        val clamped = level.coerceIn(0, 3)
        localModeLevel.intValue = clamped
        context.applicationContext
            .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_LOCAL_MODE_LEVEL, clamped)
            .apply()
        localBuildEligible.value = LocalBuildCapability.evaluate(context).eligible
        OutageBackgroundScheduler.reconcile(context)
    }

    /**
     * Migration ponctuelle : l'ancien interrupteur « Récupérer les pannes sur l'appareil »
     * (clé `outages_source` du store "settings") a été absorbé par le niveau de traitement local.
     * Un ancien « local » devient le niveau 1, puis la clé est supprimée pour ne plus jamais être
     * relue. Idempotent, et à appeler AVANT la lecture de [PREF_LOCAL_MODE_LEVEL].
     */
    fun migrateLegacyOutageSourcePref(context: Context) {
        val appContext = context.applicationContext
        val outagePrefs = appContext
            .getSharedPreferences(OutageLocalConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = outagePrefs.getString(OutageLocalConfig.LEGACY_KEY_SOURCE, null) ?: return
        outagePrefs.edit().remove(OutageLocalConfig.LEGACY_KEY_SOURCE).apply()
        if (legacy != OutageLocalConfig.LEGACY_SOURCE_LOCAL) return

        // On ne rétrograde jamais un utilisateur déjà en niveau 2 ou 3.
        val appPrefs = appContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        if (appPrefs.getInt(PREF_LOCAL_MODE_LEVEL, 0) < 1) {
            appPrefs.edit().putInt(PREF_LOCAL_MODE_LEVEL, 1).apply()
        }
    }

    // --- Mode simplifié : état effectif + application ---
    /**
     * SEULE vérité pour appliquer le mode simplifié : le choix de l'utilisateur ET le kill-switch
     * distant. Tout ce qui change de comportement (démarrage, tiroir, fusion des fiches) doit
     * passer par ici, sinon un kill-switch ne coupe qu'une partie du mode et l'app devient bancale.
     */
    fun simpleModeActive(): Boolean =
        simpleMode.value &&
            RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIMPLE_MODE_ENABLED)

    /**
     * Même réponse que [simpleModeActive], mais lisible avant que [loadSavedFilters] ait tourné :
     * MainActivity choisit sa destination de démarrage avant de charger les préférences.
     */
    fun isSimpleModeActive(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_SIMPLE_MODE, false) &&
            RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIMPLE_MODE_ENABLED)

    /**
     * Style d'affichage effectif. Le mode simplifié impose le plein écran : les fiches y sont déjà
     * fusionnées, garder en plus le volet fractionné ferait un troisième comportement à maintenir.
     */
    fun effectiveDisplayStyle(): Int = if (simpleModeActive()) 0 else displayStyle.intValue

    /** Applique un choix de mode simplifié : état en mémoire + persistance. */
    fun setSimpleMode(context: Context, enabled: Boolean) {
        simpleMode.value = enabled
        context.applicationContext
            .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SIMPLE_MODE, enabled)
            .apply()
    }

    /** Route d'accueil : le mode simplifié n'a pas de page d'accueil, la carte en tient lieu. */
    fun homeRoute(): String = if (simpleModeActive()) "map" else "home"

    // --- FONCTION POUR CHARGER LA MÉMOIRE AU DÉMARRAGE ---
    fun loadSavedFilters(prefs: android.content.SharedPreferences) {

        // Interrupteur maître des notifications (indépendant du suivi live).
        enableAppNotifications.value = AppNotifications.enabled(prefs)

        //Notification de téléchargement
        enableUpdateNotifications.value = prefs.getBoolean("enable_update_notifications", true)
        colorPalette.value = prefs.getString(PREF_COLOR_PALETTE, DEFAULT_COLOR_PALETTE) ?: DEFAULT_COLOR_PALETTE
        appLogoDrawingChoice.value = AppLogoDrawingResources.normalize(
            prefs.getString(AppLogoDrawingResources.PREF_KEY, AppLogoDrawingResources.AUTO)
        )
        val model = DeviceProfile.model
        val device = DeviceProfile.device

        AppLogger.d("GeoTower", "Fold device detection model=$model device=$device")

        //Statut
        shareSiteStatus.value = prefs.getBoolean("share_site_status", true)
        shareSiteSpeedtest.value = prefs.getBoolean("share_site_speedtest", true) // 🚨 NEW

        // Si c'est un Z Fold OU un Pixel Fold, la valeur par défaut est 1 (Fractionné), sinon 0 (Plein écran)
        val defaultDisplayStyle = if (DeviceProfile.prefersSplitDisplay) 1 else 0

        // Suivi live (clé historique conservée)
        enableLiveTracking.value = prefs.getBoolean(PREF_ENABLE_LIVE_TRACKING, false)

        // Mode faible consommation (0 Normal / 1 Éco / 2 Éco+)
        lowPowerLevel.intValue = prefs.getInt(PREF_LOW_POWER_LEVEL, 0)
        lowPowerFollowSystem.value = prefs.getBoolean(PREF_LOW_POWER_FOLLOW_SYSTEM, false)

        // Mode « traitement local » (niveau 0..3).
        localModeLevel.intValue = prefs.getInt(PREF_LOCAL_MODE_LEVEL, 0).coerceIn(0, 3)

        // Mode simplifié (carte au lancement, tiroir latéral, fiches fusionnées).
        simpleMode.value = prefs.getBoolean(PREF_SIMPLE_MODE, false)

        // ✅ CHARGEMENT DU STYLE D'AFFICHAGE (avec la nouvelle valeur par défaut)
        displayStyle.intValue = prefs.getInt("display_style", defaultDisplayStyle)

        // --- CHARGEMENT DES UNITÉS ---
        distanceUnit.intValue = prefs.getInt("distance_unit", 0)
        speedUnit.intValue = prefs.getInt("speed_unit", 0)

        loadMapDisplayPreferences(prefs)
        statsDisplayMode.value = StatsPreferences.displayMode(prefs)

        // Barre latérale de défilement, activable page par page.
        PageScrollPrefs.load(prefs)

        // Relais de découverte de « Personnalisation des pages » (bulles + rappel de bas de page).
        PageCustomizationPrefs.load(prefs)

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
        siteF5G_1400.value = prefs.getBoolean("site_f5g_1400", true)
        siteF5G_2100.value = prefs.getBoolean("site_f5g_2100", true)
        siteF5G_3500.value = prefs.getBoolean("site_f5g_3500", true)
        siteF5G_4200.value = prefs.getBoolean("site_f5g_4200", true)
        siteF5G_26000.value = prefs.getBoolean("site_f5g_26000", true)

        //AFFICHAGE DES FREQUENCES EN GRILLE
        siteFreqGridDisplay.value = prefs.getBoolean("site_freq_grid_display", false)

        // --- CHARGEMENT LARGEUR DE BANDE (clés historiques « site_show_spectrum* ») ---
        siteShowBandwidth.value = prefs.getBoolean("site_show_spectrum", true)
        siteShowBandwidthPerRange.value = prefs.getBoolean("site_show_spectrum_band", true)
        siteShowBandwidthTotal.value = prefs.getBoolean("site_show_spectrum_total", true)

        // --- CHARGEMENT SPECTRE (plages de fréquences) ---
        siteShowSpectrumRanges.value = prefs.getBoolean("site_show_spectrum_ranges", true)

        // AFFICHAGE DU STATUT
        siteShowStatus.value = prefs.getBoolean("site_show_status", true)
        siteShowPhotos.value = prefs.getBoolean("page_site_photos", true)

        // --- AFFICHAGE DES SPEEDTESTS
        siteShowSpeedtest.value = prefs.getBoolean("site_show_speedtest", true)

        // --- AFFICHAGE DES PHOTOS & SCHÉMAS ---
        siteShowCellularFrPhotos.value = prefs.getBoolean("site_show_cellularfr_photos", true)
        siteShowSignalQuestPhotos.value = prefs.getBoolean("site_show_signalquest_photos", true)
        siteShowSchemes.value = prefs.getBoolean("site_show_schemes", true)
        siteShowPhotoExif.value = prefs.getBoolean("site_show_photo_exif", true)

        // --- CHARGEMENT DE L'ORDRE DES TECHNOLOGIES ET FRÉQUENCES ---
        val tOrder = prefs.getString("site_techno_order", "5G,4G,3G,2G,FH")
        siteTechnoOrder.value = tOrder?.split(",") ?: listOf("5G", "4G", "3G", "2G", "FH")

        val f5gDefaultOrder = listOf("26000", "4200", "3500", "2100", "1400", "700")
        val f5gOrder = prefs.getString("site_freq_5g_order", f5gDefaultOrder.joinToString(","))
        siteFreqOrder5G.value = normalizeSavedOrder(f5gOrder?.split(",") ?: f5gDefaultOrder, f5gDefaultOrder)

        val f4gDefaultOrder = listOf("2600", "2100", "1800", "900", "800", "700")
        val f4gOrder = prefs.getString("site_freq_4g_order", f4gDefaultOrder.joinToString(","))
        siteFreqOrder4G.value = normalizeSavedOrder(f4gOrder?.split(",") ?: f4gDefaultOrder, f4gDefaultOrder)

        val f3gOrder = prefs.getString("site_freq_3g_order", "2100,900")
        siteFreqOrder3G.value = f3gOrder?.split(",") ?: listOf("2100", "900")

        val f2gOrder = prefs.getString("site_freq_2g_order", "1800,900")
        siteFreqOrder2G.value = f2gOrder?.split(",") ?: listOf("1800", "900")

        // --- CHARGEMENT PARTAGE DE LA CARTE ---
        shareMapAzimuths.value = prefs.getBoolean("share_map_azimuths", true)
        shareMapSpeedometer.value = prefs.getBoolean("share_map_speedometer", false)
        shareMapScale.value = prefs.getBoolean("share_map_scale", true)
        shareMapAttribution.value = prefs.getBoolean("share_map_attribution", true)
        shareMapConfidential.value = prefs.getBoolean("share_map_confidential", false)
    }

    // --- TAILLE DE L'INTERFACE ---
    /**
     * Convertit l'ancien reglage menuSize (petit/normal/large) en pourcentage de taille d'UI.
     * L'ancien "normal" (x0.925) devient la reference 100% : petit = 0.85/0.925 ~= 92, large = 1.0/0.925 ~= 108.
     */
    fun menuSizeLegacyToPercent(menuSize: String?): Int = when (menuSize) {
        "petit" -> 92
        "large" -> 108
        else -> 100
    }

    /** Lit le pourcentage de taille d'UI, avec migration transparente depuis l'ancien menuSize. */
    fun readUiScalePercent(prefs: SharedPreferences): Int {
        if (prefs.contains(PREF_UI_SCALE_PERCENT)) {
            return prefs.getInt(PREF_UI_SCALE_PERCENT, 100)
        }
        return menuSizeLegacyToPercent(prefs.getString(PREF_LEGACY_MENU_SIZE, null))
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

    fun saveSelectedSignalQuestCoverageOperatorKeys(prefs: SharedPreferences, keys: Set<String>) {
        val normalizedKeys = normalizeSignalQuestCoverageOperatorKeys(keys)
        selectedSignalQuestCoverageOperatorKeys.value = normalizedKeys
        prefs.edit()
            .putStringSet(PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS, normalizedKeys)
            .apply()
    }

    fun radioMapCategoryMask(): Int {
        var mask = 0
        if (showRadioTv.value) mask = mask or RadioMapCategoryMasks.TV
        if (showRadioBroadcast.value) mask = mask or RadioMapCategoryMasks.RADIO
        if (showRadioPrivateMobile.value) mask = mask or RadioMapCategoryMasks.PRIVATE_MOBILE
        if (showRadioFh.value) mask = mask or RadioMapCategoryMasks.FH
        if (showRadioOther.value) mask = mask or RadioMapCategoryMasks.OTHER
        return mask
    }

    fun updateShowRadioSitesFromCategoryFilters() {
        showRadioSites.value = radioMapCategoryMask() != 0
    }

    private fun loadSelectedOperatorKeys(prefs: android.content.SharedPreferences): Set<String> {
        if (prefs.contains(PREF_SELECTED_OPERATORS)) {
            return prefs.getStringSet(PREF_SELECTED_OPERATORS, emptySet())
                .orEmpty()
                .mapNotNull { OperatorColors.specForKey(it)?.key }
                .toSet()
        }

        return buildSet {
            if (showOrange.value) add(OperatorColors.ORANGE_KEY)
            if (showSfr.value) add(OperatorColors.SFR_KEY)
            if (showBouygues.value) add(OperatorColors.BOUYGUES_KEY)
            if (showFree.value) add(OperatorColors.FREE_KEY)
            addAll(OperatorColors.overseas.map { it.key })
        }
    }

    private fun loadSignalQuestCoverageOperatorKeys(prefs: android.content.SharedPreferences): Set<String> {
        if (!prefs.contains(PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS)) {
            return defaultSignalQuestCoverageOperatorKeys
        }
        return normalizeSignalQuestCoverageOperatorKeys(
            prefs.getStringSet(PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS, defaultSignalQuestCoverageOperatorKeys)
                .orEmpty()
        )
    }

    private fun normalizeSignalQuestCoverageOperatorKeys(keys: Set<String>): Set<String> {
        val valid = keys
            .mapNotNull { OperatorColors.specForKey(it)?.key }
            .filter { it in signalQuestCoverageOperatorKeys }
        // Un seul opérateur à la fois : on garde une clé unique (ordre déterministe si legacy multi).
        val chosen = OperatorColors.orderedKeys.firstOrNull { it in valid } ?: valid.firstOrNull()
        return chosen?.let { setOf(it) } ?: emptySet()
    }

    private fun normalizeSavedOrder(savedOrder: List<String>, defaultOrder: List<String>): List<String> {
        val known = defaultOrder.toSet()
        val normalized = savedOrder
            .map { it.trim() }
            .filter { it in known }
            .distinct()
            .toMutableList()

        val is5GOrder = known == setOf("26000", "4200", "3500", "2100", "1400", "700")
        if (is5GOrder && normalized in legacy5GFrequencyOrders) {
            return defaultOrder
        }

        defaultOrder.forEach { item ->
            if (!normalized.contains(item)) normalized.add(item)
        }

        return normalized
    }

    private val legacy5GFrequencyOrders = setOf(
        listOf("4200", "3500", "2100", "1400", "700", "26000"),
        listOf("3500", "2100", "1400", "700", "26000"),
        listOf("3500", "2100", "700")
    )

}
