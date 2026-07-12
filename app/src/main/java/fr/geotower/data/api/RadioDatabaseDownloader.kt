package fr.geotower.data.api

import android.content.Context
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.RadioDatabaseValidator
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object RadioDatabaseDownloader {

    private const val DOWNLOAD_MANIFEST_URL = "https://api.cajejuma.fr/api/v2/download/manifest"
    private const val DB_NAME = RadioDatabaseValidator.DB_NAME
    private val sha256Regex = Regex("^[A-Fa-f0-9]{64}$")
    private val downloadClient: OkHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .callTimeout(15, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    fun getDatabaseSize(): Double {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_UPDATE_CHECK)) {
            return 0.0
        }
        return try {
            val sizeBytes = readVerifiedRadioDatabaseInfo()?.sizeBytes ?: 0L
            if (sizeBytes > 0L) sizeBytes / (1024.0 * 1024.0) else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getLatestDatabaseVersion(): String? {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_UPDATE_CHECK)) {
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                readVerifiedRadioDatabaseInfo()?.version
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun downloadUpdate(context: Context, onProgress: suspend (Int) -> Unit): Boolean {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD)) {
            return false
        }
        return withContext(Dispatchers.IO) {
            val remoteInfo = readVerifiedRadioDatabaseInfo()
            if (remoteInfo == null) {
                AppLogger.w(TAG, "Remote radio database is unavailable or incompatible")
                return@withContext false
            }

            val expectedSizeBytes = remoteInfo.sizeBytes
            val expectedSha256 = remoteInfo.sha256
            val maxAllowedBytes = maxAllowedRadioDatabaseDownloadBytes(expectedSizeBytes)
                ?: return@withContext false

            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            val tempFile = context.getDatabasePath("$DB_NAME.download")
            val backupFile = context.getDatabasePath("$DB_NAME.backup")

            try {
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(remoteInfo.url)
                    .header("Accept-Encoding", "identity")
                    .build()
                val response = downloadClient.newCall(request).execute()

                response.use { safeResponse ->
                    if (!safeResponse.isSuccessful) {
                        AppLogger.w(TAG, "Radio database download HTTP ${safeResponse.code}")
                        return@withContext false
                    }

                    val body = safeResponse.body ?: return@withContext false
                    val expectedContentLength = body.contentLength()
                    if (expectedContentLength > maxAllowedBytes) {
                        AppLogger.w(TAG, "Remote radio database content length exceeds hard limit")
                        return@withContext false
                    }
                    val progressLength = expectedContentLength.takeIf { it > 0L } ?: expectedSizeBytes

                    body.byteStream().use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesCopied = 0L
                            var bytes = inputStream.read(buffer)
                            var lastProgress = 0

                            while (bytes >= 0) {
                                ensureActive()

                                bytesCopied += bytes
                                if (bytesCopied > maxAllowedBytes) {
                                    AppLogger.w(TAG, "Remote radio database download exceeded hard limit")
                                    throw IllegalStateException("radio_database_download_too_large")
                                }
                                outputStream.write(buffer, 0, bytes)

                                if (progressLength > 0) {
                                    val progress = ((bytesCopied * 100) / progressLength).toInt().coerceIn(0, 100)
                                    if (progress > lastProgress) {
                                        lastProgress = progress
                                        onProgress(progress)
                                    }
                                }

                                bytes = inputStream.read(buffer)
                            }
                        }
                    }

                    if (!hasExpectedDownloadIntegrity(tempFile, expectedContentLength, expectedSizeBytes, expectedSha256)) {
                        AppLogger.w(TAG, "Downloaded radio database integrity mismatch")
                        tempFile.delete()
                        return@withContext false
                    }
                }

                val validation = RadioDatabaseValidator.validateDatabaseFile(tempFile)
                if (!validation.isValid) {
                    AppLogger.w(TAG, "Downloaded radio database validation failed: ${validation.reason}")
                    tempFile.delete()
                    return@withContext false
                }

                deleteSqliteSidecars(dbFile)
                if (!installValidatedDatabase(tempFile, dbFile, backupFile)) {
                    tempFile.delete()
                    return@withContext false
                }

                true
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Radio database download failed", e)
                if (tempFile.exists()) tempFile.delete()
                false
            }
        }
    }

    /**
     * Installe une base radio `geotower_fr_radio.db` produite localement (builder on-device) via le
     * **meme** chemin atomique que le telechargement : validation structurelle ([RadioDatabaseValidator]),
     * suppression des sidecars WAL/SHM, swap `.backup`/rename. Contrairement a la base principale, la
     * base radio n'est pas geree par Room (ouverte en lecture seule, par requete, par `RadioRepository`),
     * il n'y a donc ni fermeture Room ni reconstruction d'index a faire. `builtFile` doit etre sur le meme
     * volume que la base installee (idealement `context.getDatabasePath("$DB_NAME.localbuild")`).
     */
    internal suspend fun installBuiltRadioDatabase(context: Context, builtFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val validation = RadioDatabaseValidator.validateDatabaseFile(builtFile)
            if (!validation.isValid) {
                AppLogger.w(TAG, "Locally built radio database validation failed: ${validation.reason}")
                if (builtFile.exists()) builtFile.delete()
                return@withContext false
            }

            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()
            val backupFile = context.getDatabasePath("$DB_NAME.backup")

            deleteSqliteSidecars(dbFile)
            if (!installValidatedDatabase(builtFile, dbFile, backupFile)) {
                if (builtFile.exists()) builtFile.delete()
                return@withContext false
            }
            true
        }

    private fun readVerifiedRadioDatabaseInfo(): DownloadManifestDatabase? {
        return readVerifiedDownloadManifest()
            ?.radioDatabase
            ?.takeIf { database ->
                isOfficialRadioDatabaseDownloadUrl(database.url) &&
                    isValidRemoteRadioDatabaseInfo(
                        filename = database.filename,
                        sizeBytes = database.sizeBytes,
                        sha256 = database.sha256,
                        schemaVersion = database.schemaVersion,
                        countryCode = database.countryCode
                    )
            }
    }

    private fun readVerifiedDownloadManifest(): DownloadManifest? {
        val request = Request.Builder()
            .url(DOWNLOAD_MANIFEST_URL)
            .header("Accept-Encoding", "identity")
            .build()

        return try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                DownloadManifestVerifier.verifyAndParse(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    internal fun isOfficialRadioDatabaseDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.host.equals("api.cajejuma.fr", ignoreCase = true) &&
            uri.path == "/api/v2/download/radio_db"
    }

    internal fun isValidRemoteRadioDatabaseInfo(
        filename: String,
        sizeBytes: Long,
        sha256: String,
        schemaVersion: Int?,
        countryCode: String?
    ): Boolean {
        if (filename != DB_NAME) return false

        if (maxAllowedRadioDatabaseDownloadBytes(sizeBytes) == null || !isValidSha256(sha256)) {
            return false
        }

        if (schemaVersion != null && schemaVersion != RadioDatabaseValidator.EXPECTED_SCHEMA_VERSION) {
            return false
        }

        if (
            countryCode != null &&
            !countryCode.equals(
                RadioDatabaseValidator.EXPECTED_COUNTRY_CODE,
                ignoreCase = true
            )
        ) {
            return false
        }

        return true
    }

    private fun hasExpectedDownloadIntegrity(
        file: File,
        expectedContentLength: Long,
        expectedSizeBytes: Long,
        expectedSha256: String
    ): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (file.length() != expectedSizeBytes) return false
        if (expectedContentLength > 0L && file.length() != expectedContentLength) return false
        if (!isValidSha256(expectedSha256)) return false
        return calculateSha256(file).equals(expectedSha256, ignoreCase = true)
    }

    private fun maxAllowedRadioDatabaseDownloadBytes(sizeBytes: Long): Long? {
        if (sizeBytes <= 0L || sizeBytes > 1024L * 1024L * 1024L) return null
        val marginBytes = maxOf(1L * 1024L * 1024L, sizeBytes / 100L)
        return sizeBytes + marginBytes
    }

    private fun isValidSha256(value: String?): Boolean {
        return sha256Regex.matches(value?.trim().orEmpty())
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    private const val TAG = "GeoTowerRadioDb"
}
