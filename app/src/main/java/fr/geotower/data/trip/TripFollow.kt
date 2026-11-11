package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi
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
    /** Temps qu'il reste, arrêts sur les étapes non relevées compris. */
    val remainingDurationSeconds: Double,
    /** Écart au tracé calculé, ou `null` si aucun segment n'est encore calculé. */
    val offRouteMeters: Double?,
    val isOffRoute: Boolean,
    /** Étapes atteintes à l'instant, à cocher : indices dans [TripPlan.steps]. */
    val reachedStepIndices: List<Int>,
    /** Tronçon de l'itinéraire sur lequel se trouve l'utilisateur, le cas échéant. */
    val currentLeg: TripCurrentLeg? = null
)

/**
 * Un tronçon de l'itinéraire sur lequel l'utilisateur se trouve actuellement.
 */
data class TripCurrentLeg(
    /** Indice de la tranche dans [TripPlan.legPairs]. */
    val legOrdinal: Int,
    /** Indice de l'étape de départ du tronçon dans [TripPlan.steps]. */
    val fromStepIndex: Int,
    /** Indice de l'étape d'arrivée du tronçon dans [TripPlan.steps] (l'étape immédiatement devant). */
    val toStepIndex: Int,
    /** Distance perpendiculaire au tracé en mètres. */
    val distanceToLegMeters: Double,
    /** Distance restante à parcourir le long de ce tronçon jusqu'à [toStepIndex]. */
    val remainingDistanceOnLegMeters: Double
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

/**
 * Détecte si la position se trouve sur l'un des tronçons calculés de la tournée.
 * Rend le tronçon le plus proche si l'écart est inférieur à [maxSnapMeters], ou `null`.
 */
fun detectCurrentLeg(
    plan: TripPlan,
    latitude: Double,
    longitude: Double,
    maxSnapMeters: Double = TRIP_OFF_ROUTE_THRESHOLD_METERS
): TripCurrentLeg? {
    val legPairs = plan.legPairs()
    if (legPairs.isEmpty()) return null

    var bestLeg: TripCurrentLeg? = null
    var bestDistance = Double.MAX_VALUE

    legPairs.forEachIndexed { ordinal, (fromIndex, toIndex) ->
        val leg = plan.legBetween(fromIndex, toIndex) ?: return@forEachIndexed
        val points = leg.points()
        if (points.size < 2) return@forEachIndexed

        var minSegmentDistance = Double.MAX_VALUE
        var bestSegmentIndex = -1
        var bestRatio = 0.0

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val projection = projectOntoSegment(latitude, longitude, p1[0], p1[1], p2[0], p2[1])
            if (projection.distanceMeters < minSegmentDistance) {
                minSegmentDistance = projection.distanceMeters
                bestSegmentIndex = i
                bestRatio = projection.ratio
            }
        }

        if (minSegmentDistance <= maxSnapMeters && minSegmentDistance < bestDistance) {
            bestDistance = minSegmentDistance
            var remainingMeters = 0.0
            if (bestSegmentIndex >= 0) {
                val p1 = points[bestSegmentIndex]
                val p2 = points[bestSegmentIndex + 1]
                val currentSegmentLength = haversineMeters(p1[0], p1[1], p2[0], p2[1])
                remainingMeters += currentSegmentLength * (1.0 - bestRatio)
                for (j in (bestSegmentIndex + 1) until (points.size - 1)) {
                    remainingMeters += haversineMeters(points[j][0], points[j][1], points[j + 1][0], points[j + 1][1])
                }
            }
            bestLeg = TripCurrentLeg(
                legOrdinal = ordinal,
                fromStepIndex = fromIndex,
                toStepIndex = toIndex,
                distanceToLegMeters = minSegmentDistance,
                remainingDistanceOnLegMeters = remainingMeters
            )
        }
    }
    return bestLeg
}

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

    val currentLeg = detectCurrentLeg(plan, latitude, longitude, offRouteThresholdMeters)

    // Si l'utilisateur est déjà sur un tronçon menant à une étape non visitée, on cible cette
    // étape immédiatement devant lui. Sinon, repli sur la première étape non relevée dans l'ordre.
    val legTargetIndex = currentLeg?.toStepIndex?.takeIf {
        it in plan.steps.indices && plan.steps[it].visitedAtMillis == null
    }
    val fallbackNextIndex = plan.steps.indexOfFirst { it.visitedAtMillis == null }.takeIf { it >= 0 }
    val nextIndex = legTargetIndex ?: fallbackNextIndex

    val distanceToNext = if (currentLeg != null && currentLeg.toStepIndex == nextIndex) {
        currentLeg.remainingDistanceOnLegMeters
    } else {
        nextIndex?.let {
            haversineMeters(latitude, longitude, plan.steps[it].latitude, plan.steps[it].longitude)
        }
    }

    // Ce qu'il reste : le parcours le long du tracé jusqu'à l'étape suivante, puis les segments
    // calculés au-delà.
    val remainingLegs = if (nextIndex == null) {
        emptyList()
    } else {
        plan.legPairs().filter { it.first >= nextIndex }
    }
    val remaining = if (nextIndex == null) {
        0.0
    } else {
        (distanceToNext ?: 0.0) +
            remainingLegs.sumOf { plan.legBetween(it.first, it.second)?.distanceMeters ?: 0.0 }
    }

    val remainingDuration = if (nextIndex == null) {
        0.0
    } else {
        val legsDuration = remainingLegs
            .sumOf { plan.legBetween(it.first, it.second)?.durationSeconds ?: 0.0 }
        val stops = plan.steps.drop(nextIndex).count { it.visitedAtMillis == null } *
            plan.stopDurationMinutes * 60.0
        legsDuration + (distanceToNext ?: 0.0) / approachSpeedMetersPerSecond(plan, nextIndex) + stops
    }

    val offRoute = currentLeg?.distanceToLegMeters ?: distanceToRouteMeters(plan, latitude, longitude)
    return TripFollowStatus(
        nextStepIndex = nextIndex,
        distanceToNextMeters = distanceToNext,
        remainingDistanceMeters = remaining,
        remainingDurationSeconds = remainingDuration,
        offRouteMeters = offRoute,
        isOffRoute = offRoute != null && offRoute > offRouteThresholdMeters,
        reachedStepIndices = reached,
        currentLeg = currentLeg
    )
}

/** Repli quand aucun segment calculé ne renseigne le rythme réel : ~40 km/h et ~4,5 km/h. */
private const val DEFAULT_CAR_SPEED_METERS_PER_SECOND = 11.0
private const val DEFAULT_WALK_SPEED_METERS_PER_SECOND = 1.25

/**
 * À quelle vitesse estimer les mètres qui restent jusqu'à l'étape suivante.
 *
 * On préfère le rythme **réellement observé sur le segment qui y mène** (sa distance divisée par sa
 * durée, telles que le service les a rendues) : il tient compte du relief, des limitations et du
 * type de voie, là où une constante ne saurait rien de tout ça. Sans ce segment, on retombe sur une
 * vitesse de repli propre au profil.
 */
private fun approachSpeedMetersPerSecond(plan: TripPlan, nextIndex: Int): Double {
    val leadingLeg = plan.legBetween(nextIndex - 1, nextIndex)
    if (leadingLeg != null && leadingLeg.durationSeconds > 0.0 && leadingLeg.distanceMeters > 0.0) {
        return leadingLeg.distanceMeters / leadingLeg.durationSeconds
    }
    return if (plan.profile == RouteApi.PROFILE_PEDESTRIAN) {
        DEFAULT_WALK_SPEED_METERS_PER_SECOND
    } else {
        DEFAULT_CAR_SPEED_METERS_PER_SECOND
    }
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
