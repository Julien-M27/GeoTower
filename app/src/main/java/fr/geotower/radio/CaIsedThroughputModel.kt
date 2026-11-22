package fr.geotower.radio

import fr.geotower.data.CaIsedSectorDetail
import fr.geotower.data.CaIsedSiteDetails
import fr.geotower.ui.components.ThroughputConeDistance
import fr.geotower.ui.components.throughputBandKey
import fr.geotower.ui.components.throughputCalculationBands
import fr.geotower.utils.FreqBand
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class CaBandSpec(
    val bandValue: Int,
    val ratBandLte: String,
    val ratBandNr: String,
    val duplexMode: DuplexMode,
    val centerFreqMHz: Double
)

data class CaBandRadioProfile(
    val bandKey: String,
    val band: FreqBand,
    val technology: RadioTechnology,
    val bandValue: Int,
    val ratBand: String,
    val duplexMode: DuplexMode,
    val centerFreqMHz: Double,
    val bandwidthMHz: Double,
    val bandwidthIsEstimated: Boolean,
    val txFrequenciesMHz: List<Double>,
    val eirpDbm: Double?,
    val eirpWatts: Double?,
    val txPowerWatts: Double?,
    val antGainDbi: Double?,
    val lineLossDb: Double?,
    val txAntennas: Int?,
    val rxAntennas: Int?,
    val antennaHeightM: Double?,
    val tiltDeg: Double?,
    val horizBeamwidthDeg: Double?,
    val vertBeamwidthDeg: Double?,
    val azimuths: List<Double>,
    val representativeAzimuthDeg: Double?,
    val tddDlRatio: Double?,
    val tddUlRatio: Double?,
    val hasSectorData: Boolean
)

data class CaBandThroughputEstimate(
    val dlMbps: Double,
    val ulMbps: Double,
    val dlModulationOrder: Int,
    val ulModulationOrder: Int,
    val dlMimoLayers: Int,
    val ulMimoLayers: Int,
    val coneDistance: ThroughputConeDistance?,
    val estimatedDlSinrDb: Double? = null,
    val estimatedDlRsrpDbm: Double? = null
)

object CaIsedThroughputModel {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val UE_TX_POWER_DBM = 23.0
    private const val UE_NOISE_FIGURE_DB = 7.0
    private const val BS_NOISE_FIGURE_DB = 5.0
    private const val THERMAL_NOISE_DBM_HZ = -174.0
    private const val MAX_DL_SINR_DB = 28.0
    private const val MAX_UL_SINR_DB = 24.0
    private const val DEFAULT_MACRO_EIRP_DBM = 58.0

    fun resolveCanadianBandSpec(
        txFreqMHz: Double?,
        rxFreqMHz: Double?,
        fallbackBandValue: Int
    ): CaBandSpec {
        if (txFreqMHz != null && txFreqMHz > 0.0) {
            return when {
                txFreqMHz in 600.0..665.0 -> CaBandSpec(600, "B71", "n71", DuplexMode.FDD, txFreqMHz)
                txFreqMHz in 716.0..728.0 && rxFreqMHz == null -> CaBandSpec(700, "B29", "n29", DuplexMode.SDL, txFreqMHz)
                txFreqMHz in 695.0..760.0 -> CaBandSpec(700, "B12/B13/B17", "n12", DuplexMode.FDD, txFreqMHz)
                txFreqMHz in 850.0..900.0 -> CaBandSpec(850, "B5", "n5", DuplexMode.FDD, txFreqMHz)
                txFreqMHz in 1920.0..2000.0 -> CaBandSpec(1900, "B2/B25", "n2/n25", DuplexMode.FDD, txFreqMHz)
                txFreqMHz in 2100.0..2200.0 -> CaBandSpec(2100, "B4/B66", "n66", DuplexMode.FDD, txFreqMHz)
                txFreqMHz in 2345.0..2365.0 -> CaBandSpec(2300, "B30", "n30", DuplexMode.FDD, txFreqMHz)
                txFreqMHz in 2495.0..2695.0 -> {
                    val isFdd = rxFreqMHz != null && abs(txFreqMHz - rxFreqMHz) > 5.0
                    if (isFdd) {
                        CaBandSpec(2600, "B7", "n7", DuplexMode.FDD, txFreqMHz)
                    } else {
                        CaBandSpec(2600, "B38/B41", "n41", DuplexMode.TDD, txFreqMHz)
                    }
                }
                txFreqMHz in 3400.0..3900.0 -> CaBandSpec(3500, "B42/B43", "n78/n77", DuplexMode.TDD, txFreqMHz)
                txFreqMHz in 3900.01..4250.0 -> CaBandSpec(3800, "B43", "n77", DuplexMode.TDD, txFreqMHz)
                txFreqMHz >= 24000.0 -> CaBandSpec(26000, "n258", "n258/n260", DuplexMode.TDD, txFreqMHz)
                else -> fallbackSpec(fallbackBandValue, txFreqMHz)
            }
        }
        return fallbackSpec(fallbackBandValue, fallbackBandValue.toDouble().coerceAtLeast(700.0))
    }

    private fun fallbackSpec(bandValue: Int, centerFreqMHz: Double): CaBandSpec {
        return when (bandValue) {
            600 -> CaBandSpec(600, "B71", "n71", DuplexMode.FDD, 635.0)
            700 -> CaBandSpec(700, "B12/B13/B17", "n12", DuplexMode.FDD, 735.0)
            850 -> CaBandSpec(850, "B5", "n5", DuplexMode.FDD, 880.0)
            1900 -> CaBandSpec(1900, "B2/B25", "n2/n25", DuplexMode.FDD, 1960.0)
            2100 -> CaBandSpec(2100, "B4/B66", "n66", DuplexMode.FDD, 2140.0)
            2300 -> CaBandSpec(2300, "B30", "n30", DuplexMode.FDD, 2355.0)
            2600 -> CaBandSpec(2600, "B7", "n7", DuplexMode.FDD, 2655.0)
            3500 -> CaBandSpec(3500, "B42/B43", "n78/n77", DuplexMode.TDD, 3650.0)
            3800 -> CaBandSpec(3800, "B43", "n77", DuplexMode.TDD, 3950.0)
            else -> CaBandSpec(bandValue, "LTE", "NR", DuplexMode.FDD, centerFreqMHz)
        }
    }

    fun buildCanadianBandProfiles(
        bands: List<FreqBand>,
        caDetails: CaIsedSiteDetails?,
        fallbackSupportHeightM: Double?,
        siteLat: Double,
        siteLon: Double,
        selectedLat: Double?,
        selectedLon: Double?
    ): List<CaBandRadioProfile> {
        val sectors = caDetails?.sectors.orEmpty()
        val selectedBearing = if (selectedLat != null && selectedLon != null) {
            bearingDegreesBetween(siteLat, siteLon, selectedLat, selectedLon)
        } else {
            null
        }

        return throughputCalculationBands(bands).map { band ->
            val technology = if (band.gen == 5) RadioTechnology.NR_5G else RadioTechnology.LTE_4G
            val key = throughputBandKey(band)
            val matchingSectors = sectors.filter { sector ->
                sectorTechnologyMatches(sector.technology, technology) &&
                    resolveCanadianBandSpec(sector.txFrequency, sector.rxFrequency, band.value).bandValue == band.value
            }

            if (matchingSectors.isEmpty()) {
                val fallback = fallbackSpec(band.value, band.value.toDouble())
                return@map CaBandRadioProfile(
                    bandKey = key,
                    band = band,
                    technology = technology,
                    bandValue = band.value,
                    ratBand = if (technology == RadioTechnology.NR_5G) fallback.ratBandNr else fallback.ratBandLte,
                    duplexMode = fallback.duplexMode,
                    centerFreqMHz = fallback.centerFreqMHz,
                    bandwidthMHz = defaultCaBandwidthMHz(band.gen, band.value),
                    bandwidthIsEstimated = true,
                    txFrequenciesMHz = emptyList(),
                    eirpDbm = null,
                    eirpWatts = null,
                    txPowerWatts = null,
                    antGainDbi = null,
                    lineLossDb = null,
                    txAntennas = null,
                    rxAntennas = null,
                    antennaHeightM = fallbackSupportHeightM,
                    tiltDeg = null,
                    horizBeamwidthDeg = null,
                    vertBeamwidthDeg = null,
                    azimuths = emptyList(),
                    representativeAzimuthDeg = null,
                    tddDlRatio = null,
                    tddUlRatio = null,
                    hasSectorData = false
                )
            }

            val allAzimuths = matchingSectors
                .mapNotNull { it.txAntAzimuth?.toDouble() }
                .filter { it in 0.0..360.0 }
                .distinctBy { it.roundToInt() }
                .sorted()

            val byAzimuth = matchingSectors.groupBy { it.txAntAzimuth }
            val chosenAzimuth: Int? = when {
                selectedBearing != null && byAzimuth.keys.any { it != null } -> {
                    byAzimuth.keys.filterNotNull().minByOrNull { az ->
                        angularDistanceDegrees(az.toDouble(), selectedBearing)
                    }
                }
                else -> {
                    byAzimuth.entries.maxWithOrNull(
                        compareBy<Map.Entry<Int?, List<CaIsedSectorDetail>>> { (_, list) ->
                            deduplicateCarriers(list).sumOf { it.bandwidth ?: 0.0 }
                        }.thenBy { (_, list) ->
                            list.mapNotNull { it.eirpDbm }.maxOrNull() ?: Double.NEGATIVE_INFINITY
                        }
                    )?.key
                }
            }

            val representativePool = byAzimuth[chosenAzimuth].orEmpty().ifEmpty { matchingSectors }
            val distinctCarriers = deduplicateCarriers(representativePool)
            val measuredBandwidths = distinctCarriers.mapNotNull { it.bandwidth?.takeIf { bw -> bw > 0.0 } }
            val bandwidthIsEstimated = measuredBandwidths.isEmpty()
            val totalBandwidthMHz = if (measuredBandwidths.isNotEmpty()) {
                measuredBandwidths.sum().coerceIn(1.4, 200.0)
            } else {
                defaultCaBandwidthMHz(band.gen, band.value)
            }

            val primarySector = distinctCarriers.maxByOrNull { it.eirpDbm ?: it.txPower ?: 0.0 } ?: representativePool.first()
            val spec = resolveCanadianBandSpec(primarySector.txFrequency, primarySector.rxFrequency, band.value)

            val txFreqs = distinctCarriers
                .mapNotNull { it.txFrequency?.takeIf { f -> f > 0.0 } }
                .distinctBy { (it * 100.0).roundToInt() }
                .sorted()

            val dlAlloc = distinctCarriers
                .mapNotNull { it.downlinkAllocation?.takeIf { alloc -> alloc in 1.0..99.0 } }
                .maxOrNull()
            val tddDlRatio = if (spec.duplexMode == DuplexMode.TDD && dlAlloc != null) {
                (dlAlloc / 100.0).coerceIn(0.10, 0.95)
            } else {
                null
            }
            val tddUlRatio = if (tddDlRatio != null) {
                ((1.0 - tddDlRatio) * 0.85).coerceIn(0.05, 0.80)
            } else {
                null
            }

            val rawTilt = distinctCarriers
                .mapNotNull { it.txAntElevationAngle?.let(::abs)?.takeIf { t -> t > 0.5 && t <= 45.0 } }
                .averageOrNull()

            CaBandRadioProfile(
                bandKey = key,
                band = band,
                technology = technology,
                bandValue = band.value,
                ratBand = if (technology == RadioTechnology.NR_5G) spec.ratBandNr else spec.ratBandLte,
                duplexMode = spec.duplexMode,
                centerFreqMHz = txFreqs.averageOrNull() ?: spec.centerFreqMHz,
                bandwidthMHz = totalBandwidthMHz,
                bandwidthIsEstimated = bandwidthIsEstimated,
                txFrequenciesMHz = txFreqs,
                eirpDbm = representativePool.mapNotNull { it.eirpDbm }.maxOrNull(),
                eirpWatts = representativePool.mapNotNull { it.eirpWatts }.maxOrNull(),
                txPowerWatts = representativePool.mapNotNull { it.txPower?.takeIf { p -> p > 0.0 } }.maxOrNull(),
                antGainDbi = representativePool.mapNotNull { it.txAntGain }.maxOrNull(),
                lineLossDb = representativePool.mapNotNull { it.txLineLoss }.minOrNull(),
                txAntennas = representativePool.mapNotNull { it.txNumberAntennas?.takeIf { n -> n > 0 } }.maxOrNull(),
                rxAntennas = representativePool.mapNotNull { it.rxNumberAntennas?.takeIf { n -> n > 0 } }.maxOrNull(),
                antennaHeightM = representativePool.mapNotNull { it.txAntHeight?.takeIf { h -> h > 0.0 } }.maxOrNull()
                    ?: fallbackSupportHeightM,
                tiltDeg = rawTilt,
                horizBeamwidthDeg = representativePool.mapNotNull { it.txAntHorizBeamwidth?.takeIf { b -> b > 1.0 } }.maxOrNull(),
                vertBeamwidthDeg = representativePool.mapNotNull { it.txAntVertBeamwidth?.takeIf { b -> b > 1.0 } }.maxOrNull(),
                azimuths = allAzimuths,
                representativeAzimuthDeg = chosenAzimuth?.toDouble(),
                tddDlRatio = tddDlRatio,
                tddUlRatio = tddUlRatio,
                hasSectorData = true
            )
        }
    }

    fun estimateBandThroughput(
        profile: CaBandRadioProfile,
        throughputProfile: ThroughputProfile,
        bandOverride: ThroughputBandOverride?,
        siteLat: Double,
        siteLon: Double,
        selectedLat: Double?,
        selectedLon: Double?,
        receiverHeightMeters: Double,
        isCustomPreset: Boolean,
        customDlMultiplier: Double = 1.0,
        customUlMultiplier: Double = 1.0
    ): CaBandThroughputEstimate {
        val baseAssumptions = if (profile.technology == RadioTechnology.NR_5G) throughputProfile.nr else throughputProfile.lte
        val effectiveBw = bandOverride?.bandwidthMHz ?: profile.bandwidthMHz
        val effectiveDuplex = bandOverride?.duplexMode ?: profile.duplexMode
        val dlModOrder = bandOverride?.dlModulationOrder ?: baseAssumptions.dlModulationOrder
        val ulModOrder = bandOverride?.ulModulationOrder ?: baseAssumptions.ulModulationOrder

        val dlMimo = bandOverride?.dlMimoLayers ?: effectiveDlMimoLayers(
            bsTxAntennas = profile.txAntennas,
            bandValue = profile.bandValue,
            profileDlLayers = baseAssumptions.dlMimoLayers
        )
        val ulMimo = bandOverride?.ulMimoLayers ?: effectiveUlMimoLayers(
            bsRxAntennas = profile.rxAntennas,
            profileUlLayers = baseAssumptions.ulMimoLayers
        )

        val tddDl = bandOverride?.tddDlRatio ?: profile.tddDlRatio ?: baseAssumptions.tddDlRatio
        val tddUl = bandOverride?.tddUlRatio ?: profile.tddUlRatio ?: baseAssumptions.tddUlRatio

        val rawDlMbps: Double
        val rawUlMbps: Double
        if (profile.technology == RadioTechnology.NR_5G) {
            val scsKHz = if (profile.bandValue >= 24000) 120 else baseAssumptions.scsKHz
            val nPrb = RadioThroughputEngine.nrPrbFor(effectiveBw, scsKHz)
            rawDlMbps = RadioThroughputEngine.calculateNrRateMbps(
                bandwidthMHz = effectiveBw,
                scsKHz = scsKHz,
                nPrb = nPrb,
                layers = dlMimo,
                modulationOrder = dlModOrder,
                overhead = baseAssumptions.overheadDl,
                tddRatio = if (effectiveDuplex == DuplexMode.TDD) tddDl else 1.0
            )
            rawUlMbps = if (effectiveDuplex == DuplexMode.SDL) {
                0.0
            } else {
                RadioThroughputEngine.calculateNrRateMbps(
                    bandwidthMHz = effectiveBw,
                    scsKHz = scsKHz,
                    nPrb = nPrb,
                    layers = ulMimo,
                    modulationOrder = ulModOrder,
                    overhead = baseAssumptions.overheadUl,
                    tddRatio = if (effectiveDuplex == DuplexMode.TDD) tddUl else 1.0
                )
            }
        } else {
            val lteTddDlScale = if (effectiveDuplex == DuplexMode.TDD) tddDl else 1.0
            val lteTddUlScale = if (effectiveDuplex == DuplexMode.TDD) tddUl else 1.0
            rawDlMbps = RadioThroughputEngine.calculateLteApproxMbps(
                bandwidthMHz = effectiveBw,
                modulationOrder = dlModOrder,
                layers = dlMimo,
                downlink = true,
                overhead = baseAssumptions.overheadDl
            ) * lteTddDlScale
            rawUlMbps = if (effectiveDuplex == DuplexMode.SDL) {
                0.0
            } else {
                RadioThroughputEngine.calculateLteApproxMbps(
                    bandwidthMHz = effectiveBw,
                    modulationOrder = ulModOrder,
                    layers = ulMimo,
                    downlink = false,
                    overhead = baseAssumptions.overheadUl
                ) * lteTddUlScale
            }
        }

        val coneDistance = estimateCanadianConeDistance(profile, receiverHeightMeters)

        if (isCustomPreset) {
            return CaBandThroughputEstimate(
                dlMbps = rawDlMbps * customDlMultiplier,
                ulMbps = rawUlMbps * customUlMultiplier,
                dlModulationOrder = dlModOrder,
                ulModulationOrder = ulModOrder,
                dlMimoLayers = dlMimo,
                ulMimoLayers = ulMimo,
                coneDistance = coneDistance
            )
        }

        if (selectedLat != null && selectedLon != null) {
            val distanceM = distanceMetersBetween(siteLat, siteLon, selectedLat, selectedLon).coerceAtLeast(10.0)
            val bearingDeg = bearingDegreesBetween(siteLat, siteLon, selectedLat, selectedLon)
            val azimuthDelta = profile.representativeAzimuthDeg?.let { angularDistanceDegrees(it, bearingDeg) }
                ?: profile.azimuths.minOfOrNull { angularDistanceDegrees(it, bearingDeg) }

            val rfBudget = computeRfLinkBudget(
                profile = profile,
                bandwidthMHz = effectiveBw,
                distanceMeters = distanceM,
                azimuthDeltaDeg = azimuthDelta,
                receiverHeightMeters = receiverHeightMeters
            )
            return CaBandThroughputEstimate(
                dlMbps = rawDlMbps * rfBudget.dlFactor,
                ulMbps = rawUlMbps * rfBudget.ulFactor,
                dlModulationOrder = dlModOrder,
                ulModulationOrder = ulModOrder,
                dlMimoLayers = dlMimo,
                ulMimoLayers = ulMimo,
                coneDistance = coneDistance,
                estimatedDlSinrDb = rfBudget.dlSinrDb,
                estimatedDlRsrpDbm = rfBudget.dlRsrpDbm
            )
        }

        return CaBandThroughputEstimate(
            dlMbps = rawDlMbps,
            ulMbps = rawUlMbps,
            dlModulationOrder = dlModOrder,
            ulModulationOrder = ulModOrder,
            dlMimoLayers = dlMimo,
            ulMimoLayers = ulMimo,
            coneDistance = coneDistance
        )
    }

    fun formatBandDetails(profile: CaBandRadioProfile): String {
        val parts = mutableListOf<String>()
        if (profile.txFrequenciesMHz.isNotEmpty()) {
            val freqText = profile.txFrequenciesMHz.joinToString(" / ") { f ->
                if (f % 1.0 == 0.0) "${f.toInt()}" else String.format(Locale.US, "%.2f", f)
            }
            parts += "TX $freqText MHz"
        }
        profile.eirpDbm?.let { eirp ->
            val watts = profile.eirpWatts
            val wattsText = when {
                watts == null -> ""
                watts >= 1000.0 -> String.format(Locale.US, " (%.1f kW)", watts / 1000.0)
                watts >= 10.0 -> String.format(Locale.US, " (%.0f W)", watts)
                else -> String.format(Locale.US, " (%.1f W)", watts)
            }
            parts += String.format(Locale.US, "PIRE %.1f dBm%s", eirp, wattsText)
        } ?: profile.txPowerWatts?.let { p ->
            parts += String.format(Locale.US, "TX %.1f W", p)
        }
        if (profile.txAntennas != null && profile.rxAntennas != null) {
            parts += "${profile.txAntennas}T${profile.rxAntennas}R"
        } else if (profile.txAntennas != null) {
            parts += "${profile.txAntennas}T"
        }
        profile.antennaHeightM?.let { h ->
            parts += String.format(Locale.US, "H %.1f m", h)
        }
        return parts.joinToString(" \u2022 ").ifBlank {
            "${profile.ratBand} (${profile.duplexMode.name})"
        }
    }

    fun estimateCanadianConeDistance(
        profile: CaBandRadioProfile,
        receiverHeightMeters: Double = 2.0
    ): ThroughputConeDistance? {
        val height = profile.antennaHeightM ?: return null
        val verticalDelta = (height - receiverHeightMeters).coerceAtLeast(1.0)
        val eirp = profile.eirpDbm ?: DEFAULT_MACRO_EIRP_DBM
        val freqGHz = (profile.centerFreqMHz / 1000.0).coerceAtLeast(0.6)

        // Compute RF-based coverage distances where DL SINR hits ~18 dB (near/peak), ~11 dB (center), ~4 dB (far)
        val isIndoorOrMicro = eirp < 48.0 || height < 8.0
        val rfCenter = solveDistanceForTargetSinr(eirp, freqGHz, profile.bandwidthMHz, height, receiverHeightMeters, targetSinrDb = 12.0)
        val rfNear = solveDistanceForTargetSinr(eirp, freqGHz, profile.bandwidthMHz, height, receiverHeightMeters, targetSinrDb = 19.0)
        val rfFar = solveDistanceForTargetSinr(eirp, freqGHz, profile.bandwidthMHz, height, receiverHeightMeters, targetSinrDb = 4.0)

        val tilt = profile.tiltDeg
        return if (tilt != null && tilt > 0.5 && !isIndoorOrMicro) {
            val vBw = (profile.vertBeamwidthDeg ?: 10.0).coerceIn(4.0, 25.0)
            val minTilt = (tilt - vBw * 0.35).coerceAtLeast(1.5)
            val maxTilt = (tilt + vBw * 0.35).coerceAtMost(45.0)
            val geoCenter = verticalDelta / tan(Math.toRadians(tilt))
            val geoNear = verticalDelta / tan(Math.toRadians(maxTilt))
            val geoFar = verticalDelta / tan(Math.toRadians(minTilt))
            ThroughputConeDistance(
                centerMeters = min(geoCenter, rfCenter).coerceAtLeast(15.0),
                nearMeters = min(geoNear, rfNear).coerceAtLeast(10.0),
                farMeters = min(geoFar, rfFar).coerceAtLeast(30.0)
            )
        } else {
            ThroughputConeDistance(
                centerMeters = rfCenter.coerceAtLeast(15.0),
                nearMeters = rfNear.coerceAtLeast(10.0),
                farMeters = rfFar.coerceAtLeast(30.0)
            )
        }
    }

    private data class RfLinkBudgetResult(
        val dlFactor: Double,
        val ulFactor: Double,
        val dlSinrDb: Double,
        val dlRsrpDbm: Double
    )

    private fun computeRfLinkBudget(
        profile: CaBandRadioProfile,
        bandwidthMHz: Double,
        distanceMeters: Double,
        azimuthDeltaDeg: Double?,
        receiverHeightMeters: Double
    ): RfLinkBudgetResult {
        val hTx = (profile.antennaHeightM ?: 25.0).coerceAtLeast(1.5)
        val freqGHz = (profile.centerFreqMHz / 1000.0).coerceAtLeast(0.6)
        val pathLossDb = compute3gppUmaPathLossDb(distanceMeters, freqGHz, hTx, receiverHeightMeters)

        val hAttenDb = computeHorizontalAttenuationDb(azimuthDeltaDeg, profile.horizBeamwidthDeg)
        val vAttenDb = computeVerticalAttenuationDb(
            distanceMeters = distanceMeters,
            hTx = hTx,
            hRx = receiverHeightMeters,
            tiltDeg = profile.tiltDeg,
            vertBeamwidthDeg = profile.vertBeamwidthDeg
        )

        val eirpDbm = profile.eirpDbm ?: DEFAULT_MACRO_EIRP_DBM
        val bwHz = (bandwidthMHz * 1e6).coerceAtLeast(1.4e6)
        val dlNoiseDbm = THERMAL_NOISE_DBM_HZ + 10.0 * log10(bwHz) + UE_NOISE_FIGURE_DB
        val dlSignalDbm = eirpDbm - pathLossDb - hAttenDb - vAttenDb
        val dlSnrDb = dlSignalDbm - dlNoiseDbm
        val dlSinrDb = softCapSinrDb(dlSnrDb, MAX_DL_SINR_DB)

        // Approximate per-RE RSRP (15 kHz / 30 kHz subcarrier count in bandwidth)
        val nSubcarriers = (bandwidthMHz * 1000.0 / 15.0 * 0.9).coerceAtLeast(72.0)
        val dlRsrpDbm = dlSignalDbm - 10.0 * log10(nSubcarriers)

        // Uplink: UE 23 dBm + BS antenna gain - BS line loss
        val bsGainDb = profile.antGainDbi ?: 15.0
        val bsLossDb = profile.lineLossDb ?: 2.0
        val ulNoiseDbm = THERMAL_NOISE_DBM_HZ + 10.0 * log10(bwHz) + BS_NOISE_FIGURE_DB
        val ulSignalDbm = UE_TX_POWER_DBM + bsGainDb - bsLossDb - pathLossDb - hAttenDb - vAttenDb
        val ulSnrDb = ulSignalDbm - ulNoiseDbm
        val ulSinrDb = softCapSinrDb(ulSnrDb, MAX_UL_SINR_DB)

        return RfLinkBudgetResult(
            dlFactor = sinrToThroughputFactor(dlSinrDb),
            ulFactor = sinrToThroughputFactor(ulSinrDb),
            dlSinrDb = dlSinrDb,
            dlRsrpDbm = dlRsrpDbm
        )
    }

    /**
     * Blended 3GPP TR 38.901 UMa LOS/NLOS path loss model (dB).
     */
    private fun compute3gppUmaPathLossDb(
        distanceMeters: Double,
        freqGHz: Double,
        hTx: Double,
        hRx: Double
    ): Double {
        val d2d = distanceMeters.coerceAtLeast(10.0)
        val d3d = hypot(d2d, abs(hTx - hRx))
        val pLos = if (d2d <= 18.0) {
            1.0
        } else {
            (18.0 / d2d) + (1.0 - 18.0 / d2d) * exp(-d2d / 63.0)
        }
        val plLos = 28.0 + 22.0 * log10(d3d) + 20.0 * log10(freqGHz)
        val plNlos = (13.54 + 39.08 * log10(d3d) + 20.0 * log10(freqGHz) - 0.6 * (hRx - 1.5))
            .coerceAtLeast(plLos)
        return pLos * plLos + (1.0 - pLos) * plNlos
    }

    private fun computeHorizontalAttenuationDb(
        azimuthDeltaDeg: Double?,
        horizBeamwidthDeg: Double?
    ): Double {
        if (azimuthDeltaDeg == null) return 0.0
        val bw = horizBeamwidthDeg ?: 65.0
        if (bw >= 300.0) return 0.0 // Omni / indoor antenna
        val halfBw = (bw / 2.0).coerceAtLeast(10.0)
        return min(12.0 * (azimuthDeltaDeg / (2.0 * halfBw)).pow(2), 25.0)
    }

    private fun computeVerticalAttenuationDb(
        distanceMeters: Double,
        hTx: Double,
        hRx: Double,
        tiltDeg: Double?,
        vertBeamwidthDeg: Double?
    ): Double {
        if (tiltDeg == null || tiltDeg <= 0.5) return 0.0
        val verticalDelta = abs(hTx - hRx)
        if (verticalDelta < 1.0) return 0.0
        val elevationDeg = Math.toDegrees(atan2(verticalDelta, distanceMeters.coerceAtLeast(1.0)))
        val vBw = (vertBeamwidthDeg ?: 12.0).coerceIn(5.0, 40.0)
        val delta = abs(elevationDeg - tiltDeg)
        return min(12.0 * (delta / vBw).pow(2), 18.0)
    }

    private fun softCapSinrDb(snrDb: Double, maxSinrDb: Double): Double {
        val snrLinear = 10.0.pow(snrDb / 10.0)
        val maxLinear = 10.0.pow(maxSinrDb / 10.0)
        val effectiveLinear = (snrLinear * maxLinear) / (snrLinear + maxLinear)
        return 10.0 * log10(effectiveLinear.coerceAtLeast(1e-4))
    }

    private fun sinrToThroughputFactor(sinrDb: Double): Double {
        // Maps SINR [-8 dB .. 25 dB] to realistic [0.03 .. 1.03] spectral efficiency factor
        val normalized = ((sinrDb + 8.0) / 33.0).coerceIn(0.0, 1.0)
        // Smooth S-curve reflecting AMC steps (QPSK -> 16QAM -> 64QAM -> 256QAM)
        return (0.03 + 1.00 * normalized.pow(1.25)).coerceIn(0.03, 1.03)
    }

    private fun solveDistanceForTargetSinr(
        eirpDbm: Double,
        freqGHz: Double,
        bandwidthMHz: Double,
        hTx: Double,
        hRx: Double,
        targetSinrDb: Double
    ): Double {
        val bwHz = (bandwidthMHz * 1e6).coerceAtLeast(1.4e6)
        val noiseDbm = THERMAL_NOISE_DBM_HZ + 10.0 * log10(bwHz) + UE_NOISE_FIGURE_DB
        var low = 10.0
        var high = 25_000.0
        repeat(24) {
            val mid = (low + high) * 0.5
            val pl = compute3gppUmaPathLossDb(mid, freqGHz, hTx, hRx)
            val snr = eirpDbm - pl - noiseDbm
            val sinr = softCapSinrDb(snr, MAX_DL_SINR_DB)
            if (sinr > targetSinrDb) {
                low = mid
            } else {
                high = mid
            }
        }
        return (low + high) * 0.5
    }

    private fun effectiveDlMimoLayers(
        bsTxAntennas: Int?,
        bandValue: Int,
        profileDlLayers: Int
    ): Int {
        val bsCapability = when {
            bsTxAntennas == null -> profileDlLayers
            bsTxAntennas <= 1 -> 1
            bsTxAntennas in 2..3 -> 2
            else -> 4
        }
        val ueBandCap = if (bandValue < 1000 && profileDlLayers <= 2) 2 else profileDlLayers
        return min(bsCapability, ueBandCap).coerceAtLeast(1)
    }

    private fun effectiveUlMimoLayers(
        bsRxAntennas: Int?,
        profileUlLayers: Int
    ): Int {
        val bsCapability = when {
            bsRxAntennas == null -> profileUlLayers
            bsRxAntennas <= 1 -> 1
            else -> 2
        }
        return min(bsCapability, profileUlLayers).coerceAtLeast(1)
    }

    private fun deduplicateCarriers(sectors: List<CaIsedSectorDetail>): List<CaIsedSectorDetail> {
        return sectors.distinctBy { sector ->
            val freqKey = sector.txFrequency?.let { (it * 10.0).roundToInt() } ?: -1
            val bwKey = sector.bandwidth?.let { (it * 10.0).roundToInt() } ?: -1
            "$freqKey:$bwKey"
        }
    }

    private fun sectorTechnologyMatches(rawTech: String?, technology: RadioTechnology): Boolean {
        val norm = rawTech?.uppercase(Locale.ROOT)?.trim().orEmpty()
        return when (technology) {
            RadioTechnology.NR_5G -> norm.contains("5G") || norm.contains("NR")
            RadioTechnology.LTE_4G -> norm.contains("LTE") || norm.contains("4G")
            else -> false
        }
    }

    private fun defaultCaBandwidthMHz(gen: Int, bandValue: Int): Double {
        return when (gen) {
            5 -> when (bandValue) {
                600, 700, 850 -> 10.0
                1900, 2100 -> 15.0
                3500, 3800 -> 60.0
                else -> 20.0
            }
            4 -> when (bandValue) {
                600, 700, 850, 2300 -> 10.0
                1900, 2100, 2600 -> 20.0
                else -> 10.0
            }
            else -> 10.0
        }
    }

    private fun distanceMetersBetween(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearingDegreesBetween(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg % 360.0) + 360.0) % 360.0
    }

    private fun angularDistanceDegrees(first: Double, second: Double): Double {
        val diff = abs(first - second) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
