# Plan d'implémentation — Couverture théorique (« Viewshed » / visibilité radio) — PRÉCISION MAX / AZIMUTS RÉELS

> Document autonome destiné à une IA chargée d'implémenter la fonctionnalité.
> Projet : GeoTower (Android, Kotlin, Jetpack Compose, package `fr.geotower`, carte = **osmdroid**).
> Lis intégralement ce plan avant d'écrire du code. Réutilise au maximum le pipeline de **profil altimétrique** existant.
>
> **Objectif de précision (décision produit) :** modéliser la couverture **par antenne réelle** (azimut + hauteur de chaque antenne du site), pas un disque 360° uniforme. Viser la meilleure précision géométrique possible dès la V1.

---

## 1. Objectif

À partir d'un **site**, calculer et afficher une **zone de visibilité radioélectrique** (viewshed) **secteur par secteur**, en itérant sur **chaque antenne** du site avec son **azimut** et sa **hauteur** réels, compte tenu :
- du **terrain** (MNT RGE ALTI, déjà utilisé par le profil) ;
- des **obstacles bâtis** (BD TOPO, déjà utilisés par le profil « avec obstacles ») — **activés par défaut** pour la précision ;
- de la **hauteur de chaque antenne** (émetteur) et d'une hauteur récepteur (1,5 m) ;
- de la **courbure terrestre** (rayon effectif k = 4/3) ;
- du **diagramme horizontal d'ouverture** (paramétrique, voir §2 D6) centré sur l'azimut réel ;
- du **dégagement de la 1ʳᵉ zone de Fresnel** selon la fréquence de l'antenne.

Le résultat = **union des secteurs** de toutes les antennes, affichée **(a)** en **overlay carte** (polygones colorés par secteur/opérateur) et **(b)** via un **écran-outil dédié** (paramètres + partage image), sur le modèle de l'écran « profil altimétrique ».

### ⚠️ Avertissement scientifique + plafond de précision des données ANFR (à refléter dans l'UI)
C'est une estimation **géométrique de ligne de visée (LoS)**, **PAS** une simulation de propagation RF.
**Ce qu'ANFR fournit par antenne** (table `antenne`) : `azimut`, `hauteur_bas`, `tae_id` (type), `is_fh`. **Rien d'autre.**
**Ce qu'ANFR NE fournit PAS** (et qu'on ne peut donc pas modéliser fidèlement) : **tilt** (down-tilt mécanique/électrique), **gain**, **ouverture de faisceau**, **puissance/PIRE**, **diagramme constructeur**.
Conséquences à assumer et documenter :
- L'**ouverture** est un **modèle paramétrique générique** (pas la vraie largeur de lobe).
- Le **tilt** réel est inconnu ⇒ modélisé via un **down-tilt supposé** réglable par l'utilisateur (défaut 3°, voir D6). Hypothèse, pas une mesure : le disclaimer doit le dire.
- Le lien **antenne → bande/fréquence** n'est pas explicite dans ANFR (la table `antenne` ne porte pas la bande). On approxime (voir §5.3).
Nommer la fonctionnalité **« Visibilité théorique / Ligne de visée »**, jamais « couverture réseau réelle ». Disclaimer visible à l'écran et sur l'image partagée.

---

## 2. Décisions d'architecture (tranchées avec le mainteneur — « précision max + azimuts réels »)

### D1 — Récupération du terrain : **batch `elevation.json` + terrain mutualisé** (dès la V1)
La précision « par antenne » multiplie les rayons. Pour tenir le budget réseau, **on découple** :
1. **TerrainField (réseau, UNE fois par site)** : une grille de **profils radiaux** autour du site (360° à pas fin, sampling fin), récupérée via le endpoint **batch** IGN `elevation.json` (listes de points `lon=a|b|…&lat=…`), **+ une passe BD TOPO** pour surélever les points bâtis. Mutualisée pour toutes les antennes.
2. **SectorViewshed (CPU, PAR antenne)** : visibilité calculée **localement** sur ces profils déjà téléchargés, avec la hauteur/fréquence propres à chaque antenne. **Zéro appel réseau supplémentaire par antenne.**

**Justification :** un site à 3 secteurs × 3 bandes (9 antennes) coûte alors ~le même réseau qu'un seul viewshed, tout en autorisant un pas angulaire fin (précision). ⚠️ Le endpoint batch **n'existe pas encore** dans le code (l'actuel `ElevationProfileApi` ne fait que `elevationLine.json`, 1 ligne/appel) → **à ajouter** (§4.1).

### D2 — Modèle physique : **LoS géométrique + courbure + Fresnel + diagramme horizontal**
- Visibilité = ligne émetteur→cible non coupée par le terrain/bâtiments (angle vertical croissant).
- Courbure terrestre via `R_eff = (4/3)·6 371 000 m`.
- Fresnel : dégagement à 60 % de la 1ʳᵉ zone (réutilise `elevationFresnelClearanceMeters`), selon la fréquence de l'antenne.
- **V1 = distance jusqu'au premier blocage** par rayon (poches re-visibles au-delà d'un obstacle ⇒ V2, multi-polygones).

### D3 — Rendu : **overlay carte** (principal) + **écran-outil** (paramètres/partage)
- Overlay osmdroid custom, **multi-secteurs** (un polygone par antenne/secteur, couleur par opérateur ; option « fusionner en une enveloppe »).
- Écran dédié `theoretical_coverage/{id}` (mini-carte + paramètres + partage), routé/gated comme `elevation_profile/{id}`.

### D4 — Géométrie **par antenne** (cœur de la précision)
Itérer sur **toutes les antennes** du site **hors faisceaux hertziens** (`is_fh == 1` exclus). Pour chaque antenne :
- **Émetteur** : `H_tx = z_sol(site) + hauteur_bas(antenne)` (hauteur **propre** à l'antenne, pas le max du site).
- **Secteur** : centré sur `azimut(antenne)` réel, demi-largeur issue du diagramme (D6) ; antenne **omni** (selon `tae_id`) ⇒ 360°.
- **Fréquence** : voir §5.3 (pour Fresnel + plafond de portée).
- Récepteur : `z_terrain(cible) + 1,5 m` (constante existante).
Couverture du site = **union** des secteurs.

### D6 — Diagrammes horizontal **et vertical (tilt)** : **modèle paramétrique** (faute de données ANFR)
- **Type d'antenne** via `tae_id` → `ref_type_antenne.libelle` : classer en **omni** vs **sectorielle/panneau** (mapping libellé → catégorie ; FH exclus).
- **Horizontal — sectorielle** : 3GPP `A_h(θ) = −min(12·(θ/θ_3dB)², A_m)`, défaut `θ_3dB = 65°`, `A_m = 30 dB`. **Omni** : `A_h = 0`, 360°.
- **Vertical (tilt) — INCLUS en V1** : **down-tilt supposé global** réglable (défaut **3°**) + ouverture verticale générique (défaut **10°**, réglage avancé). Le tilt **borne la portée au sol** : on coupe le rayon dès que le sol remonte au-dessus du bord lointain du lobe incliné (règle en §4.3). C'est l'atténuation principale de la surestimation.
- **Seuil/dégradé** : gain combiné `A_h + A_v` (dB) → borne l'arc tracé (seuil défaut `−10 dB`) et colore en dégradé (proximité de l'axe).
- ⚠️ **Ouverture horizontale, tilt et ouverture verticale sont des HYPOTHÈSES** (absents d'ANFR), pas le vrai lobe constructeur — à afficher comme tels.

### D5 — Performance & respect de l'IGN
Calcul **en arrière-plan**, **annulable**, **progression** ; **concurrence limitée** (≤ 6 requêtes) ; **cache** agressif par configuration ; **bornes via feature-flags** (rayon, pas angulaire, points/requête, features WFS) ; couche **désactivable** par `Providers.ELEVATION_IGN`. Aperçu rapide (pas grossier) → raffinement.

---

## 3. Référence du code existant (ancrages — vérifier avant de coder)

| Sujet | Fichier | À réutiliser / à étendre |
|---|---|---|
| **API élévation (ligne)** | `app/src/main/java/fr/geotower/data/api/ElevationProfileApi.kt` | `getProfile(...)` → `ElevationProfileApiResult(points: List<ElevationProfileApiPoint(lat, lon, elevation, distanceMeters)>, …)`. OkHttp direct, URL `…/calcul/alti/rest/elevationLine.json`, `resource=ign_rge_alti_wld`, sampling ≤ 2000, **1 ligne/appel**. `parseElevationProfile(json, …)` (lit `{"elevations":[{lat,lon,z}]}`) **réutilisable pour le batch**. **À AJOUTER : un appel `elevation.json` (points multiples).** |
| **Obstacles bâtis** | `app/src/main/java/fr/geotower/data/api/BdTopoBuildingsApi.kt` | `fetchBuildingsForSegment(...)` (WFS, ≤ 5000 features) ; `BdTopoBuilding.contains(lon, lat)` + `topAltitude(terrain)`. **Généraliser** à une bbox de disque (tuilage si > 5000). |
| **Calculs LoS / Fresnel** | `app/src/main/java/fr/geotower/ui/screens/emitters/ElevationProfileUtils.kt` | `elevationFresnelClearanceMeters(distance, total, freqMHz)`, `elevationLineHeightAt(...)`. Constante `ELEVATION_USER_EYE_HEIGHT_METERS = 1.5`. |
| **Fréquences** | même fichier | `extractElevationProfileFrequencies(raw)`, ordre de bandes, `DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ = 3500`. |
| **Antennes du site** | `data/db/GeoTowerDao.kt`, `data/AnfrRepository.kt`, `data/models/OfflineEntities.kt` | `AntenneDbEntity(aerId, idAnfr, idSupport, taeId, azimut: Int?, hauteurBas: Double?, isFh)` ; `ref_type_antenne(taeId → libelle)` ; `getPhysiqueDetails(idAnfr)`. **À AJOUTER probablement** : une requête DAO renvoyant les antennes brutes (azimut, hauteurBas, taeId, isFh) d'un `idAnfr` — vérifier si elle existe déjà avant d'en créer une. |
| **Site** | `OfflineEntities.kt` | `LocalisationEntity(idAnfr, latitude, longitude, operateur, azimuts, bandMask)`. |
| **Bande → MHz** | `OfflineEntities.kt` (`RadioFilterMasks`) | constantes `BAND_*` → fréquence MHz. |
| **Écran-outil (patron)** | `ui/screens/emitters/ElevationProfileScreen.kt` | throttle 60 s, cache par clé, persistance JSON, offline, toggle obstacles, sélecteur fréquence, rendu Canvas. |
| **Partage image (patron)** | `ui/components/ElevationProfileShareGenerator.kt` | `createElevationProfileShareBitmap(...)` (Canvas, bannière opérateur, QR `geotower://site/{id}`, thèmes, FileProvider, copier/partager). |
| **Carte / overlays** | `ui/screens/map/MapScreen.kt` | `MapView` via `AndroidView`, liste `overlays`, `mapViewRef`. Patron overlay : `SignalQuestCoverageOverlay : Overlay()` (`draw(canvas, projection)` + `projection.toPixels(GeoPoint, point)`). Centrage : `mapViewRef?.controller?.setCenter/setZoom`. |
| **ViewModel carte** | `ui/screens/map/MapViewModel.kt` | patron `_x = MutableStateFlow(...)` + `loadXInBox(...)`. |
| **Toggle couche** | `ui/screens/map/MapSettingsSheet.kt` + `utils/AppConfig.kt` | `showSignalQuestCoveragePoints` (`mutableStateOf`) + `PREF_*` + `loadMapDisplayPreferences` + `SelectableButton`. |
| **Routage écran-outil** | `MainActivity.kt` | route `elevation_profile/{id}` gated par `Screens.ELEVATION_PROFILE && Features.SITE_ELEVATION_PROFILE && Providers.ELEVATION_IGN` → sinon `DisabledFeatureRoute`. |
| **Unités** | `utils/AppConfig.kt` | `distanceUnit` (0 = km/m, 1 = miles/ft). |
| **Flags** | `data/config/RemoteFeatureFlags.kt` | `Screens`/`Features`/`Providers`/`Limits` + map de défauts ; `isScreenEnabled/isFeatureEnabled/isProviderEnabled/limitOrDefault`. |
| **i18n** | `app/src/main/res/values*/strings.xml` | **7 locales** (`values`, `-fr`, `-en`, `-de`, `-es`, `-it`, `-pt`). |

---

## 4. Cœur algorithmique

Nouveau package suggéré : `fr.geotower.data.coverage`. Code **pur** (testable), réseau injecté.

### 4.1 Étendre l'API élévation au batch — `ElevationProfileApi.getElevations(points)`
Ajouter un appel **points multiples** au service IGN (même base que `elevationLine.json`) :
```kotlin
// Endpoint: https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevation.json
// Params: lon=l1|l2|…  lat=la1|la2|…  resource=ign_rge_alti_wld  delimiter=|  zonly=false  measures=false
// Réponse: {"elevations":[{"lon","lat","z"}, …]} dans le MÊME ORDRE que l'entrée → réassociation par index.
suspend fun getElevations(points: List<DoubleArray /*[lon,lat]*/>): List<Double /*z, NaN si invalide*/>
```
- **Chunking** : découper en lots (borne `Limits.coverageMaxPointsPerRequest`, défaut 2000) ; **préférer POST** (form-urlencoded) pour éviter la limite de longueur d'URL en GET.
- Réutiliser le parsing existant (`elevations[].z`, invalide si `z <= -99990`).

### 4.2 TerrainField — grille radiale mutualisée (réseau, 1×/site)
```kotlin
data class TerrainRay(val bearingDeg: Double, val distances: DoubleArray, val ground: DoubleArray /*z terrain ± toit*/)
data class TerrainField(val siteLat: Double, val siteLon: Double, val rays: List<TerrainRay>, val sampleStepM: Double)

class TerrainFieldLoader(
    private val getElevations: suspend (List<DoubleArray>) -> List<Double>,
    private val fetchBuildings: suspend (bbox) -> List<BdTopoBuilding>
) {
    suspend fun load(
        siteLat: Double, siteLon: Double,
        maxRadiusM: Double, angularStepDeg: Double, sampleStepM: Double,
        includeObstacles: Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): TerrainField
}
```
Étapes :
1. Construire les relèvements `0..360 step angularStepDeg` (360° complet : mutualisable par toutes les antennes, omni comprises).
2. Pour chaque relèvement, points échantillonnés `d = sampleStepM, 2·sampleStepM, … ≤ maxRadiusM` → `destinationPoint(site, bearing, d)`.
3. Récupérer toutes les altitudes en **batch** (`getElevations`, chunké).
4. Si `includeObstacles` : un **seul** `fetchBuildings` sur la bbox du disque (tuilage si > 5000 features) puis surélever chaque point bâti (`contains` → `topAltitude`).
5. Assembler `TerrainField`.

> Le terrain ne dépend NI de l'antenne NI de la hauteur d'émetteur ⇒ on le calcule une fois et on le réutilise pour tous les secteurs.

### 4.3 SectorViewshed — visibilité par antenne (CPU, local)
```kotlin
data class AntennaSpec(
    val aerId: String, val azimutDeg: Double, val txHeightM: Double,
    val omni: Boolean, val halfBeamDeg: Double, val frequencyMHz: Int?, val operator: String?
)
data class SectorRay(val bearingDeg: Double, val maxVisibleM: Double, val relGainDb: Double)
data class SectorViewshed(val antenna: AntennaSpec, val rays: List<SectorRay>) { fun toPolygon(siteLat, siteLon): List<GeoPoint> }

data class ViewshedParams(
    val maxRadiusM: Double, val curvature: Boolean = true,
    val fresnel: Boolean = false, val frequencyMHz: Int? = null,
    val tiltDeg: Double = 3.0, val verticalBeamwidthDeg: Double = 10.0,
    val theta3dbDeg: Double = 65.0, val patternAmDb: Double = 30.0,
    val gainThresholdDb: Double = -10.0
)
object ViewshedSolver {
    fun solveSector(field: TerrainField, site: LatLon, ant: AntennaSpec, params: ViewshedParams): SectorViewshed
}
```
Pour chaque relèvement du secteur (`|Δ(bearing, azimut)| ≤ halfBeam`, ou tous si omni) :
1. `H_tx = field.ground@site + ant.txHeightM` (z_sol = première valeur du rayon). `vBeam = params.verticalBeamwidthDeg`, `tilt = params.tiltDeg`.
2. Parcourir les échantillons `i` croissants, `alphaMax = -∞`, `maxVisible = 0` :
   ```
   bulge   = if (curvature) d_i² / (2·R_eff) else 0           // R_eff = 4/3 · 6_371_000
   zCible  = ground_i + 1.5 - bulge
   alpha_i = atan2(zCible - H_tx, d_i)                        // angle vertical (rad, négatif vers le bas)
   // (a) Bornage par TILT (bord lointain du lobe incliné) — diagramme vertical
   if (tilt > vBeam/2 && toDegrees(alpha_i) >= -(tilt - vBeam/2)) break   // sol remonté au-dessus du lobe
   // (b) Ligne de visée (terrain/bâti) + Fresnel
   visible = alpha_i >= alphaMax
   if (fresnel && visible && freq != null) visible = fresnelClear(rayon, jusqu'à d_i, H_tx, freq)
   if (!visible) break                                        // obstacle dur
   alphaMax = max(alphaMax, alpha_i)
   maxVisible = d_i
   ```
   Portée du rayon = `maxVisible`, bornée par le **premier** des deux : obstacle (LoS/Fresnel) **ou** bord lointain du lobe incliné (tilt). `tilt ≤ vBeam/2` ⇒ pas de bornage tilt (= « sans tilt », portée max). *(V1 : on ignore le creux sous-lobe en champ proche pour garder un polygone en étoile.)*
3. `aH = horizontalPatternDb(Δbearing, θ3dB, Am)` (omni ⇒ 0) ; ignorer le rayon si `aH < gainThresholdDb` (hors azimut). `relGainDb = aH` (+ `aV` pour le dégradé).
4. `SectorRay(bearing, maxVisible, relGainDb)`.

### 4.4 Assemblage site
```kotlin
data class SiteCoverage(val idAnfr: String, val sectors: List<SectorViewshed>, val computedAtMillis: Long)
class CoverageEngine(...) {
    suspend fun compute(idAnfr: String, params: ViewshedParams, onProgress: (Int,Int)->Unit): SiteCoverage
    // 1) resolveEmitters (§5)  2) TerrainFieldLoader.load (réseau)  3) ViewshedSolver.solveSector par antenne (CPU)
}
```
Rendu : un polygone par secteur (couleur opérateur), option « union/dissolve » en une enveloppe.

### 4.5 Bornes physiques optionnelles (plafond de portée par fréquence)
Plafonner `maxRadiusM` par fréquence (pratique, **pas** un modèle de propagation) : 700→10 km, 800–900→8, 1800–2100→6, 2600→5, 3500→4, ≥26000→1. Fonction `suggestedMaxRadiusMeters(freqMHz)`.

---

## 5. Données antenne (entrée de la précision)

### 5.1 Récupérer **toutes** les antennes du site
Helper `data/coverage/SiteEmitterResolver.kt` :
```kotlin
suspend fun resolveAntennas(repo: AnfrRepository, idAnfr: String): List<AntennaSpec>
```
- Source : table `antenne` filtrée `id_anfr = idAnfr AND is_fh = 0`. **Vérifier** si une requête DAO renvoyant `azimut`, `hauteur_bas`, `tae_id` par antenne existe ; sinon l'ajouter à `GeoTowerDao` (ne PAS modifier le schéma, juste une `@Query SELECT`).
- Coordonnées site : `LocalisationEntity` (lat/lon).
- Ignorer les antennes sans `azimut` ou sans `hauteur_bas` (ou fallback hauteur support).

### 5.2 Type d'antenne → omni/sectorielle (D6)
- Joindre `tae_id` → `ref_type_antenne.libelle`. Construire un mapping libellé → catégorie (`OMNI`, `PANEL/SECTOR`, `OTHER`). Heuristique sur le libellé (contient « omni » ⇒ omni ; « panneau »/« sectoriel » ⇒ secteur ; défaut ⇒ secteur 65°). **Documenter** le mapping ; le rendre ajustable.

### 5.3 Fréquence par antenne (limite ANFR)
ANFR ne lie pas l'antenne à la bande. Stratégie :
- Par défaut, fréquence = **valeur choisie par l'utilisateur** (sélecteur global, défaut 3500) appliquée à tous les secteurs **pour Fresnel/plafond**.
- Option : si une seule bande sur le site, l'utiliser ; sinon proposer le choix. **Documenter** que l'attribution bande↔antenne est une approximation.

---

## 6. Rendu carte — overlay multi-secteurs

### 6.1 Overlay — `ui/screens/map/TheoreticalCoverageOverlay.kt`
- Dessine **une liste de polygones** (un par secteur), chacun avec une couleur (par opérateur) et un remplissage semi-transparent ; trait du secteur. Patron `SignalQuestCoverageOverlay` (`draw` + `projection.toPixels`).
- Ajouter à `overlays` **avant** `markersOverlay`. Sur changement : `setSectors(...)` + `mapViewRef?.invalidate()`.
- **Amélioration** : opacité modulée par `relGainDb` (axe plus marqué) ; option « fusionner » les secteurs en une enveloppe ; coloration par marge de Fresnel.

### 6.2 Toggle + déclenchement
- `AppConfig` : `var showTheoreticalCoverage = mutableStateOf(false)` + `PREF_SHOW_THEORETICAL_COVERAGE` ; chargé dans `loadMapDisplayPreferences`. Toggle dans `MapSettingsSheet` (copier le bloc SignalQuest).
- Bouton « Couverture théorique » dans `SiteDetailScreen`/`SupportDetailScreen` → active le toggle, navigue carte centrée (`geotower://map?lat=..&lon=..&zoom=14`) **ou** ouvre l'écran-outil (§7).
- `MapViewModel` : `coverageSectors: StateFlow<List<CoveragePolygon>>` + progression ; `loadTheoreticalCoverageForSite(idAnfr, params)` (cache → sinon `CoverageEngine.compute`).

---

## 7. Écran-outil dédié (paramètres + partage) — recommandé
Package `ui/screens/coverage/`.
- **`TheoreticalCoverageScreen(navController, repository, idAnfr)`** : mini-carte osmdroid + overlay multi-secteurs ; **panneau paramètres** : rayon, qualité (pas angulaire + sampling), obstacles on/off (défaut **on**), fréquence, Fresnel on/off, **down-tilt supposé (défaut 3°)** + ouverture verticale (avancé), seuil de diagramme (−10 dB), `θ_3dB`, hauteur émetteur (auto par antenne / override) ; **liste des antennes** détectées (azimut, hauteur, type) ; **progression + annulation** ; **disclaimer** ; **partager**.
- Patron `ElevationProfileScreen` : `Scaffold` + `GeoTowerBackTopBar` + `rememberSafeBackNavigation(fallbackRoute="home")`, throttle/cache, offline.
- **Partage** : `ui/components/TheoreticalCoverageShareGenerator.kt` calqué sur `ElevationProfileShareGenerator` (mini-carte + secteurs, paramètres, **disclaimer**, QR `geotower://site/{idAnfr}`, FileProvider, copier + partager).

---

## 8. Navigation & deep links — `MainActivity.kt`
```kotlin
composable(
    route = "theoretical_coverage/{id}",
    arguments = listOf(navArgument("id") { type = NavType.StringType }),
    deepLinks = listOf(navDeepLink { uriPattern = "geotower://coverage/{id}" })
) { back -> Box(Modifier.padding(innerPadding)) {
    val id = back.arguments?.getString("id").orEmpty()
    if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.THEORETICAL_COVERAGE) &&
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_THEORETICAL_COVERAGE) &&
        featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ELEVATION_IGN))
        TheoreticalCoverageScreen(navController, repository, id)
    else DisabledFeatureRoute(navController, txtUnavailable)
} }
```

---

## 9. Réglages & feature flags — `data/config/RemoteFeatureFlags.kt`
```kotlin
// Screens
const val THEORETICAL_COVERAGE = "theoreticalCoverage"               // défaut: true
// Features
const val SITE_THEORETICAL_COVERAGE = "site.theoreticalCoverage"     // défaut: true
// Providers : réutiliser ELEVATION_IGN (même source ; pas de nouveau provider)
// Limits
const val COVERAGE_MAX_RADIUS_KM = "coverageMaxRadiusKm"             // défaut: 8
const val COVERAGE_MIN_ANGULAR_STEP_DEG = "coverageMinAngularStepDeg"// défaut: 1
const val COVERAGE_SAMPLE_STEP_M = "coverageSampleStepM"             // défaut: 10
const val COVERAGE_MAX_POINTS_PER_REQUEST = "coverageMaxPointsPerRequest" // défaut: 2000
const val COVERAGE_MAX_CONCURRENT_REQUESTS = "coverageMaxConcurrentRequests" // défaut: 6
const val COVERAGE_MAX_WFS_FEATURES = "coverageMaxWfsFeatures"       // défaut: 5000
const val COVERAGE_DEFAULT_TILT_DEG = "coverageDefaultTiltDeg"       // défaut: 3  (down-tilt supposé)
const val COVERAGE_DEFAULT_VBEAM_DEG = "coverageDefaultVBeamDeg"     // défaut: 10 (ouverture verticale)
```
> Vérifier les noms exacts des objets et de la map de défauts avant d'ajouter. Tout le moteur doit respecter ces bornes (`limitOrDefault`).

---

## 10. i18n — 7 fichiers (`values`, `-fr`, `-en`, `-de`, `-es`, `-it`, `-pt`)
```
coverage_title, coverage_button, settings_coverage_layer_label,
coverage_param_radius, coverage_param_quality, coverage_param_obstacles,
coverage_param_frequency, coverage_param_fresnel, coverage_param_tilt, coverage_param_vbeam, coverage_param_beamwidth, coverage_param_gain_threshold,
coverage_tx_height_auto, coverage_tx_height_custom, coverage_antennas_detected,
coverage_progress, coverage_cancel, coverage_disclaimer, coverage_share,
coverage_error_offline, coverage_empty
```
`coverage_disclaimer` doit mentionner : ligne de visée (pas couverture réelle) **et** absence de tilt/gain/puissance dans la donnée ANFR. Placeholders `%1$s`/`%1$d`. Tenir les 7 fichiers synchronisés.

---

## 11. Performance & garde-fous (CRITIQUE)
- **Budget réseau par site** ≈ `(nbRelèvements × nbÉchantillons) / pointsParRequête` requêtes batch + 1 (ou tuilage) WFS. Ex. 360 relèvements × 800 échantillons (8 km @ 10 m) = 288 000 points → ~144 requêtes de 2000 pts… **trop**. ⇒ **régler les défauts** : pas angulaire 1–2°, sampling 10–15 m, et surtout **borne dure** ; pour 8 km viser plutôt sampling 20 m + pas 2° en « qualité haute », et un mode **aperçu** (pas 4°, sampling 30 m). Exposer la qualité, mais **plafonner** via flags. Le calcul par antenne reste gratuit (CPU).
- **Mutualisation terrain** : ne récupérer le terrain **qu'une fois** par site (toutes antennes le partagent) — c'est le levier principal.
- **Concurrence ≤ 6**, `Dispatchers.IO`, annulation coopérative (`ensureActive()`), progression visible.
- **Cache** par `idAnfr + rayon + pasAngulaire + sampling + obstacles + courbure` (terrain) et résultat secteurs ; persister (JSON). Pas de recalcul à chaque recomposition (throttle + clé).
- **Respect IGN** : couche désactivable (`Providers.ELEVATION_IGN`), bornes strictes, cache ; envisager throttle inter-requêtes.
- **WFS dense** : en ville, > 5000 bâtiments ⇒ tuiler la bbox du disque.

---

## 12. Contraintes techniques du dépôt à respecter
- **Horloge** en paramètre (`nowMillis`) pour le domaine ; `System.currentTimeMillis()` côté Android.
- **Coroutines** : I/O sur `Dispatchers.IO` ; calcul annulable ; un rayon/une antenne en échec ne casse pas le tout (`runCatching` + `AppLogger`).
- **Réutiliser** `parseElevationProfile`, `BdTopoBuildingsApi`, `elevationFresnelClearanceMeters` — ne pas réimplémenter.
- **Disclaimer obligatoire** (LoS + limites données ANFR) à l'écran et sur l'image.

---

## 13. Découpage en lots livrables (ordre de dépendance)

### Lot 1 — Moteur (batch + terrain mutualisé + visibilité par antenne)
- [ ] `ElevationProfileApi.getElevations(points)` (batch `elevation.json`, chunké, POST) (§4.1).
- [ ] `TerrainFieldLoader` (grille radiale + obstacles mutualisés) (§4.2).
- [ ] `ViewshedSolver.solveSector` (LoS + courbure + Fresnel + diagramme) (§4.3) ; `CoverageEngine` (§4.4) ; `SiteEmitterResolver` (§5).
- [ ] **Tests unitaires** (réseau simulé) : colline qui masque, plaine, courbure, secteur vs omni, deux antennes d'azimuts opposés → union cohérente, antenne FH ignorée.
- **Done** : tests verts ; coût réseau = 1 chargement terrain par site quel que soit le nb d'antennes.

### Lot 2 — Rendu carte + déclenchement depuis le site
- [ ] `TheoreticalCoverageOverlay` multi-secteurs (§6.1) + `overlays`.
- [ ] `AppConfig` toggle + `MapSettingsSheet` ; `MapViewModel.loadTheoreticalCoverageForSite` + progression + cache (§6.2).
- [ ] Bouton « Couverture théorique » dans la fiche site (§6.2) ; flags `Screens`/`Features`/`Limits` (§9).
- **Done** : depuis un site, les secteurs réels s'affichent sur la carte (couleur par opérateur), avec progression/annulation, sous les bornes de flags.

### Lot 3 — Écran-outil + partage + i18n + finitions
- [ ] `TheoreticalCoverageScreen` (paramètres, liste antennes, disclaimer) + route `theoretical_coverage/{id}` (§7–8).
- [ ] `TheoreticalCoverageShareGenerator` (image + copier/partager).
- [ ] Réglages avancés (θ3dB, seuil gain, sampling/pas) ; i18n 7 langues (§10).
- **Done** : parcours site → écran-outil paramétrable → partage ; couche togglable.

---

## 14. Critères d'acceptation (global)
1. Sur un site à 3 secteurs, **3 lobes orientés selon les azimuts réels** apparaissent (pas un disque uniforme), couleur par opérateur, en ≤ ~20 s avec progression/annulation.
2. Le coût réseau d'un site à 9 antennes ≈ celui d'un site à 1 antenne (terrain mutualisé) — vérifiable par le nombre de requêtes.
3. Derrière un relief, le secteur concerné se raccourcit nettement ; « obstacles » (défaut on) réduit la portée en zone bâtie. **Augmenter le down-tilt raccourcit visiblement la portée ; tilt ≤ ½ ouverture verticale = portée maximale (« sans tilt »).**
4. Les antennes `is_fh = 1` sont ignorées ; les antennes omni couvrent 360°.
5. Bornes de flags respectées ; calcul annulable ; aucune requête si `Providers.ELEVATION_IGN` désactivé ; résultat identique servi depuis le cache.
6. Disclaimer (LoS + absence de tilt/gain/puissance ANFR) visible à l'écran et sur l'image ; strings dans les 7 locales.

---

## 15. Points à confirmer / risques avant de coder
- **Endpoint batch IGN** : confirmer le chemin exact `…/calcul/alti/rest/elevation.json`, le format de réponse, l'ordre des points, et la limite réelle de points (GET vs POST). C'est le pivot de la précision/perf.
- **Requête DAO antennes** : vérifier/ajouter une `@Query` renvoyant `azimut, hauteur_bas, tae_id, is_fh` par `id_anfr` (sans toucher au schéma).
- **Mapping `ref_type_antenne.libelle` → omni/secteur** : lister les libellés réels présents en base pour caler les heuristiques (§5.2).
- **Plafond de précision (à assumer)** : pas de gain/ouverture/tilt/puissance dans ANFR ⇒ diagrammes horizontal **et vertical** paramétriques. Le **down-tilt supposé** (défaut 3°) et l'**ouverture verticale** (défaut 10°) sont des **hypothèses réglables**, pas des mesures — caler les défauts et l'afficher clairement.
- **Attribution bande↔antenne** approximée (§5.3) : impacte Fresnel/plafond ; valider l'UX du sélecteur de fréquence.
- **Budget réseau réel** : caler pas angulaire / sampling / rayon pour que l'« aperçu » soit rapide sur réseau mobile, le « haute qualité » restant borné.
- **V1 = premier blocage** : poches re-visibles au-delà d'un obstacle non rendues (V2 multi-polygones).
