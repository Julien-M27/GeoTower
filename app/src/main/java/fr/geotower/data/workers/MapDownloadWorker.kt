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

class MapDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val zipUrl = inputData.getString("zip_url") ?: return@withContext Result.failure()
        val mapFilename = inputData.getString("map_filename") ?: return@withContext Result.failure()
        val estimatedSizeMb = inputData.getInt("estimated_size_mb", 2000)

        // 1️⃣ SÉCURITÉ : Vérification de l'espace libre
        if (!hasEnoughSpace(estimatedSizeMb)) {
            setProgress(workDataOf("error" to "not_enough_space"))
            return@withContext Result.failure()
        }

        // On utilise le stockage externe de l'appli (idéal pour les gros fichiers)
        val mapsDir = File(applicationContext.getExternalFilesDir(null), "maps")
        if (!mapsDir.exists()) mapsDir.mkdirs()

        // On rend le nom du fichier ZIP temporaire UNIQUE pour chaque carte !
        val tempZipFile = File(mapsDir, "temp_${mapFilename}.zip")
        val finalMapFile = File(mapsDir, mapFilename)

        try {
            // 2️⃣ TÉLÉCHARGEMENT DU ZIP
            setProgress(workDataOf("state" to "DOWNLOADING", "progress" to 0))

            val request = Request.Builder().url(zipUrl).build()
            val response = RetrofitClient.currentClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure()
            }

            val body = response.body ?: return@withContext Result.failure()
            val fileLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempZipFile)

            val buffer = ByteArray(16 * 1024) // Buffer de 16 Ko pour aller vite
            var bytesCopied: Long = 0
            var bytes = inputStream.read(buffer)
            var lastProgress = 0

            while (bytes >= 0) {
                // ✅ CORRECTION : Utilisation de isStopped propre au Worker
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
                    }
                }
                bytes = inputStream.read(buffer)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // 3️⃣ EXTRACTION DU FICHIER .MAP
            setProgress(workDataOf("state" to "EXTRACTING", "progress" to 100))
            val extracted = extractMapFromZip(tempZipFile, mapsDir, mapFilename)

            // 4️⃣ NETTOYAGE (Suppression du fichier ZIP de 2 Go)
            tempZipFile.delete()

            if (extracted) {
                return@withContext Result.success()
            } else {
                setProgress(workDataOf("error" to "no_map_in_zip"))
                if (finalMapFile.exists()) finalMapFile.delete()
                return@withContext Result.failure()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // En cas de coupure wifi ou crash, on efface le fichier cassé
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
}