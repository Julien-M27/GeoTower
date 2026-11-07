package fr.geotower.utils

fun formatTechnologies(
    tech: String?,
    txtUnknown: String,
    order: TechFrequencyOrder? = null
): String {
    if (tech == null) return txtUnknown
    val parts = tech
        .split(Regex("[/,\\-]"))
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }

    if (parts.isEmpty()) return ""

    val sorted = if (order != null) {
        fun rank(t: String): Int {
            val is5G = t.contains("5G") || t.contains("NR")
            val is4G = t.contains("4G") || t.contains("LTE")
            val is3G = t.contains("3G") || t.contains("UMTS")
            val is2G = t.contains("2G") || t.contains("GSM")
            val isFH = t.contains("FH")
            return when (order) {
                TechFrequencyOrder.DESCENDING -> when {
                    is5G -> 1
                    is4G -> 2
                    is3G -> 3
                    is2G -> 4
                    isFH -> 5
                    else -> 6
                }
                TechFrequencyOrder.ASCENDING -> when {
                    is2G -> 1
                    is3G -> 2
                    is4G -> 3
                    is5G -> 4
                    isFH -> 5
                    else -> 6
                }
            }
        }
        parts.distinct().sortedWith(compareBy({ rank(it) }, { it }))
    } else {
        parts.sortedDescending()
    }

    return sorted.joinToString(" - ")
}
