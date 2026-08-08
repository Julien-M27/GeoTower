# Optimisation de la generation locale des bases (RAM, CPU, duree)

Cahier des charges pour une IA / agent de code. Suite de
`docs/agent-ia-plan-generation-locale-db-geotower-2026-07-08.md`, qui a livre la
fonctionnalite. Ce plan-ci ne change **ni le format produit ni les sources** : il
reduit ce que la generation coute a l'appareil, pour pouvoir **abaisser le gate**
(aujourd'hui RAM >= 6 Go) et ouvrir la generation aux telephones modestes.

Objectifs, dans cet ordre :

1. **Moins de RAM** — c'est le seul critere qui exclut vraiment des appareils.
2. **Moins de disque** — le second cout reel, aujourd'hui sous-estime par le gate.
3. **Plus vite / moins de batterie** — meme travail, moins de cycles, pas de surchauffe.
4. **Puis seulement** : baisser le seuil d'eligibilite, avec des chiffres mesures.

Regle non negociable : **la base produite doit rester octet-pour-octet equivalente**
(memes tables, memes lignes, memes blobs) apres chaque optimisation. Cf. section 5.

---

## 1. Ce qui est deja fait (ne pas refaire)

Le builder n'est pas naif : plusieurs points chauds ont deja ete traites, et les
commentaires du code en gardent la trace. A relire avant de toucher quoi que ce soit.

- Staging SQLite plutot que RAM pour les grosses tables SUP
  (`GeoTowerDbBuilder.kt`, `RadioDbBuilder.kt`).
- Index secondaires crees **apres** le chargement en masse (`GeoTowerDbBuilder.kt:543`).
- Index couvrants pour que les `GROUP BY id_anfr` se fassent en flux, sans tri
  temporaire (`GeoTowerDbBuilder.kt:183`, `:264`).
- `group_concat` cote SQL pour ne remonter qu'une ligne par station au lieu de
  ~2,5 M lignes (`GeoTowerDbBuilder.kt:495`).
- Curseurs forces en forward-only, sinon scan en O(N²) (`AndroidSqlDatabase.kt:69`).
- Cache de l'index de colonne par nom (`AndroidSqlDatabase.kt:105`).
- En-tete CSV resolu une fois par fichier, pas par ligne (`AnfrCsvRow.kt`).
- Chemin rapide de decoupage CSV sans guillemets (`AnfrSourceReaders.kt:101`).
- `Deflater` en `BEST_SPEED` + `end()` explicite (`FrequencyDetailsEncoder.kt:32`).
- Suppression precoce de `stg_bande` / `stg_antenne_sta` / `stg_emetteur`
  (`GeoTowerDbBuilder.kt:220`, `:270`, `:341`).
- `DISTINCT` retire de la requete de stats (`AnfrStatsBuilder.kt:81`).
- ZIP mensuel lu en flux, jamais decompresse en entier (`AnfrSourceReaders.kt:236`).
- Observatoire non telecharge du tout si le pack mobile n'est pas demande
  (`LocalDbBuildPipeline.kt:143`).

Ce plan attaque ce qui reste : les **accumulateurs RAM par station/support**, les
**allocations par ligne**, le **fichier de staging fusionne avec la base finale**, et
l'**absence totale de parallelisme**.

---

## 2. Diagnostic : le gate ne mesure pas la bonne chose

`LocalBuildCapability` bloque sur `totalMem >= 6 Go` (`LocalBuildCapability.kt:21`).
Or ce n'est pas la RAM totale qui fait echouer un build, c'est le **heap Java du
process**, plafonne par `dalvik.vm.heapgrowthlimit` :

| RAM appareil | `getMemoryClass()` typique | `getLargeMemoryClass()` typique |
|---|---|---|
| 3-4 Go | 128-192 Mo | 256-384 Mo |
| 6-8 Go | 192-256 Mo | 512 Mo |
| 12 Go+ | 256 Mo | 512 Mo |

L'ecart entre un 4 Go et un 8 Go est donc de l'ordre de **1,3x sur le heap**, pas de
2x sur la RAM. Autrement dit : si le pic heap actuel tient a peine sur un 6 Go, il
manque peu pour tenir sur un 4 Go — et si on le divise par deux, meme un 3 Go passe.

Deuxieme angle mort : le gate exige **1 Go de stockage libre**
(`LocalBuildCapability.kt:23`) alors que le pic disque reel additionne le ZIP SUP, le
CSV observatoire, les CSV ARCEP, la base mobile **avec son staging dedans**, et la
base radio **avec son staging + son VACUUM**. C'est tres probablement bien au-dela
d'1 Go (a mesurer en S0). Un build qui remplit le stockage echoue tard, apres avoir
deja consomme la data et la batterie.

Conclusion : **on ne peut pas baisser le seuil sans mesurer d'abord**. S0 est un
prealable, pas une formalite.

---

## 3. Inventaire des points chauds

### 3.1 RAM — accumulateurs vivants pendant tout le build

| # | Ou | Structure | Ordre de grandeur estime |
|---|---|---|---|
| R1 | `RadioDbBuilder.kt:230` | `HashMap<String, Agg>`, un `Agg` par support non-mobile, avec `LinkedHashMap systems` + `ArrayList freqSamples` (**jusqu'a 24 chaines**, `:46`) | ~1-2 Ko/support -> **150-400 Mo** |
| R2 | `GeoTowerDbBuilder.kt:78` | `LinkedHashMap<String, StationAcc>`, un objet + jusqu'a 8 chaines (adresse, 3 dates, azimuts...) par station | ~400-700 o/station -> **80-200 Mo** |
| R3 | `AnfrStatsBuilder.kt:119` | `HashMap<Triple, MutableSet<String>>` x2 : chaque support est insere dans 5 a 20 ensembles | 1-3 M entrees -> **50-150 Mo** |
| R4 | `RadioDbBuilder.kt:302` | `HashMap<String, AntennaInfo>`, jusqu'a 12 chaines d'echantillon par support | **20-60 Mo** |
| R5 | `LocalDbBuildPipeline.kt:334` | map ARCEP `(id_anfr, operateur) -> meta`, construite **avant** le build et gardee tout du long, alors qu'elle finit recopiee dans `stg_arcep` (`GeoTowerDbBuilder.kt:329`) | **15-25 Mo** |
| R6 | `RawSourceDownloader.parseCommunesJson` | ~35 000 communes en map | **5-8 Mo** |

R1 explique pourquoi le pack radio est le plus gourmand ; R2 + R3 se cumulent dans la
meme execution que R1 quand l'utilisateur coche tout (cas par defaut du worker,
`LocalDbBuildWorker.kt:94`). **Le pire cas est donc « tous les packs ».**

### 3.2 RAM — pression GC (invisible au pic, tres visible au chrono)

- `BatchInserter.add(listOf(...))` (`GeoTowerDbBuilder.kt:49`, `RadioDbBuilder.kt:66`,
  `DepartmentStatsBuilder.kt:231`) alloue **une `ArrayList` + un boxing par colonne
  numerique**, pour chaque ligne inseree. Puis `AndroidSqlDatabase.bindRow`
  (`:81`) re-parcourt la liste avec un `when` sur type boxe.
- `CsvRowIterator.next()` (`AnfrSourceReaders.kt:210`) alloue un `arrayOfNulls` +
  un `AnfrCsvRow` + un `String` par champ, **a chaque ligne lue**.

Sur ~15-25 M lignes source, cela represente des **centaines de millions d'objets
ephemeres**. Sur un heap de 128 Mo, le GC tourne en permanence : c'est du temps CPU
et de la batterie purs, sans aucun travail utile.

### 3.3 CPU / duree

- **Aucun parallelisme.** Tout est sequentiel dans un seul `withContext(Dispatchers.IO)`
  (`LocalDbBuildPipeline.kt:59`). Les telephones vises ont 6 a 8 coeurs.
- **Telechargements sequentiels et bloquants** : ZIP SUP, puis observatoire, puis
  ARCEP (`LocalDbBuildPipeline.kt:90-187`). Pendant ces minutes, le CPU ne fait rien,
  alors que l'ingestion de `SUP_BANDE` ne depend pas de l'observatoire.
- **`details_frequences` decode deux fois** apres avoir ete encode une fois :
  `AnfrStatsBuilder.kt:137` puis `DepartmentStatsBuilder.kt:264`. Deux passes
  d'inflate + split sur la totalite des stations.
- **Un `Deflater` natif cree et detruit par station** (`FrequencyDetailsEncoder.kt:32`) :
  `end()` est indispensable (fuite native sinon), mais un `reset()` sur une instance
  reutilisee eviterait ~250 k a 700 k init/free natifs.
- **`VACUUM` complet de la base radio** (`RadioDbBuilder.kt:388`) : reecriture
  integrale du fichier, uniquement pour se debarrasser du staging.
- **Transaction par lot de 5 000** (`GeoTowerDbBuilder.kt:43`) avec `journal_mode=OFF` :
  la transaction n'apporte plus de garantie de rollback, seulement son cout.
- **`page_size` laisse par defaut** (4096) alors que le build ecrit des centaines de
  Mo ; `locking_mode` laisse en `NORMAL` (verrou pris/relache a chaque transaction).
- **Cles primaires maintenues pendant le chargement en masse** de `stg_antenne`,
  `stg_support`, `stg_station_final`, `stg_r_support` (`GeoTowerDbBuilder.kt:547`) :
  contrairement aux index secondaires, elles n'ont pas ete deportees apres le load.

### 3.4 Disque

- **Le staging vit dans le fichier final.** `GeoTowerDbBuilder` cree `stg_*` dans la
  meme base que `localisation`/`technique`, les supprime a la fin
  (`GeoTowerDbBuilder.kt:410`) **sans VACUUM** (decision assumee du plan initial).
  SQLite ne rend pas les pages liberees : **le fichier installe conserve la taille du
  pic de staging**. A verifier en S0, mais si c'est confirme, la base generee
  localement est bien plus grosse que la base telechargee, pour un contenu identique.
- Les sources ne sont liberees qu'a la toute fin, dans le `finally`
  (`LocalDbBuildPipeline.kt:318`), alors que l'observatoire n'est lu qu'en phase 1 et
  le ZIP SUP plus du tout apres la phase 8.
- La base radio, elle, fait un `VACUUM` : il faut **2x la taille du fichier** en pic.

---

## 4. Les tranches

Chaque tranche est independante, mesurable, et livrable seule. L'ordre est celui du
rapport gain/risque.

### S0 — Instrumentation (prealable obligatoire) — **FAIT**

Sans chiffres, toutes les tranches suivantes sont des paris.

**Livre le 2026-08-05.** `LocalBuildMetrics` (pur, teste JVM) cumule duree et lignes par phase —
en gerant les phases traversees plusieurs fois — et retient les pics ; `LocalBuildMetricsRecorder`
echantillonne toutes les 2 s le tas Java, la memoire native et le stockage de travail (le PSS une
fois sur cinq, plus couteux a lire) sur un thread demon arrete dans le `finally` du pipeline ;
`BuildDeviceProfiles` lit `memoryClass` / `largeMemoryClass` / `isLowRamDevice` / coeurs ;
`LocalBuildReportStore` conserve le dernier rapport, affiche dans **Diagnostic > « Generation
locale (mesures) »** (carte depliable, absente tant qu'aucune generation n'a tourne, incluse dans
le rapport copiable). Les tailles des sources et des bases produites sont relevees **avant** le
nettoyage du `finally`.

#### Mesure de reference — Galaxy A52s (SM-A528B), tous packs, 2026-08-06

RAM 5371 Mo | tas Java max **256 Mo** (largeHeap 512) | 8 coeurs | pas lowRam.

| Poste | Valeur |
|---|---|
| Duree totale | **43 min 07 s** |
| Pic tas Java | **187 Mo = 73 % du plafond** |
| Pic memoire native | 235 Mo |
| Pic PSS process | 725 Mo |
| Pic stockage | **1350 Mo** (travail 255 + bases en cours 1094) |
| Sources | ZIP SUP 62 Mo + observatoire 173 Mo + ARCEP 19 Mo |
| Bases produites | `geotower_fr.db` **146 Mo**, `geotower_fr_radio.db` 20 Mo |

Repartition du temps : BUILDING_DETAILS 14 min 23 s (100 k stations, **115 lignes/s**) +
RADIO_BUILDING 14 min 19 s (2,6 M lignes, 3026 lignes/s) = **67 % du build**. Puis
COMPUTING_FREQUENCIES 5 min 10 s (4,65 M lignes), READING_SUPPORTS 4 min 48 s (7,85 M lignes,
27 256 lignes/s), COMPUTING_STATS 2 min 23 s, READING_STATIONS 1 min 48 s (800 k lignes, telechargement
de l'observatoire compris). Tout le reste est sous 5 s.

**Ce que la mesure corrige dans ce plan :**

1. **Le fichier produit n'est PAS gonfle** (146 Mo, soit moins que la base servie par le serveur) :
   les pages liberees par les DROP progressifs sont bien reutilisees par les tables finales.
   L'hypothese de la section 3.4 est fausse. S3 garde sa valeur pour le **pic** disque
   (1094 Mo de bases en vol pour 166 Mo utiles), pas pour la taille installee.
2. **Le reseau n'est pas un sujet** : ~2 min sur 43, soit 4 %. Les telechargements concurrents
   (S4.3) sont **abandonnes** — ils n'achetent rien.
3. **Le gate disque de 1 Go est faux** : le pic reel est de 1350 Mo. Ce n'est pas une optimisation
   a planifier, c'est un **bug de gate** a corriger (un appareil a 1,2 Go libre echoue en fin de
   build, apres avoir consomme 250 Mo de data et 40 min de batterie).
4. **Le tas est bien le mur** : 187 Mo passent sur un plafond de 256 Mo, mais un 4 Go typique
   plafonne a 192 Mo — il faut donc reduire le pic pour ouvrir la generation en dessous de 6 Go.

#### Mesure « mobile seul » — meme appareil, 2026-08-06

| Poste | Valeur |
|---|---|
| Duree totale | **28 min 08 s** |
| Pic tas Java | **199 Mo = 78 % du plafond — pendant COMPUTING_STATS** |
| Pic stockage | **1021 Mo** (travail 255 + bases 765) — pendant COMPUTING_FREQUENCIES |
| Base produite | 146 Mo (serveur : 138,7 Mo, soit **+5 %** seulement) |

Pic de tas **par phase** : COMPUTING_STATS **199** ; BUILDING_DETAILS 105 ; READING_SUPPORTS 98 ;
COMPUTING_FREQUENCIES 91 ; READING_STATIONS 73 ; INSTALLING 48 ; FINALIZING 47 ; INSERTING 44 ;
DOWNLOADING 21 ; RESOLVING 20.

**Ce que ca change, encore :**

1. **Le coupable memoire est R3, pas R1 ni R2.** Un build mobile seul monte AUSSI haut que le
   build complet (199 vs 187 Mo — l'ecart est du bruit de GC), et le pic tombe pendant
   COMPUTING_STATS, une phase qui ne pese que 10 % de la duree. Ce sont bien les deux
   `HashMap<Triple, MutableSet<String>>` d'`AnfrStatsBuilder`.
2. **Rendre le gate « par pack » n'ouvre RIEN.** L'hypothese « la radio est le poste lourd » est
   fausse : sans elle, c'est aussi lourd. S6 seul ne sert pas la cause des petits telephones.
3. **Le vrai plancher est ailleurs** : une fois R3 traite, le point haut suivant est
   BUILDING_DETAILS a 105 Mo, puis READING_SUPPORTS a 98 Mo — c'est-a-dire R2, l'accumulateur
   `stations`. Autrement dit le chemin est : R3 (ouvre le 4 Go), puis R2 (vise le 3 Go).
4. **La duree est un probleme distinct de la memoire** : BUILDING_DETAILS pese 15 min sur 28
   (54 %) mais ne coute que 105 Mo. Optimiser la RAM n'accelerera pas le build, et inversement.

**Fait le 2026-08-06 — R3 traite.** `AnfrStatsBuilder` compte desormais via une table de staging
(`stg_stat_pair`) et `COUNT(DISTINCT)`, comme `DepartmentStatsBuilder`. Les deux `HashMap` ont
disparu. Sortie **prouvee identique** par les instantanes (goldens inchanges au bit pres).

#### Mesure apres R3 — meme appareil, mobile seul, 2026-08-06

| Poste | Avant | Apres |
|---|---|---|
| Pic tas Java | 199 Mo (78 %) — COMPUTING_STATS | **114 Mo (44 %) — BUILDING_DETAILS** |
| Pic de COMPUTING_STATS | 199 Mo | **73 Mo** |
| Pic stockage | 1021 Mo | 1021 Mo (inchange) |
| Base produite | 146 Mo | 146 Mo (inchange) |
| Duree | 28 min 08 s | 34 min 25 s |

**-43 % sur le pic de tas.** Le point haut est desormais BUILDING_DETAILS (114 Mo) puis
READING_SUPPORTS (108 Mo) : c'est-a-dire **R2**, l'accumulateur `stations`, comme prevu.

Les +22 % de duree ne sont **pas** imputables au changement : ils frappent uniformement des phases
qui s'executent AVANT le code touche et qui n'ont pas ete modifiees (READING_SUPPORTS 32 437 ->
25 595 lignes/s, COMPUTING_FREQUENCIES 15 035 -> 12 096, BUILDING_DETAILS 109 -> 89 ; rapport
constant ~0,80). Signature d'un bridage thermique, pas d'une regression algorithmique — le
troisieme build de plus de 30 min d'affilee sur le meme telephone. Le cout reel du staging SQL se
lit sur la phase concernee : COMPUTING_STATS 2 min 55 -> 3 min 12, soit **+17 s**. A confirmer sur
un appareil froid.

#### Mesure apres S3 + liberation des sources — mobile seul, 2026-08-06

| Poste | Avant R3 | Apres R3 | Apres S3 |
|---|---|---|---|
| Pic tas Java | 199 Mo (78 %) | 114 Mo (44 %) | **110 Mo (42 %)** — READING_SUPPORTS |
| Pic stockage simultane | 1021 Mo | 1021 Mo | **828 Mo** |
| Base produite | 146 Mo | 146 Mo | **146 Mo** |
| Duree | 28 min 08 s | 34 min 25 s | **28 min 44 s** |

**-193 Mo de stockage**, conforme a la prevision (-192). La composition du pic bascule comme
prevu : « travail 62 Mo (ZIP) + staging 765 Mo » au lieu de « sources 255 Mo + base en cours
765 Mo ». La duree revient a 28 min : les +22 % du run precedent etaient bien **thermiques**, pas
le fait de R3 — confirme.

**Le fichier produit reste a 146 Mo**, alors que je pariais sur ~139. Donc les 7 Mo d'ecart avec la
base serveur ne venaient PAS des pages du staging : c'est le taux de remplissage normal des pages
B-tree d'une base construite par inserts, que le VACUUM du serveur repacke. On pourrait le
recuperer par un `VACUUM` final (le fichier ne fait que 146 Mo a ce moment-la, le staging est deja
rendu), mais pour 7 Mo et quelques dizaines de secondes, ca ne vaut pas le detour.

#### Mesure apres S3 — build COMPLET (tous packs), 2026-08-06

| Poste | Reference (avant tout) | Apres R3 + S3 |
|---|---|---|
| Pic tas Java | 187 Mo (73 %) | **114 Mo (44 %)** |
| Pic stockage simultane | 1350 Mo | **1157 Mo** |
| Duree totale | 43 min 07 s | **58 min 56 s** |
| Base radio produite | 20 Mo | 23 Mo (sans VACUUM) |

Memoire et disque tiennent leurs promesses. **La duree, non : +37 %.** Le coupable est isole et
c'est une regression introduite ici : `COMPUTING_STATS` passe de **2 min 23 s a 12 min 45 s**, pour
un travail rigoureusement identique (les stats ne portent que sur la base mobile). Les autres
phases sont stables a la mesure pres (BUILDING_DETAILS 109 lignes/s dans les deux runs), ce qui
exclut un simple bridage thermique.

Cause : le `COUNT(DISTINCT ...) GROUP BY ...` de la nouvelle agregation SQL. Sur un build mobile
seul il ne coutait que +26 s ; sur un build complet, quatre bases sont ouvertes simultanement
(mobile + son staging, radio + le sien) et se disputent le cache de pages — les tables temporaires
que SQLite alloue par `DISTINCT` deviennent alors ruineuses.

**Corrige le 2026-08-06** : plus de `DISTINCT` ni de `GROUP BY`. L'index de staging devient
**couvrant** et l'agregation se fait en **un scan ordonne dedoublonne en flux** cote Kotlin
(`countGroups`), a memoire constante et sans table temporaire. Goldens inchanges. A remesurer.

`RADIO_BUILDING` passe aussi de 14 min 19 s a 18 min 01 s (2403 vs 3026 lignes/s) **malgre** la
suppression du VACUUM. Le rapport de 0,79 est la signature d'un bridage : cette phase tourne entre
la 40e et la 59e minute d'un build continu. A reverifier sur un appareil froid avant d'en conclure
quoi que ce soit.

#### Mesure finale — build COMPLET apres correction du comptage, 2026-08-06

| Poste | Reference (avant tout) | Final |
|---|---|---|
| Pic tas Java | 187 Mo (73 %) | **112 Mo (43 %)** |
| Pic stockage simultane | 1350 Mo | **1157 Mo** |
| Duree totale | 43 min 07 s | **44 min 47 s** |
| COMPUTING_STATS | 2 min 23 s (199 Mo) | **2 min 32 s (69 Mo)** |

La regression est effacee : le comptage en flux rend la phase des stats a son temps d'origine
(+9 s) tout en consommant **130 Mo de tas en moins**. `FINALIZING` repasse de 47 s a 0 s — meme
cause, memes tables temporaires. Le total revient a 44 min 47 s.

Bilan mobile seul : **110 Mo de tas, 828 Mo de stockage, 28 min 44 s**.

Point non tranche : `RADIO_BUILDING` reste au-dessus de sa reference (16 min 45 s contre
14 min 19 s), alors que le VACUUM a disparu. Mais entre deux runs au code radio **identique** on a
deja observe 18 min 01 s puis 16 min 45 s, soit 7,5 % d'ecart de pur bruit. Le surcout reel du
staging attache sur cette phase est donc quelque part entre 0 et 2 min 30, et il faudrait plusieurs
runs pour le mesurer. Non prioritaire.

#### Ou on en est vis-a-vis de l'objectif

- **4 Go : atteint cote memoire.** 114 Mo sur un plafond de 192 Mo (`memoryClass` typique d'un
  4 Go) = 59 %, marge reelle. Un 4 Go plafonne a 128 Mo reste hors jeu (89 %).
- **Le blocage restant pour ces appareils est le DISQUE** : 1021 Mo de pic, gate declare a 1 Go.
- **Etape suivante pour descendre plus bas** : R2 (`stations`), qui porte les deux paliers a
  108-114 Mo. Puis S3 pour le disque.

Attention en relisant le point 3.2 : les pics par phase mesurent le tas **occupe** (donc dechets
non encore ramasses compris), pas seulement la memoire retenue. Pour R3 les deux convergent — le
code retient bel et bien 1 a 3 millions d'entrees — mais une phase a fort taux d'allocation peut
afficher un pic eleve sans rien retenir.

A produire : un **rapport de build** persistant (prefs + affichage dans
`DiagnosticScreen`, ligne par ligne, plus une copie logcat en debug) contenant :

- par phase (`BuildPhase`) : duree, lignes traitees, debit lignes/s ;
- pic heap Java (`Runtime.totalMemory() - freeMemory()`, echantillonne toutes les
  2 s par une coroutine dediee) et `Debug.MemoryInfo.getTotalPss()` en fin de phase ;
- `am.memoryClass` / `largeMemoryClass` de l'appareil, `isLowRamDevice`, nb de coeurs ;
- pic disque du repertoire de travail + taille de chaque source + **taille finale de
  `geotower_fr.db` et `geotower_fr_radio.db`, comparee a celle des bases telechargees** ;
- nombre de GC / temps GC si accessible ;
- packs demandes, resultat, cause d'echec.

Etendre `DbOperationTimings` (aujourd'hui : un seul chrono global,
`DbOperationTimings.kt:31`) avec une variante par phase, ou ajouter un
`LocalBuildReport` dedie. Ne rien logger de sensible (aucune position, aucune URL
utilisateur).

**Critere de sortie** : trois mesures completes sur trois appareils differents (un
haut de gamme, un milieu de gamme, un 4 Go si disponible), packs « tout » et packs
« mobile seul ». Ce sont les chiffres de reference de tout le reste du plan.

### S1 — Gains sans changement d'architecture

Faible risque, gain immediat, aucune modification de la logique metier.

1. **Tuer le boxing d'insertion.** Ajouter a `SqlDatabase` une variante
   `insertBatch(sql, count) { index, binder -> ... }` ou le binder ecrit directement
   dans le `SQLiteStatement` (`bindString`/`bindLong`/`bindDouble`/`bindNull`), sans
   `List<Any?>` ni boxing. Migrer les trois `BatchInserter`. Garder l'ancienne
   signature pour les petits inserts (referentiels, metadata) et les tests JDBC.
2. **Ligne CSV en flyweight.** Reutiliser un unique `AnfrCsvRow` + son tableau de
   cellules par fichier dans `CsvRowIterator` (`AnfrSourceReaders.kt:210`). Contrat a
   documenter en gras : **la ligne n'est valide que jusqu'au `next()` suivant**, aucun
   consommateur ne doit la conserver. Verifier les deux consommateurs actuels
   (`GeoTowerDbBuilder` et `RadioDbBuilder.RadioStagingSink` via le tee) : ils lisent
   tout immediatement, c'est bon.
3. **Un `Deflater` par thread.** Transformer `FrequencyDetailsEncoder` en encodeur
   avec instance reutilisable (`reset()` entre les appels, `end()` a la fermeture),
   plus un `ByteArrayOutputStream` reutilise. Conserver l'API statique actuelle pour
   les tests.
4. **Une seule passe de decodage des details.** Fusionner la lecture de
   `AnfrStatsBuilder.populateCurrentStats` et de `DepartmentStatsBuilder.fillStaging`
   en un scan unique qui decode `details_frequences` une fois et alimente les deux
   agregateurs. Les deux requetes lisent deja quasiment les memes colonnes.
5. **ARCEP direct en staging.** Supprimer `parseArcepFiles`
   (`LocalDbBuildPipeline.kt:334`) : ecrire dans `stg_arcep` au fil de la lecture des
   CSV, puis supprimer les CSV. La fusion nidt/is_zb devient un `INSERT ... ON
   CONFLICT DO UPDATE` (ou un `GROUP BY` au moment de la jointure finale). Supprime R5.
6. **Liberer les sources des qu'elles sont lues** : `observatoire.csv` juste apres la
   boucle hebdomadaire, les CSV ARCEP apres leur staging, le ZIP SUP apres la phase 8.
   Gain direct sur le pic disque, donc sur le seuil de stockage exigible.
7. **Pragmas de build.** Avant toute creation de table :
   `PRAGMA page_size = 8192` (tester 4096 / 8192 / 16384),
   `PRAGMA locking_mode = EXCLUSIVE`, `PRAGMA auto_vacuum = NONE`. Reevaluer
   `temp_store = FILE` (`GeoTowerDbBuilder.kt:88`) : c'est prudent, mais mesurer le
   cout sur les rares tris qui subsistent. `cache_size` est deja adaptatif
   (`LocalDbBuildPipeline.kt:381`) — le rendre fonction du **heap dispo** et non de la
   RAM totale n'a pas de sens (c'est de la memoire native), mais le borner plus bas
   sur `isLowRamDevice` est raisonnable.
8. **Lots plus gros** (20 000-50 000) une fois le boxing supprime, et une seule
   transaction par table de staging plutot qu'une par lot.
9. **Cles primaires du staging.** Tester le chargement dans des tables sans PK puis
   dedup/index apres coup, pour `stg_antenne` (millions de lignes) et
   `stg_r_support`. A ne garder que si la mesure le confirme.

**Gain attendu** (a confirmer) : -20 a -40 % de duree, -30 a -60 Mo de heap (surtout
via la pression GC), plusieurs centaines de Mo de pic disque en moins.

### S2 — Sortir les gros accumulateurs du heap

C'est la tranche qui debloque les petits telephones. Trois refontes ciblees.

1. **R2 — `stations`.** L'identifiant ANFR est numerique et normalise sur 10 chiffres
   (`AnfrParsing.kt:20`) : il tient dans un `Long`. Remplacer
   `LinkedHashMap<String, StationAcc>` par une **table de hachage a adressage ouvert
   sur primitives** : `LongArray` de cles + `IntArray` de masques tech/band +
   `IntArray` de drapeaux (has_active, index). Tous les **champs texte** (adresse,
   dates, azimuts, code_insee, libelles) partent directement dans `stg_station_final`
   au fil de l'eau (upsert), au lieu d'etre gardes en RAM jusqu'a la phase 9. Prevoir
   un repli pour les rares identifiants non numeriques (map secondaire, quelques
   entrees). Cible : **< 15 Mo** pour 300 000 stations.
2. **R1 + R4 — agregats radio.** Meme principe : ne garder en RAM que
   `serviceMask`/`systemMask`/compteurs, dans une map primitive indexee par un
   `(sta, sup)` compacte. Les listes d'echantillons (`freqSamples` 24 entrees,
   `systems`, `AntennaInfo.samples` 12 entrees) ne servent qu'a construire le texte
   de detail au moment de l'emission : les produire par un **scan ordonne**
   (`ORDER BY sta, sup` sur index couvrant, meme technique que `applyDetails`) au lieu
   de les accumuler. Min/max/compte de frequences se calculent en SQL (`MIN`, `MAX`,
   `COUNT`). Cible : **< 20 Mo**.
3. **R3 — stats courantes.** Remplacer les deux `HashMap<Triple, Set<String>>` par une
   table de staging `stg_stat_pairs(operator, category, item, support_key, active)` +
   un `SELECT operator, category, item, COUNT(DISTINCT support_key), COUNT(DISTINCT
   CASE WHEN active THEN support_key END) ... GROUP BY 1,2,3`. Attention : le
   `DISTINCT` est **necessaire** (un meme `id_support` peut apparaitre sous plusieurs
   `id_anfr` — sites mutualises), c'est justement pour cela qu'un simple compteur ne
   suffit pas. SQLite fait ce dedup sur disque sans toucher au heap.

**Gain attendu** : pic heap du build complet ramene sous ~64-96 Mo, soit dans le
budget d'un appareil 3-4 Go. C'est la condition technique pour baisser le gate.

### S3 — Staging dans un fichier separe (`ATTACH`) — **FAIT le 2026-08-06**

**Correction apportee par la mesure** : contrairement a ce qui etait ecrit ci-dessous, S3 **ne
reduit pas le pic de stockage**. Au moment du pic (1021 Mo, pendant COMPUTING_FREQUENCIES) la
composition est « sources 255 Mo + staging 765 Mo » : ces 765 Mo existent a l'identique dans un
fichier annexe. Ce que S3 apporte reellement : le fichier installe ne porte plus les pages du
staging, le staging se rend en supprimant un fichier, et surtout **le VACUUM de la base radio
disparait** (reecriture integrale d'un fichier de plusieurs centaines de Mo, plusieurs minutes sur
un telephone).

Ce qui fait vraiment tomber le pic, c'est de **rendre les sources des qu'elles sont lues** :
l'observatoire (173 Mo) n'est lu que par la phase 1, les CSV ARCEP (19 Mo) sont consommes avant le
build. Les deux sont desormais supprimes sur-le-champ, soit **-192 Mo attendus sur le pic**.

Implementation : `SqlDatabase.stagingPrefix` porte le qualificatif de schema ; seule la **DDL** du
staging est qualifiee, les lectures/ecritures restent non qualifiees (SQLite resout dans `main`
puis les bases attachees, et `main` n'a jamais de table `stg_*`). `AndroidSqlDatabase.withStagingFile`
attache le fichier ; `GeoTowerDbBuilder.build(onWeeklyConsumed = …)` signale la fin de lecture de
l'observatoire. Un instantane supplementaire (`attachedStagingProducesTheSameDatabases`) rejoue les
deux builds avec staging attache et exige **les memes goldens**.

ATTENTION APPAREIL : un ATTACH est propre a une **connexion**. Ce n'est sur que parce que le build
tourne hors WAL (`journal_mode = OFF`), ce qui reduit le pool de connexions d'Android a une seule.
Repasser le build en WAL casserait le staging avec un « no such table ».

#### Redaction d'origine (conservee)

Creer le staging dans une base annexe (`local_db_build/staging.db`) attachee par
`ATTACH DATABASE ... AS stg`, et prefixer les tables `stg.` :

- la base finale ne contient **jamais** de pages de staging -> fichier installe de la
  taille reelle des donnees, comparable a la base telechargee ;
- le staging se libere en **supprimant un fichier**, instantanement, avant
  l'installation — au lieu d'un `DROP TABLE` qui ne rend rien ;
- le `VACUUM` de la base radio (`RadioDbBuilder.kt:388`) devient inutile : suppression
  pure du pic « 2x la taille du fichier » et de plusieurs dizaines de secondes ;
- pragmas differencies possibles (staging agressif, base finale prudente).

Points de vigilance : les jointures inter-bases fonctionnent nativement, mais **tout
le SQL du builder doit etre prefixe** de facon coherente ; garder un mode « meme
fichier » pour les tests JVM (`JdbcSqlDatabase`) ou introduire le prefixe comme
parametre du builder. Le staging doit etre sur le **meme volume** que la base.

### S4 — Parallelisme mesure

Uniquement apres S1/S2 (paralleliser du code qui alloue trop ne fait que deplacer le
probleme sur le GC).

1. **Producteur/consommateur parse -> insert.** Un thread lit et decoupe le CSV/ZIP,
   un thread ecrit dans SQLite, relies par une `ArrayBlockingQueue` de **lots deja
   extraits** (tableaux de primitives et de `String`, surtout pas de `AnfrCsvRow`
   flyweight : cf. contrat S1.2). SQLite reste **mono-ecrivain**. Gain attendu sur les
   phases d'ingestion : 1,4 a 1,8x.
2. **Compression parallele.** La phase `BUILDING_DETAILS` est du CPU pur et
   embarrassingly parallel : dispatcher l'encodage `Z1:` sur `min(3, coeurs-1)`
   threads, l'insertion restant sur le thread ecrivain.
3. **Telechargements concurrents / recouverts.** Deux pistes, par ordre de gain :
   (a) lancer le telechargement de l'observatoire **pendant** l'ingestion de
   `SUP_BANDE` (qui ne depend pas des stations) ; (b) a defaut, les deux
   telechargements en parallele. Conserver strictement la logique de retry et de
   verification de completude existante (`LocalDbBuildPipeline.kt:94-171`) : c'est
   elle qui rattrape les coupures reseau.

Garde-fous : n'activer le parallelisme que si `availableProcessors() >= 4`, si le
statut thermique est normal (cf. S5) et si `PowerProfile` ne demande pas le mode
econome. Un flag distant doit pouvoir le desactiver.

### S5 — Thermique et energie

Sur un petit telephone, un build a fond finit throttle : il dure 3x plus longtemps
**et** consomme plus. Moins de cycles (S1-S4) est le premier levier ; ensuite :

- ecouter `PowerManager.addThermalStatusListener` (API 29+) : a partir de
  `THERMAL_STATUS_MODERATE`, retomber a un seul thread ; a `SEVERE`, inserer des
  pauses ; a `CRITICAL`, suspendre et reprendre a froid (avec la phase affichee dans
  la notification, pour que l'utilisateur comprenne).
- brancher `PowerProfile` / `AppConfig.lowPowerLevel` (aujourd'hui l'infra existe mais
  les call sites ne la lisent pas, cf. memoire projet) : niveau 2 = mono-thread +
  exiger le chargeur.
- option utilisateur « demarrer quand le telephone charge » (contrainte WorkManager
  `setRequiresCharging`, en plus de `NetworkType.CONNECTED`), non imposee.
- le wake lock (`LocalDbBuildWorker.kt:87`) reste necessaire ; son plafond de 4 h
  devra etre revu a la baisse une fois les durees reelles connues.

### S6 — Nouveau modele d'eligibilite — **FAIT le 2026-08-07**

`LocalBuildCapability` decide desormais sur les budgets **mesures** :

- critere memoire sur `ActivityManager.getMemoryClass()` (le vrai plafond) et non sur `totalMem` ;
  112 Mo de pic mesure + 40 % de marge = **157 Mo de tas exiges** ;
- budget disque **par pack** : 828 Mo (mobile seul) / 1157 Mo (tous packs) + 25 % de marge — le
  pack radio seul reste une **estimation** (~600 Mo), faute de mesure ;
- `isLowRamDevice` reste bloquant : imposer 30 a 45 min de service au premier plan a un appareil
  que le systeme declare contraint n'a pas de sens ;
- la carte de reglages evalue l'eligibilite **des packs coches** et se met a jour a chaque case :
  un appareil trop juste pour tout generer voit qu'il peut generer le mobile seul ;
- cout annonce avant lancement (espace necessaire), et **« Tenter quand meme »** pour les
  appareils sous les seuils — l'echec est sans danger (fichier temporaire, base active intacte),
  il ne coute que du temps et des donnees. Le drapeau descend jusqu'au pipeline
  (`LocalDbBuildWorker.KEY_FORCE`) qui saute alors le refus.

Effet attendu : la generation s'ouvre a tout appareil dont le tas atteint ~160 Mo, ce qui couvre
l'essentiel du parc 4 Go — la ou l'ancien seuil de 6 Go les excluait tous par principe.

### Redaction d'origine de S6

Remplacer le couple `RAM >= 6 Go` / `1 Go libre` par un budget **par pack**, derive
des mesures S0 :

```
besoinHeap(packs)   = base + (mobile ? Hm : 0) + (radio ? Hr : 0)
besoinDisque(packs) = base + (mobile ? Dm : 0) + (radio ? Dr : 0)
eligible = !isLowRamDevice
        && memoryClassMo >= besoinHeap(packs) * MARGE
        && stockageLibre >= besoinDisque(packs) * MARGE
```

Consequences UI (`LocalDbBuildCard.kt:97`, `LocalModeScreen`, onboarding) :

- l'eligibilite devient **par pack** : un appareil qui ne peut pas tout faire peut
  souvent generer **le mobile seul** (le pack radio est le plus gourmand, R1) — c'est
  la porte d'entree la plus rapide pour les petits telephones, disponible des S2 ;
- afficher les couts estimes du pack choisi (data, espace, duree), calcules a partir
  des mesures reelles et non de constantes inventees ;
- pour les appareils juste sous le seuil : bouton « tenter quand meme » avec
  avertissement clair. L'echec est sans danger (build dans un fichier temporaire, base
  active jamais touchee) — l'utilisateur ne risque que du temps et de la data ;
- kill-switch distant conserve, et pouvant cibler un palier (ex. couper le pack radio
  sur les appareils sous X Mo de heap) ;
- vocabulaire : ne jamais presenter cela comme une economie de data (cf. plan initial
  section 2 et memoire projet sur le vocabulaire des pannes).

**Reprise apres mort du process.** Sur un petit telephone, la probabilite d'etre tue
augmente : ajouter le checkpoint par phase prevu au plan initial (section 10) mais
jamais implemente — marqueur de phase persistant + sources conservees, reprise a la
derniere phase terminee. Sans cela, un build de 40 min tue a 90 % repart de zero, ce
qui est le pire scenario possible sur la cible visee.

### S7 — Exploratoire : reduire la source elle-meme

L'observatoire est le plus gros telechargement du pack mobile
(`OfficialSources.kt:48`, plafond a 512 Mo dans `LocalDbBuildPipeline.kt:434`) alors
que le builder n'en lit que ~8 colonnes (`sta_nm_anfr`, `coordonnees`, `adm_lb_nom`,
`statut`, `generation`, `emr_lb_systeme`, `emr_dt`, `date_maj`).

A tester : l'endpoint d'export Opendatasoft avec selection de colonnes
(`/api/explore/v2.1/catalog/datasets/observatoire_2g_3g_4g/exports/csv?select=...&delimiter=;`).
Si la plateforme l'honore, le telechargement peut fondre — gain simultane sur la data,
la duree et le disque. **Repli obligatoire sur le CSV statique actuel** si la reponse
est vide, incomplete, ou si le nombre de lignes s'ecarte de la reference : cette
source est deja documentee comme capricieuse (« l'API d4c/records ne renvoie qu'une
erreur », `OfficialSources.kt:46`). A n'activer que derriere un flag, apres comparaison
ligne a ligne du resultat du build.

---

## 5. Filet de securite : test « sortie identique » — **FAIT**

Indispensable avant S1. Sans lui, une refonte de cette ampleur casse la parite avec le
builder serveur sans que personne ne s'en apercoive.

**Livre le 2026-08-05** : `BuilderOutputSnapshotTest` + `CanonicalDump` + `BuildSnapshotFixture`,
goldens dans `app/src/test/resources/golden/`. La fixture est ecrite en **vrais fichiers CSV/ZIP**,
donc l'instantane traverse aussi le lecteur (detection d'encodage et de separateur, champ entre
guillemets contenant un `;`, accents) : c'est ce qui protegera le passage de `AnfrCsvRow` en
flyweight (S1.2). Trois instantanes : base mobile, base radio, et le **chemin reel de l'appareil**
(build mobile qui « tee » vers le staging radio). Deux invariants supplementaires y sont figes :
le build radio autonome et le build par tee produisent **exactement** la meme base, et brancher le
sink radio ne change **rien** a la base mobile.

Le dump couvre `sqlite_master` en entier (donc la DDL et l'absence de table de staging oubliee),
`PRAGMA user_version`, et chaque ligne de chaque table avec sa classe de stockage.

- Ajouter aux tests JVM existants (`GeoTowerDbBuilderTest`, `RadioDbBuilderTest`,
  `RadioMutualizedBuildTest`, `DepartmentStatsBuilderTest`) un **dump canonique** :
  toutes les tables finales, colonnes ordonnees, lignes triees, serialisees puis
  hachees (SHA-256). Le hash devient une constante du test.
- Toute optimisation doit laisser le hash **inchange**. S'il change, c'est soit un bug,
  soit un changement fonctionnel a assumer explicitement (et a repercuter cote
  serveur).
- Completer les fixtures pour couvrir ce que les optimisations risquent de casser :
  identifiant ANFR non numerique (repli de la map primitive, S2.1), site mutualise
  (dedup `COUNT(DISTINCT)`, S2.3), ligne CSV avec guillemets (flyweight, S1.2),
  station sans antenne, support partage entre deux stations.
- Test instrumente sur appareil : build complet, assert sur pic heap et pic disque
  sous les budgets annonces (les valeurs de S6), pour que la regression soit detectee
  avant publication.

---

## 6. A ne pas faire

- Ne pas casser la parite avec `docs/server/build_fr_anfr_db.py` : le format produit
  est un contrat (schema 7, identity_hash, `user_version`, codec `Z1:`).
- Ne pas ajouter de `@Index` Room ni de colonne : la base est prebatie, le hash de
  schema est fige (cf. memoire projet).
- Ne pas activer `android:largeHeap` en premiere intention : c'est global a
  l'application, cela degrade la pression memoire de toute l'app et masque le vrai
  probleme. A ne considerer qu'en dernier recours, et alors couple a un **process
  dedie** (`androidx.work:work-multiprocess`, `RemoteCoroutineWorker`) pour que le pic
  du build n'entre jamais en concurrence avec l'UI et soit rendu integralement a la
  fin. A decider sur mesures, pas par principe.
- Ne pas decouper le build par departement / par zone : le staging SQL joue deja ce
  role, et le decoupage multiplierait les passes sur les memes fichiers.
- Ne pas paralleliser les **ecritures** SQLite : un seul thread ecrivain.
- Ne pas supprimer les garde-fous existants (allowlist d'hotes, plafonds de taille,
  verification de completude, validation avant installation) au nom de la vitesse.
- Ne pas remplacer le SQLite du framework par une distribution embarquee : gain
  incertain, taille d'APK et surface de risque certaines.
- Ne pas presenter la generation locale comme une economie de donnees.

---

## 7. Ordre d'implementation et criteres d'acceptation

| Tranche | Contenu | Risque | Debloque |
|---|---|---|---|
| S0 | Instrumentation — **code fait**, mesures a lancer | nul | tout le reste |
| Filet | Dump canonique + fixtures — **fait** | nul | S1-S4 |
| S1 | Allocations, pragmas, sources liberees, ARCEP en staging | faible | duree, disque |
| S2 | Accumulateurs primitifs / SQL (R1, R2, R3, R4) | moyen | **le gate RAM** |
| S3 | Staging en fichier attache | moyen | taille finale, disque, VACUUM |
| S4 | Parallelisme parse/insert, compression, telechargements | moyen | duree |
| S5 | Thermique + `PowerProfile` | faible | batterie, stabilite |
| S6 | Eligibilite par pack, UI, reprise apres kill | faible | **les petits telephones** |
| S7 | Source observatoire reduite | eleve (source tierce) | data, duree |

Criteres d'acceptation :

- le dump canonique est identique avant/apres, sur toutes les fixtures ;
- pic heap mesure du build complet **divise par au moins deux** par rapport a la
  reference S0, et compatible avec un `memoryClass` de 128 Mo ;
- taille du `geotower_fr.db` genere localement **egale, a quelques pour cent pres, a
  celle de la base telechargee** ;
- pic disque mesure et **annonce** a l'utilisateur avant lancement, par pack ;
- duree totale reduite d'au moins 30 % sur le meme appareil, sans surchauffe
  (statut thermique restant sous `SEVERE`) ;
- un appareil 4 Go non `lowRam` genere le pack mobile de bout en bout, valide, et la
  base s'ouvre dans Room sans migration ;
- un build tue en cours de route reprend, ou echoue proprement sans laisser de base
  corrompue ni de fichier de travail orphelin.

---

## 8. Prompt court pour l'IA qui implementera

Tu dois rendre la generation locale des bases GeoTower assez frugale pour qu'un
telephone 3-4 Go puisse la faire, sans changer d'un octet le fichier produit. Lis
`docs/agent-ia-plan-generation-locale-db-geotower-2026-07-08.md` puis
`data/build/GeoTowerDbBuilder.kt`, `RadioDbBuilder.kt`, `AnfrStatsBuilder.kt`,
`DepartmentStatsBuilder.kt`, `AnfrSourceReaders.kt`, `AndroidSqlDatabase.kt`,
`LocalDbBuildPipeline.kt`, `LocalBuildCapability.kt`, `LocalDbBuildWorker.kt`.
**Commence par S0** (instrumentation : duree, pic heap, pic disque, taille des
fichiers produits, par phase) et par le **dump canonique** des tables finales dans les
tests JVM existants : aucune optimisation ne doit modifier ce hash. Puis, dans
l'ordre : supprime les allocations par ligne (boxing d'insertion, ligne CSV
flyweight, `Deflater` reutilise, une seule passe de decodage des details, ARCEP
directement en staging, sources supprimees des qu'elles sont lues, pragmas de build) ;
remplace les trois gros accumulateurs RAM (`stations`, agregats radio, ensembles de
stats) par des structures primitives ou du SQL de staging ; deplace le staging dans
une base attachee separee pour que le fichier installe ne porte plus les pages de
staging et que le `VACUUM` radio disparaisse ; ajoute un parallelisme borne
(parse/insert, compression, telechargements recouverts) conditionne au nombre de
coeurs, au statut thermique et a `PowerProfile`. **Enfin seulement**, remplace le gate
`RAM >= 6 Go / 1 Go libre` par un budget **par pack** derive des mesures reelles
(`memoryClass` et non `totalMem`), rends le pack mobile accessible seul aux appareils
modestes, affiche les couts estimes, ajoute « tenter quand meme » et la reprise apres
mort du process. Ne touche pas au schema, ne mets pas `largeHeap`, ne paralellise pas
les ecritures SQLite, et garde tous les garde-fous reseau et de validation existants.
