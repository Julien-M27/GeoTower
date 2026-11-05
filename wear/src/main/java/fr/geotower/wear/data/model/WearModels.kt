package fr.geotower.wear.data.model

/**
 * Détail d'un opérateur / émetteur sur un support physique (comme sur Garmin).
 */
data class WearOperatorSite(
    val operator: String, // "ORANGE", "SFR", "BOUYGUES", "FREE"
    val technologies: String = "4G / 5G", // "2G / 3G / 4G / 5G"
    val bands: String = "4G: 700, 1800, 2100 • 5G: 3500 MHz",
    val azimuths: List<Int> = listOf(0, 120, 240)
) {
    val azimuthsString: String
        get() = if (azimuths.isEmpty()) "Omnidirectionnel (360°)" else azimuths.joinToString(", ") { "$it°" }
}

/**
 * Modèle unifié d'un support physique d'antennes relais (comme sur Garmin).
 * Prend en charge le multi-opérateur (ex: Orange + Free sur le même pylône).
 */
data class WearTower(
    val id: String,
    val distanceMeters: Int,
    val bearingDegrees: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val supportType: String = "Pylône autostable",
    val supportHeight: Int? = 35,
    val operators: List<WearOperatorSite> = emptyList(),
    val source: String = "auto"
) {
    // Opérateur principal ou de repli
    val operator: String
        get() = operators.firstOrNull()?.operator ?: "Inconnu"

    val technologies: String
        get() = operators.flatMap { it.technologies.split("/", ",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifEmpty { "4G / 5G" }

    val operatorNamesSummary: String
        get() = if (operators.isEmpty()) "Inconnu" else operators.joinToString(" / ") { it.operator }

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
 * - AUTO : tente d'abord la base Room du smartphone via Bluetooth, bascule automatiquement sur l'API Web si indisponible.
 * - PHONE_LOCAL : base SQLite Room locale du smartphone via Play Services Wearable (100% hors-ligne).
 * - WEB_API : appel direct à l'API Web GeoTower / ANFR en HTTPS (Wi-Fi / eSIM 4G de la montre).
 */
enum class DataSourceMode(val label: String, val shortLabel: String) {
    AUTO("Automatique", "🔄 Auto"),
    PHONE_LOCAL("Base locale smartphone", "📱 Téléphone"),
    WEB_API("API Web directe", "🌐 Web API")
}

/**
 * Fréquence d'actualisation du GPS de la montre (comme sur Garmin).
 */
enum class GpsRefreshInterval(val label: String, val description: String, val intervalMs: Long) {
    ON_OPEN("À l'ouverture", "Fix unique au lancement", 0L),
    EVERY_1_MIN("1 minute", "Actualisation toutes les 60s", 60_000L),
    CONTINUOUS("Continu", "Temps réel haute précision", 2_000L);

    fun next(): GpsRefreshInterval {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
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
    HOME,
    LIST,
    RADAR,
    MAP,
    SETTINGS,
    SUPPORT_DETAIL,
    OPERATOR_DETAIL,
    ABOUT
}

