package fr.geotower.data.community

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/**
 * Un signalement envoyé à SignalQuest, conservé **uniquement sur l'appareil**.
 *
 * Entrée volontairement plate (aucun champ `List`/`Map`/`Set`) : les classes lues par Gson qui
 * portent une collection doivent être `-keep` dans proguard-rules.pro, et une règle `-keep` fige
 * les noms de champs — donc rendrait illisibles les fichiers déjà écrits sur les téléphones.
 */
data class PhotoReportHistoryEntry(
    val id: String,
    val photoId: String,
    val siteId: String,
    val reason: String,
    val description: String?,
    val photoUrl: String?,
    val operatorLabel: String?,
    val createdAtMillis: Long,
    val status: String,
    val lastCheckAtMillis: Long? = null,
    val checkCount: Int = 0,
    val resolvedAtMillis: Long? = null
)

object PhotoReportHistoryStore {
    /** Transmis à SignalQuest. Indiscernable d'un refus : l'API ne rend pas l'état de modération. */
    const val STATUS_SENT = "sent"

    /** La photo a disparu des photos publiées du site — seule issue que l'app sache observer. */
    const val STATUS_REMOVED = "removed"

    /** Au-delà, on cesse de sonder : la photo est restée en ligne, la modération a tranché sans nous. */
    const val FOLLOW_UP_WINDOW_MILLIS = 180L * 24 * 60 * 60 * 1000

    private const val HISTORY_FILE_NAME = "photo_report_history.json"
    private const val MAX_ENTRIES = 200

    private val gson = Gson()

    @Synchronized
    fun add(
        context: Context,
        photoId: String,
        siteId: String,
        reason: String,
        description: String?,
        photoUrl: String?,
        operatorLabel: String?,
        createdAtMillis: Long = System.currentTimeMillis()
    ): String {
        val id = UUID.randomUUID().toString()
        val entry = PhotoReportHistoryEntry(
            id = id,
            photoId = photoId,
            siteId = siteId,
            reason = reason,
            description = description,
            photoUrl = photoUrl,
            operatorLabel = operatorLabel,
            createdAtMillis = createdAtMillis,
            status = STATUS_SENT
        )

        // Un re-signalement de la même photo remplace le précédent : côté SignalQuest aussi, un
        // client API n'a qu'un signalement par photo.
        val nextEntries = (readInternal(context).filterNot { it.photoId == photoId } + entry)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_ENTRIES)
        saveInternal(context, nextEntries)
        return id
    }

    /** Signalements encore à surveiller : transmis, et dans la fenêtre de suivi. */
    @Synchronized
    fun pendingFollowUp(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): List<PhotoReportHistoryEntry> {
        return readInternal(context).filter { entry ->
            entry.status == STATUS_SENT &&
                entry.siteId.isNotBlank() &&
                nowMillis - entry.createdAtMillis <= FOLLOW_UP_WINDOW_MILLIS
        }
    }

    @Synchronized
    fun markRemoved(context: Context, entryId: String, nowMillis: Long = System.currentTimeMillis()) {
        val nextEntries = readInternal(context).map { entry ->
            if (entry.id == entryId) {
                entry.copy(
                    status = STATUS_REMOVED,
                    resolvedAtMillis = nowMillis,
                    lastCheckAtMillis = nowMillis,
                    checkCount = entry.checkCount + 1
                )
            } else {
                entry
            }
        }
        saveInternal(context, nextEntries)
    }

    @Synchronized
    fun recordCheck(context: Context, entryId: String, nowMillis: Long = System.currentTimeMillis()) {
        val nextEntries = readInternal(context).map { entry ->
            if (entry.id == entryId) {
                entry.copy(lastCheckAtMillis = nowMillis, checkCount = entry.checkCount + 1)
            } else {
                entry
            }
        }
        saveInternal(context, nextEntries)
    }

    @Synchronized
    fun read(context: Context): List<PhotoReportHistoryEntry> {
        return readInternal(context).sortedByDescending { it.createdAtMillis }
    }

    /**
     * Ajoute les signalements d'une sauvegarde qui manquent ici. La clé est la **photo** et non
     * l'identifiant d'entrée : côté SignalQuest, un client API n'a qu'un signalement par photo (voir
     * [add]), donc deux appareils qui signalent la même photo décrivent le même signalement.
     *
     * Un signalement déjà connu n'est jamais réécrit : son statut est le fruit du suivi mené par cet
     * appareil-ci, la sauvegarde n'en sait pas plus que lui. Rend le nombre d'entrées réellement
     * conservées après application du plafond [MAX_ENTRIES].
     */
    @Synchronized
    fun mergeEntries(context: Context, entries: List<PhotoReportHistoryEntry>): Int {
        if (entries.isEmpty()) return 0

        val existing = readInternal(context)
        val knownPhotoIds = existing.mapTo(HashSet()) { it.photoId }
        val incoming = entries.filter { it.photoId.isNotBlank() && knownPhotoIds.add(it.photoId) }
        if (incoming.isEmpty()) return 0

        val nextEntries = (existing + incoming)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_ENTRIES)
        saveInternal(context, nextEntries)

        val keptPhotoIds = nextEntries.mapTo(HashSet()) { it.photoId }
        return incoming.count { it.photoId in keptPhotoIds }
    }

    @Synchronized
    fun remove(context: Context, entryIds: Collection<String>) {
        val ids = entryIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        saveInternal(context, readInternal(context).filterNot { it.id in ids })
    }

    @Synchronized
    fun clear(context: Context) {
        historyFile(context).delete()
    }

    private fun readInternal(context: Context): List<PhotoReportHistoryEntry> {
        val file = historyFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            gson.fromJson(file.readText(), Array<PhotoReportHistoryEntry>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveInternal(context: Context, entries: List<PhotoReportHistoryEntry>) {
        historyFile(context).writeText(gson.toJson(entries))
    }

    private fun historyFile(context: Context): File =
        File(context.applicationContext.filesDir, HISTORY_FILE_NAME)
}
