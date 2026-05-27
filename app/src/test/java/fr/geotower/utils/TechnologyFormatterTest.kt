package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TechnologyFormatterTest {
    @Test
    fun formatsTechnologiesLikeLegacySiteScreenHelper() {
        assertEquals("NR - LTE - GSM", formatTechnologies("gsm/lte-nr", "Inconnu"))
        assertEquals("UMTS - LTE", formatTechnologies(" LTE, UMTS ", "Inconnu"))
    }

    @Test
    fun preservesLegacyNullAndBlankBehavior() {
        assertEquals("Inconnu", formatTechnologies(null, "Inconnu"))
        assertEquals("", formatTechnologies("", "Inconnu"))
    }
}
