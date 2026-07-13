package fr.geotower.data.db

import android.content.Context
import fr.geotower.utils.PreferenceStores

/**
 * Chronometrage des operations de base de donnees (telechargement / generation locale) pour l'UI
 * Reglages : un chrono **live** pendant l'operation, puis la **duree finale** conservee apres.
 *
 * Stockage prefs ([PreferenceStores.APP]) par operation, cle logique `<key>` :
 *  - `<key>_op_start` : horodatage de debut ([System.currentTimeMillis]) tant que l'operation tourne ;
 *    efface a la fin. Sert au chrono live (survit a un aller-retour dans l'ecran, contrairement au
 *    progress WorkManager qui est ephemere).
 *  - `<key>_op_ms` : duree de la **derniere operation reussie** (ms). Persistant.
 *
 * Aucune donnee sensible : uniquement des horodatages/durees. A appeler hors du thread principal
 * cote worker ; cote UI les lectures sont triviales (une pref).
 */
object DbOperationTimings {

    /** Base mobile telechargee ([fr.geotower.data.workers.DatabaseDownloadWorker]). */
    const val MOBILE_DOWNLOAD = "db_mobile_download"

    /** Base radio telechargee ([fr.geotower.data.workers.RadioDatabaseDownloadWorker]). */
    const val RADIO_DOWNLOAD = "db_radio_download"

    /** Generation locale ([fr.geotower.data.workers.LocalDbBuildWorker]), mobile et/ou radio en une passe. */
    const val LOCAL_BUILD = "db_local_build"

    /** Cle de chronometrage d'une carte hors-ligne (une par fichier de carte). */
    fun mapKey(mapFilename: String): String = "map_dl_$mapFilename"

    /** Marque le debut d'une operation (chrono live). Ecrase un eventuel debut precedent (nouvelle tentative). */
    fun markStart(context: Context, key: String, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(startKey(key), now).apply()
    }

    /**
     * Fige la duree = `now - debut` (si un debut est memorise) comme derniere duree reussie et efface
     * le debut. Retourne la duree en ms, ou null si aucun debut n'etait memorise.
     */
    fun finish(context: Context, key: String, now: Long = System.currentTimeMillis()): Long? {
        val p = prefs(context)
        val start = p.getLong(startKey(key), -1L)
        if (start <= 0L) {
            p.edit().remove(startKey(key)).apply()
            return null
        }
        val duration = (now - start).coerceAtLeast(0L)
        p.edit().putLong(durationKey(key), duration).remove(startKey(key)).apply()
        return duration
    }

    /** Abandonne le chrono en cours sans enregistrer de duree (annulation / echec). */
    fun clearStart(context: Context, key: String) {
        prefs(context).edit().remove(startKey(key)).apply()
    }

    /** Horodatage de debut de l'operation en cours (chrono live), ou null si aucune n'est en cours. */
    fun readStartTime(context: Context, key: String): Long? =
        prefs(context).getLong(startKey(key), -1L).takeIf { it > 0L }

    /** Duree de la derniere operation reussie (ms), ou null si jamais mesuree. */
    fun readDurationMs(context: Context, key: String): Long? =
        prefs(context).getLong(durationKey(key), -1L).takeIf { it >= 0L }

    /**
     * Formate une duree avec les symboles SI (universels, pas de traduction) : `45 s`, `4 min 32 s`,
     * `1 h 03 min`. Les secondes sont omises au-dela de l'heure (bruit inutile sur les longs builds).
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0L -> "$hours h ${minutes.toString().padStart(2, '0')} min"
            minutes > 0L -> "$minutes min ${seconds.toString().padStart(2, '0')} s"
            else -> "$seconds s"
        }
    }

    private fun startKey(key: String) = "${key}_op_start"
    private fun durationKey(key: String) = "${key}_op_ms"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
}
