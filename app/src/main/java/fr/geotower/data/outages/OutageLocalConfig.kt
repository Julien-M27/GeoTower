package fr.geotower.data.outages

import android.content.Context
import android.content.SharedPreferences

/**
 * Réglages de la génération LOCALE des pannes (vs source serveur). Persistés dans les prefs
 * partagées "settings" (même store que `last_hs_update`). Défaut : source serveur.
 */
class OutageLocalConfig(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    /** true = l'app génère les pannes elle-même ; false = GeoJSON serveur (défaut). */
    var useLocalSource: Boolean
        get() = prefs.getString(KEY_SOURCE, SOURCE_SERVER) == SOURCE_LOCAL
        set(value) = prefs.edit().putString(KEY_SOURCE, if (value) SOURCE_LOCAL else SOURCE_SERVER).apply()

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

    val frequencyMillis: Long get() = frequencyHours.toLong() * 3_600_000L

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_SOURCE = "outages_source"
        const val KEY_FREQUENCY_HOURS = "outages_local_frequency_hours"
        const val KEY_BACKGROUND = "outages_local_background_enabled"
        const val KEY_LAST_GENERATED = "outages_local_last_generated_at"
        const val SOURCE_SERVER = "server"
        const val SOURCE_LOCAL = "local"
        const val DEFAULT_FREQUENCY_HOURS = 6
        const val MIN_FREQUENCY_HOURS = 1
        const val MAX_FREQUENCY_HOURS = 24
    }
}
