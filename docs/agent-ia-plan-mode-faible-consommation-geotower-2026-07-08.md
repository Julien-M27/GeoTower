# Plan / Audit — Mode « Faible consommation » GeoTower

_Date : 2026-07-08 — Cible : réduire batterie / CPU / GPU / réseau **sans dégrader la fluidité**._

---

## 0. Constat central (à lire en premier)

L'audit du code (localisation, carte/rendu, réseau/données, UI Compose, calculs lourds, infra prefs) fait ressortir **une bonne nouvelle** :

> **Une grande partie de la consommation vient de travail redondant qui provoque _aussi_ du jank.**
> Couper ce travail rend l'app **à la fois** plus économe **et** plus fluide. « Faible conso » et « fluide » ne s'opposent donc pas ici — ils vont souvent dans le même sens.

Concrètement, il faut séparer deux familles de gains :

| Famille | Nature | Faut-il un toggle ? |
|---|---|---|
| **A. Perf « gratuite »** | Corrige du gaspillage pur (thread bloqué, redraw inutile, recalcul par frame). Aucune perte de fonctionnalité. | ❌ Non — à activer **pour tout le monde, tout le temps**. |
| **B. Compromis « éco »** | Baisse volontairement une qualité de service (fréquence GPS, densité de rendu, résolution photo) en échange d'économies. | ✅ Oui — piloté par le **mode faible consommation**. |

Ce plan traite les deux, mais **commence par la famille A** : ce sont les meilleurs rapports gain/risque et ils bénéficient à 100 % des utilisateurs.

---

## 1. Principe de conception du mode

### Ce que le mode fait
Un **interrupteur global unique** (`low_power_mode`) qui, quand il est actif, bascule une série de curseurs vers leurs valeurs économes : GPS moins fréquent, carte allégée, réseau mis en cache plus agressivement, animations figées.

### Ce que le mode **ne** fait **pas**
- ❌ Il ne rouvre **pas** la carte « exemption d'optimisation batterie » — elle a été **retirée volontairement le 2026-06-27** (jugée inutile). Les helpers `LiveTrackingController.isIgnoringBatteryOptimizations()` / `requestIgnoreBatteryOptimizations()` restent en dormance et **ne doivent pas être ré-exposés** par ce mode.
- ❌ Il ne casse aucune fonctionnalité : tout reste accessible, juste moins gourmand.
- ❌ Il ne touche pas au comportement de survie du service live (START_STICKY + onResume, cf. mémoire projet) — on ne modifie que les **fréquences/priorités**, pas l'architecture du service.

### Activation (2 chemins)
1. **Manuel** : toggle dans Réglages → *Préférences* (persisté).
2. **Auto « suivre le système »** (optionnel, recommandé) : s'aligner sur le mode économie d'énergie d'Android via `PowerManager.isPowerSaveMode()` + un `BroadcastReceiver` sur `ACTION_POWER_SAVE_MODE_CHANGED`. Aujourd'hui **absent** du projet (aucun `isPowerSaveMode`, aucun `BatteryManager`).

---

## 2. Architecture technique du branchement

### 2.1 Le point d'ancrage existe déjà : `AppConfig`
Le projet n'a **pas** de DataStore ni de ViewModel de settings partagé. Tout réglage global passe par :
- `object AppConfig` (`app/src/main/java/fr/geotower/utils/AppConfig.kt:9`) — des `mutableStateOf` observés directement par Compose.
- Persistance `SharedPreferences` store `GeoTowerPrefs` (`utils/TypedPreferences.kt:44`).
- Chargement au démarrage via `AppConfig.loadSavedFilters(prefs)` (`AppConfig.kt:286`), appelé depuis `MainActivity.onCreate` (`MainActivity.kt:470`).

**Patron à copier à l'identique** : `enableUpdateNotifications` (état `AppConfig.kt:49` → load `AppConfig.kt:289` → UI `SettingsScreen.kt:2180`).

### 2.2 Ne pas disséminer des `if (lowPowerMode)` partout → un objet `PowerProfile`
Beaucoup des curseurs à piloter sont aujourd'hui des **constantes littérales** dispersées (`MapScreen.kt:203-218`, `MainActivity.kt:352-359`, défauts `TypedPreferences.kt`). Plutôt que d'ajouter des `if` un peu partout, centraliser dans **une seule source de vérité** qui expose des valeurs sémantiques :

```kotlin
// nouveau : utils/PowerProfile.kt
object PowerProfile {
    // Vérité combinée : toggle manuel OU (suivre-système ET système en éco)
    val isLowPower: Boolean
        get() = AppConfig.lowPowerMode.value ||
                (AppConfig.lowPowerFollowSystem.value && SystemPower.isSaveMode)

    // --- Curseurs dérivés (lus par les call sites) ---
    val mapReloadDebounceMs   get() = if (isLowPower) 450L else 180L
    val mapMarkerCap          get() = if (isLowPower) 2000 else 6000
    val mapCompassRotation    get() = !isLowPower            // rotation carte on/off
    val mapCompassIntervalMs  get() = if (isLowPower) 250L else 80L
    val mapHardwareLayer      get() = !isLowPower
    val mapUseNetworkTiles    get() = !isLowPower            // → setUseDataConnection
    val drawAzimuthCones      get() = if (isLowPower) false else AppConfig.showAzimuthsCone
    val coveragePointDays     get() = if (isLowPower) 90 else 365
    val sensorDelayCompass    get() = if (isLowPower) SENSOR_DELAY_UI else SENSOR_DELAY_GAME
    val uiAnimationsRich      get() = !isLowPower            // LoadingIndicator, fading edges…
    // … etc.
}
```

Les call sites lisent `PowerProfile.mapReloadDebounceMs` au lieu de la constante brute. **Avantage** : un seul fichier à faire évoluer, testable, et on peut plus tard introduire des presets/niveaux sans toucher aux call sites.

Pour le **code non-Compose** (services, workers) qui ne peut pas observer `AppConfig` : lire directement `prefs.getBoolean("low_power_mode", false)` — exactement comme `LiveTrackingController.startOnAppLaunchIfEnabled` lit déjà ses prefs (`LiveTrackingController.kt:49`).

### 2.3 Détection du mode système (pour l'activation auto)
```kotlin
// nouveau : utils/SystemPower.kt
object SystemPower {
    @Volatile var isSaveMode = false ; private set
    fun init(ctx: Context) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        isSaveMode = pm.isPowerSaveMode
        ctx.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { isSaveMode = pm.isPowerSaveMode }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    }
}
```
Init depuis `GeoTowerApp.onCreate` (`GeoTowerApp.kt:17`).

---

## 3. Catalogue des leviers

Légende — **Gain** : ●●● fort / ●● moyen / ● faible. **Effort** : S / M / L. **UX** : ✅ améliore la fluidité · ➖ neutre · ⚠️ compromis (léger recul de service).

### 3.A — Perf « gratuite » (à activer pour tous, sans toggle)

| # | Levier | Ressource | Gain | Effort | UX | Ancrage |
|---|---|---|---|---|---|---|
| A1 | **Couverture théorique : sortir le CPU du thread Main** (`withContext(Dispatchers.Default)` autour de grille + index + `solveSector`). Aujourd'hui tout tourne sur Main → jank/ANR. | 🔥 | ●●● | M | ✅ | `TheoreticalCoverageScreen.kt:189`, `CoverageEngine.kt:23`, `CoverageComputer.kt:11` |
| A2 | **Overlay couverture : précalculer les contours hors `draw()`**. `sector.outline()` (tri + `destinationPoint` par rayon) est recalculé **à chaque frame**. Le figer dans `setCoverage`, ne garder que `toPixels` dans `draw`. | 🎮🔥 | ●●● | M | ✅ | `TheoreticalCoverageOverlay.kt:49-94`, `CoverageModels.kt:86` |
| A3 | **Partage image couverture sur IO**. `mv.draw()` + `compress(PNG,95)` + I/O **synchrones sur Main** → gèle l'UI. Capturer le bitmap sur Main, compresser/écrire sur `Dispatchers.IO`. | 🔥 | ●● | S | ✅ | `TheoreticalCoverageScreen.kt:213-246` |
| A4 | **Profil altimétrique : réutiliser `BuildingIndex`**. Surélévation en O(N×M) (2000 pts × 5000 bâtiments ≈ 10 M tests point-in-polygon) car elle re-filtre tous les bâtiments au lieu d'utiliser l'index spatial déjà existant. | 🔥 | ●●● | M | ✅ | `ElevationProfileApi.kt:77-88`, `CoverageModels.kt:143` |
| A5 | **Cône compas carte : throttler comme référence**. La carte se redessine ~12×/s dès que le tel bouge (rotation compas) → tous les overlays (cônes compris) redessinés. Même hors mode éco, monter légèrement le seuil d'angle suffit à couper les redraws inutiles. | 🎮⚡ | ●● | S | ✅ | `MapScreen.kt:1497-1503`, `4419` |
| A6 | **`SignalQuestCoverageOverlay` : culling viewport**. Boucle sur jusqu'à 5000 points en dessinant un cercle chacun **sans test d'écran**, à chaque frame. Ajouter un skip si hors viewport. | 🎮 | ●● | S | ✅ | `MapScreen.kt:4803`, `MapViewModel.kt:610` |
| A7 | **Ne pas précharger les pannes (`hs`) si le calque est off**. `fetchSitesHs()` (GeoJSON national) part **inconditionnellement à l'init** de la carte. | 📶 | ●● | S | ➖ | `MapViewModel.kt:377-394` |
| A8 | **`CompassScreen` : `key` + tri hors composition**. `LazyColumn { items(list.sortedBy{…}) }` sans clé, tri exécuté dans la composition. | 🔥 | ● | S | ✅ | `CompassScreen.kt:580` |
| A9 | Nettoyer le `pulsePaint` mort (effet radar retiré, objet encore alloué). | 🧠 | ● | S | ➖ | `MapScreen.kt:4390` |

> **A1→A4 sont les plus rentables du plan** : ils suppriment le jank de la couverture théorique et du profil altimétrique, qui sont les traitements les plus lourds de l'app.

### 3.B — Batterie / localisation (mode éco)

| # | Levier | Gain | Effort | UX | Ancrage |
|---|---|---|---|---|---|
| B1 | **GPS live → `PRIORITY_BALANCED_POWER_ACCURACY`** (défaut actuel `HIGH_ACCURACY`). L'option existe déjà (`PRIORITY_LOW_POWER`). | ●●● | S | ⚠️ | `TypedPreferences.kt:453`, `LiveTrackingService.kt:225` |
| B2 | **Intervalle GPS live 5 s → 15-20 s** en éco (options déjà présentes 5/10/15/20). | ●●● | S | ⚠️ | `TypedPreferences.kt:427`, `LiveTrackingService.kt:219` |
| B3 | **Ajouter `setMinUpdateDistanceMeters`** au `LocationRequest` (absent → le GPS refixe à l'arrêt inutilement). | ●● | S | ➖ | `LiveTrackingService.kt:193` |
| B4 | **Provider osmdroid carte : fixer `setLocationUpdateMinTime/MinDistance`**. Actuellement `0 ms / 0 m` = fréquence GPS **maximale** non bridée dès que la carte est ouverte. | ●●● | S | ➖ | `MapScreen.kt:2591` |
| B5 | **Boussole `SENSOR_DELAY_GAME` (~50 Hz) → `SENSOR_DELAY_UI`**. Plus gros driver CPU continu de l'écran boussole ; la carte le fait déjà. | ●●● | S | ➖ | `CompassScreen.kt:269` |
| B6 | **LocationManager écrans détail 1 s / 1 m → 3-5 s / 10 m**. | ●● | S | ⚠️ | `SiteDetailScreen.kt:821`, `SupportDetailScreen.kt:411` |
| B7 | **Widget : GPS `HIGH_ACCURACY` toutes 60 min → `BALANCED`** et/ou période allongée. | ●● | S | ⚠️ | `AntennaWidgetWorker.kt:117`, `TypedPreferences.kt:407` |
| B8 | **Notif live : ne pas télécharger la photo du site** en éco (réseau + décodage bitmap à chaque site). | ●● | S | ⚠️ | `LiveTrackingService.kt:349-360` |
| B9 | **TTL refresh pannes live 15 min → 60 min** en éco. | ● | S | ➖ | `LiveTrackingService.kt:1282` |

> **Déjà bien fait, à préserver** : throttle CPU 30 s/15 m du traitement live, notif silencieuse (`setOnlyAlertOnce`), aucun AlarmManager, aucun wakelock manuel, listeners capteurs/GPS tous libérés (`onDispose`/`ON_PAUSE`).

### 3.C — Carte / GPU (mode éco)

| # | Levier | Gain | Effort | UX | Ancrage |
|---|---|---|---|---|---|
| C1 | **Couper la rotation compas de la carte** (repère fixe orienté nord). Supprime le redraw continu ~12×/s de **toute** la MapView + overlays. **Plus gros gain GPU/CPU au repos.** | ●●● | S | ⚠️ | `MapScreen.kt:2702-2708`, `1497`, `4419` |
| C2 | **`setUseDataConnection(false)`** → rendu 100 % cache/offline, **zéro réseau tuiles**. À proposer surtout si une carte hors-ligne est téléchargée. | ●●● | S | ⚠️ | config `MainActivity.kt:345`, provider `MapScreen.kt:2711` |
| C3 | **Désactiver les cônes d'azimut** (`drawArc` + 2 lignes de bord par marqueur, **par frame**) et **relever le seuil de zoom** des lobes (14 → 15/16). | ●●● | M | ⚠️ | `MapScreen.kt:4603`, `4635-4643` |
| C4 | **Réduire les plafonds de marqueurs** `.take(6000)` → ~2000 et `RADIO_MAP_MARKER_LIMIT` en éco. | ●● | S | ⚠️ | `MapScreen.kt:2146/2240`, `208-209` |
| C5 | **Repasser la MapView en `LAYER_TYPE_NONE`** en éco (aujourd'hui hardware layer forcé → texture ré-uploadée à chaque `invalidate`). À tester **après** C1. | ●● | S | ➖ | `MapScreen.kt:2566-2570` |
| C6 | **Débounces plus longs** : reload carte 180 → 450 ms, compas 80 → 250 ms ; durcir le seuil `isCloseTo`. Moins de requêtes DB/réseau au pan. | ●● | S | ⚠️ | `MapScreen.kt:210-214`, `364-374` |
| C7 | **Réduire le cache RAM tuiles** 160 → 48-64 (+ overshoot 80 → 24) en éco → moins de pression GC/RAM. | ● | S | ➖ | `MainActivity.kt:358-359` |
| C8 | **Marqueurs `ARGB_8888` → `RGB_565`/`ARGB_4444`** + taille 105dp → 72dp en éco (moitié mémoire, upload GPU allégé). | ● | M | ⚠️ | `MapUtils.kt:112-224` |
| C9 | **Éviter le filtre d'inversion couleur** (mode sombre IGN/OSM) en éco : préférer un provider déjà sombre (CartoCDN dark) sans `ColorMatrixColorFilter` per-pixel. | ● | S | ➖ | `MapScreen.kt:2779`, `MapUtils.kt:22` |

### 3.D — Réseau / données (mode éco)

| # | Levier | Gain | Effort | UX | Ancrage |
|---|---|---|---|---|---|
| D1 | **Interceptor `Cache-Control` en ligne**. Le cache disque 20 Mo n'est servi **qu'hors-ligne** ; forcer `max-age`/`stale-while-revalidate` sur les GET lents à changer (IGN élévation, WFS, feature-flags, outages, coverage) → réutilise le cache **même connecté**. Gros gain, faible risque. | ●●● | M | ➖ | `RetrofitClient.kt:50-83` |
| D2 | **`ImageLoader` Coil dédié + `.size()` + `thumbnailUrl`**. Les vignettes décodent l'image **pleine résolution** (pas de `.size()`), et `thumbnailUrl` (déjà dans le DTO) n'est pas utilisé. | ●●● | M | ➖ | `CommunityPhotosViewer.kt:1226`, `SignalQuestApi.kt:27` |
| D3 | **Réduire `days` (365 → 90) et le plafond 5000** des coverage points SignalQuest en éco. | ●● | S | ⚠️ | `MapViewModel.kt:610-611`, `272` |
| D4 | **Couverture théorique : forcer qualité « aperçu » + obstacles off** en éco → divise points, requêtes IGN et tests bâti. | ●● | S | ⚠️ | `TheoreticalCoverageScreen.kt:505-522` |
| D5 | **Ne pas forcer le refresh des feature-flags au démarrage** (s'appuyer sur le cache TTL 1 h). | ● | S | ➖ | `GeoTowerApp.kt:38`, `RemoteFeatureFlags.kt:541` |
| D6 | **Réduire le parallélisme** réseau (bbox fallback 8 → 2) et durcir backoff/`MAX_ATTEMPTS` en éco. | ● | S | ➖ | `AnfrRepository.kt:1443`, `TerrainFieldLoader.kt:167` |
| D7 | **Cache terrain couverture par site** (clé `idAnfr + rayon + pas + sampling + obstacles`). Prévu au plan `theoretical-coverage-plan.md` §11 mais **non implémenté** → chaque « Calculer » recharge tout le réseau. | ●● | M | ✅ | `CoverageEngine.kt`, `TerrainFieldLoader.kt` |

> **DB (Room, base préconstruite)** : threading OK (tout en `suspend`). Points lourds = sous-requêtes `EXISTS` corrélées des clusters (`GeoTowerDao.kt:762`), double tour d'enrichissement `enrichRadioBandMasksFromDetails` (`AnfrRepository.kt:1169`), `getNearest` en 7 requêtes séquentielles (`AnfrRepository.kt:917`). Optimisables mais **hors périmètre mode éco** (bénéfice permanent, pas un compromis) — à traiter comme dette perf séparée. ⚠️ Rappel mémoire projet : **interdit d'ajouter `@Index` Room** sur cette base (hash figé) — passer par `CREATE INDEX IF NOT EXISTS` dans `GeoTowerDatabaseIndexes`.

### 3.E — UI Compose (mode éco)

| # | Levier | Gain | Effort | UX | Ancrage |
|---|---|---|---|---|---|
| E1 | **Remplacer le `LoadingIndicator` Expressive** (morphing continu, ~20 emplacements) par un `CircularProgressIndicator` simple en éco. | ●● | M | ➖ | `SplashScreen.kt:113`, `MapScreen.kt:3968`, +18 sites |
| E2 | **Splash : réduire/supprimer le `delay(2200L)` artificiel** (délai cosmétique). | ●● | S | ✅ | `SplashScreen.kt:44-82` |
| E3 | **Désactiver les fondus de bords** (`FadingEdgeModifiers`) en éco : `graphicsLayer(compositingStrategy = Offscreen)` alloue un buffer hors-écran à chaque draw sur listes scrollables → principal coût GPU au scroll. | ●● | S | ➖ | `FadingEdgeModifiers.kt:24-96` |
| E4 | **Gater/allonger les boucles périodiques** : suivi carte `while(true) delay(1000)` (recalcul + `invalidate` 1 Hz même à vide) → 2-3 s ; radar boussole 3 s → 6-10 s. | ●● | S | ➖ | `MapScreen.kt:2446-2532`, `CompassScreen.kt:415` |
| E5 | **Figer les transitions d'écran** (`EnterTransition.None`) et les micro-anims (snap) en éco. | ● | S | ⚠️ | `MainActivity.kt:565` |
| E6 | Généraliser `derivedStateOf`/`remember(key)` sur les valeurs dérivées (seulement 3 usages dans tout le projet) — surtout `filteredAntennas` piloté par ~30 clés d'état. | ● | M | ✅ | `MapScreen.kt:1557-1636` |

> **Déjà sain** : aucun `rememberInfiniteTransition`, aucun `.blur()`/RenderEffect, quasi tout en `elevation(0.dp)`. Le scaling UI (`sizing.*`) ne coûte rien par frame — c'est aussi un bon endroit pour injecter le flag `lowPower` dans le style.

---

## 4. Ce qui sera désactivé ou altéré (effets concrets du mode)

Cette section liste **tout** ce que l'utilisateur pourrait remarquer. Deux principes d'abord :

1. **La famille A ne désactive rien.** Les correctifs de perf « gratuite » (A1→A9) ne changent aucun comportement visible : mêmes données, mêmes écrans, mêmes fonctions — juste plus fluides et plus rapides. Rien à annoncer à l'utilisateur.
2. **Aucune fonctionnalité n'est retirée par le mode éco.** Tout reste accessible (données antennes complètes, recherche, détails, partage, favoris, couverture…). Le mode ne fait que **baisser des fréquences, des résolutions et des densités d'affichage**, et il est **100 % réversible** : décocher le mode restaure instantanément tous les réglages (`PowerProfile` lit l'état en direct).

Échelle de sévérité : 🟢 imperceptible · 🟡 léger (visible si on cherche, sans gêne réelle) · 🟠 notable (change ce que tu vois ou l'info affichée).

### 4.1 Localisation & suivi live
| Ce qui change | Effet concret pour l'utilisateur | Sévérité |
|---|---|---|
| GPS live en `BALANCED` (au lieu de haute précision) | Position live un peu moins précise (quelques dizaines de mètres possibles au lieu de ~5 m) | 🟡 |
| Intervalle live 15-20 s (au lieu de 5 s) | En déplacement, le point et le « site le plus proche » se rafraîchissent par paliers plus espacés ; un changement de site est notifié avec quelques secondes de retard | 🟡 |
| Filtre de déplacement GPS (min-distance) | À l'arrêt, le GPS cesse de « refixer » inutilement — **aucun effet visible** | 🟢 |
| Bridage GPS de la carte (min-time/min-distance) | Le point bleu sur la carte se met à jour un peu moins souvent | 🟢 |
| Boussole en cadence réduite (`SENSOR_DELAY_UI`) | La boussole réagit à ~60 ms au lieu de ~20 ms — imperceptible à l'œil | 🟢 |
| Écrans détail : localisation 3-5 s / 10 m (au lieu de 1 s / 1 m) | Le compteur de distance vers un site se rafraîchit moins souvent | 🟡 |
| Widget en `BALANCED` | Le widget peut afficher une position un peu moins précise/fraîche | 🟡 |
| Notif live sans photo du site | La notification de suivi n'affiche plus la vignette du site — juste texte + icône | 🟡 |
| Rafraîchissement des pannes toutes les 60 min (au lieu de 15) | L'info « site en panne » du suivi live peut avoir jusqu'à 1 h de retard | 🟡 |

### 4.2 Carte
| Ce qui change | Effet concret pour l'utilisateur | Sévérité |
|---|---|---|
| Indicateur d'orientation figé (rotation compas coupée) | Le cône/la flèche « vers où je regarde » sur ta position ne pivote plus en continu (figé ou rafraîchi rarement). **La position reste affichée.** | 🟠 |
| Tuiles hors-ligne uniquement *(option)* | Les tuiles absentes du cache ne se téléchargent plus → zones grises/vides hors des secteurs déjà chargés. **À n'activer que si une carte hors-ligne est présente.** | 🟠 |
| Cônes d'azimut masqués + lobes au zoom plus élevé | Les cônes de direction / lobes des antennes ne s'affichent plus (ou seulement en zoomant davantage). Les points/pastilles d'antennes restent. | 🟠 |
| Plafond d'antennes 2000 (au lieu de 6000) | En vue large sur zone très dense, moins d'antennes affichées d'un coup ; les autres réapparaissent en zoomant | 🟠 |
| Débounce de rechargement plus long (450 ms) | Après un déplacement de carte, les antennes se rechargent avec un léger délai supplémentaire | 🟡 |
| Hardware layer désactivé | Réglage interne de rendu — aucun effet visible | 🟢 |
| Cache mémoire de tuiles réduit | Léger rechargement possible en re-balayant vite une zone déjà vue | 🟢 |
| Icônes marqueurs allégées (couleur `RGB_565` / taille) | Icônes d'antennes légèrement moins nettes ou plus petites | 🟡 |
| Rendu sombre sans filtre d'inversion | En thème sombre, les couleurs de la carte peuvent différer légèrement | 🟡 |

### 4.3 Photos & réseau
| Ce qui change | Effet concret pour l'utilisateur | Sévérité |
|---|---|---|
| Cache réseau servi même en ligne | Certaines données **stables** (relief IGN, bâtiments, flags) peuvent être un peu moins fraîches, servies depuis le cache. Position, sites et pannes **restent temps réel** (exclus du cache). | 🟢 |
| Vignettes photos en résolution réduite | Les miniatures des photos communautaires sont un peu moins nettes ; la pleine résolution reste disponible au clic | 🟡 |
| Pas de refresh forcé des feature-flags au lancement | Une config distante modifiée s'applique au prochain rafraîchissement (≤ 1 h) au lieu d'immédiatement | 🟢 |
| Moins de requêtes réseau en parallèle | Certains chargements (fallback bbox, couverture) sont un peu plus lents | 🟡 |

### 4.4 Couverture (théorique & communautaire)
| Ce qui change | Effet concret pour l'utilisateur | Sévérité |
|---|---|---|
| Couche couverture SignalQuest réduite (90 j, moins de points) | La couche de couverture communautaire affiche moins de points, sur 90 jours au lieu d'1 an | 🟠 |
| Couverture théorique en qualité « aperçu » + sans obstacles | Le calcul est plus grossier (rayon/échantillonnage réduits) et **ne tient plus compte des bâtiments**. Plus rapide mais moins fidèle. | 🟠 |

### 4.5 Interface
| Ce qui change | Effet concret pour l'utilisateur | Sévérité |
|---|---|---|
| Indicateurs de chargement simplifiés | Les animations de chargement « expressives » deviennent de simples cercles tournants | 🟢 |
| Splash accéléré | L'écran de démarrage disparaît plus vite — c'est un **gain**, pas une perte | 🟢 |
| Fondus de bord de liste désactivés | Plus de dégradé d'estompage en haut/bas des listes défilables — les bords sont nets | 🟢 |
| Boucles de rafraîchissement allongées | Le « suivi de l'antenne la plus proche » et le radar boussole se recalculent un peu moins souvent | 🟡 |
| Transitions d'écran figées | La navigation entre écrans devient instantanée (plus de fondu) | 🟡 |

### 4.6 Les altérations « notables » (🟠) — à décider consciemment
Ce sont les **seules** qui changent vraiment l'expérience ; tout le reste est léger ou invisible. Recommandation selon le niveau retenu (§7, décision 3) :

| Altération | Garder en niveau « conservateur » ? | Note |
|---|---|---|
| Indicateur d'orientation figé (C1) | ✅ garder actif | Gros gain, gêne faible : la position reste, seule la flèche d'orientation se fige |
| Tuiles hors-ligne (C2) | ❌ ne pas activer par défaut | N'activer **que si** une carte offline est détectée, sinon trous dans la carte |
| Cônes d'azimut masqués (C3) | ⚠️ optionnel | À garder si l'utilisateur exploite les lobes/directions |
| Plafond antennes 2000 (C4) | ✅ acceptable | Les antennes masquées réapparaissent au zoom |
| Couverture SignalQuest réduite (D3) | ✅ acceptable | 90 j reste représentatif |
| Couverture théorique dégradée (D4) | ⚠️ optionnel | Peut gêner un usage « expert » ponctuel |

**Piste** : les 🟠 les plus sensibles (C2, C3, D4) pourraient rester activables **indépendamment**, ou n'entrer en jeu qu'au niveau « agressif ». `PowerProfile` (§2.2) le permet sans toucher aux call sites.

### 4.7 Proposition de texte pour les Réglages
Résumé honnête et court à afficher sous le toggle :

> **Faible consommation** — Réduit l'usage de la batterie, des données mobiles et du processeur. La position se rafraîchit moins souvent, la carte s'allège (cônes de direction et animations réduits) et certaines images se chargent en qualité moindre. Aucune donnée ni fonction n'est supprimée ; désactivable à tout moment.

---

## 5. Plan d'implémentation par phases

### Phase 0 — Fondations _(prérequis)_ — ✅ RÉALISÉE (2026-07-08)
**Choix retenus** : **3 niveaux** (Normal/Éco/Éco+, `AppConfig.lowPowerLevel` 0/1/2), Éco = équilibré, Éco+ = agressif, **+ suivi de l'éco système** (`AppConfig.lowPowerFollowSystem` + `utils/SystemPower.kt` via `PowerManager.isPowerSaveMode`). Central : `utils/PowerProfile.kt` — `level` effectif = `max(manuel, système)` + curseurs sémantiques (gpsBalanced, mapReloadDebounceMs, drawAzimuthCones, mapMarkerCap, mapCompassRotation, mapTilesOfflineOnly, coveragePointDays, coverageQualityPreview, richAnimations, instantScreenTransitions, compassSensorDelay…). UI : sélecteur 3 niveaux + toggle « suivre système » dans `SectionPreferences` + entrée de recherche + 10 strings × 7 langues. **Compile OK.**
**⚠️ Reste à faire — le branchement** : les call sites ne lisent PAS encore `PowerProfile`. Tant que les familles B/C/D/E ne sont pas câblées aux curseurs, activer un niveau **ne change rien** au comportement. C'est la prochaine étape.

_Rappel spéc initiale (remplacée par la version 3-niveaux ci-dessus) :_
1. `AppConfig.lowPowerMode = mutableStateOf(false)` (+ `lowPowerFollowSystem` si activation auto) — `AppConfig.kt:49`.
2. Load dans `loadSavedFilters` — `AppConfig.kt:286`.
3. Nouveau `utils/PowerProfile.kt` (§2.2) — source de vérité des curseurs.
4. (option auto) Nouveau `utils/SystemPower.kt` + init `GeoTowerApp.onCreate`.
5. UI : `PreferenceSwitchCard` dans `SectionPreferences` (`SettingsScreen.kt:~2205`) + entrée d'index de recherche (`SettingsScreen.kt:~680`).
6. Strings `appstrings_low_power_title` / `_desc` dans `values/strings.xml` **+ les 6 `values-*`** (fr, en, es, de, it, pt).

À ce stade : le toggle existe et est persistant, mais ne pilote encore rien.

### Phase 1 — Perf « gratuite » (famille A) _(sans toggle, pour tous)_ — ✅ RÉALISÉE (2026-07-08)
**Faites & compilées** (2 builds `compileDebugKotlin --rerun-tasks` OK) :
- **A1** — `CoverageComputer.compute` enveloppé dans `Dispatchers.Default` : tout le CPU couverture (grille terrain + viewshed par antenne) sort du thread principal. Corrige **l'écran-outil ET l'overlay carte** en un point.
- **A2** — `TheoreticalCoverageOverlay` : contours géo précalculés dans `setCoverage`, `draw()` ne fait plus que projeter (fini le tri + trig par frame).
- **A3** — `shareCoverage` : capture de la MapView sur Main, puis composition + `compress(PNG)` + I/O sur `Dispatchers.IO`, chooser de retour sur Main (fini le gel à l'export).
- **A4** — `ElevationProfileApi` : surélévation bâti via `BuildingIndex` (O(N×M) → O(N), jusqu'à ~10 M tests point-in-polygon évités).
- **A6** — `SignalQuestCoverageOverlay` : culling viewport (ne rasterise que les points à l'écran parmi ~5000).
- **A8** — `CompassScreen` : tri mémoïsé (`remember`) + clés stables (`key = { it.id }`) sur la liste du cluster.
- **A9** — `MapScreen` : suppression du `Paint` mort `pulsePaint`.

**Reclassées mode éco** (non « gratuites » après lecture fine du code — ne pas les faire sans toggle) :
- **A5** — le throttle boussole existe DÉJÀ (seuil 0,75° + 80 ms) et le bloc `update` n'invalide pas inconditionnellement → aucun gain gratuit ; l'augmenter = compromis fluidité → mode éco (cf. levier C1).
- **A7** — les pannes HS (`fetchSitesHs`) ne sont **pas** un calque optionnel : elles colorent/badgent tous les marqueurs. Les omettre change le fonctionnel → mode éco.

Validation : compile OK sur les 7 correctifs ; pas de run end-to-end (perf sans changement de logique). `CoverageEngineTest` inchangé (le moteur n'est pas touché).

### Phase 2 — Batterie (famille B via PowerProfile)
B4, B5, B3 d'abord (neutres UX : bridages GPS carte, boussole, min-distance) → puis B1, B2, B6-B9 (compromis GPS pilotés par le mode).

### Phase 3 — Carte / GPU (famille C)
C1 (rotation compas — plus gros gain), puis C5 (hardware layer, à mesurer après C1), C3/C4 (cônes + plafonds), C6/C7, enfin C2 (offline tuiles) et C8/C9.

### Phase 4 — Réseau (famille D)
D1 (interceptor cache — meilleur ratio) et D2 (Coil) en tête ; D3-D7 ensuite.

### Phase 5 — UI (famille E)
E2 (splash) et E3 (fading edges) rapides ; E1 (LoadingIndicator) plus large ; E4-E6 pour finir.

> **Séquençage recommandé** : Phase 0 → Phase 1 (livrable de valeur immédiat, sans mode) → puis 2/3/4/5 en incréments, chacun mesurable indépendamment.

---

## 6. Comment mesurer (pour valider « moins gourmand ET fluide »)
- **Batterie / CPU / réseau** : Android Studio **Profiler** (Energy + CPU + Network) et `dumpsys batterystats` avant/après, scénario cadré (ex. carte ouverte 5 min immobile ; suivi live 10 min ; 1 calcul de couverture).
- **Fluidité** : `dumpsys gfxinfo fr.geotower framestats` (janky frames %) sur un pan/zoom scripté ; viser < 5 % de frames jankées.
- **Repère clé** : mesurer **carte immobile compas actif** avant/après C1 — c'est là que le gain « au repos » doit être le plus spectaculaire.

---

## 7. Décisions à trancher (avant de coder au-delà de la Phase 1)

1. **Un seul mode binaire, ou des niveaux/presets** (Équilibré / Éco / Éco+) ? → Le binaire suffit pour la demande ; `PowerProfile` permet d'ajouter des niveaux plus tard sans retoucher les call sites.
2. **Activation auto « suivre le mode éco système »** : oui/non ? (recommandé oui, faible coût — `SystemPower`).
3. **Agressivité par défaut** du mode : plutôt « conservateur » (surtout perf gratuite + bridages neutres B3/B4/B5 + carte C1/C6) ou « agressif » (inclut GPS BALANCED, offline tuiles, cônes off) ? → détail des effets visibles en **§4.6**.
4. **Portée** : livrer d'abord la **Phase 1 seule** (perf pour tous, sans UI) puis le mode ? ou tout en une fois ?

---

## Annexe — Fichiers-clés
- Prefs / état global : `utils/AppConfig.kt`, `utils/TypedPreferences.kt`
- UI réglages : `ui/screens/settings/SettingsScreen.kt` (`SectionPreferences` ~2205), `ui/components/GeoTowerSwitch.kt`
- Localisation : `services/LiveTrackingService.kt`, `services/LiveTrackingController.kt`, `AntennaWidgetWorker.kt`
- Carte / rendu : `ui/screens/map/MapScreen.kt`, `MapUtils.kt`, `MapViewModel.kt`, overlays couverture
- Réseau : `data/api/RetrofitClient.kt`, `ElevationProfileApi.kt`, `BdTopoBuildingsApi.kt`, `SignalQuestApi.kt`
- Calcul lourd : `coverage/*` (`CoverageEngine`, `TerrainFieldLoader`, `ViewshedSolver`, `CoverageModels`), `TheoreticalCoverageScreen.kt`, `ElevationProfileScreen.kt`
- Boussole / capteurs : `ui/screens/compass/CompassScreen.kt`
- Entrée app : `GeoTowerApp.kt`, `MainActivity.kt`
