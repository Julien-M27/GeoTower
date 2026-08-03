import asyncio
import os
import shutil
import sqlite3
import sys
import types
import unittest
import uuid
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

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

    def query(default=None, *args, **kwargs):
        return default

    fastapi_stub.APIRouter = APIRouter
    fastapi_stub.HTTPException = HTTPException
    fastapi_stub.Query = query
    sys.modules["fastapi"] = fastapi_stub

import build_live_fr_db
import live_fr_api


def create_source_db(path: Path) -> None:
    conn = sqlite3.connect(path)
    conn.executescript(
        """
        CREATE TABLE localisation (
            id_anfr TEXT NOT NULL PRIMARY KEY,
            operateur_id INTEGER,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            azimuts TEXT,
            code_insee TEXT,
            azimuts_fh TEXT,
            tech_mask INTEGER NOT NULL,
            band_mask INTEGER NOT NULL,
            arcep_nidt TEXT,
            is_zb INTEGER NOT NULL
        );
        CREATE TABLE technique (
            id_anfr TEXT NOT NULL PRIMARY KEY,
            adm_id INTEGER,
            statut_id INTEGER,
            date_implantation TEXT,
            date_service TEXT,
            date_modif TEXT,
            details_frequences TEXT,
            adresse TEXT,
            has_active INTEGER NOT NULL
        );
        CREATE TABLE support (
            id_anfr TEXT NOT NULL,
            id_support TEXT NOT NULL,
            nat_id INTEGER,
            tpo_id INTEGER,
            hauteur REAL,
            PRIMARY KEY(id_anfr, id_support)
        );
        CREATE TABLE antenne (
            aer_id TEXT NOT NULL PRIMARY KEY,
            id_anfr TEXT NOT NULL,
            id_support TEXT,
            tae_id INTEGER,
            azimut INTEGER,
            hauteur_bas REAL,
            is_fh INTEGER NOT NULL
        );
        CREATE TABLE ref_operateur (id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_nature (nat_id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_proprietaire (tpo_id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_exploitant (adm_id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_type_antenne (tae_id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_statut (id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_commune (code_insee TEXT NOT NULL PRIMARY KEY, nom TEXT NOT NULL);
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
        CREATE TABLE metadata (
            version TEXT NOT NULL PRIMARY KEY,
            schema_version INTEGER NOT NULL,
            country_code TEXT NOT NULL,
            country_name TEXT,
            source TEXT NOT NULL,
            date_maj_anfr TEXT,
            zip_version TEXT
        );
        INSERT INTO ref_operateur VALUES (1, 'Orange');
        INSERT INTO ref_nature VALUES (1, 'Pylone');
        INSERT INTO ref_proprietaire VALUES (1, 'TowerCo');
        INSERT INTO ref_exploitant VALUES (1, 'Exploitant');
        INSERT INTO ref_type_antenne VALUES (1, 'Panneau');
        INSERT INTO ref_statut VALUES (1, 'En service');
        INSERT INTO ref_commune VALUES ('75056', 'PARIS');
        INSERT INTO localisation VALUES (
            '0000000001',
            1,
            48.8566,
            2.3522,
            '0,120,240',
            '75056',
            NULL,
            12,
            0,
            'NIDT-1',
            0
        );
        INSERT INTO technique VALUES (
            '0000000001',
            1,
            1,
            '2024-01-01',
            '2024-01-02',
            '2024-01-03',
            '5G NR 4200 : 3800.1-4200 MHz | En service',
            '1 rue Test, 75000 PARIS',
            1
        );
        INSERT INTO support VALUES ('0000000001', '123', 1, 1, 30.5);
        INSERT INTO antenne VALUES ('AER1', '0000000001', '123', 1, 120, 25.0, 0);
        INSERT INTO radio_stat_current VALUES ('ORANGE', 'support', 'ALL', 'Supports', 1, 1);
        INSERT INTO radio_stat_current VALUES ('ORANGE', 'tech', '5G', '5G', 1, 1);
        INSERT INTO radio_stat_current VALUES ('ORANGE', 'band', '5G|3500', '3500 MHz', 1, 1);
        INSERT INTO radio_stat_weekly VALUES ('2026-W23', '2026-06-01', '2026-06-07', 'ORANGE', 'support', 'ALL', 'Supports', 1, 1);
        INSERT INTO radio_stat_weekly VALUES ('2026-W23', '2026-06-01', '2026-06-07', 'ORANGE', 'tech', '5G', '5G', 1, 1);
        INSERT INTO metadata VALUES ('20260607_1200', 7, 'FR', 'France', 'ANFR', '2026-06-07', 'anfr.zip');
        """
    )
    conn.commit()
    conn.close()


class LiveFrApiTest(unittest.TestCase):
    def setUp(self):
        self.tmp_dir = Path(os.getcwd()) / f"test-tmp-live-fr-{uuid.uuid4().hex}"
        self.tmp_dir.mkdir(parents=True)
        self.source_db = self.tmp_dir / "geotower_fr.db"
        self.live_db = self.tmp_dir / "geotower_live_fr.db"
        create_source_db(self.source_db)

    def tearDown(self):
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def test_build_live_db_and_query_api(self):
        output = build_live_fr_db.build_live_fr_db(
            source_db_path=self.source_db,
            live_db_path=self.live_db,
        )
        self.assertEqual(self.live_db, output)

        with sqlite3.connect(self.live_db) as conn:
            row_count = conn.execute("SELECT COUNT(*) FROM site_summary").fetchone()[0]
            rtree_count = conn.execute("SELECT COUNT(*) FROM site_rtree").fetchone()[0]
            masks = conn.execute("SELECT tech_mask, band_mask FROM site_summary LIMIT 1").fetchone()
        self.assertEqual(1, row_count)
        self.assertEqual(1, rtree_count)
        self.assertNotEqual(0, masks[0] & 8)
        self.assertNotEqual(0, masks[1] & 4194304)

        old_live_path = live_fr_api.LIVE_DB_PATH
        try:
            live_fr_api.LIVE_DB_PATH = str(self.live_db)
            status = asyncio.run(live_fr_api.get_live_fr_status())
            nearby = asyncio.run(
                live_fr_api.get_live_fr_nearby_sites(
                    lat=48.8566,
                    lon=2.3522,
                    limit=10,
                    radius_km=5.0,
                )
            )
            site = asyncio.run(live_fr_api.get_live_fr_site("123"))
            current_stats = asyncio.run(live_fr_api.get_live_fr_current_stats(operator=["orange"]))
            weekly_stats = asyncio.run(live_fr_api.get_live_fr_weekly_stats(operator=["ORANGE"]))
        finally:
            live_fr_api.LIVE_DB_PATH = old_live_path

        self.assertEqual("FR", status["country_code"])
        self.assertEqual(1, status["row_count"])
        self.assertEqual("0000000001", nearby["sites"][0]["id_anfr"])
        self.assertEqual("0000000001", site["summary"]["id_anfr"])
        self.assertEqual("1 rue Test, 75000 PARIS", site["detail"]["adresse"])
        self.assertEqual("123", site["supports"][0]["id_support"])
        self.assertEqual(3, len(current_stats["rows"]))
        self.assertEqual(1, current_stats["rows"][0]["total_count"])
        self.assertEqual("2026-W23", weekly_stats["rows"][0]["week_key"])


if __name__ == "__main__":
    unittest.main()
