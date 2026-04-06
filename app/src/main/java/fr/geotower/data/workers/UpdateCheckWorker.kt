package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.utils.AppStrings

class UpdateCheckWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

        // 1. Si l'utilisateur a désactivé l'option, on ne fait rien
        if (!prefs.getBoolean("enable_update_notifications", false)) {
            return Result.success()
        }

        // 2. On interroge discrètement ton serveur
        val remoteVersion = DatabaseDownloader.getLatestDatabaseVersion() ?: return Result.retry()

        // 3. On regarde si on l'a déjà notifié pour CETTE version précise
        // (Pour ne pas le spammer tous les jours avec la même mise à jour)
        val lastNotified = prefs.getString("last_notified_db_version", "")

        if (remoteVersion != lastNotified && remoteVersion.isNotBlank()) {
            showNotification()
            // On sauvegarde qu'on l'a prévenu pour cette version
            prefs.edit().putString("last_notified_db_version", remoteVersion).apply()
        }

        return Result.success()
    }

    private fun showNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "update_alerts_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Mises à jour Base de données", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // Si l'utilisateur clique, ça ouvre l'application
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentTitle(AppStrings.newDbNotifTitle(context))
            .setContentText(AppStrings.newDbNotifDesc(context))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}