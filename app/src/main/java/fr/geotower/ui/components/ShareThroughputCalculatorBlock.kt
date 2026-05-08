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
import fr.geotower.radio.SiteRadioStatus
import fr.geotower.radio.SiteRadioSystem
import fr.geotower.radio.ThroughputProfile
import fr.geotower.radio.ThroughputProfiles
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

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
            .map { shareBandKey(it) }
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
                    text = AppStrings.get("Débit théorique", "Theoretical throughput", "Débito teórico"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ShareThroughputMetric(
                    label = AppStrings.get("Descendant", "Download", "Download"),
                    value = formatShareThroughput(result.totalDownMbps),
                    iconColor = Color(0xFF4CAF50),
                    isDownload = true,
                    modifier = Modifier.weight(1f)
                )
                ShareThroughputMetric(
                    label = AppStrings.get("Montant tél.", "Phone upload", "Upload tel."),
                    value = formatShareThroughput(result.totalUpMbps),
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
                            text = AppStrings.get(
                                "Distance optimale : ${formatShareDistanceMeters(coneDistance.centerMeters)}",
                                "Optimal distance: ${formatShareDistanceMeters(coneDistance.centerMeters)}",
                                "Distância ótima: ${formatShareDistanceMeters(coneDistance.centerMeters)}"
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppStrings.get(
                                "Zone : ${formatShareDistanceMeters(coneDistance.nearMeters)} à ${formatShareDistanceMeters(coneDistance.farMeters)}",
                                "Zone: ${formatShareDistanceMeters(coneDistance.nearMeters)} to ${formatShareDistanceMeters(coneDistance.farMeters)}",
                                "Zona: ${formatShareDistanceMeters(coneDistance.nearMeters)} a ${formatShareDistanceMeters(coneDistance.farMeters)}"
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Text(
                text = AppStrings.get(
                    "${shareThroughputPresetLabel(preset)} · ${result.includedBands.size}/${result.bands.size} bande(s)",
                    "${shareThroughputPresetLabel(preset)} · ${result.includedBands.size}/${result.bands.size} band(s)",
                    "${shareThroughputPresetLabel(preset)} · ${result.includedBands.size}/${result.bands.size} banda(s)"
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
                text = AppStrings.get(
                    "Débit radio estimé : hors charge réseau, signal réel, backhaul et limites exactes du téléphone.",
                    "Estimated radio throughput: excludes network load, real signal, backhaul and exact phone limits.",
                    "Débito rádio estimado: exclui carga da rede, sinal real, backhaul e limites exatos do telefone."
                ),
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
                text = "${formatShareThroughput(band.downMbps)} / ${formatShareThroughput(band.upMbps)}",
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
                    sourceKey = shareBandKey(band),
                    supportId = "unknown",
                    operator = operator,
                    technology = if (band.gen == 5) RadioTechnology.NR_5G else RadioTechnology.LTE_4G,
                    bandLabel = band.value.toString(),
                    status = shareSiteStatusFromBandStatus(band.status),
                    azimuthDeg = shareExtractAzimuths(band).firstOrNull(),
                    supportHeightM = supportHeightMeters,
                    antennaHeightM = shareExtractPanelHeightMeters(band, supportHeightMeters),
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
            val key = shareBandKey(band)
            val carrierResult = carrierByKey[key]
            val bandwidth = carrierResult?.let {
                ShareThroughputBandwidth(valueMHz = it.bandwidthMHz, isEstimated = false)
            } ?: shareResolveBandwidthMHz(band)
            val isPlanned = band.status.contains("Projet", ignoreCase = true) ||
                band.status.contains("Approuv", ignoreCase = true) ||
                band.status.contains("Planned", ignoreCase = true)
            val generationAllowed = (band.gen == 4 && include4G) || (band.gen == 5 && include5G)
            val bandAllowed = enabledBandKeys.contains(key)
            val engineIncluded = carrierResult?.included == true
            val isIncluded = generationAllowed && bandAllowed && engineIncluded && (includePlanned || !isPlanned)
            val panelHeightMeters = shareExtractPanelHeightMeters(band, supportHeightMeters)
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
                label = shareBandLabel(band),
                generation = band.gen,
                modulationLabel = carrierResult?.let {
                    shareModulationLabel(it.dlModulationOrder, it.ulModulationOrder, it.dlMimoLayers, it.ulMimoLayers)
                } ?: shareModulationLabel(band.gen, engineProfile),
                bandwidthMHz = bandwidth.valueMHz,
                coneDistance = shareEstimateConeDistance(panelHeightMeters),
                azimuths = shareExtractAzimuths(band),
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

private fun shareThroughputPresetLabel(preset: ShareThroughputPreset): String {
    return when (preset) {
        ShareThroughputPreset.Conservative -> "Prudent"
        ShareThroughputPreset.Standard -> "Standard"
        ShareThroughputPreset.Maximum -> "Idéal"
        ShareThroughputPreset.Custom -> "Personnalisé"
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

private fun shareSiteStatusFromBandStatus(status: String): SiteRadioStatus {
    val normalized = status.lowercase(Locale.ROOT)
    return when {
        normalized.contains("commercial") || normalized.contains("ouvert") -> SiteRadioStatus.COMMERCIAL_OPEN
        normalized.contains("en service") || normalized.contains("service") -> SiteRadioStatus.IN_SERVICE
        normalized.contains("techniquement") || normalized.contains("operationnel") || normalized.contains("opérationnel") -> SiteRadioStatus.TECHNICALLY_OPERATIONAL
        normalized.contains("autor") || normalized.contains("approuv") || normalized.contains("projet") -> SiteRadioStatus.AUTHORIZED
        else -> SiteRadioStatus.UNKNOWN
    }
}

private fun shareResolveBandwidthMHz(band: FreqBand): ShareThroughputBandwidth {
    val ranges = shareFrequencyRangeRegex.findAll(band.rawFreq)
        .mapNotNull { match ->
            val start = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            val end = match.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (start == null || end == null) null else shareNormalizeRangeWidthToMHz(abs(end - start), match.groupValues.getOrNull(3).orEmpty())
        }
        .filter { it > 0.0 }
        .toList()

    if (ranges.isNotEmpty()) {
        val value = if (ranges.size > 1 && shareIsLikelyFddBand(band)) ranges.maxOrNull() ?: ranges.sum() else ranges.sum()
        return ShareThroughputBandwidth(valueMHz = value.coerceAtLeast(1.0), isEstimated = false)
    }

    return ShareThroughputBandwidth(valueMHz = shareDefaultBandwidthMHz(band.gen, band.value), isEstimated = true)
}

private fun shareNormalizeRangeWidthToMHz(width: Double, unit: String): Double {
    val lowerUnit = unit.lowercase(Locale.ROOT)
    return when {
        lowerUnit.contains("ghz") -> width * 1000.0
        lowerUnit.contains("khz") -> width / 1000.0
        else -> width
    }
}

private fun shareIsLikelyFddBand(band: FreqBand): Boolean {
    return band.value !in setOf(3500, 26000)
}

private fun shareDefaultBandwidthMHz(gen: Int, value: Int): Double {
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

private fun shareExtractPanelHeightMeters(band: FreqBand, fallbackSupportHeightMeters: Double?): Double? {
    val panelHeights = band.physDetails.flatMap { detail ->
        sharePanelHeightRegex.findAll(detail).mapNotNull { match ->
            match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        }.toList()
    }

    return panelHeights.maxOrNull() ?: fallbackSupportHeightMeters?.takeIf { it > SHARE_USER_DEVICE_HEIGHT_METERS }
}

private fun shareExtractAzimuths(band: FreqBand): List<Double> {
    return band.physDetails
        .flatMap { detail ->
            shareAzimuthRegex.findAll(detail).mapNotNull { match ->
                match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            }.toList()
        }
        .filter { it in 0.0..360.0 }
        .distinctBy { it.roundToInt() }
}

private fun shareEstimateConeDistance(heightMeters: Double?): ShareConeDistance? {
    val antennaHeight = heightMeters ?: return null
    val verticalDelta = (antennaHeight - SHARE_USER_DEVICE_HEIGHT_METERS).coerceAtLeast(1.0)
    val center = verticalDelta / tan(Math.toRadians(SHARE_NOMINAL_DOWNTILT_DEGREES))
    val near = verticalDelta / tan(Math.toRadians(SHARE_MAX_DOWNTILT_DEGREES))
    val far = verticalDelta / tan(Math.toRadians(SHARE_MIN_DOWNTILT_DEGREES))
    return ShareConeDistance(centerMeters = center, nearMeters = near, farMeters = far)
}

private fun shareBandLabel(band: FreqBand): String {
    return if (band.value > 0) {
        val base = "${band.gen}G ${band.value} MHz"
        shareRadioBandCode(band.gen, band.value)?.let { "$base ($it)" } ?: base
    } else {
        band.rawFreq.substringBefore(":").ifBlank { "${band.gen}G" }
    }
}

private fun shareRadioBandCode(gen: Int, value: Int): String? {
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
        else -> null
    }
}

private fun formatShareThroughput(valueMbps: Double): String {
    return if (valueMbps >= 1000.0) {
        String.format(Locale.US, "%.2f Gbit/s", valueMbps / 1000.0)
    } else {
        String.format(Locale.US, "%.0f Mbit/s", valueMbps)
    }
}

private fun formatShareDistanceMeters(valueMeters: Double): String {
    if (AppConfig.distanceUnit.intValue == 1) {
        val miles = valueMeters / 1609.34
        return if (miles < 0.1) "${(valueMeters * 3.28084).roundToInt()} ft" else String.format(Locale.US, "%.2f mi", miles)
    }

    return if (valueMeters >= 1000.0) {
        String.format(Locale.US, "%.2f km", valueMeters / 1000.0)
    } else {
        "${valueMeters.toInt()} m"
    }
}

private fun shareBandKey(band: FreqBand): String {
    return "${band.gen}:${band.value}:${band.rawFreq.substringBefore(":").trim()}"
}

private const val SHARE_USER_DEVICE_HEIGHT_METERS = 1.5
private const val SHARE_NOMINAL_DOWNTILT_DEGREES = 6.0
private const val SHARE_MIN_DOWNTILT_DEGREES = 4.0
private const val SHARE_MAX_DOWNTILT_DEGREES = 8.0

private val shareFrequencyRangeRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")
private val sharePanelHeightRegex = Regex("""\(([0-9]+(?:[.,][0-9]+)?)\s*m\)""", RegexOption.IGNORE_CASE)
private val shareAzimuthRegex = Regex("""([0-9]{1,3}(?:[.,][0-9]+)?)\s*(?:\u00B0|\u00C2\u00B0)""")

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

private data class ShareThroughputBandwidth(
    val valueMHz: Double,
    val isEstimated: Boolean
)

private data class ShareThroughputResult(
    val bands: List<ShareThroughputBandResult>
) {
    val includedBands: List<ShareThroughputBandResult>
        get() = bands.filter { it.isIncluded }
    val totalDownMbps: Double
        get() = includedBands.sumOf { it.downMbps }
    val totalUpMbps: Double
        get() = includedBands.sumOf { it.upMbps }
    val coneDistance: ShareConeDistance?
        get() {
            val distances = includedBands.mapNotNull { it.coneDistance }
            if (distances.isEmpty()) return null
            return ShareConeDistance(
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
    val coneDistance: ShareConeDistance?,
    val azimuths: List<Double>,
    val downMbps: Double,
    val upMbps: Double,
    val isIncluded: Boolean,
    val excludedReason: String?
)

private data class ShareConeDistance(
    val centerMeters: Double,
    val nearMeters: Double,
    val farMeters: Double
)
