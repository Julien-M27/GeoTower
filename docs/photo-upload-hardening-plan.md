# Durcissement de l'upload photo SignalQuest

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique les risques actuels autour de l'upload photo et decrit les changements a faire.

## Objectif

Rendre l'upload photo plus robuste, plus sur et plus previsible :

- eviter les crashs memoire avec de grosses images ;
- verifier taille, type MIME et nombre de fichiers avant envoi ;
- ne pas perdre les permissions d'acces aux URI pendant un upload differe WorkManager ;
- nettoyer correctement les fichiers temporaires ;
- reduire l'exposition via `FileProvider` ;
- ne pas logger de donnees sensibles ;
- retourner un resultat WorkManager coherent en cas d'echec reseau.

La migration de la cle API SignalQuest est traitee dans `docs/api-key-security-migration.md`. Ne pas resoudre ce probleme en cachant mieux la cle cote Android.

## Probleme actuel

Etat observe dans le code :

- `MainActivity.kt` passe les URI au worker via une chaine `finalUris.joinToString(",")`.
- `SignalQuestUploadWorker.kt` relit cette chaine avec `urisStr.split(",")`.
- `SignalQuestUploadWorker.kt` ouvre chaque URI, decode l'image entiere en `Bitmap`, applique une rotation, puis recompresse en JPEG qualite 90.
- Le worker ne semble pas imposer de limite dure de taille/dimensions avant decodage.
- L'UI indique une limite de 20 Mo par photo, mais cette limite doit aussi etre imposee cote worker et cote backend.
- Les fichiers camera temporaires sont crees dans `context.cacheDir` et utilisent `deleteOnExit()`.
- `file_paths.xml` expose tout le cache via `<cache-path path="." />`.
- Les erreurs API et exceptions peuvent etre loggees de facon trop detaillee.
- Le worker retourne `Result.success()` meme si toutes les photos echouent.

## Risques

### Crash memoire

`BitmapFactory.decodeStream(...)` charge l'image complete en memoire. Une photo moderne peut etre tres grande, parfois plusieurs dizaines de megapixels. Meme si le fichier pese moins de 20 Mo, le bitmap decompresse peut consommer beaucoup plus de RAM.

Exemple approximatif :

- 12000 x 9000 pixels ;
- 4 octets par pixel en ARGB_8888 ;
- plus de 400 Mo en memoire.

Cela peut provoquer un `OutOfMemoryError` ou tuer le worker.

### Upload differe et permissions URI

Les URI venant du Photo Picker, de la camera ou d'un autre provider peuvent avoir des permissions temporaires. Comme l'upload est fait plus tard par WorkManager, il faut garantir que le worker pourra toujours lire les images.

Strategie recommandee : copier les images selectionnees dans un dossier prive de l'app avant d'enqueue le worker, puis passer au worker un identifiant d'upload ou des chemins internes controles.

### Parsing fragile des URI

Passer les URI sous forme de chaine separee par virgules est fragile. Une URI ou une valeur encodee peut contenir des caracteres qui cassent le split. WorkManager `Data` a aussi une limite de taille, donc ce mecanisme ne doit pas transporter trop de donnees.

### Fichiers temporaires mal isoles

`deleteOnExit()` n'est pas fiable dans une app Android. Les fichiers camera peuvent rester dans le cache. De plus, `FileProvider` expose actuellement tout le cache, ce qui augmente le rayon d'exposition si une URI est partagee par erreur.

### Logs sensibles

Logger un corps d'erreur API complet ou une exception brute en release peut exposer des IDs, details serveur, chemins de fichiers, metadata, ou informations utilisateur.

## Architecture cible recommandee

Utiliser une file d'upload interne :

1. L'utilisateur selectionne ou prend des photos.
2. L'app copie chaque image dans un dossier prive dedie, par exemple `cache/sq_upload/<uploadId>/`.
3. Pendant cette copie, l'app valide :
   - nombre maximal de photos ;
   - taille maximale du fichier source ;
   - type MIME accepte ;
   - dimensions raisonnables.
4. L'app stocke un petit manifeste d'upload :
   - `uploadId` ;
   - `siteId` ;
   - `operator` ;
   - `description` ;
   - liste des fichiers internes ;
   - etat de chaque fichier si necessaire.
5. WorkManager recoit seulement `uploadId`.
6. Le worker lit le manifeste, prepare les JPEG limites, upload, met a jour la progression, puis nettoie les fichiers.

Cette approche evite les URI externes fragiles dans WorkManager et facilite les reprises.

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/MainActivity.kt`
- `app/src/main/java/fr/geotower/data/workers/SignalQuestUploadWorker.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SignalQuestUploadScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/fr/geotower/data/api/SignalQuestApi.kt`

## Changements a effectuer

### 1. Remplacer le passage des URI en chaine

Ne plus utiliser :

- `finalUris.joinToString(",")`
- `urisStr.split(",")`

Options acceptables :

- solution minimale : `Data.Builder.putStringArray("uris", finalUris.toTypedArray())`;
- solution recommandee : copier les fichiers en cache prive et passer seulement un `uploadId`.

La solution recommandee est plus robuste car elle evite les problemes de permissions URI et la limite de taille de WorkManager `Data`.

### 2. Creer un dossier cache dedie

Utiliser un dossier controle, par exemple :

- `cacheDir/sq_upload/`
- `cacheDir/sq_camera/`

Ne pas mettre les fichiers directement a la racine de `cacheDir`.

Mettre en place :

- nettoyage apres upload reussi ;
- nettoyage apres annulation ;
- nettoyage des uploads anciens au demarrage ou avant creation d'un nouvel upload ;
- suppression des fichiers partiels si une copie echoue.

Ne pas utiliser `deleteOnExit()`.

### 3. Restreindre `FileProvider`

Dans `file_paths.xml` :

- supprimer ou eviter `<cache-path path="." />` ;
- exposer uniquement les sous-dossiers strictement necessaires :
  - `images/` pour les partages existants si necessaire ;
  - `sq_camera/` pour les captures camera ;
  - eventuellement un dossier dedie aux exports partageables.

Attention : avant de retirer `path="."`, lire aussi les generateurs de partage qui utilisent `FileProvider` pour verifier leurs dossiers.

### 4. Valider taille et type MIME

Avant d'accepter une photo :

- verifier le MIME avec `ContentResolver.getType(uri)` quand disponible ;
- accepter uniquement les formats attendus, par exemple `image/jpeg`, `image/png`, `image/heic`, `image/heif`, `image/webp` selon compatibilite voulue ;
- refuser les types inconnus ou non-image ;
- verifier la taille source via `OpenableColumns.SIZE` quand disponible ;
- imposer une limite dure coherente avec l'UI, par exemple 20 Mo par photo ;
- refaire la verification dans le worker, pas seulement dans l'UI.

Le backend doit aussi imposer ces limites. Les controles Android ameliorent l'UX mais ne remplacent pas les controles serveur.

### 5. Decodage image sans OOM

Remplacer le decode complet direct par une preparation limitee.

Approche recommandee :

- lire d'abord les dimensions avec `BitmapFactory.Options.inJustDecodeBounds = true` ;
- calculer un `inSampleSize` ;
- decoder une version reduite ;
- limiter la dimension maximale, par exemple 2048 ou 2560 px sur le plus grand cote selon la qualite souhaitee ;
- compresser en JPEG avec une qualite controlee ;
- verifier que le fichier final respecte la taille maximale ;
- recycler/liberer les bitmaps ;
- gerer explicitement `OutOfMemoryError`.

Pour Android 9+ (`ImageDecoder`), utiliser une taille cible via `setTargetSize` peut etre plus propre. Garder une compatibilite `BitmapFactory` pour les versions plus anciennes si necessaire.

### 6. Orientation et EXIF

Conserver la correction d'orientation, mais documenter la politique EXIF :

- si les metadata GPS ne sont pas necessaires, les retirer ;
- si une orientation est appliquee physiquement au bitmap, ne pas recopier l'EXIF d'origine ;
- ne pas uploader involontairement des metadata sensibles.

La recompression actuelle tend deja a supprimer beaucoup de metadata, mais il faut en faire un comportement voulu et teste.

### 7. Resultat WorkManager coherent

Le worker ne doit pas toujours retourner `Result.success()`.

Regles conseillees :

- si toutes les photos reussissent : `Result.success()`;
- si certaines reussissent et certaines echouent a cause d'erreurs definitives de fichier : `Result.success()` avec etat partiel notifie ;
- si aucune photo ne reussit a cause d'un probleme reseau temporaire : `Result.retry()`;
- si les entrees sont invalides : `Result.failure()`;
- si le serveur refuse definitivement une photo : ne pas retry indefiniment.

Mettre a jour la notification et l'overlay pour refleter les echecs partiels.

### 8. Logs et erreurs

En release :

- ne pas logger `errorBody` complet ;
- ne pas logger de chemins de fichiers utilisateur ;
- ne pas utiliser `printStackTrace()` directement ;
- logger au maximum un code d'erreur court, gate par `BuildConfig.DEBUG` si detail utile.

Les erreurs utilisateur doivent rester comprehensibles sans exposer les details internes.

### 9. Tests a ajouter

Ajouter des tests autour de la logique pure quand possible :

- parsing/manifest d'upload ;
- refus MIME non-image ;
- refus taille superieure a la limite ;
- preparation JPEG limitee en dimensions ;
- fichier temporaire nettoye apres succes ;
- echec reseau retourne `Result.retry()` si aucune photo n'a reussi ;
- URI contenant des caracteres speciaux ne casse pas la file d'upload.

Si les tests image reels sont lourds, creer de petits bitmaps temporaires dans les tests et tester la preparation avec des dimensions controlees.

## Criteres d'acceptation

La correction est terminee seulement si :

- `finalUris.joinToString(",")` et `urisStr.split(",")` ne sont plus utilises pour transporter les photos d'upload ;
- les fichiers temporaires d'upload sont dans un dossier dedie ;
- `deleteOnExit()` n'est plus utilise pour les photos camera/upload ;
- `file_paths.xml` n'expose plus tout `cacheDir` via `path="."`, sauf justification documentee et limitee ;
- le worker refuse les fichiers trop gros ou non-image ;
- le worker prepare les images avec une limite de dimensions avant upload ;
- une grosse image ne peut pas provoquer facilement un OOM ;
- les logs release ne contiennent pas de corps d'erreur API complet ;
- le worker retourne `success`, `retry` ou `failure` selon la cause reelle ;
- `:app:compileDebugKotlin` passe ;
- les tests existants passent, ou les echecs restants sont documentes comme non lies.

## Commandes de verification suggerees

Depuis la racine du projet Android :

```powershell
rg "joinToString\\(\",\"\\)|split\\(\",\"\\)|deleteOnExit|path=\"\\.\"|printStackTrace|errorBody\\(\\)\\?\\.string" app/src/main
rg "SignalQuestUploadWorker|sq_upload|FileProvider|file_paths" app/src/main
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

## A ne pas faire

- Ne pas seulement augmenter la memoire ou baisser la qualite JPEG sans limiter les dimensions.
- Ne pas faire confiance a l'UI seule pour la limite de 20 Mo.
- Ne pas garder les URI externes comme source unique pour un upload differe.
- Ne pas exposer toute la racine du cache via `FileProvider`.
- Ne pas logger les details complets d'erreur serveur en release.
- Ne pas retry indefiniment une photo invalide.
- Ne pas melanger cette correction avec la migration de la cle API, sauf pour eviter les logs sensibles.

## Prompt court pour l'IA qui implementera

Tu dois durcir l'upload photo SignalQuest de GeoTower. Lis `MainActivity.kt`, `SignalQuestUploadWorker.kt`, `SignalQuestUploadScreen.kt`, `SiteDetailScreen.kt`, `file_paths.xml`, `AndroidManifest.xml` et `SignalQuestApi.kt`. Remplace le passage fragile des URI par virgules par une file d'upload interne ou au minimum un `StringArray`. Strategie preferee : copier les photos dans un dossier prive `cache/sq_upload/<uploadId>/`, passer seulement `uploadId` a WorkManager, valider MIME/taille/nombre de photos, preparer des JPEG limites en dimensions avec downsampling, corriger l'orientation, supprimer les metadata sensibles, nettoyer les fichiers temporaires et restreindre `FileProvider` a des sous-dossiers precis. Le worker doit retourner `success`, `retry` ou `failure` selon la cause reelle, ne pas logger de details sensibles en release, puis verifier avec `rg`, compilation Kotlin et tests unitaires.
