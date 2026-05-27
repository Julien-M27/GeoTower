package fr.geotower.ui.components

import android.content.SharedPreferences
import fr.geotower.radio.MobileOperator
import fr.geotower.radio.RadioTechnology
import fr.geotower.radio.SiteRadioSystem
import fr.geotower.radio.SiteRadioStatus
import fr.geotower.radio.ThroughputProfile
import fr.geotower.utils.AppConfig
import fr.geotower.utils.FreqBand
import fr.geotower.utils.ThroughputPrefs
import fr.geotower.utils.radioBandCode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

data class ThroughputBandwidth(
    val valueMHz: Double,
    val isEstimated: Boolean
)

data class ThroughputConeDistance(
    val centerMeters: Double,
    val nearMeters: Double,
    val farMeters: Double
)

private val throughputFrequencyRangeRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")
private val throughputPanelHeightRegex = Regex("""\(([0-9]+(?:[.,][0-9]+)?)\s*m\)""", RegexOption.IGNORE_CASE)
private val throughputAzimuthRegex = Regex("""([0-9]{1,3}(?:[.,][0-9]+)?)\s*(?:\u00B0|\u00C2\u00B0)""")

fun resolveThroughputBandwidth(band: FreqBand): ThroughputBandwidth {
    val ranges = throughputFrequencyRangeRegex.findAll(band.rawFreq)
        .mapNotNull { match ->
            val start = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            val end = match.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (start == null || end == null) {
                null
            } else {
                normalizeThroughputRangeWidthToMHz(abs(end - start), match.groupValues.getOrNull(3).orEmpty())
            }
        }
        .filter { it > 0.0 }
        .toList()

    if (ranges.isNotEmpty()) {
        val value = if (ranges.size > 1 && isLikelyFddThroughputBand(band)) {
            ranges.maxOrNull() ?: ranges.sum()
        } else {
            ranges.sum()
        }
        return ThroughputBandwidth(valueMHz = value.coerceAtLeast(1.0), isEstimated = false)
    }

    return ThroughputBandwidth(
        valueMHz = defaultThroughputBandwidthMHz(band.gen, band.value),
        isEstimated = true
    )
}

fun normalizeThroughputRangeWidthToMHz(width: Double, unit: String): Double {
    val lowerUnit = unit.lowercase(Locale.ROOT)
    return when {
        lowerUnit.contains("ghz") -> width * 1000.0
        lowerUnit.contains("khz") -> width / 1000.0
        else -> width
    }
}

fun isLikelyFddThroughputBand(band: FreqBand): Boolean {
    return band.value !in setOf(3500, 26000)
}

fun defaultThroughputBandwidthMHz(gen: Int, value: Int): Double {
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

fun extractThroughputPanelHeightMeters(
    band: FreqBand,
    fallbackSupportHeightMeters: Double?,
    userDeviceHeightMeters: Double = 1.5
): Double? {
    val panelHeights = band.physDetails.flatMap { detail ->
        throughputPanelHeightRegex.findAll(detail).mapNotNull { match ->
            match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        }.toList()
    }

    return panelHeights.maxOrNull() ?: fallbackSupportHeightMeters?.takeIf { it > userDeviceHeightMeters }
}

fun extractThroughputAzimuths(band: FreqBand): List<Double> {
    return band.physDetails
        .flatMap { detail ->
            throughputAzimuthRegex.findAll(detail).mapNotNull { match ->
                match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            }.toList()
        }
        .filter { it in 0.0..360.0 }
        .distinctBy { it.roundToInt() }
}

fun estimateThroughputConeDistance(
    heightMeters: Double?,
    userDeviceHeightMeters: Double = 1.5,
    nominalDowntiltDegrees: Double = 6.0,
    minDowntiltDegrees: Double = 4.0,
    maxDowntiltDegrees: Double = 8.0
): ThroughputConeDistance? {
    val antennaHeight = heightMeters ?: return null
    val verticalDelta = (antennaHeight - userDeviceHeightMeters).coerceAtLeast(1.0)
    val center = verticalDelta / tan(Math.toRadians(nominalDowntiltDegrees))
    val near = verticalDelta / tan(Math.toRadians(maxDowntiltDegrees))
    val far = verticalDelta / tan(Math.toRadians(minDowntiltDegrees))
    return ThroughputConeDistance(centerMeters = center, nearMeters = near, farMeters = far)
}

fun buildThroughputRadioSystems(
    bands: List<FreqBand>,
    operator: MobileOperator,
    supportHeightMeters: Double?
): List<SiteRadioSystem> {
    return throughputCalculationBands(bands)
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
}

fun throughputCalculationBands(bands: List<FreqBand>): List<FreqBand> {
    return bands
        .filter { it.gen == 4 || it.gen == 5 }
        .filterNot { isHiddenThroughputBand(it) }
}

fun isHiddenThroughputBand(band: FreqBand): Boolean {
    return band.gen == 5 && band.value in setOf(1800, 26000)
}

fun isPlannedThroughputBand(band: FreqBand): Boolean {
    return band.status.contains("Projet", ignoreCase = true) ||
        band.status.contains("Approuv", ignoreCase = true) ||
        band.status.contains("Planned", ignoreCase = true)
}

fun isThroughputBandEnabledByDefault(
    band: FreqBand,
    prefs: SharedPreferences
): Boolean {
    if (band.value <= 0) return true
    val generationPrefix = when (band.gen) {
        4 -> "4g"
        5 -> "5g"
        else -> return true
    }
    return prefs.getBoolean(ThroughputPrefs.bandVisiblePrefKey(generationPrefix, band.value), true)
}

fun throughputModulationLabel(gen: Int, profile: ThroughputProfile): String {
    val assumptions = if (gen == 5) profile.nr else profile.lte
    return throughputModulationLabel(
        dlModulationOrder = assumptions.dlModulationOrder,
        ulModulationOrder = assumptions.ulModulationOrder,
        dlLayers = assumptions.dlMimoLayers,
        ulLayers = assumptions.ulMimoLayers
    )
}

fun throughputModulationLabel(
    dlModulationOrder: Int,
    ulModulationOrder: Int,
    dlLayers: Int,
    ulLayers: Int
): String {
    return "${throughputModulationName(dlModulationOrder)} ${throughputLayerLabel(dlLayers)} DL / ${throughputModulationName(ulModulationOrder)} ${throughputLayerLabel(ulLayers)} UL"
}

private fun throughputModulationName(modulationOrder: Int): String {
    return when (modulationOrder) {
        2 -> "QPSK"
        4 -> "16-QAM"
        6 -> "64-QAM"
        8 -> "256-QAM"
        10 -> "1024-QAM"
        else -> "$modulationOrder bits/symbol"
    }
}

private fun throughputLayerLabel(layers: Int): String {
    return if (layers <= 1) {
        "1 layer"
    } else {
        "MIMO ${layers}x${layers}"
    }
}

fun throughputBandLabel(band: FreqBand): String {
    return if (band.value > 0) {
        val base = "${band.gen}G ${band.value} MHz"
        radioBandCode(band.gen, band.value)?.let { "$base ($it)" } ?: base
    } else {
        band.rawFreq.substringBefore(":").ifBlank { "${band.gen}G" }
    }
}

fun throughputBandKey(band: FreqBand): String {
    return "${band.gen}:${band.value}:${band.rawFreq.substringBefore(":").trim()}"
}

fun siteRadioStatusFromBandStatus(status: String): SiteRadioStatus {
    val normalized = status.lowercase(Locale.ROOT)
    return when {
        normalized.contains("commercial") || normalized.contains("ouvert") -> SiteRadioStatus.COMMERCIAL_OPEN
        normalized.contains("en service") || normalized.contains("service") -> SiteRadioStatus.IN_SERVICE
        normalized.contains("techniquement") || normalized.contains("operationnel") || normalized.contains("op\u00e9rationnel") -> SiteRadioStatus.TECHNICALLY_OPERATIONAL
        normalized.contains("autor") || normalized.contains("approuv") || normalized.contains("projet") -> SiteRadioStatus.AUTHORIZED
        else -> SiteRadioStatus.UNKNOWN
    }
}

fun formatThroughputMbps(valueMbps: Double): String {
    return if (valueMbps >= 1000.0) {
        String.format(Locale.US, "%.2f Gbit/s", valueMbps / 1000.0)
    } else {
        String.format(Locale.US, "%.0f Mbit/s", valueMbps)
    }
}

fun formatThroughputDistanceMeters(valueMeters: Double): String {
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
