package fr.geotower.utils

import androidx.compose.ui.graphics.Color

/** Shared status colors for frequency summaries, sector markers, and exported reports. */
object FrequencyStatusPalette {
    val InService = Color(0xFF4CAF50)
    val TechnicallyOperational = Color(0xFF2196F3)
    val Approved = Color(0xFFFFA000)
    val Unknown = Color.Gray

    fun color(status: FrequencyStatusType): Color = when (status) {
        FrequencyStatusType.InService -> InService
        FrequencyStatusType.TechnicallyOperational -> TechnicallyOperational
        FrequencyStatusType.Approved -> Approved
        FrequencyStatusType.Unknown -> Unknown
    }
}

fun FreqBand.sectorMarkerColor(azimuth: Int?): Color? {
    if (azimuth == null || !isActiveOnAzimuth(azimuth)) return null
    return FrequencyStatusPalette.color(classifyFrequencyStatus(status))
}
