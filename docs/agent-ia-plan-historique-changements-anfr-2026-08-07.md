# Historique hebdomadaire des changements ANFR (script serveur)

Cahier des charges pour une IA / agent de code. Perimetre de ce lot : **un seul
script serveur**, `docs/server/build_site_changes.py`, plus ses tests et son cron.
**Aucune modification de l'app Android dans ce lot.**

Le script compare la publication hebdomadaire de l'observatoire ANFR a la
precedente et ecrit, chaque semaine, deux fichiers : une archive exhaustive de
tous les changements, et un fichier leger destine a une future carte des sites
qui ont bouge.

## 0. Pourquoi c'est urgent alors que l'app ne l'utilise pas encore

**L'historique n'est pas retroactif.** Le serveur ne conserve aujourd'hui que la
publication courante. Chaque semaine qui passe sans archive est perdue
definitivement : aucun travail ulterieur ne permettra de savoir ce qui a change
entre juillet et aout 2026 si personne ne l'a note a ce moment-la.

C'est la seule raison de livrer le script avant l'interface qui l'exploitera.
L'ordre inverse (interface d'abord) couterait des mois d'historique.

---

## 1. Perimetre

Dans ce lot :

- `docs/server/build_site_changes.py` — le script.
- `docs/server/test_build_site_changes.py` — tests `unittest`, meme style que
  `test_build_sites_hs.py` et `test_fr_dept_stats.py`.
- une entree cron quotidienne.

**Hors de ce lot**, a ne pas anticiper dans le code :

- la base SQLite d'historique distribuee aux telephones ;
- la page carte dans l'app et son popup ;
- la timeline dans la fiche site (`docs/agent-ia-plan-timeline-site-geotower-2026-06-19.md`) ;
- le diff de l'export SUP mensuel (azimut, hauteur, bandes, FH) ;
- l'archivage optionnel du CSV cote telephone.

Ces lots consommeront les fichiers produits ici. Le format de sortie doit donc
etre stable et documente, mais rien d'autre ne doit etre construit maintenant.

---

## 2. Decisions actees

| Sujet | Decision |
|---|---|
| Source comparee | Observatoire hebdomadaire ANFR (celui que `fr_anfr_stats.py` lit deja) |
| Profondeur | **Toutes** les colonnes du fichier, valeurs brutes avant/apres |
| Unite de suivi | Station `sta_nm_anfr`, indexee aussi par `sup_id` |
| Sources conservees | Paire glissante : publication courante + precedente, gzippees |
| Archive de sortie | Un JSONL gzippe par publication, **jamais purge** |
| Fichier carte | Un point par support, codes de changement, pas de phrase |
| Retention | Illimitee cote serveur |
| Declenchement | Cron **quotidien** qui ne fait rien si la version n'a pas change |
| Integration build | Script independant, **hors** de `build_all_db.py` |

---

## 3. La source

L'observatoire est un CSV unique, separateur `;`, avec BOM. Etat constate sur le
serveur le 2026-08-07 :

```
/opt/geotower/data/imports/france_sources/20260806174318_observatoireod_20260806.csv
181 720 708 octets, une seule publication presente
```

Le nom suit `<horodatage>_observatoireod_<date_donnees>.csv` : c'est le **second**
groupe de chiffres qui porte la date des donnees. Le fichier est dans
`france_sources/`, donc dans l'arborescence balayee **en recursif** par
`discover_weekly_csv_files` — voir l'avertissement de la section 4.

Il est deja localise et ouvert par des helpers existants a **reutiliser tels
quels** plutot qu'a reecrire :

- `discover_weekly_csv_files(imports_dir, ())` (`fr_anfr_stats.py:490`) — trouve les
  candidats dans `imports/` et `imports/france_sources/`, y compris dans des ZIP ;
- `open_csv(source)` (`fr_anfr_stats.py:438`) — gere l'encodage en essayant
  `utf-8-sig`, `cp1252`, puis `latin-1` ;
- `clean_text`, `normalize_id_anfr`, `parse_coordinates` (`build_fr_anfr_db.py:106`,
  `:110`, `:149`) — normalisation identique a celle du builder.

### 3.1 Identite d'une ligne

Une station apparait en **plusieurs lignes**, une par systeme declare. L'identite
d'une ligne est donc le triplet :

```
(sta_nm_anfr, adm_lb_nom, emr_lb_systeme)
```

C'est ce triplet qui permet de distinguer *"Orange a allume la 5G sur ce site"*
d'un simple changement de valeur sur une ligne existante. Un diff qui ignorerait
le systeme produirait des changements incomprehensibles sur les sites
multi-technos, c'est-a-dire la quasi-totalite du parc.

Le fichier ne porte **aucun identifiant de ligne stable** : ce triplet est la
seule cle disponible. Les doublons exacts (meme triplet, memes valeurs) sont
fusionnes, pas comptes deux fois.

### 3.2 Colonnes

**Ne pas figer la liste des colonnes suivies.** Le script lit l'en-tete et suit
*toutes* les colonnes presentes. Une colonne ajoutee par l'ANFR doit apparaitre
dans le diff sans modification du code, et ne doit jamais faire planter le script.

En-tete reel de la publication du 2026-08-06, 22 colonnes :

```
id;adm_lb_nom;sup_id;emr_lb_systeme;emr_dt;sta_nm_dpt;code_insee;generation;
date_maj;sta_nm_anfr;nat_id;sup_nm_haut;tpo_id;adr_lb_lieu;adr_lb_add1;
adr_lb_add2;adr_lb_add3;adr_nm_cp;com_cd_insee;coordonnees;coord;statut
```

Seules les colonnes de **structure** sont nommees, avec des alias tolerants sur le
modele de `WEEKLY_COLUMN_ALIASES` (`fr_anfr_stats.py:108`) :

| Role | Colonne |
|---|---|
| Station | `sta_nm_anfr` |
| Operateur | `adm_lb_nom` |
| Systeme | `emr_lb_systeme` |
| Support | `sup_id` |
| Coordonnees | `coordonnees`, repli `coord` |
| Departement | `sta_nm_dpt`, repli sur le prefixe de `code_insee` |
| Statut | `statut` |

Le fichier est plus riche qu'anticipe : il porte aussi la **hauteur du support**
(`sup_nm_haut`), l'**adresse complete** (`adr_lb_lieu`, `adr_lb_add1..3`,
`adr_nm_cp`), la **nature** du support (`nat_id`) et le **proprietaire** (`tpo_id`).
Ces colonnes ne sont pas nommees dans le code : elles sont suivies automatiquement
comme toutes les autres, mais elles expliquent pourquoi le diff hebdomadaire sera
nettement plus interessant qu'un simple suivi operateur/techno.

**La colonne `id` n'est pas utilisee comme cle.** Rien ne prouve qu'elle soit
stable d'une publication a l'autre — les exports Opendatasoft renumerotent souvent
leurs enregistrements. Elle est donc suivie comme une colonne ordinaire, et le
premier diff reel tranchera de lui-meme : si `id` bouge sur toutes les lignes, le
compteur de changements par champ le montrera immediatement. Tant que ce n'est pas
verifie, la cle reste le triplet.

---

## 4. Arborescence

Tout vit dans un dossier **frere** de `imports/` et `references/` :

```
/opt/geotower/data/history/
  sources/
    20260806174318_observatoireod_20260806.csv.gz   reference actuelle
    20260813......_observatoireod_20260813.csv.gz   la suivante, une fois arrivee
  state.json                  courant, precedent, dates de donnees, empreintes, nb de lignes
  weeks/
    2026-08-13.jsonl.gz       diff exhaustif, jamais purge
  map/
    2026-08-13.map.json.gz    fichier carte allege
```

Les sources archivees **gardent leur nom d'origine** : il porte la date des
donnees, et la renommer en `courant.csv.gz` perdrait la seule information qui
permet de savoir ce qu'on compare. C'est `state.json` qui designe laquelle est
courante et laquelle est precedente. La rotation supprime la plus ancienne des
que trois publications se retrouvent dans `sources/`.

**Interdiction absolue d'ecrire ces archives dans `imports/` ou
`imports/france_sources/`.** `discover_weekly_csv_files` balaie `france_sources/`
**recursivement** et `get_latest_weekly_csv` (`build_fr_anfr_db.py:325`) retient le
fichier au `mtime` le plus recent. Une copie d'archive fraichement ecrite y
deviendrait "la publication la plus recente" et le build des stats repartirait sur
un fichier perime.

Les noms de fichiers portent la **date des donnees** ANFR (prefixe du nom de
fichier source, ou `date_maj`), jamais la date du run ni un `mtime`. Ce piege a
deja ete rencontre cote client, voir `OfficialSources.dataDateKey`.

---

## 5. Deroule du script

1. **Resoudre la publication courante.** Chercher d'abord un CSV local dans
   `imports/` et `imports/france_sources/`. Comparer sa date de donnees a
   `state.json`. Si le local est plus recent que la reference, l'utiliser. Sinon,
   resoudre l'URL du CSV sur la page d'export ANFR (meme regex que
   `OfficialSources.resolveObservatoireCsvUrl`) et le telecharger dans
   `history/sources/`.

   Le telechargement n'est **pas** un chemin de secours theorique : le `crontab`
   du serveur (constate le 2026-08-07) ne contient que `update_sites_hs.sh`
   toutes les 6 h et `build_fr_enb_db.py` le lundi. Personne ne telecharge
   l'observatoire, le CSV du 6 aout a ete depose a la main. Sans telechargement
   autonome, le script ne verrait jamais une nouvelle publication.
2. **Sortir tot si rien de neuf.** Si la version resolue est celle de `state.json`,
   ne rien ecrire, journaliser `deja traite`, code de sortie 0. C'est le cas
   nominal six jours sur sept.
3. **Charger la reference precedente** (`courant.csv.gz`) sous forme d'index
   `triplet -> valeurs`.
4. **Calculer le diff** (section 6).
5. **Appliquer les garde-fous** (section 8). En cas de refus : ne rien ecrire,
   ne pas faire tourner la rotation, code de sortie non nul, message explicite.
6. **Ecrire** `weeks/<date>.jsonl.gz` puis `map/<date>.map.json.gz`.
7. **Faire tourner la paire** : `courant` devient `precedent`, la nouvelle
   publication devient `courant`. Mettre a jour `state.json` **en dernier**, en
   ecriture atomique (fichier temporaire puis `rename`), pour qu'une interruption
   laisse un etat rejouable plutot qu'un etat incoherent.

Le script doit etre **idempotent** : relance sur la meme publication = aucun
nouveau fichier, aucune rotation.

---

## 6. Regles de comparaison

**Tout changement est note.** La normalisation ne sert qu'a eliminer le bruit
indiscutable, pas a masquer des differences :

- espaces de tete/fin et espaces multiples reduits (`clean_text`) ;
- BOM et guillemets d'encadrement retires ;
- comparaison de la casse **insensible** ;
- tout le reste (accent corrige, format de date, libelle reformule) **est** un
  changement, note dans l'archive, simplement classe en priorite basse.

Coordonnees : la distance entre l'ancien et le nouveau point est calculee. Le
changement brut part dans l'archive quelle que soit la distance ; seul un
deplacement **superieur a 5 m** produit un code `MOVED` dans le fichier carte.
En dessous, c'est du recalage de saisie et la carte se remplirait de faux
mouvements.

Classification, calculee **cote serveur** (une seule implementation, l'app se
contente de filtrer) :

| Priorite | Changements |
|---|---|
| Haute | station apparue/disparue, operateur arrive/parti d'un support, systeme ajoute/retire, changement de statut |
| Moyenne | deplacement > 5 m, changement de support, changement de generation |
| Basse | tout le reste : libelles, formats, colonnes annexes |

---

## 7. Formats de sortie

### 7.1 `weeks/<date>.jsonl.gz` — archive exhaustive

Un objet JSON par ligne. La **premiere ligne** est un en-tete :

```json
{"type":"header","from":"2026-07-31","to":"2026-08-07","gap_days":7,
 "rows_before":1043221,"rows_after":1044090,"stations_changed":3874}
```

`from`/`to` sont les dates de **donnees**, et `gap_days` l'ecart reel : si un run a
saute, le fichier doit dire honnetement qu'il couvre deux semaines plutot que de
se presenter comme hebdomadaire.

Ensuite, un objet par **station changee** :

```json
{"sta":"1234567","sup":"SUP-98","lat":48.8566,"lon":2.3522,"dept":"75",
 "chg":[
   {"key":["ORANGE","NR 3500"],"op":"add","v":{"statut":"En service","generation":"5G"}},
   {"key":["SFR","LTE 800"],"op":"upd","f":{"statut":["Accord ANFR","En service"]}},
   {"key":["SFR","GSM 900"],"op":"del","v":{"statut":"En service"}}
 ]}
```

- `op` : `add` (ligne apparue), `del` (ligne disparue), `upd` (valeurs modifiees).
- `f` : un champ par colonne modifiee, `[avant, apres]`, **valeurs brutes**.
- `v` : toutes les colonnes de la ligne, pour `add` et `del`.

Les valeurs brutes sont obligatoires : ce fichier sera la seule memoire longue
puisque seules deux publications sont conservees. Sans elles, reconstruire l'etat
d'un site a une date passee deviendra impossible.

Station apparue ou disparue entierement : `"op":"add"` / `"op":"del"` au niveau de
l'objet station, avec toutes ses lignes.

### 7.2 `map/<date>.map.json.gz` — fichier carte

Un objet JSON, un point par **support**. Quand `sup_id` est vide, le regroupement
bascule sur les coordonnees arrondies, sinon le point serait perdu.

```json
{"from":"2026-07-31","to":"2026-08-07","points":[
  {"sup":"SUP-98","lat":48.8566,"lon":2.3522,"dept":"75",
   "chg":[
     {"c":"SYSTEM_ADDED","op":"ORANGE","sys":"NR 3500"},
     {"c":"STATUS_CHANGED","op":"SFR","sys":"LTE 800","was":"Accord ANFR","now":"En service"}
   ]}
]}
```

Codes autorises, et **rien d'autre** :

`SITE_ADDED`, `SITE_REMOVED`, `OPERATOR_ADDED`, `OPERATOR_REMOVED`,
`SYSTEM_ADDED`, `SYSTEM_REMOVED`, `STATUS_CHANGED`, `MOVED`.

**Aucune phrase en francais dans le fichier.** Le popup de la future carte doit
exister en 7 langues : le serveur ecrit des codes et des valeurs, l'app fabrique
la phrase. Une phrase francaise ecrite ici condamnerait toutes les semaines deja
archivees a rester mono-langue.

Cas particulier a ne pas rater : **les coordonnees d'un site disparu ne sont plus
dans le nouveau CSV**. Elles doivent etre reprises dans l'ancien au moment du
diff. Si on les oublie ce jour-la, ce site n'aura jamais de point sur la carte.

---

## 8. Garde-fous

Le script doit refuser d'ecrire plutot que produire un historique faux. Les
publications ANFR sont parfois republiees, tronquees ou anciennes.

| Condition | Comportement |
|---|---|
| Plus de **5 %** des stations disparues | Refus, aucun fichier ecrit, aucune rotation, code de sortie non nul, message chiffre |
| Date de donnees <= a la reference | Refus (republication ou fichier plus ancien) |
| En-tete sans les colonnes d'identite | Refus |
| Fichier anormalement court (< 100 000 lignes) | Refus |
| Ecart > 10 jours entre les deux publications | Ecriture normale, mais `gap_days` renseigne dans l'en-tete |

Une option `--force` permet de passer outre le seuil de 5 % quand une baisse
massive est reelle et verifiee a la main. Elle n'est jamais utilisee par le cron.

---

## 9. Premiere execution

Aucun diff n'est produit : le script se contente d'archiver la publication
courante dans `sources/` et d'ecrire `state.json`. C'est normal et le message doit
le dire clairement, sans ressembler a une erreur.

**Situation reelle au 2026-08-07** : une seule publication est presente sur le
serveur, celle du 2026-08-06. Le premier diff sortira donc a la publication
suivante, vers le 2026-08-13. Tout ce qui a change avant le 2026-08-06 est deja
perdu et ne se recuperera pas.

Le script doit accepter une reference **deja archivee a la main** dans
`sources/` : si un `.csv.gz` s'y trouve sans `state.json`, il le prend comme
reference au lieu de le remplacer. C'est ce qui permet de sauvegarder la
publication du 2026-08-06 tout de suite, avant meme que le script existe, et de ne
pas dependre de sa date de livraison.

Une option `--bootstrap-from <fichier>` permet en plus de designer explicitement
une ancienne publication comme reference.

---

## 10. Volume : a mesurer, pas a supposer

Personne ne sait aujourd'hui combien de lignes bouge une publication. L'estimation
conditionne tout le lot suivant (base distribuee, chargement par la carte), et une
mauvaise estimation ferait dimensionner l'app pour rien.

Prevoir donc `--dry-run` : calcule le diff, affiche le nombre de changements par
code et la taille estimee des deux fichiers, **n'ecrit rien et ne fait pas
tourner la paire**. A lancer sur les deux premieres publications reelles avant de
figer quoi que ce soit cote app.

---

## 11. Tests attendus

`docs/server/test_build_site_changes.py`, `unittest`, sur de petits CSV en dur :

- ajout, retrait et modification de ligne ;
- station entierement apparue / entierement disparue ;
- coordonnees d'un site disparu reprises de l'ancienne publication ;
- seuil des 5 m : 3 m ne produit pas de `MOVED`, 40 m oui ;
- garde-fou des 5 % : refus, et rien n'est ecrit ni fait tourner ;
- meme publication traitee deux fois : idempotent ;
- doublons exacts fusionnes ;
- colonne inconnue apparue dans l'en-tete : suivie, aucun plantage ;
- fichier en `cp1252` : lu sans exception ;
- regroupement par `sup_id`, et repli sur les coordonnees quand il est vide ;
- `state.json` inchange quand le script refuse d'ecrire.

---

## 12. Exploitation

Cron **quotidien**, pas hebdomadaire : l'ANFR ne publie pas toujours le meme jour,
et un cron cale sur le mauvais jour rate une publication puis fusionne deux
semaines sans le signaler. Un passage quotidien qui ne fait rien six jours sur
sept rend le rythme hebdomadaire emergent au lieu de le parier.

Meme forme que les entrees existantes (`flock` pour eviter deux executions
simultanees, sortie journalisee) :

```
30 5 * * * flock -n /tmp/geotower_site_changes.lock /usr/bin/python3 /opt/geotower/api/build_site_changes.py >> /opt/geotower/data/history/site_changes_cron.log 2>&1
```

Le script reste **hors** de `build_all_db.py` : son echec ne doit jamais empecher
la construction des bases, et sa reussite ne doit jamais en dependre.

---

## 13. A la charge du proprietaire du serveur

1. Creer `/opt/geotower/data/history/{sources,weeks,map}` avec les droits de
   l'utilisateur du cron.
2. Verifier l'espace libre sur `/opt/geotower/data` : compter environ 1 Go de
   marge (deux CSV gzippes plus un decompresse temporaire).
3. Dire si des publications de l'observatoire sont **deja** presentes dans
   `imports/` ou `imports/france_sources/`, et combien.
4. Fournir la **premiere ligne** du CSV observatoire courant, pour nommer
   exactement la colonne departement / code INSEE.
5. Dire comment le CSV arrive aujourd'hui : cron de telechargement, ou depot
   manuel, et a quel chemin.
6. Poser la ligne de cron une fois le script livre et teste a la main.
