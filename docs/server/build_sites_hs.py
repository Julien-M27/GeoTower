#!/usr/bin/env python3
"""Construit le GeoJSON GeoTower des sites hors service a partir des SEULS fichiers operateurs.

Sources de verite : les fichiers CSV publies par Orange, SFR, Bouygues Telecom et Free.
La base ANFR locale (SQLite) ne sert qu'a completer la station_anfr / les coordonnees
quand l'operateur ne les fournit pas ; ce n'est jamais une source de panne.

L'ancienne dependance ARCEP (fichier agrege sites-indisponibles) a ete retiree :
elle etait indisponible une execution sur deux et bloquait tout le build.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import io
import json
import math
import os
import re
import sqlite3
import sys
import tempfile
import time
import unicodedata
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any


OPERATOR_SOURCES = {
    "free": {
        "label": "Free Mobile",
        "url": "https://mobile.free.fr/static/pannes-antennes-relais.csv",
        "code_columns": ("code_site_op",),
    },
    "sfr": {
        "label": "SFR",
        "url": "https://www.sfr.fr/media/export-arcep/siteshorsservices.csv",
        "code_columns": ("code_site_op",),
    },
    "bouygues": {
        "label": "Bouygues Telecom",
        "url": (
            "https://www.bouyguestelecom.fr/static/com/assets/reseau/"
            "siteshs/downloads/antennesindisponibles.csv"
        ),
        "code_columns": ("code_si",),
    },
    "orange": {
        "label": "Orange",
        "url": (
            "https://couverture-mobile.orange.fr/mapV3/siteshs/data/"
            "Liste_des_antennes_provisoirement_hors_service.csv"
        ),
        "code_columns": ("code_site_op",),
    },
}

STANDARD_FIELDS = (
    "operateur",
    "departement",
    "code_postal",
    "code_insee",
    "commune",
    "station_anfr",
    "voix2g",
    "voix3g",
    "voix4g",
    "voix5g",
    "data2g",
    "data3g",
    "data4g",
    "data5g",
    "voix",
    "data",
    "propre",
    "raison",
    "detail",
    "debut_voix",
    "fin_voix",
    "debut_data",
    "fin_data",
    "debut",
    "fin",
)

# Champs de dates elargis lors d'une fusion de doublons operateur.
_START_DATE_FIELDS = ("debut", "debut_voix", "debut_data")
_END_DATE_FIELDS = ("fin", "fin_voix", "fin_data")

EARTH_RADIUS_M = 6_371_000.0


def log(message: str) -> None:
    print(message, flush=True)


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip().strip(chr(0xFEFF))
    if text == "":
        return None
    if text.lower() in {"null", "none", "nan"}:
        return None
    return text


def strip_accents(value: str) -> str:
    return "".join(
        char
        for char in unicodedata.normalize("NFD", value)
        if unicodedata.category(char) != "Mn"
    )


def normalized_token(value: Any) -> str:
    text = clean_text(value) or ""
    text = strip_accents(text).upper()
    return re.sub(r"[^A-Z0-9]+", "", text)


def normalized_header(value: Any) -> str:
    text = clean_text(value) or ""
    text = strip_accents(text).lower()
    text = re.sub(r"[^a-z0-9]+", "_", text)
    return text.strip("_")


def operator_key(value: Any) -> str | None:
    token = normalized_token(value)
    if "BOUYGUES" in token or token in {"BYTEL", "BT"}:
        return "bouygues"
    if "ORANGE" in token:
        return "orange"
    if "FREE" in token:
        return "free"
    if "SFR" in token:
        return "sfr"
    return None


def parse_float(value: Any) -> float | None:
    text = clean_text(value)
    if not text:
        return None
    try:
        return float(text.replace(",", "."))
    except ValueError:
        return None


def normalize_status(value: Any) -> str | None:
    text = clean_text(value)
    if not text:
        return None
    return strip_accents(text).upper()


def normalize_date(value: Any) -> str | None:
    text = clean_text(value)
    if not text:
        return None
    text = text.replace("T", " ").replace("Z", "").strip()
    formats = (
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
        "%d/%m/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M",
        "%d/%m/%Y",
    )
    for fmt in formats:
        try:
            parsed = dt.datetime.strptime(text, fmt)
            if "%H" in fmt:
                return parsed.strftime("%Y-%m-%d %H:%M:%S")
            return parsed.strftime("%Y-%m-%d")
        except ValueError:
            continue
    return text


def sortable_datetime(value: str | None) -> dt.datetime | None:
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return dt.datetime.strptime(value, fmt)
        except ValueError:
            continue
    return None


def earliest_date(*values: str | None) -> str | None:
    dates = [(sortable_datetime(value), value) for value in values if value]
    parsed = [(parsed, value) for parsed, value in dates if parsed is not None]
    if parsed:
        return min(parsed, key=lambda item: item[0])[1]
    return next((value for _parsed, value in dates), None)


def latest_date(*values: str | None) -> str | None:
    dates = [(sortable_datetime(value), value) for value in values if value]
    parsed = [(parsed, value) for parsed, value in dates if parsed is not None]
    if parsed:
        return max(parsed, key=lambda item: item[0])[1]
    return next((value for _parsed, value in dates), None)


def station_id(value: Any) -> str | None:
    text = clean_text(value)
    if not text:
        return None
    return text.zfill(10) if text.isdigit() else text


def station_id_from_operator_code(value: Any) -> str | None:
    text = clean_text(value)
    if not text:
        return None
    return station_id(text) if re.fullmatch(r"\d{10}", text) else None


def download(url: str, timeout_seconds: int) -> bytes:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "GeoTower outage builder/2.0",
            "Accept": "*/*",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return response.read()


def decode_text(raw: bytes) -> str:
    for encoding in ("utf-8-sig", "utf-8", "cp1252", "iso-8859-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def atomic_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as tmp:
            tmp.write(text)
            tmp.flush()
            os.fsync(tmp.fileno())
        os.replace(tmp_name, path)
    finally:
        if os.path.exists(tmp_name):
            os.unlink(tmp_name)


def csv_header_index(lines: list[str]) -> int:
    for index, line in enumerate(lines):
        normalized = normalized_header(line)
        if "code_site_op" in normalized or "code_si" in normalized:
            return index
    return 0


def read_operator_csv(source_key: str, raw: bytes) -> list[dict[str, str]]:
    text = decode_text(raw)
    lines = text.splitlines()
    header_index = csv_header_index(lines)
    csv_text = "\n".join(lines[header_index:])
    reader = csv.DictReader(io.StringIO(csv_text), delimiter=";")
    rows: list[dict[str, str]] = []
    for row in reader:
        normalized = {normalized_header(key): value for key, value in row.items() if key is not None}
        if any(clean_text(value) for value in normalized.values()):
            rows.append(normalized)
    if not rows:
        raise ValueError(f"CSV {source_key} vide ou non reconnu")
    return rows


def row_get(row: dict[str, Any], *names: str) -> str | None:
    for name in names:
        value = clean_text(row.get(name))
        if value is not None:
            return value
    return None


def own_site_value(value: Any) -> int | None:
    text = normalized_token(value)
    if text in {"OUI", "YES", "1", "TRUE"}:
        return 1
    if text in {"NON", "NO", "0", "FALSE"}:
        return 0
    return None


def free_insee_from_code_site(code_site: str | None) -> str | None:
    if not code_site:
        return None
    prefix = code_site.split("_", 1)[0]
    return prefix if re.fullmatch(r"\d{5}", prefix) else None


def normalize_operator_row(source_key: str, row: dict[str, str]) -> dict[str, Any] | None:
    source = OPERATOR_SOURCES[source_key]
    operator_code = next((row_get(row, column) for column in source["code_columns"]), None)
    lat = parse_float(row_get(row, "lat", "latitude"))
    lon = parse_float(row_get(row, "lon", "lng", "longitude"))
    if lat is None or lon is None:
        return None

    debut_voix = normalize_date(row_get(row, "debut_interruption_voix", "debut_voix"))
    fin_voix = normalize_date(row_get(row, "fin_interruption_voix", "fin_voix"))
    debut_data = normalize_date(row_get(row, "debut_interruption_data", "debut_data"))
    fin_data = normalize_date(row_get(row, "fin_interruption_data", "fin_data"))
    debut = normalize_date(row_get(row, "debut")) or earliest_date(debut_voix, debut_data)
    fin = normalize_date(row_get(row, "fin", "fin_prev")) or latest_date(fin_voix, fin_data)

    code_insee = row_get(row, "code_insee")
    code_postal = row_get(row, "code_postal")
    if source_key == "free":
        code_postal = code_postal or code_insee
        code_insee = free_insee_from_code_site(operator_code)

    explicit_station = station_id(row_get(row, "station_anfr", "id_anfr", "id_station_anfr"))
    properties: dict[str, Any] = {
        "operateur": source["label"],
        "departement": row_get(row, "departement"),
        "code_postal": code_postal,
        "code_insee": code_insee,
        "commune": row_get(row, "commune"),
        "station_anfr": explicit_station or station_id_from_operator_code(operator_code),
        "voix2g": normalize_status(row_get(row, "2gvoix")),
        "voix3g": normalize_status(row_get(row, "3gvoix")),
        "voix4g": normalize_status(row_get(row, "4gvoix")),
        "voix5g": normalize_status(row_get(row, "5gvoix")),
        "data2g": normalize_status(row_get(row, "2gdata")),
        "data3g": normalize_status(row_get(row, "3gdata")),
        "data4g": normalize_status(row_get(row, "4gdata")),
        "data5g": normalize_status(row_get(row, "5gdata")),
        "voix": normalize_status(row_get(row, "voix")),
        "data": normalize_status(row_get(row, "data")),
        "propre": own_site_value(row_get(row, "antenne_relais_geree_par_sfr", "propre")),
        "raison": normalize_status(row_get(row, "raison")),
        "detail": row_get(row, "detail"),
        "debut_voix": debut_voix,
        "fin_voix": fin_voix,
        "debut_data": debut_data,
        "fin_data": fin_data,
        "debut": debut,
        "fin": fin,
        "region": row_get(row, "region"),
        "code_site_op": operator_code,
    }
    return {
        "source_key": source_key,
        "operator_key": source_key,
        "lat": lat,
        "lon": lon,
        "properties": {key: value for key, value in properties.items() if value is not None},
    }


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lon2 - lon1)
    a = math.sin(d_phi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2.0) ** 2
    return 2.0 * EARTH_RADIUS_M * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def grid_key(lat: float, lon: float, size: float) -> tuple[int, int]:
    return math.floor(lat / size), math.floor(lon / size)


def nearby_grid_keys(lat: float, lon: float, size: float, radius: int = 1) -> list[tuple[int, int]]:
    base_lat, base_lon = grid_key(lat, lon, size)
    return [
        (base_lat + d_lat, base_lon + d_lon)
        for d_lat in range(-radius, radius + 1)
        for d_lon in range(-radius, radius + 1)
    ]


class DbSiteIndex:
    """Index spatial de la base ANFR locale, utilise UNIQUEMENT pour geocoder
    (retrouver la station_anfr et/ou des coordonnees) une ligne operateur."""

    def __init__(self, db_path: Path, grid_size: float) -> None:
        self.grid_size = grid_size
        self.by_operator_insee: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
        self.by_operator_grid: dict[tuple[str, int, int], list[dict[str, Any]]] = defaultdict(list)
        self.count = 0
        if db_path.exists():
            self._load(db_path)

    def _load(self, db_path: Path) -> None:
        connection = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        connection.row_factory = sqlite3.Row
        try:
            rows = connection.execute(
                """
                SELECT l.id_anfr, l.latitude, l.longitude, l.code_insee, o.libelle AS operateur
                FROM localisation l
                LEFT JOIN ref_operateur o ON l.operateur_id = o.id
                WHERE l.latitude IS NOT NULL AND l.longitude IS NOT NULL
                """
            )
            for row in rows:
                op_key = operator_key(row["operateur"])
                if not op_key:
                    continue
                site = {
                    "station_anfr": station_id(row["id_anfr"]),
                    "lat": float(row["latitude"]),
                    "lon": float(row["longitude"]),
                    "code_insee": clean_text(row["code_insee"]),
                    "operateur": row["operateur"],
                }
                if site["code_insee"]:
                    self.by_operator_insee[(op_key, site["code_insee"])].append(site)
                grid_lat, grid_lon = grid_key(site["lat"], site["lon"], self.grid_size)
                self.by_operator_grid[(op_key, grid_lat, grid_lon)].append(site)
                self.count += 1
        finally:
            connection.close()

    def best_match(
        self,
        row: dict[str, Any],
        same_insee_threshold_m: float,
        spatial_threshold_m: float,
    ) -> tuple[dict[str, Any], float, str] | None:
        if self.count == 0:
            return None
        props = row["properties"]
        op_key = row["operator_key"]
        lat = row["lat"]
        lon = row["lon"]
        best: tuple[dict[str, Any], float, str] | None = None
        seen: set[str] = set()

        code_insee = clean_text(props.get("code_insee"))
        if code_insee:
            for site in self.by_operator_insee.get((op_key, code_insee), []):
                station = site.get("station_anfr")
                if not station or station in seen:
                    continue
                seen.add(station)
                distance = haversine_m(lat, lon, site["lat"], site["lon"])
                if distance <= same_insee_threshold_m and (best is None or distance < best[1]):
                    best = (site, distance, "db_insee")
            if best is not None:
                return best

        for grid_lat, grid_lon in nearby_grid_keys(lat, lon, self.grid_size, radius=1):
            for site in self.by_operator_grid.get((op_key, grid_lat, grid_lon), []):
                station = site.get("station_anfr")
                if not station or station in seen:
                    continue
                seen.add(station)
                distance = haversine_m(lat, lon, site["lat"], site["lon"])
                if distance <= spatial_threshold_m and (best is None or distance < best[1]):
                    best = (site, distance, "db_spatial")
        return best


def build_operator_feature(
    row: dict[str, Any],
    station: str | None,
    match_type: str | None,
    distance_m: float | None,
) -> dict[str, Any]:
    """Construit la Feature GeoJSON finale a partir d'une ligne operateur normalisee."""
    props = {
        field: row["properties"].get(field)
        for field in STANDARD_FIELDS
        if row["properties"].get(field) is not None
    }
    if station:
        props["station_anfr"] = station
    props["sources"] = [row["source_key"]]
    props["source_operateurs"] = [row["source_key"]]
    if match_type:
        props["source_match"] = match_type
    if distance_m is not None:
        props["source_match_distance_m"] = round(distance_m, 1)
    region = row["properties"].get("region")
    if region:
        props["region"] = region
    operator_code = row["properties"].get("code_site_op")
    if operator_code:
        props["code_site_op"] = operator_code
        props["codes_site_op"] = [operator_code]
    return {
        "type": "Feature",
        "properties": props,
        "geometry": {"type": "Point", "coordinates": [row["lon"], row["lat"]]},
    }


def merge_duplicate_row(feature: dict[str, Any], row: dict[str, Any]) -> None:
    """Fusionne une seconde ligne du meme operateur ciblant le meme site.

    La premiere ligne fait foi pour les statuts ; on comble seulement les trous et on
    elargit la fenetre de dates (debut le plus tot, fin le plus tard)."""
    props = feature["properties"]
    incoming = row["properties"]
    for field in STANDARD_FIELDS:
        value = incoming.get(field)
        if value is None:
            continue
        if field in _START_DATE_FIELDS:
            props[field] = earliest_date(props.get(field), value) or value
        elif field in _END_DATE_FIELDS:
            props[field] = latest_date(props.get(field), value) or value
        elif props.get(field) is None:
            props[field] = value
    operator_code = incoming.get("code_site_op")
    if operator_code:
        codes = props.setdefault("codes_site_op", [])
        if operator_code not in codes:
            codes.append(operator_code)
        props.setdefault("code_site_op", operator_code)


def dedup_key(source_key: str, station: str | None, row: dict[str, Any]) -> tuple:
    if station:
        return ("station", source_key, station)
    operator_code = clean_text(row["properties"].get("code_site_op"))
    if operator_code:
        return ("code", source_key, operator_code)
    return ("pos", source_key, round(row["lat"], 5), round(row["lon"], 5))


def cleanup_properties(feature: dict[str, Any]) -> None:
    props = feature.get("properties")
    if not isinstance(props, dict):
        feature["properties"] = {}
        return
    for field in STANDARD_FIELDS:
        if field in props and props[field] == "":
            props[field] = None
    if "source_operateurs" in props and not props["source_operateurs"]:
        props.pop("source_operateurs", None)


def build_sites_hs(args: argparse.Namespace) -> dict[str, Any]:
    source_date = dt.date.fromisoformat(args.date) if args.date else dt.date.today()
    generated_at = (
        dt.datetime.now(dt.timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z")
    )

    db_index = DbSiteIndex(Path(args.db), args.grid_size) if args.db else None
    if db_index is not None:
        log(f"DB locale: {db_index.count} sites charge(s)")

    stats: dict[str, Any] = {
        "generated_at": generated_at,
        "source_date": source_date.isoformat(),
        "operator_rows": {},
        "operator_station_from_operator": defaultdict(int),
        "operator_station_from_db": defaultdict(int),
        "operator_without_station": defaultdict(int),
        "operator_duplicates_merged": defaultdict(int),
        "operator_download_errors": {},
        "db_sites_loaded": db_index.count if db_index is not None else 0,
    }

    features: list[dict[str, Any]] = []
    index: dict[tuple, dict[str, Any]] = {}

    for source_key, source in OPERATOR_SOURCES.items():
        try:
            raw_csv = download(source["url"], args.timeout)
            rows = read_operator_csv(source_key, raw_csv)
        except Exception as exc:  # noqa: BLE001 - on veut journaliser tout echec source
            stats["operator_download_errors"][source_key] = str(exc)
            log(f"{source['label']}: erreur de telechargement/lecture ({exc})")
            continue

        normalized_rows = [
            normalized
            for row in rows
            if (normalized := normalize_operator_row(source_key, row)) is not None
        ]
        stats["operator_rows"][source_key] = len(normalized_rows)
        log(f"{source['label']}: {len(normalized_rows)} ligne(s) exploitable(s)")

        for row in normalized_rows:
            station = station_id(row["properties"].get("station_anfr"))
            match_type: str | None
            distance_m: float | None = None
            if station:
                match_type = "operator"
                stats["operator_station_from_operator"][source_key] += 1
            else:
                match_type = None
                db_match = (
                    db_index.best_match(
                        row,
                        same_insee_threshold_m=args.db_same_insee_match_m,
                        spatial_threshold_m=args.db_spatial_match_m,
                    )
                    if db_index is not None
                    else None
                )
                if db_match is not None:
                    site, distance_m, match_type = db_match
                    station = site["station_anfr"]
                    stats["operator_station_from_db"][source_key] += 1
                else:
                    stats["operator_without_station"][source_key] += 1

            key = dedup_key(source_key, station, row)
            existing = index.get(key)
            if existing is not None:
                merge_duplicate_row(existing, row)
                stats["operator_duplicates_merged"][source_key] += 1
                continue

            feature = build_operator_feature(row, station, match_type, distance_m)
            features.append(feature)
            index[key] = feature

    # Garde-fou : ne jamais ecraser une sortie valide avec une sortie partielle/vide.
    downloaded = [key for key in OPERATOR_SOURCES if key not in stats["operator_download_errors"]]
    if not downloaded:
        raise RuntimeError(
            "Aucun fichier operateur accessible: sortie inchangee ("
            + " | ".join(f"{k}: {v}" for k, v in stats["operator_download_errors"].items())
            + ")"
        )
    if stats["operator_download_errors"] and not args.allow_partial:
        failed = ", ".join(stats["operator_download_errors"])
        raise RuntimeError(
            f"Fichier(s) operateur inaccessible(s) ({failed}); sortie inchangee. "
            "Utiliser --allow-partial pour publier malgre tout."
        )
    if not features:
        raise RuntimeError("Aucune panne operateur exploitable: sortie inchangee.")

    for feature in features:
        cleanup_properties(feature)

    output = {
        "type": "FeatureCollection",
        "metadata": {
            "generator": "GeoTower sites HS operator-only builder",
            "generated_at": generated_at,
            "source_date": source_date.isoformat(),
            "sources": {key: source["url"] for key, source in OPERATOR_SOURCES.items()},
        },
        "features": features,
    }

    atomic_write_text(
        Path(args.output),
        json.dumps(output, ensure_ascii=False, separators=(",", ":")),
    )
    atomic_write_text(Path(args.date_output), source_date.isoformat() + "\n")

    stats["output_features"] = len(features)
    report = json.dumps(stats, ensure_ascii=False, indent=2, default=dict) + "\n"
    if args.report_output:
        atomic_write_text(Path(args.report_output), report)
    return stats


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a GeoTower sites HS GeoJSON file from operator files only."
    )
    parser.add_argument("--output", required=True, help="GeoJSON output path")
    parser.add_argument("--date-output", required=True, help="Last update date output path")
    parser.add_argument("--report-output", help="Optional JSON report path")
    parser.add_argument("--db", help="Optional GeoTower SQLite DB path for operator geocoding")
    parser.add_argument("--date", help="Source date YYYY-MM-DD stamped in the output, defaults to today")
    parser.add_argument("--timeout", type=int, default=30, help="Download timeout in seconds")
    parser.add_argument("--grid-size", type=float, default=0.01, help="Spatial index cell size in degrees")
    # Seuils de geocodage DB elargis : les coordonnees des CSV operateurs (surtout Orange)
    # sont souvent decalees de 200-400 m par rapport au site ANFR reel. 600/400 rattache
    # ~1/3 des pannes non geolocalisees sans fusion notable ; au-dela (~700 m+) on commence
    # a rattacher au site voisin (faux match). Le reste s'affiche via ses coordonnees brutes.
    parser.add_argument("--db-same-insee-match-m", type=float, default=600.0)
    parser.add_argument("--db-spatial-match-m", type=float, default=400.0)
    parser.add_argument(
        "--allow-partial",
        action="store_true",
        help="Publish even if some operator files failed to download (default: keep previous output).",
    )
    # Arguments ARCEP obsoletes : acceptes mais IGNORES pour ne pas casser un cron existant
    # qui les passerait encore. L'ARCEP n'est plus une source (voir docstring du module).
    parser.add_argument("--raw-output", help=argparse.SUPPRESS)
    parser.add_argument("--arcep-fallback-days", type=int, help=argparse.SUPPRESS)
    parser.add_argument("--arcep-same-insee-match-m", type=float, help=argparse.SUPPRESS)
    parser.add_argument("--arcep-spatial-match-m", type=float, help=argparse.SUPPRESS)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    started = time.time()
    try:
        stats = build_sites_hs(args)
    except Exception as exc:  # noqa: BLE001 - point d'entree CLI
        print(f"Erreur critique: {exc}", file=sys.stderr)
        return 1

    elapsed = time.time() - started
    log(
        "Termine en %.1fs: %s feature(s), station via DB=%s, sans station=%s"
        % (
            elapsed,
            stats["output_features"],
            dict(stats["operator_station_from_db"]),
            dict(stats["operator_without_station"]),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
