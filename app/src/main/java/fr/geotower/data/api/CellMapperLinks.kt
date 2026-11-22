package fr.geotower.data.api

import fr.geotower.utils.OperatorColors
import java.util.Locale

data class CellMapperNetwork(
    val mcc: Int,
    val mnc: Int
)

object CellMapperLinks {
    private val networkByOperatorKey = mapOf(
        OperatorColors.ORANGE_KEY to CellMapperNetwork(mcc = 208, mnc = 1),
        OperatorColors.SFR_KEY to CellMapperNetwork(mcc = 208, mnc = 10),
        OperatorColors.BOUYGUES_KEY to CellMapperNetwork(mcc = 208, mnc = 20),
        OperatorColors.FREE_KEY to CellMapperNetwork(mcc = 208, mnc = 15),
        OperatorColors.ROGERS_KEY to CellMapperNetwork(mcc = 302, mnc = 720),
        OperatorColors.BELL_KEY to CellMapperNetwork(mcc = 302, mnc = 610),
        OperatorColors.TELUS_KEY to CellMapperNetwork(mcc = 302, mnc = 220),
        OperatorColors.VIDEOTRON_KEY to CellMapperNetwork(mcc = 302, mnc = 500),
        OperatorColors.FREEDOM_MOBILE_KEY to CellMapperNetwork(mcc = 302, mnc = 490),
        OperatorColors.SASKTEL_KEY to CellMapperNetwork(mcc = 302, mnc = 780),
        OperatorColors.EASTLINK_KEY to CellMapperNetwork(mcc = 302, mnc = 270),
        OperatorColors.TBAYTEL_KEY to CellMapperNetwork(mcc = 302, mnc = 370),
        OperatorColors.ICE_WIRELESS_KEY to CellMapperNetwork(mcc = 302, mnc = 620),
        OperatorColors.SOGETEL_KEY to CellMapperNetwork(mcc = 302, mnc = 940),
        OperatorColors.XPLORE_KEY to CellMapperNetwork(mcc = 302, mnc = 770)
    )

    fun networkFor(rawOperator: String?): CellMapperNetwork? {
        return OperatorColors.keysFor(rawOperator)
            .firstNotNullOfOrNull { key -> networkByOperatorKey[key] }
    }

    fun supports(rawOperator: String?): Boolean = networkFor(rawOperator) != null

    fun preferredMapType(rawTechnologies: String?): String {
        val value = rawTechnologies.orEmpty().uppercase(Locale.ROOT)
        return when {
            value.contains("4G") || value.contains("LTE") -> "LTE"
            value.contains("5G") || value.contains("NR") -> "NR"
            value.contains("3G") || value.contains("UMTS") -> "UMTS"
            value.contains("2G") || value.contains("GSM") -> "GSM"
            else -> "LTE"
        }
    }
}
