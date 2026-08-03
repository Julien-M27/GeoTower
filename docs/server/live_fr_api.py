import math
import os
import sqlite3
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException, Query

from feature_flags import (
    FEATURE_LIVE_API_FR,
    FEATURE_LIVE_API_FR_BBOX,
    FEATURE_LIVE_API_FR_NEARBY,
    FEATURE_LIVE_API_FR_SITE_DETAIL,
    FEATURE_LIVE_API_FR_STATS,
    get_feature_flags,
    require_feature_enabled,
)


IMPORTS_DIR = os.environ.get("GEOTOWER_IMPORTS_DIR", "/opt/geotower/data/imports")
LIVE_DB_FILENAME = "geotower_live_fr.db"
LIVE_DB_PATH = os.environ.get(
    "GEOTOWER_LIVE_FR_DB_PATH",
    str(Path(IMPORTS_DIR) / LIVE_DB_FILENAME),
)

LIMIT_NEARBY_MAX = "liveApiNearbyMaxLimit"
LIMIT_NEARBY_RADIUS_KM_MAX = "liveApiNearbyMaxRadiusKm"
LIMIT_BBOX_MAX = "liveApiBboxMaxLimit"
LIMIT_BBOX_MAX_DEGREES = "liveApiBboxMaxDegrees"

DEFAULT_NEARBY_LIMIT = 100
DEFAULT_NEARBY_MAX_LIMIT = 200
DEFAULT_NEARBY_RADIUS_KM = 10.0
DEFAULT_NEARBY_MAX_RADIUS_KM = 50
DEFAULT_BBOX_LIMIT = 500
DEFAULT_BBOX_MAX_LIMIT = 1000
DEFAULT_BBOX_MAX_DEGREES = 3
DEFAULT_STATS_MAX_OPERATORS = 20
EARTH_KM_PER_DEGREE = 111.32

router = APIRouter(prefix="/api/v2/live/fr", tags=["Live FR"])


def _limit_value(key: str, default: int) -> int:
    raw = get_feature_flags().get("limits", {}).get(key, default)
    return raw if isinstance(raw, int) and raw > 0 else default


def _db_path() -> Path:
    return Path(LIVE_DB_PATH)


def _ensure_live_enabled(feature_key: Optional[str] = None) -> None:
    require_feature_enabled(FEATURE_LIVE_API_FR, "API live FR desactivee.")
    if feature_key:
        require_feature_enabled(feature_key, "Endpoint API live FR desactive.")


def _db_connection() -> sqlite3.Connection:
    path = _db_path()
    if not path.is_file():
        raise HTTPException(status_code=503, detail="DB live FR introuvable.")

    try:
        conn = sqlite3.connect(f"file:{path}?mode=ro&immutable=1", uri=True)
        conn.row_factory = sqlite3.Row
        return conn
    except sqlite3.Error:
        raise HTTPException(status_code=503, detail="DB live FR indisponible.")


def _row_to_dict(row: sqlite3.Row) -> Dict[str, Any]:
    return {key: row[key] for key in row.keys()}


def _site_id_candidates(value: str) -> List[str]:
    trimmed = value.strip()
    if not trimmed:
        return []
    if not trimmed.isdigit():
        return [trimmed]

    compact = trimmed.lstrip("0") or "0"
    return list(dict.fromkeys([trimmed, compact, trimmed.zfill(10)]))


def _placeholders(values: List[str]) -> str:
    return ",".join("?" for _ in values)


def _normalized_operators(values: List[str]) -> List[str]:
    operators: List[str] = []
    for value in values:
        operator = value.strip().upper()
        if operator and operator not in operators:
            operators.append(operator)
    return operators[:DEFAULT_STATS_MAX_OPERATORS]


def _query_site_summaries_by_id(conn: sqlite3.Connection, site_id: str) -> List[Dict[str, Any]]:
    candidates = _site_id_candidates(site_id)
    summaries: List[sqlite3.Row] = []
    if candidates:
        summaries = conn.execute(
            f"""
            SELECT *
            FROM site_summary
            WHERE id_anfr IN ({_placeholders(candidates)})
            ORDER BY id_anfr
            LIMIT 50
            """,
            candidates,
        ).fetchall()

    if not summaries:
        support_candidates = candidates or [site_id.strip()]
        summaries = conn.execute(
            f"""
            SELECT DISTINCT s.*
            FROM site_summary s
            INNER JOIN site_support support ON support.id_anfr = s.id_anfr
            WHERE support.id_support IN ({_placeholders(support_candidates)})
               OR (
                    ? != ''
                    AND ? NOT GLOB '*[^0-9]*'
                    AND CAST(support.id_support AS INTEGER) = CAST(? AS INTEGER)
               )
            ORDER BY s.id_anfr
            LIMIT 50
            """,
            support_candidates + [site_id.strip(), site_id.strip(), site_id.strip()],
        ).fetchall()

    return [_row_to_dict(row) for row in summaries]


def _detail_payload(conn: sqlite3.Connection, id_anfr: str) -> Optional[Dict[str, Any]]:
    row = conn.execute(
        "SELECT * FROM site_detail WHERE id_anfr = ? LIMIT 1",
        (id_anfr,),
    ).fetchone()
    return _row_to_dict(row) if row else None


def _supports_payload(conn: sqlite3.Connection, id_anfr: str) -> List[Dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT *
        FROM site_support
        WHERE id_anfr = ?
        ORDER BY id_support
        """,
        (id_anfr,),
    ).fetchall()
    return [_row_to_dict(row) for row in rows]


def _antennas_payload(conn: sqlite3.Connection, id_anfr: str) -> List[Dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT *
        FROM site_antenna
        WHERE id_anfr = ?
        ORDER BY COALESCE(azimut, 9999), aer_id
        """,
        (id_anfr,),
    ).fetchall()
    return [_row_to_dict(row) for row in rows]


def _bounded_limit(raw_limit: int, default_limit: int, max_key: str, hard_default: int) -> int:
    max_limit = _limit_value(max_key, hard_default)
    return max(1, min(raw_limit or default_limit, max_limit))


def _valid_lat_lon(lat: float, lon: float) -> None:
    if not (-90.0 <= lat <= 90.0) or not (-180.0 <= lon <= 180.0):
        raise HTTPException(status_code=400, detail="Coordonnees invalides.")


def _site_summary_filters(
    params: Dict[str, Any],
    *,
    zb_only: bool = False,
    active_only: bool = False,
) -> str:
    filters = []
    if zb_only:
        filters.append("s.is_zb = 1")
    if active_only:
        filters.append("s.has_active = 1")
    return (" AND " + " AND ".join(filters)) if filters else ""


@router.get("/status", summary="Etat de la DB live FR")
async def get_live_fr_status():
    _ensure_live_enabled()
    with _db_connection() as conn:
        row = conn.execute(
            """
            SELECT
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
            FROM metadata
            LIMIT 1
            """
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=503, detail="Metadata DB live FR absente.")
        return _row_to_dict(row)


@router.get("/sites/nearby", summary="Sites FR proches d'une position")
async def get_live_fr_nearby_sites(
    lat: float = Query(..., ge=-90.0, le=90.0),
    lon: float = Query(..., ge=-180.0, le=180.0),
    limit: int = Query(DEFAULT_NEARBY_LIMIT, ge=1),
    radius_km: float = Query(DEFAULT_NEARBY_RADIUS_KM, gt=0.0),
    zb_only: bool = Query(False),
    active_only: bool = Query(False),
):
    _ensure_live_enabled(FEATURE_LIVE_API_FR_NEARBY)
    _valid_lat_lon(lat, lon)

    safe_limit = _bounded_limit(limit, DEFAULT_NEARBY_LIMIT, LIMIT_NEARBY_MAX, DEFAULT_NEARBY_MAX_LIMIT)
    max_radius_km = _limit_value(LIMIT_NEARBY_RADIUS_KM_MAX, DEFAULT_NEARBY_MAX_RADIUS_KM)
    safe_radius_km = min(radius_km, float(max_radius_km))
    lat_delta = safe_radius_km / EARTH_KM_PER_DEGREE
    lon_scale = max(math.cos(math.radians(lat)), 0.15)
    lon_delta = safe_radius_km / (EARTH_KM_PER_DEGREE * lon_scale)
    min_lat = max(-90.0, lat - lat_delta)
    max_lat = min(90.0, lat + lat_delta)
    min_lon = max(-180.0, lon - lon_delta)
    max_lon = min(180.0, lon + lon_delta)
    filters = _site_summary_filters({}, zb_only=zb_only, active_only=active_only)

    with _db_connection() as conn:
        rows = conn.execute(
            f"""
            SELECT
                s.*,
                ((s.latitude - :lat) * (s.latitude - :lat) +
                 ((s.longitude - :lon) * :lon_scale) * ((s.longitude - :lon) * :lon_scale)) AS distance_score
            FROM site_rtree r
            INNER JOIN site_summary s ON s.site_rowid = r.site_rowid
            WHERE r.min_lat <= :max_lat
              AND r.max_lat >= :min_lat
              AND r.min_lon <= :max_lon
              AND r.max_lon >= :min_lon
              {filters}
            ORDER BY distance_score ASC
            LIMIT :limit
            """,
            {
                "lat": lat,
                "lon": lon,
                "lon_scale": lon_scale,
                "min_lat": min_lat,
                "max_lat": max_lat,
                "min_lon": min_lon,
                "max_lon": max_lon,
                "limit": safe_limit,
            },
        ).fetchall()
        return {
            "country_code": "FR",
            "limit": safe_limit,
            "radius_km": safe_radius_km,
            "sites": [_row_to_dict(row) for row in rows],
        }


@router.get("/sites/bbox", summary="Sites FR dans une emprise carte")
async def get_live_fr_bbox_sites(
    north: float = Query(..., ge=-90.0, le=90.0),
    south: float = Query(..., ge=-90.0, le=90.0),
    east: float = Query(..., ge=-180.0, le=180.0),
    west: float = Query(..., ge=-180.0, le=180.0),
    limit: int = Query(DEFAULT_BBOX_LIMIT, ge=1),
    zb_only: bool = Query(False),
    active_only: bool = Query(False),
):
    _ensure_live_enabled(FEATURE_LIVE_API_FR_BBOX)
    min_lat = min(north, south)
    max_lat = max(north, south)
    min_lon = min(east, west)
    max_lon = max(east, west)
    max_degrees = _limit_value(LIMIT_BBOX_MAX_DEGREES, DEFAULT_BBOX_MAX_DEGREES)
    if (max_lat - min_lat) > max_degrees or (max_lon - min_lon) > max_degrees:
        raise HTTPException(status_code=400, detail="Emprise live FR trop grande.")

    safe_limit = _bounded_limit(limit, DEFAULT_BBOX_LIMIT, LIMIT_BBOX_MAX, DEFAULT_BBOX_MAX_LIMIT)
    filters = _site_summary_filters({}, zb_only=zb_only, active_only=active_only)

    with _db_connection() as conn:
        rows = conn.execute(
            f"""
            SELECT s.*
            FROM site_rtree r
            INNER JOIN site_summary s ON s.site_rowid = r.site_rowid
            WHERE r.min_lat <= :max_lat
              AND r.max_lat >= :min_lat
              AND r.min_lon <= :max_lon
              AND r.max_lon >= :min_lon
              {filters}
            ORDER BY s.id_anfr
            LIMIT :limit
            """,
            {
                "min_lat": min_lat,
                "max_lat": max_lat,
                "min_lon": min_lon,
                "max_lon": max_lon,
                "limit": safe_limit,
            },
        ).fetchall()
        return {
            "country_code": "FR",
            "limit": safe_limit,
            "sites": [_row_to_dict(row) for row in rows],
        }


@router.get("/stats/current", summary="Stats radio FR courantes")
async def get_live_fr_current_stats(operator: List[str] = Query(default=[])):
    _ensure_live_enabled(FEATURE_LIVE_API_FR_STATS)
    operators = _normalized_operators(operator)
    if not operators:
        return {"country_code": "FR", "operators": [], "rows": []}

    with _db_connection() as conn:
        rows = conn.execute(
            f"""
            SELECT
                category,
                item_key,
                COALESCE(MAX(label), '') AS label,
                CAST(SUM(total_count) AS INTEGER) AS total_count,
                CAST(SUM(active_count) AS INTEGER) AS active_count
            FROM radio_stat_current
            WHERE operator_name IN ({_placeholders(operators)})
            GROUP BY category, item_key
            """,
            operators,
        ).fetchall()
        return {
            "country_code": "FR",
            "operators": operators,
            "rows": [_row_to_dict(row) for row in rows],
        }


@router.get("/stats/weekly", summary="Stats radio FR hebdomadaires")
async def get_live_fr_weekly_stats(operator: List[str] = Query(default=[])):
    _ensure_live_enabled(FEATURE_LIVE_API_FR_STATS)
    operators = _normalized_operators(operator)
    if not operators:
        return {"country_code": "FR", "operators": [], "rows": []}

    with _db_connection() as conn:
        rows = conn.execute(
            f"""
            SELECT
                week_key,
                week_start,
                source_date,
                category,
                item_key,
                COALESCE(MAX(label), '') AS label,
                CAST(SUM(total_count) AS INTEGER) AS total_count,
                CAST(SUM(active_count) AS INTEGER) AS active_count
            FROM radio_stat_weekly
            WHERE operator_name IN ({_placeholders(operators)})
            GROUP BY week_key, week_start, source_date, category, item_key
            ORDER BY COALESCE(week_start, source_date, week_key) ASC, week_key ASC
            """,
            operators,
        ).fetchall()
        return {
            "country_code": "FR",
            "operators": operators,
            "rows": [_row_to_dict(row) for row in rows],
        }


@router.get("/sites/{site_id}", summary="Fiche live FR d'un site")
async def get_live_fr_site(site_id: str):
    _ensure_live_enabled(FEATURE_LIVE_API_FR_SITE_DETAIL)
    if not site_id.strip():
        raise HTTPException(status_code=400, detail="Identifiant site vide.")

    with _db_connection() as conn:
        matches = _query_site_summaries_by_id(conn, site_id)
        if not matches:
            raise HTTPException(status_code=404, detail="Site live FR introuvable.")

        primary_id = matches[0]["id_anfr"]
        return {
            "country_code": "FR",
            "summary": matches[0],
            "matches": matches,
            "detail": _detail_payload(conn, primary_id),
            "supports": _supports_payload(conn, primary_id),
            "antennas": _antennas_payload(conn, primary_id),
        }
