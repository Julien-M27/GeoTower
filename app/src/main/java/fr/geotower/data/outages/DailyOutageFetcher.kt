package fr.geotower.data.outages

import fr.geotower.data.build.OfficialSources
import fr.geotower.data.build.RawSourceDownloader
import fr.geotower.data.models.SiteHsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Télécharge et traite le fichier journalier officiel consolidé des pannes publié par l'Arcep
 * sur data.gouv.fr (jeu de données « Sites indisponibles »).
 *
 * Le téléchargement est injecté ([downloadText] et [downloadBytes]) pour permettre des tests
 * unitaires rapides et fiables hors réseau.
 */
class DailyOutageFetcher(
    private val downloadText: (url: String) -> String,
    private val downloadBytes: (url: String) -> ByteArray,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class DailyFetchResult(
        val sites: List<SiteHsEntity>,
        val sourceDate: String?,
        val stats: OutageBuildStats,
    )

    suspend fun fetch(onProgress: OutageProgressCallback = NoOutageProgress): DailyFetchResult = withContext(Dispatchers.IO) {
        onProgress(OutageGenerationStep.DOWNLOAD, 10, "data.gouv.fr")

        // 1. Résolution de la ressource GeoJSON la plus récente
        val resource = try {
            val datasetJson = downloadText(OfficialSources.ARCEP_SITES_INDISPONIBLES_DATASET_API_URL)
            OfficialSources.selectLatestDailyOutageResource(datasetJson)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: run {
            // Repli direct sur le bucket Arcep pour aujourd'hui ou hier
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(clock()))
            val directUrl = "https://arcep.s3.rbx.io.cloud.ovh.net/sites-indisponibles/all/$today/raw$today.geojson"
            OfficialSources.DailyOutageResource(directUrl, today)
        }

        onProgress(OutageGenerationStep.DOWNLOAD, 40, resource.date ?: "Arcep")

        // 2. Téléchargement du GeoJSON
        val geoJsonBytes = try {
            downloadBytes(resource.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Impossible de télécharger le fichier journalier Arcep (${resource.url}): ${e.message}", e)
        }

        if (geoJsonBytes.isEmpty()) {
            throw IOException("Fichier journalier Arcep vide")
        }

        onProgress(OutageGenerationStep.GEOCODE, 70, "${geoJsonBytes.size / 1024} Ko")

        // 3. Parsing GeoJSON
        val geoJsonString = String(geoJsonBytes, Charsets.UTF_8)
        val parseResult = SitesHsGeoJsonParser.parse(geoJsonString, resource.date)

        val sites = parseResult.sites
        val stats = OutageBuildStats()

        for (site in sites) {
            val op = site.operateur
            stats.operatorRows[op] = (stats.operatorRows[op] ?: 0) + 1
            if (site.idAnfr.isNotBlank()) {
                stats.stationFromOperator[op] = (stats.stationFromOperator[op] ?: 0) + 1
            } else {
                stats.withoutStation[op] = (stats.withoutStation[op] ?: 0) + 1
            }
        }
        stats.outputFeatures = sites.size

        onProgress(OutageGenerationStep.FINALIZE, 95, null)
        DailyFetchResult(sites, resource.date, stats)
    }

    companion object {
        private const val MAX_DATASET_JSON_BYTES = 10L * 1024 * 1024
        private const val MAX_GEOJSON_BYTES = 20L * 1024 * 1024

        fun real(downloader: RawSourceDownloader = RawSourceDownloader()): DailyOutageFetcher =
            DailyOutageFetcher(
                downloadText = { url -> downloader.fetchText(url, MAX_DATASET_JSON_BYTES) },
                downloadBytes = { url ->
                    downloader.withStream(url) { stream -> readCapped(stream, MAX_GEOJSON_BYTES) }
                },
            )

        private fun readCapped(input: InputStream, maxBytes: Long): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("Fichier pannes trop volumineux (> $maxBytes octets)")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
