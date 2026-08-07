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
 * Un fichier de pannes relu est-il réellement exploitable ?
 *
 * Gson construit l'objet **sans passer par le constructeur Kotlin** : rien ne garantit que `sites`
 * soit rempli, malgré son type non-nullable. Deux façons de récupérer un cache inutilisable, toutes
 * deux vécues en 2.0.11 :
 *
 * - le fichier a été écrit par une version où R8 renommait les champs de la classe (`sites` → `d`),
 *   les clés ne correspondent plus et `sites` revient `null` — la panne n'éclate alors que bien plus
 *   loin, chez l'appelant ;
 * - privé de la signature générique du champ (R8 remplaçait `List<SiteHsEntity>` par un `ArrayList`
 *   brut), Gson ignore le type des éléments et remplit la liste de `LinkedTreeMap` : le premier
 *   parcours de la carte cassait sur un `ClassCastException`, application fermée.
 *
 * Les règles `-keep` de `proguard-rules.pro` traitent la cause ; ce contrôle reste le filet, parce
 * qu'un cache ignoré (donc regénéré) coûte infiniment moins cher qu'une application qui se ferme.
 */
internal fun cachedOutageSitesAreUsable(sites: List<SiteHsEntity>?): Boolean {
    // Volontairement vu comme `List<*>` : lire un élément via le type déclaré insérerait le cast
    // qu'on cherche justement à éviter ici.
    val raw: List<*> = sites ?: return false
    val first = raw.firstOrNull() ?: return true // liste vide : cache valide, il n'y a aucune panne
    return first is SiteHsEntity
}

/**
 * Cache disque (JSON) du résultat de la génération locale, pour éviter de re-télécharger/re-géocoder
 * à chaque appel. Écriture atomique via un fichier temporaire.
 */
class OutageLocalCache(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    private val gson = Gson()

    fun load(): CachedOutages? = try {
        if (!file.exists()) {
            null
        } else {
            gson.fromJson(file.readText(), CachedOutages::class.java)
                ?.takeIf { cachedOutageSitesAreUsable(it.sites) }
        }
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
