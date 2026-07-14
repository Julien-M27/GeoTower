package fr.geotower.data.outages

/**
 * Les 4 fichiers officiels de pannes publiés par les opérateurs mobiles FR.
 *
 * Miroir de `OPERATOR_SOURCES` dans `docs/server/build_sites_hs.py` : c'est la MÊME vérité que
 * le builder serveur. `url` sert à la tranche « téléchargement » (T3) ; `codeColumns` indique
 * la/les colonne(s) portant le code site interne opérateur.
 */
enum class OperatorOutageSource(
    val key: String,
    val label: String,
    val url: String,
    val codeColumns: List<String>,
) {
    FREE(
        key = "free",
        label = "Free Mobile",
        url = "https://mobile.free.fr/static/pannes-antennes-relais.csv",
        codeColumns = listOf("code_site_op"),
    ),
    SFR(
        key = "sfr",
        label = "SFR",
        url = "https://www.sfr.fr/media/export-arcep/siteshorsservices.csv",
        codeColumns = listOf("code_site_op"),
    ),
    BOUYGUES(
        key = "bouygues",
        label = "Bouygues Telecom",
        url = "https://www.bouyguestelecom.fr/static/com/assets/reseau/siteshs/downloads/antennesindisponibles.csv",
        codeColumns = listOf("code_si"),
    ),
    ORANGE(
        key = "orange",
        label = "Orange",
        url = "https://couverture-mobile.orange.fr/mapV3/siteshs/data/Liste_des_antennes_provisoirement_hors_service.csv",
        codeColumns = listOf("code_site_op"),
    );

    companion object {
        fun fromKey(key: String): OperatorOutageSource? = entries.firstOrNull { it.key == key }
    }
}
