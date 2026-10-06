package fr.geotower.data.outages

import android.content.Context
import android.content.SharedPreferences
import fr.geotower.data.models.SiteHsEntity

/**
 * Réglages de la récupération LOCALE des pannes (vs source serveur). Persistés dans les prefs
 * partagées "settings" (même store que `last_hs_update`).
 *
 * Le niveau de « Provenance des données » ([fr.geotower.utils.AppConfig.outagesLocal], crans « pannes en local »)
 * décide si les pannes sont traitées sur l'appareil. Le choix de la sous-source ([sourceMode]) décide ensuite
 * d'utiliser les 4 fichiers opérateurs CSV en direct ou le fichier consolidé quotidien Arcep (data.gouv.fr).
 */
class OutageLocalConfig(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    /** Mode de source pour les pannes : direct opérateurs (temps réel) ou journalier consolidé (Arcep). */
    var sourceMode: OutageSourceMode
        get() = OutageSourceMode.fromKey(prefs.getString(KEY_SOURCE_MODE, OutageSourceMode.OPERATORS.key))
        set(value) = prefs.edit().putString(KEY_SOURCE_MODE, value.key).apply()

    /** Mode de source utilisé lors de la dernière génération réussie. */
    var lastSourceMode: OutageSourceMode
        get() = OutageSourceMode.fromKey(prefs.getString(KEY_LAST_SOURCE_MODE, OutageSourceMode.OPERATORS.key))
        set(value) = prefs.edit().putString(KEY_LAST_SOURCE_MODE, value.key).apply()

    /** Fréquence (heures) : durée de validité du cache ET période de la planification en fond. */
    var frequencyHours: Int
        get() = prefs.getInt(KEY_FREQUENCY_HOURS, DEFAULT_FREQUENCY_HOURS).coerceIn(MIN_FREQUENCY_HOURS, MAX_FREQUENCY_HOURS)
        set(value) = prefs.edit().putInt(KEY_FREQUENCY_HOURS, value.coerceIn(MIN_FREQUENCY_HOURS, MAX_FREQUENCY_HOURS)).apply()

    /** true = un WorkManager périodique régénère en arrière-plan même app fermée (opt-in). */
    var backgroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND, value).apply()

    private fun keyFor(baseKey: String, mode: OutageSourceMode): String =
        if (mode == OutageSourceMode.OPERATORS) baseKey else "${baseKey}_${mode.key}"

    /** Horodatage (ms) de la dernière génération locale réussie. 0 si jamais. */
    var lastGeneratedAtMillis: Long
        get() = getLastGeneratedAtMillis(sourceMode)
        set(value) = prefs.edit().putLong(keyFor(KEY_LAST_GENERATED, sourceMode), value).apply()

    fun getLastGeneratedAtMillis(mode: OutageSourceMode = sourceMode): Long =
        prefs.getLong(keyFor(KEY_LAST_GENERATED, mode), 0L)

    /** Nombre de pannes produites par la dernière génération réussie. -1 si inconnu. */
    var lastGeneratedCount: Int
        get() = getLastGeneratedCount(sourceMode)
        set(value) = prefs.edit().putInt(keyFor(KEY_LAST_COUNT, sourceMode), value).apply()

    fun getLastGeneratedCount(mode: OutageSourceMode = sourceMode): Int =
        prefs.getInt(keyFor(KEY_LAST_COUNT, mode), -1)

    /**
     * Répartition par opérateur de la dernière génération réussie (ordre décroissant), vide si
     * inconnue. Encodée « opérateur<TAB>nombre » par ligne : pas de dépendance JSON pour 4 lignes,
     * et les tabulations/retours ligne sont neutralisés à l'écriture.
     */
    var lastBreakdown: List<Pair<String, Int>>
        get() = getLastBreakdown(sourceMode)
        set(value) {
            prefs.edit().putString(keyFor(KEY_LAST_BREAKDOWN, sourceMode), encodeBreakdown(value)).apply()
        }

    fun getLastBreakdown(mode: OutageSourceMode = sourceMode): List<Pair<String, Int>> =
        decodeBreakdown(prefs.getString(keyFor(KEY_LAST_BREAKDOWN, mode), null))

    /**
     * Détail par génération (2G/3G/4G/5G) de la dernière génération réussie, vide si inconnu.
     * Calculé une fois ici plutôt qu'à l'affichage : la page des réglages ne relit jamais la liste
     * complète des pannes. Voir [OutageTechBreakdown].
     */
    var lastTechBreakdown: List<OutageTechRow>
        get() = getLastTechBreakdown(sourceMode)
        set(value) = setLastTechBreakdown(value, sourceMode)

    fun getLastTechBreakdown(mode: OutageSourceMode = sourceMode): List<OutageTechRow> =
        OutageTechBreakdown.decode(prefs.getString(keyFor(KEY_LAST_TECH_BREAKDOWN, mode), null))

    fun setLastTechBreakdown(rows: List<OutageTechRow>, mode: OutageSourceMode = sourceMode) {
        prefs.edit().putString(keyFor(KEY_LAST_TECH_BREAKDOWN, mode), OutageTechBreakdown.encode(rows)).apply()
    }

    /**
     * Mémorise le résultat d'une génération réussie (horodatage, total, répartition par opérateur
     * puis par génération), quelle que soit son origine : bouton « Générer maintenant »,
     * planification en arrière-plan ou régénération paresseuse à l'expiration du cache. C'est ce que
     * relit la page « Source des pannes ».
     */
    fun recordGeneration(atMillis: Long, sites: List<SiteHsEntity>, mode: OutageSourceMode = sourceMode) {
        prefs.edit()
            .putLong(keyFor(KEY_LAST_GENERATED, mode), atMillis)
            .putInt(keyFor(KEY_LAST_COUNT, mode), sites.size)
            .putString(keyFor(KEY_LAST_BREAKDOWN, mode), encodeBreakdown(breakdownOf(sites)))
            .putString(keyFor(KEY_LAST_TECH_BREAKDOWN, mode), OutageTechBreakdown.encode(OutageTechBreakdown.of(sites)))
            .putString(KEY_LAST_SOURCE_MODE, mode.key)
            .apply()
    }

    val frequencyMillis: Long get() = frequencyHours.toLong() * 3_600_000L

    companion object {

        /** Répartition par opérateur, du plus touché au moins touché. */
        fun breakdownOf(sites: List<SiteHsEntity>): List<Pair<String, Int>> =
            sites.groupingBy { it.operateur }.eachCount()
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }

        /** Encodage « opérateur<TAB>nombre » par ligne, partagé avec [OutageServerInfo]. */
        fun encodeBreakdown(breakdown: List<Pair<String, Int>>): String =
            breakdown.joinToString("\n") { (operateur, nombre) ->
                "${operateur.replace('\t', ' ').replace('\n', ' ')}\t$nombre"
            }

        fun decodeBreakdown(encoded: String?): List<Pair<String, Int>> =
            encoded?.lineSequence()
                ?.mapNotNull { line ->
                    val parts = line.split('\t')
                    val count = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (parts.size == 2 && count != null && parts[0].isNotBlank()) parts[0] to count else null
                }
                ?.toList()
                .orEmpty()

        const val PREFS_NAME = "settings"

        /** Ancienne clé du choix de source, conservée uniquement pour la migration ponctuelle. */
        const val LEGACY_KEY_SOURCE = "outages_source"
        const val LEGACY_SOURCE_LOCAL = "local"

        const val KEY_SOURCE_MODE = "outages_source_mode"
        const val KEY_LAST_SOURCE_MODE = "outages_local_last_source_mode"
        const val KEY_FREQUENCY_HOURS = "outages_local_frequency_hours"
        const val KEY_BACKGROUND = "outages_local_background_enabled"
        const val KEY_LAST_GENERATED = "outages_local_last_generated_at"
        const val KEY_LAST_COUNT = "outages_local_last_count"
        const val KEY_LAST_BREAKDOWN = "outages_local_last_breakdown"
        const val KEY_LAST_TECH_BREAKDOWN = "outages_local_last_tech_breakdown"
        const val DEFAULT_FREQUENCY_HOURS = 6
        const val MIN_FREQUENCY_HOURS = 1
        const val MAX_FREQUENCY_HOURS = 24
    }
}
