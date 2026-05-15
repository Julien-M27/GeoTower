package fr.geotower.utils

import android.graphics.Color as AndroidColor

data class OperatorColorSpec(
    val key: String,
    val label: String,
    val colorHex: String,
    val colorArgb: Long,
    val aliases: List<String>,
    val region: OperatorRegion = OperatorRegion.METRO
)

enum class OperatorRegion {
    METRO,
    OVERSEAS
}

object OperatorColors {
    const val ORANGE_KEY = "ORANGE"
    const val BOUYGUES_KEY = "BOUYGUES"
    const val SFR_KEY = "SFR"
    const val FREE_KEY = "FREE"
    const val DIGICEL_KEY = "DIGICEL"
    const val FREE_CARAIBE_KEY = "FREE_CARAIBE"
    const val OUTREMER_TELECOM_KEY = "OUTREMER_TELECOM"
    const val UTS_CARAIBE_KEY = "UTS_CARAIBE"
    const val DAUPHIN_TELECOM_KEY = "DAUPHIN_TELECOM"
    const val SRR_KEY = "SRR"
    const val TELCO_OI_KEY = "TELCO_OI"
    const val ZEOP_KEY = "ZEOP"
    const val MAORE_MOBILE_KEY = "MAORE_MOBILE"
    const val SPM_TELECOM_KEY = "SPM_TELECOM"
    const val GLOBALTEL_KEY = "GLOBALTEL"
    const val OPT_NC_KEY = "OPT_NC"
    const val ONATI_KEY = "ONATI"
    const val PMT_VODAFONE_KEY = "PMT_VODAFONE"
    const val VITI_KEY = "VITI"
    const val SPT_KEY = "SPT"

    const val ORANGE_HEX = "#FF7900"
    const val BOUYGUES_HEX = "#009FE3"
    const val SFR_HEX = "#E2001A"
    const val FREE_HEX = "#757575"
    const val UNKNOWN_HEX = "#808080"

    const val ORANGE_ARGB: Long = 0xFFFF7900
    const val BOUYGUES_ARGB: Long = 0xFF009FE3
    const val SFR_ARGB: Long = 0xFFE2001A
    const val FREE_ARGB: Long = 0xFF757575
    const val UNKNOWN_ARGB: Long = 0xFF808080

    val all: List<OperatorColorSpec> = listOf(
        OperatorColorSpec(ORANGE_KEY, "Orange", ORANGE_HEX, ORANGE_ARGB, listOf("ORANGE", "ORANGE FRANCE")),
        OperatorColorSpec(BOUYGUES_KEY, "Bouygues Telecom", BOUYGUES_HEX, BOUYGUES_ARGB, listOf("BOUYGUES", "BOUYGUES TELECOM")),
        OperatorColorSpec(SFR_KEY, "SFR", SFR_HEX, SFR_ARGB, listOf("SFR", "SOCIETE FRANCAISE DU RADIOTELEPHONE")),
        OperatorColorSpec(FREE_KEY, "Free Mobile", FREE_HEX, FREE_ARGB, listOf("FREE", "FREE MOBILE")),
        OperatorColorSpec(DIGICEL_KEY, "Digicel", "#F4DA95", 0xFFF4DA95, listOf("DIGICEL", "DIGICEL AFG"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(FREE_CARAIBE_KEY, "Free Caraibe", FREE_HEX, FREE_ARGB, listOf("FREE CARAIBE", "FREE CARAIBES", "FREE CARAÏBE", "FREE CARAÏBES"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(OUTREMER_TELECOM_KEY, "Outremer Telecom", "#F3A6C8", 0xFFF3A6C8, listOf("OUTREMER TELECOM", "OUTREMER TÉLÉCOM", "OUTREMER", "OMT", "SFR CARAIBE", "SFR CARAÏBE"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(UTS_CARAIBE_KEY, "UTS Caraibe", "#F09B49", 0xFFF09B49, listOf("UTS CARAIBE", "UTS CARAÏBE", "UTS CARAIBES", "UTS CARAÏBES"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(DAUPHIN_TELECOM_KEY, "Dauphin Telecom", "#51B2F8", 0xFF51B2F8, listOf("DAUPHIN TELECOM", "DAUPHIN TÉLÉCOM", "DAUPHIN"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(SRR_KEY, "SRR", SFR_HEX, SFR_ARGB, listOf("SRR"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(TELCO_OI_KEY, "Telco OI", "#C45A9E", 0xFFC45A9E, listOf("TELCO OI", "TELCO", "ONLY"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(ZEOP_KEY, "ZEOP Mobile", "#F9C846", 0xFFF9C846, listOf("ZEOP", "ZEOP MOBILE"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(MAORE_MOBILE_KEY, "Maore Mobile", "#2FBF5B", 0xFF2FBF5B, listOf("MAORE MOBILE", "MAORÉ MOBILE", "MAORE"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(SPM_TELECOM_KEY, "SPM Telecom", "#875CEE", 0xFF875CEE, listOf("SPM TELECOM", "SPM TÉLÉCOM", "SPM"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(GLOBALTEL_KEY, "Globaltel", "#59C28F", 0xFF59C28F, listOf("GLOBALTEL"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(OPT_NC_KEY, "OPT Nouvelle-Caledonie", "#005BAC", 0xFF005BAC, listOf("OPT NOUVELLE-CALEDONIE", "OPT NOUVELLE-CALÉDONIE", "OPT NOUVELLE CALEDONIE", "OPT NOUVELLE CALÉDONIE", "OPT NC"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(ONATI_KEY, "ONATi (Vini)", "#00A3E0", 0xFF00A3E0, listOf("ONATI", "ONATI VINI", "VINI"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(PMT_VODAFONE_KEY, "PMT/Vodafone", "#E60000", 0xFFE60000, listOf("PMT/VODAFONE", "PMT", "VODAFONE"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(VITI_KEY, "Ora (Vini)", "#005B2F", 0xFF005B2F, listOf("ORA", "ORA VINI", "VITI", "VITI SAS"), OperatorRegion.OVERSEAS),
        OperatorColorSpec(SPT_KEY, "SPT", "#00806A", 0xFF00806A, listOf("SPT"), OperatorRegion.OVERSEAS)
    )

    val orderedKeys: List<String> = all.map { it.key }
    val metro: List<OperatorColorSpec> = all.filter { it.region == OperatorRegion.METRO }
    val overseas: List<OperatorColorSpec> = all.filter { it.region == OperatorRegion.OVERSEAS }
    val defaultVisibleKeys: Set<String> = orderedKeys.toSet()

    private val specsByKey: Map<String, OperatorColorSpec> = all.associateBy { it.key }

    fun keyFor(raw: String?): String? {
        val normalized = raw?.uppercase()?.trim().orEmpty()
        if (normalized.isBlank()) return null
        if (normalized in specsByKey) return normalized
        return all
            .asSequence()
            .flatMap { spec -> spec.aliases.asSequence().map { alias -> spec to alias.uppercase() } }
            .filter { (_, alias) -> normalized.contains(alias) }
            .maxByOrNull { (_, alias) -> alias.length }
            ?.first
            ?.key
    }

    fun keysFor(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()
        return value
            .split(Regex("\\s+-\\s+|[,;/\\u2022\\r\\n]+"))
            .mapNotNull { keyFor(it) }
            .distinct()
            .ifEmpty { keyFor(value)?.let(::listOf) ?: emptyList() }
    }

    fun specForKey(key: String?): OperatorColorSpec? {
        return specsByKey[key?.uppercase()?.trim()]
    }

    fun hasKey(key: String): Boolean {
        return key.uppercase().trim() in specsByKey
    }

    fun colorHex(raw: String?, fallback: String = UNKNOWN_HEX): String {
        return keyFor(raw)?.let { specsByKey[it]?.colorHex } ?: fallback
    }

    fun colorInt(raw: String?, fallback: Int = AndroidColor.GRAY): Int {
        return keyFor(raw)?.let { colorIntForKey(it) } ?: fallback
    }

    fun colorArgbForKey(key: String, fallback: Long = UNKNOWN_ARGB): Long {
        return specsByKey[key]?.colorArgb ?: fallback
    }

    fun colorIntForKey(key: String, fallback: Int = AndroidColor.GRAY): Int {
        return specsByKey[key]?.colorHex?.let(AndroidColor::parseColor) ?: fallback
    }

    fun androidColorMap(): Map<String, Int> = specsByKey.mapValues { (_, spec) ->
        AndroidColor.parseColor(spec.colorHex)
    }
}
