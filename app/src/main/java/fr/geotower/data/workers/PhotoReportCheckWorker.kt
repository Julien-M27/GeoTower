package fr.geotower.data.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.community.PhotoReportHistoryEntry
import fr.geotower.data.community.PhotoReportHistoryStore
import fr.geotower.data.community.SignalQuestPhotoReporter
import fr.geotower.utils.AppLogger

/**
 * Sonde périodiquement les photos signalées : SignalQuest n'expose que les photos **approuvées**,
 * donc une photo qui n'apparaît plus dans la liste de son site a été retirée.
 *
 * C'est la seule issue observable. Une photo qui reste en ligne ne dit rien : signalement encore en
 * file d'attente ou signalement refusé, l'API ne permet pas de trancher, et on ne notifie donc rien.
 */
class PhotoReportCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!SignalQuestPhotoReporter.canReport()) return Result.success()

        val pending = PhotoReportHistoryStore.pendingFollowUp(context)
        if (pending.isEmpty()) return Result.success()

        var networkFailed = false
        pending.groupBy { it.siteId }.forEach { (siteId, entries) ->
            when (val outcome = fetchPublishedPhotoIds(siteId)) {
                is SitePhotos.Unavailable -> networkFailed = true
                is SitePhotos.Inconclusive -> entries.forEach {
                    PhotoReportHistoryStore.recordCheck(context, it.id)
                }
                is SitePhotos.Ids -> entries.forEach { entry ->
                    if (entry.photoId in outcome.value) {
                        PhotoReportHistoryStore.recordCheck(context, entry.id)
                    } else {
                        onPhotoRemoved(context, entry)
                    }
                }
            }
        }

        // Un échec réseau ne doit JAMAIS passer pour un retrait : on repasse plus tard.
        return if (networkFailed) Result.retry() else Result.success()
    }

    private fun onPhotoRemoved(context: Context, entry: PhotoReportHistoryEntry) {
        PhotoReportHistoryStore.markRemoved(context, entry.id)
        PhotoReportNotifier.notifyRemoved(context, entry)
    }

    private sealed interface SitePhotos {
        /** Le site a répondu : voici les identifiants encore publiés. */
        data class Ids(val value: Set<String>) : SitePhotos

        /** Réponse tronquée ou sans identifiants : impossible de conclure à une absence. */
        data object Inconclusive : SitePhotos

        data object Unavailable : SitePhotos
    }

    private suspend fun fetchPublishedPhotoIds(siteId: String): SitePhotos {
        val response = runCatching {
            SignalQuestClient.api.getSitePhotos(siteId = siteId, limit = PAGE_LIMIT)
        }.getOrElse { error ->
            AppLogger.w(TAG, "Suivi de signalement : site $siteId injoignable", error)
            return SitePhotos.Unavailable
        }

        if (!response.isSuccessful) return SitePhotos.Unavailable
        val photos = response.body()?.data ?: return SitePhotos.Unavailable

        // Liste pleine = il y a probablement une page suivante ; conclure à une absence ici
        // annoncerait un retrait qui n'a pas eu lieu.
        if (photos.size >= PAGE_LIMIT) return SitePhotos.Inconclusive

        val publishedIds = photos.mapNotNull { it.id?.takeIf { id -> id.isNotBlank() } }.toSet()
        // Des photos sans identifiant : on ne peut pas dire qu'une absence est un retrait.
        if (photos.isNotEmpty() && publishedIds.isEmpty()) return SitePhotos.Inconclusive

        return SitePhotos.Ids(publishedIds)
    }

    companion object {
        private const val TAG = "PhotoReportCheck"

        /** Plafond imposé par le proxy (`limit` borné à 100). */
        private const val PAGE_LIMIT = 100
    }
}
