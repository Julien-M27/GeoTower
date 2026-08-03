package fr.geotower.data.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import fr.geotower.data.outages.OutageLocalConfig
import java.util.concurrent.TimeUnit

/**
 * Planifie la régénération des pannes via [OutageGenerationWorker] : une génération immédiate
 * (« Générer maintenant ») et/ou une planification périodique en arrière-plan, selon les réglages.
 */
object OutageBackgroundScheduler {
    const val UNIQUE_PERIODIC_WORK = "OutageLocalPeriodicGeneration"
    const val UNIQUE_ONESHOT_WORK = "outage_generate_now"

    private fun networkConstraints() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Réconcilie la planif périodique avec l'état des réglages (source locale ET fond activé). */
    fun reconcile(context: Context) {
        val config = OutageLocalConfig(context)
        // Même condition que celle relue par le worker, sinon on planifie un travail qui refuse
        // de s'exécuter (cf. OutageGenerationWorker.doWork).
        if (fr.geotower.utils.AppConfig.outagesLocal() && config.backgroundEnabled) {
            schedule(context, config.frequencyHours)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context, frequencyHours: Int) {
        val hours = frequencyHours
            .coerceIn(OutageLocalConfig.MIN_FREQUENCY_HOURS, OutageLocalConfig.MAX_FREQUENCY_HOURS)
            .toLong()
        val request = PeriodicWorkRequestBuilder<OutageGenerationWorker>(hours, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(OutageGenerationWorker.KEY_CHECK_ENABLED to true))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
    }

    /** Lance une génération immédiate en tâche de fond avec notification live (« Générer maintenant »). */
    fun generateNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutageGenerationWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(OutageGenerationWorker.KEY_CHECK_ENABLED to false))
            .build()
        // REPLACE et pas KEEP : « maintenant » doit toujours repartir. Avec KEEP, un travail resté
        // en attente (échec précédent, contrainte réseau) absorbait silencieusement chaque appui.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_ONESHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
