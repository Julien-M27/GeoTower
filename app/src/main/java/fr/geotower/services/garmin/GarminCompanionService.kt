package fr.geotower.services.garmin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import fr.geotower.MainActivity
import fr.geotower.utils.AppFileLog
import fr.geotower.utils.NotificationIconResources

/**
 * Service de premier plan pour maintenir la liaison Bluetooth Low Energy avec la montre Garmin
 * active même lorsque le smartphone est verrouillé dans la poche (écran éteint) ou l'application fermée.
 *
 * Ce service passif ne réalise aucun calcul permanent : le processeur reste en veille (Deep Sleep)
 * et n'interroge la base locale SQLite que les quelques millisecondes nécessaires lorsqu'une
 * requête radio est émise par la montre.
 */
class GarminCompanionService : Service() {

    companion object {
        const val CHANNEL_ID = "garmin_companion_channel_v2"
        const val NOTIFICATION_ID = 9650

        fun start(context: Context) {
            val intent = Intent(context, GarminCompanionService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppFileLog.e("GarminBridge", "Impossible de démarrer GarminCompanionService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GarminCompanionService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                AppFileLog.e("GarminBridge", "Impossible d'arrêter GarminCompanionService", e)
            }
        }
    }

    private val autoStopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        AppFileLog.i("GarminBridge", "Durée programmée de liaison écoulée, arrêt automatique")
        GarminCompanionManager.setBackgroundServiceEnabled(applicationContext, false)
        stopSelf()
    }

    private val tickerHandler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            val durationMs = GarminCompanionManager.backgroundDurationMs.value
            if (durationMs > 0L) {
                updateNotification()
                tickerHandler.postDelayed(this, 30_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            AppFileLog.i("GarminBridge", "GarminCompanionService actif en premier plan")
        } catch (e: Exception) {
            AppFileLog.e("GarminBridge", "Erreur startForeground GarminCompanionService", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMs = GarminCompanionManager.backgroundDurationMs.value
        val startTime = GarminCompanionManager.serviceStartTimeMs.value
        autoStopHandler.removeCallbacks(autoStopRunnable)
        tickerHandler.removeCallbacks(tickerRunnable)

        if (durationMs > 0L && startTime > 0L) {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = durationMs - elapsed
            if (remaining <= 0) {
                AppFileLog.i("GarminBridge", "Durée programmée déjà expirée, arrêt immédiat")
                GarminCompanionManager.setBackgroundServiceEnabled(applicationContext, false)
                stopSelf()
                return START_NOT_STICKY
            } else {
                AppFileLog.i("GarminBridge", "Arrêt automatique programmé dans ${remaining / 1000}s")
                autoStopHandler.postDelayed(autoStopRunnable, remaining)
                tickerHandler.postDelayed(tickerRunnable, 30_000L)
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        autoStopHandler.removeCallbacks(autoStopRunnable)
        tickerHandler.removeCallbacks(tickerRunnable)
        AppFileLog.i("GarminBridge", "GarminCompanionService arrêté")
        if (GarminCompanionManager.isBackgroundServiceEnabled.value) {
            val durationMs = GarminCompanionManager.backgroundDurationMs.value
            val startTime = GarminCompanionManager.serviceStartTimeMs.value
            val isExpired = durationMs > 0L && startTime > 0L && (System.currentTimeMillis() - startTime) >= durationMs
            if (!isExpired) {
                AppFileLog.i("GarminBridge", "Relance automatique du service GarminCompanionService")
                start(applicationContext)
            } else {
                GarminCompanionManager.setBackgroundServiceEnabled(applicationContext, false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            try {
                manager?.deleteNotificationChannel("garmin_companion_channel")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Liaison montre Garmin",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Maintient la liaison Bluetooth avec la montre Garmin lorsque l'écran est éteint."
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun formatRemainingTime(remainingMs: Long): String {
        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_SHOW_GARMIN_SETTINGS"
            putExtra("widget_dest", "garmin")
            data = android.net.Uri.parse("geotower://settings?section=garmin")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildStandardNotification(pendingIntent: PendingIntent): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTower • Montre Garmin")
            .setContentText("Liaison active • Durée : Illimité (écran éteint)")
            .setSubText("Illimité")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)

        NotificationIconResources.applyTo(builder, this)
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveNotificationBaklava(
        pendingIntent: PendingIntent,
        progress: Int,
        remainingText: String
    ): Notification {
        val progressStyle = Notification.ProgressStyle()
            .setProgress(progress)
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(100).setColor(0xFF00A0E2.toInt()))
            )
        val nativeBuilder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTower • Montre Garmin")
            .setContentText("Liaison active • Reste $remainingText")
            .setSubText(remainingText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setStyle(progressStyle)
            .setColor(0xFF00A0E2.toInt())

        NotificationIconResources.applyTo(nativeBuilder, this)

        runCatching {
            Notification.Builder::class.java
                .getMethod("setForegroundServiceBehavior", Int::class.javaPrimitiveType)
                .invoke(nativeBuilder, Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        runCatching {
            Notification.Builder::class.java
                .getMethod("setShortCriticalText", CharSequence::class.java)
                .invoke(nativeBuilder, remainingText)
        }
        runCatching {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(nativeBuilder, true)
        }
        return nativeBuilder.build().apply {
            extras.putBoolean("android.requestPromotedOngoing", true)
            extras.putString("android.shortCriticalText", remainingText)
        }
    }

    private fun buildLiveNotificationCompat(
        pendingIntent: PendingIntent,
        progress: Int,
        remainingText: String
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTower • Montre Garmin")
            .setContentText("Liaison active • Reste $remainingText")
            .setSubText(remainingText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress, false)
            .setColor(0xFF00A0E2.toInt())

        NotificationIconResources.applyTo(builder, this)
        return builder.build().apply {
            extras.putBoolean("android.requestPromotedOngoing", true)
            extras.putString("android.shortCriticalText", remainingText)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = getPendingIntent()
        val durationMs = GarminCompanionManager.backgroundDurationMs.value
        if (durationMs <= 0L) {
            return buildStandardNotification(pendingIntent)
        }

        val startTime = GarminCompanionManager.serviceStartTimeMs.value
        val elapsed = if (startTime > 0L) (System.currentTimeMillis() - startTime).coerceAtLeast(0L) else 0L
        val remaining = (durationMs - elapsed).coerceAtLeast(0L)
        val progress = ((elapsed.toDouble() / durationMs.toDouble()) * 100).toInt().coerceIn(0, 100)
        val remainingText = formatRemainingTime(remaining)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildLiveNotificationBaklava(pendingIntent, progress, remainingText)
        } else {
            buildLiveNotificationCompat(pendingIntent, progress, remainingText)
        }
    }
}
