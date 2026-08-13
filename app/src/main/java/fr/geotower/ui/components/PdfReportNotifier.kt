package fr.geotower.ui.components

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geotower.R
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources

/**
 * Notifications du rapport PDF : une notification **live** pendant la génération (barre de
 * progression, page en cours et station concernée) puis, pour un téléchargement, une notification
 * cliquable qui ouvre le fichier.
 *
 * Un rapport multi-opérateurs prend plusieurs secondes : la notification permet de suivre l'avancée
 * et de retrouver le PDF même si l'utilisateur a quitté l'écran entre-temps.
 */
object PdfReportNotifier {
    private const val CHANNEL_ID = "pdf_report_channel"
    private const val PROGRESS_NOTIFICATION_ID = 473_001
    private const val RESULT_NOTIFICATION_ID = 473_002
    private const val TAG = "PdfReportNotifier"

    /**
     * Début de génération, avant que le nombre de pages soit connu (collecte des speedtests,
     * capture de la carte) : barre de progression indéterminée.
     */
    fun showPreparing(context: Context, detail: String) {
        val builder = builder(context)
            .setContentTitle(context.getString(R.string.appstrings_pdf_report_generating))
            .setContentText(detail)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        notifySafely(context, PROGRESS_NOTIFICATION_ID, builder.build())
    }

    /** Progression : [pageNumber] sur [pageCount], [detail] nommant la station en cours de rendu. */
    fun showProgress(context: Context, pageNumber: Int, pageCount: Int, detail: String) {
        val safeCount = pageCount.coerceAtLeast(1)
        val safeNumber = pageNumber.coerceIn(1, safeCount)
        val pageLabel = context.getString(R.string.appstrings_pdf_page_indicator, safeNumber, safeCount)
        val text = if (detail.isBlank()) pageLabel else "$detail — $pageLabel"
        val builder = builder(context)
            .setContentTitle(context.getString(R.string.appstrings_pdf_report_generating))
            .setContentText(text)
            .setProgress(safeCount, safeNumber, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        notifySafely(context, PROGRESS_NOTIFICATION_ID, builder.build())
    }

    /** Rapport enregistré : la notification ouvre le PDF, l'action ouvre les Téléchargements. */
    fun showDownloaded(context: Context, uri: Uri, fileName: String) {
        cancelProgress(context)
        // Le journal vise le fichier lui-même : l'URI MediaStore reste valide après le redémarrage,
        // et si le PDF a été supprimé entre-temps la page le dira plutôt que d'ouvrir dans le vide.
        NotificationHistoryStore.record(
            context = context,
            type = NotificationHistoryStore.TYPE_PDF_REPORT,
            status = NotificationHistoryStore.STATUS_SUCCESS,
            label = fileName,
            target = uri.toString(),
            posted = AppNotifications.canPost(context)
        )
        val description = context.getString(R.string.appstrings_pdf_downloaded_desc, fileName)
        val builder = builder(context)
            .setContentTitle(context.getString(R.string.appstrings_pdf_downloaded))
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setContentIntent(openPdfPendingIntent(context, uri))
            .setAutoCancel(true)
            .addAction(
                NotificationIconResources.smallIconRes(context),
                context.getString(R.string.appstrings_pdf_open_folder),
                openDownloadsPendingIntent(context)
            )
        notifySafely(context, RESULT_NOTIFICATION_ID, builder.build())
    }

    /** Fin sans fichier à proposer : partage direct, export vide ou échec. */
    fun cancelProgress(context: Context) {
        runCatching { notificationManager(context).cancel(PROGRESS_NOTIFICATION_ID) }
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        NotificationIconResources.applyTo(builder, context)
        return builder
    }

    private fun openPdfPendingIntent(context: Context, uri: Uri): PendingIntent {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            RESULT_NOTIFICATION_ID,
            view,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openDownloadsPendingIntent(context: Context): PendingIntent {
        val downloads = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            RESULT_NOTIFICATION_ID + 1,
            downloads,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            notificationManager(context).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.appstrings_pdf_report_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    /**
     * Sans la permission POST_NOTIFICATIONS — ou avec les notifications de l'app coupées — le
     * rapport se génère quand même, en silence.
     */
    private fun notifySafely(context: Context, id: Int, notification: android.app.Notification) {
        if (!AppNotifications.canPost(context)) return
        runCatching { notificationManager(context).notify(id, notification) }
            .onFailure { AppLogger.w(TAG, "Report PDF notification failed", it) }
    }

    private fun notificationManager(context: Context): NotificationManager {
        return context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
