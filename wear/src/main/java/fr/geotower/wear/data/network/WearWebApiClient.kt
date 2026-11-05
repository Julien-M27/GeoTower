package fr.geotower.wear.data.network

import android.location.Location
import com.google.gson.annotations.SerializedName
import fr.geotower.wear.data.model.WearTower
import okhttp3.OkHttpClient
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
        @Query("limit") limit: Int = 15,
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
    @SerializedName("tech_mask") val techMask: Int? = null
)

object WearWebApiClient {
    private const val BASE_URL = "https://api.geotower.fr/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val api: WearLiveSitesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WearLiveSitesApi::class.java)
    }

    suspend fun fetchNearbyTowers(lat: Double, lon: Double, radiusKm: Double = 5.0, limit: Int = 15): List<WearTower> {
        val resp = api.getNearbySites(lat = lat, lon = lon, limit = limit, radiusKm = radiusKm)
        if (!resp.isSuccessful) {
            throw RuntimeException("Erreur serveur API (${resp.code()})")
        }

        val sites = resp.body()?.sites.orEmpty()
        val resultsArray = FloatArray(2)

        return sites.mapNotNull { site ->
            val siteLat = site.latitude ?: return@mapNotNull null
            val siteLon = site.longitude ?: return@mapNotNull null

            Location.distanceBetween(lat, lon, siteLat, siteLon, resultsArray)
            val distance = resultsArray[0]
            val bearing = (resultsArray[1] + 360f) % 360f

            val addressParts = listOfNotNull(site.adresse, site.commune).filter { it.isNotBlank() }
            val formattedAddress = if (addressParts.isEmpty()) "" else addressParts.joinToString(", ")

            WearTower(
                id = site.idAnfr ?: site.hashCode().toString(),
                operator = site.operatorName ?: "Inconnu",
                distanceMeters = distance.toInt(),
                bearingDegrees = bearing.toInt(),
                technologies = parseTechMask(site.techMask ?: 0),
                address = formattedAddress,
                latitude = siteLat,
                longitude = siteLon,
                source = "web_api"
            )
        }.sortedBy { it.distanceMeters }
    }

    private fun parseTechMask(mask: Int): String {
        val techs = mutableListOf<String>()
        if (mask and 1 != 0) techs.add("2G")
        if (mask and 2 != 0) techs.add("3G")
        if (mask and 4 != 0) techs.add("4G")
        if (mask and 8 != 0) techs.add("5G")
        return if (techs.isEmpty()) "Mobile" else techs.joinToString(", ")
    }
}
