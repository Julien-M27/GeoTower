package fr.geotower.utils

import java.util.Locale
import kotlin.math.roundToInt

data class NearbyDistanceLabel(
    val primaryText: String,
    val secondaryText: String? = null
)

fun formatNearbyDistanceLabel(distanceMeters: Int, useMiles: Boolean): NearbyDistanceLabel {
    return if (useMiles) {
        val distanceMiles = distanceMeters / 1609.34f
        if (distanceMiles < 0.1f) {
            NearbyDistanceLabel(primaryText = "${(distanceMeters * 3.28084f).toInt()} ft")
        } else {
            NearbyDistanceLabel(primaryText = String.format(Locale.US, "%.2f mi", distanceMiles))
        }
    } else if (distanceMeters >= 1000) {
        NearbyDistanceLabel(
            primaryText = String.format(Locale.FRANCE, "%.2f", distanceMeters / 1000f),
            secondaryText = "km"
        )
    } else {
        NearbyDistanceLabel(primaryText = "$distanceMeters m")
    }
}

fun formatSiteDistanceMeters(
    distanceMeters: Double,
    distanceUnit: Int = AppConfig.distanceUnit.intValue
): String {
    return if (distanceUnit == 1) {
        val feet = distanceMeters * 3.28084
        val miles = distanceMeters / 1609.344
        if (miles >= 0.1) String.format(Locale.US, "%.2f mi", miles) else "${feet.roundToInt()} ft"
    } else {
        if (distanceMeters >= 1000.0) {
            String.format(Locale.US, "%.3f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.toInt()} m"
        }
    }
}
