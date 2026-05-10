package fr.geotower.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ElevationProfileApiPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val distanceMeters: Float
)

data class ElevationProfileApiResult(
    val points: List<ElevationProfileApiPoint>,
    val distanceMeters: Float
)

object ElevationProfileApi {
    private const val PROFILE_URL = "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevationLine.json"

    fun getProfile(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): ElevationProfileApiResult {
        val fallbackDistanceMeters = distanceMeters(fromLatitude, fromLongitude, toLatitude, toLongitude)
        val sampling = samplingForDistance(fallbackDistanceMeters)
        val url = PROFILE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("lon", "$fromLongitude|$toLongitude")
            .addQueryParameter("lat", "$fromLatitude|$toLatitude")
            .addQueryParameter("resource", "ign_rge_alti_wld")
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
            parseElevationProfile(body, fallbackDistanceMeters)
        }
    }
}

internal fun parseElevationProfile(json: String, fallbackDistanceMeters: Float): ElevationProfileApiResult {
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
    return ElevationProfileApiResult(points = points, distanceMeters = distance)
}

private fun samplingForDistance(distanceMeters: Float): Int = when {
    distanceMeters < 1_000f -> 90
    distanceMeters < 5_000f -> 140
    distanceMeters < 20_000f -> 220
    else -> 320
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
