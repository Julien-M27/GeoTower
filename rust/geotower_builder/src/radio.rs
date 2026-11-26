use crate::parsing::{
    clean_text, dms_to_e6, encode_frequency_details, float_or_none, format_freq_range,
    frequency_to_khz, int_or_none, normalize_id_anfr, CsvTable,
};
use crate::schema::{
    radio_staging_statements, RADIO_COUNTRY_CODE, RADIO_COUNTRY_NAME, RADIO_CREATE_INDEXES,
    RADIO_CREATE_TABLES, RADIO_SCHEMA_VERSION, RADIO_SOURCE,
};
use crate::{BResult, ProgressSink};
use rusqlite::{params, Connection};
use std::collections::{HashMap, HashSet};

pub const SERVICE_BROADCAST: i32 = 1 << 0;
pub const SERVICE_PRIVATE: i32 = 1 << 1;
pub const SERVICE_RAIL: i32 = 1 << 2;
pub const SERVICE_TRANSPORT: i32 = 1 << 3;
pub const SERVICE_FH: i32 = 1 << 4;
pub const SERVICE_SATELLITE: i32 = 1 << 5;
pub const SERVICE_RADAR: i32 = 1 << 6;
pub const SERVICE_OTHER: i32 = 1 << 7;

const PUBLIC_MOBILE_MARKERS: &[&str] = &[
    "BOUYGUES",
    "ORANGE",
    "SFR",
    "SOCIETE FRANCAISE DU RADIOTELEPHONE",
    "FREE MOBILE",
    "FREE CARAIB",
    "DIGICEL",
    "OUTREMER",
    "SRR",
    "ZEOP",
    "MAORE",
    "DAUPHIN",
    "TELCO OI",
    "VITI",
    "PMT",
    "VODAFONE",
    "SPM TELECOM",
    "ONATI",
    "GLOBALTEL",
    "UTS CARAIBE",
    "OPT",
];

fn is_mobile_like_system(system: &str) -> bool {
    let u = system.to_ascii_uppercase();
    if u.starts_with("GSM 900")
        || u.starts_with("GSM 1800")
        || u.starts_with("UMTS")
        || u.starts_with("5G NR")
    {
        return true;
    }
    u.starts_with("LTE") && !u.contains(" P")
}

fn is_public_mobile_operator(label: &str) -> bool {
    let u = label.to_ascii_uppercase();
    PUBLIC_MOBILE_MARKERS.iter().any(|m| u.contains(m))
}

fn system_key(system: &str) -> &'static str {
    let u = system.to_ascii_uppercase();
    if u.starts_with("FM") {
        "FM"
    } else if u.starts_with("RDF DVB") {
        "DVB_T"
    } else if u.starts_with("RDF T-DAB") {
        "DAB"
    } else if u.starts_with("RDF AM") {
        "AM"
    } else if u.starts_with("FH") {
        "FH"
    } else if u.starts_with("GSM R") {
        "GSM_R"
    } else if u.starts_with("PMR") {
        "PMR"
    } else if u.starts_with("TETRA") || u.starts_with("TETRAPOL") {
        "TETRA"
    } else if u.starts_with("RMU") {
        "POCSAG"
    } else if u.starts_with("COM TER") {
        "COM_TER"
    } else if u.starts_with("COM MAR") {
        "COM_MAR"
    } else if u.starts_with("AIS") {
        "AIS"
    } else if u.starts_with("SAT") {
        "SAT"
    } else if u.starts_with("RDR") {
        "RADAR"
    } else if u.starts_with("BLR") {
        "BLR"
    } else if u.starts_with("LTE") {
        "LTE_PRIVATE"
    } else if u.starts_with("5G BROADCAST") {
        "BROADCAST_5G"
    } else if u == "RS" {
        "METEO_RS"
    } else if u.starts_with("TELEM") || u.starts_with("TELECD") {
        "TELEMETRY"
    } else {
        "OTHER"
    }
}

fn system_bit(key: &str) -> i32 {
    match key {
        "FM" => 1 << 0,
        "DVB_T" => 1 << 1,
        "DAB" => 1 << 2,
        "AM" => 1 << 3,
        "FH" => 1 << 4,
        "GSM_R" => 1 << 5,
        "PMR" => 1 << 6,
        "TETRA" => 1 << 7,
        "POCSAG" => 1 << 8,
        "COM_TER" => 1 << 9,
        "COM_MAR" => 1 << 10,
        "AIS" => 1 << 11,
        "SAT" => 1 << 12,
        "RADAR" => 1 << 13,
        "BLR" => 1 << 14,
        "LTE_PRIVATE" => 1 << 15,
        "BROADCAST_5G" => 1 << 16,
        "METEO_RS" => 1 << 17,
        "TELEMETRY" => 1 << 18,
        _ => 1 << 19,
    }
}

fn service_for(system: &str, actor_label: &str) -> i32 {
    let u = system.to_ascii_uppercase();
    let au = actor_label.to_ascii_uppercase();
    if u.starts_with("FH") {
        SERVICE_FH
    } else if u.starts_with("FM")
        || u.starts_with("RDF DVB")
        || u.starts_with("RDF T-DAB")
        || u.starts_with("RDF AM")
        || u.starts_with("5G BROADCAST")
    {
        SERVICE_BROADCAST
    } else if au.contains("SNCF") || u.starts_with("GSM R") {
        SERVICE_RAIL
    } else if u.starts_with("COM TER")
        || u.starts_with("COM MAR")
        || u.starts_with("AIS")
        || u.starts_with("COM AERTER")
    {
        SERVICE_TRANSPORT
    } else if u.starts_with("SAT") || u.starts_with("GPS") {
        SERVICE_SATELLITE
    } else if u.starts_with("RDR") || u.starts_with("GONIO") {
        SERVICE_RADAR
    } else if u.starts_with("PMR")
        || u.starts_with("TETRA")
        || u.starts_with("TETRAPOL")
        || u.starts_with("RMU")
        || u.starts_with("EM/REC")
        || u.starts_with("REC")
        || u.starts_with("TELEM")
        || u.starts_with("TELECD")
        || u.starts_with("ANM")
        || u.starts_with("LTE")
    {
        SERVICE_PRIVATE
    } else {
        SERVICE_OTHER
    }
}

fn service_names(mask: i32) -> String {
    const ORDERED: &[(i32, &str)] = &[
        (SERVICE_BROADCAST, "Radio/TV"),
        (SERVICE_PRIVATE, "Reseaux prives"),
        (SERVICE_RAIL, "Ferroviaire"),
        (SERVICE_TRANSPORT, "Transport"),
        (SERVICE_FH, "FH"),
        (SERVICE_SATELLITE, "Satellite"),
        (SERVICE_RADAR, "Radar"),
        (SERVICE_OTHER, "Autres"),
    ];
    let mut parts = Vec::new();
    for &(bit, label) in ORDERED {
        if (mask & bit) != 0 {
            parts.push(label);
        }
    }
    parts.join(", ")
}

struct Agg {
    adm_id: Option<i32>,
    service_mask: i32,
    system_mask: i32,
    emitter_count: i32,
    systems: HashMap<String, i32>,
    min_khz: Option<i32>,
    max_khz: Option<i32>,
    freq_range_count: i32,
    freq_samples: Vec<String>,
}

impl Agg {
    fn new(adm_id: Option<i32>) -> Self {
        Self {
            adm_id,
            service_mask: 0,
            system_mask: 0,
            emitter_count: 0,
            systems: HashMap::new(),
            min_khz: None,
            max_khz: None,
            freq_range_count: 0,
            freq_samples: Vec::new(),
        }
    }

    fn add_freq_range(&mut self, start_khz: i32, end_khz: i32, raw_label: String) {
        let low = start_khz.min(end_khz);
        let high = start_khz.max(end_khz);
        self.min_khz = Some(self.min_khz.map_or(low, |m| m.min(low)));
        self.max_khz = Some(self.max_khz.map_or(high, |m| m.max(high)));
        self.freq_range_count += 1;
        if self.freq_samples.len() < 24 && !self.freq_samples.contains(&raw_label) {
            self.freq_samples.push(raw_label);
        }
    }
}

struct AntennaInfo {
    count: i32,
    samples: Vec<String>,
    type_ids: HashSet<i32>,
}

impl AntennaInfo {
    fn new() -> Self {
        Self {
            count: 0,
            samples: Vec::new(),
            type_ids: HashSet::new(),
        }
    }
}

pub struct RadioRefs<'a> {
    pub actor: &'a HashMap<String, String>,
    pub nature: &'a HashMap<String, String>,
    pub owner: &'a HashMap<String, String>,
    pub type_antenne: &'a HashMap<String, String>,
}

pub struct RadioStagingWriter {
    pub conn: Connection,
}

impl RadioStagingWriter {
    pub fn open(built_path: &str, staging_path: &str, cache_kb: i64) -> Result<Self, String> {
        let conn = Connection::open(built_path).map_err(|e| format!("open radio db: {e}"))?;
        conn.execute_batch(&format!(
            "PRAGMA journal_mode = OFF;
             PRAGMA synchronous = OFF;
             PRAGMA temp_store = FILE;
             PRAGMA cache_size = -{cache_kb};
             ATTACH DATABASE '{staging_path}' AS stg;"
        ))
        .map_err(|e| format!("radio pragmas/attach: {e}"))?;
        for sql in RADIO_CREATE_TABLES {
            conn.execute(sql, []).map_err(|e| format!("radio ddl: {e}"))?;
        }
        for sql in radio_staging_statements("stg.") {
            conn.execute(&sql, []).map_err(|e| format!("radio stg ddl: {e}"))?;
        }
        Ok(Self { conn })
    }

    pub fn finish_staging(&self) -> BResult<()> {
        self.conn
            .execute_batch(
                "INSERT OR REPLACE INTO stg_r_single SELECT sta, MIN(sup) FROM stg_r_support GROUP BY sta HAVING COUNT(*) = 1;
                 CREATE INDEX stg.ix_stg_r_bande_emr ON stg_r_bande(emr);",
            )
            .map_err(|e| format!("radio finish_staging: {e}"))?;
        Ok(())
    }
}

pub fn ingest_radio_station(
    stmt: &mut rusqlite::Statement<'_>,
    table: &CsvTable,
    rec: &csv::StringRecord,
) {
    let sta = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
    if sta.is_empty() {
        return;
    }
    let adm_id = int_or_none(table.idx("adm_id").and_then(|i| rec.get(i)));
    let _ = stmt.execute(params![sta, adm_id]);
}

pub fn ingest_radio_support(
    stmt: &mut rusqlite::Statement<'_>,
    table: &CsvTable,
    rec: &csv::StringRecord,
) {
    let sta = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
    let sup = clean_text(table.idx("sup_id").and_then(|i| rec.get(i)));
    if sta.is_empty() || sup.is_empty() {
        return;
    }
    let lat_e6 = dms_to_e6(
        table.idx("cor_nb_dg_lat").and_then(|i| rec.get(i)),
        table.idx("cor_nb_mn_lat").and_then(|i| rec.get(i)),
        table.idx("cor_nb_sc_lat").and_then(|i| rec.get(i)),
        table.idx("cor_cd_ns_lat").and_then(|i| rec.get(i)),
    );
    let lon_e6 = dms_to_e6(
        table.idx("cor_nb_dg_lon").and_then(|i| rec.get(i)),
        table.idx("cor_nb_mn_lon").and_then(|i| rec.get(i)),
        table.idx("cor_nb_sc_lon").and_then(|i| rec.get(i)),
        table.idx("cor_cd_ew_lon").and_then(|i| rec.get(i)),
    );
    let (Some(lat), Some(lon)) = (lat_e6, lon_e6) else {
        return;
    };
    let h_raw = clean_text(table.idx("sup_nm_haut").and_then(|i| rec.get(i)));
    let height_dm = if !h_raw.is_empty() {
        Some((float_or_none(Some(h_raw)).unwrap_or(0.0) * 10.0).round() as i32)
    } else {
        None
    };
    let nat_id = int_or_none(table.idx("nat_id").and_then(|i| rec.get(i)));
    let tpo_id = int_or_none(table.idx("tpo_id").and_then(|i| rec.get(i)));
    let insee_raw = clean_text(table.idx("com_cd_insee").and_then(|i| rec.get(i)));
    let code_insee = if insee_raw.is_empty() { None } else { Some(insee_raw) };

    let mut addr_parts = Vec::with_capacity(5);
    for col in &["adr_lb_lieu", "adr_lb_add1", "adr_lb_add2", "adr_lb_add3", "adr_nm_cp"] {
        let p = clean_text(table.idx(col).and_then(|i| rec.get(i)));
        if !p.is_empty() {
            addr_parts.push(p);
        }
    }
    let address = if addr_parts.is_empty() {
        None
    } else {
        Some(addr_parts.join(", "))
    };
    let _ = stmt.execute(params![sta, sup, lat, lon, nat_id, tpo_id, height_dm, code_insee, address]);
}

pub fn ingest_radio_antenne(
    stmt: &mut rusqlite::Statement<'_>,
    table: &CsvTable,
    rec: &csv::StringRecord,
    type_labels: &HashMap<String, String>,
) {
    let sta = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
    let aer = clean_text(table.idx("aer_id").and_then(|i| rec.get(i)));
    let sup = clean_text(table.idx("sup_id").and_then(|i| rec.get(i)));
    if sta.is_empty() || aer.is_empty() || sup.is_empty() {
        return;
    }
    let tae_id = int_or_none(table.idx("tae_id").and_then(|i| rec.get(i)));
    let type_label = match tae_id {
        Some(id) => type_labels
            .get(&id.to_string())
            .cloned()
            .unwrap_or_else(|| format!("TAE_ID {id}")),
        None => "Type inconnu".to_string(),
    };
    let az = {
        let s = clean_text(table.idx("aer_nb_azimut").and_then(|i| rec.get(i)));
        if s.is_empty() { "N/A" } else { s }
    };
    let h = {
        let s = clean_text(table.idx("aer_nb_alt_bas").and_then(|i| rec.get(i)));
        if s.is_empty() { "N/A" } else { s }
    };
    let sample = format!("{type_label}: {az} deg ({h}m)");
    let _ = stmt.execute(params![aer, sta, sup, tae_id, sample]);
}

pub fn ingest_radio_emetteur(
    stmt: &mut rusqlite::Statement<'_>,
    table: &CsvTable,
    rec: &csv::StringRecord,
) {
    let sta = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
    let emr = clean_text(table.idx("emr_id").and_then(|i| rec.get(i)));
    if sta.is_empty() || emr.is_empty() {
        return;
    }
    let aer = clean_text(table.idx("aer_id").and_then(|i| rec.get(i)));
    let sys_raw = clean_text(table.idx("emr_lb_systeme").and_then(|i| rec.get(i)));
    let system = if sys_raw.is_empty() { "Inconnu" } else { sys_raw };
    let _ = stmt.execute(params![emr, sta, aer, system]);
}

pub fn ingest_radio_bande(
    stmt: &mut rusqlite::Statement<'_>,
    table: &CsvTable,
    rec: &csv::StringRecord,
) {
    let emr = clean_text(table.idx("emr_id").and_then(|i| rec.get(i)));
    if emr.is_empty() {
        return;
    }
    let f_deb = clean_text(table.idx("ban_nb_f_deb").and_then(|i| rec.get(i)));
    let f_fin = clean_text(table.idx("ban_nb_f_fin").and_then(|i| rec.get(i)));
    let u_raw = clean_text(table.idx("ban_fg_unite").and_then(|i| rec.get(i)));
    let unite = if u_raw.is_empty() { "M" } else { u_raw };
    let _ = stmt.execute(params![emr, f_deb, f_fin, unite]);
}

pub fn build_from_staging(
    writer: &RadioStagingWriter,
    refs: &RadioRefs<'_>,
    version: &str,
    zip_version: &str,
    date_maj_anfr: &str,
    allowed_service_mask: i32,
    sink: &mut dyn ProgressSink,
) -> BResult<()> {
    const PHASE_RADIO: i32 = 12; // BuildPhase.RADIO_BUILDING.ordinal
    const EMIT_EVERY: i64 = 100_000;

    let conn = &writer.conn;
    sink.emit(PHASE_RADIO, 62, None, 0);

    let mut aggregates: HashMap<(String, String), Agg> = HashMap::new();
    {
        let tx = conn.unchecked_transaction().map_err(|e| format!("radio tx sel: {e}"))?;
        let mut sel_stmt = tx
            .prepare_cached("INSERT OR REPLACE INTO stg_r_sel VALUES (?1, ?2, ?3)")
            .map_err(|e| format!("radio sel prep: {e}"))?;
        let mut q = conn
            .prepare(
                "SELECT e.sta, e.emr, e.system, a.sta, a.sup, sp.sta, sg.sup, st.adm_id
                 FROM stg_r_emetteur e
                 LEFT JOIN stg_r_antenne a ON e.aer = a.aer
                 LEFT JOIN stg_r_support sp ON sp.sta = a.sta AND sp.sup = a.sup
                 LEFT JOIN stg_r_single sg ON sg.sta = e.sta
                 LEFT JOIN stg_r_station st ON st.sta = e.sta",
            )
            .map_err(|e| format!("radio classify q: {e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("radio classify exec: {e}"))?;
        let mut classified = 0i64;
        while let Some(row) = rows.next().map_err(|e| format!("radio classify row: {e}"))? {
            let e_sta: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            let emr: Option<String> = row.get(1)?;
            let system: String = row.get::<_, Option<String>>(2)?.unwrap_or_else(|| "Inconnu".to_string());
            let a_sta: Option<String> = row.get(3)?;
            let a_sup: Option<String> = row.get(4)?;
            let sp_exists: Option<String> = row.get(5)?;
            let single_sup: Option<String> = row.get(6)?;
            let adm_id: Option<i32> = row.get(7)?;

            let actor_label = adm_id
                .and_then(|id| refs.actor.get(&id.to_string()))
                .map(|s| s.as_str())
                .unwrap_or("");
            let sys_key = system_key(&system);
            let excluded = (is_mobile_like_system(&system) || sys_key == "FH")
                && is_public_mobile_operator(actor_label);
            if !excluded {
                let target = if let Some(sup) = a_sup {
                    if sp_exists.is_some() {
                        Some((a_sta.unwrap_or_else(|| e_sta.clone()), sup))
                    } else {
                        None
                    }
                } else if let Some(single) = single_sup {
                    Some((e_sta, single))
                } else {
                    None
                };
                if let Some((k_sta, k_sup)) = target {
                    if let Some(ref emr_id) = emr {
                        let _ = sel_stmt.execute(params![emr_id, &k_sta, &k_sup]);
                    }
                    let agg = aggregates
                        .entry((k_sta, k_sup))
                        .or_insert_with(|| Agg::new(adm_id));
                    agg.service_mask |= service_for(&system, actor_label);
                    agg.system_mask |= system_bit(sys_key);
                    agg.emitter_count += 1;
                    *agg.systems.entry(system).or_insert(0) += 1;
                }
            }
            classified += 1;
            if classified % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_RADIO, 75, None, classified);
            }
        }
        drop(rows);
        drop(q);
        drop(sel_stmt);
        tx.commit().map_err(|e| format!("radio sel commit: {e}"))?;
    }

    sink.emit(PHASE_RADIO, 78, None, 0);
    {
        let mut q = conn
            .prepare("SELECT s.sta, s.sup, b.f_deb, b.f_fin, b.unite FROM stg_r_bande b JOIN stg_r_sel s ON b.emr = s.emr")
            .map_err(|e| format!("radio band q: {e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("radio band exec: {e}"))?;
        let mut matched = 0i64;
        while let Some(row) = rows.next().map_err(|e| format!("radio band row: {e}"))? {
            let sta: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            let sup: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
            if let Some(agg) = aggregates.get_mut(&(sta, sup)) {
                let start_raw: String = row.get::<_, Option<String>>(2)?.unwrap_or_default();
                let end_raw: String = row.get::<_, Option<String>>(3)?.unwrap_or_default();
                let unite: String = row.get::<_, Option<String>>(4)?.unwrap_or_else(|| "M".to_string());
                if let (Some(s), Some(e)) = (
                    frequency_to_khz(Some(&start_raw), Some(&unite)),
                    frequency_to_khz(Some(&end_raw), Some(&unite)),
                ) {
                    agg.add_freq_range(s, e, format_freq_range(&start_raw, &end_raw, &unite));
                }
            }
            matched += 1;
            if matched % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_RADIO, 84, None, matched);
            }
        }
    }

    sink.emit(PHASE_RADIO, 85, None, 0);
    let mut antenna_info: HashMap<(String, String), AntennaInfo> = HashMap::new();
    let mut used_tae: HashSet<i32> = HashSet::new();
    {
        let mut q = conn
            .prepare("SELECT sta, sup, tae_id, sample FROM stg_r_antenne")
            .map_err(|e| format!("radio ant q: {e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("radio ant exec: {e}"))?;
        while let Some(row) = rows.next().map_err(|e| format!("radio ant row: {e}"))? {
            let sta: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            let sup: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
            let key = (sta, sup);
            if aggregates.contains_key(&key) {
                let info = antenna_info.entry(key).or_insert_with(AntennaInfo::new);
                info.count += 1;
                let tae_id: Option<i32> = row.get(2)?;
                if let Some(tid) = tae_id {
                    info.type_ids.insert(tid);
                    used_tae.insert(tid);
                }
                if info.samples.len() < 12 {
                    info.samples
                        .push(row.get::<_, Option<String>>(3)?.unwrap_or_default());
                }
            }
        }
    }

    sink.emit(PHASE_RADIO, 90, None, 0);
    let mut used_adm: HashSet<i32> = HashSet::new();
    let mut used_nat: HashSet<i32> = HashSet::new();
    let mut used_tpo: HashSet<i32> = HashSet::new();
    let mut total_sites = 0i64;

    {
        let tx = conn.unchecked_transaction().map_err(|e| format!("radio tx emit: {e}"))?;
        let mut site_stmt = tx
            .prepare_cached("INSERT INTO non_mobile_site VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)")
            .map_err(|e| format!("radio site prep: {e}"))?;
        let mut detail_stmt = tx
            .prepare_cached("INSERT INTO non_mobile_detail VALUES (?1, ?2, ?3)")
            .map_err(|e| format!("radio detail prep: {e}"))?;

        let mut q = conn
            .prepare("SELECT sta, sup, lat_e6, lon_e6, nat_id, tpo_id, height_dm, code_insee, address FROM stg_r_support")
            .map_err(|e| format!("radio sup q: {e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("radio sup exec: {e}"))?;
        let mut emitted = 0i64;
        while let Some(row) = rows.next().map_err(|e| format!("radio sup row: {e}"))? {
            let sta: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            let sup: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
            let key = (sta, sup);
            if let Some(agg) = aggregates.get(&key) {
                if (agg.service_mask & allowed_service_mask) != 0 {
                    let lat_e6: Option<i32> = row.get(2)?;
                    let lon_e6: Option<i32> = row.get(3)?;
                    let nat_id: Option<i32> = row.get(4)?;
                    let tpo_id: Option<i32> = row.get(5)?;
                    let height_dm: Option<i32> = row.get(6)?;
                    let code_insee: Option<String> = row.get(7)?;
                    let address: Option<String> = row.get(8)?;
                    let info = antenna_info.get(&key);
                    let ant_count = info.map_or(0, |i| i.count);

                    site_stmt
                        .execute(params![
                            &key.0,
                            &key.1,
                            agg.adm_id,
                            lat_e6,
                            lon_e6,
                            nat_id,
                            tpo_id,
                            height_dm,
                            code_insee,
                            agg.service_mask,
                            agg.system_mask,
                            agg.emitter_count,
                            ant_count,
                            agg.freq_range_count,
                            agg.min_khz,
                            agg.max_khz,
                        ])
                        .map_err(|e| format!("radio site ins: {e}"))?;
                    total_sites += 1;

                    let detail = format_detail_text(
                        agg,
                        nat_id,
                        tpo_id,
                        height_dm,
                        address.as_deref(),
                        info,
                        refs,
                    );
                    if let Some(enc) = encode_frequency_details(&detail) {
                        detail_stmt
                            .execute(params![&key.0, &key.1, enc])
                            .map_err(|e| format!("radio detail ins: {e}"))?;
                    }
                    if let Some(id) = agg.adm_id {
                        used_adm.insert(id);
                    }
                    if let Some(id) = nat_id {
                        used_nat.insert(id);
                    }
                    if let Some(id) = tpo_id {
                        used_tpo.insert(id);
                    }
                }
            }
            emitted += 1;
            if emitted % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_RADIO, 96, None, emitted);
            }
        }
        drop(rows);
        drop(q);
        drop(site_stmt);
        drop(detail_stmt);
        tx.commit().map_err(|e| format!("radio emit commit: {e}"))?;
    }

    {
        let tx = conn.unchecked_transaction().map_err(|e| format!("radio ref tx: {e}"))?;
        {
            let mut s = tx.prepare("INSERT INTO ref_actor VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_adm.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = refs.actor.get(&id.to_string()).cloned().unwrap_or_else(|| format!("ADM_ID {id}"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_nature VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_nat.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = refs.nature.get(&id.to_string()).cloned().unwrap_or_else(|| format!("NAT_ID {id}"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_owner VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_tpo.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = refs.owner.get(&id.to_string()).cloned().unwrap_or_else(|| format!("TPO_ID {id}"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_type_antenne VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_tae.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = refs.type_antenne.get(&id.to_string()).cloned().unwrap_or_else(|| format!("TAE_ID {id}"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        tx.execute(
            "INSERT INTO metadata VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                version,
                RADIO_SCHEMA_VERSION,
                RADIO_COUNTRY_CODE,
                RADIO_COUNTRY_NAME,
                RADIO_SOURCE,
                date_maj_anfr,
                zip_version,
                total_sites
            ],
        )
        .map_err(|e| format!("radio metadata: {e}"))?;
        tx.commit().map_err(|e| format!("radio ref commit: {e}"))?;
    }

    sink.emit(PHASE_RADIO, 98, None, 0);
    for tbl in &[
        "stg_r_station",
        "stg_r_support",
        "stg_r_antenne",
        "stg_r_emetteur",
        "stg_r_bande",
        "stg_r_single",
        "stg_r_sel",
    ] {
        let _ = conn.execute(&format!("DROP TABLE IF EXISTS stg.{tbl}"), []);
    }
    for idx_sql in RADIO_CREATE_INDEXES {
        conn.execute(idx_sql, []).map_err(|e| format!("radio idx: {e}"))?;
    }
    conn.execute_batch("PRAGMA journal_mode = DELETE;")
        .map_err(|e| format!("radio journal_mode: {e}"))?;
    sink.emit(PHASE_RADIO, 100, None, 0);
    Ok(())
}

fn format_detail_text(
    agg: &Agg,
    nat_id: Option<i32>,
    tpo_id: Option<i32>,
    height_dm: Option<i32>,
    address: Option<&str>,
    antenna: Option<&AntennaInfo>,
    refs: &RadioRefs<'_>,
) -> String {
    let actor = match agg.adm_id {
        Some(id) => refs
            .actor
            .get(&id.to_string())
            .cloned()
            .unwrap_or_else(|| format!("ADM_ID {id}")),
        None => "ADM_ID null".to_string(),
    };
    let nature = match nat_id {
        Some(id) => refs
            .nature
            .get(&id.to_string())
            .cloned()
            .unwrap_or_else(|| format!("NAT_ID {id}")),
        None => "NAT_ID null".to_string(),
    };
    let owner = match tpo_id {
        Some(id) => refs
            .owner
            .get(&id.to_string())
            .cloned()
            .unwrap_or_else(|| format!("TPO_ID {id}")),
        None => "TPO_ID null".to_string(),
    };
    let mut sys_vec: Vec<(&String, &i32)> = agg.systems.iter().collect();
    sys_vec.sort_by(|a, b| b.1.cmp(a.1));
    let systems = sys_vec
        .into_iter()
        .map(|(k, v)| format!("{k} x{v}"))
        .collect::<Vec<_>>()
        .join(", ");

    let mut freqs = agg.freq_samples.join(", ");
    if agg.freq_range_count as usize > agg.freq_samples.len() {
        let extra = agg.freq_range_count as usize - agg.freq_samples.len();
        if !freqs.is_empty() {
            freqs = format!("{freqs}, +{extra}");
        }
    }
    let antenna_text = antenna.map(|a| a.samples.join("; ")).unwrap_or_default();
    let h_str = height_dm.map(|h| h.to_string()).unwrap_or_default();

    let candidates = [
        format!("Acteur: {actor}"),
        format!("Familles: {}", service_names(agg.service_mask)),
        format!("Systemes: {systems}"),
        format!("Support: {nature}; proprietaire {owner}; hauteur_dm={h_str}"),
        format!("Adresse: {}", address.unwrap_or("")),
        format!("Frequences: {freqs}"),
        "Programmes: ".to_string(),
        format!("Antennes: {antenna_text}"),
    ];
    candidates
        .into_iter()
        .filter(|l| !l.is_empty() && !l.ends_with(": "))
        .collect::<Vec<_>>()
        .join("\n")
}
