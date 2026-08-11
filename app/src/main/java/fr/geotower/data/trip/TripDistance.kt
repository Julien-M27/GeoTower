package fr.geotower.data.trip

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance à vol d'oiseau, en mètres.
 *
 * Deux usages, tous deux internes : le garde-fou d'éloignement avant d'interroger le service
 * d'itinéraire, et la matrice de l'optimisation d'ordre. **Jamais pour afficher la longueur d'un
 * trajet** — celle-ci vient de la route, sans quoi une tournée de montagne serait annoncée à la
 * moitié de sa vraie distance.
 *
 * Dans son propre fichier plutôt que dans le calculateur : il sert aussi à l'optimiseur, qui est du
 * Kotlin pur et doit rester testable sans charger la façade réseau.
 */
internal fun haversineMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val fromLatRad = Math.toRadians(fromLatitude)
    val toLatRad = Math.toRadians(toLatitude)
    val deltaLat = Math.toRadians(toLatitude - fromLatitude)
    val deltaLon = Math.toRadians(toLongitude - fromLongitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(fromLatRad) * cos(toLatRad) * sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}
