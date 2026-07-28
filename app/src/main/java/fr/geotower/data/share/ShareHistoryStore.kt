package fr.geotower.data.share

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/**
 * Une entrée = un partage (ou une génération PDF) d'un site ou d'un support. On ne garde que des
 * données brutes (identifiants, opérateur, adresse) : les libellés sont construits à l'affichage,
 * sinon un changement de langue figerait l'historique dans l'ancienne.
 */
data class ShareHistoryEntry(
    val id: String,
    /** Voir [ShareHistoryStore.KIND_MOBILE_SITE] et consorts. */
    val kind: String,
    /** Voir [ShareHistoryStore.DEST_SHARE] et consorts. */
    val destination: String,
    /** `id_support` ANFR (mobile) ou identifiant de support radio. Vide si inconnu. */
    val supportId: String = "",
    /** `id_anfr` de la station (mobile) ou identifiant de station radio. Vide pour un support. */
    val stationId: String = "",
    /** Opérateur (mobile) ou nom du réseau/multiplex (radio). */
    val label: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Nombre de stations incluses dans le partage : 1 pour un site, N pour un support. */
    val itemCount: Int = 1,
    val createdAtMillis: Long = 0L
)

/**
 * Historique local des partages de sites et de supports, sur le même modèle que l'historique des
 * photos envoyées : un fichier JSON dans `filesDir`, lu et purgé depuis la page dédiée. Rien n'est
 * envoyé au serveur.
 */
object ShareHistoryStore {
    const val KIND_MOBILE_SITE = "mobile_site"
    const val KIND_MOBILE_SUPPORT = "mobile_support"
    const val KIND_RADIO_SITE = "radio_site"
    const val KIND_RADIO_SUPPORT = "radio_support"

    const val DEST_SHARE = "share"
    const val DEST_CLIPBOARD = "clipboard"
    const val DEST_PDF = "pdf"
    const val DEST_PDF_DOWNLOAD = "pdf_download"

    private const val HISTORY_FILE_NAME = "share_history.json"
    private const val MAX_ENTRIES = 300

    private val gson = Gson()

    @Synchronized
    fun record(
        context: Context,
        kind: String,
        destination: String,
        supportId: String? = null,
        stationId: String? = null,
        label: String? = null,
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        itemCount: Int = 1,
        createdAtMillis: Long = System.currentTimeMillis()
    ) {
        val entry = ShareHistoryEntry(
            id = UUID.randomUUID().toString(),
            kind = kind,
            destination = destination,
            supportId = supportId.orEmpty().trim(),
            stationId = stationId.orEmpty().trim(),
            label = label.orEmpty().trim(),
            address = address.orEmpty().trim(),
            latitude = latitude,
            longitude = longitude,
            itemCount = itemCount.coerceAtLeast(1),
            createdAtMillis = createdAtMillis
        )

        val nextEntries = (readInternal(context.applicationContext) + entry)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_ENTRIES)
        saveInternal(context.applicationContext, nextEntries)
    }

    @Synchronized
    fun read(context: Context): List<ShareHistoryEntry> {
        return readInternal(context).sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun removeEntries(context: Context, entryIds: Collection<String>) {
        val ids = entryIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return

        val entries = readInternal(context)
        if (entries.none { it.id in ids }) return
        saveInternal(context, entries.filterNot { it.id in ids })
    }

    @Synchronized
    fun clear(context: Context) {
        historyFile(context).delete()
    }

    fun estimatedFreedBytes(entries: List<ShareHistoryEntry>): Long {
        if (entries.isEmpty()) return 0L
        return entries.sumOf { gson.toJson(it).toByteArray().size.toLong() }
    }

    private fun readInternal(context: Context): List<ShareHistoryEntry> {
        val file = historyFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            gson.fromJson(file.readText(), Array<ShareHistoryEntry>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveInternal(context: Context, entries: List<ShareHistoryEntry>) {
        runCatching { historyFile(context).writeText(gson.toJson(entries)) }
    }

    private fun historyFile(context: Context): File = File(context.filesDir, HISTORY_FILE_NAME)
}
