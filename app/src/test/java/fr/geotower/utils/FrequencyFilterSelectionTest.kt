package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioFilterMasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyFilterSelectionTest {
    @Test
    fun selectedBandMatchesOnlyAntennasWithThatBand() {
        val filter = only2G1800()
        val orange = antenna(RadioFilterMasks.BAND_2G_1800)
        val free = antenna(RadioFilterMasks.BAND_4G_700 or RadioFilterMasks.BAND_5G_700)

        assertTrue(filter.matchesAntenna(orange))
        assertFalse(filter.matchesAntenna(free))
    }

    @Test
    fun selectedBandMutesOtherSiteDetailBands() {
        val filter = only2G1800()

        assertTrue(filter.matchesBand(gen = 2, value = 1800))
        assertFalse(filter.matchesBand(gen = 2, value = 900))
        assertFalse(filter.matchesBand(gen = 4, value = 700))
        assertFalse(filter.matchesBand(gen = 0, value = 0))
    }

    @Test
    fun microwaveLinksMatchOnlyWhenMobileFrequenciesAreNotRestricted() {
        val fh = LocalisationEntity(
            idAnfr = "456",
            operateur = "Orange",
            latitude = 48.0,
            longitude = 2.0,
            azimuts = null,
            codeInsee = null,
            azimutsFh = "120",
            bandMask = RadioFilterMasks.BAND_FH
        )

        assertTrue(fullyEnabled().matchesAntenna(fh))
        assertFalse(only2G1800().matchesAntenna(fh))
    }

    @Test
    fun unknownBandsRemainVisibleOnlyWhenFilterIsFullyEnabled() {
        val unknown = antenna(bandMask = 0)

        assertTrue(fullyEnabled().matchesAntenna(unknown))
        assertFalse(only2G1800().matchesAntenna(unknown))
    }

    @Test
    fun fullyEnabledFilterDoesNotRequestDetailBackedMapEnrichment() {
        assertEquals(0, fullyEnabled().detailBackedBandMaskForEnrichment())
    }

    @Test
    fun selectedExperimental5GBandsRequestDetailBackedMapEnrichment() {
        val filter = only5G26000()

        assertEquals(RadioFilterMasks.BAND_5G_26000, filter.detailBackedBandMaskForEnrichment())
    }

    @Test
    fun selected5G4200RequestsDetailBackedMapEnrichment() {
        val filter = only5G4200()

        assertTrue(filter.matchesBand(gen = 5, value = 4200))
        assertEquals(RadioFilterMasks.BAND_5G_4200, filter.detailBackedBandMaskForEnrichment())
    }

    private fun only2G1800(): FrequencyFilterSelection {
        return FrequencyFilterSelection(
            show2G = true,
            show3G = true,
            show4G = true,
            show5G = true,
            showFh = false,
            f2G900 = false,
            f2G1800 = true,
            f3G900 = false,
            f3G2100 = false,
            f4G700 = false,
            f4G800 = false,
            f4G900 = false,
            f4G1800 = false,
            f4G2100 = false,
            f4G2600 = false,
            f5G700 = false,
            f5G1400 = false,
            f5G2100 = false,
            f5G3500 = false,
            f5G4200 = false,
            f5G26000 = false
        )
    }

    private fun fullyEnabled(): FrequencyFilterSelection {
        return FrequencyFilterSelection(
            show2G = true,
            show3G = true,
            show4G = true,
            show5G = true,
            showFh = true,
            f2G900 = true,
            f2G1800 = true,
            f3G900 = true,
            f3G2100 = true,
            f4G700 = true,
            f4G800 = true,
            f4G900 = true,
            f4G1800 = true,
            f4G2100 = true,
            f4G2600 = true,
            f5G700 = true,
            f5G1400 = true,
            f5G2100 = true,
            f5G3500 = true,
            f5G4200 = true,
            f5G26000 = true
        )
    }

    private fun only5G26000(): FrequencyFilterSelection {
        return FrequencyFilterSelection(
            show2G = false,
            show3G = false,
            show4G = false,
            show5G = true,
            showFh = false,
            f2G900 = false,
            f2G1800 = false,
            f3G900 = false,
            f3G2100 = false,
            f4G700 = false,
            f4G800 = false,
            f4G900 = false,
            f4G1800 = false,
            f4G2100 = false,
            f4G2600 = false,
            f5G700 = false,
            f5G1400 = false,
            f5G2100 = false,
            f5G3500 = false,
            f5G4200 = false,
            f5G26000 = true
        )
    }

    private fun only5G4200(): FrequencyFilterSelection {
        return FrequencyFilterSelection(
            show2G = false,
            show3G = false,
            show4G = false,
            show5G = true,
            showFh = false,
            f2G900 = false,
            f2G1800 = false,
            f3G900 = false,
            f3G2100 = false,
            f4G700 = false,
            f4G800 = false,
            f4G900 = false,
            f4G1800 = false,
            f4G2100 = false,
            f4G2600 = false,
            f5G700 = false,
            f5G1400 = false,
            f5G2100 = false,
            f5G3500 = false,
            f5G4200 = true,
            f5G26000 = false
        )
    }

    private fun antenna(bandMask: Int): LocalisationEntity {
        return LocalisationEntity(
            idAnfr = "123",
            operateur = "Orange",
            latitude = 48.0,
            longitude = 2.0,
            azimuts = null,
            codeInsee = null,
            azimutsFh = null,
            bandMask = bandMask
        )
    }
}
