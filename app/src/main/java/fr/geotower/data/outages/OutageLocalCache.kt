package fr.geotower.data.outages

import android.content.Context
import com.google.gson.Gson
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppConfig
import java.io.File

/** Contenu mis en cache d'une génération locale : les pannes + quand elles ont été produites. */
data class CachedOutages(
    val generatedAtMillis: Long,
    val sourceLastUpdate: String?,
    val sites: List<SiteHsEntity>,
)

/**
 * Un fichier de pannes relu est-il réellement exploitable ?
 *
 * Gson construit l'objet **sans passer par le constructeur Kotlin** : rien ne garantit que `sites`
 * soit rempli, malgré son type non-nullable.
 */
internal fun cachedOutageSitesAreUsable(sites: List<SiteHsEntity>?): Boolean {
    val raw: List<*> = sites ?: return false
    val first = raw.firstOrNull() ?: return true // liste vide : cache valide, il n'y a aucune panne
    return first is SiteHsEntity
}

/**
 * Cache disque (JSON) du résultat de la génération locale, pour éviter de re-télécharger/re-géocoder
 * à chaque appel. Écriture atomique via un fichier temporaire.
 */
class OutageLocalCache(
    private val staticFile: File? = null,
    private val context: Context? = null,
) {

    constructor(file: File) : this(staticFile = file, context = null)
    constructor(context: Context) : this(staticFile = null, context = context)

    private val gson = Gson()

    private fun targetFile(): File {
        if (staticFile != null) return staticFile
        val mode = AppConfig.outageSourceMode.value
        val file = File(context!!.filesDir, fileFor(mode))
        if (!file.exists() && mode == OutageSourceMode.OPERATORS) {
            val legacy = File(context.filesDir, FILE_NAME)
            if (legacy.exists()) return legacy
        }
        return file
    }

    fun load(): CachedOutages? = try {
        val target = targetFile()
        if (!target.exists()) {
            null
        } else {
            gson.fromJson(target.readText(), CachedOutages::class.java)
                ?.takeIf { cachedOutageSitesAreUsable(it.sites) }
        }
    } catch (_: Exception) {
        null // cache corrompu/illisible : traité comme absent
    }

    fun save(cache: CachedOutages) {
        try {
            val file = targetFile()
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(gson.toJson(cache))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
            // Best effort : un échec d'écriture du cache ne doit pas casser la génération.
        }
    }

    fun clear() {
        val target = targetFile()
        runCatching { target.delete() }
        val ctx = context
        if (ctx != null && AppConfig.outageSourceMode.value == OutageSourceMode.OPERATORS) {
            runCatching { File(ctx.filesDir, FILE_NAME).delete() }
        }
    }

    companion object {
        const val FILE_NAME = "sites_hs_local.json"
        fun fileFor(mode: OutageSourceMode): String = "sites_hs_local_${mode.key}.json"
    }
}
