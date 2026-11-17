package fr.geotower.ui.screens.emitters

import fr.geotower.utils.OperatorColors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearEmittersOperatorSearchTest {

    @Test
    fun `nearbySiteMatchesOperatorQuery matches all operator keys aliases and raw names`() {
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.ORANGE_KEY), ""))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.BOUYGUES_KEY), "bytel"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.BOUYGUES_KEY), "Bouygues Telecom"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.OPT_NC_KEY), "opt"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.OPT_NC_KEY), "OPT Nouvelle-Calédonie"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.OUTREMER_TELECOM_KEY), "SFR Caraibe"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.VITI_KEY), "ora"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.VITI_KEY), "vini"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.ONATI_KEY), "vini"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.SFR_KEY), "SFR + Free"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.DAUPHIN_TELECOM_KEY), "tous"))
        assertTrue(nearbySiteMatchesOperatorQuery(listOf("TDF"), "tdf"))
        assertFalse(nearbySiteMatchesOperatorQuery(listOf(OperatorColors.ORANGE_KEY), "free"))
    }
}
