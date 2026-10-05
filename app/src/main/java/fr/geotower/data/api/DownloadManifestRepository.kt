package fr.geotower.data.api

import fr.geotower.utils.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Référentiel centralisé pour la lecture et la mise en cache des manifestes signés.
 *
 * Évite les téléchargements répétés du même fichier manifeste (mobile, radio et eNB
 * sont publiés dans un seul et même manifeste signé).
 * Les manifestes sont mis en cache en mémoire pendant 60 secondes, et les requêtes
 * vers le serveur principal et le miroir s'exécutent en parallèle avec des timeouts courts.
 */
object DownloadManifestRepository {
    private const val DOWNLOAD_MANIFEST_URL = "https://api.geotower.fr/api/v2/download/manifest"
    private const val CACHE_TTL_MS = 60_000L

    private data class CacheEntry(
        val servedManifest: ServedFrom<DownloadManifest>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<ApiServer, CacheEntry>()
    private val serverLocks = ApiServer.entries.associateWith { Any() }

    /**
     * Client dédié à la vérification des manifestes : timeouts courts (2.5s connect, 4s read)
     * sans cache HTTP disque, pour détecter immédiatement les pannes sans figer l'interface.
     */
    private val manifestClient: OkHttpClient by lazy {
        RetrofitClient.currentClient.newBuilder()
            .cache(null)
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .writeTimeout(4000, TimeUnit.MILLISECONDS)
            .callTimeout(5000, TimeUnit.MILLISECONDS)
            .build()
    }

    fun clearCache() {
        cache.clear()
    }

    fun getVerifiedManifest(server: ApiServer, forceRefresh: Boolean = false): ServedFrom<DownloadManifest>? {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = cache[server]
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
                return cached.servedManifest
            }
        }

        val lock = serverLocks[server] ?: this
        synchronized(lock) {
            val nowLocked = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = cache[server]
                if (cached != null && nowLocked - cached.timestamp < CACHE_TTL_MS) {
                    return cached.servedManifest
                }
            }
            val fetched = fetchVerifiedDownloadManifest(server)
            if (fetched != null) {
                cache[server] = CacheEntry(fetched, System.currentTimeMillis())
            }
            return fetched
        }
    }

    suspend fun getPreferredCandidates(forceRefresh: Boolean = false): List<ServedFrom<DownloadManifest>> =
        withContext(Dispatchers.IO) {
            if (ApiEndpoints.isAutomaticMode()) {
                val active = ApiEndpoints.active()
                if (active == ApiServer.MIRROR && !ApiEndpoints.shouldRetryPrimary()) {
                    // Le miroir est actif suite à une indisponibilité récente du principal :
                    // on interroge directement le miroir pour une réponse instantanée (< 1s).
                    listOfNotNull(getVerifiedManifest(ApiServer.MIRROR, forceRefresh))
                } else {
                    // Les deux serveurs sont interrogés en parallèle sans se bloquer.
                    val (primary, mirror) = coroutineScope {
                        val primaryDeferred = async { getVerifiedManifest(ApiServer.PRIMARY, forceRefresh) }
                        val mirrorDeferred = async { getVerifiedManifest(ApiServer.MIRROR, forceRefresh) }
                        Pair(primaryDeferred.await(), mirrorDeferred.await())
                    }
                    if (primary == null && mirror != null) {
                        ApiEndpoints.switchTo(ApiServer.MIRROR)
                    } else if (primary != null && mirror == null) {
                        ApiEndpoints.switchTo(ApiServer.PRIMARY)
                    }
                    listOfNotNull(primary, mirror)
                }
            } else {
                listOfNotNull(getVerifiedManifest(ApiEndpoints.active(), forceRefresh))
            }
        }

    fun getPreferredCandidatesSync(forceRefresh: Boolean = false): List<ServedFrom<DownloadManifest>> {
        return if (ApiEndpoints.isAutomaticMode()) {
            val active = ApiEndpoints.active()
            if (active == ApiServer.MIRROR && !ApiEndpoints.shouldRetryPrimary()) {
                listOfNotNull(getVerifiedManifest(ApiServer.MIRROR, forceRefresh))
            } else {
                val first = getVerifiedManifest(active, forceRefresh)
                val fallback = ApiEndpoints.failoverTarget(active)
                if (first != null) {
                    val second = fallback?.let { getVerifiedManifest(it, forceRefresh) }
                    listOfNotNull(first, second)
                } else {
                    val second = fallback?.let { getVerifiedManifest(it, forceRefresh) }
                    if (second != null) {
                        ApiEndpoints.switchTo(fallback)
                    }
                    listOfNotNull(second)
                }
            }
        } else {
            listOfNotNull(getVerifiedManifest(ApiEndpoints.active(), forceRefresh))
        }
    }

    private fun fetchVerifiedDownloadManifest(server: ApiServer): ServedFrom<DownloadManifest>? {
        val request = Request.Builder()
            .url(ApiEndpoints.urlOnHost(DOWNLOAD_MANIFEST_URL, server.host))
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header(ApiEndpoints.PIN_HOST_HEADER, server.host)
            .header("Accept-Encoding", "identity")
            .build()

        return try {
            manifestClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val manifest = DownloadManifestVerifier.verifyAndParse(body) ?: return null
                ServedFrom(manifest, response.request.url.host)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPreferredMobileDatabase(forceRefresh: Boolean = false): ServedFrom<DownloadManifestDatabase>? {
        if (AppConfig.blockServerDatabase()) return null
        val candidates = getPreferredCandidates(forceRefresh).mapNotNull { served ->
            val db = served.value.database ?: return@mapNotNull null
            if (!DatabaseDownloader.isOfficialDatabaseDownloadUrl(db.url) ||
                !DatabaseDownloader.isValidRemoteDatabaseInfo(
                    filename = db.filename,
                    sizeBytes = db.sizeBytes,
                    sha256 = db.sha256,
                    schemaVersion = db.schemaVersion,
                    countryCode = db.countryCode
                )
            ) null else ServedFrom(db, served.host)
        }
        val primary = candidates.firstOrNull { it.host.equals(ApiServer.PRIMARY.host, ignoreCase = true) }
        val mirror = candidates.firstOrNull { it.host.equals(ApiServer.MIRROR.host, ignoreCase = true) }
        return DatabaseDownloader.selectPreferredDatabase(primary, mirror)
    }

    fun getPreferredMobileDatabaseSync(forceRefresh: Boolean = false): ServedFrom<DownloadManifestDatabase>? {
        if (AppConfig.blockServerDatabase()) return null
        val candidates = getPreferredCandidatesSync(forceRefresh).mapNotNull { served ->
            val db = served.value.database ?: return@mapNotNull null
            if (!DatabaseDownloader.isOfficialDatabaseDownloadUrl(db.url) ||
                !DatabaseDownloader.isValidRemoteDatabaseInfo(
                    filename = db.filename,
                    sizeBytes = db.sizeBytes,
                    sha256 = db.sha256,
                    schemaVersion = db.schemaVersion,
                    countryCode = db.countryCode
                )
            ) null else ServedFrom(db, served.host)
        }
        val primary = candidates.firstOrNull { it.host.equals(ApiServer.PRIMARY.host, ignoreCase = true) }
        val mirror = candidates.firstOrNull { it.host.equals(ApiServer.MIRROR.host, ignoreCase = true) }
        return DatabaseDownloader.selectPreferredDatabase(primary, mirror)
    }

    suspend fun getPreferredRadioDatabase(forceRefresh: Boolean = false): ServedFrom<DownloadManifestDatabase>? {
        if (AppConfig.blockServerDatabase()) return null
        val candidates = getPreferredCandidates(forceRefresh).mapNotNull { served ->
            val db = served.value.radioDatabase ?: return@mapNotNull null
            if (!RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl(db.url) ||
                !RadioDatabaseDownloader.isValidRemoteRadioDatabaseInfo(
                    filename = db.filename,
                    sizeBytes = db.sizeBytes,
                    sha256 = db.sha256,
                    schemaVersion = db.schemaVersion,
                    countryCode = db.countryCode
                )
            ) null else ServedFrom(db, served.host)
        }
        val primary = candidates.firstOrNull { it.host.equals(ApiServer.PRIMARY.host, ignoreCase = true) }
        val mirror = candidates.firstOrNull { it.host.equals(ApiServer.MIRROR.host, ignoreCase = true) }
        return DatabaseDownloader.selectPreferredDatabase(primary, mirror)
    }

    fun getPreferredRadioDatabaseSync(forceRefresh: Boolean = false): ServedFrom<DownloadManifestDatabase>? {
        if (AppConfig.blockServerDatabase()) return null
        val candidates = getPreferredCandidatesSync(forceRefresh).mapNotNull { served ->
            val db = served.value.radioDatabase ?: return@mapNotNull null
            if (!RadioDatabaseDownloader.isOfficialRadioDatabaseDownloadUrl(db.url) ||
                !RadioDatabaseDownloader.isValidRemoteRadioDatabaseInfo(
                    filename = db.filename,
                    sizeBytes = db.sizeBytes,
                    sha256 = db.sha256,
                    schemaVersion = db.schemaVersion,
                    countryCode = db.countryCode
                )
            ) null else ServedFrom(db, served.host)
        }
        val primary = candidates.firstOrNull { it.host.equals(ApiServer.PRIMARY.host, ignoreCase = true) }
        val mirror = candidates.firstOrNull { it.host.equals(ApiServer.MIRROR.host, ignoreCase = true) }
        return DatabaseDownloader.selectPreferredDatabase(primary, mirror)
    }

    suspend fun getPreferredEnbDatabase(forceRefresh: Boolean = false): ServedFrom<DownloadManifestDatabase>? {
        if (AppConfig.blockCommunityAndUpdates()) return null
        val candidates = getPreferredCandidates(forceRefresh).mapNotNull { served ->
            val db = served.value.enbDatabase ?: return@mapNotNull null
            if (!EnbDatabaseDownloader.isOfficialEnbDatabaseDownloadUrl(db.url) ||
                !EnbDatabaseDownloader.isValidRemoteEnbDatabaseInfo(
                    filename = db.filename,
                    sizeBytes = db.sizeBytes,
                    sha256 = db.sha256,
                    schemaVersion = db.schemaVersion,
                    countryCode = db.countryCode
                )
            ) null else ServedFrom(db, served.host)
        }
        val primary = candidates.firstOrNull { it.host.equals(ApiServer.PRIMARY.host, ignoreCase = true) }
        val mirror = candidates.firstOrNull { it.host.equals(ApiServer.MIRROR.host, ignoreCase = true) }
        return DatabaseDownloader.selectPreferredDatabase(primary, mirror)
    }

    fun getPreferredEnbDatabaseSync(forceRefresh: Boolean = false): ServedFrom<DownloadManifestDatabase>? {
        if (AppConfig.blockCommunityAndUpdates()) return null
        val candidates = getPreferredCandidatesSync(forceRefresh).mapNotNull { served ->
            val db = served.value.enbDatabase ?: return@mapNotNull null
            if (!EnbDatabaseDownloader.isOfficialEnbDatabaseDownloadUrl(db.url) ||
                !EnbDatabaseDownloader.isValidRemoteEnbDatabaseInfo(
                    filename = db.filename,
                    sizeBytes = db.sizeBytes,
                    sha256 = db.sha256,
                    schemaVersion = db.schemaVersion,
                    countryCode = db.countryCode
                )
            ) null else ServedFrom(db, served.host)
        }
        val primary = candidates.firstOrNull { it.host.equals(ApiServer.PRIMARY.host, ignoreCase = true) }
        val mirror = candidates.firstOrNull { it.host.equals(ApiServer.MIRROR.host, ignoreCase = true) }
        return DatabaseDownloader.selectPreferredDatabase(primary, mirror)
    }
}
