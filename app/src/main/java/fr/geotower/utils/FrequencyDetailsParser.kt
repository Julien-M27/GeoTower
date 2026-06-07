package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity

data class FreqBand(
    val rawFreq: String,
    val status: String,
    val date: String,
    val physDetails: List<String>,
    val gen: Int,
    val value: Int,
    val spectrumLines: List<String> = emptyList()
)

private data class FrequencyAccumulator(
    val band: FreqBand,
    val physDetails: MutableSet<String> = mutableSetOf(),
    val spectrumLines: MutableMap<String, String> = linkedMapOf()
)

private data class SpectrumLine(
    val key: String,
    val display: String
)

fun parseAndSortFrequencies(
    freqStr: String?,
    txtUnknown: String,
    txtAzimuthNotSpecified: String
): List<FreqBand> {
    if (freqStr.isNullOrBlank()) return emptyList()

    val parsedLines = freqStr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val tempMap = mutableMapOf<String, FrequencyAccumulator>()

    for (line in parsedLines) {
        val parts = line.split("|").map { it.trim() }
        val rawFrequencies = parts.getOrNull(0) ?: ""
        val status = parts.getOrNull(1) ?: txtUnknown
        val dateStr = parts.getOrNull(2) ?: ""
        val phys = parts.getOrNull(3) ?: ""

        val systemName = rawFrequencies.substringBefore(":").trim().uppercase()
        val isFh = isMicrowaveSystem(systemName)
        val generation = when {
            isFh -> 0
            systemName.contains("5G", true) || systemName.contains("NR", true) -> 5
            systemName.contains("4G", true) || systemName.contains("LTE", true) -> 4
            systemName.contains("3G", true) || systemName.contains("UMTS", true) -> 3
            systemName.contains("2G", true) || systemName.contains("GSM", true) -> 2
            else -> 0
        }
        val groupingKey = frequencyGroupingKey(systemName, rawFrequencies, status, dateStr, generation, isFh)
        val preciseFrequencies = rawFrequencies.substringAfter(":", "").trim()

        val accumulator = tempMap.getOrPut(groupingKey) {
            val freqValue = frequencySortValue(systemName, rawFrequencies, isFh)
            val band = FreqBand(rawFrequencies, status, dateStr, emptyList(), generation, freqValue)
            FrequencyAccumulator(band)
        }

        if (preciseFrequencies.isNotBlank() && preciseFrequencies != rawFrequencies.trim()) {
            val spectrumLines = extractSpectrumLines(preciseFrequencies)
            if (spectrumLines.isEmpty()) {
                accumulator.spectrumLines.putIfAbsent(
                    preciseFrequencies.normalizedSpectrumKey(),
                    preciseFrequencies
                )
            } else {
                spectrumLines.forEach { spectrumLine ->
                    accumulator.spectrumLines.putIfAbsent(spectrumLine.key, spectrumLine.display)
                }
            }
        }

        if (phys.isNotBlank() && phys != "Azimut non spécifié" && phys != txtAzimuthNotSpecified) {
            accumulator.physDetails.add(phys)
        }
    }

    return sortFrequencyBandsForDisplay(tempMap.values.map { accumulator ->
        accumulator.band.copy(
            physDetails = accumulator.physDetails.toList().sorted(),
            spectrumLines = accumulator.spectrumLines.values.toList().sortedWith(compareBy(
                { frequencySortValue("", it, isFh = true) },
                { it }
            ))
        )
    })
}

internal fun addMicrowaveFallbackBands(
    bands: List<FreqBand>,
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    rawFreqs: String?,
    txtUnknown: String
): List<FreqBand> {
    val fallbackPhysDetails = microwavePhysicalDetailsFromAzimuths(info.azimutsFh)
    val enrichedBands = bands.map { band ->
        if (band.isMicrowaveBand() && band.physDetails.isEmpty() && fallbackPhysDetails.isNotEmpty()) {
            band.copy(physDetails = fallbackPhysDetails)
        } else {
            band
        }
    }

    if (enrichedBands.any { it.isMicrowaveBand() } || !hasDeclaredMicrowave(info, technique, rawFreqs)) {
        return enrichedBands
    }

    if (fallbackPhysDetails.isEmpty()) {
        return enrichedBands
    }

    val fallbackStatus = technique?.statut?.takeIf { it.isNotBlank() }
        ?: info.statut?.takeIf { it.isNotBlank() }
        ?: txtUnknown
    val fallbackDate = listOfNotNull(
        technique?.dateService,
        technique?.dateImplantation,
        technique?.dateModif
    ).firstOrNull { it.isNotBlank() }.orEmpty()

    return sortFrequencyBandsForDisplay(enrichedBands + FreqBand(
        rawFreq = "FH",
        status = fallbackStatus,
        date = fallbackDate,
        physDetails = fallbackPhysDetails,
        gen = 0,
        value = 0
    ))
}

private fun sortFrequencyBandsForDisplay(bands: List<FreqBand>): List<FreqBand> {
    return bands.sortedWith(compareBy(
        { band -> AppConfig.siteTechnoOrder.value.indexOf(band.technologyOrderKey()) },
        { band ->
            val orderList = when (band.gen) {
                5 -> AppConfig.siteFreqOrder5G.value
                4 -> AppConfig.siteFreqOrder4G.value
                3 -> AppConfig.siteFreqOrder3G.value
                2 -> AppConfig.siteFreqOrder2G.value
                else -> emptyList()
            }
            val index = orderList.indexOf(band.value.toString())
            if (index == -1) 999 else index
        },
        { band -> if (band.gen == 0) band.value else 0 },
        { band -> band.rawFreq }
    ))
}

private fun FreqBand.technologyOrderKey(): String {
    return when (gen) {
        5 -> "5G"
        4 -> "4G"
        3 -> "3G"
        2 -> "2G"
        else -> "FH"
    }
}

private fun hasDeclaredMicrowave(
    info: LocalisationEntity,
    technique: TechniqueEntity?,
    rawFreqs: String?
): Boolean {
    return !info.azimutsFh.isNullOrBlank() ||
        info.filtres?.contains("FH", ignoreCase = true) == true ||
        technique?.technologies?.contains("FH", ignoreCase = true) == true ||
        rawFreqs?.let { isMicrowaveSystem(it) } == true
}

private fun microwavePhysicalDetailsFromAzimuths(rawAzimuths: String?): List<String> {
    return rawAzimuths
        ?.split(",")
        ?.mapNotNull { value ->
            value.substringBefore("\u00B0")
                .trim()
                .toIntOrNull()
                ?.let { if (it == 360) 0 else it }
        }
        ?.distinct()
        ?.sorted()
        ?.map { azimuth -> "FH : $azimuth\u00B0 (-)" }
        .orEmpty()
}

private fun isMicrowaveSystem(systemName: String): Boolean {
    return systemName.contains("FH", ignoreCase = true) ||
        systemName.contains("FAISCEAU", ignoreCase = true) ||
        systemName.contains("HERTZIEN", ignoreCase = true)
}

private fun FreqBand.isMicrowaveBand(): Boolean {
    return gen == 0 && isMicrowaveSystem(rawFreq.substringBefore(":").trim())
}

private fun frequencyGroupingKey(
    systemName: String,
    rawFrequencies: String,
    status: String,
    dateStr: String,
    gen: Int,
    isFh: Boolean
): String {
    if (!isFh && gen in 2..5) return systemName

    val preciseFrequencies = rawFrequencies.substringAfter(":", rawFrequencies).trim()
    return listOf(
        systemName,
        preciseFrequencies.uppercase(),
        status.trim().uppercase(),
        dateStr.trim()
    ).joinToString("|")
}

private fun frequencySortValue(systemName: String, rawFrequencies: String, isFh: Boolean): Int {
    val source = if (isFh) {
        rawFrequencies.substringAfter(":", rawFrequencies)
    } else {
        systemName
    }
    val values = Regex("\\d+").findAll(source).mapNotNull { it.value.toIntOrNull() }.toList()
    if (isFh) return values.firstOrNull() ?: 0

    val generation = when {
        systemName.contains("5G", true) || systemName.contains("NR", true) -> 5
        systemName.contains("4G", true) || systemName.contains("LTE", true) -> 4
        systemName.contains("3G", true) || systemName.contains("UMTS", true) -> 3
        systemName.contains("2G", true) || systemName.contains("GSM", true) -> 2
        else -> 0
    }
    val knownValues = mobileFrequencyValuesForGeneration(generation)
    val systemValue = values
        .filter { it in knownValues }
        .maxOrNull()
        ?: if (generation == 5 && values.contains(26)) 26000 else values.maxOrNull() ?: 0

    if (systemValue in knownValues || systemValue == 26000) return systemValue

    return mobileFrequencyValuesFromRanges(
        generation,
        rawFrequencies.substringAfter(":", "")
    ).maxOrNull() ?: systemValue
}

private fun mobileFrequencyValuesForGeneration(gen: Int): Set<Int> = when (gen) {
    5 -> setOf(700, 1400, 2100, 3500, 4200, 26000)
    4 -> setOf(700, 800, 900, 1800, 2100, 2600)
    3 -> setOf(900, 2100)
    2 -> setOf(900, 1800)
    else -> emptySet()
}

private fun mobileFrequencyValuesFromRanges(gen: Int, rawRanges: String): Set<Int> {
    return spectrumRangeRegex.findAll(rawRanges)
        .flatMap { match ->
            val start = normalizeFrequencyToMhz(match.groupValues[1], match.groupValues[3])
            val end = normalizeFrequencyToMhz(match.groupValues[2], match.groupValues[3])
            frequencyValuesForRange(gen, start, end).asSequence()
        }
        .toSet()
}

private fun normalizeFrequencyToMhz(value: String, unit: String): Double? {
    val number = value.replace(',', '.').toDoubleOrNull() ?: return null
    val normalizedUnit = unit.lowercase()
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
    val lowValue = minOf(start, end)
    val highValue = maxOf(start, end)
    return lowValue <= high && highValue >= low
}

private val spectrumRangeRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")

private fun extractSpectrumLines(raw: String): List<SpectrumLine> {
    return spectrumRangeRegex.findAll(raw).map { match ->
        val start = match.groupValues[1].trim()
        val end = match.groupValues[2].trim()
        val unit = match.groupValues[3].trim().ifBlank { "MHz" }
        SpectrumLine(
            key = "${start.normalizedSpectrumNumberKey()}-${end.normalizedSpectrumNumberKey()}-${unit.lowercase()}",
            display = "$start-$end $unit"
        )
    }.toList()
}

private fun String.normalizedSpectrumKey(): String {
    return trim().replace(Regex("""\s+"""), " ").uppercase()
}

private fun String.normalizedSpectrumNumberKey(): String {
    return replace(',', '.').toDoubleOrNull()?.toString() ?: normalizedSpectrumKey()
}
