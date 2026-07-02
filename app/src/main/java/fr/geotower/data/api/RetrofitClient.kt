package fr.geotower.data.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    const val BASE_URL = "https://api.cajejuma.fr/"

    private const val HTTP_CACHE_SIZE_BYTES = 20L * 1024 * 1024 // 20 Mo de cache HTTP disque

    @Volatile
    private var appContext: Context? = null

    /**
     * À appeler tôt (Application.onCreate) AVANT toute requête : fournit le [Context] nécessaire
     * au cache HTTP disque. Sans cet appel, le client fonctionne simplement sans cache.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val httpCache: Cache? by lazy {
        appContext?.let { ctx ->
            try {
                Cache(File(ctx.cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Sert le cache HTTP UNIQUEMENT en cas d'échec réseau (hors-ligne) : jamais de données
     * périmées quand la connexion est présente. N'affecte que les GET, et préserve le
     * comportement d'origine (l'IOException est relancée) si rien n'est en cache.
     */
    private val offlineFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        try {
            chain.proceed(request)
        } catch (e: IOException) {
            if (!request.method.equals("GET", ignoreCase = true)) throw e
            val cachedResponse = chain.proceed(
                request.newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .onlyIfCached()
                            .maxStale(7, TimeUnit.DAYS)
                            .build()
                    )
                    .build()
            )
            if (cachedResponse.code == 504) {
                cachedResponse.close()
                throw e // rien en cache → on conserve le comportement hors-ligne d'origine
            }
            cachedResponse
        }
    }

    val currentClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { httpCache?.let { cache(it) } }
            .addInterceptor(offlineFallbackInterceptor)
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
