package fr.geotower.data.build

/**
 * Schema exact de `geotower_fr.db` reproduit a l'identique depuis le builder serveur
 * (`create_schema` de docs/server/build_fr_anfr_db.py et `ensure_stats_tables` de
 * docs/server/fr_anfr_stats.py), plus les estampilles Room.
 *
 * La DDL est reproduite verbatim pour que la base construite localement soit acceptee
 * a la fois par [fr.geotower.data.db.GeoTowerDatabaseValidator] (structure) et par Room
 * (identity_hash + user_version). Ne pas s'ecarter de ces chaines sans mettre a jour le
 * builder serveur ET l'identity_hash du schema Room.
 */
object GeoTowerDbSchema {
    const val SCHEMA_VERSION = 7
    const val COUNTRY_CODE = "FR"
    const val COUNTRY_NAME = "France"
    const val SOURCE = "ANFR"

    /**
     * Identity hash du schema Room. Doit rester synchronise avec `ROOM_IDENTITY_HASH`
     * de build_fr_anfr_db.py et avec le schema des entites Room. S'il change, Room refuse
     * d'ouvrir la base.
     */
    const val ROOM_IDENTITY_HASH = "f92129b45cc37b357c5ecb8e0ba597f0"

    /** DDL des tables, dans l'ordre du builder serveur. */
    val CREATE_TABLE_STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `localisation` (`id_anfr` TEXT NOT NULL, `operateur_id` INTEGER, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `azimuts` TEXT, `code_insee` TEXT, `azimuts_fh` TEXT, `tech_mask` INTEGER NOT NULL, `band_mask` INTEGER NOT NULL, `arcep_nidt` TEXT, `is_zb` INTEGER NOT NULL, PRIMARY KEY(`id_anfr`))",
        "CREATE TABLE IF NOT EXISTS `technique` (`id_anfr` TEXT NOT NULL, `adm_id` INTEGER, `statut_id` INTEGER, `date_implantation` TEXT, `date_service` TEXT, `date_modif` TEXT, `details_frequences` TEXT, `adresse` TEXT, `has_active` INTEGER NOT NULL, PRIMARY KEY(`id_anfr`))",
        "CREATE TABLE IF NOT EXISTS `support` (`id_anfr` TEXT NOT NULL, `id_support` TEXT NOT NULL, `nat_id` INTEGER, `tpo_id` INTEGER, `hauteur` REAL, PRIMARY KEY(`id_anfr`, `id_support`))",
        "CREATE TABLE IF NOT EXISTS `antenne` (`aer_id` TEXT NOT NULL, `id_anfr` TEXT NOT NULL, `id_support` TEXT, `tae_id` INTEGER, `azimut` INTEGER, `hauteur_bas` REAL, `is_fh` INTEGER NOT NULL, PRIMARY KEY(`aer_id`))",
        "CREATE TABLE IF NOT EXISTS `ref_operateur` (`id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `ref_nature` (`nat_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`nat_id`))",
        "CREATE TABLE IF NOT EXISTS `ref_proprietaire` (`tpo_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`tpo_id`))",
        "CREATE TABLE IF NOT EXISTS `ref_exploitant` (`adm_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`adm_id`))",
        "CREATE TABLE IF NOT EXISTS `ref_type_antenne` (`tae_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`tae_id`))",
        "CREATE TABLE IF NOT EXISTS `ref_systeme` (`id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `ref_statut` (`id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `ref_commune` (`code_insee` TEXT NOT NULL, `nom` TEXT NOT NULL, PRIMARY KEY(`code_insee`))",
        "CREATE TABLE IF NOT EXISTS `metadata` (`version` TEXT NOT NULL, `schema_version` INTEGER NOT NULL, `country_code` TEXT NOT NULL, `country_name` TEXT, `source` TEXT NOT NULL, `date_maj_anfr` TEXT, `zip_version` TEXT, PRIMARY KEY(`version`))",
        "CREATE TABLE IF NOT EXISTS `source_versions` (`source_key` TEXT NOT NULL, `source_value` TEXT NOT NULL, PRIMARY KEY(`source_key`))",
        "CREATE TABLE IF NOT EXISTS radio_stat_current (operator_name TEXT NOT NULL, category TEXT NOT NULL, item_key TEXT NOT NULL, label TEXT, total_count INTEGER NOT NULL, active_count INTEGER NOT NULL, PRIMARY KEY (operator_name, category, item_key))",
        "CREATE TABLE IF NOT EXISTS radio_stat_weekly (week_key TEXT NOT NULL, week_start TEXT, source_date TEXT, operator_name TEXT NOT NULL, category TEXT NOT NULL, item_key TEXT NOT NULL, label TEXT, total_count INTEGER NOT NULL, active_count INTEGER NOT NULL, PRIMARY KEY (week_key, operator_name, category, item_key))"
    )

    /** Table technique de Room portant l'identity_hash. */
    const val CREATE_ROOM_MASTER_TABLE =
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"

    /** Insertion de l'identity_hash attendu par Room. */
    const val INSERT_ROOM_IDENTITY =
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$ROOM_IDENTITY_HASH')"

    /**
     * A executer en tout dernier (apres commit / eventuel VACUUM). Room lit
     * `PRAGMA user_version` a l'ouverture et exige qu'il vaille [SCHEMA_VERSION] ;
     * sinon il tente une migration introuvable et refuse d'ouvrir la base.
     */
    const val SET_USER_VERSION = "PRAGMA user_version = $SCHEMA_VERSION"
}
