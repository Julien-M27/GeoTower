package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSourceDownloaderTest {

    @Test
    fun parsesCommunesToUppercaseNames() {
        val json = """[{"nom":"Paris","code":"75056"},{"nom":"Toulouse","code":"31555"}]"""
        val communes = RawSourceDownloader.parseCommunesJson(json)
        assertEquals("PARIS", communes["75056"])
        assertEquals("TOULOUSE", communes["31555"])
    }

    @Test
    fun parsesCommunesReturnsEmptyOnBadJson() {
        assertTrue(RawSourceDownloader.parseCommunesJson("nope").isEmpty())
    }

    @Test
    fun parsesArcepSitesWithAliasesNidtAndZb() {
        val rows = listOf(
            row("sta_nm_anfr" to "1", "nom_op" to "Orange", "nidt" to "NIDT1", "site_zb" to "1"),
            row("sta_nm_anfr" to "2", "nom_op" to "SFR", "nidt" to "NIDT2", "site_zb" to "0"),
        )
        val meta = RawSourceDownloader.parseArcepSites(rows)

        assertEquals(ArcepSiteMeta("NIDT1", 1), meta["0000000001" to "ORANGE"])
        assertEquals(ArcepSiteMeta("NIDT2", 0), meta["0000000002" to "SFR"])
    }

    @Test
    fun arcepMergesRowsKeepingNidtAndOrOfZb() {
        val rows = listOf(
            row("sta_nm_anfr" to "1", "nom_op" to "Orange", "nidt" to "NIDT1", "site_zb" to "0"),
            row("sta_nm_anfr" to "1", "nom_op" to "Orange", "nidt" to "", "site_dcc" to "oui"),
        )
        val meta = RawSourceDownloader.parseArcepSites(rows)

        // nidt conserve du premier, is_zb passe a 1 via site_dcc de la 2e ligne.
        assertEquals(ArcepSiteMeta("NIDT1", 1), meta["0000000001" to "ORANGE"])
    }

    @Test
    fun arcepSkipsRowsWithoutStationOrOperator() {
        val meta = RawSourceDownloader.parseArcepSites(
            listOf(row("nidt" to "X", "site_zb" to "1")),
        )
        assertNull(meta["" to ""])
        assertTrue(meta.isEmpty())
    }

    private fun row(vararg pairs: Pair<String, String?>): AnfrCsvRow = AnfrCsvRow.of(mapOf(*pairs))
}
