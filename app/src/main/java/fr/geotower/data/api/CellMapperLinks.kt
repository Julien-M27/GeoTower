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
        OperatorColors.FREE_KEY to CellMapperNetwork(mcc = 208, mnc = 15)
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
