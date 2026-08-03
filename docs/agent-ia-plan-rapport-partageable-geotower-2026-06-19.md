# Prompt IA - rapport partageable GeoTower

## Objectif

Ajouter un rapport partageable propre pour un site/support GeoTower : une fiche lisible, exportable, avec les informations essentielles deja presentes dans l'app.

Le rapport doit etre utile pour forum, Discord, reseaux sociaux, support technique ou archivage personnel. Il doit valoriser les donnees GeoTower sans remplacer les ecrans existants.

## Direction produit

Nom conseille :

- `Rapport GeoTower`
- ou `Fiche partageable`

Format MVP :

- PNG long vertical, comme les images de partage existantes.

Format v2 :

- PDF optionnel, mais ne pas commencer par le PDF si cela complique l'implementation.

Pourquoi PNG d'abord :

- l'app a deja `ShareImageGenerator.kt`, `MapShareGenerator.kt`, `ElevationProfileShareGenerator.kt`, `FileProvider`, QR code et copie presse-papiers ;
- les utilisateurs partagent facilement une image ;
- le controle visuel est plus simple que PDF sur Android.

## Ou placer la fonctionnalite

Emplacement principal :

- dans le menu de partage de la fiche site/support existant.

Fichiers centraux :

- `app/src/main/java/fr/geotower/ui/components/ShareImageGenerator.kt`
- `app/src/main/java/fr/geotower/ui/components/MapShareGenerator.kt`
- `app/src/main/java/fr/geotower/ui/components/ElevationProfileShareGenerator.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SiteDetailScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/emitters/SupportDetailScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/fr/geotower/ui/screens/settings/SharePreferencesSheet.kt`
- `app/src/main/java/fr/geotower/utils/SharePrefs.kt`

Ne pas creer une nouvelle entree de navigation principale pour le MVP. Le rapport est une action de partage, pas une destination quotidienne.

## Relation avec le partage existant

GeoTower possede deja des blocs de partage configurables :

- carte ;
- details support ;
- photos ;
- operateurs ;
- identifiants ;
- dates ;
- adresse ;
- statut ;
- speedtests ;
- debit estime ;
- frequences ;
- QR code ;
- confidentialite ;
- ordre des blocs.

Le rapport doit reutiliser ces preferences quand c'est coherent, au lieu de creer un second systeme de reglages parallele.

Approche conseillee :

- ajouter un preset `Rapport complet` dans le partage existant ;
- garder l'option de personnalisation actuelle ;
- ne pas casser les exports actuels ;
- ne pas changer brutalement les valeurs par defaut sans raison.

## Contenu du rapport site

Sections conseillees, dans cet ordre :

1. En-tete
2. Resume
3. Carte ou mini-carte
4. Operateurs et technologies
5. Frequences / bandes
6. Azimuts si disponible
7. Support et hauteur
8. Dates / historique court
9. Profil d'elevation si disponible
10. Speedtests / SignalQuest si disponible
11. Photos si activees
12. QR code GeoTower
13. Source et date des donnees

Le rapport doit rester lisible. S'il y a trop de donnees, preferer :

- une version synthetique par defaut ;
- un mode `Details complets` optionnel.

## Contenu du rapport support

Pour un support/pylone :

- identifiant support ;
- commune/adresse ;
- coordonnees si confidentialite autorisee ;
- type et hauteur ;
- nombre de sites/antennes rattaches ;
- operateurs presents ;
- technologies presentes ;
- mini-carte ;
- photos ;
- liens/QR code ;
- source/date.

## En-tete

L'en-tete doit donner tout de suite le contexte :

- logo GeoTower ;
- nom/commune du site ;
- operateur principal ou multi-operateurs ;
- identifiant ANFR/support ;
- date de generation ;
- badge `Donnees ANFR` ou source equivalente ;
- QR code petit si active.

Exemple :

```text
GeoTower
Site Orange - Paris 15e
ID ANFR 123456 - genere le 19/06/2026
```

## Resume court

Ajouter un resume lisible :

```text
4G/5G activees. Bandes 700, 1800, 2100, 2600 et 3500 MHz. Support de 32 m. Derniere modification ANFR : 12/06/2026.
```

Le resume doit etre localise, court et factuel.

Ne pas promettre une couverture ou un debit reel.

## Carte et QR code

Reutiliser les patterns existants :

- `MapShareGenerator.kt` pour les captures de carte ;
- `generateQrCodeBitmap(...)` pour le QR code ;
- deep link GeoTower si disponible :
  - site : `geotower://site/...` si deja existant ;
  - carte : `geotower://map?lat=...&lon=...&zoom=...`.

Le QR code doit ouvrir l'app dans le bon contexte quand possible.

Si le QR code pointe vers une zone de carte, garder le comportement centre + zoom deja utilise par le partage carte.

## Confidentialite

Le rapport doit respecter les preferences existantes :

- masquer coordonnees exactes si `confidential` est actif ;
- eviter d'inclure une position utilisateur ;
- ne pas inclure de donnees privees SignalQuest ;
- ne pas inclure de chemins locaux ou infos de debug ;
- ne pas inclure d'EXIF dans les images exportees.

Si les photos communautaires sont incluses :

- verifier les droits/source ;
- garder les credits si l'app en affiche deja ;
- ne pas exposer d'EXIF.

## UI de selection

Dans le menu de partage :

- ajouter un choix clair :
  - `Partager le rapport`
  - `Copier le rapport`
  - `Personnaliser`

Actions :

- Android share intent ;
- copier l'image dans le presse-papiers ;
- optionnel : enregistrer dans les fichiers.

L'app a deja un pattern "partager" + "copier". Le rapport doit suivre le meme pattern.

## Reglages

Dans `Parametres > Preferences > Contenu du partage par defaut` :

- ajouter un preset ou une section `Rapport GeoTower`.
- Ne pas multiplier les toggles si les toggles existants suffisent.
- Si de nouveaux blocs sont ajoutes, les integrer a `SharePrefs`.

Blocs potentiels :

- `timeline`
- `source_data`
- `summary`
- `elevation_profile`
- `speedtests`
- `throughput`
- `photos`
- `qr`

Si la timeline n'est pas encore implementee, afficher simplement `Dates` et `Derniere modification`.

## Architecture conseillee

Eviter de grossir encore un fichier geant si possible.

Option propre :

- creer `app/src/main/java/fr/geotower/ui/components/report/GeoTowerReportModels.kt`
- creer `app/src/main/java/fr/geotower/ui/components/report/GeoTowerReportBuilder.kt`
- creer `app/src/main/java/fr/geotower/ui/components/report/GeoTowerReportRenderer.kt`
- garder les menus/actions dans `ShareImageGenerator.kt`, mais deleguer la construction du bitmap.

Modele indicatif :

```kotlin
data class GeoTowerReport(
    val title: String,
    val subtitle: String,
    val generatedAt: String,
    val sourceVersion: String?,
    val sections: List<GeoTowerReportSection>,
    val qrText: String?
)

sealed interface GeoTowerReportSection {
    data class Summary(val text: String) : GeoTowerReportSection
    data class Map(val bitmap: Bitmap?) : GeoTowerReportSection
    data class KeyValues(val title: String, val rows: List<Pair<String, String>>) : GeoTowerReportSection
    data class Operators(val rows: List<OperatorTechnologySummary>) : GeoTowerReportSection
    data class Photos(val bitmaps: List<Bitmap>) : GeoTowerReportSection
}
```

Le builder collecte les donnees. Le renderer dessine.

## Design visuel

Le rapport doit etre propre, dense et partageable :

- fond clair ou adapte au theme, mais export stable conseille en clair ;
- marges regulieres ;
- sections separees par titres courts ;
- cartes a rayon modere ;
- couleurs operateurs utilisees comme accents ;
- texte suffisamment grand pour etre lisible sur mobile ;
- pas de decoration inutile ;
- pas de gros vide ;
- QR code visible mais pas dominant.

Largeur recommandee :

- 1080 px pour une image partageable.

Hauteur :

- dynamique, mais limiter les sections trop longues ;
- si trop long, proposer split en plusieurs images comme le partage existant si ce mode existe deja.

## Source des donnees

Donnees a reutiliser depuis les ecrans existants :

- `AntennaEntity` / localisation ;
- `TechniqueEntity` pour dates, hauteurs, statuts ;
- `PhysiqueEntity` pour support ;
- frequences depuis les blocs existants ;
- photos depuis `CommunityPhotosViewer` / donnees deja chargees ;
- speedtests depuis les composants existants ;
- debit estime depuis le calculateur si disponible ;
- profil d'elevation via `ElevationProfileShareGenerator.kt` si deja calculable ;
- metadata base si disponible pour afficher la source.

Ne pas refaire des appels reseau juste pour generer un rapport si les donnees sont deja chargees. Si une section demande un appel, afficher cette section seulement quand elle est disponible.

## PDF optionnel v2

Si PDF est demande plus tard :

- generer d'abord la meme structure de rapport en modele ;
- rendre en bitmap ou en document PDF selon destination ;
- ne pas maintenir deux logiques de contenu separees.

Destinations possibles :

- PNG ;
- PDF ;
- presse-papiers ;
- Android share ;
- fichier local.

Le PDF doit rester optionnel tant que le PNG couvre le besoin de partage.

## Strings a prevoir

Noms indicatifs :

- `appstrings_report_title`
- `appstrings_report_share`
- `appstrings_report_copy`
- `appstrings_report_copied`
- `appstrings_report_customize`
- `appstrings_report_complete`
- `appstrings_report_summary`
- `appstrings_report_generated_on`
- `appstrings_report_source_data`
- `appstrings_report_section_map`
- `appstrings_report_section_operators`
- `appstrings_report_section_frequencies`
- `appstrings_report_section_support`
- `appstrings_report_section_dates`
- `appstrings_report_section_timeline`
- `appstrings_report_section_elevation_profile`
- `appstrings_report_section_speedtests`
- `appstrings_report_section_photos`
- `appstrings_report_confidential_location_hidden`
- `appstrings_report_unavailable_section`

Mettre a jour `values`, `values-fr`, `values-en`, `values-de`, `values-es`, `values-it`, `values-pt`.

## Accessibilite

Dans l'interface :

- les boutons `Partager`, `Copier`, `Personnaliser` doivent avoir des descriptions claires ;
- les interrupteurs de blocs doivent annoncer leur etat ;
- le preview de rapport doit avoir une description courte.

Dans l'export :

- pour une image PNG, l'accessibilite dependra surtout du texte visible et de la lisibilite ;
- garder un contraste suffisant ;
- eviter les infos uniquement portees par la couleur.

## Criteres d'acceptation MVP

- Depuis une fiche site, l'utilisateur peut generer un rapport PNG.
- L'utilisateur peut partager ou copier ce rapport.
- Le rapport contient au minimum :
  - titre/site ;
  - identifiant ;
  - operateurs/technos ;
  - frequences ou resume radio ;
  - dates ;
  - mini-carte si disponible ;
  - QR code si active ;
  - source/date de generation.
- Les preferences de confidentialite sont respectees.
- Le rendu est lisible en 1080 px de large.
- Les strings sont localisees.
- La compilation Kotlin passe.

## Criteres d'acceptation v2

- Rapport support disponible.
- Rapport avec timeline si la fonctionnalite existe.
- Rapport avec profil d'elevation si disponible.
- Option PDF.
- Split automatique si le rapport est trop long.
- Tests sur builder de rapport.

## Validation

Executer au minimum :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugKotlin
```

Si des strings sont ajoutees :

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:ANDROID_HOME='C:\Users\Julien\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests fr.geotower.AndroidI18nResourcesTest
```

Tests manuels :

1. Ouvrir une fiche site avec plusieurs operateurs.
2. Generer le rapport.
3. Verifier partage Android.
4. Verifier copie presse-papiers.
5. Verifier QR code.
6. Activer le mode confidentiel et verifier que les coordonnees sensibles sont masquees.
7. Tester une fiche avec peu de donnees.
8. Tester une fiche avec photos/speedtests si disponibles.
9. Tester theme clair/sombre si le rendu depend du theme.

## Recommendation de livraison

Livrer en trois passes :

1. Rapport PNG site base sur les blocs deja existants.
2. Refactor propre en builder/renderer si `ShareImageGenerator.kt` devient trop gros.
3. PDF + rapport support + timeline.

Le meilleur premier livrable est un `Rapport complet` dans le menu de partage actuel, avec partage et copie.
