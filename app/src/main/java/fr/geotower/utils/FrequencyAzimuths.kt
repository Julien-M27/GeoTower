package fr.geotower.utils

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.Locale

/** Parsing and stable JSON encoding for the per-system ANFR azimuth mapping. */
object FrequencyAzimuths {
    private val gson = Gson()

    fun parseList(value: String?): List<Int> = value.orEmpty()
        .split('|')
        .mapNotNull(::parseAzimuth)
        .distinct()
        .sorted()

    fun encode(mapping: Map<String, Set<Int>>): String? {
        val normalized = mapping.entries
            .groupBy { normalizeSystem(it.key) }
            .mapNotNull { (key, entries) ->
                val azimuths = entries.flatMap { it.value }.mapNotNull(::normalizeAzimuth).distinct().sorted()
                if (key.isBlank() || azimuths.isEmpty()) null else key to azimuths
            }
            .sortedBy { it.first }
        if (normalized.isEmpty()) return null
        return buildString {
            append('{')
            normalized.forEachIndexed { index, (label, azimuths) ->
                if (index > 0) append(',')
                append(gson.toJson(label)).append(':')
                append(azimuths.joinToString(separator = ",", prefix = "[", postfix = "]"))
            }
            append('}')
        }
    }

    fun decode(json: String?): Map<String, Set<Int>> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            buildMap<String, Set<Int>> {
                root.entrySet().forEach { (rawKey, value) ->
                    val key = normalizeSystem(rawKey)
                    if (!value.isJsonArray || key.isBlank()) return@forEach
                    val azimuths = value.asJsonArray.mapNotNull { element ->
                        runCatching { normalizeAzimuth(element.asInt) }.getOrNull()
                    }.toSet()
                    if (azimuths.isNotEmpty()) put(key, this[key].orEmpty() + azimuths)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun normalizeSystem(value: String): String = value.trim().uppercase(Locale.ROOT)

    fun normalizeAzimuth(value: Int): Int? = when (value) {
        in 0..359 -> value
        360 -> 0
        else -> null
    }

    private fun parseAzimuth(token: String): Int? = token.trim().toIntOrNull()?.let(::normalizeAzimuth)
}
