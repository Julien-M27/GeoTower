package fr.geotower.data.outages

import fr.geotower.data.models.SiteHsEntity

/** Résultat d'un géocodage : la station ANFR retrouvée, la distance et le type d'appariement. */
data class GeocodeMatch(
    val stationAnfr: String,
    val distanceMeters: Double,
    val matchType: String,
)

/**
 * Abstraction du géocodage d'une panne opérateur vers un site ANFR.
 *
 * La T1 la teste avec une implémentation en mémoire ; la T2 en fournira une adossée à la base ANFR
 * locale (`localisation` + `ref_operateur`, index `code_insee` et `(latitude, longitude)`).
 */
interface SiteGeocoder {
    /**
     * Retrouve la meilleure station ANFR du même opérateur pour la position donnée : d'abord dans
     * le même code INSEE (seuil [sameInseeThresholdMeters]), sinon la plus proche
     * (seuil [spatialThresholdMeters]). Renvoie null si aucune n'est assez proche.
     */
    suspend fun bestMatch(
        operatorKey: String,
        codeInsee: String?,
        lat: Double,
        lon: Double,
        sameInseeThresholdMeters: Double,
        spatialThresholdMeters: Double,
    ): GeocodeMatch?
}

/** Compteurs de diagnostic, miroir du rapport d'enrichissement du builder serveur. */
data class OutageBuildStats(
    val operatorRows: MutableMap<String, Int> = mutableMapOf(),
    val stationFromOperator: MutableMap<String, Int> = mutableMapOf(),
    val stationFromDb: MutableMap<String, Int> = mutableMapOf(),
    val withoutStation: MutableMap<String, Int> = mutableMapOf(),
    val duplicatesMerged: MutableMap<String, Int> = mutableMapOf(),
    var outputFeatures: Int = 0,
)

data class SitesHsBuildResult(
    val sites: List<SiteHsEntity>,
    val stats: OutageBuildStats,
)

/**
 * Construit la liste des pannes à partir des lignes CSV opérateurs (déjà parsées, clés normalisées)
 * et d'un [SiteGeocoder]. Port Kotlin du cœur de `build_sites_hs.py` : normalisation → géocodage
 * des lignes sans station → déduplication/fusion → [SiteHsEntity].
 *
 * Ne fait NI réseau NI accès base : ces responsabilités sont injectées (rows + geocoder), ce qui
 * rend cette classe entièrement testable hors device.
 */
class SitesHsLocalBuilder(
    private val sameInseeThresholdMeters: Double = DEFAULT_SAME_INSEE_MATCH_M,
    private val spatialThresholdMeters: Double = DEFAULT_SPATIAL_MATCH_M,
) {
    suspend fun build(
        rowsBySource: Map<OperatorOutageSource, List<Map<String, String?>>>,
        geocoder: SiteGeocoder,
        sourceLastUpdate: String? = null,
        onProgress: OutageProgressCallback = NoOutageProgress,
    ): SitesHsBuildResult {
        val stats = OutageBuildStats()
        // LinkedHashMap : ordre d'insertion stable → sortie déterministe.
        val records = LinkedHashMap<String, OutageRecord>()
        val totalRows = rowsBySource.values.sumOf { it.size }
        var processed = 0

        for ((source, rows) in rowsBySource) {
            var kept = 0
            for (row in rows) {
                processed++
                if (processed % GEOCODE_PROGRESS_EVERY == 0 || processed == totalRows) {
                    val percent = DOWNLOAD_PERCENT_END +
                        if (totalRows > 0) processed * (GEOCODE_PERCENT_END - DOWNLOAD_PERCENT_END) / totalRows else 0
                    onProgress(OutageGenerationStep.GEOCODE, percent, "$processed/$totalRows")
                }
                val base = normalizeOperatorRow(source, row) ?: continue
                kept++

                val record = if (!base.stationAnfr.isNullOrEmpty()) {
                    stats.stationFromOperator.increment(source.key)
                    base.copy(matchType = "operator")
                } else {
                    val match = geocoder.bestMatch(
                        operatorKey = base.operatorKey,
                        codeInsee = base.codeInsee,
                        lat = base.lat,
                        lon = base.lon,
                        sameInseeThresholdMeters = sameInseeThresholdMeters,
                        spatialThresholdMeters = spatialThresholdMeters,
                    )
                    if (match != null) {
                        stats.stationFromDb.increment(source.key)
                        base.copy(
                            stationAnfr = match.stationAnfr,
                            matchType = match.matchType,
                            matchDistanceMeters = match.distanceMeters,
                        )
                    } else {
                        stats.withoutStation.increment(source.key)
                        base
                    }
                }

                val key = dedupKey(record)
                val existing = records[key]
                if (existing != null) {
                    records[key] = mergeOutage(existing, record)
                    stats.duplicatesMerged.increment(source.key)
                } else {
                    records[key] = record
                }
            }
            stats.operatorRows[source.key] = kept
        }

        val sites = records.values.map { it.toSiteHsEntity(sourceLastUpdate) }
        stats.outputFeatures = sites.size
        return SitesHsBuildResult(sites, stats)
    }

    companion object {
        /** Seuils calibrés sur les données réelles (cf. builder serveur, balayage 2026-07-13). */
        const val DEFAULT_SAME_INSEE_MATCH_M = 600.0
        const val DEFAULT_SPATIAL_MATCH_M = 400.0

        /** Fréquence d'émission de la progression pendant le géocodage (une notif toutes les N lignes). */
        private const val GEOCODE_PROGRESS_EVERY = 100
    }
}

private fun MutableMap<String, Int>.increment(key: String) {
    this[key] = (this[key] ?: 0) + 1
}
