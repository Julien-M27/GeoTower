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
import fr.geotower.data.api.EnbDatabaseDownloader
import fr.geotower.data.api.RadioDatabaseDownloader
import fr.geotower.data.build.LocalDbRebuildOffer
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.db.EnbDatabaseValidator
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.utils.NotificationIconResources

class UpdateCheckWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private companion object {
        const val UPDATE_ALERTS_CHANNEL_ID = "update_alerts_channel"
        const val PREF_LAST_NOTIFIED_MOBILE_VERSION = "last_notified_db_version"
        const val PREF_LAST_NOTIFIED_RADIO_VERSION = "last_notified_radio_db_version"
        const val PREF_LAST_NOTIFIED_ENB_VERSION = "last_notified_enb_db_version"
    }

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

        // 1. Si l'utilisateur a désactivé l'option, on ne fait rien
        if (!UpdateCheckScheduler.areUpdateNotificationsEnabled(context)) {
            return Result.success()
        }

        val canCheckApp = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.APP_UPDATE_CHECK) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.APP_UPDATE_CHECK)
        // « Autonomie maximale » sur un appareil qui génère ses bases : aucune version distante ne
        // sera servie. Sans cette garde, le worker prenait le null pour une panne réseau et
        // repartait en Result.retry() en boucle. Sur un appareil inéligible à la génération, le
        // manifeste reste lu (la base vient forcément du serveur) : la vérification a un sens.
        val canCheckDatabase = RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_UPDATE_CHECK) &&
            RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.DATABASE_UPDATE_CHECK) &&
            !fr.geotower.utils.AppConfig.blockServerDatabase()

        if (canCheckApp) {
            AppUpdateNotifier.checkAndNotify(context)
        }

        if (!canCheckDatabase) {
            UpdateCheckScheduler.scheduleNextAfterSuccessfulRun(context)
            return Result.success()
        }

        // Une indisponibilite du manifeste rend aussi les autres bases illisibles : on conserve le
        // retry historique pour ne pas repousser toute la verification au lendemain.
        val remote = DatabaseDownloader.getLatestDatabaseUpdateInfo() ?: return Result.retry()
        if (remote.version.isNullOrBlank() && remote.sha256.isBlank()) {
            UpdateCheckScheduler.scheduleNextAfterSuccessfulRun(context)
            return Result.success()
        }

        checkMobileUpdate(prefs, remote)
        checkRadioUpdate(prefs)
        checkEnbUpdate(prefs)

        UpdateCheckScheduler.scheduleNextAfterSuccessfulRun(context)
        return Result.success()
    }

    private fun checkMobileUpdate(
        prefs: android.content.SharedPreferences,
        remote: DatabaseDownloader.UpdateInfo
    ) {
        val localFileStatus = GeoTowerDatabaseValidator.getInstalledDatabaseFileStatus(context)
        val localVersion = if (localFileStatus.state != GeoTowerDatabaseValidator.LocalDatabaseState.MISSING) {
            GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)
        } else {
            null
        }
        if (localFileStatus.state != GeoTowerDatabaseValidator.LocalDatabaseState.MISSING) {
            GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context)
        }

        if (!DatabaseDownloader.isRemoteDatabaseUpdateAvailable(context, remote, localVersion)) {
            prefs.edit().putString(PREF_LAST_NOTIFIED_MOBILE_VERSION, remote.notificationIdentity()).apply()
            return
        }

        // 3. On regarde si on l'a déjà notifié pour CETTE version précise
        // (Pour ne pas le spammer tous les jours avec la même mise à jour)
        val lastNotified = prefs.getString(PREF_LAST_NOTIFIED_MOBILE_VERSION, "")

        if (shouldNotifyMobile(remote, localVersion, lastNotified)) {
            // Base générée sur l'appareil : on annonce la mise à jour, mais on l'oriente vers une
            // régénération locale plutôt que vers le téléchargement de la base du serveur.
            val rebuild = LocalDbRebuildOffer.forMobile(context)
            showNotification(
                type = NotificationHistoryStore.TYPE_DB_MOBILE,
                section = if (rebuild) "db_local_build" else "db_mobile",
                notificationId = DownloadNotificationCenter.DB_UPDATE_AVAILABLE_NOTIFICATION_ID,
                rebuild = rebuild
            )
            // On sauvegarde qu'on l'a prévenu pour cette version
            prefs.edit().putString(PREF_LAST_NOTIFIED_MOBILE_VERSION, remote.notificationIdentity()).apply()
        }
    }

    private fun shouldNotifyMobile(
        remote: DatabaseDownloader.UpdateInfo,
        localVersion: String?,
        lastNotified: String?
    ): Boolean {
        val version = remote.version
        return if (version != null) {
            DatabaseVersionPolicy.shouldNotify(version, localVersion, lastNotified)
        } else {
            remote.notificationIdentity() != lastNotified
        }
    }

    private fun DatabaseDownloader.UpdateInfo.notificationIdentity(): String =
        version?.takeIf { it.isNotBlank() } ?: sha256

    private suspend fun checkRadioUpdate(prefs: android.content.SharedPreferences) {
        // Une base radio construite localement est mise a jour avec le build ANFR global : la
        // notification mobile l'oriente deja vers cette action, il ne faut pas lui proposer en plus
        // un telechargement qui ecraserait sa provenance.
        if (fr.geotower.utils.AppConfig.dbForcedLocal()) return

        val remoteVersion = RadioDatabaseDownloader.getLatestDatabaseVersion() ?: return
        if (remoteVersion.isBlank()) return

        val localVersion = RadioDatabaseValidator.getInstalledDatabaseVersion(context)
        if (!DatabaseVersionPolicy.isRemoteNewer(remoteVersion, localVersion)) {
            prefs.edit().putString(PREF_LAST_NOTIFIED_RADIO_VERSION, remoteVersion).apply()
            return
        }

        val lastNotified = prefs.getString(PREF_LAST_NOTIFIED_RADIO_VERSION, "")
        if (DatabaseVersionPolicy.shouldNotify(remoteVersion, localVersion, lastNotified)) {
            showNotification(
                type = NotificationHistoryStore.TYPE_DB_RADIO,
                section = "db_radio",
                notificationId = DownloadNotificationCenter.RADIO_DB_UPDATE_AVAILABLE_NOTIFICATION_ID
            )
            prefs.edit().putString(PREF_LAST_NOTIFIED_RADIO_VERSION, remoteVersion).apply()
        }
    }

    private suspend fun checkEnbUpdate(prefs: android.content.SharedPreferences) {
        if (
            fr.geotower.utils.AppConfig.blockCommunityAndUpdates() ||
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.ENB_DATABASE)
        ) {
            return
        }

        val remoteVersion = EnbDatabaseDownloader.getLatestDatabaseVersion() ?: return
        if (remoteVersion.isBlank()) return

        val localVersion = EnbDatabaseValidator.getInstalledDatabaseVersion(context)
        // La version eNB contient un digest : une egalite stricte detecte les changements que le
        // comparateur date-only ne verrait pas.
        if (remoteVersion == localVersion) {
            prefs.edit().putString(PREF_LAST_NOTIFIED_ENB_VERSION, remoteVersion).apply()
            return
        }

        val lastNotified = prefs.getString(PREF_LAST_NOTIFIED_ENB_VERSION, "")
        if (remoteVersion != lastNotified) {
            showNotification(
                type = NotificationHistoryStore.TYPE_DB_ENB,
                section = "db_enb",
                notificationId = DownloadNotificationCenter.ENB_DB_UPDATE_AVAILABLE_NOTIFICATION_ID
            )
            prefs.edit().putString(PREF_LAST_NOTIFIED_ENB_VERSION, remoteVersion).apply()
        }
    }

    private fun showNotification(
        type: String,
        section: String,
        notificationId: Int,
        rebuild: Boolean = false
    ) {
        val databaseName = context.getString(databaseNameResource(type))
        // Consigné avant le garde-fou : l'appelant note de toute façon la version comme annoncée,
        // donc sans cette ligne une mise à jour signalée notifications coupées ne laisserait
        // aucune trace nulle part.
        NotificationHistoryStore.record(
            context = context,
            type = type,
            status = NotificationHistoryStore.STATUS_INFO,
            detail = if (rebuild) NotificationHistoryStore.DETAIL_DB_UPDATE_REBUILD else "",
            target = "geotower://settings?section=$section",
            posted = fr.geotower.utils.AppNotifications.canPost(context)
        )
        if (!fr.geotower.utils.AppNotifications.canPost(context)) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(UPDATE_ALERTS_CHANNEL_ID, context.getString(R.string.notification_db_updates_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geotower://settings?section=$section")).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_ALERTS_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_db_update_available_title, databaseName))
            .setContentText(
                context.getString(
                    if (rebuild) R.string.notification_db_update_available_desc_rebuild
                    else R.string.notification_db_update_available_desc,
                    databaseName
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .let { NotificationIconResources.applyTo(it, context) }
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun databaseNameResource(type: String): Int = when (type) {
        NotificationHistoryStore.TYPE_DB_RADIO -> R.string.notification_history_type_db_radio
        NotificationHistoryStore.TYPE_DB_ENB -> R.string.notification_history_type_db_enb
        else -> R.string.notification_history_type_db_mobile
    }

}
