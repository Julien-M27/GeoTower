package fr.geotower.data.db

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

enum class DatabaseCountry(
    val code: String,
    val displayName: String,
    val dbFileName: String,
    val sourceLabel: String
) {
    FR(
        code = "FR",
        displayName = "France",
        dbFileName = "geotower_fr.db",
        sourceLabel = "ANFR / ARCEP"
    ),
    CA(
        code = "CA",
        displayName = "Canada",
        dbFileName = "geotower_ca.db",
        sourceLabel = "ISDE / ISED"
    );

    companion object {
        fun fromCode(code: String?): DatabaseCountry {
            val normalized = code?.trim()?.uppercase()
            return entries.firstOrNull { it.code == normalized } ?: FR
        }

        /**
         * Détermine la base nationale à utiliser en mode AUTO pour des coordonnées données.
         * Saint-Pierre-et-Miquelon (territoire français couvert par l'ANFR) est exclu de la
         * zone Canada bien qu'il soit géographiquement en Amérique du Nord.
         */
        fun forCoordinates(latitude: Double, longitude: Double): DatabaseCountry {
            val isSaintPierreEtMiquelon = latitude in 46.72..47.18 && longitude in -56.45..-56.08
            if (isSaintPierreEtMiquelon) return FR
            val inCanada = latitude in 41.0..84.0 && longitude in -142.0..-52.0
            return if (inCanada) CA else FR
        }
    }
}

object DatabaseCountryStore {
    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val KEY_ACTIVE_COUNTRY = "active_database_country"

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedCountry: DatabaseCountry = DatabaseCountry.FR

    private val _activeCountryState = mutableStateOf(DatabaseCountry.FR)
    val activeCountryState: State<DatabaseCountry> get() = _activeCountryState

    fun getActive(context: Context? = null): DatabaseCountry {
        if (!initialized && context != null) {
            synchronized(this) {
                if (!initialized) {
                    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val stored = DatabaseCountry.fromCode(prefs.getString(KEY_ACTIVE_COUNTRY, DatabaseCountry.FR.code))
                    cachedCountry = stored
                    _activeCountryState.value = stored
                    initialized = true
                }
            }
        }
        return cachedCountry
    }

    fun setActive(context: Context, country: DatabaseCountry) {
        val appContext = context.applicationContext
        val previous = getActive(appContext)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_COUNTRY, country.code)
            .apply()
        cachedCountry = country
        initialized = true
        _activeCountryState.value = country
        if (previous != country) {
            AppDatabase.closeDatabase()
        }
    }
}
