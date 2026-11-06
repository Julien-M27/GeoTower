package fr.geotower.wear.data.model

object BandFormatter {

    fun formatBands(bandMask: Int, techMask: Int): String {
        if (bandMask == 0) {
            val parts = mutableListOf<String>()
            if (techMask and 8 != 0) parts.add("5G: 3500")
            if (techMask and 4 != 0) parts.add("4G: 700, 1800, 2100")
            if (techMask and 2 != 0) parts.add("3G: 900")
            if (techMask and 1 != 0) parts.add("2G: 900")
            return if (parts.isNotEmpty()) parts.joinToString(" • ") + " MHz" else "4G: 800, 1800, 2100 MHz"
        }

        val f5g = mutableListOf<String>()
        if (bandMask and (1 shl 12) != 0) f5g.add("3500")
        if (bandMask and (1 shl 10) != 0) f5g.add("700")
        if (bandMask and (1 shl 11) != 0) f5g.add("2100")
        if (bandMask and (1 shl 21) != 0) f5g.add("1400")
        if (bandMask and (1 shl 13) != 0) f5g.add("26G")

        val f4g = mutableListOf<String>()
        if (bandMask and (1 shl 4) != 0) f4g.add("700")
        if (bandMask and (1 shl 5) != 0) f4g.add("800")
        if (bandMask and (1 shl 6) != 0) f4g.add("900")
        if (bandMask and (1 shl 7) != 0) f4g.add("1800")
        if (bandMask and (1 shl 8) != 0) f4g.add("2100")
        if (bandMask and (1 shl 9) != 0) f4g.add("2600")

        val f3g = mutableListOf<String>()
        if (bandMask and (1 shl 2) != 0) f3g.add("900")
        if (bandMask and (1 shl 3) != 0) f3g.add("2100")

        val f2g = mutableListOf<String>()
        if (bandMask and (1 shl 0) != 0) f2g.add("900")
        if (bandMask and (1 shl 1) != 0) f2g.add("1800")

        val groups = mutableListOf<String>()
        if (f5g.isNotEmpty()) groups.add("5G: ${f5g.joinToString(", ")}")
        if (f4g.isNotEmpty()) groups.add("4G: ${f4g.joinToString(", ")}")
        if (f3g.isNotEmpty()) groups.add("3G: ${f3g.joinToString(", ")}")
        if (f2g.isNotEmpty()) groups.add("2G: ${f2g.joinToString(", ")}")

        return if (groups.isNotEmpty()) groups.joinToString(" • ") + " MHz" else "4G: 800, 1800, 2100 MHz"
    }

    /**
     * Normalise toute chaîne de fréquences pour garantir le format identique à Garmin :
     * "5G: 3500 • 4G: 700, 1800, 2100 • 3G: 900 • 2G: 900 MHz"
     */
    fun normalizeBandsString(raw: String?, techStr: String? = null): String {
        if (raw.isNullOrBlank()) {
            return if (!techStr.isNullOrBlank() && techStr.contains("5G")) {
                "4G: 700, 1800, 2100 • 5G: 3500 MHz"
            } else {
                "4G: 800, 1800, 2100 MHz"
            }
        }

        // Si la chaîne est déjà au format Garmin "5G: ... • 4G: ... MHz"
        if (raw.contains(":") && (raw.contains("4G:") || raw.contains("5G:") || raw.contains("3G:") || raw.contains("2G:"))) {
            return if (raw.endsWith("MHz")) raw else "$raw MHz"
        }

        // Sinon, c'est une liste brute de fréquences, par ex "700, 800, 1800, 2100, 3500 MHz"
        val clean = raw.replace("MHz", "").replace("•", ",").replace(";", ",")
        val tokens = clean.split(",").map { it.trim() }.filter { it.isNotBlank() }

        val f5g = mutableListOf<String>()
        val f4g = mutableListOf<String>()
        val f3g = mutableListOf<String>()
        val f2g = mutableListOf<String>()

        val is5gPresent = techStr?.contains("5G", ignoreCase = true) == true
        val is4gPresent = techStr?.contains("4G", ignoreCase = true) != false // default true

        for (token in tokens) {
            when (token) {
                "3500", "26G", "n78", "n28", "n1" -> f5g.add(token)
                "700" -> {
                    if (is5gPresent && !f5g.contains("700")) f5g.add("700")
                    if (is4gPresent && !f4g.contains("700")) f4g.add("700")
                }
                "800", "1800", "2600" -> f4g.add(token)
                "2100" -> {
                    if (is5gPresent && !f5g.contains("2100")) f5g.add("2100")
                    if (is4gPresent && !f4g.contains("2100")) f4g.add("2100")
                }
                "900" -> {
                    f3g.add("900")
                    f2g.add("900")
                }
                else -> f4g.add(token)
            }
        }

        val groups = mutableListOf<String>()
        if (f5g.isNotEmpty()) groups.add("5G: ${f5g.distinct().joinToString(", ")}")
        if (f4g.isNotEmpty()) groups.add("4G: ${f4g.distinct().joinToString(", ")}")
        if (f3g.isNotEmpty()) groups.add("3G: ${f3g.distinct().joinToString(", ")}")
        if (f2g.isNotEmpty()) groups.add("2G: ${f2g.distinct().joinToString(", ")}")

        return if (groups.isNotEmpty()) groups.joinToString(" • ") + " MHz" else "$raw MHz"
    }
}
