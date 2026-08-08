package fr.geotower.data.share

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/**
 * Une entrée = un partage (ou une génération PDF) d'un site, d'un support ou de la carte. On ne
 * garde que des données brutes (identifiants, opérateur, adresse, codes de blocs) : les libellés
 * sont construits à l'affichage, sinon un changement de langue figerait l'historique dans l'ancienne.
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
    val createdAtMillis: Long = 0L,
    // Champs ajoutés après coup : ils DOIVENT rester nuls par défaut et scalaires, et être ajoutés à
    // la fin. Gson n'applique pas les valeurs par défaut de Kotlin, donc une entrée écrite par une
    // version précédente les relit à `null` -- un type non nullable planterait à la première lecture.
    // Scalaires : un champ collection imposerait une règle `-keep` (voir proguard-rules.pro), qui
    // figerait les noms de champs et rendrait illisibles les fichiers déjà sur les téléphones.
    /** Codes des blocs inclus, séparés par des virgules. Voir [ShareHistoryStore.CONTENT_MAP]. */
    val contents: String? = null,
    /** Thème choisi au moment du partage : `true` = sombre. */
    val darkTheme: Boolean? = null,
    /** Niveau de zoom, pour les partages de carte uniquement. */
    val mapZoom: Double? = null
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

    /** Partage de la carte des antennes : ni station ni support, on rouvre la carte sur le cadrage. */
    const val KIND_MAP = "map"

    const val DEST_SHARE = "share"
    const val DEST_CLIPBOARD = "clipboard"
    const val DEST_PDF = "pdf"
    const val DEST_PDF_DOWNLOAD = "pdf_download"

    // Codes des blocs inclus dans l'image partagée. Ils reprennent tels quels les identifiants de
    // blocs des menus de partage (`shareOrder`), pour qu'un code ne veuille jamais dire deux choses.
    const val CONTENT_MAP = "map"
    const val CONTENT_ELEVATION_PROFILE = "elevation_profile"
    const val CONTENT_SUPPORT = "support"
    const val CONTENT_PHOTOS = "photos"
    const val CONTENT_IDS = "ids"
    const val CONTENT_DATES = "dates"
    const val CONTENT_ADDRESS = "address"
    const val CONTENT_SPEEDTEST = "speedtest"
    const val CONTENT_THROUGHPUT = "throughput"
    const val CONTENT_FREQ = "freq"
    const val CONTENT_STATUS = "status"
    const val CONTENT_HEIGHTS = "heights"
    const val CONTENT_OPERATORS = "operators"
    const val CONTENT_RADIO_ENTRIES = "radio_entries"
    const val CONTENT_QR_CODE = "qr"
    const val CONTENT_CONFIDENTIAL = "confidential"
    const val CONTENT_SPLIT_IMAGE = "split"
    const val CONTENT_AZIMUTHS = "azimuths"
    const val CONTENT_SPEEDOMETER = "speedometer"
    const val CONTENT_SCALE = "scale"
    const val CONTENT_ATTRIBUTION = "attribution"
    const val CONTENT_COVERAGE = "coverage"
    const val CONTENT_OBSTACLES = "obstacles"

    // Blocs propres aux partages radio (mêmes identifiants que RADIO_SHARE_* côté menu).
    const val CONTENT_SUMMARY = "summary"
    const val CONTENT_SOURCE = "source"
    const val CONTENT_BEARING_HEIGHT = "bearing_height"
    const val CONTENT_RADIO_FREQ = "radio"
    const val CONTENT_PROGRAMS = "programs"
    const val CONTENT_EXTRA = "extra"
    const val CONTENT_SUPPORT_ENTRIES = "support_entries"

    /**
     * Assemble la liste des blocs cochés : `contentsOf(incMap to CONTENT_MAP, ...)`. Renvoie `null`
     * quand rien n'est coché, pour ne pas distinguer « aucun bloc » d'une entrée d'avant ce champ.
     */
    fun contentsOf(vararg blocks: Pair<Boolean, String>): String? {
        return blocks.filter { it.first }
            .joinToString(",") { it.second }
            .takeIf { it.isNotBlank() }
    }

    /** Découpe le champ stocké ; tolère les codes inconnus, qui seront simplement ignorés à l'affichage. */
    fun contentCodes(contents: String?): List<String> {
        return contents.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

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
        contents: String? = null,
        darkTheme: Boolean? = null,
        mapZoom: Double? = null,
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
            createdAtMillis = createdAtMillis,
            contents = contents?.takeIf { it.isNotBlank() },
            darkTheme = darkTheme,
            mapZoom = mapZoom?.takeIf { !it.isNaN() && !it.isInfinite() }
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
