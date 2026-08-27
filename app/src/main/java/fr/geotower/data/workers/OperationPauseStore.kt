package fr.geotower.data.workers

import android.content.Context
import kotlinx.coroutines.delay

/**
 * Etat de pause des operations longues visibles dans les reglages.
 *
 * WorkManager ne propose pas de pause native pour un [CoroutineWorker]. On conserve donc un
 * interrupteur applicatif : le worker reste vivant, mais attend cooperativement avant de
 * poursuivre son traitement. L'etat est persistant afin que le bouton reste coherent apres une
 * recomposition ou un redemarrage du processus.
 */
object OperationPauseStore {
    const val LOCAL_DB_BUILD = "local_db_build"
    const val MOBILE_DB_DOWNLOAD = "mobile_db_download"
    const val RADIO_DB_DOWNLOAD = "radio_db_download"
    const val ENB_DB_DOWNLOAD = "enb_db_download"
    const val OFFLINE_MAP_DOWNLOAD_PREFIX = "offline_map_download:"

    private const val PREFS_NAME = "GeoTowerOperationPause"
    private const val PAUSED_OPERATIONS_KEY = "paused_operations"

    fun mapDownloadKey(mapFilename: String): String = "$OFFLINE_MAP_DOWNLOAD_PREFIX$mapFilename"

    fun isPaused(context: Context, operation: String): Boolean =
        pausedOperations(context).contains(operation)

    fun setPaused(context: Context, operation: String, paused: Boolean) {
        val operations = updatePausedOperations(pausedOperations(context), operation, paused)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PAUSED_OPERATIONS_KEY, operations)
            .apply()
    }

    fun clear(context: Context, operation: String) = setPaused(context, operation, paused = false)

    internal fun updatePausedOperations(
        current: Set<String>,
        operation: String,
        paused: Boolean,
    ): Set<String> = current.toMutableSet().apply {
        if (paused) add(operation) else remove(operation)
    }

    suspend fun awaitUntilResumed(context: Context, operation: String) {
        while (isPaused(context, operation)) {
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pausedOperations(context: Context): Set<String> =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PAUSED_OPERATIONS_KEY, emptySet())
            .orEmpty()

    /**
     * Point de pause utilisable depuis les builders synchrones (SQLite/CSV). Le traitement est
     * execute sur le dispatcher IO du worker ; dormir ici ne bloque donc jamais le thread UI.
     */
    fun blockIfPaused(context: Context, operation: String) {
        while (isPaused(context, operation)) {
            if (Thread.currentThread().isInterrupted) {
                throw java.util.concurrent.CancellationException("Operation interrompue")
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.util.concurrent.CancellationException("Operation interrompue")
            }
        }
    }

    private const val POLL_INTERVAL_MS = 250L
}
