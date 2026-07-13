package fr.geotower.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fr.geotower.utils.PreferenceStores

/**
 * Determine si la base installee (mobile / radio) a ete **generee sur l'appareil** (build local)
 * plutot que **telechargee** depuis le serveur.
 *
 * - **Mobile** (`geotower_fr.db`) : le builder local ecrit `source_versions.provenance = 'local_build'`
 *   (le build serveur, lui, ne pose jamais cette valeur). Signal interne a la base, auto-suffisant.
 * - **Radio** (`geotower_fr_radio.db`) : le schema `metadata` est fige (WITHOUT ROWID, pas de table
 *   `source_versions`) — impossible d'y poser un marqueur sans casser le hash de schema. On memorise
 *   donc dans les prefs la `version` du dernier build radio local et on la compare a la version
 *   installee. Auto-correcteur : apres un telechargement, la version installee differe de la version
 *   memorisee -> la base n'est plus consideree comme locale (sans avoir a nettoyer la pref).
 *
 * Les lectures ouvrent une connexion SQLite en lecture seule (tables `metadata` / `source_versions`,
 * 1 ligne) : negligeable, meme si Room garde la base mobile ouverte par ailleurs. A appeler hors du
 * thread principal.
 */
object LocalDbProvenance {

    /**
     * @param installed la base existe et a pu etre lue.
     * @param locallyBuilt la base installee provient d'un build **local** (sinon telechargee/absente).
     * @param buildVersionRaw `metadata.version` (format `yyyyMMdd_HHmm`) quand [locallyBuilt], sinon null.
     */
    data class Info(
        val installed: Boolean,
        val locallyBuilt: Boolean,
        val buildVersionRaw: String?,
    ) {
        companion object {
            val NONE = Info(installed = false, locallyBuilt = false, buildVersionRaw = null)
        }
    }

    private const val PROVENANCE_LOCAL_BUILD = "local_build"
    private const val RADIO_LOCAL_VERSION_KEY = "db_radio_local_build_version"

    /** Provenance de la base **mobile** installee (via `source_versions.provenance`). */
    fun readMobile(context: Context): Info {
        val dbPath = context.getDatabasePath(GeoTowerDatabaseValidator.DB_NAME)
        if (!dbPath.exists()) return Info.NONE
        return try {
            SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val provenance = readSingleString(
                    db,
                    "SELECT source_value FROM source_versions WHERE source_key = 'provenance' LIMIT 1",
                )
                val local = provenance == PROVENANCE_LOCAL_BUILD
                val version = if (local) readSingleString(db, "SELECT version FROM metadata LIMIT 1") else null
                Info(installed = true, locallyBuilt = local, buildVersionRaw = version)
            }
        } catch (e: Exception) {
            Info.NONE
        }
    }

    /** Provenance de la base **radio** installee (comparaison version installee / version memorisee). */
    fun readRadio(context: Context): Info {
        val dbPath = context.getDatabasePath(RadioDatabaseValidator.DB_NAME)
        if (!dbPath.exists()) return Info.NONE
        return try {
            SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val version = readSingleString(db, "SELECT version FROM metadata LIMIT 1")
                val recorded = prefs(context).getString(RADIO_LOCAL_VERSION_KEY, null)
                val local = version != null && version == recorded
                Info(installed = true, locallyBuilt = local, buildVersionRaw = if (local) version else null)
            }
        } catch (e: Exception) {
            Info.NONE
        }
    }

    /** Memorise la `version` du build radio local qui vient d'etre installe (appele par le pipeline). */
    fun recordRadioLocalBuild(context: Context, version: String) {
        prefs(context).edit().putString(RADIO_LOCAL_VERSION_KEY, version).apply()
    }

    /** `yyyyMMdd_HHmm` -> `dd/MM/yyyy - HH:mm` (renvoie l'entree brute si non parsable, ou null). */
    fun formatBuildTime(versionRaw: String?): String? {
        if (versionRaw == null) return null
        if (versionRaw.length != 13) return versionRaw
        return try {
            val year = versionRaw.substring(0, 4)
            val month = versionRaw.substring(4, 6)
            val day = versionRaw.substring(6, 8)
            val hour = versionRaw.substring(9, 11)
            val minute = versionRaw.substring(11, 13)
            "$day/$month/$year - $hour:$minute"
        } catch (e: Exception) {
            versionRaw
        }
    }

    private fun readSingleString(db: SQLiteDatabase, sql: String): String? =
        db.rawQuery(sql, null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
}
