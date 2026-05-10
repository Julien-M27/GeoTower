# Durcissement de la suppression "toutes les cartes"

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique comment corriger le point 7, c'est-a-dire la suppression trop large des cartes hors ligne.

## Objectif

Limiter l'action "Supprimer toutes les cartes" aux seuls fichiers de carte hors ligne attendus et valides.

Le bouton ne doit pas supprimer aveuglement tout ce qui se trouve dans le dossier `maps`.

## Probleme actuel

Etat observe dans `MapDownloadCard.kt` :

```kotlin
mapsDir.listFiles()?.forEach { it.delete() }
```

Cette ligne supprime tous les fichiers presents dans :

```kotlin
File(context.getExternalFilesDir(null), "maps")
```

La suppression individuelle est deja plus prudente :

```kotlin
OfflineMapDownloadValidator.safeMapFile(mapsDir, mapToDelete!!.mapFilename)?.delete()
```

Donc le probleme concerne surtout la suppression globale.

## Risque

Le dossier `maps` est cense contenir des fichiers `.map`, mais une suppression globale reste fragile :

- elle peut supprimer des fichiers temporaires utiles ;
- elle peut supprimer des fichiers metadata/cache futurs ;
- elle peut supprimer des fichiers inattendus crees par une ancienne version ;
- elle rend plus dangereux tout bug futur qui ecrirait autre chose dans `maps`;
- elle contourne la logique de validation deja presente dans `OfflineMapDownloadValidator`.

Ce n'est pas une faille critique comme une cle API exposee, mais c'est une mauvaise hygiene filesystem.

## Comportement cible

Le bouton "Supprimer toutes les cartes" doit supprimer uniquement :

- les fichiers reguliers ;
- directement dans `mapsDir` ;
- dont le nom est valide selon `OfflineMapDownloadValidator.isSafeMapFilename`;
- avec extension `.map`.

Il ne doit pas :

- supprimer des sous-dossiers ;
- suivre des chemins suspects ;
- supprimer des fichiers non `.map` ;
- supprimer des fichiers temporaires `.download`, `.tmp`, `.backup`, etc. sauf decision explicite ;
- supprimer quoi que ce soit hors de `mapsDir`.

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/ui/components/MapDownloadCard.kt`
- `app/src/main/java/fr/geotower/data/workers/OfflineMapDownloadValidator.kt`
- `app/src/main/java/fr/geotower/data/workers/MapDownloadWorker.kt`
- `app/src/main/java/fr/geotower/data/models/OfflineMapDto.kt`

## Changements a effectuer

### 1. Ajouter une fonction de liste/suppression sure

Ajouter une fonction dans `OfflineMapDownloadValidator`, par exemple :

```kotlin
fun listSafeMapFiles(mapsDir: File): List<File>
```

Elle doit :

- lire uniquement `mapsDir.listFiles()`;
- garder seulement les fichiers (`isFile`) ;
- garder seulement les noms valides avec `isSafeMapFilename(file.name)`;
- revalider avec `safeMapFile(mapsDir, file.name)`;
- ne retourner que les fichiers dont le parent canonique est `mapsDir`.

Option :

```kotlin
fun deleteAllSafeMapFiles(mapsDir: File): Int
```

qui retourne le nombre de fichiers supprimes.

### 2. Remplacer la suppression globale

Dans `MapDownloadCard.kt`, remplacer :

```kotlin
mapsDir.listFiles()?.forEach { it.delete() }
```

par une suppression filtree, par exemple :

```kotlin
OfflineMapDownloadValidator.listSafeMapFiles(mapsDir).forEach { it.delete() }
```

ou :

```kotlin
OfflineMapDownloadValidator.deleteAllSafeMapFiles(mapsDir)
```

### 3. Garder les fichiers temporaires sous controle

Si `MapDownloadWorker` cree des fichiers temporaires dans ce dossier, verifier leurs noms/extensions.

Deux options possibles :

- ne pas les supprimer via le bouton "cartes", car ce ne sont pas des cartes installees ;
- ou creer une fonction dediee de nettoyage des fichiers temporaires anciens, distincte de la suppression des cartes installees.

Ne pas melanger les deux comportements.

### 4. Mettre a jour l'UI si utile

Si la fonction retourne un nombre de fichiers supprimes, l'UI peut :

- simplement rafraichir la liste comme aujourd'hui ;
- ou afficher un message court.

Ne pas compliquer l'UX si le comportement actuel suffit.

## Tests a ajouter

Ajouter ou completer les tests unitaires de `OfflineMapDownloadValidator`.

Cas a verifier :

- `france.map` est listable/supprimable ;
- `../evil.map` est refuse ;
- `evil.txt` est refuse ;
- `file.map.download` est refuse ;
- un sous-dossier `nested.map` est refuse ;
- un fichier valide dans un autre dossier n'est jamais retourne ;
- `deleteAllSafeMapFiles` ne supprime que les `.map` valides.

Si les tests filesystem utilisent des dossiers temporaires, s'assurer qu'ils nettoient apres execution.

## Criteres d'acceptation

La correction est terminee seulement si :

- `MapDownloadCard.kt` n'utilise plus `mapsDir.listFiles()?.forEach { it.delete() }`;
- la suppression globale passe par `OfflineMapDownloadValidator` ou une fonction equivalente ;
- seuls les fichiers `.map` valides sont supprimes ;
- les sous-dossiers et fichiers non `.map` sont conserves ;
- `:app:compileDebugKotlin` passe ;
- `:app:testDebugUnitTest` passe ;
- les tests couvrent au moins un fichier `.map` valide et un fichier non `.map` conserve.

## Commandes de verification

Depuis la racine du projet :

```powershell
rg "listFiles\\(\\)\\?\\.forEach \\{ it\\.delete\\(\\) \\}|deleteAllSafeMapFiles|listSafeMapFiles" app/src/main/java app/src/test/java
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

## A ne pas faire

- Ne pas supprimer tout `mapsDir` avec `deleteRecursively()`.
- Ne pas supprimer tous les fichiers du dossier sans filtrage.
- Ne pas suivre des chemins fournis par le catalogue sans validation.
- Ne pas supprimer des dossiers.
- Ne pas supprimer des fichiers temporaires sans politique explicite.
- Ne pas dupliquer la logique de validation dans l'UI si `OfflineMapDownloadValidator` peut la porter.

## Prompt court pour l'IA qui implementera

Tu dois durcir la suppression "toutes les cartes" de GeoTower. Lis `MapDownloadCard.kt`, `OfflineMapDownloadValidator.kt` et `MapDownloadWorker.kt`. Remplace la suppression globale `mapsDir.listFiles()?.forEach { it.delete() }` par une fonction centralisee qui ne supprime que les fichiers `.map` valides, directement sous `mapsDir`, verifies par `OfflineMapDownloadValidator.safeMapFile` ou une nouvelle fonction `listSafeMapFiles`/`deleteAllSafeMapFiles`. Ne supprime ni sous-dossiers, ni fichiers non `.map`, ni fichiers temporaires sans politique explicite. Ajoute des tests unitaires pour verifier qu'un `.map` valide est supprimable et qu'un fichier non `.map` est conserve, puis lance `:app:compileDebugKotlin` et `:app:testDebugUnitTest`.
