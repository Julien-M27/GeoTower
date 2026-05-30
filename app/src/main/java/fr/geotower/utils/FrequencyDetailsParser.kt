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
    return if (isFh) values.firstOrNull() ?: 0 else values.maxOrNull() ?: 0
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
