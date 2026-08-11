package fr.geotower.data.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import fr.geotower.data.trip.TripPlanStore

/**
 * Les deux boutons du rappel de trajet.
 *
 * « Repousser d'un jour » décale **le trajet lui-même**, pas seulement le rappel : c'est le sens
 * qu'on attend quand on repousse une tournée, et les rappels suivent la nouvelle date.
 *
 * `onReceive` tourne sur le fil principal avec un budget de quelques secondes : lire et réécrire le
 * fichier des trajets y tient largement, mais rien de plus lourd ne doit venir s'y ajouter.
 */
class TripReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val applicationContext = context.applicationContext

        val plan = TripPlanStore.readOne(applicationContext, tripId)
        if (plan != null) {
            when (intent.action) {
                ACTION_POSTPONE -> {
                    val plannedAt = plan.plannedAtMillis
                    if (plannedAt != null) {
                        val next = plan.copy(plannedAtMillis = plannedAt + DAY_MILLIS)
                        TripPlanStore.save(applicationContext, next)
                        TripReminderScheduler.reschedule(applicationContext, next)
                    }
                }

                ACTION_STOP -> {
                    // On vide les délais plutôt que la date : la tournée reste prévue, elle cesse
                    // seulement de réveiller.
                    val next = plan.copy(reminderOffsetsMinutes = emptyList())
                    TripPlanStore.save(applicationContext, next)
                    TripReminderScheduler.cancel(applicationContext, tripId)
                }
            }
        }

        if (notificationId != 0) {
            NotificationManagerCompat.from(applicationContext).cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_POSTPONE = "fr.geotower.action.TRIP_REMINDER_POSTPONE"
        const val ACTION_STOP = "fr.geotower.action.TRIP_REMINDER_STOP"
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
