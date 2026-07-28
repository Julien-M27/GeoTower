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
        if (v.isEmpty() || v.equals("N/A", ignoreCase = true)) return null
        val parsed = decimalToDouble(v) ?: return null
        return parsed.toInt()
    }

    /** Python `float_or_none`. */
    fun floatOrNone(value: String?): Double? {
        val v = cleanText(value)
        if (v.isEmpty() || v.equals("N/A", ignoreCase = true)) return null
        return decimalToDouble(v)
    }

    /**
     * `toDoubleOrNull` en acceptant la virgule decimale, sans allouer quand il n'y en a pas.
     * [intOrNone] et [floatOrNone] sont appelees des millions de fois pendant la generation locale
     * (azimuts, hauteurs, identifiants de nature / proprietaire / type d'antenne...) : le
     * `replace` et le `uppercase` inconditionnels d'origine y allouaient deux chaines par appel.
     */
    private fun decimalToDouble(value: String): Double? =
        (if (value.indexOf(',') >= 0) value.replace(',', '.') else value).toDoubleOrNull()

    /**
     * Python `format_antenna_dimension` : dimension physique du panneau (`AER_NB_DIMENSION`, en
     * metres) formatee pour la chaine `physique` -> `"1,5m"`, ou `null` si l'ANFR ne la declare
     * pas (vide, "N/A" ou 0). La valeur brute est conservee telle quelle (notation francaise),
     * comme la hauteur `aer_nb_alt_bas` inseree brute dans la meme chaine.
     */
    fun antennaDimensionText(value: String?): String? {
        val v = cleanText(value)
        val number = floatOrNone(v) ?: return null
        if (number <= 0.0) return null
        return "${v}m"
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
        return when (unitInitial(unit)) {
            'G' -> number * 1000.0
            'K' -> number / 1000.0
            'H' -> number / 1_000_000.0
            else -> number
        }
    }

    /** Python `format_band_range` : "f_debut-f_fin SUFFIX" (SUFFIX = GHz/MHz/kHz/Hz). */
    fun formatBandRange(fDebut: String, fFin: String, unite: String): String {
        val suffix = when (unitInitial(unite)) {
            'G' -> "GHz"
            'M' -> "MHz"
            'K' -> "kHz"
            'H' -> "Hz"
            else -> "${unite}Hz"
        }
        return "$fDebut-$fFin $suffix"
    }

    /**
     * Initiale majuscule de l'unite ANFR, `M` par defaut (unite vide ou absente) — equivalent de
     * `clean_text(unite) or "M"` en majuscules, mais sans allouer la chaine intermediaire :
     * [formatBandRange] est appele une fois par bande, soit des millions de fois.
     */
    private fun unitInitial(unit: String?): Char {
        if (unit == null) return 'M'
        var start = 0
        var end = unit.length
        while (start < end && unit[start].isWhitespace()) start++
        while (end > start && unit[end - 1].isWhitespace()) end--
        return if (start >= end) 'M' else unit[start].uppercaseChar()
    }

    /** Python `range_overlaps` : true si [start,end] recoupe [low,high]. */
    fun rangeOverlaps(start: Double?, end: Double?, low: Double, high: Double): Boolean {
        if (start == null || end == null) return false
        val lowValue = minOf(start, end)
        val highValue = maxOf(start, end)
        return lowValue <= high && highValue >= low
    }

    /**
     * Python `is_active_status` : "EN SERVICE" ou "TECHNIQUEMENT OP" (insensible a la casse).
     * La recherche insensible a la casse evite le `uppercase()` prealable — donc une allocation
     * par ligne de l'observatoire — pour un resultat identique.
     */
    fun isActiveStatus(status: String?): Boolean {
        if (status == null) return false
        return status.contains("EN SERVICE", ignoreCase = true) ||
            status.contains("TECHNIQUEMENT OP", ignoreCase = true)
    }
}
