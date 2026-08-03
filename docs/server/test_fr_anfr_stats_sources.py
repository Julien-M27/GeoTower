import sqlite3
import shutil
import unittest
import zipfile
from pathlib import Path

from fr_anfr_stats import (
    discover_weekly_csv_files,
    ensure_arcep_site_columns,
    ensure_stats_tables,
    populate_current_stats,
    populate_weekly_stats,
)


OBSERVATORY_CSV = """adm_lb_nom;sup_id;emr_lb_systeme;generation;statut;date_maj
Orange;SUP-1;LTE 800;4G;En service;2024-01-01
Orange;SUP-2;NR 3500;5G;Accord ANFR;2024-04-01
"""

QUARTER_FILENAME_CSV = """adm_lb_nom;sup_id;emr_lb_systeme;generation;statut
Orange;SUP-1;LTE 800;4G;En service
"""

TEST_TMP_DIR = Path(__file__).resolve().parent / ".test_tmp_fr_anfr_stats"


def reset_test_dir(name: str) -> Path:
    path = TEST_TMP_DIR / name
    if path.exists():
        shutil.rmtree(path, ignore_errors=True)
    path.mkdir(parents=True)
    return path


class FrAnfrStatsSourceTest(unittest.TestCase):
    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(TEST_TMP_DIR, ignore_errors=True)

    def test_discovers_observatory_csv_inside_zip_and_ignores_monthly_zip(self):
        imports_dir = reset_test_dir("discover_sources")
        try:
            sources_dir = imports_dir / "france_sources"
            sources_dir.mkdir()

            with zipfile.ZipFile(sources_dir / "anfr_monthly.zip", "w") as archive:
                for name in ("SUP_STATION.txt", "SUP_SUPPORT.txt", "SUP_ANTENNE.txt", "SUP_EMETTEUR.txt", "SUP_BANDE.txt"):
                    archive.writestr(name, "")

            with zipfile.ZipFile(sources_dir / "observatoire_2024_t2.zip", "w") as archive:
                archive.writestr("observatoire_20240401.csv", OBSERVATORY_CSV)

            sources = discover_weekly_csv_files(imports_dir, ())
        finally:
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual(1, len(sources))
        self.assertEqual("observatoire_2024_t2.zip!observatoire_20240401.csv", sources[0].display_name)

    def test_discovers_observatory_csv_inside_nested_france_sources_dir(self):
        imports_dir = reset_test_dir("discover_nested_sources")
        try:
            sources_dir = imports_dir / "france_sources" / "20260531-export-etalab-data"
            sources_dir.mkdir(parents=True)

            with zipfile.ZipFile(sources_dir / "observatoire_2026_t2.zip", "w") as archive:
                archive.writestr("observatoire_20260529.csv", OBSERVATORY_CSV)

            sources = discover_weekly_csv_files(imports_dir, ())
        finally:
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual(1, len(sources))
        self.assertEqual("observatoire_2026_t2.zip!observatoire_20260529.csv", sources[0].display_name)

    def test_populates_weekly_stats_from_multiple_dates_in_one_csv(self):
        imports_dir = reset_test_dir("populate_stats")
        try:
            source_path = imports_dir / "observatoire_2024_t2.zip"
            with zipfile.ZipFile(source_path, "w") as archive:
                archive.writestr("observatoire_20240401.csv", OBSERVATORY_CSV)

            sources = discover_weekly_csv_files(imports_dir, ())
            conn = sqlite3.connect(":memory:")
            try:
                ensure_stats_tables(conn)
                inserted = populate_weekly_stats(conn, sources)
                rows = set(
                    conn.execute(
                        """
                        SELECT week_key, category, item_key, total_count, active_count
                        FROM radio_stat_weekly
                        """
                    ).fetchall()
                )
            finally:
                conn.close()
        finally:
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertGreater(inserted, 0)
        self.assertIn(("2024-W01", "support", "ALL", 1, 1), rows)
        self.assertIn(("2024-W14", "tech", "5G", 1, 0), rows)

    def test_populates_current_stats_with_overseas_parent_operator_split(self):
        conn = sqlite3.connect(":memory:")
        try:
            conn.executescript(
                """
                CREATE TABLE localisation (
                    id_anfr TEXT NOT NULL PRIMARY KEY,
                    operateur_id INTEGER,
                    code_insee TEXT,
                    tech_mask INTEGER NOT NULL,
                    band_mask INTEGER NOT NULL
                );
                CREATE TABLE technique (
                    id_anfr TEXT NOT NULL PRIMARY KEY,
                    statut_id INTEGER,
                    has_active INTEGER NOT NULL,
                    details_frequences TEXT
                );
                CREATE TABLE support (
                    id_anfr TEXT NOT NULL,
                    id_support TEXT NOT NULL,
                    PRIMARY KEY(id_anfr, id_support)
                );
                CREATE TABLE ref_operateur (id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
                CREATE TABLE ref_statut (id INTEGER NOT NULL PRIMARY KEY, libelle TEXT NOT NULL);
                INSERT INTO ref_operateur VALUES (1, 'Orange');
                INSERT INTO ref_operateur VALUES (2, 'SFR Caraibe');
                INSERT INTO ref_operateur VALUES (3, 'SFR Mayotte');
                INSERT INTO ref_statut VALUES (1, 'En service');

                INSERT INTO localisation VALUES ('0000000001', 1, '75056', 12, 4096);
                INSERT INTO localisation VALUES ('0000000002', 1, '97101', 4, 16);
                INSERT INTO localisation VALUES ('0000000003', 2, '97101', 4, 16);
                INSERT INTO localisation VALUES ('0000000004', 3, '97611', 8, 2048);

                INSERT INTO technique VALUES ('0000000001', 1, 1, NULL);
                INSERT INTO technique VALUES ('0000000002', 1, 1, NULL);
                INSERT INTO technique VALUES ('0000000003', 1, 1, NULL);
                INSERT INTO technique VALUES ('0000000004', 1, 1, NULL);

                INSERT INTO support VALUES ('0000000001', 'SUP-OR-METRO');
                INSERT INTO support VALUES ('0000000002', 'SUP-OR-OM');
                INSERT INTO support VALUES ('0000000003', 'SUP-SFR-CARAIBE');
                INSERT INTO support VALUES ('0000000004', 'SUP-SFR-MAYOTTE');
                """
            )
            ensure_stats_tables(conn)
            populate_current_stats(conn)
            support_counts = {
                row[0]: row[1:]
                for row in conn.execute(
                    """
                    SELECT operator_name, total_count, active_count
                    FROM radio_stat_current
                    WHERE category = 'support' AND item_key = 'ALL'
                    """
                ).fetchall()
            }
            tech_counts = {
                (row[0], row[1]): row[2]
                for row in conn.execute(
                    """
                    SELECT operator_name, item_key, total_count
                    FROM radio_stat_current
                    WHERE category = 'tech'
                    """
                ).fetchall()
            }
        finally:
            conn.close()

        self.assertEqual((1, 1), support_counts["ORANGE"])
        self.assertEqual((1, 1), support_counts["ORANGE_OVERSEAS"])
        self.assertEqual((1, 1), support_counts["SFR CARAIBE"])
        self.assertEqual((1, 1), support_counts["SFR_OVERSEAS"])
        self.assertEqual(1, tech_counts[("ORANGE_OVERSEAS", "4G")])
        self.assertEqual(1, tech_counts[("SFR_OVERSEAS", "5G")])

    def test_uses_french_quarter_filename_when_date_column_is_missing(self):
        imports_dir = reset_test_dir("quarter_filename")
        try:
            source_path = imports_dir / "observatoire_trimestriel.zip"
            with zipfile.ZipFile(source_path, "w") as archive:
                archive.writestr("1er_trimestre_2024.csv", QUARTER_FILENAME_CSV)

            sources = discover_weekly_csv_files(imports_dir, ())
            conn = sqlite3.connect(":memory:")
            try:
                ensure_stats_tables(conn)
                populate_weekly_stats(conn, sources)
                week_keys = {
                    row[0]
                    for row in conn.execute("SELECT DISTINCT week_key FROM radio_stat_weekly").fetchall()
                }
            finally:
                conn.close()
        finally:
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual({"2024-W01"}, week_keys)

    def test_adds_arcep_columns_to_existing_localisation_table(self):
        conn = sqlite3.connect(":memory:")
        try:
            conn.execute(
                """
                CREATE TABLE localisation (
                    id_anfr TEXT NOT NULL PRIMARY KEY,
                    operateur_id INTEGER,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    tech_mask INTEGER NOT NULL,
                    band_mask INTEGER NOT NULL
                )
                """
            )
            ensure_arcep_site_columns(conn)
            columns = {row[1]: row for row in conn.execute("PRAGMA table_info(localisation)").fetchall()}
        finally:
            conn.close()

        self.assertIn("arcep_nidt", columns)
        self.assertIn("is_zb", columns)
        self.assertEqual(1, columns["is_zb"][3])


if __name__ == "__main__":
    unittest.main()
