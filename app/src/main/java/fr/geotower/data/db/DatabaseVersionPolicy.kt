package fr.geotower.data.db

object DatabaseVersionPolicy {
    fun normalizedVersion(version: String?): String? {
        return version?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun isRemoteNewer(remoteVersion: String?, localVersion: String?): Boolean {
        val remote = normalizedVersion(remoteVersion) ?: return false
        val local = normalizedVersion(localVersion) ?: return true
        return compareVersions(remote, local) > 0
    }

    fun isLocalCurrentOrNewer(remoteVersion: String?, localVersion: String?): Boolean {
        val remote = normalizedVersion(remoteVersion) ?: return false
        val local = normalizedVersion(localVersion) ?: return false
        return compareVersions(remote, local) <= 0
    }

    fun shouldNotify(remoteVersion: String?, localVersion: String?, lastNotifiedVersion: String?): Boolean {
        val remote = normalizedVersion(remoteVersion) ?: return false
        return isRemoteNewer(remote, localVersion) && remote != normalizedVersion(lastNotifiedVersion)
    }

    private fun compareVersions(remoteVersion: String, localVersion: String): Int {
        if (remoteVersion == localVersion) return 0

        val remoteKey = orderedVersionKey(remoteVersion)
        val localKey = orderedVersionKey(localVersion)
        if (remoteKey != null && localKey != null) {
            return remoteKey.compareTo(localKey)
        }

        // Unknown non-empty formats keep the legacy behavior: different means available.
        return 1
    }

    private fun orderedVersionKey(version: String): String? {
        val digits = version.filter { it.isDigit() }
        return digits.takeIf { it.length >= 8 }
    }
}
