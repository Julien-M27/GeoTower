package fr.geotower.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioFilterMasksTest {
    @Test
    fun bandMaskConvertsToLegacyFilterTokens() {
        val mask = RadioFilterMasks.BAND_4G_700 or
            RadioFilterMasks.BAND_4G_2600 or
            RadioFilterMasks.BAND_5G_3500

        assertEquals("4G700 4G2600 5G3500", RadioFilterMasks.bandMaskToFilterString(mask))
    }

    @Test
    fun techMaskConvertsToTechnologySummary() {
        val mask = RadioFilterMasks.TECH_2G or
            RadioFilterMasks.TECH_4G or
            RadioFilterMasks.TECH_FH

        assertEquals("2G, 4G, FH", RadioFilterMasks.techMaskToString(mask))
    }
}
