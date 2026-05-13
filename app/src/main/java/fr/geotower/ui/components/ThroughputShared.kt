package fr.geotower.ui.components

import fr.geotower.radio.SiteRadioStatus
import fr.geotower.utils.AppConfig
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

fun throughputBandLabel(band: FreqBand): String {
    return if (band.value > 0) {
        val base = "${band.gen}G ${band.value} MHz"
        throughputRadioBandCode(band.gen, band.value)?.let { "$base ($it)" } ?: base
    } else {
        band.rawFreq.substringBefore(":").ifBlank { "${band.gen}G" }
    }
}

fun throughputRadioBandCode(gen: Int, value: Int): String? {
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
