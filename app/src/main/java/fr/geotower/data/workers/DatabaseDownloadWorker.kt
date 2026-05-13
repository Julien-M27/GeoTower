package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import android.content.pm.ServiceInfo
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class DatabaseDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "db_download_channel"
    private val notificationId = DownloadNotificationCenter.DB_DOWNLOAD_PROGRESS_NOTIFICATION_ID

    override suspend fun doWork(): Result {
        createChannel()
        return try {

            // 1. Démarrer en premier plan
            setForeground(createForegroundInfo(0))

            // 2. Lancer le téléchargement
            val success = DatabaseDownloader.downloadUpdate(context) { progress ->
                setProgress(workDataOf(KEY_PROGRESS to progress))
                notifySafely(notificationId, createNotification(progress))
            }

            // 3. Fin du téléchargement
            if (success) {
                setProgress(workDataOf(KEY_PROGRESS to 100))
                showSuccessNotification()
                Result.success()
            } else {
                showErrorNotification()
                Result.failure()
            }
        } catch (e: CancellationException) {
            cancelSafely(notificationId)
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Database download worker failed", e)
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result {
        cancelSafely(notificationId)
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            showErrorNotification()
            Result.failure()
        }
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
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
            val channelName = AppStrings.dbDownloadChannelName(context)
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

    private fun createNotification(progress: Int): android.app.Notification {
        val title = AppStrings.dbDownloadTitle(context)
        val content = AppStrings.dbDownloadProgress(context, progress)

        // ✅ AJOUT : Intent pour ouvrir les paramètres DB au clic
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=database")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent) // ✅ On ajoute le clic !
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = android.app.Notification.ProgressStyle()
                .setProgress(progress)
                .setProgressSegments(listOf(android.app.Notification.ProgressStyle.Segment(100)))

            val nativeBuilder = android.app.Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.geotower_logo)
                .setContentIntent(pendingIntent) // ✅ On ajoute le clic ici aussi !
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_PROGRESS)
                .setStyle(progressStyle)

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

    private fun showSuccessNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=database")
            putExtra("SHOW_DB_SUCCESS_POPUP", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = AppStrings.dbDownloadedTitle(context)
        val content = AppStrings.dbDownloadedContent(context)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notifySafely(DownloadNotificationCenter.DB_DOWNLOAD_RESULT_NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification() {
        val title = AppStrings.errorForService(context)
        val content = AppStrings.dbDownloadFailed(context)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setAutoCancel(true)
            .build()
        notifySafely(DownloadNotificationCenter.DB_DOWNLOAD_RESULT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "db_download"
        const val KEY_PROGRESS = "progress"

        private const val TAG = "GeoTowerDb"
        private const val MAX_RETRY_ATTEMPTS = 3
        fun buildRequest() = OneTimeWorkRequestBuilder<DatabaseDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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
