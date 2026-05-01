package fr.geotower.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // <-- ON UTILISE GSON ICI
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query // ✅ AJOUT DE L'IMPORT QUERY
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// 1. Les modèles de données (Photos)
data class SqPhotosResponse(
    val data: List<SqPhotoData>
)

data class SqPhotoData(
    val imageUrl: String,
    val thumbnailUrl: String,
    val authorName: String?,
    val uploadedAt: String?
)

// --- Modèles pour les Speedtests ---
data class SqSpeedtestsResponse(
    val data: List<SqSpeedtestData>
)

data class SqSpeedtestData(
    val id: String?,          // 🚨 C'est un String dans le JSON !
    val downloadSpeed: Float?,// 🚨 Nom exact du JSON
    val uploadSpeed: Float?,  // 🚨 Nom exact du JSON
    val ping: Float?,         // 🚨 Changé de Int? à Float? car l'API peut renvoyer 39.125
    val timestamp: String?    // 🚨 Nom exact du JSON
)

// 2. L'interface de l'API
interface SignalQuestApiService {
    @GET("api/external/v1/sites/{siteId}/photos")
    suspend fun getSitePhotos(
        @Header("Authorization") authHeader: String,
        @Path("siteId") siteId: String
    ): retrofit2.Response<SqPhotosResponse>

    @Multipart
    @POST("api/external/v1/sites/{siteId}/photos")
    suspend fun uploadSitePhoto(
        @Header("Authorization") authHeader: String,
        @Path("siteId") siteId: String,
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody?,
        @Part("operator") operator: RequestBody?
    ): retrofit2.Response<ResponseBody>

    @GET("api/external/v1/speedtests/site")
    suspend fun getSiteSpeedtests(
        @Header("Authorization") authHeader: String,
        @Query("siteId") siteId: String? = null,
        @Query("anfrCode") anfrCode: String? = null,
        @Query("enb") enb: String? = null,
        @Query("operator") operator: String? = null,
        @Query("market") market: String = "FR",
        @Query("bestOnly") bestOnly: Boolean = true
    ): retrofit2.Response<SqSpeedtestsResponse>
}

// 3. Le Client Retrofit dédié à SignalQuest
object SignalQuestClient {
    val api: SignalQuestApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://signalquest.fr/")
            .client(RetrofitClient.currentClient) // ✅ UTILISATION DU CLIENT SÉCURISÉ/UNSAFE SELON ANDROID
            .addConverterFactory(GsonConverterFactory.create()) // <-- GSON CONVERTER ICI
            .build()
            .create(SignalQuestApiService::class.java)
    }
}