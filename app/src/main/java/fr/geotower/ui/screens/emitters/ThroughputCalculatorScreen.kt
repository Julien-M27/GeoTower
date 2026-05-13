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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.radio.MobileOperator
import fr.geotower.radio.RadioTechnology
import fr.geotower.radio.RadioThroughputEngine
import fr.geotower.radio.RatAssumptions
import fr.geotower.radio.SiteRadioSystem
import fr.geotower.radio.ThroughputProfile
import fr.geotower.radio.ThroughputProfiles
import fr.geotower.ui.components.FreqBand
import fr.geotower.ui.components.MiniMapConeOverlayData
import fr.geotower.ui.components.MiniMapStrongPoint
import fr.geotower.ui.components.SharedMiniMapCard
import fr.geotower.ui.components.ThroughputBandwidth
import fr.geotower.ui.components.estimateThroughputConeDistance
import fr.geotower.ui.components.extractThroughputAzimuths
import fr.geotower.ui.components.extractThroughputPanelHeightMeters
import fr.geotower.ui.components.formatThroughputDistanceMeters
import fr.geotower.ui.components.formatThroughputMbps
import fr.geotower.ui.components.parseAndSortFrequencies
import fr.geotower.ui.components.resolveThroughputBandwidth
import fr.geotower.ui.components.siteRadioStatusFromBandStatus
import fr.geotower.ui.components.throughputBandKey
import fr.geotower.ui.components.throughputBandLabel
import fr.geotower.ui.components.ThroughputConeDistance as ConeDistance
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PANEL_AZIMUTH_HALF_BEAM_DEGREES = 45.0
private const val MAX_FR_UPLINK_AGGREGATED_CARRIERS = 2
private val LTE_LOW_BAND_MHZ = setOf(700, 800, 900)
private const val THROUGHPUT_BLOCK_ORDER_PREF = "page_throughput_order"
private const val DEFAULT_THROUGHPUT_BLOCK_ORDER = "header,summary,cone,controls,bands,assumptions"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThroughputCalculatorScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: String,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
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
    var selectedPreset by remember { mutableStateOf(presetFromPreference(prefs.getString("throughput_default_preset", "conservative"))) }
    var customSettings by remember {
        mutableStateOf(
            CustomModulationSettings(
                lteDownIndex = prefs.getInt("throughput_custom_lte_down", 3).coerceIn(0, lteDownModulationOptions.lastIndex),
                lteUpIndex = prefs.getInt("throughput_custom_lte_up", 2).coerceIn(0, lteUpModulationOptions.lastIndex),
                nrDownIndex = prefs.getInt("throughput_custom_nr_down", 3).coerceIn(0, nrDownModulationOptions.lastIndex),
                nrUpIndex = prefs.getInt("throughput_custom_nr_up", 2).coerceIn(0, nrUpModulationOptions.lastIndex),
                lteRsrpDbm = prefs.getFloat("throughput_custom_lte_rsrp", -95f),
                lteSinrDb = prefs.getFloat("throughput_custom_lte_sinr", 15f),
                nrRsrpDbm = prefs.getFloat("throughput_custom_nr_rsrp", -92f),
                nrSinrDb = prefs.getFloat("throughput_custom_nr_sinr", 18f),
                environment = radioEnvironmentFromPreference(prefs.getString("throughput_custom_environment", null)),
                positionScenario = positionScenarioFromPreference(prefs.getString("throughput_custom_position", null)),
                networkLoad = networkLoadFromPreference(prefs.getString("throughput_custom_network_load", null)),
                backhaul = backhaulQualityFromPreference(prefs.getString("throughput_custom_backhaul", null)),
                lteAggregation = lteAggregationModeFromPreference(prefs.getString("throughput_custom_lte_aggregation", null)),
                selectedLatitude = prefs.getString("throughput_custom_selected_lat", null)?.toDoubleOrNull(),
                selectedLongitude = prefs.getString("throughput_custom_selected_lon", null)?.toDoubleOrNull()
            )
        )
    }
    fun updateCustomSettings(newSettings: CustomModulationSettings) {
        customSettings = newSettings
        val editor = prefs.edit()
            .putInt("throughput_custom_lte_down", newSettings.lteDownIndex)
            .putInt("throughput_custom_lte_up", newSettings.lteUpIndex)
            .putInt("throughput_custom_nr_down", newSettings.nrDownIndex)
            .putInt("throughput_custom_nr_up", newSettings.nrUpIndex)
            .putFloat("throughput_custom_lte_rsrp", newSettings.lteRsrpDbm)
            .putFloat("throughput_custom_lte_sinr", newSettings.lteSinrDb)
            .putFloat("throughput_custom_nr_rsrp", newSettings.nrRsrpDbm)
            .putFloat("throughput_custom_nr_sinr", newSettings.nrSinrDb)
            .putString("throughput_custom_environment", newSettings.environment.id)
            .putString("throughput_custom_position", newSettings.positionScenario.id)
            .putString("throughput_custom_network_load", newSettings.networkLoad.id)
            .putString("throughput_custom_backhaul", newSettings.backhaul.id)
            .putString("throughput_custom_lte_aggregation", newSettings.lteAggregation.id)
            .remove("throughput_custom_device")
        if (newSettings.selectedLatitude != null && newSettings.selectedLongitude != null) {
            editor
                .putString("throughput_custom_selected_lat", newSettings.selectedLatitude.toString())
                .putString("throughput_custom_selected_lon", newSettings.selectedLongitude.toString())
        } else {
            editor
                .remove("throughput_custom_selected_lat")
                .remove("throughput_custom_selected_lon")
        }
        editor.apply()
    }
    var enabledBandKeys by remember(antennaId) { mutableStateOf<Set<String>>(emptySet()) }
    var bandKeysInitialized by remember(antennaId) { mutableStateOf(false) }
    var include4G by remember { mutableStateOf(prefs.getBoolean("throughput_include_4g", true)) }
    var include5G by remember { mutableStateOf(prefs.getBoolean("throughput_include_5g", true)) }
    var includePlanned by remember { mutableStateOf(prefs.getBoolean("throughput_include_planned", false)) }
    val throughputBlockOrder = remember(prefs) {
        normalizeThroughputBlockOrder(
            prefs.getString(THROUGHPUT_BLOCK_ORDER_PREF, DEFAULT_THROUGHPUT_BLOCK_ORDER)
                ?.split(",")
                .orEmpty()
        )
    }
    val visibleThroughputBlocks = remember(prefs) {
        ThroughputBlock.entries.associateWith { block ->
            prefs.getBoolean(block.prefKey, true)
        }
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

    val txtUnknown = AppStrings.unknown
    val txtAzimuthNotSpecified = AppStrings.azimuthNotSpecified
    val rawFrequencies = remember(site, technique) {
        technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: site?.frequences
    }
    val parsedBands = remember(rawFrequencies, txtUnknown, txtAzimuthNotSpecified) {
        parseAndSortFrequencies(rawFrequencies, txtUnknown, txtAzimuthNotSpecified)
    }
    val availableBandKeys = remember(parsedBands) {
        parsedBands
            .filter { it.gen == 4 || it.gen == 5 }
            .filterNot { isHiddenThroughputBand(it) }
            .map { throughputBandKey(it) }
            .toSet()
    }
    val defaultEnabledBandKeys = remember(parsedBands, prefs) {
        parsedBands
            .filter { it.gen == 4 || it.gen == 5 }
            .filterNot { isHiddenThroughputBand(it) }
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
    val result = remember(parsedBands, site?.operateur, selectedPreset, customSettings, include4G, include5G, includePlanned, supportHeightMeters, effectiveEnabledBandKeys) {
        calculateThroughput(
            bands = parsedBands,
            operatorName = site?.operateur,
            preset = selectedPreset,
            customSettings = customSettings,
            include4G = include4G,
            include5G = include5G,
            includePlanned = includePlanned,
            enabledBandKeys = effectiveEnabledBandKeys,
            supportHeightMeters = supportHeightMeters
        )
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mainBgColor)
                    .padding(top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { handleBackNavigation() },
                    enabled = isSplitScreen || !safeBackNavigation.isLocked,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppStrings.back, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = AppStrings.throughputCalculatorTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { navController.navigate("settings?section=throughput") },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = AppStrings.pagesCustomizationTitle,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> LoadingPane(padding, mainBgColor)
            site == null -> MessagePane(
                padding = padding,
                mainBgColor = mainBgColor,
                message = AppStrings.throughputNoSite
            )
            else -> ThroughputContent(
                padding = padding,
                site = site!!,
                physique = physique,
                mainBgColor = mainBgColor,
                cardBgColor = cardBgColor,
                blockShape = blockShape,
                selectedPreset = selectedPreset,
                onPresetChange = { selectedPreset = it },
                customSettings = customSettings,
                onCustomSettingsChange = ::updateCustomSettings,
                useOneUi = useOneUiDesign,
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
                visibleBlocks = visibleThroughputBlocks
            )
        }
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
        LoadingIndicator()
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
    selectedPreset: ThroughputPreset,
    onPresetChange: (ThroughputPreset) -> Unit,
    customSettings: CustomModulationSettings,
    onCustomSettingsChange: (CustomModulationSettings) -> Unit,
    useOneUi: Boolean,
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
    visibleBlocks: Map<ThroughputBlock, Boolean>
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(top = padding.calculateTopPadding())
            .fillMaxSize()
            .background(mainBgColor)
            .navigationBarsPadding()
            .throughputFadingEdge(scrollState)
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
                    onCustomSettingsChange = onCustomSettingsChange
                )
                ThroughputBlock.Controls -> ThroughputControlsCard(
                    cardBgColor = cardBgColor,
                    blockShape = blockShape,
                    selectedPreset = selectedPreset,
                    onPresetChange = onPresetChange,
                    customSettings = customSettings,
                    onCustomSettingsChange = onCustomSettingsChange,
                    useOneUi = useOneUi,
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
                                text = AppStrings.throughputNoBands,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        ThroughputBandsCard(result, cardBgColor, blockShape)
                    }
                }
                ThroughputBlock.Assumptions -> ThroughputAssumptionsCard(selectedPreset, result, cardBgColor, blockShape)
            }
        }
        Spacer(Modifier.height(8.dp))
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
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OperatorLogoOrFallback(
                operatorName = site.operateur,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = site.operateur ?: AppStrings.unknown,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.throughputHeaderSite(site.idAnfr, supportHeightLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OperatorLogoOrFallback(operatorName: String?, modifier: Modifier = Modifier) {
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
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
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
                    text = AppStrings.throughputEstimatedRadioTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ThroughputMetric(
                    label = AppStrings.throughputDownloadLabel,
                    value = formatThroughputMbps(result.totalDownMbps),
                    modifier = Modifier.weight(1f)
                )
                ThroughputMetric(
                    label = AppStrings.throughputPhoneUploadLabel,
                    value = formatThroughputMbps(result.totalUpMbps),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = AppStrings.throughputSummaryUploadNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AppStrings.throughputIncludedBandsCount(result.includedBands.size, result.bands.size),
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
    onCustomSettingsChange: ((CustomModulationSettings) -> Unit)? = null
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
    val currentPositionAppliedText = AppStrings.throughputPositionCurrentApplied
    val mapPointAppliedText = AppStrings.throughputPositionMapPointApplied
    val permissionDeniedText = AppStrings.throughputPositionPermissionDenied
    val unavailableText = AppStrings.throughputPositionUnavailable
    val tapMapText = AppStrings.throughputTapMapToChoose
    val locatingText = AppStrings.throughputPositionLocating
    val pureTheoreticalText = AppStrings.throughputPositionCleared

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
                text = AppStrings.throughputEstimatedOptimalDistanceTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (coneDistance == null) {
                Text(
                    text = AppStrings.throughputConeHeightUnavailable,
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
                    text = AppStrings.throughputMainZoneEstimated(
                        formatThroughputDistanceMeters(coneDistance.nearMeters),
                        formatThroughputDistanceMeters(coneDistance.farMeters)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = AppStrings.throughputConeAssumption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (coneMapOverlay != null) {
                    Spacer(Modifier.height(4.dp))
                    if (customSettings != null && onCustomSettingsChange != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Text(
                            text = AppStrings.throughputCustomPositionTitle,
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
                                Text(if (isResolvingPosition) locatingText else AppStrings.throughputUseCurrentPosition)
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
                                Text(AppStrings.throughputChooseMapPoint)
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
                                    Text(AppStrings.throughputClearPosition)
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
                        onMapReady = {},
                        focusOperator = site.operateur,
                        coneOverlay = coneMapOverlay,
                        initialZoom = mapZoomForCone(coneDistance.centerMeters),
                        onMapTap = mapTapHandler,
                        allowGestures = customSettings != null,
                        fitSelectedPointRequest = mapFitRequest
                    )
                    Text(
                        text = AppStrings.throughputConeMapExplanation,
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
            text = AppStrings.throughputPositionNoSelection,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = AppStrings.throughputCustomSelectedPosition(
                AppStrings.throughputPositionScenarioLabel(scenario.id)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val nearestAzimuth = analysis.nearestAzimuthDegrees?.let { formatDegrees(it) }
    val deltaDegrees = analysis.azimuthDeltaDegrees?.roundToInt()
    val azimuthText = when (analysis.isInsideAzimuth) {
        true -> AppStrings.throughputPositionAzimuthInside(
            nearestAzimuth.orEmpty(),
            deltaDegrees ?: 0
        )
        false -> AppStrings.throughputPositionAzimuthOutside(
            nearestAzimuth.orEmpty(),
            deltaDegrees ?: 0
        )
        null -> AppStrings.throughputPositionAzimuthUnknown
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = AppStrings.throughputCustomSelectedPosition(
                AppStrings.throughputPositionScenarioLabel(analysis.scenario.id)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = AppStrings.throughputPositionDistance(formatThroughputDistanceMeters(analysis.distanceMeters)),
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
                AppStrings.throughputPositionCone(
                    center = formatThroughputDistanceMeters(cone.centerMeters),
                    near = formatThroughputDistanceMeters(cone.nearMeters),
                    far = formatThroughputDistanceMeters(cone.farMeters)
                )
            } ?: AppStrings.throughputPositionConeUnavailable,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThroughputControlsCard(
    cardBgColor: Color,
    blockShape: Shape,
    selectedPreset: ThroughputPreset,
    onPresetChange: (ThroughputPreset) -> Unit,
    customSettings: CustomModulationSettings,
    onCustomSettingsChange: (CustomModulationSettings) -> Unit,
    useOneUi: Boolean,
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
                text = AppStrings.throughputRadioAssumption,
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
                ThroughputPreset.values().forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { onPresetChange(preset) },
                        label = { Text(presetLabel(preset)) }
                    )
                }
            }
            if (selectedPreset == ThroughputPreset.Custom) {
                CustomModulationControls(
                    settings = customSettings,
                    onSettingsChange = onCustomSettingsChange,
                    useOneUi = useOneUi
                )
            }
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
                    label = { Text(AppStrings.throughputIncludePlanned) }
                )
            }
            if (bands.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    text = AppStrings.throughputIncludedBandsTitle,
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
            text = AppStrings.throughputCustomModulationTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        ModulationSlider(
            label = AppStrings.throughput4gDownloadLabel,
            options = lteDownModulationOptions,
            selectedIndex = settings.lteDownIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(lteDownIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = AppStrings.throughput4gUploadLabel,
            options = lteUpModulationOptions,
            selectedIndex = settings.lteUpIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(lteUpIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = AppStrings.throughput5gDownloadLabel,
            options = nrDownModulationOptions,
            selectedIndex = settings.nrDownIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(nrDownIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = AppStrings.throughput5gUploadLabel,
            options = nrUpModulationOptions,
            selectedIndex = settings.nrUpIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(nrUpIndex = it)) },
            useOneUi = useOneUi
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Text(
            text = AppStrings.throughputCustomSignalTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = AppStrings.throughputCustomSignalDesc,
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
            title = AppStrings.throughputCustomEnvironmentTitle,
            selectedId = settings.environment.id,
            options = RadioEnvironment.entries.map { it.id },
            optionLabel = { AppStrings.throughputEnvironmentLabel(it) },
            onSelect = { onSettingsChange(settings.copy(environment = radioEnvironmentFromPreference(it))) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Text(
            text = AppStrings.throughputCustomTerminalTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = AppStrings.throughputCustomTerminalDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CustomChoiceRow(
            title = AppStrings.throughputCustomNetworkLoadTitle,
            selectedId = settings.networkLoad.id,
            options = NetworkLoad.entries.map { it.id },
            optionLabel = { AppStrings.throughputNetworkLoadLabel(it) },
            onSelect = { onSettingsChange(settings.copy(networkLoad = networkLoadFromPreference(it))) }
        )
        CustomChoiceRow(
            title = AppStrings.throughputCustomBackhaulTitle,
            selectedId = settings.backhaul.id,
            options = BackhaulQuality.entries.map { it.id },
            optionLabel = { AppStrings.throughputBackhaulLabel(it) },
            onSelect = { onSettingsChange(settings.copy(backhaul = backhaulQualityFromPreference(it))) }
        )
        CustomChoiceRow(
            title = AppStrings.throughputCustomAggregationTitle,
            selectedId = settings.lteAggregation.id,
            options = LteAggregationMode.entries.map { it.id },
            optionLabel = { AppStrings.throughputLteAggregationLabel(it) },
            onSelect = { onSettingsChange(settings.copy(lteAggregation = lteAggregationModeFromPreference(it))) }
        )

        val lteImpact = (customThroughputMultipliers(4, settings).down * 100).roundToInt()
        val nrImpact = (customThroughputMultipliers(5, settings).down * 100).roundToInt()
        Text(
            text = AppStrings.throughputCustomImpact(lteImpact, nrImpact),
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
            text = AppStrings.throughputCustomExplanationTitle,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomExplanationModulationTitle,
            description = AppStrings.throughputCustomExplanationModulationDesc
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomSignalTitle,
            description = AppStrings.throughputCustomExplanationSignalDesc(lteImpactPercent, nrImpactPercent)
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomEnvironmentTitle,
            description = AppStrings.throughputCustomExplanationEnvironmentDesc
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomPositionTitle,
            description = AppStrings.throughputCustomExplanationPositionDesc
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomNetworkLoadTitle,
            description = AppStrings.throughputCustomExplanationNetworkLoadDesc
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomBackhaulTitle,
            description = AppStrings.throughputCustomExplanationBackhaulDesc
        )
        CustomExplanationLine(
            title = AppStrings.throughputCustomAggregationTitle,
            description = AppStrings.throughputCustomExplanationAggregationDesc(maxLteCarriers)
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
    onValueChange: (Float) -> Unit
) {
    val roundedValue = value.roundToInt().coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
    val valueSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val valueFraction = ((roundedValue - valueRange.start) / valueSpan).coerceIn(0f, 1f)
    val tickCount = steps.coerceAtLeast(0) + 2
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label : $roundedValue $unit",
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
                text = AppStrings.throughputFrequenciesAndModulationTitle,
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
    val estimatedText = AppStrings.throughputEstimatedSuffix
    val bandDetail = buildString {
        append(formatBandwidth(band.bandwidthMHz))
        if (band.bandwidthIsEstimated) append(" ").append(estimatedText)
        if (band.status.isNotBlank()) append(" - ").append(band.status)
    }
    val coneLabel = band.coneDistance?.let {
        AppStrings.throughputEstimatedCone(
            formatThroughputDistanceMeters(it.centerMeters),
            formatThroughputDistanceMeters(it.nearMeters),
            formatThroughputDistanceMeters(it.farMeters)
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(band.label, fontWeight = FontWeight.Bold, color = contentColor)
            ThroughputDetailLine(
                label = AppStrings.throughputFrequenciesLabel,
                value = band.frequencyDetails,
                color = contentColor
            )
            ThroughputDetailLine(
                label = AppStrings.throughputModulationAndAntennasLabel,
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
                    text = AppStrings.translateThroughputExcludedReason(band.excludedReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (band.isIncluded && band.downAggregationExcludedReason != null) {
                Text(
                    text = AppStrings.translateThroughputExcludedReason(band.downAggregationExcludedReason),
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
    selectedPreset: ThroughputPreset,
    result: ThroughputResult,
    cardBgColor: Color,
    blockShape: Shape
) {
    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = AppStrings.throughputReadAsEstimateTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.throughputDisclaimer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = presetDescription(selectedPreset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.warnings.isNotEmpty()) {
                Text(
                    text = AppStrings.throughputAttentionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.warnings.take(4).forEach { warning ->
                    Text(
                        text = "- ${AppStrings.translateThroughputWarning(warning)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (result.assumptions.isNotEmpty()) {
                Text(
                    text = AppStrings.throughputCalculationAssumptions(localizedThroughputAssumptions(result.assumptions)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = AppStrings.throughputSources(AppStrings.translateThroughputSourceSummary(result.sourceSummary)),
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
        translated += AppStrings.translateThroughputAssumption(assumption)
    }
    return translated.joinToString(" | ")
}

@Composable
private fun presetLabel(preset: ThroughputPreset): String {
    return when (preset) {
        ThroughputPreset.Conservative -> AppStrings.throughputPresetLabel("conservative")
        ThroughputPreset.Standard -> AppStrings.throughputPresetLabel("standard")
        ThroughputPreset.Maximum -> AppStrings.throughputPresetLabel("ideal")
        ThroughputPreset.Custom -> AppStrings.throughputPresetLabel("custom")
    }
}

@Composable
private fun presetDescription(preset: ThroughputPreset): String {
    return when (preset) {
        ThroughputPreset.Conservative -> AppStrings.throughputPresetDescription("conservative")
        ThroughputPreset.Standard -> AppStrings.throughputPresetDescription("standard")
        ThroughputPreset.Maximum -> AppStrings.throughputPresetDescription("ideal")
        ThroughputPreset.Custom -> AppStrings.throughputPresetDescription("custom")
    }
}

private fun presetFromPreference(raw: String?): ThroughputPreset {
    return when (raw?.lowercase(Locale.ROOT)) {
        "conservative", "prudent" -> ThroughputPreset.Conservative
        "ideal", "maximum" -> ThroughputPreset.Maximum
        "custom" -> ThroughputPreset.Custom
        else -> ThroughputPreset.Standard
    }
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

private fun calculateThroughput(
    bands: List<FreqBand>,
    operatorName: String?,
    preset: ThroughputPreset,
    customSettings: CustomModulationSettings,
    include4G: Boolean,
    include5G: Boolean,
    includePlanned: Boolean,
    enabledBandKeys: Set<String>,
    supportHeightMeters: Double?
): ThroughputResult {
    val operator = MobileOperator.fromLabel(operatorName)
    val engineProfile = engineProfileFor(preset, customSettings)
    val engineResult = if (operator != null) {
        val systems = bands
            .filter { it.gen == 4 || it.gen == 5 }
            .filterNot { isHiddenThroughputBand(it) }
            .map { band ->
                SiteRadioSystem(
                    sourceKey = throughputBandKey(band),
                    supportId = "unknown",
                    operator = operator,
                    technology = if (band.gen == 5) RadioTechnology.NR_5G else RadioTechnology.LTE_4G,
                    bandLabel = band.value.toString(),
                    status = siteRadioStatusFromBandStatus(band.status),
                    azimuthDeg = extractThroughputAzimuths(band).firstOrNull(),
                    supportHeightM = supportHeightMeters,
                    antennaHeightM = extractThroughputPanelHeightMeters(band, supportHeightMeters),
                    lastSeenAt = band.date.takeIf { it.isNotBlank() }
                )
            }
        RadioThroughputEngine.estimate(systems, engineProfile)
    } else {
        null
    }
    val carrierByKey = engineResult?.perCarrierResults.orEmpty().associateBy { it.sourceKey }
    val excludedByKey = engineResult?.excludedCarriers.orEmpty().associateBy { it.sourceKey }

    val calculatedBands = bands
        .filter { it.gen == 4 || it.gen == 5 }
        .filterNot { isHiddenThroughputBand(it) }
        .map { band ->
            val key = throughputBandKey(band)
            val carrierResult = carrierByKey[key]
            val bandwidth = carrierResult?.let {
                ThroughputBandwidth(valueMHz = it.bandwidthMHz, isEstimated = false)
            } ?: resolveThroughputBandwidth(band)
            val isPlanned = band.status.contains("Projet", ignoreCase = true) ||
                band.status.contains("Approuv", ignoreCase = true) ||
                band.status.contains("Planned", ignoreCase = true)
            val generationAllowed = (band.gen == 4 && include4G) || (band.gen == 5 && include5G)
            val bandAllowed = enabledBandKeys.contains(key)
            val engineIncluded = carrierResult?.included == true
            val isIncluded = generationAllowed && bandAllowed && engineIncluded && (includePlanned || !isPlanned)
            val panelHeightMeters = extractThroughputPanelHeightMeters(band, supportHeightMeters)
            val azimuths = extractThroughputAzimuths(band)
            val customMultipliers = throughputMultipliersFor(preset, band.gen, customSettings)
            val excludedReason = when {
                !generationAllowed -> if (band.gen == 5) "5G désactivée" else "4G désactivée"
                !bandAllowed -> "Bande exclue"
                operator == null -> "Opérateur non reconnu pour les allocations Arcep"
                carrierResult == null -> excludedByKey[key]?.reason ?: "Allocation Arcep introuvable"
                !carrierResult.included -> carrierResult.excludedReason
                isPlanned && !includePlanned -> "Bande en projet"
                else -> null
            }

            ThroughputBandResult(
                key = key,
                label = throughputBandLabel(band),
                generation = band.gen,
                frequencyMHz = band.value,
                frequencyDetails = frequencyDetailsLabel(band),
                modulationLabel = carrierResult?.let { modulationLabel(it.dlModulationOrder, it.ulModulationOrder, it.dlMimoLayers, it.ulMimoLayers) }
                    ?: modulationLabel(band.gen, engineProfile),
                bandwidthMHz = bandwidth.valueMHz,
                bandwidthIsEstimated = bandwidth.isEstimated,
                coneDistance = estimateThroughputConeDistance(panelHeightMeters),
                azimuths = azimuths,
                status = band.status,
                downMbps = (carrierResult?.dlMbps ?: 0.0) * customMultipliers.down,
                upMbps = (carrierResult?.ulMbps ?: 0.0) * customMultipliers.up,
                isIncluded = isIncluded,
                excludedReason = excludedReason
            )
        }

    val aggregationAwareBands = applyCarrierAggregationPolicy(calculatedBands, engineProfile)
    val aggregationWarnings = aggregationAwareBands
        .mapNotNull { it.downAggregationExcludedReason }
        .distinct()
    val uplinkAggregationWarning = if (aggregationAwareBands.count { it.isIncluded && it.downAggregationExcludedReason == null && it.upMbps > 0.0 } > MAX_FR_UPLINK_AGGREGATED_CARRIERS) {
        listOf("Le débit montant est limité aux deux meilleures fréquences agrégées, une hypothèse plus réaliste pour les réseaux mobiles en France.")
    } else {
        emptyList()
    }

    return ThroughputResult(
        bands = aggregationAwareBands,
        warnings = (engineResult?.warnings.orEmpty() + aggregationWarnings + uplinkAggregationWarning).distinct(),
        assumptions = engineResult?.assumptions.orEmpty(),
        confidenceScore = engineResult?.confidenceScore ?: 35,
        calculationVersion = engineResult?.calculationVersion ?: fr.geotower.radio.THROUGHPUT_CALCULATION_VERSION,
        sourceSummary = engineResult?.sourceSummary ?: "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, 3GPP pour le modèle radio."
    )
}

private fun frequencyDetailsLabel(band: FreqBand): String {
    val detailed = band.rawFreq.substringAfter(":", "").trim()
    return detailed.takeIf { it.isNotBlank() } ?: if (band.value > 0) "${band.value} MHz" else band.rawFreq
}

private fun isThroughputBandEnabledByDefault(
    band: FreqBand,
    prefs: SharedPreferences
): Boolean {
    if (band.value <= 0) return true
    val generationPrefix = when (band.gen) {
        4 -> "4g"
        5 -> "5g"
        else -> return true
    }
    return prefs.getBoolean("throughput_band_${generationPrefix}_${band.value}", true)
}

private fun isHiddenThroughputBand(band: FreqBand): Boolean {
    return band.gen == 5 && band.value in setOf(1800, 26000)
}

private fun modulationLabel(gen: Int, profile: ThroughputProfile): String {
    val assumptions = if (gen == 5) profile.nr else profile.lte
    return modulationLabel(
        dlModulationOrder = assumptions.dlModulationOrder,
        ulModulationOrder = assumptions.ulModulationOrder,
        dlLayers = assumptions.dlMimoLayers,
        ulLayers = assumptions.ulMimoLayers
    )
}

private fun modulationLabel(
    dlModulationOrder: Int,
    ulModulationOrder: Int,
    dlLayers: Int,
    ulLayers: Int
): String {
    return "${modulationName(dlModulationOrder)} ${layerLabel(dlLayers)} DL / ${modulationName(ulModulationOrder)} ${layerLabel(ulLayers)} UL"
}

private fun modulationName(modulationOrder: Int): String {
    return when (modulationOrder) {
        2 -> "QPSK"
        4 -> "16-QAM"
        6 -> "64-QAM"
        8 -> "256-QAM"
        10 -> "1024-QAM"
        else -> "$modulationOrder bits/symbole"
    }
}

private fun layerLabel(layers: Int): String {
    return if (layers <= 1) {
        "1 couche"
    } else {
        "MIMO ${layers}x${layers}"
    }
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
        label = "Personnalisé",
        description = "Profil personnalisé : modulations descendantes et montantes choisies dans l'interface, débit montant traité comme celui d'un téléphone.",
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
    val reason = "Agrégation 4G entre bandes basses 700/800/900 MHz limitée : beaucoup de téléphones ne cumulent pas ces porteuses."

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
            reason = "Limite d'agrégation 4G choisie : seules les meilleures porteuses sont comptées."
        )
        .limitAggregatedCarriers(
            generation = 5,
            maxCarriers = maxNrCarriers,
            reason = "Limite d'agrégation 5G du profil : seules les meilleures porteuses sont comptées."
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

private fun Modifier.throughputFadingEdge(scrollState: ScrollState): Modifier {
    if (!AppConfig.isBlurEnabled.value) return this
    val fadeHeight = 80.dp
    return graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val heightPx = fadeHeight.toPx()
        val topAlpha = (scrollState.value / heightPx).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 1f - topAlpha), Color.Black),
                startY = 0f,
                endY = heightPx
            ),
            blendMode = BlendMode.DstIn
        )
        val remainingScroll = scrollState.maxValue - scrollState.value
        val bottomAlpha = (remainingScroll / heightPx).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomAlpha)),
                startY = size.height - heightPx,
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

private enum class ThroughputBlock(val id: String, val prefKey: String) {
    Header("header", "page_throughput_header"),
    Summary("summary", "page_throughput_summary"),
    Cone("cone", "page_throughput_cone"),
    Controls("controls", "page_throughput_controls"),
    Bands("bands", "page_throughput_bands"),
    Assumptions("assumptions", "page_throughput_assumptions")
}

private fun normalizeThroughputBlockOrder(order: List<String>): List<ThroughputBlock> {
    val byId = ThroughputBlock.entries.associateBy { it.id }
    val normalized = order.mapNotNull { byId[it.trim()] }.distinct().toMutableList()
    ThroughputBlock.entries.forEach { block ->
        if (!normalized.contains(block)) normalized.add(block)
    }
    return normalized
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

