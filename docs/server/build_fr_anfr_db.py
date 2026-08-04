import csv
import base64
import glob
import io
import json
import os
import re
import sqlite3
import urllib.error
import urllib.request
import zipfile
import zlib
from collections import defaultdict
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from fr_anfr_stats import (
    FRANCE_SOURCES_DIR_NAME,
    discover_weekly_csv_files,
    ensure_stats_tables,
    open_csv,
    csv_sources_from_file,
    parse_source_date,
    populate_current_stats,
    populate_weekly_stats,
)
from fr_dept_stats import (
    ensure_dept_stat_tables,
    load_department_reference,
    populate_department_stats,
    update_source_versions as update_dept_source_versions,
)


DATA_DIR = "/opt/geotower/data/imports/"
REF_DIR = "/opt/geotower/data/references/"
DB_FILENAME = "geotower_fr.db"
LEGACY_DB_FILENAME = "geotower.db"
VERSION_FILENAME = "version_fr.json"
DB_FILE = os.path.join(DATA_DIR, DB_FILENAME)
LEGACY_DB_FILE = os.path.join(DATA_DIR, LEGACY_DB_FILENAME)

TXT_STATION = "SUP_STATION.txt"
TXT_SUPPORT = "SUP_SUPPORT.txt"
TXT_ANTENNE = "SUP_ANTENNE.txt"
TXT_EMETTEUR = "SUP_EMETTEUR.txt"
TXT_BANDE = "SUP_BANDE.txt"

SCHEMA_VERSION = 7
COUNTRY_CODE = "FR"
COUNTRY_NAME = "France"
SOURCE = "ANFR"
ROOM_IDENTITY_HASH = "f92129b45cc37b357c5ecb8e0ba597f0"

TECH_2G = 1 << 0
TECH_3G = 1 << 1
TECH_4G = 1 << 2
TECH_5G = 1 << 3
TECH_FH = 1 << 4

BAND_2G_900 = 1 << 0
BAND_2G_1800 = 1 << 1
BAND_3G_900 = 1 << 2
BAND_3G_2100 = 1 << 3
BAND_4G_700 = 1 << 4
BAND_4G_800 = 1 << 5
BAND_4G_900 = 1 << 6
BAND_4G_1800 = 1 << 7
BAND_4G_2100 = 1 << 8
BAND_4G_2600 = 1 << 9
BAND_5G_700 = 1 << 10
BAND_5G_2100 = 1 << 11
BAND_5G_3500 = 1 << 12
BAND_5G_26000 = 1 << 13
BAND_FH = 1 << 14
BAND_5G_1400 = 1 << 21
BAND_5G_4200 = 1 << 22

cache_villes = {}


class IdRegistry:
    def __init__(self):
        self._ids = {}
        self._labels = {}
        self._next_id = 1

    def get_id(self, label, default="Inconnu"):
        value = clean_text(label) or default
        key = value.upper()
        if key not in self._ids:
            self._ids[key] = self._next_id
            self._labels[self._next_id] = value
            self._next_id += 1
        return self._ids[key]

    def rows(self):
        return sorted(self._labels.items())

    def get_label(self, id_value, default="Inconnu"):
        return self._labels.get(id_value, default)


def clean_text(value):
    return str(value).strip() if value is not None else ""


def normalize_id_anfr(value):
    value = clean_text(value)
    return value.zfill(10) if value.isdigit() else value


def int_or_none(value):
    value = clean_text(value)
    if not value or value.upper() == "N/A":
        return None
    try:
        return int(float(value.replace(",", ".")))
    except ValueError:
        return None


def float_or_none(value):
    value = clean_text(value)
    if not value or value.upper() == "N/A":
        return None
    try:
        return float(value.replace(",", "."))
    except ValueError:
        return None


def format_antenna_dimension(value):
    """Dimension physique du panneau (AER_NB_DIMENSION, en metres) -> "1,5m", ou None.

    La valeur brute ANFR est deja en notation francaise ("1,5") : on la conserve telle quelle,
    comme la hauteur (AER_NB_ALT_BAS) inseree brute dans la meme chaine `physique`. Une
    dimension absente ou nulle n'est pas declaree par l'ANFR : on n'emet alors aucun tag.
    """
    text = clean_text(value)
    number = float_or_none(text)
    if number is None or number <= 0:
        return None
    return f"{text}m"


def parse_coordinates(raw_coord):
    matches = re.findall(r"[-+]?(?:\d+(?:[\.,]\d*)?|[\.,]\d+)", clean_text(raw_coord))
    if len(matches) < 2:
        return 0.0, 0.0
    try:
        return float(matches[0].replace(",", ".")), float(matches[1].replace(",", "."))
    except ValueError:
        return 0.0, 0.0


def frequency_to_mhz(value, unit):
    number = float_or_none(value)
    if number is None:
        return None
    unit_upper = (clean_text(unit) or "M").upper()
    if unit_upper.startswith("G"):
        return number * 1000.0
    if unit_upper.startswith("K"):
        return number / 1000.0
    if unit_upper.startswith("H"):
        return number / 1_000_000.0
    return number


def format_band_range(f_debut, f_fin, unite):
    suffix = {
        "G": "GHz",
        "M": "MHz",
        "K": "kHz",
        "H": "Hz",
    }.get((clean_text(unite) or "M").upper()[:1], f"{unite}Hz")
    return f"{f_debut}-{f_fin} {suffix}"


def compress_frequency_details(value):
    if not value:
        return None
    compressed = zlib.compress(value.encode("utf-8"), level=9)
    encoded = "Z1:" + base64.b64encode(compressed).decode("ascii")
    return encoded if len(encoded) < len(value) else value


def range_overlaps(start, end, low, high):
    if start is None or end is None:
        return False
    low_value = min(start, end)
    high_value = max(start, end)
    return low_value <= high and high_value >= low


def add_band_mask(station, band_bit):
    station["band_mask"] |= band_bit


def is_active_status(status):
    status_upper = clean_text(status).upper()
    return "EN SERVICE" in status_upper or "TECHNIQUEMENT OP" in status_upper


def update_masks_from_generation(station, generation):
    generation_upper = clean_text(generation).upper()
    if "2G" in generation_upper:
        station["tech_mask"] |= TECH_2G
    if "3G" in generation_upper:
        station["tech_mask"] |= TECH_3G
    if "4G" in generation_upper:
        station["tech_mask"] |= TECH_4G
    if "5G" in generation_upper:
        station["tech_mask"] |= TECH_5G


def update_masks_from_system_and_band(station, system, f_start_mhz, f_end_mhz):
    system_upper = clean_text(system).upper()

    if "FH" in system_upper:
        station["tech_mask"] |= TECH_FH
        add_band_mask(station, BAND_FH)
        return

    is_2g = "GSM" in system_upper or "2G" in system_upper
    is_3g = "UMTS" in system_upper or "3G" in system_upper
    is_4g = "LTE" in system_upper or "4G" in system_upper
    is_5g = "NR" in system_upper or "5G" in system_upper

    if is_2g:
        station["tech_mask"] |= TECH_2G
        if range_overlaps(f_start_mhz, f_end_mhz, 880, 960):
            add_band_mask(station, BAND_2G_900)
        if range_overlaps(f_start_mhz, f_end_mhz, 1710, 1880):
            add_band_mask(station, BAND_2G_1800)

    if is_3g:
        station["tech_mask"] |= TECH_3G
        if range_overlaps(f_start_mhz, f_end_mhz, 880, 960):
            add_band_mask(station, BAND_3G_900)
        if range_overlaps(f_start_mhz, f_end_mhz, 1920, 2170):
            add_band_mask(station, BAND_3G_2100)

    if is_4g:
        station["tech_mask"] |= TECH_4G
        if range_overlaps(f_start_mhz, f_end_mhz, 700, 790):
            add_band_mask(station, BAND_4G_700)
        if range_overlaps(f_start_mhz, f_end_mhz, 791, 862):
            add_band_mask(station, BAND_4G_800)
        if range_overlaps(f_start_mhz, f_end_mhz, 880, 960):
            add_band_mask(station, BAND_4G_900)
        if range_overlaps(f_start_mhz, f_end_mhz, 1710, 1880):
            add_band_mask(station, BAND_4G_1800)
        if range_overlaps(f_start_mhz, f_end_mhz, 1920, 2170):
            add_band_mask(station, BAND_4G_2100)
        if range_overlaps(f_start_mhz, f_end_mhz, 2500, 2690):
            add_band_mask(station, BAND_4G_2600)

    if is_5g:
        station["tech_mask"] |= TECH_5G
        if range_overlaps(f_start_mhz, f_end_mhz, 700, 790):
            add_band_mask(station, BAND_5G_700)
        if range_overlaps(f_start_mhz, f_end_mhz, 1427, 1518):
            add_band_mask(station, BAND_5G_1400)
        if range_overlaps(f_start_mhz, f_end_mhz, 1920, 2170):
            add_band_mask(station, BAND_5G_2100)
        if range_overlaps(f_start_mhz, f_end_mhz, 3300, 3800):
            add_band_mask(station, BAND_5G_3500)
        if range_overlaps(f_start_mhz, f_end_mhz, 3800.1, 4200):
            add_band_mask(station, BAND_5G_4200)
        if range_overlaps(f_start_mhz, f_end_mhz, 24000, 27500):
            add_band_mask(station, BAND_5G_26000)


def precharger_villes():
    print("Chargement du referentiel des communes...")
    try:
        request = urllib.request.Request(
            "https://geo.api.gouv.fr/communes?fields=nom,code",
            headers={"User-Agent": "GeoTower offline DB builder"},
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            if response.status == 200:
                payload = json.loads(response.read().decode("utf-8"))
                for commune in payload:
                    code = clean_text(commune.get("code"))
                    nom = clean_text(commune.get("nom")).upper()
                    if code and nom:
                        cache_villes[code] = nom
                print(f"{len(cache_villes)} communes chargees.")
            else:
                print(f"Erreur API Gouv: {response.status}")
    except (urllib.error.URLError, OSError, json.JSONDecodeError) as exc:
        print(f"Impossible de joindre l'API Gouv: {exc}")


MONTHLY_ZIP_REQUIRED_FILES = {
    TXT_STATION,
    TXT_SUPPORT,
    TXT_ANTENNE,
    TXT_EMETTEUR,
    TXT_BANDE,
}

ARCEP_SITE_COLUMN_ALIASES = {
    "operator": ("nom_op", "op_name", "operator", "operator_name"),
    "nidt": ("num_site", "op_site_id", "nidt"),
    "station": ("id_station_anfr", "sta_nm_anfr", "id_anfr", "station_anfr"),
    "site_zb": ("site_zb", "zb", "zone_blanche"),
    "site_dcc": ("site_dcc", "dcc"),
}


def is_monthly_anfr_zip(path):
    try:
        with zipfile.ZipFile(path, "r") as archive:
            names = {Path(name).name.upper() for name in archive.namelist()}
    except (OSError, zipfile.BadZipFile):
        return False
    return all(filename.upper() in names for filename in MONTHLY_ZIP_REQUIRED_FILES)


def get_latest_weekly_csv():
    csv_paths = discover_weekly_csv_files(Path(DATA_DIR), ())
    if not csv_paths:
        return None
    return max(csv_paths, key=lambda source: source.mtime())


def get_latest_monthly_zip():
    imports_dir = Path(DATA_DIR)
    source_dirs = [
        (imports_dir / FRANCE_SOURCES_DIR_NAME, True),
        (imports_dir, False),
    ]
    zip_paths = []
    seen = set()
    for directory, recursive in source_dirs:
        if directory.is_dir():
            iterator = directory.rglob("*.zip") if recursive else directory.glob("*.zip")
            for path in iterator:
                resolved = path.resolve()
                if resolved in seen:
                    continue
                seen.add(resolved)
                if is_monthly_anfr_zip(path):
                    zip_paths.append(path)
    if not zip_paths:
        return None
    return str(max(zip_paths, key=lambda path: path.stat().st_mtime))


def charger_reference(nom_fichier, col_cle, col_valeur):
    dico = {}
    variantes = reference_filename_variants(nom_fichier)
    for chemin in reference_file_candidates(variantes):
        with open(chemin, mode="r", encoding="utf-8-sig", errors="ignore") as f:
            loaded = read_reference_rows(f, col_cle, col_valeur)
            if loaded:
                return loaded

    for zip_path in reference_zip_candidates():
        loaded = charger_reference_depuis_zip(zip_path, variantes, col_cle, col_valeur)
        if loaded:
            return loaded
    return dico


def reference_file_candidates(variantes):
    noms_attendus = {Path(variante).name.lower() for variante in variantes}
    seen = set()
    for directory, recursive in reference_source_directories():
        if not directory.is_dir():
            continue

        for variante in variantes:
            path = directory / variante
            if path.is_file():
                resolved = path.resolve()
                if resolved not in seen:
                    seen.add(resolved)
                    yield path

        if not recursive:
            continue

        try:
            iterator = directory.rglob("*")
        except OSError:
            continue

        for path in iterator:
            if not path.is_file() or path.name.lower() not in noms_attendus:
                continue
            resolved = path.resolve()
            if resolved in seen:
                continue
            seen.add(resolved)
            yield path


def reference_filename_variants(nom_fichier):
    variants = [nom_fichier]
    suffix = Path(nom_fichier).suffix.lower()
    if suffix == ".txt":
        variants.append(nom_fichier[:-4] + ".csv")
    elif suffix == ".csv":
        variants.append(nom_fichier[:-4] + ".txt")
    return variants


def read_reference_rows(handle, col_cle, col_valeur):
    dico = {}
    reader = csv.DictReader(handle, delimiter=";")
    for row in reader:
        row_lower = {str(k).strip().lower(): v for k, v in row.items() if k}
        key = clean_text(row_lower.get(col_cle.lower()))
        value = clean_text(row_lower.get(col_valeur.lower()))
        if key and value:
            dico[key] = value
    return dico


def reference_source_directories():
    directories = [
        (Path(REF_DIR), True),
        (Path(DATA_DIR) / FRANCE_SOURCES_DIR_NAME, True),
        (Path(DATA_DIR), False),
    ]

    unique = []
    seen = set()
    for directory, recursive in directories:
        try:
            resolved = directory.resolve()
        except OSError:
            resolved = directory
        if resolved in seen:
            continue
        seen.add(resolved)
        unique.append((directory, recursive))
    return unique


def reference_zip_candidates():
    search_roots = reference_source_directories()
    candidates = []
    seen = set()
    for directory, recursive in search_roots:
        if not directory.is_dir():
            continue
        iterator = directory.rglob("*.zip") if recursive else directory.glob("*.zip")
        for path in iterator:
            resolved = path.resolve()
            if resolved in seen:
                continue
            seen.add(resolved)
            try:
                mtime = path.stat().st_mtime
            except OSError:
                continue
            candidates.append((mtime, path))
    return [path for _, path in sorted(candidates, reverse=True)]


def charger_reference_depuis_zip(zip_path, variantes, col_cle, col_valeur):
    noms_attendus = {Path(variante).name.lower() for variante in variantes}
    try:
        with zipfile.ZipFile(zip_path, "r") as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                if Path(info.filename).name.lower() not in noms_attendus:
                    continue
                with archive.open(info, "r") as raw:
                    text = io.TextIOWrapper(raw, encoding="utf-8-sig", errors="ignore", newline="")
                    return read_reference_rows(text, col_cle, col_valeur)
    except (OSError, zipfile.BadZipFile):
        return {}
    return {}


def get_latest_file(pattern):
    files = glob.glob(pattern)
    if not files:
        return None
    return max(files, key=os.path.getmtime)


def row_lower(row):
    return {str(k).strip().lower(): v for k, v in row.items() if k}


def normalize_arcep_operator(value):
    return clean_text(value).upper()


def resolve_columns(fieldnames, aliases):
    lookup = {name.strip().lower(): name for name in fieldnames if name}
    resolved = {}
    for key, candidates in aliases.items():
        for candidate in candidates:
            match = lookup.get(candidate.lower())
            if match:
                resolved[key] = match
                break
    return resolved


def parse_arcep_bool(value):
    normalized = clean_text(value).lower()
    return normalized in {"1", "true", "vrai", "oui", "yes", "y"}


def decode_csv_sample(raw_bytes):
    for encoding in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            return encoding, raw_bytes.decode(encoding)
        except UnicodeDecodeError:
            continue
    return "utf-8", raw_bytes.decode("utf-8", errors="replace")


def sniff_csv_dialect(sample):
    try:
        return csv.Sniffer().sniff(sample, delimiters=";,")
    except csv.Error:
        return csv.excel


@contextmanager
def open_arcep_csv_stream(source):
    archive = None
    handle = None
    try:
        if getattr(source, "member", None):
            archive = zipfile.ZipFile(source.path, "r")
            with archive.open(source.member, "r") as probe:
                encoding, sample = decode_csv_sample(probe.read(4096))
            handle = io.TextIOWrapper(
                archive.open(source.member, "r"),
                encoding=encoding,
                errors="replace",
                newline="",
            )
        else:
            path = Path(source.path if hasattr(source, "path") else source)
            with path.open("rb") as probe:
                encoding, sample = decode_csv_sample(probe.read(4096))
            handle = path.open("r", encoding=encoding, errors="replace", newline="")

        yield handle, sniff_csv_dialect(sample)
    finally:
        if handle is not None:
            handle.close()
        if archive is not None:
            archive.close()


def is_arcep_site_csv(source):
    try:
        with open_arcep_csv_stream(source) as (handle, dialect):
            reader = csv.DictReader(handle, dialect=dialect)
            if not reader.fieldnames:
                return False
            columns = resolve_columns(reader.fieldnames, ARCEP_SITE_COLUMN_ALIASES)
            return all(key in columns for key in ("operator", "nidt", "station"))
    except Exception:
        return False


def discover_arcep_site_csv_sources(imports_dir):
    imports_dir = Path(imports_dir)
    arcep_sites_dir = imports_dir / FRANCE_SOURCES_DIR_NAME / "arcep_sites"
    search_dirs = [arcep_sites_dir] if arcep_sites_dir.is_dir() else [imports_dir / FRANCE_SOURCES_DIR_NAME, imports_dir]
    candidates = []
    for directory in search_dirs:
        if not directory.is_dir():
            continue
        for path in sorted(directory.rglob("*")):
            if path.is_file() and path.suffix.lower() in {".csv", ".zip"}:
                candidates.extend(csv_sources_from_file(path))

    unique = []
    seen = set()
    for candidate in candidates:
        identity = candidate.identity
        if identity in seen:
            continue
        seen.add(identity)
        if is_arcep_site_csv(candidate):
            unique.append(candidate)
    return unique


def extract_quarterly_version_from_name(name):
    raw = str(name)
    quarter_patterns = (
        (r"(20\d{2})[\s_\-]*(?:t|q|trim(?:estre)?)[\s_\-]*([1-4])", 1, 2),
        (r"(?:t|q|trim(?:estre)?)[\s_\-]*([1-4])[\s_\-]*(20\d{2})", 2, 1),
        (r"([1-4])(?:er|e)?[\s_\-]*(?:trim(?:estre)?)[\s_\-]*(20\d{2})", 2, 1),
    )
    for pattern, year_group, quarter_group in quarter_patterns:
        match = re.search(pattern, raw, re.IGNORECASE)
        if match:
            return f"{match.group(year_group)}-T{int(match.group(quarter_group))}"
    return None


def latest_quarterly_version_from_sources(sources):
    versions = []
    for source in sources:
        version = (
            extract_quarterly_version_from_name(source.display_name)
            or extract_quarterly_version_from_name(source.name)
            or extract_quarterly_version_from_name(source.path.name)
        )
        if version:
            year, quarter = version.split("-T", 1)
            versions.append((int(year), int(quarter), version))
    if not versions:
        return None
    return max(versions)[2]


def load_arcep_site_metadata(imports_dir, sources=None):
    sources = list(sources) if sources is not None else discover_arcep_site_csv_sources(imports_dir)
    metadata = {}
    for source in sorted(sources, key=lambda item: (parse_source_date(None, item) or "", item.mtime(), item.display_name)):
        rows_read = 0
        with open_arcep_csv_stream(source) as (handle, dialect):
            reader = csv.DictReader(handle, dialect=dialect)
            if not reader.fieldnames:
                continue
            columns = resolve_columns(reader.fieldnames, ARCEP_SITE_COLUMN_ALIASES)
            if not all(key in columns for key in ("operator", "nidt", "station")):
                continue

            for row in reader:
                id_anfr = normalize_id_anfr(row.get(columns["station"]))
                operator_key = normalize_arcep_operator(row.get(columns["operator"]))
                if not id_anfr or not operator_key:
                    continue

                nidt = clean_text(row.get(columns["nidt"])) or None
                is_zb = (
                    ("site_zb" in columns and parse_arcep_bool(row.get(columns["site_zb"])))
                    or ("site_dcc" in columns and parse_arcep_bool(row.get(columns["site_dcc"])))
                )
                previous = metadata.get((id_anfr, operator_key), {})
                metadata[(id_anfr, operator_key)] = {
                    "arcep_nidt": nidt or previous.get("arcep_nidt"),
                    "is_zb": 1 if is_zb or previous.get("is_zb") == 1 else 0,
                }
                rows_read += 1
        print(f"ARCEP sites: {source.display_name} -> {rows_read} lignes")
    return metadata


def execute_many_in_batches(cur, sql, rows, batch_size=5000):
    batch = []
    total = 0
    for row in rows:
        batch.append(row)
        if len(batch) >= batch_size:
            cur.executemany(sql, batch)
            total += len(batch)
            batch.clear()
    if batch:
        cur.executemany(sql, batch)
        total += len(batch)
    return total


def build_frequency_details_for_station(id_anfr, raw_emetteurs, emr_bands, dict_aer_physique):
    detail_lines = set()
    for emetteur in raw_emetteurs.get(id_anfr, []):
        freqs_str = ", ".join(
            format_band_range(f_debut_raw, f_fin_raw, unite)
            for _f_debut, _f_fin, unite, f_debut_raw, f_fin_raw in emr_bands.get(emetteur["emr_id"], [])
        )
        phys_str = dict_aer_physique.get(emetteur["aer_id"], "Azimut non specifie")
        detail_lines.add(
            f"{emetteur['sys']} : {freqs_str} | "
            f"{emetteur['statut']} | "
            f"{emetteur['date_info'] or ''} | "
            f"{phys_str}"
        )
    return compress_frequency_details("\n".join(sorted(detail_lines)))


def create_schema(cur):
    cur.execute("CREATE TABLE IF NOT EXISTS `localisation` (`id_anfr` TEXT NOT NULL, `operateur_id` INTEGER, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `azimuts` TEXT, `code_insee` TEXT, `azimuts_fh` TEXT, `tech_mask` INTEGER NOT NULL, `band_mask` INTEGER NOT NULL, `arcep_nidt` TEXT, `is_zb` INTEGER NOT NULL, PRIMARY KEY(`id_anfr`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `technique` (`id_anfr` TEXT NOT NULL, `adm_id` INTEGER, `statut_id` INTEGER, `date_implantation` TEXT, `date_service` TEXT, `date_modif` TEXT, `details_frequences` TEXT, `adresse` TEXT, `has_active` INTEGER NOT NULL, PRIMARY KEY(`id_anfr`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `support` (`id_anfr` TEXT NOT NULL, `id_support` TEXT NOT NULL, `nat_id` INTEGER, `tpo_id` INTEGER, `hauteur` REAL, PRIMARY KEY(`id_anfr`, `id_support`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `antenne` (`aer_id` TEXT NOT NULL, `id_anfr` TEXT NOT NULL, `id_support` TEXT, `tae_id` INTEGER, `azimut` INTEGER, `hauteur_bas` REAL, `is_fh` INTEGER NOT NULL, PRIMARY KEY(`aer_id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_operateur` (`id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_nature` (`nat_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`nat_id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_proprietaire` (`tpo_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`tpo_id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_exploitant` (`adm_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`adm_id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_type_antenne` (`tae_id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`tae_id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_systeme` (`id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_statut` (`id` INTEGER NOT NULL, `libelle` TEXT NOT NULL, PRIMARY KEY(`id`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `ref_commune` (`code_insee` TEXT NOT NULL, `nom` TEXT NOT NULL, PRIMARY KEY(`code_insee`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `metadata` (`version` TEXT NOT NULL, `schema_version` INTEGER NOT NULL, `country_code` TEXT NOT NULL, `country_name` TEXT, `source` TEXT NOT NULL, `date_maj_anfr` TEXT, `zip_version` TEXT, PRIMARY KEY(`version`))")
    cur.execute("CREATE TABLE IF NOT EXISTS `source_versions` (`source_key` TEXT NOT NULL, `source_value` TEXT NOT NULL, PRIMARY KEY(`source_key`))")
    ensure_stats_tables(cur.connection)
    ensure_dept_stat_tables(cur.connection)


def write_version_json(db_version, date_maj_anfr, zip_filename, quarterly_version):
    payload = {
        "version": db_version,
        "schema_version": SCHEMA_VERSION,
        "country_code": COUNTRY_CODE,
        "country_name": COUNTRY_NAME,
        "source": SOURCE,
        "date_anfr": date_maj_anfr,
        "zip_name": zip_filename,
        "quarterly_version": quarterly_version,
        "db_name": DB_FILENAME,
    }
    version_file = os.path.join(DATA_DIR, VERSION_FILENAME)
    with open(version_file, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)


def run_build():
    print("Demarrage de la creation de la base offline GeoTower FR...")

    dict_nature = charger_reference("SUP_NATURE.txt", "nat_id", "nat_lb_nom")
    dict_proprio = charger_reference("SUP_PROPRIETAIRE.txt", "tpo_id", "tpo_lb")
    dict_exploitant = charger_reference("SUP_EXPLOITANT.txt", "adm_id", "adm_lb_nom")
    dict_type_ant = charger_reference("SUP_TYPE_ANTENNE.txt", "tae_id", "tae_lb")

    if not dict_nature:
        dict_nature = {"23": "Pylone", "17": "Chateau d'eau", "14": "Batiment", "5": "Mat"}
    if not dict_type_ant:
        dict_type_ant = {"16": "Panneau", "17": "Panneau", "18": "Panneau", "21": "Panneau", "26": "Parabole FH", "32": "Panneau 5G"}

    precharger_villes()

    zip_mensuel = get_latest_monthly_zip()
    if not zip_mensuel:
        print(f"Erreur: aucun ZIP mensuel trouve dans {Path(DATA_DIR) / FRANCE_SOURCES_DIR_NAME} ou {DATA_DIR}.")
        return
    print(f"ZIP mensuel utilise: {os.path.basename(zip_mensuel)}")

    csv_hebdo = get_latest_weekly_csv()
    if not csv_hebdo:
        print(f"Erreur: aucun CSV hebdomadaire trouve dans {Path(DATA_DIR) / FRANCE_SOURCES_DIR_NAME} ou {DATA_DIR}.")
        return
    print(f"CSV hebdomadaire utilise: {csv_hebdo.display_name}")

    if os.path.exists(DB_FILE):
        os.remove(DB_FILE)

    operateur_ids = IdRegistry()
    systeme_ids = IdRegistry()
    statut_ids = IdRegistry()
    operateur_ids.get_id("Inconnu")
    systeme_ids.get_id("Inconnu")
    statut_ids.get_id("Inconnu")

    vip_stations = {}
    date_maj_anfr = "Inconnue"

    print("1/6 Lecture du CSV hebdomadaire...")
    csv_handle, csv_dialect = open_csv(csv_hebdo)
    with csv_handle as f:
        reader = csv.DictReader(f, dialect=csv_dialect)
        for row in reader:
            lower = row_lower(row)

            if date_maj_anfr == "Inconnue":
                date_maj_anfr = clean_text(lower.get("date_maj")) or "Inconnue"

            id_anfr = normalize_id_anfr(lower.get("sta_nm_anfr"))
            if not id_anfr:
                continue

            if id_anfr not in vip_stations:
                lat, lon = parse_coordinates(lower.get("coordonnees"))
                statut_id = statut_ids.get_id(lower.get("statut"))
                vip_stations[id_anfr] = {
                    "operateur_id": operateur_ids.get_id(lower.get("adm_lb_nom")),
                    "adm_id": None,
                    "latitude": lat,
                    "longitude": lon,
                    "statut_id": statut_id,
                    "date_imp": None,
                    "date_ser": None,
                    "date_mod": None,
                    "adresse": None,
                    "code_insee": None,
                    "tech_mask": 0,
                    "band_mask": 0,
                    "has_active": 0,
                    "sys_status_map": {},
                }

            if is_active_status(lower.get("statut")):
                vip_stations[id_anfr]["has_active"] = 1

            update_masks_from_generation(vip_stations[id_anfr], lower.get("generation"))

            sys_name = clean_text(lower.get("emr_lb_systeme"))
            if sys_name:
                vip_stations[id_anfr]["sys_status_map"][sys_name.upper()] = (
                    statut_ids.get_id(lower.get("statut")),
                    clean_text(lower.get("emr_dt")) or None,
                )

    emr_bands = defaultdict(list)
    raw_emetteurs = defaultdict(list)
    antenne_by_id = {}
    support_by_key = {}
    aer_systems = defaultdict(set)
    anfr_azimuts = defaultdict(set)
    anfr_azimuts_fh = defaultdict(set)
    dict_aer_physique = {}
    used_nat_ids = set()
    used_tpo_ids = set()
    used_adm_ids = set()
    used_tae_ids = set()
    used_communes = set()

    with zipfile.ZipFile(zip_mensuel, "r") as z:
        def get_reader_from_zip(filename):
            file_in_zip = z.open(filename)
            text_file = io.TextIOWrapper(file_in_zip, encoding="utf-8", errors="ignore")
            return csv.DictReader(text_file, delimiter=";"), file_in_zip, text_file

        print("2/6 Recuperation des dates stations...")
        reader, f_bin, f_txt = get_reader_from_zip(TXT_STATION)
        for row in reader:
            id_anfr = normalize_id_anfr(row.get("STA_NM_ANFR"))
            if id_anfr in vip_stations:
                adm_id = int_or_none(row.get("ADM_ID"))
                vip_stations[id_anfr]["adm_id"] = adm_id
                vip_stations[id_anfr]["date_imp"] = clean_text(row.get("DTE_IMPLANTATION")) or None
                vip_stations[id_anfr]["date_mod"] = clean_text(row.get("DTE_MODIF")) or None
                vip_stations[id_anfr]["date_ser"] = clean_text(row.get("DTE_EN_SERVICE")) or None
                if adm_id is not None:
                    used_adm_ids.add(adm_id)
        f_txt.close()
        f_bin.close()

        print("3/6 Lecture des bandes de frequences...")
        reader, f_bin, f_txt = get_reader_from_zip(TXT_BANDE)
        for row in reader:
            emr_id = clean_text(row.get("EMR_ID"))
            f_debut_raw = clean_text(row.get("BAN_NB_F_DEB"))
            f_fin_raw = clean_text(row.get("BAN_NB_F_FIN"))
            f_debut = float_or_none(f_debut_raw)
            f_fin = float_or_none(f_fin_raw)
            unite = clean_text(row.get("BAN_FG_UNITE")) or "M"
            if emr_id and f_debut is not None and f_fin is not None:
                emr_bands[emr_id].append((f_debut, f_fin, unite, f_debut_raw, f_fin_raw))
        f_txt.close()
        f_bin.close()

        print("4/6 Traitement des emetteurs...")
        reader, f_bin, f_txt = get_reader_from_zip(TXT_EMETTEUR)
        for row in reader:
            id_anfr = normalize_id_anfr(row.get("STA_NM_ANFR"))
            if id_anfr not in vip_stations:
                continue

            emr_id = clean_text(row.get("EMR_ID"))
            aer_id = clean_text(row.get("AER_ID")) or None
            sys_name = clean_text(row.get("EMR_LB_SYSTEME")) or "Inconnu"

            if not emr_id:
                continue

            station = vip_stations[id_anfr]
            status_id, date_info = station["sys_status_map"].get(
                sys_name.upper(),
                (station["statut_id"], None),
            )
            systeme_ids.get_id(sys_name)
            raw_emetteurs[id_anfr].append(
                {
                    "emr_id": emr_id,
                    "aer_id": aer_id,
                    "sys": sys_name,
                    "statut": statut_ids.get_label(status_id),
                    "date_info": date_info,
                }
            )

            if aer_id:
                aer_systems[aer_id].add(sys_name)

            if not emr_bands.get(emr_id) and "FH" in sys_name.upper():
                station["tech_mask"] |= TECH_FH
                station["band_mask"] |= BAND_FH

            for f_debut, f_fin, unite, _f_debut_raw, _f_fin_raw in emr_bands.get(emr_id, []):
                update_masks_from_system_and_band(
                    station,
                    sys_name,
                    frequency_to_mhz(f_debut, unite),
                    frequency_to_mhz(f_fin, unite),
                )
        f_txt.close()
        f_bin.close()

        print("5/6 Analyse des antennes physiques...")
        reader, f_bin, f_txt = get_reader_from_zip(TXT_ANTENNE)
        for row in reader:
            id_anfr = normalize_id_anfr(row.get("STA_NM_ANFR"))
            if id_anfr not in vip_stations:
                continue

            aer_id = clean_text(row.get("AER_ID"))
            if not aer_id:
                continue

            sup_id = clean_text(row.get("SUP_ID")) or None
            tae_id = int_or_none(row.get("TAE_ID"))
            azimut = int_or_none(row.get("AER_NB_AZIMUT"))
            hauteur_bas = float_or_none(row.get("AER_NB_ALT_BAS"))
            is_fh = 1 if any("FH" in system.upper() for system in aer_systems.get(aer_id, set())) else 0
            type_texte = dict_type_ant.get(str(tae_id), f"Type inconnu ({tae_id})") if tae_id is not None else "Type inconnu"
            azimut_text = clean_text(row.get("AER_NB_AZIMUT")) or "N/A"
            hauteur_text = clean_text(row.get("AER_NB_ALT_BAS")) or "N/A"
            # Tag [DIM: ...] optionnel, meme convention que [AER_ID: ...] : les clients le
            # retirent de la chaine et l'affichent a cote du type de panneau.
            dimension_text = format_antenna_dimension(row.get("AER_NB_DIMENSION"))
            physique = f"{type_texte} : {azimut_text}° ({hauteur_text}m) [AER_ID: {aer_id}]"
            if dimension_text:
                physique += f" [DIM: {dimension_text}]"
            dict_aer_physique[aer_id] = physique

            if tae_id is not None:
                used_tae_ids.add(tae_id)

            if azimut is not None:
                if is_fh:
                    anfr_azimuts_fh[id_anfr].add(str(azimut))
                    vip_stations[id_anfr]["tech_mask"] |= TECH_FH
                    vip_stations[id_anfr]["band_mask"] |= BAND_FH
                else:
                    anfr_azimuts[id_anfr].add(str(azimut))

            antenne_by_id[aer_id] = (aer_id, id_anfr, sup_id, tae_id, azimut, hauteur_bas, is_fh)
        f_txt.close()
        f_bin.close()

        print("6/6 Preparation des supports, adresses et communes...")
        reader, f_bin, f_txt = get_reader_from_zip(TXT_SUPPORT)
        for row in reader:
            id_anfr = normalize_id_anfr(row.get("STA_NM_ANFR"))
            sup_id = clean_text(row.get("SUP_ID"))
            if id_anfr not in vip_stations or not sup_id:
                continue

            nat_id = int_or_none(row.get("NAT_ID"))
            tpo_id = int_or_none(row.get("TPO_ID"))
            hauteur = float_or_none(row.get("SUP_NM_HAUT"))
            code_insee = clean_text(row.get("COM_CD_INSEE")) or None

            if nat_id is not None:
                used_nat_ids.add(nat_id)
            if tpo_id is not None:
                used_tpo_ids.add(tpo_id)
            if code_insee:
                used_communes.add(code_insee)

            lieu = clean_text(row.get("ADR_LB_LIEU"))
            add1 = clean_text(row.get("ADR_LB_ADD1"))
            add2 = clean_text(row.get("ADR_LB_ADD2"))
            add3 = clean_text(row.get("ADR_LB_ADD3"))
            cp = clean_text(row.get("ADR_NM_CP"))
            ville = cache_villes.get(code_insee, "") if code_insee else ""

            rue_complete = ", ".join(part for part in [lieu, add1, add2, add3] if part)
            adresse_finale = rue_complete
            if cp or ville:
                adresse_finale = f"{adresse_finale}, " if adresse_finale else ""
                adresse_finale += f"{cp} {ville}".strip()

            if adresse_finale:
                vip_stations[id_anfr]["adresse"] = adresse_finale
            if code_insee:
                vip_stations[id_anfr]["code_insee"] = code_insee

            support_by_key[(id_anfr, sup_id)] = (id_anfr, sup_id, nat_id, tpo_id, hauteur)
        f_txt.close()
        f_bin.close()

    print("Creation de la base SQLite compacte...")
    conn = sqlite3.connect(DB_FILE)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode = OFF")
    cur.execute("PRAGMA synchronous = OFF")
    cur.execute("PRAGMA temp_store = FILE")

    create_schema(cur)

    print("Chargement des metadonnees ARCEP sites...")
    arcep_site_sources = discover_arcep_site_csv_sources(Path(DATA_DIR))
    quarterly_version = latest_quarterly_version_from_sources(arcep_site_sources)
    if quarterly_version:
        print(f"Version trimestrielle ARCEP: {quarterly_version}")
    arcep_site_metadata = load_arcep_site_metadata(Path(DATA_DIR), arcep_site_sources)

    print("Insertion des stations...")
    loc_batch = []
    tech_batch = []
    inserted_loc = 0
    inserted_tech = 0

    def flush_station_batches(force=False):
        nonlocal inserted_loc, inserted_tech
        if loc_batch and (force or len(loc_batch) >= 5000):
            cur.executemany("INSERT INTO localisation VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", loc_batch)
            inserted_loc += len(loc_batch)
            loc_batch.clear()
        if tech_batch and (force or len(tech_batch) >= 5000):
            cur.executemany("INSERT INTO technique VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", tech_batch)
            inserted_tech += len(tech_batch)
            tech_batch.clear()

    for id_anfr, data in vip_stations.items():
        az_list = sorted(anfr_azimuts.get(id_anfr, set()), key=lambda x: int(x) if x.isdigit() else 999)
        fh_az_list = sorted(anfr_azimuts_fh.get(id_anfr, set()), key=lambda x: int(x) if x.isdigit() else 999)
        operator_label = operateur_ids.get_label(data["operateur_id"])
        arcep_metadata = arcep_site_metadata.get((id_anfr, normalize_arcep_operator(operator_label)), {})
        loc_batch.append((
            id_anfr,
            data["operateur_id"],
            data["latitude"],
            data["longitude"],
            ",".join(az_list) if az_list else None,
            data["code_insee"],
            ",".join(fh_az_list) if fh_az_list else None,
            data["tech_mask"],
            data["band_mask"],
            arcep_metadata.get("arcep_nidt"),
            arcep_metadata.get("is_zb", 0),
        ))
        tech_batch.append((
            id_anfr,
            data["adm_id"],
            data["statut_id"],
            data["date_imp"],
            data["date_ser"],
            data["date_mod"],
            build_frequency_details_for_station(id_anfr, raw_emetteurs, emr_bands, dict_aer_physique),
            data["adresse"],
            data["has_active"],
        ))
        flush_station_batches()

    flush_station_batches(force=True)
    print(f"Stations inserees: localisation={inserted_loc}, technique={inserted_tech}")
    del arcep_site_metadata, vip_stations, raw_emetteurs, emr_bands, dict_aer_physique, anfr_azimuts, anfr_azimuts_fh

    ref_nature_data = sorted((nat_id, dict_nature.get(str(nat_id), f"Code Nature {nat_id}")) for nat_id in used_nat_ids)
    ref_proprio_data = sorted((tpo_id, dict_proprio.get(str(tpo_id), "Inconnu")) for tpo_id in used_tpo_ids)
    ref_exploitant_data = sorted((adm_id, dict_exploitant.get(str(adm_id), f"Code Exploitant {adm_id}")) for adm_id in used_adm_ids)
    ref_type_ant_data = sorted((tae_id, dict_type_ant.get(str(tae_id), f"Type inconnu ({tae_id})")) for tae_id in used_tae_ids)
    ref_commune_data = sorted((code, cache_villes[code]) for code in used_communes if code in cache_villes)

    print("Insertion des supports et antennes...")
    inserted_support = execute_many_in_batches(cur, "INSERT INTO support VALUES (?, ?, ?, ?, ?)", support_by_key.values())
    inserted_antenne = execute_many_in_batches(cur, "INSERT INTO antenne VALUES (?, ?, ?, ?, ?, ?, ?)", antenne_by_id.values())
    print(f"Supports inseres: {inserted_support} | Antennes inserees: {inserted_antenne}")
    # Ces deux dictionnaires ne servent plus, et ils pesent lourd (une entree par antenne).
    # Les stats par departement qui suivent relisent la base : autant leur laisser la place.
    del support_by_key, antenne_by_id, aer_systems
    cur.executemany("INSERT INTO ref_operateur VALUES (?, ?)", operateur_ids.rows())
    cur.executemany("INSERT INTO ref_nature VALUES (?, ?)", ref_nature_data)
    cur.executemany("INSERT INTO ref_proprietaire VALUES (?, ?)", ref_proprio_data)
    cur.executemany("INSERT INTO ref_exploitant VALUES (?, ?)", ref_exploitant_data)
    cur.executemany("INSERT INTO ref_type_antenne VALUES (?, ?)", ref_type_ant_data)
    cur.executemany("INSERT INTO ref_systeme VALUES (?, ?)", systeme_ids.rows())
    cur.executemany("INSERT INTO ref_statut VALUES (?, ?)", statut_ids.rows())
    cur.executemany("INSERT INTO ref_commune VALUES (?, ?)", ref_commune_data)

    db_version = datetime.now(ZoneInfo("Europe/Paris")).strftime("%Y%m%d_%H%M")
    zip_filename = os.path.basename(zip_mensuel)
    cur.execute(
        "INSERT INTO metadata VALUES (?, ?, ?, ?, ?, ?, ?)",
        (db_version, SCHEMA_VERSION, COUNTRY_CODE, COUNTRY_NAME, SOURCE, date_maj_anfr, zip_filename),
    )
    if quarterly_version:
        cur.execute(
            "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES (?, ?)",
            ("quarterly_version", quarterly_version),
        )

    france_sources_dir = Path(DATA_DIR) / FRANCE_SOURCES_DIR_NAME
    france_sources_dir.mkdir(parents=True, exist_ok=True)
    weekly_csv_paths = discover_weekly_csv_files(Path(DATA_DIR), ())
    current_stats_rows = populate_current_stats(conn)
    weekly_stats_rows = populate_weekly_stats(conn, weekly_csv_paths)

    print("Statistiques par departement...")
    dept_reference = load_department_reference(Path(REF_DIR))
    dept_stats = populate_department_stats(conn, dept_reference)
    update_dept_source_versions(conn)

    cur.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    cur.execute(
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
        (ROOM_IDENTITY_HASH,),
    )

    conn.commit()
    cur.execute("VACUUM")
    # Room lit PRAGMA user_version a l'ouverture et exige qu'il egale @Database(version).
    # Sans ca (user_version reste 0), Room declenche une migration 0->N introuvable et refuse
    # d'ouvrir la base, meme si room_master_table porte le bon identity_hash. On le pose apres
    # le VACUUM pour ne dependre d'aucun detail d'implementation.
    cur.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
    conn.close()

    write_version_json(db_version, date_maj_anfr, zip_filename, quarterly_version)

    if os.path.exists(LEGACY_DB_FILE):
        os.remove(LEGACY_DB_FILE)

    print(f"Version DB: {db_version} | ANFR: {date_maj_anfr} | ZIP: {zip_filename} | Trimestriel: {quarterly_version or 'Inconnu'}")
    print(f"Stats courantes: {current_stats_rows} lignes | Stats hebdo: {weekly_stats_rows} lignes")
    print(
        f"Stats departements: {dept_stats.department_rows} departements | "
        f"{dept_stats.operator_tech_rows} lignes operateur/techno"
    )
    print(f"Dossier sources FR: {france_sources_dir}")
    print(f"Termine: {DB_FILE} ({os.path.getsize(DB_FILE) / (1024 * 1024):.2f} Mo)")
    return

    details_frequences_by_station = {}
    for id_anfr, emetteurs in raw_emetteurs.items():
        detail_lines = set()
        for emetteur in emetteurs:
            freqs_str = ", ".join(
                format_band_range(f_debut_raw, f_fin_raw, unite)
                for _f_debut, _f_fin, unite, f_debut_raw, f_fin_raw in emr_bands.get(emetteur["emr_id"], [])
            )
            phys_str = dict_aer_physique.get(emetteur["aer_id"], "Azimut non spécifié")
            detail_lines.add(
                f"{emetteur['sys']} : {freqs_str} | "
                f"{emetteur['statut']} | "
                f"{emetteur['date_info'] or ''} | "
                f"{phys_str}"
            )
        details_frequences_by_station[id_anfr] = compress_frequency_details("\n".join(sorted(detail_lines)))

    arcep_site_sources = discover_arcep_site_csv_sources(Path(DATA_DIR))
    quarterly_version = latest_quarterly_version_from_sources(arcep_site_sources)
    arcep_site_metadata = load_arcep_site_metadata(Path(DATA_DIR), arcep_site_sources)

    loc_data = []
    tech_data = []
    for id_anfr, data in sorted(vip_stations.items()):
        az_list = sorted(anfr_azimuts.get(id_anfr, set()), key=lambda x: int(x) if x.isdigit() else 999)
        fh_az_list = sorted(anfr_azimuts_fh.get(id_anfr, set()), key=lambda x: int(x) if x.isdigit() else 999)
        operator_label = operateur_ids.get_label(data["operateur_id"])
        arcep_metadata = arcep_site_metadata.get((id_anfr, normalize_arcep_operator(operator_label)), {})
        loc_data.append((
            id_anfr,
            data["operateur_id"],
            data["latitude"],
            data["longitude"],
            ",".join(az_list) if az_list else None,
            data["code_insee"],
            ",".join(fh_az_list) if fh_az_list else None,
            data["tech_mask"],
            data["band_mask"],
            arcep_metadata.get("arcep_nidt"),
            arcep_metadata.get("is_zb", 0),
        ))
        tech_data.append((
            id_anfr,
            data["adm_id"],
            data["statut_id"],
            data["date_imp"],
            data["date_ser"],
            data["date_mod"],
            details_frequences_by_station.get(id_anfr),
            data["adresse"],
            data["has_active"],
        ))

    ref_nature_data = sorted((nat_id, dict_nature.get(str(nat_id), f"Code Nature {nat_id}")) for nat_id in used_nat_ids)
    ref_proprio_data = sorted((tpo_id, dict_proprio.get(str(tpo_id), "Inconnu")) for tpo_id in used_tpo_ids)
    ref_exploitant_data = sorted((adm_id, dict_exploitant.get(str(adm_id), f"Code Exploitant {adm_id}")) for adm_id in used_adm_ids)
    ref_type_ant_data = sorted((tae_id, dict_type_ant.get(str(tae_id), f"Type inconnu ({tae_id})")) for tae_id in used_tae_ids)
    ref_commune_data = sorted((code, cache_villes[code]) for code in used_communes if code in cache_villes)

    cur.executemany("INSERT INTO localisation VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", loc_data)
    cur.executemany("INSERT INTO technique VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", tech_data)
    cur.executemany("INSERT INTO support VALUES (?, ?, ?, ?, ?)", sorted(support_by_key.values()))
    cur.executemany("INSERT INTO antenne VALUES (?, ?, ?, ?, ?, ?, ?)", sorted(antenne_by_id.values()))
    cur.executemany("INSERT INTO ref_operateur VALUES (?, ?)", operateur_ids.rows())
    cur.executemany("INSERT INTO ref_nature VALUES (?, ?)", ref_nature_data)
    cur.executemany("INSERT INTO ref_proprietaire VALUES (?, ?)", ref_proprio_data)
    cur.executemany("INSERT INTO ref_exploitant VALUES (?, ?)", ref_exploitant_data)
    cur.executemany("INSERT INTO ref_type_antenne VALUES (?, ?)", ref_type_ant_data)
    cur.executemany("INSERT INTO ref_systeme VALUES (?, ?)", systeme_ids.rows())
    cur.executemany("INSERT INTO ref_statut VALUES (?, ?)", statut_ids.rows())
    cur.executemany("INSERT INTO ref_commune VALUES (?, ?)", ref_commune_data)

    db_version = datetime.now(ZoneInfo("Europe/Paris")).strftime("%Y%m%d_%H%M")
    zip_filename = os.path.basename(zip_mensuel)
    cur.execute(
        "INSERT INTO metadata VALUES (?, ?, ?, ?, ?, ?, ?)",
        (db_version, SCHEMA_VERSION, COUNTRY_CODE, COUNTRY_NAME, SOURCE, date_maj_anfr, zip_filename),
    )
    if quarterly_version:
        cur.execute(
            "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES (?, ?)",
            ("quarterly_version", quarterly_version),
        )

    france_sources_dir = Path(DATA_DIR) / FRANCE_SOURCES_DIR_NAME
    france_sources_dir.mkdir(parents=True, exist_ok=True)
    weekly_csv_paths = discover_weekly_csv_files(Path(DATA_DIR), ())
    current_stats_rows = populate_current_stats(conn)
    weekly_stats_rows = populate_weekly_stats(conn, weekly_csv_paths)

    cur.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    cur.execute(
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
        (ROOM_IDENTITY_HASH,),
    )

    conn.commit()
    cur.execute("VACUUM")
    # Room lit PRAGMA user_version a l'ouverture et exige qu'il egale @Database(version).
    # Sans ca (user_version reste 0), Room declenche une migration 0->N introuvable et refuse
    # d'ouvrir la base, meme si room_master_table porte le bon identity_hash. On le pose apres
    # le VACUUM pour ne dependre d'aucun detail d'implementation.
    cur.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
    conn.close()

    write_version_json(db_version, date_maj_anfr, zip_filename, quarterly_version)

    if os.path.exists(LEGACY_DB_FILE):
        os.remove(LEGACY_DB_FILE)

    print(f"Version DB: {db_version} | ANFR: {date_maj_anfr} | ZIP: {zip_filename} | Trimestriel: {quarterly_version or 'Inconnu'}")
    print(f"Stats courantes: {current_stats_rows} lignes | Stats hebdo: {weekly_stats_rows} lignes")
    print(f"Dossier sources FR: {france_sources_dir}")
    print(f"Termine: {DB_FILE} ({os.path.getsize(DB_FILE) / (1024 * 1024):.2f} Mo)")


if __name__ == "__main__":
    run_build()
