package fr.geotower.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object DatabaseDownloader {

    private const val DB_URL = "https://api.cajejuma.fr/api/v2/download/db"
    private const val DB_NAME = GeoTowerDatabaseValidator.DB_NAME
    private val downloadClient: OkHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    fun getDatabaseSize(): Double {
        return try {
            val request = Request.Builder()
                .url(DB_URL)
                .header("Accept-Encoding", "identity")
                .build()

            val response = RetrofitClient.currentClient.newCall(request).execute()
            response.use {
                val length = it.body?.contentLength() ?: 0L
                if (length > 0) length / (1024.0 * 1024.0) else 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getLatestDatabaseVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.cajejuma.fr/api/v2/download/version")
                    .build()

                val response = RetrofitClient.currentClient.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) return@withContext null
                    val bodyString = it.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(bodyString)
                    if (json.isNull("version")) null else json.optString("version")
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun downloadUpdate(context: Context, onProgress: suspend (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            val tempFile = context.getDatabasePath("$DB_NAME.download")
            val backupFile = context.getDatabasePath("$DB_NAME.backup")

            try {
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(DB_URL)
                    .header("Accept-Encoding", "identity")
                    .build()
                val response = downloadClient.newCall(request).execute()

                response.use { safeResponse ->
                    if (!safeResponse.isSuccessful) return@withContext false

                    val body = safeResponse.body ?: return@withContext false
                    val expectedContentLength = body.contentLength()

                    body.byteStream().use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesCopied = 0L
                            var bytes = inputStream.read(buffer)
                            var lastProgress = 0

                            while (bytes >= 0) {
                                ensureActive()

                                outputStream.write(buffer, 0, bytes)
                                bytesCopied += bytes

                                if (expectedContentLength > 0) {
                                    val progress = ((bytesCopied * 100) / expectedContentLength).toInt()
                                    if (progress > lastProgress) {
                                        lastProgress = progress
                                        onProgress(progress)
                                    }
                                }

                                bytes = inputStream.read(buffer)
                            }
                        }
                    }

                    if (!hasExpectedDownloadSize(tempFile, expectedContentLength)) {
                        tempFile.delete()
                        return@withContext false
                    }
                }

                if (!GeoTowerDatabaseValidator.validateDatabaseFile(tempFile).isValid) {
                    tempFile.delete()
                    return@withContext false
                }

                AppDatabase.closeDatabase()
                deleteSqliteSidecars(dbFile)

                if (!installValidatedDatabase(tempFile, dbFile, backupFile)) {
                    tempFile.delete()
                    return@withContext false
                }

                GeoTowerDatabaseValidator.clearInstalledDatabaseInvalid(context)
                updateCachedDatabaseState(GeoTowerDatabaseValidator.LocalDatabaseState.VALID)
                true
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Database download failed", e)
                if (tempFile.exists()) tempFile.delete()
                false
            }
        }
    }

    private fun hasExpectedDownloadSize(file: File, expectedContentLength: Long): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return expectedContentLength <= 0L || file.length() == expectedContentLength
    }

    private fun installValidatedDatabase(tempFile: File, dbFile: File, backupFile: File): Boolean {
        if (!dbFile.exists() && backupFile.exists()) {
            backupFile.renameTo(dbFile)
        }

        if (backupFile.exists() && !backupFile.delete()) return false

        val hadExistingDb = dbFile.exists()
        if (hadExistingDb && !dbFile.renameTo(backupFile)) {
            return false
        }

        if (!tempFile.renameTo(dbFile)) {
            if (hadExistingDb && backupFile.exists()) {
                backupFile.renameTo(dbFile)
            }
            return false
        }

        if (backupFile.exists()) backupFile.delete()
        return true
    }

    private fun deleteSqliteSidecars(dbFile: File) {
        listOf(
            File(dbFile.path + "-wal"),
            File(dbFile.path + "-shm")
        ).forEach { sidecar ->
            if (sidecar.exists()) sidecar.delete()
        }
    }

    private fun updateCachedDatabaseState(state: GeoTowerDatabaseValidator.LocalDatabaseState) {
        Handler(Looper.getMainLooper()).post {
            AppConfig.localDatabaseState.value = state
        }
    }

    private const val TAG = "GeoTowerDb"
}
