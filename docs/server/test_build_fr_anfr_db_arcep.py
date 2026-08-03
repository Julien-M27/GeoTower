import shutil
import unittest
import zipfile
from pathlib import Path

import build_fr_anfr_db as builder
from build_fr_anfr_db import (
    discover_arcep_site_csv_sources,
    extract_quarterly_version_from_name,
    latest_quarterly_version_from_sources,
    load_arcep_site_metadata,
)


ARCEP_SITES_CSV = """code_op;nom_op;num_site;id_station_anfr;site_zb;site_DCC
20801;Orange;NIDT-123;0000004567;1;0
20810;SFR;NIDT-987;0000007654;0;0
20820;Bouygues Telecom;NIDT-456;0000003456;0;1
"""

SUP_NATURE_CSV = """nat_id;nat_lb_nom
23;Pylone treillis
17;Chateau d'eau
"""

MONTHLY_ANFR_FILES = (
    "SUP_STATION.txt",
    "SUP_SUPPORT.txt",
    "SUP_ANTENNE.txt",
    "SUP_EMETTEUR.txt",
    "SUP_BANDE.txt",
)

TEST_TMP_DIR = Path(__file__).resolve().parent / ".test_tmp_build_fr_anfr_db"


def reset_test_dir(name: str) -> Path:
    path = TEST_TMP_DIR / name
    if path.exists():
        shutil.rmtree(path, ignore_errors=True)
    path.mkdir(parents=True)
    return path


class BuildFrAnfrDbArcepTest(unittest.TestCase):
    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(TEST_TMP_DIR, ignore_errors=True)

    def test_formats_anfr_frequency_unit_codes(self):
        self.assertEqual("26,5-26,9 GHz", builder.format_band_range("26,5", "26,9", "G"))
        self.assertEqual("1472-1492 MHz", builder.format_band_range("1472", "1492", "M"))
        self.assertEqual("900-950 kHz", builder.format_band_range("900", "950", "K"))
        self.assertEqual("100-200 Hz", builder.format_band_range("100", "200", "H"))

    def test_maps_5g_4200_frequency_range_to_band_mask(self):
        station = {"tech_mask": 0, "band_mask": 0}

        builder.update_masks_from_system_and_band(station, "5G NR 4200", 3800.1, 4200.0)

        self.assertNotEqual(0, station["tech_mask"] & builder.TECH_5G)
        self.assertNotEqual(0, station["band_mask"] & builder.BAND_5G_4200)

    def test_loads_arcep_nidt_and_zb_from_quarterly_zip(self):
        imports_dir = reset_test_dir("arcep_sites")
        try:
            sources_dir = imports_dir / "france_sources" / "2024_T1"
            sources_dir.mkdir(parents=True)
            with zipfile.ZipFile(sources_dir / "2024_T1_sites_Metropole.zip", "w") as archive:
                archive.writestr("2024_T1_sites_Metropole.csv", ARCEP_SITES_CSV)

            metadata = load_arcep_site_metadata(imports_dir)
        finally:
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual(
            {"arcep_nidt": "NIDT-123", "is_zb": 1},
            metadata[("0000004567", "ORANGE")],
        )
        self.assertEqual(
            {"arcep_nidt": "NIDT-987", "is_zb": 0},
            metadata[("0000007654", "SFR")],
        )
        self.assertEqual(
            {"arcep_nidt": "NIDT-456", "is_zb": 1},
            metadata[("0000003456", "BOUYGUES TELECOM")],
        )

    def test_extracts_quarterly_version_from_arcep_site_filename(self):
        self.assertEqual(
            "2025-T4",
            extract_quarterly_version_from_name("2025_T4_sites_Metropole (1).csv"),
        )

    def test_latest_quarterly_version_uses_latest_source_filename(self):
        imports_dir = reset_test_dir("arcep_quarter_version")
        try:
            sources_dir = imports_dir / "france_sources"
            sources_dir.mkdir(parents=True)
            with zipfile.ZipFile(sources_dir / "2025_T3_sites_Metropole.zip", "w") as archive:
                archive.writestr("2025_T3_sites_Metropole.csv", ARCEP_SITES_CSV)
            with zipfile.ZipFile(sources_dir / "2025_T4_sites_Metropole.zip", "w") as archive:
                archive.writestr("2025_T4_sites_Metropole.csv", ARCEP_SITES_CSV)

            sources = discover_arcep_site_csv_sources(imports_dir)
        finally:
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual("2025-T4", latest_quarterly_version_from_sources(sources))


class BuildFrAnfrDbReferenceZipTest(unittest.TestCase):
    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(TEST_TMP_DIR, ignore_errors=True)

    def test_loads_reference_from_zip_in_references_dir(self):
        imports_dir = reset_test_dir("reference_zip_refs")
        refs_dir = imports_dir / "references"
        refs_dir.mkdir(parents=True)
        with zipfile.ZipFile(refs_dir / "references_anfr.zip", "w") as archive:
            archive.writestr("REFERENCES/SUP_NATURE.txt", SUP_NATURE_CSV)

        old_data_dir = builder.DATA_DIR
        old_ref_dir = builder.REF_DIR
        try:
            builder.DATA_DIR = str(imports_dir)
            builder.REF_DIR = str(refs_dir)

            references = builder.charger_reference("SUP_NATURE.txt", "nat_id", "nat_lb_nom")
        finally:
            builder.DATA_DIR = old_data_dir
            builder.REF_DIR = old_ref_dir
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual(
            {"23": "Pylone treillis", "17": "Chateau d'eau"},
            references,
        )

    def test_loads_reference_from_zip_next_to_import_sources(self):
        imports_dir = reset_test_dir("reference_zip_imports")
        refs_dir = imports_dir / "empty_references"
        sources_dir = imports_dir / "france_sources" / "20260531-export-etalab-ref"
        refs_dir.mkdir(parents=True)
        sources_dir.mkdir(parents=True)
        with zipfile.ZipFile(sources_dir / "20260531-export-etalab-ref.zip", "w") as archive:
            archive.writestr("SUP_NATURE.csv", SUP_NATURE_CSV)

        old_data_dir = builder.DATA_DIR
        old_ref_dir = builder.REF_DIR
        try:
            builder.DATA_DIR = str(imports_dir)
            builder.REF_DIR = str(refs_dir)

            references = builder.charger_reference("SUP_NATURE.txt", "nat_id", "nat_lb_nom")
        finally:
            builder.DATA_DIR = old_data_dir
            builder.REF_DIR = old_ref_dir
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual("Pylone treillis", references["23"])

    def test_detects_monthly_zip_inside_etalab_data_export_dir(self):
        imports_dir = reset_test_dir("monthly_zip_imports")
        refs_dir = imports_dir / "empty_references"
        export_dir = imports_dir / "france_sources" / "20260531-export-etalab-data"
        refs_dir.mkdir(parents=True)
        export_dir.mkdir(parents=True)
        monthly_zip = export_dir / "20260531-export-etalab-data.zip"
        with zipfile.ZipFile(monthly_zip, "w") as archive:
            for filename in MONTHLY_ANFR_FILES:
                archive.writestr(filename, "id;value\n1;x\n")

        old_data_dir = builder.DATA_DIR
        old_ref_dir = builder.REF_DIR
        try:
            builder.DATA_DIR = str(imports_dir)
            builder.REF_DIR = str(refs_dir)

            detected_zip = builder.get_latest_monthly_zip()
        finally:
            builder.DATA_DIR = old_data_dir
            builder.REF_DIR = old_ref_dir
            shutil.rmtree(imports_dir, ignore_errors=True)

        self.assertEqual(str(monthly_zip), detected_zip)


if __name__ == "__main__":
    unittest.main()
