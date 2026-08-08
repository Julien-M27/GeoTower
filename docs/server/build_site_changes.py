#!/usr/bin/env python3
"""Historique hebdomadaire des changements ANFR (observatoire).

Compare la publication courante de l'observatoire ANFR a la precedente et ecrit
deux fichiers par publication :

    weeks/<date>.jsonl.gz     diff exhaustif, une ligne par station changee
    map/<date>.map.json.gz    un point par support, codes de changement

Specification : docs/agent-ia-plan-historique-changements-anfr-2026-08-07.md

Points structurants :

* L'historique n'est PAS retroactif. Seules deux publications sont conservees ;
  les fichiers de `weeks/` sont donc la seule memoire longue et portent les
  valeurs BRUTES avant/apres.
* Cle d'une ligne = (sta_nm_anfr, adm_lb_nom, emr_lb_systeme). L'observatoire n'a
  pas d'identifiant de ligne stable ; la colonne `id` est suivie comme une colonne
  ordinaire mais ne sert jamais de cle.
* Toutes les colonnes sont suivies, y compris celles ajoutees plus tard par l'ANFR.
* Le fichier carte ne contient que des CODES, jamais de phrase : le popup de l'app
  doit exister en 7 langues.
* Conçu pour un cron QUOTIDIEN : sortie immediate tant que la version des donnees
  n'a pas change (l'ANFR ne publie pas un jour fixe).

Aucune dependance hors bibliotheque standard.

    python3 build_site_changes.py
    python3 build_site_changes.py --dry-run
    python3 build_site_changes.py --bootstrap-from /chemin/publication_precedente.csv
"""

from __future__ import annotations

import argparse
import codecs
import contextlib
import csv
import gzip
import hashlib
import io
import json
import math
import os
import re
import shutil
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

# --- Emplacements (= serveur de production) ---------------------------------

IMPORTS_DIR = Path("/opt/geotower/data/imports")
HISTORY_DIR = Path("/opt/geotower/data/history")

# Sous-dossier ou le build de base cherche ses sources (cf. fr_anfr_stats.py).
FRANCE_SOURCES_DIRNAME = "france_sources"

SOURCES_DIRNAME = "sources"
INCOMING_DIRNAME = "incoming"
WEEKS_DIRNAME = "weeks"
MAP_DIRNAME = "map"
STATE_FILENAME = "state.json"

# --- Source ANFR ------------------------------------------------------------

OBSERVATOIRE_EXPORT_PAGE_URL = (
    "https://data.anfr.fr/explore/dataset/observatoire_2g_3g_4g/export/"
)
# Le nom du CSV porte une date : 20260806174318_observatoireod_20260806.csv
OBSERVATOIRE_CSV_REGEX = re.compile(
    r"data\.anfr\.fr\\?/sites\\?/default\\?/files\\?/dataset\\?/\d+_observatoireod_\d+\.csv",
    re.IGNORECASE,
)
ALLOWED_HOST = "data.anfr.fr"
USER_AGENT = "GeoTower-SiteChanges/1.0"
PAGE_TIMEOUT_S = 60
DOWNLOAD_TIMEOUT_S = 900
MAX_DOWNLOAD_BYTES = 600 * 1024 * 1024

CSV_DELIMITER = ";"

# --- Garde-fous -------------------------------------------------------------

MAX_STATION_LOSS_RATIO = 0.05
MIN_ROWS = 100_000
GAP_WARN_DAYS = 10
MOVE_THRESHOLD_M = 5.0

# --- Colonnes ---------------------------------------------------------------
# Seules les colonnes de STRUCTURE sont nommees. Toutes les autres sont suivies
# automatiquement : une colonne ajoutee par l'ANFR doit apparaitre dans le diff
# sans modification du code.

COLUMN_ALIASES = {
    "station": ("sta_nm_anfr", "id_anfr", "station_anfr"),
    "operator": ("adm_lb_nom", "operateur", "operator"),
    "system": ("emr_lb_systeme", "systeme", "system"),
    "support": ("sup_id", "id_support", "support_id"),
    "coords": ("coordonnees", "coord"),
    "dept": ("sta_nm_dpt", "departement", "dept"),
    "insee": ("code_insee", "com_cd_insee"),
    "status": ("statut", "status"),
}
REQUIRED_ROLES = ("station", "operator", "system")

# Colonnes d'EXPORT, pas de donnee site : l'ANFR les reecrit a chaque
# publication. Mesure du 2026-08-07 sur deux paliers reels : `date_maj` change
# sur 100 % des lignes survivantes, `id` sur 99 %. Les comparer ferait apparaitre
# le parc entier (138 000 stations) comme modifie chaque semaine, noierait les
# quelques milliers de vrais changements et rendrait la timeline par site
# inutilisable. Elles restent presentes dans les lignes `add`/`del`, qui sont des
# instantanes complets.
VOLATILE_COLUMNS = {"id", "date_maj"}

# Codes du fichier carte. Aucune phrase : l'app fabrique le texte localise.
CODE_SITE_ADDED = "SITE_ADDED"
CODE_SITE_REMOVED = "SITE_REMOVED"
CODE_OPERATOR_ADDED = "OPERATOR_ADDED"
CODE_OPERATOR_REMOVED = "OPERATOR_REMOVED"
CODE_SYSTEM_ADDED = "SYSTEM_ADDED"
CODE_SYSTEM_REMOVED = "SYSTEM_REMOVED"
CODE_STATUS_CHANGED = "STATUS_CHANGED"
CODE_MOVED = "MOVED"

HASH_MASK = (1 << 64) - 1

WHITESPACE_RE = re.compile(r"\s+")
NUMBER_RE = re.compile(r"[-+]?(?:\d+(?:[\.,]\d*)?|[\.,]\d+)")
DATA_DATE_RE = re.compile(r"_observatoireod_(\d{8})", re.IGNORECASE)
LEADING_DATE_RE = re.compile(r"^(\d{8})")


class SourceError(Exception):
    """Source inexploitable : le script refuse d'ecrire plutot que d'inventer."""


def log(message: str) -> None:
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}", flush=True)


# --- Normalisation ----------------------------------------------------------


def clean_text(value) -> str:
    """Nettoyage minimal : BOM, guillemets d'encadrement, espaces redondants."""
    if value is None:
        return ""
    text = str(value).replace("﻿", "").strip()
    if len(text) >= 2 and text[0] == text[-1] and text[0] in "\"'":
        text = text[1:-1].strip()
    return WHITESPACE_RE.sub(" ", text)


def norm(value) -> str:
    """Valeur de COMPARAISON : casse ignoree. Tout le reste (accent, format de
    date, libelle reformule) reste un changement, simplement classe en priorite
    basse. On ne masque que le bruit indiscutable."""
    return clean_text(value).casefold()


def parse_coordinates(raw):
    """(lat, lon) ou None. Contrairement au builder de base, on renvoie None
    plutot que (0.0, 0.0) : un point a Null Island polluerait la carte."""
    matches = NUMBER_RE.findall(clean_text(raw))
    if len(matches) < 2:
        return None
    try:
        lat = float(matches[0].replace(",", "."))
        lon = float(matches[1].replace(",", "."))
    except ValueError:
        return None
    if not (-90.0 <= lat <= 90.0) or not (-180.0 <= lon <= 180.0):
        return None
    if lat == 0.0 and lon == 0.0:
        return None
    return lat, lon


def distance_m(first, second):
    """Distance en metres entre deux points, None si l'un des deux manque."""
    if not first or not second:
        return None
    lat1, lon1 = first
    lat2, lon2 = second
    radius = 6371008.8
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = phi2 - phi1
    d_lambda = math.radians(lon2 - lon1)
    inner = (
        math.sin(d_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    )
    return 2 * radius * math.asin(min(1.0, math.sqrt(inner)))


def extract_data_date(name: str) -> str:
    """AAAAMMJJ des DONNEES, depuis le nom de fichier. Jamais le mtime : data.anfr
    republie parfois d'anciens exports, et un tri sur la date de depot choisirait
    alors un fichier perime (piege deja rencontre cote app, cf. dataDateKey)."""
    base = Path(name).name
    match = DATA_DATE_RE.search(base)
    if match:
        return match.group(1)
    match = LEADING_DATE_RE.match(base)
    if match and match.group(1).startswith(("19", "20")):
        return match.group(1)
    return ""


def pretty_date(data_date: str) -> str:
    """20260806 -> 2026-08-06 (nom des fichiers de sortie)."""
    if len(data_date) == 8 and data_date.isdigit():
        return f"{data_date[0:4]}-{data_date[4:6]}-{data_date[6:8]}"
    return data_date or "inconnue"


def days_between(older: str, newer: str):
    try:
        first = datetime.strptime(older, "%Y%m%d")
        second = datetime.strptime(newer, "%Y%m%d")
    except (ValueError, TypeError):
        return None
    return (second - first).days


# --- Lecture CSV ------------------------------------------------------------


def open_binary(path: Path):
    path = Path(path)
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rb")
    return open(path, "rb")


def sniff_encoding(path: Path) -> str:
    """L'observatoire est en UTF-8 avec BOM, mais d'anciens exports sont en
    cp1252. On teste sur un echantillon plutot que d'echouer a 90 % d'un
    fichier de 180 Mo."""
    with open_binary(path) as handle:
        head = handle.read(65536)
    for encoding in ("utf-8-sig", "cp1252", "latin-1"):
        # final=False : une sequence multi-octets coupee par la fin de
        # l'echantillon ne doit pas faire conclure au mauvais encodage.
        decoder = codecs.getincrementaldecoder(encoding)()
        try:
            decoder.decode(head, False)
            return encoding
        except UnicodeDecodeError:
            continue
    return "latin-1"


@contextlib.contextmanager
def csv_source(path: Path):
    """Cede (header normalise, reader). Le header est en minuscules."""
    encoding = sniff_encoding(path)
    stream = io.TextIOWrapper(
        open_binary(path), encoding=encoding, errors="replace", newline=""
    )
    try:
        reader = csv.reader(stream, delimiter=CSV_DELIMITER)
        raw_header = next(reader, None)
        if not raw_header:
            raise SourceError(f"{Path(path).name} : fichier vide ou sans en-tete")
        header = [clean_text(name).lower() for name in raw_header]
        yield header, reader
    finally:
        stream.close()


def resolve_columns(header):
    lookup = {name: index for index, name in enumerate(header)}
    columns = {}
    for role, aliases in COLUMN_ALIASES.items():
        for alias in aliases:
            if alias in lookup:
                columns[role] = lookup[alias]
                break
    missing = [role for role in REQUIRED_ROLES if role not in columns]
    if missing:
        raise SourceError(
            "colonnes d'identite absentes de l'en-tete : " + ", ".join(missing)
        )
    return columns


def cell(row, index) -> str:
    if index is None or index >= len(row):
        return ""
    return row[index]


def row_to_dict(header, row):
    values = {}
    for index, name in enumerate(header):
        values[name] = clean_text(row[index]) if index < len(row) else ""
    return values


# --- Publication ------------------------------------------------------------


class Publication:
    """Une publication de l'observatoire, lue en trois passes.

    Les deux premieres passes ne gardent que des EMPREINTES (entiers), pas les
    valeurs : un fichier de 180 Mo ne tient pas en memoire sous forme de dict de
    dicts. Seules les lignes dont la cle a bouge sont relues en clair.

    Les empreintes utilisent hash() : c'est volontaire (bien plus rapide que
    hashlib) et sans danger tant que les trois passes tournent dans LE MEME
    processus, ce qui est le cas ici. Ne jamais persister ces valeurs.
    """

    def __init__(self, path: Path, data_date: str = ""):
        self.path = Path(path)
        self.data_date = data_date or extract_data_date(self.path.name)
        self.header = []
        self.columns = {}
        self.keys = {}
        self.stations = set()
        self.station_operators = set()
        self.rows = 0
        self.skipped = 0

    def scan(self) -> None:
        with csv_source(self.path) as (header, reader):
            self.header = header
            self.columns = resolve_columns(header)
            i_station = self.columns["station"]
            i_operator = self.columns["operator"]
            i_system = self.columns["system"]
            volatile = {
                index for index, name in enumerate(header) if name in VOLATILE_COLUMNS
            }
            keys = self.keys
            for row in reader:
                station = norm(cell(row, i_station))
                if not station:
                    self.skipped += 1
                    continue
                operator = norm(cell(row, i_operator))
                system = norm(cell(row, i_system))
                key = hash((station, operator, system)) & HASH_MASK
                # Empreinte SANS les colonnes d'export, sinon toutes les cles
                # seraient "changees" et la troisieme passe relirait le fichier
                # entier en clair.
                digest = hash(
                    tuple(
                        norm(value)
                        for index, value in enumerate(row)
                        if index not in volatile
                    )
                ) & HASH_MASK
                previous = keys.get(key)
                # Somme, pas XOR : deux lignes identiques ne doivent pas
                # s'annuler mutuellement.
                keys[key] = digest if previous is None else (previous + digest) & HASH_MASK
                self.stations.add(hash(station) & HASH_MASK)
                self.station_operators.add(hash((station, operator)) & HASH_MASK)
                self.rows += 1

    def collect(self, wanted):
        """Relit le fichier et ne retient que les lignes des cles demandees."""
        found = {}
        if not wanted:
            return found
        with csv_source(self.path) as (header, reader):
            columns = resolve_columns(header)
            i_station = columns["station"]
            i_operator = columns["operator"]
            i_system = columns["system"]
            for row in reader:
                station = norm(cell(row, i_station))
                if not station:
                    continue
                operator = norm(cell(row, i_operator))
                system = norm(cell(row, i_system))
                key = hash((station, operator, system)) & HASH_MASK
                if key in wanted:
                    found.setdefault(key, []).append(row_to_dict(header, row))
        return found

    def has_station(self, station_value: str) -> bool:
        return (hash(norm(station_value)) & HASH_MASK) in self.stations

    def has_station_operator(self, station_value: str, operator_value: str) -> bool:
        pair = (norm(station_value), norm(operator_value))
        return (hash(pair) & HASH_MASK) in self.station_operators


# --- Diff -------------------------------------------------------------------


def changed_keys(previous: Publication, current: Publication):
    changed = set()
    for key, digest in current.keys.items():
        if previous.keys.get(key) != digest:
            changed.add(key)
    for key in previous.keys:
        if key not in current.keys:
            changed.add(key)
    return changed


def row_signature(row):
    return tuple(
        sorted(
            (name, norm(value))
            for name, value in row.items()
            if name not in VOLATILE_COLUMNS
        )
    )


def diff_one_key(old_rows, new_rows):
    """Changements pour une meme cle (station, operateur, systeme).

    Cas normal : une ligne de chaque cote -> comparaison champ par champ.
    Cas multiple : l'observatoire peut porter plusieurs lignes pour un meme
    triplet. On annule alors les lignes identiques et on declare le reste en
    ajout/retrait, plutot que d'inventer un appariement.
    """
    if not old_rows:
        return [{"op": "add", "row": row} for row in new_rows]
    if not new_rows:
        return [{"op": "del", "row": row} for row in old_rows]

    if len(old_rows) == 1 and len(new_rows) == 1:
        before, after = old_rows[0], new_rows[0]
        fields = {}
        for column in sorted((set(before) | set(after)) - VOLATILE_COLUMNS):
            old_value = before.get(column, "")
            new_value = after.get(column, "")
            if norm(old_value) != norm(new_value):
                fields[column] = [old_value, new_value]
        if not fields:
            return []
        return [{"op": "upd", "row": after, "old_row": before, "fields": fields}]

    buckets = {}
    for row in old_rows:
        buckets.setdefault(row_signature(row), []).append(row)
    changes = []
    for row in new_rows:
        bucket = buckets.get(row_signature(row))
        if bucket:
            bucket.pop()
        else:
            changes.append({"op": "add", "row": row})
    for bucket in buckets.values():
        for row in bucket:
            changes.append({"op": "del", "row": row})
    return changes


def value_of(row, header, columns, role):
    """Valeur d'un role dans une ligne deja convertie en dict {colonne: valeur}."""
    index = columns.get(role)
    if index is None or index >= len(header):
        return ""
    return row.get(header[index], "")


def station_dept(row, header, columns):
    dept = clean_text(value_of(row, header, columns, "dept"))
    if dept:
        return dept
    insee = clean_text(value_of(row, header, columns, "insee"))
    if len(insee) >= 2:
        # 97xxx / 98xxx = outre-mer, code departement sur 3 caracteres.
        return insee[:3] if insee[:2] in ("97", "98") else insee[:2]
    return ""


def build_diff(previous: Publication, current: Publication):
    """Retourne (stations, stats). `stations` est la liste des objets JSONL."""
    keys = changed_keys(previous, current)
    old_rows = previous.collect(keys)
    new_rows = current.collect(keys)

    grouped = {}
    for source, rows_by_key in (("old", old_rows), ("new", new_rows)):
        header = previous.header if source == "old" else current.header
        columns = previous.columns if source == "old" else current.columns
        for key, rows in rows_by_key.items():
            for row in rows:
                station = clean_text(value_of(row, header, columns, "station"))
                if not station:
                    continue
                entry = grouped.setdefault(
                    station, {"old": {}, "new": {}, "old_any": None, "new_any": None}
                )
                entry[source].setdefault(key, []).append(row)
                if entry[f"{source}_any"] is None:
                    entry[f"{source}_any"] = row

    stations = []
    stats = {
        "codes": {},
        "fields": {},
        "rows_added": 0,
        "rows_removed": 0,
        "rows_updated": 0,
        "multi_row_keys": 0,
    }

    for station, entry in sorted(grouped.items()):
        reference = entry["new_any"] or entry["old_any"]
        from_new = entry["new_any"] is not None
        header = current.header if from_new else previous.header
        columns = current.columns if from_new else previous.columns

        support = clean_text(value_of(reference, header, columns, "support"))
        dept = station_dept(reference, header, columns)
        point = parse_coordinates(value_of(reference, header, columns, "coords"))
        if point is None and entry["old_any"] is not None:
            # Site disparu : ses coordonnees n'existent plus que dans l'ancienne
            # publication. Si on ne les reprend pas ici, ce site n'aura JAMAIS de
            # point sur la carte.
            point = parse_coordinates(
                value_of(entry["old_any"], previous.header, previous.columns, "coords")
            )

        station_added = not previous.has_station(station) and current.has_station(station)
        station_removed = previous.has_station(station) and not current.has_station(station)

        changes = []
        codes = []
        seen_codes = set()

        def add_code(code, **payload):
            signature = (code,) + tuple(sorted(payload.items()))
            if signature in seen_codes:
                return
            seen_codes.add(signature)
            entry_code = {"c": code}
            entry_code.update(payload)
            codes.append(entry_code)
            stats["codes"][code] = stats["codes"].get(code, 0) + 1

        all_keys = set(entry["old"]) | set(entry["new"])
        for key in all_keys:
            before_rows = entry["old"].get(key, [])
            after_rows = entry["new"].get(key, [])
            if len(before_rows) > 1 or len(after_rows) > 1:
                stats["multi_row_keys"] += 1
            for change in diff_one_key(before_rows, after_rows):
                row = change["row"]
                row_header = current.header if change["op"] != "del" else previous.header
                row_columns = current.columns if change["op"] != "del" else previous.columns
                operator = clean_text(value_of(row, row_header, row_columns, "operator"))
                system = clean_text(value_of(row, row_header, row_columns, "system"))

                if change["op"] == "add":
                    stats["rows_added"] += 1
                    changes.append({"key": [operator, system], "op": "add", "v": row})
                    if not station_added:
                        if not previous.has_station_operator(station, operator):
                            add_code(CODE_OPERATOR_ADDED, op=operator)
                        else:
                            add_code(CODE_SYSTEM_ADDED, op=operator, sys=system)
                elif change["op"] == "del":
                    stats["rows_removed"] += 1
                    changes.append({"key": [operator, system], "op": "del", "v": row})
                    if not station_removed:
                        if not current.has_station_operator(station, operator):
                            add_code(CODE_OPERATOR_REMOVED, op=operator)
                        else:
                            add_code(CODE_SYSTEM_REMOVED, op=operator, sys=system)
                else:
                    stats["rows_updated"] += 1
                    fields = change["fields"]
                    for column in fields:
                        stats["fields"][column] = stats["fields"].get(column, 0) + 1
                    changes.append(
                        {"key": [operator, system], "op": "upd", "f": fields}
                    )
                    status_column = column_name(current.header, current.columns, "status")
                    if status_column and status_column in fields:
                        add_code(
                            CODE_STATUS_CHANGED,
                            op=operator,
                            sys=system,
                            was=fields[status_column][0],
                            now=fields[status_column][1],
                        )
                    coords_column = column_name(current.header, current.columns, "coords")
                    if coords_column and coords_column in fields:
                        moved = distance_m(
                            parse_coordinates(fields[coords_column][0]),
                            parse_coordinates(fields[coords_column][1]),
                        )
                        if moved is not None and moved > MOVE_THRESHOLD_M:
                            add_code(CODE_MOVED, m=round(moved))

        if not changes:
            continue

        if station_added:
            codes = []
            seen_codes = set()
            stats["codes"][CODE_SITE_ADDED] = stats["codes"].get(CODE_SITE_ADDED, 0) + 1
            codes.append({"c": CODE_SITE_ADDED, "ops": operators_of(changes)})
        elif station_removed:
            codes = []
            seen_codes = set()
            stats["codes"][CODE_SITE_REMOVED] = (
                stats["codes"].get(CODE_SITE_REMOVED, 0) + 1
            )
            codes.append({"c": CODE_SITE_REMOVED, "ops": operators_of(changes)})

        record = {"sta": station}
        if station_added:
            record["op"] = "add"
        elif station_removed:
            record["op"] = "del"
        if support:
            record["sup"] = support
        if point:
            record["lat"] = round(point[0], 6)
            record["lon"] = round(point[1], 6)
        if dept:
            record["dept"] = dept
        record["chg"] = changes
        record["codes"] = codes
        stations.append(record)

    return stations, stats


def column_name(header, columns, role):
    index = columns.get(role)
    if index is None or index >= len(header):
        return None
    return header[index]


def operators_of(changes):
    operators = []
    for change in changes:
        operator = change["key"][0]
        if operator and operator not in operators:
            operators.append(operator)
    return operators


def build_map_points(stations):
    """Un point par support. Les codes portent la station concernee, pour que le
    popup puisse nommer ce qui a bouge sur un pylone mutualise."""
    points = {}
    for station in stations:
        if not station.get("codes"):
            continue
        support = station.get("sup", "")
        lat = station.get("lat")
        lon = station.get("lon")
        if support:
            group = f"S{support}"
        elif lat is not None and lon is not None:
            # sup_id vide : repli geographique, sinon le point serait perdu.
            group = f"@{lat:.5f},{lon:.5f}"
        else:
            group = f"?{station['sta']}"
        point = points.get(group)
        if point is None:
            point = {"sup": support or None, "chg": []}
            if lat is not None and lon is not None:
                point["lat"] = lat
                point["lon"] = lon
            if station.get("dept"):
                point["dept"] = station["dept"]
            points[group] = point
        elif "lat" not in point and lat is not None:
            point["lat"] = lat
            point["lon"] = lon
        for code in station["codes"]:
            enriched = {"sta": station["sta"]}
            enriched.update(code)
            point["chg"].append(enriched)
    return [points[key] for key in sorted(points)]


# --- Ecriture ---------------------------------------------------------------


def atomic_write_bytes(path: Path, payload: bytes) -> None:
    temporary = Path(str(path) + ".tmp")
    with open(temporary, "wb") as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def write_weeks_file(path: Path, header_object, stations) -> None:
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode="wb", mtime=0) as archive:
        writer = io.TextIOWrapper(archive, encoding="utf-8", newline="\n")
        writer.write(json.dumps(header_object, ensure_ascii=False) + "\n")
        for station in stations:
            # Les codes sont conserves ici aussi : l'archive doit se suffire a
            # elle-meme, sinon regenerer un fichier carte plus tard exigerait de
            # relire des publications qu'on n'aura plus.
            writer.write(json.dumps(station, ensure_ascii=False) + "\n")
        writer.flush()
        writer.detach()
    atomic_write_bytes(path, buffer.getvalue())


def write_map_file(path: Path, meta, points) -> None:
    payload = dict(meta)
    payload["points"] = points
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode="wb", mtime=0) as archive:
        archive.write(json.dumps(payload, ensure_ascii=False).encode("utf-8"))
    atomic_write_bytes(path, buffer.getvalue())


# --- Etat -------------------------------------------------------------------


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_state(state_path: Path):
    if not state_path.is_file():
        return {}
    try:
        with open(state_path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        log("state.json illisible : il sera reecrit")
        return {}


def save_state(state_path: Path, state) -> None:
    atomic_write_bytes(
        state_path, json.dumps(state, ensure_ascii=False, indent=2).encode("utf-8")
    )


def describe(publication: Publication) -> dict:
    return {
        "file": publication.path.name,
        "data_date": publication.data_date,
        "rows": publication.rows,
        "stations": len(publication.stations),
        "sha256": sha256_of(publication.path),
    }


# --- Acquisition de la publication -----------------------------------------


def gz_name(name: str) -> str:
    """x.csv -> x.csv.gz ; x.csv.gz inchange."""
    return name if name.lower().endswith(".gz") else name + ".gz"


def local_candidates(directory: Path):
    """CSV de l'observatoire deposes a la main (le fichier du 2026-08-06 etait
    dans imports/france_sources/, pas directement dans imports/)."""
    found = []
    if not directory.is_dir():
        return found
    for path in directory.rglob("*"):
        if not path.is_file():
            continue
        name = path.name.lower()
        if "observatoire" not in name:
            continue
        if not (name.endswith(".csv") or name.endswith(".csv.gz")):
            continue
        found.append(path)
    return found


def next_local(directories, reference_date: str):
    """La plus ANCIENNE publication strictement plus recente que la reference.

    Surtout pas la plus recente : quand plusieurs publications sont disponibles
    (rattrapage d'anciennes copies, cron arrete plusieurs semaines), prendre
    directement la derniere ferait un seul diff bout-a-bout et perdrait
    definitivement tous les paliers intermediaires.
    """
    dated = []
    for directory in directories:
        for path in local_candidates(Path(directory)):
            data_date = extract_data_date(path.name)
            if data_date and data_date > reference_date:
                dated.append((data_date, path))
    if not dated:
        return None
    return min(dated, key=lambda item: (item[0], item[1].name))[1]


def publish_to_imports(gz_path: Path, imports_dir: Path) -> None:
    """Depose la publication decompressee la ou le build de base la cherche.

    Le script telecharge de toute facon le CSV pour son propre diff : autant
    qu'il serve aussi a la reconstruction de `geotower_fr.db`, ce qui evite un
    depot manuel. Fait seulement APRES un palier reussi : une publication
    refusee par les garde-fous ne doit jamais alimenter le build.
    """
    destination_dir = imports_dir / FRANCE_SOURCES_DIRNAME
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / gz_path.name[: -len(".gz")]
    if destination.is_file():
        log(f"deja present pour le build : {destination.name}")
        return
    temporary = Path(str(destination) + ".part")
    with gzip.open(gz_path, "rb") as reader, open(temporary, "wb") as writer:
        shutil.copyfileobj(reader, writer, length=4 * 1024 * 1024)
    os.replace(temporary, destination)
    log(f"depose pour le build : {destination}")

    # Aucune suppression automatique ici : c'est le dossier du build, pas le
    # notre. On se contente de signaler l'accumulation (~180 Mo par fichier).
    existing = sorted(destination_dir.glob("*observatoireod*.csv"))
    if len(existing) > 2:
        log(
            f"ATTENTION : {len(existing)} CSV observatoire dans {destination_dir} "
            "(~180 Mo chacun), a faire le menage"
        )


def normalize_sources(sources_dir: Path) -> None:
    """Compresse les CSV bruts deposes a la main dans sources/.

    On peut donc y copier les publications telles quelles, sans les gzipper au
    prealable : le script s'en charge au premier passage (180 Mo -> ~40 Mo) et
    supprime l'original. Tout le reste du code ne voit que des .csv.gz.
    """
    for path in sorted(sources_dir.glob("*.csv")):
        destination = Path(str(path) + ".gz")
        if destination.is_file():
            log(f"deja compresse, original supprime : {path.name}")
            path.unlink()
            continue
        log(f"compression de {path.name}")
        temporary = Path(str(destination) + ".part")
        archive_source(path, temporary)
        os.replace(temporary, destination)
        path.unlink()


def adopt_reference(sources_dir: Path, bootstrap_from):
    """Reference initiale : celle designee par --bootstrap-from, sinon la plus
    ANCIENNE deja presente dans sources/ — les plus recentes doivent rester
    disponibles comme publications a diffuser."""
    if bootstrap_from:
        source = Path(bootstrap_from)
        if not source.is_file():
            raise SourceError(f"--bootstrap-from : fichier introuvable ({source})")
        destination = sources_dir / gz_name(source.name)
        if source.resolve() != destination.resolve() and not destination.is_file():
            archive_source(source, destination)
        return Publication(destination)
    existing = sorted(
        sources_dir.glob("*.csv.gz"), key=lambda path: extract_data_date(path.name)
    )
    if not existing:
        return None
    return Publication(existing[0])


def http_get(url: str, timeout: int) -> bytes:
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"}
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def resolve_observatoire_csv_url(export_page_html: str):
    match = OBSERVATOIRE_CSV_REGEX.search(export_page_html)
    if not match:
        return None
    url = ("https://" + match.group(0)).replace("\\/", "/")
    if not url.startswith(f"https://{ALLOWED_HOST}/"):
        return None
    return url


def download_to_gz(url: str, destination: Path) -> None:
    """Telecharge en gzippant a la volee : 180 Mo bruts ne sont jamais poses sur
    le disque."""
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"}
    )
    total = 0
    temporary = Path(str(destination) + ".part")
    with urllib.request.urlopen(request, timeout=DOWNLOAD_TIMEOUT_S) as response:
        with gzip.open(temporary, "wb") as archive:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > MAX_DOWNLOAD_BYTES:
                    raise SourceError(
                        f"telechargement interrompu : plus de {MAX_DOWNLOAD_BYTES} octets"
                    )
                archive.write(chunk)
    if total == 0:
        temporary.unlink(missing_ok=True)
        raise SourceError("telechargement vide")
    os.replace(temporary, destination)
    log(f"telecharge : {destination.name} ({total} octets bruts)")


def archive_source(source: Path, destination: Path) -> None:
    """Copie une publication dans le dossier d'historique, gzippee."""
    if source.suffix.lower() == ".gz":
        shutil.copyfile(source, destination)
        return
    with open(source, "rb") as reader, gzip.open(destination, "wb") as writer:
        shutil.copyfileobj(reader, writer, length=4 * 1024 * 1024)


def acquire(search_dirs, incoming_dir: Path, reference_date: str, allow_download: bool):
    """Retourne (chemin, date_donnees) de la publication a traiter, ou None."""
    local = next_local(search_dirs, reference_date)
    if local is not None:
        local_date = extract_data_date(local.name)
        destination = incoming_dir / gz_name(local.name)
        if not destination.is_file():
            log(f"publication locale retenue : {local}")
            archive_source(local, destination)
        return destination, local_date

    if not allow_download:
        return None

    log("aucune publication locale plus recente : interrogation de data.anfr.fr")
    html = http_get(OBSERVATOIRE_EXPORT_PAGE_URL, PAGE_TIMEOUT_S).decode(
        "utf-8", errors="replace"
    )
    url = resolve_observatoire_csv_url(html)
    if not url:
        raise SourceError("URL du CSV de l'observatoire introuvable sur la page d'export")
    remote_name = url.rsplit("/", 1)[-1]
    remote_date = extract_data_date(remote_name)
    if remote_date and remote_date <= reference_date:
        return None
    destination = incoming_dir / (remote_name + ".gz")
    if not destination.is_file():
        download_to_gz(url, destination)
    else:
        log(f"deja telecharge : {destination.name}")
    return destination, remote_date


# --- Programme principal ----------------------------------------------------


def prune_sources(sources_dir: Path, keep_from_date: str) -> None:
    """Supprime les publications ANTERIEURES a `keep_from_date` (la date de la
    publication precedente), donc tout sauf la paire courante.

    Jamais par liste de noms a garder : une publication PLUS RECENTE peut etre en
    attente de traitement, et sa suppression serait definitive.
    """
    if not keep_from_date:
        return
    for path in sorted(sources_dir.glob("*.csv.gz")):
        data_date = extract_data_date(path.name)
        if data_date and data_date < keep_from_date:
            log(f"publication trop ancienne, supprimee : {path.name}")
            path.unlink(missing_ok=True)


def summarize(stats, stations, points) -> None:
    log(f"stations changees : {len(stations)} | points carte : {len(points)}")
    log(
        "lignes : {} ajoutees, {} retirees, {} modifiees".format(
            stats["rows_added"], stats["rows_removed"], stats["rows_updated"]
        )
    )
    if stats["multi_row_keys"]:
        log(f"triplets porteurs de plusieurs lignes : {stats['multi_row_keys']}")
    if stats["codes"]:
        log("codes carte : " + ", ".join(
            f"{code}={count}" for code, count in sorted(stats["codes"].items())
        ))
    if stats["fields"]:
        top = sorted(stats["fields"].items(), key=lambda item: -item[1])[:15]
        log("champs modifies : " + ", ".join(f"{name}={count}" for name, count in top))


class Layout:
    """Emplacements de l'historique, derives d'un seul dossier racine."""

    def __init__(self, history_dir):
        self.history = Path(history_dir)
        self.sources = self.history / SOURCES_DIRNAME
        self.incoming = self.sources / INCOMING_DIRNAME
        self.weeks = self.history / WEEKS_DIRNAME
        self.map = self.history / MAP_DIRNAME
        self.state = self.history / STATE_FILENAME

    def ensure(self) -> None:
        for directory in (self.sources, self.incoming, self.weeks, self.map):
            directory.mkdir(parents=True, exist_ok=True)


def process_step(args, layout: Layout, reference_path: Path, reference_date: str,
                 incoming_path: Path, incoming_date: str) -> int:
    """Compare DEUX publications successives et ecrit les fichiers du palier."""
    started = datetime.now()
    previous = Publication(reference_path, reference_date)
    previous.scan()
    log(
        f"reference {pretty_date(previous.data_date)} : {previous.rows} lignes, "
        f"{len(previous.stations)} stations"
    )

    current = Publication(incoming_path, incoming_date)
    current.scan()
    log(
        f"nouvelle  {pretty_date(current.data_date)} : {current.rows} lignes, "
        f"{len(current.stations)} stations"
    )

    problems = []
    if current.rows < MIN_ROWS:
        problems.append(f"seulement {current.rows} lignes exploitables (< {MIN_ROWS})")
    lost = len(previous.stations - current.stations)
    ratio = lost / len(previous.stations) if previous.stations else 0.0
    if ratio > MAX_STATION_LOSS_RATIO:
        problems.append(
            f"{lost} stations disparues ({ratio * 100:.1f} % > "
            f"{MAX_STATION_LOSS_RATIO * 100:.0f} %)"
        )
    if problems and not args.force:
        for problem in problems:
            log(f"REFUS : {problem}")
        log(
            "aucun fichier ecrit, reference inchangee. Verifier la publication "
            "ANFR, puis relancer avec --force si la baisse est reelle."
        )
        return 3
    for problem in problems:
        log(f"AVERTISSEMENT (--force) : {problem}")

    gap = days_between(previous.data_date, current.data_date)
    if gap is not None and gap > GAP_WARN_DAYS:
        log(f"AVERTISSEMENT : {gap} jours entre les deux publications (une a pu etre manquee)")

    stations, stats = build_diff(previous, current)
    points = build_map_points(stations)
    summarize(stats, stations, points)

    header_object = {
        "type": "header",
        "from": pretty_date(previous.data_date),
        "to": pretty_date(current.data_date),
        "gap_days": gap,
        "rows_before": previous.rows,
        "rows_after": current.rows,
        "stations_before": len(previous.stations),
        "stations_after": len(current.stations),
        "stations_changed": len(stations),
        "rows_added": stats["rows_added"],
        "rows_removed": stats["rows_removed"],
        "rows_updated": stats["rows_updated"],
        "fields_changed": stats["fields"],
        "source_before": previous.path.name,
        "source_after": current.path.name,
    }
    map_meta = {
        "from": header_object["from"],
        "to": header_object["to"],
        "points_count": len(points),
    }

    if args.dry_run:
        log("--dry-run : aucun fichier ecrit, aucune rotation")
        return 0

    label = pretty_date(current.data_date)
    weeks_path = layout.weeks / f"{label}.jsonl.gz"
    map_path = layout.map / f"{label}.map.json.gz"
    write_weeks_file(weeks_path, header_object, stations)
    write_map_file(map_path, map_meta, points)
    log(f"ecrit : {weeks_path} ({weeks_path.stat().st_size} octets)")
    log(f"ecrit : {map_path} ({map_path.stat().st_size} octets)")

    # --- Rotation, state.json en DERNIER ---
    final_path = layout.sources / incoming_path.name
    os.replace(incoming_path, final_path)
    current.path = final_path
    save_state(
        layout.state,
        {
            "current": describe(current),
            "previous": describe(previous),
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "last_output": {"weeks": weeks_path.name, "map": map_path.name},
        },
    )
    prune_sources(layout.sources, previous.data_date)
    if args.publish_imports:
        publish_to_imports(final_path, Path(args.imports_dir))
    log(f"palier termine en {(datetime.now() - started).total_seconds():.1f}s")
    return 0


def run(args) -> int:
    layout = Layout(args.history_dir)
    imports_dir = Path(args.imports_dir)
    layout.ensure()
    sources_dir = layout.sources
    incoming_dir = layout.incoming
    state_path = layout.state
    if not args.dry_run:
        # Compresser supprime les .csv d'origine : jamais en simulation.
        normalize_sources(sources_dir)

    state = load_state(state_path)
    current_state = state.get("current") or {}
    reference_name = current_state.get("file", "")
    reference_date = current_state.get("data_date", "")

    # Reference posee a la main dans sources/ avant meme l'existence du script,
    # ou designee par --bootstrap-from. On l'adopte puis on CONTINUE : si une
    # publication plus recente est deja disponible, le diff sort dans la foulee.
    if not reference_name:
        adopted = adopt_reference(sources_dir, args.bootstrap_from)
        if adopted is not None:
            log(f"adoption de la reference deja presente : {adopted.path.name}")
            adopted.scan()
            if args.dry_run:
                log("--dry-run : state.json non ecrit")
            else:
                save_state(
                    state_path,
                    {
                        "current": describe(adopted),
                        "previous": None,
                        "updated_at": datetime.now(timezone.utc).isoformat(),
                    },
                )
            reference_name = adopted.path.name
            reference_date = adopted.data_date
            log(
                f"reference posee sur {pretty_date(reference_date)} : "
                f"{adopted.rows} lignes, {len(adopted.stations)} stations"
            )

    # Un palier par tour : plusieurs publications peuvent etre en attente
    # (rattrapage d'anciennes copies, cron arrete). Chacune donne son propre
    # fichier de changements, dans l'ordre chronologique.
    steps = 0
    while True:
        acquired = acquire(
            [imports_dir, sources_dir], incoming_dir, reference_date, not args.no_download
        )
        if acquired is None:
            if steps:
                log(f"{steps} palier(s) traite(s), plus rien a diffuser")
            else:
                log(
                    f"rien de neuf (reference {pretty_date(reference_date)}) : "
                    "aucun fichier ecrit"
                )
            return 0
        incoming_path, incoming_date = acquired

        # Toute premiere execution : rien a comparer, on pose la reference.
        if not reference_name:
            destination = sources_dir / incoming_path.name
            os.replace(incoming_path, destination)
            publication = Publication(destination, incoming_date)
            publication.scan()
            if not args.dry_run:
                save_state(
                    state_path,
                    {
                        "current": describe(publication),
                        "previous": None,
                        "updated_at": datetime.now(timezone.utc).isoformat(),
                    },
                )
            reference_name = destination.name
            reference_date = incoming_date
            log(
                f"premiere execution : reference posee sur {pretty_date(incoming_date)} "
                f"({publication.rows} lignes, {len(publication.stations)} stations)"
            )
            if args.dry_run:
                return 0
            continue

        reference_path = sources_dir / reference_name
        if not reference_path.is_file():
            log(f"ERREUR : la reference {reference_name} est absente de {sources_dir}")
            return 2

        code = process_step(
            args, layout, reference_path, reference_date, incoming_path, incoming_date
        )
        if code != 0:
            return code
        if args.dry_run:
            return 0
        steps += 1
        reference_name = incoming_path.name
        reference_date = incoming_date


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description=(
            "Diff hebdomadaire de l'observatoire ANFR : archive exhaustive + "
            "fichier carte. Concu pour un cron quotidien."
        )
    )
    parser.add_argument("--history-dir", default=str(HISTORY_DIR))
    parser.add_argument("--imports-dir", default=str(IMPORTS_DIR))
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="calcule et affiche le diff sans produire de fichier de sortie ni "
        "faire tourner la reference (des copies de travail peuvent apparaitre "
        "dans sources/incoming/)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="passe outre les garde-fous (baisse massive verifiee a la main)",
    )
    parser.add_argument(
        "--bootstrap-from",
        metavar="FICHIER",
        help="designe une publication comme reference initiale",
    )
    parser.add_argument(
        "--no-download",
        action="store_true",
        help="n'utilise que les fichiers deja presents (tests, execution hors ligne)",
    )
    parser.add_argument(
        "--no-publish-imports",
        dest="publish_imports",
        action="store_false",
        help="ne pas deposer le CSV telecharge dans imports/france_sources/ "
        "pour le build de base (depot actif par defaut)",
    )
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    try:
        return run(args)
    except SourceError as error:
        log(f"REFUS : {error}")
        return 3
    except Exception as error:  # noqa: BLE001
        log(f"ERREUR : {error.__class__.__name__}: {error}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
