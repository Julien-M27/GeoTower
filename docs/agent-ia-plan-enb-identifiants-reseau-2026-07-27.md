# Base des identifiants réseau eNB / gNB — Plan

Date : 2026-07-27. Objectif : rattacher les **eNB (4G) et gNB (5G)** aux sites ANFR de GeoTower,
à partir des fichiers du partenaire **eNB-Analytics**, via une base SQLite dédiée
`geotower_fr_enb.db` téléchargeable depuis la section « Base de données » des réglages.

Accord de redistribution : **en place** (confirmé le 2026-07-27). Le serveur GeoTower télécharge
les fichiers du partenaire, en dérive une base compacte et la sert lui-même — on ne relaie pas
leurs URLs vers les téléphones.

---

## 1. La source

Un fichier par opérateur, nommé par le PLMN (MCC+MNC) :

```
https://enb-analytics.fr/files/ressources/NmEa_<PLMN>.ntm.zip
```

| PLMN  | Opérateur | MNC | Taille zip (27/07/2026) |
|-------|-----------|-----|--------------------------|
| 20801 | Orange    | 1   | 932 Ko |
| 20810 | SFR       | 10  | 724 Ko |
| 20815 | Free      | 15  | 1,78 Mo |
| 20820 | Bouygues  | 20  | 819 Ko |

**Métropole uniquement.** Sondage HEAD du 2026-07-27 : 20802 et 20826 renvoient 404, ainsi que
les PLMN ultramarins testés (647xx Réunion, 340-01/02/08 Antilles). Non exhaustif — la liste des
PLMN est donc une **constante de configuration** (`OPERATOR_SOURCES`) et le build ignore
proprement un PLMN en 404, pour qu'ajouter un opérateur reste une ligne.

Les 4 fichiers sont rafraîchis **indépendamment** (dates de dernière modification différentes,
apparemment plus souvent qu'hebdomadaire). Le cron hebdo ne doit donc jamais supposer qu'ils
bougent ensemble.

### Format `.ntm`

CSV `;`, UTF-8, 10 colonnes, **sans ligne d'en-tête nommée**. Relevé sur le fichier Orange
(23 348 lignes) :

| Col | Contenu | Retenu ? |
|-----|---------|----------|
| 1 | Techno (`4G` / `5G`) | oui |
| 2-3 | MCC / MNC | oui (contrôle) |
| 4 | Drapeau `-1` / `0` / `1` / `5` — sémantique non documentée | **non** |
| 5 | TAC présumé (sentinelles `-1`, `2147483647`) | **non** |
| 6 | **eNB / gNB** | oui |
| 7 | **ID support ANFR** (`-1` si non rattaché) | oui |
| 8-9 | Latitude / longitude | oui |
| 10 | Adresse | oui, **seulement** si non rattaché |

> Les colonnes 4 et 5 sont écartées par décision produit du 2026-07-27, faute de sémantique
> documentée. Les récupérer plus tard coûtera un bump de `schema_version` : ne pas les
> réintroduire « au cas où » sans demande explicite.

**Un fichier n'est pas mono-PLMN.** Constaté le 2026-07-27 sur le fichier Free (54 501 lignes en
208-15) : 394 lignes en **208-16** (second code réseau de Free), 817 lignes en **MNC -1**
(opérateur inconnu) et une centaine de lignes portant le MNC d'un autre opérateur (208-1, -3, -6,
-2, -9, -10, -4). Toutes ces lignes partagent `col4 = 2` et `id_support = -1` — un drapeau absent
du fichier Orange. Les fichiers Orange, SFR et Bouygues, eux, sont 100 % mono-PLMN.

Conséquence sur le build : chaque fichier a une **liste de MNC autorisés** (le principal et le
secondaire de l'opérateur), les lignes sont stockées avec **leur vrai MNC**, et tout autre MNC est
ignoré — la source faisant autorité pour un opérateur est son propre fichier. Le contrôle « est-ce
bien le bon fichier ? » porte sur la **dominance du MNC principal parmi les lignes déclarant un
PLMN**, pas sur l'absence de lignes étrangères (elles sont normales) ni sur le total des lignes
(un fichier bruité n'est pas un fichier du mauvais opérateur).

**Ligne 1 = sentinelle**, pas une donnée : `4G;999;99;0;0000;0;-1;0.0;0.0;19/08/2022 / 24/07/2026`.
Le dernier champ porte « date de début / **date des données** ». La seconde date colle exactement
au `Last-Modified` du zip : c'est la version des données de cet opérateur.

Observations utiles (fichier Orange) : 21 423 lignes 4G / 1 925 lignes 5G ; l'eNB est **unique**
dans le fichier (23 347/23 347) ; 19 366 supports distincts, soit jusqu'à 4 eNB par support ;
**1 347 lignes (~6 %) sans support ANFR** (`id_support = -1`, exactement les lignes `col4 = 5`) —
indoor, DAS, femto. Ces lignes sont **conservées** avec leur position et leur adresse, pour qu'une
future recherche par eNB puisse répondre « eNB 39257 — non rattaché ANFR » plutôt que rien.

---

## 2. La base produite

Un **seul fichier fusionné** pour les 4 opérateurs (~4 Mo estimés, ~110 000 lignes) — pas une base
par opérateur : à cette taille, découper multiplierait par 4 les entrées de manifeste, cartes UI,
workers et états d'erreur pour aucun gain, et amputerait la recherche par eNB.

`geotower_fr_enb.db`, `schema_version = 1`, source `ENB_ANALYTICS` :

```sql
CREATE TABLE enb_cell (
    mnc        INTEGER NOT NULL,   -- 1 / 10 / 15 / 20
    techno     INTEGER NOT NULL,   -- 4 (4G) ou 5 (5G)
    enb        INTEGER NOT NULL,   -- eNB ou gNB
    id_support INTEGER,            -- NULL si non rattache (-1 dans la source)
    lat_e6     INTEGER NOT NULL,
    lon_e6     INTEGER NOT NULL,
    address    TEXT,               -- uniquement si id_support IS NULL
    PRIMARY KEY (mnc, techno, enb)
) WITHOUT ROWID;

CREATE INDEX idx_enb_cell_support ON enb_cell(mnc, id_support);

CREATE TABLE enb_source (   -- une ligne par fichier source : tracabilite et fraicheur
    plmn TEXT NOT NULL PRIMARY KEY, mnc INTEGER NOT NULL, mnc_list TEXT NOT NULL,
    operator TEXT NOT NULL, source_date TEXT, row_count INTEGER NOT NULL,
    fetched_at TEXT, from_cache INTEGER NOT NULL
) WITHOUT ROWID;
-- mnc_list = les MNC reellement presents pour cet operateur (ex. « 15,16 » pour Free). C'est LUI
-- qui resout operateur -> lignes enb_cell, pas mnc seul : sinon on perd les 208-16 de Free.

CREATE TABLE metadata (
    version TEXT NOT NULL PRIMARY KEY, schema_version INTEGER NOT NULL,
    country_code TEXT NOT NULL, country_name TEXT, source TEXT NOT NULL,
    source_date TEXT, generated_at TEXT, row_count INTEGER NOT NULL
) WITHOUT ROWID;
```

Les positions sont conservées pour **toutes** les lignes (~1 Mo) : c'est le filet quand un support
disparaît de la base ANFR entre deux publications trimestrielles.

`version` = `<date de données la plus récente>-<digest6>`, ex. `2026-07-27-9f3a1c`. Le digest porte
sur les couples (PLMN, date source, nombre de lignes) : sans lui, un opérateur qui se rafraîchit
avec une date antérieure au maximum ne ferait pas bouger la version, et les clients resteraient
silencieusement sur une base périmée.

### Rattachement à un site

`enb_cell.id_support` → `support.id_support`, puis filtre sur l'opérateur correspondant au MNC.
La table `support` a une PK composite `(id_anfr, id_support)` : un support mutualisé remonte donc
la station de chaque opérateur, et le filtre MNC choisit la bonne. C'est le bon axe — contrairement
au piège `aer_id` déjà rencontré sur la table `antenne`.

---

## 3. Serveur

### `build_fr_enb_db.py` (nouveau)

Script de cron autonome, sur le modèle de `build_sites_hs.py` : télécharge, parse, fusionne,
écrit `geotower_fr_enb.db` + `version_fr_enb.json` + un rapport JSON.

**Cache brut par opérateur** dans `<imports>/enb_sources/NmEa_<PLMN>.ntm` : tout téléchargement
jugé sain y est conservé. Si un opérateur est injoignable ou renvoie un fichier aberrant, le build
repart de sa dernière copie saine au lieu de le faire disparaître de la base. Une panne partielle
chez le partenaire ne doit jamais amputer la base d'un opérateur.

Garde-fous, dans l'ordre :

1. **Téléchargement** : plafond de taille (32 Mio compressé), contrôle du volume décompressé
   annoncé par le zip (64 Mio) avant extraction, une seule entrée `.ntm` attendue.
2. **Format** : MCC obligatoirement 208, MNC obligatoirement celui demandé — sinon le fichier
   entier est rejeté (le format a changé), on retombe sur le cache.
3. **Volume** : plancher absolu par opérateur, et rejet si l'effectif tombe sous 50 % de la
   version précédente (`--min-ratio`). Idem sur le total avant publication.
4. **Complétude** : si un opérateur n'a ni fichier frais ni cache, on **n'écrase pas** la base
   existante — sauf `--allow-partial`.
5. **Écriture atomique** : construction dans un fichier temporaire du même dossier, puis
   `os.replace` — le fichier servi n'est jamais à moitié écrit.

**Canari de qualité** (`--db <geotower_fr.db>`, optionnel) : pourcentage d'`id_support` réellement
retrouvés dans la base ANFR **pour le bon opérateur**, via la jointure exacte que fera l'app.
Journalisé par opérateur dans le rapport. S'il s'effondre d'une semaine à l'autre, c'est que le
format ou la sémantique de la colonne 7 a changé.

Cron **hebdomadaire autonome** (pas dans la chaîne `build_all_db.py`, calée sur le rythme mensuel
ANFR). Le script est tout de même référencé comme étape `enb` de l'orchestrateur pour pouvoir être
rejoué à la main.

### `main.py`

Trois routes calquées sur la base radio, plus l'entrée de manifeste :

| Route | Rôle |
|---|---|
| `GET /api/v2/download/enb_db` | le fichier SQLite |
| `GET /api/v2/enb_db/info` | taille, sha256, version, dates par opérateur |
| `GET /api/v2/download/version_fr_enb` | le JSON de version |
| `enb_db` dans `/api/v2/download/manifest` | **c'est lui qui porte le SHA-256 signé** |

Flags : réutilisation de `database.download` et `database.updateCheck`, plus un kill-switch dédié
`enbDatabase.enabled` pour pouvoir couper la donnée partenaire sans toucher à la base ANFR.
Rappel : tout défaut doit être déclaré **côté serveur (`feature_flags.py`) et côté client**, sinon
le serveur supprime la clé.

---

## 4. Client

Recopie du patron **base radio**, pas du patron base ANFR : hors Room, ouverte en lecture seule à
la requête. Conséquence agréable — les index sont libres, aucun hash de schéma Room à respecter.

- `EnbDatabaseValidator` — nom de fichier, `schema_version`, `country_code`, tables et colonnes.
- `EnbDatabaseDownloader` — URL officielle vérifiée, SHA-256, plafond de taille, validation
  structurelle, installation atomique `.download` / `.backup`.
- `EnbDatabaseDownloadWorker` — mise à jour automatique calquée sur la base radio.
- `DownloadManifest.enbDatabase` dans `DownloadManifestVerifier`.
- 3ᵉ carte de la section « Base de données » des réglages, libellée **« Identifiants eNB / gNB »**,
  avec la mention « Source : eNB-Analytics ».

Mode « traitement local » niveau 3 : la source est le partenaire, il n'existe **aucun chemin de
génération locale** — la base se fige simplement (pas de mise à jour), à documenter dans l'écran.

---

## 5. Lots

| Lot | Contenu | État |
|---|---|---|
| 1 | `build_fr_enb_db.py` : fetch + cache + build + garde-fous + 13 tests | **fait** |
| 2 | `main.py` : 3 routes + entrée manifeste + flags serveur | **fait** |
| 3 | Client : validator + downloader + worker + carte réglages | **fait** |
| 4 | Bloc « Identifiants réseau » sur la fiche site | **fait** |
| 5 | Recherche par eNB (dont eNB orphelins) | à faire |
| 6 | `docs/data-sources.md`, attribution | **fait** (avec le lot 3) |

### Restes connus après les lots 1 à 3

- **Validé en production le 2026-07-27** : les 4 fichiers téléchargés, 118 165 identifiants,
  7,64 Mo, et surtout **99,9 à 100 % des `id_support` retrouvés dans la base ANFR** pour les
  4 opérateurs — la colonne 7 est bien l'ID support et l'axe de jointure est le bon. Le manifeste
  signé porte `enb_db` et l'app le valide.
- **Le taux de rattachement est très inégal** et conditionne l'intérêt du lot 4 : Orange 94 %
  (22 000 rattachés / 1 347 orphelins), Bouygues 69 %, SFR 71 %, **Free 3 %** (1 584 rattachés
  pour 53 311 orphelins). Le bloc « Identifiants réseau » d'une fiche site Free sera donc presque
  toujours vide. Les orphelins gardent position et adresse : la recherche par eNB (lot 5) répond
  pour eux, pas la fiche site. À arbitrer avec le partenaire avant d'attaquer le lot 4.
- **Écran À propos** : il liste les versions des bases ANFR et radio, pas encore celle des eNB.
  Purement informatif (la carte des réglages porte déjà l'info) — à faire si tu veux la parité.
- **Fraîcheur par opérateur** : la table `enb_source` la porte (une date par opérateur), la carte
  n'affiche que la date globale. Une ligne « Orange 24/07 · SFR 27/07 · … » serait utile puisque
  les 4 fichiers bougent indépendamment.

### Lot 4 — bloc « Identifiants réseau » (fait)

`EnbRepository` (lecture seule, comme `RadioRepository`) + `SiteNetworkIdsBlock` + clé de bloc
`network_ids` dans `SitePagePrefs`. Deux colonnes, 4G à gauche et 5G à droite, une ligne par
identifiant (une station peut en porter plusieurs : 3 eNB + 1 gNB observés sur un même support),
appui pour copier. Les libellés de colonne sont des **`<plurals>`** (`site_network_ids_4g/5g`) :
« Identifiant 4G » au singulier, « Identifiants 4G » dès qu'il y en a plusieurs.

En bas à droite, « Source : eNB Analytics » avec un ⓘ qui ouvre une fenêtre présentant le projet
(texte tiré de leur site, plus un lien vers `enb-analytics.fr`). L'attribution est portée là où la
donnée est lue, pas seulement dans les réglages. Orthographe retenue pour tout ce qui vient de
cette fonctionnalité : **« eNB Analytics »**, comme le projet s'écrit lui-même — les raccourcis
externes de l'app, plus anciens, disent encore « eNB-Analytics ».

Trois points à ne pas défaire :

- La résolution opérateur → lignes passe par **`enb_source.mnc_list`**, pas par le MNC principal :
  Free apporte 208-15 **et** 208-16.
- Le bloc **n'émet rien** quand il n'y a aucun identifiant (base absente, opérateur non couvert,
  support non rattaché). Le parent utilisant `Arrangement.spacedBy`, ne rien émettre ne laisse
  aucun trou — ne pas le remplacer par un placeholder « aucune donnée », il serait affiché sur la
  quasi-totalité des sites Free.
- `SitePagePrefs.normalizeOrder` insère `network_ids` dans les ordres déjà personnalisés. Sans
  cette reprise, quiconque a réordonné sa fiche site ne verrait jamais le bloc apparaître.

### Déploiement serveur (à faire par toi)

1. Copier `build_fr_enb_db.py` dans `/opt/geotower/api/`.
2. Déployer `main.py` et `feature_flags.py`, puis redémarrer l'API.
3. Ajouter la ligne de cron hebdomadaire :
   `python3 /opt/geotower/api/build_fr_enb_db.py --db /opt/geotower/data/imports/geotower_fr.db --report-output /opt/geotower/data/imports/enb_build_report.json`
4. Lancer le build une première fois à la main et vérifier le rapport (notamment `join_quality`).

Périmètre validé le 2026-07-27 : **lots 1 à 3** (rendre la base téléchargeable). Les lots 4 et 5
viendront une fois la base éprouvée en conditions réelles.

---

## 6. Reste à demander au partenaire

1. Sémantique des colonnes 4 et 5 (si la 4 encode un statut indoor/projet/confiance, c'est de
   l'information affichable gratuitement ; si la 5 est bien le TAC, elle intéresse le public visé).
   **Question précise** : que signifie `col4 = 2` ? Ce drapeau marque les 1 315 lignes du fichier
   Free qui portent un MNC autre que 15 (dont 817 en MNC -1), toutes sans support. Cellules
   détectées et non attribuées ? Itinérance ? Selon la réponse, ces lignes sont à garder ou non.
2. Stabilité du schéma d'URL et cadence réelle de rafraîchissement.
3. Existe-t-il d'autres PLMN (DOM) ou un fichier d'index listant les bases disponibles ?
4. Formulation d'attribution souhaitée pour la carte des réglages et `docs/data-sources.md`.
