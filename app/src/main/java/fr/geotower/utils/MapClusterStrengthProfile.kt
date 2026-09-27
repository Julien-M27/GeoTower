package fr.geotower.utils

import kotlin.math.roundToInt

/** Pure conversions for the mobile antenna clustering strength setting. */
internal object MapClusterStrengthProfile {
    fun normalize(value: Int): Int = value.coerceIn(0, 100)

    fun nearbyRadiusPx(zoom: Double, strength: Int): Int {
        val baseRadius = when {
            zoom < 14.0 -> 220
            zoom < 15.5 -> 150
            zoom < 17.0 -> 90
            else -> 60
        }
        return (baseRadius * normalize(strength) / 100.0).roundToInt()
    }

    fun aggregationZoom(mapZoom: Double, strength: Int): Double {
        if (mapZoom >= 13.0) return mapZoom
        val finestZoom = maxOf(mapZoom, 12.5)
        val finerLevelFraction = (100 - normalize(strength)) / 100.0
        return mapZoom + (finestZoom - mapZoom) * finerLevelFraction
    }

    fun iconScale(strength: Int): Float =
        0.6f + 0.4f * normalize(strength) / 100f

    fun showTowerPictogram(strength: Int): Boolean = normalize(strength) > 25

    /** Keep the colored core the same size as simple mode at the 25% artwork transition. */
    fun antennaCoreRadiusUnits(strength: Int): Float {
        val normalized = normalize(strength)
        if (!showTowerPictogram(normalized)) return 20f
        val detailedFraction = (normalized - 25) / 75f
        return 20f + 25f * detailedFraction
    }
}
