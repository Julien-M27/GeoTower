package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceFormatterTest {
    @Test
    fun formatsNearbyMetricDistancesLikeNearbyScreen() {
        assertEquals(NearbyDistanceLabel("999 m"), formatNearbyDistanceLabel(999, useMiles = false))
        assertEquals(NearbyDistanceLabel("1,50", "km"), formatNearbyDistanceLabel(1500, useMiles = false))
    }

    @Test
    fun formatsNearbyImperialDistancesLikeNearbyScreen() {
        assertEquals(NearbyDistanceLabel("164 ft"), formatNearbyDistanceLabel(50, useMiles = true))
        assertEquals(NearbyDistanceLabel("0.62 mi"), formatNearbyDistanceLabel(1000, useMiles = true))
    }

    @Test
    fun formatsSiteDetailDistancesLikeSiteScreen() {
        assertEquals("999 m", formatSiteDistanceMeters(999.9, distanceUnit = 0))
        assertEquals("1.500 km", formatSiteDistanceMeters(1500.0, distanceUnit = 0))
        assertEquals("164 ft", formatSiteDistanceMeters(50.0, distanceUnit = 1))
        assertEquals("0.62 mi", formatSiteDistanceMeters(1000.0, distanceUnit = 1))
    }
}
