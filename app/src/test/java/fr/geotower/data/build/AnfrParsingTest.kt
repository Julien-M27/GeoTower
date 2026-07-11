package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnfrParsingTest {

    @Test
    fun normalizeIdAnfrPadsNumericIds() {
        assertEquals("0000012345", AnfrParsing.normalizeIdAnfr("12345"))
        assertEquals("0000012345", AnfrParsing.normalizeIdAnfr("  12345 "))
        // Deja a 10 chiffres ou plus : inchange (pas de troncature).
        assertEquals("1234567890", AnfrParsing.normalizeIdAnfr("1234567890"))
        assertEquals("12345678901", AnfrParsing.normalizeIdAnfr("12345678901"))
        // Non numerique : inchange.
        assertEquals("ABC123", AnfrParsing.normalizeIdAnfr("ABC123"))
        assertEquals("", AnfrParsing.normalizeIdAnfr(null))
    }

    @Test
    fun intOrNoneMatchesPythonSemantics() {
        assertEquals(15, AnfrParsing.intOrNone("15"))
        assertEquals(12, AnfrParsing.intOrNone("12,9"))
        assertEquals(12, AnfrParsing.intOrNone("12.9"))
        assertNull(AnfrParsing.intOrNone(""))
        assertNull(AnfrParsing.intOrNone("N/A"))
        assertNull(AnfrParsing.intOrNone("n/a"))
        assertNull(AnfrParsing.intOrNone("abc"))
    }

    @Test
    fun floatOrNoneMatchesPythonSemantics() {
        assertEquals(48.85, AnfrParsing.floatOrNone("48,85")!!, 1e-9)
        assertEquals(48.85, AnfrParsing.floatOrNone("48.85")!!, 1e-9)
        assertNull(AnfrParsing.floatOrNone(""))
        assertNull(AnfrParsing.floatOrNone("N/A"))
        assertNull(AnfrParsing.floatOrNone("xyz"))
    }

    @Test
    fun parseCoordinatesReadsLatLon() {
        // Format ANFR reel : les deux nombres sont separes par un espace
        // (point decimal ou virgule decimale, tous deux acceptes).
        val (lat, lon) = AnfrParsing.parseCoordinates("48.8566 2.3522")
        assertEquals(48.8566, lat, 1e-9)
        assertEquals(2.3522, lon, 1e-9)

        val (latFr, lonFr) = AnfrParsing.parseCoordinates("48,8566 2,3522")
        assertEquals(48.8566, latFr, 1e-9)
        assertEquals(2.3522, lonFr, 1e-9)

        // Non exploitable (vide ou un seul nombre) -> (0.0, 0.0).
        assertEquals(0.0 to 0.0, AnfrParsing.parseCoordinates(""))
        assertEquals(0.0 to 0.0, AnfrParsing.parseCoordinates("48.85"))
    }

    @Test
    fun frequencyToMhzHandlesUnits() {
        assertEquals(1800.0, AnfrParsing.frequencyToMhz(1800.0, "MHz")!!, 1e-9)
        assertEquals(3500.0, AnfrParsing.frequencyToMhz(3.5, "GHz")!!, 1e-9)
        assertEquals(0.9, AnfrParsing.frequencyToMhz(900.0, "kHz")!!, 1e-9)
        assertEquals(1800.0, AnfrParsing.frequencyToMhz(1800.0, "")!!, 1e-9) // defaut = M
        assertNull(AnfrParsing.frequencyToMhz(null, "MHz"))
    }

    @Test
    fun formatBandRangeUsesUnitSuffix() {
        assertEquals("791-801 MHz", AnfrParsing.formatBandRange("791", "801", "M"))
        assertEquals("3.4-3.8 GHz", AnfrParsing.formatBandRange("3.4", "3.8", "GHz"))
        assertEquals("900-905 kHz", AnfrParsing.formatBandRange("900", "905", "K"))
    }

    @Test
    fun rangeOverlapsDetectsIntersection() {
        assertTrue(AnfrParsing.rangeOverlaps(890.0, 915.0, 880.0, 960.0))
        assertFalse(AnfrParsing.rangeOverlaps(700.0, 720.0, 880.0, 960.0))
        assertFalse(AnfrParsing.rangeOverlaps(null, 915.0, 880.0, 960.0))
        // Bornes inversees supportees (min/max).
        assertTrue(AnfrParsing.rangeOverlaps(960.0, 880.0, 900.0, 905.0))
    }

    @Test
    fun isActiveStatusMatchesFrenchLabels() {
        assertTrue(AnfrParsing.isActiveStatus("En service"))
        assertTrue(AnfrParsing.isActiveStatus("Techniquement operationnel"))
        assertFalse(AnfrParsing.isActiveStatus("En projet"))
        assertFalse(AnfrParsing.isActiveStatus(null))
    }
}
