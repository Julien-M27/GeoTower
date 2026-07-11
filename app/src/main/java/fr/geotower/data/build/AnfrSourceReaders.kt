package fr.geotower.data.build

import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Lecture en flux des sources ANFR brutes (CSV hebdomadaire + fichiers du ZIP mensuel) vers
 * des `Iterable<AnfrCsvRow>` consommables par [GeoTowerDbBuilder].
 *
 * Discipline disque : le ZIP mensuel n'est PAS decompresse entierement ; chaque fichier est lu
 * en flux, a la demande, via [ZipFile.getInputStream]. Chaque source n'est parcourue qu'une
 * fois par le builder.
 */
object AnfrCsvParser {

    private const val BOM = 0xFEFF

    /**
     * Parcourt un flux CSV/TXT : 1re ligne = entete, puis lignes de donnees. Ferme le flux en fin.
     * Si `delimiter` est null, le separateur est **devine** d'apres l'entete (`;`, `,` ou tab) —
     * indispensable car l'export d4c de l'observatoire ANFR n'utilise pas toujours `;`.
     */
    fun iterator(input: InputStream, delimiter: Char? = null): Iterator<AnfrCsvRow> =
        CsvRowIterator(input.bufferedReader(Charsets.UTF_8), delimiter)

    /** Devine le separateur d'apres l'entete. */
    fun detectDelimiter(header: String?): Char {
        if (header.isNullOrEmpty()) return ';'
        val semicolons = header.count { it == ';' }
        val commas = header.count { it == ',' }
        val tabs = header.count { it == '\t' }
        return when {
            tabs > semicolons && tabs > commas -> '\t'
            commas > semicolons -> ','
            else -> ';'
        }
    }

    /** Decoupe une ligne en champs, en gerant les guillemets doubles (`"..."`, `""` = guillemet echappe). */
    fun splitLine(line: String, delimiter: Char = ';'): List<String> {
        val result = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes -> {
                    if (char == '"') {
                        if (index + 1 < line.length && line[index + 1] == '"') {
                            field.append('"')
                            index++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(char)
                    }
                }
                char == '"' -> inQuotes = true
                char == delimiter -> {
                    result.add(field.toString())
                    field.setLength(0)
                }
                else -> field.append(char)
            }
            index++
        }
        result.add(field.toString())
        return result
    }

    private class CsvRowIterator(
        private val reader: BufferedReader,
        requestedDelimiter: Char?,
    ) : Iterator<AnfrCsvRow>, Closeable {

        private val delimiter: Char
        private val header: List<String>
        private var pendingLine: String?

        init {
            var first = reader.readLine()
            // Retire un eventuel BOM UTF-8 en tete d'entete.
            if (first != null && first.isNotEmpty() && first[0].code == BOM) first = first.substring(1)
            delimiter = requestedDelimiter ?: detectDelimiter(first)
            header = first?.let { splitLine(it, delimiter) } ?: emptyList()
            pendingLine = if (header.isEmpty()) null else reader.readLine()
        }

        override fun hasNext(): Boolean {
            if (pendingLine == null) {
                close()
                return false
            }
            return true
        }

        override fun next(): AnfrCsvRow {
            val line = pendingLine ?: throw NoSuchElementException()
            pendingLine = reader.readLine()
            return AnfrCsvRow.of(header, splitLine(line, delimiter))
        }

        override fun close() {
            try {
                reader.close()
            } catch (_: Exception) {
                // Flux deja ferme : sans consequence.
            }
        }
    }
}

/** `Iterable<AnfrCsvRow>` paresseux : ouvre un flux neuf a chaque `iterator()`. Separateur devine si null. */
fun csvRows(delimiter: Char? = null, open: () -> InputStream): Iterable<AnfrCsvRow> =
    Iterable { AnfrCsvParser.iterator(open(), delimiter) }

/**
 * ZIP mensuel ANFR ouvert en acces aleatoire. A garder ouvert ([Closeable]) pendant toute la
 * construction, car les `Iterable` retournes lisent leurs entrees en flux a la demande.
 */
class AnfrMonthlyZip(file: File) : Closeable {
    private val zip = ZipFile(file)

    /** Lignes d'un fichier du ZIP (variantes .txt/.csv acceptees). Vide si absent. */
    fun rows(vararg nameCandidates: String): Iterable<AnfrCsvRow> {
        val entry = findEntry(nameCandidates) ?: return emptyList()
        return csvRows { zip.getInputStream(entry) }
    }

    /** Charge un referentiel `id -> libelle` (petit fichier lu entierement). Vide si absent. */
    fun reference(keyColumn: String, valueColumn: String, vararg nameCandidates: String): Map<String, String> {
        val entry = findEntry(nameCandidates) ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        zip.getInputStream(entry).use { input ->
            AnfrCsvParser.iterator(input).forEach { row ->
                val key = row.get(keyColumn)?.trim()
                val value = row.get(valueColumn)?.trim()
                if (!key.isNullOrEmpty() && !value.isNullOrEmpty()) result[key] = value
            }
        }
        return result
    }

    /**
     * Recherche tolerante : compare les **radicaux** (nom sans dossier ni extension) et accepte
     * qu'une entree CONTIENNE le radical voulu. Gere ainsi les prefixes/suffixes de date
     * (`20260630_SUP_STATION.txt`), les extensions `.txt`/`.csv` et les sous-dossiers.
     */
    private fun findEntry(nameCandidates: Array<out String>): ZipEntry? {
        val stems = nameCandidates
            .map { it.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').lowercase() }
            .filter { it.isNotEmpty() }
        return zip.entries().asSequence().firstOrNull { entry ->
            if (entry.isDirectory) return@firstOrNull false
            val stem = entry.name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').lowercase()
            stems.any { stem == it || stem.contains(it) }
        }
    }

    override fun close() = zip.close()
}

/** Assemble les six sources du builder depuis le CSV hebdomadaire et le ZIP mensuel ouvert. */
fun anfrSourcesFrom(weeklyCsv: File, monthlyZip: AnfrMonthlyZip): AnfrSources = AnfrSources(
    weekly = csvRows { FileInputStream(weeklyCsv) },
    stations = monthlyZip.rows("SUP_STATION.txt"),
    bandes = monthlyZip.rows("SUP_BANDE.txt"),
    emetteurs = monthlyZip.rows("SUP_EMETTEUR.txt"),
    antennes = monthlyZip.rows("SUP_ANTENNE.txt"),
    supports = monthlyZip.rows("SUP_SUPPORT.txt"),
)

/** Charge les referentiels `id -> libelle` du ZIP mensuel ; `communes` vient de geo.api.gouv.fr (Slice 3). */
fun anfrReferencesFrom(monthlyZip: AnfrMonthlyZip, communes: Map<String, String> = emptyMap()): AnfrReferences =
    AnfrReferences(
        nature = monthlyZip.reference("nat_id", "nat_lb_nom", "SUP_NATURE.txt"),
        proprietaire = monthlyZip.reference("tpo_id", "tpo_lb", "SUP_PROPRIETAIRE.txt"),
        exploitant = monthlyZip.reference("adm_id", "adm_lb_nom", "SUP_EXPLOITANT.txt"),
        typeAntenne = monthlyZip.reference("tae_id", "tae_lb", "SUP_TYPE_ANTENNE.txt"),
        communes = communes,
    )
