package fr.geotower.data.trip

/**
 * Rayon dans lequel on cherche un support sous une étape.
 *
 * Les coordonnées d'une étape font foi, mais elles ne tombent pas forcément sur le pylône : une
 * étape posée sur un marqueur d'antenne est juste, une étape posée à la main ou par recherche
 * d'adresse peut être à quelques dizaines de mètres. Cent cinquante mètres couvrent l'erreur de
 * pointage sans ramener le pylône de la rue d'à côté.
 */
const val TRIP_STEP_SITE_RADIUS_METERS = 150.0

/**
 * Ce qu'il faut savoir d'une station pour la rattacher à son support et la classer.
 *
 * Volontairement réduit : le regroupement ci-dessous n'a besoin ni des fréquences ni des azimuts, et
 * une forme minimale se construit à la main dans un test, ce qu'une entité de base ne permet pas.
 */
data class TripStepAntennaRef(
    val idAnfr: String,
    /** Support portant la station, ou son propre identifiant à défaut de support connu. */
    val supportId: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Un support trouvé sous une étape, avec la station la plus proche pour le représenter.
 *
 * C'est l'unité qui compte pour un envoi de photos : on photographie **un pylône**, pas une station.
 * Un support mutualisé porte plusieurs opérateurs, qui partiront donc ensemble.
 */
data class TripStepSupport(
    val supportId: String,
    /** Position de la station la plus proche de l'étape : celle que l'envoi transporte. */
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    /** Toutes les stations du support retrouvées autour de l'étape, la plus proche en tête. */
    val idAnfrs: List<String>
)

/**
 * Les supports présents autour d'une étape, du plus proche au plus lointain.
 *
 * Un support est retenu dès qu'**une** de ses stations tombe dans le rayon, et il est classé sur
 * cette station-là : sur un site étendu, la station la plus proche est celle devant laquelle on se
 * trouve.
 */
fun groupTripStepSupports(
    antennas: List<TripStepAntennaRef>,
    stepLatitude: Double,
    stepLongitude: Double,
    radiusMeters: Double = TRIP_STEP_SITE_RADIUS_METERS
): List<TripStepSupport> {
    if (antennas.isEmpty() || radiusMeters <= 0.0) return emptyList()

    val bySupport = linkedMapOf<String, MutableList<Pair<TripStepAntennaRef, Double>>>()
    antennas.forEach { antenna ->
        val supportId = antenna.supportId.trim().ifBlank { antenna.idAnfr.trim() }
        if (supportId.isBlank()) return@forEach
        if (!antenna.latitude.isFinite() || !antenna.longitude.isFinite()) return@forEach

        val distance = haversineMeters(
            stepLatitude, stepLongitude, antenna.latitude, antenna.longitude
        )
        if (distance > radiusMeters) return@forEach
        bySupport.getOrPut(supportId) { mutableListOf() } += antenna to distance
    }

    return bySupport.map { (supportId, entries) ->
        val sorted = entries.sortedBy { it.second }
        val (closest, distance) = sorted.first()
        TripStepSupport(
            supportId = supportId,
            latitude = closest.latitude,
            longitude = closest.longitude,
            distanceMeters = distance,
            // `distinct` : une même station peut revenir si la base la rend deux fois.
            idAnfrs = sorted.map { it.first.idAnfr }.distinct()
        )
    }.sortedBy { it.distanceMeters }
}

/**
 * Le rectangle `[nord, est, sud, ouest]` à lire en base pour couvrir ce rayon.
 *
 * Calculé par déplacement géodésique et non par une conversion en degrés constante : un degré de
 * longitude ne fait pas la même largeur à Dunkerque et à Cayenne, et l'application couvre les deux.
 */
fun tripStepSearchBox(
    latitude: Double,
    longitude: Double,
    radiusMeters: Double = TRIP_STEP_SITE_RADIUS_METERS
): DoubleArray {
    val north = destinationPoint(latitude, longitude, 0.0, radiusMeters)[0]
    val south = destinationPoint(latitude, longitude, 180.0, radiusMeters)[0]
    val east = destinationPoint(latitude, longitude, 90.0, radiusMeters)[1]
    val west = destinationPoint(latitude, longitude, 270.0, radiusMeters)[1]
    return doubleArrayOf(north, east, south, west)
}
