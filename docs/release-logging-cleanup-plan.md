# Nettoyage des logs et `printStackTrace()` avant release

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique quoi faire pour nettoyer les logs, exceptions et traces avant une build release.

## Objectif

Reduire les informations exposees par GeoTower en release :

- supprimer les `printStackTrace()` bruts ;
- eviter les logs de payloads, reponses API, erreurs serveur completes ou donnees utilisateur ;
- garder les details techniques uniquement en debug ;
- conserver des messages release courts, utiles et non sensibles ;
- rendre le comportement coherent dans toute l'app.

## Probleme actuel

Etat observe :

- de nombreux fichiers appellent encore `e.printStackTrace()`;
- plusieurs ecrans loggent des erreurs reseau ou API directement avec `Log.*`;
- certains logs concernent la localisation, les speedtests, les cartes, les widgets, Android Auto ou les telechargements ;
- `SignalQuestUploadWorker.kt` commence deja a appliquer une bonne approche avec `BuildConfig.DEBUG`, mais ce n'est pas encore generalise.

Exemples de zones a traiter en priorite :

- `SiteDetailScreen.kt`, notamment les logs `SpeedtestDebug`;
- `SupportDetailScreen.kt`;
- `DatabaseDownloader.kt`;
- `MapDownloadWorker.kt`;
- `LiveTrackingService.kt`;
- `GeoTowerCarAppService.kt`;
- `MapViewModel.kt`;
- `AnfrRepository.kt`;
- `ShareImageGenerator.kt`;
- `MapShareGenerator.kt`;
- `AntennaWidgetWorker.kt`;
- `AntennaWidget.kt`.

## Risques

Les logs release peuvent exposer :

- IDs de sites/supports ;
- localisation ou contexte utilisateur ;
- details d'erreurs API ;
- chemins de fichiers locaux ;
- structure interne de l'app ;
- exceptions completes qui aident a comprendre le fonctionnement interne ;
- payloads ou fragments de reponse serveur.

Ce n'est pas toujours critique, mais c'est une mauvaise hygiene avant diffusion publique.

## Strategie recommandee

Mettre en place un logger central leger, par exemple :

```kotlin
object AppLogger {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG && throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG && throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
```

Puis remplacer les logs directs par ce logger.

Alternative acceptable : utiliser directement `if (BuildConfig.DEBUG) { ... }` autour des logs de diagnostic, mais un logger central evite de repeter la meme logique partout.

## Regles de nettoyage

### 1. Remplacer `printStackTrace()`

Ne plus utiliser :

```kotlin
e.printStackTrace()
```

Remplacer par :

```kotlin
AppLogger.w("GeoTower", "Operation failed", e)
```

ou, si l'erreur est attendue et non utile :

```kotlin
// no-op: optional feature unavailable
```

Ne pas avaler silencieusement une erreur importante si elle doit changer l'etat UI.

### 2. Gate les logs de diagnostic

Les logs comme :

```kotlin
Log.d("SpeedtestDebug", "Donnée finale : $speedtestData")
```

doivent etre debug-only.

En release, ne pas logger :

- les donnees speedtest completes ;
- le code ANFR envoye si ce n'est pas necessaire ;
- les reponses API ;
- les erreurs serveur completes ;
- les details d'exception.

### 3. Ne pas logger les bodies d'erreur API

Interdit en release :

```kotlin
response.errorBody()?.string()
Log.e("API", "Erreur : $errorBody")
```

En debug seulement, et encore avec prudence.

En release, garder au maximum :

```text
API request failed
```

ou un code court non sensible.

### 4. Conserver les logs utiles mais non sensibles

Tous les logs ne doivent pas disparaitre.

Acceptable en release :

- erreur systeme courte ;
- echec d'une operation de fond sans payload ;
- etat global non sensible.

Exemple :

```kotlin
AppLogger.w("MapDownload", "Map download failed")
```

Pas acceptable :

```kotlin
AppLogger.w("MapDownload", "Failed url=$url path=$file error=${e.stackTraceToString()}")
```

### 5. Harmoniser les tags

Utiliser des tags stables et courts :

- `GeoTower`
- `GeoTowerDb`
- `GeoTowerMap`
- `GeoTowerUpload`
- `GeoTowerLocation`
- `GeoTowerCar`

Eviter les tags temporaires comme `SpeedtestDebug` en release.

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/data/workers/SignalQuestUploadWorker.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SupportDetailScreen.kt`
- `app/src/main/java/fr/geotower/data/api/DatabaseDownloader.kt`
- `app/src/main/java/fr/geotower/data/workers/MapDownloadWorker.kt`
- `app/src/main/java/fr/geotower/services/LiveTrackingService.kt`
- `app/src/main/java/fr/geotower/services/GeoTowerCarAppService.kt`
- `app/src/main/java/fr/geotower/ui/screens/map/MapViewModel.kt`
- tous les fichiers trouves par :

```powershell
rg "Log\\.|android\\.util\\.Log|printStackTrace|errorBody\\(\\)\\?\\.string" app/src/main/java
```

## Ordre de travail recommande

1. Creer un logger central dans `fr.geotower.utils`, par exemple `AppLogger.kt`.
2. Remplacer les logs les plus sensibles :
   - `SiteDetailScreen.kt`;
   - `SupportDetailScreen.kt`;
   - `DatabaseDownloader.kt`;
   - `MapDownloadWorker.kt`.
3. Remplacer les `printStackTrace()` dans les workers/services.
4. Nettoyer les logs UI et widgets.
5. Lancer `rg` pour verifier ce qui reste.
6. Compiler et lancer les tests.

## Criteres d'acceptation

La correction est terminee seulement si :

- aucun `printStackTrace()` ne reste dans `app/src/main/java`, sauf justification documentee ;
- aucun body d'erreur API complet n'est logge en release ;
- les logs de diagnostic sont gates par `BuildConfig.DEBUG` ou passent par un logger central ;
- les logs release ne contiennent pas de payload, token, chemin fichier utilisateur, coordonnees exactes ou reponse serveur complete ;
- `SignalQuestUploadWorker.kt` garde son comportement actuel de log court en release ;
- `:app:compileDebugKotlin` passe ;
- `:app:testDebugUnitTest` passe.

## Commandes de verification

Depuis la racine du projet :

```powershell
rg "printStackTrace|errorBody\\(\\)\\?\\.string" app/src/main/java
rg "Log\\.|android\\.util\\.Log" app/src/main/java
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Apres activation de R8/minify, verifier aussi une build release :

```powershell
.\gradlew.bat :app:assembleRelease
```

## A ne pas faire

- Ne pas supprimer une gestion d'erreur utile sans alternative UI/metier.
- Ne pas remplacer tous les logs par des no-op aveugles.
- Ne pas logger les headers, tokens, API keys ou bodies complets.
- Ne pas garder `printStackTrace()` en production.
- Ne pas afficher des messages techniques a l'utilisateur final.
- Ne pas melanger ce chantier avec la migration de la cle API.

## Prompt court pour l'IA qui implementera

Tu dois nettoyer les logs release de GeoTower. Lis les usages de `Log.*`, `android.util.Log`, `printStackTrace()` et `errorBody()?.string()` dans `app/src/main/java`. Cree si utile un logger central `AppLogger` qui logge les details et exceptions seulement en debug via `BuildConfig.DEBUG`, et garde en release des messages courts non sensibles. Remplace les `printStackTrace()`, supprime les logs de payloads/reponses API, gate les logs `SpeedtestDebug`, et conserve seulement les logs release utiles sans donnees sensibles. Termine par `rg "printStackTrace|errorBody\\(\\)\\?\\.string" app/src/main/java`, `rg "Log\\.|android\\.util\\.Log" app/src/main/java`, `:app:compileDebugKotlin` et `:app:testDebugUnitTest`.
