package fr.geotower.radio

import fr.geotower.data.CaIsedSectorDetail
import fr.geotower.data.CaIsedSiteDetails
import fr.geotower.utils.FreqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaIsedThroughputModelTest {

    @Test
    fun `resolves Canadian bands and duplex modes accurately`() {
        assertEquals("B71", CaIsedThroughputModel.resolveCanadianBandSpec(622.0, 668.0, 600).ratBandLte)
        assertEquals(DuplexMode.SDL, CaIsedThroughputModel.resolveCanadianBandSpec(722.0, null, 700).duplexMode)
        assertEquals("B2/B25", CaIsedThroughputModel.resolveCanadianBandSpec(1967.5, 1887.5, 1900).ratBandLte)
        assertEquals("B4/B66", CaIsedThroughputModel.resolveCanadianBandSpec(2132.5, 1732.5, 2100).ratBandLte)
        assertEquals("B7", CaIsedThroughputModel.resolveCanadianBandSpec(2655.0, 2535.0, 2600).ratBandLte)
        assertEquals(DuplexMode.TDD, CaIsedThroughputModel.resolveCanadianBandSpec(2595.0, null, 2600).duplexMode)
        assertEquals("n78/n77", CaIsedThroughputModel.resolveCanadianBandSpec(3550.0, 3550.0, 3500).ratBandNr)
    }

    @Test
    fun `aggregates distinct carriers on same sector without double counting duplicate PCIs`() {
        val caDetails = CaIsedSiteDetails(
            sectors = listOf(
                CaIsedSectorDetail(
                    technology = "LTE",
                    txFrequency = 1967.5,
                    rxFrequency = 1887.5,
                    bandwidth = 20.0,
                    txPower = 41.2,
                    txAntGain = 5.0,
                    txLineLoss = 0.0,
                    txNumberAntennas = 10,
                    rxNumberAntennas = 4,
                    txAntHeight = 1.0,
                    txAntAzimuth = 0,
                    physicalId = "30"
                ),
                CaIsedSectorDetail(
                    technology = "LTE",
                    txFrequency = 1952.5,
                    rxFrequency = 1872.5,
                    bandwidth = 20.0,
                    txPower = 41.2,
                    txAntGain = 5.0,
                    txLineLoss = 0.0,
                    txNumberAntennas = 4,
                    rxNumberAntennas = 4,
                    txAntHeight = 1.0,
                    txAntAzimuth = 0,
                    physicalId = "30"
                ),
                // Same 1967.5 MHz frequency, different PCI -> must NOT double-count bandwidth
                CaIsedSectorDetail(
                    technology = "LTE",
                    txFrequency = 1967.5,
                    rxFrequency = 1887.5,
                    bandwidth = 20.0,
                    txPower = 41.2,
                    txAntGain = 5.0,
                    txLineLoss = 0.0,
                    txNumberAntennas = 12,
                    rxNumberAntennas = 4,
                    txAntHeight = 1.0,
                    txAntAzimuth = 0,
                    physicalId = "417"
                )
            )
        )

        val bands = listOf(FreqBand("4G 1900 (PCS)", "En service", "", emptyList(), gen = 4, value = 1900))
        val profiles = CaIsedThroughputModel.buildCanadianBandProfiles(
            bands = bands,
            caDetails = caDetails,
            fallbackSupportHeightM = 1.0,
            siteLat = 43.6532,
            siteLon = -79.3832,
            selectedLat = null,
            selectedLon = null
        )

        assertEquals(1, profiles.size)
        assertEquals(40.0, profiles.first().bandwidthMHz, 0.01)
        assertEquals(false, profiles.first().bandwidthIsEstimated)
        assertEquals(12, profiles.first().txAntennas)
    }

    @Test
    fun `caps MIMO layers to base station TX antennas and applies TDD downlink allocation`() {
        val caDetails = CaIsedSiteDetails(
            sectors = listOf(
                CaIsedSectorDetail(
                    technology = "5GNR",
                    txFrequency = 3550.0,
                    rxFrequency = 3550.0,
                    bandwidth = 80.0,
                    downlinkAllocation = 73.0,
                    txPower = 80.0,
                    txAntGain = 17.5,
                    txLineLoss = 1.0,
                    txNumberAntennas = 2,
                    rxNumberAntennas = 2,
                    txAntHeight = 30.0,
                    txAntAzimuth = 120
                )
            )
        )

        val bands = listOf(FreqBand("5G 3500/3800", "En service", "", emptyList(), gen = 5, value = 3500))
        val profile = CaIsedThroughputModel.buildCanadianBandProfiles(
            bands = bands,
            caDetails = caDetails,
            fallbackSupportHeightM = 30.0,
            siteLat = 45.5017,
            siteLon = -73.5673,
            selectedLat = null,
            selectedLon = null
        ).first()

        assertEquals(0.73, profile.tddDlRatio!!, 0.001)
        val estimate = CaIsedThroughputModel.estimateBandThroughput(
            profile = profile,
            throughputProfile = ThroughputProfiles.ideal,
            bandOverride = null,
            siteLat = 45.5017,
            siteLon = -73.5673,
            selectedLat = null,
            selectedLon = null,
            receiverHeightMeters = 2.0,
            isCustomPreset = false
        )
        // Even with Ideal profile (4 layers), 2T2R site caps DL MIMO at 2 layers
        assertEquals(2, estimate.dlMimoLayers)
        assertTrue(estimate.dlMbps > 400.0 && estimate.dlMbps < 900.0)
    }

    @Test
    fun `link budget reflects real EIRP, distance, and directional beamwidth`() {
        val siteLat = 45.5017
        val siteLon = -73.5673
        // ~500m North (azimuth 0) vs ~2000m North vs ~500m East (azimuth 90, off-boresight)
        val north500mLat = siteLat + 0.0045
        val north2000mLat = siteLat + 0.0180
        val east500mLon = siteLon + 0.0064

        val macroDetails = CaIsedSiteDetails(
            sectors = listOf(
                CaIsedSectorDetail(
                    technology = "LTE",
                    txFrequency = 2132.5,
                    rxFrequency = 1732.5,
                    bandwidth = 20.0,
                    txPower = 120.0,
                    txAntGain = 17.5,
                    txLineLoss = 1.5,
                    txNumberAntennas = 4,
                    rxNumberAntennas = 4,
                    txAntHeight = 32.0,
                    txAntAzimuth = 0,
                    txAntHorizBeamwidth = 65.0
                )
            )
        )
        val lowPowerDetails = CaIsedSiteDetails(
            sectors = listOf(
                CaIsedSectorDetail(
                    technology = "LTE",
                    txFrequency = 2132.5,
                    rxFrequency = 1732.5,
                    bandwidth = 20.0,
                    txPower = 0.5,
                    txAntGain = 3.0,
                    txLineLoss = 1.0,
                    txNumberAntennas = 4,
                    rxNumberAntennas = 4,
                    txAntHeight = 4.0,
                    txAntAzimuth = 0,
                    txAntHorizBeamwidth = 359.0
                )
            )
        )

        val bands = listOf(FreqBand("4G 2100 (AWS)", "En service", "", emptyList(), gen = 4, value = 2100))

        fun estimateAt(details: CaIsedSiteDetails, targetLat: Double, targetLon: Double): CaBandThroughputEstimate {
            val profile = CaIsedThroughputModel.buildCanadianBandProfiles(
                bands, details, 30.0, siteLat, siteLon, targetLat, targetLon
            ).first()
            return CaIsedThroughputModel.estimateBandThroughput(
                profile, ThroughputProfiles.standard, null, siteLat, siteLon, targetLat, targetLon, 2.0, false
            )
        }

        val macroNear = estimateAt(macroDetails, north500mLat, siteLon)
        val macroFar = estimateAt(macroDetails, north2000mLat, siteLon)
        val macroOffBeam = estimateAt(macroDetails, siteLat, east500mLon)
        val microAt500m = estimateAt(lowPowerDetails, north500mLat, siteLon)

        assertTrue("Closer position should yield higher DL throughput", macroNear.dlMbps > macroFar.dlMbps)
        assertTrue("On-boresight should beat 90 deg off-beam", macroNear.dlMbps > macroOffBeam.dlMbps)
        assertTrue("High-EIRP macro should beat 0.5W microcell at 500m", macroNear.dlMbps > microAt500m.dlMbps)
    }
}
