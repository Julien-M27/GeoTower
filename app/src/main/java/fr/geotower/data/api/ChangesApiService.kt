package fr.geotower.data.api

import fr.geotower.data.models.ChangesMapPayload
import fr.geotower.data.models.ChangesWeeksResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface ChangesApiService {

    @Headers("Cache-Control: no-cache")
    @GET("api/v2/changes/weeks")
    suspend fun getAvailableWeeks(): Response<ChangesWeeksResponse>

    @Headers("Cache-Control: no-cache")
    @GET("api/v2/changes/map/{date}")
    suspend fun getMapChanges(
        @Path("date") date: String,
        @Query("raw") raw: Boolean = true
    ): Response<ChangesMapPayload>
}
