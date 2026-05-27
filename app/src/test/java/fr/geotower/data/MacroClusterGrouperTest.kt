package fr.geotower.data

import fr.geotower.data.models.DbCluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroClusterGrouperTest {
    @Test
    fun lowZoomMergesTargetedTerritoriesOnly() {
        val reunion = cluster(lat = -21.1, lon = 55.5, count = 222, operators = "Orange Reunion")
        val mayotte = cluster(lat = -12.8, lon = 45.2, count = 344, operators = "SFR Mayotte")

        val merged = MacroClusterGrouper.mergeTargetedTerritories(
            clusters = listOf(
                cluster(lat = 48.8, lon = 2.3, count = 100, operators = "Orange"),
                cluster(lat = 43.6, lon = 5.0, count = 50, operators = "SFR"),
                cluster(lat = 4.9, lon = -52.3, count = 180, operators = "Orange Caraibe"),
                cluster(lat = 3.9, lon = -53.1, count = 87, operators = "Digicel"),
                cluster(lat = 16.2, lon = -61.5, count = 120, operators = "Orange Caraibe"),
                cluster(lat = 14.6, lon = -61.0, count = 55, operators = "Digicel"),
                reunion,
                mayotte
            ),
            zoom = 5.0
        )

        assertEquals(5, merged.size)
        assertTrue(merged.any { it.count == 150 && it.operators == "Orange, SFR" })
        assertTrue(merged.any { it.count == 267 && it.operators == "Orange Caraibe, Digicel" })
        assertTrue(merged.any { it.count == 175 && it.operators == "Orange Caraibe, Digicel" })
        assertTrue(merged.contains(reunion))
        assertTrue(merged.contains(mayotte))
    }

    @Test
    fun higherZoomKeepsProgressiveGridClusters() {
        val clusters = listOf(
            cluster(lat = 48.8, lon = 2.3, count = 100, operators = "Orange"),
            cluster(lat = 43.6, lon = 5.0, count = 50, operators = "SFR")
        )

        assertEquals(
            clusters,
            MacroClusterGrouper.mergeTargetedTerritories(clusters, zoom = 7.0)
        )
    }

    private fun cluster(lat: Double, lon: Double, count: Int, operators: String): DbCluster {
        return DbCluster(centerLat = lat, centerLon = lon, count = count, operators = operators)
    }
}
