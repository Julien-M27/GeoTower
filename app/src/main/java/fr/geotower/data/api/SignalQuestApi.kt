package fr.geotower.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // <-- ON UTILISE GSON ICI
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// 1. Les modèles de données
data class SqPhotosResponse(
    val data: List<SqPhotoData>
)

data class SqPhotoData(
    val imageUrl: String,
    val thumbnailUrl: String,
    val authorName: String?,
    val uploadedAt: String?
)

// 2. L'interface de l'API
interface SignalQuestApiService {
    @GET("api/external/v1/sites/{siteId}/photos")
    suspend fun getSitePhotos(
        @Header("Authorization") authHeader: String,
        @Path("siteId") siteId: String
    ): retrofit2.Response<SqPhotosResponse>

    // --- NOUVELLE MÉTHODE POUR L'UPLOAD ---
    @Multipart
    @POST("api/external/v1/sites/{siteId}/photos")
    suspend fun uploadSitePhoto(
        @Header("Authorization") authHeader: String,
        @Path("siteId") siteId: String,
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody?,
        @Part("operator") operator: RequestBody?
    ): retrofit2.Response<ResponseBody>
}

// 3. Le Client Retrofit dédié à SignalQuest
object SignalQuestClient {
    val api: SignalQuestApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://sfr.alexandregermain.eu/")
            .addConverterFactory(GsonConverterFactory.create()) // <-- GSON CONVERTER ICI
            .build()
            .create(SignalQuestApiService::class.java)
    }
}