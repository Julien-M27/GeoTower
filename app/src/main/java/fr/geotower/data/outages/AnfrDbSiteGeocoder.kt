package fr.geotower.data.outages

import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.OutageGeocodeSiteRow

/**
 * Fournit les sites ANFR candidats au géocodage. Isolée du DAO pour tester la logique du géocodeur
 * hors de Room (le mapping SQL, lui, est trivial : deux `SELECT` indexés).
 */
interface OutageSiteSource {
    suspend fun sitesByInsee(codeInsee: String): List<OutageGeocodeSiteRow>
    suspend fun sitesInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<OutageGeocodeSiteRow>
}

/** Implémentation réelle adossée à la base ANFR locale via [GeoTowerDao]. */
class DaoOutageSiteSource(private val dao: GeoTowerDao) : OutageSiteSource {
    override suspend fun sitesByInsee(codeInsee: String): List<OutageGeocodeSiteRow> =
        dao.getGeocodeSitesByInsee(codeInsee)

    override suspend fun sitesInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<OutageGeocodeSiteRow> =
        dao.getGeocodeSitesInBox(minLat, maxLat, minLon, maxLon)
}

/**
 * Géocodeur réel : retrouve la station ANFR du même opérateur pour une panne, d'abord dans le même
 * code INSEE puis par proximité spatiale. Port de `DbSiteIndex.best_match` (`build_sites_hs.py`),
 * mais en requêtes indexées à la demande plutôt qu'en chargeant toute la table en RAM.
 *
 * Conçu pour un usage SÉQUENTIEL (une génération = une instance) : le cache INSEE n'est pas synchronisé.
 */
class AnfrDbSiteGeocoder(private val source: OutageSiteSource) : SiteGeocoder {

    private val inseeCache = HashMap<String, List<OutageGeocodeSiteRow>>()

    override suspend fun bestMatch(
        operatorKey: String,
        codeInsee: String?,
        lat: Double,
        lon: Double,
        sameInseeThresholdMeters: Double,
        spatialThresholdMeters: Double,
    ): GeocodeMatch? {
        // 1) Même commune (INSEE) : le plus fréquent, borné et indexé.
        if (codeInsee != null) {
            val candidates = inseeCache[codeInsee] ?: source.sitesByInsee(codeInsee).also { inseeCache[codeInsee] = it }
            nearest(operatorKey, lat, lon, candidates, sameInseeThresholdMeters, "db_insee")?.let { return it }
        }

        // 2) Repli spatial : boîte englobante ± seuil, puis plus proche site du bon opérateur.
        val deltaLat = spatialThresholdMeters / METERS_PER_DEGREE_LAT
        val cosLat = Math.cos(Math.toRadians(lat))
        val deltaLon = if (cosLat > 1e-9) spatialThresholdMeters / (METERS_PER_DEGREE_LAT * cosLat) else 180.0
        val candidates = source.sitesInBox(lat - deltaLat, lat + deltaLat, lon - deltaLon, lon + deltaLon)
        return nearest(operatorKey, lat, lon, candidates, spatialThresholdMeters, "db_spatial")
    }

    private fun nearest(
        targetOperatorKey: String,
        lat: Double,
        lon: Double,
        candidates: List<OutageGeocodeSiteRow>,
        thresholdMeters: Double,
        matchType: String,
    ): GeocodeMatch? {
        var best: GeocodeMatch? = null
        for (site in candidates) {
            if (operatorKey(site.operateur) != targetOperatorKey) continue
            val distance = haversineMeters(lat, lon, site.latitude, site.longitude)
            if (distance <= thresholdMeters && (best == null || distance < best.distanceMeters)) {
                best = GeocodeMatch(site.idAnfr, distance, matchType)
            }
        }
        return best
    }

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}
