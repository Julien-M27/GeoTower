package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.geotower.data.AnfrRepository
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.R
import fr.geotower.radio.MobileOperator
import fr.geotower.radio.RadioThroughputEngine
import fr.geotower.radio.RatAssumptions
import fr.geotower.radio.ThroughputProfile
import fr.geotower.radio.ThroughputProfiles
import fr.geotower.ui.components.buildThroughputRadioSystems
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerBreadcrumbItem
import fr.geotower.ui.components.GeoTowerLoadingMessage
import fr.geotower.ui.components.GeoTowerNavigationBreadcrumbBar
import fr.geotower.ui.components.MiniMapConeOverlayData
import fr.geotower.ui.components.MiniMapStrongPoint
import fr.geotower.ui.components.SharedMiniMapCard
import fr.geotower.ui.components.ThroughputBandwidth
import fr.geotower.ui.components.estimateThroughputConeDistance
import fr.geotower.ui.components.extractThroughputAzimuths
import fr.geotower.ui.components.extractThroughputPanelHeightMeters
import fr.geotower.ui.components.formatThroughputDistanceMeters
import fr.geotower.ui.components.formatThroughputMbps
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.isPlannedThroughputBand
import fr.geotower.ui.components.isThroughputBandEnabledByDefault
import fr.geotower.ui.components.resolveThroughputBandwidth
import fr.geotower.ui.components.throughputBandKey
import fr.geotower.ui.components.throughputCalculationBands
import fr.geotower.ui.components.throughputBandLabel
import fr.geotower.ui.components.throughputModulationLabel
import fr.geotower.ui.components.ThroughputConeDistance as ConeDistance
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.ThroughputCalculationDefaultsSheet
import fr.geotower.ui.screens.settings.ThroughputCalculatorSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.FreqBand
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.SitePagePrefs
import fr.geotower.utils.ThroughputPrefs
import fr.geotower.utils.parseAndSortFrequencies
import fr.geotower.utils.radioFrequencyLabel
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import fr.geotower.utils.ThroughputDisplayText
import fr.geotower.utils.ThroughputTextKey

private const val PANEL_AZIMUTH_HALF_BEAM_DEGREES = 45.0
private const val MAX_FR_UPLINK_AGGREGATED_CARRIERS = 2
private val LTE_LOW_BAND_MHZ = setOf(700, 800, 900)

private const val DEFAULT_RECEIVER_HEIGHT_METERS = 2f
private const val RECEIVER_HEIGHT_MIN_METERS = 2f
private const val RECEIVER_HEIGHT_MAX_METERS = 50f
private const val GROUND_FLOOR_HEIGHT_METERS = 1.5
private const val METERS_PER_FLOOR = 3.0

private fun snapReceiverHeight(value: Float): Float {
    return value.roundToInt().toFloat().coerceIn(RECEIVER_HEIGHT_MIN_METERS, RECEIVER_HEIGHT_MAX_METERS)
}

private fun floorEquivalentForHeight(heightMeters: Double): Int {
    return ((heightMeters - GROUND_FLOOR_HEIGHT_METERS) / METERS_PER_FLOOR).roundToInt().coerceAtLeast(0)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThroughputCalculatorScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: String,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {},
    incomingConfig: String? = null
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.THROUGHPUT_CALCULATOR)
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
    // A throughput deep link (QR share) carries the settings as a compact config string. Apply it to
    // the prefs before the state below reads them, so the calculator opens with the shared settings.
    remember(incomingConfig) {
        if (!incomingConfig.isNullOrBlank()) applyThroughputShareConfig(prefs, incomingConfig)
        incomingConfig
    }
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUiDesign = AppConfig.useOneUiDesign
    val isSystemDark = isSystemInDarkTheme()
    val isDark = themeMode == 2 || (themeMode == 0 && isSystemDark)
    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUiDesign) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val uiStyle = LocalGeoTowerUiStyle.current
    val blockShape = if (useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "site_detail/$antennaId")

    fun handleBackNavigation() {
        if (isSplitScreen) {
            onCloseSplitScreen()
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    var site by remember { mutableStateOf<LocalisationEntity?>(null) }
    var physique by remember { mutableStateOf<PhysiqueEntity?>(null) }
    var technique by remember { mutableStateOf<TechniqueEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var coneMapView by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    var customSettings by remember {
        mutableStateOf(readThroughputCustomSettings(prefs))
    }
    fun updateCustomSettings(newSettings: CustomModulationSettings) {
        customSettings = newSettings
        val editor = prefs.edit()
            .putInt(ThroughputPrefs.CUSTOM_LTE_DOWN, newSettings.lteDownIndex)
            .putInt(ThroughputPrefs.CUSTOM_LTE_UP, newSettings.lteUpIndex)
            .putInt(ThroughputPrefs.CUSTOM_NR_DOWN, newSettings.nrDownIndex)
            .putInt(ThroughputPrefs.CUSTOM_NR_UP, newSettings.nrUpIndex)
            .putFloat(ThroughputPrefs.CUSTOM_LTE_RSRP, newSettings.lteRsrpDbm)
            .putFloat(ThroughputPrefs.CUSTOM_LTE_SINR, newSettings.lteSinrDb)
            .putFloat(ThroughputPrefs.CUSTOM_NR_RSRP, newSettings.nrRsrpDbm)
            .putFloat(ThroughputPrefs.CUSTOM_NR_SINR, newSettings.nrSinrDb)
            .putString(ThroughputPrefs.CUSTOM_ENVIRONMENT, newSettings.environment.id)
            .putString(ThroughputPrefs.CUSTOM_POSITION, newSettings.positionScenario.id)
            .putString(ThroughputPrefs.CUSTOM_NETWORK_LOAD, newSettings.networkLoad.id)
            .putString(ThroughputPrefs.CUSTOM_BACKHAUL, newSettings.backhaul.id)
            .putString(ThroughputPrefs.CUSTOM_LTE_AGGREGATION, newSettings.lteAggregation.id)
            .putFloat(ThroughputPrefs.CUSTOM_RECEIVER_HEIGHT, newSettings.receiverHeightMeters)
            .remove(ThroughputPrefs.CUSTOM_DEVICE)
        if (newSettings.selectedLatitude != null && newSettings.selectedLongitude != null) {
            editor
                .putString(ThroughputPrefs.CUSTOM_SELECTED_LAT, newSettings.selectedLatitude.toString())
                .putString(ThroughputPrefs.CUSTOM_SELECTED_LON, newSettings.selectedLongitude.toString())
        } else {
            editor
                .remove(ThroughputPrefs.CUSTOM_SELECTED_LAT)
                .remove(ThroughputPrefs.CUSTOM_SELECTED_LON)
        }
        editor.apply()
    }
    var enabledBandKeys by remember(antennaId) { mutableStateOf<Set<String>>(emptySet()) }
    var bandKeysInitialized by remember(antennaId) { mutableStateOf(false) }
    var include4G by remember { mutableStateOf(ThroughputPrefs.include4G.read(prefs)) }
    var include5G by remember { mutableStateOf(ThroughputPrefs.include5G.read(prefs)) }
    var includePlanned by remember { mutableStateOf(ThroughputPrefs.includePlanned.read(prefs)) }
    var pageSiteThroughputCalculator by remember { mutableStateOf(SitePagePrefs.throughputCalculator.read(prefs)) }
    var showThroughputSettingsSheet by remember { mutableStateOf(false) }
    var showThroughputDefaultsSheet by remember { mutableStateOf(false) }
    var throughputDefaultsVersion by remember { mutableStateOf(0) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var throughputBlockOrder by remember(prefs) {
        mutableStateOf(
        normalizeThroughputBlockOrder(
            prefs.getString(ThroughputPrefs.BLOCK_ORDER, ThroughputPrefs.defaultBlockOrder.joinToString(","))
                ?.split(",")
                .orEmpty()
        )
        )
    }
    var visibleThroughputBlocks by remember(prefs) {
        mutableStateOf(
            ThroughputBlock.entries.associateWith { block ->
                prefs.getBoolean(block.prefKey, true)
            }
        )
    }

    BackHandler(enabled = isSplitScreen || !safeBackNavigation.isLocked) {
        handleBackNavigation()
    }

    LaunchedEffect(antennaId) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            loadThroughputSite(context, repository, antennaId)
        }
        site = loaded.site
        physique = loaded.physique
        technique = loaded.technique
        isLoading = false
    }

    val txtUnknown = stringResource(R.string.appstrings_unknown)
    val txtAzimuthNotSpecified = stringResource(R.string.appstrings_azimuth_not_specified)
    val rawFrequencies = remember(site, technique) {
        technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: site?.frequences
    }
    val parsedBands = remember(rawFrequencies, txtUnknown, txtAzimuthNotSpecified) {
        parseAndSortFrequencies(rawFrequencies, txtUnknown, txtAzimuthNotSpecified)
    }
    val availableBandKeys = remember(parsedBands) {
        throughputCalculationBands(parsedBands)
            .map { throughputBandKey(it) }
            .toSet()
    }
    val defaultEnabledBandKeys = remember(parsedBands, prefs, throughputDefaultsVersion) {
        throughputCalculationBands(parsedBands)
            .filter { isThroughputBandEnabledByDefault(it, prefs) }
            .map { throughputBandKey(it) }
            .toSet()
    }
    LaunchedEffect(availableBandKeys, defaultEnabledBandKeys) {
        if (!bandKeysInitialized && availableBandKeys.isNotEmpty()) {
            enabledBandKeys = defaultEnabledBandKeys.intersect(availableBandKeys)
            bandKeysInitialized = true
        } else if (bandKeysInitialized) {
            enabledBandKeys = enabledBandKeys.intersect(availableBandKeys)
        }
    }
    val effectiveEnabledBandKeys = if (bandKeysInitialized) enabledBandKeys else availableBandKeys
    val supportHeightMeters = physique?.hauteur
    val result = remember(parsedBands, site?.operateur, customSettings, include4G, include5G, includePlanned, supportHeightMeters, effectiveEnabledBandKeys) {
        calculateThroughput(
            bands = parsedBands,
            operatorName = site?.operateur,
            preset = ThroughputPreset.Conservative,
            customSettings = customSettings,
            include4G = include4G,
            include5G = include5G,
            includePlanned = includePlanned,
            enabledBandKeys = effectiveEnabledBandKeys,
            supportHeightMeters = supportHeightMeters,
            receiverHeightMeters = customSettings.receiverHeightMeters.toDouble(),
            siteLatitude = site?.latitude ?: 0.0,
            siteLongitude = site?.longitude ?: 0.0,
            siteAzimuths = site?.azimuts
        )
    }
    val txtSiteDetailsTitle = stringResource(R.string.appstrings_site_detail_title)
    val txtThroughputCalculatorTitle = stringResource(R.string.appstrings_throughput_calculator_title)

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Column(modifier = Modifier.background(mainBgColor)) {
                GeoTowerBackTopBar(
                    title = txtThroughputCalculatorTitle,
                    onBack = { handleBackNavigation() },
                    backgroundColor = mainBgColor,
                    backEnabled = isSplitScreen || !safeBackNavigation.isLocked,
                    actions = {
                        site?.let { loadedSite ->
                            fr.geotower.ui.components.ThroughputShareMenu(
                                info = loadedSite,
                                physique = physique,
                                technique = technique,
                                useOneUi = uiStyle.useOneUi,
                                coneMapView = coneMapView
                            )
                        }
                        IconButton(onClick = { showThroughputSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_pages_customization_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
                GeoTowerNavigationBreadcrumbBar(
                    navController = navController,
                    currentItem = GeoTowerBreadcrumbItem(
                        label = txtThroughputCalculatorTitle,
                        icon = Icons.Default.Speed,
                        key = "throughput_calculator"
                    ),
                    currentRouteKeys = setOf("throughput_calculator"),
                    impliedParentItems = if (isSplitScreen) {
                        listOf(
                            GeoTowerBreadcrumbItem(
                                label = txtSiteDetailsTitle,
                                icon = Icons.Default.Tag,
                                onClick = onCloseSplitScreen,
                                key = "site_detail"
                            )
                        )
                    } else {
                        emptyList()
                    },
                    onBackStackItemClick = {
                        if (isSplitScreen) onCloseSplitScreen()
                    },
                    backgroundColor = if (useOneUiDesign) cardBgColor else MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    ) { padding ->
        when {
            isLoading -> LoadingPane(padding, mainBgColor)
            site == null -> MessagePane(
                padding = padding,
                mainBgColor = mainBgColor,
                message = stringResource(R.string.appstrings_throughput_no_site)
            )
            else -> ThroughputContent(
                padding = padding,
                site = site!!,
                physique = physique,
                mainBgColor = mainBgColor,
                cardBgColor = cardBgColor,
                blockShape = blockShape,
                customSettings = customSettings,
                onCustomSettingsChange = ::updateCustomSettings,
                include4G = include4G,
                onInclude4GChange = { include4G = it },
                include5G = include5G,
                onInclude5GChange = { include5G = it },
                includePlanned = includePlanned,
                onIncludePlannedChange = { includePlanned = it },
                enabledBandKeys = effectiveEnabledBandKeys,
                onBandEnabledChange = { key, enabled ->
                    val currentKeys = if (bandKeysInitialized) enabledBandKeys else availableBandKeys
                    enabledBandKeys = if (enabled) currentKeys + key else currentKeys - key
                    bandKeysInitialized = true
                },
                result = result,
                blockOrder = throughputBlockOrder,
                visibleBlocks = visibleThroughputBlocks,
                onConeMapReady = { coneMapView = it }
            )
        }
    }

    fun refreshThroughputDefaultsFromPrefs() {
        customSettings = readThroughputCustomSettings(prefs)
        include4G = ThroughputPrefs.include4G.read(prefs)
        include5G = ThroughputPrefs.include5G.read(prefs)
        includePlanned = ThroughputPrefs.includePlanned.read(prefs)
        bandKeysInitialized = false
        throughputDefaultsVersion++
    }

    if (showThroughputSettingsSheet) {
        ThroughputCalculatorSettingsSheet(
            showThroughputCalculator = pageSiteThroughputCalculator,
            onThroughputCalculatorChange = {
                pageSiteThroughputCalculator = it
                prefs.edit().putBoolean(SitePagePrefs.throughputCalculator.key, it).apply()
            },
            throughputOrder = throughputBlockOrder.map { it.id },
            onThroughputOrderChange = { newOrder ->
                val normalized = normalizeThroughputBlockOrder(newOrder)
                throughputBlockOrder = normalized
                prefs.edit().putString(ThroughputPrefs.BLOCK_ORDER, normalized.joinToString(",") { it.id }).apply()
            },
            showHeader = visibleThroughputBlocks[ThroughputBlock.Header] ?: true,
            onHeaderChange = {
                visibleThroughputBlocks = visibleThroughputBlocks + (ThroughputBlock.Header to it)
                prefs.edit().putBoolean(ThroughputBlock.Header.prefKey, it).apply()
            },
            showSummary = visibleThroughputBlocks[ThroughputBlock.Summary] ?: true,
            onSummaryChange = {
                visibleThroughputBlocks = visibleThroughputBlocks + (ThroughputBlock.Summary to it)
                prefs.edit().putBoolean(ThroughputBlock.Summary.prefKey, it).apply()
            },
            showCone = visibleThroughputBlocks[ThroughputBlock.Cone] ?: true,
            onConeChange = {
                visibleThroughputBlocks = visibleThroughputBlocks + (ThroughputBlock.Cone to it)
                prefs.edit().putBoolean(ThroughputBlock.Cone.prefKey, it).apply()
            },
            showControls = visibleThroughputBlocks[ThroughputBlock.Controls] ?: true,
            onControlsChange = {
                visibleThroughputBlocks = visibleThroughputBlocks + (ThroughputBlock.Controls to it)
                prefs.edit().putBoolean(ThroughputBlock.Controls.prefKey, it).apply()
            },
            showBands = visibleThroughputBlocks[ThroughputBlock.Bands] ?: true,
            onBandsChange = {
                visibleThroughputBlocks = visibleThroughputBlocks + (ThroughputBlock.Bands to it)
                prefs.edit().putBoolean(ThroughputBlock.Bands.prefKey, it).apply()
            },
            showAssumptions = visibleThroughputBlocks[ThroughputBlock.Assumptions] ?: true,
            onAssumptionsChange = {
                visibleThroughputBlocks = visibleThroughputBlocks + (ThroughputBlock.Assumptions to it)
                prefs.edit().putBoolean(ThroughputBlock.Assumptions.prefKey, it).apply()
            },
            onOpenCalculationDefaults = {
                showThroughputSettingsSheet = false
                showThroughputDefaultsSheet = true
            },
            onDismiss = { showThroughputSettingsSheet = false },
            onBack = { showThroughputSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }

    if (showThroughputDefaultsSheet) {
        ThroughputCalculationDefaultsSheet(
            onDismiss = {
                showThroughputDefaultsSheet = false
                refreshThroughputDefaultsFromPrefs()
            },
            onBack = {
                showThroughputDefaultsSheet = false
                refreshThroughputDefaultsFromPrefs()
                showThroughputSettingsSheet = true
            },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane(padding: PaddingValues, mainBgColor: Color) {
    Box(
        modifier = Modifier
            .padding(top = padding.calculateTopPadding())
            .fillMaxSize()
            .background(mainBgColor),
        contentAlignment = Alignment.Center
    ) {
        GeoTowerLoadingMessage(
            title = stringResource(R.string.appstrings_throughput_loading_title),
            detail = stringResource(R.string.appstrings_throughput_loading_desc)
        )
    }
}

@Composable
private fun MessagePane(padding: PaddingValues, mainBgColor: Color, message: String) {
    Box(
        modifier = Modifier
            .padding(top = padding.calculateTopPadding())
            .fillMaxSize()
            .background(mainBgColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ThroughputContent(
    padding: PaddingValues,
    site: LocalisationEntity,
    physique: PhysiqueEntity?,
    mainBgColor: Color,
    cardBgColor: Color,
    blockShape: Shape,
    customSettings: CustomModulationSettings,
    onCustomSettingsChange: (CustomModulationSettings) -> Unit,
    include4G: Boolean,
    onInclude4GChange: (Boolean) -> Unit,
    include5G: Boolean,
    onInclude5GChange: (Boolean) -> Unit,
    includePlanned: Boolean,
    onIncludePlannedChange: (Boolean) -> Unit,
    enabledBandKeys: Set<String>,
    onBandEnabledChange: (String, Boolean) -> Unit,
    result: ThroughputResult,
    blockOrder: List<ThroughputBlock>,
    visibleBlocks: Map<ThroughputBlock, Boolean>,
    onConeMapReady: (org.osmdroid.views.MapView) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(top = padding.calculateTopPadding())
            .fillMaxSize()
            .background(mainBgColor)
            .navigationBarsPadding()
            .geoTowerFadingEdge(scrollState)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        blockOrder.forEach { block ->
            if (visibleBlocks[block] != true) return@forEach
            when (block) {
                ThroughputBlock.Header -> ThroughputHeaderCard(site, physique, cardBgColor, blockShape)
                ThroughputBlock.Summary -> ThroughputSummaryCard(result, cardBgColor, blockShape)
                ThroughputBlock.Cone -> ThroughputConeCard(
                    site = site,
                    result = result,
                    cardBgColor = cardBgColor,
                    blockShape = blockShape,
                    customSettings = customSettings,
                    onCustomSettingsChange = onCustomSettingsChange,
                    onConeMapReady = onConeMapReady
                )
                ThroughputBlock.Controls -> ThroughputControlsCard(
                    cardBgColor = cardBgColor,
                    blockShape = blockShape,
                    include4G = include4G,
                    onInclude4GChange = onInclude4GChange,
                    include5G = include5G,
                    onInclude5GChange = onInclude5GChange,
                    includePlanned = includePlanned,
                    onIncludePlannedChange = onIncludePlannedChange,
                    bands = result.bands,
                    enabledBandKeys = enabledBandKeys,
                    onBandEnabledChange = onBandEnabledChange
                )
                ThroughputBlock.Bands -> {
                    if (result.bands.isEmpty()) {
                        Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
                            Text(
                                text = stringResource(R.string.appstrings_throughput_no_bands),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        ThroughputBandsCard(result, cardBgColor, blockShape)
                    }
                }
                ThroughputBlock.Assumptions -> ThroughputAssumptionsCard(result, cardBgColor, blockShape)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Static rendering of the calculator page used for the share image / QR export: same header and
 * cards, in the user's block order, minus the interactive controls and live map.
 */
@Composable
fun ThroughputShareContent(
    site: LocalisationEntity,
    physique: PhysiqueEntity?,
    technique: TechniqueEntity?,
    prefs: SharedPreferences,
    cardBgColor: Color,
    blockShape: Shape,
    coneMapBitmap: android.graphics.Bitmap? = null
) {
    val txtUnknown = stringResource(R.string.appstrings_unknown)
    val txtAzimuthNotSpecified = stringResource(R.string.appstrings_azimuth_not_specified)
    val rawFrequencies = technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: site.frequences
    val parsedBands = remember(rawFrequencies, txtUnknown, txtAzimuthNotSpecified) {
        parseAndSortFrequencies(rawFrequencies, txtUnknown, txtAzimuthNotSpecified)
    }
    val customSettings = remember(prefs) { readThroughputCustomSettings(prefs) }
    val include4G = remember(prefs) { ThroughputPrefs.include4G.read(prefs) }
    val include5G = remember(prefs) { ThroughputPrefs.include5G.read(prefs) }
    val includePlanned = remember(prefs) { ThroughputPrefs.includePlanned.read(prefs) }
    val enabledBandKeys = remember(parsedBands, prefs) {
        throughputCalculationBands(parsedBands)
            .filter { isThroughputBandEnabledByDefault(it, prefs) }
            .map { throughputBandKey(it) }
            .toSet()
    }
    val result = remember(parsedBands, customSettings, include4G, include5G, includePlanned, enabledBandKeys, physique?.hauteur) {
        calculateThroughput(
            bands = parsedBands,
            operatorName = site.operateur,
            preset = ThroughputPreset.Conservative,
            customSettings = customSettings,
            include4G = include4G,
            include5G = include5G,
            includePlanned = includePlanned,
            enabledBandKeys = enabledBandKeys,
            supportHeightMeters = physique?.hauteur,
            receiverHeightMeters = customSettings.receiverHeightMeters.toDouble(),
            siteLatitude = site.latitude,
            siteLongitude = site.longitude,
            siteAzimuths = site.azimuts
        )
    }
    val blockOrder = remember(prefs) {
        normalizeThroughputBlockOrder(
            prefs.getString(ThroughputPrefs.BLOCK_ORDER, ThroughputPrefs.defaultBlockOrder.joinToString(","))
                ?.split(",")
                .orEmpty()
        )
    }
    val visibleBlocks = remember(prefs) {
        ThroughputBlock.entries.associateWith { prefs.getBoolean(it.prefKey, true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        blockOrder.forEach { block ->
            if (visibleBlocks[block] != true) return@forEach
            when (block) {
                ThroughputBlock.Header -> ThroughputHeaderCard(site, physique, cardBgColor, blockShape)
                ThroughputBlock.Summary -> ThroughputSummaryCard(result, cardBgColor, blockShape)
                ThroughputBlock.Cone -> ThroughputShareConeCard(result, cardBgColor, blockShape, coneMapBitmap)
                ThroughputBlock.Controls -> Unit // interactive: omitted from the static share image
                ThroughputBlock.Bands -> {
                    if (result.bands.isEmpty()) {
                        Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
                            Text(
                                text = stringResource(R.string.appstrings_throughput_no_bands),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        ThroughputBandsCard(result, cardBgColor, blockShape)
                    }
                }
                ThroughputBlock.Assumptions -> ThroughputAssumptionsCard(result, cardBgColor, blockShape)
            }
        }
    }
}

@Composable
private fun ThroughputShareConeCard(
    result: ThroughputResult,
    cardBgColor: Color,
    blockShape: Shape,
    coneMapBitmap: android.graphics.Bitmap? = null
) {
    val coneDistance = result.coneDistance
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.appstrings_throughput_estimated_optimal_distance_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (coneDistance == null) {
                Text(
                    text = stringResource(R.string.appstrings_throughput_cone_height_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = formatThroughputDistanceMeters(coneDistance.centerMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ThroughputDisplayText.mainZoneEstimated(
                        formatThroughputDistanceMeters(coneDistance.nearMeters),
                        formatThroughputDistanceMeters(coneDistance.farMeters)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.appstrings_throughput_cone_assumption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (coneMapBitmap != null) {
                    Spacer(Modifier.height(4.dp))
                    Image(
                        bitmap = coneMapBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = stringResource(R.string.appstrings_throughput_cone_map_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThroughputHeaderCard(
    site: LocalisationEntity,
    physique: PhysiqueEntity?,
    cardBgColor: Color,
    blockShape: Shape
) {
    val supportHeightLabel = physique?.hauteur?.let { formatHeightMeters(it) }
    val unknown = stringResource(R.string.appstrings_unknown)
    val supportLabel = stringResource(R.string.appstrings_speedtests_support_label)
    val operatorName = site.operateur?.trim()?.takeIf { it.isNotEmpty() } ?: unknown
    val operatorColor = OperatorColors.keyFor(operatorName)
        ?.let { Color(OperatorColors.colorArgbForKey(it)) }
        ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OperatorLogoOrFallback(
                operatorName = operatorName,
                operatorColor = operatorColor,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operatorName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ThroughputDisplayText.headerSite(site.idAnfr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supportHeightLabel?.let { supportHeight ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$supportLabel $supportHeight",
                        style = MaterialTheme.typography.labelMedium,
                        color = operatorColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun OperatorLogoOrFallback(
    operatorName: String?,
    operatorColor: Color,
    modifier: Modifier = Modifier
) {
    val logoRes = getDetailLogoRes(operatorName)
    if (logoRes != null) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = operatorName,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(operatorColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = operatorName?.take(1)?.uppercase()?.ifBlank { "?" } ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
        }
    }
}

@Composable
private fun ThroughputSummaryCard(result: ThroughputResult, cardBgColor: Color, blockShape: Shape) {
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.appstrings_throughput_estimated_radio_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ThroughputMetric(
                    label = stringResource(R.string.appstrings_throughput_download_label),
                    value = formatThroughputMbps(result.totalDownMbps),
                    modifier = Modifier.weight(1f)
                )
                ThroughputMetric(
                    label = stringResource(R.string.appstrings_throughput_phone_upload_label),
                    value = formatThroughputMbps(result.totalUpMbps),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = stringResource(R.string.appstrings_throughput_summary_upload_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = ThroughputDisplayText.includedBandsCount(result.includedBands.size, result.bands.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThroughputMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ThroughputConeCard(
    site: LocalisationEntity,
    result: ThroughputResult,
    cardBgColor: Color,
    blockShape: Shape,
    customSettings: CustomModulationSettings? = null,
    onCustomSettingsChange: ((CustomModulationSettings) -> Unit)? = null,
    onConeMapReady: (org.osmdroid.views.MapView) -> Unit = {}
) {
    val coneDistance = result.coneDistance
    val selectedMapPoint = customSettings?.let { settings ->
        val latitude = settings.selectedLatitude
        val longitude = settings.selectedLongitude
        if (latitude != null && longitude != null) MapPoint(latitude, longitude) else null
    }
    val hasPositionCorrection = customSettings?.let { settings ->
        settings.positionScenario != PositionScenario.Unknown ||
            (settings.selectedLatitude != null && settings.selectedLongitude != null)
    } == true
    val coneMapOverlay = remember(site, result, selectedMapPoint) {
        buildConeOverlayData(site, result, selectedMapPoint)
    }
    val selectedPositionAnalysis = remember(site, result, selectedMapPoint) {
        selectedMapPoint?.let { analyzePosition(site, result, it.latitude, it.longitude) }
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isChoosingPoint by remember { mutableStateOf(false) }
    var isResolvingPosition by remember { mutableStateOf(false) }
    var positionStatusMessage by remember { mutableStateOf<String?>(null) }
    var mapFitRequest by remember { mutableStateOf(0) }
    val currentPositionAppliedText = stringResource(R.string.appstrings_throughput_position_current_applied)
    val mapPointAppliedText = stringResource(R.string.appstrings_throughput_position_map_point_applied)
    val permissionDeniedText = stringResource(R.string.appstrings_throughput_position_permission_denied)
    val unavailableText = stringResource(R.string.appstrings_throughput_position_unavailable)
    val tapMapText = stringResource(R.string.appstrings_throughput_tap_map_to_choose)
    val locatingText = stringResource(R.string.appstrings_throughput_position_locating)
    val pureTheoreticalText = stringResource(R.string.appstrings_throughput_position_cleared)

    fun applySelectedPosition(
        latitude: Double,
        longitude: Double,
        statusMessage: String,
        fitMapToSelection: Boolean
    ) {
        if (customSettings == null || onCustomSettingsChange == null) return
        val analysis = analyzePosition(site, result, latitude, longitude)
        onCustomSettingsChange(
            customSettings.copy(
                positionScenario = analysis.scenario,
                selectedLatitude = latitude,
                selectedLongitude = longitude
            )
        )
        isChoosingPoint = false
        positionStatusMessage = statusMessage
        if (fitMapToSelection) {
            mapFitRequest += 1
        }
    }

    fun resolveCurrentPosition() {
        coroutineScope.launch {
            isResolvingPosition = true
            val location = loadCurrentThroughputLocation(context)
            if (location != null) {
                applySelectedPosition(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    statusMessage = currentPositionAppliedText,
                    fitMapToSelection = true
                )
            } else {
                positionStatusMessage = unavailableText
            }
            isResolvingPosition = false
        }
    }

    fun clearSelectedPosition() {
        if (customSettings == null || onCustomSettingsChange == null) return
        onCustomSettingsChange(
            customSettings.copy(
                positionScenario = PositionScenario.Unknown,
                selectedLatitude = null,
                selectedLongitude = null
            )
        )
        isChoosingPoint = false
        positionStatusMessage = pureTheoreticalText
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            resolveCurrentPosition()
        } else {
            positionStatusMessage = permissionDeniedText
        }
    }

    fun handleUseCurrentPosition() {
        if (hasThroughputLocationPermission(context)) {
            resolveCurrentPosition()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.appstrings_throughput_estimated_optimal_distance_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (coneDistance == null) {
                Text(
                    text = stringResource(R.string.appstrings_throughput_cone_height_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = formatThroughputDistanceMeters(coneDistance.centerMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ThroughputDisplayText.mainZoneEstimated(
                        formatThroughputDistanceMeters(coneDistance.nearMeters),
                        formatThroughputDistanceMeters(coneDistance.farMeters)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.appstrings_throughput_cone_assumption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (customSettings != null && onCustomSettingsChange != null) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Text(
                        text = stringResource(R.string.appstrings_throughput_receiver_height_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.appstrings_throughput_receiver_height_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SignalSlider(
                        label = "",
                        value = customSettings.receiverHeightMeters,
                        valueRange = RECEIVER_HEIGHT_MIN_METERS..RECEIVER_HEIGHT_MAX_METERS,
                        steps = (RECEIVER_HEIGHT_MAX_METERS - RECEIVER_HEIGHT_MIN_METERS).roundToInt() - 1,
                        unit = "m",
                        useOneUi = LocalGeoTowerUiStyle.current.useOneUi,
                        onValueChange = { newHeight ->
                            onCustomSettingsChange(customSettings.copy(receiverHeightMeters = snapReceiverHeight(newHeight)))
                        },
                        valueLabel = { meters ->
                            ThroughputDisplayText.receiverHeightValue(
                                formatHeightMeters(meters.toDouble()),
                                ThroughputDisplayText.floorEquivalent(floorEquivalentForHeight(meters.toDouble()))
                            )
                        }
                    )
                }
                if (coneMapOverlay != null) {
                    Spacer(Modifier.height(4.dp))
                    if (customSettings != null && onCustomSettingsChange != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Text(
                            text = stringResource(R.string.appstrings_throughput_custom_position_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isResolvingPosition,
                                onClick = { handleUseCurrentPosition() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isResolvingPosition) locatingText else stringResource(R.string.appstrings_throughput_use_current_position))
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    isChoosingPoint = true
                                    positionStatusMessage = tapMapText
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.appstrings_throughput_choose_map_point))
                            }
                            if (hasPositionCorrection) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { clearSelectedPosition() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.appstrings_throughput_clear_position))
                                }
                            }
                        }
                        positionStatusMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isChoosingPoint) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        PositionAnalysisSummary(
                            analysis = selectedPositionAnalysis,
                            scenario = customSettings.positionScenario
                        )
                    }
                    val mapTapHandler = if (customSettings != null && onCustomSettingsChange != null && isChoosingPoint) {
                        { latitude: Double, longitude: Double ->
                            applySelectedPosition(
                                latitude = latitude,
                                longitude = longitude,
                                statusMessage = mapPointAppliedText,
                                fitMapToSelection = false
                            )
                        }
                    } else {
                        null
                    }
                    SharedMiniMapCard(
                        modifier = Modifier.fillMaxWidth(),
                        centerLat = site.latitude,
                        centerLon = site.longitude,
                        mappedAntennas = listOf(site),
                        blockShape = blockShape,
                        cardBorder = null,
                        onMapReady = onConeMapReady,
                        focusOperator = site.operateur,
                        coneOverlay = coneMapOverlay,
                        initialZoom = mapZoomForCone(coneDistance.centerMeters),
                        onMapTap = mapTapHandler,
                        allowGestures = customSettings != null,
                        fitSelectedPointRequest = mapFitRequest
                    )
                    Text(
                        text = stringResource(R.string.appstrings_throughput_cone_map_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionAnalysisSummary(
    analysis: PositionAnalysis?,
    scenario: PositionScenario
) {
    if (analysis == null) {
        Text(
            text = stringResource(R.string.appstrings_throughput_position_no_selection),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = ThroughputDisplayText.customSelectedPosition(
                ThroughputDisplayText.positionScenarioLabel(scenario.id)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val nearestAzimuth = analysis.nearestAzimuthDegrees?.let { formatDegrees(it) }
    val deltaDegrees = analysis.azimuthDeltaDegrees?.roundToInt()
    val azimuthText = when (analysis.isInsideAzimuth) {
        true -> ThroughputDisplayText.positionAzimuthInside(
            nearestAzimuth.orEmpty(),
            deltaDegrees ?: 0
        )
        false -> ThroughputDisplayText.positionAzimuthOutside(
            nearestAzimuth.orEmpty(),
            deltaDegrees ?: 0
        )
        null -> stringResource(R.string.appstrings_throughput_position_azimuth_unknown)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = ThroughputDisplayText.customSelectedPosition(
                ThroughputDisplayText.positionScenarioLabel(analysis.scenario.id)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = ThroughputDisplayText.positionDistance(formatThroughputDistanceMeters(analysis.distanceMeters)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = azimuthText,
            style = MaterialTheme.typography.bodySmall,
            color = if (analysis.isInsideAzimuth == false) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = analysis.coneDistance?.let { cone ->
                ThroughputDisplayText.positionCone(
                    center = formatThroughputDistanceMeters(cone.centerMeters),
                    near = formatThroughputDistanceMeters(cone.nearMeters),
                    far = formatThroughputDistanceMeters(cone.farMeters)
                )
            } ?: stringResource(R.string.appstrings_throughput_position_cone_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThroughputControlsCard(
    cardBgColor: Color,
    blockShape: Shape,
    include4G: Boolean,
    onInclude4GChange: (Boolean) -> Unit,
    include5G: Boolean,
    onInclude5GChange: (Boolean) -> Unit,
    includePlanned: Boolean,
    onIncludePlannedChange: (Boolean) -> Unit,
    bands: List<ThroughputBandResult>,
    enabledBandKeys: Set<String>,
    onBandEnabledChange: (String, Boolean) -> Unit
) {
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.appstrings_throughput_frequencies_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = include4G, onClick = { onInclude4GChange(!include4G) }, label = { Text("4G") })
                FilterChip(selected = include5G, onClick = { onInclude5GChange(!include5G) }, label = { Text("5G") })
                FilterChip(
                    selected = includePlanned,
                    onClick = { onIncludePlannedChange(!includePlanned) },
                    label = { Text(stringResource(R.string.appstrings_throughput_include_planned)) }
                )
            }
            if (bands.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    text = stringResource(R.string.appstrings_throughput_included_bands_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bands.forEach { band ->
                        BandCheckboxRow(
                            band = band,
                            checked = enabledBandKeys.contains(band.key),
                            onCheckedChange = { onBandEnabledChange(band.key, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomModulationControls(
    settings: CustomModulationSettings,
    onSettingsChange: (CustomModulationSettings) -> Unit,
    useOneUi: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.appstrings_throughput_custom_modulation_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        ModulationSlider(
            label = stringResource(R.string.appstrings_throughput4g_download_label),
            options = lteDownModulationOptions,
            selectedIndex = settings.lteDownIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(lteDownIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = stringResource(R.string.appstrings_throughput4g_upload_label),
            options = lteUpModulationOptions,
            selectedIndex = settings.lteUpIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(lteUpIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = stringResource(R.string.appstrings_throughput5g_download_label),
            options = nrDownModulationOptions,
            selectedIndex = settings.nrDownIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(nrDownIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = stringResource(R.string.appstrings_throughput5g_upload_label),
            options = nrUpModulationOptions,
            selectedIndex = settings.nrUpIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(nrUpIndex = it)) },
            useOneUi = useOneUi
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Text(
            text = stringResource(R.string.appstrings_throughput_custom_signal_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.appstrings_throughput_custom_signal_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SignalSlider(
            label = "RSRP 4G",
            value = settings.lteRsrpDbm,
            valueRange = -125f..-60f,
            steps = 64,
            unit = "dBm",
            useOneUi = useOneUi,
            onValueChange = { onSettingsChange(settings.copy(lteRsrpDbm = it)) }
        )
        SignalSlider(
            label = "SNR/SINR 4G",
            value = settings.lteSinrDb,
            valueRange = -10f..35f,
            steps = 44,
            unit = "dB",
            useOneUi = useOneUi,
            onValueChange = { onSettingsChange(settings.copy(lteSinrDb = it)) }
        )
        SignalSlider(
            label = "SS-RSRP 5G",
            value = settings.nrRsrpDbm,
            valueRange = -125f..-60f,
            steps = 64,
            unit = "dBm",
            useOneUi = useOneUi,
            onValueChange = { onSettingsChange(settings.copy(nrRsrpDbm = it)) }
        )
        SignalSlider(
            label = "SS-SINR 5G",
            value = settings.nrSinrDb,
            valueRange = -10f..40f,
            steps = 49,
            unit = "dB",
            useOneUi = useOneUi,
            onValueChange = { onSettingsChange(settings.copy(nrSinrDb = it)) }
        )

        CustomChoiceRow(
            title = stringResource(R.string.appstrings_throughput_custom_environment_title),
            selectedId = settings.environment.id,
            options = RadioEnvironment.entries.map { it.id },
            optionLabel = { ThroughputDisplayText.environmentLabel(it) },
            onSelect = { onSettingsChange(settings.copy(environment = radioEnvironmentFromPreference(it))) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Text(
            text = stringResource(R.string.appstrings_throughput_custom_terminal_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.appstrings_throughput_custom_terminal_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CustomChoiceRow(
            title = stringResource(R.string.appstrings_throughput_custom_network_load_title),
            selectedId = settings.networkLoad.id,
            options = NetworkLoad.entries.map { it.id },
            optionLabel = { ThroughputDisplayText.networkLoadLabel(it) },
            onSelect = { onSettingsChange(settings.copy(networkLoad = networkLoadFromPreference(it))) }
        )
        CustomChoiceRow(
            title = stringResource(R.string.appstrings_throughput_custom_backhaul_title),
            selectedId = settings.backhaul.id,
            options = BackhaulQuality.entries.map { it.id },
            optionLabel = { ThroughputDisplayText.backhaulLabel(it) },
            onSelect = { onSettingsChange(settings.copy(backhaul = backhaulQualityFromPreference(it))) }
        )
        CustomChoiceRow(
            title = stringResource(R.string.appstrings_throughput_custom_aggregation_title),
            selectedId = settings.lteAggregation.id,
            options = LteAggregationMode.entries.map { it.id },
            optionLabel = { ThroughputDisplayText.lteAggregationLabel(it) },
            onSelect = { onSettingsChange(settings.copy(lteAggregation = lteAggregationModeFromPreference(it))) }
        )

        val lteImpact = (customThroughputMultipliers(4, settings).down * 100).roundToInt()
        val nrImpact = (customThroughputMultipliers(5, settings).down * 100).roundToInt()
        Text(
            text = ThroughputDisplayText.customImpact(lteImpact, nrImpact),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        CustomCalculationExplanation(
            settings = settings,
            lteImpactPercent = lteImpact,
            nrImpactPercent = nrImpact
        )
    }
}

@Composable
private fun CustomCalculationExplanation(
    settings: CustomModulationSettings,
    lteImpactPercent: Int,
    nrImpactPercent: Int
) {
    val maxLteCarriers = settings.lteAggregation.maxLteCarriers

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.appstrings_throughput_custom_explanation_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_explanation_modulation_title),
            description = stringResource(R.string.appstrings_throughput_custom_explanation_modulation_desc)
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_signal_title),
            description = ThroughputDisplayText.customExplanationSignalDesc(lteImpactPercent, nrImpactPercent)
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_environment_title),
            description = stringResource(R.string.appstrings_throughput_custom_explanation_environment_desc)
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_position_title),
            description = stringResource(R.string.appstrings_throughput_custom_explanation_position_desc)
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_network_load_title),
            description = stringResource(R.string.appstrings_throughput_custom_explanation_network_load_desc)
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_backhaul_title),
            description = stringResource(R.string.appstrings_throughput_custom_explanation_backhaul_desc)
        )
        CustomExplanationLine(
            title = stringResource(R.string.appstrings_throughput_custom_aggregation_title),
            description = ThroughputDisplayText.customExplanationAggregationDesc(maxLteCarriers)
        )
    }
}

@Composable
private fun CustomExplanationLine(
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignalSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    useOneUi: Boolean,
    onValueChange: (Float) -> Unit,
    valueLabel: (@Composable (Int) -> String)? = null
) {
    val roundedValue = value.roundToInt().coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
    val valueSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val valueFraction = ((roundedValue - valueRange.start) / valueSpan).coerceIn(0f, 1f)
    val tickCount = steps.coerceAtLeast(0) + 2
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val valueText = valueLabel?.invoke(roundedValue) ?: "$roundedValue $unit"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (label.isBlank()) valueText else "$label : $valueText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (useOneUi) {
            Slider(
                value = roundedValue.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().toFloat()) },
                valueRange = valueRange,
                steps = steps.coerceAtLeast(0),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = { _ ->
                    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                        val centerY = size.height / 2
                        drawLine(
                            color = inactiveTrackColor,
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 10.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = activeTrackColor,
                            start = Offset(0f, centerY),
                            end = Offset(size.width * valueFraction, centerY),
                            strokeWidth = 10.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        val dotRadius = if (tickCount > 52) 0.85.dp.toPx() else 1.15.dp.toPx()
                        val lastIndex = (tickCount - 1).coerceAtLeast(1)
                        for (i in 0 until tickCount) {
                            val x = size.width * (i.toFloat() / lastIndex.toFloat())
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = Offset(x, centerY)
                            )
                        }
                    }
                }
            )
        } else {
            Slider(
                value = roundedValue.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().toFloat()) },
                valueRange = valueRange,
                steps = steps.coerceAtLeast(0)
            )
        }
    }
}

@Composable
private fun CustomChoiceRow(
    title: String,
    selectedId: String,
    options: List<String>,
    optionLabel: @Composable (String) -> String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selectedId == option,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModulationSlider(
    label: String,
    options: List<ModulationOption>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    useOneUi: Boolean
) {
    val coercedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val selectedOption = options[coercedIndex]
    val onSliderChange: (Float) -> Unit = { value ->
        onSelectedIndexChange(value.roundToInt().coerceIn(0, options.lastIndex))
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label : ${selectedOption.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (useOneUi) {
            Slider(
                value = coercedIndex.toFloat(),
                onValueChange = onSliderChange,
                valueRange = 0f..options.lastIndex.toFloat(),
                steps = (options.size - 2).coerceAtLeast(0),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = { _ ->
                    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                        val centerY = size.height / 2
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 14.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        val stepWidth = size.width / options.lastIndex.coerceAtLeast(1)
                        for (i in options.indices) {
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.6f),
                                radius = 4.dp.toPx(),
                                center = Offset(i * stepWidth, centerY)
                            )
                        }
                    }
                }
            )
        } else {
            Slider(
                value = coercedIndex.toFloat(),
                onValueChange = onSliderChange,
                valueRange = 0f..options.lastIndex.toFloat(),
                steps = (options.size - 2).coerceAtLeast(0)
            )
        }
    }
}

@Composable
private fun BandCheckboxRow(
    band: ThroughputBandResult,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = band.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${band.frequencyDetails} - ${formatBandwidth(band.bandwidthMHz)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThroughputBandsCard(result: ThroughputResult, cardBgColor: Color, blockShape: Shape) {
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.appstrings_throughput_frequencies_and_modulation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            result.bands.forEach { band ->
                ThroughputBandRow(band)
            }
        }
    }
}

@Composable
private fun ThroughputBandRow(band: ThroughputBandResult) {
    val contentColor = if (band.isIncluded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val metricColor = if (band.isIncluded && band.downAggregationExcludedReason == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val estimatedText = stringResource(R.string.appstrings_throughput_estimated_suffix)
    val bandDetail = buildString {
        append(formatBandwidth(band.bandwidthMHz))
        if (band.bandwidthIsEstimated) append(" ").append(estimatedText)
        if (band.status.isNotBlank()) append(" - ").append(band.status)
    }
    val coneLabel = band.coneDistance?.let {
        ThroughputDisplayText.estimatedCone(
            formatThroughputDistanceMeters(it.centerMeters),
            formatThroughputDistanceMeters(it.nearMeters),
            formatThroughputDistanceMeters(it.farMeters)
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(band.label, fontWeight = FontWeight.Bold, color = contentColor)
            ThroughputDetailLine(
                label = stringResource(R.string.appstrings_throughput_frequencies_label),
                value = band.frequencyDetails,
                color = contentColor
            )
            ThroughputDetailLine(
                label = stringResource(R.string.appstrings_throughput_modulation_and_antennas_label),
                value = band.modulationLabel,
                color = contentColor
            )
            Text(
                text = bandDetail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            if (coneLabel != null) {
                Text(
                    text = coneLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
            if (!band.isIncluded && band.excludedReason != null) {
                Text(
                    text = ThroughputDisplayText.translateExcludedReason(band.excludedReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (band.isIncluded && band.downAggregationExcludedReason != null) {
                Text(
                    text = ThroughputDisplayText.translateExcludedReason(band.downAggregationExcludedReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatThroughputMbps(band.downMbps), fontWeight = FontWeight.Bold, color = metricColor)
            Text(formatThroughputMbps(band.upMbps), style = MaterialTheme.typography.bodySmall, color = metricColor)
        }
    }
}

@Composable
private fun ThroughputDetailLine(label: String, value: String, color: Color) {
    Text(
        text = "$label : $value",
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

@Composable
private fun ThroughputAssumptionsCard(
    result: ThroughputResult,
    cardBgColor: Color,
    blockShape: Shape
) {
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.appstrings_throughput_read_as_estimate_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.appstrings_throughput_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.warnings.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.appstrings_throughput_attention_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.warnings.take(4).forEach { warning ->
                    Text(
                        text = "- ${ThroughputDisplayText.translateWarning(warning)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (result.assumptions.isNotEmpty()) {
                Text(
                    text = ThroughputDisplayText.calculationAssumptions(localizedThroughputAssumptions(result.assumptions)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = ThroughputDisplayText.sources(ThroughputDisplayText.translateSourceSummary(result.sourceSummary)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun localizedThroughputAssumptions(assumptions: List<String>): String {
    val translated = mutableListOf<String>()
    for (assumption in assumptions) {
        translated += ThroughputDisplayText.translateAssumption(assumption)
    }
    return translated.joinToString(" | ")
}

private suspend fun loadThroughputSite(
    context: Context,
    repository: AnfrRepository,
    antennaId: String
): ThroughputSiteData {
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
    val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()
    val antennas = repository.getAntennasByExactId(antennaId)
    val selected = when {
        antennas.isEmpty() -> null
        savedLat != 0.0 && savedLon != 0.0 -> antennas.minByOrNull {
            abs(it.latitude - savedLat) + abs(it.longitude - savedLon)
        }
        else -> antennas.first()
    }
    val physique = selected?.let { repository.getPhysiqueByAnfr(it.idAnfr).firstOrNull() }
    val technique = selected?.let { repository.getTechniqueByAnfr(it.idAnfr).firstOrNull() }
    return ThroughputSiteData(selected, physique, technique)
}

private fun readThroughputCustomSettings(prefs: SharedPreferences): CustomModulationSettings {
    return CustomModulationSettings(
        lteDownIndex = prefs.getInt(ThroughputPrefs.CUSTOM_LTE_DOWN, 3).coerceIn(0, lteDownModulationOptions.lastIndex),
        lteUpIndex = prefs.getInt(ThroughputPrefs.CUSTOM_LTE_UP, 2).coerceIn(0, lteUpModulationOptions.lastIndex),
        nrDownIndex = prefs.getInt(ThroughputPrefs.CUSTOM_NR_DOWN, 3).coerceIn(0, nrDownModulationOptions.lastIndex),
        nrUpIndex = prefs.getInt(ThroughputPrefs.CUSTOM_NR_UP, 2).coerceIn(0, nrUpModulationOptions.lastIndex),
        lteRsrpDbm = prefs.getFloat(ThroughputPrefs.CUSTOM_LTE_RSRP, -95f),
        lteSinrDb = prefs.getFloat(ThroughputPrefs.CUSTOM_LTE_SINR, 15f),
        nrRsrpDbm = prefs.getFloat(ThroughputPrefs.CUSTOM_NR_RSRP, -92f),
        nrSinrDb = prefs.getFloat(ThroughputPrefs.CUSTOM_NR_SINR, 18f),
        environment = radioEnvironmentFromPreference(prefs.getString(ThroughputPrefs.CUSTOM_ENVIRONMENT, null)),
        positionScenario = positionScenarioFromPreference(prefs.getString(ThroughputPrefs.CUSTOM_POSITION, null)),
        networkLoad = networkLoadFromPreference(prefs.getString(ThroughputPrefs.CUSTOM_NETWORK_LOAD, null)),
        backhaul = backhaulQualityFromPreference(prefs.getString(ThroughputPrefs.CUSTOM_BACKHAUL, null)),
        lteAggregation = lteAggregationModeFromPreference(prefs.getString(ThroughputPrefs.CUSTOM_LTE_AGGREGATION, null)),
        receiverHeightMeters = snapReceiverHeight(prefs.getFloat(ThroughputPrefs.CUSTOM_RECEIVER_HEIGHT, DEFAULT_RECEIVER_HEIGHT_METERS)),
        selectedLatitude = prefs.getString(ThroughputPrefs.CUSTOM_SELECTED_LAT, null)?.toDoubleOrNull(),
        selectedLongitude = prefs.getString(ThroughputPrefs.CUSTOM_SELECTED_LON, null)?.toDoubleOrNull()
    )
}

private const val THROUGHPUT_SHARE_CONFIG_VERSION = "v1"

/**
 * Encodes the settings that actually influence the calculator result (generation toggles, planned
 * bands, arrival height, selected position) into a compact string carried by the QR deep link.
 */
fun encodeThroughputShareConfig(prefs: SharedPreferences): String {
    val include4G = if (ThroughputPrefs.include4G.read(prefs)) "1" else "0"
    val include5G = if (ThroughputPrefs.include5G.read(prefs)) "1" else "0"
    val includePlanned = if (ThroughputPrefs.includePlanned.read(prefs)) "1" else "0"
    val receiverHeight = prefs.getFloat(ThroughputPrefs.CUSTOM_RECEIVER_HEIGHT, DEFAULT_RECEIVER_HEIGHT_METERS).roundToInt().toString()
    val latitude = prefs.getString(ThroughputPrefs.CUSTOM_SELECTED_LAT, null).orEmpty()
    val longitude = prefs.getString(ThroughputPrefs.CUSTOM_SELECTED_LON, null).orEmpty()
    return listOf(THROUGHPUT_SHARE_CONFIG_VERSION, include4G, include5G, includePlanned, receiverHeight, latitude, longitude)
        .joinToString(";")
}

/** Writes a config string produced by [encodeThroughputShareConfig] back into the throughput prefs. */
fun applyThroughputShareConfig(prefs: SharedPreferences, config: String) {
    val parts = config.split(";")
    if (parts.size < 5 || parts[0] != THROUGHPUT_SHARE_CONFIG_VERSION) return
    val editor = prefs.edit()
    ThroughputPrefs.include4G.write(editor, parts[1] == "1")
    ThroughputPrefs.include5G.write(editor, parts[2] == "1")
    ThroughputPrefs.includePlanned.write(editor, parts[3] == "1")
    parts[4].toFloatOrNull()?.let {
        editor.putFloat(ThroughputPrefs.CUSTOM_RECEIVER_HEIGHT, it.coerceIn(RECEIVER_HEIGHT_MIN_METERS, RECEIVER_HEIGHT_MAX_METERS))
    }
    val latitude = parts.getOrNull(5)?.takeIf { it.isNotBlank() && it.toDoubleOrNull() != null }
    val longitude = parts.getOrNull(6)?.takeIf { it.isNotBlank() && it.toDoubleOrNull() != null }
    if (latitude != null && longitude != null) {
        editor.putString(ThroughputPrefs.CUSTOM_SELECTED_LAT, latitude)
        editor.putString(ThroughputPrefs.CUSTOM_SELECTED_LON, longitude)
    } else {
        editor.remove(ThroughputPrefs.CUSTOM_SELECTED_LAT).remove(ThroughputPrefs.CUSTOM_SELECTED_LON)
    }
    editor.apply()
}

private fun calculateThroughput(
    bands: List<FreqBand>,
    operatorName: String?,
    preset: ThroughputPreset,
    customSettings: CustomModulationSettings,
    include4G: Boolean,
    include5G: Boolean,
    includePlanned: Boolean,
    enabledBandKeys: Set<String>,
    supportHeightMeters: Double?,
    receiverHeightMeters: Double,
    siteLatitude: Double,
    siteLongitude: Double,
    siteAzimuths: String?
): ThroughputResult {
    val operator = MobileOperator.fromLabel(operatorName)
    val engineProfile = engineProfileFor(preset, customSettings)
    val engineResult = if (operator != null) {
        val systems = buildThroughputRadioSystems(bands, operator, supportHeightMeters)
        RadioThroughputEngine.estimate(systems, engineProfile)
    } else {
        null
    }
    val carrierByKey = engineResult?.perCarrierResults.orEmpty().associateBy { it.sourceKey }
    val excludedByKey = engineResult?.excludedCarriers.orEmpty().associateBy { it.sourceKey }

    val calculatedBands = throughputCalculationBands(bands)
        .map { band ->
            val key = throughputBandKey(band)
            val carrierResult = carrierByKey[key]
            val bandwidth = carrierResult?.let {
                ThroughputBandwidth(valueMHz = it.bandwidthMHz, isEstimated = false)
            } ?: resolveThroughputBandwidth(band)
            val isPlanned = isPlannedThroughputBand(band)
            val generationAllowed = (band.gen == 4 && include4G) || (band.gen == 5 && include5G)
            val bandAllowed = enabledBandKeys.contains(key)
            val engineIncluded = carrierResult?.included == true
            val isIncluded = generationAllowed && bandAllowed && engineIncluded && (includePlanned || !isPlanned)
            val panelHeightMeters = extractThroughputPanelHeightMeters(band, supportHeightMeters)
            val azimuths = extractThroughputAzimuths(band)
            // Position is applied as a second pass below (continuous geometric factor); here we only
            // apply the non-position multipliers so the per-band cone/azimuths are available first.
            val baseMultipliers = if (preset == ThroughputPreset.Custom) {
                throughputMultipliersFor(preset, band.gen, customSettings)
            } else {
                CustomThroughputMultipliers()
            }
            val excludedReason = when {
                !generationAllowed -> if (band.gen == 5) ThroughputTextKey.THROUGHPUT_REASON_5G_DISABLED else ThroughputTextKey.THROUGHPUT_REASON_4G_DISABLED
                !bandAllowed -> ThroughputTextKey.THROUGHPUT_REASON_BAND_EXCLUDED
                operator == null -> ThroughputTextKey.THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED_ARCEP
                carrierResult == null -> excludedByKey[key]?.reason ?: ThroughputTextKey.THROUGHPUT_REASON_ARCEP_ALLOCATION_NOT_FOUND
                !carrierResult.included -> carrierResult.excludedReason
                isPlanned && !includePlanned -> ThroughputTextKey.THROUGHPUT_REASON_PLANNED_BAND
                else -> null
            }

            ThroughputBandResult(
                key = key,
                label = throughputBandLabel(band),
                generation = band.gen,
                frequencyMHz = band.value,
                frequencyDetails = frequencyDetailsLabel(band),
                modulationLabel = carrierResult?.let { throughputModulationLabel(it.dlModulationOrder, it.ulModulationOrder, it.dlMimoLayers, it.ulMimoLayers) }
                    ?: throughputModulationLabel(band.gen, engineProfile),
                bandwidthMHz = bandwidth.valueMHz,
                bandwidthIsEstimated = bandwidth.isEstimated,
                coneDistance = estimateThroughputConeDistance(panelHeightMeters, userDeviceHeightMeters = receiverHeightMeters),
                azimuths = azimuths,
                status = band.status,
                downMbps = (carrierResult?.dlMbps ?: 0.0) * baseMultipliers.down,
                upMbps = (carrierResult?.ulMbps ?: 0.0) * baseMultipliers.up,
                isIncluded = isIncluded,
                excludedReason = excludedReason
            )
        }

    // Second pass: when a position is selected (non-custom presets), scale the débit by a continuous
    // factor derived from the real geometry. The arrival height reshapes the cone, so this factor —
    // and therefore the throughput — varies smoothly with height instead of jumping between zones.
    val selectedLat = customSettings.selectedLatitude
    val selectedLon = customSettings.selectedLongitude
    val positionMultipliers = if (preset != ThroughputPreset.Custom && selectedLat != null && selectedLon != null) {
        val cone = aggregateThroughputCone(calculatedBands)
        val azimuths = strongThroughputAzimuths(calculatedBands, siteAzimuths)
        val distance = distanceMetersBetween(siteLatitude, siteLongitude, selectedLat, selectedLon)
        val bearing = bearingDegreesBetween(siteLatitude, siteLongitude, selectedLat, selectedLon)
        val nearestAzimuth = azimuths.minByOrNull { angularDistanceDegrees(it, bearing) }
        val azimuthDelta = nearestAzimuth?.let { angularDistanceDegrees(it, bearing) }
        continuousPositionMultipliers(cone, distance, azimuthDelta)
    } else {
        CustomThroughputMultipliers()
    }
    val positionedBands = if (positionMultipliers.down == 1.0 && positionMultipliers.up == 1.0) {
        calculatedBands
    } else {
        calculatedBands.map { band ->
            band.copy(
                downMbps = band.downMbps * positionMultipliers.down,
                upMbps = band.upMbps * positionMultipliers.up
            )
        }
    }

    val aggregationAwareBands = applyCarrierAggregationPolicy(positionedBands, engineProfile)
    val aggregationWarnings = aggregationAwareBands
        .mapNotNull { it.downAggregationExcludedReason }
        .distinct()
    val uplinkAggregationWarning = if (aggregationAwareBands.count { it.isIncluded && it.downAggregationExcludedReason == null && it.upMbps > 0.0 } > MAX_FR_UPLINK_AGGREGATED_CARRIERS) {
        listOf(ThroughputTextKey.THROUGHPUT_WARNING_UPLINK_AGGREGATION)
    } else {
        emptyList()
    }

    return ThroughputResult(
        bands = aggregationAwareBands,
        warnings = (engineResult?.warnings.orEmpty().filterNot(::isThroughputProfileWarning) + aggregationWarnings + uplinkAggregationWarning).distinct(),
        assumptions = engineResult?.assumptions.orEmpty().filterNot(::isThroughputProfileAssumption).distinct(),
        confidenceScore = engineResult?.confidenceScore ?: 35,
        calculationVersion = engineResult?.calculationVersion ?: fr.geotower.radio.THROUGHPUT_CALCULATION_VERSION,
        sourceSummary = engineResult?.sourceSummary ?: ThroughputTextKey.THROUGHPUT_SOURCE_SUMMARY_DEFAULT
    )
}

private fun isThroughputProfileWarning(warning: String): Boolean {
    return warning.startsWith(ThroughputTextKey.THROUGHPUT_WARNING_PROFILE_PREFIX) &&
        warning.endsWith(ThroughputTextKey.THROUGHPUT_WARNING_PROFILE_SUFFIX)
}

private fun isThroughputProfileAssumption(assumption: String): Boolean {
    return assumption in setOf(
        ThroughputTextKey.THROUGHPUT_PROFILE_PRUDENT_DESC,
        ThroughputTextKey.THROUGHPUT_PROFILE_STANDARD_DESC,
        ThroughputTextKey.THROUGHPUT_PROFILE_IDEAL_DESC,
        ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_DESC,
        ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_SHORT_DESC
    )
}

private fun frequencyDetailsLabel(band: FreqBand): String {
    val detailed = band.rawFreq.substringAfter(":", "").trim()
    return detailed.takeIf { it.isNotBlank() } ?: if (band.value > 0) radioFrequencyLabel(band.value) else band.rawFreq
}

private fun engineProfileFor(
    preset: ThroughputPreset,
    customSettings: CustomModulationSettings
): ThroughputProfile {
    return when (preset) {
        ThroughputPreset.Conservative -> ThroughputProfiles.prudent
        ThroughputPreset.Standard -> ThroughputProfiles.standard
        ThroughputPreset.Maximum -> ThroughputProfiles.ideal
        ThroughputPreset.Custom -> customProfile(customSettings)
    }
}

private fun customProfile(customSettings: CustomModulationSettings): ThroughputProfile {
    val lteDown = lteDownModulationOptions[customSettings.lteDownIndex.coerceIn(0, lteDownModulationOptions.lastIndex)]
    val lteUp = lteUpModulationOptions[customSettings.lteUpIndex.coerceIn(0, lteUpModulationOptions.lastIndex)]
    val nrDown = nrDownModulationOptions[customSettings.nrDownIndex.coerceIn(0, nrDownModulationOptions.lastIndex)]
    val nrUp = nrUpModulationOptions[customSettings.nrUpIndex.coerceIn(0, nrUpModulationOptions.lastIndex)]

    return ThroughputProfile(
        id = "CUSTOM",
        label = ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_LABEL,
        description = ThroughputTextKey.THROUGHPUT_PROFILE_CUSTOM_DESC,
        lte = RatAssumptions(
            dlModulationOrder = lteDown.modulationOrder,
            ulModulationOrder = lteUp.modulationOrder,
            dlMimoLayers = 2,
            ulMimoLayers = 1,
            maxCaComponents = customSettings.lteAggregation.maxLteCarriers
        ),
        nr = RatAssumptions(
            dlModulationOrder = nrDown.modulationOrder,
            ulModulationOrder = nrUp.modulationOrder,
            dlMimoLayers = 4,
            ulMimoLayers = 2,
            maxCaComponents = 1,
            scsKHz = 30,
            tddDlRatio = 0.70,
            tddUlRatio = 0.20,
            overheadDl = 0.14,
            overheadUl = 0.08
        )
    )
}

private fun customThroughputMultipliers(
    generation: Int,
    settings: CustomModulationSettings
): CustomThroughputMultipliers {
    val rsrp = if (generation == 5) settings.nrRsrpDbm else settings.lteRsrpDbm
    val sinr = if (generation == 5) settings.nrSinrDb else settings.lteSinrDb
    val rsrpScore = ((rsrp + 120f) / 45f).coerceIn(0f, 1f).toDouble()
    val sinrScore = ((sinr + 5f) / 35f).coerceIn(0f, 1f).toDouble()
    val radioScore = (rsrpScore * 0.4) + (sinrScore * 0.6)
    val radioMultiplier = (0.55 + radioScore * 0.75).coerceIn(0.25, 1.20)
    val down = (
        radioMultiplier *
            settings.environment.multiplier *
            settings.positionScenario.multiplier *
            settings.networkLoad.downMultiplier *
            settings.backhaul.downMultiplier
        )
        .coerceIn(0.10, 1.20)
    val upPositionMultiplier = (settings.positionScenario.multiplier * 0.9) + 0.1
    val up = (
        radioMultiplier *
            settings.environment.multiplier *
            upPositionMultiplier *
            settings.networkLoad.upMultiplier *
            settings.backhaul.upMultiplier
        )
        .coerceIn(0.10, 1.15)
    return CustomThroughputMultipliers(down = down, up = up)
}

private fun throughputMultipliersFor(
    preset: ThroughputPreset,
    generation: Int,
    settings: CustomModulationSettings
): CustomThroughputMultipliers {
    return if (preset == ThroughputPreset.Custom) {
        customThroughputMultipliers(generation, settings)
    } else {
        positionThroughputMultipliers(settings.positionScenario)
    }
}

private fun positionThroughputMultipliers(
    scenario: PositionScenario
): CustomThroughputMultipliers {
    val upPositionMultiplier = (scenario.multiplier * 0.9) + 0.1
    return CustomThroughputMultipliers(
        down = scenario.multiplier.coerceIn(0.10, 1.20),
        up = upPositionMultiplier.coerceIn(0.10, 1.15)
    )
}

private const val NOMINAL_DOWNTILT_DEGREES = 6.0
private const val ELEVATION_BEAM_SIGMA_DEGREES = 2.5
private const val AZIMUTH_BEAM_SIGMA_DEGREES = 35.0
private const val POSITION_FACTOR_FLOOR = 0.45
private const val POSITION_FACTOR_PEAK = 1.06

private fun aggregateThroughputCone(bands: List<ThroughputBandResult>): ConeDistance? {
    val distances = bands.filter { it.isIncluded }.mapNotNull { it.coneDistance }
    if (distances.isEmpty()) return null
    return ConeDistance(
        centerMeters = distances.map { it.centerMeters }.average(),
        nearMeters = distances.minOf { it.nearMeters },
        farMeters = distances.maxOf { it.farMeters }
    )
}

private fun strongThroughputAzimuths(
    bands: List<ThroughputBandResult>,
    siteAzimuths: String?
): List<Double> {
    val fromBands = bands.filter { it.isIncluded }
        .flatMap { it.azimuths }
        .distinctBy { it.roundToInt() }
    if (fromBands.isNotEmpty()) return fromBands
    return siteAzimuths
        ?.split(",")
        ?.mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
        ?.filter { it in 0.0..360.0 }
        ?.distinctBy { it.roundToInt() }
        .orEmpty()
}

/**
 * Continuous geometric factor: instead of a 4-step zone classification, the elevation angle from the
 * panel to the receiver is compared with the nominal downtilt. The gain peaks when the angle matches
 * the beam centre and rolls off with a Gaussian on both the elevation and azimuth offsets. Because the
 * cone centre encodes (antenna height - arrival height), the factor varies smoothly with the chosen
 * arrival height and the horizontal distance.
 */
private fun continuousPositionMultipliers(
    cone: ConeDistance?,
    distanceMeters: Double,
    azimuthDeltaDegrees: Double?
): CustomThroughputMultipliers {
    if (cone == null) return CustomThroughputMultipliers()
    val center = cone.centerMeters.coerceAtLeast(1.0)
    val verticalDelta = center * tan(Math.toRadians(NOMINAL_DOWNTILT_DEGREES))
    val safeDistance = distanceMeters.coerceAtLeast(1.0)
    val elevationDeg = Math.toDegrees(atan2(verticalDelta, safeDistance))
    val elevationGain = exp(-(elevationDeg - NOMINAL_DOWNTILT_DEGREES).pow(2) / (2 * ELEVATION_BEAM_SIGMA_DEGREES.pow(2)))
    val azimuthGain = azimuthDeltaDegrees?.let {
        exp(-it.pow(2) / (2 * AZIMUTH_BEAM_SIGMA_DEGREES.pow(2)))
    } ?: 1.0
    val gain = (elevationGain * azimuthGain).coerceIn(0.0, 1.0)
    val down = (POSITION_FACTOR_FLOOR + (POSITION_FACTOR_PEAK - POSITION_FACTOR_FLOOR) * gain).coerceIn(0.10, 1.20)
    val up = (down * 0.9 + 0.1).coerceIn(0.10, 1.15)
    return CustomThroughputMultipliers(down = down, up = up)
}

private fun applyLteLowBandAggregationPolicy(
    bands: List<ThroughputBandResult>
): List<ThroughputBandResult> {
    val includedLowBands = bands
        .filter { it.isIncluded && it.generation == 4 && it.frequencyMHz in LTE_LOW_BAND_MHZ }
    if (includedLowBands.size <= 1) return bands

    val keptBand = includedLowBands.maxWith(
        compareBy<ThroughputBandResult> { it.downMbps }
            .thenBy { it.bandwidthMHz }
    )
    val excludedKeys = includedLowBands
        .filterNot { it.key == keptBand.key }
        .map { it.key }
        .toSet()
    val reason = ThroughputTextKey.THROUGHPUT_WARNING_LOW_BAND_AGGREGATION

    return bands.map { band ->
        if (band.key in excludedKeys) {
            band.copy(downAggregationExcludedReason = reason)
        } else {
            band
        }
    }
}

private fun applyCarrierAggregationPolicy(
    bands: List<ThroughputBandResult>,
    profile: ThroughputProfile
): List<ThroughputBandResult> {
    val lowBandAwareBands = applyLteLowBandAggregationPolicy(bands)
    val maxLteCarriers = profile.lte.maxCaComponents
    val maxNrCarriers = profile.nr.maxCaComponents

    return lowBandAwareBands
        .limitAggregatedCarriers(
            generation = 4,
            maxCarriers = maxLteCarriers,
            reason = ThroughputTextKey.THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT
        )
        .limitAggregatedCarriers(
            generation = 5,
            maxCarriers = maxNrCarriers,
            reason = ThroughputTextKey.THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT
        )
}

private fun List<ThroughputBandResult>.limitAggregatedCarriers(
    generation: Int,
    maxCarriers: Int,
    reason: String
): List<ThroughputBandResult> {
    val safeMaxCarriers = maxCarriers.coerceAtLeast(1)
    val eligibleBands = filter {
        it.isIncluded &&
            it.generation == generation &&
            it.downAggregationExcludedReason == null
    }
    if (eligibleBands.size <= safeMaxCarriers) return this

    val keptKeys = eligibleBands
        .sortedWith(
            compareByDescending<ThroughputBandResult> { it.downMbps }
                .thenByDescending { it.bandwidthMHz }
        )
        .take(safeMaxCarriers)
        .map { it.key }
        .toSet()

    return map { band ->
        if (band in eligibleBands && band.key !in keptKeys) {
            band.copy(downAggregationExcludedReason = reason)
        } else {
            band
        }
    }
}

private fun formatBandwidth(valueMHz: Double): String {
    return if (valueMHz % 1.0 == 0.0) {
        "${valueMHz.toInt()} MHz"
    } else {
        String.format(Locale.US, "%.1f MHz", valueMHz)
    }
}

private fun formatHeightMeters(valueMeters: Double): String {
    return if (AppConfig.distanceUnit.intValue == 1) {
        "${(valueMeters * 3.28084).roundToInt()} ft"
    } else {
        "${formatNumber(valueMeters)} m"
    }
}

private fun formatDegrees(value: Double): String = "${value.roundToInt()}\u00B0"

private fun formatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

private fun buildConeOverlayData(
    site: LocalisationEntity,
    result: ThroughputResult,
    selectedPoint: MapPoint? = null
): MiniMapConeOverlayData? {
    val coneDistance = result.coneDistance ?: return null
    val azimuths = strongAzimuthsForSite(site, result)

    val strongPoints = azimuths.take(12).map { azimuth ->
        val point = destinationPoint(site.latitude, site.longitude, coneDistance.centerMeters, azimuth)
        MiniMapStrongPoint(latitude = point.latitude, longitude = point.longitude)
    }

    return MiniMapConeOverlayData(
        centerLat = site.latitude,
        centerLon = site.longitude,
        radiusMeters = coneDistance.centerMeters,
        strongPoints = strongPoints,
        selectedPoint = selectedPoint?.let { MiniMapStrongPoint(latitude = it.latitude, longitude = it.longitude) }
    )
}

private fun analyzePosition(
    site: LocalisationEntity,
    result: ThroughputResult,
    latitude: Double,
    longitude: Double
): PositionAnalysis {
    val coneDistance = result.coneDistance
    val distance = distanceMetersBetween(site.latitude, site.longitude, latitude, longitude)
    val azimuths = strongAzimuthsForSite(site, result)
    val bearing = bearingDegreesBetween(site.latitude, site.longitude, latitude, longitude)
    val nearestAzimuth = azimuths.minByOrNull { angularDistanceDegrees(it, bearing) }
    val azimuthDelta = nearestAzimuth?.let { angularDistanceDegrees(it, bearing) }
    val isInsideAzimuth = azimuthDelta?.let { it <= PANEL_AZIMUTH_HALF_BEAM_DEGREES }
    val scenario = when {
        coneDistance == null -> PositionScenario.Unknown
        isInsideAzimuth == false -> PositionScenario.OutsideBeam
        distance < coneDistance.nearMeters -> PositionScenario.TooClose
        distance > coneDistance.farMeters -> PositionScenario.TooFar
        else -> PositionScenario.InCone
    }

    return PositionAnalysis(
        distanceMeters = distance,
        bearingDegrees = bearing,
        scenario = scenario,
        nearestAzimuthDegrees = nearestAzimuth,
        azimuthDeltaDegrees = azimuthDelta,
        isInsideAzimuth = isInsideAzimuth,
        coneDistance = coneDistance
    )
}

private fun strongAzimuthsForSite(site: LocalisationEntity, result: ThroughputResult): List<Double> {
    return result.strongAzimuths.ifEmpty {
        site.azimuts
            ?.split(",")
            ?.mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            ?.filter { it in 0.0..360.0 }
            .orEmpty()
    }.distinctBy { it.roundToInt() }
}

private fun distanceMetersBetween(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val fromLatRad = Math.toRadians(fromLatitude)
    val toLatRad = Math.toRadians(toLatitude)
    val deltaLat = Math.toRadians(toLatitude - fromLatitude)
    val deltaLon = Math.toRadians(toLongitude - fromLongitude)
    val sinHalfLat = sin(deltaLat / 2.0)
    val sinHalfLon = sin(deltaLon / 2.0)
    val a = sinHalfLat * sinHalfLat + cos(fromLatRad) * cos(toLatRad) * sinHalfLon * sinHalfLon
    val normalizedA = a.coerceIn(0.0, 1.0)
    val c = 2.0 * atan2(sqrt(normalizedA), sqrt(1.0 - normalizedA))
    return earthRadiusMeters * c
}

private fun bearingDegreesBetween(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val fromLatRad = Math.toRadians(fromLatitude)
    val toLatRad = Math.toRadians(toLatitude)
    val deltaLon = Math.toRadians(toLongitude - fromLongitude)
    val y = sin(deltaLon) * cos(toLatRad)
    val x = cos(fromLatRad) * sin(toLatRad) - sin(fromLatRad) * cos(toLatRad) * cos(deltaLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun angularDistanceDegrees(first: Double, second: Double): Double {
    return abs(((first - second + 540.0) % 360.0) - 180.0)
}

private fun destinationPoint(
    latitude: Double,
    longitude: Double,
    distanceMeters: Double,
    bearingDegrees: Double
): MapPoint {
    val angularDistance = distanceMeters / 6_371_000.0
    val bearing = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(latitude)
    val lon1 = Math.toRadians(longitude)

    val lat2 = asin(
        sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing)
    )
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )

    return MapPoint(
        latitude = Math.toDegrees(lat2),
        longitude = ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0
    )
}

private fun mapZoomForCone(radiusMeters: Double): Double {
    return when {
        radiusMeters >= 900.0 -> 14.0
        radiusMeters >= 500.0 -> 15.0
        radiusMeters >= 250.0 -> 16.0
        else -> 17.0
    }
}

private fun hasThroughputLocationPermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private suspend fun loadCurrentThroughputLocation(context: Context): Location? {
    if (!hasThroughputLocationPermission(context)) return null

    return try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        withTimeoutOrNull(8_000L) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
        } ?: fusedLocationClient.lastLocation.await()
            ?: withContext(Dispatchers.IO) { bestLastKnownThroughputLocation(context) }
    } catch (_: Exception) {
        withContext(Dispatchers.IO) { bestLastKnownThroughputLocation(context) }
    }
}

@SuppressLint("MissingPermission")
private fun bestLastKnownThroughputLocation(context: Context): Location? {
    if (!hasThroughputLocationPermission(context)) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return locationManager.getProviders(true)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
}

private enum class ThroughputBlock(val id: String, val prefKey: String) {
    Header(ThroughputPrefs.BLOCK_HEADER, ThroughputPrefs.BLOCK_HEADER_VISIBLE),
    Summary(ThroughputPrefs.BLOCK_SUMMARY, ThroughputPrefs.BLOCK_SUMMARY_VISIBLE),
    Cone(ThroughputPrefs.BLOCK_CONE, ThroughputPrefs.BLOCK_CONE_VISIBLE),
    Controls(ThroughputPrefs.BLOCK_CONTROLS, ThroughputPrefs.BLOCK_CONTROLS_VISIBLE),
    Bands(ThroughputPrefs.BLOCK_BANDS, ThroughputPrefs.BLOCK_BANDS_VISIBLE),
    Assumptions(ThroughputPrefs.BLOCK_ASSUMPTIONS, ThroughputPrefs.BLOCK_ASSUMPTIONS_VISIBLE)
}

private fun normalizeThroughputBlockOrder(order: List<String>): List<ThroughputBlock> {
    val byId = ThroughputBlock.entries.associateBy { it.id }
    return ThroughputPrefs.normalizeBlockOrder(order).mapNotNull { byId[it] }
}

private enum class ThroughputPreset {
    Conservative,
    Standard,
    Maximum,
    Custom
}

private enum class RadioEnvironment(val id: String, val multiplier: Double) {
    Outdoor("outdoor", 1.0),
    Vehicle("vehicle", 0.85),
    Indoor("indoor", 0.65),
    DeepIndoor("deep_indoor", 0.45)
}

private enum class PositionScenario(val id: String, val multiplier: Double) {
    Unknown("unknown", 1.0),
    InCone("in_cone", 1.06),
    TooClose("too_close", 0.75),
    TooFar("too_far", 0.68),
    OutsideBeam("outside_beam", 0.45)
}

private enum class NetworkLoad(val id: String, val downMultiplier: Double, val upMultiplier: Double) {
    Unknown("unknown", 1.0, 1.0),
    Light("light", 0.90, 0.88),
    Medium("medium", 0.68, 0.62),
    Heavy("heavy", 0.46, 0.40),
    Saturated("saturated", 0.28, 0.24)
}

private enum class BackhaulQuality(val id: String, val downMultiplier: Double, val upMultiplier: Double) {
    Unknown("unknown", 1.0, 1.0),
    Fiber("fiber", 1.0, 1.0),
    Radio("radio", 0.84, 0.78),
    Limited("limited", 0.55, 0.48)
}

private enum class LteAggregationMode(val id: String, val maxLteCarriers: Int) {
    Single("single", 1),
    Realistic("realistic", 3),
    Wide("wide", 4)
}

private data class CustomModulationSettings(
    val lteDownIndex: Int = 3,
    val lteUpIndex: Int = 2,
    val nrDownIndex: Int = 3,
    val nrUpIndex: Int = 2,
    val lteRsrpDbm: Float = -95f,
    val lteSinrDb: Float = 15f,
    val nrRsrpDbm: Float = -92f,
    val nrSinrDb: Float = 18f,
    val environment: RadioEnvironment = RadioEnvironment.Outdoor,
    val positionScenario: PositionScenario = PositionScenario.Unknown,
    val networkLoad: NetworkLoad = NetworkLoad.Unknown,
    val backhaul: BackhaulQuality = BackhaulQuality.Unknown,
    val lteAggregation: LteAggregationMode = LteAggregationMode.Realistic,
    val receiverHeightMeters: Float = DEFAULT_RECEIVER_HEIGHT_METERS,
    val selectedLatitude: Double? = null,
    val selectedLongitude: Double? = null
)

private data class CustomThroughputMultipliers(
    val down: Double = 1.0,
    val up: Double = 1.0
)

private fun radioEnvironmentFromPreference(raw: String?): RadioEnvironment =
    RadioEnvironment.entries.firstOrNull { it.id == raw } ?: RadioEnvironment.Outdoor

private fun positionScenarioFromPreference(raw: String?): PositionScenario =
    PositionScenario.entries.firstOrNull { it.id == raw } ?: PositionScenario.Unknown

private fun networkLoadFromPreference(raw: String?): NetworkLoad =
    NetworkLoad.entries.firstOrNull { it.id == raw } ?: NetworkLoad.Unknown

private fun backhaulQualityFromPreference(raw: String?): BackhaulQuality =
    BackhaulQuality.entries.firstOrNull { it.id == raw } ?: BackhaulQuality.Unknown

private fun lteAggregationModeFromPreference(raw: String?): LteAggregationMode =
    LteAggregationMode.entries.firstOrNull { it.id == raw } ?: LteAggregationMode.Realistic

private data class ModulationOption(
    val label: String,
    val modulationOrder: Int
)

private val lteDownModulationOptions = listOf(
    ModulationOption("QPSK", 2),
    ModulationOption("16-QAM", 4),
    ModulationOption("64-QAM", 6),
    ModulationOption("256-QAM", 8)
)

private val lteUpModulationOptions = listOf(
    ModulationOption("QPSK", 2),
    ModulationOption("16-QAM", 4),
    ModulationOption("64-QAM", 6),
    ModulationOption("256-QAM", 8)
)

private val nrDownModulationOptions = listOf(
    ModulationOption("QPSK", 2),
    ModulationOption("16-QAM", 4),
    ModulationOption("64-QAM", 6),
    ModulationOption("256-QAM", 8)
)

private val nrUpModulationOptions = listOf(
    ModulationOption("QPSK", 2),
    ModulationOption("16-QAM", 4),
    ModulationOption("64-QAM", 6),
    ModulationOption("256-QAM", 8)
)

private data class ThroughputSiteData(
    val site: LocalisationEntity?,
    val physique: PhysiqueEntity?,
    val technique: TechniqueEntity?
)

private data class MapPoint(
    val latitude: Double,
    val longitude: Double
)

private data class PositionAnalysis(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val scenario: PositionScenario,
    val nearestAzimuthDegrees: Double?,
    val azimuthDeltaDegrees: Double?,
    val isInsideAzimuth: Boolean?,
    val coneDistance: ConeDistance?
)

private data class ThroughputResult(
    val bands: List<ThroughputBandResult>,
    val warnings: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val confidenceScore: Int = 35,
    val calculationVersion: String = fr.geotower.radio.THROUGHPUT_CALCULATION_VERSION,
    val sourceSummary: String = ""
) {
    val includedBands: List<ThroughputBandResult>
        get() = bands.filter { it.isIncluded }
    val downAggregatedBands: List<ThroughputBandResult>
        get() = includedBands.filter { it.downAggregationExcludedReason == null }
    val totalDownMbps: Double
        get() = downAggregatedBands.sumOf { it.downMbps }
    val totalUpMbps: Double
        get() = downAggregatedBands
            .sortedByDescending { it.upMbps }
            .take(MAX_FR_UPLINK_AGGREGATED_CARRIERS)
            .sumOf { it.upMbps }
    val strongAzimuths: List<Double>
        get() = includedBands.flatMap { it.azimuths }.distinctBy { it.roundToInt() }
    val coneDistance: ConeDistance?
        get() {
            val distances = includedBands.mapNotNull { it.coneDistance }
            if (distances.isEmpty()) return null
            return ConeDistance(
                centerMeters = distances.map { it.centerMeters }.average(),
                nearMeters = distances.minOf { it.nearMeters },
                farMeters = distances.maxOf { it.farMeters }
            )
        }
}

private data class ThroughputBandResult(
    val key: String,
    val label: String,
    val generation: Int,
    val frequencyMHz: Int,
    val frequencyDetails: String,
    val modulationLabel: String,
    val bandwidthMHz: Double,
    val bandwidthIsEstimated: Boolean,
    val coneDistance: ConeDistance?,
    val azimuths: List<Double>,
    val status: String,
    val downMbps: Double,
    val upMbps: Double,
    val isIncluded: Boolean,
    val excludedReason: String?,
    val downAggregationExcludedReason: String? = null
)
