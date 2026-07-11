@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.workers.DownloadNotificationCenter
import fr.geotower.data.workers.UpdateCheckScheduler
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLocale
import fr.geotower.utils.AppLogoDrawingResources
import fr.geotower.utils.AppUiMode
import fr.geotower.utils.AppIconManager
import fr.geotower.utils.HomePrefs
import fr.geotower.utils.LiveTrackingPrefs
import fr.geotower.utils.MapDisplayPrefs
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.SafeClick
import fr.geotower.ui.components.colorPaletteFadingEdge
import fr.geotower.ui.components.DialogDestructiveButton
import fr.geotower.ui.components.DialogNeutralButton
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.components.appLogoDrawingChoiceDescription
import fr.geotower.ui.components.appLogoDrawingChoiceName
import fr.geotower.ui.components.appLogoDrawingFamilyName
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.settingsPopupFadingEdge
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.services.LiveTrackingController
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.SharePrefs
import fr.geotower.utils.SitePagePrefs
import fr.geotower.utils.SupportPagePrefs
import fr.geotower.utils.ThroughputPrefs
import fr.geotower.utils.WidgetPrefs
import fr.geotower.widget.WidgetUpdateScheduler
import kotlin.math.roundToInt

private data class SettingsSectionBounds(
    val top: Float = Float.NaN,
    val height: Int = 0
) {
    val bottom: Float
        get() = top + height
    val isValid: Boolean
        get() = !top.isNaN() && height > 0
}

private fun resetSettingsToDefaultsAndRestart(context: Context, prefs: SharedPreferences) {
    val appContext = context.applicationContext

    LiveTrackingController.stop(appContext)
    AppIconManager.setIcon(appContext, 0)

    SiteSpeedtestsPagePreferences.putDefaults(
        prefs.edit()
        .clear()
        .putBoolean("isFirstRun", false)
        .putBoolean("is_blur_enabled", true)
    ).apply()
    AppConfig.isBlurEnabled.value = true
    CommunityDataPreferences.reset(prefs)

    UpdateCheckScheduler.reconcile(appContext)

    // Ne replanifie la tâche de localisation que si un widget est réellement posé.
    if (WidgetUpdateScheduler.hasAnyWidget(appContext)) {
        WidgetUpdateScheduler.schedulePeriodicUpdate(appContext, WidgetPrefs.DEFAULT_SYNC_MINUTES)
    } else {
        WidgetUpdateScheduler.cancelPeriodicUpdateIfNoWidgetsRemain(appContext)
    }

    val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
fun SettingsScreen(
    navController: NavController,
     repository: AnfrRepository,
    initialSection: String? = null,
    targetOfflineMapFilename: String? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colorPaletteScrollState = rememberScrollState()
    val sectionBringIntoViewRequesters = remember { List(5) { BringIntoViewRequester() } }
    val sectionRootPositions = remember { mutableStateMapOf<Int, Float>() }
    val sectionBounds = remember { mutableStateMapOf<Int, SettingsSectionBounds>() }
    var scrollViewportTop by remember { mutableFloatStateOf(0f) }
    var scrollViewportBottom by remember { mutableFloatStateOf(0f) }
    var offlineMapsBounds by remember { mutableStateOf(SettingsSectionBounds()) }
    var offlineMapsExpandedForNavigation by remember { mutableStateOf(false) }
    val offlineMapsTargetFilename = targetOfflineMapFilename?.takeIf { it.isNotBlank() }
    var offlineMapsTargetBounds by remember(offlineMapsTargetFilename) { mutableStateOf(SettingsSectionBounds()) }
    var hasPrimedOfflineMapsTargetScroll by remember(initialSection, offlineMapsTargetFilename) { mutableStateOf(false) }
    val databaseBringIntoViewRequester = sectionBringIntoViewRequesters[4]
    val offlineMapsBringIntoViewRequester = remember { BringIntoViewRequester() }
    var shouldBringDatabaseIntoView by remember(initialSection) { mutableStateOf(initialSection == "database") }
    var shouldBringOfflineMapsIntoView by remember(initialSection) { mutableStateOf(initialSection == "offline_maps") }

    var themeMode by AppConfig.themeMode
    var isOledMode by AppConfig.isOledMode
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config
    val uiStyle = LocalGeoTowerUiStyle.current
    var showUnitSheet by remember { mutableStateOf(false) }
    var showColorPalettePage by remember { mutableStateOf(false) }
    var settingsSearchQuery by remember { mutableStateOf("") }
    var pendingSearchScrollSection by remember { mutableStateOf<Int?>(null) }

    fun updateOneUi(enabled: Boolean) {
        val mode = AppUiMode.fromOneUiEnabled(enabled)
        AppConfig.uiMode.value = mode
        prefs.edit().putString(AppConfig.PREF_UI_MODE, mode.storageKey).apply()
    }

    val useOneUi = uiStyle.useOneUi
    val isDark = uiStyle.isDark
    val sizing = uiStyle.sizing
    val cardShape = uiStyle.cardShape
    val cardBorder = uiStyle.cardBorder
    val bubbleBaseColor = uiStyle.bubbleColor
    val mainBgColor = uiStyle.backgroundColor

    val packageInfo = remember { try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null } }
    val versionName = packageInfo?.versionName ?: "1.0.0"
    val isWideScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600

    val safeClick = rememberSafeClick()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    BackHandler(enabled = showColorPalettePage) {
        showColorPalettePage = false
    }

    BackHandler(enabled = !showColorPalettePage && !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    // Recherche active : le retour efface d'abord la recherche au lieu de quitter l'écran.
    BackHandler(enabled = settingsSearchQuery.isNotBlank() && !showColorPalettePage) {
        settingsSearchQuery = ""
    }

    var isBlurEnabled by AppConfig.isBlurEnabled
    var mapProvider by AppConfig.mapProvider
    var ignStyle by AppConfig.ignStyle
    var defaultOperator by AppConfig.defaultOperator
    val navMode = AppConfig.navMode.intValue
    var activeSectionIndex by remember { mutableIntStateOf(0) }

    // Recherche : actions de navigation déclenchées depuis un résultat.
    fun searchScrollTo(section: Int) {
        settingsSearchQuery = ""
        activeSectionIndex = section
        pendingSearchScrollSection = section
    }
    fun searchOpen(action: () -> Unit) {
        settingsSearchQuery = ""
        action()
    }

    // ✅ NOUVEAU : Auto-scroll vers la section demandée (ex: database)
    suspend fun alignAnchorToViewportTop(anchorRootY: Float?) {
        if (anchorRootY == null || anchorRootY.isNaN() || scrollState.maxValue <= 0) return
        val target = (scrollState.value + (anchorRootY - scrollViewportTop).roundToInt())
            .coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    fun isDisplayedAsMuchAsPossible(bounds: SettingsSectionBounds): Boolean {
        if (bounds.top.isNaN() || bounds.height <= 0 || scrollViewportBottom <= scrollViewportTop) return false

        val viewportHeight = scrollViewportBottom - scrollViewportTop
        val visibleTop = maxOf(bounds.top, scrollViewportTop)
        val visibleBottom = minOf(bounds.bottom, scrollViewportBottom)
        val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)
        val maxVisibleHeight = minOf(bounds.height.toFloat(), viewportHeight)

        return visibleHeight >= maxVisibleHeight - 2f
    }

    LaunchedEffect(initialSection) {
        if (initialSection == "database" || initialSection == "offline_maps") {
            activeSectionIndex = 4
            shouldBringDatabaseIntoView = initialSection == "database"
            shouldBringOfflineMapsIntoView = initialSection == "offline_maps"
        }
    }

    LaunchedEffect(shouldBringDatabaseIntoView, scrollState.maxValue, navMode, isWideScreen) {
        if (shouldBringDatabaseIntoView && (navMode == 0 || !isWideScreen) && scrollState.maxValue > 0) {
            kotlinx.coroutines.delay(120)
            databaseBringIntoViewRequester.bringIntoView()
            kotlinx.coroutines.delay(80)
            alignAnchorToViewportTop(sectionRootPositions[4])
            kotlinx.coroutines.delay(250)
            alignAnchorToViewportTop(sectionRootPositions[4])
            shouldBringDatabaseIntoView = false
        }
    }

    LaunchedEffect(
        shouldBringOfflineMapsIntoView,
        scrollState.maxValue,
        navMode,
        isWideScreen,
        offlineMapsTargetFilename,
        offlineMapsBounds.isValid,
        offlineMapsTargetBounds.isValid,
        hasPrimedOfflineMapsTargetScroll
    ) {
        if (shouldBringOfflineMapsIntoView && (navMode == 0 || !isWideScreen) && scrollState.maxValue > 0) {
            val hasTargetMap = offlineMapsTargetFilename != null
            val hasTargetBounds = offlineMapsTargetBounds.isValid
            if (hasTargetMap && !hasTargetBounds && hasPrimedOfflineMapsTargetScroll) return@LaunchedEffect

            val targetBounds = if (hasTargetMap && hasTargetBounds) {
                offlineMapsTargetBounds
            } else {
                offlineMapsBounds
            }
            if (!targetBounds.isValid) return@LaunchedEffect

            kotlinx.coroutines.delay(120)
            offlineMapsBringIntoViewRequester.bringIntoView()
            kotlinx.coroutines.delay(80)
            alignAnchorToViewportTop(targetBounds.top)
            kotlinx.coroutines.delay(250)
            alignAnchorToViewportTop(targetBounds.top)

            if (hasTargetMap && !hasTargetBounds) {
                hasPrimedOfflineMapsTargetScroll = true
            } else {
                shouldBringOfflineMapsIntoView = false
            }
        }
    }

    // Recherche : une fois la recherche fermée, on défile vers la section du paramètre choisi
    // (on attend que le contenu normal soit recomposé et mesuré).
    LaunchedEffect(pendingSearchScrollSection, scrollState.maxValue) {
        val target = pendingSearchScrollSection ?: return@LaunchedEffect
        if (navMode == 0 || !isWideScreen) {
            var tries = 0
            while (tries < 25 && (scrollState.maxValue <= 0 || sectionRootPositions[target] == null)) {
                kotlinx.coroutines.delay(40)
                tries++
            }
            sectionBringIntoViewRequesters[target].bringIntoView()
            kotlinx.coroutines.delay(80)
            alignAnchorToViewportTop(sectionRootPositions[target])
        }
        pendingSearchScrollSection = null
    }

    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", AppLocale.LANGUAGE_FRENCH) ?: AppLocale.LANGUAGE_FRENCH) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showOperatorSheet by remember { mutableStateOf(false) }
    var showIconSheet by remember { mutableStateOf(false) }
    var showLogoDrawingSheet by remember { mutableStateOf(false) }


    // --- VARIABLES POUR LE PARTAGE ---
    var showShareSelectorSheet by remember { mutableStateOf(false) } // LE NOUVEAU SOUS-MENU
    var showSharePrefsSheet by remember { mutableStateOf(false) } // Fenêtre Antenne
    var showSupportSharePrefsSheet by remember { mutableStateOf(false) } // Fenêtre Pylône
    var showMapSharePrefsSheet by remember { mutableStateOf(false) } // ✅ AJOUT : Fenêtre Carte
    var showGlobalResetDialog by remember { mutableStateOf(false) }

    // ✅ AJOUT : Variables de la Carte
    var shareMapAzimuths by remember { mutableStateOf(SharePrefs.mapAzimuths.read(prefs)) }
    var shareMapSpeedometer by remember { mutableStateOf(SharePrefs.mapSpeedometer.read(prefs)) }
    var shareMapScale by remember { mutableStateOf(SharePrefs.mapScale.read(prefs)) }
    var shareMapAttribution by remember { mutableStateOf(SharePrefs.mapAttribution.read(prefs)) }
    var shareMapQrEnabled by remember { mutableStateOf(SharePrefs.mapQrEnabled.read(prefs)) }
    var shareMapConfidential by remember { mutableStateOf(SharePrefs.mapConfidential.read(prefs)) }

    // 1. Variables de l'Antenne (Site)
    var shareMapEnabled by remember { mutableStateOf(SharePrefs.siteMapEnabled.read(prefs)) }
    var shareElevationProfileEnabled by remember { mutableStateOf(SharePrefs.siteElevationProfileEnabled.read(prefs)) }
    var shareSupportEnabled by remember { mutableStateOf(SharePrefs.siteSupportEnabled.read(prefs)) }
    var sharePhotosEnabled by remember { mutableStateOf(SharePrefs.sitePhotosEnabled.read(prefs)) }
    var shareIdsEnabled by remember { mutableStateOf(SharePrefs.siteIdsEnabled.read(prefs)) }
    var shareDatesEnabled by remember { mutableStateOf(SharePrefs.siteDatesEnabled.read(prefs)) }
    var shareAddressEnabled by remember { mutableStateOf(SharePrefs.siteAddressEnabled.read(prefs)) }
    var shareSpeedtestEnabled by remember { mutableStateOf(SharePrefs.siteSpeedtestEnabled.read(prefs)) } // 🚨 NEW
    var shareThroughputEnabled by remember { mutableStateOf(SharePrefs.siteThroughputEnabled.read(prefs)) }
    var shareFreqEnabled by remember { mutableStateOf(SharePrefs.siteFrequencyEnabled.read(prefs)) }
    var shareConfidentialEnabled by remember { mutableStateOf(SharePrefs.siteConfidentialEnabled.read(prefs)) }
    var shareSiteQrEnabled by remember { mutableStateOf(SharePrefs.siteQrEnabled.read(prefs)) }
    var shareSupQrEnabled by remember { mutableStateOf(SharePrefs.supportQrEnabled.read(prefs)) }
    var shareSplitImageEnabled by remember { mutableStateOf(SharePrefs.siteSplitImageEnabled.read(prefs)) } // ✅ NOUVELLE VARIABLE
    var shareOrder by remember {
        mutableStateOf(SharePrefs.siteOrder(prefs))
    }

    // 2. Variables du Pylône (Support) - SEULEMENT 3 BLOCS !
    var shareSupMapEnabled by remember { mutableStateOf(SharePrefs.supportMapEnabled.read(prefs)) }
    var shareSupSupportEnabled by remember { mutableStateOf(SharePrefs.supportDetailsEnabled.read(prefs)) }
    var shareSupPhotosEnabled by remember { mutableStateOf(SharePrefs.supportPhotosEnabled.read(prefs)) }
    var shareSupOperatorsEnabled by remember { mutableStateOf(SharePrefs.supportOperatorsEnabled.read(prefs)) }
    var shareSupConfidentialEnabled by remember { mutableStateOf(SharePrefs.supportConfidentialEnabled.read(prefs)) }
    var shareSupOrder by remember { mutableStateOf(SharePrefs.supportOrder(prefs)) }

    // --- VARIABLES POUR LA VISIBILITÉ DES PAGES ---
    var showNearbyPage by AppConfig.showNearbyPage
    var showMapPage by AppConfig.showMapPage
    var showCompassPage by AppConfig.showCompassPage
    var showStatsPage by AppConfig.showStatsPage

    var showMapScale by remember { mutableStateOf(prefs.getBoolean("show_map_scale", true)) }
    var showMapAttribution by remember { mutableStateOf(prefs.getBoolean("show_map_attribution", true)) }
    var showMapSpeedometer by remember { mutableStateOf(MapDisplayPrefs.showSpeedometer.read(prefs)) }
    var measureReconnectOnDelete by remember { mutableStateOf(MapDisplayPrefs.measureReconnectOnDelete.read(prefs)) }
    var showMapSettingsSheet by remember { mutableStateOf(false) }
    var showMapLocation by remember { mutableStateOf(prefs.getBoolean("show_map_location", true)) }
    var showMapLocationMarker by AppConfig.showMapLocationMarker
    var showMapAzimuths by AppConfig.showAzimuths
    var showMapAzimuthsCone by AppConfig.showAzimuthsCone
    var showMapZoom by remember { mutableStateOf(prefs.getBoolean("show_map_zoom", true)) }
    var showMapToolbox by remember { mutableStateOf(prefs.getBoolean("show_map_toolbox", true)) }
    var showMapCompass by remember { mutableStateOf(prefs.getBoolean("show_map_compass", true)) }

    var showStatsSettingsSheet by remember { mutableStateOf(false) }
    var showCompassSettingsSheet by remember { mutableStateOf(false) }
    var compassOrder by remember { mutableStateOf(prefs.getString("compass_order", "location,gps,accuracy")!!.split(",")) }
    var showCompassLocation by remember { mutableStateOf(prefs.getBoolean("show_compass_location", true)) }
    var showCompassGps by remember { mutableStateOf(prefs.getBoolean("show_compass_gps", true)) }
    var showCompassAccuracy by remember { mutableStateOf(prefs.getBoolean("show_compass_accuracy", true)) }

    // --- Variables d'état pour le Pylône et l'Antenne ---
    var showSupportSettingsSheet by remember { mutableStateOf(false) }
    var showSiteSettingsSheet by remember { mutableStateOf(false) }
    var showSupportMiniMapSettingsSheet by remember { mutableStateOf(false) }
    var showSiteMiniMapSettingsSheet by remember { mutableStateOf(false) }
    var showPhotosSettingsSheet by remember { mutableStateOf(false) }

    var pageSupportOrder by remember { mutableStateOf(SupportPagePrefs.order(prefs)) }
    var pageSupportMap by remember { mutableStateOf(SupportPagePrefs.map.read(prefs)) }
    var pageSupportDetails by remember { mutableStateOf(SupportPagePrefs.details.read(prefs)) }
    var pageSupportPhotos by remember { mutableStateOf(SupportPagePrefs.photos.read(prefs)) }
    var pageSupportOpenMap by remember { mutableStateOf(SupportPagePrefs.openMap.read(prefs)) }
    var pageSupportNav by remember { mutableStateOf(SupportPagePrefs.nav.read(prefs)) }
    var pageSupportShare by remember { mutableStateOf(SupportPagePrefs.share.read(prefs)) }
    var pageSupportOperators by remember { mutableStateOf(SupportPagePrefs.operators.read(prefs)) }
    var pageSupportMiniMapMode by remember { mutableStateOf(MiniMapViewMode.fromStorageKey(prefs.getString(SupportPagePrefs.MINI_MAP_MODE, null))) }

    // --- Variables d'état pour l'Antenne (Site) ---
    var pageSiteOrder by remember {
        mutableStateOf(SitePagePrefs.order(prefs))
    }
    var pageSiteOperator by remember { mutableStateOf(SitePagePrefs.operator.read(prefs)) }
    var pageSiteBearingHeight by remember { mutableStateOf(SitePagePrefs.bearingHeight.read(prefs)) }
    var pageSiteMap by remember { mutableStateOf(SitePagePrefs.map.read(prefs)) }
    var pageSiteSupportDetails by remember { mutableStateOf(SitePagePrefs.supportDetails.read(prefs)) }
    var pageSitePanelHeights by remember { mutableStateOf(SitePagePrefs.panelHeights.read(prefs)) }
    var pageSiteIds by remember { mutableStateOf(SitePagePrefs.ids.read(prefs)) }
    var pageSiteOpenMap by remember { mutableStateOf(SitePagePrefs.openMap.read(prefs)) }
    var pageSiteElevationProfile by remember { mutableStateOf(SitePagePrefs.elevationProfile.read(prefs)) }
    var pageSiteThroughputCalculator by remember { mutableStateOf(SitePagePrefs.throughputCalculator.read(prefs)) }
    var pageSiteNav by remember { mutableStateOf(SitePagePrefs.nav.read(prefs)) }
    var pageSiteShare by remember { mutableStateOf(SitePagePrefs.share.read(prefs)) }
    var pageSiteDates by remember { mutableStateOf(SitePagePrefs.dates.read(prefs)) }
    var pageSiteAddress by remember { mutableStateOf(SitePagePrefs.address.read(prefs)) }
    var pageSiteFreqs by remember { mutableStateOf(SitePagePrefs.freqs.read(prefs)) }
    var pageSiteLinks by remember { mutableStateOf(SitePagePrefs.links.read(prefs)) }
    var pageSiteMiniMapMode by remember { mutableStateOf(MiniMapViewMode.fromStorageKey(prefs.getString(SitePagePrefs.MINI_MAP_MODE, null))) }
    var pageThroughputOrder by remember {
        mutableStateOf(ThroughputPrefs.blockOrder(prefs))
    }
    var pageThroughputHeader by remember { mutableStateOf(prefs.getBoolean(ThroughputPrefs.BLOCK_HEADER_VISIBLE, true)) }
    var pageThroughputSummary by remember { mutableStateOf(prefs.getBoolean(ThroughputPrefs.BLOCK_SUMMARY_VISIBLE, true)) }
    var pageThroughputCone by remember { mutableStateOf(prefs.getBoolean(ThroughputPrefs.BLOCK_CONE_VISIBLE, true)) }
    var pageThroughputControls by remember { mutableStateOf(prefs.getBoolean(ThroughputPrefs.BLOCK_CONTROLS_VISIBLE, true)) }
    var pageThroughputBands by remember { mutableStateOf(prefs.getBoolean(ThroughputPrefs.BLOCK_BANDS_VISIBLE, true)) }
    var pageThroughputAssumptions by remember { mutableStateOf(prefs.getBoolean(ThroughputPrefs.BLOCK_ASSUMPTIONS_VISIBLE, true)) }

    var showPagesCustomizationSheet by remember { mutableStateOf(false) }
    var showCoverageDefaultsSheet by remember { mutableStateOf(false) }
    var showElevationDefaultsSheet by remember { mutableStateOf(false) }
    var showPreferenceProfilesSheet by remember { mutableStateOf(false) }
    var showFrequenciesSheet by remember { mutableStateOf(false) }
    var showCommunityDataSheet by remember { mutableStateOf(false) }
    var communityDataSettingsFeatureId by remember { mutableStateOf<String?>(null) }
    var communityDataReturnTarget by remember { mutableStateOf<String?>(null) }
    var photosSettingsReturnTarget by remember { mutableStateOf("site") }
    var showExternalLinksSheet by remember { mutableStateOf(false) }
    var showStartupPageSheet by remember { mutableStateOf(false) }
    var showThroughputCalculatorSettingsSheet by remember { mutableStateOf(false) }
    var showThroughputCalculationDefaultsSheet by remember { mutableStateOf(false) }
    var showSpeedtestsSettingsSheet by remember { mutableStateOf(false) }
    // On préparera les autres (showHomeSettingsSheet, etc.) dans la prochaine étape

    // La sauvegarde de la page de démarrage
    var startupPage by remember { mutableStateOf(HomePrefs.startupPage(prefs)) }
    var showHomeSettingsSheet by remember { mutableStateOf(false) }
    var pagesOrder by remember {
        mutableStateOf(
            (prefs.getString(HomePrefs.PAGES_ORDER, HomePrefs.DEFAULT_PAGES_ORDER) ?: HomePrefs.DEFAULT_PAGES_ORDER)
                .let { if (!it.contains("settings")) "$it,settings" else it }
                .split(",")
        )
    }
    var showNearbySettingsSheet by remember { mutableStateOf(false) }
    var pageSpeedtestsFilterMajorEnb by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.FILTER_MAJOR_ENB, SiteSpeedtestsPagePreferences.DEFAULT_FILTER_MAJOR_ENB)) }
    var pageSpeedtestsIncludeMissingEnb by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.INCLUDE_MISSING_ENB, SiteSpeedtestsPagePreferences.DEFAULT_INCLUDE_MISSING_ENB)) }
    var pageSpeedtestsShowCount by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_COUNT, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COUNT)) }
    var pageSpeedtestsShowRadio by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_RADIO, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_RADIO)) }
    var pageSpeedtestsShowNetwork by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_NETWORK, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_NETWORK)) }
    var pageSpeedtestsShowCoordinates by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_COORDINATES, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COORDINATES)) }
    var pageSpeedtestsBestMetric by remember {
        mutableStateOf(
            SiteSpeedtestsPagePreferences.normalizeSortMetric(
                prefs.getString(SiteSpeedtestsPagePreferences.BEST_METRIC, SiteSpeedtestsPagePreferences.DEFAULT_BEST_METRIC)
            )
        )
    }
    var pageSpeedtestsSortMetric by remember {
        mutableStateOf(
            SiteSpeedtestsPagePreferences.normalizeSortMetric(
                prefs.getString(SiteSpeedtestsPagePreferences.SORT_METRIC, SiteSpeedtestsPagePreferences.DEFAULT_SORT_METRIC)
            )
        )
    }
    var pageSpeedtestsSortDescending by remember { mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SORT_DESCENDING, SiteSpeedtestsPagePreferences.DEFAULT_SORT_DESCENDING)) }
    var nearbyOrder by remember { mutableStateOf(prefs.getString("nearby_order", "search,sites")!!.split(",")) }
    var showSearchBar by remember { mutableStateOf(prefs.getBoolean("show_search_bar", true)) }
    var showSearchSuggestions by remember { mutableStateOf(prefs.getBoolean("show_search_suggestions", true)) }
    var showNearbySites by remember { mutableStateOf(prefs.getBoolean("show_nearby_sites", true)) }
    var nearbySearchRadius by remember { mutableIntStateOf(prefs.getInt("nearby_search_radius", 5)) } // Par défaut 5 km

    LaunchedEffect(Unit) {
        AppConfig.uiScalePercent.intValue = AppConfig.readUiScalePercent(prefs)
        showNearbyPage = HomePrefs.showNearbyPage.read(prefs)
        showMapPage = HomePrefs.showMapPage.read(prefs)
        showCompassPage = HomePrefs.showCompassPage.read(prefs)
        showStatsPage = HomePrefs.showStatsPage.read(prefs)
    }

    val logoResId by AppIconManager.currentIconRes
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun updateSharedPhotosVisibility(visible: Boolean) {
        pageSupportPhotos = visible
        AppConfig.siteShowPhotos.value = visible
        prefs.edit()
            .putBoolean(SupportPagePrefs.photos.key, visible)
            .putBoolean(SitePagePrefs.photos.key, visible)
            .apply()
    }

    fun resetSpeedtestsSettings() {
        SiteSpeedtestsPagePreferences.reset(prefs)
        pageSpeedtestsFilterMajorEnb = SiteSpeedtestsPagePreferences.DEFAULT_FILTER_MAJOR_ENB
        pageSpeedtestsIncludeMissingEnb = SiteSpeedtestsPagePreferences.DEFAULT_INCLUDE_MISSING_ENB
        pageSpeedtestsShowCount = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COUNT
        pageSpeedtestsShowRadio = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_RADIO
        pageSpeedtestsShowNetwork = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_NETWORK
        pageSpeedtestsShowCoordinates = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COORDINATES
        pageSpeedtestsBestMetric = SiteSpeedtestsPagePreferences.DEFAULT_BEST_METRIC
        pageSpeedtestsSortMetric = SiteSpeedtestsPagePreferences.DEFAULT_SORT_METRIC
        pageSpeedtestsSortDescending = SiteSpeedtestsPagePreferences.DEFAULT_SORT_DESCENDING
    }

    LaunchedEffect(initialSection) {
        when (initialSection) {
            "nearby", "map", "compass", "support", "site", "throughput" -> {
                kotlinx.coroutines.delay(300)
                activeSectionIndex = 2
                if (navMode == 0 || !isWideScreen) {
                    sectionBringIntoViewRequesters[2].bringIntoView()
                    kotlinx.coroutines.delay(80)
                    alignAnchorToViewportTop(sectionRootPositions[2])
                }
                when (initialSection) {
                    "nearby" -> showNearbySettingsSheet = true
                    "map" -> showMapSettingsSheet = true
                    "compass" -> showCompassSettingsSheet = true
                    "support" -> showSupportSettingsSheet = true
                    "site" -> showSiteSettingsSheet = true
                    "throughput" -> showThroughputCalculatorSettingsSheet = true
                }
            }
        }
    }

    val menuItems = listOf(
        Triple(stringResource(R.string.settings_section_appearance), Icons.Outlined.Palette, 0),
        Triple(stringResource(R.string.settings_section_mapping), Icons.Outlined.Map, 1),
        Triple(stringResource(R.string.settings_section_preferences), Icons.Outlined.Tune, 2),
        Triple(stringResource(R.string.settings_section_system), Icons.Outlined.Settings, 3),
        Triple(stringResource(R.string.settings_section_database), Icons.Outlined.Storage, 4)
    )
    val sectionRootSnapshot = sectionRootPositions.toMap()
    val sectionBoundsSnapshot = sectionBounds.toMap()
    val databaseBounds = sectionBoundsSnapshot[4] ?: SettingsSectionBounds()
    val sectionAnchorModifiers = sectionBringIntoViewRequesters.mapIndexed { index, requester ->
        Modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                sectionRootPositions[index] = top
                sectionBounds[index] = SettingsSectionBounds(top = top, height = coordinates.size.height)
            }
    }

    // Recherche : index de tous les paramètres trouvables depuis la barre de recherche.
    val settingsSearchEntries = remember(featureFlags, appLanguage, isWideScreen) {
        buildList {
            fun entry(title: String, keywords: String, section: Int, openAction: (() -> Unit)? = null) {
                val meta = menuItems[section]
                add(
                    SettingsSearchEntry(
                        title = title,
                        keywords = keywords,
                        sectionLabel = meta.first,
                        icon = meta.second,
                        onClick = { if (openAction != null) searchOpen(openAction) else searchScrollTo(section) }
                    )
                )
            }

            // --- Apparence (0) ---
            entry(context.getString(R.string.appearance_theme_title), "theme thème clair sombre systeme dark light mode nuit jour couleur apparence", 0)
            entry(context.getString(R.string.appstrings_color_palette_title), "palette couleur color accent teinte material", 0) { showColorPalettePage = true }
            entry(context.getString(R.string.appearance_oled_title), "oled noir pur black amoled sombre economie", 0)
            entry(context.getString(R.string.appearance_one_ui_title), "one ui oneui samsung style interface bulle", 0)
            entry(context.getString(R.string.appearance_scroll_blur_title), "flou blur defilement transparence effet", 0)
            entry(context.getString(R.string.appearance_app_icon_title), "icone icon launcher logo application accueil", 0) { showIconSheet = true }
            entry(context.getString(R.string.appearance_in_app_logo_title), "logo dessin drawing application interne", 0) { showLogoDrawingSheet = true }
            entry(context.getString(R.string.appearance_menu_size_title), "taille menu size police texte echelle zoom", 0)

            // --- Cartographie (1) ---
            entry(context.getString(R.string.settings_section_mapping), "carte map fond fournisseur ign osm maplibre topo provider tuiles", 1)
            entry(context.getString(R.string.mapping_style_title), "style carte clair sombre satellite couleur", 1)

            // --- Préférences (2) ---
            entry(context.getString(R.string.preference_profiles_title), "profil profils profiles preferences sauvegarde configuration", 2) { showPreferenceProfilesSheet = true }
            entry(context.getString(R.string.settings_default_operator), "operateur operator orange sfr free bouygues sim defaut", 2) { showOperatorSheet = true }
            entry(context.getString(R.string.settings_app_language), "langue language francais anglais traduction locale", 2) { showLanguageSheet = true }
            entry(context.getString(R.string.settings_units_title), "unites units distance vitesse metre km mesure imperial", 2) { showUnitSheet = true }
            entry(context.getString(R.string.appstrings_update_notif_setting_title), "notification mise a jour update base donnees alerte", 2)
            entry(context.getString(R.string.appstrings_low_power_title), "faible consommation economie batterie eco energie basse performance low power mode", 2)
            entry(context.getString(R.string.appstrings_live_notification_title), "notification live suivi temps reel antenne direct", 2)
            entry(context.getString(R.string.appstrings_widget_refresh_title), "widget frequence rafraichissement synchronisation accueil", 2)
            if (isWideScreen) {
                entry(context.getString(R.string.settings_navigation_mode_title), "navigation mode defilement pages scroll", 2)
                entry(context.getString(R.string.settings_display_style_title), "affichage display plein ecran split divise tablette", 2)
            }
            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.PAGES_CUSTOMIZATION)) {
                entry(context.getString(R.string.settings_pages_customization_title), "pages personnalisation accueil carte boussole site support proximite statistiques", 2) { showPagesCustomizationSheet = true }
            }
            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.EXTERNAL_LINKS_SETTINGS)) {
                entry(context.getString(R.string.settings_external_links_title), "liens externes links cartoradio sites web", 2) { showExternalLinksSheet = true }
            }
            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
                entry(context.getString(R.string.settings_default_share_content_title), "partage share image contenu carte antenne support", 2) { showShareSelectorSheet = true }
            }

            // --- Système (3) ---
            entry(context.getString(R.string.appstrings_manage_permissions), "permissions autorisations systeme application acces", 3)
            entry(context.getString(R.string.appstrings_diagnostic_title), "diagnostic logs debogage info journal probleme", 3) { navController.navigate("diagnostic") }

            // --- Base de données (4) ---
            entry(context.getString(R.string.settings_section_database), "base de donnees database telechargement anfr support antenne", 4)
            entry(context.getString(R.string.appstrings_radio_data_title), "radio donnees frequences base anfr", 4)
            entry(context.getString(R.string.appstrings_offline_maps_title), "cartes hors ligne offline maps telechargement tuiles", 4)
        }
    }

    if (isWideScreen && navMode == 0) {
        LaunchedEffect(
            scrollState.value,
            scrollState.maxValue,
            sectionRootSnapshot,
            offlineMapsExpandedForNavigation,
            scrollViewportTop
        ) {
            if (sectionRootSnapshot.size >= menuItems.size) {
                val activationLine = scrollViewportTop + 24f
                val databaseSectionIndex = menuItems.last().third
                val regularSectionIndices = menuItems.dropLast(1).map { it.third }.toSet()
                val allowDatabaseSelectionBeforeEnd =
                    initialSection == "database" ||
                        initialSection == "offline_maps" ||
                        offlineMapsTargetFilename != null ||
                        offlineMapsExpandedForNavigation
                val selectableSectionRoots = sectionRootSnapshot.filterKeys {
                    it in regularSectionIndices || (allowDatabaseSelectionBeforeEnd && it == databaseSectionIndex)
                }
                val isAtScrollEnd = scrollState.maxValue > 0 && !scrollState.canScrollForward
                val nextSection = if (isAtScrollEnd) {
                    databaseSectionIndex
                } else {
                    selectableSectionRoots.entries
                        .filter { it.value <= activationLine }
                        .maxByOrNull { it.value }
                        ?.key
                        ?: selectableSectionRoots.minByOrNull { it.value }?.key
                }
                if (nextSection != null) activeSectionIndex = nextSection
            }
        }
    }

    LaunchedEffect(
        databaseBounds,
        scrollViewportTop,
        scrollViewportBottom,
        activeSectionIndex,
        navMode,
        isWideScreen
    ) {
        val isDatabasePageOpen = isWideScreen && navMode != 0 && activeSectionIndex == 4
        if (isDatabasePageOpen || isDisplayedAsMuchAsPossible(databaseBounds)) {
            DownloadNotificationCenter.clearDatabaseSectionNotifications(context)
        }
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            if (!isWideScreen) {
                // On remet la vraie barre supérieure pour les téléphones !
                if (showColorPalettePage) {
                    fr.geotower.ui.components.ColorPaletteTopBar(onBack = { showColorPalettePage = false })
                } else {
                    SettingsTopBar(onBack = { safeBackNavigation.navigateBack() })
                }
            }
        }
    ) { innerPadding ->
        // 🚀 NOUVEL AFFICHAGE QUI UTILISE LE COMPOSANT COMMUN
        if (showColorPalettePage) {
            Column(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize()
                    .background(mainBgColor)
            ) {
                if (isWideScreen) {
                    fr.geotower.ui.components.ColorPaletteTopBar(onBack = { showColorPalettePage = false })
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .colorPaletteFadingEdge(colorPaletteScrollState)
                        .verticalScroll(colorPaletteScrollState)
                        .padding(horizontal = if (isWideScreen) sizing.spacing(48.dp) else sizing.spacing(24.dp))
                        .navigationBarsPadding()
                ) {
                    fr.geotower.ui.components.ColorPalettePickerContent(
                        modifier = Modifier.padding(top = sizing.spacing(16.dp), bottom = sizing.spacing(48.dp)),
                        useOneUi = useOneUi,
                        bubbleColor = bubbleBaseColor
                    )
                }
            }
        } else {
        fr.geotower.ui.components.ResponsiveDualPaneLayout(
            // 🚨 CORRECTION 1 : On utilise uniquement le padding du haut
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            // ✅ AJOUT : onCloseSidebar
            sidebar = { width, onCloseSidebar ->
                Row(modifier = Modifier.width(width).fillMaxHeight().background(mainBgColor)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // 🚨 CORRECTION 2 : Marge pour les boutons de navigation
                            .navigationBarsPadding()
                            .padding(top = sizing.spacing(16.dp), bottom = sizing.spacing(16.dp))
                    ) {
                        // ✅ RETOUR DU ROW AVEC LES DEUX BOUTONS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { safeBackNavigation.navigateBack() },
                                enabled = !safeBackNavigation.isLocked,
                                modifier = Modifier.padding(start = sizing.spacing(8.dp))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(
                                onClick = onCloseSidebar,
                                modifier = Modifier.padding(end = sizing.spacing(8.dp))
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(sizing.spacing(16.dp)))
                        menuItems.forEach { (title, icon, index) ->
                            NavigationMenuItem(title, icon, activeSectionIndex == index, isDark) {
                                activeSectionIndex = index
                                if (navMode == 0) {
                                    scope.launch {
                                        sectionBringIntoViewRequesters[index].bringIntoView()
                                        kotlinx.coroutines.delay(80)
                                        alignAnchorToViewportTop(sectionRootPositions[index])
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        NavigationMenuItem(stringResource(R.string.nav_about), Icons.Outlined.Info, false, isDark) {
                            safeClick {
                                val currentDestinationId = navController.currentDestination?.id
                                navController.navigate("about") {
                                    launchSingleTop = true
                                    if (currentDestinationId != null) {
                                        popUpTo(currentDestinationId) {
                                            inclusive = true
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        NavigationMenuItem(title = stringResource(R.string.settings_reset), icon = Icons.Default.Refresh, isSelected = false, isDark = isDark) {
                            safeClick { showGlobalResetDialog = true }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${stringResource(R.string.common_version)} $versionName", style = sizing.textStyle(MaterialTheme.typography.labelSmall), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp)), textAlign = TextAlign.Center)
                    }
                    VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            },
            content = { isExpanded, isSidebarVisible, onToggleSidebar ->
                Column(modifier = Modifier.fillMaxSize().background(mainBgColor)) {

                    // --- EN-TÊTE TABLETTE (Apparaît quand le menu latéral est replié) ---
                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(16.dp), bottom = sizing.spacing(16.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = !isSidebarVisible, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                                IconButton(onClick = onToggleSidebar, modifier = Modifier.padding(start = sizing.spacing(8.dp))) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Text(stringResource(R.string.nav_settings), style = sizing.textStyle(MaterialTheme.typography.headlineMedium), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            AnimatedVisibility(visible = !isSidebarVisible) { Spacer(Modifier.width(sizing.component(56.dp))) }
                        }
                    }

                    // --- CONTENU DÉFILANT DES PARAMÈTRES ---
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                scrollViewportTop = coordinates.positionInRoot().y
                                scrollViewportBottom = scrollViewportTop + coordinates.size.height
                            }
                            .then(if (navMode == 0 || !isExpanded) Modifier.geoTowerFadingEdge(scrollState) else Modifier)
                            .then(if (navMode == 0 || !isExpanded) Modifier.verticalScroll(scrollState) else Modifier)
                            .padding(horizontal = if (isExpanded) sizing.spacing(48.dp) else sizing.spacing(24.dp))
                            // 🚨 CORRECTION 3 : Marge pour pouvoir scroller jusqu'au bout
                            .navigationBarsPadding()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            SettingsSearchBar(
                                query = settingsSearchQuery,
                                onQueryChange = { settingsSearchQuery = it },
                                shape = cardShape,
                                border = cardBorder,
                                bubbleColor = bubbleBaseColor,
                                useOneUi = useOneUi
                            )
                            Spacer(Modifier.height(sizing.spacing(20.dp)))
                            if (settingsSearchQuery.isNotBlank()) {
                                SettingsSearchResults(
                                    query = settingsSearchQuery,
                                    entries = settingsSearchEntries,
                                    shape = cardShape,
                                    border = cardBorder,
                                    bubbleColor = bubbleBaseColor,
                                    useOneUi = useOneUi
                                )
                            } else {
                            if (navMode == 0 || !isExpanded) {
                                AllSettingsContent(isExpanded, navMode, { AppConfig.navMode.intValue = it; prefs.edit().putInt("nav_mode", it).apply(); if (it == 1) activeSectionIndex = 2 }, themeMode, { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() }, isOledMode, { isOledMode = it; prefs.edit().putBoolean("is_oled_mode", it).apply() }, useOneUi, ::updateOneUi, isBlurEnabled, { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() }, logoResId, { showIconSheet = true }, { showLogoDrawingSheet = true }, defaultOperator, { showOperatorSheet = true }, appLanguage, { showLanguageSheet = true }, { showUnitSheet = true }, { showPagesCustomizationSheet = true }, { showCommunityDataSheet = true }, { showExternalLinksSheet = true }, { showShareSelectorSheet = true }, { showPreferenceProfilesSheet = true }, mapProvider, { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() }, ignStyle, { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() }, context, cardShape, cardBorder, bubbleBaseColor, useOneUi, safeClick, { showColorPalettePage = true }, repository, scope, sectionAnchorModifiers[0], sectionAnchorModifiers[1], sectionAnchorModifiers[2], sectionAnchorModifiers[3], sectionAnchorModifiers[4], Modifier.bringIntoViewRequester(offlineMapsBringIntoViewRequester).onGloballyPositioned { coordinates -> val top = coordinates.positionInRoot().y; offlineMapsBounds = SettingsSectionBounds(top = top, height = coordinates.size.height) }, scrollViewportTop, scrollViewportBottom, scrollState.value, scrollState.maxValue, targetMapFilename = offlineMapsTargetFilename, onTargetMapPositioned = { top, height -> offlineMapsTargetBounds = SettingsSectionBounds(top = top, height = height) }, onOfflineMapsExpandedChange = { offlineMapsExpandedForNavigation = it }, onOpenDiagnostic = { navController.navigate("diagnostic") }, onPhotosFavorites = { navController.navigate("photos_favorites") })
                            } else {
                                when (activeSectionIndex) {
                                    0 -> SectionApparence(themeMode, { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() }, isOledMode, { isOledMode = it; prefs.edit().putBoolean("is_oled_mode", it).apply() }, useOneUi, ::updateOneUi, isBlurEnabled, { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() }, logoResId, { showIconSheet = true }, { showLogoDrawingSheet = true }, cardShape, cardBorder, bubbleBaseColor, useOneUi, safeClick, { showColorPalettePage = true })
                                    1 -> SectionCartographie(mapProvider, { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() }, ignStyle, { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() }, cardShape, cardBorder, bubbleBaseColor, useOneUi, safeClick)
                                    2 -> SectionPreferences(isExpanded, navMode, { AppConfig.navMode.intValue = it; prefs.edit().putInt("nav_mode", it).apply(); if (it == 1) activeSectionIndex = 2 }, defaultOperator, { showOperatorSheet = true }, appLanguage, { showLanguageSheet = true }, { showUnitSheet = true }, { showPagesCustomizationSheet = true }, { showCommunityDataSheet = true }, { showExternalLinksSheet = true }, { showShareSelectorSheet = true }, { showPreferenceProfilesSheet = true }, cardShape, cardBorder, bubbleBaseColor, useOneUi, safeClick, onPhotosFavorites = { navController.navigate("photos_favorites") })
                                    3 -> SectionSysteme(context, cardShape, border = cardBorder, bubbleColor = bubbleBaseColor, useOneUi = useOneUi, safeClick = safeClick, onOpenDiagnostic = { navController.navigate("diagnostic") })
                                    4 -> SectionDatabase(
                                        isExpanded,
                                        cardShape,
                                        bubbleBaseColor,
                                        useOneUi,
                                        repository,
                                        scope,
                                        context,
                                        viewportTop = scrollViewportTop,
                                        viewportBottom = scrollViewportBottom,
                                        scrollValue = scrollState.value,
                                        scrollMaxValue = scrollState.maxValue,
                                        targetMapFilename = offlineMapsTargetFilename,
                                        onTargetMapPositioned = { top, height ->
                                            offlineMapsTargetBounds = SettingsSectionBounds(top = top, height = height)
                                        },
                                        onOfflineMapsExpandedChange = { offlineMapsExpandedForNavigation = it }
                                    )
                                }
                            }
                            }
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                }
            }
        )
        }

        if (showIconSheet) {
            IconSheet(
                onDismiss = { showIconSheet = false },
                currentIconRes = logoResId,
                onToggle = { choix -> AppIconManager.setIcon(context, choix) },
                context = context,
                sheetState = sheetState,
                useOneUi = useOneUi,
                safeClick = safeClick
            )
        };
        if (showLogoDrawingSheet) {
            LogoDrawingSheet(
                onDismiss = { showLogoDrawingSheet = false },
                currentChoice = AppConfig.appLogoDrawingChoice.value,
                activeIconRes = logoResId,
                isDark = isDark,
                onSelect = { choice ->
                    val normalized = AppLogoDrawingResources.normalize(choice)
                    AppConfig.appLogoDrawingChoice.value = normalized
                    prefs.edit().putString(AppLogoDrawingResources.PREF_KEY, normalized).apply()
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor,
                safeClick = safeClick
            )
        }
        if (showOperatorSheet) {
            fr.geotower.ui.components.OperatorSheet(
                defaultOperator,
                { selectedOperator ->
                    defaultOperator = selectedOperator
                    prefs.edit().putString("default_operator", selectedOperator).apply()
                    // La notif live ne dépend plus d'un opérateur : on la relance pour qu'elle
                    // suive soit l'opérateur choisi, soit l'antenne la plus proche si « Aucun ».
                    if (AppConfig.enableLiveNotifications.value) {
                        LiveTrackingController.startIfEligible(context)
                    }
                },
                { showOperatorSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showLanguageSheet) {
            fr.geotower.ui.components.LanguageSheet(
                current = appLanguage,
                onSelect = { nouvelleLangue ->
                    appLanguage = nouvelleLangue
                    AppConfig.appLanguage.value = nouvelleLangue
                    AppLocale.applyApplicationLocale(context, nouvelleLangue)
                    prefs.edit().putString("app_language", nouvelleLangue).apply()
                },
                onDismiss = { showLanguageSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- NOUVEAU MENU DE PERSONNALISATION DES PAGES ---
        if (showPagesCustomizationSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.PAGES_CUSTOMIZATION)) {
            PagesCustomizationSheet(
                onDismiss = { showPagesCustomizationSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                onStartupPageClick = { safeClick { showPagesCustomizationSheet = false; showStartupPageSheet = true } },
                onHomeClick = { safeClick { showPagesCustomizationSheet = false; showHomeSettingsSheet = true } },
                onNearbyClick = { safeClick { showPagesCustomizationSheet = false; showNearbySettingsSheet = true } },
                onMapClick = { safeClick { showPagesCustomizationSheet = false; showMapSettingsSheet = true } },
                onCompassClick = { safeClick { showPagesCustomizationSheet = false; showCompassSettingsSheet = true } },
                onStatsClick = { safeClick { showPagesCustomizationSheet = false; showStatsSettingsSheet = true } },
                onSupportClick = { safeClick { showPagesCustomizationSheet = false; showSupportSettingsSheet = true } },
                onSiteClick = { safeClick { showPagesCustomizationSheet = false; showSiteSettingsSheet = true } },
                onSpeedtestsClick = { safeClick { showPagesCustomizationSheet = false; showSpeedtestsSettingsSheet = true } },
                onThroughputCalculatorClick = { safeClick { showPagesCustomizationSheet = false; showThroughputCalculatorSettingsSheet = true } },
                onOpenFrequencies = {
                    // ✅ L'échange se fait ici : on ferme l'un et on ouvre l'autre
                    showPagesCustomizationSheet = false
                    showFrequenciesSheet = true
                },
                onTheoreticalCoverageClick = { safeClick { showPagesCustomizationSheet = false; showCoverageDefaultsSheet = true } },
                onElevationProfileClick = { safeClick { showPagesCustomizationSheet = false; showElevationDefaultsSheet = true } }
            )
        }

        if (showCoverageDefaultsSheet) {
            CoverageSettingsSheet(
                onDismiss = { showCoverageDefaultsSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                onBack = { safeClick { showCoverageDefaultsSheet = false; showPagesCustomizationSheet = true } }
            )
        }

        if (showElevationDefaultsSheet) {
            ElevationProfileSettingsSheet(
                onDismiss = { showElevationDefaultsSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                onBack = { safeClick { showElevationDefaultsSheet = false; showPagesCustomizationSheet = true } }
            )
        }

        // ✅ AJOUT : Fenêtre des Unités
        if (showPreferenceProfilesSheet) {
            PreferenceProfilesSheet(
                onDismiss = { showPreferenceProfilesSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi
            )
        }

        if (showUnitSheet) {
            fr.geotower.ui.components.UnitSettingsSheet(
                onDismiss = { showUnitSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        // ✅ AJOUT : Fenêtre des Fréquences
        if (showFrequenciesSheet) {
            fr.geotower.ui.screens.settings.SiteFreqFiltersSheet(
                onDismiss = { showFrequenciesSheet = false },
                onBack = {
                    // ✅ 3. LOGIQUE DE RETOUR
                    showFrequenciesSheet = false
                    showSiteSettingsSheet = true
                }
            )
        }

        // ✅ AJOUT : Fenêtre des Photos & Schémas
        if (showPhotosSettingsSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.PHOTO_SETTINGS)) {
            fr.geotower.ui.screens.settings.SitePhotosSettingsSheet(
                onDismiss = { showPhotosSettingsSheet = false },
                onBack = {
                    showPhotosSettingsSheet = false
                    when (photosSettingsReturnTarget) {
                        "support" -> showSupportSettingsSheet = true
                        else -> showSiteSettingsSheet = true
                    }
                },
                photosVisible = AppConfig.siteShowPhotos.value,
                onPhotosVisibilityChange = ::updateSharedPhotosVisibility,
                onOpenCommunityDataSettings = {
                    showPhotosSettingsSheet = false
                    communityDataSettingsFeatureId = CommunityDataPreferences.FEATURE_PHOTOS
                    communityDataReturnTarget = "photos"
                    showCommunityDataSheet = true
                }
            )
        }

        if (showStartupPageSheet) {
            StartupPageSelectionSheet(
                currentStartupPage = startupPage,
                onPageSelected = { newPage ->
                    startupPage = newPage
                    prefs.edit().putString(HomePrefs.STARTUP_PAGE, newPage).apply()
                },
                onDismiss = { showStartupPageSheet = false },
                onBack = {
                    safeClick {
                        showStartupPageSheet = false; showPagesCustomizationSheet = true
                    }
                }, // <-- AJOUT ICI
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showThroughputCalculatorSettingsSheet) {
            ThroughputCalculatorSettingsSheet(
                showThroughputCalculator = pageSiteThroughputCalculator,
                onThroughputCalculatorChange = {
                    pageSiteThroughputCalculator = it
                    prefs.edit().putBoolean(SitePagePrefs.throughputCalculator.key, it).apply()
                },
                throughputOrder = pageThroughputOrder,
                onThroughputOrderChange = {
                    val normalized = ThroughputPrefs.normalizeBlockOrder(it)
                    pageThroughputOrder = normalized
                    prefs.edit().putString(ThroughputPrefs.BLOCK_ORDER, normalized.joinToString(",")).apply()
                },
                showHeader = pageThroughputHeader,
                onHeaderChange = {
                    pageThroughputHeader = it
                    prefs.edit().putBoolean(ThroughputPrefs.BLOCK_HEADER_VISIBLE, it).apply()
                },
                showSummary = pageThroughputSummary,
                onSummaryChange = {
                    pageThroughputSummary = it
                    prefs.edit().putBoolean(ThroughputPrefs.BLOCK_SUMMARY_VISIBLE, it).apply()
                },
                showCone = pageThroughputCone,
                onConeChange = {
                    pageThroughputCone = it
                    prefs.edit().putBoolean(ThroughputPrefs.BLOCK_CONE_VISIBLE, it).apply()
                },
                showControls = pageThroughputControls,
                onControlsChange = {
                    pageThroughputControls = it
                    prefs.edit().putBoolean(ThroughputPrefs.BLOCK_CONTROLS_VISIBLE, it).apply()
                },
                showBands = pageThroughputBands,
                onBandsChange = {
                    pageThroughputBands = it
                    prefs.edit().putBoolean(ThroughputPrefs.BLOCK_BANDS_VISIBLE, it).apply()
                },
                showAssumptions = pageThroughputAssumptions,
                onAssumptionsChange = {
                    pageThroughputAssumptions = it
                    prefs.edit().putBoolean(ThroughputPrefs.BLOCK_ASSUMPTIONS_VISIBLE, it).apply()
                },
                onOpenCalculationDefaults = {
                    showThroughputCalculatorSettingsSheet = false
                    showThroughputCalculationDefaultsSheet = true
                },
                onDismiss = { showThroughputCalculatorSettingsSheet = false },
                onBack = {
                    safeClick {
                        showThroughputCalculatorSettingsSheet = false
                        showPagesCustomizationSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showThroughputCalculationDefaultsSheet) {
            ThroughputCalculationDefaultsSheet(
                onDismiss = { showThroughputCalculationDefaultsSheet = false },
                onBack = {
                    safeClick {
                        showThroughputCalculationDefaultsSheet = false
                        showThroughputCalculatorSettingsSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        // --- NOUVEAU MENU DES PAGES (AJOUTÉ ICI) ---
        // --- SOUS-MENU : PAGE D'ACCUEIL ---
        if (showHomeSettingsSheet) {
            HomeSettingsSheet(
                pagesOrder = pagesOrder,
                onOrderChange = { newOrder ->
                    pagesOrder = newOrder
                    prefs.edit().putString(HomePrefs.PAGES_ORDER, newOrder.joinToString(",")).apply()
                },
                showNearby = showNearbyPage,
                onNearbyChange = {
                    showNearbyPage = it; prefs.edit().putBoolean("show_nearby_page", it).apply()
                },
                showMap = showMapPage,
                onMapChange = {
                    showMapPage = it; prefs.edit().putBoolean("show_map_page", it).apply()
                },
                showCompass = showCompassPage,
                onCompassChange = {
                    showCompassPage = it; prefs.edit().putBoolean("show_compass_page", it).apply()
                },
                showStats = showStatsPage,
                onStatsChange = {
                    showStatsPage = it; prefs.edit().putBoolean("show_stats_page", it).apply()
                },
                onDismiss = { showHomeSettingsSheet = false },
                onBack = {
                    safeClick {
                        showHomeSettingsSheet = false; showPagesCustomizationSheet = true
                    }
                }, // <-- AJOUT ICI
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU : ANTENNES À PROXIMITÉ ---
        if (showNearbySettingsSheet) {
            NearbySettingsSheet(
                nearbyOrder = nearbyOrder,
                onOrderChange = { newOrder ->
                    nearbyOrder = newOrder
                    prefs.edit().putString("nearby_order", newOrder.joinToString(",")).apply()
                },
                showSearch = showSearchBar,
                onSearchChange = {
                    showSearchBar = it; prefs.edit().putBoolean("show_search_bar", it).apply()
                },
                showSuggestions = showSearchSuggestions,
                onSuggestionsChange = {
                    showSearchSuggestions = it; prefs.edit().putBoolean("show_search_suggestions", it).apply()
                },
                showSites = showNearbySites,
                onSitesChange = {
                    showNearbySites = it; prefs.edit().putBoolean("show_nearby_sites", it).apply()
                },
                searchRadius = nearbySearchRadius,
                onRadiusChange = {
                    nearbySearchRadius = it; prefs.edit().putInt("nearby_search_radius", it).apply()
                },
                onDismiss = { showNearbySettingsSheet = false },
                onBack = {
                    safeClick {
                        showNearbySettingsSheet = false; showPagesCustomizationSheet = true
                    }
                }, // <-- AJOUT ICI
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU : CARTE DES ANTENNES ---
        if (showMapSettingsSheet) {
            MapSettingsSheet(
                showLocation = showMapLocation,
                onLocationChange = {
                    showMapLocation = it; prefs.edit().putBoolean("show_map_location", it).apply()
                },
                showLocationMarker = showMapLocationMarker,
                onLocationMarkerChange = {
                    showMapLocationMarker = it; prefs.edit().putBoolean(AppConfig.PREF_SHOW_MAP_LOCATION_MARKER, it).apply()
                },
                showAzimuths = showMapAzimuths,
                onAzimuthsChange = {
                    showMapAzimuths = it; prefs.edit().putBoolean(AppConfig.PREF_SHOW_AZIMUTH_LINES, it).apply()
                },
                showAzimuthsCone = showMapAzimuthsCone,
                onAzimuthsConeChange = {
                    showMapAzimuthsCone = it; prefs.edit().putBoolean(AppConfig.PREF_SHOW_AZIMUTH_CONES, it).apply()
                },
                showZoom = showMapZoom,
                onZoomChange = {
                    showMapZoom = it; prefs.edit().putBoolean("show_map_zoom", it).apply()
                },
                showToolbox = showMapToolbox,
                onToolboxChange = {
                    showMapToolbox = it; prefs.edit().putBoolean("show_map_toolbox", it).apply()
                },
                showCompass = showMapCompass,
                onCompassChange = {
                    showMapCompass = it; prefs.edit().putBoolean("show_map_compass", it).apply()
                },
                // --- NOUVELLES OPTIONS ---
                showScale = showMapScale,
                onScaleChange = {
                    showMapScale = it; prefs.edit().putBoolean("show_map_scale", it).apply()
                },
                showAttribution = showMapAttribution,
                onAttributionChange = {
                    showMapAttribution = it; prefs.edit().putBoolean("show_map_attribution", it)
                    .apply()
                },

                showSpeedometer = showMapSpeedometer,
                onSpeedometerChange = {
                    showMapSpeedometer = it
                    AppConfig.showSpeedometer.value = it
                    prefs.edit().putBoolean(MapDisplayPrefs.showSpeedometer.key, it).apply()
                },

                measureReconnectOnDelete = measureReconnectOnDelete,
                onMeasureReconnectChange = {
                    measureReconnectOnDelete = it
                    AppConfig.measureReconnectOnDelete.value = it
                    prefs.edit().putBoolean(MapDisplayPrefs.measureReconnectOnDelete.key, it).apply()
                },

                onDismiss = { showMapSettingsSheet = false },
                onBack = {
                    safeClick {
                        showMapSettingsSheet = false; showPagesCustomizationSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU : BOUSSOLE ---
        if (showCompassSettingsSheet) {
            CompassSettingsSheet(
                compassOrder = compassOrder,
                onOrderChange = { newOrder ->
                    compassOrder = newOrder
                    prefs.edit().putString("compass_order", newOrder.joinToString(",")).apply()
                },
                showLocation = showCompassLocation,
                onLocationChange = {
                    showCompassLocation = it; prefs.edit().putBoolean("show_compass_location", it)
                    .apply()
                },
                showGps = showCompassGps,
                onGpsChange = {
                    showCompassGps = it; prefs.edit().putBoolean("show_compass_gps", it).apply()
                },
                showAccuracy = showCompassAccuracy,
                onAccuracyChange = {
                    showCompassAccuracy = it; prefs.edit().putBoolean("show_compass_accuracy", it)
                    .apply()
                },
                onDismiss = { showCompassSettingsSheet = false },
                onBack = {
                    safeClick {
                        showCompassSettingsSheet = false; showPagesCustomizationSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- NOUVELLES FENÊTRES ---
        if (showStatsSettingsSheet) {
            StatsSettingsSheet(
                onDismiss = { showStatsSettingsSheet = false },
                onBack = { safeClick { showStatsSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showSupportSettingsSheet) {
            SupportSettingsSheet(
                supportOrder = pageSupportOrder, onOrderChange = { pageSupportOrder = it; prefs.edit().putString(SupportPagePrefs.ORDER, it.joinToString(",")).apply() },
                showMap = pageSupportMap, onMapChange = { pageSupportMap = it; prefs.edit().putBoolean(SupportPagePrefs.map.key, it).apply() },
                showDetails = pageSupportDetails, onDetailsChange = { pageSupportDetails = it; prefs.edit().putBoolean(SupportPagePrefs.details.key, it).apply() },
                showPhotos = AppConfig.siteShowPhotos.value, onPhotosChange = ::updateSharedPhotosVisibility,
                showOpenMap = pageSupportOpenMap, onOpenMapChange = { pageSupportOpenMap = it; prefs.edit().putBoolean(SupportPagePrefs.openMap.key, it).apply() },
                showNav = pageSupportNav, onNavChange = { pageSupportNav = it; prefs.edit().putBoolean(SupportPagePrefs.nav.key, it).apply() },
                showShare = pageSupportShare, onShareChange = { pageSupportShare = it; prefs.edit().putBoolean(SupportPagePrefs.share.key, it).apply() },
                showOperators = pageSupportOperators, onOperatorsChange = { pageSupportOperators = it; prefs.edit().putBoolean(SupportPagePrefs.operators.key, it).apply() },
                onOpenMiniMapSettings = {
                    showSupportSettingsSheet = false
                    showSupportMiniMapSettingsSheet = true
                },
                onOpenPhotosSettings = {
                    photosSettingsReturnTarget = "support"
                    showSupportSettingsSheet = false
                    showPhotosSettingsSheet = true
                },
                onDismiss = { showSupportSettingsSheet = false },
                onBack = { safeClick { showSupportSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showSiteSettingsSheet) {
            SiteSettingsSheet(
                siteOrder = pageSiteOrder, onOrderChange = { pageSiteOrder = it; prefs.edit().putString(SitePagePrefs.ORDER, it.joinToString(",")).apply() },
                showOperator = pageSiteOperator, onOperatorChange = { pageSiteOperator = it; prefs.edit().putBoolean(SitePagePrefs.operator.key, it).apply() },
                showBearingHeight = pageSiteBearingHeight, onBearingHeightChange = { pageSiteBearingHeight = it; prefs.edit().putBoolean(SitePagePrefs.bearingHeight.key, it).apply() },
                showMap = pageSiteMap, onMapChange = { pageSiteMap = it; prefs.edit().putBoolean(SitePagePrefs.map.key, it).apply() },
                showSupportDetails = pageSiteSupportDetails, onSupportDetailsChange = { pageSiteSupportDetails = it; prefs.edit().putBoolean(SitePagePrefs.supportDetails.key, it).apply() },
                showPhotos = AppConfig.siteShowPhotos.value, onPhotosChange = ::updateSharedPhotosVisibility,
                showPanelHeights = pageSitePanelHeights, onPanelHeightsChange = { pageSitePanelHeights = it; prefs.edit().putBoolean(SitePagePrefs.panelHeights.key, it).apply() },
                showIds = pageSiteIds, onIdsChange = { pageSiteIds = it; prefs.edit().putBoolean(SitePagePrefs.ids.key, it).apply() },
                showOpenMap = pageSiteOpenMap, onOpenMapChange = { pageSiteOpenMap = it; prefs.edit().putBoolean(SitePagePrefs.openMap.key, it).apply() },
                showElevationProfile = pageSiteElevationProfile, onElevationProfileChange = { pageSiteElevationProfile = it; prefs.edit().putBoolean(SitePagePrefs.elevationProfile.key, it).apply() },
                showThroughputCalculator = pageSiteThroughputCalculator, onThroughputCalculatorChange = { pageSiteThroughputCalculator = it; prefs.edit().putBoolean(SitePagePrefs.throughputCalculator.key, it).apply() },
                showNav = pageSiteNav, onNavChange = { pageSiteNav = it; prefs.edit().putBoolean(SitePagePrefs.nav.key, it).apply() },
                showShare = pageSiteShare, onShareChange = { pageSiteShare = it; prefs.edit().putBoolean(SitePagePrefs.share.key, it).apply() },
                showDates = pageSiteDates, onDatesChange = { pageSiteDates = it; prefs.edit().putBoolean(SitePagePrefs.dates.key, it).apply() },
                showAddress = pageSiteAddress, onAddressChange = { pageSiteAddress = it; prefs.edit().putBoolean(SitePagePrefs.address.key, it).apply() },
                showStatus = AppConfig.siteShowStatus.value, onStatusChange = { AppConfig.siteShowStatus.value = it; prefs.edit().putBoolean("site_show_status", it).apply() }, // 🚨 AJOUT DU STATUT
                showSpeedtest = AppConfig.siteShowSpeedtest.value, onSpeedtestChange = { AppConfig.siteShowSpeedtest.value = it; prefs.edit().putBoolean("site_show_speedtest", it).apply() }, // 🚨 NEW
                showFreqs = pageSiteFreqs, onFreqsChange = { pageSiteFreqs = it; prefs.edit().putBoolean(SitePagePrefs.freqs.key, it).apply() },
                showLinks = pageSiteLinks, onLinksChange = { pageSiteLinks = it; prefs.edit().putBoolean(SitePagePrefs.links.key, it).apply() },
                onOpenMiniMapSettings = {
                    showSiteSettingsSheet = false
                    showSiteMiniMapSettingsSheet = true
                },
                onOpenFrequencies = {
                    showSiteSettingsSheet = false
                    showFrequenciesSheet = true
                },
                onOpenPhotosSettings = {
                    photosSettingsReturnTarget = "site"
                    showSiteSettingsSheet = false
                    showPhotosSettingsSheet = true
                },
                onOpenSpeedtestSettings = {
                    communityDataSettingsFeatureId = CommunityDataPreferences.FEATURE_SPEEDTEST
                    communityDataReturnTarget = "site"
                    showSiteSettingsSheet = false
                    showCommunityDataSheet = true
                },
                onDismiss = { showSiteSettingsSheet = false },
                onBack = { safeClick { showSiteSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        if (showSpeedtestsSettingsSheet) {
            SiteSpeedtestsSettingsSheet(
                filterMajorEnb = pageSpeedtestsFilterMajorEnb,
                onFilterMajorEnbChange = {
                    pageSpeedtestsFilterMajorEnb = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.FILTER_MAJOR_ENB, it).apply()
                },
                includeMissingEnb = pageSpeedtestsIncludeMissingEnb,
                onIncludeMissingEnbChange = {
                    pageSpeedtestsIncludeMissingEnb = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.INCLUDE_MISSING_ENB, it).apply()
                },
                showSpeedtestsCount = pageSpeedtestsShowCount,
                onShowSpeedtestsCountChange = {
                    pageSpeedtestsShowCount = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.SHOW_COUNT, it).apply()
                },
                showRadioDetails = pageSpeedtestsShowRadio,
                onShowRadioDetailsChange = {
                    pageSpeedtestsShowRadio = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.SHOW_RADIO, it).apply()
                },
                showNetworkDetails = pageSpeedtestsShowNetwork,
                onShowNetworkDetailsChange = {
                    pageSpeedtestsShowNetwork = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.SHOW_NETWORK, it).apply()
                },
                showCoordinates = pageSpeedtestsShowCoordinates,
                onShowCoordinatesChange = {
                    pageSpeedtestsShowCoordinates = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.SHOW_COORDINATES, it).apply()
                },
                bestMetric = pageSpeedtestsBestMetric,
                onBestMetricChange = {
                    val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                    pageSpeedtestsBestMetric = normalizedMetric
                    prefs.edit().putString(SiteSpeedtestsPagePreferences.BEST_METRIC, normalizedMetric).apply()
                },
                sortMetric = pageSpeedtestsSortMetric,
                onSortMetricChange = {
                    val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                    pageSpeedtestsSortMetric = normalizedMetric
                    prefs.edit().putString(SiteSpeedtestsPagePreferences.SORT_METRIC, normalizedMetric).apply()
                },
                sortDescending = pageSpeedtestsSortDescending,
                onSortDescendingChange = {
                    pageSpeedtestsSortDescending = it
                    prefs.edit().putBoolean(SiteSpeedtestsPagePreferences.SORT_DESCENDING, it).apply()
                },
                onReset = ::resetSpeedtestsSettings,
                onDismiss = { showSpeedtestsSettingsSheet = false },
                onBack = { safeClick { showSpeedtestsSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        // --- SOUS-MENU MINI-CARTE ---
        if (showSupportMiniMapSettingsSheet) {
            MiniMapSettingsSheet(
                selectedMode = pageSupportMiniMapMode,
                onModeChange = {
                    pageSupportMiniMapMode = it
                    prefs.edit().putString(SupportPagePrefs.MINI_MAP_MODE, it.storageKey).apply()
                },
                onDismiss = { showSupportMiniMapSettingsSheet = false },
                onBack = {
                    safeClick {
                        showSupportMiniMapSettingsSheet = false
                        showSupportSettingsSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        if (showSiteMiniMapSettingsSheet) {
            MiniMapSettingsSheet(
                selectedMode = pageSiteMiniMapMode,
                onModeChange = {
                    pageSiteMiniMapMode = it
                    prefs.edit().putString(SitePagePrefs.MINI_MAP_MODE, it.storageKey).apply()
                },
                onDismiss = { showSiteMiniMapSettingsSheet = false },
                onBack = {
                    safeClick {
                        showSiteMiniMapSettingsSheet = false
                        showSiteSettingsSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }

        // --- MENU DES PRÉFÉRENCES DE PARTAGE ---
        // --- SOUS-MENU DE SÉLECTION DU PARTAGE ---
        if (showShareSelectorSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
            val sheetBgColor2 =
                if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
            val shareSelectorScrollState = rememberScrollState()
            ModalBottomSheet(
                onDismissRequest = { showShareSelectorSheet = false },
                sheetState = sheetState,
                containerColor = sheetBgColor2
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsPopupFadingEdge(shareSelectorScrollState)
                        .verticalScroll(shareSelectorScrollState)
                        .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(16.dp), end = sizing.spacing(16.dp))
                ) {
                    Text(
                        stringResource(R.string.settings_default_share_content_title),
                        style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(bottom = sizing.spacing(24.dp))
                    )

                    // ✅ AJOUT DU BOUTON CARTE
                    NavigationMenuItem(
                        title = stringResource(R.string.appstrings_share_map_details_title), // "Carte"
                        icon = Icons.Outlined.Map,
                        isSelected = false,
                        isDark = isDark
                    ) {
                        safeClick {
                            showShareSelectorSheet = false
                            showMapSharePrefsSheet = true
                        }
                    }
                    Spacer(Modifier.height(sizing.spacing(12.dp)))

                    NavigationMenuItem(
                        title = stringResource(R.string.appstrings_share_support_details_title),
                        icon = Icons.Default.VerticalAlignTop,
                        isSelected = false,
                        isDark = isDark
                    ) {
                        safeClick {
                            showShareSelectorSheet = false
                            showSupportSharePrefsSheet = true
                        }
                    }
                    Spacer(Modifier.height(sizing.spacing(12.dp)))
                    NavigationMenuItem(
                        title = stringResource(R.string.appstrings_share_site_details_title),
                        icon = Icons.Default.WifiTethering,
                        isSelected = false,
                        isDark = isDark
                    ) {
                        safeClick {
                            showShareSelectorSheet = false
                            showSharePrefsSheet = true
                        }
                    }
                }
            }
        }

        // --- MENU PRÉFÉRENCES PARTAGE PYLÔNE (SUPPORT) ---
        if (showSupportSharePrefsSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
            SupportSharePreferencesSheet(
                shareOrder = shareSupOrder,
                onOrderChange = { newOrder ->
                    shareSupOrder = newOrder; prefs.edit()
                    .putString(SharePrefs.SUPPORT_ORDER, newOrder.joinToString(",")).apply()
                },
                mapEnabled = shareSupMapEnabled,
                onMapChange = {
                    shareSupMapEnabled = it; prefs.edit().putBoolean(SharePrefs.supportMapEnabled.key, it)
                    .apply()
                },
                supportEnabled = shareSupSupportEnabled,
                onSupportChange = {
                    shareSupSupportEnabled = it; prefs.edit()
                    .putBoolean(SharePrefs.supportDetailsEnabled.key, it).apply()
                },
                photosEnabled = shareSupPhotosEnabled,
                onPhotosChange = {
                    shareSupPhotosEnabled = it; prefs.edit()
                    .putBoolean(SharePrefs.supportPhotosEnabled.key, it).apply()
                },
                operatorsEnabled = shareSupOperatorsEnabled,
                onOperatorsChange = {
                    shareSupOperatorsEnabled = it; prefs.edit()
                    .putBoolean(SharePrefs.supportOperatorsEnabled.key, it).apply()
                },
                qrEnabled = shareSupQrEnabled,
                onQrChange = { shareSupQrEnabled = it; prefs.edit().putBoolean(SharePrefs.supportQrEnabled.key, it).apply() },
                confidentialEnabled = shareSupConfidentialEnabled,
                onConfidentialChange = {
                    shareSupConfidentialEnabled = it; prefs.edit()
                    .putBoolean(SharePrefs.supportConfidentialEnabled.key, it).apply()
                },
                onDismiss = { showSupportSharePrefsSheet = false },
                onBack = {
                    safeClick {
                        showSupportSharePrefsSheet = false; showShareSelectorSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
        if (showCommunityDataSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.COMMUNITY_DATA_SETTINGS)) {
            CommunityDataSettingsSheet(
                onDismiss = {
                    showCommunityDataSheet = false
                    val returnTarget = communityDataReturnTarget
                    communityDataSettingsFeatureId = null
                    communityDataReturnTarget = null
                    when (returnTarget) {
                        "photos" -> showPhotosSettingsSheet = true
                        "site" -> showSiteSettingsSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                featureId = communityDataSettingsFeatureId
            )
        }
        if (showExternalLinksSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.EXTERNAL_LINKS_SETTINGS)) {
            ExternalLinksSettingsSheet(
                onDismiss = { showExternalLinksSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi
            )
        }
        if (showSharePrefsSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
            SharePreferencesSheet(
                shareOrder = shareOrder,
                onOrderChange = { newOrder ->
                    shareOrder = newOrder
                    prefs.edit().putString(SharePrefs.SITE_ORDER, newOrder.joinToString(",")).apply()
                },
                mapEnabled = shareMapEnabled,
                onMapChange = {
                    shareMapEnabled = it; prefs.edit().putBoolean(SharePrefs.siteMapEnabled.key, it).apply()
                },
                elevationProfileEnabled = shareElevationProfileEnabled,
                onElevationProfileChange = {
                    shareElevationProfileEnabled = it; prefs.edit().putBoolean(SharePrefs.siteElevationProfileEnabled.key, it).apply()
                },
                supportEnabled = shareSupportEnabled,
                onSupportChange = {
                    shareSupportEnabled = it; prefs.edit().putBoolean(SharePrefs.siteSupportEnabled.key, it)
                    .apply()
                },
                photosEnabled = sharePhotosEnabled,
                onPhotosChange = {
                    sharePhotosEnabled = it; prefs.edit().putBoolean(SharePrefs.sitePhotosEnabled.key, it).apply()
                },
                idsEnabled = shareIdsEnabled,
                onIdsChange = {
                    shareIdsEnabled = it; prefs.edit().putBoolean(SharePrefs.siteIdsEnabled.key, it).apply()
                },
                datesEnabled = shareDatesEnabled,
                onDatesChange = {
                    shareDatesEnabled = it; prefs.edit().putBoolean(SharePrefs.siteDatesEnabled.key, it)
                    .apply()
                },
                addressEnabled = shareAddressEnabled,
                onAddressChange = {
                    shareAddressEnabled = it; prefs.edit().putBoolean(SharePrefs.siteAddressEnabled.key, it).apply()
                },
                statusEnabled = AppConfig.shareSiteStatus.value,
                onStatusChange = {
                    AppConfig.shareSiteStatus.value = it; prefs.edit().putBoolean("share_site_status", it).apply()
                },
                speedtestEnabled = shareSpeedtestEnabled, // 🚨 NEW
                onSpeedtestChange = {
                    shareSpeedtestEnabled = it; prefs.edit().putBoolean(SharePrefs.siteSpeedtestEnabled.key, it).apply()
                },
                throughputEnabled = shareThroughputEnabled,
                onThroughputChange = {
                    shareThroughputEnabled = it; prefs.edit().putBoolean(SharePrefs.siteThroughputEnabled.key, it).apply()
                },
                freqEnabled = shareFreqEnabled,
                onFreqChange = {
                    shareFreqEnabled = it; prefs.edit().putBoolean(SharePrefs.siteFrequencyEnabled.key, it).apply()
                },
                qrEnabled = shareSiteQrEnabled,
                onQrChange = { shareSiteQrEnabled = it; prefs.edit().putBoolean(SharePrefs.siteQrEnabled.key, it).apply() },

                // ✅ AJOUT DES DEUX PARAMÈTRES MANQUANTS ICI :
                splitImageEnabled = shareSplitImageEnabled,
                onSplitImageChange = {
                    shareSplitImageEnabled = it; prefs.edit().putBoolean(SharePrefs.siteSplitImageEnabled.key, it).apply()
                },

                confidentialEnabled = shareConfidentialEnabled,
                onConfidentialChange = {
                    shareConfidentialEnabled = it; prefs.edit()
                    .putBoolean(SharePrefs.siteConfidentialEnabled.key, it).apply()
                },
                onDismiss = { showSharePrefsSheet = false },
                onBack = {
                    safeClick {
                        showSharePrefsSheet = false; showShareSelectorSheet = true
                    }
                },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        }
    }
    // ✅ AJOUT : FENÊTRE DES PRÉFÉRENCES DE PARTAGE DE LA CARTE
    if (showMapSharePrefsSheet && featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
        MapSharePreferencesSheet(
            // ✅ On change 'compass' par 'azimuths'
            azimuthsEnabled = shareMapAzimuths,
            onAzimuthsChange = {
                shareMapAzimuths = it; prefs.edit().putBoolean(SharePrefs.mapAzimuths.key, it).apply()
                AppConfig.shareMapAzimuths.value = it // Met à jour l'état global
            },
            speedometerEnabled = shareMapSpeedometer,
            onSpeedometerChange = {
                shareMapSpeedometer = it; prefs.edit().putBoolean(SharePrefs.mapSpeedometer.key, it).apply()
                AppConfig.shareMapSpeedometer.value = it
            },
            scaleEnabled = shareMapScale,
            onScaleChange = {
                shareMapScale = it; prefs.edit().putBoolean(SharePrefs.mapScale.key, it).apply()
                AppConfig.shareMapScale.value = it
            },
            attributionEnabled = shareMapAttribution,
            onAttributionChange = {
                shareMapAttribution = it; prefs.edit().putBoolean(SharePrefs.mapAttribution.key, it).apply()
                AppConfig.shareMapAttribution.value = it
            },
            qrEnabled = shareMapQrEnabled,
            onQrChange = {
                shareMapQrEnabled = it; prefs.edit().putBoolean(SharePrefs.mapQrEnabled.key, it).apply()
            },
            statusEnabled = AppConfig.shareSiteStatus.value, // 🚨 C'EST ICI QU'IL MANQUAIT LES VARIABLES !
            onStatusChange = {
                AppConfig.shareSiteStatus.value = it; prefs.edit().putBoolean("share_site_status", it).apply()
            },
            confidentialEnabled = shareMapConfidential,
            onConfidentialChange = {
                shareMapConfidential = it; prefs.edit().putBoolean(SharePrefs.mapConfidential.key, it).apply()
                AppConfig.shareMapConfidential.value = it
            },
            onDismiss = { showMapSharePrefsSheet = false },
            onBack = {
                safeClick {
                    showMapSharePrefsSheet = false; showShareSelectorSheet = true
                }
            },
            sheetState = sheetState,
            useOneUi = useOneUi,
            bubbleColor = bubbleBaseColor
        )
    }
    // --- POP-UP DE RÉINITIALISATION GLOBALE (DEPUIS LE MENU LATÉRAL) ---
    if (showGlobalResetDialog) {
        AlertDialog(
            onDismissRequest = { showGlobalResetDialog = false },
            title = { Text(text = stringResource(R.string.settings_reset_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_reset_warning_desc)) },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                DialogDestructiveButton(
                    text = stringResource(R.string.common_yes),
                    onClick = {
                        showGlobalResetDialog = false

                        resetSettingsToDefaultsAndRestart(context, prefs)
                    }
                )
            },
            confirmButton = {
                DialogNeutralButton(text = stringResource(R.string.common_no), onClick = { showGlobalResetDialog = false })
            }
        )
    }
}

// ============================================================
// SECTIONS & HELPERS UI
// ============================================================

@Composable
fun SectionTitle(title: String) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Text(
        text = title,
        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
        fontWeight = FontWeight.Bold,
        // On utilise primary tout court, Android gérera le mode clair/sombre tout seul !
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp))
    )
}

@Composable
fun AllSettingsContent(
    isWide: Boolean, nav: Int, onNav: (Int) -> Unit, theme: Int, onTheme: (Int) -> Unit, oled: Boolean, onOled: (Boolean) -> Unit, oneUi: Boolean, onOneUi: (Boolean) -> Unit, blur: Boolean, onBlur: (Boolean) -> Unit, logo: Int, onIcon: () -> Unit, onLogoDrawing: () -> Unit, op: String, onOp: () -> Unit, lang: String, onLang: () -> Unit,
    onUnitSettings: () -> Unit,
    onPages: () -> Unit,
    onCommunityData: () -> Unit,
    onExternalLinks: () -> Unit,
    onSharePrefs: () -> Unit,
    onPreferenceProfiles: () -> Unit,
    map: Int,
    onMap: (Int) -> Unit,
    ign: Int,
    onIgn: (Int) -> Unit,
    ctx: Context,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: SafeClick,
    onColorPaletteClick: () -> Unit,
    repository: AnfrRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    appearanceSectionModifier: Modifier = Modifier,
    mappingSectionModifier: Modifier = Modifier,
    preferencesSectionModifier: Modifier = Modifier,
    systemSectionModifier: Modifier = Modifier,
    databaseSectionModifier: Modifier = Modifier,
    offlineMapsSectionModifier: Modifier = Modifier,
    viewportTop: Float = Float.NaN,
    viewportBottom: Float = Float.NaN,
    scrollValue: Int = 0,
    scrollMaxValue: Int = 0,
    targetMapFilename: String? = null,
    onTargetMapPositioned: (Float, Int) -> Unit = { _, _ -> },
    onOfflineMapsExpandedChange: (Boolean) -> Unit = {},
    onOpenDiagnostic: () -> Unit = {},
    onPhotosFavorites: () -> Unit = {}
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(modifier = appearanceSectionModifier.fillMaxWidth()) {
        SectionApparence(theme, onTheme, oled, onOled, oneUi, onOneUi, blur, onBlur, logo, onIcon, onLogoDrawing, shape, border, bubbleColor, useOneUi, safeClick, onColorPaletteClick)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = mappingSectionModifier.fillMaxWidth()) {
        SectionCartographie(map, onMap, ign, onIgn, shape, border, bubbleColor, useOneUi, safeClick)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = preferencesSectionModifier.fillMaxWidth()) {
        SectionPreferences(isWide, nav, onNav, op, onOp, lang, onLang, onUnitSettings, onPages, onCommunityData, onExternalLinks, onSharePrefs, onPreferenceProfiles, shape, border, bubbleColor, useOneUi, safeClick, onPhotosFavorites = onPhotosFavorites)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = systemSectionModifier.fillMaxWidth()) {
        SectionSysteme(ctx, shape, border, bubbleColor, useOneUi, safeClick, onOpenDiagnostic)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    SectionDatabase(
        isWide,
        shape,
        bubbleColor,
        useOneUi,
        repository,
        scope,
        ctx,
        databaseSectionModifier,
        offlineMapsSectionModifier,
        viewportTop,
        viewportBottom,
        scrollValue,
        scrollMaxValue,
        targetMapFilename,
        onTargetMapPositioned,
        onOfflineMapsExpandedChange = onOfflineMapsExpandedChange
    )
}

@Composable
fun SectionApparence(
    theme: Int, onTheme: (Int) -> Unit, oled: Boolean, onOled: (Boolean) -> Unit,
    oneUi: Boolean, onOneUi: (Boolean) -> Unit, blur: Boolean, onBlur: (Boolean) -> Unit,
    logo: Int, onIcon: () -> Unit, onLogoDrawing: () -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick,
    onColorPaletteClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, android.content.Context.MODE_PRIVATE)
    val uiScalePercent by AppConfig.uiScalePercent
    val logoDrawingChoice by AppConfig.appLogoDrawingChoice
    val isDark = LocalGeoTowerUiStyle.current.isDark
    val logoDrawingRes = AppLogoDrawingResources.resolve(logoDrawingChoice, logo, isDark)

    SectionTitle(stringResource(R.string.settings_section_appearance))

    fr.geotower.ui.components.AppearanceOptionsBlock(
        themeMode = theme, onThemeChange = onTheme,
        isOled = oled, onOledChange = onOled,
        useOneUi = oneUi, onOneUiChange = onOneUi,
        isBlur = blur, onBlurChange = onBlur,
        uiScalePercent = uiScalePercent,
        onUiScalePercentChange = { newPercent ->
            AppConfig.uiScalePercent.intValue = newPercent
            prefs.edit().putInt(AppConfig.PREF_UI_SCALE_PERCENT, newPercent).apply()
        },
        appIconRes = logo,
        onAppIconClick = onIcon,
        appLogoDrawingChoice = logoDrawingChoice,
        appLogoDrawingRes = logoDrawingRes,
        onAppLogoDrawingClick = onLogoDrawing,
        onColorPaletteClick = onColorPaletteClick,
        shape = shape, border = border, bubbleColor = bubbleColor, safeClick = safeClick
    )
}

@Composable
fun SectionCartographie(map: Int, onMap: (Int) -> Unit, ign: Int, onIgn: (Int) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick) {
    SectionTitle(stringResource(R.string.settings_section_mapping))

    fr.geotower.ui.components.MappingOptionsBlock(
        mapProvider = map,
        onMapProviderChange = onMap,
        ignStyle = ign,
        onIgnStyleChange = onIgn,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionPreferences(
    isWide: Boolean, nav: Int, onNav: (Int) -> Unit,
    op: String, onOp: () -> Unit, lang: String, onLang: () -> Unit,
    onUnitSettings: () -> Unit,
    onPages: () -> Unit,
    onCommunityData: () -> Unit,
    onExternalLinks: () -> Unit,
    onSharePrefs: () -> Unit,
    onPreferenceProfiles: () -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick,
    onPhotosFavorites: () -> Unit
) {
    // NOUVEAU : On récupère le contexte et les préférences ici pour le curseur
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)

    // ✅ NOUVEAU : Le lanceur magique qui déclenche le menu Android spécifique !
    val featureFlags by RemoteFeatureFlags.config
    val sizing = LocalGeoTowerUiStyle.current.sizing

    val bgLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { /* L'interface de Jetpack Compose se mettra à jour toute seule */ }
    )

    var widgetFrequency by remember {
        mutableIntStateOf(WidgetPrefs.syncFrequencyMinutes(prefs))
    }
    var liveLocationIntervalSeconds by remember {
        mutableIntStateOf(LiveTrackingPrefs.locationUpdateIntervalSeconds(prefs))
    }
    var liveLocationPriority by remember {
        mutableIntStateOf(LiveTrackingPrefs.locationPriority(prefs))
    }

    // Présence d'au moins un widget GeoTower posé : conditionne l'activation du curseur de fréquence
    // (réévaluée à chaque retour au premier plan, l'utilisateur pouvant ajouter/retirer un widget entre-temps).
    var hasWidgetInstalled by remember {
        mutableStateOf(fr.geotower.widget.WidgetUpdateScheduler.hasAnyWidget(context))
    }
    val widgetLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(widgetLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasWidgetInstalled = fr.geotower.widget.WidgetUpdateScheduler.hasAnyWidget(context)
            }
        }
        widgetLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { widgetLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showWidgetPickerSheet by remember { mutableStateOf(false) }

    // ✅ AJOUT POUR LE STYLE D'AFFICHAGE
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var displayStyle by remember { mutableIntStateOf(prefs.getInt("display_style", 0)) }
    var showDisplayStylesSheet by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.settings_section_preferences))
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode // <-- AJOUT
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val paleBgColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow // <-- AJOUT

    PreferenceActionCard(
        title = stringResource(R.string.preference_profiles_title),
        desc = stringResource(R.string.preference_profiles_card_desc),
        onClick = onPreferenceProfiles,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick
    )
    Spacer(Modifier.height(sizing.spacing(12.dp)))

    if (isWide) {
        var showModeSheet by remember { mutableStateOf(false) }
        val cardBg = if (useOneUi) bubbleColor else Color.Transparent
        Surface(onClick = { showModeSheet = true }, modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)), shape = shape, border = border, color = cardBg) {
            Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_navigation_mode_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                    Text(if (nav == 0) stringResource(R.string.settings_navigation_mode_scroll) else stringResource(R.string.settings_navigation_mode_pages), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showModeSheet) {
            val modeScrollState = rememberScrollState()
            ModalBottomSheet(
                onDismissRequest = { showModeSheet = false },
                containerColor = sheetBgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsPopupFadingEdge(modeScrollState)
                        .verticalScroll(modeScrollState)
                        .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp))
                ) {
                    Text(stringResource(R.string.settings_navigation_style_title), style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(sizing.spacing(16.dp)))
                    NavigationModeOption(stringResource(R.string.settings_navigation_scroll_title), stringResource(R.string.settings_navigation_scroll_desc), nav == 0, useOneUi) {
                        onNav(0)
                        showModeSheet = false
                    }
                    Spacer(Modifier.height(sizing.spacing(12.dp)))
                    NavigationModeOption(stringResource(R.string.settings_navigation_pages_title), stringResource(R.string.settings_navigation_pages_desc), nav == 1, useOneUi) {
                        onNav(1)
                        showModeSheet = false
                    }
                }
            }
        }
    }

    // ✅ NOUVEAU : Option Style d'affichage (uniquement sur grand écran réel)
    if (minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600) {
        val cardBg = if (useOneUi) bubbleColor else Color.Transparent
        Surface(onClick = { safeClick("display_styles_sheet") { showDisplayStylesSheet = true } }, modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)), shape = shape, border = border, color = cardBg) {
            Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_display_style_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                    Text(if (displayStyle == 0) stringResource(R.string.settings_display_fullscreen_title) else stringResource(R.string.settings_display_split_title), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showDisplayStylesSheet) {
            val displayStylesScrollState = rememberScrollState()
            ModalBottomSheet(
                onDismissRequest = { showDisplayStylesSheet = false },
                containerColor = sheetBgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsPopupFadingEdge(displayStylesScrollState)
                        .verticalScroll(displayStylesScrollState)
                        .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp))
                ) {
                    Text(
                        text = stringResource(R.string.settings_display_style_title),
                        style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = sizing.spacing(16.dp))
                    )

                    NavigationModeOption(
                        title = stringResource(R.string.settings_display_fullscreen_title),
                        desc = stringResource(R.string.settings_display_fullscreen_desc),
                        isSelected = displayStyle == 0,
                        useOneUi = useOneUi,
                        onClick = {
                            displayStyle = 0
                            prefs.edit().putInt("display_style", 0).apply()
                            AppConfig.displayStyle.intValue = 0
                            showDisplayStylesSheet = false
                        }
                    )

                    Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

                    NavigationModeOption(
                        title = stringResource(R.string.settings_display_split_title),
                        desc = stringResource(R.string.settings_display_split_desc),
                        isSelected = displayStyle == 1,
                        useOneUi = useOneUi,
                        onClick = {
                            displayStyle = 1
                            prefs.edit().putInt("display_style", 1).apply()
                            AppConfig.displayStyle.intValue = 1
                            showDisplayStylesSheet = false
                        }
                    )
                }
            }
        }
    }

    // ========================================================
    // ✅ NOUVEAU : NOTIFICATIONS DE MISE À JOUR DE LA BASE
    // ========================================================
    val updateNotifsEnabled by fr.geotower.utils.AppConfig.enableUpdateNotifications

    PreferenceSwitchCard(
        title = stringResource(R.string.appstrings_update_notif_setting_title),
        desc = stringResource(R.string.appstrings_update_notif_setting_desc),
        checked = updateNotifsEnabled,
        onCheckedChange = { isChecked ->
            fr.geotower.utils.AppConfig.enableUpdateNotifications.value = isChecked
            prefs.edit().putBoolean("enable_update_notifications", isChecked).apply()
            UpdateCheckScheduler.onNotificationsPreferenceChanged(context, isChecked)

            // Demande la permission sur Android 13+ si on active
            if (isChecked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    (context as? android.app.Activity)?.requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1003
                    )
                }
            }
        },
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )
    Spacer(Modifier.height(12.dp))

    // ========================================================
    // ✅ MODE FAIBLE CONSOMMATION (Normal / Éco / Éco+)
    // ========================================================
    val lowPowerLevel by AppConfig.lowPowerLevel
    val lowPowerFollowSystem by AppConfig.lowPowerFollowSystem
    // Niveau EFFECTIF (manuel, ou relevé par l'économie d'énergie système) → la sélection le reflète, réactif.
    val effectiveLowPowerLevel = fr.geotower.utils.PowerProfile.level
    fun applyLowPowerLevel(newLevel: Int) {
        AppConfig.lowPowerLevel.intValue = newLevel
        prefs.edit().putInt(AppConfig.PREF_LOW_POWER_LEVEL, newLevel).apply()
        // Applique à chaud la priorité/intervalle GPS au service live s'il tourne.
        LiveTrackingController.refreshLocationSettings(context)
    }
    Surface(
        shape = shape,
        border = border,
        color = if (useOneUi) bubbleColor else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(sizing.spacing(16.dp))) {
            Text(
                stringResource(R.string.appstrings_low_power_title),
                style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.appstrings_low_power_desc),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            NavigationModeOption(
                title = stringResource(R.string.appstrings_low_power_level_normal),
                desc = stringResource(R.string.appstrings_low_power_level_normal_desc),
                isSelected = effectiveLowPowerLevel == 0,
                useOneUi = useOneUi,
                onClick = { applyLowPowerLevel(0) }
            )
            Spacer(Modifier.height(8.dp))
            NavigationModeOption(
                title = stringResource(R.string.appstrings_low_power_level_eco),
                desc = stringResource(R.string.appstrings_low_power_level_eco_desc),
                isSelected = effectiveLowPowerLevel == 1,
                useOneUi = useOneUi,
                onClick = { applyLowPowerLevel(1) }
            )
            Spacer(Modifier.height(8.dp))
            NavigationModeOption(
                title = stringResource(R.string.appstrings_low_power_level_ecoplus),
                desc = stringResource(R.string.appstrings_low_power_level_ecoplus_desc),
                isSelected = effectiveLowPowerLevel == 2,
                useOneUi = useOneUi,
                onClick = { applyLowPowerLevel(2) }
            )
            if (effectiveLowPowerLevel > lowPowerLevel) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.appstrings_low_power_forced_by_system),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    PreferenceSwitchCard(
        title = stringResource(R.string.appstrings_low_power_follow_system_title),
        desc = stringResource(R.string.appstrings_low_power_follow_system_desc),
        checked = lowPowerFollowSystem,
        onCheckedChange = { isChecked ->
            AppConfig.lowPowerFollowSystem.value = isChecked
            prefs.edit().putBoolean(AppConfig.PREF_LOW_POWER_FOLLOW_SYSTEM, isChecked).apply()
            LiveTrackingController.refreshLocationSettings(context)
        },
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )
    Spacer(Modifier.height(12.dp))

    // --- NOUVEAU : NOTIFICATION LIVE ---
    val liveNotifsEnabled by AppConfig.enableLiveNotifications
    val isOperatorSelected = op != "Aucun"

    fr.geotower.ui.components.LiveNotificationCard(
        title = stringResource(R.string.appstrings_live_notification_title),
        desc = if (isOperatorSelected) stringResource(R.string.appstrings_live_notification_desc) else stringResource(R.string.appstrings_live_notification_desc_nearest),
        checked = liveNotifsEnabled,
        onCheckedChange = { isChecked ->
            if (isChecked) {
                val eligibility = LiveTrackingController.eligibility(context)
                if (
                    eligibility == LiveTrackingController.StartResult.MissingNotifications &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                ) {
                    (context as? android.app.Activity)?.requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        1004
                    )
                }

                if (eligibility == LiveTrackingController.StartResult.Started) {
                    AppConfig.enableLiveNotifications.value = true
                    prefs.edit().putBoolean("enable_live_notifications", true).apply()
                    if (LiveTrackingController.shouldOpenPromotedNotificationSettings(context)) {
                        LiveTrackingController.openPromotedNotificationSettings(context)
                    }
                    LiveTrackingController.startIfEligible(context)
                } else {
                    AppConfig.enableLiveNotifications.value = false
                    prefs.edit().putBoolean("enable_live_notifications", false).apply()
                    LiveTrackingController.stop(context)
                }
            } else {
                AppConfig.enableLiveNotifications.value = false
                prefs.edit().putBoolean("enable_live_notifications", false).apply()
                LiveTrackingController.stop(context)
            }
        },
        enabled = true,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )

    if (liveNotifsEnabled) {
        Spacer(Modifier.height(12.dp))
        // Mode faible conso : le slider suit la valeur imposée par le niveau (grisé tant qu'il pilote).
        val intervalFloor = fr.geotower.utils.PowerProfile.liveIntervalFloorSeconds
        val intervalImposedByEco = intervalFloor > 0
        val effectiveInterval = if (intervalImposedByEco) maxOf(liveLocationIntervalSeconds, intervalFloor) else liveLocationIntervalSeconds
        fr.geotower.ui.components.CustomSliderCard(
            title = stringResource(R.string.appstrings_live_location_refresh_title),
            currentValue = effectiveInterval,
            enabled = !intervalImposedByEco,
            steps = LiveTrackingPrefs.LOCATION_UPDATE_INTERVAL_OPTIONS_SECONDS,
            labels = LiveTrackingPrefs.LOCATION_UPDATE_INTERVAL_OPTIONS_SECONDS.map { "$it s" },
            onValueChange = { newIntervalSeconds ->
                val normalizedIntervalSeconds =
                    LiveTrackingPrefs.normalizeLocationUpdateIntervalSeconds(newIntervalSeconds)
                liveLocationIntervalSeconds = normalizedIntervalSeconds
                prefs.edit()
                    .putInt(
                        LiveTrackingPrefs.LOCATION_UPDATE_INTERVAL_SECONDS,
                        normalizedIntervalSeconds
                    )
                    .apply()
                LiveTrackingController.refreshLocationSettings(context)
            },
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            footerText = stringResource(R.string.appstrings_live_location_refresh_footer)
        )
        Spacer(Modifier.height(12.dp))
        val priorityLabels = listOf(
            stringResource(R.string.appstrings_live_location_accuracy_low),
            stringResource(R.string.appstrings_live_location_accuracy_balanced),
            stringResource(R.string.appstrings_live_location_accuracy_high)
        )
        // Mode faible conso : impose au moins BALANCED → le slider se cale dessus (grisé), sauf réglage plus économe.
        val priorityImposedByEco = fr.geotower.utils.PowerProfile.gpsBalanced
        val effectivePriority = if (priorityImposedByEco) maxOf(liveLocationPriority, LiveTrackingPrefs.PRIORITY_BALANCED_POWER_ACCURACY) else liveLocationPriority
        fr.geotower.ui.components.CustomSliderCard(
            title = stringResource(R.string.appstrings_live_location_accuracy_title),
            currentValue = effectivePriority,
            enabled = !priorityImposedByEco,
            steps = LiveTrackingPrefs.LOCATION_PRIORITY_OPTIONS,
            labels = priorityLabels,
            onValueChange = { newPriority ->
                val normalizedPriority =
                    LiveTrackingPrefs.normalizeLocationPriority(newPriority)
                liveLocationPriority = normalizedPriority
                prefs.edit()
                    .putInt(LiveTrackingPrefs.LOCATION_PRIORITY, normalizedPriority)
                    .apply()
                LiveTrackingController.refreshLocationSettings(context)
            },
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            footerText = stringResource(R.string.appstrings_live_location_accuracy_footer)
        )
    }
    Spacer(Modifier.height(12.dp))

    PreferenceOperatorCard(stringResource(R.string.settings_default_operator), op, onOp, shape, border, bubbleColor, useOneUi, safeClick)
    Spacer(Modifier.height(12.dp))

    PreferenceLanguageCard(stringResource(R.string.settings_app_language), lang, onLang, shape, border, bubbleColor, useOneUi, safeClick)
    Spacer(Modifier.height(12.dp))
    PreferenceActionCard(
        title = stringResource(R.string.settings_units_title),
        desc = stringResource(R.string.settings_units_desc),
        onClick = onUnitSettings,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick,
        icon = Icons.Default.Straighten
    )
    Spacer(Modifier.height(12.dp))

    if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.PAGES_CUSTOMIZATION)) {
        PreferenceActionCard(
            title = stringResource(R.string.settings_pages_customization_title),
            desc = stringResource(R.string.settings_pages_customization_desc),
            onClick = onPages,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick,
            icon = Icons.Default.Edit
        )
        Spacer(Modifier.height(12.dp))
    }
    if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.EXTERNAL_LINKS_SETTINGS)) {
        PreferenceActionCard(
            title = stringResource(R.string.settings_external_links_title),
            desc = stringResource(R.string.settings_external_links_desc),
            onClick = onExternalLinks,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick,
            icon = Icons.Default.Language
        )
        Spacer(Modifier.height(12.dp))
    }

    if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
        PreferenceActionCard(
            title = stringResource(R.string.settings_default_share_content_title),
            desc = stringResource(R.string.settings_default_share_content_desc),
            onClick = onSharePrefs,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick,
            icon = Icons.Default.Share
        )
        Spacer(Modifier.height(12.dp))
    }

    PreferenceActionCard(
        title = stringResource(R.string.photos_favorites_title),
        desc = stringResource(R.string.photos_favorites_desc),
        onClick = onPhotosFavorites,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick,
        icon = Icons.Default.PhotoLibrary
    )
    Spacer(Modifier.height(12.dp))

    // --- NOUVEAU : BOUTON D'AUTORISATION ARRIÈRE-PLAN ---
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val backgroundPermissionLabel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching { context.packageManager.backgroundPermissionOptionLabel.toString() }.getOrNull()
        } else {
            null
        }

        // ✅ 1. L'état qui permet à l'interface de se mettre à jour toute seule
        var isBgLocationGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        // ✅ 2. On écoute le retour sur l'application pour revérifier la permission instantanément
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isBgLocationGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ✅ 3. L'animation de disparition fluide (le bloc disparaît si isBgLocationGranted devient true)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isBgLocationGranted,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Column {
                PreferenceActionCard(
                    title = stringResource(R.string.appstrings_bg_location_perm_title),
                    desc = buildString {
                        append(stringResource(R.string.appstrings_bg_location_perm_desc))
                        if (!backgroundPermissionLabel.isNullOrBlank()) {
                            append('\n')
                            append(backgroundPermissionLabel)
                        }
                    },
                    onClick = {
                        // ✅ 4. Trouver la VRAIE activité (On déballe le contexte de Compose pour réparer le bug de redirection)
                        var currentContext = context
                        while (currentContext is android.content.ContextWrapper && currentContext !is android.app.Activity) {
                            currentContext = currentContext.baseContext
                        }
                        val activity = currentContext as? android.app.Activity
                        val hasFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!hasFineLocation && !hasCoarseLocation) {
                            activity?.requestPermissions(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ),
                                1005
                            )
                            return@PreferenceActionCard
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                // FLAG_ACTIVITY_NEW_TASK : LocalContext est le contexte localisé (LocaleProvider),
                                // pas une Activity → sans ce flag, l'ouverture des réglages échoue silencieusement sur OnePlus.
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                            return@PreferenceActionCard
                        }

                        // ✅ 5. Analyser l'état de la permission
                        val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ?: false
                        val alreadyAsked = prefs.getBoolean("bg_loc_asked", false)

                        // Si on a déjà demandé, que l'OS refuse d'afficher l'alerte, ET qu'on a bien trouvé l'activité
                        if (alreadyAsked && !shouldShowRationale && activity != null) {
                            // Plan B : Le blocage est total (bouton "Ne plus demander" coché), on ouvre les paramètres globaux
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // idem : contexte localisé sans Activity
                            }
                            context.startActivity(intent)
                        } else {
                            // Plan A : La magie d'Android ! Ouvre le sous-menu "Position"
                            prefs.edit().putBoolean("bg_loc_asked", true).apply()
                            bgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }

                        // ❌ Le Toast a été supprimé !
                    },
                    shape = shape, border = border, bubbleColor = MaterialTheme.colorScheme.errorContainer, useOneUi = useOneUi, safeClick = safeClick,
                    icon = Icons.Default.Place
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // --- CURSEUR PARTAGÉ (Nettoyé des < 30 min) ---
    fr.geotower.ui.components.CustomSliderCard(
        title = stringResource(R.string.appstrings_widget_refresh_title),
        currentValue = widgetFrequency,
        steps = listOf(30, 45, 60, 120, 240, 480, 720, 1440),
        labels = listOf("30 min", "45 min", "1 h", "2 h", "4 h", "8 h", "12 h", "24 h"),
        onValueChange = { newFreq ->
            if (!hasWidgetInstalled) return@CustomSliderCard
            widgetFrequency = newFreq
            prefs.edit().putInt(WidgetPrefs.SYNC_FREQUENCY_MINUTES, newFreq).apply()

            // Mettre à jour le WorkManager instantanément avec la nouvelle fréquence
            WidgetUpdateScheduler.schedulePeriodicUpdate(context, newFreq)
        },
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        enabled = hasWidgetInstalled,
        footerText = if (hasWidgetInstalled) {
            stringResource(R.string.appstrings_widget_refresh_warning)
        } else {
            stringResource(R.string.appstrings_widget_refresh_disabled_no_widget)
        }
    )

    // Bouton « Ajouter un widget » : visible uniquement quand aucun widget n'est posé
    // et que le launcher sait épingler (One UI, etc.). Il déclenche la boîte de dialogue système.
    val canPinWidget = remember { fr.geotower.widget.WidgetUpdateScheduler.canPinWidget(context) }
    if (!hasWidgetInstalled && canPinWidget) {
        Spacer(Modifier.height(12.dp))
        PreferenceActionCard(
            title = stringResource(R.string.appstrings_widget_add_button_title),
            desc = stringResource(R.string.appstrings_widget_add_button_desc),
            onClick = { showWidgetPickerSheet = true },
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick,
            icon = Icons.Default.Add
        )
    }

    if (showWidgetPickerSheet) {
        WidgetFormatPickerSheet(
            useOneUi = useOneUi,
            onPick = { receiver ->
                fr.geotower.widget.WidgetUpdateScheduler.requestPinWidget(context, receiver)
                showWidgetPickerSheet = false
            },
            onDismiss = { showWidgetPickerSheet = false }
        )
    }
}

/** Format de widget proposé à l'épinglage : libellé, taille et receiver cible. */
private data class WidgetFormatOption(
    val labelRes: Int,
    val sizeRes: Int,
    val receiver: Class<*>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetFormatPickerSheet(
    useOneUi: Boolean,
    onPick: (Class<*>) -> Unit,
    onDismiss: () -> Unit
) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val sizing = LocalGeoTowerUiStyle.current.sizing

    val formats = listOf(
        WidgetFormatOption(R.string.antenna_widget_label, R.string.appstrings_widget_size_compact, fr.geotower.widget.AntennaWidgetReceiver::class.java),
        WidgetFormatOption(R.string.antenna_widget_label, R.string.appstrings_widget_size_medium, fr.geotower.widget.AntennaWidgetMediumReceiver::class.java),
        WidgetFormatOption(R.string.antenna_widget_label, R.string.appstrings_widget_size_large, fr.geotower.widget.AntennaWidgetLargeReceiver::class.java),
        WidgetFormatOption(R.string.antenna_map_widget_label, R.string.appstrings_widget_size_medium, fr.geotower.widget.AntennaMapWidgetReceiver::class.java)
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetBgColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp))
        ) {
            Text(
                text = stringResource(R.string.appstrings_widget_add_button_title),
                style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = sizing.spacing(16.dp))
            )
            formats.forEachIndexed { index, format ->
                if (index > 0) Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))
                val cardBg = if (useOneUi) MaterialTheme.colorScheme.surface else Color.Transparent
                val cardBorder = if (useOneUi) null else BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Surface(
                    onClick = { onPick(format.receiver) },
                    shape = if (useOneUi) RoundedCornerShape(sizing.component(22.dp)) else RoundedCornerShape(sizing.component(12.dp)),
                    border = cardBorder,
                    color = cardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(format.labelRes), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                            Text(stringResource(format.sizeRes), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(22.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionSysteme(
    ctx: Context,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: SafeClick,
    onOpenDiagnostic: () -> Unit = {}
) {
    SectionTitle(stringResource(R.string.settings_section_system));
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = { safeClick("system_app_details_settings") { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", ctx.packageName, null) }) } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column {
                Text(stringResource(R.string.appstrings_manage_permissions), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.appstrings_permissions_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Spacer(Modifier.height(sizing.spacing(12.dp)))

    Surface(
        onClick = { safeClick("system_diagnostic") { onOpenDiagnostic() } },
        shape = shape,
        border = border,
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column {
                Text(stringResource(R.string.appstrings_diagnostic_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.appstrings_diagnostic_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionDatabase(
    isWideScreen: Boolean,
    shape: Shape,
    bubbleColor: Color,
    useOneUi: Boolean,
    repository: AnfrRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    modifier: Modifier = Modifier,
    offlineMapsModifier: Modifier = Modifier,
    viewportTop: Float = Float.NaN,
    viewportBottom: Float = Float.NaN,
    scrollValue: Int = 0,
    scrollMaxValue: Int = 0,
    targetMapFilename: String? = null,
    onTargetMapPositioned: (Float, Int) -> Unit = { _, _ -> },
    onOfflineMapsExpandedChange: (Boolean) -> Unit = {}
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    // On génère la bordure si on n'est pas en OneUI
    val border = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    // 🚀 LA CARTE DE LA BASE DE DONNÉES (Existante)
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_section_database))

        fr.geotower.ui.components.DatabaseDownloadCard(
            useOneUi = useOneUi,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            title = stringResource(R.string.settings_section_database)
        )

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        fr.geotower.ui.components.RadioDatabaseDownloadCard(
            useOneUi = useOneUi,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor
        )

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        fr.geotower.ui.components.LocalDbBuildCard(
            useOneUi = useOneUi,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor
        )
    }

    Spacer(modifier = Modifier.height(sizing.spacing(16.dp))) // Espace entre les deux cartes

    // 🚀 NOUVEAU : LA CARTE DES CARTES HORS-LIGNE
    Box(modifier = offlineMapsModifier.fillMaxWidth()) {
        fr.geotower.ui.components.MapDownloadCard(
            useOneUi = useOneUi,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            viewportTop = viewportTop,
            viewportBottom = viewportBottom,
            scrollValue = scrollValue,
            scrollMaxValue = scrollMaxValue,
            targetMapFilename = targetMapFilename,
            onTargetMapPositioned = onTargetMapPositioned,
            onExpandedChange = onOfflineMapsExpandedChange
        )
    }

    if (!isWideScreen) {
        Spacer(modifier = Modifier.height(sizing.spacing(32.dp)))
        TextButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.settings_reset), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(R.string.settings_reset_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_reset_warning_desc)) },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            dismissButton = {
                DialogDestructiveButton(text = stringResource(R.string.common_yes), onClick = {
                    showResetDialog = false
                    resetSettingsToDefaultsAndRestart(context, prefs)
                })
            },
            confirmButton = {
                DialogNeutralButton(text = stringResource(R.string.common_no), onClick = { showResetDialog = false })
            }
        )
    }
}

@Composable
fun SettingsOptionCard(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val themeMode by AppConfig.themeMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val paleBgColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val paleTextColor = if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer

    val finalColor = if (isSelected) paleBgColor else (if (useOneUi) bubbleColor else Color.Transparent)
    val contentColor = if (isSelected) paleTextColor else MaterialTheme.colorScheme.onSurface

    Surface(onClick = onClick, modifier = modifier.height(sizing.component(80.dp)), shape = shape, border = if (isSelected) null else border, color = finalColor) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Text(label, style = sizing.textStyle(MaterialTheme.typography.labelMedium), color = contentColor)
        }
    }
}

@Composable
fun PreferenceSwitchCard(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    // Toujours primary !
    val accentColor = MaterialTheme.colorScheme.primary

    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(desc, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            fr.geotower.ui.components.GeoTowerSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                useOneUi = useOneUi,
                checkedColor = accentColor
            )
        }
    }
}

@Composable
fun PreferenceActionCard(
    title: String,
    desc: String,
    onClick: () -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: SafeClick,
    icon: ImageVector? = null // ✅ Garde le paramètre d'icône (si pas déjà fait)
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = { safeClick("preference_action_$title") { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            // ✅ LE TEXTE RESTE À GAUCHE (Prend toute la place dispo grâce au weight)
            Column(Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                if (desc.isNotEmpty()) {
                    Text(desc, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ✅ AJOUT DE L'ICÔNE ICI (À droite du texte, avant la flèche)
            if (icon != null) {
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp))) // Espace avec le texte
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(sizing.component(22.dp)) // Légèrement plus petite pour l'équilibre
                )
            }

            // ✅ LA FLÈCHE RESTE TOUT À DROITE
            Spacer(modifier = Modifier.width(sizing.spacing(8.dp))) // Espace avec l'icône
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(24.dp)))
        }
    }
}

@Composable
fun PreferenceOperatorCard(title: String, operator: String, onClick: () -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val logoRes = OperatorLogos.drawableRes(operator)
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = { safeClick("preference_operator_$title") { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(if (operator == "Aucun") stringResource(R.string.common_select) else stringResource(R.string.common_current_value, operator), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logoRes != null) {
                    Image(painterResource(logoRes), contentDescription = null, modifier = Modifier.size(sizing.component(32.dp)).clip(RoundedCornerShape(sizing.component(6.dp))))
                    Spacer(Modifier.width(sizing.spacing(12.dp)))
                } else {
                    // ✅ AJOUT DE L'ICÔNE PAR DÉFAUT ICI (Si aucun opérateur n'est choisi)
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(sizing.component(24.dp))
                    )
                    Spacer(Modifier.width(sizing.spacing(12.dp)))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(24.dp)))
            }
        }
    }
}

@Composable
fun PreferenceLanguageCard(title: String, language: String, onClick: () -> Unit, shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent

    val flag = AppLocale.languageFlag(language)
    val displayLanguage = stringResource(AppLocale.languageDisplayNameRes(language))

    Surface(onClick = { safeClick("preference_language_$title") { onClick() } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.common_current_value, displayLanguage), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // L'EMOJI REMPLACE L'ICÔNE PLANÈTE
                Box(
                    modifier = Modifier.size(sizing.component(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = flag, fontSize = sizing.text(24.sp))
                }
                Spacer(Modifier.width(sizing.spacing(8.dp)))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sizing.component(24.dp)))
            }
        }
    }
}

private fun iconPreviewResource(iconRes: Int): Int {
    return when (iconRes) {
        R.mipmap.ic_launcher_georadio -> R.mipmap.ic_launcher_georadio_foreground
        R.mipmap.ic_launcher_funny -> R.mipmap.ic_launcher_funny_foreground
        else -> R.mipmap.ic_launcher_geotower_foreground
    }
}

@Composable
private fun LauncherIconPreview(iconRes: Int, modifier: Modifier = Modifier) {
    val themeMode by AppConfig.themeMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    DrawableImage(if (isDark) iconPreviewResource(iconRes) else iconRes, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoDrawingSheet(
    onDismiss: () -> Unit,
    currentChoice: String,
    activeIconRes: Int,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    sheetState: SheetState,
    useOneUi: Boolean,
    bubbleColor: Color,
    safeClick: SafeClick
) {
    val normalizedCurrent = AppLogoDrawingResources.normalize(currentChoice)
    val options = remember { AppLogoDrawingResources.choices }
    val scrollState = rememberScrollState()
    val sheetBgColor = if (useOneUi) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    val sizing = LocalGeoTowerUiStyle.current.sizing

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(start = sizing.spacing(24.dp), end = sizing.spacing(24.dp), bottom = sizing.spacing(40.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.appstrings_app_logo_drawing_title),
                style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = sizing.spacing(8.dp))
            )
            Text(
                stringResource(R.string.appstrings_app_logo_drawing_subtitle),
                style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = sizing.spacing(20.dp))
            )

            options.forEach { choice ->
                val family = AppLogoDrawingResources.family(choice)
                val previousChoice = options.getOrNull(options.indexOf(choice) - 1)
                val previousFamily = previousChoice?.let { AppLogoDrawingResources.family(it) }
                if (family != null && family != previousFamily) {
                    Text(
                        text = appLogoDrawingFamilyName(family),
                        style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(12.dp), bottom = sizing.spacing(8.dp))
                    )
                }

                LogoDrawingOptionRow(
                    choice = choice,
                    activeIconRes = activeIconRes,
                    isDark = isDark,
                    isSelected = normalizedCurrent == choice,
                    useOneUi = useOneUi,
                    bubbleColor = bubbleColor,
                    onClick = {
                        safeClick("logo_drawing_$choice") {
                            onSelect(choice)
                        }
                    }
                )
                Spacer(Modifier.height(sizing.spacing(8.dp)))
            }
        }
    }
}

@Composable
private fun LogoDrawingOptionRow(
    choice: String,
    activeIconRes: Int,
    isDark: Boolean,
    isSelected: Boolean,
    useOneUi: Boolean,
    bubbleColor: Color,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val previewRes = AppLogoDrawingResources.resolve(choice, activeIconRes, isDark)
    val cardColor = if (useOneUi) bubbleColor else Color.Transparent
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (useOneUi) sizing.component(22.dp) else sizing.component(12.dp)),
        color = cardColor,
        border = BorderStroke(sizing.component(if (isSelected) 2.dp else 1.dp), borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(10.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                modifier = Modifier.size(sizing.component(52.dp)),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageResource(previewRes)
                    }
                },
                update = { it.setImageResource(previewRes) }
            )
            Spacer(Modifier.width(sizing.spacing(14.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appLogoDrawingChoiceName(choice),
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = appLogoDrawingChoiceDescription(choice),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = isSelected, onClick = onClick)
        }
    }
}

@Composable
fun NavigationMenuItem(title: String, icon: ImageVector, isSelected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    // 1. On utilise le beau bleu dynamique pour l'élément sélectionné
    val activeColor = MaterialTheme.colorScheme.primary
    // 2. On utilise le gris par défaut d'Android pour les éléments inactifs
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 3. Fond légèrement bleuté (15% d'opacité) si sélectionné, sinon transparent
    val bgColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent

    // 4. Couleur du texte et de l'icône
    val contentColor = if (isSelected) activeColor else inactiveColor

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(4.dp)),
        shape = RoundedCornerShape(sizing.component(12.dp)),
        color = bgColor
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Text(
                text = title,
                style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun NavigationModeOption(
    title: String,
    desc: String,
    isSelected: Boolean,
    useOneUi: Boolean,
    // ✅ NOUVEAU PARAMÈTRE : trailingIcon
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        useOneUi -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val border = if (!useOneUi && isSelected) BorderStroke(sizing.component(1.dp), MaterialTheme.colorScheme.primary) else null
    val optionShape = if (useOneUi) RoundedCornerShape(sizing.component(22.dp)) else RoundedCornerShape(sizing.component(12.dp))
    val selectedTextColor = if (useOneUi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
    val selectedDescColor = if (useOneUi) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = optionShape,
        color = bgColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold, color = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(sizing.spacing(2.dp)))
                Text(desc, style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = if (isSelected) selectedDescColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ✅ NOUVELLE LOGIQUE : Affichage de l'icône descriptive
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    // Légèrement transparent si non sélectionné, coloré si sélectionné
                    tint = if (isSelected) selectedTextColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    // Espacement avant la coche si sélectionné, sinon aligné à droite
                    modifier = Modifier.padding(end = sizing.spacing(8.dp)).size(sizing.component(20.dp))
                )
            }

            if (useOneUi) {
                fr.geotower.ui.components.OneUiRadioButton(isSelected, onClick)
            } else {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSheet(
    onDismiss: () -> Unit,
    currentIconRes: Int,
    onToggle: (Int) -> Unit,
    context: Context,
    sheetState: SheetState,
    useOneUi: Boolean,
    safeClick: SafeClick
) {
    // 1. On détermine l'index initial (0, 1 ou 2) en fonction de l'image actuellement active
    val initialIndex = when (currentIconRes) {
        R.mipmap.ic_launcher_georadio -> 1
        R.mipmap.ic_launcher_funny -> 2
        else -> 0
    }

    // 2. On crée une variable temporaire pour stocker le clic avant la validation
    var tempIconIndex by remember { mutableIntStateOf(initialIndex) }

    // --- AJOUTS POUR OLED ---
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val scrollState = rememberScrollState()
    val sizing = LocalGeoTowerUiStyle.current.sizing

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBgColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsPopupFadingEdge(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.appstrings_app_icon), style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = sizing.spacing(32.dp)))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {

                // --- LOGO 1 (Classique) : Index 0 ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Clic sur l'image : on change juste la variable temporaire (pas de onDismiss/onToggle)
                    Surface(onClick = { safeClick("launcher_icon_0") { tempIconIndex = 0 } }, shape = RoundedCornerShape(sizing.component(22.dp)), color = Color.Transparent, modifier = Modifier.size(sizing.component(70.dp))) { LauncherIconPreview(R.mipmap.ic_launcher_geotower, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(sizing.spacing(12.dp)))
                    val isSelected = tempIconIndex == 0
                    // Clic sur le cercle radio
                    if(useOneUi) fr.geotower.ui.components.OneUiRadioButton(isSelected) { tempIconIndex = 0 } else RadioButton(selected = isSelected, onClick = { tempIconIndex = 0 })
                }

                // --- LOGO 2 (Radio) : Index 1 ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = { safeClick("launcher_icon_1") { tempIconIndex = 1 } }, shape = RoundedCornerShape(sizing.component(22.dp)), color = Color.Transparent, modifier = Modifier.size(sizing.component(70.dp))) { LauncherIconPreview(R.mipmap.ic_launcher_georadio, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(sizing.spacing(12.dp)))
                    val isSelected = tempIconIndex == 1
                    if(useOneUi) fr.geotower.ui.components.OneUiRadioButton(isSelected) { tempIconIndex = 1 } else RadioButton(selected = isSelected, onClick = { tempIconIndex = 1 })
                }

                // --- LOGO 3 (Funny) : Index 2 ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(onClick = { safeClick("launcher_icon_2") { tempIconIndex = 2 } }, shape = RoundedCornerShape(sizing.component(22.dp)), color = Color.Transparent, modifier = Modifier.size(sizing.component(70.dp))) { LauncherIconPreview(R.mipmap.ic_launcher_funny, Modifier.fillMaxSize()) }
                    Spacer(Modifier.height(sizing.spacing(12.dp)))
                    val isSelected = tempIconIndex == 2
                    if(useOneUi) fr.geotower.ui.components.OneUiRadioButton(isSelected) { tempIconIndex = 2 } else RadioButton(selected = isSelected, onClick = { tempIconIndex = 2 })
                }
            }

            Text(stringResource(R.string.appstrings_restart_to_apply), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = sizing.spacing(24.dp), bottom = sizing.spacing(16.dp)))

            // --- NOUVEAU : BOUTON VALIDER ---
            Button(
                onClick = {
                    safeClick {
                        onToggle(tempIconIndex) // On applique le changement
                        onDismiss() // On ferme la fenêtre
                    }
                },
                modifier = Modifier.fillMaxWidth().height(sizing.component(50.dp)),
                shape = RoundedCornerShape(sizing.component(25.dp))
            ) {
                Text(stringResource(R.string.appstrings_validate), style = sizing.textStyle(MaterialTheme.typography.labelLarge), fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun SettingsTopBar(onBack: () -> Unit) {
    GeoTowerBackTopBar(
        title = stringResource(R.string.nav_settings),
        onBack = onBack,
        backgroundColor = MaterialTheme.colorScheme.background
    )
}

@Composable
fun DrawableImage(resId: Int, modifier: Modifier = Modifier) { AndroidView({ ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, modifier, { view -> view.setImageResource(resId) }) }

// ============================================================
// 🔎 RECHERCHE DE PARAMÈTRES
// ============================================================

/** Une entrée indexée pour la barre de recherche des réglages. */
private class SettingsSearchEntry(
    val title: String,
    val keywords: String,
    val sectionLabel: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** Normalise une chaîne pour la recherche : minuscules + suppression des accents. */
private fun normalizeForSearch(input: String): String {
    val decomposed = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{Mn}+"), "").lowercase()
}

@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    Surface(shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(22.dp))
            )
            Spacer(Modifier.width(sizing.spacing(12.dp)))
            Box(modifier = Modifier.weight(1f).padding(vertical = sizing.spacing(16.dp))) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_placeholder),
                        style = sizing.textStyle(MaterialTheme.typography.bodyLarge),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = sizing.textStyle(MaterialTheme.typography.bodyLarge)
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(sizing.component(36.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(sizing.component(20.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    entries: List<SettingsSearchEntry>,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val tokens = normalizeForSearch(query).split(' ').filter { it.isNotBlank() }
    val results = if (tokens.isEmpty()) {
        emptyList()
    } else {
        entries.filter { entry ->
            val haystack = normalizeForSearch("${entry.title} ${entry.keywords} ${entry.sectionLabel}")
            tokens.all { haystack.contains(it) }
        }
    }

    if (results.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(48.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(sizing.component(40.dp))
            )
            Spacer(Modifier.height(sizing.spacing(12.dp)))
            Text(
                text = stringResource(R.string.settings_search_no_results),
                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
        ) {
            results.forEach { entry ->
                SettingsSearchResultRow(entry, shape, border, bubbleColor, useOneUi)
            }
        }
    }
}

@Composable
private fun SettingsSearchResultRow(
    entry: SettingsSearchEntry,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(onClick = entry.onClick, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.sectionLabel,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizing.component(24.dp))
            )
        }
    }
}
