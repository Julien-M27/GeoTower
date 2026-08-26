package fr.geotower.data.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.data.api.EnbDatabaseDownloader
import fr.geotower.data.api.RadioDatabaseDownloader
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.db.EnbDatabaseValidator
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.LocalDbProvenance
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recherche et enfile les mises a jour des bases telechargees depuis le serveur.
 *
 * Les fichiers sont volontairement traites un par un : la base mobile peut etre volumineuse, et
 * lancer les trois transferts en parallele gaspillerait bande passante, batterie et stockage
 * temporaire. Chaque worker conserve ses propres notifications de progression et de resultat.
 */
object DatabaseBulkUpdate {
    const val UNIQUE_WORK_NAME = "database_bulk_update"

    enum class Target {
        MOBILE,
        RADIO,
        ENB
    }

    /**
     * Ne retourne que les bases qui peuvent etre telechargees ici et dont la version distante est
     * plus recente. Une base deja en cours de telechargement est laissee tranquille.
     */
    suspend fun findAvailableUpdates(context: Context, workManager: WorkManager): List<Target> =
        withContext(Dispatchers.IO) {
            if (
                !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) ||
                !RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) ||
                !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
            ) {
                return@withContext emptyList()
            }

            buildList {
                // Les deux bases ANFR se reconstruisent localement quand ce mode est impose : les
                // telecharger ici ecraserait cette decision de provenance.
                if (!AppConfig.dbForcedLocal()) {
                    val mobileLocallyBuilt = LocalDbProvenance.readMobile(context).locallyBuilt
                    val radioLocallyBuilt = LocalDbProvenance.readRadio(context).locallyBuilt
                    if (
                        !hasUnfinishedWork(workManager, LocalDbBuildWorker.UNIQUE_WORK_NAME)
                    ) {
                        if (!mobileLocallyBuilt && !hasUnfinishedWork(workManager, DatabaseDownloadWorker.UNIQUE_WORK_NAME)) {
                            val remote = DatabaseDownloader.getLatestDatabaseUpdateInfo()
                            val local = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
                            if (DatabaseDownloader.isRemoteDatabaseUpdateAvailable(context, remote, local)) {
                                add(Target.MOBILE)
                            }
                        }

                        if (!radioLocallyBuilt && !hasUnfinishedWork(workManager, RadioDatabaseDownloadWorker.UNIQUE_WORK_NAME)) {
                            val remote = RadioDatabaseDownloader.getLatestDatabaseVersion()
                            val dbFile = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
                            val local = if (RadioDatabaseValidator.validateDatabaseFile(dbFile).isValid) {
                                RadioDatabaseValidator.getInstalledDatabaseVersion(context)
                            } else {
                                null
                            }
                            if (DatabaseVersionPolicy.isRemoteNewer(remote, local)) add(Target.RADIO)
                        }
                    }
                }

                // Au niveau d'autonomie maximal, cette base partenaire n'est plus servie du tout.
                if (
                    !AppConfig.blockCommunityAndUpdates() &&
                    RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.ENB_DATABASE) &&
                    !hasUnfinishedWork(workManager, EnbDatabaseDownloadWorker.UNIQUE_WORK_NAME)
                ) {
                    val remote = EnbDatabaseDownloader.getLatestDatabaseVersion()
                    val dbFile = context.getDatabasePath(EnbDatabaseValidator.DB_NAME)
                    val local = if (EnbDatabaseValidator.validateDatabaseFile(dbFile).isValid) {
                        EnbDatabaseValidator.getInstalledDatabaseVersion(context)
                    } else {
                        null
                    }
                    // La version eNB contient un digest : une comparaison exacte est indispensable
                    // pour detecter le changement d'une source plus ancienne que les autres.
                    if (!remote.isNullOrBlank() && remote != local) add(Target.ENB)
                }
            }
        }

    /**
     * Construit une seule chaine WorkManager. `continueAfterFailure` permet aux bases suivantes de
     * partir meme si une precedente a echoue apres ses tentatives : chaque erreur reste notifiee.
     */
    fun enqueue(workManager: WorkManager, targets: List<Target>) {
        val requests = targets.distinct().mapNotNull(::requestFor)
        if (requests.isEmpty()) return

        var continuation = workManager.beginUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            requests.first()
        )
        requests.drop(1).forEach { request ->
            continuation = continuation.then(request)
        }
        continuation.enqueue()
    }

    private fun requestFor(target: Target): OneTimeWorkRequest? = when (target) {
        Target.MOBILE -> DatabaseDownloadWorker.buildRequest(continueAfterFailure = true)
        Target.RADIO -> RadioDatabaseDownloadWorker.buildRequest(continueAfterFailure = true)
        Target.ENB -> EnbDatabaseDownloadWorker.buildRequest(continueAfterFailure = true)
    }

    private fun hasUnfinishedWork(workManager: WorkManager, uniqueWorkName: String): Boolean =
        runCatching {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
                .any { workInfo -> !workInfo.state.isFinished }
        }.getOrDefault(false)
}
