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

    /**
     * Les libelles de systeme sont memoises (le scan des masques rencontre les memes des millions
     * de fois) : un libelle deja vu doit produire exactement le meme resultat qu'a la premiere
     * rencontre. Les autres tests n'emploient que des libelles distincts et ne franchissent donc
     * jamais le chemin « cache hit ».
     */
    @Test
    fun repeatedSystemLabelStillAppliesMasks() {
        val first = masksFor("LTE 1800", 1710.0, 1785.0)
        val second = masksFor("LTE 1800", 1710.0, 1785.0)
        assertEquals(RadioFilterMasks.TECH_4G, second.techMask)
        assertEquals(RadioFilterMasks.BAND_4G_1800, second.bandMask)
        assertEquals(first.techMask, second.techMask)
        assertEquals(first.bandMask, second.bandMask)

        // Le cache ne porte que sur la famille deduite du libelle, jamais sur la bande : le meme
        // libelle avec d'autres frequences doit donner une autre bande.
        val other = masksFor("LTE 1800", 2500.0, 2690.0)
        assertEquals(RadioFilterMasks.TECH_4G, other.techMask)
        assertEquals(RadioFilterMasks.BAND_4G_2600, other.bandMask)
    }

    /** Un libelle FH deja memoise doit toujours court-circuiter vers les bits FH. */
    @Test
    fun repeatedFhLabelStillShortCircuits() {
        masksFor("FH 18 GHz", 18000.0, 18500.0)
        val second = masksFor("FH 18 GHz", 18000.0, 18500.0)
        assertEquals(RadioFilterMasks.TECH_FH, second.techMask)
        assertEquals(RadioFilterMasks.BAND_FH, second.bandMask)
    }

    @Test
    fun repeatedGenerationLabelStillAppliesMasks() {
        val first = StationMasks().also { RadioMaskComputer.updateMasksFromGeneration(it, "4G") }
        val second = StationMasks().also { RadioMaskComputer.updateMasksFromGeneration(it, "4G") }
        assertEquals(RadioFilterMasks.TECH_4G, first.techMask)
        assertEquals(RadioFilterMasks.TECH_4G, second.techMask)
    }

    @Test
    fun unknownSystemOutOfBandSetsNothing() {
        val masks = masksFor("GSM", 600.0, 650.0)
        assertEquals(RadioFilterMasks.TECH_2G, masks.techMask)
        assertEquals(0, masks.bandMask)
    }
}
