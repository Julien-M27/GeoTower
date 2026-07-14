package fr.geotower.data.outages

import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.SiteHsEntity
import java.io.IOException

/**
 * Orchestrateur de la génération LOCALE des pannes : télécharge les CSV opérateurs, géocode via la
 * base ANFR locale, produit la `List<SiteHsEntity>`. Équivalent app de `build_sites_hs.py`.
 *
 * Le branchement (switch source, cache/TTL, déclenchement) relève de la tranche UI (T4) ; ici on
 * expose une génération « à la demande ».
 */
class LocalOutageGenerator(
    private val fetcher: OperatorOutageFetcher,
    private val geocoder: SiteGeocoder,
    private val builder: SitesHsLocalBuilder = SitesHsLocalBuilder(),
) {
    data class GenerationResult(
        val sites: List<SiteHsEntity>,
        val stats: OutageBuildStats,
        val downloadErrors: Map<OperatorOutageSource, String>,
    )

    suspend fun generate(
        sourceLastUpdate: String? = null,
        onProgress: OutageProgressCallback = NoOutageProgress,
    ): GenerationResult {
        val fetch = fetcher.fetchAll(onProgress)
        // Garde-fou : si AUCUN opérateur n'a répondu, ne rien produire (l'appelant conserve l'existant).
        if (fetch.rowsBySource.isEmpty()) {
            throw IOException(
                "Aucun fichier opérateur accessible: " +
                    fetch.downloadErrors.entries.joinToString(" | ") { "${it.key.label}: ${it.value}" },
            )
        }
        val result = builder.build(fetch.rowsBySource, geocoder, sourceLastUpdate, onProgress)
        return GenerationResult(result.sites, result.stats, fetch.downloadErrors)
    }

    companion object {
        /** Génération réelle adossée à la base ANFR locale ([dao]) et aux téléchargements HTTPS. */
        fun create(dao: GeoTowerDao): LocalOutageGenerator = LocalOutageGenerator(
            fetcher = OperatorOutageFetcher.real(),
            geocoder = AnfrDbSiteGeocoder(DaoOutageSiteSource(dao)),
        )
    }
}
