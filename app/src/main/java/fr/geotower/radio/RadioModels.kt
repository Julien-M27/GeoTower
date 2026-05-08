package fr.geotower.radio

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
    ORANGE("Orange", "#FF7900", listOf("ORANGE", "ORANGE FRANCE")),
    SFR("SFR", "#E2001A", listOf("SFR", "SOCIETE FRANCAISE DU RADIOTELEPHONE")),
    BOUYGUES("Bouygues Telecom", "#00295F", listOf("BOUYGUES", "BOUYGUES TELECOM")),
    FREE("Free Mobile", "#757575", listOf("FREE", "FREE MOBILE"));

    companion object {
        fun fromLabel(raw: String?): MobileOperator? {
            val normalized = raw?.uppercase()?.trim().orEmpty()
            return entries.firstOrNull { operator ->
                operator.aliases.any { alias -> normalized.contains(alias) }
            }
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
