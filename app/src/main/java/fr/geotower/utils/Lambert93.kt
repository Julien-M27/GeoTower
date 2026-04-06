package fr.geotower.utils

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Lambert93 {
    private const val M_PI = Math.PI

    // Constantes Lambert 93 (France)
    private const val A = 6378137.0 // Demi-grand axe
    private const val E = 0.08181919106 // Première excentricité
    private const val LC = 46.5 * M_PI / 180.0 // Latitude de référence
    private const val L0 = 3.0 * M_PI / 180.0  // Longitude d'origine (Méridien de Greenwich + 3°)
    private const val XS = 700000.0 // Faux Est
    private const val YS = 6600000.0 // Faux Nord

    // Constantes pré-calculées pour l'algo
    private const val N = 0.7256077650
    private const val C = 11754255.426
    private const val XS_N = 700000.0

    /**
     * Convertit Lambert 93 (X, Y) vers GPS WGS84 (Lat, Lon)
     * @param x Coordonnée X (Lambert)
     * @param y Coordonnée Y (Lambert)
     * @return Pair(Latitude, Longitude)
     */
    fun toWGS84(x: Double, y: Double): Pair<Double, Double> {
        // Algorithme inverse de projection conique conforme de Lambert
        val dx = x - XS
        val dy = YS - y
        val r = sqrt(dx * dx + dy * dy)
        val gamma = atan(dx / (YS - C * exp(-1 * N))) // Correction approximation

        // Formule simplifiée mais précise pour le mobile
        // (Pour une précision au mètre près, on utilise une itération, mais ici c'est suffisant)

        // On repasse par l'algo standard IGN inverse
        val r1 = sqrt((x - 700000) * (x - 700000) + (y - 12655612.05) * (y - 12655612.05))
        val gamma1 = atan((x - 700000) / (12655612.05 - y))

        // Latitude isométrique
        var latIso = -1 / N * ln(abs(r1 / C))

        // Latitude (approximation itérative rapide)
        var lat = 2 * atan(exp(latIso)) - M_PI / 2
        var delta = 1.0
        while (delta > 0.000000001) {
            val prevLat = lat
            lat = 2 * atan(product(lat) * exp(latIso)) - M_PI / 2
            delta = abs(lat - prevLat)
        }

        val lon = (gamma1 / N) + L0

        return Pair(Math.toDegrees(lat), Math.toDegrees(lon))
    }

    private fun product(lat: Double): Double {
        val sinLat = sin(lat)
        return ((1 + E * sinLat) / (1 - E * sinLat)).pow(E / 2)
    }
}