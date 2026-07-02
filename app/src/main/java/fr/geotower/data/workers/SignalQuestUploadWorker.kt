package fr.geotower.data.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.AppGlobalState
import fr.geotower.BuildConfig
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.upload.CancelledUploadSummary
import fr.geotower.data.upload.ExternalPhotoUploadHistoryStore
import fr.geotower.data.upload.SignalQuestInvalidPhotoException
import fr.geotower.data.upload.SignalQuestUploadFile
import fr.geotower.data.upload.SignalQuestUploadFileStatus
import fr.geotower.data.upload.SignalQuestUploadManifest
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.upload.SignalQuestUploadQueueException
import fr.geotower.utils.AppLogger
import fr.geotower.utils.NotificationIconResources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

        var manifest = try {
            SignalQuestUploadQueue.loadManifest(applicationContext, uploadId)
        } catch (e: SignalQuestUploadQueueException) {
            logUploadIssue("invalid_manifest", e)
            return Result.failure()
        }

        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_UPLOAD) ||
            !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.SIGNALQUEST_UPLOAD)
        ) {
            SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
            return Result.failure()
        }

        val total = manifest.files.size
        if (total == 0 || manifest.siteId.isBlank()) {
            SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
            return Result.failure()
        }

        val progressNotificationId = progressNotificationId(uploadId)
        val resultNotificationId = resultNotificationId(uploadId)
        return try {
            ensureNotificationChannel()
            val initialProgress = finishedCount(manifest)
            setProgress(workDataOf("current" to initialProgress, "total" to total))
            if (!startForegroundSafely(manifest, current = initialProgress, total = total, notificationId = progressNotificationId)) {
                setProgress(workDataOf("error" to "foreground_unavailable"))
                return Result.retry()
            }

            for (uploadFile in SignalQuestUploadQueue.filesInUploadOrder(manifest.files)) {
                if (!SignalQuestUploadFileStatus.shouldUpload(uploadFile.status)) {
                    syncHistoryFromManifest(uploadFile)
                    continue
                }

                val result = uploadOneFile(manifest, uploadFile)
                manifest = SignalQuestUploadQueue.updateFileResult(
                    context = applicationContext,
                    manifest = manifest,
                    uploadFile = uploadFile,
                    status = result.toManifestStatus(),
                    remotePhotoId = result.remotePhotoId,
                    remoteImageUrl = result.remoteImageUrl,
                    remoteUploadedAt = result.remoteUploadedAt
                )
                ExternalPhotoUploadHistoryStore.updateUploadResult(
                    context = applicationContext,
                    entryId = uploadFile.historyEntryId,
                    status = result.toHistoryStatus(),
                    remotePhotoId = result.remotePhotoId,
                    remoteImageUrl = result.remoteImageUrl,
                    remoteUploadedAt = result.remoteUploadedAt
                )

                val current = finishedCount(manifest)
                setProgress(workDataOf("current" to current, "total" to total))
                showProgressNotification(manifest, current, total, progressNotificationId)

                if (result.outcome == UploadFileOutcome.RetryableFailure) {
                    showRetryNotification(progressNotificationId, resultNotificationId)
                    return Result.retry()
                }
            }

            val summary = manifestSummary(manifest)
            when {
                summary.retryableCount > 0 || summary.pendingCount > 0 -> {
                    showRetryNotification(progressNotificationId, resultNotificationId)
                    Result.retry()
                }
                summary.uploadedCount == total -> {
                    SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                    showFinishedNotification(uploadId, summary.uploadedCount, total, partial = false, progressNotificationId, resultNotificationId)
                    Result.success(uploadResultData(summary.uploadedCount, total))
                }
                summary.uploadedCount > 0 -> {
                    SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                    showFinishedNotification(uploadId, summary.uploadedCount, total, partial = true, progressNotificationId, resultNotificationId)
                    Result.success(uploadResultData(summary.uploadedCount, total))
                }
                else -> {
                    SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                    showFinishedNotification(uploadId, summary.uploadedCount, total, partial = true, progressNotificationId, resultNotificationId)
                    Result.failure(uploadResultData(summary.uploadedCount, total))
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                onUploadInterrupted(uploadId, progressNotificationId, resultNotificationId)
            }
            throw e
        }
    }

    // La coroutine du worker est annulee aussi bien quand l'utilisateur annule l'envoi que quand
    // WorkManager le stoppe pour reessayer plus tard (perte reseau, quotas systeme...). Seule la
    // vraie annulation (etat CANCELLED, persiste en base avant l'interruption du worker) finalise
    // l'upload ; sinon on laisse manifeste et fichiers en place pour la reprise.
    private suspend fun onUploadInterrupted(
        uploadId: String,
        progressNotificationId: Int,
        resultNotificationId: Int
    ) {
        cancelSafely(progressNotificationId)
        if (!isWorkCancelled()) return

        SignalQuestUploadQueue.finalizeCancelledUpload(applicationContext, uploadId)?.let { summary ->
            showCancelledNotification(uploadId, summary, resultNotificationId)
        }
        cleanupCancelledQueuedUploads()
    }

    private suspend fun isWorkCancelled(): Boolean {
        return runCatching {
            WorkManager.getInstance(applicationContext)
                .getWorkInfoByIdFlow(id)
                .first()
                ?.state == WorkInfo.State.CANCELLED
        }.getOrDefault(false)
    }

    // Annuler le work en cours annule aussi les envois ajoutes a sa suite dans la file unique
    // (APPEND_OR_REPLACE) : leurs workers ne demarreront jamais, on finalise donc leurs
    // manifestes ici pour ne pas laisser de photos en cache ni d'historique bloque « en cours ».
    private suspend fun cleanupCancelledQueuedUploads() {
        val cancelledInfos = runCatching {
            WorkManager.getInstance(applicationContext)
                .getWorkInfosByTagFlow(SignalQuestUploadScheduler.GLOBAL_TAG)
                .first()
        }.getOrDefault(emptyList())
            .filter { it.state == WorkInfo.State.CANCELLED && it.id != id }

        cancelledInfos.forEach { info ->
            val queuedUploadId = SignalQuestUploadScheduler.uploadIdFromTags(info.tags) ?: return@forEach
            if (SignalQuestUploadQueue.finalizeCancelledUpload(applicationContext, queuedUploadId) != null) {
                cancelUploadNotifications(applicationContext, queuedUploadId)
            }
        }
    }

    private fun showCancelledNotification(uploadId: String, summary: CancelledUploadSummary, resultNotificationId: Int) {
        notifySafely(
            resultNotificationId,
            createResultNotification(
                message = applicationContext.getString(
                    R.string.notification_signalquest_upload_cancelled,
                    summary.uploadedCount,
                    summary.totalCount
                ),
                successCount = summary.uploadedCount,
                total = summary.totalCount,
                hasErrors = summary.uploadedCount < summary.totalCount,
                requestCode = resultNotificationId,
                uploadId = uploadId
            )
        )
    }

    private suspend fun uploadOneFile(
        manifest: SignalQuestUploadManifest,
        uploadFile: SignalQuestUploadFile
    ): UploadFileResult {
        val uploadToken = requestUploadToken(manifest.siteId)
            ?: return UploadFileResult(UploadFileOutcome.RetryableFailure)

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
            val stripExifBody = manifest.stripExifBeforeUpload.toString()
                .toRequestBody("text/plain".toMediaTypeOrNull())
            val exifBody = SignalQuestUploadQueue.exifMetadataJsonForUpload(applicationContext, manifest, uploadFile)
                ?.toRequestBody("application/json".toMediaTypeOrNull())

            val response = SignalQuestClient.api.uploadSitePhoto(
                siteId = manifest.siteId,
                uploadToken = uploadToken,
                file = body,
                description = descBody,
                operator = opBody,
                anfrCode = anfrCodeBody,
                nationalSiteCode = nationalSiteCodeBody,
                sourceCode = sourceCodeBody,
                stripExifBeforeUpload = stripExifBody,
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

    private suspend fun requestUploadToken(siteId: String): String? {
        return try {
            val response = SignalQuestClient.api.createUploadToken(siteId)
            if (!response.isSuccessful) {
                val code = response.code()
                response.errorBody()?.close()
                logApiFailure(code)
                return null
            }
            response.body()?.token?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logUploadIssue("upload_token_network_failure", e)
            null
        } catch (e: Exception) {
            logUploadIssue("upload_token_exception", e)
            null
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
    ): Boolean {
        try {
            setForeground(createForegroundInfo(manifest, current, total, notificationId))
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "SignalQuest upload foreground notification failed", e)
            return false
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
        uploadId: String,
        successCount: Int,
        total: Int,
        partial: Boolean,
        progressNotificationId: Int,
        resultNotificationId: Int
    ) {
        cancelSafely(progressNotificationId)
        val message = if (!partial) {
            applicationContext.getString(R.string.notification_signalquest_upload_success, successCount, total)
        } else {
            applicationContext.getString(R.string.notification_signalquest_upload_partial, successCount, total)
        }
        notifySafely(
            resultNotificationId,
            createResultNotification(
                message = message,
                successCount = successCount,
                total = total,
                hasErrors = partial,
                requestCode = resultNotificationId,
                uploadId = uploadId
            )
        )
    }

    private fun showRetryNotification(progressNotificationId: Int, resultNotificationId: Int) {
        cancelSafely(progressNotificationId)
        notifySafely(
            resultNotificationId,
            createResultNotification(
                message = applicationContext.getString(R.string.notification_signalquest_upload_retry),
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
        val message = applicationContext.getString(R.string.notification_signalquest_upload_progress, current, total)
        val progressPercent = progressPercent(current, total)
        val shortCriticalText = "$current/$total"
        val pendingIntent = createUploadPendingIntent(manifest, notificationId)
        val cancelLabel = applicationContext.getString(R.string.appstrings_upload_cancel)
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val actionIconRes = NotificationIconResources.smallIconRes(applicationContext)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(100, progressPercent, false)
            .addAction(actionIconRes, cancelLabel, cancelIntent)
        NotificationIconResources.applyTo(builder, applicationContext)

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
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
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
        requestCode: Int,
        uploadId: String? = null
    ): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(APP_NOTIFICATION_TITLE)
            .setContentText(message)
            .setContentIntent(createUploadResultPendingIntent(message, successCount, total, hasErrors, requestCode, uploadId))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .let { NotificationIconResources.applyTo(it, applicationContext) }
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
        requestCode: Int,
        uploadId: String? = null
    ): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppGlobalState.EXTRA_SHOW_UPLOAD_RESULT_POPUP, true)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_MESSAGE, message)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_SUCCESS_COUNT, successCount)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_TOTAL, total)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_HAS_ERRORS, hasErrors)
            putExtra(AppGlobalState.EXTRA_UPLOAD_RESULT_UPLOAD_ID, uploadId)
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

    private fun UploadFileResult.toManifestStatus(): String {
        return when (outcome) {
            UploadFileOutcome.Success -> SignalQuestUploadFileStatus.UPLOADED
            UploadFileOutcome.AwaitingValidation -> SignalQuestUploadFileStatus.AWAITING_VALIDATION
            UploadFileOutcome.PermanentFailure -> SignalQuestUploadFileStatus.FAILED_PERMANENT
            UploadFileOutcome.RetryableFailure -> SignalQuestUploadFileStatus.RETRY
        }
    }

    private fun syncHistoryFromManifest(uploadFile: SignalQuestUploadFile) {
        val historyStatus = when (SignalQuestUploadFileStatus.normalized(uploadFile.status)) {
            SignalQuestUploadFileStatus.UPLOADED -> ExternalPhotoUploadHistoryStore.STATUS_SUCCESS
            SignalQuestUploadFileStatus.AWAITING_VALIDATION -> ExternalPhotoUploadHistoryStore.STATUS_AWAITING_VALIDATION
            SignalQuestUploadFileStatus.FAILED_PERMANENT -> ExternalPhotoUploadHistoryStore.STATUS_FAILED
            else -> return
        }

        ExternalPhotoUploadHistoryStore.updateUploadResult(
            context = applicationContext,
            entryId = uploadFile.historyEntryId,
            status = historyStatus,
            remotePhotoId = uploadFile.remotePhotoId,
            remoteImageUrl = uploadFile.remoteImageUrl,
            remoteUploadedAt = uploadFile.remoteUploadedAt
        )
    }

    private fun finishedCount(manifest: SignalQuestUploadManifest): Int {
        return manifest.files.count { uploadFile ->
            SignalQuestUploadFileStatus.countsAsFinished(uploadFile.status)
        }
    }

    private fun manifestSummary(manifest: SignalQuestUploadManifest): UploadManifestSummary {
        var uploadedCount = 0
        var permanentFailureCount = 0
        var retryableCount = 0
        var pendingCount = 0

        manifest.files.forEach { uploadFile ->
            when (SignalQuestUploadFileStatus.normalized(uploadFile.status)) {
                SignalQuestUploadFileStatus.UPLOADED,
                SignalQuestUploadFileStatus.AWAITING_VALIDATION -> uploadedCount++
                SignalQuestUploadFileStatus.FAILED_PERMANENT -> permanentFailureCount++
                SignalQuestUploadFileStatus.RETRY -> retryableCount++
                else -> pendingCount++
            }
        }

        return UploadManifestSummary(
            uploadedCount = uploadedCount,
            permanentFailureCount = permanentFailureCount,
            retryableCount = retryableCount,
            pendingCount = pendingCount
        )
    }

    private data class UploadManifestSummary(
        val uploadedCount: Int,
        val permanentFailureCount: Int,
        val retryableCount: Int,
        val pendingCount: Int
    )

    companion object {
        private const val TAG = "GeoTowerUpload"
        private const val APP_NOTIFICATION_TITLE = "GeoTower"
        private const val UPLOAD_NOTIFICATION_ID_BASE = 99
        private const val UPLOAD_RESULT_NOTIFICATION_ID_MASK = 0x3F3F

        private fun progressNotificationId(uploadId: String): Int {
            return UPLOAD_NOTIFICATION_ID_BASE xor uploadId.hashCode()
        }

        private fun resultNotificationId(uploadId: String): Int {
            return progressNotificationId(uploadId) xor UPLOAD_RESULT_NOTIFICATION_ID_MASK
        }

        // Purge les notifications residuelles (progression ou « nouvel essai plus tard ») d'un
        // upload finalise apres annulation sans que son worker n'ait tourne.
        fun cancelUploadNotifications(context: Context, uploadId: String) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { notificationManager.cancel(progressNotificationId(uploadId)) }
            runCatching { notificationManager.cancel(resultNotificationId(uploadId)) }
        }
    }
}
