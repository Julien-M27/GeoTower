package fr.geotower.data.community

import fr.geotower.utils.OperatorColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityDataPreferencesTest {
    @Test
    fun orderedOperators_usesRequestedBaseOrder() {
        val orderedKeys = CommunityDataPreferences.orderedOperators(null).map { it.key }

        assertEquals(
            listOf(
                OperatorColors.ORANGE_KEY,
                OperatorColors.BOUYGUES_KEY,
                OperatorColors.SFR_KEY,
                OperatorColors.FREE_KEY
            ),
            orderedKeys.take(4)
        )
    }

    @Test
    fun orderedOperators_pinsDefaultOperatorWithoutDuplicate() {
        val orderedKeys = CommunityDataPreferences.orderedOperators("Free Mobile").map { it.key }

        assertEquals(OperatorColors.FREE_KEY, orderedKeys.first())
        assertEquals(orderedKeys.distinct(), orderedKeys)
        assertEquals(
            listOf(
                OperatorColors.FREE_KEY,
                OperatorColors.ORANGE_KEY,
                OperatorColors.BOUYGUES_KEY,
                OperatorColors.SFR_KEY
            ),
            orderedKeys.take(4)
        )
    }

    @Test
    fun orderedOperators_canPinSupportedOverseasOperator() {
        val orderedKeys = CommunityDataPreferences.orderedOperators("SRR").map { it.key }

        assertEquals(OperatorColors.SRR_KEY, orderedKeys.first())
        assertEquals(OperatorColors.ORANGE_KEY, orderedKeys[1])
    }

    @Test
    fun sourceIdForCommunityName_mapsKnownPhotoSources() {
        assertEquals(
            CommunityDataPreferences.SOURCE_CELLULARFR,
            CommunityDataPreferences.sourceIdForCommunityName("CellularFR")
        )
        assertEquals(
            CommunityDataPreferences.SOURCE_SIGNALQUEST,
            CommunityDataPreferences.sourceIdForCommunityName("Signal Quest")
        )
    }
}
