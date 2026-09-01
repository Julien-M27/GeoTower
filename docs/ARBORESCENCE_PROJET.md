# Arborescence et rôle des fichiers de GeoTower

> Documentation technique destinée à faciliter la prise en main du projet. Elle décrit la
> structure observée dans le dépôt et les responsabilités principales des fichiers. Les
> ressources cartographiques répétitives sont décrites par familles, car leur rôle est identique
> et leur nom indique directement le symbole représenté.

## 1. Vue d'ensemble

GeoTower est une application Android Kotlin orientée cartographie et analyse des antennes relais.
Elle combine :

- une base ANFR locale, consultable hors connexion ;
- une base radio séparée, également ouverte en lecture seule ;
- des API distantes pour les mises à jour, les données « live », les photos, le routage et les
  profils altimétriques ;
- une interface Jetpack Compose Material 3 pour téléphone et Android Automotive ;
- des traitements différés WorkManager pour les téléchargements, la génération de bases, les
  notifications, les uploads et les widgets ;
- des fonctions cartographiques OSMdroid/Mapsforge et des ressources de thèmes vectoriels.

Le flux principal est le suivant :

```text
MainActivity / écrans Compose
        │
        ├── repositories (AnfrRepository, RadioRepository, EnbRepository)
        │       ├── AppDatabase + GeoTowerDao       → base ANFR Room/SQLite
        │       ├── fichiers SQLite radio/eNB       → lectures spécialisées
        │       └── RetrofitClient + services API    → données distantes
        │
        ├── workers / services                      → tâches et suivi en arrière-plan
        ├── widgets Glance                           → affichage hors application
        └── carte OSMdroid / Mapsforge              → tuiles, marqueurs, thèmes et overlays
```

### Chiffres utiles

| Élément | Volume observé | Commentaire |
| --- | ---: | --- |
| Fichiers Kotlin de production | 373 | `app/src/main/java/fr/geotower` |
| Tests unitaires Kotlin | 108 | `app/src/test/java` |
| Test instrumenté | 1 | `CarAppHandshakeTest` |
| SVG de thème cartographique | 796 | `assets/themes` |
| Images PNG/WebP Android | 86 | icônes, logos, aperçus et lanceurs |
| Versions de schéma Room | 7 | `app/schemas/.../1.json` à `7.json` |

Les répertoires générés comme `build/`, `.gradle/`, `.kotlin/` et la plupart des fichiers de
`.idea/` ne sont pas détaillés : ils sont produits par Gradle ou Android Studio et ne constituent
pas le code fonctionnel de l'application.

## 2. Arbre du dépôt

```text
GeoTower/
├── app/                                  Module Android principal
│   ├── build.gradle.kts                   Configuration Gradle du module
│   ├── proguard-rules.pro                 Règles de minification release
│   ├── schemas/                           Historique des schémas Room
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         Déclarations Android communes
│       │   ├── baseline-prof.txt           Profil de performance Android
│       │   ├── assets/themes/              Thèmes, symboles et motifs Mapsforge
│       │   ├── java/fr/geotower/           Code Kotlin de production
│       │   └── res/                        Ressources Android compilées
│       ├── mobile/AndroidManifest.xml      Compléments de la variante téléphone
│       ├── automotive/AndroidManifest.xml  Compléments Android Automotive OS
│       ├── test/                           Tests unitaires JVM et fichiers golden
│       └── androidTest/                    Tests exécutés sur appareil/émulateur
├── docs/                                  Documentation et outils de données
│   ├── i18n/                               Copies de travail des traductions
│   └── server/build_site_changes.py        Construction/diff des changements ANFR
├── gradle/
│   ├── libs.versions.toml                 Catalogue des versions et dépendances
│   └── wrapper/                            Wrapper Gradle
├── osm/org/osmdroid/views/MapController.java
│                                           Adaptation locale d'osmdroid
├── prototype-carte/                        Prototype cartographique indépendant
├── build.gradle.kts                        Plugins Gradle communs
├── settings.gradle.kts                     Nom du projet, module et dépôts Maven
├── gradle.properties                       Options Gradle/Kotlin/Android
├── gradlew / gradlew.bat                   Lanceurs Gradle Unix/Windows
├── keystore.properties.example             Modèle de signature local
├── local.properties                        Chemins SDK locaux, non portable
├── .editorconfig                           Règles d'édition
├── .gitignore                              Fichiers exclus de Git
├── README.md                               Présentation et démarrage rapide
└── fichiers *.log                          Journaux de builds conservés localement
```

## 3. Fichiers racine et configuration Gradle

| Fichier | Rôle |
| --- | --- |
| `README.md` | Présente GeoTower, ses fonctionnalités, sa stack (Kotlin, Compose, Room, OSMdroid, WorkManager) et les prérequis de build. |
| `settings.gradle.kts` | Définit le nom `GeoTower`, le module `:app`, les dépôts Google/Maven Central/JitPack et les règles de résolution des dépendances. |
| `build.gradle.kts` | Déclare au niveau racine les plugins Android application, Kotlin Compose et KSP sans les appliquer directement. |
| `gradle.properties` | Centralise les propriétés de compilation Gradle/Kotlin et les options du projet. |
| `gradle/libs.versions.toml` | Catalogue versionné des plugins et bibliothèques : AndroidX, Compose, Room, Retrofit/OkHttp, OSMdroid/Mapsforge, Car App, Glance et WorkManager. |
| `gradle/wrapper/gradle-wrapper.jar` | Runtime du wrapper Gradle ; permet d'utiliser la version configurée sans installation globale. |
| `gradle/wrapper/gradle-wrapper.properties` | URL et version de la distribution Gradle téléchargée par le wrapper. |
| `gradlew` / `gradlew.bat` | Scripts de lancement Gradle pour environnements Unix et Windows. |
| `.editorconfig` | Règles communes d'indentation et de fin de ligne. |
| `.gitignore` | Exclut les sorties de build, secrets, fichiers locaux, caches et artefacts temporaires. |
| `keystore.properties.example` | Exemple des propriétés nécessaires à la signature release ; le vrai `keystore.properties` reste local. |
| `local.properties` | Chemins SDK de la machine Android Studio ; ne doit pas être copié tel quel sur une autre machine. |
| `build-fulltest.log` / `build-zb-test.log` | Journaux de précédentes exécutions de build/tests, utiles pour diagnostic mais non nécessaires à l'exécution. |

### Module `app`

| Fichier | Rôle |
| --- | --- |
| `app/build.gradle.kts` | Configure l'application (`fr.geotower`), SDK/minSdk, version, variantes `mobile` et `automotive`, signature release, R8/ProGuard, KSP Room, dépendances et types de tests. |
| `app/proguard-rules.pro` | Règles de conservation/minification spécifiques à GeoTower pour les builds release. |
| `app/.gitignore` | Exclusions propres au module Android. |
| `app/schemas/fr.geotower.data.db.AppDatabase/1.json` à `7.json` | Snapshots successifs du schéma Room ; ils servent à vérifier les migrations et à documenter l'évolution de la base. |

## 4. Code de production : `app/src/main/java/fr/geotower`

### 4.1 Racine de l'application

| Fichier | Rôle |
| --- | --- |
| `GeoTowerApp.kt` | Sous-classe `Application`. Initialise les logs fichier et le crash handler, Retrofit, les endpoints, OSMdroid, les feature flags, la distribution, les préférences, le mode économie d'énergie et la base locale. Expose le repository ANFR partagé. |
| `MainActivity.kt` | Point d'entrée UI. Installe Compose, le thème, le graphe Navigation Compose, les deep links, les intents de partage, les popups globaux et le démarrage de services/workers liés à l'activité. |

### 4.2 Accès aux données principaux : `data`

| Fichier | Rôle |
| --- | --- |
| `data/AnfrRepository.kt` | Façade principale des données ANFR : recherche locale, antennes/supports, regroupements, proximité, détails, cartes, filtres, données live et bascule réseau/base locale. |
| `data/EnbRepository.kt` | Accès en lecture aux bases eNB/gNB issues d'eNB Analytics, avec recherches géographiques et statistiques opérateur. |
| `data/RadioRepository.kt` | Requêtes dans la base radio non-ANFR : sites radio/TV, faisceaux hertziens, services, filtres binaires, regroupements carte et détails. |
| `data/MacroClusterGrouper.kt` | Regroupe les sites proches en macro-clusters lorsque le niveau de zoom est faible afin de limiter le nombre de marqueurs. |

### 4.3 Services HTTP et téléchargements : `data/api`

| Fichier | Rôle |
| --- | --- |
| `AnfrService.kt` | Contrat Retrofit des endpoints ANFR et DTO associés. |
| `ApiEndpoints.kt` | Sélection et persistance du serveur API principal ou miroir. |
| `RetrofitClient.kt` | Construction des clients Retrofit/OkHttp partagés, configuration réseau et blocage des endpoints communautaires en mode économie maximale. |
| `AppUpdateChecker.kt` | Interroge les informations de version disponibles et décide si une mise à jour est proposée. |
| `AppUpdateState.kt` | Conserve la dernière information de mise à jour affichée/connue pour éviter les bandeaux incohérents au démarrage. |
| `BdTopoBuildingsApi.kt` | Client et modèles pour les bâtiments de la BD TOPO, utilisés autour d'un site ou pour enrichir son contexte. |
| `CellMapperLinks.kt` | Construit les liens vers CellMapper à partir des identifiants et opérateurs radio. |
| `CellularFrApi.kt` | Client des photos/données CellularFR et modèles associés. |
| `CommuneReferenceApi.kt` | Recherche de références géographiques de communes et correspondance administrative. |
| `DatabaseDownloader.kt` | Télécharge, vérifie, remplace et ouvre la base ANFR principale ; gère progression, versions, fichiers temporaires et reprise sûre. |
| `DownloadManifestVerifier.kt` | Vérifie la signature/identité publique du manifeste de téléchargement avant installation. |
| `ElevationProfileApi.kt` | Client et modèles des profils d'altitude nécessaires à l'analyse d'un trajet radio. |
| `EnbDatabaseDownloader.kt` | Téléchargement et installation de la base eNB/gNB. |
| `LiveDatabaseStatus.kt` | Modèles et calcul de l'état d'utilisation/disponibilité des données live. |
| `LiveSitesApi.kt` | Client des sites et informations d'activité remontés par le serveur live. |
| `NominatimApi.kt` | Client de géocodage inverse/recherche Nominatim. |
| `RadioDatabaseDownloader.kt` | Téléchargement, validation et installation de la base radio. |
| `RouteApi.kt` | Client de calcul d'itinéraire et de géométrie de route. |
| `ServerReachability.kt` | Test de joignabilité et représentation de l'état du serveur. |
| `SignalQuestApi.kt` | Client des photos, opérateurs, speedtests et rapports SignalQuest. |
| `SignalQuestOperators.kt` | Normalisation, ordre d'affichage et filtres des opérateurs SignalQuest. |
| `SignalQuestSpeedtests.kt` | Parsing et classement des résultats de tests de débit SignalQuest. |

### 4.4 Sauvegarde : `data/backup`

| Fichier | Rôle |
| --- | --- |
| `AppBackup.kt` | Modèles de sections et contenu exportable des préférences/historiques. |
| `AppBackupCodec.kt` | Encode et décode la sauvegarde dans un format JSON versionné. |
| `AppBackupManager.kt` | Coordonne création, import, validation, partage et restauration d'une sauvegarde. |

### 4.5 Génération locale des bases : `data/build`

Ce package est le pipeline de fabrication de bases utilisables hors ligne à partir de sources
brutes. Il est utilisé par le mode de génération locale et par les tests de snapshots.

| Fichier | Rôle |
| --- | --- |
| `AndroidSqlDatabase.kt` | Implémentation SQLite adaptée à Android pour écrire/lire une base pendant le build. |
| `AnfrBuildInputs.kt` | Décrit les fichiers sources ANFR et les entrées nécessaires au pipeline. |
| `AnfrCsvRow.kt` | Modèles d'une ligne CSV ANFR et de son en-tête. |
| `AnfrParsing.kt` | Normalise et convertit les valeurs CSV ANFR : dates, nombres, identifiants et champs optionnels. |
| `AnfrSourceReaders.kt` | Lit les différentes sources ANFR et expose un flux de lignes parsées. |
| `AnfrStatsBuilder.kt` | Calcule les agrégats/statistiques nécessaires à la base ANFR et à l'interface. |
| `BuildProgress.kt` | États, phases et progression publiables pendant une génération longue. |
| `DepartmentStatsBuilder.kt` | Construit les statistiques par département à partir des références administratives. |
| `FrequencyDetailsEncoder.kt` | Encode les détails de fréquences dans le format compact relu par l'application. |
| `GeoTowerDbBuilder.kt` | Assemble les tables et index de la base GeoTower à partir des lignes ANFR. |
| `GeoTowerDbSchema.kt` | Définit le schéma SQL de la base ANFR générée. |
| `IdRegistry.kt` | Stabilise/attribue les identifiants internes des entités durant la génération. |
| `LocalBuildCapability.kt` | Évalue si l'appareil dispose de suffisamment de mémoire/espace/CPU pour un build local. |
| `LocalBuildMetrics.kt` | Modèles des métriques de durée, mémoire et profil appareil du build. |
| `LocalBuildMetricsRecorder.kt` | Enregistre les métriques d'exécution des builds locaux pour les diagnostics. |
| `LocalDbBuildPipeline.kt` | Orchestrateur du téléchargement des sources, parsing, construction, validation, remplacement et nettoyage de la base locale. |
| `LocalDbRebuildOffer.kt` | Modèle de décision affiché à l'utilisateur pour proposer ou non une reconstruction. |
| `OfficialSources.kt` | Référentiel des sources officielles, URLs et métadonnées utilisées par le pipeline. |
| `RadioClassifier.kt` | Classe les lignes radio par service : mobile, broadcast, privé, ferroviaire, FH, satellite, radar ou autre. |
| `RadioDbBuilder.kt` | Construit la base radio à partir des sources radio/stations et applique les masques de services. |
| `RadioDbSchema.kt` | Schéma SQL de la base radio générée. |
| `RadioMaskComputer.kt` | Calcule les masques de technologies/services radio pour les filtres et requêtes. |
| `RadioParsing.kt` | Parse et normalise les fichiers de données radio. |
| `RawSourceDownloader.kt` | Télécharge les fichiers bruts nécessaires à une génération locale. |
| `SqlDatabase.kt` | Abstraction de bas niveau utilisée par les builders SQL, indépendante de Room. |
| `SupRowSink.kt` | Écrit les lignes de supports/entités dans les tables générées en limitant l'usage mémoire. |

### 4.6 Données communautaires et configuration distante

| Fichier | Rôle |
| --- | --- |
| `data/community/CommunityDataPreferences.kt` | Préférences des sources communautaires (photos, données et autorisations d'affichage). |
| `data/community/PhotoReportHistoryStore.kt` | Historique local des signalements de photos. |
| `data/community/SignalQuestPhotoReporter.kt` | Prépare et envoie les signalements de photos à SignalQuest. |
| `data/config/RemoteFeatureFlags.kt` | Charge, met en cache et actualise les feature flags distants et les annonces officielles. |

### 4.7 Calcul de couverture : `data/coverage`

| Fichier | Rôle |
| --- | --- |
| `CoverageComputer.kt` | Point d'entrée partagé pour calculer une couverture autour d'un site depuis l'overlay carte ou l'écran outil. |
| `CoverageEngine.kt` | Moteur de calcul et résolution des sites/émetteurs à prendre en compte. |
| `CoverageEngineFactory.kt` | Construit le moteur avec les sources, paramètres et stratégies adaptées. |
| `CoverageGeo.kt` | Fonctions de géométrie géographique : distances, azimuts, grilles et conversions. |
| `CoverageModels.kt` | Modèles de points, cellules, résultats et paramètres de couverture. |
| `SiteEmitterResolver.kt` | Résout les émetteurs et systèmes radio associés à un support. |
| `TerrainFieldLoader.kt` | Charge les altitudes/élévations nécessaires au calcul de visibilité. |
| `ViewshedSolver.kt` | Détermine la visibilité terrain et la couverture théorique entre émetteur et points. |

### 4.8 Modèles de données : `data/models`

| Fichier | Rôle |
| --- | --- |
| `LiveDbStatusDto.kt` | DTO de statut de la base live retourné par l'API. |
| `LiveSiteDtos.kt` | DTO des sites, antennes et informations de service live. |
| `LiveStatsDtos.kt` | DTO des statistiques live et agrégats associés. |
| `OfflineEntities.kt` | Entités Room de la base ANFR hors ligne et masques de filtrage persistés. |
| `OfflineMapCatalog.kt` | Modèle/catalogue des cartes hors ligne disponibles et de leurs métadonnées. |
| `OfflineMapDto.kt` | DTO des cartes hors ligne échangés avec le serveur. |
| `RadioMapModels.kt` | Modèles d'affichage cartographique radio et masques de services. |
| `SiteHsEntity.kt` | Entité représentant un site hors service dans les données locales. |

### 4.9 Room, SQLite et validation : `data/db`

| Fichier | Rôle |
| --- | --- |
| `AppDatabase.kt` | Déclare la base Room, ses entités, migrations et singleton d'accès. |
| `GeoTowerDao.kt` | Requêtes Room pour sites, supports, antennes, recherche, bounding boxes, clusters et statistiques. |
| `GeoTowerDatabaseIndexes.kt` | Crée/vérifie les index SQL de performance, notamment pour les recherches géographiques. |
| `GeoTowerDatabaseValidator.kt` | Valide la structure et le contenu de la base ANFR installée. |
| `RadioDatabaseValidator.kt` | Valide la présence, version et cohérence de la base radio. |
| `EnbDatabaseValidator.kt` | Valide la base eNB/gNB téléchargée. |
| `DatabaseArtifactIdentity.kt` | Décrit l'identité d'un artefact de base : fichier, version, date et empreinte. |
| `InstalledDatabaseArtifactIdentity.kt` | Lit et persiste l'identité de l'artefact réellement installé. |
| `DatabaseVersionPolicy.kt` | Règles de comparaison et d'acceptation des versions de bases. |
| `DatabaseStorageCleanup.kt` | Nettoie les anciens fichiers, temporaires et artefacts invalides sans supprimer la base active. |
| `LocalDbBuildStatus.kt` | État persistant de la génération locale et de ses erreurs/progressions. |
| `LocalDbProvenance.kt` | Trace l'origine d'une base : source distante, build local, date et paramètres. |
| `MobileDbRowCounts.kt` | Compte les lignes importantes d'une base mobile pour diagnostics et affichage. |
| `DbOperationTimings.kt` | Mesure les durées d'opérations de base et expose les timings aux diagnostics. |
| `EnbDatabaseOperatorCounts.kt` | Calcule les décomptes eNB/gNB par opérateur et technologie. |

### 4.10 Stockages locaux spécialisés

| Fichier | Rôle |
| --- | --- |
| `data/hidden/HiddenSitesStore.kt` | Enregistre les sites masqués par l'utilisateur et permet leur restauration. |
| `data/notifications/NotificationHistoryStore.kt` | Persiste l'historique des notifications importantes. |
| `data/notifications/TripArrivalNotifier.kt` | Construit et déclenche une notification d'arrivée pendant le suivi d'un trajet. |
| `data/share/ShareHistoryStore.kt` | Persiste les exports/partages réalisés depuis l'application. |

### 4.11 Pannes opérateurs : `data/outages`

| Fichier | Rôle |
| --- | --- |
| `AnfrDbSiteGeocoder.kt` | Géocode les sites à partir de la base ANFR locale pour rattacher des pannes à un emplacement. |
| `LocalOutageGenerator.kt` | Génère localement des pannes à partir des données ANFR et des sources opérateurs. |
| `LocalOutageProvider.kt` | Fournit les pannes calculées localement à la carte et aux écrans. |
| `OperatorCsvParser.kt` | Parse les exports CSV de pannes opérateurs. |
| `OperatorOutageFetcher.kt` | Télécharge les données de pannes auprès des sources configurées. |
| `OperatorOutageSource.kt` | Décrit une source opérateur et ses paramètres de téléchargement/parsing. |
| `OutageGenerationProgress.kt` | États de progression de génération des pannes. |
| `OutageLocalCache.kt` | Cache local des pannes générées/téléchargées. |
| `OutageLocalConfig.kt` | Préférences et paramètres du mode pannes locales. |
| `OutageParsing.kt` | Normalise les statuts, dates, identifiants et coordonnées des pannes. |
| `OutageRecord.kt` | Modèle canonique d'une panne et de son rattachement à un site/opérateur. |
| `OutageServerInfo.kt` | Modèle des informations de disponibilité, quota et génération côté serveur. |
| `OutageTechBreakdown.kt` | Agrège les pannes par technologie et codes de statut. |
| `ServerOutageCache.kt` | Cache des résultats de pannes fournis par le serveur. |
| `ServerOutageRebuild.kt` | Demande/suit la reconstruction serveur des données de pannes. |
| `ServerOutageRebuildMonitor.kt` | Observe une reconstruction en cours, son quota, ses erreurs et sa progression. |
| `SitesHsLocalBuilder.kt` | Construit la liste locale des sites hors service à partir des données disponibles. |

### 4.12 Trajets : `data/trip`

| Fichier | Rôle |
| --- | --- |
| `TripPlan.kt` | Modèles du trajet, étapes, options et état d'édition. |
| `TripPlanStore.kt` | Stockage, fusion, sauvegarde et suppression des plans de trajets. |
| `TripRoutePlanner.kt` | Orchestration du calcul d'itinéraire entre les étapes. |
| `TripRouteCalculator.kt` | Calcule un résultat de route à partir des points et préférences. |
| `TripNavigation.kt` | Logique de navigation active : étape courante, progression et vitesse minimale. |
| `TripFollow.kt` | État et transitions du suivi de trajet en temps réel. |
| `TripRouteProgress.kt` | Projection de la position sur la géométrie et calcul de la progression. |
| `TripOrderOptimizer.kt` | Optimise l'ordre des étapes selon distance/coût et contraintes. |
| `TripGeometryCodec.kt` | Encode/décode la géométrie d'itinéraire pour le stockage et le partage. |
| `TripDistance.kt` | Distances géographiques et calculs de distance entre coordonnées. |
| `TripDirectionArrows.kt` | Transforme la géométrie en flèches de direction pour la carte. |
| `TripStepSite.kt` | Rattache une étape aux sites/antennes proches et gère le rayon de correspondance. |
| `TripFormatting.kt` | Formate durées, distances et libellés de trajet. |
| `TripExport.kt` | Exporte un trajet dans un format partageable. |
| `TripSharing.kt` | Construit et décode les liens/intentions de partage de trajet. |

### 4.13 Uploads : `data/upload`

| Fichier | Rôle |
| --- | --- |
| `ExternalPhotoUploadHistoryStore.kt` | Historique des uploads de photos vers des services externes. |
| `ExternalPhotoUploadHistoryValidator.kt` | Vérifie et nettoie les entrées d'historique d'upload. |
| `SignalQuestUploadQueue.kt` | File persistante de rapports/photos SignalQuest, règles de validation et reprise. |
| `SupportPhotoUploadOperators.kt` | Liste et règles des opérateurs autorisés pour les photos de support. |

### 4.14 WorkManager et tâches en arrière-plan : `data/workers`

| Fichier | Rôle |
| --- | --- |
| `DatabaseDownloadWorker.kt` | Télécharge/installe la base ANFR principale en tâche suivie, avec notification de progression. |
| `EnbDatabaseDownloadWorker.kt` | Même rôle pour la base eNB/gNB. |
| `RadioDatabaseDownloadWorker.kt` | Même rôle pour la base radio. |
| `LocalDbBuildWorker.kt` | Exécute le pipeline de génération locale hors UI et publie sa progression. |
| `MapDownloadWorker.kt` | Télécharge les cartes/tuiles hors ligne. |
| `OfflineMapDownloadValidator.kt` | Vérifie les paramètres et l'état d'un téléchargement de carte hors ligne. |
| `DatabaseBulkUpdate.kt` | Regroupe les opérations de remplacement/rafraîchissement des bases. |
| `DownloadNotificationCenter.kt` | Centralise les notifications de téléchargement et leurs actions. |
| `OperationPauseStore.kt` | Persiste l'état pause/reprise des opérations longues. |
| `UpdateCheckWorker.kt` | Vérifie périodiquement les mises à jour de l'application. |
| `UpdateCheckScheduler.kt` | Programme ou annule le worker de vérification de mise à jour. |
| `AppUpdateNotifier.kt` | Affiche la notification/bandeau correspondant à une mise à jour disponible. |
| `OutageGenerationWorker.kt` | Lance la génération locale des pannes en arrière-plan. |
| `OutageBackgroundScheduler.kt` | Programme la génération périodique ou le travail ponctuel des pannes. |
| `PhotoReportCheckWorker.kt` | Vérifie l'état des rapports de photos envoyés. |
| `PhotoReportCheckScheduler.kt` | Programme les vérifications de rapports photo. |
| `PhotoReportNotifier.kt` | Notifie la réussite, l'échec ou la progression d'un rapport photo. |
| `SignalQuestUploadWorker.kt` | Traite la file d'uploads SignalQuest avec reprise et notification. |
| `SignalQuestUploadScheduler.kt` | Programme les uploads différés SignalQuest. |
| `TripReminderWorker.kt` | Déclenche les rappels programmés d'un trajet. |
| `TripReminderScheduler.kt` | Programme et annule les rappels de trajet. |
| `TripReminderActionReceiver.kt` | Reçoit les actions « ouvrir », « reporter » ou « ignorer » depuis la notification de trajet. |

### 4.15 Modèles et règles radio : `radio`

| Fichier | Rôle |
| --- | --- |
| `radio/RadioModels.kt` | Modèles métier des opérateurs, technologies, bandes, duplex, allocations et résultats de débit. |
| `radio/RadioThroughputEngine.kt` | Estime les débits LTE/5G à partir de bande passante, MIMO, modulation, agrégation et hypothèses documentées. |
| `radio/SpectrumAllocationsFrMetro.kt` | Référentiel des allocations de fréquences en France métropolitaine. |

### 4.16 Services Android : `services`

| Fichier | Rôle |
| --- | --- |
| `GeoTowerCarAppService.kt` | Service Android Auto/Car App commun aux hôtes projeté et Automotive. |
| `GeoTowerCarSession.kt` | Session Car App : templates, navigation et accès aux sites proches dans l'interface voiture. |
| `LiveTrackingController.kt` | Vérifie les permissions/préférences et démarre, arrête ou rafraîchit le suivi live. |
| `LiveTrackingService.kt` | Service foreground de suivi GPS : recherche d'antennes proches, progression, notifications enrichies et photos live. |
| `LiveSitePhotoSelector.kt` | Sélectionne la meilleure photo live selon opérateur, source, favoris, visibilité et cache. |

## 5. Interface Compose : `ui`

### 5.1 Thème et navigation

| Fichier | Rôle |
| --- | --- |
| `ui/theme/Color.kt` | Palettes de couleurs, modes clair/sombre et couleurs accessibles. |
| `ui/theme/GeoTowerUiStyle.kt` | Fournisseur du style global : Material 3, mode One UI, palette choisie et transitions. |
| `ui/navigation/SafeBackNavigation.kt` | Navigation arrière défensive pour éviter les doubles retours ou destinations invalides. |

### 5.2 Écrans principaux

| Package / fichier | Rôle |
| --- | --- |
| `screens/splash/SplashScreen.kt` | Écran de démarrage et initialisations visuelles. |
| `screens/onboarding/FirstStartScreen.kt` | Parcours de première ouverture, permissions et choix initiaux. |
| `screens/home/HomeScreen.kt` | Accueil et accès aux pages principales. |
| `screens/home/HomeMenuReorder.kt` | Réordonnancement des entrées du menu d'accueil. |
| `screens/home/HomeHelpButtonDrag.kt` | Positionnement/glissement du bouton d'aide de l'accueil. |
| `screens/help/HelpScreen.kt` | Aide intégrée et explication des fonctions de l'application. |
| `screens/map/MapScreen.kt` | Carte principale : tuiles, marqueurs, clusters, filtres, position, overlays et interactions. |
| `screens/map/MapViewModel.kt` | État et chargements de la carte : sites, filtres, zoom, sélection, trajets et couverture. |
| `screens/map/AntennaMapToolBox.kt` | Barre d'outils des actions de carte. |
| `screens/map/LocationMarkerPainter.kt` | Dessin du marqueur de position utilisateur. |
| `screens/map/MapSettingsSheet.kt` | Panneau des options cartographiques. |
| `screens/map/MapTimeSliderBar.kt` | Curseur temporel de visualisation des données. |
| `screens/map/TheoreticalCoverageOverlay.kt` | Overlay de couverture théorique sur la carte. |
| `screens/map/TripArrivalSheet.kt` | Panneau d'arrivée et informations de l'étape atteinte. |
| `screens/map/TripFollowBar.kt` | Barre de suivi de trajet en cours. |
| `screens/map/TripMapIcons.kt` | Icônes spécifiques aux étapes et éléments de trajet. |
| `screens/map/TripMapMode.kt` | Modes d'affichage de la carte pendant la planification/le suivi. |
| `screens/map/TripPlannerBar.kt` | Barre de commandes du planificateur. |
| `screens/map/TripViewBar.kt` | Barre d'actions de visualisation d'un trajet existant. |
| `screens/emitters/NearEmittersScreen.kt` | Liste des émetteurs proches de la position ou d'un point. |
| `screens/emitters/SiteDetailScreen.kt` | Fiche détaillée d'une antenne/site. |
| `screens/emitters/SupportDetailScreen.kt` | Fiche détaillée d'un support regroupant ses antennes. |
| `screens/emitters/SupportSiteWrapperScreen.kt` | Adaptateur de navigation entre les anciennes fiches site et la fiche support. |
| `screens/emitters/ElevationProfileScreen.kt` | Profil altimétrique entre l'utilisateur et un support. |
| `screens/emitters/ElevationProfileUtils.kt` | Calculs et transformations utilisés par le profil altimétrique. |
| `screens/emitters/FrequencyReferenceScreen.kt` | Référence des bandes/fréquences et de leurs libellés. |
| `screens/emitters/SignalQuestUploadScreen.kt` | Sélection et envoi de photos/rapports SignalQuest. |
| `screens/emitters/SiteSpeedtestsScreen.kt` | Résultats de tests de débit d'un site. |
| `screens/emitters/ThroughputCalculatorScreen.kt` | Outil d'estimation de débit théorique. |
| `screens/coverage/TheoreticalCoverageScreen.kt` | Écran complet de calcul/visualisation de couverture théorique. |
| `screens/compass/CompassScreen.kt` | Boussole et orientation vers les sites/azimuts. |
| `screens/stats/StatisticsScreen.kt` | Vue globale des statistiques de l'observatoire. |
| `screens/stats/DepartmentStatsScreen.kt` | Statistiques par département et navigation vers les détails. |
| `screens/about/AboutScreen.kt` | Informations sur l'application, versions et liens. |
| `screens/about/AboutDataSources.kt` | Présentation des sources de données utilisées. |
| `screens/about/ReleaseNotes.kt` | Notes de version affichables dans l'application. |
| `screens/about/PhotoUploadHistorySection.kt` | Section d'historique des uploads photo dans « À propos ». |
| `screens/diagnostic/DiagnosticScreen.kt` | Diagnostic réseau, bases, permissions, version et environnement. |
| `screens/diagnostic/DiagnosticModels.kt` | Modèles des lignes/états affichés par le diagnostic. |
| `screens/terms/TermsScreen.kt` | Affichage des conditions d'utilisation localisées. |

### 5.3 Android Automotive : `ui/screens/car`

| Fichier | Rôle |
| --- | --- |
| `CarHomeScreen.kt` | Écran d'accueil de l'expérience voiture. |
| `CarAntennaMapScreen.kt` | Carte d'antennes dans la surface de l'écran voiture. |
| `CarAntennaMapSurfaceCallback.kt` | Callback de dessin/redimensionnement de la surface cartographique Car App. |
| `CarNearbySitesScreen.kt` | Liste des sites proches pour Android Auto/Automotive. |
| `CarNearbySitesLoader.kt` | Chargement asynchrone des sites proches. |
| `CarSiteDetailScreen.kt` | Détail d'un site dans l'interface voiture. |
| `CarSiteListItem.kt` | Élément de liste compact pour un site. |
| `CarOperatorGridIcon.kt` | Icône d'opérateur dans la grille voiture. |
| `CarScreenUtils.kt` | Utilitaires communs de taille, texte et contraintes Car App. |
| `CarErrorScreen.kt` | Écran d'erreur récupérable côté voiture. |
| `CarUnavailableScreen.kt` | Écran lorsque les données/fonctions ne sont pas disponibles en voiture. |

### 5.4 Paramètres, historique et trajets

| Fichier | Rôle |
| --- | --- |
| `screens/settings/SettingsScreen.kt` | Point d'entrée des réglages et orchestration des sections. |
| `screens/settings/BackupScreen.kt` | Export/import des réglages et données sauvegardables. |
| `screens/settings/CommunityDataSettingsSheet.kt` | Choix des sources communautaires et autorisations photos. |
| `screens/settings/EmbeddedSiteBlocksSettingsSheet.kt` | Personnalisation des blocs intégrés dans les fiches site/support. |
| `screens/settings/ExternalLinksSettingsSheet.kt` | Choix des liens externes visibles. |
| `screens/settings/HiddenSitesScreen.kt` | Gestion des sites masqués. |
| `screens/settings/HistoriesScreen.kt` | Accès aux différents historiques locaux. |
| `screens/settings/LocalModeScreen.kt` | Configuration du mode local et de la génération de base. |
| `screens/settings/LocalModeLevelControls.kt` | Contrôles des niveaux de consommation/traitement local. |
| `screens/settings/MapFiltersDefaultsSheet.kt` | Réinitialisation et édition des filtres par défaut de la carte. |
| `screens/settings/NotificationHistoryScreen.kt` | Historique des notifications. |
| `screens/settings/OutageLocalControls.kt` | Paramètres de génération/consultation des pannes locales. |
| `screens/settings/PagesCustomizationSheet.kt` | Choix et ordre des pages visibles. |
| `screens/settings/PhotoReportsScreen.kt` | Suivi des rapports photo. |
| `screens/settings/PhotosFavoritesScreen.kt` | Gestion des photos favorites. |
| `screens/settings/PreferenceProfilesSheet.kt` | Création, import, export et activation de profils de préférences. |
| `screens/settings/ShareHistoryScreen.kt` | Historique des partages. |
| `screens/settings/SharePreferencesSheet.kt` | Préférences de contenu et de mise en forme des exports. |
| `screens/trips/TripsScreen.kt` | Liste et édition des trajets enregistrés. |
| `screens/trips/TripNaming.kt` | Génération/normalisation des noms de trajets. |
| `screens/trips/TripScheduleDialog.kt` | Dialogue de programmation d'un rappel de trajet. |
| `screens/trips/TripSparkline.kt` | Mini-graphique de synthèse d'un trajet. |

### 5.5 Composants réutilisables : `ui/components`

Ces fichiers sont des composables ou utilitaires d'interface partagés par plusieurs écrans.

| Fichier | Rôle |
| --- | --- |
| `AnnouncedOnlyStationBanner.kt` | Bandeau signalant une station annoncée uniquement. |
| `ApiServerControls.kt` | Sélection de l'API principale ou miroir. |
| `AppearanceOptionsBlock.kt` | Options d'apparence, taille et thème. |
| `AppLogoDrawingText.kt` / `AppLogoImage.kt` | Affichage et choix des logos GeoTower/GeoRadio/Fun. |
| `ColorPaletteSelector.kt` | Sélection et aperçu de la palette de couleurs utilisateur. |
| `HomeLogoSelectorBlock.kt` | Sélection du logo de la page d'accueil. |
| `GeoTowerBackTopBar.kt` / `GeoTowerBreadcrumbBar.kt` | Barres supérieures avec retour et fil d'Ariane. |
| `GeoTowerLoadingMessage.kt` / `LocationUnavailableBanner.kt` | États de chargement et d'absence de position. |
| `GeoTowerPullToRefreshBox.kt` / `DatabaseSectionRefresh.kt` | Rafraîchissement manuel et indicateurs de sections. |
| `GeoTowerScrollbar.kt` / `GeoTowerDateScrollbar.kt` / `GeoTowerScrollEdgeButtons.kt` | Navigation rapide dans de longues listes. |
| `GeoTowerSwitch.kt` / `DialogActionButtons.kt` / `SafeClick.kt` | Contrôles et interactions sûres partagés. |
| `ResponsiveDualPaneLayout.kt` | Mise en page adaptative téléphone/tablette/voiture. |
| `SimpleModeDrawer.kt` / `NavigationBottomSheet.kt` | Navigation latérale ou par panneau inférieur. |
| `SecureScreenEffect.kt` | Application des protections d'écran sensible. |
| `SharedLoaders.kt` | Chargeurs Compose et utilitaires d'état communs. |
| `SharedSettingsComponents.kt` / `UnitSettingsSheet.kt` | Contrôles réutilisables des réglages et unités. |
| `FadingEdgeModifiers.kt` / `ReorderableDragState.kt` | Effets visuels et état de glisser-déposer. |
| `GlobalUploadOverlay.kt` | Overlay global indiquant un upload en cours. |
| `LiveNotificationCard.kt` / `LiveDatabaseUsageWarningDialog.kt` | Affichage de l'état live et avertissements de consommation serveur. |
| `ServerUnreachableBanner.kt` | Alerte lorsque l'API est inaccessible. |
| `DatabaseDownloadCard.kt` / `EnbDatabaseDownloadCard.kt` / `RadioDatabaseDownloadCard.kt` | Cartes de téléchargement/validation des bases respectives. |
| `LocalDbBuildCard.kt` / `LocalDbBuildStatusState.kt` | Carte et état de génération locale. |
| `MapDownloadCard.kt` / `MapLocationZoomCard.kt` / `MappingOptionsBlock.kt` | Téléchargement hors ligne, position/zoom et options de carte. |
| `OperationPauseButton.kt` | Pause/reprise d'une opération WorkManager. |
| `DatabaseOperatorCountsTable.kt` / `OperatorsListSection.kt` | Tableaux et listes de statistiques opérateurs. |
| `DbOperationTimingText.kt` | Présentation des durées de requêtes/base. |
| `OutageDownloadCard.kt` / `OutageLocalGenerationControls.kt` / `OutageServerRebuildControls.kt` | Contrôles des pannes distantes, locales et serveur. |
| `OutageTechBreakdownTable.kt` | Répartition des pannes par technologie. |
| `PageCustomizationDiscovery.kt` | Découverte des pages/blocs personnalisables. |
| `SupportDetailsSection.kt` / `SupportRadioPresenceCard.kt` | Sections et présence radio d'un support. |
| `SiteAddressBlock.kt` / `SiteDatesBlock.kt` / `SiteIdentifiersBlock.kt` | Blocs adresse, dates et identifiants d'une fiche. |
| `SiteNetworkIdsBlock.kt` / `SitePanelHeightsBlock.kt` | Réseaux/identifiants et hauteurs de panneaux. |
| `SiteFrequenciesBlock.kt` | Détails et regroupement des fréquences. |
| `SiteStatusCard.kt` | Statut de service d'un site et de ses technologies. |
| `SiteExternalLinks.kt` / `SiteExternalLinksBlock.kt` | Construction et affichage des liens externes. |
| `SiteSharePagination.kt` | Pagination des fiches lors de la génération d'un partage. |
| `SharedMiniMapCard.kt` | Mini-carte réutilisable dans les fiches et exports. |
| `SpeedtestCard.kt` | Carte d'affichage des mesures de débit. |
| `ThroughputShared.kt` / `ShareThroughputCalculatorBlock.kt` | Composants partagés de calcul et de présentation du débit. |
| `RadioUsageIcon.kt` | Icône représentant l'usage/service radio. |
| `PhotoAsyncImage.kt` / `CommunityPhotosViewer.kt` | Chargement, galerie, favoris, EXIF, export et partage des photos communautaires. |
| `PhotoReportDialog.kt` | Dialogue de signalement d'une photo. |
| `PdfReportNotifier.kt` / `PdfDownloadNotice.kt` | Feedback de génération/téléchargement de rapports PDF. |
| `GeoTowerReportPdf.kt` | Génération du rapport PDF détaillé. |
| `ShareImageGenerator.kt` / `MapShareGenerator.kt` | Génération d'images partageables génériques ou cartographiques. |
| `ElevationProfileShareGenerator.kt` | Génération d'une image de partage du profil altimétrique. |
| `TheoreticalCoverageShareGenerator.kt` | Génération d'une image de partage de la couverture théorique. |
| `MapSpeedometer.kt` | Compteur de vitesse dans la carte. |
| `CityStatsDetailSheet.kt` | Panneau détaillé des statistiques d'une commune/zone. |
| `EnbSourceAttribution.kt` | Attribution de la source eNB utilisée. |
| `LiveDatabaseUsageWarningDialog.kt` | Confirmation/avertissement lors d'un accès à une donnée live coûteuse. |

Les répertoires `ui/components/detail_parts` et `ui/components/icons` sont réservés à des
composants spécialisés ; ils sont actuellement vides ou ne contiennent pas de fichier Kotlin
dans l'arborescence analysée.

## 6. Utilitaires transverses : `utils`

| Fichier | Rôle |
| --- | --- |
| `AppConfig.kt` | Configuration globale, niveaux de mode local/économie et clés de préférences. |
| `AppDistribution.kt` | Identifie l'origine de l'installation et les variantes de distribution. |
| `AppFileLog.kt` / `AppLogger.kt` | Journalisation persistante et logs structurés, notamment utiles pour Android Auto. |
| `AppLocale.kt` | Gestion de la langue et du contexte localisé de l'application. |
| `AppUiMode.kt` | Modes d'interface, notamment affichage simplifié/One UI. |
| `AppIconManager.kt` | Activation des alias d'icône et choix de l'icône de launcher. |
| `AppLogoDrawingResources.kt` | Ressources nécessaires au dessin des logos. |
| `AppNotifications.kt` / `NotificationIconResources.kt` | Canaux, icônes et helpers de notifications. |
| `SystemPower.kt` | Détecte l'état d'économie d'énergie du système et le rend disponible aux politiques de rafraîchissement. |
| `AnfrDisplayText.kt` | Conversion des valeurs ANFR brutes en textes lisibles. |
| `HelpDisplayText.kt` | Textes d'aide et libellés techniques contextualisés. |
| `CommuneNameMatching.kt` | Normalisation et rapprochement de noms de communes. |
| `DepartmentCodes.kt` / `FrenchAdminAreas.kt` | Référentiels des départements et zones administratives françaises. |
| `DepartmentStatsPreferences.kt` / `StatsPreferences.kt` | Préférences d'affichage et ordre des statistiques. |
| `DeviceProfile.kt` | Profil de capacité/forme de l'appareil pour adapter l'UI et les traitements. |
| `DistanceFormatter.kt` | Formatage des distances en unités métriques ou miles. |
| `FrequencyAzimuthFilter.kt` | Filtrage des fréquences et azimuts affichés. |
| `FrequencyDetailsParser.kt` | Parse les détails de fréquences encodés dans les données ANFR. |
| `FrequencyFilterSelection.kt` | Modèle de sélection de filtres de bandes/fréquences. |
| `FrequencyStatusParser.kt` | Interprète le statut d'activité d'une fréquence. |
| `LocalizedDateLabels.kt` / `SiteDateFormatter.kt` | Formatage des dates et libellés localisés. |
| `LocationHelper.kt` / `LocationPermissionState.kt` | Accès à la localisation et suivi des permissions/états système. |
| `MapFilterDefaults.kt` / `MapFilterEffects.kt` | Valeurs par défaut et application des filtres de carte aux sites. |
| `MapUtils.kt` | Sources de tuiles, marqueurs, clusters, couleurs, azimuts et helpers de dessin. |
| `NetworkMonitor.kt` | Observe la connectivité et la présence d'un transport Internet validé. |
| `OfflineMapDisplayNames.kt` | Libellés propres des cartes hors ligne. |
| `OperatorColors.kt` / `OperatorLogos.kt` | Référentiel des opérateurs, couleurs, alias et logos. |
| `PageCustomizationPrefs.kt` / `PageScrollPrefs.kt` | Préférences de pages visibles, ordre et aide progressive. |
| `PreferenceProfileManager.kt` | Création, import/export, activation et synchronisation de profils de préférences. |
| `PowerProfile.kt` | Politique d'économie d'énergie : GPS, animations, carte, couverture et fréquence de rafraîchissement. |
| `RadioBandFormatter.kt` | Formatage des codes de bandes et fréquences radio. |
| `SpectrumDisplayFormatter.kt` | Présentation des plages de spectre et largeurs de bande. |
| `TechnologyFormatter.kt` | Formatage des technologies 2G/3G/4G/5G/FH. |
| `ThroughputDisplayText.kt` | Traduction des hypothèses, avertissements et unités du calcul de débit. |
| `TypedPreferences.kt` | Accès typé aux préférences pour carte, fiches, statistiques, accueil, widgets et suivi live. |

### Localisation et mouvement : `utils/location`

| Fichier | Rôle |
| --- | --- |
| `FusedMyLocationProvider.kt` | Fournit les positions GPS via Google Play Services avec gestion du fallback. |
| `PedestrianDeadReckoning.kt` | Estime le déplacement piéton entre deux fixes GPS à partir des capteurs et de la cadence. |
| `SmoothLocationEngine.kt` | Lisse les positions et extrapole brièvement le mouvement pour une carte plus stable. |

## 7. Widgets : `widget`

| Fichier | Rôle |
| --- | --- |
| `AntennaWidget.kt` | Widget Glance affichant les antennes proches, opérateurs, distances et actions d'ouverture. |
| `AntennaWidgetReceiver.kt` | Receiver du widget standard et point d'intégration Android. |
| `AntennaWidgetMediumReceiver.kt` | Receiver de la variante medium. |
| `AntennaWidgetLargeReceiver.kt` | Receiver de la variante large. |
| `AntennaMapWidget.kt` | Widget Glance affichant une image de carte autour de la position. |
| `AntennaMapWidgetReceiver.kt` | Receiver du widget carte. |
| `AntennaMapWidgetRenderer.kt` | Rend hors écran les tuiles, sites, marqueurs, azimuts et cônes en bitmap. |
| `AntennaWidgetWorker.kt` | Récupère position/données, prépare le JSON et les images destinés aux widgets. |
| `WidgetUpdateScheduler.kt` | Programme les mises à jour périodiques et gère la présence de widgets épinglés. |

## 8. Ressources Android : `app/src/main/res`

| Répertoire | Contenu et rôle |
| --- | --- |
| `drawable/` | 36 XML et 33 PNG : icônes de carte, formes de pylônes/supports, logos opérateurs et logos des applications GeoTower/GeoRadio/Fun. |
| `drawable-nodpi/` | Aperçus bitmap de widgets sans mise à l'échelle de densité. |
| `drawable-night-nodpi/` | Versions sombres des aperçus de widgets. |
| `app/src/main/ic_launcher_funny-playstore.png`, `ic_launcher_georadio-playstore.png`, `ic_launcher_geotower-playstore.png` | Assets haute résolution destinés à la fiche/visuel de publication Play Store ; ils sont à la racine de `main`, pas dans un dossier `res`. |
| `mipmap-anydpi-v26/` | Icônes adaptatives Android 8+ pour les trois identités d'application. |
| `mipmap-mdpi/`, `hdpi/`, `xhdpi/`, `xxhdpi/`, `xxxhdpi/` | PNG/WebP de launcher par densité ; chaque densité contient les variantes GeoTower, GeoRadio et Funny. |
| `values/` | Couleurs, thèmes, pluriels, chaînes par défaut et arrière-plans d'icônes. |
| `values-de/`, `values-en/`, `values-es/`, `values-fr/`, `values-it/`, `values-pt/` | Traductions et pluriels localisés. `values/` sert de fallback. |
| `raw/` | Conditions par défaut et certificat racine `isrg_root_x1.pem`. |
| `raw-de/`, `raw-es/`, `raw-fr/`, `raw-it/`, `raw-pt/` | Versions localisées de `terms.txt`. |
| `xml/` | Métadonnées de widgets, description Automotive, règles de backup/extraction, chemins partageables, locales, sécurité réseau et raccourcis. |

Fichiers XML importants :

| Fichier | Rôle |
| --- | --- |
| `res/xml/antenna_widget_info.xml`, `antenna_widget_medium_info.xml`, `antenna_widget_large_info.xml` | Décrivent les tailles, contraintes et informations des widgets d'antennes. |
| `res/xml/antenna_map_widget_info.xml` | Décrit le widget carte. |
| `res/xml/automotive_app_desc.xml` | Déclare les capacités de l'expérience Android Automotive. |
| `res/xml/backup_rules.xml` / `data_extraction_rules.xml` | Contrôlent les données incluses/exclues des sauvegardes Android. |
| `res/xml/file_paths.xml` | Autorise le partage de fichiers générés via FileProvider. |
| `res/xml/locales_config.xml` | Déclare les langues supportées. |
| `res/xml/network_security_config.xml` | Paramètre la sécurité réseau de l'application. |
| `res/xml/shortcuts.xml` | Raccourcis de lancement Android. |

## 9. Manifestes et variantes

| Fichier | Rôle |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Déclarations communes : Internet, localisation, notifications, foreground services, wake lock, Android Auto projeté, FileProvider, services, workers, receivers et alias d'icônes. |
| `app/src/mobile/AndroidManifest.xml` | Déclarations spécifiques à l'artefact téléphone/mobile. |
| `app/src/automotive/AndroidManifest.xml` | Déclarations spécifiques à Android Automotive OS : feature automobile et activité de lancement voiture. |

La variante `mobile` sert aussi à Android Auto projeté depuis le téléphone. La variante
`automotive` cible Android Automotive OS et relève le `versionCode` grâce au décalage défini dans
`app/build.gradle.kts`.

## 10. Thèmes cartographiques : `app/src/main/assets/themes`

| Chemin | Contenu |
| --- | --- |
| `themes/Elevate.xml` | Thème Mapsforge utilisé pour le rendu de carte avec relief/élévation et styles associés. |
| `themes/freizeitkarte-v5.xml` | Thème principal Freizeitkarte/Mapsforge. |
| `themes/ele_res/` | Environ 500 SVG de ressources de rendu : fonds (`wmbg_*`), premiers plans (`wmfg_*`), points (`p_*`) et symboles (`s_*`) pour relief, occupation du sol, équipements et éléments OSM. |
| `themes/patterns/` | 45 motifs SVG pour zones surfaciques : forêts, marais, carrières, plages, cimetières, réserves, etc. |
| `themes/symbols/` | 251 symboles SVG pour équipements, services, transports, relief, bâtiments et repères cartographiques. |

Les noms de ces SVG sont intentionnels : `p_` correspond aux patterns/points de rendu Elevate,
`s_` aux symboles, `wmbg_` aux fonds et `wmfg_` aux premiers plans. Il n'y a donc pas de logique
Kotlin propre à documenter fichier par fichier dans ces ressources ; leur contenu est consommé
par le moteur Mapsforge à partir des deux thèmes XML.

## 11. Tests

Les tests unitaires sont organisés selon les mêmes domaines que le code de production. Ils
valident surtout les parsers, codecs, règles métier, calculs géographiques, politiques de version
et composants d'affichage sensibles.

### 11.1 Tests de données, API et bases

| Package | Fichiers et responsabilité couverte |
| --- | --- |
| `data/` | `AnfrRepositoryFrequencyDetailsTest.kt` vérifie l'extraction des fréquences ; `AnfrRepositoryLiveNearbyRadiusTest.kt` les rayons de recherche live ; `MacroClusterGrouperTest.kt` le regroupement géographique. |
| `data/api/` | `ApiEndpointsTest.kt` endpoints et miroir ; `AppUpdateCheckerTest.kt` détection de version ; `CellMapperLinksTest.kt` URLs CellMapper ; `CommuneReferenceApiTest.kt` références de communes ; `DatabaseDownloaderTest.kt` téléchargement/installation ; `DownloadManifestVerifierTest.kt` validation du manifeste ; `NetworkingParsersTest.kt` parsers réseau ; `RadioDatabaseDownloaderTest.kt` téléchargement radio ; `RoutePortionsParserTest.kt` portions de route ; `SignalQuestOperatorsTest.kt` opérateurs ; `SignalQuestSpeedtestsResponseTest.kt` réponses speedtests. |
| `data/backup/` | `AppBackupCodecTest.kt` encode/décode et compatibilité des sauvegardes. |
| `data/models/` | `FrequencyDetailsCodecTest.kt` codec fréquences ; `OfflineMapCatalogTest.kt` catalogue cartes ; `RadioFilterMasksTest.kt` masques radio ; `RadioReportSummaryTest.kt` résumé des rapports radio. |
| `data/db/` | `DatabaseArtifactIdentityPolicyTest.kt`, `DatabaseStorageCleanupTest.kt`, `DatabaseVersionPolicyTest.kt`, `DbOperationTimingsTest.kt` et `EnbDatabaseOperatorCountsTest.kt` couvrent identité, nettoyage, versions, métriques et statistiques eNB. |

### 11.2 Tests du pipeline de build local et des pannes

| Package | Fichiers et responsabilité couverte |
| --- | --- |
| `data/build/` | `AnfrParsingTest.kt` parsing ANFR ; `AnfrSourceReadersTest.kt` lecture des sources ; `BuilderOutputSnapshotTest.kt` sorties stables ; `BuildSnapshotFixture.kt` fixture ; `CanonicalDump.kt` comparaison canonique ; `DepartmentStatsBuilderTest.kt` stats départements ; `FrequencyDetailsEncoderTest.kt` encodage fréquences ; `GeoTowerDbBuilderTest.kt` construction ANFR ; `GeoTowerDbSchemaTest.kt` schéma ; `JdbcSqlDatabase.kt` backend SQL de test ; `LocalBuildCapabilityTest.kt` éligibilité appareil ; `LocalBuildMetricsTest.kt` métriques ; `OfficialSourcesTest.kt` référentiel de sources ; `RadioClassifierTest.kt` classification ; `RadioDbBuilderTest.kt` construction radio ; `RadioMaskComputerTest.kt` masques ; `RadioMutualizedBuildTest.kt` mutualisation ; `RadioParsingTest.kt` parsing radio ; `RawSourceDownloaderTest.kt` téléchargement des sources brutes. |
| `data/outages/` | `AnfrDbSiteGeocoderTest.kt` géocodage ; `LocalOutageGeneratorTest.kt` génération ; `LocalOutageProviderTest.kt` fourniture ; `OperatorCsvParserTest.kt` CSV opérateurs ; `OutageLocalCacheTest.kt` cache ; `OutageLocalConfigTest.kt` configuration ; `OutageTechBreakdownTest.kt` ventilation technique ; `ServerOutageCacheTest.kt` cache serveur ; `ServerOutageRebuildTest.kt` reconstruction serveur ; `SitesHsLocalBuilderTest.kt` construction des sites HS. |
| `data/workers/` | `OfflineMapDownloadValidatorTest.kt` validation cartes ; `OperationPauseStoreTest.kt` pause/reprise ; `TripReminderSchedulerTest.kt` planification des rappels. |
| `data/upload/` | `SignalQuestUploadRulesTest.kt` vérifie les règles de validation/normalisation de la file ; `SignalQuestUploadTargetsTest.kt` vérifie l'encodage et le décodage des cibles d'upload. |
| `data/community/` | `CommunityDataPreferencesTest.kt` préférences des sources communautaires. |
| `data/config/` | `RemoteFeatureFlagsTest.kt` cache et règles des flags distants. |
| `data/coverage/` | `CoverageEngineTest.kt` calcul de couverture et visibilité. |

### 11.3 Tests des trajets

| Fichier | Rôle |
| --- | --- |
| `TripBoundingBoxTest.kt` | Bounding box de trajet. |
| `TripContentComparisonTest.kt` | Comparaison de contenu de plans. |
| `TripDirectionArrowsTest.kt` | Flèches et orientations. |
| `TripEmptyDraftTest.kt` | Brouillon vide et valeurs par défaut. |
| `TripExportTest.kt` | Export d'un trajet. |
| `TripFollowTest.kt` | États du suivi. |
| `TripGeometryCodecTest.kt` | Encodage/décodage de géométrie. |
| `TripNavigationTest.kt` | Progression et navigation. |
| `TripOrderOptimizerTest.kt` | Optimisation de l'ordre. |
| `TripPlanTest.kt` | Modèle et règles du plan. |
| `TripRoutePlannerTest.kt` | Planification de route. |
| `TripRouteProgressTest.kt` | Projection et progression sur route. |
| `TripSchedulingRulesTest.kt` | Règles des rappels. |
| `TripStepSiteTest.kt` | Association étapes/sites. |
| `TripTestFixtures.kt` | Données communes de test. |

Ces fichiers se trouvent dans `app/src/test/java/fr/geotower/data/trip/`.

### 11.4 Tests radio, UI et utilitaires

| Package | Fichiers et responsabilité couverte |
| --- | --- |
| `radio/` | `RadioThroughputEngineTest.kt` vérifie l'estimation des débits et les cas LTE/NR/DSS. |
| `ui/components/` | `PdfReportPaginationTest.kt` pagination PDF ; `SafeClickTest.kt` clic protégé ; `SiteFrequenciesBlockParsingTest.kt` fréquences ; `SiteServiceStatusGridTest.kt` grille de statuts ; `SiteSharePaginationTest.kt` pagination de partage ; `ThroughputSharedTest.kt` composants de débit. |
| `ui/screens/trips/` | `TripSparklineTest.kt` rendu/calcul du mini-graphique. |
| `utils/` | `AndroidI18nResourcesTest.kt`, `DepartmentCodesTest.kt`, `DisplayStyleSplitRuleTest.kt`, `DistanceFormatterTest.kt`, `FrenchAdminAreasTest.kt`, `FrequencyAzimuthFilterTest.kt`, `FrequencyDetailsParserTest.kt`, `FrequencyFilterSelectionTest.kt`, `FrequencyStatusParserTest.kt`, `LocalizedDateLabelsInstantTest.kt`, `LocalModeLevelScaleTest.kt`, `MapFilterDefaultsTest.kt`, `OperatorColorsTest.kt`, `RadioBandFormatterTest.kt`, `SiteDateFormatterTest.kt`, `SpectrumDisplayFormatterTest.kt`, `StatsPreferencesTest.kt`, `TechnologyFormatterTest.kt`, `TypedPreferencesTest.kt` et `ZbOutagePropagationTest.kt` couvrent respectivement localisation, référentiels, formats, filtres, préférences, couleurs, fréquences, technologies et propagation des pannes ZB. |
| `utils/location/` | `PedestrianDeadReckoningTest.kt` et `SmoothLocationEngineTest.kt` valident le mouvement interpolé et la fusion GPS/capteurs. |
| `widget/` | `WidgetUpdateSchedulerTest.kt` vérifie la planification et l'annulation des mises à jour. |
| racine `fr/geotower/` | `OrientationPolicyTest.kt` valide la politique d'orientation de l'application. |

Les fichiers `app/src/test/resources/golden/mobile.txt` et `radio.txt` sont des sorties de
référence (« golden files ») utilisées pour détecter une modification involontaire du résultat
des builders mobile et radio.

Le test instrumenté `app/src/androidTest/java/fr/geotower/services/CarAppHandshakeTest.kt` vérifie
le handshake et l'intégration minimale du service Car App sur environnement Android.

## 12. Manifestes de test et composants externes

| Chemin | Rôle |
| --- | --- |
| `osm/org/osmdroid/views/MapController.java` | Classe Java locale appartenant à l'intégration osmdroid ; elle adapte le contrôle de carte aux besoins de GeoTower. |
| `prototype-carte/index.html` | Prototype web indépendant pour expérimenter l'affichage de carte. |
| `prototype-carte/serve-carte.ps1` | Lance un serveur local PowerShell pour le prototype. |
| `prototype-carte/ouvrir-carte.bat` | Raccourci Windows pour ouvrir le prototype/serveur local. |

## 13. Documentation et outils serveur

| Chemin | Rôle |
| --- | --- |
| `docs/agent-ia-plan-planificateur-trajet-geotower-2026-08-11.md` | Notes de conception/plan d'implémentation du planificateur de trajets assisté. |
| `docs/i18n/de/strings.xml`, `en/strings.xml`, `es/strings.xml`, `fr/strings.xml`, `it/strings.xml`, `pt/strings.xml` | Copies de travail des chaînes traduites, utiles pour préparer ou comparer les ressources Android localisées. |
| `docs/server/build_site_changes.py` | Outil Python de suivi des publications ANFR : recherche/téléchargement des CSV, compression, comparaison successive des publications, production des diffs JSONL et des points de carte, garde-fous contre les publications incomplètes et publication optionnelle dans le dossier d'import. |

## 14. Où commencer pour comprendre le projet ?

Pour une lecture progressive, l'ordre conseillé est :

1. `README.md` puis `app/build.gradle.kts` pour comprendre la cible Android et les dépendances ;
2. `GeoTowerApp.kt` et `MainActivity.kt` pour le démarrage et la navigation ;
3. `data/AnfrRepository.kt`, `data/db/AppDatabase.kt` et `data/db/GeoTowerDao.kt` pour le chemin
   principal des données ;
4. `ui/screens/map/MapScreen.kt` et `ui/screens/map/MapViewModel.kt` pour le cas d'usage central ;
5. `data/workers/` et `services/` pour les traitements hors écran ;
6. `data/build/` pour comprendre comment une base hors ligne est fabriquée ;
7. les tests du package correspondant avant de modifier une règle métier.

Lorsqu'un fichier est ajouté ou déplacé, cette documentation devrait être mise à jour dans la
même modification afin de conserver l'arborescence fonctionnelle et l'arborescence réelle en
accord.
