"""Tests du diff hebdomadaire de l'observatoire ANFR (build_site_changes.py).

Aucun appel reseau : les tests s'executent avec --no-download et des CSV en dur.
Lancer depuis docs/server :  python3 -m pytest test_build_site_changes.py
"""
from __future__ import annotations

import gzip
import json
import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, os.path.dirname(__file__))
import build_site_changes as bsc  # noqa: E402


# En-tete reel de la publication du 2026-08-06.
HEADER = (
    "id;adm_lb_nom;sup_id;emr_lb_systeme;emr_dt;sta_nm_dpt;code_insee;generation;"
    "date_maj;sta_nm_anfr;nat_id;sup_nm_haut;tpo_id;adr_lb_lieu;adr_lb_add1;"
    "adr_lb_add2;adr_lb_add3;adr_nm_cp;com_cd_insee;coordonnees;coord;statut"
)
COLUMNS = HEADER.split(";")

DEFAULTS = {
    "id": "1",
    "adm_lb_nom": "ORANGE",
    "sup_id": "SUP-1",
    "emr_lb_systeme": "LTE 800",
    "sta_nm_dpt": "75",
    "code_insee": "75101",
    "generation": "4G",
    "date_maj": "2026-08-06",
    "sta_nm_anfr": "1000001",
    "sup_nm_haut": "25",
    "adr_lb_lieu": "1 rue de Paris",
    "coordonnees": "48.856600, 2.352200",
    "statut": "En service",
}


def make_row(**values):
    row = {name: "" for name in COLUMNS}
    row.update(DEFAULTS)
    row.update(values)
    return ";".join(row[name] for name in COLUMNS)


def write_csv(path: Path, rows, encoding="utf-8-sig", header=HEADER):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = header + "\n" + "\n".join(rows) + "\n"
    path.write_bytes(text.encode(encoding))


@pytest.fixture
def workspace(tmp_path, monkeypatch):
    """Arborescence de test + garde-fous neutralises (les CSV font 3 lignes)."""
    monkeypatch.setattr(bsc, "MIN_ROWS", 0)
    monkeypatch.setattr(bsc, "MAX_STATION_LOSS_RATIO", 1.0)
    (tmp_path / "imports" / "france_sources").mkdir(parents=True)
    (tmp_path / "history").mkdir()
    return tmp_path


def publish(workspace: Path, data_date: str, rows, **kwargs):
    """Depose une publication comme le fait l'ANFR, avec son nom date."""
    name = f"{data_date}120000_observatoireod_{data_date}.csv"
    write_csv(workspace / "imports" / "france_sources" / name, rows, **kwargs)


def run(workspace: Path, *extra):
    return bsc.main(
        [
            "--history-dir",
            str(workspace / "history"),
            "--imports-dir",
            str(workspace / "imports"),
            "--no-download",
            *extra,
        ]
    )


def weeks_files(workspace: Path):
    return sorted((workspace / "history" / "weeks").glob("*.jsonl.gz"))


def read_weeks(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        records = [json.loads(line) for line in handle if line.strip()]
    return records[0], records[1:]


def read_map(workspace: Path):
    files = sorted((workspace / "history" / "map").glob("*.map.json.gz"))
    assert files, "aucun fichier carte ecrit"
    with gzip.open(files[-1], "rt", encoding="utf-8") as handle:
        return json.load(handle)


def stations_by_id(records):
    return {record["sta"]: record for record in records}


def codes_of(record):
    return {code["c"] for code in record.get("codes", [])}


# --- Fonctions pures --------------------------------------------------------


def test_extract_data_date_prend_la_date_des_donnees():
    assert bsc.extract_data_date("20260806174318_observatoireod_20260806.csv") == "20260806"
    assert bsc.extract_data_date("20260813010203_observatoireod_20260813.csv.gz") == "20260813"
    assert bsc.extract_data_date("observatoire.csv") == ""


def test_resolve_url_refuse_un_hote_etranger():
    bon = 'https:\\/\\/data.anfr.fr\\/sites\\/default\\/files\\/dataset\\/20260806174318_observatoireod_20260806.csv'
    assert bsc.resolve_observatoire_csv_url(bon).startswith("https://data.anfr.fr/")
    assert bsc.resolve_observatoire_csv_url("aucune url ici") is None


def test_parse_coordinates_refuse_null_island():
    assert bsc.parse_coordinates("48.85, 2.35") == pytest.approx((48.85, 2.35))
    assert bsc.parse_coordinates("0,0") is None
    assert bsc.parse_coordinates("") is None


# --- Premiere execution -----------------------------------------------------


def test_premiere_execution_pose_la_reference_sans_diff(workspace):
    publish(workspace, "20260730", [make_row()])

    assert run(workspace) == 0

    assert weeks_files(workspace) == []
    state = json.loads((workspace / "history" / "state.json").read_text(encoding="utf-8"))
    assert state["current"]["data_date"] == "20260730"
    assert state["previous"] is None
    assert list((workspace / "history" / "sources").glob("*.csv.gz"))


def test_reference_archivee_a_la_main_est_adoptee(workspace):
    """Cas reel : le CSV a ete gzippe dans sources/ avant que le script existe."""
    sources = workspace / "history" / "sources"
    sources.mkdir(parents=True)
    brut = workspace / "20260730120000_observatoireod_20260730.csv"
    write_csv(brut, [make_row()])
    with open(brut, "rb") as reader, gzip.open(
        sources / "20260730120000_observatoireod_20260730.csv.gz", "wb"
    ) as writer:
        writer.write(reader.read())

    publish(workspace, "20260806", [make_row(), make_row(emr_lb_systeme="NR 3500", generation="5G")])
    assert run(workspace) == 0

    files = weeks_files(workspace)
    assert len(files) == 1, "le diff doit sortir dans la foulee de l'adoption"
    header, _ = read_weeks(files[0])
    assert header["from"] == "2026-07-30"
    assert header["to"] == "2026-08-06"


# --- Diff -------------------------------------------------------------------


def test_systeme_ajoute_et_retire(workspace):
    publish(workspace, "20260730", [make_row(), make_row(emr_lb_systeme="GSM 900", generation="2G")])
    assert run(workspace) == 0

    publish(workspace, "20260806", [make_row(), make_row(emr_lb_systeme="NR 3500", generation="5G")])
    assert run(workspace) == 0

    header, records = read_weeks(weeks_files(workspace)[0])
    assert header["rows_added"] == 1
    assert header["rows_removed"] == 1
    assert header["stations_changed"] == 1

    record = records[0]
    operations = {(change["op"], change["key"][1]) for change in record["chg"]}
    assert operations == {("add", "NR 3500"), ("del", "GSM 900")}
    assert codes_of(record) == {bsc.CODE_SYSTEM_ADDED, bsc.CODE_SYSTEM_REMOVED}


def test_changement_de_statut(workspace):
    publish(workspace, "20260730", [make_row(statut="Accord ANFR")])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(statut="En service")])
    assert run(workspace) == 0

    header, records = read_weeks(weeks_files(workspace)[0])
    assert header["rows_updated"] == 1
    assert header["fields_changed"]["statut"] == 1

    change = records[0]["chg"][0]
    assert change["op"] == "upd"
    assert change["f"]["statut"] == ["Accord ANFR", "En service"]

    code = next(c for c in records[0]["codes"] if c["c"] == bsc.CODE_STATUS_CHANGED)
    assert code["was"] == "Accord ANFR"
    assert code["now"] == "En service"


def test_operateur_ajoute_plutot_que_systeme(workspace):
    """SFR arrive sur une station ou il n'etait pas : OPERATOR_ADDED, pas SYSTEM_ADDED."""
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(), make_row(adm_lb_nom="SFR", emr_lb_systeme="LTE 2600")])
    assert run(workspace) == 0

    _, records = read_weeks(weeks_files(workspace)[0])
    codes = [code for code in records[0]["codes"] if code["c"] == bsc.CODE_OPERATOR_ADDED]
    assert len(codes) == 1
    assert codes[0]["op"] == "SFR"


def test_station_apparue(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    publish(
        workspace,
        "20260806",
        [make_row(), make_row(sta_nm_anfr="2000002", sup_id="SUP-2", coordonnees="45.75, 4.85")],
    )
    assert run(workspace) == 0

    _, records = read_weeks(weeks_files(workspace)[0])
    nouvelle = stations_by_id(records)["2000002"]
    assert nouvelle["op"] == "add"
    # Une station entierement nouvelle ne doit pas etre decrite ligne par ligne
    # sur la carte : un seul code, avec la liste des operateurs.
    assert codes_of(nouvelle) == {bsc.CODE_SITE_ADDED}
    assert nouvelle["codes"][0]["ops"] == ["ORANGE"]


def test_station_disparue_garde_ses_coordonnees(workspace):
    publish(
        workspace,
        "20260730",
        [make_row(), make_row(sta_nm_anfr="2000002", sup_id="SUP-2", coordonnees="45.750000, 4.850000")],
    )
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row()])
    assert run(workspace) == 0

    _, records = read_weeks(weeks_files(workspace)[0])
    disparue = stations_by_id(records)["2000002"]
    assert disparue["op"] == "del"
    assert codes_of(disparue) == {bsc.CODE_SITE_REMOVED}
    # Les coordonnees ne sont plus que dans l'ancienne publication.
    assert disparue["lat"] == pytest.approx(45.75)
    assert disparue["lon"] == pytest.approx(4.85)

    carte = read_map(workspace)
    points = [point for point in carte["points"] if point.get("sup") == "SUP-2"]
    assert len(points) == 1
    assert points[0]["lat"] == pytest.approx(45.75)


def test_seuil_de_deplacement(workspace):
    publish(workspace, "20260730", [make_row(coordonnees="48.856600, 2.352200")])
    assert run(workspace) == 0
    # ~3 m : sous le seuil, note dans l'archive mais absent de la carte.
    publish(workspace, "20260806", [make_row(coordonnees="48.856627, 2.352200")])
    assert run(workspace) == 0

    _, records = read_weeks(weeks_files(workspace)[0])
    assert "coordonnees" in records[0]["chg"][0]["f"]
    assert bsc.CODE_MOVED not in codes_of(records[0])

    # ~110 m : au-dessus du seuil.
    publish(workspace, "20260813", [make_row(coordonnees="48.857600, 2.352200")])
    assert run(workspace) == 0
    _, records = read_weeks(weeks_files(workspace)[1])
    assert bsc.CODE_MOVED in codes_of(records[0])


def test_hauteur_et_adresse_sont_suivies_sans_etre_nommees(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(sup_nm_haut="32", adr_lb_lieu="2 rue de Lyon")])
    assert run(workspace) == 0

    header, records = read_weeks(weeks_files(workspace)[0])
    fields = records[0]["chg"][0]["f"]
    assert fields["sup_nm_haut"] == ["25", "32"]
    assert fields["adr_lb_lieu"] == ["1 rue de Paris", "2 rue de Lyon"]
    # Changements de priorite basse : rien sur la carte.
    assert records[0]["codes"] == []
    assert header["fields_changed"]["sup_nm_haut"] == 1


def test_colonne_ajoutee_par_lanfr_ne_casse_rien(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0

    header_v2 = HEADER + ";nouvelle_colonne"
    rows = [make_row() + ";valeur"]
    name = "20260806120000_observatoireod_20260806.csv"
    write_csv(workspace / "imports" / "france_sources" / name, rows, header=header_v2)
    assert run(workspace) == 0

    _, records = read_weeks(weeks_files(workspace)[0])
    assert records[0]["chg"][0]["f"]["nouvelle_colonne"] == ["", "valeur"]


def test_doublons_exacts_fusionnes(workspace):
    publish(workspace, "20260730", [make_row(), make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(), make_row(), make_row()])
    assert run(workspace) == 0

    files = weeks_files(workspace)
    assert len(files) == 1
    header, records = read_weeks(files[0])
    # Deux lignes identiques s'annulent, la troisieme est un ajout.
    assert header["rows_added"] == 1
    assert header["rows_removed"] == 0
    assert len(records) == 1


def test_encodage_cp1252(workspace):
    publish(workspace, "20260730", [make_row(adr_lb_lieu="Rue de la Gare")])
    assert run(workspace) == 0
    publish(
        workspace,
        "20260806",
        [make_row(adr_lb_lieu="Rue de l'Éolienne")],
        encoding="cp1252",
    )
    assert run(workspace) == 0

    _, records = read_weeks(weeks_files(workspace)[0])
    assert records[0]["chg"][0]["f"]["adr_lb_lieu"][1] == "Rue de l'Éolienne"


def test_regroupement_par_support(workspace):
    """Deux stations sur le meme pylone = un seul point sur la carte."""
    publish(workspace, "20260730", [make_row(), make_row(sta_nm_anfr="2000002", adm_lb_nom="SFR")])
    assert run(workspace) == 0
    publish(
        workspace,
        "20260806",
        [
            make_row(statut="Accord ANFR"),
            make_row(sta_nm_anfr="2000002", adm_lb_nom="SFR", statut="Accord ANFR"),
        ],
    )
    assert run(workspace) == 0

    carte = read_map(workspace)
    assert len(carte["points"]) == 1
    point = carte["points"][0]
    assert point["sup"] == "SUP-1"
    assert {change["sta"] for change in point["chg"]} == {"1000001", "2000002"}


def test_repli_geographique_quand_sup_id_est_vide(workspace):
    publish(workspace, "20260730", [make_row(sup_id="")])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(sup_id="", statut="Accord ANFR")])
    assert run(workspace) == 0

    carte = read_map(workspace)
    assert len(carte["points"]) == 1
    assert carte["points"][0]["sup"] is None
    assert carte["points"][0]["lat"] == pytest.approx(48.8566)


def test_aucune_phrase_francaise_dans_le_fichier_carte(workspace):
    """Le popup doit exister en 7 langues : le serveur n'ecrit que des codes."""
    publish(workspace, "20260730", [make_row(statut="Accord ANFR")])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(statut="En service")])
    assert run(workspace) == 0

    carte = read_map(workspace)
    codes = {change["c"] for point in carte["points"] for change in point["chg"]}
    autorises = {
        bsc.CODE_SITE_ADDED,
        bsc.CODE_SITE_REMOVED,
        bsc.CODE_OPERATOR_ADDED,
        bsc.CODE_OPERATOR_REMOVED,
        bsc.CODE_SYSTEM_ADDED,
        bsc.CODE_SYSTEM_REMOVED,
        bsc.CODE_STATUS_CHANGED,
        bsc.CODE_MOVED,
    }
    assert codes <= autorises


# --- Idempotence et garde-fous ---------------------------------------------


def test_csv_bruts_deposes_dans_sources_sont_compresses(workspace):
    """On doit pouvoir copier les publications telles quelles, sans les gzipper."""
    sources = workspace / "history" / "sources"
    sources.mkdir(parents=True)
    write_csv(sources / "20260730120000_observatoireod_20260730.csv", [make_row()])
    write_csv(
        sources / "20260806120000_observatoireod_20260806.csv",
        [make_row(statut="Accord ANFR")],
    )

    assert run(workspace) == 0

    assert list(sources.glob("*.csv")) == [], "les originaux non compresses sont retires"
    assert len(list(sources.glob("*.csv.gz"))) == 2
    files = weeks_files(workspace)
    assert len(files) == 1
    header, _ = read_weeks(files[0])
    assert (header["from"], header["to"]) == ("2026-07-30", "2026-08-06")


def _sources_only_publication(workspace: Path, data_date: str, rows):
    """Publication visible seulement de l'historique, pas du build."""
    name = f"{data_date}120000_observatoireod_{data_date}.csv"
    write_csv(workspace / "history" / "sources" / name, rows)
    return name


def test_csv_depose_pour_le_build(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    name = _sources_only_publication(
        workspace, "20260806", [make_row(statut="Accord ANFR")]
    )

    assert run(workspace) == 0

    depose = workspace / "imports" / "france_sources" / name
    assert depose.is_file(), "le CSV telecharge doit servir aussi au build de base"
    assert "sta_nm_anfr" in depose.read_text(encoding="utf-8-sig")


def test_depot_pour_le_build_desactivable(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    name = _sources_only_publication(
        workspace, "20260806", [make_row(statut="Accord ANFR")]
    )

    assert run(workspace, "--no-publish-imports") == 0

    assert not (workspace / "imports" / "france_sources" / name).is_file()


def test_publications_en_attente_dans_sources_ne_sont_pas_supprimees(workspace):
    """La purge ne doit toucher qu'a ce qui est plus ancien que la paire
    courante : une publication en attente supprimee serait perdue a jamais."""
    sources = workspace / "history" / "sources"
    sources.mkdir(parents=True)
    for date, rows in (
        ("20260716", [make_row()]),
        ("20260723", [make_row(statut="Accord ANFR")]),
        ("20260730", [make_row(statut="En service")]),
    ):
        write_csv(sources / f"{date}120000_observatoireod_{date}.csv", rows)

    assert run(workspace) == 0

    assert [path.name for path in weeks_files(workspace)] == [
        "2026-07-23.jsonl.gz",
        "2026-07-30.jsonl.gz",
    ]
    conservees = sorted(path.name for path in sources.glob("*.csv.gz"))
    assert len(conservees) == 2
    assert conservees[0].startswith("20260723")


def test_colonnes_d_export_ignorees(workspace):
    """id et date_maj changent sur tout le parc a chaque publication : les
    comparer ferait apparaitre chaque site comme modifie chaque semaine."""
    publish(workspace, "20260730", [make_row(id="1", date_maj="2026-07-30")])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(id="999", date_maj="2026-08-06")])
    assert run(workspace) == 0

    header, records = read_weeks(weeks_files(workspace)[0])
    assert records == []
    assert header["stations_changed"] == 0
    assert header["rows_updated"] == 0
    assert header["fields_changed"] == {}


def test_vrai_changement_detecte_malgre_les_colonnes_d_export(workspace):
    publish(
        workspace,
        "20260730",
        [make_row(id="1", date_maj="2026-07-30", statut="Accord ANFR")],
    )
    assert run(workspace) == 0
    publish(
        workspace,
        "20260806",
        [make_row(id="999", date_maj="2026-08-06", statut="En service")],
    )
    assert run(workspace) == 0

    header, records = read_weeks(weeks_files(workspace)[0])
    assert header["fields_changed"] == {"statut": 1}
    fields = records[0]["chg"][0]["f"]
    assert set(fields) == {"statut"}


def test_plusieurs_publications_traitees_dans_l_ordre(workspace):
    """Rattrapage : trois publications en attente donnent trois paliers, pas un
    seul diff bout-a-bout qui ecraserait les etapes intermediaires."""
    publish(workspace, "20260212", [make_row()])
    publish(workspace, "20260219", [make_row(statut="Accord ANFR")])
    publish(
        workspace,
        "20260312",
        [make_row(), make_row(emr_lb_systeme="NR 3500", generation="5G")],
    )

    assert run(workspace) == 0

    files = weeks_files(workspace)
    assert [path.name for path in files] == [
        "2026-02-19.jsonl.gz",
        "2026-03-12.jsonl.gz",
    ]
    premier, _ = read_weeks(files[0])
    second, _ = read_weeks(files[1])
    assert (premier["from"], premier["to"]) == ("2026-02-12", "2026-02-19")
    assert (second["from"], second["to"]) == ("2026-02-19", "2026-03-12")
    assert premier["gap_days"] == 7
    assert second["gap_days"] == 21
    assert second["rows_added"] == 1

    conservees = sorted(
        path.name for path in (workspace / "history" / "sources").glob("*.csv.gz")
    )
    assert len(conservees) == 2
    assert conservees[0].startswith("20260219")


def test_republication_identique_ne_produit_rien(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(statut="Accord ANFR")])
    assert run(workspace) == 0
    assert len(weeks_files(workspace)) == 1

    state_avant = (workspace / "history" / "state.json").read_text(encoding="utf-8")
    assert run(workspace) == 0
    assert len(weeks_files(workspace)) == 1, "aucun second fichier pour la meme publication"
    assert (workspace / "history" / "state.json").read_text(encoding="utf-8") == state_avant


def test_publication_plus_ancienne_ignoree(workspace):
    publish(workspace, "20260806", [make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260730", [make_row(statut="Accord ANFR")])

    assert run(workspace) == 0
    assert weeks_files(workspace) == []


def test_dry_run_n_ecrit_rien(workspace):
    publish(workspace, "20260730", [make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260806", [make_row(statut="Accord ANFR")])

    assert run(workspace, "--dry-run") == 0
    assert weeks_files(workspace) == []
    state = json.loads((workspace / "history" / "state.json").read_text(encoding="utf-8"))
    assert state["current"]["data_date"] == "20260730", "la reference ne doit pas tourner"


def test_garde_fou_refuse_une_publication_amputee(tmp_path, monkeypatch):
    monkeypatch.setattr(bsc, "MIN_ROWS", 0)  # seul le seuil de disparition est teste
    (tmp_path / "imports" / "france_sources").mkdir(parents=True)
    (tmp_path / "history").mkdir()

    stations = [make_row(sta_nm_anfr=f"100000{index}") for index in range(10)]
    publish(tmp_path, "20260730", stations)
    assert run(tmp_path) == 0

    # 3 stations sur 10 disparaissent : bien au-dela des 5 % autorises.
    publish(tmp_path, "20260806", stations[:7])
    assert run(tmp_path) == 3

    assert weeks_files(tmp_path) == []
    state = json.loads((tmp_path / "history" / "state.json").read_text(encoding="utf-8"))
    assert state["current"]["data_date"] == "20260730"
    assert state["previous"] is None


def test_force_passe_outre_le_garde_fou(tmp_path, monkeypatch):
    monkeypatch.setattr(bsc, "MIN_ROWS", 0)
    (tmp_path / "imports" / "france_sources").mkdir(parents=True)
    (tmp_path / "history").mkdir()

    stations = [make_row(sta_nm_anfr=f"100000{index}") for index in range(10)]
    publish(tmp_path, "20260730", stations)
    assert run(tmp_path) == 0
    publish(tmp_path, "20260806", stations[:7])

    assert run(tmp_path, "--force") == 0
    header, _ = read_weeks(weeks_files(tmp_path)[0])
    assert header["stations_changed"] == 3


def test_rotation_ne_garde_que_deux_publications(workspace):
    publish(workspace, "20260716", [make_row()])
    assert run(workspace) == 0
    publish(workspace, "20260723", [make_row(statut="Accord ANFR")])
    assert run(workspace) == 0
    publish(workspace, "20260730", [make_row(statut="En service")])
    assert run(workspace) == 0

    conservees = sorted(
        path.name for path in (workspace / "history" / "sources").glob("*.csv.gz")
    )
    assert len(conservees) == 2
    assert "20260716" not in " ".join(conservees)
    assert len(weeks_files(workspace)) == 2, "les diffs, eux, ne sont jamais purges"
