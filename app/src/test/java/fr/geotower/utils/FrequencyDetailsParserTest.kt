package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lignes de `details_frequences` produites par les deux builders, telles que la fiche site les
 * reparse — en particulier les systemes que l'ANFR annonce dans son observatoire hebdomadaire sans
 * avoir encore publie leurs bandes ni leur antenne dans l'export mensuel.
 */
class FrequencyDetailsParserTest {

    private val txtUnknown = "Inconnu"
    private val txtAzimuthNotSpecified = "Azimut non spécifié"

    private fun parse(details: String) =
        parseAndSortFrequencies(details, txtUnknown, txtAzimuthNotSpecified)

    @Test
    fun readsTechnologyAndFrequencyOfAnAnnouncedOnlySystem() {
        val bands = parse("5G NR 2100 :  | Projet approuve |  | Azimut non specifie")

        assertEquals(1, bands.size)
        assertEquals(5, bands[0].gen)
        assertEquals(2100, bands[0].value)
        assertEquals("Projet approuve", bands[0].status)
    }

    @Test
    fun doesNotTurnTheAzimuthPlaceholderIntoAnAntennaDetail() {
        // GOTCHA : les builders ecrivent le marqueur SANS accent ; seule la forme accentuee etait
        // filtree, si bien que « Azimut non specifie » s'affichait comme un panneau de la fiche.
        val bands = parse("5G NR 2100 :  | Projet approuve |  | Azimut non specifie")

        assertEquals(emptyList<String>(), bands[0].physDetails)
    }

    @Test
    fun stillFiltersTheAccentedAndLocalizedPlaceholders() {
        val bands = parse(
            "LTE 700 :  | Projet approuve |  | Azimut non spécifié\n" +
                "LTE 1800 :  | Projet approuve |  | $txtAzimuthNotSpecified",
        )

        assertTrue(bands.all { it.physDetails.isEmpty() })
    }

    @Test
    fun marksAsAnnouncedOnlyABandWithoutSpectrumNorAntenna() {
        val bands = parse("5G NR 2100 :  | Projet approuve |  | Azimut non specifie")

        assertTrue(bands[0].isAnnouncedOnly())
    }

    @Test
    fun doesNotMarkAsAnnouncedOnlyABandThatCarriesItsSpectrum() {
        val bands = parse(
            "LTE 800 : 852-862 MHz | En service | 25/03/2019 | Panneau : 185° (28,7m) [AER_ID: AE1]",
        )

        assertFalse(bands[0].isAnnouncedOnly())
        assertEquals(listOf("852-862 MHz"), bands[0].spectrumLines)
    }

    @Test
    fun doesNotMarkAsAnnouncedOnlyABandWithoutSpectrumButWithAnAntenna() {
        // Emetteur reel sans bande declaree (cas des FH) : son panneau reste une information.
        val bands = parse("FH 18 GHz :  | En service | 2026-04-01 | Parabole : 45° (35m) [AER_ID: AE6]")

        assertFalse(bands[0].isAnnouncedOnly())
    }

    @Test
    fun keepsAnnouncedAndPublishedSystemsSideBySide() {
        // Cas reel du support 758790 apres completion : la 5G annoncee cotoie la 4G publiee.
        val bands = parse(
            "5G NR 2100 :  | Projet approuve |  | Azimut non specifie\n" +
                "LTE 800 : 852-862 MHz | En service | 25/03/2019 | Panneau : 185° (28,7m) [AER_ID: AE1]",
        )

        assertEquals(2, bands.size)
        assertEquals(listOf(true, false), bands.map { it.isAnnouncedOnly() })
        assertEquals(listOf(5, 4), bands.map { it.gen })
    }
}
