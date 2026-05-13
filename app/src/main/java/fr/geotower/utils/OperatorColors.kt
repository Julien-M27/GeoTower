package fr.geotower.utils

import android.graphics.Color as AndroidColor

data class OperatorColorSpec(
    val key: String,
    val label: String,
    val colorHex: String,
    val colorArgb: Long,
    val aliases: List<String>
)

object OperatorColors {
    const val ORANGE_KEY = "ORANGE"
    const val BOUYGUES_KEY = "BOUYGUES"
    const val SFR_KEY = "SFR"
    const val FREE_KEY = "FREE"

    const val ORANGE_HEX = "#FF7900"
    const val BOUYGUES_HEX = "#009FE3"
    const val SFR_HEX = "#E2001A"
    const val FREE_HEX = "#757575"
    const val UNKNOWN_HEX = "#808080"

    const val ORANGE_ARGB: Long = 0xFFFF7900
    const val BOUYGUES_ARGB: Long = 0xFF009FE3
    const val SFR_ARGB: Long = 0xFFE2001A
    const val FREE_ARGB: Long = 0xFF757575
    const val UNKNOWN_ARGB: Long = 0xFF808080

    val all: List<OperatorColorSpec> = listOf(
        OperatorColorSpec(ORANGE_KEY, "Orange", ORANGE_HEX, ORANGE_ARGB, listOf("ORANGE", "ORANGE FRANCE")),
        OperatorColorSpec(BOUYGUES_KEY, "Bouygues Telecom", BOUYGUES_HEX, BOUYGUES_ARGB, listOf("BOUYGUES", "BOUYGUES TELECOM")),
        OperatorColorSpec(SFR_KEY, "SFR", SFR_HEX, SFR_ARGB, listOf("SFR", "SOCIETE FRANCAISE DU RADIOTELEPHONE")),
        OperatorColorSpec(FREE_KEY, "Free Mobile", FREE_HEX, FREE_ARGB, listOf("FREE", "FREE MOBILE"))
    )

    val orderedKeys: List<String> = all.map { it.key }

    private val specsByKey: Map<String, OperatorColorSpec> = all.associateBy { it.key }

    fun keyFor(raw: String?): String? {
        val normalized = raw?.uppercase()?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return all.firstOrNull { spec ->
            spec.aliases.any { alias -> normalized.contains(alias) }
        }?.key
    }

    fun colorHex(raw: String?, fallback: String = UNKNOWN_HEX): String {
        return keyFor(raw)?.let { specsByKey[it]?.colorHex } ?: fallback
    }

    fun colorInt(raw: String?, fallback: Int = AndroidColor.GRAY): Int {
        return keyFor(raw)?.let { colorIntForKey(it) } ?: fallback
    }

    fun colorArgbForKey(key: String, fallback: Long = UNKNOWN_ARGB): Long {
        return specsByKey[key]?.colorArgb ?: fallback
    }

    fun colorIntForKey(key: String, fallback: Int = AndroidColor.GRAY): Int {
        return specsByKey[key]?.colorHex?.let(AndroidColor::parseColor) ?: fallback
    }

    fun androidColorMap(): Map<String, Int> = specsByKey.mapValues { (_, spec) ->
        AndroidColor.parseColor(spec.colorHex)
    }
}
