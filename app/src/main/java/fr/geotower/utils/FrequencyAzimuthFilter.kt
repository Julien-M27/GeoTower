package fr.geotower.utils

import java.util.Locale
import kotlin.math.roundToInt

private data class RadioBandKey(
    val gen: Int,
    val value: Int
)

fun filteredAzimuthsForFrequencySelection(
    detailsFrequences: String?,
    filter: FrequencyFilterSelection
): String? {
    if (detailsFrequences.isNullOrBlank() || filter.isFullyEnabled) return null

    val selectedAzimuths = linkedSetOf<Int>()
    var hasSelectedFrequencyLine = false
    detailsFrequences.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val parts = line.split("|").map { it.trim() }
            val rawFrequency = parts.getOrNull(0).orEmpty()
            val physDetails = parts.getOrNull(3).orEmpty()
            if (rawFrequency.isBlank() || physDetails.isBlank()) return@forEach

            val matchesSelectedBand = radioBandKeysFromRawDetails(rawFrequency)
                .any { key -> filter.matchesBand(key.gen, key.value) }
            if (!matchesSelectedBand) return@forEach

            hasSelectedFrequencyLine = true
            selectedAzimuths.addAll(extractMobileAzimuths(physDetails))
        }

    return selectedAzimuths
        .takeIf { it.isNotEmpty() }
        ?.sorted()
        ?.joinToString(",")
        ?: if (hasSelectedFrequencyLine) "" else null
}

private fun radioBandKeysFromRawDetails(rawFrequency: String): Set<RadioBandKey> {
    val systemName = rawFrequency.substringBefore(":").trim().uppercase(Locale.ROOT)
    val gen = when {
        systemName.contains("5G") || systemName.contains("NR") -> 5
        systemName.contains("4G") || systemName.contains("LTE") -> 4
        systemName.contains("3G") || systemName.contains("UMTS") -> 3
        systemName.contains("2G") || systemName.contains("GSM") -> 2
        else -> 0
    }
    if (gen !in 2..5) return emptySet()

    val knownValues = mobileFrequencyValuesForGeneration(gen)
    val systemNumbers = numberRegex.findAll(systemName)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
    val systemValue = systemNumbers
        .filter { it in knownValues }
        .maxOrNull()
        ?: if (gen == 5 && systemNumbers.contains(26)) 26000 else null

    if (systemValue != null) return setOf(RadioBandKey(gen, systemValue))

    return mobileFrequencyValuesFromRanges(gen, rawFrequency.substringAfter(":", ""))
        .map { value -> RadioBandKey(gen, value) }
        .toSet()
}

private fun extractMobileAzimuths(physDetails: String): Set<Int> {
    val explicitMatches = azimuthWithUnitRegex.findAll(physDetails)
        .mapNotNull { match -> normalizeAzimuth(match.groupValues.getOrNull(1)) }
        .toSet()
    if (explicitMatches.isNotEmpty()) return explicitMatches

    val valueNearTypeSeparator = physDetails
        .substringAfter(":", "")
        .substringBefore("(")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return emptySet()

    return numberRegex.findAll(valueNearTypeSeparator)
        .mapNotNull { match -> normalizeAzimuth(match.value) }
        .toSet()
}

private fun normalizeAzimuth(rawValue: String?): Int? {
    val value = rawValue
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.roundToInt()
        ?: return null
    if (value !in 0..360) return null
    return if (value == 360) 0 else value
}

private fun mobileFrequencyValuesForGeneration(gen: Int): Set<Int> = when (gen) {
    5 -> setOf(700, 1400, 2100, 3500, 4200, 26000)
    4 -> setOf(700, 800, 900, 1800, 2100, 2600)
    3 -> setOf(900, 2100)
    2 -> setOf(900, 1800)
    else -> emptySet()
}

private fun mobileFrequencyValuesFromRanges(gen: Int, rawRanges: String): Set<Int> {
    return frequencyRangeRegex.findAll(rawRanges)
        .flatMap { match ->
            val start = normalizeFrequencyToMhz(match.groupValues[1], match.groupValues[3])
            val end = normalizeFrequencyToMhz(match.groupValues[2], match.groupValues[3])
            frequencyValuesForRange(gen, start, end).asSequence()
        }
        .toSet()
}

private fun normalizeFrequencyToMhz(value: String, unit: String): Double? {
    val number = value.replace(',', '.').toDoubleOrNull() ?: return null
    val normalizedUnit = unit.lowercase(Locale.ROOT)
    return when {
        normalizedUnit.contains("ghz") -> number * 1000.0
        normalizedUnit.contains("khz") -> number / 1000.0
        normalizedUnit.contains("hz") && !normalizedUnit.contains("mhz") -> number / 1_000_000.0
        else -> number
    }
}

private fun frequencyValuesForRange(gen: Int, start: Double?, end: Double?): Set<Int> {
    if (start == null || end == null) return emptySet()
    return when (gen) {
        5 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 700.0, 790.0)) add(700)
            if (frequencyRangeOverlaps(start, end, 1427.0, 1518.0)) add(1400)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
            if (frequencyRangeOverlaps(start, end, 3300.0, 3800.0)) add(3500)
            if (frequencyRangeOverlaps(start, end, 3800.1, 4200.0)) add(4200)
            if (frequencyRangeOverlaps(start, end, 24000.0, 27500.0)) add(26000)
        }
        4 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 700.0, 790.0)) add(700)
            if (frequencyRangeOverlaps(start, end, 791.0, 862.0)) add(800)
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1710.0, 1880.0)) add(1800)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
            if (frequencyRangeOverlaps(start, end, 2500.0, 2690.0)) add(2600)
        }
        3 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1920.0, 2170.0)) add(2100)
        }
        2 -> buildSet {
            if (frequencyRangeOverlaps(start, end, 880.0, 960.0)) add(900)
            if (frequencyRangeOverlaps(start, end, 1710.0, 1880.0)) add(1800)
        }
        else -> emptySet()
    }
}

private fun frequencyRangeOverlaps(start: Double, end: Double, low: Double, high: Double): Boolean {
    val min = minOf(start, end)
    val max = maxOf(start, end)
    return min <= high && max >= low
}

private val numberRegex = Regex("""[0-9]+(?:[.,][0-9]+)?""")
private val azimuthWithUnitRegex = Regex(
    "([0-9]{1,3}(?:[.,][0-9]+)?)\\s*(?:\\u00B0|\\u00C2\\u00B0|deg(?:res|ree|rees)?|degrees?)",
    RegexOption.IGNORE_CASE
)
private val frequencyRangeRegex =
    Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""", RegexOption.IGNORE_CASE)
