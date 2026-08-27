package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.data.db.GeoTowerDatabaseValidator
import android.content.pm.ServiceInfo
import fr.geotower.utils.AppLogger
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class DatabaseDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "db_download_channel"
    private val notificationId = DownloadNotificationCenter.DB_DOWNLOAD_PROGRESS_NOTIFICATION_ID
    private var isUpdatingExistingDatabase = false

    override suspend fun doWork(): Result {
        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) ||
            !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
        ) {
            return Result.success()
        }
        createChannel()
        // Chrono de telechargement (live pendant, duree finale apres) affiche par DatabaseDownloadCard.
        DbOperationTimings.markStart(context, DbOperationTimings.MOBILE_DOWNLOAD)
        return try {
            isUpdatingExistingDatabase = GeoTowerDatabaseValidator
                .getInstalledDatabaseStatus(context)
                .state == GeoTowerDatabaseValidator.LocalDatabaseState.VALID

            // 1. Démarrer en premier plan
            setForeground(createForegroundInfo(0))
            awaitIfPaused(0)

            // 2. Lancer le téléchargement
            val success = DatabaseDownloader.downloadUpdate(context) { progress ->
                awaitIfPaused(progress)
                setProgress(workDataOf(KEY_PROGRESS to progress, KEY_PAUSED to false))
                notifySafely(notificationId, createNotification(progress, paused = false))
            }

            // 3. Fin du téléchargement
            if (success) {
                OperationPauseStore.clear(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)
                DbOperationTimings.finish(context, DbOperationTimings.MOBILE_DOWNLOAD)
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_PAUSED to false))
                showSuccessNotification()
                Result.success()
            } else {
                OperationPauseStore.clear(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)
                DbOperationTimings.clearStart(context, DbOperationTimings.MOBILE_DOWNLOAD)
                showErrorNotification()
                failureResult()
            }
        } catch (e: CancellationException) {
            DbOperationTimings.clearStart(context, DbOperationTimings.MOBILE_DOWNLOAD)
            cancelSafely(notificationId)
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Database download worker failed", e)
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result {
        OperationPauseStore.clear(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)
        cancelSafely(notificationId)
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            DbOperationTimings.clearStart(context, DbOperationTimings.MOBILE_DOWNLOAD)
            showErrorNotification()
            failureResult()
        }
    }

    /** Dans une mise a jour groupee, une erreur est deja notifiee et ne doit pas bloquer la suite. */
    private fun failureResult(): Result =
        if (inputData.getBoolean(KEY_CONTINUE_AFTER_FAILURE, false)) Result.success() else Result.failure()

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        // Les notifications de fin obéissent à l'interrupteur maître des notifications ; celle de
        // progression est celle du service de premier plan, imposée par Android tant que le
        // téléchargement tourne, et elle disparaît avec lui.
        if (id != notificationId && !AppNotifications.canPost(context)) return
        runCatching {
            notificationManager.notify(id, notification)
        }.onFailure { error ->
            AppLogger.w(TAG, "Database download notification update failed", error)
        }
    }

    private fun cancelSafely(id: Int) {
        runCatching {
            notificationManager.cancel(id)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.notification_database_channel_name)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = createNotification(progress)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotification(progress: Int, paused: Boolean = false): android.app.Notification {
        val databaseName = context.getString(R.string.notification_history_type_db_mobile)
        val title = context.getString(
            if (isUpdatingExistingDatabase) R.string.notification_database_download_title
            else R.string.notification_database_first_download_title,
            databaseName
        )
        val content = if (paused) {
            context.getString(R.string.appstrings_operation_paused)
        } else {
            context.getString(R.string.notification_database_download_progress, progress)
        }

        // ✅ AJOUT : Intent pour ouvrir les paramètres DB au clic
        val pendingIntent = settingsPendingIntent(0, showSuccessPopup = false)
        val cancelLabel = context.getString(R.string.appstrings_download_cancel)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val actionIconRes = NotificationIconResources.smallIconRes(context)

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent) // ✅ On ajoute le clic !
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(actionIconRes, cancelLabel, cancelIntent)
        NotificationIconResources.applyTo(builder, context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = android.app.Notification.ProgressStyle()
                .setProgress(progress)
                .setProgressSegments(listOf(android.app.Notification.ProgressStyle.Segment(100)))

            val nativeBuilder = android.app.Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent) // ✅ On ajoute le clic ici aussi !
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_PROGRESS)
                .setStyle(progressStyle)
                .addAction(
                    android.app.Notification.Action.Builder(
                        Icon.createWithResource(context, actionIconRes),
                        cancelLabel,
                        cancelIntent
                    ).build()
                )
            NotificationIconResources.applyTo(nativeBuilder, context)

            // ✅ Utilisation de la réflexion pour compatibilité A16
            runCatching {
                android.app.Notification.Builder::class.java
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(nativeBuilder, "$progress%")
            }
            runCatching {
                android.app.Notification.Builder::class.java
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(nativeBuilder, true)
            }

            return nativeBuilder.build().apply {
                extras.putBoolean("android.requestPromotedOngoing", true)
                extras.putString("android.shortCriticalText", "$progress%")
            }
        }

        return builder.build()
    }

    private suspend fun awaitIfPaused(progress: Int) {
        if (!OperationPauseStore.isPaused(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)) return
        setProgress(workDataOf(KEY_PROGRESS to progress, KEY_PAUSED to true))
        notifySafely(notificationId, createNotification(progress, paused = true))
        OperationPauseStore.awaitUntilResumed(context, OperationPauseStore.MOBILE_DB_DOWNLOAD)
        setProgress(workDataOf(KEY_PROGRESS to progress, KEY_PAUSED to false))
        notifySafely(notificationId, createNotification(progress, paused = false))
    }

    private fun showSuccessNotification() {
        recordHistory(NotificationHistoryStore.STATUS_SUCCESS)
        val pendingIntent = settingsPendingIntent(1, showSuccessPopup = true)

        val databaseName = context.getString(R.string.notification_history_type_db_mobile)
        val title = context.getString(R.string.notification_database_downloaded_title, databaseName)
        val content = context.getString(R.string.notification_database_downloaded_content, databaseName)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        notifySafely(DownloadNotificationCenter.DB_DOWNLOAD_RESULT_NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification() {
        recordHistory(NotificationHistoryStore.STATUS_ERROR)
        val title = context.getString(
            R.string.notification_database_download_failed_title,
            context.getString(R.string.notification_history_type_db_mobile)
        )
        val content = context.getString(R.string.notification_database_download_failed_content)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()
        notifySafely(DownloadNotificationCenter.DB_DOWNLOAD_RESULT_NOTIFICATION_ID, notification)
    }

    /**
     * Le journal des notifications garde la trace de l'événement même quand la notification ne part
     * pas (notifications coupées, permission refusée) : c'est justement là qu'il sert. L'échec vise
     * la même carte de réglages que la réussite — la notification d'échec n'est pas cliquable, mais
     * depuis le journal c'est de là qu'on relance le téléchargement.
     */
    private fun recordHistory(status: String) {
        NotificationHistoryStore.record(
            context = context,
            type = NotificationHistoryStore.TYPE_DB_MOBILE,
            status = status,
            target = "geotower://settings?section=db_mobile",
            posted = AppNotifications.canPost(context)
        )
    }

    /**
     * Cible la carte « base de données mobile » précisément, pas seulement le haut de la section
     * « Base de données » (les cartes radio / eNB / génération locale ont leurs propres ancres).
     * Le `requestCode` distingue la notif de progression de celle de fin : sans ça les deux
     * PendingIntent se confondraient (les extras ne comptent pas dans `filterEquals`) et le clic
     * sur la progression ouvrirait la popup de succès.
     */
    private fun settingsPendingIntent(requestCode: Int, showSuccessPopup: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=db_mobile")
            if (showSuccessPopup) putExtra("SHOW_DB_SUCCESS_POPUP", true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "db_download"
        const val WORK_TAG = "database_download_mobile"
        const val KEY_PROGRESS = "progress"
        const val KEY_PAUSED = "paused"
        private const val KEY_CONTINUE_AFTER_FAILURE = "continue_after_failure"

        private const val TAG = "GeoTowerDb"
        private const val MAX_RETRY_ATTEMPTS = 3
        fun buildRequest(
            continueAfterFailure: Boolean = false,
            bulkActionTag: String? = null
        ) = OneTimeWorkRequestBuilder<DatabaseDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_CONTINUE_AFTER_FAILURE to continueAfterFailure))
            .addTag(WORK_TAG)
            .apply { bulkActionTag?.let(::addTag) }
            .build()

        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest()
            )
        }
    }
}
