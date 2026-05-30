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
            details.detailedFrequencies
        )
        assertEquals("14,805", details.totalBandwidth)
        assertEquals("MHz", details.totalUnit)
    }
}
