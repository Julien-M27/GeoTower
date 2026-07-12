package fr.geotower.data.build

/**
 * Conversions specifiques a la base radio, portees a l'identique depuis
 * `docs/server/fr_radio_db_builder.py` (`dms_to_e6`, `frequency_to_khz`, `format_freq_range`).
 *
 * Distinctes de [AnfrParsing] (qui vise `geotower_fr.db`) : la base radio lit des coordonnees en
 * degres/minutes/secondes depuis `SUP_SUPPORT` et exprime les frequences en kHz (pas en MHz).
 * Logique pure -> testable en JVM, parite garantie avec le builder serveur.
 */
object RadioParsing {

    /**
     * Python `dms_to_e6` : degres/minutes/secondes -> micro-degres signes (S/W -> negatif),
     * `round(valeur * 1e6)`. `null` si le degre est absent.
     */
    fun dmsToE6(deg: String?, minute: String?, second: String?, direction: String?): Int? {
        val degValue = AnfrParsing.floatOrNone(deg) ?: return null
        val minuteValue = AnfrParsing.floatOrNone(minute) ?: 0.0
        val secondValue = AnfrParsing.floatOrNone(second) ?: 0.0
        var value = degValue + minuteValue / 60.0 + secondValue / 3600.0
        if (AnfrParsing.cleanText(direction).uppercase() in setOf("S", "W")) value = -value
        return Math.round(value * 1_000_000.0).toInt()
    }

    /**
     * Python `frequency_to_khz` : convertit `value` exprimee dans `unit` (G/M/k/H) en kHz entiers.
     * Unite par defaut ou inconnue -> MHz (x1000), comme le serveur.
     */
    fun frequencyToKhz(value: String?, unit: String?): Int? {
        val number = AnfrParsing.floatOrNone(value) ?: return null
        val unitUpper = AnfrParsing.cleanText(unit).ifEmpty { "M" }.uppercase()
        return when {
            unitUpper.startsWith("G") -> Math.round(number * 1_000_000.0).toInt()
            unitUpper.startsWith("M") -> Math.round(number * 1_000.0).toInt()
            unitUpper.startsWith("K") -> Math.round(number).toInt()
            unitUpper.startsWith("H") -> Math.round(number / 1_000.0).toInt()
            else -> Math.round(number * 1_000.0).toInt()
        }
    }

    /** Python `format_freq_range` : "f_deb-f_fin SUFFIX" (SUFFIX = GHz/MHz/kHz/Hz). */
    fun formatFreqRange(startRaw: String, endRaw: String, unit: String): String {
        val first = (unit.ifEmpty { "M" }).uppercase().substring(0, 1)
        val suffix = when (first) {
            "G" -> "GHz"
            "M" -> "MHz"
            "K" -> "kHz"
            "H" -> "Hz"
            else -> "${unit}Hz"
        }
        return "$startRaw-$endRaw $suffix"
    }
}
