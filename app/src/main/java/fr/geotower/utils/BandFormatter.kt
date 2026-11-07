package fr.geotower.utils

object BandFormatter {

    fun toFreqNum(token: String): Int {
        val clean = token.trim()
        if (clean.equals("26G", ignoreCase = true) || clean.equals("26GHz", ignoreCase = true)) return 26000
        if (clean.equals("n78", ignoreCase = true)) return 3500
        if (clean.equals("n28", ignoreCase = true)) return 700
        if (clean.equals("n1", ignoreCase = true)) return 2100
        val digits = clean.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    fun formatTechnologies(techMask: Int, order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING): String {
        if (techMask == 0) return "4G"
        val list = mutableListOf<String>()
        if (order == TechFrequencyOrder.DESCENDING) {
            if (techMask and 8 != 0) list.add("5G")
            if (techMask and 4 != 0) list.add("4G")
            if (techMask and 2 != 0) list.add("3G")
            if (techMask and 1 != 0) list.add("2G")
        } else {
            if (techMask and 1 != 0) list.add("2G")
            if (techMask and 2 != 0) list.add("3G")
            if (techMask and 4 != 0) list.add("4G")
            if (techMask and 8 != 0) list.add("5G")
        }
        return if (list.isEmpty()) "4G" else list.joinToString(" / ")
    }

    fun formatTechnologies(techStr: String?, order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING): String {
        if (techStr.isNullOrBlank()) return "4G"
        val has5g = techStr.contains("5G", ignoreCase = true)
        val has4g = techStr.contains("4G", ignoreCase = true)
        val has3g = techStr.contains("3G", ignoreCase = true)
        val has2g = techStr.contains("2G", ignoreCase = true)

        val list = mutableListOf<String>()
        if (order == TechFrequencyOrder.DESCENDING) {
            if (has5g) list.add("5G")
            if (has4g) list.add("4G")
            if (has3g) list.add("3G")
            if (has2g) list.add("2G")
        } else {
            if (has2g) list.add("2G")
            if (has3g) list.add("3G")
            if (has4g) list.add("4G")
            if (has5g) list.add("5G")
        }
        return if (list.isEmpty()) techStr else list.joinToString(" / ")
    }

    fun formatBands(bandMask: Int, techMask: Int, order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING): String {
        val f5g = mutableListOf<String>()
        val f4g = mutableListOf<String>()
        val f3g = mutableListOf<String>()
        val f2g = mutableListOf<String>()

        if (bandMask == 0) {
            if (techMask and 8 != 0) f5g.add("3500")
            if (techMask and 4 != 0) { f4g.add("700"); f4g.add("1800"); f4g.add("2100") }
            if (techMask and 2 != 0) f3g.add("900")
            if (techMask and 1 != 0) f2g.add("900")
        } else {
            if (bandMask and (1 shl 12) != 0) f5g.add("3500")
            if (bandMask and (1 shl 10) != 0) f5g.add("700")
            if (bandMask and (1 shl 11) != 0) f5g.add("2100")
            if (bandMask and (1 shl 21) != 0) f5g.add("1400")
            if (bandMask and (1 shl 13) != 0) f5g.add("26G")

            if (bandMask and (1 shl 4) != 0) f4g.add("700")
            if (bandMask and (1 shl 5) != 0) f4g.add("800")
            if (bandMask and (1 shl 6) != 0) f4g.add("900")
            if (bandMask and (1 shl 7) != 0) f4g.add("1800")
            if (bandMask and (1 shl 8) != 0) f4g.add("2100")
            if (bandMask and (1 shl 9) != 0) f4g.add("2600")

            if (bandMask and (1 shl 2) != 0) f3g.add("900")
            if (bandMask and (1 shl 3) != 0) f3g.add("2100")

            if (bandMask and (1 shl 0) != 0) f2g.add("900")
            if (bandMask and (1 shl 1) != 0) f2g.add("1800")
        }

        return assembleBands(f5g, f4g, f3g, f2g, order)
    }

    fun reorderBandsString(raw: String?, techStr: String? = null, order: TechFrequencyOrder = TechFrequencyOrder.DESCENDING): String {
        if (raw.isNullOrBlank()) {
            val is5g = techStr?.contains("5G", ignoreCase = true) == true
            return if (order == TechFrequencyOrder.DESCENDING) {
                if (is5g) "5G: 3500 • 4G: 2100, 1800, 700 MHz" else "4G: 2100, 1800, 800 MHz"
            } else {
                if (is5g) "4G: 700, 1800, 2100 • 5G: 3500 MHz" else "4G: 800, 1800, 2100 MHz"
            }
        }

        if (raw.contains(":") && (raw.contains("4G:") || raw.contains("5G:") || raw.contains("3G:") || raw.contains("2G:"))) {
            val f5g = mutableListOf<String>()
            val f4g = mutableListOf<String>()
            val f3g = mutableListOf<String>()
            val f2g = mutableListOf<String>()

            val sections = raw.replace("MHz", "").split("•", "·", ";")
            for (sec in sections) {
                val s = sec.trim()
                if (s.isBlank()) continue
                val parts = s.split(":")
                if (parts.size >= 2) {
                    val gen = parts[0].trim().uppercase()
                    val freqs = parts[1].split(",").map { it.trim() }.filter { it.isNotBlank() }
                    when {
                        gen.contains("5G") -> f5g.addAll(freqs)
                        gen.contains("4G") -> f4g.addAll(freqs)
                        gen.contains("3G") -> f3g.addAll(freqs)
                        gen.contains("2G") -> f2g.addAll(freqs)
                    }
                }
            }
            return assembleBands(f5g, f4g, f3g, f2g, order)
        }

        val clean = raw.replace("MHz", "").replace("•", ",").replace("·", ",").replace(";", ",")
        val tokens = clean.split(",").map { it.trim() }.filter { it.isNotBlank() }

        val f5g = mutableListOf<String>()
        val f4g = mutableListOf<String>()
        val f3g = mutableListOf<String>()
        val f2g = mutableListOf<String>()

        val is5gPresent = techStr?.contains("5G", ignoreCase = true) == true
        val is4gPresent = techStr?.contains("4G", ignoreCase = true) != false

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

        return assembleBands(f5g, f4g, f3g, f2g, order)
    }

    private fun assembleBands(
        f5g: List<String>,
        f4g: List<String>,
        f3g: List<String>,
        f2g: List<String>,
        order: TechFrequencyOrder
    ): String {
        val groups = mutableListOf<String>()

        if (order == TechFrequencyOrder.DESCENDING) {
            val s5g = f5g.distinct().sortedWith { a, b -> toFreqNum(b).compareTo(toFreqNum(a)) }
            val s4g = f4g.distinct().sortedWith { a, b -> toFreqNum(b).compareTo(toFreqNum(a)) }
            val s3g = f3g.distinct().sortedWith { a, b -> toFreqNum(b).compareTo(toFreqNum(a)) }
            val s2g = f2g.distinct().sortedWith { a, b -> toFreqNum(b).compareTo(toFreqNum(a)) }

            if (s5g.isNotEmpty()) groups.add("5G: ${s5g.joinToString(", ")}")
            if (s4g.isNotEmpty()) groups.add("4G: ${s4g.joinToString(", ")}")
            if (s3g.isNotEmpty()) groups.add("3G: ${s3g.joinToString(", ")}")
            if (s2g.isNotEmpty()) groups.add("2G: ${s2g.joinToString(", ")}")
        } else {
            val s2g = f2g.distinct().sortedWith { a, b -> toFreqNum(a).compareTo(toFreqNum(b)) }
            val s3g = f3g.distinct().sortedWith { a, b -> toFreqNum(a).compareTo(toFreqNum(b)) }
            val s4g = f4g.distinct().sortedWith { a, b -> toFreqNum(a).compareTo(toFreqNum(b)) }
            val s5g = f5g.distinct().sortedWith { a, b -> toFreqNum(a).compareTo(toFreqNum(b)) }

            if (s2g.isNotEmpty()) groups.add("2G: ${s2g.joinToString(", ")}")
            if (s3g.isNotEmpty()) groups.add("3G: ${s3g.joinToString(", ")}")
            if (s4g.isNotEmpty()) groups.add("4G: ${s4g.joinToString(", ")}")
            if (s5g.isNotEmpty()) groups.add("5G: ${s5g.joinToString(", ")}")
        }

        return if (groups.isNotEmpty()) groups.joinToString(" • ") + " MHz" else "4G: 800, 1800, 2100 MHz"
    }
}
