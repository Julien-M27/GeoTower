package fr.geotower.ui.screens.emitters

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.radio.MobileOperator
import fr.geotower.radio.RadioTechnology
import fr.geotower.radio.RadioThroughputEngine
import fr.geotower.radio.RatAssumptions
import fr.geotower.radio.SiteRadioStatus
import fr.geotower.radio.SiteRadioSystem
import fr.geotower.radio.ThroughputProfile
import fr.geotower.radio.ThroughputProfiles
import fr.geotower.ui.components.FreqBand
import fr.geotower.ui.components.MiniMapConeOverlayData
import fr.geotower.ui.components.MiniMapStrongPoint
import fr.geotower.ui.components.SharedMiniMapCard
import fr.geotower.ui.components.parseAndSortFrequencies
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
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val USER_DEVICE_HEIGHT_METERS = 1.5
private const val NOMINAL_DOWNTILT_DEGREES = 6.0
private const val MIN_DOWNTILT_DEGREES = 4.0
private const val MAX_DOWNTILT_DEGREES = 8.0
private const val MAX_FR_UPLINK_AGGREGATED_CARRIERS = 2
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
                nrUpIndex = prefs.getInt("throughput_custom_nr_up", 2).coerceIn(0, nrUpModulationOptions.lastIndex)
            )
        )
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
            .map { bandKey(it) }
            .toSet()
    }
    val defaultEnabledBandKeys = remember(parsedBands, prefs) {
        parsedBands
            .filter { it.gen == 4 || it.gen == 5 }
            .filterNot { isHiddenThroughputBand(it) }
            .filter { isThroughputBandEnabledByDefault(it, prefs) }
            .map { bandKey(it) }
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
                onCustomSettingsChange = { customSettings = it },
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
                ThroughputBlock.Cone -> ThroughputConeCard(site, result, cardBgColor, blockShape)
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
                    text = AppStrings.get(
                        "Site ${site.idAnfr}${supportHeightLabel?.let { " - support $it" } ?: ""}",
                        "Site ${site.idAnfr}${supportHeightLabel?.let { " - support $it" } ?: ""}",
                        "Site ${site.idAnfr}${supportHeightLabel?.let { " - suporte $it" } ?: ""}"
                    ),
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
                    text = AppStrings.get("Débit radio théorique estimé", "Estimated theoretical radio throughput", "Débito rádio teórico estimado"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ThroughputMetric(
                    label = AppStrings.get("Descendant", "Download", "Download"),
                    value = formatThroughput(result.totalDownMbps),
                    modifier = Modifier.weight(1f)
                )
                ThroughputMetric(
                    label = AppStrings.get("Montant (téléphone)", "Upload (phone)", "Upload (telemóvel)"),
                    value = formatThroughput(result.totalUpMbps),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = AppStrings.get(
                    "Le montant est pondéré côté terminal : puissance plus faible, moins de MIMO et modulation souvent plus basse qu'en descendant.",
                    "Upload is weighted for a handset: lower transmit power, less MIMO and usually lower modulation than download.",
                    "O upload é ponderado para o telemóvel: menor potência de emissão, menos MIMO e modulação geralmente inferior ao download."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AppStrings.get(
                    "${result.includedBands.size} bande(s) incluse(s) sur ${result.bands.size}",
                    "${result.includedBands.size} band(s) included out of ${result.bands.size}",
                    "${result.includedBands.size} banda(s) incluída(s) de ${result.bands.size}"
                ),
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
    blockShape: Shape
) {
    val coneDistance = result.coneDistance
    val coneMapOverlay = remember(site, result) { buildConeOverlayData(site, result) }

    Card(shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = AppStrings.get("Distance optimale estimée", "Estimated optimal distance", "Distância ótima estimada"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (coneDistance == null) {
                Text(
                    text = AppStrings.get(
                        "Hauteur de panneau/support indisponible : impossible d'estimer la zone principale du cône.",
                        "Panel/support height unavailable: unable to estimate the main cone zone.",
                        "Altura do painel/suporte indisponível: não é possível estimar a zona principal do cone."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = formatDistanceMeters(coneDistance.centerMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = AppStrings.get(
                        "Zone principale estimée : ${formatDistanceMeters(coneDistance.nearMeters)} à ${formatDistanceMeters(coneDistance.farMeters)}",
                        "Estimated main zone: ${formatDistanceMeters(coneDistance.nearMeters)} to ${formatDistanceMeters(coneDistance.farMeters)}",
                        "Zona principal estimada: ${formatDistanceMeters(coneDistance.nearMeters)} a ${formatDistanceMeters(coneDistance.farMeters)}"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = AppStrings.get(
                        "Hypothèse : hauteur panneau/support, mobile à 1,5 m, tilt vertical typique 4°-8° avec un point nominal à 6°.",
                        "Assumption: panel/support height, handset at 1.5 m, typical vertical tilt 4°-8° with a 6° nominal point.",
                        "Hipótese: altura do painel/suporte, telemóvel a 1,5 m, tilt vertical típico 4°-8° com ponto nominal a 6°."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (coneMapOverlay != null) {
                    Spacer(Modifier.height(4.dp))
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
                        initialZoom = mapZoomForCone(coneDistance.centerMeters)
                    )
                    Text(
                        text = AppStrings.get(
                            "Le cercle marque la distance optimale, les points indiquent les axes de panneau où le signal devrait être le plus fort.",
                            "The circle marks the optimal distance; dots show panel axes where signal should be strongest.",
                            "O círculo marca a distância ótima; os pontos mostram os eixos de painel onde o sinal deve ser mais forte."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
                text = AppStrings.get("Hypothèse radio", "Radio assumption", "Hipótese rádio"),
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
                    label = { Text(AppStrings.get("Inclure les projets", "Include planned", "Incluir projetos")) }
                )
            }
            if (bands.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    text = AppStrings.get("Bandes incluses", "Included bands", "Bandas incluidas"),
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
            text = AppStrings.get("Modulation personnalisée", "Custom modulation", "Modulação personalizada"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        ModulationSlider(
            label = AppStrings.get("4G descendant", "4G download", "4G download"),
            options = lteDownModulationOptions,
            selectedIndex = settings.lteDownIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(lteDownIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = AppStrings.get("4G montant", "4G upload", "4G upload"),
            options = lteUpModulationOptions,
            selectedIndex = settings.lteUpIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(lteUpIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = AppStrings.get("5G descendant", "5G download", "5G download"),
            options = nrDownModulationOptions,
            selectedIndex = settings.nrDownIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(nrDownIndex = it)) },
            useOneUi = useOneUi
        )
        ModulationSlider(
            label = AppStrings.get("5G montant", "5G upload", "5G upload"),
            options = nrUpModulationOptions,
            selectedIndex = settings.nrUpIndex,
            onSelectedIndexChange = { onSettingsChange(settings.copy(nrUpIndex = it)) },
            useOneUi = useOneUi
        )
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
                text = AppStrings.get("Fréquences et modulation", "Frequencies and modulation", "Frequências e modulação"),
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
    val metricColor = if (band.isIncluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val estimatedText = AppStrings.get("(estimé)", "(estimated)", "(estimado)")
    val bandDetail = buildString {
        append(formatBandwidth(band.bandwidthMHz))
        if (band.bandwidthIsEstimated) append(" ").append(estimatedText)
        if (band.status.isNotBlank()) append(" - ").append(band.status)
    }
    val coneLabel = band.coneDistance?.let {
        AppStrings.get(
            "Cône estimé : ${formatDistanceMeters(it.centerMeters)} (${formatDistanceMeters(it.nearMeters)}-${formatDistanceMeters(it.farMeters)})",
            "Estimated cone: ${formatDistanceMeters(it.centerMeters)} (${formatDistanceMeters(it.nearMeters)}-${formatDistanceMeters(it.farMeters)})",
            "Cone estimado: ${formatDistanceMeters(it.centerMeters)} (${formatDistanceMeters(it.nearMeters)}-${formatDistanceMeters(it.farMeters)})"
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(band.label, fontWeight = FontWeight.Bold, color = contentColor)
            ThroughputDetailLine(
                label = AppStrings.get("Fréquences", "Frequencies", "Frequências"),
                value = band.frequencyDetails,
                color = contentColor
            )
            ThroughputDetailLine(
                label = AppStrings.get("Modulation et antennes", "Modulation and antennas", "Modulação e antenas"),
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
                Text(band.excludedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatThroughput(band.downMbps), fontWeight = FontWeight.Bold, color = metricColor)
            Text(formatThroughput(band.upMbps), style = MaterialTheme.typography.bodySmall, color = metricColor)
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
                text = AppStrings.get("À lire comme une estimation", "Read as an estimate", "Ler como uma estimativa"),
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
                    text = AppStrings.get("Avertissements", "Warnings", "Avisos"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.warnings.take(4).forEach { warning ->
                    Text(
                        text = "- $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (result.assumptions.isNotEmpty()) {
                Text(
                    text = AppStrings.get("Hypothèses moteur : ${result.assumptions.joinToString(" | ")}", "Engine assumptions: ${result.assumptions.joinToString(" | ")}", "Hipóteses do motor: ${result.assumptions.joinToString(" | ")}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = AppStrings.get("Sources : ${result.sourceSummary}", "Sources: ${result.sourceSummary}", "Fontes: ${result.sourceSummary}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun presetLabel(preset: ThroughputPreset): String {
    return when (preset) {
        ThroughputPreset.Conservative -> AppStrings.get("Prudent", "Conservative", "Prudente")
        ThroughputPreset.Standard -> AppStrings.get("Standard", "Standard", "Padrão")
        ThroughputPreset.Maximum -> AppStrings.get("Idéal", "Ideal", "Ideal")
        ThroughputPreset.Custom -> AppStrings.get("Personnalisé", "Custom", "Personalizado")
    }
}

@Composable
private fun presetDescription(preset: ThroughputPreset): String {
    return when (preset) {
        ThroughputPreset.Conservative -> AppStrings.get(
            "Profil prudent : modulation moyenne, UL fortement limité par la puissance du téléphone.",
            "Conservative profile: average modulation, upload strongly limited by handset transmit power.",
            "Perfil prudente: modulação média, upload fortemente limitado pela potência do telemóvel."
        )
        ThroughputPreset.Standard -> AppStrings.get(
            "Profil standard : 4G MIMO 2x2 et 5G MIMO 4x4 en descendant, montant calculé comme un téléphone réel.",
            "Standard profile: 4G MIMO 2x2 and 5G MIMO 4x4 for download, upload calculated like a real handset.",
            "Perfil padrão: 4G MIMO 2x2 e 5G MIMO 4x4 no download, upload calculado como um telemóvel real."
        )
        ThroughputPreset.Maximum -> AppStrings.get(
            "Profil idéal : très bonnes conditions radio plausibles, mais l'UL reste plafonné côté terminal.",
            "Ideal profile: plausible very good radio conditions, but upload remains capped on the handset side.",
            "Perfil ideal: condições rádio muito boas e plausíveis, mas o upload continua limitado pelo terminal."
        )
        ThroughputPreset.Custom -> AppStrings.get(
            "Profil personnalisé : les modulations DL/UL sont réglées manuellement, avec un UL toujours limité comme un téléphone.",
            "Custom profile: DL/UL modulations are manually tuned, with upload still limited like a handset.",
            "Perfil personalizado: as modulações DL/UL são ajustadas manualmente, com upload ainda limitado como um telemóvel."
        )
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
                    sourceKey = bandKey(band),
                    supportId = "unknown",
                    operator = operator,
                    technology = if (band.gen == 5) RadioTechnology.NR_5G else RadioTechnology.LTE_4G,
                    bandLabel = band.value.toString(),
                    status = siteStatusFromBandStatus(band.status),
                    azimuthDeg = extractAzimuths(band).firstOrNull(),
                    supportHeightM = supportHeightMeters,
                    antennaHeightM = extractPanelHeightMeters(band, supportHeightMeters),
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
            val key = bandKey(band)
            val carrierResult = carrierByKey[key]
            val bandwidth = carrierResult?.let {
                ThroughputBandwidth(valueMHz = it.bandwidthMHz, isEstimated = false)
            } ?: resolveBandwidthMHz(band)
            val isPlanned = band.status.contains("Projet", ignoreCase = true) ||
                band.status.contains("Approuv", ignoreCase = true) ||
                band.status.contains("Planned", ignoreCase = true)
            val generationAllowed = (band.gen == 4 && include4G) || (band.gen == 5 && include5G)
            val bandAllowed = enabledBandKeys.contains(key)
            val engineIncluded = carrierResult?.included == true
            val isIncluded = generationAllowed && bandAllowed && engineIncluded && (includePlanned || !isPlanned)
            val panelHeightMeters = extractPanelHeightMeters(band, supportHeightMeters)
            val azimuths = extractAzimuths(band)
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
                label = bandLabel(band),
                generation = band.gen,
                frequencyDetails = frequencyDetailsLabel(band),
                modulationLabel = carrierResult?.let { modulationLabel(it.dlModulationOrder, it.ulModulationOrder, it.dlMimoLayers, it.ulMimoLayers) }
                    ?: modulationLabel(band.gen, engineProfile),
                bandwidthMHz = bandwidth.valueMHz,
                bandwidthIsEstimated = bandwidth.isEstimated,
                coneDistance = estimateConeDistance(panelHeightMeters),
                azimuths = azimuths,
                status = band.status,
                downMbps = carrierResult?.dlMbps ?: 0.0,
                upMbps = carrierResult?.ulMbps ?: 0.0,
                isIncluded = isIncluded,
                excludedReason = excludedReason
            )
        }

    val uplinkAggregationWarning = if (calculatedBands.count { it.isIncluded && it.upMbps > 0.0 } > MAX_FR_UPLINK_AGGREGATED_CARRIERS) {
        listOf("Upload limite aux 2 meilleures frequences agregees, contrainte retenue pour les reseaux mobiles en France.")
    } else {
        emptyList()
    }

    return ThroughputResult(
        bands = calculatedBands,
        warnings = (engineResult?.warnings.orEmpty() + uplinkAggregationWarning).distinct(),
        assumptions = engineResult?.assumptions.orEmpty(),
        confidenceScore = engineResult?.confidenceScore ?: 35,
        calculationVersion = engineResult?.calculationVersion ?: fr.geotower.radio.THROUGHPUT_CALCULATION_VERSION,
        sourceSummary = engineResult?.sourceSummary ?: "ANFR/data.gouv pour les frequences declarees, Arcep pour les allocations operateur, 3GPP pour le modele radio."
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
        description = "Profil personnalisé : modulations DL/UL choisies dans l'interface, UL traité comme un téléphone.",
        lte = RatAssumptions(
            dlModulationOrder = lteDown.modulationOrder,
            ulModulationOrder = lteUp.modulationOrder,
            dlMimoLayers = 2,
            ulMimoLayers = 1,
            maxCaComponents = 5
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

private fun siteStatusFromBandStatus(status: String): SiteRadioStatus {
    val normalized = status.lowercase(Locale.ROOT)
    return when {
        normalized.contains("commercial") || normalized.contains("ouvert") -> SiteRadioStatus.COMMERCIAL_OPEN
        normalized.contains("en service") || normalized.contains("service") -> SiteRadioStatus.IN_SERVICE
        normalized.contains("techniquement") || normalized.contains("operationnel") || normalized.contains("opérationnel") -> SiteRadioStatus.TECHNICALLY_OPERATIONAL
        normalized.contains("autor") || normalized.contains("approuv") || normalized.contains("projet") -> SiteRadioStatus.AUTHORIZED
        else -> SiteRadioStatus.UNKNOWN
    }
}

private fun resolveBandwidthMHz(band: FreqBand): ThroughputBandwidth {
    val ranges = frequencyRangeRegex.findAll(band.rawFreq)
        .mapNotNull { match ->
            val start = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            val end = match.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (start == null || end == null) {
                null
            } else {
                val unit = match.groupValues.getOrNull(3).orEmpty()
                normalizeRangeWidthToMHz(abs(end - start), unit)
            }
        }
        .filter { it > 0.0 }
        .toList()

    if (ranges.isNotEmpty()) {
        val value = if (ranges.size > 1 && isLikelyFddBand(band)) {
            ranges.maxOrNull() ?: ranges.sum()
        } else {
            ranges.sum()
        }
        return ThroughputBandwidth(valueMHz = value.coerceAtLeast(1.0), isEstimated = false)
    }

    return ThroughputBandwidth(valueMHz = defaultBandwidthMHz(band.gen, band.value), isEstimated = true)
}

private fun normalizeRangeWidthToMHz(width: Double, unit: String): Double {
    val lowerUnit = unit.lowercase(Locale.ROOT)
    return when {
        lowerUnit.contains("ghz") -> width * 1000.0
        lowerUnit.contains("khz") -> width / 1000.0
        else -> width
    }
}

private fun isLikelyFddBand(band: FreqBand): Boolean {
    return band.value !in setOf(3500, 26000)
}

private fun defaultBandwidthMHz(gen: Int, value: Int): Double {
    return when (gen) {
        4 -> when (value) {
            700, 800, 900, 2100 -> 10.0
            1800 -> 15.0
            2600 -> 20.0
            else -> 10.0
        }
        5 -> when (value) {
            700 -> 10.0
            1800 -> 10.0
            2100 -> 15.0
            3500 -> 70.0
            26000 -> 200.0
            else -> 20.0
        }
        else -> 10.0
    }
}

private fun extractPanelHeightMeters(band: FreqBand, fallbackSupportHeightMeters: Double?): Double? {
    val panelHeights = band.physDetails.flatMap { detail ->
        panelHeightRegex.findAll(detail).mapNotNull { match ->
            match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        }.toList()
    }

    return panelHeights.maxOrNull() ?: fallbackSupportHeightMeters?.takeIf { it > USER_DEVICE_HEIGHT_METERS }
}

private fun extractAzimuths(band: FreqBand): List<Double> {
    return band.physDetails
        .flatMap { detail ->
            azimuthRegex.findAll(detail).mapNotNull { match ->
                match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            }.toList()
        }
        .filter { it in 0.0..360.0 }
        .distinctBy { it.roundToInt() }
}

private fun estimateConeDistance(heightMeters: Double?): ConeDistance? {
    val antennaHeight = heightMeters ?: return null
    val verticalDelta = (antennaHeight - USER_DEVICE_HEIGHT_METERS).coerceAtLeast(1.0)
    val center = verticalDelta / tan(Math.toRadians(NOMINAL_DOWNTILT_DEGREES))
    val near = verticalDelta / tan(Math.toRadians(MAX_DOWNTILT_DEGREES))
    val far = verticalDelta / tan(Math.toRadians(MIN_DOWNTILT_DEGREES))
    return ConeDistance(centerMeters = center, nearMeters = near, farMeters = far)
}

private fun bandLabel(band: FreqBand): String {
    return if (band.value > 0) {
        val base = "${band.gen}G ${band.value} MHz"
        radioBandCode(band.gen, band.value)?.let { "$base ($it)" } ?: base
    } else {
        band.rawFreq.substringBefore(":").ifBlank { "${band.gen}G" }
    }
}

private fun radioBandCode(gen: Int, value: Int): String? {
    return when (gen) {
        5 -> when (value) {
            700 -> "N28"
            800 -> "N20"
            900 -> "N8"
            1800 -> "N3"
            2100 -> "N1"
            2600 -> "N7"
            3500 -> "N78"
            26000 -> "N258"
            else -> null
        }
        4 -> when (value) {
            700 -> "B28"
            800 -> "B20"
            900 -> "B8"
            1800 -> "B3"
            2100 -> "B1"
            2600 -> "B7"
            3500 -> "B42"
            else -> null
        }
        3 -> when (value) {
            900 -> "B8"
            2100 -> "B1"
            else -> null
        }
        2 -> when (value) {
            900 -> "GSM 900"
            1800 -> "DCS 1800"
            else -> null
        }
        else -> null
    }
}

private fun formatThroughput(valueMbps: Double): String {
    return if (valueMbps >= 1000.0) {
        String.format(Locale.US, "%.2f Gbit/s", valueMbps / 1000.0)
    } else {
        String.format(Locale.US, "%.0f Mbit/s", valueMbps)
    }
}

private fun formatBandwidth(valueMHz: Double): String {
    return if (valueMHz % 1.0 == 0.0) {
        "${valueMHz.toInt()} MHz"
    } else {
        String.format(Locale.US, "%.1f MHz", valueMHz)
    }
}

private fun formatDistanceMeters(valueMeters: Double): String {
    if (AppConfig.distanceUnit.intValue == 1) {
        val miles = valueMeters / 1609.34
        return if (miles < 0.1) {
            "${(valueMeters * 3.28084).roundToInt()} ft"
        } else {
            String.format(Locale.US, "%.2f mi", miles)
        }
    }

    return if (valueMeters >= 1000.0) {
        String.format(Locale.US, "%.2f km", valueMeters / 1000.0)
    } else {
        "${valueMeters.toInt()} m"
    }
}

private fun formatHeightMeters(valueMeters: Double): String {
    return if (AppConfig.distanceUnit.intValue == 1) {
        "${(valueMeters * 3.28084).roundToInt()} ft"
    } else {
        "${formatNumber(valueMeters)} m"
    }
}

private fun formatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

private fun bandKey(band: FreqBand): String {
    return "${band.gen}:${band.value}:${band.rawFreq.substringBefore(":").trim()}"
}

private fun buildConeOverlayData(site: LocalisationEntity, result: ThroughputResult): MiniMapConeOverlayData? {
    val coneDistance = result.coneDistance ?: return null
    val azimuths = result.strongAzimuths.ifEmpty {
        site.azimuts
            ?.split(",")
            ?.mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            ?.filter { it in 0.0..360.0 }
            .orEmpty()
    }.distinctBy { it.roundToInt() }

    val strongPoints = azimuths.take(12).map { azimuth ->
        val point = destinationPoint(site.latitude, site.longitude, coneDistance.centerMeters, azimuth)
        MiniMapStrongPoint(latitude = point.latitude, longitude = point.longitude)
    }

    return MiniMapConeOverlayData(
        centerLat = site.latitude,
        centerLon = site.longitude,
        radiusMeters = coneDistance.centerMeters,
        strongPoints = strongPoints
    )
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

private val frequencyRangeRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")
private val panelHeightRegex = Regex("""\(([0-9]+(?:[.,][0-9]+)?)\s*m\)""", RegexOption.IGNORE_CASE)
private val azimuthRegex = Regex("""([0-9]{1,3}(?:[.,][0-9]+)?)\s*(?:\u00B0|\u00C2\u00B0)""")

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

private data class CustomModulationSettings(
    val lteDownIndex: Int = 3,
    val lteUpIndex: Int = 2,
    val nrDownIndex: Int = 3,
    val nrUpIndex: Int = 2
)

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

private data class ThroughputBandwidth(
    val valueMHz: Double,
    val isEstimated: Boolean
)

private data class MapPoint(
    val latitude: Double,
    val longitude: Double
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
    val totalDownMbps: Double
        get() = includedBands.sumOf { it.downMbps }
    val totalUpMbps: Double
        get() = includedBands
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
    val excludedReason: String?
)

private data class ConeDistance(
    val centerMeters: Double,
    val nearMeters: Double,
    val farMeters: Double
)
