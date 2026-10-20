package fr.geotower.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntennaVoiceQueryHandlerTest {

    @Test
    fun testCleanOperatorFilter() {
        assertNull(AntennaVoiceQueryHandler.cleanOperatorFilter(null))
        assertNull(AntennaVoiceQueryHandler.cleanOperatorFilter("   "))
        assertEquals("Free", AntennaVoiceQueryHandler.cleanOperatorFilter("free"))
        assertEquals("Free", AntennaVoiceQueryHandler.cleanOperatorFilter("Free Mobile"))
        assertEquals("Orange", AntennaVoiceQueryHandler.cleanOperatorFilter("orange"))
        assertEquals("SFR", AntennaVoiceQueryHandler.cleanOperatorFilter("sfr"))
        assertEquals("Bouygues", AntennaVoiceQueryHandler.cleanOperatorFilter("Bouygues Telecom"))
        assertEquals("Autre", AntennaVoiceQueryHandler.cleanOperatorFilter("Autre"))
    }

    @Test
    fun testCardinalDirection() {
        assertEquals("au nord", AntennaVoiceQueryHandler.getCardinalDirection(0f))
        assertEquals("au nord", AntennaVoiceQueryHandler.getCardinalDirection(350f))
        assertEquals("au nord-est", AntennaVoiceQueryHandler.getCardinalDirection(45f))
        assertEquals("à l'est", AntennaVoiceQueryHandler.getCardinalDirection(90f))
        assertEquals("au sud-est", AntennaVoiceQueryHandler.getCardinalDirection(135f))
        assertEquals("au sud", AntennaVoiceQueryHandler.getCardinalDirection(180f))
        assertEquals("au sud-ouest", AntennaVoiceQueryHandler.getCardinalDirection(225f))
        assertEquals("à l'ouest", AntennaVoiceQueryHandler.getCardinalDirection(270f))
        assertEquals("au nord-ouest", AntennaVoiceQueryHandler.getCardinalDirection(315f))
    }

    @Test
    fun testExtractTechnologies() {
        assertEquals(emptyList<String>(), AntennaVoiceQueryHandler.extractTechnologies(0))
        assertEquals(listOf("2G"), AntennaVoiceQueryHandler.extractTechnologies(1))
        assertEquals(listOf("4G", "5G"), AntennaVoiceQueryHandler.extractTechnologies(4 or 8))
        assertEquals(listOf("2G", "3G", "4G", "5G"), AntennaVoiceQueryHandler.extractTechnologies(1 or 2 or 4 or 8))
    }

    @Test
    fun testBuildSpokenSentence() {
        val sentence1 = AntennaVoiceQueryHandler.buildSpokenSentence(
            operatorName = "Free Mobile",
            distanceMeters = 350,
            directionLabel = "au nord-est",
            technologies = listOf("4G", "5G")
        )
        assertTrue(sentence1.contains("Free Mobile"))
        assertTrue(sentence1.contains("350 mètres"))
        assertTrue(sentence1.contains("au nord-est"))
        assertTrue(sentence1.contains("4G et 5G"))

        val sentence2 = AntennaVoiceQueryHandler.buildSpokenSentence(
            operatorName = "Orange",
            distanceMeters = 1500,
            directionLabel = "au sud",
            technologies = emptyList()
        )
        assertTrue(sentence2.contains("1.5 kilomètres") || sentence2.contains("1,5 kilomètres"))
        assertTrue(sentence2.contains("au sud"))
    }
}
