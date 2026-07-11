package fr.geotower.data.coverage

import fr.geotower.data.AnfrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Résout un site (position + opérateur + antennes + hauteur de repli) puis calcule sa couverture.
 * Logique partagée entre l'overlay carte ([fr.geotower.ui.screens.map.MapViewModel]) et l'écran-outil
 * dédié, pour qu'ils produisent exactement le même résultat à paramètres égaux.
 *
 * Tout le corps s'exécute sur [Dispatchers.Default] : le calcul CPU (résolution des émetteurs, grille
 * terrain, viewshed par antenne) ne doit jamais tourner sur le thread principal (jank/ANR). Les accès
 * réseau internes rebasculent d'eux-mêmes sur [Dispatchers.IO] (cf. CoverageEngineFactory) et les DAO
 * Room `suspend` gèrent leur propre executor — les envelopper ici ne les bloque pas.
 */
object CoverageComputer {
    suspend fun compute(
        repository: AnfrRepository,
        idAnfr: String,
        request: CoverageRequest,
        maxPointsPerRequest: Int,
        maxConcurrentRequests: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): SiteCoverage? = withContext(Dispatchers.Default) {
        val site = repository.getCoverageSiteLocation(idAnfr) ?: return@withContext null
        val antennas = repository.getAntennesForCoverage(idAnfr)
        val typeLabels = repository.getAntennaTypes().associate { it.taeId to it.libelle }
        // hauteur connue des antennes, sinon hauteur du support (structure), sinon 15 m.
        val fallbackHeightM = antennas.mapNotNull { it.hauteurBas }.filter { it > 0.0 }.maxOrNull()
            ?: site.supportHeight?.takeIf { it > 0.0 }
            ?: 15.0
        val specs = SiteEmitterResolver.resolve(
            antennas = antennas,
            typeLabels = typeLabels,
            operator = site.operateur,
            defaultFrequencyMHz = request.viewshed.frequencyMHz,
            viewshed = request.viewshed,
            fallbackHeightM = fallbackHeightM
        )
        val resolved = ResolvedSite(idAnfr, site.latitude, site.longitude, site.operateur, specs)
        val engine = CoverageEngineFactory.create(
            maxPointsPerRequest = maxPointsPerRequest,
            maxConcurrentRequests = maxConcurrentRequests
        )
        engine.compute(resolved, request, onProgress)
    }
}
