package fr.geotower.utils

import android.content.SharedPreferences
import java.util.Locale

enum class StatsDisplayMode(val storageKey: String) {
    Sites("sites"),
    Active("active"),
    Both("both");

    companion object {
        fun fromStorageKey(value: String?): StatsDisplayMode {
            return values().firstOrNull { it.storageKey == value } ?: Both
        }
    }
}

object StatsPreferences {
    const val PREF_DISPLAY_MODE = "page_stats_display_mode"
    const val PREF_STATS_ORDER = "page_stats_order"

    val defaultStatsBlockOrder = listOf("supports", "5G", "4G", "3G", "2G")
    val defaultTechOrder = listOf("5G", "4G", "3G", "2G")

    fun displayMode(prefs: SharedPreferences): StatsDisplayMode {
        return StatsDisplayMode.fromStorageKey(prefs.getString(PREF_DISPLAY_MODE, StatsDisplayMode.Both.storageKey))
    }

    fun statsBlockOrder(prefs: SharedPreferences): List<String> {
        val savedOrder = prefs.getString(PREF_STATS_ORDER, defaultStatsBlockOrder.joinToString(","))
        return normalizeStatsBlockOrder(savedOrder?.split(",").orEmpty())
    }

    fun isStatsBlockVisible(prefs: SharedPreferences, blockId: String): Boolean {
        return prefs.getBoolean(statsBlockVisiblePrefKey(blockId), true)
    }

    fun statsFrequencyOrder(prefs: SharedPreferences, tech: String): List<String> {
        val normalizedTech = normalizeTech(tech)
        val defaultOrder = defaultFrequencyOrder(normalizedTech)
        val savedOrder = prefs.getString(statsFrequencyOrderPrefKey(normalizedTech), defaultOrder.joinToString(","))
        return normalizeFrequencyOrder(normalizedTech, savedOrder?.split(",").orEmpty())
    }

    fun isStatsFrequencyVisible(prefs: SharedPreferences, tech: String, frequencyId: String): Boolean {
        return prefs.getBoolean(statsFrequencyVisiblePrefKey(tech, frequencyId), true)
    }

    fun statsBlockVisiblePrefKey(blockId: String): String {
        return "page_stats_${normalizeStatsBlockId(blockId).lowercase(Locale.ROOT)}"
    }

    fun statsFrequencyOrderPrefKey(tech: String): String {
        return "page_stats_freq_${normalizeTech(tech).lowercase(Locale.ROOT)}_order"
    }

    fun statsFrequencyVisiblePrefKey(tech: String, frequencyId: String): String {
        return "page_stats_freq_${normalizeTech(tech).lowercase(Locale.ROOT)}_${frequencyId.trim()}"
    }

    fun defaultFrequencyOrder(tech: String): List<String> {
        return when (normalizeTech(tech)) {
            "5G" -> listOf("3500", "2100", "700", "26000")
            "4G" -> listOf("2600", "2100", "1800", "900", "800", "700")
            "3G" -> listOf("2100", "900")
            "2G" -> listOf("1800", "900")
            else -> emptyList()
        }
    }

    fun normalizeStatsBlockOrder(order: List<String>): List<String> {
        val normalized = order
            .mapNotNull(::normalizeStatsBlockIdOrNull)
            .distinct()
            .toMutableList()
        defaultStatsBlockOrder.forEach { block ->
            if (!normalized.contains(block)) normalized.add(block)
        }
        return normalized
    }

    fun normalizeFrequencyOrder(tech: String, order: List<String>): List<String> {
        val defaultOrder = defaultFrequencyOrder(tech)
        val known = defaultOrder.toSet()
        val normalized = order
            .map { it.trim() }
            .filter { it in known }
            .distinct()
            .toMutableList()
        defaultOrder.forEach { frequencyId ->
            if (!normalized.contains(frequencyId)) normalized.add(frequencyId)
        }
        return normalized
    }

    fun normalizeTech(tech: String): String {
        return tech.trim().uppercase(Locale.ROOT)
    }

    fun frequencyLabel(frequencyId: String): String {
        return if (frequencyId == "26000") "26 GHz" else "$frequencyId MHz"
    }

    private fun normalizeStatsBlockId(blockId: String): String {
        return normalizeStatsBlockIdOrNull(blockId) ?: blockId.trim()
    }

    private fun normalizeStatsBlockIdOrNull(blockId: String): String? {
        val key = blockId.trim().lowercase(Locale.ROOT)
        return defaultStatsBlockOrder.firstOrNull { it.lowercase(Locale.ROOT) == key }
    }
}
