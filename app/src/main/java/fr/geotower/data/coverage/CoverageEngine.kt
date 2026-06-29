package fr.geotower.data.coverage

/** Site résolu : position + opérateur + antennes prêtes au calcul. */
data class ResolvedSite(
    val idAnfr: String,
    val latitude: Double,
    val longitude: Double,
    val operator: String?,
    val antennas: List<AntennaSpec>
)

/**
 * Orchestre le calcul de couverture d'un site :
 *  1) charge le terrain **une fois** (mutualisé) via [TerrainFieldLoader] ;
 *  2) résout la visibilité **par antenne** (CPU, zéro réseau supplémentaire).
 *
 * L'horloge est injectée ([nowMillis]) : domaine sans dépendance à `System.currentTimeMillis()`.
 */
class CoverageEngine(
    private val loader: TerrainFieldLoader,
    private val nowMillis: () -> Long
) {
    suspend fun compute(
        site: ResolvedSite,
        request: CoverageRequest,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): SiteCoverage {
        if (site.antennas.isEmpty()) {
            return SiteCoverage(site.idAnfr, site.latitude, site.longitude, site.operator, emptyList(), request, nowMillis())
        }
        val field = loader.load(
            siteLat = site.latitude,
            siteLon = site.longitude,
            maxRadiusM = request.maxRadiusM,
            angularStepDeg = request.angularStepDeg,
            sampleStepM = request.sampleStepM,
            includeObstacles = request.includeObstacles,
            onProgress = onProgress
        )
        val sectors = site.antennas.map { ant ->
            ViewshedSolver.solveSector(field, ant, request.viewshed)
        }
        return SiteCoverage(
            site.idAnfr, site.latitude, site.longitude, site.operator, sectors, request, nowMillis(),
            field.validPointFraction, field.failedChunks, field.totalChunks, field.sampleError
        )
    }
}
