#!/usr/bin/env python3
"""Construit geotower_fr_enb.db a partir des fichiers du partenaire eNB-Analytics.

Source : https://enb-analytics.fr/files/ressources/NmEa_<PLMN>.ntm.zip, un fichier par
operateur metropolitain (20801 Orange, 20810 SFR, 20815 Free, 20820 Bouygues). Chaque
archive contient un CSV ';' sans en-tete nomme dont on ne retient que la techno, le MNC,
l'eNB/gNB, l'ID support ANFR, la position et - pour les seules lignes non rattachees a un
support - l'adresse.

Les colonnes 4 (drapeau -1/0/1/5) et 5 (TAC presume) sont volontairement ignorees : leur
semantique n'est pas documentee par le partenaire (decision produit du 2026-07-27). Les
reintroduire coutera un bump de SCHEMA_VERSION, ne pas le faire sans demande explicite.

Cache brut par operateur (<imports>/enb_sources/) : tout telechargement juge sain y est
conserve. Si un operateur est injoignable ou renvoie un fichier aberrant, on rebatit avec sa
derniere copie saine plutot que de le faire disparaitre de la base. Aucune sortie partielle
n'ecrase une base existante sans --allow-partial.

Exemples :
    python3 build_fr_enb_db.py
    python3 build_fr_enb_db.py --db /opt/geotower/data/imports/geotower_fr.db
    python3 build_fr_enb_db.py --offline --output /tmp/geotower_fr_enb.db
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import io
import json
import os
import re
import sqlite3
import sys
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path
from typing import Any


IMPORTS_DIR = Path(os.environ.get("GEOTOWER_IMPORTS_DIR", "/opt/geotower/data/imports"))
CACHE_DIR_NAME = "enb_sources"
DEFAULT_DB_FILENAME = "geotower_fr_enb.db"
DEFAULT_VERSION_FILENAME = "version_fr_enb.json"

SCHEMA_VERSION = 1
COUNTRY_CODE = "FR"
COUNTRY_NAME = "France"
SOURCE = "ENB_ANALYTICS"
SOURCE_URL_TEMPLATE = "https://enb-analytics.fr/files/ressources/NmEa_{plmn}.ntm.zip"
USER_AGENT = "GeoTower eNB builder/1.0"

# PLMN metropole. Le partenaire ne publie rien pour les DOM (404 au 2026-07-27) : ajouter une
# entree ici suffira le jour ou ca change. "db_operator_like" reproduit exactement le filtre
# operateur utilise par l'app (SiteDetailScreen) pour la mesure de qualite de jointure.
#
# "mncs" = codes reseau que ce fichier a le droit d'apporter : le principal et le secondaire de
# l'operateur. Les fichiers en contiennent d'autres a la marge (constate le 2026-07-27 dans le
# fichier Free : 394 lignes en 208-16, 817 en MNC -1 et ~100 lignes portant le MNC d'un autre
# operateur, toutes avec `col4 = 2` et sans support). Ces lignes-la sont ignorees : la source
# faisant autorite pour un operateur, c'est son propre fichier.
OPERATOR_SOURCES: dict[str, dict[str, Any]] = {
    "orange": {"label": "Orange", "plmn": "20801", "mnc": 1, "mncs": (1, 2), "db_operator_like": "%ORANGE%"},
    "sfr": {"label": "SFR", "plmn": "20810", "mnc": 10, "mncs": (10, 13), "db_operator_like": "%SFR%"},
    "free": {"label": "Free Mobile", "plmn": "20815", "mnc": 15, "mncs": (15, 16), "db_operator_like": "%FREE%"},
    "bouygues": {
        "label": "Bouygues Telecom",
        "plmn": "20820",
        "mnc": 20,
        "mncs": (20, 88),
        "db_operator_like": "%BOUYGUES%",
    },
}

TECHNO_CODES = {"4G": 4, "5G": 5}
EXPECTED_MCC = 208
# Ligne 1 des fichiers : "4G;999;99;0;0000;0;-1;0.0;0.0;<date debut> / <date des donnees>".
# Ce n'est pas une cellule mais l'en-tete de version, d'ou le MCC sentinelle.
SENTINEL_MCC = 999
NTM_FIELD_COUNT = 10
ADDRESS_MAX_CHARS = 200

MAX_ZIP_BYTES = 32 * 1024 * 1024
MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024
MIN_ROWS_PER_OPERATOR = 500
DEFAULT_MIN_RATIO = 0.5
# « Est-ce bien le fichier de cet operateur ? » se mesure a la dominance de son MNC principal,
# pas a l'absence totale de lignes etrangeres (elles existent, cf. OPERATOR_SOURCES).
MIN_PRIMARY_MNC_RATIO = 0.5
# Un MCC autre que 208 en volume = ce ne sont plus des donnees France metropolitaine.
MAX_FOREIGN_MCC_RATIO = 0.01

DATE_RE = re.compile(r"(\d{2})/(\d{2})/(\d{4})")


class SourceFormatError(RuntimeError):
    """Le fichier telecharge n'a pas la forme attendue : on retombe sur le cache."""


def log(message: str) -> None:
    print(message, flush=True)


def parse_int(value: str | None) -> int | None:
    text = (value or "").strip()
    if not text:
        return None
    try:
        return int(text)
    except ValueError:
        return None


def parse_float(value: str | None) -> float | None:
    text = (value or "").strip().replace(",", ".")
    if not text:
        return None
    try:
        result = float(text)
    except ValueError:
        return None
    return result if result == result else None  # NaN exclu


def clean_address(value: str | None) -> str | None:
    text = (value or "").strip().strip(chr(0xFEFF))
    if not text:
        return None
    text = " ".join(text.split())
    return text[:ADDRESS_MAX_CHARS] if text else None


def last_date_in(value: str | None) -> str | None:
    """Renvoie la derniere date jj/mm/aaaa du texte, au format ISO (date des donnees)."""
    matches = DATE_RE.findall(value or "")
    if not matches:
        return None
    day, month, year = matches[-1]
    try:
        return dt.date(int(year), int(month), int(day)).isoformat()
    except ValueError:
        return None


def decode_text(raw: bytes) -> str:
    for encoding in ("utf-8-sig", "utf-8", "cp1252", "iso-8859-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def download(url: str, timeout_seconds: int) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        payload = response.read(MAX_ZIP_BYTES + 1)
    if len(payload) > MAX_ZIP_BYTES:
        raise SourceFormatError(f"archive > {MAX_ZIP_BYTES} octets")
    if not payload:
        raise SourceFormatError("archive vide")
    return payload


def extract_ntm(raw_zip: bytes) -> bytes:
    """Extrait l'unique membre .ntm de l'archive, avec garde anti-decompression abusive."""
    try:
        with zipfile.ZipFile(io.BytesIO(raw_zip)) as archive:
            members = [info for info in archive.infolist() if not info.is_dir()]
            candidates = [info for info in members if info.filename.lower().endswith(".ntm")] or members
            if len(candidates) != 1:
                raise SourceFormatError(f"archive: {len(candidates)} membre(s) exploitable(s), 1 attendu")
            info = candidates[0]
            if info.file_size > MAX_UNCOMPRESSED_BYTES:
                raise SourceFormatError(f"membre decompresse annonce a {info.file_size} octets")
            with archive.open(info) as handle:
                payload = handle.read(MAX_UNCOMPRESSED_BYTES + 1)
    except zipfile.BadZipFile as exc:
        raise SourceFormatError(f"archive illisible ({exc})") from exc
    if len(payload) > MAX_UNCOMPRESSED_BYTES:
        raise SourceFormatError("membre decompresse trop volumineux")
    if not payload:
        raise SourceFormatError("membre vide")
    return payload


def parse_ntm(raw: bytes, source: dict[str, Any]) -> dict[str, Any]:
    """Parse un fichier .ntm. Leve SourceFormatError si ce n'est pas le fichier de cet operateur.

    Les lignes sont conservees avec **leur vrai MNC** (un fichier peut apporter le code reseau
    secondaire de l'operateur, ex. 208-16 pour Free), et non celui du nom de fichier.
    """
    primary_mnc = int(source["mnc"])
    accepted_mncs = {int(value) for value in source.get("mncs", (primary_mnc,))}
    rows: dict[tuple[int, int, int], tuple] = {}
    source_date: str | None = None
    stats = {
        "lines": 0,
        "plmn_rows": 0,
        "kept": 0,
        "primary_mnc_seen": 0,
        "primary_mnc_rows": 0,
        "secondary_mnc_rows": 0,
        "orphans": 0,
        "duplicates": 0,
        "skipped_fields": 0,
        "skipped_techno": 0,
        "skipped_other_operator": 0,
        "skipped_foreign_mcc": 0,
        "skipped_enb": 0,
        "skipped_coords": 0,
    }

    for raw_line in decode_text(raw).splitlines():
        line = raw_line.strip()
        if not line:
            continue
        stats["lines"] += 1

        parts = line.split(";")
        if len(parts) < NTM_FIELD_COUNT:
            stats["skipped_fields"] += 1
            continue
        if len(parts) > NTM_FIELD_COUNT:
            # Adresse contenant un ';' : on recolle la fin dans le dernier champ.
            parts = parts[: NTM_FIELD_COUNT - 1] + [";".join(parts[NTM_FIELD_COUNT - 1 :])]

        mcc = parse_int(parts[1])
        if mcc == SENTINEL_MCC:
            source_date = source_date or last_date_in(parts[9])
            continue

        if mcc != EXPECTED_MCC:
            stats["skipped_foreign_mcc"] += 1
            continue
        stats["plmn_rows"] += 1

        mnc = parse_int(parts[2])
        # Compte au moment de la decision PLMN, avant tout controle de validite : c'est cette
        # repartition-la qui dit si le fichier est bien celui de cet operateur.
        if mnc == primary_mnc:
            stats["primary_mnc_seen"] += 1
        if mnc is None or mnc not in accepted_mncs:
            # MNC d'un autre operateur, ou -1 (operateur inconnu) : ces lignes n'ont jamais de
            # support et leur source faisant autorite est le fichier de l'operateur concerne.
            stats["skipped_other_operator"] += 1
            continue

        techno = TECHNO_CODES.get(parts[0].strip().upper())
        if techno is None:
            stats["skipped_techno"] += 1
            continue

        enb = parse_int(parts[5])
        if enb is None or enb <= 0:
            stats["skipped_enb"] += 1
            continue

        lat = parse_float(parts[7])
        lon = parse_float(parts[8])
        if lat is None or lon is None or not (-90.0 <= lat <= 90.0) or not (-180.0 <= lon <= 180.0):
            stats["skipped_coords"] += 1
            continue
        if lat == 0.0 and lon == 0.0:
            stats["skipped_coords"] += 1
            continue

        key = (mnc, techno, enb)
        if key in rows:
            stats["duplicates"] += 1
            continue

        support = parse_int(parts[6])
        if support is not None and support <= 0:
            support = None
        # L'adresse n'est stockee que pour les eNB sans support : pour les autres, elle vient
        # de la base ANFR (et l'y dupliquer couterait ~2 Mo).
        address = clean_address(parts[9]) if support is None else None
        if support is None:
            stats["orphans"] += 1
        if mnc == primary_mnc:
            stats["primary_mnc_rows"] += 1
        else:
            stats["secondary_mnc_rows"] += 1

        rows[key] = (
            mnc,
            techno,
            enb,
            support,
            int(round(lat * 1_000_000)),
            int(round(lon * 1_000_000)),
            address,
        )

    stats["kept"] = len(rows)
    if stats["kept"] == 0:
        raise SourceFormatError("aucune ligne exploitable")
    if stats["skipped_foreign_mcc"] > max(10, int(stats["lines"] * MAX_FOREIGN_MCC_RATIO)):
        raise SourceFormatError(
            f"{stats['skipped_foreign_mcc']} ligne(s) hors MCC {EXPECTED_MCC} : ce ne sont pas des donnees FR"
        )
    # Le bon fichier est celui ou le MNC principal domine parmi les lignes qui declarent un PLMN
    # (denominateur volontairement independant des lignes ecartees pour cause de techno/position
    # invalide : un fichier bruite n'est pas un fichier du mauvais operateur).
    if stats["primary_mnc_seen"] < max(1, int(stats["plmn_rows"] * MIN_PRIMARY_MNC_RATIO)):
        raise SourceFormatError(
            f"MNC {primary_mnc} minoritaire "
            f"({stats['primary_mnc_seen']}/{stats['plmn_rows']} lignes PLMN) : mauvais fichier ?"
        )
    return {"rows": list(rows.values()), "source_date": source_date, "stats": stats}


def atomic_write_bytes(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as tmp:
            tmp.write(payload)
            tmp.flush()
            os.fsync(tmp.fileno())
        os.replace(tmp_name, path)
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)


def atomic_write_text(path: Path, text: str) -> None:
    atomic_write_bytes(path, text.encode("utf-8"))


def read_only_uri(path: Path) -> str:
    """URI SQLite lecture seule, portable (les chemins Windows ne passent pas en brut)."""
    return f"{path.resolve().as_uri()}?mode=ro"


def read_previous_sources(output: Path) -> dict[str, dict[str, Any]]:
    """Effectifs par fichier source de la base precedente, pour detecter un effondrement."""
    if not output.is_file():
        return {}
    try:
        conn = sqlite3.connect(read_only_uri(output), uri=True)
    except sqlite3.Error:
        return {}
    try:
        rows = conn.execute("SELECT plmn, source_date, row_count FROM enb_source").fetchall()
    except sqlite3.Error:
        return {}
    finally:
        conn.close()
    return {
        str(plmn): {"source_date": source_date, "row_count": int(row_count or 0)}
        for plmn, source_date, row_count in rows
    }


def create_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;

        CREATE TABLE enb_cell (
            mnc INTEGER NOT NULL,
            techno INTEGER NOT NULL,
            enb INTEGER NOT NULL,
            id_support INTEGER,
            lat_e6 INTEGER NOT NULL,
            lon_e6 INTEGER NOT NULL,
            address TEXT,
            PRIMARY KEY (mnc, techno, enb)
        ) WITHOUT ROWID;

        -- Une ligne par fichier source. `mnc` est le code principal de l'operateur, `mnc_list`
        -- enumere ceux reellement presents dans la base pour lui (ex. "15,16" pour Free) : c'est
        -- ce que l'app doit utiliser pour resoudre operateur -> lignes de enb_cell.
        CREATE TABLE enb_source (
            plmn TEXT NOT NULL PRIMARY KEY,
            mnc INTEGER NOT NULL,
            mnc_list TEXT NOT NULL,
            operator TEXT NOT NULL,
            source_date TEXT,
            row_count INTEGER NOT NULL,
            fetched_at TEXT,
            from_cache INTEGER NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE metadata (
            version TEXT NOT NULL PRIMARY KEY,
            schema_version INTEGER NOT NULL,
            country_code TEXT NOT NULL,
            country_name TEXT,
            source TEXT NOT NULL,
            source_date TEXT,
            generated_at TEXT,
            row_count INTEGER NOT NULL
        ) WITHOUT ROWID;
        """
    )


def compute_version(sources: list[dict[str, Any]]) -> tuple[str, str | None]:
    """Version = <date de donnees la plus recente>-<digest6>.

    Le digest porte sur les couples (PLMN, date source, effectif) : sans lui, un operateur qui
    se rafraichit avec une date anterieure au maximum ne ferait pas bouger la version et les
    clients resteraient silencieusement sur une base perimee.
    """
    dates = sorted(entry["source_date"] for entry in sources if entry.get("source_date"))
    latest = dates[-1] if dates else dt.date.today().isoformat()
    material = "|".join(
        f"{entry['plmn']}:{entry.get('source_date') or '-'}:{entry['row_count']}"
        for entry in sorted(sources, key=lambda item: item["plmn"])
    )
    digest = hashlib.sha256(material.encode("utf-8")).hexdigest()[:6]
    return f"{latest}-{digest}", (dates[-1] if dates else None)


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_database(output: Path, rows: list[tuple], sources: list[dict[str, Any]], generated_at: str) -> dict[str, Any]:
    version, source_date = compute_version(sources)
    output.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = output.parent / f".{output.name}.build.tmp"
    if tmp_path.exists():
        tmp_path.unlink()

    conn = sqlite3.connect(str(tmp_path))
    try:
        create_schema(conn)
        conn.executemany(
            "INSERT INTO enb_cell (mnc, techno, enb, id_support, lat_e6, lon_e6, address) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            rows,
        )
        conn.executemany(
            "INSERT INTO enb_source (plmn, mnc, mnc_list, operator, source_date, row_count, fetched_at, from_cache) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    entry["plmn"],
                    entry["mnc"],
                    entry["mnc_list"],
                    entry["operator"],
                    entry.get("source_date"),
                    entry["row_count"],
                    entry.get("fetched_at"),
                    1 if entry.get("from_cache") else 0,
                )
                for entry in sources
            ],
        )
        conn.execute(
            "INSERT INTO metadata (version, schema_version, country_code, country_name, source, "
            "source_date, generated_at, row_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (
                version,
                SCHEMA_VERSION,
                COUNTRY_CODE,
                COUNTRY_NAME,
                SOURCE,
                source_date,
                generated_at,
                len(rows),
            ),
        )
        conn.execute("CREATE INDEX idx_enb_cell_support ON enb_cell(mnc, id_support)")
        conn.commit()
        conn.execute("VACUUM")
        conn.commit()
    except BaseException:
        conn.close()
        if tmp_path.exists():
            tmp_path.unlink()
        raise
    else:
        conn.close()

    os.replace(tmp_path, output)
    return {"version": version, "source_date": source_date}


def join_quality(db_path: Path, rows: list[tuple]) -> dict[str, Any]:
    """% d'id_support retrouves dans la base ANFR, avec la jointure exacte que fera l'app.

    Canari de format : si ce taux s'effondre d'une semaine a l'autre, la colonne 7 a change de
    sens ou de referentiel.
    """
    result: dict[str, Any] = {}
    conn = sqlite3.connect(read_only_uri(db_path), uri=True)
    try:
        for key, source in OPERATOR_SOURCES.items():
            try:
                cursor = conn.execute(
                    """
                    SELECT DISTINCT s.id_support
                    FROM support s
                    JOIN localisation l ON l.id_anfr = s.id_anfr
                    JOIN ref_operateur o ON o.id = l.operateur_id
                    WHERE UPPER(COALESCE(o.libelle, '')) LIKE ?
                    """,
                    (source["db_operator_like"],),
                )
                known = {value for value in (parse_int(row[0]) for row in cursor) if value is not None}
            except sqlite3.Error as exc:
                result[key] = {"error": str(exc)}
                continue

            accepted_mncs = {int(value) for value in source.get("mncs", (source["mnc"],))}
            attached = [row for row in rows if row[0] in accepted_mncs and row[3] is not None]
            matched = sum(1 for row in attached if row[3] in known)
            result[key] = {
                "db_supports": len(known),
                "attached_rows": len(attached),
                "matched_rows": matched,
                "matched_ratio": round(matched / len(attached), 4) if attached else None,
            }
    finally:
        conn.close()
    return result


def write_version_json(
    version_json: Path, output: Path, version: str, source_date: str | None, rows: int, sources: list[dict[str, Any]]
) -> None:
    payload = {
        "version": version,
        "schema_version": SCHEMA_VERSION,
        "country_code": COUNTRY_CODE,
        "country_name": COUNTRY_NAME,
        "source": SOURCE,
        "source_date": source_date,
        "db_name": output.name,
        "size_bytes": output.stat().st_size,
        "sha256": file_sha256(output),
        "row_count": rows,
        "operators": [
            {
                "plmn": entry["plmn"],
                "mnc": entry["mnc"],
                "mnc_list": entry["mnc_list"],
                "operator": entry["operator"],
                "source_date": entry.get("source_date"),
                "row_count": entry["row_count"],
                "from_cache": bool(entry.get("from_cache")),
            }
            for entry in sources
        ],
    }
    atomic_write_text(version_json, json.dumps(payload, ensure_ascii=False))


def load_operator(
    key: str, source: dict[str, Any], args: argparse.Namespace, cache_dir: Path, previous: dict[str, dict[str, Any]]
) -> tuple[dict[str, Any] | None, str | None]:
    """Renvoie (donnees, erreur). Tente le reseau, retombe sur le cache brut si besoin."""
    cache_path = cache_dir / f"NmEa_{source['plmn']}.ntm"
    url = SOURCE_URL_TEMPLATE.format(plmn=source["plmn"])
    previous_count = previous.get(source["plmn"], {}).get("row_count", 0)
    floor = max(args.min_rows, int(previous_count * args.min_ratio))
    errors: list[str] = []

    if not args.offline:
        try:
            payload = extract_ntm(download(url, args.timeout))
            parsed = parse_ntm(payload, source)
            if parsed["stats"]["kept"] < floor:
                raise SourceFormatError(
                    f"{parsed['stats']['kept']} ligne(s) pour un plancher de {floor} (base precedente : {previous_count})"
                )
            atomic_write_bytes(cache_path, payload)
            log(f"{source['label']}: {parsed['stats']['kept']} eNB/gNB (frais, {parsed['source_date'] or 'date inconnue'})")
            return (
                {
                    "rows": parsed["rows"],
                    "source_date": parsed["source_date"],
                    "stats": parsed["stats"],
                    "from_cache": False,
                    "fetched_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
                },
                None,
            )
        except Exception as exc:  # noqa: BLE001 - toute panne source doit basculer sur le cache
            errors.append(f"telechargement: {exc}")
            log(f"{source['label']}: echec source ({exc}) -> tentative de cache")

    if not cache_path.is_file():
        errors.append("aucun cache disponible")
        return None, " | ".join(errors)

    try:
        parsed = parse_ntm(cache_path.read_bytes(), source)
    except Exception as exc:  # noqa: BLE001 - cache corrompu : l'operateur est manquant
        errors.append(f"cache illisible: {exc}")
        return None, " | ".join(errors)

    fetched_at = dt.datetime.fromtimestamp(cache_path.stat().st_mtime, dt.timezone.utc).replace(microsecond=0).isoformat()
    log(f"{source['label']}: {parsed['stats']['kept']} eNB/gNB (cache du {fetched_at[:10]})")
    return (
        {
            "rows": parsed["rows"],
            "source_date": parsed["source_date"],
            "stats": parsed["stats"],
            "from_cache": True,
            "fetched_at": fetched_at,
        },
        " | ".join(errors) or None,
    )


def build_enb_db(args: argparse.Namespace) -> dict[str, Any]:
    output = Path(args.output)
    cache_dir = Path(args.cache_dir) if args.cache_dir else output.parent / CACHE_DIR_NAME
    generated_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    previous = read_previous_sources(output)
    previous_total = sum(entry["row_count"] for entry in previous.values())

    rows: list[tuple] = []
    sources: list[dict[str, Any]] = []
    per_operator: dict[str, Any] = {}
    errors: dict[str, str] = {}
    missing: list[str] = []

    for key, source in OPERATOR_SOURCES.items():
        loaded, error = load_operator(key, source, args, cache_dir, previous)
        if error:
            errors[key] = error
        if loaded is None:
            missing.append(key)
            continue
        rows.extend(loaded["rows"])
        present_mncs = sorted({row[0] for row in loaded["rows"]})
        sources.append(
            {
                "mnc": source["mnc"],
                "mnc_list": ",".join(str(value) for value in present_mncs),
                "plmn": source["plmn"],
                "operator": source["label"],
                "source_date": loaded["source_date"],
                "row_count": len(loaded["rows"]),
                "fetched_at": loaded["fetched_at"],
                "from_cache": loaded["from_cache"],
            }
        )
        per_operator[key] = {**loaded["stats"], "from_cache": loaded["from_cache"], "source_date": loaded["source_date"]}

    # Garde-fous : ne jamais ecraser une base valide par une sortie amputee.
    if not sources:
        raise RuntimeError(
            "Aucun operateur exploitable (ni reseau ni cache): base inchangee. "
            + " | ".join(f"{k}: {v}" for k, v in errors.items())
        )
    if missing and not args.allow_partial:
        raise RuntimeError(
            f"Operateur(s) manquant(s) ({', '.join(missing)}): base inchangee. "
            "Utiliser --allow-partial pour publier malgre tout."
        )
    if previous_total and len(rows) < previous_total * args.min_ratio and not args.allow_shrink:
        raise RuntimeError(
            f"Effondrement du volume ({len(rows)} lignes contre {previous_total} precedemment): base inchangee. "
            "Utiliser --allow-shrink pour publier malgre tout."
        )

    written = write_database(output, rows, sources, generated_at)

    version_json = Path(args.version_json) if args.version_json else output.parent / DEFAULT_VERSION_FILENAME
    write_version_json(version_json, output, written["version"], written["source_date"], len(rows), sources)

    report: dict[str, Any] = {
        "output": str(output),
        "version": written["version"],
        "source_date": written["source_date"],
        "generated_at": generated_at,
        "bytes": output.stat().st_size,
        "mib": round(output.stat().st_size / 1024 / 1024, 2),
        "row_count": len(rows),
        "orphan_rows": sum(1 for row in rows if row[3] is None),
        "previous_row_count": previous_total,
        "operators": per_operator,
        "missing_operators": missing,
        "errors": errors,
    }
    if args.db:
        db_path = Path(args.db)
        if db_path.is_file():
            report["join_quality"] = join_quality(db_path, rows)
        else:
            report["join_quality"] = {"error": f"base ANFR introuvable: {db_path}"}

    if args.report_output:
        atomic_write_text(Path(args.report_output), json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    return report


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the GeoTower FR eNB/gNB database.")
    parser.add_argument("--output", default=str(IMPORTS_DIR / DEFAULT_DB_FILENAME), help="SQLite output path")
    parser.add_argument("--version-json", help=f"Version JSON path (default: <output dir>/{DEFAULT_VERSION_FILENAME})")
    parser.add_argument("--report-output", help="Optional JSON report path")
    parser.add_argument("--cache-dir", help=f"Raw source cache directory (default: <output dir>/{CACHE_DIR_NAME})")
    parser.add_argument("--db", help="Optional geotower_fr.db path, for the support join-quality metric")
    parser.add_argument("--timeout", type=int, default=60, help="Download timeout in seconds")
    parser.add_argument(
        "--min-ratio",
        type=float,
        default=DEFAULT_MIN_RATIO,
        help="Reject a source below this fraction of its previous row count (default: 0.5)",
    )
    parser.add_argument(
        "--min-rows",
        type=int,
        default=MIN_ROWS_PER_OPERATOR,
        help=f"Absolute row floor per operator (default: {MIN_ROWS_PER_OPERATOR})",
    )
    parser.add_argument("--offline", action="store_true", help="Use the raw cache only, no network access")
    parser.add_argument(
        "--allow-partial", action="store_true", help="Publish even if an operator has neither fresh file nor cache"
    )
    parser.add_argument("--allow-shrink", action="store_true", help="Publish even if the total row count collapsed")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    started = time.time()
    try:
        report = build_enb_db(args)
    except Exception as exc:  # noqa: BLE001 - point d'entree CLI
        print(f"Erreur critique: {exc}", file=sys.stderr)
        return 1

    log(
        "Termine en %.1fs: %s eNB/gNB (%s sans support), version %s, %s Mo"
        % (
            time.time() - started,
            report["row_count"],
            report["orphan_rows"],
            report["version"],
            report["mib"],
        )
    )
    if report.get("join_quality"):
        for key, quality in report["join_quality"].items():
            if isinstance(quality, dict) and quality.get("matched_ratio") is not None:
                log(f"  jointure {key}: {quality['matched_ratio'] * 100:.1f}% des supports retrouves dans la base ANFR")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
