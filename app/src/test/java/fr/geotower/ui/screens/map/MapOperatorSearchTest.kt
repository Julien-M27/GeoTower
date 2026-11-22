package fr.geotower.ui.screens.map

import fr.geotower.utils.OperatorColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOperatorSearchTest {

    @Test
    fun `extractExplicitOperatorQuery recognizes op prefixes`() {
        assertEquals("", extractExplicitOperatorQuery("op:"))
        assertEquals("", extractExplicitOperatorQuery("o: "))
        assertEquals("daup", extractExplicitOperatorQuery("operateur:daup"))
        assertEquals("opt", extractExplicitOperatorQuery("operator: opt"))
        assertNull(extractExplicitOperatorQuery("orange"))
        assertNull(extractExplicitOperatorQuery("ville:Paris"))
    }

    @Test
    fun `suggestOperatorsForExplicitQuery lists all operators when query is empty or tous`() {
        assertEquals(OperatorColors.all, suggestOperatorsForExplicitQuery("op:"))
        assertEquals(OperatorColors.all, suggestOperatorsForExplicitQuery("o:"))
        assertEquals(OperatorColors.all, suggestOperatorsForExplicitQuery("op:tous"))
        assertEquals(OperatorColors.all, suggestOperatorsForExplicitQuery("op:all"))
        assertEquals(OperatorColors.all, suggestOperatorsForExplicitQuery("op:*"))
    }

    @Test
    fun `suggestOperatorsForExplicitQuery finds overseas and partial operators`() {
        assertEquals(
            listOf(OperatorColors.DAUPHIN_TELECOM_KEY),
            suggestOperatorsForExplicitQuery("op:daup").map { it.key }
        )
        assertEquals(
            listOf(OperatorColors.OPT_NC_KEY),
            suggestOperatorsForExplicitQuery("op:opt").map { it.key }
        )
        val freeKeys = suggestOperatorsForExplicitQuery("op:free").map { it.key }
        assertTrue(OperatorColors.FREE_KEY in freeKeys)
        assertTrue(OperatorColors.FREE_CARAIBE_KEY in freeKeys)
        assertEquals(
            listOf(OperatorColors.ONATI_KEY, OperatorColors.VITI_KEY),
            suggestOperatorsForExplicitQuery("op:vini").map { it.key }
        )
        val caraibeKeys = suggestOperatorsForExplicitQuery("op:caraibe").map { it.key }
        assertTrue(OperatorColors.FREE_CARAIBE_KEY in caraibeKeys)
        assertTrue(OperatorColors.OUTREMER_TELECOM_KEY in caraibeKeys)
        assertTrue(OperatorColors.UTS_CARAIBE_KEY in caraibeKeys)
    }

    @Test
    fun `parseOperatorSearchKeys resolves exact, combined, partial and all operators`() {
        assertEquals(listOf(OperatorColors.FREE_KEY), parseOperatorSearchKeys("op:free"))
        assertEquals(
            listOf(OperatorColors.SFR_KEY, OperatorColors.FREE_KEY),
            parseOperatorSearchKeys("op:SFR + Free")
        )
        assertEquals(listOf(OperatorColors.OPT_NC_KEY), parseOperatorSearchKeys("op:opt"))
        assertEquals(listOf(OperatorColors.DAUPHIN_TELECOM_KEY), parseOperatorSearchKeys("op:daup"))
        assertEquals(OperatorColors.orderedKeys, parseOperatorSearchKeys("op:tous"))
        assertEquals(OperatorColors.orderedKeys, parseOperatorSearchKeys("op:*"))
        assertEquals(emptyList<String>(), parseOperatorSearchKeys("op:"))
        assertEquals(emptyList<String>(), parseOperatorSearchKeys("orange"))
    }

    @Test
    fun `matchOperatorSuggestions keeps exact and prefix behavior without op prefix`() {
        assertEquals(
            listOf(OperatorColors.ORANGE_KEY),
            matchOperatorSuggestions("orange", exactOnly = true).map { it.key }
        )
        assertEquals(
            emptyList<String>(),
            matchOperatorSuggestions("oran", exactOnly = true).map { it.key }
        )
        assertEquals(
            listOf(OperatorColors.ORANGE_KEY, OperatorColors.VITI_KEY),
            matchOperatorSuggestions("ora", exactOnly = false).map { it.key }
        )
    }
}
