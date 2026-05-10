package fr.geotower.data.workers

import fr.geotower.data.models.OfflineMapDto
import java.io.File
import java.net.URI
import java.security.MessageDigest

object OfflineMapDownloadValidator {
    private val allowedHosts = setOf("download.mapsforge.org")
    private val safeFilenameRegex = Regex("^[A-Za-z0-9._-]+\\.map$")
    private val sha256Regex = Regex("^[A-Fa-f0-9]{64}$")

    fun isValidCatalogEntry(map: OfflineMapDto): Boolean {
        return isAllowedHttpsUrl(map.mapUrl) &&
            isSafeMapFilename(map.mapFilename) &&
            isValidSha256OrBlank(map.sha256)
    }

    fun isAllowedHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && host in allowedHosts
    }

    fun isSafeMapFilename(filename: String): Boolean {
        if (!safeFilenameRegex.matches(filename)) return false
        if (filename.contains("..")) return false
        val asFile = File(filename)
        return !asFile.isAbsolute && asFile.name == filename
    }

    fun safeMapFile(mapsDir: File, filename: String): File? {
        if (!isSafeMapFilename(filename)) return null
        val canonicalDir = mapsDir.canonicalFile
        val candidate = File(canonicalDir, filename).canonicalFile
        return if (candidate.parentFile == canonicalDir) candidate else null
    }

    fun listSafeMapFiles(mapsDir: File): List<File> {
        val canonicalDir = mapsDir.canonicalFile
        return mapsDir.listFiles()
            ?.mapNotNull { file ->
                if (!file.isFile) return@mapNotNull null
                if (!isSafeMapFilename(file.name)) return@mapNotNull null

                val safeFile = safeMapFile(canonicalDir, file.name) ?: return@mapNotNull null
                if (safeFile.parentFile == canonicalDir) safeFile else null
            }
            .orEmpty()
    }

    fun deleteAllSafeMapFiles(mapsDir: File): Int {
        return listSafeMapFiles(mapsDir).count { it.delete() }
    }

    fun isValidDownloadedMap(
        file: File,
        expectedContentLength: Long,
        expectedSha256: String?
    ): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (expectedContentLength > 0L && file.length() != expectedContentLength) return false
        val normalizedSha256 = expectedSha256?.trim().orEmpty()
        if (normalizedSha256.isEmpty()) return true
        if (!sha256Regex.matches(normalizedSha256)) return false
        return calculateSha256(file).equals(normalizedSha256, ignoreCase = true)
    }

    private fun isValidSha256OrBlank(value: String?): Boolean {
        val normalized = value?.trim().orEmpty()
        return normalized.isEmpty() || sha256Regex.matches(normalized)
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
}
