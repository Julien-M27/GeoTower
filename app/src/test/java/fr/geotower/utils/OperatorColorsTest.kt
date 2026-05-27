package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorColorsTest {
    @Test
    fun searchLabelsFor_resolvesOverseasAliasesFromDefaultLabel() {
        val labels = OperatorColors.searchLabelsFor("Outremer Telecom")

        assertTrue(labels.contains("OUTREMER TELECOM"))
        assertTrue(labels.contains("SFR CARAIBE"))
        assertTrue(labels.contains(OperatorColors.OUTREMER_TELECOM_KEY))
    }

    @Test
    fun searchLabelsFor_returnsFallbackForUnknownOperator() {
        assertEquals(listOf("Unknown Mobile"), OperatorColors.searchLabelsFor(" Unknown Mobile "))
    }
}
