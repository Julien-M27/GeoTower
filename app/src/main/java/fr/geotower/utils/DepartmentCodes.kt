package fr.geotower.utils

/**
 * Département d'un code INSEE communal.
 *
 * Miroir exact de `department_code()` de docs/server/fr_dept_stats.py : c'est ce découpage qui a
 * servi à agréger `dept_stat_current`, donc toute divergence ferait pointer l'app sur un
 * département inexistant dans la table.
 */
object DepartmentCodes {

    /**
     * `72181` → `72`, `2A004` → `2A`, `97411` → `974`, `98735` → `987`.
     * Renvoie null si le code n'est pas exploitable.
     */
    fun fromInsee(codeInsee: String?): String? {
        var code = codeInsee?.trim()?.uppercase() ?: return null
        if (code.isEmpty()) return null
        // Filet de sécurité : certains exports perdent le zéro de tête ('1004' = '01004').
        if (code.length == 4 && code.all { it.isDigit() }) code = "0$code"
        if (code.length != 5) return null

        val prefix = code.take(2)
        if (prefix == "2A" || prefix == "2B") return prefix
        if (!code.all { it.isDigit() }) return null
        // Outre-mer : le code département tient sur trois chiffres (971 à 988).
        if (prefix == "97" || prefix == "98") return code.take(3)
        return prefix
    }
}
