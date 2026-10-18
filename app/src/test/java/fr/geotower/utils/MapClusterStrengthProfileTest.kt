package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapClusterStrengthProfileTest {

    @Test
    fun normalize_clampsValuesBetween0And100() {
        assertEquals(0, MapClusterStrengthProfile.normalize(-10))
        assertEquals(0, MapClusterStrengthProfile.normalize(0))
        assertEquals(50, MapClusterStrengthProfile.normalize(50))
        assertEquals(100, MapClusterStrengthProfile.normalize(100))
        assertEquals(100, MapClusterStrengthProfile.normalize(150))
    }

    @Test
    fun nearbyRadiusPx_scalesWithStrengthAndZoom() {
        // Zoom < 14 -> base 220
        assertEquals(220, MapClusterStrengthProfile.nearbyRadiusPx(12.0, 100))
        assertEquals(110, MapClusterStrengthProfile.nearbyRadiusPx(12.0, 50))
        assertEquals(0, MapClusterStrengthProfile.nearbyRadiusPx(12.0, 0))

        // Zoom >= 17 -> base 60
        assertEquals(60, MapClusterStrengthProfile.nearbyRadiusPx(17.5, 100))
        assertEquals(30, MapClusterStrengthProfile.nearbyRadiusPx(17.5, 50))
    }

    @Test
    fun aggregationZoom_atOrAbove14_returnsUnchangedMapZoom() {
        assertEquals(14.0, MapClusterStrengthProfile.aggregationZoom(14.0, 50), 0.001)
        assertEquals(15.5, MapClusterStrengthProfile.aggregationZoom(15.5, 80), 0.001)
    }

    @Test
    fun aggregationZoom_lowZoom_shiftsTowardsFinerZoomWhenStrengthIsLow() {
        // At 100% strength, aggregationZoom should equal mapZoom
        assertEquals(10.0, MapClusterStrengthProfile.aggregationZoom(10.0, 100), 0.001)
        // At 0% strength, aggregationZoom shifts to finestZoom = min(12.5, 10.0 + 2.5) = 12.5
        assertEquals(12.5, MapClusterStrengthProfile.aggregationZoom(10.0, 0), 0.001)
    }

    @Test
    fun iconScale_scalesFrom60PercentTo100Percent() {
        assertEquals(1.0f, MapClusterStrengthProfile.iconScale(100), 0.001f)
        assertEquals(0.8f, MapClusterStrengthProfile.iconScale(50), 0.001f)
        assertEquals(0.6f, MapClusterStrengthProfile.iconScale(0), 0.001f)
    }

    @Test
    fun showTowerPictogram_onlyAbove25Percent() {
        assertFalse(MapClusterStrengthProfile.showTowerPictogram(0))
        assertFalse(MapClusterStrengthProfile.showTowerPictogram(25))
        assertTrue(MapClusterStrengthProfile.showTowerPictogram(26))
        assertTrue(MapClusterStrengthProfile.showTowerPictogram(100))
    }

    @Test
    fun antennaCoreRadiusUnits_transitionsCorrectly() {
        assertEquals(20f, MapClusterStrengthProfile.antennaCoreRadiusUnits(0), 0.001f)
        assertEquals(20f, MapClusterStrengthProfile.antennaCoreRadiusUnits(25), 0.001f)
        assertEquals(45f, MapClusterStrengthProfile.antennaCoreRadiusUnits(100), 0.001f)
    }
}
