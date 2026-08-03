package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sert les pannes générées localement, avec cache paresseux + TTL. Régénère à la demande quand le
 * cache dépasse la fréquence choisie, en sérialisant les générations (mutex) et en retombant sur le
 * dernier cache si la génération échoue.
 *
 * Les dépendances sont injectées (cache, fréquence, callback, génération, horloge) pour rester
 * testable hors device.
 */
class LocalOutageProvider(
    private val cache: OutageLocalCache,
    private val frequencyMillis: () -> Long,
    private val markGenerated: (generatedAtMillis: Long, sites: List<SiteHsEntity>) -> Unit,
    private val generate: suspend (sourceLastUpdate: String?, onProgress: OutageProgressCallback) -> LocalOutageGenerator.GenerationResult,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    /** Sert le cache s'il est encore frais, sinon régénère. */
    suspend fun getSites(): List<SiteHsEntity> {
        val fresh = cache.load()?.takeIf { clock() - it.generatedAtMillis < frequencyMillis() }
        return fresh?.sites ?: regenerate(force = false)
    }

    /**
     * Régénère les pannes. Avec [force] = false, un cache encore frais court-circuite (utile quand
     * plusieurs appels concurrents arrivent après péremption). [force] = true = bouton « générer maintenant ».
     *
     * L'échec est traité différemment selon l'origine : un déclenchement EXPLICITE ([force] = true,
     * bouton ou planification) **propage** l'exception, parce que l'appelant doit pouvoir dire à
     * l'utilisateur pourquoi ça n'a pas marché ; le chemin paresseux, lui, garde silencieusement le
     * dernier cache pour ne pas casser la carte ou une fiche. Sans cette distinction, une source
     * injoignable ou une base ANFR illisible donnaient exactement la même chose qu'un succès à zéro
     * panne : un bouton qui « ne fait rien ».
     */
    suspend fun regenerate(
        force: Boolean,
        onProgress: OutageProgressCallback = NoOutageProgress,
    ): List<SiteHsEntity> = mutex.withLock {
        val existing = cache.load()
        if (!force && existing != null && clock() - existing.generatedAtMillis < frequencyMillis()) {
            return@withLock existing.sites
        }
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(clock()))
            val result = generate(today, onProgress)
            onProgress(OutageGenerationStep.FINALIZE, GEOCODE_PERCENT_END + 5, null)
            val generatedAt = clock()
            cache.save(CachedOutages(generatedAt, today, result.sites))
            markGenerated(generatedAt, result.sites)
            onProgress(OutageGenerationStep.DONE, 100, null)
            result.sites
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Échec (ex. aucun opérateur joignable) : on garde le dernier cache plutôt que rien.
            if (force) throw e
            existing?.sites ?: emptyList()
        }
    }
}
