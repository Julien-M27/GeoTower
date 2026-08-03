"""Tests du builder eNB/gNB (docs/server/build_fr_enb_db.py).

Le reseau est simule (monkeypatch de `download`) : aucun appel HTTP reel.
Lancer depuis docs/server :  python -m pytest test_build_fr_enb_db.py
"""
from __future__ import annotations

import io
import json
import os
import sqlite3
import sys
import zipfile
from pathlib import Path

import pytest

sys.path.insert(0, os.path.dirname(__file__))
import build_fr_enb_db as enb  # noqa: E402


TEST_SOURCES = {
    "orange": {"label": "Orange", "plmn": "20801", "mnc": 1, "mncs": (1, 2), "db_operator_like": "%ORANGE%"},
    "sfr": {"label": "SFR", "plmn": "20810", "mnc": 10, "mncs": (10, 13), "db_operator_like": "%SFR%"},
}
FREE_SOURCE = {"label": "Free Mobile", "plmn": "20815", "mnc": 15, "mncs": (15, 16), "db_operator_like": "%FREE%"}


def ntm_row(mnc, enb_id, support, techno="4G", lat=48.8566, lon=2.3522, address="1 r de Paris 75001 PARIS"):
    return f"{techno};208;{mnc};-1;14147;{enb_id};{support};{lat};{lon};{address}"


def ntm_file(rows, source_date="24/07/2026"):
    """Fichier .ntm complet : ligne sentinelle de version + lignes de cellules."""
    lines = [f"4G;999;99;0;0000;0;-1;0.0;0.0;19/08/2022 / {source_date}", *rows]
    return ("\n".join(lines) + "\n").encode("utf-8")


def bulk_rows(mnc, count, first_enb=1000, support_base=600000):
    return [ntm_row(mnc, first_enb + index, support_base + index) for index in range(count)]


def zipped(payload: bytes, name: str = "NmEa.ntm") -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(name, payload)
    return buffer.getvalue()


def fake_network(monkeypatch, payloads: dict[str, bytes | None]):
    """payloads : PLMN -> contenu .ntm, ou None pour simuler une source injoignable."""

    def _download(url, timeout_seconds):
        for plmn, payload in payloads.items():
            if plmn in url:
                if payload is None:
                    raise OSError("connexion refusee")
                return zipped(payload)
        raise OSError(f"404 {url}")

    monkeypatch.setattr(enb, "download", _download)


def build(tmp_path: Path, *extra: str):
    args = enb.parse_args(
        ["--output", str(tmp_path / "geotower_fr_enb.db"), "--min-rows", "1", *extra]
    )
    return enb.build_enb_db(args)


def query(db_path: Path, sql: str, params=()):
    conn = sqlite3.connect(str(db_path))
    try:
        return conn.execute(sql, params).fetchall()
    finally:
        conn.close()


# --- Parsing ---------------------------------------------------------------


def test_parse_ntm_keeps_expected_fields():
    raw = ntm_file(
        [
            ntm_row(1, 19, 688435),
            ntm_row(1, 20, -1, address="Hall Maine (Gare Montparnasse) 75015 PARIS"),
            ntm_row(1, 19, 688436),  # doublon (meme techno + eNB) -> ignore
            ntm_row(1, 21, 688437, techno="5G"),
            ntm_row(1, 22, 688438, techno="3G"),  # techno non geree
            ntm_row(1, 0, 688439),  # eNB invalide
            ntm_row(1, 23, 688440, lat=0.0, lon=0.0),  # position sentinelle
        ]
    )

    parsed = enb.parse_ntm(raw, TEST_SOURCES["orange"])
    rows = {(row[1], row[2]): row for row in parsed["rows"]}

    # La ligne sentinelle 999/99 porte la date des donnees, pas une cellule.
    assert parsed["source_date"] == "2026-07-24"
    assert set(rows) == {(4, 19), (4, 20), (5, 21)}
    assert rows[(4, 19)][3] == 688435
    assert rows[(4, 19)][4] == 48856600  # lat_e6
    assert rows[(4, 19)][5] == 2352200  # lon_e6
    # L'adresse n'est stockee que pour les eNB sans support.
    assert rows[(4, 19)][6] is None
    assert rows[(4, 20)][3] is None
    assert rows[(4, 20)][6].startswith("Hall Maine")
    assert parsed["stats"]["duplicates"] == 1
    assert parsed["stats"]["orphans"] == 1
    assert parsed["stats"]["skipped_techno"] == 1
    assert parsed["stats"]["skipped_enb"] == 1
    assert parsed["stats"]["skipped_coords"] == 1


def test_parse_ntm_keeps_address_containing_semicolon():
    raw = ntm_file([ntm_row(1, 19, -1, address="Zone A; batiment B 75001 PARIS")])
    parsed = enb.parse_ntm(raw, TEST_SOURCES["orange"])
    assert parsed["rows"][0][6] == "Zone A; batiment B 75001 PARIS"


def test_parse_ntm_rejects_file_of_another_operator():
    raw = ntm_file(bulk_rows(10, 100))
    with pytest.raises(enb.SourceFormatError):
        enb.parse_ntm(raw, TEST_SOURCES["orange"])


def test_parse_ntm_keeps_secondary_mnc_and_drops_foreign_operators():
    """Cas reel du fichier Free (2026-07-27) : 208-16 legitime, MNC -1 et MNC d'autrui a jeter."""
    raw = ntm_file(
        bulk_rows(15, 600)
        + bulk_rows(16, 40, first_enb=8000)  # second code reseau de Free : a garder
        + bulk_rows(-1, 30, first_enb=9000)  # operateur inconnu : a jeter
        + bulk_rows(1, 20, first_enb=9500)  # Orange dans le fichier Free : a jeter
    )
    parsed = enb.parse_ntm(raw, FREE_SOURCE)

    assert parsed["stats"]["kept"] == 640
    assert parsed["stats"]["primary_mnc_rows"] == 600
    assert parsed["stats"]["secondary_mnc_rows"] == 40
    assert parsed["stats"]["skipped_other_operator"] == 50
    # Le vrai MNC de la ligne est conserve, pas celui du nom de fichier.
    assert {row[0] for row in parsed["rows"]} == {15, 16}


def test_parse_ntm_rejects_file_where_primary_mnc_is_minority():
    raw = ntm_file(bulk_rows(15, 100) + bulk_rows(16, 400, first_enb=8000))
    with pytest.raises(enb.SourceFormatError, match="minoritaire"):
        enb.parse_ntm(raw, FREE_SOURCE)


def test_parse_ntm_rejects_foreign_mcc_in_volume():
    lines = ntm_file(bulk_rows(1, 600)).decode("utf-8").splitlines()
    lines += [line.replace("4G;208;", "4G;340;", 1) for line in lines[1:51]]
    with pytest.raises(enb.SourceFormatError, match="MCC"):
        enb.parse_ntm(("\n".join(lines) + "\n").encode("utf-8"), TEST_SOURCES["orange"])


def test_extract_ntm_rejects_ambiguous_archive():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("a.ntm", b"x")
        archive.writestr("b.ntm", b"y")
    with pytest.raises(enb.SourceFormatError):
        enb.extract_ntm(buffer.getvalue())


# --- Build -----------------------------------------------------------------


def test_build_writes_database_and_version_json(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    fake_network(
        monkeypatch,
        {
            "20801": ntm_file([ntm_row(1, 19, 688435), ntm_row(1, 20, -1)], source_date="24/07/2026"),
            "20810": ntm_file(bulk_rows(10, 5), source_date="27/07/2026"),
        },
    )

    report = build(tmp_path)
    db_path = tmp_path / "geotower_fr_enb.db"

    assert report["row_count"] == 7
    assert report["orphan_rows"] == 1
    assert db_path.is_file()
    assert query(db_path, "SELECT COUNT(*) FROM enb_cell")[0][0] == 7
    assert query(db_path, "SELECT COUNT(*) FROM enb_source")[0][0] == 2
    sources = query(db_path, "SELECT plmn, mnc, mnc_list, row_count FROM enb_source ORDER BY plmn")
    assert sources == [("20801", 1, "1", 2), ("20810", 10, "10", 5)]
    assert query(
        db_path, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_enb_cell_support'"
    )[0][0] == 1

    metadata = query(db_path, "SELECT version, schema_version, country_code, source, source_date FROM metadata")[0]
    assert metadata[1] == enb.SCHEMA_VERSION
    assert metadata[2] == "FR"
    assert metadata[3] == "ENB_ANALYTICS"
    # La date de donnees retenue est la plus recente des operateurs.
    assert metadata[4] == "2026-07-27"
    assert metadata[0].startswith("2026-07-27-")

    payload = json.loads((tmp_path / "version_fr_enb.json").read_text(encoding="utf-8"))
    assert payload["version"] == metadata[0]
    assert payload["row_count"] == 7
    assert payload["size_bytes"] == db_path.stat().st_size
    assert len(payload["sha256"]) == 64
    assert {entry["plmn"] for entry in payload["operators"]} == {"20801", "20810"}


def test_build_falls_back_to_raw_cache(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    payloads = {"20801": ntm_file(bulk_rows(1, 40)), "20810": ntm_file(bulk_rows(10, 20))}
    fake_network(monkeypatch, payloads)
    build(tmp_path)

    # Orange devient injoignable : on doit repartir de son cache brut, pas le perdre.
    fake_network(monkeypatch, {"20801": None, "20810": payloads["20810"]})
    report = build(tmp_path)

    assert report["row_count"] == 60
    assert report["operators"]["orange"]["from_cache"] is True
    assert report["operators"]["sfr"]["from_cache"] is False
    assert "orange" in report["errors"]
    assert report["missing_operators"] == []


def test_build_offline_uses_cache_without_network(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    fake_network(monkeypatch, {"20801": ntm_file(bulk_rows(1, 40)), "20810": ntm_file(bulk_rows(10, 20))})
    build(tmp_path)

    def _forbidden(url, timeout_seconds):
        raise AssertionError("--offline ne doit declencher aucun telechargement")

    monkeypatch.setattr(enb, "download", _forbidden)
    report = build(tmp_path, "--offline")

    assert report["row_count"] == 60
    assert all(entry["from_cache"] for entry in report["operators"].values())


def test_build_rejects_truncated_source_and_keeps_cache(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    payloads = {"20801": ntm_file(bulk_rows(1, 600)), "20810": ntm_file(bulk_rows(10, 600))}
    fake_network(monkeypatch, payloads)
    build(tmp_path)

    # Orange renvoie un fichier ampute (10 lignes contre 600) : sous le plancher relatif,
    # on garde le cache plutot que de publier une base trouee.
    fake_network(monkeypatch, {"20801": ntm_file(bulk_rows(1, 10)), "20810": payloads["20810"]})
    report = build(tmp_path)

    assert report["operators"]["orange"]["from_cache"] is True
    assert report["operators"]["orange"]["kept"] == 600
    assert report["row_count"] == 1200


def test_build_refuses_partial_output_without_flag(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    fake_network(monkeypatch, {"20801": None, "20810": ntm_file(bulk_rows(10, 20))})

    with pytest.raises(RuntimeError, match="manquant"):
        build(tmp_path)
    assert not (tmp_path / "geotower_fr_enb.db").exists()

    report = build(tmp_path, "--allow-partial")
    assert report["missing_operators"] == ["orange"]
    assert report["row_count"] == 20


def test_build_refuses_volume_collapse(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    payloads = {"20801": ntm_file(bulk_rows(1, 900)), "20810": ntm_file(bulk_rows(10, 300))}
    fake_network(monkeypatch, payloads)
    build(tmp_path)

    # Orange disparait (source ET cache) : 300 lignes contre 1200, on n'ecrase pas.
    (tmp_path / enb.CACHE_DIR_NAME / "NmEa_20801.ntm").unlink()
    fake_network(monkeypatch, {"20801": None, "20810": payloads["20810"]})

    with pytest.raises(RuntimeError, match="Effondrement"):
        build(tmp_path, "--allow-partial")
    assert query(tmp_path / "geotower_fr_enb.db", "SELECT COUNT(*) FROM enb_cell")[0][0] == 1200

    report = build(tmp_path, "--allow-partial", "--allow-shrink")
    assert report["row_count"] == 300


def test_version_changes_when_an_older_source_moves(tmp_path, monkeypatch):
    """Le digest de version protege du cas ou la date maximale ne bouge pas."""
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    orange_v1 = ntm_file(bulk_rows(1, 40), source_date="01/01/2026")
    sfr = ntm_file(bulk_rows(10, 20), source_date="27/07/2026")

    fake_network(monkeypatch, {"20801": orange_v1, "20810": sfr})
    first = build(tmp_path)["version"]

    fake_network(monkeypatch, {"20801": orange_v1, "20810": sfr})
    assert build(tmp_path)["version"] == first  # sources inchangees -> version stable

    # Orange bouge, mais sa date reste anterieure a celle de SFR : sans digest, la version
    # ne changerait pas et les clients resteraient sur une base perimee.
    fake_network(monkeypatch, {"20801": ntm_file(bulk_rows(1, 41), source_date="02/01/2026"), "20810": sfr})
    second = build(tmp_path)["version"]
    assert second != first
    assert second.startswith("2026-07-27-")


# --- Canari de jointure ----------------------------------------------------


def make_anfr_db(path: Path) -> None:
    conn = sqlite3.connect(str(path))
    try:
        conn.execute("CREATE TABLE ref_operateur (id INTEGER PRIMARY KEY, libelle TEXT)")
        conn.execute("CREATE TABLE localisation (id_anfr TEXT, operateur_id INTEGER)")
        conn.execute("CREATE TABLE support (id_anfr TEXT, id_support TEXT)")
        conn.execute("INSERT INTO ref_operateur (id, libelle) VALUES (1, 'ORANGE'), (2, 'SFR')")
        conn.execute("INSERT INTO localisation (id_anfr, operateur_id) VALUES ('0750001', 1), ('0750002', 2)")
        # Support mutualise : les deux operateurs sont sur le pylone 688435.
        conn.execute(
            "INSERT INTO support (id_anfr, id_support) VALUES ('0750001', '688435'), ('0750002', '688435')"
        )
        conn.commit()
    finally:
        conn.close()


def test_join_quality_uses_the_operator_filter(tmp_path, monkeypatch):
    monkeypatch.setattr(enb, "OPERATOR_SOURCES", TEST_SOURCES)
    db_path = tmp_path / "geotower_fr.db"
    make_anfr_db(db_path)

    rows = [
        (1, 4, 19, 688435, 48856600, 2352200, None),  # Orange, support connu
        (1, 4, 20, 999999, 48856600, 2352200, None),  # Orange, support inconnu
        (1, 4, 21, None, 48856600, 2352200, "Indoor"),  # orphelin : hors du calcul
        (10, 4, 22, 688435, 48856600, 2352200, None),  # SFR sur le meme pylone mutualise
    ]

    quality = enb.join_quality(db_path, rows)
    assert quality["orange"]["attached_rows"] == 2
    assert quality["orange"]["matched_rows"] == 1
    assert quality["orange"]["matched_ratio"] == 0.5
    assert quality["sfr"]["matched_ratio"] == 1.0
