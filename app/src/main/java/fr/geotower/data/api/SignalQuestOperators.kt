package fr.geotower.data.api

import fr.geotower.utils.OperatorColors

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

    fun supports(rawOperator: String?): Boolean {
        return operatorParamFor(rawOperator) != null
    }

    fun operatorParamFor(rawOperator: String?): String? {
        return OperatorColors.keysFor(rawOperator)
            .firstNotNullOfOrNull { key -> operatorParamByKey[key] }
    }
}
