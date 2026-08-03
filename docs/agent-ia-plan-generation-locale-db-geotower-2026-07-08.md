# Generation locale de geotower_fr.db sur les appareils performants

Cahier des charges pour une IA / agent de code. Objectif : permettre aux telephones
suffisamment performants de **construire eux-memes** la base offline `geotower_fr.db`
a partir des sources ANFR **officielles**, au lieu de telecharger la base compilee
par le serveur. Tout le traitement lourd se fait alors en local sur l'appareil.

Ce document ne modifie pas l'app : il decrit quoi construire, dans quel ordre, et
comment s'inserer sans casser le pipeline offline existant.

Decisions cadre (validees) :

- **Sources = URLs officielles** (data.gouv.fr / data.anfr.fr / data.arcep.fr /
  geo.api.gouv.fr). Pas de miroir serveur.
- **Eligibilite = 2 criteres seulement** : RAM >= 6 Go et stockage libre >= 1 Go.
  Rien d'autre en bloquant.

---

## 1. Objectif

Aujourd'hui `geotower_fr.db` est genere cote serveur par `docs/server/build_fr_anfr_db.py`
puis telecharge tel quel par l'app (manifeste signe -> download -> validation ->
swap atomique -> index -> ouverture Room). Voir le pipeline complet en section 4.

On ajoute un **second chemin de production** du meme fichier :

- l'app telecharge les **sources ANFR officielles brutes** (pas la base compilee) ;
- elle reconstruit `geotower_fr.db` **sur l'appareil** ;
- le fichier produit est ensuite installe par **la meme mecanique** que le
  telechargement actuel (aucun code aval a reecrire).

Cette option est **opt-in**, reservee aux appareils capables, et le telechargement
serveur reste le chemin par defaut et le fallback.

### Pourquoi (benefices reels)

- Autonomie / resilience : l'app se reconstruit depuis les donnees publiques
  officielles meme si le serveur qui heberge la base compilee disparait.
- Fraicheur : reconstruction possible des qu'ANFR publie, sans attendre le serveur.
- Confiance : la base provient directement des sources officielles.

---

## 2. Arbitrage a assumer (a dire clairement dans l'app)

**La generation locale ne fait PAS economiser de la bande passante.** Les sources
ANFR brutes pesent, compressees, autant ou plus que la base finale, et decompressees
beaucoup plus. En plus du reseau, le build consomme CPU, RAM, batterie, temps.

Donc :

- ce n'est pas un gain de data, c'est une fonction d'autonomie/fraicheur ;
- presentee comme "avancee / appareils performants" ;
- l'ecran annonce explicitement les couts (data, duree, batterie) ;
- le telechargement de la base compilee reste **recommande par defaut**.

Cibles a **mesurer sur appareil reel** en phase 1 avant ouverture publique : data
telechargee, RAM crete, **pic de stockage de travail** (doit tenir sous le budget,
cf. sections 7 et 8.2), duree totale.

---

## 3. Principe general : produire le meme artefact (Modele A)

- **Modele A (retenu)** : le builder local produit un fichier `geotower_fr.db`
  compatible avec le schema Room actuel (memes tables, meme `room_master_table`
  identity_hash, meme `PRAGMA user_version = 7`), puis le remet a la chaine
  d'installation existante (`installValidatedDatabase` + `GeoTowerDatabaseValidator`
  + `GeoTowerDatabaseIndexes`). Le builder devient un **producteur alternatif** de
  l'artefact que le downloader produit deja.
- **Modele B (rejete)** : laisser Room creer le schema puis inserer en Room —
  duplique/deregle la logique d'installation/validation. Plus risque.

Consequence : le builder Kotlin est un port fidele de `build_fr_anfr_db.py`. Tout ce
qui est en aval de "un fichier `geotower_fr.db` valide existe" est **deja ecrit** et
reutilise tel quel.

### Determinisme : ne PAS exiger l'egalite octet-a-octet

Un build local n'est pas byte-identique a celui du serveur (timestamp de version,
ordre d'insertion, ids internes `ref_operateur`/`ref_systeme`/`ref_statut` generes
par ordre de rencontre). C'est **acceptable** : les ids internes doivent seulement
etre coherents a l'interieur de la base (l'app resout via les tables `ref_*`). On
n'authentifie donc pas la base locale par un SHA-256 global (voir section 5).

---

## 4. Le pipeline existant (a reutiliser, ne pas reecrire)

- `data/api/DatabaseDownloader.kt` : download, streaming, verif taille + SHA-256,
  puis `GeoTowerDatabaseValidator.validateDatabaseFile`, puis
  `installValidatedDatabase` (swap atomique `.backup`/rename, purge `-wal`/`-shm`),
  puis `GeoTowerDatabaseIndexes.applyToFile`.
- `data/db/GeoTowerDatabaseValidator.kt` : `integrity_check`, **`user_version == 7`**,
  tables/colonnes/affinites/PK/NOT NULL, tables critiques non vides (dont
  **`radio_stat_current`**), `metadata.schema_version == 7`, `country_code == FR`.
- `data/db/AppDatabase.kt` : ouvre le fichier prebuilt (pas de migration, pas
  d'asset). Exige `user_version` + `room_master_table` identity_hash corrects.
- `data/db/GeoTowerDatabaseIndexes.kt` : index runtime `CREATE INDEX IF NOT EXISTS`.
- `data/workers/DatabaseDownloadWorker.kt` : worker foreground dataSync + notif.
- `data/AnfrRepository.kt` : lectures `queryLocalDatabase`, fallback live API.

Point d'insertion du build local : **juste avant** `installValidatedDatabase`. On
produit un fichier temporaire valide et on appelle la meme installation. Extraire de
`DatabaseDownloader` une fonction `installBuiltDatabase(tempFile)` partagee par les
deux chemins.

```
[resolve datasets officiels] -> [download sources] -> [build engine Kotlin]
                                                              |
                                                   geotower_fr.db.localbuild
                                                              |
                                          GeoTowerDatabaseValidator + sanity contenu
                                                              |
                                          installValidatedDatabase (existant)
                                                              |
                                          GeoTowerDatabaseIndexes.applyToFile
                                                              |
                                                   AppDatabase reouvre (Room)
```

---

## 5. Modele de confiance / authenticite (sans miroir)

Le download actuel est authentifie par signature ECDSA (manifeste) + SHA-256 du
`.db`. Avec des **sources officielles directes** et **pas de miroir signe**, la
garantie change. Modele retenu :

1. **Transport** : HTTPS uniquement, vers une **allowlist d'hotes officiels** (voir
   section 6, incl. les hotes de redirection de data.gouv). Rien d'autre accepte.
2. **Sanity de contenu des sources** (avant build) :
   - le ZIP mensuel contient bien les fichiers requis (`SUP_STATION/SUPPORT/ANTENNE/
     EMETTEUR/BANDE`, cf. `MONTHLY_ZIP_REQUIRED_FILES` du script) ;
   - le CSV hebdo a les colonnes attendues (`sta_nm_anfr`, `coordonnees`, `statut`,
     `generation`, `emr_lb_systeme`) ;
   - bornes de taille min/max par source (refus si tronque/vide/absurde).
3. **Sanity de contenu de la base produite** (apres build, avant install) :
   - nombre de stations dans une plage plausible (ex. > 50 000) ;
   - la grande majorite des coordonnees dans les bbox France metro + DOM ;
   - `radio_stat_current` non vide.
4. **Validation finale** : `GeoTowerDatabaseValidator` + ouverture Room
   (identity_hash + `user_version = 7`). Garde-fou terminal.

Confiance = HTTPS vers hotes officiels + sanity sources + sanity base + validation
structurelle. **Honnetete** : la validation structurelle ne detecte PAS une donnee
"plausible mais fausse" ; seule un hash de reference signe le ferait. On n'a pas de
tel hash sans dependance serveur, d'ou les checks de sanity contenu comme garde-fou
raisonnable.

**Durcissement optionnel (OFF par defaut)** : un petit descripteur signe (tiny, notre
API) qui epingle les identifiants de dataset officiels + le SHA-256 attendu des
fichiers courants. Les octets restent telecharges depuis les URLs officielles, mais
verifies contre un hash signe. Active seulement si on accepte cette dependance
serveur ; par defaut le chemin est officiel + validation.

---

## 6. Sources officielles a recuperer (resolues au runtime)

Les URLs de fichiers officiels **tournent a chaque publication** : on ne hardcode pas
un lien de fichier, on **resout la version courante** via les API open-data, puis on
telecharge. Datasets confirmes (deja references dans `AboutScreen.kt`) :

### 6.1 Mensuel "Donnees SUP" (data.gouv.fr) — source principale

- Dataset : `donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1`.
- Resolution : `GET https://www.data.gouv.fr/api/1/datasets/donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1/`
  -> `resources[]` ; choisir la ressource ZIP du support (format `zip`, la plus
  recente par `last_modified`) ; telecharger `resource.url`.
- Contenu : `SUP_STATION.txt`, `SUP_SUPPORT.txt`, `SUP_ANTENNE.txt`,
  `SUP_EMETTEUR.txt`, `SUP_BANDE.txt` + references `SUP_NATURE/PROPRIETAIRE/
  EXPLOITANT/TYPE_ANTENNE.txt`.
- Note redirection : `resource.url` redirige souvent vers
  `object.files.data.gouv.fr` / `static.data.gouv.fr` -> a mettre dans l'allowlist.

### 6.2 Hebdo "Observatoire" (data.anfr.fr, OpenDataSoft) — liste maitresse

- Dataset : `observatoire_2g_3g_4g`.
- Export CSV (Explore v2.1) :
  `GET https://data.anfr.fr/api/explore/v2.1/catalog/datasets/observatoire_2g_3g_4g/exports/csv?delimiter=%3B`
  (fallback v1 : `https://data.anfr.fr/api/records/1.0/download/?dataset=observatoire_2g_3g_4g&format=csv`).
- Fournit `vip_stations` (coordonnees, statut, generation, systeme, date_maj).

### 6.3 Trimestriel ARCEP "sites" (data.arcep.fr) — enrichissement optionnel

- Point d'entree : `https://data.arcep.fr/mobile/sites/`.
- Fournit `arcep_nidt` + `is_zb`. **Optionnel** : si non resolu/echec, le build
  continue avec ces champs a `null`/0 (le validateur ne les exige pas). A resoudre
  par pattern de fichier trimestriel ou index de repertoire ; en cas de doute, sauter.

### 6.4 Communes (geo.api.gouv.fr) — officiel

- `GET https://geo.api.gouv.fr/communes?fields=nom,code` (exactement l'appel du
  script serveur). ~35k entrees INSEE -> nom. Mettre en cache. Si echec, `ref_commune`
  reduite / adresses sans nom de ville (degrade mais valide).

Allowlist d'hotes (HTTPS only) : `www.data.gouv.fr`, `object.files.data.gouv.fr`,
`static.data.gouv.fr`, `data.anfr.fr`, `data.arcep.fr`, `geo.api.gouv.fr`.

---

## 7. Eligibilite de l'appareil (2 criteres, rien d'autre)

Helper `data/build/LocalBuildCapability.kt`. **Bloquants, uniquement** :

- **RAM** : `ActivityManager.MemoryInfo.totalMem` >= **6 Go** (et pas `lowRamDevice`).
- **Stockage libre** : >= **1 Go** dans le repertoire de travail.

Rien d'autre n'est bloquant (pas de gate CPU, Wi-Fi, charge, thermique).

Notes **non bloquantes** (advisory UI, l'utilisateur peut ignorer) : si en donnees
mobiles ou batterie faible, afficher un avertissement de cout, mais **ne pas
empecher**. Le choix RAM+stockage seul est la regle.

Pourquoi 1 Go alors que la base finale est plus petite : il faut de la marge pour le
staging + le fichier final + la copie transitoire de finalisation + le `.backup` du
swap (cf. 8.2). Le pipeline est concu pour tenir largement sous ce budget.

Comportement : non eligible -> option masquee/desactivee avec explication ; eligible
-> proposee en opt-in, defaut OFF, avec l'avertissement de la section 2.

---

## 8. Moteur de build Kotlin (port de build_fr_anfr_db.py)

Module isole et testable : `data/build/`.

- `GeoTowerDbBuilder.kt` : orchestrateur (equivalent `run_build`).
- `AnfrSourceReaders.kt` : lecture streaming CSV/TXT et ZIP.
- `RadioMaskComputer.kt` : masques tech/band, azimuts, is_fh.
- `FrequencyDetailsEncoder.kt` : encodage `Z1:` (miroir de `FrequencyDetailsCodec`).
- `AnfrStatsBuilder.kt` : port de `fr_anfr_stats` (stats current + weekly).
- `GeoTowerDbSchema.kt` : DDL exacte + stamps.
- `LocalBuildCapability.kt` : gating (section 7).

### 8.1 Parsing streaming

- CSV/TXT : delimiteur `;`, encodages testes dans l'ordre `utf-8-sig`, `cp1252`,
  `latin-1`. Lecture **ligne a ligne**, jamais tout le fichier en memoire.
- ZIP : `ZipInputStream` en **une seule passe avant** ; chaque entree routee vers sa
  table de staging (voir 8.2). Pas d'extraction complete sur disque.

### 8.2 Budget RAM **et disque** (contrainte forte : tenir sous 1 Go)

RAM : ne pas reproduire les gros dictionnaires Python. Grosses tables (EMETTEUR,
ANTENNE, BANDE : millions de lignes) -> **tables SQLite de staging**, calculs des
masques/azimuts/`details_frequences` par **aggregation SQL**. L'accumulateur station
(clé `id_anfr`, dizaines de milliers) peut rester en RAM. Peak RAM borne a des
centaines de Mo.

Disque (pour honorer le gate 1 Go, viser un pic ~= taille de la base finale) :

- **Streamer le reseau directement vers le staging** ; ne pas conserver les gros
  fichiers bruts entiers sur disque. Le ZIP mensuel est lu en flux (`ZipInputStream`)
  et insere dans le staging au fil de l'eau ; une **seule passe avant** suffit car
  chaque fichier du ZIP alimente sa propre table (STATION/BANDE/EMETTEUR/ANTENNE/
  SUPPORT), les jointures se font ensuite en SQL.
- Traiter une source, l'ingerer, la supprimer, passer a la suivante.
- **Supprimer le staging AVANT la finalisation** de la base (pour ne pas cumuler
  staging + copie de finalisation).
- **Pas de `VACUUM` sur l'appareil** (il duplique temporairement la base ~x2).
  Inserer dans un ordre naturellement compact et s'en passer ; la compacite finale
  importe peu en local (pas d'upload). VACUUM optionnel seulement si l'espace libre
  le permet largement.

Ecriture SQLite : transaction unique, `PRAGMA journal_mode=OFF`, `synchronous=OFF`,
`temp_store=FILE`, inserts par batch (~5000).

Mesurer le pic disque reel en phase 1 ; s'il depasse le budget, augmenter la
frugalite (streaming plus fin) plutot que relever le gate.

### 8.3 Transformations a reproduire a l'identique

- `normalize_id_anfr` (zfill 10 si numerique).
- `tech_mask` / `band_mask` : toute la logique de `update_masks_from_generation` et
  `update_masks_from_system_and_band` (bandes 2G/3G/4G/5G + FH, plages MHz exactes,
  bits `BAND_*`). **Valeurs de bits identiques a `RadioFilterMasks`** cote app.
- Azimuts mobiles vs FH, `is_fh`, `has_active`, adresses, `code_insee`.
- Registres `ref_operateur`/`ref_systeme`/`ref_statut` (ids par ordre de rencontre).
- References `ref_nature`/`ref_proprietaire`/`ref_exploitant`/`ref_type_antenne`/
  `ref_commune` (uniquement ids utilises).

### 8.4 Codec `details_frequences` (compatibilite obligatoire)

Blob `"Z1:" + base64(zlib.compress(txt, 9))`, stocke seulement si plus court que le
texte brut. L'app le decompresse via `FrequencyDetailsCodec`. Le builder doit
produire exactement ce format : `java.util.zip.Deflater(level=9, nowrap=false)` (flux
zlib) + prefixe `Z1:` + base64 ; ne compresser que si plus court. **Test round-trip
encoder -> `FrequencyDetailsCodec` obligatoire.**

### 8.5 ARCEP (optionnel)

Port de `load_arcep_site_metadata` : `(id_anfr, operateur normalise)` ->
`arcep_nidt`, `is_zb`. Alimente `localisation.arcep_nidt`/`is_zb`. Si source ARCEP
absente, laisser `null`/0.

### 8.6 Stats (OBLIGATOIRE)

`GeoTowerDatabaseValidator` exige **`radio_stat_current` non vide**. Porter
`ensure_stats_tables` + `populate_current_stats` (obligatoire) + `populate_weekly_stats`
(ecran stats). Sans ca, la base echoue a la validation.

### 8.7 Finalisation (stamps Room)

- `metadata` (version, `schema_version=7`, country FR, source ANFR, `date_maj_anfr`,
  `zip_version`) ; `source_versions` (quarterly_version si dispo, provenance
  "local_build").
- `room_master_table (id=42, identity_hash = "f92129b45cc37b357c5ecb8e0ba597f0")`.
- `commit`, (pas de VACUUM, cf. 8.2), puis **`PRAGMA user_version = 7` en tout dernier**
  (sinon Room refuse d'ouvrir — cf. memoire projet).

Si l'`identity_hash` du schema Room evolue, mettre a jour la constante **des deux
cotes** (Python + Kotlin) ; idealement l'exposer depuis un point unique cote app.

---

## 9. Versionnage & verification de mise a jour

`UpdateCheckWorker` compare `metadata.version` vs version distante via
`DatabaseVersionPolicy` (format date). Le build local stampe `metadata.version` avec
une valeur **comparable** derivee de la date ANFR des sources (deterministe), pas d'un
timestamp local arbitraire. Provenance "local_build" dans `source_versions`.
Politique apres build local : proposer re-build local (si toujours eligible) et/ou
download.

---

## 10. Execution : service foreground + reprise

Worker `data/workers/LocalDbBuildWorker.kt` (calque sur `DatabaseDownloadWorker`) :

- `CoroutineWorker` foreground dataSync, unique work `db_local_build`, exclusif avec
  `db_download`.
- **Contraintes bloquantes minimales** : coherentes avec le gate (RAM verifiee en
  amont, stockage). Reseau requis (`CONNECTED`). Pas de contrainte Wi-Fi/charge
  imposee (advisory seulement).
- **Wake lock** pendant la phase CPU.
- **Progression** : phases mappees sur une barre — resolve datasets, download sources
  (%), parse hebdo, ingest 5 fichiers mensuels, calcul SQL, inserts, stats,
  finalisation, validation. `setProgress` + notification.
- **Reprise / mort de process** : long -> checkpoint par phase, reprise propre ou
  redemarrage sans etat corrompu ; concu pour survivre au timeout FGS dataSync.
- **Annulation** + nettoyage integral (`.localbuild`, staging, sources, `-wal`/`-shm`).
- **Succes** : `installBuiltDatabase` partage -> validation -> index -> Room reouvre.
  Echec -> purge + proposer fallback download.

Repertoire de travail dedie (cache), hors base active ; verifier l'espace avant
chaque grosse phase ; supprimer les sources des que la base finale est valide.

---

## 11. UX / reglages

- **Reglage opt-in** : "Generer la base localement (appareils performants)", defaut
  OFF, visible seulement si eligible (section 7).
- **Ecran de lancement** : couts clairs (data approx., duree estimee, batterie),
  mention "source : donnees officielles ANFR/ARCEP", rappel que le telechargement
  reste recommande.
- **Progression** : phase + %, bouton annuler. **Fallback** download sur echec.
- **i18n** : nouvelles strings `values` + `values-fr/en/de/es/it/pt`
  (cf. `AndroidI18nResourcesTest`).
- Points d'entree coherents avec l'existant (onboarding `FirstStartScreen`,
  `HomeScreen`, `DatabaseDownloadCard`) : "Telecharger" (defaut) ou "Generer
  localement" (avance).

---

## 12. Securite & garde-fous

- HTTPS uniquement, **allowlist d'hotes officiels** (section 6) incl. cibles de
  redirection data.gouv ; refuser toute autre URL.
- Sanity sources + sanity base + validation structurelle (section 5).
- Aucune ecriture hors du cache de travail ; swap final uniquement via
  `installBuiltDatabase`/`installValidatedDatabase` (backup/rollback).
- Bornes de taille par source ; ceiling absolu.
- Pas de log de donnees sensibles ni de position GPS.
- Jamais d'inserts directs dans la base servie : produire a cote, valider, swap.
- Feature flag distant pour couper la generation locale, en plus du gating local.

---

## 13. Tests

Unitaires (JVM, logique pure) : normalisation id ANFR ; masques tech/band (cas
limites) == `RadioFilterMasks` ; azimuts/is_fh/has_active/adresses ; **round-trip
codec `Z1:`** ; ARCEP.

Parite vs Python : rejouer builder Kotlin et `build_fr_anfr_db.py` sur **une meme
petite fixture** (sous-ensemble SUP_* + hebdo + ARCEP ; reutiliser les fixtures de
`test_build_fr_anfr_db_arcep.py`/`test_fr_anfr_stats_sources.py`) ; comparer counts,
echantillons, masques, stats current.

Validation / Room : base locale passe `GeoTowerDatabaseValidator` ; s'ouvre dans Room
sans migration ; `radio_stat_current` non vide.

Instrumentes (appareil reel) : build complet depuis sources reelles -> mesurer **RAM
crete, pic disque, duree, batterie** ; valider gate 6 Go / 1 Go ; robustesse (mort de
process -> reprise ; stockage insuffisant ; annulation ; source tronquee -> echec
propre + fallback ; ARCEP/communes indispo -> build degrade mais valide).

---

## 14. Fichiers

Nouveaux (app) : `data/build/GeoTowerDbBuilder.kt`, `AnfrSourceReaders.kt`,
`RadioMaskComputer.kt`, `FrequencyDetailsEncoder.kt`, `AnfrStatsBuilder.kt`,
`GeoTowerDbSchema.kt`, `LocalBuildCapability.kt` ; `data/api/OfficialSourcesResolver.kt`
(resolution datasets + allowlist) ; `data/api/RawSourceDownloader.kt` (download +
sanity + streaming vers staging) ; `data/workers/LocalDbBuildWorker.kt` ;
`ui/components/LocalDbBuildCard.kt` (+ section reglages) ; strings i18n.

A modifier (app) : `data/api/DatabaseDownloader.kt` (extraire
`installBuiltDatabase(tempFile)` partage) ; points d'entree UI ; feature flags.

Serveur : **aucun changement requis** (sources officielles directes). Le durcissement
optionnel (descripteur signe, section 5) est un ajout facultatif.

---

## 15. Ordre d'implementation

- **Phase 1 - Moteur de build (coeur)** : porter `build_fr_anfr_db.py` en
  `data/build/*` (schema, transformations, codec, stats), **testable JVM**, avec
  staging SQLite frugal (RAM + disque). Parite vs Python sur fixtures. Produit un
  `.db` valide depuis des fichiers locaux. **Mesurer RAM/disque ici.**
- **Phase 2 - Sources officielles & capability** : `OfficialSourcesResolver`
  (resolution data.gouv/ANFR/ARCEP/communes + allowlist) + `RawSourceDownloader`
  (streaming, sanity, resumable) + `LocalBuildCapability` (gate 6 Go / 1 Go).
- **Phase 3 - Orchestration** : `LocalDbBuildWorker` foreground (progression,
  reprise, annulation) + `installBuiltDatabase` partage. Bout-en-bout.
- **Phase 4 - UX** : reglage opt-in + ecran couts + progression + fallback + i18n +
  points d'entree.
- **Phase 5 - Durcissement** : RAM/disque/reprise, tests instrumentes, mesures
  reelles, docs, feature flag distant.

Extensions ulterieures (hors MVP) : meme patron pour `geotower_fr_radio.db` et la DB
live.

---

## 16. Criteres d'acceptation MVP

- Sur un appareil eligible (RAM >= 6 Go, >= 1 Go libre), l'app resout et telecharge
  les sources ANFR **officielles** et reconstruit `geotower_fr.db` **en local**, en
  tenant sous le budget disque.
- Le fichier passe `GeoTowerDatabaseValidator`, s'ouvre dans Room sans migration
  (`user_version=7`, identity_hash), `radio_stat_current` non vide.
- Installe par la **meme** mecanique atomique que le download ; index runtime
  appliques ; l'app fonctionne a l'identique (carte, fiche, recherche, stats).
- Opt-in, gate 6 Go / 1 Go, avertissement des couts ; download reste defaut/fallback.
- Parite verifiee vs le builder Python sur fixtures.
- Build interrompu -> pas de base corrompue ; annulation -> nettoyage complet.

---

## 17. A ne pas faire

- Pas d'insert direct dans la base servie : produire a cote puis swap atomique.
- Pas d'exigence d'egalite octet-a-octet avec la base serveur.
- Pas de duplication de la logique install/validation/index : reutiliser l'existant.
- Pas de `@Index` Room (rejet schema) : index runtime uniquement.
- Pas de `VACUUM` sur l'appareil (budget disque) sauf si l'espace le permet largement.
- Pas de presentation "economie de data".
- Pas de miroir serveur pour les sources : URLs officielles uniquement.
- Pas de gate autre que RAM (6 Go) et stockage (1 Go) ; le reste est advisory.
- Pas de chargement des gros fichiers ANFR entierement en RAM ni sur disque :
  streaming + staging.
- Pas de hardcode d'URL de fichier officiel qui tourne : resoudre via les API.

---

## 18. Prompt court pour l'IA qui implementera

Tu dois permettre aux appareils performants (RAM >= 6 Go, >= 1 Go de stockage libre,
et rien d'autre en bloquant) de generer `geotower_fr.db` en local, sans remplacer le
telechargement (qui reste defaut et fallback). Lis `docs/server/build_fr_anfr_db.py`,
`docs/server/fr_anfr_stats.py`, `data/api/DatabaseDownloader.kt`,
`data/db/GeoTowerDatabaseValidator.kt`, `data/db/AppDatabase.kt`,
`data/db/GeoTowerDatabaseIndexes.kt`, `data/models/OfflineEntities.kt`,
`data/AnfrRepository.kt`, `data/api/DownloadManifestVerifier.kt`,
`ui/screens/about/AboutScreen.kt` (URLs officielles). Cree un moteur de build Kotlin
(`data/build/*`) qui porte fidelement `build_fr_anfr_db.py` (schema exact, masques
identiques a `RadioFilterMasks`, codec `Z1:` compatible `FrequencyDetailsCodec`, stats
`radio_stat_current`/`weekly`, stamps `room_master_table` identity_hash
`f92129b45cc37b357c5ecb8e0ba597f0` + `PRAGMA user_version = 7` en dernier, **sans
VACUUM**). Alimente-le par les sources **officielles** resolues au runtime
(data.gouv `donnees-sur-les-installations-radioelectriques-de-plus-de-5-watts-1`,
data.anfr `observatoire_2g_3g_4g`, data.arcep `mobile/sites` optionnel, communes
`geo.api.gouv.fr`), via une allowlist d'hotes HTTPS, en **streaming reseau -> staging
SQLite** pour tenir sous le budget disque (borne RAM et disque, staging supprime avant
finalisation). Ajoute le gating (RAM/stockage seulement), un `LocalDbBuildWorker`
foreground (progression, reprise, annulation), et branche la sortie sur la **meme**
installation atomique que le download (extrais `installBuiltDatabase`). Applique les
checks de sanity (sources + base) puisqu'il n'y a pas de hash signe. La base doit
passer `GeoTowerDatabaseValidator` et s'ouvrir dans Room. Ajoute les tests de parite
vs Python sur fixtures, le round-trip du codec, et les tests instrumentes de mesure
RAM/disque/duree.
