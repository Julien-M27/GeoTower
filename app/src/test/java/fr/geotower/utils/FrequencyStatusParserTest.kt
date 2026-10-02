package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.graphics.Color

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

    @Test
    fun globalAndSectorMarkersShareFrequencyStatusColors() {
        val active = FreqBand("LTE 800", "En service", "", emptyList(), 4, 800, activeAzimuths = setOf(120))
        val inactive = FreqBand("LTE 800", "En service", "", emptyList(), 4, 800, activeAzimuths = setOf(240))
        assertEquals(FrequencyStatusPalette.InService, active.sectorMarkerColor(120))
        assertEquals(null, inactive.sectorMarkerColor(120))
        assertEquals(Color(0xFF2196F3), FrequencyStatusPalette.color(FrequencyStatusType.TechnicallyOperational))
        assertEquals(Color(0xFFFFA000), FrequencyStatusPalette.color(FrequencyStatusType.Approved))
        assertEquals(FrequencyStatusPalette.Unknown, FrequencyStatusPalette.color(FrequencyStatusType.Unknown))
    }
}
