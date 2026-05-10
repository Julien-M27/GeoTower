# Remplacement de `fallbackToDestructiveMigration()`

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique le probleme actuel et decrit comment remplacer `fallbackToDestructiveMigration()` proprement.

## Objectif

Retirer l'usage de `fallbackToDestructiveMigration()` dans la configuration Room de GeoTower, sans provoquer de crash au demarrage ni effacer silencieusement la base locale telechargee.

La base `geotower.db` est une base SQLite telechargee et remplacee par l'application. Elle contient les donnees ANFR utilisees par les ecrans principaux. Elle ne doit pas etre detruite automatiquement par Room en cas de mismatch de version/schema.

## Probleme actuel

Etat observe :

- `AppDatabase.kt` construit Room avec le fichier `"geotower.db"`.
- `AppDatabase.kt` appelle `.fallbackToDestructiveMigration()`.
- `DatabaseDownloader.kt` telecharge un fichier SQLite complet, le valide partiellement, ferme Room, puis remplace `geotower.db`.
- `AppDatabase.kt` declare `version = 1` et `exportSchema = false`.

Risque principal :

`fallbackToDestructiveMigration()` autorise Room a detruire et recreer la base si une migration attendue manque. Dans GeoTower, cela peut transformer une base locale telechargee en base vide ou partielle, sans explication claire pour l'utilisateur.

Effets possibles :

- disparition silencieuse des donnees offline ;
- ecrans vides alors que l'utilisateur pense avoir telecharge la base ;
- bug masque pendant le developpement, car une vraie incompatibilite schema/version est effacee au lieu d'etre corrigee ;
- confusion avec le systeme de telechargement, qui croit parfois qu'une base existe alors que son contenu utile a ete recrée vide ;
- difficulte a diagnostiquer les changements de schema entre l'app et le backend qui genere `geotower.db`.

## Architecture cible recommandee

Ne pas remplacer par un autre fallback destructif. La strategie cible doit etre explicite :

1. Room ne detruit jamais automatiquement `geotower.db`.
2. Les changements de schema Android sont couverts par des migrations Room.
3. Les bases telechargees sont validees avant installation, y compris leur schema.
4. Si la base locale est incompatible, l'app affiche un etat "base invalide / a telecharger" au lieu de recreer une base vide en silence.
5. Le backend qui fournit la base expose une notion de version de donnees et, idealement, une version de schema compatible avec l'app.

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/data/db/AppDatabase.kt`
- `app/src/main/java/fr/geotower/data/db/GeoTowerDao.kt`
- `app/src/main/java/fr/geotower/data/api/DatabaseDownloader.kt`
- `app/src/main/java/fr/geotower/data/workers/DatabaseDownloadWorker.kt`
- `app/src/main/java/fr/geotower/data/models/OfflineEntities.kt`
- les ecrans qui ouvrent la base directement avec `SQLiteDatabase`, notamment `AboutScreen.kt`, `DatabaseDownloadCard.kt` et `SplashScreen.kt`

## Changements a effectuer

### 1. Activer l'export de schema Room

Dans `AppDatabase.kt` :

- passer `exportSchema = true` ;
- configurer le dossier de schemas Room si necessaire dans Gradle/KSP ;
- versionner les fichiers de schema generes.

But : pouvoir comparer les schemas et ecrire des migrations fiables.

### 2. Supprimer le fallback destructif

Dans `AppDatabase.kt` :

- supprimer `.fallbackToDestructiveMigration()` ;
- ajouter des migrations explicites avec `.addMigrations(...)` des qu'une version superieure a `1` existe ;
- ne pas remplacer par `fallbackToDestructiveMigrationFrom(...)` sauf decision produit explicite et documentee.

Important : ne pas simplement supprimer le fallback sans verifier le comportement avec une base deja installee. Si Room crash au lancement, il faut ajouter une strategie de recuperation controlee.

### 3. Decider qui possede le schema

L'IA doit clarifier et documenter une des deux strategies suivantes.

Strategie recommandee : schema compatible Room, fourni par le backend.

- Le fichier `geotower.db` telecharge doit correspondre au schema attendu par les entites Room.
- Le backend doit produire une base compatible avec la version de l'app.
- Si le backend change le schema, l'app doit etre mise a jour avec migration/validation avant de consommer ce nouveau schema.
- Le endpoint de version devrait exposer une `schemaVersion` ou equivalent.

Strategie alternative : base telechargee traitee comme dataset externe.

- L'app valide le schema externe avant usage.
- En cas d'incompatibilite, elle marque la base comme invalide et demande un nouveau telechargement.
- Room ne doit pas recreer une base vide qui ressemble a une base valide.

### 4. Ajouter une validation de schema avant installation

Dans `DatabaseDownloader.kt`, renforcer `validateDownloadedDatabase`.

Verifier au minimum :

- `PRAGMA integrity_check = ok` ;
- presence des tables :
  - `localisation`
  - `technique`
  - `physique`
  - `faisceaux_hertziens`
  - `metadata`
- presence des colonnes attendues par les entites Room :
  - `localisation`: `id_anfr`, `operateur`, `latitude`, `longitude`, `azimuts`, `code_insee`, `azimuts_fh`, `filtres`
  - `technique`: `id_anfr`, `technologies`, `statut`, `date_implantation`, `date_service`, `date_modif`, `details_frequences`, `adresse`
  - `physique`: `id_anfr`, `id_support`, `nature_support`, `proprietaire`, `hauteur`, `azimuts_et_types`
  - `faisceaux_hertziens`: `id_anfr`, `id_support`, `type_fh`, `azimuts_fh`
- presence des colonnes `metadata` effectivement lues par l'app :
  - `version`
  - `date_maj_anfr`
  - `zip_version`, si l'app continue de la lire comme optionnelle
- presence de donnees dans les tables critiques.

Option conseillee : verifier aussi les types SQLite via `PRAGMA table_info(<table>)`, au moins pour les colonnes critiques comme IDs et coordonnees.

### 5. Gerer une base locale incompatible

Si Room detecte une base incompatible ou si la validation locale echoue :

- fermer l'instance Room avec `AppDatabase.closeDatabase()` ;
- ne pas recreer automatiquement une base vide ;
- marquer la base comme absente/invalide dans l'etat UI ;
- afficher une action claire de retelechargement ;
- supprimer ou renommer la base invalide seulement dans un flux controle et visible.

Ne pas laisser un crash brut arriver jusqu'a l'utilisateur.

### 6. Ajouter des migrations explicites

Quand le schema Room change :

- incrementer `version` dans `@Database` ;
- ajouter une migration `Migration(oldVersion, newVersion)` ;
- ecrire les `ALTER TABLE`, creation d'index ou transformations necessaires ;
- ajouter un test qui ouvre une base de l'ancienne version puis verifie le schema final.

Si aucune migration n'est possible parce que le dataset est entierement fourni par le serveur, alors l'app doit refuser l'ancienne base et demander un retelechargement, mais cette decision doit etre explicite dans le code.

## Tests a ajouter ou mettre a jour

Ajouter des tests Room/migration si l'infrastructure le permet :

- ouverture d'une base compatible existante : succes ;
- ouverture d'une base avec ancienne version + migration : succes ;
- ouverture d'une base incompatible sans migration : pas de destruction silencieuse ;
- base telechargee valide : installation acceptee ;
- base telechargee sans colonne requise : installation refusee ;
- base corrompue : installation refusee ;
- base invalide deja presente : l'app affiche un etat recuperable.

Si les tests instrumentation sont trop lourds, ajouter au minimum des tests unitaires autour des fonctions de validation SQLite avec des fichiers temporaires.

## Criteres d'acceptation

La correction est terminee seulement si :

- `rg "fallbackToDestructiveMigration" app` ne retourne rien ;
- `AppDatabase.kt` utilise des migrations explicites ou une strategie de recuperation documentee ;
- `exportSchema = true` ou une justification solide est documentee ;
- une base existante compatible n'est pas effacee au lancement ;
- une base incompatible n'est pas remplacee silencieusement par une base vide ;
- le telechargement refuse une base dont le schema ne correspond pas aux entites utilisees ;
- les ecrans affichent un message/action de recuperation si la base locale est invalide ;
- `:app:compileDebugKotlin` passe ;
- les tests existants passent, ou les echecs restants sont documentes comme non lies.

## Commandes de verification suggerees

Depuis la racine du projet Android :

```powershell
rg "fallbackToDestructiveMigration" app
rg "exportSchema" app/src/main/java/fr/geotower/data/db/AppDatabase.kt
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Si des tests instrumentation Room sont ajoutes :

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## A ne pas faire

- Ne pas supprimer simplement `fallbackToDestructiveMigration()` en laissant un crash non gere.
- Ne pas utiliser un autre fallback destructif pour masquer le probleme.
- Ne pas recreer une base vide et la traiter comme valide.
- Ne pas ignorer `metadata`, car plusieurs ecrans lisent cette table directement.
- Ne pas changer le nom du fichier `geotower.db` sans migrer tout le code qui l'ouvre directement.
- Ne pas modifier le schema des entites sans migration ou validation correspondante.

## Prompt court pour l'IA qui implementera

Tu dois retirer `fallbackToDestructiveMigration()` de GeoTower sans effacer silencieusement la base locale `geotower.db`. Lis `AppDatabase.kt`, `GeoTowerDao.kt`, `DatabaseDownloader.kt`, `DatabaseDownloadWorker.kt`, `OfflineEntities.kt` et les ecrans qui ouvrent SQLite directement. Active l'export de schema Room, remplace le fallback destructif par des migrations explicites ou par une recuperation controlee si la base telechargee est incompatible. Renforce la validation de `DatabaseDownloader` avec verification des tables, colonnes et metadata attendues. En cas de base invalide, ferme Room, marque la base comme invalide/absente et propose un retelechargement, sans recreer de base vide en silence. Termine en verifiant que `rg "fallbackToDestructiveMigration" app` ne retourne rien, que la compilation passe, et que les tests existants passent ou que les echecs non lies sont documentes.
