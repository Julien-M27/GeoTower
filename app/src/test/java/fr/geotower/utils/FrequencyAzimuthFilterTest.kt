package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrequencyAzimuthFilterTest {
    @Test
    fun selected3500KeepsOnlyPanelsServing3500() {
        val details = sectorDetails()

        val azimuths = filteredAzimuthsForFrequencySelection(
            detailsFrequences = details,
            filter = selection(f5G3500 = true)
        )

        assertEquals("120,240", azimuths)
    }

    @Test
    fun selected3500And1800ReturnsUnionOfMatchingPanels() {
        val details = sectorDetails()

        val azimuths = filteredAzimuthsForFrequencySelection(
            detailsFrequences = details,
            filter = selection(f4G1800 = true, f5G3500 = true)
        )

        assertEquals("0,120,240", azimuths)
    }

    @Test
    fun fullyEnabledFilterKeepsMapFallbackAzimuths() {
        val azimuths = filteredAzimuthsForFrequencySelection(
            detailsFrequences = sectorDetails(),
            filter = fullyEnabled()
        )

        assertNull(azimuths)
    }

    @Test
    fun selectedExperimentalBandsAreRecognized() {
        val details = """
            5G 1400 MHz (exp) : 1472-1492 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 40 deg (24m)
            5G NR 4200 : 3800,1-4200 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 80 deg (24m)
            5G NR 26 GHz Expe : 26,5-26,9 GHz | Techniquement operationnel | 2026-05-07 | Panneau : 200 deg (24m)
        """.trimIndent()

        val azimuths = filteredAzimuthsForFrequencySelection(
            detailsFrequences = details,
            filter = selection(f5G1400 = true, f5G4200 = true, f5G26000 = true)
        )

        assertEquals("40,80,200", azimuths)
    }

    @Test
    fun selectedBandWithKnownLineButNoPanelAzimuthHidesSectors() {
        val details = "5G NR 3500 : 3710-3800 MHz | Techniquement operationnel | 2026-05-07 | Azimut non specifie"

        val azimuths = filteredAzimuthsForFrequencySelection(
            detailsFrequences = details,
            filter = selection(f5G3500 = true)
        )

        assertEquals("", azimuths)
    }

    private fun sectorDetails(): String {
        return """
            5G NR 3500 : 3710-3800 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 120 deg (24m)
            5G NR 3500 : 3710-3800 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 240 deg (24m)
            LTE 1800 (4G) : 1835-1850 MHz | En service | 2026-05-07 | Panneau : 0 deg (24m)
            LTE 1800 (4G) : 1835-1850 MHz | En service | 2026-05-07 | Panneau : 120 deg (24m)
            LTE 1800 (4G) : 1835-1850 MHz | En service | 2026-05-07 | Panneau : 240 deg (24m)
        """.trimIndent()
    }

    private fun selection(
        f4G1800: Boolean = false,
        f5G1400: Boolean = false,
        f5G3500: Boolean = false,
        f5G4200: Boolean = false,
        f5G26000: Boolean = false
    ): FrequencyFilterSelection {
        return FrequencyFilterSelection(
            show2G = false,
            show3G = false,
            show4G = f4G1800,
            show5G = f5G1400 || f5G3500 || f5G4200 || f5G26000,
            showFh = false,
            f2G900 = false,
            f2G1800 = false,
            f3G900 = false,
            f3G2100 = false,
            f4G700 = false,
            f4G800 = false,
            f4G900 = false,
            f4G1800 = f4G1800,
            f4G2100 = false,
            f4G2600 = false,
            f5G700 = false,
            f5G1400 = f5G1400,
            f5G2100 = false,
            f5G3500 = f5G3500,
            f5G4200 = f5G4200,
            f5G26000 = f5G26000
        )
    }

    private fun fullyEnabled(): FrequencyFilterSelection {
        return FrequencyFilterSelection(
            show2G = true,
            show3G = true,
            show4G = true,
            show5G = true,
            showFh = true,
            f2G900 = true,
            f2G1800 = true,
            f3G900 = true,
            f3G2100 = true,
            f4G700 = true,
            f4G800 = true,
            f4G900 = true,
            f4G1800 = true,
            f4G2100 = true,
            f4G2600 = true,
            f5G700 = true,
            f5G1400 = true,
            f5G2100 = true,
            f5G3500 = true,
            f5G4200 = true,
            f5G26000 = true
        )
    }
}
