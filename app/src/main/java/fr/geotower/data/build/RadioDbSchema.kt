package fr.geotower.data.build

/**
 * Schema de la base radio non-mobile `geotower_fr_radio.db` (tables `WITHOUT ROWID`) et tables de
 * staging du builder on-device. Port fidele de `create_schema` (docs/server/fr_radio_db_builder.py).
 *
 * Les constantes reproduisent celles attendues par [fr.geotower.data.db.RadioDatabaseValidator]
 * (schema_version = 1, country_code = FR, source = ANFR_RADIO). Le staging SQLite (tables `stg_r_*`
 * dans le meme fichier, purgees puis VACUUM en fin de build) borne la RAM comme [GeoTowerDbBuilder] :
 * les tables ANFR de plusieurs millions de lignes ne sont jamais materialisees en memoire.
 */
object RadioDbSchema {
    const val SCHEMA_VERSION = 1
    const val COUNTRY_CODE = "FR"
    const val COUNTRY_NAME = "France"
    const val SOURCE = "ANFR_RADIO"

    /** Tables finales, strictement conformes au validateur (identifiants et types verifies bit a bit). */
    val CREATE_TABLE_STATEMENTS = listOf(
        "CREATE TABLE non_mobile_site (" +
            "sta_nm_anfr TEXT NOT NULL, sup_id TEXT NOT NULL, adm_id INTEGER, " +
            "lat_e6 INTEGER NOT NULL, lon_e6 INTEGER NOT NULL, nat_id INTEGER, tpo_id INTEGER, " +
            "height_dm INTEGER, code_insee TEXT, service_mask INTEGER NOT NULL, system_mask INTEGER NOT NULL, " +
            "emitter_count INTEGER NOT NULL, antenna_count INTEGER NOT NULL, freq_range_count INTEGER NOT NULL, " +
            "min_freq_khz INTEGER, max_freq_khz INTEGER, PRIMARY KEY (sta_nm_anfr, sup_id)) WITHOUT ROWID",
        "CREATE TABLE non_mobile_detail (" +
            "sta_nm_anfr TEXT NOT NULL, sup_id TEXT NOT NULL, detail_z TEXT NOT NULL, " +
            "PRIMARY KEY (sta_nm_anfr, sup_id)) WITHOUT ROWID",
        "CREATE TABLE ref_actor (adm_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
        "CREATE TABLE ref_nature (nat_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
        "CREATE TABLE ref_owner (tpo_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
        "CREATE TABLE ref_type_antenne (tae_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
        "CREATE TABLE metadata (" +
            "version TEXT NOT NULL PRIMARY KEY, schema_version INTEGER NOT NULL, country_code TEXT NOT NULL, " +
            "country_name TEXT, source TEXT NOT NULL, date_maj_anfr TEXT, zip_version TEXT, " +
            "row_count INTEGER NOT NULL) WITHOUT ROWID",
    )

    /** Index de lecture (crees apres insertion, comme le builder serveur). */
    val CREATE_INDEX_STATEMENTS = listOf(
        "CREATE INDEX idx_non_mobile_site_lat_lon ON non_mobile_site(lat_e6, lon_e6)",
        "CREATE INDEX idx_non_mobile_site_service ON non_mobile_site(service_mask)",
        "CREATE INDEX idx_non_mobile_site_actor ON non_mobile_site(adm_id)",
    )

    /**
     * Tables de staging on-device. Les gros fichiers ANFR (SUP_STATION/ANTENNE/EMETTEUR/BANDE) sont
     * poses ici puis agreges par des scans SQL indexes ; seuls les accumulateurs bornes au nombre de
     * supports non-mobiles (~200 k) restent en RAM.
     */
    // NOTE perf : `ix_stg_r_bande_emr` est cree APRES le chargement de stg_r_bande (cf. RadioDbBuilder),
    // pas ici — inserer des millions de lignes dans une table indexee maintient le B-tree a chaque ligne.
    // (L'ancien index sur stg_r_antenne(sta,sup) a ete supprime : inutilise, le scan antennes est complet.)
    val STAGING_STATEMENTS = listOf(
        "CREATE TABLE stg_r_station (sta TEXT PRIMARY KEY, adm_id INTEGER)",
        "CREATE TABLE stg_r_support (sta TEXT, sup TEXT, lat_e6 INTEGER, lon_e6 INTEGER, " +
            "nat_id INTEGER, tpo_id INTEGER, height_dm INTEGER, code_insee TEXT, address TEXT, " +
            "PRIMARY KEY (sta, sup))",
        "CREATE TABLE stg_r_antenne (aer TEXT PRIMARY KEY, sta TEXT, sup TEXT, tae_id INTEGER, sample TEXT)",
        "CREATE TABLE stg_r_emetteur (emr TEXT, sta TEXT, aer TEXT, system TEXT)",
        "CREATE TABLE stg_r_bande (emr TEXT, f_deb TEXT, f_fin TEXT, unite TEXT)",
        "CREATE TABLE stg_r_single (sta TEXT PRIMARY KEY, sup TEXT)",
        "CREATE TABLE stg_r_sel (emr TEXT PRIMARY KEY, sta TEXT, sup TEXT)",
    )

    val STAGING_TABLES = listOf(
        "stg_r_station", "stg_r_support", "stg_r_antenne", "stg_r_emetteur",
        "stg_r_bande", "stg_r_single", "stg_r_sel",
    )
}
