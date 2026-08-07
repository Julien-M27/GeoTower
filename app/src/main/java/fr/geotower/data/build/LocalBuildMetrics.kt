package fr.geotower.data.build

/**
 * Capacites de l'appareil pertinentes pour la generation locale, lues une fois au demarrage.
 *
 * [heapLimitBytes] (`ActivityManager.getMemoryClass`) est **le** chiffre qui decide si un build
 * tient ou non : ce n'est pas la RAM totale qui plafonne le builder mais le tas Java du process.
 * Un 4 Go et un 8 Go peuvent n'avoir qu'un rapport de 1,3 sur ce plafond alors que le gate actuel
 * (RAM >= 6 Go) les separe en deux (cf.
 * `docs/agent-ia-plan-optimisation-generation-locale-db-2026-08-05.md`, section 2).
 */
data class BuildDeviceProfile(
    val totalRamBytes: Long,
    val heapLimitBytes: Long,
    val largeHeapLimitBytes: Long,
    val lowRamDevice: Boolean,
    val processors: Int,
)

/**
 * Mesures d'une generation locale : duree et lignes par phase, pics memoire (tas Java, memoire
 * native, PSS du process), pic de stockage de travail, taille des sources et des bases produites.
 *
 * A quoi ca sert : abaisser le gate d'eligibilite (aujourd'hui RAM >= 6 Go / 1 Go libre) demande de
 * connaitre le cout REEL d'un build, par pack, sur de vrais appareils. Tant que ces chiffres
 * n'existent pas, toute optimisation et tout nouveau seuil sont des paris. Le rapport produit par
 * [report] est conserve par `LocalBuildReportStore` et affiche dans l'ecran Diagnostic.
 *
 * Classe **pure** (aucune dependance Android) pour rester testable en JVM ; l'echantillonnage reel
 * est fait par `LocalBuildMetricsRecorder`.
 *
 * Non thread-safe pour les compteurs de phase (appeles depuis le thread du build) ; les `sample*`
 * viennent du thread d'echantillonnage et n'ecrivent que des maximums, ce qui suffit ici.
 */
class LocalBuildMetrics(private val nowMs: () -> Long = System::currentTimeMillis) {

    private val startedAt = nowMs()

    /** Lue par le thread d'echantillonnage pour attribuer chaque releve a la phase en cours. */
    @Volatile
    private var currentPhase: BuildPhase? = null
    private var phaseStartedAt = startedAt
    private var pendingRows = 0L
    private val phaseDurations = LinkedHashMap<BuildPhase, Long>()
    private val phaseRows = LinkedHashMap<BuildPhase, Long>()
    private val fileSizes = LinkedHashMap<String, Long>()

    // Ecrites uniquement par le thread d'echantillonnage, relues apres son arret (happens-before
    // assure par le `join` de LocalBuildMetricsRecorder.stop).
    private val phasePeakHeap = LinkedHashMap<BuildPhase, Long>()

    /**
     * Phase pendant laquelle le pic a ete atteint : c'est ce qui dit **ou** porter l'effort. Un pic
     * de tas atteint pendant RADIO_BUILDING signifie qu'un build « mobile seul » tient dans bien
     * moins de memoire, donc qu'il peut etre ouvert a des appareils plus modestes sans rien
     * optimiser.
     */
    @Volatile
    var peakHeapPhase: BuildPhase? = null
        private set

    @Volatile
    var peakDiskPhase: BuildPhase? = null
        private set

    @Volatile
    var peakHeapBytes = 0L
        private set

    @Volatile
    var peakNativeBytes = 0L
        private set

    @Volatile
    var peakPssBytes = 0L
        private set

    @Volatile
    var peakWorkDirBytes = 0L
        private set

    @Volatile
    var peakOutputBytes = 0L
        private set

    /** Pic du stockage occupe SIMULTANEMENT par les sources et les bases en construction. */
    @Volatile
    var peakTotalDiskBytes = 0L
        private set

    /**
     * A appeler a chaque evenement de progression. [processed] est le compteur de lignes de la
     * sous-etape en cours : il repart de zero a chaque nouvelle boucle, ce qui sert justement de
     * signal pour cumuler les lignes d'une phase traversee plusieurs fois (READING_SUPPORTS l'est
     * trois fois : emetteurs, antennes, supports).
     */
    fun onProgress(phase: BuildPhase, processed: Long = 0L) {
        val now = nowMs()
        if (phase != currentPhase) {
            commitRows()
            currentPhase?.let { phaseDurations[it] = (phaseDurations[it] ?: 0L) + (now - phaseStartedAt) }
            currentPhase = phase
            phaseStartedAt = now
        } else if (processed < pendingRows) {
            commitRows()
        }
        pendingRows = processed
    }

    fun sampleMemory(heapBytes: Long, nativeBytes: Long, pssBytes: Long) {
        if (heapBytes > peakHeapBytes) {
            peakHeapBytes = heapBytes
            peakHeapPhase = currentPhase
        }
        if (nativeBytes > peakNativeBytes) peakNativeBytes = nativeBytes
        if (pssBytes > peakPssBytes) peakPssBytes = pssBytes
        val phase = currentPhase
        if (phase != null && heapBytes > (phasePeakHeap[phase] ?: 0L)) phasePeakHeap[phase] = heapBytes
    }

    fun sampleDisk(workDirBytes: Long, outputBytes: Long) {
        if (workDirBytes > peakWorkDirBytes) peakWorkDirBytes = workDirBytes
        if (outputBytes > peakOutputBytes) peakOutputBytes = outputBytes
        val total = workDirBytes + outputBytes
        if (total > peakTotalDiskBytes) {
            peakTotalDiskBytes = total
            peakDiskPhase = currentPhase
        }
    }

    /** Taille d'une source telechargee ou d'une base produite, a relever AVANT tout nettoyage. */
    fun noteFile(label: String, bytes: Long) {
        if (bytes > 0L) fileSizes[label] = bytes
    }

    /**
     * Rapport lisible, une entree par ligne (format attendu par `DiagnosticItem.details`). Les
     * unites sont des symboles SI, volontairement non traduits comme le reste des chiffres
     * techniques de l'app.
     */
    fun report(
        device: BuildDeviceProfile,
        packs: String,
        success: Boolean,
        reason: String?,
    ): List<String> {
        commitRows()
        currentPhase?.let { phaseDurations[it] = (phaseDurations[it] ?: 0L) + (nowMs() - phaseStartedAt) }
        currentPhase = null

        val lines = ArrayList<String>()
        lines += "Resultat : " + (if (success) "OK" else "echec — ${reason ?: "cause inconnue"}")
        lines += "Duree totale : " + duration(nowMs() - startedAt)
        lines += "Packs : $packs"
        lines += "Appareil : RAM ${mo(device.totalRamBytes)} | tas Java max ${mo(device.heapLimitBytes)} " +
            "(largeHeap ${mo(device.largeHeapLimitBytes)}) | ${device.processors} coeurs | " +
            "lowRam ${if (device.lowRamDevice) "oui" else "non"}"
        lines += "Pic tas Java : ${mo(peakHeapBytes)} " +
            "(${percentOf(peakHeapBytes, device.heapLimitBytes)} du plafond)" + during(peakHeapPhase)
        lines += "Pic memoire native : ${mo(peakNativeBytes)}"
        if (peakPssBytes > 0L) lines += "Pic PSS process : ${mo(peakPssBytes)}"
        // Les trois pics sont releves a des INSTANTS differents : ils ne s'additionnent pas, et le
        // libelle doit l'assumer (un « 828 (travail 827 + bases 146) » se lit comme une somme fausse).
        lines += "Pic stockage simultane : ${mo(peakTotalDiskBytes)}" + during(peakDiskPhase)
        lines += "Pics separes : travail ${mo(peakWorkDirBytes)} | bases produites ${mo(peakOutputBytes)}"
        for ((label, bytes) in fileSizes) lines += "Fichier $label : ${mo(bytes)}"
        for ((phase, ms) in phaseDurations) {
            val rows = phaseRows[phase] ?: 0L
            val rate = if (ms > 0L && rows > 0L) " — ${rows * 1000L / ms} lignes/s" else ""
            val counted = if (rows > 0L) " — $rows lignes" else ""
            val heap = phasePeakHeap[phase]?.let { " — pic tas ${mo(it)}" } ?: ""
            lines += "Phase ${phase.name} : ${duration(ms)}$counted$rate$heap"
        }
        return lines
    }

    private fun commitRows() {
        val phase = currentPhase
        if (phase != null && pendingRows > 0L) {
            phaseRows[phase] = (phaseRows[phase] ?: 0L) + pendingRows
        }
        pendingRows = 0L
    }

    private fun during(phase: BuildPhase?): String = phase?.let { " — pendant ${it.name}" } ?: ""

    private fun percentOf(value: Long, total: Long): String =
        if (total <= 0L) "?" else "${value * 100L / total} %"

    private fun mo(bytes: Long): String = when {
        bytes <= 0L -> "0 Mo"
        bytes < 1024L * 1024L -> "${bytes / 1024L} Ko"
        else -> "${bytes / (1024L * 1024L)} Mo"
    }

    /**
     * Meme rendu que `DbOperationTimings.formatDuration`, reimplemente ici pour que la classe
     * reste sans dependance Android (donc testable en JVM).
     */
    private fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0L -> "$hours h ${minutes.toString().padStart(2, '0')} min"
            minutes > 0L -> "$minutes min ${seconds.toString().padStart(2, '0')} s"
            else -> "$seconds s"
        }
    }
}
