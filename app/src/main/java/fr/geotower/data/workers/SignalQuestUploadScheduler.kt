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

    // WorkInfo n'expose pas l'inputData : l'uploadId voyage donc aussi en tag pour pouvoir
    // retrouver le dossier d'upload d'un work annule (nettoyage historique + cache).
    private const val UPLOAD_ID_TAG_PREFIX = "sq_upload_id:"

    // L'operateur cible voyage aussi en tag pour l'afficher dans le pop-up « Envoi en cours »
    // des l'etat ENQUEUED (avant tout setProgress), sans relire le manifeste sur le disque.
    private const val OPERATOR_TAG_PREFIX = "sq_operator:"

    // Un envoi vers plusieurs operateurs cree un work par operateur, tous marques du meme batchId :
    // l'overlay attend la fin de TOUT le lot pour afficher un seul recapitulatif par operateur.
    private const val BATCH_TAG_PREFIX = "sq_batch:"

    fun uploadIdFromTags(tags: Set<String>): String? {
        return tags.firstOrNull { it.startsWith(UPLOAD_ID_TAG_PREFIX) }
            ?.removePrefix(UPLOAD_ID_TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }
    }

    fun operatorParamFromTags(tags: Set<String>): String? {
        return tags.firstOrNull { it.startsWith(OPERATOR_TAG_PREFIX) }
            ?.removePrefix(OPERATOR_TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }
    }

    fun batchIdFromTags(tags: Set<String>): String? {
        return tags.firstOrNull { it.startsWith(BATCH_TAG_PREFIX) }
            ?.removePrefix(BATCH_TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }
    }

    fun enqueue(context: Context, manifest: SignalQuestUploadManifest, batchId: String? = null): UUID {
        val appContext = context.applicationContext
        val request = buildRequest(manifest, batchId)
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return request.id
    }

    private fun buildRequest(manifest: SignalQuestUploadManifest, batchId: String?): OneTimeWorkRequest {
        val uploadData = workDataOf(
            SignalQuestUploadQueue.INPUT_UPLOAD_ID to manifest.uploadId
        )

        val builder = OneTimeWorkRequestBuilder<SignalQuestUploadWorker>()
            .setInputData(uploadData)
            .addTag("sq_upload_${manifest.siteId}")
            .addTag(GLOBAL_TAG)
            .addTag(UPLOAD_ID_TAG_PREFIX + manifest.uploadId)
            .addTag(OPERATOR_TAG_PREFIX + manifest.operator)
        if (!batchId.isNullOrBlank()) {
            builder.addTag(BATCH_TAG_PREFIX + batchId)
        }
        return builder
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
