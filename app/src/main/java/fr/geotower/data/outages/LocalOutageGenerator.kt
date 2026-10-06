package fr.geotower.data.outages

import fr.geotower.data.db.GeoTowerDao
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppConfig
import java.io.IOException

/**
 * Orchestrateur de la génération LOCALE des pannes :
 * - Mode [OutageSourceMode.OPERATORS] : télécharge les CSV opérateurs, géocode via la base ANFR locale,
 *   produit la `List<SiteHsEntity>`. Équivalent app de `build_sites_hs.py`.
 * - Mode [OutageSourceMode.DAILY] : télécharge le snapshot quotidien consolidé officiel Arcep (data.gouv.fr).
 */
class LocalOutageGenerator(
    private val fetcher: OperatorOutageFetcher,
    private val geocoder: SiteGeocoder,
    private val builder: SitesHsLocalBuilder = SitesHsLocalBuilder(),
    private val dailyFetcher: DailyOutageFetcher = DailyOutageFetcher.real(),
    private val sourceModeProvider: () -> OutageSourceMode = { AppConfig.outageSourceMode.value },
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
        if (sourceModeProvider() == OutageSourceMode.DAILY) {
            val dailyResult = dailyFetcher.fetch(onProgress)
            return GenerationResult(
                sites = dailyResult.sites,
                stats = dailyResult.stats,
                downloadErrors = emptyMap(),
            )
        }

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
        fun create(
            dao: GeoTowerDao,
            sourceModeProvider: () -> OutageSourceMode = { AppConfig.outageSourceMode.value },
        ): LocalOutageGenerator = LocalOutageGenerator(
            fetcher = OperatorOutageFetcher.real(),
            geocoder = AnfrDbSiteGeocoder(DaoOutageSiteSource(dao)),
            dailyFetcher = DailyOutageFetcher.real(),
            sourceModeProvider = sourceModeProvider,
        )
    }
}
