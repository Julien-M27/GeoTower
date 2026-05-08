package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import fr.geotower.utils.AppConfig
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONObject

data class ElevationProfileDataPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val distanceMeters: Float
)

data class ElevationProfileDataResult(
    val points: List<ElevationProfileDataPoint>,
    val distanceMeters: Float
)

fun hasElevationProfileLocationPermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
fun getElevationProfileLastKnownLocation(context: Context): Location? {
    if (!hasElevationProfileLocationPermission(context)) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        locationManager.getProviders(true)
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .maxByOrNull { location -> location.time }
    } catch (e: Exception) {
        null
    }
}

fun fetchIgnElevationProfileData(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): ElevationProfileDataResult {
    val totalDistance = FloatArray(1)
    Location.distanceBetween(fromLatitude, fromLongitude, toLatitude, toLongitude, totalDistance)
    val sampling = elevationSamplingForDistance(totalDistance[0])
    val url = Uri.parse("https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevationLine.json")
        .buildUpon()
        .appendQueryParameter("lon", "$fromLongitude|$toLongitude")
        .appendQueryParameter("lat", "$fromLatitude|$toLatitude")
        .appendQueryParameter("resource", "ign_rge_alti_wld")
        .appendQueryParameter("delimiter", "|")
        .appendQueryParameter("indent", "false")
        .appendQueryParameter("measures", "false")
        .appendQueryParameter("profile_mode", "simple")
        .appendQueryParameter("sampling", sampling.toString())
        .build()
        .toString()

    val connection = URL(url).openConnection() as java.net.HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 8000
    connection.readTimeout = 8000

    return try {
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        val json = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        parseElevationProfileData(json, totalDistance[0])
    } finally {
        connection.disconnect()
    }
}

fun calculateElevationLineObstruction(
    profile: ElevationProfileDataResult,
    supportHeightMeters: Double
): Double {
    val total = profile.distanceMeters.coerceAtLeast(1f).toDouble()
    val startHeight = profile.points.first().elevation + ELEVATION_USER_EYE_HEIGHT_METERS
    val endHeight = profile.points.last().elevation + supportHeightMeters
    return profile.points.maxOf { point ->
        val lineHeight = elevationLineHeightAt(
            distanceMeters = point.distanceMeters.toDouble(),
            totalDistanceMeters = total,
            startHeightMeters = startHeight,
            endHeightMeters = endHeight
        )
        point.elevation - lineHeight
    }
}

fun calculateElevationFresnelObstruction(
    profile: ElevationProfileDataResult,
    supportHeightMeters: Double,
    frequencyMHz: Int
): Double {
    val total = profile.distanceMeters.coerceAtLeast(1f).toDouble()
    val startHeight = profile.points.first().elevation + ELEVATION_USER_EYE_HEIGHT_METERS
    val endHeight = profile.points.last().elevation + supportHeightMeters
    return profile.points.maxOf { point ->
        val lineHeight = elevationLineHeightAt(
            distanceMeters = point.distanceMeters.toDouble(),
            totalDistanceMeters = total,
            startHeightMeters = startHeight,
            endHeightMeters = endHeight
        )
        val clearance = elevationFresnelClearanceMeters(
            distanceMeters = point.distanceMeters.toDouble(),
            totalDistanceMeters = total,
            frequencyMHz = frequencyMHz
        )
        point.elevation - (lineHeight - clearance)
    }
}

fun elevationLineHeightAt(
    distanceMeters: Double,
    totalDistanceMeters: Double,
    startHeightMeters: Double,
    endHeightMeters: Double
): Double {
    val fraction = (distanceMeters / totalDistanceMeters.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
    return startHeightMeters + (endHeightMeters - startHeightMeters) * fraction
}

fun elevationFresnelClearanceMeters(
    distanceMeters: Double,
    totalDistanceMeters: Double,
    frequencyMHz: Int
): Double {
    val d1Km = distanceMeters / 1000.0
    val d2Km = (totalDistanceMeters - distanceMeters) / 1000.0
    val totalKm = totalDistanceMeters / 1000.0
    val frequencyGHz = frequencyMHz / 1000.0
    if (d1Km <= 0.0 || d2Km <= 0.0 || totalKm <= 0.0 || frequencyGHz <= 0.0) return 0.0
    return 0.6 * 17.32 * sqrt((d1Km * d2Km) / (frequencyGHz * totalKm))
}

fun extractElevationProfileFrequencies(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return listOf(DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ)

    val frequencyText = raw
        .lineSequence()
        .joinToString(" ") { line -> line.substringBefore("|") }

    val frequencies = extractNormalizedElevationProfileFrequencies(frequencyText)
        .distinct()
        .sortedWith(
            compareBy<Int> { frequency ->
                ELEVATION_PROFILE_FREQUENCY_ORDER.indexOf(frequency).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it }
        )
        .toList()

    return frequencies.ifEmpty { listOf(DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ) }
}

fun extractElevationProfileAntennaHeightsByFrequency(raw: String?): Map<Int, Double> {
    if (raw.isNullOrBlank()) return emptyMap()

    val heightsByFrequency = mutableMapOf<Int, Double>()
    raw.lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotBlank() }
        .forEach { line ->
            val parts = line.split("|").map { part -> part.trim() }
            val rawFrequencies = parts.getOrNull(0).orEmpty()
            val physicalDetails = parts.getOrNull(3).orEmpty()
            val antennaHeight = extractElevationProfileAntennaHeightMeters(physicalDetails) ?: return@forEach

            extractNormalizedElevationProfileFrequencies(rawFrequencies).forEach { frequency ->
                val previousHeight = heightsByFrequency[frequency]
                if (previousHeight == null || antennaHeight > previousHeight) {
                    heightsByFrequency[frequency] = antennaHeight
                }
            }
        }

    return heightsByFrequency
}

fun formatElevationProfileDistance(distanceMeters: Float): String {
    return if (AppConfig.distanceUnit.intValue == 1) {
        val miles = distanceMeters / 1609.34f
        if (miles < 0.1f) "${(distanceMeters * 3.28084f).roundToInt()} ft" else String.format(Locale.US, "%.2f mi", miles)
    } else {
        if (distanceMeters >= 1000f) String.format(Locale.US, "%.2f km", distanceMeters / 1000f) else "${distanceMeters.roundToInt()} m"
    }
}

private fun extractNormalizedElevationProfileFrequencies(text: String): List<Int> {
    return Regex("""\d{3,5}""")
        .findAll(text)
        .mapNotNull { match -> match.value.toIntOrNull()?.let(::normalizeElevationProfileFrequency) }
        .distinct()
        .toList()
}

private fun extractElevationProfileAntennaHeightMeters(physicalDetails: String): Double? {
    return Regex("""(?i)([0-9]+(?:[.,][0-9]+)?)\s*m\b""")
        .findAll(physicalDetails)
        .mapNotNull { match -> match.groupValues[1].replace(',', '.').toDoubleOrNull() }
        .maxOrNull()
}

private fun parseElevationProfileData(json: String, fallbackDistanceMeters: Float): ElevationProfileDataResult {
    val array = JSONObject(json).getJSONArray("elevations")
    val points = mutableListOf<ElevationProfileDataPoint>()
    var cumulativeDistance = 0f
    var previousLat: Double? = null
    var previousLon: Double? = null

    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val latitude = item.getDouble("lat")
        val longitude = item.getDouble("lon")
        val elevation = item.getDouble("z")
        if (elevation <= -99990.0) continue

        if (previousLat != null && previousLon != null) {
            val segment = FloatArray(1)
            Location.distanceBetween(previousLat, previousLon, latitude, longitude, segment)
            cumulativeDistance += segment[0]
        }

        points.add(
            ElevationProfileDataPoint(
                latitude = latitude,
                longitude = longitude,
                elevation = elevation,
                distanceMeters = cumulativeDistance
            )
        )
        previousLat = latitude
        previousLon = longitude
    }

    if (points.size < 2) error("No profile data")
    val distance = if (cumulativeDistance > 0f) cumulativeDistance else fallbackDistanceMeters
    return ElevationProfileDataResult(points = points, distanceMeters = distance)
}

private fun elevationSamplingForDistance(distanceMeters: Float): Int = when {
    distanceMeters < 1_000f -> 90
    distanceMeters < 5_000f -> 140
    distanceMeters < 20_000f -> 220
    else -> 320
}

private fun normalizeElevationProfileFrequency(value: Int): Int? {
    return when (value) {
        in 650..760 -> 700
        in 791..860 -> 800
        in 870..960 -> 900
        in 1700..1900 -> 1800
        in 1901..2200 -> 2100
        in 2400..2700 -> 2600
        in 3300..3800 -> 3500
        in 24000..28000 -> 26000
        else -> null
    }
}

const val ELEVATION_USER_EYE_HEIGHT_METERS = 1.5
const val DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ = 3500
private val ELEVATION_PROFILE_FREQUENCY_ORDER = listOf(3500, 2600, 2100, 1800, 900, 800, 700, 26000)
