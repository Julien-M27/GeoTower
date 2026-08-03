# 🔒 Audit de sécurité — GeoTower

**Application :** GeoTower (Android natif, Kotlin / Jetpack Compose) + backend Python (FastAPI)
**Version auditée :** 1.9.9.5.2 (`versionName`), commit `b1328a4`
**Date de l'audit :** 8 juin 2026
**Périmètre :** application Android complète (`app/`) + backend serveur (`docs/server/`)
**Méthodologie :** revue de code statique manuelle approfondie, organisée en 5 domaines audités en parallèle, avec vérification des sinks (lecture effective du code, pas de signalement sans confirmation).

---

## 1. Synthèse exécutive

GeoTower est une application **remarquablement bien durcie** pour son périmètre. Les fondamentaux de sécurité mobile sont en place et correctement implémentés : signature cryptographique des bases de données téléchargées (ECDSA P‑256), trafic 100 % chiffré (cleartext désactivé), absence totale d'injection SQL (Room paramétré partout), aucun secret en dur dans le dépôt, `PendingIntent` immuables, `FileProvider` non exporté et restreint, validation Android Auto correcte, et un backend Python bien protégé (proxy en liste blanche, jetons d'upload HMAC à usage unique, requêtes SQL paramétrées).

**Aucune vulnérabilité Critique ou Haute n'a été identifiée.**

Les constats sont majoritairement **Faibles** et **Informationnels**, avec **3 constats de gravité Moyenne** méritant une action :

| Priorité | Constat | Gravité | Domaine |
|----------|---------|---------|---------|
| 🥇 1 | Métadonnées GPS (EXIF) non supprimées par défaut lors de l'upload de photos | **Moyenne** | Vie privée |
| 🥈 2 | Dépendances Python non épinglées (CVE `python-multipart`/`Pillow` atteignables) | **Moyenne** | Backend / chaîne d'appro. |
| 🥉 3 | Endpoints `live-FR` / `download` sans limitation de débit ; attribution d'IP à durcir | **Moyenne** | Backend / DoS |
| 4 | Ouverture de la base par Room sans re‑validation complète (TOCTOU, défense en profondeur) | **Moyenne** | Intégrité données |

Le reste (12 constats Faibles/Info) relève du durcissement défensif et n'est pas exploitable dans un modèle de menace standard.

### Note de tendance

L'effort de sécurité existant est visible et de bonne qualité : présence d'un fichier de tests de sécurité côté serveur (`test_signalquest_proxy_security.py`), manifeste signé avec rotation de clé par `keyId`, contrôle d'intégrité multi‑étapes (signature → fraîcheur → liste blanche d'hôte → taille exacte → SHA‑256 → schéma SQLite → install atomique avec rollback). C'est un niveau de maturité supérieur à la moyenne des applications mobiles.

---

## 2. Échelle de gravité

| Niveau | Définition |
|--------|------------|
| 🔴 **Critique** | Exploitable à distance sans interaction, compromission totale. *Aucun constat.* |
| 🟠 **Haute** | Exploitable avec impact fort (RCE, fuite massive de données, contournement d'authentification). *Aucun constat.* |
| 🟡 **Moyenne** | Impact réel mais conditions/portée limitées, ou fuite de données sensibles unitaire. |
| 🔵 **Faible** | Impact mineur, nécessite des prérequis forts (root, accès local) ou durcissement. |
| ⚪ **Info** | Bonne pratique / observation, pas de vulnérabilité. |

---

## 3. Constats détaillés

### 3.1 — Vie privée & données utilisateur

#### 🟡 [PRIV‑01] Métadonnées GPS (EXIF) non supprimées par défaut lors de l'upload de photos
- **Gravité :** Moyenne
- **Fichiers :**
  - `app/src/main/java/fr/geotower/ui/screens/emitters/SignalQuestUploadScreen.kt:164` (`stripExifBeforeUpload` initialisé à `false`)
  - Override du défaut `=true` à `app/src/main/java/fr/geotower/MainActivity.kt:996`
  - Chaîne de traitement : `app/src/main/java/fr/geotower/data/upload/SignalQuestUploadQueue.kt:360-375`
- **Description :** Lorsqu'un utilisateur envoie une photo de site, l'option « supprimer EXIF » est **désactivée par défaut**. Dans ce cas, la JPEG d'origine peut être transmise telle quelle, et l'ensemble des tags EXIF — dont `TAG_GPS_LATITUDE` / `TAG_GPS_LONGITUDE` — est sérialisé dans un champ `exifMetadata` envoyé au serveur. Les photos d'antennes étant prises sur place, ces coordonnées correspondent typiquement à la **position physique réelle de l'utilisateur**.
- **Impact :** La localisation précise de capture est transmise à `api.geotower.fr` et potentiellement exposée via la galerie photo publique, sans que l'utilisateur en ait conscience.
- **Recommandation :** Mettre `stripExifBeforeUpload` à `true` par défaut (privacy‑by‑default), ou **toujours supprimer les tags GPS** quelle que soit l'option (en ne conservant que l'orientation), et faire de « conserver toutes les métadonnées » un choix explicite. **L'infrastructure de suppression existe déjà et est correcte** (`SignalQuestUploadQueue.kt:167-180`, avec une liste d'exclusion bien construite des tags non transférables) — seul le défaut est à corriger.

#### 🔵 [PRIV‑02] Localisation précise transmise via le mode de secours « Live API FR »
- **Gravité :** Faible
- **Fichiers :** `app/src/main/java/fr/geotower/data/api/LiveSitesApi.kt:13-21` (paramètres `lat`/`lon`) ; appelants `AnfrRepository.kt:494` et `:549`
- **Description :** Si la base ANFR locale est absente/invalide, l'app interroge `GET api/v2/live/fr/sites/nearby?lat=&lon=`, envoyant les coordonnées de l'utilisateur au serveur. C'est un fallback délibéré (commit `b1328a4`), protégé par feature flag. C'est le **seul** endroit où la localisation précise quitte l'appareil pour la cartographie.
- **Impact :** Le serveur apprend la position de l'utilisateur lorsque la base hors‑ligne n'est pas installée. Contredit la promesse « 100 % hors‑ligne ».
- **Recommandation :** Documenter dans une note de confidentialité ; envisager d'arrondir/dégrader la précision des coordonnées transmises (la recherche de proximité n'a pas besoin de la précision GPS maximale).

#### 🔵 [PRIV‑03] Coordonnées de la dernière vue carte stockées en clair
- **Gravité :** Faible / Info
- **Fichiers :** `MainActivity.kt` (clés `clicked_lat`, `clicked_lon`, `last_map_lat`, `last_map_lon`, `last_map_zoom` via `putFloat`, store `PreferenceStores.APP`)
- **Description :** Position caméra de la carte écrite en clair dans `MODE_PRIVATE`. Ce ne sont pas des fixes GPS, mais sur un localisateur d'antennes elles coïncident souvent avec le voisinage de l'utilisateur.
- **Impact :** Lisible uniquement avec root / compromission locale. Atténué par `allowBackup=false` (pas de fuite via sauvegarde cloud).
- **Recommandation :** Acceptable en l'état. Pour la défense en profondeur, migrer ce store vers `EncryptedSharedPreferences` (androidx.security‑crypto).

---

### 3.2 — Intégrité des données & réseau

#### 🟡 [NET‑01] L'ouverture de la base par Room ne rejoue pas la validation complète
- **Gravité :** Moyenne (défense en profondeur)
- **Fichiers :** `app/src/main/java/fr/geotower/data/db/AppDatabase.kt:55-59` ; `GeoTowerDatabaseValidator.kt:34-43`
- **Description :** `getDatabase()` conditionne l'ouverture à `getInstalledDatabaseFileStatus()`, qui vérifie seulement l'existence du fichier et `length() > 0` — il **ne rejoue pas** `validateDatabaseFile()` (PRAGMA integrity_check / schéma / métadonnées). La validation complète n'a lieu qu'au moment du téléchargement.
- **Impact :** Si un attaquant remplace le fichier sur disque **après** le téléchargement authentifié (appareil rooté/compromis, restauration de sauvegarde), il est ouvert sans re‑vérification. La garantie du manifeste signé ne porte qu'au moment de l'installation. Impact pratique limité : Room rejette quand même un schéma structurellement incorrect, et le répertoire `databases/` n'est pas accessible aux autres apps hors root.
- **Recommandation :** Sur le chemin d'ouverture Room, appeler la validation complète (`validateDatabaseFile()` / `getInstalledDatabaseStatus()`) plutôt que la simple vérification d'existence.

#### 🔵 [NET‑02] Fenêtre TOCTOU entre validation et installation/ouverture
- **Gravité :** Faible
- **Fichiers :** `DatabaseDownloader.kt:145-158`, `RadioDatabaseDownloader.kt:138-149`
- **Description :** Le fichier temporaire est validé, puis renommé séparément, puis ouvert plus tard par Room ; les octets ne sont pas « épinglés » (ex. via un descripteur de fichier maintenu ouvert). Exploitation nécessite root → risque réel faible.
- **Recommandation :** Acceptable compte tenu du sandbox applicatif ; si durcissement souhaité, re‑valider juste avant l'ouverture (recouvre NET‑01).

#### 🔵 [NET‑03] Clé publique de signature unique, codée en dur et non révocable côté client
- **Gravité :** Faible
- **Fichiers :** `app/build.gradle.kts:11-12` (`defaultManifestPublicKeys`, `geotower-prod-2026-01`) ; consommée à `DownloadManifestVerifier.kt:77`
- **Description :** La clé **publique** EC est embarquée dans `BuildConfig` — c'est le bon pattern (pinning de clé de vérification, une clé publique n'est pas un secret). Mais la rotation impose une nouvelle build, et une clé **privée** compromise reste de confiance sur les clients déjà installés jusqu'à mise à jour ; pas de mécanisme de révocation par `keyId`.
- **Recommandation :** Pré‑embarquer plusieurs `keyId` avant rotation (le code le supporte déjà via la map keyId→clé) et documenter le runbook de rotation.

#### 🔵 [NET‑04] Pas de protection anti‑rollback / anti‑rejeu (seule l'expiration est contrôlée)
- **Gravité :** Faible
- **Fichier :** `DownloadManifestVerifier.kt:60-62`
- **Description :** Le vérificateur rejette les manifestes expirés (`expiresAt < now`) mais pas un manifeste **plus ancien mais non expiré**, et ne compare pas `generatedAt` à la version installée. Un attaquant réseau peut donc rejouer un manifeste authentique antérieur (downgrade vers une base signée plus ancienne) dans la fenêtre de validité.
- **Impact :** Limité à servir des données obsolètes mais authentiques ; aucune injection de données forgées possible.
- **Recommandation :** Persister le `generatedAt` le plus élevé observé et rejeter tout manifeste antérieur à la base installée ; conserver des TTL `expiresAt` courts côté serveur.

#### ⚪ [NET‑05] Trust anchor ajouté (ISRG Root X1), ce n'est pas du certificate pinning
- **Gravité :** Info (par conception) — *voir aussi PRIV‑04 plus bas pour la recommandation pinning*
- **Fichier :** `app/src/main/res/xml/network_security_config.xml:4-7`
- **Description :** La config ajoute la racine Let's Encrypt à l'ensemble des CA système — ce **n'est pas** un pinning. Tout certificat émis par une CA valide pour `api.geotower.fr` est accepté. Les chemins critiques en intégrité (mises à jour de base) restent protégés par la signature du manifeste ; les flux upload photo / live‑sites (lat/lon) / Nominatim ne sont protégés que par TLS standard.
- **Recommandation :** Si le modèle de menace le justifie, ajouter un `CertificatePinner` OkHttp (avec pins de secours) pour `api.geotower.fr`, au moins sur les endpoints upload/live.

#### ⚪ [NET‑06] Code mort : lecteur d'info non signé `/db/info`
- **Gravité :** Info
- **Fichier :** `DatabaseDownloader.kt:206-223` (`readRemoteDatabaseInfo()`) et constantes `DB_URL`/`DB_VERSION_URL`
- **Description :** Le chemin actif utilise le manifeste **signé** (`readVerifiedDownloadManifest()`). Le parseur `/db/info` **non signé** semble être du code legacy inutilisé — piège latent s'il était re‑branché dans la décision de téléchargement.
- **Recommandation :** Supprimer `readRemoteDatabaseInfo()`, `isValidRemoteDatabaseInfo(JSONObject)` et les constantes d'URL inutilisées.

#### ⚪ [NET‑07] Gson en mode `setLenient()` pour les DTO réseau
- **Gravité :** Info
- **Fichier :** `RetrofitClient.kt:12-14`
- **Description :** Le Gson tolérant accepte du JSON malformé. Sans impact sur la vérification de signature (le manifeste est parsé via `JsonParser` direct, pas par ce Gson). Affaiblit légèrement la stricte validation des réponses serveur.
- **Recommandation :** Préférer un parsing strict pour les DTO réseau. Priorité basse.

---

### 3.3 — Composants Android, IPC & deeplinks

#### 🔵 [AND‑01] Permissions d'URI partagées persistées sans validation de type MIME
- **Gravité :** Faible
- **Fichiers :** `MainActivity.kt:148-155` (`persistSharedImageReadPermission`), site d'appel `:137-145`
- **Description :** Sur tout `ACTION_SEND`/`SEND_MULTIPLE`, chaque URI (`EXTRA_STREAM` et `clipData`) est passée à `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` **avant** toute vérification MIME. Une app malveillante peut déclarer `image/png` tout en joignant un `content://` arbitraire. L'app prend une permission **persistante** (durable) sur chaque URI partagée, même celles jamais réellement envoyées.
- **Impact :** Accumulation de droits de lecture persistants vers des fournisseurs de contenu tiers, maintenus longtemps après le partage, et contenu forgé ensuite décodé par le pipeline EXIF/bitmap. (Pas d'exfiltration directe : l'émetteur possédait déjà ces droits.)
- **Recommandation :** N'appeler `takePersistableUriPermission` que pour les URI réellement validées pour l'upload, après contrôle de `contentResolver.getType(uri)`. Une permission transitoire (déjà attachée à l'Intent) suffit pour construire le brouillon. Appeler `releasePersistableUriPermission` après upload/abandon.

#### ⚪ [AND‑02] Deeplinks `geotower://` navigués sans validation d'entrée (impact borné)
- **Gravité :** Info / Faible
- **Fichiers :** `MainActivity.kt:215-220`, `:499-509`, routes `:734-826`
- **Description :** Les intents `geotower://` sont transmis à `navController.handleDeepLink(...)` sans validation. Sinks tracés : les `{id}` atteignent des `@Query` Room paramétrés (**pas d'injection SQL**) ; `target_map` ne déclenche **aucun** téléchargement (seulement un scroll/expand UI, écriture fichier contrainte par `OfflineMapDownloadValidator.safeMapFile`) ; aucune valeur n'alimente un chemin de fichier ou une WebView.
- **Impact :** Pas d'open redirect, pas d'injection, pas d'action privilégiée. Au pire, forcer l'app au premier plan sur un écran arbitraire (phishing/UI redress mineur).
- **Recommandation :** Durcissement optionnel — borner `{id}` au format numérique attendu et mettre en liste blanche `section`/`target_map`.

#### ⚪ [AND‑03] Filtre d'intent `geotower://` sans `host` (surface élargie)
- **Gravité :** Info
- **Fichier :** `AndroidManifest.xml:84-89`
- **Description :** Le premier filtre `VIEW`/`BROWSABLE` déclare le scheme `geotower` sans host, matchant donc **tout** `geotower://...` en plus des filtres `support`/`site`. Seules les routes enregistrées résolvent réellement ; impact pratique faible.
- **Recommandation :** Supprimer le filtre sans host et ne garder que les filtres explicites par host, conformément à `docs/deeplinks-geotower.txt`.

---

### 3.4 — Stockage local, secrets, journalisation & crypto

#### 🔵 [STO‑01] `Log.w` non neutralisé/conditionné en build release
- **Gravité :** Faible
- **Fichiers :** `app/src/main/java/fr/geotower/utils/AppLogger.kt:20-24` ; `app/proguard-rules.pro`
- **Description :** `d()` et `i()` sont correctement encadrés par `if (BuildConfig.DEBUG)`, mais `w()` appelle toujours `Log.w` en release, et aucune règle `assumenosideeffects` ne retire `android.util.Log`. **Atténuation forte :** la revue des ~50 sites d'appel `AppLogger.w` montre des messages génériques en anglais, **sans** coordonnées, jetons, chemins ni contenu de requête.
- **Recommandation :** Conditionner `Log.w` derrière `BuildConfig.DEBUG`, ou ajouter à `proguard-rules.pro` :
  ```
  -assumenosideeffects class android.util.Log {
      public static int w(...); public static int d(...);
      public static int i(...); public static int v(...);
  }
  ```

> **✅ Note positive :** toute la journalisation passe par `AppLogger` (seuls appels directs à `android.util.Log` : à l'intérieur d'`AppLogger.kt`). Excellente discipline.

#### ⚪ [STO‑02] Aucun secret, aucune injection SQL, aucune crypto faible — *confirmé*
- **Gravité :** Info (résultat positif)
- **Détails vérifiés :**
  - **Injection SQL : aucune.** Toutes les requêtes DAO (`GeoTowerDao.kt`, lignes 43–1082) sont des `@Query` Room à paramètres liés ; les recherches `LIKE '%' || :query || '%'` utilisent un paramètre lié. Les `rawQuery` (`RadioRepository.kt`, validateurs) n'emploient que des `?` et des constantes entières contrôlées par l'app, avec `quoteIdentifier()` sur les noms de table d'une liste blanche.
  - **Secrets : aucun.** Pas d'API key/mot de passe/jeton dans `strings.xml`, `gradle.properties`, `local.properties`, `BuildConfig` ni source. Pas de clé **privée**/keystore/`.jks`/`.p12`/`signingConfig` dans le dépôt ni l'historique git. La clé EC en dur est **publique** (vérification de signature) — pattern correct. Le jeton d'upload SignalQuest est récupéré par upload et gardé en mémoire uniquement (jamais sur disque).
  - **Crypto : correcte.** `SHA256withECDSA` (vérification), `SHA‑256` (intégrité des téléchargements). Aucun MD5/SHA‑1/DES/ECB/IV en dur, aucun `java.util.Random` à usage sécurité, aucun `Cipher` (aucun secret stocké).
  - **Sauvegarde :** `allowBackup="false"`, `backup_rules.xml`/`data_extraction_rules.xml` vides ⇒ aucune fuite. Pas de WebView, pas de `setWebContentsDebuggingEnabled`, pas de `StrictMode`, pas d'`HttpLoggingInterceptor`.

---

### 3.5 — Permissions, services & build

#### 🔵 [PRM‑01] Téléchargements WorkManager autorisés sur réseau facturé (metered)
- **Gravité :** Faible (pas d'impact sécurité)
- **Fichiers :** `DatabaseDownloadWorker.kt:237-239`, `RadioDatabaseDownloadWorker`, `MapDownloadWorker`, `UpdateCheckScheduler.kt:66-70`
- **Description :** Les gros téléchargements (base ANFR, cartes) utilisent `NetworkType.CONNECTED` plutôt que `UNMETERED`. Initié par l'utilisateur, donc défendable, mais une base de plusieurs centaines de Mo en données mobiles/roaming peut surprendre.
- **Recommandation :** Envisager `UNMETERED` (ou une préférence « autoriser en données mobiles ») pour la base complète et les cartes hors‑ligne. Aucun abus de travail expédié détecté (`setExpedited` absent).

#### 🔵 [PRM‑02] `versionCode = 1` incohérent avec `versionName 1.9.9.5.2`
- **Gravité :** Faible (qualité de livraison)
- **Fichier :** `app/build.gradle.kts` (`versionCode = 1`)
- **Description :** Un `versionCode` de 1 pour une app en 1.9.x bloquera les mises à jour Play Store et est vraisemblablement une erreur de configuration.
- **Recommandation :** Corriger/vérifier le `versionCode` pour les vraies releases ; envisager `isShrinkResources`.

#### 🔵 [PRM‑03] README « 100 % hors‑ligne » inexact
- **Gravité :** Faible (documentation/transparence)
- **Fichier :** `README.md:5,15-17`
- **Description :** L'app contacte le réseau pour : téléchargements base/radio/cartes/manifeste et vérifs de MAJ (`api.geotower.fr`), **fallback live‑sites envoyant lat/lon** (PRIV‑02), upload + lecture photos communautaires (SignalQuest, CellularFR), recherche Nominatim (envoie la requête), profils d'élévation, tuiles de carte en ligne. Le cœur de navigation **est** hors‑ligne une fois la base installée.
- **Recommandation :** Reformuler en « offline‑first » et ajouter une section confidentialité listant les endpoints et ce que chacun transmet.

> **✅ Mitigations vérifiées (non vulnérables) :**
> - **Android Auto** (`GeoTowerCarAppService.kt:16-26`) : `HostValidator` restreint à l'allowlist Google réelle ; `ALLOW_ALL_HOSTS_VALIDATOR` limité à `BuildConfig.DEBUG`.
> - **`PendingIntent`** : tous en `FLAG_IMMUTABLE` et ciblant des composants explicites.
> - **Récepteurs widgets** : exportés uniquement pour `APPWIDGET_UPDATE` système ; aucune action sensible déclenchable par une app tierce.
> - **`FileProvider`** : `exported="false"`, restreint à `cache/images` et `cache/sq_camera` (`file_paths.xml`).
> - **`LiveTrackingService`** : `exported="false"`, opt‑in explicite (`enable_live_notifications` défaut `false`).
> - **`<queries>` tiers** (enb4g, sfrmap, cellularfr) : uniquement pour lancer l'app / ouvrir le Play Store — **pas d'exfiltration**.
> - **Aucun SDK analytics/télémétrie** (pas de Firebase/Crashlytics/ads).
> - **Pipeline EXIF/bitmap** robuste : taille source plafonnée (20 Mo), liste blanche MIME, bornes de décodage, OOM capturé, suppressions confinées à la racine cache (`deleteInsideRoot`).
> - **Dépendances Android** : okhttp 4.12.0, retrofit 2.11.0, osmdroid 6.1.18, zxing 3.5.4, work 2.11.2 — toutes récentes, aucune CVE exploitable connue.

---

### 3.6 — Backend Python (`docs/server/`)

> **Framework :** FastAPI + uvicorn + httpx. **Évaluation globale : backend notablement bien durci.** SQL entièrement paramétré, proxy SignalQuest en liste blanche (aucune URL fournie par l'utilisateur), jetons d'upload HMAC à usage unique liés IP+site, validation JPEG approfondie anti‑bombe, aucun `eval/exec/subprocess/pickle/yaml.load`, aucun secret commité, bases live ouvertes en lecture seule (`mode=ro&immutable=1`), `docs_url`/`redoc_url` désactivés, pas de `debug=True`, pas de CORS permissif.

#### 🟡 [SRV‑01] Dépendances Python non épinglées
- **Gravité :** Moyenne
- **Fichier :** `docs/server/requirements.txt:1-6`
- **Description :** Les 6 dépendances (`fastapi`, `uvicorn`, `httpx`, `python-multipart`, `cryptography`, `Pillow`) sont **sans contrainte de version** ni lockfile. `python-multipart` (CVE‑2024‑53981, DoS multipart) et `Pillow` (nombreuses CVE de parsing d'images) ont des avis de sécurité ; la CVE multipart est **directement atteignable** via l'endpoint d'upload photo.
- **Impact :** Risque chaîne d'approvisionnement, builds non reproductibles, possibilité de tirer silencieusement une version vulnérable.
- **Recommandation :** Épingler les versions exactes (`==`), ajouter un `requirements.lock` avec hashes (`pip install --require-hashes`), et suivre les avis. **Supprimer `Pillow`** s'il est réellement inutilisé (voir SRV‑04).

#### 🟡 [SRV‑02] Absence de limitation de débit sur les endpoints `live-FR` / `download` ; attribution d'IP à durcir
- **Gravité :** Moyenne
- **Fichiers :** `signalquest_proxy.py:275-297` (`_check_rate_limit`), `live_fr_api.py` (tous endpoints), `main.py`
- **Description :** La limitation de débit n'existe **que** sur les endpoints SignalQuest et repose sur SQLite (DELETE+COUNT+INSERT synchrones par requête). Les endpoints `/api/v2/live/fr/*`, `/api/v2/maps/catalog` et `/api/v2/download/manifest` (qui calcule un SHA‑256 sur toute la base) **n'ont aucune limitation**. Le keying par `client_ip` derrière un CDN sans `TRUSTED_PROXY_IPS` correctement réglé écrase tous les clients sur une IP, ou — si mal configuré — laisse `X-Forwarded-For` spoofable.
- **Impact :** Surface d'amplification DoS bon marché ; la table SQLite de rate‑limit est elle‑même un point de contention.
- **Recommandation :** Ajouter une limitation (ou throttling CDN amont) sur les endpoints live‑FR et download ; déplacer l'état de rate‑limit vers un store mémoire/Redis ; **documenter que `TRUSTED_PROXY_IPS` DOIT être réglé en production** (le défaut vide ignore `X-Forwarded-For` — sûr par défaut, mais le déploiement doit l'attribuer correctement).

#### 🔵 [SRV‑03] Clé privée de signature du manifeste possiblement passée par variable d'environnement, non chiffrée au repos
- **Gravité :** Faible
- **Fichiers :** `main.py:94, 306-316` (`manifest_signing_key_pem()`), `generate_manifest_keys.py:19` (`NoEncryption()`)
- **Description :** La clé privée ECDSA P‑256 peut être lue depuis `GEOTOWER_MANIFEST_SIGNING_KEY_PEM` (env var) ; `generate_manifest_keys.py` l'écrit **non chiffrée** (PKCS8). Sinon le traitement est correct : aucune clé commitée (historique vérifié), jamais loggée. Les variables d'environnement fuient plus facilement (`/proc/<pid>/environ`, listings, `container inspect`, dumps de crash).
- **Recommandation :** Préférer le chemin fichier (`GEOTOWER_MANIFEST_SIGNING_KEY_PATH`, `chmod 600`) ou un secrets manager/KMS ; chiffrer la clé au repos.

#### 🔵 [SRV‑04] `description` et `exifMetadata` relayés sans assainissement (XSS stocké déporté en amont)
- **Gravité :** Faible
- **Fichier :** `signalquest_proxy.py:876-900`
- **Description :** `description` (tronquée à 1000 car.) et la chaîne `exifMetadata` brute (seulement validée comme JSON parseable, puis le **brut** est relayé) sont transmises à `signalquest.fr` sans suppression HTML/script. Ce backend ne rend jamais ces valeurs ⇒ **pas de XSS ici** ; le risque est entièrement reporté sur le service amont s'il les affiche.
- **Recommandation :** Borner la taille d'`exifMetadata` et rejeter les charges manifestement non‑EXIF ; noter dans le modèle de menace que le proxy est un pass‑through de champs libres.

#### ⚪ [SRV‑05] `Pillow` présent mais non utilisé
- **Gravité :** Info
- **Fichiers :** `requirements.txt:6` ; validation JPEG faite à la main (`signalquest_proxy.py:514-603`)
- **Description :** La validation d'upload ne décode **jamais** l'image (parsing manuel des marqueurs JPEG + limites de dimensions/pixels avant tout décodage) — conception délibérément anti‑bombe, plus sûre que Pillow. `Pillow` n'est importé nulle part ⇒ surface de dépendance inutile (Pillow a un long historique de CVE).
- **Recommandation :** Retirer `Pillow` de `requirements.txt`.

#### ⚪ [SRV‑06] Latent : `get_remote_file_size_mb` suit les redirections (URLs statiques aujourd'hui)
- **Gravité :** Info
- **Fichiers :** `main.py:771-780`, appelé par `get_maps_catalog` (`:827`)
- **Description :** Sur rafraîchissement du catalogue, `urllib.request.urlopen(HEAD)` est émis vers des URLs **codées en dur** (`download.mapsforge.org`) ⇒ pas de SSRF utilisateur aujourd'hui. Mais `urllib` suit les redirections sans re‑vérification d'allowlist ; si le catalogue devenait data‑driven, cela deviendrait un sink SSRF.
- **Recommandation :** Garder les URLs en dur ; valider l'hôte résolu contre une allowlist fixe ; rafraîchir le catalogue hors du chemin de requête.

> **✅ Vérifié SÛR côté backend (aucune action) :**
> - **Injection SQL** : tout paramétré ; les f‑strings SQL n'interpolent que des fragments **codés en dur** pilotés par des booléens ; `ATTACH DATABASE ?` paramétré.
> - **SSRF dans le proxy** : cibles limitées à `SIGNALQUEST_BASE_URL`, chemin construit depuis un `site_id` validé par regex `SAFE_SITE_ID_RE` ; httpx ne suit **pas** les redirections par défaut ⇒ pas de DNS‑rebinding.
> - **Path traversal** : noms de fichiers serveur‑générés (UUID), `FileResponse` sur chemins fixes, `upload_id` validé via `uuid.UUID()`.
> - **Injection commande / désérialisation** : aucun `os.system`/`subprocess`/`eval`/`exec`/`pickle`/`yaml.load`.
> - **XXE / ReDoS** : pas de parsing XML ; regex linéaires et bornées.
> - **Jetons d'upload** : HMAC‑SHA256, comparaison constante `hmac.compare_digest`, liés `siteId`+`clientIp`+nonce, usage unique atomique, TTL borné, quotas horaires — couverts par la suite de tests de sécurité.
> - **Secrets** : `SIGNALQUEST_API_KEY`/`UPLOAD_TOKEN_SECRET` via env, 503 si absents (pas de défaut).

---

## 4. Tableau récapitulatif des constats

| ID | Constat | Gravité | Domaine |
|----|---------|---------|---------|
| PRIV‑01 | EXIF GPS non supprimés par défaut à l'upload | 🟡 Moyenne | Vie privée |
| SRV‑01 | Dépendances Python non épinglées | 🟡 Moyenne | Backend |
| SRV‑02 | Pas de rate‑limit live‑FR/download ; attribution IP | 🟡 Moyenne | Backend |
| NET‑01 | Ouverture Room sans re‑validation complète | 🟡 Moyenne | Intégrité |
| PRIV‑02 | Localisation transmise via fallback Live API | 🔵 Faible | Vie privée |
| PRIV‑03 | Coordonnées carte en clair dans les prefs | 🔵 Faible | Stockage |
| NET‑02 | TOCTOU validation → install | 🔵 Faible | Intégrité |
| NET‑03 | Clé publique unique non révocable | 🔵 Faible | Intégrité |
| NET‑04 | Pas d'anti‑rollback/rejeu | 🔵 Faible | Intégrité |
| AND‑01 | URI persistées sans validation MIME | 🔵 Faible | IPC |
| STO‑01 | `Log.w` non neutralisé en release | 🔵 Faible | Stockage |
| PRM‑01 | Téléchargements sur réseau facturé | 🔵 Faible | Réseau |
| PRM‑02 | `versionCode = 1` incohérent | 🔵 Faible | Build |
| PRM‑03 | README « 100 % hors‑ligne » inexact | 🔵 Faible | Doc |
| SRV‑03 | Clé privée via env var, non chiffrée | 🔵 Faible | Backend |
| SRV‑04 | `description`/`exifMetadata` non assainis (amont) | 🔵 Faible | Backend |
| NET‑05 | Trust anchor ≠ pinning | ⚪ Info | Réseau |
| NET‑06 | Code mort `/db/info` non signé | ⚪ Info | Réseau |
| NET‑07 | Gson `setLenient()` | ⚪ Info | Réseau |
| AND‑02 | Deeplinks non validés (borné) | ⚪ Info | IPC |
| AND‑03 | Filtre `geotower://` sans host | ⚪ Info | IPC |
| SRV‑05 | `Pillow` inutilisé | ⚪ Info | Backend |
| SRV‑06 | SSRF latent catalogue cartes | ⚪ Info | Backend |
| STO‑02 | Aucun secret/SQLi/crypto faible | ⚪ Info ✅ | Stockage |

**Total : 0 Critique · 0 Haute · 4 Moyenne · 12 Faible · 8 Info**

---

## 5. Feuille de route de remédiation priorisée

### Court terme (rapide, fort gain)
1. **PRIV‑01** — Passer `stripExifBeforeUpload` à `true` par défaut, ou toujours retirer les tags GPS. *(1 ligne + politique ; gain confidentialité majeur)*
2. **SRV‑01 / SRV‑05** — Épingler les versions dans `requirements.txt`, ajouter un lockfile à hashes, retirer `Pillow`.
3. **SRV‑02** — Documenter/imposer `TRUSTED_PROXY_IPS` en prod ; ajouter throttling CDN sur live‑FR/download.
4. **PRM‑02** — Corriger `versionCode`.

### Moyen terme (durcissement)
5. **NET‑01 / NET‑02** — Rejouer la validation complète de la base sur le chemin d'ouverture Room.
6. **NET‑04** — Persister `generatedAt` max et rejeter les manifestes antérieurs (anti‑rollback).
7. **AND‑01** — Valider le MIME avant `takePersistableUriPermission` ; libérer les permissions inutiles.
8. **STO‑01** — Neutraliser `Log.w` en release via ProGuard.
9. **SRV‑03** — Migrer la clé privée vers fichier `chmod 600` / KMS, chiffrée au repos.

### Long terme (politique & transparence)
10. **NET‑05 / PRIV‑04** — Évaluer le certificate pinning pour `api.geotower.fr` (upload/live).
11. **NET‑03** — Documenter le runbook de rotation de clé ; pré‑embarquer plusieurs `keyId`.
12. **PRM‑03 / PRIV‑02** — Réécrire la promesse « hors‑ligne » et publier une note de confidentialité listant les endpoints réseau.
13. **NET‑06 / NET‑07 / SRV‑04 / AND‑02 / AND‑03 / SRV‑06** — Nettoyage de code mort et durcissements défensifs.

---

## 6. Conclusion

GeoTower présente une **posture de sécurité solide et au‑dessus de la moyenne** pour une application mobile. L'architecture d'intégrité des mises à jour (manifeste signé ECDSA + SHA‑256 + validation SQLite + install atomique) est exemplaire, l'absence d'injection SQL est confirmée sur l'ensemble du code, aucun secret n'est exposé, et le backend applique de bonnes pratiques (liste blanche proxy, jetons HMAC à usage unique, lecture seule des bases).

Le constat le plus actionnable est la **fuite de coordonnées GPS via les EXIF de photos (PRIV‑01)** — corrigeable en changeant un simple défaut, avec un fort bénéfice pour la vie privée des utilisateurs. Les autres points relèvent du durcissement défensif et de la transparence.

*Aucune action urgente de sécurité critique n'est requise.*

---

*Audit réalisé par revue statique du code source. Une revue dynamique (tests d'intrusion runtime, fuzzing des endpoints, analyse de l'APK signée release) compléterait utilement cet audit statique.*
