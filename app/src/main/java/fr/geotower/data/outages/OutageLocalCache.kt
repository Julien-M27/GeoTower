package fr.geotower.data.outages

import android.content.Context
import com.google.gson.Gson
import fr.geotower.data.models.SiteHsEntity
import java.io.File

/** Contenu mis en cache d'une génération locale : les pannes + quand elles ont été produites. */
data class CachedOutages(
    val generatedAtMillis: Long,
    val sourceLastUpdate: String?,
    val sites: List<SiteHsEntity>,
)

/**
 * Cache disque (JSON) du résultat de la génération locale, pour éviter de re-télécharger/re-géocoder
 * à chaque appel. Écriture atomique via un fichier temporaire.
 */
class OutageLocalCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val gson = Gson()

    fun load(): CachedOutages? = try {
        if (!file.exists()) null else gson.fromJson(file.readText(), CachedOutages::class.java)
    } catch (_: Exception) {
        null // cache corrompu/illisible : traité comme absent
    }

    fun save(cache: CachedOutages) {
        try {
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
        runCatching { file.delete() }
    }

    companion object {
        const val FILE_NAME = "sites_hs_local.json"
    }
}
