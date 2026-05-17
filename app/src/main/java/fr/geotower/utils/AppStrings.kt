package fr.geotower.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import java.text.Normalizer
import java.util.Locale

object AppStrings {
    const val LANGUAGE_SYSTEM = "Système"
    const val LANGUAGE_FRENCH = "Français"
    const val LANGUAGE_ENGLISH = "English"
    const val LANGUAGE_PORTUGUESE = "Português"
    const val LANGUAGE_ITALIAN = "Italiano"
    const val LANGUAGE_GERMAN = "Deutsch"
    const val LANGUAGE_SPANISH = "Español"

    // Constantes moteur radio : elles servent de clés stables et sont traduites plus bas.
    const val THROUGHPUT_WARNING_NETWORK_UNKNOWN = "La charge du réseau, le backhaul et les capacités exactes du téléphone ne sont pas connus."
    const val THROUGHPUT_WARNING_PROFILE_PREFIX = "Le MIMO et la modulation ne sont pas publiés au niveau du site : le profil "
    const val THROUGHPUT_WARNING_PROFILE_SUFFIX = " est donc appliqué."
    const val THROUGHPUT_WARNING_ALLOCATION_PREFIX = "Bande "
    const val THROUGHPUT_WARNING_ALLOCATION_SUFFIX = " exclue : allocation opérateur introuvable."
    const val THROUGHPUT_WARNING_DSS_PREFIX = "Bande "
    const val THROUGHPUT_WARNING_DSS_SUFFIX = " potentiellement partagée entre la 4G et la 5G : le débit n'est pas additionné intégralement."
    const val THROUGHPUT_WARNING_UPLINK_AGGREGATION = "Le débit montant est limité aux deux meilleures fréquences agrégées, une hypothèse plus réaliste pour les réseaux mobiles en France."
    const val THROUGHPUT_WARNING_LOW_BAND_AGGREGATION = "Agrégation 4G entre bandes basses 700/800/900 MHz limitée : beaucoup de téléphones ne cumulent pas ces porteuses."
    const val THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT = "Limite d'agrégation 4G choisie : seules les meilleures porteuses sont comptées."
    const val THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT = "Limite d'agrégation 5G du profil : seules les meilleures porteuses sont comptées."
    const val THROUGHPUT_REASON_NO_METROPOLITAN_ARCEP_ALLOCATION = "Aucune allocation Arcep France métropolitaine compatible avec cette technologie et cette bande."
    const val THROUGHPUT_REASON_DSS_SHARED = "Bande potentiellement partagée entre la 4G et la 5G : elle n'est pas additionnée deux fois."
    const val THROUGHPUT_REASON_5G_DISABLED = "5G désactivée"
    const val THROUGHPUT_REASON_4G_DISABLED = "4G désactivée"
    const val THROUGHPUT_REASON_BAND_EXCLUDED = "Bande exclue"
    const val THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED = "Opérateur non reconnu"
    const val THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED_ARCEP = "Opérateur non reconnu pour les allocations Arcep"
    const val THROUGHPUT_REASON_ARCEP_ALLOCATION_NOT_FOUND = "Allocation Arcep introuvable"
    const val THROUGHPUT_REASON_ALLOCATION_NOT_FOUND = "Allocation introuvable"
    const val THROUGHPUT_REASON_PLANNED_BAND = "Bande en projet"
    const val THROUGHPUT_SOURCE_SUMMARY_ENGINE = "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, ETSI/3GPP TS 38.306 et TS 36.306/36.213 pour le modèle radio."
    const val THROUGHPUT_SOURCE_SUMMARY_DEFAULT = "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, 3GPP pour le modèle radio."
    const val THROUGHPUT_PROFILE_PRUDENT_DESC = "Profil prudent : 4G 64-QAM en descendant, 16-QAM en montant, 5G NR 64-QAM, agrégation limitée et DSS non compté deux fois."
    const val THROUGHPUT_PROFILE_STANDARD_DESC = "Profil standard : 4G 256-QAM en descendant avec MIMO 2x2, montant 64-QAM côté téléphone, 5G n78 256-QAM en descendant avec MIMO 4x4, montant 64-QAM sur 2 couches, DSS non compté deux fois."
    const val THROUGHPUT_PROFILE_IDEAL_LABEL = "Profil idéal"
    const val THROUGHPUT_PROFILE_IDEAL_DESC = "Profil idéal : très bonnes conditions radio plausibles, 4G en descendant avec MIMO 4x4, 5G NR 256-QAM, agrégation plus ouverte et sans double comptage DSS."
    const val THROUGHPUT_PROFILE_CUSTOM_LABEL = "Personnalisé"
    const val THROUGHPUT_PROFILE_CUSTOM_DESC = "Profil personnalisé : modulations descendantes et montantes choisies dans l'interface, débit montant traité comme celui d'un téléphone."
    const val THROUGHPUT_PROFILE_CUSTOM_SHORT_DESC = "Profil personnalisé : modulations DL/UL choisies dans l'interface, UL traité comme un téléphone."

    private data class FallbackTranslation(
        val italian: String,
        val german: String,
        val spanish: String
    )

    // Fallbacks italien / allemand / espagnol pour les anciens appels get(fr, en, pt).
    // Les entrées sont regroupées par écran ou fonctionnalité dominante.
    private val fallbackTranslations = mapOf(
        // Historique d'envoi, navigation principale et centre d'aide.
        "Upload history" to FallbackTranslation("Cronologia invii", "Upload-Verlauf", "Historial de envíos"),
        "No photo upload recorded on this device." to FallbackTranslation("Nessun invio di foto registrato su questo dispositivo.", "Kein Foto-Upload auf diesem Gerät gespeichert.", "No hay ningún envío de fotos registrado en este dispositivo."),
        "Clear history" to FallbackTranslation("Cancella cronologia", "Verlauf löschen", "Borrar historial"),
        "Clear upload history?" to FallbackTranslation("Cancellare la cronologia degli invii?", "Upload-Verlauf löschen?", "¿Borrar el historial de envíos?"),
        "Local thumbnails and history rows will be deleted. Photos already sent to external apps are not changed." to FallbackTranslation("Le miniature locali e le righe della cronologia saranno eliminate. Le foto già inviate alle app esterne non vengono modificate.", "Lokale Vorschaubilder und Verlaufseinträge werden gelöscht. Bereits an externe Apps gesendete Fotos werden nicht geändert.", "Se eliminarán las miniaturas locales y las líneas del historial. Las fotos ya enviadas a apps externas no se modifican."),
        "Loading..." to FallbackTranslation("Caricamento...", "Wird geladen...", "Cargando..."),
        "Nearby Antennas" to FallbackTranslation("Antenne vicine", "Antennen in der Nähe", "Antenas cercanas"),
        "Nearby antennas" to FallbackTranslation("Antenne vicine", "Antennen in der Nähe", "Antenas cercanas"),
        "Antenna Map" to FallbackTranslation("Mappa delle antenne", "Antennenkarte", "Mapa de antenas"),
        "Antenna map" to FallbackTranslation("Mappa delle antenne", "Antennenkarte", "Mapa de antenas"),
        "Antennas map" to FallbackTranslation("Mappa delle antenne", "Antennenkarte", "Mapa de antenas"),
        "Compass" to FallbackTranslation("Bussola", "Kompass", "Brújula"),
        "Statistics" to FallbackTranslation("Statistiche", "Statistiken", "Estadísticas"),
        "Settings" to FallbackTranslation("Impostazioni", "Einstellungen", "Ajustes"),
        "About" to FallbackTranslation("Informazioni", "Über", "Acerca de"),
        "Version" to FallbackTranslation("Versione", "Version", "Versión"),
        "Help" to FallbackTranslation("Aiuto", "Hilfe", "Ayuda"),
        "GeoTower help center" to FallbackTranslation("Centro assistenza GeoTower", "GeoTower-Hilfe", "Centro de ayuda de GeoTower"),
        "Find the table of contents, screen-by-screen explanations, search codes and button meanings here." to FallbackTranslation("Trovi qui il sommario, le spiegazioni schermata per schermata, i codici di ricerca e il significato dei pulsanti.", "Hier findest du Inhaltsverzeichnis, Erklärungen pro Bildschirm, Suchcodes und die Bedeutung der Schaltflächen.", "Aquí encontrarás el índice, explicaciones pantalla por pantalla, códigos de búsqueda y el significado de los botones."),
        "Search a feature..." to FallbackTranslation("Cerca una funzione...", "Funktion suchen...", "Buscar una función..."),
        "Table of contents" to FallbackTranslation("Sommario", "Inhaltsverzeichnis", "Índice"),
        "Results" to FallbackTranslation("Risultati", "Ergebnisse", "Resultados"),
        "No help topic matches this search." to FallbackTranslation("Nessun argomento di aiuto corrisponde a questa ricerca.", "Kein Hilfethema passt zu dieser Suche.", "Ningún tema de ayuda coincide con esta búsqueda."),
        "Clear" to FallbackTranslation("Cancella", "Löschen", "Borrar"),
        "This help section" to FallbackTranslation("Questa sezione di aiuto", "Dieser Hilfebereich", "Esta sección de ayuda"),
        "Back to contents" to FallbackTranslation("Torna al sommario", "Zurück zum Inhalt", "Volver al índice"),
        "Getting started" to FallbackTranslation("Primi passi", "Erste Schritte", "Primeros pasos"),
        "Home" to FallbackTranslation("Home", "Startseite", "Inicio"),
        "Support details" to FallbackTranslation("Dettagli del supporto", "Supportdetails", "Detalles del soporte"),
        "Site details" to FallbackTranslation("Dettagli del sito", "Standortdetails", "Detalles del sitio"),
        "Elevation profile" to FallbackTranslation("Profilo altimetrico", "Höhenprofil", "Perfil altimétrico"),
        "Throughput calculator" to FallbackTranslation("Calcolatore di velocità", "Durchsatzrechner", "Calculadora de velocidad"),
        "Community photos" to FallbackTranslation("Foto della community", "Community-Fotos", "Fotos comunitarias"),
        "Sharing" to FallbackTranslation("Condivisione", "Teilen", "Compartir"),
        "Database and offline maps" to FallbackTranslation("Database e mappe offline", "Datenbank und Offline-Karten", "Base de datos y mapas sin conexión"),
        "Button glossary and troubleshooting" to FallbackTranslation("Glossario dei pulsanti e risoluzione problemi", "Schaltflächen-Glossar und Fehlerbehebung", "Glosario de botones y solución de problemas"),
        "Home diagram" to FallbackTranslation("Schema della home", "Startseiten-Diagramm", "Esquema de inicio"),
        "Search diagram" to FallbackTranslation("Schema della ricerca", "Suchdiagramm", "Esquema de búsqueda"),
        "Map diagram" to FallbackTranslation("Schema della mappa", "Kartendiagramm", "Esquema del mapa"),
        "Support detail diagram" to FallbackTranslation("Schema del dettaglio supporto", "Diagramm der Supportdetails", "Esquema del detalle del soporte"),
        "Site detail diagram" to FallbackTranslation("Schema del dettaglio sito", "Diagramm der Standortdetails", "Esquema del detalle del sitio"),
        "Elevation profile diagram" to FallbackTranslation("Schema del profilo altimetrico", "Höhenprofil-Diagramm", "Esquema del perfil altimétrico"),
        "Calculator diagram" to FallbackTranslation("Schema del calcolatore", "Rechnerdiagramm", "Esquema de la calculadora"),
        "Photos diagram" to FallbackTranslation("Schema delle foto", "Fotodiagramm", "Esquema de fotos"),
        "Sharing diagram" to FallbackTranslation("Schema della condivisione", "Teilen-Diagramm", "Esquema de compartir"),
        "Settings diagram" to FallbackTranslation("Schema delle impostazioni", "Einstellungsdiagramm", "Esquema de ajustes"),
        "Data diagram" to FallbackTranslation("Schema dei dati", "Datendiagramm", "Esquema de datos"),
        "About screen diagram" to FallbackTranslation("Schema della schermata Informazioni", "Diagramm der Über-Seite", "Esquema de la pantalla Acerca de"),
        "Database and network status banner." to FallbackTranslation("Banner di stato del database e della rete.", "Statusbanner für Datenbank und Netzwerk.", "Banner de estado de la base de datos y la red."),
        "Shortcuts to the main screens." to FallbackTranslation("Scorciatoie verso le schermate principali.", "Verknüpfungen zu den Hauptbildschirmen.", "Accesos a las pantallas principales."),
        "Customizable Help button." to FallbackTranslation("Pulsante Aiuto personalizzabile.", "Anpassbare Hilfe-Schaltfläche.", "Botón Ayuda personalizable."),
        "Free text or code search field." to FallbackTranslation("Campo di ricerca libero o per codice.", "Suchfeld für freien Text oder Code.", "Campo de búsqueda libre o por código."),
        "Quick suggestions and code help." to FallbackTranslation("Suggerimenti rapidi e aiuto sui codici.", "Schnellvorschläge und Codehilfe.", "Sugerencias rápidas y ayuda de códigos."),
        "Results that open details or split screen." to FallbackTranslation("Risultati che aprono dettagli o split screen.", "Ergebnisse, die Details oder geteilte Ansicht öffnen.", "Resultados que abren detalles o pantalla dividida."),
        "City, address or area search." to FallbackTranslation("Ricerca per città, indirizzo o zona.", "Suche nach Stadt, Adresse oder Bereich.", "Búsqueda por ciudad, dirección o zona."),
        "Support and site markers." to FallbackTranslation("Marcatori di supporti e siti.", "Support- und Standortmarker.", "Marcadores de soportes y sitios."),
        "GPS, zoom and orientation buttons." to FallbackTranslation("Pulsanti GPS, zoom e orientamento.", "GPS-, Zoom- und Ausrichtungsschaltflächen.", "Botones de GPS, zoom y orientación."),
        "Physical support summary." to FallbackTranslation("Riepilogo del supporto fisico.", "Zusammenfassung des physischen Supports.", "Resumen del soporte físico."),
        "Linked sites and operators." to FallbackTranslation("Siti e operatori collegati.", "Verknüpfte Standorte und Betreiber.", "Sitios y operadores vinculados."),
        "Map, navigation, sharing and photo actions." to FallbackTranslation("Azioni mappa, navigazione, condivisione e foto.", "Aktionen für Karte, Navigation, Teilen und Fotos.", "Acciones de mapa, navegación, compartir y fotos."),
        "Operator banner and status." to FallbackTranslation("Banner operatore e stato.", "Betreiberbanner und Status.", "Banner del operador y estado."),
        "Frequencies, technologies and azimuths." to FallbackTranslation("Frequenze, tecnologie e azimut.", "Frequenzen, Technologien und Azimute.", "Frecuencias, tecnologías y azimuts."),
        "Tools: profile, throughput, sharing and settings." to FallbackTranslation("Strumenti: profilo, velocità, condivisione e impostazioni.", "Tools: Profil, Durchsatz, Teilen und Einstellungen.", "Herramientas: perfil, velocidad, compartir y ajustes."),
        "Route between your position and the site." to FallbackTranslation("Percorso tra la tua posizione e il sito.", "Route zwischen deiner Position und dem Standort.", "Trayecto entre tu posición y el sitio."),
        "Terrain and estimated radio visibility." to FallbackTranslation("Rilievo e visibilità radio stimata.", "Gelände und geschätzte Funk-Sichtbarkeit.", "Relieve y visibilidad radio estimada."),
        "Immediate or deferred recalculation." to FallbackTranslation("Ricalcolo immediato o differito.", "Sofortige oder spätere Neuberechnung.", "Recalculo inmediato o diferido."),
        "Conservative, ideal or custom mode." to FallbackTranslation("Modalità prudente, ideale o personalizzata.", "Vorsichtiger, idealer oder benutzerdefinierter Modus.", "Modo prudente, ideal o personalizado."),
        "Bands, technologies and modulation." to FallbackTranslation("Bande, tecnologie e modulazione.", "Bänder, Technologien und Modulation.", "Bandas, tecnologías y modulación."),
        "Optimal distance and mini-map." to FallbackTranslation("Distanza ottimale e mini-mappa.", "Optimale Entfernung und Minikarte.", "Distancia óptima y mini mapa."),
        "Available photo carousel." to FallbackTranslation("Carosello delle foto disponibili.", "Karussell verfügbarer Fotos.", "Carrusel de fotos disponibles."),
        "Full screen opening." to FallbackTranslation("Apertura a schermo intero.", "Öffnen im Vollbild.", "Apertura a pantalla completa."),
        "SignalQuest community upload." to FallbackTranslation("Invio comunitario SignalQuest.", "SignalQuest-Community-Upload.", "Envío comunitario SignalQuest."),
        "Checkboxes for included blocks." to FallbackTranslation("Caselle per i blocchi inclusi.", "Kontrollkästchen für enthaltene Blöcke.", "Casillas de bloques incluidos."),
        "Generated image preview." to FallbackTranslation("Anteprima dell'immagine generata.", "Vorschau des erzeugten Bildes.", "Vista previa de la imagen generada."),
        "Export or Android sharing." to FallbackTranslation("Esportazione o condivisione Android.", "Export oder Android-Teilen.", "Exportación o compartir desde Android."),
        "Side sections or page mode." to FallbackTranslation("Sezioni laterali o modalità pagine.", "Seitliche Bereiche oder Seitenmodus.", "Secciones laterales o modo por páginas."),
        "Quick app preferences." to FallbackTranslation("Preferenze rapide dell'app.", "Schnelle App-Einstellungen.", "Preferencias rápidas de la app."),
        "Page and button customization." to FallbackTranslation("Personalizzazione di pagine e pulsanti.", "Anpassung von Seiten und Schaltflächen.", "Personalización de páginas y botones."),
        "ANFR database download." to FallbackTranslation("Download del database ANFR.", "ANFR-Datenbankdownload.", "Descarga de la base ANFR."),
        "Available offline maps." to FallbackTranslation("Mappe offline disponibili.", "Verfügbare Offline-Karten.", "Mapas sin conexión disponibles."),
        "Notifications leading to the right section." to FallbackTranslation("Notifiche che portano alla sezione corretta.", "Benachrichtigungen führen zum richtigen Bereich.", "Notificaciones que llevan a la sección correcta."),
        "Section navigation." to FallbackTranslation("Navigazione tra sezioni.", "Bereichsnavigation.", "Navegación por secciones."),
        "App and data version." to FallbackTranslation("Versione dell'app e dei dati.", "App- und Datenversion.", "Versión de la app y los datos."),
        "Sources, development and useful links." to FallbackTranslation("Fonti, sviluppo e link utili.", "Quellen, Entwicklung und nützliche Links.", "Fuentes, desarrollo y enlaces útiles."),
        "Search field" to FallbackTranslation("Campo di ricerca", "Suchfeld", "Campo de búsqueda"),
        "X / Clear" to FallbackTranslation("X / Cancella", "X / Löschen", "X / Borrar"),
        "Quick suggestions" to FallbackTranslation("Suggerimenti rapidi", "Schnellvorschläge", "Sugerencias rápidas"),
        "Info" to FallbackTranslation("Info", "Info", "Info"),
        "Show more" to FallbackTranslation("Mostra altro", "Mehr anzeigen", "Mostrar más"),
        "Expand area" to FallbackTranslation("Espandi zona", "Bereich erweitern", "Ampliar zona"),
        "Location" to FallbackTranslation("Localizzazione", "Standort", "Ubicación"),
        "Coordinates" to FallbackTranslation("Coordinate", "Koordinaten", "Coordenadas"),
        "Map compass" to FallbackTranslation("Bussola della mappa", "Kartenkompass", "Brújula del mapa"),
        "Scale" to FallbackTranslation("Scala", "Maßstab", "Escala"),
        // Paramètres, apparence, cartes et personnalisation.
        "System language" to FallbackTranslation("Lingua di sistema", "Systemsprache", "Idioma del sistema"),
        "IGN (Gov)" to FallbackTranslation("IGN (Gov)", "IGN (Behörde)", "IGN (Gob.)"),
        "Map Style" to FallbackTranslation("Stile mappa", "Kartenstil", "Estilo del mapa"),
        "Navigation mode in settings" to FallbackTranslation("Modalità di navigazione nelle impostazioni", "Navigationsmodus in den Einstellungen", "Modo de navegación en ajustes"),
        "Continuous scroll" to FallbackTranslation("Scorrimento continuo", "Fortlaufendes Scrollen", "Desplazamiento continuo"),
        "Page system" to FallbackTranslation("Sistema a pagine", "Seitensystem", "Sistema por páginas"),
        "Scrolling" to FallbackTranslation("Scorrimento", "Scrollen", "Desplazamiento"),
        "All options on one page" to FallbackTranslation("Tutte le opzioni in una pagina", "Alle Optionen auf einer Seite", "Todas las opciones en una página"),
        "Pages" to FallbackTranslation("Pagine", "Seiten", "Páginas"),
        "Show one category at a time" to FallbackTranslation("Mostra una categoria alla volta", "Eine Kategorie auf einmal anzeigen", "Mostrar una categoría cada vez"),
        "One UI Interface" to FallbackTranslation("Interfaccia One UI", "One-UI-Oberfläche", "Interfaz One UI"),
        "Default Operator" to FallbackTranslation("Operatore predefinito", "Standardbetreiber", "Operador predeterminado"),
        "Mainland France" to FallbackTranslation("Francia metropolitana", "Französisches Festland", "Francia metropolitana"),
        "Overseas" to FallbackTranslation("Oltremare", "Übersee", "Ultramar"),
        "Select all" to FallbackTranslation("Seleziona tutto", "Alle auswählen", "Seleccionar todo"),
        "Clear all" to FallbackTranslation("Deseleziona tutto", "Alle abwählen", "Deseleccionar todo"),
        "None" to FallbackTranslation("Nessuno", "Keine", "Ninguno"),
        "Select" to FallbackTranslation("Seleziona", "Auswählen", "Seleccionar"),
        "Up to date" to FallbackTranslation("Aggiornato", "Aktuell", "Actualizado"),
        "Manage Permissions" to FallbackTranslation("Gestisci autorizzazioni", "Berechtigungen verwalten", "Gestionar permisos"),
        "Location and Notifications" to FallbackTranslation("Localizzazione e notifiche", "Standort und Benachrichtigungen", "Ubicación y notificaciones"),
        "Download the entire database to use the list offline. Warning : large file." to FallbackTranslation("Scarica l'intero database per usare l'elenco offline. Attenzione: file grande.", "Lade die gesamte Datenbank herunter, um die Liste offline zu nutzen. Warnung: große Datei.", "Descarga toda la base de datos para usar la lista sin conexión. Atención: archivo grande."),
        "Download antennas" to FallbackTranslation("Scarica antenne", "Antennen herunterladen", "Descargar antenas"),
        "Cancel download" to FallbackTranslation("Annulla download", "Download abbrechen", "Cancelar descarga"),
        "Main menu size" to FallbackTranslation("Dimensione menu principale", "Größe des Hauptmenüs", "Tamaño del menú principal"),
        "Small" to FallbackTranslation("Piccolo", "Klein", "Pequeño"),
        "Normal" to FallbackTranslation("Normale", "Normal", "Normal"),
        "Large" to FallbackTranslation("Grande", "Groß", "Grande"),
        "Widget refresh rate" to FallbackTranslation("Aggiornamento widget", "Widget-Aktualisierung", "Actualización del widget"),
        "Navigation style" to FallbackTranslation("Stile di navigazione", "Navigationsstil", "Estilo de navegación"),
        "Pylon details (Support)" to FallbackTranslation("Dettagli pilone (supporto)", "Mastdetails (Support)", "Detalles del pilón (soporte)"),
        "Antenna details (Site)" to FallbackTranslation("Dettagli antenna (sito)", "Antennendetails (Standort)", "Detalles de la antena (sitio)"),
        "Pylon share" to FallbackTranslation("Condivisione pilone", "Mast teilen", "Compartir pilón"),
        "Offline" to FallbackTranslation("Offline", "Offline", "Sin conexión"),
        "Pages customization" to FallbackTranslation("Personalizzazione pagine", "Seiten anpassen", "Personalización de páginas"),
        "Customize the display of the different pages of the application" to FallbackTranslation("Personalizza la visualizzazione delle diverse pagine dell'app", "Passe die Anzeige der verschiedenen App-Seiten an", "Personaliza la visualización de las distintas páginas de la app"),
        "Startup page" to FallbackTranslation("Pagina di avvio", "Startseite beim Öffnen", "Página de inicio"),
        "Home page" to FallbackTranslation("Pagina iniziale", "Startseite", "Página principal"),
        "Help button position" to FallbackTranslation("Posizione del pulsante Aiuto", "Position der Hilfe-Schaltfläche", "Posición del botón Ayuda"),
        "Top left" to FallbackTranslation("In alto a sinistra", "Oben links", "Arriba a la izquierda"),
        "Top right" to FallbackTranslation("In alto a destra", "Oben rechts", "Arriba a la derecha"),
        "Bottom left" to FallbackTranslation("In basso a sinistra", "Unten links", "Abajo a la izquierda"),
        "Bottom right" to FallbackTranslation("In basso a destra", "Unten rechts", "Abajo a la derecha"),
        "Search bar" to FallbackTranslation("Barra di ricerca", "Suchleiste", "Barra de búsqueda"),
        "Nearest sites" to FallbackTranslation("Siti più vicini", "Nächste Standorte", "Sitios más cercanos"),
        "Search radius" to FallbackTranslation("Raggio di ricerca", "Suchradius", "Radio de búsqueda"),
        "Accuracy" to FallbackTranslation("Precisione", "Genauigkeit", "Precisión"),
        "Location button" to FallbackTranslation("Pulsante localizzazione", "Standort-Schaltfläche", "Botón de ubicación"),
        "GPS dot" to FallbackTranslation("Punto GPS", "GPS-Punkt", "Punto GPS"),
        "Azimuths" to FallbackTranslation("Azimut", "Azimute", "Azimuts"),
        "Azimuth lines" to FallbackTranslation("Linee di azimut", "Azimutlinien", "Líneas de azimut"),
        "Azimuth cones" to FallbackTranslation("Coni di azimut", "Azimutkegel", "Conos de azimut"),
        "Zoom buttons" to FallbackTranslation("Pulsanti zoom", "Zoom-Schaltflächen", "Botones de zoom"),
        "Toolbox" to FallbackTranslation("Strumenti", "Werkzeugbox", "Herramientas"),
        "Map scale" to FallbackTranslation("Scala mappa", "Kartenmaßstab", "Escala del mapa"),
        "Credits (Attribution)" to FallbackTranslation("Crediti (attribuzione)", "Credits (Zuordnung)", "Créditos (atribución)"),
        "Reset to default settings" to FallbackTranslation("Ripristina impostazioni predefinite", "Standardeinstellungen wiederherstellen", "Restablecer ajustes predeterminados"),
        "Community photos and diagrams" to FallbackTranslation("Foto e schemi della community", "Community-Fotos und Diagramme", "Fotos comunitarias y esquemas"),
        "Photos and diagrams settings" to FallbackTranslation("Impostazioni foto e schemi", "Foto- und Diagrammeinstellungen", "Ajustes de fotos y esquemas"),
        "Show CellularFR photos" to FallbackTranslation("Mostra foto CellularFR", "CellularFR-Fotos anzeigen", "Mostrar fotos de CellularFR"),
        "Show SignalQuest photos" to FallbackTranslation("Mostra foto SignalQuest", "SignalQuest-Fotos anzeigen", "Mostrar fotos de SignalQuest"),
        "Show support diagrams" to FallbackTranslation("Mostra schemi del supporto", "Supportdiagramme anzeigen", "Mostrar esquemas del soporte"),
        "Show EXIF" to FallbackTranslation("Mostra EXIF", "EXIF anzeigen", "Mostrar EXIF"),
        "Mini-map" to FallbackTranslation("Mini-mappa", "Minikarte", "Mini mapa"),
        "Open map button" to FallbackTranslation("Pulsante Apri mappa", "Schaltfläche Karte öffnen", "Botón Abrir mapa"),
        "Navigate button" to FallbackTranslation("Pulsante Naviga", "Schaltfläche Navigieren", "Botón Navegar"),
        "Share button" to FallbackTranslation("Pulsante Condividi", "Schaltfläche Teilen", "Botón Compartir"),
        "Operators list" to FallbackTranslation("Elenco operatori", "Betreiberliste", "Lista de operadores"),
        "Operator banner" to FallbackTranslation("Banner operatore", "Betreiberbanner", "Banner del operador"),
        "Bearing and Height" to FallbackTranslation("Direzione e altezza", "Richtung und Höhe", "Rumbo y altura"),
        "Identifiers" to FallbackTranslation("Identificativi", "Kennungen", "Identificadores"),
        "Elevation profile button" to FallbackTranslation("Pulsante profilo altimetrico", "Schaltfläche Höhenprofil", "Botón Perfil altimétrico"),
        "Throughput calculator button" to FallbackTranslation("Pulsante calcolatore velocità", "Schaltfläche Durchsatzrechner", "Botón Calculadora de velocidad"),
        "Activation dates" to FallbackTranslation("Date di attivazione", "Aktivierungsdaten", "Fechas de activación"),
        "Address & Coordinates" to FallbackTranslation("Indirizzo e coordinate", "Adresse und Koordinaten", "Dirección y coordenadas"),
        "Frequencies, Spectrum & Azimuths" to FallbackTranslation("Frequenze, spettro e azimut", "Frequenzen, Spektrum und Azimute", "Frecuencias, espectro y azimuts"),
        "External links" to FallbackTranslation("Link esterni", "Externe Links", "Enlaces externos"),
        "Community data" to FallbackTranslation("Dati comunitari", "Community-Daten", "Datos comunitarios"),
        "Choose operators, sources, and display order for photos and speedtests." to FallbackTranslation("Scegli operatori, fonti e ordine di visualizzazione per foto e speedtest.", "Wähle Betreiber, Quellen und Anzeigereihenfolge für Fotos und Speedtests.", "Elige operadores, fuentes y orden de visualización de fotos y speedtests."),
        "Photos" to FallbackTranslation("Foto", "Fotos", "Fotos"),
        "Show photos for this operator" to FallbackTranslation("Mostra foto per questo operatore", "Fotos für diesen Betreiber anzeigen", "Mostrar fotos de este operador"),
        "Source order" to FallbackTranslation("Ordine delle fonti", "Quellenreihenfolge", "Orden de fuentes"),
        "Show only if sources above have no photos" to FallbackTranslation("Mostra solo se le fonti sopra non hanno foto", "Nur anzeigen, wenn die Quellen darüber keine Fotos haben", "Mostrar solo si las fuentes superiores no tienen fotos"),
        "Manage the order and display of external shortcuts" to FallbackTranslation("Gestisci ordine e visualizzazione delle scorciatoie esterne", "Reihenfolge und Anzeige externer Verknüpfungen verwalten", "Gestionar el orden y la visualización de accesos externos"),
        "Reset settings" to FallbackTranslation("Reimposta impostazioni", "Einstellungen zurücksetzen", "Restablecer ajustes"),
        "Warning" to FallbackTranslation("Attenzione", "Warnung", "Atención"),
        "Yes" to FallbackTranslation("Sì", "Ja", "Sí"),
        "No" to FallbackTranslation("No", "Nein", "No"),
        "You are offline" to FallbackTranslation("Sei offline", "Du bist offline", "Estás sin conexión"),
        "App logo" to FallbackTranslation("Logo app", "App-Logo", "Logo de la app"),
        "Home page logo" to FallbackTranslation("Logo della home", "Logo der Startseite", "Logo de la página principal"),
        "Application" to FallbackTranslation("Applicazione", "Anwendung", "Aplicación"),
        "Calculating size..." to FallbackTranslation("Calcolo dimensione...", "Größe wird berechnet...", "Calculando tamaño..."),
        "Unknown size" to FallbackTranslation("Dimensione sconosciuta", "Unbekannte Größe", "Tamaño desconocido"),
        "Speedometer" to FallbackTranslation("Tachimetro", "Tachometer", "Velocímetro"),
        "Antenna frequency filters" to FallbackTranslation("Filtri frequenze antenna", "Antennenfrequenzfilter", "Filtros de frecuencias de la antena"),
        "Display frequencies in a grid" to FallbackTranslation("Mostra frequenze in griglia", "Frequenzen als Raster anzeigen", "Mostrar frecuencias en una cuadrícula"),
        "Emitters" to FallbackTranslation("Emettitori", "Sender", "Emisores"),
        "Antennas" to FallbackTranslation("Antenne", "Antennen", "Antenas"),
        "Band" to FallbackTranslation("Banda", "Band", "Banda"),
        "In service" to FallbackTranslation("In servizio", "In Betrieb", "En servicio"),
        "State" to FallbackTranslation("Stato", "Status", "Estado"),
        "Azimuth" to FallbackTranslation("Azimut", "Azimut", "Azimut"),
        "Height" to FallbackTranslation("Altezza", "Höhe", "Altura"),
        "Frequencies" to FallbackTranslation("Frequenze", "Frequenzen", "Frecuencias"),
        "Show azimuths" to FallbackTranslation("Mostra azimut", "Azimute anzeigen", "Mostrar azimuts"),
        "Elevation Profile" to FallbackTranslation("Profilo altimetrico", "Höhenprofil", "Perfil altimétrico"),
        "Calculating elevation profile..." to FallbackTranslation("Calcolo del profilo altimetrico...", "Höhenprofil wird berechnet...", "Calculando perfil altimétrico..."),
        "Terrain and Fresnel zone analysis in progress." to FallbackTranslation("Analisi del rilievo e della zona di Fresnel in corso.", "Gelände- und Fresnelzonenanalyse läuft.", "Análisis del relieve y de la zona de Fresnel en curso."),
        "User location unavailable. Enable location and try again." to FallbackTranslation("Posizione utente non disponibile. Attiva la localizzazione e riprova.", "Benutzerstandort nicht verfügbar. Standort aktivieren und erneut versuchen.", "Ubicación del usuario no disponible. Activa la ubicación e inténtalo de nuevo."),
        "Site not found for this profile." to FallbackTranslation("Sito non trovato per questo profilo.", "Standort für dieses Profil nicht gefunden.", "Sitio no encontrado para este perfil."),
        "Unable to load elevation profile." to FallbackTranslation("Impossibile caricare il profilo altimetrico.", "Höhenprofil kann nicht geladen werden.", "No se puede cargar el perfil altimétrico."),
        "Elevation profile unavailable offline" to FallbackTranslation("Profilo altimetrico non disponibile offline", "Höhenprofil offline nicht verfügbar", "Perfil altimétrico no disponible sin conexión"),
        "Calculate later" to FallbackTranslation("Calcola più tardi", "Später berechnen", "Calcular más tarde"),
        "Calculation saved for later" to FallbackTranslation("Calcolo salvato per dopo", "Berechnung für später gespeichert", "Cálculo guardado para más tarde"),
        "Saved profile available" to FallbackTranslation("Profilo salvato disponibile", "Gespeichertes Profil verfügbar", "Perfil guardado disponible"),
        "Show" to FallbackTranslation("Mostra", "Anzeigen", "Mostrar"),
        "Do not show" to FallbackTranslation("Non mostrare", "Nicht anzeigen", "No mostrar"),
        "Distance" to FallbackTranslation("Distanza", "Entfernung", "Distancia"),
        "Panel height" to FallbackTranslation("Altezza pannello", "Panelhöhe", "Altura del panel"),
        "Start height" to FallbackTranslation("Altezza iniziale", "Starthöhe", "Altura inicial"),
        "Arrival height" to FallbackTranslation("Altezza di arrivo", "Ankunftshöhe", "Altura de llegada"),
        "Frequency" to FallbackTranslation("Frequenza", "Frequenz", "Frecuencia"),
        "Direct signal path" to FallbackTranslation("Percorso diretto del segnale", "Direkter Signalweg", "Trayecto directo de la señal"),
        "Fresnel zone" to FallbackTranslation("Zona di Fresnel", "Fresnelzone", "Zona de Fresnel"),
        "Theoretical throughput" to FallbackTranslation("Velocità teorica", "Theoretischer Durchsatz", "Velocidad teórica"),
        "Throughput Calculator" to FallbackTranslation("Calcolatore di velocità", "Durchsatzrechner", "Calculadora de velocidad"),
        "Site not found for this calculation." to FallbackTranslation("Sito non trovato per questo calcolo.", "Standort für diese Berechnung nicht gefunden.", "Sitio no encontrado para este cálculo."),
        "Estimated theoretical radio throughput" to FallbackTranslation("Velocità radio teorica stimata", "Geschätzter theoretischer Funkdurchsatz", "Velocidad radio teórica estimada"),
        "Download" to FallbackTranslation("Download", "Download", "Descarga"),
        "Upload (phone)" to FallbackTranslation("Upload (telefono)", "Upload (Telefon)", "Subida (teléfono)"),
        "Estimated optimal distance" to FallbackTranslation("Distanza ottimale stimata", "Geschätzte optimale Entfernung", "Distancia óptima estimada"),
        "Radio assumption" to FallbackTranslation("Ipotesi radio", "Funkannahme", "Hipótesis radio"),
        "Include planned" to FallbackTranslation("Includi progetti", "Geplante einschließen", "Incluir proyectos"),
        "Included bands" to FallbackTranslation("Bande incluse", "Einbezogene Bänder", "Bandas incluidas"),
        "Custom modulation" to FallbackTranslation("Modulazione personalizzata", "Benutzerdefinierte Modulation", "Modulación personalizada"),
        "Frequencies and modulation" to FallbackTranslation("Frequenze e modulazione", "Frequenzen und Modulation", "Frecuencias y modulación"),
        "(estimated)" to FallbackTranslation("(stimato)", "(geschätzt)", "(estimado)"),
        "Modulation and antennas" to FallbackTranslation("Modulazione e antenne", "Modulation und Antennen", "Modulación y antenas"),
        "Read as an estimate" to FallbackTranslation("Da leggere come stima", "Als Schätzung lesen", "Leer como una estimación"),
        "Conservative" to FallbackTranslation("Prudente", "Vorsichtig", "Prudente"),
        "Ideal" to FallbackTranslation("Ideale", "Ideal", "Ideal"),
        "Custom" to FallbackTranslation("Personalizzato", "Benutzerdefiniert", "Personalizado"),
        "Standard" to FallbackTranslation("Standard", "Standard", "Estándar"),
        "Phone upload" to FallbackTranslation("Upload telefono", "Telefon-Upload", "Subida tel."),
        "Sources and warnings" to FallbackTranslation("Fonti e avvisi", "Quellen und Warnungen", "Fuentes y advertencias"),
        "Calculation settings" to FallbackTranslation("Impostazioni di calcolo", "Berechnungseinstellungen", "Ajustes de cálculo"),
        "Default calculation mode" to FallbackTranslation("Modalità di calcolo predefinita", "Standard-Berechnungsmodus", "Modo de cálculo predeterminado"),
        "Include 4G" to FallbackTranslation("Includi 4G", "4G einschließen", "Incluir 4G"),
        "Include 5G" to FallbackTranslation("Includi 5G", "5G einschließen", "Incluir 5G"),
        "Default frequency bands" to FallbackTranslation("Bande di frequenza predefinite", "Standard-Frequenzbänder", "Bandas de frecuencia predeterminadas"),
        "Important notes" to FallbackTranslation("Note importanti", "Wichtige Hinweise", "Notas importantes"),
        "Measured signal" to FallbackTranslation("Segnale misurato", "Gemessenes Signal", "Señal medida"),
        "Environment" to FallbackTranslation("Ambiente", "Umgebung", "Entorno"),
        "Position" to FallbackTranslation("Posizione", "Position", "Posición"),
        "Use my current position" to FallbackTranslation("Usa la mia posizione attuale", "Meinen aktuellen Standort verwenden", "Usar mi ubicación actual"),
        "Choose a point on the map" to FallbackTranslation("Scegli un punto sulla mappa", "Punkt auf der Karte wählen", "Elegir un punto en el mapa"),
        "Remove location from calculation" to FallbackTranslation("Rimuovi posizione dal calcolo", "Standort aus der Berechnung entfernen", "Quitar ubicación del cálculo"),
        "Current position used for the analysis." to FallbackTranslation("Posizione attuale usata per l'analisi.", "Aktueller Standort wird für die Analyse verwendet.", "Ubicación actual usada para el análisis."),
        "Map point used for the analysis." to FallbackTranslation("Punto sulla mappa usato per l'analisi.", "Kartenpunkt wird für die Analyse verwendet.", "Punto del mapa usado para el análisis."),
        "Location removed from the calculation." to FallbackTranslation("Localizzazione rimossa dal calcolo.", "Standort aus der Berechnung entfernt.", "Ubicación quitada del cálculo."),
        "Current position is unavailable for now." to FallbackTranslation("La posizione attuale non è disponibile al momento.", "Der aktuelle Standort ist momentan nicht verfügbar.", "La ubicación actual no está disponible por ahora."),
        "Locating..." to FallbackTranslation("Localizzazione...", "Standort wird gesucht...", "Localizando..."),
        "Network and aggregation" to FallbackTranslation("Rete e aggregazione", "Netz und Aggregation", "Red y agregación"),
        "Network load" to FallbackTranslation("Carico rete", "Netzlast", "Carga de red"),
        "4G aggregation" to FallbackTranslation("Aggregazione 4G", "4G-Aggregation", "Agregación 4G"),
        "Outdoor" to FallbackTranslation("Esterno", "Draußen", "Exterior"),
        "Vehicle" to FallbackTranslation("Veicolo", "Fahrzeug", "Vehículo"),
        "Indoor" to FallbackTranslation("Interno", "Innenbereich", "Interior"),
        "Deep indoor" to FallbackTranslation("Interno profondo", "Tief im Gebäude", "Interior profundo"),
        "Unknown" to FallbackTranslation("Sconosciuto", "Unbekannt", "Desconocido"),
        "In the beam" to FallbackTranslation("Nel fascio", "Im Strahl", "En el haz"),
        "Too close" to FallbackTranslation("Troppo vicino", "Zu nah", "Demasiado cerca"),
        "Too far" to FallbackTranslation("Troppo lontano", "Zu weit", "Demasiado lejos"),
        "Outside beam" to FallbackTranslation("Fuori fascio", "Außerhalb des Strahls", "Fuera del haz"),
        "Light" to FallbackTranslation("Leggero", "Gering", "Baja"),
        "Medium" to FallbackTranslation("Medio", "Mittel", "Media"),
        "Heavy" to FallbackTranslation("Forte", "Hoch", "Alta"),
        "Saturated" to FallbackTranslation("Satura", "Ausgelastet", "Saturada"),
        "Fiber / very good" to FallbackTranslation("Fibra / molto buono", "Glasfaser / sehr gut", "Fibra / muy bueno"),
        "Microwave link" to FallbackTranslation("Ponte radio", "Richtfunkstrecke", "Enlace radio"),
        "Limited" to FallbackTranslation("Limitato", "Begrenzt", "Limitado"),
        "Realistic" to FallbackTranslation("Realistico", "Realistisch", "Realista"),
        "Wide" to FallbackTranslation("Ampia", "Breit", "Amplia"),
        "Impact on the calculation" to FallbackTranslation("Impatto sul calcolo", "Auswirkung auf die Berechnung", "Impacto en el cálculo"),
        "Modulation" to FallbackTranslation("Modulazione", "Modulation", "Modulación"),
        "New database" to FallbackTranslation("Nuovo database", "Neue Datenbank", "Nueva base de datos"),
        "An antenna update is available. Tap to open the download section." to FallbackTranslation("È disponibile un aggiornamento delle antenne. Tocca per aprire la sezione download.", "Ein Antennen-Update ist verfügbar. Tippe, um den Download-Bereich zu öffnen.", "Hay una actualización de antenas disponible. Toca para abrir la sección de descarga."),
        "New GeoTower version" to FallbackTranslation("Nuova versione GeoTower", "Neue GeoTower-Version", "Nueva versión de GeoTower"),
        "GeoTower updates" to FallbackTranslation("Aggiornamenti GeoTower", "GeoTower-Updates", "Actualizaciones de GeoTower"),
        "Nearby" to FallbackTranslation("Vicino", "In der Nähe", "Cerca"),
        "Live antenna tracking" to FallbackTranslation("Monitoraggio antenne in tempo reale", "Live-Antennenverfolgung", "Seguimiento de antenas en directo"),
        "Searching..." to FallbackTranslation("Ricerca...", "Suche läuft...", "Buscando..."),
        "Stop" to FallbackTranslation("Esci", "Beenden", "Salir"),
        "Error" to FallbackTranslation("Errore", "Fehler", "Error"),
        "Database Update" to FallbackTranslation("Aggiornamento database", "Datenbank-Update", "Actualización de base de datos"),
        "Database update" to FallbackTranslation("Aggiornamento database", "Datenbankaktualisierung", "Actualización de la base"),
        "Database" to FallbackTranslation("Database", "Datenbank", "Base de datos"),
        "Database downloaded. Tap to open." to FallbackTranslation("Database scaricato. Tocca per aprire.", "Datenbank heruntergeladen. Zum Öffnen tippen.", "Base de datos descargada. Toca para abrir."),
        "Download failed. Please check your connection." to FallbackTranslation("Download non riuscito. Controlla la connessione.", "Download fehlgeschlagen. Bitte Verbindung prüfen.", "Descarga fallida. Comprueba tu conexión."),
        "Database updates" to FallbackTranslation("Aggiornamenti database", "Datenbank-Updates", "Actualizaciones de base de datos"),
        "Maps download" to FallbackTranslation("Download mappe", "Kartendownload", "Descarga de mapas"),
        "Map downloaded" to FallbackTranslation("Mappa scaricata", "Karte heruntergeladen", "Mapa descargado"),
        "Signal Quest upload" to FallbackTranslation("Invio Signal Quest", "Signal-Quest-Upload", "Envío Signal Quest"),
        "Network error, retrying later." to FallbackTranslation("Errore di rete, nuovo tentativo più tardi.", "Netzwerkfehler, später erneuter Versuch.", "Error de red, se reintentará más tarde."),
        "📍 Nearby antennas" to FallbackTranslation("📍 Antenne vicine", "📍 Antennen in der Nähe", "📍 Antenas cercanas"),
        "Waiting for GPS..." to FallbackTranslation("In attesa del GPS...", "Warte auf GPS...", "Esperando GPS..."),
        "GeoTower is connected to Android Auto." to FallbackTranslation("GeoTower è connesso ad Android Auto.", "GeoTower ist mit Android Auto verbunden.", "GeoTower está conectado a Android Auto."),
        "Nearby sites" to FallbackTranslation("Siti vicini", "Standorte in der Nähe", "Sitios cercanos"),
        "Sites around me" to FallbackTranslation("Siti intorno a me", "Standorte um mich herum", "Sitios a mi alrededor"),
        "No site found around your position." to FallbackTranslation("Nessun sito trovato intorno alla tua posizione.", "Kein Standort in deiner Nähe gefunden.", "No se ha encontrado ningún sitio cerca de tu posición."),
        "Try again" to FallbackTranslation("Riprova", "Erneut versuchen", "Reintentar"),
        "Searching nearby sites..." to FallbackTranslation("Ricerca dei siti vicini...", "Suche nach Standorten in der Nähe...", "Buscando sitios cercanos..."),
        "Position unavailable for now." to FallbackTranslation("Posizione al momento non disponibile.", "Position momentan nicht verfügbar.", "Posición no disponible por ahora."),
        "Allow location in GeoTower on the phone." to FallbackTranslation("Autorizza la localizzazione in GeoTower sul telefono.", "Erlaube GeoTower den Standortzugriff auf dem Telefon.", "Autoriza la ubicación en GeoTower en el teléfono."),
        "GeoTower needs location to show sites around you." to FallbackTranslation("GeoTower ha bisogno della posizione per mostrare i siti intorno a te.", "GeoTower benötigt den Standort, um Standorte in deiner Nähe anzuzeigen.", "GeoTower necesita la ubicación para mostrar sitios a tu alrededor."),
        "Location required" to FallbackTranslation("Localizzazione richiesta", "Standort erforderlich", "Ubicación requerida"),
        "Open app" to FallbackTranslation("Apri app", "App öffnen", "Abrir app"),
        "Operators" to FallbackTranslation("Operatori", "Betreiber", "Operadores"),
        "Address" to FallbackTranslation("Indirizzo", "Adresse", "Dirección"),
        "Navigate" to FallbackTranslation("Naviga", "Navigieren", "Navegar"),
        "Live Notification" to FallbackTranslation("Notifica live", "Live-Benachrichtigung", "Notificación en directo"),
        "Enable real-time notifications" to FallbackTranslation("Attiva le notifiche in tempo reale", "Echtzeitbenachrichtigungen aktivieren", "Activar notificaciones en tiempo real"),
        "Update notifications" to FallbackTranslation("Notifiche di aggiornamento", "Update-Benachrichtigungen", "Notificaciones de actualización"),
        "Get alerted when a new database or APK is available" to FallbackTranslation("Ricevi un avviso quando è disponibile un nuovo database o APK", "Benachrichtigen, wenn eine neue Datenbank oder APK verfügbar ist", "Recibe una alerta cuando haya una nueva base de datos o APK"),
        "Requires choosing a default operator" to FallbackTranslation("Richiede la scelta di un operatore predefinito", "Erfordert einen Standardbetreiber", "Requiere elegir un operador predeterminado"),
        "You must keep at least one mobile technology (2G, 3G, 4G, or 5G)." to FallbackTranslation("Devi mantenere almeno una tecnologia mobile (2G, 3G, 4G o 5G).", "Du musst mindestens eine Mobilfunktechnologie behalten (2G, 3G, 4G oder 5G).", "Debes mantener al menos una tecnología móvil (2G, 3G, 4G o 5G)."),
        "You must keep at least one frequency." to FallbackTranslation("Devi mantenere almeno una frequenza.", "Du musst mindestens eine Frequenz behalten.", "Debes mantener al menos una frecuencia."),
        "Weekly data currently downloaded:" to FallbackTranslation("Dati settimanali attualmente scaricati:", "Aktuell heruntergeladene Wochendaten:", "Datos semanales descargados actualmente:"),
        "No database installed" to FallbackTranslation("Nessun database installato", "Keine Datenbank installiert", "No hay base de datos instalada"),
        "Invalid local database" to FallbackTranslation("Database locale non valido", "Ungültige lokale Datenbank", "Base local no válida"),
        "Old version (Undated)" to FallbackTranslation("Vecchia versione (senza data)", "Alte Version (ohne Datum)", "Versión antigua (sin fecha)"),
        "Latest database:" to FallbackTranslation("Ultimo database:", "Neueste Datenbank:", "Última base:"),
        "Currently downloaded:" to FallbackTranslation("Attualmente scaricato:", "Aktuell heruntergeladen:", "Descargada actualmente:"),
        "Delete data" to FallbackTranslation("Elimina dati", "Daten löschen", "Eliminar datos"),
        "Are you sure you want to delete the database?" to FallbackTranslation("Vuoi davvero eliminare il database?", "Möchtest du die Datenbank wirklich löschen?", "¿Seguro que quieres eliminar la base de datos?"),
        // Base ANFR, cartes hors ligne et mises à jour.
        "Offline Maps" to FallbackTranslation("Mappe offline", "Offline-Karten", "Mapas sin conexión"),
        "Download maps of France to navigate without an internet connection." to FallbackTranslation("Scarica le mappe della Francia per navigare senza connessione internet.", "Lade Frankreich-Karten herunter, um ohne Internetverbindung zu navigieren.", "Descarga mapas de Francia para navegar sin conexión a internet."),
        "Delete map?" to FallbackTranslation("Eliminare la mappa?", "Karte löschen?", "¿Eliminar mapa?"),
        "Do you really want to delete this map from your device?" to FallbackTranslation("Vuoi davvero eliminare questa mappa dal dispositivo?", "Möchtest du diese Karte wirklich von deinem Gerät löschen?", "¿Seguro que quieres eliminar este mapa de tu dispositivo?"),
        "Download All" to FallbackTranslation("Scarica tutto", "Alle herunterladen", "Descargar todo"),
        "Delete All" to FallbackTranslation("Elimina tutto", "Alle löschen", "Eliminar todo"),
        "Delete all maps?" to FallbackTranslation("Eliminare tutte le mappe?", "Alle Karten löschen?", "¿Eliminar todos los mapas?"),
        "Do you really want to delete all downloaded maps?" to FallbackTranslation("Vuoi davvero eliminare tutte le mappe scaricate?", "Möchtest du wirklich alle heruntergeladenen Karten löschen?", "¿Seguro que quieres eliminar todos los mapas descargados?"),
        "Split share image" to FallbackTranslation("Dividi immagine di condivisione", "Teilen-Bild aufteilen", "Dividir imagen compartida"),
        "Separates frequencies on a 2nd image" to FallbackTranslation("Separa le frequenze in una seconda immagine", "Trennt Frequenzen auf ein zweites Bild", "Separa las frecuencias en una segunda imagen"),
        "Units of measure" to FallbackTranslation("Unità di misura", "Maßeinheiten", "Unidades de medida"),
        "Distance and speed" to FallbackTranslation("Distanza e velocità", "Entfernung und Geschwindigkeit", "Distancia y velocidad"),
        "Speed :" to FallbackTranslation("Velocità: ", "Geschwindigkeit: ", "Velocidad: "),
        "Kilometers (km)" to FallbackTranslation("Chilometri (km)", "Kilometer (km)", "Kilómetros (km)"),
        "Miles (mi)" to FallbackTranslation("Miglia (mi)", "Meilen (mi)", "Millas (mi)"),
        "Kilometers/hour (km/h)" to FallbackTranslation("Chilometri/ora (km/h)", "Kilometer/Stunde (km/h)", "Kilómetros/hora (km/h)"),
        "Miles/hour (mph)" to FallbackTranslation("Miglia/ora (mph)", "Meilen/Stunde (mph)", "Millas/hora (mph)"),
        "Nearby Emitters" to FallbackTranslation("Emettitori vicini", "Sender in der Nähe", "Emisores cercanos"),
        "Searching for GPS position..." to FallbackTranslation("Ricerca posizione GPS...", "GPS-Position wird gesucht...", "Buscando posición GPS..."),
        "No sites found." to FallbackTranslation("Nessun sito trovato.", "Keine Standorte gefunden.", "No se han encontrado sitios."),
        "Load more sites" to FallbackTranslation("Carica altri siti", "Mehr Standorte laden", "Cargar más sitios"),
        "City" to FallbackTranslation("Città", "Stadt", "Ciudad"),
        "Pylon" to FallbackTranslation("Pilone", "Mast", "Pilón"),
        "Roof" to FallbackTranslation("Tetto", "Dach", "Tejado"),
        "Postal code" to FallbackTranslation("Codice postale", "Postleitzahl", "Código postal"),
        "Search help" to FallbackTranslation("Aiuto ricerca", "Suchhilfe", "Ayuda de búsqueda"),
        "Search codes" to FallbackTranslation("Codici di ricerca", "Suchcodes", "Códigos de búsqueda"),
        "Got it" to FallbackTranslation("Capito", "Verstanden", "Entendido"),
        "Searches for sites in the city using the Nominatim area, like the map." to FallbackTranslation("Cerca i siti nella città usando l'area Nominatim, come la mappa.", "Sucht Standorte in der Stadt über den Nominatim-Bereich, wie die Karte.", "Busca sitios en la ciudad usando el área de Nominatim, como el mapa."),
        "Searches the full ANFR address of the site." to FallbackTranslation("Cerca nell'indirizzo ANFR completo del sito.", "Sucht in der vollständigen ANFR-Adresse des Standorts.", "Busca en toda la dirección ANFR del sitio."),
        "Searches by postal code." to FallbackTranslation("Cerca per codice postale.", "Sucht nach Postleitzahl.", "Busca por código postal."),
        "Searches around GPS coordinates." to FallbackTranslation("Cerca intorno a coordinate GPS.", "Sucht um GPS-Koordinaten herum.", "Busca alrededor de coordenadas GPS."),
        "Searches for an ANFR identifier." to FallbackTranslation("Cerca un identificativo ANFR.", "Sucht nach einer ANFR-Kennung.", "Busca un identificador ANFR."),
        "Searches for a support identifier." to FallbackTranslation("Cerca un identificativo di supporto.", "Sucht nach einer Support-Kennung.", "Busca un identificador de soporte."),
        "Filters by operator." to FallbackTranslation("Filtra per operatore.", "Filtert nach Betreiber.", "Filtra por operador."),
        "Filters by technology." to FallbackTranslation("Filtra per tecnologia.", "Filtert nach Technologie.", "Filtra por tecnología."),
        "Filters by support type." to FallbackTranslation("Filtra per tipo di supporto.", "Filtert nach Supporttyp.", "Filtra por tipo de soporte."),
        "Open" to FallbackTranslation("Apri", "Öffnen", "Abrir"),
        // Détail support, actions carte et navigation.
        "Support Detail" to FallbackTranslation("Dettaglio supporto", "Supportdetail", "Detalle del soporte"),
        "No data found." to FallbackTranslation("Nessun dato trovato.", "Keine Daten gefunden.", "No se han encontrado datos."),
        "Identification number : " to FallbackTranslation("Numero identificativo: ", "Identifikationsnummer: ", "Número de identificación: "),
        "Identification number copied" to FallbackTranslation("Numero identificativo copiato", "Identifikationsnummer kopiert", "Número de identificación copiado"),
        "Number unavailable at the moment" to FallbackTranslation("Numero al momento non disponibile", "Nummer momentan nicht verfügbar", "Número no disponible por ahora"),
        "Address : " to FallbackTranslation("Indirizzo: ", "Adresse: ", "Dirección: "),
        "Not specified" to FallbackTranslation("Non specificato", "Nicht angegeben", "No especificado"),
        "Address copied" to FallbackTranslation("Indirizzo copiato", "Adresse kopiert", "Dirección copiada"),
        "GPS : " to FallbackTranslation("GPS: ", "GPS: ", "GPS: "),
        "Coordinates copied" to FallbackTranslation("Coordinate copiate", "Koordinaten kopiert", "Coordenadas copiadas"),
        "Support height : " to FallbackTranslation("Altezza supporto: ", "Supporthöhe: ", "Altura del soporte: "),
        "Distance : " to FallbackTranslation("Distanza: ", "Entfernung: ", "Distancia: "),
        "from you" to FallbackTranslation("da te", "von dir", "de ti"),
        "Bearing measured from the antenna : " to FallbackTranslation("Direzione misurata dall'antenna: ", "Peilung von der Antenne gemessen: ", "Rumbo medido desde la antena: "),
        "Open map" to FallbackTranslation("Apri mappa", "Karte öffnen", "Abrir mapa"),
        "Navigate to this site" to FallbackTranslation("Naviga verso questo sito", "Zu diesem Standort navigieren", "Navegar a este sitio"),
        "Share this site" to FallbackTranslation("Condividi questo sito", "Diesen Standort teilen", "Compartir este sitio"),
        "Share as..." to FallbackTranslation("Condividi come...", "Teilen als...", "Compartir como..."),
        "Ideal for emails or messages" to FallbackTranslation("Ideale per email o messaggi", "Ideal für E-Mails oder Nachrichten", "Ideal para correos o mensajes"),
        "Ideal for social media (Twitter, Discord)" to FallbackTranslation("Ideale per social network (Twitter, Discord)", "Ideal für soziale Netzwerke (Twitter, Discord)", "Ideal para redes sociales (Twitter, Discord)"),
        "Open route with..." to FallbackTranslation("Apri itinerario con...", "Route öffnen mit...", "Abrir ruta con..."),
        "Installed application" to FallbackTranslation("Applicazione installata", "Installierte App", "Aplicación instalada"),
        "Open with Waze, Maps, OsmAnd..." to FallbackTranslation("Apri con Waze, Maps, OsmAnd...", "Mit Waze, Maps, OsmAnd öffnen...", "Abrir con Waze, Maps, OsmAnd..."),
        "On the internet" to FallbackTranslation("Su internet", "Im Internet", "En internet"),
        "Open in web browser" to FallbackTranslation("Apri nel browser web", "Im Webbrowser öffnen", "Abrir en el navegador web"),
        "No GPS application found." to FallbackTranslation("Nessuna applicazione GPS trovata.", "Keine GPS-App gefunden.", "No se ha encontrado ninguna aplicación GPS."),
        "Share site via..." to FallbackTranslation("Condividi sito tramite...", "Standort teilen über...", "Compartir sitio por..."),
        "Implementation : " to FallbackTranslation("Installazione: ", "Implementierung: ", "Implantación: "),
        "Last modification : " to FallbackTranslation("Ultima modifica: ", "Letzte Änderung: ", "Última modificación: "),
        "Generated via the GeoTower app" to FallbackTranslation("Generato tramite l'app GeoTower", "Erstellt mit der GeoTower-App", "Generado con la app GeoTower"),
        "Support nature" to FallbackTranslation("Natura del supporto", "Supportart", "Naturaleza del soporte"),
        "Owner" to FallbackTranslation("Proprietario", "Eigentümer", "Propietario"),
        "Likely network vendor" to FallbackTranslation("Probabile fornitore di rete", "Wahrscheinlicher Netzausrüster", "Proveedor de red probable"),
        "Antenna type" to FallbackTranslation("Tipo di antenna", "Antennentyp", "Tipo de antena"),
        "Support diagram" to FallbackTranslation("Schema del supporto", "Supportdiagramm", "Esquema del soporte"),
        "Add photos" to FallbackTranslation("Aggiungi foto", "Fotos hinzufügen", "Añadir fotos"),
        "Camera" to FallbackTranslation("Fotocamera", "Kamera", "Cámara"),
        "Gallery" to FallbackTranslation("Galleria", "Galerie", "Galería"),
        "Files / external drive" to FallbackTranslation("File / unità esterna", "Dateien / externes Laufwerk", "Archivos / unidad externa"),
        "Upload photos" to FallbackTranslation("Invia foto", "Fotos hochladen", "Enviar fotos"),
        "Could not prepare photos." to FallbackTranslation("Impossibile preparare le foto.", "Fotos konnten nicht vorbereitet werden.", "No se han podido preparar las fotos."),
        "Previous" to FallbackTranslation("Precedente", "Zurück", "Anterior"),
        "Next" to FallbackTranslation("Successivo", "Weiter", "Siguiente"),
        "Bands not specified" to FallbackTranslation("Bande non specificate", "Bänder nicht angegeben", "Bandas no especificadas"),
        "External Links" to FallbackTranslation("Link esterni", "Externe Links", "Enlaces externos"),
        "Install application" to FallbackTranslation("Installa applicazione", "App installieren", "Instalar aplicación"),
        "4G Map" to FallbackTranslation("Mappa 4G", "4G-Karte", "Mapa 4G"),
        "5G Map" to FallbackTranslation("Mappa 5G", "5G-Karte", "Mapa 5G"),
        "Unavailable" to FallbackTranslation("Non disponibile", "Nicht verfügbar", "No disponible"),
        "Which map to consult?" to FallbackTranslation("Quale mappa consultare?", "Welche Karte anzeigen?", "¿Qué mapa consultar?"),
        "Technically operational" to FallbackTranslation("Tecnicamente operativo", "Technisch betriebsbereit", "Técnicamente operativo"),
        "Project approved" to FallbackTranslation("Progetto approvato", "Projekt genehmigt", "Proyecto aprobado"),
        "Unknown status" to FallbackTranslation("Stato sconosciuto", "Unbekannter Status", "Estado desconocido"),
        "ANFR Station Number : " to FallbackTranslation("Numero stazione ANFR: ", "ANFR-Stationsnummer: ", "Número de estación ANFR: "),
        "Dates" to FallbackTranslation("Date", "Daten", "Fechas"),
        "Initialization error" to FallbackTranslation("Errore di inizializzazione", "Initialisierungsfehler", "Error de inicialización"),
        "Total spectrum" to FallbackTranslation("Spettro totale", "Gesamtspektrum", "Espectro total"),
        "The total spectrum may be inaccurate due to likely declaration errors." to FallbackTranslation("Lo spettro totale può essere impreciso a causa di probabili errori di dichiarazione.", "Das Gesamtspektrum kann wegen wahrscheinlicher Meldefehler ungenau sein.", "El espectro total puede ser impreciso por posibles errores de declaración."),
        "Spectrum" to FallbackTranslation("Spettro", "Spektrum", "Espectro"),
        "Spectrum by frequency band" to FallbackTranslation("Spettro per banda di frequenza", "Spektrum nach Frequenzband", "Espectro por banda de frecuencia"),
        "City, address, site ID..." to FallbackTranslation("Città, indirizzo, ID sito...", "Stadt, Adresse, Standort-ID...", "Ciudad, dirección, ID de sitio..."),
        "Location not found" to FallbackTranslation("Luogo non trovato", "Ort nicht gefunden", "Lugar no encontrado"),
        "Network error during search" to FallbackTranslation("Errore di rete durante la ricerca", "Netzwerkfehler bei der Suche", "Error de red durante la búsqueda"),
        "Delete traces" to FallbackTranslation("Elimina tracciati", "Spuren löschen", "Eliminar trazados"),
        "Closest site" to FallbackTranslation("Sito più vicino", "Nächster Standort", "Sitio más cercano"),
        "Filters" to FallbackTranslation("Filtri", "Filter", "Filtros"),
        "Dark" to FallbackTranslation("Scuro", "Dunkel", "Oscuro"),
        "Satellite" to FallbackTranslation("Satellite", "Satellit", "Satélite"),
        "Technologies" to FallbackTranslation("Tecnologie", "Technologien", "Tecnologías"),
        // Détail site, statistiques et photos communautaires.
        "Site Detail" to FallbackTranslation("Dettaglio sito", "Standortdetail", "Detalle del sitio"),
        "Open on" to FallbackTranslation("Apri su", "Öffnen auf", "Abrir en"),
        "Website" to FallbackTranslation("Sito web", "Website", "Sitio web"),
        "Activated on : " to FallbackTranslation("Attivato il: ", "Aktiviert am: ", "Activado el: "),
        "Activation date not specified by ANFR" to FallbackTranslation("Data di attivazione non specificata da ANFR", "Aktivierungsdatum von ANFR nicht angegeben", "Fecha de activación no especificada por ANFR"),
        "Azimuth not specified" to FallbackTranslation("Azimut non specificato", "Azimut nicht angegeben", "Azimut no especificado"),
        "Panel heights" to FallbackTranslation("Altezze dei pannelli", "Panelhöhen", "Alturas de paneles"),
        "Support ID : " to FallbackTranslation("ID supporto: ", "Support-ID: ", "ID del soporte: "),
        "Show azimuths (antenna direction)" to FallbackTranslation("Mostra azimut (direzione antenna)", "Azimute anzeigen (Antennenrichtung)", "Mostrar azimuts (dirección de la antena)"),
        "Show azimuths (cone representation)" to FallbackTranslation("Mostra azimut (rappresentazione a cono)", "Azimute anzeigen (Kegeldarstellung)", "Mostrar azimuts (representación en cono)"),
        "Site display" to FallbackTranslation("Visualizzazione siti", "Standortanzeige", "Visualización de sitios"),
        "Out of Service" to FallbackTranslation("Fuori servizio", "Außer Betrieb", "Fuera de servicio"),
        "Global tracking active..." to FallbackTranslation("Monitoraggio globale attivo...", "Globale Verfolgung aktiv...", "Seguimiento global activo..."),
        "View statistics" to FallbackTranslation("Consulta statistiche", "Statistiken anzeigen", "Ver estadísticas"),
        "Mobile telephony" to FallbackTranslation("Telefonia mobile", "Mobiltelefonie", "Telefonía móvil"),
        "Details" to FallbackTranslation("Dettagli", "Details", "Detalles"),
        "Operator details" to FallbackTranslation("Dettaglio per operatore", "Betreiberdetails", "Detalle por operador"),
        "Active / declared" to FallbackTranslation("Attivi / dichiarati", "Aktiv / gemeldet", "Activos / declarados"),
        "Technologies / Frequencies" to FallbackTranslation("Tecnologie / Frequenze", "Technologien / Frequenzen", "Tecnologías / Frecuencias"),
        "Loading operators..." to FallbackTranslation("Caricamento operatori...", "Betreiber werden geladen...", "Cargando operadores..."),
        "Loading frequencies..." to FallbackTranslation("Caricamento frequenze...", "Frequenzen werden geladen...", "Cargando frecuencias..."),
        "Supports (Pylons)" to FallbackTranslation("Supporti (piloni)", "Supports (Masten)", "Soportes (pilones)"),
        "Number of physical sites per operator" to FallbackTranslation("Numero di siti fisici per operatore", "Anzahl physischer Standorte pro Betreiber", "Número de sitios físicos por operador"),
        "4G Sites" to FallbackTranslation("Siti 4G", "4G-Standorte", "Sitios 4G"),
        "Number of 4G-equipped sites per operator" to FallbackTranslation("Numero di siti dotati di 4G per operatore", "Anzahl 4G-ausgerüsteter Standorte pro Betreiber", "Número de sitios con 4G por operador"),
        "5G Sites" to FallbackTranslation("Siti 5G", "5G-Standorte", "Sitios 5G"),
        "Number of 5G-equipped sites per operator" to FallbackTranslation("Numero di siti dotati di 5G per operatore", "Anzahl 5G-ausgerüsteter Standorte pro Betreiber", "Número de sitios con 5G por operador"),
        "Widget Permission" to FallbackTranslation("Autorizzazione widget", "Widget-Berechtigung", "Permiso del widget"),
        "Allow \"Always\" for the widget to refresh" to FallbackTranslation("Consenti \"Sempre\" per aggiornare il widget", "\"Immer\" erlauben, damit das Widget aktualisiert wird", "Permite \"Siempre\" para que el widget se actualice"),
        "You are offline.\nCommunity photos cannot be retrieved without an internet connection. The diagram remains visible when available." to FallbackTranslation("Sei offline.\nLe foto della community non possono essere recuperate senza connessione Internet. Lo schema resta visibile quando disponibile.", "Du bist offline.\nCommunity-Fotos können ohne Internetverbindung nicht abgerufen werden. Das Diagramm bleibt sichtbar, wenn es verfügbar ist.", "Estás sin conexión.\nLas fotos comunitarias no pueden recuperarse sin Internet. El esquema seguirá visible cuando esté disponible."),
        "Site photo" to FallbackTranslation("Foto del sito", "Standortfoto", "Foto del sitio"),
        "Full screen photo" to FallbackTranslation("Foto a schermo intero", "Vollbildfoto", "Foto a pantalla completa"),
        "Support image" to FallbackTranslation("Immagine del supporto", "Supportbild", "Imagen del soporte"),
        "Full screen image" to FallbackTranslation("Immagine a schermo intero", "Vollbildbild", "Imagen a pantalla completa"),
        // Partage, génération d'image et actions communes.
        "Default share content" to FallbackTranslation("Contenuto di condivisione predefinito", "Standard-Teilen-Inhalt", "Contenido compartido predeterminado"),
        "Choose the elements to include on the image" to FallbackTranslation("Scegli gli elementi da includere nell'immagine", "Wähle die Elemente aus, die im Bild enthalten sein sollen", "Elige los elementos que incluir en la imagen"),
        "Display the map" to FallbackTranslation("Mostra la mappa", "Karte anzeigen", "Mostrar el mapa"),
        "Elevation profile (separate image)" to FallbackTranslation("Profilo altimetrico (immagine separata)", "Höhenprofil (separates Bild)", "Perfil altimétrico (imagen separada)"),
        "Best Speedtest" to FallbackTranslation("Miglior Speedtest", "Bester Speedtest", "Mejor Speedtest"),
        "Address and Coordinates" to FallbackTranslation("Indirizzo e coordinate", "Adresse und Koordinaten", "Dirección y coordenadas"),
        "Antenna identifiers" to FallbackTranslation("Identificativi antenna", "Antennenkennungen", "Identificadores de la antena"),
        "Confidential share" to FallbackTranslation("Condivisione riservata", "Vertrauliches Teilen", "Compartir confidencial"),
        "Removes data allowing location identification" to FallbackTranslation("Rimuove i dati che permettono di identificare il luogo", "Entfernt Daten, mit denen der Ort identifiziert werden kann", "Elimina datos que permiten identificar el lugar"),
        "Scan to open in" to FallbackTranslation("Scansiona per aprire in", "Scannen zum Öffnen in", "Escanea para abrir en"),
        "the GeoTower app" to FallbackTranslation("l'app GeoTower", "der GeoTower-App", "la app GeoTower"),
        "Open menu" to FallbackTranslation("Apri menu", "Menü öffnen", "Abrir menú"),
        "Close menu" to FallbackTranslation("Chiudi menu", "Menü schließen", "Cerrar menú"),
        "Search" to FallbackTranslation("Cerca", "Suchen", "Buscar"),
        "Delete" to FallbackTranslation("Elimina", "Löschen", "Eliminar"),
        "Apply" to FallbackTranslation("Applica", "Anwenden", "Aplicar"),
        "Technical data unavailable" to FallbackTranslation("Dati tecnici non disponibili", "Technische Daten nicht verfügbar", "Datos técnicos no disponibles"),
        "Ruler" to FallbackTranslation("Righello", "Lineal", "Regla"),
        "Layers" to FallbackTranslation("Livelli", "Ebenen", "Capas"),
        "Tools" to FallbackTranslation("Strumenti", "Werkzeuge", "Herramientas"),
        "Locate" to FallbackTranslation("Localizza", "Orten", "Localizar"),
        "Top" to FallbackTranslation("Alto", "Oben", "Arriba"),
        "Bottom" to FallbackTranslation("Basso", "Unten", "Abajo"),
        "(No offline map installed)" to FallbackTranslation("(Nessuna mappa offline installata)", "(Keine Offline-Karte installiert)", "(Ningún mapa sin conexión instalado)"),
        "Image content" to FallbackTranslation("Contenuto dell'immagine", "Bildinhalt", "Contenido de la imagen"),
        "Generate image" to FallbackTranslation("Genera immagine", "Bild erstellen", "Generar imagen"),
        "Generating share images..." to FallbackTranslation("Generazione immagini di condivisione...", "Teilen-Bilder werden erstellt...", "Generando imágenes para compartir..."),
        "Preparing the main image..." to FallbackTranslation("Preparazione dell'immagine principale...", "Hauptbild wird vorbereitet...", "Preparando la imagen principal..."),
        "Elevation profile unavailable for sharing." to FallbackTranslation("Profilo altimetrico non disponibile per la condivisione.", "Höhenprofil zum Teilen nicht verfügbar.", "Perfil altimétrico no disponible para compartir."),
        "Distance hidden (Confidential mode)" to FallbackTranslation("Distanza nascosta (modalità riservata)", "Entfernung ausgeblendet (vertraulicher Modus)", "Distancia oculta (modo confidencial)"),
        "Support ID" to FallbackTranslation("ID supporto", "Support-ID", "ID del soporte"),
        "GPS Coordinates" to FallbackTranslation("Coordinate GPS", "GPS-Koordinaten", "Coordenadas GPS"),
        "Your MaterialUi color is too light. For better readability, dark blue has been applied." to FallbackTranslation("Il colore MaterialUi è troppo chiaro. Per una migliore leggibilità è stato applicato il blu scuro.", "Deine MaterialUi-Farbe ist zu hell. Für bessere Lesbarkeit wurde Dunkelblau angewendet.", "Tu color MaterialUi es demasiado claro. Para mejorar la legibilidad se ha aplicado azul oscuro."),
        "Do not show this message again" to FallbackTranslation("Non mostrare più questo messaggio", "Diese Meldung nicht mehr anzeigen", "No volver a mostrar este mensaje"),
        "Understood" to FallbackTranslation("Ho capito", "Verstanden", "Entendido"),
        "Send to Signal Quest" to FallbackTranslation("Invia a Signal Quest", "An Signal Quest senden", "Enviar a Signal Quest"),
        "Remove EXIF data" to FallbackTranslation("Rimuovi dati EXIF", "EXIF-Daten entfernen", "Eliminar datos EXIF"),
        "Add a description for this batch (optional)..." to FallbackTranslation("Aggiungi una descrizione per questo lotto (opzionale)...", "Beschreibung für diesen Stapel hinzufügen (optional)...", "Añadir una descripción para este lote (opcional)..."),
        "Target operator" to FallbackTranslation("Operatore target", "Zielbetreiber", "Operador objetivo"),
        "Site ID" to FallbackTranslation("ID sito", "Standort-ID", "ID del sitio"),
        "Limit: 20 MB per photo" to FallbackTranslation("Limite: 20 MB per foto", "Limit: 20 MB pro Foto", "Límite: 20 MB por foto"),
        "Upload Confirmation" to FallbackTranslation("Conferma invio", "Upload-Bestätigung", "Confirmación de envío"),
        "Uploading" to FallbackTranslation("Invio in corso", "Upload läuft", "Enviando"),
        "Preparing..." to FallbackTranslation("Preparazione...", "Vorbereitung...", "Preparando..."),
        "⚠️ Background location required" to FallbackTranslation("⚠️ Localizzazione in background richiesta", "⚠️ Hintergrundstandort erforderlich", "⚠️ Ubicación en segundo plano requerida"),
        "Support" to FallbackTranslation("Supporto", "Support", "Soporte"),
        "Authorize" to FallbackTranslation("Autorizza", "Autorisieren", "Autorizar"),
        "Start setup ➜" to FallbackTranslation("Avvia configurazione ➜", "Einrichtung starten ➜", "Iniciar configuración ➜"),
        "Welcome to GeoTower, the app for locating nearby cell towers and getting information about them." to FallbackTranslation("Benvenuto in GeoTower, l'app per localizzare le antenne mobili vicine e ottenere informazioni su di esse.", "Willkommen bei GeoTower, der App zum Finden von Mobilfunkstandorten in deiner Nähe und ihren Informationen.", "Bienvenido a GeoTower, la app para localizar antenas móviles cercanas y obtener información sobre ellas."),
        "Find what is nearby" to FallbackTranslation("Trova cosa c'è vicino", "Finde, was in der Nähe ist", "Encuentra lo que está cerca"),
        "Quickly list the nearest radio supports and sites around your position." to FallbackTranslation("Elenca rapidamente i supporti radio e i siti più vicini alla tua posizione.", "Liste schnell die nächsten Funkstandorte und Träger um deine Position auf.", "Lista rápidamente los soportes y sitios de radio más cercanos a tu posición."),
        "Explore on the map" to FallbackTranslation("Esplora sulla mappa", "Auf der Karte erkunden", "Explorar en el mapa"),
        "Browse antennas, filters, layers and technical information directly on a map." to FallbackTranslation("Consulta antenne, filtri, livelli e informazioni tecniche direttamente su una mappa.", "Durchsuche Antennen, Filter, Ebenen und technische Informationen direkt auf der Karte.", "Consulta antenas, filtros, capas e información técnica directamente en un mapa."),
        "Understand the network" to FallbackTranslation("Capire la rete", "Das Netz verstehen", "Entender la red"),
        "Access frequencies, azimuths, photos, profiles and measurement tools when data is available." to FallbackTranslation("Accedi a frequenze, azimut, foto, profili e strumenti di misura quando i dati sono disponibili.", "Greife auf Frequenzen, Azimute, Fotos, Profile und Messwerkzeuge zu, wenn Daten verfügbar sind.", "Accede a frecuencias, azimuts, fotos, perfiles y herramientas de medición cuando haya datos disponibles."),
        "Please allow location permission so GeoTower can quickly locate nearby transmitters." to FallbackTranslation("Concedi l'autorizzazione alla posizione affinché GeoTower trovi rapidamente i trasmettitori vicini.", "Erlaube den Standortzugriff, damit GeoTower Sender in der Nähe schnell finden kann.", "Permite la ubicación para que GeoTower localice rápidamente emisores cercanos."),
        "Nearby in one tap" to FallbackTranslation("Vicino con un tocco", "Nähe mit einem Tippen", "Cerca en un toque"),
        "Your position is used to sort antennas around you and open the right area immediately." to FallbackTranslation("La tua posizione serve a ordinare le antenne intorno a te e aprire subito l'area corretta.", "Deine Position sortiert Antennen in deiner Umgebung und öffnet sofort den passenden Bereich.", "Tu posición se usa para ordenar antenas cercanas y abrir directamente la zona correcta."),
        "More precise map" to FallbackTranslation("Mappa più precisa", "Präzisere Karte", "Mapa más preciso"),
        "The location button can center the map, then follow your movement when you enable it." to FallbackTranslation("Il pulsante posizione può centrare la mappa e poi seguire i tuoi spostamenti quando lo abiliti.", "Die Standorttaste kann die Karte zentrieren und danach deiner Bewegung folgen, wenn du es aktivierst.", "El botón de ubicación puede centrar el mapa y seguir tu movimiento cuando lo actives."),
        "You stay in control" to FallbackTranslation("Resti tu al comando", "Du behältst die Kontrolle", "Tú mantienes el control"),
        "You can continue without permission and change it later from Android settings." to FallbackTranslation("Puoi continuare senza autorizzazione e modificarla più tardi dalle impostazioni Android.", "Du kannst ohne Berechtigung fortfahren und sie später in den Android-Einstellungen ändern.", "Puedes continuar sin permiso y cambiarlo más tarde desde los ajustes de Android."),
        "Notifications" to FallbackTranslation("Notifiche", "Benachrichtigungen", "Notificaciones"),
        "Please allow notification permission to show notifications for database downloads, offline map downloads, and new database availability." to FallbackTranslation("Concedi l'autorizzazione alle notifiche per mostrare download del database, download delle mappe offline e disponibilità di un nuovo database.", "Erlaube Benachrichtigungen für Datenbankdownloads, Offline-Kartendownloads und neue verfügbare Datenbanken.", "Permite las notificaciones para mostrar descargas de la base, mapas sin conexión y nuevas bases disponibles."),
        "Visible downloads" to FallbackTranslation("Download visibili", "Sichtbare Downloads", "Descargas visibles"),
        "GeoTower can show progress for database and offline map downloads while they are running." to FallbackTranslation("GeoTower può mostrare l'avanzamento dei download del database e delle mappe offline mentre sono in corso.", "GeoTower kann den Fortschritt von Datenbank- und Offline-Kartendownloads anzeigen.", "GeoTower puede mostrar el progreso de la base y de los mapas sin conexión durante la descarga."),
        "Up-to-date data" to FallbackTranslation("Dati aggiornati", "Aktuelle Daten", "Datos actualizados"),
        "You can be notified when a new database is available without opening the app to check." to FallbackTranslation("Puoi ricevere un avviso quando è disponibile un nuovo database senza aprire l'app.", "Du kannst benachrichtigt werden, wenn eine neue Datenbank verfügbar ist, ohne die App zu öffnen.", "Puedes recibir un aviso cuando haya una nueva base disponible sin abrir la app."),
        "Useful alerts only" to FallbackTranslation("Solo avvisi utili", "Nur nützliche Hinweise", "Solo avisos útiles"),
        "No social notifications or ads: only long-running operations and important data updates." to FallbackTranslation("Niente notifiche social o pubblicità: solo operazioni lunghe e aggiornamenti dati importanti.", "Keine Social-Benachrichtigungen oder Werbung: nur lange Vorgänge und wichtige Datenupdates.", "Sin notificaciones sociales ni anuncios: solo operaciones largas y actualizaciones importantes."),
        "Selected operator" to FallbackTranslation("Operatore selezionato", "Ausgewählter Betreiber", "Operador seleccionado"),
        "Location permission denied" to FallbackTranslation("Autorizzazione posizione negata", "Standortberechtigung abgelehnt", "Permiso de ubicación denegado"),
        "Location permission was denied. Some features will be limited, such as nearby search, map recentering, and live tracking." to FallbackTranslation("L'autorizzazione alla posizione è stata negata. Alcune funzioni saranno limitate, come la ricerca vicina, il ricentraggio della mappa e il tracciamento live.", "Die Standortberechtigung wurde abgelehnt. Einige Funktionen sind eingeschränkt, etwa Suche in der Nähe, Kartenzentrierung und Live-Tracking.", "Se denegó el permiso de ubicación. Algunas funciones estarán limitadas, como la búsqueda cercana, centrar el mapa y el seguimiento en directo."),
        "Notification permission denied" to FallbackTranslation("Autorizzazione notifiche negata", "Benachrichtigungsberechtigung abgelehnt", "Permiso de notificaciones denegado"),
        "Notification permission was denied. Some features will be limited, such as download progress, update alerts, and live notifications." to FallbackTranslation("L'autorizzazione alle notifiche è stata negata. Alcune funzioni saranno limitate, come avanzamento download, avvisi di aggiornamento e notifiche live.", "Die Benachrichtigungsberechtigung wurde abgelehnt. Einige Funktionen sind eingeschränkt, etwa Downloadfortschritt, Update-Hinweise und Live-Benachrichtigungen.", "Se denegó el permiso de notificaciones. Algunas funciones estarán limitadas, como progreso de descargas, avisos de actualización y notificaciones en directo."),
        "Continue anyway" to FallbackTranslation("Continua comunque", "Trotzdem fortfahren", "Continuar de todos modos"),
        "Let's go!" to FallbackTranslation("Andiamo!", "Los geht's!", "¡Vamos!"),
        "Choose the style that suits you." to FallbackTranslation("Scegli lo stile più adatto a te.", "Wähle den Stil, der zu dir passt.", "Elige el estilo que prefieras."),
        "OLED Mode (Pure Black)" to FallbackTranslation("Modalità OLED (nero puro)", "OLED-Modus (reines Schwarz)", "Modo OLED (negro puro)"),
        "Saves battery" to FallbackTranslation("Risparmia batteria", "Spart Akku", "Ahorra batería"),
        "Scroll Blur" to FallbackTranslation("Sfocatura scorrimento", "Scroll-Weichzeichnung", "Desenfoque al desplazar"),
        "Enable or disable blur (consumes more battery)" to FallbackTranslation("Attiva o disattiva la sfocatura (consuma più batteria)", "Weichzeichnung aktivieren oder deaktivieren (verbraucht mehr Akku)", "Activa o desactiva el desenfoque (consume más batería)"),
        "Which map provider do you prefer?" to FallbackTranslation("Quale provider di mappe preferisci?", "Welchen Kartenanbieter bevorzugst du?", "¿Qué proveedor de mapas prefieres?"),
        "Configure your main operator to make it easier to use the measurement tools on the map." to FallbackTranslation("Configura il tuo operatore principale per usare più facilmente gli strumenti di misura sulla mappa.", "Konfiguriere deinen Hauptbetreiber, damit die Messwerkzeuge auf der Karte leichter nutzbar sind.", "Configura tu operador principal para facilitar el uso de las herramientas de medición en el mapa."),
        "Select your main operator" to FallbackTranslation("Seleziona il tuo operatore principale", "Wähle deinen Hauptbetreiber", "Selecciona tu operador principal"),
        "Use One UI display (rounded bubbles)" to FallbackTranslation("Usa visualizzazione One UI (bolle arrotondate)", "One-UI-Anzeige verwenden (runde Kacheln)", "Usar visualización One UI (burbujas redondeadas)"),
        "No operator selected" to FallbackTranslation("Nessun operatore selezionato", "Kein Betreiber ausgewählt", "Ningún operador seleccionado"),
        "You have not chosen a default operator. The filtering tools on the map will be disabled.\n\nDo you really want to continue?" to FallbackTranslation("Non hai scelto un operatore predefinito. Gli strumenti di filtro sulla mappa saranno disattivati.\n\nVuoi davvero continuare?", "Du hast keinen Standardbetreiber gewählt. Die Filterwerkzeuge auf der Karte werden deaktiviert.\n\nMöchtest du wirklich fortfahren?", "No has elegido un operador predeterminado. Las herramientas de filtro del mapa se desactivarán.\n\n¿Quieres continuar?"),
        "Choose an operator" to FallbackTranslation("Scegli un operatore", "Betreiber wählen", "Elegir un operador"),
        "Display Style" to FallbackTranslation("Stile di visualizzazione", "Anzeigestil", "Estilo de visualización"),
        "Full screen" to FallbackTranslation("Schermo intero", "Vollbild", "Pantalla completa"),
        "Display support details and site details individually in full screen" to FallbackTranslation("Mostra i dettagli del supporto e del sito singolarmente a schermo intero", "Support- und Standortdetails einzeln im Vollbild anzeigen", "Mostrar los detalles del soporte y del sitio por separado en pantalla completa"),
        "Split" to FallbackTranslation("Diviso", "Geteilt", "Dividido"),
        "Split display for compatible screens: the context stays on the left while details open on the right." to FallbackTranslation("Visualizzazione divisa per schermate compatibili: il contesto resta a sinistra e i dettagli si aprono a destra.", "Geteilte Ansicht für kompatible Bildschirme: der Kontext bleibt links, Details öffnen sich rechts.", "Vista dividida en pantallas compatibles: el contexto queda a la izquierda y los detalles se abren a la derecha."),
        "Download finished!" to FallbackTranslation("Download completato!", "Download abgeschlossen!", "¡Descarga terminada!"),
        "The offline database has been successfully installed. The application is ready to run at full speed." to FallbackTranslation("Il database offline è stato installato correttamente. L'applicazione è pronta a funzionare al massimo.", "Die Offline-Datenbank wurde erfolgreich installiert. Die Anwendung ist vollständig bereit.", "La base sin conexión se ha instalado correctamente. La aplicación está lista para funcionar a pleno rendimiento."),
        // À propos, crédits, onboarding et libellés techniques ANFR.
        "Presentation" to FallbackTranslation("Presentazione", "Präsentation", "Presentación"),
        "What's New" to FallbackTranslation("Novità", "Neuigkeiten", "Novedades"),
        "Download the new database" to FallbackTranslation("Scarica il nuovo database", "Neue Datenbank herunterladen", "Descargar la nueva base"),
        "Not installed" to FallbackTranslation("Non installato", "Nicht installiert", "No instalado"),
        "Data Sources" to FallbackTranslation("Fonti dati", "Datenquellen", "Fuentes de datos"),
        "Development" to FallbackTranslation("Sviluppo", "Entwicklung", "Desarrollo"),
        "GeoTower allows you to locate cell towers around you and identify available technologies." to FallbackTranslation("GeoTower ti permette di localizzare le antenne mobili intorno a te e identificare le tecnologie disponibili.", "GeoTower ermöglicht es dir, Mobilfunkstandorte in deiner Umgebung zu finden und verfügbare Technologien zu erkennen.", "GeoTower te permite localizar antenas móviles a tu alrededor e identificar las tecnologías disponibles."),
        "Antenna Data" to FallbackTranslation("Dati antenne", "Antennendaten", "Datos de antenas"),
        "IGN Basemap" to FallbackTranslation("Mappa base IGN", "IGN-Basiskarte", "Mapa base IGN"),
        "OSM Basemap" to FallbackTranslation("Mappa base OSM", "OSM-Basiskarte", "Mapa base OSM"),
        "Inspiration & External Sources" to FallbackTranslation("Ispirazione e fonti esterne", "Inspiration und externe Quellen", "Inspiración y fuentes externas"),
        "Privacy" to FallbackTranslation("Privacy", "Datenschutz", "Privacidad"),
        "Your data" to FallbackTranslation("I tuoi dati", "Deine Daten", "Tus datos"),
        "GeoTower does not collect any personal data. Your settings and favorites are stored only on your device." to FallbackTranslation("GeoTower non raccoglie dati personali. Le impostazioni e i preferiti restano solo sul tuo dispositivo.", "GeoTower sammelt keine personenbezogenen Daten. Deine Einstellungen und Favoriten bleiben nur auf deinem Gerät.", "GeoTower no recopila datos personales. Tus ajustes y favoritos se guardan solo en tu dispositivo."),
        "Developed by Julien and GitHub contributors 😉" to FallbackTranslation("Sviluppato da Julien e dai contributori GitHub 😉", "Entwickelt von Julien und GitHub-Mitwirkenden 😉", "Desarrollado por Julien y colaboradores de GitHub 😉"),
        "Close" to FallbackTranslation("Chiudi", "Schließen", "Cerrar"),
        "Map" to FallbackTranslation("Mappa", "Karte", "Mapa"),
        "Back" to FallbackTranslation("Indietro", "Zurück", "Atrás"),
        "Show more sites" to FallbackTranslation("Mostra altri siti", "Mehr Standorte anzeigen", "Mostrar más sitios"),
        "Unknown address" to FallbackTranslation("Indirizzo sconosciuto", "Unbekannte Adresse", "Dirección desconocida"),
        "ANFR site" to FallbackTranslation("Sito ANFR", "ANFR-Standort", "Sitio ANFR"),
        "Move" to FallbackTranslation("Sposta", "Verschieben", "Mover"),
        "Copy" to FallbackTranslation("Copia", "Kopieren", "Copiar"),
        "Cancel" to FallbackTranslation("Annulla", "Abbrechen", "Cancelar"),
        "Hide" to FallbackTranslation("Nascondi", "Ausblenden", "Ocultar"),
        "Upload finished!" to FallbackTranslation("Invio completato!", "Upload abgeschlossen!", "¡Envío terminado!"),
        "Some photos could not be sent (network issue)." to FallbackTranslation("Alcune foto non sono state inviate (problema di rete).", "Einige Fotos konnten nicht gesendet werden (Netzwerkproblem).", "Algunas fotos no se pudieron enviar (problema de red)."),
        "Awesome!" to FallbackTranslation("Perfetto!", "Super!", "¡Genial!"),
        "Download finished" to FallbackTranslation("Download completato", "Download abgeschlossen", "Descarga terminada"),
        "The database was successfully downloaded!" to FallbackTranslation("Il database è stato scaricato correttamente!", "Die Datenbank wurde erfolgreich heruntergeladen!", "¡La base se descargó correctamente!"),
        "Finish" to FallbackTranslation("Fine", "Fertig", "Finalizar"),
        "Database not downloaded" to FallbackTranslation("Database non scaricato", "Datenbank nicht heruntergeladen", "Base no descargada"),
        "You haven't downloaded the app's database, so you won't have any items displayed on the screen." to FallbackTranslation("Non hai scaricato il database dell'app, quindi non verrà mostrato alcun elemento sullo schermo.", "Du hast die App-Datenbank nicht heruntergeladen, daher werden keine Einträge angezeigt.", "No has descargado la base de la app, así que no se mostrará ningún elemento en la pantalla."),
        "Are you sure you want to continue?" to FallbackTranslation("Vuoi davvero continuare?", "Möchtest du wirklich fortfahren?", "¿Seguro que quieres continuar?"),
        "Continue" to FallbackTranslation("Continua", "Fortfahren", "Continuar"),
        "Missing database" to FallbackTranslation("Database mancante", "Datenbank fehlt", "Base ausente"),
        "Invalid database" to FallbackTranslation("Database non valido", "Ungültige Datenbank", "Base no válida"),
        "Update available" to FallbackTranslation("Aggiornamento disponibile", "Update verfügbar", "Actualización disponible"),
        "Download the database to use the app." to FallbackTranslation("Scarica il database per usare l'app.", "Lade die Datenbank herunter, um die App zu nutzen.", "Descarga la base para usar la app."),
        "The local database is incompatible. Download a valid database to continue." to FallbackTranslation("Il database locale non è compatibile. Scarica un database valido per continuare.", "Die lokale Datenbank ist inkompatibel. Lade eine gültige Datenbank herunter, um fortzufahren.", "La base local es incompatible. Descarga una base válida para continuar."),
        "No type" to FallbackTranslation("Nessun tipo", "Kein Typ", "Sin tipo"),
        "Semaphore tower" to FallbackTranslation("Torre semaforica", "Semaphor-Turm", "Torre de semáforo"),
        "Lighthouse" to FallbackTranslation("Faro", "Leuchtturm", "Faro"),
        "Water tower / reservoir" to FallbackTranslation("Torre dell'acqua / serbatoio", "Wasserturm / Reservoir", "Torre de agua / depósito"),
        "Building" to FallbackTranslation("Edificio", "Gebäude", "Edificio"),
        "Technical room / equipment shelter" to FallbackTranslation("Locale tecnico / shelter", "Technikraum / Geräteschutzraum", "Sala técnica / caseta"),
        "Mast" to FallbackTranslation("Palo", "Mast", "Mástil"),
        "Inside gallery" to FallbackTranslation("Interno galleria", "Innenbereich einer Galerie", "Interior de galería"),
        "Underground interior" to FallbackTranslation("Interno sotterraneo", "Unterirdischer Innenbereich", "Interior subterráneo"),
        "Concrete mast" to FallbackTranslation("Palo in cemento", "Betonmast", "Mástil de hormigón"),
        "Metal mast" to FallbackTranslation("Palo metallico", "Metallmast", "Mástil metálico"),
        "Tower / pylon" to FallbackTranslation("Torre / traliccio", "Turm / Mast", "Torre / pilón"),
        "Historic monument" to FallbackTranslation("Monumento storico", "Historisches Denkmal", "Monumento histórico"),
        "Religious monument" to FallbackTranslation("Monumento religioso", "Religiöses Denkmal", "Monumento religioso"),
        "Self-supporting pylon" to FallbackTranslation("Traliccio autoportante", "Selbsttragender Mast", "Pilón autoportante"),
        "Free-standing pylon" to FallbackTranslation("Traliccio autostabile", "Freistehender Mast", "Pilón autosoportado"),
        "Guyed tower" to FallbackTranslation("Torre strallata", "Abgespannter Mast", "Torre arriostrada"),
        "Lattice tower" to FallbackTranslation("Torre a traliccio", "Gittermast", "Torre de celosía"),
        "Tubular tower" to FallbackTranslation("Torre tubolare", "Rohrmast", "Torre tubular"),
        "Engineering structure (bridge, viaduct)" to FallbackTranslation("Opera d'arte (ponte, viadotto)", "Ingenieurbauwerk (Brücke, Viadukt)", "Estructura de ingeniería (puente, viaducto)"),
        "Microwave tower" to FallbackTranslation("Torre hertziana", "Richtfunkturm", "Torre de microondas"),
        "Concrete slab" to FallbackTranslation("Soletta in cemento", "Betonplatte", "Losa de hormigón"),
        "Unspecified support" to FallbackTranslation("Supporto non specificato", "Nicht beschriebener Träger", "Soporte no especificado"),
        "Column / shaft" to FallbackTranslation("Colonna / fusto", "Säule / Schaft", "Columna / fuste"),
        "Control tower" to FallbackTranslation("Torre di controllo", "Kontrollturm", "Torre de control"),
        "Ground counterweight" to FallbackTranslation("Contrappeso a terra", "Bodengegengewicht", "Contrapeso en el suelo"),
        "Shelter counterweight" to FallbackTranslation("Contrappeso su shelter", "Shelter-Gegengewicht", "Contrapeso sobre caseta"),
        "Defense support" to FallbackTranslation("Supporto Difesa", "Verteidigungsträger", "Soporte de defensa"),
        "Tree mast / camouflaged tower" to FallbackTranslation("Palo ad albero / torre mimetizzata", "Baummast / getarnter Turm", "Mástil árbol / torre camuflada"),
        "Signage structure (road gantry, road panel)" to FallbackTranslation("Struttura di segnaletica (portale o pannello stradale)", "Signalbauwerk (Straßenportal, Schild)", "Estructura de señalización (pórtico o panel vial)"),
        "Beacon or buoy" to FallbackTranslation("Balise o boa", "Bake oder Boje", "Baliza o boya"),
        "Wind turbine" to FallbackTranslation("Pala eolica", "Windkraftanlage", "Aerogenerador"),
        "Urban furniture / street structure" to FallbackTranslation("Arredo urbano / struttura stradale", "Stadtmobiliar / Straßenstruktur", "Mobiliario urbano / estructura vial"),
        "Outage warning" to FallbackTranslation("Avviso di guasto", "Störungswarnung", "Aviso de avería"),
        "Unknown reason" to FallbackTranslation("Motivo sconosciuto", "Unbekannter Grund", "Motivo desconocido"),
        "Technical intervention" to FallbackTranslation("Intervento tecnico", "Technischer Einsatz", "Intervención técnica"),
        "Voice" to FallbackTranslation("Voce", "Sprache", "Voz"),
        "Degraded" to FallbackTranslation("Degradato", "Beeinträchtigt", "Degradado"),
        "Site Status" to FallbackTranslation("Stato del sito", "Standortstatus", "Estado del sitio"),
        "Functional" to FallbackTranslation("Funzionante", "Funktionsfähig", "Funcional"),
        "Out of service" to FallbackTranslation("Fuori servizio", "Außer Betrieb", "Fuera de servicio"),
        "Planned" to FallbackTranslation("Pianificato", "Geplant", "Planificado"),
        "Show status" to FallbackTranslation("Mostra stato", "Status anzeigen", "Mostrar estado"),
        "Share status" to FallbackTranslation("Condividi stato", "Status teilen", "Compartir estado"),
        "Last updated at" to FallbackTranslation("Ultimo aggiornamento alle", "Zuletzt aktualisiert um", "Última actualización a las"),
        "Ongoing incident" to FallbackTranslation("Incidente in corso", "Laufender Vorfall", "Incidencia en curso"),
        "Maintenance work" to FallbackTranslation("Lavori di manutenzione", "Wartungsarbeiten", "Trabajos de mantenimiento"),
        "Versions" to FallbackTranslation("Versioni", "Versionen", "Versiones"),
        "App\nversion" to FallbackTranslation("Versione\napp", "App-\nVersion", "Versión\nde la app"),
        "Database\nversion" to FallbackTranslation("Versione\ndatabase", "Datenbank-\nversion", "Versión\nde la base"),
        "Weekly\ndata" to FallbackTranslation("Dati\nsettimanali", "Wochen-\ndaten", "Datos\nsemanales"),
        "Monthly\ndata" to FallbackTranslation("Dati\nmensili", "Monats-\ndaten", "Datos\nmensuales"),
        "HS sites\ndata" to FallbackTranslation("Dati siti\nHS", "Daten HS-\nStandorte", "Datos de\nsitios HS"),
        "at" to FallbackTranslation("alle", "um", "a las"),
        "Show best Speedtest" to FallbackTranslation("Mostra il miglior Speedtest", "Besten Speedtest anzeigen", "Mostrar mejor Speedtest"),
        "No speedtest recorded for this site." to FallbackTranslation("Nessuno speedtest registrato per questo sito.", "Kein Speedtest für diesen Standort gespeichert.", "No hay ningún speedtest registrado para este sitio."),

        // Centre d'aide : sommaire, rubriques, descriptions et glossaire.
        "The recommended path to set up GeoTower and quickly find an antenna." to FallbackTranslation("Il percorso consigliato per configurare GeoTower e trovare rapidamente un'antenna.", "Der empfohlene Ablauf, um GeoTower einzurichten und schnell eine Antenne zu finden.", "El recorrido recomendado para configurar GeoTower y encontrar rápidamente una antena."),
        "Explains the main buttons, database banner and Help button." to FallbackTranslation("Spiega i pulsanti principali, il banner del database e il pulsante Aiuto.", "Erklärt die Hauptschaltflächen, das Datenbank-Banner und die Hilfe-Schaltfläche.", "Explica los botones principales, el banner de la base de datos y el botón Ayuda."),
        "Search by city, address, postal code, support, ANFR, coordinates, type and technology." to FallbackTranslation("Ricerca per città, indirizzo, codice postale, supporto, ANFR, coordinate, tipo e tecnologia.", "Suche nach Stadt, Adresse, Postleitzahl, Träger, ANFR, Koordinaten, Typ und Technologie.", "Búsqueda por ciudad, dirección, código postal, soporte, ANFR, coordenadas, tipo y tecnología."),
        "Markers, search, filters, GPS position, layers and map tools." to FallbackTranslation("Marcatori, ricerca, filtri, posizione GPS, livelli e strumenti mappa.", "Marker, Suche, Filter, GPS-Position, Ebenen und Kartenwerkzeuge.", "Marcadores, búsqueda, filtros, posición GPS, capas y herramientas de mapa."),
        "Orientation toward antennas and understanding bearings around your position." to FallbackTranslation("Orientamento verso le antenne e comprensione delle direzioni intorno alla tua posizione.", "Ausrichtung zu Antennen und Verständnis der Richtungen rund um deine Position.", "Orientación hacia antenas y comprensión de los rumbos alrededor de tu posición."),
        "Everything about the tower, roof, operators and linked sites." to FallbackTranslation("Tutto su traliccio, tetto, operatori e siti collegati.", "Alles über Mast, Dach, Betreiber und verknüpfte Standorte.", "Todo sobre la torre, el tejado, los operadores y los sitios vinculados."),
        "Frequencies, azimuths, height, status, photos, links, sharing and advanced tools." to FallbackTranslation("Frequenze, azimut, altezza, stato, foto, link, condivisione e strumenti avanzati.", "Frequenzen, Azimute, Höhe, Status, Fotos, Links, Teilen und erweiterte Werkzeuge.", "Frecuencias, azimuts, altura, estado, fotos, enlaces, compartir y herramientas avanzadas."),
        "Terrain, line of sight, Fresnel zone, recalculation and network limits." to FallbackTranslation("Rilievo, linea di vista, zona di Fresnel, ricalcolo e limiti di rete.", "Gelände, Sichtlinie, Fresnel-Zone, Neuberechnung und Netzgrenzen.", "Relieve, línea de vista, zona de Fresnel, recálculo y límites de red."),
        "Conservative/ideal/custom modes, bands, modulations, MIMO and optimal distance." to FallbackTranslation("Modalità prudente/ideale/personalizzata, bande, modulazioni, MIMO e distanza ottimale.", "Vorsichtig/ideal/benutzerdefiniert, Bänder, Modulationen, MIMO und optimale Entfernung.", "Modos prudente/ideal/personalizado, bandas, modulaciones, MIMO y distancia óptima."),
        "Carousel, full screen, SignalQuest upload, descriptions and target operator." to FallbackTranslation("Carosello, schermo intero, invio SignalQuest, descrizioni e operatore target.", "Karussell, Vollbild, SignalQuest-Upload, Beschreibungen und Zielbetreiber.", "Carrusel, pantalla completa, envío SignalQuest, descripciones y operador objetivo."),
        "Image options, support, frequencies, dates, speedtest, throughput and elevation profile." to FallbackTranslation("Opzioni immagine, supporto, frequenze, date, speedtest, velocità e profilo altimetrico.", "Bildoptionen, Träger, Frequenzen, Daten, Speedtest, Durchsatz und Höhenprofil.", "Opciones de imagen, soporte, frecuencias, fechas, speedtest, velocidad y perfil altimétrico."),
        "Appearance, One UI, units, split screen, customization, database, offline maps and notifications." to FallbackTranslation("Aspetto, One UI, unità, schermo diviso, personalizzazione, database, mappe offline e notifiche.", "Darstellung, One UI, Einheiten, geteilte Ansicht, Anpassung, Datenbank, Offline-Karten und Benachrichtigungen.", "Apariencia, One UI, unidades, pantalla dividida, personalización, base de datos, mapas sin conexión y notificaciones."),
        "Downloads, progress notifications, updates and local storage." to FallbackTranslation("Download, notifiche di avanzamento, aggiornamenti e archiviazione locale.", "Downloads, Fortschrittsbenachrichtigungen, Updates und lokaler Speicher.", "Descargas, notificaciones de progreso, actualizaciones y almacenamiento local."),
        "Version, data sources, development information and useful links." to FallbackTranslation("Versione, fonti dati, informazioni di sviluppo e link utili.", "Version, Datenquellen, Entwicklungsinformationen und nützliche Links.", "Versión, fuentes de datos, información de desarrollo y enlaces útiles."),
        "Meaning of common icons and solutions to frequent issues." to FallbackTranslation("Significato delle icone comuni e soluzioni ai problemi frequenti.", "Bedeutung häufiger Symbole und Lösungen für typische Probleme.", "Significado de iconos comunes y soluciones a problemas frecuentes."),
        "Prepare the app" to FallbackTranslation("Prepara l'app", "App vorbereiten", "Preparar la app"),
        "Search for an antenna" to FallbackTranslation("Cercare un'antenna", "Nach einer Antenne suchen", "Buscar una antena"),
        "Understand detail pages" to FallbackTranslation("Capire le schede", "Detailseiten verstehen", "Entender las fichas"),
        "Navigation buttons" to FallbackTranslation("Pulsanti di navigazione", "Navigationsschaltflächen", "Botones de navegación"),
        "Status banners" to FallbackTranslation("Banner di stato", "Statusbanner", "Banners de estado"),
        "Useful codes" to FallbackTranslation("Codici utili", "Nützliche Codes", "Códigos útiles"),
        "Site list" to FallbackTranslation("Elenco siti", "Standortliste", "Lista de sitios"),
        "Map controls" to FallbackTranslation("Controlli mappa", "Kartensteuerung", "Controles del mapa"),
        "Search and filters" to FallbackTranslation("Ricerca e filtri", "Suche und Filter", "Búsqueda y filtros"),
        "Offline maps" to FallbackTranslation("Mappe offline", "Offline-Karten", "Mapas sin conexión"),
        "How it works" to FallbackTranslation("Funzionamento", "Funktionsweise", "Funcionamiento"),
        "Buttons" to FallbackTranslation("Pulsanti", "Schaltflächen", "Botones"),
        "Understand the support" to FallbackTranslation("Capire il supporto", "Den Träger verstehen", "Entender el soporte"),
        "Available actions" to FallbackTranslation("Azioni disponibili", "Verfügbare Aktionen", "Acciones disponibles"),
        "Displayed information" to FallbackTranslation("Informazioni visualizzate", "Angezeigte Informationen", "Información mostrada"),
        "Buttons and tools" to FallbackTranslation("Pulsanti e strumenti", "Schaltflächen und Werkzeuge", "Botones y herramientas"),
        "Usage" to FallbackTranslation("Utilizzo", "Nutzung", "Uso"),
        "Calculation assumptions" to FallbackTranslation("Ipotesi di calcolo", "Berechnungsannahmen", "Supuestos de cálculo"),
        "Controls" to FallbackTranslation("Comandi", "Steuerung", "Controles"),
        "View photos" to FallbackTranslation("Vedere le foto", "Fotos ansehen", "Ver fotos"),
        "Create a share image" to FallbackTranslation("Creare un'immagine di condivisione", "Ein Teilen-Bild erstellen", "Crear una imagen para compartir"),
        "Understand the options" to FallbackTranslation("Capire le opzioni", "Optionen verstehen", "Entender las opciones"),
        "General preferences" to FallbackTranslation("Preferenze generali", "Allgemeine Einstellungen", "Preferencias generales"),
        "Display and split screen" to FallbackTranslation("Visualizzazione e schermo diviso", "Anzeige und geteilte Ansicht", "Visualización y pantalla dividida"),
        "Page customization" to FallbackTranslation("Personalizzazione delle pagine", "Seitenanpassung", "Personalización de páginas"),
        "Sections" to FallbackTranslation("Sezioni", "Bereiche", "Secciones"),
        "Navigation loops" to FallbackTranslation("Cicli di navigazione", "Navigationsschleifen", "Bucles de navegación"),
        "Common icons" to FallbackTranslation("Icone comuni", "Häufige Symbole", "Iconos comunes"),
        "Frequent issues" to FallbackTranslation("Problemi frequenti", "Häufige Probleme", "Problemas frecuentes"),
        "On first launch, make sure the database is downloaded, location is allowed and your main operator is set in settings. Without the local database, antenna screens cannot show ANFR sites." to FallbackTranslation("Al primo avvio, verifica che il database sia scaricato, che la posizione sia autorizzata e che il tuo operatore principale sia impostato. Senza database locale, le schermate delle antenne non possono mostrare i siti ANFR.", "Prüfe beim ersten Start, ob die Datenbank heruntergeladen ist, Standortzugriff erlaubt ist und dein Hauptbetreiber in den Einstellungen gesetzt wurde. Ohne lokale Datenbank können Antennenseiten keine ANFR-Standorte anzeigen.", "En el primer inicio, comprueba que la base de datos esté descargada, que la ubicación esté permitida y que tu operador principal esté configurado. Sin base local, las pantallas de antenas no pueden mostrar sitios ANFR."),
        "From Home, open Nearby antennas for a list around you or Antenna map to explore visually. Both paths then lead to support details and site details." to FallbackTranslation("Dalla Home, apri Antenne vicine per una lista intorno a te o Mappa antenne per esplorare visivamente. Entrambi i percorsi portano poi ai dettagli del supporto e del sito.", "Öffne auf der Startseite Antennen in der Nähe für eine Liste um dich herum oder Antennenkarte für die visuelle Erkundung. Beide Wege führen anschließend zu Support- und Standortdetails.", "Desde Inicio, abre Antenas cercanas para ver una lista a tu alrededor o Mapa de antenas para explorar visualmente. Ambos caminos llevan luego a los detalles de soporte y de sitio."),
        "A support is the tower, roof or building. A site is an operator installation on that support. Technical details, frequencies, azimuths and advanced tools are mostly on the site detail page." to FallbackTranslation("Un supporto è il traliccio, il tetto o l'edificio. Un sito è l'installazione di un operatore su quel supporto. Dettagli tecnici, frequenze, azimut e strumenti avanzati sono soprattutto nella scheda del sito.", "Ein Träger ist der Mast, das Dach oder Gebäude. Ein Standort ist die Installation eines Betreibers auf diesem Träger. Technische Details, Frequenzen, Azimute und erweiterte Werkzeuge findest du meist auf der Standortdetailseite.", "Un soporte es la torre, el tejado o el edificio. Un sitio es la instalación de un operador sobre ese soporte. Los detalles técnicos, frecuencias, azimuts y herramientas avanzadas están sobre todo en la ficha del sitio."),
        "Home groups the app's main shortcuts. Some buttons can be hidden or moved from Settings > Page customization." to FallbackTranslation("La Home raggruppa le scorciatoie principali dell'app. Alcuni pulsanti possono essere nascosti o spostati da Impostazioni > Personalizzazione pagine.", "Die Startseite bündelt die wichtigsten App-Verknüpfungen. Einige Schaltflächen können unter Einstellungen > Seitenanpassung ausgeblendet oder verschoben werden.", "Inicio agrupa los accesos principales de la app. Algunos botones se pueden ocultar o mover desde Ajustes > Personalización de páginas."),
        "If the database is missing, outdated or downloading, a banner appears at the top. If the network is unavailable, an offline banner warns that some features cannot update." to FallbackTranslation("Se il database manca, è obsoleto o è in download, appare un banner in alto. Se la rete non è disponibile, un banner offline avvisa che alcune funzioni non possono aggiornarsi.", "Wenn die Datenbank fehlt, veraltet ist oder heruntergeladen wird, erscheint oben ein Banner. Wenn kein Netz verfügbar ist, warnt ein Offline-Banner, dass manche Funktionen nicht aktualisiert werden können.", "Si falta la base, está obsoleta o se está descargando, aparece un banner arriba. Si no hay red, un banner sin conexión avisa de que algunas funciones no podrán actualizarse."),
        "Search accepts free text and codes. For a city, GeoTower can use the same logic as the map: find the municipality area, then filter sites across the full ANFR address." to FallbackTranslation("La ricerca accetta testo libero e codici. Per una città, GeoTower può usare la stessa logica della mappa: trova l'area del comune, poi filtra i siti sull'intero indirizzo ANFR.", "Die Suche akzeptiert freien Text und Codes. Für eine Stadt kann GeoTower dieselbe Logik wie die Karte verwenden: Gemeindegebiet finden und dann Standorte über die vollständige ANFR-Adresse filtern.", "La búsqueda acepta texto libre y códigos. Para una ciudad, GeoTower puede usar la misma lógica que el mapa: encuentra el área municipal y filtra los sitios por toda la dirección ANFR."),
        "Use prefixes when a search may be ambiguous: ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:pylon, op:Orange. Accents, uppercase letters and hyphens are normalized to make city searches easier." to FallbackTranslation("Usa prefissi quando una ricerca può essere ambigua: ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:pylon, op:Orange. Accenti, maiuscole e trattini sono normalizzati per facilitare le ricerche dei comuni.", "Verwende Präfixe, wenn eine Suche mehrdeutig sein kann: ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:pylon, op:Orange. Akzente, Großbuchstaben und Bindestriche werden normalisiert, um Stadtsuchen zu erleichtern.", "Usa prefijos cuando una búsqueda pueda ser ambigua: ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:pylon, op:Orange. Acentos, mayúsculas y guiones se normalizan para facilitar búsquedas de municipios."),
        "Each result card shows the support, address, distance and available operators. Tapping opens the detail page. In split screen, the list stays on the left and details open on the right." to FallbackTranslation("Ogni scheda risultato mostra supporto, indirizzo, distanza e operatori disponibili. Un tocco apre il dettaglio. In schermo diviso, la lista resta a sinistra e i dettagli si aprono a destra.", "Jede Ergebnis-Karte zeigt Träger, Adresse, Entfernung und verfügbare Betreiber. Tippen öffnet die Detailseite. In geteilter Ansicht bleibt die Liste links und Details öffnen rechts.", "Cada tarjeta de resultado muestra soporte, dirección, distancia y operadores disponibles. Al tocar se abre el detalle. En pantalla dividida, la lista queda a la izquierda y el detalle se abre a la derecha."),
        "The map lets you browse supports and sites. Tapping a marker opens its information. The GPS button recenters on your position when location is available." to FallbackTranslation("La mappa permette di esplorare supporti e siti. Toccando un marcatore si aprono le informazioni. Il pulsante GPS ricentra sulla tua posizione quando disponibile.", "Die Karte ermöglicht das Durchsuchen von Trägern und Standorten. Tippen auf einen Marker öffnet seine Informationen. Die GPS-Schaltfläche zentriert auf deine Position, wenn Standort verfügbar ist.", "El mapa permite explorar soportes y sitios. Al tocar un marcador se abre su información. El botón GPS recentra en tu posición cuando la ubicación está disponible."),
        "The toolbox lets you search a city or address, then refine display by operator, technology, frequency or layer. Filters avoid overloading the map when an area contains many antennas." to FallbackTranslation("La toolbox permette di cercare una città o un indirizzo, poi affinare la visualizzazione per operatore, tecnologia, frequenza o livello. I filtri evitano di sovraccaricare la mappa quando un'area contiene molte antenne.", "Die Werkzeugleiste ermöglicht Stadt- oder Adresssuche und anschließend Filter nach Betreiber, Technologie, Frequenz oder Ebene. Filter verhindern eine überladene Karte, wenn ein Bereich viele Antennen enthält.", "La caja de herramientas permite buscar una ciudad o dirección y luego refinar por operador, tecnología, frecuencia o capa. Los filtros evitan sobrecargar el mapa cuando una zona contiene muchas antenas."),
        "If an offline map is downloaded and selected, GeoTower can show the local layer without downloading standard tiles. Files and areas are managed in settings." to FallbackTranslation("Se una mappa offline è scaricata e selezionata, GeoTower può mostrare il livello locale senza scaricare tile standard. File e aree si gestiscono nelle impostazioni.", "Wenn eine Offline-Karte heruntergeladen und ausgewählt ist, kann GeoTower die lokale Ebene ohne Standard-Kacheln anzeigen. Dateien und Bereiche werden in den Einstellungen verwaltet.", "Si se descarga y selecciona un mapa sin conexión, GeoTower puede mostrar la capa local sin descargar teselas estándar. Archivos y zonas se gestionan en ajustes."),
        "The compass uses your position, phone sensors and antenna data to help you understand where nearby sites are located." to FallbackTranslation("La bussola usa la tua posizione, i sensori del telefono e i dati antenna per aiutarti a capire dove si trovano i siti vicini.", "Der Kompass nutzt deine Position, Telefonsensoren und Antennendaten, um dir die Richtung naher Standorte zu zeigen.", "La brújula usa tu posición, los sensores del teléfono y datos de antenas para ayudarte a entender dónde están los sitios cercanos."),
        "The back button closes the screen. Internal actions can start antenna search, stop live tracking or open details depending on the displayed mode." to FallbackTranslation("Il pulsante indietro chiude la schermata. Le azioni interne possono avviare la ricerca antenna, fermare il tracciamento live o aprire dettagli in base alla modalità mostrata.", "Die Zurück-Schaltfläche schließt die Seite. Interne Aktionen können je nach Modus Antennensuche starten, Live-Tracking stoppen oder Details öffnen.", "El botón volver cierra la pantalla. Las acciones internas pueden iniciar la búsqueda de antena, detener el seguimiento en directo o abrir detalles según el modo mostrado."),
        "The support is the physical structure: tower, building, water tower, roof or another type. Several operators and sites can be linked to the same support." to FallbackTranslation("Il supporto è la struttura fisica: traliccio, edificio, torre dell'acqua, tetto o altro tipo. Più operatori e siti possono essere collegati allo stesso supporto.", "Der Träger ist die physische Struktur: Mast, Gebäude, Wasserturm, Dach oder ein anderer Typ. Mehrere Betreiber und Standorte können mit demselben Träger verknüpft sein.", "El soporte es la estructura física: torre, edificio, depósito de agua, tejado u otro tipo. Varios operadores y sitios pueden estar vinculados al mismo soporte."),
        "Support detail buttons open the location on the map, start navigation, share information or view community photos." to FallbackTranslation("I pulsanti del dettaglio supporto aprono la posizione sulla mappa, avviano la navigazione, condividono informazioni o mostrano foto della community.", "Schaltflächen in den Supportdetails öffnen den Ort auf der Karte, starten Navigation, teilen Informationen oder zeigen Community-Fotos.", "Los botones del detalle de soporte abren la ubicación en el mapa, inician navegación, comparten información o muestran fotos comunitarias."),
        "The site page describes an operator installation: operator banner, support, height, azimuths, frequencies, technologies, activation status and external links." to FallbackTranslation("La pagina sito descrive un'installazione operatore: banner operatore, supporto, altezza, azimut, frequenze, tecnologie, stato di attivazione e link esterni.", "Die Standortseite beschreibt eine Betreiberinstallation: Betreiberbanner, Träger, Höhe, Azimute, Frequenzen, Technologien, Aktivierungsstatus und externe Links.", "La página del sitio describe una instalación de operador: banner del operador, soporte, altura, azimuts, frecuencias, tecnologías, estado de activación y enlaces externos."),
        "Action buttons open tools related to the site. Some blocks can be hidden or moved from site page customization." to FallbackTranslation("I pulsanti azione aprono strumenti legati al sito. Alcuni blocchi possono essere nascosti o spostati dalla personalizzazione della pagina sito.", "Aktionsschaltflächen öffnen standortbezogene Werkzeuge. Einige Blöcke können über die Standortseiten-Anpassung ausgeblendet oder verschoben werden.", "Los botones de acción abren herramientas relacionadas con el sitio. Algunos bloques se pueden ocultar o mover desde la personalización de la página del sitio."),
        "The elevation profile compares your current position to the selected site. It helps estimate whether terrain may block the signal. The calculation depends on GPS position and online elevation data." to FallbackTranslation("Il profilo altimetrico confronta la tua posizione attuale con il sito selezionato. Aiuta a stimare se il rilievo può ostacolare il segnale. Il calcolo dipende da posizione GPS e dati altimetrici online.", "Das Höhenprofil vergleicht deine aktuelle Position mit dem ausgewählten Standort. Es hilft einzuschätzen, ob Gelände das Signal blockieren kann. Die Berechnung hängt von GPS-Position und Online-Höhendaten ab.", "El perfil altimétrico compara tu posición actual con el sitio seleccionado. Ayuda a estimar si el relieve puede bloquear la señal. El cálculo depende de la posición GPS y de datos de altitud en línea."),
        "The calculation gives theoretical throughput. Conservative mode lowers expectations, ideal mode shows a favorable scenario, and custom mode lets you adjust bands, technologies and modulations. Upload accounts for the fact that a phone transmits with far less power than an antenna." to FallbackTranslation("Il calcolo fornisce una velocità teorica. La modalità prudente riduce le aspettative, quella ideale mostra uno scenario favorevole e quella personalizzata permette di regolare bande, tecnologie e modulazioni. L'upload considera che un telefono trasmette con molta meno potenza di un'antenna.", "Die Berechnung liefert theoretischen Durchsatz. Der vorsichtige Modus senkt die Erwartungen, der ideale Modus zeigt ein günstiges Szenario und der benutzerdefinierte Modus erlaubt Anpassungen von Bändern, Technologien und Modulationen. Der Upload berücksichtigt, dass ein Telefon viel schwächer sendet als eine Antenne.", "El cálculo da una velocidad teórica. El modo prudente reduce expectativas, el modo ideal muestra un escenario favorable y el modo personalizado permite ajustar bandas, tecnologías y modulaciones. La subida considera que un teléfono transmite con mucha menos potencia que una antena."),
        "The 4G, 5G and project buttons choose which technologies are included. Band checkboxes select frequencies. Modulation sliders choose QPSK, 16 QAM, 64 QAM or 256 QAM depending on the expected radio level." to FallbackTranslation("I pulsanti 4G, 5G e progetto scelgono le tecnologie incluse. Le caselle delle bande selezionano le frequenze. Gli slider di modulazione scelgono QPSK, 16 QAM, 64 QAM o 256 QAM in base al livello radio previsto.", "Die 4G-, 5G- und Projekt-Schaltflächen wählen die einbezogenen Technologien. Band-Checkboxen wählen Frequenzen. Modulationsregler wählen je nach erwartetem Funkniveau QPSK, 16 QAM, 64 QAM oder 256 QAM.", "Los botones 4G, 5G y proyecto eligen las tecnologías incluidas. Las casillas de bandas seleccionan frecuencias. Los controles de modulación eligen QPSK, 16 QAM, 64 QAM o 256 QAM según el nivel radio esperado."),
        "The carousel shows available community photos. In full screen, controls let you close, switch photos and inspect the image more comfortably." to FallbackTranslation("Il carosello mostra le foto della community disponibili. A schermo intero, i controlli permettono di chiudere, cambiare foto e ispezionare l'immagine più comodamente.", "Das Karussell zeigt verfügbare Community-Fotos. Im Vollbild kannst du mit den Bedienelementen schließen, Fotos wechseln und das Bild bequemer betrachten.", "El carrusel muestra las fotos comunitarias disponibles. En pantalla completa, los controles permiten cerrar, cambiar de foto e inspeccionar la imagen con más comodidad."),
        "SignalQuest upload lets you attach images to a support or site. Select files, check the target operator, add a description if needed, then start the upload. Upload requires a network connection." to FallbackTranslation("L'invio SignalQuest permette di allegare immagini a un supporto o sito. Seleziona file, controlla l'operatore target, aggiungi una descrizione se serve, poi avvia l'invio. L'upload richiede connessione di rete.", "Mit dem SignalQuest-Upload kannst du Bilder an einen Träger oder Standort anhängen. Wähle Dateien, prüfe den Zielbetreiber, füge bei Bedarf eine Beschreibung hinzu und starte den Upload. Der Upload benötigt eine Netzwerkverbindung.", "El envío SignalQuest permite adjuntar imágenes a un soporte o sitio. Selecciona archivos, comprueba el operador objetivo, añade una descripción si hace falta y empieza el envío. Requiere conexión de red."),
        "From a detail page, the Share button opens options. You can include support details, frequencies, activation dates, best speedtest, throughput calculator and sometimes the elevation profile." to FallbackTranslation("Da una scheda dettaglio, il pulsante Condividi apre le opzioni. Puoi includere dettagli del supporto, frequenze, date di attivazione, miglior speedtest, calcolatore di velocità e talvolta profilo altimetrico.", "Auf einer Detailseite öffnet Teilen die Optionen. Du kannst Supportdetails, Frequenzen, Aktivierungsdaten, besten Speedtest, Durchsatzrechner und manchmal das Höhenprofil einbeziehen.", "Desde una ficha, el botón Compartir abre opciones. Puedes incluir detalles del soporte, frecuencias, fechas de activación, mejor speedtest, calculadora de velocidad y a veces el perfil altimétrico."),
        "Each checkbox adds or removes a block from the final image. The image respects the units chosen in settings, such as distances in kilometers or miles." to FallbackTranslation("Ogni casella aggiunge o rimuove un blocco dall'immagine finale. L'immagine rispetta le unità scelte nelle impostazioni, come distanze in chilometri o miglia.", "Jede Checkbox fügt einen Block zum endgültigen Bild hinzu oder entfernt ihn. Das Bild nutzt die in den Einstellungen gewählten Einheiten, etwa Kilometer oder Meilen.", "Cada casilla añade o quita un bloque de la imagen final. La imagen respeta las unidades elegidas en ajustes, como distancias en kilómetros o millas."),
        "Settings group theme, OLED mode, One UI design, language, default operator, metric or imperial units and settings navigation style." to FallbackTranslation("Le impostazioni raggruppano tema, modalità OLED, design One UI, lingua, operatore predefinito, unità metriche o imperiali e stile di navigazione.", "Einstellungen bündeln Design, OLED-Modus, One-UI-Design, Sprache, Standardbetreiber, metrische oder imperiale Einheiten und Navigationsstil.", "Los ajustes agrupan tema, modo OLED, diseño One UI, idioma, operador predeterminado, unidades métricas o imperiales y estilo de navegación."),
        "Page mode makes settings more compartmentalized. Split screen shows compatible screens in two panels: context on the left, detail or tool on the right." to FallbackTranslation("La modalità pagina rende le impostazioni più compartimentate. Lo schermo diviso mostra le schermate compatibili in due pannelli: contesto a sinistra, dettaglio o strumento a destra.", "Der Seitenmodus unterteilt Einstellungen stärker. Die geteilte Ansicht zeigt kompatible Seiten in zwei Bereichen: Kontext links, Detail oder Werkzeug rechts.", "El modo por páginas compartimenta más los ajustes. La pantalla dividida muestra pantallas compatibles en dos paneles: contexto a la izquierda, detalle o herramienta a la derecha."),
        "This menu is used to show, hide or move page blocks. For Home, you can also enable the Help button and choose its corner." to FallbackTranslation("Questo menu serve a mostrare, nascondere o spostare i blocchi delle pagine. Per la Home puoi anche attivare il pulsante Aiuto e scegliere il suo angolo.", "Dieses Menü zeigt, versteckt oder verschiebt Seitenblöcke. Für die Startseite kannst du auch die Hilfe-Schaltfläche aktivieren und ihre Ecke wählen.", "Este menú sirve para mostrar, ocultar o mover bloques de páginas. Para Inicio, también puedes activar el botón Ayuda y elegir su esquina."),
        "The database contains ANFR information used by detail pages, the map and searches. Download can be started from Home or Settings. Progress notifications take you to the corresponding section." to FallbackTranslation("Il database contiene informazioni ANFR usate da schede dettaglio, mappa e ricerche. Il download può partire da Home o Impostazioni. Le notifiche di avanzamento portano alla sezione corrispondente.", "Die Datenbank enthält ANFR-Informationen für Detailseiten, Karte und Suche. Der Download kann über Startseite oder Einstellungen gestartet werden. Fortschrittsbenachrichtigungen führen zum passenden Bereich.", "La base contiene información ANFR usada por fichas, mapa y búsquedas. La descarga puede iniciarse desde Inicio o Ajustes. Las notificaciones de progreso llevan a la sección correspondiente."),
        "Offline maps let you display an area without relying on network tiles. Download an area, select the offline layer, then use the map normally inside the available area." to FallbackTranslation("Le mappe offline permettono di visualizzare un'area senza dipendere dai tile di rete. Scarica un'area, seleziona il livello offline e usa normalmente la mappa nell'area disponibile.", "Offline-Karten zeigen einen Bereich ohne Netz-Kacheln. Lade ein Gebiet herunter, wähle die Offline-Ebene und nutze die Karte im verfügbaren Bereich normal.", "Los mapas sin conexión permiten mostrar una zona sin depender de teselas de red. Descarga un área, selecciona la capa sin conexión y usa el mapa normalmente dentro de la zona disponible."),
        "About shows the GeoTower version, data sources, development information and useful links. The side bar lets you quickly switch sections when the screen is wide enough." to FallbackTranslation("Informazioni mostra versione GeoTower, fonti dati, informazioni di sviluppo e link utili. La barra laterale permette di cambiare sezione rapidamente quando lo schermo è abbastanza largo.", "Über zeigt GeoTower-Version, Datenquellen, Entwicklungsinformationen und nützliche Links. Die Seitenleiste erlaubt schnellen Bereichswechsel, wenn der Bildschirm breit genug ist.", "Acerca de muestra la versión de GeoTower, fuentes de datos, información de desarrollo y enlaces útiles. La barra lateral permite cambiar rápido de sección cuando la pantalla es lo bastante ancha."),
        "If you come from settings or go back there from About, GeoTower limits unnecessary navigation stacking so you do not need to press Back many times." to FallbackTranslation("Se arrivi dalle impostazioni o ci torni da Informazioni, GeoTower limita gli accumuli inutili di navigazione, così non devi premere Indietro troppe volte.", "Wenn du aus den Einstellungen kommst oder von Über dorthin zurückgehst, begrenzt GeoTower unnötige Navigationsstapel, damit du nicht oft Zurück drücken musst.", "Si vienes de ajustes o vuelves allí desde Acerca de, GeoTower limita apilados de navegación innecesarios para que no tengas que pulsar Atrás muchas veces."),
        "The same icons appear across several screens. They generally keep the same role." to FallbackTranslation("Le stesse icone compaiono in più schermate e in genere mantengono lo stesso ruolo.", "Dieselben Symbole erscheinen auf mehreren Seiten und behalten meist dieselbe Rolle.", "Los mismos iconos aparecen en varias pantallas y generalmente mantienen el mismo rol."),
        "If no antenna appears, check the database, filters and search area. If GPS does not respond, check Android permission and wait a few seconds outside or near a window. If the offline map does not appear, make sure the file covers the visible area." to FallbackTranslation("Se non appare alcuna antenna, controlla database, filtri e area di ricerca. Se il GPS non risponde, controlla il permesso Android e attendi qualche secondo all'aperto o vicino a una finestra. Se la mappa offline non appare, verifica che il file copra l'area visibile.", "Wenn keine Antenne erscheint, prüfe Datenbank, Filter und Suchbereich. Wenn GPS nicht reagiert, prüfe die Android-Berechtigung und warte draußen oder am Fenster einige Sekunden. Wenn die Offline-Karte nicht erscheint, stelle sicher, dass die Datei den sichtbaren Bereich abdeckt.", "Si no aparece ninguna antena, comprueba la base, filtros y zona de búsqueda. Si el GPS no responde, revisa el permiso Android y espera unos segundos fuera o cerca de una ventana. Si el mapa sin conexión no aparece, asegúrate de que el archivo cubre la zona visible."),
        "Zoom + / -" to FallbackTranslation("Zoom + / -", "Zoom + / -", "Zoom + / -"),
        "Live search" to FallbackTranslation("Ricerca live", "Live-Suche", "Búsqueda en directo"),
        "Share" to FallbackTranslation("Condividi", "Teilen", "Compartir"),
        "Operator site" to FallbackTranslation("Sito operatore", "Betreiberstandort", "Sitio del operador"),
        "Recalculate" to FallbackTranslation("Ricalcola", "Neu berechnen", "Recalcular"),
        "Optimal distance" to FallbackTranslation("Distanza ottimale", "Optimale Entfernung", "Distancia óptima"),
        "Back arrow" to FallbackTranslation("Freccia indietro", "Zurück-Pfeil", "Flecha atrás"),
        "Magnifier" to FallbackTranslation("Lente", "Lupe", "Lupa"),
        "X" to FallbackTranslation("X", "X", "X"),
        "Gear" to FallbackTranslation("Ingranaggio", "Zahnrad", "Engranaje"),
        "Refresh" to FallbackTranslation("Aggiorna", "Aktualisieren", "Actualizar"),
        "Opens the list of sites around your position or searched area." to FallbackTranslation("Apre la lista dei siti intorno alla tua posizione o all'area cercata.", "Öffnet die Liste der Standorte um deine Position oder den Suchbereich.", "Abre la lista de sitios alrededor de tu posición o zona buscada."),
        "Opens the interactive map with markers, filters and map tools." to FallbackTranslation("Apre la mappa interattiva con marcatori, filtri e strumenti mappa.", "Öffnet die interaktive Karte mit Markern, Filtern und Kartenwerkzeugen.", "Abre el mapa interactivo con marcadores, filtros y herramientas de mapa."),
        "Opens the orientation tool to aim toward antennas around you." to FallbackTranslation("Apre lo strumento di orientamento verso le antenne intorno a te.", "Öffnet das Orientierungswerkzeug zum Ausrichten auf Antennen um dich herum.", "Abre la herramienta de orientación para apuntar hacia antenas cercanas."),
        "Opens preferences, customization, offline maps and database management." to FallbackTranslation("Apre preferenze, personalizzazione, mappe offline e gestione database.", "Öffnet Einstellungen, Anpassung, Offline-Karten und Datenbankverwaltung.", "Abre preferencias, personalización, mapas sin conexión y gestión de base de datos."),
        "Shows version, data sources and development information." to FallbackTranslation("Mostra versione, fonti dati e informazioni di sviluppo.", "Zeigt Version, Datenquellen und Entwicklungsinformationen.", "Muestra versión, fuentes de datos e información de desarrollo."),
        "Opens this help center. Its position is configured in Home page customization." to FallbackTranslation("Apre questo centro assistenza. La posizione si configura nella personalizzazione della Home.", "Öffnet diese Hilfe. Ihre Position wird in der Startseiten-Anpassung konfiguriert.", "Abre este centro de ayuda. Su posición se configura en la personalización de Inicio."),
        "Type a city, address, postal code, support ID, ANFR ID, technology, support type or GPS coordinates." to FallbackTranslation("Digita città, indirizzo, codice postale, ID supporto, ID ANFR, tecnologia, tipo supporto o coordinate GPS.", "Gib Stadt, Adresse, Postleitzahl, Support-ID, ANFR-ID, Technologie, Trägertyp oder GPS-Koordinaten ein.", "Escribe ciudad, dirección, código postal, ID de soporte, ID ANFR, tecnología, tipo de soporte o coordenadas GPS."),
        "Clears the search and returns to the previous list." to FallbackTranslation("Cancella la ricerca e torna alla lista precedente.", "Löscht die Suche und kehrt zur vorherigen Liste zurück.", "Borra la búsqueda y vuelve a la lista anterior."),
        "Inserts common searches. They can be hidden from page customization." to FallbackTranslation("Inserisce ricerche comuni. Possono essere nascoste dalla personalizzazione delle pagine.", "Fügt häufige Suchen ein. Sie können in der Seitenanpassung ausgeblendet werden.", "Inserta búsquedas comunes. Se pueden ocultar desde la personalización de páginas."),
        "Shows the list of available codes and how to use them." to FallbackTranslation("Mostra la lista dei codici disponibili e come usarli.", "Zeigt die Liste verfügbarer Codes und deren Nutzung.", "Muestra la lista de códigos disponibles y cómo usarlos."),
        "Adds more results to the list." to FallbackTranslation("Aggiunge altri risultati alla lista.", "Fügt der Liste weitere Ergebnisse hinzu.", "Añade más resultados a la lista."),
        "Increases the search area when no relevant site is found." to FallbackTranslation("Aumenta l'area di ricerca quando non viene trovato un sito pertinente.", "Vergrößert den Suchbereich, wenn kein relevanter Standort gefunden wird.", "Aumenta la zona de búsqueda cuando no se encuentra un sitio relevante."),
        "Starts or restarts GPS search and centers the map on your position." to FallbackTranslation("Avvia o riavvia la ricerca GPS e centra la mappa sulla tua posizione.", "Startet oder wiederholt die GPS-Suche und zentriert die Karte auf deine Position.", "Inicia o reinicia la búsqueda GPS y centra el mapa en tu posición."),
        "Zooms the map in or out." to FallbackTranslation("Avvicina o allontana la mappa.", "Vergrößert oder verkleinert die Karte.", "Acerca o aleja el mapa."),
        "Shows the current map orientation if enabled." to FallbackTranslation("Mostra l'orientamento attuale della mappa se attivo.", "Zeigt die aktuelle Kartenausrichtung, wenn aktiviert.", "Muestra la orientación actual del mapa si está activada."),
        "Shows an estimate of visible distances." to FallbackTranslation("Mostra una stima delle distanze visibili.", "Zeigt eine Schätzung sichtbarer Entfernungen.", "Muestra una estimación de distancias visibles."),
        "Updates the target antenna or site based on your position." to FallbackTranslation("Aggiorna l'antenna o il sito target in base alla tua posizione.", "Aktualisiert die Zielantenne oder den Zielstandort anhand deiner Position.", "Actualiza la antena o sitio objetivo según tu posición."),
        "Stops active tracking and returns to normal mode." to FallbackTranslation("Ferma il tracciamento attivo e torna alla modalità normale.", "Stoppt aktives Tracking und kehrt zum normalen Modus zurück.", "Detiene el seguimiento activo y vuelve al modo normal."),
        "Centers the GeoTower map on this support." to FallbackTranslation("Centra la mappa GeoTower su questo supporto.", "Zentriert die GeoTower-Karte auf diesen Träger.", "Centra el mapa GeoTower en este soporte."),
        "Opens the phone navigation app toward the coordinates." to FallbackTranslation("Apre l'app di navigazione del telefono verso le coordinate.", "Öffnet die Navigations-App des Telefons zu den Koordinaten.", "Abre la app de navegación del teléfono hacia las coordenadas."),
        "Opens sharing and image generation options." to FallbackTranslation("Apre le opzioni di condivisione e generazione immagine.", "Öffnet Optionen zum Teilen und zur Bilderzeugung.", "Abre opciones de compartir y generación de imagen."),
        "Shows community photos and diagrams if available." to FallbackTranslation("Mostra foto della community e schemi se disponibili.", "Zeigt Community-Fotos und Diagramme, wenn verfügbar.", "Muestra fotos comunitarias y esquemas si están disponibles."),
        "Opens the detail page for an operator installed on the support." to FallbackTranslation("Apre la pagina dettaglio di un operatore installato sul supporto.", "Öffnet die Detailseite eines auf dem Träger installierten Betreibers.", "Abre la ficha detallada de un operador instalado en el soporte."),
        "Calculates terrain between your position and the site." to FallbackTranslation("Calcola il rilievo tra la tua posizione e il sito.", "Berechnet das Gelände zwischen deiner Position und dem Standort.", "Calcula el relieve entre tu posición y el sitio."),
        "Opens the throughput calculator based on frequencies and radio assumptions." to FallbackTranslation("Apre il calcolatore di velocità basato su frequenze e ipotesi radio.", "Öffnet den Durchsatzrechner auf Basis von Frequenzen und Funkannahmen.", "Abre la calculadora de velocidad basada en frecuencias y supuestos radio."),
        "Opens page block customization when available." to FallbackTranslation("Apre la personalizzazione dei blocchi pagina quando disponibile.", "Öffnet die Anpassung der Seitenblöcke, wenn verfügbar.", "Abre la personalización de bloques de página cuando está disponible."),
        "Runs the analysis again with current position and data." to FallbackTranslation("Rilancia l'analisi con posizione e dati attuali.", "Führt die Analyse erneut mit aktueller Position und aktuellen Daten aus.", "Ejecuta de nuevo el análisis con la posición y datos actuales."),
        "Temporarily saves the request to run it when the connection returns." to FallbackTranslation("Salva temporaneamente la richiesta per eseguirla quando torna la connessione.", "Speichert die Anfrage vorübergehend, um sie bei Rückkehr der Verbindung auszuführen.", "Guarda temporalmente la solicitud para ejecutarla cuando vuelva la conexión."),
        "Unlocks fine settings for modulation, technologies and bands." to FallbackTranslation("Sblocca impostazioni avanzate per modulazione, tecnologie e bande.", "Schaltet Feineinstellungen für Modulation, Technologien und Bänder frei.", "Desbloquea ajustes finos de modulación, tecnologías y bandas."),
        "Shows the area where the user is most likely to be inside the antenna emission cone." to FallbackTranslation("Mostra l'area in cui è più probabile trovarsi nel cono di emissione dell'antenna.", "Zeigt den Bereich, in dem sich der Nutzer am wahrscheinlichsten im Sendekegel der Antenne befindet.", "Muestra la zona donde el usuario tiene más probabilidad de estar dentro del cono de emisión de la antena."),
        "Visualizes the optimal distance circle and points where estimated throughput is strongest." to FallbackTranslation("Visualizza il cerchio della distanza ottimale e i punti in cui la velocità stimata è più forte.", "Visualisiert den Kreis der optimalen Entfernung und Punkte mit dem höchsten geschätzten Durchsatz.", "Visualiza el círculo de distancia óptima y los puntos donde la velocidad estimada es mayor."),
        "Returns to the previous screen or local contents." to FallbackTranslation("Torna alla schermata precedente o al sommario locale.", "Kehrt zum vorherigen Bildschirm oder lokalen Inhalt zurück.", "Vuelve a la pantalla anterior o al índice local."),
        "Opens or represents search." to FallbackTranslation("Apre o rappresenta la ricerca.", "Öffnet oder steht für Suche.", "Abre o representa la búsqueda."),
        "Clears a field or closes a mode." to FallbackTranslation("Cancella un campo o chiude una modalità.", "Leert ein Feld oder schließt einen Modus.", "Borra un campo o cierra un modo."),
        "Opens settings or block customization." to FallbackTranslation("Apre impostazioni o personalizzazione blocchi.", "Öffnet Einstellungen oder Blockanpassung.", "Abre ajustes o personalización de bloques."),
        "Opens sharing options." to FallbackTranslation("Apre le opzioni di condivisione.", "Öffnet Teilen-Optionen.", "Abre opciones de compartir."),
        "Starts a database or map download." to FallbackTranslation("Avvia il download di un database o di una mappa.", "Startet einen Datenbank- oder Kartendownload.", "Inicia una descarga de base de datos o mapa."),
        "Restarts a calculation, check or resets settings depending on context." to FallbackTranslation("Riavvia un calcolo, una verifica o ripristina le impostazioni secondo il contesto.", "Startet je nach Kontext eine Berechnung oder Prüfung neu oder setzt Einstellungen zurück.", "Reinicia un cálculo, comprobación o restablece ajustes según el contexto."),

        // Paramètres : apparence, palette, opérateur et personnalisation.
        "Appearance" to FallbackTranslation("Aspetto", "Darstellung", "Apariencia"),
        "Mapping" to FallbackTranslation("Cartografia", "Kartografie", "Cartografía"),
        "Preferences" to FallbackTranslation("Preferenze", "Einstellungen", "Preferencias"),
        "System" to FallbackTranslation("Sistema", "System", "Sistema"),
        "App Icon" to FallbackTranslation("Icona app", "App-Symbol", "Icono de la app"),
        "In-app logo" to FallbackTranslation("Logo nell'app", "Logo in der App", "Logo en la app"),
        "Chooses the artwork used on Home, splash and About." to FallbackTranslation("Sceglie l'illustrazione usata in Home, splash e Informazioni.", "Wählt die Grafik für Startseite, Splash und Über.", "Elige la ilustración usada en Inicio, splash y Acerca de."),
        "Automatic" to FallbackTranslation("Automatico", "Automatisch", "Automático"),
        "Follows the active app icon and theme." to FallbackTranslation("Segue l'icona app attiva e il tema.", "Folgt dem aktiven App-Symbol und Design.", "Sigue el icono activo de la app y el tema."),
        "Color on dark" to FallbackTranslation("Colore su scuro", "Farbe auf dunkel", "Color sobre oscuro"),
        "Color on light" to FallbackTranslation("Colore su chiaro", "Farbe auf hell", "Color sobre claro"),
        "Light monochrome" to FallbackTranslation("Monocromo chiaro", "Helles Monochrom", "Monocromo claro"),
        "Dark monochrome" to FallbackTranslation("Monocromo scuro", "Dunkles Monochrom", "Monocromo oscuro"),
        "Muted monochrome" to FallbackTranslation("Monocromo attenuato", "Gedämpftes Monochrom", "Monocromo apagado"),
        "The app will restart to apply the change." to FallbackTranslation("L'app si riavvierà per applicare la modifica.", "Die App startet neu, um die Änderung anzuwenden.", "La app se reiniciará para aplicar el cambio."),
        "Color palette" to FallbackTranslation("Palette colori", "Farbpalette", "Paleta de colores"),
        "Color source" to FallbackTranslation("Origine colori", "Farbquelle", "Fuente de color"),
        "Dynamic mode (wallpaper) takes priority. Otherwise, choose a native Material 3 palette." to FallbackTranslation("La modalità dinamica (sfondo) ha priorità. Altrimenti scegli una palette nativa Material 3.", "Der dynamische Modus (Hintergrundbild) hat Vorrang. Andernfalls wähle eine native Material-3-Palette.", "El modo dinámico (fondo de pantalla) tiene prioridad. Si no, elige una paleta Material 3 nativa."),
        "Dynamic (wallpaper)" to FallbackTranslation("Dinamico (sfondo)", "Dynamisch (Hintergrundbild)", "Dinámico (fondo)"),
        "Automatically uses the phone system colors (Material You)." to FallbackTranslation("Usa automaticamente i colori di sistema del telefono (Material You).", "Verwendet automatisch die Systemfarben des Telefons (Material You).", "Usa automáticamente los colores del sistema del teléfono (Material You)."),
        "Material Baseline" to FallbackTranslation("Material Baseline", "Material Baseline", "Material Baseline"),
        "Native reference Android Material 3 palette" to FallbackTranslation("Palette nativa Android Material 3 di riferimento", "Native Referenzpalette von Android Material 3", "Paleta nativa de referencia Android Material 3"),
        "Material Red" to FallbackTranslation("Material Red", "Material Red", "Material Red"),
        "Material 3 red system palette" to FallbackTranslation("Palette sistema rossa Material 3", "Rote Systempalette Material 3", "Paleta del sistema roja Material 3"),
        "Material Green" to FallbackTranslation("Material Green", "Material Green", "Material Green"),
        "Material 3 green system palette" to FallbackTranslation("Palette sistema verde Material 3", "Grüne Systempalette Material 3", "Paleta del sistema verde Material 3"),
        "Material Blue" to FallbackTranslation("Material Blue", "Material Blue", "Material Blue"),
        "Material 3 blue system palette" to FallbackTranslation("Palette sistema blu Material 3", "Blaue Systempalette Material 3", "Paleta del sistema azul Material 3"),
        "Material Cyan" to FallbackTranslation("Material Cyan", "Material Cyan", "Material Cyan"),
        "Material 3 cyan system palette" to FallbackTranslation("Palette sistema ciano Material 3", "Cyan-Systempalette Material 3", "Paleta del sistema cian Material 3"),
        "Material Teal" to FallbackTranslation("Material Teal", "Material Teal", "Material Teal"),
        "Material 3 teal system palette" to FallbackTranslation("Palette sistema teal Material 3", "Teal-Systempalette Material 3", "Paleta del sistema teal Material 3"),
        "Material Indigo" to FallbackTranslation("Material Indigo", "Material Indigo", "Material Indigo"),
        "Material 3 indigo system palette" to FallbackTranslation("Palette sistema indaco Material 3", "Indigo-Systempalette Material 3", "Paleta del sistema índigo Material 3"),
        "Material Rose" to FallbackTranslation("Material Rose", "Material Rose", "Material Rose"),
        "Expressive Material 3 rose system palette" to FallbackTranslation("Palette sistema rosa espressiva Material 3", "Expressive rosa Systempalette Material 3", "Paleta del sistema rosa expresiva Material 3"),
        "Material Amber" to FallbackTranslation("Material Amber", "Material Amber", "Material Amber"),
        "Material 3 amber system palette" to FallbackTranslation("Palette sistema ambra Material 3", "Bernstein-Systempalette Material 3", "Paleta del sistema ámbar Material 3"),
        "Material Graphite" to FallbackTranslation("Material Graphite", "Material Graphite", "Material Graphite"),
        "Material 3 graphite system palette" to FallbackTranslation("Palette sistema grafite Material 3", "Graphit-Systempalette Material 3", "Paleta del sistema grafito Material 3"),
        "(Long press and drag to reorder)" to FallbackTranslation("(Tieni premuto e trascina per riordinare)", "(Lange drücken und ziehen zum Neuordnen)", "(Mantén pulsado y arrastra para reordenar)"),
        "Warning: A high frequency (30 min) may increase background battery consumption." to FallbackTranslation("Attenzione: una frequenza alta (30 min) può aumentare il consumo della batteria in background.", "Warnung: Eine hohe Frequenz (30 min) kann den Akkuverbrauch im Hintergrund erhöhen.", "Aviso: una frecuencia alta (30 min) puede aumentar el consumo de batería en segundo plano."),
        "MapLibre" to FallbackTranslation("MapLibre", "MapLibre", "MapLibre"),
        "OpenTopoMap" to FallbackTranslation("OpenTopoMap", "OpenTopoMap", "OpenTopoMap"),
        "Speedtest" to FallbackTranslation("Speedtest", "Speedtest", "Speedtest"),
        "Are you sure you want to restore default settings? This will delete all settings you have made in the app." to FallbackTranslation("Vuoi davvero ripristinare le impostazioni predefinite? Verranno eliminate tutte le impostazioni fatte nell'app.", "Möchtest du wirklich die Standardeinstellungen wiederherstellen? Dadurch werden alle App-Einstellungen gelöscht, die du vorgenommen hast.", "¿Seguro que quieres restaurar los ajustes predeterminados? Se eliminarán todos los ajustes que hayas hecho en la app."),
        "Orange" to FallbackTranslation("Orange", "Orange", "Orange"),
        "SFR" to FallbackTranslation("SFR", "SFR", "SFR"),
        "Bouygues" to FallbackTranslation("Bouygues", "Bouygues", "Bouygues"),
        "Free" to FallbackTranslation("Free", "Free", "Free"),
        "Techno." to FallbackTranslation("Tecno.", "Techno.", "Tecno."),

        // Profil altimétrique et calculateur de débit.
        "The calculation uses IGN elevation data and requires an internet connection. Try again when the network is available." to FallbackTranslation("Il calcolo usa dati altimetrici IGN e richiede una connessione internet. Riprova quando la rete è disponibile.", "Die Berechnung nutzt IGN-Höhendaten und benötigt eine Internetverbindung. Versuche es erneut, wenn das Netz verfügbar ist.", "El cálculo usa datos de altitud IGN y requiere conexión a internet. Vuelve a intentarlo cuando haya red."),
        "The calculation uses IGN elevation data and requires an internet connection. You can temporarily save your current position to run the calculation automatically when the network returns." to FallbackTranslation("Il calcolo usa dati altimetrici IGN e richiede una connessione internet. Puoi salvare temporaneamente la posizione attuale per eseguirlo automaticamente quando torna la rete.", "Die Berechnung nutzt IGN-Höhendaten und benötigt eine Internetverbindung. Du kannst deine aktuelle Position vorübergehend speichern, damit die Berechnung automatisch startet, wenn die Verbindung zurückkehrt.", "El cálculo usa datos de altitud IGN y requiere conexión a internet. Puedes guardar temporalmente tu posición actual para ejecutar el cálculo automáticamente cuando vuelva la red."),
        "Your current position and this site are temporarily saved. The elevation profile will be calculated automatically when the connection returns." to FallbackTranslation("La tua posizione attuale e questo sito sono salvati temporaneamente. Il profilo altimetrico sarà calcolato automaticamente quando torna la connessione.", "Deine aktuelle Position und dieser Standort wurden vorübergehend gespeichert. Das Höhenprofil wird automatisch berechnet, wenn die Verbindung zurückkehrt.", "Tu posición actual y este sitio se han guardado temporalmente. El perfil altimétrico se calculará automáticamente cuando vuelva la conexión."),
        "Calculated at" to FallbackTranslation("Calcolato alle", "Berechnet um", "Calculado a las"),
        "GPS coordinates used" to FallbackTranslation("Coordinate GPS usate", "Verwendete GPS-Koordinaten", "Coordenadas GPS usadas"),
        "You are offline. A previously calculated elevation profile exists for this site. Do you want to display it?" to FallbackTranslation("Sei offline. Esiste un profilo altimetrico calcolato in precedenza per questo sito. Vuoi visualizzarlo?", "Du bist offline. Für diesen Standort gibt es ein zuvor berechnetes Höhenprofil. Möchtest du es anzeigen?", "Estás sin conexión. Existe un perfil altimétrico calculado previamente para este sitio. ¿Quieres mostrarlo?"),
        "Panel height for the selected frequency." to FallbackTranslation("Altezza del pannello per la frequenza selezionata.", "Panelhöhe für die ausgewählte Frequenz.", "Altura del panel para la frecuencia seleccionada."),
        "Start point altitude + 1.5 m." to FallbackTranslation("Altitudine del punto di partenza + 1,5 m.", "Höhe des Startpunkts + 1,5 m.", "Altitud del punto inicial + 1,5 m."),
        "Site altitude + panel height." to FallbackTranslation("Altitudine del sito + altezza pannello.", "Standorthöhe + Panelhöhe.", "Altitud del sitio + altura del panel."),
        "Terrain does not cut the direct path between you and the panel" to FallbackTranslation("Il terreno non taglia il percorso diretto tra te e il pannello", "Das Gelände schneidet den direkten Pfad zwischen dir und dem Panel nicht", "El terreno no corta el trayecto directo entre tú y el panel"),
        "Terrain may cut the direct path" to FallbackTranslation("Il terreno può tagliare il percorso diretto", "Das Gelände kann den direkten Pfad schneiden", "El terreno puede cortar el trayecto directo"),
        "60% Fresnel clear" to FallbackTranslation("60% Fresnel libero", "60% Fresnel frei", "60% Fresnel despejado"),
        "60% Fresnel obstructed" to FallbackTranslation("60% Fresnel ostruito", "60% Fresnel verdeckt", "60% Fresnel obstruido"),
        "The Fresnel zone is the volume around the radio path. For a more reliable link, at least 60% of this zone is usually kept clear, so terrain can affect the signal even if it does not exactly cut the direct path." to FallbackTranslation("La zona di Fresnel è il volume attorno al percorso radio. Per un collegamento più affidabile, di solito si mantiene libero almeno il 60% di questa zona; quindi il terreno può influire sul segnale anche se non taglia esattamente il percorso diretto.", "Die Fresnel-Zone ist das Volumen um den Funkpfad. Für eine zuverlässigere Verbindung bleiben normalerweise mindestens 60% dieser Zone frei, daher kann Gelände das Signal beeinflussen, selbst wenn es den direkten Pfad nicht exakt schneidet.", "La zona de Fresnel es el volumen alrededor del trayecto radio. Para un enlace más fiable, normalmente se mantiene libre al menos el 60% de esta zona, por lo que el relieve puede afectar a la señal aunque no corte exactamente el trayecto directo."),
        "Source: IGN Geoplatform - RGE ALTI" to FallbackTranslation("Fonte: IGN Geopiattaforma - RGE ALTI", "Quelle: IGN Geoplattform - RGE ALTI", "Fuente: IGN Geoplataforma - RGE ALTI"),
        "No usable 4G or 5G band was detected for this site." to FallbackTranslation("Nessuna banda 4G o 5G utilizzabile è stata rilevata per questo sito.", "Für diesen Standort wurde kein nutzbares 4G- oder 5G-Band erkannt.", "No se detectó ninguna banda 4G o 5G utilizable para este sitio."),
        "The result is a theoretical radio throughput: it does not account for distance, signal level, SINR, network load, backhaul, or phone limits." to FallbackTranslation("Il risultato è una velocità radio teorica: non considera distanza, livello del segnale, SINR, carico rete, backhaul o limiti del telefono.", "Das Ergebnis ist ein theoretischer Funkdurchsatz: Entfernung, Signalpegel, SINR, Netzlast, Backhaul und genaue Telefonlimits werden nicht berücksichtigt.", "El resultado es una velocidad radio teórica: no considera distancia, nivel de señal, SINR, carga de red, backhaul ni límites del teléfono."),
        "Upload is weighted for a handset: lower transmit power, less MIMO and usually lower modulation than download." to FallbackTranslation("L'upload è ponderato per un telefono: potenza di trasmissione più bassa, meno MIMO e modulazione di solito inferiore al download.", "Upload wird für ein Mobilgerät gewichtet: geringere Sendeleistung, weniger MIMO und meist niedrigere Modulation als beim Download.", "La subida se pondera para un teléfono: menor potencia de transmisión, menos MIMO y normalmente modulación inferior a la descarga."),
        "Panel/support height unavailable: unable to estimate the main cone zone." to FallbackTranslation("Altezza pannello/supporto non disponibile: impossibile stimare la zona principale del cono.", "Panel-/Trägerhöhe nicht verfügbar: Hauptzone des Kegels kann nicht geschätzt werden.", "Altura del panel/soporte no disponible: no se puede estimar la zona principal del cono."),
        "Assumption: panel/support height, handset at 1.5 m, typical vertical tilt 4°-8° with a 6° nominal point." to FallbackTranslation("Ipotesi: altezza pannello/supporto, telefono a 1,5 m, tilt verticale tipico 4°-8° con punto nominale a 6°.", "Annahme: Panel-/Trägerhöhe, Mobilgerät auf 1,5 m, typischer vertikaler Tilt 4°-8° mit 6° nominalem Punkt.", "Supuesto: altura panel/soporte, teléfono a 1,5 m, inclinación vertical típica 4°-8° con punto nominal a 6°."),
        "The circle marks the optimal distance; dots show panel axes where signal should be strongest." to FallbackTranslation("Il cerchio indica la distanza ottimale; i punti mostrano gli assi dei pannelli dove il segnale dovrebbe essere più forte.", "Der Kreis markiert die optimale Entfernung; Punkte zeigen Panelachsen, auf denen das Signal am stärksten sein sollte.", "El círculo marca la distancia óptima; los puntos muestran ejes de panel donde la señal debería ser más fuerte."),
        "4G download" to FallbackTranslation("Download 4G", "4G-Download", "Descarga 4G"),
        "4G upload" to FallbackTranslation("Upload 4G", "4G-Upload", "Subida 4G"),
        "5G download" to FallbackTranslation("Download 5G", "5G-Download", "Descarga 5G"),
        "5G upload" to FallbackTranslation("Upload 5G", "5G-Upload", "Subida 5G"),
        "Conservative profile: average modulation, upload strongly limited by handset transmit power." to FallbackTranslation("Profilo prudente: modulazione media, upload fortemente limitato dalla potenza di trasmissione del telefono.", "Vorsichtiges Profil: durchschnittliche Modulation, Upload stark durch die Sendeleistung des Mobilgeräts begrenzt.", "Perfil prudente: modulación media, subida muy limitada por la potencia de transmisión del teléfono."),
        "Ideal profile: plausible very good radio conditions, but upload remains capped on the handset side." to FallbackTranslation("Profilo ideale: condizioni radio plausibilmente molto buone, ma upload ancora limitato lato telefono.", "Ideales Profil: plausibel sehr gute Funkbedingungen, aber Upload bleibt auf Mobilgerät-Seite begrenzt.", "Perfil ideal: condiciones radio muy buenas y plausibles, pero la subida sigue limitada del lado del teléfono."),
        "Custom profile: DL/UL modulations are manually tuned, with upload still limited like a handset." to FallbackTranslation("Profilo personalizzato: modulazioni DL/UL regolate manualmente, con upload ancora limitato come su un telefono.", "Benutzerdefiniertes Profil: DL/UL-Modulationen werden manuell eingestellt, Upload bleibt wie bei einem Mobilgerät begrenzt.", "Perfil personalizado: modulaciones DL/UL ajustadas manualmente, con subida aún limitada como en un teléfono."),
        "Standard profile: 4G MIMO 2x2 and 5G MIMO 4x4 for download, upload calculated like a real handset." to FallbackTranslation("Profilo standard: MIMO 2x2 in 4G e MIMO 4x4 in 5G per il download, upload calcolato come su un telefono reale.", "Standardprofil: 4G MIMO 2x2 und 5G MIMO 4x4 für Download, Upload wie bei einem realen Mobilgerät berechnet.", "Perfil estándar: MIMO 2x2 en 4G y MIMO 4x4 en 5G para descarga, subida calculada como en un teléfono real."),
        "Estimated radio throughput: excludes network load, real signal, backhaul and exact phone limits." to FallbackTranslation("Velocità radio stimata: esclude carico rete, segnale reale, backhaul e limiti esatti del telefono.", "Geschätzter Funkdurchsatz: ohne Netzlast, reales Signal, Backhaul und genaue Telefonlimits.", "Velocidad radio estimada: excluye carga de red, señal real, backhaul y límites exactos del teléfono."),
        "Site header" to FallbackTranslation("Intestazione sito", "Standortkopf", "Encabezado del sitio"),
        "Throughput summary" to FallbackTranslation("Riepilogo velocità", "Durchsatzübersicht", "Resumen de velocidad"),
        "Assumptions and filters" to FallbackTranslation("Ipotesi e filtri", "Annahmen und Filter", "Supuestos y filtros"),
        "Standard profile: 4G 256-QAM downlink with 2x2 MIMO, 64-QAM phone-side uplink, 5G n78 256-QAM downlink with 4x4 MIMO, 64-QAM uplink on 2 layers, DSS not counted twice." to FallbackTranslation("Profilo standard: 4G downlink 256-QAM con MIMO 2x2, uplink lato telefono 64-QAM, 5G n78 downlink 256-QAM con MIMO 4x4, uplink 64-QAM su 2 layer, DSS non contato due volte.", "Standardprofil: 4G 256-QAM Downlink mit 2x2 MIMO, 64-QAM Upload auf Telefonseite, 5G n78 256-QAM Downlink mit 4x4 MIMO, 64-QAM Upload auf 2 Layern, DSS nicht doppelt gezählt.", "Perfil estándar: 4G 256-QAM en descarga con MIMO 2x2, subida 64-QAM del lado del teléfono, 5G n78 256-QAM en descarga con MIMO 4x4, subida 64-QAM en 2 capas, DSS sin doble conteo."),
        "Ideal profile: plausible very good radio conditions, 4G downlink with 4x4 MIMO, 5G NR 256-QAM, more open aggregation and no DSS double counting." to FallbackTranslation("Profilo ideale: condizioni radio plausibilmente molto buone, 4G downlink con MIMO 4x4, 5G NR 256-QAM, aggregazione più aperta e nessun doppio conteggio DSS.", "Ideales Profil: plausibel sehr gute Funkbedingungen, 4G Downlink mit 4x4 MIMO, 5G NR 256-QAM, offenere Aggregation und keine DSS-Doppelzählung.", "Perfil ideal: condiciones radio muy buenas plausibles, 4G en descarga con MIMO 4x4, 5G NR 256-QAM, agregación más abierta y sin doble conteo DSS."),
        "These values weight the estimate: good SINR allows more efficient modulation, while weak RSRP reduces stability." to FallbackTranslation("Questi valori ponderano la stima: un buon SINR permette una modulazione più efficiente, mentre un RSRP debole riduce la stabilità.", "Diese Werte gewichten die Schätzung: gutes SINR ermöglicht effizientere Modulation, schwaches RSRP reduziert die Stabilität.", "Estos valores ponderan la estimación: un buen SINR permite modulación más eficiente, mientras un RSRP débil reduce la estabilidad."),
        "Move/zoom the mini map if needed, then tap the point to analyze." to FallbackTranslation("Sposta/ingrandisci la mini-mappa se necessario, poi tocca il punto da analizzare.", "Verschiebe oder zoome die Minikarte bei Bedarf und tippe dann auf den zu analysierenden Punkt.", "Mueve o amplía el mini mapa si hace falta y toca el punto que quieres analizar."),
        "Location permission denied: choose a point on the map or try again after enabling it." to FallbackTranslation("Permesso posizione negato: scegli un punto sulla mappa o riprova dopo averlo abilitato.", "Standortberechtigung verweigert: Wähle einen Punkt auf der Karte oder versuche es nach der Aktivierung erneut.", "Permiso de ubicación denegado: elige un punto en el mapa o inténtalo de nuevo tras activarlo."),
        "No position selected: the calculation keeps a neutral position coefficient." to FallbackTranslation("Nessuna posizione selezionata: il calcolo mantiene un coefficiente posizione neutro.", "Keine Position ausgewählt: Die Berechnung behält einen neutralen Positionskoeffizienten.", "No hay posición seleccionada: el cálculo mantiene un coeficiente de posición neutral."),
        "Panel azimuth unavailable: only the distance cone can be used." to FallbackTranslation("Azimut pannello non disponibile: si può usare solo il cono di distanza.", "Panel-Azimut nicht verfügbar: Nur der Entfernungskegel kann genutzt werden.", "Azimut del panel no disponible: solo se puede usar el cono de distancia."),
        "Radio cone unavailable: missing panel/support height." to FallbackTranslation("Cono radio non disponibile: altezza pannello/supporto mancante.", "Funkkegel nicht verfügbar: Panel-/Trägerhöhe fehlt.", "Cono radio no disponible: falta altura de panel/soporte."),
        "These choices adjust cell load, backhaul quality and the number of counted 4G carriers." to FallbackTranslation("Queste scelte regolano carico cella, qualità backhaul e numero di portanti 4G conteggiate.", "Diese Auswahl passt Zelllast, Backhaul-Qualität und Anzahl gezählter 4G-Träger an.", "Estas opciones ajustan carga de celda, calidad de backhaul y número de portadoras 4G contadas."),
        "Backhaul" to FallbackTranslation("Backhaul", "Backhaul", "Backhaul"),
        "1 carrier" to FallbackTranslation("1 portante", "1 Träger", "1 portadora"),
        "The selected QAM sets the raw radio throughput: higher modulation increases theoretical speed but assumes a better signal." to FallbackTranslation("Il QAM selezionato definisce la velocità radio grezza: una modulazione più alta aumenta la velocità teorica ma presuppone un segnale migliore.", "Das gewählte QAM bestimmt den rohen Funkdurchsatz: höhere Modulation erhöht die theoretische Geschwindigkeit, setzt aber ein besseres Signal voraus.", "El QAM seleccionado fija la velocidad radio bruta: una modulación más alta aumenta la velocidad teórica pero asume mejor señal."),
        "Environment multiplies the result: outdoor 100%, vehicle 85%, indoor 65%, deep indoor 45%." to FallbackTranslation("L'ambiente moltiplica il risultato: esterno 100%, veicolo 85%, interno 65%, interno profondo 45%.", "Die Umgebung multipliziert das Ergebnis: außen 100%, Fahrzeug 85%, innen 65%, tief innen 45%.", "El entorno multiplica el resultado: exterior 100%, vehículo 85%, interior 65%, interior profundo 45%."),
        "Position adjusts throughput by radio zone: in the beam 106%, too close 75%, too far 68%, outside azimuth 45%." to FallbackTranslation("La posizione regola la velocità per zona radio: nel fascio 106%, troppo vicino 75%, troppo lontano 68%, fuori azimut 45%.", "Die Position passt den Durchsatz nach Funkzone an: im Strahl 106%, zu nah 75%, zu weit 68%, außerhalb Azimut 45%.", "La posición ajusta la velocidad por zona radio: en el haz 106%, demasiado cerca 75%, demasiado lejos 68%, fuera de azimut 45%."),
        "Network load reduces per-user throughput: unknown 100%, light 90%, medium 68%, heavy 46%, saturated 28% on download." to FallbackTranslation("Il carico di rete riduce la velocità per utente: sconosciuto 100%, leggero 90%, medio 68%, alto 46%, saturo 28% in download.", "Netzlast reduziert den Durchsatz pro Nutzer: unbekannt 100%, leicht 90%, mittel 68%, hoch 46%, gesättigt 28% im Download.", "La carga de red reduce la velocidad por usuario: desconocida 100%, ligera 90%, media 68%, alta 46%, saturada 28% en descarga."),
        "Backhaul represents the link behind the antenna: fiber 100%, microwave link 84%, limited 55% on download." to FallbackTranslation("Il backhaul rappresenta il collegamento dietro l'antenna: fibra 100%, ponte radio 84%, limitato 55% in download.", "Backhaul steht für die Anbindung hinter der Antenne: Glasfaser 100%, Richtfunk 84%, begrenzt 55% im Download.", "El backhaul representa el enlace detrás de la antena: fibra 100%, radioenlace 84%, limitado 55% en descarga."),

        // Onboarding, notifications live, crédits et libellés communs.
        "OpenStreetMap" to FallbackTranslation("OpenStreetMap", "OpenStreetMap", "OpenStreetMap"),
        "Sat" to FallbackTranslation("Sat", "Sat", "Sat"),
        "LAT" to FallbackTranslation("LAT", "LAT", "LAT"),
        "LONG" to FallbackTranslation("LONG", "LONG", "LONG"),
        "ACCURACY" to FallbackTranslation("PRECISIONE", "GENAUIGKEIT", "PRECISIÓN"),
        "GeoTower" to FallbackTranslation("GeoTower", "GeoTower", "GeoTower"),
        "Live notifications" to FallbackTranslation("Notifiche live", "Live-Benachrichtigungen", "Notificaciones en directo"),
        "Enable a discreet live tracker to keep the nearest antenna for your operator visible in a notification." to FallbackTranslation("Attiva un tracciamento discreto per mantenere visibile in notifica l'antenna più vicina del tuo operatore.", "Aktiviere einen dezenten Live-Tracker, damit die nächste Antenne deines Betreibers in einer Benachrichtigung sichtbar bleibt.", "Activa un seguimiento discreto para mantener visible en una notificación la antena más cercana de tu operador."),
        "Linked to your operator" to FallbackTranslation("Collegato al tuo operatore", "Mit deinem Betreiber verknüpft", "Vinculado a tu operador"),
        "Live tracking uses the default operator you just chose to show relevant information." to FallbackTranslation("Il tracciamento live usa l'operatore predefinito appena scelto per mostrare informazioni pertinenti.", "Live-Tracking nutzt den gerade gewählten Standardbetreiber, um relevante Informationen zu zeigen.", "El seguimiento en directo usa el operador predeterminado que acabas de elegir para mostrar información relevante."),
        "Nearest antenna live" to FallbackTranslation("Antenna vicina live", "Nächste Antenne live", "Antena cercana en directo"),
        "The notification can update while you move to show the nearest site." to FallbackTranslation("La notifica può aggiornarsi mentre ti sposti per mostrare il sito più vicino.", "Die Benachrichtigung kann sich während deiner Bewegung aktualisieren und den nächsten Standort zeigen.", "La notificación puede actualizarse mientras te desplazas para mostrar el sitio más cercano."),
        "Always optional" to FallbackTranslation("Sempre opzionale", "Immer optional", "Siempre opcional"),
        "You can turn live notifications off at any time from GeoTower settings." to FallbackTranslation("Puoi disattivare le notifiche live in qualsiasi momento dalle impostazioni di GeoTower.", "Du kannst Live-Benachrichtigungen jederzeit in den GeoTower-Einstellungen ausschalten.", "Puedes desactivar las notificaciones en directo en cualquier momento desde los ajustes de GeoTower."),
        "The " to FallbackTranslation("Le ", "Die ", "Las "),
        "live notifications" to FallbackTranslation("notifiche live", "Live-Benachrichtigungen", "notificaciones en directo"),
        " you enabled " to FallbackTranslation(" che hai attivato ", " die du aktiviert hast ", " que has activado "),
        "will be turned off" to FallbackTranslation("saranno disattivate", "werden deaktiviert", "se desactivarán"),
        " because they require a default operator." to FallbackTranslation(" perché richiedono un operatore predefinito.", ", da sie einen Standardbetreiber benötigen.", " porque requieren un operador predeterminado."),
        "National Frequency Agency (ANFR).\nData from Cartoradio (Open Data)." to FallbackTranslation("Agenzia nazionale delle frequenze (ANFR).\nDati da Cartoradio (Open Data).", "Nationale Frequenzagentur (ANFR).\nDaten von Cartoradio (Open Data).", "Agencia Nacional de Frecuencias (ANFR).\nDatos de Cartoradio (Open Data)."),
        "© IGN - National Institute of Geographic and Forest Information." to FallbackTranslation("© IGN - Istituto nazionale dell'informazione geografica e forestale.", "© IGN - Nationales Institut für geografische und forstliche Informationen.", "© IGN - Instituto Nacional de Información Geográfica y Forestal."),
        "© OpenStreetMap contributors." to FallbackTranslation("© contributori OpenStreetMap.", "© OpenStreetMap-Mitwirkende.", "© colaboradores de OpenStreetMap."),
        "MapsForges" to FallbackTranslation("MapsForges", "MapsForges", "MapsForges"),
        "Offline vector maps and render theme (Elevate)." to FallbackTranslation("Mappe vettoriali offline e tema di rendering (Elevate).", "Offline-Vektorkarten und Rendering-Theme (Elevate).", "Mapas vectoriales sin conexión y tema de renderizado (Elevate)."),
        "Sites" to FallbackTranslation("Siti", "Standorte", "Sitios"),
        "Latest changes" to FallbackTranslation("Ultime modifiche", "Letzte Änderungen", "Últimos cambios"),
        "Geoportal (IGN)" to FallbackTranslation("Geoportale (IGN)", "Geoportal (IGN)", "Geoportal (IGN)"),

        // Données ANFR : natures, types d'antennes et propriétaires.
        "Tunnel" to FallbackTranslation("Tunnel", "Tunnel", "Túnel"),
        "Silo" to FallbackTranslation("Silo", "Silo", "Silo"),
        "Ran-Sharing antenna" to FallbackTranslation("Antenna Ran-Sharing", "Ran-Sharing-Antenne", "Antena Ran-Sharing"),
        "Tube" to FallbackTranslation("Tubo", "Rohr", "Tubo"),
        "Tunable" to FallbackTranslation("Accordabile", "Abstimmbar", "Sintonizable"),
        "Active (directional or omni)" to FallbackTranslation("Attiva (direzionale o omnidirezionale)", "Aktiv (gerichtet oder omnidirektional)", "Activa (direccional u omnidireccional)"),
        "Cigar antenna" to FallbackTranslation("Antenna a sigaro", "Zigarrenantenne", "Antena tipo cigarro"),
        "Corolla antenna" to FallbackTranslation("Antenna a corolla", "Korollenantenne", "Antena corola"),
        "Wideband dipole" to FallbackTranslation("Dipolo a larga banda", "Breitbanddipol", "Dipolo de banda ancha"),
        "Adjustable dipole" to FallbackTranslation("Dipolo regolabile", "Verstellbarer Dipol", "Dipolo ajustable"),
        "Directional antenna" to FallbackTranslation("Antenna direttiva", "Richtantenne", "Antena direccional"),
        "Wire antenna" to FallbackTranslation("Antenna filare", "Drahtantenne", "Antena de hilo"),
        "Whip antenna" to FallbackTranslation("Antenna a frusta", "Stabantenne", "Antena látigo"),
        "Spindle antenna" to FallbackTranslation("Antenna a fuso", "Spindelantenne", "Antena fusiforme"),
        "Linear array (25 antennas)" to FallbackTranslation("Array lineare (25 antenne)", "Lineares Array (25 Antennen)", "Matriz lineal (25 antenas)"),
        "Ground plane" to FallbackTranslation("Piano di massa", "Groundplane", "Plano de tierra"),
        "HLO" to FallbackTranslation("HLO", "HLO", "HLO"),
        "Log-periodic antenna" to FallbackTranslation("Antenna log-periodica", "Logperiodische Antenne", "Antena log-periódica"),
        "Diamond antenna" to FallbackTranslation("Antenna a losanga", "Rautenantenne", "Antena romboidal"),
        "Panel" to FallbackTranslation("Pannello", "Panel", "Panel"),
        "Dish antenna" to FallbackTranslation("Antenna parabolica", "Parabolantenne", "Antena parabólica"),
        "Whip / Pole antenna" to FallbackTranslation("Antenna a frusta / palo", "Stab-/Mastantenne", "Antena látigo / poste"),
        "Antenna array" to FallbackTranslation("Array di antenne", "Antennenarray", "Matriz de antenas"),
        "Antenna system" to FallbackTranslation("Sistema antenne", "Antennensystem", "Sistema de antenas"),
        "Yagi" to FallbackTranslation("Yagi", "Yagi", "Yagi"),
        "Linear array (13 antennas)" to FallbackTranslation("Array lineare (13 antenne)", "Lineares Array (13 Antennen)", "Matriz lineal (13 antenas)"),
        "Slot antenna" to FallbackTranslation("Antenna a fessure", "Schlitzantenne", "Antena de ranuras"),
        "Circular array (49 antennas)" to FallbackTranslation("Array circolare (49 antenne)", "Kreisförmiges Array (49 Antennen)", "Matriz circular (49 antenas)"),
        "Vertical array" to FallbackTranslation("Array verticale", "Vertikales Array", "Matriz vertical"),
        "Vertical array (2 antennas, type P)" to FallbackTranslation("Array verticale (2 antenne, tipo P)", "Vertikales Array (2 Antennen, Typ P)", "Matriz vertical (2 antenas, tipo P)"),
        "Vertical array (3 antennas, type M)" to FallbackTranslation("Array verticale (3 antenne, tipo M)", "Vertikales Array (3 Antennen, Typ M)", "Matriz vertical (3 antenas, tipo M)"),
        "Daisy antenna" to FallbackTranslation("Antenna margherita", "Gänseblümchen-Antenne", "Antena margarita"),
        "Umbrella antenna" to FallbackTranslation("Antenna ombrello", "Schirmantenne", "Antena paraguas"),
        "Goniometric antenna" to FallbackTranslation("Antenna goniometrica", "Goniometrische Antenne", "Antena goniométrica"),
        "Dipole" to FallbackTranslation("Dipolo", "Dipol", "Dipolo"),
        "Folded dipole" to FallbackTranslation("Dipolo ripiegato", "Faltdipol", "Dipolo plegado"),
        "Collinear antenna" to FallbackTranslation("Antenna collineare", "Kollineare Antenne", "Antena colineal"),
        "Flat antenna LVA" to FallbackTranslation("Antenna piana LVA", "Flachantenne LVA", "Antena plana LVA"),
        "VHF dipole" to FallbackTranslation("Dipolo VHF", "VHF-Dipol", "Dipolo VHF"),
        "HF antenna" to FallbackTranslation("Antenna HF", "HF-Antenne", "Antena HF"),
        "Flat antenna" to FallbackTranslation("Antenna piana", "Flachantenne", "Antena plana"),
        "DAB mast" to FallbackTranslation("Palo DAB", "DAB-Mast", "Mástil DAB"),
        "Recovered electronic data antenna" to FallbackTranslation("Antenna da recupero dati elettronici", "Antenne aus übernommenen elektronischen Daten", "Antena procedente de recuperación de datos electrónicos"),
        "DAB panel" to FallbackTranslation("Pannello DAB", "DAB-Panel", "Panel DAB"),
        "DAB antenna" to FallbackTranslation("Antenna DAB", "DAB-Antenne", "Antena DAB"),
        "Passive plane or reflector" to FallbackTranslation("Piano passivo o riflettore", "Passive Ebene oder Reflektor", "Plano pasivo o reflector"),
        "Grid antenna" to FallbackTranslation("Antenna a griglia", "Gitterantenne", "Antena de rejilla"),
        "Horn antenna" to FallbackTranslation("Antenna a tromba", "Hornantenne", "Antena de bocina"),
        "Dual-band panel" to FallbackTranslation("Pannello bibanda", "Dualband-Panel", "Panel bibanda"),
        "Tri-band panel" to FallbackTranslation("Pannello tribanda", "Triband-Panel", "Panel tribanda"),
        "Cylindrical antenna" to FallbackTranslation("Antenna cilindrica", "Zylindrische Antenne", "Antena cilíndrica"),
        "Dihedral antenna" to FallbackTranslation("Antenna diedrica", "Diederantenne", "Antena diédrica"),
        "Ceiling globe antenna" to FallbackTranslation("Antenna a globo da soffitto", "Decken-Kugelantenne", "Antena globo de techo"),
        "Discone" to FallbackTranslation("Discone", "Discone", "Discone"),
        "Tile antenna" to FallbackTranslation("Antenna a lastra", "Kachelantenne", "Antena de losa"),
        "Radar antenna" to FallbackTranslation("Antenna radar", "Radarantenne", "Antena radar"),
        "Shell antenna" to FallbackTranslation("Antenna a obice", "Granatenantenne", "Antena ojival"),
        "Helical antenna" to FallbackTranslation("Antenna elicoidale", "Helixantenne", "Antena helicoidal"),
        "Defense aerial" to FallbackTranslation("Aereo Difesa", "Verteidigungsantenne", "Aéreo Defensa"),
        "Tri-sector antenna" to FallbackTranslation("Antenna trisettoriale", "Dreisektorantenne", "Antena trisectorial"),
        "Indoor antenna" to FallbackTranslation("Antenna indoor", "Innenantenne", "Antena interior"),
        "Leaky feeder (coaxial antenna)" to FallbackTranslation("Cavo radiante (antenna coassiale)", "Leckkabel (Koaxialantenne)", "Cable radiante (antena coaxial)"),
        "Equidirectional antenna in one plane" to FallbackTranslation("Antenna equidirezionale in un piano", "Gleichgerichtete Antenne in einer Ebene", "Antena equidireccional en un plano"),
        "Longitudinal radiation antenna" to FallbackTranslation("Antenna a radiazione longitudinale", "Antenne mit longitudinaler Abstrahlung", "Antena de radiación longitudinal"),
        "Zenithal radiation antenna" to FallbackTranslation("Antenna a radiazione zenitale", "Antenne mit zenitaler Abstrahlung", "Antena de radiación cenital"),
        "Multi-dipole array" to FallbackTranslation("Array multi-dipolo", "Multi-Dipol-Array", "Matriz multidipolo"),
        "Beam antenna" to FallbackTranslation("Antenna a fascio", "Strahlantenne", "Antena de haz"),
        "Skirt antenna" to FallbackTranslation("Antenna a gonna", "Schürzenantenne", "Antena de falda"),
        "Biconical antenna" to FallbackTranslation("Antenna biconica", "Bikonische Antenne", "Antena bicónica"),
        "REC-465" to FallbackTranslation("REC-465", "REC-465", "REC-465"),
        "REC-580" to FallbackTranslation("REC-580", "REC-580", "REC-580"),
        "AP27" to FallbackTranslation("AP27", "AP27", "AP27"),
        "29-25LOG(FI)" to FallbackTranslation("29-25LOG(FI)", "29-25LOG(FI)", "29-25LOG(FI)"),
        "Radiating tower" to FallbackTranslation("Torre radiante", "Strahlender Mast", "Torre radiante"),
        "Dual-mode panel" to FallbackTranslation("Pannello bimodale", "Dual-Mode-Panel", "Panel bimodo"),
        "Shortwave broadcast antenna (ALLOUIS ISSOUDUN)" to FallbackTranslation("Antenna di diffusione a onde corte (ALLOUIS ISSOUDUN)", "Kurzwellen-Rundfunkantenne (ALLOUIS ISSOUDUN)", "Antena de difusión de onda corta (ALLOUIS ISSOUDUN)"),
        "All-in-one (steerable panel-beam)" to FallbackTranslation("Tutto in uno (pannello-fascio orientabile)", "All-in-one (steuerbares Panel-Bündel)", "Todo en uno (panel-haz orientable)"),
        "Steerable beam antenna" to FallbackTranslation("Antenna a fasci orientabili", "Antenne mit steuerbaren Strahlen", "Antena de haces orientables"),
        "Frame antenna" to FallbackTranslation("Antenna a telaio", "Rahmenantenne", "Antena de marco"),
        "BYT antenna" to FallbackTranslation("Antenna BYT", "BYT-Antenne", "Antena BYT"),
        "SFR antenna" to FallbackTranslation("Antenna SFR", "SFR-Antenne", "Antena SFR"),
        "Private individual" to FallbackTranslation("Privato", "Privatperson", "Particular"),
        "Condominium, Trustee, SCI" to FallbackTranslation("Condominio, amministratore, SCI", "Eigentümergemeinschaft, Verwalter, SCI", "Comunidad, administrador, SCI"),
        "Municipality" to FallbackTranslation("Comune", "Gemeinde", "Municipio"),
        "Departmental Council" to FallbackTranslation("Consiglio dipartimentale", "Départementrat", "Consejo departamental"),
        "Regional Council" to FallbackTranslation("Consiglio regionale", "Regionalrat", "Consejo regional"),
        "Private company" to FallbackTranslation("Società privata", "Privatunternehmen", "Empresa privada"),
        "Healthcare facility" to FallbackTranslation("Struttura sanitaria", "Gesundheitseinrichtung", "Centro sanitario"),
        "State / Ministry" to FallbackTranslation("Stato / Ministero", "Staat / Ministerium", "Estado / Ministerio"),
        "Civil Aviation" to FallbackTranslation("Aviazione civile", "Zivilluftfahrt", "Aviación civil"),

        // Statuts, dates, versions et libellés courts.
        "Maintenance" to FallbackTranslation("Manutenzione", "Wartung", "Mantenimiento"),
        "Incident" to FallbackTranslation("Incidente", "Vorfall", "Incidente"),
        "Data" to FallbackTranslation("Dati", "Daten", "Datos"),
        "SMS" to FallbackTranslation("SMS", "SMS", "SMS"),
        "Internet" to FallbackTranslation("Internet", "Internet", "Internet"),
        "January" to FallbackTranslation("Gennaio", "Januar", "Enero"),
        "February" to FallbackTranslation("Febbraio", "Februar", "Febrero"),
        "March" to FallbackTranslation("Marzo", "März", "Marzo"),
        "April" to FallbackTranslation("Aprile", "April", "Abril"),
        "May" to FallbackTranslation("Maggio", "Mai", "Mayo"),
        "June" to FallbackTranslation("Giugno", "Juni", "Junio"),
        "July" to FallbackTranslation("Luglio", "Juli", "Julio"),
        "August" to FallbackTranslation("Agosto", "August", "Agosto"),
        "September" to FallbackTranslation("Settembre", "September", "Septiembre"),
        "October" to FallbackTranslation("Ottobre", "Oktober", "Octubre"),
        "November" to FallbackTranslation("Novembre", "November", "Noviembre"),
        "December" to FallbackTranslation("Dicembre", "Dezember", "Diciembre"),
        "Week" to FallbackTranslation("Settimana", "Woche", "Semana"),
        "Upload" to FallbackTranslation("Upload", "Upload", "Subida"),
        "Ping" to FallbackTranslation("Ping", "Ping", "Ping")
    )

    // Lecture en temps réel de la langue choisie dans les paramètres
    private val language: String
        @Composable get() = AppConfig.appLanguage.value

    // Fonction utilitaire pour les langues de l'application.
    @Composable
    fun get(
        fr: String,
        en: String,
        pt: String,
        it: String = fallbackTranslation(en, LANGUAGE_ITALIAN),
        de: String = fallbackTranslation(en, LANGUAGE_GERMAN),
        es: String = fallbackTranslation(en, LANGUAGE_SPANISH)
    ): String {
        val currentLang = AppConfig.appLanguage.value

        // Si "Système" est sélectionné, on récupère le code langue du téléphone
        val langToCheck = if (currentLang == LANGUAGE_SYSTEM) {
            currentSystemLanguage()
        } else {
            currentLang
        }

        return resolveForLanguage(langToCheck, fr, en, pt, it, de, es)
    }

    fun resolveForLanguage(
        langToCheck: String,
        fr: String,
        en: String,
        pt: String,
        it: String = fallbackTranslation(en, LANGUAGE_ITALIAN),
        de: String = fallbackTranslation(en, LANGUAGE_GERMAN),
        es: String = fallbackTranslation(en, LANGUAGE_SPANISH)
    ): String = when {
        matchesLanguage(langToCheck, LANGUAGE_FRENCH, "fr") -> fr
        matchesLanguage(langToCheck, LANGUAGE_PORTUGUESE, "pt") -> pt
        matchesLanguage(langToCheck, LANGUAGE_ITALIAN, "it") -> it
        matchesLanguage(langToCheck, LANGUAGE_GERMAN, "de") -> de
        matchesLanguage(langToCheck, LANGUAGE_SPANISH, "es") -> es
        else -> en
    }

    private fun matchesLanguage(value: String, displayName: String, isoCode: String): Boolean {
        return value.equals(displayName, ignoreCase = true) || value.equals(isoCode, ignoreCase = true)
    }

    private fun fallbackTranslation(english: String, targetLanguage: String): String {
        fallbackDynamicTranslation(english, targetLanguage)?.let { return it }

        val translated = fallbackTranslations[english] ?: return english
        return when (targetLanguage) {
            LANGUAGE_ITALIAN -> translated.italian
            LANGUAGE_GERMAN -> translated.german
            LANGUAGE_SPANISH -> translated.spanish
            else -> english
        }
    }

    private fun fallbackDynamicTranslation(english: String, targetLanguage: String): String? {
        fun translated(italian: String, german: String, spanish: String): String = when (targetLanguage) {
            LANGUAGE_ITALIAN -> italian
            LANGUAGE_GERMAN -> german
            LANGUAGE_SPANISH -> spanish
            else -> english
        }

        Regex("""^GeoTower (.+) is available\. Tap to open the download\.$""").matchEntire(english)?.let {
            val versionName = it.groupValues[1]
            return translated(
                "GeoTower $versionName è disponibile. Tocca per aprire il download.",
                "GeoTower $versionName ist verfügbar. Tippe, um den Download zu öffnen.",
                "GeoTower $versionName está disponible. Toca para abrir la descarga."
            )
        }
        Regex("""^Downloading\.\.\. (\d+)%$""").matchEntire(english)?.let {
            val progress = it.groupValues[1]
            return translated("Download... $progress%", "Download läuft... $progress%", "Descargando... $progress%")
        }
        Regex("""^Downloading : (\d+) %$""").matchEntire(english)?.let {
            val progress = it.groupValues[1]
            return translated("Download: $progress %", "Download: $progress %", "Descarga: $progress %")
        }
        Regex("""^Updated at (.+)$""").matchEntire(english)?.let {
            val lastUpdate = it.groupValues[1]
            return translated("Aggiornato alle $lastUpdate", "Aktualisiert um $lastUpdate", "Actualizado a las $lastUpdate")
        }
        Regex("""^Map: (.+)$""").matchEntire(english)?.let {
            val mapName = it.groupValues[1]
            return translated("Mappa: $mapName", "Karte: $mapName", "Mapa: $mapName")
        }
        Regex("""^Map (.+) is ready offline\. Tap to open\.$""").matchEntire(english)?.let {
            val mapName = it.groupValues[1]
            return translated("La mappa $mapName è pronta offline. Tocca per aprire.", "Karte $mapName ist offline bereit. Zum Öffnen tippen.", "El mapa $mapName está listo sin conexión. Toca para abrir.")
        }
        Regex("""^Site (.+) is not in the displayed area\. Move the map to its city first\.$""").matchEntire(english)?.let {
            val siteId = it.groupValues[1]
            return translated(
                "Il sito $siteId non è nell'area visualizzata. Sposta prima la mappa verso la sua città.",
                "Standort $siteId liegt nicht im angezeigten Bereich. Verschiebe die Karte zuerst zu seiner Stadt.",
                "El sitio $siteId no está en la zona mostrada. Mueve primero el mapa hacia su ciudad."
            )
        }
        Regex("""^Uploading \((\d+)/(\d+)\)\.\.\.$""").matchEntire(english)?.let {
            val current = it.groupValues[1]
            val total = it.groupValues[2]
            return translated("Invio in corso ($current/$total)...", "Upload läuft ($current/$total)...", "Enviando ($current/$total)...")
        }
        Regex("""^(\d+)/(\d+) photos sent successfully to Signal Quest!$""").matchEntire(english)?.let {
            val success = it.groupValues[1]
            val total = it.groupValues[2]
            return translated("$success/$total foto inviate correttamente a Signal Quest!", "$success/$total Fotos erfolgreich an Signal Quest gesendet!", "$success/$total fotos enviadas correctamente a Signal Quest!")
        }
        Regex("""^(\d+) out of (\d+) photo\(s\) successfully sent to Signal Quest\.$""").matchEntire(english)?.let {
            val success = it.groupValues[1]
            val total = it.groupValues[2]
            return translated("$success su $total foto inviate correttamente a Signal Quest.", "$success von $total Foto(s) erfolgreich an Signal Quest gesendet.", "$success de $total foto(s) enviadas correctamente a Signal Quest.")
        }
        Regex("""^(\d+)/(\d+) photos sent to Signal Quest\. Some failed\.$""").matchEntire(english)?.let {
            val success = it.groupValues[1]
            val total = it.groupValues[2]
            return translated("$success/$total foto inviate a Signal Quest. Alcune non sono riuscite.", "$success/$total Fotos an Signal Quest gesendet. Einige sind fehlgeschlagen.", "$success/$total fotos enviadas a Signal Quest. Algunas han fallado.")
        }
        Regex("""^🏆 Total: (\d+) photos shared since you started!$""").matchEntire(english)?.let {
            val score = it.groupValues[1]
            return translated("🏆 Totale: $score foto condivise dall'inizio!", "🏆 Gesamt: $score Fotos seit Beginn geteilt!", "🏆 Total: $score fotos compartidas desde tus inicios!")
        }
        Regex("""^Current : (.+)$""").matchEntire(english)?.let {
            val value = it.groupValues[1]
            return translated("Attuale: $value", "Aktuell: $value", "Actual: $value")
        }
        Regex("""^Optimal distance: (.+)$""").matchEntire(english)?.let {
            val distance = it.groupValues[1]
            return translated("Distanza ottimale: $distance", "Optimale Entfernung: $distance", "Distancia óptima: $distance")
        }
        Regex("""^Zone: (.+) to (.+)$""").matchEntire(english)?.let {
            val near = it.groupValues[1]
            val far = it.groupValues[2]
            return translated("Zona: da $near a $far", "Zone: $near bis $far", "Zona: de $near a $far")
        }
        Regex("""^Selected position: (.+)$""").matchEntire(english)?.let {
            val label = it.groupValues[1]
            return translated("Posizione scelta: $label", "Ausgewählte Position: $label", "Posición elegida: $label")
        }
        Regex("""^Distance to site: (.+)$""").matchEntire(english)?.let {
            val distance = it.groupValues[1]
            return translated("Distanza dal sito: $distance", "Entfernung zum Standort: $distance", "Distancia al sitio: $distance")
        }
        Regex("""^↓ Number of operators : (\d+)$""").matchEntire(english)?.let {
            val count = it.groupValues[1]
            return translated("↓ Numero di operatori: $count", "↓ Anzahl Betreiber: $count", "↓ Número de operadores: $count")
        }
        Regex("""^By (.+)$""").matchEntire(english)?.let {
            val author = it.groupValues[1]
            return translated("Da $author", "Von $author", "Por $author")
        }
        Regex("""^on (.+)$""").matchEntire(english)?.let {
            val date = it.groupValues[1]
            return translated("il $date", "am $date", "el $date")
        }
        Regex("""^No (.+) antenna found nearby\.$""").matchEntire(english)?.let {
            val operator = it.groupValues[1]
            return translated("Nessuna antenna $operator trovata nelle vicinanze.", "Keine $operator-Antenne in der Nähe gefunden.", "No se encontró ninguna antena $operator cerca.")
        }
        Regex("""^(.+) antenna : (.+)$""").matchEntire(english)?.let {
            val operator = it.groupValues[1]
            val distance = it.groupValues[2]
            return translated("Antenna $operator: $distance", "$operator-Antenne: $distance", "Antena $operator: $distance")
        }
        Regex("""^(\d+) sections$""").matchEntire(english)?.let {
            val count = it.groupValues[1]
            return translated("$count sezioni", "$count Abschnitte", "$count secciones")
        }
        Regex("""^What's new \((.+)\)$""").matchEntire(english)?.let {
            val version = it.groupValues[1]
            return translated("Novità ($version)", "Neuigkeiten ($version)", "Novedades ($version)")
        }
        Regex("""^(\d+) sites found$""").matchEntire(english)?.let {
            val count = it.groupValues[1]
            return translated("$count siti trovati", "$count Standorte gefunden", "$count sitios encontrados")
        }
        Regex("""^(\d+) sites$""").matchEntire(english)?.let {
            val count = it.groupValues[1]
            return translated("$count siti", "$count Standorte", "$count sitios")
        }
        Regex("""^(.+) tracking active\.\.\.$""").matchEntire(english)?.let {
            val operator = it.groupValues[1]
            return translated("Tracciamento $operator in corso...", "$operator-Tracking aktiv...", "Seguimiento de $operator activo...")
        }
        Regex("""^(\d+) / (\d+) photo\(s\) sent\.\.\.$""").matchEntire(english)?.let {
            val current = it.groupValues[1]
            val total = it.groupValues[2]
            return translated("$current / $total foto inviate...", "$current / $total Foto(s) gesendet...", "$current / $total foto(s) enviadas...")
        }
        Regex("""^File size: (.+) MB\nWi-Fi download is recommended\.$""").matchEntire(english)?.let {
            val size = it.groupValues[1]
            return translated(
                "Dimensione file: $size MB\nDownload tramite Wi-Fi consigliato.",
                "Dateigröße: $size MB\nDownload über WLAN empfohlen.",
                "Tamaño del archivo: $size MB\nSe recomienda descargar por Wi-Fi."
            )
        }
        Regex("""^(\d+) band\(s\) included out of (\d+)$""").matchEntire(english)?.let {
            val included = it.groupValues[1]
            val total = it.groupValues[2]
            return translated("$included banda/e incluse su $total", "$included von $total Band/Bändern einbezogen", "$included banda(s) incluidas de $total")
        }
        Regex("""^Estimated main zone: (.+) to (.+)$""").matchEntire(english)?.let {
            val near = it.groupValues[1]
            val far = it.groupValues[2]
            return translated("Zona principale stimata: da $near a $far", "Geschätzte Hauptzone: $near bis $far", "Zona principal estimada: de $near a $far")
        }
        Regex("""^Estimated cone: (.+) \((.+)-(.+)\)$""").matchEntire(english)?.let {
            val center = it.groupValues[1]
            val near = it.groupValues[2]
            val far = it.groupValues[3]
            return translated("Cono stimato: $center ($near-$far)", "Geschätzter Kegel: $center ($near-$far)", "Cono estimado: $center ($near-$far)")
        }
        Regex("""^(.+) · (\d+)/(\d+) band\(s\)$""").matchEntire(english)?.let {
            val profile = it.groupValues[1]
            val included = it.groupValues[2]
            val total = it.groupValues[3]
            return translated("$profile · $included/$total banda/e", "$profile · $included/$total Band/Bänder", "$profile · $included/$total banda(s)")
        }
        Regex("""^Calculation assumptions: (.+)$""").matchEntire(english)?.let {
            val assumptions = it.groupValues[1]
            return translated("Ipotesi di calcolo: $assumptions", "Berechnungsannahmen: $assumptions", "Supuestos de cálculo: $assumptions")
        }
        Regex("""^Sources: (.+)$""").matchEntire(english)?.let {
            val summary = it.groupValues[1]
            return translated("Fonti: $summary", "Quellen: $summary", "Fuentes: $summary")
        }
        Regex("""^MIMO and modulation are not published at site level, so the (.+) profile is applied\.$""").matchEntire(english)?.let {
            val profile = it.groupValues[1]
            return translated(
                "MIMO e modulazione non sono pubblicati a livello di sito, quindi viene applicato il profilo $profile.",
                "MIMO und Modulation werden nicht auf Standortebene veröffentlicht, daher wird das Profil $profile angewendet.",
                "MIMO y modulación no se publican a nivel de sitio, así que se aplica el perfil $profile."
            )
        }
        Regex("""^Band (.+) excluded: operator allocation not found\.$""").matchEntire(english)?.let {
            val band = it.groupValues[1]
            return translated("Banda $band esclusa: allocazione operatore non trovata.", "Band $band ausgeschlossen: Betreiberzuteilung nicht gefunden.", "Banda $band excluida: asignación del operador no encontrada.")
        }
        Regex("""^Band (.+) may be shared between 4G and 5G, so its throughput is not fully added\.$""").matchEntire(english)?.let {
            val band = it.groupValues[1]
            return translated(
                "La banda $band può essere condivisa tra 4G e 5G, quindi il suo throughput non viene sommato integralmente.",
                "Band $band kann zwischen 4G und 5G geteilt sein, daher wird sein Durchsatz nicht vollständig addiert.",
                "La banda $band puede compartirse entre 4G y 5G, por eso su velocidad no se suma por completo."
            )
        }
        Regex("""^Inside a panel azimuth \((.+), (\d+)° offset\)\.$""").matchEntire(english)?.let {
            val azimuth = it.groupValues[1]
            val delta = it.groupValues[2]
            return translated("Dentro l'azimut di un pannello ($azimuth, scarto $delta°).", "Innerhalb eines Panel-Azimuts ($azimuth, Abweichung $delta°).", "Dentro del azimut de un panel ($azimuth, desvío $delta°).")
        }
        Regex("""^Outside beam: closest panel (.+), (\d+)° offset\.$""").matchEntire(english)?.let {
            val azimuth = it.groupValues[1]
            val delta = it.groupValues[2]
            return translated("Fuori fascio: pannello più vicino $azimuth, scarto $delta°.", "Außerhalb des Strahls: nächstes Panel $azimuth, Abweichung $delta°.", "Fuera del haz: panel más cercano $azimuth, desvío $delta°.")
        }
        Regex("""^Estimated radio cone: nominal point (.+), useful zone (.+)-(.+) from panel/support height\.$""").matchEntire(english)?.let {
            val center = it.groupValues[1]
            val near = it.groupValues[2]
            val far = it.groupValues[3]
            return translated(
                "Cono radio stimato: punto nominale $center, zona utile $near-$far dalla quota pannello/supporto.",
                "Geschätzter Funkkegel: nominaler Punkt $center, Nutzbereich $near-$far aus Panel-/Trägerhöhe.",
                "Cono radio estimado: punto nominal $center, zona útil $near-$far según altura del panel/soporte."
            )
        }
        Regex("""^Estimated impact: 4G (\d+)% / 5G (\d+)%$""").matchEntire(english)?.let {
            val lte = it.groupValues[1]
            val nr = it.groupValues[2]
            return translated("Impatto stimato: 4G $lte% / 5G $nr%", "Geschätzter Einfluss: 4G $lte% / 5G $nr%", "Impacto estimado: 4G $lte% / 5G $nr%")
        }
        Regex("""^RSRP accounts for 40% of the radio score and SNR/SINR for 60%\. The current coefficient gives 4G (\d+)% / 5G (\d+)%\.$""").matchEntire(english)?.let {
            val lte = it.groupValues[1]
            val nr = it.groupValues[2]
            return translated(
                "RSRP pesa il 40% del punteggio radio e SNR/SINR il 60%. Il coefficiente attuale dà 4G $lte% / 5G $nr%.",
                "RSRP zählt zu 40% für den Funkwert, SNR/SINR zu 60%. Der aktuelle Koeffizient ergibt 4G $lte% / 5G $nr%.",
                "RSRP cuenta el 40% de la puntuación radio y SNR/SINR el 60%. El coeficiente actual da 4G $lte% / 5G $nr%."
            )
        }
        Regex("""^The total keeps the best (\d+) 4G carrier\(s\) for the profile\. Low bands 700/800/900 MHz are not added together\.$""").matchEntire(english)?.let {
            val maxCarriers = it.groupValues[1]
            return translated(
                "Il totale mantiene le migliori $maxCarriers portanti 4G del profilo. Le bande basse 700/800/900 MHz non vengono sommate tra loro.",
                "Die Summe behält die besten $maxCarriers 4G-Träger des Profils. Niedrige Bänder 700/800/900 MHz werden nicht miteinander addiert.",
                "El total conserva las mejores $maxCarriers portadoras 4G del perfil. Las bandas bajas 700/800/900 MHz no se suman entre sí."
            )
        }

        return null
    }

    @Composable
    private fun currentSystemLanguage(): String = ComposeLocale.current.language

    @Composable
    private fun currentJavaLocale(): Locale = Locale.forLanguageTag(ComposeLocale.current.language.ifBlank { "en" })

    val uploadHistoryTitle @Composable get() = get("Historique d'envoi", "Upload history", "Hist\u00f3rico de envios")
    @Composable
    fun uploadHistorySubtitle(count: Int) = get(
        if (count == 0) "Aucune photo enregistr\u00e9e" else "$count photo${if (count > 1) "s" else ""} enregistr\u00e9e${if (count > 1) "s" else ""}",
        if (count == 0) "No photo recorded" else "$count photo${if (count > 1) "s" else ""} recorded",
        if (count == 0) "Nenhuma foto registada" else "$count foto${if (count > 1) "s" else ""} registada${if (count > 1) "s" else ""}"
    )
    @Composable
    fun uploadHistorySelectedCount(count: Int) = get(
        "$count photo${if (count > 1) "s" else ""} s\u00e9lectionn\u00e9e${if (count > 1) "s" else ""}",
        "$count photo${if (count > 1) "s" else ""} selected",
        "$count foto${if (count > 1) "s" else ""} selecionada${if (count > 1) "s" else ""}"
    )
    val uploadHistoryEmpty @Composable get() = get(
        "Aucun envoi de photo enregistr\u00e9 sur cet appareil.",
        "No photo upload recorded on this device.",
        "Nenhum envio de foto registado neste dispositivo."
    )
    val uploadHistoryClear @Composable get() = get("Effacer l'historique", "Clear history", "Limpar hist\u00f3rico")
    val uploadHistoryClearTitle @Composable get() = get("Effacer l'historique d'envoi ?", "Clear upload history?", "Limpar hist\u00f3rico de envios?")
    val uploadHistoryClearDesc @Composable get() = get(
        "Les miniatures locales et les lignes d'historique seront supprim\u00e9es. Les photos d\u00e9j\u00e0 envoy\u00e9es sur les apps externes ne sont pas modifi\u00e9es.",
        "Local thumbnails and history rows will be deleted. Photos already sent to external apps are not changed.",
        "As miniaturas locais e linhas de hist\u00f3rico ser\u00e3o eliminadas. As fotos j\u00e1 enviadas para apps externas n\u00e3o ser\u00e3o alteradas."
    )
    @Composable
    fun uploadHistoryExif(stripped: Boolean) = get(
        if (stripped) "EXIF supprim\u00e9s" else "EXIF conserv\u00e9s",
        if (stripped) "EXIF removed" else "EXIF kept",
        if (stripped) "EXIF removidos" else "EXIF preservados"
    )
    @Composable
    fun uploadHistoryStatus(status: String) = get(
        when (status) {
            "success" -> "Valid\u00e9e"
            "awaiting_validation" -> "En validation"
            "failed" -> "\u00c9chec"
            "retry" -> "En attente"
            else -> "En cours"
        },
        when (status) {
            "success" -> "Approved"
            "awaiting_validation" -> "Awaiting approval"
            "failed" -> "Failed"
            "retry" -> "Waiting"
            else -> "In progress"
        },
        when (status) {
            "success" -> "Validada"
            "awaiting_validation" -> "A aguardar valida\u00e7\u00e3o"
            "failed" -> "Falhou"
            "retry" -> "Em espera"
            else -> "Em curso"
        }
    )

    // ==========================================
    // 🚀 SPLASH SCREEN
    // ==========================================
    val loadingApp @Composable get() = get("Chargement...", "Loading...", "A carregar...")

    // ==========================================
    // 🏠 ÉCRAN D'ACCUEIL
    // ==========================================
    val nearAntennas @Composable get() = get("Antennes à proximité", "Nearby Antennas", "Antenas próximas")
    val mapTitle @Composable get() = get("Carte des Antennes", "Antenna Map", "Mapa de Antenas")
    val compassTitle @Composable get() = get("Boussole", "Compass", "Bússola")
    val statsTitle @Composable get() = get("Statistiques", "Statistics", "Estatísticas")
    val settingsTitle @Composable get() = get("Paramètres", "Settings", "Configurações")
    val about @Composable get() = get("À propos", "About", "Sobre")
    val version @Composable get() = get("Version", "Version", "Versão")
    val helpTitle @Composable get() = get("Aides", "Help", "Ajuda")
    val helpCenterTitle @Composable get() = get("Centre d'aide GeoTower", "GeoTower help center", "Centro de ajuda GeoTower")
    val helpCenterIntro @Composable get() = get(
        "Retrouve ici le sommaire, les explications écran par écran, les codes de recherche et le rôle des boutons.",
        "Find the table of contents, screen-by-screen explanations, search codes and button meanings here.",
        "Encontra aqui o índice, explicações por ecrã, códigos de pesquisa e significado dos botões."
    )
    val helpSearchPlaceholder @Composable get() = get(
        "Rechercher une fonction...",
        "Search a feature...",
        "Pesquisar uma função..."
    )
    val helpContents @Composable get() = get("Sommaire", "Table of contents", "Índice")
    val helpResults @Composable get() = get("Résultats", "Results", "Resultados")
    val helpNoResults @Composable get() = get(
        "Aucune aide ne correspond à cette recherche.",
        "No help topic matches this search.",
        "Nenhuma ajuda corresponde a esta pesquisa."
    )
    val helpClearSearch @Composable get() = get("Effacer", "Clear", "Limpar")
    val helpLocalContents @Composable get() = get("Sommaire de cette aide", "This help section", "Índice desta ajuda")
    val helpBackToContents @Composable get() = get("Retour au sommaire", "Back to contents", "Voltar ao índice")
    @Composable fun helpSectionCount(count: Int): String = get("$count rubriques", "$count sections", "$count secções")

    @Composable
    fun helpVisualTitle(id: String): String = when (id) {
        "home" -> get("Schéma de l'accueil", "Home diagram", "Esquema do início")
        "nearby" -> get("Schéma de la recherche", "Search diagram", "Esquema da pesquisa")
        "map" -> get("Schéma de la carte", "Map diagram", "Esquema do mapa")
        "support" -> get("Schéma du détail support", "Support detail diagram", "Esquema do detalhe do suporte")
        "site" -> get("Schéma du détail site", "Site detail diagram", "Esquema do detalhe do site")
        "elevation" -> get("Schéma du profil altimétrique", "Elevation profile diagram", "Esquema do perfil altimétrico")
        "throughput" -> get("Schéma du calculateur", "Calculator diagram", "Esquema da calculadora")
        "photos" -> get("Schéma des photos", "Photos diagram", "Esquema das fotos")
        "share" -> get("Schéma du partage", "Sharing diagram", "Esquema da partilha")
        "settings" -> get("Schéma des paramètres", "Settings diagram", "Esquema das configurações")
        "data" -> get("Schéma des données", "Data diagram", "Esquema dos dados")
        "about" -> get("Schéma de l'écran À propos", "About screen diagram", "Esquema do ecrã Sobre")
        else -> id
    }

    @Composable
    fun helpVisualLabel(id: String, index: Int): String = when (id) {
        "home" -> when (index) {
            1 -> get("Bandeau de statut de la base et du réseau.", "Database and network status banner.", "Aviso de estado da base e da rede.")
            2 -> get("Raccourcis vers les écrans principaux.", "Shortcuts to the main screens.", "Atalhos para os ecrãs principais.")
            else -> get("Bouton Aides, position personnalisable.", "Customizable Help button.", "Botão Ajuda com posição personalizável.")
        }
        "nearby" -> when (index) {
            1 -> get("Champ de recherche libre ou par code.", "Free text or code search field.", "Campo de pesquisa livre ou por código.")
            2 -> get("Suggestions rapides et aide des codes.", "Quick suggestions and code help.", "Sugestões rápidas e ajuda dos códigos.")
            else -> get("Résultats ouvrables en détail ou en split screen.", "Results that open details or split screen.", "Resultados que abrem detalhe ou ecrã dividido.")
        }
        "map" -> when (index) {
            1 -> get("Recherche de ville, adresse ou zone.", "City, address or area search.", "Pesquisa por cidade, endereço ou zona.")
            2 -> get("Marqueurs des supports et sites.", "Support and site markers.", "Marcadores dos suportes e sites.")
            else -> get("Boutons GPS, zoom et orientation.", "GPS, zoom and orientation buttons.", "Botões GPS, zoom e orientação.")
        }
        "support" -> when (index) {
            1 -> get("Résumé du support physique.", "Physical support summary.", "Resumo do suporte físico.")
            2 -> get("Sites et opérateurs rattachés.", "Linked sites and operators.", "Sites e operadoras associados.")
            else -> get("Actions carte, navigation, partage et photos.", "Map, navigation, sharing and photo actions.", "Ações de mapa, navegação, partilha e fotos.")
        }
        "site" -> when (index) {
            1 -> get("Bandeau opérateur et statut.", "Operator banner and status.", "Faixa da operadora e estado.")
            2 -> get("Fréquences, technologies et azimuts.", "Frequencies, technologies and azimuths.", "Frequências, tecnologias e azimutes.")
            else -> get("Outils : profil, débit, partage et réglages.", "Tools: profile, throughput, sharing and settings.", "Ferramentas: perfil, débito, partilha e definições.")
        }
        "elevation" -> when (index) {
            1 -> get("Trajet entre ta position et le site.", "Route between your position and the site.", "Trajeto entre a tua posição e o site.")
            2 -> get("Relief et visibilité radio estimée.", "Terrain and estimated radio visibility.", "Relevo e visibilidade rádio estimada.")
            else -> get("Recalcul immédiat ou différé.", "Immediate or deferred recalculation.", "Recálculo imediato ou adiado.")
        }
        "throughput" -> when (index) {
            1 -> get("Mode prudent, idéal ou personnalisé.", "Conservative, ideal or custom mode.", "Modo prudente, ideal ou personalizado.")
            2 -> get("Bandes, technologies et modulation.", "Bands, technologies and modulation.", "Bandas, tecnologias e modulação.")
            else -> get("Distance optimale et mini-carte.", "Optimal distance and mini-map.", "Distância ideal e mini-mapa.")
        }
        "photos" -> when (index) {
            1 -> get("Carrousel des photos disponibles.", "Available photo carousel.", "Carrossel das fotos disponíveis.")
            2 -> get("Ouverture plein écran.", "Full screen opening.", "Abertura em ecrã inteiro.")
            else -> get("Upload communautaire SignalQuest.", "SignalQuest community upload.", "Envio comunitário SignalQuest.")
        }
        "share" -> when (index) {
            1 -> get("Cases des blocs à inclure.", "Checkboxes for included blocks.", "Caixas dos blocos a incluir.")
            2 -> get("Aperçu de l'image générée.", "Generated image preview.", "Pré-visualização da imagem gerada.")
            else -> get("Export ou partage depuis Android.", "Export or Android sharing.", "Exportação ou partilha Android.")
        }
        "settings" -> when (index) {
            1 -> get("Sections latérales ou mode page.", "Side sections or page mode.", "Secções laterais ou modo por páginas.")
            2 -> get("Préférences rapides de l'application.", "Quick app preferences.", "Preferências rápidas da aplicação.")
            else -> get("Personnalisation des pages et boutons.", "Page and button customization.", "Personalização das páginas e botões.")
        }
        "data" -> when (index) {
            1 -> get("Téléchargement de la base ANFR.", "ANFR database download.", "Transferência da base ANFR.")
            2 -> get("Cartes hors ligne disponibles.", "Available offline maps.", "Mapas offline disponíveis.")
            else -> get("Notifications qui mènent à la bonne section.", "Notifications leading to the right section.", "Notificações que levam à secção correta.")
        }
        "about" -> when (index) {
            1 -> get("Navigation entre sections.", "Section navigation.", "Navegação entre secções.")
            2 -> get("Version de l'application et données.", "App and data version.", "Versão da aplicação e dos dados.")
            else -> get("Sources, développement et liens utiles.", "Sources, development and useful links.", "Fontes, desenvolvimento e ligações úteis.")
        }
        else -> ""
    }

    @Composable
    fun helpTopicTitle(id: String): String = when (id) {
        "start" -> get("Premiers pas", "Getting started", "Primeiros passos")
        "home" -> get("Accueil", "Home", "Início")
        "nearby" -> get("Antennes à proximité", "Nearby antennas", "Antenas próximas")
        "map" -> get("Carte des antennes", "Antenna map", "Mapa de antenas")
        "compass" -> get("Boussole", "Compass", "Bússola")
        "support" -> get("Détail support", "Support details", "Detalhes do suporte")
        "site" -> get("Détail site", "Site details", "Detalhes do site")
        "elevation" -> get("Profil altimétrique", "Elevation profile", "Perfil altimétrico")
        "throughput" -> get("Calculateur de débit", "Throughput calculator", "Calculadora de débito")
        "photos" -> get("Photos communautaires", "Community photos", "Fotos da comunidade")
        "share" -> get("Partage", "Sharing", "Partilha")
        "settings" -> get("Paramètres", "Settings", "Configurações")
        "data" -> get("Base de données et cartes hors ligne", "Database and offline maps", "Base de dados e mapas offline")
        "about" -> get("À propos", "About", "Sobre")
        "glossary" -> get("Glossaire des boutons et dépannage", "Button glossary and troubleshooting", "Glossário de botões e resolução de problemas")
        else -> id
    }

    @Composable
    fun helpTopicSubtitle(id: String): String = when (id) {
        "start" -> get("Le parcours conseillé pour configurer GeoTower et trouver rapidement une antenne.", "The recommended path to set up GeoTower and quickly find an antenna.", "O percurso recomendado para configurar o GeoTower e encontrar rapidamente uma antena.")
        "home" -> get("Explication des boutons principaux, du bandeau de base de données et du bouton Aides.", "Explains the main buttons, database banner and Help button.", "Explica os botões principais, o aviso da base de dados e o botão Ajuda.")
        "nearby" -> get("Recherche par ville, adresse, code postal, support, ANFR, coordonnées, type et technologie.", "Search by city, address, postal code, support, ANFR, coordinates, type and technology.", "Pesquisa por cidade, endereço, código postal, suporte, ANFR, coordenadas, tipo e tecnologia.")
        "map" -> get("Marqueurs, recherche, filtres, position GPS, couches et outils cartographiques.", "Markers, search, filters, GPS position, layers and map tools.", "Marcadores, pesquisa, filtros, posição GPS, camadas e ferramentas de mapa.")
        "compass" -> get("Orientation vers les antennes et compréhension des caps autour de ta position.", "Orientation toward antennas and understanding bearings around your position.", "Orientação para antenas e compreensão dos rumos à volta da tua posição.")
        "support" -> get("Tout ce qui concerne le pylône, le toit, les opérateurs et les sites rattachés.", "Everything about the tower, roof, operators and linked sites.", "Tudo sobre o poste, telhado, operadoras e sites associados.")
        "site" -> get("Fréquences, azimuts, hauteur, statut, photos, liens, partage et outils avancés.", "Frequencies, azimuths, height, status, photos, links, sharing and advanced tools.", "Frequências, azimutes, altura, estado, fotos, ligações, partilha e ferramentas avançadas.")
        "elevation" -> get("Relief, ligne de vue, zone de Fresnel, recalcul et limites réseau.", "Terrain, line of sight, Fresnel zone, recalculation and network limits.", "Relevo, linha de vista, zona de Fresnel, recálculo e limites de rede.")
        "throughput" -> get("Modes prudent/idéal/personnalisé, bandes, modulations, MIMO et distance optimale.", "Conservative/ideal/custom modes, bands, modulations, MIMO and optimal distance.", "Modos prudente/ideal/personalizado, bandas, modulações, MIMO e distância ideal.")
        "photos" -> get("Carrousel, plein écran, upload SignalQuest, descriptions et opérateur cible.", "Carousel, full screen, SignalQuest upload, descriptions and target operator.", "Carrossel, ecrã inteiro, envio SignalQuest, descrições e operadora alvo.")
        "share" -> get("Options d'image, support, fréquences, dates, speedtest, débit et profil altimétrique.", "Image options, support, frequencies, dates, speedtest, throughput and elevation profile.", "Opções de imagem, suporte, frequências, datas, speedtest, débito e perfil altimétrico.")
        "settings" -> get("Apparence, One UI, unités, split screen, personnalisation, base, cartes hors ligne et notifications.", "Appearance, One UI, units, split screen, customization, database, offline maps and notifications.", "Aparência, One UI, unidades, ecrã dividido, personalização, base, mapas offline e notificações.")
        "data" -> get("Téléchargements, notifications de progression, mise à jour et stockage local.", "Downloads, progress notifications, updates and local storage.", "Transferências, notificações de progresso, atualizações e armazenamento local.")
        "about" -> get("Version, sources de données, informations de développement et liens utiles.", "Version, data sources, development information and useful links.", "Versão, fontes de dados, informações de desenvolvimento e ligações úteis.")
        "glossary" -> get("Signification des icônes courantes et solutions aux problèmes fréquents.", "Meaning of common icons and solutions to frequent issues.", "Significado dos ícones comuns e soluções para problemas frequentes.")
        else -> id
    }

    @Composable
    fun helpSectionTitle(id: String): String = when (id) {
        "start_prepare" -> get("Préparer l'application", "Prepare the app", "Preparar a aplicação")
        "start_search" -> get("Chercher une antenne", "Search for an antenna", "Procurar uma antena")
        "start_cards" -> get("Comprendre les fiches", "Understand detail pages", "Compreender as fichas")
        "home_buttons" -> get("Boutons de navigation", "Navigation buttons", "Botões de navegação")
        "home_banners" -> get("Bandeaux de statut", "Status banners", "Avisos de estado")
        "nearby_search" -> get("Barre de recherche", "Search bar", "Barra de pesquisa")
        "nearby_codes" -> get("Codes utiles", "Useful codes", "Códigos úteis")
        "nearby_list" -> get("Liste des sites", "Site list", "Lista de sites")
        "map_controls" -> get("Contrôles de carte", "Map controls", "Controlos do mapa")
        "map_filters" -> get("Recherche et filtres", "Search and filters", "Pesquisa e filtros")
        "map_offline" -> get("Cartes hors ligne", "Offline maps", "Mapas offline")
        "compass_use" -> get("Fonctionnement", "How it works", "Funcionamento")
        "buttons" -> get("Boutons", "Buttons", "Botões")
        "support_understand" -> get("Comprendre le support", "Understand the support", "Compreender o suporte")
        "actions" -> get("Actions disponibles", "Available actions", "Ações disponíveis")
        "site_info" -> get("Informations affichées", "Displayed information", "Informações apresentadas")
        "site_tools" -> get("Boutons et outils", "Buttons and tools", "Botões e ferramentas")
        "elevation_use" -> get("Utilisation", "Usage", "Utilização")
        "throughput_assumptions" -> get("Hypothèses de calcul", "Calculation assumptions", "Hipóteses de cálculo")
        "throughput_controls" -> get("Commandes", "Controls", "Comandos")
        "photos_view" -> get("Consulter les photos", "View photos", "Ver fotos")
        "photos_upload" -> get("Envoyer des photos", "Upload photos", "Enviar fotos")
        "share_create" -> get("Créer une image de partage", "Create a share image", "Criar uma imagem de partilha")
        "share_options" -> get("Comprendre les options", "Understand the options", "Compreender as opções")
        "settings_general" -> get("Préférences générales", "General preferences", "Preferências gerais")
        "settings_split" -> get("Affichage et split screen", "Display and split screen", "Apresentação e ecrã dividido")
        "settings_pages" -> get("Personnalisation des pages", "Page customization", "Personalização das páginas")
        "data_database" -> get("Base de données", "Database", "Base de dados")
        "data_maps" -> get("Cartes hors ligne", "Offline maps", "Mapas offline")
        "about_sections" -> get("Sections", "Sections", "Secções")
        "about_loops" -> get("Boucles de navigation", "Navigation loops", "Ciclos de navegação")
        "glossary_icons" -> get("Icônes courantes", "Common icons", "Ícones comuns")
        "glossary_issues" -> get("Problèmes fréquents", "Frequent issues", "Problemas frequentes")
        else -> id
    }

    @Composable
    fun helpSectionBody(id: String): String = when (id) {
        "start_prepare" -> get("Au premier lancement, vérifie que la base de données est téléchargée, que la localisation est autorisée et que ton opérateur principal est défini dans les paramètres. Sans base locale, les écrans d'antennes ne peuvent pas afficher les sites ANFR.", "On first launch, make sure the database is downloaded, location is allowed and your main operator is set in settings. Without the local database, antenna screens cannot show ANFR sites.", "No primeiro arranque, confirma que a base de dados foi transferida, que a localização está autorizada e que a tua operadora principal está definida nas configurações. Sem base local, os ecrãs de antenas não conseguem mostrar os sites ANFR.")
        "start_search" -> get("Depuis l'accueil, ouvre Antennes à proximité pour une liste autour de toi ou Carte des antennes pour explorer visuellement. Les deux chemins mènent ensuite aux fiches support puis aux fiches site.", "From Home, open Nearby antennas for a list around you or Antenna map to explore visually. Both paths then lead to support details and site details.", "No início, abre Antenas próximas para uma lista à tua volta ou Mapa de antenas para explorar visualmente. Os dois caminhos levam depois às fichas de suporte e de site.")
        "start_cards" -> get("Un support correspond au pylône, au toit ou au bâtiment. Un site correspond à l'installation d'un opérateur sur ce support. Les détails techniques, fréquences, azimuts et outils avancés se trouvent surtout sur la fiche site.", "A support is the tower, roof or building. A site is an operator installation on that support. Technical details, frequencies, azimuths and advanced tools are mostly on the site detail page.", "Um suporte corresponde ao poste, telhado ou edifício. Um site corresponde à instalação de uma operadora nesse suporte. Detalhes técnicos, frequências, azimutes e ferramentas avançadas estão sobretudo na ficha de site.")
        "home_buttons" -> get("L'accueil regroupe les raccourcis principaux de l'application. Certains boutons peuvent être masqués ou déplacés depuis Paramètres > Personnalisation des pages.", "Home groups the app's main shortcuts. Some buttons can be hidden or moved from Settings > Page customization.", "O início agrupa os atalhos principais da aplicação. Alguns botões podem ser ocultados ou movidos em Configurações > Personalização das páginas.")
        "home_banners" -> get("Si la base de données est absente, obsolète ou en cours de téléchargement, un bandeau s'affiche en haut. Si le réseau est absent, un bandeau hors ligne prévient que certaines fonctions ne pourront pas se mettre à jour.", "If the database is missing, outdated or downloading, a banner appears at the top. If the network is unavailable, an offline banner warns that some features cannot update.", "Se a base de dados estiver ausente, desatualizada ou em transferência, aparece um aviso no topo. Se não houver rede, um aviso offline indica que algumas funções não poderão atualizar.")
        "nearby_search" -> get("La recherche accepte du texte libre et des codes. Pour une ville, GeoTower peut utiliser la même logique que la carte : recherche de la zone de la commune puis filtrage des sites dans toute l'adresse ANFR.", "Search accepts free text and codes. For a city, GeoTower can use the same logic as the map: find the municipality area, then filter sites across the full ANFR address.", "A pesquisa aceita texto livre e códigos. Para uma cidade, o GeoTower pode usar a mesma lógica do mapa: encontra a área do município e filtra os sites em todo o endereço ANFR.")
        "nearby_codes" -> get("Utilise des préfixes quand une recherche peut être ambiguë : ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:pylône, op:Orange. Les accents, majuscules et tirets sont normalisés pour faciliter les recherches de communes.", "Use prefixes when a search may be ambiguous: ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:pylon, op:Orange. Accents, uppercase letters and hyphens are normalized to make city searches easier.", "Usa prefixos quando uma pesquisa pode ser ambígua: ville:Paris, cp:75015, gps:48.8566,2.3522, support:123456, anfr:987654, tech:5G, type:poste, op:Orange. Acentos, maiúsculas e hífenes são normalizados para facilitar pesquisas de municípios.")
        "nearby_list" -> get("Chaque carte de résultat présente le support, l'adresse, la distance et les opérateurs disponibles. Un appui ouvre le détail. En split screen, la liste reste à gauche et le détail s'ouvre à droite.", "Each result card shows the support, address, distance and available operators. Tapping opens the detail page. In split screen, the list stays on the left and details open on the right.", "Cada cartão de resultado mostra o suporte, endereço, distância e operadoras disponíveis. Um toque abre o detalhe. Em ecrã dividido, a lista fica à esquerda e o detalhe abre à direita.")
        "map_controls" -> get("La carte permet de parcourir les supports et sites. Un appui sur un marqueur ouvre les informations associées. Le bouton GPS recentre sur ta position quand la localisation est disponible.", "The map lets you browse supports and sites. Tapping a marker opens its information. The GPS button recenters on your position when location is available.", "O mapa permite explorar suportes e sites. Tocar num marcador abre as informações associadas. O botão GPS recentra na tua posição quando a localização está disponível.")
        "map_filters" -> get("La toolbox permet de chercher une ville ou une adresse, puis d'affiner l'affichage par opérateur, technologie, fréquence ou couche. Les filtres évitent de surcharger la carte quand la zone contient beaucoup d'antennes.", "The toolbox lets you search a city or address, then refine display by operator, technology, frequency or layer. Filters avoid overloading the map when an area contains many antennas.", "A caixa de ferramentas permite pesquisar cidade ou endereço e filtrar por operadora, tecnologia, frequência ou camada. Os filtros evitam sobrecarregar o mapa quando a zona tem muitas antenas.")
        "map_offline" -> get("Si une carte hors ligne est téléchargée et sélectionnée, GeoTower peut afficher la couche locale sans télécharger les tuiles classiques. Les fichiers et zones se règlent dans les paramètres.", "If an offline map is downloaded and selected, GeoTower can show the local layer without downloading standard tiles. Files and areas are managed in settings.", "Se um mapa offline estiver transferido e selecionado, o GeoTower pode mostrar a camada local sem transferir mosaicos clássicos. Ficheiros e zonas são geridos nas configurações.")
        "compass_use" -> get("La boussole utilise ta position, les capteurs du téléphone et les données d'antennes pour t'aider à comprendre dans quelle direction se trouvent les sites proches.", "The compass uses your position, phone sensors and antenna data to help you understand where nearby sites are located.", "A bússola usa a tua posição, sensores do telefone e dados de antenas para ajudar a perceber onde estão os sites próximos.")
        "buttons" -> get("Le bouton de retour ferme l'écran. Les actions internes peuvent lancer la recherche d'antenne, quitter un suivi en direct ou ouvrir un détail selon le mode affiché.", "The back button closes the screen. Internal actions can start antenna search, stop live tracking or open details depending on the displayed mode.", "O botão voltar fecha o ecrã. As ações internas podem iniciar a pesquisa de antena, sair do seguimento em direto ou abrir detalhes conforme o modo apresentado.")
        "support_understand" -> get("Le support représente la structure physique : pylône, bâtiment, château d'eau, toit ou autre. Plusieurs opérateurs et plusieurs sites peuvent être rattachés au même support.", "The support is the physical structure: tower, building, water tower, roof or another type. Several operators and sites can be linked to the same support.", "O suporte representa a estrutura física: poste, edifício, depósito de água, telhado ou outro. Várias operadoras e sites podem estar ligados ao mesmo suporte.")
        "actions" -> get("Les boutons du détail support servent à ouvrir la position sur la carte, lancer une navigation, partager les informations ou consulter les photos communautaires.", "Support detail buttons open the location on the map, start navigation, share information or view community photos.", "Os botões do detalhe do suporte servem para abrir a posição no mapa, iniciar navegação, partilhar informações ou ver fotos da comunidade.")
        "site_info" -> get("La fiche site décrit l'installation d'un opérateur : bandeau opérateur, support, hauteur, azimuts, fréquences, technologies, statut d'activation et liens externes.", "The site page describes an operator installation: operator banner, support, height, azimuths, frequencies, technologies, activation status and external links.", "A ficha de site descreve a instalação de uma operadora: faixa da operadora, suporte, altura, azimutes, frequências, tecnologias, estado de ativação e ligações externas.")
        "site_tools" -> get("Les boutons d'action ouvrent les outils liés au site. Certains blocs peuvent être masqués ou déplacés depuis la personnalisation de la page site.", "Action buttons open tools related to the site. Some blocks can be hidden or moved from site page customization.", "Os botões de ação abrem ferramentas ligadas ao site. Alguns blocos podem ser ocultados ou movidos na personalização da página de site.")
        "elevation_use" -> get("Le profil altimétrique compare ta position actuelle au site sélectionné. Il aide à estimer si le relief peut gêner le signal. Le calcul dépend de la position GPS et de données d'altitude en ligne.", "The elevation profile compares your current position to the selected site. It helps estimate whether terrain may block the signal. The calculation depends on GPS position and online elevation data.", "O perfil altimétrico compara a tua posição atual com o site selecionado. Ajuda a estimar se o relevo pode prejudicar o sinal. O cálculo depende da posição GPS e de dados de altitude online.")
        "throughput_assumptions" -> get("Le calcul donne un débit théorique. Le mode prudent réduit les attentes, le mode idéal montre un scénario favorable, et le mode personnalisé permet d'ajuster les bandes, technologies et modulations. L'upload tient compte du fait qu'un téléphone émet beaucoup moins fort qu'une antenne.", "The calculation gives theoretical throughput. Conservative mode lowers expectations, ideal mode shows a favorable scenario, and custom mode lets you adjust bands, technologies and modulations. Upload accounts for the fact that a phone transmits with far less power than an antenna.", "O cálculo dá um débito teórico. O modo prudente reduz expectativas, o modo ideal mostra um cenário favorável e o modo personalizado permite ajustar bandas, tecnologias e modulações. O upload considera que um telefone transmite com muito menos potência do que uma antena.")
        "throughput_controls" -> get("Les boutons 4G, 5G et projets choisissent les technologies prises en compte. Les cases de bandes sélectionnent les fréquences. Les sliders de modulation choisissent QPSK, 16 QAM, 64 QAM ou 256 QAM selon le niveau radio envisagé.", "The 4G, 5G and project buttons choose which technologies are included. Band checkboxes select frequencies. Modulation sliders choose QPSK, 16 QAM, 64 QAM or 256 QAM depending on the expected radio level.", "Os botões 4G, 5G e projetos escolhem as tecnologias consideradas. As caixas de bandas selecionam frequências. Os sliders de modulação escolhem QPSK, 16 QAM, 64 QAM ou 256 QAM conforme o nível rádio previsto.")
        "photos_view" -> get("Le carrousel affiche les photos communautaires disponibles. En plein écran, les contrôles permettent de fermer, changer de photo et inspecter l'image plus confortablement.", "The carousel shows available community photos. In full screen, controls let you close, switch photos and inspect the image more comfortably.", "O carrossel mostra as fotos comunitárias disponíveis. Em ecrã inteiro, os controlos permitem fechar, mudar de foto e inspecionar a imagem com mais conforto.")
        "photos_upload" -> get("L'upload SignalQuest permet d'associer des images à un support ou un site. Sélectionne les fichiers, vérifie l'opérateur cible, ajoute une description si besoin, puis lance l'envoi. L'envoi nécessite une connexion réseau.", "SignalQuest upload lets you attach images to a support or site. Select files, check the target operator, add a description if needed, then start the upload. Upload requires a network connection.", "O envio SignalQuest permite associar imagens a um suporte ou site. Seleciona ficheiros, verifica a operadora alvo, adiciona uma descrição se necessário e inicia o envio. O envio requer ligação de rede.")
        "share_create" -> get("Depuis une fiche, le bouton Partager ouvre les options. Tu peux inclure les détails du support, les fréquences, les dates d'activation, le meilleur speedtest, le calculateur de débit et parfois le profil altimétrique.", "From a detail page, the Share button opens options. You can include support details, frequencies, activation dates, best speedtest, throughput calculator and sometimes the elevation profile.", "A partir de uma ficha, o botão Partilhar abre opções. Podes incluir detalhes do suporte, frequências, datas de ativação, melhor speedtest, calculadora de débito e por vezes o perfil altimétrico.")
        "share_options" -> get("Chaque case ajoute ou retire un bloc de l'image finale. L'image respecte les unités choisies dans les paramètres, comme les distances en kilomètres ou en miles.", "Each checkbox adds or removes a block from the final image. The image respects the units chosen in settings, such as distances in kilometers or miles.", "Cada caixa adiciona ou remove um bloco da imagem final. A imagem respeita as unidades escolhidas nas configurações, como distâncias em quilómetros ou milhas.")
        "settings_general" -> get("Les paramètres regroupent le thème, le mode OLED, le design One UI, la langue, l'opérateur par défaut, les unités métriques ou impériales et le style de navigation dans les paramètres.", "Settings group theme, OLED mode, One UI design, language, default operator, metric or imperial units and settings navigation style.", "As configurações agrupam tema, modo OLED, design One UI, idioma, operadora padrão, unidades métricas ou imperiais e estilo de navegação nas configurações.")
        "settings_split" -> get("Le mode page rend les réglages plus compartimentés. Le split screen affiche les écrans compatibles en deux panneaux : contexte à gauche, détail ou outil à droite.", "Page mode makes settings more compartmentalized. Split screen shows compatible screens in two panels: context on the left, detail or tool on the right.", "O modo por páginas torna as configurações mais compartimentadas. O ecrã dividido mostra ecrãs compatíveis em dois painéis: contexto à esquerda, detalhe ou ferramenta à direita.")
        "settings_pages" -> get("Ce menu sert à afficher, masquer ou déplacer les blocs des pages. Pour l'accueil, tu peux aussi activer le bouton Aides et choisir son coin d'affichage.", "This menu is used to show, hide or move page blocks. For Home, you can also enable the Help button and choose its corner.", "Este menu serve para mostrar, ocultar ou mover blocos das páginas. No início, também podes ativar o botão Ajuda e escolher o canto onde aparece.")
        "data_database" -> get("La base contient les informations ANFR utilisées par les fiches, la carte et les recherches. Le téléchargement peut être lancé depuis l'accueil ou les paramètres. Les notifications de progression renvoient vers la section correspondante.", "The database contains ANFR information used by detail pages, the map and searches. Download can be started from Home or Settings. Progress notifications take you to the corresponding section.", "A base contém informações ANFR usadas pelas fichas, mapa e pesquisas. A transferência pode ser iniciada no início ou nas configurações. As notificações de progresso levam à secção correspondente.")
        "data_maps" -> get("Les cartes hors ligne permettent d'afficher une zone sans dépendre des tuiles réseau. Télécharge une zone, sélectionne la couche hors ligne, puis utilise la carte normalement dans la zone disponible.", "Offline maps let you display an area without relying on network tiles. Download an area, select the offline layer, then use the map normally inside the available area.", "Os mapas offline permitem mostrar uma zona sem depender de mosaicos de rede. Transfere uma zona, seleciona a camada offline e usa o mapa normalmente na área disponível.")
        "about_sections" -> get("L'écran À propos présente la version de GeoTower, les sources de données, les informations de développement et les liens utiles. La barre latérale permet de passer rapidement d'une section à l'autre quand l'écran est assez large.", "About shows the GeoTower version, data sources, development information and useful links. The side bar lets you quickly switch sections when the screen is wide enough.", "O ecrã Sobre mostra a versão do GeoTower, fontes de dados, informações de desenvolvimento e ligações úteis. A barra lateral permite mudar rapidamente de secção quando o ecrã é suficientemente largo.")
        "about_loops" -> get("Si tu viens des paramètres ou y retournes depuis À propos, GeoTower limite les empilements inutiles pour éviter de devoir appuyer trop de fois sur retour.", "If you come from settings or go back there from About, GeoTower limits unnecessary navigation stacking so you do not need to press Back many times.", "Se vieres das configurações ou voltares para lá a partir de Sobre, o GeoTower limita empilhamentos inúteis para evitar muitos toques no botão voltar.")
        "glossary_icons" -> get("Les mêmes icônes reviennent dans plusieurs écrans. Elles gardent généralement le même rôle.", "The same icons appear across several screens. They generally keep the same role.", "Os mesmos ícones aparecem em vários ecrãs. Geralmente mantêm a mesma função.")
        "glossary_issues" -> get("Si aucune antenne ne s'affiche, vérifie la base de données, les filtres et la zone de recherche. Si le GPS ne répond pas, vérifie l'autorisation Android et attends quelques secondes dehors ou près d'une fenêtre. Si la carte hors ligne ne s'affiche pas, vérifie que le fichier couvre bien la zone visible.", "If no antenna appears, check the database, filters and search area. If GPS does not respond, check Android permission and wait a few seconds outside or near a window. If the offline map does not appear, make sure the file covers the visible area.", "Se nenhuma antena aparecer, verifica a base de dados, filtros e área de pesquisa. Se o GPS não responder, verifica a permissão Android e espera alguns segundos no exterior ou perto de uma janela. Se o mapa offline não aparecer, confirma que o ficheiro cobre a área visível.")
        else -> id
    }

    @Composable
    fun helpActionTitle(id: String): String = when (id) {
        "home_nearby" -> nearAntennas
        "home_map" -> mapTitle
        "home_compass" -> compassTitle
        "home_settings" -> settingsTitle
        "home_about" -> about
        "home_help" -> helpTitle
        "search_field" -> get("Champ de recherche", "Search field", "Campo de pesquisa")
        "clear" -> get("X / Effacer", "X / Clear", "X / Limpar")
        "quick_suggestions" -> get("Suggestions rapides", "Quick suggestions", "Sugestões rápidas")
        "info" -> get("Info", "Info", "Info")
        "load_more" -> get("Afficher plus", "Show more", "Mostrar mais")
        "expand_area" -> get("Élargir la zone", "Expand area", "Expandir a zona")
        "location" -> get("Localisation", "Location", "Localização")
        "zoom" -> get("Zoom + / -", "Zoom + / -", "Zoom + / -")
        "map_compass" -> get("Boussole de carte", "Map compass", "Bússola do mapa")
        "scale" -> get("Échelle", "Scale", "Escala")
        "live_search" -> get("Recherche en direct", "Live search", "Pesquisa em direto")
        "quit" -> get("Quitter", "Stop", "Sair")
        "open_map" -> get("Ouvrir la carte", "Open map", "Abrir o mapa")
        "navigate" -> get("Naviguer", "Navigate", "Navegar")
        "share" -> get("Partager", "Share", "Partilhar")
        "photos" -> get("Photos", "Photos", "Fotos")
        "operator_site" -> get("Site opérateur", "Operator site", "Site da operadora")
        "elevation_profile" -> get("Profil altimétrique", "Elevation profile", "Perfil altimétrico")
        "throughput" -> get("Débit théorique", "Theoretical throughput", "Débito teórico")
        "settings_gear" -> get("Réglage", "Settings", "Configuração")
        "recalculate" -> get("Recalculer", "Recalculate", "Recalcular")
        "calculate_later" -> get("Calculer plus tard", "Calculate later", "Calcular mais tarde")
        "custom" -> get("Personnalisé", "Custom", "Personalizado")
        "optimal_distance" -> get("Distance optimale", "Optimal distance", "Distância ideal")
        "mini_map" -> get("Mini-carte", "Mini-map", "Mini-mapa")
        "back_arrow" -> get("Flèche retour", "Back arrow", "Seta voltar")
        "magnifier" -> get("Loupe", "Magnifier", "Lupa")
        "x_icon" -> get("X", "X", "X")
        "gear" -> get("Engrenage", "Gear", "Engrenagem")
        "share_icon" -> get("Partager", "Share", "Partilhar")
        "download" -> get("Télécharger", "Download", "Transferir")
        "refresh" -> get("Actualiser", "Refresh", "Atualizar")
        else -> id
    }

    @Composable
    fun helpActionDesc(id: String): String = when (id) {
        "home_nearby" -> get("Ouvre la liste des sites autour de ta position ou autour de la zone recherchée.", "Opens the list of sites around your position or searched area.", "Abre a lista de sites à volta da tua posição ou da zona pesquisada.")
        "home_map" -> get("Ouvre la carte interactive avec les marqueurs, les filtres et les outils cartographiques.", "Opens the interactive map with markers, filters and map tools.", "Abre o mapa interativo com marcadores, filtros e ferramentas cartográficas.")
        "home_compass" -> get("Ouvre l'outil d'orientation pour viser les antennes autour de toi.", "Opens the orientation tool to aim toward antennas around you.", "Abre a ferramenta de orientação para apontar para antenas à tua volta.")
        "home_settings" -> get("Ouvre les préférences, la personnalisation, les cartes hors ligne et la gestion de la base.", "Opens preferences, customization, offline maps and database management.", "Abre preferências, personalização, mapas offline e gestão da base.")
        "home_about" -> get("Affiche la version, les sources de données et les informations de développement.", "Shows version, data sources and development information.", "Mostra versão, fontes de dados e informações de desenvolvimento.")
        "home_help" -> get("Ouvre ce centre d'aide. Sa position se règle dans la personnalisation de la page d'accueil.", "Opens this help center. Its position is configured in Home page customization.", "Abre este centro de ajuda. A posição é configurada na personalização da página inicial.")
        "search_field" -> get("Tape une ville, une adresse, un code postal, un support ID, un ID ANFR, une technologie, un type de support ou des coordonnées GPS.", "Type a city, address, postal code, support ID, ANFR ID, technology, support type or GPS coordinates.", "Escreve cidade, endereço, código postal, ID de suporte, ID ANFR, tecnologia, tipo de suporte ou coordenadas GPS.")
        "clear" -> get("Vide la recherche et revient à la liste précédente.", "Clears the search and returns to the previous list.", "Limpa a pesquisa e volta à lista anterior.")
        "quick_suggestions" -> get("Insère des recherches fréquentes. Elles peuvent être masquées depuis la personnalisation des pages.", "Inserts common searches. They can be hidden from page customization.", "Insere pesquisas frequentes. Podem ser ocultadas na personalização das páginas.")
        "info" -> get("Affiche la liste des codes disponibles et leur usage.", "Shows the list of available codes and how to use them.", "Mostra a lista de códigos disponíveis e como os usar.")
        "load_more" -> get("Ajoute davantage de résultats à la liste.", "Adds more results to the list.", "Adiciona mais resultados à lista.")
        "expand_area" -> get("Augmente la zone de recherche quand aucun site pertinent n'est trouvé.", "Increases the search area when no relevant site is found.", "Aumenta a zona de pesquisa quando nenhum site relevante é encontrado.")
        "location" -> get("Lance ou relance la recherche GPS et centre la carte sur ta position.", "Starts or restarts GPS search and centers the map on your position.", "Inicia ou reinicia a pesquisa GPS e centra o mapa na tua posição.")
        "zoom" -> get("Rapproche ou éloigne la carte.", "Zooms the map in or out.", "Aproxima ou afasta o mapa.")
        "map_compass" -> get("Indique l'orientation actuelle de la carte si l'option est activée.", "Shows the current map orientation if enabled.", "Mostra a orientação atual do mapa se a opção estiver ativa.")
        "scale" -> get("Affiche une estimation des distances visibles.", "Shows an estimate of visible distances.", "Mostra uma estimativa das distâncias visíveis.")
        "live_search" -> get("Actualise l'antenne ou le site cible selon ta position.", "Updates the target antenna or site based on your position.", "Atualiza a antena ou site alvo conforme a tua posição.")
        "quit" -> get("Arrête le suivi actif et revient au mode normal.", "Stops active tracking and returns to normal mode.", "Para o seguimento ativo e volta ao modo normal.")
        "open_map" -> get("Centre la carte GeoTower sur ce support.", "Centers the GeoTower map on this support.", "Centra o mapa GeoTower neste suporte.")
        "navigate" -> get("Ouvre l'application de navigation du téléphone vers les coordonnées.", "Opens the phone navigation app toward the coordinates.", "Abre a aplicação de navegação do telefone para as coordenadas.")
        "share" -> get("Ouvre les options de partage et de génération d'image.", "Opens sharing and image generation options.", "Abre opções de partilha e geração de imagem.")
        "photos" -> get("Affiche les photos communautaires et les schémas si disponibles.", "Shows community photos and diagrams if available.", "Mostra fotos comunitárias e esquemas se disponíveis.")
        "operator_site" -> get("Ouvre la fiche détaillée d'un opérateur installé sur le support.", "Opens the detail page for an operator installed on the support.", "Abre a ficha detalhada de uma operadora instalada no suporte.")
        "elevation_profile" -> get("Calcule le relief entre ta position et le site.", "Calculates terrain between your position and the site.", "Calcula o relevo entre a tua posição e o site.")
        "throughput" -> get("Ouvre le calculateur de débit basé sur les fréquences et hypothèses radio.", "Opens the throughput calculator based on frequencies and radio assumptions.", "Abre a calculadora de débito baseada em frequências e hipóteses rádio.")
        "settings_gear" -> get("Ouvre la personnalisation des blocs de la page quand disponible.", "Opens page block customization when available.", "Abre a personalização dos blocos da página quando disponível.")
        "recalculate" -> get("Relance l'analyse avec la position et les données actuelles.", "Runs the analysis again with current position and data.", "Repete a análise com a posição e os dados atuais.")
        "calculate_later" -> get("Garde temporairement la demande pour la lancer quand la connexion revient.", "Temporarily saves the request to run it when the connection returns.", "Guarda temporariamente o pedido para o executar quando a ligação voltar.")
        "custom" -> get("Déverrouille les réglages fins de modulation, technologies et bandes.", "Unlocks fine settings for modulation, technologies and bands.", "Desbloqueia definições detalhadas de modulação, tecnologias e bandas.")
        "optimal_distance" -> get("Affiche la zone où l'utilisateur a le plus de chance d'être dans le cône d'émission.", "Shows the area where the user is most likely to be inside the antenna emission cone.", "Mostra a zona onde o utilizador tem mais hipóteses de estar no cone de emissão.")
        "mini_map" -> get("Visualise le cercle de distance optimale et les points où le débit estimé est le plus fort.", "Visualizes the optimal distance circle and points where estimated throughput is strongest.", "Visualiza o círculo de distância ideal e os pontos onde o débito estimado é mais forte.")
        "back_arrow" -> get("Revient à l'écran précédent ou au sommaire local.", "Returns to the previous screen or local contents.", "Volta ao ecrã anterior ou ao índice local.")
        "magnifier" -> get("Ouvre ou représente une recherche.", "Opens or represents search.", "Abre ou representa uma pesquisa.")
        "x_icon" -> get("Efface un champ ou ferme un mode.", "Clears a field or closes a mode.", "Limpa um campo ou fecha um modo.")
        "gear" -> get("Ouvre les réglages ou la personnalisation du bloc.", "Opens settings or block customization.", "Abre as configurações ou a personalização do bloco.")
        "share_icon" -> get("Ouvre les options de partage.", "Opens sharing options.", "Abre opções de partilha.")
        "download" -> get("Lance un téléchargement de base ou de carte.", "Starts a database or map download.", "Inicia a transferência da base ou de um mapa.")
        "refresh" -> get("Relance un calcul, une vérification ou remet les réglages par défaut selon le contexte.", "Restarts a calculation, check or resets settings depending on context.", "Reinicia um cálculo, verificação ou repõe definições conforme o contexto.")
        else -> id
    }

    // ==========================================
    // ⚙️ PARAMÈTRES
    // ==========================================
    val appearance @Composable get() = get("Apparence", "Appearance", "Aparência")
    val mapping @Composable get() = get("Cartographie", "Mapping", "Cartografia")
    val preferences @Composable get() = get("Préférences", "Preferences", "Preferências")
    val system @Composable get() = get("Système", "System", "Sistema")
    val database @Composable get() = get("Base de données", "Database", "Base de dados")

    val themeLight @Composable get() = get("Clair", "Light", "Claro")
    val themeDark @Composable get() = get("Sombre", "Dark", "Escuro")
    val appIcon @Composable get() = get("Icône de l'application", "App Icon", "Ícone da aplicação")
    val appLogoDrawingTitle @Composable get() = get("Logo affiché dans l'application", "In-app logo", "Logótipo na aplicação")
    val appLogoDrawingSubtitle @Composable get() = get("Choisit le dessin utilisé sur l'accueil, le splash et À propos.", "Chooses the artwork used on Home, splash and About.", "Escolhe o desenho usado no início, no splash e em Sobre.")
    val logoDrawingAuto @Composable get() = get("Automatique", "Automatic", "Automático")
    val logoDrawingAutoDesc @Composable get() = get("Suit l'icône d'application active et le thème.", "Follows the active app icon and theme.", "Segue o ícone ativo da aplicação e o tema.")
    val logoDrawingColorOnDark @Composable get() = get("Couleur fond sombre", "Color on dark", "Cor em fundo escuro")
    val logoDrawingColorOnLight @Composable get() = get("Couleur fond clair", "Color on light", "Cor em fundo claro")
    val logoDrawingMonoLight @Composable get() = get("Monochrome clair", "Light monochrome", "Monocromático claro")
    val logoDrawingMonoDark @Composable get() = get("Monochrome sombre", "Dark monochrome", "Monocromático escuro")
    val logoDrawingMonoMuted @Composable get() = get("Monochrome gris", "Muted monochrome", "Monocromático cinzento")
    val restartToApply @Composable get() = get("L'app redémarrera pour appliquer le changement.", "The app will restart to apply the change.", "O aplicativo será reiniciado para appliquer a alteração.")
    val colorPaletteTitle @Composable get() = get("Palette de couleur", "Color palette", "Paleta de cores")
    val colorSourceTitle @Composable get() = get("Source des couleurs", "Color source", "Fonte das cores")
    val colorSourceDesc @Composable get() = get(
        "Le mode Dynamique (fond d'écran) est prioritaire. Sinon, choisissez une palette Material 3 native.",
        "Dynamic mode (wallpaper) takes priority. Otherwise, choose a native Material 3 palette.",
        "O modo Dinâmico (papel de parede) tem prioridade. Caso contrário, escolha uma paleta Material 3 nativa."
    )
    val colorPaletteDynamicTitle @Composable get() = get("Dynamique (fond d'écran)", "Dynamic (wallpaper)", "Dinâmico (papel de parede)")
    val colorPaletteDynamicDesc @Composable get() = get(
        "Utilise automatiquement les couleurs système du téléphone (Material You).",
        "Automatically uses the phone system colors (Material You).",
        "Usa automaticamente as cores do sistema do telemóvel (Material You)."
    )
    val colorPaletteBaselineTitle @Composable get() = get("Material Baseline", "Material Baseline", "Material Baseline")
    val colorPaletteBaselineDesc @Composable get() = get("Palette Material 3 native de référence Android", "Native reference Android Material 3 palette", "Paleta Material 3 nativa de referência Android")
    val colorPaletteRedTitle @Composable get() = get("Material Red", "Material Red", "Material Red")
    val colorPaletteRedDesc @Composable get() = get("Palette système rouge Material 3", "Material 3 red system palette", "Paleta de sistema vermelha Material 3")
    val colorPaletteGreenTitle @Composable get() = get("Material Green", "Material Green", "Material Green")
    val colorPaletteGreenDesc @Composable get() = get("Palette système verte Material 3", "Material 3 green system palette", "Paleta de sistema verde Material 3")
    val colorPaletteBlueTitle @Composable get() = get("Material Blue", "Material Blue", "Material Blue")
    val colorPaletteBlueDesc @Composable get() = get("Palette système bleue Material 3", "Material 3 blue system palette", "Paleta de sistema azul Material 3")
    val colorPaletteCyanTitle @Composable get() = get("Material Cyan", "Material Cyan", "Material Cyan")
    val colorPaletteCyanDesc @Composable get() = get("Palette système cyan Material 3", "Material 3 cyan system palette", "Paleta de sistema ciano Material 3")
    val colorPaletteTealTitle @Composable get() = get("Material Teal", "Material Teal", "Material Teal")
    val colorPaletteTealDesc @Composable get() = get("Palette système teal Material 3", "Material 3 teal system palette", "Paleta de sistema teal Material 3")
    val colorPaletteIndigoTitle @Composable get() = get("Material Indigo", "Material Indigo", "Material Indigo")
    val colorPaletteIndigoDesc @Composable get() = get("Palette système indigo Material 3", "Material 3 indigo system palette", "Paleta de sistema indigo Material 3")
    val colorPaletteRoseTitle @Composable get() = get("Material Rose", "Material Rose", "Material Rose")
    val colorPaletteRoseDesc @Composable get() = get("Palette système rose expressive Material 3", "Expressive Material 3 rose system palette", "Paleta de sistema rosa expressiva Material 3")
    val colorPaletteAmberTitle @Composable get() = get("Material Amber", "Material Amber", "Material Amber")
    val colorPaletteAmberDesc @Composable get() = get("Palette système ambre Material 3", "Material 3 amber system palette", "Paleta de sistema âmbar Material 3")
    val colorPaletteGraphiteTitle @Composable get() = get("Material Graphite", "Material Graphite", "Material Graphite")
    val colorPaletteGraphiteDesc @Composable get() = get("Palette système graphite Material 3", "Material 3 graphite system palette", "Paleta de sistema grafite Material 3")
    @Composable
    fun colorPaletteName(key: String): String = when (key) {
        "baseline" -> colorPaletteBaselineTitle
        "red", "canada" -> colorPaletteRedTitle
        "green" -> colorPaletteGreenTitle
        "blue" -> colorPaletteBlueTitle
        "cyan" -> colorPaletteCyanTitle
        "teal" -> colorPaletteTealTitle
        "indigo" -> colorPaletteIndigoTitle
        "rose" -> colorPaletteRoseTitle
        "amber" -> colorPaletteAmberTitle
        "graphite" -> colorPaletteGraphiteTitle
        else -> colorPaletteDynamicTitle
    }

    @Composable
    fun colorPaletteDescription(key: String): String = when (key) {
        "baseline" -> colorPaletteBaselineDesc
        "red", "canada" -> colorPaletteRedDesc
        "green" -> colorPaletteGreenDesc
        "blue" -> colorPaletteBlueDesc
        "cyan" -> colorPaletteCyanDesc
        "teal" -> colorPaletteTealDesc
        "indigo" -> colorPaletteIndigoDesc
        "rose" -> colorPaletteRoseDesc
        "amber" -> colorPaletteAmberDesc
        "graphite" -> colorPaletteGraphiteDesc
        else -> colorPaletteDynamicDesc
    }

    @Composable
    fun logoDrawingFamilyName(family: String): String = when (family) {
        "georadio" -> "GeoRadio"
        "fun" -> "Fun"
        else -> "GeoTower"
    }

    @Composable
    fun logoDrawingStyleName(style: String): String = when (style) {
        "color_on_light" -> logoDrawingColorOnLight
        "mono_light" -> logoDrawingMonoLight
        "mono_dark" -> logoDrawingMonoDark
        "mono_muted" -> logoDrawingMonoMuted
        else -> logoDrawingColorOnDark
    }

    @Composable
    fun logoDrawingChoiceName(choice: String): String {
        val normalized = AppLogoDrawingResources.normalize(choice)
        if (normalized == AppLogoDrawingResources.AUTO) return logoDrawingAuto
        val family = normalized.substringBefore(":")
        val style = normalized.substringAfter(":")
        return "${logoDrawingFamilyName(family)} · ${logoDrawingStyleName(style)}"
    }

    @Composable
    fun logoDrawingChoiceDescription(choice: String): String {
        val normalized = AppLogoDrawingResources.normalize(choice)
        if (normalized == AppLogoDrawingResources.AUTO) return logoDrawingAutoDesc
        return appLogoDrawingSubtitle
    }

    val systemLanguage @Composable get() = get("Langage système", "System language", "Idioma do sistema", "Lingua di sistema", "Systemsprache", "Idioma del sistema")

    val mapIgn @Composable get() = get("IGN (Gouv)", "IGN (Gov)", "IGN (Gov)")
    val mapOsm @Composable get() = get("OpenStreetMap", "OpenStreetMap", "OpenStreetMap")
    val mapStyle @Composable get() = get("Style de carte", "Map Style", "Estilo do mapa")
    val mapSat @Composable get() = get("Sat", "Sat", "Sat")

    val navMode @Composable get() = get("Mode de navigation dans les paramètres", "Navigation mode in settings", "Modo de navegação nas configurações")
    val navScroll @Composable get() = get("Défilement continu", "Continuous scroll", "Deslocamento contínuo")
    val navPages @Composable get() = get("Système par pages", "Page system", "Sistema de páginas")
    val navScrollTitle @Composable get() = get("Défilant", "Scrolling", "Rolagem")
    val navScrollDesc @Composable get() = get("Toutes les options sur une page", "All options on one page", "Todas as options numa page")
    val navPagesTitle @Composable get() = get("Pages", "Pages", "Páginas")
    val navPagesDesc @Composable get() = get("Afficher une catégorie à la fois", "Show one category at a time", "Mostrar uma catégorie de cada vez")

    val oneUiInterface @Composable get() = get("Interface One UI", "One UI Interface", "Interface One UI")

    val defaultOperator @Composable get() = get("Opérateur par défaut", "Default Operator", "Operadora padrão")
    val operatorRegionMetro @Composable get() = get("Métropole", "Mainland France", "Metrópole")
    val operatorRegionOverseas @Composable get() = get("Outre-mer", "Overseas", "Ultramar")
    val selectAll @Composable get() = get("Tout sélectionner", "Select all", "Selecionar tudo")
    val clearAll @Composable get() = get("Tout désélectionner", "Clear all", "Limpar tudo")
    val appLanguageLabel @Composable get() = get("Langue de l'application", "App Language", "Idioma da aplicação", "Lingua dell'app", "App-Sprache", "Idioma de la aplicación")
    val none @Composable get() = get("Aucun", "None", "Nenhum")
    val select @Composable get() = get("Sélectionner", "Select", "Selecionar")

    @Composable
    fun current(value: String) = get("Actuel : $value", "Current : $value", "Atual : $value")
    val validate @Composable get() = get("Valider", "Validate", "Validar", "Conferma", "Bestätigen", "Validar")
    @Composable
    fun validateForLanguage(languageValue: String): String {
        val langToCheck = if (languageValue == LANGUAGE_SYSTEM) currentSystemLanguage() else languageValue
        return resolveForLanguage(
            langToCheck,
            "Valider",
            "Validate",
            "Validar",
            "Conferma",
            "Bestätigen",
            "Validar"
        )
    }
    val upToDate @Composable get() = get("À jour", "Up to date", "Atualizado")

    val managePermissions @Composable get() = get("Gérer les permissions", "Manage Permissions", "Gerir permissões")
    val permissionsDesc @Composable get() = get("Localisation et Notifications", "Location and Notifications", "Localização e Notificações")

    val offlineDesc @Composable get() = get("Télécharge toute la base pour utiliser la liste sans réseau. Attention : fichier volumineux.", "Download the entire database to use the list offline. Warning : large file.", "Transfere toute a base de données para utiliser a liste offline. Aviso : ficheiro grande.")
    val downloadAntennas @Composable get() = get("Télécharger les antennes", "Download antennas", "Transferir antenas")
    val cancelDownload @Composable get() = get("Annuler le téléchargement", "Cancel download", "Cancelar transferência")
    val pageCompass @Composable get() = get("Boussole", "Compass", "Bússola")
    val dragToReorderHint @Composable get() = get("(Appui long et glissé pour déplacer)", "(Long press and drag to reorder)", "(Pressione e segure para arrastar)")
    @Composable
    fun downloadProgress(progress: Int) = get("Téléchargement : $progress %", "Downloading : $progress %", "A transferir : $progress %")
    @Composable
    fun fileSizeMb(sizeMb: Int) = get("$sizeMb Mo", "$sizeMb MB", "$sizeMb MB", "$sizeMb MB", "$sizeMb MB", "$sizeMb MB")

    val menuSizeTitle @Composable get() = get("Taille du menu principal", "Main menu size", "Tamanho do menu principal")
    val menuSizeSmall @Composable get() = get("Petit", "Small", "Pequeno")
    val menuSizeNormal @Composable get() = get("Normal", "Normal", "Normal")
    val menuSizeLarge @Composable get() = get("Large", "Large", "Grande")
    val widgetRefreshTitle @Composable get() = get("Actualisation du widget", "Widget refresh rate", "Atualização do widget")
    val widgetRefreshWarning @Composable get() = get("Attention : Une fréquence élevée (30 min) peut augmenter la consommation de batterie en arrière-plan.", "Warning: A high frequency (30 min) may increase background battery consumption.", "Atenção: Uma frequência alta (30 min) pode aumentar o consumo de bateria em segundo plano.")
    val navStyleTitle @Composable get() = get("Style de navigation", "Navigation style", "Estilo de navegação")
    val shareSupportDetailsTitle @Composable get() = get("Détails du pylône (Support)", "Pylon details (Support)", "Detalhes do pilão (Suporte)")
    val shareSiteDetailsTitle @Composable get() = get("Détails de l'antenne (Site)", "Antenna details (Site)", "Detalhes da antena (Site)")
    val supportShareTitle @Composable get() = get("Partage du pylône", "Pylon share", "Partilha do pilão")

    val mapMapLibre @Composable get() = get("MapLibre", "MapLibre", "MapLibre")
    val mapTopo @Composable get() = get("OpenTopoMap", "OpenTopoMap", "OpenTopoMap")
    val mapOfflineLayer @Composable get() = get("Hors-ligne", "Offline", "Offline")
    // --- PERSONNALISATION DES PAGES ---
    val pagesCustomizationTitle @Composable get() = get("Personnalisation des pages", "Pages customization", "Personalização das páginas")
    val pagesCustomizationDesc @Composable get() = get("Personnalisez l'affichage des différentes pages de l'application", "Customize the display of the different pages of the application", "Personalize a exibição das differentes páginas do aplicativo")

    val startupPageSettings @Composable get() = get("Page de démarrage", "Startup page", "Página de inicialização")
    val pageHomeSettings @Composable get() = get("Page d'accueil", "Home page", "Página inicial")
    val pageNearbySettings @Composable get() = get("Antennes à proximité", "Nearby antennas", "Antenas próximas")
    val pageMapSettings @Composable get() = get("Carte des antennes", "Antennas map", "Mapa de antenas")
    val pageCompassSettings @Composable get() = get("Boussole", "Compass", "Bússola")
    val statsGroupTitle @Composable get() = get("Statistiques", "Statistics", "Estatísticas")
    val homeHelpSettings @Composable get() = get("Aides", "Help", "Ajuda")
    val homeHelpPositionSettings @Composable get() = get("Position du bouton Aides", "Help button position", "Posição do botão Ajuda")
    val positionTopLeft @Composable get() = get("En haut à gauche", "Top left", "Em cima à esquerda")
    val positionTopRight @Composable get() = get("En haut à droite", "Top right", "Em cima à direita")
    val positionBottomLeft @Composable get() = get("En bas à gauche", "Bottom left", "Em baixo à esquerda")
    val positionBottomRight @Composable get() = get("En bas à droite", "Bottom right", "Em baixo à direita")
    val nearbySearchOption @Composable get() = get("Barre de recherche", "Search bar", "Barra de pesquisa")
    val nearbySearchSuggestionsOption @Composable get() = get("Suggestions rapides", "Quick suggestions", "Sugestões rápidas")
    val nearbySitesOption @Composable get() = get("Sites les plus proches", "Nearest sites", "Locais mais prochains")
    val searchRadiusTitle @Composable get() = get("Rayon de recherche", "Search radius", "Raio de pesquisa")
    val compassLocationOption @Composable get() = get("Lieu", "Location", "Local")
    val compassGpsOption @Composable get() = get("Localisation", "Coordinates", "Coordenadas")
    val compassAccuracyOption @Composable get() = get("Précision", "Accuracy", "Precisão")
    val mapLocationSectionTitle @Composable get() = get("Localisation", "Location", "Localização")
    val mapLocationOption @Composable get() = get("Bouton de localisation", "Location button", "Botão de localisation")
    val mapLocationMarkerOption @Composable get() = get("Point GPS", "GPS dot", "Ponto GPS")
    val mapAzimuthsOption @Composable get() = get("Azimuts", "Azimuths", "Azimutes")
    val mapAzimuthLinesOption @Composable get() = get("Traits d'azimut", "Azimuth lines", "Linhas de azimute")
    val mapAzimuthConesOption @Composable get() = get("Cônes d'azimut", "Azimuth cones", "Cones de azimute")
    val mapZoomOption @Composable get() = get("Boutons de zoom", "Zoom buttons", "Botões de zoom")
    val mapToolboxOption @Composable get() = get("Toolbox (Outils)", "Toolbox", "Ferramentas")
    val mapCompassOption @Composable get() = get("Boussole de la carte", "Map compass", "Bússola do mapa")
    val mapScaleOption @Composable get() = get("Échelle de la carte", "Map scale", "Escala do mapa")
    val mapAttributionOption @Composable get() = get("Crédits (Attribution)", "Credits (Attribution)", "Créditos (Atribuição)")
    val resetToDefault @Composable get() = get("Rétablir les paramètres par défaut", "Reset to default settings", "Restaurar configurações padrão")
    val pageSupportSettings @Composable get() = get("Détail du pylône (Support)", "Support details", "Detalhes do suporte")
    val pageSiteSettings @Composable get() = get("Détail de l'antenne (Site)", "Site details", "Detalhes do site")
    val sitePhotosAndSchemesOption @Composable get() = get("Photos communautaires et schémas", "Community photos and diagrams", "Fotos da comunidade e esquemas")
    val sitePhotosSettingsTitle @Composable get() = get("Réglages des photos et schémas", "Photos and diagrams settings", "Configurações de fotos e esquemas")
    val showCellularFrPhotosLabel @Composable get() = get("Afficher les photos de CellularFR", "Show CellularFR photos", "Mostrar fotos do CellularFR")
    val showSignalQuestPhotosLabel @Composable get() = get("Afficher les photos de SignalQuest", "Show SignalQuest photos", "Mostrar fotos do SignalQuest")
    val showSchemesLabel @Composable get() = get("Afficher les schémas du support", "Show support diagrams", "Mostrar esquemas do suporte")
    val showExifLabel @Composable get() = get("Afficher les EXIF", "Show EXIF", "Mostrar EXIF", "Mostra EXIF", "EXIF anzeigen", "Mostrar EXIF")
    val supportMapOption @Composable get() = get("Mini-carte", "Mini-map", "Mini-mapa")
    val supportDetailsOption @Composable get() = get("Détails du pylône", "Support details", "Detalhes do suporte")
    val supportPhotosOption @Composable get() = get("Photos communautaires", "Community photos", "Fotos da comunidade")
    val supportOpenMapOption @Composable get() = get("Bouton Ouvrir la carte", "Open map button", "Botao Abrir o mapa")
    val supportNavOption @Composable get() = get("Bouton Naviguer", "Navigate button", "Botão Navegar")
    val supportShareOption @Composable get() = get("Bouton Partager", "Share button", "Botão Compartilhar")
    val supportOperatorsOption @Composable get() = get("Liste des opérateurs", "Operators list", "Lista de operadoras")
    val siteOperatorOption @Composable get() = get("Bandeau Opérateur", "Operator banner", "Banner da operadora")
    val siteBearingHeightOption @Composable get() = get("Cap et Hauteur", "Bearing and Height", "Rumo e Altura")
    val siteMapOption @Composable get() = get("Mini-carte", "Mini-map", "Mini-mapa")
    val siteSupportDetailsOption @Composable get() = get("Détails du pylône", "Support details", "Detalhes do suporte")
    val siteIdsOption @Composable get() = get("Identifiants", "Identifiers", "Identificadores")
    val siteOpenMapOption @Composable get() = get("Bouton Ouvrir la carte", "Open map button", "Botao Abrir o mapa")
    val siteElevationProfileOption @Composable get() = get("Bouton Profil altimétrique", "Elevation profile button", "Botão Perfil altimétrico")
    val siteThroughputCalculatorOption @Composable get() = get("Bouton Calculateur de débit", "Throughput calculator button", "Botão Calculadora de débito")
    val siteNavOption @Composable get() = get("Bouton Naviguer", "Navigate button", "Botão Navegar")
    val siteShareOption @Composable get() = get("Bouton Partager", "Share button", "Botão Compartilhar")
    val siteDatesOption @Composable get() = get("Dates d'activation", "Activation dates", "Datas de ativação")
    val siteAddressOption @Composable get() = get("Adresse et Coordonnées", "Address & Coordinates", "Endereço e Coordonadas")
    val siteFreqsOption @Composable get() = get("Fréquences, Spectres et Azimuts", "Frequencies, Spectrum & Azimuths", "Frequências, Espectros e Azimutes")
    val siteLinksOption @Composable get() = get("Liens externes", "External links", "Links externes")
    val communityDataSettingsTitle @Composable get() = get("Données communautaires", "Community data", "Dados comunitários")
    val communityDataSettingsDesc @Composable get() = get("Choisir les opérateurs, les sources et l'ordre d'affichage des photos et speedtests.", "Choose operators, sources, and display order for photos and speedtests.", "Escolher operadores, fontes e ordem de apresentação das fotos e speedtests.")
    val communityDataPhotos @Composable get() = get("Photos", "Photos", "Fotos")
    val communityDataSpeedtest @Composable get() = get("Speedtest", "Speedtest", "Speedtest")
    val communityDataShowOperatorPhotos @Composable get() = get("Afficher les photos de cet opérateur", "Show photos for this operator", "Mostrar fotos deste operador")
    val communityDataPhotoSourceOrder @Composable get() = get("Ordre des sources", "Source order", "Ordem das fontes")
    val communityDataPhotoSourceFallbackOnly @Composable get() = get("Afficher seulement si les sources au-dessus n'ont aucune photo", "Show only if sources above have no photos", "Mostrar apenas se as fontes acima não tiverem fotos")
    val externalLinksSettingsTitle @Composable get() = get("Liens externes", "External links", "Links externos")
    val externalLinksSettingsDesc @Composable get() = get("Gérer l'ordre et l'affichage des raccourcis externes", "Manage the order and display of external shortcuts", "Gerir a ordem e apresentação dos atalhos externos")
    val resetSettings @Composable get() = get("Réinitialiser les paramètres", "Reset settings", "Redefinir configurações")
    val resetWarningTitle @Composable get() = get("Attention", "Warning", "Aviso")
    val resetWarningDesc @Composable get() = get("Êtes-vous sûr de vouloir rétablir les paramètres par défaut ? Cela supprimera tous les réglages que vous avez faits dans l'application.", "Are you sure you want to restore default settings? This will delete all settings you have made in the app.", "Tem certeza de que deseja restaurar as configurações padrão? Isso excluirá toutes as configurações que vous avez fez no aplicativo.")
    val yes @Composable get() = get("Oui", "Yes", "Sim")
    val no @Composable get() = get("Non", "No", "Não")
    val offlineMessage @Composable get() = get("Vous êtes hors ligne", "You are offline", "Você está offline")
    val pageHomeLogoSettings @Composable get() = get("Logo de l'application", "App logo", "Logótipo da aplicação")
    val homeLogoSettingTitle @Composable get() = get("Logo de la page d'accueil", "Home page logo", "Logótipo da page inicial")
    val logoApp @Composable get() = get("Application", "Application", "Aplicação")
    val logoOrange @Composable get() = get("Orange", "Orange", "Orange")
    val logoSfr @Composable get() = get("SFR", "SFR", "SFR")
    val logoBouygues @Composable get() = get("Bouygues", "Bouygues", "Bouygues")
    val logoFree @Composable get() = get("Free", "Free", "Free")
    val calcDbSize @Composable get() = get("Calcul de la taille...", "Calculating size...", "A calcular o tamanho...")
    val unknownSize @Composable get() = get("Taille inconnue", "Unknown size", "Tamanho desconhecido")

    val showSpeedometer @Composable get() = get("Compteur de vitesse", "Speedometer", "Velocímetro")
    val siteFreqFiltersTitle @Composable get() = get("Filtres des fréquences de l'antenne", "Antenna frequency filters", "Filtros de fréquence da antena")
    val freqGridDisplayOption @Composable get() = get("Afficher les fréquences en grille", "Display frequencies in a grid", "Exibir frequências em grade")

    val emittersTableTitle @Composable get() = get("Émetteurs", "Emitters", "Emissores")
    val antennasTableTitle @Composable get() = get("Antennes", "Antennas", "Antenas")
    val colTechno @Composable get() = get("Techno.", "Techno.", "Tecno.")
    val colBand @Composable get() = get("Bande", "Band", "Banda")
    val colService @Composable get() = get("Mise en service", "In service", "Em serviço")
    val colState @Composable get() = get("État", "State", "Estado")
    val colAzimuth @Composable get() = get("Azimut", "Azimuth", "Azimute")
    val colHeight @Composable get() = get("Hauteur", "Height", "Altura")
    val colFreqs @Composable get() = get("Fréquences", "Frequencies", "Frequências")

    val shareMapAzimuthsOption @Composable get() = get("Afficher les azimuts", "Show azimuths", "Mostrar azimutes")

    val elevationProfileTitle @Composable get() = get("Profil Altimétrique", "Elevation Profile", "Perfil Altimétrico")
    val elevationProfileButton @Composable get() = get("Profil altimétrique", "Elevation profile", "Perfil altimétrico")
    val elevationProfileLoading @Composable get() = get("Calcul du profil altimétrique...", "Calculating elevation profile...", "A calcular o perfil altimétrico...")
    val elevationProfileCalculationInProgress @Composable get() = get("Analyse du relief et de la zone de Fresnel en cours.", "Terrain and Fresnel zone analysis in progress.", "Análise do relevo e da zona de Fresnel em curso.")
    val elevationProfileNoLocation @Composable get() = get("Position utilisateur indisponible. Activez la localisation puis réessayez.", "User location unavailable. Enable location and try again.", "Localização do utilizador indisponível. Ative a localização e tente novamente.")
    val elevationProfileNoSite @Composable get() = get("Site introuvable pour ce profil.", "Site not found for this profile.", "Site não encontrado para este perfil.")
    val elevationProfileError @Composable get() = get("Impossible de charger le profil altimétrique.", "Unable to load elevation profile.", "Não foi possível carregar o perfil altimétrico.")
    val elevationProfileOfflineTitle @Composable get() = get("Profil altimétrique indisponible hors ligne", "Elevation profile unavailable offline", "Perfil altimétrico indisponível offline")
    val elevationProfileOfflineDetail @Composable get() = get("Le calcul utilise les altitudes IGN et nécessite une connexion internet. Réessayez quand le réseau sera disponible.", "The calculation uses IGN elevation data and requires an internet connection. Try again when the network is available.", "O cálculo usa dados de altitude IGN e requer ligação à internet. Tente novamente quando a rede estiver disponível.")
    val elevationProfileOfflineSaveDetail @Composable get() = get("Le calcul utilise les altitudes IGN et nécessite une connexion internet. Vous pouvez enregistrer temporairement votre position actuelle pour lancer le calcul automatiquement quand le réseau reviendra.", "The calculation uses IGN elevation data and requires an internet connection. You can temporarily save your current position to run the calculation automatically when the network returns.", "O cálculo usa dados de altitude IGN e requer ligação à internet. Pode guardar temporariamente a sua posição atual para iniciar o cálculo automaticamente quando a rede voltar.")
    val elevationProfileSaveForLater @Composable get() = get("Calculer plus tard", "Calculate later", "Calcular mais tarde")
    val elevationProfilePendingSavedTitle @Composable get() = get("Calcul enregistré pour plus tard", "Calculation saved for later", "Cálculo guardado para mais tarde")
    val elevationProfilePendingSavedDetail @Composable get() = get("Votre position actuelle et ce site sont enregistrés temporairement. Le profil altimétrique sera calculé automatiquement dès que la connexion reviendra.", "Your current position and this site are temporarily saved. The elevation profile will be calculated automatically when the connection returns.", "A sua posição atual e este site foram guardados temporariamente. O perfil altimétrico será calculado automaticamente quando a ligação voltar.")
    val elevationProfileCalculatedAt @Composable get() = get("Calculé à", "Calculated at", "Calculado às")
    val elevationProfileUsedGps @Composable get() = get("Coordonnées GPS utilisées", "GPS coordinates used", "Coordenadas GPS utilizadas")
    val elevationProfileSavedDialogTitle @Composable get() = get("Profil enregistré disponible", "Saved profile available", "Perfil guardado disponível")
    val elevationProfileSavedDialogMessage @Composable get() = get("Vous êtes hors ligne. Un profil altimétrique calculé précédemment existe pour ce site. Voulez-vous l'afficher ?", "You are offline. A previously calculated elevation profile exists for this site. Do you want to display it?", "Está offline. Existe um perfil altimétrico calculado anteriormente para este site. Quer apresentá-lo?")
    val elevationProfileSavedDialogShow @Composable get() = get("Afficher", "Show", "Mostrar")
    val elevationProfileSavedDialogHide @Composable get() = get("Ne pas afficher", "Do not show", "Não mostrar")
    val elevationProfileDistance @Composable get() = get("Distance", "Distance", "Distância")
    val elevationProfileSupportHeight @Composable get() = get("Hauteur du panneau", "Panel height", "Altura do painel")
    val elevationProfileSupportHeightDetail @Composable get() = get("Hauteur du panneau de la fréquence sélectionnée.", "Panel height for the selected frequency.", "Altura do painel da frequência selecionada.")
    val elevationProfileStartAltitude @Composable get() = get("Hauteur de départ", "Start height", "Altura inicial")
    val elevationProfileStartAltitudeDetail @Composable get() = get("Altitude du point de départ + 1,5 m.", "Start point altitude + 1.5 m.", "Altitude do ponto inicial + 1,5 m.")
    val elevationProfileSiteAltitude @Composable get() = get("Hauteur d'arrivée", "Arrival height", "Altura de chegada")
    val elevationProfileSiteAltitudeDetail @Composable get() = get("Altitude du site + hauteur du panneau.", "Site altitude + panel height.", "Altitude do site + altura do painel.")
    val elevationProfileFrequency @Composable get() = get("Fréquence", "Frequency", "Frequência")
    val elevationProfileDirectLineLabel @Composable get() = get("Trajet direct du signal", "Direct signal path", "Trajeto direto do sinal")
    val elevationProfileFresnelLabel @Composable get() = get("Zone de Fresnel", "Fresnel zone", "Zona de Fresnel")
    val elevationProfileLineClear @Composable get() = get("Le relief ne coupe pas le trajet direct entre vous et le panneau", "Terrain does not cut the direct path between you and the panel", "O relevo não corta o trajeto direto entre si e o painel")
    val elevationProfileLineBlocked @Composable get() = get("Le relief coupe potentiellement le trajet direct", "Terrain may cut the direct path", "O relevo pode cortar o trajeto direto")
    val elevationProfileFresnelClear @Composable get() = get("Fresnel 60 % dégagé", "60% Fresnel clear", "Fresnel 60% livre")
    val elevationProfileFresnelBlocked @Composable get() = get("Fresnel 60 % obstrué", "60% Fresnel obstructed", "Fresnel 60% obstruído")
    val elevationProfileFresnelExplanation @Composable get() = get("La zone de Fresnel est le volume autour du trajet radio. Pour une liaison plus fiable, on cherche généralement à garder au moins 60 % de cette zone sans obstacle : un relief peut donc gêner le signal même s'il ne coupe pas exactement le trajet direct.", "The Fresnel zone is the volume around the radio path. For a more reliable link, at least 60% of this zone is usually kept clear, so terrain can affect the signal even if it does not exactly cut the direct path.", "A zona de Fresnel é o volume em torno do trajeto rádio. Para uma ligação mais fiável, normalmente tenta-se manter pelo menos 60% desta zona livre de obstáculos; por isso, o relevo pode afetar o sinal mesmo sem cortar exatamente o trajeto direto.")
    val elevationProfileIgnSource @Composable get() = get("Source : IGN Géoplateforme - RGE ALTI", "Source: IGN Geoplatform - RGE ALTI", "Fonte: IGN Geoplataforma - RGE ALTI")

    val throughputCalculatorTitle @Composable get() = get("Calculateur de débit", "Throughput Calculator", "Calculadora de débito")
    val throughputCalculatorButton @Composable get() = get("Calculateur de débit", "Throughput calculator", "Calculadora de débito")
    val throughputNoSite @Composable get() = get("Site introuvable pour ce calcul.", "Site not found for this calculation.", "Site não encontrado para este cálculo.")
    val throughputNoBands @Composable get() = get("Aucune bande 4G ou 5G exploitable n'a été détectée pour ce site.", "No usable 4G or 5G band was detected for this site.", "Nenhuma banda 4G ou 5G utilizável foi detetada para este site.")
    val throughputDisclaimer @Composable get() = get("Le résultat est un débit radio théorique : il ne tient pas compte de la distance, du niveau de signal, du SINR, de la charge réseau, du backhaul, ni des limites du téléphone.", "The result is a theoretical radio throughput: it does not account for distance, signal level, SINR, network load, backhaul, or phone limits.", "O resultado é um débito rádio teórico: não considera distância, nível de sinal, SINR, carga da rede, backhaul ou limites do telemóvel.")
    @Composable fun throughputHeaderSite(siteId: String, supportHeightLabel: String?) = get(
        "Site $siteId${supportHeightLabel?.let { " - support $it" } ?: ""}",
        "Site $siteId${supportHeightLabel?.let { " - support $it" } ?: ""}",
        "Site $siteId${supportHeightLabel?.let { " - suporte $it" } ?: ""}"
    )
    val throughputEstimatedRadioTitle @Composable get() = get("Débit radio théorique estimé", "Estimated theoretical radio throughput", "Débito rádio teórico estimado")
    val throughputDownloadLabel @Composable get() = get("Descendant", "Download", "Download")
    val throughputPhoneUploadLabel @Composable get() = get("Montant (téléphone)", "Upload (phone)", "Upload (telemóvel)")
    val throughputSummaryUploadNote @Composable get() = get(
        "Le montant est pondéré côté terminal : puissance plus faible, moins de MIMO et modulation souvent plus basse qu'en descendant.",
        "Upload is weighted for a handset: lower transmit power, less MIMO and usually lower modulation than download.",
        "O upload é ponderado para o telemóvel: menor potência de emissão, menos MIMO e modulação geralmente inferior ao download."
    )
    @Composable fun throughputIncludedBandsCount(included: Int, total: Int) = get(
        "$included bande(s) incluse(s) sur $total",
        "$included band(s) included out of $total",
        "$included banda(s) incluída(s) de $total"
    )
    val throughputEstimatedOptimalDistanceTitle @Composable get() = get("Distance optimale estimée", "Estimated optimal distance", "Distância ótima estimada")
    val throughputConeHeightUnavailable @Composable get() = get(
        "Hauteur de panneau/support indisponible : impossible d'estimer la zone principale du cône.",
        "Panel/support height unavailable: unable to estimate the main cone zone.",
        "Altura do painel/suporte indisponível: não é possível estimar a zona principal do cone."
    )
    @Composable fun throughputMainZoneEstimated(near: String, far: String) = get(
        "Zone principale estimée : $near à $far",
        "Estimated main zone: $near to $far",
        "Zona principal estimada: $near a $far"
    )
    val throughputConeAssumption @Composable get() = get(
        "Hypothèse : hauteur panneau/support, mobile à 1,5 m, tilt vertical typique 4°-8° avec un point nominal à 6°.",
        "Assumption: panel/support height, handset at 1.5 m, typical vertical tilt 4°-8° with a 6° nominal point.",
        "Hipótese: altura do painel/suporte, telemóvel a 1,5 m, tilt vertical típico 4°-8° com ponto nominal a 6°."
    )
    val throughputConeMapExplanation @Composable get() = get(
        "Le cercle marque la distance optimale, les points indiquent les axes de panneau où le signal devrait être le plus fort.",
        "The circle marks the optimal distance; dots show panel axes where signal should be strongest.",
        "O círculo marca a distância ótima; os pontos mostram os eixos de painel onde o sinal deve ser mais forte."
    )
    val throughputRadioAssumption @Composable get() = get("Hypothèse radio", "Radio assumption", "Hipótese rádio")
    val throughputIncludePlanned @Composable get() = get("Inclure les projets", "Include planned", "Incluir projetos")
    val throughputIncludedBandsTitle @Composable get() = get("Bandes incluses", "Included bands", "Bandas incluídas")
    val throughputCustomModulationTitle @Composable get() = get("Modulation personnalisée", "Custom modulation", "Modulação personalizada")
    val throughput4gDownloadLabel @Composable get() = get("4G descendant", "4G download", "4G download")
    val throughput4gUploadLabel @Composable get() = get("4G montant", "4G upload", "4G upload")
    val throughput5gDownloadLabel @Composable get() = get("5G descendant", "5G download", "5G download")
    val throughput5gUploadLabel @Composable get() = get("5G montant", "5G upload", "5G upload")
    val throughputFrequenciesAndModulationTitle @Composable get() = get("Fréquences et modulation", "Frequencies and modulation", "Frequências e modulação")
    val throughputEstimatedSuffix @Composable get() = get("(estimé)", "(estimated)", "(estimado)")
    @Composable fun throughputEstimatedCone(center: String, near: String, far: String) = get(
        "Cône estimé : $center ($near-$far)",
        "Estimated cone: $center ($near-$far)",
        "Cone estimado: $center ($near-$far)"
    )
    val throughputFrequenciesLabel @Composable get() = get("Fréquences", "Frequencies", "Frequências")
    val throughputModulationAndAntennasLabel @Composable get() = get("Modulation et antennes", "Modulation and antennas", "Modulação e antenas")
    val throughputReadAsEstimateTitle @Composable get() = get("À lire comme une estimation", "Read as an estimate", "Ler como uma estimativa")
    @Composable
    fun throughputPresetLabel(presetId: String): String = when (presetId.lowercase()) {
        "conservative", "prudent" -> get("Prudent", "Conservative", "Prudente")
        "ideal", "maximum" -> get("Idéal", "Ideal", "Ideal")
        "custom" -> get("Personnalisé", "Custom", "Personalizado")
        else -> get("Standard", "Standard", "Padrão")
    }
    @Composable
    fun throughputPresetDescription(presetId: String): String = when (presetId.lowercase()) {
        "conservative", "prudent" -> get(
            "Profil prudent : modulation moyenne, UL fortement limité par la puissance du téléphone.",
            "Conservative profile: average modulation, upload strongly limited by handset transmit power.",
            "Perfil prudente: modulação média, upload fortemente limitado pela potência do telemóvel."
        )
        "ideal", "maximum" -> get(
            "Profil idéal : très bonnes conditions radio plausibles, mais l'UL reste plafonné côté terminal.",
            "Ideal profile: plausible very good radio conditions, but upload remains capped on the handset side.",
            "Perfil ideal: condições rádio muito boas e plausíveis, mas o upload continua limitado pelo terminal."
        )
        "custom" -> get(
            "Profil personnalisé : les modulations DL/UL sont réglées manuellement, avec un UL toujours limité comme un téléphone.",
            "Custom profile: DL/UL modulations are manually tuned, with upload still limited like a handset.",
            "Perfil personalizado: as modulações DL/UL são ajustadas manualmente, com upload ainda limitado como um telemóvel."
        )
        else -> get(
            "Profil standard : 4G MIMO 2x2 et 5G MIMO 4x4 en descendant, montant calculé comme un téléphone réel.",
            "Standard profile: 4G MIMO 2x2 and 5G MIMO 4x4 for download, upload calculated like a real handset.",
            "Perfil padrão: 4G MIMO 2x2 e 5G MIMO 4x4 no download, upload calculado como um telemóvel real."
        )
    }
    val shareThroughputTitle @Composable get() = get("Débit théorique", "Theoretical throughput", "Débito teórico")
    val shareThroughputPhoneUploadLabel @Composable get() = get("Montant tél.", "Phone upload", "Upload tel.")
    @Composable fun shareThroughputOptimalDistance(distance: String) = get("Distance optimale : $distance", "Optimal distance: $distance", "Distância ótima: $distance")
    @Composable fun shareThroughputZone(near: String, far: String) = get("Zone : $near à $far", "Zone: $near to $far", "Zona: $near a $far")
    @Composable fun shareThroughputBandsSummary(presetLabel: String, included: Int, total: Int) = get(
        "$presetLabel · $included/$total bande(s)",
        "$presetLabel · $included/$total band(s)",
        "$presetLabel · $included/$total banda(s)"
    )
    val shareThroughputDisclaimer @Composable get() = get(
        "Débit radio estimé : hors charge réseau, signal réel, backhaul et limites exactes du téléphone.",
        "Estimated radio throughput: excludes network load, real signal, backhaul and exact phone limits.",
        "Débito rádio estimado: exclui carga da rede, sinal real, backhaul e limites exatos do telefone."
    )
    @Composable
    fun throughputBlockTitle(blockId: String): String = when (blockId) {
        "header" -> get("En-tête du site", "Site header", "Cabeçalho do site")
        "summary" -> get("Résumé des débits", "Throughput summary", "Resumo dos débitos")
        "cone" -> get("Distance optimale", "Optimal distance", "Distância ideal")
        "controls" -> get("Hypothèses et filtres", "Assumptions and filters", "Hipóteses e filtros")
        "bands" -> throughputFrequenciesAndModulationTitle
        "assumptions" -> get("Sources et avertissements", "Sources and warnings", "Fontes e avisos")
        else -> blockId
    }
    val throughputCalculationSettingsTitle @Composable get() = get("Réglages de calcul", "Calculation settings", "Definições de cálculo")
    val throughputDefaultModeTitle @Composable get() = get("Mode de calcul par défaut", "Default calculation mode", "Modo de cálculo predefinido")
    val throughputInclude4g @Composable get() = get("Inclure la 4G", "Include 4G", "Incluir 4G")
    val throughputInclude5g @Composable get() = get("Inclure la 5G", "Include 5G", "Incluir 5G")
    val throughputDefaultFrequencyBandsTitle @Composable get() = get("Bandes de fréquences par défaut", "Default frequency bands", "Bandas de frequência predefinidas")
    val throughputAttentionTitle @Composable get() = get("Points d'attention", "Important notes", "Pontos de atenção")
    @Composable fun throughputCalculationAssumptions(assumptions: String) = get("Hypothèses de calcul : $assumptions", "Calculation assumptions: $assumptions", "Hipóteses de cálculo: $assumptions")
    @Composable fun throughputSources(summary: String) = get("Sources : $summary", "Sources: $summary", "Fontes: $summary")
    val throughputWarningNetworkUnknown @Composable get() = get(
        THROUGHPUT_WARNING_NETWORK_UNKNOWN,
        "Network load, backhaul and the phone's exact capabilities are not known.",
        "A carga da rede, o backhaul e as capacidades exatas do telemóvel não são conhecidos.",
        "Il carico della rete, il backhaul e le capacità esatte del telefono non sono noti.",
        "Netzlast, Backhaul und die genauen Fähigkeiten des Telefons sind nicht bekannt.",
        "No se conocen la carga de red, el backhaul ni las capacidades exactas del teléfono."
    )
    @Composable fun throughputWarningProfileApplied(profileLabel: String) = get(
        "Le MIMO et la modulation ne sont pas publiés au niveau du site : le profil $profileLabel est donc appliqué.",
        "MIMO and modulation are not published at site level, so the $profileLabel profile is applied.",
        "O MIMO e a modulação não são publicados ao nível do site; por isso, é aplicado o perfil $profileLabel."
    )
    @Composable fun throughputWarningAllocationMissing(bandAndTech: String) = get(
        "Bande $bandAndTech exclue : allocation opérateur introuvable.",
        "Band $bandAndTech excluded: operator allocation not found.",
        "Banda $bandAndTech excluída: alocação da operadora não encontrada."
    )
    @Composable fun throughputWarningDssShared(band: String) = get(
        "Bande $band potentiellement partagée entre la 4G et la 5G : le débit n'est pas additionné intégralement.",
        "Band $band may be shared between 4G and 5G, so its throughput is not fully added.",
        "A banda $band pode ser partilhada entre 4G e 5G; por isso, o débito não é somado integralmente."
    )
    val throughputWarningUplinkAggregation @Composable get() = get(
        THROUGHPUT_WARNING_UPLINK_AGGREGATION,
        "Upload throughput is limited to the two best aggregated frequencies, which is a more realistic assumption for mobile networks in France.",
        "O débito ascendente é limitado às duas melhores frequências agregadas, uma hipótese mais realista para redes móveis em França.",
        "La velocità in upload è limitata alle due migliori frequenze aggregate, un'ipotesi più realistica per le reti mobili in Francia.",
        "Der Upload-Durchsatz ist auf die zwei besten aggregierten Frequenzen begrenzt, eine realistischere Annahme für Mobilfunknetze in Frankreich.",
        "La velocidad de subida se limita a las dos mejores frecuencias agregadas, una hipótesis más realista para las redes móviles en Francia."
    )
    val throughputWarningLowBandAggregation @Composable get() = get(
        THROUGHPUT_WARNING_LOW_BAND_AGGREGATION,
        "4G aggregation between low bands 700/800/900 MHz is limited: many phones do not combine these carriers.",
        "A agregação 4G entre bandas baixas 700/800/900 MHz é limitada: muitos telemóveis não combinam estas portadoras.",
        "L'aggregazione 4G tra bande basse 700/800/900 MHz è limitata: molti telefoni non combinano queste portanti.",
        "Die 4G-Aggregation zwischen den niedrigen Bändern 700/800/900 MHz ist begrenzt: viele Telefone bündeln diese Träger nicht.",
        "La agregación 4G entre bandas bajas 700/800/900 MHz es limitada: muchos teléfonos no combinan esas portadoras."
    )
    val throughputWarningLteAggregationLimit @Composable get() = get(
        THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT,
        "Selected 4G aggregation limit: only the best carriers are counted.",
        "Limite de agregação 4G escolhida: apenas as melhores portadoras são contabilizadas.",
        "Limite di aggregazione 4G selezionato: vengono conteggiate solo le migliori portanti.",
        "Gewählte 4G-Aggregationsgrenze: nur die besten Träger werden gezählt.",
        "Límite de agregación 4G elegido: solo se cuentan las mejores portadoras."
    )
    val throughputWarningNrAggregationLimit @Composable get() = get(
        THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT,
        "Profile 5G aggregation limit: only the best carriers are counted.",
        "Limite de agregação 5G do perfil: apenas as melhores portadoras são contabilizadas.",
        "Limite di aggregazione 5G del profilo: vengono conteggiate solo le migliori portanti.",
        "5G-Aggregationsgrenze des Profils: nur die besten Träger werden gezählt.",
        "Límite de agregación 5G del perfil: solo se cuentan las mejores portadoras."
    )
    val throughputExcludedNoMetropolitanArcepAllocation @Composable get() = get(
        THROUGHPUT_REASON_NO_METROPOLITAN_ARCEP_ALLOCATION,
        "No compatible Arcep allocation for metropolitan France was found for this technology and band.",
        "Não foi encontrada nenhuma alocação Arcep da França metropolitana compatível com esta tecnologia e esta banda.",
        "Nessuna allocazione Arcep della Francia metropolitana compatibile trovata per questa tecnologia e questa banda.",
        "Keine kompatible Arcep-Zuteilung für Metropolitan-Frankreich für diese Technologie und dieses Band gefunden.",
        "No se ha encontrado ninguna asignación Arcep de Francia metropolitana compatible con esta tecnología y esta banda."
    )
    val throughputExcludedDssShared @Composable get() = get(
        THROUGHPUT_REASON_DSS_SHARED,
        "Band potentially shared between 4G and 5G: it is not counted twice.",
        "Banda potencialmente partilhada entre 4G e 5G: não é contabilizada duas vezes.",
        "Banda potenzialmente condivisa tra 4G e 5G: non viene conteggiata due volte.",
        "Potenziell zwischen 4G und 5G geteiltes Band: es wird nicht doppelt gezählt.",
        "Banda potencialmente compartida entre 4G y 5G: no se cuenta dos veces."
    )
    val throughputSourceSummaryEngine @Composable get() = get(
        THROUGHPUT_SOURCE_SUMMARY_ENGINE,
        "ANFR/data.gouv for declared frequencies, Arcep for operator allocations, ETSI/3GPP TS 38.306 and TS 36.306/36.213 for the radio model.",
        "ANFR/data.gouv para as frequências declaradas, Arcep para as alocações das operadoras, ETSI/3GPP TS 38.306 e TS 36.306/36.213 para o modelo rádio.",
        "ANFR/data.gouv per le frequenze dichiarate, Arcep per le allocazioni degli operatori, ETSI/3GPP TS 38.306 e TS 36.306/36.213 per il modello radio.",
        "ANFR/data.gouv für deklarierte Frequenzen, Arcep für Betreiberzuteilungen, ETSI/3GPP TS 38.306 und TS 36.306/36.213 für das Funkmodell.",
        "ANFR/data.gouv para las frecuencias declaradas, Arcep para las asignaciones de operadores, ETSI/3GPP TS 38.306 y TS 36.306/36.213 para el modelo radio."
    )
    val throughputSourceSummaryDefault @Composable get() = get(
        THROUGHPUT_SOURCE_SUMMARY_DEFAULT,
        "ANFR/data.gouv for declared frequencies, Arcep for operator allocations, 3GPP for the radio model.",
        "ANFR/data.gouv para as frequências declaradas, Arcep para as alocações das operadoras, 3GPP para o modelo rádio.",
        "ANFR/data.gouv per le frequenze dichiarate, Arcep per le allocazioni degli operatori, 3GPP per il modello radio.",
        "ANFR/data.gouv für deklarierte Frequenzen, Arcep für Betreiberzuteilungen, 3GPP für das Funkmodell.",
        "ANFR/data.gouv para las frecuencias declaradas, Arcep para las asignaciones de operadores, 3GPP para el modelo radio."
    )
    val throughputProfilePrudentEngineDesc @Composable get() = get(
        THROUGHPUT_PROFILE_PRUDENT_DESC,
        "Conservative profile: 4G 64-QAM downlink, 16-QAM uplink, 5G NR 64-QAM, limited aggregation and DSS not counted twice.",
        "Perfil prudente: 4G 64-QAM em download, 16-QAM em upload, 5G NR 64-QAM, agregação limitada e DSS sem dupla contagem.",
        "Profilo prudente: 4G 64-QAM in download, 16-QAM in upload, 5G NR 64-QAM, aggregazione limitata e DSS non conteggiato due volte.",
        "Vorsichtiges Profil: 4G 64-QAM im Download, 16-QAM im Upload, 5G NR 64-QAM, begrenzte Aggregation und DSS nicht doppelt gezählt.",
        "Perfil prudente: 4G 64-QAM en bajada, 16-QAM en subida, 5G NR 64-QAM, agregación limitada y DSS sin doble conteo."
    )
    val throughputProfileStandardEngineDesc @Composable get() = get(
        THROUGHPUT_PROFILE_STANDARD_DESC,
        "Standard profile: 4G 256-QAM downlink with 2x2 MIMO, 64-QAM phone-side uplink, 5G n78 256-QAM downlink with 4x4 MIMO, 64-QAM uplink on 2 layers, DSS not counted twice.",
        "Perfil padrão: 4G 256-QAM em download com MIMO 2x2, upload 64-QAM no telemóvel, 5G n78 256-QAM em download com MIMO 4x4, upload 64-QAM em 2 camadas, DSS sem dupla contagem."
    )
    val throughputProfileIdealEngineDesc @Composable get() = get(
        THROUGHPUT_PROFILE_IDEAL_DESC,
        "Ideal profile: plausible very good radio conditions, 4G downlink with 4x4 MIMO, 5G NR 256-QAM, more open aggregation and no DSS double counting.",
        "Perfil ideal: condições rádio muito boas e plausíveis, 4G em download com MIMO 4x4, 5G NR 256-QAM, agregação mais aberta e sem dupla contagem DSS."
    )
    val throughputProfileCustomEngineDesc @Composable get() = get(
        THROUGHPUT_PROFILE_CUSTOM_DESC,
        "Custom profile: downlink and uplink modulations chosen in the interface, with upload treated like a phone.",
        "Perfil personalizado: modulações de download e upload escolhidas na interface, com o upload tratado como o de um telemóvel.",
        "Profilo personalizzato: modulazioni di download e upload scelte nell'interfaccia, con upload trattato come quello di un telefono.",
        "Benutzerdefiniertes Profil: Download- und Upload-Modulationen werden in der Oberfläche gewählt, der Upload wird wie bei einem Telefon behandelt.",
        "Perfil personalizado: modulaciones de bajada y subida elegidas en la interfaz, con la subida tratada como la de un teléfono."
    )
    val throughputCustomSignalTitle @Composable get() = get("Signal mesuré", "Measured signal", "Sinal medido")
    val throughputCustomSignalDesc @Composable get() = get(
        "Ces valeurs pondèrent le débit estimé : un bon SINR autorise une modulation plus efficace, alors qu'un RSRP faible dégrade la stabilité.",
        "These values weight the estimate: good SINR allows more efficient modulation, while weak RSRP reduces stability.",
        "Estes valores ponderam a estimativa: um bom SINR permite uma modulação mais eficiente, enquanto um RSRP fraco reduz a estabilidade."
    )
    val throughputCustomEnvironmentTitle @Composable get() = get("Environnement", "Environment", "Ambiente")
    val throughputCustomPositionTitle @Composable get() = get("Position", "Position", "Posição")
    val throughputUseCurrentPosition @Composable get() = get("Utiliser ma position actuelle", "Use my current position", "Usar a minha posição atual")
    val throughputChooseMapPoint @Composable get() = get("Choisir un point sur la carte", "Choose a point on the map", "Escolher um ponto no mapa")
    val throughputClearPosition @Composable get() = get("Retirer la localisation du calcul", "Remove location from calculation", "Remover a localização do cálculo")
    val throughputTapMapToChoose @Composable get() = get(
        "Déplacez/zoomez la mini-carte si besoin, puis touchez le point à analyser.",
        "Move/zoom the mini map if needed, then tap the point to analyze.",
        "Mova/aproxime o minimapa se necessário e toque no ponto a analisar."
    )
    val throughputPositionCurrentApplied @Composable get() = get(
        "Position actuelle utilisée pour l'analyse.",
        "Current position used for the analysis.",
        "Posição atual usada para a análise."
    )
    val throughputPositionMapPointApplied @Composable get() = get(
        "Point choisi sur la carte utilisé pour l'analyse.",
        "Map point used for the analysis.",
        "Ponto escolhido no mapa usado para a análise."
    )
    val throughputPositionCleared @Composable get() = get(
        "Localisation retirée du calcul.",
        "Location removed from the calculation.",
        "Localização removida do cálculo."
    )
    val throughputPositionPermissionDenied @Composable get() = get(
        "Autorisation de localisation refusée : choisis un point sur la carte ou réessaie après l'avoir activée.",
        "Location permission denied: choose a point on the map or try again after enabling it.",
        "Autorização de localização recusada: escolha um ponto no mapa ou tente novamente depois de a ativar."
    )
    val throughputPositionUnavailable @Composable get() = get(
        "Position actuelle indisponible pour le moment.",
        "Current position is unavailable for now.",
        "A posição atual está indisponível neste momento."
    )
    val throughputPositionLocating @Composable get() = get("Recherche de la position...", "Locating...", "A localizar...")
    val throughputPositionNoSelection @Composable get() = get(
        "Aucune position choisie : le calcul garde un coefficient de position neutre.",
        "No position selected: the calculation keeps a neutral position coefficient.",
        "Nenhuma posição escolhida: o cálculo mantém um coeficiente de posição neutro."
    )
    @Composable fun throughputCustomSelectedPosition(label: String) = get(
        "Position choisie : $label",
        "Selected position: $label",
        "Posição escolhida: $label"
    )
    @Composable fun throughputPositionDistance(distance: String) = get(
        "Distance au site : $distance",
        "Distance to site: $distance",
        "Distância ao site: $distance"
    )
    @Composable fun throughputPositionAzimuthInside(azimuth: String, delta: Int) = get(
        "Dans l'azimut d'un panneau ($azimuth, écart $delta°).",
        "Inside a panel azimuth ($azimuth, $delta° offset).",
        "Dentro do azimute de um painel ($azimuth, desvio $delta°)."
    )
    @Composable fun throughputPositionAzimuthOutside(azimuth: String, delta: Int) = get(
        "Hors faisceau : panneau le plus proche $azimuth, écart $delta°.",
        "Outside beam: closest panel $azimuth, $delta° offset.",
        "Fora do feixe: painel mais próximo $azimuth, desvio $delta°."
    )
    val throughputPositionAzimuthUnknown @Composable get() = get(
        "Azimut panneau indisponible : seul le cône de distance est exploitable.",
        "Panel azimuth unavailable: only the distance cone can be used.",
        "Azimute do painel indisponível: apenas o cone de distância pode ser usado."
    )
    @Composable fun throughputPositionCone(center: String, near: String, far: String) = get(
        "Cône radio estimé : point nominal $center, zone utile $near-$far d'après la hauteur panneau/support.",
        "Estimated radio cone: nominal point $center, useful zone $near-$far from panel/support height.",
        "Cone rádio estimado: ponto nominal $center, zona útil $near-$far a partir da altura do painel/suporte."
    )
    val throughputPositionConeUnavailable @Composable get() = get(
        "Cône radio indisponible : hauteur panneau/support manquante.",
        "Radio cone unavailable: missing panel/support height.",
        "Cone rádio indisponível: altura do painel/suporte em falta."
    )
    val throughputCustomTerminalTitle @Composable get() = get("Réseau et agrégation", "Network and aggregation", "Rede e agregação")
    val throughputCustomTerminalDesc @Composable get() = get(
        "Ces choix ajustent la charge cellule, la qualité du lien de collecte et le nombre de porteuses 4G comptées.",
        "These choices adjust cell load, backhaul quality and the number of counted 4G carriers.",
        "Estas opções ajustam a carga da célula, a qualidade do backhaul e o número de portadoras 4G contabilizadas."
    )
    val throughputCustomNetworkLoadTitle @Composable get() = get("Charge réseau", "Network load", "Carga da rede")
    val throughputCustomBackhaulTitle @Composable get() = get("Backhaul", "Backhaul", "Backhaul")
    val throughputCustomAggregationTitle @Composable get() = get("Agrégation 4G", "4G aggregation", "Agregação 4G")
    @Composable fun throughputCustomImpact(ltePercent: Int, nrPercent: Int) = get(
        "Impact estimé : 4G ${ltePercent}% / 5G ${nrPercent}%",
        "Estimated impact: 4G ${ltePercent}% / 5G ${nrPercent}%",
        "Impacto estimado: 4G ${ltePercent}% / 5G ${nrPercent}%"
    )
    @Composable
    fun throughputEnvironmentLabel(id: String): String = when (id) {
        "outdoor" -> get("Extérieur", "Outdoor", "Exterior")
        "vehicle" -> get("Voiture", "Vehicle", "Carro")
        "indoor" -> get("Intérieur", "Indoor", "Interior")
        "deep_indoor" -> get("Intérieur profond", "Deep indoor", "Interior profundo")
        else -> id
    }
    @Composable
    fun throughputPositionScenarioLabel(id: String): String = when (id) {
        "unknown" -> get("Non renseignée", "Unknown", "Desconhecida")
        "in_cone" -> get("Dans le cône", "In the beam", "No feixe")
        "too_close" -> get("Trop proche", "Too close", "Demasiado perto")
        "too_far" -> get("Trop loin", "Too far", "Demasiado longe")
        "outside_beam" -> get("Hors azimut", "Outside beam", "Fora do azimute")
        else -> id
    }
    @Composable
    fun throughputNetworkLoadLabel(id: String): String = when (id) {
        "unknown" -> get("Non renseignée", "Unknown", "Desconhecida")
        "light" -> get("Faible", "Light", "Baixa")
        "medium" -> get("Moyenne", "Medium", "Média")
        "heavy" -> get("Forte", "Heavy", "Alta")
        "saturated" -> get("Saturée", "Saturated", "Saturada")
        else -> id
    }
    @Composable
    fun throughputBackhaulLabel(id: String): String = when (id) {
        "unknown" -> get("Non renseigné", "Unknown", "Desconhecido")
        "fiber" -> get("Fibre / très bon", "Fiber / very good", "Fibra / muito bom")
        "radio" -> get("Faisceau hertzien", "Microwave link", "Feixe hertziano")
        "limited" -> get("Limité", "Limited", "Limitado")
        else -> id
    }
    @Composable
    fun throughputLteAggregationLabel(id: String): String = when (id) {
        "single" -> get("1 porteuse", "1 carrier", "1 portadora")
        "realistic" -> get("Réaliste", "Realistic", "Realista")
        "wide" -> get("Large", "Wide", "Ampla")
        else -> id
    }
    val throughputCustomExplanationTitle @Composable get() = get("Impact sur le calcul", "Impact on the calculation", "Impacto no cálculo")
    val throughputCustomExplanationModulationTitle @Composable get() = get("Modulation", "Modulation", "Modulação")
    val throughputCustomExplanationModulationDesc @Composable get() = get(
        "Le QAM choisi fixe le débit radio brut : plus la modulation est élevée, plus le débit théorique monte, mais elle suppose un meilleur signal.",
        "The selected QAM sets the raw radio throughput: higher modulation increases theoretical speed but assumes a better signal.",
        "O QAM escolhido define o débito rádio bruto: uma modulação mais alta aumenta o débito teórico, mas pressupõe melhor sinal."
    )
    @Composable fun throughputCustomExplanationSignalDesc(ltePercent: Int, nrPercent: Int) = get(
        "Le RSRP compte pour 40 % du score radio et le SNR/SINR pour 60 %. Le coefficient actuel donne 4G $ltePercent % / 5G $nrPercent %.",
        "RSRP accounts for 40% of the radio score and SNR/SINR for 60%. The current coefficient gives 4G $ltePercent% / 5G $nrPercent%.",
        "O RSRP conta 40% da pontuação rádio e o SNR/SINR 60%. O coeficiente atual dá 4G $ltePercent% / 5G $nrPercent%."
    )
    val throughputCustomExplanationEnvironmentDesc @Composable get() = get(
        "L'environnement multiplie le résultat : extérieur 100 %, voiture 85 %, intérieur 65 %, intérieur profond 45 %.",
        "Environment multiplies the result: outdoor 100%, vehicle 85%, indoor 65%, deep indoor 45%.",
        "O ambiente multiplica o resultado: exterior 100%, carro 85%, interior 65%, interior profundo 45%."
    )
    val throughputCustomExplanationPositionDesc @Composable get() = get(
        "La position ajuste le débit selon la zone radio : dans le cône 106 %, trop proche 75 %, trop loin 68 %, hors azimut 45 %.",
        "Position adjusts throughput by radio zone: in the beam 106%, too close 75%, too far 68%, outside azimuth 45%.",
        "A posição ajusta o débito pela zona rádio: no feixe 106%, demasiado perto 75%, demasiado longe 68%, fora do azimute 45%."
    )
    val throughputCustomExplanationNetworkLoadDesc @Composable get() = get(
        "La charge réseau réduit le débit disponible par utilisateur : inconnue 100 %, faible 90 %, moyenne 68 %, forte 46 %, saturée 28 % en descendant.",
        "Network load reduces per-user throughput: unknown 100%, light 90%, medium 68%, heavy 46%, saturated 28% on download.",
        "A carga da rede reduz o débito por utilizador: desconhecida 100%, baixa 90%, média 68%, alta 46%, saturada 28% em download."
    )
    val throughputCustomExplanationBackhaulDesc @Composable get() = get(
        "Le backhaul représente le lien derrière l'antenne : fibre 100 %, faisceau hertzien 84 %, limité 55 % en descendant.",
        "Backhaul represents the link behind the antenna: fiber 100%, microwave link 84%, limited 55% on download.",
        "O backhaul representa a ligação por trás da antena: fibra 100%, feixe hertziano 84%, limitado 55% em download."
    )
    @Composable fun throughputCustomExplanationAggregationDesc(maxLteCarriers: Int) = get(
        "Le total garde les $maxLteCarriers meilleure(s) porteuse(s) 4G selon le profil. Les bandes basses 700/800/900 MHz ne sont pas additionnées entre elles.",
        "The total keeps the best $maxLteCarriers 4G carrier(s) for the profile. Low bands 700/800/900 MHz are not added together.",
        "O total mantém as $maxLteCarriers melhor(es) portadora(s) 4G do perfil. As bandas baixas 700/800/900 MHz não são somadas entre si."
    )

    @Composable
    fun translateThroughputWarning(warning: String): String {
        return when {
            warning == THROUGHPUT_WARNING_NETWORK_UNKNOWN -> throughputWarningNetworkUnknown
            warning.startsWith(THROUGHPUT_WARNING_PROFILE_PREFIX) && warning.endsWith(THROUGHPUT_WARNING_PROFILE_SUFFIX) -> {
                val rawLabel = warning.removePrefix(THROUGHPUT_WARNING_PROFILE_PREFIX).removeSuffix(THROUGHPUT_WARNING_PROFILE_SUFFIX)
                throughputWarningProfileApplied(translateThroughputProfileLabel(rawLabel))
            }
            warning.startsWith(THROUGHPUT_WARNING_ALLOCATION_PREFIX) && warning.endsWith(THROUGHPUT_WARNING_ALLOCATION_SUFFIX) -> {
                throughputWarningAllocationMissing(warning.removePrefix(THROUGHPUT_WARNING_ALLOCATION_PREFIX).removeSuffix(THROUGHPUT_WARNING_ALLOCATION_SUFFIX))
            }
            warning.startsWith(THROUGHPUT_WARNING_DSS_PREFIX) && warning.endsWith(THROUGHPUT_WARNING_DSS_SUFFIX) -> {
                throughputWarningDssShared(warning.removePrefix(THROUGHPUT_WARNING_DSS_PREFIX).removeSuffix(THROUGHPUT_WARNING_DSS_SUFFIX))
            }
            warning == THROUGHPUT_WARNING_UPLINK_AGGREGATION -> throughputWarningUplinkAggregation
            warning == THROUGHPUT_WARNING_LOW_BAND_AGGREGATION -> throughputWarningLowBandAggregation
            warning == THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT -> throughputWarningLteAggregationLimit
            warning == THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT -> throughputWarningNrAggregationLimit
            else -> warning
        }
    }

    @Composable
    fun translateThroughputAssumption(assumption: String): String = when (assumption) {
        THROUGHPUT_PROFILE_PRUDENT_DESC -> throughputProfilePrudentEngineDesc
        THROUGHPUT_PROFILE_STANDARD_DESC -> throughputProfileStandardEngineDesc
        THROUGHPUT_PROFILE_IDEAL_DESC -> throughputProfileIdealEngineDesc
        THROUGHPUT_PROFILE_CUSTOM_DESC -> throughputProfileCustomEngineDesc
        THROUGHPUT_PROFILE_CUSTOM_SHORT_DESC -> throughputProfileCustomEngineDesc
        else -> assumption
    }

    @Composable
    fun translateThroughputExcludedReason(reason: String): String = when (reason) {
        THROUGHPUT_REASON_5G_DISABLED -> get("5G désactivée", "5G disabled", "5G desativado", "5G disattivato", "5G deaktiviert", "5G desactivada")
        THROUGHPUT_REASON_4G_DISABLED -> get("4G désactivée", "4G disabled", "4G desativado", "4G disattivato", "4G deaktiviert", "4G desactivada")
        THROUGHPUT_REASON_BAND_EXCLUDED -> get("Bande exclue", "Band excluded", "Banda excluída", "Banda esclusa", "Band ausgeschlossen", "Banda excluida")
        THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED -> get("Opérateur non reconnu", "Operator not recognized", "Operadora não reconhecida", "Operatore non riconosciuto", "Betreiber nicht erkannt", "Operador no reconocido")
        THROUGHPUT_REASON_OPERATOR_NOT_RECOGNIZED_ARCEP -> get("Opérateur non reconnu pour les allocations Arcep", "Operator not recognized for Arcep allocations", "Operadora não reconhecida nas alocações Arcep", "Operatore non riconosciuto per le allocazioni Arcep", "Betreiber für Arcep-Zuteilungen nicht erkannt", "Operador no reconocido para las asignaciones Arcep")
        THROUGHPUT_REASON_ARCEP_ALLOCATION_NOT_FOUND -> get("Allocation Arcep introuvable", "Arcep allocation not found", "Alocação Arcep não encontrada", "Allocazione Arcep non trovata", "Arcep-Zuteilung nicht gefunden", "Asignación Arcep no encontrada")
        THROUGHPUT_REASON_ALLOCATION_NOT_FOUND -> get("Allocation introuvable", "Allocation not found", "Alocação não encontrada", "Allocazione non trovata", "Zuteilung nicht gefunden", "Asignación no encontrada")
        THROUGHPUT_REASON_PLANNED_BAND -> get("Bande en projet", "Planned band", "Banda em projeto", "Banda pianificata", "Geplantes Band", "Banda planificada")
        THROUGHPUT_REASON_NO_METROPOLITAN_ARCEP_ALLOCATION -> throughputExcludedNoMetropolitanArcepAllocation
        THROUGHPUT_REASON_DSS_SHARED -> throughputExcludedDssShared
        THROUGHPUT_WARNING_LOW_BAND_AGGREGATION -> throughputWarningLowBandAggregation
        THROUGHPUT_WARNING_LTE_AGGREGATION_LIMIT -> throughputWarningLteAggregationLimit
        THROUGHPUT_WARNING_NR_AGGREGATION_LIMIT -> throughputWarningNrAggregationLimit
        else -> reason
    }

    @Composable
    fun translateThroughputSourceSummary(summary: String): String = when (summary) {
        THROUGHPUT_SOURCE_SUMMARY_ENGINE -> throughputSourceSummaryEngine
        THROUGHPUT_SOURCE_SUMMARY_DEFAULT -> throughputSourceSummaryDefault
        else -> summary
    }

    @Composable
    private fun translateThroughputProfileLabel(label: String): String = when (label) {
        "Prudent" -> get("prudent", "conservative", "prudente", "prudente", "vorsichtig", "prudente")
        "Standard" -> get("standard", "standard", "padrão", "standard", "Standard", "estándar")
        THROUGHPUT_PROFILE_IDEAL_LABEL -> get("idéal", "ideal", "ideal", "ideale", "ideal", "ideal")
        THROUGHPUT_PROFILE_CUSTOM_LABEL -> get("personnalisé", "custom", "personalizado", "personalizzato", "benutzerdefiniert", "personalizado")
        else -> label
    }

    val liveNotificationTitle @Composable get() = get("Notification Live", "Live Notification", "Notificação au vivo")
    val liveNotificationDesc @Composable get() = get("Activez les notifications en temps réel pour être informé de l'antenne de votre opérateur par défaut la plus proche de vous", "Enable real-time notifications", "Ativar notifications em tempo real")
    val updateNotifSettingTitle @Composable get() = get("Notifications de mise à jour", "Update notifications", "Notificações de atualização")
    val updateNotifSettingDesc @Composable get() = get("Etre alerte quand une nouvelle base de donnees ou une nouvelle APK est disponible", "Get alerted when a new database or APK is available", "Ser alertado quando uma nova base de dados ou APK estiver disponivel")
    val liveNotificationRequiresOp @Composable get() = get("Nécessite de choisir un opérateur par défaut", "Requires choosing a default operator", "Requer a escolha de uma operadora padrão")

    // ✅ NOUVEAU : Messages d'avertissement pour les filtres
    val minOneTechnoWarning @Composable get() = get("Vous devez garder au moins une technologie mobile (2G, 3G, 4G ou 5G).", "You must keep at least one mobile technology (2G, 3G, 4G, or 5G).", "Deve manter pelo menos uma tecnologia móvel (2G, 3G, 4G ou 5G).")
    val minOneFreqWarning @Composable get() = get("Vous devez garder au moins une fréquence.", "You must keep at least one frequency.", "Deve manter pelo menos uma fréquence.")
    val anfrDatabaseFrom @Composable get() = get("Données hebdomadaires actuellement téléchargées :", "Weekly data currently downloaded:", "Dados semanais atualmente transferidos:")
    val noDatabaseInstalled @Composable get() = get("Aucune base installée", "No database installed", "Nenhuma base instalada")
    val invalidLocalDatabase @Composable get() = get("Base locale invalide", "Invalid local database", "Base local inválida")
    val oldUndatedDatabase @Composable get() = get("Ancienne version (Non datée)", "Old version (Undated)", "Versão antiga (Sem data)")
    val latestDatabaseAvailable @Composable get() = get("Dernière base disponible :", "Latest database:", "Última base disp.:")
    val currentlyDownloadedDatabase @Composable get() = get("Base actuellement téléchargée :", "Currently downloaded:", "Base atualmente transferida:")

    val deleteData @Composable get() = get("Supprimer les données", "Delete data", "Eliminar dados")
    val deleteDbWarningTitle @Composable get() = get("Attention", "Warning", "Atenção")
    val deleteDbWarningDesc @Composable get() = get("Êtes-vous sûr de vouloir supprimer la base de données ?", "Are you sure you want to delete the database?", "Tem certeza de que deseja excluir a base de données?")

    val offlineMapsTitle @Composable get() = get("Cartes Hors-Ligne", "Offline Maps", "Mapas Offline")
    val offlineMapsDesc @Composable get() = get("Téléchargez des cartes de la France pour naviguer sans réseau.", "Download maps of France to navigate without an internet connection.", "Descarregue mapas de França para navegar sem rede.")
    val mapDeleteWarningTitle @Composable get() = get("Supprimer la carte ?", "Delete map?", "Eliminar mapa?")
    val mapDeleteWarningDesc @Composable get() = get("Voulez-vous vraiment supprimer cette carte de votre appareil ?", "Do you really want to delete this map from your device?", "Tem a certeza de que pretende eliminar este mapa do seu dispositivo?")
    val downloadAll @Composable get() = get("Tout télécharger", "Download All", "Descarregar tudo")
    val deleteAllMaps @Composable get() = get("Tout supprimer", "Delete All", "Eliminar tudo")
    val deleteAllMapsWarningTitle @Composable get() = get("Supprimer toutes les cartes ?", "Delete all maps?", "Eliminar todos os mapas?")
    val deleteAllMapsWarningDesc @Composable get() = get("Voulez-vous vraiment supprimer toutes les cartes téléchargées ?", "Do you really want to delete all downloaded maps?", "Tem a certeza de que pretende eliminar todos os mapas descarregados?")

    val splitShareImage @Composable get() = get("Scinder l'image de partage", "Split share image", "Dividir a imagem de partilha")
    val splitShareImageDesc @Composable get() = get("Sépare les fréquences sur une 2ème image", "Separates frequencies on a 2nd image", "Separa as frequências numa 2ª imagem")
    // ==========================================
    // 📏 UNITÉS DE MESURE
    // ==========================================
    val unitSettingsTitle @Composable get() = get("Unités de mesure", "Units of measure", "Unidades de medida")
    val unitSettingsDesc @Composable get() = get("Distance et vitesse", "Distance and speed", "Distância e velocidade")
    val speedLabel @Composable get() = get("Vitesse :", "Speed :", "Velocidade :")
    val unitKm @Composable get() = get("Kilomètres (km)", "Kilometers (km)", "Quilômetros (km)")
    val unitMi @Composable get() = get("Miles (mi)", "Miles (mi)", "Milhas (mi)")
    val unitKmh @Composable get() = get("Kilomètres/heure (km/h)", "Kilometers/hour (km/h)", "Quilômetros/hora (km/h)")
    val unitMph @Composable get() = get("Miles/heure (mph)", "Miles/hour (mph)", "Milhas/hora (mph)")

    @Composable
    fun dbSizeWarning(size: Double): String {
        val formattedSize = String.format(java.util.Locale.US, "%.1f", size)
        return get(
            "Taille du fichier : $formattedSize Mo\nLe téléchargement est recommandé en Wi-Fi.",
            "File size: $formattedSize MB\nWi-Fi download is recommended.",
            "Tamanho do ficheiro: $formattedSize MB\nRecomenda-se a transferência por Wi-Fi."
        )
    }

    // ==========================================
    // 📡 ANTENNES À PROXIMITÉ
    // ==========================================
    val nearEmittersTitle @Composable get() = get("Émetteurs à proximité", "Nearby Emitters", "Emissores próximos")
    @Composable
    fun sitesFound(count: Int) = get("$count sites trouvés", "$count sites found", "$count locais encontrados")
    val searchGps @Composable get() = get("Recherche position GPS...", "Searching for GPS position...", "À procura de posição GPS...")
    val noSitesFound @Composable get() = get("Aucun site trouvé.", "No sites found.", "Nenhum local encontrado.")
    val loadMoreSites @Composable get() = get("Afficher plus de sites", "Load more sites", "Carregar mais sites")
    val nearbySearchSuggestionCity @Composable get() = get("Ville", "City", "Cidade")
    val nearbySearchSuggestionPylon @Composable get() = get("Pylône", "Pylon", "Poste")
    val nearbySearchSuggestionRoof @Composable get() = get("Toit", "Roof", "Telhado")
    val nearbySearchSuggestionPostalCode @Composable get() = get("Code postal", "Postal code", "Código postal")
    val nearbySearchHelpContentDescription @Composable get() = get("Aide recherche", "Search help", "Ajuda da pesquisa")
    val nearbySearchHelpTitle @Composable get() = get("Codes de recherche", "Search codes", "Códigos de pesquisa")
    val nearbySearchHelpOk @Composable get() = get("Compris", "Got it", "Entendi")
    val nearbySearchHelpCityDesc @Composable get() = get("Cherche les sites dans la ville avec la zone Nominatim, comme la carte.", "Searches for sites in the city using the Nominatim area, like the map.", "Procura locais na cidade usando a área do Nominatim, como no mapa.")
    val nearbySearchHelpAddressDesc @Composable get() = get("Cherche dans toute l'adresse ANFR du site.", "Searches the full ANFR address of the site.", "Procura no endereço ANFR completo do local.")
    val nearbySearchHelpPostalDesc @Composable get() = get("Cherche par code postal.", "Searches by postal code.", "Procura por código postal.")
    val nearbySearchHelpGpsDesc @Composable get() = get("Cherche autour de coordonnées GPS.", "Searches around GPS coordinates.", "Procura à volta de coordenadas GPS.")
    val nearbySearchHelpAnfrDesc @Composable get() = get("Cherche un identifiant ANFR.", "Searches for an ANFR identifier.", "Procura um identificador ANFR.")
    val nearbySearchHelpSupportDesc @Composable get() = get("Cherche un identifiant de support.", "Searches for a support identifier.", "Procura um identificador de suporte.")
    val nearbySearchHelpOperatorDesc @Composable get() = get("Filtre par opérateur.", "Filters by operator.", "Filtra por operador.")
    val nearbySearchHelpTechDesc @Composable get() = get("Filtre par technologie.", "Filters by technology.", "Filtra por tecnologia.")
    val nearbySearchHelpTypeDesc @Composable get() = get("Filtre par type de support.", "Filters by support type.", "Filtra por tipo de suporte.")
    val open @Composable get() = get("Ouvrir", "Open", "Abrir")

    val supportDetailsTitle @Composable get() = get("Détails du support", "Support details", "Detalhes do suporte")
    val supportNature @Composable get() = get("Nature du support", "Support nature", "Natureza do suporte")
    val owner @Composable get() = get("Propriétaire", "Owner", "Proprietário")
    val likelyNetworkVendor @Composable get() = get("Équipementier réseau probable", "Likely network vendor", "Fornecedor provável da rede")
    val antennaType @Composable get() = get("Type d'antenne", "Antenna type", "Tipo de antena")

    // ==========================================
    // 📸 PHOTOS COMMUNAUTAIRES (SIGNAL QUEST)
    // ==========================================
    val supportDiagram @Composable get() = get("Schéma du support", "Support diagram", "Esquema do suporte")
    val addPhotos @Composable get() = get("Ajouter des photos", "Add photos", "Adicionar fotos")
    val camera @Composable get() = get("Appareil photo", "Camera", "Câmara")
    val gallery @Composable get() = get("Galerie", "Gallery", "Galeria")
    val externalPhotoFiles @Composable get() = get("Fichiers / disque externe", "Files / external drive", "Ficheiros / disco externo")
    val uploadPhotosPrompt @Composable get() = get("Envoyer des photos", "Upload photos", "Enviar fotos")
    val photoPrepareError @Composable get() = get("Impossible de préparer les photos.", "Could not prepare photos.", "Não foi possível preparar as fotos.")
    val previous @Composable get() = get("Précédent", "Previous", "Anterior")
    val next @Composable get() = get("Suivant", "Next", "Próximo")

    // ==========================================
    // 🧭 BOUSSOLE
    // ==========================================
    val searching @Composable get() = get("Recherche...", "Searching...", "A pesquisar...")
    val unknown @Composable get() = get("Inconnu", "Unknown", "Desconhecido")
    val unknownFeminine @Composable get() = get("Inconnue", "Unknown", "Desconhecida")

    // --- NOUVEAUX AJOUTS ---
    val latShort @Composable get() = get("LAT", "LAT", "LAT")
    val lonShort @Composable get() = get("LONG", "LONG", "LONG")
    val accuracy @Composable get() = get("PRÉCISION", "ACCURACY", "PRECISÃO")
    val nearbyAntennasAzimuth @Composable get() = get("Antennes à proximité", "Nearby antennas", "Antenas nas proximidades")
    val supportPrefix @Composable get() = get("Support", "Support", "Suporte")
    val height @Composable get() = get("Hauteur", "Height", "Altura")
    // ==========================================
    // 🆕 ONBOARDING
    // ==========================================
    val btnAuthorize @Composable get() = get("Autoriser", "Authorize", "Autorizar")
    val btnNext @Composable get() = get("Suivant", "Next", "Seguinte")
    val btnStartConfiguration @Composable get() = get("Commencer la configuration ➜", "Start setup ➜", "Começar configuração ➜")
    val onboardingWelcomeTitle @Composable get() = get("GeoTower", "GeoTower", "GeoTower")
    val onboardingWelcomeDesc @Composable get() = get(
        "Bienvenue sur GeoTower, l'application pour localiser les antennes relais proches de vous et obtenir des informations sur celles ci.",
        "Welcome to GeoTower, the app for locating nearby cell towers and getting information about them.",
        "Bem-vindo ao GeoTower, a aplicação para localizar antenas retransmissoras perto de si e obter informações sobre elas."
    )
    val onboardingWelcomeNearbyTitle @Composable get() = get("Trouver autour de vous", "Find what is nearby", "Encontrar por perto")
    val onboardingWelcomeNearbyDesc @Composable get() = get(
        "Listez rapidement les supports et sites radio les plus proches de votre position.",
        "Quickly list the nearest radio supports and sites around your position.",
        "Liste rapidamente os suportes e sites rádio mais próximos da sua posição."
    )
    val onboardingWelcomeMapTitle @Composable get() = get("Explorer sur la carte", "Explore on the map", "Explorar no mapa")
    val onboardingWelcomeMapDesc @Composable get() = get(
        "Parcourez les antennes, filtres, couches et informations techniques directement sur une carte.",
        "Browse antennas, filters, layers and technical information directly on a map.",
        "Explore antenas, filtros, camadas e informações técnicas diretamente no mapa."
    )
    val onboardingWelcomeToolsTitle @Composable get() = get("Comprendre le réseau", "Understand the network", "Compreender a rede")
    val onboardingWelcomeToolsDesc @Composable get() = get(
        "Accédez aux fréquences, azimuts, photos, profils et outils de mesure quand les données sont disponibles.",
        "Access frequencies, azimuths, photos, profiles and measurement tools when data is available.",
        "Aceda a frequências, azimutes, fotos, perfis e ferramentas de medição quando os dados estiverem disponíveis."
    )
    val onboardingLocationTitle @Composable get() = get("Localisation", "Location", "Localização")
    val onboardingLocationDesc @Composable get() = get(
        "Veuillez accepter l'autorisation de localisation pour pouvoir localiser rapidement les émetteurs proches de vous.",
        "Please allow location permission so GeoTower can quickly locate nearby transmitters.",
        "Aceite a autorização de localização para poder localizar rapidamente os emissores perto de si."
    )
    val onboardingLocationNearbyTitle @Composable get() = get("À proximité en un geste", "Nearby in one tap", "Por perto num toque")
    val onboardingLocationNearbyDesc @Composable get() = get(
        "Votre position sert à trier les antennes autour de vous et à ouvrir directement la bonne zone.",
        "Your position is used to sort antennas around you and open the right area immediately.",
        "A sua posição serve para ordenar as antenas à sua volta e abrir diretamente a zona certa."
    )
    val onboardingLocationMapTitle @Composable get() = get("Carte plus précise", "More precise map", "Mapa mais preciso")
    val onboardingLocationMapDesc @Composable get() = get(
        "Le bouton de localisation pourra centrer la carte, puis suivre vos déplacements si vous l'activez.",
        "The location button can center the map, then follow your movement when you enable it.",
        "O botão de localização poderá centrar o mapa e depois seguir os seus movimentos quando o ativar."
    )
    val onboardingLocationPrivacyTitle @Composable get() = get("Vous gardez le contrôle", "You stay in control", "Mantém o controlo")
    val onboardingLocationPrivacyDesc @Composable get() = get(
        "Vous pourrez continuer sans autorisation et la modifier plus tard depuis les réglages Android.",
        "You can continue without permission and change it later from Android settings.",
        "Pode continuar sem autorização e alterá-la mais tarde nas definições do Android."
    )
    val onboardingNotificationsTitle @Composable get() = get("Notifications", "Notifications", "Notificações")
    val onboardingNotificationsDesc @Composable get() = get(
        "Veuillez accepter l'autorisation pour les notifications afin d'afficher les notifications de téléchargement de base de données, de carte hors ligne, ou encore de la disponibilité de nouvelle base de données.",
        "Please allow notification permission to show notifications for database downloads, offline map downloads, and new database availability.",
        "Aceite a autorização de notificações para apresentar notificações de transferência da base de dados, de mapas offline ou da disponibilidade de uma nova base de dados."
    )
    val onboardingNotificationsDownloadTitle @Composable get() = get("Téléchargements visibles", "Visible downloads", "Transferências visíveis")
    val onboardingNotificationsDownloadDesc @Composable get() = get(
        "GeoTower pourra afficher l'avancement des bases de données et cartes hors ligne pendant leur téléchargement.",
        "GeoTower can show progress for database and offline map downloads while they are running.",
        "O GeoTower poderá mostrar o progresso das bases de dados e dos mapas offline durante a transferência."
    )
    val onboardingNotificationsUpdateTitle @Composable get() = get("Données à jour", "Up-to-date data", "Dados atualizados")
    val onboardingNotificationsUpdateDesc @Composable get() = get(
        "Vous pourrez être prévenu quand une nouvelle base est disponible, sans ouvrir l'application pour vérifier.",
        "You can be notified when a new database is available without opening the app to check.",
        "Poderá ser notificado quando uma nova base de dados estiver disponível sem abrir a aplicação para verificar."
    )
    val onboardingNotificationsControlTitle @Composable get() = get("Alertes utiles seulement", "Useful alerts only", "Apenas alertas úteis")
    val onboardingNotificationsControlDesc @Composable get() = get(
        "Aucune notification sociale ou publicité : uniquement les opérations longues et les données importantes.",
        "No social notifications or ads: only long-running operations and important data updates.",
        "Sem notificações sociais nem publicidade: apenas operações demoradas e dados importantes."
    )
    val onboardingLiveNotificationsTitle @Composable get() = get("Notifications live", "Live notifications", "Notificações live")
    val onboardingLiveNotificationsDesc @Composable get() = get(
        "Activez un suivi discret pour garder l'antenne la plus proche de votre opérateur visible en notification.",
        "Enable a discreet live tracker to keep the nearest antenna for your operator visible in a notification.",
        "Ative um acompanhamento discreto para manter a antena mais próxima da sua operadora visível numa notificação."
    )
    val onboardingLiveNotificationsOperatorTitle @Composable get() = get("Lié à votre opérateur", "Linked to your operator", "Ligado à sua operadora")
    val onboardingLiveNotificationsOperatorDesc @Composable get() = get(
        "Le suivi live utilise l'opérateur par défaut choisi juste avant pour afficher une information pertinente.",
        "Live tracking uses the default operator you just chose to show relevant information.",
        "O acompanhamento live usa a operadora padrão escolhida antes para mostrar informação relevante."
    )
    val onboardingLiveNotificationsNearestTitle @Composable get() = get("Antenne proche en direct", "Nearest antenna live", "Antena próxima em direto")
    val onboardingLiveNotificationsNearestDesc @Composable get() = get(
        "La notification peut se mettre à jour pendant vos déplacements pour indiquer le site le plus proche.",
        "The notification can update while you move to show the nearest site.",
        "A notificação pode atualizar-se durante as suas deslocações para indicar o local mais próximo."
    )
    val onboardingLiveNotificationsControlTitle @Composable get() = get("Toujours désactivable", "Always optional", "Sempre opcional")
    val onboardingLiveNotificationsControlDesc @Composable get() = get(
        "Vous pourrez couper les notifications live à tout moment depuis les paramètres de GeoTower.",
        "You can turn live notifications off at any time from GeoTower settings.",
        "Pode desativar as notificações live a qualquer momento nas definições do GeoTower."
    )
    val onboardingLiveNotificationsSelectedOperator @Composable get() = get("Opérateur sélectionné", "Selected operator", "Operadora selecionada")
    val onboardingLocationDisabledTitle @Composable get() = get("Autorisation de localisation refusée", "Location permission denied", "Autorização de localização recusada")
    val onboardingLocationDisabledDesc @Composable get() = get(
        "L'autorisation de localisation a été refusée. Certaines fonctionnalités seront limitées, comme la recherche autour de vous, le recentrage de la carte et le suivi live.",
        "Location permission was denied. Some features will be limited, such as nearby search, map recentering, and live tracking.",
        "A autorização de localização foi recusada. Algumas funcionalidades ficarão limitadas, como a pesquisa perto de si, o recentramento do mapa e o acompanhamento live."
    )
    val onboardingNotificationsDisabledTitle @Composable get() = get("Autorisation de notifications refusée", "Notification permission denied", "Autorização de notificações recusada")
    val onboardingNotificationsDisabledDesc @Composable get() = get(
        "L'autorisation de notifications a été refusée. Certaines fonctionnalités seront limitées, comme le suivi des téléchargements, les alertes de mise à jour et les notifications live.",
        "Notification permission was denied. Some features will be limited, such as download progress, update alerts, and live notifications.",
        "A autorização de notificações foi recusada. Algumas funcionalidades ficarão limitadas, como o progresso das transferências, os alertas de atualização e as notificações live."
    )
    val retry @Composable get() = get("Réessayer", "Try again", "Tentar novamente")
    val permissionContinueAnyway @Composable get() = get("Continuer tout de même", "Continue anyway", "Continuar mesmo assim")
    val btnLetsGo @Composable get() = get("C'est parti !", "Let's go!", "Vamos lá!")

    val themeDesc @Composable get() = get("Choisissez le style qui vous convient.", "Choose the style that suits you.", "Escolha o estilo que mais lhe convém.")
    val oledTitle @Composable get() = get("Mode OLED (Noir Pur)", "OLED Mode (Pure Black)", "Modo OLED (Preto Puro)")
    val oledSubtitle @Composable get() = get("Économise de la batterie", "Saves battery", "Poupa batterie")
    val blurTitle @Composable get() = get("Flou de défilement", "Scroll Blur", "Desfoque de rolagem")
    val blurSubtitle @Composable get() = get("Activer ou désactiver le flou (plus énergivore)", "Enable or disable blur (consumes more battery)", "Ativar ou desativar o desfoque (consome mais bateria)")

    val mapDesc @Composable get() = get("Quel fournisseur de carte préférez-vous ?", "Which map provider do you prefer?", "Qual fornecedor de mapas prefere?")

    val prefDesc @Composable get() = get("Configurez votre opérateur principal pour faciliter l'utilisation des outils de mesure sur la carte.", "Configure your main operator to make it easier to use the measurement tools on the map.", "Configure a sua operadora principal para facilitar a utilização das ferramentas de medição no mapa.")
    val selectOperator @Composable get() = get("Sélection de votre opérateur principal", "Select your main operator", "Selecione a sua operadora principal")
    val oneUiSubtitle @Composable get() = get("Utiliser l'affichage One UI (bulles arrondies)", "Use One UI display (rounded bubbles)", "Usar o visual One UI (bolhas arredondadas)")

    val warningNoOpTitle @Composable get() = get("Aucun opérateur sélectionné", "No operator selected", "Nenhuma operadora selecionada")
    val warningNoOpDesc @Composable get() = get("Vous n'avez pas choisi d'opérateur par défaut. Les outils de filtrage sur la carte seront désactivés.\n\nVoulez-vous vraiment continuer ?", "You have not chosen a default operator. The filtering tools on the map will be disabled.\n\nDo you really want to continue?", "Não escolheu uma operadora por defeito. As ferramentas de filtragem no mapa serão desativadas.\n\nTem a certeza de que pretende continuer?")
    val warningNoOpLiveNotificationsLead @Composable get() = get("Les ", "The ", "As ")
    val warningNoOpLiveNotificationsLabel @Composable get() = get("notifications live", "live notifications", "notificações live")
    val warningNoOpLiveNotificationsMiddle @Composable get() = get(" que vous avez activées ", " you enabled ", " que ativou ")
    val warningNoOpLiveNotificationsHighlight @Composable get() = get("vont être désactivées", "will be turned off", "serão desativadas")
    val warningNoOpLiveNotificationsSuffix @Composable get() = get(", car elles nécessitent un opérateur par défaut.", " because they require a default operator.", ", porque exigem uma operadora padrão.")
    val warningContinue @Composable get() = get("Continuer quand même", "Continue anyway", "Continuer mesmo assim")
    val warningChooseOp @Composable get() = get("Choisir un opérateur", "Choose an operator", "Escolher uma operadora")

    // --- Style d'affichage ---
    val displayStyleTitle @Composable get() = get("Style d'affichage", "Display Style", "Estilo de exibição")
    val displayStyleFullScreen @Composable get() = get("Plein écran", "Full screen", "Ecrã inteiro")
    val displayStyleFullScreenDesc @Composable get() = get("Affichage du détail du support et du détail du site individuellement en plein écran", "Display support details and site details individually in full screen", "Exibição de detalhes de suporte e detalhes do site individualmente em tela cheia")
    val displayStyleSplit @Composable get() = get("Fractionné", "Split", "Dividido")
    val displayStyleSplitDesc @Composable get() = get("Affichage fractionné des écrans compatibles : le contexte reste à gauche et le détail s'ouvre à droite.", "Split display for compatible screens: the context stays on the left while details open on the right.", "Exibição dividida para ecrãs compatíveis: o contexto fica à esquerda e os detalhes abrem à direita.")

    // ==========================================
    // 🎉 SUCCÈS TÉLÉCHARGEMENT (ONBOARDING)
    // ==========================================
    val dbSuccessTitle @Composable get() = get("Téléchargement terminé !", "Download finished!", "Transferência concluída!")
    val dbSuccessDesc @Composable get() = get(
        "La base de données hors-ligne a été installée avec succès. L'application est prête à fonctionner à pleine vitesse.",
        "The offline database has been successfully installed. The application is ready to run at full speed.",
        "A base de données offline foi instalada com sucesso. A application est prête à fonctionner à toute a vitesse."
    )
    val btnContinue @Composable get() = get("Continuer", "Continue", "Continuar")

    // ==========================================
    // ℹ️ À PROPOS
    // ==========================================
    val aboutPresentation @Composable get() = get("Présentation", "Presentation", "Apresentação")
    val aboutNew @Composable get() = get("Nouveautés", "What's New", "Novidades")
    @Composable fun aboutNewForVersion(appVersion: String) = get("Nouveautés ($appVersion)", "What's new ($appVersion)", "Novidades ($appVersion)")
    val aboutDownloadNewDatabase @Composable get() = get("Téléchargez la nouvelle base", "Download the new database", "Transfira a nova base")
    val aboutDatabaseNotInstalled @Composable get() = get("Non installée", "Not installed", "Não instalada")
    val aboutSources @Composable get() = get("Sources de données", "Data Sources", "Fontes de données")
    val aboutDev @Composable get() = get("Développement", "Development", "Desenvolvimento")
    val aboutIntro @Composable get() = get("GeoTower vous permet de localiser les antennes relais autour de vous et d'identifier les technologies disponibles.", "GeoTower allows you to locate cell towers around you and identify available technologies.", "A GeoTower permite-lhe localizar torres de celular à sua volta e identificar as tecnologias disponibles.")
    val devCredit @Composable get() = get(
        "Développé par Julien et les contributeurs de GitHub 😉",
        "Developed by Julien and GitHub contributors 😉",
        "Desenvolvido por Julien e colaboradores do GitHub 😉",
        "Sviluppato da Julien e dai contributori GitHub 😉",
        "Entwickelt von Julien und GitHub-Mitwirkenden 😉",
        "Desarrollado por Julien y colaboradores de GitHub 😉"
    )
    val srcAntennas @Composable get() = get("Données Antennes", "Antenna Data", "Dados de Antenas")
    val srcAntennasDesc @Composable get() = get("Agence Nationale des Fréquences (ANFR).\nDonnées issues de Cartoradio (Open Data).", "National Frequency Agency (ANFR).\nData from Cartoradio (Open Data).", "Agência Nacional de Frequências (ANFR).\nDados do Cartoradio (Open Data).")
    val srcIgn @Composable get() = get("Fond de carte IGN", "IGN Basemap", "Mapa base IGN")
    val srcIgnDesc @Composable get() = get("© IGN - Institut National de l'Information Géographique et Forestière.", "© IGN - National Institute of Geographic and Forest Information.", "© IGN - Instituto Nacional de Informação Geográfica e Florestal.")
    val srcOsm @Composable get() = get("Fond de carte OSM", "OSM Basemap", "Mapa base OSM")
    val srcOsmDesc @Composable get() = get("© les contributeurs d'OpenStreetMap.", "© OpenStreetMap contributors.", "© os colaboradores do OpenStreetMap.")
    val srcInspo @Composable get() = get("Inspiration & Sources Externes", "Inspiration & External Sources", "Inspiração e Fontes Externas")
    val srcInspoDesc @Composable get() = get(
        "• © CellularFR développé par Luis Baker\n• © Signal Quest développé par Alexandre Germain\n• © RNC Mobile développé par Cédric\n• © eNB-Analytics développé par Tristan\n• © GeoRadio - L'icône alternative provient de l'application GéoRadio sur iOS développée par Hugo Martin.\n• Concept original basé sur l'application GéoRadio\n• Icône fun dessinée par Johan",
        "• © CellularFR developed by Luis Baker\n• © Signal Quest developed by Alexandre Germain\n• © RNC Mobile developed by Cédric\n• © eNB-Analytics developed by Tristan\n• © GeoRadio - The alternative icon comes from the GéoRadio iOS app developed by Hugo Martin.\n• Original concept based on the GéoRadio app\n• Fun icon designed by Johan",
        "• © CellularFR desenvolvido por Luis Baker\n• © Signal Quest desenvolvido por Alexandre Germain\n• © RNC Mobile desenvolvido por Cédric\n• © eNB-Analytics desenvolvido por Tristan\n• © GeoRadio - O ícone alternativo vem da aplicação GéoRadio para iOS desenvolvida por Hugo Martin.\n• Conceito original baseado na aplicação GéoRadio\n• Ícone divertido desenhado por Johan",
        "• © CellularFR sviluppato da Luis Baker\n• © Signal Quest sviluppato da Alexandre Germain\n• © RNC Mobile sviluppato da Cédric\n• © eNB-Analytics sviluppato da Tristan\n• © GeoRadio - L'icona alternativa proviene dall'app GéoRadio per iOS sviluppata da Hugo Martin.\n• Concetto originale basato sull'app GéoRadio\n• Icona fun disegnata da Johan",
        "• © CellularFR entwickelt von Luis Baker\n• © Signal Quest entwickelt von Alexandre Germain\n• © RNC Mobile entwickelt von Cédric\n• © eNB-Analytics entwickelt von Tristan\n• © GeoRadio - Das alternative Symbol stammt aus der von Hugo Martin entwickelten iOS-App GéoRadio.\n• Ursprüngliches Konzept auf Basis der GéoRadio-App\n• Fun-Symbol von Johan gestaltet",
        "• © CellularFR desarrollado por Luis Baker\n• © Signal Quest desarrollado por Alexandre Germain\n• © RNC Mobile desarrollado por Cédric\n• © eNB-Analytics desarrollado por Tristan\n• © GeoRadio - El icono alternativo proviene de la app GéoRadio para iOS desarrollada por Hugo Martin.\n• Concepto original basado en la app GéoRadio\n• Icono fun diseñado por Johan"
    )

    // ==========================================
    // 🗺️ CRÉDITS CARTES (AboutScreen)
    // ==========================================
    val mapsForgesTitle @Composable get() = get(
        "MapsForges",
        "MapsForges",
        "MapsForges"
    )
    val mapsForgesDesc @Composable get() = get(
        "Cartes vectorielles hors-ligne et thème de rendu (Elevate).",
        "Offline vector maps and render theme (Elevate).",
        "Mapas vetoriais offline e tema de renderização (Elevate)."
    )

    // ==========================================
    // 🔒 CONFIDENTIALITÉ
    // ==========================================
    val privacyCategory @Composable get() = get("Confidentialité", "Privacy", "Privacidade")
    val yourDataTitle @Composable get() = get("Vos données", "Your data", "Os seus données")
    val yourDataDesc @Composable get() = get(
        "GeoTower ne collecte aucune donnée personnelle. Vos réglages et favoris sont stockés uniquement sur votre appareil.",
        "GeoTower does not collect any personal data. Your settings and favorites are stored only on your device.",
        "O GeoTower não recolhe quaisquer données pessoais. As suas definições e favoritos sont guardados apenas no seu dispositivo."
    )

    // ==========================================
    // 🏢 DÉTAIL DU SUPPORT
    // ==========================================
    val supportDetailTitle @Composable get() = get("Détail du support", "Support Detail", "Detalhe do suporte")
    val noDataFound @Composable get() = get("Aucune donnée trouvée.", "No data found.", "Nenhum dado encontrado.")
    val idNumber @Composable get() = get("Numéro d'identification : ", "Identification number : ", "Número de identificação : ")
    val idCopied @Composable get() = get("Numéro d'identification copié", "Identification number copied", "Número de identificação copiado")
    val idUnavailable @Composable get() = get("Numéro indisponible pour le moment", "Number unavailable at the moment", "Número indisponível no momento")
    val addressLabel @Composable get() = get("Adresse : ", "Address : ", "Endereço : ")
    val notSpecified @Composable get() = get("Non spécifiée", "Not specified", "Não especificado")
    val addressCopied @Composable get() = get("Adresse copiée", "Address copied", "Endereço copiado")
    val gpsLabel @Composable get() = get("GPS : ", "GPS : ", "GPS : ")
    val coordsCopied @Composable get() = get("Coordonnées copiées", "Coordinates copied", "Coordonadas copiadas")
    val supportHeight @Composable get() = get("Hauteur du support : ", "Support height : ", "Altura do suporte : ")
    val distanceLabel @Composable get() = get("Distance : ", "Distance : ", "Distância : ")
    val fromMyPosition @Composable get() = get("de vous", "from you", "de si")
    val bearingLabel @Composable get() = get("Cap mesuré depuis l’antenne : ", "Bearing measured from the antenna : ", "Rumo medido a partir da antena : ")
    val openMap @Composable get() = get("Ouvrir la carte", "Open map", "Abrir o mapa")
    val navToSite @Composable get() = get("Naviguer vers ce site", "Navigate to this site", "Navegar para este local")
    val shareSite @Composable get() = get("Partager ce site", "Share this site", "Partilhar este local")
    @Composable
    fun operatorCount(count: Int) = get("↓ Nombre d'opérateurs : $count", "↓ Number of operators : $count", "↓ Número de operadoras : $count")
    val shareAs @Composable get() = get("Partager en...", "Share as...", "Partilhar comme...")
    val lightModeDesc @Composable get() = get("Idéal pour les emails ou messages", "Ideal for emails or messages", "Ideal para e-mails ou mensagens")
    val darkModeDesc @Composable get() = get("Idéal pour les réseaux sociaux (Twitter, Discord)", "Ideal for social media (Twitter, Discord)", "Ideal para redes sociais (Twitter, Discord)")
    val openRouteWith @Composable get() = get("Ouvrir l'itinéraire avec...", "Open route with...", "Abrir rota com...")
    val installedApp @Composable get() = get("Application installée", "Installed application", "Aplicação instalada")
    val installedAppDesc @Composable get() = get("Ouvrir avec Waze, Maps, OsmAnd...", "Open with Waze, Maps, OsmAnd...", "Abrir com Waze, Maps, OsmAnd...")
    val onInternet @Composable get() = get("Sur internet", "On the internet", "Na internet")
    val onInternetDesc @Composable get() = get("Ouvrir dans le navigateur web", "Open in web browser", "Abrir no navegador web")
    val noGpsApp @Composable get() = get("Aucune application GPS trouvée.", "No GPS application found.", "Nenhuma aplicação de GPS encontrada.")
    val shareSiteVia @Composable get() = get("Partager le site via...", "Share site via...", "Partilhar o local via...")
    val implementation @Composable get() = get("Implémentation : ", "Implementation : ", "Implementação : ")
    val lastModification @Composable get() = get("Dernière modification : ", "Last modification : ", "Última modificação : ")
    val generatedBy @Composable get() = get("Généré via l'application GeoTower", "Generated via the GeoTower app", "Gerado através da application GeoTower")
    val operatorsTitle @Composable get() = get("Opérateurs", "Operators", "Operadoras")

    // ==========================================
    // 📡 DÉTAIL DU SITE
    // ==========================================
    val frequenciesTitle @Composable get() = get("Fréquences", "Frequencies", "Frequências")
    val bandsNotSpecified @Composable get() = get("Bandes non spécifiées", "Bands not specified", "Bandas não especificadas")
    val externalLinks @Composable get() = get("Liens externes", "External Links", "Links externos")
    val installApp @Composable get() = get("Installer l'application", "Install application", "Instalar a aplicação")
    val map4G @Composable get() = get("Carte 4G", "4G Map", "Mapa 4G")
    val map5G @Composable get() = get("Carte 5G", "5G Map", "Mapa 5G")
    val unavailable @Composable get() = get("Indisponible", "Unavailable", "Indisponível")
    val whichMap @Composable get() = get("Quelle carte consulter ?", "Which map to consult?", "Que mapa consultar?")

    val identifiers @Composable get() = get("Identifiants", "Identifiers", "Identificadores")
    val inService @Composable get() = get("En service", "In service", "Em serviço")
    val technically @Composable get() = get("Techniquement opérationnel", "Technically operational", "Tecnicamente operacional")
    val projectApproved @Composable get() = get("Projet approuvé", "Project approved", "Projeto aprovado")
    val unknownStatus @Composable get() = get("Statut inconnu", "Unknown status", "Estado desconhecido")
    val anfrStationNumber @Composable get() = get("Numéro de station ANFR : ", "ANFR Station Number : ", "Número de estação ANFR : ")
    val dates @Composable get() = get("Dates", "Dates", "Datas")
    val error @Composable get() = get("Erreur", "Error", "Erro")
    val initError @Composable get() = get("Erreur d'initialisation", "Initialization error", "Erro de inicialização")

    val totalspectrum @Composable get() = get("Spectre total", "Total spectrum", "Espectro total")
    val totalSpectrumWarning @Composable get() = get(
        "Le spectre total peut être erroné en raison de probables erreurs de déclaration.",
        "The total spectrum may be inaccurate due to likely declaration errors.",
        "O espectro total pode estar incorreto devido a prováveis erros de declaração."
    )
    val spectrumTitle @Composable get() = get("Spectre", "Spectrum", "Espectro")
    val spectrumByBand @Composable get() = get("Spectre par plage de fréquence", "Spectrum by frequency band", "Espectro por faixa de fréquence")

    // ==========================================
    // 🗺️ CARTE ET RECHERCHE
    // ==========================================
    val searchCityOrId @Composable get() = get("Ville, adresse, ID de site...", "City, address, site ID...", "Cidade, endereço, ID do local...")
    val locationNotFound @Composable get() = get("Lieu introuvable", "Location not found", "Localização não encontrada")
    val networkErrorSearch @Composable get() = get("Erreur réseau lors de la recherche", "Network error during search", "Erro de rede durante a pesquisa")
    val deleteTraces @Composable get() = get("Supprimer les tracés", "Delete traces", "Eliminar traços")
    val closestSite @Composable get() = get("Site le plus proche", "Closest site", "Local mais prochain")
    val filter @Composable get() = get("Filtres", "Filters", "Filtros")
    val mapIgnLayer @Composable get() = get("IGN (Gouv)", "IGN (Gov)", "IGN (Gov)")
    val mapOsmLayer @Composable get() = get("OpenStreetMap", "OpenStreetMap", "OpenStreetMap")
    val mapLight @Composable get() = get("Clair", "Light", "Claro")
    val mapDark @Composable get() = get("Sombre", "Dark", "Escuro")
    val mapSatellite @Composable get() = get("Satellite", "Satellite", "Satélite")
    val technologiesTitle @Composable get() = get("Technologies", "Technologies", "Tecnologias")

    val siteDetailTitle @Composable get() = get("Détail du site", "Site Detail", "Detalhes do site")

    val openOn @Composable get() = get("Ouvrir sur", "Open on", "Abrir em")
    val website @Composable get() = get("Site Web", "Website", "Site")

    val activatedOn @Composable get() = get("Activé le : ", "Activated on : ", "Ativado em : ")
    val dateNotSpecifiedAnfr @Composable get() = get("Date d'activation non spécifiée par l'ANFR", "Activation date not specified by ANFR", "Data de ativação não especificada pela ANFR")
    val azimuthNotSpecified @Composable get() = get("Azimut non spécifié", "Azimuth not specified", "Azimute não especificado")

    val azimuthsLabel @Composable get() = get("Azimuts", "Azimuths", "Azimutes")

    val panelHeightsTitle @Composable get() = get("Hauteur des panneaux", "Panel heights", "Altura dos painéis")
    val idSupportLabel @Composable get() = get("ID Support : ", "Support ID : ", "ID do Suporte : ")

    val azimuthsTitle @Composable get() = get("Azimuts", "Azimuths", "Azimutes")

    val showAzimuthsLabel @Composable get() = get("Afficher les azimuts (direction de l'antenne)", "Show azimuths (antenna direction)", "Mostrar azimutes (direção da antena)")
    val showAzimuthsConeLabel @Composable get() = get("Afficher les azimuts (représentation en cône)", "Show azimuths (cone representation)", "Mostrar azimutes (représentação em cone)")
    val siteDisplayTitle @Composable get() = get("Affichage des sites", "Site display", "Exibição dos sites")
    val sitesInServiceLabel @Composable get() = get("En service", "In service", "Em serviço")
    val sitesOutOfServiceLabel @Composable get() = get("Hors Service", "Out of Service", "Fora de Serviço")

    val trackGlobalActive @Composable get() = get(
        "Suivi global en cours...",
        "Global tracking active...",
        "Acompanhamento global ativo..."
    )

    @Composable
    fun trackOpActive(op: String): String {
        return get(
            "Suivi $op en cours...",
            "$op tracking active...",
            "Acompanhamento $op ativo..."
        )
    }

    // ==========================================
    // 📊 STATISTIQUES
    // ==========================================
    val cityStatsTitle @Composable get() = get("Consulter les statistiques", "View statistics", "Ver estatísticas")
    val mobileTelephony @Composable get() = get("Téléphonie mobile", "Mobile telephony", "Telefonia móvel")
    val details @Composable get() = get("Détails", "Details", "Detalhes")
    val operatorDetailsTitle @Composable get() = get("Détail par opérateur", "Operator details", "Detalhe por operadora")
    @Composable
    fun sitesCount(count: Int) = get("$count sites", "$count sites", "$count locais")
    val activeDeclaredSitesLabel @Composable get() = get("Actifs / déclarés", "Active / declared", "Ativos / declarados")
    val frequenciesAndTechs @Composable get() = get("Technologies / Fréquences", "Technologies / Frequencies", "Tecnologias / Frequências")
    val loadingOperatorStats @Composable get() = get("Chargement des opérateurs...", "Loading operators...", "A carregar operadoras...")
    val loadingFrequencies @Composable get() = get("Chargement des fréquences...", "Loading frequencies...", "A carregar frequências...")
    val sitesLabel @Composable get() = get("Sites", "Sites", "Locais")

    val statsSupportsTitle @Composable get() = get("Supports (Pylônes)", "Supports (Pylons)", "Suportes (Pilões)")
    val statsSupportsDesc @Composable get() = get("Nombre de sites physiques par opérateur", "Number of physical sites per operator", "Número de locais físicos por operadora")
    val stats4GTitle @Composable get() = get("Sites 4G", "4G Sites", "Locais 4G")
    val stats4GDesc @Composable get() = get("Nombre de sites équipés en 4G par opérateur", "Number of 4G-equipped sites per operator", "Número de locaux équipés avec 4G por operadora")
    val stats5GTitle @Composable get() = get("Sites 5G", "5G Sites", "Locais 5G")
    val stats5GDesc @Composable get() = get("Nombre de sites équipés en 5G par opérateur", "Number of 5G-equipped sites per operator", "Número de locaux équipés com 5G por operadora")

    // ==========================================
    // 🧩 WIDGET
    // ==========================================
    val widgetTitle @Composable get() = get("📍 Antennes à proximité", "📍 Nearby antennas", "📍 Antenas próximas")
    val bgLocationPermTitle @Composable get() = get("Autorisation Widget", "Widget Permission", "Permissão do Widget")
    val bgLocationPermDesc @Composable get() = get("Autorisez \"Toujours\" pour que le widget s'actualise", "Allow \"Always\" for the widget to refresh", "Permita \"Sempre\" para o widget atualizar")

    // ==========================================
    // 📸 PHOTOS COMMUNAUTAIRES
    // ==========================================
    @Composable
    fun communityPhotosTitle(count: Int, communityName: String): String {
        val cleanName = communityName.replace(" ", "\u00A0")
        val fr = if (count > 1) "Photos de la communauté\n$cleanName" else "Photo de la communauté\n$cleanName"
        val en = if (count > 1) "Community photos from\n$cleanName" else "Community photo from\n$cleanName"
        val pt = if (count > 1) "Fotos da communauté\n$cleanName" else "Foto da comunidade\n$cleanName"
        return get(fr, en, pt)
    }

    val communityPhotosOffline @Composable get() = get(
        "Vous êtes hors ligne.\nLes photos communautaires ne peuvent pas être récupérées sans connexion internet. Le schéma reste affiché s'il est disponible.",
        "You are offline.\nCommunity photos cannot be retrieved without an internet connection. The diagram remains visible when available.",
        "Você está offline.\nAs fotos da comunidade não podem ser recuperadas sem ligação à internet. O esquema permanece visível quando disponível."
    )

    @Composable
    fun photoByAuthor(author: String) = get("Par $author", "By $author", "Por $author")

    @Composable
    fun photoOnDate(date: String) = get("le $date", "on $date", "em $date")

    @Composable
    fun communityPhotosTitleShort(count: Int): String {
        val fr = if (count > 1) "Photos de la communauté" else "Photo de la communauté"
        val en = if (count > 1) "Community photos" else "Community photo"
        val pt = if (count > 1) "Fotos da communauté" else "Foto da communauté"
        return get(fr, en, pt)
    }

    val sitePhotoDesc @Composable get() = get("Photo du site", "Site photo", "Foto do local")
    val fullScreenPhotoDesc @Composable get() = get("Photo en plein écran", "Full screen photo", "Foto em tela cheia")
    val supportImageDesc @Composable get() = get("Image du support", "Support image", "Imagem do suporte")
    val supportImageFullScreenDesc @Composable get() = get("Image en plein écran", "Full screen image", "Imagem em ecrã inteiro")
    val photoExifInfoDesc @Composable get() = get("Infos EXIF", "EXIF info", "Informações EXIF", "Info EXIF", "EXIF-Infos", "Información EXIF")
    val photoExifMetadataTitle @Composable get() = get("Métadonnées EXIF", "EXIF metadata", "Metadados EXIF", "Metadati EXIF", "EXIF-Metadaten", "Metadatos EXIF")
    val photoExifGpsPosition @Composable get() = get("Position GPS", "GPS position", "Posição GPS", "Posizione GPS", "GPS-Position", "Posición GPS")

    @Composable
    fun formatPhotoExifMonth(rawValue: String): String {
        val cleanValue = rawValue.trim().replace(Regex("\\s+"), " ")
        if (cleanValue.isEmpty()) return cleanValue

        val year = Regex("""\b(\d{4})\b""").find(cleanValue)?.groupValues?.get(1)
        val monthIndex = photoExifMonthIndex(cleanValue)

        return if (year != null && monthIndex != null) {
            "${getMonthName(monthIndex)} $year"
        } else {
            cleanValue.replaceFirstChar { if (it.isLowerCase()) it.titlecase(currentJavaLocale()) else it.toString() }
        }
    }

    private fun photoExifMonthIndex(rawValue: String): String? {
        Regex("""\b(\d{4})[-_/](\d{1,2})\b""").find(rawValue)
            ?.groupValues
            ?.getOrNull(2)
            ?.toPhotoExifMonthIndexOrNull()
            ?.let { return it }

        Regex("""\b(\d{1,2})[-_/](\d{4})\b""").find(rawValue)
            ?.groupValues
            ?.getOrNull(1)
            ?.toPhotoExifMonthIndexOrNull()
            ?.let { return it }

        Regex("""\b(\d{4})(\d{2})(?:\d{2})?\b""").find(rawValue)
            ?.groupValues
            ?.getOrNull(2)
            ?.toPhotoExifMonthIndexOrNull()
            ?.let { return it }

        val normalizedValue = rawValue.normalizedMonthSearchText()
        return photoExifMonthNamesByIndex.firstNotNullOfOrNull { (index, names) ->
            index.takeIf { names.any { monthName -> normalizedValue.contains(monthName) } }
        }
    }

    private fun String.toPhotoExifMonthIndexOrNull(): String? {
        val month = toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return month.toString().padStart(2, '0')
    }

    private fun String.normalizedMonthSearchText(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
    }

    private val photoExifMonthNamesByIndex = mapOf(
        "01" to listOf("janvier", "january", "janeiro", "gennaio", "januar", "enero"),
        "02" to listOf("fevrier", "february", "fevereiro", "febbraio", "februar"),
        "03" to listOf("mars", "march", "marco", "marzo", "marz"),
        "04" to listOf("avril", "april", "abril", "aprile"),
        "05" to listOf("mai", "may", "maio", "maggio", "mayo"),
        "06" to listOf("juin", "june", "junho", "giugno", "juni", "junio"),
        "07" to listOf("juillet", "july", "julho", "luglio", "juli", "julio"),
        "08" to listOf("aout", "august", "agosto"),
        "09" to listOf("septembre", "september", "setembro", "settembre", "septiembre"),
        "10" to listOf("octobre", "october", "outubro", "ottobre", "oktober", "octubre"),
        "11" to listOf("novembre", "november", "novembro", "noviembre"),
        "12" to listOf("decembre", "december", "dezembro", "dicembre", "dezember", "diciembre")
    )

    @Composable
    fun photoExifLabel(key: String): String = when (key) {
        "cameraModel" -> get("Appareil", "Device", "Aparelho", "Dispositivo", "Gerät", "Dispositivo")
        "distanceToSiteMeters" -> get("Distance au site", "Distance to site", "Distância ao site", "Distanza dal sito", "Entfernung zum Standort", "Distancia al sitio")
        "takenMonthLabel" -> get("Date de prise de vue", "Capture date", "Data de captura", "Data di scatto", "Aufnahmedatum", "Fecha de captura")
        "takenMonth" -> get("Mois EXIF", "EXIF month", "Mês EXIF", "Mese EXIF", "EXIF-Monat", "Mes EXIF")
        "gpsImgDirectionDegrees" -> get("Direction GPS", "GPS direction", "Direção GPS", "Direzione GPS", "GPS-Richtung", "Dirección GPS")
        "orientationDegrees" -> get("Orientation", "Orientation", "Orientação", "Orientamento", "Ausrichtung", "Orientación")
        "gpsLatitude", "latitude", "lat" -> get("Latitude GPS", "GPS latitude", "Latitude GPS", "Latitudine GPS", "GPS-Breitengrad", "Latitud GPS")
        "gpsLongitude", "longitude", "lng", "lon" -> get("Longitude GPS", "GPS longitude", "Longitude GPS", "Longitudine GPS", "GPS-Längengrad", "Longitud GPS")
        else -> key.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replaceFirstChar { it.uppercase() }
    }
    val close @Composable get() = get("Fermer", "Close", "Fechar")

    // ==========================================
    // 🖼️ PARTAGE ET GÉNÉRATION D'IMAGE
    // ==========================================
    val defaultShareContentTitle @Composable get() = get("Contenu du partage par défaut", "Default share content", "Conteúdo de partilha predefinido")
    val defaultShareContentDesc @Composable get() = get("Choisir les éléments à inclure sur l'image", "Choose the elements to include on the image", "Escolher os éléments a incluir na image")

    val shareMapDetailsTitle @Composable get() = get("Carte", "Map", "Mapa")
    val shareMapSpeedometerOption @Composable get() = get("Compteur de vitesse", "Speedometer", "Velocímetro")
    val shareMapScaleOption @Composable get() = get("Échelle", "Scale", "Escala")
    val shareMapAttributionOption @Composable get() = get("Crédits (Attribution)", "Credits (Attribution)", "Créditos (Atribuição)")

    val shareMapOption @Composable get() = get("Afficher la carte", "Display the map", "Mostrar o mapa")
    val shareElevationProfileOption @Composable get() = get("Profil altimétrique (image séparée)", "Elevation profile (separate image)", "Perfil altimétrico (imagem separada)")
    val shareSupportOption @Composable get() = get("Détails du support", "Support details", "Detalhes do suporte")
    val shareSpeedtestOption @Composable get() = get("Meilleur Speedtest", "Best Speedtest", "Melhor Speedtest") // 🚨 NEW
    val shareThroughputOption @Composable get() = get("Débit théorique", "Theoretical throughput", "Débito teórico")
    val shareFreqOption @Composable get() = get("Fréquences", "Frequencies", "Frequências")
    val shareDatesOption @Composable get() = get("Dates d'activation", "Activation dates", "Datas de ativação")
    val shareAddressOption @Composable get() = get("Adresse et Coordonnées", "Address and Coordinates", "Endereço e Coordonadas")
    val shareIdsOption @Composable get() = get("Identifiants de l'antenne", "Antenna identifiers", "Identificadores da antena")
    val shareConfidentialOption @Composable get() = get("Partage confidentiel", "Confidential share", "Partilha confidencial")
    val shareConfidentialDesc @Composable get() = get("Supprime les données permettant l'identification du lieu", "Removes data allowing location identification", "Remove dados que permettent a identificação do local")

    val scanToOpen @Composable get() = get("Scannez pour ouvrir dans", "Scan to open in", "Escaneie para ouvrir no")
    val geoTowerApp @Composable get() = get("l'application GeoTower", "the GeoTower app", "aplicativo GeoTower")

    // ==========================================
    // 🔁 ACTIONS COMMUNES ET NAVIGATION
    // ==========================================
    val back @Composable get() = get("Retour", "Back", "Voltar")
    val openMenu @Composable get() = get("Ouvrir menu", "Open menu", "Abrir menu")
    val closeMenu @Composable get() = get("Fermer menu", "Close menu", "Fechar menu")
    val search @Composable get() = get("Rechercher", "Search", "Pesquisar")
    val clear @Composable get() = get("Effacer", "Clear", "Limpar")
    val download @Composable get() = get("Télécharger", "Download", "Transferir")
    val delete @Composable get() = get("Supprimer", "Delete", "Eliminar")
    val apply @Composable get() = get("Appliquer", "Apply", "Aplicar")
    val technicalDataUnavailable @Composable get() = get("Données techniques non disponibles", "Technical data unavailable", "Dados técnicos indisponíveis")
    val ruler @Composable get() = get("Règle", "Ruler", "Régua")
    val layers @Composable get() = get("Calques", "Layers", "Camadas")
    val tools @Composable get() = get("Outils", "Tools", "Ferramentas")
    val locate @Composable get() = get("Localiser", "Locate", "Localizar")
    val top @Composable get() = get("Haut", "Top", "Topo")
    val bottom @Composable get() = get("Bas", "Bottom", "Fundo")
    val noOfflineMapsInstalled @Composable get() = get("(Aucune carte hors-ligne installée)", "(No offline map installed)", "(Nenhum mapa offline instalado)")

    // ==========================================
    // 📝 NOTES DE VERSION
    // ==========================================
    val latestChanges @Composable get() = get("Dernières modifications", "Latest changes", "Últimas alterações")
    val releaseSectionLanguages @Composable get() = get("Langues", "Languages", "Idiomas", "Lingue", "Sprachen", "Idiomas")
    val releaseSectionTranslations @Composable get() = get("Traductions", "Translations", "Traduções", "Traduzioni", "Übersetzungen", "Traducciones")
    val releaseSectionCommunityPhotos @Composable get() = get("Photos communautaires", "Community photos", "Fotos comunitárias", "Foto della comunità", "Community-Fotos", "Fotos comunitarias")
    val releaseSectionInterface @Composable get() = get("Interface", "Interface", "Interface", "Interfaccia", "Oberfläche", "Interfaz")
    val releaseLanguageAdditions @Composable get() = get(
        "Ajout de l'italien, de l'allemand et de l'espagnol",
        "Added Italian, German and Spanish",
        "Adição de italiano, alemão e espanhol",
        "Aggiunti italiano, tedesco e spagnolo",
        "Italienisch, Deutsch und Spanisch hinzugefügt",
        "Añadidos italiano, alemán y español"
    )
    val releaseMultilingualManagement @Composable get() = get(
        "Amélioration de la gestion multilingue dans toute l'application",
        "Improved multilingual handling across the app",
        "Melhoria da gestão multilingue em toda a aplicação",
        "Migliorata la gestione multilingue in tutta l'app",
        "Mehrsprachige Verwaltung in der gesamten App verbessert",
        "Mejorada la gestión multilingüe en toda la aplicación"
    )
    val releaseNotificationsWidgetAutoTranslations @Composable get() = get(
        "Ajout de nouvelles traductions pour les notifications, le widget et Android Auto",
        "Added new translations for notifications, the widget and Android Auto",
        "Adição de novas traduções para notificações, widget e Android Auto",
        "Aggiunte nuove traduzioni per notifiche, widget e Android Auto",
        "Neue Übersetzungen für Benachrichtigungen, Widget und Android Auto hinzugefügt",
        "Añadidas nuevas traducciones para notificaciones, widget y Android Auto"
    )
    val releaseSignalQuestHelpErrorTranslations @Composable get() = get(
        "Amélioration des textes traduits pour SignalQuest, l'aide, les erreurs et le calculateur de débit",
        "Improved translated texts for SignalQuest, help, errors and the throughput calculator",
        "Melhoria dos textos traduzidos para SignalQuest, ajuda, erros e calculadora de débito",
        "Migliorati i testi tradotti per SignalQuest, guida, errori e calcolatore di velocità",
        "Übersetzte Texte für SignalQuest, Hilfe, Fehler und Durchsatzrechner verbessert",
        "Mejorados los textos traducidos para SignalQuest, ayuda, errores y calculadora de velocidad"
    )
    val releaseCommunityExifSetting @Composable get() = get(
        "Ajout d'un réglage pour afficher ou masquer les informations EXIF",
        "Added a setting to show or hide EXIF information",
        "Adição de uma definição para mostrar ou ocultar informações EXIF",
        "Aggiunta un'impostazione per mostrare o nascondere le informazioni EXIF",
        "Einstellung zum Ein- oder Ausblenden von EXIF-Informationen hinzugefügt",
        "Añadido un ajuste para mostrar u ocultar la información EXIF"
    )
    val releaseCommunityPhotoLocalization @Composable get() = get(
        "Meilleure localisation des informations liées aux photos",
        "Improved localization of photo-related information",
        "Melhor localização das informações relacionadas com fotos",
        "Migliore localizzazione delle informazioni relative alle foto",
        "Bessere Lokalisierung fotobezogener Informationen",
        "Mejor localización de la información relacionada con las fotos"
    )
    val releaseWidgetLocalization @Composable get() = get(
        "Amélioration de la localisation du widget",
        "Improved widget localization",
        "Melhoria da localização do widget",
        "Migliorata la localizzazione del widget",
        "Lokalisierung des Widgets verbessert",
        "Mejorada la localización del widget"
    )
    val releaseTranslatedReleaseNotes @Composable get() = get(
        "Notes de version désormais mieux intégrées aux traductions",
        "Release notes are now better integrated with translations",
        "Notas de versão agora melhor integradas nas traduções",
        "Note di versione ora meglio integrate nelle traduzioni",
        "Versionshinweise sind jetzt besser in die Übersetzungen integriert",
        "Notas de versión ahora mejor integradas con las traducciones"
    )
    val showMoreSites @Composable get() = get("Afficher plus de sites", "Show more sites", "Mostrar mais")
    val geoportailIgn @Composable get() = get("Géoportail (IGN)", "Geoportal (IGN)", "Geoportal (IGN)")
    val unknownAddress @Composable get() = get("Adresse inconnue", "Unknown address", "Endereço desconhecido", "Indirizzo sconosciuto", "Unbekannte Adresse", "Dirección desconocida")
    val siteAnfrLabel @Composable get() = get("Site ANFR", "ANFR site", "Site ANFR", "Sito ANFR", "ANFR-Standort", "Sitio ANFR")
    val languageFrenchName @Composable get() = get("Français", "French", "Francês", "Francese", "Französisch", "Francés")
    val languageEnglishName @Composable get() = get("Anglais", "English", "Inglês", "Inglese", "Englisch", "Inglés")
    val languagePortugueseName @Composable get() = get("Portugais", "Portuguese", "Português", "Portoghese", "Portugiesisch", "Portugués")
    val languageItalianName @Composable get() = get("Italien", "Italian", "Italiano", "Italiano", "Italienisch", "Italiano")
    val languageGermanName @Composable get() = get("Allemand", "German", "Alemão", "Tedesco", "Deutsch", "Alemán")
    val languageSpanishName @Composable get() = get("Espagnol", "Spanish", "Espanhol", "Spagnolo", "Spanisch", "Español")
    @Composable
    fun languageDisplayName(languageValue: String): String = when (languageValue) {
        LANGUAGE_SYSTEM -> systemLanguage
        LANGUAGE_FRENCH -> languageFrenchName
        LANGUAGE_ENGLISH -> languageEnglishName
        LANGUAGE_PORTUGUESE -> languagePortugueseName
        LANGUAGE_ITALIAN -> languageItalianName
        LANGUAGE_GERMAN -> languageGermanName
        LANGUAGE_SPANISH -> languageSpanishName
        else -> languageValue
    }
    val imageContent @Composable get() = get("Contenu de l'image", "Image content", "Conteúdo da imagem")
    val move @Composable get() = get("Déplacer", "Move", "Mover")
    val generateImage @Composable get() = get("Générer l'image", "Generate image", "Gerar imagem")
    val shareImageGenerationInProgress @Composable get() = get("Génération des images de partage...", "Generating share images...", "A gerar as imagens de partilha...")
    val shareImagePreparingInProgress @Composable get() = get("Préparation de l'image principale...", "Preparing the main image...", "A preparar a imagem principal...")
    val shareElevationProfileOnlyUnavailable @Composable get() = get("Profil altimétrique indisponible pour le partage.", "Elevation profile unavailable for sharing.", "Perfil altimétrico indisponível para partilha.")
    val copy @Composable get() = get("Copier", "Copy", "Copiar")
    val distanceHidden @Composable get() = get("Distance masquée (Mode confidentiel)", "Distance hidden (Confidential mode)", "Distância oculta (Modo confidencial)")

    val idSupportCopy @Composable get() = get("ID Support", "Support ID", "ID do Suporte")
    val addressCopy @Composable get() = get("Adresse", "Address", "Endereço")
    val gpsCoordsCopy @Composable get() = get("Coordonnées GPS", "GPS Coordinates", "Coordenadas GPS")

    val warningTitle @Composable get() = get("Attention", "Warning", "Atenção")
    val lightColorWarning @Composable get() = get(
        "Votre couleur MaterialUi est trop claire. Pour plus de lisibilité, la couleur bleu foncé a été appliquée.",
        "Your MaterialUi color is too light. For better readability, dark blue has been applied.",
        "A sua couleur MaterialUi é muito clara. Para melhor legibilidade, a cor azul escuro foi appliquée."
    )
    val doNotShowAgain @Composable get() = get("Ne plus afficher ce message", "Do not show this message again", "Não mostrar esta mensagem novamente")
    val understood @Composable get() = get("J'ai compris", "Understood", "Entendi")

    // ==========================================
    // 🚀 ENVOI SIGNALQUEST
    // ==========================================
    val uploadSqTitle @Composable get() = get("Envoi vers Signal Quest", "Send to Signal Quest", "Enviar para Signal Quest")
    val uploadSqStripExif @Composable get() = get("Supprimer les donn\u00e9es EXIF", "Remove EXIF data", "Remover dados EXIF")
    val uploadSqDescPlaceholder @Composable get() = get("Ajouter une description pour ce lot (optionnel)...", "Add a description for this batch (optional)...", "Adicionar uma descrição pour este lote (optionnal)...")
    val uploadSqTargetOperator @Composable get() = get("Opérateur cible", "Target operator", "Operadora destino")
    val uploadSqTargetSite @Composable get() = get("Support N°", "Site ID", "ID do Suporte")

    @Composable
    fun uploadSqButton(count: Int) = get(
        "Envoyer $count photo${if (count > 1) "s" else ""}",
        "Send $count photo${if (count > 1) "s" else ""}",
        "Enviar $count foto${if (count > 1) "s" else ""}"
    )

    val uploadSqLimit @Composable get() = get("Limite : 20 Mo par photo", "Limit: 20 MB per photo", "Limite: 20 MB por foto")
    val uploadConfirmTitle @Composable get() = get("Confirmation d'envoi", "Upload Confirmation", "Confirmação de envio")
    @Composable
    fun uploadConfirmMessage(count: Int) = get(
        "Êtes-vous sûr de vouloir envoyer ${if (count > 1) "ces $count photos" else "cette photo"} vers Signal Quest ?",
        "Are you sure you want to send ${if (count > 1) "these $count photos" else "this photo"} to Signal Quest?",
        "Tem a certeza de que deseja enviar ${if (count > 1) "estas $count fotos" else "esta foto"} para a Signal Quest?"
    )
    val cancel @Composable get() = get("Annuler", "Cancel", "Cancelar")

    val uploadingTitle @Composable get() = get("Envoi en cours", "Uploading", "Enviando")
    val uploadPreparing @Composable get() = get("Préparation...", "Preparing...", "Preparando...")

    @Composable
    fun uploadProgressText(current: Int, total: Int) = get(
        "$current / $total photo(s) envoyée(s)...",
        "$current / $total photo(s) sent...",
        "$current / $total foto(s) enviada(s)..."
    )
    val hide @Composable get() = get("Masquer", "Hide", "Ocultar")

    val uploadFinishedTitle @Composable get() = get("Envoi terminé !", "Upload finished!", "¡Envío terminado!")

    @Composable
    fun uploadResultText(success: Int, total: Int) = get(
        "$success sur $total photo(s) envoyée(s) avec succès vers Signal Quest.",
        "$success out of $total photo(s) successfully sent to Signal Quest.",
        "$success de $total foto(s) enviadas com sucesso para Signal Quest."
    )

    val uploadErrorWarning @Composable get() = get(
        "Certaines photos n'ont pas pu être envoyées (problème réseau).",
        "Some photos could not be sent (network issue).",
        "Algumas fotos não puderam ser enviadas (problema de rede)."
    )

    @Composable
    fun uploadLifetimeScore(score: Int) = get(
        "🏆 Total : $score photos partagées depuis vos débuts !",
        "🏆 Total: $score photos shared since you started!",
        "🏆 Total: $score fotos partilhadas desde o início!"
    )

    val awesome @Composable get() = get("Super !", "Awesome!", "¡Genial!")

    // ==========================================
    // 💾 TÉLÉCHARGEMENTS ET BANNIÈRES BASE
    // ==========================================
    val notifDbDownloadSuccess @Composable get() = get("Téléchargement terminé", "Download finished", "Transferência concluída")
    val dbDownloadSuccessDesc @Composable get() = get("La base de données a été téléchargée avec succès !", "The database was successfully downloaded!", "A base de données foi transferida com sucesso!")
    val dbDownloadTermine @Composable get() = get("Terminer", "Finish", "Terminar")

    val dbWarningTitle @Composable get() = get("Base de donnée non téléchargée", "Database not downloaded", "Base de données não transferida")
    val dbWarningDesc @Composable get() = get("Vous n'avez pas téléchargé la base de donnée de l'application, vous n'aurez donc aucun élément affiché à l'écran.", "You haven't downloaded the app's database, so you won't have any items displayed on the screen.", "Você não transferiu a base de données da aplicação, portanto não terá nenhum item exibido na tela.")
    val dbWarningQuestion @Composable get() = get("Êtes-vous sûr de vouloir continuer ?", "Are you sure you want to continue?", "Tem certeza que deseja continuar?")
    val continueAnyway @Composable get() = get("Continuer", "Continue", "Continuar")

    val missingDbBannerTitle @Composable get() = get("Base de données manquante", "Missing database", "Base de données ausente")
    val invalidDbBannerTitle @Composable get() = get("Base de données invalide", "Invalid database", "Base de données invalida")
    val updateDbBannerTitle @Composable get() = get("Mise à jour disponible", "Update available", "Atualização disponible")
    val missingDbBannerDesc @Composable get() = get("Téléchargez la base pour utiliser l'appli.", "Download the database to use the app.", "Baixe o banco de données para usar o app.")
    val invalidDbBannerDesc @Composable get() = get("La base locale est incompatible. Téléchargez une base valide pour continuer.", "The local database is incompatible. Download a valid database to continue.", "A base local e incompativel. Baixe uma base valida para continuar.")
    val btnDownloadBanner @Composable get() = get("Télécharger", "Download", "Baixar")

    // ==========================================
    // 🗂️ DONNÉES ANFR : NATURES, TYPES, PROPRIÉTAIRES
    // ==========================================
    @Composable
    fun translateNature(nature: String?): String {
        if (nature.isNullOrBlank()) return get("Non spécifiée", "Not specified", "Não especificado")

        return when (nature.trim()) {
            "Sans nature" -> get("Sans nature", "No type", "Sem natureza")
            "Sémaphore" -> get("Sémaphore", "Semaphore tower", "Torre de semáforo")
            "Phare" -> get("Phare", "Lighthouse", "Farol")
            "Château d'eau - réservoir" -> get("Château d'eau - réservoir", "Water tower / reservoir", "Castelo d'água / reservatório")
            "Immeuble" -> get("Immeuble", "Building", "Prédio")
            "Local technique" -> get("Local technique", "Technical room / equipment shelter", "Sala técnica / abrigo de équipements")
            "Mât" -> get("Mât", "Mast", "Mastro")
            "Intérieur galerie" -> get("Intérieur galerie", "Inside gallery", "Interior de galeria")
            "Intérieur sous-terrain" -> get("Intérieur sous-terrain", "Underground interior", "Interior subterrâneo")
            "Tunnel" -> get("Tunnel", "Tunnel", "Túnel")
            "Mât béton" -> get("Mât béton", "Concrete mast", "Mastro de concreto")
            "Mât métallique" -> get("Mât métallique", "Metal mast", "Mastro metálico")
            "Pylône" -> get("Pylône", "Tower / pylon", "Pilar / torre")
            "Bâtiment" -> get("Bâtiment", "Building", "Edifício")
            "Monument historique" -> get("Monument historique", "Historic monument", "Monumento histórico")
            "Monument religieux" -> get("Monument religieux", "Religious monument", "Monumento religioso")
            "Pylône autoportant" -> get("Pylône autoportant", "Self-supporting pylon", "Pilar autoportante")
            "Pylône autostable" -> get("Pylône autostable", "Free-standing pylon", "Pilar autossustentado")
            "Pylône haubané" -> get("Pylône haubané", "Guyed tower", "Torre estaiada")
            "Pylône treillis" -> get("Pylône treillis", "Lattice tower", "Torre treliçada")
            "Pylône tubulaire" -> get("Pylône tubulaire", "Tubular tower", "Torre tubular")
            "Silo" -> get("Silo", "Silo", "Silo")
            "Ouvrage d'art (pont, viaduc)" -> get("Ouvrage d'art (pont, viaduc)", "Engineering structure (bridge, viaduct)", "Obra de arte (ponte, viaduto)")
            "Tour hertzienne" -> get("Tour hertzienne", "Microwave tower", "Torre hertziana")
            "Dalle en béton" -> get("Dalle en béton", "Concrete slab", "Laje de concreto")
            "Support non décrit" -> get("Support non décrit", "Unspecified support", "Suporte não descrito")
            "Fût" -> get("Fût", "Column / shaft", "Coluna / eixo")
            "Tour de contrôle" -> get("Tour de contrôle", "Control tower", "Torre de controle")
            "Contre-poids au sol" -> get("Contre-poids au sol", "Ground counterweight", "Contrapeso no solo")
            "Contre-poids sur shelter" -> get("Contre-poids sur shelter", "Shelter counterweight", "Contrapeso sobre abrigo")
            "Support DEFENSE" -> get("Support DEFENSE", "Defense support", "Suporte de defesa")
            "pylône arbre" -> get("pylône arbre", "Tree mast / camouflaged tower", "Mastro em forma de árvore / torre camuflada")
            "Ouvrage de signalisation (portique routier, panneau routier)" -> get("Ouvrage de signalisation (portique routier, panneau routier)", "Signage structure (road gantry, road panel)", "Estrutura de sinalização (pórtico rodoviário, painel)")
            "Balise ou bouée" -> get("Balise ou bouée", "Beacon or buoy", "Baliza ou boia")
            "XXX" -> get("XXX", "Unknown", "Desconhecido")
            "Eolienne" -> get("Eolienne", "Wind turbine", "Turbina eólica")
            "Mobilier urbain" -> get("Mobilier urbain", "Urban furniture / street structure", "Mobiliário urbano / estrutura de rua")
            else -> nature
        }
    }

    @Composable
    fun translateAntennaType(type: String?): String {
        if (type.isNullOrBlank()) return get("Non spécifié", "Not specified", "Não especificado")

        return when (type.trim()) {
            "Antenne Ran-Sharing" -> get("Antenne Ran-Sharing", "Ran-Sharing antenna", "Antena Ran-Sharing")
            "Tube" -> get("Tube", "Tube", "Tubo")
            "Sans type" -> get("Sans type", "No type", "Sem tipo")
            "Accordable" -> get("Accordable", "Tunable", "Sintonizável")
            "Active (directionnelle ou omnidirectionnelle)" -> get("Active (directionnelle ou omnidirectionnelle)", "Active (directional or omni)", "Ativa (direcional ou omnidirecional)")
            "Cigare" -> get("Cigare", "Cigar antenna", "Antena tipo charuto")
            "Corolle" -> get("Corolle", "Corolla antenna", "Antena corola")
            "Dipôle large bande" -> get("Dipôle large bande", "Wideband dipole", "Dipolo de banda larga")
            "Dipôle réglable" -> get("Dipôle réglable", "Adjustable dipole", "Dipolo ajustável")
            "Antenne directive" -> get("Antenne directive", "Directional antenna", "Antena direcional")
            "Filaire" -> get("Filaire", "Wire antenna", "Antena de fio")
            "Fouet" -> get("Fouet", "Whip antenna", "Antena chicote")
            "Fuseau" -> get("Fuseau", "Spindle antenna", "Antena fusiforme")
            "Réseau linéaire 25 antennes" -> get("Réseau linéaire 25 antennes", "Linear array (25 antennas)", "Matriz linear (25 antenas)")
            "Groundplane" -> get("Groundplane", "Ground plane", "Plano de terra")
            "HLO" -> get("HLO", "HLO", "HLO")
            "Logarithmique/Log périodique" -> get("Logarithmique/Log périodique", "Log-periodic antenna", "Antena log-periódica")
            "Losange" -> get("Losange", "Diamond antenna", "Antena losango")
            "Panneau" -> get("Panneau", "Panel", "Painel")
            "Antenne parabolique" -> get("Antenne parabolique", "Dish antenna", "Antena parabólica")
            "Cierge/Perche" -> get("Cierge/Perche", "Whip / Pole antenna", "Antena tipo poste")
            "Réseaux d'antennes" -> get("Réseaux d'antennes", "Antenna array", "Matriz de antenas")
            "Système antennaire" -> get("Système antennaire", "Antenna system", "Sistema de antenas")
            "Yagi" -> get("Yagi", "Yagi", "Yagi")
            "Réseau linéaire 13 antennes" -> get("Réseau linéaire 13 antennes", "Linear array (13 antennas)", "Matriz linear (13 antenas)")
            "Antenne à fentes" -> get("Antenne à fentes", "Slot antenna", "Antena de fendas")
            "Réseau circulaire 49 antennes" -> get("Réseau circulaire 49 antennes", "Circular array (49 antennas)", "Matriz circular (49 antenas)")
            "Réseau vertical" -> get("Réseau vertical", "Vertical array", "Matriz vertical")
            "Réseau vertical 2 antennes type P" -> get("Réseau vertical 2 antennes type P", "Vertical array (2 antennas, type P)", "Matriz vertical (2 antenas tipo P)")
            "Réseau vertical 3 antennes type M" -> get("Réseau vertical 3 antennes type M", "Vertical array (3 antennas, type M)", "Matriz vertical (3 antenas tipo M)")
            "Antenne Marguerite" -> get("Antenne Marguerite", "Daisy antenna", "Antena margarida")
            "Antenne Parapluie" -> get("Antenne Parapluie", "Umbrella antenna", "Antena guarda-chuva")
            "Antenne Gonio" -> get("Antenne Gonio", "Goniometric antenna", "Antena goniométrica")
            "Dipôle/Doublet" -> get("Dipôle/Doublet", "Dipole", "Dipolo")
            "Trombone" -> get("Trombone", "Folded dipole", "Dipolo dobrado")
            "Colinéaire" -> get("Colinéaire", "Collinear antenna", "Antena colinear")
            "Antenne Plane LVA" -> get("Antenne Plane LVA", "Flat antenna LVA", "Antena plana LVA")
            "Dipôle VHF" -> get("Dipôle VHF", "VHF dipole", "Dipolo VHF")
            "Antenne HF" -> get("Antenne HF", "HF antenna", "Antena HF")
            "Antenne Plane" -> get("Antenne Plane", "Flat antenna", "Antena plana")
            "Perche DAB" -> get("Perche DAB", "DAB mast", "Mastro DAB")
            "Aérien issu de reprise des données électroniques" -> get("Aérien issu de reprise des données électroniques", "Recovered electronic data antenna", "Antena proveniente de dados eletrônicos")
            "Panneau DAB" -> get("Panneau DAB", "DAB panel", "Painel DAB")
            "Antenne DAB" -> get("Antenne DAB", "DAB antenna", "Antena DAB")
            "Plan passif ou miroir" -> get("Plan passif ou miroir", "Passive plane or reflector", "Plano passivo ou refletor")
            "Antenne Grille" -> get("Antenne Grille", "Grid antenna", "Antena gradeada")
            "Cornet" -> get("Cornet", "Horn antenna", "Antena corneta")
            "Panneau bi-bandes" -> get("Panneau bi-bandes", "Dual-band panel", "Painel bibanda")
            "Panneau tri-bandes" -> get("Panneau tri-bandes", "Tri-band panel", "Painel tribanda")
            "Cylindre" -> get("Cylindre", "Cylindrical antenna", "Antena cilíndrica")
            "Dièdre" -> get("Dièdre", "Dihedral antenna", "Antena diédrica")
            "Globe-Plafonnier" -> get("Globe-Plafonnier", "Ceiling globe antenna", "Antena globo de teto")
            "Discone" -> get("Discone", "Discone", "Discone")
            "Antenne dalle" -> get("Antenne dalle", "Tile antenna", "Antena de laje")
            "Antenne radar" -> get("Antenne radar", "Radar antenna", "Antena de radar")
            "Obus" -> get("Obus", "Shell antenna", "Antena ogiva")
            "Helicoidal" -> get("Helicoidal", "Helical antenna", "Antena helicoidal")
            "Aérien DEFENSE" -> get("Aérien DEFENSE", "Defense aerial", "Aéreo de defesa")
            "Antenne trisectorielle" -> get("Antenne trisectorielle", "Tri-sector antenna", "Antena trissetorial")
            "Antenne indoor pour téléphonie mobile" -> get("Antenne indoor pour téléphonie mobile", "Indoor antenna", "Antena de interior")
            "Cable rayonnant (antenne coaxiale)" -> get("Cable rayonnant (antenne coaxiale)", "Leaky feeder (coaxial antenna)", "Cabo radiante (antena coaxial)")
            "Antenne équidirective dans un plan" -> get("Antenne équidirective dans un plan", "Equidirectional antenna in one plane", "Antena equidirecional em um plano")
            "Antenne à rayonnement longitudinal" -> get("Antenne à rayonnement longitudinal", "Longitudinal radiation antenna", "Antena de radiação longitudinal")
            "Antenne à rayonnement zenithal" -> get("Antenne à rayonnement zenithal", "Zenithal radiation antenna", "Antena de radiação zenital")
            "Multi Doublets/Multi dipoles" -> get("Multi Doublets/Multi dipoles", "Multi-dipole array", "Matriz de múltiplos dipolos")
            "Antenne à faisceau" -> get("Antenne à faisceau", "Beam antenna", "Antena de feixe")
            "Antenne à jupe" -> get("Antenne à jupe", "Skirt antenna", "Antena com saia")
            "Antenne biconique" -> get("Antenne biconique", "Biconical antenna", "Antena biconica")
            "REC-465" -> get("REC-465", "REC-465", "REC-465")
            "REC-580" -> get("REC-580", "REC-580", "REC-580")
            "AP27" -> get("AP27", "AP27", "AP27")
            "29-25LOG(FI)" -> get("29-25LOG(FI)", "29-25LOG(FI)", "29-25LOG(FI)")
            "Pylone Rayonnant" -> get("Pylone Rayonnant", "Radiating tower", "Pilar radiante")
            "XXX" -> get("XXX", "Unknown", "Desconhecida")
            "Panneau bi-mode" -> get("Panneau bi-mode", "Dual-mode panel", "Painel bimodo")
            "Antenne pour la diffusion radio à ondes courtes ALLOUIS ISSOUDUN" -> get("Antenne pour la diffusion radio à ondes courtes ALLOUIS ISSOUDUN", "Shortwave broadcast antenna (ALLOUIS ISSOUDUN)", "Antena de ondas curtas de radiodifusão (ALLOUIS ISSOUDUN)")
            "Tout en 1(panneau-faisceau orientable)" -> get("Tout en 1(panneau-faisceau orientable)", "All-in-one (steerable panel-beam)", "Tudo em um (painel-feixe orientável)")
            "Antenne à faisceaux orientables" -> get("Antenne à faisceaux orientables", "Steerable beam antenna", "Antena de feixes orientáveis")
            "Antenne cadre" -> get("Antenne cadre", "Frame antenna", "Antena de quadro")
            "Antenne BYT" -> get("Antenne BYT", "BYT antenna", "Antena BYT")
            "Antenne SFR" -> get("Antenne SFR", "SFR antenna", "Antena SFR")
            else -> type
        }
    }

    @Composable
    fun translateOwner(owner: String?): String {
        if (owner.isNullOrBlank()) return get("Non spécifié", "Not specified", "Não especificado")

        return when (owner.trim()) {
            "Particulier" -> get("Particulier", "Private individual", "Particular")
            "Copropriété, Syndic, SCI" -> get("Copropriété, Syndic, SCI", "Condominium, Trustee, SCI", "Condomínio, Síndico, SCI")
            "Commune, communauté de commune" -> get("Commune, communauté de commune", "Municipality", "Município")
            "Conseil Départemental" -> get("Conseil Départemental", "Departmental Council", "Conselho Departamental")
            "Conseil Régional" -> get("Conseil Régional", "Regional Council", "Conselho Regional")
            "Société Privée" -> get("Société Privée", "Private company", "Empresa privada")
            "Etablissement de soins" -> get("Établissement de soins", "Healthcare facility", "Estabelecimento de saúde")
            "Etat Ministère" -> get("Etat Ministère", "State / Ministry", "Estado / Ministério")
            "Aviation Civile" -> get("Aviation Civile", "Civil Aviation", "Aviação Civil")
            else -> owner
        }
    }

    // ==========================================
    // 🔔 SERVICES, NOTIFICATIONS, WIDGET ET ANDROID AUTO
    // ==========================================
    // Fonction utilitaire spéciale pour lire la langue sans @Composable
    fun getForService(
        context: android.content.Context,
        fr: String,
        en: String,
        pt: String,
        it: String = fallbackTranslation(en, LANGUAGE_ITALIAN),
        de: String = fallbackTranslation(en, LANGUAGE_GERMAN),
        es: String = fallbackTranslation(en, LANGUAGE_SPANISH)
    ): String {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", android.content.Context.MODE_PRIVATE)
        val currentLang = prefs.getString("app_language", LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        val langToCheck = if (currentLang == LANGUAGE_SYSTEM) java.util.Locale.getDefault().language else currentLang

        return resolveForLanguage(langToCheck, fr, en, pt, it, de, es)
    }

    fun newDbNotifTitle(ctx: android.content.Context) = getForService(ctx, "Nouvelle base de données", "New database", "Nova base de données")
    fun newDbNotifDesc(ctx: android.content.Context) = getForService(ctx, "Une mise à jour des antennes est disponible. Touchez pour ouvrir la section de téléchargement.", "An antenna update is available. Tap to open the download section.", "Uma atualização das antenas está disponível. Toque para abrir a secção de transferência.")
    fun newAppNotifTitle(ctx: android.content.Context) = getForService(ctx, "Nouvelle version GeoTower", "New GeoTower version", "Nova versao GeoTower")
    fun newAppNotifDesc(ctx: android.content.Context, versionName: String) = getForService(ctx, "GeoTower $versionName est disponible. Touchez pour ouvrir le telechargement.", "GeoTower $versionName is available. Tap to open the download.", "GeoTower $versionName esta disponivel. Toque para abrir a transferencia.")
    fun newAppChannelName(ctx: android.content.Context) = getForService(ctx, "Mises a jour GeoTower", "GeoTower updates", "Atualizacoes GeoTower")
    fun nearestAntennaTitle(ctx: android.content.Context) = getForService(ctx, "À proximité", "Nearby", "Nas proximidades")
    fun liveTrackingChannelDesc(ctx: android.content.Context) = getForService(ctx, "Suivi d'antennes en direct", "Live antenna tracking", "Rastreamento de antennes ao vivo")
    fun searchInProgress(ctx: android.content.Context) = getForService(ctx, "Recherche en cours...", "Searching...", "Buscando...")
    fun quitAction(ctx: android.content.Context) = getForService(ctx, "Quitter", "Stop", "Sair")
    fun errorForService(ctx: android.content.Context) = getForService(ctx, "Erreur", "Error", "Erro")

    // Notifications base de données et cartes hors ligne.
    fun dbDownloadChannelName(ctx: android.content.Context) = getForService(ctx, "Mise à jour Base de données", "Database Update", "Atualização da base de dados")
    fun dbDownloadTitle(ctx: android.content.Context) = getForService(ctx, "Mise à jour de la base", "Database update", "Atualização da base")
    fun dbDownloadProgress(ctx: android.content.Context, progress: Int) = getForService(ctx, "Téléchargement en cours... $progress%", "Downloading... $progress%", "A transferir... $progress%")
    fun dbDownloadedTitle(ctx: android.content.Context) = getForService(ctx, "Base de données", "Database", "Base de dados")
    fun dbDownloadedContent(ctx: android.content.Context) = getForService(ctx, "La base a été téléchargée. Appuyez pour ouvrir.", "Database downloaded. Tap to open.", "Base transferida. Toque para abrir.")
    fun dbDownloadFailed(ctx: android.content.Context) = getForService(ctx, "Échec du téléchargement. Veuillez vérifier votre connexion.", "Download failed. Please check your connection.", "Falha na transferência. Verifique a sua ligação.")
    fun newDbChannelName(ctx: android.content.Context) = getForService(ctx, "Mises à jour Base de données", "Database updates", "Atualizações da base de dados")

    fun mapDownloadChannelName(ctx: android.content.Context) = getForService(ctx, "Téléchargement de cartes", "Maps download", "Transferência de mapas")
    fun mapDownloadTitle(ctx: android.content.Context, mapName: String) = getForService(ctx, "Carte : $mapName", "Map: $mapName", "Mapa: $mapName")
    fun mapDownloadProgress(ctx: android.content.Context, progress: Int) = getForService(ctx, "Téléchargement... $progress%", "Downloading... $progress%", "A transferir... $progress%")
    fun mapDownloadedTitle(ctx: android.content.Context) = getForService(ctx, "Carte téléchargée", "Map downloaded", "Mapa transferido")
    fun mapDownloadedContent(ctx: android.content.Context, mapName: String) = getForService(ctx, "La carte $mapName est prête hors ligne. Appuyez pour ouvrir.", "Map $mapName is ready offline. Tap to open.", "O mapa $mapName está pronto offline. Toque para abrir.")
    fun mapSiteNotInArea(ctx: android.content.Context, siteId: String) = getForService(ctx, "Le site $siteId n'est pas dans la zone affichée. Déplacez la carte vers sa ville d'abord.", "Site $siteId is not in the displayed area. Move the map to its city first.", "O site $siteId não está na área apresentada. Mova primeiro o mapa para a sua cidade.")

    // Service d'envoi SignalQuest.
    fun signalQuestUploadChannelName(ctx: android.content.Context) = getForService(ctx, "Envoi Signal Quest", "Signal Quest upload", "Envio Signal Quest")
    fun signalQuestUploadProgress(ctx: android.content.Context, current: Int, total: Int) = getForService(ctx, "Envoi en cours ($current/$total)...", "Uploading ($current/$total)...", "A enviar ($current/$total)...")
    fun signalQuestUploadRetry(ctx: android.content.Context) = getForService(ctx, "Échec réseau, nouvel essai plus tard.", "Network error, retrying later.", "Erro de rede, nova tentativa mais tarde.")
    fun signalQuestUploadSuccess(ctx: android.content.Context, success: Int, total: Int) = getForService(ctx, "$success/$total photos envoyées avec succès vers Signal Quest !", "$success/$total photos sent successfully to Signal Quest!", "$success/$total fotos enviadas com sucesso para Signal Quest!")
    fun signalQuestUploadPartial(ctx: android.content.Context, success: Int, total: Int) = getForService(ctx, "$success/$total photos envoyées vers Signal Quest. Certaines ont échoué.", "$success/$total photos sent to Signal Quest. Some failed.", "$success/$total fotos enviadas para Signal Quest. Algumas falharam.")
    fun signalQuestInvalidSite(ctx: android.content.Context) = getForService(ctx, "Site SignalQuest invalide.", "Invalid SignalQuest site.", "Site SignalQuest inválido.", "Sito SignalQuest non valido.", "Ungültiger SignalQuest-Standort.", "Sitio SignalQuest no válido.")
    fun signalQuestPhotoRequired(ctx: android.content.Context) = getForService(ctx, "Ajoutez au moins une photo avant l'envoi.", "Add at least one photo before uploading.", "Adicione pelo menos uma foto antes do envio.", "Aggiungi almeno una foto prima dell'invio.", "Füge vor dem Upload mindestens ein Foto hinzu.", "Añade al menos una foto antes del envío.")
    fun signalQuestMaxPhotos(ctx: android.content.Context, max: Int) = getForService(ctx, "Maximum $max photos par envoi.", "Maximum $max photos per upload.", "Máximo de $max fotos por envio.", "Massimo $max foto per invio.", "Maximal $max Fotos pro Upload.", "Máximo $max fotos por envío.")
    fun signalQuestUnsupportedOperator(ctx: android.content.Context) = getForService(ctx, "Opérateur SignalQuest non pris en charge.", "SignalQuest operator not supported.", "Operadora SignalQuest não suportada.", "Operatore SignalQuest non supportato.", "SignalQuest-Betreiber wird nicht unterstützt.", "Operador SignalQuest no compatible.")
    fun signalQuestUploadDisabledForOperator(ctx: android.content.Context) = getForService(ctx, "Envoi SignalQuest désactivé pour cet opérateur.", "SignalQuest upload is disabled for this operator.", "Envio SignalQuest desativado para esta operadora.", "Invio SignalQuest disattivato per questo operatore.", "SignalQuest-Upload ist für diesen Betreiber deaktiviert.", "Envío SignalQuest desactivado para este operador.")
    fun signalQuestUnsupportedPhotoFormat(ctx: android.content.Context) = getForService(ctx, "Format de photo non pris en charge.", "Photo format not supported.", "Formato de foto não suportado.", "Formato foto non supportato.", "Fotoformat wird nicht unterstützt.", "Formato de foto no compatible.")
    fun signalQuestPhotoTooLarge(ctx: android.content.Context) = getForService(ctx, "Une photo dépasse la limite de 20 Mo.", "A photo exceeds the 20 MB limit.", "Uma foto ultrapassa o limite de 20 MB.", "Una foto supera il limite di 20 MB.", "Ein Foto überschreitet das Limit von 20 MB.", "Una foto supera el límite de 20 MB.")
    fun signalQuestPreparePhotosFailed(ctx: android.content.Context) = getForService(ctx, "Impossible de préparer les photos.", "Unable to prepare photos.", "Não foi possível preparar as fotos.", "Impossibile preparare le foto.", "Fotos konnten nicht vorbereitet werden.", "No se han podido preparar las fotos.")
    fun signalQuestManifestMissing(ctx: android.content.Context) = getForService(ctx, "Manifeste d'envoi introuvable.", "Upload manifest not found.", "Manifesto de envio não encontrado.", "Manifesto di invio non trovato.", "Upload-Manifest nicht gefunden.", "Manifiesto de envío no encontrado.")
    fun signalQuestManifestInvalid(ctx: android.content.Context) = getForService(ctx, "Manifeste d'envoi invalide.", "Invalid upload manifest.", "Manifesto de envio inválido.", "Manifesto di invio non valido.", "Ungültiges Upload-Manifest.", "Manifiesto de envío no válido.")
    fun signalQuestPhotoInaccessible(ctx: android.content.Context) = getForService(ctx, "Photo inaccessible.", "Photo inaccessible.", "Foto inacessível.", "Foto non accessibile.", "Foto nicht zugänglich.", "Foto inaccesible.")

    // Widget Android et Android Auto.
    fun widgetTitle(ctx: android.content.Context) = getForService(ctx, "📍 Antennes à proximité", "📍 Nearby antennas", "📍 Antenas próximas")
    fun widgetUpdatedAt(ctx: android.content.Context, lastUpdate: String) = getForService(ctx, "Mis à jour à $lastUpdate", "Updated at $lastUpdate", "Atualizado às $lastUpdate")
    fun widgetWaitingGps(ctx: android.content.Context) = getForService(ctx, "En attente du GPS...", "Waiting for GPS...", "À espera do GPS...")
    fun widgetImmediateSearch(ctx: android.content.Context) = getForService(ctx, "Recherche immédiate...", "Searching...", "Pesquisa imediata...")
    fun unknownAddress(ctx: android.content.Context) = getForService(ctx, "Adresse inconnue", "Unknown address", "Endereço desconhecido", "Indirizzo sconosciuto", "Unbekannte Adresse", "Dirección desconocida")
    fun siteAnfrLabel(ctx: android.content.Context) = getForService(ctx, "Site ANFR", "ANFR site", "Site ANFR", "Sito ANFR", "ANFR-Standort", "Sitio ANFR")
    fun siteAnfrTitle(ctx: android.content.Context, idAnfr: String) = "${siteAnfrLabel(ctx)} $idAnfr"

    fun carConnected(ctx: android.content.Context) = getForService(ctx, "GeoTower est connecté à Android Auto.", "GeoTower is connected to Android Auto.", "GeoTower está ligado ao Android Auto.")
    fun carNearbySites(ctx: android.content.Context) = getForService(ctx, "Sites proches", "Nearby sites", "Sites próximos")
    fun carSitesAroundMe(ctx: android.content.Context) = getForService(ctx, "Sites autour de moi", "Sites around me", "Sites à minha volta")
    fun carNoSitesNearby(ctx: android.content.Context) = getForService(ctx, "Aucun site trouvé autour de votre position.", "No site found around your position.", "Nenhum site encontrado à volta da sua posição.")
    fun carRetry(ctx: android.content.Context) = getForService(ctx, "Réessayer", "Try again", "Tentar novamente")
    fun carSearchNearby(ctx: android.content.Context) = getForService(ctx, "Recherche des sites proches...", "Searching nearby sites...", "A pesquisar sites próximos...")
    fun carLocationUnavailable(ctx: android.content.Context) = getForService(ctx, "Position indisponible pour le moment.", "Position unavailable for now.", "Posição indisponível neste momento.")
    fun carLocationPermissionMessage(ctx: android.content.Context) = getForService(ctx, "Autorisez la localisation dans GeoTower sur le téléphone.", "Allow location in GeoTower on the phone.", "Autorize a localização no GeoTower no telefone.")
    fun carPermissionExplanation(ctx: android.content.Context) = getForService(ctx, "GeoTower a besoin de la localisation pour afficher les sites autour de vous.", "GeoTower needs location to show sites around you.", "O GeoTower precisa da localização para mostrar sites à sua volta.")
    fun carLocationRequired(ctx: android.content.Context) = getForService(ctx, "Localisation requise", "Location required", "Localização necessária")
    fun carOpenApp(ctx: android.content.Context) = getForService(ctx, "Ouvrir l'app", "Open app", "Abrir app")
    fun carOperators(ctx: android.content.Context) = getForService(ctx, "Opérateurs", "Operators", "Operadoras")
    fun carDistance(ctx: android.content.Context) = getForService(ctx, "Distance", "Distance", "Distância")
    fun carAddress(ctx: android.content.Context) = getForService(ctx, "Adresse", "Address", "Endereço")
    fun carCoordinates(ctx: android.content.Context) = getForService(ctx, "Coordonnées", "Coordinates", "Coordenadas")
    fun carNavigate(ctx: android.content.Context) = getForService(ctx, "Naviguer", "Navigate", "Navegar")

    fun noAntennaFound(ctx: android.content.Context, op: String) = getForService(ctx, "Aucune antenne $op trouvée à proximité.", "No $op antenna found nearby.", "Nenhuma antenne $op encontrada nas proximidades.")
    fun antennaDistance(ctx: android.content.Context, op: String, dist: String) = getForService(ctx, "Antenne $op : $dist", "$op antenna : $dist", "Antenne $op : $dist")

    fun widgetBgLocationWarning(ctx: android.content.Context) = getForService(ctx, "⚠️ Localisation arrière-plan requise", "⚠️ Background location required", "⚠️ Localização em segundo plano nécessaire")
    fun widgetBgLocationDesc(ctx: android.content.Context) = getForService(
        ctx,
        "Touchez ici, allez dans Autorisations > Localisation, puis choisissez \"Toujours autoriser\".",
        "Tap here, go to Permissions > Location, then select \"Allow all the time\".",
        "Toque aqui, vá em Permissões > Localização e selecione \"Permitir o tempo todo\".",
        "Tocca qui, vai in Autorizzazioni > Posizione, poi seleziona \"Consenti sempre\".",
        "Tippe hier, gehe zu Berechtigungen > Standort und wähle dann \"Immer zulassen\".",
        "Toca aquí, ve a Permisos > Ubicación y selecciona \"Permitir todo el tiempo\"."
    )

    // ==========================================
    // 🚨 INCIDENTS, STATUTS ET API OPÉRATEUR
    // ==========================================
    val outageAttentionDesc @Composable get() = get("Attention panne", "Outage warning", "Aviso de falha")
    val unknownOutageReason @Composable get() = get("Raison inconnue", "Unknown reason", "Motivo desconhecido")
    val outageReasonMaintenance @Composable get() = get("Maintenance", "Maintenance", "Manutenção")
    val outageReasonIncident @Composable get() = get("Incident", "Incident", "Incidente")
    val outageReasonTechnical @Composable get() = get("Intervention technique", "Technical intervention", "Intervenção técnica")

    val outageVoice @Composable get() = get("Voix", "Voice", "Voz")
    val outageData @Composable get() = get("Data", "Data", "Dados")
    val outageStatusDegraded @Composable get() = get("Dégradé", "Degraded", "Degradado")
    val outageStatusHs @Composable get() = get("Hors Service", "Out of Service", "Fora de Serviço")

    val statusTitle @Composable get() = get("Statut du site", "Site Status", "Status do site")
    val statusFunctional @Composable get() = get("Fonctionnel", "Functional", "Funcional")
    val statusOutage @Composable get() = get("En panne", "Out of service", "Em manutenção")
    val statusProject @Composable get() = get("En projet", "Planned", "Em projeto")
    val showStatusOption @Composable get() = get("Afficher le statut", "Show status", "Mostrar status")
    val shareStatusOption @Composable get() = get("Partager le statut", "Share status", "Partilhar o status")

    val serviceVoice @Composable get() = get("Voix", "Voice", "Voz")
    val serviceSms @Composable get() = get("SMS", "SMS", "SMS")
    val serviceInternet @Composable get() = get("Internet", "Internet", "Internet")
    val lastUpdatedText @Composable get() = get("Dernière mise à jour à", "Last updated at", "Última atualização às")

    val apiDetailIncident @Composable get() = get("Incident en cours", "Ongoing incident", "Incidente em curso")
    val apiDetailMaintenance @Composable get() = get("Travaux de maintenance", "Maintenance work", "Trabalhos de manutenção")

    // ==========================================
    // 🧾 VERSIONS, CARTES ET FORMATAGE
    // ==========================================
    val aboutVersionsTitle @Composable get() = get("Versions", "Versions", "Versões")
    val versionAppLabel @Composable get() = get("Version de\nl'application", "App\nversion", "Versão do\napp")
    val versionDbLabel @Composable get() = get("Version de la base\nde données", "Database\nversion", "Versão da base\nde dados")
    val versionWeeklyLabel @Composable get() = get("Données\nhebdomadaires", "Weekly\ndata", "Dados\nsemanais")
    val versionMonthlyLabel @Composable get() = get("Données\nmensuelles", "Monthly\ndata", "Dados\nmensais")
    val versionHsLabel @Composable get() = get("Données des\nsites HS", "HS sites\ndata", "Dados dos\nsites HS")
    val versionTimeAt @Composable get() = get("à", "at", "às")

    fun formatMapName(rawName: String): String {
        val cleanName = rawName.replace(".map", "", ignoreCase = true)
        return when (cleanName.lowercase()) {
            "alsace" -> "Alsace"
            "aquitaine" -> "Aquitaine"
            "auvergne" -> "Auvergne"
            "basse-normandie" -> "Basse-Normandie"
            "bourgogne" -> "Bourgogne"
            "bretagne" -> "Bretagne"
            "centre" -> "Centre-Val de Loire"
            "champagne-ardenne" -> "Champagne-Ardenne"
            "corse" -> "Corse"
            "franche-comte" -> "Franche-Comté"
            "guadeloupe" -> "Guadeloupe"
            "guyane" -> "Guyane"
            "haute-normandie" -> "Haute-Normandie"
            "ile-de-france" -> "Île-de-France"
            "languedoc-roussillon" -> "Languedoc-Roussillon"
            "limousin" -> "Limousin"
            "lorraine" -> "Lorraine"
            "martinique" -> "Martinique"
            "mayotte" -> "Mayotte"
            "midi-pyrenees" -> "Midi-Pyrénées"
            "nord-pas-de-calais" -> "Nord-Pas-de-Calais"
            "pays-de-la-loire" -> "Pays de la Loire"
            "picardie" -> "Picardie"
            "poitou-charentes" -> "Poitou-Charentes"
            "provence-alpes-cote-d-azur", "provence-alpes-cote-d-azur.map" -> "Provence-Alpes-Côte d'Azur"
            "reunion" -> "La Réunion"
            "rhone-alpes" -> "Rhône-Alpes"
            else -> cleanName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        }
    }

    @Composable
    fun formatMonthlyVersion(rawName: String): String {
        if (rawName.isBlank() || rawName == "-" || rawName == aboutDownloadNewDatabase) return rawName
        val regex = Regex("^(\\d{4})(\\d{2})\\d{2}.*")
        val match = regex.find(rawName)
        if (match != null) {
            val year = match.groupValues[1]
            val monthName = getMonthName(match.groupValues[2])
            if (monthName.isNotEmpty()) return "$monthName $year"
        }
        return rawName
    }

    @Composable
    fun getMonthName(monthIndex: String): String {
        return when (monthIndex) {
            "01" -> get("Janvier", "January", "Janeiro")
            "02" -> get("Février", "February", "Fevereiro")
            "03" -> get("Mars", "March", "Março")
            "04" -> get("Avril", "April", "Abril")
            "05" -> get("Mai", "May", "Maio")
            "06" -> get("Juin", "June", "Junho")
            "07" -> get("Juillet", "July", "Julho")
            "08" -> get("Août", "August", "Agosto")
            "09" -> get("Septembre", "September", "Setembro")
            "10" -> get("Octobre", "October", "Outubro")
            "11" -> get("Novembre", "November", "Novembro")
            "12" -> get("Décembre", "December", "Dezembro")
            else -> ""
        }
    }

    @Composable
    fun formatWeeklyVersionWithWeekNumber(dateStr: String): String {
        if (dateStr.isBlank() || dateStr == "-" || !dateStr.contains("/")) return dateStr
        val weekWord = get("Semaine", "Week", "Semana")
        val locale = currentJavaLocale()
        return try {
            val format = java.text.SimpleDateFormat("dd/MM/yyyy", locale)
            val date = format.parse(dateStr)
            if (date != null) {
                val cal = java.util.Calendar.getInstance(locale)
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.minimalDaysInFirstWeek = 4
                cal.time = date
                val weekNumber = cal.get(java.util.Calendar.WEEK_OF_YEAR)
                "$weekWord $weekNumber\n$dateStr"
            } else dateStr
        } catch (e: Exception) { dateStr }
    }

    // ==========================================
    // 🚀 SPEEDTEST (Signal Quest)
    // ==========================================
    val showSpeedtestLabel @Composable get() = get("Afficher le meilleur Speedtest", "Show best Speedtest", "Mostrar o melhor Speedtest")
    val speedtestTitle @Composable get() = get("Meilleur Speedtest", "Best Speedtest", "Melhor Speedtest")
    val speedtestDownload @Composable get() = get("Descendant", "Download", "Download")
    val speedtestUpload @Composable get() = get("Montant", "Upload", "Upload")
    val speedtestPing @Composable get() = get("Ping", "Ping", "Ping")
    val speedtestNoData @Composable get() = get("Aucun test de débit répertorié pour ce site.", "No speedtest recorded for this site.", "Nenhum teste de velocidade registado para este site.")
}
