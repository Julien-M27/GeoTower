package fr.geotower.wear.data.model

/**
 * Modèle unifié d'une antenne relais affichée sur la montre Wear OS.
 */
data class WearTower(
    val id: String,
    val operator: String,
    val distanceMeters: Int,
    val bearingDegrees: Int,
    val technologies: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val supportType: String = "Pylône",
    val supportHeight: Int? = null,
    val source: String = "phone_db" // "phone_db" ou "web_api"
) {
    val cardinalDirection: String
        get() = when (((bearingDegrees + 22) % 360) / 45) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SO"
            6 -> "O"
            7 -> "NO"
            else -> "N"
        }

    val arrowSymbol: String
        get() = when (((bearingDegrees + 22) % 360) / 45) {
            0 -> "↑"
            1 -> "↗"
            2 -> "→"
            3 -> "↘"
            4 -> "↓"
            5 -> "↙"
            6 -> "←"
            7 -> "↖"
            else -> "↑"
        }
}

/**
 * Choix de la source des données :
 * - PHONE_LOCAL : base SQLite Room locale du smartphone via Play Services Wearable (100% hors-ligne).
 * - WEB_API : appel direct à l'API Web GeoTower / ANFR en HTTPS (Wi-Fi / eSIM 4G de la montre).
 */
enum class DataSourceMode(val label: String, val shortLabel: String) {
    PHONE_LOCAL("Base locale smartphone", "📱 Téléphone"),
    WEB_API("API Web directe", "🌐 Web API")
}

/**
 * État du fix GPS de la montre.
 */
sealed interface GpsFixStatus {
    object Searching : GpsFixStatus
    data class Fixed(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val provider: String = "GPS Montre"
    ) : GpsFixStatus
    data class Error(val message: String) : GpsFixStatus
}

enum class WearScreen {
    RADAR,
    LIST,
    SETTINGS,
    DETAIL
}

