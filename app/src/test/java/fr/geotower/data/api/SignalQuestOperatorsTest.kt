package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalQuestOperatorsTest {
    @Test
    fun operatorParamFor_supportsMetroOperators() {
        assertEquals("ORANGE", SignalQuestOperators.operatorParamFor("Orange France"))
        assertEquals("SFR", SignalQuestOperators.operatorParamFor("Societe Francaise du Radiotelephone"))
        assertEquals("BOUYGUES", SignalQuestOperators.operatorParamFor("Bouygues Telecom"))
        assertEquals("FREE", SignalQuestOperators.operatorParamFor("Free Mobile"))
    }

    @Test
    fun operatorParamFor_mapsSupportedOverseasOperatorsToParentOperator() {
        assertEquals("FREE", SignalQuestOperators.operatorParamFor("Free Caraibe"))
        assertEquals("FREE", SignalQuestOperators.operatorParamFor("Telco OI"))
        assertEquals("SFR", SignalQuestOperators.operatorParamFor("SRR"))
        assertEquals("SFR", SignalQuestOperators.operatorParamFor("SFR Caraibe"))
        assertEquals("ORANGE", SignalQuestOperators.operatorParamFor("Orange Caraibe"))
    }

    @Test
    fun operatorParamFor_rejectsUnsupportedOperators() {
        assertNull(SignalQuestOperators.operatorParamFor("Digicel"))
        assertNull(SignalQuestOperators.operatorParamFor("Inconnu"))
        assertNull(SignalQuestOperators.operatorParamFor(null))
    }

    @Test
    fun speedtestPlmnFor_mapsMetroOperatorsToPlmn() {
        assertEquals(SignalQuestPlmnFilter(mcc = 208, mnc = 1), SignalQuestOperators.speedtestPlmnFor("Orange France"))
        assertEquals(SignalQuestPlmnFilter(mcc = 208, mnc = 10), SignalQuestOperators.speedtestPlmnFor("Societe Francaise du Radiotelephone"))
        assertEquals(SignalQuestPlmnFilter(mcc = 208, mnc = 20), SignalQuestOperators.speedtestPlmnFor("Bouygues Telecom"))
        assertEquals(SignalQuestPlmnFilter(mcc = 208, mnc = 15), SignalQuestOperators.speedtestPlmnFor("Free Mobile"))
    }

    @Test
    fun speedtestPlmnFor_rejectsUnsupportedOperators() {
        assertNull(SignalQuestOperators.speedtestPlmnFor("Digicel"))
        assertNull(SignalQuestOperators.speedtestPlmnFor("Free Caraibe"))
        assertNull(SignalQuestOperators.speedtestPlmnFor("SRR"))
        assertNull(SignalQuestOperators.speedtestPlmnFor("Inconnu"))
        assertNull(SignalQuestOperators.speedtestPlmnFor(null))
    }

    @Test
    fun supports_matchesOperatorParamAvailability() {
        assertTrue(SignalQuestOperators.supports("SFR"))
        assertTrue(SignalQuestOperators.supports("Orange"))
        assertFalse(SignalQuestOperators.supports("Dauphin Telecom"))
    }
}
