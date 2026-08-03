# Prompt IA - timeline d'un site GeoTower

## Objectif

Ajouter une timeline claire dans la fiche d'un site/support GeoTower pour montrer ce qui est connu dans le temps : implantation, mise en service, derniere modification, changements detectes entre bases ANFR, ajout/retrait d'operateur, apparition d'une techno ou d'une bande, evolution du statut.

La timeline doit rendre une fiche antenne plus vivante et plus utile pour les passionnes telecom, sans inventer d'historique quand la donnee n'existe pas.

## Point important

GeoTower affiche aujourd'hui surtout un etat courant. La base locale est remplacee lors des mises a jour. Une vraie timeline de changements demande donc une source d'historique :

- soit des dates deja presentes dans la base courante (`date_implantation`, `date_service`, `date_modif`) ;
- soit une comparaison entre l'ancienne base et la nouvelle base au moment de la mise a jour ;
- soit un historique/diff prepare cote serveur.

Ne pas faire semblant d'avoir une timeline complete si l'app ne possede qu'un snapshot. Le MVP peut afficher les dates connues. La version forte doit stocker des evenements de changement.

## Ou placer la timeline

Emplacement principal :

- dans la fiche site : `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- ajouter un bloc `Historique` apres le bloc `Dates` ou pres des blocs techniques.

Pourquoi :

- le bloc `SiteDatesBlock.kt` contient deja les dates importantes ;
- la fiche site est l'endroit naturel pour lire l'historique d'un identifiant ANFR ;
- l'utilisateur ne doit pas aller dans les parametres pour comprendre l'evolution d'une antenne.

Option secondaire :

- si le site et le support sont separes, afficher une timeline "site" dans `SiteDetailScreen.kt` et une timeline "support" dans `SupportDetailScreen.kt`.
- pour la premiere version, commencer par `SiteDetailScreen.kt`.

## Fichiers probablement concernes

- `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SupportDetailScreen.kt` si version support
- `app/src/main/java/fr/geotower/ui/components/SiteDatesBlock.kt`
- nouveau composant conseille : `app/src/main/java/fr/geotower/ui/components/SiteTimelineBlock.kt`
- `app/src/main/java/fr/geotower/data/AnfrRepository.kt`
- `app/src/main/java/fr/geotower/data/db/GeoTowerDao.kt`
- `app/src/main/java/fr/geotower/data/models/OfflineEntities.kt`
- optionnel : `app/src/main/java/fr/geotower/data/history/SiteHistoryStore.kt`
- optionnel : `app/src/main/java/fr/geotower/data/history/SiteHistoryModels.kt`
- optionnel : `app/src/main/java/fr/geotower/data/history/SiteHistoryDiff.kt`
- `app/src/main/java/fr/geotower/ui/screens/settings/PagesCustomizationSheet.kt` si le bloc devient personnalisable
- strings dans tous les `res/values*`

## MVP recommande

Faire d'abord une timeline "dates connues", sans diff inter-version.

Elle doit utiliser les donnees deja disponibles :

- `TechniqueEntity.dateImplantation`
- `TechniqueEntity.dateService`
- `TechniqueEntity.dateModif`
- etat actuel du site si disponible ;
- eventuellement dates de panne si `SiteStatusCard` a deja `outageStartDate` / `outageExpectedRestorationDate`.

Evenements MVP :

- `Implantation declaree`
- `Mise en service`
- `Derniere modification ANFR`
- `Incident signale` si donnees disponibles
- `Retablissement prevu` si donnees disponibles

Si une date est absente, ne pas afficher un evenement vide.

Si aucune date exploitable n'existe, afficher une petite carte :

`Aucun historique date disponible pour ce site.`

## Version forte : vrais changements entre bases

Ajouter un mecanisme de diff lors de l'installation d'une nouvelle base.

Principe :

1. Avant de remplacer la base locale, garder acces a l'ancienne base.
2. Ouvrir l'ancienne et la nouvelle base en lecture seule.
3. Comparer les sites par `id_anfr` et/ou `id_support`.
4. Detecter les changements importants.
5. Ecrire des evenements persistants dans un stockage local separe de la base telechargee.

Important :

- ne pas stocker les evenements dans la base ANFR remplacee si cela risque d'etre perdu a la mise a jour suivante ;
- preferer une petite base locale ou une table Room separee non remplacee par le fichier ANFR ;
- garder une limite de retention, par exemple 12 ou 24 mois.

## Evenements a detecter

Priorite haute :

- site apparu ;
- site disparu ;
- operateur ajoute ;
- operateur retire ;
- techno ajoutee : 2G, 3G, 4G, 5G ;
- bande ajoutee : 700, 800, 1800, 2100, 2600, 3500, etc. ;
- passage en service / hors service si la donnee existe ;
- modification de `date_modif`.

Priorite moyenne :

- changement d'adresse ou commune ;
- changement de hauteur support ;
- changement de type de support ;
- changement d'azimut ;
- faisceau hertzien ajoute/retire ;
- changement de frequences FH.

Priorite basse :

- changement mineur de libelle ;
- correction de format ;
- doublons nettoyes.

## Modele de donnees conseille

```kotlin
enum class SiteTimelineEventType {
    SiteCreated,
    SiteRemoved,
    OperatorAdded,
    OperatorRemoved,
    TechnologyAdded,
    TechnologyRemoved,
    BandAdded,
    BandRemoved,
    StatusChanged,
    DatesChanged,
    SupportChanged,
    AddressChanged,
    AzimuthChanged,
    MicrowaveLinkAdded,
    MicrowaveLinkRemoved,
    OutageStarted,
    OutageExpectedEnd,
    Unknown
}

data class SiteTimelineEvent(
    val id: String,
    val idAnfr: String,
    val supportId: String?,
    val eventDate: String?,
    val sourceDatabaseVersion: String?,
    val type: SiteTimelineEventType,
    val title: String,
    val description: String,
    val operatorName: String? = null,
    val technology: String? = null,
    val bandLabel: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null
)
```

Pour le MVP sans diff, ce modele peut etre construit en memoire depuis `TechniqueEntity`.

Pour la version diff, il faut persister les evenements.

## Design UI

Bloc `Historique` :

- titre avec icone `History` ou `Timeline` ;
- carte compacte ;
- evenements tries du plus recent au plus ancien ;
- chaque evenement avec :
  - date ;
  - titre court ;
  - detail en une ligne ;
  - badge operateur/techno si pertinent.

Exemple :

```text
Historique

12 juin 2026
Derniere modification ANFR
Le site a ete modifie dans la base de donnees.

3 fevrier 2024
Mise en service
L'antenne est declaree en service.

18 janvier 2024
Implantation declaree
Le site apparait dans les donnees ANFR.
```

Ne pas faire une grande page separee pour le MVP. Une carte dans la fiche suffit.

Si la liste devient longue :

- afficher les 3 a 5 evenements les plus importants ;
- bouton `Voir tout l'historique` ;
- bottom sheet plein ecran ou route `site_history/{id}` en v2.

## Integration avec personnalisation des pages

Si GeoTower permet deja de choisir les blocs de la fiche site :

- ajouter un toggle `Historique du site` dans `PagesCustomizationSheet.kt` ;
- ajouter une preference, par exemple `SitePagePrefs.timeline` ;
- inclure le bloc dans l'ordre personnalisable seulement si ce systeme existe deja pour les blocs site.

Ne pas forcer la timeline si l'utilisateur masque les blocs avances.

## Donnees et diff : approche technique

### MVP

- Ajouter `SiteTimelineBlock`.
- Dans `SiteDetailScreen.kt`, construire une liste depuis `technique`.
- Reutiliser `formatDateToFrench` ou un helper localise existant.
- Ajouter strings localisees.

### Version diff locale

- Pendant `DatabaseDownloader.installValidatedDatabase(...)` ou le worker de telechargement, comparer ancien/nouveau fichier.
- Ne pas ralentir l'installation principale sur de grosses comparaisons : faire un worker dedie si necessaire.
- Comparer seulement les sites proches/favoris serait insuffisant pour une vraie timeline globale ; mais c'est acceptable pour une premiere version limitee si documente.
- Stocker les evenements dans une base locale non remplacee par la DB ANFR.

### Version diff serveur

- Generer les diffs dans le pipeline serveur qui produit la base ANFR.
- Exposer un endpoint par site :
  - `/api/v2/sites/{idAnfr}/history`
  - ou integrer l'historique dans le manifeste de base.
- Garder l'app offline-first : si l'historique est telecharge, il doit etre cache localement.

## Strings a prevoir

Noms indicatifs :

- `appstrings_site_timeline_title`
- `appstrings_site_timeline_empty`
- `appstrings_site_timeline_view_all`
- `appstrings_site_timeline_implantation`
- `appstrings_site_timeline_service`
- `appstrings_site_timeline_last_modification`
- `appstrings_site_timeline_site_created`
- `appstrings_site_timeline_site_removed`
- `appstrings_site_timeline_operator_added`
- `appstrings_site_timeline_operator_removed`
- `appstrings_site_timeline_technology_added`
- `appstrings_site_timeline_technology_removed`
- `appstrings_site_timeline_band_added`
- `appstrings_site_timeline_band_removed`
- `appstrings_site_timeline_status_changed`
- `appstrings_site_timeline_outage_started`
- `appstrings_site_timeline_outage_expected_end`
- `appstrings_site_timeline_source_version`
- `appstrings_page_site_timeline_settings`

Mettre a jour `values`, `values-fr`, `values-en`, `values-de`, `values-es`, `values-it`, `values-pt`.

## Accessibilite

- TalkBack doit lire la date, le type d'evenement et le detail.
- Ne pas utiliser uniquement la couleur pour differencier les evenements.
- Si la timeline est une liste, l'ordre doit etre logique et stable.
- Les badges operateur/techno doivent avoir une description ou etre purement decoratifs si le texte adjacent donne deja l'information.

## Criteres d'acceptation MVP

- Un bloc `Historique` apparait sur une fiche site quand au moins une date utile existe.
- Les evenements sont tries chronologiquement.
- Les dates vides ne generent pas de lignes inutiles.
- Le bloc respecte le style existant des cartes site.
- Les strings sont localisees.
- La compilation Kotlin passe.

## Criteres d'acceptation version diff

- Une mise a jour de base peut creer des evenements persistants.
- Les evenements restent visibles apres remplacement de la base.
- Les doublons sont evites si la meme version de base est traitee plusieurs fois.
- Le diff est limite aux changements utiles, pas aux variations de format.
- La retention evite une croissance infinie.

## Validation

Executer au minimum :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugKotlin
```

Si des strings sont ajoutees :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests fr.geotower.AndroidI18nResourcesTest
```

Pour la version diff, ajouter des tests unitaires sur :

- tri des evenements ;
- deduplication ;
- detection ajout/retrait operateur ;
- detection ajout/retrait techno ;
- detection changement date/status ;
- traitement des valeurs vides.

## Recommendation de livraison

Livrer en deux passes :

1. MVP visuel avec les dates deja presentes dans la fiche site.
2. Vraie timeline historique via diff de bases.

Le MVP donne vite de la valeur et pose le bloc UI. La seconde passe donne la vraie force produit.
