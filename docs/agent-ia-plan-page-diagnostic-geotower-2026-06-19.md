# Prompt IA - page Diagnostic GeoTower

## Objectif

Ajouter une page "Diagnostic GeoTower" qui permet de comprendre rapidement l'etat de l'application : bases de donnees, cartes hors-ligne, permissions, notifications, uploads SignalQuest, stockage, version de l'app et etat general.

Cette page doit aider deux publics :

- l'utilisateur qui ne comprend pas pourquoi rien ne s'affiche ou pourquoi une fonction ne marche pas ;
- le developpeur/support qui veut un rapport clair, copiable, sans fouiller dans les logs.

Ne pas en faire un ecran technique anxiogene. L'objectif est un tableau de bord lisible : "OK", "Attention", "Action requise", avec des boutons de correction simples.

## Ou placer la page

Recommendation principale : creer une vraie route/ecran `diagnostic`, accessible depuis `Parametres > Systeme`.

Pourquoi :

- le diagnostic touche plusieurs domaines, pas seulement la base de donnees ;
- `A propos` est plutot informatif, pas un endroit naturel pour corriger les problemes ;
- l'accueil doit rester centre sur l'usage quotidien ;
- `Parametres > Systeme` contient deja la carte "Gerer les permissions", donc le diagnostic y est coherent.

Emplacement exact conseille :

- fichier : `app/src/main/java/fr/geotower/ui/screens/settings/SettingsScreen.kt`
- composable : `SectionSysteme(...)`
- ajouter une carte sous "Gerer les permissions" :
  - titre : `Diagnostic GeoTower`
  - description : `Etat des donnees, permissions, notifications et stockage`
  - icone : `Icons.Outlined.Troubleshoot`, `Icons.Outlined.BugReport` ou `Icons.Outlined.HealthAndSafety`
  - action : `navController.navigate("diagnostic")`

Comme `SectionSysteme` ne recoit pas actuellement `NavController`, adapter sa signature ou passer un callback `onOpenDiagnostic: () -> Unit`.

Raccourci secondaire :

- ajouter aussi une entree discrete dans `A propos`, pres de la version de l'app ou des infos de donnees ;
- action identique : ouvrir `diagnostic`.

Ne pas mettre en premier niveau dans la navigation principale pour la premiere version. Trop visible pour une page d'assistance.

## Fichiers probablement concernes

- `app/src/main/java/fr/geotower/MainActivity.kt`
- `app/src/main/java/fr/geotower/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/about/AboutScreen.kt`
- `app/src/main/java/fr/geotower/ui/components/GeoTowerBreadcrumbBar.kt`
- nouveau fichier conseille : `app/src/main/java/fr/geotower/ui/screens/diagnostic/DiagnosticScreen.kt`
- nouveau fichier conseille : `app/src/main/java/fr/geotower/ui/screens/diagnostic/DiagnosticModels.kt`
- optionnel : `app/src/main/java/fr/geotower/ui/screens/diagnostic/DiagnosticViewModel.kt`
- optionnel : `app/src/main/java/fr/geotower/data/diagnostic/GeoTowerDiagnosticRepository.kt`
- strings :
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-en/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`

## Navigation a ajouter

Dans `MainActivity.kt`, ajouter une route :

```kotlin
composable(
    route = "diagnostic",
    deepLinks = listOf(navDeepLink { uriPattern = "geotower://diagnostic" })
) {
    Box(modifier = Modifier.padding(innerPadding)) {
        DiagnosticScreen(
            navController = navController,
            repository = repository,
            radioRepository = radioRepository
        )
    }
}
```

Adapter les parametres selon les besoins reels. Ne pas passer un objet si la page peut le recuperer proprement depuis le contexte ou un ViewModel.

Si le projet impose des feature flags pour les ecrans, ajouter une entree dediee dans `RemoteFeatureFlags.Screens`, par exemple `DIAGNOSTIC = "diagnostic"`, et suivre le meme comportement que les autres ecrans.

Dans `GeoTowerBreadcrumbBar.kt`, ajouter :

- un label `diagnostic` dans `GeoTowerBreadcrumbLabels` ;
- une string `appstrings_diagnostic_title` ;
- une entree route `"diagnostic" -> GeoTowerBreadcrumbItem(...)`.

## Structure UX de la page

La page doit etre composee de sections compactes :

1. Resume global
2. Donnees antennes
3. Donnees radio / FH si applicable
4. Cartes hors-ligne
5. Permissions
6. Notifications
7. SignalQuest et uploads
8. Stockage et cache
9. Version app et environnement
10. Rapport support

Chaque section doit avoir :

- un statut visuel : OK, Attention, Action requise, Inconnu ;
- un titre clair ;
- une phrase courte ;
- une action si elle existe.

Eviter les longs paragraphes. Mettre les details techniques dans un bloc "Details avances".

## Etats et severites

Creer un modele simple :

```kotlin
enum class DiagnosticSeverity {
    Ok,
    Info,
    Warning,
    Error,
    Unknown
}

data class DiagnosticItem(
    val id: String,
    val title: String,
    val summary: String,
    val severity: DiagnosticSeverity,
    val details: List<String> = emptyList(),
    val actionLabel: String? = null
)
```

Ne pas melanger la logique metier dans les composables si elle devient longue. Preferer un `DiagnosticRepository` ou un ViewModel qui construit un `DiagnosticState`.

## Resume global

Afficher en haut :

- un statut general :
  - `Tout semble pret`
  - `Quelques points a verifier`
  - `Action requise`
- une phrase :
  - `La base antennes est installee et les permissions essentielles sont accordees.`
  - `La base locale est absente : la carte peut etre vide ou utiliser la base en ligne.`
  - `Les notifications sont bloquees : les telechargements et alertes ne seront pas visibles.`
- boutons :
  - `Actualiser`
  - `Copier le rapport`
  - optionnel : `Partager`

Le resume global doit etre calcule par priorite :

- Error si base antennes absente/invalide et aucune alternative utilisable ;
- Warning si permissions/notifications bloquent une fonction active ;
- Warning si uploads SignalQuest bloques/en echec ;
- Info si tout est OK mais une base plus recente est connue ;
- Ok si rien d'important n'est detecte.

## Section Donnees antennes

Verifier la base principale :

- nom attendu : `GeoTowerDatabaseValidator.DB_NAME` (`geotower_fr.db`) ;
- fichier present ;
- taille ;
- validation via `GeoTowerDatabaseValidator.getInstalledDatabaseStatus(context)` ;
- version via `GeoTowerDatabaseValidator.getInstalledDatabaseVersion(context)` si disponible ;
- schema attendu : `GeoTowerDatabaseValidator.EXPECTED_SCHEMA_VERSION` ;
- fichiers sidecar : `-wal`, `-shm`, `.download`, `.backup` ;
- raison d'invalidite si disponible.

Afficher :

- `Base antennes installee`
- `Base absente`
- `Base invalide`
- `Schema incompatible`
- `Telechargement incomplet detecte`

Actions :

- si absente/invalide : ouvrir `settings?section=database` ;
- si une mise a jour est connue : ouvrir `settings?section=database` ;
- ne pas supprimer les donnees depuis le diagnostic en v1. Garder la suppression dans la section base de donnees existante.

## Section Donnees radio / FH

Verifier la base radio si elle est utilisee par l'app :

- nom attendu : `RadioDatabaseValidator.DB_NAME` (`geotower_fr_radio.db`) ;
- validation via `RadioDatabaseValidator.validateDatabaseFile(...)` ;
- version/date ANFR si lisible dans la table `metadata` ;
- `row_count` si disponible ;
- taille du fichier.

Afficher :

- `Base radio installee`
- `Base radio absente`
- `Base radio invalide`

Action :

- ouvrir `settings?section=database`.

Ne pas bloquer le diagnostic global si cette base est optionnelle ou si l'app fonctionne sans elle.

## Section Base en ligne / fallback live

GeoTower est offline-first, mais peut utiliser une base en ligne si la base locale est absente ou invalide.

A afficher si l'information est disponible :

- base locale valide : oui/non ;
- fallback live actif : oui/non/inconnu ;
- dernier etat connu du repository ;
- avertissement si l'ecran risque de consommer du reseau.

Ne pas declencher automatiquement de gros appels reseau a l'ouverture de la page. Le bouton `Actualiser` peut lancer les checks reseau legers si l'app a deja des APIs pour ca.

## Section Cartes hors-ligne

Verifier :

- dossier `context.getExternalFilesDir(null)/maps` ;
- nombre de fichiers `.map` ;
- taille totale ;
- fichiers a 0 octet ;
- fichiers non reconnus ;
- date de modification la plus recente ;
- telechargements `.download` ou temporaires si presents.

Afficher :

- `3 cartes hors-ligne installees, 1,2 Go`
- `Aucune carte hors-ligne`
- `Une carte semble incomplete`

Actions :

- ouvrir `settings?section=offline_maps` ;
- si une carte precise pose probleme, ouvrir `settings?section=offline_maps&target_map=...` si le flux existant le permet.

## Section Permissions

Verifier avec les APIs Android compatibles :

- localisation precise ;
- localisation approximative ;
- localisation en arriere-plan si une fonction active en depend ;
- notifications Android 13+ (`POST_NOTIFICATIONS`) ;
- acces photos/media si l'upload SignalQuest l'utilise sur la version Android courante ;
- etat d'optimisation batterie si pertinent pour le suivi live.

Afficher selon l'usage :

- si la notification live est active mais la localisation manque : Error/Warning ;
- si les notifications sont bloquees mais les alertes de mise a jour sont activees : Warning ;
- si l'upload photo est utilise mais permission media indisponible : Warning ;
- si une permission n'est pas necessaire dans l'etat actuel : Info ou ne pas afficher.

Actions :

- ouvrir les parametres de l'app via `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` ;
- eventuellement ouvrir les parametres de localisation Android ;
- ne pas demander toutes les permissions automatiquement a l'ouverture du diagnostic.

## Section Notifications

Verifier :

- notifications globales activees via `NotificationManagerCompat.areNotificationsEnabled()`;
- canaux principaux si possible :
  - telechargement base ;
  - telechargement cartes ;
  - mises a jour base ;
  - mises a jour app ;
  - notification live ;
  - SignalQuest upload ;
- preferences internes :
  - `enable_update_notifications` ;
  - `enable_live_notifications` ;
  - intervalle live ;
  - operateur par defaut.

Afficher :

- `Notifications autorisees`
- `Notifications bloquees par Android`
- `Notifications live activees`
- `Notifications de mise a jour desactivees`

Actions :

- ouvrir les parametres notification de l'app ;
- ouvrir la section systeme ou preferences selon le cas.

## Section SignalQuest et uploads

Verifier :

- donnees communautaires activees/desactivees ;
- sources photos/speedtests activees ;
- nombre d'uploads en attente si la queue expose cette info ;
- travaux WorkManager en cours/echec avec le tag global `SignalQuestUploadScheduler.GLOBAL_TAG` ;
- fichiers temporaires d'upload restants ;
- derniere erreur si elle est stockee.

Afficher :

- `Aucun envoi en attente`
- `2 photos en attente d'envoi`
- `Dernier envoi en echec, nouvel essai prevu`
- `SignalQuest desactive pour cet operateur`

Actions :

- ouvrir l'historique d'upload si disponible (`photo_upload_history`) ;
- ouvrir les parametres de donnees communautaires ;
- ne pas vider la queue depuis le diagnostic en v1.

## Section Stockage et cache

Afficher :

- taille base antennes ;
- taille base radio ;
- taille cartes hors-ligne ;
- taille cache images/partage si facile a calculer ;
- taille queue SignalQuest ;
- espace disponible sur le stockage app si disponible.

Attention :

- calculer les tailles en tache de fond ;
- eviter de parcourir recursivement des dossiers enormes sur le thread UI ;
- afficher `Calcul en cours...` si necessaire.

Actions v1 :

- ouvrir les sections existantes.

Actions v2 possibles :

- nettoyer les images de partage temporaires ;
- nettoyer des anciens fichiers `.download` ;
- nettoyer des uploads obsoletes apres confirmation.

Ne pas faire d'action destructive sans confirmation explicite.

## Section Version app et environnement

Afficher :

- `BuildConfig.VERSION_NAME` ;
- `BuildConfig.VERSION_CODE` ;
- variante/debug/release si disponible ;
- version Android ;
- modele appareil ;
- langue app ;
- theme/systeme si utile ;
- date/heure de generation du rapport.

Ne pas afficher :

- API keys ;
- tokens ;
- identifiants sensibles ;
- position GPS precise ;
- fichiers locaux complets avec chemins prives, sauf dans details avances et rapport local non partage.

## Rapport support copiable

Ajouter un bouton `Copier le rapport`.

Le rapport doit etre texte, lisible, et anonymise par defaut.

Exemple :

```text
GeoTower diagnostic
Date: 2026-06-19 18:42
App: 1.9.9.x (code ...)
Android: 15

Global: Action requise

Base antennes: absente
Base radio: installee, version 2026-06-xx
Cartes hors-ligne: 2 fichiers, 840 Mo
Notifications: bloquees par Android
Localisation: accordee approximative uniquement
SignalQuest: aucun upload en attente
Stockage app: 1,4 Go utilises
```

Prevoir ensuite une version JSON interne si utile pour debug, mais le texte simple est prioritaire.

Ne pas inclure la position courante ni le dernier site consulte dans le rapport par defaut.

## UI conseillee

Ecran Compose :

- top bar avec retour ;
- titre `Diagnostic GeoTower` ;
- bouton refresh en haut a droite ;
- carte resume global ;
- liste de cartes compactes par domaine ;
- detail deployable par carte ;
- bouton `Copier le rapport` en bas et/ou dans le resume.

Utiliser les composants existants si possible :

- style One UI si actif ;
- sizing via `LocalGeoTowerUiStyle.current.sizing` ;
- formes/couleurs coherentes avec `SettingsScreen` ;
- loader court via `GeoTowerLoadingMessage` si disponible.

Ne pas mettre de hero, illustration ou page marketing.

## Strings a prevoir

Noms indicatifs, a adapter aux conventions existantes :

- `appstrings_diagnostic_title`
- `appstrings_diagnostic_desc`
- `appstrings_diagnostic_open`
- `appstrings_diagnostic_refresh`
- `appstrings_diagnostic_copy_report`
- `appstrings_diagnostic_report_copied`
- `appstrings_diagnostic_global_ok`
- `appstrings_diagnostic_global_warning`
- `appstrings_diagnostic_global_error`
- `appstrings_diagnostic_section_antennas_db`
- `appstrings_diagnostic_section_radio_db`
- `appstrings_diagnostic_section_live_database`
- `appstrings_diagnostic_section_offline_maps`
- `appstrings_diagnostic_section_permissions`
- `appstrings_diagnostic_section_notifications`
- `appstrings_diagnostic_section_signalquest`
- `appstrings_diagnostic_section_storage`
- `appstrings_diagnostic_section_environment`
- `appstrings_diagnostic_status_ok`
- `appstrings_diagnostic_status_info`
- `appstrings_diagnostic_status_warning`
- `appstrings_diagnostic_status_error`
- `appstrings_diagnostic_status_unknown`
- `appstrings_diagnostic_action_open_database_settings`
- `appstrings_diagnostic_action_open_offline_maps`
- `appstrings_diagnostic_action_open_app_settings`
- `appstrings_diagnostic_action_open_notification_settings`
- `appstrings_diagnostic_action_open_upload_history`
- `appstrings_diagnostic_action_open_community_settings`

Mettre a jour toutes les langues du projet si des strings sont ajoutees.

## Accessibilite

Cette page doit etre exemplaire :

- chaque carte doit annoncer son statut ;
- les boutons icon-only doivent avoir un `contentDescription` ;
- le rapport copie doit declencher un feedback clair ;
- les cartes deployables doivent annoncer ouvert/ferme ;
- le statut ne doit pas dependre uniquement de la couleur.

## MVP recommande

Pour une premiere implementation solide, faire seulement :

1. Route `diagnostic`.
2. Carte dans `Parametres > Systeme`.
3. Raccourci dans `A propos`.
4. Resume global.
5. Base antennes.
6. Base radio.
7. Cartes hors-ligne.
8. Permissions.
9. Notifications.
10. Rapport texte copiable.

Laisser SignalQuest/WorkManager/cache avance pour une seconde passe si le chantier devient trop large.

## Criteres d'acceptation

- La page est accessible depuis `Parametres > Systeme`.
- Un utilisateur peut comprendre en moins de 10 secondes pourquoi la carte est vide ou pourquoi une notification ne marche pas.
- La page ne lance pas de gros telechargement toute seule.
- Les informations sensibles ne sont pas partagees par defaut.
- Le rapport support est copiable.
- Les actions renvoient vers les ecrans existants plutot que de dupliquer les reglages.
- Les strings sont localisees.
- TalkBack lit correctement le titre, les statuts et les actions.

## Validation

Executer au minimum :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugKotlin
```

Si des strings sont ajoutees, executer aussi le test i18n si disponible :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests fr.geotower.AndroidI18nResourcesTest
```

Faire une verification manuelle :

1. Ouvrir `Parametres > Systeme`.
2. Ouvrir `Diagnostic GeoTower`.
3. Verifier les statuts avec une base installee.
4. Verifier les statuts sans base ou avec permissions refusees si possible.
5. Copier le rapport.
6. Verifier que le rapport ne contient pas de position GPS precise, token ou cle API.
7. Tester le retour et le fil d'Ariane si affiche.

## Evolution possible apres le MVP

- ajouter un bouton "Verifier maintenant" pour les versions distantes ;
- afficher les WorkManager en cours/echec ;
- proposer un nettoyage de cache avec confirmation ;
- ajouter un QR code ou fichier diagnostic partageable ;
- ouvrir automatiquement cette page depuis certaines erreurs critiques ;
- ajouter un mode "diagnostic avance" pour developpeur.
