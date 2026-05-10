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
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.utils.AppStrings

class UpdateCheckWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

        // 1. Si l'utilisateur a désactivé l'option, on ne fait rien
        if (!UpdateCheckScheduler.areUpdateNotificationsEnabled(context)) {
            return Result.success()
        }

        // 2. On interroge discrètement ton serveur
        val remoteVersion = DatabaseDownloader.getLatestDatabaseVersion() ?: return Result.retry()
        if (remoteVersion.isBlank()) {
            UpdateCheckScheduler.scheduleNextAfterSuccessfulRun(context)
            return Result.success()
        }

        val localStatus = GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context)
        val localVersion = if (localStatus.state == GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
            GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
        } else {
            null
        }

        if (!DatabaseVersionPolicy.isRemoteNewer(remoteVersion, localVersion)) {
            prefs.edit().putString("last_notified_db_version", remoteVersion).apply()
            UpdateCheckScheduler.scheduleNextAfterSuccessfulRun(context)
            return Result.success()
        }

        // 3. On regarde si on l'a déjà notifié pour CETTE version précise
        // (Pour ne pas le spammer tous les jours avec la même mise à jour)
        val lastNotified = prefs.getString("last_notified_db_version", "")

        if (DatabaseVersionPolicy.shouldNotify(remoteVersion, localVersion, lastNotified)) {
            showNotification()
            // On sauvegarde qu'on l'a prévenu pour cette version
            prefs.edit().putString("last_notified_db_version", remoteVersion).apply()
        }

        UpdateCheckScheduler.scheduleNextAfterSuccessfulRun(context)
        return Result.success()
    }

    private fun showNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "update_alerts_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, AppStrings.newDbChannelName(context), NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geotower://settings?section=database")).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 2001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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
