# Spécification serveur — base radio « non-mobile » (`geotower_fr_radio.db`)

> Réponse à la « section 4 » (ce qu'il faut côté serveur pour garantir la parité
> de lecture). **Rédigé le 2026-07-11.** Source de vérité = les scripts serveur,
> qui sont **déjà dans le repo** (non commités) :
> `docs/server/fr_radio_db_builder.py` (le builder, 38 Ko) et
> `docs/server/build_fr_radio_db.py` (l'orchestrateur qui choisit les sources).
> Il n'y a donc **rien à rétro-concevoir** : le builder EST la spec.

---

## 0. Deux corrections préalables (importantes)

**a) Il y a DEUX bases, ne pas les confondre.** La section 4 mélange les classes
de deux features distinctes :

| Base | Contenu | Builder serveur | Masques | Blob détail | Classes Kotlin liées |
|------|---------|-----------------|---------|-------------|----------------------|
| `geotower_fr.db` | **Mobile grand public** (2G/3G/4G/5G des opérateurs) | `build_fr_anfr_db.py` | `tech_mask` / `band_mask` | `technique.details_frequences` | `RadioMaskComputer`, `FrequencyDetailsEncoder` (réimpl. pour **génération locale**) |
| `geotower_fr_radio.db` | **Non-mobile** (broadcast, réseaux privés, ferroviaire, transport, radar, satellite, FH) | `fr_radio_db_builder.py` | `service_mask` / `system_mask` | `detail_z` | `RadioDatabaseValidator`, `RadioRepository` |

La section 4 (service_mask, system_mask, detail_z, table `non_mobile_site`) porte
**exclusivement sur la base radio non-mobile**. Mais elle cite aussi
`FrequencyDetailsEncoder` et `RadioMaskComputer`, qui appartiennent à **l'autre**
base (mobile). Ces deux classes ne servent PAS à lire la base radio ; elles
réimplémentent le builder mobile pour la génération locale. Pour la base radio,
le seul code de lecture est `RadioDatabaseValidator` (schéma) + `RadioRepository`
(requêtes + décodage `detail_z`).

**b) C'est ARCOM, pas ARCEP.** L'enrichissement broadcast vient de l'**ARCOM**
(ex-CSA, autorité de l'audiovisuel) via `fetch_arcom_radio_services.py`, qui
scrape `arcom.fr` (« Ma Radio » FM/DAB+). L'ARCEP (régulateur télécom) n'est pas
utilisée pour la base radio. Le fichier produit est `arcom_radio_services.json`.

---

## 1. Règle de classification « mobile vs non-mobile »

**Granularité.** L'unité de la base est le **support d'une station**, clé
`(STA_NM_ANFR, SUP_ID)`. On lit les émetteurs ANFR (`SUP_EMETTEUR`), on écarte
les émetteurs « mobile public », et tout support qui conserve ≥ 1 émetteur
devient une ligne `non_mobile_site`.

**Règle d'exclusion** (`fr_radio_db_builder.py`, boucle émetteurs ~l.934-967) :

```python
if (is_mobile_like_system(system) or sys_key == "FH") and is_public_mobile_operator(actor_label):
    skipped_public_mobile += 1
    continue
```

Un émetteur est **exclu** de la base radio si **les deux** conditions sont vraies :

1. **Système « mobile-like » OU FH** — `is_mobile_like_system()` (l.465-471) :
   - `startswith("GSM 900" | "GSM 1800" | "UMTS" | "5G NR")` → vrai
   - `startswith("LTE")` **et** `" P" not in upper` → vrai (donc LTE **non** privé)
   - sinon faux ;
   - **OU** `system_key(system) == "FH"` (faisceau hertzien).
2. **Opérateur = mobile public** — `is_public_mobile_operator(actor_label)`
   (l.474-476) : le libellé exploitant (jointure `ADM_ID`→`SUP_EXPLOITANT`)
   contient un des marqueurs `PUBLIC_MOBILE_OPERATOR_MARKERS` (l.83-105) :
   `BOUYGUES, ORANGE, SFR, SOCIETE FRANCAISE DU RADIOTELEPHONE, FREE MOBILE,
   FREE CARAIB, DIGICEL, OUTREMER, SRR, ZEOP, MAORE, DAUPHIN, TELCO OI, VITI,
   PMT, VODAFONE, SPM TELECOM, ONATI, GLOBALTEL, UTS CARAIBE, OPT`.

**Conséquences (les cas limites comptent) :**
- Opérateur public + FM/DAB/TETRA/… → **conservé** (ex. un diffuseur public).
- Opérateur privé (SNCF, EDF, …) + LTE/GSM → **conservé** (réseau privé).
- « LTE P… » (privé) même chez un opérateur public → **conservé** (le `" P"`
  le sort de `is_mobile_like_system`).
- 2G/3G/4G/5G **et** FH des opérateurs mobiles publics → **exclu** (c'est la
  base mobile `geotower_fr.db` qui les porte).

Autres rejets silencieux (comptés dans le report, non « non-mobile ») :
support sans coordonnées valides (`skipped_support_coords`), émetteur sans
support résolvable (`skipped_no_support` / `skipped_ambiguous_support` quand la
station a >1 support et pas d'`AER_ID` mappable).

**Double filet côté app (à connaître pour la parité).** Même si une ligne FH
d'opérateur public passait, `RadioRepository` la re-masque à la lecture
(`excludePublicMobileFhSql`, l.385-395) :
`AND NOT ((service_mask & FH) != 0 AND actor LIKE un des marqueurs)`.
La vue effective de l'app = base serveur **moins** les lignes FH d'opérateurs
mobiles publics. Le builder les élimine déjà, c'est une ceinture-bretelles.

---

## 2. Bits de `service_mask` et `system_mask` (table `non_mobile_site`)

Les deux colonnes sont des `INTEGER` bitmask. Valeurs **identiques** côté Kotlin
(`RadioServiceMasks` / `RadioSystemMasks`, vérifié bit à bit).

### 2.1 `service_mask` — famille de service (8 bits)

Source Python `SERVICE_*` (l.40-47) ; calcul dans `service_for(system, actor)` (l.522-541).

| Bit | Constante | Valeur | Label | Règle d'affectation (préfixe `EMR_LB_SYSTEME`, en MAJ) |
|-----|-----------|--------|-------|--------------------------------------------------------|
| 0 | `SERVICE_BROADCAST` | `1<<0` = 1 | Radio/TV | `FM`, `RDF DVB`, `RDF T-DAB`, `RDF AM`, `5G BROADCAST` |
| 1 | `SERVICE_PRIVATE` | `1<<1` = 2 | Réseaux privés | `PMR`, `TETRA`, `TETRAPOL`, `RMU`, `EM/REC`, `REC`, `TELEM`, `TELECD`, `ANM`, `LTE` |
| 2 | `SERVICE_RAIL` | `1<<2` = 4 | Ferroviaire | libellé exploitant contient `SNCF`, **ou** système `GSM R` |
| 3 | `SERVICE_TRANSPORT` | `1<<3` = 8 | Transport | `COM TER`, `COM MAR`, `AIS`, `COM AERTER` |
| 4 | `SERVICE_FH` | `1<<4` = 16 | FH | `FH` |
| 5 | `SERVICE_SATELLITE` | `1<<5` = 32 | Satellite | `SAT`, `GPS` |
| 6 | `SERVICE_RADAR` | `1<<6` = 64 | Radar | `RDR`, `GONIO` |
| 7 | `SERVICE_OTHER` | `1<<7` = 128 | Autres | défaut (aucun des ci-dessus) |

Ordre d'évaluation dans `service_for` : FH → BROADCAST → RAIL (SNCF/GSM R) →
TRANSPORT → SATELLITE → RADAR → PRIVATE → OTHER. Le masque d'un site est le
**OU** des services de tous ses émetteurs conservés (`aggregate.service_mask |= service`).

### 2.2 `system_mask` — système technique (20 bits)

Source Python `SYSTEM_BITS` (l.49-70) ; clé calculée par `system_key(system)` (l.479-519).
Le masque du site est le OU de `SYSTEM_BITS[system_key(system)]` sur ses émetteurs
(défaut `OTHER` si non mappé).

| Bit | Clé | Valeur | Préfixe `EMR_LB_SYSTEME` (MAJ) → clé |
|-----|-----|--------|--------------------------------------|
| 0 | `FM` | 1 | `FM` |
| 1 | `DVB_T` | 2 | `RDF DVB` |
| 2 | `DAB` | 4 | `RDF T-DAB` |
| 3 | `AM` | 8 | `RDF AM` |
| 4 | `FH` | 16 | `FH` |
| 5 | `GSM_R` | 32 | `GSM R` |
| 6 | `PMR` | 64 | `PMR` |
| 7 | `TETRA` | 128 | `TETRA`, `TETRAPOL` |
| 8 | `POCSAG` | 256 | `RMU` |
| 9 | `COM_TER` | 512 | `COM TER` |
| 10 | `COM_MAR` | 1024 | `COM MAR` |
| 11 | `AIS` | 2048 | `AIS` |
| 12 | `SAT` | 4096 | `SAT` |
| 13 | `RADAR` | 8192 | `RDR` |
| 14 | `BLR` | 16384 | `BLR` |
| 15 | `LTE_PRIVATE` | 32768 | `LTE` |
| 16 | `BROADCAST_5G` | 65536 | `5G BROADCAST` |
| 17 | `METEO_RS` | 131072 | `RS` (égalité stricte) |
| 18 | `TELEMETRY` | 262144 | `TELEM`, `TELECD` |
| 19 | `OTHER` | 524288 | tout le reste |

> Catégories carte de l'app (dérivées, pas stockées) : `RadioSystemMasks.TV =
> DVB_T | BROADCAST_5G`, `RadioSystemMasks.RADIO = FM | DAB | AM`.

---

## 3. Format de `detail_z` (`non_mobile_detail`)

### 3.1 Encodage / compression

`compress_text()` (l.284-289) :

```
detail_z = "Z1:" + base64_standard( zlib.compress(texte_utf8, level=9) )
```

- Compression **zlib** (deflate **avec** en-tête zlib + Adler-32), niveau 9.
- Base64 **standard** (alphabet `+/`, avec padding `=`) — pas URL-safe.
- **Fallback** : si `len(detail_z) >= len(texte)`, on stocke le **texte brut**
  (sans préfixe `Z1:`). Le lecteur doit gérer les deux cas.

Miroir Kotlin vérifié : écriture `FrequencyDetailsEncoder.encode` (même algo,
`Deflater(BEST_COMPRESSION)` = zlib nowrap=false, `Base64.getEncoder()`) ;
lecture `RadioRepository.decodeDetail` (l.479-492) : si pas de préfixe `Z1:` →
texte brut ; sinon `Base64.decode(DEFAULT)` puis `InflaterInputStream` (zlib).
Les alphabets base64 et le wrapping zlib concordent des deux côtés.

### 3.2 Contenu du texte (avant compression)

`detail_for()` (l.643-671) produit des **lignes `Clé: valeur`** séparées par
`\n` ; toute ligne dont la valeur est vide est **omise**. Ordre et clés exacts :

```
Acteur: <label ADM_ID via ref_actor>
Familles: <libellés service_mask joints par ", ">
Systemes: <"SYS xN" par système, tri most_common, ", ">
Support: <nature>; proprietaire <owner>; hauteur_dm=<height_dm ou "">
Adresse: <ADR_LB_LIEU, ADD1, ADD2, ADD3, CP joints ", ">
Frequences: <échantillons de bandes, max 24, ", "> [, +<reste> si tronqué]
Programmes: <programmes ARCOM "packés", joints "; ">
Antennes: <échantillons antennes, max 12, joints "; ">
```

Ce que l'app lit réellement (`RadioMapModels.kt`, propriétés `detailValue(...)`) :
`Systemes`, `Frequences`, `Antennes`, `Programmes`, `Support`, `Adresse`.
L'`Acteur` est relu via la jointure `ref_actor` (pas depuis le texte), et
`hauteur_dm=(\d+)` + `proprietaire ` sont extraits de la ligne `Support`.

**Détail d'un échantillon d'antenne** (`Antennes`) :
`"<label type TAE>: <azimut> deg (<AER_NB_ALT_BAS>m)"`, éléments séparés par `; `.
Si le type n'est pas résolu à la construction, le texte contient
`TAE_ID <n>: …` ; l'app le remplace à la lecture via `ref_type_antenne`
(`normalizeAntennaTypeLabels`, l.513-520).

**Programme broadcast « packé »** (`Programmes`) — `BroadcastProgram.packed()`
(l.164-171), champs séparés par `|`, entrées séparées par `; ` :

```
service_name | frequency_label | mode | category
```

`compact_token` remplace `|`→`/` et `;`→`,` dans chaque champ pour ne pas casser
les séparateurs. L'app parse via `RadioBroadcastProgram.fromPacked` (même ordre).

---

## 4. Unités & colonnes SUP (confirmations)

Toutes dérivées dans `build_radio_db` (l.811-996). Réponses à tes questions :

| Colonne DB | Unité | Type | Dérivation depuis les fichiers ANFR SUP_* |
|------------|-------|------|-------------------------------------------|
| `lat_e6` | **micro-degrés** (×1e6) ✔ | INTEGER NOT NULL | `SUP_SUPPORT` : `dms_to_e6(COR_NB_DG_LAT, COR_NB_MN_LAT, COR_NB_SC_LAT, COR_CD_NS_LAT)` — DMS→décimal, signe `-` si `S`, `round(×1e6)` |
| `lon_e6` | **micro-degrés** (×1e6) ✔ | INTEGER NOT NULL | idem `COR_*_LON` + `COR_CD_EW_LON` (signe `-` si `W`) |
| `height_dm` | **décimètres** ✔ | INTEGER (nullable) | `SUP_SUPPORT.SUP_NM_HAUT` (mètres) × 10, `round`. `NULL` si champ vide |
| `min_freq_khz` / `max_freq_khz` | **kHz** | INTEGER (nullable) | `SUP_BANDE` : `frequency_to_khz(BAN_NB_F_DEB/FIN, BAN_FG_UNITE)` — G→×1e6, M→×1e3, k→×1, H→÷1e3 ; min/max sur toutes les bandes du site |
| `sta_nm_anfr` | — | TEXT PK | `STA_NM_ANFR`, normalisé : si numérique, **zfill(10)** (zéro-padding sur 10) |
| `sup_id` | — | TEXT PK | `SUP_SUPPORT.SUP_ID` (brut, non paddé) |
| `adm_id` | — | INTEGER | `SUP_STATION.ADM_ID` (station→exploitant). Label via `SUP_EXPLOITANT` (`ADM_ID`→`ADM_LB_NOM`) |
| `nat_id` | — | INTEGER | `SUP_SUPPORT.NAT_ID`. Label via `SUP_NATURE` (`NAT_ID`→`NAT_LB_NOM`) |
| `tpo_id` | — | INTEGER | `SUP_SUPPORT.TPO_ID`. Label via `SUP_PROPRIETAIRE` (`TPO_ID`→`TPO_LB`) |
| `code_insee` | — | TEXT | `SUP_SUPPORT.COM_CD_INSEE` |
| `emitter_count` | comptage | INTEGER NOT NULL | nb d'émetteurs non-mobile conservés sur le support |
| `antenna_count` | comptage | INTEGER NOT NULL | nb de lignes `SUP_ANTENNE` du support |
| `freq_range_count` | comptage | INTEGER NOT NULL | nb de bandes `SUP_BANDE` rattachées |

**CSV ANFR** : séparateur `;`, encodage `utf-8-sig`, virgule décimale gérée
(`float_or_none` fait `,`→`.`). Rattachement antenne/émetteur→support via
`AER_ID` (`SUP_ANTENNE`→`SUP_EMETTEUR`), fallback « support unique de la station »
si l'`AER_ID` est absent/ambigu (`resolve_support`, l.919-925).

**Rappel lecture app** : `RadioRepository` fait `lat_e6 / 1_000_000.0`,
`height_dm / 10.0` — cohérent avec les unités ci-dessus.

---

## 5. Mapping ARCOM (bonus)

**Fichier** : `arcom_radio_services.json`, produit par
`docs/server/fetch_arcom_radio_services.py`.

**Source** : site **ARCOM** `https://www.arcom.fr/radio-et-audio-numerique/radio-fm-dab/radios`
(rubrique « Ma Radio »). Scrape la liste des radios, puis chaque page émetteur
(lat/lon en DMS, fréquence, PAR, hauteur, titulaire, catégorie…). Écrit un JSON
`{ source, source_url, fetched_at, entry_count, entries: [...] }`.

**Champs par entrée** (utiles au matching) : `service_name`, `mode` (FM/DAB…),
`frequency_label`, `frequency_khz`, `category`, `holder`, `latitude_e6`,
`longitude_e6`, `address`, `city`, `department`, `par_max_w`, `emitter_path`.

**Matching ARCOM → site ANFR** (`build_arcom_program_matches`, l.305-437) —
purement géographique + fréquentiel, aucune clé partagée :

1. On n'indexe que les sites ANFR avec `service_mask & SERVICE_BROADCAST`.
2. Grille spatiale au pas `ARCOM_MATCH_BUCKET_E6 = 5000` (µ°), recherche 3×3 cases.
3. Le mode ARCOM doit correspondre à un bit système : `FM`→`SYSTEM_BITS["FM"]`,
   `DAB…`→`SYSTEM_BITS["DAB"]` (`arcom_mode_to_system_bit`) ; le site candidat
   doit avoir ce bit dans `system_mask`.
4. Distance haversine ≤ `ARCOM_MATCH_MAX_DISTANCE_M = 500 m`.
5. Si fréquence connue : on privilégie les candidats dont une bande couvre la
   fréquence à ±`ARCOM_FREQUENCY_TOLERANCE_KHZ = 2 kHz`. Si des candidats ont des
   bandes mais aucune ne matche → l'entrée est rejetée (`unmatched_frequency`).
6. Sinon on garde le candidat le plus proche ; dé-doublonnage par
   `(service_name, mode, frequency_khz)` par site.

Les programmes retenus sont sérialisés dans la ligne `Programmes:` du `detail_z`
(cf. §3.2). Les stats de matching sont dans le `.report.json` généré à côté du `.db`.

**Optionnel** : si `arcom_radio_services.json` est absent, la base se construit
sans la ligne `Programmes` (l'orchestrateur `build_fr_radio_db.py` le passe en
entrée optionnelle, l.133-135). Aucun impact schéma.

---

## 6. Schéma SQLite complet (référence)

`create_schema` (l.575-640). Tables `WITHOUT ROWID` ; `schema_version = 1`,
`country_code = "FR"`, `source = "ANFR_RADIO"` (constantes attendues par
`RadioDatabaseValidator`).

```sql
CREATE TABLE non_mobile_site (
    sta_nm_anfr TEXT NOT NULL, sup_id TEXT NOT NULL, adm_id INTEGER,
    lat_e6 INTEGER NOT NULL, lon_e6 INTEGER NOT NULL,
    nat_id INTEGER, tpo_id INTEGER, height_dm INTEGER, code_insee TEXT,
    service_mask INTEGER NOT NULL, system_mask INTEGER NOT NULL,
    emitter_count INTEGER NOT NULL, antenna_count INTEGER NOT NULL,
    freq_range_count INTEGER NOT NULL, min_freq_khz INTEGER, max_freq_khz INTEGER,
    PRIMARY KEY (sta_nm_anfr, sup_id)
) WITHOUT ROWID;

CREATE TABLE non_mobile_detail (
    sta_nm_anfr TEXT NOT NULL, sup_id TEXT NOT NULL, detail_z TEXT NOT NULL,
    PRIMARY KEY (sta_nm_anfr, sup_id)
) WITHOUT ROWID;

CREATE TABLE ref_actor        (adm_id INTEGER PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE ref_nature       (nat_id INTEGER PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE ref_owner        (tpo_id INTEGER PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE ref_type_antenne (tae_id INTEGER PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID;

CREATE TABLE metadata (
    version TEXT PRIMARY KEY, schema_version INTEGER NOT NULL,
    country_code TEXT NOT NULL, country_name TEXT, source TEXT NOT NULL,
    date_maj_anfr TEXT, zip_version TEXT, row_count INTEGER NOT NULL
) WITHOUT ROWID;

-- index créés après insertion :
CREATE INDEX idx_non_mobile_site_lat_lon ON non_mobile_site(lat_e6, lon_e6);
CREATE INDEX idx_non_mobile_site_service ON non_mobile_site(service_mask);
CREATE INDEX idx_non_mobile_site_actor   ON non_mobile_site(adm_id);
```

Seules `ref_actor` / `ref_nature` / `ref_owner` / `ref_type_antenne` **utilisées**
sont insérées (set des ids référencés). `country_name = "France"`.

---

## 7. Fichiers sources ANFR requis

`build_fr_radio_db.py` prend le dernier export mensuel + le dernier export de
références (dans `$GEOTOWER_IMPORTS_DIR`, défaut `/opt/geotower/data/imports`) :

- **Mensuel** (marqueur `data` dans le nom) : `SUP_STATION.txt`, `SUP_SUPPORT.txt`,
  `SUP_ANTENNE.txt`, `SUP_EMETTEUR.txt`, `SUP_BANDE.txt`.
- **Références** (marqueur `ref`) : `SUP_EXPLOITANT.txt`, `SUP_NATURE.txt`,
  `SUP_PROPRIETAIRE.txt`, `SUP_TYPE_ANTENNE.txt`.
- **Optionnel** : `arcom_radio_services.json`.

Commande serveur : `python3 docs/server/build_fr_radio_db.py`
(produit `geotower_fr_radio.db` + `version_fr_radio.json` + `*.report.json`).
```
