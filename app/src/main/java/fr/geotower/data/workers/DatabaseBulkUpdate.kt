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

    enum class Action {
        DOWNLOAD,
        UPDATE
    }

    data class TargetAction(
        val target: Target,
        val action: Action
    )

    data class AvailableUpdatesResult(
        val targets: List<Target>,
        val isComplete: Boolean,
        val hasMissingDatabases: Boolean = false,
        val hasDatabaseUpdates: Boolean = false,
        val targetActions: List<TargetAction> = emptyList()
    )

    /**
     * Ne retourne que les bases qui peuvent etre telechargees ici et dont la version distante est
     * plus recente. Une base deja en cours de telechargement est laissee tranquille.
     */
    suspend fun checkAvailableUpdates(context: Context, workManager: WorkManager): AvailableUpdatesResult =
        withContext(Dispatchers.IO) {
            if (
                !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) ||
                !RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) ||
                !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
            ) {
                return@withContext AvailableUpdatesResult(emptyList(), isComplete = false)
            }

            var isComplete = true
            var hasMissingDatabases = false
            var hasDatabaseUpdates = false
            val targetActions = mutableListOf<TargetAction>()
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
                            if (local == null) {
                                hasMissingDatabases = true
                            }
                            if (remote == null) {
                                isComplete = false
                            } else if (DatabaseDownloader.isRemoteDatabaseUpdateAvailable(context, remote, local)) {
                                if (local != null) {
                                    hasDatabaseUpdates = true
                                }
                                add(Target.MOBILE)
                                targetActions += TargetAction(
                                    Target.MOBILE,
                                    if (local == null) Action.DOWNLOAD else Action.UPDATE
                                )
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
                            if (local == null) {
                                hasMissingDatabases = true
                            }
                            if (remote == null) {
                                isComplete = false
                            } else if (DatabaseVersionPolicy.isRemoteNewer(remote, local)) {
                                if (local != null) {
                                    hasDatabaseUpdates = true
                                }
                                add(Target.RADIO)
                                targetActions += TargetAction(
                                    Target.RADIO,
                                    if (local == null) Action.DOWNLOAD else Action.UPDATE
                                )
                            }
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
                    if (local == null) {
                        hasMissingDatabases = true
                    }
                    // La version eNB contient un digest : une comparaison exacte est indispensable
                    // pour detecter le changement d'une source plus ancienne que les autres.
                    if (remote.isNullOrBlank()) {
                        isComplete = false
                    } else if (remote != local) {
                        if (local != null) {
                            hasDatabaseUpdates = true
                        }
                        add(Target.ENB)
                        targetActions += TargetAction(
                            Target.ENB,
                            if (local == null) Action.DOWNLOAD else Action.UPDATE
                        )
                    }
                }
            }.let { targets ->
                AvailableUpdatesResult(
                    targets = targets,
                    isComplete = isComplete,
                    hasMissingDatabases = hasMissingDatabases,
                    hasDatabaseUpdates = hasDatabaseUpdates,
                    targetActions = targetActions
                )
            }
        }

    suspend fun findAvailableUpdates(context: Context, workManager: WorkManager): List<Target> =
        checkAvailableUpdates(context, workManager).targets

    /**
     * Construit une seule chaine WorkManager. `continueAfterFailure` permet aux bases suivantes de
     * partir meme si une precedente a echoue apres ses tentatives : chaque erreur reste notifiee.
     */
    fun enqueue(workManager: WorkManager, targets: List<Target>) {
        enqueueDetailed(
            workManager,
            targets.distinct().map { target -> TargetAction(target, Action.UPDATE) }
        )
    }

    fun enqueueDetailed(workManager: WorkManager, targetActions: List<TargetAction>) {
        val requests = targetActions.distinctBy { it.target }.mapNotNull(::requestFor)
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

    private fun requestFor(targetAction: TargetAction): OneTimeWorkRequest? = when (targetAction.target) {
        Target.MOBILE -> DatabaseDownloadWorker.buildRequest(
            continueAfterFailure = true,
            bulkActionTag = actionTag(targetAction.action)
        )
        Target.RADIO -> RadioDatabaseDownloadWorker.buildRequest(
            continueAfterFailure = true,
            bulkActionTag = actionTag(targetAction.action)
        )
        Target.ENB -> EnbDatabaseDownloadWorker.buildRequest(
            continueAfterFailure = true,
            bulkActionTag = actionTag(targetAction.action)
        )
    }

    fun actionTag(action: Action): String = when (action) {
        Action.DOWNLOAD -> "database_bulk_action_download"
        Action.UPDATE -> "database_bulk_action_update"
    }

    private fun hasUnfinishedWork(workManager: WorkManager, uniqueWorkName: String): Boolean =
        runCatching {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
                .any { workInfo -> !workInfo.state.isFinished }
        }.getOrDefault(false)
}
