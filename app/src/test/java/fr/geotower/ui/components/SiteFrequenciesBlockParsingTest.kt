package fr.geotower.ui.components

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.utils.addMicrowaveFallbackBands
import fr.geotower.utils.parseAndSortFrequencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteFrequenciesBlockParsingTest {
    @Test
    fun microwaveLinksWithSameSystemNameKeepSeparateSpectrumBlocks() {
        val details = """
            FH : 10700-10728 MHz | En service | 2026-05-07 | Parabole FH : 10 deg (20m)
            FH : 11200-11228 MHz | En service | 2026-05-07 | Parabole FH : 190 deg (20m)
        """.trimIndent()

        val parsed = parseAndSortFrequencies(
            freqStr = details,
            txtUnknown = "Inconnu",
            txtAzimuthNotSpecified = "Azimut non specifie"
        )

        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.spectrumLines == listOf("10700-10728 MHz") && it.physDetails.any { phys -> phys.contains("10 deg") } })
        assertTrue(parsed.any { it.spectrumLines == listOf("11200-11228 MHz") && it.physDetails.any { phys -> phys.contains("190 deg") } })
    }

    @Test
    fun mobileBandWithSameRangesInDifferentOrderKeepsUniqueSpectrumLines() {
        val details = """
            5G NR 2100 (5G) : 1935,3-1950,1 MHz, 2125,3-2140,1 MHz | Techniquement operationnel | 2024-06-27 | Panneau : 120 deg (24,6m)
            5G NR 2100 (5G) : 2125,3-2140,1 MHz, 1935,3-1950,1 MHz | Techniquement operationnel | 2024-06-27 | Panneau : 220 deg (24,6m)
            LTE 2100 (4G) : 1935,3-1950,1 MHz, 2125,3-2140,1 MHz | En service | 2024-06-11 | Panneau : 120 deg (24,6m)
        """.trimIndent()

        val parsed = parseAndSortFrequencies(
            freqStr = details,
            txtUnknown = "Inconnu",
            txtAzimuthNotSpecified = "Azimut non specifie"
        )

        val nr2100 = parsed.single { it.gen == 5 && it.value == 2100 }
        val lte2100 = parsed.single { it.gen == 4 && it.value == 2100 }

        assertEquals(listOf("1935,3-1950,1 MHz", "2125,3-2140,1 MHz"), nr2100.spectrumLines)
        assertEquals(listOf("1935,3-1950,1 MHz", "2125,3-2140,1 MHz"), lte2100.spectrumLines)
        assertEquals(2, nr2100.physDetails.size)
    }

    @Test
    fun mobileBandKeepsDistinctPanelIdsInPhysicalDetails() {
        val details = """
            LTE 1800 (4G) : 1835-1850 MHz | En service | 2024-06-11 | Panneau : 120 deg (24,6m) [AER_ID: 7001]
            LTE 1800 (4G) : 1835-1850 MHz | En service | 2024-06-11 | Panneau : 120 deg (24,6m) [AER_ID: 7002]
        """.trimIndent()

        val parsed = parseAndSortFrequencies(
            freqStr = details,
            txtUnknown = "Inconnu",
            txtAzimuthNotSpecified = "Azimut non specifie"
        )

        val lte1800 = parsed.single { it.gen == 4 && it.value == 1800 }
        assertEquals(2, lte1800.physDetails.size)
        assertTrue(lte1800.physDetails.any { it.contains("[AER_ID: 7001]") })
        assertTrue(lte1800.physDetails.any { it.contains("[AER_ID: 7002]") })
    }

    @Test
    fun microwaveFallbackAddsTableRowFromLocalisationAzimuths() {
        val enriched = addMicrowaveFallbackBands(
            bands = emptyList(),
            info = localisation(azimutsFh = "40,220"),
            technique = technique(technologies = "FH", statut = "En service", dateService = "2026-05-07"),
            rawFreqs = null,
            txtUnknown = "Inconnu"
        )

        assertEquals(1, enriched.size)
        assertEquals("FH", enriched.single().rawFreq)
        assertEquals(listOf("FH : 40\u00B0 (-)", "FH : 220\u00B0 (-)"), enriched.single().physDetails)
    }

    @Test
    fun microwaveFallbackDoesNotAddEmptyRowWithoutPhysicalDetails() {
        val enriched = addMicrowaveFallbackBands(
            bands = emptyList(),
            info = localisation(azimutsFh = null),
            technique = technique(technologies = "FH", statut = "Projet approuve", dateService = "1992-12-04"),
            rawFreqs = null,
            txtUnknown = "Inconnu"
        )

        assertTrue(enriched.isEmpty())
    }

    @Test
    fun microwaveFallbackCompletesExistingTableRowPhysicalDetails() {
        val parsed = parseAndSortFrequencies(
            freqStr = "FH : 10700-10728 MHz | En service | 2026-05-07 | Azimut non specifie",
            txtUnknown = "Inconnu",
            txtAzimuthNotSpecified = "Azimut non specifie"
        )

        val enriched = addMicrowaveFallbackBands(
            bands = parsed,
            info = localisation(azimutsFh = "360,180"),
            technique = technique(technologies = "FH"),
            rawFreqs = null,
            txtUnknown = "Inconnu"
        )

        assertEquals(1, enriched.size)
        assertEquals(listOf("FH : 0\u00B0 (-)", "FH : 180\u00B0 (-)"), enriched.single().physDetails)
    }

    private fun localisation(azimutsFh: String?): LocalisationEntity {
        return LocalisationEntity(
            idAnfr = "12345",
            operateur = "Test",
            latitude = 48.0,
            longitude = 2.0,
            azimuts = null,
            codeInsee = null,
            azimutsFh = azimutsFh,
            techMask = 16,
            bandMask = 1 shl 14,
            statut = "En service",
            hasActive = 1
        )
    }

    private fun technique(
        technologies: String?,
        statut: String = "En service",
        dateService: String = ""
    ): TechniqueEntity {
        return TechniqueEntity(
            idAnfr = "12345",
            technologies = technologies,
            statut = statut,
            dateImplantation = "",
            dateService = dateService,
            dateModif = "",
            encodedDetailsFrequences = null,
            adresse = null
        )
    }
}
