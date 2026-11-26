use crate::parsing::{
    detect_delimiter, decode_bytes_lossy, encode_frequency_details, BAND_2G_1800, BAND_2G_900,
    BAND_3G_1900, BAND_3G_2100, BAND_3G_900, BAND_4G_1800, BAND_4G_2100, BAND_4G_2300,
    BAND_4G_2600, BAND_4G_3500, BAND_4G_600, BAND_4G_700, BAND_4G_800, BAND_5G_1900,
    BAND_5G_2000, BAND_5G_2100, BAND_5G_2600, BAND_5G_26000, BAND_5G_3500, BAND_5G_3800,
    BAND_5G_600, BAND_5G_700, BAND_5G_850, TECH_2G, TECH_3G, TECH_4G, TECH_5G,
};
use crate::schema::{INSERT_ROOM_IDENTITY, MOBILE_CREATE_TABLES, MOBILE_SCHEMA_VERSION};
use crate::{BResult, BuildRequest, ProgressSink};
use rusqlite::{params, Connection};
use serde_json::{Map, Value};
use sha1::{Digest, Sha1};
use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::fs::File;
use std::io::{BufRead, BufReader, Read};
use std::path::Path;
use zip::ZipArchive;

const PHASE_READING_STATIONS: i32 = 2;
const PHASE_BUILDING_DETAILS: i32 = 6;
const PHASE_INSERTING: i32 = 7;
const PHASE_COMPUTING_STATS: i32 = 8;
const PHASE_FINALIZING: i32 = 9;

const CA_ISED_DETAILS_DDL: &str = "CREATE TABLE IF NOT EXISTS `ca_ised_site_details` (`id_anfr` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id_anfr`))";

const OPERATOR_PATTERNS: &[(&[&str], &str)] = &[
    (&["BELL MOBILITY", "BELL CANADA", "BELL MTS", "BELL ALIANT"], "Bell"),
    (&["ROGERS COMMUNICATIONS", "ROGERS WIRELESS", "ROGERS"], "Rogers"),
    (&["TELUS COMMUNICATIONS", "TELUS MOBILITY", "TELUS"], "Telus"),
    (&["VIDEOTRON", "VIDÉOTRON", "QUEBECOR", "FIZZ"], "Videotron"),
    (&["FREEDOM MOBILE", "SHAW TELECOM", "WIND MOBILE"], "Freedom Mobile"),
    (&["SASKTEL", "SASKATCHEWAN TELECOMMUNICATIONS"], "SaskTel"),
    (&["EASTLINK", "BRAGG COMMUNICATIONS"], "Eastlink"),
    (&["TBAYTEL"], "Tbaytel"),
    (&["ICE WIRELESS"], "Ice Wireless"),
    (&["SOGETEL"], "Sogetel"),
    (&["XPLORE", "XPLORNET"], "Xplore"),
    (&["COGECO"], "Cogeco"),
    (&["NORTHWESTEL"], "Northwestel"),
    (&["SSI MICRO", "SSIMICRO"], "SSI Micro"),
    (&["ECOTEL"], "Ecotel"),
    (&["TERRESTAR"], "Terrestar"),
];

fn ca_province_ref(code: &str) -> Option<(&'static str, f64, i64, &'static str)> {
    match code {
        "ON" => Some(("Ontario", 1_076_395.0, 16_124_000, "2024")),
        "QC" => Some(("Québec", 1_542_056.0, 9_056_000, "2024")),
        "BC" => Some(("Colombie-Britannique", 944_735.0, 5_698_000, "2024")),
        "AB" => Some(("Alberta", 661_848.0, 4_888_000, "2024")),
        "MB" => Some(("Manitoba", 647_797.0, 1_494_000, "2024")),
        "SK" => Some(("Saskatchewan", 651_036.0, 1_239_000, "2024")),
        "NS" => Some(("Nouvelle-Écosse", 55_284.0, 1_076_000, "2024")),
        "NB" => Some(("Nouveau-Brunswick", 72_908.0, 854_000, "2024")),
        "NL" => Some(("Terre-Neuve-et-Labrador", 405_212.0, 545_000, "2024")),
        "PE" => Some(("Île-du-Prince-Édouard", 5_660.0, 178_000, "2024")),
        "YT" => Some(("Yukon", 482_443.0, 46_700, "2024")),
        "NT" => Some(("Territoires du Nord-Ouest", 1_346_106.0, 44_700, "2024")),
        "NU" => Some(("Nunavut", 2_093_190.0, 41_100, "2024")),
        _ => None,
    }
}

fn clean_ca_text(v: Option<&str>) -> String {
    let Some(s) = v else { return String::new() };
    let mut out = String::new();
    for word in s.split_whitespace() {
        if !out.is_empty() {
            out.push(' ');
        }
        out.push_str(word);
    }
    if matches!(out.as_str(), "...." | "..." | ".." | "-" | "N/A") {
        String::new()
    } else {
        out
    }
}

fn match_canadian_operator(licensee: &str) -> Option<&'static str> {
    let upper = licensee.to_uppercase();
    for &(patterns, canonical) in OPERATOR_PATTERNS {
        if patterns.iter().any(|p| upper.contains(p)) {
            return Some(canonical);
        }
    }
    None
}

fn classify_technology(raw_tech: &str) -> Option<(&'static str, i32)> {
    let u = raw_tech.trim().to_ascii_uppercase();
    if u.is_empty() {
        return None;
    }
    let is_mobile = matches!(
        u.as_str(),
        "LTE" | "NR" | "5G" | "5G NR" | "UMTS" | "HSPA" | "HSPA+" | "WCDMA" | "GSM" | "EDGE" | "GPRS" | "CDMA" | "EVDO"
    ) || u.contains("LTE")
        || u.contains("NR")
        || u.contains("5G")
        || u.contains("UMTS")
        || u.contains("HSPA")
        || u.contains("GSM");
    if !is_mobile {
        return None;
    }
    if u.contains("NR") || u.contains("5G") {
        Some(("5G", TECH_5G))
    } else if u.contains("LTE") {
        Some(("4G", TECH_4G))
    } else if u.contains("UMTS") || u.contains("HSPA") || u.contains("WCDMA") {
        Some(("3G", TECH_3G))
    } else if u.contains("GSM") || u.contains("EDGE") || u.contains("GPRS") || u.contains("CDMA") {
        Some(("2G", TECH_2G))
    } else {
        None
    }
}

fn classify_band(tx_freq_mhz: Option<f64>, gen: &str) -> (String, i32) {
    let Some(f) = tx_freq_mhz else {
        return (gen.to_string(), 0);
    };
    let (band_label, mask) = if (600.0..698.0).contains(&f) {
        ("600".to_string(), if gen == "5G" { BAND_5G_600 } else { BAND_4G_600 })
    } else if (698.0..806.0).contains(&f) {
        ("700".to_string(), if gen == "5G" { BAND_5G_700 } else { BAND_4G_700 })
    } else if (806.0..902.0).contains(&f) {
        let m = match gen {
            "5G" => BAND_5G_850,
            "3G" => BAND_3G_900,
            "2G" => BAND_2G_900,
            _ => BAND_4G_800,
        };
        ("850".to_string(), m)
    } else if (1710.0..1785.0).contains(&f) || (2110.0..2200.0).contains(&f) {
        let m = match gen {
            "5G" => BAND_5G_2100,
            "3G" => BAND_3G_2100,
            _ => BAND_4G_2100,
        };
        ("2100 (AWS)".to_string(), m)
    } else if (1850.0..1998.0).contains(&f) {
        let m = match gen {
            "5G" => BAND_5G_1900,
            "3G" => BAND_3G_1900,
            "2G" => BAND_2G_1800,
            _ => BAND_4G_1800,
        };
        ("1900 (PCS)".to_string(), m)
    } else if (1998.0..2025.0).contains(&f) {
        ("2000 (AWS-4)".to_string(), if gen == "5G" { BAND_5G_2000 } else { BAND_4G_2100 })
    } else if (2300.0..2400.0).contains(&f) {
        ("2300 (WCS)".to_string(), BAND_4G_2300)
    } else if (2500.0..2700.0).contains(&f) {
        ("2600 (BRS)".to_string(), if gen == "5G" { BAND_5G_2600 } else { BAND_4G_2600 })
    } else if (3400.0..3650.0).contains(&f) {
        ("3500".to_string(), if gen == "5G" { BAND_5G_3500 } else { BAND_4G_3500 })
    } else if (3650.0..4000.0).contains(&f) {
        ("3800".to_string(), if gen == "5G" { BAND_5G_3800 } else { BAND_4G_3500 })
    } else if f >= 24000.0 {
        (format!("{}GHz", (f / 1000.0).round() as i32), BAND_5G_26000)
    } else {
        (format!("{}", f.round() as i32), 0)
    };
    (format!("{gen} {band_label}"), mask)
}

fn is_omni_antenna(
    horiz_bw: Option<f64>,
    omni_ind: &str,
    az_val: Option<i32>,
    ant_gain: Option<f64>,
    site_type: &str,
) -> bool {
    if horiz_bw.is_some_and(|w| w >= 300.0) {
        return true;
    }
    if omni_ind.trim().eq_ignore_ascii_case("O") {
        return true;
    }
    if az_val == Some(0)
        && (horiz_bw.is_none_or(|w| w <= 5.0)
            || site_type.trim().eq_ignore_ascii_case("I")
            || ant_gain.is_some_and(|g| g <= 6.0))
    {
        return true;
    }
    false
}

fn sha1_hex_upper(input: &str, len: usize) -> String {
    let mut hasher = Sha1::new();
    hasher.update(input.as_bytes());
    let res = hasher.finalize();
    let mut hex = String::with_capacity(len);
    for b in res {
        use std::fmt::Write;
        let _ = write!(hex, "{b:02X}");
        if hex.len() >= len {
            break;
        }
    }
    hex.truncate(len);
    hex
}

fn make_site_id(op: &str, lat: f64, lon: f64) -> String {
    let key = format!("{op}|{lat:.5}|{lon:.5}");
    format!("CA-{}", sha1_hex_upper(&key, 12))
}

fn make_support_id(lat: f64, lon: f64) -> String {
    let key = format!("{lat:.5}|{lon:.5}");
    format!("CA-SUP-{}", sha1_hex_upper(&key, 10))
}

struct CaAntenna {
    aer_id: String,
    tae_id: i32,
    azimut: Option<i32>,
    hauteur_bas: Option<f64>,
}

struct CaSite {
    id_anfr: String,
    id_support: String,
    operateur_id: i32,
    operator_name: String,
    province_code: String,
    latitude: f64,
    longitude: f64,
    adresse: Option<String>,
    nat_id: i32,
    hauteur: Option<f64>,
    date_modif: String,
    tech_mask: i32,
    band_mask: i32,
    tech_keys: BTreeSet<String>,
    band_keys: BTreeSet<String>,
    azimuths: BTreeSet<i32>,
    freq_lines: BTreeMap<(String, String), BTreeSet<String>>,
    azimuth_freqs: BTreeMap<String, BTreeSet<i32>>,
    antennas: HashMap<(Option<i32>, Option<i64>, i32), CaAntenna>,
    site_meta: Map<String, Value>,
}

struct CaCsvHeader {
    idx: HashMap<String, usize>,
}

impl CaCsvHeader {
    fn from_line(line: &str, delim: u8) -> Self {
        let mut rdr = csv::ReaderBuilder::new()
            .has_headers(false)
            .delimiter(delim)
            .flexible(true)
            .from_reader(line.as_bytes());
        let mut idx = HashMap::new();
        if let Some(Ok(rec)) = rdr.records().next() {
            for (i, f) in rec.iter().enumerate() {
                let norm = f.trim().trim_end_matches('*').trim().to_ascii_lowercase();
                idx.insert(norm, i);
            }
        }
        Self { idx }
    }

    fn get<'a>(&self, rec: &'a csv::StringRecord, name: &str) -> Option<&'a str> {
        self.idx.get(name).and_then(|&i| rec.get(i))
    }
}

fn stream_ca_csv<R: Read, F>(reader: R, mut on_row: F) -> BResult<()>
where
    F: FnMut(&CaCsvHeader, &csv::StringRecord) -> BResult<()>,
{
    let mut buf = BufReader::with_capacity(256 * 1024, reader);
    let mut header_bytes = Vec::new();
    if buf.read_until(b'\n', &mut header_bytes).map_err(|e| format!("{e}"))? == 0 {
        return Ok(());
    }
    let header_str = decode_bytes_lossy(&header_bytes);
    let delim = detect_delimiter(&header_str);
    let header = CaCsvHeader::from_line(&header_str, delim);

    let mut line_bytes = Vec::with_capacity(1024);
    let mut rec = csv::StringRecord::new();
    loop {
        line_bytes.clear();
        let n = buf.read_until(b'\n', &mut line_bytes).map_err(|e| format!("{e}"))?;
        if n == 0 {
            break;
        }
        while matches!(line_bytes.last(), Some(b'\n' | b'\r')) {
            line_bytes.pop();
        }
        if line_bytes.is_empty() {
            continue;
        }
        let line_str = decode_bytes_lossy(&line_bytes);
        if !line_str.contains('"') {
            rec.clear();
            for p in line_str.split(delim as char) {
                rec.push_field(p);
            }
            on_row(&header, &rec)?;
        } else {
            let mut rdr = csv::ReaderBuilder::new()
                .has_headers(false)
                .delimiter(delim)
                .flexible(true)
                .from_reader(line_str.as_bytes());
            if let Some(Ok(parsed)) = rdr.records().next() {
                on_row(&header, &parsed)?;
            }
        }
    }
    Ok(())
}

pub fn run_build_canada(req: &BuildRequest, sink: &mut dyn ProgressSink) -> BResult<()> {
    let zip_file = File::open(&req.data_zip_path).map_err(|e| format!("open CA zip: {e}"))?;
    let mut archive = ZipArchive::new(zip_file).map_err(|e| format!("CA zip: {e}"))?;

    let mut csv_names: Vec<String> = Vec::new();
    for i in 0..archive.len() {
        if let Ok(f) = archive.by_index_raw(i) {
            let name = f.name().to_string();
            let low = name.to_ascii_lowercase();
            if !f.is_dir() && (low.ends_with(".csv") || low.ends_with(".txt")) {
                csv_names.push(name);
            }
        }
    }
    if csv_names.is_empty() {
        return Err(crate::BuildErr("Aucun fichier CSV trouvé dans l'archive ISDE".to_string()));
    }

    let _ = std::fs::remove_file(&req.built_mobile_path);
    let _ = std::fs::remove_file(&req.staging_mobile_path);
    if let Some(parent) = Path::new(&req.built_mobile_path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let conn = Connection::open(&req.built_mobile_path)?;
    conn.execute_batch(&format!(
        "PRAGMA journal_mode = OFF;
         PRAGMA synchronous = OFF;
         PRAGMA temp_store = FILE;
         PRAGMA cache_size = -16384;
         ATTACH DATABASE '{}' AS stg;",
        req.staging_mobile_path
    ))?;
    for sql in MOBILE_CREATE_TABLES {
        conn.execute(sql, [])?;
    }
    conn.execute(CA_ISED_DETAILS_DDL, [])?;
    conn.execute(
        "CREATE TABLE stg.stg_ca_sector (site_id TEXT NOT NULL, dedup_key TEXT NOT NULL, sector_json TEXT NOT NULL, PRIMARY KEY (site_id, dedup_key))",
        [],
    )?;

    let mut sites: HashMap<String, CaSite> = HashMap::with_capacity(32_768);
    let mut operators_ref: HashMap<String, i32> = HashMap::new();
    let mut natures_ref: HashMap<String, i32> = HashMap::new();
    let mut ant_types_ref: HashMap<String, i32> = HashMap::new();
    let mut latest_upload_date = String::new();
    let mut scanned = 0i64;

    sink.emit(PHASE_READING_STATIONS, 45, Some("Site_Data_Extract_FX.zip"), 0);

    let tx_stg = conn.unchecked_transaction()?;
    let mut sec_ins = tx_stg.prepare_cached("INSERT OR IGNORE INTO stg_ca_sector VALUES (?1, ?2, ?3)")?;

    for name in csv_names {
        let entry = archive.by_name(&name).map_err(|e| format!("{e}"))?;
        stream_ca_csv(entry, |hdr, rec| {
            scanned += 1;
            if scanned % 100_000 == 0 {
                sink.check_paused_or_cancelled()?;
                sink.emit(PHASE_READING_STATIONS, 60, Some("Site_Data_Extract_FX.zip"), scanned);
            }

            let licensee_raw = clean_ca_text(hdr.get(rec, "licensee_name"));
            if licensee_raw.is_empty() {
                return Ok(());
            }
            let Some(op_name) = match_canadian_operator(&licensee_raw) else {
                return Ok(());
            };
            let raw_tech = clean_ca_text(hdr.get(rec, "technology"));
            let Some((gen_label, tech_bit)) = classify_technology(&raw_tech) else {
                return Ok(());
            };
            let Some(lat) = hdr.get(rec, "latitude").and_then(|s| s.trim().parse::<f64>().ok()) else {
                return Ok(());
            };
            let Some(lon) = hdr.get(rec, "longitude").and_then(|s| s.trim().parse::<f64>().ok()) else {
                return Ok(());
            };
            if !(41.0..=84.0).contains(&lat) || !(-142.0..=-52.0).contains(&lon) {
                return Ok(());
            }

            let site_id = make_site_id(op_name, lat, lon);
            let upload_date = clean_ca_text(hdr.get(rec, "upload_date"));
            if !upload_date.is_empty() && upload_date > latest_upload_date {
                latest_upload_date = upload_date.clone();
            }

            let op_id = if let Some(&id) = operators_ref.get(op_name) {
                id
            } else {
                let id = (operators_ref.len() as i32) + 1;
                operators_ref.insert(op_name.to_string(), id);
                id
            };

            let site = sites.entry(site_id.clone()).or_insert_with(|| {
                let support_id = make_support_id(lat, lon);
                let loc_str = clean_ca_text(hdr.get(rec, "location"));
                let prov_code = clean_ca_text(hdr.get(rec, "province_code"));
                let addr = match (loc_str.is_empty(), prov_code.is_empty()) {
                    (false, false) => Some(format!("{loc_str}, {prov_code}")),
                    (false, true) => Some(loc_str),
                    (true, false) => Some(prov_code.clone()),
                    (true, true) => None,
                };
                let struct_type = clean_ca_text(hdr.get(rec, "structure_type")).to_ascii_uppercase();
                let struct_label = match struct_type.as_str() {
                    "T" => "Pylône / Tour",
                    "R" => "Toit / Bâtiment",
                    "O" => "Intérieur / Autre",
                    "B" => "Bâtiment",
                    "P" => "Poteau",
                    "" => "Non renseigné",
                    other => other,
                }
                .to_string();
                let nat_id = if let Some(&id) = natures_ref.get(&struct_label) {
                    id
                } else {
                    let id = (natures_ref.len() as i32) + 1;
                    natures_ref.insert(struct_label, id);
                    id
                };

                let mut meta = Map::new();
                for &(k, col) in &[
                    ("record_id", "record_id"),
                    ("account_number", "account_number"),
                    ("licensee_name", "licensee_name"),
                    ("province_code", "province_code"),
                    ("site_type", "site_type"),
                    ("structure_type", "structure_type"),
                    ("area_id", "area_id"),
                    ("area_name", "area_name"),
                    ("upload_date", "upload_date"),
                ] {
                    let val = clean_ca_text(hdr.get(rec, col));
                    if !val.is_empty() {
                        meta.insert(k.to_string(), Value::String(val));
                    }
                }

                CaSite {
                    id_anfr: site_id.clone(),
                    id_support: support_id,
                    operateur_id: op_id,
                    operator_name: op_name.to_string(),
                    province_code: prov_code.to_ascii_uppercase(),
                    latitude: lat,
                    longitude: lon,
                    adresse: addr,
                    nat_id,
                    hauteur: hdr.get(rec, "structure_height").and_then(|s| s.trim().parse::<f64>().ok()),
                    date_modif: clean_ca_text(hdr.get(rec, "date_last_changed")),
                    tech_mask: 0,
                    band_mask: 0,
                    tech_keys: BTreeSet::new(),
                    band_keys: BTreeSet::new(),
                    azimuths: BTreeSet::new(),
                    freq_lines: BTreeMap::new(),
                    azimuth_freqs: BTreeMap::new(),
                    antennas: HashMap::new(),
                    site_meta: meta,
                }
            });

            let date_changed = clean_ca_text(hdr.get(rec, "date_last_changed"));
            if !date_changed.is_empty() && date_changed > site.date_modif {
                site.date_modif = date_changed.clone();
            }
            if let Some(sh) = hdr.get(rec, "structure_height").and_then(|s| s.trim().parse::<f64>().ok()) {
                if site.hauteur.is_none_or(|h| sh > h) {
                    site.hauteur = Some(sh);
                }
            }

            let tx_freq = hdr.get(rec, "tx_frequency").and_then(|s| s.trim().parse::<f64>().ok());
            let rx_freq = hdr.get(rec, "rx_frequency").and_then(|s| s.trim().parse::<f64>().ok());
            let (band_display, band_bit) = classify_band(tx_freq, gen_label);
            site.tech_mask |= tech_bit;
            site.band_mask |= band_bit;
            site.tech_keys.insert(gen_label.to_string());
            if let Some((_, rest)) = band_display.split_once(' ') {
                let bshort = rest.split(' ').next().unwrap_or("");
                if !bshort.is_empty() {
                    let key_freq = if let Some(ghz) = bshort.strip_suffix("GHz") {
                        format!("{}", ghz.parse::<i32>().unwrap_or(26) * 1000)
                    } else {
                        bshort.to_string()
                    };
                    site.band_keys.insert(format!("{gen_label}|{key_freq}"));
                }
            }

            let horiz_bw = hdr.get(rec, "tx_ant_horiz_beamwidth").and_then(|s| s.trim().parse::<f64>().ok());
            let az_val = hdr
                .get(rec, "tx_ant_azimuth")
                .and_then(|s| s.trim().parse::<f64>().ok())
                .map(|f| (f.round() as i32).rem_euclid(360));
            let ant_gain = hdr.get(rec, "tx_ant_gain").and_then(|s| s.trim().parse::<f64>().ok());
            let omni = is_omni_antenna(
                horiz_bw,
                hdr.get(rec, "tx_ant_omni_indicator").unwrap_or(""),
                az_val,
                ant_gain,
                &clean_ca_text(hdr.get(rec, "site_type")),
            );
            let ant_height = hdr.get(rec, "tx_ant_height").and_then(|s| s.trim().parse::<f64>().ok());

            let phys_str = if !omni {
                if let Some(az) = az_val {
                    site.azimuths.insert(az);
                    site.azimuth_freqs.entry(band_display.clone()).or_default().insert(az);
                    match ant_height {
                        Some(h) => format!("{az}° ({h:.1} m)"),
                        None => format!("{az}°"),
                    }
                } else {
                    match ant_height {
                        Some(h) => format!("Omni ({h:.1} m)"),
                        None => "Omni".to_string(),
                    }
                }
            } else {
                match ant_height {
                    Some(h) => format!("Omni ({h:.1} m)"),
                    None => "Omni".to_string(),
                }
            };
            site.freq_lines
                .entry((band_display, date_changed.clone()))
                .or_default()
                .insert(phys_str);

            let ant_mfg = clean_ca_text(hdr.get(rec, "tx_ant_manufacturer"));
            let ant_model = clean_ca_text(hdr.get(rec, "tx_ant_model_no"));
            let ant_label = match (ant_mfg.is_empty(), ant_model.is_empty()) {
                (false, false) => format!("{ant_mfg} {ant_model}"),
                (false, true) => ant_mfg.clone(),
                (true, false) => ant_model.clone(),
                (true, true) => "Antenne ISDE".to_string(),
            };
            let tae_id = if let Some(&id) = ant_types_ref.get(&ant_label) {
                id
            } else {
                let id = (ant_types_ref.len() as i32) + 1;
                ant_types_ref.insert(ant_label, id);
                id
            };

            let ant_az = if omni { None } else { az_val };
            let ant_h_key = ant_height.map(|h| (h * 10.0).round() as i64);
            let ant_dedup = (ant_az, ant_h_key, tae_id);
            if !site.antennas.contains_key(&ant_dedup) {
                let idx = site.antennas.len() + 1;
                site.antennas.insert(
                    ant_dedup,
                    CaAntenna {
                        aer_id: format!("{}-A{idx}", site.id_anfr),
                        tae_id,
                        azimut: ant_az,
                        hauteur_bas: ant_height,
                    },
                );
            }

            let cell_id = clean_ca_text(hdr.get(rec, "cell_id"));
            let physical_id = clean_ca_text(hdr.get(rec, "physical_id"));
            let tx_key = tx_freq.map(|f| (f * 1000.0).round() as i64).unwrap_or(-1);
            let az_key = ant_az.unwrap_or(-1);
            let dedup_key = format!("{raw_tech}|{tx_key}|{az_key}|{physical_id}|{cell_id}");
            {
                let mut sec = Map::new();
                for (k, v) in [
                    ("licence_number", clean_ca_text(hdr.get(rec, "licence_number"))),
                    ("licence_category", clean_ca_text(hdr.get(rec, "licence_category"))),
                    ("subservice_id", clean_ca_text(hdr.get(rec, "subservice_id"))),
                    ("station_type", clean_ca_text(hdr.get(rec, "station_type"))),
                    ("technology", raw_tech),
                    ("cell_id", cell_id),
                    ("physical_id", physical_id),
                    ("class_emission", clean_ca_text(hdr.get(rec, "class_emission"))),
                    ("tx_ant_type", clean_ca_text(hdr.get(rec, "tx_ant_type"))),
                    ("tx_ant_model_no", ant_model),
                    ("tx_ant_manufacturer", ant_mfg),
                    ("tx_radio_model_number", clean_ca_text(hdr.get(rec, "tx_radio_model_number"))),
                    ("tx_radio_manufacturer_code", clean_ca_text(hdr.get(rec, "tx_radio_manufacturer_code"))),
                    ("date_last_changed", date_changed),
                ] {
                    if !v.is_empty() {
                        sec.insert(k.to_string(), Value::String(v));
                    }
                }
                if let Some(v) = tx_freq { sec.insert("tx_frequency".into(), Value::from(v)); }
                if let Some(v) = rx_freq { sec.insert("rx_frequency".into(), Value::from(v)); }
                if let Some(v) = hdr.get(rec, "bandwidth").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("bandwidth".into(), Value::from(v));
                }
                if let Some(v) = hdr.get(rec, "tx_power").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("tx_power".into(), Value::from(v));
                }
                if let Some(v) = hdr.get(rec, "downlink_allocation").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("downlink_allocation".into(), Value::from(v));
                }
                if let Some(v) = hdr.get(rec, "tx_number_antennas").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("tx_number_antennas".into(), Value::from(v.round() as i64));
                }
                if let Some(v) = hdr.get(rec, "rx_number_antennas").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("rx_number_antennas".into(), Value::from(v.round() as i64));
                }
                if let Some(v) = ant_height { sec.insert("tx_ant_height".into(), Value::from(v)); }
                if let Some(v) = horiz_bw { sec.insert("tx_ant_horiz_beamwidth".into(), Value::from(v)); }
                if let Some(v) = hdr.get(rec, "tx_ant_vert_beamwidth").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("tx_ant_vert_beamwidth".into(), Value::from(v));
                }
                if let Some(v) = ant_az { sec.insert("tx_ant_azimuth".into(), Value::from(v)); }
                if let Some(v) = hdr.get(rec, "tx_ant_elevation_angle").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("tx_ant_elevation_angle".into(), Value::from(v));
                }
                if let Some(v) = ant_gain { sec.insert("tx_ant_gain".into(), Value::from(v)); }
                if let Some(v) = hdr.get(rec, "tx_line_loss").and_then(|s| s.trim().parse::<f64>().ok()) {
                    sec.insert("tx_line_loss".into(), Value::from(v));
                }
                let sec_json = Value::Object(sec).to_string();
                let _ = sec_ins.execute(params![&site_id, &dedup_key, &sec_json]);
            }
            Ok(())
        })?;
    }
    drop(sec_ins);
    tx_stg.commit()?;

    sink.emit(PHASE_INSERTING, 82, Some("tables finales CA"), sites.len() as i64);
    {
        let tx = conn.unchecked_transaction()?;
        {
            let mut s = tx.prepare("INSERT INTO ref_operateur (id, libelle) VALUES (?1, ?2)")?;
            for (lbl, id) in &operators_ref {
                s.execute(params![id, lbl])?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_nature (nat_id, libelle) VALUES (?1, ?2)")?;
            for (lbl, id) in &natures_ref {
                s.execute(params![id, lbl])?;
            }
        }
        {
            let mut s = tx.prepare("INSERT INTO ref_type_antenne (tae_id, libelle) VALUES (?1, ?2)")?;
            for (lbl, id) in &ant_types_ref {
                s.execute(params![id, lbl])?;
            }
        }
        tx.execute("INSERT INTO ref_statut (id, libelle) VALUES (1, 'En service')", [])?;

        {
            let mut loc_s = tx.prepare_cached("INSERT INTO localisation VALUES (?1, ?2, ?3, ?4, ?5, NULL, NULL, ?6, ?7, NULL, 0)")?;
            let mut tech_s = tx.prepare_cached("INSERT INTO technique VALUES (?1, NULL, 1, NULL, NULL, ?2, ?3, ?4, 1, ?5)")?;
            let mut sup_s = tx.prepare_cached("INSERT INTO support VALUES (?1, ?2, ?3, NULL, ?4)")?;
            let mut ant_s = tx.prepare_cached("INSERT INTO antenne VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0)")?;

            for site in sites.values() {
                let az_str = if site.azimuths.is_empty() {
                    None
                } else {
                    Some(site.azimuths.iter().map(|a| a.to_string()).collect::<Vec<_>>().join(","))
                };
                let mut freq_lines = Vec::with_capacity(site.freq_lines.len());
                for ((band_disp, dt), phys) in &site.freq_lines {
                    let p_str = phys.iter().cloned().collect::<Vec<_>>().join(", ");
                    freq_lines.push(format!("{band_disp} | En service | {dt} | {p_str}"));
                }
                let details_freq = encode_frequency_details(&freq_lines.join("\n"));

                let mut az_freq_lines = Vec::new();
                for (band_disp, azs) in &site.azimuth_freqs {
                    if !azs.is_empty() {
                        let list = azs.iter().map(|a| a.to_string()).collect::<Vec<_>>().join(",");
                        az_freq_lines.push(format!("{band_disp}={list}"));
                    }
                }
                let details_az_freq = if az_freq_lines.is_empty() {
                    None
                } else {
                    Some(az_freq_lines.join(";"))
                };

                loc_s.execute(params![
                    &site.id_anfr,
                    site.operateur_id,
                    site.latitude,
                    site.longitude,
                    az_str,
                    site.tech_mask,
                    site.band_mask
                ])?;
                let dm = if site.date_modif.is_empty() { None } else { Some(site.date_modif.as_str()) };
                tech_s.execute(params![&site.id_anfr, dm, details_freq, &site.adresse, details_az_freq])?;
                sup_s.execute(params![&site.id_anfr, &site.id_support, site.nat_id, site.hauteur])?;
                for ant in site.antennas.values() {
                    ant_s.execute(params![
                        &ant.aer_id,
                        &site.id_anfr,
                        &site.id_support,
                        ant.tae_id,
                        ant.azimut,
                        ant.hauteur_bas
                    ])?;
                }
            }
        }
        tx.commit()?;
    }

    sink.emit(PHASE_BUILDING_DETAILS, 86, Some("ca_ised_site_details"), sites.len() as i64);
    {
        let tx = conn.unchecked_transaction()?;
        {
            let mut det_s = tx.prepare_cached("INSERT INTO ca_ised_site_details (id_anfr, payload) VALUES (?1, ?2)")?;
            let mut q = conn.prepare("SELECT site_id, sector_json FROM stg_ca_sector ORDER BY site_id")?;
            let mut rows = q.query([])?;
            let mut current_id: Option<String> = None;
            let mut sectors_buf: Vec<String> = Vec::new();

            let flush_site = |cid: Option<String>, sbuf: &mut Vec<String>, sites_map: &HashMap<String, CaSite>, det_s: &mut rusqlite::Statement<'_>| -> BResult<()> {
                let Some(sid) = cid else { return Ok(()) };
                if let Some(site) = sites_map.get(&sid) {
                    let meta_json = Value::Object(site.site_meta.clone()).to_string();
                    let prefix = meta_json.strip_suffix('}').unwrap_or("{");
                    let mut payload = String::with_capacity(meta_json.len() + sbuf.len() * 256);
                    payload.push_str(prefix);
                    if prefix.len() > 1 {
                        payload.push(',');
                    }
                    payload.push_str("\"sectors\":[");
                    for (idx, sj) in sbuf.iter().enumerate() {
                        if idx > 0 {
                            payload.push(',');
                        }
                        payload.push_str(sj);
                    }
                    payload.push_str("]}");
                    if let Some(enc) = encode_frequency_details(&payload) {
                        det_s.execute(params![&sid, enc])?;
                    }
                }
                sbuf.clear();
                Ok(())
            };

            while let Some(row) = rows.next()? {
                let sid: String = row.get(0)?;
                let sj: String = row.get(1)?;
                if current_id.as_deref() != Some(&sid) {
                    flush_site(current_id.take(), &mut sectors_buf, &sites, &mut det_s)?;
                    current_id = Some(sid);
                }
                sectors_buf.push(sj);
            }
            flush_site(current_id, &mut sectors_buf, &sites, &mut det_s)?;
        }
        tx.commit()?;
    }

    let _ = conn.execute("DROP TABLE IF EXISTS stg.stg_ca_sector", []);

    sink.emit(PHASE_COMPUTING_STATS, 90, Some("stats CA"), sites.len() as i64);
    populate_ca_stats(&conn, &sites)?;

    let date_maj = if latest_upload_date.is_empty() { None } else { Some(latest_upload_date.as_str()) };
    conn.execute(
        "INSERT INTO metadata VALUES (?1, ?2, 'CA', 'Canada', 'ISED', ?3, 'Site_Data_Extract_FX.zip')",
        params![&req.version, MOBILE_SCHEMA_VERSION, date_maj],
    )?;
    conn.execute(
        "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES ('ised_upload_date', ?1)",
        params![date_maj.unwrap_or(&req.version)],
    )?;
    conn.execute(
        "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES ('provenance', 'local_build')",
        [],
    )?;

    sink.emit(PHASE_FINALIZING, 93, Some("finalisation CA"), 0);
    conn.execute(INSERT_ROOM_IDENTITY, [])?;
    conn.execute_batch(&format!(
        "PRAGMA user_version = {MOBILE_SCHEMA_VERSION};
         PRAGMA journal_mode = DELETE;"
    ))?;
    drop(conn);
    let _ = std::fs::remove_file(&req.staging_mobile_path);
    Ok(())
}

fn populate_ca_stats(conn: &Connection, sites: &HashMap<String, CaSite>) -> BResult<()> {
    let mut radio_counts: BTreeMap<(String, String, String), i32> = BTreeMap::new();
    let mut dept_agg: BTreeMap<String, (i32, i32)> = BTreeMap::new();
    let mut dept_op_tech: BTreeMap<(String, String, String), (i32, i32)> = BTreeMap::new();

    for site in sites.values() {
        let op_name = site.operator_name.to_ascii_uppercase();
        let ant_count = site.antennas.len() as i32;
        *radio_counts.entry((op_name.clone(), "support".into(), "ALL".into())).or_insert(0) += 1;
        for tk in &site.tech_keys {
            *radio_counts.entry((op_name.clone(), "tech".into(), tk.clone())).or_insert(0) += 1;
        }
        for bk in &site.band_keys {
            *radio_counts.entry((op_name.clone(), "band".into(), bk.clone())).or_insert(0) += 1;
        }
        if !site.province_code.is_empty() {
            let e = dept_agg.entry(site.province_code.clone()).or_insert((0, 0));
            e.0 += 1;
            e.1 += ant_count;
            let e_all = dept_op_tech.entry((site.province_code.clone(), op_name.clone(), "ALL".into())).or_insert((0, 0));
            e_all.0 += 1;
            e_all.1 += ant_count;
            for tk in &site.tech_keys {
                let e_t = dept_op_tech.entry((site.province_code.clone(), op_name.clone(), tk.clone())).or_insert((0, 0));
                e_t.0 += 1;
                e_t.1 += ant_count;
            }
        }
    }

    let tx = conn.unchecked_transaction()?;
    {
        let mut s = tx.prepare_cached("INSERT OR REPLACE INTO radio_stat_current VALUES (?1, ?2, ?3, ?4, ?5, ?5)")?;
        for ((op, cat, item), count) in radio_counts {
            let lbl = if cat == "support" {
                "Supports".to_string()
            } else if cat == "band" {
                if let Some((g, f)) = item.split_once('|') {
                    format!("{g} {f} MHz")
                } else {
                    item.clone()
                }
            } else {
                item.clone()
            };
            s.execute(params![op, cat, item, lbl, count])?;
        }
    }
    {
        let mut s = tx.prepare_cached(
            "INSERT OR REPLACE INTO dept_stat_current VALUES (?1, ?2, 'CA', ?3, ?4, ?5, ?6, ?6, ?6, ?6, ?7, ?7, 0, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18)",
        )?;
        for (prov, (sup, ant)) in &dept_agg {
            let ref_row = ca_province_ref(prov);
            let name = ref_row.map_or(prov.as_str(), |r| r.0);
            let area = ref_row.map(|r| r.1);
            let pop = ref_row.map(|r| r.2);
            let pop_yr = ref_row.map(|r| r.3);
            let sf = *sup as f64;
            let af = *ant as f64;
            let r_st_sup = if *sup > 0 { Some((sf / sf * 1000.0).round() / 1000.0) } else { None };
            let r_ant_st = if *sup > 0 { Some((af / sf * 1000.0).round() / 1000.0) } else { None };
            let r_area = |v: f64| area.filter(|&a| a > 0.0).map(|a| (v / a * 100_000.0).round() / 100_000.0);
            let r_1k = |v: f64| pop.filter(|&p| p > 0).map(|p| (v * 1000.0 / (p as f64) * 10_000.0).round() / 10_000.0);
            let r_hab = |v: f64| pop.filter(|_| v > 0.0).map(|p| ((p as f64) / v * 10.0).round() / 10.0);
            s.execute(params![
                prov, name, area, pop, pop_yr, sup, ant, r_st_sup, r_ant_st,
                r_area(sf), r_area(sf), r_area(af),
                r_1k(sf), r_1k(sf), r_1k(af),
                r_hab(sf), r_hab(sf), r_hab(af),
            ])?;
        }
    }
    {
        let mut s = tx.prepare_cached("INSERT OR REPLACE INTO dept_stat_operator_tech VALUES (?1, ?2, ?3, ?4, ?4, ?4, ?4, ?5, ?5)")?;
        let mut all_agg: BTreeMap<(String, String), (i32, i32)> = BTreeMap::new();
        for ((prov, op, tech), &(sup, ant)) in &dept_op_tech {
            s.execute(params![prov, op, tech, sup, ant])?;
            let e = all_agg.entry((prov.clone(), tech.clone())).or_insert((0, 0));
            e.0 += sup;
            e.1 += ant;
        }
        for ((prov, tech), (sup, ant)) in all_agg {
            s.execute(params![prov, "ALL", tech, sup, ant])?;
        }
    }
    tx.commit()?;
    Ok(())
}
