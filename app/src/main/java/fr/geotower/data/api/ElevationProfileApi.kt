package fr.geotower.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.geotower.data.config.RemoteFeatureFlags
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

data class ElevationProfileApiPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val distanceMeters: Float
)

data class ElevationProfileApiResult(
    val points: List<ElevationProfileApiPoint>,
    val distanceMeters: Float,
    /** true si le profil s'appuie sur le MNS (sol + bâtiments + végétation), false si MNT (sol nu). */
    val obstaclesIncluded: Boolean = false
)

object ElevationProfileApi {
    private const val PROFILE_URL = "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevationLine.json"

    /** Endpoint « points multiples » (batch) : renvoie les z dans l'ordre d'entrée. GET uniquement. */
    private const val ELEVATIONS_URL = "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevation.json"

    /** MNT RGE ALTI : altitude du sol nu, couverture nationale complète. */
    private const val RESOURCE_TERRAIN = "ign_rge_alti_wld"

    fun getProfile(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
        includeObstacles: Boolean = false
    ): ElevationProfileApiResult {
        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_ELEVATION_PROFILE) ||
            !RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ELEVATION_IGN)
        ) {
            error("Elevation profile disabled")
        }
        val fallbackDistanceMeters = distanceMeters(fromLatitude, fromLongitude, toLatitude, toLongitude)
        // En mode obstacles, on échantillonne plus finement pour mieux « toucher » les bâtiments.
        val sampling = samplingForDistance(fallbackDistanceMeters, includeObstacles)

        val terrain = requestProfile(
            fromLatitude = fromLatitude,
            fromLongitude = fromLongitude,
            toLatitude = toLatitude,
            toLongitude = toLongitude,
            sampling = sampling,
            fallbackDistanceMeters = fallbackDistanceMeters
        )

        if (!includeObstacles) return terrain

        // Obstacles : on surélève les points qui tombent sur un bâtiment BD TOPO (altitude de toit).
        // Si le WFS échoue, on retombe proprement sur le profil terrain (obstaclesIncluded reste false).
        val buildings = runCatching {
            BdTopoBuildingsApi.fetchBuildingsForSegment(
                fromLatitude = fromLatitude,
                fromLongitude = fromLongitude,
                toLatitude = toLatitude,
                toLongitude = toLongitude
            )
        }.getOrNull() ?: return terrain

        val overlaidPoints = terrain.points.map { point ->
            val rooftop = buildings
                .asSequence()
                .filter { it.contains(point.longitude, point.latitude) }
                .map { it.topAltitude(point.elevation) }
                .maxOrNull()
            if (rooftop != null && rooftop > point.elevation) {
                point.copy(elevation = rooftop)
            } else {
                point
            }
        }

        return terrain.copy(points = overlaidPoints, obstaclesIncluded = true)
    }

    private fun requestProfile(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
        sampling: Int,
        fallbackDistanceMeters: Float
    ): ElevationProfileApiResult {
        val url = PROFILE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("lon", "$fromLongitude|$toLongitude")
            .addQueryParameter("lat", "$fromLatitude|$toLatitude")
            .addQueryParameter("resource", RESOURCE_TERRAIN)
            .addQueryParameter("delimiter", "|")
            .addQueryParameter("indent", "false")
            .addQueryParameter("measures", "false")
            .addQueryParameter("profile_mode", "simple")
            .addQueryParameter("sampling", sampling.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return RetrofitClient.currentClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty response")
            parseElevationProfile(body, fallbackDistanceMeters, obstaclesIncluded = false)
        }
    }

    /**
     * Altitudes terrain (MNT RGE ALTI) pour une liste de points `[lon, lat]`, via UNE requête GET
     * batch à `elevation.json`. Renvoie les z dans le **même ordre** que [points] (NaN si invalide ou
     * hors couverture). GET uniquement : l'endpoint refuse le POST (HTTP 500). Garder [points] sous
     * ~300 (URL ~7 Ko ; 600 points ⇒ HTTP 414 URI Too Long) — le découpage en lots est à la charge
     * de l'appelant (cf. TerrainFieldLoader).
     */
    fun getElevations(points: List<DoubleArray>): List<Double> {
        if (points.isEmpty()) return emptyList()
        if (!RemoteFeatureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ELEVATION_IGN)) {
            error("Elevation provider disabled")
        }
        val lon = points.joinToString("|") { formatCoordinate(it[0]) }
        val lat = points.joinToString("|") { formatCoordinate(it[1]) }
        val url = ELEVATIONS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("lon", lon)
            .addQueryParameter("lat", lat)
            .addQueryParameter("resource", RESOURCE_TERRAIN)
            .addQueryParameter("delimiter", "|")
            .addQueryParameter("indent", "false")
            .addQueryParameter("measures", "false")
            .addQueryParameter("zonly", "false")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return RetrofitClient.currentClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty response")
            parseElevationsZ(body, points.size)
        }
    }
}

internal fun parseElevationProfile(
    json: String,
    fallbackDistanceMeters: Float,
    obstaclesIncluded: Boolean = false
): ElevationProfileApiResult {
    val root = JsonParser.parseString(json).asJsonObjectOrNull() ?: error("No profile data")
    val elevations = root.get("elevations")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("No profile data")
    val points = mutableListOf<ElevationProfileApiPoint>()
    var cumulativeDistance = 0f
    var previousLat: Double? = null
    var previousLon: Double? = null

    for (index in 0 until elevations.size()) {
        val item = elevations[index].asJsonObjectOrNull() ?: continue
        val latitude = item.getRequiredDouble("lat")
        val longitude = item.getRequiredDouble("lon")
        val elevation = item.getRequiredDouble("z")
        if (elevation <= -99990.0) continue

        if (previousLat != null && previousLon != null) {
            cumulativeDistance += distanceMeters(previousLat, previousLon, latitude, longitude)
        }

        points += ElevationProfileApiPoint(
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            distanceMeters = cumulativeDistance
        )
        previousLat = latitude
        previousLon = longitude
    }

    if (points.size < 2) error("No profile data")
    val distance = if (cumulativeDistance > 0f) cumulativeDistance else fallbackDistanceMeters
    return ElevationProfileApiResult(
        points = points,
        distanceMeters = distance,
        obstaclesIncluded = obstaclesIncluded
    )
}

/**
 * Lit la réponse batch `{"elevations":[{lon,lat,z}, …]}` en **préservant l'ordre d'entrée**.
 * Renvoie exactement [expectedSize] valeurs ; z invalide (≤ -99990, hors couverture) ⇒ NaN.
 * (On NE réutilise PAS [parseElevationProfile] qui filtre les points invalides et casserait l'alignement par index.)
 */
internal fun parseElevationsZ(json: String, expectedSize: Int): List<Double> {
    val root = JsonParser.parseString(json).asJsonObjectOrNull() ?: error("No elevation data")
    val elevations = root.get("elevations")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("No elevation data")
    val result = ArrayList<Double>(expectedSize)
    for (index in 0 until elevations.size()) {
        val item = elevations[index].asJsonObjectOrNull()
        val z = item?.let { runCatching { it.getRequiredDouble("z") }.getOrNull() }
        result += if (z == null || z <= -99990.0) Double.NaN else z
    }
    // Aligne strictement sur l'entrée même si la réponse est tronquée / surnuméraire.
    while (result.size < expectedSize) result += Double.NaN
    return if (result.size > expectedSize) result.subList(0, expectedSize).toList() else result
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun samplingForDistance(distanceMeters: Float, includeObstacles: Boolean = false): Int {
    val base = when {
        distanceMeters < 1_000f -> 90
        distanceMeters < 5_000f -> 140
        distanceMeters < 20_000f -> 220
        else -> 320
    }
    if (!includeObstacles) return base
    // ~8 m entre points pour mieux détecter les bâtiments, plafonné pour rester raisonnable.
    val fine = (distanceMeters / 8f).toInt()
    return fine.coerceIn(base, 2_000)
}

private fun distanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Float {
    val earthRadiusMeters = 6_371_000.0
    val fromLatRad = Math.toRadians(fromLatitude)
    val toLatRad = Math.toRadians(toLatitude)
    val deltaLat = Math.toRadians(toLatitude - fromLatitude)
    val deltaLon = Math.toRadians(toLongitude - fromLongitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(fromLatRad) * cos(toLatRad) * sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadiusMeters * c).toFloat()
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
    return this?.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.getRequiredDouble(name: String): Double {
    val value = get(name) ?: error("Missing $name")
    return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString.toDouble() else value.asDouble
}
