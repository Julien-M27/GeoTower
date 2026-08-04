package fr.geotower.utils

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * Aides au défilement activables page par page : barre latérale glissable et boutons
 * « haut de page » / « bas de page ».
 *
 * Une clé booléenne par (aide, page) — `<préfixe>_page_<id>` — désactivée par défaut : ce sont
 * des ajouts optionnels, l'affichage historique reste inchangé tant que rien n'est activé. Seule
 * exception : la page « À proximité » avait déjà ses deux boutons en dur, ils restent donc actifs
 * par défaut (voir [defaultEnabled]).
 *
 * Les pages de [customizablePages] ont leurs propres interrupteurs dans « Personnalisation des
 * pages » ; celles de [otherPages] n'en ont pas et ne sont touchées que par l'interrupteur global
 * (qui, lui, écrit toutes les pages d'un coup).
 *
 * L'état est miroité dans une map observable pour que les écrans se recomposent dès que le
 * réglage change, sans relire les préférences.
 */
object PageScrollPrefs {
    const val HOME = "home"
    const val NEARBY = "nearby"
    const val COMPASS = "compass"
    const val STATS = "stats"
    const val SUPPORT = "support"
    const val SITE = "site"
    const val SPEEDTESTS = "speedtests"
    const val COVERAGE = "coverage"
    const val ELEVATION_PROFILE = "elevation_profile"
    const val THROUGHPUT_CALCULATOR = "throughput_calculator"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val HELP = "help"
    const val DIAGNOSTIC = "diagnostic"
    const val TERMS = "terms"
    const val PHOTOS_FAVORITES = "photos_favorites"
    const val HISTORIES = "histories"
    const val PHOTO_UPLOAD_HISTORY = "photo_upload_history"
    const val SHARE_HISTORY = "share_history"
    const val DEPARTMENT_STATS = "department_stats"

    /** Les trois aides réglables indépendamment (l'ordre est celui affiché dans les réglages). */
    enum class Aid(val keyPrefix: String) {
        /** Barre latérale de défilement, glissable. */
        BAR("scrollbar"),

        /** Bouton « remonter tout en haut ». */
        TOP("scroll_top"),

        /** Bouton « descendre tout en bas ». */
        BOTTOM("scroll_bottom")
    }

    /** Pages avec leurs propres interrupteurs dans « Personnalisation des pages ». */
    val customizablePages = listOf(
        HOME,
        NEARBY,
        COMPASS,
        STATS,
        SUPPORT,
        SITE,
        SPEEDTESTS,
        COVERAGE,
        ELEVATION_PROFILE,
        THROUGHPUT_CALCULATOR,
        ABOUT,
        DEPARTMENT_STATS
    )

    /**
     * Pages absentes de « Personnalisation des pages » : l'interrupteur global les touche toujours,
     * et les trois pages d'historiques ont en plus leur propre roue dentée dans leur barre du haut.
     */
    val otherPages = listOf(
        SETTINGS,
        HELP,
        DIAGNOSTIC,
        TERMS,
        PHOTOS_FAVORITES,
        HISTORIES,
        PHOTO_UPLOAD_HISTORY,
        SHARE_HISTORY
    )

    val allPages = customizablePages + otherPages

    fun key(aid: Aid, page: String): String = "${aid.keyPrefix}_page_$page"

    /**
     * Défaut d'usine. Tout est désactivé, sauf les deux boutons de la page « À proximité » qui
     * existaient déjà en dur avant que le réglage n'apparaisse : les retirer silencieusement
     * serait une régression pour les utilisateurs en place.
     */
    fun defaultEnabled(aid: Aid, page: String): Boolean =
        page == NEARBY && (aid == Aid.TOP || aid == Aid.BOTTOM)

    private val states = mutableStateMapOf<String, Boolean>()

    /** Recharge tout depuis les préférences (démarrage + application d'un profil). */
    fun load(prefs: SharedPreferences) {
        Aid.entries.forEach { aid ->
            allPages.forEach { page ->
                val prefKey = key(aid, page)
                states[prefKey] = prefs.getBoolean(prefKey, defaultEnabled(aid, page))
            }
        }
    }

    /** Lecture observable : appelée depuis un composable, elle déclenche la recomposition. */
    fun isEnabled(aid: Aid, page: String): Boolean =
        states[key(aid, page)] ?: defaultEnabled(aid, page)

    fun set(prefs: SharedPreferences, aid: Aid, page: String, enabled: Boolean) {
        val prefKey = key(aid, page)
        states[prefKey] = enabled
        prefs.edit().putBoolean(prefKey, enabled).apply()
    }

    /** Interrupteur global d'une aide : toutes les pages d'un coup, réglage dédié ou non. */
    fun setAll(prefs: SharedPreferences, aid: Aid, enabled: Boolean) {
        val editor = prefs.edit()
        allPages.forEach { page ->
            val prefKey = key(aid, page)
            states[prefKey] = enabled
            editor.putBoolean(prefKey, enabled)
        }
        editor.apply()
    }

    /** Vrai quand toutes les pages ont cette aide : sert d'état de l'interrupteur global. */
    fun allEnabled(aid: Aid): Boolean = allPages.all { isEnabled(aid, it) }
}
