package fr.geotower.ui.screens.fh

import fr.geotower.data.models.FhLinkItem
import fr.geotower.utils.OperatorColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FhOperatorResolverTest {

    private fun link(label: String?, idA: String = "A1", idB: String = "B1"): FhLinkItem {
        return FhLinkItem(
            idAnfrA = idA,
            latA = 48.85,
            lonA = 2.35,
            idAnfrB = idB,
            latB = 48.90,
            lonB = 2.40,
            operateurId = null,
            admId = null,
            operatorLabel = label,
            azimutAb = 45.0,
            distanceM = 6500
        )
    }

    @Test
    fun `resolve maps major mobile operators and other named operators`() {
        val orange = FhOperatorResolver.resolve(link("ORANGE"))
        assertEquals(OperatorColors.ORANGE_KEY, orange.id)
        assertEquals("Orange", orange.label)
        assertTrue(orange.isMajorMobile)

        val tdf = FhOperatorResolver.resolve(link("TDF"))
        assertEquals("RAW:TDF", tdf.id)
        assertEquals("TDF", tdf.label)
        assertFalse(tdf.isMajorMobile)

        val unknown = FhOperatorResolver.resolve(link("adm_999"))
        assertEquals(FhOperatorResolver.OTHER_OPERATOR_ID, unknown.id)
        assertFalse(unknown.isMajorMobile)
    }

    @Test
    fun `buildBuckets orders major operators first then named then other`() {
        val prepared = FhOperatorResolver.prepare(
            listOf(
                link("adm_12"),
                link("TDF"),
                link("FREE MOBILE"),
                link("ORANGE"),
                link("ORANGE", idA = "A2", idB = "B2"),
                link("TDF", idA = "A3", idB = "B3"),
                link("TDF", idA = "A4", idB = "B4")
            )
        )
        val buckets = FhOperatorResolver.buildBuckets(prepared)
        assertEquals(
            listOf(OperatorColors.ORANGE_KEY, OperatorColors.FREE_KEY, "RAW:TDF", FhOperatorResolver.OTHER_OPERATOR_ID),
            buckets.map { it.id }
        )
        assertEquals(2, buckets[0].count)
        assertEquals(1, buckets[1].count)
        assertEquals(3, buckets[2].count)
        assertEquals(1, buckets[3].count)
    }
}
