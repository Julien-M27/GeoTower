# Prompt IA - plan d'accessibilite GeoTower

## Objectif

Ameliorer l'accessibilite de GeoTower pour les personnes aveugles ou malvoyantes, en priorite avec TalkBack/lecteurs d'ecran. Ne pas refaire le design. Ne pas transformer l'app en mode simplifie. Ajouter des alternatives vocales et des descriptions utiles aux elements tres visuels.

Le chantier doit rester compatible avec l'architecture actuelle : Android natif, Kotlin, Jetpack Compose, Material 3, Osmdroid, Room, app offline-first, ressources localisees dans `res/values*`.

## Regles importantes

- Lire le code actuel avant de modifier. Le repo peut deja contenir des changements non lies : ne pas les revert.
- Garder les modifications ciblees et coherentes avec les composants existants.
- Mettre les textes utilisateur dans les ressources Android, pas en dur dans le Kotlin.
- Mettre a jour au minimum `values`, `values-fr`, `values-en`, `values-de`, `values-es`, `values-it`, `values-pt` si une cle de string est ajoutee.
- Ne pas considerer tous les `contentDescription = null` comme des bugs. Ils sont acceptables pour les icones decoratives deja accompagnees par du texte.
- Corriger en priorite les controles icon-only, les zones cliquables sans role clair, les visuals Canvas, les cartes Osmdroid integrees par `AndroidView`, les poignees de deplacement et les champs de recherche custom.
- Separarer les sujets "aveugles/malvoyants" et "malentendants". L'app ne semble pas dependante du son, donc le plus gros gain est TalkBack/navigation vocale.

## Surfaces prioritaires

1. `app/src/main/java/fr/geotower/ui/screens/map/MapScreen.kt`
2. `app/src/main/java/fr/geotower/ui/components/SiteStatusCard.kt`
3. `app/src/main/java/fr/geotower/ui/components/SharedMiniMapCard.kt`
4. `app/src/main/java/fr/geotower/ui/screens/emitters/ElevationProfileScreen.kt`
5. `app/src/main/java/fr/geotower/ui/screens/emitters/SignalQuestUploadScreen.kt`
6. `app/src/main/java/fr/geotower/ui/screens/settings/PagesCustomizationSheet.kt`
7. `app/src/main/java/fr/geotower/ui/screens/settings/ExternalLinksSettingsSheet.kt`
8. Eventuellement `CompassScreen.kt`, `StatisticsScreen.kt`, widgets et composants de partage si le temps le permet.

## Tache 1 - Carte principale accessible

Probleme : la carte Osmdroid est integree via `AndroidView`. Les marqueurs, clusters, azimuts, filtres et gestes ne sont pas automatiquement lisibles par TalkBack.

A faire :

- Ajouter une description TalkBack au conteneur de carte.
- La description doit resumer l'etat utile, pas decrire l'image :
  - nombre de sites visibles si disponible ;
  - site le plus proche ;
  - distance approximative ;
  - operateurs visibles ou presents ;
  - filtre actif, par exemple 5G, operateur, frequence, FH ;
  - etat de localisation si pertinent.
- Ajouter ou exposer une action accessible du type "Parcourir les antennes visibles".
- Cette action peut ouvrir une bottom sheet/liste Compose deja accessible, avec les sites visibles autour de la zone courante.
- Verifier les boutons autour de la carte :
  - localisation ;
  - partage ;
  - filtres ;
  - recherche ;
  - fermeture/annulation ;
  - reset ;
  - credits/attribution si cliquable.
- Les champs custom `BasicTextField` doivent avoir un label/description clair et etre lisibles par TalkBack.

Exemple de description cible :

`Carte des antennes. 14 sites visibles. Le plus proche est a 420 metres. Operateurs visibles : Orange, SFR, Free. Filtre 5G actif.`

Ne pas ajouter un gros texte visible permanent si cela pollue l'interface. Utiliser `Modifier.semantics`, `contentDescription`, `stateDescription` ou une action accessible selon le cas.

## Tache 2 - Statuts operateur lisibles sans couleur

Fichier principal : `SiteStatusCard.kt`.

Probleme : les statuts radio sont tres visuels : couleurs, icones, badges, grilles. TalkBack doit pouvoir lire le sens exact.

A faire :

- Pour chaque ligne/cellule operateur/technologie, fournir une phrase vocale claire :
  - `Orange 5G active`
  - `SFR 4G autorisee mais non active`
  - `Free 3G non presente`
  - `Faisceau hertzien present`
- Si une cellule est cliquable, lui donner un role de bouton et une description d'action.
- Si plusieurs icones decoratives composent une ligne, utiliser une seule description composite au niveau du parent et masquer les decorations.
- Verifier la legende des statuts : elle doit etre lisible dans l'ordre et ne pas dependre uniquement de la couleur.

## Tache 3 - Mini-cartes et cartes embarquees

Fichier principal : `SharedMiniMapCard.kt`.

Probleme : les mini-cartes Osmdroid/Canvas sont utiles visuellement mais pauvres vocalement.

A faire :

- Ajouter une description du contenu de la mini-carte :
  - site/support affiche ;
  - distance si disponible ;
  - direction ou relation avec la position utilisateur si disponible ;
  - nombre d'antennes/faisceaux si connu ;
  - etat de chargement ou absence de carte.
- Les overlays, marqueurs et images decoratives doivent rester decoratifs s'ils sont deja resumes par le parent.
- Les boutons de bascule/plein ecran/partage doivent avoir une description d'action.

Exemple cible :

`Mini-carte du site. Support a 1,2 kilometre. Orange et Bouygues presents. Double toucher pour ouvrir la carte.`

## Tache 4 - Profil d'elevation et Canvas

Fichier principal : `ElevationProfileScreen.kt`.

Probleme : le graphique est dessine avec `Canvas`, donc TalkBack ne lit pas les axes, les reliefs, ni la zone de Fresnel.

A faire :

- Ajouter une description TalkBack au graphique :
  - distance totale ;
  - altitude depart/arrivee ;
  - altitude min/max si disponible ;
  - degagement ou obstruction Fresnel ;
  - conclusion courte : "liaison probablement degagee", "obstacle detecte", etc.
- Eviter les descriptions trop longues. L'objectif est un resume exploitable.
- Si une copie/partage/export existe, verifier les descriptions des boutons.
- Si un detail plus long existe deja a l'ecran, s'assurer que TalkBack le lit dans un ordre logique.

Exemple cible :

`Profil d'elevation sur 2,4 kilometres. Altitude de depart 52 metres, arrivee 83 metres. Obstacle detecte vers 1,1 kilometre. Zone de Fresnel partiellement obstruee.`

## Tache 5 - Upload SignalQuest et photos

Fichier principal : `SignalQuestUploadScreen.kt`.

Probleme : l'ajout de photo, les apercus image, les zones de drag/drop et les boutons icon-only peuvent etre ambigus.

A faire :

- Donner une description aux zones d'ajout photo :
  - `Ajouter une photo du support`
  - `Ajouter une photo du site`
  - `Remplacer la photo`
- Les apercus photo doivent indiquer leur role :
  - photo selectionnee ;
  - bouton pour voir/remplacer/supprimer si cliquable.
- Les actions de suppression, ajout, source image, validation doivent avoir des descriptions courtes.
- Si l'ordre des photos peut etre change, fournir une alternative accessible ou au minimum une description claire de la poignee.

## Tache 6 - Sheets de personnalisation et elements reordonnables

Fichiers principaux :

- `PagesCustomizationSheet.kt`
- `ExternalLinksSettingsSheet.kt`

Probleme : les poignees de deplacement et certains boutons `Refresh`/retour sont icon-only.

A faire :

- Ajouter des descriptions aux boutons retour :
  - utiliser la string existante `appstrings_back` si disponible.
- Ajouter des descriptions aux boutons reset/actualiser :
  - `Reinitialiser`
  - `Restaurer l'ordre par defaut`
  - ou une phrase precise selon le contexte.
- Pour les poignees `DragHandle`, ne pas se contenter de `contentDescription = null` si l'element est interactif.
- Si possible, ajouter des actions accessibles "Monter" et "Descendre" pour les elements reordonnables. C'est mieux qu'une poignee uniquement tactile.
- Verifier que chaque ligne cliquable annonce son etat :
  - actif/inactif ;
  - visible/cache ;
  - selectionne/non selectionne.

## Tache 7 - Recherche, champs custom et ordre de focus

A faire dans les ecrans concernes :

- Verifier les `BasicTextField` custom, notamment recherche carte.
- Ajouter label, placeholder ou semantics pour que TalkBack annonce le champ comme un champ de recherche.
- Verifier que le bouton effacer/fermer a une description.
- Verifier l'ordre de focus : titre, champ, resultats, actions.
- Pour les resultats de recherche, chaque ligne doit annoncer le nom, la commune/distance si disponible et l'action.

## Tache 8 - Accessibilite pour malentendants

L'app semble peu dependante de sons. Faire quand meme une verification rapide :

- Aucune information critique ne doit etre uniquement sonore.
- Les notifications doivent avoir un contenu texte clair.
- Si vibration/son existe quelque part, garder une alternative visuelle/texte.
- Ne pas ajouter d'audio obligatoire.

## Ressources strings a prevoir

Ajouter uniquement les cles necessaires, mais prevoir des noms proches de ceux-ci :

- `appstrings_accessibility_map_summary`
- `appstrings_accessibility_map_visible_sites`
- `appstrings_accessibility_map_nearest_site`
- `appstrings_accessibility_map_active_filter`
- `appstrings_accessibility_browse_visible_sites`
- `appstrings_accessibility_site_status_operator_active`
- `appstrings_accessibility_site_status_operator_authorized`
- `appstrings_accessibility_site_status_operator_absent`
- `appstrings_accessibility_mini_map_summary`
- `appstrings_accessibility_elevation_profile_summary`
- `appstrings_accessibility_add_site_photo`
- `appstrings_accessibility_replace_site_photo`
- `appstrings_accessibility_remove_site_photo`
- `appstrings_accessibility_move_item_up`
- `appstrings_accessibility_move_item_down`
- `appstrings_restore_default_order`

Adapter les noms aux conventions deja presentes dans le projet.

## Validation obligatoire

Executer au minimum :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugKotlin
```

Si des strings sont ajoutees, executer aussi les tests i18n si disponibles :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests fr.geotower.AndroidI18nResourcesTest
```

Si `:app:lintDebug` echoue sur un probleme existant non lie, noter l'erreur sans bloquer tout le chantier. Lint peut detecter des erreurs d'accessibilite Android classiques, mais il ne detectera pas toutes les lacunes Compose/Canvas.

## Parcours manuel TalkBack a tester

Tester au moins ce parcours avec TalkBack active :

1. Accueil.
2. Carte.
3. Recherche d'un site ou navigation vers une antenne proche.
4. Detail site/support.
5. Statuts operateurs.
6. Mini-carte.
7. Profil d'elevation si accessible depuis le site.
8. Partage ou copie.
9. Parametres, personnalisation des pages.
10. Upload SignalQuest/photo.

Pour chaque ecran, verifier :

- le titre est annonce ;
- les boutons ont une action claire ;
- les elements visuels importants ont une alternative vocale ;
- l'ordre de lecture est logique ;
- aucun element interactif important n'est silencieux ;
- les etats actif/inactif/selectionne sont annonces.

## Priorite de livraison conseillee

Livrer en petites passes :

1. Carte principale : resume TalkBack + controles icon-only.
2. `SiteStatusCard` : statuts operateur comprehensibles sans couleur.
3. `SharedMiniMapCard` + `ElevationProfileScreen` : alternatives aux visuels.
4. Reorder/settings + SignalQuest photo.
5. Audit final TalkBack et correction des oublis.

La meilleure premiere PR est la passe 1 + 2, car elle apporte tout de suite un gros gain sans toucher toute l'app.
