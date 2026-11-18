package fr.geotower.radio

import fr.geotower.utils.OperatorColors

const val THROUGHPUT_CALCULATION_VERSION = "FR_RADIO_THROUGHPUT_V1"

enum class CountryScope {
    FR_METRO,
    OUTRE_MER
}

enum class MobileOperator(
    val label: String,
    val colorHex: String,
    val aliases: List<String>
) {
    ORANGE("Orange", OperatorColors.ORANGE_HEX, listOf("ORANGE", "ORANGE FRANCE", "ORANGE CARAIBE", "ORANGE CARAÏBE")),
    SFR("SFR", OperatorColors.SFR_HEX, listOf("SFR", "SOCIETE FRANCAISE DU RADIOTELEPHONE")),
    BOUYGUES("Bouygues Telecom", OperatorColors.BOUYGUES_HEX, listOf("BOUYGUES", "BOUYGUES TELECOM", "BYTEL")),
    FREE("Free Mobile", OperatorColors.FREE_HEX, listOf("FREE", "FREE MOBILE")),
    FREE_CARAIBE("Free Caraibe", OperatorColors.FREE_HEX, listOf("FREE CARAIBE", "FREE CARAIBES", "FREE CARAÏBE", "FREE CARAÏBES")),
    SRR("SRR", OperatorColors.SFR_HEX, listOf("SRR")),
    DIGICEL("Digicel", "#F4DA95", listOf("DIGICEL", "DIGICEL AFG")),
    OUTREMER_TELECOM("Outremer Telecom", "#F3A6C8", listOf("OUTREMER TELECOM", "OUTREMER TÉLÉCOM", "OUTREMER", "OMT", "SFR CARAIBE", "SFR CARAÏBE")),
    UTS_CARAIBE("UTS Caraibe", "#F09B49", listOf("UTS CARAIBE", "UTS CARAÏBE", "UTS CARAIBES", "UTS CARAÏBES")),
    DAUPHIN_TELECOM("Dauphin Telecom", "#51B2F8", listOf("DAUPHIN TELECOM", "DAUPHIN TÉLÉCOM", "DAUPHIN")),
    TELCO_OI("Telco OI", "#C45A9E", listOf("TELCO OI", "TELCO", "ONLY")),
    ZEOP("ZEOP Mobile", "#F9C846", listOf("ZEOP", "ZEOP MOBILE")),
    MAORE_MOBILE("Maore Mobile", "#2FBF5B", listOf("MAORE MOBILE", "MAORÉ MOBILE", "MAORE")),
    SPM_TELECOM("SPM Telecom", "#875CEE", listOf("SPM TELECOM", "SPM TÉLÉCOM", "SPM")),
    GLOBALTEL("Globaltel", "#59C28F", listOf("GLOBALTEL")),
    OPT_NC("OPT Nouvelle-Calédonie", "#005BAC", listOf("OPT NOUVELLE-CALEDONIE", "OPT NOUVELLE-CALÉDONIE", "OPT NOUVELLE CALEDONIE", "OPT NC", "GOUV NELLE CALEDONIE")),
    ONATI("ONATi (Vini)", "#00A3E0", listOf("ONATI", "ONATI VINI", "VINI")),
    PMT_VODAFONE("PMT/Vodafone", "#E60000", listOf("PMT/VODAFONE", "PMT", "VODAFONE")),
    VITI("Ora (Vini)", "#005B2F", listOf("ORA", "ORA VINI", "VITI", "VITI SAS")),
    SPT("SPT", "#00806A", listOf("SPT"));

    companion object {
        fun fromLabel(raw: String?): MobileOperator? {
            val normalized = raw?.uppercase()?.trim().orEmpty()
            if (normalized.isBlank()) return null
            return entries
                .asSequence()
                .flatMap { operator -> operator.aliases.asSequence().map { alias -> operator to alias } }
                .filter { (_, alias) -> normalized.contains(alias) }
                .maxByOrNull { (_, alias) -> alias.length }
                ?.first
        }
    }
}

enum class RadioTechnology {
    GSM_2G,
    UMTS_3G,
    LTE_4G,
    NR_5G
}

enum class DuplexMode {
    FDD,
    TDD,
    SDL
}

enum class SiteRadioStatus {
    AUTHORIZED,
    TECHNICALLY_OPERATIONAL,
    IN_SERVICE,
    COMMERCIAL_OPEN,
    UNKNOWN
}

enum class DataSource {
    ANFR,
    CARTORADIO,
    ARCEP,
    LOCAL,
    PROFILE_ASSUMPTION
}

enum class DssPolicy {
    DO_NOT_DOUBLE_COUNT,
    SPLIT_SPECTRUM,
    LTE_PRIORITY,
    NR_PRIORITY
}

enum class DeviceCategory {
    AVERAGE_PHONE,
    RECENT_PHONE,
    FLAGSHIP,
    CUSTOM
}

data class SpectrumAllocation(
    val operator: MobileOperator,
    val countryScope: CountryScope = CountryScope.FR_METRO,
    val bandLabel: String,
    val ratBandLte: String? = null,
    val ratBandNr: String? = null,
    val duplexMode: DuplexMode,
    val uplinkStartMHz: Double? = null,
    val uplinkEndMHz: Double? = null,
    val downlinkStartMHz: Double? = null,
    val downlinkEndMHz: Double? = null,
    val tddStartMHz: Double? = null,
    val tddEndMHz: Double? = null,
    val bandwidthMHz: Double,
    val validFrom: String,
    val validTo: String?,
    val sourceName: String,
    val sourceUrl: String,
    val confidence: Int
)

data class SiteRadioSystem(
    val sourceKey: String,
    val supportId: String,
    val operator: MobileOperator,
    val technology: RadioTechnology,
    val bandLabel: String,
    val frequencyRangeMHz: ClosedFloatingPointRange<Double>? = null,
    val status: SiteRadioStatus = SiteRadioStatus.UNKNOWN,
    val source: DataSource = DataSource.ANFR,
    val azimuthDeg: Double? = null,
    val antennaHeightM: Double? = null,
    val supportHeightM: Double? = null,
    val tiltDeg: Double? = null,
    val sectorId: String? = null,
    val lastSeenAt: String? = null
)

data class RatAssumptions(
    val dlModulationOrder: Int,
    val ulModulationOrder: Int,
    val dlMimoLayers: Int,
    val ulMimoLayers: Int,
    val maxCaComponents: Int,
    val scsKHz: Int = 30,
    val tddDlRatio: Double = 1.0,
    val tddUlRatio: Double = 1.0,
    val overheadDl: Double = 0.0,
    val overheadUl: Double = 0.0
)

data class ThroughputProfile(
    val id: String,
    val label: String,
    val description: String,
    val lte: RatAssumptions,
    val nr: RatAssumptions,
    val dssPolicy: DssPolicy = DssPolicy.DO_NOT_DOUBLE_COUNT,
    val defaultDeviceCategory: DeviceCategory = DeviceCategory.RECENT_PHONE,
    val endcAllowed: Boolean = true
)

data class CarrierThroughputResult(
    val sourceKey: String,
    val operator: MobileOperator,
    val technology: RadioTechnology,
    val bandLabel: String,
    val ratBand: String?,
    val bandwidthMHz: Double,
    val duplexMode: DuplexMode,
    val dlMbps: Double,
    val ulMbps: Double,
    val dlModulationOrder: Int,
    val ulModulationOrder: Int,
    val dlMimoLayers: Int,
    val ulMimoLayers: Int,
    val included: Boolean,
    val excludedReason: String? = null,
    val sourceName: String,
    val sourceUrl: String,
    val assumptions: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class ExcludedCarrier(
    val sourceKey: String,
    val technology: RadioTechnology,
    val bandLabel: String,
    val reason: String
)

data class ThroughputEngineResult(
    val totalDlMbps: Double,
    val totalUlMbps: Double,
    val perCarrierResults: List<CarrierThroughputResult>,
    val excludedCarriers: List<ExcludedCarrier>,
    val warnings: List<String>,
    val assumptions: List<String>,
    val confidenceScore: Int,
    val calculationVersion: String = THROUGHPUT_CALCULATION_VERSION,
    val sourceSummary: String,
    val generatedAtEpochMs: Long = System.currentTimeMillis()
)
