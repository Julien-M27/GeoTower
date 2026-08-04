"""Statistiques par departement de la base GeoTower FR (geotower_fr.db).

Deux tables sont produites, dans la meme base que les stats nationales
(`radio_stat_current` / `radio_stat_weekly` de fr_anfr_stats.py) :

    dept_stat_current        -> une ligne par departement : supports, stations,
                                antennes, superficie, population et tous les ratios
                                (par km2, pour mille habitants, habitants par ...).
    dept_stat_operator_tech  -> une ligne par (departement, operateur, technologie)
                                avec 'ALL' comme totalisateur des deux cotes.

Conventions retenues :

  * Perimetre = ce que contient la base, c'est-a-dire les stations mobiles de
    l'observatoire ANFR (2G/3G/4G/5G). Les chiffres portent sur les AUTORISATIONS
    (tout ce qui est declare), avec en parallele un compteur `*_active` restreint
    aux emissions en service - meme definition que radio_stat_current.
  * Une antenne est comptee par station : un AER_ID mutualise entre deux operateurs
    compte pour chacun d'eux (c'est une antenne declaree par station, et c'est la
    seule lecture qui rende le tableau par operateur additif).
  * Les faisceaux hertziens sont EXCLUS des technologies. Ils ne sont pas perdus
    pour autant : `dept_stat_current.antennas_fh` les compte a part.
  * Les technologies d'une antenne sont lues dans `technique.details_frequences`
    (une ligne par systeme, avec le tag [AER_ID: ...]). La table `antenne` ne peut
    pas servir : sa cle primaire est `aer_id` seul, donc un support mutualise n'y
    garde qu'une seule des stations qui declarent l'antenne.

Superficie et population viennent de geo.api.gouv.fr (agregation des communes),
avec trois niveaux de repli : fichier de references pose a la main, API, puis cache
disque de la derniere reponse. Voir [load_department_reference].

Utilisable seul pour rejouer les stats sans reconstruire la base :

    python3 fr_dept_stats.py --db /opt/geotower/data/imports/geotower_fr.db
"""

import argparse
import csv
import io
import json
import os
import re
import sqlite3
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Set, Tuple

from fr_anfr_stats import (
    DB_FILENAME,
    IMPORTS_DIR,
    decode_details,
    generation_from_system,
    is_active_status,
    normalized_operator,
    tech_keys_from_mask,
)


REFERENCES_DIR = Path(os.environ.get("GEOTOWER_REFERENCES_DIR", "/opt/geotower/data/references"))
REFERENCE_CSV_NAME = "departements_fr.csv"
REFERENCE_CACHE_NAME = "departements_fr.json"

GEO_API_BASE = "https://geo.api.gouv.fr"
USER_AGENT = "GeoTower dept stats builder"

# Collectivites d'outre-mer : absentes de /departements mais leurs communes
# repondent bien sur /departements/<code>/communes.
EXTRA_DEPARTMENT_CODES = ("975", "977", "978", "984", "986", "987", "988")

# Millesime des populations legales exposees par geo.api.gouv.fr. L'API ne le
# renvoie pas : il est fige ici pour l'affichage et surchargeable (--population-year
# ou colonne `annee_population` du CSV de references).
POPULATION_YEAR_DEFAULT = "2022"
POPULATION_SOURCE = "INSEE (populations legales) via geo.api.gouv.fr"
AREA_SOURCE = "IGN (somme des superficies communales) via geo.api.gouv.fr"

TECH_KEYS = ("2G", "3G", "4G", "5G")
ITEM_ALL = "ALL"

# Tag pose par le builder dans details_frequences : "Panneau : 90° (25m) [AER_ID: 1234]".
AER_ID_PATTERN = re.compile(r"\[AER_ID:\s*([^\]]+)\]")

REFERENCE_CSV_ALIASES = {
    "code": ("code", "code_departement", "dept_code", "departement"),
    "name": ("nom", "name", "libelle", "dept_name"),
    "area_km2": ("superficie_km2", "superficie", "area_km2", "km2"),
    "population": ("population", "pop", "habitants"),
    "population_year": ("annee_population", "annee", "population_year", "millesime"),
    "region_code": ("code_region", "region_code", "coderegion"),
}


@dataclass
class DepartmentReference:
    """Donnees de cadrage d'un departement, independantes de l'ANFR."""

    code: str
    name: Optional[str] = None
    region_code: Optional[str] = None
    area_km2: Optional[float] = None
    population: Optional[int] = None
    population_year: Optional[str] = None


@dataclass
class StationAntennas:
    """Ce que les lignes de `details_frequences` d'une station apprennent sur ses antennes."""

    by_tech: Dict[str, Set[str]]
    active_by_tech: Dict[str, Set[str]]
    techs: Set[str]
    active_techs: Set[str]
    fh: Set[str]
    lines_without_aer: int

    @classmethod
    def empty(cls) -> "StationAntennas":
        return cls(defaultdict(set), defaultdict(set), set(), set(), set(), 0)

    def distinct(self) -> Set[str]:
        """Antennes distinctes de la station, hors FH (une antenne 4G+5G compte une fois)."""
        merged: Set[str] = set()
        for antennas in self.by_tech.values():
            merged |= antennas
        return merged

    def distinct_active(self) -> Set[str]:
        merged: Set[str] = set()
        for antennas in self.active_by_tech.values():
            merged |= antennas
        return merged


@dataclass
class DepartmentStatsResult:
    department_rows: int = 0
    operator_tech_rows: int = 0
    stations_without_department: int = 0
    departments_without_reference: List[str] = field(default_factory=list)
    lines_without_aer: int = 0


def ensure_dept_stat_tables(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS dept_stat_current (
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
            PRIMARY KEY (dept_code)
        );

        CREATE TABLE IF NOT EXISTS dept_stat_operator_tech (
            dept_code TEXT NOT NULL,
            operator_name TEXT NOT NULL,
            tech TEXT NOT NULL,
            supports INTEGER NOT NULL,
            supports_active INTEGER NOT NULL,
            stations INTEGER NOT NULL,
            stations_active INTEGER NOT NULL,
            antennas INTEGER NOT NULL,
            antennas_active INTEGER NOT NULL,
            PRIMARY KEY (dept_code, operator_name, tech)
        );

        """
    )


def department_code(code_insee: Optional[str]) -> Optional[str]:
    """Departement d'un code INSEE communal : '72181' -> '72', '2A004' -> '2A', '97501' -> '975'."""
    code = (code_insee or "").strip().upper()
    if not code:
        return None
    if len(code) == 4 and code.isdigit():
        # Filet de securite : certains exports perdent le zero de tete ('1004' = '01004').
        code = "0" + code
    if len(code) != 5:
        return None
    if code[:2] in ("2A", "2B"):
        return code[:2]
    if not code.isdigit():
        return None
    if code[:2] in ("97", "98"):
        return code[:3]
    return code[:2]


def antennas_from_details(details_value: Optional[str]) -> StationAntennas:
    """Extrait les antennes par technologie d'une valeur de `technique.details_frequences`."""
    result = StationAntennas.empty()
    details = decode_details(details_value)
    if not details:
        return result

    for raw_line in details.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parts = [part.strip() for part in line.split("|")]
        system = parts[0] if parts else ""
        status = parts[1] if len(parts) > 1 else ""
        active = is_active_status(status)
        match = AER_ID_PATTERN.search(line)
        aer_id = match.group(1).strip() if match else ""

        if "FH" in system.upper():
            if aer_id:
                result.fh.add(aer_id)
            else:
                result.lines_without_aer += 1
            continue

        generation = generation_from_system(system)
        if generation is None:
            continue

        result.techs.add(generation)
        if active:
            result.active_techs.add(generation)

        if not aer_id:
            result.lines_without_aer += 1
            continue

        result.by_tech[generation].add(aer_id)
        if active:
            result.active_by_tech[generation].add(aer_id)

    return result


def _supports_by_station(conn: sqlite3.Connection) -> Dict[str, Set[str]]:
    supports: Dict[str, Set[str]] = defaultdict(set)
    for id_anfr, id_support in conn.execute("SELECT id_anfr, id_support FROM support"):
        station = (id_anfr or "").strip()
        support = (id_support or "").strip()
        if station and support:
            # sys.intern : ces identifiants finissent dans des dizaines de milliers
            # d'ensembles (departement x operateur x techno). Sans partage d'objet,
            # chaque ligne SQLite apporterait sa propre copie de la chaine.
            supports[station].add(sys.intern(support))
    return supports


def _ratio(numerator: Optional[float], denominator: Optional[float]) -> Optional[float]:
    if numerator is None or not denominator:
        return None
    return round(numerator / denominator, 6)


def populate_department_stats(
    conn: sqlite3.Connection,
    reference: Optional[Dict[str, DepartmentReference]] = None,
    population_year: Optional[str] = None,
) -> DepartmentStatsResult:
    """Recalcule integralement les deux tables de stats departementales."""
    reference = reference or {}
    result = DepartmentStatsResult()

    supports_by_station = _supports_by_station(conn)

    # Cles = (departement, operateur, technologie), 'ALL' compris. Les supports demandent
    # un ensemble (plusieurs stations partagent un pylone), les stations et les antennes
    # se comptent directement : une station n'appartient qu'a un couple (dept, operateur),
    # et une antenne est comptee par station.
    support_sets: Dict[Tuple[str, str, str], Set[str]] = defaultdict(set)
    support_active_sets: Dict[Tuple[str, str, str], Set[str]] = defaultdict(set)
    station_counts: Dict[Tuple[str, str, str], int] = defaultdict(int)
    station_active_counts: Dict[Tuple[str, str, str], int] = defaultdict(int)
    antenna_counts: Dict[Tuple[str, str, str], int] = defaultdict(int)
    antenna_active_counts: Dict[Tuple[str, str, str], int] = defaultdict(int)
    fh_counts: Dict[str, int] = defaultdict(int)
    operators_by_department: Dict[str, Set[str]] = defaultdict(set)

    query = """
        SELECT
            l.id_anfr,
            l.code_insee,
            COALESCE(l.tech_mask, 0) AS tech_mask,
            COALESCE(o.libelle, '') AS operator_name,
            COALESCE(t.has_active, 0) AS has_active,
            t.details_frequences AS details_frequences,
            COALESCE(st.libelle, '') AS statut
        FROM localisation l
        LEFT JOIN ref_operateur o ON l.operateur_id = o.id
        LEFT JOIN technique t ON t.id_anfr = l.id_anfr
        LEFT JOIN ref_statut st ON t.statut_id = st.id
    """

    for row in conn.execute(query):
        id_anfr, code_insee, tech_mask, operator_label, has_active, details, statut = row
        dept = department_code(code_insee)
        if dept is None:
            result.stations_without_department += 1
            continue

        operator = normalized_operator(operator_label) or "INCONNU"
        operators_by_department[dept].add(operator)

        station_antennas = antennas_from_details(details)
        result.lines_without_aer += station_antennas.lines_without_aer

        # Memes regles que populate_current_stats : le detail par systeme fait foi
        # quand il existe, sinon on retombe sur le masque et le statut de la station.
        tech_keys = tech_keys_from_mask(int(tech_mask or 0))
        station_active = (
            bool(station_antennas.active_techs) or int(has_active or 0) == 1 or is_active_status(statut)
        )
        active_tech_keys = (
            station_antennas.active_techs
            if station_antennas.active_techs
            else (tech_keys if station_active else set())
        )

        station_supports = supports_by_station.get((id_anfr or "").strip(), set())
        distinct_antennas = len(station_antennas.distinct())
        distinct_active_antennas = len(station_antennas.distinct_active())

        totals_keys = ((dept, operator, ITEM_ALL), (dept, ITEM_ALL, ITEM_ALL))
        for key in totals_keys:
            station_counts[key] += 1
            if station_active:
                station_active_counts[key] += 1
            antenna_counts[key] += distinct_antennas
            antenna_active_counts[key] += distinct_active_antennas
            support_sets[key] |= station_supports
            if station_active:
                support_active_sets[key] |= station_supports

        # Union du masque de la station (issu du CSV hebdomadaire) et des systemes
        # reellement listes dans le detail : sinon une techno pourrait afficher des
        # antennes sans station, ou l'inverse.
        for tech in tech_keys | station_antennas.techs:
            tech_active = tech in active_tech_keys
            tech_antennas = len(station_antennas.by_tech.get(tech, set()))
            tech_active_antennas = len(station_antennas.active_by_tech.get(tech, set()))
            for key in ((dept, operator, tech), (dept, ITEM_ALL, tech)):
                station_counts[key] += 1
                if tech_active:
                    station_active_counts[key] += 1
                antenna_counts[key] += tech_antennas
                antenna_active_counts[key] += tech_active_antennas
                support_sets[key] |= station_supports
                if tech_active:
                    support_active_sets[key] |= station_supports

        fh_counts[dept] += len(station_antennas.fh)

    year = population_year or POPULATION_YEAR_DEFAULT
    department_rows = []
    for dept in sorted(operators_by_department):
        total_key = (dept, ITEM_ALL, ITEM_ALL)
        supports = len(support_sets.get(total_key, set()))
        stations = station_counts.get(total_key, 0)
        antennas = antenna_counts.get(total_key, 0)

        info = reference.get(dept)
        if info is None:
            result.departments_without_reference.append(dept)
            info = DepartmentReference(code=dept)
        area_km2 = info.area_km2
        population = info.population
        population_1k = (population / 1000.0) if population else None

        department_rows.append(
            (
                dept,
                info.name,
                info.region_code,
                area_km2,
                population,
                info.population_year or year,
                supports,
                len(support_active_sets.get(total_key, set())),
                stations,
                station_active_counts.get(total_key, 0),
                antennas,
                antenna_active_counts.get(total_key, 0),
                fh_counts.get(dept, 0),
                _ratio(stations, supports),
                _ratio(antennas, stations),
                _ratio(supports, area_km2),
                _ratio(stations, area_km2),
                _ratio(antennas, area_km2),
                _ratio(supports, population_1k),
                _ratio(stations, population_1k),
                _ratio(antennas, population_1k),
                _ratio(population, supports),
                _ratio(population, stations),
                _ratio(population, antennas),
            )
        )

    operator_tech_rows = []
    for dept in sorted(operators_by_department):
        operators = sorted(operators_by_department[dept]) + [ITEM_ALL]
        for operator in operators:
            for tech in list(TECH_KEYS) + [ITEM_ALL]:
                key = (dept, operator, tech)
                stations = station_counts.get(key, 0)
                antennas = antenna_counts.get(key, 0)
                supports = len(support_sets.get(key, set()))
                if not (stations or antennas or supports):
                    continue
                operator_tech_rows.append(
                    (
                        dept,
                        operator,
                        tech,
                        supports,
                        len(support_active_sets.get(key, set())),
                        stations,
                        station_active_counts.get(key, 0),
                        antennas,
                        antenna_active_counts.get(key, 0),
                    )
                )

    conn.execute("DELETE FROM dept_stat_current")
    conn.execute("DELETE FROM dept_stat_operator_tech")
    conn.executemany(
        """
        INSERT INTO dept_stat_current (
            dept_code, dept_name, region_code, area_km2, population, population_year,
            supports, supports_active, stations, stations_active,
            antennas, antennas_active, antennas_fh,
            stations_per_support, antennas_per_station,
            supports_per_km2, stations_per_km2, antennas_per_km2,
            supports_per_1k_hab, stations_per_1k_hab, antennas_per_1k_hab,
            hab_per_support, hab_per_station, hab_per_antenna
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        department_rows,
    )
    conn.executemany(
        """
        INSERT INTO dept_stat_operator_tech (
            dept_code, operator_name, tech,
            supports, supports_active, stations, stations_active, antennas, antennas_active
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        operator_tech_rows,
    )

    result.department_rows = len(department_rows)
    result.operator_tech_rows = len(operator_tech_rows)
    return result


def update_source_versions(conn: sqlite3.Connection, population_year: Optional[str] = None) -> None:
    """Trace la provenance des donnees de cadrage a cote des autres versions de sources."""
    year = population_year or POPULATION_YEAR_DEFAULT
    conn.executemany(
        "INSERT OR REPLACE INTO source_versions (source_key, source_value) VALUES (?, ?)",
        (
            ("dept_stats_population_source", POPULATION_SOURCE),
            ("dept_stats_population_year", year),
            ("dept_stats_area_source", AREA_SOURCE),
        ),
    )


def _reference_csv_columns(fieldnames: Iterable[str]) -> Dict[str, str]:
    lookup = {(name or "").strip().lower(): name for name in fieldnames}
    resolved: Dict[str, str] = {}
    for key, aliases in REFERENCE_CSV_ALIASES.items():
        for alias in aliases:
            if alias in lookup:
                resolved[key] = lookup[alias]
                break
    return resolved


def _float_or_none(value: Optional[str]) -> Optional[float]:
    text = (value or "").strip().replace(",", ".")
    for separator in (" ", chr(0x00A0), chr(0x202F)):  # espaces des milliers, insecables compris
        text = text.replace(separator, "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def _int_or_none(value: Optional[str]) -> Optional[int]:
    number = _float_or_none(value)
    return int(round(number)) if number is not None else None


def read_reference_csv(path: Path, population_year: Optional[str]) -> Dict[str, DepartmentReference]:
    """Lit un fichier de references pose a la main (prioritaire sur l'API)."""
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    first_line = text.splitlines()[0] if text.splitlines() else ""
    delimiter = ";" if first_line.count(";") >= first_line.count(",") else ","
    reader = csv.DictReader(io.StringIO(text), delimiter=delimiter)
    if not reader.fieldnames:
        return {}
    columns = _reference_csv_columns(reader.fieldnames)
    if "code" not in columns:
        return {}

    reference: Dict[str, DepartmentReference] = {}
    for row in reader:
        code = (row.get(columns["code"]) or "").strip().upper()
        if len(code) == 1:
            code = "0" + code
        if not code:
            continue
        reference[code] = DepartmentReference(
            code=code,
            name=(row.get(columns.get("name", "")) or "").strip() or None,
            region_code=(row.get(columns.get("region_code", "")) or "").strip() or None,
            area_km2=_float_or_none(row.get(columns.get("area_km2", ""))),
            population=_int_or_none(row.get(columns.get("population", ""))),
            population_year=(row.get(columns.get("population_year", "")) or "").strip() or population_year,
        )
    return reference


def _get_json(url: str, timeout: int = 30):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        if response.status != 200:
            raise OSError(f"HTTP {response.status} sur {url}")
        return json.loads(response.read().decode("utf-8"))


def _accumulate_communes(communes, totals: Dict[str, List[float]], forced_code: Optional[str] = None) -> None:
    for commune in communes:
        code = forced_code or (commune.get("codeDepartement") or department_code(commune.get("code")))
        if not code:
            continue
        entry = totals.setdefault(code, [0.0, 0, 0])
        surface = commune.get("surface")
        population = commune.get("population")
        if surface:
            entry[0] += float(surface)
        if population:
            entry[1] += int(population)
        entry[2] += 1


def fetch_reference_from_api(population_year: Optional[str]) -> Dict[str, DepartmentReference]:
    """Superficie et population par departement, agregees depuis les communes de geo.api.gouv.fr."""
    totals: Dict[str, List[float]] = {}
    reference: Dict[str, DepartmentReference] = {}
    try:
        departments = _get_json(f"{GEO_API_BASE}/departements?fields=nom,code,codeRegion")
        communes = _get_json(f"{GEO_API_BASE}/communes?fields=code,codeDepartement,population,surface")
    except (urllib.error.URLError, OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Reference departements: API geo.api.gouv.fr injoignable ({exc})")
        return {}

    _accumulate_communes(communes, totals)
    for department in departments:
        code = (department.get("code") or "").strip().upper()
        if not code:
            continue
        surface_ha, population, commune_count = totals.get(code, [0.0, 0, 0])
        reference[code] = DepartmentReference(
            code=code,
            name=(department.get("nom") or "").strip() or None,
            region_code=(department.get("codeRegion") or "").strip() or None,
            area_km2=round(surface_ha / 100.0, 3) if commune_count else None,
            # Population a None plutot qu'a zero : les communes des TAAF n'ont pas de champ
            # `population`, et « 0 habitant » s'afficherait comme une vraie valeur.
            population=population if commune_count and population else None,
            population_year=population_year,
        )

    # Les COM ne sont pas listees par /departements : on les interroge une par une.
    for code in EXTRA_DEPARTMENT_CODES:
        if code in reference:
            continue
        try:
            department = _get_json(f"{GEO_API_BASE}/departements/{code}")
            communes = _get_json(f"{GEO_API_BASE}/departements/{code}/communes?fields=code,population,surface")
        except (urllib.error.URLError, OSError, ValueError, json.JSONDecodeError) as exc:
            print(f"Reference departements: {code} indisponible ({exc})")
            continue
        extra_totals: Dict[str, List[float]] = {}
        _accumulate_communes(communes, extra_totals, forced_code=code)
        surface_ha, population, commune_count = extra_totals.get(code, [0.0, 0, 0])
        reference[code] = DepartmentReference(
            code=code,
            name=(department.get("nom") or "").strip() or None,
            region_code=(department.get("codeRegion") or "").strip() or None,
            area_km2=round(surface_ha / 100.0, 3) if commune_count else None,
            # Population a None plutot qu'a zero : les communes des TAAF n'ont pas de champ
            # `population`, et « 0 habitant » s'afficherait comme une vraie valeur.
            population=population if commune_count and population else None,
            population_year=population_year,
        )

    print(f"Reference departements: {len(reference)} departements depuis geo.api.gouv.fr")
    return reference


def write_reference_cache(references_dir: Path, reference: Dict[str, DepartmentReference]) -> None:
    try:
        references_dir.mkdir(parents=True, exist_ok=True)
        payload = {
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "population_source": POPULATION_SOURCE,
            "area_source": AREA_SOURCE,
            "departments": [
                {
                    "code": item.code,
                    "name": item.name,
                    "region_code": item.region_code,
                    "area_km2": item.area_km2,
                    "population": item.population,
                    "population_year": item.population_year,
                }
                for item in sorted(reference.values(), key=lambda item: item.code)
            ],
        }
        (references_dir / REFERENCE_CACHE_NAME).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    except OSError as exc:
        print(f"Reference departements: cache non ecrit ({exc})")


def read_reference_cache(references_dir: Path, population_year: Optional[str]) -> Dict[str, DepartmentReference]:
    cache_path = references_dir / REFERENCE_CACHE_NAME
    if not cache_path.is_file():
        return {}
    try:
        payload = json.loads(cache_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"Reference departements: cache illisible ({exc})")
        return {}

    reference: Dict[str, DepartmentReference] = {}
    for item in payload.get("departments", []):
        code = (item.get("code") or "").strip().upper()
        if not code:
            continue
        reference[code] = DepartmentReference(
            code=code,
            name=item.get("name"),
            region_code=item.get("region_code"),
            area_km2=item.get("area_km2"),
            population=item.get("population"),
            population_year=item.get("population_year") or population_year,
        )
    print(f"Reference departements: {len(reference)} departements depuis le cache {cache_path}")
    return reference


def load_department_reference(
    references_dir: Optional[Path] = None,
    population_year: Optional[str] = None,
    offline: bool = False,
) -> Dict[str, DepartmentReference]:
    """Superficie / population / nom par departement, avec repli en cascade.

    1. `references/departements_fr.csv` s'il existe (surcharge manuelle, par exemple
       pour utiliser les superficies officielles INSEE plutot que la somme des communes) ;
    2. geo.api.gouv.fr, dont la reponse est mise en cache ;
    3. le cache de la derniere reponse connue.
    """
    directory = Path(references_dir) if references_dir else REFERENCES_DIR
    csv_path = directory / REFERENCE_CSV_NAME
    if csv_path.is_file():
        reference = read_reference_csv(csv_path, population_year)
        if reference:
            print(f"Reference departements: {len(reference)} departements depuis {csv_path}")
            return reference
        print(f"Reference departements: {csv_path} inexploitable, on passe a l'API")

    if not offline:
        reference = fetch_reference_from_api(population_year)
        if reference:
            write_reference_cache(directory, reference)
            return reference

    reference = read_reference_cache(directory, population_year)
    if not reference:
        print("Reference departements: aucune donnee de cadrage, les ratios seront vides")
    return reference


def run_build(
    db_path: Path,
    references_dir: Optional[Path] = None,
    population_year: Optional[str] = None,
    offline: bool = False,
) -> DepartmentStatsResult:
    db_path = Path(db_path)
    if not db_path.exists():
        raise FileNotFoundError(f"Base SQLite introuvable: {db_path}")

    reference = load_department_reference(references_dir, population_year, offline)
    conn = sqlite3.connect(db_path)
    try:
        ensure_dept_stat_tables(conn)
        result = populate_department_stats(conn, reference, population_year)
        update_source_versions(conn, population_year)
        conn.commit()
    finally:
        conn.close()

    print(f"Stats departements: {result.department_rows} departements, {result.operator_tech_rows} lignes operateur/techno")
    if result.stations_without_department:
        print(f"Stats departements: {result.stations_without_department} stations sans code INSEE exploitable")
    if result.departments_without_reference:
        codes = ", ".join(result.departments_without_reference)
        print(f"Stats departements: sans superficie/population -> {codes}")
    if result.lines_without_aer:
        print(f"Stats departements: {result.lines_without_aer} lignes de frequences sans AER_ID (antennes non comptees)")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Calcule les statistiques par departement de la base GeoTower FR.")
    parser.add_argument("--imports-dir", type=Path, default=IMPORTS_DIR)
    parser.add_argument("--db", type=Path, default=None)
    parser.add_argument("--references-dir", type=Path, default=REFERENCES_DIR)
    parser.add_argument("--population-year", default=None, help=f"Millesime affiche (defaut {POPULATION_YEAR_DEFAULT})")
    parser.add_argument("--offline", action="store_true", help="N'appelle pas geo.api.gouv.fr, utilise le cache")
    args = parser.parse_args()

    db_path = args.db if args.db else Path(args.imports_dir) / DB_FILENAME
    run_build(
        db_path=db_path,
        references_dir=args.references_dir,
        population_year=args.population_year,
        offline=args.offline,
    )


if __name__ == "__main__":
    main()
