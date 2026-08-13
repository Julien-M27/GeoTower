package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geotower.R
import fr.geotower.data.community.PhotoReportHistoryEntry
import fr.geotower.utils.AppLocale
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources

/**
 * Les deux seules choses que l'app puisse honnêtement annoncer sur un signalement : qu'il est parti,
 * et que la photo a fini par disparaître. L'API SignalQuest est en écriture seule — pas de `GET`
 * pour relire un état de modération — donc un signalement **refusé** reste indiscernable d'un
 * signalement encore en attente, et n'est jamais notifié.
 */
object PhotoReportNotifier {
    private const val CHANNEL_ID = "photo_report_channel"

    fun notifySent(context: Context) {
        val appContext = context.applicationContext
        NotificationHistoryStore.record(
            context = appContext,
            type = NotificationHistoryStore.TYPE_PHOTO_REPORT,
            status = NotificationHistoryStore.STATUS_SUCCESS,
            target = "geotower://photo_reports",
            posted = AppNotifications.canPost(appContext)
        )
        if (!AppNotifications.canPost(appContext)) return

        val localized = localizedContext(appContext)
        post(
            appContext,
            localized,
            DownloadNotificationCenter.PHOTO_REPORT_SENT_NOTIFICATION_ID,
            localized.getString(R.string.notification_photo_report_sent_title),
            localized.getString(R.string.notification_photo_report_sent_desc)
        )
    }

    fun notifyRemoved(context: Context, entry: PhotoReportHistoryEntry) {
        val appContext = context.applicationContext
        // `label` porte le site concerné : le libellé complet est reconstruit à l'affichage.
        NotificationHistoryStore.record(
            context = appContext,
            type = NotificationHistoryStore.TYPE_PHOTO_REPORT,
            status = NotificationHistoryStore.STATUS_INFO,
            label = entry.siteId,
            target = "geotower://photo_reports",
            posted = AppNotifications.canPost(appContext)
        )
        if (!AppNotifications.canPost(appContext)) return

        val localized = localizedContext(appContext)
        post(
            appContext,
            localized,
            DownloadNotificationCenter.photoReportRemovedNotificationId(entry.photoId),
            localized.getString(R.string.notification_photo_report_removed_title),
            localized.getString(R.string.notification_photo_report_removed_desc, entry.siteId)
        )
    }

    private fun post(
        context: Context,
        localized: Context,
        notificationId: Int,
        title: String,
        text: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.notification_photo_report_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geotower://photo_reports")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun localizedContext(context: Context): Context {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val language = prefs.getString("app_language", AppLocale.LANGUAGE_SYSTEM)
            ?: AppLocale.LANGUAGE_SYSTEM
        return AppLocale.localizedContext(context, language)
    }
}
