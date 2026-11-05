package fr.geotower.wear.data.network

import android.location.Location
import android.util.Log
import com.google.gson.annotations.SerializedName
import fr.geotower.wear.BuildConfig
import fr.geotower.wear.data.model.WearOperatorSite
import fr.geotower.wear.data.model.WearTower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface WearLiveSitesApi {
    @GET("api/v2/live/fr/sites/nearby")
    suspend fun getNearbySites(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 20,
        @Query("radius_km") radiusKm: Double = 5.0,
        @Query("active_only") activeOnly: Boolean = true
    ): Response<WearLiveSitesResponseDto>
}

data class WearLiveSitesResponseDto(
    @SerializedName("sites") val sites: List<WearLiveSiteSummaryDto> = emptyList()
)

data class WearLiveSiteSummaryDto(
    @SerializedName("id_anfr") val idAnfr: String? = null,
    @SerializedName("operator_name") val operatorName: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("azimuts") val azimuts: String? = null,
    @SerializedName("commune") val commune: String? = null,
    @SerializedName("adresse") val adresse: String? = null,
    @SerializedName("nature_support") val natureSupport: String? = null,
    @SerializedName("hauteur") val hauteur: Double? = null,
    @SerializedName("tech_mask") val techMask: Int? = null,
    @SerializedName("band_mask") val bandMask: Int? = null
)

object WearWebApiClient {
    private const val TAG = "WearWebApiClient"
    private const val BASE_URL = "https://api.geotower.fr/"

    // Timeouts adaptés au Bluetooth proxy Wear OS (latence plus élevée qu'en Wi-Fi direct)
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "GeoTower-WearOS/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    val api: WearLiveSitesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WearLiveSitesApi::class.java)
    }

    suspend fun fetchNearbyTowers(lat: Double, lon: Double, radiusKm: Double = 5.0, limit: Int = 15): List<WearTower> = withContext(Dispatchers.IO) {
        Log.i(TAG, "fetchNearbyTowers début : lat=$lat, lon=$lon, radius=$radiusKm, limit=$limit")

        // 1. Tenter l'endpoint dédié aux montres (identique à Garmin, groupé par support physique avec multi-opérateurs)
        try {
            val watchUrl = "${BASE_URL}api/v1/watch/nearby?lat=$lat&lon=$lon&limit=$limit&radius_km=$radiusKm"
            val request = Request.Builder().url(watchUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = parseWatchApiJson(body, lat, lon)
                        if (parsed.isNotEmpty()) {
                            Log.i(TAG, "Succès Watch API : ${parsed.size} supports chargés")
                            return@withContext parsed
                        }
                    }
                } else {
                    Log.w(TAG, "Watch API code retour : ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Watch API endpoint non disponible, bascule sur l'API Live v2 : ${e.message}", e)
        }

        // 2. Repli sur l'API Live v2 avec regroupement par support physique
        Log.i(TAG, "Interrogation de l'API Live v2 de secours...")
        val resp = api.getNearbySites(lat = lat, lon = lon, limit = limit * 2, radiusKm = radiusKm)
        if (!resp.isSuccessful) {
            throw RuntimeException("Erreur serveur API (${resp.code()})")
        }

        val rawSites = resp.body()?.sites.orEmpty()
        val resultsArray = FloatArray(2)

        // Regroupement par support (coordonnées très proches ~15m ou idAnfr)
        val supportMap = mutableMapOf<String, MutableList<WearLiveSiteSummaryDto>>()

        for (site in rawSites) {
            val siteLat = site.latitude ?: continue
            val siteLon = site.longitude ?: continue
            val key = "${roundTo4(siteLat)}_${roundTo4(siteLon)}"
            supportMap.getOrPut(key) { mutableListOf() }.add(site)
        }

        val parsedV2 = supportMap.values.mapNotNull { group ->
            val first = group.first()
            val siteLat = first.latitude ?: return@mapNotNull null
            val siteLon = first.longitude ?: return@mapNotNull null

            Location.distanceBetween(lat, lon, siteLat, siteLon, resultsArray)
            val distance = resultsArray[0]
            val bearing = (resultsArray[1] + 360f) % 360f

            val addressParts = listOfNotNull(first.adresse, first.commune).filter { it.isNotBlank() }
            val formattedAddress = if (addressParts.isEmpty()) "Site relais" else addressParts.joinToString(", ")

            val operatorsList = group.map { s ->
                val opName = s.operatorName ?: "Inconnu"
                val azList = s.azimuts?.split(",", ";", "-")
                    ?.mapNotNull { it.replace("°", "").trim().toIntOrNull() }
                    ?.distinct()
                    ?.sorted() ?: listOf(0, 120, 240)

                WearOperatorSite(
                    operator = opName,
                    technologies = parseTechMask(s.techMask ?: 0),
                    bands = parseTechBands(s.techMask ?: 0),
                    azimuths = azList
                )
            }.distinctBy { it.operator }

            WearTower(
                id = first.idAnfr ?: first.hashCode().toString(),
                distanceMeters = distance.toInt(),
                bearingDegrees = bearing.toInt(),
                address = formattedAddress,
                latitude = siteLat,
                longitude = siteLon,
                supportType = first.natureSupport ?: "Pylône autostable",
                supportHeight = first.hauteur?.toInt() ?: 35,
                operators = operatorsList,
                source = "web_api"
            )
        }.sortedBy { it.distanceMeters }.take(limit)

        Log.i(TAG, "Succès API Live v2 : ${parsedV2.size} supports agrégés")
        parsedV2
    }

    private fun parseWatchApiJson(jsonStr: String, userLat: Double, userLon: Double): List<WearTower> {
        val root = JSONObject(jsonStr)
        val sitesArray = root.optJSONArray("sites") ?: return emptyList()
        val result = mutableListOf<WearTower>()

        for (i in 0 until sitesArray.length()) {
            val obj = sitesArray.getJSONObject(i)
            val d = obj.optInt("d", 0)
            val az = obj.optInt("az", 0)
            val lat = obj.optDouble("lat", userLat)
            val lon = obj.optDouble("lon", userLon)
            val supName = obj.optString("sup_name").ifBlank { obj.optString("support_name", "Pylône autostable") }
            val h = obj.optInt("h", 30)
            val addr = obj.optString("addr", "")
            val loc = obj.optString("loc", "")
            val fullAddr = if (addr.isNotBlank() && loc.isNotBlank()) "$addr, $loc" else (addr.ifBlank { loc })

            val opsArray = obj.optJSONArray("ops")
            val opList = mutableListOf<WearOperatorSite>()
            if (opsArray != null) {
                for (j in 0 until opsArray.length()) {
                    val opObj = opsArray.getJSONObject(j)
                    val opId = opObj.optInt("id", 1)
                    val opName = when (opId) {
                        1 -> "ORANGE"
                        2 -> "SFR"
                        3 -> "BOUYGUES TELECOM"
                        4 -> "FREE"
                        else -> "AUTRE"
                    }
                    val t = opObj.optString("t", "4G / 5G")
                    val bands = opObj.optString("bands", "700, 1800, 2100 MHz")
                    val azArray = opObj.optJSONArray("az")
                    val azimList = mutableListOf<Int>()
                    if (azArray != null) {
                        for (k in 0 until azArray.length()) {
                            azimList.add(azArray.getInt(k))
                        }
                    }
                    opList.add(
                        WearOperatorSite(
                            operator = opName,
                            technologies = t,
                            bands = bands,
                            azimuths = azimList.ifEmpty { listOf(0, 120, 240) }
                        )
                    )
                }
            }

            result.add(
                WearTower(
                    id = obj.optString("id", i.toString()),
                    distanceMeters = d,
                    bearingDegrees = az,
                    address = fullAddr,
                    latitude = lat,
                    longitude = lon,
                    supportType = supName,
                    supportHeight = h,
                    operators = opList,
                    source = "web_api"
                )
            )
        }
        return result
    }

    private fun roundTo4(v: Double): Double = Math.round(v * 10000.0) / 10000.0

    private fun parseTechMask(mask: Int): String {
        val techs = mutableListOf<String>()
        if (mask and 1 != 0) techs.add("2G")
        if (mask and 2 != 0) techs.add("3G")
        if (mask and 4 != 0) techs.add("4G")
        if (mask and 8 != 0) techs.add("5G")
        return if (techs.isEmpty()) "4G / 5G" else techs.joinToString(" / ")
    }

    private fun parseTechBands(mask: Int): String {
        val parts = mutableListOf<String>()
        if (mask and 8 != 0) parts.add("5G: 3500, 2100, 700")
        if (mask and 4 != 0) parts.add("4G: 700, 800, 1800, 2100, 2600")
        if (mask and 2 != 0) parts.add("3G: 900, 2100")
        if (mask and 1 != 0) parts.add("2G: 900, 1800")
        return if (parts.isEmpty()) "4G: 800, 1800, 2600 • 5G: 3500 MHz" else parts.joinToString(" • ") + " MHz"
    }
}
