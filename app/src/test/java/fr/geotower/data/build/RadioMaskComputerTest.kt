package fr.geotower.data.build

import fr.geotower.data.models.RadioFilterMasks
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioMaskComputerTest {

    private fun masksFor(system: String, start: Double?, end: Double?): StationMasks {
        val masks = StationMasks()
        RadioMaskComputer.updateMasksFromSystemAndBand(masks, system, start, end)
        return masks
    }

    @Test
    fun generationSetsTechBits() {
        val masks = StationMasks()
        RadioMaskComputer.updateMasksFromGeneration(masks, "2G/3G")
        assertEquals(RadioFilterMasks.TECH_2G or RadioFilterMasks.TECH_3G, masks.techMask)
        assertEquals(0, masks.bandMask)

        RadioMaskComputer.updateMasksFromGeneration(masks, "5G")
        assertEquals(
            RadioFilterMasks.TECH_2G or RadioFilterMasks.TECH_3G or RadioFilterMasks.TECH_5G,
            masks.techMask
        )
    }

    @Test
    fun gsm900SetsTech2gAndBand2g900() {
        val masks = masksFor("GSM 900", 890.0, 915.0)
        assertEquals(RadioFilterMasks.TECH_2G, masks.techMask)
        assertEquals(RadioFilterMasks.BAND_2G_900, masks.bandMask)
    }

    @Test
    fun lte800SetsTech4gAndBand4g800() {
        val masks = masksFor("LTE 800", 796.0, 806.0)
        assertEquals(RadioFilterMasks.TECH_4G, masks.techMask)
        assertEquals(RadioFilterMasks.BAND_4G_800, masks.bandMask)
    }

    @Test
    fun nr3500SetsTech5gAndBand5g3500() {
        val masks = masksFor("NR 3500", 3600.0, 3700.0)
        assertEquals(RadioFilterMasks.TECH_5G, masks.techMask)
        assertEquals(RadioFilterMasks.BAND_5G_3500, masks.bandMask)
    }

    @Test
    fun fhSystemSetsFhTechAndBandRegardlessOfFrequency() {
        val masks = masksFor("FH", null, null)
        assertEquals(RadioFilterMasks.TECH_FH, masks.techMask)
        assertEquals(RadioFilterMasks.BAND_FH, masks.bandMask)
    }

    @Test
    fun lteWideBlockCanSetMultipleBands() {
        // Un bloc large 700-960 MHz recoupe 4G 700 / 800 / 900.
        val masks = masksFor("LTE", 700.0, 960.0)
        assertEquals(RadioFilterMasks.TECH_4G, masks.techMask)
        assertEquals(
            RadioFilterMasks.BAND_4G_700 or RadioFilterMasks.BAND_4G_800 or RadioFilterMasks.BAND_4G_900,
            masks.bandMask
        )
    }

    @Test
    fun unknownSystemOutOfBandSetsNothing() {
        val masks = masksFor("GSM", 600.0, 650.0)
        assertEquals(RadioFilterMasks.TECH_2G, masks.techMask)
        assertEquals(0, masks.bandMask)
    }
}
