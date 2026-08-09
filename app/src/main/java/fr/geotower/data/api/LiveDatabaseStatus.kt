package fr.geotower.data.api

import android.content.Context
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException

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

    @Volatile
    private var cached: LiveDatabaseDataset? = null

    @Volatile
    private var cachedAtMillis = 0L

    /**
     * true quand les écrans lisent la base en ligne au lieu d'une base installée.
     *
     * L'état validé au démarrage sert de réponse quand il est connu ; sinon on retombe sur le
     * fichier, ce qui touche le disque (à appeler hors du thread principal).
     */
    fun isInUse(context: Context): Boolean {
        if (AppConfig.blockCommunityAndUpdates()) return false
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.LIVE_API_FR)) return false

        val knownState = AppConfig.localDatabaseState.value
        if (knownState != null) {
            return knownState != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
        }

        return GeoTowerDatabaseValidator
            .getInstalledDatabaseFileStatus(context)
            .state != GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    }

    /**
     * Métadonnées de la base en ligne, ou `null` si elle n'est pas utilisée / le serveur n'a pas
     * répondu. Un échec n'est jamais mis en cache : la page se rattrape à la visite suivante.
     *
     * [forceRefresh] court-circuite le cache, pour les actualisations demandées par l'utilisateur :
     * un bouton qui rendrait la même réponse pendant dix minutes mentirait sur ce qu'il fait.
     */
    suspend fun dataset(context: Context, forceRefresh: Boolean = false): LiveDatabaseDataset? {
        if (!isInUse(context)) return null

        val cachedDataset = cached
        if (!forceRefresh && cachedDataset != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL_MS) {
            return cachedDataset
        }

        return try {
            val response = LiveSitesClient.api.getStatus()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                AppLogger.w(TAG, "Live database status responded ${response.code()}")
                return null
            }

            val dataset = LiveDatabaseDataset(
                source = body.source,
                offlineDbVersion = body.offlineDbVersion,
                dateMajAnfr = body.dateMajAnfr,
                zipVersion = body.zipVersion,
                quarterlyVersion = body.quarterlyVersion,
                generatedAt = body.generatedAt,
                stationCount = body.rowCount,
                host = response.raw().request.url.host
            )
            cached = dataset
            cachedAtMillis = System.currentTimeMillis()
            dataset
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Live database status request failed", e)
            null
        }
    }
}
