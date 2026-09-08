package fr.geotower.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThroughputBandOverrideTest {
    @Test
    fun aggregationSettingsOverrideTheProfileCapsByGeneration() {
        val configured = ThroughputProfiles.prudent.withAggregationSettings(
            ThroughputAggregationSettings(lteMaxComponents = 5, nrMaxComponents = 4)
        )

        assertEquals(5, configured.lte.maxCaComponents)
        assertEquals(4, configured.nr.maxCaComponents)
    }

    @Test
    fun codecRoundTripsSupportedAdvancedBandFields() {
        val overrides = mapOf(
            "5:3500:5G 3500" to ThroughputBandOverride(
                bandwidthMHz = 80.0,
                duplexMode = DuplexMode.TDD,
                dlModulationOrder = 8,
                ulModulationOrder = 6,
                dlMimoLayers = 4,
                ulMimoLayers = 2,
                tddDlRatio = 0.75,
                tddUlRatio = 0.20
            )
        )

        val decoded = ThroughputBandOverrideCodec.decode(
            ThroughputBandOverrideCodec.encode(overrides)
        )

        assertEquals(overrides, decoded)
    }

    @Test
    fun codecIgnoresMalformedEntriesAndKeepsValidEntries() {
        val encoded = """
            {"version":1,"bands":[
              {"key":"valid","bandwidthMHz":20.0,"dlModulationOrder":6},
              {"key":"bad","bandwidthMHz":-1.0,"dlModulationOrder":0},
              {"bandwidthMHz":10.0}
            ]}
        """.trimIndent()

        val decoded = ThroughputBandOverrideCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(20.0, decoded.getValue("valid").bandwidthMHz ?: 0.0, 0.0)
        assertEquals(6, decoded.getValue("valid").dlModulationOrder)
        assertTrue(decoded.getValue("valid").duplexMode == null)
    }
}
