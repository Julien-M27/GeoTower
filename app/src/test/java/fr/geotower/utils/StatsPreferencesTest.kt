package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPreferencesTest {
    @Test
    fun defaultFrequencyOrder5g_putsExperimentalBandsLast() {
        assertEquals(
            listOf("3500", "2100", "700", "1400", "4200", "26000"),
            StatsPreferences.defaultFrequencyOrder("5G")
        )
    }

    @Test
    fun normalizeFrequencyOrder5g_migratesPreviousDefaultOrder() {
        val previousDefaultOrder = listOf("26000", "4200", "3500", "2100", "1400", "700")

        assertEquals(
            StatsPreferences.defaultFrequencyOrder("5G"),
            StatsPreferences.normalizeFrequencyOrder("5G", previousDefaultOrder)
        )
    }
}
