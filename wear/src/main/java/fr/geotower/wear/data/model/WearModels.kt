package fr.geotower.wear.data.model

/**
 * Détail d'un opérateur / émetteur sur un support physique (comme sur Garmin).
 */
data class WearOperatorSite(
    val operator: String, // "ORANGE", "SFR", "BOUYGUES", "FREE"
    val technologies: String = "4G / 5G", // "2G / 3G / 4G / 5G"
    val bands: String = "4G: 700, 1800, 2100 • 5G: 3500 MHz",
    val azimuths: List<Int> = listOf(0, 120, 240),
    val rawTechMask: Int = 0,
    val rawBandMask: Int = 0
) {
    val azimuthsString: String
        get() = if (azimuths.isEmpty()) "Omnidirectionnel (360°)" else azimuths.joinToString(", ") { "$it°" }

    fun formattedTechnologies(order: TechOrder = TechOrder.DESCENDING): String {
        return if (rawTechMask != 0) {
            BandFormatter.formatTechnologies(rawTechMask, order)
        } else {
            BandFormatter.formatTechnologies(technologies, order)
        }
    }

    fun formattedBands(order: TechOrder = TechOrder.DESCENDING): String {
        return if (rawBandMask != 0 || rawTechMask != 0) {
            BandFormatter.formatBands(rawBandMask, rawTechMask, order)
        } else {
            BandFormatter.reorderBandsString(bands, technologies, order)
        }
    }
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

    fun formattedTechnologies(order: TechOrder = TechOrder.DESCENDING): String {
        val allTechs = operators.flatMap { it.formattedTechnologies(order).split("/", ",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return BandFormatter.formatTechnologies(allTechs.joinToString(" / "), order)
    }

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
    AUTO("Automatique", "Auto"),
    PHONE_LOCAL("Base locale smartphone", "Téléphone"),
    WEB_API("API Web directe", "Web API")
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

/**
 * Thème d'affichage du fond de carte (OSM) :
 * - DARK : rendu nocturne contrasté adapté aux écrans OLED de montre.
 * - LIGHT : rendu clair natif OpenStreetMap (comme en plein soleil).
 */
enum class WearMapTheme(val label: String, val shortLabel: String) {
    DARK("Sombre", "Sombre"),
    LIGHT("Clair", "Clair");

    fun next(): WearMapTheme = if (this == DARK) LIGHT else DARK
}

/**
 * Choix de la langue de l'application sur la montre :
 * - AUTO : suit la langue du système Android Wear OS.
 * - FR, EN, DE, ES, IT, PT : langues prises en charge.
 */
enum class WearLanguage(val code: String, val label: String) {
    AUTO("", "Automatique"),
    FR("fr", "Français"),
    EN("en", "English"),
    DE("de", "Deutsch"),
    ES("es", "Español"),
    IT("it", "Italiano"),
    PT("pt", "Português");

    fun next(): WearLanguage {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
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

/**
 * Unités de mesure des distances et des hauteurs :
 * - METRIC : mètres (m), kilomètres (km)
 * - IMPERIAL : pieds (ft), miles (mi)
 */
enum class DistanceUnit(val label: String, val shortLabel: String) {
    METRIC("Métrique (m, km)", "Métrique"),
    IMPERIAL("Impérial (ft, mi)", "Impérial");

    fun next(): DistanceUnit = if (this == METRIC) IMPERIAL else METRIC
}

fun formatDistance(meters: Int, unit: DistanceUnit): String {
    return when (unit) {
        DistanceUnit.METRIC -> {
            if (meters >= 1000) {
                String.format(java.util.Locale.US, "%.1f km", meters / 1000f)
            } else {
                "$meters m"
            }
        }
        DistanceUnit.IMPERIAL -> {
            val feet = (meters * 3.28084).toInt()
            val miles = meters / 1609.344f
            if (miles >= 0.2f) {
                String.format(java.util.Locale.US, "%.1f mi", miles)
            } else {
                "$feet ft"
            }
        }
    }
}

fun formatHeight(meters: Int?, unit: DistanceUnit): String? {
    if (meters == null || meters <= 0) return null
    return when (unit) {
        DistanceUnit.METRIC -> "$meters m"
        DistanceUnit.IMPERIAL -> "${(meters * 3.28084).toInt()} ft"
    }
}

/**
 * Ordre d'affichage des technologies et des fréquences :
 * - DESCENDING : 5G / 4G / 3G / 2G (3500 → 700 MHz, décroissant)
 * - ASCENDING : 2G / 3G / 4G / 5G (700 → 3500 MHz, croissant)
 */
enum class TechOrder(val label: String, val shortLabel: String, val description: String) {
    DESCENDING("5G / 4G / 3G / 2G", "5G → 2G", "Plus récente à plus ancienne (décroissant)"),
    ASCENDING("2G / 3G / 4G / 5G", "2G → 5G", "Plus ancienne à plus récente (croissant)");

    fun next(): TechOrder = if (this == DESCENDING) ASCENDING else DESCENDING
}

