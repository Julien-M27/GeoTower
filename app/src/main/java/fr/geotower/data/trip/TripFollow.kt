package fr.geotower.data.trip

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Où en est la tournée, vu depuis la position courante. Tout est calculé ici, sans Android : c'est
 * ce qui rend le suivi testable sans appareil ni GPS.
 */
data class TripFollowStatus(
    /** Prochaine étape à relever, ou `null` quand tout est fait. */
    val nextStepIndex: Int?,
    val distanceToNextMeters: Double?,
    /** Distance qu'il reste à parcourir, étape suivante comprise. */
    val remainingDistanceMeters: Double,
    /** Écart au tracé calculé, ou `null` si aucun segment n'est encore calculé. */
    val offRouteMeters: Double?,
    val isOffRoute: Boolean,
    /** Étapes atteintes à l'instant, à cocher : indices dans [TripPlan.steps]. */
    val reachedStepIndices: List<Int>
)

/**
 * Rayon d'approche par défaut. Un relevé se fait au pied du pylône : plus large, on cocherait une
 * étape en passant sur la route d'à côté ; plus étroit, un GPS urbain imprécis ne cocherait jamais.
 */
const val TRIP_REACHED_RADIUS_METERS = 80.0

/**
 * Écart au tracé au-delà duquel on prévient. Large à dessein : une route à deux chaussées, un
 * parking ou un GPS qui dérive sous les arbres écartent de plusieurs dizaines de mètres sans qu'on
 * se soit trompé de chemin.
 */
const val TRIP_OFF_ROUTE_THRESHOLD_METERS = 120.0

fun computeTripFollowStatus(
    plan: TripPlan,
    latitude: Double,
    longitude: Double,
    reachedRadiusMeters: Double = TRIP_REACHED_RADIUS_METERS,
    offRouteThresholdMeters: Double = TRIP_OFF_ROUTE_THRESHOLD_METERS
): TripFollowStatus {
    val reached = plan.steps.mapIndexedNotNull { index, step ->
        if (step.visitedAtMillis != null) return@mapIndexedNotNull null
        val distance = haversineMeters(latitude, longitude, step.latitude, step.longitude)
        if (distance <= reachedRadiusMeters) index else null
    }

    // La prochaine étape est la première non relevée dans l'ordre de la tournée -- pas la plus
    // proche : sauter une étape parce qu'on en frôle une autre casserait l'ordre voulu.
    val nextIndex = plan.steps.indexOfFirst { it.visitedAtMillis == null }.takeIf { it >= 0 }
    val distanceToNext = nextIndex?.let {
        haversineMeters(latitude, longitude, plan.steps[it].latitude, plan.steps[it].longitude)
    }

    val remaining = if (nextIndex == null) {
        0.0
    } else {
        // Ce qu'il reste : le trajet à vol d'oiseau jusqu'à l'étape suivante, puis les segments
        // calculés au-delà. On ne prétend pas connaître la route depuis un point hors tracé.
        val after = plan.legPairs()
            .filter { it.first >= nextIndex }
            .sumOf { plan.legBetween(it.first, it.second)?.distanceMeters ?: 0.0 }
        (distanceToNext ?: 0.0) + after
    }

    val offRoute = distanceToRouteMeters(plan, latitude, longitude)
    return TripFollowStatus(
        nextStepIndex = nextIndex,
        distanceToNextMeters = distanceToNext,
        remainingDistanceMeters = remaining,
        offRouteMeters = offRoute,
        isOffRoute = offRoute != null && offRoute > offRouteThresholdMeters,
        reachedStepIndices = reached
    )
}

/**
 * Distance au tracé calculé, en mètres. `null` quand aucun segment n'est calculé : sans route
 * connue, on ne peut pas dire qu'on s'en écarte.
 */
fun distanceToRouteMeters(plan: TripPlan, latitude: Double, longitude: Double): Double? {
    var best: Double? = null
    plan.legPairs().forEach { (fromIndex, toIndex) ->
        val points = plan.legBetween(fromIndex, toIndex)?.points() ?: return@forEach
        for (index in 0 until points.size - 1) {
            val distance = distanceToSegmentMeters(
                latitude, longitude,
                points[index][0], points[index][1],
                points[index + 1][0], points[index + 1][1]
            )
            if (best == null || distance < best!!) best = distance
        }
    }
    return best
}

/**
 * Distance d'un point à un segment, en mètres.
 *
 * Le calcul passe par une projection plane locale centrée sur le point interrogé : à l'échelle d'un
 * segment routier, l'erreur est négligeable, et cela évite une trigonométrie sphérique inutile
 * dans une boucle appelée sur des milliers de points à chaque position reçue.
 */
internal fun distanceToSegmentMeters(
    latitude: Double,
    longitude: Double,
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val metersPerLatitudeDegree = 110_540.0
    val metersPerLongitudeDegree = 111_320.0 * cos(Math.toRadians(latitude))

    val segmentX = (toLongitude - fromLongitude) * metersPerLongitudeDegree
    val segmentY = (toLatitude - fromLatitude) * metersPerLatitudeDegree
    val pointX = (longitude - fromLongitude) * metersPerLongitudeDegree
    val pointY = (latitude - fromLatitude) * metersPerLatitudeDegree

    val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
    // Segment dégénéré (deux points confondus) : c'est une simple distance de point à point.
    val projection = if (segmentLengthSquared <= 0.0) {
        0.0
    } else {
        ((pointX * segmentX + pointY * segmentY) / segmentLengthSquared).coerceIn(0.0, 1.0)
    }

    val closestX = projection * segmentX
    val closestY = projection * segmentY
    val deltaX = pointX - closestX
    val deltaY = pointY - closestY
    return sqrt(deltaX * deltaX + deltaY * deltaY)
}
