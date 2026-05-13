package fr.geotower.data.workers

import android.app.NotificationManager
import android.content.Context
import android.os.Build

object DownloadNotificationCenter {
    const val DB_UPDATE_AVAILABLE_NOTIFICATION_ID = 2001
    const val APP_UPDATE_AVAILABLE_NOTIFICATION_ID = 2002
    const val DB_DOWNLOAD_PROGRESS_NOTIFICATION_ID = 2101
    const val DB_DOWNLOAD_RESULT_NOTIFICATION_ID = 2102
    const val MAP_DOWNLOAD_CHANNEL_ID = "map_download_channel"

    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val KEY_MAP_DOWNLOAD_NOTIFICATION_IDS = "map_download_notification_ids"
    private const val KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES = "map_download_notification_filenames"
    private const val MAP_DOWNLOAD_RESULT_ID_MASK = 0x4D415000

    fun mapDownloadNotificationId(mapFilename: String): Int = mapFilename.hashCode()
    fun mapDownloadResultNotificationId(mapFilename: String): Int = mapFilename.hashCode() xor MAP_DOWNLOAD_RESULT_ID_MASK

    fun rememberMapDownloadNotification(context: Context, mapFilename: String) {
        rememberMapDownloadNotification(context, mapDownloadNotificationId(mapFilename))
        rememberMapDownloadNotification(context, mapDownloadResultNotificationId(mapFilename))

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val filenames = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES, emptySet())
            .orEmpty()
            .toMutableSet()

        if (filenames.add(mapFilename)) {
            prefs.edit()
                .putStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES, filenames)
                .apply()
        }
    }

    fun rememberMapDownloadNotification(context: Context, notificationId: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationIds = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, emptySet())
            .orEmpty()
            .toMutableSet()

        if (notificationIds.add(notificationId.toString())) {
            prefs.edit()
                .putStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, notificationIds)
                .apply()
        }
    }

    fun clearDatabaseSectionNotifications(context: Context) {
        val manager = notificationManager(context)
        manager.cancel(DB_UPDATE_AVAILABLE_NOTIFICATION_ID)
        manager.cancel(DB_DOWNLOAD_RESULT_NOTIFICATION_ID)
    }

    fun clearOfflineMapNotifications(context: Context, mapFilename: String) {
        val manager = notificationManager(context)
        val notificationIds = setOf(
            mapDownloadNotificationId(mapFilename),
            mapDownloadResultNotificationId(mapFilename)
        )

        notificationIds.forEach { manager.cancel(it) }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedNotificationIds = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, emptySet())
            .orEmpty()
            .toMutableSet()
            .apply { removeAll(notificationIds.map { it.toString() }.toSet()) }
        val storedFilenames = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES, emptySet())
            .orEmpty()
            .toMutableSet()
            .apply { remove(mapFilename) }

        prefs.edit()
            .putStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, storedNotificationIds)
            .putStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES, storedFilenames)
            .apply()
    }

    fun clearOfflineMapResultNotification(context: Context, mapFilename: String) {
        val resultNotificationId = mapDownloadResultNotificationId(mapFilename)
        notificationManager(context).cancel(resultNotificationId)

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedNotificationIds = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, emptySet())
            .orEmpty()
            .toMutableSet()
            .apply { remove(resultNotificationId.toString()) }
        val storedFilenames = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES, emptySet())
            .orEmpty()
            .toMutableSet()
            .apply { remove(mapFilename) }

        prefs.edit()
            .putStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, storedNotificationIds)
            .putStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES, storedFilenames)
            .apply()
    }

    fun clearOfflineMapsSectionNotifications(context: Context) {
        val manager = notificationManager(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedNotificationIds = prefs
            .getStringSet(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        storedNotificationIds.forEach { manager.cancel(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                manager.activeNotifications
                    .filter { statusBarNotification ->
                        storedNotificationIds.contains(statusBarNotification.id) ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                statusBarNotification.notification.channelId == MAP_DOWNLOAD_CHANNEL_ID)
                    }
                    .forEach { manager.cancel(it.id) }
            }
        }

        prefs.edit()
            .remove(KEY_MAP_DOWNLOAD_NOTIFICATION_IDS)
            .remove(KEY_MAP_DOWNLOAD_NOTIFICATION_FILENAMES)
            .apply()
    }

    private fun notificationManager(context: Context): NotificationManager {
        return context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
