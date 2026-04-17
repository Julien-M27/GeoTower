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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
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

    // 🚨 AJOUT DES VARIABLES POUR LA NOTIFICATION
    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val channelId = "map_download_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val zipUrl = inputData.getString("zip_url") ?: return@withContext Result.failure()
        val mapFilename = inputData.getString("map_filename") ?: return@withContext Result.failure()
        val estimatedSizeMb = inputData.getInt("estimated_size_mb", 2000)

        // 🚨 NOUVEAU : On crée un ID unique pour cette carte spécifique
        val uniqueNotifId = mapFilename.hashCode()

        // Initialisation de la notification à 0% avec son ID unique
        try {
            setForeground(createForegroundInfo(0, false, mapFilename, uniqueNotifId))
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

        val tempZipFile = File(mapsDir, "temp_${mapFilename}.zip")
        val finalMapFile = File(mapsDir, mapFilename)

        try {
            // 2️⃣ TÉLÉCHARGEMENT DU ZIP
            setProgress(workDataOf("state" to "DOWNLOADING", "progress" to 0))

            val request = Request.Builder().url(zipUrl).build()
            val response = RetrofitClient.currentClient.newCall(request).execute()

            if (!response.isSuccessful) return@withContext Result.failure()

            val body = response.body ?: return@withContext Result.failure()
            val fileLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempZipFile)

            val buffer = ByteArray(16 * 1024)
            var bytesCopied: Long = 0
            var bytes = inputStream.read(buffer)
            var lastProgress = 0

            while (bytes >= 0) {
                if (isStopped) {
                    outputStream.close()
                    inputStream.close()
                    tempZipFile.delete()
                    return@withContext Result.failure()
                }

                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes

                // Mise à jour de la barre de progression UI
                if (fileLength > 0) {
                    val progress = ((bytesCopied * 100) / fileLength).toInt()

                    if (progress > lastProgress) {
                        lastProgress = progress
                        setProgress(workDataOf("state" to "DOWNLOADING", "progress" to progress))
                        // 🚨 On met à jour la notification SPÉCIFIQUE à cette carte
                        notificationManager.notify(uniqueNotifId, createNotification(progress, false, mapFilename))
                    }
                }
                bytes = inputStream.read(buffer)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // 3️⃣ EXTRACTION DU FICHIER .MAP
            setProgress(workDataOf("state" to "EXTRACTING", "progress" to 100))
            // 🚨 Mise à jour pour l'extraction de CETTE carte
            notificationManager.notify(uniqueNotifId, createNotification(100, true, mapFilename))

            val extracted = extractMapFromZip(tempZipFile, mapsDir, mapFilename)

            // 4️⃣ NETTOYAGE
            tempZipFile.delete()

            return@withContext if (extracted) {
                Result.success()
            } else {
                setProgress(workDataOf("error" to "no_map_in_zip"))
                if (finalMapFile.exists()) finalMapFile.delete()
                Result.failure()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            if (tempZipFile.exists()) tempZipFile.delete()
            return@withContext Result.failure()
        }
    }

    /**
     * Vérifie s'il y a assez de place.
     * Pendant l'extraction, le téléphone a besoin du ZIP (ex: 2Go) ET de la carte extraite (ex: 2.5Go).
     * On demande donc environ 2.5x la taille du ZIP en espace libre pour être sûr.
     */
    private fun hasEnoughSpace(estimatedZipSizeMb: Int): Boolean {
        val requiredBytes = estimatedZipSizeMb * 1024L * 1024L * 2.5
        val stat = StatFs(applicationContext.getExternalFilesDir(null)?.path ?: return false)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes > requiredBytes
    }

    /**
     * Ouvre le ZIP, trouve le premier ".map", le sort et le renomme.
     */
    private fun extractMapFromZip(zipFile: File, targetDirectory: File, targetFilename: String): Boolean {
        try {
            val zis = ZipInputStream(FileInputStream(zipFile))
            var zipEntry = zis.nextEntry

            while (zipEntry != null) {
                if (!zipEntry.isDirectory && zipEntry.name.endsWith(".map", ignoreCase = true)) {
                    val outputFile = File(targetDirectory, targetFilename)
                    val fos = FileOutputStream(outputFile)

                    val buffer = ByteArray(16 * 1024)
                    var count = zis.read(buffer)
                    while (count != -1) {
                        // ✅ CORRECTION : Utilisation de isStopped
                        if (isStopped) {
                            fos.close()
                            zis.close()
                            outputFile.delete()
                            return false
                        }
                        fos.write(buffer, 0, count)
                        count = zis.read(buffer)
                    }

                    fos.flush()
                    fos.close()
                    zis.closeEntry()
                    zis.close()
                    return true
                }
                zis.closeEntry()
                zipEntry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Crée une notification persistante unique pour cette carte.
     */
    private fun createForegroundInfo(progress: Int, isExtracting: Boolean, mapName: String, notifId: Int): ForegroundInfo {
        val notification = createNotification(progress, isExtracting, mapName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun createNotification(progress: Int, isExtracting: Boolean, mapName: String): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getLocalString("Téléchargement de cartes", "Maps download", "Descarga de mapas"),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🚨 NOUVEAU : On intègre le nom du fichier dans le titre
        val title = getLocalString("Carte : $mapName", "Map: $mapName", "Mapa: $mapName")

        val content = if (isExtracting) {
            getLocalString("Extraction en cours...", "Extracting...", "Extrayendo...")
        } else {
            getLocalString("Téléchargement... $progress%", "Downloading... $progress%", "Descargando... $progress%")
        }

        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_geotower)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, isExtracting)
            .setOngoing(true)
            .build()
    }

    /**
     * Petite fonction pour traduire la notification en arrière-plan
     */
    private fun getLocalString(fr: String, en: String, es: String): String {
        return when (Locale.getDefault().language) {
            "fr" -> fr
            "es" -> es
            else -> en
        }
    }
}