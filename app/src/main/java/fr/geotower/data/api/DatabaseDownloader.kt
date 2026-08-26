package fr.geotower.data.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.DatabaseStorageCleanup
import fr.geotower.data.db.AppDatabase
import fr.geotower.data.db.DatabaseArtifactIdentity
import fr.geotower.data.db.DatabaseVersionPolicy
import fr.geotower.data.db.GeoTowerDatabaseIndexes
import fr.geotower.data.db.GeoTowerDatabaseValidator
import fr.geotower.data.db.InstalledDatabaseArtifactIdentity
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
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object DatabaseDownloader {

    private const val DB_URL = "https://api.geotower.fr/api/v2/download/db"
    private const val DB_INFO_URL = "https://api.geotower.fr/api/v2/db/info"
    private const val DB_VERSION_URL = "https://api.geotower.fr/api/v2/download/version_fr"
    private const val DOWNLOAD_MANIFEST_URL = "https://api.geotower.fr/api/v2/download/manifest"
    private const val DB_NAME = GeoTowerDatabaseValidator.DB_NAME
    private val sha256Regex = Regex("^[A-Fa-f0-9]{64}$")
    private val downloadClient: OkHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .callTimeout(15, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    /** Identite de la base mobile proposee par le manifeste signe actuellement lu. */
    data class UpdateInfo(
        val version: String?,
        val sha256: String
    ) {
        val identity: DatabaseArtifactIdentity get() = DatabaseArtifactIdentity(version, sha256)
    }

    fun getDatabaseSize(): Double {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_UPDATE_CHECK)) {
            return 0.0
        }
        return try {
            val sizeBytes = readVerifiedDatabaseInfo()?.value?.sizeBytes ?: 0L
            if (sizeBytes > 0L) sizeBytes / (1024.0 * 1024.0) else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getLatestDatabaseVersion(): String? {
        return getLatestDatabaseUpdateInfo()?.version
    }

    suspend fun getLatestDatabaseUpdateInfo(): UpdateInfo? {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_UPDATE_CHECK)) {
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                readVerifiedDatabaseInfo()?.value?.let { database ->
                    UpdateInfo(version = database.version, sha256 = database.sha256)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Verifie d'abord l'identite du manifeste ayant installe le fichier. Ainsi, le meme jeu de
     * donnees ne redevient pas une mise a jour apres un passage principal <-> miroir. Les bases
     * installees avant cette memorisation conservent le comparateur historique de version.
     */
    fun isInstalledDatabaseCurrent(
        context: Context,
        remote: UpdateInfo?,
        localVersion: String?
    ): Boolean {
        remote ?: return false
        val databaseFile = context.getDatabasePath(DB_NAME)
        if (InstalledDatabaseArtifactIdentity.matchesInstalledMobile(context, databaseFile, remote.identity)) {
            return true
        }
        return DatabaseVersionPolicy.isLocalCurrentOrNewer(remote.version, localVersion)
    }

    fun isRemoteDatabaseUpdateAvailable(
        context: Context,
        remote: UpdateInfo?,
        localVersion: String?
    ): Boolean = remote != null && !isInstalledDatabaseCurrent(context, remote, localVersion)

    suspend fun downloadUpdate(context: Context, onProgress: suspend (Int) -> Unit): Boolean {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.DATABASE_DOWNLOAD)) {
            return false
        }
        // Crans « base en local » : la version distante est lisible, le fichier non — l'appareil
        // construit la sienne. Garde portée ici depuis [readVerifiedDatabaseInfo], qui sert aussi
        // aux lectures de version/taille.
        if (AppConfig.dbForcedLocal()) {
            AppLogger.w(TAG, "Database download skipped: local build enforced")
            return false
        }
        return withContext(Dispatchers.IO) {
            val served = readVerifiedDatabaseInfo()
            if (served == null) {
                AppLogger.w(TAG, "Remote database is unavailable or incompatible")
                return@withContext false
            }
            val remoteInfo = served.value
            val expectedSizeBytes = remoteInfo.sizeBytes
            val expectedSha256 = remoteInfo.sha256
            val maxAllowedBytes = maxAllowedDatabaseDownloadBytes(expectedSizeBytes)
                ?: return@withContext false

            GeoTowerDatabaseValidator.deleteObsoleteDatabases(context)
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()

            val tempFile = context.getDatabasePath("$DB_NAME.download")
            val backupFile = context.getDatabasePath("$DB_NAME.backup")

            try {
                if (tempFile.exists()) tempFile.delete()

                // Épinglé au serveur du manifeste : les deux serveurs construisent leur base
                // séparément, donc un rejeu sur l'autre ferait échouer le SHA-256 après 145 Mo.
                val request = Request.Builder()
                    .url(ApiEndpoints.urlOnHost(remoteInfo.url, served.host))
                    .header(ApiEndpoints.PIN_HOST_HEADER, served.host)
                    .header("Accept-Encoding", "identity")
                    .build()
                val response = downloadClient.newCall(request).execute()

                response.use { safeResponse ->
                    if (!safeResponse.isSuccessful) {
                        AppLogger.w(TAG, "Database download HTTP ${safeResponse.code}")
                        return@withContext false
                    }

                    val body = safeResponse.body ?: return@withContext false
                    val expectedContentLength = body.contentLength()
                    if (expectedContentLength > maxAllowedBytes) {
                        AppLogger.w(TAG, "Remote database content length exceeds hard limit")
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
                                    AppLogger.w(TAG, "Remote database download exceeded hard limit")
                                    throw IllegalStateException("database_download_too_large")
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
                        AppLogger.w(TAG, "Downloaded database integrity mismatch")
                        tempFile.delete()
                        return@withContext false
                    }
                }

                val validation = GeoTowerDatabaseValidator.validateDatabaseFile(tempFile)
                if (!validation.isValid) {
                    AppLogger.w(TAG, "Downloaded database validation failed: ${validation.reason}")
                    tempFile.delete()
                    return@withContext false
                }

                AppDatabase.closeDatabase()
                DatabaseStorageCleanup.clearSqliteSidecars(dbFile)

                if (!installValidatedDatabase(tempFile, dbFile, backupFile)) {
                    tempFile.delete()
                    return@withContext false
                }

                // Recrée les index de perf sur le fichier fraîchement installé (toujours sur
                // Dispatchers.IO ici) afin que la prochaine ouverture Room n'ait rien à reconstruire.
                GeoTowerDatabaseIndexes.applyToFile(dbFile)

                GeoTowerDatabaseValidator.clearInstalledDatabaseInvalid(context)
                updateCachedDatabaseState(GeoTowerDatabaseValidator.LocalDatabaseState.VALID)
                // A enregistrer apres les index : ils modifient le fichier SQLite et donc son
                // empreinte de presence. L'identite reste celle du manifeste verifie.
                InstalledDatabaseArtifactIdentity.recordMobileServerDownload(
                    context,
                    dbFile,
                    DatabaseArtifactIdentity(remoteInfo.version, remoteInfo.sha256)
                )
                DatabaseStorageCleanup.clearTransientArtifacts(context, DB_NAME)
                true
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Database download failed", e)
                if (tempFile.exists()) tempFile.delete()
                false
            } finally {
                DatabaseStorageCleanup.clearDownloadArtifacts(context, DB_NAME)
            }
        }
    }

    /**
     * Installe une base `geotower_fr.db` produite localement (builder on-device) via le **meme**
     * chemin atomique que le telechargement : validation structurelle, fermeture de Room, swap
     * `.backup`/rename, reconstruction des index, etat VALID. `builtFile` doit etre sur le meme
     * volume que la base installee (idealement `context.getDatabasePath("$DB_NAME.localbuild")`).
     */
    internal suspend fun installBuiltDatabase(context: Context, builtFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val validation = GeoTowerDatabaseValidator.validateDatabaseFile(builtFile)
            if (!validation.isValid) {
                AppLogger.w(TAG, "Locally built database validation failed: ${validation.reason}")
                if (builtFile.exists()) builtFile.delete()
                return@withContext false
            }

            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.parentFile?.mkdirs()
            val backupFile = context.getDatabasePath("$DB_NAME.backup")

            AppDatabase.closeDatabase()
            DatabaseStorageCleanup.clearSqliteSidecars(dbFile)

            if (!installValidatedDatabase(builtFile, dbFile, backupFile)) {
                if (builtFile.exists()) builtFile.delete()
                return@withContext false
            }

            GeoTowerDatabaseIndexes.applyToFile(dbFile)
            GeoTowerDatabaseValidator.clearInstalledDatabaseInvalid(context)
            updateCachedDatabaseState(GeoTowerDatabaseValidator.LocalDatabaseState.VALID)
            InstalledDatabaseArtifactIdentity.clearMobile(context)
            DatabaseStorageCleanup.clearTransientArtifacts(context, DB_NAME)
            true
        }

    private fun readVerifiedDatabaseInfo(): ServedFrom<DownloadManifestDatabase>? {
        // Seul le cran « autonomie maximale » coupe le manifeste. Aux crans « base en local », la
        // version distante reste lue EXPRÈS : c'est elle qui dit « l'ANFR a publié du neuf », donc
        // qu'il est temps de REGÉNÉRER. La couper laissait ces utilisateurs sans aucun signal de
        // mise à jour. Le téléchargement, lui, reste bloqué (cf. [downloadUpdate]).
        // ... et seulement si l'appareil peut se passer du serveur (cf. [AppConfig.blockServerDatabase]) :
        // sur un appareil inéligible à la génération, couper ici ne donnerait pas de l'autonomie,
        // mais une application sans la moindre donnée.
        if (AppConfig.blockServerDatabase()) return null
        val served = readVerifiedDownloadManifest() ?: return null
        val database = served.value.database ?: return null
        if (
            !isOfficialDatabaseDownloadUrl(database.url) ||
            !isValidRemoteDatabaseInfo(
                filename = database.filename,
                sizeBytes = database.sizeBytes,
                sha256 = database.sha256,
                schemaVersion = database.schemaVersion,
                countryCode = database.countryCode
            )
        ) {
            return null
        }
        return ServedFrom(database, served.host)
    }

    private fun readVerifiedDownloadManifest(): ServedFrom<DownloadManifest>? {
        val request = Request.Builder()
            .url(DOWNLOAD_MANIFEST_URL)
            .header("Accept-Encoding", "identity")
            .build()

        return try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val manifest = DownloadManifestVerifier.verifyAndParse(body) ?: return null
                // `response.request` porte l'URL réellement partie sur le réseau : c'est l'hôte
                // choisi par l'intercepteur de bascule, donc le serveur qui engage ce manifeste.
                ServedFrom(manifest, response.request.url.host)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readRemoteDatabaseInfo(): org.json.JSONObject? {
        val request = Request.Builder()
            .url(DB_INFO_URL)
            .header("Accept-Encoding", "identity")
            .build()

        return try {
            RetrofitClient.currentClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = org.json.JSONObject(body)

                if (isValidRemoteDatabaseInfo(json)) json else null
            }
        } catch (e: Exception) {
            null
        }
    }

    internal fun isOfficialDatabaseDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        // Le miroir sert son propre manifeste, avec ses propres URLs : les deux hôtes officiels
        // sont acceptés (la signature du manifeste reste, elle, la garantie de fond).
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            ApiEndpoints.isOfficialApiHost(uri.host) &&
            uri.path == "/api/v2/download/db"
    }

    internal fun isValidRemoteDatabaseInfo(json: org.json.JSONObject): Boolean {
        return isValidRemoteDatabaseInfo(
            filename = json.optString("filename"),
            sizeBytes = json.optLong("size_bytes", -1L),
            sha256 = json.optString("sha256", ""),
            schemaVersion = json.optInt("schema_version").takeIf { json.has("schema_version") },
            countryCode = json.optString("country_code").takeIf { json.has("country_code") }
        )
    }

    internal fun isValidRemoteDatabaseInfo(
        filename: String,
        sizeBytes: Long,
        sha256: String,
        schemaVersion: Int?,
        countryCode: String?
    ): Boolean {
        if (filename != DB_NAME) return false

        if (maxAllowedDatabaseDownloadBytes(sizeBytes) == null || !isValidSha256(sha256)) {
            return false
        }

        if (schemaVersion != null && schemaVersion != GeoTowerDatabaseValidator.EXPECTED_SCHEMA_VERSION) {
            return false
        }

        if (
            countryCode != null &&
            !countryCode.equals(
                GeoTowerDatabaseValidator.EXPECTED_COUNTRY_CODE,
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

    private fun maxAllowedDatabaseDownloadBytes(sizeBytes: Long): Long? {
        if (sizeBytes <= 0L || sizeBytes > 5L * 1024L * 1024L * 1024L) return null
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

    private fun updateCachedDatabaseState(state: GeoTowerDatabaseValidator.LocalDatabaseState) {
        Handler(Looper.getMainLooper()).post {
            AppConfig.localDatabaseState.value = state
        }
    }

    private const val TAG = "GeoTowerDb"
}
