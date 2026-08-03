#!/usr/bin/env python3
"""Build a compact ANFR non-mobile SQLite database.

This module is intentionally separate from build_fr_anfr_db.py. It builds the
optional "extra radio actors" layer: broadcast, private networks, rail,
transport, radar, satellite and FH.
"""

from __future__ import annotations

import argparse
import base64
import csv
import json
import math
import os
import re
import sqlite3
import zlib
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Iterable
from zoneinfo import ZoneInfo


DEFAULT_DB_FILENAME = "geotower_fr_radio.db"
DEFAULT_VERSION_FILENAME = "version_fr_radio.json"
DEFAULT_OUTPUT = Path(os.environ.get("GEOTOWER_IMPORTS_DIR", "/opt/geotower/data/imports")) / DEFAULT_DB_FILENAME
SCHEMA_VERSION = 1
COUNTRY_CODE = "FR"
COUNTRY_NAME = "France"
SOURCE = "ANFR_RADIO"
DEFAULT_ARCOM_RADIO_JSON = "arcom_radio_services.json"
ARCOM_MATCH_BUCKET_E6 = 5_000
ARCOM_MATCH_MAX_DISTANCE_M = 500.0
ARCOM_FREQUENCY_TOLERANCE_KHZ = 2

SERVICE_BROADCAST = 1 << 0
SERVICE_PRIVATE = 1 << 1
SERVICE_RAIL = 1 << 2
SERVICE_TRANSPORT = 1 << 3
SERVICE_FH = 1 << 4
SERVICE_SATELLITE = 1 << 5
SERVICE_RADAR = 1 << 6
SERVICE_OTHER = 1 << 7

SYSTEM_BITS = {
    "FM": 1 << 0,
    "DVB_T": 1 << 1,
    "DAB": 1 << 2,
    "AM": 1 << 3,
    "FH": 1 << 4,
    "GSM_R": 1 << 5,
    "PMR": 1 << 6,
    "TETRA": 1 << 7,
    "POCSAG": 1 << 8,
    "COM_TER": 1 << 9,
    "COM_MAR": 1 << 10,
    "AIS": 1 << 11,
    "SAT": 1 << 12,
    "RADAR": 1 << 13,
    "BLR": 1 << 14,
    "LTE_PRIVATE": 1 << 15,
    "BROADCAST_5G": 1 << 16,
    "METEO_RS": 1 << 17,
    "TELEMETRY": 1 << 18,
    "OTHER": 1 << 19,
}

SERVICE_LABELS = {
    SERVICE_BROADCAST: "Radio/TV",
    SERVICE_PRIVATE: "Reseaux prives",
    SERVICE_RAIL: "Ferroviaire",
    SERVICE_TRANSPORT: "Transport",
    SERVICE_FH: "FH",
    SERVICE_SATELLITE: "Satellite",
    SERVICE_RADAR: "Radar",
    SERVICE_OTHER: "Autres",
}

PUBLIC_MOBILE_OPERATOR_MARKERS = (
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
)


@dataclass
class SupportInfo:
    sta: str
    sup: str
    lat_e6: int
    lon_e6: int
    nat_id: int | None
    tpo_id: int | None
    height_dm: int | None
    code_insee: str | None
    address: str | None


@dataclass
class AntennaSummary:
    count: int = 0
    samples: list[str] = field(default_factory=list)
    type_ids: set[int] = field(default_factory=set)


@dataclass
class SiteAggregate:
    sta: str
    sup: str
    adm_id: int | None
    service_mask: int = 0
    system_mask: int = 0
    emitter_count: int = 0
    systems: Counter[str] = field(default_factory=Counter)
    min_freq_khz: int | None = None
    max_freq_khz: int | None = None
    freq_range_count: int = 0
    freq_samples: list[str] = field(default_factory=list)
    freq_ranges: list[tuple[int, int]] = field(default_factory=list)

    def add_freq_range(self, start_khz: int, end_khz: int, raw_label: str) -> None:
        low = min(start_khz, end_khz)
        high = max(start_khz, end_khz)
        self.min_freq_khz = low if self.min_freq_khz is None else min(self.min_freq_khz, low)
        self.max_freq_khz = high if self.max_freq_khz is None else max(self.max_freq_khz, high)
        self.freq_range_count += 1
        self.freq_ranges.append((low, high))
        if raw_label not in self.freq_samples and len(self.freq_samples) < 24:
            self.freq_samples.append(raw_label)


@dataclass(frozen=True)
class BroadcastProgram:
    service_name: str
    mode: str
    frequency_label: str
    frequency_khz: int | None = None
    category: str | None = None
    holder: str | None = None
    distance_m: float | None = None

    def packed(self) -> str:
        parts = [
            compact_token(self.service_name),
            compact_token(self.frequency_label),
            compact_token(self.mode),
            compact_token(self.category or ""),
        ]
        return "|".join(parts)


def clean(value: object) -> str:
    return str(value).strip() if value is not None else ""


def compact_token(value: object) -> str:
    return clean(value).replace("|", "/").replace(";", ",")


def normalize_station_id(value: object) -> str:
    text = clean(value)
    return text.zfill(10) if text.isdigit() else text


def int_or_none(value: object) -> int | None:
    text = clean(value)
    if not text:
        return None
    try:
        return int(float(text.replace(",", ".")))
    except ValueError:
        return None


def float_or_none(value: object) -> float | None:
    text = clean(value)
    if not text:
        return None
    try:
        return float(text.replace(",", "."))
    except ValueError:
        return None


def dms_to_e6(deg: object, minute: object, second: object, direction: object) -> int | None:
    deg_value = float_or_none(deg)
    minute_value = float_or_none(minute) or 0.0
    second_value = float_or_none(second) or 0.0
    if deg_value is None:
        return None
    value = deg_value + minute_value / 60.0 + second_value / 3600.0
    if clean(direction).upper() in {"S", "W"}:
        value = -value
    return int(round(value * 1_000_000))


def frequency_to_khz(value: object, unit: object) -> int | None:
    number = float_or_none(value)
    if number is None:
        return None
    unit_upper = (clean(unit) or "M").upper()
    if unit_upper.startswith("G"):
        return int(round(number * 1_000_000))
    if unit_upper.startswith("M"):
        return int(round(number * 1_000))
    if unit_upper.startswith("K"):
        return int(round(number))
    if unit_upper.startswith("H"):
        return int(round(number / 1_000))
    return int(round(number * 1_000))


def frequency_label_to_khz(value: object) -> int | None:
    text = clean(value).replace("\xa0", " ")
    if not text:
        return None
    match = re.search(r"([0-9]+(?:[,.][0-9]+)?)\s*(GHz|MHz|kHz|Hz)?", text, re.IGNORECASE)
    if not match:
        return None
    return frequency_to_khz(match.group(1), (match.group(2) or "MHz")[0])


def aggregate_contains_frequency(
    aggregate: SiteAggregate,
    frequency_khz: int,
    tolerance_khz: int = ARCOM_FREQUENCY_TOLERANCE_KHZ,
) -> bool:
    return any(
        low - tolerance_khz <= frequency_khz <= high + tolerance_khz
        for low, high in aggregate.freq_ranges
    )


def format_freq_range(start_raw: str, end_raw: str, unit: str) -> str:
    suffix = {
        "G": "GHz",
        "M": "MHz",
        "K": "kHz",
        "H": "Hz",
    }.get((unit or "M").upper()[:1], f"{unit}Hz")
    return f"{start_raw}-{end_raw} {suffix}"


def haversine_m(lat1_e6: int, lon1_e6: int, lat2_e6: int, lon2_e6: int) -> float:
    lat1 = math.radians(lat1_e6 / 1_000_000.0)
    lat2 = math.radians(lat2_e6 / 1_000_000.0)
    dlat = lat2 - lat1
    dlon = math.radians((lon2_e6 - lon1_e6) / 1_000_000.0)
    a = math.sin(dlat / 2.0) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2.0) ** 2
    return 6_371_000.0 * 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def arcom_mode_to_system_bit(mode: str) -> int | None:
    upper = mode.upper().replace(" ", "")
    if upper.startswith("FM"):
        return SYSTEM_BITS["FM"]
    if upper.startswith("DAB"):
        return SYSTEM_BITS["DAB"]
    return None


def compress_text(value: str | None) -> str | None:
    if not value:
        return None
    compressed = zlib.compress(value.encode("utf-8"), level=9)
    encoded = "Z1:" + base64.b64encode(compressed).decode("ascii")
    return encoded if len(encoded) < len(value) else value


def read_arcom_entries(path: Path | None) -> list[dict[str, object]]:
    if path is None or not path.is_file():
        return []
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, list):
        return [entry for entry in payload if isinstance(entry, dict)]
    if isinstance(payload, dict):
        entries = payload.get("entries")
        if isinstance(entries, list):
            return [entry for entry in entries if isinstance(entry, dict)]
    return []


def build_arcom_program_matches(
    arcom_json: Path | None,
    aggregates: dict[tuple[str, str], SiteAggregate],
    supports: dict[tuple[str, str], SupportInfo],
    *,
    max_distance_m: float = ARCOM_MATCH_MAX_DISTANCE_M,
) -> tuple[dict[tuple[str, str], list[BroadcastProgram]], dict[str, object]]:
    entries = read_arcom_entries(arcom_json)
    if not entries:
        return {}, {
            "arcom_source": str(arcom_json) if arcom_json else None,
            "arcom_entries_read": 0,
            "arcom_entries_matched": 0,
            "arcom_entries_matched_by_frequency": 0,
            "arcom_entries_matched_without_frequency_data": 0,
            "arcom_entries_unmatched": 0,
            "arcom_entries_unmatched_frequency": 0,
            "arcom_site_matches": 0,
        }

    grid: dict[tuple[int, int], list[tuple[tuple[str, str], SiteAggregate, SupportInfo]]] = defaultdict(list)
    for key, aggregate in aggregates.items():
        if (aggregate.service_mask & SERVICE_BROADCAST) == 0:
            continue
        support = supports.get(key)
        if support is None:
            continue
        lat_bucket = support.lat_e6 // ARCOM_MATCH_BUCKET_E6
        lon_bucket = support.lon_e6 // ARCOM_MATCH_BUCKET_E6
        grid[(lat_bucket, lon_bucket)].append((key, aggregate, support))

    programs_by_site: dict[tuple[str, str], list[BroadcastProgram]] = defaultdict(list)
    seen_by_site: dict[tuple[str, str], set[tuple[str, str, int | None]]] = defaultdict(set)
    matched = 0
    unmatched = 0
    matched_by_frequency = 0
    matched_without_frequency_data = 0
    unmatched_frequency = 0

    for entry in entries:
        service_name = clean(entry.get("service_name") or entry.get("service") or entry.get("name"))
        mode = clean(entry.get("mode") or entry.get("diffusion") or entry.get("type"))
        lat_e6 = int_or_none(entry.get("latitude_e6"))
        lon_e6 = int_or_none(entry.get("longitude_e6"))
        if lat_e6 is None:
            lat = float_or_none(entry.get("latitude"))
            lat_e6 = int(round(lat * 1_000_000.0)) if lat is not None else None
        if lon_e6 is None:
            lon = float_or_none(entry.get("longitude"))
            lon_e6 = int(round(lon * 1_000_000.0)) if lon is not None else None

        required_system_bit = arcom_mode_to_system_bit(mode)
        if not service_name or lat_e6 is None or lon_e6 is None or required_system_bit is None:
            unmatched += 1
            continue

        freq_label = clean(entry.get("frequency_label") or entry.get("frequency") or entry.get("frequence"))
        freq_khz = int_or_none(entry.get("frequency_khz")) or frequency_label_to_khz(freq_label)
        lat_bucket = lat_e6 // ARCOM_MATCH_BUCKET_E6
        lon_bucket = lon_e6 // ARCOM_MATCH_BUCKET_E6
        candidates_for_entry: list[tuple[float, tuple[str, str], SiteAggregate]] = []

        for delta_lat in (-1, 0, 1):
            for delta_lon in (-1, 0, 1):
                for key, aggregate, support in grid.get((lat_bucket + delta_lat, lon_bucket + delta_lon), []):
                    if (aggregate.system_mask & required_system_bit) == 0:
                        continue
                    distance = haversine_m(lat_e6, lon_e6, support.lat_e6, support.lon_e6)
                    if distance > max_distance_m:
                        continue
                    candidates_for_entry.append((distance, key, aggregate))

        if not candidates_for_entry:
            unmatched += 1
            continue

        best_candidates = candidates_for_entry
        if freq_khz is not None:
            frequency_candidates = [
                candidate
                for candidate in candidates_for_entry
                if aggregate_contains_frequency(candidate[2], freq_khz)
            ]
            if frequency_candidates:
                best_candidates = frequency_candidates
                matched_by_frequency += 1
            elif any(candidate[2].freq_ranges for candidate in candidates_for_entry):
                unmatched += 1
                unmatched_frequency += 1
                continue
            else:
                matched_without_frequency_data += 1

        distance, key, _aggregate = min(best_candidates, key=lambda item: item[0])
        dedupe_key = (service_name.casefold(), mode.upper(), freq_khz)
        if dedupe_key in seen_by_site[key]:
            matched += 1
            continue
        seen_by_site[key].add(dedupe_key)
        programs_by_site[key].append(
            BroadcastProgram(
                service_name=service_name,
                mode=mode,
                frequency_label=freq_label,
                frequency_khz=freq_khz,
                category=clean(entry.get("category") or entry.get("categorie")) or None,
                holder=clean(entry.get("holder") or entry.get("titulaire")) or None,
                distance_m=distance,
            )
        )
        matched += 1

    for key in programs_by_site:
        programs_by_site[key].sort(
            key=lambda item: (
                item.mode.upper(),
                item.frequency_khz or 0,
                item.service_name.casefold(),
            )
        )

    return programs_by_site, {
        "arcom_source": str(arcom_json),
        "arcom_entries_read": len(entries),
        "arcom_entries_matched": matched,
        "arcom_entries_matched_by_frequency": matched_by_frequency,
        "arcom_entries_matched_without_frequency_data": matched_without_frequency_data,
        "arcom_entries_unmatched": unmatched,
        "arcom_entries_unmatched_frequency": unmatched_frequency,
        "arcom_site_matches": len(programs_by_site),
        "arcom_match_max_distance_m": max_distance_m,
        "arcom_frequency_tolerance_khz": ARCOM_FREQUENCY_TOLERANCE_KHZ,
    }


def open_csv(path: Path) -> Iterable[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", errors="ignore", newline="") as handle:
        reader = csv.DictReader(handle, delimiter=";")
        for row in reader:
            yield row


def read_reference(path: Path, key_col: str, label_col: str) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for row in open_csv(path):
        key = clean(row.get(key_col))
        label = clean(row.get(label_col))
        if key and label:
            values[key] = label
    return values


def latest_ref_dir_for(data_dir: Path) -> Path | None:
    parent = data_dir.parent
    candidates = sorted(parent.glob("*-export-etalab-ref"), reverse=True)
    return candidates[0] if candidates else None


def is_mobile_like_system(system: str) -> bool:
    upper = system.upper()
    if upper.startswith(("GSM 900", "GSM 1800", "UMTS", "5G NR")):
        return True
    if upper.startswith("LTE") and " P" not in upper:
        return True
    return False


def is_public_mobile_operator(label: str) -> bool:
    upper = label.upper()
    return any(marker in upper for marker in PUBLIC_MOBILE_OPERATOR_MARKERS)


def system_key(system: str) -> str:
    upper = system.upper()
    if upper.startswith("FM"):
        return "FM"
    if upper.startswith("RDF DVB"):
        return "DVB_T"
    if upper.startswith("RDF T-DAB"):
        return "DAB"
    if upper.startswith("RDF AM"):
        return "AM"
    if upper.startswith("FH"):
        return "FH"
    if upper.startswith("GSM R"):
        return "GSM_R"
    if upper.startswith("PMR"):
        return "PMR"
    if upper.startswith(("TETRA", "TETRAPOL")):
        return "TETRA"
    if upper.startswith("RMU"):
        return "POCSAG"
    if upper.startswith("COM TER"):
        return "COM_TER"
    if upper.startswith("COM MAR"):
        return "COM_MAR"
    if upper.startswith("AIS"):
        return "AIS"
    if upper.startswith("SAT"):
        return "SAT"
    if upper.startswith("RDR"):
        return "RADAR"
    if upper.startswith("BLR"):
        return "BLR"
    if upper.startswith("LTE"):
        return "LTE_PRIVATE"
    if upper.startswith("5G BROADCAST"):
        return "BROADCAST_5G"
    if upper == "RS":
        return "METEO_RS"
    if upper.startswith(("TELEM", "TELECD")):
        return "TELEMETRY"
    return "OTHER"


def service_for(system: str, actor_label: str) -> int:
    upper = system.upper()
    actor_upper = actor_label.upper()
    if upper.startswith("FH"):
        return SERVICE_FH
    if upper.startswith(("FM", "RDF DVB", "RDF T-DAB", "RDF AM", "5G BROADCAST")):
        return SERVICE_BROADCAST
    if "SNCF" in actor_upper or upper.startswith("GSM R"):
        return SERVICE_RAIL
    if upper.startswith(("COM TER", "COM MAR", "AIS", "COM AERTER")):
        return SERVICE_TRANSPORT
    if upper.startswith(("SAT", "GPS")):
        return SERVICE_SATELLITE
    if upper.startswith(("RDR", "GONIO")):
        return SERVICE_RADAR
    if upper.startswith(
        ("PMR", "TETRA", "TETRAPOL", "RMU", "EM/REC", "REC", "TELEM", "TELECD", "ANM", "LTE")
    ):
        return SERVICE_PRIVATE
    return SERVICE_OTHER


def service_names(mask: int) -> str:
    return ", ".join(label for bit, label in SERVICE_LABELS.items() if mask & bit)


def progress(label: str) -> None:
    print(label, flush=True)


def date_from_source_name(path: Path) -> str:
    candidates = [path.name, path.parent.name]
    for candidate in candidates:
        match = re.search(r"(20\d{2})(\d{2})(\d{2})", candidate)
        if match:
            return f"{match.group(1)}-{match.group(2)}-{match.group(3)}"
    return "Inconnue"


def version_from_source_name(path: Path) -> str:
    return datetime.now(ZoneInfo("Europe/Paris")).strftime("%Y%m%d_%H%M")


def file_sha256(path: Path) -> str:
    import hashlib

    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def create_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;

        CREATE TABLE non_mobile_site (
            sta_nm_anfr TEXT NOT NULL,
            sup_id TEXT NOT NULL,
            adm_id INTEGER,
            lat_e6 INTEGER NOT NULL,
            lon_e6 INTEGER NOT NULL,
            nat_id INTEGER,
            tpo_id INTEGER,
            height_dm INTEGER,
            code_insee TEXT,
            service_mask INTEGER NOT NULL,
            system_mask INTEGER NOT NULL,
            emitter_count INTEGER NOT NULL,
            antenna_count INTEGER NOT NULL,
            freq_range_count INTEGER NOT NULL,
            min_freq_khz INTEGER,
            max_freq_khz INTEGER,
            PRIMARY KEY (sta_nm_anfr, sup_id)
        ) WITHOUT ROWID;

        CREATE TABLE non_mobile_detail (
            sta_nm_anfr TEXT NOT NULL,
            sup_id TEXT NOT NULL,
            detail_z TEXT NOT NULL,
            PRIMARY KEY (sta_nm_anfr, sup_id)
        ) WITHOUT ROWID;

        CREATE TABLE ref_actor (
            adm_id INTEGER NOT NULL PRIMARY KEY,
            label TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE ref_nature (
            nat_id INTEGER NOT NULL PRIMARY KEY,
            label TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE ref_owner (
            tpo_id INTEGER NOT NULL PRIMARY KEY,
            label TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE ref_type_antenne (
            tae_id INTEGER NOT NULL PRIMARY KEY,
            label TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE metadata (
            version TEXT NOT NULL PRIMARY KEY,
            schema_version INTEGER NOT NULL,
            country_code TEXT NOT NULL,
            country_name TEXT,
            source TEXT NOT NULL,
            date_maj_anfr TEXT,
            zip_version TEXT,
            row_count INTEGER NOT NULL
        ) WITHOUT ROWID;
        """
    )


def detail_for(
    aggregate: SiteAggregate,
    support: SupportInfo,
    antenna: AntennaSummary,
    actor_labels: dict[str, str],
    nature_labels: dict[str, str],
    owner_labels: dict[str, str],
    programs: list[BroadcastProgram] | None = None,
) -> str:
    actor = actor_labels.get(str(aggregate.adm_id), f"ADM_ID {aggregate.adm_id}")
    nature = nature_labels.get(str(support.nat_id), f"NAT_ID {support.nat_id}")
    owner = owner_labels.get(str(support.tpo_id), f"TPO_ID {support.tpo_id}")
    systems = ", ".join(f"{name} x{count}" for name, count in aggregate.systems.most_common())
    freqs = ", ".join(aggregate.freq_samples)
    if aggregate.freq_range_count > len(aggregate.freq_samples):
        freqs = f"{freqs}, +{aggregate.freq_range_count - len(aggregate.freq_samples)}" if freqs else ""
    antenna_text = "; ".join(antenna.samples)
    program_text = "; ".join(program.packed() for program in (programs or []))
    lines = [
        f"Acteur: {actor}",
        f"Familles: {service_names(aggregate.service_mask)}",
        f"Systemes: {systems}",
        f"Support: {nature}; proprietaire {owner}; hauteur_dm={support.height_dm or ''}",
        f"Adresse: {support.address or ''}",
        f"Frequences: {freqs}",
        f"Programmes: {program_text}",
        f"Antennes: {antenna_text}",
    ]
    return "\n".join(line for line in lines if line and not line.endswith(": "))


def insert_rows(
    conn: sqlite3.Connection,
    aggregates: dict[tuple[str, str], SiteAggregate],
    supports: dict[tuple[str, str], SupportInfo],
    antennas: dict[tuple[str, str], AntennaSummary],
    programs_by_site: dict[tuple[str, str], list[BroadcastProgram]],
    actor_labels: dict[str, str],
    nature_labels: dict[str, str],
    owner_labels: dict[str, str],
    type_labels: dict[str, str],
    data_dir: Path,
    ref_dir: Path | None,
) -> dict[str, object]:
    used_adm = set()
    used_nat = set()
    used_tpo = set()
    used_tae = set()
    rows = []
    detail_rows = []
    raw_detail_bytes = 0
    stored_detail_bytes = 0

    for key, aggregate in sorted(aggregates.items()):
        support = supports.get(key)
        if support is None:
            continue
        antenna = antennas.get(key, AntennaSummary())
        detail = detail_for(
            aggregate,
            support,
            antenna,
            actor_labels,
            nature_labels,
            owner_labels,
            programs_by_site.get(key),
        )
        raw_detail_bytes += len(detail.encode("utf-8"))
        detail_z = compress_text(detail)
        stored_detail_bytes += len((detail_z or "").encode("utf-8"))
        if aggregate.adm_id is not None:
            used_adm.add(aggregate.adm_id)
        if support.nat_id is not None:
            used_nat.add(support.nat_id)
        if support.tpo_id is not None:
            used_tpo.add(support.tpo_id)
        rows.append(
            (
                aggregate.sta,
                aggregate.sup,
                aggregate.adm_id,
                support.lat_e6,
                support.lon_e6,
                support.nat_id,
                support.tpo_id,
                support.height_dm,
                support.code_insee,
                aggregate.service_mask,
                aggregate.system_mask,
                aggregate.emitter_count,
                antenna.count,
                aggregate.freq_range_count,
                aggregate.min_freq_khz,
                aggregate.max_freq_khz,
            )
        )
        if detail_z:
            detail_rows.append((aggregate.sta, aggregate.sup, detail_z))

    conn.executemany(
        """
        INSERT INTO non_mobile_site (
            sta_nm_anfr, sup_id, adm_id, lat_e6, lon_e6, nat_id, tpo_id, height_dm,
            code_insee, service_mask, system_mask, emitter_count, antenna_count,
            freq_range_count, min_freq_khz, max_freq_khz
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    conn.executemany("INSERT INTO non_mobile_detail VALUES (?, ?, ?)", detail_rows)
    conn.executemany(
        "INSERT INTO ref_actor VALUES (?, ?)",
        sorted((adm, actor_labels.get(str(adm), f"ADM_ID {adm}")) for adm in used_adm),
    )
    conn.executemany(
        "INSERT INTO ref_nature VALUES (?, ?)",
        sorted((nat, nature_labels.get(str(nat), f"NAT_ID {nat}")) for nat in used_nat),
    )
    conn.executemany(
        "INSERT INTO ref_owner VALUES (?, ?)",
        sorted((tpo, owner_labels.get(str(tpo), f"TPO_ID {tpo}")) for tpo in used_tpo),
    )

    for summary in antennas.values():
        used_tae.update(summary.type_ids)

    conn.executemany(
        "INSERT INTO ref_type_antenne VALUES (?, ?)",
        sorted((tae, type_labels.get(str(tae), f"TAE_ID {tae}")) for tae in used_tae),
    )

    return {
        "inserted_rows": len(rows),
        "inserted_detail_rows": len(detail_rows),
        "raw_detail_bytes": raw_detail_bytes,
        "stored_detail_bytes": stored_detail_bytes,
    }


def sqlite_object_sizes(conn: sqlite3.Connection) -> list[dict[str, object]]:
    try:
        rows = conn.execute(
            "SELECT name, SUM(pgsize) AS bytes FROM dbstat GROUP BY name ORDER BY bytes DESC"
        ).fetchall()
        return [{"name": name, "bytes": int(size or 0)} for name, size in rows]
    except sqlite3.Error:
        return []


def write_version_json(report: dict[str, object], output: Path, version_json: Path) -> None:
    payload = {
        "version": report["version"],
        "schema_version": SCHEMA_VERSION,
        "country_code": COUNTRY_CODE,
        "country_name": COUNTRY_NAME,
        "source": SOURCE,
        "date_anfr": report["date_anfr"],
        "zip_name": report["zip_name"],
        "db_name": output.name,
        "size_bytes": output.stat().st_size,
        "sha256": file_sha256(output),
        "row_count": report["site_rows"],
        "detail_row_count": report["detail_rows"],
    }
    version_json.parent.mkdir(parents=True, exist_ok=True)
    version_json.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def build_radio_db(
    data_dir: Path,
    ref_dir: Path | None,
    output: Path,
    *,
    version: str | None = None,
    version_json: Path | None = None,
    source_path: Path | None = None,
    arcom_radio_json: Path | None = None,
    write_report_file: bool = True,
) -> dict[str, object]:
    txt_station = data_dir / "SUP_STATION.txt"
    txt_support = data_dir / "SUP_SUPPORT.txt"
    txt_antenne = data_dir / "SUP_ANTENNE.txt"
    txt_emetteur = data_dir / "SUP_EMETTEUR.txt"
    txt_bande = data_dir / "SUP_BANDE.txt"

    if ref_dir is None:
        ref_dir = latest_ref_dir_for(data_dir)

    for path in (txt_station, txt_support, txt_antenne, txt_emetteur, txt_bande):
        if not path.is_file():
            raise FileNotFoundError(path)

    actor_labels = read_reference(ref_dir / "SUP_EXPLOITANT.txt", "ADM_ID", "ADM_LB_NOM") if ref_dir else {}
    nature_labels = read_reference(ref_dir / "SUP_NATURE.txt", "NAT_ID", "NAT_LB_NOM") if ref_dir else {}
    owner_labels = read_reference(ref_dir / "SUP_PROPRIETAIRE.txt", "TPO_ID", "TPO_LB") if ref_dir else {}
    type_labels = read_reference(ref_dir / "SUP_TYPE_ANTENNE.txt", "TAE_ID", "TAE_LB") if ref_dir else {}

    progress("1/6 Reading stations...")
    station_adm: dict[str, int | None] = {}
    station_count = 0
    for row in open_csv(txt_station):
        sta = normalize_station_id(row.get("STA_NM_ANFR"))
        if not sta:
            continue
        station_adm[sta] = int_or_none(row.get("ADM_ID"))
        station_count += 1

    progress("2/6 Reading supports...")
    supports: dict[tuple[str, str], SupportInfo] = {}
    station_supports: dict[str, list[str]] = defaultdict(list)
    skipped_support_coords = 0
    for row in open_csv(txt_support):
        sta = normalize_station_id(row.get("STA_NM_ANFR"))
        sup = clean(row.get("SUP_ID"))
        if not sta or not sup:
            continue
        lat_e6 = dms_to_e6(
            row.get("COR_NB_DG_LAT"),
            row.get("COR_NB_MN_LAT"),
            row.get("COR_NB_SC_LAT"),
            row.get("COR_CD_NS_LAT"),
        )
        lon_e6 = dms_to_e6(
            row.get("COR_NB_DG_LON"),
            row.get("COR_NB_MN_LON"),
            row.get("COR_NB_SC_LON"),
            row.get("COR_CD_EW_LON"),
        )
        if lat_e6 is None or lon_e6 is None:
            skipped_support_coords += 1
            continue
        address_parts = [
            clean(row.get("ADR_LB_LIEU")),
            clean(row.get("ADR_LB_ADD1")),
            clean(row.get("ADR_LB_ADD2")),
            clean(row.get("ADR_LB_ADD3")),
            clean(row.get("ADR_NM_CP")),
        ]
        support = SupportInfo(
            sta=sta,
            sup=sup,
            lat_e6=lat_e6,
            lon_e6=lon_e6,
            nat_id=int_or_none(row.get("NAT_ID")),
            tpo_id=int_or_none(row.get("TPO_ID")),
            height_dm=int(round((float_or_none(row.get("SUP_NM_HAUT")) or 0.0) * 10.0))
            if clean(row.get("SUP_NM_HAUT"))
            else None,
            code_insee=clean(row.get("COM_CD_INSEE")) or None,
            address=", ".join(part for part in address_parts if part) or None,
        )
        supports[(sta, sup)] = support
        station_supports[sta].append(sup)

    progress("3/6 Reading antennas...")
    aer_to_support: dict[str, tuple[str, str]] = {}
    antennas: dict[tuple[str, str], AntennaSummary] = defaultdict(AntennaSummary)
    for row in open_csv(txt_antenne):
        sta = normalize_station_id(row.get("STA_NM_ANFR"))
        aer = clean(row.get("AER_ID"))
        sup = clean(row.get("SUP_ID"))
        if not sta or not aer or not sup:
            continue
        key = (sta, sup)
        aer_to_support[aer] = key
        summary = antennas[key]
        summary.count += 1
        tae_id = int_or_none(row.get("TAE_ID"))
        if tae_id is not None:
            summary.type_ids.add(tae_id)
        if len(summary.samples) < 12:
            type_label = type_labels.get(str(tae_id), f"TAE_ID {tae_id}") if tae_id is not None else "Type inconnu"
            az = clean(row.get("AER_NB_AZIMUT")) or "N/A"
            height = clean(row.get("AER_NB_ALT_BAS")) or "N/A"
            summary.samples.append(f"{type_label}: {az} deg ({height}m)")

    def resolve_support(sta: str, aer: str) -> tuple[str, str] | None:
        if aer and aer in aer_to_support:
            return aer_to_support[aer]
        candidates = station_supports.get(sta)
        if candidates and len(candidates) == 1:
            return sta, candidates[0]
        return None

    progress("4/6 Reading non-mobile emitters...")
    aggregates: dict[tuple[str, str], SiteAggregate] = {}
    emr_to_site: dict[str, tuple[tuple[str, str], str]] = {}
    skipped_public_mobile = 0
    skipped_no_support = 0
    skipped_ambiguous_support = 0
    emitter_count = 0
    for row in open_csv(txt_emetteur):
        sta = normalize_station_id(row.get("STA_NM_ANFR"))
        emr = clean(row.get("EMR_ID"))
        aer = clean(row.get("AER_ID"))
        system = clean(row.get("EMR_LB_SYSTEME")) or "Inconnu"
        if not sta or not emr:
            continue
        adm_id = station_adm.get(sta)
        actor_label = actor_labels.get(str(adm_id), "")
        sys_key = system_key(system)
        if (is_mobile_like_system(system) or sys_key == "FH") and is_public_mobile_operator(actor_label):
            skipped_public_mobile += 1
            continue
        key = resolve_support(sta, aer)
        if key is None:
            if len(station_supports.get(sta, [])) > 1:
                skipped_ambiguous_support += 1
            else:
                skipped_no_support += 1
            continue
        if key not in supports:
            skipped_no_support += 1
            continue
        aggregate = aggregates.get(key)
        if aggregate is None:
            aggregate = SiteAggregate(sta=key[0], sup=key[1], adm_id=adm_id)
            aggregates[key] = aggregate
        service = service_for(system, actor_label)
        aggregate.service_mask |= service
        aggregate.system_mask |= SYSTEM_BITS.get(sys_key, SYSTEM_BITS["OTHER"])
        aggregate.emitter_count += 1
        aggregate.systems[system] += 1
        emr_to_site[emr] = (key, system)
        emitter_count += 1

    progress("5/6 Reading frequency bands for selected emitters...")
    matched_band_rows = 0
    for row in open_csv(txt_bande):
        emr = clean(row.get("EMR_ID"))
        mapped = emr_to_site.get(emr)
        if mapped is None:
            continue
        key, _system = mapped
        aggregate = aggregates.get(key)
        if aggregate is None:
            continue
        start_raw = clean(row.get("BAN_NB_F_DEB"))
        end_raw = clean(row.get("BAN_NB_F_FIN"))
        unit = clean(row.get("BAN_FG_UNITE")) or "M"
        start = frequency_to_khz(start_raw, unit)
        end = frequency_to_khz(end_raw, unit)
        if start is None or end is None:
            continue
        aggregate.add_freq_range(start, end, format_freq_range(start_raw, end_raw, unit))
        matched_band_rows += 1

    programs_by_site, arcom_stats = build_arcom_program_matches(arcom_radio_json, aggregates, supports)
    if arcom_stats["arcom_entries_read"]:
        progress(
            "ARCOM: matched "
            f"{arcom_stats['arcom_entries_matched']}/{arcom_stats['arcom_entries_read']} radio program entries"
        )

    metadata_source = source_path or data_dir
    date_maj_anfr = date_from_source_name(metadata_source)
    db_version = version or version_from_source_name(metadata_source)
    zip_version = metadata_source.name if metadata_source.suffix.lower() == ".zip" else f"{metadata_source.name}.zip"

    progress("6/6 Writing SQLite radio DB...")
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    conn = sqlite3.connect(output)
    try:
        create_schema(conn)
        insert_stats = insert_rows(
            conn,
            aggregates,
            supports,
            antennas,
            programs_by_site,
            actor_labels,
            nature_labels,
            owner_labels,
            type_labels,
            data_dir,
            ref_dir,
        )
        conn.execute(
            """
            INSERT INTO metadata (
                version, schema_version, country_code, country_name, source,
                date_maj_anfr, zip_version, row_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                db_version,
                SCHEMA_VERSION,
                COUNTRY_CODE,
                COUNTRY_NAME,
                SOURCE,
                date_maj_anfr,
                zip_version,
                insert_stats["inserted_rows"],
            ),
        )
        conn.execute("CREATE INDEX idx_non_mobile_site_lat_lon ON non_mobile_site(lat_e6, lon_e6)")
        conn.execute("CREATE INDEX idx_non_mobile_site_service ON non_mobile_site(service_mask)")
        conn.execute("CREATE INDEX idx_non_mobile_site_actor ON non_mobile_site(adm_id)")
        conn.commit()
        conn.execute("VACUUM")
        conn.commit()
        object_sizes = sqlite_object_sizes(conn)
        page_count = conn.execute("PRAGMA page_count").fetchone()[0]
        page_size = conn.execute("PRAGMA page_size").fetchone()[0]
    finally:
        conn.close()

    family_counts = {}
    conn = sqlite3.connect(output)
    try:
        for bit, label in SERVICE_LABELS.items():
            count = conn.execute(
                "SELECT COUNT(*) FROM non_mobile_site WHERE (service_mask & ?) != 0",
                (bit,),
            ).fetchone()[0]
            family_counts[label] = count
    finally:
        conn.close()

    report = {
        "output": str(output),
        "version": db_version,
        "date_anfr": date_maj_anfr,
        "zip_name": zip_version,
        "bytes": output.stat().st_size,
        "mib": round(output.stat().st_size / 1024 / 1024, 2),
        "station_rows_read": station_count,
        "support_rows_kept": len(supports),
        "support_rows_skipped_no_coords": skipped_support_coords,
        "antenna_support_groups": len(antennas),
        "non_mobile_emitters_kept": emitter_count,
        "public_mobile_emitters_skipped": skipped_public_mobile,
        "emitters_skipped_no_support": skipped_no_support,
        "emitters_skipped_ambiguous_support": skipped_ambiguous_support,
        "selected_emr_ids": len(emr_to_site),
        "matched_band_rows": matched_band_rows,
        **arcom_stats,
        "site_rows": insert_stats["inserted_rows"],
        "detail_rows": insert_stats["inserted_detail_rows"],
        "raw_detail_bytes": insert_stats["raw_detail_bytes"],
        "stored_detail_bytes": insert_stats["stored_detail_bytes"],
        "page_count": page_count,
        "page_size": page_size,
        "family_site_counts": family_counts,
        "sqlite_object_sizes": object_sizes,
    }
    if version_json is not None:
        write_version_json(report, output, version_json)
    if write_report_file:
        report_path = output.with_suffix(".report.json")
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def build_probe(
    data_dir: Path,
    ref_dir: Path | None,
    output: Path,
    arcom_radio_json: Path | None = None,
) -> dict[str, object]:
    return build_radio_db(data_dir, ref_dir, output, arcom_radio_json=arcom_radio_json)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=None)
    parser.add_argument("--ref-dir", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--arcom-radio-json", type=Path, default=None)
    args = parser.parse_args()
    if args.data_dir is None:
        parser.error(
            "--data-dir est obligatoire avec fr_radio_db_builder.py. "
            "Sur le serveur, utilise plutot: python3 build_fr_radio_db.py"
        )
    return args


def main() -> None:
    args = parse_args()
    report = build_probe(args.data_dir, args.ref_dir, args.output, args.arcom_radio_json)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
