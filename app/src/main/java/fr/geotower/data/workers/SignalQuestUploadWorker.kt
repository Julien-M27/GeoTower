package fr.geotower.data.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.AppGlobalState
import fr.geotower.BuildConfig
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.upload.ExternalPhotoUploadHistoryStore
import fr.geotower.data.upload.SignalQuestInvalidPhotoException
import fr.geotower.data.upload.SignalQuestUploadFile
import fr.geotower.data.upload.SignalQuestUploadManifest
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.upload.SignalQuestUploadQueueException
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SignalQuestUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val channelId = "sq_upload_channel"

    override suspend fun doWork(): Result {
        val uploadId = inputData.getString(SignalQuestUploadQueue.INPUT_UPLOAD_ID)
            ?: return Result.failure()

        val manifest = try {
            SignalQuestUploadQueue.loadManifest(applicationContext, uploadId)
        } catch (e: SignalQuestUploadQueueException) {
            logUploadIssue("invalid_manifest", e)
            return Result.failure()
        }

        val total = manifest.files.size
        if (total == 0 || manifest.siteId.isBlank()) {
            SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
            return Result.failure()
        }

        val progressNotificationId = progressNotificationId(uploadId)
        val resultNotificationId = resultNotificationId(uploadId)
        var successCount = 0
        var permanentFailureCount = 0
        var retryableFailureCount = 0

        return try {
            ensureNotificationChannel()
            setProgress(workDataOf("current" to 0, "total" to total))
            startForegroundSafely(manifest, current = 0, total = total, notificationId = progressNotificationId)

            manifest.files.forEachIndexed { index, uploadFile ->
                val result = uploadOneFile(manifest, uploadFile)
                when (result.outcome) {
                    UploadFileOutcome.Success,
                    UploadFileOutcome.AwaitingValidation -> successCount++
                    UploadFileOutcome.PermanentFailure -> permanentFailureCount++
                    UploadFileOutcome.RetryableFailure -> retryableFailureCount++
                }
                ExternalPhotoUploadHistoryStore.updateUploadResult(
                    context = applicationContext,
                    entryId = uploadFile.historyEntryId,
                    status = result.toHistoryStatus(),
                    remotePhotoId = result.remotePhotoId,
                    remoteImageUrl = result.remoteImageUrl,
                    remoteUploadedAt = result.remoteUploadedAt
                )

                val current = index + 1
                setProgress(workDataOf("current" to current, "total" to total))
                showProgressNotification(manifest, current, total, progressNotificationId)
            }

            when {
                successCount == total -> {
                    SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                    showFinishedNotification(successCount, total, partial = false, progressNotificationId, resultNotificationId)
                    Result.success(uploadResultData(successCount, total))
                }
                successCount > 0 -> {
                    SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                    showFinishedNotification(successCount, total, partial = true, progressNotificationId, resultNotificationId)
                    Result.success(uploadResultData(successCount, total))
                }
                retryableFailureCount > 0 && permanentFailureCount == 0 -> {
                    showRetryNotification(progressNotificationId, resultNotificationId)
                    Result.retry()
                }
                else -> {
                    SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                    showFinishedNotification(successCount, total, partial = true, progressNotificationId, resultNotificationId)
                    Result.failure(uploadResultData(successCount, total))
                }
            }
        } catch (e: CancellationException) {
            cancelSafely(progressNotificationId)
            throw e
        }
    }

    private suspend fun uploadOneFile(
        manifest: SignalQuestUploadManifest,
        uploadFile: SignalQuestUploadFile
    ): UploadFileResult {
        val preparedFile = try {
            SignalQuestUploadQueue.prepareJpegForUpload(applicationContext, manifest, uploadFile)
        } catch (e: SignalQuestInvalidPhotoException) {
            logUploadIssue("invalid_photo", e)
            return UploadFileResult(UploadFileOutcome.PermanentFailure)
        }

        return try {
            val requestFile = preparedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", preparedFile.name, requestFile)
            val descBody = manifest.description.toRequestBody("text/plain".toMediaTypeOrNull())
            val opBody = manifest.operator.toRequestBody("text/plain".toMediaTypeOrNull())
            val anfrCodeBody = manifest.anfrCode.toTextPartBody()
            val nationalSiteCodeBody = manifest.nationalSiteCode.toTextPartBody()
            val sourceCodeBody = manifest.sourceCode.toTextPartBody()
            val exifBody = SignalQuestUploadQueue.exifMetadataJsonForUpload(applicationContext, manifest, uploadFile)
                ?.toRequestBody("application/json".toMediaTypeOrNull())

            val response = SignalQuestClient.api.uploadSitePhoto(
                authHeader = "Bearer ${BuildConfig.SQ_API_KEY}",
                siteId = manifest.siteId,
                file = body,
                description = descBody,
                operator = opBody,
                anfrCode = anfrCodeBody,
                nationalSiteCode = nationalSiteCodeBody,
                sourceCode = sourceCodeBody,
                exifMetadata = exifBody
            )

            if (response.isSuccessful) {
                val uploadedPhoto = response.body()?.data
                preparedFile.delete()
                UploadFileResult(
                    outcome = if (uploadedPhoto?.approved == true) {
                        UploadFileOutcome.Success
                    } else {
                        UploadFileOutcome.AwaitingValidation
                    },
                    remotePhotoId = uploadedPhoto?.id,
                    remoteImageUrl = uploadedPhoto?.imageUrl,
                    remoteUploadedAt = uploadedPhoto?.uploadedAt
                )
            } else {
                val code = response.code()
                response.errorBody()?.close()
                logApiFailure(code)
                if (isRetryableHttpCode(code)) {
                    UploadFileResult(UploadFileOutcome.RetryableFailure)
                } else {
                    preparedFile.delete()
                    UploadFileResult(UploadFileOutcome.PermanentFailure)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logUploadIssue("network_failure", e)
            UploadFileResult(UploadFileOutcome.RetryableFailure)
        } catch (e: Exception) {
            logUploadIssue("upload_exception", e)
            UploadFileResult(UploadFileOutcome.RetryableFailure)
        }
    }

    private fun String?.toTextPartBody(): RequestBody? {
        val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    private suspend fun startForegroundSafely(
        manifest: SignalQuestUploadManifest,
        current: Int,
        total: Int,
        notificationId: Int
    ) {
        try {
            setForeground(createForegroundInfo(manifest, current, total, notificationId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "SignalQuest upload foreground notification failed", e)
            showProgressNotification(manifest, current, total, notificationId)
        }
    }

    private fun createForegroundInfo(
        manifest: SignalQuestUploadManifest,
        current: Int,
        total: Int,
        notificationId: Int
    ): ForegroundInfo {
        val notification = createProgressNotification(manifest, current, total, notificationId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun showProgressNotification(
        manifest: SignalQuestUploadManifest,
        current: Int,
        total: Int,
        notificationId: Int
    ) {
        notifySafely(notificationId, createProgressNotification(manifest, current, total, notificationId))
    }

    private fun showFinishedNotification(
        successCount: Int,
        total: Int,
        partial: Boolean,
        progressNotificationId: Int,
        resultNotificationId: Int
    ) {
        cancelSafely(progressNotificationId)
        val message = if (!partial) {
            AppStrings.signalQuestUploadSuccess(applicationContext, successCount, total)
        } else {
            AppStrings.signalQuestUploadPartial(applicationContext, successCount, total)
        }
        notifySafely(
            resultNotificationId,
            createResultNotification(
                message = message,
                successCount = successCount,
                total = total,
                hasErrors = partial,
                requestCode = resultNotificationId
            )
        )
    }

    private fun showRetryNotification(progressNotificationId: Int, resultNotificationId: Int) {
        cancelSafely(progressNotificationId)
        notifySafely(
            resultNotificationId,
            createResultNotification(
                message = AppStrings.signalQuestUploadRetry(applicationContext),
                successCount = 0,
                total = 0,
                hasErrors = true,
                requestCode = resultNotificationId
            )
        )
    }

    private fun isRetryableHttpCode(code: Int): Boolean {
        return code == 408 || code == 425 || code == 429 || code in 500..599
    }

    private fun logApiFailure(code: Int) {
        if (BuildConfig.DEBUG) {
            AppLogger.w(TAG, "SignalQuest upload API failure code=$code")
        } else {
            AppLogger.w(TAG, "SignalQuest upload API failure")
        }
    }

    private fun logUploadIssue(reason: String, throwable: Throwable) {
        AppLogger.w(TAG, reason, throwable)
    }

    private fun createProgressNotification(
        manifest: SignalQuestUploadManifest,
        current: Int,
        total: Int,
        notificationId: Int
    ): Notification {
        ensureNotificationChannel()
        val title = APP_NOTIFICATION_TITLE
        val message = AppStrings.signalQuestUploadProgress(applicationContext, current, total)
        val progressPercent = progressPercent(current, total)
        val shortCriticalText = "$current/$total"
        val pendingIntent = createUploadPendingIntent(manifest, notificationId)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(100, progressPercent, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = Notification.ProgressStyle()
                .setProgress(progressPercent)
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))

            val nativeBuilder = Notification.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.geotower_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setStyle(progressStyle)

            runCatching {
                Notification.Builder::class.java
                    .getMethod("setForegroundServiceBehavior", Int::class.javaPrimitiveType)
                    .invoke(nativeBuilder, Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            runCatching {
                Notification.Builder::class.java
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(nativeBuilder, true)
            }
            runCatching {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(nativeBuilder, shortCriticalText)
            }

            return nativeBuilder.build().apply {
                extras.putBoolean("android.requestPromotedOngoing", true)
                extras.putString("android.shortCriticalText", shortCriticalText)
            }
        }

        return builder.build()
    }

    private fun createResultNotification(
        message: String,
        successCount: Int,
        total: Int,
        hasErrors: Boolean,
        requestCode: Int
    ): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(APP_NOTIFICATION_TITLE)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(createUploadResultPendingIntent(message, successCount, total, hasErrors, requestCode))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createUploadPendingIntent(manifest: SignalQuestUploadManifest, requestCode: Int): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("geotower://support/${Uri.encode(manifest.siteId)}")
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createUploadResultPendingIntent(
        message: String,
        successCount: Int,
        total: Int,
        hasErrors: Boolean,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppGlobalState.EXTRA_SHOW_UPLOAD_RESULT_POPUP, true)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_MESSAGE, message)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_SUCCESS_COUNT, successCount)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_TOTAL, total)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_HAS_ERRORS, hasErrors)
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifySafely(id: Int, notification: Notification) {
        runCatching {
            notificationManager.notify(id, notification)
        }.onFailure { error ->
            AppLogger.w(TAG, "SignalQuest upload notification update failed", error)
        }
    }

    private fun cancelSafely(id: Int) {
        runCatching {
            notificationManager.cancel(id)
        }
    }

    private fun progressPercent(current: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((current.coerceIn(0, total) * 100f) / total).toInt().coerceIn(0, 100)
    }

    private fun progressNotificationId(uploadId: String): Int {
        return UPLOAD_NOTIFICATION_ID_BASE xor uploadId.hashCode()
    }

    private fun resultNotificationId(uploadId: String): Int {
        return progressNotificationId(uploadId) xor UPLOAD_RESULT_NOTIFICATION_ID_MASK
    }

    private fun uploadResultData(successCount: Int, total: Int): androidx.work.Data {
        return workDataOf("success_count" to successCount, "total" to total)
    }

    private data class UploadFileResult(
        val outcome: UploadFileOutcome,
        val remotePhotoId: String? = null,
        val remoteImageUrl: String? = null,
        val remoteUploadedAt: String? = null
    )

    private enum class UploadFileOutcome {
        Success,
        AwaitingValidation,
        PermanentFailure,
        RetryableFailure
    }

    private fun UploadFileResult.toHistoryStatus(): String {
        return when (outcome) {
            UploadFileOutcome.Success -> ExternalPhotoUploadHistoryStore.STATUS_SUCCESS
            UploadFileOutcome.AwaitingValidation -> ExternalPhotoUploadHistoryStore.STATUS_AWAITING_VALIDATION
            UploadFileOutcome.PermanentFailure -> ExternalPhotoUploadHistoryStore.STATUS_FAILED
            UploadFileOutcome.RetryableFailure -> ExternalPhotoUploadHistoryStore.STATUS_RETRY
        }
    }

    private companion object {
        private const val TAG = "GeoTowerUpload"
        private const val APP_NOTIFICATION_TITLE = "GeoTower"
        private const val UPLOAD_NOTIFICATION_ID_BASE = 99
        private const val UPLOAD_RESULT_NOTIFICATION_ID_MASK = 0x3F3F
    }
}
