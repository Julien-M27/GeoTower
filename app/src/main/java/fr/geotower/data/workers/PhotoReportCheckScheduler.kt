package fr.geotower.data.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.geotower.data.community.PhotoReportHistoryStore
import java.util.concurrent.TimeUnit

/**
 * La modération SignalQuest se compte en jours : une vérification quotidienne suffit, et le travail
 * n'est planifié que s'il y a réellement un signalement à suivre.
 */
object PhotoReportCheckScheduler {
    const val WORK_NAME = "photo_report_follow_up"

    /** Aligne la planification sur l'état réel : à appeler au lancement et après un signalement. */
    fun reconcile(context: Context) {
        val appContext = context.applicationContext
        if (PhotoReportHistoryStore.pendingFollowUp(appContext).isEmpty()) {
            cancel(appContext)
            return
        }
        enqueue(appContext, ExistingPeriodicWorkPolicy.KEEP)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<PhotoReportCheckWorker>(1, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }
}
