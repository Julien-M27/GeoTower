package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SpectrumDisplayFormatterTest {
    @Test
    fun spectrumWidthsKeepDecimalCommasWithoutRounding() {
        val details = formatSpectrumDisplayDetails(
            "1935,3-1950,1 MHz\n100,005-100,010 MHz"
        )

        assertEquals(
            "1935,3-1950,1 MHz [14,8 MHz]\n100,005-100,010 MHz [0,005 MHz]",
            details.detailedFrequencies()
        )
        assertEquals("14,805", details.totalBandwidth)
        assertEquals("MHz", details.totalUnit)
    }

    @Test
    fun spectrumAndBandwidthAreDisplayedIndependently() {
        val details = formatSpectrumDisplayDetails("708-718 MHz\n763-773 MHz")

        assertEquals(
            "708-718 MHz\n763-773 MHz",
            details.detailedFrequencies(withSpectrum = true, withBandwidth = false)
        )
        assertEquals(
            "10 MHz\n10 MHz",
            details.detailedFrequencies(withSpectrum = false, withBandwidth = true)
        )
        assertEquals(
            "",
            details.detailedFrequencies(withSpectrum = false, withBandwidth = false)
        )
    }

    @Test
    fun spacedRenderingKeepsDecimalCommasForTheAntennaTable() {
        val nb = '\u00A0' // insécable : la cellule ne coupe qu'entre la plage et ses crochets
        val details = formatSpectrumDisplayDetails("1935,3-1950,1 MHz")

        assertEquals(
            "1935,3 - 1950,1 MHz [14,8${nb}MHz]",
            details.detailedFrequencies(spaced = true)
        )
        assertEquals(
            "1935,3 - 1950,1 MHz",
            details.detailedFrequencies(withBandwidth = false, spaced = true)
        )
    }

    @Test
    fun unparsableFrequenciesFallBackToRawTextOnlyForSpectrum() {
        val details = formatSpectrumDisplayDetails("Non spécifié")

        assertEquals("Non spécifié", details.detailedFrequencies())
        assertEquals(
            "",
            details.detailedFrequencies(withSpectrum = false, withBandwidth = true)
        )
    }
}
