package fr.geotower.ui.screens.emitters

import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioFilterMasks
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.TechFrequencyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteComparatorModelTest {

    @Test
    fun `compareSiteBands separates common and exclusive bands`() {
        val result = compareSiteBands(
            bandsA = listOf("4G 800", "4G 1800", "5G 3500"),
            bandsB = listOf("4G 800", "4G 2600", "5G 700")
        )

        assertEquals(listOf("4G 800"), result.common)
        assertEquals(listOf("5G 3500", "4G 1800"), result.onlyA)
        assertEquals(listOf("5G 700", "4G 2600"), result.onlyB)
    }

    @Test
    fun `computeDistanceMeters and computeBearingDegrees are accurate`() {
        // Paris (Notre-Dame) -> ~1 km North
        val lat1 = 48.8530
        val lon1 = 2.3499
        val lat2 = 48.8620
        val lon2 = 2.3499

        val dist = computeDistanceMeters(lat1, lon1, lat2, lon2)
        val bearing = computeBearingDegrees(lat1, lon1, lat2, lon2)

        assertTrue("Expected ~1000 m, got $dist", dist in 950.0..1050.0)
        assertEquals(0, bearing)
    }

    @Test
    fun `parseAzimuths normalizes and deduplicates degrees`() {
        val parsed = parseAzimuths("120°, 360, 0, 240; 120")
        assertEquals(listOf(0, 120, 240), parsed)
    }

    @Test
    fun `formatComparatorDistance formats meters and kilometers`() {
        assertEquals("450 m", formatComparatorDistance(450.2))
        val km = formatComparatorDistance(1250.0)
        assertTrue("Expected km suffix in $km", km.endsWith("km"))
    }

    @Test
    fun `buildComparableSiteSummary extracts support height, FH and technologies`() {
        val loc = LocalisationEntity(
            idAnfr = "0750010001",
            operateur = "ORANGE",
            latitude = 48.8566,
            longitude = 2.3522,
            azimuts = "0, 120, 240",
            codeInsee = "75101",
            azimutsFh = "45",
            techMask = RadioFilterMasks.TECH_4G or RadioFilterMasks.TECH_5G or RadioFilterMasks.TECH_FH,
            bandMask = RadioFilterMasks.BAND_4G_800 or RadioFilterMasks.BAND_4G_2600 or RadioFilterMasks.BAND_5G_3500,
            statut = "En service",
            hasActive = 1
        )
        val tech = TechniqueEntity(
            idAnfr = "0750010001",
            technologies = "4G, 5G",
            statut = "En service",
            dateImplantation = "2018-04-12",
            dateService = "2018-06-01",
            dateModif = "2026-01-10",
            encodedDetailsFrequences = "LTE 800 | En service | 2018-06-01 | Azimut 0°\nLTE 2600 | En service | 2018-06-01 | Azimut 0°\n5G NR 3500 | En service | 2022-01-15 | Azimut 0°",
            adresse = "10 Rue de Rivoli, 75001 Paris"
        )
        val phys = listOf(
            PhysiqueEntity(
                idAnfr = "0750010001",
                idSupport = "123456",
                natureSupport = "Immeuble",
                proprietaire = "Syndic",
                exploitant = "ORANGE",
                hauteur = 32.5,
                azimutsEtTypes = null
            )
        )
        val fh = listOf(
            FaisceauxEntity(
                idAnfr = "0750010001",
                idSupport = "123456",
                typeFh = "FH",
                azimutsFh = "45"
            )
        )

        val summary = buildComparableSiteSummary(loc, tech, phys, fh)

        assertEquals("0750010001", summary.idAnfr)
        assertEquals("ORANGE", summary.operatorName)
        assertEquals("10 Rue de Rivoli, 75001 Paris", summary.address)
        assertEquals(32.5, summary.maxSupportHeightMeters ?: 0.0, 0.01)
        assertEquals("Immeuble", summary.supportNature)
        assertTrue(summary.hasFh)
        assertTrue(summary.isActive)
        assertEquals(listOf(0, 120, 240), summary.azimuths)
        assertTrue(summary.technologies.containsAll(listOf("4G", "5G")))
        assertFalse(summary.bandLabels.isEmpty())
    }

    @Test
    fun `formatBandTokensFromMask respects descending and ascending order`() {
        val mask = RadioFilterMasks.BAND_2G_900 or
            RadioFilterMasks.BAND_4G_800 or
            RadioFilterMasks.BAND_4G_2600 or
            RadioFilterMasks.BAND_5G_3500

        val desc = formatBandTokensFromMask(mask, TechFrequencyOrder.DESCENDING)
        assertEquals(listOf("5G 3500", "4G 2600", "4G 800", "2G 900"), desc)

        val asc = formatBandTokensFromMask(mask, TechFrequencyOrder.ASCENDING)
        assertEquals(listOf("2G 900", "4G 800", "4G 2600", "5G 3500"), asc)
    }
}
