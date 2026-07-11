package fr.geotower.data.build

/**
 * Fonctions de parsing / normalisation portees a l'identique depuis le builder
 * serveur `docs/server/build_fr_anfr_db.py` (clean_text, normalize_id_anfr,
 * int_or_none, float_or_none, parse_coordinates, frequency_to_mhz,
 * format_band_range, range_overlaps, is_active_status).
 *
 * Logique pure, sans dependance Android / SQLite, pour rester testable en JVM et
 * garantir la parite avec la generation serveur de `geotower_fr.db`.
 */
object AnfrParsing {

    private val COORD_REGEX = Regex("""[-+]?(?:\d+(?:[.,]\d*)?|[.,]\d+)""")

    /** Python `clean_text` : trim, chaine vide si null. */
    fun cleanText(value: String?): String = value?.trim() ?: ""

    /** Python `normalize_id_anfr` : zfill(10) si la valeur est entierement numerique. */
    fun normalizeIdAnfr(value: String?): String {
        val v = cleanText(value)
        return if (v.isNotEmpty() && v.all { it in '0'..'9' }) v.padStart(10, '0') else v
    }

    /** Python `int_or_none` : null si vide / "N/A", sinon int(float(...)) avec virgule -> point. */
    fun intOrNone(value: String?): Int? {
        val v = cleanText(value)
        if (v.isEmpty() || v.uppercase() == "N/A") return null
        val parsed = v.replace(",", ".").toDoubleOrNull() ?: return null
        return parsed.toInt()
    }

    /** Python `float_or_none`. */
    fun floatOrNone(value: String?): Double? {
        val v = cleanText(value)
        if (v.isEmpty() || v.uppercase() == "N/A") return null
        return v.replace(",", ".").toDoubleOrNull()
    }

    /** Python `parse_coordinates` : (latitude, longitude), (0.0, 0.0) si non exploitable. */
    fun parseCoordinates(rawCoord: String?): Pair<Double, Double> {
        val matches = COORD_REGEX.findAll(cleanText(rawCoord)).map { it.value }.toList()
        if (matches.size < 2) return 0.0 to 0.0
        val lat = matches[0].replace(",", ".").toDoubleOrNull() ?: return 0.0 to 0.0
        val lon = matches[1].replace(",", ".").toDoubleOrNull() ?: return 0.0 to 0.0
        return lat to lon
    }

    /** Python `frequency_to_mhz` : convertit `number` exprime dans `unit` (G/M/K/H) en MHz. */
    fun frequencyToMhz(number: Double?, unit: String?): Double? {
        if (number == null) return null
        val unitUpper = cleanText(unit).ifEmpty { "M" }.uppercase()
        return when {
            unitUpper.startsWith("G") -> number * 1000.0
            unitUpper.startsWith("K") -> number / 1000.0
            unitUpper.startsWith("H") -> number / 1_000_000.0
            else -> number
        }
    }

    /** Python `format_band_range` : "f_debut-f_fin SUFFIX" (SUFFIX = GHz/MHz/kHz/Hz). */
    fun formatBandRange(fDebut: String, fFin: String, unite: String): String {
        val first = cleanText(unite).ifEmpty { "M" }.uppercase().substring(0, 1)
        val suffix = when (first) {
            "G" -> "GHz"
            "M" -> "MHz"
            "K" -> "kHz"
            "H" -> "Hz"
            else -> "${unite}Hz"
        }
        return "$fDebut-$fFin $suffix"
    }

    /** Python `range_overlaps` : true si [start,end] recoupe [low,high]. */
    fun rangeOverlaps(start: Double?, end: Double?, low: Double, high: Double): Boolean {
        if (start == null || end == null) return false
        val lowValue = minOf(start, end)
        val highValue = maxOf(start, end)
        return lowValue <= high && highValue >= low
    }

    /** Python `is_active_status` : "EN SERVICE" ou "TECHNIQUEMENT OP" (insensible a la casse). */
    fun isActiveStatus(status: String?): Boolean {
        val upper = cleanText(status).uppercase()
        return upper.contains("EN SERVICE") || upper.contains("TECHNIQUEMENT OP")
    }
}
