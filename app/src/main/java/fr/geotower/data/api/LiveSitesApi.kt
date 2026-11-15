package fr.geotower.data.api

import fr.geotower.data.models.LiveDbStatusDto
import fr.geotower.data.models.LiveFhLinksResponseDto
import fr.geotower.data.models.LiveRadioMarkerResponseDto
import fr.geotower.data.models.LiveRadioMarkersResponseDto
import fr.geotower.data.models.LiveSiteResponseDto
import fr.geotower.data.models.LiveSitesListResponseDto
import fr.geotower.data.models.LiveDepartmentOperatorStatsResponseDto
import fr.geotower.data.models.LiveDepartmentStatsResponseDto
import fr.geotower.data.models.LiveRadioStatsResponseDto
import fr.geotower.data.models.LiveWeeklyRadioStatsResponseDto
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface LiveSitesApiService {
    /** Jeu de données servi par la base live : versions, dates ANFR, nombre de stations. */
    @GET("api/v2/live/fr/status")
    suspend fun getStatus(): Response<LiveDbStatusDto>

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

    @GET("api/v2/live/fr/fh-links/bbox")
    suspend fun getFhLinksInBox(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double,
        @Query("limit") limit: Int
    ): Response<LiveFhLinksResponseDto>

    @GET("api/v2/live/fr/sites/{siteId}")
    suspend fun getSite(
        @Path("siteId") siteId: String
    ): Response<LiveSiteResponseDto>

    @GET("api/v2/live/fr/stats/current")
    suspend fun getCurrentRadioStats(
        @Query("operator") operators: List<String>
    ): Response<LiveRadioStatsResponseDto>

    @GET("api/v2/live/fr/stats/weekly")
    suspend fun getWeeklyRadioStats(
        @Query("operator") operators: List<String>
    ): Response<LiveWeeklyRadioStatsResponseDto>

    @GET("api/v2/live/fr/stats/departments")
    suspend fun getDepartmentStats(): Response<LiveDepartmentStatsResponseDto>

    @GET("api/v2/live/fr/stats/departments/{deptCode}")
    suspend fun getDepartmentOperatorTechStats(
        @Path("deptCode") deptCode: String
    ): Response<LiveDepartmentOperatorStatsResponseDto>

    @GET("api/v2/live/fr/radio/bbox")
    suspend fun getRadioMarkersInBox(
        @Query("zoom") zoom: Double,
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double,
        @Query("service_mask") serviceMask: Int,
        @Query("category_mask") categoryMask: Int
    ): Response<LiveRadioMarkersResponseDto>

    @GET("api/v2/live/fr/radio/support/{supportId}")
    suspend fun getRadioMarkersForSupport(
        @Path("supportId") supportId: String,
        @Query("limit") limit: Int
    ): Response<LiveRadioMarkersResponseDto>

    @GET("api/v2/live/fr/radio/sites/{stationId}/{supportId}")
    suspend fun getRadioMarkerForSite(
        @Path("stationId") stationId: String,
        @Path("supportId") supportId: String
    ): Response<LiveRadioMarkerResponseDto>

    @GET("api/v2/live/fr/radio/search")
    suspend fun searchRadioSites(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("exact_id") exactId: Boolean = false
    ): Response<LiveRadioMarkersResponseDto>
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
