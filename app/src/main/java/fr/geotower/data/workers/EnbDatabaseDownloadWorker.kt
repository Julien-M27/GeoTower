package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.EnbDatabaseDownloader
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.data.db.EnbDatabaseValidator
import fr.geotower.utils.AppLogger
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Telechargement de la base des identifiants eNB/gNB, calque sur [RadioDatabaseDownloadWorker]. */
class EnbDatabaseDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "db_download_channel"
    private val notificationId = DownloadNotificationCenter.ENB_DB_DOWNLOAD_PROGRESS_NOTIFICATION_ID
    private var isUpdatingExistingDatabase = false

    override suspend fun doWork(): Result {
        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD) ||
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.ENB_DATABASE) ||
            !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_DOWNLOAD)
        ) {
            return Result.success()
        }

        createChannel()
        // Chrono de telechargement (live pendant, duree finale apres) affiche par EnbDatabaseDownloadCard.
        DbOperationTimings.markStart(context, DbOperationTimings.ENB_DOWNLOAD)
        return try {
            isUpdatingExistingDatabase = EnbDatabaseValidator.validateDatabaseFile(
                context.getDatabasePath(EnbDatabaseValidator.DB_NAME)
            ).isValid
            setForeground(createForegroundInfo(0))

            val success = EnbDatabaseDownloader.downloadUpdate(context) { progress ->
                setProgress(workDataOf(KEY_PROGRESS to progress))
                notifySafely(notificationId, createNotification(progress))
            }

            if (success) {
                DbOperationTimings.finish(context, DbOperationTimings.ENB_DOWNLOAD)
                setProgress(workDataOf(KEY_PROGRESS to 100))
                showSuccessNotification()
                Result.success()
            } else {
                DbOperationTimings.clearStart(context, DbOperationTimings.ENB_DOWNLOAD)
                showErrorNotification()
                failureResult()
            }
        } catch (e: CancellationException) {
            DbOperationTimings.clearStart(context, DbOperationTimings.ENB_DOWNLOAD)
            cancelSafely(notificationId)
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "eNB database download worker failed", e)
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result {
        cancelSafely(notificationId)
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            DbOperationTimings.clearStart(context, DbOperationTimings.ENB_DOWNLOAD)
            showErrorNotification()
            failureResult()
        }
    }

    /** Dans une mise a jour groupee, une erreur est deja notifiee et ne doit pas bloquer la suite. */
    private fun failureResult(): Result =
        if (inputData.getBoolean(KEY_CONTINUE_AFTER_FAILURE, false)) Result.success() else Result.failure()

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        // Voir DatabaseDownloadWorker : seule la notification de progression (service de premier
        // plan) échappe à l'interrupteur maître des notifications.
        if (id != notificationId && !AppNotifications.canPost(context)) return
        runCatching {
            notificationManager.notify(id, notification)
        }.onFailure { error ->
            AppLogger.w(TAG, "eNB database download notification update failed", error)
        }
    }

    private fun cancelSafely(id: Int) {
        runCatching {
            notificationManager.cancel(id)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_database_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = createNotification(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotification(progress: Int): android.app.Notification {
        val databaseName = context.getString(R.string.notification_history_type_db_enb)
        val title = context.getString(
            if (isUpdatingExistingDatabase) R.string.notification_database_download_title
            else R.string.notification_database_first_download_title,
            databaseName
        )
        val content = context.getString(R.string.notification_database_download_progress, progress)
        val pendingIntent = settingsPendingIntent(0, showSuccessPopup = false)
        val cancelLabel = context.getString(R.string.appstrings_download_cancel)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val actionIconRes = NotificationIconResources.smallIconRes(context)

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
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
                .setContentIntent(pendingIntent)
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

    private fun showSuccessNotification() {
        recordHistory(NotificationHistoryStore.STATUS_SUCCESS)
        val databaseName = context.getString(R.string.notification_history_type_db_enb)
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_database_downloaded_title, databaseName))
            .setContentText(context.getString(R.string.notification_database_downloaded_content, databaseName))
            .setContentIntent(settingsPendingIntent(1, showSuccessPopup = true))
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        notifySafely(DownloadNotificationCenter.ENB_DB_DOWNLOAD_RESULT_NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification() {
        recordHistory(NotificationHistoryStore.STATUS_ERROR)
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(
                context.getString(
                    R.string.notification_database_download_failed_title,
                    context.getString(R.string.notification_history_type_db_enb)
                )
            )
            .setContentText(context.getString(R.string.notification_database_download_failed_content))
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        notifySafely(DownloadNotificationCenter.ENB_DB_DOWNLOAD_RESULT_NOTIFICATION_ID, notification)
    }

    /** Voir [DatabaseDownloadWorker] : on consigne même quand la notification ne part pas. */
    private fun recordHistory(status: String) {
        NotificationHistoryStore.record(
            context = context,
            type = NotificationHistoryStore.TYPE_DB_ENB,
            status = status,
            target = "geotower://settings?section=db_enb",
            posted = AppNotifications.canPost(context)
        )
    }

    private fun settingsPendingIntent(requestCode: Int, showSuccessPopup: Boolean): PendingIntent {
        // Cible la carte « identifiants eNB/gNB » précisément, pas le haut de la section.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=db_enb")
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
        const val UNIQUE_WORK_NAME = "enb_db_download"
        const val WORK_TAG = "database_download_enb"
        const val KEY_PROGRESS = "progress"
        private const val KEY_CONTINUE_AFTER_FAILURE = "continue_after_failure"

        private const val TAG = "GeoTowerEnbDb"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun buildRequest(continueAfterFailure: Boolean = false) = OneTimeWorkRequestBuilder<EnbDatabaseDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_CONTINUE_AFTER_FAILURE to continueAfterFailure))
            .addTag(WORK_TAG)
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
