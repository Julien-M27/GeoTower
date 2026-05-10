package fr.geotower.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioThroughputEngineTest {
    @Test
    fun allocationsMatchKnownFrenchMetroBands() {
        val orangeN78 = SpectrumAllocationsFrMetro.find(
            operator = MobileOperator.ORANGE,
            technology = RadioTechnology.NR_5G,
            bandLabel = "n78"
        )
        val free800 = SpectrumAllocationsFrMetro.find(
            operator = MobileOperator.FREE,
            technology = RadioTechnology.LTE_4G,
            bandLabel = "800"
        )

        assertNotNull(orangeN78)
        assertEquals(90.0, orangeN78!!.bandwidthMHz, 0.01)
        assertEquals("B28", SpectrumAllocationsFrMetro.find(MobileOperator.FREE, RadioTechnology.LTE_4G, "700")?.ratBandLte)
        assertNull(free800)
    }

    @Test
    fun nrRateUsesPrbTableAnd38_306Approximation() {
        val rate = RadioThroughputEngine.calculateNrRateMbps(
            bandwidthMHz = 90.0,
            scsKHz = 30,
            layers = 4,
            modulationOrder = 8,
            overhead = 0.14,
            tddRatio = 0.70
        )

        assertTrue("Expected around 1.47 Gbit/s, got $rate", rate in 1450.0..1490.0)
    }

    @Test
    fun lteApproxScalesWithBandwidthModulationAndLayers() {
        val standard20MHz = RadioThroughputEngine.calculateLteApproxMbps(
            bandwidthMHz = 20.0,
            modulationOrder = 6,
            layers = 2,
            downlink = true
        )
        val highEnd20MHz = RadioThroughputEngine.calculateLteApproxMbps(
            bandwidthMHz = 20.0,
            modulationOrder = 8,
            layers = 4,
            downlink = true
        )

        assertEquals(150.0, standard20MHz, 0.01)
        assertEquals(400.0, highEnd20MHz, 0.01)
    }

    @Test
    fun dssPolicyDoesNotDoubleCountSameFddBand() {
        val systems = listOf(
            SiteRadioSystem(
                sourceKey = "lte-2100",
                supportId = "site-1",
                operator = MobileOperator.ORANGE,
                technology = RadioTechnology.LTE_4G,
                bandLabel = "2100",
                status = SiteRadioStatus.IN_SERVICE
            ),
            SiteRadioSystem(
                sourceKey = "nr-2100",
                supportId = "site-1",
                operator = MobileOperator.ORANGE,
                technology = RadioTechnology.NR_5G,
                bandLabel = "2100",
                status = SiteRadioStatus.IN_SERVICE
            )
        )

        val result = RadioThroughputEngine.estimate(systems, ThroughputProfiles.standard)
        val included = result.perCarrierResults.filter { it.included }
        val excludedResult = result.perCarrierResults.single { !it.included }
        val excludedCarrier = result.excludedCarriers.single()

        assertEquals(2, result.perCarrierResults.size)
        assertEquals(1, included.size)
        assertEquals("nr-2100", included.single().sourceKey)
        assertEquals("lte-2100", excludedResult.sourceKey)
        assertEquals(excludedResult.sourceKey, excludedCarrier.sourceKey)
        assertEquals(RadioTechnology.LTE_4G, excludedCarrier.technology)
        assertEquals("2100", excludedCarrier.bandLabel)
        assertFalse(excludedResult.excludedReason.isNullOrBlank())
        assertFalse(excludedCarrier.reason.isBlank())
        assertFalse(result.warnings.isEmpty())
    }
}
