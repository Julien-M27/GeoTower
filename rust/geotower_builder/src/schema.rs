pub const MOBILE_SCHEMA_VERSION: i32 = 8;
pub const MOBILE_COUNTRY_CODE: &str = "FR";
pub const MOBILE_COUNTRY_NAME: &str = "France";
pub const MOBILE_SOURCE: &str = "ANFR";
pub const ROOM_IDENTITY_HASH: &str = "10080ab6df5dd01987800ed39f1c3b46";

pub const MOBILE_CREATE_TABLES: &[&str] = &[
    "CREATE TABLE IF NOT EXISTS `localisation` (`id_anfr` TEXT NOT NULL, `operateur_id` INTEGER, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `azimuts` TEXT, `code_insee` TEXT, `azimuts_fh` TEXT, `tech_mask` INTEGER NOT NULL, `band_mask` INTEGER NOT NULL, `arcep_nidt` TEXT, `is_zb` INTEGER NOT NULL, PRIMARY KEY(`id_anfr`))",
    "CREATE TABLE IF NOT EXISTS `technique` (`id_anfr` TEXT NOT NULL, `adm_id` INTEGER, `statut_id` INTEGER, `date_implantation` TEXT, `date_service` TEXT, `date_modif` TEXT, `details_frequences` TEXT, `adresse` TEXT, `has_active` INTEGER NOT NULL, `details_azimuts_frequences` TEXT, PRIMARY KEY(`id_anfr`))",
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
    "CREATE TABLE IF NOT EXISTS radio_stat_weekly (week_key TEXT NOT NULL, week_start TEXT, source_date TEXT, operator_name TEXT NOT NULL, category TEXT NOT NULL, item_key TEXT NOT NULL, label TEXT, total_count INTEGER NOT NULL, active_count INTEGER NOT NULL, PRIMARY KEY (week_key, operator_name, category, item_key))",
    "CREATE TABLE IF NOT EXISTS dept_stat_current (dept_code TEXT NOT NULL, dept_name TEXT, region_code TEXT, area_km2 REAL, population INTEGER, population_year TEXT, supports INTEGER NOT NULL, supports_active INTEGER NOT NULL, stations INTEGER NOT NULL, stations_active INTEGER NOT NULL, antennas INTEGER NOT NULL, antennas_active INTEGER NOT NULL, antennas_fh INTEGER NOT NULL, stations_per_support REAL, antennas_per_station REAL, supports_per_km2 REAL, stations_per_km2 REAL, antennas_per_km2 REAL, supports_per_1k_hab REAL, stations_per_1k_hab REAL, antennas_per_1k_hab REAL, hab_per_support REAL, hab_per_station REAL, hab_per_antenna REAL, PRIMARY KEY (dept_code))",
    "CREATE TABLE IF NOT EXISTS dept_stat_operator_tech (dept_code TEXT NOT NULL, operator_name TEXT NOT NULL, tech TEXT NOT NULL, supports INTEGER NOT NULL, supports_active INTEGER NOT NULL, stations INTEGER NOT NULL, stations_active INTEGER NOT NULL, antennas INTEGER NOT NULL, antennas_active INTEGER NOT NULL, PRIMARY KEY (dept_code, operator_name, tech))",
    "CREATE TABLE IF NOT EXISTS fh_link (id_anfr_a TEXT NOT NULL, lat_a REAL NOT NULL, lon_a REAL NOT NULL, id_anfr_b TEXT NOT NULL, lat_b REAL NOT NULL, lon_b REAL NOT NULL, min_lat REAL NOT NULL, max_lat REAL NOT NULL, min_lon REAL NOT NULL, max_lon REAL NOT NULL, operateur_id INTEGER, adm_id INTEGER, operator_label TEXT, azimut_ab REAL NOT NULL, distance_m INTEGER NOT NULL, PRIMARY KEY (id_anfr_a, id_anfr_b, operator_label))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
];

pub const CREATE_FH_LINK_INDEX: &str =
    "CREATE INDEX IF NOT EXISTS ix_fh_link_bbox ON fh_link (min_lat, max_lat, min_lon, max_lon)";

pub const INSERT_ROOM_IDENTITY: &str =
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '10080ab6df5dd01987800ed39f1c3b46')";

pub fn mobile_staging_statements(prefix: &str) -> Vec<String> {
    vec![
        format!("CREATE TABLE {prefix}stg_bande (emr_id TEXT, f_deb REAL, f_fin REAL, unite TEXT, f_deb_raw TEXT, f_fin_raw TEXT)"),
        format!("CREATE TABLE {prefix}stg_emr_freqs (emr_id TEXT PRIMARY KEY, freqs_text TEXT)"),
        format!("CREATE TABLE {prefix}stg_emetteur (id_anfr TEXT, emr_id TEXT, aer_id TEXT, systeme TEXT)"),
        format!("CREATE TABLE {prefix}stg_fh_aer (aer_id TEXT PRIMARY KEY)"),
        format!("CREATE TABLE {prefix}stg_antenne (aer_id TEXT PRIMARY KEY, id_anfr TEXT, sup_id TEXT, tae_id INTEGER, azimut INTEGER, hauteur_bas REAL, is_fh INTEGER, physique TEXT)"),
        format!("CREATE TABLE {prefix}stg_antenne_sta (id_anfr TEXT, aer_id TEXT, azimut INTEGER, is_fh INTEGER)"),
        format!("CREATE TABLE {prefix}stg_support (id_anfr TEXT, sup_id TEXT, nat_id INTEGER, tpo_id INTEGER, hauteur REAL, PRIMARY KEY(id_anfr, sup_id))"),
        format!("CREATE TABLE {prefix}stg_sysstatus (id_anfr TEXT, systeme_upper TEXT, systeme TEXT, statut TEXT, emr_dt TEXT, PRIMARY KEY(id_anfr, systeme_upper))"),
        format!("CREATE TABLE {prefix}stg_sysazimut (id_anfr TEXT, systeme_upper TEXT, azimut INTEGER, PRIMARY KEY(id_anfr, systeme_upper, azimut))"),
        format!("CREATE TABLE {prefix}stg_azimut_json (id_anfr TEXT PRIMARY KEY, details TEXT)"),
        format!("CREATE TABLE {prefix}stg_details_extra (id_anfr TEXT PRIMARY KEY, details TEXT)"),
        format!("CREATE TABLE {prefix}stg_station_final (id_anfr TEXT PRIMARY KEY, operateur_id INTEGER, operator_label TEXT, latitude REAL, longitude REAL, statut_id INTEGER, statut_label TEXT, adm_id INTEGER, date_imp TEXT, date_ser TEXT, date_mod TEXT, adresse TEXT, code_insee TEXT, tech_mask INTEGER, band_mask INTEGER, has_active INTEGER, azimuts TEXT, azimuts_fh TEXT)"),
        format!("CREATE TABLE {prefix}stg_arcep (id_anfr TEXT, operator_upper TEXT, nidt TEXT, is_zb INTEGER, PRIMARY KEY(id_anfr, operator_upper))"),
        format!("CREATE TABLE {prefix}stg_details (id_anfr TEXT PRIMARY KEY, details TEXT)"),
    ]
}

pub const RADIO_SCHEMA_VERSION: i32 = 1;
pub const RADIO_COUNTRY_CODE: &str = "FR";
pub const RADIO_COUNTRY_NAME: &str = "France";
pub const RADIO_SOURCE: &str = "ANFR_RADIO";

pub const RADIO_CREATE_TABLES: &[&str] = &[
    "CREATE TABLE non_mobile_site (sta_nm_anfr TEXT NOT NULL, sup_id TEXT NOT NULL, adm_id INTEGER, lat_e6 INTEGER NOT NULL, lon_e6 INTEGER NOT NULL, nat_id INTEGER, tpo_id INTEGER, height_dm INTEGER, code_insee TEXT, service_mask INTEGER NOT NULL, system_mask INTEGER NOT NULL, emitter_count INTEGER NOT NULL, antenna_count INTEGER NOT NULL, freq_range_count INTEGER NOT NULL, min_freq_khz INTEGER, max_freq_khz INTEGER, PRIMARY KEY (sta_nm_anfr, sup_id)) WITHOUT ROWID",
    "CREATE TABLE non_mobile_detail (sta_nm_anfr TEXT NOT NULL, sup_id TEXT NOT NULL, detail_z TEXT NOT NULL, PRIMARY KEY (sta_nm_anfr, sup_id)) WITHOUT ROWID",
    "CREATE TABLE ref_actor (adm_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
    "CREATE TABLE ref_nature (nat_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
    "CREATE TABLE ref_owner (tpo_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
    "CREATE TABLE ref_type_antenne (tae_id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID",
    "CREATE TABLE metadata (version TEXT NOT NULL PRIMARY KEY, schema_version INTEGER NOT NULL, country_code TEXT NOT NULL, country_name TEXT, source TEXT NOT NULL, date_maj_anfr TEXT, zip_version TEXT, row_count INTEGER NOT NULL) WITHOUT ROWID",
];

pub const RADIO_CREATE_INDEXES: &[&str] = &[
    "CREATE INDEX idx_non_mobile_site_lat_lon ON non_mobile_site(lat_e6, lon_e6)",
    "CREATE INDEX idx_non_mobile_site_service ON non_mobile_site(service_mask)",
    "CREATE INDEX idx_non_mobile_site_actor ON non_mobile_site(adm_id)",
];

pub fn radio_staging_statements(prefix: &str) -> Vec<String> {
    vec![
        format!("CREATE TABLE {prefix}stg_r_station (sta TEXT PRIMARY KEY, adm_id INTEGER)"),
        format!("CREATE TABLE {prefix}stg_r_support (sta TEXT, sup TEXT, lat_e6 INTEGER, lon_e6 INTEGER, nat_id INTEGER, tpo_id INTEGER, height_dm INTEGER, code_insee TEXT, address TEXT, PRIMARY KEY (sta, sup))"),
        format!("CREATE TABLE {prefix}stg_r_antenne (aer TEXT PRIMARY KEY, sta TEXT, sup TEXT, tae_id INTEGER, sample TEXT)"),
        format!("CREATE TABLE {prefix}stg_r_emetteur (emr TEXT, sta TEXT, aer TEXT, system TEXT)"),
        format!("CREATE TABLE {prefix}stg_r_bande (emr TEXT, f_deb TEXT, f_fin TEXT, unite TEXT)"),
        format!("CREATE TABLE {prefix}stg_r_single (sta TEXT PRIMARY KEY, sup TEXT)"),
        format!("CREATE TABLE {prefix}stg_r_sel (emr TEXT PRIMARY KEY, sta TEXT, sup TEXT)"),
    ]
}
