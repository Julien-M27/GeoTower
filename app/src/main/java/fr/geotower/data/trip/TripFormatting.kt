package fr.geotower.data.trip

import kotlin.math.roundToInt

/**
 * Durée d'un trajet, en texte court : « 45 min », « 2 h », « 2 h 15 ».
 *
 * Les abréviations arrivent en paramètre plutôt que d'être écrites ici : elles vivent dans
 * `strings.xml` et se traduisent donc, alors que cette fonction reste pure et testable.
 */
fun formatTripDuration(seconds: Double, hourLabel: String, minuteLabel: String): String {
    if (!seconds.isFinite() || seconds <= 0.0) return "0 $minuteLabel"

    val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes $minuteLabel"
        minutes == 0 -> "$hours $hourLabel"
        else -> "$hours $hourLabel $minutes"
    }
}
