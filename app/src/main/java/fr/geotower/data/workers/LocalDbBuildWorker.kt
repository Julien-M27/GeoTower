package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.build.BuildPhase
import fr.geotower.data.build.LocalDbBuildPipeline
import fr.geotower.data.build.labelRes
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.utils.AppLogger
import fr.geotower.utils.NotificationIconResources
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Worker foreground (dataSync) de generation locale de `geotower_fr.db`. Enveloppe
 * [LocalDbBuildPipeline] et affiche une notification **live** (ongoing, promue Now Bar sur A16)
 * qui detaille la phase en cours et le pourcentage. En cas d'echec, la notification indique la
 * **vraie** cause. Un ticker pousse aussi la progression vers la carte des reglages (setProgress).
 */
class LocalDbBuildWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "db_download_channel"

    override suspend fun doWork(): Result = coroutineScope {
        createChannel()
        // Chrono de generation (live pendant, duree finale apres) affiche par LocalDbBuildCard.
        DbOperationTimings.markStart(context, DbOperationTimings.LOCAL_BUILD)
        setForeground(createForegroundInfo(BuildPhase.RESOLVING, 0, null))

        val phaseOrdinal = AtomicInteger(BuildPhase.RESOLVING.ordinal)
        val percentValue = AtomicInteger(0)
        // Detail « live » de l'etape en cours (ex. compteur de lignes) pousse aussi vers la carte,
        // afin qu'une phase longue (calcul des masques) affiche un mouvement continu, pas un % fige.
        val detailValue = AtomicReference("")

        // Pousse la progression vers la carte (setProgress est suspend -> coroutine dediee).
        val ticker = launch {
            try {
                while (true) {
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to percentValue.get(),
                            KEY_PHASE to phaseOrdinal.get(),
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
            val packs = LocalDbBuildPipeline.Packs(
                mobile = inputData.getBoolean(KEY_PACK_MOBILE, true),
                radioBroadcast = inputData.getBoolean(KEY_PACK_RADIO_BROADCAST, true),
                nonMobileTech = inputData.getBoolean(KEY_PACK_NONMOBILE, true),
            )
            val result = LocalDbBuildPipeline().run(context, packs) { phase, percent, detail ->
                phaseOrdinal.set(phase.ordinal)
                percentValue.set(percent)
                detailValue.set(detail.orEmpty())
                notifyLive(phase, percent, detail)
            }

            ticker.cancel()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            if (result.success) {
                DbOperationTimings.finish(context, DbOperationTimings.LOCAL_BUILD)
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_PHASE to BuildPhase.DONE.ordinal))
                showResult(success = true, reason = null)
                Result.success()
            } else {
                DbOperationTimings.clearStart(context, DbOperationTimings.LOCAL_BUILD)
                AppLogger.w(TAG, "Local DB build failed: ${result.reason}")
                showResult(success = false, reason = result.reason)
                Result.failure()
            }
        } catch (e: CancellationException) {
            DbOperationTimings.clearStart(context, DbOperationTimings.LOCAL_BUILD)
            ticker.cancel()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            throw e
        } catch (e: Exception) {
            DbOperationTimings.clearStart(context, DbOperationTimings.LOCAL_BUILD)
            ticker.cancel()
            AppLogger.w(TAG, "Local DB build worker crashed", e)
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            showResult(success = false, reason = e.message)
            Result.failure()
        }
    }

    private fun notifyLive(phase: BuildPhase, percent: Int, detail: String?) {
        notifySafely(PROGRESS_NOTIFICATION_ID, buildProgressNotification(phase, percent, detail))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_database_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(phase: BuildPhase, percent: Int, detail: String?): ForegroundInfo {
        val notification = buildProgressNotification(phase, percent, detail)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgressNotification(phase: BuildPhase, percent: Int, detail: String?): android.app.Notification {
        val title = context.getString(R.string.appstrings_local_build_notif_title)
        val label = context.getString(phase.labelRes())
        val labelWithDetail = if (detail.isNullOrBlank()) label else "$label ($detail)"
        val content = "$labelWithDetail — $percent %"
        val cancelLabel = context.getString(R.string.database_cancel_download)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val actionIcon = NotificationIconResources.smallIconRes(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = android.app.Notification.ProgressStyle()
                .setProgress(percent)
                .setProgressSegments(listOf(android.app.Notification.ProgressStyle.Segment(100)))
            val nativeBuilder = android.app.Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(settingsPendingIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_PROGRESS)
                .setStyle(progressStyle)
                .addAction(
                    android.app.Notification.Action.Builder(
                        Icon.createWithResource(context, actionIcon), cancelLabel, cancelIntent,
                    ).build(),
                )
            NotificationIconResources.applyTo(nativeBuilder, context)
            runCatching {
                android.app.Notification.Builder::class.java
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(nativeBuilder, "$percent %")
            }
            runCatching {
                android.app.Notification.Builder::class.java
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(nativeBuilder, true)
            }
            return nativeBuilder.build().apply {
                extras.putBoolean("android.requestPromotedOngoing", true)
                extras.putString("android.shortCriticalText", "$percent %")
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(settingsPendingIntent())
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(actionIcon, cancelLabel, cancelIntent)
        NotificationIconResources.applyTo(builder, context)
        return builder.build()
    }

    private fun showResult(success: Boolean, reason: String?) {
        val title = context.getString(R.string.appstrings_local_build_notif_title)
        val content = if (success) {
            context.getString(R.string.appstrings_local_build_notif_done)
        } else {
            context.getString(R.string.appstrings_local_build_notif_failed, reason ?: "?")
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(settingsPendingIntent())
            .setAutoCancel(true)
        NotificationIconResources.applyTo(builder, context)
        notifySafely(RESULT_NOTIFICATION_ID, builder.build())
    }

    private fun settingsPendingIntent(): PendingIntent {
        // Cible le bloc « generation locale » (LocalDbBuildCard) precisement, pas seulement le haut
        // de la section « Base de donnees ».
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=db_local_build")
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        runCatching { notificationManager.notify(id, notification) }
            .onFailure { AppLogger.w(TAG, "Local build notification failed", it) }
    }

    private fun cancelSafely(id: Int) {
        runCatching { notificationManager.cancel(id) }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "db_local_build"
        const val KEY_PROGRESS = "progress"
        const val KEY_PHASE = "phase"
        const val KEY_DETAIL = "detail"
        const val KEY_PACK_MOBILE = "pack_mobile"
        const val KEY_PACK_RADIO_BROADCAST = "pack_radio_broadcast"
        const val KEY_PACK_NONMOBILE = "pack_nonmobile"

        private const val TAG = "GeoTowerDb"
        private const val PROGRESS_NOTIFICATION_ID = 471_001
        private const val RESULT_NOTIFICATION_ID = 471_002

        fun buildRequest(mobile: Boolean, radioBroadcast: Boolean, nonMobileTech: Boolean) =
            OneTimeWorkRequestBuilder<LocalDbBuildWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(
                    workDataOf(
                        KEY_PACK_MOBILE to mobile,
                        KEY_PACK_RADIO_BROADCAST to radioBroadcast,
                        KEY_PACK_NONMOBILE to nonMobileTech,
                    ),
                )
                .build()

        fun enqueue(
            workManager: WorkManager,
            mobile: Boolean = true,
            radioBroadcast: Boolean = true,
            nonMobileTech: Boolean = true,
        ) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest(mobile, radioBroadcast, nonMobileTech),
            )
        }
    }
}
