package fr.geotower.data.api

import fr.geotower.data.models.LiveSiteResponseDto
import fr.geotower.data.models.LiveSitesListResponseDto
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface LiveSitesApiService {
    @GET("api/v2/live/fr/sites/nearby")
    suspend fun getNearbySites(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int,
        @Query("radius_km") radiusKm: Double? = null,
        @Query("zb_only") zbOnly: Boolean? = null,
        @Query("active_only") activeOnly: Boolean? = null
    ): Response<LiveSitesListResponseDto>

    @GET("api/v2/live/fr/sites/bbox")
    suspend fun getSitesInBox(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double,
        @Query("limit") limit: Int,
        @Query("zb_only") zbOnly: Boolean? = null,
        @Query("active_only") activeOnly: Boolean? = null
    ): Response<LiveSitesListResponseDto>

    @GET("api/v2/live/fr/sites/{siteId}")
    suspend fun getSite(
        @Path("siteId") siteId: String
    ): Response<LiveSiteResponseDto>
}

object LiveSitesClient {
    val api: LiveSitesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(RetrofitClient.BASE_URL)
            .client(RetrofitClient.currentClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LiveSitesApiService::class.java)
    }
}
