package fr.geotower.data.trip

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

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

/**
 * Distance sur laquelle est lu le cap d'un tracé quand le GPS n'en donne pas. Assez longue pour
 * ignorer les zigzags du premier segment, assez courte pour ne pas moyenner un virage à venir.
 */
const val NAV_HEADING_LOOKAHEAD_METERS = 120.0

/**
 * Le cap du tracé sur ses premiers mètres, ou `null` s'il n'y a rien à suivre.
 *
 * C'est le cap de repli quand l'utilisateur est **à l'arrêt** : le GPS ne rend alors aucun cap de
 * déplacement, et laisser la carte au nord mettrait le trajet à suivre en travers de l'écran. En
 * lisant la direction du tracé lui-même, la carte s'oriente comme si l'on marchait déjà dessus.
 *
 * On mesure une **corde**, du départ au point situé [aheadMeters] plus loin, et non l'inclinaison du
 * tout premier segment : une sortie de parking de dix mètres ne doit pas décider de l'orientation.
 */
fun routeHeadingAhead(
    points: List<DoubleArray>,
    aheadMeters: Double = NAV_HEADING_LOOKAHEAD_METERS
): Double? {
    if (points.size < 2) return null

    val origin = points.first()
    var travelled = 0.0
    var target = points[1]
    for (index in 0 until points.size - 1) {
        travelled += haversineMeters(
            points[index][0], points[index][1],
            points[index + 1][0], points[index + 1][1]
        )
        target = points[index + 1]
        if (travelled >= aheadMeters) break
    }

    // Tracé dégénéré (tous les points confondus) : aucun cap à en tirer.
    if (haversineMeters(origin[0], origin[1], target[0], target[1]) <= 0.0) return null
    return bearingDegrees(origin[0], origin[1], target[0], target[1])
}

/**
 * Étendue minimale du cadrage d'ouverture, en degrés (~200 m).
 *
 * Un trajet d'une seule étape, ou dont les étapes sont voisines, donnerait un rectangle plat : le
 * cadrage partirait alors à un zoom absurde. On lui garantit une taille plancher.
 */
private const val TRIP_BOUNDS_MIN_SPAN_DEGREES = 0.002

/**
 * Le rectangle qui contient toute la tournée — ses étapes **et** ses segments calculés — sous la
 * forme `[nord, est, sud, ouest]`, ou `null` si elle n'a aucun point.
 *
 * Les segments comptent autant que les étapes : une route qui contourne un massif sort largement
 * du rectangle de ses seuls points d'arrivée, et un cadrage qui l'ignorerait couperait le tracé.
 */
fun tripBoundingBox(plan: TripPlan): DoubleArray? {
    var north = -90.0
    var south = 90.0
    var east = -180.0
    var west = 180.0
    var seen = false

    fun add(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        seen = true
        if (latitude > north) north = latitude
        if (latitude < south) south = latitude
        if (longitude > east) east = longitude
        if (longitude < west) west = longitude
    }

    plan.steps.forEach { add(it.latitude, it.longitude) }
    plan.legPairs().forEach { (fromIndex, toIndex) ->
        plan.legBetween(fromIndex, toIndex)?.points()?.forEach { add(it[0], it[1]) }
    }
    if (!seen) return null

    // Rectangle plat (étape unique, ou étapes très proches) : on l'élargit au plancher.
    if (north - south < TRIP_BOUNDS_MIN_SPAN_DEGREES) {
        val center = (north + south) / 2.0
        north = center + TRIP_BOUNDS_MIN_SPAN_DEGREES / 2.0
        south = center - TRIP_BOUNDS_MIN_SPAN_DEGREES / 2.0
    }
    if (east - west < TRIP_BOUNDS_MIN_SPAN_DEGREES) {
        val center = (east + west) / 2.0
        east = center + TRIP_BOUNDS_MIN_SPAN_DEGREES / 2.0
        west = center - TRIP_BOUNDS_MIN_SPAN_DEGREES / 2.0
    }
    return doubleArrayOf(north, east, south, west)
}

/** Plafond du cadrage d'ouverture : sur une tournée minuscule, inutile de coller au trottoir. */
const val TRIP_FRAME_MAX_ZOOM = 17.0

/** Plancher : au-delà on ne montre plus une tournée, on montre un continent. */
const val TRIP_FRAME_MIN_ZOOM = 3.0

/**
 * Le zoom qui fait tenir toute la tournée dans la vue, **au plus près**.
 *
 * Calculé plutôt que confié à `zoomToBoundingBox` : celui d'osmdroid arrondit largement et recule
 * bien plus que nécessaire. Ici on prend exactement le zoom qui fait entrer le rectangle, marges
 * comprises, borné aux deux extrêmes.
 *
 * @param box `[nord, est, sud, ouest]`, tel que le rend [tripBoundingBox].
 */
fun tripFrameZoom(
    box: DoubleArray,
    viewportWidthPixels: Int,
    viewportHeightPixels: Int,
    borderPixels: Int,
    maxZoom: Double = TRIP_FRAME_MAX_ZOOM
): Double {
    val usableWidth = (viewportWidthPixels - 2 * borderPixels).coerceAtLeast(1)
    val usableHeight = (viewportHeightPixels - 2 * borderPixels).coerceAtLeast(1)

    val longitudeSpan = (box[1] - box[3]).coerceAtLeast(1e-9)
    // Projection de Mercator : un degré de latitude n'occupe pas la même hauteur selon l'endroit.
    val mercatorSpan = (mercatorY(box[0]) - mercatorY(box[2])).coerceAtLeast(1e-12)

    val zoomForWidth = log2(usableWidth * 360.0 / (TILE_SIZE_PIXELS * longitudeSpan))
    val zoomForHeight = log2(usableHeight * 2.0 * Math.PI / (TILE_SIZE_PIXELS * mercatorSpan))

    // Le plus contraignant des deux : c'est lui qui décide si la tournée tient.
    return minOf(zoomForWidth, zoomForHeight).coerceIn(TRIP_FRAME_MIN_ZOOM, maxZoom)
}

/**
 * Recul maximal du survol, en crans de zoom. Au-delà, on ne montre plus le chemin parcouru : on
 * traverse un continent pour deux kilomètres.
 */
const val MAP_SWOOP_MAX_ZOOM_OUT = 4.0

/**
 * Recul minimal, même quand tout tient déjà à l'écran. Sans lui, un recentrage sur place n'aurait
 * aucun mouvement, et l'appui sur le bouton passerait pour sans effet.
 */
const val MAP_SWOOP_EXTRA_ZOOM_OUT = 0.8

/**
 * Le zoom auquel la carte recule avant de replonger sur sa cible.
 *
 * C'est le survol des applis de cartographie : on prend du champ, on glisse jusqu'au point, on
 * redescend. Le recul dit d'où l'on vient — un saut brutal, lui, laisse l'utilisateur chercher où
 * la carte l'a emmené.
 *
 * Le calcul retient le plus contraignant de trois candidats — le zoom courant, celui d'arrivée, et
 * celui qui ferait tenir départ **et** arrivée à l'écran — puis recule encore un peu. Reculer
 * au-delà du zoom courant n'aurait pas de sens quand on part déjà d'une vue large.
 *
 * @param currentZoom zoom d'où part la carte.
 * @param targetZoom zoom sur lequel elle doit finir.
 */
fun mapSwoopZoom(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
    currentZoom: Double,
    targetZoom: Double,
    viewportWidthPixels: Int,
    viewportHeightPixels: Int,
    borderPixels: Int,
    maxZoomOut: Double = MAP_SWOOP_MAX_ZOOM_OUT,
    extraZoomOut: Double = MAP_SWOOP_EXTRA_ZOOM_OUT
): Double {
    if (!currentZoom.isFinite() || !targetZoom.isFinite()) return targetZoom
    if (!fromLatitude.isFinite() || !fromLongitude.isFinite()) return targetZoom

    val fitZoom = tripFrameZoom(
        box = doubleArrayOf(
            maxOf(fromLatitude, toLatitude),
            maxOf(fromLongitude, toLongitude),
            minOf(fromLatitude, toLatitude),
            minOf(fromLongitude, toLongitude)
        ),
        viewportWidthPixels = viewportWidthPixels,
        viewportHeightPixels = viewportHeightPixels,
        borderPixels = borderPixels,
        maxZoom = maxOf(currentZoom, targetZoom)
    )
    // Le plancher se mesure sur le plus serré des deux bouts, jamais sur le seul zoom d'arrivée :
    // un départ en vue large reculerait sinon moins qu'un départ au ras du sol.
    val floor = minOf(currentZoom, targetZoom) - maxZoomOut
    return (minOf(currentZoom, targetZoom, fitZoom) - extraZoomOut).coerceAtLeast(floor)
}

private const val TILE_SIZE_PIXELS = 256.0

private fun mercatorY(latitudeDegrees: Double): Double {
    val clamped = latitudeDegrees.coerceIn(-85.05, 85.05)
    return ln(tan(Math.PI / 4.0 + Math.toRadians(clamped) / 2.0))
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
