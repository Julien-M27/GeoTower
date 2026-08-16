package fr.geotower.data.notifications

import android.content.Context
import com.google.gson.Gson
import fr.geotower.utils.PreferenceStores
import java.io.File
import java.util.UUID

/**
 * Une entrée = une notification que l'application a décidé d'émettre. Comme pour l'historique des
 * partages, on ne garde que des données brutes (type, statut, paramètre) : le libellé est reconstruit
 * à l'affichage, sinon un changement de langue figerait le journal dans l'ancienne.
 *
 * Tous les champs sont scalaires : un champ collection imposerait une règle `-keep` (voir
 * proguard-rules.pro), R8 en mode complet effaçant le type générique. Un champ ajouté après coup
 * DOIT l'être à la fin et rester nullable ou pourvu d'une valeur par défaut scalaire — Gson
 * n'applique pas les valeurs par défaut de Kotlin, une entrée écrite par une version précédente le
 * relirait à `null` et un type non nullable planterait à la première lecture.
 */
data class NotificationHistoryEntry(
    val id: String,
    /** Voir [NotificationHistoryStore.TYPE_DB_MOBILE] et consorts. */
    val type: String,
    /** Voir [NotificationHistoryStore.STATUS_SUCCESS] et consorts. */
    val status: String,
    /** Paramètre principal du libellé : nom de carte, de fichier, version, opérateur… */
    val label: String = "",
    /** Complément éventuel : motif d'échec, second paramètre du libellé. */
    val detail: String = "",
    /** Nombre d'éléments concernés (photos envoyées, sites traités). 0 = sans objet. */
    val itemCount: Int = 0,
    /**
     * Ce que le clic doit rouvrir, sous l'une de trois formes :
     * - `geotower://…` : le lien profond que portait la notification, rejoué tel quel ;
     * - `content://…` ou `file://…` : un fichier produit, ouvert par une application tierce ;
     * - tout le reste : une route interne de navigation (par exemple `photo_upload_history`).
     *
     * Vide quand l'entrée n'a rien à rouvrir. Même contrat que `navigateToSiteFlow` de MainActivity,
     * augmenté du cas fichier.
     */
    val target: String = "",
    /**
     * Vrai si la notification système a réellement été affichée. Fausse quand l'interrupteur maître
     * était coupé, la permission refusée ou le kill-switch distant actif : l'événement a bien eu
     * lieu, l'utilisateur ne l'a simplement jamais vu passer — c'est précisément ce que ce journal
     * lui rend.
     */
    val posted: Boolean = false,
    val createdAtMillis: Long = 0L
)

/**
 * Journal local des notifications de l'application, sur le modèle de
 * [fr.geotower.data.share.ShareHistoryStore] : un fichier JSON dans `filesDir`, lu, filtré et purgé
 * depuis la page dédiée. Rien n'est envoyé au serveur.
 *
 * On n'y consigne que les événements **terminaux** — téléchargement fini, envoi terminé, rappel,
 * mise à jour disponible. Les notifications de progression, qui se réécrivent des dizaines de fois
 * et appartiennent au service de premier plan (voir [fr.geotower.utils.AppNotifications]), n'y
 * entrent jamais : elles noieraient le journal.
 */
object NotificationHistoryStore {
    // Bases de données : une sorte par base, elles ont chacune leur carte de réglages.
    const val TYPE_DB_MOBILE = "db_mobile"
    const val TYPE_DB_RADIO = "db_radio"
    const val TYPE_DB_ENB = "db_enb"

    /** Génération de la base sur l'appareil, par opposition à son téléchargement. */
    const val TYPE_DB_LOCAL_BUILD = "db_local_build"

    /** Mise à jour annoncée : de la base d'un côté, de l'application de l'autre. */
    const val TYPE_DB_UPDATE = "db_update"
    const val TYPE_APP_UPDATE = "app_update"

    const val TYPE_MAP_DOWNLOAD = "map_download"
    const val TYPE_OUTAGES = "outages"

    /** Envoi de photos vers SignalQuest, et signalement d'une photo communautaire. */
    const val TYPE_PHOTO_UPLOAD = "photo_upload"
    const val TYPE_PHOTO_REPORT = "photo_report"

    const val TYPE_TRIP_REMINDER = "trip_reminder"

    /** Arrivée sur une étape pendant le suivi d'une tournée. */
    const val TYPE_TRIP_ARRIVAL = "trip_arrival"

    const val TYPE_PDF_REPORT = "pdf_report"

    /** Opération menée à bien. */
    const val STATUS_SUCCESS = "success"

    /** Opération échouée : `detail` porte le motif quand il est connu. */
    const val STATUS_ERROR = "error"

    /** Ni réussite ni échec : une information ou un rappel (mise à jour disponible, départ à préparer). */
    const val STATUS_INFO = "info"

    /**
     * `detail` d'une entrée [TYPE_DB_UPDATE] annoncée à qui génère sa base sur l'appareil : la mise
     * à jour se prend par régénération locale, pas par téléchargement, et le libellé le dit.
     */
    const val DETAIL_DB_UPDATE_REBUILD = "rebuild"

    /** Rangées de la plus récente à la plus ancienne, comme la page les affiche. */
    @Synchronized
    fun read(context: Context): List<NotificationHistoryEntry> {
        return readInternal(context.applicationContext).sortedByDescending { it.createdAtMillis }
    }

    /**
     * Consigne un événement. À appeler **avant** le garde-fou `AppNotifications.canPost()` de
     * l'émetteur, en lui passant son résultat dans [posted] : le journal doit garder la trace d'un
     * téléchargement terminé même quand les notifications de l'app sont coupées.
     */
    @Synchronized
    fun record(
        context: Context,
        type: String,
        status: String,
        label: String? = null,
        detail: String? = null,
        itemCount: Int = 0,
        target: String? = null,
        posted: Boolean = false,
        createdAtMillis: Long = System.currentTimeMillis()
    ) {
        val entry = NotificationHistoryEntry(
            id = UUID.randomUUID().toString(),
            type = type,
            status = status,
            label = label.orEmpty().trim(),
            detail = detail.orEmpty().trim(),
            itemCount = itemCount.coerceAtLeast(0),
            target = target.orEmpty().trim(),
            posted = posted,
            createdAtMillis = createdAtMillis
        )

        val nextEntries = (readInternal(context.applicationContext) + entry)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_ENTRIES)
        saveInternal(context.applicationContext, nextEntries)
    }

    /**
     * Ajoute les entrées d'une sauvegarde qui manquent ici, sur le même contrat que
     * [fr.geotower.data.share.ShareHistoryStore.mergeEntries] : identifiant inconnu = ajout,
     * identifiant déjà là = on n'y touche pas. Rend le nombre d'entrées réellement conservées après
     * application du plafond [MAX_ENTRIES].
     */
    @Synchronized
    fun mergeEntries(context: Context, entries: List<NotificationHistoryEntry>): Int {
        if (entries.isEmpty()) return 0

        val applicationContext = context.applicationContext
        val existing = readInternal(applicationContext)
        val knownIds = existing.mapTo(HashSet()) { it.id }
        val incoming = entries.filter { it.id.isNotBlank() && knownIds.add(it.id) }
        if (incoming.isEmpty()) return 0

        val nextEntries = (existing + incoming)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_ENTRIES)
        saveInternal(applicationContext, nextEntries)

        val keptIds = nextEntries.mapTo(HashSet()) { it.id }
        return incoming.count { it.id in keptIds }
    }

    @Synchronized
    fun removeEntries(context: Context, entryIds: Collection<String>) {
        val ids = entryIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return

        val entries = readInternal(context.applicationContext)
        if (entries.none { it.id in ids }) return
        saveInternal(context.applicationContext, entries.filterNot { it.id in ids })
    }

    @Synchronized
    fun clear(context: Context) {
        historyFile(context.applicationContext).delete()
    }

    fun estimatedFreedBytes(entries: List<NotificationHistoryEntry>): Long {
        if (entries.isEmpty()) return 0L
        return entries.sumOf { gson.toJson(it).toByteArray().size.toLong() }
    }

    /**
     * Nombre d'entrées plus récentes que la dernière ouverture de la page : c'est ce que porte la
     * pastille du bouton d'accueil. Une entrée supprimée sans avoir été lue disparaît d'elle-même
     * du compte, puisqu'on compte les entrées présentes et non les événements passés.
     */
    fun unreadCount(context: Context): Int {
        val lastRead = lastReadMillis(context)
        return read(context).count { it.createdAtMillis > lastRead }
    }

    fun markAllRead(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_READ, System.currentTimeMillis()).apply()
    }

    private fun lastReadMillis(context: Context): Long = prefs(context).getLong(KEY_LAST_READ, 0L)

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)

    private const val KEY_LAST_READ = "notification_history_last_read"
    private const val HISTORY_FILE_NAME = "notification_history.json"

    // Les notifications sont bien plus rares que les partages (quelques-unes par semaine) : 500
    // entrées couvrent largement plusieurs années d'usage pour ~120 Ko de JSON.
    private const val MAX_ENTRIES = 500

    private val gson = Gson()

    private fun readInternal(context: Context): List<NotificationHistoryEntry> {
        val file = historyFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            gson.fromJson(file.readText(), Array<NotificationHistoryEntry>::class.java)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveInternal(context: Context, entries: List<NotificationHistoryEntry>) {
        runCatching { historyFile(context).writeText(gson.toJson(entries)) }
    }

    private fun historyFile(context: Context): File = File(context.filesDir, HISTORY_FILE_NAME)
}
