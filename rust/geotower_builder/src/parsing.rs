use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use flate2::write::ZlibEncoder;
use flate2::Compression;
use std::collections::HashMap;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::Path;
use zip::ZipArchive;

pub const TECH_2G: i32 = 1 << 0;
pub const TECH_3G: i32 = 1 << 1;
pub const TECH_4G: i32 = 1 << 2;
pub const TECH_5G: i32 = 1 << 3;
pub const TECH_FH: i32 = 1 << 4;

pub const BAND_2G_900: i32 = 1 << 0;
pub const BAND_2G_1800: i32 = 1 << 1;
pub const BAND_3G_900: i32 = 1 << 2;
pub const BAND_3G_2100: i32 = 1 << 3;
pub const BAND_4G_700: i32 = 1 << 4;
pub const BAND_4G_800: i32 = 1 << 5;
pub const BAND_4G_900: i32 = 1 << 6;
pub const BAND_4G_1800: i32 = 1 << 7;
pub const BAND_4G_2100: i32 = 1 << 8;
pub const BAND_4G_2600: i32 = 1 << 9;
pub const BAND_5G_700: i32 = 1 << 10;
pub const BAND_5G_2100: i32 = 1 << 11;
pub const BAND_5G_3500: i32 = 1 << 12;
pub const BAND_5G_26000: i32 = 1 << 13;
pub const BAND_FH: i32 = 1 << 14;
pub const BAND_5G_1400: i32 = 1 << 21;
pub const BAND_5G_4200: i32 = 1 << 22;
pub const BAND_3G_1900: i32 = 1 << 15;
pub const BAND_4G_600: i32 = 1 << 16;
pub const BAND_4G_2300: i32 = 1 << 17;
pub const BAND_4G_3500: i32 = 1 << 18;
pub const BAND_5G_600: i32 = 1 << 19;
pub const BAND_5G_850: i32 = 1 << 20;
pub const BAND_5G_1900: i32 = 1 << 23;
pub const BAND_5G_2000: i32 = 1 << 24;
pub const BAND_5G_2600: i32 = 1 << 25;
pub const BAND_5G_3800: i32 = 1 << 26;

#[inline]
pub fn clean_text(s: Option<&str>) -> &str {
    match s {
        Some(v) => v.trim(),
        None => "",
    }
}

pub fn normalize_id_anfr(value: Option<&str>) -> String {
    let v = clean_text(value);
    if !v.is_empty() && v.bytes().all(|b| b.is_ascii_digit()) {
        if v.len() >= 10 {
            v.to_string()
        } else {
            format!("{v:0>10}")
        }
    } else {
        v.to_string()
    }
}

fn decimal_to_f64(v: &str) -> Option<f64> {
    if v.contains(',') {
        v.replace(',', ".").parse::<f64>().ok()
    } else {
        v.parse::<f64>().ok()
    }
}

pub fn int_or_none(value: Option<&str>) -> Option<i32> {
    let v = clean_text(value);
    if v.is_empty() || v.eq_ignore_ascii_case("N/A") {
        return None;
    }
    decimal_to_f64(v).map(|f| f as i32)
}

pub fn float_or_none(value: Option<&str>) -> Option<f64> {
    let v = clean_text(value);
    if v.is_empty() || v.eq_ignore_ascii_case("N/A") {
        return None;
    }
    decimal_to_f64(v)
}

pub fn antenna_dimension_text(value: Option<&str>) -> Option<String> {
    let v = clean_text(value);
    let num = float_or_none(Some(v))?;
    if num <= 0.0 {
        return None;
    }
    Some(format!("{v}m"))
}

pub fn parse_coordinates(raw: Option<&str>) -> (f64, f64) {
    let s = clean_text(raw);
    let mut nums = [0.0f64; 2];
    let mut found = 0usize;
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() && found < 2 {
        let b = bytes[i];
        if b == b'-' || b == b'+' || b.is_ascii_digit() || b == b'.' || b == b',' {
            let start = i;
            if b == b'-' || b == b'+' {
                i += 1;
            }
            let mut has_digit = false;
            while i < bytes.len() {
                let c = bytes[i];
                if c.is_ascii_digit() {
                    has_digit = true;
                    i += 1;
                } else if c == b'.' || c == b',' {
                    i += 1;
                } else {
                    break;
                }
            }
            if has_digit {
                if let Some(v) = decimal_to_f64(&s[start..i]) {
                    nums[found] = v;
                    found += 1;
                }
            }
        } else {
            i += 1;
        }
    }
    if found < 2 {
        (0.0, 0.0)
    } else {
        (nums[0], nums[1])
    }
}

#[inline]
pub fn unit_initial(unit: Option<&str>) -> u8 {
    let u = clean_text(unit);
    u.bytes()
        .next()
        .map(|b| b.to_ascii_uppercase())
        .unwrap_or(b'M')
}

pub fn frequency_to_mhz(number: Option<f64>, unit: Option<&str>) -> Option<f64> {
    let n = number?;
    Some(match unit_initial(unit) {
        b'G' => n * 1000.0,
        b'K' => n / 1000.0,
        b'H' => n / 1_000_000.0,
        _ => n,
    })
}

pub fn format_band_range(f_deb: &str, f_fin: &str, unite: &str) -> String {
    let suffix = match unit_initial(Some(unite)) {
        b'G' => "GHz",
        b'M' => "MHz",
        b'K' => "kHz",
        b'H' => "Hz",
        _ => return format!("{f_deb}-{f_fin} {unite}Hz"),
    };
    format!("{f_deb}-{f_fin} {suffix}")
}

#[inline]
fn range_overlaps(start: Option<f64>, end: Option<f64>, low: f64, high: f64) -> bool {
    match (start, end) {
        (Some(s), Some(e)) => {
            let lo = s.min(e);
            let hi = s.max(e);
            lo <= high && hi >= low
        }
        _ => false,
    }
}

pub fn is_active_status(status: Option<&str>) -> bool {
    let s = match status {
        Some(v) => v,
        None => return false,
    };
    let upper = s.to_ascii_uppercase();
    upper.contains("EN SERVICE") || upper.contains("TECHNIQUEMENT OP")
}

pub fn update_masks_from_generation(tech_mask: &mut i32, generation: Option<&str>) {
    let g = clean_text(generation).to_ascii_uppercase();
    if g.contains("2G") {
        *tech_mask |= TECH_2G;
    }
    if g.contains("3G") {
        *tech_mask |= TECH_3G;
    }
    if g.contains("4G") {
        *tech_mask |= TECH_4G;
    }
    if g.contains("5G") {
        *tech_mask |= TECH_5G;
    }
}

pub fn update_masks_from_system_and_band(
    tech_mask: &mut i32,
    band_mask: &mut i32,
    system: Option<&str>,
    f_start_mhz: Option<f64>,
    f_end_mhz: Option<f64>,
) {
    let s = clean_text(system).to_ascii_uppercase();
    if s.contains("FH") {
        *tech_mask |= TECH_FH;
        *band_mask |= BAND_FH;
        return;
    }
    let is_2g = s.contains("GSM") || s.contains("2G");
    let is_3g = s.contains("UMTS") || s.contains("3G");
    let is_4g = s.contains("LTE") || s.contains("4G");
    let is_5g = s.contains("NR") || s.contains("5G");

    if is_2g {
        *tech_mask |= TECH_2G;
        if range_overlaps(f_start_mhz, f_end_mhz, 880.0, 960.0) {
            *band_mask |= BAND_2G_900;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 1710.0, 1880.0) {
            *band_mask |= BAND_2G_1800;
        }
    }
    if is_3g {
        *tech_mask |= TECH_3G;
        if range_overlaps(f_start_mhz, f_end_mhz, 880.0, 960.0) {
            *band_mask |= BAND_3G_900;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 1920.0, 2170.0) {
            *band_mask |= BAND_3G_2100;
        }
    }
    if is_4g {
        *tech_mask |= TECH_4G;
        if range_overlaps(f_start_mhz, f_end_mhz, 700.0, 790.0) {
            *band_mask |= BAND_4G_700;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 791.0, 862.0) {
            *band_mask |= BAND_4G_800;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 880.0, 960.0) {
            *band_mask |= BAND_4G_900;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 1710.0, 1880.0) {
            *band_mask |= BAND_4G_1800;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 1920.0, 2170.0) {
            *band_mask |= BAND_4G_2100;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 2500.0, 2690.0) {
            *band_mask |= BAND_4G_2600;
        }
    }
    if is_5g {
        *tech_mask |= TECH_5G;
        if range_overlaps(f_start_mhz, f_end_mhz, 700.0, 790.0) {
            *band_mask |= BAND_5G_700;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 1427.0, 1518.0) {
            *band_mask |= BAND_5G_1400;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 1920.0, 2170.0) {
            *band_mask |= BAND_5G_2100;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 3300.0, 3800.0) {
            *band_mask |= BAND_5G_3500;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 3800.1, 4200.0) {
            *band_mask |= BAND_5G_4200;
        }
        if range_overlaps(f_start_mhz, f_end_mhz, 24000.0, 27500.0) {
            *band_mask |= BAND_5G_26000;
        }
    }
}

pub fn encode_frequency_details(value: &str) -> Option<String> {
    if value.is_empty() {
        return None;
    }
    let mut encoder = ZlibEncoder::new(Vec::with_capacity(value.len() / 2), Compression::fast());
    if encoder.write_all(value.as_bytes()).is_err() {
        return Some(value.to_string());
    }
    let compressed = match encoder.finish() {
        Ok(c) => c,
        Err(_) => return Some(value.to_string()),
    };
    let b64 = STANDARD.encode(&compressed);
    let encoded_len = 3 + b64.len();
    if encoded_len < value.len() {
        Some(format!("Z1:{b64}"))
    } else {
        Some(value.to_string())
    }
}

pub fn normalize_azimuth(v: i32) -> Option<i32> {
    match v {
        0..=359 => Some(v),
        360 => Some(0),
        _ => None,
    }
}

pub fn parse_azimuth_list(value: Option<&str>) -> Vec<i32> {
    let s = clean_text(value);
    if s.is_empty() {
        return Vec::new();
    }
    let mut out: Vec<i32> = s
        .split('|')
        .filter_map(|tok| tok.trim().parse::<i32>().ok().and_then(normalize_azimuth))
        .collect();
    out.sort_unstable();
    out.dedup();
    out
}

pub fn dms_to_e6(deg: Option<&str>, minute: Option<&str>, second: Option<&str>, dir: Option<&str>) -> Option<i32> {
    let d = float_or_none(deg)?;
    let m = float_or_none(minute).unwrap_or(0.0);
    let s = float_or_none(second).unwrap_or(0.0);
    let mut val = d + m / 60.0 + s / 3600.0;
    let d_up = clean_text(dir).to_ascii_uppercase();
    if d_up == "S" || d_up == "W" {
        val = -val;
    }
    Some((val * 1_000_000.0).round() as i32)
}

pub fn frequency_to_khz(value: Option<&str>, unit: Option<&str>) -> Option<i32> {
    let n = float_or_none(value)?;
    let u = unit_initial(unit);
    let res = match u {
        b'G' => (n * 1_000_000.0).round(),
        b'M' => (n * 1_000.0).round(),
        b'K' => n.round(),
        b'H' => (n / 1_000.0).round(),
        _ => (n * 1_000.0).round(),
    };
    Some(res as i32)
}

pub fn format_freq_range(start_raw: &str, end_raw: &str, unit: &str) -> String {
    let suffix = match unit_initial(Some(unit)) {
        b'G' => "GHz",
        b'M' => "MHz",
        b'K' => "kHz",
        b'H' => "Hz",
        _ => return format!("{start_raw}-{end_raw} {unit}Hz"),
    };
    format!("{start_raw}-{end_raw} {suffix}")
}

/// Decodes a byte slice as UTF-8, falling back to Windows-1252/Latin-1 if invalid UTF-8 is found.
pub fn decode_bytes_lossy(bytes: &[u8]) -> String {
    match std::str::from_utf8(bytes) {
        Ok(s) => s.strip_prefix('\u{FEFF}').unwrap_or(s).to_string(),
        Err(_) => {
            let mut out = String::with_capacity(bytes.len());
            for &b in bytes {
                if b < 0x80 {
                    out.push(b as char);
                } else {
                    out.push(cp1252_char(b));
                }
            }
            out
        }
    }
}

fn cp1252_char(b: u8) -> char {
    match b {
        0x80 => '€', 0x82 => '‚', 0x83 => 'ƒ', 0x84 => '„', 0x85 => '…', 0x86 => '†',
        0x87 => '‡', 0x88 => 'ˆ', 0x89 => '‰', 0x8A => 'Š', 0x8B => '‹', 0x8C => 'Œ',
        0x8E => 'Ž', 0x91 => '‘', 0x92 => '’', 0x93 => '“', 0x94 => '”', 0x95 => '•',
        0x96 => '–', 0x97 => '—', 0x98 => '˜', 0x99 => '™', 0x9A => 'š', 0x9B => '›',
        0x9C => 'œ', 0x9E => 'ž', 0x9F => 'Ÿ',
        _ => b as char,
    }
}

pub fn detect_delimiter(header: &str) -> u8 {
    let semicolons = header.bytes().filter(|&b| b == b';').count();
    let commas = header.bytes().filter(|&b| b == b',').count();
    let tabs = header.bytes().filter(|&b| b == b'\t').count();
    if tabs > semicolons && tabs > commas {
        b'\t'
    } else if commas > semicolons {
        b','
    } else {
        b';'
    }
}

pub struct CsvTable {
    col_map: HashMap<String, usize>,
}

impl CsvTable {
    pub fn from_header(header_line: &str, delim: u8) -> Self {
        let mut rdr = csv::ReaderBuilder::new()
            .has_headers(false)
            .delimiter(delim)
            .flexible(true)
            .from_reader(header_line.as_bytes());
        let mut col_map = HashMap::new();
        if let Some(Ok(rec)) = rdr.records().next() {
            for (idx, field) in rec.iter().enumerate() {
                col_map.insert(field.trim().to_ascii_lowercase(), idx);
            }
        }
        Self { col_map }
    }

    #[inline]
    pub fn idx(&self, name: &str) -> Option<usize> {
        self.col_map.get(name).copied()
    }
}

pub fn stream_csv_reader<R: Read, F>(reader: R, mut on_row: F) -> Result<(), String>
where
    F: FnMut(&CsvTable, &csv::StringRecord) -> Result<(), String>,
{
    let mut buf = BufReader::with_capacity(128 * 1024, reader);
    let mut header_bytes = Vec::new();
    let n = buf
        .read_until(b'\n', &mut header_bytes)
        .map_err(|e| format!("CSV header read error: {e}"))?;
    if n == 0 {
        return Ok(());
    }
    let header_str = decode_bytes_lossy(&header_bytes);
    let delim = detect_delimiter(&header_str);
    let table = CsvTable::from_header(&header_str, delim);

    let mut line_bytes = Vec::with_capacity(512);
    let mut rec = csv::StringRecord::new();
    loop {
        line_bytes.clear();
        let bytes_read = buf
            .read_until(b'\n', &mut line_bytes)
            .map_err(|e| format!("CSV line read error: {e}"))?;
        if bytes_read == 0 {
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
            for part in line_str.split(delim as char) {
                rec.push_field(part);
            }
            on_row(&table, &rec)?;
        } else {
            let mut rdr = csv::ReaderBuilder::new()
                .has_headers(false)
                .delimiter(delim)
                .flexible(true)
                .from_reader(line_str.as_bytes());
            if let Some(Ok(parsed)) = rdr.records().next() {
                on_row(&table, &parsed)?;
            }
        }
    }
    Ok(())
}

pub fn find_zip_entry_name(archive: &mut ZipArchive<std::fs::File>, target: &str) -> Option<String> {
    let target_lower = target.to_ascii_lowercase();
    for i in 0..archive.len() {
        if let Ok(file) = archive.by_index_raw(i) {
            let name = file.name().to_string();
            let base = name.rsplit('/').next().unwrap_or(&name).to_ascii_lowercase();
            if base == target_lower {
                return Some(name);
            }
        }
    }
    None
}

pub fn read_reference_map(ref_zip_path: &Path, filename: &str, id_col: &str, label_col: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    let file = match std::fs::File::open(ref_zip_path) {
        Ok(f) => f,
        Err(_) => return map,
    };
    let mut archive = match ZipArchive::new(file) {
        Ok(a) => a,
        Err(_) => return map,
    };
    let entry_name = match find_zip_entry_name(&mut archive, filename) {
        Some(n) => n,
        None => return map,
    };
    let entry = match archive.by_name(&entry_name) {
        Ok(e) => e,
        Err(_) => return map,
    };
    let _ = stream_csv_reader(entry, |table, rec| {
        let id_idx = table.idx(id_col);
        let lbl_idx = table.idx(label_col);
        if let (Some(ii), Some(li)) = (id_idx, lbl_idx) {
            let id = clean_text(rec.get(ii));
            let lbl = clean_text(rec.get(li));
            if !id.is_empty() && !lbl.is_empty() {
                map.insert(id.to_string(), lbl.to_string());
            }
        }
        Ok(())
    });
    map
}
