package fr.geotower.ui.components

import fr.geotower.radio.MobileOperator
import fr.geotower.radio.RadioTechnology
import fr.geotower.radio.SiteRadioStatus
import fr.geotower.utils.FreqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThroughputSharedTest {
    @Test
    fun hiddenThroughputBandsMatchScreenAndShareRules() {
        assertTrue(isHiddenThroughputBand(band(gen = 5, value = 1800)))
        assertTrue(isHiddenThroughputBand(band(gen = 5, value = 26000)))
        assertFalse(isHiddenThroughputBand(band(gen = 5, value = 3500)))
        assertFalse(isHiddenThroughputBand(band(gen = 4, value = 1800)))
    }

    @Test
    fun throughputCalculationBandsKeepOnlyVisible4gAnd5gBands() {
        val calculatedBands = throughputCalculationBands(
            listOf(
                band(gen = 2, value = 900),
                band(gen = 3, value = 2100),
                band(gen = 4, value = 700),
                band(gen = 5, value = 1800),
                band(gen = 5, value = 3500),
                band(gen = 5, value = 26000)
            )
        )

        assertEquals(listOf(4 to 700, 5 to 3500), calculatedBands.map { it.gen to it.value })
    }

    @Test
    fun plannedThroughputBandDetectionMatchesScreenAndShareRules() {
        assertTrue(isPlannedThroughputBand(band(gen = 4, value = 700, status = "Projet")))
        assertTrue(isPlannedThroughputBand(band(gen = 5, value = 3500, status = "Approuvé")))
        assertTrue(isPlannedThroughputBand(band(gen = 5, value = 3500, status = "Planned")))
        assertFalse(isPlannedThroughputBand(band(gen = 4, value = 1800, status = "En service")))
    }

    @Test
    fun buildThroughputRadioSystemsMapsBandsForRadioEngine() {
        val systems = buildThroughputRadioSystems(
            bands = listOf(
                band(gen = 4, value = 1800, status = "En service", physDetails = listOf("Panel : 90\u00B0 (15m) [AER_ID: 9001]")),
                band(gen = 5, value = 3500, status = "Projet approuvé", physDetails = listOf("Panel : 120\u00B0 (18m)")),
                band(gen = 5, value = 1800)
            ),
            operator = MobileOperator.SFR,
            supportHeightMeters = 30.0
        )

        assertEquals(2, systems.size)
        assertEquals(RadioTechnology.LTE_4G, systems[0].technology)
        assertEquals(SiteRadioStatus.IN_SERVICE, systems[0].status)
        assertEquals(90.0, systems[0].azimuthDeg ?: -1.0, 0.0)
        assertEquals(15.0, systems[0].antennaHeightM ?: -1.0, 0.0)
        assertEquals(RadioTechnology.NR_5G, systems[1].technology)
        assertEquals(SiteRadioStatus.AUTHORIZED, systems[1].status)
    }

    @Test
    fun throughputExtractorsIgnorePanelIdSuffix() {
        val band = band(
            gen = 4,
            value = 1800,
            physDetails = listOf("Panel : 90\u00B0 (15m) [AER_ID: 9001]")
        )

        assertEquals(listOf(90.0), extractThroughputAzimuths(band))
        assertEquals(15.0, extractThroughputPanelHeightMeters(band, null) ?: -1.0, 0.0)
    }

    @Test
    fun formatsThroughputModulationLabelsFromEngineValues() {
        assertEquals(
            "256-QAM MIMO 4x4 DL / 64-QAM MIMO 2x2 UL",
            throughputModulationLabel(
                dlModulationOrder = 8,
                ulModulationOrder = 6,
                dlLayers = 4,
                ulLayers = 2
            )
        )
        assertEquals(
            "1024-QAM 1 layer DL / 12 bits/symbol 1 layer UL",
            throughputModulationLabel(
                dlModulationOrder = 10,
                ulModulationOrder = 12,
                dlLayers = 1,
                ulLayers = 0
            )
        )
    }

    private fun band(
        gen: Int,
        value: Int,
        status: String = "En service",
        physDetails: List<String> = emptyList()
    ): FreqBand {
        return FreqBand(
            rawFreq = "${gen}G $value : $value MHz",
            status = status,
            date = "2026-05-07",
            physDetails = physDetails,
            gen = gen,
            value = value
        )
    }
}
