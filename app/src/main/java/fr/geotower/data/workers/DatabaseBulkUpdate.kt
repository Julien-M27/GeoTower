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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private data class TargetCheckResult(
        val targetAction: TargetAction? = null,
        val isComplete: Boolean = true,
        val hasMissingDatabase: Boolean = false,
        val hasDatabaseUpdate: Boolean = false
    )

    /**
     * Ne retourne que les bases qui peuvent etre telechargees ici et dont la version distante est
     * plus recente. Une base deja en cours de telechargement est laissee tranquille.
     */
    suspend fun checkAvailableUpdates(
        context: Context,
        workManager: WorkManager,
        onProgress: suspend (AvailableUpdatesResult) -> Unit = {}
    ): AvailableUpdatesResult =
        withContext(Dispatchers.IO) {
            if (
                !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) ||
                !RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_DATABASE_DOWNLOAD) ||
                !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
            ) {
                return@withContext AvailableUpdatesResult(emptyList(), isComplete = false)
            }

            val checks = buildList<suspend () -> TargetCheckResult> {
                // Les deux bases ANFR se reconstruisent localement quand ce mode est impose : les
                // telecharger ici ecraserait cette decision de provenance.
                if (!AppConfig.dbForcedLocal()) {
                    add { checkMobile(context, workManager) }
                    add { checkRadio(context, workManager) }
                }

                // Au niveau d'autonomie maximal, cette base partenaire n'est plus servie du tout.
                if (
                    !AppConfig.blockCommunityAndUpdates() &&
                    RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.ENB_DATABASE)
                ) {
                    add { checkEnb(context, workManager) }
                }
            }

            val totalChecks = checks.size
            val results = runChecksConcurrently(checks) { _, completedResults ->
                onProgress(completedResults.toAvailableUpdatesResult(totalChecks, isComplete = false))
            }
            results.toAvailableUpdatesResult(totalChecks, isComplete = true)
        }

    internal suspend fun <T> runChecksConcurrently(
        checks: List<suspend () -> T>,
        onResult: suspend (result: T, completedResults: List<T>) -> Unit = { _, _ -> }
    ): List<T> =
        coroutineScope {
            val completedResults = mutableListOf<T>()
            checks.map { check ->
                async(Dispatchers.IO) {
                    val result = check()
                    val snapshot = synchronized(completedResults) {
                        completedResults += result
                        completedResults.toList()
                    }
                    onResult(result, snapshot)
                    result
                }
            }.awaitAll()
        }

    private fun List<TargetCheckResult>.toAvailableUpdatesResult(
        totalChecks: Int,
        isComplete: Boolean
    ): AvailableUpdatesResult = AvailableUpdatesResult(
        targets = mapNotNull { it.targetAction?.target },
        isComplete = isComplete && size == totalChecks && all { it.isComplete },
        hasMissingDatabases = any { it.hasMissingDatabase },
        hasDatabaseUpdates = any { it.hasDatabaseUpdate },
        targetActions = mapNotNull { it.targetAction }
    )

    private suspend fun checkMobile(context: Context, workManager: WorkManager): TargetCheckResult {
        if (
            LocalDbProvenance.readMobile(context).locallyBuilt ||
            hasUnfinishedWork(workManager, LocalDbBuildWorker.UNIQUE_WORK_NAME) ||
            hasUnfinishedWork(workManager, DatabaseDownloadWorker.UNIQUE_WORK_NAME)
        ) {
            return TargetCheckResult()
        }

        val remote = DatabaseDownloader.getLatestDatabaseUpdateInfo()
        val local = GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
        val isMissing = local == null
        if (remote == null) {
            return TargetCheckResult(isComplete = false, hasMissingDatabase = isMissing)
        }
        if (!DatabaseDownloader.isRemoteDatabaseUpdateAvailable(context, remote, local)) {
            return TargetCheckResult(hasMissingDatabase = isMissing)
        }

        return TargetCheckResult(
            targetAction = TargetAction(
                Target.MOBILE,
                if (isMissing) Action.DOWNLOAD else Action.UPDATE
            ),
            hasMissingDatabase = isMissing,
            hasDatabaseUpdate = !isMissing
        )
    }

    private suspend fun checkRadio(context: Context, workManager: WorkManager): TargetCheckResult {
        if (
            LocalDbProvenance.readRadio(context).locallyBuilt ||
            hasUnfinishedWork(workManager, LocalDbBuildWorker.UNIQUE_WORK_NAME) ||
            hasUnfinishedWork(workManager, RadioDatabaseDownloadWorker.UNIQUE_WORK_NAME)
        ) {
            return TargetCheckResult()
        }

        val remote = RadioDatabaseDownloader.getLatestDatabaseVersion()
        val dbFile = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
        val local = if (RadioDatabaseValidator.validateDatabaseFile(dbFile).isValid) {
            RadioDatabaseValidator.getInstalledDatabaseVersion(context)
        } else {
            null
        }
        val isMissing = local == null
        if (remote == null) {
            return TargetCheckResult(isComplete = false, hasMissingDatabase = isMissing)
        }
        if (!DatabaseVersionPolicy.isRemoteNewer(remote, local)) {
            return TargetCheckResult(hasMissingDatabase = isMissing)
        }

        return TargetCheckResult(
            targetAction = TargetAction(
                Target.RADIO,
                if (isMissing) Action.DOWNLOAD else Action.UPDATE
            ),
            hasMissingDatabase = isMissing,
            hasDatabaseUpdate = !isMissing
        )
    }

    private suspend fun checkEnb(context: Context, workManager: WorkManager): TargetCheckResult {
        if (hasUnfinishedWork(workManager, EnbDatabaseDownloadWorker.UNIQUE_WORK_NAME)) {
            return TargetCheckResult()
        }

        val remote = EnbDatabaseDownloader.getLatestDatabaseVersion()
        val dbFile = context.getDatabasePath(EnbDatabaseValidator.DB_NAME)
        val local = if (EnbDatabaseValidator.validateDatabaseFile(dbFile).isValid) {
            EnbDatabaseValidator.getInstalledDatabaseVersion(context)
        } else {
            null
        }
        val isMissing = local == null
        // La version eNB contient un digest : une comparaison exacte est indispensable
        // pour detecter le changement d'une source plus ancienne que les autres.
        if (remote.isNullOrBlank()) {
            return TargetCheckResult(isComplete = false, hasMissingDatabase = isMissing)
        }
        if (remote == local) {
            return TargetCheckResult(hasMissingDatabase = isMissing)
        }

        return TargetCheckResult(
            targetAction = TargetAction(
                Target.ENB,
                if (isMissing) Action.DOWNLOAD else Action.UPDATE
            ),
            hasMissingDatabase = isMissing,
            hasDatabaseUpdate = !isMissing
        )
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
