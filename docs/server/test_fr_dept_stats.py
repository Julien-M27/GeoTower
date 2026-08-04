import shutil
import sqlite3
import unittest
from pathlib import Path

from fr_dept_stats import (
    DepartmentReference,
    antennas_from_details,
    department_code,
    ensure_dept_stat_tables,
    populate_department_stats,
    read_reference_csv,
    update_source_versions,
)


TEST_TMP_DIR = Path(__file__).resolve().parent / ".test_tmp_fr_dept_stats"

TECH_2G = 1 << 0
TECH_4G = 1 << 2
TECH_5G = 1 << 3

# Format pose par build_frequency_details_for_station : "systeme : bandes | statut | date | physique".
DETAILS_ORANGE_72 = "\n".join(
    (
        "LTE 800 : 791-821 MHz | En service | 2024-01-01 | Panneau : 90° (25m) [AER_ID: A1]",
        "NR 3500 : 3490-3540 MHz | En service | 2024-01-01 | Panneau : 90° (25m) [AER_ID: A1]",
        "LTE 1800 : 1805-1830 MHz | Projet approuve |  | Panneau : 210° (25m) [AER_ID: A2]",
        "FH 4 : 22-23 GHz | En service |  | Parabole FH : 12° (30m) [AER_ID: F1]",
    )
)

# Meme AER_ID A1 : antenne mutualisee, declaree par les deux operateurs du pylone.
DETAILS_SFR_72 = "LTE 2600 : 2500-2570 MHz | En service | 2024-02-01 | Panneau : 90° (25m) [AER_ID: A1]"

DETAILS_ORANGE_35 = "GSM 900 : 890-915 MHz | Projet approuve |  | Panneau : 0° (18m) [AER_ID: B1]"


def build_test_database(path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(path)
    conn.executescript(
        """
        CREATE TABLE localisation (id_anfr TEXT PRIMARY KEY, operateur_id INTEGER, code_insee TEXT,
            tech_mask INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE technique (id_anfr TEXT PRIMARY KEY, statut_id INTEGER, details_frequences TEXT,
            has_active INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE support (id_anfr TEXT NOT NULL, id_support TEXT NOT NULL, PRIMARY KEY (id_anfr, id_support));
        CREATE TABLE ref_operateur (id INTEGER PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE ref_statut (id INTEGER PRIMARY KEY, libelle TEXT NOT NULL);
        CREATE TABLE source_versions (source_key TEXT PRIMARY KEY, source_value TEXT NOT NULL);
        """
    )
    conn.executemany("INSERT INTO ref_operateur VALUES (?, ?)", ((1, "ORANGE"), (2, "SFR")))
    conn.executemany("INSERT INTO ref_statut VALUES (?, ?)", ((1, "En service"), (2, "Projet approuve")))
    conn.executemany(
        "INSERT INTO localisation (id_anfr, operateur_id, code_insee, tech_mask) VALUES (?, ?, ?, ?)",
        (
            ("S1", 1, "72181", TECH_4G | TECH_5G),
            ("S2", 2, "72181", TECH_4G),
            ("S3", 1, "35238", TECH_2G),
        ),
    )
    conn.executemany(
        "INSERT INTO technique (id_anfr, statut_id, details_frequences, has_active) VALUES (?, ?, ?, ?)",
        (
            ("S1", 1, DETAILS_ORANGE_72, 1),
            ("S2", 1, DETAILS_SFR_72, 1),
            ("S3", 2, DETAILS_ORANGE_35, 0),
        ),
    )
    conn.executemany(
        "INSERT INTO support (id_anfr, id_support) VALUES (?, ?)",
        (("S1", "SUP1"), ("S2", "SUP1"), ("S3", "SUP2")),
    )
    ensure_dept_stat_tables(conn)
    conn.commit()
    return conn


class DepartmentCodeTest(unittest.TestCase):
    def test_extracts_department_from_insee_code(self):
        self.assertEqual("72", department_code("72181"))
        self.assertEqual("01", department_code("01004"))
        self.assertEqual("2A", department_code("2A004"))
        self.assertEqual("2B", department_code("2b033"))
        self.assertEqual("974", department_code("97411"))
        self.assertEqual("987", department_code("98735"))

    def test_pads_codes_that_lost_their_leading_zero(self):
        self.assertEqual("01", department_code("1004"))

    def test_rejects_unusable_codes(self):
        self.assertIsNone(department_code(None))
        self.assertIsNone(department_code(""))
        self.assertIsNone(department_code("72"))
        self.assertIsNone(department_code("ABCDE"))


class AntennasFromDetailsTest(unittest.TestCase):
    def test_counts_antennas_by_technology(self):
        antennas = antennas_from_details(DETAILS_ORANGE_72)

        self.assertEqual({"A1", "A2"}, antennas.by_tech["4G"])
        self.assertEqual({"A1"}, antennas.by_tech["5G"])
        # Une antenne qui porte 4G et 5G ne compte qu'une fois dans le total.
        self.assertEqual({"A1", "A2"}, antennas.distinct())
        self.assertEqual({"A1"}, antennas.distinct_active())
        self.assertEqual({"4G", "5G"}, antennas.techs)
        self.assertEqual({"4G", "5G"}, antennas.active_techs)

    def test_keeps_hertzian_beams_apart(self):
        antennas = antennas_from_details(DETAILS_ORANGE_72)

        self.assertEqual({"F1"}, antennas.fh)
        self.assertNotIn("F1", antennas.distinct())
        self.assertNotIn("FH", antennas.by_tech)

    def test_ignores_lines_without_aer_id_but_counts_them(self):
        antennas = antennas_from_details("LTE 800 : 791-821 MHz | En service |  | Azimut non specifie")

        self.assertEqual(0, len(antennas.distinct()))
        self.assertEqual({"4G"}, antennas.techs)
        self.assertEqual(1, antennas.lines_without_aer)

    def test_handles_missing_details(self):
        self.assertEqual(0, len(antennas_from_details(None).distinct()))
        self.assertEqual(0, len(antennas_from_details("").distinct()))


class PopulateDepartmentStatsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        TEST_TMP_DIR.mkdir(parents=True, exist_ok=True)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(TEST_TMP_DIR, ignore_errors=True)

    def setUp(self):
        self.db_path = TEST_TMP_DIR / f"{self.id().rsplit('.', 1)[-1]}.db"
        if self.db_path.exists():
            self.db_path.unlink()
        self.conn = build_test_database(self.db_path)
        self.addCleanup(self.conn.close)

    def dept_row(self, dept_code):
        cursor = self.conn.execute("SELECT * FROM dept_stat_current WHERE dept_code = ?", (dept_code,))
        columns = [description[0] for description in cursor.description]
        row = cursor.fetchone()
        return dict(zip(columns, row)) if row else None

    def matrix(self):
        return {
            (dept, operator, tech): (supports, stations, antennas)
            for dept, operator, tech, supports, stations, antennas in self.conn.execute(
                "SELECT dept_code, operator_name, tech, supports, stations, antennas FROM dept_stat_operator_tech"
            )
        }

    def test_counts_supports_stations_and_antennas_per_department(self):
        populate_department_stats(self.conn)

        sarthe = self.dept_row("72")
        self.assertEqual(1, sarthe["supports"])
        self.assertEqual(2, sarthe["stations"])
        # A1 + A2 pour Orange, A1 pour SFR : une antenne mutualisee est declaree par station.
        self.assertEqual(3, sarthe["antennas"])
        self.assertEqual(1, sarthe["antennas_fh"])

        ille_et_vilaine = self.dept_row("35")
        self.assertEqual(1, ille_et_vilaine["supports"])
        self.assertEqual(1, ille_et_vilaine["stations"])
        self.assertEqual(1, ille_et_vilaine["antennas"])
        self.assertEqual(0, ille_et_vilaine["antennas_fh"])

    def test_separates_authorised_and_active_counts(self):
        populate_department_stats(self.conn)

        sarthe = self.dept_row("72")
        self.assertEqual(2, sarthe["stations_active"])
        self.assertEqual(2, sarthe["antennas_active"])

        # Station en projet : declaree mais rien en service.
        ille_et_vilaine = self.dept_row("35")
        self.assertEqual(0, ille_et_vilaine["stations_active"])
        self.assertEqual(0, ille_et_vilaine["antennas_active"])

    def test_breaks_down_by_operator_and_technology(self):
        populate_department_stats(self.conn)
        matrix = self.matrix()

        self.assertEqual((1, 1, 2), matrix[("72", "ORANGE", "4G")])
        self.assertEqual((1, 1, 1), matrix[("72", "ORANGE", "5G")])
        self.assertEqual((1, 1, 1), matrix[("72", "SFR", "4G")])
        # 'ALL' cote technologie : antennes distinctes de l'operateur, pas la somme des colonnes.
        self.assertEqual((1, 1, 2), matrix[("72", "ORANGE", "ALL")])
        self.assertEqual((1, 2, 3), matrix[("72", "ALL", "4G")])
        self.assertEqual((1, 2, 3), matrix[("72", "ALL", "ALL")])
        self.assertNotIn(("72", "ORANGE", "2G"), matrix)
        self.assertNotIn(("72", "ALL", "FH"), matrix)

    def test_matrix_totals_match_the_department_row(self):
        populate_department_stats(self.conn)

        sarthe = self.dept_row("72")
        supports, stations, antennas = self.matrix()[("72", "ALL", "ALL")]
        self.assertEqual((sarthe["supports"], sarthe["stations"], sarthe["antennas"]), (supports, stations, antennas))

    def test_computes_ratios_from_the_reference(self):
        reference = {
            "72": DepartmentReference(
                code="72",
                name="Sarthe",
                region_code="52",
                area_km2=6206.0,
                population=566506,
                population_year="2020",
            )
        }

        populate_department_stats(self.conn, reference)

        sarthe = self.dept_row("72")
        self.assertEqual("Sarthe", sarthe["dept_name"])
        self.assertEqual(566506, sarthe["population"])
        self.assertEqual("2020", sarthe["population_year"])
        # Les ratios sont stockes arrondis a 1e-6 pres.
        self.assertAlmostEqual(2.0, sarthe["stations_per_support"], places=9)
        self.assertAlmostEqual(1.5, sarthe["antennas_per_station"], places=9)
        self.assertAlmostEqual(round(1 / 6206.0, 6), sarthe["supports_per_km2"], places=9)
        self.assertAlmostEqual(round(3 / 6206.0, 6), sarthe["antennas_per_km2"], places=9)
        self.assertAlmostEqual(round(2 / (566506 / 1000.0), 6), sarthe["stations_per_1k_hab"], places=9)
        self.assertAlmostEqual(round(566506 / 3, 6), sarthe["hab_per_antenna"], places=9)

    def test_leaves_ratios_empty_without_reference(self):
        populate_department_stats(self.conn)

        sarthe = self.dept_row("72")
        self.assertIsNone(sarthe["area_km2"])
        self.assertIsNone(sarthe["population"])
        self.assertIsNone(sarthe["supports_per_km2"])
        self.assertIsNone(sarthe["hab_per_station"])
        # Les ratios qui ne dependent que de l'ANFR restent calcules.
        self.assertAlmostEqual(2.0, sarthe["stations_per_support"], places=6)

    def test_reports_stations_without_usable_insee_code(self):
        self.conn.execute(
            "INSERT INTO localisation (id_anfr, operateur_id, code_insee, tech_mask) VALUES (?, ?, ?, ?)",
            ("S4", 1, None, TECH_4G),
        )

        result = populate_department_stats(self.conn)

        self.assertEqual(1, result.stations_without_department)
        self.assertEqual(2, result.department_rows)
        self.assertEqual(["35", "72"], sorted(result.departments_without_reference))

    def test_replaces_previous_rows_on_each_run(self):
        populate_department_stats(self.conn)
        first_matrix = self.matrix()
        populate_department_stats(self.conn)

        self.assertEqual(2, self.conn.execute("SELECT COUNT(*) FROM dept_stat_current").fetchone()[0])
        self.assertEqual(first_matrix, self.matrix())

    def test_records_reference_sources(self):
        update_source_versions(self.conn, "2020")

        stored = dict(self.conn.execute("SELECT source_key, source_value FROM source_versions"))
        self.assertEqual("2020", stored["dept_stats_population_year"])
        self.assertIn("geo.api.gouv.fr", stored["dept_stats_population_source"])
        self.assertIn("geo.api.gouv.fr", stored["dept_stats_area_source"])


class ReferenceCsvTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        TEST_TMP_DIR.mkdir(parents=True, exist_ok=True)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(TEST_TMP_DIR, ignore_errors=True)

    def test_reads_manual_reference_file(self):
        path = TEST_TMP_DIR / "departements_fr.csv"
        path.write_text(
            "code;nom;superficie_km2;population;annee_population\n"
            "72;Sarthe;6206;566 506;2020\n"
            "2A;Corse-du-Sud;4014,4;158507;2020\n",
            encoding="utf-8",
        )

        reference = read_reference_csv(path, "2022")

        self.assertEqual("Sarthe", reference["72"].name)
        self.assertEqual(6206.0, reference["72"].area_km2)
        self.assertEqual(566506, reference["72"].population)
        self.assertEqual("2020", reference["72"].population_year)
        self.assertEqual(4014.4, reference["2A"].area_km2)

    def test_falls_back_to_the_requested_year_when_absent(self):
        path = TEST_TMP_DIR / "departements_sans_annee.csv"
        path.write_text("code,nom,superficie_km2,population\n72,Sarthe,6206,566506\n", encoding="utf-8")

        reference = read_reference_csv(path, "2022")

        self.assertEqual("2022", reference["72"].population_year)


if __name__ == "__main__":
    unittest.main()
