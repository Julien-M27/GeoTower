package fr.geotower.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.geotower.utils.PreferenceStores

/**
 * Nombre d'antennes de la base mobile installee (`geotower_fr.db`), pour la carte
 * « Base de donnees des antennes » des reglages.
 *
 * Les bases radio et eNB portent ce total dans `metadata.row_count` et l'affichent gratuitement.
 * La base mobile, elle, ne le peut pas : son schema est fige par l'identity_hash Room (schema 7),
 * donc pas de colonne supplementaire possible - voir [GeoTowerDatabaseValidator]. Le total est donc
 * compte a la lecture, puis **memorise en prefs avec la version de la base** : un `COUNT(*)` sur
 * `antenne` balaye un index de plusieurs millions d'entrees, hors de question de le refaire a chaque
 * ouverture de la page. Le cache se perime tout seul quand la version installee change (base
 * telechargee comme base generee sur l'appareil).
 *
 * A appeler hors du thread principal.
 */
object MobileDbRowCounts {

    private const val CACHE_VERSION_KEY = "db_mobile_antenna_count_version"
    private const val CACHE_VALUE_KEY = "db_mobile_antenna_count"

    /**
     * @param versionRaw `metadata.version` de la base installee, qui sert de cle de cache. A null,
     *   le comptage est fait mais rien n'est memorise.
     * @return le nombre de lignes de `antenne`, ou null si la base est absente ou illisible.
     */
    fun antennaCount(
        context: Context,
        versionRaw: String?,
        country: DatabaseCountry = DatabaseCountryStore.getActive(context)
    ): Int? {
        val dbName = country.dbFileName
        val dbPath = context.getDatabasePath(dbName)
        if (!dbPath.exists()) return null

        val versionPrefKey = if (country == DatabaseCountry.FR) CACHE_VERSION_KEY else "${CACHE_VERSION_KEY}_${country.code}"
        val valuePrefKey = if (country == DatabaseCountry.FR) CACHE_VALUE_KEY else "${CACHE_VALUE_KEY}_${country.code}"
        val cacheKey = versionRaw?.let { "$dbName:$it" }
        val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
        if (cacheKey != null && prefs.getString(versionPrefKey, null) == cacheKey) {
            val cached = prefs.getInt(valuePrefKey, -1)
            if (cached >= 0) return cached
        }

        return try {
            SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val count = db.rawQuery("SELECT COUNT(*) FROM antenne", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else null
                }
                if (count != null && cacheKey != null) {
                    prefs.edit()
                        .putString(versionPrefKey, cacheKey)
                        .putInt(valuePrefKey, count)
                        .apply()
                }
                count
            }
        } catch (e: Exception) {
            null
        }
    }
}
