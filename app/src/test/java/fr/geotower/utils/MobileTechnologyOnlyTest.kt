package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioFilterMasks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileTechnologyOnlyTest {

    @Test
    fun selectedTechnologyMatchesOnlySitesWithThatSingleMobileTechnology() {
        val fourGOnly = siteWith(RadioFilterMasks.TECH_4G)
        val fourGAndFiveG = siteWith(RadioFilterMasks.TECH_4G or RadioFilterMasks.TECH_5G)

        assertTrue(MobileTechnologyOnly.FOUR_G.matches(fourGOnly))
        assertFalse(MobileTechnologyOnly.FOUR_G.matches(fourGAndFiveG))
        assertFalse(MobileTechnologyOnly.FIVE_G.matches(fourGOnly))
    }

    @Test
    fun fhDoesNotPreventAMobileTechnologyFromBeingOnly() {
        val fourGWithFh = siteWith(RadioFilterMasks.TECH_4G or RadioFilterMasks.TECH_FH)

        assertTrue(MobileTechnologyOnly.FOUR_G.matches(fourGWithFh))
    }

    @Test
    fun noSelectionMatchesEverySite() {
        assertTrue(MobileTechnologyOnly.NONE.matches(siteWith(0)))
        assertTrue(MobileTechnologyOnly.NONE.matches(siteWith(RadioFilterMasks.TECH_2G or RadioFilterMasks.TECH_5G)))
    }

    private fun siteWith(techMask: Int) = LocalisationEntity(
        idAnfr = "TEST",
        operateur = "Orange",
        latitude = 0.0,
        longitude = 0.0,
        azimuts = null,
        codeInsee = null,
        azimutsFh = null,
        techMask = techMask,
        bandMask = 0
    )
}
