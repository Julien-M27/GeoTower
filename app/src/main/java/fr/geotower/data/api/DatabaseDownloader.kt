package fr.geotower.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object DatabaseDownloader {

    private const val DB_URL = "https://api.cajejuma.fr/api/v2/download/db"
    private const val DB_NAME = "geotower.db"

    fun getDatabaseSize(): Double {
        return try {
            val client = RetrofitClient.currentClient

            // 1. On utilise un vrai GET (plus fiable que HEAD)
            // 2. On ajoute 'Accept-Encoding: identity' pour bloquer la compression qui cache le vrai poids
            val request = Request.Builder()
                .url(DB_URL)
                .header("Accept-Encoding", "identity")
                .build()

            val response = client.newCall(request).execute()

            // 3. On lit le poids directement depuis le corps de la réponse
            val length = response.body?.contentLength() ?: 0L

            // 4. TRÈS IMPORTANT : On ferme tout de suite pour annuler le téléchargement
            response.close()

            if (length > 0) length / (1024.0 * 1024.0) else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    // ✅ NOUVEAU : Récupère la version de la base de données sur le serveur
    suspend fun getLatestDatabaseVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val client = RetrofitClient.currentClient
                val request = Request.Builder()
                    .url("https://api.cajejuma.fr/api/v2/download/version")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (bodyString != null) {
                        val json = org.json.JSONObject(bodyString)
                        json.optString("version", null)
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    // ✅ MODIFICATION 1 : 'onProgress' devient une fonction 'suspend'
    suspend fun downloadUpdate(context: Context, onProgress: suspend (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val tempFile = File(context.getDatabasePath(DB_NAME).path + "_temp")
            try {
                val client = RetrofitClient.currentClient
                val request = Request.Builder().url(DB_URL).build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext false
                    val fileLength = body.contentLength()
                    val inputStream = body.byteStream()

                    val dbFile = context.getDatabasePath(DB_NAME)
                    dbFile.parentFile?.mkdirs()

                    val outputStream = java.io.FileOutputStream(tempFile)

                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied: Long = 0
                    var bytes = inputStream.read(buffer)
                    var lastProgress = 0

                    while (bytes >= 0) {
                        // ✅ MODIFICATION 2 : On vérifie si le WorkManager a été annulé
                        if (!isActive) {
                            outputStream.close()
                            inputStream.close()
                            if (tempFile.exists()) tempFile.delete() // On nettoie le fichier incomplet
                            return@withContext false
                        }

                        outputStream.write(buffer, 0, bytes)
                        bytesCopied += bytes

                        if (fileLength > 0) {
                            val progress = ((bytesCopied * 100) / fileLength).toInt()
                            if (progress > lastProgress) {
                                lastProgress = progress
                                // ✅ Plus besoin de repasser sur le thread principal pour le WorkManager
                                onProgress(progress)
                            }
                        }
                        bytes = inputStream.read(buffer)
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    // 🚨 SUPPRESSION DES CACHES FANTÔMES (-wal et -shm) 🚨
                    val walFile = File(dbFile.path + "-wal")
                    val shmFile = File(dbFile.path + "-shm")
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()

                    // ✅ CORRECTION CRUCIALE : On ferme proprement Room avant de remplacer le fichier !
                    fr.geotower.data.db.AppDatabase.closeDatabase()

                    // ✅ CORRECTION : Remplacement ultra-sécurisé du fichier
                    try {
                        tempFile.copyTo(dbFile, overwrite = true)
                        tempFile.delete() // On nettoie le fichier temporaire
                    } catch (e: Exception) {
                        // Plan B si copyTo échoue
                        if (dbFile.exists()) dbFile.delete()
                        tempFile.renameTo(dbFile)
                    }

                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Sécurité : on supprime le tempFile en cas de crash réseau
                if (tempFile.exists()) tempFile.delete()
                false
            }
        }
    }

}