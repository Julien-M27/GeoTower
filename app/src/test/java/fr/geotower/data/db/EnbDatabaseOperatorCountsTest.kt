package fr.geotower.data.db

import fr.geotower.data.db.EnbDatabaseOperatorCounts.EnbMncTechnologyCounts
import fr.geotower.data.db.EnbDatabaseOperatorCounts.EnbSourceOperatorRow
import org.junit.Assert.assertEquals
import org.junit.Test

class EnbDatabaseOperatorCountsTest {

    @Test
    fun combinesAllMncsOfAnOperatorAndKeepsTechnologiesSeparate() {
        val counts = EnbDatabaseOperatorCounts.aggregateOperatorCounts(
            sourceRows = listOf(
                EnbSourceOperatorRow("FREE MOBILE", listOf(15, 16)),
                EnbSourceOperatorRow("ORANGE", listOf(1)),
            ),
            countsByMnc = mapOf(
                15 to EnbMncTechnologyCounts(enbCount = 10, gnbCount = 2),
                16 to EnbMncTechnologyCounts(enbCount = 3, gnbCount = 1),
                1 to EnbMncTechnologyCounts(enbCount = 20, gnbCount = 4),
            ),
        )

        assertEquals(
            listOf(
                EnbOperatorCount("FREE MOBILE", enbCount = 13, gnbCount = 3),
                EnbOperatorCount("ORANGE", enbCount = 20, gnbCount = 4),
            ),
            counts,
        )
    }

    @Test
    fun mergesDuplicateSourceRowsWithoutDoubleCountingTheirMncs() {
        val counts = EnbDatabaseOperatorCounts.aggregateOperatorCounts(
            sourceRows = listOf(
                EnbSourceOperatorRow("ORANGE", listOf(1, 2)),
                EnbSourceOperatorRow("ORANGE", listOf(2)),
            ),
            countsByMnc = mapOf(
                1 to EnbMncTechnologyCounts(enbCount = 5),
                2 to EnbMncTechnologyCounts(gnbCount = 7),
            ),
        )

        assertEquals(listOf(EnbOperatorCount("ORANGE", 5, 7)), counts)
    }
}
