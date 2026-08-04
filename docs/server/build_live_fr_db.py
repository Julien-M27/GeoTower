import argparse
import base64
import json
import os
import re
import sqlite3
import zlib
from datetime import datetime, timezone
from pathlib import Path


IMPORTS_DIR = os.environ.get("GEOTOWER_IMPORTS_DIR", "/opt/geotower/data/imports")
SOURCE_DB_FILENAME = "geotower_fr.db"
LIVE_DB_FILENAME = "geotower_live_fr.db"
LIVE_VERSION_FILENAME = "version_live_fr.json"
LIVE_SCHEMA_VERSION = 1
LIVE_BUILD_PROGRESS_STEPS = 20
TECH_5G = 1 << 3
BAND_5G_700 = 1 << 10
BAND_5G_2100 = 1 << 11
BAND_5G_3500 = 1 << 12
BAND_5G_26000 = 1 << 13
BAND_5G_1400 = 1 << 21
BAND_5G_4200 = 1 << 22
FREQUENCY_RANGE_RE = re.compile(r"(\d+(?:[.,]\d+)?)\s*-\s*(\d+(?:[.,]\d+)?)\s*(kHz|MHz|GHz|Hz)?", re.IGNORECASE)


def progress(label: str) -> None:
    print(label, flush=True)


def default_source_db_path() -> Path:
    return Path(IMPORTS_DIR) / SOURCE_DB_FILENAME


def default_live_db_path() -> Path:
    return Path(IMPORTS_DIR) / LIVE_DB_FILENAME


def default_version_path(live_db_path: Path) -> Path:
    return live_db_path.with_name(LIVE_VERSION_FILENAME)


def table_exists(conn: sqlite3.Connection, table_name: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM src.sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        (table_name,),
    ).fetchone()
    return row is not None


def decode_frequency_details(value: str | None) -> str:
    if not value:
        return ""
    if not value.startswith("Z1:"):
        return value
    try:
        return zlib.decompress(base64.b64decode(value[3:])).decode("utf-8")
    except Exception:
        return ""


def normalize_frequency_to_mhz(value: str, unit: str | None) -> float | None:
    try:
        number = float(value.replace(",", "."))
    except ValueError:
        return None

    normalized_unit = (unit or "MHz").lower()
    if "ghz" in normalized_unit:
        return number * 1000.0
    if "khz" in normalized_unit:
        return number / 1000.0
    if "hz" in normalized_unit and "mhz" not in normalized_unit:
        return number / 1_000_000.0
    return number


def range_overlaps(start: float | None, end: float | None, low: float, high: float) -> bool:
    if start is None or end is None:
        return False
    return min(start, end) <= high and max(start, end) >= low


def live_detail_band_mask(value: str | None) -> int:
    details = decode_frequency_details(value)
    if not details:
        return 0

    mask = 0
    for raw_line in details.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        system = line.split(":", 1)[0].upper()
        if "5G" not in system and "NR" not in system:
            continue

        if "700" in system:
            mask |= BAND_5G_700
        if "1400" in system or "N75" in system:
            mask |= BAND_5G_1400
        if "2100" in system:
            mask |= BAND_5G_2100
        if "3500" in system or "N78" in system:
            mask |= BAND_5G_3500
        if "4200" in system or "N77" in system:
            mask |= BAND_5G_4200
        if "26000" in system or "26" in system or "N258" in system:
            mask |= BAND_5G_26000

        ranges = line.split(":", 1)[1].split("|", 1)[0] if ":" in line else line
        for match in FREQUENCY_RANGE_RE.finditer(ranges):
            start = normalize_frequency_to_mhz(match.group(1), match.group(3))
            end = normalize_frequency_to_mhz(match.group(2), match.group(3))
            if range_overlaps(start, end, 700.0, 790.0):
                mask |= BAND_5G_700
            if range_overlaps(start, end, 1427.0, 1518.0):
                mask |= BAND_5G_1400
            if range_overlaps(start, end, 1920.0, 2170.0):
                mask |= BAND_5G_2100
            if range_overlaps(start, end, 3300.0, 3800.0):
                mask |= BAND_5G_3500
            if range_overlaps(start, end, 3800.1, 4200.0):
                mask |= BAND_5G_4200
            if range_overlaps(start, end, 24000.0, 27500.0):
                mask |= BAND_5G_26000
    return mask


def validate_source_schema(conn: sqlite3.Connection) -> None:
    required_tables = {
        "localisation",
        "technique",
        "support",
        "antenne",
        "ref_operateur",
        "ref_statut",
        "ref_nature",
        "ref_proprietaire",
        "ref_exploitant",
        "ref_type_antenne",
        "ref_commune",
        "radio_stat_current",
        "radio_stat_weekly",
        # Stats par departement : ecrites par fr_dept_stats.py, toujours creees par
        # create_schema(). Si elles manquent, la base source est anterieure a cette
        # fonctionnalite : rejouer `python3 fr_dept_stats.py` avant de generer la base live.
        "dept_stat_current",
        "dept_stat_operator_tech",
        "metadata",
    }
    missing = sorted(table for table in required_tables if not table_exists(conn, table))
    if missing:
        raise RuntimeError(f"Tables source manquantes: {', '.join(missing)}")


def create_live_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;

        CREATE TABLE metadata (
            country_code TEXT NOT NULL,
            country_name TEXT,
            source TEXT NOT NULL,
            offline_db_version TEXT,
            offline_schema_version INTEGER NOT NULL,
            live_schema_version INTEGER NOT NULL,
            date_maj_anfr TEXT,
            zip_version TEXT,
            generated_at TEXT NOT NULL,
            row_count INTEGER NOT NULL,
            PRIMARY KEY(country_code)
        );

        CREATE TABLE site_summary (
            site_rowid INTEGER PRIMARY KEY AUTOINCREMENT,
            id_anfr TEXT NOT NULL,
            operator_name TEXT,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            azimuts TEXT,
            azimuts_fh TEXT,
            code_insee TEXT,
            commune TEXT,
            adresse TEXT,
            tech_mask INTEGER NOT NULL,
            band_mask INTEGER NOT NULL,
            statut TEXT,
            has_active INTEGER NOT NULL,
            has_underground_support INTEGER NOT NULL,
            arcep_nidt TEXT,
            is_zb INTEGER NOT NULL,
            support_count INTEGER NOT NULL,
            support_ids TEXT
        );

        CREATE TABLE site_detail (
            id_anfr TEXT PRIMARY KEY,
            technologies TEXT,
            statut TEXT,
            date_implantation TEXT,
            date_service TEXT,
            date_modif TEXT,
            details_frequences TEXT,
            adresse TEXT,
            operator_name TEXT,
            code_insee TEXT,
            commune TEXT,
            arcep_nidt TEXT,
            is_zb INTEGER NOT NULL
        );

        CREATE TABLE site_support (
            id_anfr TEXT NOT NULL,
            id_support TEXT NOT NULL,
            nature_support TEXT,
            proprietaire TEXT,
            exploitant TEXT,
            hauteur REAL,
            azimuts_et_types TEXT,
            PRIMARY KEY(id_anfr, id_support)
        );

        CREATE TABLE site_antenna (
            aer_id TEXT PRIMARY KEY,
            id_anfr TEXT NOT NULL,
            id_support TEXT,
            type_antenne TEXT,
            azimut INTEGER,
            hauteur_bas REAL,
            is_fh INTEGER NOT NULL
        );

        CREATE TABLE radio_stat_current (
            operator_name TEXT NOT NULL,
            category TEXT NOT NULL,
            item_key TEXT NOT NULL,
            label TEXT,
            total_count INTEGER NOT NULL,
            active_count INTEGER NOT NULL,
            PRIMARY KEY(operator_name, category, item_key)
        );

        CREATE TABLE radio_stat_weekly (
            week_key TEXT NOT NULL,
            week_start TEXT,
            source_date TEXT,
            operator_name TEXT NOT NULL,
            category TEXT NOT NULL,
            item_key TEXT NOT NULL,
            label TEXT,
            total_count INTEGER NOT NULL,
            active_count INTEGER NOT NULL,
            PRIMARY KEY(week_key, operator_name, category, item_key)
        );

        CREATE TABLE dept_stat_current (
            dept_code TEXT NOT NULL,
            dept_name TEXT,
            region_code TEXT,
            area_km2 REAL,
            population INTEGER,
            population_year TEXT,
            supports INTEGER NOT NULL,
            supports_active INTEGER NOT NULL,
            stations INTEGER NOT NULL,
            stations_active INTEGER NOT NULL,
            antennas INTEGER NOT NULL,
            antennas_active INTEGER NOT NULL,
            antennas_fh INTEGER NOT NULL,
            stations_per_support REAL,
            antennas_per_station REAL,
            supports_per_km2 REAL,
            stations_per_km2 REAL,
            antennas_per_km2 REAL,
            supports_per_1k_hab REAL,
            stations_per_1k_hab REAL,
            antennas_per_1k_hab REAL,
            hab_per_support REAL,
            hab_per_station REAL,
            hab_per_antenna REAL,
            PRIMARY KEY(dept_code)
        );

        CREATE TABLE dept_stat_operator_tech (
            dept_code TEXT NOT NULL,
            operator_name TEXT NOT NULL,
            tech TEXT NOT NULL,
            supports INTEGER NOT NULL,
            supports_active INTEGER NOT NULL,
            stations INTEGER NOT NULL,
            stations_active INTEGER NOT NULL,
            antennas INTEGER NOT NULL,
            antennas_active INTEGER NOT NULL,
            PRIMARY KEY(dept_code, operator_name, tech)
        );

        CREATE VIRTUAL TABLE site_rtree USING rtree(
            site_rowid,
            min_lat,
            max_lat,
            min_lon,
            max_lon
        );
        """
    )

    try:
        conn.execute(
            """
            CREATE VIRTUAL TABLE site_search_fts USING fts5(
                id_anfr,
                operator_name,
                adresse,
                commune,
                code_insee,
                support_ids,
                support_types,
                proprietaires,
                technologies,
                content=''
            )
            """
        )
    except sqlite3.OperationalError:
        conn.execute(
            """
            CREATE TABLE site_search_fts (
                id_anfr TEXT,
                operator_name TEXT,
                adresse TEXT,
                commune TEXT,
                code_insee TEXT,
                support_ids TEXT,
                support_types TEXT,
                proprietaires TEXT,
                technologies TEXT
            )
            """
        )


def execute_progress_script(
    conn: sqlite3.Connection,
    script: str,
    labels: list[str],
    start_step: int,
    total_steps: int,
) -> None:
    statements = [statement.strip() for statement in script.split(";") if statement.strip()]
    if len(statements) != len(labels):
        progress(f"{start_step}/{total_steps} Remplissage des tables live...")
        conn.executescript(script)
        return

    for offset, (label, statement) in enumerate(zip(labels, statements)):
        progress(f"{start_step + offset}/{total_steps} {label}...")
        conn.execute(statement)


def populate_live_tables(
    conn: sqlite3.Connection,
    start_step: int = 3,
    total_steps: int = LIVE_BUILD_PROGRESS_STEPS,
) -> int:
    live_table_script = """
        DROP TABLE IF EXISTS live_support_antenna_lines;

        CREATE TEMP TABLE live_support_antenna_lines AS
        SELECT
            id_anfr,
            id_support,
            GROUP_CONCAT(antenna_line, char(10)) AS azimuts_et_types
        FROM (
            SELECT DISTINCT
                a.id_anfr,
                a.id_support,
                a.azimut,
                COALESCE(ta.libelle, 'Type inconnu (' || COALESCE(CAST(a.tae_id AS TEXT), '') || ')') ||
                ' : ' || COALESCE(CAST(a.azimut AS TEXT), 'N/A') ||
                ' deg (' || COALESCE(CAST(a.hauteur_bas AS TEXT), 'N/A') || 'm)' ||
                ' [AER_ID: ' || a.aer_id || ']' AS antenna_line
            FROM src.antenne a
            LEFT JOIN src.ref_type_antenne ta ON a.tae_id = ta.tae_id
            WHERE a.is_fh = 0
            ORDER BY a.id_anfr, a.id_support, a.azimut
        )
        GROUP BY id_anfr, id_support;

        CREATE INDEX idx_live_support_antenna_lines
        ON live_support_antenna_lines(id_anfr, id_support);

        INSERT INTO site_summary (
            id_anfr,
            operator_name,
            latitude,
            longitude,
            azimuts,
            azimuts_fh,
            code_insee,
            commune,
            adresse,
            tech_mask,
            band_mask,
            statut,
            has_active,
            has_underground_support,
            arcep_nidt,
            is_zb,
            support_count,
            support_ids
        )
        SELECT
            l.id_anfr,
            COALESCE(o.libelle, 'Inconnu') AS operator_name,
            l.latitude,
            l.longitude,
            l.azimuts,
            l.azimuts_fh,
            l.code_insee,
            c.nom AS commune,
            t.adresse,
            l.tech_mask | CASE WHEN geotower_detail_band_mask(t.details_frequences) != 0 THEN 8 ELSE 0 END,
            l.band_mask | geotower_detail_band_mask(t.details_frequences),
            COALESCE(st.libelle, '') AS statut,
            COALESCE(t.has_active, 0) AS has_active,
            CASE WHEN EXISTS (
                SELECT 1
                FROM src.support underground_support
                LEFT JOIN src.ref_nature underground_nature
                    ON underground_support.nat_id = underground_nature.nat_id
                WHERE underground_support.id_anfr = l.id_anfr
                AND UPPER(COALESCE(underground_nature.libelle, '')) LIKE '%SOUS-TERRAIN%'
            ) THEN 1 ELSE 0 END AS has_underground_support,
            l.arcep_nidt,
            l.is_zb,
            COALESCE(supports.support_count, 0) AS support_count,
            supports.support_ids
        FROM src.localisation l
        LEFT JOIN src.ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN src.technique t ON l.id_anfr = t.id_anfr
        LEFT JOIN src.ref_statut st ON t.statut_id = st.id
        LEFT JOIN src.ref_commune c ON l.code_insee = c.code_insee
        LEFT JOIN (
            SELECT
                id_anfr,
                COUNT(DISTINCT id_support) AS support_count,
                GROUP_CONCAT(DISTINCT id_support) AS support_ids
            FROM src.support
            GROUP BY id_anfr
        ) supports ON supports.id_anfr = l.id_anfr
        ORDER BY l.id_anfr;

        INSERT INTO site_detail (
            id_anfr,
            technologies,
            statut,
            date_implantation,
            date_service,
            date_modif,
            details_frequences,
            adresse,
            operator_name,
            code_insee,
            commune,
            arcep_nidt,
            is_zb
        )
        SELECT
            t.id_anfr,
            RTRIM(
                CASE WHEN (l.tech_mask & 1) != 0 THEN '2G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 2) != 0 THEN '3G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 4) != 0 THEN '4G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 8) != 0 THEN '5G, ' ELSE '' END ||
                CASE WHEN (l.tech_mask & 16) != 0 THEN 'FH, ' ELSE '' END,
                ', '
            ) AS technologies,
            COALESCE(st.libelle, '') AS statut,
            t.date_implantation,
            t.date_service,
            t.date_modif,
            t.details_frequences,
            t.adresse,
            COALESCE(o.libelle, 'Inconnu') AS operator_name,
            l.code_insee,
            c.nom AS commune,
            l.arcep_nidt,
            l.is_zb
        FROM src.technique t
        INNER JOIN src.localisation l ON t.id_anfr = l.id_anfr
        LEFT JOIN src.ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN src.ref_statut st ON t.statut_id = st.id
        LEFT JOIN src.ref_commune c ON l.code_insee = c.code_insee;

        INSERT INTO site_support (
            id_anfr,
            id_support,
            nature_support,
            proprietaire,
            exploitant,
            hauteur,
            azimuts_et_types
        )
        SELECT
            s.id_anfr,
            s.id_support,
            COALESCE(n.libelle, CASE WHEN s.nat_id IS NULL THEN NULL ELSE 'Code Nature ' || CAST(s.nat_id AS TEXT) END) AS nature_support,
            COALESCE(p.libelle, 'Inconnu') AS proprietaire,
            COALESCE(e.libelle, CASE WHEN t.adm_id IS NULL THEN NULL ELSE 'Code Exploitant ' || CAST(t.adm_id AS TEXT) END) AS exploitant,
            s.hauteur,
            antenna_lines.azimuts_et_types
        FROM src.support s
        LEFT JOIN src.technique t ON s.id_anfr = t.id_anfr
        LEFT JOIN src.ref_nature n ON s.nat_id = n.nat_id
        LEFT JOIN src.ref_proprietaire p ON s.tpo_id = p.tpo_id
        LEFT JOIN src.ref_exploitant e ON t.adm_id = e.adm_id
        LEFT JOIN live_support_antenna_lines antenna_lines
            ON antenna_lines.id_anfr = s.id_anfr
            AND antenna_lines.id_support = s.id_support;

        INSERT INTO site_antenna (
            aer_id,
            id_anfr,
            id_support,
            type_antenne,
            azimut,
            hauteur_bas,
            is_fh
        )
        SELECT
            a.aer_id,
            a.id_anfr,
            a.id_support,
            COALESCE(ta.libelle, CASE WHEN a.tae_id IS NULL THEN NULL ELSE 'Type inconnu (' || CAST(a.tae_id AS TEXT) || ')' END),
            a.azimut,
            a.hauteur_bas,
            a.is_fh
        FROM src.antenne a
        LEFT JOIN src.ref_type_antenne ta ON a.tae_id = ta.tae_id;

        INSERT INTO site_rtree (site_rowid, min_lat, max_lat, min_lon, max_lon)
        SELECT site_rowid, latitude, latitude, longitude, longitude
        FROM site_summary;

        INSERT INTO site_search_fts (
            id_anfr,
            operator_name,
            adresse,
            commune,
            code_insee,
            support_ids,
            support_types,
            proprietaires,
            technologies
        )
        SELECT
            s.id_anfr,
            s.operator_name,
            s.adresse,
            s.commune,
            s.code_insee,
            s.support_ids,
            support_text.support_types,
            support_text.proprietaires,
            d.technologies
        FROM site_summary s
        LEFT JOIN site_detail d ON s.id_anfr = d.id_anfr
        LEFT JOIN (
            SELECT
                id_anfr,
                GROUP_CONCAT(DISTINCT nature_support) AS support_types,
                GROUP_CONCAT(DISTINCT proprietaire) AS proprietaires
            FROM site_support
            GROUP BY id_anfr
        ) support_text ON support_text.id_anfr = s.id_anfr;

        INSERT INTO radio_stat_current (
            operator_name,
            category,
            item_key,
            label,
            total_count,
            active_count
        )
        SELECT
            operator_name,
            category,
            item_key,
            label,
            total_count,
            active_count
        FROM src.radio_stat_current;

        INSERT INTO radio_stat_weekly (
            week_key,
            week_start,
            source_date,
            operator_name,
            category,
            item_key,
            label,
            total_count,
            active_count
        )
        SELECT
            week_key,
            week_start,
            source_date,
            operator_name,
            category,
            item_key,
            label,
            total_count,
            active_count
        FROM src.radio_stat_weekly;

        INSERT INTO dept_stat_current (
            dept_code,
            dept_name,
            region_code,
            area_km2,
            population,
            population_year,
            supports,
            supports_active,
            stations,
            stations_active,
            antennas,
            antennas_active,
            antennas_fh,
            stations_per_support,
            antennas_per_station,
            supports_per_km2,
            stations_per_km2,
            antennas_per_km2,
            supports_per_1k_hab,
            stations_per_1k_hab,
            antennas_per_1k_hab,
            hab_per_support,
            hab_per_station,
            hab_per_antenna
        )
        SELECT
            dept_code,
            dept_name,
            region_code,
            area_km2,
            population,
            population_year,
            supports,
            supports_active,
            stations,
            stations_active,
            antennas,
            antennas_active,
            antennas_fh,
            stations_per_support,
            antennas_per_station,
            supports_per_km2,
            stations_per_km2,
            antennas_per_km2,
            supports_per_1k_hab,
            stations_per_1k_hab,
            antennas_per_1k_hab,
            hab_per_support,
            hab_per_station,
            hab_per_antenna
        FROM src.dept_stat_current;

        INSERT INTO dept_stat_operator_tech (
            dept_code,
            operator_name,
            tech,
            supports,
            supports_active,
            stations,
            stations_active,
            antennas,
            antennas_active
        )
        SELECT
            dept_code,
            operator_name,
            tech,
            supports,
            supports_active,
            stations,
            stations_active,
            antennas,
            antennas_active
        FROM src.dept_stat_operator_tech;

        DROP TABLE IF EXISTS live_support_antenna_lines;
        """
    execute_progress_script(
        conn,
        live_table_script,
        labels=[
            "Nettoyage pre-agregation support",
            "Pre-agregation des antennes par support",
            "Index pre-agregation support",
            "Remplissage site_summary",
            "Remplissage site_detail",
            "Remplissage site_support",
            "Remplissage site_antenna",
            "Remplissage site_rtree",
            "Remplissage site_search_fts",
            "Copie radio_stat_current",
            "Copie radio_stat_weekly",
            "Copie dept_stat_current",
            "Copie dept_stat_operator_tech",
            "Nettoyage pre-agregation support",
        ],
        start_step=start_step,
        total_steps=total_steps,
    )
    row = conn.execute("SELECT COUNT(*) AS count FROM site_summary").fetchone()
    row_count = int(row["count"])
    progress(f"Tables live remplies: {row_count} sites")
    return row_count


def create_indexes(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE INDEX idx_site_summary_id_anfr ON site_summary(id_anfr);
        CREATE INDEX idx_site_summary_code_insee ON site_summary(code_insee);
        CREATE INDEX idx_site_summary_operator ON site_summary(operator_name);
        CREATE INDEX idx_site_summary_lat_lon ON site_summary(latitude, longitude);
        CREATE INDEX idx_site_summary_zb ON site_summary(is_zb);
        CREATE INDEX idx_site_summary_active ON site_summary(has_active);
        CREATE INDEX idx_site_support_id_anfr ON site_support(id_anfr);
        CREATE INDEX idx_site_support_id_support ON site_support(id_support);
        CREATE INDEX idx_site_antenna_id_anfr ON site_antenna(id_anfr);
        CREATE INDEX idx_site_antenna_id_support ON site_antenna(id_support);
        CREATE INDEX idx_radio_stat_current_operator ON radio_stat_current(operator_name, category, item_key);
        CREATE INDEX idx_radio_stat_weekly_operator ON radio_stat_weekly(operator_name, category, item_key, week_key);
        ANALYZE;
        """
    )


def read_source_metadata(conn: sqlite3.Connection) -> dict:
    row = conn.execute(
        """
        SELECT version, schema_version, country_code, country_name, source, date_maj_anfr, zip_version
        FROM src.metadata
        LIMIT 1
        """
    ).fetchone()
    if row is None:
        raise RuntimeError("Metadata source absente")
    return dict(row)


def validate_live_db(conn: sqlite3.Connection, expected_row_count: int) -> None:
    integrity_row = conn.execute("PRAGMA integrity_check").fetchone()
    if not integrity_row or str(integrity_row[0]).lower() != "ok":
        raise RuntimeError("PRAGMA integrity_check a echoue sur geotower_live_fr.db")

    actual_count = conn.execute("SELECT COUNT(*) FROM site_summary").fetchone()[0]
    if int(actual_count) != expected_row_count or expected_row_count <= 0:
        raise RuntimeError(f"Row count live invalide: {actual_count}")

    rtree_count = conn.execute("SELECT COUNT(*) FROM site_rtree").fetchone()[0]
    if int(rtree_count) != expected_row_count:
        raise RuntimeError(f"Row count RTree invalide: {rtree_count}")


def write_version_json(version_path: Path, metadata: dict, row_count: int, live_db_path: Path) -> None:
    payload = {
        "db_name": live_db_path.name,
        "country_code": metadata.get("country_code"),
        "country_name": metadata.get("country_name"),
        "source": metadata.get("source"),
        "offline_db_version": metadata.get("version"),
        "offline_schema_version": metadata.get("schema_version"),
        "live_schema_version": LIVE_SCHEMA_VERSION,
        "date_anfr": metadata.get("date_maj_anfr"),
        "zip_name": metadata.get("zip_version"),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "row_count": row_count,
    }
    with version_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def build_live_fr_db(
    source_db_path: Path | str | None = None,
    live_db_path: Path | str | None = None,
    version_path: Path | str | None = None,
) -> Path:
    progress("Demarrage de la creation de la base live GeoTower FR...")
    source_path = Path(source_db_path) if source_db_path is not None else default_source_db_path()
    output_path = Path(live_db_path) if live_db_path is not None else default_live_db_path()
    version_output_path = Path(version_path) if version_path is not None else default_version_path(output_path)
    temp_path = output_path.with_name(output_path.name + ".tmp")

    progress(f"DB source utilisee: {source_path}")
    progress(f"DB live cible: {output_path}")

    progress(f"1/{LIVE_BUILD_PROGRESS_STEPS} Verification de la DB source...")
    if not source_path.is_file():
        raise FileNotFoundError(f"DB source introuvable: {source_path}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    if temp_path.exists():
        temp_path.unlink()

    conn = sqlite3.connect(temp_path)
    conn.row_factory = sqlite3.Row
    try:
        conn.execute("ATTACH DATABASE ? AS src", (str(source_path),))
        conn.create_function("geotower_detail_band_mask", 1, live_detail_band_mask)
        validate_source_schema(conn)
        progress(f"2/{LIVE_BUILD_PROGRESS_STEPS} Creation du schema live...")
        create_live_schema(conn)
        row_count = populate_live_tables(conn)
        progress(f"15/{LIVE_BUILD_PROGRESS_STEPS} Lecture et ecriture des metadata...")
        metadata = read_source_metadata(conn)
        generated_at = datetime.now(timezone.utc).isoformat()
        conn.execute(
            """
            INSERT INTO metadata (
                country_code,
                country_name,
                source,
                offline_db_version,
                offline_schema_version,
                live_schema_version,
                date_maj_anfr,
                zip_version,
                generated_at,
                row_count
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                metadata.get("country_code") or "FR",
                metadata.get("country_name"),
                metadata.get("source") or "ANFR",
                metadata.get("version"),
                int(metadata.get("schema_version") or 0),
                LIVE_SCHEMA_VERSION,
                metadata.get("date_maj_anfr"),
                metadata.get("zip_version"),
                generated_at,
                row_count,
            ),
        )
        progress(f"16/{LIVE_BUILD_PROGRESS_STEPS} Creation des index et analyse SQLite...")
        create_indexes(conn)
        progress(f"17/{LIVE_BUILD_PROGRESS_STEPS} Validation de la DB live...")
        validate_live_db(conn, row_count)
        conn.commit()
        conn.execute("DETACH DATABASE src")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    progress(f"18/{LIVE_BUILD_PROGRESS_STEPS} Remplacement atomique et version JSON...")
    os.replace(temp_path, output_path)
    write_version_json(version_output_path, metadata, row_count, output_path)
    progress(f"DB live generee: {output_path}")
    return output_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Genere geotower_live_fr.db depuis geotower_fr.db")
    parser.add_argument("--source-db", default=str(default_source_db_path()))
    parser.add_argument("--output-db", default=str(default_live_db_path()))
    parser.add_argument("--version-json", default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    build_live_fr_db(
        source_db_path=args.source_db,
        live_db_path=args.output_db,
        version_path=args.version_json,
    )


if __name__ == "__main__":
    main()
