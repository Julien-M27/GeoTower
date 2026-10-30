package fr.geotower.data

import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.RadioServiceMasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioSearchTest {

    @Test
    fun radioMarkerSearchProperties_matchExpectedValues() {
        val marker = RadioMapMarker(
            id = "RADIO_STA01_SUP01",
            latitude = 48.8566,
            longitude = 2.3522,
            serviceMask = RadioServiceMasks.BROADCAST,
            systemMask = 0,
            actorLabel = "TDF",
            emitterCount = 3,
            antennaCount = 6,
            minFreqKhz = 87500,
            maxFreqKhz = 108000,
            stationId = "STA01",
            supportId = "SUP01"
        )

        assertEquals("STA01", marker.stationId)
        assertEquals("SUP01", marker.supportId)
        assertEquals("TDF", marker.actorLabel)
        assertEquals(48.8566, marker.latitude, 0.0001)
        assertEquals(2.3522, marker.longitude, 0.0001)
        assertFalse(marker.isCluster)
    }

    @Test
    fun radioMarkerFallbackLabels_whenActorIsMissing() {
        val marker = RadioMapMarker(
            id = "RADIO_STA02_SUP02",
            latitude = 45.7640,
            longitude = 4.8357,
            serviceMask = RadioServiceMasks.RAIL,
            systemMask = 0,
            actorLabel = null,
            emitterCount = 1,
            antennaCount = 2,
            minFreqKhz = null,
            maxFreqKhz = null,
            stationId = "STA02",
            supportId = "SUP02"
        )

        assertEquals("STA02", marker.stationId)
        assertEquals("SUP02", marker.supportId)
        assertTrue(marker.actorLabel == null)
    }
}
