package fr.geotower.data.api

import com.google.gson.annotations.SerializedName
import fr.geotower.data.models.OfflineMapDto
import retrofit2.http.GET

data class SitesHsInfoDto(
    @SerializedName("last_update")
    val lastUpdate: String? = null
)

interface AnfrService {
    @GET("/api/v2/download/manifest")
    suspend fun getDownloadManifest(): okhttp3.ResponseBody

    @GET("/api/v2/maps/catalog")
    suspend fun getMapsCatalog(): List<OfflineMapDto>

    @GET("/api/v2/antennes/hs")
    suspend fun getSitesHsGeoJson(): okhttp3.ResponseBody

    @GET("/api/v2/antennes/hs/info")
    suspend fun getSitesHsInfo(): SitesHsInfoDto
}
