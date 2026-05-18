package fr.geotower.data.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import fr.geotower.data.upload.SignalQuestUploadManifest
import fr.geotower.data.upload.SignalQuestUploadQueue
import java.util.UUID
import java.util.concurrent.TimeUnit

object SignalQuestUploadScheduler {
    const val UNIQUE_WORK_NAME = "signalquest_upload_queue"
    const val GLOBAL_TAG = "sq_upload_global"

    fun enqueue(context: Context, manifest: SignalQuestUploadManifest): UUID {
        val appContext = context.applicationContext
        val request = buildRequest(manifest)
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return request.id
    }

    private fun buildRequest(manifest: SignalQuestUploadManifest): OneTimeWorkRequest {
        val uploadData = workDataOf(
            SignalQuestUploadQueue.INPUT_UPLOAD_ID to manifest.uploadId
        )

        return OneTimeWorkRequestBuilder<SignalQuestUploadWorker>()
            .setInputData(uploadData)
            .addTag("sq_upload_${manifest.siteId}")
            .addTag(GLOBAL_TAG)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .build()
    }
}
