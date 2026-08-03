#!/usr/bin/env python3
"""Orchestrateur de generation des bases GeoTower FR.

Lance, dans le bon ordre, les scripts de construction des bases :

    1. anfr   -> build_fr_anfr_db.py   (construit geotower_fr.db, stats incluses)
    2. live   -> build_live_fr_db.py   (genere geotower_live_fr.db depuis geotower_fr.db)

L'etape "stats" (fr_anfr_stats.py) n'est PAS dans la chaine par defaut car
build_fr_anfr_db.py la realise deja en interne. Elle reste disponible seule
via --only stats si besoin de la rejouer.

Chaque script est lance comme un sous-processus, dans le dossier de ce fichier,
avec le meme interpreteur Python. Par defaut, on s'arrete a la premiere erreur
(les etapes suivantes dependent de la sortie des precedentes).

Exemples :
    python3 build_all_db.py
    python3 build_all_db.py --only anfr
    python3 build_all_db.py --skip live
    python3 build_all_db.py --only stats
    python3 build_all_db.py --continue-on-error --log-file /opt/geotower/api/build.log
    python3 build_all_db.py --dry-run
"""

import argparse
import os
import subprocess
import sys
import time
from datetime import datetime

# Dossier de ce script (= /opt/geotower/api sur le serveur).
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Definition des etapes : cle -> (script, description, arguments supplementaires).
STEPS = {
    "anfr": {
        "script": "build_fr_anfr_db.py",
        "desc": "Construction de geotower_fr.db (ANFR + stats)",
        "args": [],
    },
    "stats": {
        "script": "fr_anfr_stats.py",
        "desc": "Re-enrichissement des statistiques de geotower_fr.db",
        "args": [],
    },
    "live": {
        "script": "build_live_fr_db.py",
        "desc": "Generation de geotower_live_fr.db depuis geotower_fr.db",
        "args": [],
    },
    "enb": {
        "script": "build_fr_enb_db.py",
        "desc": "Construction de geotower_fr_enb.db (identifiants eNB/gNB, partenaire)",
        "args": [],
    },
}

# Ordre canonique de toutes les etapes connues.
ALL_STEPS = ["anfr", "stats", "live", "enb"]

# Chaine executee par defaut (stats exclu, deja fait par anfr ; enb exclu, il a son propre
# cron hebdomadaire et ne depend pas des sources ANFR mensuelles - voir --only enb pour rejouer).
DEFAULT_STEPS = ["anfr", "live"]


class Logger:
    """Ecrit a la fois sur la sortie standard et, en option, dans un fichier."""

    def __init__(self, log_path=None):
        self._fh = None
        if log_path:
            os.makedirs(os.path.dirname(os.path.abspath(log_path)), exist_ok=True)
            self._fh = open(log_path, "a", encoding="utf-8")

    def log(self, message):
        line = f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}"
        print(line, flush=True)
        if self._fh:
            self._fh.write(line + "\n")
            self._fh.flush()

    def close(self):
        if self._fh:
            self._fh.close()


def parse_step_list(raw):
    """Transforme 'anfr,live' en ['anfr', 'live'] en validant les cles."""
    steps = [s.strip() for s in raw.split(",") if s.strip()]
    unknown = [s for s in steps if s not in STEPS]
    if unknown:
        valid = ", ".join(ALL_STEPS)
        raise SystemExit(
            f"Etape(s) inconnue(s) : {', '.join(unknown)}. Valeurs possibles : {valid}"
        )
    return steps


def resolve_steps(args):
    """Determine la liste ordonnee d'etapes a executer selon --only / --skip."""
    if args.only:
        selected = parse_step_list(args.only)
    else:
        selected = list(DEFAULT_STEPS)

    if args.skip:
        skipped = set(parse_step_list(args.skip))
        selected = [s for s in selected if s not in skipped]

    # Re-ordonne selon l'ordre canonique et deduplique en conservant l'ordre.
    ordered = [s for s in ALL_STEPS if s in selected]
    return ordered


def run_step(key, logger, python_exe, dry_run):
    """Execute une etape en sous-processus. Renvoie (succes, duree_secondes)."""
    step = STEPS[key]
    script_path = os.path.join(SCRIPT_DIR, step["script"])
    cmd = [python_exe, script_path, *step["args"]]

    if not os.path.exists(script_path):
        logger.log(f"  ERREUR : script introuvable -> {script_path}")
        return False, 0.0

    logger.log(f"==> [{key}] {step['desc']}")
    logger.log(f"    Commande : {' '.join(cmd)}")

    if dry_run:
        logger.log("    (dry-run : non execute)")
        return True, 0.0

    start = time.monotonic()
    try:
        # cwd = SCRIPT_DIR pour que les imports inter-scripts fonctionnent.
        result = subprocess.run(cmd, cwd=SCRIPT_DIR)
        ok = result.returncode == 0
    except Exception as exc:  # noqa: BLE001
        logger.log(f"    Exception au lancement : {exc}")
        ok = False
    duration = time.monotonic() - start

    if ok:
        logger.log(f"    OK ({duration:.1f}s)")
    else:
        logger.log(f"    ECHEC ({duration:.1f}s)")
    return ok, duration


def parse_args():
    parser = argparse.ArgumentParser(
        description="Orchestre la generation des bases GeoTower FR (ANFR -> live).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--only",
        metavar="LISTE",
        help="Liste d'etapes a executer (separees par des virgules), ex: anfr,live ou stats.",
    )
    parser.add_argument(
        "--skip",
        metavar="LISTE",
        help="Liste d'etapes a sauter (separees par des virgules), ex: live.",
    )
    parser.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Continuer les etapes suivantes meme si l'une echoue (par defaut : arret).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Afficher les commandes sans rien executer.",
    )
    parser.add_argument(
        "--log-file",
        metavar="CHEMIN",
        help="Journaliser aussi la sortie dans ce fichier (en plus de la console).",
    )
    parser.add_argument(
        "--python",
        default="python3",
        metavar="EXE",
        help="Interpreteur Python a utiliser pour les sous-processus (defaut: python3).",
    )
    return parser.parse_args()


def main():
    args = parse_args()
    logger = Logger(args.log_file)

    steps = resolve_steps(args)
    if not steps:
        logger.log("Aucune etape a executer (apres --only/--skip). Rien a faire.")
        logger.close()
        return 0

    mode = "DRY-RUN" if args.dry_run else "EXECUTION"
    on_error = "continue" if args.continue_on_error else "arret"
    logger.log("=" * 60)
    logger.log(f"GeoTower build-all | mode={mode} | sur erreur={on_error}")
    logger.log(f"Etapes : {' -> '.join(steps)}")
    logger.log("=" * 60)

    global_start = time.monotonic()
    results = []  # (key, ok, duration)
    aborted = False

    for key in steps:
        ok, duration = run_step(key, logger, args.python, args.dry_run)
        results.append((key, ok, duration))
        if not ok and not args.continue_on_error:
            logger.log(f"Arret : l'etape '{key}' a echoue (les suivantes en dependent).")
            aborted = True
            break

    total = time.monotonic() - global_start

    logger.log("-" * 60)
    logger.log("Recapitulatif :")
    for key, ok, duration in results:
        status = "OK " if ok else "ECHEC"
        logger.log(f"  [{status}] {key:<6} {duration:6.1f}s  - {STEPS[key]['desc']}")
    not_run = [s for s in steps if s not in {k for k, _, _ in results}]
    for key in not_run:
        logger.log(f"  [SKIP ] {key:<6}    -.-s  - non execute (arret precedent)")
    logger.log(f"Duree totale : {total:.1f}s")
    logger.log("=" * 60)

    logger.close()

    all_ok = all(ok for _, ok, _ in results) and not aborted
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
