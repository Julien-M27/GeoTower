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

    /** Hotes HTTPS autorises (incl. les cibles de redirection de data.gouv et du bucket ARCEP). */
    val ALLOWED_HOSTS = setOf(
        "www.data.gouv.fr",
        "object.files.data.gouv.fr",
        "static.data.gouv.fr",
        "data.anfr.fr",
        "data.arcep.fr",
        // Les CSV de data.arcep.fr redirigent (302) vers le stockage objet OVH de l'ARCEP.
        "arcep.s3.rbx.io.cloud.ovh.net",
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

    /**
     * Raccourci ARCEP vers le repertoire du **trimestre courant** ("Mon Reseau Mobile"). Son HTML
     * liste les CSV de sites du trimestre (Metropole, Outremer, comptage 5G). On resout ce listing
     * plutot que le repertoire parent : `last/` pointe toujours vers la publication la plus recente.
     */
    const val ARCEP_SITES_LAST_URL = "https://data.arcep.fr/mobile/sites/last/"

    /** `href="....csv"` d'un listing ARCEP (liens relatifs ou absolus). */
    private val ARCEP_CSV_HREF_REGEX = Regex(
        """href\s*=\s*["']([^"'>]+?\.csv)["']""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extrait de la page de listing ARCEP ([ARCEP_SITES_LAST_URL]) les URLs des CSV « sites » a
     * charger pour l'enrichissement `arcep_nidt`/`is_zb` (Metropole + Outremer), en ecartant les
     * fichiers de comptage/historique 5G. Les liens du listing sont relatifs au repertoire courant :
     * on les resout en absolu contre [listingUrl] (dont le slash final est significatif). Ne retourne
     * que des URLs sur hotes autorises ; liste vide si rien d'exploitable (l'ARCEP est optionnel).
     */
    fun resolveArcepSitesCsvUrls(
        listingHtml: String,
        listingUrl: String = ARCEP_SITES_LAST_URL,
    ): List<String> {
        val base = try {
            URI(listingUrl)
        } catch (_: Exception) {
            return emptyList()
        }
        return ARCEP_CSV_HREF_REGEX.findAll(listingHtml)
            .mapNotNull { match -> runCatching { base.resolve(match.groupValues[1]).toString() }.getOrNull() }
            .filter { url ->
                val name = url.substringAfterLast('/').substringBefore('?').lowercase()
                // Metropole + Outremer, mais PAS "..._5G_historique_comptage.csv" (pas des sites par ligne).
                name.endsWith(".csv") && name.contains("sites") &&
                    !name.contains("historique") && !name.contains("comptage")
            }
            .filter { isAllowedHost(it) }
            .distinct()
            .toList()
    }

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

    /** 8 chiffres en tete d'un nom d'export ANFR = la periode des DONNEES (formats variables). */
    private val DATA_DATE_REGEX = Regex("""^(\d{8})""")

    /**
     * Cle de tri **normalisee YYYYMMDD** extraite du prefixe date d'un nom d'export ANFR. Gere DEUX
     * formats historiques :
     *  - moderne `YYYYMMDD` (ex. `20260630-export-etalab-data.zip`, depuis ~2018) ;
     *  - ancien `DDMMYYYY` (ex. `31052018_export_etalab_data.zip`, 2015-2018) — reordonne en YYYYMMDD.
     * Sans cette normalisation, un tri de CHAINES mettait `31052018` AVANT `20260630` (`'3' > '2'`),
     * donc l'app choisissait le vieux fichier de mai 2018 (perime + Latin-1). "" si pas de date en tete.
     */
    internal fun dataDateKey(filename: String): String {
        val digits = DATA_DATE_REGEX.find(filename)?.groupValues?.getOrNull(1) ?: return ""
        // Format moderne : commence par l'annee (19xx/20xx) -> deja triable tel quel.
        if (digits.startsWith("19") || digits.startsWith("20")) return digits
        // Ancien format DDMMYYYY : l'annee est en fin -> reordonne AAAAMMJJ.
        val year = digits.substring(4, 8)
        return if (year.startsWith("19") || year.startsWith("20")) {
            year + digits.substring(2, 4) + digits.substring(0, 2)
        } else {
            "" // format non reconnu : ne doit pas gagner le tri
        }
    }

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

        data class Zip(val url: String, val filename: String, val dataDate: String, val modified: String)
        val zips = ArrayList<Zip>()
        for (element in resources) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val url = obj.get("url").stringOrNull() ?: continue
            val format = obj.get("format").stringOrNull()?.lowercase().orEmpty()
            val filename = url.substringAfterLast('/').lowercase()
            val isZip = format == "zip" || filename.endsWith(".zip")
            if (!isZip || !isAllowedHost(url)) continue
            // La date des DONNEES est le prefixe du nom de fichier (ex. 20260630-export-etalab-data.zip),
            // PAS la date d'upload : data.gouv re-publie parfois de vieux exports (last_modified recent
            // mais donnees de 2022). Trier sur last_modified choisissait alors un fichier perime, plus
            // petit (sites manquants) ET en Latin-1 (accents casses). On trie donc sur la date de donnees,
            // normalisee en YYYYMMDD (les vieux exports 2015-2018 sont en DDMMYYYY : voir dataDateKey).
            val dataDate = dataDateKey(filename)
            val modified = obj.get("last_modified").stringOrNull()
                ?: obj.get("created_at").stringOrNull()
                ?: ""
            zips.add(Zip(url, filename, dataDate, modified))
        }
        if (zips.isEmpty()) return null

        // Tri par date de donnees (prefixe) DESC, puis last_modified en secours pour les fichiers non dates.
        val sorted = zips.sortedWith(compareByDescending<Zip> { it.dataDate }.thenByDescending { it.modified })
        val data = sorted.firstOrNull { it.filename.contains("data") }
            ?: sorted.firstOrNull { !it.filename.contains("ref") }
            ?: return null
        // Reference du MEME export (meme date de donnees) de preference, sinon la plus recente.
        val ref = sorted.firstOrNull { it.filename.contains("ref") && it.dataDate == data.dataDate }
            ?: sorted.firstOrNull { it.filename.contains("ref") }
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
