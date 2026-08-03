package fr.geotower.data.outages

import android.content.Context
import android.content.SharedPreferences
import fr.geotower.data.models.SiteHsEntity

/**
 * Réglages de la récupération LOCALE des pannes (vs source serveur). Persistés dans les prefs
 * partagées "settings" (même store que `last_hs_update`).
 *
 * Le CHOIX de la source ne vit plus ici : il est porté par le niveau de « Traitement local »
 * ([fr.geotower.utils.AppConfig.outagesLocal], niveau ≥ 1), seule vérité de l'app. Cette classe ne
 * garde que les réglages d'exécution (fréquence, arrière-plan) et le résumé de la dernière
 * récupération. L'ancienne clé `outages_source` est migrée au démarrage puis supprimée
 * (cf. `AppConfig.migrateLegacyOutageSourcePref`).
 */
class OutageLocalConfig(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    /** Fréquence (heures) : durée de validité du cache ET période de la planification en fond. */
    var frequencyHours: Int
        get() = prefs.getInt(KEY_FREQUENCY_HOURS, DEFAULT_FREQUENCY_HOURS).coerceIn(MIN_FREQUENCY_HOURS, MAX_FREQUENCY_HOURS)
        set(value) = prefs.edit().putInt(KEY_FREQUENCY_HOURS, value.coerceIn(MIN_FREQUENCY_HOURS, MAX_FREQUENCY_HOURS)).apply()

    /** true = un WorkManager périodique régénère en arrière-plan même app fermée (opt-in). */
    var backgroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND, value).apply()

    /** Horodatage (ms) de la dernière génération locale réussie. 0 si jamais. */
    var lastGeneratedAtMillis: Long
        get() = prefs.getLong(KEY_LAST_GENERATED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_GENERATED, value).apply()

    /** Nombre de pannes produites par la dernière génération réussie. -1 si inconnu. */
    var lastGeneratedCount: Int
        get() = prefs.getInt(KEY_LAST_COUNT, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_COUNT, value).apply()

    /**
     * Répartition par opérateur de la dernière génération réussie (ordre décroissant), vide si
     * inconnue. Encodée « opérateur<TAB>nombre » par ligne : pas de dépendance JSON pour 4 lignes,
     * et les tabulations/retours ligne sont neutralisés à l'écriture.
     */
    var lastBreakdown: List<Pair<String, Int>>
        get() = decodeBreakdown(prefs.getString(KEY_LAST_BREAKDOWN, null))
        set(value) {
            prefs.edit().putString(KEY_LAST_BREAKDOWN, encodeBreakdown(value)).apply()
        }

    /**
     * Mémorise le résultat d'une génération réussie (horodatage, total, répartition par opérateur),
     * quelle que soit son origine : bouton « Générer maintenant », planification en arrière-plan ou
     * régénération paresseuse à l'expiration du cache. C'est ce que relit la page « Source des pannes ».
     */
    fun recordGeneration(atMillis: Long, sites: List<SiteHsEntity>) {
        lastGeneratedAtMillis = atMillis
        lastGeneratedCount = sites.size
        lastBreakdown = breakdownOf(sites)
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

        const val KEY_FREQUENCY_HOURS = "outages_local_frequency_hours"
        const val KEY_BACKGROUND = "outages_local_background_enabled"
        const val KEY_LAST_GENERATED = "outages_local_last_generated_at"
        const val KEY_LAST_COUNT = "outages_local_last_count"
        const val KEY_LAST_BREAKDOWN = "outages_local_last_breakdown"
        const val DEFAULT_FREQUENCY_HOURS = 6
        const val MIN_FREQUENCY_HOURS = 1
        const val MAX_FREQUENCY_HOURS = 24
    }
}
