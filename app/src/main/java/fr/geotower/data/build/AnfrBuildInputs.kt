package fr.geotower.data.build

/**
 * Les six sources de lignes ANFR alimentant le build : le CSV hebdomadaire (observatoire) et
 * les cinq fichiers du ZIP mensuel (SUP_STATION / SUP_BANDE / SUP_EMETTEUR / SUP_ANTENNE /
 * SUP_SUPPORT). Chaque source est iteree une seule fois par [GeoTowerDbBuilder] (compatible
 * avec une lecture en flux depuis le reseau/ZIP en Slice 3).
 */
class AnfrSources(
    val weekly: Iterable<AnfrCsvRow>,
    val stations: Iterable<AnfrCsvRow>,
    val bandes: Iterable<AnfrCsvRow>,
    val emetteurs: Iterable<AnfrCsvRow>,
    val antennes: Iterable<AnfrCsvRow>,
    val supports: Iterable<AnfrCsvRow>,
)

/**
 * Referentiels `id -> libelle` (issus du ZIP mensuel) et communes `code_insee -> nom`
 * (issues de geo.api.gouv.fr). Cles sous forme de chaine, comme le builder serveur.
 */
class AnfrReferences(
    val nature: Map<String, String> = emptyMap(),
    val proprietaire: Map<String, String> = emptyMap(),
    val exploitant: Map<String, String> = emptyMap(),
    val typeAntenne: Map<String, String> = emptyMap(),
    val communes: Map<String, String> = emptyMap(),
)

/** Metadonnee ARCEP rattachee a une cle (id_anfr, operateur en majuscules). */
data class ArcepSiteMeta(val nidt: String?, val isZb: Int)

/** Parametres de la base produite (version comparable par DatabaseVersionPolicy, provenance...). */
class BuildConfig(
    val version: String,
    val zipVersion: String? = null,
    val quarterlyVersion: String? = null,
)

/** Comptes retournes apres construction (diagnostic / tests). */
data class BuildResult(
    val stations: Int,
    val supports: Int,
    val antennes: Int,
)
