package fr.geotower.utils

import java.math.BigDecimal

/**
 * Une plage déclarée par l'ANFR, séparée en deux informations distinctes :
 * le spectre occupé et la largeur de bande correspondante. Chacune est pilotée
 * par son propre interrupteur dans la personnalisation des pages.
 */
data class SpectrumRangeDisplay(
    val start: String,
    val end: String,
    val unit: String,
    val width: String
) {
    /** Le spectre, par exemple « 708-718 MHz ». */
    val spectrum: String = "$start-$end $unit".trim()

    /** La largeur de bande, par exemple « 10 MHz ». */
    val bandwidth: String = "$width $unit".trim()

    /** Le spectre au rendu aéré du tableau des antennes : « 708 - 718 MHz ». */
    val spacedSpectrum: String = "$start - $end $unit".trim()

    /**
     * « 10 MHz » insécable : dans les cellules étroites du tableau des antennes, la coupure
     * doit tomber entre la plage et sa largeur de bande, jamais au milieu des crochets.
     */
    val nonBreakingBandwidth: String = bandwidth.withNonBreakingSpaces()
}

private const val NBSP = '\u00A0'

/** Rend un fragment ins\u00E9cable, pour les cellules \u00E9troites du tableau des antennes. */
fun String.withNonBreakingSpaces(): String = replace(' ', NBSP)

data class SpectrumDisplayDetails(
    val ranges: List<SpectrumRangeDisplay>,
    val totalBandwidth: String?,
    val totalUnit: String,
    /** Texte brut, affiché tel quel quand aucune plage n'a pu être analysée. */
    val rawFrequencies: String
) {
    val hasTotal: Boolean = totalBandwidth != null

    /**
     * Lignes affichées sous la bande : le spectre (« 708-718 MHz »), la largeur de bande
     * (« 10 MHz »), ou les deux (« 708-718 MHz [10 MHz] »).
     */
    fun detailedFrequencies(
        withSpectrum: Boolean = true,
        withBandwidth: Boolean = true,
        spaced: Boolean = false
    ): String {
        if (!withSpectrum && !withBandwidth) return ""
        if (ranges.isEmpty()) return if (withSpectrum) rawFrequencies else ""
        return ranges.joinToString("\n") { range ->
            val spectrum = if (spaced) range.spacedSpectrum else range.spectrum
            val bandwidth = if (spaced) range.nonBreakingBandwidth else range.bandwidth
            when {
                withSpectrum && withBandwidth -> "$spectrum [$bandwidth]"
                withSpectrum -> spectrum
                else -> bandwidth
            }
        }
    }
}

private val spectrumDisplayRangeRegex =
    Regex("""([0-9]+(?:[.,][0-9]+)?)\s*-\s*([0-9]+(?:[.,][0-9]+)?)\s*([a-zA-Z]*Hz)?""")

fun formatSpectrumDisplayDetails(rawFrequencies: String): SpectrumDisplayDetails {
    var detectedUnit = "MHz"
    var totalBandwidth = BigDecimal.ZERO

    val ranges = spectrumDisplayRangeRegex.findAll(rawFrequencies).mapNotNull { match ->
        val startText = match.groupValues[1]
        val endText = match.groupValues[2]
        val start = startText.toSpectrumBigDecimal() ?: return@mapNotNull null
        val end = endText.toSpectrumBigDecimal() ?: return@mapNotNull null
        val unit = match.groupValues[3].takeIf { it.isNotBlank() } ?: "MHz"
        val width = end.subtract(start).abs()
        detectedUnit = unit
        totalBandwidth = totalBandwidth.add(width)
        SpectrumRangeDisplay(
            start = startText,
            end = endText,
            unit = unit,
            width = width.toSpectrumDisplayText()
        )
    }.toList()

    return SpectrumDisplayDetails(
        ranges = ranges,
        totalBandwidth = totalBandwidth.takeIf { it.signum() > 0 }?.toSpectrumDisplayText(),
        totalUnit = detectedUnit,
        rawFrequencies = rawFrequencies
    )
}

private fun String.toSpectrumBigDecimal(): BigDecimal? {
    return replace(',', '.').toBigDecimalOrNull()
}

private fun BigDecimal.toSpectrumDisplayText(): String {
    return stripTrailingZeros().toPlainString().replace('.', ',')
}
