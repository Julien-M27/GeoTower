package fr.geotower.data.community

import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.api.SignalQuestPhotoReportReasons
import fr.geotower.data.api.SqPhotoReportRequest
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Signalement d'une photo hébergée par SignalQuest, via le proxy GeoTower — la clé API reste
 * côté serveur, l'application ne la voit jamais.
 *
 * Un signalement **ne retire pas** la photo : SignalQuest le range dans sa file de modération et
 * un humain tranche. Aucun écran ne doit donc annoncer une suppression, seulement une transmission.
 *
 * Ne vaut que pour les photos SignalQuest : celles des autres sources (CellularFR) n'ont pas
 * d'identifiant exploitable ici, l'appelant doit filtrer en amont.
 */
object SignalQuestPhotoReporter {

    private const val TAG = "PhotoReport"

    sealed interface Result {
        /** Transmis. La photo reste en ligne tant que la modération n'a pas tranché. */
        data object Sent : Result

        /** Trop de signalements sur la période : la clé API est partagée par tous les utilisateurs. */
        data object RateLimited : Result

        /** La photo n'existe plus côté SignalQuest. */
        data object NotFound : Result

        /** Fonction coupée (kill-switch, autonomie maximale) ou service indisponible. */
        data object Unavailable : Result

        data object Failed : Result
    }

    /**
     * Le signalement est-il proposable ? Coupé par le kill-switch distant, et par le cran maximal
     * de « Provenance des données » qui détache justement l'application de SignalQuest.
     */
    fun canReport(): Boolean =
        !AppConfig.blockCommunityAndUpdates() &&
            RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_PHOTO_REPORT)

    suspend fun report(photoId: String, reason: String, description: String?): Result {
        if (!canReport()) return Result.Unavailable
        if (photoId.isBlank() || reason !in SignalQuestPhotoReportReasons.ordered) return Result.Failed

        val trimmedDescription = description
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(SignalQuestPhotoReportReasons.MAX_DESCRIPTION_LENGTH)

        return withContext(Dispatchers.IO) {
            runCatching {
                SignalQuestClient.api.reportSitePhoto(
                    photoId = photoId,
                    body = SqPhotoReportRequest(reason = reason, description = trimmedDescription)
                )
            }.fold(
                onSuccess = { response ->
                    when {
                        response.isSuccessful -> Result.Sent
                        response.code() == 429 -> Result.RateLimited
                        response.code() == 404 -> Result.NotFound
                        response.code() == 503 -> Result.Unavailable
                        else -> {
                            AppLogger.w(TAG, "Signalement refuse (HTTP ${response.code()})")
                            Result.Failed
                        }
                    }
                },
                onFailure = { error ->
                    AppLogger.w(TAG, "Signalement impossible", error)
                    Result.Failed
                }
            )
        }
    }
}
