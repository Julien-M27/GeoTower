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
import android.os.IBinder
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
        const val CHANNEL_ID = "garmin_companion_channel"
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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AppFileLog.i("GarminBridge", "GarminCompanionService arrêté")
        if (GarminCompanionManager.isBackgroundServiceEnabled.value) {
            AppFileLog.i("GarminBridge", "Relance automatique du service GarminCompanionService (mode illimité)")
            start(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Liaison montre Garmin",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient la liaison Bluetooth avec la montre Garmin lorsque l'écran est éteint."
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_SHOW_GARMIN_SETTINGS"
            putExtra("widget_dest", "garmin")
            data = android.net.Uri.parse("geotower://settings?section=garmin")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTower • Montre Garmin")
            .setContentText("Liaison active • Durée illimitée (écran éteint)")
            .setSubText("En continu")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)

        NotificationIconResources.applyTo(builder, this)
        return builder.build()
    }
}
