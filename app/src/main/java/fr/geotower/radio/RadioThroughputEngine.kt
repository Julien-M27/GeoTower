package fr.geotower.radio

import kotlin.math.pow
import kotlin.math.abs

object RadioThroughputEngine {
    private const val NR_RMAX = 948.0 / 1024.0
    private const val LTE_APPROX_BASE_DL_20MHZ_2X2_64QAM_MBPS = 150.0
    private const val LTE_APPROX_BASE_UL_20MHZ_1X1_64QAM_MBPS = 75.0

    val nrPrbTableScs30Khz: Map<Int, Int> = mapOf(
        5 to 11,
        10 to 24,
        15 to 38,
        20 to 51,
        25 to 65,
        30 to 78,
        40 to 106,
        50 to 133,
        60 to 162,
        70 to 189,
        80 to 217,
        90 to 245,
        100 to 273
    )

    fun estimate(
        systems: List<SiteRadioSystem>,
        profile: ThroughputProfile,
        allocations: List<SpectrumAllocation> = SpectrumAllocationsFrMetro.allocations
    ): ThroughputEngineResult {
        val warnings = mutableListOf(
            "La charge reseau, le backhaul et les capacites exactes du terminal ne sont pas connus.",
            "MIMO et modulation ne sont pas publies au niveau du site : profil ${profile.label} applique."
        )
        val assumptions = mutableListOf(profile.description)
        val excluded = mutableListOf<ExcludedCarrier>()

        val modernSystems = systems
            .filter { it.technology == RadioTechnology.LTE_4G || it.technology == RadioTechnology.NR_5G }
            .distinctBy { "${it.operator}:${it.technology}:${it.bandLabel}:${it.sectorId ?: "unknown-sector"}" }

        val initialResults = modernSystems.mapNotNull { system ->
            val allocation = allocations.firstOrNull {
                it.operator == system.operator &&
                    it.bandLabel == SpectrumAllocationsFrMetro.normalizeBandLabel(system.bandLabel) &&
                    when (system.technology) {
                        RadioTechnology.LTE_4G -> it.ratBandLte != null
                        RadioTechnology.NR_5G -> it.ratBandNr != null
                        else -> false
                    }
            }

            if (allocation == null) {
                excluded += ExcludedCarrier(
                    sourceKey = system.sourceKey,
                    technology = system.technology,
                    bandLabel = system.bandLabel,
                    reason = "Aucune allocation Arcep FR metropole compatible avec cette techno/bande."
                )
                warnings += "Bande ${system.bandLabel} ${system.technology.labelForUi()} exclue : allocation operateur non trouvee."
                return@mapNotNull null
            }

            calculateCarrier(system, allocation, profile)
        }

        val dssFiltered = applyDssPolicy(initialResults, profile, excluded, warnings)
        val includedResults = dssFiltered.filter { it.included }

        var confidence = 92
        if (systems.any { it.azimuthDeg == null }) confidence -= 6
        if (warnings.any { it.contains("exclue", ignoreCase = true) }) confidence -= 8
        if (systems.any { it.status != SiteRadioStatus.COMMERCIAL_OPEN && it.status != SiteRadioStatus.IN_SERVICE }) confidence -= 6
        confidence = confidence.coerceIn(35, 95)

        return ThroughputEngineResult(
            totalDlMbps = includedResults.sumOf { it.dlMbps },
            totalUlMbps = includedResults.sumOf { it.ulMbps },
            perCarrierResults = dssFiltered,
            excludedCarriers = excluded,
            warnings = warnings.distinct(),
            assumptions = assumptions.distinct(),
            confidenceScore = confidence,
            sourceSummary = "ANFR/data.gouv pour les frequences declarees, Arcep pour les allocations operateur, ETSI/3GPP TS 38.306 et TS 36.306/36.213 pour le modele radio."
        )
    }

    fun calculateCarrier(
        system: SiteRadioSystem,
        allocation: SpectrumAllocation,
        profile: ThroughputProfile
    ): CarrierThroughputResult {
        val ratBand = mapAnfrBandToRatBand(system.operator, system.technology, system.bandLabel)
        val dlMbps: Double
        val ulMbps: Double
        val assumptions = mutableListOf<String>()

        if (system.technology == RadioTechnology.NR_5G) {
            val nPrb = nrPrbFor(allocation.bandwidthMHz, profile.nr.scsKHz)
            dlMbps = calculateNrRateMbps(
                bandwidthMHz = allocation.bandwidthMHz,
                scsKHz = profile.nr.scsKHz,
                nPrb = nPrb,
                layers = profile.nr.dlMimoLayers,
                modulationOrder = profile.nr.dlModulationOrder,
                overhead = profile.nr.overheadDl,
                tddRatio = if (allocation.duplexMode == DuplexMode.TDD) profile.nr.tddDlRatio else 1.0
            )
            ulMbps = calculateNrRateMbps(
                bandwidthMHz = allocation.bandwidthMHz,
                scsKHz = profile.nr.scsKHz,
                nPrb = nPrb,
                layers = profile.nr.ulMimoLayers,
                modulationOrder = profile.nr.ulModulationOrder,
                overhead = profile.nr.overheadUl,
                tddRatio = if (allocation.duplexMode == DuplexMode.TDD) profile.nr.tddUlRatio else 1.0
            )
            assumptions += "NR_TS_38_306_PRB_SCS_${profile.nr.scsKHz}_KHZ"
            if (allocation.duplexMode == DuplexMode.TDD) {
                assumptions += "TDD DL ${profile.nr.tddDlRatio} / UL ${profile.nr.tddUlRatio}"
            }
        } else {
            dlMbps = calculateLteApproxMbps(
                bandwidthMHz = allocation.bandwidthMHz,
                modulationOrder = profile.lte.dlModulationOrder,
                layers = profile.lte.dlMimoLayers,
                downlink = true
            )
            ulMbps = calculateLteApproxMbps(
                bandwidthMHz = allocation.bandwidthMHz,
                modulationOrder = profile.lte.ulModulationOrder,
                layers = profile.lte.ulMimoLayers,
                downlink = false
            )
            assumptions += "LTE_APPROX_V1"
        }

        return CarrierThroughputResult(
            sourceKey = system.sourceKey,
            operator = system.operator,
            technology = system.technology,
            bandLabel = SpectrumAllocationsFrMetro.normalizeBandLabel(system.bandLabel),
            ratBand = ratBand,
            bandwidthMHz = allocation.bandwidthMHz,
            duplexMode = allocation.duplexMode,
            dlMbps = dlMbps,
            ulMbps = ulMbps,
            dlModulationOrder = if (system.technology == RadioTechnology.NR_5G) profile.nr.dlModulationOrder else profile.lte.dlModulationOrder,
            ulModulationOrder = if (system.technology == RadioTechnology.NR_5G) profile.nr.ulModulationOrder else profile.lte.ulModulationOrder,
            dlMimoLayers = if (system.technology == RadioTechnology.NR_5G) profile.nr.dlMimoLayers else profile.lte.dlMimoLayers,
            ulMimoLayers = if (system.technology == RadioTechnology.NR_5G) profile.nr.ulMimoLayers else profile.lte.ulMimoLayers,
            included = true,
            sourceName = allocation.sourceName,
            sourceUrl = allocation.sourceUrl,
            assumptions = assumptions
        )
    }

    fun calculateNrRateMbps(
        bandwidthMHz: Double,
        scsKHz: Int,
        nPrb: Int = nrPrbFor(bandwidthMHz, scsKHz),
        layers: Int,
        modulationOrder: Int,
        overhead: Double,
        tddRatio: Double = 1.0,
        scalingFactor: Double = 1.0
    ): Double {
        val mu = when (scsKHz) {
            15 -> 0
            30 -> 1
            60 -> 2
            120 -> 3
            else -> 1
        }
        val tsMu = 1e-3 / (14.0 * 2.0.pow(mu.toDouble()))
        return 1e-6 *
            layers *
            modulationOrder *
            scalingFactor *
            NR_RMAX *
            ((nPrb * 12.0) / tsMu) *
            (1.0 - overhead) *
            tddRatio
    }

    fun calculateLteApproxMbps(
        bandwidthMHz: Double,
        modulationOrder: Int,
        layers: Int,
        downlink: Boolean
    ): Double {
        val base = if (downlink) LTE_APPROX_BASE_DL_20MHZ_2X2_64QAM_MBPS else LTE_APPROX_BASE_UL_20MHZ_1X1_64QAM_MBPS
        val baseLayers = if (downlink) 2.0 else 1.0
        return base *
            (bandwidthMHz / 20.0) *
            (modulationOrder / 6.0) *
            (layers / baseLayers)
    }

    fun nrPrbFor(bandwidthMHz: Double, scsKHz: Int): Int {
        require(scsKHz == 30) { "FR_RADIO_THROUGHPUT_V1 only ships FR1 SCS 30 kHz PRB values for now." }
        val key = nrPrbTableScs30Khz.keys.minBy { abs(it - bandwidthMHz) }
        return nrPrbTableScs30Khz[key]
            ?: error("No NR PRB value for ${bandwidthMHz} MHz at SCS $scsKHz kHz.")
    }

    fun mapAnfrBandToRatBand(
        operator: MobileOperator,
        technology: RadioTechnology,
        bandLabel: String
    ): String? {
        return SpectrumAllocationsFrMetro.find(operator, technology, bandLabel)?.let { allocation ->
            when (technology) {
                RadioTechnology.LTE_4G -> allocation.ratBandLte
                RadioTechnology.NR_5G -> allocation.ratBandNr
                else -> null
            }
        }
    }

    private fun applyDssPolicy(
        results: List<CarrierThroughputResult>,
        profile: ThroughputProfile,
        excluded: MutableList<ExcludedCarrier>,
        warnings: MutableList<String>
    ): List<CarrierThroughputResult> {
        if (profile.dssPolicy != DssPolicy.DO_NOT_DOUBLE_COUNT) return results

        val mutableResults = results.toMutableList()
        results
            .groupBy { "${it.operator}:${it.bandLabel}" }
            .filterValues { group ->
                group.any { it.technology == RadioTechnology.LTE_4G } &&
                    group.any { it.technology == RadioTechnology.NR_5G } &&
                    group.first().duplexMode == DuplexMode.FDD
            }
            .values
            .forEach { dssGroup ->
                val winner = dssGroup.maxBy { it.dlMbps }
                dssGroup.filter { it.sourceKey != winner.sourceKey }.forEach { loser ->
                    val reason = "Bande potentiellement partagee 4G/5G : non additionnee deux fois."
                    val index = mutableResults.indexOfFirst { it.sourceKey == loser.sourceKey }
                    if (index >= 0) {
                        mutableResults[index] = loser.copy(included = false, excludedReason = reason, warnings = loser.warnings + reason)
                    }
                    excluded += ExcludedCarrier(loser.sourceKey, loser.technology, loser.bandLabel, reason)
                }
                warnings += "Bande ${winner.bandLabel} potentiellement partagee 4G/5G : debit non additionne integralement."
            }

        return mutableResults
    }

    private fun RadioTechnology.labelForUi(): String {
        return when (this) {
            RadioTechnology.LTE_4G -> "4G LTE"
            RadioTechnology.NR_5G -> "5G NR"
            RadioTechnology.GSM_2G -> "2G GSM"
            RadioTechnology.UMTS_3G -> "3G UMTS"
        }
    }
}

object ThroughputProfiles {
    val prudent = ThroughputProfile(
        id = "PRUDENT",
        label = "Prudent",
        description = "Profil prudent : 4G 64-QAM DL, 16-QAM UL, 5G NR 64-QAM, CA limitee et DSS non double-compte.",
        lte = RatAssumptions(dlModulationOrder = 6, ulModulationOrder = 4, dlMimoLayers = 2, ulMimoLayers = 1, maxCaComponents = 3),
        nr = RatAssumptions(dlModulationOrder = 6, ulModulationOrder = 6, dlMimoLayers = 4, ulMimoLayers = 1, maxCaComponents = 1, scsKHz = 30, tddDlRatio = 0.70, tddUlRatio = 0.20, overheadDl = 0.14, overheadUl = 0.08),
        defaultDeviceCategory = DeviceCategory.AVERAGE_PHONE
    )

    val standard = ThroughputProfile(
        id = "STANDARD",
        label = "Standard",
        description = "Profil standard : 4G 256-QAM DL MIMO 2x2, UL 64-QAM telephone, 5G n78 256-QAM DL MIMO 4x4, UL 64-QAM 2 couches, DSS non double-compte.",
        lte = RatAssumptions(dlModulationOrder = 8, ulModulationOrder = 6, dlMimoLayers = 2, ulMimoLayers = 1, maxCaComponents = 5),
        nr = RatAssumptions(dlModulationOrder = 8, ulModulationOrder = 6, dlMimoLayers = 4, ulMimoLayers = 2, maxCaComponents = 1, scsKHz = 30, tddDlRatio = 0.70, tddUlRatio = 0.20, overheadDl = 0.14, overheadUl = 0.08),
        defaultDeviceCategory = DeviceCategory.RECENT_PHONE
    )

    val ideal = ThroughputProfile(
        id = "IDEAL",
        label = "Profil ideal",
        description = "Profil ideal : meilleures conditions radio plausibles, 4G DL MIMO 4x4, 5G NR 256-QAM, CA plus ouverte, sans double comptage DSS.",
        lte = RatAssumptions(dlModulationOrder = 8, ulModulationOrder = 6, dlMimoLayers = 4, ulMimoLayers = 1, maxCaComponents = 5),
        nr = RatAssumptions(dlModulationOrder = 8, ulModulationOrder = 8, dlMimoLayers = 4, ulMimoLayers = 2, maxCaComponents = 2, scsKHz = 30, tddDlRatio = 0.70, tddUlRatio = 0.20, overheadDl = 0.14, overheadUl = 0.08),
        defaultDeviceCategory = DeviceCategory.FLAGSHIP
    )
}
