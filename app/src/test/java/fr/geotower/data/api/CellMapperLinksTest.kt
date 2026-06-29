package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellMapperLinksTest {
    @Test
    fun networkFor_mapsMetroOperatorsToCellMapperPlmn() {
        assertEquals(CellMapperNetwork(mcc = 208, mnc = 1), CellMapperLinks.networkFor("Orange"))
        assertEquals(CellMapperNetwork(mcc = 208, mnc = 10), CellMapperLinks.networkFor("SFR"))
        assertEquals(CellMapperNetwork(mcc = 208, mnc = 20), CellMapperLinks.networkFor("Bouygues Telecom"))
        assertEquals(CellMapperNetwork(mcc = 208, mnc = 15), CellMapperLinks.networkFor("Free Mobile"))
    }

    @Test
    fun networkFor_ignoresUnsupportedOperators() {
        assertNull(CellMapperLinks.networkFor("Digicel"))
    }

    @Test
    fun preferredMapType_prefersLteWhenAvailable() {
        assertEquals("LTE", CellMapperLinks.preferredMapType("5G 3500\n4G 1800"))
        assertEquals("NR", CellMapperLinks.preferredMapType("5G NR 3500"))
        assertEquals("UMTS", CellMapperLinks.preferredMapType("3G UMTS 2100"))
        assertEquals("GSM", CellMapperLinks.preferredMapType("2G GSM 900"))
        assertEquals("LTE", CellMapperLinks.preferredMapType(null))
    }
}
