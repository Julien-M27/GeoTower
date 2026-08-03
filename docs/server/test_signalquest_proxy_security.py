import os
import shutil
import sys
import types
import unittest
import uuid

if "fastapi" not in sys.modules:
    fastapi_stub = types.ModuleType("fastapi")

    class HTTPException(Exception):
        def __init__(self, status_code: int, detail: str):
            super().__init__(detail)
            self.status_code = status_code
            self.detail = detail

    class APIRouter:
        def __init__(self, *args, **kwargs):
            pass

        def get(self, *args, **kwargs):
            return lambda func: func

        def post(self, *args, **kwargs):
            return lambda func: func

    class Request:
        pass

    class UploadFile:
        pass

    def parameter(default=None, *args, **kwargs):
        return default

    fastapi_stub.APIRouter = APIRouter
    fastapi_stub.File = parameter
    fastapi_stub.Form = parameter
    fastapi_stub.Header = parameter
    fastapi_stub.HTTPException = HTTPException
    fastapi_stub.Query = parameter
    fastapi_stub.Request = Request
    fastapi_stub.UploadFile = UploadFile
    sys.modules["fastapi"] = fastapi_stub

    responses_stub = types.ModuleType("fastapi.responses")

    class JSONResponse:
        def __init__(self, status_code=200, content=None):
            self.status_code = status_code
            self.content = content

    responses_stub.JSONResponse = JSONResponse
    sys.modules["fastapi.responses"] = responses_stub

from fastapi import HTTPException

import signalquest_proxy as proxy


def app1_segment(payload: bytes) -> bytes:
    return b"\xff\xe1" + (len(payload) + 2).to_bytes(2, "big") + payload


def make_jpeg(width: int = 1, height: int = 1, app1_payloads=None) -> bytes:
    app1_payloads = app1_payloads or []
    return (
        b"\xff\xd8"
        b"\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00"
        + b"".join(app1_segment(payload) for payload in app1_payloads)
        + b"\xff\xc0\x00\x11\x08"
        + height.to_bytes(2, "big")
        + width.to_bytes(2, "big")
        + b"\x03\x01\x11\x00\x02\x11\x01\x03\x11\x01"
        b"\xff\xda\x00\x0c\x03\x01\x00\x02\x11\x03\x11\x00\x3f\x00\x00"
        b"\xff\xd9"
    )


class FakeUploadFile:
    def __init__(self, content: bytes, content_type: str = "image/jpeg", filename: str = "photo.jpg"):
        self.content = content
        self.content_type = content_type
        self.filename = filename
        self.offset = 0
        self.closed = False

    async def read(self, size: int) -> bytes:
        if self.offset >= len(self.content):
            return b""
        chunk = self.content[self.offset : self.offset + size]
        self.offset += len(chunk)
        return chunk

    async def close(self) -> None:
        self.closed = True


class SignalQuestProxySecurityTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp_dir_path = os.path.join(os.getcwd(), f"test-tmp-{uuid.uuid4().hex}")
        os.makedirs(self.temp_dir_path)
        self.previous_secret = os.environ.get(proxy.UPLOAD_TOKEN_SECRET_ENV)
        self.previous_values = {
            "UPLOAD_ROOT": proxy.UPLOAD_ROOT,
            "UPLOAD_DB_PATH": proxy.UPLOAD_DB_PATH,
            "MAX_UPLOAD_BYTES": proxy.MAX_UPLOAD_BYTES,
            "UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR": proxy.UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR,
            "UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR": proxy.UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR,
            "RETRY_UPLOAD_RETENTION_SECONDS": proxy.RETRY_UPLOAD_RETENTION_SECONDS,
            "MAX_UPLOAD_IMAGE_DIMENSION": proxy.MAX_UPLOAD_IMAGE_DIMENSION,
            "MAX_UPLOAD_IMAGE_PIXELS": proxy.MAX_UPLOAD_IMAGE_PIXELS,
            "SERVER_STRIP_EXIF_WHEN_REQUESTED": proxy.SERVER_STRIP_EXIF_WHEN_REQUESTED,
        }
        os.environ[proxy.UPLOAD_TOKEN_SECRET_ENV] = "unit-test-secret"
        proxy.UPLOAD_ROOT = os.path.join(self.temp_dir_path, "uploads")
        proxy.UPLOAD_DB_PATH = os.path.join(self.temp_dir_path, "uploads.db")
        proxy.MAX_UPLOAD_BYTES = 1024 * 1024
        proxy.UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR = 30
        proxy.UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR = 300
        proxy.RETRY_UPLOAD_RETENTION_SECONDS = 3600
        proxy.MAX_UPLOAD_IMAGE_DIMENSION = 10000
        proxy.MAX_UPLOAD_IMAGE_PIXELS = 50000000
        proxy.SERVER_STRIP_EXIF_WHEN_REQUESTED = True

    def tearDown(self):
        if self.previous_secret is None:
            os.environ.pop(proxy.UPLOAD_TOKEN_SECRET_ENV, None)
        else:
            os.environ[proxy.UPLOAD_TOKEN_SECRET_ENV] = self.previous_secret
        for name, value in self.previous_values.items():
            setattr(proxy, name, value)
        shutil.rmtree(self.temp_dir_path, ignore_errors=True)

    def test_client_ip_ignores_x_forwarded_for_without_trusted_proxy(self):
        self.assertEqual(
            "203.0.113.10",
            proxy._client_ip_from_values(
                remote_host="203.0.113.10",
                forwarded_for="198.51.100.20",
                trusted_proxy_ips="",
            ),
        )

    def test_client_ip_uses_leftmost_forwarded_ip_from_trusted_proxy(self):
        self.assertEqual(
            "198.51.100.20",
            proxy._client_ip_from_values(
                remote_host="10.0.0.12",
                forwarded_for="198.51.100.20, 10.0.0.12",
                trusted_proxy_ips="10.0.0.0/24",
            ),
        )

    def test_client_ip_supports_ipv6_and_rejects_invalid_forwarded_values(self):
        self.assertEqual(
            "2001:db8::7",
            proxy._client_ip_from_values(
                remote_host="2001:db8::1",
                forwarded_for="2001:db8::7, 2001:db8::1",
                trusted_proxy_ips="2001:db8::/64",
            ),
        )
        self.assertEqual(
            "10.0.0.12",
            proxy._client_ip_from_values(
                remote_host="10.0.0.12",
                forwarded_for="not-an-ip, 198.51.100.20",
                trusted_proxy_ips="10.0.0.0/24",
            ),
        )

    def test_upload_token_accepts_valid_token(self):
        issued = proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        proxy._verify_upload_token(issued["token"], "site-123", "198.51.100.20", now=1_001)

    def test_upload_token_rejects_expired_token(self):
        issued = proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        with self.assertRaises(HTTPException) as raised:
            proxy._verify_upload_token(issued["token"], "site-123", "198.51.100.20", now=issued["expiresAt"] + 1)
        self.assertEqual(401, raised.exception.status_code)

    def test_upload_token_rejects_wrong_site(self):
        issued = proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        with self.assertRaises(HTTPException) as raised:
            proxy._verify_upload_token(issued["token"], "site-999", "198.51.100.20", now=1_001)
        self.assertEqual(401, raised.exception.status_code)

    def test_upload_token_rejects_wrong_ip(self):
        issued = proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        with self.assertRaises(HTTPException) as raised:
            proxy._verify_upload_token(issued["token"], "site-123", "198.51.100.21", now=1_001)
        self.assertEqual(401, raised.exception.status_code)

    def test_upload_token_is_one_shot(self):
        issued = proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        proxy._verify_upload_token(issued["token"], "site-123", "198.51.100.20", now=1_001)
        with self.assertRaises(HTTPException) as raised:
            proxy._verify_upload_token(issued["token"], "site-123", "198.51.100.20", now=1_002)
        self.assertEqual(401, raised.exception.status_code)

    def test_upload_token_rejects_site_quota_exceeded(self):
        proxy.UPLOAD_TOKEN_SITE_QUOTA_PER_HOUR = 1
        proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        with self.assertRaises(HTTPException) as raised:
            proxy._create_upload_token("site-123", "198.51.100.21", now=1_001)
        self.assertEqual(429, raised.exception.status_code)

    def test_upload_token_rejects_global_quota_exceeded(self):
        proxy.UPLOAD_TOKEN_GLOBAL_QUOTA_PER_HOUR = 1
        proxy._create_upload_token("site-123", "198.51.100.20", now=1_000)
        with self.assertRaises(HTTPException) as raised:
            proxy._create_upload_token("site-456", "198.51.100.21", now=1_001)
        self.assertEqual(429, raised.exception.status_code)

    async def test_save_upload_accepts_valid_jpeg(self):
        file = FakeUploadFile(make_jpeg())
        stored_filename, stored_path, size_bytes = await proxy._save_upload_file("upload-1", file)

        self.assertEqual("upload-1.jpg", stored_filename)
        self.assertTrue(os.path.exists(stored_path))
        self.assertEqual(len(make_jpeg()), size_bytes)
        self.assertTrue(file.closed)

    async def test_save_upload_preserves_metadata_when_strip_not_requested(self):
        jpeg = make_jpeg(app1_payloads=[b"Exif\x00\x00GPSLatitude=48;Model=GeoTowerCam"])
        file = FakeUploadFile(jpeg)

        _, stored_path, size_bytes = await proxy._save_upload_file(
            "upload-meta-keep",
            file,
            strip_exif_before_upload=False,
        )

        with open(stored_path, "rb") as handle:
            stored = handle.read()
        self.assertEqual(jpeg, stored)
        self.assertEqual(len(jpeg), size_bytes)
        self.assertIn(b"Exif\x00\x00", stored)

    async def test_save_upload_strips_app1_metadata_when_requested(self):
        jpeg = make_jpeg(app1_payloads=[b"Exif\x00\x00GPSLatitude=48;Model=GeoTowerCam"])
        file = FakeUploadFile(jpeg)

        _, stored_path, size_bytes = await proxy._save_upload_file(
            "upload-meta-strip",
            file,
            strip_exif_before_upload=True,
        )

        with open(stored_path, "rb") as handle:
            stored = handle.read()
        self.assertLess(size_bytes, len(jpeg))
        self.assertNotIn(b"Exif\x00\x00", stored)
        self.assertIn(b"JFIF", stored)
        proxy._jpeg_dimensions(stored)

    async def test_save_upload_rejects_fake_jpeg(self):
        file = FakeUploadFile(b"%PDF-1.7 fake", content_type="image/jpeg")
        with self.assertRaises(HTTPException) as raised:
            await proxy._save_upload_file("upload-2", file)
        self.assertEqual(400, raised.exception.status_code)
        self.assertFalse(os.path.exists(os.path.join(proxy.UPLOAD_ROOT, "upload-2.jpg")))

    async def test_save_upload_rejects_empty_file(self):
        file = FakeUploadFile(b"", content_type="image/jpeg")
        with self.assertRaises(HTTPException) as raised:
            await proxy._save_upload_file("upload-3", file)
        self.assertEqual(400, raised.exception.status_code)

    async def test_save_upload_rejects_oversized_payload(self):
        proxy.MAX_UPLOAD_BYTES = 8
        file = FakeUploadFile(make_jpeg(), content_type="image/jpeg")
        with self.assertRaises(HTTPException) as raised:
            await proxy._save_upload_file("upload-4", file)
        self.assertEqual(413, raised.exception.status_code)
        self.assertFalse(os.path.exists(os.path.join(proxy.UPLOAD_ROOT, "upload-4.jpg")))

    async def test_save_upload_rejects_excessive_dimensions(self):
        file = FakeUploadFile(make_jpeg(width=10001, height=1), content_type="image/jpeg")
        with self.assertRaises(HTTPException) as raised:
            await proxy._save_upload_file("upload-5", file)
        self.assertEqual(413, raised.exception.status_code)

    def test_purge_old_local_uploads_deletes_retry_files_after_retention(self):
        os.makedirs(proxy.UPLOAD_ROOT, exist_ok=True)
        stored_path = os.path.join(proxy.UPLOAD_ROOT, "retry.jpg")
        with open(stored_path, "wb") as handle:
            handle.write(make_jpeg())

        old_time = "2026-05-26T09:00:00+00:00"
        with proxy._db_connection() as conn:
            conn.execute(
                """
                INSERT INTO signalquest_uploads (
                    upload_id, created_at, updated_at, client_ip, site_id,
                    stored_filename, stored_path, mime_type, size_bytes, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    "upload-purge",
                    old_time,
                    old_time,
                    "198.51.100.20",
                    "site-123",
                    "retry.jpg",
                    stored_path,
                    "image/jpeg",
                    len(make_jpeg()),
                    "retry",
                ),
            )

        deleted_count = proxy.purge_old_signalquest_uploads(now=1_779_798_000)

        self.assertEqual(1, deleted_count)
        self.assertFalse(os.path.exists(stored_path))
        with proxy._db_connection() as conn:
            row = conn.execute(
                "SELECT stored_path FROM signalquest_uploads WHERE upload_id = ?",
                ("upload-purge",),
            ).fetchone()
        self.assertEqual("", row["stored_path"])


if __name__ == "__main__":
    unittest.main()
