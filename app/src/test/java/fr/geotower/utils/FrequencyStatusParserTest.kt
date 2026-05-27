package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyStatusParserTest {
    @Test
    fun classifiesKnownAnfrFrequencyStatuses() {
        assertEquals(FrequencyStatusType.InService, classifyFrequencyStatus("En service"))
        assertEquals(FrequencyStatusType.TechnicallyOperational, classifyFrequencyStatus("Techniquement opérationnel"))
        assertEquals(FrequencyStatusType.Approved, classifyFrequencyStatus("Projet approuvé"))
    }

    @Test
    fun unknownStatusFallsBackToUnknown() {
        assertEquals(FrequencyStatusType.Unknown, classifyFrequencyStatus(""))
        assertEquals(FrequencyStatusType.Unknown, classifyFrequencyStatus("Sans statut"))
    }
}
