package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Parite des conversions radio avec `dms_to_e6` / `frequency_to_khz` / `format_freq_range` (serveur). */
class RadioParsingTest {

    @Test
    fun dmsToE6ConvertsNorthEastToPositiveMicroDegrees() {
        // 48°51'30"N -> 48 + 51/60 + 30/3600 = 48.858333... -> 48858333
        assertEquals(48_858_333, RadioParsing.dmsToE6("48", "51", "30", "N"))
    }

    @Test
    fun dmsToE6MakesSouthAndWestNegative() {
        assertEquals(-2_350_000, RadioParsing.dmsToE6("2", "21", "0", "W"))
        assertEquals(-43_600_000, RadioParsing.dmsToE6("43", "36", "0", "S"))
    }

    @Test
    fun dmsToE6ReturnsNullWithoutDegrees() {
        assertNull(RadioParsing.dmsToE6("", "10", "0", "N"))
    }

    @Test
    fun dmsToE6HandlesFrenchDecimalComma() {
        // Secondes "30,5" (virgule decimale) doivent etre lues comme 30.5.
        assertEquals(48_858_472, RadioParsing.dmsToE6("48", "51", "30,5", "N"))
    }

    @Test
    fun frequencyToKhzHonoursUnitPrefix() {
        assertEquals(100_000, RadioParsing.frequencyToKhz("100", "M")) // 100 MHz
        assertEquals(2_000_000, RadioParsing.frequencyToKhz("2", "G")) // 2 GHz
        assertEquals(700, RadioParsing.frequencyToKhz("700", "k")) // 700 kHz
        assertEquals(1_000, RadioParsing.frequencyToKhz("1000000", "H")) // 1 000 000 Hz
    }

    @Test
    fun frequencyToKhzDefaultsToMegahertz() {
        assertEquals(93_500, RadioParsing.frequencyToKhz("93.5", "")) // unite vide -> MHz
        assertEquals(93_500, RadioParsing.frequencyToKhz("93,5", null)) // virgule + unite nulle -> MHz
    }

    @Test
    fun frequencyToKhzReturnsNullForEmpty() {
        assertNull(RadioParsing.frequencyToKhz("", "M"))
    }

    @Test
    fun formatFreqRangeAppendsSuffix() {
        assertEquals("791-801 MHz", RadioParsing.formatFreqRange("791", "801", "M"))
        assertEquals("87.5-108 MHz", RadioParsing.formatFreqRange("87.5", "108", "M"))
        assertEquals("10.7-12.75 GHz", RadioParsing.formatFreqRange("10.7", "12.75", "G"))
    }
}
