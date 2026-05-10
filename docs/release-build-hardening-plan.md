# Durcissement du build release Android

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique le probleme du build release actuel et decrit comment le durcir proprement.

## Objectif

Durcir la variante `release` de GeoTower avant diffusion :

- activer R8/minification ;
- activer le shrink des ressources ;
- nettoyer ou limiter les logs en release ;
- verifier que les fonctionnalites sensibles fonctionnent toujours apres obfuscation ;
- documenter les regles ProGuard/R8 necessaires.

Important : ce durcissement ne remplace pas la migration de la cle API SignalQuest. Une cle secrete embarquee dans l'APK reste compromise, meme avec R8 active.

## Probleme actuel

Etat observe :

- `app/build.gradle.kts` configure `release` avec `isMinifyEnabled = false`.
- `isShrinkResources` n'est pas active.
- `app/proguard-rules.pro` contient uniquement le squelette par defaut.
- Beaucoup de `Log.*` et `printStackTrace()` restent dans `app/src/main/java`.
- Certains logs concernent des appels API, speedtest, Android Auto, localisation ou erreurs reseau.

Effets :

- l'APK release est plus facile a decompiler et comprendre ;
- les noms de classes/methodes restent plus lisibles ;
- le code et les ressources inutilises restent dans l'APK ;
- les logs release peuvent exposer des details techniques ou donnees utilisateur ;
- les erreurs sensibles peuvent etre visibles via logcat sur des appareils debugables/rootes ou dans des captures de support.

## Architecture cible recommandee

La variante `release` doit :

1. compiler avec R8 active ;
2. supprimer les ressources inutilisees ;
3. conserver uniquement les classes que les frameworks exigent par reflection/metadata ;
4. ne pas logger de payloads, reponses API, tokens, chemins de fichiers utilisateur ou details internes inutiles ;
5. etre testee comme une vraie build release.

## Fichiers a lire avant modification

Lire au minimum :

- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/java/fr/geotower/data/api/SignalQuestApi.kt`
- `app/src/main/java/fr/geotower/data/api/RetrofitClient.kt`
- `app/src/main/java/fr/geotower/data/workers/SignalQuestUploadWorker.kt`
- `app/src/main/java/fr/geotower/data/workers/DatabaseDownloadWorker.kt`
- `app/src/main/java/fr/geotower/data/workers/MapDownloadWorker.kt`
- `app/src/main/java/fr/geotower/services/GeoTowerCarAppService.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- tous les fichiers trouves par `rg "Log\\.|printStackTrace|errorBody\\(\\)\\?\\.string" app/src/main/java`

## Changements a effectuer

### 1. Activer R8 et le shrink resources

Dans `app/build.gradle.kts`, remplacer progressivement le bloc `release`.

Configuration cible :

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Verifier aussi que le bloc `buildFeatures` n'est pas duplique inutilement.

### 2. Ajouter uniquement les regles R8 necessaires

Ne pas ajouter des regles `-keep` trop larges. Elles annulent une partie de l'interet de R8.

Verifier d'abord si les dependances fournissent deja leurs consumer rules :

- AndroidX Room ;
- Retrofit ;
- OkHttp ;
- Gson/Moshi ;
- Compose ;
- WorkManager ;
- OSMDroid ;
- Mapsforge ;
- AndroidX Car App ;
- Glance.

Ajouter des regles seulement si une build release ou un test runtime casse.

Exemples de cas a surveiller :

- DTO JSON utilises par Gson ou Moshi ;
- classes referencees par nom depuis XML, manifest, WorkManager ou reflection ;
- workers WorkManager ;
- services Android Auto ;
- modeles de serialisation.

### 3. Nettoyer les logs release

Remplacer les logs directs par une strategie claire.

Options acceptees :

- logger central `AppLogger` qui ne sort les details qu'en debug ;
- `if (BuildConfig.DEBUG) { Log.d(...) }` autour des logs de diagnostic ;
- logs release tres courts et non sensibles pour les erreurs systeme vraiment utiles.

Interdits en release :

- bodies d'erreur API complets ;
- headers HTTP ;
- tokens ou cles ;
- payloads complets ;
- chemins de fichiers utilisateur ;
- donnees de localisation precises ;
- stack traces brutes via `printStackTrace()`.

Priorites de nettoyage :

- `SiteDetailScreen.kt`, notamment les logs `SpeedtestDebug` ;
- `SignalQuestUploadWorker.kt`, meme si les logs y sont deja mieux gates ;
- `DatabaseDownloader.kt` ;
- `MapDownloadWorker.kt` ;
- `LiveTrackingService.kt` ;
- `GeoTowerCarAppService.kt` ;
- `MapViewModel.kt` ;
- tous les `printStackTrace()`.

### 4. Ne pas utiliser R8 pour cacher des secrets

R8 rend la lecture plus difficile, mais ne protege pas un secret embarque.

Avant une vraie release publique :

- retirer `SQ_API_KEY` de l'APK ;
- supprimer `BuildConfig.SQ_API_KEY` ;
- verifier avec `strings` ou equivalent que la cle n'est pas presente.

Voir `docs/api-key-security-migration.md`.

### 5. Tester une vraie release

Ne pas se contenter de `compileDebugKotlin`.

Verifier au minimum :

- compilation release ;
- installation d'une APK release locale si possible ;
- ouverture de l'app ;
- telechargement/validation DB ;
- cartes en ligne et hors ligne ;
- upload photo ;
- speedtest SignalQuest ;
- widgets ;
- Android Auto ;
- notifications ;
- deep links `geotower://site/...`, `geotower://support/...`, `geotower://settings?...`.

## Tests et commandes de verification

Depuis la racine du projet :

```powershell
rg "isMinifyEnabled\\s*=\\s*false|isShrinkResources" app/build.gradle.kts
rg "Log\\.|printStackTrace|errorBody\\(\\)\\?\\.string" app/src/main/java
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
```

Si `assembleRelease` echoue a cause de la signature, utiliser une variante release non signee ou configurer une signature locale de test selon les pratiques du projet.

Apres generation d'une APK/AAB release :

```powershell
.\gradlew.bat :app:analyzeReleaseBundle
```

Cette commande peut varier selon la configuration Gradle. L'objectif est de verifier taille, ressources et contenu final.

## Criteres d'acceptation

La correction est terminee seulement si :

- `release.isMinifyEnabled = true` ;
- `release.isShrinkResources = true` ;
- la build release se genere ;
- les tests unitaires passent ou les echecs non lies sont documentes ;
- les logs sensibles sont retires ou gates ;
- aucun `errorBody()?.string()` sensible n'est logge en release ;
- les fonctionnalites critiques ont ete testees sur une build release ;
- les regles `proguard-rules.pro` sont minimales et justifiees ;
- la cle API SignalQuest n'est pas consideree comme protegee par R8.

## A ne pas faire

- Ne pas ajouter `-dontobfuscate`.
- Ne pas ajouter des `-keep class ** { *; }`.
- Ne pas desactiver R8 apres un crash sans diagnostiquer la regle manquante.
- Ne pas laisser les logs API ou stack traces brutes en release.
- Ne pas croire que R8 securise une cle API embarquee.
- Ne pas activer minify juste avant publication sans tester une vraie APK release.

## Prompt court pour l'IA qui implementera

Tu dois durcir le build release Android de GeoTower. Lis `app/build.gradle.kts`, `app/proguard-rules.pro` et les usages de `Log.*`, `printStackTrace()` et `errorBody()?.string()` dans `app/src/main/java`. Active `isMinifyEnabled = true` et `isShrinkResources = true` pour `release`, garde `proguard-android-optimize.txt`, puis ajoute seulement les regles R8 strictement necessaires apres test. Nettoie les logs sensibles ou gate-les avec `BuildConfig.DEBUG`, sans utiliser R8 comme protection pour `SQ_API_KEY`. Verifie avec compilation debug, tests unitaires et generation d'une build release. Si une fonctionnalite casse en release, ajoute une regle `-keep` minimale et documentee plutot que des regles globales.
