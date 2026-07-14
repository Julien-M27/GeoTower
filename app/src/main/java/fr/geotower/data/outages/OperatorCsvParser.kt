package fr.geotower.data.outages

import fr.geotower.data.build.AnfrCsvParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream

/**
 * Parse un CSV de pannes opérateur (octets bruts) en lignes à clés normalisées, prêtes pour
 * [SitesHsLocalBuilder]. Port de `read_operator_csv` (`docs/server/build_sites_hs.py`) :
 * détection d'encodage, saut du préambule éventuel, séparateur `;`, guillemets.
 *
 * Réutilise l'infra CSV existante ([AnfrCsvParser.detectCharset]/[AnfrCsvParser.splitLine]) pour ne
 * pas réimplémenter le décodage charset ni la gestion des champs quotés.
 */
internal object OperatorCsvParser {

    fun parse(rawBytes: ByteArray): List<Map<String, String?>> {
        if (rawBytes.isEmpty()) return emptyList()
        val lines = decode(rawBytes).split(Regex("\r\n|\r|\n"))
        if (lines.isEmpty()) return emptyList()

        val headerIndex = headerIndex(lines)
        val delimiter = AnfrCsvParser.detectDelimiter(lines[headerIndex])
        val header = AnfrCsvParser.splitLine(lines[headerIndex], delimiter).map { normalizedHeader(it) }

        val rows = ArrayList<Map<String, String?>>()
        for (i in headerIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val values = AnfrCsvParser.splitLine(line, delimiter)
            val row = HashMap<String, String?>(header.size)
            header.forEachIndexed { index, key ->
                if (key.isNotEmpty()) row[key] = values.getOrNull(index)
            }
            if (row.values.any { cleanText(it) != null }) rows.add(row)
        }
        return rows
    }

    /** Ligne d'en-tête = première ligne contenant `code_site_op` ou `code_si` (saute un préambule). */
    private fun headerIndex(lines: List<String>): Int {
        lines.forEachIndexed { index, line ->
            val normalized = normalizedHeader(line)
            if ("code_site_op" in normalized || "code_si" in normalized) return index
        }
        return 0
    }

    private fun decode(bytes: ByteArray): String {
        val charset = BufferedInputStream(ByteArrayInputStream(bytes), 65536 * 2).use { buffered ->
            AnfrCsvParser.detectCharset(buffered)
        }
        return String(bytes, charset)
    }
}
