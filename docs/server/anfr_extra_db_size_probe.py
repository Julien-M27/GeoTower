#!/usr/bin/env python3
"""Generate a local size-probe DB for the optional ANFR radio layer."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from fr_radio_db_builder import build_radio_db


DEFAULT_DATA_DIR = Path(
    r"D:\Documents_HDD\ANFR\Import Mensuel\20260228-export-etalab-data"
)
DEFAULT_REF_DIR = Path(
    r"D:\Documents_HDD\ANFR\Import Mensuel\20260531-export-etalab-ref"
)
DEFAULT_OUTPUT = Path(
    r"build\tmp\anfr_extra_db_size_probe\geotower_fr_extra_non_mobile_test.db"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument("--ref-dir", type=Path, default=DEFAULT_REF_DIR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--version-json", type=Path, default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    report = build_radio_db(
        data_dir=args.data_dir,
        ref_dir=args.ref_dir,
        output=args.output,
        version_json=args.version_json,
        write_report_file=True,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
