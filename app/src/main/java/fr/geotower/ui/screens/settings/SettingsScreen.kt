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
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.ApiEndpoints
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
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.navigation.ROOT_FALLBACK_ROUTE
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.components.ApiServerModeDialog
import fr.geotower.ui.components.apiServerModeLabelRes
import fr.geotower.ui.components.applyApiServerMode
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.DatabaseRefreshState
import fr.geotower.ui.components.DatabaseRefreshTimeout
import fr.geotower.ui.components.DatabaseSectionRefreshButton
import fr.geotower.ui.components.rememberDatabaseRefreshIndicator
import fr.geotower.ui.components.rememberDatabaseRefreshState
import fr.geotower.ui.components.SafeClick
import fr.geotower.ui.components.colorPaletteFadingEdge
import fr.geotower.ui.components.DialogDestructiveButton
import fr.geotower.ui.components.DialogNeutralButton
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.components.appLogoDrawingChoiceDescription
import fr.geotower.ui.components.appLogoDrawingChoiceName
import fr.geotower.ui.components.appLogoDrawingFamilyName
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.GeoTowerFadingEdgeHeight
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.isGeoTowerFadingEdgeActive
import fr.geotower.ui.components.pageScrollbar
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

// Ordre des sections de réglages : sert d'index à la barre latérale des tablettes, à l'accueil
// par sections des téléphones, aux ancres de défilement et à l'index de recherche.
private const val SECTION_APPEARANCE = 0
private const val SECTION_MAPPING = 1
private const val SECTION_PREFERENCES = 2
private const val SECTION_BACKGROUND = 3
private const val SECTION_SYSTEM = 4
private const val SECTION_DATABASE = 5
private const val SECTION_COUNT = 6

// Ancres fines de la section « Base de données » : valeurs possibles du paramètre `section` des
// liens profonds `geotower://settings?section=…` émis par les notifications de téléchargement
// (DatabaseDownloadWorker, RadioDatabaseDownloadWorker, EnbDatabaseDownloadWorker,
// LocalDbBuildWorker, UpdateCheckWorker). Chaque identifiant amène pile sur SA carte, alors que
// `section=database` s'arrête au titre de la section. `db_outages` n'est émis par aucun worker
// (les pannes se téléchargent depuis la carte elle-même), il reste une cible de lien valable.
private const val ANCHOR_DB_MOBILE = "db_mobile"
private const val ANCHOR_DB_RADIO = "db_radio"
private const val ANCHOR_DB_ENB = "db_enb"
private const val ANCHOR_DB_OUTAGES = "db_outages"
private const val ANCHOR_DB_LOCAL_BUILD = "db_local_build"
private val DATABASE_CARD_ANCHORS = listOf(
    ANCHOR_DB_MOBILE,
    ANCHOR_DB_RADIO,
    ANCHOR_DB_ENB,
    ANCHOR_DB_OUTAGES,
    ANCHOR_DB_LOCAL_BUILD
)

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
    val sectionBringIntoViewRequesters = remember { List(SECTION_COUNT) { BringIntoViewRequester() } }
    val sectionRootPositions = remember { mutableStateMapOf<Int, Float>() }
    val sectionBounds = remember { mutableStateMapOf<Int, SettingsSectionBounds>() }
    var scrollViewportTop by remember { mutableFloatStateOf(0f) }
    var scrollViewportBottom by remember { mutableFloatStateOf(0f) }
    var offlineMapsBounds by remember { mutableStateOf(SettingsSectionBounds()) }
    val offlineMapsTargetFilename = targetOfflineMapFilename?.takeIf { it.isNotBlank() }
    var offlineMapsTargetBounds by remember(offlineMapsTargetFilename) { mutableStateOf(SettingsSectionBounds()) }
    var hasPrimedOfflineMapsTargetScroll by remember(initialSection, offlineMapsTargetFilename) { mutableStateOf(false) }
    val databaseBringIntoViewRequester = sectionBringIntoViewRequesters[SECTION_DATABASE]
    val offlineMapsBringIntoViewRequester = remember { BringIntoViewRequester() }
    // Les cibles de défilement d'un lien profond sont à usage unique et SURVIVENT à un aller-retour
    // vers un sous-écran (rememberSaveable) : sinon, revenir de « Traitement local » relançait le
    // défilement vers la section base de données comme si on rouvrait la notification.
    var shouldBringDatabaseIntoView by rememberSaveable(initialSection) { mutableStateOf(initialSection == "database") }
    var shouldBringOfflineMapsIntoView by rememberSaveable(initialSection) { mutableStateOf(initialSection == "offline_maps") }
    // Cibles fines des cartes de la section base de donnees (base mobile, base radio, base eNB,
    // sites en panne,
    // generation locale) : la notif d'un telechargement doit arriver precisement sur SA carte, pas
    // au titre de la section.
    val initialDatabaseCardAnchor = initialSection?.takeIf { it in DATABASE_CARD_ANCHORS }
    val databaseCardRequesters = remember { DATABASE_CARD_ANCHORS.associateWith { BringIntoViewRequester() } }
    val databaseCardBounds = remember { mutableStateMapOf<String, SettingsSectionBounds>() }
    var pendingDatabaseCardAnchor by rememberSaveable(initialSection) { mutableStateOf(initialDatabaseCardAnchor) }
    // Le lien profond n'ouvre sa section qu'une fois : au retour d'un sous-écran, on garde la
    // section réellement consultée par l'utilisateur.
    var hasAppliedInitialSection by rememberSaveable(initialSection) { mutableStateOf(false) }

    var themeMode by AppConfig.themeMode
    var isOledMode by AppConfig.isOledMode
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config
    val uiStyle = LocalGeoTowerUiStyle.current
    var showUnitSheet by remember { mutableStateOf(false) }
    var showColorPalettePage by rememberSaveable { mutableStateOf(false) }
    // Actualisation de la section « Base de données » : les quatre cartes relisent la base installée
    // et réinterrogent le manifeste. Deux déclencheurs selon le mode d'affichage — bouton sur le
    // titre en page unique, tirage vers le bas quand la section occupe la page à elle seule.
    val databaseRefreshState = rememberDatabaseRefreshState()
    val databaseRefreshVisible = rememberDatabaseRefreshIndicator(databaseRefreshState)
    DatabaseRefreshTimeout(databaseRefreshState)
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
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
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = ROOT_FALLBACK_ROUTE)

    // Accueil des réglages par sections (un bouton par section) au lieu de la longue page unique :
    // `openedSection` = null → on est sur l'accueil, sinon la section occupe la page.
    //   • téléphone : piloté par `settings_sections_mode` (bouton de la barre du haut) ;
    //   • grand écran (Fold déplié, tablette) : c'est le mode « pages » (nav_mode ≠ 0) qui ouvre
    //     le même accueil, la barre latérale restant un raccourci direct vers les sections.
    val navMode = AppConfig.navMode.intValue
    val settingsSectionsMode by AppConfig.settingsSectionsMode
    val usePhoneSections = !isWideScreen && settingsSectionsMode
    val useWideSections = isWideScreen && navMode != 0
    val useSectionsHome = usePhoneSections || useWideSections
    // rememberSaveable : partir sur un sous-écran (Traitement local, Diagnostic, Historiques…) ou
    // tourner l'appareil détruit la composition. Avec un simple `remember`, le retour retombait sur
    // l'accueil des sections au lieu de la section d'où l'on venait.
    var openedSection by rememberSaveable { mutableStateOf<Int?>(null) }
    // Section « active » de la barre latérale en défilement continu (nav_mode = 0) : elle suit le
    // défilement, alors qu'en accueil par sections c'est `openedSection` qui fait foi.
    var activeSectionIndex by rememberSaveable { mutableIntStateOf(0) }

    fun openSection(section: Int?) {
        openedSection = section
        if (section != null) activeSectionIndex = section
        scope.launch { scrollState.scrollTo(0) }
    }

    fun setSettingsSectionsMode(enabled: Boolean) {
        AppConfig.settingsSectionsMode.value = enabled
        prefs.edit().putBoolean(AppConfig.PREF_SETTINGS_SECTIONS_MODE, enabled).apply()
        openSection(null)
    }

    fun setNavMode(mode: Int) {
        AppConfig.navMode.intValue = mode
        prefs.edit().putInt(AppConfig.PREF_NAV_MODE, mode).apply()
        // Passage en « pages » : on ramène sur l'accueil des sections, c'est là qu'on arrive
        // désormais en ouvrant les réglages sur grand écran.
        openSection(null)
    }

    // Un seul cran par appui sur « retour », dans cet ordre :
    //   palette de couleurs → recherche → section ouverte → sortie de l'écran.
    // Les conditions sont mutuellement exclusives : on ne dépend pas de l'ordre d'enregistrement
    // des BackHandler dans le dispatcher (LIFO), qui est un détail d'implémentation.
    val isSectionOpen = useSectionsHome && openedSection != null
    val isSearchActive = settingsSearchQuery.isNotBlank()

    BackHandler(enabled = showColorPalettePage) {
        showColorPalettePage = false
    }

    // Recherche active : le retour efface d'abord la recherche au lieu de quitter l'écran.
    BackHandler(enabled = !showColorPalettePage && isSearchActive) {
        settingsSearchQuery = ""
    }

    // Accueil par sections : le retour remonte d'abord à la liste des sections.
    BackHandler(enabled = !showColorPalettePage && !isSearchActive && isSectionOpen) {
        openSection(null)
    }

    BackHandler(
        enabled = !showColorPalettePage && !isSearchActive && !isSectionOpen &&
            !safeBackNavigation.isLocked
    ) {
        safeBackNavigation.navigateBack()
    }

    var isBlurEnabled by AppConfig.isBlurEnabled
    var mapProvider by AppConfig.mapProvider
    var ignStyle by AppConfig.ignStyle
    var defaultOperator by AppConfig.defaultOperator

    // Une seule section à l'écran : section ouverte depuis l'accueil par sections (téléphone comme
    // grand écran en mode « pages »). Sinon tout est empilé sur une page.
    val showsSingleSection = isSectionOpen

    // Recherche : actions de navigation déclenchées depuis un résultat.
    fun searchScrollTo(section: Int) {
        settingsSearchQuery = ""
        activeSectionIndex = section
        // Accueil par sections : on ouvre directement la section, il n'y a rien à faire défiler.
        if (useSectionsHome) openSection(section) else pendingSearchScrollSection = section
    }
    // Ouvrir un réglage depuis la recherche positionne aussi la section : en refermant la fenêtre
    // (ou en revenant du sous-écran), on retombe sur la section du réglage, pas sur l'accueil.
    fun searchOpen(section: Int?, action: () -> Unit) {
        settingsSearchQuery = ""
        if (section != null) {
            activeSectionIndex = section
            if (useSectionsHome) openSection(section)
        }
        action()
    }

    // Flou au défilement : les bandes du haut et du bas sont délavées (voir geoTowerFadingEdge).
    // On vise donc SOUS la bande du haut, sinon le titre de la carte ciblée arrive illisible.
    val fadeInsetPx = if (isGeoTowerFadingEdgeActive()) {
        with(LocalDensity.current) { GeoTowerFadingEdgeHeight.toPx() }
    } else {
        0f
    }
    // ✅ NOUVEAU : Auto-scroll vers la section demandée (ex: database)
    suspend fun alignAnchorToViewportTop(anchorRootY: Float?) {
        if (anchorRootY == null || anchorRootY.isNaN() || scrollState.maxValue <= 0) return
        val target = (scrollState.value + (anchorRootY - scrollViewportTop - fadeInsetPx).roundToInt())
            .coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    fun isDisplayedAsMuchAsPossible(bounds: SettingsSectionBounds): Boolean {
        if (bounds.top.isNaN() || bounds.height <= 0 || scrollViewportBottom <= scrollViewportTop) return false

        // Zone réellement lisible : le viewport moins les bandes estompées. Le fondu du haut
        // n'existe qu'une fois qu'on a défilé, celui du bas tant qu'il reste du contenu.
        val readableTop = scrollViewportTop + if (scrollState.value > 0) fadeInsetPx else 0f
        val readableBottom = scrollViewportBottom -
            if (scrollState.value < scrollState.maxValue) fadeInsetPx else 0f
        if (readableBottom <= readableTop) return false

        val viewportHeight = readableBottom - readableTop
        val visibleTop = maxOf(bounds.top, readableTop)
        val visibleBottom = minOf(bounds.bottom, readableBottom)
        val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)
        val maxVisibleHeight = minOf(bounds.height.toFloat(), viewportHeight)

        return visibleHeight >= maxVisibleHeight - 2f
    }

    LaunchedEffect(initialSection) {
        if (hasAppliedInitialSection) return@LaunchedEffect
        hasAppliedInitialSection = true
        if (initialSection == "database" || initialSection == "offline_maps" || initialDatabaseCardAnchor != null) {
            // Les cartes hors ligne vivent maintenant dans la section Cartographie.
            val target = if (initialSection == "offline_maps") SECTION_MAPPING else SECTION_DATABASE
            activeSectionIndex = target
            if (useSectionsHome) openedSection = target
            shouldBringDatabaseIntoView = initialSection == "database"
            shouldBringOfflineMapsIntoView = initialSection == "offline_maps"
            pendingDatabaseCardAnchor = initialDatabaseCardAnchor
        }
    }

    // Les défilements ciblés valent pour TOUS les modes d'affichage (page unique, accueil par
    // sections des téléphones, pages des grands écrans/Fold) : le contenu est défilable partout.
    LaunchedEffect(shouldBringDatabaseIntoView, scrollState.maxValue) {
        if (shouldBringDatabaseIntoView && scrollState.maxValue > 0) {
            kotlinx.coroutines.delay(120)
            databaseBringIntoViewRequester.bringIntoView()
            kotlinx.coroutines.delay(80)
            alignAnchorToViewportTop(sectionRootPositions[SECTION_DATABASE])
            kotlinx.coroutines.delay(250)
            alignAnchorToViewportTop(sectionRootPositions[SECTION_DATABASE])
            shouldBringDatabaseIntoView = false
        }
    }

    // Scroll fin vers UNE carte de la section base de donnees (celle du telechargement en cours) :
    // on attend que la carte visee soit mesuree (bounds.isValid) avant d'aligner.
    val pendingDatabaseCardBounds = pendingDatabaseCardAnchor?.let { databaseCardBounds[it] }
    LaunchedEffect(
        pendingDatabaseCardAnchor,
        scrollState.maxValue,
        pendingDatabaseCardBounds?.isValid
    ) {
        val anchor = pendingDatabaseCardAnchor ?: return@LaunchedEffect
        val isMeasured = pendingDatabaseCardBounds?.isValid == true
        if (isMeasured && scrollState.maxValue > 0) {
            kotlinx.coroutines.delay(120)
            databaseCardRequesters[anchor]?.bringIntoView()
            kotlinx.coroutines.delay(80)
            alignAnchorToViewportTop(databaseCardBounds[anchor]?.top)
            kotlinx.coroutines.delay(250)
            alignAnchorToViewportTop(databaseCardBounds[anchor]?.top)
            pendingDatabaseCardAnchor = null
        }
    }

    LaunchedEffect(
        shouldBringOfflineMapsIntoView,
        scrollState.maxValue,
        offlineMapsTargetFilename,
        offlineMapsBounds.isValid,
        offlineMapsTargetBounds.isValid,
        hasPrimedOfflineMapsTargetScroll
    ) {
        if (shouldBringOfflineMapsIntoView && scrollState.maxValue > 0) {
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
        if (showsSingleSection) {
            // La section demandée occupe déjà toute la page : rien à chercher, on remonte en haut.
            scrollState.animateScrollTo(0)
            pendingSearchScrollSection = null
            return@LaunchedEffect
        }
        var tries = 0
        while (tries < 25 && (scrollState.maxValue <= 0 || sectionRootPositions[target] == null)) {
            kotlinx.coroutines.delay(40)
            tries++
        }
        sectionBringIntoViewRequesters[target].bringIntoView()
        kotlinx.coroutines.delay(80)
        alignAnchorToViewportTop(sectionRootPositions[target])
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
    var pageSiteNetworkIds by remember { mutableStateOf(SitePagePrefs.networkIds.read(prefs)) }
    var pageSiteOpenMap by remember { mutableStateOf(SitePagePrefs.openMap.read(prefs)) }
    var pageSiteElevationProfile by remember { mutableStateOf(SitePagePrefs.elevationProfile.read(prefs)) }
    var pageSiteThroughputCalculator by remember { mutableStateOf(SitePagePrefs.throughputCalculator.read(prefs)) }
    var pageSiteTheoreticalCoverage by remember { mutableStateOf(SitePagePrefs.theoreticalCoverage.read(prefs)) }
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
    var showMapFiltersDefaultsSheet by remember { mutableStateOf(false) }
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
                activeSectionIndex = SECTION_PREFERENCES
                if (useSectionsHome) {
                    openedSection = SECTION_PREFERENCES
                } else if (navMode == 0 || !isWideScreen) {
                    sectionBringIntoViewRequesters[SECTION_PREFERENCES].bringIntoView()
                    kotlinx.coroutines.delay(80)
                    alignAnchorToViewportTop(sectionRootPositions[SECTION_PREFERENCES])
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
        Triple(stringResource(R.string.settings_section_appearance), Icons.Outlined.Palette, SECTION_APPEARANCE),
        Triple(stringResource(R.string.settings_section_mapping), Icons.Outlined.Map, SECTION_MAPPING),
        Triple(stringResource(R.string.settings_section_preferences), Icons.Outlined.Tune, SECTION_PREFERENCES),
        Triple(stringResource(R.string.settings_section_background), Icons.Outlined.Notifications, SECTION_BACKGROUND),
        Triple(stringResource(R.string.settings_section_system), Icons.Outlined.Settings, SECTION_SYSTEM),
        Triple(stringResource(R.string.settings_section_database), Icons.Outlined.Storage, SECTION_DATABASE)
    )
    val sectionRootSnapshot = sectionRootPositions.toMap()
    val sectionBoundsSnapshot = sectionBounds.toMap()
    val databaseBounds = sectionBoundsSnapshot[SECTION_DATABASE] ?: SettingsSectionBounds()
    val sectionAnchorModifiers = sectionBringIntoViewRequesters.mapIndexed { index, requester ->
        Modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInRoot().y
                sectionRootPositions[index] = top
                sectionBounds[index] = SettingsSectionBounds(top = top, height = coordinates.size.height)
            }
    }
    // Ancres fines de la section base de données : réutilisées par l'affichage par sections
    // (téléphone) pour que les notifications gardent leur défilement précis.
    val offlineMapsAnchorModifier = Modifier
        .bringIntoViewRequester(offlineMapsBringIntoViewRequester)
        .onGloballyPositioned { coordinates ->
            offlineMapsBounds = SettingsSectionBounds(
                top = coordinates.positionInRoot().y,
                height = coordinates.size.height
            )
        }
    val databaseCardAnchorModifiers = DATABASE_CARD_ANCHORS.associateWith { anchor ->
        Modifier
            .bringIntoViewRequester(databaseCardRequesters.getValue(anchor))
            .onGloballyPositioned { coordinates ->
                databaseCardBounds[anchor] = SettingsSectionBounds(
                    top = coordinates.positionInRoot().y,
                    height = coordinates.size.height
                )
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
                        onClick = { if (openAction != null) searchOpen(section, openAction) else searchScrollTo(section) }
                    )
                )
            }

            // Entrée qui n'appartient à aucune section (elle vit sur l'accueil des réglages) :
            // on l'étiquette simplement « Paramètres » au lieu d'une section trompeuse.
            fun directEntry(title: String, keywords: String, icon: ImageVector, action: () -> Unit) {
                add(
                    SettingsSearchEntry(
                        title = title,
                        keywords = keywords,
                        sectionLabel = context.getString(R.string.nav_settings),
                        icon = icon,
                        onClick = { searchOpen(null, action) }
                    )
                )
            }

            // --- Apparence ---
            entry(context.getString(R.string.appearance_theme_title), "theme thème clair sombre systeme dark light mode nuit jour couleur apparence", SECTION_APPEARANCE)
            entry(context.getString(R.string.appstrings_color_palette_title), "palette couleur color accent teinte material", SECTION_APPEARANCE) { showColorPalettePage = true }
            entry(context.getString(R.string.appearance_oled_title), "oled noir pur black amoled sombre economie", SECTION_APPEARANCE)
            entry(context.getString(R.string.appearance_one_ui_title), "one ui oneui samsung style interface bulle", SECTION_APPEARANCE)
            entry(context.getString(R.string.appearance_scroll_blur_title), "flou blur defilement transparence effet", SECTION_APPEARANCE)
            entry(context.getString(R.string.appearance_app_icon_title), "icone icon launcher logo application accueil", SECTION_APPEARANCE) { showIconSheet = true }
            entry(context.getString(R.string.appearance_in_app_logo_title), "logo dessin drawing application interne", SECTION_APPEARANCE) { showLogoDrawingSheet = true }
            entry(context.getString(R.string.appearance_menu_size_title), "taille menu size police texte echelle zoom", SECTION_APPEARANCE)
            if (isWideScreen) {
                entry(context.getString(R.string.settings_navigation_mode_title), "navigation mode defilement pages scroll mise en page", SECTION_APPEARANCE)
                entry(context.getString(R.string.settings_display_style_title), "affichage display plein ecran split divise tablette", SECTION_APPEARANCE)
            }

            // --- Cartographie ---
            entry(context.getString(R.string.settings_section_mapping), "carte map fond fournisseur ign osm maplibre topo provider tuiles", SECTION_MAPPING)
            entry(context.getString(R.string.mapping_style_title), "style carte clair sombre satellite couleur", SECTION_MAPPING)
            entry(context.getString(R.string.appstrings_offline_maps_title), "cartes hors ligne offline maps telechargement tuiles mapsforge", SECTION_MAPPING)

            // --- Préférences ---
            entry(context.getString(R.string.settings_default_operator), "operateur operator orange sfr free bouygues sim defaut", SECTION_PREFERENCES) { showOperatorSheet = true }
            entry(context.getString(R.string.settings_app_language), "langue language francais anglais traduction locale", SECTION_PREFERENCES) { showLanguageSheet = true }
            entry(context.getString(R.string.settings_units_title), "unites units distance vitesse metre km mesure imperial", SECTION_PREFERENCES) { showUnitSheet = true }
            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.PAGES_CUSTOMIZATION)) {
                entry(context.getString(R.string.settings_pages_customization_title), "pages personnalisation accueil carte boussole site support proximite statistiques blocs reorganiser masquer astuces bulle rappel appui long", SECTION_PREFERENCES) { showPagesCustomizationSheet = true }
            }
            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.EXTERNAL_LINKS_SETTINGS)) {
                entry(context.getString(R.string.settings_external_links_title), "liens externes links cartoradio sites web", SECTION_PREFERENCES) { showExternalLinksSheet = true }
            }
            if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
                entry(context.getString(R.string.settings_default_share_content_title), "partage share image contenu carte antenne support", SECTION_PREFERENCES) { showShareSelectorSheet = true }
            }

            // --- Notifications et arrière-plan ---
            entry(context.getString(R.string.appstrings_app_notifications_title), "notifications app autoriser permission activer couper toutes", SECTION_BACKGROUND)
            entry(context.getString(R.string.appstrings_update_notif_setting_title), "notification mise a jour update base donnees alerte", SECTION_BACKGROUND)
            entry(context.getString(R.string.appstrings_live_notification_title), "notification live suivi temps reel antenne direct", SECTION_BACKGROUND)
            entry(context.getString(R.string.appstrings_live_location_accuracy_title), "precision gps position live exactitude", SECTION_BACKGROUND)
            entry(context.getString(R.string.appstrings_low_power_title), "faible consommation economie batterie eco energie basse performance low power mode", SECTION_BACKGROUND)
            entry(context.getString(R.string.appstrings_widget_refresh_title), "widget frequence rafraichissement synchronisation accueil", SECTION_BACKGROUND)

            // --- Système ---
            entry(context.getString(R.string.appstrings_manage_permissions), "permissions autorisations systeme application acces", SECTION_SYSTEM)
            entry(context.getString(R.string.appstrings_bg_location_perm_title), "position arriere plan background localisation permission autorisation", SECTION_SYSTEM)
            entry(context.getString(R.string.appstrings_diagnostic_api_dialog_title), "serveur server miroir mirror principal secours bascule api hote host reseau geotower cajejuma", SECTION_SYSTEM)
            entry(context.getString(R.string.appstrings_diagnostic_title), "diagnostic logs debogage info journal probleme", SECTION_SYSTEM) { navController.navigate("diagnostic") }

            // --- Base de données ---
            entry(context.getString(R.string.settings_section_database), "base de donnees database telechargement anfr support antenne", SECTION_DATABASE)
            entry(context.getString(R.string.appstrings_radio_data_title), "radio donnees frequences base anfr", SECTION_DATABASE)
            // Pannes : la carte de la section télécharge le fichier serveur, ou lance la génération
            // locale selon le niveau de traitement local — les deux vocabulaires mènent donc ici.
            entry(context.getString(R.string.outage_download_title), "pannes sites hs telecharger actualiser generer copie hors ligne serveur coupures", SECTION_DATABASE)
            entry(context.getString(R.string.local_mode_settings_title), "traitement local autonomie serveur hors ligne generation", SECTION_DATABASE) { navController.navigate("local_mode") }
            // Les pannes n'ont plus de page dédiée : leurs réglages vivent dans « Traitement local »
            // (niveau ≥ 1). On garde l'entrée de recherche pour que les mots-clés continuent de mener au bon endroit.
            entry(context.getString(R.string.outage_source_settings_title), "coupures pannes source sites hs operateurs frequence arriere plan", SECTION_DATABASE) { navController.navigate("local_mode") }

            // --- Entrées directes (hors section) ---
            // Le mode simplifié vit en tête des réglages : le résultat referme la recherche (et la
            // section ouverte) pour ramener sur la page où sa carte est visible.
            if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIMPLE_MODE_ENABLED)) {
                directEntry(
                    context.getString(R.string.settings_simple_mode_title),
                    "mode simplifie simple carte demarrage tiroir menu lateral debutant epure",
                    Icons.Outlined.Tune
                ) { openSection(null) }
            }
            directEntry(
                context.getString(R.string.photos_favorites_title),
                "photos favorites galerie images preferees",
                Icons.Default.PhotoLibrary
            ) { navController.navigate("photos_favorites") }
            directEntry(
                context.getString(R.string.appstrings_upload_history_title),
                "historique envoi photos signalquest upload",
                Icons.Default.History
            ) { navController.navigate("photo_upload_history") }
            directEntry(
                context.getString(R.string.share_history_title),
                "historique partages partage export pdf sites supports genere",
                Icons.Default.History
            ) { navController.navigate("share_history") }
            directEntry(
                context.getString(R.string.preference_profiles_title),
                "profil profils profiles preferences sauvegarde configuration",
                Icons.Outlined.Bookmarks
            ) { showPreferenceProfilesSheet = true }
        }
    }

    if (isWideScreen && navMode == 0) {
        LaunchedEffect(
            scrollState.value,
            scrollState.maxValue,
            sectionRootSnapshot,
            scrollViewportTop
        ) {
            if (sectionRootSnapshot.size >= menuItems.size) {
                val activationLine = scrollViewportTop + 24f
                val databaseSectionIndex = menuItems.last().third
                val regularSectionIndices = menuItems.dropLast(1).map { it.third }.toSet()
                // La dernière section est difficile à « activer » au défilement : on l'autorise
                // quand on y arrive par un lien profond.
                val allowDatabaseSelectionBeforeEnd =
                    initialSection == "database" || initialDatabaseCardAnchor != null
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
        openedSection,
        navMode,
        isWideScreen
    ) {
        val isDatabasePageOpen = useSectionsHome && openedSection == SECTION_DATABASE
        if (isDatabasePageOpen || isDisplayedAsMuchAsPossible(databaseBounds)) {
            DownloadNotificationCenter.clearDatabaseSectionNotifications(context)
        }
    }

    Scaffold(
        containerColor = mainBgColor,
        // L'écran est déjà posé dans un `Box(padding(innerPadding))` du NavHost, donc DÉJÀ décalé
        // sous la barre d'état. Sans ça, le Scaffold sans barre supérieure (grand écran) rajoute
        // l'inset une seconde fois : la hauteur de la barre d'état — celle de l'encoche sur un
        // Fold, ~40 dp — était comptée deux fois. Avec une barre supérieure (téléphone), le padding
        // vaut la hauteur de la barre et ne change pas.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!isWideScreen) {
                // On remet la vraie barre supérieure pour les téléphones !
                if (showColorPalettePage) {
                    fr.geotower.ui.components.ColorPaletteTopBar(onBack = { showColorPalettePage = false })
                } else {
                    val phoneOpenSection = openedSection?.takeIf { usePhoneSections }
                    GeoTowerBackTopBar(
                        title = phoneOpenSection?.let { menuItems[it].first }
                            ?: stringResource(R.string.nav_settings),
                        // Même échelle que le retour système : recherche, puis section, puis sortie.
                        onBack = {
                            when {
                                isSearchActive -> settingsSearchQuery = ""
                                phoneOpenSection != null -> openSection(null)
                                else -> safeBackNavigation.navigateBack()
                            }
                        },
                        backgroundColor = MaterialTheme.colorScheme.background,
                        actions = {
                            // Bascule « un bouton par section » ↔ « tout sur une page ».
                            IconButton(
                                onClick = {
                                    safeClick("settings_sections_mode") {
                                        setSettingsSectionsMode(!settingsSectionsMode)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (settingsSectionsMode) {
                                        Icons.Outlined.ViewAgenda
                                    } else {
                                        Icons.Outlined.Dashboard
                                    },
                                    contentDescription = stringResource(R.string.settings_navigation_mode_title),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
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
                // La barre latérale doit défiler : elle porte 6 sections + 6 entrées directes + la
                // version, ce qui dépasse la hauteur d'un Fold dès l'échelle 100 %. Sans défilement
                // le `Spacer(weight(1f))` du bas n'a plus rien à distribuer et les dernières entrées
                // (profils, À propos, réinitialisation) étaient purement et simplement rognées.
                val sidebarScrollState = rememberScrollState()
                Row(modifier = Modifier.width(width).fillMaxHeight().background(mainBgColor)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // 🚨 CORRECTION 2 : Marge pour les boutons de navigation
                            .navigationBarsPadding()
                            // `fillMaxHeight` + `navigationBarsPadding` fixent la hauteur MINIMALE
                            // transmise au contenu ; le défilement ne relâche que le maximum. Le
                            // `Spacer(weight(1f))` continue donc de plaquer la version en bas quand
                            // il reste de la place, et la colonne défile quand il n'y en a plus.
                            .verticalScroll(sidebarScrollState)
                            // Même resserrage que l'en-tête du volet de contenu, pour que la ligne
                            // retour/menu et le titre restent sur le même axe.
                            .padding(top = sizing.spacing(4.dp), bottom = sizing.spacing(16.dp))
                    ) {
                        // ✅ RETOUR DU ROW AVEC LES DEUX BOUTONS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                // Comme sur téléphone : la recherche se referme avant de quitter.
                                onClick = {
                                    if (isSearchActive) settingsSearchQuery = "" else safeBackNavigation.navigateBack()
                                },
                                enabled = isSearchActive || !safeBackNavigation.isLocked,
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
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        // Mode « pages » : première entrée = l'accueil des sections, sur lequel on
                        // arrive en ouvrant les réglages. Sans elle, la barre latérale n'offrirait
                        // aucun moyen d'y revenir une fois une section ouverte.
                        if (useWideSections) {
                            NavigationMenuItem(
                                stringResource(R.string.settings_sections_home_title),
                                Icons.Outlined.Dashboard,
                                openedSection == null,
                                isDark
                            ) {
                                openSection(null)
                            }
                            Spacer(Modifier.height(sizing.spacing(8.dp)))
                        }
                        menuItems.forEach { (title, icon, index) ->
                            val isSelected = if (useWideSections) openedSection == index else activeSectionIndex == index
                            NavigationMenuItem(title, icon, isSelected, isDark) {
                                activeSectionIndex = index
                                if (navMode == 0) {
                                    scope.launch {
                                        sectionBringIntoViewRequesters[index].bringIntoView()
                                        kotlinx.coroutines.delay(80)
                                        alignAnchorToViewportTop(sectionRootPositions[index])
                                    }
                                } else {
                                    // Mode « pages » : la nouvelle section remplace l'ancienne (ou
                                    // l'accueil), on repart de son début (sinon on hérite du
                                    // défilement précédent).
                                    openSection(index)
                                }
                            }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        // Entrées directes : une galerie et un méta-réglage, pas des sections.
                        NavigationMenuItem(stringResource(R.string.photos_favorites_title), Icons.Default.PhotoLibrary, false, isDark) {
                            safeClick { navController.navigate("photos_favorites") }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        NavigationMenuItem(stringResource(R.string.appstrings_upload_history_title), Icons.Default.History, false, isDark) {
                            safeClick { navController.navigate("photo_upload_history") }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        NavigationMenuItem(stringResource(R.string.share_history_title), Icons.Default.Share, false, isDark) {
                            safeClick { navController.navigate("share_history") }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
                        NavigationMenuItem(stringResource(R.string.preference_profiles_title), Icons.Outlined.Bookmarks, false, isDark) {
                            safeClick { showPreferenceProfilesSheet = true }
                        }
                        Spacer(Modifier.height(sizing.spacing(8.dp)))
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
                        // Section ouverte depuis l'accueil : l'en-tête prend son nom et gagne une
                        // flèche de retour, exactement comme la barre du haut d'un téléphone.
                        val wideOpenSection = openedSection?.takeIf { useWideSections }
                        // Marges volontairement serrées : la bande de titre est déjà repoussée sous
                        // la barre d'état (et sous l'encoche du Fold, plus haute), et sa hauteur est
                        // imposée par les boutons. Chaque dp rendu ici est du contenu visible.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(4.dp), bottom = sizing.spacing(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = !isSidebarVisible, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                                IconButton(onClick = onToggleSidebar, modifier = Modifier.padding(start = sizing.spacing(8.dp))) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            AnimatedVisibility(visible = wideOpenSection != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                                IconButton(onClick = { openSection(null) }, modifier = Modifier.padding(start = sizing.spacing(8.dp))) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_sections_home_title),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            // Contrepoids du bouton de bascule posé à droite : sans lui le titre
                            // serait décentré de la moitié de sa largeur.
                            Spacer(Modifier.width(sizing.component(56.dp)))
                            Text(
                                text = wideOpenSection?.let { menuItems[it].first } ?: stringResource(R.string.nav_settings),
                                style = sizing.textStyle(MaterialTheme.typography.headlineSmall),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            AnimatedVisibility(visible = !isSidebarVisible) { Spacer(Modifier.width(sizing.component(56.dp))) }
                            AnimatedVisibility(visible = wideOpenSection != null) { Spacer(Modifier.width(sizing.component(56.dp))) }
                            // Bascule « accueil par sections » ↔ « tout sur une page », pendant
                            // exact du bouton de la barre du haut des téléphones.
                            IconButton(
                                onClick = {
                                    safeClick("settings_nav_mode") {
                                        setNavMode(if (useWideSections) 0 else 1)
                                    }
                                },
                                modifier = Modifier.padding(end = sizing.spacing(8.dp))
                            ) {
                                Icon(
                                    imageVector = if (useWideSections) {
                                        Icons.Outlined.ViewAgenda
                                    } else {
                                        Icons.Outlined.Dashboard
                                    },
                                    contentDescription = stringResource(R.string.settings_navigation_mode_title),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // --- CONTENU DÉFILANT DES PARAMÈTRES ---
                    Box(modifier = Modifier.fillMaxSize()) {
                    // Modes d'affichage, décidés ici puis réutilisés tels quels par les branches
                    // plus bas : la condition du tirage vers le bas doit suivre EXACTEMENT ce qui
                    // est réellement rendu, sinon le geste survit à un changement de mode.
                    val isSectionsHome = useSectionsHome && openedSection == null
                    val isAllInOnePage = !usePhoneSections && (navMode == 0 || !isExpanded)
                    val openSectionIndex =
                        if (useSectionsHome) openedSection ?: SECTION_APPEARANCE else activeSectionIndex
                    // Le tirage n'a de sens que si la section « Base de données » occupe la page à
                    // elle seule : ailleurs elle n'est pas en haut du défilement, et c'est le bouton
                    // posé sur son titre qui l'actualise.
                    val databaseSectionAlone = settingsSearchQuery.isBlank() && !isSectionsHome &&
                        !isAllInOnePage && openSectionIndex == SECTION_DATABASE
                    GeoTowerPullToRefreshBox(
                        isRefreshing = databaseSectionAlone && databaseRefreshVisible,
                        onRefresh = { databaseRefreshState.refresh() },
                        enabled = databaseSectionAlone,
                        modifier = Modifier.fillMaxSize()
                    ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                scrollViewportTop = coordinates.positionInRoot().y
                                scrollViewportBottom = scrollViewportTop + coordinates.size.height
                            }
                            // Le défilement vaut pour TOUS les modes : en « pages » sur grand écran
                            // (Fold déplié) une section peut dépasser la hauteur d'écran, et sans
                            // défilement le bas était simplement inatteignable.
                            .geoTowerFadingEdge(scrollState)
                            .pageScrollbar(PageScrollPrefs.SETTINGS, scrollState)
                            .verticalScroll(scrollState)
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

                            // Le mode simplifié change toute la navigation de l'app : il est en
                            // tête des réglages, au-dessus des sections, et non enterré dans
                            // « Apparence ». Masqué pendant une recherche (les résultats prennent
                            // la page) et sur une section ouverte (il n'appartient à aucune).
                            if (
                                settingsSearchQuery.isBlank() &&
                                !isSectionOpen &&
                                RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIMPLE_MODE_ENABLED)
                            ) {
                                val simpleModeEnabled by AppConfig.simpleMode
                                PreferenceSwitchCard(
                                    title = stringResource(R.string.settings_simple_mode_title),
                                    desc = stringResource(R.string.settings_simple_mode_desc),
                                    checked = simpleModeEnabled,
                                    onCheckedChange = { enabled ->
                                        AppConfig.setSimpleMode(context, enabled)
                                        // Le mode change la RACINE de la navigation, et celle-ci est
                                        // figée au lancement de l'app : sans reconstruction, le
                                        // retour depuis les réglages retombait sur l'accueil, page
                                        // qui n'existe plus en mode simplifié. On repose donc la
                                        // bonne racine, puis les réglages par-dessus pour laisser
                                        // l'utilisateur là où il est.
                                        navController.navigate(AppConfig.homeRoute()) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                        navController.navigate("settings")
                                    },
                                    shape = cardShape,
                                    border = cardBorder,
                                    bubbleColor = bubbleBaseColor,
                                    useOneUi = useOneUi
                                )
                                Spacer(Modifier.height(sizing.spacing(20.dp)))
                            }

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
                            if (isSectionsHome) {
                                // Accueil des réglages : un bouton par section. Le raccourci
                                // « tout sur une page » vise le réglage du mode courant — la
                                // bascule par sections sur téléphone, le mode de navigation
                                // (nav_mode) sur grand écran.
                                SettingsSectionsHome(
                                    sections = menuItems,
                                    onSectionClick = { openSection(it) },
                                    onShowAll = { if (useWideSections) setNavMode(0) else setSettingsSectionsMode(false) },
                                    onPhotosFavorites = { navController.navigate("photos_favorites") },
                                    onPhotoUploadHistory = { navController.navigate("photo_upload_history") },
                                    onShareHistory = { navController.navigate("share_history") },
                                    onPreferenceProfiles = { showPreferenceProfilesSheet = true },
                                    shape = cardShape,
                                    border = cardBorder,
                                    bubbleColor = bubbleBaseColor,
                                    useOneUi = useOneUi,
                                    safeClick = safeClick
                                )
                            } else if (isAllInOnePage) {
                                AllSettingsContent(
                                    isWide = isExpanded,
                                    nav = navMode,
                                    onNav = { setNavMode(it) },
                                    theme = themeMode,
                                    onTheme = { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() },
                                    oled = isOledMode,
                                    onOled = { isOledMode = it; prefs.edit().putBoolean("is_oled_mode", it).apply() },
                                    oneUi = useOneUi,
                                    onOneUi = ::updateOneUi,
                                    blur = isBlurEnabled,
                                    onBlur = { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() },
                                    logo = logoResId,
                                    onIcon = { showIconSheet = true },
                                    onLogoDrawing = { showLogoDrawingSheet = true },
                                    op = defaultOperator,
                                    onOp = { showOperatorSheet = true },
                                    lang = appLanguage,
                                    onLang = { showLanguageSheet = true },
                                    onUnitSettings = { showUnitSheet = true },
                                    onPages = { showPagesCustomizationSheet = true },
                                    onExternalLinks = { showExternalLinksSheet = true },
                                    onSharePrefs = { showShareSelectorSheet = true },
                                    onPreferenceProfiles = { showPreferenceProfilesSheet = true },
                                    map = mapProvider,
                                    onMap = { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() },
                                    ign = ignStyle,
                                    onIgn = { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() },
                                    ctx = context,
                                    shape = cardShape,
                                    border = cardBorder,
                                    bubbleColor = bubbleBaseColor,
                                    useOneUi = useOneUi,
                                    safeClick = safeClick,
                                    onColorPaletteClick = { showColorPalettePage = true },
                                    repository = repository,
                                    scope = scope,
                                    appearanceSectionModifier = sectionAnchorModifiers[SECTION_APPEARANCE],
                                    mappingSectionModifier = sectionAnchorModifiers[SECTION_MAPPING],
                                    preferencesSectionModifier = sectionAnchorModifiers[SECTION_PREFERENCES],
                                    backgroundSectionModifier = sectionAnchorModifiers[SECTION_BACKGROUND],
                                    systemSectionModifier = sectionAnchorModifiers[SECTION_SYSTEM],
                                    databaseSectionModifier = sectionAnchorModifiers[SECTION_DATABASE],
                                    offlineMapsSectionModifier = offlineMapsAnchorModifier,
                                    viewportTop = scrollViewportTop,
                                    viewportBottom = scrollViewportBottom,
                                    scrollValue = scrollState.value,
                                    scrollMaxValue = scrollState.maxValue,
                                    targetMapFilename = offlineMapsTargetFilename,
                                    onTargetMapPositioned = { top, height -> offlineMapsTargetBounds = SettingsSectionBounds(top = top, height = height) },
                                    onOpenDiagnostic = { navController.navigate("diagnostic") },
                                    onPhotosFavorites = { navController.navigate("photos_favorites") },
                                    onPhotoUploadHistory = { navController.navigate("photo_upload_history") },
                                    onShareHistory = { navController.navigate("share_history") },
                                    onLocalMode = { navController.navigate("local_mode") },
                                    databaseCardModifiers = databaseCardAnchorModifiers,
                                    databaseRefreshState = databaseRefreshState
                                )
                                run {
                                    // Passage à l'accueil par sections : pendant du bouton de la
                                    // barre du haut sur téléphone, du mode « pages » sur grand
                                    // écran (sinon le réglage n'est atteignable que depuis la
                                    // carte « Mode de navigation » d'Apparence).
                                    Spacer(Modifier.height(sizing.spacing(8.dp)))
                                    TextButton(
                                        onClick = { if (isExpanded) setNavMode(1) else setSettingsSectionsMode(true) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Dashboard,
                                            contentDescription = null,
                                            modifier = Modifier.size(sizing.component(20.dp))
                                        )
                                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                                        Text(
                                            text = stringResource(R.string.settings_navigation_pages_desc),
                                            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                // Une seule section : mode « pages » des grands écrans, ou section
                                // ouverte depuis l'accueil par sections d'un téléphone.
                                when (openSectionIndex) {
                                    SECTION_APPEARANCE -> SectionApparence(
                                        themeMode,
                                        { themeMode = it; prefs.edit().putInt("theme_mode", it).apply() },
                                        isOledMode,
                                        { isOledMode = it; prefs.edit().putBoolean("is_oled_mode", it).apply() },
                                        useOneUi,
                                        ::updateOneUi,
                                        isBlurEnabled,
                                        { isBlurEnabled = it; prefs.edit().putBoolean("is_blur_enabled", it).apply() },
                                        logoResId,
                                        { showIconSheet = true },
                                        { showLogoDrawingSheet = true },
                                        cardShape,
                                        cardBorder,
                                        bubbleBaseColor,
                                        useOneUi,
                                        safeClick,
                                        { showColorPalettePage = true },
                                        isWide = isExpanded,
                                        nav = navMode,
                                        onNav = { setNavMode(it) }
                                    )
                                    SECTION_MAPPING -> SectionCartographie(
                                        mapProvider,
                                        { mapProvider = it; prefs.edit().putInt("map_provider", it).apply() },
                                        ignStyle,
                                        { ignStyle = it; prefs.edit().putInt("ign_style", it).apply() },
                                        cardShape,
                                        cardBorder,
                                        bubbleBaseColor,
                                        useOneUi,
                                        safeClick,
                                        offlineMapsModifier = offlineMapsAnchorModifier,
                                        viewportTop = scrollViewportTop,
                                        viewportBottom = scrollViewportBottom,
                                        scrollValue = scrollState.value,
                                        scrollMaxValue = scrollState.maxValue,
                                        targetMapFilename = offlineMapsTargetFilename,
                                        onTargetMapPositioned = { top, height ->
                                            offlineMapsTargetBounds = SettingsSectionBounds(top = top, height = height)
                                        }
                                    )
                                    SECTION_PREFERENCES -> SectionPreferences(
                                        defaultOperator,
                                        { showOperatorSheet = true },
                                        appLanguage,
                                        { showLanguageSheet = true },
                                        { showUnitSheet = true },
                                        { showPagesCustomizationSheet = true },
                                        { showExternalLinksSheet = true },
                                        { showShareSelectorSheet = true },
                                        cardShape,
                                        cardBorder,
                                        bubbleBaseColor,
                                        useOneUi,
                                        safeClick
                                    )
                                    SECTION_BACKGROUND -> SectionNotifications(
                                        defaultOperator,
                                        cardShape,
                                        cardBorder,
                                        bubbleBaseColor,
                                        useOneUi,
                                        safeClick
                                    )
                                    SECTION_SYSTEM -> SectionSysteme(
                                        context,
                                        cardShape,
                                        border = cardBorder,
                                        bubbleColor = bubbleBaseColor,
                                        useOneUi = useOneUi,
                                        safeClick = safeClick,
                                        onOpenDiagnostic = { navController.navigate("diagnostic") }
                                    )
                                    SECTION_DATABASE -> SectionDatabase(
                                        cardShape,
                                        bubbleBaseColor,
                                        useOneUi,
                                        repository,
                                        scope,
                                        context,
                                        modifier = sectionAnchorModifiers[SECTION_DATABASE],
                                        onLocalMode = { navController.navigate("local_mode") },
                                        safeClick = safeClick,
                                        databaseCardModifiers = databaseCardAnchorModifiers,
                                        refreshState = databaseRefreshState
                                        // Pas de bouton ici : la section est seule sur la page,
                                        // le tirage vers le bas la couvre.
                                    )
                                }
                            }
                            }
                            Spacer(Modifier.height(sizing.spacing(48.dp)))
                        }
                    }
                    }
                    // Le contenu défile dans tous les modes : les aides au défilement suivent.
                    PageScrollEdgeButtons(PageScrollPrefs.SETTINGS, scrollState)
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
                    if (AppConfig.enableLiveTracking.value) {
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

        if (showMapFiltersDefaultsSheet) {
            MapFiltersDefaultsSheet(
                onDismiss = { showMapFiltersDefaultsSheet = false },
                sheetState = sheetState,
                onBack = { safeClick { showMapFiltersDefaultsSheet = false; showMapSettingsSheet = true } }
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
                onFiltersClick = { safeClick { showMapSettingsSheet = false; showMapFiltersDefaultsSheet = true } },
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

        // En mode simplifié, la fiche site autonome n'est plus atteignable (site_detail est réécrite
        // vers le pylône) : régler ses clés ne changerait rien à l'écran. Ce sont les sections
        // opérateur dépliées qu'il faut piloter, et elles ont leurs propres clés.
        if (showSiteSettingsSheet && AppConfig.simpleModeActive()) {
            EmbeddedSiteBlocksSettingsSheet(
                onDismiss = { showSiteSettingsSheet = false },
                onBack = { safeClick { showSiteSettingsSheet = false; showPagesCustomizationSheet = true } },
                sheetState = sheetState,
                useOneUi = useOneUi,
                bubbleColor = bubbleBaseColor
            )
        } else if (showSiteSettingsSheet) {
            SiteSettingsSheet(
                siteOrder = pageSiteOrder, onOrderChange = { pageSiteOrder = it; prefs.edit().putString(SitePagePrefs.ORDER, it.joinToString(",")).apply() },
                showOperator = pageSiteOperator, onOperatorChange = { pageSiteOperator = it; prefs.edit().putBoolean(SitePagePrefs.operator.key, it).apply() },
                showBearingHeight = pageSiteBearingHeight, onBearingHeightChange = { pageSiteBearingHeight = it; prefs.edit().putBoolean(SitePagePrefs.bearingHeight.key, it).apply() },
                showMap = pageSiteMap, onMapChange = { pageSiteMap = it; prefs.edit().putBoolean(SitePagePrefs.map.key, it).apply() },
                showSupportDetails = pageSiteSupportDetails, onSupportDetailsChange = { pageSiteSupportDetails = it; prefs.edit().putBoolean(SitePagePrefs.supportDetails.key, it).apply() },
                showPhotos = AppConfig.siteShowPhotos.value, onPhotosChange = ::updateSharedPhotosVisibility,
                showPanelHeights = pageSitePanelHeights, onPanelHeightsChange = { pageSitePanelHeights = it; prefs.edit().putBoolean(SitePagePrefs.panelHeights.key, it).apply() },
                showIds = pageSiteIds, onIdsChange = { pageSiteIds = it; prefs.edit().putBoolean(SitePagePrefs.ids.key, it).apply() },
                showNetworkIds = pageSiteNetworkIds, onNetworkIdsChange = { pageSiteNetworkIds = it; prefs.edit().putBoolean(SitePagePrefs.networkIds.key, it).apply() },
                showOpenMap = pageSiteOpenMap, onOpenMapChange = { pageSiteOpenMap = it; prefs.edit().putBoolean(SitePagePrefs.openMap.key, it).apply() },
                showElevationProfile = pageSiteElevationProfile, onElevationProfileChange = { pageSiteElevationProfile = it; prefs.edit().putBoolean(SitePagePrefs.elevationProfile.key, it).apply() },
                showThroughputCalculator = pageSiteThroughputCalculator, onThroughputCalculatorChange = { pageSiteThroughputCalculator = it; prefs.edit().putBoolean(SitePagePrefs.throughputCalculator.key, it).apply() },
                showTheoreticalCoverage = pageSiteTheoreticalCoverage, onTheoreticalCoverageChange = { pageSiteTheoreticalCoverage = it; prefs.edit().putBoolean(SitePagePrefs.theoreticalCoverage.key, it).apply() },
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
    backgroundSectionModifier: Modifier = Modifier,
    systemSectionModifier: Modifier = Modifier,
    databaseSectionModifier: Modifier = Modifier,
    offlineMapsSectionModifier: Modifier = Modifier,
    viewportTop: Float = Float.NaN,
    viewportBottom: Float = Float.NaN,
    scrollValue: Int = 0,
    scrollMaxValue: Int = 0,
    targetMapFilename: String? = null,
    onTargetMapPositioned: (Float, Int) -> Unit = { _, _ -> },
    onOpenDiagnostic: () -> Unit = {},
    onPhotosFavorites: () -> Unit = {},
    onPhotoUploadHistory: () -> Unit = {},
    onShareHistory: () -> Unit = {},
    onLocalMode: () -> Unit = {},
    databaseCardModifiers: Map<String, Modifier> = emptyMap(),
    databaseRefreshState: DatabaseRefreshState? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(modifier = appearanceSectionModifier.fillMaxWidth()) {
        SectionApparence(theme, onTheme, oled, onOled, oneUi, onOneUi, blur, onBlur, logo, onIcon, onLogoDrawing, shape, border, bubbleColor, useOneUi, safeClick, onColorPaletteClick, isWide = isWide, nav = nav, onNav = onNav)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = mappingSectionModifier.fillMaxWidth()) {
        SectionCartographie(
            map, onMap, ign, onIgn, shape, border, bubbleColor, useOneUi, safeClick,
            offlineMapsModifier = offlineMapsSectionModifier,
            viewportTop = viewportTop,
            viewportBottom = viewportBottom,
            scrollValue = scrollValue,
            scrollMaxValue = scrollMaxValue,
            targetMapFilename = targetMapFilename,
            onTargetMapPositioned = onTargetMapPositioned
        )
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = preferencesSectionModifier.fillMaxWidth()) {
        SectionPreferences(op, onOp, lang, onLang, onUnitSettings, onPages, onExternalLinks, onSharePrefs, shape, border, bubbleColor, useOneUi, safeClick)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = backgroundSectionModifier.fillMaxWidth()) {
        SectionNotifications(op, shape, border, bubbleColor, useOneUi, safeClick)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    Column(modifier = systemSectionModifier.fillMaxWidth()) {
        SectionSysteme(ctx, shape, border, bubbleColor, useOneUi, safeClick, onOpenDiagnostic)
    }
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    SectionDatabase(
        shape,
        bubbleColor,
        useOneUi,
        repository,
        scope,
        ctx,
        databaseSectionModifier,
        onLocalMode = onLocalMode,
        safeClick = safeClick,
        databaseCardModifiers = databaseCardModifiers,
        refreshState = databaseRefreshState,
        // Page unique : la section est noyée au milieu du défilement, seul un bouton peut l'actualiser.
        showRefreshButton = true
    )
    Spacer(Modifier.height(sizing.spacing(32.dp)))
    SettingsDirectEntries(
        onPhotosFavorites = onPhotosFavorites,
        onPhotoUploadHistory = onPhotoUploadHistory,
        onShareHistory = onShareHistory,
        onPreferenceProfiles = onPreferenceProfiles,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi,
        safeClick = safeClick
    )
    // Les grands écrans ont déjà la réinitialisation dans leur barre latérale.
    if (!isWide) {
        SettingsResetButton(shape = shape, modifier = Modifier.padding(top = sizing.spacing(24.dp)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionApparence(
    theme: Int, onTheme: (Int) -> Unit, oled: Boolean, onOled: (Boolean) -> Unit,
    oneUi: Boolean, onOneUi: (Boolean) -> Unit, blur: Boolean, onBlur: (Boolean) -> Unit,
    logo: Int, onIcon: () -> Unit, onLogoDrawing: () -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick,
    onColorPaletteClick: () -> Unit,
    // Mise en page des réglages : réservée aux grands écrans. Les téléphones basculent
    // « par sections » / « page unique » depuis la barre du haut.
    isWide: Boolean = false,
    nav: Int = 0,
    onNav: (Int) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, android.content.Context.MODE_PRIVATE)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val uiScalePercent by AppConfig.uiScalePercent
    val logoDrawingChoice by AppConfig.appLogoDrawingChoice
    val isDark = LocalGeoTowerUiStyle.current.isDark
    val isOledMode by AppConfig.isOledMode
    val sheetBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val logoDrawingRes = AppLogoDrawingResources.resolve(logoDrawingChoice, logo, isDark)
    var displayStyle by remember { mutableIntStateOf(prefs.getInt("display_style", 0)) }
    var showDisplayStylesSheet by remember { mutableStateOf(false) }

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

    // Le mode simplifié n'est plus ici : il change toute la navigation de l'app, il est donc
    // présenté en tête des réglages (cf. SimpleModeSettingsCard), au-dessus des sections.

    // --- MISE EN PAGE DES RÉGLAGES (grands écrans) ---
    if (isWide) {
        Spacer(Modifier.height(sizing.spacing(12.dp)))
        var showModeSheet by remember { mutableStateOf(false) }
        val cardBg = if (useOneUi) bubbleColor else Color.Transparent
        Surface(onClick = { showModeSheet = true }, modifier = Modifier.fillMaxWidth(), shape = shape, border = border, color = cardBg) {
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

    // Style d'affichage plein écran / fractionné : uniquement sur grand écran réel.
    if (minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600) {
        Spacer(Modifier.height(sizing.spacing(12.dp)))
        val cardBg = if (useOneUi) bubbleColor else Color.Transparent
        Surface(onClick = { safeClick("display_styles_sheet") { showDisplayStylesSheet = true } }, modifier = Modifier.fillMaxWidth(), shape = shape, border = border, color = cardBg) {
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
}

@Composable
fun SectionCartographie(
    map: Int, onMap: (Int) -> Unit, ign: Int, onIgn: (Int) -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick,
    offlineMapsModifier: Modifier = Modifier,
    viewportTop: Float = Float.NaN,
    viewportBottom: Float = Float.NaN,
    scrollValue: Int = 0,
    scrollMaxValue: Int = 0,
    targetMapFilename: String? = null,
    onTargetMapPositioned: (Float, Int) -> Unit = { _, _ -> }
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
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

    // Les cartes hors ligne sont un fond de carte téléchargé : elles vivent ici, pas dans la
    // section base de données (qui parle des antennes ANFR).
    Spacer(Modifier.height(sizing.spacing(16.dp)))
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
            onTargetMapPositioned = onTargetMapPositioned
        )
    }
}

@Composable
fun SectionPreferences(
    op: String, onOp: () -> Unit, lang: String, onLang: () -> Unit,
    onUnitSettings: () -> Unit,
    onPages: () -> Unit,
    onExternalLinks: () -> Unit,
    onSharePrefs: () -> Unit,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick
) {
    val featureFlags by RemoteFeatureFlags.config
    val sizing = LocalGeoTowerUiStyle.current.sizing

    SectionTitle(stringResource(R.string.settings_section_preferences))

    PreferenceOperatorCard(stringResource(R.string.settings_default_operator), op, onOp, shape, border, bubbleColor, useOneUi, safeClick)
    Spacer(Modifier.height(sizing.spacing(12.dp)))

    PreferenceLanguageCard(stringResource(R.string.settings_app_language), lang, onLang, shape, border, bubbleColor, useOneUi, safeClick)
    Spacer(Modifier.height(sizing.spacing(12.dp)))

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

    if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.PAGES_CUSTOMIZATION)) {
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
    }
    if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.EXTERNAL_LINKS_SETTINGS)) {
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
    }
    if (featureFlags.isMenuEnabled(RemoteFeatureFlags.Menus.SHARE_SETTINGS)) {
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
    }
}

/**
 * Tout ce qui vit hors de l'application : notifications de mise à jour de la base, notification
 * live et sa précision GPS, mode faible consommation, rafraîchissement du widget.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionNotifications(
    op: String,
    shape: Shape, border: BorderStroke?, bubbleColor: Color, useOneUi: Boolean, safeClick: SafeClick
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val sizing = LocalGeoTowerUiStyle.current.sizing

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

    // Demande de POST_NOTIFICATIONS par un launcher et non par un cast de LocalContext en Activity :
    // GeoTowerLocaleProvider remplace LocalContext par un contexte localisé, le cast serait null et
    // la boîte de dialogue système ne s'ouvrirait jamais. Le réglage reste tel que choisi : sans la
    // permission l'app se contente de ne rien afficher.
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    val requestNotificationPermission: () -> Unit = {
        if (!fr.geotower.utils.AppNotifications.hasPermission(context)) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SectionTitle(stringResource(R.string.settings_section_background))

    // --- INTERRUPTEUR MAÎTRE DES NOTIFICATIONS ---
    // Séparé du suivi live : on peut recevoir toutes les notifications de l'app (base, mises à jour,
    // pannes, rapport PDF, envois de photos, cartes hors ligne) sans lancer le tracking GPS.
    val appNotifsEnabled by fr.geotower.utils.AppConfig.enableAppNotifications

    PreferenceSwitchCard(
        title = stringResource(R.string.appstrings_app_notifications_title),
        desc = stringResource(R.string.appstrings_app_notifications_desc),
        checked = appNotifsEnabled,
        onCheckedChange = { isChecked ->
            fr.geotower.utils.AppNotifications.setEnabled(context, isChecked)

            // Demande la permission sur Android 13+ si on active : c'est ce réglage qui porte
            // désormais l'autorisation de notifier, plus la notification live.
            if (isChecked) requestNotificationPermission()
        },
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )
    Spacer(Modifier.height(sizing.spacing(12.dp)))

    // --- NOTIFICATIONS DE MISE À JOUR DE LA BASE ---
    // Sous-réglage : sans l'interrupteur maître, il ne pourrait rien afficher.
    val updateNotifsEnabled by fr.geotower.utils.AppConfig.enableUpdateNotifications

    if (appNotifsEnabled) {
        PreferenceSwitchCard(
            title = stringResource(R.string.appstrings_update_notif_setting_title),
            desc = stringResource(R.string.appstrings_update_notif_setting_desc),
            checked = updateNotifsEnabled,
            onCheckedChange = { isChecked ->
                fr.geotower.utils.AppConfig.enableUpdateNotifications.value = isChecked
                prefs.edit().putBoolean("enable_update_notifications", isChecked).apply()
                UpdateCheckScheduler.onNotificationsPreferenceChanged(context, isChecked)

                // Demande la permission sur Android 13+ si on active
                if (isChecked) requestNotificationPermission()
            },
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi
        )
        Spacer(Modifier.height(sizing.spacing(12.dp)))
    }

    // --- SUIVI LIVE (tracking seul) ---
    // Placé juste sous les interrupteurs de notifications : c'est le réglage le plus consulté de la
    // section, il ne doit pas se retrouver derrière le bloc « faible consommation ».
    // Ce réglage ne pilote que le service de suivi : les autres notifications de l'app dépendent de
    // l'interrupteur maître ci-dessus, et la permission POST_NOTIFICATIONS ne décide plus que de la
    // visibilité de la notification live — le suivi tourne sans elle.
    val liveTrackingEnabled by AppConfig.enableLiveTracking
    val isOperatorSelected = op != "Aucun"

    val startLiveTracking: () -> Unit = {
        if (LiveTrackingController.setEnabled(context, true) == LiveTrackingController.StartResult.Started &&
            LiveTrackingController.shouldOpenPromotedNotificationSettings(context)
        ) {
            LiveTrackingController.openPromotedNotificationSettings(context)
        }
    }
    // La permission est proposée pour que la notification live soit visible, puis le suivi démarre
    // dans tous les cas : refuser l'affichage n'est pas refuser le suivi.
    val liveTrackingPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { startLiveTracking() }

    fr.geotower.ui.components.LiveNotificationCard(
        title = stringResource(R.string.appstrings_live_notification_title),
        desc = if (isOperatorSelected) stringResource(R.string.appstrings_live_notification_desc) else stringResource(R.string.appstrings_live_notification_desc_nearest),
        checked = liveTrackingEnabled,
        onCheckedChange = { isChecked ->
            if (!isChecked) {
                LiveTrackingController.setEnabled(context, false)
            } else if (
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                LiveTrackingController.notificationPermissionMissing(context)
            ) {
                // Le suivi démarrera dans le callback du launcher, accord ou pas.
                liveTrackingPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startLiveTracking()
            }
        },
        enabled = true,
        shape = shape,
        border = border,
        bubbleColor = bubbleColor,
        useOneUi = useOneUi
    )

    if (liveTrackingEnabled) {
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
    Spacer(Modifier.height(sizing.spacing(12.dp)))

    // --- MODE FAIBLE CONSOMMATION (Normal / Éco / Éco+) ---
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
            Spacer(Modifier.height(sizing.spacing(12.dp)))
            NavigationModeOption(
                title = stringResource(R.string.appstrings_low_power_level_normal),
                desc = stringResource(R.string.appstrings_low_power_level_normal_desc),
                isSelected = effectiveLowPowerLevel == 0,
                useOneUi = useOneUi,
                onClick = { applyLowPowerLevel(0) }
            )
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            NavigationModeOption(
                title = stringResource(R.string.appstrings_low_power_level_eco),
                desc = stringResource(R.string.appstrings_low_power_level_eco_desc),
                isSelected = effectiveLowPowerLevel == 1,
                useOneUi = useOneUi,
                onClick = { applyLowPowerLevel(1) }
            )
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            NavigationModeOption(
                title = stringResource(R.string.appstrings_low_power_level_ecoplus),
                desc = stringResource(R.string.appstrings_low_power_level_ecoplus_desc),
                isSelected = effectiveLowPowerLevel == 2,
                useOneUi = useOneUi,
                onClick = { applyLowPowerLevel(2) }
            )
            if (effectiveLowPowerLevel > lowPowerLevel) {
                Spacer(Modifier.height(sizing.spacing(8.dp)))
                Text(
                    stringResource(R.string.appstrings_low_power_forced_by_system),
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    Spacer(Modifier.height(sizing.spacing(12.dp)))

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
    Spacer(Modifier.height(sizing.spacing(12.dp)))

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
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
    Surface(onClick = { safeClick("system_app_details_settings") { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", ctx.packageName, null); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } } }, shape = shape, border = border, color = cardBg, modifier = Modifier.fillMaxWidth()) {
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

    // --- AUTORISATION DE POSITION EN ARRIÈRE-PLAN ---
    // Même sujet que « Gérer les permissions » juste au-dessus : la carte n'apparaît que si
    // l'autorisation manque encore, et disparaît en douceur dès qu'elle est accordée.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val prefs = ctx.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        val bgLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            onResult = { /* L'interface de Jetpack Compose se mettra à jour toute seule */ }
        )
        val backgroundPermissionLabel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching { ctx.packageManager.backgroundPermissionOptionLabel.toString() }.getOrNull()
        } else {
            null
        }

        // L'état qui permet à l'interface de se mettre à jour toute seule
        var isBgLocationGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        // Divulgation « bien visible » exigée par Google Play : elle doit précéder TOUTE demande de
        // localisation en arrière-plan, et n'être franchie que par une action explicite. La carte
        // ne déclenche donc plus la demande directement, elle ouvre ce dialogue.
        var showBgDisclosure by remember { mutableStateOf(false) }

        fun requestBackgroundLocation() {
            // Trouver la VRAIE activité (on déballe le contexte de Compose pour réparer
            // le bug de redirection).
            var currentContext: Context = ctx
            while (currentContext is android.content.ContextWrapper && currentContext !is android.app.Activity) {
                currentContext = currentContext.baseContext
            }
            val activity = currentContext as? android.app.Activity
            val hasFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
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
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", ctx.packageName, null)
                    // FLAG_ACTIVITY_NEW_TASK : LocalContext est le contexte localisé (LocaleProvider),
                    // pas une Activity → sans ce flag, l'ouverture des réglages échoue silencieusement sur OnePlus.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { ctx.startActivity(intent) }
                return
            }

            // Analyser l'état de la permission
            val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ?: false
            val alreadyAsked = prefs.getBoolean("bg_loc_asked", false)

            // Si on a déjà demandé, que l'OS refuse d'afficher l'alerte, ET qu'on a bien trouvé l'activité
            if (alreadyAsked && !shouldShowRationale && activity != null) {
                // Plan B : le blocage est total (« Ne plus demander » coché), on ouvre les paramètres globaux
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", ctx.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // idem : contexte localisé sans Activity
                }
                ctx.startActivity(intent)
            } else {
                // Plan A : ouvre le sous-menu « Position » d'Android
                prefs.edit().putBoolean("bg_loc_asked", true).apply()
                bgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        if (showBgDisclosure) {
            AlertDialog(
                onDismissRequest = { showBgDisclosure = false },
                icon = { Icon(Icons.Default.Place, contentDescription = null) },
                title = { Text(stringResource(R.string.bg_location_disclosure_title)) },
                text = {
                    Text(
                        buildString {
                            append(stringResource(R.string.bg_location_disclosure_body))
                            if (!backgroundPermissionLabel.isNullOrBlank()) {
                                append("\n\n")
                                append(
                                    stringResource(
                                        R.string.bg_location_disclosure_option,
                                        backgroundPermissionLabel
                                    )
                                )
                            }
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showBgDisclosure = false
                        requestBackgroundLocation()
                    }) { Text(stringResource(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { showBgDisclosure = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        // On écoute le retour sur l'application pour revérifier la permission instantanément
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isBgLocationGranted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

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
                    // La divulgation passe d'abord : c'est elle qui déclenche la demande système.
                    onClick = { showBgDisclosure = true },
                    shape = shape, border = border, bubbleColor = MaterialTheme.colorScheme.errorContainer, useOneUi = useOneUi, safeClick = safeClick,
                    icon = Icons.Default.Place
                )
                Spacer(Modifier.height(sizing.spacing(12.dp)))
            }
        }
    }

    // --- SERVEUR GEOTOWER ---
    // Raccourci vers le même réglage que la carte « Serveur GeoTower » de la page Diagnostic
    // (elle-même atteignable depuis « À propos ») : état unique dans ApiEndpoints, dialogue commun,
    // et sonde forcée à la sélection. Les deux entrées ne peuvent donc pas se contredire.
    val apiServerScope = rememberCoroutineScope()
    var showApiServerDialog by remember { mutableStateOf(false) }
    val apiServerMode = ApiEndpoints.mode.value
    val apiServerActive = ApiEndpoints.activeServer.value
    Surface(
        onClick = { safeClick("system_api_server") { showApiServerDialog = true } },
        shape = shape,
        border = border,
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(sizing.component(24.dp)))
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column {
                Text(stringResource(R.string.appstrings_diagnostic_api_dialog_title), style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_api_server_desc), style = sizing.textStyle(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Le mode dit ce qui a été choisi, l'hôte ce qui est réellement utilisé : en mode
                // automatique, les deux diffèrent dès que la bascule sur le miroir a eu lieu.
                Text(
                    text = "${stringResource(apiServerModeLabelRes(apiServerMode))} · ${apiServerActive.host}",
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showApiServerDialog) {
        ApiServerModeDialog(
            currentMode = apiServerMode,
            onDismiss = { showApiServerDialog = false },
            onSelect = { selectedMode ->
                showApiServerDialog = false
                applyApiServerMode(ctx, apiServerScope, selectedMode)
            }
        )
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
    shape: Shape,
    bubbleColor: Color,
    useOneUi: Boolean,
    repository: AnfrRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    modifier: Modifier = Modifier,
    onLocalMode: () -> Unit = {},
    safeClick: SafeClick,
    databaseCardModifiers: Map<String, Modifier> = emptyMap(),
    refreshState: DatabaseRefreshState? = null,
    showRefreshButton: Boolean = false
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    fun cardAnchor(anchor: String): Modifier = databaseCardModifiers[anchor] ?: Modifier

    // On génère la bordure si on n'est pas en OneUI
    val border = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    // 🚀 LA CARTE DE LA BASE DE DONNÉES (Existante)
    Column(modifier = modifier.fillMaxWidth()) {
        // Page unique : la section n'est pas en haut du défilement, le tirage vers le bas ne peut
        // pas la viser — d'où le bouton posé sur son titre. Quand la section occupe la page à elle
        // seule, c'est le tirage Material qui prend le relais (voir SettingsScreen).
        if (showRefreshButton && refreshState != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_section_database),
                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                DatabaseSectionRefreshButton(state = refreshState, onSafeClick = safeClick)
            }
        } else {
            SectionTitle(stringResource(R.string.settings_section_database))
        }

        Box(modifier = cardAnchor(ANCHOR_DB_MOBILE).fillMaxWidth()) {
            fr.geotower.ui.components.DatabaseDownloadCard(
                useOneUi = useOneUi,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                title = stringResource(R.string.settings_database_online_title),
                refreshState = refreshState
            )
        }

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        Box(modifier = cardAnchor(ANCHOR_DB_RADIO).fillMaxWidth()) {
            fr.geotower.ui.components.RadioDatabaseDownloadCard(
                useOneUi = useOneUi,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                refreshState = refreshState
            )
        }

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        // Identifiants eNB/gNB : donnée du partenaire eNB-Analytics, pas de l'ANFR — d'où
        // l'attribution portée par la carte elle-même.
        Box(modifier = cardAnchor(ANCHOR_DB_ENB).fillMaxWidth()) {
            fr.geotower.ui.components.EnbDatabaseDownloadCard(
                useOneUi = useOneUi,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                refreshState = refreshState
            )
        }

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        // Sites en panne : fichier national du serveur, conservé sur l'appareil pour l'afficher
        // hors ligne. Il vit ici, avec les autres données téléchargées, plutôt que dans l'écran
        // Traitement local qui ne porte que la source ALTERNATIVE (récupération sur l'appareil).
        Box(modifier = cardAnchor(ANCHOR_DB_OUTAGES).fillMaxWidth()) {
            fr.geotower.ui.components.OutageDownloadCard(
                repository = repository,
                useOneUi = useOneUi,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                onSafeClick = safeClick,
                refreshState = refreshState
            )
        }

        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        Box(modifier = cardAnchor(ANCHOR_DB_LOCAL_BUILD).fillMaxWidth()) {
            fr.geotower.ui.components.LocalDbBuildCard(
                useOneUi = useOneUi,
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                refreshState = refreshState
            )
        }

        // Provenance des données : un SEUL écran décide d'où viennent la base ET les sites en panne
        // (le niveau de traitement local), leurs réglages d'exécution y sont rassemblés.
        Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

        PreferenceActionCard(
            title = stringResource(R.string.local_mode_settings_title),
            desc = stringResource(R.string.local_mode_settings_desc),
            onClick = onLocalMode,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick
        )
    }

}

/**
 * Réinitialisation globale des réglages. Elle ne dépend d'aucune section : sur téléphone en page
 * unique elle clôt la liste, ailleurs elle vit à côté de la navigation (barre latérale des
 * tablettes, accueil par sections).
 */
@Composable
private fun SettingsResetButton(shape: Shape, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var showResetDialog by remember { mutableStateOf(false) }

    TextButton(onClick = { showResetDialog = true }, modifier = modifier.fillMaxWidth()) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(sizing.spacing(8.dp)))
        Text(text = stringResource(R.string.settings_reset), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
internal fun NavigationModeOption(
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


/**
 * Entrées qui ne sont pas des réglages d'une section : deux journaux (photos favorites, historique
 * des partages) et un méta-réglage qui sauvegarde tous les autres (profils). Rendues à l'identique
 * sur l'accueil par sections et en fin de page unique ; la barre latérale des tablettes a ses
 * propres lignes.
 */
@Composable
private fun SettingsDirectEntries(
    onPhotosFavorites: () -> Unit,
    onPhotoUploadHistory: () -> Unit,
    onShareHistory: () -> Unit,
    onPreferenceProfiles: () -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: SafeClick
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(modifier = Modifier.fillMaxWidth()) {
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
        Spacer(Modifier.height(sizing.spacing(12.dp)))
        PreferenceActionCard(
            title = stringResource(R.string.appstrings_upload_history_title),
            desc = stringResource(R.string.upload_history_card_desc),
            onClick = onPhotoUploadHistory,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick,
            icon = Icons.Default.History
        )
        Spacer(Modifier.height(sizing.spacing(12.dp)))
        PreferenceActionCard(
            title = stringResource(R.string.share_history_title),
            desc = stringResource(R.string.share_history_desc),
            onClick = onShareHistory,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick,
            icon = Icons.Default.Share
        )
        Spacer(Modifier.height(sizing.spacing(12.dp)))
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
    }
}

/**
 * Accueil des réglages sur téléphone : un bouton par section plutôt que la longue page unique.
 * La bascule « tout sur une page » est dans la barre du haut et au bas de cet accueil.
 */
@Composable
private fun SettingsSectionsHome(
    sections: List<Triple<String, ImageVector, Int>>,
    onSectionClick: (Int) -> Unit,
    onShowAll: () -> Unit,
    onPhotosFavorites: () -> Unit,
    onPhotoUploadHistory: () -> Unit,
    onShareHistory: () -> Unit,
    onPreferenceProfiles: () -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: SafeClick
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_sections_home_subtitle),
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(12.dp))
        )
        sections.forEach { (title, icon, index) ->
            SettingsSectionsHomeCard(
                title = title,
                desc = settingsSectionDescription(index),
                icon = icon,
                onClick = { onSectionClick(index) },
                shape = shape,
                border = border,
                bubbleColor = bubbleColor,
                useOneUi = useOneUi,
                safeClick = safeClick
            )
            Spacer(Modifier.height(sizing.spacing(12.dp)))
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = sizing.spacing(4.dp)),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(sizing.spacing(12.dp)))
        SettingsDirectEntries(
            onPhotosFavorites = onPhotosFavorites,
            onPhotoUploadHistory = onPhotoUploadHistory,
            onShareHistory = onShareHistory,
            onPreferenceProfiles = onPreferenceProfiles,
            shape = shape,
            border = border,
            bubbleColor = bubbleColor,
            useOneUi = useOneUi,
            safeClick = safeClick
        )
        Spacer(Modifier.height(sizing.spacing(8.dp)))
        TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.ViewAgenda,
                contentDescription = null,
                modifier = Modifier.size(sizing.component(20.dp))
            )
            Spacer(Modifier.width(sizing.spacing(8.dp)))
            Text(
                text = stringResource(R.string.settings_navigation_scroll_desc),
                style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                fontWeight = FontWeight.Bold
            )
        }
        SettingsResetButton(shape = shape)
    }
}

/** Résumé de ce que contient chaque section, affiché sous son nom sur l'accueil des réglages. */
@Composable
private fun settingsSectionDescription(section: Int): String = when (section) {
    SECTION_APPEARANCE -> stringResource(R.string.settings_hub_appearance_desc)
    SECTION_MAPPING -> stringResource(R.string.settings_hub_mapping_desc)
    SECTION_PREFERENCES -> stringResource(R.string.settings_hub_preferences_desc)
    SECTION_BACKGROUND -> stringResource(R.string.settings_hub_background_desc)
    SECTION_SYSTEM -> stringResource(R.string.settings_hub_system_desc)
    else -> stringResource(R.string.settings_hub_database_desc)
}

@Composable
private fun SettingsSectionsHomeCard(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit,
    shape: Shape,
    border: BorderStroke?,
    bubbleColor: Color,
    useOneUi: Boolean,
    safeClick: SafeClick
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val cardBg = if (useOneUi) bubbleColor else Color.Transparent
    Surface(
        onClick = { safeClick("settings_section_$title") { onClick() } },
        shape = shape,
        border = border,
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(26.dp))
            )
            Spacer(Modifier.width(sizing.spacing(16.dp)))
            Column(Modifier.weight(1f)) {
                Text(title, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                Text(
                    text = desc,
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
