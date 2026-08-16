package fr.geotower.data.trip

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Une flèche de sens posée sur le tracé : où elle va, et vers où elle pointe. */
data class TripArrow(
    val latitude: Double,
    val longitude: Double,
    /** Cap géographique, 0 = nord, croissant vers l'est. */
    val bearingDegrees: Double,
    /**
     * Distance depuis le début du tracé qui la porte. C'est elle qui dit si la flèche est déjà
     * derrière nous pendant un suivi — la comparer sur les coordonnées demanderait de reprojeter.
     */
    val distanceAlongMeters: Double = 0.0
)

/** Au-delà, un long segment couvrirait la carte de flèches sans rien apprendre de plus. */
const val TRIP_ARROWS_PER_LEG = 6

/** En deçà, deux flèches se chevaucheraient sur un tracé sinueux. */
const val TRIP_ARROW_MIN_SPACING_METERS = 150.0

/**
 * Répartit des flèches de sens le long d'un tracé.
 *
 * L'espacement est calculé **par segment** et non en distance fixe : à l'échelle d'un département,
 * une flèche tous les 150 m en produirait des milliers ; sur 300 m de rue, une tous les 5 km n'en
 * produirait aucune. Chaque segment reçoit donc au plus [maxArrows] flèches, réparties
 * régulièrement, jamais plus serrées que [minSpacingMeters].
 *
 * La première est posée à une demi-période du début, pour ne pas se superposer à la pastille de
 * l'étape qui s'y trouve déjà.
 */
fun tripDirectionArrows(
    points: List<DoubleArray>,
    maxArrows: Int = TRIP_ARROWS_PER_LEG,
    minSpacingMeters: Double = TRIP_ARROW_MIN_SPACING_METERS
): List<TripArrow> {
    if (points.size < 2 || maxArrows <= 0) return emptyList()

    val segmentLengths = DoubleArray(points.size - 1)
    var total = 0.0
    for (index in 0 until points.size - 1) {
        val length = haversineMeters(
            points[index][0], points[index][1],
            points[index + 1][0], points[index + 1][1]
        )
        segmentLengths[index] = length
        total += length
    }
    if (total <= 0.0) return emptyList()

    val spacing = maxOf(minSpacingMeters, total / maxArrows)
    val arrows = ArrayList<TripArrow>(maxArrows)

    var target = spacing / 2.0
    var travelled = 0.0
    var segment = 0
    while (target < total && arrows.size < maxArrows) {
        // Avance jusqu'au segment qui contient la distance visée.
        while (segment < segmentLengths.size && travelled + segmentLengths[segment] < target) {
            travelled += segmentLengths[segment]
            segment++
        }
        if (segment >= segmentLengths.size) break

        val length = segmentLengths[segment]
        val ratio = if (length <= 0.0) 0.0 else ((target - travelled) / length).coerceIn(0.0, 1.0)
        val from = points[segment]
        val to = points[segment + 1]
        arrows += TripArrow(
            // Interpolation linéaire : à l'échelle d'un segment de tracé, la courbure est nulle.
            latitude = from[0] + (to[0] - from[0]) * ratio,
            longitude = from[1] + (to[1] - from[1]) * ratio,
            bearingDegrees = bearingDegrees(from[0], from[1], to[0], to[1]),
            distanceAlongMeters = target
        )
        target += spacing
    }
    return arrows
}

/**
 * Nombre de points du rail d'animation. Assez dense pour que le défilement paraisse continu, assez
 * court pour rester projetable à chaque image.
 */
const val TRIP_FLOW_SAMPLES = 600

/**
 * Le tracé **entier** rééchantillonné à pas constant, du départ à l'arrivée : le rail sur lequel
 * les flèches défilent.
 *
 * C'est ce qui permet de faire glisser tout le fil d'un coup. Décaler l'indice de départ d'une
 * image à l'autre fait avancer toutes les flèches ensemble, et comme le pas est constant, la boucle
 * se referme sans saut : après un pas complet, chaque flèche occupe la place de la suivante.
 *
 * Les segments non calculés comptent aussi, avec leur trait direct : le sens de parcours est connu
 * même quand la route ne l'est pas.
 */
fun tripFlowTrack(plan: TripPlan, sampleCount: Int = TRIP_FLOW_SAMPLES): List<TripArrow> {
    if (sampleCount < 2) return emptyList()

    val points = ArrayList<DoubleArray>()
    plan.legPairs().forEach { (fromIndex, toIndex) ->
        val leg = plan.legBetween(fromIndex, toIndex)?.points()?.takeIf { it.size >= 2 }
            ?: listOf(
                doubleArrayOf(plan.steps[fromIndex].latitude, plan.steps[fromIndex].longitude),
                doubleArrayOf(plan.steps[toIndex].latitude, plan.steps[toIndex].longitude)
            )
        leg.forEach { point ->
            // La jonction entre deux segments est le même point des deux côtés : le garder deux
            // fois créerait un pas de longueur nulle, donc un cap indéfini.
            val last = points.lastOrNull()
            if (last == null || last[0] != point[0] || last[1] != point[1]) points += point
        }
    }
    if (points.size < 2) return emptyList()

    val segmentLengths = DoubleArray(points.size - 1)
    var total = 0.0
    for (index in 0 until points.size - 1) {
        val length = haversineMeters(
            points[index][0], points[index][1],
            points[index + 1][0], points[index + 1][1]
        )
        segmentLengths[index] = length
        total += length
    }
    if (total <= 0.0) return emptyList()

    val step = total / sampleCount
    val samples = ArrayList<TripArrow>(sampleCount)
    var travelled = 0.0
    var segment = 0
    for (index in 0 until sampleCount) {
        val target = index * step
        while (segment < segmentLengths.size && travelled + segmentLengths[segment] < target) {
            travelled += segmentLengths[segment]
            segment++
        }
        if (segment >= segmentLengths.size) break

        val length = segmentLengths[segment]
        val ratio = if (length <= 0.0) 0.0 else ((target - travelled) / length).coerceIn(0.0, 1.0)
        val from = points[segment]
        val to = points[segment + 1]
        samples += TripArrow(
            latitude = from[0] + (to[0] - from[0]) * ratio,
            longitude = from[1] + (to[1] - from[1]) * ratio,
            bearingDegrees = bearingDegrees(from[0], from[1], to[0], to[1]),
            distanceAlongMeters = target
        )
    }
    return samples
}

/** Cap initial de `from` vers `to`, en degrés depuis le nord. */
internal fun bearingDegrees(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val fromLatRad = Math.toRadians(fromLatitude)
    val toLatRad = Math.toRadians(toLatitude)
    val deltaLon = Math.toRadians(toLongitude - fromLongitude)
    val y = sin(deltaLon) * cos(toLatRad)
    val x = cos(fromLatRad) * sin(toLatRad) - sin(fromLatRad) * cos(toLatRad) * cos(deltaLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}
