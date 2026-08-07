package fr.geotower.data.build

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import fr.geotower.utils.AppLogger
import fr.geotower.utils.PreferenceStores
import java.io.File

/**
 * Cote Android de [LocalBuildMetrics] : lecture des capacites de l'appareil, echantillonnage
 * periodique de la memoire et du stockage pendant le build, et conservation du dernier rapport.
 *
 * L'echantillonnage tourne sur un thread demon dedie plutot que sur une coroutine : la generation
 * sort par une dizaine de chemins differents (`return@withContext` d'erreur), un thread arrete
 * dans le `finally` est plus sur qu'un job structure a annuler partout.
 */
object BuildDeviceProfiles {

    fun read(context: Context): BuildDeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return BuildDeviceProfile(
            totalRamBytes = memoryInfo.totalMem,
            heapLimitBytes = activityManager.memoryClass.toLong() * 1024L * 1024L,
            largeHeapLimitBytes = activityManager.largeMemoryClass.toLong() * 1024L * 1024L,
            lowRamDevice = activityManager.isLowRamDevice,
            processors = Runtime.getRuntime().availableProcessors(),
        )
    }
}

/** Echantillonneur : pousse dans [metrics] les pics memoire et stockage pendant toute la generation. */
class LocalBuildMetricsRecorder(
    private val metrics: LocalBuildMetrics,
    private val workDir: File,
    private val outputs: List<File>,
) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "GeoTowerBuildMetrics").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        runCatching { thread?.join(JOIN_TIMEOUT_MS) }
        thread = null
    }

    private fun loop() {
        var tick = 0
        while (running) {
            // Le PSS est bien plus couteux a lire que le tas : une fois sur cinq suffit pour un pic.
            runCatching { sample(withPss = tick % PSS_EVERY == 0) }
                .onFailure { AppLogger.w(TAG, "Echantillonnage des mesures de build impossible", it) }
            tick++
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun sample(withPss: Boolean) {
        val runtime = Runtime.getRuntime()
        val pss = if (withPss) {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            info.totalPss.toLong() * 1024L
        } else {
            0L
        }
        metrics.sampleMemory(
            heapBytes = runtime.totalMemory() - runtime.freeMemory(),
            nativeBytes = Debug.getNativeHeapAllocatedSize(),
            pssBytes = pss,
        )
        metrics.sampleDisk(directorySize(workDir), outputs.sumOf { fileSize(it) })
    }

    /** Taille d'une base en construction, journaux `-wal`/`-shm` compris. */
    private fun fileSize(file: File): Long =
        file.length() + File(file.path + "-wal").length() + File(file.path + "-shm").length()

    private fun directorySize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private companion object {
        const val TAG = "GeoTowerDb"
        const val SAMPLE_INTERVAL_MS = 2_000L
        const val PSS_EVERY = 5
        const val JOIN_TIMEOUT_MS = 500L
    }
}

/**
 * Dernier rapport de generation locale, conserve en preferences pour l'ecran Diagnostic. Purement
 * technique et sans donnee personnelle (durees, tailles, capacites de l'appareil).
 */
object LocalBuildReportStore {

    private const val KEY_REPORT = "local_build_last_report"
    private const val KEY_AT = "local_build_last_report_at"

    fun save(context: Context, lines: List<String>, now: Long = System.currentTimeMillis()) {
        if (lines.isEmpty()) return
        prefs(context).edit()
            .putString(KEY_REPORT, lines.joinToString("\n"))
            .putLong(KEY_AT, now)
            .apply()
    }

    fun read(context: Context): List<String> =
        prefs(context).getString(KEY_REPORT, null)?.lines()?.filter { it.isNotBlank() } ?: emptyList()

    /** Horodatage du dernier rapport, ou null si aucune generation n'a encore ete mesuree. */
    fun readTimestamp(context: Context): Long? =
        prefs(context).getLong(KEY_AT, -1L).takeIf { it > 0L }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
}
