package fr.geotower.data.trip

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * En deçà, le cap rendu par le GPS n'a plus de sens : à l'arrêt il tourne au gré du bruit de mesure.
 * 1 m/s ≈ 3,6 km/h, soit une marche lente — assez bas pour suivre un piéton, assez haut pour ne pas
 * faire pivoter la carte quand on piétine au pied d'un pylône.
 */
const val NAV_MIN_SPEED_METERS_PER_SECOND = 1.0

/** Part du nouveau cap retenue à chaque mesure. Plus c'est bas, plus la carte tourne doucement. */
const val NAV_HEADING_SMOOTHING = 0.25

/**
 * Hauteur d'écran dont la caméra avance devant l'utilisateur : à 0,25, il se retrouve aux trois
 * quarts de la hauteur, donc en bas, avec la route devant lui — le cadrage des applis de guidage.
 */
const val NAV_CAMERA_AHEAD_FRACTION = 0.25

/** Au-delà, le repère sortirait par le bas de l'écran. */
const val NAV_CAMERA_MAX_AHEAD_FRACTION = 0.42

/**
 * Niveau de zoom du suivi. Serré, comme les applis de guidage : à cette échelle on lit les noms de
 * rue et on voit l'intersection arriver, ce qu'un cadrage plus large ne permet pas au volant.
 */
const val NAV_FOLLOW_ZOOM = 18.5

/**
 * En deçà, on considère qu'on est sur l'étape : inutile de calculer une route d'approche pour les
 * derniers mètres, la tournée elle-même prend le relais.
 */
const val NAV_APPROACH_MIN_METERS = 150.0

/**
 * Déplacement au-delà duquel la route d'approche est recalculée. Sans ce seuil, on la redemanderait
 * au service à chaque position reçue.
 */
const val NAV_APPROACH_REFRESH_METERS = 300.0

/**
 * Le cap de la carte pendant le suivi, tiré du **déplacement** et non de la boussole.
 *
 * Le magnétomètre est le mauvais capteur ici : un téléphone posé sur un support métallique, ou tenu
 * près d'un moteur, lui fait raconter n'importe quoi. Le cap de déplacement rendu par le GPS, lui,
 * est juste dès qu'on avance — c'est ce que font les applis de guidage.
 *
 * Deux garde-fous : sous [NAV_MIN_SPEED_METERS_PER_SECOND] on **garde le dernier cap** au lieu de
 * suivre un bruit de mesure, et chaque nouvelle valeur n'est retenue qu'en partie, pour que la carte
 * tourne au lieu de sauter.
 */
class TripHeadingSmoother(
    private val minSpeedMetersPerSecond: Double = NAV_MIN_SPEED_METERS_PER_SECOND,
    private val smoothingFactor: Double = NAV_HEADING_SMOOTHING
) {
    var headingDegrees: Double? = null
        private set

    /**
     * @param bearingDegrees cap de déplacement du GPS, ou `null` s'il n'en fournit pas.
     * @param speedMetersPerSecond vitesse instantanée, ou `null` si inconnue.
     * @return le cap à appliquer à la carte, ou `null` tant qu'aucun n'a pu être établi.
     */
    fun update(bearingDegrees: Double?, speedMetersPerSecond: Double?): Double? {
        if (bearingDegrees == null || !bearingDegrees.isFinite()) return headingDegrees
        if (speedMetersPerSecond == null || speedMetersPerSecond < minSpeedMetersPerSecond) {
            return headingDegrees
        }

        val target = normalizeDegrees(bearingDegrees)
        val current = headingDegrees
        headingDegrees = if (current == null) {
            // Premier cap fiable : on l'adopte tel quel, sinon la carte mettrait plusieurs secondes
            // à s'orienter au démarrage.
            target
        } else {
            normalizeDegrees(current + shortestDeltaDegrees(current, target) * smoothingFactor)
        }
        return headingDegrees
    }

    fun reset() {
        headingDegrees = null
    }
}

/** Ramène un angle dans `[0, 360[`. */
internal fun normalizeDegrees(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

/**
 * Écart signé le plus court entre deux caps, dans `]-180, 180]`. Sans lui, passer de 350° à 10°
 * ferait faire à la carte un tour complet dans le mauvais sens.
 */
internal fun shortestDeltaDegrees(fromDegrees: Double, toDegrees: Double): Double {
    var delta = normalizeDegrees(toDegrees) - normalizeDegrees(fromDegrees)
    if (delta > 180.0) delta -= 360.0
    if (delta <= -180.0) delta += 360.0
    return delta
}

/** Mètres couverts par un pixel d'écran, pour des tuiles de 256 px. */
fun metersPerPixel(latitude: Double, zoom: Double): Double =
    156_543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom)

/** Le point atteint en partant de là, dans cette direction, sur cette distance. */
fun destinationPoint(
    latitude: Double,
    longitude: Double,
    bearingDegrees: Double,
    distanceMeters: Double
): DoubleArray {
    val earthRadiusMeters = 6_371_000.0
    val angular = distanceMeters / earthRadiusMeters
    val bearing = Math.toRadians(bearingDegrees)
    val latRad = Math.toRadians(latitude)
    val lonRad = Math.toRadians(longitude)

    val targetLat = asin(sin(latRad) * cos(angular) + cos(latRad) * sin(angular) * cos(bearing))
    val targetLon = lonRad + atan2(
        sin(bearing) * sin(angular) * cos(latRad),
        cos(angular) - sin(latRad) * sin(targetLat)
    )
    return doubleArrayOf(Math.toDegrees(targetLat), normalizeLongitude(Math.toDegrees(targetLon)))
}

private fun normalizeLongitude(degrees: Double): Double = ((degrees + 540.0) % 360.0) - 180.0

/**
 * Où centrer la carte pour que l'utilisateur apparaisse **en bas** de l'écran, la route devant lui.
 *
 * On ne décale pas la vue : on vise un point situé devant, dans la direction de marche. Le cadrage
 * suit alors le cap tout seul, et le calcul reste vérifiable sans appareil.
 */
fun navigationCameraTarget(
    latitude: Double,
    longitude: Double,
    headingDegrees: Double,
    zoom: Double = NAV_FOLLOW_ZOOM,
    screenHeightPixels: Int,
    aheadFraction: Double = NAV_CAMERA_AHEAD_FRACTION
): DoubleArray {
    if (screenHeightPixels <= 0 || aheadFraction <= 0.0) {
        return doubleArrayOf(latitude, longitude)
    }
    val aheadMeters = screenHeightPixels * aheadFraction * metersPerPixel(latitude, zoom)
    return destinationPoint(latitude, longitude, headingDegrees, aheadMeters)
}
