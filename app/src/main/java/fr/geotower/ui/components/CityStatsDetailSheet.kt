package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.models.isDeclaredActive
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.radioFrequencyLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

// ✅ CLASSE DE DONNÉES COMPLÈTE
data class OperatorStat(
    val key: String,
    val name: String,
    val activeCount: Int,
    val totalCount: Int,
    val logoRes: Int?,
    val color: Color,
    val idAnfrs: Set<String>,
    val groupedFreqs: Map<String, List<FrequencyStat>>
)

data class FrequencyStat(
    val label: String,
    val activeCount: Int,
    val totalCount: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CityStatsDetailSheet(
    antennas: List<LocalisationEntity>,
    techniques: Map<String, TechniqueEntity>,
    isFrequencyStatusLoading: Boolean,
    onRequestFrequencyStatus: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sizing = LocalGeoTowerUiStyle.current.sizing

    val stats by produceState<List<OperatorStat>?>(initialValue = null, antennas, techniques) {
        value = withContext(Dispatchers.Default) {
            buildCityOperatorStats(
                antennas = antennas,
                techniques = techniques
            )
        }
    }

    var expandedOps by remember { mutableStateOf(setOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sizing.spacing(24.dp))
                .padding(bottom = sizing.spacing(32.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.appstrings_operator_details_title),
                style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                val currentStats = stats
                if (currentStats == null) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = sizing.spacing(32.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(sizing.component(48.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.appstrings_loading_operator_stats),
                                style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                items(currentStats, key = { it.key }) { stat ->
                    val isExpanded = expandedOps.contains(stat.key)
                    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowAnim")

                    Card(
                        onClick = {
                            if (isExpanded) {
                                expandedOps = expandedOps - stat.key
                            } else {
                                expandedOps = expandedOps + stat.key
                                onRequestFrequencyStatus(stat.idAnfrs)
                            }
                        },
                        shape = RoundedCornerShape(sizing.component(16.dp)),
                        // ✅ BANDEAU OPÉRATEUR : alpha = 0.5f
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (stat.logoRes != null) {
                                    Image(
                                        painter = painterResource(id = stat.logoRes),
                                        contentDescription = stat.name,
                                        modifier = Modifier.size(sizing.component(60.dp)).clip(RoundedCornerShape(sizing.component(8.dp))).background(Color.White)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(sizing.component(60.dp))
                                            .clip(RoundedCornerShape(sizing.component(8.dp)))
                                            .background(stat.color.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stat.name.take(1).uppercase(),
                                            color = stat.color,
                                            fontWeight = FontWeight.Black,
                                            fontSize = sizing.text(24.sp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(sizing.spacing(16.dp)))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stat.name, style = sizing.textStyle(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.Bold)
                                    Text(text = pluralStringResource(R.plurals.sites_count, stat.totalCount, stat.totalCount), style = sizing.textStyle(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.background(stat.color, CircleShape).padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)), contentAlignment = Alignment.Center) {
                                    Text(text = "${stat.activeCount}/${stat.totalCount}", fontSize = sizing.text(24.sp), fontWeight = FontWeight.Black, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(sizing.component(24.dp)).rotate(arrowRotation))
                            }

                            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = sizing.component(0.5.dp))

                                    CartoradioGroupedTable(
                                        groupedData = stat.groupedFreqs,
                                        brandColor = stat.color,
                                        isLoadingActiveStatus = isFrequencyStatusLoading
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

private fun buildCityOperatorStats(
    antennas: List<LocalisationEntity>,
    techniques: Map<String, TechniqueEntity>
): List<OperatorStat> {
    val targetInsee = antennas.mapNotNull { normalizeCityStatsInsee(it.codeInsee)?.takeIf { c -> c.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    val cityAntennas = if (targetInsee != null) {
        antennas.filter { normalizeCityStatsInsee(it.codeInsee) == targetInsee }
    } else {
        antennas
    }

    val rawList = OperatorColors.all.map { operator ->
        val opKey = operator.key
        val logo = OperatorLogos.drawableRes(opKey)
        val color = Color(operator.colorArgb)

        val opAntennasGrouped = cityAntennas
            .asSequence()
            .filter { OperatorColors.keysFor(it.operateur).contains(opKey) }
            .groupBy { it.physicalSiteKey() }

        val totalSiteCount = opAntennasGrouped.size
        val activeSiteCount = opAntennasGrouped.values.count { siteAntennas ->
            siteAntennas.any { it.isDeclaredActive() }
        }
        val idAnfrs = opAntennasGrouped.values
            .asSequence()
            .flatten()
            .map { it.idAnfr }
            .filter { it.isNotBlank() && !it.startsWith("CLUSTER_") }
            .toSet()

        val counts = mutableMapOf<String, FrequencyStat>()

        opAntennasGrouped.values.forEach { siteAntennas ->
            val activeSystems = siteAntennas.flatMap { antenna ->
                activeFrequencyKeysFromDetails(techniques[antenna.idAnfr]?.detailsFrequences)
            }.toSet()
            val siteSystems = siteAntennas.flatMap { ant ->
                cityStatsSystemsFromFilters(ant.filtres)
            }.distinct()

            siteSystems.forEach { sys ->
                val current = counts[sys]
                counts[sys] = FrequencyStat(
                    label = sys.substringAfter("|"),
                    activeCount = (current?.activeCount ?: 0) + if (sys in activeSystems) 1 else 0,
                    totalCount = (current?.totalCount ?: 0) + 1
                )
            }
        }

        val groupedByTech = counts.toList().groupBy { (sys, _) ->
            sys.substringBefore("|")
        }.mapValues { (_, items) ->
            items.map { (_, stat) -> stat }
        }.toSortedMap(compareBy { tech ->
            val idx = AppConfig.siteTechnoOrder.value.indexOf(tech)
            if (idx == -1) 99 else idx
        })

        OperatorStat(
            key = operator.key,
            name = operator.label,
            activeCount = activeSiteCount,
            totalCount = totalSiteCount,
            logoRes = logo,
            color = color,
            idAnfrs = idAnfrs,
            groupedFreqs = groupedByTech
        )
    }

    return rawList.filter { it.totalCount > 0 }.ifEmpty { rawList }
        .sortedByDescending { it.totalCount }
}

private fun normalizeCityStatsInsee(code: String?): String? {
    return when {
        code == null -> null
        code.startsWith("751") && code.length == 5 -> "75056"
        code.startsWith("132") && code.length == 5 -> "13055"
        code.startsWith("6938") && code.length == 5 -> "69123"
        else -> code
    }
}

private val cityStatsFilterRegex = Regex("^(2G|3G|4G|5G)(\\d{3,4})$")

private fun cityStatsSystemsFromFilters(filters: String?): List<String> {
    return filters.orEmpty()
        .split(Regex("\\s+"))
        .mapNotNull { token ->
            val match = cityStatsFilterRegex.find(token.trim().uppercase())
            if (match != null) {
                val tech = match.groupValues[1]
                val freq = match.groupValues[2]
                "$tech|$freq"
            } else {
                null
            }
        }
}

private fun activeFrequencyKeysFromDetails(details: String?): Set<String> {
    if (details.isNullOrBlank()) return emptySet()

    return details.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .flatMap { line ->
            val parts = line.split("|").map { it.trim() }
            val rawFrequency = parts.getOrNull(0).orEmpty()
            val status = parts.getOrNull(1).orEmpty()
            if (isActiveFrequencyStatus(status)) {
                frequencyKeysFromRawDetails(rawFrequency).asSequence()
            } else {
                emptySequence()
            }
        }
        .toSet()
}

private fun isActiveFrequencyStatus(status: String): Boolean {
    val normalized = status.lowercase()
    return normalized.contains("en service") ||
        normalized.contains("techniquement")
}

private fun frequencyKeysFromRawDetails(rawFrequency: String): Set<String> {
    val systemName = rawFrequency.substringBefore(":").trim().uppercase()
    val gen = when {
        systemName.contains("5G") || systemName.contains("NR") -> 5
        systemName.contains("4G") || systemName.contains("LTE") -> 4
        systemName.contains("3G") || systemName.contains("UMTS") -> 3
        systemName.contains("2G") || systemName.contains("GSM") -> 2
        else -> 0
    }
    if (gen !in 2..5) return emptySet()

    val knownValues = mobileFrequencyValuesForGeneration(gen)
    val systemValue = Regex("\\d+").findAll(systemName)
        .mapNotNull { it.value.toIntOrNull() }
        .filter { it in knownValues }
        .maxOrNull()
        ?: if (gen == 5 && systemName.contains("26") && systemName.contains("GHZ")) 26000 else null

    if (systemValue != null) return setOf("${gen}G|$systemValue")

    val preciseFrequencies = rawFrequency.substringAfter(":", "")
    return mobileFrequencyValuesFromRanges(gen, preciseFrequencies).map { "${gen}G|$it" }.toSet()
}

private fun mobileFrequencyValuesForGeneration(gen: Int): Set<Int> = when (gen) {
    5 -> setOf(700, 1400, 2100, 3500, 4200, 26000)
    4 -> setOf(700, 800, 900, 1800, 2100, 2600)
    3 -> setOf(900, 2100)
    2 -> setOf(900, 1800)
    else -> emptySet()
}

private fun mobileFrequencyValuesFromRanges(gen: Int, rawRanges: String): Set<Int> {
    return frequencyRangeRegex.findAll(rawRanges)
        .flatMap { match ->
            val start = normalizeFrequencyToMhz(match.groupValues[1], match.groupValues[3])
            val end = normalizeFrequencyToMhz(match.groupValues[2], match.groupValues[3])
            frequencyValuesForRange(gen, start, end).asSequence()
        }
        .toSet()
}

private fun normalizeFrequencyToMhz(value: String, unit: String): Double? {
    val number = value.replace(',', '.').toDoubleOrNull() ?: return null
    val normalizedUnit = unit.lowercase()
    return when {
        normalizedUnit.contains("ghz") -> number * 1000.0
        normalizedUnit.contains("khz") -> number / 1000.0
        normalizedUnit.contains("hz") && !normalizedUnit.contains("mhz") -> number / 1_000_000.0
        else -> number
    }
}

private fun frequencyValuesForRange(gen: Int, start: Double?, end: Double?): Set<Int> {
    if (start == null || end == null) return emptySet()
    return when (gen) {
        5 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 700.0, 790.0)) add(700)
            if (frequencyRangeOverlaps(start, end, 1427.0, 1518.0)) add(1400)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
            if (frequencyRangeOverlaps(start, end, 3300.0, 3800.0)) add(3500)
            if (frequencyRangeOverlaps(start, end, 3800.1, 4200.0)) add(4200)
            if (frequencyRangeOverlaps(start, end, 24000.0, 27500.0)) add(26000)
        }
        4 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 700.0, 790.0)) add(700)
            if (frequencyRangeOverlaps(start, end, 791.0, 862.0)) add(800)
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1710.0, 1880.0)) add(1800)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
            if (frequencyRangeOverlaps(start, end, 2500.0, 2690.0)) add(2600)
        }
        3 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
        }
        2 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1710.0, 1880.0)) add(1800)
        }
        else -> emptySet()
    }
}

private fun frequencyRangeOverlaps(start: Double, end: Double, low: Double, high: Double): Boolean {
    val min = minOf(start, end)
    val max = maxOf(start, end)
    return min <= high && max >= low
}

private val frequencyRangeRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")

// LE TABLEAU
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CartoradioGroupedTable(
    groupedData: Map<String, List<FrequencyStat>>,
    brandColor: Color,
    isLoadingActiveStatus: Boolean = false
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val tableBgColor = MaterialTheme.colorScheme.surface

    if (groupedData.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().background(tableBgColor).padding(sizing.spacing(16.dp)), contentAlignment = Alignment.Center) {
            if (isLoadingActiveStatus) {
                LoadingIndicator(
                    modifier = Modifier.size(sizing.component(28.dp)),
                    color = brandColor
                )
            } else {
                Text(stringResource(R.string.appstrings_technical_data_unavailable), fontSize = sizing.text(12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // ✅ CHANGEMENT ICI : En-tête du tableau mis à 0.5f pour matcher avec le reste
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(sizing.spacing(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.appstrings_frequencies_and_techs), fontWeight = FontWeight.Bold, fontSize = sizing.text(13.sp), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.appstrings_sites_label), fontWeight = FontWeight.Bold, fontSize = sizing.text(13.sp))
        }

        groupedData.forEach { (tech, items) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // ✅ COLONNE TECHNOLOGIE : Déjà à 0.5f, donc elle matche avec l'en-tête et le bandeau
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(width = sizing.component(0.5.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                // COLONNE GAUCHE
                Box(modifier = Modifier.weight(0.25f).padding(vertical = sizing.spacing(16.dp)), contentAlignment = Alignment.Center) {
                    Text(text = tech, fontWeight = FontWeight.Black, fontSize = sizing.text(16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // COLONNE DROITE
                Column(modifier = Modifier.weight(0.75f).background(tableBgColor)) {
                    val sortedItems = items.sortedWith(compareBy { stat ->
                        val freqValue = Regex("\\d+").findAll(stat.label).map { it.value }.lastOrNull() ?: ""
                        val orderList = when(tech) {
                            "5G" -> AppConfig.siteFreqOrder5G.value
                            "4G" -> AppConfig.siteFreqOrder4G.value
                            "3G" -> AppConfig.siteFreqOrder3G.value
                            "2G" -> AppConfig.siteFreqOrder2G.value
                            else -> emptyList()
                        }
                        val idx = orderList.indexOf(freqValue)
                        if (idx == -1) 999 else idx
                    })

                    sortedItems.forEachIndexed { index, stat ->
                        val isAlternate = index % 2 != 0
                        val frequencyLabel = Regex("\\d+").findAll(stat.label)
                            .mapNotNull { it.value.toIntOrNull() }
                            .lastOrNull()
                            ?.let(::radioFrequencyLabel)
                            ?: stat.label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isAlternate) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(vertical = sizing.spacing(12.dp), horizontal = sizing.spacing(16.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (
                                    frequencyLabel.contains("MHz", true) ||
                                    frequencyLabel.contains("GHz", true)
                                ) {
                                    frequencyLabel
                                } else {
                                    "$frequencyLabel MHz"
                                },
                                fontSize = sizing.text(14.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            FrequencySitesCount(
                                activeCount = stat.activeCount,
                                totalCount = stat.totalCount,
                                brandColor = brandColor,
                                isLoadingActiveStatus = isLoadingActiveStatus
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FrequencySitesCount(
    activeCount: Int,
    totalCount: Int,
    brandColor: Color,
    isLoadingActiveStatus: Boolean
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Row(
        modifier = Modifier.widthIn(min = sizing.component(56.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (isLoadingActiveStatus) {
            LoadingIndicator(
                modifier = Modifier.size(sizing.component(16.dp)),
                color = brandColor
            )
            Text(
                text = "/$totalCount",
                fontSize = sizing.text(16.sp),
                fontWeight = FontWeight.Bold,
                color = brandColor
            )
        } else {
            Text(
                text = "$activeCount/$totalCount",
                fontSize = sizing.text(16.sp),
                fontWeight = FontWeight.Bold,
                color = brandColor
            )
        }
    }
}
