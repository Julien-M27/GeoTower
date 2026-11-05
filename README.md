# 🗼 GeoTower

**GeoTower** est une application Android native conçue pour la cartographie, l'analyse et le suivi des antennes relais et faisceaux hertziens en France (basée sur les données publiques ouvertes de l'ANFR). 

Pensée pour les passionnés de télécoms et les professionnels du terrain, elle offre une navigation fluide, une base de données 100% hors-ligne, un compagnon pour montres connectées et un très haut niveau de personnalisation visuelle.

---

## ✨ Fonctionnalités Principales

* 🗺️ **Cartographie Avancée (Osmdroid & Mapsforge)** : 
  * Rendu natif des pylônes avec gestion dynamique des clusters interactifs (Macro et Micro).
  * Affichage géométrique précis des azimuts (cônes de diffusion) pour les réseaux Mobiles (2G/3G/4G/5G) et les Faisceaux Hertziens (FH).
  * Alignement algorithmique des technologies superposées (points de couleurs alignés au bout des traits de direction).
  * Filtres par opérateur (Orange, SFR, Bouygues Telecom, Free Mobile) et intégration des diffuseurs (TDF, Towercast, SNCF Réseau...).
  * Fonds de carte en ligne ou hors-ligne téléchargeables par région.

* 📡 **Mode 100% Hors-Ligne (Offline-First)** : 
  * Téléchargement complet de la base de données ANFR via une API dédiée avec basculement automatique sur miroir.
  * Génération autonome de la base SQLite directement sur l'appareil (traitement local).
  * Moteur de recherche instantané (adresse, commune, identifiant #id, eNB / gNB) sans aucune connexion internet.

* ⌚ **Compagnon Montres Garmin Connect IQ** :
  * Détection automatique et communication Bluetooth BLE avec votre montre connectée Garmin (Forerunner, Fenix, Epix, Venu...).
  * Envoi multi-trames fluide des émetteurs à proximité directement au poignet, 100% autonome sans connexion internet requise sur le téléphone.
  * Réveil en arrière-plan (service Binder) avec gestion de veille écran éteint.
  * Outil de diagnostic en direct avec journalisation des événements BLE et test de ping.

* 🚗 **Compatibilité Android Auto** :
  * Application POI dédiée pour écran de bord automobile via la bibliothèque Android for Cars.
  * Recherche d'antennes et surveillance de la couverture réseau le long de vos trajets.

* 🧭 **Outils Terrain & Mesures en Direct** :
  * Boussole gyroscopique dynamique orientée vers le site sélectionné.
  * Calcul en temps réel de la distance, altitude et profil altimétrique entre votre position et l'antenne.
  * Suivi de position en direct (Live Tracking) avec notifications d'émetteur le plus proche.
  * Commande vocale intégrée avec l'Assistant Android / Gemini pour interroger les antennes autour de vous.

* 📊 **Statistiques & Analyse du Territoire** :
  * Synthèse complète par département : densités de pylônes par km² et par millier d'habitants.
  * Répartition détaillée par opérateur et par génération (2G/3G/4G/5G).
  * Évolution historique de l'application avec journal des versions interactif.

* 🤖 **Widgets & Arrière-plan (Glance & WorkManager)** :
  * Synchronisation en arrière-plan des Widgets (tailles Petite, Moyenne, Grande et Widget Carte).
  * Détection automatique des nouvelles versions de base de données ANFR et des pannes réseau déclarées par les opérateurs.

* 🎨 **Interface Moderne & Personnalisable (Jetpack Compose)** :
  * Design Material 3 Expressive avec animations fluides et mode sombre profond (OLED Black).
  * Barre de navigation inférieure personnalisable à 5 onglets avec gestes de balayage tactiles.
  * Prise en charge des écrans larges, tablettes et appareils pliables (mode écran partagé).
  * Personnalisation complète : icônes d'application, couleurs d'opérateurs, styles de carte et filtres de fréquences.

* 📤 **Rapports & Partage** : 
  * Fiches techniques de synthèse PDF haute résolution d'un site ou pylône.
  * Images de partage avec données radio, coordonnées géographiques et QR Codes d'accès direct.

* 🔒 **Respect de la Vie Privée** :
  * Aucun compte, aucune inscription, aucun achat intégré.
  * Zéro publicité, zéro traceur.
  * La géolocalisation reste strictement sur votre appareil et n'est jamais transmise à des tiers.

---

## 🛠️ Stack Technique

L'application est développée entièrement en **Kotlin** et repose sur les standards les plus récents de l'architecture Android moderne :

* **UI** : Jetpack Compose (100% déclaratif), Material 3 Expressive, Glance (Widgets).
* **Cartographie** : Osmdroid, OSMBonusPack (clustering), Mapsforge (tuiles vectorielles locales).
* **Compagnon Connecté** : Garmin Connect IQ SDK (communication BLE & Binder Service).
* **Automobile** : Android for Cars App Library (`androidx.car.app`).
* **Base de données** : Room (SQLite) avec requêtes spatiales optimisées (Bounding Box), tables d'indexation eNB/gNB et agrégats statistiques.
* **Tâches en arrière-plan** : WorkManager, Foreground Services, WakeLocks temporaires.
* **Asynchronisme & Flux** : Kotlin Coroutines, StateFlow, Channel.
* **Réseau** : Retrofit 2, OkHttp 3, Gson.
* **Architecture** : MVVM (Model-View-ViewModel) / Single Source of Truth.

---

## 🚀 Installation & Build

### Prérequis
* Android Studio (Ladybug ou plus récent).
* JDK 17 / JDK 21.
* SDK Android minimum : API 24 (Android 7.0 Nougat).
* SDK Android cible : API 37.

### Compilation locale
```bash
# Lancer les tests unitaires
./gradlew :app:testMobileDebugUnitTest

# Générer l'APK de débogage
./gradlew :app:assembleMobileDebug

# Générer le bundle de production signé
./gradlew :app:bundleMobileRelease
```
