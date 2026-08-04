# Statistiques par département dans `geotower_fr.db`

> **Rédigé le 2026-08-04.** Source de vérité = `docs/server/fr_dept_stats.py`
> (le builder EST la spec). Tests : `docs/server/test_fr_dept_stats.py`.

Deux tables sont calculées côté serveur et écrites dans la base mobile, à côté
des stats nationales existantes (`radio_stat_current` / `radio_stat_weekly`) :

| Table | Contenu |
|-------|---------|
| `dept_stat_current` | une ligne par département : supports, stations, antennes, superficie, population et **tous les ratios** |
| `dept_stat_operator_tech` | une ligne par (département, opérateur, technologie), avec `ALL` comme totalisateur des deux côtés |

**Pas de bump de schéma.** Ces tables ne sont pas des entités Room : elles
s'ajoutent au fichier sans toucher à `room_master_table` ni à `PRAGMA user_version`
(schéma 7, identity hash inchangé). Elles se lisent donc depuis l'app en SQL brut,
comme `MobileDbRowCounts` le fait déjà pour `antenne`. Le validateur
(`GeoTowerDatabaseValidator`) ne les exige pas : une base téléchargée avant ce
changement reste valide.

---

## 1. `dept_stat_current`

| Colonne | Sens |
|---------|------|
| `dept_code` | `72`, `2A`, `971`, `987` — préfixe du code INSEE de la commune du support |
| `dept_name`, `region_code` | libellés du référentiel (peuvent être `NULL`) |
| `area_km2`, `population`, `population_year` | données de cadrage, voir §3 |
| `supports`, `stations`, `antennas` | autorisations ANFR déclarées |
| `supports_active`, `stations_active`, `antennas_active` | idem, restreint à ce qui est **en service** |
| `antennas_fh` | paraboles FH, comptées **à part** et exclues de `antennas` |
| `stations_per_support`, `antennas_per_station` | ratios internes |
| `supports_per_km2`, `stations_per_km2`, `antennas_per_km2` | densité |
| `supports_per_1k_hab`, `stations_per_1k_hab`, `antennas_per_1k_hab` | pour mille habitants |
| `hab_per_support`, `hab_per_station`, `hab_per_antenna` | habitants par équipement |

Les ratios sont arrondis à 1e-6 et valent `NULL` quand le dénominateur manque
(département sans superficie/population connue). Le formatage (2 ou 3 décimales,
séparateurs) reste à la charge de l'app.

## 2. `dept_stat_operator_tech`

Clé `(dept_code, operator_name, tech)` avec `supports`, `supports_active`,
`stations`, `stations_active`, `antennas`, `antennas_active`.

* `tech` ∈ `2G`, `3G`, `4G`, `5G`, `ALL`. **Le FH n'a pas de ligne** : il est hors
  statistiques, seul `dept_stat_current.antennas_fh` le mentionne.
* `operator_name` = libellé ANFR normalisé en majuscules (`ORANGE`, `SFR`,
  `BOUYGUES TELECOM`, `FREE MOBILE`, plus les opérateurs ultramarins dans les
  départements concernés), ou `ALL`.
* Une ligne n'est écrite que si elle porte au moins un compteur non nul.

**`ALL` = distinct, pas la somme.** Une antenne qui porte 4G et 5G compte dans les
deux colonnes mais **une seule fois** dans `tech = 'ALL'`. Les sites de référence
qui affichent un « Total » par opérateur additionnent les colonnes : pour
reproduire exactement ce chiffre, sommer les quatre technos côté app plutôt que
lire `ALL`. `(ALL, ALL)` est cohérent avec la ligne `dept_stat_current`
correspondante.

## 3. Superficie et population

`load_department_reference()` essaie, dans l'ordre :

1. `/opt/geotower/data/references/departements_fr.csv` s'il existe — surcharge
   manuelle, colonnes `code;nom;superficie_km2;population;annee_population`
   (séparateur `;` ou `,`, décimales françaises acceptées) ;
2. `geo.api.gouv.fr` : `/departements` pour les noms, `/communes` pour la
   population et la surface, agrégées par département. Les COM (975, 977, 978,
   984, 986, 987, 988) ne sont pas listées par `/departements` et sont donc
   interrogées une par une ;
3. le cache disque `references/departements_fr.json` de la dernière réponse.

Sans aucune de ces sources, les compteurs ANFR sont quand même écrits : seuls les
ratios de densité et de population restent `NULL`.

**Écart connu sur la superficie.** La somme des surfaces communales IGN donne
6 241 km² pour la Sarthe, là où la superficie officielle INSEE est 6 206 km²
(deux méthodes de calcul différentes, ~0,6 %). Les sites de comparaison utilisent
en général le chiffre INSEE. Pour s'aligner dessus, poser le CSV de références :
il est prioritaire sur l'API.

Le millésime des populations légales n'est pas renvoyé par l'API : il est figé
dans `POPULATION_YEAR_DEFAULT` (`2022`), surchargeable par `--population-year` ou
par la colonne `annee_population` du CSV. La provenance est tracée dans
`source_versions` (`dept_stats_population_source`, `dept_stats_population_year`,
`dept_stats_area_source`).

## 4. Conventions de comptage

* **Périmètre** = ce que contient la base, c'est-à-dire les stations mobiles de
  l'observatoire ANFR (2G/3G/4G/5G). Les chiffres portent sur les **autorisations**
  (tout ce qui est déclaré, « en projet » compris) ; le suffixe `_active` donne la
  part en service. Même définition d'« actif » que `radio_stat_current`, pour que
  les deux familles de stats restent comparables.
* **Une antenne est comptée par station.** Un `AER_ID` mutualisé entre deux
  opérateurs compte pour chacun d'eux : c'est une antenne déclarée par station, et
  c'est la seule lecture qui rende le tableau par opérateur additif.
* **La table `antenne` ne peut pas servir de source.** Sa clé primaire est
  `aer_id` seul : sur un pylône mutualisé, une seule des stations qui déclarent
  l'antenne survit à l'insertion. Les antennes sont donc lues dans
  `technique.details_frequences` (une ligne par système, avec le tag
  `[AER_ID: ...]` posé par le builder). Les lignes sans tag ne sont pas comptées ;
  leur nombre est affiché en fin de build.
* **Technologies d'une station** = union du masque `tech_mask` (issu du CSV
  hebdomadaire) et des systèmes réellement listés dans le détail. Sinon une techno
  pourrait afficher des antennes sans station, ou l'inverse.
* Une station sans `code_insee` exploitable est ignorée et signalée en fin de build.

## 5. Exécution

Le calcul est intégré au build mensuel : `build_fr_anfr_db.py` crée les tables
dans `create_schema()` et les remplit juste après les stats nationales. Rien à
changer dans le cron.

Pour rejouer seulement les stats sur une base déjà construite :

```bash
python3 fr_anfr_stats.py            # stats nationales + départements
python3 fr_dept_stats.py --db /opt/geotower/data/imports/geotower_fr.db
python3 fr_dept_stats.py --offline  # sans appeler geo.api.gouv.fr (cache)
```

Tests : `python3 -m unittest test_fr_dept_stats -v`

## 6. Côté app

Fait : bouton « Par département » en bas de l'écran Statistiques → liste
recherchable (route `stats/departments`, département courant épinglé d'après
`last_map_lat`/`last_map_lon`) → fiche `stats/departments/{deptCode}`.

La lecture passe par deux `@RawQuery` du `GeoTowerDao` : Room ne vérifie pas leur
SQL à la compilation, donc les tables n'ont pas à être déclarées en entités et
l'identity hash reste intact. `queryLocalDatabase` avale l'exception « no such
table » et renvoie une liste vide, ce qui donne le message « indisponible » sans
casser l'écran.

La fiche affiche le tableau opérateur × technologie **en nombre de stations**
(comparable aux sites de référence) avec une colonne « Panneaux » pour le
comptage physique, plus un sélecteur Autorisations / En service. En mode « en
service », les ratios sont recalculés côté app : ceux du serveur portent sur les
autorisations.

La page suit la mécanique commune : entrée dans « Personnalisation des pages »
(`DepartmentStatsSettingsSheet`), blocs de la fiche réordonnables et masquables
via `CustomizableBlock` (appui long), options d'affichage de la liste, aides au
défilement (`PageScrollPrefs.DEPARTMENT_STATS`, dans `customizablePages`), bulle
de découverte, pied de page, fil d'Ariane et entrée dans la recherche des
réglages. Les préférences sont préfixées `page_` pour entrer dans les profils.

## 7. Mode live

`build_live_fr_db.py` recopie les deux tables telles quelles dans
`geotower_live_fr.db` (schéma identique, deux `INSERT ... SELECT` de plus), et
`live_fr_api.py` les expose sous le même drapeau que les stats nationales
(`FEATURE_LIVE_API_FR_STATS`) :

    GET /api/v2/live/fr/stats/departments              -> tous les départements
    GET /api/v2/live/fr/stats/departments/{dept_code}  -> matrice opérateur × techno

Côté app, `getDepartmentStats()` et `getDepartmentOperatorTechStats()` passent par
l'API quand `shouldUseLiveApiFallback(LIVE_API_FR_STATS)` est vrai, exactement
comme les stats nationales ; un échec réseau renvoie une liste vide, donc le
message « indisponible » plutôt qu'une erreur.

`validate_source_schema` **exige** désormais les deux tables dans la base source :
générer la base live depuis un `geotower_fr.db` antérieur à cette fonctionnalité
échoue avec « Tables source manquantes ». Le correctif tient en une commande —
`python3 fr_dept_stats.py --db …/geotower_fr.db` — et vaut mieux qu'une base live
silencieusement amputée.

## 8. Génération locale sur l'appareil

`DepartmentStatsBuilder` remplit les deux tables pendant le build embarqué, juste
après `radio_stat_current`. Les définitions sont celles du serveur, vérifiées par
`DepartmentStatsBuilderTest` qui reprend **les attendus de `test_fr_dept_stats.py`**
(même scénario : pylône mutualisé, antenne 4G+5G, parabole FH, station en projet).

Deux différences d'implémentation, imposées par le téléphone :

* **Mémoire.** Le serveur garde un ensemble d'identifiants de support par
  (département × opérateur × technologie) — plusieurs centaines de Mo sur la France
  entière. Sur l'appareil, une table de staging `stg_dept_station` reçoit une ligne
  par (station, technologie) et SQLite fait les agrégats sur disque ; seules les 101
  lignes départementales repassent en mémoire, pour les ratios. La table est
  supprimée en fin de build.
* **Référentiel.** Superficie et population viennent du **même téléchargement de
  communes** que `ref_commune` : `COMMUNES_URL` demande simplement quelques champs
  de plus (`codeDepartement`, `population`, `surface`), plus un petit appel
  `/departements` pour les noms. Si geo.api.gouv.fr ne répond pas, les compteurs
  sont écrits quand même et seuls les ratios de densité et de population restent
  NULL — le build ne casse pas pour autant.

Les COM (975, 977, 978, 984, 986, 987, 988) ne sont ni dans `/departements` ni dans
`/communes` : comme le serveur, l'appareil les interroge une par une
(`/departements/{code}` renvoie un objet, d'où un parseur dédié). Ces requêtes ne
sont tentées que si la liste globale a déjà répondu — sinon, geo.api.gouv.fr étant
injoignable, ce serait douze délais d'attente enchaînés pour rien.

Détail vu sur la vraie API : les communes des TAAF n'ont pas de champ `population`.
Les deux implémentations écrivent donc `NULL` plutôt que zéro, pour ne pas afficher
« 0 habitant » comme une vraie valeur.

Le millésime des populations (`POPULATION_YEAR`) est figé des deux côtés : penser à
le changer en même temps que `POPULATION_YEAR_DEFAULT` du serveur.
