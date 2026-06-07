package fr.geotower.data

import fr.geotower.data.models.RadioFilterMasks
import org.junit.Assert.assertTrue
import org.junit.Test

class AnfrRepositoryFrequencyDetailsTest {
    @Test
    fun frequencyDetailsMaskKeepsExistingLteBands() {
        val details = "LTE 1800 (4G) : 1835-1850 MHz | En service | 2026-05-07 | Panneau : 120 deg (24m)"

        val mask = radioBandMaskFromFrequencyDetails(details)

        assertTrue((mask and RadioFilterMasks.BAND_4G_1800) != 0)
    }

    @Test
    fun frequencyDetailsMaskRecognizesNr26GhzLabel() {
        val details = "NR 26 GHz (5G) | Techniquement operationnel | 2026-05-07 | Panneau : 120 deg (24m)"

        val mask = radioBandMaskFromFrequencyDetails(details)

        assertTrue((mask and RadioFilterMasks.BAND_5G_26000) != 0)
    }

    @Test
    fun frequencyDetailsMaskRecognizesNr26LabelWithoutGhzUnit() {
        val details = "NR 26 (5G) | Techniquement operationnel | 2026-05-07 | Panneau : 120 deg (24m)"

        val mask = radioBandMaskFromFrequencyDetails(details)

        assertTrue((mask and RadioFilterMasks.BAND_5G_26000) != 0)
    }

    @Test
    fun frequencyDetailsMaskRecognizesNr1400WithGenerationSuffix() {
        val details = "5G 1400 MHz (exp) : 1472-1492 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 120 deg (24m)"

        val mask = radioBandMaskFromFrequencyDetails(details)

        assertTrue((mask and RadioFilterMasks.BAND_5G_1400) != 0)
    }

    @Test
    fun frequencyDetailsMaskRecognizesNr4200WithSystemName() {
        val details = "5G NR 4200 : 3800,1-4200 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 120 deg (24m)"

        val mask = radioBandMaskFromFrequencyDetails(details)

        assertTrue((mask and RadioFilterMasks.BAND_5G_4200) != 0)
    }

    @Test
    fun frequencyDetailsMaskRecognizesNr4200FromRange() {
        val details = "5G NR : 3800,1-4200 MHz | Techniquement operationnel | 2026-05-07 | Panneau : 120 deg (24m)"

        val mask = radioBandMaskFromFrequencyDetails(details)

        assertTrue((mask and RadioFilterMasks.BAND_5G_4200) != 0)
    }
}
