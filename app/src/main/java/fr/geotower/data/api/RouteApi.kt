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
     * Au-delà, on ne sollicite pas le service : un trait de plusieurs centaines de kilomètres sort
     * de l'usage de l'outil de mesure et coûterait une longue attente pour un tracé illisible.
     */
    const val MAX_ROUTABLE_DISTANCE_METERS = 400_000.0

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
    ): Boolean {
        val first = route.points.firstOrNull() ?: return false
        val last = route.points.lastOrNull() ?: return false
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

/** Le service renvoie parfois les nombres sous forme de chaîne ("1234.5"). */
private fun JsonElement?.asRouteDoubleOrNull(): Double? {
    val element = this?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
    return if (element.isString) element.asString.toDoubleOrNull() else runCatching { element.asDouble }.getOrNull()
}
