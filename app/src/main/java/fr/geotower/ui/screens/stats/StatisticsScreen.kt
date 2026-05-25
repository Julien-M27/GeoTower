package fr.geotower.ui.screens.stats

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.ActiveSupportRadioCounts
import fr.geotower.data.db.RadioStatRow
import fr.geotower.data.db.WeeklyRadioStatRow
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.StatsSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.StatsDisplayMode
import fr.geotower.utils.StatsPreferences
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import java.util.Locale

private const val STATS_FREQUENCIES_ROUTE = "stats/frequencies"
private const val CATEGORY_SUPPORT = "support"
private const val CATEGORY_TECH = "tech"
private const val CATEGORY_BAND = "band"
private const val ITEM_ALL = "ALL"

private data class StatisticsData(
    val supports: List<OperatorStatValue>,
    val supports2G: List<OperatorStatValue>,
    val supports3G: List<OperatorStatValue>,
    val supports4G: List<OperatorStatValue>,
    val supports5G: List<OperatorStatValue>,
    val weeklyByItem: Map<String, List<WeeklyStatValue>>
)

private data class FrequencyBandSpec(
    val frequencyId: String,
    val label: String,
    val mask: Int,
    val itemKey: String
)

private data class FrequencyBandStats(
    val frequencyId: String,
    val title: String,
    val description: String,
    val data: List<OperatorStatValue>,
    val weeklyData: List<WeeklyStatValue>
)

private data class StatsCardSpec(
    val blockId: String,
    val title: String,
    val description: String,
    val data: List<OperatorStatValue>,
    val weeklyData: List<WeeklyStatValue>,
    val onClick: (() -> Unit)? = null
)

private data class StatCount(
    val totalCount: Int,
    val activeCount: Int
) {
    fun normalized(): StatCount = copy(activeCount = activeCount.coerceIn(0, totalCount.coerceAtLeast(0)))
}

private data class OperatorStatValue(
    val name: String,
    val totalCount: Int,
    val activeCount: Int
)

private data class WeeklyStatValue(
    val weekKey: String,
    val weekStart: String?,
    val sourceDate: String?,
    val totalCount: Int,
    val activeCount: Int
)

private data class WeeklyStatAccumulator(
    val weekKey: String,
    val weekStart: String?,
    val sourceDate: String?,
    var totalCount: Int,
    var activeCount: Int
)

private object StatisticsScreenCache {
    var data: StatisticsData? = null
}

private object FrequencyStatsScreenCache {
    val dataByTech = mutableMapOf<String, List<FrequencyBandStats>>()
}

private fun statsFrequenciesRoute(tech: String): String = "$STATS_FREQUENCIES_ROUTE/$tech"

private fun normalizeTech(tech: String): String = tech.trim().uppercase(Locale.ROOT)

private fun operatorDisplayOrder(defaultOp: String): List<String> {
    val displayOrder = mutableListOf<String>()
    OperatorColors.keyFor(defaultOp)?.let { displayOrder.add(it) }
    OperatorColors.orderedKeys.forEach { if (!displayOrder.contains(it)) displayOrder.add(it) }
    return displayOrder
}

private fun statsKey(category: String, itemKey: String): String = "$category|$itemKey"

private fun formatOperatorData(map: Map<String, StatCount>, displayOrder: List<String>): List<OperatorStatValue> {
    val rows = displayOrder.map { op ->
        val name = OperatorColors.specForKey(op)?.label ?: op
        val count = (map[op] ?: StatCount(0, 0)).normalized()
        OperatorStatValue(
            name = name,
            totalCount = count.totalCount,
            activeCount = count.activeCount
        )
    }
    return rows.filter { it.totalCount > 0 || it.activeCount > 0 }.ifEmpty { rows }
}

private fun statCount(rows: List<RadioStatRow>, category: String, itemKey: String): StatCount {
    val row = rows.firstOrNull {
        it.category.equals(category, ignoreCase = true) &&
            it.itemKey.equals(itemKey, ignoreCase = true)
    }
    return StatCount(
        totalCount = row?.totalCount ?: 0,
        activeCount = row?.activeCount ?: 0
    ).normalized()
}

private fun aggregateWeeklyStats(
    rows: List<WeeklyRadioStatRow>,
    accumulator: MutableMap<String, MutableMap<String, WeeklyStatAccumulator>>
) {
    rows.forEach { row ->
        val itemStats = accumulator.getOrPut(statsKey(row.category, row.itemKey)) { mutableMapOf() }
        val weekStats = itemStats.getOrPut(row.weekKey) {
            WeeklyStatAccumulator(
                weekKey = row.weekKey,
                weekStart = row.weekStart,
                sourceDate = row.sourceDate,
                totalCount = 0,
                activeCount = 0
            )
        }
        weekStats.totalCount += row.totalCount
        weekStats.activeCount += row.activeCount
    }
}

private fun weeklyAccumulatorToMap(
    accumulator: Map<String, Map<String, WeeklyStatAccumulator>>
): Map<String, List<WeeklyStatValue>> {
    return accumulator.mapValues { (_, weeks) ->
        weeks.values
            .sortedWith(compareBy<WeeklyStatAccumulator> { it.weekStart ?: it.sourceDate ?: it.weekKey }.thenBy { it.weekKey })
            .map {
                val active = it.activeCount.coerceIn(0, it.totalCount.coerceAtLeast(0))
                WeeklyStatValue(
                    weekKey = it.weekKey,
                    weekStart = it.weekStart,
                    sourceDate = it.sourceDate,
                    totalCount = it.totalCount,
                    activeCount = active
                )
            }
    }
}

private fun frequencyBandsForTech(tech: String): List<FrequencyBandSpec> {
    return when (normalizeTech(tech)) {
        "2G" -> listOf(
            FrequencyBandSpec("900", "900 MHz", RadioFilterMasks.BAND_2G_900, "2G|900"),
            FrequencyBandSpec("1800", "1800 MHz", RadioFilterMasks.BAND_2G_1800, "2G|1800")
        )
        "3G" -> listOf(
            FrequencyBandSpec("900", "900 MHz", RadioFilterMasks.BAND_3G_900, "3G|900"),
            FrequencyBandSpec("2100", "2100 MHz", RadioFilterMasks.BAND_3G_2100, "3G|2100")
        )
        "4G" -> listOf(
            FrequencyBandSpec("700", "700 MHz", RadioFilterMasks.BAND_4G_700, "4G|700"),
            FrequencyBandSpec("800", "800 MHz", RadioFilterMasks.BAND_4G_800, "4G|800"),
            FrequencyBandSpec("900", "900 MHz", RadioFilterMasks.BAND_4G_900, "4G|900"),
            FrequencyBandSpec("1800", "1800 MHz", RadioFilterMasks.BAND_4G_1800, "4G|1800"),
            FrequencyBandSpec("2100", "2100 MHz", RadioFilterMasks.BAND_4G_2100, "4G|2100"),
            FrequencyBandSpec("2600", "2600 MHz", RadioFilterMasks.BAND_4G_2600, "4G|2600")
        )
        "5G" -> listOf(
            FrequencyBandSpec("700", "700 MHz", RadioFilterMasks.BAND_5G_700, "5G|700"),
            FrequencyBandSpec("2100", "2100 MHz", RadioFilterMasks.BAND_5G_2100, "5G|2100"),
            FrequencyBandSpec("3500", "3500 MHz", RadioFilterMasks.BAND_5G_3500, "5G|3500"),
            FrequencyBandSpec("26000", "26 GHz", RadioFilterMasks.BAND_5G_26000, "5G|26000")
        )
        else -> emptyList()
    }
}

private fun visibleFrequencyStats(
    prefs: android.content.SharedPreferences,
    tech: String,
    stats: List<FrequencyBandStats>
): List<FrequencyBandStats> {
    val statsByFrequency = stats.associateBy { it.frequencyId }
    return StatsPreferences.statsFrequencyOrder(prefs, tech)
        .mapNotNull { frequencyId -> statsByFrequency[frequencyId] }
        .filter { band -> StatsPreferences.isStatsFrequencyVisible(prefs, tech, band.frequencyId) }
}

private fun frequencyDetailTitle(tech: String): String {
    val normalizedTech = normalizeTech(tech)
    return if (frequencyBandsForTech(normalizedTech).isEmpty()) {
        "Détail des fréquences"
    } else {
        "Détail $normalizedTech"
    }
}

private suspend fun loadStatisticsData(
    repository: AnfrRepository,
    defaultOp: String
): StatisticsData {
    val totalMap = mutableMapOf<String, StatCount>()
    val counts2GMap = mutableMapOf<String, StatCount>()
    val counts3GMap = mutableMapOf<String, StatCount>()
    val counts4GMap = mutableMapOf<String, StatCount>()
    val counts5GMap = mutableMapOf<String, StatCount>()
    val weeklyAccumulator = mutableMapOf<String, MutableMap<String, WeeklyStatAccumulator>>()

    for (operator in OperatorColors.all) {
        val queryNames = operator.aliases.ifEmpty { listOf(operator.key) }
        val currentStats = repository.getCurrentRadioStatsByOperator(queryNames)
        if (currentStats.isNotEmpty()) {
            totalMap[operator.key] = statCount(currentStats, CATEGORY_SUPPORT, ITEM_ALL)
            counts2GMap[operator.key] = statCount(currentStats, CATEGORY_TECH, "2G")
            counts3GMap[operator.key] = statCount(currentStats, CATEGORY_TECH, "3G")
            counts4GMap[operator.key] = statCount(currentStats, CATEGORY_TECH, "4G")
            counts5GMap[operator.key] = statCount(currentStats, CATEGORY_TECH, "5G")
        } else {
            val activeCounts = repository.getActiveSupportRadioCountsByOperator(queryNames)
            totalMap[operator.key] = StatCount(
                totalCount = repository.getUniqueSupportCountByOperator(queryNames),
                activeCount = repository.getActiveUniqueSupportCountByOperator(queryNames)
            )
            counts2GMap[operator.key] = StatCount(
                totalCount = repository.get2GSupportCountByOperator(queryNames),
                activeCount = activeCounts.techCounts["2G"] ?: 0
            )
            counts3GMap[operator.key] = StatCount(
                totalCount = repository.get3GSupportCountByOperator(queryNames),
                activeCount = activeCounts.techCounts["3G"] ?: 0
            )
            counts4GMap[operator.key] = StatCount(
                totalCount = repository.get4GSupportCountByOperator(queryNames),
                activeCount = activeCounts.techCounts["4G"] ?: 0
            )
            counts5GMap[operator.key] = StatCount(
                totalCount = repository.get5GSupportCountByOperator(queryNames),
                activeCount = activeCounts.techCounts["5G"] ?: 0
            )
        }
        aggregateWeeklyStats(repository.getWeeklyRadioStatsByOperator(queryNames), weeklyAccumulator)
    }

    val displayOrder = operatorDisplayOrder(defaultOp)

    return StatisticsData(
        supports = formatOperatorData(totalMap, displayOrder),
        supports2G = formatOperatorData(counts2GMap, displayOrder),
        supports3G = formatOperatorData(counts3GMap, displayOrder),
        supports4G = formatOperatorData(counts4GMap, displayOrder),
        supports5G = formatOperatorData(counts5GMap, displayOrder),
        weeklyByItem = weeklyAccumulatorToMap(weeklyAccumulator)
    )
}

private suspend fun loadFrequencyStatsData(
    repository: AnfrRepository,
    defaultOp: String,
    tech: String
): List<FrequencyBandStats> {
    val normalizedTech = normalizeTech(tech)
    val displayOrder = operatorDisplayOrder(defaultOp)
    val bands = frequencyBandsForTech(normalizedTech)
    val currentRowsByOperator = mutableMapOf<String, List<RadioStatRow>>()
    val activeCountsByOperator = mutableMapOf<String, ActiveSupportRadioCounts>()
    val weeklyAccumulator = mutableMapOf<String, MutableMap<String, WeeklyStatAccumulator>>()

    for (operator in OperatorColors.all) {
        val queryNames = operator.aliases.ifEmpty { listOf(operator.key) }
        val currentStats = repository.getCurrentRadioStatsByOperator(queryNames)
        currentRowsByOperator[operator.key] = currentStats
        if (currentStats.isEmpty()) {
            activeCountsByOperator[operator.key] = repository.getActiveSupportRadioCountsByOperator(queryNames)
        }
        aggregateWeeklyStats(repository.getWeeklyRadioStatsByOperator(queryNames), weeklyAccumulator)
    }
    val weeklyByItem = weeklyAccumulatorToMap(weeklyAccumulator)

    val stats = bands.map { band ->
        val countsMap = mutableMapOf<String, StatCount>()

        for (operator in OperatorColors.all) {
            val queryNames = operator.aliases.ifEmpty { listOf(operator.key) }
            val currentStats = currentRowsByOperator[operator.key].orEmpty()
            countsMap[operator.key] = if (currentStats.isNotEmpty()) {
                statCount(currentStats, CATEGORY_BAND, band.itemKey)
            } else {
                val activeCounts = activeCountsByOperator[operator.key] ?: ActiveSupportRadioCounts(emptyMap(), emptyMap())
                StatCount(
                    totalCount = repository.getSupportCountByOperatorAndBand(queryNames, band.mask),
                    activeCount = activeCounts.bandCounts[band.itemKey] ?: 0
                )
            }
        }

        FrequencyBandStats(
            frequencyId = band.frequencyId,
            title = "$normalizedTech ${band.label}",
            description = "Supports déclarés par opérateur",
            data = formatOperatorData(countsMap, displayOrder),
            weeklyData = weeklyByItem[statsKey(CATEGORY_BAND, band.itemKey)].orEmpty()
        )
    }

    return stats.filter { band -> band.data.any { it.totalCount > 0 || it.activeCount > 0 } }.ifEmpty { stats }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatisticsScreen(navController: NavController, repository: AnfrRepository) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val defaultOp = AppConfig.defaultOperator.value
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }
    val uiStyle = LocalGeoTowerUiStyle.current

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val cachedStats = StatisticsScreenCache.data
    var displayMode by AppConfig.statsDisplayMode
    var showStatsSettingsSheet by remember { mutableStateOf(false) }
    val statsSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // --- ÉTAT DES DONNÉES ---
    var supportCounts by remember { mutableStateOf(cachedStats?.supports ?: emptyList()) }
    var support2GCounts by remember { mutableStateOf(cachedStats?.supports2G ?: emptyList()) }
    var support3GCounts by remember { mutableStateOf(cachedStats?.supports3G ?: emptyList()) }
    var support4GCounts by remember { mutableStateOf(cachedStats?.supports4G ?: emptyList()) }
    var support5GCounts by remember { mutableStateOf(cachedStats?.supports5G ?: emptyList()) }
    var weeklyByItem by remember { mutableStateOf(cachedStats?.weeklyByItem ?: emptyMap()) }
    var isLoading by remember { mutableStateOf(cachedStats == null) }
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")
    val scrollState = rememberScrollState()

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LaunchedEffect(defaultOp) {
        if (StatisticsScreenCache.data == null) {
            isLoading = true
        }

        val refreshedStats = loadStatisticsData(repository, defaultOp)
        if (refreshedStats != StatisticsScreenCache.data) {
            StatisticsScreenCache.data = refreshedStats
            supportCounts = refreshedStats.supports
            support2GCounts = refreshedStats.supports2G
            support3GCounts = refreshedStats.supports3G
            support4GCounts = refreshedStats.supports4G
            support5GCounts = refreshedStats.supports5G
            weeklyByItem = refreshedStats.weeklyByItem
        }
        isLoading = false
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_stats_title),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = mainBgColor,
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    IconButton(onClick = { showStatsSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(mainBgColor)
                .geoTowerFadingEdge(scrollState)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            StatsDisplayModeSelector(
                displayMode = displayMode,
                onDisplayModeChange = { newMode ->
                    displayMode = newMode
                    prefs.edit().putString(StatsPreferences.PREF_DISPLAY_MODE, newMode.storageKey).apply()
                },
                bgColor = cardBgColor
            )

            val cards = listOf(
                StatsCardSpec(
                    blockId = "supports",
                    title = stringResource(R.string.appstrings_stats_supports_title),
                    description = stringResource(R.string.appstrings_stats_supports_desc),
                    data = supportCounts,
                    weeklyData = weeklyByItem[statsKey(CATEGORY_SUPPORT, ITEM_ALL)].orEmpty()
                ),
                StatsCardSpec(
                    blockId = "5G",
                    title = stringResource(R.string.appstrings_stats5_g_title),
                    description = stringResource(R.string.appstrings_stats5_g_desc),
                    data = support5GCounts,
                    weeklyData = weeklyByItem[statsKey(CATEGORY_TECH, "5G")].orEmpty(),
                    onClick = { navController.navigate(statsFrequenciesRoute("5G")) }
                ),
                StatsCardSpec(
                    blockId = "4G",
                    title = stringResource(R.string.appstrings_stats4_g_title),
                    description = stringResource(R.string.appstrings_stats4_g_desc),
                    data = support4GCounts,
                    weeklyData = weeklyByItem[statsKey(CATEGORY_TECH, "4G")].orEmpty(),
                    onClick = { navController.navigate(statsFrequenciesRoute("4G")) }
                ),
                StatsCardSpec(
                    blockId = "3G",
                    title = stringResource(R.string.appstrings_stats3_g_title),
                    description = stringResource(R.string.appstrings_stats3_g_desc),
                    data = support3GCounts,
                    weeklyData = weeklyByItem[statsKey(CATEGORY_TECH, "3G")].orEmpty(),
                    onClick = { navController.navigate(statsFrequenciesRoute("3G")) }
                ),
                StatsCardSpec(
                    blockId = "2G",
                    title = stringResource(R.string.appstrings_stats2_g_title),
                    description = stringResource(R.string.appstrings_stats2_g_desc),
                    data = support2GCounts,
                    weeklyData = weeklyByItem[statsKey(CATEGORY_TECH, "2G")].orEmpty(),
                    onClick = { navController.navigate(statsFrequenciesRoute("2G")) }
                )
            ).associateBy { it.blockId }

            val visibleCards = StatsPreferences.statsBlockOrder(prefs)
                .mapNotNull { blockId -> cards[blockId] }
                .filter { card -> StatsPreferences.isStatsBlockVisible(prefs, card.blockId) }

            visibleCards.forEach { card ->
                Spacer(modifier = Modifier.height(16.dp))
                StatCard(
                    title = card.title,
                    desc = card.description,
                    data = card.data,
                    isLoading = isLoading,
                    bgColor = cardBgColor,
                    displayMode = displayMode,
                    weeklyData = card.weeklyData,
                    onClick = card.onClick
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showStatsSettingsSheet) {
        StatsSettingsSheet(
            onDismiss = { showStatsSettingsSheet = false },
            onBack = { showStatsSettingsSheet = false },
            sheetState = statsSettingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrequencyStatsDetailScreen(navController: NavController, repository: AnfrRepository, tech: String) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val defaultOp = AppConfig.defaultOperator.value
    val normalizedTech = remember(tech) { normalizeTech(tech) }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE) }

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val cachedStats = FrequencyStatsScreenCache.dataByTech[normalizedTech]

    var displayMode by AppConfig.statsDisplayMode
    var frequencyStats by remember(normalizedTech) { mutableStateOf(cachedStats ?: emptyList()) }
    var isLoading by remember(normalizedTech) { mutableStateOf(cachedStats == null) }
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "stats")
    val scrollState = rememberScrollState()

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LaunchedEffect(defaultOp, normalizedTech) {
        if (FrequencyStatsScreenCache.dataByTech[normalizedTech] == null) {
            isLoading = true
        }

        val refreshedStats = loadFrequencyStatsData(repository, defaultOp, normalizedTech)
        if (refreshedStats != FrequencyStatsScreenCache.dataByTech[normalizedTech]) {
            FrequencyStatsScreenCache.dataByTech[normalizedTech] = refreshedStats
            frequencyStats = refreshedStats
        }
        isLoading = false
    }

    fun updateDisplayMode(newMode: StatsDisplayMode) {
        displayMode = newMode
        prefs.edit().putString(StatsPreferences.PREF_DISPLAY_MODE, newMode.storageKey).apply()
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = frequencyDetailTitle(normalizedTech),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = mainBgColor,
                backEnabled = !safeBackNavigation.isLocked
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(mainBgColor)
                .geoTowerFadingEdge(scrollState)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            StatsDisplayModeSelector(
                displayMode = displayMode,
                onDisplayModeChange = ::updateDisplayMode,
                bgColor = cardBgColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            val orderedFrequencyStats = visibleFrequencyStats(prefs, normalizedTech, frequencyStats)

            when {
                isLoading && frequencyStats.isEmpty() -> {
                    StatCard(
                        title = frequencyDetailTitle(normalizedTech),
                        desc = "Chargement du détail par fréquence",
                        data = emptyList(),
                        isLoading = true,
                        bgColor = cardBgColor,
                        displayMode = displayMode
                    )
                }
                orderedFrequencyStats.isEmpty() -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.58f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Aucune fréquence disponible pour $normalizedTech",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    orderedFrequencyStats.forEachIndexed { index, band ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        StatCard(
                            title = band.title,
                            desc = band.description,
                            data = band.data,
                            isLoading = false,
                            bgColor = cardBgColor,
                            displayMode = displayMode,
                            weeklyData = band.weeklyData
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun statsDisplayModeLabel(mode: StatsDisplayMode): String {
    return when (mode) {
        StatsDisplayMode.Sites -> stringResource(R.string.appstrings_sites_label)
        StatsDisplayMode.Active -> stringResource(R.string.appstrings_active_sites_label)
        StatsDisplayMode.Both -> stringResource(R.string.appstrings_active_declared_sites_label)
    }
}

@Composable
private fun StatsDisplayModeSelector(
    displayMode: StatsDisplayMode,
    onDisplayModeChange: (StatsDisplayMode) -> Unit,
    bgColor: Color
) {
    val chipScrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor.copy(alpha = 0.58f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.appstrings_stats_display_mode_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScrollState)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsDisplayMode.values().forEach { mode ->
                    StatsDisplayModeChip(
                        label = statsDisplayModeLabel(mode),
                        selected = displayMode == mode,
                        onClick = { onDisplayModeChange(mode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsDisplayModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
    }
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.clip(shape)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatCard(
    title: String,
    desc: String,
    data: List<OperatorStatValue>,
    isLoading: Boolean,
    bgColor: Color,
    displayMode: StatsDisplayMode,
    weeklyData: List<WeeklyStatValue> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(20.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    val cardColors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.58f))
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(18.dp))
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                SupportBarChart(data = data, displayMode = displayMode)
                WeeklyTrendChart(data = weeklyData, displayMode = displayMode)
            }
        }
    }

    val clickAction = onClick
    if (clickAction != null && !isLoading) {
        Card(
            onClick = clickAction,
            shape = cardShape,
            colors = cardColors,
            modifier = cardModifier
        ) {
            content()
        }
    } else {
        Card(
            shape = cardShape,
            colors = cardColors,
            modifier = cardModifier
        ) {
            content()
        }
    }
}

@Composable
private fun SupportBarChart(data: List<OperatorStatValue>, displayMode: StatsDisplayMode) {
    fun displayCount(stat: OperatorStatValue): Int {
        return when (displayMode) {
            StatsDisplayMode.Sites -> stat.totalCount
            StatsDisplayMode.Active -> stat.activeCount
            StatsDisplayMode.Both -> stat.totalCount
        }
    }

    val maxCount = (data.maxOfOrNull(::displayCount) ?: 0).coerceAtLeast(1)
    val rows = data.sortedWith(compareByDescending<OperatorStatValue> { displayCount(it) }.thenBy { it.name })
    val chartScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(chartScrollState)
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        rows.forEach { stat ->
            val barColor = OperatorColors.keyFor(stat.name)
                ?.let { Color(OperatorColors.colorArgbForKey(it)) }
                ?: Color.Gray
            val targetFraction = (displayCount(stat).toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(targetValue = targetFraction, label = "statsBar")

            OperatorStatBar(
                operatorName = stat.name,
                totalCount = stat.totalCount,
                activeCount = stat.activeCount,
                fraction = animatedFraction,
                color = barColor,
                displayMode = displayMode
            )
        }
    }
}

@Composable
private fun WeeklyTrendChart(data: List<WeeklyStatValue>, displayMode: StatsDisplayMode) {
    fun displayCount(stat: WeeklyStatValue): Int {
        return when (displayMode) {
            StatsDisplayMode.Sites -> stat.totalCount
            StatsDisplayMode.Active -> stat.activeCount
            StatsDisplayMode.Both -> maxOf(stat.totalCount, stat.activeCount)
        }
    }

    val points = data.filter { displayCount(it) > 0 }
    if (points.size < 2) return

    val maxCount = points.maxOf(::displayCount).coerceAtLeast(1)
    val activeColor = MaterialTheme.colorScheme.primary
    val totalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Spacer(modifier = Modifier.height(18.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Evolution hebdomadaire",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            when (displayMode) {
                StatsDisplayMode.Sites -> TrendLegend(color = totalColor, label = stringResource(R.string.appstrings_sites_label))
                StatsDisplayMode.Active -> TrendLegend(color = activeColor, label = stringResource(R.string.appstrings_active_sites_label))
                StatsDisplayMode.Both -> {
                    TrendLegend(color = activeColor, label = stringResource(R.string.appstrings_active_sites_label))
                    Spacer(modifier = Modifier.width(10.dp))
                    TrendLegend(color = totalColor, label = stringResource(R.string.appstrings_sites_label))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp)
        ) {
            val left = 6f
            val right = size.width - 6f
            val top = 8f
            val bottom = size.height - 8f
            val usableWidth = (right - left).coerceAtLeast(1f)
            val usableHeight = (bottom - top).coerceAtLeast(1f)

            fun pointAt(index: Int, value: Int): Offset {
                val x = left + usableWidth * (index.toFloat() / (points.lastIndex).coerceAtLeast(1).toFloat())
                val y = bottom - usableHeight * (value.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                return Offset(x, y)
            }

            for (index in 0 until points.lastIndex) {
                if (displayMode != StatsDisplayMode.Active) {
                    drawLine(
                        color = totalColor,
                        start = pointAt(index, points[index].totalCount),
                        end = pointAt(index + 1, points[index + 1].totalCount),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
                if (displayMode != StatsDisplayMode.Sites) {
                    drawLine(
                        color = activeColor,
                        start = pointAt(index, points[index].activeCount),
                        end = pointAt(index + 1, points[index + 1].activeCount),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            points.forEachIndexed { index, value ->
                if (displayMode != StatsDisplayMode.Active) {
                    drawCircle(totalColor, radius = 4f, center = pointAt(index, value.totalCount))
                }
                if (displayMode != StatsDisplayMode.Sites) {
                    drawCircle(activeColor, radius = 5f, center = pointAt(index, value.activeCount))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = points.first().shortWeekLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = points.last().shortWeekLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrendLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private fun WeeklyStatValue.shortWeekLabel(): String {
    return sourceDate?.take(10)?.takeIf { it.isNotBlank() }
        ?: weekStart?.take(10)?.takeIf { it.isNotBlank() }
        ?: weekKey
}

@Composable
private fun OperatorStatBar(
    operatorName: String,
    totalCount: Int,
    activeCount: Int,
    fraction: Float,
    color: Color,
    displayMode: StatsDisplayMode
) {
    val chartHeight = 174.dp
    val primaryCount = when (displayMode) {
        StatsDisplayMode.Sites -> totalCount
        StatsDisplayMode.Active -> activeCount
        StatsDisplayMode.Both -> totalCount
    }
    val primaryBarHeight = when {
        primaryCount <= 0 -> 0.dp
        else -> (chartHeight * fraction).coerceAtLeast(8.dp)
    }
    val activeFraction = if (totalCount > 0) {
        activeCount.toFloat().coerceAtLeast(0f) / totalCount.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    val activeBarHeight = primaryBarHeight * activeFraction
    val countText = when (displayMode) {
        StatsDisplayMode.Sites -> totalCount.toString()
        StatsDisplayMode.Active -> activeCount.toString()
        StatsDisplayMode.Both -> if (totalCount == 0) "0" else "${activeCount.coerceIn(0, totalCount)}/$totalCount"
    }

    Column(
        modifier = Modifier.width(176.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(chartHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
            )
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(primaryBarHeight)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(if (displayMode == StatsDisplayMode.Active) color else color.copy(alpha = 0.36f))
            )
            if (displayMode == StatsDisplayMode.Both) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(activeBarHeight)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(color)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (displayMode == StatsDisplayMode.Both) 70.dp else 52.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = operatorName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (displayMode == StatsDisplayMode.Both) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (displayMode != StatsDisplayMode.Both) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatCountBadge(countText = countText, color = color)
                    }
                }

                if (displayMode == StatsDisplayMode.Both) {
                    Spacer(modifier = Modifier.height(6.dp))
                    StatCountBadge(
                        countText = countText,
                        color = color,
                        modifier = Modifier.padding(start = 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCountBadge(
    countText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        modifier = modifier
    ) {
        Text(
            text = countText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            softWrap = false
        )
    }
}
