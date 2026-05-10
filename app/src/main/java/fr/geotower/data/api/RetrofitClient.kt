package fr.geotower.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.cajejuma.fr/"

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    val currentClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val apiService: AnfrService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(currentClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AnfrService::class.java)
    }
}
