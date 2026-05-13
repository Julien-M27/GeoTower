package fr.geotower.data.db

object DatabaseVersionPolicy {
    private val dateVersionPattern =
        Regex("""(\d{4})\D?(\d{2})\D?(\d{2})(?:\D?(\d{2})\D?(\d{2})(?:\D?(\d{2}))?)?""")

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
        return isRemoteNewer(remote, localVersion) && !areEquivalent(remote, lastNotifiedVersion)
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

    private fun areEquivalent(firstVersion: String?, secondVersion: String?): Boolean {
        val first = normalizedVersion(firstVersion) ?: return false
        val second = normalizedVersion(secondVersion) ?: return false
        return compareVersions(first, second) == 0
    }

    private fun orderedVersionKey(version: String): OrderedVersionKey? {
        val match = dateVersionPattern.find(version) ?: return null
        val groups = match.groupValues
        val digits = buildString {
            append(groups[1])
            append(groups[2])
            append(groups[3])
            if (groups[4].isNotEmpty() && groups[5].isNotEmpty()) {
                append(groups[4])
                append(groups[5])
            }
            if (groups[6].isNotEmpty()) {
                append(groups[6])
            }
        }
        return OrderedVersionKey(digits)
    }

    private data class OrderedVersionKey(private val digits: String) : Comparable<OrderedVersionKey> {
        override fun compareTo(other: OrderedVersionKey): Int {
            val commonLength = minOf(digits.length, other.digits.length)
            val commonComparison = digits.take(commonLength).compareTo(other.digits.take(commonLength))
            return if (commonComparison != 0) commonComparison else 0
        }
    }
}
