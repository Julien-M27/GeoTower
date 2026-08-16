package fr.geotower.data.trip

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Écart maximal au tracé pour se considérer dessus. Au-delà, on ne sait plus où l'on en est : le
 * tracé reste alors affiché entier plutôt que d'être effacé au petit bonheur.
 *
 * Plus serré que [TRIP_OFF_ROUTE_THRESHOLD_METERS], qui sert à prévenir qu'on s'égare : effacer le
 * chemin derrière soi demande d'être vraiment dessus.
 */
const val TRIP_PROGRESS_SNAP_METERS = 60.0

/**
 * Poids de la continuité face à la géométrie, quand plusieurs endroits du tracé sont à portée.
 *
 * Une tournée qui revient à son point de départ, ou qui longe deux fois la même avenue, se recoupe :
 * la seule distance au tracé désignerait alors indifféremment l'aller ou le retour, et le trait
 * effacé sauterait d'un bout à l'autre. On départage par le kilométrage déjà parcouru. À 0,05, un
 * kilomètre d'écart le long du tracé pèse autant que cinquante mètres d'écart perpendiculaire : la
 * géométrie décide, la continuité tranche les ex aequo.
 */
private const val CONTINUITY_WEIGHT = 0.05

/**
 * Où l'on en est sur le tracé d'une tournée.
 *
 * Recalculé à chaque position, jamais accumulé : faire demi-tour fait naturellement reculer la
 * progression, et le chemin effacé réapparaît. Rien n'est consommé de façon définitive.
 */
data class TripRouteProgress(
    /** Rang du segment dans l'ordre de `TripPlan.legPairs()`. */
    val legOrdinal: Int,
    /** Distance parcourue depuis le début de CE segment. */
    val distanceIntoLegMeters: Double,
    /** Distance depuis le départ de la tournée. Sert à départager les recoupements. */
    val distanceFromStartMeters: Double,
    /** Écart perpendiculaire au tracé, en mètres. */
    val offRouteMeters: Double,
    /** Cap du tracé à cet endroit, en degrés depuis le nord. */
    val bearingDegrees: Double
)

/**
 * Projette une position sur le tracé, segment par segment.
 *
 * @param legs les segments de la tournée dans l'ordre, chacun avec ses points.
 * @param previousDistanceFromStartMeters progression du relevé précédent, pour lever l'ambiguïté
 *   d'un tracé qui se recoupe. `null` à la première position.
 * @return `null` si l'on est trop loin du tracé pour savoir où l'on en est.
 */
fun tripRouteProgress(
    legs: List<List<DoubleArray>>,
    latitude: Double,
    longitude: Double,
    previousDistanceFromStartMeters: Double? = null,
    maxSnapMeters: Double = TRIP_PROGRESS_SNAP_METERS
): TripRouteProgress? {
    var best: TripRouteProgress? = null
    var bestScore = Double.MAX_VALUE
    var travelled = 0.0

    legs.forEachIndexed { legOrdinal, points ->
        var intoLeg = 0.0
        for (index in 0 until points.size - 1) {
            val from = points[index]
            val to = points[index + 1]
            val segment = projectOntoSegment(latitude, longitude, from[0], from[1], to[0], to[1])
            val segmentLength = haversineMeters(from[0], from[1], to[0], to[1])

            if (segment.distanceMeters <= maxSnapMeters) {
                val alongSegment = segmentLength * segment.ratio
                val fromStart = travelled + intoLeg + alongSegment
                val score = segment.distanceMeters + if (previousDistanceFromStartMeters == null) {
                    0.0
                } else {
                    abs(fromStart - previousDistanceFromStartMeters) * CONTINUITY_WEIGHT
                }
                if (score < bestScore) {
                    bestScore = score
                    best = TripRouteProgress(
                        legOrdinal = legOrdinal,
                        distanceIntoLegMeters = intoLeg + alongSegment,
                        distanceFromStartMeters = fromStart,
                        offRouteMeters = segment.distanceMeters,
                        bearingDegrees = bearingDegrees(from[0], from[1], to[0], to[1])
                    )
                }
            }
            intoLeg += segmentLength
        }
        travelled += intoLeg
    }
    return best
}

/**
 * La part d'un segment qui reste à parcourir, à partir de la distance déjà faite dedans.
 *
 * Le premier point rendu est **exactement** l'endroit atteint, pas le sommet suivant : sans cette
 * interpolation, le trait se raccourcirait par à-coups d'un sommet à l'autre au lieu de fondre sous
 * le repère.
 */
fun remainingLegPoints(points: List<DoubleArray>, fromDistanceMeters: Double): List<DoubleArray> {
    if (points.size < 2) return points
    if (fromDistanceMeters <= 0.0) return points

    var travelled = 0.0
    for (index in 0 until points.size - 1) {
        val from = points[index]
        val to = points[index + 1]
        val length = haversineMeters(from[0], from[1], to[0], to[1])
        if (travelled + length > fromDistanceMeters) {
            val ratio = if (length <= 0.0) 0.0 else ((fromDistanceMeters - travelled) / length)
                .coerceIn(0.0, 1.0)
            val cut = doubleArrayOf(
                from[0] + (to[0] - from[0]) * ratio,
                from[1] + (to[1] - from[1]) * ratio
            )
            return listOf(cut) + points.subList(index + 1, points.size)
        }
        travelled += length
    }
    // Segment entièrement parcouru : plus rien à dessiner.
    return emptyList()
}

/**
 * Le cap à afficher : celui du **tracé** quand la mesure va dans le même sens, la mesure sinon.
 *
 * Un cap GPS oscille de quelques degrés en permanence, et la flèche frétille alors que la route,
 * elle, est droite. Tant qu'on va dans le sens du tracé, c'est lui qui fait foi : la flèche suit la
 * ligne. Au-delà de la tolérance — on tourne, on fait demi-tour, on quitte la route — c'est la
 * mesure qui reprend la main, sinon la flèche mentirait sur ce qu'on est en train de faire.
 */
fun headingAlignedToRoute(
    measuredDegrees: Double?,
    routeDegrees: Double?,
    toleranceDegrees: Double = NAV_ROUTE_ALIGN_TOLERANCE_DEGREES
): Double? {
    if (routeDegrees == null) return measuredDegrees
    if (measuredDegrees == null) return routeDegrees
    val delta = abs(shortestDeltaDegrees(measuredDegrees, routeDegrees))
    return if (delta <= toleranceDegrees) routeDegrees else measuredDegrees
}

/**
 * Écart en deçà duquel on considère qu'on suit le tracé. Assez large pour absorber le bruit du GPS
 * et une trajectoire qui coupe un virage, assez étroit pour ne pas aligner la flèche sur une route
 * qu'on est en train de quitter.
 */
const val NAV_ROUTE_ALIGN_TOLERANCE_DEGREES = 45.0

/** Projection d'un point sur un segment : à quelle distance, et à quelle fraction du segment. */
private class SegmentProjection(val distanceMeters: Double, val ratio: Double)

/**
 * Projection plane locale centrée sur le point interrogé : à l'échelle d'un segment routier
 * l'erreur est négligeable, et cela évite une trigonométrie sphérique dans une boucle appelée sur
 * des milliers de points à chaque position reçue.
 */
private fun projectOntoSegment(
    latitude: Double,
    longitude: Double,
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): SegmentProjection {
    val metersPerLatitudeDegree = 110_540.0
    val metersPerLongitudeDegree = 111_320.0 * cos(Math.toRadians(latitude))

    val segmentX = (toLongitude - fromLongitude) * metersPerLongitudeDegree
    val segmentY = (toLatitude - fromLatitude) * metersPerLatitudeDegree
    val pointX = (longitude - fromLongitude) * metersPerLongitudeDegree
    val pointY = (latitude - fromLatitude) * metersPerLatitudeDegree

    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    val ratio = if (lengthSquared <= 0.0) {
        0.0
    } else {
        ((pointX * segmentX + pointY * segmentY) / lengthSquared).coerceIn(0.0, 1.0)
    }
    return SegmentProjection(
        distanceMeters = hypot(pointX - ratio * segmentX, pointY - ratio * segmentY),
        ratio = ratio
    )
}
