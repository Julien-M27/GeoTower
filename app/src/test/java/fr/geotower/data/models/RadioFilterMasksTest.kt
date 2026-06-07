package fr.geotower.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioFilterMasksTest {
    @Test
    fun bandMaskConvertsToLegacyFilterTokens() {
        val mask = RadioFilterMasks.BAND_4G_700 or
            RadioFilterMasks.BAND_4G_2600 or
            RadioFilterMasks.BAND_5G_1400 or
            RadioFilterMasks.BAND_5G_3500 or
            RadioFilterMasks.BAND_5G_4200

        assertEquals("4G700 4G2600 5G1400 5G3500 5G4200", RadioFilterMasks.bandMaskToFilterString(mask))
    }

    @Test
    fun techMaskConvertsToTechnologySummary() {
        val mask = RadioFilterMasks.TECH_2G or
            RadioFilterMasks.TECH_4G or
            RadioFilterMasks.TECH_FH

        assertEquals("2G, 4G, FH", RadioFilterMasks.techMaskToString(mask))
    }
}
