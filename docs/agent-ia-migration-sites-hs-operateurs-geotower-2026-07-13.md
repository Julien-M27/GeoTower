# Migration « sites HS → fichiers opérateurs uniquement » — Runbook

Date : 2026-07-13. Objectif : le fichier `sites_hs.geojson` ne doit plus être bâti à partir de
l'ARCEP mais **uniquement des 4 fichiers CSV opérateurs** (Orange / SFR / Bouygues / Free).
La base ANFR locale reste utilisée, mais seulement pour **géocoder** (retrouver `station_anfr` /
coordonnées). L'ARCEP est **totalement retirée** (elle tombait ~1 exécution sur 2 et bloquait le build).

> Tu ne touches à rien tant que tu n'es pas prêt. Ce document liste, pour chaque élément,
> **ce qui est remplacé par quoi**. La procédure de bascule (§5) se fait d'abord en `/tmp`,
> sans écraser la prod.

---

## 1. Ce qui NE change PAS (aucune action)

- **L'app Android** : schéma de sortie identique → rien à recompiler, rien à republier.
- **`main.py`** (serveur) : sert toujours `sites_hs.geojson` + `sites_hs_date.txt`. Aucune modif.
- **La base ANFR** (`--db`) : inchangée, toujours nécessaire (géocodage).
- **Les autres builders** (`build_fr_anfr_db.py`, `build_fr_radio_db.py`, …) : inchangés.

---

## 2. Les valeurs propres à ton serveur (à récupérer une fois)

La commande Python est presque sûrement dans un **script wrapper `.sh`** (le log a des lignes
`=== Debut de la mise a jour… ===` que le wrapper écrit). Pour le localiser, en **lecture seule** :

```bash
crontab -l                 # montre la ligne cron et le chemin du wrapper .sh (ou du .py)
```

Note ces 3 valeurs (elles apparaissent dans la commande actuelle) :

| Placeholder            | Ce que c'est                                  | Valeur connue / à confirmer                              |
|------------------------|-----------------------------------------------|----------------------------------------------------------|
| `<SCRIPT_DIR>`         | dossier du `build_sites_hs.py` sur le serveur | à confirmer (ex. `/opt/geotower/scripts`)                |
| `<CHEMIN_DB_ANFR>`     | base SQLite ANFR passée à `--db`              | à confirmer (le log dit « DB locale : 134 446 sites »)   |
| dossier de sortie      | où sont écrits les fichiers                    | `/opt/geotower/data/sites_hs/` (confirmé par le log)     |

> Colle-moi ta sortie de `crontab -l` (+ le contenu du wrapper `.sh` s'il existe) et je te
> réécris la commande **exacte**, sans placeholder.

---

## 3. Remplacement n°1 — le script `build_sites_hs.py`

**AVANT** : ton `build_sites_hs.py` actuel (socle ARCEP, ~935 lignes).
**APRÈS** : le nouveau `docs/server/build_sites_hs.py` de ce repo (opérateurs seuls, ~735 lignes).

Action (le jour J) :

```bash
# sauvegarde de l'ancien (pour rollback)
cp <SCRIPT_DIR>/build_sites_hs.py <SCRIPT_DIR>/build_sites_hs.py.arcep.bak
# on remplace par la nouvelle version (copiée depuis le repo)
#   scp .../docs/server/build_sites_hs.py  serveur:<SCRIPT_DIR>/build_sites_hs.py
```

C'est un **remplacement de fichier entier** — rien à éditer à l'intérieur (URLs opérateurs,
chemins de sortie, etc. sont pilotés par les arguments, inchangés).

---

## 4. Remplacement n°2 — la commande (cron ou wrapper `.sh`)

Le **seul changement** : retirer l'argument `--raw-output …` (et d'éventuels `--arcep-*`).
Tout le reste est identique.

**AVANT** (reconstituée d'après le log — la tienne peut différer légèrement) :

```bash
python3 <SCRIPT_DIR>/build_sites_hs.py \
  --output       /opt/geotower/data/sites_hs/sites_hs.geojson \
  --date-output  /opt/geotower/data/sites_hs/sites_hs_date.txt \
  --raw-output   /opt/geotower/data/sites_hs/sites_hs_arcep_raw.geojson \   # ← À SUPPRIMER
  --report-output /opt/geotower/data/sites_hs/sites_hs_enrichment_report.json \
  --db           <CHEMIN_DB_ANFR>
```

**APRÈS** :

```bash
python3 <SCRIPT_DIR>/build_sites_hs.py \
  --output       /opt/geotower/data/sites_hs/sites_hs.geojson \
  --date-output  /opt/geotower/data/sites_hs/sites_hs_date.txt \
  --report-output /opt/geotower/data/sites_hs/sites_hs_enrichment_report.json \
  --db           <CHEMIN_DB_ANFR>
```

> **Filet de sécurité** : même si tu oublies de retirer `--raw-output` / `--arcep-*`, le nouveau
> script les **accepte et les ignore** (il ne plantera pas). Mais autant nettoyer.
>
> Horaire cron inchangé (toutes les 6 h) : `0 */6 * * *`.

---

## 5. Procédure de bascule sans risque (rien n'est écrasé avant l'étape 5.4)

**5.1 — Tests unitaires** (sur le serveur, où Python est dispo) :

```bash
cd <repo>/docs/server && python -m pytest test_build_sites_hs.py -v
```

**5.2 — Run à blanc vers `/tmp`** (ne touche PAS la prod) :

```bash
python3 <SCRIPT_DIR>/build_sites_hs.py \
  --output /tmp/sites_hs_new.geojson \
  --date-output /tmp/sites_hs_date_new.txt \
  --report-output /tmp/report_new.json \
  --db <CHEMIN_DB_ANFR>
```

**5.3 — Comparer avant / après** :

```bash
echo "PROD :" ; jq '.features | length' /opt/geotower/data/sites_hs/sites_hs.geojson
echo "NEW  :" ; jq '.features | length' /tmp/sites_hs_new.geojson
echo "--- répartition par opérateur (NEW) ---"
jq -r '.features[].properties.operateur' /tmp/sites_hs_new.geojson | sort | uniq -c
```

Attendu : NEW ≈ 100–150 features de **moins** que PROD (les pannes ARCEP jamais confirmées par
un opérateur disparaissent), 4 opérateurs présents, libellé Free = « Free Mobile » partout.

**5.4 — Bascule prod** (quand le §5.3 te convient) : appliquer §3 (remplacer le script) puis
§4 (nettoyer la commande), et lancer **un run manuel** pour régénérer la prod :

```bash
python3 <SCRIPT_DIR>/build_sites_hs.py \
  --output /opt/geotower/data/sites_hs/sites_hs.geojson \
  --date-output /opt/geotower/data/sites_hs/sites_hs_date.txt \
  --report-output /opt/geotower/data/sites_hs/sites_hs_enrichment_report.json \
  --db <CHEMIN_DB_ANFR>
```

**5.5 — Vérifier côté app** : ouvrir la carte, filtrer « sites hors service », vérifier que
les marqueurs s'affichent et que la date en pied de fiche = aujourd'hui.

**5.6 — Laisser le cron reprendre** : rien à faire, la prochaine exécution 6 h utilisera le
nouveau script.

---

## 6. Remplacement n°3 (optionnel) — fichier ARCEP devenu inutile

`sites_hs_arcep_raw.geojson` **ne sera plus régénéré** (il va figer à sa dernière valeur).
`main.py` ne le sert pas → aucun impact. Tu peux le laisser ou le supprimer :

```bash
rm -f /opt/geotower/data/sites_hs/sites_hs_arcep_raw.geojson   # optionnel
```

---

## 7. Rollback (si besoin de revenir en arrière)

```bash
cp <SCRIPT_DIR>/build_sites_hs.py.arcep.bak <SCRIPT_DIR>/build_sites_hs.py
# remettre l'argument --raw-output dans la commande, puis relancer un run manuel
```

---

## 8. Récapitulatif « remplace X par Y »

| # | Élément | AVANT | APRÈS |
|---|---------|-------|-------|
| 1 | Script builder | `build_sites_hs.py` version ARCEP | nouveau `build_sites_hs.py` (opérateurs seuls) |
| 2 | Commande cron/wrapper | contient `--raw-output …` | **retirer** `--raw-output` (et `--arcep-*` s'il y en a) |
| 3 | `sites_hs_arcep_raw.geojson` | régénéré à chaque run | plus régénéré → supprimable (optionnel) |
| — | app / main.py / DB / autres builders | — | **inchangés** |
