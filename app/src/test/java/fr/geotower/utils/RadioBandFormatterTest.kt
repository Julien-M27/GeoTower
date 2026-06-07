package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioBandFormatterTest {
    @Test
    fun mapsMobileRadioBandCodes() {
        assertEquals("N28", radioBandCode(5, 700))
        assertEquals("N75", radioBandCode(5, 1400))
        assertEquals("N78", radioBandCode(5, 3500))
        assertEquals("N77", radioBandCode(5, 4200))
        assertEquals("B20", radioBandCode(4, 800))
        assertEquals("B1", radioBandCode(3, 2100))
        assertEquals("DCS 1800", radioBandCode(2, 1800))
    }

    @Test
    fun formatsExperimentalFrequencyLabels() {
        assertEquals("1400 MHz (exp)", radioFrequencyLabel(1400))
        assertEquals("4200 MHz (exp)", radioFrequencyLabel(4200))
        assertEquals("26 GHz (exp)", radioFrequencyLabel(26000))
        assertEquals("700 MHz", radioFrequencyLabel(700))
    }

    @Test
    fun returnsNullForUnknownBandCodes() {
        assertNull(radioBandCode(5, 1234))
        assertNull(radioBandCode(0, 700))
    }
}
