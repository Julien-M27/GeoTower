# Plan d'implémentation — Surveillance de zone (« Area Watch »)

> Document autonome destiné à une IA chargée d'implémenter la fonctionnalité.
> Projet : GeoTower (Android, Kotlin, Jetpack Compose, package `fr.geotower`).
> Lis intégralement ce plan avant d'écrire du code. Respecte les conventions du dépôt.

---

## 1. Objectif

Permettre à l'utilisateur de **surveiller une ou plusieurs zones géographiques** et d'être **notifié** quand quelque chose change dans la base ANFR sur cette zone :

- **Nouveau site** apparu (nouvel `id_anfr`) ;
- **Nouvelle bande** déployée sur un site existant (ex : 5G 3500 MHz / n78 ajoutée) ;
- **Nouvelle technologie** (ex : passage en 5G) ;
- **Mise en service / hors service** (changement de statut) ;
- **Site retiré**.

Public visé : passionnés de suivi de déploiement réseau (« la 5G de Free arrive dans ma commune »). C'est la fonctionnalité qui transforme l'app d'un explorateur passif en outil de veille active.

---

## 2. Décisions d'architecture (déjà tranchées — modifiables par le mainteneur)

### D1 — Source de données : **base ANFR locale** (offline-first), Live API en option
La détection se fait en comparant l'état **de la base ANFR locale** entre deux exécutions.

**Justification :**
- La base ANFR locale (`geotower_fr.db`) est la seule source toujours disponible et gratuite.
- Les endpoints temps réel (`liveApi.fr.*`) sont **désactivés par défaut** (`RemoteFeatureFlags`, tous à `false`) → on **ne doit pas** en dépendre pour le socle.
- La donnée ANFR ne change de toute façon qu'au rythme des mises à jour de la base (≈ hebdomadaire), ce qui correspond exactement à la granularité de « déploiement » qu'on veut détecter.

**Conséquence :** le vrai déclencheur d'un check est **la fin réussie d'un téléchargement de base ANFR** (voir D3). Un check périodique sert de filet de sécurité.

> Évolution possible (V2) : si `liveApi.fr.bbox` est activé, faire un check plus frais via l'API. Garder la logique de diff identique.

### D2 — Persistance : **base Room SÉPARÉE** `geotower_app_data.db`
⚠️ **POINT CRITIQUE.** La base ANFR (`geotower_fr.db`) est un fichier SQLite **pré-construit, téléchargé et remplacé en bloc** (`DatabaseDownloader.installValidatedDatabase()` renomme le fichier). Elle n'est **pas** gérée par des migrations Room.

- **NE JAMAIS** ajouter d'`@Entity` à `AppDatabase` / `geotower_fr.db` : le swap de fichier écraserait les tables et casserait `GeoTowerDatabaseValidator`.
- Les zones surveillées + snapshots vont dans une **nouvelle base Room app-managed** `geotower_app_data.db`, totalement isolée.

### D3 — Déclenchement : **hook post-téléchargement** + filet périodique
- **Principal :** à la fin d'un téléchargement/installation réussi dans `DatabaseDownloadWorker`, déclencher un run unique de `AreaWatchWorker`.
- **Filet :** un `PeriodicWorkRequest` (~24 h, flexible) au cas où l'utilisateur n'ouvre jamais l'app au bon moment / base déjà à jour.

### D4 — Forme d'une zone (V1) : **cercle** (centre + rayon)
Une zone = `centerLat`, `centerLon`, `radiusKm`, + filtre opérateur optionnel.
Création possible depuis : (a) la carte (« surveiller cette zone » = centre carte + rayon par défaut), (b) une recherche de commune (Nominatim, déjà intégré), (c) la position courante.

> V2 possible : zone = commune (`code_insee`) ou bbox dessinée. Le modèle de données prévoit déjà de quoi l'étendre.

### D5 — Événements notifiés : configurables, tous activés par défaut
L'utilisateur peut filtrer quels types d'événements déclenchent une notif (nouveau site / nouvelle bande / nouvelle techno / mise en service / retrait).

---

## 3. Référence du code existant (ancrages — vérifier avant de coder)

| Sujet | Fichier | Notes |
|---|---|---|
| Application / startup | `app/src/main/java/fr/geotower/GeoTowerApp.kt` | `onCreate()` init `RemoteFeatureFlags.loadCached`, `PreferenceProfileManager.install`. Y brancher le scheduling de secours. |
| Base ANFR (read-only) | `app/src/main/java/fr/geotower/data/db/AppDatabase.kt`, `GeoTowerDao.kt`, `GeoTowerDatabaseValidator.kt` | **Ne pas modifier le schéma.** |
| Swap de base | `app/src/main/java/fr/geotower/data/api/DatabaseDownloader.kt` | `installValidatedDatabase()` → point de hook D3. |
| Worker DB | `app/src/main/java/fr/geotower/data/workers/DatabaseDownloadWorker.kt` | Patron de `CoroutineWorker` + notif + `enqueueUniqueWork`. |
| Worker périodique (patron) | `app/src/main/java/fr/geotower/widget/AntennaWidgetWorker.kt` + `WidgetUpdateScheduler.kt` | Seul worker **périodique** existant (`PeriodicWorkRequestBuilder` + `enqueueUniquePeriodicWork`). À copier. |
| Scheduler chaîné (patron) | `app/src/main/java/fr/geotower/data/workers/UpdateCheckScheduler.kt` | Pattern « replanifie à heure fixe ». |
| IDs/centre notifs | `app/src/main/java/fr/geotower/data/workers/DownloadNotificationCenter.kt` | Y ajouter le channel + les IDs Area Watch. |
| Icônes notifs | `app/src/main/java/fr/geotower/utils/NotificationIconResources.kt` | `applyTo(builder, context)` obligatoire. |
| Repository données | `app/src/main/java/fr/geotower/data/AnfrRepository.kt` | `getAntennasInBox(latNorth, lonEast, latSouth, lonWest)`, `getNearest(lat, lon, limit)` → renvoient `List<LocalisationEntity>`. |
| Entité site + masques | `app/src/main/java/fr/geotower/data/models/OfflineEntities.kt`, `RadioMapModels.kt` | `LocalisationEntity(idAnfr, operateur, latitude, longitude, techMask, bandMask, statut, hasActive, …)` ; `RadioFilterMasks` (bits TECH_*/BAND_*) ; `FrequencyDetailsCodec.decode(...)`. |
| Feature flags | `app/src/main/java/fr/geotower/data/config/RemoteFeatureFlags.kt` | objets `Screens`, `Features`, `Workers`, `Platform`, `Limits` + map de défauts. |
| Préférences typées | `app/src/main/java/fr/geotower/utils/TypedPreferences.kt`, `AppConfig.kt` | Store `PreferenceStores.APP = "GeoTowerPrefs"` ; pattern `mutableStateOf` / `StateFlow`. |
| NavHost + deep links | `app/src/main/java/fr/geotower/MainActivity.kt` | Toutes les routes y sont déclarées ; deep links `geotower://…`. |
| Écran simple (patron) | `app/src/main/java/fr/geotower/ui/screens/help/HelpScreen.kt` | `Scaffold` + `GeoTowerBackTopBar` + `rememberSafeBackNavigation`. |
| Top bar | `app/src/main/java/fr/geotower/ui/components/GeoTowerBackTopBar.kt` | |
| Réglages | `app/src/main/java/fr/geotower/ui/screens/settings/SettingsScreen.kt` | `PreferenceActionCard(...)` pour une ligne qui navigue ; `GeoTowerSwitch` pour un toggle. |
| i18n | `app/src/main/res/values/strings.xml` + `values-fr/-en/-de/-es/-it/-pt` | **7 fichiers** à tenir synchronisés. |
| Permissions | `app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS` déjà présent. Pas de nouvelle permission requise. |

---

## 4. Modèle de données (nouveau)

Nouveau package suggéré : `fr.geotower.data.watch`.

### 4.1 Entités Room — `data/watch/WatchedZoneEntities.kt`

```kotlin
@Entity(tableName = "watched_zones")
data class WatchedZoneEntity(
    @PrimaryKey val zoneId: String,      // UUID généré à la création
    val name: String,                    // libellé utilisateur (ex: "Grenoble centre")
    val centerLat: Double,
    val centerLon: Double,
    val radiusKm: Double,
    val operatorFilter: String?,         // null = tous opérateurs ; sinon nom opérateur ANFR
    val eventMask: Int,                   // bits des types d'événements surveillés (voir 6.x)
    val createdAt: Long,                  // epoch millis (passer l'horloge en paramètre, cf. §12)
    val lastCheckedAt: Long?,
    val enabled: Boolean = true
)

// Snapshot du dernier état connu d'un site dans une zone (pour le diff)
@Entity(
    tableName = "watched_zone_site_snapshots",
    primaryKeys = ["zoneId", "idAnfr"],
    foreignKeys = [ForeignKey(
        entity = WatchedZoneEntity::class,
        parentColumns = ["zoneId"], childColumns = ["zoneId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("zoneId")]
)
data class WatchedZoneSiteSnapshot(
    val zoneId: String,
    val idAnfr: String,
    val operateur: String?,
    val lat: Double,
    val lon: Double,
    val techMask: Int,
    val bandMask: Int,
    val statut: String?,
    val hasActive: Int,
    val updatedAt: Long
)

// Journal d'événements détectés (alimente l'écran détail + dédup notifs)
@Entity(tableName = "watched_zone_events")
data class WatchedZoneEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zoneId: String,
    val idAnfr: String,
    val type: Int,            // NEW_SITE / NEW_BAND / NEW_TECH / IN_SERVICE / REMOVED (voir 6.x)
    val detail: String,       // texte lisible déjà localisé OU clé + args sérialisés
    val detectedAt: Long,
    val seen: Boolean = false
)
```

### 4.2 DAO — `data/watch/WatchedZoneDao.kt`
Méthodes minimales :
```kotlin
@Dao interface WatchedZoneDao {
    @Query("SELECT * FROM watched_zones ORDER BY createdAt DESC")
    fun observeZones(): Flow<List<WatchedZoneEntity>>
    @Query("SELECT * FROM watched_zones WHERE enabled = 1")
    suspend fun enabledZones(): List<WatchedZoneEntity>
    @Upsert suspend fun upsertZone(zone: WatchedZoneEntity)
    @Query("DELETE FROM watched_zones WHERE zoneId = :zoneId")
    suspend fun deleteZone(zoneId: String)

    @Query("SELECT * FROM watched_zone_site_snapshots WHERE zoneId = :zoneId")
    suspend fun snapshotFor(zoneId: String): List<WatchedZoneSiteSnapshot>
    @Upsert suspend fun upsertSnapshots(rows: List<WatchedZoneSiteSnapshot>)
    @Query("DELETE FROM watched_zone_site_snapshots WHERE zoneId = :zoneId AND idAnfr IN (:ids)")
    suspend fun deleteSnapshots(zoneId: String, ids: List<String>)

    @Insert suspend fun insertEvents(events: List<WatchedZoneEvent>)
    @Query("SELECT * FROM watched_zone_events WHERE zoneId = :zoneId ORDER BY detectedAt DESC LIMIT 200")
    fun observeEvents(zoneId: String): Flow<List<WatchedZoneEvent>>
    @Query("UPDATE watched_zone_events SET seen = 1 WHERE zoneId = :zoneId")
    suspend fun markSeen(zoneId: String)
}
```

### 4.3 Base Room séparée — `data/db/AppDataDatabase.kt`
```kotlin
@Database(
    entities = [WatchedZoneEntity::class, WatchedZoneSiteSnapshot::class, WatchedZoneEvent::class],
    version = 1, exportSchema = true
)
abstract class AppDataDatabase : RoomDatabase() {
    abstract fun watchedZoneDao(): WatchedZoneDao
    companion object {
        @Volatile private var INSTANCE: AppDataDatabase? = null
        fun get(context: Context): AppDataDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDataDatabase::class.java, "geotower_app_data.db"
            ).build().also { INSTANCE = it }
        }
    }
}
```
> Vérifier que `kapt`/`ksp` Room est déjà configuré dans `app/build.gradle.kts` (la base ANFR utilise Room → ça devrait l'être).

---

## 5. Repository de surveillance — `data/watch/AreaWatchRepository.kt`

Responsable de la logique métier (sans Android UI). Signatures cibles :
```kotlin
class AreaWatchRepository(
    private val dao: WatchedZoneDao,
    private val anfr: AnfrRepository
) {
    suspend fun addZone(name: String, lat: Double, lon: Double, radiusKm: Double,
                        operatorFilter: String?, eventMask: Int, nowMillis: Long): String
    suspend fun removeZone(zoneId: String)
    fun observeZones(): Flow<List<WatchedZoneEntity>>
    fun observeEvents(zoneId: String): Flow<List<WatchedZoneEvent>>

    /** Cœur : recalcule le diff de toutes les zones actives et journalise les événements.
     *  Retourne la liste des événements nouvellement détectés (pour notifier). */
    suspend fun runCheck(nowMillis: Long): List<WatchedZoneEvent>
}
```

### 5.1 Algorithme de `runCheck`
Pour **chaque** zone active :
1. Calculer la bbox englobant le cercle (centre ± rayon converti en degrés ; attention au cos(lat) pour la longitude).
2. `current = anfr.getAntennasInBox(north, east, south, west)` puis **filtrer par distance réelle** ≤ `radiusKm` (Haversine) et par `operatorFilter` si défini.
3. `previous = dao.snapshotFor(zoneId)` indexé par `idAnfr`.
4. **Diff** :
   - `idAnfr` présent dans `current` mais absent de `previous` → `NEW_SITE`.
   - `idAnfr` présent dans les deux :
     - `newBands = current.bandMask and previous.bandMask.inv()` → si ≠ 0 → `NEW_BAND` (décrire via `bandLabels(newBands)`).
     - `newTechs = current.techMask and previous.techMask.inv()` → si ≠ 0 → `NEW_TECH`.
     - `previous.hasActive == 0 && current.hasActive == 1` (ou changement `statut`) → `IN_SERVICE`.
   - `idAnfr` présent dans `previous` mais absent de `current` → `REMOVED`.
5. Ne produire un événement que si son **type est dans `zone.eventMask`**.
6. **Premier check d'une zone** (snapshot vide) : enregistrer le snapshot **sans** générer d'événements (sinon tout est « nouveau »). Marquer la zone comme initialisée via `lastCheckedAt`.
7. Écrire les événements (`insertEvents`), remplacer le snapshot (`upsertSnapshots` + suppression des `REMOVED`), mettre à jour `lastCheckedAt`.

### 5.2 Libellés bandes / technos
Réutiliser `RadioFilterMasks` (bits `BAND_*`, `TECH_*`) pour mapper un masque → liste de libellés courts (« 5G 3500 », « 4G 700 »…). Centraliser dans une fonction `bandLabels(mask: Int): List<String>` / `techLabels(mask: Int)`. Vérifier la sémantique exacte des bits dans `RadioMapModels.kt` avant de figer les libellés.

> ⚠️ **À confirmer dans le DAO** : granularité d'`idAnfr` vs opérateur. Si une même ligne `LocalisationEntity` agrège plusieurs opérateurs, la clé de diff reste `idAnfr` ; si c'est `(idAnfr, operateur)`, adapter la clé primaire du snapshot. Vérifier `GeoTowerDao` (requêtes `getAntennasInBox*`) avant d'implémenter §4.1.

---

## 6. Worker + Scheduler

### 6.1 Types d'événements (constantes)
```kotlin
object AreaWatchEventType {
    const val NEW_SITE  = 1 shl 0
    const val NEW_BAND  = 1 shl 1
    const val NEW_TECH  = 1 shl 2
    const val IN_SERVICE= 1 shl 3
    const val REMOVED   = 1 shl 4
    const val ALL = NEW_SITE or NEW_BAND or NEW_TECH or IN_SERVICE or REMOVED
}
```

### 6.2 `data/workers/AreaWatchWorker.kt`
- `class AreaWatchWorker(ctx, params) : CoroutineWorker(ctx, params)`.
- `doWork()` :
  1. Si `!RemoteFeatureFlags.isWorkerEnabled(Workers.AREA_WATCH)` → `Result.success()`.
  2. Si aucune zone active → `Result.success()`.
  3. `val events = repo.runCheck(nowMillis = System.currentTimeMillis())`.
  4. Si `events` non vide **et** `RemoteFeatureFlags.isPlatformEnabled(Platform.NOTIFICATIONS)` → notifier (voir §7), en **regroupant par zone**.
  5. `Result.success()` ; `Result.retry()` seulement sur erreur réseau transitoire (le socle offline ne devrait pas en avoir).
- Contraintes : aucune contrainte réseau pour le socle offline (mettre `NetworkType.CONNECTED` uniquement si Live API activée).

### 6.3 `data/workers/AreaWatchScheduler.kt`
- `UNIQUE_PERIODIC = "area_watch_periodic"`, `UNIQUE_ONESHOT = "area_watch_oneshot"`.
- `fun runNow(context)` → `enqueueUniqueWork(UNIQUE_ONESHOT, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AreaWatchWorker>().build())`.
- `fun reconcilePeriodic(context)` → si flag worker actif **et** au moins une zone : `enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<AreaWatchWorker>(24, TimeUnit.HOURS).build())` ; sinon `cancelUniqueWork`.
- Copier le patron de `WidgetUpdateScheduler` / `UpdateCheckScheduler`.

### 6.4 Branchements
- **Hook D3** : dans `DatabaseDownloadWorker` (après installation réussie d'une nouvelle version) → `AreaWatchScheduler.runNow(applicationContext)`. Un seul point d'ancrage, idéalement juste après le succès d'`installValidatedDatabase`.
- **Startup** : dans `GeoTowerApp.onCreate()` (ou à la première ouverture d'écran) → `AreaWatchScheduler.reconcilePeriodic(this)`.
- **CRUD zone** : après `addZone`/`removeZone`, appeler `reconcilePeriodic` (et `runNow` après un ajout pour initialiser le snapshot tout de suite — sans notif au 1er check, cf. §5.1.6).

---

## 7. Notifications

- **Channel** : `area_watch_channel`, `IMPORTANCE_DEFAULT`. Créer via le patron des workers ; centraliser l'ID dans `DownloadNotificationCenter.kt`.
- **IDs** : base `AREA_WATCH_NOTIFICATION_ID_BASE = 3000`. Une notif par zone = `3000 + zoneId.hashCode() and 0xFFFF` ; une **summary notification** (groupe) si plusieurs zones (`setGroup("area_watch")`).
- **Contenu** : titre = nom de la zone ; texte = résumé (« 2 nouveaux sites, 1 nouvelle bande 5G 3500 »). Utiliser `NotificationCompat.InboxStyle`/`BigTextStyle` pour le détail.
- **Icône** : `NotificationIconResources.applyTo(builder, context)` (obligatoire, gère les variantes d'icône).
- **Tap → deep link** : ouvrir l'écran détail de la zone : `geotower://area_watch/{zoneId}` (voir §8). `PendingIntent.FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, `requestCode` unique par zone.
- **`setAutoCancel(true)`**. Marquer les events `seen = true` à l'ouverture de l'écran.
- Respecter `RemoteFeatureFlags.isPlatformEnabled(Platform.NOTIFICATIONS)` ; ne notifier que si `POST_NOTIFICATIONS` accordée (API 33+).

---

## 8. UI & navigation

### 8.1 Écrans (package `ui/screens/areawatch/`)
- **`AreaWatchScreen.kt`** — liste des zones : nom, rayon, opérateur filtré, date du dernier check, badge « N nouveautés non vues ». Actions : ajouter, supprimer (swipe ou menu), activer/désactiver, ouvrir le détail. État vide explicite (`area_watch_empty`).
- **`AreaWatchDetailScreen.kt`** — journal d'événements d'une zone (liste `WatchedZoneEvent`), bouton « voir sur la carte » (`geotower://map?lat=..&lon=..&zoom=..`) et accès aux fiches sites concernées. Marque les events comme vus à l'ouverture.
- **`AreaWatchViewModel.kt`** — expose `observeZones()` / `observeEvents()` en `StateFlow`, actions `addZone/removeZone/toggle`.
- Suivre le patron `HelpScreen` : `Scaffold` + `GeoTowerBackTopBar` + `rememberSafeBackNavigation(navController, fallbackRoute = "home")` + `BackHandler`. Contenu enveloppé dans `Box(Modifier.padding(innerPadding))`.

### 8.2 Création depuis la carte
Ajouter dans `MapScreen` une action « Surveiller cette zone » (bouton/overlay ou entrée de menu carte) : pré-remplit centre = centre de la carte courant, rayon par défaut (ex 5 km), ouvre une bottom-sheet de confirmation (nom + rayon + opérateur + types d'événements) → `addZone(...)`.
> Réutiliser la recherche **Nominatim** déjà intégrée pour proposer « surveiller la commune X ».

### 8.3 Routes (dans `MainActivity.kt`, NavHost)
```kotlin
composable(
    route = "area_watch",
    deepLinks = listOf(navDeepLink { uriPattern = "geotower://area_watch" })
) { Box(Modifier.padding(innerPadding)) {
    if (featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.AREA_WATCH))
        AreaWatchScreen(navController)
    else DisabledFeatureRoute(navController, txtUnavailable)
} }

composable(
    route = "area_watch/{zoneId}",
    arguments = listOf(navArgument("zoneId") { type = NavType.StringType }),
    deepLinks = listOf(navDeepLink { uriPattern = "geotower://area_watch/{zoneId}" })
) { back -> Box(Modifier.padding(innerPadding)) {
    AreaWatchDetailScreen(navController, back.arguments?.getString("zoneId").orEmpty())
} }
```
Respecter le pattern `DisabledFeatureRoute` utilisé pour les autres écrans gated.

### 8.4 Réglages
Dans `SettingsScreen.kt`, **section « Préférences »**, ajouter un `PreferenceActionCard` :
```kotlin
PreferenceActionCard(
    title = stringResource(R.string.settings_area_watch_title),
    desc  = stringResource(R.string.settings_area_watch_desc),
    onClick = { navController.navigate("area_watch") { launchSingleTop = true } },
    icon = Icons.Outlined.NotificationsActive, /* + params shape/border/bubbleColor/useOneUi/safeClick */
)
```

---

## 9. Feature flags — `data/config/RemoteFeatureFlags.kt`

Ajouter et **renseigner les défauts** dans la map de configuration par défaut :
```kotlin
// Screens
const val AREA_WATCH = "areaWatch"                 // défaut: true
// Features
const val AREA_WATCH = "areaWatch"                 // défaut: true   (master fonctionnel)
// Workers
const val AREA_WATCH = "areaWatch"                 // défaut: true
// Limits
const val AREA_WATCH_MAX_ZONES = "areaWatchMaxZones"               // défaut: 10
const val AREA_WATCH_MAX_RADIUS_KM = "areaWatchMaxRadiusKm"        // défaut: 25
const val AREA_WATCH_PERIODIC_HOURS = "areaWatchPeriodicHours"     // défaut: 24
```
> Respecter la convention de nommage existante (`liveApi.fr.bbox`, etc.). Vérifier les noms exacts des objets (`Screens`/`Features`/`Workers`/`Limits`) et la map de défauts avant d'ajouter.

---

## 10. i18n — 7 fichiers

Ajouter les clés dans **`values/strings.xml` (base/en)**, **`values-fr`**, **`values-en`**, **`values-de`**, **`values-es`**, **`values-it`**, **`values-pt`**. Garder les 7 strictement synchronisés (mêmes clés). Penser à `docs/i18n/` si ce dossier sert de miroir.

Clés minimales :
```
area_watch_title, settings_area_watch_title, settings_area_watch_desc,
area_watch_empty, area_watch_add, area_watch_delete, area_watch_radius_km,
area_watch_operator_all, area_watch_last_check, area_watch_new_badge,
area_watch_event_new_site, area_watch_event_new_band, area_watch_event_new_tech,
area_watch_event_in_service, area_watch_event_removed,
area_watch_notif_channel_name, area_watch_notif_title, area_watch_notif_summary,
area_watch_create_from_map, area_watch_create_from_commune
```
Pour les notifs/événements avec valeurs dynamiques, utiliser des placeholders (`%1$s`, `%1$d`) et éventuellement des `plurals` (cf. `help_section_count`).

---

## 11. Permissions
Aucune nouvelle permission. `POST_NOTIFICATIONS` est déjà déclarée ; gérer le **runtime request** (API 33+) si l'app ne le fait pas déjà au moment d'activer la surveillance. La localisation n'est **pas** requise pour le check (zones à coordonnées fixes) ; elle ne sert qu'à pré-remplir « ma position » lors de la création (réutiliser le fournisseur de localisation existant, ne rien ajouter).

---

## 12. Contraintes techniques du dépôt à respecter
- **Horloge** : passer le temps en paramètre (`nowMillis: Long`) aux fonctions de domaine pour rester testable ; n'appeler `System.currentTimeMillis()` que dans le worker / couche Android.
- **Coroutines** : I/O sur `Dispatchers.IO`. Pas de requêtes DB sur le main thread.
- **`runCatching`/logs** : envelopper les accès notifs/IO comme le font les workers existants ; logger via le `AppLogger` du projet.
- **Idempotence** : `runCheck` doit pouvoir tourner plusieurs fois sans dupliquer d'événements (le snapshot mis à jour à la fin garantit ça).
- **Ne pas dépendre** de la Live API (flags `false` par défaut).
- **Ne pas modifier** `geotower_fr.db` / `AppDatabase` / `GeoTowerDatabaseValidator`.

---

## 13. Découpage en lots livrables (ordre de dépendance)

### Lot 1 — Couche données + logique de diff (sans UI)
- [ ] `AppDataDatabase` + entités + DAO (§4) ; vérifier la config Room dans `build.gradle.kts`.
- [ ] `AreaWatchRepository.runCheck` + helpers bbox/Haversine/`bandLabels` (§5).
- [ ] **Tests unitaires** du diff : snapshot A→B couvrant chaque type d'événement + cas « 1er check sans notif ».
- **Done** : tests verts ; aucun changement à la base ANFR.

### Lot 2 — Worker + scheduler + notifications
- [ ] `AreaWatchWorker` + `AreaWatchScheduler` (§6) ; channel + IDs dans `DownloadNotificationCenter` (§7).
- [ ] Hook dans `DatabaseDownloadWorker` + `reconcilePeriodic` dans `GeoTowerApp`.
- [ ] Flags `Workers.AREA_WATCH`, `Platform.NOTIFICATIONS` respectés.
- **Done** : un run manuel sur une zone de test produit une notif cliquable ouvrant le deep link.

### Lot 3 — UI gestion + navigation + réglages + flags
- [ ] `AreaWatchScreen` + `AreaWatchViewModel` ; routes `area_watch` / `area_watch/{zoneId}` (§8.3) ; entrée Réglages (§8.4).
- [ ] Flags `Screens.AREA_WATCH`, `Features.AREA_WATCH`, `Limits.*` (§9).
- [ ] i18n des clés UI (§10).
- **Done** : on peut créer/lister/supprimer une zone depuis l'app ; écran gated par flag.

### Lot 4 — Création depuis la carte + détail + finitions
- [ ] `AreaWatchDetailScreen` (journal d'événements, « voir sur la carte »).
- [ ] Action « Surveiller cette zone » dans `MapScreen` + création depuis commune (Nominatim).
- [ ] i18n complet (7 langues) + relecture libellés FR.
- **Done** : parcours complet carte → zone → notif → détail fonctionnel.

---

## 14. Critères d'acceptation (global)
1. Créer une zone (cercle) depuis la carte et depuis l'écran de gestion.
2. Au **premier** check : snapshot enregistré, **aucune** notif.
3. Après modification simulée de la base (nouveau site / nouvelle bande dans la zone) : **une** notif pertinente, cliquable, ouvrant le détail de la zone.
4. Aucun doublon d'événement si le worker tourne deux fois de suite.
5. Désactiver le flag `Workers.areaWatch` stoppe toute surveillance ; désactiver `Screens.areaWatch` masque l'écran.
6. La base ANFR reste téléchargeable/remplaçable sans impact sur les zones (bases séparées).
7. Strings présentes dans les 7 locales ; pas de clé manquante.

---

## 15. Points à confirmer par l'implémenteur avant de coder
- **Granularité `idAnfr` vs opérateur** dans `GeoTowerDao.getAntennasInBox*` → fixe la clé du snapshot (§5.2).
- **Sémantique exacte des bits** `BAND_*` / `TECH_*` dans `RadioMapModels.kt` → libellés corrects.
- **Config Room** (kapt/ksp) déjà active dans `app/build.gradle.kts`.
- **Noms exacts** des objets de flags et de la map de défauts dans `RemoteFeatureFlags.kt`.
- **Point d'insertion exact** du hook dans `DatabaseDownloadWorker` (après succès d'install, pas après simple download).
