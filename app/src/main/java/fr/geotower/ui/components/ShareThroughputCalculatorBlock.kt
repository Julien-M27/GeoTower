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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import fr.geotower.utils.AppStrings
import java.util.Locale

private const val MAX_SHARE_FR_UPLINK_AGGREGATED_CARRIERS = 2

@Composable
fun ShareThroughputCalculatorBlock(
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    physique: PhysiqueEntity?,
    prefs: SharedPreferences,
    cardBgColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    blockShape: Shape = RoundedCornerShape(12.dp)
) {
    val txtUnknown = AppStrings.unknown
    val txtAzimuthNotSpecified = AppStrings.azimuthNotSpecified
    val rawFrequencies = technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: info.frequences
    val parsedBands = remember(rawFrequencies, txtUnknown, txtAzimuthNotSpecified) {
        parseAndSortFrequencies(rawFrequencies, txtUnknown, txtAzimuthNotSpecified)
    }
    val preset = remember(prefs) {
        shareThroughputPresetFromPreference(prefs.getString("throughput_default_preset", "conservative"))
    }
    val customSettings = remember(prefs) {
        ShareCustomModulationSettings(
            lteDownIndex = prefs.getInt("throughput_custom_lte_down", 3).coerceIn(0, shareLteDownModulationOptions.lastIndex),
            lteUpIndex = prefs.getInt("throughput_custom_lte_up", 2).coerceIn(0, shareLteUpModulationOptions.lastIndex),
            nrDownIndex = prefs.getInt("throughput_custom_nr_down", 3).coerceIn(0, shareNrDownModulationOptions.lastIndex),
            nrUpIndex = prefs.getInt("throughput_custom_nr_up", 2).coerceIn(0, shareNrUpModulationOptions.lastIndex)
        )
    }
    val include4G = remember(prefs) { prefs.getBoolean("throughput_include_4g", true) }
    val include5G = remember(prefs) { prefs.getBoolean("throughput_include_5g", true) }
    val includePlanned = remember(prefs) { prefs.getBoolean("throughput_include_planned", false) }
    val enabledBandKeys = remember(parsedBands, prefs) {
        parsedBands
            .filter { it.gen == 4 || it.gen == 5 }
            .filterNot { isShareHiddenThroughputBand(it) }
            .filter { isShareThroughputBandEnabledByDefault(it, prefs) }
            .map { throughputBandKey(it) }
            .toSet()
    }
    val result = remember(
        parsedBands,
        info.operateur,
        preset,
        customSettings,
        include4G,
        include5G,
        includePlanned,
        physique?.hauteur,
        enabledBandKeys
    ) {
        calculateShareThroughput(
            bands = parsedBands,
            operatorName = info.operateur,
            preset = preset,
            customSettings = customSettings,
            include4G = include4G,
            include5G = include5G,
            includePlanned = includePlanned,
            enabledBandKeys = enabledBandKeys,
            supportHeightMeters = physique?.hauteur
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = blockShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = AppStrings.shareThroughputTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ShareThroughputMetric(
                    label = AppStrings.throughputDownloadLabel,
                    value = formatThroughputMbps(result.totalDownMbps),
                    iconColor = Color(0xFF4CAF50),
                    isDownload = true,
                    modifier = Modifier.weight(1f)
                )
                ShareThroughputMetric(
                    label = AppStrings.shareThroughputPhoneUploadLabel,
                    value = formatThroughputMbps(result.totalUpMbps),
                    iconColor = Color(0xFF2196F3),
                    isDownload = false,
                    modifier = Modifier.weight(1f)
                )
            }

            val coneDistance = result.coneDistance
            if (coneDistance != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = AppStrings.shareThroughputOptimalDistance(formatThroughputDistanceMeters(coneDistance.centerMeters)),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppStrings.shareThroughputZone(
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
                text = AppStrings.shareThroughputBandsSummary(
                    shareThroughputPresetLabel(preset),
                    result.includedBands.size,
                    result.bands.size
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result.includedBands.isEmpty()) {
                Text(
                    text = AppStrings.throughputNoBands,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                result.includedBands.forEachIndexed { index, band ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                    ShareThroughputBandLine(band)
                }
            }

            Text(
                text = AppStrings.shareThroughputDisclaimer,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShareThroughputMetric(
    label: String,
    value: String,
    iconColor: Color,
    isDownload: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isDownload) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ShareThroughputBandLine(band: ShareThroughputBandResult) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Circle,
                null,
                tint = if (band.generation == 5) Color(0xFF7E57C2) else Color(0xFF009688),
                modifier = Modifier.size(9.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = band.label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatThroughputMbps(band.downMbps)} / ${formatThroughputMbps(band.upMbps)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = band.modulationLabel,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun calculateShareThroughput(
    bands: List<FreqBand>,
    operatorName: String?,
    preset: ShareThroughputPreset,
    customSettings: ShareCustomModulationSettings,
    include4G: Boolean,
    include5G: Boolean,
    includePlanned: Boolean,
    enabledBandKeys: Set<String>,
    supportHeightMeters: Double?
): ShareThroughputResult {
    val operator = MobileOperator.fromLabel(operatorName)
    val engineProfile = shareEngineProfileFor(preset, customSettings)
    val engineResult = if (operator != null) {
        val systems = bands
            .filter { it.gen == 4 || it.gen == 5 }
            .filterNot { isShareHiddenThroughputBand(it) }
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
        .filterNot { isShareHiddenThroughputBand(it) }
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
            val excludedReason = when {
                !generationAllowed -> if (band.gen == 5) "5G désactivée" else "4G désactivée"
                !bandAllowed -> "Bande exclue"
                operator == null -> "Opérateur non reconnu"
                carrierResult == null -> excludedByKey[key]?.reason ?: "Allocation introuvable"
                !carrierResult.included -> carrierResult.excludedReason
                isPlanned && !includePlanned -> "Bande en projet"
                else -> null
            }

            ShareThroughputBandResult(
                key = key,
                label = throughputBandLabel(band),
                generation = band.gen,
                modulationLabel = carrierResult?.let {
                    shareModulationLabel(it.dlModulationOrder, it.ulModulationOrder, it.dlMimoLayers, it.ulMimoLayers)
                } ?: shareModulationLabel(band.gen, engineProfile),
                bandwidthMHz = bandwidth.valueMHz,
                coneDistance = estimateThroughputConeDistance(panelHeightMeters),
                azimuths = extractThroughputAzimuths(band),
                downMbps = carrierResult?.dlMbps ?: 0.0,
                upMbps = carrierResult?.ulMbps ?: 0.0,
                isIncluded = isIncluded,
                excludedReason = excludedReason
            )
        }

    return ShareThroughputResult(bands = calculatedBands)
}

private fun shareThroughputPresetFromPreference(raw: String?): ShareThroughputPreset {
    return when (raw?.lowercase(Locale.ROOT)) {
        "conservative", "prudent" -> ShareThroughputPreset.Conservative
        "ideal", "maximum" -> ShareThroughputPreset.Maximum
        "custom" -> ShareThroughputPreset.Custom
        else -> ShareThroughputPreset.Standard
    }
}

@Composable
private fun shareThroughputPresetLabel(preset: ShareThroughputPreset): String {
    return when (preset) {
        ShareThroughputPreset.Conservative -> AppStrings.throughputPresetLabel("conservative")
        ShareThroughputPreset.Standard -> AppStrings.throughputPresetLabel("standard")
        ShareThroughputPreset.Maximum -> AppStrings.throughputPresetLabel("ideal")
        ShareThroughputPreset.Custom -> AppStrings.throughputPresetLabel("custom")
    }
}

private fun isShareThroughputBandEnabledByDefault(band: FreqBand, prefs: SharedPreferences): Boolean {
    if (band.value <= 0) return true
    val generationPrefix = when (band.gen) {
        4 -> "4g"
        5 -> "5g"
        else -> return true
    }
    return prefs.getBoolean("throughput_band_${generationPrefix}_${band.value}", true)
}

private fun isShareHiddenThroughputBand(band: FreqBand): Boolean {
    return band.gen == 5 && band.value in setOf(1800, 26000)
}

private fun shareModulationLabel(gen: Int, profile: ThroughputProfile): String {
    val assumptions = if (gen == 5) profile.nr else profile.lte
    return shareModulationLabel(
        dlModulationOrder = assumptions.dlModulationOrder,
        ulModulationOrder = assumptions.ulModulationOrder,
        dlLayers = assumptions.dlMimoLayers,
        ulLayers = assumptions.ulMimoLayers
    )
}

private fun shareModulationLabel(
    dlModulationOrder: Int,
    ulModulationOrder: Int,
    dlLayers: Int,
    ulLayers: Int
): String {
    return "${shareModulationName(dlModulationOrder)} ${shareLayerLabel(dlLayers)} DL / ${shareModulationName(ulModulationOrder)} ${shareLayerLabel(ulLayers)} UL"
}

private fun shareModulationName(modulationOrder: Int): String {
    return when (modulationOrder) {
        2 -> "QPSK"
        4 -> "16-QAM"
        6 -> "64-QAM"
        8 -> "256-QAM"
        10 -> "1024-QAM"
        else -> "$modulationOrder bits/symbole"
    }
}

private fun shareLayerLabel(layers: Int): String {
    return if (layers <= 1) "1 couche" else "MIMO ${layers}x${layers}"
}

private fun shareEngineProfileFor(
    preset: ShareThroughputPreset,
    customSettings: ShareCustomModulationSettings
): ThroughputProfile {
    return when (preset) {
        ShareThroughputPreset.Conservative -> ThroughputProfiles.prudent
        ShareThroughputPreset.Standard -> ThroughputProfiles.standard
        ShareThroughputPreset.Maximum -> ThroughputProfiles.ideal
        ShareThroughputPreset.Custom -> shareCustomProfile(customSettings)
    }
}

private fun shareCustomProfile(customSettings: ShareCustomModulationSettings): ThroughputProfile {
    val lteDown = shareLteDownModulationOptions[customSettings.lteDownIndex.coerceIn(0, shareLteDownModulationOptions.lastIndex)]
    val lteUp = shareLteUpModulationOptions[customSettings.lteUpIndex.coerceIn(0, shareLteUpModulationOptions.lastIndex)]
    val nrDown = shareNrDownModulationOptions[customSettings.nrDownIndex.coerceIn(0, shareNrDownModulationOptions.lastIndex)]
    val nrUp = shareNrUpModulationOptions[customSettings.nrUpIndex.coerceIn(0, shareNrUpModulationOptions.lastIndex)]

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

private enum class ShareThroughputPreset {
    Conservative,
    Standard,
    Maximum,
    Custom
}

private data class ShareCustomModulationSettings(
    val lteDownIndex: Int = 3,
    val lteUpIndex: Int = 2,
    val nrDownIndex: Int = 3,
    val nrUpIndex: Int = 2
)

private data class ShareModulationOption(
    val label: String,
    val modulationOrder: Int
)

private val shareLteDownModulationOptions = listOf(
    ShareModulationOption("QPSK", 2),
    ShareModulationOption("16-QAM", 4),
    ShareModulationOption("64-QAM", 6),
    ShareModulationOption("256-QAM", 8)
)

private val shareLteUpModulationOptions = shareLteDownModulationOptions
private val shareNrDownModulationOptions = shareLteDownModulationOptions
private val shareNrUpModulationOptions = shareLteDownModulationOptions

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
