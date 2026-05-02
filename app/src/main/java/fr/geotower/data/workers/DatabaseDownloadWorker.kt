package fr.geotower.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.data.api.DatabaseDownloader
import android.content.pm.ServiceInfo
import java.util.Locale

class DatabaseDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "db_download_channel"
    private val notificationId = 1001

    // ✅ NOUVEAU : Une petite fonction classique (Non-Composable) pour traduire en arrière-plan
    private fun getLocalString(fr: String, en: String, es: String): String {
        return when (Locale.getDefault().language) {
            "fr" -> fr
            "es" -> es
            else -> en
        }
    }

    override suspend fun doWork(): Result {
        createChannel()

        // 1. Démarrer en premier plan
        setForeground(createForegroundInfo(0))

        // 2. Lancer le téléchargement
        val success = DatabaseDownloader.downloadUpdate(context) { progress ->
            setProgressAsync(workDataOf("progress" to progress))
            notificationManager.notify(notificationId, createNotification(progress))
        }

        // 3. Fin du téléchargement
        return if (success) {
            showSuccessNotification()
            Result.success()
        } else {
            showErrorNotification()
            Result.failure()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getLocalString("Mise à jour Base de données", "Database Update", "Actualización de la base de datos")
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = createNotification(progress)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotification(progress: Int): android.app.Notification {
        val title = getLocalString("Mise à jour de la base", "Database update", "Actualización de base de datos")
        val content = getLocalString("Téléchargement en cours... $progress%", "Downloading... $progress%", "Descargando... $progress%")

        // ✅ AJOUT : Intent pour ouvrir les paramètres DB au clic
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=database")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent) // ✅ On ajoute le clic !
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = android.app.Notification.ProgressStyle()
                .setProgress(progress)
                .setProgressSegments(listOf(android.app.Notification.ProgressStyle.Segment(100)))

            val nativeBuilder = android.app.Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.geotower_logo)
                .setContentIntent(pendingIntent) // ✅ On ajoute le clic ici aussi !
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_PROGRESS)
                .setStyle(progressStyle)

            // ✅ Utilisation de la réflexion pour compatibilité A16
            runCatching {
                android.app.Notification.Builder::class.java
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(nativeBuilder, "$progress%")
            }
            runCatching {
                android.app.Notification.Builder::class.java
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(nativeBuilder, true)
            }

            return nativeBuilder.build().apply {
                extras.putBoolean("android.requestPromotedOngoing", true)
                extras.putString("android.shortCriticalText", "$progress%")
            }
        }

        return builder.build()
    }

    private fun showSuccessNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=database")
            putExtra("SHOW_DB_SUCCESS_POPUP", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getLocalString("Base de données", "Database", "Base de datos")
        val content = getLocalString("La base a été téléchargée. Appuyez pour ouvrir.", "Database downloaded. Tap to open.", "Base de datos descargada. Toca para abrir.")

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun showErrorNotification() {
        val title = getLocalString("Erreur", "Error", "Error")
        val content = getLocalString("Échec du téléchargement. Veuillez vérifier votre connexion.", "Download failed. Please check your connection.", "Error de descarga. Por favor comprueba tu conexión.")

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(1002, notification)
    }
}