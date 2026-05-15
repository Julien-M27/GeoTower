package fr.geotower.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.geotower.BuildConfig
import fr.geotower.utils.AppLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

data class NominatimArea(
    val latNorth: Double,
    val lonEast: Double,
    val latSouth: Double,
    val lonWest: Double,
    val geoJsonFeature: String?,
    val polygons: List<List<NominatimGeoPoint>>
)

data class NominatimGeoPoint(
    val latitude: Double,
    val longitude: Double
)

object NominatimApi {
    private const val TAG = "GeoTowerNominatim"
    private const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
    private const val FRENCH_TERRITORY_COUNTRY_CODES = "fr,gp,mq,gf,re,yt,pm,nc,pf,wf,bl,mf"
    private val userAgent = "GeoTower/${BuildConfig.VERSION_NAME} (Android)"

    fun searchArea(query: String): NominatimArea? {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return null

        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmedQuery)
            .addQueryParameter("format", "json")
            .addQueryParameter("polygon_geojson", "1")
            .addQueryParameter("countrycodes", FRENCH_TERRITORY_COUNTRY_CODES)
            .addQueryParameter("limit", "10")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        return try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Nominatim request failed code=${response.code}")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                parseNominatimArea(body)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Nominatim request failed", e)
            null
        }
    }
}

internal fun parseNominatimArea(json: String): NominatimArea? {
    return runCatching {
        val root = JsonParser.parseString(json).asJsonArrayOrNull() ?: return@runCatching null
        if (root.size() == 0) return@runCatching null

        val areas = (0 until root.size())
            .mapNotNull { index -> root[index].asJsonObjectOrNull()?.toNominatimAreaOrNull() }

        areas.firstOrNull { it.polygons.isNotEmpty() } ?: areas.firstOrNull()
    }.getOrNull()
}

private fun JsonObject.toNominatimAreaOrNull(): NominatimArea? {
    val boundingBox = get("boundingbox").asJsonArrayOrNull() ?: return null
    if (boundingBox.size() < 4) return null

    val geoJson = get("geojson").asJsonObjectOrNull()
    return NominatimArea(
        latSouth = boundingBox[0].asDoubleValue(),
        latNorth = boundingBox[1].asDoubleValue(),
        lonWest = boundingBox[2].asDoubleValue(),
        lonEast = boundingBox[3].asDoubleValue(),
        geoJsonFeature = geoJson?.let { geometry ->
            """{"type":"Feature","properties":{},"geometry":$geometry}"""
        },
        polygons = geoJson?.let(::extractNominatimGeoJsonPolygons).orEmpty()
    )
}

private fun extractNominatimGeoJsonPolygons(geoJson: JsonObject): List<List<NominatimGeoPoint>> {
    return when (geoJson.get("type")?.asString) {
        "Polygon" -> extractNominatimPolygonRings(geoJson.get("coordinates").asJsonArrayOrNull())
        "MultiPolygon" -> {
            val polygons = mutableListOf<List<NominatimGeoPoint>>()
            val coordinates = geoJson.get("coordinates").asJsonArrayOrNull() ?: return emptyList()
            for (index in 0 until coordinates.size()) {
                polygons += extractNominatimPolygonRings(coordinates[index].asJsonArrayOrNull())
            }
            polygons
        }
        else -> emptyList()
    }
}

private fun extractNominatimPolygonRings(coordinates: JsonArray?): List<List<NominatimGeoPoint>> {
    if (coordinates == null) return emptyList()

    val rings = mutableListOf<List<NominatimGeoPoint>>()
    for (ringIndex in 0 until coordinates.size()) {
        val ring = coordinates[ringIndex].asJsonArrayOrNull() ?: continue
        val points = mutableListOf<NominatimGeoPoint>()
        for (pointIndex in 0 until ring.size()) {
            val point = ring[pointIndex].asJsonArrayOrNull() ?: continue
            if (point.size() < 2) continue
            points += NominatimGeoPoint(
                latitude = point[1].asDoubleValue(),
                longitude = point[0].asDoubleValue()
            )
        }
        if (points.size >= 3) rings += points
    }
    return rings
}

private fun JsonElement?.asJsonArrayOrNull(): JsonArray? {
    return this?.takeIf { it.isJsonArray }?.asJsonArray
}

private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
    return this?.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonElement.asDoubleValue(): Double {
    return if (isJsonPrimitive && asJsonPrimitive.isString) asString.toDouble() else asDouble
}
