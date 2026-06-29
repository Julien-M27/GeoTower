package fr.geotower.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Un bâtiment BD TOPO : ses anneaux extérieurs (en [lon, lat]) et l'altitude de son toit.
 *
 * [roofAltitudeAbsolute] est l'altitude absolue du toit (NGF) quand la BD TOPO la fournit ;
 * sinon on retombe sur [heightMeters] (hauteur relative) à additionner au terrain.
 */
internal data class BdTopoBuilding(
    val exteriorRings: List<List<DoubleArray>>,
    val roofAltitudeAbsolute: Double?,
    val heightMeters: Double?,
    override val minLon: Double,
    override val minLat: Double,
    override val maxLon: Double,
    override val maxLat: Double
) : fr.geotower.data.coverage.BuildingObstacle {
    override fun contains(longitude: Double, latitude: Double): Boolean {
        // Rejet rapide par boîte englobante avant le test point-dans-polygone.
        if (longitude < minLon || longitude > maxLon || latitude < minLat || latitude > maxLat) {
            return false
        }
        return exteriorRings.any { ring -> ringContains(ring, longitude, latitude) }
    }

    /** Altitude du toit au-dessus d'un point dont le terrain est à [terrainElevation]. */
    override fun topAltitude(terrainElevation: Double): Double {
        roofAltitudeAbsolute?.let { return it }
        val height = heightMeters ?: return terrainElevation
        return terrainElevation + height
    }
}

internal object BdTopoBuildingsApi {
    private const val WFS_URL = "https://data.geopf.fr/wfs/ows"
    private const val LAYER = "BDTOPO_V3:batiment"
    private const val MAX_FEATURES = 5000
    private const val BBOX_MARGIN_DEGREES = 0.0003

    /**
     * Récupère les bâtiments BD TOPO dans l'emprise du segment [from] → [to] (plus une petite marge).
     * Une seule requête WFS. Lève une exception en cas d'échec réseau / HTTP.
     */
    fun fetchBuildingsForSegment(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): List<BdTopoBuilding> {
        return fetchBuildingsForBbox(
            minLatitude = minOf(fromLatitude, toLatitude) - BBOX_MARGIN_DEGREES,
            minLongitude = minOf(fromLongitude, toLongitude) - BBOX_MARGIN_DEGREES,
            maxLatitude = maxOf(fromLatitude, toLatitude) + BBOX_MARGIN_DEGREES,
            maxLongitude = maxOf(fromLongitude, toLongitude) + BBOX_MARGIN_DEGREES
        )
    }

    /**
     * Récupère les bâtiments BD TOPO dans une bbox — utilisé par la couverture théorique pour couvrir
     * tout le disque autour d'un site en **une seule** requête WFS.
     * ⚠️ En zone dense le résultat est plafonné à [MAX_FEATURES] (5000) — tuilage à envisager (V2).
     */
    fun fetchBuildingsForBbox(
        minLatitude: Double,
        minLongitude: Double,
        maxLatitude: Double,
        maxLongitude: Double
    ): List<BdTopoBuilding> {
        // En EPSG:4326 (urn) la BBOX est en ordre lat,lon ; la sortie GeoJSON reste en lon,lat.
        val url = WFS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("SERVICE", "WFS")
            .addQueryParameter("VERSION", "2.0.0")
            .addQueryParameter("REQUEST", "GetFeature")
            .addQueryParameter("TYPENAMES", LAYER)
            .addQueryParameter("SRSNAME", "urn:ogc:def:crs:EPSG::4326")
            .addQueryParameter("BBOX", "$minLatitude,$minLongitude,$maxLatitude,$maxLongitude,urn:ogc:def:crs:EPSG::4326")
            .addQueryParameter("COUNT", MAX_FEATURES.toString())
            .addQueryParameter("OUTPUTFORMAT", "application/json")
            .build()

        val request = Request.Builder().url(url).get().build()

        return RetrofitClient.currentClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty response")
            parseBuildings(body)
        }
    }
}

internal fun parseBuildings(json: String): List<BdTopoBuilding> {
    val root = JsonParser.parseString(json).asObjectOrNull() ?: return emptyList()
    val features = root.get("features")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()

    val buildings = mutableListOf<BdTopoBuilding>()
    for (index in 0 until features.size()) {
        val feature = features[index].asObjectOrNull() ?: continue
        val geometry = feature.get("geometry")?.asObjectOrNull() ?: continue
        val properties = feature.get("properties")?.asObjectOrNull()

        val rings = extractExteriorRings(geometry)
        if (rings.isEmpty()) continue

        val roofMax = properties?.optDouble("altitude_maximale_toit")
        val roofMin = properties?.optDouble("altitude_minimale_toit")
        val solMax = properties?.optDouble("altitude_maximale_sol")
        val height = properties?.optDouble("hauteur")
        val roofAltitude = roofMax
            ?: roofMin
            ?: solMax?.let { sol -> height?.let { sol + it } }

        var minLon = Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        rings.forEach { ring ->
            ring.forEach { point ->
                if (point[0] < minLon) minLon = point[0]
                if (point[0] > maxLon) maxLon = point[0]
                if (point[1] < minLat) minLat = point[1]
                if (point[1] > maxLat) maxLat = point[1]
            }
        }

        buildings += BdTopoBuilding(
            exteriorRings = rings,
            roofAltitudeAbsolute = roofAltitude,
            heightMeters = height,
            minLon = minLon,
            minLat = minLat,
            maxLon = maxLon,
            maxLat = maxLat
        )
    }
    return buildings
}

/** Renvoie les anneaux extérieurs (premier anneau de chaque polygone) en [lon, lat]. */
private fun extractExteriorRings(geometry: JsonObject): List<List<DoubleArray>> {
    val type = geometry.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return emptyList()
    val coordinates = geometry.get("coordinates")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()

    return when (type) {
        "Polygon" -> {
            val ring = coordinates.firstOrNull()?.asArrayOrNull()?.let(::parseRing)
            if (ring.isNullOrEmpty()) emptyList() else listOf(ring)
        }
        "MultiPolygon" -> {
            val rings = mutableListOf<List<DoubleArray>>()
            for (polygonIndex in 0 until coordinates.size()) {
                val polygon = coordinates[polygonIndex].asArrayOrNull() ?: continue
                val ring = polygon.firstOrNull()?.asArrayOrNull()?.let(::parseRing)
                if (!ring.isNullOrEmpty()) rings += ring
            }
            rings
        }
        else -> emptyList()
    }
}

private fun parseRing(ring: JsonArray): List<DoubleArray> {
    val points = mutableListOf<DoubleArray>()
    for (index in 0 until ring.size()) {
        val pair = ring[index].asArrayOrNull() ?: continue
        if (pair.size() < 2) continue
        val lon = pair[0].asDoubleOrNull() ?: continue
        val lat = pair[1].asDoubleOrNull() ?: continue
        points += doubleArrayOf(lon, lat)
    }
    return points
}

/** Ray casting : le point ([lon],[lat]) est-il dans l'anneau (liste de [lon, lat]) ? */
private fun ringContains(ring: List<DoubleArray>, lon: Double, lat: Double): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val xi = ring[i][0]
        val yi = ring[i][1]
        val xj = ring[j][0]
        val yj = ring[j][1]
        val intersects = (yi > lat) != (yj > lat) &&
            lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

private fun JsonElement?.asObjectOrNull(): JsonObject? =
    this?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? =
    this?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonElement?.asDoubleOrNull(): Double? =
    this?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

private fun JsonObject.optDouble(name: String): Double? {
    val element = get(name) ?: return null
    if (element.isJsonNull) return null
    return runCatching { element.asDouble }.getOrNull()
}
