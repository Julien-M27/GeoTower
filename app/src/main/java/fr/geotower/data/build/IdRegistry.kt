package fr.geotower.data.build

/**
 * Port de la classe `IdRegistry` de build_fr_anfr_db.py : attribue un identifiant entier
 * sequentiel a chaque libelle par ordre de premiere rencontre (cle = libelle en majuscules).
 * Ces identifiants n'ont pas besoin de correspondre a ceux du serveur : ils doivent seulement
 * etre coherents a l'interieur de la base (l'app resout tout via les tables `ref_*`).
 */
class IdRegistry {
    private val ids = HashMap<String, Int>()
    private val labels = HashMap<Int, String>()
    private var next = 1

    fun getId(label: String?, default: String = "Inconnu"): Int {
        val value = AnfrParsing.cleanText(label).ifEmpty { default }
        val key = value.uppercase()
        ids[key]?.let { return it }
        val id = next++
        ids[key] = id
        labels[id] = value
        return id
    }

    fun getLabel(id: Int, default: String = "Inconnu"): String = labels[id] ?: default

    /** Lignes (id, libelle) triees par id, pour l'insertion dans une table `ref_*`. */
    fun rows(): List<Pair<Int, String>> = labels.entries.sortedBy { it.key }.map { it.key to it.value }
}
