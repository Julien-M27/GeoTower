package fr.geotower.data.api

import fr.geotower.data.models.OfflineMapDto
import retrofit2.http.GET

interface AnfrService {
    @GET("/api/v2/maps/catalog")
    suspend fun getMapsCatalog(): List<OfflineMapDto>

    @GET("/api/v2/antennes/hs")
    suspend fun getSitesHsGeoJson(): okhttp3.ResponseBody
}
