# Notes de version Google Play — GeoTower 2.0.14 (versionCode 16)

Un bloc par langue, à coller dans « Nouveautés de cette version » de la Play Console.
Limite Google Play : **500 caractères par langue** (le compte est indiqué en fin de bloc).

---

## Français (fr-FR)

```
• Réglages : la personnalisation des pages devient une section à part entière, avec un bouton et une description par page. La recherche trouve chaque page (carte, boussole, statistiques…).
• Carte : le repère de position glisse entre deux points GPS au lieu de sauter, et continue d'avancer quand le GPS se tait.
• Fiche site : les systèmes annoncés par l'ANFR dont les bandes ne sont pas encore publiées sont signalés.
• Génération locale : besoins réels mesurés avant de lancer, et plus rapide.
```

## English (en-US)

```
• Settings: page customization is now a section of its own, with one button and a description per page. Search now finds each page (map, compass, statistics, and so on).
• Map: the location marker glides between GPS fixes instead of jumping, and keeps moving when the GPS goes quiet.
• Site details: systems announced by ANFR whose bands are not published yet are flagged as such.
• Local database generation: actual needs measured and shown before you start, and faster.
```

## Deutsch (de-DE)

```
• Einstellungen: Die Seitenanpassung ist jetzt ein eigener Bereich, mit einer Schaltfläche und einer Beschreibung pro Seite. Die Suche findet nun jede Seite einzeln (Karte, Kompass, Statistiken usw.).
• Karte: Die Positionsmarkierung gleitet zwischen GPS-Punkten, statt zu springen, und bewegt sich weiter, wenn das GPS ausfällt.
• Standortdetails: Von der ANFR angekündigte Systeme ohne veröffentlichte Bänder werden gekennzeichnet.
• Lokale Erzeugung: Bedarf vorab gemessen, schneller.
```

## Español (es-ES)

```
• Ajustes: la personalización de las páginas pasa a ser una sección propia, con un botón y una descripción por página. La búsqueda encuentra ahora cada página (mapa, brújula, estadísticas…).
• Mapa: el marcador de posición se desliza entre puntos GPS en lugar de saltar, y sigue avanzando cuando el GPS se queda mudo.
• Ficha: los sistemas anunciados por la ANFR cuyas bandas aún no se publican se señalan como tales.
• Generación local: necesidades reales medidas antes de empezar, y más rápida.
```

## Italiano (it-IT)

```
• Impostazioni: la personalizzazione delle pagine diventa una sezione a sé, con un pulsante e una descrizione per pagina. La ricerca trova ora ogni pagina (mappa, bussola, statistiche…).
• Mappa: il segnaposto della posizione scivola tra i punti GPS invece di saltare e continua ad avanzare quando il GPS tace.
• Scheda: i sistemi annunciati dall'ANFR le cui bande non sono ancora pubblicate vengono segnalati.
• Generazione locale: fabbisogni reali misurati prima di iniziare, e più veloce.
```

## Português (pt-PT)

```
• Definições: a personalização das páginas passa a ser uma secção própria, com um botão e uma descrição por página. A pesquisa encontra agora cada página (mapa, bússola, estatísticas…).
• Mapa: o marcador de posição desliza entre pontos GPS em vez de saltar e continua a avançar quando o GPS se cala.
• Ficha: os sistemas anunciados pela ANFR cujas bandas ainda não foram publicadas são assinalados.
• Geração local: necessidades reais medidas antes de começar, e mais rápida.
```

---

## Rappel de publication

- Bundle signé : `app/build/outputs/bundle/release/app-release.aab` (17,2 Mo), reconstructible par
  `./gradlew :app:bundleRelease` (nécessite `keystore.properties` à la racine).
- `versionCode` 16 : Google Play ne compare que celui-là, et un numéro déjà envoyé ne peut jamais
  être réutilisé. Les numéros 15 / 2.0.13 n'ont jamais été publiés — ils ont été renumérotés avant
  envoi, et non brûlés.
- Le changelog affiché **dans l'application** vit à part, dans
  `app/src/main/java/fr/geotower/ui/screens/about/ReleaseNotes.kt` (page « À propos » >
  « Nouveautés »), et il est plus détaillé que ces notes-ci.
