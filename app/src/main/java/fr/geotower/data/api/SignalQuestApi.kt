package fr.geotower.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

data class SqPhotosResponse(
    val data: List<SqPhotoData>
)

data class SqPhotoData(
    val imageUrl: String,
    val authorName: String?,
    val uploadedAt: String?,
    val operator: String? = null,
    val publicMetadata: Map<String, Any?>? = null,
    val id: String? = null,
    val thumbnailUrl: String? = null,
    val approved: Boolean? = null
)

data class SqPhotoUploadResponse(
    val data: SqPhotoUploadData?
)

data class SqUploadTokenResponse(
    val token: String?,
    val expiresAt: Long?
)

data class SqPhotoUploadData(
    val id: String?,
    val siteId: String?,
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val operator: String?,
    val authorName: String?,
    val uploadedAt: String?,
    val approved: Boolean?
)

data class SqSpeedtestsResponse(
    val data: List<SqSpeedtestData> = emptyList(),
    val meta: SqSpeedtestsMeta? = null,
    val requestId: String? = null
)

data class SqCoveragePointsResponse(
    val data: List<SqCoveragePointData> = emptyList(),
    val meta: SqCoveragePointsMeta? = null,
    val requestId: String? = null
)

data class SqSpeedtestData(
    val id: String? = null,
    val coordinates: SqCoordinates? = null,
    val downloadSpeed: Float? = null,
    val averageSpeed: Float? = null,
    val maxSpeed: Float? = null,
    val uploadSpeed: Float? = null,
    val ping: Float? = null,
    val timestamp: String? = null,
    val mcc: Int? = null,
    val mnc: Int? = null,
    val mobileOperator: String? = null,
    val networkType: String? = null,
    val connectionType: String? = null,
    val deviceType: String? = null,
    val radio: SqSpeedtestRadio? = null
)

data class SqCoveragePointData(
    val id: String? = null,
    val timestamp: String? = null,
    val coordinates: SqCoordinates? = null,
    val signalStrength: Float? = null,
    val rsrq: Float? = null,
    val snr: Float? = null,
    val technology: String? = null,
    val networkType: String? = null,
    val mobileOperator: String? = null,
    val mcc: Int? = null,
    val mnc: Int? = null,
    val radio: SqCoverageRadio? = null
)

data class SqCoordinates(
    val lat: Double? = null,
    val lng: Double? = null
)

data class SqSpeedtestRadio(
    val enb: String? = null,
    val gnb: String? = null,
    val cellId: String? = null,
    val pci: Int? = null,
    val rsrp: Float? = null,
    val rsrq: Float? = null,
    val snr: Float? = null
)

data class SqCoverageRadio(
    val enb: String? = null,
    val gnb: String? = null,
    val cellId: String? = null,
    val pci: Int? = null
)

data class SqSpeedtestsMeta(
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val bestOnly: Boolean? = null,
    val market: String? = null,
    val operator: String? = null
)

data class SqCoveragePointsMeta(
    val limit: Int? = null,
    val returned: Int? = null,
    val market: String? = null,
    val operator: String? = null,
    val technology: String? = null
)

interface SignalQuestApiService {
    @GET("api/v2/signalquest/sites/{siteId}/photos")
    suspend fun getSitePhotos(
        @Path("siteId") siteId: String,
        @Query("operator") operator: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): retrofit2.Response<SqPhotosResponse>

    @Multipart
    @POST("api/v2/signalquest/sites/{siteId}/photos")
    suspend fun uploadSitePhoto(
        @Path("siteId") siteId: String,
        @Header("X-GeoTower-Upload-Token") uploadToken: String,
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody?,
        @Part("operator") operator: RequestBody?,
        @Part("anfrCode") anfrCode: RequestBody?,
        @Part("nationalSiteCode") nationalSiteCode: RequestBody?,
        @Part("sourceCode") sourceCode: RequestBody?,
        @Part("stripExifBeforeUpload") stripExifBeforeUpload: RequestBody,
        @Part("exifMetadata") exifMetadata: RequestBody?
    ): retrofit2.Response<SqPhotoUploadResponse>

    @POST("api/v2/signalquest/upload-token")
    suspend fun createUploadToken(
        @Query("siteId") siteId: String
    ): retrofit2.Response<SqUploadTokenResponse>

    @GET("api/v2/signalquest/speedtests/site")
    suspend fun getSiteSpeedtests(
        @Query("siteId") siteId: String? = null,
        @Query("anfrCode") anfrCode: String? = null,
        @Query("nationalSiteCode") nationalSiteCode: String? = null,
        @Query("sourceCode") sourceCode: String? = null,
        @Query("enb") enb: String? = null,
        @Query("operator") operator: String? = null,
        @Query("mcc") mcc: Int? = null,
        @Query("mnc") mnc: Int? = null,
        @Query("market") market: String = "FR",
        @Query("bestOnly") bestOnly: Boolean = true,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): retrofit2.Response<SqSpeedtestsResponse>

    @GET("api/v2/signalquest/coverage/points")
    suspend fun getCoveragePoints(
        @Query("market") market: String = "FR",
        @Query("operator") operator: String? = null,
        @Query("technology") technology: String? = null,
        @Query("north") north: Double? = null,
        @Query("south") south: Double? = null,
        @Query("east") east: Double? = null,
        @Query("west") west: Double? = null,
        @Query("days") days: Int? = null,
        @Query("limit") limit: Int? = null
    ): retrofit2.Response<SqCoveragePointsResponse>
}

object SignalQuestClient {
    val api: SignalQuestApiService by lazy {
        Retrofit.Builder()
            .baseUrl(RetrofitClient.BASE_URL)
            .client(RetrofitClient.currentClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SignalQuestApiService::class.java)
    }
}
