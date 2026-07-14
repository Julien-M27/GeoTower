package fr.geotower.data.outages

import java.text.Normalizer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fonctions de normalisation PURES, port Kotlin des utilitaires de `docs/server/build_sites_hs.py`.
 *
 * Elles sont volontairement sans état ni dépendance (testables isolément). Convention identique au
 * builder serveur : les clés des lignes CSV sont supposées déjà normalisées via [normalizedHeader]
 * (c'est la tranche « téléchargement » qui s'en charge, comme `read_operator_csv` côté serveur).
 */

private const val EARTH_RADIUS_M = 6_371_000.0

private val DATE_INPUT_FORMATS = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd",
    "dd/MM/yyyy HH:mm:ss",
    "dd/MM/yyyy HH:mm",
    "dd/MM/yyyy",
)

private val SORTABLE_DATE_FORMATS = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")

internal fun cleanText(value: Any?): String? {
    if (value == null) return null
    val text = value.toString().trim().trim(Char(0xFEFF))
    if (text.isEmpty()) return null
    if (text.lowercase(Locale.ROOT) in setOf("null", "none", "nan")) return null
    return text
}

internal fun stripAccents(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

internal fun normalizedToken(value: Any?): String {
    val cleaned = cleanText(value) ?: ""
    val text = stripAccents(cleaned).uppercase(Locale.ROOT)
    return text.replace(Regex("[^A-Z0-9]+"), "")
}

internal fun normalizedHeader(value: Any?): String {
    val cleaned = cleanText(value) ?: ""
    val text = stripAccents(cleaned).lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
    return text.trim('_')
}

internal fun operatorKey(value: Any?): String? {
    val token = normalizedToken(value)
    return when {
        "BOUYGUES" in token || token == "BYTEL" || token == "BT" -> "bouygues"
        "ORANGE" in token -> "orange"
        "FREE" in token -> "free"
        "SFR" in token -> "sfr"
        else -> null
    }
}

internal fun parseCoordinate(value: Any?): Double? {
    val text = cleanText(value) ?: return null
    return text.replace(",", ".").toDoubleOrNull()
}

internal fun normalizeStatus(value: Any?): String? {
    val text = cleanText(value) ?: return null
    return stripAccents(text).uppercase(Locale.ROOT)
}

/**
 * Normalise une date vers `yyyy-MM-dd[ HH:mm:ss]`. Accepte les formats ISO et FR (`dd/MM/yyyy`).
 * Comme `normalize_date` côté serveur : renvoie le texte nettoyé tel quel si aucun format ne
 * correspond, et null si l'entrée est vide.
 */
internal fun normalizeDate(value: Any?): String? {
    val raw = cleanText(value) ?: return null
    val text = raw.replace("T", " ").replace("Z", "").trim()
    for (pattern in DATE_INPUT_FORMATS) {
        val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = formatter.parse(text, position)
        // Exige la consommation de TOUTE la chaîne (équivalent strptime), sinon un format court
        // « avalerait » le préfixe d'une date longue.
        if (parsed != null && position.index == text.length) {
            val outputPattern = if (pattern.contains("H")) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd"
            return SimpleDateFormat(outputPattern, Locale.US).format(parsed)
        }
    }
    return text
}

private fun sortableDateMillis(value: String?): Long? {
    if (value.isNullOrEmpty()) return null
    for (pattern in SORTABLE_DATE_FORMATS) {
        val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position)
        if (parsed != null && position.index == value.length) return parsed.time
    }
    return null
}

internal fun earliestDate(vararg values: String?): String? {
    val present = values.filterNotNull().filter { it.isNotEmpty() }
    val parsed = present.mapNotNull { v -> sortableDateMillis(v)?.let { it to v } }
    if (parsed.isNotEmpty()) return parsed.minByOrNull { it.first }!!.second
    return present.firstOrNull()
}

internal fun latestDate(vararg values: String?): String? {
    val present = values.filterNotNull().filter { it.isNotEmpty() }
    val parsed = present.mapNotNull { v -> sortableDateMillis(v)?.let { it to v } }
    if (parsed.isNotEmpty()) return parsed.maxByOrNull { it.first }!!.second
    return present.firstOrNull()
}

internal fun stationId(value: Any?): String? {
    val text = cleanText(value) ?: return null
    return if (text.all { it.isDigit() }) text.padStart(10, '0') else text
}

internal fun stationIdFromOperatorCode(value: Any?): String? {
    val text = cleanText(value) ?: return null
    return if (Regex("\\d{10}").matches(text)) stationId(text) else null
}

internal fun ownSiteValue(value: Any?): Int? = when (normalizedToken(value)) {
    "OUI", "YES", "1", "TRUE" -> 1
    "NON", "NO", "0", "FALSE" -> 0
    else -> null
}

internal fun freeInseeFromCodeSite(codeSite: String?): String? {
    if (codeSite.isNullOrEmpty()) return null
    val prefix = codeSite.substringBefore("_")
    return if (Regex("\\d{5}").matches(prefix)) prefix else null
}

/** Première valeur non vide parmi les colonnes candidates (équivalent `row_get`). */
internal fun rowGet(row: Map<String, String?>, vararg names: String): String? {
    for (name in names) {
        val value = cleanText(row[name])
        if (value != null) return value
    }
    return null
}

/** Distance haversine en mètres (équivalent `haversine_m`). Réutilisée par le géocodeur réel (T2). */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dPhi / 2.0) * Math.sin(dPhi / 2.0) +
        Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2.0) * Math.sin(dLambda / 2.0)
    return 2.0 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
}
