package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geotower.BuildConfig
import fr.geotower.R
import fr.geotower.data.api.AppReleaseInfo
import fr.geotower.data.api.AppUpdateChecker
import fr.geotower.utils.AppLocale
import fr.geotower.utils.NotificationIconResources
import kotlinx.coroutines.sync.Mutex

object AppUpdateNotifier {
    private const val APP_UPDATE_ALERTS_CHANNEL_ID = "app_update_alerts_channel"
    private const val LAST_NOTIFIED_APP_RELEASE_KEY = "last_notified_app_release_id"
    private const val APP_UPDATE_BASELINE_INITIALIZED_KEY = "app_update_baseline_initialized"

    private val checkMutex = Mutex()

    suspend fun checkAndNotify(context: Context) {
        val appContext = context.applicationContext
        if (!UpdateCheckScheduler.areUpdateNotificationsEnabled(appContext)) return
        if (!checkMutex.tryLock()) return

        try {
            val prefs = appContext.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
            val latestRelease = AppUpdateChecker.getLatestRelease() ?: return
            val lastNotifiedReleaseId = prefs.getString(LAST_NOTIFIED_APP_RELEASE_KEY, "")

            if (latestRelease.releaseId == lastNotifiedReleaseId) return

            if (!AppUpdateChecker.isRemoteVersionNewer(latestRelease.versionName, BuildConfig.VERSION_NAME)) {
                prefs.edit()
                    .putBoolean(APP_UPDATE_BASELINE_INITIALIZED_KEY, true)
                    .putString(LAST_NOTIFIED_APP_RELEASE_KEY, latestRelease.releaseId)
                    .apply()
                return
            }

            val baselineInitialized = prefs.getBoolean(APP_UPDATE_BASELINE_INITIALIZED_KEY, false)
            if (!baselineInitialized && latestRelease.versionName == BuildConfig.VERSION_NAME) {
                prefs.edit()
                    .putBoolean(APP_UPDATE_BASELINE_INITIALIZED_KEY, true)
                    .putString(LAST_NOTIFIED_APP_RELEASE_KEY, latestRelease.releaseId)
                    .apply()
                return
            }

            showNotification(appContext, latestRelease)
            prefs.edit()
                .putBoolean(APP_UPDATE_BASELINE_INITIALIZED_KEY, true)
                .putString(LAST_NOTIFIED_APP_RELEASE_KEY, latestRelease.releaseId)
                .apply()
        } finally {
            checkMutex.unlock()
        }
    }

    private fun showNotification(context: Context, latestRelease: AppReleaseInfo) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val appLanguage = prefs.getString("app_language", AppLocale.LANGUAGE_SYSTEM) ?: AppLocale.LANGUAGE_SYSTEM
        val localizedContext = AppLocale.localizedContext(context, appLanguage)
        val languageTag = AppLocale.languageTagForPreference(appLanguage)
            ?: localizedContext.resources.configuration.locales[0]?.toLanguageTag()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                APP_UPDATE_ALERTS_CHANNEL_ID,
                localizedContext.getString(R.string.notification_app_updates_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latestRelease.downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DownloadNotificationCenter.APP_UPDATE_AVAILABLE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = localizedContext.getString(R.string.notification_app_update_available_desc, latestRelease.versionName)
        val expandedText = listOfNotNull(contentText, latestRelease.localizedNotes(languageTag))
            .joinToString(separator = "\n\n")

        val notification = NotificationCompat.Builder(context, APP_UPDATE_ALERTS_CHANNEL_ID)
            .setContentTitle(localizedContext.getString(R.string.notification_app_update_available_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        notificationManager.notify(DownloadNotificationCenter.APP_UPDATE_AVAILABLE_NOTIFICATION_ID, notification)
    }
}
