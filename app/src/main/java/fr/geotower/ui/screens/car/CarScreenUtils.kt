package fr.geotower.ui.screens.car

import android.location.Location
import fr.geotower.data.models.LocalisationEntity
import java.util.Locale

internal fun formatCarDistance(distanceMeters: Float): String {
    return if (distanceMeters >= 1000f) {
        String.format(Locale.FRANCE, "%.1f km", distanceMeters / 1000f)
    } else {
        "${distanceMeters.toInt()} m"
    }
}

internal fun calculateCarDistance(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Float {
    val result = FloatArray(1)
    Location.distanceBetween(fromLatitude, fromLongitude, toLatitude, toLongitude, result)
    return result[0]
}

internal fun LocalisationEntity.operatorSummary(): String {
    return operateur
        ?.split(Regex("[/,\\-]"))
        ?.map { it.trim().uppercase(Locale.FRANCE) }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }
        ?: "Operateur inconnu"
}
