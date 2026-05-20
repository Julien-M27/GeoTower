package fr.geotower.ui.screens.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import java.util.Locale

private const val STATS_FREQUENCIES_ROUTE = "stats/frequencies"

private data class StatisticsData(
    val supports: List<Pair<String, Int>>,
    val supports2G: List<Pair<String, Int>>,
    val supports3G: List<Pair<String, Int>>,
    val supports4G: List<Pair<String, Int>>,
    val supports5G: List<Pair<String, Int>>
)

private data class FrequencyBandSpec(
    val label: String,
    val mask: Int
)

private data class FrequencyBandStats(
    val title: String,
    val description: String,
    val data: List<Pair<String, Int>>
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

private fun formatOperatorData(map: Map<String, Int>, displayOrder: List<String>): List<Pair<String, Int>> {
    val rows = displayOrder.map { op ->
        val name = OperatorColors.specForKey(op)?.label ?: op
        Pair(name, map[op] ?: 0)
    }
    return rows.filter { it.second > 0 }.ifEmpty { rows }
}

private fun frequencyBandsForTech(tech: String): List<FrequencyBandSpec> {
    return when (normalizeTech(tech)) {
        "2G" -> listOf(
            FrequencyBandSpec("900 MHz", RadioFilterMasks.BAND_2G_900),
            FrequencyBandSpec("1800 MHz", RadioFilterMasks.BAND_2G_1800)
        )
        "3G" -> listOf(
            FrequencyBandSpec("900 MHz", RadioFilterMasks.BAND_3G_900),
            FrequencyBandSpec("2100 MHz", RadioFilterMasks.BAND_3G_2100)
        )
        "4G" -> listOf(
            FrequencyBandSpec("700 MHz", RadioFilterMasks.BAND_4G_700),
            FrequencyBandSpec("800 MHz", RadioFilterMasks.BAND_4G_800),
            FrequencyBandSpec("900 MHz", RadioFilterMasks.BAND_4G_900),
            FrequencyBandSpec("1800 MHz", RadioFilterMasks.BAND_4G_1800),
            FrequencyBandSpec("2100 MHz", RadioFilterMasks.BAND_4G_2100),
            FrequencyBandSpec("2600 MHz", RadioFilterMasks.BAND_4G_2600)
        )
        "5G" -> listOf(
            FrequencyBandSpec("700 MHz", RadioFilterMasks.BAND_5G_700),
            FrequencyBandSpec("2100 MHz", RadioFilterMasks.BAND_5G_2100),
            FrequencyBandSpec("3500 MHz", RadioFilterMasks.BAND_5G_3500),
            FrequencyBandSpec("26 GHz", RadioFilterMasks.BAND_5G_26000)
        )
        else -> emptyList()
    }
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
    val totalMap = mutableMapOf<String, Int>()
    val counts2GMap = mutableMapOf<String, Int>()
    val counts3GMap = mutableMapOf<String, Int>()
    val counts4GMap = mutableMapOf<String, Int>()
    val counts5GMap = mutableMapOf<String, Int>()

    for (operator in OperatorColors.all) {
        val queryNames = operator.aliases.ifEmpty { listOf(operator.key) }
        totalMap[operator.key] = repository.getUniqueSupportCountByOperator(queryNames)
        counts2GMap[operator.key] = repository.get2GSupportCountByOperator(queryNames)
        counts3GMap[operator.key] = repository.get3GSupportCountByOperator(queryNames)
        counts4GMap[operator.key] = repository.get4GSupportCountByOperator(queryNames)
        counts5GMap[operator.key] = repository.get5GSupportCountByOperator(queryNames)
    }

    val displayOrder = operatorDisplayOrder(defaultOp)

    return StatisticsData(
        supports = formatOperatorData(totalMap, displayOrder),
        supports2G = formatOperatorData(counts2GMap, displayOrder),
        supports3G = formatOperatorData(counts3GMap, displayOrder),
        supports4G = formatOperatorData(counts4GMap, displayOrder),
        supports5G = formatOperatorData(counts5GMap, displayOrder)
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
    val stats = bands.map { band ->
        val countsMap = mutableMapOf<String, Int>()

        for (operator in OperatorColors.all) {
            val queryNames = operator.aliases.ifEmpty { listOf(operator.key) }
            countsMap[operator.key] = repository.getSupportCountByOperatorAndBand(queryNames, band.mask)
        }

        FrequencyBandStats(
            title = "$normalizedTech ${band.label}",
            description = "Supports déclarés par opérateur",
            data = formatOperatorData(countsMap, displayOrder)
        )
    }

    return stats.filter { band -> band.data.any { it.second > 0 } }.ifEmpty { stats }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatisticsScreen(navController: NavController, repository: AnfrRepository) {
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val defaultOp = AppConfig.defaultOperator.value

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val cachedStats = StatisticsScreenCache.data

    // --- ÉTAT DES DONNÉES ---
    var supportCounts by remember { mutableStateOf(cachedStats?.supports ?: emptyList()) }
    var support2GCounts by remember { mutableStateOf(cachedStats?.supports2G ?: emptyList()) }
    var support3GCounts by remember { mutableStateOf(cachedStats?.supports3G ?: emptyList()) }
    var support4GCounts by remember { mutableStateOf(cachedStats?.supports4G ?: emptyList()) }
    var support5GCounts by remember { mutableStateOf(cachedStats?.supports5G ?: emptyList()) }
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

            // Graphique 1 : TOTAL
            StatCard(stringResource(R.string.appstrings_stats_supports_title), stringResource(R.string.appstrings_stats_supports_desc), supportCounts, isLoading, cardBgColor)

            Spacer(modifier = Modifier.height(16.dp))

            // Graphique 2 : 5G
            StatCard(
                title = stringResource(R.string.appstrings_stats5_g_title),
                desc = stringResource(R.string.appstrings_stats5_g_desc),
                data = support5GCounts,
                isLoading = isLoading,
                bgColor = cardBgColor,
                onClick = { navController.navigate(statsFrequenciesRoute("5G")) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Graphique 3 : 4G
            StatCard(
                title = stringResource(R.string.appstrings_stats4_g_title),
                desc = stringResource(R.string.appstrings_stats4_g_desc),
                data = support4GCounts,
                isLoading = isLoading,
                bgColor = cardBgColor,
                onClick = { navController.navigate(statsFrequenciesRoute("4G")) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatCard(
                title = stringResource(R.string.appstrings_stats3_g_title),
                desc = stringResource(R.string.appstrings_stats3_g_desc),
                data = support3GCounts,
                isLoading = isLoading,
                bgColor = cardBgColor,
                onClick = { navController.navigate(statsFrequenciesRoute("3G")) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatCard(
                title = stringResource(R.string.appstrings_stats2_g_title),
                desc = stringResource(R.string.appstrings_stats2_g_desc),
                data = support2GCounts,
                isLoading = isLoading,
                bgColor = cardBgColor,
                onClick = { navController.navigate(statsFrequenciesRoute("2G")) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
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

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val cachedStats = FrequencyStatsScreenCache.dataByTech[normalizedTech]

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

            when {
                isLoading && frequencyStats.isEmpty() -> {
                    StatCard(
                        title = frequencyDetailTitle(normalizedTech),
                        desc = "Chargement du détail par fréquence",
                        data = emptyList(),
                        isLoading = true,
                        bgColor = cardBgColor
                    )
                }
                frequencyStats.isEmpty() -> {
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
                    frequencyStats.forEachIndexed { index, band ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        StatCard(
                            title = band.title,
                            desc = band.description,
                            data = band.data,
                            isLoading = false,
                            bgColor = cardBgColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    desc: String,
    data: List<Pair<String, Int>>,
    isLoading: Boolean,
    bgColor: Color,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .then(
            if (onClick != null && !isLoading) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.58f)),
        modifier = cardModifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(18.dp))
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                SupportBarChart(data = data)
            }
        }
    }
}

@Composable
fun SupportBarChart(data: List<Pair<String, Int>>) {
    val maxCount = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val rows = data.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    val chartScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(chartScrollState)
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        rows.forEach { (opName, count) ->
            val barColor = OperatorColors.keyFor(opName)
                ?.let { Color(OperatorColors.colorArgbForKey(it)) }
                ?: Color.Gray
            val targetFraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(targetValue = targetFraction, label = "statsBar")

            OperatorStatBar(
                operatorName = opName,
                count = count,
                fraction = animatedFraction,
                color = barColor
            )
        }
    }
}

@Composable
private fun OperatorStatBar(
    operatorName: String,
    count: Int,
    fraction: Float,
    color: Color
) {
    val chartHeight = 174.dp
    val visibleBarHeight = when {
        count <= 0 -> 0.dp
        else -> (chartHeight * fraction).coerceAtLeast(8.dp)
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
                    .height(visibleBarHeight)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(color)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = color.copy(alpha = 0.14f),
                    contentColor = color
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        softWrap = false
                    )
                }
            }
        }
    }
}
