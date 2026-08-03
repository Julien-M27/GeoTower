package fr.geotower.data.outages

import android.content.Context
import com.google.gson.Gson
import fr.geotower.data.models.SiteHsEntity
import java.io.File

/** Copie conservée du fichier de pannes du serveur : les pannes, et ce qui les date. */
data class CachedServerOutages(
    /** Instant du téléchargement : c'est lui qui borne la fraîcheur de NOTRE copie. */
    val downloadedAtMillis: Long,
    /** `last_update` de `/api/v2/antennes/hs/info`, connue au jour près. */
    val sourceLastUpdate: String?,
    /** `metadata.generated_at` du GeoJSON (UTC), 0 si le fichier ne le porte pas. */
    val serverGeneratedAtMillis: Long,
    val sites: List<SiteHsEntity>,
)

/**
 * Cache disque (JSON) du fichier de pannes SERVEUR, pendant de [OutageLocalCache] pour l'autre source.
 *
 * Le fichier de pannes est national : sans copie sur l'appareil, il repartait du réseau à chaque
 * démarrage du process, et il n'y avait tout simplement pas de pannes hors ligne. La copie sert donc
 * trois choses — affichage immédiat au lancement, repli quand le serveur ne répond pas, et résumé
 * affiché dans la carte « Sites en panne » des réglages.
 *
 * Écriture atomique via un fichier temporaire, comme le cache local.
 */
class ServerOutageCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val gson = Gson()

    fun load(): CachedServerOutages? = try {
        if (!file.exists()) null else gson.fromJson(file.readText(), CachedServerOutages::class.java)
    } catch (_: Exception) {
        null // cache corrompu/illisible : traité comme absent
    }

    fun save(cache: CachedServerOutages) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(gson.toJson(cache))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
            // Best effort : un échec d'écriture ne doit pas priver l'appel en cours de ses pannes.
        }
    }

    /** Taille de la copie sur l'appareil (octets), 0 si aucune. Sert la ligne « espace occupé ». */
    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    fun clear() {
        runCatching { file.delete() }
    }

    companion object {
        const val FILE_NAME = "sites_hs_server.json"
    }
}
