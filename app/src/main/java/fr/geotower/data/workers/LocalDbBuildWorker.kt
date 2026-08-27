package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.build.BuildPhase
import fr.geotower.data.build.BuildProgressUpdate
import fr.geotower.data.build.LocalDbBuildPipeline
import fr.geotower.data.build.labelRes
import fr.geotower.data.db.DbOperationTimings
import fr.geotower.utils.AppLogger
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.utils.AppNotifications
import fr.geotower.utils.NotificationIconResources
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
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

    private data class ProgressSnapshot(
        val percent: Int,
        val phaseOrdinal: Int,
        val detail: String,
        val importOrdinal: Int,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "db_download_channel"

    override suspend fun doWork(): Result = coroutineScope {
        createChannel()
        // Le réseau est nécessaire pour récupérer les sources, mais pas pour le traitement local.
        // Une contrainte WorkManager CONNECTED sur toute la tâche ferait arrêter un build d'une
        // heure dès qu'un téléphone perd brièvement le Wi-Fi ou le réseau mobile pendant le calcul.
        if (!hasUsableNetwork()) {
            setProgress(
                workDataOf(
                    KEY_PROGRESS to 0,
                    KEY_PHASE to BuildPhase.RESOLVING.ordinal,
                    KEY_DETAIL to context.getString(R.string.appstrings_local_build_waiting_network),
                ),
            )
            AppLogger.i(TAG, "Local build postponed: no validated network for source download")
            return@coroutineScope Result.retry()
        }
        // Chrono de generation (live pendant, duree finale apres) affiche par LocalDbBuildCard.
        DbOperationTimings.markStart(context, DbOperationTimings.LOCAL_BUILD)
        setForeground(createForegroundInfo(BuildPhase.RESOLVING, 0, null))

        // Un seul objet atomique evite de publier un ancien fichier avec une nouvelle phase pendant
        // la fenetre ou le ticker lit les donnees de progression.
        val progressSnapshot = AtomicReference(
            ProgressSnapshot(
                percent = 0,
                phaseOrdinal = BuildPhase.RESOLVING.ordinal,
                detail = "",
                importOrdinal = -1,
                fileName = "",
                downloadedBytes = 0L,
                totalBytes = -1L,
            ),
        )

        // Pousse la progression vers la carte (setProgress est suspend -> coroutine dediee).
        val ticker = launch {
            try {
                while (true) {
                    val snapshot = progressSnapshot.get()
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to snapshot.percent,
                            KEY_PHASE to snapshot.phaseOrdinal,
                            KEY_DETAIL to snapshot.detail,
                            KEY_IMPORT to snapshot.importOrdinal,
                            KEY_FILE to snapshot.fileName,
                            KEY_DOWNLOADED_BYTES to snapshot.downloadedBytes,
                            KEY_TOTAL_BYTES to snapshot.totalBytes,
                            KEY_PAUSED to OperationPauseStore.isPaused(context, OperationPauseStore.LOCAL_DB_BUILD),
                        ),
                    )
                    BuildPhase.values().getOrNull(snapshot.phaseOrdinal)?.let { phase ->
                        notifyLive(
                            phase,
                            snapshot.percent,
                            snapshot.detail,
                            OperationPauseStore.isPaused(context, OperationPauseStore.LOCAL_DB_BUILD),
                        )
                    }
                    delay(500)
                }
            } catch (_: CancellationException) {
                // Fin normale.
            }
        }

        // Un service au premier plan empeche la mort du process, mais PAS la suspension du CPU
        // ecran eteint : sans ce wake lock, un build lance puis range dans une poche est
        // interrompu en boucle et s'etire bien au-dela du necessaire. Plafond de securite pour ne
        // jamais laisser le verrou pris si le worker meurt sans passer par le `finally`.
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
        runCatching { wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS) }
            .onFailure { AppLogger.w(TAG, "Local build wake lock unavailable", it) }

        try {
            val packs = LocalDbBuildPipeline.Packs(
                mobile = inputData.getBoolean(KEY_PACK_MOBILE, true),
                radioBroadcast = inputData.getBoolean(KEY_PACK_RADIO_BROADCAST, true),
                nonMobileTech = inputData.getBoolean(KEY_PACK_NONMOBILE, true),
            )
            val force = inputData.getBoolean(KEY_FORCE, false)
            val result = LocalDbBuildPipeline().run(
                context = context,
                packs = packs,
                force = force,
                onProgress = { update: BuildProgressUpdate ->
                    val previous = progressSnapshot.get()
                    val snapshot = ProgressSnapshot(
                        percent = maxOf(previous.percent, update.percent.coerceIn(0, 100)),
                        phaseOrdinal = update.phase.ordinal,
                        detail = update.detail.orEmpty(),
                        importOrdinal = update.importType?.ordinal ?: -1,
                        fileName = update.fileName.orEmpty(),
                        downloadedBytes = update.downloadedBytes,
                        totalBytes = update.totalBytes,
                    )
                    progressSnapshot.set(snapshot)
                    notifyLive(
                        update.phase,
                        snapshot.percent,
                        update.detail,
                        OperationPauseStore.isPaused(context, OperationPauseStore.LOCAL_DB_BUILD),
                    )
                },
                onPause = {
                    OperationPauseStore.blockIfPaused(context, OperationPauseStore.LOCAL_DB_BUILD)
                },
            )

            ticker.cancel()
            ticker.join()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            if (result.success) {
                OperationPauseStore.clear(context, OperationPauseStore.LOCAL_DB_BUILD)
                DbOperationTimings.finish(context, DbOperationTimings.LOCAL_BUILD)
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_PHASE to BuildPhase.DONE.ordinal))
                showResult(success = true, reason = null)
                Result.success()
            } else {
                OperationPauseStore.clear(context, OperationPauseStore.LOCAL_DB_BUILD)
                DbOperationTimings.clearStart(context, DbOperationTimings.LOCAL_BUILD)
                AppLogger.w(TAG, "Local DB build failed: ${result.reason}")
                showResult(success = false, reason = result.reason)
                Result.failure(workDataOf(KEY_ERROR to (result.reason ?: "Cause inconnue")))
            }
        } catch (e: CancellationException) {
            DbOperationTimings.clearStart(context, DbOperationTimings.LOCAL_BUILD)
            ticker.cancel()
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            throw e
        } catch (e: Exception) {
            OperationPauseStore.clear(context, OperationPauseStore.LOCAL_DB_BUILD)
            DbOperationTimings.clearStart(context, DbOperationTimings.LOCAL_BUILD)
            ticker.cancel()
            AppLogger.w(TAG, "Local DB build worker crashed", e)
            cancelSafely(PROGRESS_NOTIFICATION_ID)
            showResult(success = false, reason = e.message)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        } finally {
            runCatching { if (wakeLock.isHeld) wakeLock.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun hasUsableNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun notifyLive(phase: BuildPhase, percent: Int, detail: String?, paused: Boolean) {
        notifySafely(PROGRESS_NOTIFICATION_ID, buildProgressNotification(phase, percent, detail, paused))
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
        val notification = buildProgressNotification(phase, percent, detail, paused = false)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun buildProgressNotification(
        phase: BuildPhase,
        percent: Int,
        detail: String?,
        paused: Boolean,
    ): android.app.Notification {
        val title = context.getString(R.string.appstrings_local_build_notif_title)
        val label = context.getString(phase.labelRes())
        val labelWithDetail = if (detail.isNullOrBlank()) label else "$label ($detail)"
        val content = if (paused) {
            context.getString(R.string.appstrings_operation_paused)
        } else {
            "$labelWithDetail — $percent %"
        }
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
        // Consigné même quand la notification ne part pas : voir [NotificationHistoryStore].
        NotificationHistoryStore.record(
            context = context,
            type = NotificationHistoryStore.TYPE_DB_LOCAL_BUILD,
            status = if (success) NotificationHistoryStore.STATUS_SUCCESS else NotificationHistoryStore.STATUS_ERROR,
            detail = reason.orEmpty(),
            target = "geotower://settings?section=db_local_build",
            posted = AppNotifications.canPost(context)
        )
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
        // La notification de fin suit l'interrupteur maître des notifications ; celle de progression
        // est celle du service de premier plan, imposée par Android pendant la génération.
        if (id != PROGRESS_NOTIFICATION_ID && !AppNotifications.canPost(context)) return
        runCatching { notificationManager.notify(id, notification) }
            .onFailure { AppLogger.w(TAG, "Local build notification failed", it) }
    }

    private fun cancelSafely(id: Int) {
        runCatching { notificationManager.cancel(id) }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "db_local_build"

        // Tags de pack : `WorkInfo` ne transporte pas les donnees d'entree, mais bien les tags. Ils
        // permettent aux ecrans de savoir QUELLE base est en cours de generation (et donc de ne pas
        // l'annoncer « manquante ») des l'etat ENQUEUED, avant la premiere progression.
        const val TAG_PACK_MOBILE = "local_build_pack_mobile"
        const val TAG_PACK_RADIO = "local_build_pack_radio"

        const val KEY_PROGRESS = "progress"
        const val KEY_PHASE = "phase"
        const val KEY_DETAIL = "detail"
        const val KEY_IMPORT = "import"
        const val KEY_FILE = "file"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_PAUSED = "paused"
        const val KEY_ERROR = "error"
        const val KEY_PACK_MOBILE = "pack_mobile"
        const val KEY_PACK_RADIO_BROADCAST = "pack_radio_broadcast"
        const val KEY_PACK_NONMOBILE = "pack_nonmobile"

        /** L'utilisateur a demande a generer malgre un appareil sous les budgets mesures. */
        const val KEY_FORCE = "force"

        private const val TAG = "GeoTowerDb"
        private const val PROGRESS_NOTIFICATION_ID = 471_001
        private const val RESULT_NOTIFICATION_ID = 471_002
        private const val WAKE_LOCK_TAG = "GeoTower:localDbBuild"

        /** Plafond de securite du wake lock : tres au-dela d'un build normal, mais borne. */
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L

        fun buildRequest(
            mobile: Boolean,
            radioBroadcast: Boolean,
            nonMobileTech: Boolean,
            force: Boolean = false,
        ) =
            OneTimeWorkRequestBuilder<LocalDbBuildWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PACK_MOBILE to mobile,
                        KEY_PACK_RADIO_BROADCAST to radioBroadcast,
                        KEY_PACK_NONMOBILE to nonMobileTech,
                        KEY_FORCE to force,
                    ),
                )
                // Le réseau est vérifié au démarrage, puis le traitement peut continuer hors ligne.
                // Ce backoff sert uniquement à attendre une connexion avant les téléchargements.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .apply {
                    if (mobile) addTag(TAG_PACK_MOBILE)
                    // Les deux packs non-mobiles alimentent la meme base radio.
                    if (radioBroadcast || nonMobileTech) addTag(TAG_PACK_RADIO)
                }
                .build()

        fun enqueue(
            workManager: WorkManager,
            mobile: Boolean = true,
            radioBroadcast: Boolean = true,
            nonMobileTech: Boolean = true,
            force: Boolean = false,
        ) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest(mobile, radioBroadcast, nonMobileTech, force),
            )
        }
    }
}
