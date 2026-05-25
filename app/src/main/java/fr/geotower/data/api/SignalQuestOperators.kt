package fr.geotower.data.api

import fr.geotower.utils.OperatorColors

data class SignalQuestPlmnFilter(val mcc: Int? = null, val mnc: Int)

object SignalQuestOperators {
    private val operatorParamByKey = mapOf(
        OperatorColors.ORANGE_KEY to "ORANGE",
        OperatorColors.SFR_KEY to "SFR",
        OperatorColors.BOUYGUES_KEY to "BOUYGUES",
        OperatorColors.FREE_KEY to "FREE",
        OperatorColors.FREE_CARAIBE_KEY to "FREE",
        OperatorColors.TELCO_OI_KEY to "FREE",
        OperatorColors.SRR_KEY to "SFR",
        OperatorColors.OUTREMER_TELECOM_KEY to "SFR"
    )

    private val speedtestPlmnByKey = mapOf(
        OperatorColors.ORANGE_KEY to SignalQuestPlmnFilter(mcc = 208, mnc = 1),
        OperatorColors.SFR_KEY to SignalQuestPlmnFilter(mcc = 208, mnc = 10),
        OperatorColors.BOUYGUES_KEY to SignalQuestPlmnFilter(mcc = 208, mnc = 20),
        OperatorColors.FREE_KEY to SignalQuestPlmnFilter(mcc = 208, mnc = 15)
    )

    fun supports(rawOperator: String?): Boolean {
        return operatorParamFor(rawOperator) != null
    }

    fun operatorParamFor(rawOperator: String?): String? {
        return OperatorColors.keysFor(rawOperator)
            .firstNotNullOfOrNull { key -> operatorParamByKey[key] }
    }

    fun speedtestPlmnFor(rawOperator: String?): SignalQuestPlmnFilter? {
        return OperatorColors.keysFor(rawOperator)
            .firstNotNullOfOrNull { key -> speedtestPlmnByKey[key] }
    }
}
