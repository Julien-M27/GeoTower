package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.TripPlanStore
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources
import java.text.DateFormat
import java.util.Date

/**
 * Poste le rappel d'un trajet à l'approche de la date prévue.
 *
 * Le travail relit le trajet au moment de se déclencher plutôt que de porter son contenu : entre la
 * programmation et le réveil, la tournée a pu être décalée, archivée ou vidée de ses rappels.
 */
class TripReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        val offsetMinutes = inputData.getInt(KEY_OFFSET_MINUTES, 0)

        val plan = TripPlanStore.readOne(applicationContext, tripId) ?: return Result.success()
        val plannedAt = plan.plannedAtMillis ?: return Result.success()
        if (plan.status == TripPlan.STATUS_ARCHIVED || plan.status == TripPlan.STATUS_DONE) {
            return Result.success()
        }
        // Le délai a pu être retiré des réglages du trajet depuis la programmation.
        if (offsetMinutes !in plan.reminderOffsetsMinutes) return Result.success()
        // Et la date a pu être repoussée : l'annulation par étiquette devrait avoir supprimé ce
        // travail, mais un réveil déjà en vol passerait au travers. On préfère se taire.
        if (System.currentTimeMillis() < plannedAt - offsetMinutes * 60_000L - TOLERANCE_MILLIS) {
            return Result.success()
        }

        showNotification(plan, plannedAt)
        return Result.success()
    }

    private fun showNotification(plan: TripPlan, plannedAtMillis: Long) {
        if (!AppNotifications.canPost(applicationContext)) return

        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.notification_trip_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notificationId = notificationId(plan.id)
        val departure = DateFormat
            .getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT)
            .format(Date(plannedAtMillis))

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geotower://trips")).apply {
            setPackage(applicationContext.packageName)
            setClass(applicationContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(
                plan.name.ifBlank { applicationContext.getString(R.string.trips_untitled) }
            )
            .setContentText(applicationContext.getString(R.string.trips_reminder_text, departure))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    applicationContext.getString(R.string.trips_reminder_text, departure)
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                applicationContext.getString(R.string.trips_reminder_action_postpone),
                actionIntent(TripReminderActionReceiver.ACTION_POSTPONE, plan.id, notificationId)
            )
            .addAction(
                0,
                applicationContext.getString(R.string.trips_reminder_action_stop),
                actionIntent(TripReminderActionReceiver.ACTION_STOP, plan.id, notificationId)
            )
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, applicationContext) }
            .build()

        manager.notify(notificationId, notification)
    }

    private fun actionIntent(action: String, tripId: String, notificationId: Int): PendingIntent {
        val intent = Intent(applicationContext, TripReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(TripReminderActionReceiver.EXTRA_TRIP_ID, tripId)
            putExtra(TripReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            applicationContext,
            // Un code par action ET par trajet : sans cela, deux actions du même trajet
            // partageraient le même PendingIntent et la seconde écraserait la première.
            notificationId xor action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val KEY_TRIP_ID = "trip_id"
        const val KEY_OFFSET_MINUTES = "offset_minutes"

        private const val CHANNEL_ID = "trip_reminders_channel"

        /** Tolérance d'avance : WorkManager n'est pas à la seconde près, et c'est assumé. */
        private const val TOLERANCE_MILLIS = 5 * 60_000L

        private const val ID_MASK = 0x54524950 // "TRIP"

        /** Une notification par trajet : un second rappel remplace le premier au lieu de s'empiler. */
        fun notificationId(tripId: String): Int = tripId.hashCode() xor ID_MASK
    }
}
