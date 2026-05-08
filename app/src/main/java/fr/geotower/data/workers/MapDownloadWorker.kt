package fr.geotower.data.workers

import android.content.Context
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.geotower.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import android.app.PendingIntent
import android.content.Intent
import fr.geotower.MainActivity
import fr.geotower.R
import java.util.Locale

class MapDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val channelId = "map_download_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // ✅ CHANGÉ : On récupère map_url
        val mapUrl = inputData.getString("map_url") ?: return@withContext Result.failure()
        val mapFilename = inputData.getString("map_filename") ?: return@withContext Result.failure()
        val estimatedSizeMb = inputData.getInt("estimated_size_mb", 2000)

        val uniqueNotifId = mapFilename.hashCode()

        try {
            setForeground(createForegroundInfo(0, mapFilename, uniqueNotifId))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1️⃣ SÉCURITÉ : Vérification de l'espace libre
        if (!hasEnoughSpace(estimatedSizeMb)) {
            setProgress(workDataOf("error" to "not_enough_space"))
            return@withContext Result.failure()
        }

        val mapsDir = File(applicationContext.getExternalFilesDir(null), "maps")
        if (!mapsDir.exists()) mapsDir.mkdirs()

        // ✅ NOUVEAU : On télécharge dans un fichier .tmp pour sécuriser le téléchargement
        val tempMapFile = File(mapsDir, "temp_${mapFilename}.tmp")
        val finalMapFile = File(mapsDir, mapFilename)

        try {
            // 2️⃣ TÉLÉCHARGEMENT DIRECT DE LA CARTE (.map)
            setProgress(workDataOf("state" to "DOWNLOADING", "progress" to 0))

            val request = Request.Builder().url(mapUrl).build()
            val response = RetrofitClient.currentClient.newCall(request).execute()

            if (!response.isSuccessful) return@withContext Result.failure()

            val body = response.body ?: return@withContext Result.failure()
            val fileLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempMapFile)

            val buffer = ByteArray(16 * 1024)
            var bytesCopied: Long = 0
            var bytes = inputStream.read(buffer)
            var lastProgress = 0

            while (bytes >= 0) {
                if (isStopped) {
                    outputStream.close()
                    inputStream.close()
                    tempMapFile.delete()
                    return@withContext Result.failure()
                }

                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes

                if (fileLength > 0) {
                    val progress = ((bytesCopied * 100) / fileLength).toInt()

                    if (progress > lastProgress) {
                        lastProgress = progress
                        setProgress(workDataOf("state" to "DOWNLOADING", "progress" to progress))
                        notificationManager.notify(uniqueNotifId, createNotification(progress, mapFilename))
                    }
                }
                bytes = inputStream.read(buffer)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // 3️⃣ FINALISATION : On renomme le fichier .tmp en .map
            setProgress(workDataOf("state" to "DONE", "progress" to 100))
            if (finalMapFile.exists()) finalMapFile.delete()
            val success = tempMapFile.renameTo(finalMapFile)

            return@withContext if (success) {
                Result.success()
            } else {
                tempMapFile.delete()
                Result.failure()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            if (tempMapFile.exists()) tempMapFile.delete()
            return@withContext Result.failure()
        }
    }

    /**
     * Vérifie s'il y a assez de place.
     * Comme on ne dézippe plus rien, on a juste besoin de 1.1x la taille du fichier (10% de marge de sécurité)
     */
    private fun hasEnoughSpace(estimatedSizeMb: Int): Boolean {
        val requiredBytes = estimatedSizeMb * 1024L * 1024L * 1.1
        val stat = StatFs(applicationContext.getExternalFilesDir(null)?.path ?: return false)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes > requiredBytes
    }

    private fun createForegroundInfo(progress: Int, mapName: String, notifId: Int): ForegroundInfo {
        val notification = createNotification(progress, mapName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun createNotification(progress: Int, mapName: String): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getLocalString("Téléchargement de cartes", "Maps download", "Descarga de mapas"),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("geotower://settings?section=offline_maps")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getLocalString("Carte : $mapName", "Map: $mapName", "Mapa: $mapName")
        val content = getLocalString("Téléchargement... $progress%", "Downloading... $progress%", "Descargando... $progress%")

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val progressStyle = android.app.Notification.ProgressStyle()
                .setProgress(progress)
                .setProgressSegments(listOf(android.app.Notification.ProgressStyle.Segment(100)))

            val nativeBuilder = android.app.Notification.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.geotower_logo)
                .setContentIntent(pendingIntent)
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

    private fun getLocalString(fr: String, en: String, es: String): String {
        return when (Locale.getDefault().language) {
            "fr" -> fr
            "es" -> es
            else -> en
        }
    }
}
