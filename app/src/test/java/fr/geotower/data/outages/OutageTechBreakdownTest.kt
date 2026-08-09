package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutageTechBreakdownTest {

    private fun site(
        operateur: String,
        voix2g: String? = null,
        voix3g: String? = null,
        voix4g: String? = null,
        voix5g: String? = null,
        data2g: String? = null,
        data3g: String? = null,
        data4g: String? = null,
        data5g: String? = null,
    ) = SiteHsEntity(
        idAnfr = "0000000001",
        operateur = operateur,
        latitude = 48.85,
        longitude = 2.35,
        voix2g = voix2g,
        voix3g = voix3g,
        voix4g = voix4g,
        voix5g = voix5g,
        data2g = data2g,
        data3g = data3g,
        data4g = data4g,
        data5g = data5g,
    )

    @Test
    fun aTechnologyIsCountedOnceWhenVoiceOrDataIsDown() {
        val rows = OutageTechBreakdown.of(
            listOf(
                // Voix ET data HS sur la 4G : une seule panne 4G, pas deux.
                site("Orange", voix4g = "HS", data4g = "HS", voix3g = "OK", data3g = "OK"),
                // Data seule HS : la 4G compte quand même.
                site("Orange", voix4g = "OK", data4g = "HS", voix3g = "OK", data3g = "OK"),
                site("Orange", voix4g = "OK", data4g = "OK", voix3g = "HS", data3g = "HS"),
            ),
        )

        val orange = rows.single()
        assertEquals("Orange", orange.operateur)
        assertEquals(2, orange.countFor(OutageTechnology.G4))
        assertEquals(1, orange.countFor(OutageTechnology.G3))
    }

    @Test
    fun degradedCodeCountsAsOutOfServiceLikeTheSiteSheet() {
        val rows = OutageTechBreakdown.of(listOf(site("SFR", voix4g = "DE", data4g = "de")))

        // « DE » vaut rouge dans la fiche site : il doit valoir panne ici aussi.
        assertEquals(1, rows.single().countFor(OutageTechnology.G4))
    }

    @Test
    fun aTechnologyNobodyReportsStaysUnpublishedInsteadOfZero() {
        val rows = OutageTechBreakdown.of(
            listOf(
                site("Free Mobile", voix4g = "HS", data4g = "HS"),
                site("Free Mobile", voix4g = "OK", data4g = "-"),
            ),
        )

        val free = rows.single()
        assertEquals(1, free.countFor(OutageTechnology.G4))
        // Aucune valeur 2G/3G/5G dans le relevé : « — », surtout pas « 0 panne ».
        assertEquals(OutageTechBreakdown.UNPUBLISHED, free.countFor(OutageTechnology.G2))
        assertEquals(OutageTechBreakdown.UNPUBLISHED, free.countFor(OutageTechnology.G3))
        assertEquals(OutageTechBreakdown.UNPUBLISHED, free.countFor(OutageTechnology.G5))
        assertTrue(free.hasUnpublished)
    }

    @Test
    fun operatorsFollowTheSameOrderAsTheCountBreakdown() {
        val sites = listOf(
            site("SFR", voix4g = "HS"),
            site("Orange", voix4g = "HS"),
            site("Orange", voix4g = "HS"),
            site("Orange", voix4g = "OK"),
        )

        assertEquals(
            OutageLocalConfig.breakdownOf(sites).map { it.first },
            OutageTechBreakdown.of(sites).map { it.operateur },
        )
    }

    @Test
    fun totalsSumOnlyWhatOperatorsPublish() {
        val rows = OutageTechBreakdown.of(
            listOf(
                site("Orange", voix4g = "HS", voix3g = "HS"),
                site("SFR", voix4g = "HS"),
            ),
        )

        val totals = OutageTechBreakdown.totalsOf(rows)
        assertEquals(2, totals[OutageTechnology.G4.ordinal])
        // Seul Orange renseigne la 3G : le total vaut le sien, pas « inconnu ».
        assertEquals(1, totals[OutageTechnology.G3.ordinal])
        assertEquals(OutageTechBreakdown.UNPUBLISHED, totals[OutageTechnology.G5.ordinal])
    }

    @Test
    fun encodingSurvivesOperatorNamesContainingSeparators() {
        val rows = listOf(
            OutageTechRow("Bouygues\tTelecom", listOf(0, 3, 12, OutageTechBreakdown.UNPUBLISHED)),
            OutageTechRow("Free\nMobile", listOf(OutageTechBreakdown.UNPUBLISHED, 0, 4, 1)),
        )

        assertEquals(
            listOf(
                OutageTechRow("Bouygues Telecom", listOf(0, 3, 12, OutageTechBreakdown.UNPUBLISHED)),
                OutageTechRow("Free Mobile", listOf(OutageTechBreakdown.UNPUBLISHED, 0, 4, 1)),
            ),
            OutageTechBreakdown.decode(OutageTechBreakdown.encode(rows)),
        )
    }

    @Test
    fun corruptedOrTruncatedLinesAreDropped() {
        val decoded = OutageTechBreakdown.decode(
            "Orange\t1\t2\t3\t4\nSFR\t1\t2\nBouygues Telecom\t1\t2\tx\t4\n\n\t1\t2\t3\t4\n",
        )

        assertEquals(listOf(OutageTechRow("Orange", listOf(1, 2, 3, 4))), decoded)
    }

    @Test
    fun freshInstallHasNoTechBreakdown() {
        assertTrue(OutageTechBreakdown.decode(null).isEmpty())
        assertTrue(OutageTechBreakdown.of(emptyList()).isEmpty())
    }
}
