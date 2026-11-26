use crate::parsing::{
    antenna_dimension_text, clean_text, encode_frequency_details, find_zip_entry_name,
    float_or_none, format_band_range, frequency_to_mhz, int_or_none, is_active_status,
    normalize_id_anfr, parse_azimuth_list, parse_coordinates, read_reference_map,
    stream_csv_reader, update_masks_from_generation, update_masks_from_system_and_band,
    CsvTable, BAND_2G_1800, BAND_2G_900, BAND_3G_2100, BAND_3G_900, BAND_4G_1800, BAND_4G_2100,
    BAND_4G_2600, BAND_4G_700, BAND_4G_800, BAND_4G_900, BAND_5G_1400, BAND_5G_2100,
    BAND_5G_26000, BAND_5G_3500, BAND_5G_4200, BAND_5G_700, TECH_2G, TECH_3G, TECH_4G, TECH_5G,
    TECH_FH,
};
use crate::radio::{
    build_from_staging, ingest_radio_antenne, ingest_radio_bande, ingest_radio_emetteur,
    ingest_radio_station, ingest_radio_support, RadioRefs, RadioStagingWriter,
};
use crate::schema::{
    mobile_staging_statements, CREATE_FH_LINK_INDEX, INSERT_ROOM_IDENTITY, MOBILE_COUNTRY_CODE,
    MOBILE_COUNTRY_NAME, MOBILE_CREATE_TABLES, MOBILE_SCHEMA_VERSION, MOBILE_SOURCE,
};
use crate::{BResult, BuildRequest, ProgressSink};
use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use flate2::read::ZlibDecoder;
use rusqlite::{params, Connection};
use std::collections::{BTreeSet, HashMap, HashSet};
use std::f64::consts::PI;
use std::fs::File;
use std::io::Read;
use std::path::Path;
use zip::ZipArchive;

const PHASE_READING_STATIONS: i32 = 2;
const PHASE_READING_SUPPORTS: i32 = 3;
const PHASE_COMPUTING_FREQUENCIES: i32 = 4;
const PHASE_COMPUTING_ANTENNAS: i32 = 5;
const PHASE_BUILDING_DETAILS: i32 = 6;
const PHASE_INSERTING: i32 = 7;
const PHASE_COMPUTING_STATS: i32 = 8;
const PHASE_FINALIZING: i32 = 9;
const PHASE_RADIO_BUILDING: i32 = 12;

const EMIT_EVERY: i64 = 50_000;

struct IdRegistry {
    ids: HashMap<String, i32>,
    ordered: Vec<(i32, String)>,
}

impl IdRegistry {
    fn new() -> Self {
        let mut r = Self {
            ids: HashMap::new(),
            ordered: Vec::new(),
        };
        r.get_id(Some("Inconnu"));
        r
    }

    fn get_id(&mut self, raw: Option<&str>) -> i32 {
        let label = clean_text(raw);
        let key = if label.is_empty() { "Inconnu" } else { label };
        if let Some(&id) = self.ids.get(key) {
            return id;
        }
        let id = (self.ordered.len() as i32) + 1;
        let owned = key.to_string();
        self.ids.insert(owned.clone(), id);
        self.ordered.push((id, owned));
        id
    }

    fn get_label(&self, id: i32) -> &str {
        self.ordered
            .get((id - 1) as usize)
            .map(|(_, l)| l.as_str())
            .unwrap_or("Inconnu")
    }
}

struct StationRecord {
    id_anfr: String,
    operateur_id: i32,
    operator_label: String,
    lat: f64,
    lon: f64,
    statut_id: i32,
    statut_label: String,
    adm_id: Option<i32>,
    date_imp: Option<String>,
    date_ser: Option<String>,
    date_mod: Option<String>,
    address: Option<String>,
    insee: Option<String>,
    tech_mask: i32,
    band_mask: i32,
    has_active: i32,
    azimuts: Option<String>,
    azimuts_fh: Option<String>,
}

struct StationAccumulator {
    index_by_id: HashMap<String, usize>,
    rows: Vec<StationRecord>,
}

impl StationAccumulator {
    fn new() -> Self {
        Self {
            index_by_id: HashMap::with_capacity(65_536),
            rows: Vec::with_capacity(65_536),
        }
    }

    #[inline]
    fn index_of(&self, id_anfr: &str) -> Option<usize> {
        self.index_by_id.get(id_anfr).copied()
    }

    fn get_or_insert(
        &mut self,
        id_anfr: String,
        operateur_id: i32,
        operator_label: String,
        lat: f64,
        lon: f64,
        statut_id: i32,
        statut_label: String,
    ) -> usize {
        if let Some(&idx) = self.index_by_id.get(&id_anfr) {
            return idx;
        }
        let idx = self.rows.len();
        self.index_by_id.insert(id_anfr.clone(), idx);
        self.rows.push(StationRecord {
            id_anfr,
            operateur_id,
            operator_label,
            lat,
            lon,
            statut_id,
            statut_label,
            adm_id: None,
            date_imp: None,
            date_ser: None,
            date_mod: None,
            address: None,
            insee: None,
            tech_mask: 0,
            band_mask: 0,
            has_active: 0,
            azimuts: None,
            azimuts_fh: None,
        });
        idx
    }
}

fn cache_kb_for_ram(total_ram_bytes: i64) -> i64 {
    if total_ram_bytes >= 6 * 1024 * 1024 * 1024 {
        32_768
    } else if total_ram_bytes >= 3 * 1024 * 1024 * 1024 {
        16_384
    } else {
        8_192
    }
}

pub fn run_build(req: &BuildRequest, sink: &mut dyn ProgressSink) -> BResult<()> {
    let cache_kb = cache_kb_for_ram(req.total_ram_bytes);

    let (ref_nature, ref_proprietaire, ref_exploitant, ref_type_antenne) =
        if let Some(ref ref_path) = req.ref_zip_path {
            let p = Path::new(ref_path);
            (
                read_reference_map(p, "SUP_NATURE.txt", "nat_id", "nat_lb_nom"),
                read_reference_map(p, "SUP_PROPRIETAIRE.txt", "tpo_id", "tpo_lb"),
                read_reference_map(p, "SUP_EXPLOITANT.txt", "adm_id", "adm_lb_nom"),
                read_reference_map(p, "SUP_TYPE_ANTENNE.txt", "tae_id", "tae_lb"),
            )
        } else {
            (HashMap::new(), HashMap::new(), HashMap::new(), HashMap::new())
        };

    let radio_refs = RadioRefs {
        actor: &ref_exploitant,
        nature: &ref_nature,
        owner: &ref_proprietaire,
        type_antenne: &ref_type_antenne,
    };

    if !req.pack_mobile {
        if !req.pack_radio {
            return Err(crate::BuildErr("Aucune donnée sélectionnée".to_string()));
        }
        return run_radio_standalone(req, &radio_refs, cache_kb, sink);
    }

    let arcep_map = parse_arcep_files(&req.arcep_csv_paths);

    let _ = std::fs::remove_file(&req.built_mobile_path);
    let _ = std::fs::remove_file(&req.staging_mobile_path);
    if let Some(parent) = Path::new(&req.built_mobile_path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }

    let mut radio_writer = if req.pack_radio && req.ref_zip_path.is_some() {
        let _ = std::fs::remove_file(&req.built_radio_path);
        let _ = std::fs::remove_file(&req.staging_radio_path);
        if let Some(parent) = Path::new(&req.built_radio_path).parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        Some(RadioStagingWriter::open(
            &req.built_radio_path,
            &req.staging_radio_path,
            cache_kb / 2,
        )?)
    } else {
        None
    };

    let conn = Connection::open(&req.built_mobile_path)
        .map_err(|e| format!("open mobile db: {e}"))?;
    conn.execute_batch(&format!(
        "PRAGMA journal_mode = OFF;
         PRAGMA synchronous = OFF;
         PRAGMA temp_store = FILE;
         PRAGMA cache_size = -{cache_kb};
         ATTACH DATABASE '{}' AS stg;",
        req.staging_mobile_path
    ))
    .map_err(|e| format!("mobile pragmas/attach: {e}"))?;

    for sql in MOBILE_CREATE_TABLES {
        conn.execute(sql, []).map_err(|e| format!("mobile ddl: {e}"))?;
    }
    for sql in mobile_staging_statements("stg.") {
        conn.execute(&sql, []).map_err(|e| format!("mobile stg ddl: {e}"))?;
    }

    let mut operateur_ids = IdRegistry::new();
    let mut systeme_ids = IdRegistry::new();
    let mut statut_ids = IdRegistry::new();
    let mut stations = StationAccumulator::new();
    let mut used_nat: HashSet<i32> = HashSet::new();
    let mut used_tpo: HashSet<i32> = HashSet::new();
    let mut used_adm: HashSet<i32> = HashSet::new();
    let mut used_tae: HashSet<i32> = HashSet::new();
    let mut used_communes: HashSet<String> = HashSet::new();
    let mut date_maj_anfr = "Inconnue".to_string();

    // 1/ Observatoire CSV
    sink.emit(PHASE_READING_STATIONS, 45, Some("observatoire.csv"), 0);
    if let Some(ref obs_path) = req.observatoire_csv_path {
        let obs_file = File::open(obs_path).map_err(|e| format!("open observatoire.csv: {e}"))?;
        let tx = conn.unchecked_transaction().map_err(|e| format!("obs tx: {e}"))?;
        {
            let mut sys_stmt = tx
                .prepare_cached("INSERT OR REPLACE INTO stg_sysstatus VALUES (?1, ?2, ?3, ?4, ?5)")
                .map_err(|e| format!("{e}"))?;
            let mut az_stmt = tx
                .prepare_cached("INSERT OR IGNORE INTO stg_sysazimut VALUES (?1, ?2, ?3)")
                .map_err(|e| format!("{e}"))?;
            let mut sup_stmt = tx
                .prepare_cached("INSERT OR IGNORE INTO stg_support VALUES (?1, ?2, ?3, ?4, ?5)")
                .map_err(|e| format!("{e}"))?;
            let mut weekly_rows = 0i64;
            stream_csv_reader(obs_file, |table, rec| {
                weekly_rows += 1;
                if date_maj_anfr == "Inconnue" {
                    let d = clean_text(table.idx("date_maj").and_then(|i| rec.get(i)));
                    if !d.is_empty() {
                        date_maj_anfr = d.to_string();
                    }
                }
                let id_anfr = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
                if id_anfr.is_empty() {
                    return Ok(());
                }
                let statut_raw = table.idx("statut").and_then(|i| rec.get(i));
                let st_idx = if let Some(idx) = stations.index_of(&id_anfr) {
                    idx
                } else {
                    let (lat, lon) = parse_coordinates(table.idx("coordonnees").and_then(|i| rec.get(i)));
                    let op_id = operateur_ids.get_id(table.idx("adm_lb_nom").and_then(|i| rec.get(i)));
                    let op_lbl = operateur_ids.get_label(op_id).to_string();
                    let st_id = statut_ids.get_id(statut_raw);
                    let st_lbl = statut_ids.get_label(st_id).to_string();
                    stations.get_or_insert(id_anfr.clone(), op_id, op_lbl, lat, lon, st_id, st_lbl)
                };
                if is_active_status(statut_raw) {
                    stations.rows[st_idx].has_active = 1;
                }
                update_masks_from_generation(
                    &mut stations.rows[st_idx].tech_mask,
                    table.idx("generation").and_then(|i| rec.get(i)),
                );

                let sup_id = clean_text(table.idx("sup_id").and_then(|i| rec.get(i)));
                if !sup_id.is_empty() {
                    let nat_id = int_or_none(table.idx("nat_id").and_then(|i| rec.get(i)));
                    let tpo_id = int_or_none(table.idx("tpo_id").and_then(|i| rec.get(i)));
                    let haut = float_or_none(table.idx("sup_nm_haut").and_then(|i| rec.get(i)));
                    if let Some(n) = nat_id {
                        used_nat.insert(n);
                    }
                    if let Some(t) = tpo_id {
                        used_tpo.insert(t);
                    }
                    let _ = sup_stmt.execute(params![&id_anfr, sup_id, nat_id, tpo_id, haut]);
                }

                let insee = {
                    let a = clean_text(table.idx("com_cd_insee").and_then(|i| rec.get(i)));
                    if !a.is_empty() {
                        Some(a.to_string())
                    } else {
                        let b = clean_text(table.idx("code_insee").and_then(|i| rec.get(i)));
                        if !b.is_empty() { Some(b.to_string()) } else { None }
                    }
                };
                if let Some(ref code) = insee {
                    used_communes.insert(code.clone());
                    stations.rows[st_idx].insee = Some(code.clone());
                }
                let addr = build_support_address(table, rec, insee.as_deref(), &req.communes);
                if !addr.is_empty() {
                    stations.rows[st_idx].address = Some(addr);
                }
                let sys_name = clean_text(table.idx("emr_lb_systeme").and_then(|i| rec.get(i)));
                if !sys_name.is_empty() {
                    let sys_upper = sys_name.to_ascii_uppercase();
                    let st_id = statut_ids.get_id(statut_raw);
                    let st_lbl = statut_ids.get_label(st_id);
                    let emr_dt_raw = clean_text(table.idx("emr_dt").and_then(|i| rec.get(i)));
                    let emr_dt = if emr_dt_raw.is_empty() { None } else { Some(emr_dt_raw) };
                    let _ = sys_stmt.execute(params![&id_anfr, &sys_upper, sys_name, st_lbl, emr_dt]);
                    for az in parse_azimuth_list(table.idx("list_azimut").and_then(|i| rec.get(i))) {
                        let _ = az_stmt.execute(params![&id_anfr, &sys_upper, az]);
                    }
                }
                if weekly_rows % EMIT_EVERY == 0 {
                    sink.check_paused_or_cancelled()?;
                    sink.emit(PHASE_READING_STATIONS, 48, Some("observatoire.csv"), weekly_rows);
                }
                Ok(())
            })?;
            sink.emit(PHASE_READING_STATIONS, 50, Some("observatoire.csv"), weekly_rows);
        }
        tx.commit().map_err(|e| format!("obs commit: {e}"))?;
        stage_frequency_azimuths(&conn)?;
        let _ = std::fs::remove_file(obs_path);
    }

    let data_zip_file = File::open(&req.data_zip_path).map_err(|e| format!("open data zip: {e}"))?;
    let mut data_zip = ZipArchive::new(data_zip_file).map_err(|e| format!("zip open: {e}"))?;

    // 2/ SUP_STATION.txt
    sink.emit(PHASE_READING_SUPPORTS, 52, Some("SUP_STATION.txt (ZIP ANFR)"), 0);
    let mut fh_station_adm: HashMap<String, i32> = HashMap::new();
    let mut fh_aer_ids: HashSet<String> = HashSet::new();
    let mut fh_azimuths_all: HashMap<String, HashSet<i32>> = HashMap::new();
    let mut fh_coords_all: HashMap<String, (f64, f64)> = HashMap::new();

    if let Some(entry_name) = find_zip_entry_name(&mut data_zip, "SUP_STATION.txt") {
        let entry = data_zip.by_name(&entry_name).map_err(|e| format!("{e}"))?;
        let r_tx = radio_writer
            .as_ref()
            .map(|w| w.conn.unchecked_transaction())
            .transpose()
            .map_err(|e| format!("r_sta tx: {e}"))?;
        let mut r_stmt = r_tx
            .as_ref()
            .map(|tx| tx.prepare_cached("INSERT OR REPLACE INTO stg_r_station VALUES (?1, ?2)"))
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut cnt = 0i64;
        stream_csv_reader(entry, |table, rec| {
            cnt += 1;
            if let Some(ref mut s) = r_stmt {
                ingest_radio_station(s, table, rec);
            }
            let id_anfr = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
            let adm_id = int_or_none(table.idx("adm_id").and_then(|i| rec.get(i)));
            if !id_anfr.is_empty() {
                if let Some(a) = adm_id {
                    fh_station_adm.insert(id_anfr.clone(), a);
                }
            }
            if let Some(idx) = stations.index_of(&id_anfr) {
                stations.rows[idx].adm_id = adm_id;
                let d_imp = clean_text(table.idx("dte_implantation").and_then(|i| rec.get(i)));
                let d_ser = clean_text(table.idx("dte_en_service").and_then(|i| rec.get(i)));
                let d_mod = clean_text(table.idx("dte_modif").and_then(|i| rec.get(i)));
                stations.rows[idx].date_imp = if d_imp.is_empty() { None } else { Some(d_imp.to_string()) };
                stations.rows[idx].date_ser = if d_ser.is_empty() { None } else { Some(d_ser.to_string()) };
                stations.rows[idx].date_mod = if d_mod.is_empty() { None } else { Some(d_mod.to_string()) };
                if let Some(a) = adm_id {
                    used_adm.insert(a);
                }
            }
            if cnt % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_READING_SUPPORTS, 53, Some("SUP_STATION.txt (ZIP ANFR)"), cnt);
            }
            Ok(())
        })?;
        drop(r_stmt);
        if let Some(tx) = r_tx {
            tx.commit().map_err(|e| format!("{e}"))?;
        }
        sink.emit(PHASE_READING_SUPPORTS, 54, Some("SUP_STATION.txt (ZIP ANFR)"), cnt);
    }

    // 3/ SUP_BANDE.txt
    sink.emit(PHASE_READING_SUPPORTS, 55, Some("SUP_BANDE.txt (ZIP ANFR)"), 0);
    if let Some(entry_name) = find_zip_entry_name(&mut data_zip, "SUP_BANDE.txt") {
        let entry = data_zip.by_name(&entry_name).map_err(|e| format!("{e}"))?;
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let r_tx = radio_writer
            .as_ref()
            .map(|w| w.conn.unchecked_transaction())
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut r_stmt = r_tx
            .as_ref()
            .map(|tx| tx.prepare_cached("INSERT INTO stg_r_bande VALUES (?1, ?2, ?3, ?4)"))
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut b_stmt = tx
            .prepare_cached("INSERT INTO stg_bande VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
            .map_err(|e| format!("{e}"))?;
        let mut cnt = 0i64;
        stream_csv_reader(entry, |table, rec| {
            cnt += 1;
            if let Some(ref mut s) = r_stmt {
                ingest_radio_bande(s, table, rec);
            }
            let emr_id = clean_text(table.idx("emr_id").and_then(|i| rec.get(i)));
            let f_deb_raw = clean_text(table.idx("ban_nb_f_deb").and_then(|i| rec.get(i)));
            let f_fin_raw = clean_text(table.idx("ban_nb_f_fin").and_then(|i| rec.get(i)));
            let f_deb = float_or_none(Some(f_deb_raw));
            let f_fin = float_or_none(Some(f_fin_raw));
            let u_raw = clean_text(table.idx("ban_fg_unite").and_then(|i| rec.get(i)));
            let unite = if u_raw.is_empty() { "M" } else { u_raw };
            if !emr_id.is_empty() {
                if let (Some(fd), Some(ff)) = (f_deb, f_fin) {
                    let _ = b_stmt.execute(params![emr_id, fd, ff, unite, f_deb_raw, f_fin_raw]);
                }
            }
            if cnt % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_READING_SUPPORTS, 57, Some("SUP_BANDE.txt (ZIP ANFR)"), cnt);
            }
            Ok(())
        })?;
        drop(b_stmt);
        drop(r_stmt);
        tx.commit().map_err(|e| format!("{e}"))?;
        if let Some(tx) = r_tx {
            tx.commit().map_err(|e| format!("{e}"))?;
        }
        sink.emit(PHASE_READING_SUPPORTS, 58, Some("SUP_BANDE.txt (ZIP ANFR)"), cnt);
    }
    conn.execute("CREATE INDEX stg.ix_stg_bande_emr ON stg_bande(emr_id)", [])
        .map_err(|e| format!("{e}"))?;
    build_emr_freqs(&conn)?;

    // 4/ SUP_EMETTEUR.txt
    sink.emit(PHASE_READING_SUPPORTS, 59, Some("SUP_EMETTEUR.txt (ZIP ANFR)"), 0);
    if let Some(entry_name) = find_zip_entry_name(&mut data_zip, "SUP_EMETTEUR.txt") {
        let entry = data_zip.by_name(&entry_name).map_err(|e| format!("{e}"))?;
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let r_tx = radio_writer
            .as_ref()
            .map(|w| w.conn.unchecked_transaction())
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut r_stmt = r_tx
            .as_ref()
            .map(|tx| tx.prepare_cached("INSERT INTO stg_r_emetteur VALUES (?1, ?2, ?3, ?4)"))
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut e_stmt = tx
            .prepare_cached("INSERT INTO stg_emetteur VALUES (?1, ?2, ?3, ?4)")
            .map_err(|e| format!("{e}"))?;
        let mut cnt = 0i64;
        stream_csv_reader(entry, |table, rec| {
            cnt += 1;
            if let Some(ref mut s) = r_stmt {
                ingest_radio_emetteur(s, table, rec);
            }
            let id_anfr = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
            let aer_raw = clean_text(table.idx("aer_id").and_then(|i| rec.get(i)));
            let aer_id = if aer_raw.is_empty() { None } else { Some(aer_raw) };
            let sys_raw = clean_text(table.idx("emr_lb_systeme").and_then(|i| rec.get(i)));
            let systeme = if sys_raw.is_empty() { "Inconnu" } else { sys_raw };
            if let Some(aer) = aer_id {
                if systeme.to_ascii_uppercase().contains("FH") {
                    fh_aer_ids.insert(aer.to_string());
                }
            }
            if stations.index_of(&id_anfr).is_some() {
                let emr_id = clean_text(table.idx("emr_id").and_then(|i| rec.get(i)));
                if !emr_id.is_empty() {
                    systeme_ids.get_id(Some(systeme));
                    let _ = e_stmt.execute(params![&id_anfr, emr_id, aer_id, systeme]);
                }
            }
            if cnt % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_READING_SUPPORTS, 61, Some("SUP_EMETTEUR.txt (ZIP ANFR)"), cnt);
            }
            Ok(())
        })?;
        drop(e_stmt);
        drop(r_stmt);
        tx.commit().map_err(|e| format!("{e}"))?;
        if let Some(tx) = r_tx {
            tx.commit().map_err(|e| format!("{e}"))?;
        }
        sink.emit(PHASE_READING_SUPPORTS, 62, Some("SUP_EMETTEUR.txt (ZIP ANFR)"), cnt);
    }

    conn.execute_batch(
        "CREATE INDEX stg.ix_stg_emetteur_emr ON stg_emetteur(emr_id);
         CREATE INDEX stg.ix_stg_emetteur_aer ON stg_emetteur(aer_id);
         CREATE INDEX stg.ix_stg_emetteur_id ON stg_emetteur(id_anfr, systeme, emr_id, aer_id);
         INSERT OR REPLACE INTO stg_fh_aer SELECT DISTINCT aer_id FROM stg_emetteur WHERE aer_id IS NOT NULL AND aer_id != '' AND UPPER(systeme) LIKE '%FH%';",
    )
    .map_err(|e| format!("emetteur idx: {e}"))?;

    // 5/ Masques technologie / bande
    sink.emit(PHASE_COMPUTING_FREQUENCIES, 64, Some("stg_emetteur x stg_bande"), 0);
    {
        let mut q = conn
            .prepare("SELECT e.id_anfr, e.systeme, b.f_deb, b.f_fin, b.unite FROM stg_emetteur e LEFT JOIN stg_bande b ON e.emr_id = b.emr_id")
            .map_err(|e| format!("{e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
        let mut mask_rows = 0i64;
        while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
            let id_anfr: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            if let Some(st_idx) = stations.index_of(&id_anfr) {
                let sys: Option<String> = row.get(1)?;
                let f_deb: Option<f64> = row.get(2)?;
                if f_deb.is_none() {
                    if sys.as_deref().unwrap_or("").to_ascii_uppercase().contains("FH") {
                        stations.rows[st_idx].tech_mask |= TECH_FH;
                        stations.rows[st_idx].band_mask |= crate::parsing::BAND_FH;
                    }
                } else {
                    let f_fin: Option<f64> = row.get(3)?;
                    let unite: Option<String> = row.get(4)?;
                    let u = unite.as_deref().or(Some("M"));
                    let st = &mut stations.rows[st_idx];
                    update_masks_from_system_and_band(
                        &mut st.tech_mask,
                        &mut st.band_mask,
                        sys.as_deref(),
                        frequency_to_mhz(f_deb, u),
                        frequency_to_mhz(f_fin, u),
                    );
                }
            }
            mask_rows += 1;
            if mask_rows % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_COMPUTING_FREQUENCIES, 67, Some("stg_emetteur x stg_bande"), mask_rows);
            }
        }
        sink.emit(PHASE_COMPUTING_FREQUENCIES, 68, Some("stg_emetteur x stg_bande"), mask_rows);
    }
    let _ = conn.execute("DROP TABLE IF EXISTS stg.stg_bande", []);

    // 6/ SUP_ANTENNE.txt
    sink.emit(PHASE_READING_SUPPORTS, 69, Some("SUP_ANTENNE.txt (ZIP ANFR)"), 0);
    if let Some(entry_name) = find_zip_entry_name(&mut data_zip, "SUP_ANTENNE.txt") {
        let entry = data_zip.by_name(&entry_name).map_err(|e| format!("{e}"))?;
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let r_tx = radio_writer
            .as_ref()
            .map(|w| w.conn.unchecked_transaction())
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut r_stmt = r_tx
            .as_ref()
            .map(|tx| tx.prepare_cached("INSERT OR REPLACE INTO stg_r_antenne VALUES (?1, ?2, ?3, ?4, ?5)"))
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut ant_stmt = tx
            .prepare_cached("INSERT OR REPLACE INTO stg_antenne VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
            .map_err(|e| format!("{e}"))?;
        let mut az_sta_stmt = tx
            .prepare_cached("INSERT INTO stg_antenne_sta VALUES (?1, ?2, ?3, ?4)")
            .map_err(|e| format!("{e}"))?;
        let mut cnt = 0i64;
        stream_csv_reader(entry, |table, rec| {
            cnt += 1;
            if let Some(ref mut s) = r_stmt {
                ingest_radio_antenne(s, table, rec, &ref_type_antenne);
            }
            let id_anfr = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
            let aer_id = clean_text(table.idx("aer_id").and_then(|i| rec.get(i)));
            let azimut = int_or_none(table.idx("aer_nb_azimut").and_then(|i| rec.get(i)));
            if !id_anfr.is_empty() && !aer_id.is_empty() {
                if let Some(az) = azimut {
                    if fh_aer_ids.contains(aer_id) {
                        fh_azimuths_all
                            .entry(id_anfr.clone())
                            .or_default()
                            .insert(az);
                    }
                }
            }
            if stations.index_of(&id_anfr).is_some() && !aer_id.is_empty() {
                let sup_raw = clean_text(table.idx("sup_id").and_then(|i| rec.get(i)));
                let sup_id = if sup_raw.is_empty() { None } else { Some(sup_raw) };
                let tae_id = int_or_none(table.idx("tae_id").and_then(|i| rec.get(i)));
                let haut_bas = float_or_none(table.idx("aer_nb_alt_bas").and_then(|i| rec.get(i)));
                let type_txt = match tae_id {
                    Some(id) => ref_type_antenne
                        .get(&id.to_string())
                        .cloned()
                        .unwrap_or_else(|| format!("Type inconnu ({id})")),
                    None => "Type inconnu".to_string(),
                };
                let az_txt = {
                    let s = clean_text(table.idx("aer_nb_azimut").and_then(|i| rec.get(i)));
                    if s.is_empty() { "N/A" } else { s }
                };
                let h_txt = {
                    let s = clean_text(table.idx("aer_nb_alt_bas").and_then(|i| rec.get(i)));
                    if s.is_empty() { "N/A" } else { s }
                };
                let dim = antenna_dimension_text(table.idx("aer_nb_dimension").and_then(|i| rec.get(i)));
                let physique = match dim {
                    Some(d) => format!("{type_txt} : {az_txt}° ({h_txt}m) [AER_ID: {aer_id}] [DIM: {d}]"),
                    None => format!("{type_txt} : {az_txt}° ({h_txt}m) [AER_ID: {aer_id}]"),
                };
                if let Some(t) = tae_id {
                    used_tae.insert(t);
                }
                let _ = ant_stmt.execute(params![aer_id, &id_anfr, sup_id, tae_id, azimut, haut_bas, 0, physique]);
                if let Some(az) = azimut {
                    let _ = az_sta_stmt.execute(params![&id_anfr, aer_id, az, 0]);
                }
            }
            if cnt % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_READING_SUPPORTS, 71, Some("SUP_ANTENNE.txt (ZIP ANFR)"), cnt);
            }
            Ok(())
        })?;
        drop(ant_stmt);
        drop(az_sta_stmt);
        drop(r_stmt);
        tx.commit().map_err(|e| format!("{e}"))?;
        if let Some(tx) = r_tx {
            tx.commit().map_err(|e| format!("{e}"))?;
        }
        sink.emit(PHASE_READING_SUPPORTS, 72, Some("SUP_ANTENNE.txt (ZIP ANFR)"), cnt);
    }

    conn.execute_batch(
        "UPDATE stg_antenne SET is_fh = 1 WHERE aer_id IN (SELECT aer_id FROM stg_fh_aer);
         UPDATE stg_antenne_sta SET is_fh = 1 WHERE aer_id IN (SELECT aer_id FROM stg_fh_aer);
         CREATE INDEX stg.ix_stg_antenne_sta ON stg_antenne_sta(id_anfr, azimut, is_fh);",
    )
    .map_err(|e| format!("antenne fh idx: {e}"))?;

    // 7/ Azimuts
    sink.emit(PHASE_COMPUTING_ANTENNAS, 74, Some("stg_antenne_sta"), 0);
    apply_azimuts(&conn, &mut stations)?;
    let _ = conn.execute("DROP TABLE IF EXISTS stg.stg_antenne_sta", []);

    // 8/ SUP_SUPPORT.txt
    sink.emit(PHASE_READING_SUPPORTS, 76, Some("SUP_SUPPORT.txt (ZIP ANFR)"), 0);
    if let Some(entry_name) = find_zip_entry_name(&mut data_zip, "SUP_SUPPORT.txt") {
        let entry = data_zip.by_name(&entry_name).map_err(|e| format!("{e}"))?;
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let r_tx = radio_writer
            .as_ref()
            .map(|w| w.conn.unchecked_transaction())
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut r_stmt = r_tx
            .as_ref()
            .map(|tx| tx.prepare_cached("INSERT OR REPLACE INTO stg_r_support VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)"))
            .transpose()
            .map_err(|e| format!("{e}"))?;
        let mut sup_stmt = tx
            .prepare_cached("INSERT OR REPLACE INTO stg_support VALUES (?1, ?2, ?3, ?4, ?5)")
            .map_err(|e| format!("{e}"))?;
        let mut cnt = 0i64;
        stream_csv_reader(entry, |table, rec| {
            cnt += 1;
            if let Some(ref mut s) = r_stmt {
                ingest_radio_support(s, table, rec);
            }
            let norm_id = normalize_id_anfr(table.idx("sta_nm_anfr").and_then(|i| rec.get(i)));
            let st_idx = stations.index_of(&norm_id);
            if fh_azimuths_all.contains_key(&norm_id) && !fh_coords_all.contains_key(&norm_id) {
                if let Some(idx) = st_idx {
                    fh_coords_all.insert(norm_id.clone(), (stations.rows[idx].lat, stations.rows[idx].lon));
                } else {
                    let lat_e6 = crate::parsing::dms_to_e6(
                        table.idx("cor_nb_dg_lat").and_then(|i| rec.get(i)),
                        table.idx("cor_nb_mn_lat").and_then(|i| rec.get(i)),
                        table.idx("cor_nb_sc_lat").and_then(|i| rec.get(i)),
                        table.idx("cor_cd_ns_lat").and_then(|i| rec.get(i)),
                    );
                    let lon_e6 = crate::parsing::dms_to_e6(
                        table.idx("cor_nb_dg_lon").and_then(|i| rec.get(i)),
                        table.idx("cor_nb_mn_lon").and_then(|i| rec.get(i)),
                        table.idx("cor_nb_sc_lon").and_then(|i| rec.get(i)),
                        table.idx("cor_cd_ew_lon").and_then(|i| rec.get(i)),
                    );
                    if let (Some(la), Some(lo)) = (lat_e6, lon_e6) {
                        fh_coords_all.insert(norm_id.clone(), (la as f64 / 1_000_000.0, lo as f64 / 1_000_000.0));
                    }
                }
            }
            if let Some(idx) = st_idx {
                let sup_id = clean_text(table.idx("sup_id").and_then(|i| rec.get(i)));
                if !sup_id.is_empty() {
                    let nat_id = int_or_none(table.idx("nat_id").and_then(|i| rec.get(i)));
                    let tpo_id = int_or_none(table.idx("tpo_id").and_then(|i| rec.get(i)));
                    let haut = float_or_none(table.idx("sup_nm_haut").and_then(|i| rec.get(i)));
                    let insee_raw = clean_text(table.idx("com_cd_insee").and_then(|i| rec.get(i)));
                    let insee = if insee_raw.is_empty() { None } else { Some(insee_raw) };
                    if let Some(n) = nat_id {
                        used_nat.insert(n);
                    }
                    if let Some(t) = tpo_id {
                        used_tpo.insert(t);
                    }
                    if let Some(c) = insee {
                        used_communes.insert(c.to_string());
                        stations.rows[idx].insee = Some(c.to_string());
                    }
                    let addr = build_support_address(table, rec, insee, &req.communes);
                    if !addr.is_empty() {
                        stations.rows[idx].address = Some(addr);
                    }
                    let _ = sup_stmt.execute(params![&stations.rows[idx].id_anfr, sup_id, nat_id, tpo_id, haut]);
                }
            }
            if cnt % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_READING_SUPPORTS, 78, Some("SUP_SUPPORT.txt (ZIP ANFR)"), cnt);
            }
            Ok(())
        })?;
        drop(sup_stmt);
        drop(r_stmt);
        tx.commit().map_err(|e| format!("{e}"))?;
        if let Some(tx) = r_tx {
            tx.commit().map_err(|e| format!("{e}"))?;
        }
        sink.emit(PHASE_READING_SUPPORTS, 79, Some("SUP_SUPPORT.txt (ZIP ANFR)"), cnt);
    }
    if let Some(ref w) = radio_writer {
        w.finish_staging()?;
    }

    // 9/ Flush stations + FH links + ARCEP
    {
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        {
            let mut s = tx
                .prepare_cached("INSERT OR REPLACE INTO stg_station_final VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18)")
                .map_err(|e| format!("{e}"))?;
            for st in &stations.rows {
                let _ = s.execute(params![
                    &st.id_anfr,
                    st.operateur_id,
                    &st.operator_label,
                    st.lat,
                    st.lon,
                    st.statut_id,
                    &st.statut_label,
                    st.adm_id,
                    &st.date_imp,
                    &st.date_ser,
                    &st.date_mod,
                    &st.address,
                    &st.insee,
                    st.tech_mask,
                    st.band_mask,
                    st.has_active,
                    &st.azimuts,
                    &st.azimuts_fh,
                ]);
            }
        }
        {
            let mut s = tx
                .prepare_cached("INSERT OR REPLACE INTO stg_arcep VALUES (?1, ?2, ?3, ?4)")
                .map_err(|e| format!("{e}"))?;
            for ((id_anfr, op_upper), (nidt, is_zb)) in &arcep_map {
                let _ = s.execute(params![id_anfr, op_upper, nidt, is_zb]);
            }
        }
        tx.commit().map_err(|e| format!("{e}"))?;
    }

    populate_fh_links(
        &conn,
        &stations,
        &ref_exploitant,
        &fh_azimuths_all,
        &fh_station_adm,
        &fh_coords_all,
    )?;
    fh_aer_ids.clear();
    fh_azimuths_all.clear();
    fh_station_adm.clear();
    fh_coords_all.clear();
    drop(stations);

    // 10/ details_frequences
    sink.emit(PHASE_BUILDING_DETAILS, 82, Some("stg_emetteur -> details_frequences"), 0);
    apply_announced_details(&conn)?;
    apply_details(&conn, sink)?;

    for tbl in &[
        "stg_emetteur",
        "stg_emr_freqs",
        "stg_sysstatus",
        "stg_sysazimut",
        "stg_fh_aer",
        "stg_details_extra",
    ] {
        let _ = conn.execute(&format!("DROP TABLE IF EXISTS stg.{tbl}"), []);
    }

    // 11/ Final tables
    sink.emit(PHASE_INSERTING, 88, Some("stg_station_final -> tables finales"), 0);
    conn.execute_batch(
        "INSERT INTO localisation SELECT sf.id_anfr, sf.operateur_id, sf.latitude, sf.longitude, sf.azimuts, sf.code_insee, sf.azimuts_fh, sf.tech_mask, sf.band_mask, ar.nidt, COALESCE(ar.is_zb, 0) FROM stg_station_final sf LEFT JOIN stg_arcep ar ON sf.id_anfr = ar.id_anfr AND ar.operator_upper = UPPER(TRIM(sf.operator_label));
         INSERT INTO technique SELECT sf.id_anfr, sf.adm_id, sf.statut_id, sf.date_imp, sf.date_ser, sf.date_mod, d.details, sf.adresse, sf.has_active, za.details FROM stg_station_final sf LEFT JOIN stg_details d ON sf.id_anfr = d.id_anfr LEFT JOIN stg_azimut_json za ON sf.id_anfr = za.id_anfr;
         DROP TABLE IF EXISTS stg.stg_azimut_json;
         INSERT INTO support SELECT id_anfr, sup_id, nat_id, tpo_id, hauteur FROM stg_support;
         INSERT INTO antenne SELECT aer_id, id_anfr, sup_id, tae_id, azimut, hauteur_bas, is_fh FROM stg_antenne;",
    )
    .map_err(|e| format!("insert final tables: {e}"))?;

    // 12/ Referentiels + metadata
    {
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        {
            let mut s = tx.prepare("INSERT INTO ref_operateur VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            for (id, lbl) in &operateur_ids.ordered {
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_nature VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_nat.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = ref_nature.get(&id.to_string()).cloned().unwrap_or_else(|| format!("Code Nature {id}"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_proprietaire VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_tpo.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = ref_proprietaire.get(&id.to_string()).cloned().unwrap_or_else(|| "Inconnu".to_string());
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_exploitant VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_adm.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = ref_exploitant.get(&id.to_string()).cloned().unwrap_or_else(|| format!("Code Exploitant {id}"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_type_antenne VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut ids: Vec<i32> = used_tae.into_iter().collect();
            ids.sort_unstable();
            for id in ids {
                let lbl = ref_type_antenne.get(&id.to_string()).cloned().unwrap_or_else(|| format!("Type inconnu ({id})"));
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_systeme VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            for (id, lbl) in &systeme_ids.ordered {
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_statut VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            for (id, lbl) in &statut_ids.ordered {
                s.execute(params![id, lbl]).map_err(|e| format!("{e}"))?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_commune VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
            let mut codes: Vec<String> = used_communes
                .into_iter()
                .filter(|c| req.communes.contains_key(c))
                .collect();
            codes.sort();
            for code in codes {
                if let Some(nom) = req.communes.get(&code) {
                    s.execute(params![code, nom]).map_err(|e| format!("{e}"))?;
                }
            }
        }
        tx.execute(
            "INSERT INTO metadata VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                &req.version,
                MOBILE_SCHEMA_VERSION,
                MOBILE_COUNTRY_CODE,
                MOBILE_COUNTRY_NAME,
                MOBILE_SOURCE,
                &date_maj_anfr,
                &req.zip_version
            ],
        )
        .map_err(|e| format!("mobile metadata: {e}"))?;
        if let Some(ref qv) = req.quarterly_version {
            tx.execute(
                "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES ('quarterly_version', ?1)",
                params![qv],
            )
            .map_err(|e| format!("{e}"))?;
        }
        tx.execute(
            "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES ('provenance', 'local_build_rust')",
            [],
        )
        .map_err(|e| format!("{e}"))?;
        tx.commit().map_err(|e| format!("{e}"))?;
    }

    // 13/ radio_stat_current
    sink.emit(PHASE_COMPUTING_STATS, 90, Some("tables finales -> statistiques"), 0);
    populate_current_stats(&conn)?;

    // 14/ Finalize mobile DB
    sink.emit(PHASE_FINALIZING, 93, Some("staging SQLite -> base finale"), 0);
    for tbl in &[
        "stg_bande",
        "stg_emr_freqs",
        "stg_emetteur",
        "stg_fh_aer",
        "stg_antenne",
        "stg_antenne_sta",
        "stg_support",
        "stg_sysstatus",
        "stg_station_final",
        "stg_arcep",
        "stg_details",
        "stg_details_extra",
    ] {
        let _ = conn.execute(&format!("DROP TABLE IF EXISTS stg.{tbl}"), []);
    }
    conn.execute(INSERT_ROOM_IDENTITY, [])
        .map_err(|e| format!("room identity: {e}"))?;
    conn.execute_batch(&format!(
        "PRAGMA user_version = {MOBILE_SCHEMA_VERSION};
         PRAGMA journal_mode = DELETE;"
    ))
    .map_err(|e| format!("user_version: {e}"))?;
    drop(conn);
    let _ = std::fs::remove_file(&req.staging_mobile_path);

    // 15/ Radio DB if requested
    if let Some(ref rw) = radio_writer {
        build_from_staging(
            rw,
            &radio_refs,
            &req.version,
            &req.zip_version,
            &date_maj_anfr,
            req.allowed_service_mask,
            sink,
        )?;
        radio_writer.take();
        let _ = std::fs::remove_file(&req.staging_radio_path);
    }

    Ok(())
}

fn run_radio_standalone(
    req: &BuildRequest,
    refs: &RadioRefs<'_>,
    cache_kb: i64,
    sink: &mut dyn ProgressSink,
) -> BResult<()> {
    let _ = std::fs::remove_file(&req.built_radio_path);
    let _ = std::fs::remove_file(&req.staging_radio_path);
    if let Some(parent) = Path::new(&req.built_radio_path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let writer = RadioStagingWriter::open(&req.built_radio_path, &req.staging_radio_path, cache_kb)?;
    let data_zip_file = File::open(&req.data_zip_path).map_err(|e| format!("open data zip: {e}"))?;
    let mut data_zip = ZipArchive::new(data_zip_file).map_err(|e| format!("zip open: {e}"))?;

    sink.emit(PHASE_RADIO_BUILDING, 45, Some("SUP_STATION.txt (ZIP ANFR)"), 0);
    if let Some(name) = find_zip_entry_name(&mut data_zip, "SUP_STATION.txt") {
        let entry = data_zip.by_name(&name).map_err(|e| format!("{e}"))?;
        let tx = writer.conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let mut s = tx.prepare_cached("INSERT OR REPLACE INTO stg_r_station VALUES (?1, ?2)").map_err(|e| format!("{e}"))?;
        stream_csv_reader(entry, |t, r| { ingest_radio_station(&mut s, t, r); Ok(()) })?;
        drop(s);
        tx.commit().map_err(|e| format!("{e}"))?;
    }
    if let Some(name) = find_zip_entry_name(&mut data_zip, "SUP_SUPPORT.txt") {
        let entry = data_zip.by_name(&name).map_err(|e| format!("{e}"))?;
        let tx = writer.conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let mut s = tx.prepare_cached("INSERT OR REPLACE INTO stg_r_support VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)").map_err(|e| format!("{e}"))?;
        stream_csv_reader(entry, |t, r| { ingest_radio_support(&mut s, t, r); Ok(()) })?;
        drop(s);
        tx.commit().map_err(|e| format!("{e}"))?;
    }
    if let Some(name) = find_zip_entry_name(&mut data_zip, "SUP_ANTENNE.txt") {
        let entry = data_zip.by_name(&name).map_err(|e| format!("{e}"))?;
        let tx = writer.conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let mut s = tx.prepare_cached("INSERT OR REPLACE INTO stg_r_antenne VALUES (?1, ?2, ?3, ?4, ?5)").map_err(|e| format!("{e}"))?;
        stream_csv_reader(entry, |t, r| { ingest_radio_antenne(&mut s, t, r, refs.type_antenne); Ok(()) })?;
        drop(s);
        tx.commit().map_err(|e| format!("{e}"))?;
    }
    if let Some(name) = find_zip_entry_name(&mut data_zip, "SUP_EMETTEUR.txt") {
        let entry = data_zip.by_name(&name).map_err(|e| format!("{e}"))?;
        let tx = writer.conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let mut s = tx.prepare_cached("INSERT INTO stg_r_emetteur VALUES (?1, ?2, ?3, ?4)").map_err(|e| format!("{e}"))?;
        stream_csv_reader(entry, |t, r| { ingest_radio_emetteur(&mut s, t, r); Ok(()) })?;
        drop(s);
        tx.commit().map_err(|e| format!("{e}"))?;
    }
    if let Some(name) = find_zip_entry_name(&mut data_zip, "SUP_BANDE.txt") {
        let entry = data_zip.by_name(&name).map_err(|e| format!("{e}"))?;
        let tx = writer.conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let mut s = tx.prepare_cached("INSERT INTO stg_r_bande VALUES (?1, ?2, ?3, ?4)").map_err(|e| format!("{e}"))?;
        stream_csv_reader(entry, |t, r| { ingest_radio_bande(&mut s, t, r); Ok(()) })?;
        drop(s);
        tx.commit().map_err(|e| format!("{e}"))?;
    }
    writer.finish_staging()?;
    build_from_staging(&writer, refs, &req.version, &req.zip_version, "Inconnue", req.allowed_service_mask, sink)?;
    drop(writer);
    let _ = std::fs::remove_file(&req.staging_radio_path);
    Ok(())
}

fn build_support_address(
    table: &CsvTable,
    rec: &csv::StringRecord,
    code_insee: Option<&str>,
    communes: &HashMap<String, String>,
) -> String {
    let mut rue_parts = Vec::with_capacity(4);
    for col in &["adr_lb_lieu", "adr_lb_add1", "adr_lb_add2", "adr_lb_add3"] {
        let v = clean_text(table.idx(col).and_then(|i| rec.get(i)));
        if !v.is_empty() {
            rue_parts.push(v);
        }
    }
    let cp = clean_text(table.idx("adr_nm_cp").and_then(|i| rec.get(i)));
    let ville = code_insee.and_then(|c| communes.get(c)).map(|s| s.as_str()).unwrap_or("");
    let mut adresse = rue_parts.join(", ");
    if !cp.is_empty() || !ville.is_empty() {
        if !adresse.is_empty() {
            adresse.push_str(", ");
        }
        let cp_ville = format!("{cp} {ville}").trim().to_string();
        adresse.push_str(&cp_ville);
    }
    adresse
}

fn build_emr_freqs(conn: &Connection) -> BResult<()> {
    let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
    let mut ins = tx
        .prepare_cached("INSERT OR REPLACE INTO stg_emr_freqs VALUES (?1, ?2)")
        .map_err(|e| format!("{e}"))?;
    let mut q = conn
        .prepare("SELECT emr_id, f_deb_raw, f_fin_raw, unite FROM stg_bande ORDER BY emr_id, rowid")
        .map_err(|e| format!("{e}"))?;
    let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
    let mut current_emr: Option<String> = None;
    let mut parts: Vec<String> = Vec::new();
    while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
        let emr_id: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
        if current_emr.as_deref() != Some(&emr_id) {
            if let Some(prev) = current_emr.take() {
                let _ = ins.execute(params![prev, parts.join(", ")]);
                parts.clear();
            }
            current_emr = Some(emr_id);
        }
        let fd: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
        let ff: String = row.get::<_, Option<String>>(2)?.unwrap_or_default();
        let u: String = row.get::<_, Option<String>>(3)?.unwrap_or_else(|| "M".to_string());
        parts.push(format_band_range(&fd, &ff, &u));
    }
    if let Some(prev) = current_emr {
        let _ = ins.execute(params![prev, parts.join(", ")]);
    }
    drop(rows);
    drop(q);
    drop(ins);
    tx.commit().map_err(|e| format!("{e}"))?;
    Ok(())
}

fn stage_frequency_azimuths(conn: &Connection) -> BResult<()> {
    let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
    let mut ins = tx
        .prepare_cached("INSERT OR REPLACE INTO stg_azimut_json VALUES (?1, ?2)")
        .map_err(|e| format!("{e}"))?;
    let mut q = conn
        .prepare("SELECT id_anfr, systeme_upper, azimut FROM stg_sysazimut ORDER BY id_anfr, systeme_upper, azimut")
        .map_err(|e| format!("{e}"))?;
    let mut rows = q.query([]).map_err(|e| format!("{e}"))?;

    let mut current_id: Option<String> = None;
    let mut current_sys: Option<String> = None;
    let mut sys_az: Vec<i32> = Vec::new();
    let mut station_map: Vec<(String, Vec<i32>)> = Vec::new();

    let flush_station = |id: Option<String>, map: &mut Vec<(String, Vec<i32>)>, ins: &mut rusqlite::Statement<'_>| {
        if let Some(sid) = id {
            if !map.is_empty() {
                let mut json = String::from("{");
                for (i, (sys, azs)) in map.iter().enumerate() {
                    if i > 0 {
                        json.push(',');
                    }
                    let key_json = serde_json::to_string(sys).unwrap_or_else(|_| "\"\"".to_string());
                    json.push_str(&key_json);
                    json.push_str(":[");
                    for (j, a) in azs.iter().enumerate() {
                        if j > 0 {
                            json.push(',');
                        }
                        json.push_str(&a.to_string());
                    }
                    json.push(']');
                }
                json.push('}');
                let _ = ins.execute(params![sid, json]);
                map.clear();
            }
        }
    };

    while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
        let id: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
        let sys: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
        let az: i32 = row.get(2)?;
        if current_id.as_deref() != Some(&id) {
            if let Some(prev_sys) = current_sys.take() {
                if !sys_az.is_empty() {
                    station_map.push((prev_sys, std::mem::take(&mut sys_az)));
                }
            }
            flush_station(current_id.take(), &mut station_map, &mut ins);
            current_id = Some(id);
        }
        if current_sys.as_deref() != Some(&sys) {
            if let Some(prev_sys) = current_sys.take() {
                if !sys_az.is_empty() {
                    station_map.push((prev_sys, std::mem::take(&mut sys_az)));
                }
            }
            current_sys = Some(sys);
        }
        if sys_az.last().copied() != Some(az) {
            sys_az.push(az);
        }
    }
    if let Some(prev_sys) = current_sys.take() {
        if !sys_az.is_empty() {
            station_map.push((prev_sys, sys_az));
        }
    }
    flush_station(current_id, &mut station_map, &mut ins);
    drop(rows);
    drop(q);
    drop(ins);
    tx.commit().map_err(|e| format!("{e}"))?;
    Ok(())
}

fn apply_azimuts(conn: &Connection, stations: &mut StationAccumulator) -> BResult<()> {
    let mut q = conn
        .prepare("SELECT id_anfr, azimut, is_fh FROM stg_antenne_sta WHERE azimut IS NOT NULL ORDER BY id_anfr")
        .map_err(|e| format!("{e}"))?;
    let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
    let mut current_id: Option<String> = None;
    let mut mobile: BTreeSet<i32> = BTreeSet::new();
    let mut fh: BTreeSet<i32> = BTreeSet::new();

    let flush = |cid: &Option<String>, mob: &mut BTreeSet<i32>, fh_set: &mut BTreeSet<i32>, stations: &mut StationAccumulator| {
        if let Some(ref id) = cid {
            if let Some(idx) = stations.index_of(id) {
                if !mob.is_empty() {
                    stations.rows[idx].azimuts = Some(mob.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(","));
                }
                if !fh_set.is_empty() {
                    stations.rows[idx].azimuts_fh = Some(fh_set.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(","));
                    stations.rows[idx].tech_mask |= TECH_FH;
                    stations.rows[idx].band_mask |= crate::parsing::BAND_FH;
                }
            }
        }
        mob.clear();
        fh_set.clear();
    };

    while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
        let id: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
        if current_id.as_deref() != Some(&id) {
            flush(&current_id, &mut mobile, &mut fh, stations);
            current_id = Some(id);
        }
        let az: Option<i32> = row.get(1)?;
        let is_fh: i32 = row.get::<_, Option<i32>>(2)?.unwrap_or(0);
        if let Some(a) = az {
            if is_fh == 1 {
                fh.insert(a);
            } else {
                mobile.insert(a);
            }
        }
    }
    flush(&current_id, &mut mobile, &mut fh, stations);

    let mut q2 = conn
        .prepare("SELECT id_anfr, azimut FROM stg_sysazimut ORDER BY id_anfr")
        .map_err(|e| format!("{e}"))?;
    let mut rows2 = q2.query([]).map_err(|e| format!("{e}"))?;
    let mut cur_sys: Option<String> = None;
    let mut sys_mob: BTreeSet<i32> = BTreeSet::new();
    while let Some(row) = rows2.next().map_err(|e| format!("{e}"))? {
        let id: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
        if cur_sys.as_deref() != Some(&id) {
            if let Some(ref prev) = cur_sys {
                if !sys_mob.is_empty() {
                    if let Some(idx) = stations.index_of(prev) {
                        if stations.rows[idx].azimuts.as_deref().unwrap_or("").trim().is_empty() {
                            stations.rows[idx].azimuts = Some(sys_mob.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(","));
                        }
                    }
                }
            }
            sys_mob.clear();
            cur_sys = Some(id);
        }
        let az: Option<i32> = row.get(1)?;
        if let Some(a) = az {
            sys_mob.insert(a);
        }
    }
    if let Some(ref prev) = cur_sys {
        if !sys_mob.is_empty() {
            if let Some(idx) = stations.index_of(prev) {
                if stations.rows[idx].azimuts.as_deref().unwrap_or("").trim().is_empty() {
                    stations.rows[idx].azimuts = Some(sys_mob.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(","));
                }
            }
        }
    }
    Ok(())
}

fn apply_announced_details(conn: &Connection) -> BResult<()> {
    let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
    let mut ins = tx
        .prepare_cached("INSERT OR REPLACE INTO stg_details_extra VALUES (?1, ?2)")
        .map_err(|e| format!("{e}"))?;
    let mut q = conn
        .prepare(
            "SELECT s.id_anfr, group_concat(s.systeme || ' :  | ' || COALESCE(s.statut, 'Inconnu') || ' | ' || COALESCE(s.emr_dt, '') || ' | Azimut non specifie', char(10))
             FROM stg_sysstatus s
             WHERE NOT EXISTS (SELECT 1 FROM stg_emetteur e WHERE e.id_anfr = s.id_anfr AND UPPER(e.systeme) = s.systeme_upper)
             GROUP BY s.id_anfr",
        )
        .map_err(|e| format!("{e}"))?;
    let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
    while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
        let id: Option<String> = row.get(0)?;
        let det: Option<String> = row.get(1)?;
        if let (Some(i), Some(d)) = (id, det) {
            let _ = ins.execute(params![i, d]);
        }
    }
    drop(rows);
    drop(q);
    drop(ins);
    tx.commit().map_err(|e| format!("{e}"))?;
    Ok(())
}

fn apply_details(conn: &Connection, sink: &mut dyn ProgressSink) -> BResult<()> {
    let mut announced: HashMap<String, String> = HashMap::new();
    {
        let mut q = conn
            .prepare("SELECT id_anfr, details FROM stg_details_extra")
            .map_err(|e| format!("{e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
        while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
            if let (Some(id), Some(det)) = (row.get::<_, Option<String>>(0)?, row.get::<_, Option<String>>(1)?) {
                announced.insert(id, det);
            }
        }
    }

    let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
    let mut ins = tx
        .prepare_cached("INSERT OR REPLACE INTO stg_details VALUES (?1, ?2)")
        .map_err(|e| format!("{e}"))?;
    let mut q = conn
        .prepare(
            "SELECT e.id_anfr, e.systeme, COALESCE(f.freqs_text, ''), COALESCE(s.statut, sf.statut_label, 'Inconnu'), COALESCE(s.emr_dt, ''), COALESCE(ap.physique, 'Azimut non specifie')
             FROM stg_emetteur e
             LEFT JOIN stg_emr_freqs f ON e.emr_id = f.emr_id
             LEFT JOIN stg_antenne ap ON e.aer_id = ap.aer_id
             LEFT JOIN stg_sysstatus s ON e.id_anfr = s.id_anfr AND s.systeme_upper = UPPER(e.systeme)
             JOIN stg_station_final sf ON e.id_anfr = sf.id_anfr
             ORDER BY e.id_anfr",
        )
        .map_err(|e| format!("{e}"))?;
    let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
    let mut current_id: Option<String> = None;
    let mut current_ann: Option<String> = None;
    let mut lines: BTreeSet<String> = BTreeSet::new();
    let mut emitted = 0i64;

    let flush = |cid: &Option<String>, cann: &mut Option<String>, lns: &mut BTreeSet<String>, ins: &mut rusqlite::Statement<'_>, emitted: &mut i64, sink: &mut dyn ProgressSink| -> Result<(), String> {
        if let Some(ref id) = cid {
            if let Some(ann) = cann.take() {
                for part in ann.split('\n') {
                    lns.insert(part.to_string());
                }
            }
            if !lns.is_empty() {
                let text = lns.iter().cloned().collect::<Vec<_>>().join("\n");
                if let Some(enc) = encode_frequency_details(&text) {
                    let _ = ins.execute(params![id, enc]);
                }
                lns.clear();
            }
            *emitted += 1;
            if *emitted % EMIT_EVERY == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_BUILDING_DETAILS, 85, Some("stg_emetteur -> details_frequences"), *emitted);
            }
        }
        Ok(())
    };

    while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
        let id: Option<String> = row.get(0)?;
        let Some(id_str) = id else { continue };
        if current_id.as_deref() != Some(&id_str) {
            flush(&current_id, &mut current_ann, &mut lines, &mut ins, &mut emitted, sink)?;
            current_ann = announced.remove(&id_str);
            current_id = Some(id_str);
        }
        let sys: String = row.get::<_, Option<String>>(1)?.unwrap_or_else(|| "Inconnu".to_string());
        let freqs: String = row.get::<_, Option<String>>(2)?.unwrap_or_default();
        let stat: String = row.get::<_, Option<String>>(3)?.unwrap_or_else(|| "Inconnu".to_string());
        let dt: String = row.get::<_, Option<String>>(4)?.unwrap_or_default();
        let phys: String = row.get::<_, Option<String>>(5)?.unwrap_or_else(|| "Azimut non specifie".to_string());
        lines.insert(format!("{sys} : {freqs} | {stat} | {dt} | {phys}"));
    }
    flush(&current_id, &mut current_ann, &mut lines, &mut ins, &mut emitted, sink)?;

    for (id, ann) in announced {
        let mut lns: BTreeSet<String> = BTreeSet::new();
        for p in ann.split('\n') {
            lns.insert(p.to_string());
        }
        if !lns.is_empty() {
            let text = lns.into_iter().collect::<Vec<_>>().join("\n");
            if let Some(enc) = encode_frequency_details(&text) {
                let _ = ins.execute(params![id, enc]);
            }
        }
    }
    drop(rows);
    drop(q);
    drop(ins);
    tx.commit().map_err(|e| format!("{e}"))?;
    Ok(())
}

fn parse_arcep_files(paths: &[String]) -> HashMap<(String, String), (Option<String>, i32)> {
    let mut map: HashMap<(String, String), (Option<String>, i32)> = HashMap::new();
    const STATION_ALIASES: &[&str] = &["id_station_anfr", "sta_nm_anfr", "id_anfr", "station_anfr"];
    const OPERATOR_ALIASES: &[&str] = &["nom_op", "op_name", "operator", "operator_name"];
    const NIDT_ALIASES: &[&str] = &["num_site", "op_site_id", "nidt"];
    const ZB_ALIASES: &[&str] = &["site_zb", "zb", "zone_blanche"];
    const DCC_ALIASES: &[&str] = &["site_dcc", "dcc"];

    let first_alias = |table: &CsvTable, rec: &csv::StringRecord, aliases: &[&str]| -> Option<String> {
        for a in aliases {
            if let Some(idx) = table.idx(a) {
                if let Some(v) = rec.get(idx) {
                    let t = v.trim();
                    if !t.is_empty() {
                        return Some(t.to_string());
                    }
                }
            }
        }
        None
    };

    let is_true = |v: Option<String>| -> bool {
        match v {
            Some(s) => matches!(
                s.trim().to_ascii_lowercase().as_str(),
                "1" | "true" | "vrai" | "oui" | "yes" | "y"
            ),
            None => false,
        }
    };

    for path in paths {
        if let Ok(file) = File::open(path) {
            let _ = stream_csv_reader(file, |table, rec| {
                let id_anfr = normalize_id_anfr(first_alias(table, rec, STATION_ALIASES).as_deref());
                let op = first_alias(table, rec, OPERATOR_ALIASES)
                    .unwrap_or_default()
                    .trim()
                    .to_ascii_uppercase();
                if id_anfr.is_empty() || op.is_empty() {
                    return Ok(());
                }
                let nidt = first_alias(table, rec, NIDT_ALIASES);
                let zb = is_true(first_alias(table, rec, ZB_ALIASES))
                    || is_true(first_alias(table, rec, DCC_ALIASES));
                let entry = map.entry((id_anfr, op)).or_insert((None, 0));
                if nidt.is_some() {
                    entry.0 = nidt;
                }
                if zb {
                    entry.1 = 1;
                }
                Ok(())
            });
        }
    }
    map
}

fn populate_fh_links(
    conn: &Connection,
    stations: &StationAccumulator,
    ref_exploitant: &HashMap<String, String>,
    fh_azimuths_all: &HashMap<String, HashSet<i32>>,
    fh_station_adm: &HashMap<String, i32>,
    fh_coords_all: &HashMap<String, (f64, f64)>,
) -> BResult<()> {
    if fh_azimuths_all.is_empty() {
        conn.execute(CREATE_FH_LINK_INDEX, [])
            .map_err(|e| format!("{e}"))?;
        return Ok(());
    }

    struct Site {
        id_anfr: String,
        lat: f64,
        lon: f64,
        group_key: String,
        azimuths: Vec<f64>,
        op_id: Option<i32>,
        adm_id: Option<i32>,
        op_label: Option<String>,
    }

    let mut sites: Vec<Site> = Vec::with_capacity(fh_azimuths_all.len());
    for (id_anfr, az_set) in fh_azimuths_all {
        if az_set.is_empty() {
            continue;
        }
        let Some(&(lat, lon)) = fh_coords_all.get(id_anfr) else {
            continue;
        };
        let st_idx = stations.index_of(id_anfr);
        let op_id = st_idx.map(|i| stations.rows[i].operateur_id);
        let adm_id = st_idx
            .and_then(|i| stations.rows[i].adm_id)
            .or_else(|| fh_station_adm.get(id_anfr).copied());
        let group_key = if let Some(a) = adm_id {
            format!("adm_{a}")
        } else if let Some(o) = op_id {
            format!("op_{o}")
        } else {
            continue;
        };
        let op_label = st_idx
            .map(|i| stations.rows[i].operator_label.clone())
            .filter(|s| !s.trim().is_empty())
            .or_else(|| adm_id.and_then(|a| ref_exploitant.get(&a.to_string()).cloned()));
        let mut azs: Vec<f64> = az_set.iter().map(|&a| a as f64).collect();
        azs.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        sites.push(Site {
            id_anfr: id_anfr.clone(),
            lat,
            lon,
            group_key,
            azimuths: azs,
            op_id,
            adm_id,
            op_label,
        });
    }

    let cell = |deg: f64| -> i32 { (deg / 0.2).floor() as i32 };
    let mut grid: HashMap<(String, i32, i32), Vec<usize>> = HashMap::with_capacity(sites.len());
    for (idx, s) in sites.iter().enumerate() {
        grid.entry((s.group_key.clone(), cell(s.lat), cell(s.lon)))
            .or_default()
            .push(idx);
    }

    struct Cand {
        a: usize,
        b: usize,
        az_a: usize,
        az_b: usize,
        dist: f64,
        bearing_ab: f64,
    }
    let mut cands: Vec<Cand> = Vec::new();
    let max_dist = 120.0;
    let min_dist = 0.05;
    let tol = 2.5;

    for (idx_a, sa) in sites.iter().enumerate() {
        let lat_d = max_dist / 111.0;
        let cos_lat = (sa.lat * PI / 180.0).cos().max(0.2);
        let lon_d = max_dist / (111.0 * cos_lat);
        for clat in cell(sa.lat - lat_d)..=cell(sa.lat + lat_d) {
            for clon in cell(sa.lon - lon_d)..=cell(sa.lon + lon_d) {
                if let Some(bucket) = grid.get(&(sa.group_key.clone(), clat, clon)) {
                    for &idx_b in bucket {
                        let sb = &sites[idx_b];
                        if sb.id_anfr <= sa.id_anfr {
                            continue;
                        }
                        let d = haversine_km(sa.lat, sa.lon, sb.lat, sb.lon);
                        if d < min_dist || d > max_dist {
                            continue;
                        }
                        let bab = bearing_deg(sa.lat, sa.lon, sb.lat, sb.lon);
                        let bba = bearing_deg(sb.lat, sb.lon, sa.lat, sa.lon);
                        let mut best_a = None;
                        let mut err_a = f64::MAX;
                        for (i, &az) in sa.azimuths.iter().enumerate() {
                            let e = angle_diff_deg(az, bab);
                            if e <= tol && e < err_a {
                                err_a = e;
                                best_a = Some(i);
                            }
                        }
                        let Some(az_a) = best_a else { continue };
                        let mut best_b = None;
                        let mut err_b = f64::MAX;
                        for (i, &az) in sb.azimuths.iter().enumerate() {
                            let e = angle_diff_deg(az, bba);
                            if e <= tol && e < err_b {
                                err_b = e;
                                best_b = Some(i);
                            }
                        }
                        let Some(az_b) = best_b else { continue };
                        cands.push(Cand {
                            a: idx_a,
                            b: idx_b,
                            az_a,
                            az_b,
                            dist: d,
                            bearing_ab: bab,
                        });
                    }
                }
            }
        }
    }

    cands.sort_by(|x, y| x.dist.partial_cmp(&y.dist).unwrap_or(std::cmp::Ordering::Equal));
    let mut used: HashSet<(usize, usize)> = HashSet::new();
    let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
    {
        let mut ins = tx
            .prepare_cached("INSERT OR REPLACE INTO fh_link (id_anfr_a, lat_a, lon_a, id_anfr_b, lat_b, lon_b, min_lat, max_lat, min_lon, max_lon, operateur_id, adm_id, operator_label, azimut_ab, distance_m) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)")
            .map_err(|e| format!("{e}"))?;
        for c in cands {
            if !used.insert((c.a, c.az_a)) || !used.insert((c.b, c.az_b)) {
                continue;
            }
            let sa = &sites[c.a];
            let sb = &sites[c.b];
            let op_id = sa.op_id.or(sb.op_id);
            let adm_id = sa.adm_id.or(sb.adm_id);
            let op_lbl = sa
                .op_label
                .as_deref()
                .or(sb.op_label.as_deref())
                .unwrap_or(&sa.group_key);
            let _ = ins.execute(params![
                &sa.id_anfr,
                sa.lat,
                sa.lon,
                &sb.id_anfr,
                sb.lat,
                sb.lon,
                sa.lat.min(sb.lat),
                sa.lat.max(sb.lat),
                sa.lon.min(sb.lon),
                sa.lon.max(sb.lon),
                op_id,
                adm_id,
                op_lbl,
                (c.bearing_ab * 100.0).round() / 100.0,
                (c.dist * 1000.0).round() as i32,
            ]);
        }
    }
    tx.commit().map_err(|e| format!("{e}"))?;
    conn.execute(CREATE_FH_LINK_INDEX, [])
        .map_err(|e| format!("{e}"))?;
    Ok(())
}

fn haversine_km(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let r = 6371.0;
    let dlat = (lat2 - lat1) * PI / 180.0;
    let dlon = (lon2 - lon1) * PI / 180.0;
    let a = (dlat / 2.0).sin().powi(2)
        + (lat1 * PI / 180.0).cos() * (lat2 * PI / 180.0).cos() * (dlon / 2.0).sin().powi(2);
    2.0 * r * a.sqrt().atan2((1.0 - a).sqrt())
}

fn bearing_deg(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let phi1 = lat1 * PI / 180.0;
    let phi2 = lat2 * PI / 180.0;
    let dlam = (lon2 - lon1) * PI / 180.0;
    let y = dlam.sin() * phi2.cos();
    let x = phi1.cos() * phi2.sin() - phi1.sin() * phi2.cos() * dlam.cos();
    (y.atan2(x) * 180.0 / PI + 360.0) % 360.0
}

fn angle_diff_deg(a: f64, b: f64) -> f64 {
    let d = (a - b).abs() % 360.0;
    if d > 180.0 { 360.0 - d } else { d }
}

fn decode_details(raw: Option<&str>) -> String {
    let Some(v) = raw else { return String::new() };
    if let Some(b64) = v.strip_prefix("Z1:") {
        if let Ok(bytes) = STANDARD.decode(b64) {
            let mut dec = ZlibDecoder::new(&bytes[..]);
            let mut out = String::new();
            if dec.read_to_string(&mut out).is_ok() {
                return out;
            }
        }
    }
    v.to_string()
}

fn strip_accents_lower(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for ch in s.trim().chars() {
        let lower = ch.to_ascii_lowercase();
        let mapped = match lower {
            'à' | 'á' | 'â' | 'ã' | 'ä' | 'å' | 'À' | 'Á' | 'Â' | 'Ã' | 'Ä' | 'Å' => 'a',
            'é' | 'è' | 'ê' | 'ë' | 'É' | 'È' | 'Ê' | 'Ë' => 'e',
            'ì' | 'í' | 'î' | 'ï' | 'Ì' | 'Í' | 'Î' | 'Ï' => 'i',
            'ò' | 'ó' | 'ô' | 'õ' | 'ö' | 'Ò' | 'Ó' | 'Ô' | 'Õ' | 'Ö' => 'o',
            'ù' | 'ú' | 'û' | 'ü' | 'Ù' | 'Ú' | 'Û' | 'Ü' => 'u',
            'ç' | 'Ç' => 'c',
            'ñ' | 'Ñ' => 'n',
            _ => lower,
        };
        out.push(mapped);
    }
    out
}

fn populate_current_stats(conn: &Connection) -> BResult<()> {
    const TECH_MAP: &[(&str, i32)] = &[
        ("2G", TECH_2G),
        ("3G", TECH_3G),
        ("4G", TECH_4G),
        ("5G", TECH_5G),
    ];
    const BAND_MAP: &[(&str, i32, &str)] = &[
        ("2G|900", BAND_2G_900, "2G 900 MHz"),
        ("2G|1800", BAND_2G_1800, "2G 1800 MHz"),
        ("3G|900", BAND_3G_900, "3G 900 MHz"),
        ("3G|2100", BAND_3G_2100, "3G 2100 MHz"),
        ("4G|700", BAND_4G_700, "4G 700 MHz"),
        ("4G|800", BAND_4G_800, "4G 800 MHz"),
        ("4G|900", BAND_4G_900, "4G 900 MHz"),
        ("4G|1800", BAND_4G_1800, "4G 1800 MHz"),
        ("4G|2100", BAND_4G_2100, "4G 2100 MHz"),
        ("4G|2600", BAND_4G_2600, "4G 2600 MHz"),
        ("5G|700", BAND_5G_700, "5G 700 MHz"),
        ("5G|1400", BAND_5G_1400, "5G 1400 MHz (exp)"),
        ("5G|2100", BAND_5G_2100, "5G 2100 MHz"),
        ("5G|3500", BAND_5G_3500, "5G 3500 MHz"),
        ("5G|4200", BAND_5G_4200, "5G 4200 MHz (exp)"),
        ("5G|26000", BAND_5G_26000, "5G 26 GHz (exp)"),
    ];
    const OVERSEAS_MARKERS: &[&str] = &[
        "FREE CARAIBE", "FREE CARAIBES", "OUTREMER TELECOM", "OUTREMER", "SFR CARAIBE",
        "UTS CARAIBE", "DAUPHIN TELECOM", "DIGICEL", "SRR", "TELCO OI", "TELCO", "ONLY",
        "ZEOP", "MAORE", "SPM TELECOM", "GLOBALTEL", "OPT NOUVELLE", "OPT NC", "ONATI",
        "VINI", "PMT", "VODAFONE", "VITI", "SPT",
    ];

    conn.execute_batch(
        "DROP TABLE IF EXISTS stg.stg_stat_pair;
         CREATE TABLE stg.stg_stat_pair (operator_name TEXT NOT NULL, category TEXT NOT NULL, item_key TEXT NOT NULL, support_key TEXT NOT NULL, in_total INTEGER NOT NULL, in_active INTEGER NOT NULL);",
    )
    .map_err(|e| format!("stats stg: {e}"))?;

    {
        let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
        let mut ins = tx
            .prepare_cached("INSERT INTO stg_stat_pair VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
            .map_err(|e| format!("{e}"))?;
        let mut q = conn
            .prepare(
                "SELECT s.id_anfr, s.id_support, UPPER(TRIM(o.libelle)), l.code_insee, COALESCE(l.tech_mask, 0), COALESCE(l.band_mask, 0), COALESCE(st.libelle, ''), COALESCE(t.has_active, 0), t.details_frequences
                 FROM support s
                 INNER JOIN localisation l ON s.id_anfr = l.id_anfr
                 INNER JOIN ref_operateur o ON l.operateur_id = o.id
                 LEFT JOIN technique t ON l.id_anfr = t.id_anfr
                 LEFT JOIN ref_statut st ON t.statut_id = st.id",
            )
            .map_err(|e| format!("{e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
        while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
            let id_anfr: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            let id_support: Option<String> = row.get(1)?;
            let raw_op: String = row.get::<_, Option<String>>(2)?.unwrap_or_default();
            let code_insee: Option<String> = row.get(3)?;
            let tech_mask: i32 = row.get(4)?;
            let band_mask: i32 = row.get(5)?;
            let statut: String = row.get::<_, Option<String>>(6)?.unwrap_or_default();
            let has_active: i32 = row.get(7)?;
            let details_raw: Option<String> = row.get(8)?;

            let op_name = if code_insee
                .as_deref()
                .map(|c| {
                    let t = c.trim();
                    t.starts_with("97") || t.starts_with("98")
                })
                .unwrap_or(false)
            {
                let norm = strip_accents_lower(&raw_op).to_ascii_uppercase();
                if OVERSEAS_MARKERS.iter().any(|m| norm.contains(m)) {
                    raw_op
                } else if norm.contains("ORANGE") {
                    "ORANGE_OVERSEAS".to_string()
                } else if norm == "SFR"
                    || norm == "SFR MAYOTTE"
                    || norm == "SOCIETE FRANCAISE DU RADIOTELEPHONE"
                {
                    "SFR_OVERSEAS".to_string()
                } else if norm.contains("BOUYGUES") || norm.contains("BYTEL") {
                    "BOUYGUES_OVERSEAS".to_string()
                } else if norm == "FREE" || norm == "FREE MOBILE" {
                    "FREE_OVERSEAS".to_string()
                } else {
                    raw_op
                }
            } else {
                raw_op
            };

            let sup_key = id_support
                .filter(|s| !s.is_empty())
                .unwrap_or_else(|| id_anfr.clone())
                .trim()
                .to_string();
            if op_name.is_empty() || sup_key.is_empty() {
                continue;
            }

            let details_dec = decode_details(details_raw.as_deref());
            let mut det_tech: HashSet<&'static str> = HashSet::new();
            let mut det_band: HashSet<&'static str> = HashSet::new();
            for line in details_dec.split('\n') {
                let l = line.trim();
                if l.is_empty() {
                    continue;
                }
                let mut parts = l.split('|');
                let raw_freq = parts.next().unwrap_or("").trim();
                let st = parts.next().unwrap_or("").trim();
                let st_norm = strip_accents_lower(st);
                if st_norm.contains("en service") || st_norm.contains("techniquement operationnel") {
                    let u = raw_freq.to_ascii_uppercase();
                    let gen = if u.contains("5G") || u.contains("NR") {
                        Some("5G")
                    } else if u.contains("4G") || u.contains("LTE") {
                        Some("4G")
                    } else if u.contains("3G") || u.contains("UMTS") {
                        Some("3G")
                    } else if u.contains("2G") || u.contains("GSM") {
                        Some("2G")
                    } else {
                        None
                    };
                    if let Some(g) = gen {
                        det_tech.insert(g);
                        let mut num_buf = String::new();
                        for ch in u.chars() {
                            if ch.is_ascii_digit() {
                                num_buf.push(ch);
                            } else if !num_buf.is_empty() {
                                if num_buf.len() >= 2 && num_buf.len() <= 5 {
                                    let cand = if num_buf == "26" && g == "5G" {
                                        "5G|26000".to_string()
                                    } else {
                                        format!("{g}|{num_buf}")
                                    };
                                    for &(bk, _, _) in BAND_MAP {
                                        if bk == cand {
                                            det_band.insert(bk);
                                        }
                                    }
                                }
                                num_buf.clear();
                            }
                        }
                        if num_buf.len() >= 2 && num_buf.len() <= 5 {
                            let cand = if num_buf == "26" && g == "5G" {
                                "5G|26000".to_string()
                            } else {
                                format!("{g}|{num_buf}")
                            };
                            for &(bk, _, _) in BAND_MAP {
                                if bk == cand {
                                    det_band.insert(bk);
                                }
                            }
                        }
                    }
                }
            }

            let has_det_active = !det_tech.is_empty() || !det_band.is_empty();
            let st_norm = strip_accents_lower(&statut);
            let is_act_stat = st_norm.contains("en service") || st_norm.contains("techniquement operationnel");
            let is_active_sup = has_det_active || has_active == 1 || is_act_stat;

            let _ = ins.execute(params![
                &op_name,
                "support",
                "ALL",
                &sup_key,
                1,
                if is_active_sup { 1 } else { 0 }
            ]);

            for &(tk, bit) in TECH_MAP {
                let in_tot = (tech_mask & bit) != 0;
                let in_act = if has_det_active {
                    det_tech.contains(tk)
                } else {
                    is_active_sup && in_tot
                };
                if in_tot || in_act {
                    let _ = ins.execute(params![
                        &op_name,
                        "tech",
                        tk,
                        &sup_key,
                        if in_tot { 1 } else { 0 },
                        if in_act { 1 } else { 0 }
                    ]);
                }
            }
            for &(bk, bit, _) in BAND_MAP {
                let in_tot = (band_mask & bit) != 0;
                let in_act = if has_det_active {
                    det_band.contains(bk)
                } else {
                    is_active_sup && in_tot
                };
                if in_tot || in_act {
                    let _ = ins.execute(params![
                        &op_name,
                        "band",
                        bk,
                        &sup_key,
                        if in_tot { 1 } else { 0 },
                        if in_act { 1 } else { 0 }
                    ]);
                }
            }
        }
        drop(rows);
        drop(q);
        drop(ins);
        tx.commit().map_err(|e| format!("{e}"))?;
    }

    conn.execute_batch(
        "CREATE INDEX IF NOT EXISTS stg.ix_stg_stat_pair ON stg_stat_pair(operator_name, category, item_key, support_key, in_total, in_active);
         DELETE FROM radio_stat_current;",
    )
    .map_err(|e| format!("{e}"))?;

    let mut rows_out: Vec<(String, String, String, String, i32, i32)> = Vec::new();
    {
        let mut q = conn
            .prepare("SELECT operator_name, category, item_key, support_key, in_total, in_active FROM stg_stat_pair ORDER BY operator_name, category, item_key, support_key")
            .map_err(|e| format!("{e}"))?;
        let mut rows = q.query([]).map_err(|e| format!("{e}"))?;
        let mut cur_op: Option<String> = None;
        let mut cur_cat = String::new();
        let mut cur_item = String::new();
        let mut cur_sup: Option<String> = None;
        let mut in_tot = false;
        let mut in_act = false;
        let mut tot = 0i32;
        let mut act = 0i32;

        let close_sup = |csup: &mut Option<String>, it: &mut bool, ia: &mut bool, t: &mut i32, a: &mut i32| {
            if csup.take().is_some() {
                if *it { *t += 1; }
                if *ia { *a += 1; }
            }
            *it = false;
            *ia = false;
        };
        let label_for = |cat: &str, item: &str| -> String {
            match cat {
                "support" => "Supports".to_string(),
                "band" => BAND_MAP
                    .iter()
                    .find(|&&(k, _, _)| k == item)
                    .map(|&(_, _, l)| l.to_string())
                    .unwrap_or_else(|| item.to_string()),
                _ => item.to_string(),
            }
        };

        while let Some(row) = rows.next().map_err(|e| format!("{e}"))? {
            let op: String = row.get::<_, Option<String>>(0)?.unwrap_or_default();
            let cat: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
            let item: String = row.get::<_, Option<String>>(2)?.unwrap_or_default();
            let sup: String = row.get::<_, Option<String>>(3)?.unwrap_or_default();
            let rt: i32 = row.get(4)?;
            let ra: i32 = row.get(5)?;
            if cur_op.as_deref() != Some(&op) || cur_cat != cat || cur_item != item {
                close_sup(&mut cur_sup, &mut in_tot, &mut in_act, &mut tot, &mut act);
                if let Some(prev_op) = cur_op.take() {
                    let lbl = label_for(&cur_cat, &cur_item);
                    rows_out.push((prev_op, cur_cat.clone(), cur_item.clone(), lbl, tot, act.min(tot)));
                }
                tot = 0;
                act = 0;
                cur_op = Some(op);
                cur_cat = cat;
                cur_item = item;
            } else if cur_sup.as_deref() != Some(&sup) {
                close_sup(&mut cur_sup, &mut in_tot, &mut in_act, &mut tot, &mut act);
            }
            cur_sup = Some(sup);
            in_tot = in_tot || rt == 1;
            in_act = in_act || ra == 1;
        }
        close_sup(&mut cur_sup, &mut in_tot, &mut in_act, &mut tot, &mut act);
        if let Some(prev_op) = cur_op {
            let lbl = label_for(&cur_cat, &cur_item);
            rows_out.push((prev_op, cur_cat, cur_item, lbl, tot, act.min(tot)));
        }
    }

    let _ = conn.execute("DROP TABLE IF EXISTS stg.stg_stat_pair", []);
    let tx = conn.unchecked_transaction().map_err(|e| format!("{e}"))?;
    {
        let mut ins = tx
            .prepare("INSERT INTO radio_stat_current (operator_name, category, item_key, label, total_count, active_count) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
            .map_err(|e| format!("{e}"))?;
        for (op, cat, item, lbl, tot, act) in rows_out {
            ins.execute(params![op, cat, item, lbl, tot, act])
                .map_err(|e| format!("{e}"))?;
        }
    }
    tx.commit().map_err(|e| format!("{e}"))?;
    Ok(())
}
