package fr.geotower.data.build

/**
 * Ligne CSV / TXT ANFR, avec acces par nom de colonne insensible a la casse et aux espaces
 * de bord (equivalent de `csv.DictReader` + `row_lower` du builder serveur). Les fichiers
 * hebdomadaires ANFR utilisent des entetes en minuscules, les fichiers mensuels en
 * majuscules : la normalisation en minuscules unifie les deux.
 */
class AnfrCsvRow private constructor(private val values: Map<String, String?>) {

    fun get(column: String): String? = values[column.trim().lowercase()]

    companion object {
        fun of(values: Map<String, String?>): AnfrCsvRow =
            AnfrCsvRow(values.entries.associate { it.key.trim().lowercase() to it.value })

        fun of(header: List<String>, cells: List<String?>): AnfrCsvRow {
            val map = HashMap<String, String?>(header.size)
            for (index in header.indices) {
                map[header[index].trim().lowercase()] = cells.getOrNull(index)
            }
            return AnfrCsvRow(map)
        }
    }
}
