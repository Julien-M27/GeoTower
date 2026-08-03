"""Tests du builder sites HS "operateurs uniquement" (docs/server/build_sites_hs.py).

Le reseau est simule (monkeypatch de `download`) : aucun appel HTTP reel.
Lancer depuis docs/server :  python -m pytest test_build_sites_hs.py
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
import urllib.error
from pathlib import Path

import pytest

sys.path.insert(0, os.path.dirname(__file__))
import build_sites_hs as bhs  # noqa: E402


TEST_SOURCES = {
    "sfr": {"label": "SFR", "url": "test://sfr", "code_columns": ("code_site_op",)},
    "free": {"label": "Free Mobile", "url": "test://free", "code_columns": ("code_site_op",)},
}

SFR_CSV = (
    "Export sites hors service SFR\n"
    "Code_Site_OP;Station_ANFR;Latitude;Longitude;Departement;Code_Postal;Code_INSEE;"
    "Commune;2GVoix;3GVoix;4GVoix;2GData;3GData;4GData;Voix;Data;Propre;Raison;Detail;Debut;Fin\n"
    # 1) station fournie par l'operateur
    "SI0001;0751234567;48.8566;2.3522;Paris;75001;75101;PARIS;HS;OK;OK;;OK;OK;HS;OK;oui;INT;"
    "Incident en cours;2026-01-01 10:00:00;2026-07-01\n"
    # 2) pas de station -> a completer via la DB ANFR
    "SI0002;;48.8600;2.3400;Paris;75002;75102;PARIS;OK;OK;HS;;OK;HS;OK;HS;non;INT;"
    "Coupure data;2026-02-01;2026-08-01\n"
    # 3) doublon de la ligne 1 (meme station) avec fenetre de dates plus large
    "SI0001;0751234567;48.8566;2.3522;Paris;75001;75101;PARIS;HS;OK;OK;;OK;OK;HS;OK;oui;INT;"
    "Incident en cours;2025-12-15;2026-07-15\n"
)

FREE_CSV = (
    "Pannes Free Mobile\n"
    "Code_Site_OP;Latitude;Longitude;Commune;Code_INSEE;2GVoix;3GVoix;4GVoix;3GData;4GData;5GData;"
    "Voix;Data;Raison;Detail;Debut;Fin\n"
    "75056_ABC;48.8000;2.3000;PARIS;75000;OK;OK;HS;OK;HS;NE;HS;HS;MAINT;Maintenance;2026-03-01;2026-09-01\n"
)


def make_db(path: Path) -> None:
    con = sqlite3.connect(str(path))
    try:
        con.execute("CREATE TABLE ref_operateur (id INTEGER PRIMARY KEY, libelle TEXT)")
        con.execute(
            "CREATE TABLE localisation "
            "(id_anfr TEXT, latitude REAL, longitude REAL, code_insee TEXT, operateur_id INTEGER)"
        )
        con.execute("INSERT INTO ref_operateur (id, libelle) VALUES (1, 'SFR')")
        # Site SFR a ~13 m de la ligne SFR sans station (meme INSEE 75102).
        con.execute(
            "INSERT INTO localisation VALUES ('0759999999', 48.8601, 2.3401, '75102', 1)"
        )
        con.commit()
    finally:
        con.close()


def make_args(tmp_path: Path, db: str | None, allow_partial: bool = False) -> argparse.Namespace:
    return argparse.Namespace(
        output=str(tmp_path / "sites_hs.geojson"),
        date_output=str(tmp_path / "sites_hs_date.txt"),
        report_output=str(tmp_path / "report.json"),
        db=db,
        date=None,
        timeout=5,
        grid_size=0.01,
        db_same_insee_match_m=250.0,
        db_spatial_match_m=120.0,
        allow_partial=allow_partial,
    )


def fake_downloader(mapping: dict[str, bytes]):
    def _download(url: str, timeout_seconds: int) -> bytes:
        if url not in mapping:
            raise urllib.error.URLError(f"boom {url}")
        return mapping[url]
    return _download


def features_by_station(output: dict) -> dict[str, dict]:
    result = {}
    for feature in output["features"]:
        props = feature["properties"]
        result[(props.get("operateur"), props.get("station_anfr"))] = feature
    return result


@pytest.fixture()
def patched_sources(monkeypatch):
    monkeypatch.setattr(bhs, "OPERATOR_SOURCES", TEST_SOURCES)


def test_end_to_end_operator_only(tmp_path, monkeypatch, patched_sources):
    db_path = tmp_path / "geotower.db"
    make_db(db_path)
    monkeypatch.setattr(
        bhs,
        "download",
        fake_downloader({
            "test://sfr": SFR_CSV.encode("utf-8"),
            "test://free": FREE_CSV.encode("utf-8"),
        }),
    )

    args = make_args(tmp_path, db=str(db_path))
    stats = bhs.build_sites_hs(args)

    output = json.loads(Path(args.output).read_text(encoding="utf-8"))
    assert output["type"] == "FeatureCollection"
    # 2 features SFR (dont 1 dedupliquee) + 1 feature Free.
    assert len(output["features"]) == 3
    assert stats["output_features"] == 3

    by_station = features_by_station(output)

    # 1) Station fournie par l'operateur.
    sfr_op = by_station[("SFR", "0751234567")]
    assert sfr_op["properties"]["source_match"] == "operator"
    assert sfr_op["properties"]["sources"] == ["sfr"]
    assert sfr_op["geometry"]["coordinates"] == [2.3522, 48.8566]
    # La fusion du doublon a elargi la fenetre de dates.
    assert sfr_op["properties"]["debut"] == "2025-12-15"
    assert sfr_op["properties"]["fin"] == "2026-07-15"

    # 2) Station recuperee depuis la base ANFR locale (geocodage).
    sfr_db = by_station[("SFR", "0759999999")]
    assert sfr_db["properties"]["source_match"] == "db_insee"
    assert "source_match_distance_m" in sfr_db["properties"]

    # 3) Free : pas de station, INSEE derive du code site, libelle uniformise.
    free_feature = next(f for f in output["features"] if f["properties"]["operateur"] == "Free Mobile")
    assert "station_anfr" not in free_feature["properties"]
    assert free_feature["properties"]["code_insee"] == "75056"
    assert free_feature["properties"]["code_postal"] == "75000"
    assert free_feature["properties"]["sources"] == ["free"]

    # Statistiques attendues.
    assert stats["operator_rows"] == {"sfr": 3, "free": 1}
    assert dict(stats["operator_station_from_operator"]) == {"sfr": 2}
    assert dict(stats["operator_station_from_db"]) == {"sfr": 1}
    assert dict(stats["operator_without_station"]) == {"free": 1}
    assert dict(stats["operator_duplicates_merged"]) == {"sfr": 1}
    assert stats["operator_download_errors"] == {}

    # Aucune trace d'ARCEP nulle part.
    assert "arcep" not in json.dumps(output).lower()

    # Date de source = aujourd'hui.
    import datetime as dt
    assert Path(args.date_output).read_text(encoding="utf-8").strip() == dt.date.today().isoformat()


def test_app_schema_fields_present(tmp_path, monkeypatch, patched_sources):
    make_db(tmp_path / "geotower.db")
    monkeypatch.setattr(
        bhs,
        "download",
        fake_downloader({"test://sfr": SFR_CSV.encode("utf-8"), "test://free": FREE_CSV.encode("utf-8")}),
    )
    args = make_args(tmp_path, db=str(tmp_path / "geotower.db"))
    bhs.build_sites_hs(args)
    output = json.loads(Path(args.output).read_text(encoding="utf-8"))

    # Champs consommes par l'app Android (AnfrRepository.getSitesHs()).
    props = features_by_station(output)[("SFR", "0751234567")]["properties"]
    for field in ("operateur", "station_anfr", "voix", "data", "voix2g", "raison", "debut", "fin", "commune"):
        assert field in props
    assert props["propre"] == 1  # "oui" -> 1


def test_all_downloads_fail_keeps_previous_output(tmp_path, monkeypatch, patched_sources):
    monkeypatch.setattr(bhs, "download", fake_downloader({}))  # tout echoue
    args = make_args(tmp_path, db=None)
    with pytest.raises(RuntimeError, match="Aucun fichier operateur accessible"):
        bhs.build_sites_hs(args)
    assert not Path(args.output).exists()  # sortie precedente jamais ecrasee


def test_partial_failure_without_flag_raises(tmp_path, monkeypatch, patched_sources):
    monkeypatch.setattr(bhs, "download", fake_downloader({"test://sfr": SFR_CSV.encode("utf-8")}))
    args = make_args(tmp_path, db=None, allow_partial=False)
    with pytest.raises(RuntimeError, match="inaccessible"):
        bhs.build_sites_hs(args)
    assert not Path(args.output).exists()


def test_partial_failure_with_flag_publishes(tmp_path, monkeypatch, patched_sources):
    monkeypatch.setattr(bhs, "download", fake_downloader({"test://sfr": SFR_CSV.encode("utf-8")}))
    args = make_args(tmp_path, db=None, allow_partial=True)
    stats = bhs.build_sites_hs(args)
    assert Path(args.output).exists()
    assert "free" in stats["operator_download_errors"]
    assert stats["operator_rows"] == {"sfr": 3}


def test_normalize_operator_row_requires_coordinates():
    row = {"code_site_op": "X1", "commune": "PARIS"}  # pas de lat/lon
    assert bhs.normalize_operator_row("sfr", row) is None


def test_free_insee_from_code_site():
    assert bhs.free_insee_from_code_site("75056_1") == "75056"
    assert bhs.free_insee_from_code_site("ABC_1") is None
    assert bhs.free_insee_from_code_site(None) is None


def test_parse_args_tolerates_deprecated_arcep_flags(tmp_path):
    # Un cron existant peut encore passer --raw-output / --arcep-* : on ne doit pas planter.
    args = bhs.parse_args([
        "--output", str(tmp_path / "o.geojson"),
        "--date-output", str(tmp_path / "d.txt"),
        "--raw-output", str(tmp_path / "raw.geojson"),
        "--arcep-fallback-days", "2",
        "--arcep-same-insee-match-m", "350",
        "--arcep-spatial-match-m", "100",
    ])
    assert args.output.endswith("o.geojson")
    assert args.allow_partial is False
    # Seuils de geocodage DB figes (coords operateurs decalees, cf. balayage 2026-07-13).
    assert args.db_same_insee_match_m == 600.0
    assert args.db_spatial_match_m == 400.0
