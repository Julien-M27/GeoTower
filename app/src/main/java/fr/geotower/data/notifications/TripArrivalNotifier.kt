package fr.geotower.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources

/**
 * Prévient qu'on vient d'arriver sur une étape de tournée, quand l'application n'est pas sous les
 * yeux.
 *
 * Sur le terrain, le téléphone finit dans une poche ou sur un support : la feuille d'arrivée
 * s'ouvre bien dans l'application, mais personne ne la voit. La notification est là pour ça — elle
 * ne fait que ramener l'application au premier plan, où la feuille attend déjà. Aucun lien profond :
 * naviguer ailleurs fermerait précisément ce qu'on veut montrer.
 */
object TripArrivalNotifier {

    private const val CHANNEL_ID = "trip_arrival_channel"
    private const val ID_MASK = 0x54415250 // "TARP"

    /**
     * @param stepLabel nom de l'étape, déjà mis en forme par l'appelant (il connaît le libellé de
     *   repli à utiliser pour une étape sans nom).
     */
    fun notifyArrival(context: Context, tripId: String, stepIndex: Int, stepLabel: String) {
        // Consigné AVANT le garde-fou : une arrivée qu'on n'a pas vue passer est justement ce qu'on
        // vient chercher dans le journal.
        NotificationHistoryStore.record(
            context = context,
            type = NotificationHistoryStore.TYPE_TRIP_ARRIVAL,
            status = NotificationHistoryStore.STATUS_INFO,
            label = stepLabel,
            detail = "",
            target = "map?tripId=${Uri.encode(tripId)}&tripMode=follow",
            posted = AppNotifications.canPost(context)
        )
        if (!AppNotifications.canPost(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_trip_arrival_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notificationId = notificationId(tripId, stepIndex)
        // `SINGLE_TOP` seul, sans `CLEAR_TOP` : on veut retrouver la carte telle qu'on l'a laissée,
        // feuille d'arrivée comprise, et non revenir à un écran de départ.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val text = context.getString(R.string.trips_arrival_notification_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.trips_arrival_notification_title, stepLabel))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Une notification par étape : arriver sur la suivante ne doit pas effacer la précédente, qu'on
     * n'a peut-être pas encore traitée.
     */
    fun notificationId(tripId: String, stepIndex: Int): Int =
        (tripId.hashCode() xor ID_MASK) + stepIndex
}
