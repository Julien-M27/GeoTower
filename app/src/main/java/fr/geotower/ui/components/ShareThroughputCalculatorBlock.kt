package fr.geotower.ui.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.radio.MobileOperator
import fr.geotower.radio.RadioThroughputEngine
import fr.geotower.radio.ThroughputProfiles
import androidx.compose.ui.res.stringResource
import fr.geotower.R
import fr.geotower.utils.FreqBand
import fr.geotower.utils.ThroughputDisplayText
import fr.geotower.utils.ThroughputPrefs
import fr.geotower.utils.ThroughputTextKey
import fr.geotower.utils.parseAndSortFrequencies

private const val MAX_SHARE_FR_UPLINK_AGGREGATED_CARRIERS = 2

@Composable
fun ShareThroughputCalculatorBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    physique: PhysiqueEntity?,
    prefs: SharedPreferences,
    cardBgColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    blockShape: Shape = RoundedCornerShape(12.dp),
    compact: Boolean = false,
    maxIncludedBands: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val txtUnknown = stringResource(R.string.appstrings_unknown)
    val txtAzimuthNotSpecified = stringResource(R.string.appstrings_azimuth_not_specified)
    val rawFrequencies = technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: info.frequences
    val parsedBands = remember(rawFrequencies, txtUnknown, txtAzimuthNotSpecified) {
        parseAndSortFrequencies(rawFrequencies, txtUnknown, txtAzimuthNotSpecified)
    }
    val include4G = remember(prefs) { ThroughputPrefs.include4G.read(prefs) }
    val include5G = remember(prefs) { ThroughputPrefs.include5G.read(prefs) }
    val includePlanned = remember(prefs) { ThroughputPrefs.includePlanned.read(prefs) }
    val receiverHeightMeters = remember(prefs) {
        prefs.getFloat(ThroughputPrefs.CUSTOM_RECEIVER_HEIGHT, 2f)
            .coerceAtLeast(0.5f)
            .toDouble()
    }
    val enabledBandKeys = remember(parsedBands, prefs) {
        throughputCalculationBands(parsedBands)
            .filter { isThroughputBandEnabledByDefault(it, prefs) }
            .map { throughputBandKey(it) }
            .toSet()
    }
    val result = remember(
        parsedBands,
        info.operateur,
        include4G,
        include5G,
        includePlanned,
        physique?.hauteur,
        enabledBandKeys,
        receiverHeightMeters
    ) {
        calculateShareThroughput(
            bands = parsedBands,
            operatorName = info.operateur,
            include4G = include4G,
            include5G = include5G,
            includePlanned = includePlanned,
            enabledBandKeys = enabledBandKeys,
            supportHeightMeters = physique?.hauteur,
            receiverHeightMeters = receiverHeightMeters
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = blockShape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 9.dp else 16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compact) 13.dp else 24.dp))
                Spacer(Modifier.width(if (compact) 5.dp else 8.dp))
                Text(
                    text = stringResource(R.string.appstrings_share_throughput_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 11.sp else 16.sp,
                    lineHeight = if (compact) 12.sp else 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp), modifier = Modifier.fillMaxWidth()) {
                ShareThroughputMetric(
                    label = stringResource(R.string.appstrings_throughput_download_label),
                    value = formatThroughputMbps(result.totalDownMbps),
                    iconColor = Color(0xFF4CAF50),
                    isDownload = true,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
                ShareThroughputMetric(
                    label = stringResource(R.string.appstrings_share_throughput_phone_upload_label),
                    value = formatThroughputMbps(result.totalUpMbps),
                    iconColor = Color(0xFF2196F3),
                    isDownload = false,
                    compact = compact,
                    modifier = Modifier.weight(1f)
                )
            }

            val coneDistance = result.coneDistance
            if (coneDistance != null && !compact) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = ThroughputDisplayText.shareOptimalDistance(formatThroughputDistanceMeters(coneDistance.centerMeters)),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = ThroughputDisplayText.shareZone(
                                formatThroughputDistanceMeters(coneDistance.nearMeters),
                                formatThroughputDistanceMeters(coneDistance.farMeters)
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Text(
                text = ThroughputDisplayText.includedBandsCount(result.includedBands.size, result.bands.size),
                fontSize = if (compact) 8.sp else 12.sp,
                lineHeight = if (compact) 9.sp else 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (result.includedBands.isEmpty()) {
                Text(
                    text = stringResource(R.string.appstrings_throughput_no_bands),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (compact) 9.sp else 13.sp,
                    lineHeight = if (compact) 10.sp else 15.sp
                )
            } else {
                val visibleBands = if (compact) result.includedBands.take(maxIncludedBands) else result.includedBands
                visibleBands.forEachIndexed { index, band ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                    ShareThroughputBandLine(band, compact)
                }
                if (compact && result.includedBands.size > visibleBands.size) {
                    Text(
                        text = "+ ${result.includedBands.size - visibleBands.size}",
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!compact) {
                Text(
                    text = stringResource(R.string.appstrings_share_throughput_disclaimer),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShareThroughputMetric(
    label: String,
    value: String,
    iconColor: Color,
    isDownload: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isDownload) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                null,
                tint = iconColor,
                modifier = Modifier.size(if (compact) 11.dp else 18.dp)
            )
            Spacer(Modifier.width(if (compact) 2.dp else 4.dp))
            Text(
                label,
                fontSize = if (compact) 7.sp else 11.sp,
                lineHeight = if (compact) 8.sp else 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 13.sp else 20.sp,
            lineHeight = if (compact) 14.sp else 22.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShareThroughputBandLine(band: ShareThroughputBandResult, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Circle,
                null,
                tint = if (band.generation == 5) Color(0xFF7E57C2) else Color(0xFF009688),
                modifier = Modifier.size(if (compact) 6.dp else 9.dp)
            )
            Spacer(Modifier.width(if (compact) 4.dp else 7.dp))
            Text(
                text = band.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 8.sp else 13.sp,
                lineHeight = if (compact) 9.sp else 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatThroughputMbps(band.downMbps)} / ${formatThroughputMbps(band.upMbps)}",
                fontSize = if (compact) 7.sp else 12.sp,
                lineHeight = if (compact) 8.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!compact) {
            Text(
                text = band.modulationLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

private fun calculateShareThroughput(
    bands: List<FreqBand>,
    operatorName: String?,
    include4G: Boolean,
    include5G: Boolean,
    includePlanned: Boolean,
    enabledBandKeys: Set<String>,
    supportHeightMeters: Double?,
    receiverHeightMeters: Double
): ShareThroughputResult {
    val operator = MobileOperator.fromLabel(operatorName)
    val engineProfile = ThroughputProfiles.prudent
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
            val excludedReason = when {
                !generationAllowed -> if (band.gen == 5) ThroughputTextKey.THROUGHPUT_REASON_5G_DISABLED else ThroughputTextKey.THROUGHPUT_REASON_4G_DISABLED
                !bandAllowed -> ThroughputTextKey.THROUGHPUT_REASON_BAND_EXCLUDED
                operator == null -> ThroughputTextKey.THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED
                carrierResult == null -> excludedByKey[key]?.reason ?: ThroughputTextKey.THROUGHPUT_REASON_ALLOCATION_NOT_FOUND
                !carrierResult.included -> carrierResult.excludedReason
                isPlanned && !includePlanned -> ThroughputTextKey.THROUGHPUT_REASON_PLANNED_BAND
                else -> null
            }

            ShareThroughputBandResult(
                key = key,
                label = throughputBandLabel(band),
                generation = band.gen,
                modulationLabel = carrierResult?.let {
                    throughputModulationLabel(it.dlModulationOrder, it.ulModulationOrder, it.dlMimoLayers, it.ulMimoLayers)
                } ?: throughputModulationLabel(band.gen, engineProfile),
                bandwidthMHz = bandwidth.valueMHz,
                coneDistance = estimateThroughputConeDistance(panelHeightMeters, userDeviceHeightMeters = receiverHeightMeters),
                azimuths = extractThroughputAzimuths(band),
                downMbps = carrierResult?.dlMbps ?: 0.0,
                upMbps = carrierResult?.ulMbps ?: 0.0,
                isIncluded = isIncluded,
                excludedReason = excludedReason
            )
        }

    return ShareThroughputResult(bands = calculatedBands)
}

private data class ShareThroughputResult(
    val bands: List<ShareThroughputBandResult>
) {
    val includedBands: List<ShareThroughputBandResult>
        get() = bands.filter { it.isIncluded }
    val totalDownMbps: Double
        get() = includedBands.sumOf { it.downMbps }
    val totalUpMbps: Double
        get() = includedBands
            .sortedByDescending { it.upMbps }
            .take(MAX_SHARE_FR_UPLINK_AGGREGATED_CARRIERS)
            .sumOf { it.upMbps }
    val coneDistance: ThroughputConeDistance?
        get() {
            val distances = includedBands.mapNotNull { it.coneDistance }
            if (distances.isEmpty()) return null
            return ThroughputConeDistance(
                centerMeters = distances.map { it.centerMeters }.average(),
                nearMeters = distances.minOf { it.nearMeters },
                farMeters = distances.maxOf { it.farMeters }
            )
        }
}

private data class ShareThroughputBandResult(
    val key: String,
    val label: String,
    val generation: Int,
    val modulationLabel: String,
    val bandwidthMHz: Double,
    val coneDistance: ThroughputConeDistance?,
    val azimuths: List<Double>,
    val downMbps: Double,
    val upMbps: Double,
    val isIncluded: Boolean,
    val excludedReason: String?
)
