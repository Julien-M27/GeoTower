import base64
import hashlib
import hmac
import ipaddress
import json
import os
import re
import sqlite3
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

from fastapi import APIRouter, File, Form, Header, HTTPException, Query, Request, UploadFile
from fastapi.responses import JSONResponse

from feature_flags import (
    FEATURE_SIGNALQUEST_COVERAGE,
    FEATURE_SIGNALQUEST_PHOTOS,
    FEATURE_SIGNALQUEST_SPEEDTESTS,
    FEATURE_SIGNALQUEST_UPLOAD,
    require_feature_enabled,
)

try:
    import httpx
except ImportError:  # pragma: no cover - deployment dependency guard
    httpx = None


router = APIRouter(prefix="/api/v2/signalquest", tags=["SignalQuest"])

SIGNALQUEST_BASE_URL = os.environ.get("SIGNALQUEST_BASE_URL", "https://api.signalquest.fr").rstrip("/")
SIGNALQUEST_API_KEY_ENV = "SIGNALQUEST_API_KEY"
UPLOAD_TOKEN_SECRET_ENV = "SIGNALQUEST_UPLOAD_TOKEN_SECRET"

UPLOAD_ROOT = os.environ.get("SIGNALQUEST_UPLOAD_ROOT", "/opt/geotower/data/signalquest_uploads")
UPLOAD_DB_PATH = os.environ.get("SIGNALQUEST_UPLOAD_DB_PATH", "/opt/geotower/data/signalquest_uploads.db")
MAX_UPLOAD_BYTES = int(os.environ.get("SIGNALQUEST_MAX_UPLOAD_BYTES", str(20 * 1024 * 1024)))
KEEP_SUCCESSFUL_UPLOADS = os.environ.get("SIGNALQUEST_KEEP_SUCCESSFUL_UPLOADS", "false").lower() == "true"

UPLOAD_RATE_LIMIT_PER_MINUTE = int(os.environ.get("SIGNALQUEST_UPLOAD_RATE_LIMIT_PER_MINUTE", "20"))
READ_RATE_LIMIT_PER_MINUTE = int(os.environ.get("SIGNALQUEST_READ_RATE_LIMIT_PER_MINUTE", "120"))
UPLOAD_TOKEN_TTL_SECONDS = int(os.environ.get("SIGNALQUEST_UPLOAD_TOKEN_TTL_SECONDS", "300"))
UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR = int(os.environ.get("SIGNALQUEST_UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR", "30"))
UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR = int(os.environ.get("SIGNALQUEST_UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR", "300"))
UPLOAD_TOKEN_AUDIT_RETENTION_SECONDS = int(
    os.environ.get("SIGNALQUEST_UPLOAD_TOKEN_AUDIT_RETENTION_SECONDS", str(24 * 60 * 60))
)

# Retry files are kept briefly so an operator can inspect transient failures.
RETRY_UPLOAD_RETENTION_SECONDS = int(os.environ.get("SIGNALQUEST_RETRY_UPLOAD_RETENTION_SECONDS", "3600"))
MAX_UPLOAD_IMAGE_DIMENSION = int(os.environ.get("SIGNALQUEST_MAX_UPLOAD_IMAGE_DIMENSION", "10000"))
MAX_UPLOAD_IMAGE_PIXELS = int(os.environ.get("SIGNALQUEST_MAX_UPLOAD_IMAGE_PIXELS", "50000000"))
SERVER_STRIP_EXIF_WHEN_REQUESTED = (
    os.environ.get("SIGNALQUEST_SERVER_STRIP_EXIF_WHEN_REQUESTED", "true").lower() == "true"
)

ALLOWED_UPLOAD_MIME_TYPES = {"image/jpeg"}
SAFE_SITE_ID_RE = re.compile(r"^[A-Za-z0-9_.:-]{1,96}$")
JPEG_SOF_MARKERS = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _parse_ip(value: Optional[str]) -> Optional[ipaddress._BaseAddress]:
    if value is None:
        return None
    candidate = value.strip()
    if not candidate:
        return None
    if candidate.startswith("[") and "]" in candidate:
        candidate = candidate[1 : candidate.index("]")]
    elif candidate.count(":") == 1 and "." in candidate:
        candidate = candidate.rsplit(":", 1)[0]
    try:
        return ipaddress.ip_address(candidate)
    except ValueError:
        return None


def _trusted_proxy_networks(raw_value: Optional[str] = None) -> List[ipaddress._BaseNetwork]:
    raw = os.environ.get("TRUSTED_PROXY_IPS", "") if raw_value is None else raw_value
    networks: List[ipaddress._BaseNetwork] = []
    for part in raw.split(","):
        candidate = part.strip()
        if not candidate:
            continue
        try:
            networks.append(ipaddress.ip_network(candidate, strict=False))
        except ValueError:
            continue
    return networks


def _is_trusted_proxy(remote_host: Optional[str], trusted_networks: List[ipaddress._BaseNetwork]) -> bool:
    remote_ip = _parse_ip(remote_host)
    if remote_ip is None:
        return False
    return any(remote_ip.version == network.version and remote_ip in network for network in trusted_networks)


def _client_ip_from_values(
    remote_host: Optional[str],
    forwarded_for: Optional[str],
    trusted_proxy_ips: Optional[str] = None,
) -> str:
    remote_ip = _parse_ip(remote_host)
    remote_value = str(remote_ip) if remote_ip is not None else "unknown"
    trusted_networks = _trusted_proxy_networks(trusted_proxy_ips)
    if not forwarded_for or not _is_trusted_proxy(remote_host, trusted_networks):
        return remote_value

    forwarded_ip = _parse_ip(forwarded_for.split(",", 1)[0])
    return str(forwarded_ip) if forwarded_ip is not None else remote_value


def _client_ip(request: Request) -> str:
    return _client_ip_from_values(
        request.client.host if request.client else None,
        request.headers.get("x-forwarded-for"),
    )


def _b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def _b64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


def _upload_token_secret() -> bytes:
    secret = os.environ.get(UPLOAD_TOKEN_SECRET_ENV, "").strip()
    if not secret:
        raise HTTPException(status_code=503, detail="Jetons d'upload SignalQuest non configures.")
    return secret.encode("utf-8")


def _sign_upload_token_payload(payload: Dict[str, Any], secret: bytes) -> str:
    payload_bytes = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
    payload_part = _b64url_encode(payload_bytes)
    signature = hmac.new(secret, payload_part.encode("ascii"), hashlib.sha256).digest()
    return f"{payload_part}.{_b64url_encode(signature)}"


def _purge_upload_token_audit_rows(conn: sqlite3.Connection, now: int) -> None:
    cutoff = now - max(3600, UPLOAD_TOKEN_AUDIT_RETENTION_SECONDS)
    conn.execute("DELETE FROM signalquest_upload_tokens WHERE expires_at < ?", (cutoff,))


def _check_upload_token_quota(conn: sqlite3.Connection, site_id: str, now: int) -> None:
    window_start = now - 3600
    if UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR > 0:
        global_count = conn.execute(
            "SELECT COUNT(*) FROM signalquest_upload_tokens WHERE created_at >= ?",
            (window_start,),
        ).fetchone()[0]
        if global_count >= UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR:
            raise HTTPException(status_code=429, detail="Quota global d'upload SignalQuest depasse.")

    if UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR > 0:
        site_count = conn.execute(
            "SELECT COUNT(*) FROM signalquest_upload_tokens WHERE site_id = ? AND created_at >= ?",
            (site_id, window_start),
        ).fetchone()[0]
        if site_count >= UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR:
            raise HTTPException(status_code=429, detail="Quota d'upload SignalQuest depasse pour ce site.")


def _create_upload_token(site_id: str, client_ip: str, now: Optional[int] = None) -> Dict[str, Any]:
    issued_at = int(time.time()) if now is None else int(now)
    expires_at = issued_at + max(30, min(UPLOAD_TOKEN_TTL_SECONDS, 900))
    nonce = uuid.uuid4().hex
    payload = {
        "siteId": site_id,
        "clientIp": client_ip,
        "iat": issued_at,
        "exp": expires_at,
        "nonce": nonce,
    }
    secret = _upload_token_secret()
    with _db_connection() as conn:
        _purge_upload_token_audit_rows(conn, issued_at)
        _check_upload_token_quota(conn, site_id, issued_at)
        conn.execute(
            """
            INSERT INTO signalquest_upload_tokens (
                nonce, created_at, expires_at, site_id, client_ip
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            (nonce, issued_at, expires_at, site_id, client_ip),
        )
    return {
        "token": _sign_upload_token_payload(payload, secret),
        "expiresAt": expires_at,
    }


def _verify_upload_token(
    token: Optional[str],
    site_id: str,
    client_ip: str,
    now: Optional[int] = None,
    upload_id: Optional[str] = None,
) -> None:
    if not token:
        raise HTTPException(status_code=401, detail="Jeton d'upload manquant.")

    parts = token.split(".")
    if len(parts) != 2:
        raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")

    payload_part, signature_part = parts
    expected_signature = hmac.new(
        _upload_token_secret(),
        payload_part.encode("ascii"),
        hashlib.sha256,
    ).digest()
    try:
        provided_signature = _b64url_decode(signature_part)
        payload = json.loads(_b64url_decode(payload_part).decode("utf-8"))
    except Exception:
        raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")

    if not hmac.compare_digest(provided_signature, expected_signature):
        raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")

    current_time = int(time.time()) if now is None else int(now)
    if payload.get("siteId") != site_id or payload.get("clientIp") != client_ip:
        raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")
    nonce = payload.get("nonce")
    if not isinstance(nonce, str) or not nonce:
        raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")
    try:
        expires_at = int(payload.get("exp", 0))
    except (TypeError, ValueError):
        raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")
    if expires_at < current_time:
        raise HTTPException(status_code=401, detail="Jeton d'upload expire.")

    with _db_connection() as conn:
        row = conn.execute(
            """
            SELECT site_id, client_ip, expires_at, consumed_at
            FROM signalquest_upload_tokens
            WHERE nonce = ?
            """,
            (nonce,),
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")
        if row["site_id"] != site_id or row["client_ip"] != client_ip:
            raise HTTPException(status_code=401, detail="Jeton d'upload invalide.")
        if int(row["expires_at"]) < current_time:
            raise HTTPException(status_code=401, detail="Jeton d'upload expire.")
        if row["consumed_at"] is not None:
            raise HTTPException(status_code=401, detail="Jeton d'upload deja utilise.")

        updated = conn.execute(
            """
            UPDATE signalquest_upload_tokens
            SET consumed_at = ?, consumed_upload_id = ?
            WHERE nonce = ? AND consumed_at IS NULL
            """,
            (current_time, upload_id, nonce),
        ).rowcount
        if updated != 1:
            raise HTTPException(status_code=401, detail="Jeton d'upload deja utilise.")


def _check_rate_limit(request: Request, action: str, limit_per_minute: int) -> None:
    if limit_per_minute <= 0:
        return

    client_ip = _client_ip(request)
    now = time.time()
    window_start = now - 60.0
    with _db_connection() as conn:
        conn.execute("DELETE FROM signalquest_rate_limit_events WHERE created_at < ?", (now - 3600.0,))
        request_count = conn.execute(
            """
            SELECT COUNT(*)
            FROM signalquest_rate_limit_events
            WHERE action = ? AND client_ip = ? AND created_at >= ?
            """,
            (action, client_ip, window_start),
        ).fetchone()[0]
        if request_count >= limit_per_minute:
            raise HTTPException(status_code=429, detail="Trop de requetes SignalQuest.")
        conn.execute(
            "INSERT INTO signalquest_rate_limit_events (action, client_ip, created_at) VALUES (?, ?, ?)",
            (action, client_ip, now),
        )


def _signalquest_api_key() -> str:
    api_key = os.environ.get(SIGNALQUEST_API_KEY_ENV, "").strip()
    if not api_key:
        raise HTTPException(status_code=503, detail="Proxy SignalQuest non configure.")
    return api_key


def _require_httpx() -> None:
    if httpx is None:
        raise HTTPException(status_code=503, detail="Dependance serveur httpx manquante.")


def _validate_site_id(site_id: str) -> str:
    normalized = site_id.strip()
    if not SAFE_SITE_ID_RE.fullmatch(normalized):
        raise HTTPException(status_code=400, detail="Identifiant de site invalide.")
    return normalized


def _clean_text(value: Optional[str], max_length: int) -> Optional[str]:
    if value is None:
        return None
    normalized = value.strip()
    if not normalized:
        return None
    return normalized[:max_length]


def _form_bool(value: Optional[Any]) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    return str(value).strip().lower() in {"1", "true", "t", "yes", "y", "on"}


def _ensure_storage() -> None:
    os.makedirs(UPLOAD_ROOT, exist_ok=True)
    db_dir = os.path.dirname(os.path.abspath(UPLOAD_DB_PATH))
    os.makedirs(db_dir, exist_ok=True)


class _ClosingSqliteConnection(sqlite3.Connection):
    def __exit__(self, exc_type, exc_value, traceback):
        try:
            return super().__exit__(exc_type, exc_value, traceback)
        finally:
            self.close()


def _db_connection() -> sqlite3.Connection:
    _ensure_storage()
    conn = sqlite3.connect(UPLOAD_DB_PATH, factory=_ClosingSqliteConnection)
    conn.row_factory = sqlite3.Row
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS signalquest_uploads (
            upload_id TEXT PRIMARY KEY,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            client_ip TEXT,
            site_id TEXT NOT NULL,
            operator TEXT,
            source_filename TEXT,
            stored_filename TEXT,
            stored_path TEXT,
            mime_type TEXT,
            size_bytes INTEGER,
            status TEXT NOT NULL,
            signalquest_status_code INTEGER,
            signalquest_photo_id TEXT,
            signalquest_image_url TEXT,
            signalquest_uploaded_at TEXT,
            error_message TEXT
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS signalquest_upload_tokens (
            nonce TEXT PRIMARY KEY,
            created_at INTEGER NOT NULL,
            expires_at INTEGER NOT NULL,
            site_id TEXT NOT NULL,
            client_ip TEXT NOT NULL,
            consumed_at INTEGER,
            consumed_upload_id TEXT
        )
        """
    )
    conn.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_signalquest_upload_tokens_created
        ON signalquest_upload_tokens(created_at)
        """
    )
    conn.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_signalquest_upload_tokens_site_created
        ON signalquest_upload_tokens(site_id, created_at)
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS signalquest_rate_limit_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            action TEXT NOT NULL,
            client_ip TEXT NOT NULL,
            created_at REAL NOT NULL
        )
        """
    )
    conn.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_signalquest_rate_limit_events_lookup
        ON signalquest_rate_limit_events(action, client_ip, created_at)
        """
    )
    conn.commit()
    return conn


def _insert_upload_log(
    *,
    upload_id: str,
    request: Request,
    site_id: str,
    operator: Optional[str],
    source_filename: Optional[str],
    stored_filename: str,
    stored_path: str,
    mime_type: Optional[str],
    size_bytes: int,
) -> None:
    now = _now_iso()
    with _db_connection() as conn:
        conn.execute(
            """
            INSERT INTO signalquest_uploads (
                upload_id, created_at, updated_at, client_ip, site_id, operator,
                source_filename, stored_filename, stored_path, mime_type,
                size_bytes, status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                upload_id,
                now,
                now,
                _client_ip(request),
                site_id,
                operator,
                source_filename,
                stored_filename,
                stored_path,
                mime_type,
                size_bytes,
                "received",
            ),
        )


def _update_upload_log(
    upload_id: str,
    *,
    status: str,
    signalquest_status_code: Optional[int] = None,
    signalquest_photo_id: Optional[str] = None,
    signalquest_image_url: Optional[str] = None,
    signalquest_uploaded_at: Optional[str] = None,
    error_message: Optional[str] = None,
    stored_path: Optional[str] = None,
) -> None:
    with _db_connection() as conn:
        conn.execute(
            """
            UPDATE signalquest_uploads
            SET updated_at = ?,
                status = ?,
                signalquest_status_code = ?,
                signalquest_photo_id = ?,
                signalquest_image_url = ?,
                signalquest_uploaded_at = ?,
                error_message = ?,
                stored_path = COALESCE(?, stored_path)
            WHERE upload_id = ?
            """,
            (
                _now_iso(),
                status,
                signalquest_status_code,
                signalquest_photo_id,
                signalquest_image_url,
                signalquest_uploaded_at,
                error_message,
                stored_path,
                upload_id,
            ),
        )


def _invalid_jpeg() -> HTTPException:
    return HTTPException(status_code=400, detail="Photo JPEG invalide.")


def _validate_jpeg_dimensions(width: int, height: int) -> None:
    if width <= 0 or height <= 0:
        raise _invalid_jpeg()
    if width > MAX_UPLOAD_IMAGE_DIMENSION or height > MAX_UPLOAD_IMAGE_DIMENSION:
        raise HTTPException(status_code=413, detail="Dimensions de photo trop grandes.")
    if width * height > MAX_UPLOAD_IMAGE_PIXELS:
        raise HTTPException(status_code=413, detail="Photo trop grande.")


def _jpeg_dimensions(content: bytes) -> Tuple[int, int]:
    if len(content) < 4 or not content.startswith(b"\xff\xd8") or not content.endswith(b"\xff\xd9"):
        raise _invalid_jpeg()

    offset = 2
    dimensions: Optional[Tuple[int, int]] = None
    while offset < len(content):
        if content[offset] != 0xFF:
            raise _invalid_jpeg()
        while offset < len(content) and content[offset] == 0xFF:
            offset += 1
        if offset >= len(content):
            raise _invalid_jpeg()

        marker = content[offset]
        offset += 1
        if marker == 0xD9:
            break
        if marker == 0x00 or marker == 0x01 or 0xD0 <= marker <= 0xD7:
            continue
        if offset + 2 > len(content):
            raise _invalid_jpeg()

        segment_length = int.from_bytes(content[offset : offset + 2], "big")
        if segment_length < 2 or offset + segment_length > len(content):
            raise _invalid_jpeg()

        if marker in JPEG_SOF_MARKERS:
            if segment_length < 7:
                raise _invalid_jpeg()
            height = int.from_bytes(content[offset + 3 : offset + 5], "big")
            width = int.from_bytes(content[offset + 5 : offset + 7], "big")
            _validate_jpeg_dimensions(width, height)
            dimensions = (width, height)
        elif marker == 0xDA:
            if dimensions is None:
                raise _invalid_jpeg()
            return dimensions

        offset += segment_length

    raise _invalid_jpeg()


def _strip_jpeg_app1_segments(content: bytes) -> bytes:
    output = bytearray(content[:2])
    offset = 2
    while offset < len(content):
        if content[offset] != 0xFF:
            raise _invalid_jpeg()

        marker_start = offset
        while offset < len(content) and content[offset] == 0xFF:
            offset += 1
        if offset >= len(content):
            raise _invalid_jpeg()

        marker = content[offset]
        offset += 1
        if marker == 0xD9:
            output.extend(content[marker_start:offset])
            break
        if marker == 0x00 or marker == 0x01 or 0xD0 <= marker <= 0xD7:
            output.extend(content[marker_start:offset])
            continue
        if offset + 2 > len(content):
            raise _invalid_jpeg()

        segment_length = int.from_bytes(content[offset : offset + 2], "big")
        segment_end = offset + segment_length
        if segment_length < 2 or segment_end > len(content):
            raise _invalid_jpeg()

        if marker == 0xDA:
            output.extend(content[marker_start:])
            return bytes(output)
        if marker != 0xE1:
            output.extend(content[marker_start:segment_end])
        offset = segment_end

    stripped = bytes(output)
    _jpeg_dimensions(stripped)
    return stripped


def _validated_jpeg_content(content: bytes, strip_exif_before_upload: bool = False) -> bytes:
    _jpeg_dimensions(content)
    if strip_exif_before_upload and SERVER_STRIP_EXIF_WHEN_REQUESTED:
        return _strip_jpeg_app1_segments(content)
    return content


async def _save_upload_file(
    upload_id: str,
    file: UploadFile,
    strip_exif_before_upload: bool = False,
) -> Tuple[str, str, int]:
    content_type = (file.content_type or "").lower()
    if content_type not in ALLOWED_UPLOAD_MIME_TYPES:
        raise HTTPException(status_code=415, detail="Type de photo refuse.")

    stored_filename = f"{upload_id}.jpg"
    stored_path = os.path.join(UPLOAD_ROOT, stored_filename)
    content = bytearray()

    try:
        while True:
            chunk = await file.read(1024 * 1024)
            if not chunk:
                break
            content.extend(chunk)
            if len(content) > MAX_UPLOAD_BYTES:
                raise HTTPException(status_code=413, detail="Photo trop volumineuse.")
    finally:
        await file.close()

    if len(content) <= 0:
        raise HTTPException(status_code=400, detail="Photo vide.")

    normalized_content = _validated_jpeg_content(
        bytes(content),
        strip_exif_before_upload=strip_exif_before_upload,
    )

    _ensure_storage()
    with open(stored_path, "wb") as output:
        output.write(normalized_content)

    return stored_filename, stored_path, len(normalized_content)


def _signalquest_error_status(status_code: int) -> int:
    if status_code in (401, 403):
        return 502
    if status_code == 429:
        return 429
    if status_code >= 500:
        return 502
    return status_code


def _json_or_empty(response: Any) -> Dict[str, Any]:
    try:
        payload = response.json()
        return payload if isinstance(payload, dict) else {}
    except Exception:
        return {}


def _status_from_upload_payload(payload: Dict[str, Any]) -> Tuple[str, Optional[str], Optional[str], Optional[str]]:
    data = payload.get("data") if isinstance(payload.get("data"), dict) else {}
    remote_photo_id = data.get("id")
    remote_image_url = data.get("imageUrl")
    remote_uploaded_at = data.get("uploadedAt")
    status = "sent" if data.get("approved") is True else "awaiting_validation"
    return status, remote_photo_id, remote_image_url, remote_uploaded_at


def _delete_file_quietly(path: str) -> bool:
    try:
        os.remove(path)
        return True
    except FileNotFoundError:
        return True
    except Exception:
        return False


def _purge_old_local_uploads(now: Optional[int] = None) -> int:
    current_time = int(time.time()) if now is None else int(now)
    retry_cutoff = datetime.fromtimestamp(
        current_time - max(60, RETRY_UPLOAD_RETENTION_SECONDS),
        timezone.utc,
    ).isoformat()
    deleted_count = 0
    with _db_connection() as conn:
        rows = conn.execute(
            """
            SELECT upload_id, stored_path
            FROM signalquest_uploads
            WHERE stored_path IS NOT NULL
              AND stored_path <> ''
              AND (
                  status = 'failed'
                  OR (status = 'retry' AND updated_at < ?)
              )
            """,
            (retry_cutoff,),
        ).fetchall()
        for row in rows:
            if _delete_file_quietly(row["stored_path"]):
                conn.execute(
                    """
                    UPDATE signalquest_uploads
                    SET updated_at = ?, stored_path = ''
                    WHERE upload_id = ?
                    """,
                    (_now_iso(), row["upload_id"]),
                )
                deleted_count += 1
    return deleted_count


def purge_old_signalquest_uploads(now: Optional[int] = None) -> int:
    return _purge_old_local_uploads(now)


async def _signalquest_get(path: str, params: Dict[str, Any]) -> JSONResponse:
    _require_httpx()
    api_key = _signalquest_api_key()
    headers = {"Authorization": f"Bearer {api_key}", "Accept": "application/json"}
    timeout = httpx.Timeout(30.0, connect=10.0)

    async with httpx.AsyncClient(timeout=timeout) as client:
        try:
            response = await client.get(f"{SIGNALQUEST_BASE_URL}{path}", headers=headers, params=params)
        except httpx.TimeoutException:
            raise HTTPException(status_code=504, detail="SignalQuest ne repond pas.")
        except httpx.HTTPError:
            raise HTTPException(status_code=502, detail="Erreur de communication SignalQuest.")

    payload = _json_or_empty(response)
    if response.is_success:
        return JSONResponse(status_code=response.status_code, content=payload)

    return JSONResponse(
        status_code=_signalquest_error_status(response.status_code),
        content={"detail": "Requete SignalQuest refusee.", "signalquestStatus": response.status_code},
    )


@router.get("/sites/{site_id}/photos", summary="Photos SignalQuest d'un site")
async def get_site_photos(
    request: Request,
    site_id: str,
    operator: Optional[str] = Query(None, max_length=32),
    limit: Optional[int] = Query(None, ge=1, le=100),
    offset: Optional[int] = Query(None, ge=0, le=10000),
):
    require_feature_enabled(FEATURE_SIGNALQUEST_PHOTOS, "Photos SignalQuest desactivees.")
    _check_rate_limit(request, "read", READ_RATE_LIMIT_PER_MINUTE)
    normalized_site_id = _validate_site_id(site_id)
    params = {
        key: value
        for key, value in {
            "operator": _clean_text(operator, 32),
            "limit": limit,
            "offset": offset,
        }.items()
        if value is not None
    }
    return await _signalquest_get(f"/external/v1/sites/{normalized_site_id}/photos", params)


@router.get("/speedtests/site", summary="Speedtests SignalQuest d'un site")
async def get_site_speedtests(
    request: Request,
    siteId: Optional[str] = Query(None, max_length=96),
    anfrCode: Optional[str] = Query(None, max_length=64),
    nationalSiteCode: Optional[str] = Query(None, max_length=64),
    sourceCode: Optional[str] = Query(None, max_length=64),
    enb: Optional[str] = Query(None, max_length=64),
    operator: Optional[str] = Query(None, max_length=32),
    mcc: Optional[int] = Query(None, ge=1, le=999),
    mnc: Optional[int] = Query(None, ge=0, le=999),
    market: str = Query("FR", min_length=2, max_length=8),
    bestOnly: bool = Query(True),
    limit: Optional[int] = Query(None, ge=1, le=100),
    offset: Optional[int] = Query(None, ge=0, le=10000),
):
    require_feature_enabled(FEATURE_SIGNALQUEST_SPEEDTESTS, "Speedtests SignalQuest desactives.")
    _check_rate_limit(request, "read", READ_RATE_LIMIT_PER_MINUTE)
    site_id_value = _clean_text(siteId, 96)
    anfr_code_value = _clean_text(anfrCode, 64)
    national_site_code_value = _clean_text(nationalSiteCode, 64)
    source_code_value = _clean_text(sourceCode, 64)
    enb_value = _clean_text(enb, 64)

    if not any((site_id_value, anfr_code_value, national_site_code_value, source_code_value, enb_value)):
        raise HTTPException(status_code=400, detail="Au moins un identifiant de site est requis.")

    params = {
        key: value
        for key, value in {
            "siteId": site_id_value,
            "anfrCode": anfr_code_value,
            "nationalSiteCode": national_site_code_value,
            "sourceCode": source_code_value,
            "enb": enb_value,
            "operator": _clean_text(operator, 32),
            "mcc": mcc,
            "mnc": mnc,
            "market": (_clean_text(market, 8) or "FR").upper(),
            "bestOnly": bestOnly,
            "limit": limit,
            "offset": offset,
        }.items()
        if value is not None
    }
    return await _signalquest_get("/external/v1/speedtests/site", params)


@router.get("/coverage/points", summary="Points de couverture SignalQuest")
async def get_coverage_points(
    request: Request,
    market: str = Query("FR", min_length=2, max_length=8),
    operator: Optional[str] = Query(None, max_length=32),
    technology: Optional[str] = Query(None, max_length=16),
    north: Optional[float] = Query(None, ge=-90, le=90),
    south: Optional[float] = Query(None, ge=-90, le=90),
    east: Optional[float] = Query(None, ge=-180, le=180),
    west: Optional[float] = Query(None, ge=-180, le=180),
    days: Optional[int] = Query(None, ge=1, le=365),
    limit: Optional[int] = Query(None, ge=1, le=5000),
):
    require_feature_enabled(FEATURE_SIGNALQUEST_COVERAGE, "Points de couverture SignalQuest desactives.")
    _check_rate_limit(request, "read", READ_RATE_LIMIT_PER_MINUTE)

    params = {
        key: value
        for key, value in {
            "market": (_clean_text(market, 8) or "FR").upper(),
            "operator": _clean_text(operator, 32),
            "technology": _clean_text(technology, 16),
            "north": north,
            "south": south,
            "east": east,
            "west": west,
            "days": days,
            "limit": limit,
        }.items()
        if value is not None
    }
    return await _signalquest_get("/external/v1/coverage/points", params)


@router.post("/upload-token", summary="Creer un jeton court pour l'upload SignalQuest")
async def create_upload_token(
    request: Request,
    siteId: str = Query(..., min_length=1, max_length=96),
):
    require_feature_enabled(FEATURE_SIGNALQUEST_UPLOAD, "Upload SignalQuest desactive.")
    _check_rate_limit(request, "upload-token", UPLOAD_RATE_LIMIT_PER_MINUTE)
    normalized_site_id = _validate_site_id(siteId)
    return _create_upload_token(normalized_site_id, _client_ip(request))


@router.post("/sites/{site_id}/photos", summary="Envoyer une photo vers SignalQuest")
async def upload_site_photo(
    request: Request,
    site_id: str,
    x_geotower_upload_token: Optional[str] = Header(None, alias="X-GeoTower-Upload-Token"),
    file: UploadFile = File(...),
    description: Optional[str] = Form(None),
    operator: Optional[str] = Form(None),
    anfrCode: Optional[str] = Form(None),
    nationalSiteCode: Optional[str] = Form(None),
    sourceCode: Optional[str] = Form(None),
    stripExifBeforeUpload: Optional[str] = Form(None),
    exifMetadata: Optional[str] = Form(None),
):
    require_feature_enabled(FEATURE_SIGNALQUEST_UPLOAD, "Upload SignalQuest desactive.")
    _require_httpx()
    _check_rate_limit(request, "upload", UPLOAD_RATE_LIMIT_PER_MINUTE)
    normalized_site_id = _validate_site_id(site_id)
    upload_id = str(uuid.uuid4())
    _verify_upload_token(
        x_geotower_upload_token,
        normalized_site_id,
        _client_ip(request),
        upload_id=upload_id,
    )
    api_key = _signalquest_api_key()
    strip_exif_requested = _form_bool(stripExifBeforeUpload)

    stored_filename, stored_path, size_bytes = await _save_upload_file(
        upload_id,
        file,
        strip_exif_before_upload=strip_exif_requested,
    )
    source_filename = _clean_text(file.filename, 180)
    operator_value = _clean_text(operator, 32)

    _insert_upload_log(
        upload_id=upload_id,
        request=request,
        site_id=normalized_site_id,
        operator=operator_value,
        source_filename=source_filename,
        stored_filename=stored_filename,
        stored_path=stored_path,
        mime_type="image/jpeg",
        size_bytes=size_bytes,
    )
    _update_upload_log(upload_id, status="forwarding")

    form_data = {
        key: value
        for key, value in {
            "description": _clean_text(description, 1000),
            "operator": operator_value,
            "anfrCode": _clean_text(anfrCode, 64),
            "nationalSiteCode": _clean_text(nationalSiteCode, 64),
            "sourceCode": _clean_text(sourceCode, 64),
        }.items()
        if value is not None
    }
    multipart_fields: Dict[str, Any] = {}
    if exifMetadata and not strip_exif_requested:
        try:
            json.loads(exifMetadata)
        except json.JSONDecodeError:
            deleted = _delete_file_quietly(stored_path)
            _update_upload_log(
                upload_id,
                status="failed",
                error_message="exifMetadata invalide",
                stored_path="" if deleted else None,
            )
            raise HTTPException(status_code=400, detail="Metadonnees EXIF invalides.")
        multipart_fields["exifMetadata"] = (None, exifMetadata, "application/json")

    headers = {"Authorization": f"Bearer {api_key}", "Accept": "application/json"}
    timeout = httpx.Timeout(60.0, connect=10.0, write=30.0, read=60.0)

    try:
        with open(stored_path, "rb") as stored_file:
            files = {
                "file": (source_filename or stored_filename, stored_file, "image/jpeg"),
                **multipart_fields,
            }
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.post(
                    f"{SIGNALQUEST_BASE_URL}/external/v1/sites/{normalized_site_id}/photos",
                    headers=headers,
                    data=form_data,
                    files=files,
                )
    except httpx.TimeoutException:
        _update_upload_log(upload_id, status="retry", error_message="timeout SignalQuest")
        _purge_old_local_uploads()
        raise HTTPException(status_code=504, detail="SignalQuest ne repond pas.")
    except httpx.HTTPError:
        _update_upload_log(upload_id, status="retry", error_message="communication SignalQuest impossible")
        _purge_old_local_uploads()
        raise HTTPException(status_code=502, detail="Erreur de communication SignalQuest.")

    payload = _json_or_empty(response)
    if response.is_success:
        status, remote_photo_id, remote_image_url, remote_uploaded_at = _status_from_upload_payload(payload)
        deleted = False
        if not KEEP_SUCCESSFUL_UPLOADS:
            deleted = _delete_file_quietly(stored_path)

        _update_upload_log(
            upload_id,
            status=status,
            signalquest_status_code=response.status_code,
            signalquest_photo_id=remote_photo_id,
            signalquest_image_url=remote_image_url,
            signalquest_uploaded_at=remote_uploaded_at,
            stored_path="" if deleted else None,
        )
        return JSONResponse(status_code=response.status_code, content=payload)

    proxy_status = _signalquest_error_status(response.status_code)
    retry_status = "retry" if proxy_status in (429, 502, 503, 504) else "failed"
    deleted = False
    if retry_status == "failed":
        deleted = _delete_file_quietly(stored_path)
    _update_upload_log(
        upload_id,
        status=retry_status,
        signalquest_status_code=response.status_code,
        error_message=f"SignalQuest HTTP {response.status_code}",
        stored_path="" if deleted else None,
    )
    if retry_status == "retry":
        _purge_old_local_uploads()
    return JSONResponse(
        status_code=proxy_status,
        content={
            "detail": "Envoi SignalQuest refuse.",
            "uploadId": upload_id,
            "signalquestStatus": response.status_code,
        },
    )


@router.get("/uploads/{upload_id}", summary="Etat d'un envoi SignalQuest")
async def get_upload_status(upload_id: str):
    try:
        parsed = str(uuid.UUID(upload_id))
    except ValueError:
        raise HTTPException(status_code=400, detail="Identifiant d'envoi invalide.")

    with _db_connection() as conn:
        row = conn.execute(
            """
            SELECT upload_id, created_at, updated_at, site_id, operator, source_filename,
                   mime_type, size_bytes, status, signalquest_status_code,
                   signalquest_photo_id, signalquest_image_url, signalquest_uploaded_at,
                   error_message
            FROM signalquest_uploads
            WHERE upload_id = ?
            """,
            (parsed,),
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=404, detail="Envoi introuvable.")

    return dict(row)
