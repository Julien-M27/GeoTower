package fr.geotower.utils

import android.content.SharedPreferences
import java.util.Locale

/**
 * Personnalisation de la page « Statistiques par département » : ordre et visibilité des blocs
 * de la fiche, plus les options d'affichage de la liste.
 *
 * Toutes les clés sont préfixées `page_`, comme les autres pages : c'est ce préfixe qui les fait
 * entrer dans les profils de préférences ([PreferenceProfileManager]).
 */
object DepartmentStatsPreferences {

    /** Bloc des compteurs et ratios du département. */
    const val BLOCK_AUTHORISATIONS = "authorisations"

    /** Bloc du tableau opérateur × technologie. */
    const val BLOCK_OPERATORS = "operators"

    const val PREF_ORDER = "page_department_stats_order"

    /** Champ de recherche en tête de la liste des départements. */
    const val PREF_SHOW_SEARCH = "page_department_stats_search"

    /** Département de la dernière position connue, épinglé au-dessus de la liste. */
    const val PREF_SHOW_CURRENT = "page_department_stats_current"

    /** Ligne « x supports · y stations · z panneaux » sous chaque département. */
    const val PREF_SHOW_SUMMARY = "page_department_stats_summary"

    val defaultBlockOrder: List<String> = listOf(BLOCK_AUTHORISATIONS, BLOCK_OPERATORS)

    val listOptionKeys: List<String> = listOf(PREF_SHOW_SEARCH, PREF_SHOW_CURRENT, PREF_SHOW_SUMMARY)

    fun blockOrder(prefs: SharedPreferences): List<String> {
        val saved = prefs.getString(PREF_ORDER, defaultBlockOrder.joinToString(","))
        return normalizeBlockOrder(saved?.split(",").orEmpty())
    }

    fun isBlockVisible(prefs: SharedPreferences, blockId: String): Boolean =
        prefs.getBoolean(blockVisiblePrefKey(blockId), true)

    fun blockVisiblePrefKey(blockId: String): String =
        "page_department_stats_${blockId.trim().lowercase(Locale.ROOT)}"

    /** Les options d'affichage sont toutes actives par défaut, comme sur les pages d'historique. */
    fun isOptionEnabled(prefs: SharedPreferences, key: String): Boolean = prefs.getBoolean(key, true)

    fun setOptionEnabled(prefs: SharedPreferences, key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
    }

    /** Complète l'ordre enregistré avec les blocs manquants et jette ce qui n'existe plus. */
    fun normalizeBlockOrder(order: List<String>): List<String> {
        val known = defaultBlockOrder.associateBy { it.lowercase(Locale.ROOT) }
        val normalized = order
            .mapNotNull { known[it.trim().lowercase(Locale.ROOT)] }
            .distinct()
            .toMutableList()
        defaultBlockOrder.forEach { blockId ->
            if (!normalized.contains(blockId)) normalized.add(blockId)
        }
        return normalized
    }
}
