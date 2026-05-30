package fr.geotower.utils

import java.math.BigDecimal

data class SpectrumDisplayDetails(
    val detailedFrequencies: String,
    val totalBandwidth: String?,
    val totalUnit: String
) {
    val hasTotal: Boolean = totalBandwidth != null
}

private data class SpectrumRangeDisplay(
    val line: String
)

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
            line = "$startText-$endText $unit [${width.toSpectrumDisplayText()} $unit]".trim()
        )
    }.toList()

    return SpectrumDisplayDetails(
        detailedFrequencies = ranges.joinToString("\n") { it.line }.ifBlank { rawFrequencies },
        totalBandwidth = totalBandwidth.takeIf { it.signum() > 0 }?.toSpectrumDisplayText(),
        totalUnit = detectedUnit
    )
}

private fun String.toSpectrumBigDecimal(): BigDecimal? {
    return replace(',', '.').toBigDecimalOrNull()
}

private fun BigDecimal.toSpectrumDisplayText(): String {
    return stripTrailingZeros().toPlainString().replace('.', ',')
}
