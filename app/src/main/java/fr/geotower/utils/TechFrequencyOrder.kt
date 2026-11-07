package fr.geotower.utils

import fr.geotower.R

enum class TechFrequencyOrder(val id: String) {
    DESCENDING("descending"), // 5G -> 2G
    ASCENDING("ascending");   // 2G -> 5G

    companion object {
        fun fromId(id: String?): TechFrequencyOrder {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DESCENDING
        }
    }
}

enum class ScreenPageTarget(val prefKey: String, val labelRes: Int) {
    GLOBAL("site_order_global", R.string.pref_order_global),
    SITE_DETAIL("site_order_page_detail", R.string.pref_order_page_detail),
    MAP("site_order_page_map", R.string.pref_order_page_map),
    SEARCH_LIST("site_order_page_search", R.string.pref_order_page_search),
    LIVE_MONITOR("site_order_page_live", R.string.pref_order_page_live);
}

object TechFrequencyOrderHelper {
    fun technologiesOrder(order: TechFrequencyOrder): List<String> = when (order) {
        TechFrequencyOrder.DESCENDING -> listOf("5G", "4G", "3G", "2G", "FH")
        TechFrequencyOrder.ASCENDING -> listOf("2G", "3G", "4G", "5G", "FH")
    }

    fun frequenciesOrder5G(order: TechFrequencyOrder): List<String> = when (order) {
        TechFrequencyOrder.DESCENDING -> listOf("26000", "4200", "3500", "2100", "1400", "700")
        TechFrequencyOrder.ASCENDING -> listOf("700", "1400", "2100", "3500", "4200", "26000")
    }

    fun frequenciesOrder4G(order: TechFrequencyOrder): List<String> = when (order) {
        TechFrequencyOrder.DESCENDING -> listOf("2600", "2100", "1800", "900", "800", "700")
        TechFrequencyOrder.ASCENDING -> listOf("700", "800", "900", "1800", "2100", "2600")
    }

    fun frequenciesOrder3G(order: TechFrequencyOrder): List<String> = when (order) {
        TechFrequencyOrder.DESCENDING -> listOf("2100", "900")
        TechFrequencyOrder.ASCENDING -> listOf("900", "2100")
    }

    fun frequenciesOrder2G(order: TechFrequencyOrder): List<String> = when (order) {
        TechFrequencyOrder.DESCENDING -> listOf("1800", "900")
        TechFrequencyOrder.ASCENDING -> listOf("900", "1800")
    }
}
