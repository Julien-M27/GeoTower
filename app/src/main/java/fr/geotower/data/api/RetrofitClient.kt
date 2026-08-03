package fr.geotower.data.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    /**
     * URL de référence des appels : c'est l'hôte **écrit** dans le code. L'hôte réellement contacté
     * est celui de [ApiEndpoints.active] (principal ou miroir), substitué par
     * [serverFailoverInterceptor] au moment de l'envoi.
     */
    const val BASE_URL = "https://api.geotower.fr/"

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

    /**
     * Prédicat injecté par l'app : true ⇒ le mode « traitement local » (niveau 3) bloque les
     * endpoints communautaires / mise à jour / live du client partagé. Laisse passer DB, flags et
     * pannes. Injecté (pas d'import AppConfig) pour garder la couche réseau découplée.
     */
    @Volatile
    var communityEndpointBlocker: () -> Boolean = { false }

    private fun isLocallyBlockedPath(path: String): Boolean =
        path.startsWith("/api/v2/signalquest/") ||
            path == "/api/v2/app/latest" ||
            path.startsWith("/api/v2/live/")

    private val localModeBlockInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (communityEndpointBlocker() && isLocallyBlockedPath(request.url.encodedPath)) {
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Traitement local actif : endpoint desactive")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        } else {
            chain.proceed(request)
        }
    }

    /**
     * Aiguille chaque appel vers le serveur actif ([ApiEndpoints]) et bascule sur l'autre serveur
     * quand celui-ci ne répond pas (échec réseau ou 5xx). Placé **au plus près du réseau**, sous
     * [offlineFallbackInterceptor] : sinon une réponse servie depuis le cache masquerait la panne
     * et le miroir ne serait jamais essayé.
     *
     * Les requêtes à corps (POST d'une photo…) ne sont pas rejouées ici — un corps « one-shot » ne
     * se relit pas. Elles partent simplement sur le serveur actif du moment ; c'est la sonde qui
     * l'aura déjà fait basculer.
     */
    private val serverFailoverInterceptor = Interceptor { chain ->
        val request = chain.request()
        val pinnedHost = request.header(ApiEndpoints.PIN_HOST_HEADER)
        if (pinnedHost != null) {
            // Sonde : l'hôte visé est imposé, on retire juste l'en-tête interne.
            return@Interceptor chain.proceed(
                request.newBuilder().removeHeader(ApiEndpoints.PIN_HOST_HEADER).build()
            )
        }
        if (!ApiEndpoints.isOfficialApiHost(request.url.host)) return@Interceptor chain.proceed(request)

        val preferred = ApiEndpoints.active()
        val firstAttempt = runCatching { chain.proceed(request.retargetTo(preferred)) }
        val firstResponse = firstAttempt.getOrNull()
        val serverIsDown = firstResponse == null || firstResponse.code >= 500

        val fallback = ApiEndpoints.failoverTarget(preferred)
        val retryable = request.method.equals("GET", ignoreCase = true) && request.body == null
        if (!serverIsDown || fallback == null || !retryable) {
            if (!serverIsDown) ApiEndpoints.switchTo(preferred)
            return@Interceptor firstAttempt.getOrThrow()
        }

        // On renonce à la réponse du serveur actif : elle doit être fermée avant de repartir.
        firstResponse?.close()
        val secondAttempt = runCatching { chain.proceed(request.retargetTo(fallback)) }
        val secondResponse = secondAttempt.getOrNull()
        if (secondResponse != null && secondResponse.code < 500) {
            ApiEndpoints.switchTo(fallback)
            return@Interceptor secondResponse
        }

        // Les deux serveurs sont hors-jeu : aucune bascule, et on remonte l'échec du miroir
        // (celui du serveur actif a déjà été consommé) pour que le cache hors-ligne prenne le relais.
        secondAttempt.getOrThrow()
    }

    private fun Request.retargetTo(server: ApiServer): Request {
        if (url.host.equals(server.host, ignoreCase = true)) return this
        return newBuilder().url(url.newBuilder().host(server.host).build()).build()
    }

    val currentClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { httpCache?.let { cache(it) } }
            .addInterceptor(localModeBlockInterceptor)
            .addInterceptor(offlineFallbackInterceptor)
            .addInterceptor(serverFailoverInterceptor)
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
