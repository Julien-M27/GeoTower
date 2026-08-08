"""Tests du telechargeur de sources ANFR mensuelles (fetch_anfr_sources.py).

Le reseau est simule (monkeypatch de `fetch_text` et `download_to_file`) :
aucun appel HTTP reel.
Lancer depuis docs/server :  python3 -m pytest test_fetch_anfr_sources.py
"""
from __future__ import annotations

import io
import json
import os
import sys
import zipfile
from pathlib import Path

import pytest

sys.path.insert(0, os.path.dirname(__file__))
import fetch_anfr_sources as fas  # noqa: E402


BASE = "https://object.files.data.gouv.fr/geotower/"


def resource(filename, modified="2026-08-01T00:00:00", fmt="zip", base=BASE):
    return {"url": base + filename, "format": fmt, "last_modified": modified}


def dataset(*resources):
    return json.dumps({"resources": list(resources)})


def monthly_zip_bytes(complete=True):
    buffer = io.BytesIO()
    names = list(fas.MONTHLY_ZIP_REQUIRED_FILES)
    if not complete:
        names = names[:2]
    with zipfile.ZipFile(buffer, "w") as archive:
        for name in names:
            archive.writestr(name, "sta_nm_anfr;valeur\n1000001;x\n")
    return buffer.getvalue()


def ref_zip_bytes():
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("SUP_NATURE.txt", "nat_id;nat_lb_nom\n1;Pylone\n")
    return buffer.getvalue()


@pytest.fixture
def workspace(tmp_path, monkeypatch):
    (tmp_path / "imports" / "france_sources").mkdir(parents=True)
    monkeypatch.setattr(fas, "MIN_FREE_BYTES", 0)
    return tmp_path


def install_network(monkeypatch, dataset_json, payloads):
    """Sert un dataset JSON et des octets par nom de fichier."""
    monkeypatch.setattr(fas, "fetch_text", lambda url, max_bytes: dataset_json)

    def fake_download(url, destination, max_bytes):
        name = url.rsplit("/", 1)[-1]
        payload = payloads[name]
        if len(payload) > max_bytes:
            raise fas.SourceError("trop volumineux")
        Path(destination).write_bytes(payload)
        return len(payload)

    monkeypatch.setattr(fas, "download_to_file", fake_download)


def run(workspace: Path, *extra):
    return fas.main(["--imports-dir", str(workspace / "imports"), *extra])


def deposited(workspace: Path):
    return sorted(
        path.name for path in (workspace / "imports" / "france_sources").glob("*.zip")
    )


# --- Selection --------------------------------------------------------------


def test_tri_sur_la_date_des_donnees_pas_sur_la_mise_en_ligne():
    """data.gouv republie parfois de vieux exports avec un last_modified recent."""
    payload = dataset(
        resource("20220131-export-etalab-data.zip", modified="2026-08-05T12:00:00"),
        resource("20260630-export-etalab-data.zip", modified="2026-07-01T09:00:00"),
    )
    data, _ = fas.select_monthly_zip_urls(payload)
    assert data["filename"] == "20260630-export-etalab-data.zip"


def test_ancien_format_de_date_jjmmaaaa():
    """31052018_... ne doit pas passer devant 20260630-... dans un tri de chaines."""
    assert fas.data_date_key("31052018_export_etalab_data.zip") == "20180531"
    payload = dataset(
        resource("31052018_export_etalab_data.zip"),
        resource("20260630-export-etalab-data.zip"),
    )
    data, _ = fas.select_monthly_zip_urls(payload)
    assert data["filename"] == "20260630-export-etalab-data.zip"


def test_references_du_meme_export_preferees():
    payload = dataset(
        resource("20260630-export-etalab-data.zip"),
        resource("20260630-export-etalab-ref.zip"),
        resource("20260531-export-etalab-ref.zip"),
    )
    data, reference = fas.select_monthly_zip_urls(payload)
    assert data["data_date"] == "20260630"
    assert reference["filename"] == "20260630-export-etalab-ref.zip"


def test_hote_non_autorise_ignore():
    payload = dataset(
        resource("20260630-export-etalab-data.zip", base="https://ailleurs.example/")
    )
    assert fas.select_monthly_zip_urls(payload) is None
    assert fas.is_allowed_url("http://www.data.gouv.fr/x.zip") is False


# --- Depot ------------------------------------------------------------------


def test_depose_les_deux_archives(workspace, monkeypatch):
    install_network(
        monkeypatch,
        dataset(
            resource("20260630-export-etalab-data.zip"),
            resource("20260630-export-etalab-ref.zip"),
        ),
        {
            "20260630-export-etalab-data.zip": monthly_zip_bytes(),
            "20260630-export-etalab-ref.zip": ref_zip_bytes(),
        },
    )

    assert run(workspace) == 0

    assert deposited(workspace) == [
        "20260630-export-etalab-data.zip",
        "20260630-export-etalab-ref.zip",
    ]


def test_idempotent(workspace, monkeypatch):
    payloads = {
        "20260630-export-etalab-data.zip": monthly_zip_bytes(),
        "20260630-export-etalab-ref.zip": ref_zip_bytes(),
    }
    install_network(
        monkeypatch,
        dataset(
            resource("20260630-export-etalab-data.zip"),
            resource("20260630-export-etalab-ref.zip"),
        ),
        payloads,
    )
    assert run(workspace) == 0

    appels = []
    original = fas.download_to_file

    def compteur(url, destination, max_bytes):
        appels.append(url)
        return original(url, destination, max_bytes)

    monkeypatch.setattr(fas, "download_to_file", compteur)
    assert run(workspace) == 0
    assert appels == [], "une archive deja presente ne doit pas etre retelechargee"


def test_archive_incomplete_refusee(workspace, monkeypatch):
    """Un ZIP sans les cinq tables SUP ne doit jamais atteindre le build."""
    install_network(
        monkeypatch,
        dataset(resource("20260630-export-etalab-data.zip")),
        {"20260630-export-etalab-data.zip": monthly_zip_bytes(complete=False)},
    )

    assert run(workspace) == 3

    assert deposited(workspace) == []
    residus = list((workspace / "imports" / "france_sources").glob("*.incoming"))
    assert residus == [], "le fichier de travail doit etre nettoye"


def test_dry_run_n_ecrit_rien(workspace, monkeypatch):
    install_network(
        monkeypatch,
        dataset(resource("20260630-export-etalab-data.zip")),
        {"20260630-export-etalab-data.zip": monthly_zip_bytes()},
    )

    assert run(workspace, "--dry-run") == 0

    assert deposited(workspace) == []


def test_skip_ref(workspace, monkeypatch):
    install_network(
        monkeypatch,
        dataset(
            resource("20260630-export-etalab-data.zip"),
            resource("20260630-export-etalab-ref.zip"),
        ),
        {
            "20260630-export-etalab-data.zip": monthly_zip_bytes(),
            "20260630-export-etalab-ref.zip": ref_zip_bytes(),
        },
    )

    assert run(workspace, "--skip-ref") == 0

    assert deposited(workspace) == ["20260630-export-etalab-data.zip"]


def test_espace_disque_insuffisant(workspace, monkeypatch):
    monkeypatch.setattr(fas, "MIN_FREE_BYTES", 1 << 60)
    install_network(
        monkeypatch,
        dataset(resource("20260630-export-etalab-data.zip")),
        {"20260630-export-etalab-data.zip": monthly_zip_bytes()},
    )

    assert run(workspace) == 3

    assert deposited(workspace) == []
