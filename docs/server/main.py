import base64
import glob
import hashlib
import json
import os
import re
import sqlite3
import time
import urllib.request
import zipfile
from urllib.parse import urlparse
from typing import Dict, List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from feature_flags import (
    FEATURE_APP_UPDATE_CHECK,
    FEATURE_DATABASE_DOWNLOAD,
    FEATURE_DATABASE_UPDATE_CHECK,
    FEATURE_ENB_DATABASE,
    FEATURE_OFFLINE_MAPS_CATALOG,
    FEATURE_OUTAGES_DATA,
    get_feature_flags,
    is_feature_enabled,
    require_feature_enabled,
)
from legal_pages import router as legal_router
from live_fr_api import router as live_fr_router
from signalquest_proxy import router as signalquest_router


description = """
# GeoTower API
Bienvenue sur l'interface backend officielle de **GeoTower**.

Ce portail gere les acces au telechargement de la base de donnees complete,
au catalogue des cartes vectorielles hors-ligne et au proxy SignalQuest.
"""

app = FastAPI(
    title="GeoTower API",
    description=description,
    version="2.1.1",
    contact={
        "name": "Support GeoTower",
        "url": "https://api.geotower.fr",
    },
    docs_url=None,
    redoc_url=None,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],            # donnees publiques -> joker OK (en prod : ["https://ton-site.fr"])
    allow_credentials=False,        # pas de cookies/session client -> obligatoire avec le joker "*"
    allow_methods=["GET", "POST"],  # GET = donnees, POST = proxy SignalQuest
    allow_headers=["*"],            # autorise X-GeoTower-Upload-Token & co.
)

app.include_router(signalquest_router)
app.include_router(live_fr_router)
app.include_router(legal_router)


# --- CONFIGURATION HORS-LIGNE ---
IMPORTS_DIR = "/opt/geotower/data/imports"
DB_FILENAME = "geotower_fr.db"
DB_SCHEMA_VERSION = 7
DB_COUNTRY_CODE = "FR"
DB_COUNTRY_NAME = "France"
DB_SOURCE = "ANFR"
RADIO_DB_FILENAME = "geotower_fr_radio.db"
RADIO_DB_SCHEMA_VERSION = 1
RADIO_DB_COUNTRY_CODE = "FR"
RADIO_DB_COUNTRY_NAME = "France"
RADIO_DB_SOURCE = "ANFR_RADIO"
# Base des identifiants reseau eNB/gNB : donnee du partenaire eNB-Analytics, batie par
# build_fr_enb_db.py (cron hebdomadaire). Redistribution autorisee par le partenaire.
ENB_DB_FILENAME = "geotower_fr_enb.db"
ENB_DB_SCHEMA_VERSION = 1
ENB_DB_COUNTRY_CODE = "FR"
ENB_DB_COUNTRY_NAME = "France"
ENB_DB_SOURCE = "ENB_ANALYTICS"
MONTHLY_ANFR_ZIP_REQUIRED_FILES = {
    "SUP_STATION.txt",
    "SUP_SUPPORT.txt",
    "SUP_ANTENNE.txt",
    "SUP_EMETTEUR.txt",
    "SUP_BANDE.txt",
}

DB_OFFLINE_PATH = os.path.join(IMPORTS_DIR, DB_FILENAME)
VERSION_FR_OFFLINE_PATH = os.path.join(IMPORTS_DIR, "version_fr.json")
RADIO_DB_OFFLINE_PATH = os.path.join(IMPORTS_DIR, RADIO_DB_FILENAME)
VERSION_FR_RADIO_OFFLINE_PATH = os.path.join(IMPORTS_DIR, "version_fr_radio.json")
ENB_DB_OFFLINE_PATH = os.path.join(IMPORTS_DIR, ENB_DB_FILENAME)
VERSION_FR_ENB_OFFLINE_PATH = os.path.join(IMPORTS_DIR, "version_fr_enb.json")
SITES_HS_DIR = os.environ.get("GEOTOWER_SITES_HS_DIR", "/opt/geotower/data/sites_hs")
SITES_HS_PATH = os.environ.get("GEOTOWER_SITES_HS_PATH", os.path.join(SITES_HS_DIR, "sites_hs.geojson"))
SITES_HS_DATE_PATH = os.environ.get(
    "GEOTOWER_SITES_HS_DATE_PATH",
    os.path.join(SITES_HS_DIR, "sites_hs_date.txt"),
)
APP_LATEST_PATH = "/opt/geotower/data/app/latest.json"
# Hote public de CETTE instance : api.geotower.fr par defaut, https://api.cajejuma.fr sur le
# miroir. Sert a fabriquer les URLs absolues du manifeste signe et du JSON de version.
PUBLIC_API_BASE_URL = os.environ.get("GEOTOWER_PUBLIC_API_BASE_URL", "https://api.geotower.fr").rstrip("/")
APP_DOWNLOAD_ALLOWED_URLS = [
    ("kdrive.infomaniak.com", ["/app/share/2149816/6d30423f-ac8b-4509-9857-86684d3a2e03"]),
    ("api.geotower.fr", ["/downloads/geotower/"]),
    # Miroir de secours : l'app accepte les deux hotes (voir ApiEndpoints cote Android).
    ("api.cajejuma.fr", ["/downloads/geotower/"]),
    ("play.google.com", ["/store/apps/details"]),
]
OFFLINE_MAP_SHA256_JSON_ENV = "GEOTOWER_OFFLINE_MAP_SHA256_JSON"
OFFLINE_MAP_SHA256_PATH_ENV = "GEOTOWER_OFFLINE_MAP_SHA256_PATH"
MANIFEST_SIGNING_KEY_PEM_ENV = "GEOTOWER_MANIFEST_SIGNING_KEY_PEM"
MANIFEST_SIGNING_KEY_PATH_ENV = "GEOTOWER_MANIFEST_SIGNING_KEY_PATH"
MANIFEST_KEY_ID_ENV = "GEOTOWER_MANIFEST_KEY_ID"
MANIFEST_TTL_SECONDS = int(os.environ.get("GEOTOWER_MANIFEST_TTL_SECONDS", str(7 * 24 * 60 * 60)))
MANIFEST_ALGORITHM = "SHA256withECDSA"
SHA256_RE = re.compile(r"^[A-Fa-f0-9]{64}$")
_FILE_SHA256_CACHE = {}


class OfflineMap(BaseModel):
    id: str = Field(..., examples=["alsace"])
    name: str = Field(..., examples=["Alsace"])
    description: str = Field(...)
    map_url: str = Field(...)
    estimated_size_mb: int = Field(...)
    map_filename: str = Field(...)
    sha256: str = Field(...)


class AppLatestRelease(BaseModel):
    schemaVersion: int = Field(1, examples=[1])
    releaseId: str = Field(..., examples=["geotower-2026-05-13-01"])
    versionName: str = Field(..., examples=["1.9.9.2.5"])
    publishedAt: Optional[str] = Field(None, examples=["2026-05-13"])
    downloadUrl: str = Field(
        ...,
        examples=["https://kdrive.infomaniak.com/app/share/2149816/6d30423f-ac8b-4509-9857-86684d3a2e03"],
    )
    fileName: Optional[str] = Field(None, examples=["GeoTower.apk"])
    notes: Optional[str] = Field(None, examples=["Corrections et ameliorations."])
    notesTranslations: Optional[Dict[str, str]] = Field(
        None,
        examples=[{"fr": "Corrections et ameliorations.", "en": "Fixes and improvements."}],
    )


def is_monthly_anfr_zip(path: str) -> bool:
    try:
        with zipfile.ZipFile(path, "r") as archive:
            names = {os.path.basename(name).upper() for name in archive.namelist()}
    except (OSError, zipfile.BadZipFile):
        return False
    return all(filename.upper() in names for filename in MONTHLY_ANFR_ZIP_REQUIRED_FILES)


def latest_zip_name() -> str:
    zip_files = glob.glob(os.path.join(IMPORTS_DIR, "france_sources", "**", "*.zip"), recursive=True)
    zip_files.extend(glob.glob(os.path.join(IMPORTS_DIR, "*.zip")))
    zip_files = [path for path in zip_files if is_monthly_anfr_zip(path)]
    if not zip_files:
        return "Aucun zip mensuel ANFR trouve"
    return os.path.basename(max(zip_files, key=os.path.getmtime))


def extract_quarterly_version_from_name(name: str) -> Optional[str]:
    quarter_patterns = (
        (r"(20\d{2})[\s_\-]*(?:t|q|trim(?:estre)?)[\s_\-]*([1-4])", 1, 2),
        (r"(?:t|q|trim(?:estre)?)[\s_\-]*([1-4])[\s_\-]*(20\d{2})", 2, 1),
        (r"([1-4])(?:er|e)?[\s_\-]*(?:trim(?:estre)?)[\s_\-]*(20\d{2})", 2, 1),
    )
    for pattern, year_group, quarter_group in quarter_patterns:
        match = re.search(pattern, name, re.IGNORECASE)
        if match:
            return f"{match.group(year_group)}-T{int(match.group(quarter_group))}"
    return None


def latest_quarterly_version_name() -> Optional[str]:
    candidates = []
    patterns = (
        os.path.join(IMPORTS_DIR, "france_sources", "**", "*.csv"),
        os.path.join(IMPORTS_DIR, "france_sources", "**", "*.zip"),
        os.path.join(IMPORTS_DIR, "*.csv"),
        os.path.join(IMPORTS_DIR, "*.zip"),
    )
    for pattern in patterns:
        for path in glob.glob(pattern, recursive=True):
            version = extract_quarterly_version_from_name(os.path.basename(path))
            if not version:
                continue
            year, quarter = version.split("-T", 1)
            candidates.append((int(year), int(quarter), version))
    if not candidates:
        return None
    return max(candidates)[2]


def read_version_file() -> dict:
    if not os.path.exists(VERSION_FR_OFFLINE_PATH):
        return {}
    try:
        with open(VERSION_FR_OFFLINE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def read_radio_version_file() -> dict:
    if not os.path.exists(VERSION_FR_RADIO_OFFLINE_PATH):
        return {}
    try:
        with open(VERSION_FR_RADIO_OFFLINE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def read_enb_version_file() -> dict:
    if not os.path.exists(VERSION_FR_ENB_OFFLINE_PATH):
        return {}
    try:
        with open(VERSION_FR_ENB_OFFLINE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def read_sqlite_metadata() -> dict:
    if not os.path.exists(DB_OFFLINE_PATH):
        return {}

    conn = None
    try:
        conn = sqlite3.connect(f"file:{DB_OFFLINE_PATH}?mode=ro", uri=True)
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            """
            SELECT version, schema_version, country_code, country_name, source, date_maj_anfr, zip_version
            FROM metadata
            LIMIT 1
            """
        ).fetchone()
        return dict(row) if row else {}
    except Exception:
        return {}
    finally:
        if conn is not None:
            conn.close()


def read_radio_sqlite_metadata() -> dict:
    if not os.path.exists(RADIO_DB_OFFLINE_PATH):
        return {}

    conn = None
    try:
        conn = sqlite3.connect(f"file:{RADIO_DB_OFFLINE_PATH}?mode=ro", uri=True)
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            """
            SELECT version, schema_version, country_code, country_name, source,
                   date_maj_anfr, zip_version, row_count
            FROM metadata
            LIMIT 1
            """
        ).fetchone()
        return dict(row) if row else {}
    except Exception:
        return {}
    finally:
        if conn is not None:
            conn.close()


def read_enb_sqlite_metadata() -> dict:
    if not os.path.exists(ENB_DB_OFFLINE_PATH):
        return {}

    conn = None
    try:
        conn = sqlite3.connect(f"file:{ENB_DB_OFFLINE_PATH}?mode=ro", uri=True)
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            """
            SELECT version, schema_version, country_code, country_name, source,
                   source_date, generated_at, row_count
            FROM metadata
            LIMIT 1
            """
        ).fetchone()
        return dict(row) if row else {}
    except Exception:
        return {}
    finally:
        if conn is not None:
            conn.close()


def read_enb_sqlite_operators() -> list:
    """Fraicheur par operateur (table enb_source) : sert le libelle de la carte cote app."""
    if not os.path.exists(ENB_DB_OFFLINE_PATH):
        return []

    conn = None
    try:
        conn = sqlite3.connect(f"file:{ENB_DB_OFFLINE_PATH}?mode=ro", uri=True)
        conn.row_factory = sqlite3.Row
        rows = conn.execute(
            """
            SELECT plmn, mnc, mnc_list, operator, source_date, row_count, from_cache
            FROM enb_source
            ORDER BY mnc
            """
        ).fetchall()
        return [dict(row) for row in rows]
    except Exception:
        return []
    finally:
        if conn is not None:
            conn.close()


def read_sqlite_source_versions() -> dict:
    if not os.path.exists(DB_OFFLINE_PATH):
        return {}

    conn = None
    try:
        conn = sqlite3.connect(f"file:{DB_OFFLINE_PATH}?mode=ro", uri=True)
        return {
            row[0]: row[1]
            for row in conn.execute(
                """
                SELECT source_key, source_value
                FROM source_versions
                """
            ).fetchall()
        }
    except Exception:
        return {}
    finally:
        if conn is not None:
            conn.close()


def file_sha256(path: str) -> str:
    stat = os.stat(path)
    cache_key = (path, stat.st_mtime, stat.st_size)
    cached = _FILE_SHA256_CACHE.get(cache_key)
    if cached:
        return cached

    digest = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)

    value = digest.hexdigest()
    _FILE_SHA256_CACHE.clear()
    _FILE_SHA256_CACHE[cache_key] = value
    return value


def b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def public_api_url(path: str) -> str:
    return f"{PUBLIC_API_BASE_URL}{path}"


def canonical_json_bytes(payload: dict) -> bytes:
    return json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")


def manifest_signing_key_pem() -> str:
    raw = os.environ.get(MANIFEST_SIGNING_KEY_PEM_ENV, "").strip()
    if raw:
        return raw.replace("\\n", "\n")

    key_path = os.environ.get(MANIFEST_SIGNING_KEY_PATH_ENV, "").strip()
    if key_path and os.path.exists(key_path):
        with open(key_path, "r", encoding="utf-8") as f:
            return f.read()

    raise HTTPException(status_code=503, detail="Cle privee de manifeste non configuree.")


def sign_manifest_payload(payload_bytes: bytes) -> str:
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
    except ImportError:
        raise HTTPException(status_code=503, detail="Dependance cryptography manquante.")

    try:
        private_key = serialization.load_pem_private_key(
            manifest_signing_key_pem().encode("utf-8"),
            password=None,
        )
        signature = private_key.sign(payload_bytes, ec.ECDSA(hashes.SHA256()))
        return b64url_encode(signature)
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=503, detail="Signature de manifeste impossible.")


def is_official_app_download_url(raw_url: str) -> bool:
    try:
        parsed = urlparse(raw_url.strip())
    except Exception:
        return False
    if parsed.scheme.lower() != "https":
        return False
    if parsed.username or parsed.password:
        return False
    host = (parsed.hostname or "").lower()
    return any(
        host == allowed_host and any(parsed.path.startswith(prefix) for prefix in path_prefixes)
        for allowed_host, path_prefixes in APP_DOWNLOAD_ALLOWED_URLS
    )


def offline_db_version_payload() -> dict:
    version_data = read_version_file()
    metadata = read_sqlite_metadata()
    source_versions = read_sqlite_source_versions()
    db_stat = os.stat(DB_OFFLINE_PATH) if os.path.exists(DB_OFFLINE_PATH) else None

    return {
        "version": version_data.get("version") or metadata.get("version"),
        "schema_version": int(version_data.get("schema_version") or metadata.get("schema_version") or DB_SCHEMA_VERSION),
        "country_code": version_data.get("country_code") or metadata.get("country_code") or DB_COUNTRY_CODE,
        "country_name": version_data.get("country_name") or metadata.get("country_name") or DB_COUNTRY_NAME,
        "source": version_data.get("source") or metadata.get("source") or DB_SOURCE,
        "date_anfr": version_data.get("date_anfr") or metadata.get("date_maj_anfr") or "Inconnue",
        "zip_name": version_data.get("zip_name") or metadata.get("zip_version") or latest_zip_name(),
        "quarterly_version": (
            version_data.get("quarterly_version")
            or source_versions.get("quarterly_version")
            or latest_quarterly_version_name()
        ),
        "db_name": version_data.get("db_name") or DB_FILENAME,
        "size_bytes": db_stat.st_size if db_stat else None,
        "sha256": file_sha256(DB_OFFLINE_PATH) if db_stat else None,
    }


def radio_db_version_payload() -> dict:
    version_data = read_radio_version_file()
    metadata = read_radio_sqlite_metadata()
    db_stat = os.stat(RADIO_DB_OFFLINE_PATH) if os.path.exists(RADIO_DB_OFFLINE_PATH) else None

    return {
        "version": version_data.get("version") or metadata.get("version"),
        "schema_version": int(
            version_data.get("schema_version") or metadata.get("schema_version") or RADIO_DB_SCHEMA_VERSION
        ),
        "country_code": version_data.get("country_code") or metadata.get("country_code") or RADIO_DB_COUNTRY_CODE,
        "country_name": version_data.get("country_name") or metadata.get("country_name") or RADIO_DB_COUNTRY_NAME,
        "source": version_data.get("source") or metadata.get("source") or RADIO_DB_SOURCE,
        "date_anfr": version_data.get("date_anfr") or metadata.get("date_maj_anfr") or "Inconnue",
        "zip_name": version_data.get("zip_name") or metadata.get("zip_version") or latest_zip_name(),
        "db_name": version_data.get("db_name") or RADIO_DB_FILENAME,
        "row_count": int(version_data.get("row_count") or metadata.get("row_count") or 0),
        "detail_row_count": int(version_data.get("detail_row_count") or 0),
        "size_bytes": db_stat.st_size if db_stat else None,
        "sha256": file_sha256(RADIO_DB_OFFLINE_PATH) if db_stat else None,
    }


def enb_db_version_payload() -> dict:
    version_data = read_enb_version_file()
    metadata = read_enb_sqlite_metadata()
    db_stat = os.stat(ENB_DB_OFFLINE_PATH) if os.path.exists(ENB_DB_OFFLINE_PATH) else None

    return {
        "version": version_data.get("version") or metadata.get("version"),
        "schema_version": int(
            version_data.get("schema_version") or metadata.get("schema_version") or ENB_DB_SCHEMA_VERSION
        ),
        "country_code": version_data.get("country_code") or metadata.get("country_code") or ENB_DB_COUNTRY_CODE,
        "country_name": version_data.get("country_name") or metadata.get("country_name") or ENB_DB_COUNTRY_NAME,
        "source": version_data.get("source") or metadata.get("source") or ENB_DB_SOURCE,
        "source_date": version_data.get("source_date") or metadata.get("source_date"),
        "db_name": version_data.get("db_name") or ENB_DB_FILENAME,
        "row_count": int(version_data.get("row_count") or metadata.get("row_count") or 0),
        "operators": version_data.get("operators") or read_enb_sqlite_operators(),
        "size_bytes": db_stat.st_size if db_stat else None,
        "sha256": file_sha256(ENB_DB_OFFLINE_PATH) if db_stat else None,
    }


@app.get("/", response_class=HTMLResponse, include_in_schema=False)
async def root():
    html_content = """
    <!DOCTYPE html>
    <html>
        <head>
            <title>GeoTower API</title>
            <link rel="icon" type="image/png" href="/favicon.png">
            <style>
                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background-color: #121212;
                    color: #ffffff;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    height: 100vh;
                    margin: 0;
                }
                .container {
                    background-color: #1e1e1e;
                    padding: 50px;
                    border-radius: 16px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.8);
                    text-align: center;
                    max-width: 500px;
                }
                h1 {
                    color: #4CAF50;
                    margin-bottom: 10px;
                    font-size: 2.5em;
                }
                p {
                    color: #b0b0b0;
                    margin-bottom: 40px;
                    font-size: 1.1em;
                    line-height: 1.5;
                }
                .button {
                    background-color: #4CAF50;
                    color: white;
                    padding: 14px 28px;
                    text-decoration: none;
                    border-radius: 8px;
                    font-weight: bold;
                    font-size: 1.1em;
                    transition: all 0.3s ease;
                    display: inline-block;
                }
                .button:hover {
                    background-color: #45a049;
                    transform: translateY(-2px);
                    box-shadow: 0 4px 12px rgba(76, 175, 80, 0.3);
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>GeoTower API</h1>
                <p>L'interface backend connectant votre application au catalogue des cartes et donnees hors-ligne.</p>
                <a href="/api-geotower-docs" class="button">Acceder a la Documentation</a>
            </div>
        </body>
    </html>
    """
    return HTMLResponse(content=html_content)


@app.get("/favicon.png", include_in_schema=False)
async def favicon():
    icon_path = "/opt/geotower/api/favicon.png"
    if os.path.exists(icon_path):
        return FileResponse(icon_path, media_type="image/png")
    raise HTTPException(status_code=404, detail="Icone introuvable")


@app.get("/api-geotower-docs", include_in_schema=False)
async def scalar_docs():
    html_content = f"""
    <!doctype html>
    <html>
      <head>
        <title>{app.title} - Documentation</title>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <link rel="icon" type="image/png" href="/favicon.png">
        <style>
          body {{ margin: 0; padding: 0; }}
        </style>
      </head>
      <body>
        <script
          id="api-reference"
          data-url="/openapi.json"
          data-configuration='{{"theme":"default","layout":"sidebar"}}'
        ></script>
        <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
      </body>
    </html>
    """
    return HTMLResponse(content=html_content)


@app.get("/api/v2/download/db", tags=["Base de Donnees"], summary="Telecharger la BDD SQLite FR")
async def download_offline_db():
    require_feature_enabled(FEATURE_DATABASE_DOWNLOAD, "Telechargement de la base desactive.")
    if not os.path.exists(DB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier DB FR introuvable.")
    return FileResponse(
        path=DB_OFFLINE_PATH,
        filename=DB_FILENAME,
        media_type="application/vnd.sqlite3",
        headers={"Cache-Control": "no-cache"},
    )


@app.get("/api/v2/db/info", tags=["Base de Donnees"], summary="Infos sur la version BDD FR")
async def get_db_info():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Infos base de donnees desactivees.")
    if not os.path.exists(DB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier DB FR introuvable.")

    stat = os.stat(DB_OFFLINE_PATH)
    version_payload = offline_db_version_payload()

    return {
        "size_bytes": stat.st_size,
        "sha256": file_sha256(DB_OFFLINE_PATH),
        "last_modified": stat.st_mtime,
        "filename": DB_FILENAME,
        "version": version_payload.get("version"),
        "schema_version": version_payload["schema_version"],
        "country_code": version_payload["country_code"],
        "country_name": version_payload["country_name"],
        "source": version_payload["source"],
        "zip_version": version_payload["zip_name"],
        "date_anfr": version_payload["date_anfr"],
        "quarterly_version": version_payload.get("quarterly_version"),
    }


@app.get("/api/v2/download/version_fr", tags=["Base de Donnees"], summary="Fichier de version JSON FR")
async def get_offline_db_version():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Version base de donnees desactivee.")
    if not os.path.exists(DB_OFFLINE_PATH) and not os.path.exists(VERSION_FR_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier de version introuvable.")
    return JSONResponse(content=offline_db_version_payload())


@app.get("/api/v2/download/version", tags=["Base de Donnees"], summary="Alias legacy du fichier de version JSON FR")
async def get_offline_db_version_legacy():
    return await get_offline_db_version()


@app.get("/api/v2/download/radio_db", tags=["Base de Donnees"], summary="Telecharger la BDD SQLite FR radio")
async def download_radio_db():
    require_feature_enabled(FEATURE_DATABASE_DOWNLOAD, "Telechargement de la base radio desactive.")
    if not os.path.exists(RADIO_DB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier DB radio FR introuvable.")
    return FileResponse(
        path=RADIO_DB_OFFLINE_PATH,
        filename=RADIO_DB_FILENAME,
        media_type="application/vnd.sqlite3",
        headers={"Cache-Control": "no-cache"},
    )


@app.get("/api/v2/radio_db/info", tags=["Base de Donnees"], summary="Infos sur la version BDD FR radio")
async def get_radio_db_info():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Infos base radio desactivees.")
    if not os.path.exists(RADIO_DB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier DB radio FR introuvable.")

    stat = os.stat(RADIO_DB_OFFLINE_PATH)
    version_payload = radio_db_version_payload()

    return {
        "size_bytes": stat.st_size,
        "sha256": file_sha256(RADIO_DB_OFFLINE_PATH),
        "last_modified": stat.st_mtime,
        "filename": RADIO_DB_FILENAME,
        "version": version_payload.get("version"),
        "schema_version": version_payload["schema_version"],
        "country_code": version_payload["country_code"],
        "country_name": version_payload["country_name"],
        "source": version_payload["source"],
        "zip_version": version_payload["zip_name"],
        "date_anfr": version_payload["date_anfr"],
        "row_count": version_payload.get("row_count", 0),
        "detail_row_count": version_payload.get("detail_row_count", 0),
    }


@app.get("/api/v2/download/version_fr_radio", tags=["Base de Donnees"], summary="Fichier de version JSON FR radio")
async def get_radio_db_version():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Version base radio desactivee.")
    if not os.path.exists(RADIO_DB_OFFLINE_PATH) and not os.path.exists(VERSION_FR_RADIO_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier de version radio introuvable.")
    return JSONResponse(content=radio_db_version_payload())


@app.get("/api/v2/download/enb_db", tags=["Base de Donnees"], summary="Telecharger la BDD SQLite FR eNB/gNB")
async def download_enb_db():
    require_feature_enabled(FEATURE_DATABASE_DOWNLOAD, "Telechargement de la base eNB desactive.")
    require_feature_enabled(FEATURE_ENB_DATABASE, "Base des identifiants eNB desactivee.")
    if not os.path.exists(ENB_DB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier DB eNB FR introuvable.")
    return FileResponse(
        path=ENB_DB_OFFLINE_PATH,
        filename=ENB_DB_FILENAME,
        media_type="application/vnd.sqlite3",
        headers={"Cache-Control": "no-cache"},
    )


@app.get("/api/v2/enb_db/info", tags=["Base de Donnees"], summary="Infos sur la version BDD FR eNB/gNB")
async def get_enb_db_info():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Infos base eNB desactivees.")
    require_feature_enabled(FEATURE_ENB_DATABASE, "Base des identifiants eNB desactivee.")
    if not os.path.exists(ENB_DB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier DB eNB FR introuvable.")

    stat = os.stat(ENB_DB_OFFLINE_PATH)
    version_payload = enb_db_version_payload()

    return {
        "size_bytes": stat.st_size,
        "sha256": file_sha256(ENB_DB_OFFLINE_PATH),
        "last_modified": stat.st_mtime,
        "filename": ENB_DB_FILENAME,
        "version": version_payload.get("version"),
        "schema_version": version_payload["schema_version"],
        "country_code": version_payload["country_code"],
        "country_name": version_payload["country_name"],
        "source": version_payload["source"],
        "source_date": version_payload.get("source_date"),
        "row_count": version_payload.get("row_count", 0),
        "operators": version_payload.get("operators", []),
    }


@app.get("/api/v2/download/version_fr_enb", tags=["Base de Donnees"], summary="Fichier de version JSON FR eNB/gNB")
async def get_enb_db_version():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Version base eNB desactivee.")
    require_feature_enabled(FEATURE_ENB_DATABASE, "Base des identifiants eNB desactivee.")
    if not os.path.exists(ENB_DB_OFFLINE_PATH) and not os.path.exists(VERSION_FR_ENB_OFFLINE_PATH):
        raise HTTPException(status_code=404, detail="Fichier de version eNB introuvable.")
    return JSONResponse(content=enb_db_version_payload())


async def download_manifest_payload() -> dict:
    now = int(time.time())
    version_payload = offline_db_version_payload() if os.path.exists(DB_OFFLINE_PATH) else {}
    db_payload = None
    if os.path.exists(DB_OFFLINE_PATH):
        stat = os.stat(DB_OFFLINE_PATH)
        db_payload = {
            "filename": DB_FILENAME,
            "url": public_api_url("/api/v2/download/db"),
            "size_bytes": stat.st_size,
            "sha256": file_sha256(DB_OFFLINE_PATH),
            "schema_version": version_payload.get("schema_version", DB_SCHEMA_VERSION),
            "country_code": version_payload.get("country_code", DB_COUNTRY_CODE),
            "country_name": version_payload.get("country_name", DB_COUNTRY_NAME),
            "source": version_payload.get("source", DB_SOURCE),
            "version": version_payload.get("version"),
            "date_anfr": version_payload.get("date_anfr"),
            "quarterly_version": version_payload.get("quarterly_version"),
        }

    radio_version_payload = radio_db_version_payload() if os.path.exists(RADIO_DB_OFFLINE_PATH) else {}
    radio_db_payload = None
    if os.path.exists(RADIO_DB_OFFLINE_PATH):
        stat = os.stat(RADIO_DB_OFFLINE_PATH)
        radio_db_payload = {
            "filename": RADIO_DB_FILENAME,
            "url": public_api_url("/api/v2/download/radio_db"),
            "size_bytes": stat.st_size,
            "sha256": file_sha256(RADIO_DB_OFFLINE_PATH),
            "schema_version": radio_version_payload.get("schema_version", RADIO_DB_SCHEMA_VERSION),
            "country_code": radio_version_payload.get("country_code", RADIO_DB_COUNTRY_CODE),
            "country_name": radio_version_payload.get("country_name", RADIO_DB_COUNTRY_NAME),
            "source": radio_version_payload.get("source", RADIO_DB_SOURCE),
            "version": radio_version_payload.get("version"),
            "date_anfr": radio_version_payload.get("date_anfr"),
            "row_count": radio_version_payload.get("row_count"),
            "detail_row_count": radio_version_payload.get("detail_row_count"),
        }

    # Kill-switch partenaire : hors manifeste = invisible pour l'app, aucune URL a bloquer.
    enb_db_payload = None
    if os.path.exists(ENB_DB_OFFLINE_PATH) and is_feature_enabled(FEATURE_ENB_DATABASE):
        enb_version_payload = enb_db_version_payload()
        stat = os.stat(ENB_DB_OFFLINE_PATH)
        enb_db_payload = {
            "filename": ENB_DB_FILENAME,
            "url": public_api_url("/api/v2/download/enb_db"),
            "size_bytes": stat.st_size,
            "sha256": file_sha256(ENB_DB_OFFLINE_PATH),
            "schema_version": enb_version_payload.get("schema_version", ENB_DB_SCHEMA_VERSION),
            "country_code": enb_version_payload.get("country_code", ENB_DB_COUNTRY_CODE),
            "country_name": enb_version_payload.get("country_name", ENB_DB_COUNTRY_NAME),
            "source": enb_version_payload.get("source", ENB_DB_SOURCE),
            "version": enb_version_payload.get("version"),
            "source_date": enb_version_payload.get("source_date"),
            "row_count": enb_version_payload.get("row_count"),
        }

    maps_payload = await get_maps_catalog()
    return {
        "schemaVersion": 1,
        "generatedAt": now,
        "expiresAt": now + max(300, MANIFEST_TTL_SECONDS),
        "db": db_payload,
        "radio_db": radio_db_payload,
        "enb_db": enb_db_payload,
        "maps": maps_payload,
    }


@app.get("/api/v2/download/manifest", tags=["Base de Donnees"], summary="Manifeste signe DB et cartes")
async def get_download_manifest():
    require_feature_enabled(FEATURE_DATABASE_UPDATE_CHECK, "Manifeste de telechargement desactive.")
    key_id = os.environ.get(MANIFEST_KEY_ID_ENV, "").strip()
    if not key_id:
        raise HTTPException(status_code=503, detail="Identifiant de cle de manifeste non configure.")

    payload = await download_manifest_payload()
    payload_bytes = canonical_json_bytes(payload)
    return {
        "schemaVersion": 1,
        "keyId": key_id,
        "algorithm": MANIFEST_ALGORITHM,
        "payload": b64url_encode(payload_bytes),
        "signature": sign_manifest_payload(payload_bytes),
    }


@app.get(
    "/api/v2/app/latest",
    response_model=AppLatestRelease,
    tags=["Application"],
    summary="Derniere version APK GeoTower",
)
async def get_latest_app_release():
    require_feature_enabled(FEATURE_APP_UPDATE_CHECK, "Verification de mise a jour application desactivee.")
    if not os.path.exists(APP_LATEST_PATH):
        raise HTTPException(status_code=404, detail="Fichier de version application introuvable.")

    try:
        with open(APP_LATEST_PATH, "r", encoding="utf-8") as f:
            release = json.load(f)
        if not isinstance(release, dict):
            raise HTTPException(status_code=500, detail="Fichier de version application invalide.")
        if not is_official_app_download_url(str(release.get("downloadUrl", ""))):
            raise HTTPException(status_code=500, detail="URL de telechargement application non officielle.")
        return release
    except UnicodeDecodeError:
        raise HTTPException(status_code=500, detail="Fichier de version application non UTF-8.")
    except json.JSONDecodeError:
        raise HTTPException(status_code=500, detail="Fichier de version application invalide.")


@app.get(
    "/api/v2/app/features",
    tags=["Application"],
    summary="Configuration distante des fonctionnalites GeoTower",
)
async def get_app_feature_flags():
    return JSONResponse(content=get_feature_flags())


@app.get("/api/v2/antennes/hs", tags=["Sites Hors Service"], summary="Obtenir les sites en panne (GeoJSON)")
async def get_sites_hs():
    require_feature_enabled(FEATURE_OUTAGES_DATA, "Donnees sites hors service desactivees.")
    if not os.path.exists(SITES_HS_PATH):
        raise HTTPException(status_code=404, detail="Fichier des sites HS introuvable sur le serveur.")

    return FileResponse(
        path=SITES_HS_PATH,
        filename="sites_hs.geojson",
        media_type="application/geo+json",
        headers={
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
        },
    )


@app.get("/api/v2/antennes/hs/info", tags=["Sites Hors Service"], summary="Date de mise a jour des pannes")
async def get_sites_hs_info():
    require_feature_enabled(FEATURE_OUTAGES_DATA, "Infos sites hors service desactivees.")
    if os.path.exists(SITES_HS_DATE_PATH):
        with open(SITES_HS_DATE_PATH, "r", encoding="utf-8", errors="ignore") as f:
            return {"last_update": f.read().strip()}
    return {"last_update": "Inconnue"}


_CATALOG_CACHE = []
_LAST_CACHE_TIME = 0


def valid_sha256(value: Optional[str]) -> bool:
    return bool(value and SHA256_RE.fullmatch(value.strip()))


def offline_map_sha256s() -> Dict[str, str]:
    raw = os.environ.get(OFFLINE_MAP_SHA256_JSON_ENV, "").strip()
    if not raw:
        path = os.environ.get(OFFLINE_MAP_SHA256_PATH_ENV, "").strip()
        if path and os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    raw = f.read()
            except Exception:
                raw = ""
    if not raw:
        return {}

    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    if not isinstance(payload, dict):
        return {}

    result: Dict[str, str] = {}
    for key, value in payload.items():
        if not isinstance(key, str) or not isinstance(value, str):
            continue
        if valid_sha256(value):
            result[key.strip()] = value.strip().lower()
    return result


def get_remote_file_size_mb(url: str, default_mb: int) -> int:
    try:
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req, timeout=3.0) as response:
            size_bytes = response.headers.get("Content-Length")
            if size_bytes:
                return int(int(size_bytes) / (1024 * 1024))
    except Exception as exc:
        print(f"Impossible de lire la taille de {url}: {exc}")
    return default_mb


@app.get("/api/v2/maps/catalog", response_model=List[OfflineMap], tags=["Cartes"], summary="Catalogue des cartes vectorielles")
async def get_maps_catalog():
    require_feature_enabled(FEATURE_OFFLINE_MAPS_CATALOG, "Catalogue cartes hors-ligne desactive.")
    global _CATALOG_CACHE, _LAST_CACHE_TIME

    if not _CATALOG_CACHE or (time.time() - _LAST_CACHE_TIME > 86400):
        base_catalog = [
            {"id": "alsace", "name": "Alsace", "description": "Region Grand Est", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/alsace.map", "estimated_size_mb": 85, "map_filename": "alsace.map"},
            {"id": "aquitaine", "name": "Aquitaine", "description": "Region Nouvelle-Aquitaine", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/aquitaine.map", "estimated_size_mb": 209, "map_filename": "aquitaine.map"},
            {"id": "auvergne", "name": "Auvergne", "description": "Region Auvergne-Rhone-Alpes", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/auvergne.map", "estimated_size_mb": 109, "map_filename": "auvergne.map"},
            {"id": "basse-normandie", "name": "Basse-Normandie", "description": "Region Normandie", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/basse-normandie.map", "estimated_size_mb": 102, "map_filename": "basse-normandie.map"},
            {"id": "bourgogne", "name": "Bourgogne", "description": "Region Bourgogne-Franche-Comte", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/bourgogne.map", "estimated_size_mb": 150, "map_filename": "bourgogne.map"},
            {"id": "bretagne", "name": "Bretagne", "description": "Region Bretagne", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/bretagne.map", "estimated_size_mb": 206, "map_filename": "bretagne.map"},
            {"id": "centre", "name": "Centre", "description": "Region Centre-Val de Loire", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/centre.map", "estimated_size_mb": 172, "map_filename": "centre.map"},
            {"id": "champagne-ardenne", "name": "Champagne-Ardenne", "description": "Region Grand Est", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/champagne-ardenne.map", "estimated_size_mb": 79, "map_filename": "champagne-ardenne.map"},
            {"id": "corse", "name": "Corse", "description": "Ile de Beaute", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/corse.map", "estimated_size_mb": 25, "map_filename": "corse.map"},
            {"id": "franche-comte", "name": "Franche-Comte", "description": "Region Bourgogne-Franche-Comte", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/franche-comte.map", "estimated_size_mb": 87, "map_filename": "franche-comte.map"},
            {"id": "haute-normandie", "name": "Haute-Normandie", "description": "Region Normandie", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/haute-normandie.map", "estimated_size_mb": 77, "map_filename": "haute-normandie.map"},
            {"id": "ile-de-france", "name": "Ile-de-France", "description": "Paris et region parisienne", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/ile-de-france.map", "estimated_size_mb": 197, "map_filename": "ile-de-france.map"},
            {"id": "languedoc-roussillon", "name": "Languedoc-Roussillon", "description": "Region Occitanie", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/languedoc-roussillon.map", "estimated_size_mb": 175, "map_filename": "languedoc-roussillon.map"},
            {"id": "limousin", "name": "Limousin", "description": "Region Nouvelle-Aquitaine", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/limousin.map", "estimated_size_mb": 72, "map_filename": "limousin.map"},
            {"id": "lorraine", "name": "Lorraine", "description": "Region Grand Est", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/lorraine.map", "estimated_size_mb": 121, "map_filename": "lorraine.map"},
            {"id": "midi-pyrenees", "name": "Midi-Pyrenees", "description": "Region Occitanie", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/midi-pyrenees.map", "estimated_size_mb": 244, "map_filename": "midi-pyrenees.map"},
            {"id": "nord-pas-de-calais", "name": "Nord-Pas-de-Calais", "description": "Region Hauts-de-France", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/nord-pas-de-calais.map", "estimated_size_mb": 157, "map_filename": "nord-pas-de-calais.map"},
            {"id": "pays-de-la-loire", "name": "Pays de la Loire", "description": "Region Pays de la Loire", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/pays-de-la-loire.map", "estimated_size_mb": 235, "map_filename": "pays-de-la-loire.map"},
            {"id": "picardie", "name": "Picardie", "description": "Region Hauts-de-France", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/picardie.map", "estimated_size_mb": 104, "map_filename": "picardie.map"},
            {"id": "poitou-charentes", "name": "Poitou-Charentes", "description": "Region Nouvelle-Aquitaine", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/poitou-charentes.map", "estimated_size_mb": 146, "map_filename": "poitou-charentes.map"},
            {"id": "provence-alpes-cote-d-azur", "name": "Provence-Alpes-Cote d'Azur", "description": "Region Sud", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/provence-alpes-cote-d-azur.map", "estimated_size_mb": 229, "map_filename": "provence-alpes-cote-d-azur.map"},
            {"id": "rhone-alpes", "name": "Rhone-Alpes", "description": "Region Auvergne-Rhone-Alpes", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/rhone-alpes.map", "estimated_size_mb": 335, "map_filename": "rhone-alpes.map"},
            {"id": "guadeloupe", "name": "Guadeloupe", "description": "Antilles Francaises", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/guadeloupe.map", "estimated_size_mb": 17, "map_filename": "guadeloupe.map"},
            {"id": "martinique", "name": "Martinique", "description": "Antilles Francaises", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/martinique.map", "estimated_size_mb": 13, "map_filename": "martinique.map"},
            {"id": "guyane", "name": "Guyane", "description": "Amerique du Sud", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/guyane.map", "estimated_size_mb": 16, "map_filename": "guyane.map"},
            {"id": "mayotte", "name": "Mayotte", "description": "Ocean Indien", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/mayotte.map", "estimated_size_mb": 7, "map_filename": "mayotte.map"},
            {"id": "reunion", "name": "La Reunion", "description": "Ocean Indien", "map_url": "https://download.mapsforge.org/maps/v5/europe/france/reunion.map", "estimated_size_mb": 21, "map_filename": "reunion.map"},
        ]

        map_hashes = offline_map_sha256s()
        secured_catalog = []
        for item in base_catalog:
            sha256 = map_hashes.get(item["id"]) or map_hashes.get(item["map_filename"])
            if not valid_sha256(sha256):
                continue
            secured_item = dict(item)
            secured_item["sha256"] = sha256.lower()
            secured_item["estimated_size_mb"] = get_remote_file_size_mb(
                secured_item["map_url"],
                secured_item["estimated_size_mb"],
            )
            secured_catalog.append(secured_item)

        _CATALOG_CACHE = secured_catalog
        _LAST_CACHE_TIME = time.time()

    return _CATALOG_CACHE


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
