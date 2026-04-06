# 🗼 GeoTower

**GeoTower** est une application Android native conçue pour la cartographie, l'analyse et le suivi des antennes relais et faisceaux hertziens en France (basée sur les données de l'ANFR). 

Pensée pour les passionnés de télécoms et les professionnels, elle offre une navigation fluide, une base de données 100% hors-ligne et un haut niveau de personnalisation visuelle.

---

## ✨ Fonctionnalités Principales

* 🗺️ **Cartographie Avancée (Osmdroid)** : 
  * Rendu natif des pylônes avec gestion dynamique des clusters interactifs (Macro et Micro).
  * Affichage géométrique précis des azimuts (cônes de diffusion) pour les réseaux Mobiles (2G/3G/4G/5G) et les Faisceaux Hertziens (FH).
  * Alignement algorithmique des technologies superposées (points de couleurs alignés au bout des traits de direction).
* 📡 **Mode 100% Hors-Ligne (Offline-First)** : 
  * Téléchargement complet de la base de données ANFR via une API dédiée.
  * Moteur de recherche ultra-rapide et affichage fluide sans aucune connexion internet.
* 🤖 **Automatisation & Arrière-plan (WorkManager)** :
  * Détection automatique des nouvelles mises à jour de la base de données avec alertes silencieuses.
  * Synchronisation en arrière-plan des Widgets (tailles Small, Medium, Large) pour garder un œil sur les antennes à proximité.
  * Suivi de position en direct (Live Tracking) avec notifications.
* 🎨 **Interface Moderne & Personnalisable (Jetpack Compose)** :
  * Design Material 3 avec option d'affichage "One UI" (bords arrondis, bulles).
  * Mode Sombre profond (OLED Black) supporté nativement.
  * Personnalisation poussée : icônes de l'application, couleurs des opérateurs (Orange, SFR, Free, Bouygues), disposition des menus, et filtres de fréquences.
* 📤 **Outils de Partage** : 
  * Génération de QR Codes.
  * Export des détails techniques complets (pylônes, supports, dates d'activation) et des captures de carte.

---

## 🛠️ Stack Technique

L'application est développée entièrement en **Kotlin** et repose sur les standards de l'architecture Android moderne :

* **UI** : Jetpack Compose (100% déclaratif), Material 3.
* **Cartographie** : Osmdroid, OSMBonusPack (pour le clustering).
* **Base de données** : Room (SQLite) avec des requêtes SQL optimisées pour la géolocalisation complexe (Bounding Box).
* **Asynchronisme & Flux** : Kotlin Coroutines, StateFlow.
* **Tâches en arrière-plan** : WorkManager, Foreground Services.
* **Réseau** : Retrofit, OkHttp.
* **Architecture** : MVVM (Model-View-ViewModel) / Single Source of Truth.

---

## 🚀 Installation & Build

### Prérequis
* Android Studio (version la plus récente recommandée).
* JDK 17+.
* SDK Android minimum : API 24 (Android 7.0).
