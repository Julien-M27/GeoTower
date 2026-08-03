import argparse
import base64
import csv
import io
import json
import os
import re
import sqlite3
import unicodedata
import zipfile
import zlib
from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple, Union


IMPORTS_DIR = Path(os.environ.get("GEOTOWER_IMPORTS_DIR", "/opt/geotower/data/imports"))
DB_FILENAME = "geotower_fr.db"
VERSION_FILENAME = "version_fr.json"
FRANCE_SOURCES_DIR_NAME = "france_sources"
SCHEMA_VERSION = 7
ROOM_IDENTITY_HASH = "f92129b45cc37b357c5ecb8e0ba597f0"

CATEGORY_SUPPORT = "support"
CATEGORY_TECH = "tech"
CATEGORY_BAND = "band"
ITEM_ALL = "ALL"

OVERSEAS_OPERATOR_SUFFIX = "_OVERSEAS"

OVERSEAS_EXPLICIT_OPERATOR_MARKERS = (
    "FREE CARAIBE",
    "FREE CARAIBES",
    "OUTREMER TELECOM",
    "OUTREMER",
    "SFR CARAIBE",
    "UTS CARAIBE",
    "DAUPHIN TELECOM",
    "DIGICEL",
    "SRR",
    "TELCO OI",
    "TELCO",
    "ONLY",
    "ZEOP",
    "MAORE",
    "SPM TELECOM",
    "GLOBALTEL",
    "OPT NOUVELLE",
    "OPT NC",
    "ONATI",
    "VINI",
    "PMT",
    "VODAFONE",
    "VITI",
    "SPT",
)

TECH_BITS = {
    "2G": 1 << 0,
    "3G": 1 << 1,
    "4G": 1 << 2,
    "5G": 1 << 3,
    "FH": 1 << 4,
}

BAND_BITS = {
    "2G|900": 1 << 0,
    "2G|1800": 1 << 1,
    "3G|900": 1 << 2,
    "3G|2100": 1 << 3,
    "4G|700": 1 << 4,
    "4G|800": 1 << 5,
    "4G|900": 1 << 6,
    "4G|1800": 1 << 7,
    "4G|2100": 1 << 8,
    "4G|2600": 1 << 9,
    "5G|700": 1 << 10,
    "5G|2100": 1 << 11,
    "5G|3500": 1 << 12,
    "5G|26000": 1 << 13,
    "FH": 1 << 14,
    "5G|1400": 1 << 21,
    "5G|4200": 1 << 22,
}

BAND_LABELS = {
    "2G|900": "2G 900 MHz",
    "2G|1800": "2G 1800 MHz",
    "3G|900": "3G 900 MHz",
    "3G|2100": "3G 2100 MHz",
    "4G|700": "4G 700 MHz",
    "4G|800": "4G 800 MHz",
    "4G|900": "4G 900 MHz",
    "4G|1800": "4G 1800 MHz",
    "4G|2100": "4G 2100 MHz",
    "4G|2600": "4G 2600 MHz",
    "5G|700": "5G 700 MHz",
    "5G|1400": "5G 1400 MHz (exp)",
    "5G|2100": "5G 2100 MHz",
    "5G|3500": "5G 3500 MHz",
    "5G|4200": "5G 4200 MHz (exp)",
    "5G|26000": "5G 26 GHz (exp)",
    "FH": "FH",
}

WEEKLY_COLUMN_ALIASES = {
    "operator": ("adm_lb_nom", "operateur", "operator", "operator_name"),
    "support": ("sup_id", "id_support", "support_id"),
    "system": ("emr_lb_systeme", "systeme", "system", "technologie"),
    "generation": ("generation", "tech", "technologie_generation"),
    "status": ("statut", "status"),
    "source_date": ("date_maj", "date_update", "updated_at"),
    "station": ("sta_nm_anfr", "id_anfr", "station_anfr"),
}


@dataclass(frozen=True)
class CsvSource:
    path: Path
    member: Optional[str] = None

    @property
    def name(self) -> str:
        return Path(self.member).name if self.member else self.path.name

    @property
    def display_name(self) -> str:
        if self.member:
            return f"{self.path.name}!{self.member}"
        return self.path.name

    @property
    def identity(self) -> Tuple[Path, str]:
        return self.path.resolve(), self.member or ""

    def mtime(self) -> float:
        return self.path.stat().st_mtime


@dataclass
class StatsAccumulator:
    totals: Dict[Tuple[str, str, str], Set[str]]
    actives: Dict[Tuple[str, str, str], Set[str]]

    @classmethod
    def create(cls) -> "StatsAccumulator":
        return cls(defaultdict(set), defaultdict(set))

    def add_total(self, operator_name: str, category: str, item_key: str, support_key: str) -> None:
        if operator_name and support_key:
            self.totals[(operator_name, category, item_key)].add(support_key)

    def add_active(self, operator_name: str, category: str, item_key: str, support_key: str) -> None:
        if operator_name and support_key:
            self.actives[(operator_name, category, item_key)].add(support_key)

    def add_support(
        self,
        operator_name: str,
        support_key: str,
        tech_keys: Iterable[str],
        band_keys: Iterable[str],
        active_tech_keys: Iterable[str],
        active_band_keys: Iterable[str],
        is_active_support: bool,
    ) -> None:
        self.add_total(operator_name, CATEGORY_SUPPORT, ITEM_ALL, support_key)
        if is_active_support:
            self.add_active(operator_name, CATEGORY_SUPPORT, ITEM_ALL, support_key)

        for tech_key in tech_keys:
            self.add_total(operator_name, CATEGORY_TECH, tech_key, support_key)
        for band_key in band_keys:
            self.add_total(operator_name, CATEGORY_BAND, band_key, support_key)
        for tech_key in active_tech_keys:
            self.add_active(operator_name, CATEGORY_TECH, tech_key, support_key)
        for band_key in active_band_keys:
            self.add_active(operator_name, CATEGORY_BAND, band_key, support_key)

    def rows(self, weekly_prefix: Optional[Tuple[str, Optional[str], Optional[str]]] = None) -> List[Tuple]:
        keys = set(self.totals.keys()) | set(self.actives.keys())
        rows = []
        for operator_name, category, item_key in sorted(keys):
            total_count = len(self.totals.get((operator_name, category, item_key), set()))
            active_count = min(len(self.actives.get((operator_name, category, item_key), set())), total_count)
            label = label_for(category, item_key)
            if weekly_prefix is None:
                rows.append((operator_name, category, item_key, label, total_count, active_count))
            else:
                week_key, week_start, source_date = weekly_prefix
                rows.append(
                    (
                        week_key,
                        week_start,
                        source_date,
                        operator_name,
                        category,
                        item_key,
                        label,
                        total_count,
                        active_count,
                    )
                )
        return rows


def normalized_text(value: Optional[str]) -> str:
    text = (value or "").strip().lower()
    text = unicodedata.normalize("NFKD", text)
    return "".join(char for char in text if not unicodedata.combining(char))


def normalized_operator(value: Optional[str]) -> str:
    return (value or "").strip().upper()


def is_overseas_code_insee(value: Optional[str]) -> bool:
    code = (value or "").strip()
    return code.startswith(("97", "98"))


def stats_operator_name(operator_name: str, code_insee: Optional[str]) -> str:
    if not is_overseas_code_insee(code_insee):
        return operator_name

    normalized = normalized_text(operator_name).upper()
    if any(marker in normalized for marker in OVERSEAS_EXPLICIT_OPERATOR_MARKERS):
        return operator_name
    if "ORANGE" in normalized:
        return f"ORANGE{OVERSEAS_OPERATOR_SUFFIX}"
    if normalized in {"SFR", "SFR MAYOTTE", "SOCIETE FRANCAISE DU RADIOTELEPHONE"}:
        return f"SFR{OVERSEAS_OPERATOR_SUFFIX}"
    if "BOUYGUES" in normalized or "BYTEL" in normalized:
        return f"BOUYGUES{OVERSEAS_OPERATOR_SUFFIX}"
    if normalized in {"FREE", "FREE MOBILE"}:
        return f"FREE{OVERSEAS_OPERATOR_SUFFIX}"
    return operator_name


def is_active_status(status: Optional[str]) -> bool:
    normalized = normalized_text(status)
    return "en service" in normalized or "techniquement operationnel" in normalized


def label_for(category: str, item_key: str) -> str:
    if category == CATEGORY_SUPPORT:
        return "Supports"
    if category == CATEGORY_TECH:
        return item_key
    if category == CATEGORY_BAND:
        return BAND_LABELS.get(item_key, item_key)
    return item_key


def tech_keys_from_mask(mask: int) -> Set[str]:
    return {key for key, bit in TECH_BITS.items() if mask & bit and key != "FH"}


def band_keys_from_mask(mask: int) -> Set[str]:
    return {key for key, bit in BAND_BITS.items() if mask & bit and key != "FH"}


def decode_details(value: Optional[str]) -> str:
    if not value or not value.startswith("Z1:"):
        return value or ""
    payload = value[3:]
    padding = "=" * (-len(payload) % 4)
    try:
        compressed = base64.urlsafe_b64decode(payload + padding)
        return zlib.decompress(compressed).decode("utf-8", errors="replace")
    except Exception:
        return value


def generation_from_system(system: Optional[str], generation: Optional[str] = None) -> Optional[str]:
    gen = (generation or "").strip().upper()
    if gen in {"2G", "3G", "4G", "5G"}:
        return gen

    raw = (system or "").upper()
    if "5G" in raw or "NR" in raw:
        return "5G"
    if "4G" in raw or "LTE" in raw:
        return "4G"
    if "3G" in raw or "UMTS" in raw:
        return "3G"
    if "2G" in raw or "GSM" in raw:
        return "2G"
    return None


def band_keys_from_system(system: Optional[str], generation: Optional[str] = None) -> Set[str]:
    gen = generation_from_system(system, generation)
    if gen is None:
        return set()

    raw = (system or "").upper().replace(",", ".")
    numbers = [int(match) for match in re.findall(r"\d{2,5}", raw)]
    candidates: Set[str] = set()
    for number in numbers:
        if number == 26 and gen == "5G":
            candidates.add("5G|26000")
        else:
            candidates.add(f"{gen}|{number}")
    return {candidate for candidate in candidates if candidate in BAND_BITS}


def active_radio_keys_from_details(details_value: Optional[str]) -> Tuple[Set[str], Set[str]]:
    details = decode_details(details_value)
    tech_keys: Set[str] = set()
    band_keys: Set[str] = set()
    for raw_line in details.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parts = [part.strip() for part in line.split("|")]
        raw_frequency = parts[0] if parts else ""
        status = parts[1] if len(parts) > 1 else ""
        if is_active_status(status):
            gen = generation_from_system(raw_frequency)
            if gen:
                tech_keys.add(gen)
                band_keys.update(band_keys_from_system(raw_frequency, gen))
    return tech_keys, band_keys


def ensure_stats_tables(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS radio_stat_current (
            operator_name TEXT NOT NULL,
            category TEXT NOT NULL,
            item_key TEXT NOT NULL,
            label TEXT,
            total_count INTEGER NOT NULL,
            active_count INTEGER NOT NULL,
            PRIMARY KEY (operator_name, category, item_key)
        );

        CREATE TABLE IF NOT EXISTS radio_stat_weekly (
            week_key TEXT NOT NULL,
            week_start TEXT,
            source_date TEXT,
            operator_name TEXT NOT NULL,
            category TEXT NOT NULL,
            item_key TEXT NOT NULL,
            label TEXT,
            total_count INTEGER NOT NULL,
            active_count INTEGER NOT NULL,
            PRIMARY KEY (week_key, operator_name, category, item_key)
        );

        """
    )


def populate_current_stats(conn: sqlite3.Connection) -> int:
    accumulator = StatsAccumulator.create()
    query = """
        SELECT DISTINCT
            s.id_anfr,
            s.id_support,
            UPPER(TRIM(o.libelle)) AS operator_name,
            l.code_insee,
            COALESCE(l.tech_mask, 0) AS tech_mask,
            COALESCE(l.band_mask, 0) AS band_mask,
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            t.details_frequences AS details_frequences
        FROM support s
        INNER JOIN localisation l ON s.id_anfr = l.id_anfr
        INNER JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
    """
    for row in conn.execute(query):
        id_anfr, id_support, raw_operator_name, code_insee, tech_mask, band_mask, statut, has_active, details = row
        operator_name = stats_operator_name(raw_operator_name, code_insee)
        support_key = str(id_support or id_anfr or "").strip()
        tech_keys = tech_keys_from_mask(int(tech_mask or 0))
        band_keys = band_keys_from_mask(int(band_mask or 0))
        detailed_active_tech, detailed_active_band = active_radio_keys_from_details(details)
        has_detailed_active = bool(detailed_active_tech or detailed_active_band)
        is_active_support = has_detailed_active or int(has_active or 0) == 1 or is_active_status(statut)
        active_tech = detailed_active_tech if has_detailed_active else (tech_keys if is_active_support else set())
        active_band = detailed_active_band if has_detailed_active else (band_keys if is_active_support else set())
        accumulator.add_support(
            operator_name=operator_name,
            support_key=support_key,
            tech_keys=tech_keys,
            band_keys=band_keys,
            active_tech_keys=active_tech,
            active_band_keys=active_band,
            is_active_support=is_active_support,
        )

    rows = accumulator.rows()
    conn.execute("DELETE FROM radio_stat_current")
    conn.executemany(
        """
        INSERT INTO radio_stat_current (
            operator_name, category, item_key, label, total_count, active_count
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    return len(rows)


def resolve_columns(fieldnames: Sequence[str]) -> Dict[str, str]:
    lookup = {name.strip().lower(): name for name in fieldnames}
    resolved: Dict[str, str] = {}
    for key, aliases in WEEKLY_COLUMN_ALIASES.items():
        for alias in aliases:
            if alias.lower() in lookup:
                resolved[key] = lookup[alias.lower()]
                break
    return resolved


def coerce_csv_source(source: Union[Path, CsvSource]) -> CsvSource:
    if isinstance(source, CsvSource):
        return source
    return CsvSource(Path(source))


def read_csv_source_bytes(source: Union[Path, CsvSource]) -> bytes:
    source = coerce_csv_source(source)
    if source.member:
        with zipfile.ZipFile(source.path, "r") as archive:
            with archive.open(source.member, "r") as member:
                return member.read()
    return source.path.read_bytes()


def open_csv(source: Union[Path, CsvSource]):
    raw_bytes = read_csv_source_bytes(source)
    for encoding in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            text = raw_bytes.decode(encoding)
            handle = io.StringIO(text)
            sample = handle.read(4096)
            handle.seek(0)
            try:
                dialect = csv.Sniffer().sniff(sample, delimiters=";,")
            except csv.Error:
                dialect = csv.excel
            return handle, dialect
        except UnicodeDecodeError:
            continue
    handle = io.StringIO(raw_bytes.decode("utf-8", errors="replace"))
    return handle, csv.excel


def csv_has_weekly_columns(source: Union[Path, CsvSource]) -> bool:
    try:
        handle, dialect = open_csv(source)
        with handle:
            reader = csv.DictReader(handle, dialect=dialect)
            if not reader.fieldnames:
                return False
            columns = resolve_columns(reader.fieldnames)
            return all(key in columns for key in ("operator", "support", "system", "status"))
    except Exception:
        return False


def csv_sources_from_file(path: Path) -> List[CsvSource]:
    suffix = path.suffix.lower()
    if suffix == ".csv":
        return [CsvSource(path)]
    if suffix != ".zip":
        return []

    sources: List[CsvSource] = []
    try:
        with zipfile.ZipFile(path, "r") as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                if Path(info.filename).suffix.lower() == ".csv":
                    sources.append(CsvSource(path, info.filename))
    except (OSError, zipfile.BadZipFile):
        return []
    return sources


def discover_weekly_csv_files(imports_dir: Path, explicit_sources: Sequence[Path]) -> List[CsvSource]:
    candidates: List[CsvSource] = []
    search_dirs = [(imports_dir / FRANCE_SOURCES_DIR_NAME, True), (imports_dir, False)]
    for source in explicit_sources:
        if source.is_dir():
            search_dirs.append((source, True))
        elif source.is_file():
            candidates.extend(csv_sources_from_file(source))

    for directory, recursive in search_dirs:
        if directory.is_dir():
            iterator = directory.rglob("*") if recursive else directory.iterdir()
            for candidate in sorted(iterator):
                candidates.extend(csv_sources_from_file(candidate))

    unique: List[CsvSource] = []
    seen = set()
    for candidate in candidates:
        identity = candidate.identity
        if identity in seen:
            continue
        seen.add(identity)
        if csv_has_weekly_columns(candidate):
            unique.append(candidate)
    return unique


def parse_source_date(value: Optional[str], fallback_source: Union[Path, CsvSource]) -> Optional[str]:
    fallback_source = coerce_csv_source(fallback_source)
    raw = (value or "").strip()
    if raw:
        raw_date = raw.split("T", 1)[0].split(" ", 1)[0]
        for pattern in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%Y/%m/%d", "%Y%m%d"):
            try:
                return datetime.strptime(raw_date, pattern).date().isoformat()
            except ValueError:
                continue

    match = re.search(r"(20\d{6})", fallback_source.name)
    if match:
        token = match.group(1)
        return f"{token[0:4]}-{token[4:6]}-{token[6:8]}"

    quarter_patterns = (
        (r"(20\d{2})[\s_\-]*(?:t|q|trim(?:estre)?)[\s_\-]*([1-4])", 1, 2),
        (r"(?:t|q|trim(?:estre)?)[\s_\-]*([1-4])[\s_\-]*(20\d{2})", 2, 1),
        (r"([1-4])(?:er|e)?[\s_\-]*(?:trim(?:estre)?)[\s_\-]*(20\d{2})", 2, 1),
    )
    for pattern, year_group, quarter_group in quarter_patterns:
        quarter_match = re.search(pattern, fallback_source.name, re.IGNORECASE)
        if quarter_match:
            year = quarter_match.group(year_group)
            quarter = int(quarter_match.group(quarter_group))
            break
    else:
        year = None
        quarter = None
    if year is not None and quarter is not None:
        month = (quarter - 1) * 3 + 1
        return f"{year}-{month:02d}-01"
    return None


def iso_week_from_source_date(source_date: Optional[str], fallback_source: Union[Path, CsvSource]) -> Tuple[str, Optional[str], Optional[str]]:
    fallback_source = coerce_csv_source(fallback_source)
    parsed_date = None
    if source_date:
        try:
            parsed_date = datetime.strptime(source_date, "%Y-%m-%d").date()
        except ValueError:
            parsed_date = None
    if parsed_date is None:
        timestamp = fallback_source.mtime()
        parsed_date = datetime.fromtimestamp(timestamp).date()
        source_date = parsed_date.isoformat()

    iso_year, iso_week, iso_weekday = parsed_date.isocalendar()
    week_start = date.fromisocalendar(iso_year, iso_week, 1).isoformat()
    return f"{iso_year}-W{iso_week:02d}", week_start, source_date


def populate_weekly_stats(conn: sqlite3.Connection, csv_paths: Sequence[Union[Path, CsvSource]]) -> int:
    conn.execute("DELETE FROM radio_stat_weekly")
    inserted = 0

    for raw_source in csv_paths:
        source = coerce_csv_source(raw_source)
        accumulators: Dict[Optional[str], StatsAccumulator] = {}
        fallback_source_date = parse_source_date(None, source)
        handle, dialect = open_csv(source)
        with handle:
            reader = csv.DictReader(handle, dialect=dialect)
            if not reader.fieldnames:
                continue
            columns = resolve_columns(reader.fieldnames)
            if not all(key in columns for key in ("operator", "support", "system", "status")):
                continue

            for row in reader:
                operator_name = normalized_operator(row.get(columns["operator"]))
                support_id = (row.get(columns["support"]) or row.get(columns.get("station", ""), "") or "").strip()
                system = row.get(columns["system"])
                generation = row.get(columns.get("generation", ""), "")
                status = row.get(columns["status"])
                source_date = fallback_source_date
                if "source_date" in columns:
                    source_date = parse_source_date(row.get(columns["source_date"], ""), source) or fallback_source_date

                tech_key = generation_from_system(system, generation)
                tech_keys = {tech_key} if tech_key else set()
                band_keys = band_keys_from_system(system, generation)
                active = is_active_status(status)
                accumulator = accumulators.setdefault(source_date, StatsAccumulator.create())
                accumulator.add_support(
                    operator_name=operator_name,
                    support_key=support_id,
                    tech_keys=tech_keys,
                    band_keys=band_keys,
                    active_tech_keys=tech_keys if active else set(),
                    active_band_keys=band_keys if active else set(),
                    is_active_support=active,
                )

        for source_date, accumulator in sorted(accumulators.items(), key=lambda item: item[0] or ""):
            week_prefix = iso_week_from_source_date(source_date, source)
            rows = accumulator.rows(weekly_prefix=week_prefix)
            conn.executemany(
                """
                INSERT OR REPLACE INTO radio_stat_weekly (
                    week_key, week_start, source_date, operator_name, category, item_key, label, total_count, active_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows,
            )
            inserted += len(rows)
            print(f"Stats hebdo: {source.display_name} [{week_prefix[0]}] -> {len(rows)} lignes")
    return inserted


def update_metadata(conn: sqlite3.Connection) -> None:
    conn.execute("UPDATE metadata SET schema_version = ?", (SCHEMA_VERSION,))


def ensure_arcep_site_columns(conn: sqlite3.Connection) -> None:
    columns = {row[1] for row in conn.execute("PRAGMA table_info(localisation)").fetchall()}
    if "arcep_nidt" not in columns:
        conn.execute("ALTER TABLE localisation ADD COLUMN arcep_nidt TEXT")
    if "is_zb" not in columns:
        conn.execute("ALTER TABLE localisation ADD COLUMN is_zb INTEGER NOT NULL DEFAULT 0")


def require_exploitant_schema(conn: sqlite3.Connection) -> None:
    tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    technique_columns = {row[1] for row in conn.execute("PRAGMA table_info(technique)").fetchall()}
    if "ref_exploitant" not in tables or "adm_id" not in technique_columns:
        raise RuntimeError("Schema 7 requis: regenerez la base complete avec build_fr_anfr_db.py")


def update_room_identity_hash(conn: sqlite3.Connection) -> None:
    conn.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    conn.execute(
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
        (ROOM_IDENTITY_HASH,),
    )


def update_version_json(imports_dir: Path) -> None:
    version_path = imports_dir / VERSION_FILENAME
    data = {}
    if version_path.exists():
        try:
            data = json.loads(version_path.read_text(encoding="utf-8"))
        except Exception:
            data = {}
    data["schema_version"] = SCHEMA_VERSION
    data["country_code"] = data.get("country_code") or "FR"
    data["country_name"] = data.get("country_name") or "France"
    data["source"] = data.get("source") or "ANFR"
    data["db_name"] = DB_FILENAME
    payload = json.dumps(data, ensure_ascii=False, indent=2)
    version_path.write_text(payload, encoding="utf-8")


def run_build(
    imports_dir: Path = IMPORTS_DIR,
    db_path: Optional[Path] = None,
    weekly_sources: Sequence[Path] = (),
) -> None:
    imports_dir = Path(imports_dir)
    db_path = Path(db_path) if db_path else imports_dir / DB_FILENAME
    france_sources_dir = imports_dir / FRANCE_SOURCES_DIR_NAME
    france_sources_dir.mkdir(parents=True, exist_ok=True)

    if not db_path.exists():
        raise FileNotFoundError(f"Base SQLite introuvable: {db_path}")

    csv_paths = discover_weekly_csv_files(imports_dir, weekly_sources)
    print(f"Base FR: {db_path}")
    print(f"Dossier sources FR: {france_sources_dir}")
    print(f"Fichiers hebdo detectes: {len(csv_paths)}")

    conn = sqlite3.connect(db_path)
    try:
        ensure_stats_tables(conn)
        ensure_arcep_site_columns(conn)
        require_exploitant_schema(conn)
        current_rows = populate_current_stats(conn)
        weekly_rows = populate_weekly_stats(conn, csv_paths)
        update_metadata(conn)
        update_room_identity_hash(conn)
        conn.commit()
        update_version_json(imports_dir)
        print(f"Stats courantes: {current_rows} lignes")
        print(f"Stats hebdo: {weekly_rows} lignes")
        print(f"Schema DB mis a jour: {SCHEMA_VERSION}")
    finally:
        conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Enrichit la base GeoTower FR avec les statistiques pre-calculees.")
    parser.add_argument("--imports-dir", type=Path, default=IMPORTS_DIR)
    parser.add_argument("--db", type=Path, default=None)
    parser.add_argument("--weekly-source", type=Path, action="append", default=[])
    args = parser.parse_args()
    run_build(imports_dir=args.imports_dir, db_path=args.db, weekly_sources=args.weekly_source)


if __name__ == "__main__":
    main()
