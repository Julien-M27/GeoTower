package fr.geotower.data.coverage

import fr.geotower.data.api.BdTopoBuildingsApi
import fr.geotower.data.api.ElevationProfileApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Câble un [CoverageEngine] sur les vraies sources réseau (IGN élévation + BD TOPO).
 * Le moteur lui-même reste pur ; seule cette fabrique connaît `android`/le réseau.
 */
object CoverageEngineFactory {
    fun create(
        maxPointsPerRequest: Int = 300,
        maxConcurrentRequests: Int = 6,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): CoverageEngine {
        val loader = TerrainFieldLoader(
            getElevations = { points ->
                withContext(Dispatchers.IO) { ElevationProfileApi.getElevations(points) }
            },
            fetchBuildings = { minLat, minLon, maxLat, maxLon ->
                withContext(Dispatchers.IO) {
                    BdTopoBuildingsApi.fetchBuildingsForBbox(minLat, minLon, maxLat, maxLon)
                }
            },
            maxPointsPerRequest = maxPointsPerRequest,
            maxConcurrentRequests = maxConcurrentRequests
        )
        return CoverageEngine(loader, nowMillis)
    }
}
