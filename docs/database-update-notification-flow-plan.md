# Notification de mise a jour DB sans telechargement automatique

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il decrit comment remplacer le lancement direct du telechargement de base depuis la notification par une ouverture de la section "Base de donnees".

## Objectif

Quand une nouvelle base de donnees est disponible :

1. GeoTower affiche une notification.
2. Le texte de la notification indique clairement que le toucher ouvre la section de telechargement.
3. Le clic sur la notification ouvre l'app dans `Parametres > Base de donnees`.
4. Le telechargement ne demarre pas automatiquement.
5. L'utilisateur lance lui-meme le telechargement depuis l'interface.

Cette modification evite qu'une autre app puisse declencher un telechargement couteux en envoyant simplement une action d'intent a `MainActivity`.

## Probleme actuel

Etat observe :

- `UpdateCheckWorker.kt` cree une notification quand une nouvelle base est disponible.
- Le `PendingIntent` de la notification ouvre `MainActivity` avec :

```kotlin
action = "ACTION_DOWNLOAD_DB"
```

- `MainActivity.kt` contient `checkDownloadIntent(intent)` :

```kotlin
if (intent?.action == "ACTION_DOWNLOAD_DB") {
    // enqueue DatabaseDownloadWorker
}
```

- `MainActivity` est exportee dans le manifest, donc elle peut recevoir des intents externes.
- Le texte actuel de notification dit :

```text
Une mise a jour des antennes est disponible ! Touchez pour l'installer.
```

Ce texte ne correspondra plus au comportement voulu si la notification ouvre seulement la section de telechargement.

## Risque

Le probleme n'est pas un vol direct de donnees. Le risque est plutot :

- lancement reseau sans intention utilisateur claire ;
- consommation batterie/data ;
- spam possible du serveur de base de donnees ;
- perturbation de l'etat local de la base ;
- flux utilisateur confus si un telechargement demarre juste parce qu'une activity exportee a recu une action.

## Comportement cible

Remplacer :

```text
Notification -> ACTION_DOWNLOAD_DB -> telechargement direct
```

par :

```text
Notification -> geotower://settings?section=database -> l'utilisateur clique sur Telecharger
```

La notification doit dire explicitement quelque chose comme :

Francais :

```text
Une mise a jour des antennes est disponible. Touchez pour ouvrir la section de telechargement.
```

Anglais :

```text
An antenna update is available. Tap to open the download section.
```

Portugais :

```text
Ha uma atualizacao das antenas disponivel. Toque para abrir a seccao de transferencia.
```

Adapter les accents/orthographe au style actuel du projet.

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/data/workers/UpdateCheckWorker.kt`
- `app/src/main/java/fr/geotower/MainActivity.kt`
- `app/src/main/java/fr/geotower/utils/AppStrings.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/fr/geotower/ui/screens/settings/SettingsScreen.kt`

## Changements a effectuer

### 1. Modifier le PendingIntent de la notification

Dans `UpdateCheckWorker.kt`, ne plus utiliser :

```kotlin
action = "ACTION_DOWNLOAD_DB"
```

Utiliser une URI de navigation existante :

```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geotower://settings?section=database")).apply {
    setPackage(context.packageName)
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
```

Points importants :

- importer `android.net.Uri` ;
- garder `FLAG_IMMUTABLE` sur le `PendingIntent` ;
- utiliser un requestCode stable ou specifique ;
- verifier que le deep link `geotower://settings?section=database` est bien pris en charge.

### 2. Changer le texte de notification

Dans `AppStrings.kt`, modifier `newDbNotifDesc`.

Le texte ne doit plus promettre une installation directe.

Remplacer l'idee :

```text
Touchez pour l'installer.
```

par :

```text
Touchez pour ouvrir la section de telechargement.
```

Faire la meme chose pour les langues gerees par `getForService`.

### 3. Supprimer ou neutraliser `ACTION_DOWNLOAD_DB`

Dans `MainActivity.kt`, retirer le lancement automatique du worker depuis une action externe.

Options :

- supprimer `checkDownloadIntent(intent)` si plus rien ne l'utilise ;
- ou conserver la fonction mais ne plus lancer `DatabaseDownloadWorker` directement ;
- ou transformer l'action legacy en navigation vers `settings?section=database`, sans telechargement.

Recommandation :

Si une compatibilite avec d'anciennes notifications est souhaitee, traiter `ACTION_DOWNLOAD_DB` comme une navigation seulement :

```text
ACTION_DOWNLOAD_DB -> ouvrir la section database -> pas de download automatique
```

Sinon, supprimer l'action.

### 4. Verifier le flux utilisateur

Tester :

- notification recue ;
- clic sur notification ;
- ouverture de l'app sur la section Base de donnees ;
- aucun `DatabaseDownloadWorker` lance automatiquement ;
- bouton de telechargement manuel toujours fonctionnel.

## Criteres d'acceptation

La correction est terminee seulement si :

- `UpdateCheckWorker.kt` ne cree plus de notification avec `ACTION_DOWNLOAD_DB` ;
- cliquer la notification ouvre la section `database` des parametres ;
- le texte de notification annonce l'ouverture de la section de telechargement ;
- `MainActivity.kt` ne lance plus `DatabaseDownloadWorker` uniquement parce qu'une action externe vaut `ACTION_DOWNLOAD_DB` ;
- le telechargement manuel depuis l'UI fonctionne toujours ;
- `rg "ACTION_DOWNLOAD_DB" app/src/main/java` ne retourne rien, ou seulement un chemin legacy documente qui ne lance pas de download ;
- `:app:compileDebugKotlin` passe ;
- `:app:testDebugUnitTest` passe.

## Commandes de verification

Depuis la racine du projet :

```powershell
rg "ACTION_DOWNLOAD_DB|newDbNotifDesc|settings\\?section=database" app/src/main/java
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

## A ne pas faire

- Ne pas garder une action exportee qui lance directement un telechargement.
- Ne pas dire "Touchez pour l'installer" si le clic ouvre seulement une section.
- Ne pas supprimer le bouton manuel de telechargement.
- Ne pas casser le deep link `geotower://settings?section=database`.
- Ne pas utiliser un token ou nonce complexe si une simple confirmation utilisateur suffit.

## Prompt court pour l'IA qui implementera

Tu dois changer le flux de notification de mise a jour de base GeoTower. Lis `UpdateCheckWorker.kt`, `MainActivity.kt` et `AppStrings.kt`. La notification ne doit plus envoyer `ACTION_DOWNLOAD_DB` ni declencher directement `DatabaseDownloadWorker`. A la place, elle doit ouvrir `geotower://settings?section=database`. Modifie aussi `newDbNotifDesc` pour dire que toucher la notification ouvre la section de telechargement, pas qu'elle installe directement la mise a jour. Supprime ou neutralise le traitement `ACTION_DOWNLOAD_DB` dans `MainActivity` afin qu'une app externe ne puisse pas lancer un telechargement. Termine par `rg "ACTION_DOWNLOAD_DB|newDbNotifDesc|settings\\?section=database" app/src/main/java`, `:app:compileDebugKotlin` et `:app:testDebugUnitTest`.
