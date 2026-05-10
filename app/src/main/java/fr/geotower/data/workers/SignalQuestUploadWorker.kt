package fr.geotower.data.workers

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.upload.SignalQuestInvalidPhotoException
import fr.geotower.data.upload.SignalQuestUploadFile
import fr.geotower.data.upload.SignalQuestUploadManifest
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.upload.SignalQuestUploadQueueException
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SignalQuestUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

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

        var successCount = 0
        var permanentFailureCount = 0
        var retryableFailureCount = 0

        setProgress(workDataOf("current" to 0, "total" to total))
        showNotification(
            title = "Signal Quest",
            message = AppStrings.signalQuestUploadProgress(applicationContext, 0, total),
            isProgress = true,
            current = 0,
            total = total
        )

        manifest.files.forEachIndexed { index, uploadFile ->
            val result = uploadOneFile(manifest, uploadFile)
            when (result) {
                UploadFileResult.Success -> successCount++
                UploadFileResult.PermanentFailure -> permanentFailureCount++
                UploadFileResult.RetryableFailure -> retryableFailureCount++
            }

            val current = index + 1
            setProgress(workDataOf("current" to current, "total" to total))
            showNotification(
                title = "Signal Quest",
                message = AppStrings.signalQuestUploadProgress(applicationContext, current, total),
                isProgress = true,
                current = current,
                total = total
            )
        }

        return when {
            successCount == total -> {
                SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                showFinishedNotification(successCount, total, partial = false)
                Result.success()
            }
            successCount > 0 -> {
                SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                showFinishedNotification(successCount, total, partial = true)
                Result.success()
            }
            retryableFailureCount > 0 && permanentFailureCount == 0 -> {
                showNotification(
                    title = "Signal Quest",
                    message = AppStrings.signalQuestUploadRetry(applicationContext),
                    isProgress = false
                )
                Result.retry()
            }
            else -> {
                SignalQuestUploadQueue.cleanupUpload(applicationContext, uploadId)
                showFinishedNotification(successCount, total, partial = true)
                Result.failure()
            }
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
            return UploadFileResult.PermanentFailure
        }

        return try {
            val requestFile = preparedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", preparedFile.name, requestFile)
            val descBody = manifest.description.toRequestBody("text/plain".toMediaTypeOrNull())
            val opBody = manifest.operator.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = SignalQuestClient.api.uploadSitePhoto(
                authHeader = "Bearer ${BuildConfig.SQ_API_KEY}",
                siteId = manifest.siteId,
                file = body,
                description = descBody,
                operator = opBody
            )

            if (response.isSuccessful) {
                preparedFile.delete()
                UploadFileResult.Success
            } else {
                val code = response.code()
                response.errorBody()?.close()
                logApiFailure(code)
                if (isRetryableHttpCode(code)) {
                    UploadFileResult.RetryableFailure
                } else {
                    preparedFile.delete()
                    UploadFileResult.PermanentFailure
                }
            }
        } catch (e: IOException) {
            logUploadIssue("network_failure", e)
            UploadFileResult.RetryableFailure
        } catch (e: Exception) {
            logUploadIssue("upload_exception", e)
            UploadFileResult.RetryableFailure
        }
    }

    private fun showFinishedNotification(successCount: Int, total: Int, partial: Boolean) {
        val message = if (!partial) {
            AppStrings.signalQuestUploadSuccess(applicationContext, successCount, total)
        } else {
            AppStrings.signalQuestUploadPartial(applicationContext, successCount, total)
        }
        showNotification("Signal Quest", message, false)
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

    private fun showNotification(
        title: String,
        message: String,
        isProgress: Boolean,
        current: Int = 0,
        total: Int = 0
    ) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "sq_upload_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                AppStrings.signalQuestUploadChannelName(applicationContext),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.logo_signalquest)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isProgress)

        if (isProgress) {
            builder.setProgress(total.coerceAtLeast(1), current.coerceAtLeast(0), false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private enum class UploadFileResult {
        Success,
        PermanentFailure,
        RetryableFailure
    }

    private companion object {
        private const val TAG = "GeoTowerUpload"
        private const val NOTIFICATION_ID = 99
    }
}
