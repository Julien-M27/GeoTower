# Centralisation et durcissement des appels reseau directs

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique comment traiter le point "reseau direct a lisser".

## Objectif

Reduire les appels reseau faits directement depuis les ecrans avec `HttpURLConnection`/`URL.openConnection()` et les centraliser dans la couche `data/api`.

Le but est de rendre les appels reseau :

- plus coherents ;
- toujours timeoutes ;
- toujours fermes correctement ;
- plus faciles a tester ;
- moins bruyants cote UI ;
- plus simples a faire evoluer vers OkHttp/Retrofit ;
- moins risques en release.

## Probleme actuel

GeoTower dispose deja d'un client OkHttp commun dans `RetrofitClient.currentClient` avec :

- `connectTimeout(20s)`;
- `readTimeout(30s)`;
- `writeTimeout(30s)`;
- `callTimeout(60s)`.

Mais plusieurs ecrans font encore des appels directs :

- `MapScreen.kt` appelle Nominatim avec `HttpURLConnection`, sans timeout explicite observe sur cet appel ;
- `NearEmittersScreen.kt` appelle aussi Nominatim avec des timeouts ;
- `SiteDetailScreen.kt` appelle CellularFR photos avec `HttpURLConnection`, sans timeout explicite observe ;
- `SupportDetailScreen.kt` appelle CellularFR photos avec `HttpURLConnection` et timeouts ;
- `ElevationProfileUtils.kt` et `ElevationProfileScreen.kt` utilisent `URL.openConnection()` pour les profils d'elevation.

Certains appels sont corrects localement, mais le comportement est disperse et difficile a auditer.

## Risques

Les appels reseau directs dans les ecrans peuvent causer :

- timeouts oublies ;
- connexions ou streams mal fermes ;
- logique reseau dupliquee ;
- parsing JSON duplique ;
- erreurs gerees differemment selon les ecrans ;
- logs incoherents ;
- tests plus difficiles ;
- UI plus couplee aux details HTTP ;
- risque de blocage long si un serveur ne repond pas.

## Architecture cible recommandee

Creer des services/repositories dedies dans `fr.geotower.data.api` ou `fr.geotower.data.repository`.

Exemples :

- `NominatimService` ou `NominatimApi`;
- `CellularFrApi`;
- `ElevationProfileApi`;
- eventuellement `CommunityPhotosRepository`.

Ces services doivent utiliser :

- Retrofit quand le schema JSON est stable ;
- ou OkHttp `RetrofitClient.currentClient` quand une requete plus flexible suffit ;
- jamais de logique HTTP brute directement dans un composable ou un ecran.

Les ecrans doivent seulement appeler une fonction metier :

```kotlin
val area = repository.searchNominatimArea(query)
val photos = communityPhotosRepository.getCellularFrPhotos(siteId)
val profile = elevationProfileRepository.getProfile(...)
```

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/data/api/RetrofitClient.kt`
- `app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/NearEmittersScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SupportDetailScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/ElevationProfileUtils.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/ElevationProfileScreen.kt`
- `app/src/main/java/fr/geotower/data/api/SignalQuestApi.kt`, comme exemple Retrofit existant.

Trouver les occurrences avec :

```powershell
rg "HttpURLConnection|openConnection\\(|URL\\(|Nominatim|connectTimeout|readTimeout" app/src/main/java
```

## Changements a effectuer

### 1. Centraliser Nominatim

Creer un service dedie pour Nominatim.

Fonctions attendues :

- recherche d'une zone par ville/adresse pour `MapScreen`;
- recherche d'une zone proche pour `NearEmittersScreen`.

Contraintes :

- utiliser un `User-Agent` clair, par exemple `GeoTower/<version>` si possible ;
- mettre `connectTimeout` et `readTimeout` via OkHttp ;
- limiter les resultats (`limit=1` si c'est le comportement actuel) ;
- fermer la reponse avec `use`;
- retourner un type Kotlin simple au lieu d'un `JSONObject` brut ;
- gerer une reponse vide sans exception UI.

### 2. Centraliser CellularFR photos

Aujourd'hui, la logique CellularFR est dupliquee dans `SiteDetailScreen.kt` et `SupportDetailScreen.kt`.

Creer un service/repository :

```kotlin
suspend fun getCellularFrPhotos(siteId: String): List<CommunityPhoto>
```

ou un DTO data-layer qui sera converti en `CommunityPhoto` dans l'UI.

Contraintes :

- utiliser OkHttp/Retrofit ;
- timeouts communs ;
- parser JSON au meme endroit ;
- valider que les URLs relatives commencent bien par `/` ou construire l'URL via une methode sure ;
- ne pas dupliquer la boucle de parsing dans plusieurs ecrans.

### 3. Centraliser le profil d'elevation

Les fonctions elevation utilisent deja des timeouts et `disconnect()`, mais elles restent en `URL.openConnection()`.

Options :

- conserver temporairement si c'est isole et bien gere ;
- ou migrer vers OkHttp pour harmoniser.

Si migration :

- creer `ElevationProfileApi`;
- construire l'URL avec `HttpUrl.Builder` ou `Uri.Builder`;
- fermer la reponse avec `use`;
- retourner `Result` ou un type nullable selon le style existant ;
- garder le parsing dans une fonction testable.

### 4. Nettoyer les ecrans

Les ecrans Compose ne doivent plus contenir :

```kotlin
URL(...).openConnection()
HttpURLConnection
connection.responseCode
connection.inputStream.bufferedReader()
JSONObject(...)
```

Ils peuvent garder :

- gestion de loading/error state ;
- appel repository dans `Dispatchers.IO`;
- transformation UI legere.

### 5. Harmoniser les erreurs et logs

Tous les services reseau doivent :

- retourner une erreur controlee ou `null` selon le contrat ;
- logger avec `AppLogger` si disponible ;
- ne pas logger de payload complet en release ;
- ne pas throw jusqu'a l'UI sauf si l'ecran sait vraiment le gerer.

## Implementation conseillee avec OkHttp

Pour un appel simple sans Retrofit :

```kotlin
val request = Request.Builder()
    .url(url)
    .header("User-Agent", userAgent)
    .build()

return RetrofitClient.currentClient.newCall(request).execute().use { response ->
    if (!response.isSuccessful) return null
    val body = response.body?.string() ?: return null
    parse(body)
}
```

Important :

- toujours utiliser `use`;
- ne jamais lire deux fois `response.body`;
- ne pas logger `body` en release ;
- ne pas faire cet appel sur le thread principal.

## Tests a ajouter

Ajouter des tests unitaires pour les parseurs purs :

- parsing Nominatim avec resultat valide ;
- parsing Nominatim vide ;
- parsing CellularFR avec `photos`;
- parsing CellularFR sans `photos`;
- URL relative CellularFR malformee ;
- parsing elevation profile valide ;
- erreur HTTP convertie en resultat attendu.

Eviter les tests reseau reels en unit tests. Utiliser des JSON fixtures ou MockWebServer si le projet accepte une dependance test.

## Criteres d'acceptation

La correction est terminee seulement si :

- les ecrans principaux ne contiennent plus d'appel direct `HttpURLConnection` pour Nominatim/CellularFR ;
- les timeouts sont centralises via OkHttp/Retrofit ;
- les responses sont fermees avec `use`;
- le parsing JSON duplique CellularFR est supprime ;
- les erreurs reseau n'affichent pas de details techniques en release ;
- `rg "HttpURLConnection|openConnection\\(|URL\\(" app/src/main/java` ne retourne plus que des exceptions justifiees ;
- `:app:compileDebugKotlin` passe ;
- `:app:testDebugUnitTest` passe.

## A ne pas faire

- Ne pas deplacer du code brut `HttpURLConnection` tel quel dans un autre fichier sans ameliorer fermeture/timeouts.
- Ne pas faire d'appel reseau depuis le thread principal.
- Ne pas logger les reponses JSON completes.
- Ne pas dupliquer le parsing CellularFR dans deux ecrans.
- Ne pas changer les comportements UI sans necessite.
- Ne pas melanger ce chantier avec la migration de la cle API SignalQuest.

## Prompt court pour l'IA qui implementera

Tu dois centraliser les appels reseau directs restants de GeoTower. Lis `RetrofitClient.kt`, `MapScreen.kt`, `NearEmittersScreen.kt`, `SiteDetailScreen.kt`, `SupportDetailScreen.kt`, `ElevationProfileUtils.kt` et `ElevationProfileScreen.kt`. Remplace les appels `HttpURLConnection`/`URL.openConnection()` de Nominatim et CellularFR par des services data-layer utilisant `RetrofitClient.currentClient` ou Retrofit, avec timeouts communs, `use` pour fermer les responses, parsing JSON testable et logs non sensibles. Factorise le parsing CellularFR duplique entre les ecrans site/support. Pour l'elevation, migre vers OkHttp ou documente pourquoi l'appel direct reste temporairement acceptable. Termine par `rg "HttpURLConnection|openConnection\\(|URL\\(" app/src/main/java`, `:app:compileDebugKotlin` et `:app:testDebugUnitTest`.
