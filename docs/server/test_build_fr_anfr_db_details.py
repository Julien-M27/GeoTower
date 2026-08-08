"""`details_frequences` : fusion des deux sources ANFR.

Le ZIP mensuel donne les lignes completes (bandes + antenne) mais a jusqu'a cinq semaines de
retard sur le CSV hebdomadaire. Un systeme declare entre les deux publications allumait deja
`tech_mask` — donc le bandeau « 5G - 4G » de la fiche — sans avoir la moindre ligne d'emetteur.
Ces tests fixent le comportement de completion et, surtout, l'absence de doublon.
"""

import base64
import unittest
import zlib

import build_fr_anfr_db as builder


def decode(value):
    """Inverse de `compress_frequency_details` (le blob n'est compresse que s'il y gagne)."""
    if value is None:
        return None
    if not value.startswith("Z1:"):
        return value
    return zlib.decompress(base64.b64decode(value[3:])).decode("utf-8")


class BuildFrequencyDetailsTest(unittest.TestCase):
    def setUp(self):
        self.statut_ids = builder.IdRegistry()
        self.statut_ids.get_id("Inconnu")
        self.en_service = self.statut_ids.get_id("En service")
        # Statut REEL de l'observatoire, accent compris : l'app ne reconnait « Projet approuvé »
        # qu'avec (`classifyFrequencyStatus`), et clean_text ne touche pas aux accents.
        self.approuve = self.statut_ids.get_id("Projet approuvé")

    def station(self, sys_status_map):
        return {"statut_id": self.en_service, "sys_status_map": sys_status_map}

    def emetteur_lte800(self):
        return {
            "emr_id": "E1",
            "aer_id": "A1",
            "sys": "LTE 800",
            "statut": "En service",
            "date_info": "25/03/2019",
        }

    def build(self, station, raw_emetteurs, emr_bands=None, physique=None):
        blob, annonces = builder.build_frequency_details_for_station(
            "0122290040",
            raw_emetteurs,
            emr_bands if emr_bands is not None else {"E1": [(852.0, 862.0, "M", "852", "862")]},
            physique if physique is not None else {"A1": "Panneau : 185° (28.7m) [AER_ID: A1]"},
            station,
            self.statut_ids,
        )
        return decode(blob), annonces

    def test_adds_a_line_for_a_system_the_monthly_zip_does_not_know_yet(self):
        # Cas reel du support 758790 : la 5G est annoncee, le ZIP du 30/06 ne la porte pas encore.
        details, annonces = self.build(
            self.station(
                {
                    "LTE 800": (self.en_service, "25/03/2019", "LTE 800"),
                    "5G NR 2100": (self.approuve, None, "5G NR 2100"),
                }
            ),
            {"0122290040": [self.emetteur_lte800()]},
        )

        self.assertEqual(1, annonces)
        self.assertEqual(
            "5G NR 2100 :  | Projet approuvé |  | Azimut non specifie\n"
            "LTE 800 : 852-862 MHz | En service | 25/03/2019 | Panneau : 185° (28.7m) [AER_ID: A1]",
            details,
        )

    def test_does_not_duplicate_a_system_both_sources_know(self):
        details, annonces = self.build(
            self.station({"LTE 800": (self.en_service, "25/03/2019", "LTE 800")}),
            {"0122290040": [self.emetteur_lte800()]},
        )

        self.assertEqual(0, annonces)
        self.assertEqual(
            "LTE 800 : 852-862 MHz | En service | 25/03/2019 | Panneau : 185° (28.7m) [AER_ID: A1]",
            details,
        )

    def test_compares_system_labels_without_case_sensitivity(self):
        # Le ZIP et l'observatoire n'ecrivent pas toujours la casse pareil : c'est ce rapprochement
        # qui evite de fabriquer un doublon a la place d'un complement.
        emetteur = self.emetteur_lte800()
        emetteur["sys"] = "Lte 800"
        details, annonces = self.build(
            self.station({"LTE 800": (self.en_service, "25/03/2019", "LTE 800")}),
            {"0122290040": [emetteur]},
        )

        self.assertEqual(0, annonces)
        self.assertEqual(
            "Lte 800 : 852-862 MHz | En service | 25/03/2019 | Panneau : 185° (28.7m) [AER_ID: A1]",
            details,
        )

    def test_builds_details_for_a_station_the_monthly_zip_ignores_entirely(self):
        # Site tout juste declare : aucune ligne d'emetteur, la fiche restait vide de bout en bout.
        details, annonces = self.build(
            self.station(
                {
                    "LTE 700": (self.approuve, None, "LTE 700"),
                    "5G NR 2100": (self.approuve, None, "5G NR 2100"),
                }
            ),
            {},
        )

        self.assertEqual(2, annonces)
        self.assertEqual(
            "5G NR 2100 :  | Projet approuvé |  | Azimut non specifie\n"
            "LTE 700 :  | Projet approuvé |  | Azimut non specifie",
            details,
        )

    def test_keeps_the_date_the_weekly_csv_carries(self):
        details, _ = self.build(
            self.station({"UMTS 900": (self.en_service, "26/12/2018", "UMTS 900")}),
            {},
        )

        self.assertEqual(
            "UMTS 900 :  | En service | 26/12/2018 | Azimut non specifie",
            details,
        )

    def test_returns_none_for_a_station_without_any_system(self):
        details, annonces = self.build(self.station({}), {})

        self.assertEqual(0, annonces)
        self.assertIsNone(details)


if __name__ == "__main__":
    unittest.main()
