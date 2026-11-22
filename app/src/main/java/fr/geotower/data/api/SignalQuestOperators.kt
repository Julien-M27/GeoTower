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
        OperatorColors.OUTREMER_TELECOM_KEY to "SFR",
        OperatorColors.ROGERS_KEY to "ROGERS",
        OperatorColors.BELL_KEY to "BELL",
        OperatorColors.TELUS_KEY to "TELUS",
        OperatorColors.VIDEOTRON_KEY to "VIDEOTRON",
        OperatorColors.FREEDOM_MOBILE_KEY to "FREEDOM",
        OperatorColors.SASKTEL_KEY to "SASKTEL",
        OperatorColors.XPLORE_KEY to "XPLORE",
        OperatorColors.EASTLINK_KEY to "EASTLINK",
        OperatorColors.TBAYTEL_KEY to "TBAYTEL",
        OperatorColors.SSI_MICRO_KEY to "SSIMICRO",
        OperatorColors.ECOTEL_KEY to "ECOTEL",
        OperatorColors.COGECO_KEY to "COGECO",
        OperatorColors.SOGETEL_KEY to "SOGETEL",
        OperatorColors.ICE_WIRELESS_KEY to "ICEWIRELESS"
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

    fun operatorParamForKey(operatorKey: String?): String? {
        val normalizedKey = OperatorColors.specForKey(operatorKey)?.key ?: return null
        return operatorParamByKey[normalizedKey]
    }

    fun speedtestPlmnFor(rawOperator: String?): SignalQuestPlmnFilter? {
        return OperatorColors.keysFor(rawOperator)
            .firstNotNullOfOrNull { key -> speedtestPlmnByKey[key] }
    }

    /**
     * Vrai si l'API speedtest peut être interrogée pour cet opérateur : soit son PLMN est connu,
     * soit il reste identifiable par le paramètre `operator`.
     */
    fun supportsSpeedtests(rawOperator: String?): Boolean {
        return speedtestPlmnFor(rawOperator) != null || operatorParamFor(rawOperator) != null
    }
}
