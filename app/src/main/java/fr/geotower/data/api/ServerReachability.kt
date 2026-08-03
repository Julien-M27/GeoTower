package fr.geotower.data.api

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Verdict de la sonde serveur GeoTower. */
enum class ServerStatus {
    /** Pas encore sondé, ou appareil hors-ligne (c'est alors le bandeau « hors ligne » qui parle). */
    UNKNOWN,

    /** Le serveur répond (même par une erreur 4xx : il est bien joignable). */
    REACHABLE,

    /** Aucune réponse, ou erreur 5xx : maintenance, panne, ou réseau qui bloque l'API. */
    UNREACHABLE
}

/**
 * Sonde « le serveur GeoTower répond-il ? », distincte de la connectivité de l'appareil
 * (voir `rememberNetworkConnectivityState`) : on peut très bien avoir du réseau et une API en panne.
 *
 * On interroge l'endpoint des feature flags : c'est la plus petite réponse JSON du serveur, elle est
 * toujours servie et n'est jamais coupée par le mode « traitement local ».
 *
 * C'est aussi l'arbitre de la bascule principal ↔ miroir ([ApiEndpoints]) : chaque serveur est sondé
 * sur son hôte **imposé** (en-tête [ApiEndpoints.PIN_HOST_HEADER]), sinon l'intercepteur de
 * [RetrofitClient] réécrirait l'URL et les deux sondes viseraient le même serveur. Le verdict
 * UNREACHABLE n'est donc rendu que si **aucun** des deux ne répond.
 */
object ServerReachability {
    private const val TAG = "GeoTowerServer"
    private const val PROBE_PATH = "api/v2/app/features"

    /** Anti-rafale : deux sondes rapprochées ne servent à rien (sauf `force`). */
    private const val MIN_CHECK_INTERVAL_MS = 30_000L

    private val statusState = mutableStateOf(ServerStatus.UNKNOWN)
    val status: State<ServerStatus> get() = statusState

    private val probeMutex = Mutex()

    @Volatile
    private var lastCheckAt = 0L

    /** Date (epoch ms) de la dernière sonde, 0 si aucune depuis le lancement. */
    val lastCheckedAt: Long get() = lastCheckAt

    /**
     * Client dédié : timeouts courts (on veut un verdict, pas un téléchargement) et surtout AUCUN
     * cache — sinon l'intercepteur hors-ligne de [RetrofitClient] servirait une réponse en cache et
     * un serveur mort passerait pour joignable.
     */
    private val probeClient: OkHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .cache(null)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Sonde le serveur et met [status] à jour. Retourne le verdict obtenu.
     * @param force ignore l'intervalle minimum entre deux sondes.
     */
    suspend fun refresh(force: Boolean = false): ServerStatus {
        if (!force && isThrottled()) return statusState.value

        return probeMutex.withLock {
            if (!force && isThrottled()) return@withLock statusState.value

            val verdict = withContext(Dispatchers.IO) { probeServers() }

            lastCheckAt = System.currentTimeMillis()
            withContext(Dispatchers.Main) { statusState.value = verdict }
            verdict
        }
    }

    /** Repasse à [ServerStatus.UNKNOWN] : appelé quand l'appareil perd le réseau. */
    fun reset() {
        lastCheckAt = 0L
        statusState.value = ServerStatus.UNKNOWN
    }

    /**
     * Sonde le serveur qu'il faut essayer en premier, puis l'autre. Après un moment passé sur le
     * miroir, le principal repasse en tête : c'est ce qui ramène l'app sur `api.geotower.fr` dès
     * qu'il est réparé, sans rien demander à l'utilisateur.
     */
    private fun probeServers(): ServerStatus {
        val first = if (ApiEndpoints.shouldRetryPrimary()) ApiServer.PRIMARY else ApiEndpoints.active()
        if (probe(first)) {
            ApiEndpoints.switchTo(first)
            return ServerStatus.REACHABLE
        }

        val second = ApiEndpoints.failoverTarget(first) ?: return ServerStatus.UNREACHABLE
        if (probe(second)) {
            ApiEndpoints.switchTo(second)
            return ServerStatus.REACHABLE
        }
        return ServerStatus.UNREACHABLE
    }

    /** true si [server] répond (même par une 4xx : il est bien vivant). */
    private fun probe(server: ApiServer): Boolean {
        val request = Request.Builder()
            .url("${server.baseUrl}$PROBE_PATH")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Accept", "application/json")
            .header(ApiEndpoints.PIN_HOST_HEADER, server.host)
            .build()

        return try {
            probeClient.newCall(request).execute().use { response ->
                // 5xx = le serveur (ou son proxy) est en vrac ; le reste = il répond.
                response.code < 500
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Server probe failed on ${server.host}", e)
            false
        }
    }

    private fun isThrottled(): Boolean {
        return statusState.value != ServerStatus.UNKNOWN &&
            System.currentTimeMillis() - lastCheckAt < MIN_CHECK_INTERVAL_MS
    }
}
