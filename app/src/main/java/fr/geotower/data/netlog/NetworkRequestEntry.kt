package fr.geotower.data.netlog

data class NetworkRequestEntry(
    val id: Long = 0L,
    val timestamp: Long,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Long,
    val responseSizeBytes: Long,
    val errorMessage: String? = null,
    val feature: String? = null
) {
    val isSuccess: Boolean
        get() = statusCode in 200..299

    val isRedirect: Boolean
        get() = statusCode in 300..399

    val isClientError: Boolean
        get() = statusCode in 400..499

    val isServerError: Boolean
        get() = statusCode >= 500

    val isNetworkFailure: Boolean
        get() = statusCode < 0 || errorMessage != null

    val displayFeature: String
        get() = feature?.takeIf { it.isNotBlank() } ?: detectFeature(path, host, url)

    companion object {
        fun detectFeature(path: String, host: String, url: String): String {
            val normalizedPath = path.lowercase()
            val normalizedHost = host.lowercase()
            return when {
                normalizedPath.contains("/api/v2/app/features") -> "Sonde de disponibilité / Ping"
                normalizedPath.contains("/api/v2/download/manifest") -> "Manifeste téléchargement"
                normalizedPath.contains("/api/v2/download/db") -> "Base antennes ANFR"
                normalizedPath.contains("/api/v2/download/radio_db") -> "Base radio & fréquences"
                normalizedPath.contains("/api/v2/download/enb_db") -> "Base eNB / gNB"
                normalizedPath.contains("/api/v2/download/version_fr") -> "Version des bases"
                normalizedPath.contains("/api/v2/db/info") -> "Informations base locale"
                normalizedPath.contains("/api/v2/antennes/hs/rebuild") -> "Régénération pannes"
                normalizedPath.contains("/api/v2/antennes/hs/info") -> "Statut pannes réseau"
                normalizedPath.contains("/api/v2/antennes/hs") -> "Pannes réseau"
                normalizedPath.contains("/api/v2/maps/catalog") -> "Catalogue des cartes"
                normalizedPath.contains("/api/v2/maps/") -> "Cartes hors-ligne"
                normalizedPath.contains("/api/v2/live/fr/status") -> "Statut Live"
                normalizedPath.contains("/api/v2/live/fr/sites/nearby") -> "Sites proches (Live)"
                normalizedPath.contains("/api/v2/live/fr/sites/bbox") -> "Zone carte (Live)"
                normalizedPath.contains("/api/v2/live/fr/sites/") -> "Détail site (Live)"
                normalizedPath.contains("/api/v2/live/fr/stats") -> "Statistiques (Live)"
                normalizedPath.contains("/api/v2/live/") -> "Données en direct (Live)"
                normalizedPath.contains("/api/v2/signalquest/") -> "SignalQuest"
                normalizedPath.contains("/api/v2/app/latest") -> "Vérification mise à jour"
                normalizedHost.contains("openstreetmap") -> "Tuiles OpenStreetMap"
                normalizedHost.contains("cartocdn") -> "Tuiles CartoDB"
                normalizedHost.contains("ign.fr") -> "Tuiles IGN"
                normalizedPath.endsWith(".png") || normalizedPath.endsWith(".jpg") ||
                    normalizedPath.endsWith(".pbf") -> "Tuiles de carte"
                normalizedHost.contains("geotower") || normalizedHost.contains("cajejuma") -> "API GeoTower"
                else -> host.ifBlank { "Réseau" }
            }
        }
    }
}
