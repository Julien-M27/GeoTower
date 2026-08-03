#!/usr/bin/env python3
"""Build the optional GeoTower FR radio actors database."""

from __future__ import annotations

import json
import os
import tempfile
import zipfile
from pathlib import Path

from fr_radio_db_builder import (
    DEFAULT_ARCOM_RADIO_JSON,
    DEFAULT_DB_FILENAME,
    DEFAULT_VERSION_FILENAME,
    build_radio_db,
)


IMPORTS_DIR = Path(os.environ.get("GEOTOWER_IMPORTS_DIR", "/opt/geotower/data/imports"))
FRANCE_SOURCES_DIR_NAME = "france_sources"
REQUIRED_MONTHLY_FILES = {
    "SUP_STATION.txt",
    "SUP_SUPPORT.txt",
    "SUP_ANTENNE.txt",
    "SUP_EMETTEUR.txt",
    "SUP_BANDE.txt",
}
REQUIRED_REF_FILES = {
    "SUP_EXPLOITANT.txt",
    "SUP_NATURE.txt",
    "SUP_PROPRIETAIRE.txt",
    "SUP_TYPE_ANTENNE.txt",
}


def has_required_files(directory: Path, required: set[str]) -> bool:
    if not directory.is_dir():
        return False
    names = {path.name.upper() for path in directory.iterdir() if path.is_file()}
    return all(name.upper() in names for name in required)


def zip_has_required_files(path: Path, required: set[str]) -> bool:
    try:
        with zipfile.ZipFile(path, "r") as archive:
            names = {Path(name).name.upper() for name in archive.namelist()}
        return all(name.upper() in names for name in required)
    except (OSError, zipfile.BadZipFile):
        return False


def source_roots() -> list[tuple[Path, bool]]:
    return [
        (IMPORTS_DIR / FRANCE_SOURCES_DIR_NAME, True),
        (IMPORTS_DIR, False),
    ]


def latest_source(required: set[str], marker: str) -> Path | None:
    candidates: list[Path] = []
    seen: set[Path] = set()
    for root, recursive in source_roots():
        if not root.is_dir():
            continue

        dir_iterator = root.rglob("*") if recursive else root.iterdir()
        for path in dir_iterator:
            if not path.is_dir():
                continue
            if path in seen:
                continue
            seen.add(path)
            if marker in path.name.lower() and has_required_files(path, required):
                candidates.append(path)

        zip_iterator = root.rglob("*.zip") if recursive else root.glob("*.zip")
        for path in zip_iterator:
            if path in seen:
                continue
            seen.add(path)
            if marker in path.name.lower() and zip_has_required_files(path, required):
                candidates.append(path)

    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime)


def latest_optional_json(name: str) -> Path | None:
    candidates: list[Path] = []
    for root, recursive in source_roots():
        if not root.is_dir():
            continue
        iterator = root.rglob(name) if recursive else root.glob(name)
        candidates.extend(path for path in iterator if path.is_file())
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime)


def extract_required_files(source: Path, required: set[str], destination: Path) -> Path:
    if source.is_dir():
        return source

    destination.mkdir(parents=True, exist_ok=True)
    wanted = {name.upper() for name in required}
    with zipfile.ZipFile(source, "r") as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            name = Path(info.filename).name
            if name.upper() not in wanted:
                continue
            with archive.open(info, "r") as raw:
                (destination / name).write_bytes(raw.read())
    if not has_required_files(destination, required):
        raise RuntimeError(f"Archive incomplete: {source}")
    return destination


def run_build() -> dict[str, object]:
    data_source = latest_source(REQUIRED_MONTHLY_FILES, "data")
    if data_source is None:
        raise RuntimeError(f"Aucune source mensuelle ANFR trouvee dans {IMPORTS_DIR}")

    ref_source = latest_source(REQUIRED_REF_FILES, "ref")
    if ref_source is None:
        raise RuntimeError(f"Aucune source de references ANFR trouvee dans {IMPORTS_DIR}")

    output = IMPORTS_DIR / DEFAULT_DB_FILENAME
    version_json = IMPORTS_DIR / DEFAULT_VERSION_FILENAME
    arcom_radio_json = latest_optional_json(DEFAULT_ARCOM_RADIO_JSON) or latest_optional_json(
        "arcom_radio_services*.json"
    )

    with tempfile.TemporaryDirectory(prefix="geotower_radio_build_") as tmp:
        tmp_root = Path(tmp)
        data_dir = extract_required_files(data_source, REQUIRED_MONTHLY_FILES, tmp_root / "data")
        ref_dir = extract_required_files(ref_source, REQUIRED_REF_FILES, tmp_root / "ref")
        report = build_radio_db(
            data_dir=data_dir,
            ref_dir=ref_dir,
            output=output,
            version_json=version_json,
            source_path=data_source,
            arcom_radio_json=arcom_radio_json,
            write_report_file=True,
        )
        report["data_source"] = str(data_source)
        report["ref_source"] = str(ref_source)
        report["arcom_radio_source"] = str(arcom_radio_json) if arcom_radio_json else None
        return report


def main() -> None:
    report = run_build()
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
