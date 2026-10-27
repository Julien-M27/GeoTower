package fr.geotower.data.api

import android.content.Context
import com.google.gson.Gson
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.models.LiveDbStatusDto
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Ce que la base EN LIGNE annonce servir : les mêmes repères que la carte « Versions » de la base
 * installée, pour que les deux se comparent d'un coup d'œil.
 */
data class LiveDatabaseDataset(
    /** Producteur de la donnée, tel que déclaré par le serveur (« ANFR »). */
    val source: String?,
    /** Version du fichier téléchargeable dont la base live est tirée. */
    val offlineDbVersion: String?,
    /** Date de l'extraction hebdomadaire ANFR. */
    val dateMajAnfr: String?,
    /** Nom de l'extraction mensuelle ANFR. */
    val zipVersion: String?,
    /** Version du trimestriel ARCEP, `null` tant que la base live n'a pas été régénérée avec. */
    val quarterlyVersion: String?,
    /** Date de génération de la base live, en ISO 8601. */
    val generatedAt: String?,
    /** Nombre de stations publiées. */
    val stationCount: Int?,
    /**
     * Serveur qui a réellement répondu. Principal et miroir construisent leur base chacun de leur
     * côté : dire « en ligne » sans dire *où* laisserait deux dates possibles pour une même ligne.
     */
    val host: String
)

/**
 * Base EN LIGNE : est-elle utilisée, et sur quelles données tourne-t-elle ?
 *
 * L'app bascule sur l'API live quand aucune base valide n'est installée (cf. le repli de
 * `AnfrRepository`) — [isInUse] est la définition unique de cet état, pour que l'affichage ne
 * puisse pas annoncer une source que les écrans n'utilisent pas.
 */
object LiveDatabaseStatus {
    private const val TAG = "GeoTowerDb"

    /**
     * Le jeu de données ne change qu'à la reconstruction de la base côté serveur (quotidienne au
     * mieux) : inutile de rappeler `/status` à chaque ouverture de la page « À propos ».
     */
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private val serverCache = ConcurrentHashMap<ApiServer, Pair<LiveDatabaseDataset, Long>>()

    private val gson = Gson()

    private val statusClient: OkHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .cache(null)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * true quand les écrans lisent la base en ligne au lieu d'une base installée.
     *
     * L'état validé au démarrage sert de réponse quand il est connu ; sinon on retombe sur le
     * fichier, ce qui touche le disque (à appeler hors du thread principal).
     */
    fun isInUse(context: Context): Boolean {
        if (!canQueryLiveDatabaseMetadata()) return false

        val knownState = AppConfig.localDatabaseState.value
        if (knownState != null) {
            return knownState != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
        }

        return GeoTowerDatabaseValidator
            .getInstalledDatabaseFileStatus(context)
            .state != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    }

    /**
     * Métadonnées de la base en ligne pour un serveur précis, ou `null` si l'API live n'est pas
     * disponible / le serveur n'a pas répondu.
     */
    suspend fun datasetForServer(server: ApiServer, forceRefresh: Boolean = false): LiveDatabaseDataset? =
        withContext(Dispatchers.IO) {
            if (!canQueryLiveDatabaseMetadata()) return@withContext null

            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                val cachedEntry = serverCache[server]
                if (cachedEntry != null && now - cachedEntry.second < CACHE_TTL_MS) {
                    return@withContext cachedEntry.first
                }
            }

            try {
                val url = "https://${server.host}/api/v2/live/fr/status"
                val request = Request.Builder()
                    .url(url)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header(ApiEndpoints.PIN_HOST_HEADER, server.host)
                    .header("Accept-Encoding", "identity")
                    .build()

                statusClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.w(TAG, "Live database status for ${server.host} responded ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string() ?: return@withContext null
                    val dto = gson.fromJson(body, LiveDbStatusDto::class.java) ?: return@withContext null
                    val dataset = LiveDatabaseDataset(
                        source = dto.source,
                        offlineDbVersion = dto.offlineDbVersion,
                        dateMajAnfr = dto.dateMajAnfr,
                        zipVersion = dto.zipVersion,
                        quarterlyVersion = dto.quarterlyVersion,
                        generatedAt = dto.generatedAt,
                        stationCount = dto.rowCount,
                        host = server.host
                    )
                    serverCache[server] = dataset to System.currentTimeMillis()
                    dataset
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Live database status request for ${server.host} failed", e)
                null
            }
        }

    /**
     * Métadonnées de la base en ligne pour le serveur actif, ou `null` si l'API live n'est pas
     * disponible / le serveur n'a pas répondu.
     *
     * [forceRefresh] court-circuite le cache, pour les actualisations demandées par l'utilisateur.
     */
    suspend fun dataset(forceRefresh: Boolean = false): LiveDatabaseDataset? {
        val activeServer = ApiEndpoints.active()
        return datasetForServer(activeServer, forceRefresh)
    }

    private fun canQueryLiveDatabaseMetadata(): Boolean =
        canQueryLiveDatabaseMetadata(
            liveApiEnabled = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LIVE_API_FR),
            communityAndUpdatesBlocked = AppConfig.blockCommunityAndUpdates()
        )
}

internal fun canQueryLiveDatabaseMetadata(
    liveApiEnabled: Boolean,
    communityAndUpdatesBlocked: Boolean
): Boolean = liveApiEnabled && !communityAndUpdatesBlocked
