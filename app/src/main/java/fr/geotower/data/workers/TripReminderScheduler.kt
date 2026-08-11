package fr.geotower.data.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import fr.geotower.data.trip.TripPlan
import java.util.concurrent.TimeUnit

/**
 * Programme les rappels d'un trajet : un travail par délai, à réveiller avant l'heure de départ.
 *
 * **WorkManager et non une alarme exacte**, assumé : l'alarme à l'heure pile demande une permission
 * spéciale depuis Android 12 et une justification au Play Store, pour un gain qui n'a pas de sens
 * ici — un rappel de tournée à quelques minutes près suffit. L'interface le dit à l'utilisateur
 * plutôt que de laisser croire à une précision qu'on n'a pas.
 */
object TripReminderScheduler {
    private const val WORK_PREFIX = "trip_reminder_"
    private const val TAG_PREFIX = "trip_reminder_of_"

    /** Un nom unique par (trajet, délai) : reprogrammer remplace, jamais n'empile. */
    fun workName(tripId: String, offsetMinutes: Int): String = "$WORK_PREFIX${tripId}_$offsetMinutes"

    /** Étiquette commune à tous les rappels d'un trajet, pour les annuler d'un coup. */
    fun tripTag(tripId: String): String = "$TAG_PREFIX$tripId"

    /**
     * Reprogramme **tous** les rappels du trajet. À appeler après chaque changement de date, de
     * délais ou de statut : on annule d'abord tout, ce qui évite qu'un rappel d'une date abandonnée
     * survive à l'ancienne heure.
     */
    fun reschedule(context: Context, plan: TripPlan, nowMillis: Long = System.currentTimeMillis()) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelAllWorkByTag(tripTag(plan.id))

        pendingReminders(plan, nowMillis).forEach { (offsetMinutes, delayMillis) ->
            val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(tripTag(plan.id))
                .setInputData(
                    workDataOf(
                        TripReminderWorker.KEY_TRIP_ID to plan.id,
                        TripReminderWorker.KEY_OFFSET_MINUTES to offsetMinutes
                    )
                )
                .build()

            workManager.enqueueUniqueWork(
                workName(plan.id, offsetMinutes),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancel(context: Context, tripId: String) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tripTag(tripId))
    }

    /**
     * Les rappels réellement programmables, avec leur délai — en calcul pur, sans WorkManager.
     * C'est ici que se décide ce qu'on **ne** programme **pas**, et c'est ce qui se teste.
     */
    internal fun pendingReminders(plan: TripPlan, nowMillis: Long): List<Pair<Int, Long>> {
        val plannedAt = plan.plannedAtMillis ?: return emptyList()
        // Une tournée archivée ou déjà faite n'a plus à réveiller personne.
        if (plan.status == TripPlan.STATUS_ARCHIVED || plan.status == TripPlan.STATUS_DONE) {
            return emptyList()
        }
        return plan.reminderOffsetsMinutes.mapNotNull { offsetMinutes ->
            val delayMillis = plannedAt - offsetMinutes * 60_000L - nowMillis
            // Un rappel dont l'heure est déjà passée ne se rattrape pas : le poster annoncerait
            // « demain » pour une tournée d'hier.
            if (delayMillis <= 0L) null else offsetMinutes to delayMillis
        }
    }
}
