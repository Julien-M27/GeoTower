package fr.geotower.ui.components

import fr.geotower.utils.FreqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le partage en images d'une station doit publier toutes ses bandes : le découpage ne doit ni
 * en perdre, ni en dupliquer, ni produire une image démesurée tant que le plafond n'est pas atteint.
 */
class SiteSharePaginationTest {

    private val firstPageSpace = siteShareFirstPageSpaceDp(includeIdentifiers = true)
    private val nextPageSpace = siteShareNextPageSpaceDp()

    private fun assertCoversEverything(bandCount: Int, pages: List<SiteShareFreqPage>) {
        var cursor = 0
        pages.forEachIndexed { index, page ->
            assertEquals("image $index : bandes discontinues", cursor, page.from)
            assertTrue("image $index : bornes invalides", page.to > page.from)
            cursor = page.to
        }
        assertEquals("bandes manquantes", bandCount, cursor)
    }

    @Test
    fun aLightStationFitsOnASingleImage() {
        val pages = planSiteShareFrequencyPages(
            bandHeightsDp = List(3) { 120f },
            firstPageSpaceDp = firstPageSpace,
            nextPageSpaceDp = nextPageSpace
        )

        assertEquals(1, pages.size)
        assertCoversEverything(3, pages)
    }

    @Test
    fun aStationWithoutBandsStillProducesOneImage() {
        val pages = planSiteShareFrequencyPages(
            bandHeightsDp = emptyList(),
            firstPageSpaceDp = firstPageSpace,
            nextPageSpaceDp = nextPageSpace
        )

        assertEquals(listOf(SiteShareFreqPage(0, 0)), pages)
    }

    @Test
    fun aBusyStationOverflowsOnFollowingImagesWithoutLosingBands() {
        val pages = planSiteShareFrequencyPages(
            bandHeightsDp = List(12) { 220f },
            firstPageSpaceDp = firstPageSpace,
            nextPageSpaceDp = nextPageSpace
        )

        assertTrue("le débordement doit ouvrir des images de suite", pages.size > 1)
        assertCoversEverything(12, pages)
    }

    @Test
    fun everyPlausibleBandCountIsFullyPublished() {
        listOf(1, 2, 3, 5, 8, 12, 16, 24, 40).forEach { count ->
            val pages = planSiteShareFrequencyPages(
                bandHeightsDp = List(count) { 180f },
                firstPageSpaceDp = firstPageSpace,
                nextPageSpaceDp = nextPageSpace
            )
            assertCoversEverything(count, pages)
            assertTrue("plafond dépassé pour $count bandes", pages.size <= SITE_SHARE_MAX_FREQ_PAGES)
        }
    }

    @Test
    fun theIdentifiersCardLeavesLessRoomOnTheFirstImage() {
        val heights = List(10) { 150f }
        val withIds = planSiteShareFrequencyPages(
            bandHeightsDp = heights,
            firstPageSpaceDp = siteShareFirstPageSpaceDp(includeIdentifiers = true),
            nextPageSpaceDp = nextPageSpace
        )
        val withoutIds = planSiteShareFrequencyPages(
            bandHeightsDp = heights,
            firstPageSpaceDp = siteShareFirstPageSpaceDp(includeIdentifiers = false),
            nextPageSpaceDp = nextPageSpace
        )

        assertTrue(withoutIds.first().to > withIds.first().to)
        assertCoversEverything(10, withIds)
        assertCoversEverything(10, withoutIds)
    }

    @Test
    fun aBandTallerThanAPageIsNeverSplitInHalf() {
        val pages = planSiteShareFrequencyPages(
            bandHeightsDp = listOf(4_000f, 120f),
            firstPageSpaceDp = firstPageSpace,
            nextPageSpaceDp = nextPageSpace
        )

        assertEquals(SiteShareFreqPage(0, 1), pages.first())
        assertCoversEverything(2, pages)
    }

    @Test
    fun theLastImageAbsorbsWhatDoesNotFitInTheCap() {
        val pages = planSiteShareFrequencyPages(
            bandHeightsDp = List(40) { 300f },
            firstPageSpaceDp = firstPageSpace,
            nextPageSpaceDp = nextPageSpace
        )

        assertEquals(SITE_SHARE_MAX_FREQ_PAGES, pages.size)
        assertCoversEverything(40, pages)
    }

    @Test
    fun aBandGrowsWithItsSpectrumRangesAndPanels() {
        val bare = FreqBand(
            rawFreq = "4G 700",
            status = "En service",
            date = "2023-01-01",
            physDetails = emptyList(),
            gen = 4,
            value = 700
        )
        val detailed = bare.copy(
            rawFreq = "4G 700 : 703-713 MHz, 758-768 MHz",
            physDetails = listOf("Panneau : 60° (28.9m)", "Panneau : 180° (28.9m)")
        )

        val bareHeight = siteShareBandHeightDp(bare, true, true, true)
        val detailedHeight = siteShareBandHeightDp(detailed, true, true, true)
        val withoutSpectrum = siteShareBandHeightDp(detailed, false, false, false)

        assertTrue("les plages et les panneaux doivent coûter de la hauteur", detailedHeight > bareHeight)
        assertTrue("couper le spectre doit rendre de la place", withoutSpectrum < detailedHeight)
        assertTrue("une bande garde toujours son en-tête et sa date", bareHeight > 0f)
    }

    @Test
    fun theSpaceLeftForBandsStaysPositive() {
        assertTrue(siteShareFirstPageSpaceDp(includeIdentifiers = true) > 0f)
        assertTrue(siteShareNextPageSpaceDp() > siteShareFirstPageSpaceDp(includeIdentifiers = true))
    }
}
