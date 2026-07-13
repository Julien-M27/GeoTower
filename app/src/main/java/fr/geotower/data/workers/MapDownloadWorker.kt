package fr.geotower.data.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.utils.AppLogger
import fr.geotower.utils.NotificationIconResources
import fr.geotower.utils.OfflineMapDisplayNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MapDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val channelId = DownloadNotificationCenter.MAP_DOWNLOAD_CHANNEL_ID

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.OFFLINE_MAPS_DOWNLOAD) ||
            !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.OFFLINE_MAP_DOWNLOAD)
        ) {
            return@withContext Result.success()
        }
        val mapUrl = inputData.getString("map_url") ?: return@withContext Result.failure()
        val mapFilename = inputData.getString("map_filename") ?: return@withContext Result.failure()
        val mapDisplayName = inputData.getString("map_name")
            ?.takeIf { it.isNotBlank() }
            ?: OfflineMapDisplayNames.formatMapName(mapFilename)
        val expectedSha256 = inputData.getString("map_sha256")?.takeIf { it.isNotBlank() }
        val estimatedSizeMb = inputData.getInt("estimated_size_mb", 2000)
        val maxAllowedBytes = OfflineMapDownloadValidator.maxAllowedDownloadBytes(estimatedSizeMb)

        if (!OfflineMapDownloadValidator.isAllowedHttpsUrl(mapUrl) ||
            !OfflineMapDownloadValidator.isSafeMapFilename(mapFilename) ||
            !OfflineMapDownloadValidator.isOptionalSha256Valid(expectedSha256) ||
            maxAllowedBytes == null
        ) {
            setProgress(workDataOf("error" to "invalid_map_catalog"))
            return@withContext Result.failure()
        }

        val progressNotifId = DownloadNotificationCenter.mapDownloadNotificationId(mapFilename)
        val resultNotifId = DownloadNotificationCenter.mapDownloadResultNotificationId(mapFilename)
        DownloadNotificationCenter.rememberMapDownloadNotification(applicationContext, mapFilename)

        if (!startForegroundOrRetry(mapDisplayName, progressNotifId)) {
            setProgress(workDataOf("error" to "foreground_unavailable"))
            return@withContext Result.retry()
        }

        if (!hasEnoughSpace(estimatedSizeMb)) {
            setProgress(workDataOf("error" to "not_enough_space"))
            return@withContext Result.failure()
        }

        val mapsDir = File(applicationContext.getExternalFilesDir(null), "maps")
        if (!mapsDir.exists() && !mapsDir.mkdirs()) {
            setProgress(workDataOf("error" to "maps_dir_unavailable"))
            return@withContext Result.failure()
        }

        val finalMapFile = OfflineMapDownloadValidator.safeMapFile(mapsDir, mapFilename)
            ?: return@withContext Result.failure()
        val tempMapFile = File(mapsDir.canonicalFile, "${finalMapFile.name}.download")
        val backupMapFile = File(mapsDir.canonicalFile, "${finalMapFile.name}.backup")

        // Chrono de telechargement (live pendant, duree finale apres) affiche par MapDownloadCard.
        val timingKey = DbOperationTimings.mapKey(mapFilename)
        DbOperationTimings.markStart(applicationContext, timingKey)

        try {
            setProgress(workDataOf("state" to "DOWNLOADING", "progress" to 0))
            if (tempMapFile.exists()) tempMapFile.delete()

            val request = Request.Builder().url(mapUrl).build()
            val response = RetrofitClient.currentClient.newCall(request).execute()

            response.use { safeResponse ->
                if (!safeResponse.isSuccessful) return@withContext Result.failure()

                val body = safeResponse.body ?: return@withContext Result.failure()
                val expectedContentLength = body.contentLength()
                if (expectedContentLength > maxAllowedBytes) {
                    setProgress(workDataOf("error" to "map_too_large"))
                    return@withContext Result.failure()
                }

                body.byteStream().use { inputStream ->
                    FileOutputStream(tempMapFile).use { outputStream ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesCopied = 0L
                        var bytes = inputStream.read(buffer)
                        var lastProgress = 0

                        while (bytes >= 0) {
                            if (isStopped) {
                                DbOperationTimings.clearStart(applicationContext, timingKey)
                                tempMapFile.delete()
                                notificationManager.cancel(progressNotifId)
                                return@withContext Result.failure()
                            }

                            bytesCopied += bytes
                            if (bytesCopied > maxAllowedBytes) {
                                setProgress(workDataOf("error" to "map_too_large"))
                                throw IllegalStateException("map_download_too_large")
                            }
                            outputStream.write(buffer, 0, bytes)

                            if (expectedContentLength > 0) {
                                val progress = ((bytesCopied * 100) / expectedContentLength).toInt()
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    setProgress(workDataOf("state" to "DOWNLOADING", "progress" to progress))
                                    notificationManager.notify(progressNotifId, createNotification(progress, mapDisplayName, progressNotifId))
                                }
                            }

                            bytes = inputStream.read(buffer)
                        }
                    }
                }

                if (!OfflineMapDownloadValidator.isValidDownloadedMap(
                        file = tempMapFile,
                        expectedContentLength = expectedContentLength,
                        expectedSha256 = expectedSha256
                    )
                ) {
                    tempMapFile.delete()
                    setProgress(workDataOf("error" to "invalid_map_file"))
                    return@withContext Result.failure()
                }
            }

            setProgress(workDataOf("state" to "DONE", "progress" to 100))
            val success = replaceMapAtomically(tempMapFile, finalMapFile, backupMapFile)

            return@withContext if (success) {
                DbOperationTimings.finish(applicationContext, timingKey)
                showSuccessNotification(mapFilename, mapDisplayName, progressNotifId, resultNotifId)
                Result.success()
            } else {
                DbOperationTimings.clearStart(applicationContext, timingKey)
                tempMapFile.delete()
                Result.failure()
            }
        } catch (e: CancellationException) {
            DbOperationTimings.clearStart(applicationContext, timingKey)
            if (tempMapFile.exists()) tempMapFile.delete()
            notificationManager.cancel(progressNotifId)
            throw e
        } catch (e: Exception) {
            DbOperationTimings.clearStart(applicationContext, timingKey)
            AppLogger.w(TAG, "Map download failed", e)
            if (tempMapFile.exists()) tempMapFile.delete()
            return@withContext Result.failure()
        }
    }

    private fun replaceMapAtomically(tempMapFile: File, finalMapFile: File, backupMapFile: File): Boolean {
        if (backupMapFile.exists() && !backupMapFile.delete()) return false

        val hadExistingMap = finalMapFile.exists()
        if (hadExistingMap && !finalMapFile.renameTo(backupMapFile)) {
            tempMapFile.delete()
            return false
        }

        if (!tempMapFile.renameTo(finalMapFile)) {
            if (hadExistingMap && backupMapFile.exists()) {
                backupMapFile.renameTo(finalMapFile)
            }
            tempMapFile.delete()
            return false
        }

        if (backupMapFile.exists()) backupMapFile.delete()
        return true
    }

    private fun hasEnoughSpace(estimatedSizeMb: Int): Boolean {
        val requiredBytes = estimatedSizeMb * 1024L * 1024L * 1.1
        val stat = StatFs(applicationContext.getExternalFilesDir(null)?.path ?: return false)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes > requiredBytes
    }

    private suspend fun startForegroundOrRetry(mapDisplayName: String, progressNotifId: Int): Boolean {
        return try {
            setForeground(createForegroundInfo(0, mapDisplayName, progressNotifId))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Map download foreground setup failed", e)
            false
        }
    }

    private fun createForegroundInfo(progress: Int, mapName: String, notifId: Int): ForegroundInfo {
        val notification = createNotification(progress, mapName, notifId)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun createNotification(progress: Int, mapName: String, notifId: Int): Notification {
        DownloadNotificationCenter.rememberMapDownloadNotification(applicationContext, notifId)

        ensureNotificationChannel()

        val pendingIntent = createOfflineMapsPendingIntent(notifId)
        val title = applicationContext.getString(R.string.notification_map_download_title, mapName)
        val content = applicationContext.getString(R.string.notification_map_download_progress, progress)
        val cancelLabel = applicationContext.getString(R.string.appstrings_download_cancel)
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val actionIconRes = NotificationIconResources.smallIconRes(applicationContext)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(actionIconRes, cancelLabel, cancelIntent)
        NotificationIconResources.applyTo(builder, applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = Notification.ProgressStyle()
                .setProgress(progress)
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))

            val nativeBuilder = Notification.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setStyle(progressStyle)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(applicationContext, actionIconRes),
                        cancelLabel,
                        cancelIntent
                    ).build()
                )
            NotificationIconResources.applyTo(nativeBuilder, applicationContext)

            runCatching {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(nativeBuilder, "$progress%")
            }
            runCatching {
                Notification.Builder::class.java
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

    private fun showSuccessNotification(mapFilename: String, mapDisplayName: String, progressNotifId: Int, resultNotifId: Int) {
        DownloadNotificationCenter.rememberMapDownloadNotification(applicationContext, mapFilename)
        ensureNotificationChannel()
        notificationManager.cancel(progressNotifId)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.notification_map_downloaded_title))
            .setContentText(applicationContext.getString(R.string.notification_map_downloaded_content, mapDisplayName))
            .setContentIntent(createOfflineMapsPendingIntent(resultNotifId, mapFilename))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .let { NotificationIconResources.applyTo(it, applicationContext) }
            .build()

        notificationManager.notify(resultNotifId, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notification_map_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createOfflineMapsPendingIntent(requestCode: Int, targetMapFilename: String? = null): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.Builder()
                .scheme("geotower")
                .authority("settings")
                .appendQueryParameter("section", "offline_maps")
                .apply {
                    if (!targetMapFilename.isNullOrBlank()) {
                        appendQueryParameter("target_map", targetMapFilename)
                    }
                }
                .build()
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        private const val TAG = "GeoTowerMap"
    }
}
