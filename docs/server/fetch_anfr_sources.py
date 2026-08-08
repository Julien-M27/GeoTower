#!/usr/bin/env python3
"""Recuperation automatique des sources ANFR mensuelles ("Donnees SUP").

Telecharge les deux ZIP mensuels publies sur data.gouv et les depose la ou
`build_fr_anfr_db.py` les cherche, c'est-a-dire `imports/france_sources/` :

    <date>-export-etalab-data.zip   tables SUP_STATION / SUPPORT / ANTENNE / EMETTEUR / BANDE
    <date>-export-etalab-ref.zip    referentiels (SUP_NATURE, SUP_PROPRIETAIRE, ...)

**Depot seulement.** Le script ne lance jamais `build_all_db.py` : une source
incomplete ou un changement de format cote ANFR ne doit pas pouvoir casser la
base servie aux telephones. La reconstruction reste une decision manuelle.

Le CSV hebdomadaire (observatoire) n'est PAS gere ici : `build_site_changes.py`
le telecharge deja pour son diff et le depose au meme endroit.

Concu pour un cron quotidien : il ne fait rien tant que la publication du mois
est deja sur le disque. Aucune dependance hors bibliotheque standard.

    python3 fetch_anfr_sources.py
    python3 fetch_anfr_sources.py --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path

IMPORTS_DIR = Path("/opt/geotower/data/imports")
FRANCE_SOURCES_DIRNAME = "france_sources"

MONTHLY_SUP_DATASET_API_URL = (
    "https://www.data.gouv.fr/api/1/datasets/"
    "donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1/"
)

# data.gouv redirige vers son stockage objet : les cibles doivent etre autorisees
# elles aussi, sinon le telechargement casse sur la redirection.
ALLOWED_HOSTS = {
    "www.data.gouv.fr",
    "object.files.data.gouv.fr",
    "static.data.gouv.fr",
}

USER_AGENT = "GeoTower-FetchAnfrSources/1.0"
JSON_TIMEOUT_S = 60
DOWNLOAD_TIMEOUT_S = 900
MAX_ATTEMPTS = 3

# Memes plafonds que la generation locale cote app (LocalDbBuildPipeline).
MAX_JSON_BYTES = 64 * 1024 * 1024
MAX_DATA_ZIP_BYTES = 900 * 1024 * 1024
MAX_REF_ZIP_BYTES = 64 * 1024 * 1024

# Marge disque exigee avant de commencer : le ZIP de donnees, plus de quoi le
# decompresser ensuite pendant le build.
MIN_FREE_BYTES = 2 * 1024 * 1024 * 1024

# Un vrai export mensuel contient ces cinq tables (cf. build_fr_anfr_db.py).
MONTHLY_ZIP_REQUIRED_FILES = {
    "SUP_STATION.txt",
    "SUP_SUPPORT.txt",
    "SUP_ANTENNE.txt",
    "SUP_EMETTEUR.txt",
    "SUP_BANDE.txt",
}

LEADING_DATE_RE = re.compile(r"^(\d{8})")


class SourceError(Exception):
    """Source inexploitable : on refuse de deposer plutot que d'alimenter le
    build avec un fichier douteux."""


def log(message: str) -> None:
    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}", flush=True)


# --- Selection de la publication -------------------------------------------


def data_date_key(filename: str) -> str:
    """Cle de tri **normalisee AAAAMMJJ** depuis le prefixe date du nom.

    Deux formats coexistent chez l'ANFR :
      - moderne `AAAAMMJJ`  (20260630-export-etalab-data.zip, depuis ~2018)
      - ancien  `JJMMAAAA`  (31052018_export_etalab_data.zip, 2015-2018)

    Sans normalisation, un tri de CHAINES place `31052018` avant `20260630`
    (`'3' > '2'`) et on repart sur un export de 2018 : perime, plus petit, et en
    Latin-1. Meme piege que `OfficialSources.dataDateKey` cote app.
    """
    match = LEADING_DATE_RE.match(Path(filename).name)
    if not match:
        return ""
    digits = match.group(1)
    if digits.startswith(("19", "20")):
        return digits
    year = digits[4:8]
    if year.startswith(("19", "20")):
        return year + digits[2:4] + digits[0:2]
    return ""


def is_allowed_url(url: str) -> bool:
    if not url.lower().startswith("https://"):
        return False
    host = url[len("https://") :].split("/", 1)[0].split("@")[-1].lower()
    return host in ALLOWED_HOSTS


def select_monthly_zip_urls(dataset_json: str):
    """Retourne (donnees, references) ; `references` peut etre None.

    Le tri se fait sur la date des DONNEES portee par le nom de fichier, jamais
    sur `last_modified` : data.gouv republie parfois d'anciens exports, avec une
    date de mise en ligne recente mais des donnees de plusieurs annees.
    """
    try:
        resources = json.loads(dataset_json).get("resources") or []
    except (ValueError, AttributeError):
        return None

    archives = []
    for resource in resources:
        if not isinstance(resource, dict):
            continue
        url = resource.get("url")
        if not isinstance(url, str) or not is_allowed_url(url):
            continue
        filename = url.rsplit("/", 1)[-1].split("?", 1)[0]
        fmt = str(resource.get("format") or "").lower()
        if fmt != "zip" and not filename.lower().endswith(".zip"):
            continue
        archives.append(
            {
                "url": url,
                "filename": filename,
                "lower": filename.lower(),
                "data_date": data_date_key(filename),
                "modified": str(
                    resource.get("last_modified") or resource.get("created_at") or ""
                ),
            }
        )
    if not archives:
        return None

    archives.sort(key=lambda item: (item["data_date"], item["modified"]), reverse=True)
    data = next((item for item in archives if "data" in item["lower"]), None)
    if data is None:
        data = next((item for item in archives if "ref" not in item["lower"]), None)
    if data is None:
        return None
    reference = next(
        (
            item
            for item in archives
            if "ref" in item["lower"] and item["data_date"] == data["data_date"]
        ),
        None,
    )
    if reference is None:
        reference = next((item for item in archives if "ref" in item["lower"]), None)
    return data, reference


# --- Reseau -----------------------------------------------------------------


def fetch_text(url: str, max_bytes: int) -> str:
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"}
    )
    with urllib.request.urlopen(request, timeout=JSON_TIMEOUT_S) as response:
        payload = response.read(max_bytes + 1)
    if len(payload) > max_bytes:
        raise SourceError(f"reponse trop volumineuse : {url}")
    return payload.decode("utf-8", errors="replace")


def download_to_file(url: str, destination: Path, max_bytes: int) -> int:
    """Telecharge en flux vers un `.part`, puis renomme. Retourne la taille."""
    request = urllib.request.Request(
        url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"}
    )
    temporary = Path(str(destination) + ".part")
    total = 0
    try:
        with urllib.request.urlopen(request, timeout=DOWNLOAD_TIMEOUT_S) as response:
            with open(temporary, "wb") as handle:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > max_bytes:
                        raise SourceError(
                            f"telechargement interrompu au-dela de {max_bytes} octets"
                        )
                    handle.write(chunk)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise
    if total == 0:
        temporary.unlink(missing_ok=True)
        raise SourceError("telechargement vide")
    os.replace(temporary, destination)
    return total


def download_with_retries(url: str, destination: Path, max_bytes: int) -> int:
    last_error = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            return download_to_file(url, destination, max_bytes)
        except SourceError:
            raise
        except Exception as error:  # noqa: BLE001
            last_error = error
            log(f"  tentative {attempt}/{MAX_ATTEMPTS} echouee : {error}")
    raise SourceError(f"telechargement impossible apres {MAX_ATTEMPTS} tentatives : {last_error}")


# --- Validation -------------------------------------------------------------


def is_monthly_anfr_zip(path: Path) -> bool:
    """Vrai si l'archive porte bien les cinq tables SUP attendues."""
    try:
        with zipfile.ZipFile(path, "r") as archive:
            names = {Path(name).name.upper() for name in archive.namelist()}
    except (OSError, zipfile.BadZipFile):
        return False
    return all(name.upper() in names for name in MONTHLY_ZIP_REQUIRED_FILES)


def is_readable_zip(path: Path) -> bool:
    try:
        with zipfile.ZipFile(path, "r") as archive:
            return bool(archive.namelist())
    except (OSError, zipfile.BadZipFile):
        return False


def ensure_free_space(directory: Path) -> None:
    free = shutil.disk_usage(directory).free
    if free < MIN_FREE_BYTES:
        raise SourceError(
            f"espace disque insuffisant sur {directory} : "
            f"{free // (1024 * 1024)} Mo libres, {MIN_FREE_BYTES // (1024 * 1024)} Mo exiges"
        )


# --- Programme principal ----------------------------------------------------


def fetch_one(entry, destination_dir: Path, max_bytes: int, validate, args) -> bool:
    """Telecharge une archive si elle n'est pas deja la. True si deposee."""
    destination = destination_dir / entry["filename"]
    if destination.is_file() and not args.force:
        log(f"deja present : {entry['filename']}")
        return False
    if args.dry_run:
        log(f"--dry-run : {entry['filename']} serait telecharge depuis {entry['url']}")
        return False

    log(f"telechargement de {entry['filename']}")
    staging = destination_dir / (entry["filename"] + ".incoming")
    size = download_with_retries(entry["url"], staging, max_bytes)
    if not validate(staging):
        staging.unlink(missing_ok=True)
        raise SourceError(
            f"{entry['filename']} : archive invalide ou incomplete, rien n'a ete depose"
        )
    os.replace(staging, destination)
    log(f"depose : {destination} ({size} octets)")
    return True


def warn_on_accumulation(destination_dir: Path) -> None:
    archives = sorted(destination_dir.glob("*.zip"))
    if len(archives) > 4:
        total = sum(path.stat().st_size for path in archives)
        log(
            f"ATTENTION : {len(archives)} ZIP dans {destination_dir} "
            f"({total // (1024 * 1024)} Mo au total), a faire le menage"
        )


def run(args) -> int:
    destination_dir = Path(args.imports_dir) / FRANCE_SOURCES_DIRNAME
    destination_dir.mkdir(parents=True, exist_ok=True)
    if not args.dry_run:
        ensure_free_space(destination_dir)

    dataset_json = fetch_text(MONTHLY_SUP_DATASET_API_URL, MAX_JSON_BYTES)
    selection = select_monthly_zip_urls(dataset_json)
    if selection is None:
        raise SourceError("aucune archive exploitable dans le dataset data.gouv")
    data, reference = selection
    log(
        f"publication mensuelle retenue : {data['filename']} "
        f"(donnees du {data['data_date'] or 'inconnu'})"
    )

    deposited = 0
    if fetch_one(data, destination_dir, MAX_DATA_ZIP_BYTES, is_monthly_anfr_zip, args):
        deposited += 1

    if reference is None:
        log("aucun ZIP de referentiels publie pour cette periode")
    elif args.skip_ref:
        log("--skip-ref : referentiels ignores")
    elif fetch_one(reference, destination_dir, MAX_REF_ZIP_BYTES, is_readable_zip, args):
        deposited += 1

    warn_on_accumulation(destination_dir)
    if deposited:
        log(
            f"{deposited} archive(s) deposee(s). La base n'est PAS reconstruite : "
            "lancer build_all_db.py quand tu le souhaites."
        )
    else:
        log("rien de neuf : aucune archive telechargee")
    return 0


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description=(
            "Telecharge les ZIP mensuels ANFR et les depose pour le build. "
            "Ne reconstruit jamais la base."
        )
    )
    parser.add_argument("--imports-dir", default=str(IMPORTS_DIR))
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="affiche ce qui serait telecharge, sans rien ecrire",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="retelecharge meme si l'archive est deja presente",
    )
    parser.add_argument(
        "--skip-ref",
        action="store_true",
        help="ne pas telecharger le ZIP de referentiels",
    )
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    try:
        return run(args)
    except SourceError as error:
        log(f"REFUS : {error}")
        return 3
    except Exception as error:  # noqa: BLE001
        log(f"ERREUR : {error.__class__.__name__}: {error}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
