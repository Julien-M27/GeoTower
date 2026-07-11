package fr.geotower.data.build

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URI

/**
 * Resolution des sources ANFR **officielles** pour la generation locale. Volontairement PUR
 * (aucun reseau) : expose les URLs d'API fixes et les fonctions de selection (allowlist d'hotes,
 * choix de la ressource ZIP dans le JSON data.gouv, extraction de trimestre ARCEP). Le
 * telechargement HTTP reel est fait par le downloader ; ces helpers restent testables en JVM.
 *
 * Datasets (references dans AboutScreen.kt) :
 * - mensuel "Donnees SUP" : dataset data.gouv `donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1`
 * - hebdo "Observatoire" : dataset data.anfr `observatoire_2g_3g_4g`
 * - trimestriel ARCEP "sites" : `data.arcep.fr/mobile/sites/`
 * - communes : `geo.api.gouv.fr`
 */
object OfficialSources {

    /** Hotes HTTPS autorises (incl. les cibles de redirection de data.gouv). */
    val ALLOWED_HOSTS = setOf(
        "www.data.gouv.fr",
        "object.files.data.gouv.fr",
        "static.data.gouv.fr",
        "data.anfr.fr",
        "data.arcep.fr",
        "geo.api.gouv.fr",
    )

    /** API data.gouv du dataset "Donnees SUP" a interroger pour trouver le ZIP mensuel courant. */
    const val MONTHLY_SUP_DATASET_API_URL =
        "https://www.data.gouv.fr/api/1/datasets/donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1/"

    /**
     * Page d'export ANFR de l'observatoire. Son HTML reference l'URL du **fichier CSV statique
     * courant** (`file_csv`) — c'est la vraie source (l'API d4c/records ne renvoie qu'une erreur).
     * Le CSV est en `;`, avec BOM, ~180 Mo, colonnes techniques (sta_nm_anfr, coordonnees, statut...).
     */
    const val OBSERVATOIRE_EXPORT_PAGE_URL =
        "https://data.anfr.fr/explore/dataset/observatoire_2g_3g_4g/export/"

    // Le nom du CSV porte une date (ex. 20260702170329_observatoireod_20260702.csv), a resoudre.
    private val OBSERVATOIRE_CSV_REGEX = Regex(
        """data\.anfr\.fr\\?/sites\\?/default\\?/files\\?/dataset\\?/\d+_observatoireod_\d+\.csv""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extrait du HTML de [OBSERVATOIRE_EXPORT_PAGE_URL] l'URL du CSV statique courant de
     * l'observatoire. Normalise les slashes echappes `\/` du JSON embarque. `null` si absente.
     */
    fun resolveObservatoireCsvUrl(exportPageHtml: String): String? {
        val match = OBSERVATOIRE_CSV_REGEX.find(exportPageHtml)?.value ?: return null
        val url = ("https://$match").replace("\\/", "/")
        return if (isAllowedHost(url)) url else null
    }

    /** Referentiel des communes (INSEE -> nom), JSON. */
    const val COMMUNES_URL = "https://geo.api.gouv.fr/communes?fields=nom,code&format=json"

    /** Base du repertoire ARCEP des fichiers "sites" trimestriels (enrichissement optionnel). */
    const val ARCEP_SITES_BASE_URL = "https://data.arcep.fr/mobile/sites/"

    /** Vrai si `url` est en HTTPS, sur un hote autorise, sans userinfo. */
    fun isAllowedHost(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                (uri.host?.lowercase() in ALLOWED_HOSTS)
        } catch (_: Exception) {
            false
        }
    }

    /** URLs des deux ZIP mensuels "Donnees SUP" : donnees (SUP_*) et references. */
    data class MonthlySupUrls(val dataUrl: String, val refUrl: String?)

    /**
     * Le dataset data.gouv publie DEUX ZIP par mois : `*-etalab-data.zip` (tables SUP_STATION/
     * SUPPORT/ANTENNE/EMETTEUR/BANDE) et `*-etalab-ref.zip` (referentiels). On retourne les deux
     * (le ZIP de donnees est obligatoire, celui des references optionnel), en prenant les plus
     * recents sur un hote autorise. `null` si aucun ZIP de donnees exploitable.
     */
    fun selectMonthlySupZipUrls(datasetJson: String): MonthlySupUrls? {
        val resources = try {
            JsonParser.parseString(datasetJson).asJsonObject.getAsJsonArray("resources")
        } catch (_: Exception) {
            null
        } ?: return null

        data class Zip(val url: String, val filename: String, val key: String)
        val zips = ArrayList<Zip>()
        for (element in resources) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val url = obj.get("url").stringOrNull() ?: continue
            val format = obj.get("format").stringOrNull()?.lowercase().orEmpty()
            val filename = url.substringAfterLast('/').lowercase()
            val isZip = format == "zip" || filename.endsWith(".zip")
            if (!isZip || !isAllowedHost(url)) continue
            val key = obj.get("last_modified").stringOrNull()
                ?: obj.get("created_at").stringOrNull()
                ?: ""
            zips.add(Zip(url, filename, key))
        }
        if (zips.isEmpty()) return null

        val sorted = zips.sortedByDescending { it.key }
        val ref = sorted.firstOrNull { it.filename.contains("ref") }
        val data = sorted.firstOrNull { it.filename.contains("data") && it !== ref }
            ?: sorted.firstOrNull { it !== ref }
            ?: return null
        return MonthlySupUrls(data.url, ref?.url)
    }

    /** Choisit le CSV ARCEP au trimestre le plus recent parmi une liste d'URLs (best-effort). */
    fun selectLatestArcepCsvUrl(urls: List<String>): String? {
        return urls
            .filter { it.lowercase().endsWith(".csv") && isAllowedHost(it) }
            .maxWithOrNull(compareBy({ extractQuarter(it) ?: "" }, { it }))
    }

    /** Extrait un trimestre "YYYY-TQ" d'un nom de fichier ARCEP, sinon null. Port du builder serveur. */
    fun extractQuarter(name: String): String? {
        for ((regex, yearGroup, quarterGroup) in QUARTER_PATTERNS) {
            val match = regex.find(name) ?: continue
            val year = match.groupValues[yearGroup]
            val quarter = match.groupValues[quarterGroup]
            if (year.isNotEmpty() && quarter.isNotEmpty()) return "$year-T$quarter"
        }
        return null
    }

    private val QUARTER_PATTERNS = listOf(
        Triple(Regex("""(20\d{2})[\s_-]*(?:t|q|trim(?:estre)?)[\s_-]*([1-4])""", RegexOption.IGNORE_CASE), 1, 2),
        Triple(Regex("""(?:t|q|trim(?:estre)?)[\s_-]*([1-4])[\s_-]*(20\d{2})""", RegexOption.IGNORE_CASE), 2, 1),
        Triple(Regex("""([1-4])(?:er|e)?[\s_-]*(?:trim(?:estre)?)[\s_-]*(20\d{2})""", RegexOption.IGNORE_CASE), 2, 1),
    )

    private fun JsonElement?.stringOrNull(): String? =
        this?.takeIf { it.isJsonPrimitive }?.asString
}
