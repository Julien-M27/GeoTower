package fr.geotower.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.geotower.data.config.RemoteFeatureFlags
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Un itinéraire calculé le long du réseau, et sa longueur réelle (et non à vol d'oiseau). */
data class RoutePathResult(
    /** Géométrie du tracé, du départ vers l'arrivée, en `[latitude, longitude]`. */
    val points: List<DoubleArray>,
    val distanceMeters: Double
)

/**
 * L'itinéraire entre deux points **demandés** consécutifs, quand la requête porte des étapes
 * intermédiaires : le service rend une portion par segment.
 */
data class RoutePortionResult(
    /** Géométrie de cette portion seule, en `[latitude, longitude]`. */
    val points: List<DoubleArray>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuvers: List<RouteManeuver>
)

/**
 * Une manœuvre du guidage, telle que le service la rend : des **codes anglais** façon OSRM, et un
 * nom de voie brut, abrégé et en majuscules façon BD TOPO (`QU DE L'HORLOGE`). La mise en phrase
 * appartient à l'app — reprendre le libellé du serveur le figerait en une seule langue.
 */
data class RouteManeuver(
    /** `depart`, `turn`, `new name`, `end of road`, `roundabout`, `fork`, `merge`, `arrive`… */
    val type: String,
    /** `left`, `right`, `slight left`, `sharp right`, `straight`, `uturn`… */
    val modifier: String?,
    val roadName: String?,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Calcul d'itinéraire « qui suit les routes » via le service navigation de la Géoplateforme IGN
 * (moteur OSRM sur BD TOPO) : même famille de services que [ElevationProfileApi], simple GET sans
 * clé. Couverture France (métropole + DROM) — ailleurs le service échoue, et l'appelant retombe
 * alors sur le trait direct.
 */
object RouteApi {
    private const val ROUTE_URL = "https://data.geopf.fr/navigation/itineraire"
    private const val RESOURCE = "bdtopo-osrm"

    /** Réseau routier : sens uniques respectés, chemins et sentiers exclus. */
    const val PROFILE_CAR = "car"

    /** Réseau piéton : ajoute les chemins, sentiers et zones piétonnes au réseau routier. */
    const val PROFILE_PEDESTRIAN = "pedestrian"

    /**
     * Au-delà, on ne sollicite pas le service : deux points aussi éloignés ne peuvent pas partager
     * un réseau routier (autre continent, métropole ↔ outre-mer), la réponse serait forcément un
     * rabattement absurde. Le plafond couvre largement la France : la plus longue diagonale
     * métropolitaine (Ouessant ↔ Menton) fait ~1 100 km à vol d'oiseau. Le service encaisse sans
     * peine ces longueurs — un Brest → Nice revient en moins d'une seconde.
     */
    const val MAX_ROUTABLE_DISTANCE_METERS = 1_500_000.0

    /**
     * Distance tolérée entre un point demandé et le point du réseau sur lequel le service le rabat.
     * Généreuse : un point posé en pleine forêt ou au milieu d'un lac est légitimement loin de la
     * première route. Cf. [isRouteAnchoredOnRequest].
     */
    private const val SNAP_TOLERANCE_METERS = 2_000.0

    fun getRoute(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
        profile: String
    ): RoutePathResult {
        if (!RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ROUTING_IGN)) {
            error("Routing provider disabled")
        }
        val url = ROUTE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("resource", RESOURCE)
            .addQueryParameter("start", "${formatCoordinate(fromLongitude)},${formatCoordinate(fromLatitude)}")
            .addQueryParameter("end", "${formatCoordinate(toLongitude)},${formatCoordinate(toLatitude)}")
            .addQueryParameter("profile", profile)
            .addQueryParameter("optimization", "fastest")
            .addQueryParameter("geometryFormat", "geojson")
            .addQueryParameter("getSteps", "false")
            .addQueryParameter("getBbox", "false")
            .addQueryParameter("distanceUnit", "meter")
            .addQueryParameter("timeUnit", "second")
            .addQueryParameter("crs", "EPSG:4326")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return RetrofitClient.currentClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty response")
            val route = parseRoutePath(body)
            // Hors couverture BD TOPO, le service ne renvoie PAS d'erreur : il rabat les deux points
            // sur le réseau le plus proche, quitte à répondre un tracé à des centaines de kilomètres
            // (un point à Berlin ressort près de la frontière alsacienne). On le refuse ici.
            if (!isRouteAnchoredOnRequest(route, fromLatitude, fromLongitude, toLatitude, toLongitude)) {
                error("Route snapped outside the requested area")
            }
            route
        }
    }

    /**
     * Vrai si les extrémités du tracé collent aux points demandés, à la tolérance de rabattement
     * près ([SNAP_TOLERANCE_METERS], élargie à 5 % sur les longs trajets).
     */
    internal fun isRouteAnchoredOnRequest(
        route: RoutePathResult,
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Boolean = isPathAnchoredOnRequest(route.points, fromLatitude, fromLongitude, toLatitude, toLongitude)

    /** Plafond du paramètre `intermediates`, imposé par le service : à 16, il répond `400 … max is 15`. */
    const val MAX_INTERMEDIATES = 15

    /** Départ + intermédiaires + arrivée dans une seule requête. */
    const val MAX_POINTS_PER_ROUTE_REQUEST = MAX_INTERMEDIATES + 2

    /**
     * Itinéraire passant par des étapes intermédiaires : le service rend **une portion par
     * segment**, avec sa distance, sa durée et ses manœuvres.
     *
     * `getSteps=true` n'est pas un confort mais une nécessité ici. Sans lui, une portion ne porte
     * **aucune géométrie** — ses clés se réduisent à `start, end, distance, duration, bbox, steps`,
     * et `steps` est vide ; seule la racine rend le tracé, d'un bloc et sans indice de découpe. La
     * géométrie d'une portion se reconstitue donc en recollant celles de ses steps. Contrepartie
     * mesurée sur 3 km : 18,7 Ko de réponse contre 5,8 Ko, d'où le `getSteps=false` conservé dans
     * [getRoute], qui n'a besoin que d'une longueur.
     *
     * @param points étapes en `[latitude, longitude]`, départ et arrivée compris.
     */
    fun getRoutePortions(points: List<DoubleArray>, profile: String): List<RoutePortionResult> {
        if (!RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ROUTING_IGN)) {
            error("Routing provider disabled")
        }
        if (points.size < 2) error("A route needs at least two points")
        if (points.size > MAX_POINTS_PER_ROUTE_REQUEST) {
            error("Too many points: ${points.size} > $MAX_POINTS_PER_ROUTE_REQUEST")
        }

        val builder = ROUTE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("resource", RESOURCE)
            .addQueryParameter("start", formatPoint(points.first()))
            .addQueryParameter("end", formatPoint(points.last()))
            .addQueryParameter("profile", profile)
            .addQueryParameter("optimization", "fastest")
            .addQueryParameter("geometryFormat", "geojson")
            .addQueryParameter("getSteps", "true")
            .addQueryParameter("getBbox", "false")
            .addQueryParameter("distanceUnit", "meter")
            .addQueryParameter("timeUnit", "second")
            .addQueryParameter("crs", "EPSG:4326")
        if (points.size > 2) {
            builder.addQueryParameter(
                "intermediates",
                points.subList(1, points.lastIndex).joinToString("|") { formatPoint(it) }
            )
        }

        val request = Request.Builder()
            .url(builder.build())
            .get()
            .build()

        return RetrofitClient.currentClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty response")
            val portions = parseRoutePortions(body)
            if (portions.size != points.size - 1) {
                error("Expected ${points.size - 1} portions, got ${portions.size}")
            }
            // Même piège que dans getRoute, mais à vérifier portion par portion : une seule étape
            // hors couverture suffit à faire répondre un tracé absurde, et les extrémités du trajet
            // complet, elles, resteraient parfaitement ancrées.
            portions.forEachIndexed { index, portion ->
                val from = points[index]
                val to = points[index + 1]
                if (!isPathAnchoredOnRequest(portion.points, from[0], from[1], to[0], to[1])) {
                    error("Portion $index snapped outside the requested area")
                }
            }
            portions
        }
    }

    /** `longitude,latitude` — l'ordre attendu par le service, l'inverse de celui des géométries. */
    private fun formatPoint(point: DoubleArray): String =
        "${formatCoordinate(point[1])},${formatCoordinate(point[0])}"

    internal fun isPathAnchoredOnRequest(
        pathPoints: List<DoubleArray>,
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Boolean {
        val first = pathPoints.firstOrNull() ?: return false
        val last = pathPoints.lastOrNull() ?: return false
        val requested = routeDistanceMeters(fromLatitude, fromLongitude, toLatitude, toLongitude)
        val tolerance = maxOf(SNAP_TOLERANCE_METERS, requested * 0.05)
        val startGap = routeDistanceMeters(fromLatitude, fromLongitude, first[0], first[1])
        val endGap = routeDistanceMeters(toLatitude, toLongitude, last[0], last[1])
        return startGap <= tolerance && endGap <= tolerance
    }
}

/**
 * Lit la réponse du service itinéraire : `geometry` en GeoJSON `LineString` (coordonnées en
 * `[lon, lat]`) et `distance` en mètres. La distance est recalculée depuis la géométrie si le
 * service ne la renvoie pas.
 */
internal fun parseRoutePath(json: String): RoutePathResult {
    val root = JsonParser.parseString(json).asRouteObjectOrNull() ?: error("No route data")
    val geometry = root.get("geometry").asRouteObjectOrNull() ?: error("No route geometry")
    val coordinates = geometry.get("coordinates")?.takeIf { it.isJsonArray }?.asJsonArray
        ?: error("No route geometry")

    val points = ArrayList<DoubleArray>(coordinates.size())
    for (index in 0 until coordinates.size()) {
        val pair = coordinates[index].takeIf { it.isJsonArray }?.asJsonArray ?: continue
        if (pair.size() < 2) continue
        val longitude = runCatching { pair[0].asDouble }.getOrNull() ?: continue
        val latitude = runCatching { pair[1].asDouble }.getOrNull() ?: continue
        points += doubleArrayOf(latitude, longitude)
    }
    if (points.size < 2) error("No route geometry")

    val announced = root.get("distance").asRouteDoubleOrNull()
    val distance = if (announced != null && announced > 0.0) announced else routeLengthMeters(points)
    return RoutePathResult(points = points, distanceMeters = distance)
}

/**
 * Lit la réponse d'une requête à étapes intermédiaires : une entrée par `portions[]`. La géométrie
 * d'une portion n'existe pas telle quelle et se recolle depuis ses `steps[].geometry` — voir
 * [RouteApi.getRoutePortions].
 */
internal fun parseRoutePortions(json: String): List<RoutePortionResult> {
    val root = JsonParser.parseString(json).asRouteObjectOrNull() ?: error("No route data")
    val portions = root.get("portions")?.takeIf { it.isJsonArray }?.asJsonArray
        ?: error("No route portions")

    val results = ArrayList<RoutePortionResult>(portions.size())
    for (index in 0 until portions.size()) {
        val portion = portions[index].asRouteObjectOrNull() ?: error("Malformed portion $index")
        val steps = portion.get("steps")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: error("Portion $index has no steps")

        val points = ArrayList<DoubleArray>()
        val maneuvers = ArrayList<RouteManeuver>()
        for (stepIndex in 0 until steps.size()) {
            val step = steps[stepIndex].asRouteObjectOrNull() ?: continue
            appendStepGeometry(step, points)
            parseManeuver(step)?.let { maneuvers += it }
        }
        if (points.size < 2) error("Portion $index has no geometry")

        val announced = portion.get("distance").asRouteDoubleOrNull()
        results += RoutePortionResult(
            points = points,
            distanceMeters = if (announced != null && announced > 0.0) announced else routeLengthMeters(points),
            durationSeconds = portion.get("duration").asRouteDoubleOrNull() ?: 0.0,
            maneuvers = maneuvers
        )
    }
    return results
}

/**
 * Ajoute la géométrie d'un step à la suite de la portion. Le premier point d'un step répète le
 * dernier du précédent : on l'écarte, sinon le tracé porterait un point en double à chaque manœuvre.
 */
private fun appendStepGeometry(step: JsonObject, into: MutableList<DoubleArray>) {
    val coordinates = step.get("geometry").asRouteObjectOrNull()
        ?.get("coordinates")?.takeIf { it.isJsonArray }?.asJsonArray
        ?: return
    for (index in 0 until coordinates.size()) {
        val pair = coordinates[index].takeIf { it.isJsonArray }?.asJsonArray ?: continue
        if (pair.size() < 2) continue
        val longitude = runCatching { pair[0].asDouble }.getOrNull() ?: continue
        val latitude = runCatching { pair[1].asDouble }.getOrNull() ?: continue
        val previous = into.lastOrNull()
        if (previous != null && previous[0] == latitude && previous[1] == longitude) continue
        into += doubleArrayOf(latitude, longitude)
    }
}

private fun parseManeuver(step: JsonObject): RouteManeuver? {
    val instruction = step.get("instruction").asRouteObjectOrNull() ?: return null
    val type = instruction.get("type").asRouteStringOrNull() ?: return null
    return RouteManeuver(
        type = type,
        modifier = instruction.get("modifier").asRouteStringOrNull(),
        // `nom_1_gauche` et `nom_1_droite` portent le même nom hors voies séparées ; la gauche
        // suffit, et une chaîne vide (voie sans nom) doit ressortir absente, pas vide.
        roadName = step.get("attributes").asRouteObjectOrNull()
            ?.get("name").asRouteObjectOrNull()
            ?.get("nom_1_gauche").asRouteStringOrNull(),
        distanceMeters = step.get("distance").asRouteDoubleOrNull() ?: 0.0,
        durationSeconds = step.get("duration").asRouteDoubleOrNull() ?: 0.0
    )
}

private fun routeLengthMeters(points: List<DoubleArray>): Double {
    var total = 0.0
    for (index in 1 until points.size) {
        total += routeDistanceMeters(
            points[index - 1][0],
            points[index - 1][1],
            points[index][0],
            points[index][1]
        )
    }
    return total
}

private fun routeDistanceMeters(
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

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun JsonElement?.asRouteObjectOrNull(): JsonObject? =
    this?.takeIf { it.isJsonObject }?.asJsonObject

/** Chaîne non vide, ou `null` : une voie sans nom revient en `""` et ne doit pas rester telle quelle. */
private fun JsonElement?.asRouteStringOrNull(): String? =
    this?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

/** Le service renvoie parfois les nombres sous forme de chaîne ("1234.5"). */
private fun JsonElement?.asRouteDoubleOrNull(): Double? {
    val element = this?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
    return if (element.isString) element.asString.toDoubleOrNull() else runCatching { element.asDouble }.getOrNull()
}
