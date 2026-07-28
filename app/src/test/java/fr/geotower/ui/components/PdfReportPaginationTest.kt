package fr.geotower.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le rapport PDF doit publier toutes les lignes de tableaux d'une station : le découpage en pages
 * ne doit ni perdre ni dupliquer une ligne, et les sections doivent rester dans l'ordre.
 */
class PdfReportPaginationTest {

    private fun assertCoversEverything(
        emitterCount: Int,
        antennaCount: Int,
        idCount: Int,
        slices: List<PdfReportTableSlice>
    ) {
        var emitter = 0
        var antenna = 0
        var id = 0
        slices.forEachIndexed { index, slice ->
            assertEquals("page $index : émetteurs discontinus", emitter, slice.emitterFrom)
            assertEquals("page $index : antennes discontinues", antenna, slice.antennaFrom)
            assertEquals("page $index : identifiants discontinus", id, slice.idFrom)
            assertTrue("page $index : borne émetteurs invalide", slice.emitterTo >= slice.emitterFrom)
            assertTrue("page $index : borne antennes invalide", slice.antennaTo >= slice.antennaFrom)
            assertTrue("page $index : borne identifiants invalide", slice.idTo >= slice.idFrom)
            // Les antennes ne commencent qu'une fois les émetteurs publiés, puis les identifiants.
            if (slice.antennaTo > slice.antennaFrom) {
                assertEquals("page $index : antennes avant la fin des émetteurs", emitterCount, slice.emitterTo)
            }
            if (slice.idTo > slice.idFrom) {
                assertEquals("page $index : identifiants avant la fin des antennes", antennaCount, slice.antennaTo)
            }
            assertEquals("page $index : continuation mal marquée", index > 0, slice.isContinuation)
            emitter = slice.emitterTo
            antenna = slice.antennaTo
            id = slice.idTo
        }
        assertEquals("émetteurs manquants", emitterCount, emitter)
        assertEquals("antennes manquantes", antennaCount, antenna)
        assertEquals("identifiants manquants", idCount, id)
        assertTrue("aucune ligne ne doit être abandonnée", slices.all { it.droppedRows == 0 })
    }

    @Test
    fun smallStationFitsOnASinglePage() {
        val slices = planPdfReportTableSlices(emitterCount = 4, antennaCount = 4, idCount = 3)

        assertEquals(1, slices.size)
        assertFalse(slices.single().isContinuation)
        assertCoversEverything(4, 4, 3, slices)
    }

    @Test
    fun stationWithoutTablesStillProducesTheIdentityPage() {
        val slices = planPdfReportTableSlices(emitterCount = 0, antennaCount = 0, idCount = 0)

        assertEquals(1, slices.size)
        assertFalse(slices.single().isContinuation)
        assertEquals(0, slices.single().emitterTo)
    }

    @Test
    fun busyStationOverflowsOnContinuationPagesWithoutLosingRows() {
        val slices = planPdfReportTableSlices(emitterCount = 22, antennaCount = 22, idCount = 18)

        assertTrue("le débordement doit ouvrir des pages de suite", slices.size > 1)
        assertCoversEverything(22, 22, 18, slices)
    }

    @Test
    fun everyPlausibleRowCountIsFullyPublished() {
        listOf(1, 2, 5, 8, 9, 12, 16, 20, 30).forEach { count ->
            val slices = planPdfReportTableSlices(
                emitterCount = count,
                antennaCount = count,
                idCount = count
            )
            assertCoversEverything(count, count, count, slices)
        }
    }

    @Test
    fun aPageWithoutTheStatusCardHoldsMoreRows() {
        val withStatus = planPdfReportTableSlices(
            emitterCount = 40,
            antennaCount = 0,
            idCount = 0,
            firstPageSpaceDp = PDF_TABLE_SPACE_FIRST_PAGE_DP
        )
        val withoutStatus = planPdfReportTableSlices(
            emitterCount = 40,
            antennaCount = 0,
            idCount = 0,
            firstPageSpaceDp = PDF_TABLE_SPACE_FIRST_PAGE_NO_STATUS_DP
        )

        assertTrue(withoutStatus.first().emitterTo > withStatus.first().emitterTo)
        assertCoversEverything(40, 0, 0, withStatus)
        assertCoversEverything(40, 0, 0, withoutStatus)
    }
}
