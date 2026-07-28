package fr.geotower.data.build

/**
 * En-tete d'un fichier CSV / TXT ANFR : resolution `nom de colonne -> index`, calculee **une fois
 * par fichier** et partagee par toutes ses lignes.
 *
 * PERF : c'est le chemin le plus chaud de la generation locale — SUP_BANDE, SUP_EMETTEUR,
 * SUP_ANTENNE et l'observatoire totalisent des dizaines de millions de lignes. La version
 * precedente reconstruisait une `HashMap` par ligne en re-`trim()`/`lowercase()`-ant les memes
 * noms de colonnes a chaque fois ; tout ce travail est desormais fait une seule fois.
 *
 * Les fichiers hebdomadaires ANFR ont des entetes en minuscules et les mensuels en majuscules :
 * la normalisation en minuscules unifie les deux. Les graphies employees par les appelants (le
 * builder mobile interroge `sta_nm_anfr`, le sink radio `STA_NM_ANFR`) sont memorisees au premier
 * appel pour que les millions d'acces suivants soient un simple acces de table.
 *
 * Non thread-safe : un en-tete appartient a un flux de lecture, lui-meme sequentiel.
 */
class AnfrCsvHeader private constructor(
    private val indexByName: HashMap<String, Int>,
    val columnCount: Int,
) {

    /** Index de `column` dans la ligne, ou -1 si la colonne est absente du fichier. */
    fun indexOf(column: String): Int {
        indexByName[column]?.let { return it }
        // Graphie encore inconnue : normalisee une seule fois, puis memorisee (y compris
        // l'absence, -1) pour que les appels suivants tombent sur le chemin direct ci-dessus.
        val index = indexByName[column.trim().lowercase()] ?: -1
        indexByName[column] = index
        return index
    }

    companion object {
        /** En cas de noms dupliques, la derniere colonne gagne (comportement de la map d'origine). */
        fun of(names: List<String>): AnfrCsvHeader {
            val map = HashMap<String, Int>(names.size * 2)
            names.forEachIndexed { index, name -> map[name.trim().lowercase()] = index }
            return AnfrCsvHeader(map, names.size)
        }
    }
}

/**
 * Ligne CSV / TXT ANFR, avec acces par nom de colonne insensible a la casse et aux espaces de
 * bord (equivalent de `csv.DictReader` + `row_lower` du builder serveur).
 *
 * La ligne ne porte qu'un tableau de valeurs ; la resolution des noms est deleguee a
 * [AnfrCsvHeader], partage par tout le fichier.
 */
class AnfrCsvRow private constructor(
    private val header: AnfrCsvHeader,
    private val cells: Array<String?>,
) {

    fun get(column: String): String? {
        val index = header.indexOf(column)
        return if (index < 0 || index >= cells.size) null else cells[index]
    }

    companion object {
        /** Ligne isolee (tests, petites sources) : construit un en-tete dedie. */
        fun of(values: Map<String, String?>): AnfrCsvRow {
            val names = ArrayList<String>(values.size)
            val cells = arrayOfNulls<String>(values.size)
            for ((index, entry) in values.entries.withIndex()) {
                names.add(entry.key)
                cells[index] = entry.value
            }
            return AnfrCsvRow(AnfrCsvHeader.of(names), cells)
        }

        fun of(header: List<String>, cells: List<String?>): AnfrCsvRow =
            of(AnfrCsvHeader.of(header), cells)

        /** Variante du parseur : l'en-tete est partage par toutes les lignes du fichier. */
        internal fun of(header: AnfrCsvHeader, cells: List<String?>): AnfrCsvRow {
            val array = arrayOfNulls<String>(header.columnCount)
            val count = minOf(header.columnCount, cells.size)
            for (index in 0 until count) array[index] = cells[index]
            return AnfrCsvRow(header, array)
        }

        /** Variante sans copie : `cells` doit avoir exactement `header.columnCount` cases. */
        internal fun of(header: AnfrCsvHeader, cells: Array<String?>): AnfrCsvRow =
            AnfrCsvRow(header, cells)
    }
}
