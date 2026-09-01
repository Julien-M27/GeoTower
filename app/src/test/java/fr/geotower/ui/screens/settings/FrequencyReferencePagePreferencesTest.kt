package fr.geotower.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyReferencePagePreferencesTest {
    @Test
    fun technologyOrderFiltersUnknownsAndAppendsMissingTechnologies() {
        assertEquals(
            listOf("5G", "2G", "3G", "4G"),
            FrequencyReferencePagePreferences.normalizeTechnologyOrder(
                listOf("5g", "unknown", "2G", "5G")
            )
        )
    }

    @Test
    fun defaultTechnologyOrderContainsAllReferenceTechnologies() {
        assertEquals(
            listOf("2G", "3G", "4G", "5G"),
            FrequencyReferencePagePreferences.normalizeTechnologyOrder(emptyList())
        )
    }
}
