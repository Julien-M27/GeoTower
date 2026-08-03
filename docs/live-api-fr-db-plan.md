# DB live FR et API GeoTower Live

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il decrit comment ajouter une base serveur dediee a l'API live, en conservant la base offline Android existante.

## Objectif

GeoTower conserve deux usages separes :

- `geotower_fr.db` : base complete offline telechargee par l'app Android.
- `geotower_live_fr.db` : base derivee, compacte et indexee, utilisee par le serveur pour l'API live FR.

La DB live doit permettre :

- d'afficher des sites dans l'app pendant que `geotower_fr.db` se telecharge ;
- de servir une future version web de GeoTower sans demander de telecharger la grosse DB ;
- de repondre rapidement aux requetes carte, recherche, fiche site et proximite ;
- de ne pas coupler l'API publique au schema Room mobile.

## Principe general

Le pipeline cible est :

1. Generer `geotower_fr.db` comme aujourd'hui.
2. Valider `geotower_fr.db`.
3. Generer `geotower_live_fr.db.tmp` depuis `geotower_fr.db`.
4. Valider `geotower_live_fr.db.tmp`.
5. Remplacer atomiquement `geotower_live_fr.db`.
6. L'API live lit uniquement `geotower_live_fr.db` en lecture seule.

La DB live n'est pas une copie brute. C'est une projection serveur optimisee pour les requetes frequentes.

## Nommage

Fichiers recommandes :

- `geotower_fr.db`
- `geotower_live_fr.db`
- `version_live_fr.json`

Endpoints recommandes :

- `/api/v2/live/fr/status`
- `/api/v2/live/fr/sites/nearby`
- `/api/v2/live/fr/sites/bbox`
- `/api/v2/live/fr/sites/{idAnfr}`
- `/api/v2/live/fr/search`
- `/api/v2/live/fr/clusters` plus tard

Cette structure prepare le multi-pays :

- `geotower_live_fr.db`
- `geotower_live_be.db`
- `geotower_live_ca.db`

## Schema DB live recommande

### `metadata`

Une seule ligne de metadata.

Champs :

- `country_code`
- `country_name`
- `source`
- `offline_db_version`
- `offline_schema_version`
- `live_schema_version`
- `date_maj_anfr`
- `zip_version`
- `generated_at`
- `row_count`

### `site_summary`

Table principale pour carte, nearby et listes.

Champs proposes :

- `site_rowid INTEGER PRIMARY KEY`
- `id_anfr TEXT NOT NULL`
- `operator_name TEXT`
- `latitude REAL NOT NULL`
- `longitude REAL NOT NULL`
- `azimuts TEXT`
- `azimuts_fh TEXT`
- `code_insee TEXT`
- `commune TEXT`
- `adresse TEXT`
- `tech_mask INTEGER NOT NULL`
- `band_mask INTEGER NOT NULL`
- `statut TEXT`
- `has_active INTEGER NOT NULL`
- `has_underground_support INTEGER NOT NULL`
- `arcep_nidt TEXT`
- `is_zb INTEGER NOT NULL`
- `support_count INTEGER NOT NULL`
- `support_ids TEXT`

Index :

- `idx_site_summary_id_anfr`
- `idx_site_summary_code_insee`
- `idx_site_summary_operator`
- `idx_site_summary_lat_lon`
- `idx_site_summary_zb`
- `idx_site_summary_active`

### `site_detail`

Table pour fiche site sans gros joins au moment de la requete.

Champs proposes :

- `id_anfr TEXT PRIMARY KEY`
- `technologies TEXT`
- `statut TEXT`
- `date_implantation TEXT`
- `date_service TEXT`
- `date_modif TEXT`
- `details_frequences TEXT`
- `adresse TEXT`
- `operator_name TEXT`
- `code_insee TEXT`
- `commune TEXT`
- `arcep_nidt TEXT`
- `is_zb INTEGER NOT NULL`

### `site_support`

Supports physiques rattaches au site.

Champs proposes :

- `id_anfr TEXT NOT NULL`
- `id_support TEXT NOT NULL`
- `nature_support TEXT`
- `proprietaire TEXT`
- `exploitant TEXT`
- `hauteur REAL`
- `azimuts_et_types TEXT`

Cle :

- `PRIMARY KEY(id_anfr, id_support)`

Index :

- `idx_site_support_id_anfr`
- `idx_site_support_id_support`

### `site_antenna`

Antennes detaillees si besoin pour les fiches et affichages techniques.

Champs proposes :

- `aer_id TEXT PRIMARY KEY`
- `id_anfr TEXT NOT NULL`
- `id_support TEXT`
- `type_antenne TEXT`
- `azimut INTEGER`
- `hauteur_bas REAL`
- `is_fh INTEGER NOT NULL`

Index :

- `idx_site_antenna_id_anfr`
- `idx_site_antenna_id_support`

### `site_rtree`

Index spatial SQLite RTree pour bbox et nearby.

Schema :

```sql
CREATE VIRTUAL TABLE site_rtree USING rtree(
    site_rowid,
    min_lat,
    max_lat,
    min_lon,
    max_lon
);
```

Chaque site utilise :

- `min_lat = latitude`
- `max_lat = latitude`
- `min_lon = longitude`
- `max_lon = longitude`

### `site_search_fts`

Recherche texte rapide.

Schema indicatif :

```sql
CREATE VIRTUAL TABLE site_search_fts USING fts5(
    id_anfr,
    operator_name,
    adresse,
    commune,
    code_insee,
    support_ids,
    support_types,
    proprietaires,
    technologies,
    content=''
);
```

La table doit contenir des textes normalises et utiles pour :

- recherche id ANFR ;
- recherche id support ;
- recherche commune/code postal ;
- recherche adresse ;
- recherche operateur ;
- recherche type de support ;
- recherche technologie/frequence.

### `map_cluster`

A ajouter apres le MVP.

Objectif : eviter de recalculer les clusters web/API a chaque chargement de carte.

Champs possibles :

- `country_code`
- `zoom_bucket`
- `tile_x`
- `tile_y`
- `cluster_lat`
- `cluster_lon`
- `site_count`
- `operators`
- `single_id_anfr`
- `tech_mask`
- `band_mask`

## Script serveur a creer

Creer un script :

```text
docs/server/build_live_fr_db.py
```

Responsabilites :

1. Ouvrir `geotower_fr.db` en lecture seule.
2. Creer `geotower_live_fr.db.tmp`.
3. Creer les tables live.
4. Precalculer les donnees de `site_summary`.
5. Precalculer `site_detail`, `site_support`, `site_antenna`.
6. Remplir `site_rtree`.
7. Remplir `site_search_fts`.
8. Creer les index.
9. Executer `ANALYZE`.
10. Verifier les counts et metadata.
11. Remplacer atomiquement la DB live.

Le script ne doit pas modifier `geotower_fr.db`.

## API serveur cible

Ajouter une couche dediee dans le serveur FastAPI, idealement separee de `main.py` si le fichier grossit :

```text
docs/server/live_fr_api.py
```

Puis inclure le router dans `main.py`.

### `GET /api/v2/live/fr/status`

Retour :

```json
{
  "country_code": "FR",
  "source": "ANFR",
  "offline_db_version": "2026-...",
  "live_schema_version": 1,
  "date_maj_anfr": "2026-...",
  "generated_at": "2026-...",
  "row_count": 123456
}
```

### `GET /api/v2/live/fr/sites/nearby`

Parametres :

- `lat`
- `lon`
- `limit`, borne serveur
- `radius_km`, optionnel et borne serveur
- `zb_only`, optionnel
- `active_only`, optionnel

Retour : liste de `site_summary`.

Le calcul peut utiliser un prefiltre RTree/bounding box puis un tri distance approxime.

### `GET /api/v2/live/fr/sites/bbox`

Parametres :

- `north`
- `south`
- `east`
- `west`
- `zoom`
- `limit`, borne serveur
- filtres optionnels : operateur, tech mask, band mask, ZB, actif

Retour :

- sites directs si la zone est petite ou le zoom eleve ;
- plus tard, clusters si la zone est grande ou zoom faible.

### `GET /api/v2/live/fr/sites/{idAnfr}`

Retour :

- `summary`
- `detail`
- `supports`
- `antennas`

Ce endpoint doit accepter les variantes d'id deja gerees cote Android :

- id exact ;
- id numerique sans zeros ;
- id padde si applicable ;
- id support si necessaire via recherche separee.

### `GET /api/v2/live/fr/search`

Parametres :

- `q`
- `limit`
- filtres optionnels

Retour : liste de `site_summary`, enrichie avec score ou type de match si utile.

Utiliser `site_search_fts`.

## Feature flags

Ajouter des flags serveur et Android :

- `liveApi.fr`
- `liveApi.fr.nearby`
- `liveApi.fr.bbox`
- `liveApi.fr.search`
- `liveApi.fr.siteDetail`

Ajouter aussi des limites distantes :

- `liveApiNearbyMaxLimit`
- `liveApiBboxMaxLimit`
- `liveApiBboxMaxDegrees`
- `liveApiSearchMaxLimit`

## Garde-fous API

Indispensable :

- lecture seule de la DB live ;
- `limit` borne cote serveur ;
- bbox trop grande refusee ou forcee en clusters ;
- rayon nearby maximal ;
- timeout court ;
- cache court cote serveur ;
- CORS limite aux domaines prevus quand le site web existe ;
- pas de logs GPS precis, ou logs arrondis ;
- pas de payload complet en logs ;
- feature flag pour couper rapidement l'API live ;
- statut HTTP clair si DB live absente.

## Integration Android

Ajouter un client API :

```text
app/src/main/java/fr/geotower/data/api/LiveSitesApi.kt
```

Ajouter des DTO :

```text
app/src/main/java/fr/geotower/data/models/LiveSiteDtos.kt
```

Dans `AnfrRepository`, ajouter une strategie :

1. Si `GeoTowerDatabaseValidator` indique une DB locale valide, utiliser la DB locale.
2. Si DB absente/invalide ou telechargement en cours, utiliser l'API live si les flags l'autorisent.
3. Si l'API live echoue, retourner une liste vide controlee et laisser le telechargement continuer.
4. Des que la DB locale devient valide, repasser automatiquement a SQLite locale.

Methodes a couvrir en MVP :

- `getNearest`
- `getNearestZb`
- `getTechniqueDetails`
- `getPhysiqueDetails`
- `getAntennasByExactId`

Methodes a couvrir ensuite :

- `getAntennasInBox`
- `getClusteredAntennas`
- `searchAntennasByText`
- `searchAntennasByAddress`
- details batch par ids

## UX Android

Pendant l'utilisation de l'API live :

- garder la progression du telechargement DB visible ;
- afficher si besoin une mention discrete "mode connecte temporaire" ;
- ne pas bloquer l'ecran carte si l'API live echoue ;
- ne pas faire croire que les donnees sont offline tant que la DB locale n'est pas valide.

## Futur site web

La future web app doit utiliser la meme API :

- carte interactive ;
- recherche globale ;
- fiche site ;
- filtres operateur/technologie/frequence ;
- pannes reseau ;
- plus tard photos, speedtests et donnees communautaires.

Quand le trafic augmente, ajouter :

- clusters precalcules ;
- tuiles vectorielles ;
- eventuellement PMTiles/MVT pour la carte web.

## Ordre d'implementation recommande

### Phase 1 - DB live MVP

- Creer `build_live_fr_db.py`.
- Generer `geotower_live_fr.db`.
- Tables : `metadata`, `site_summary`, `site_detail`, `site_support`, `site_rtree`.
- Validation counts/schema.

### Phase 2 - API live minimale

- Ajouter `/api/v2/live/fr/status`.
- Ajouter `/api/v2/live/fr/sites/nearby`.
- Ajouter `/api/v2/live/fr/sites/{idAnfr}`.
- Ajouter feature flags serveur.
- Tests FastAPI avec fixture SQLite.

### Phase 3 - Fallback Android

- Ajouter `LiveSitesApi`.
- Mapper les DTO vers `LocalisationEntity`, `TechniqueEntity`, `PhysiqueEntity`.
- Utiliser l'API live uniquement quand la DB locale est absente/invalide/en cours.
- Tester que DB valide = local, DB absente = live, API down = pas de crash.

### Phase 4 - Carte et recherche

- Ajouter `/sites/bbox`.
- Ajouter `site_search_fts`.
- Ajouter `/search`.
- Integrer carte et recherche Android en fallback.

### Phase 5 - Web-ready

- Ajouter clusters precalcules.
- Ajouter CORS pour le futur domaine.
- Prototyper une carte web simple.

## Tests a ajouter

Serveur :

- generation DB live depuis une fixture ;
- metadata coherente ;
- RTree trouve les sites attendus ;
- nearby trie les sites proches ;
- bbox refuse une zone trop grande ;
- search FTS retourne les bons ids ;
- site detail retourne supports et antennes.

Android :

- parsing DTO live ;
- fallback live quand DB locale manquante ;
- local prioritaire quand DB valide ;
- erreur API live convertie en liste vide ;
- aucun crash pendant un telechargement DB.

## Criteres d'acceptation MVP

Le MVP est termine seulement si :

- `geotower_live_fr.db` est generee depuis `geotower_fr.db` ;
- la DB live contient metadata, summaries, details, supports et RTree ;
- `/api/v2/live/fr/status` repond ;
- `/api/v2/live/fr/sites/nearby` retourne des sites bornes et tries ;
- `/api/v2/live/fr/sites/{idAnfr}` retourne une fiche exploitable ;
- l'API refuse les requetes trop larges ;
- l'app Android peut afficher des sites proches quand la DB locale n'est pas encore valide ;
- l'app repasse sur la DB locale apres validation du telechargement.

## A ne pas faire

- Ne pas remplacer `geotower_fr.db`.
- Ne pas faire de JSON geants plats comme source principale.
- Ne pas exposer directement des requetes non bornees.
- Ne pas coupler l'API live aux classes Room Android.
- Ne pas logger les positions GPS precises.
- Ne pas faire de WebSocket pour le MVP.
- Ne pas recalculer de gros joins a chaque requete API si une table prejointe peut etre generee.

## Prompt court pour l'IA qui implementera

Tu dois ajouter une DB serveur dediee a l'API live FR sans remplacer la DB offline Android. Lis `docs/server/build_fr_anfr_db.py`, `docs/server/main.py`, `app/src/main/java/fr/geotower/data/AnfrRepository.kt`, `GeoTowerDao.kt` et `OfflineEntities.kt`. Cree un script `docs/server/build_live_fr_db.py` qui genere `geotower_live_fr.db` depuis `geotower_fr.db`, avec tables `metadata`, `site_summary`, `site_detail`, `site_support`, `site_antenna`, `site_rtree` et plus tard `site_search_fts`. Ajoute des endpoints FastAPI `/api/v2/live/fr/status`, `/api/v2/live/fr/sites/nearby` et `/api/v2/live/fr/sites/{idAnfr}` avec limites strictes et lecture seule. Ensuite ajoute un client Android `LiveSitesApi` et utilise-le dans `AnfrRepository` seulement quand la DB locale est absente, invalide ou en cours de telechargement. La DB locale valide reste toujours prioritaire.
