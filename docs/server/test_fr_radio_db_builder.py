#!/usr/bin/env python3

import json
import shutil
import sys
import unittest
import uuid
from contextlib import contextmanager
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))

from fr_radio_db_builder import (  # noqa: E402
    SERVICE_BROADCAST,
    SYSTEM_BITS,
    SiteAggregate,
    SupportInfo,
    build_arcom_program_matches,
    build_radio_db,
)


class FrRadioDbBuilderTest(unittest.TestCase):
    def test_arcom_match_prefers_frequency_over_nearest_support(self) -> None:
        with self._temporary_directory() as tmp:
            arcom_json = Path(tmp) / "arcom.json"
            arcom_json.write_text(
                json.dumps(
                    [
                        {
                            "service_name": "Cherie FM",
                            "mode": "FM",
                            "frequency_label": "101.0 MHz",
                            "latitude_e6": 48_000_000,
                            "longitude_e6": 2_000_000,
                        }
                    ]
                ),
                encoding="utf-8",
            )

            matching = self._aggregate("STA", "MATCH", 101_000)
            nearest = self._aggregate("STA", "NEAR", 102_000)
            aggregates = {
                ("STA", "MATCH"): matching,
                ("STA", "NEAR"): nearest,
            }
            supports = {
                ("STA", "MATCH"): self._support("STA", "MATCH", 48_000_100, 2_000_000),
                ("STA", "NEAR"): self._support("STA", "NEAR", 48_000_000, 2_000_000),
            }

            programs, stats = build_arcom_program_matches(arcom_json, aggregates, supports)

            self.assertIn(("STA", "MATCH"), programs)
            self.assertNotIn(("STA", "NEAR"), programs)
            self.assertEqual(1, stats["arcom_entries_matched_by_frequency"])
            self.assertEqual(0, stats["arcom_entries_unmatched_frequency"])

    def test_arcom_match_rejects_known_frequency_when_anfr_bands_disagree(self) -> None:
        with self._temporary_directory() as tmp:
            arcom_json = Path(tmp) / "arcom.json"
            arcom_json.write_text(
                json.dumps(
                    [
                        {
                            "service_name": "Alouette",
                            "mode": "FM",
                            "frequency_label": "101.0 MHz",
                            "latitude_e6": 48_000_000,
                            "longitude_e6": 2_000_000,
                        }
                    ]
                ),
                encoding="utf-8",
            )

            aggregate = self._aggregate("STA", "SUP", 102_000)
            programs, stats = build_arcom_program_matches(
                arcom_json,
                {("STA", "SUP"): aggregate},
                {("STA", "SUP"): self._support("STA", "SUP", 48_000_000, 2_000_000)},
            )

            self.assertEqual({}, programs)
            self.assertEqual(0, stats["arcom_entries_matched"])
            self.assertEqual(1, stats["arcom_entries_unmatched_frequency"])

    def test_build_radio_db_skips_emitter_without_antenna_on_multi_support_station(self) -> None:
        with self._temporary_directory() as tmp:
            data_dir = Path(tmp) / "data"
            data_dir.mkdir()
            self._write_minimal_anfr_files(
                data_dir,
                supports=[
                    {"SUP_ID": "SUP1", "COR_NB_DG_LAT": "48", "COR_NB_DG_LON": "2"},
                    {"SUP_ID": "SUP2", "COR_NB_DG_LAT": "48.001", "COR_NB_DG_LON": "2"},
                ],
                emitters=[{"EMR_ID": "EMR1", "AER_ID": "", "EMR_LB_SYSTEME": "FM"}],
            )

            report = build_radio_db(
                data_dir,
                None,
                Path(tmp) / "radio.db",
                version="test",
                write_report_file=False,
            )

            self.assertEqual(0, report["non_mobile_emitters_kept"])
            self.assertEqual(1, report["emitters_skipped_ambiguous_support"])
            self.assertEqual(0, report["selected_emr_ids"])

    def test_build_radio_db_keeps_emitter_without_antenna_on_single_support_station(self) -> None:
        with self._temporary_directory() as tmp:
            data_dir = Path(tmp) / "data"
            data_dir.mkdir()
            self._write_minimal_anfr_files(
                data_dir,
                supports=[{"SUP_ID": "SUP1", "COR_NB_DG_LAT": "48", "COR_NB_DG_LON": "2"}],
                emitters=[{"EMR_ID": "EMR1", "AER_ID": "", "EMR_LB_SYSTEME": "FM"}],
            )

            report = build_radio_db(
                data_dir,
                None,
                Path(tmp) / "radio.db",
                version="test",
                write_report_file=False,
            )

            self.assertEqual(1, report["non_mobile_emitters_kept"])
            self.assertEqual(0, report["emitters_skipped_ambiguous_support"])
            self.assertEqual(1, report["selected_emr_ids"])

    def _aggregate(self, sta: str, sup: str, frequency_khz: int) -> SiteAggregate:
        aggregate = SiteAggregate(
            sta=sta,
            sup=sup,
            adm_id=1,
            service_mask=SERVICE_BROADCAST,
            system_mask=SYSTEM_BITS["FM"],
        )
        aggregate.add_freq_range(frequency_khz, frequency_khz, f"{frequency_khz / 1000:.1f} MHz")
        return aggregate

    def _support(self, sta: str, sup: str, lat_e6: int, lon_e6: int) -> SupportInfo:
        return SupportInfo(
            sta=sta,
            sup=sup,
            lat_e6=lat_e6,
            lon_e6=lon_e6,
            nat_id=None,
            tpo_id=None,
            height_dm=None,
            code_insee=None,
            address=None,
        )

    def _write_minimal_anfr_files(
        self,
        data_dir: Path,
        *,
        supports: list[dict[str, str]],
        emitters: list[dict[str, str]],
    ) -> None:
        self._write_csv(data_dir / "SUP_STATION.txt", ["STA_NM_ANFR", "ADM_ID"], [{"STA_NM_ANFR": "123", "ADM_ID": "1"}])
        self._write_csv(
            data_dir / "SUP_SUPPORT.txt",
            [
                "STA_NM_ANFR",
                "SUP_ID",
                "COR_NB_DG_LAT",
                "COR_NB_MN_LAT",
                "COR_NB_SC_LAT",
                "COR_CD_NS_LAT",
                "COR_NB_DG_LON",
                "COR_NB_MN_LON",
                "COR_NB_SC_LON",
                "COR_CD_EW_LON",
                "ADR_LB_LIEU",
                "ADR_LB_ADD1",
                "ADR_LB_ADD2",
                "ADR_LB_ADD3",
                "ADR_NM_CP",
                "NAT_ID",
                "TPO_ID",
                "SUP_NM_HAUT",
                "COM_CD_INSEE",
            ],
            [
                {
                    "STA_NM_ANFR": "123",
                    "COR_NB_MN_LAT": "0",
                    "COR_NB_SC_LAT": "0",
                    "COR_CD_NS_LAT": "N",
                    "COR_NB_MN_LON": "0",
                    "COR_NB_SC_LON": "0",
                    "COR_CD_EW_LON": "E",
                    **support,
                }
                for support in supports
            ],
        )
        self._write_csv(
            data_dir / "SUP_ANTENNE.txt",
            ["STA_NM_ANFR", "AER_ID", "SUP_ID", "TAE_ID", "AER_NB_AZIMUT", "AER_NB_ALT_BAS"],
            [],
        )
        self._write_csv(
            data_dir / "SUP_EMETTEUR.txt",
            ["STA_NM_ANFR", "EMR_ID", "AER_ID", "EMR_LB_SYSTEME"],
            [{"STA_NM_ANFR": "123", **emitter} for emitter in emitters],
        )
        self._write_csv(
            data_dir / "SUP_BANDE.txt",
            ["EMR_ID", "BAN_NB_F_DEB", "BAN_NB_F_FIN", "BAN_FG_UNITE"],
            [{"EMR_ID": emitter["EMR_ID"], "BAN_NB_F_DEB": "101", "BAN_NB_F_FIN": "101", "BAN_FG_UNITE": "M"} for emitter in emitters],
        )

    def _write_csv(self, path: Path, headers: list[str], rows: list[dict[str, str]]) -> None:
        lines = [";".join(headers)]
        for row in rows:
            lines.append(";".join(row.get(header, "") for header in headers))
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    @contextmanager
    def _temporary_directory(self):
        temp_root = Path(__file__).resolve().parents[2] / "build" / "test-tmp"
        temp_root.mkdir(parents=True, exist_ok=True)
        path = temp_root / f"fr-radio-{uuid.uuid4().hex}"
        path.mkdir()
        try:
            yield path
        finally:
            shutil.rmtree(path, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
