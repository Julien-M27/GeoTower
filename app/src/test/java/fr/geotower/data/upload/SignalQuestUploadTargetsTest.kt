package fr.geotower.data.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalQuestUploadTargetsTest {

    @Test
    fun keepsEveryOperatorWithItsOwnAzimuths() {
        val targets = listOf(
            SignalQuestUploadTarget("ORANGE", "60,180,300"),
            SignalQuestUploadTarget("SFR", "0,120,240")
        )

        assertEquals(targets, SignalQuestUploadTargets.decode(SignalQuestUploadTargets.encode(targets)))
    }

    @Test
    fun keepsOperatorWithoutAzimuths() {
        val targets = listOf(
            SignalQuestUploadTarget("BOUYGUES", ""),
            SignalQuestUploadTarget("FREE", "90")
        )

        assertEquals(targets, SignalQuestUploadTargets.decode(SignalQuestUploadTargets.encode(targets)))
    }

    @Test
    fun encodedValueCarriesNoCommaSoTheRouteStaysReadable() {
        val encoded = SignalQuestUploadTargets.encode(
            listOf(SignalQuestUploadTarget("ORANGE", "60,180,300"))
        )

        assertEquals("ORANGE:60;180;300", encoded)
        assertTrue(encoded.none { it == ',' })
    }

    @Test
    fun dropsDuplicateOperatorsSoAPhotoIsNeverSentTwiceToTheSameTarget() {
        val decoded = SignalQuestUploadTargets.decode("ORANGE:60|orange:180|SFR:90")

        assertEquals(
            listOf(
                SignalQuestUploadTarget("ORANGE", "60"),
                SignalQuestUploadTarget("SFR", "90")
            ),
            decoded
        )
    }

    @Test
    fun mergesAzimuthsOfEveryStationOfTheSameOperator() {
        assertEquals(
            "60,120,180,300",
            SignalQuestUploadTargets.mergeAzimuts("60,180,300", "120,180")
        )
    }

    @Test
    fun mergeIgnoresMissingAndEmptyAzimuths() {
        assertEquals("90", SignalQuestUploadTargets.mergeAzimuts(null, "90", "", " , "))
        assertEquals("", SignalQuestUploadTargets.mergeAzimuts(null, ""))
    }

    @Test
    fun mergedAzimuthsSurviveTheRoute() {
        val targets = listOf(
            SignalQuestUploadTarget("SFR", SignalQuestUploadTargets.mergeAzimuts("0,240", "120"))
        )

        assertEquals(
            listOf(SignalQuestUploadTarget("SFR", "0,120,240")),
            SignalQuestUploadTargets.decode(SignalQuestUploadTargets.encode(targets))
        )
    }

    @Test
    fun ignoresBlankEntries() {
        assertEquals(emptyList<SignalQuestUploadTarget>(), SignalQuestUploadTargets.decode(""))
        assertEquals(emptyList<SignalQuestUploadTarget>(), SignalQuestUploadTargets.decode(null))
        assertEquals(
            listOf(SignalQuestUploadTarget("SFR", "")),
            SignalQuestUploadTargets.decode("|SFR|")
        )
    }
}
