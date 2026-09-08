package fr.geotower.radio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional overrides for one carrier. A null value keeps the selected calculation profile's
 * default, which lets the advanced editor change one parameter without duplicating all defaults.
 */
data class ThroughputBandOverride(
    val bandwidthMHz: Double? = null,
    val duplexMode: DuplexMode? = null,
    val dlModulationOrder: Int? = null,
    val ulModulationOrder: Int? = null,
    val dlMimoLayers: Int? = null,
    val ulMimoLayers: Int? = null,
    val tddDlRatio: Double? = null,
    val tddUlRatio: Double? = null
) {
    fun isEmpty(): Boolean = this == ThroughputBandOverride()
}

data class ThroughputAggregationSettings(
    val lteMaxComponents: Int = 3,
    val nrMaxComponents: Int = 1
)

fun ThroughputProfile.withAggregationSettings(settings: ThroughputAggregationSettings): ThroughputProfile {
    return copy(
        lte = lte.copy(maxCaComponents = settings.lteMaxComponents.coerceIn(1, 5)),
        nr = nr.copy(maxCaComponents = settings.nrMaxComponents.coerceIn(1, 5))
    )
}

/** Compact, versioned persistence for per-frequency advanced settings. */
object ThroughputBandOverrideCodec {
    private const val VERSION = 1

    fun encode(overrides: Map<String, ThroughputBandOverride>): String {
        val bands = JSONArray()
        overrides.toSortedMap().forEach { (key, override) ->
            if (key.isBlank() || override.isEmpty()) return@forEach
            bands.put(JSONObject().put("key", key).apply {
                override.bandwidthMHz?.let { put("bandwidthMHz", it) }
                override.duplexMode?.let { put("duplexMode", it.name) }
                override.dlModulationOrder?.let { put("dlModulationOrder", it) }
                override.ulModulationOrder?.let { put("ulModulationOrder", it) }
                override.dlMimoLayers?.let { put("dlMimoLayers", it) }
                override.ulMimoLayers?.let { put("ulMimoLayers", it) }
                override.tddDlRatio?.let { put("tddDlRatio", it) }
                override.tddUlRatio?.let { put("tddUlRatio", it) }
            })
        }
        return JSONObject().put("version", VERSION).put("bands", bands).toString()
    }

    fun decode(raw: String?): Map<String, ThroughputBandOverride> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) return@runCatching emptyMap()
            val bands = root.optJSONArray("bands") ?: return@runCatching emptyMap()
            buildMap {
                for (index in 0 until bands.length()) {
                    val entry = bands.optJSONObject(index) ?: continue
                    val key = entry.optString("key").trim()
                    if (key.isBlank()) continue
                    val bandOverride = ThroughputBandOverride(
                        bandwidthMHz = validDouble(entry, "bandwidthMHz") { it in 0.1..1000.0 },
                        duplexMode = entry.optString("duplexMode")
                            .takeIf { it.isNotBlank() }
                            ?.let { rawDuplex -> enumValueOrNull<DuplexMode>(rawDuplex) },
                        dlModulationOrder = validInt(entry, "dlModulationOrder") { it in 1..12 },
                        ulModulationOrder = validInt(entry, "ulModulationOrder") { it in 1..12 },
                        dlMimoLayers = validInt(entry, "dlMimoLayers") { it in 1..16 },
                        ulMimoLayers = validInt(entry, "ulMimoLayers") { it in 1..16 },
                        tddDlRatio = validDouble(entry, "tddDlRatio") { it in 0.0..1.0 },
                        tddUlRatio = validDouble(entry, "tddUlRatio") { it in 0.0..1.0 }
                    )
                    if (!bandOverride.isEmpty()) put(key, bandOverride)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun validDouble(json: JSONObject, name: String, predicate: (Double) -> Boolean): Double? {
        if (!json.has(name) || json.isNull(name)) return null
        val value = json.optDouble(name, Double.NaN)
        return value.takeIf { !it.isNaN() && !it.isInfinite() && predicate(it) }
    }

    private fun validInt(json: JSONObject, name: String, predicate: (Int) -> Boolean): Int? {
        if (!json.has(name) || json.isNull(name)) return null
        val value = json.optInt(name, Int.MIN_VALUE)
        return value.takeIf(predicate)
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? =
        runCatching { enumValueOf<T>(raw) }.getOrNull()
}
