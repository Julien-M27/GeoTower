package fr.geotower.utils

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf

/**
 * Mise en avant de « Personnalisation des pages ».
 *
 * La fonctionnalité existe depuis longtemps et chaque page concernée porte déjà sa roue dentée,
 * mais rien ne dit à l'utilisateur que cette roue sert à masquer et réordonner les blocs. Trois
 * relais de découverte, tous pilotés d'ici :
 *
 * 1. une bulle ancrée sous la roue dentée, montrée **une seule fois par page** à la
 *    [VISITS_BEFORE_HINT]ᵉ ouverture (assez tôt pour servir, assez tard pour ne pas s'ajouter à la
 *    charge du premier démarrage) ;
 * 2. un rappel discret en bas des pages qui défilent ;
 * 3. l'appui long sur un bloc, qui ouvre son réglage.
 *
 * Les deux premiers sont désactivables (l'appui long, lui, est un geste : il ne dérange personne).
 * Les identifiants de page sont ceux de [PageScrollPrefs], à l'exception de la carte qui n'y figure
 * pas — elle ne défile pas, mais elle a bien une roue dentée dans sa boîte à outils.
 *
 * L'état est miroité dans des états observables pour que les écrans se recomposent dès qu'un
 * réglage change, sans relire les préférences.
 */
object PageCustomizationPrefs {
    const val HINTS_ENABLED = "page_customization_hints"
    const val FOOTER_ENABLED = "page_customization_footer"
    private const val VISITS_PREFIX = "page_customization_visits_"
    private const val HINT_SHOWN_PREFIX = "page_customization_hint_shown_"

    /** Nombre d'ouvertures d'une page à partir duquel sa bulle apparaît. */
    const val VISITS_BEFORE_HINT = 2

    /**
     * Pages qui ont une roue dentée menant à leur propre panneau de personnalisation.
     *
     * La carte en est absente à dessein : sa roue dentée vit dans la boîte à outils repliable, donc
     * invisible à l'ouverture de la page. Désigner une icône que l'utilisateur ne voit pas n'aurait
     * aucun sens ; sa personnalisation reste accessible par les réglages.
     */
    val hintPages = listOf(
        PageScrollPrefs.NEARBY,
        PageScrollPrefs.COMPASS,
        PageScrollPrefs.STATS,
        PageScrollPrefs.SUPPORT,
        PageScrollPrefs.SITE,
        PageScrollPrefs.FREQUENCY_REFERENCE,
        PageScrollPrefs.SPEEDTESTS,
        PageScrollPrefs.COVERAGE,
        PageScrollPrefs.ELEVATION_PROFILE,
        PageScrollPrefs.THROUGHPUT_CALCULATOR,
        PageScrollPrefs.DEPARTMENT_STATS
    )

    val hintsEnabled = mutableStateOf(true)
    val footerEnabled = mutableStateOf(true)

    /** Bulles déjà vues, par page. Observable pour que « revoir les astuces » agisse à chaud. */
    private val hintShown = mutableStateMapOf<String, Boolean>()

    /** Recharge tout depuis les préférences (démarrage + application d'un profil). */
    fun load(prefs: SharedPreferences) {
        hintsEnabled.value = prefs.getBoolean(HINTS_ENABLED, true)
        footerEnabled.value = prefs.getBoolean(FOOTER_ENABLED, true)
        hintPages.forEach { page ->
            hintShown[page] = prefs.getBoolean(HINT_SHOWN_PREFIX + page, false)
        }
    }

    fun setHintsEnabled(prefs: SharedPreferences, enabled: Boolean) {
        hintsEnabled.value = enabled
        prefs.edit().putBoolean(HINTS_ENABLED, enabled).apply()
    }

    fun setFooterEnabled(prefs: SharedPreferences, enabled: Boolean) {
        footerEnabled.value = enabled
        prefs.edit().putBoolean(FOOTER_ENABLED, enabled).apply()
    }

    /**
     * Comptabilise une ouverture de [page] et dit si sa bulle doit s'afficher maintenant.
     *
     * Marque la bulle comme vue dès qu'elle est rendue, sans attendre que l'utilisateur en fasse
     * quelque chose : une astuce ignorée est une astuce refusée, la remontrer serait du harcèlement.
     */
    fun registerVisit(prefs: SharedPreferences, page: String): Boolean {
        if (!hintsEnabled.value) return false
        if (hintShown[page] == true) return false

        val visits = prefs.getInt(VISITS_PREFIX + page, 0) + 1
        val show = visits >= VISITS_BEFORE_HINT
        val editor = prefs.edit().putInt(VISITS_PREFIX + page, visits)
        if (show) {
            hintShown[page] = true
            editor.putBoolean(HINT_SHOWN_PREFIX + page, true)
        }
        editor.apply()
        return show
    }

    /** Vrai dès qu'une bulle a été vue : sert à n'offrir « revoir les astuces » que si ça a un sens. */
    fun hasSeenHints(): Boolean = hintPages.any { hintShown[it] == true }

    /** « Revoir les astuces » : remet les compteurs à zéro, les bulles réapparaîtront. */
    fun resetHints(prefs: SharedPreferences) {
        val editor = prefs.edit()
        hintPages.forEach { page ->
            hintShown[page] = false
            editor.remove(HINT_SHOWN_PREFIX + page)
            editor.remove(VISITS_PREFIX + page)
        }
        editor.apply()
    }
}
