package fr.geotower.ui.screens.emitters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyReferenceSearchTest {

    @Test
    fun blankQueryMatchesAllBands() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        assertTrue(bands.all { it.matchesQuery("") })
        assertTrue(bands.all { it.matchesQuery("   ") })
    }

    @Test
    fun matchesByBandName() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        val b28Matches = bands.filter { it.matchesQuery("B28") }
        assertEquals(1, b28Matches.size)
        assertEquals("B28 · 700 MHz", b28Matches.first().name)

        val n78Matches = bands.filter { it.matchesQuery("n78") }
        assertEquals(1, n78Matches.size)
        assertEquals("n78 · 3500 MHz", n78Matches.first().name)
    }

    @Test
    fun matchesByBandNameWithSpaces() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        val matches = bands.filter { it.matchesQuery("b 28") }
        assertEquals(1, matches.size)
        assertEquals("B28 · 700 MHz", matches.first().name)
    }

    @Test
    fun matchesByFrequencyNumber() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        val matches700 = bands.filter { it.matchesQuery("700") }
        // En France: B28 (4G) et n28 (5G)
        assertEquals(2, matches700.size)
        assertTrue(matches700.any { it.name == "B28 · 700 MHz" })
        assertTrue(matches700.any { it.name == "n28 · 700 MHz" })
    }

    @Test
    fun matchesFrequencyInGhz() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        // n78 · 3500 MHz should match "3.5" or "3.5 GHz" or "3,5"
        val matchesDot = bands.filter { it.matchesQuery("3.5") }
        assertTrue(matchesDot.any { it.name == "n78 · 3500 MHz" })

        val matchesComma = bands.filter { it.matchesQuery("3,5 GHz") }
        assertTrue(matchesComma.any { it.name == "n78 · 3500 MHz" })
    }

    @Test
    fun matchesByTechnology() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        val matches5G = bands.filter { it.matchesQuery("5G") }
        assertTrue(matches5G.all { it.technology == FrequencyReferenceTechnology.NR_5G })
        assertEquals(4, matches5G.size)
    }

    @Test
    fun matchesByDuplexMode() {
        val bands = FrequencyReferenceCatalog.franceMetropolitan
        val matchesTdd = bands.filter { it.matchesQuery("TDD") }
        assertEquals(1, matchesTdd.size)
        assertEquals("n78 · 3500 MHz", matchesTdd.first().name)
    }

    @Test
    fun noMatchForUnknownQuery() {
        val bands = FrequencyReferenceCatalog.international
        assertFalse(bands.any { it.matchesQuery("xyznonexistent") })
    }
}
