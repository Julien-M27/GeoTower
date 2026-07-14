package fr.geotower.data.outages

import fr.geotower.data.build.RawSourceDownloader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Télécharge et parse les 4 CSV opérateurs → lignes normalisées prêtes pour [SitesHsLocalBuilder].
 *
 * Le téléchargement est injecté ([download]) pour rester testable ; l'implémentation réelle
 * ([real]) passe par [RawSourceDownloader] (donc l'allowlist `OfficialSources`). Chaque source qui
 * échoue est journalisée sans faire tomber les autres (miroir de `operator_download_errors`).
 */
class OperatorOutageFetcher(
    private val download: (url: String) -> ByteArray,
) {
    data class FetchResult(
        val rowsBySource: Map<OperatorOutageSource, List<Map<String, String?>>>,
        val downloadErrors: Map<OperatorOutageSource, String>,
    )

    suspend fun fetchAll(onProgress: OutageProgressCallback = NoOutageProgress): FetchResult = withContext(Dispatchers.IO) {
        val rows = LinkedHashMap<OperatorOutageSource, List<Map<String, String?>>>()
        val errors = LinkedHashMap<OperatorOutageSource, String>()
        val sources = OperatorOutageSource.entries
        sources.forEachIndexed { index, source ->
            onProgress(OutageGenerationStep.DOWNLOAD, index * DOWNLOAD_PERCENT_END / sources.size, source.label)
            try {
                val parsed = OperatorCsvParser.parse(download(source.url))
                if (parsed.isEmpty()) throw IOException("CSV vide ou non reconnu")
                rows[source] = parsed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors[source] = e.message ?: e.toString()
            }
        }
        FetchResult(rows, errors)
    }

    companion object {
        private const val DEFAULT_MAX_BYTES_PER_CSV = 8L * 1024 * 1024

        /** Implémentation réelle : téléchargement HTTPS borné par l'allowlist [fr.geotower.data.build.OfficialSources]. */
        fun real(
            downloader: RawSourceDownloader = RawSourceDownloader(),
            maxBytesPerCsv: Long = DEFAULT_MAX_BYTES_PER_CSV,
        ): OperatorOutageFetcher = OperatorOutageFetcher { url ->
            downloader.withStream(url) { readCapped(it, maxBytesPerCsv) }
        }

        private fun readCapped(input: InputStream, maxBytes: Long): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("CSV opérateur trop volumineux (> $maxBytes octets)")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
