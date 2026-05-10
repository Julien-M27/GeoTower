# Migration securisee de la cle API SignalQuest

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il decrit les changements a faire pour retirer la cle API SignalQuest de l'APK Android.

## Objectif

Supprimer toute cle API SignalQuest embarquee cote Android. L'application ne doit plus contenir `SQ_API_KEY`, `BuildConfig.SQ_API_KEY`, ni construire elle-meme un header `Authorization: Bearer ...` avec un secret statique.

Le resultat attendu est une architecture ou le secret reste cote serveur. L'application Android appelle un backend GeoTower, et ce backend ajoute la cle SignalQuest lorsqu'il contacte l'API SignalQuest.

## Probleme actuel

Etat observe dans le code :

- `app/build.gradle.kts` lit `SQ_API_KEY` depuis `local.properties`.
- La cle est injectee dans `BuildConfig` via `buildConfigField`.
- `BuildConfig.SQ_API_KEY` est utilise pour construire des headers Bearer dans l'app.
- Les usages identifies sont notamment :
  - `SignalQuestUploadWorker.kt`
  - `SiteDetailScreen.kt`
  - `SupportDetailScreen.kt`

Risque : une APK Android peut etre decompilee. Toute cle statique embarquee dans `BuildConfig`, les ressources, les assets, ou une constante Kotlin doit etre consideree comme publique.

## Architecture cible recommandee

Mettre en place un backend proxy GeoTower :

1. L'app Android appelle uniquement des endpoints GeoTower.
2. Le backend GeoTower valide les entrees.
3. Le backend applique rate limiting, limites de taille, journalisation minimale et controles metier.
4. Le backend ajoute le header `Authorization: Bearer <SIGNALQUEST_API_KEY>` cote serveur.
5. Le backend transmet la requete autorisee a SignalQuest.
6. L'app Android recoit uniquement la reponse utile.

Important : l'obfuscation, R8, le certificate pinning ou le stockage dans `local.properties` ne corrigent pas le probleme si la cle finit dans l'APK. Ces mesures peuvent aider ailleurs, mais elles ne securisent pas un secret embarque.

## Changements Android a effectuer

Ne pas commencer par editer au hasard. Lire d'abord les fichiers concernes, puis appliquer des modifications minimales.

### 1. Retirer la cle du build Android

Dans `app/build.gradle.kts` :

- supprimer la lecture de `SQ_API_KEY` depuis `local.properties` ;
- supprimer le `buildConfigField` qui expose `SQ_API_KEY` ;
- verifier que l'app compile sans `SQ_API_KEY` dans `local.properties`.

Ne pas remplacer par une autre constante secrete.

### 2. Remplacer les appels SignalQuest directs

Dans les fichiers qui utilisent actuellement `BuildConfig.SQ_API_KEY` :

- retirer la construction du header Bearer cote Android ;
- remplacer les appels directs vers `signalquest.fr` par des appels vers le backend GeoTower ;
- conserver autant que possible les modeles de donnees existants pour limiter le risque de regression ;
- centraliser le client backend dans une couche reseau claire, par exemple un `GeoTowerBackendApi` ou un client dedie.

Endpoints a couvrir cote backend :

- upload SignalQuest utilise par `SignalQuestUploadWorker.kt` ;
- recuperation speedtest/site utilisee par `SiteDetailScreen.kt` ;
- recuperation speedtest/support utilisee par `SupportDetailScreen.kt`.

### 3. Garder une UX robuste si le backend est indisponible

L'app doit echouer proprement :

- afficher une erreur comprehensible ;
- conserver les retries WorkManager existants pour l'upload si pertinent ;
- ne jamais retomber vers un appel direct SignalQuest avec une cle embarquee ;
- ne jamais logger de token, header Authorization, corps d'erreur sensible ou payload complet inutile.

### 4. Nettoyer les imports et traces

Apres migration :

- `BuildConfig.SQ_API_KEY` ne doit plus exister ;
- `SQ_API_KEY` ne doit plus etre reference dans le module Android ;
- les logs reseau doivent etre gates par `BuildConfig.DEBUG` si conserves ;
- les erreurs API doivent etre reduites a des messages non sensibles en release.

## Changements backend a demander

Si le backend GeoTower n'existe pas encore, il faut le creer avant de supprimer la fonctionnalite mobile.

Le backend doit :

- stocker `SIGNALQUEST_API_KEY` dans une variable d'environnement serveur ;
- ne jamais renvoyer cette cle au client ;
- exposer uniquement les routes necessaires a l'app ;
- valider les IDs, tailles de fichiers, types MIME et champs multipart ;
- appliquer un rate limit par IP, installation, compte ou autre identifiant disponible ;
- journaliser sans secret ;
- retourner des codes d'erreur propres a l'app Android.

Pour les uploads photo :

- definir une taille maximale acceptee ;
- verifier les types MIME attendus ;
- eviter de garder les fichiers temporaires apres transfert ;
- transmettre a SignalQuest uniquement les champs autorises.

## Rotation de cle

La cle actuelle doit etre consideree comme compromise si elle a deja ete utilisee dans une build partagee.

Actions :

1. generer une nouvelle cle SignalQuest ;
2. deployer cette cle uniquement cote backend ;
3. invalider l'ancienne cle ;
4. verifier qu'aucune build Android ne contient encore l'ancienne ou la nouvelle cle.

## Criteres d'acceptation

La migration est terminee seulement si tous ces points sont vrais :

- `rg "SQ_API_KEY|BuildConfig\\.SQ_API_KEY" app` ne retourne rien d'utile ;
- `rg "Authorization.*Bearer|Bearer \\$|Bearer \"" app/src/main` ne trouve aucun secret statique cote Android ;
- l'app compile sans cle dans `local.properties` ;
- les fonctionnalites d'upload et de lecture speedtest fonctionnent via le backend GeoTower ;
- aucun token ou secret n'apparait dans les logs release ;
- une APK release inspectee avec `strings` ou un outil equivalent ne contient pas la cle SignalQuest ;
- les tests unitaires existants passent ou les echecs restants sont documentes comme non lies.

## Commandes de verification suggerees

Depuis la racine du projet Android :

```powershell
rg "SQ_API_KEY|BuildConfig\.SQ_API_KEY" app
rg "Authorization.*Bearer|Bearer \$|Bearer \"" app/src/main
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

Pour une verification plus forte, generer une APK release puis inspecter les chaines avec un outil adapte afin de confirmer que la cle SignalQuest n'est pas presente.

## A ne pas faire

- Ne pas deplacer la cle vers une autre constante Android.
- Ne pas la mettre dans les ressources, assets, `gradle.properties`, manifeste ou native code.
- Ne pas compter sur R8/ProGuard comme protection du secret.
- Ne pas ajouter un endpoint backend generique qui proxy n'importe quelle URL.
- Ne pas logger les headers, tokens ou reponses completes d'erreur.
- Ne pas casser les retries d'upload sans solution de remplacement.

## Prompt court pour l'IA qui implementera

Tu dois retirer la cle API SignalQuest de l'application Android GeoTower. Ne laisse aucun secret SignalQuest dans l'APK. Lis `app/build.gradle.kts`, `SignalQuestUploadWorker.kt`, `SiteDetailScreen.kt`, `SupportDetailScreen.kt`, `SignalQuestApi.kt` et les clients reseau existants. Remplace les appels directs authentifies vers SignalQuest par des appels a un backend GeoTower qui detiendra la cle cote serveur. Supprime `SQ_API_KEY` du build Android, retire tous les usages de `BuildConfig.SQ_API_KEY`, nettoie les logs sensibles, conserve les comportements utilisateur et WorkManager, puis verifie avec `rg`, compilation Kotlin et tests unitaires. Si le backend n'existe pas dans ce repo, ajoute seulement l'interface Android attendue et documente clairement le contrat backend necessaire, sans inventer de secret temporaire cote Android.
