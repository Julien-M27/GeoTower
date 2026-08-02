package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.outages.OutageGenerationStep
import fr.geotower.data.outages.OutageLocalConfig
import fr.geotower.data.outages.labelRes
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Worker foreground (dataSync) de génération LOCALE des pannes. Affiche une notification **live**
 * (ongoing, barre de progression) détaillant l'étape en cours (téléchargement par opérateur,
 * géocodage, finalisation) et pousse aussi la progression vers la page de réglages (setProgress).
 *
 * Utilisé pour « Générer maintenant » (one-shot, [KEY_CHECK_ENABLED] = false) et pour la
 * planification périodique ([KEY_CHECK_ENABLED] = true → ne fait rien si le mode local/fond est off).
 */
class OutageGenerationWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "outage_generation_channel"

    override suspend fun doWork(): Result = coroutineScope {
        // Exécution périodique : ne rien faire si l'utilisateur a désactivé le mode local ou le fond.
        // La source locale se lit sur le niveau de traitement local, comme dans le planificateur.
        if (inputData.getBoolean(KEY_CHECK_ENABLED, false)) {
            val config = OutageLocalConfig(context)
            if (!fr.geotower.utils.AppConfig.outagesLocal() || !config.backgroundEnabled) {
                return@coroutineScope Result.success()
            }
        }

        createChannel()
        setForeground(createForegroundInfo(OutageGenerationStep.DOWNLOAD, 0, null))

        val stepOrdinal = AtomicInteger(OutageGenerationStep.DOWNLOAD.ordinal)
        val percentValue = AtomicInteger(0)
        val detailValue = AtomicReference("")

        val ticker = launch {
            try {
                while (true) {
                    setProgress(
                        workDataOf(
                            KEY_PERCENT to percentValue.get(),
                            KEY_STEP to stepOrdinal.get(),
                            KEY_DETAIL to detailValue.get(),
                        ),
                    )
                    delay(500)
                }
            } catch (_: CancellationException) {
                // Fin normale.
            }
        }

        try {
            val repository = AnfrRepository(RetrofitClient.apiService, context)
            val sites = repository.regenerateLocalSitesHs { step, percent, detail ->
                stepOrdinal.set(step.ordinal)
                percentValue.set(percent)
                detailValue.set(detail.orEmpty())
                notifySafely(PROGRESS_NOTIFICATION_ID, buildProgressNotification(step, percent, detail))
            }
            ticker.cancel()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            // Le détail par opérateur affiché dans les réglages est persisté par la génération
            // elle-même (OutageLocalConfig.recordGeneration) : ici il ne sert qu'à la notification.
            val breakdown = OutageLocalConfig.breakdownOf(sites)
            setProgress(
                workDataOf(
                    KEY_PERCENT to 100,
                    KEY_STEP to OutageGenerationStep.DONE.ordinal,
                    KEY_COUNT to sites.size,
                ),
            )
            showResult(sites.size, breakdown.joinToString("\n") { (operateur, nombre) -> "$operateur : $nombre" })
            Result.success(workDataOf(KEY_COUNT to sites.size))
        } catch (e: CancellationException) {
            ticker.cancel()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            throw e
        } catch (e: Exception) {
            ticker.cancel()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            AppLogger.w(TAG, "Outage generation worker crashed", e)
            Result.retry()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.outage_gen_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun createForegroundInfo(step: OutageGenerationStep, percent: Int, detail: String?): ForegroundInfo {
        val notification = buildProgressNotification(step, percent, detail)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgressNotification(step: OutageGenerationStep, percent: Int, detail: String?): android.app.Notification {
        val label = context.getString(step.labelRes())
        val labelWithDetail = if (detail.isNullOrBlank()) label else "$label ($detail)"
        val cancelLabel = context.getString(R.string.database_cancel_download)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.outage_gen_notif_title))
            .setContentText("$labelWithDetail — $percent %")
            .setContentIntent(settingsPendingIntent())
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(NotificationIconResources.smallIconRes(context), cancelLabel, cancelIntent)
        NotificationIconResources.applyTo(builder, context)
        return builder.build()
    }

    private fun showResult(count: Int, breakdown: String) {
        val content = context.getString(R.string.outage_gen_notif_done, count)
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.outage_gen_notif_title))
            .setContentText(content)
            .setContentIntent(settingsPendingIntent())
            .setAutoCancel(true)
        if (breakdown.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$content\n$breakdown"))
        }
        NotificationIconResources.applyTo(builder, context)
        notifySafely(RESULT_NOTIFICATION_ID, builder.build())
    }

    private fun settingsPendingIntent(): PendingIntent {
        // La récupération des pannes se pilote depuis « Traitement local » (niveau ≥ 1) : on ouvre
        // cette page, pas la racine des réglages.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("geotower://local_mode")
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        // La notification de fin suit l'interrupteur maître des notifications ; celle de progression
        // est celle du service de premier plan, imposée par Android pendant la récupération.
        if (id != PROGRESS_NOTIFICATION_ID && !AppNotifications.canPost(context)) return
        runCatching { notificationManager.notify(id, notification) }
            .onFailure { AppLogger.w(TAG, "Outage generation notification failed", it) }
    }

    private fun cancelSafely(id: Int) {
        runCatching { notificationManager.cancel(id) }
    }

    companion object {
        const val KEY_CHECK_ENABLED = "check_enabled"
        const val KEY_STEP = "step"
        const val KEY_PERCENT = "percent"
        const val KEY_DETAIL = "detail"
        const val KEY_COUNT = "count"

        private const val TAG = "OutageGeneration"
        private const val PROGRESS_NOTIFICATION_ID = 472_001
        private const val RESULT_NOTIFICATION_ID = 472_002
    }
}
