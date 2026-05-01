package fr.geotower.utils

import androidx.compose.runtime.Composable

object AppStrings {
    // Lecture en temps réel de la langue choisie dans les paramètres
    private val language: String
        @Composable get() = AppConfig.appLanguage.value

    // Fonction utilitaire pour 3 langues
    @Composable
    fun get(fr: String, en: String, pt: String): String {
        val currentLang = AppConfig.appLanguage.value

        // Si "Système" est sélectionné, on récupère le code langue du téléphone
        val langToCheck = if (currentLang == "Système") {
            java.util.Locale.getDefault().language
        } else {
            currentLang
        }

        return when {
            langToCheck == "Français" || langToCheck == "fr" -> fr
            langToCheck == "Português" || langToCheck == "pt" -> pt
            // Anglais par défaut
            else -> en
        }
    }

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

    // ==========================================
    // ⚙️ PARAMÈTRES
    // ==========================================
    val appearance @Composable get() = get("Apparence", "Appearance", "Aparência")
    val mapping @Composable get() = get("Cartographie", "Mapping", "Cartografia")
    val preferences @Composable get() = get("Préférences", "Preferences", "Preferências")
    val system @Composable get() = get("Système", "System", "Sistema")
    val database @Composable get() = get("Base de données", "Database", "Base de dados")

    val themeAuto @Composable get() = get("Auto", "Auto", "Auto")
    val themeLight @Composable get() = get("Clair", "Light", "Claro")
    val themeDark @Composable get() = get("Sombre", "Dark", "Escuro")
    val oledMode @Composable get() = get("Mode OLED", "OLED Mode", "Modo OLED")
    val oledDesc @Composable get() = get("Noir pur", "Pure black", "Preto puro")
    val appIcon @Composable get() = get("Icône de l'application", "App Icon", "Ícone da aplicação")
    val restartToApply @Composable get() = get("L'app redémarrera pour appliquer le changement.", "The app will restart to apply the change.", "O aplicativo será reiniciado para aplicar a alteração.")
    val systemLanguage @Composable get() = get("Langage système", "System language", "Idioma do sistema")

    val mapIgn @Composable get() = get("IGN (Gouv)", "IGN (Gov)", "IGN (Gov)")
    val mapOsm @Composable get() = get("OpenStreetMap", "OpenStreetMap", "OpenStreetMap")
    val mapStyle @Composable get() = get("Style de carte", "Map Style", "Estilo do mapa")
    val mapSat @Composable get() = get("Sat", "Sat", "Sat")

    val navMode @Composable get() = get("Mode de navigation dans les paramètres", "Navigation mode in settings", "Modo de navegação nas configurações")
    val navScroll @Composable get() = get("Défilement continu", "Continuous scroll", "Deslocamento contínuo")
    val navPages @Composable get() = get("Système par pages", "Page system", "Sistema de páginas")
    val navScrollTitle @Composable get() = get("Défilant", "Scrolling", "Rolagem")
    val navScrollDesc @Composable get() = get("Toutes les options sur une page", "All options on one page", "Todas as opções numa página")
    val navPagesTitle @Composable get() = get("Pages", "Pages", "Páginas")
    val navPagesDesc @Composable get() = get("Afficher une catégorie à la fois", "Show one category at a time", "Mostrar uma categoria de cada vez")

    val oneUiInterface @Composable get() = get("Interface One UI", "One UI Interface", "Interface One UI")
    val oneUiDesc @Composable get() = get("Activer le design Samsung", "Enable Samsung design", "Ativar o design Samsung")
    val scrollBlur @Composable get() = get("Flou de défilement", "Scroll Blur", "Desfoque de rolagem")
    val scrollBlurDesc @Composable get() = get("Activer ou désactiver l'effet de flou", "Enable or disable blur effect", "Ativar ou desativar o efeito de desfoque")

    val defaultOperator @Composable get() = get("Opérateur par défaut", "Default Operator", "Operadora padrão")
    val appLanguageLabel @Composable get() = get("Langue de l'application", "App Language", "Idioma da aplicação")
    val none @Composable get() = get("Aucun", "None", "Nenhum")
    val select @Composable get() = get("Sélectionner", "Select", "Selecionar")

    @Composable
    fun current(value: String) = get("Actuel : $value", "Current : $value", "Atual : $value")
    val validate @Composable get() = get("Valider", "Validate", "Validar")

    val managePermissions @Composable get() = get("Gérer les permissions", "Manage Permissions", "Gerir permissões")
    val permissionsDesc @Composable get() = get("Localisation et Notifications", "Location and Notifications", "Localização e Notificações")

    val offlineMode @Composable get() = get("Mode hors-ligne", "Offline Mode", "Modo offline")
    val offlineDesc @Composable get() = get("Télécharge toute la base pour utiliser la liste sans réseau. Attention : fichier volumineux.", "Download the entire database to use the list offline. Warning : large file.", "Transfere toda a base de dados para utilizar a lista offline. Aviso : ficheiro grande.")
    val downloadAntennas @Composable get() = get("Télécharger les antennes", "Download antennas", "Transferir antenas")
    val cancelDownload @Composable get() = get("Annuler le téléchargement", "Cancel download", "Cancelar transferência")
    val downloadSuccess @Composable get() = get("Base de données téléchargée !", "Database downloaded!", "Base de dados transferida!")
    val downloadError @Composable get() = get("Erreur de téléchargement", "Download error", "Erro de transferência")

    val pagesVisibilityTitle @Composable get() = get("Choix des pages à afficher", "Choice of pages to display", "Escolha das páginas a apresentar")
    val pagesVisibilityDesc @Composable get() = get("Personnaliser l'écran d'accueil", "Customize home screen", "Personalizar ecrã inicial")
    val pagesVisibilitySheetTitle @Composable get() = get("Choisissez quels écrans vous souhaitez afficher", "Choose which screens you want to display", "Escolha quais ecrãs deseja exibir")

    val pageNearby @Composable get() = get("Antennes à proximité", "Nearby antennas", "Antenas próximas")
    val pageMap @Composable get() = get("Carte des Antennes", "Antenna map", "Mapa de antenas")
    val pageCompass @Composable get() = get("Boussole", "Compass", "Bússola")
    val pageStats @Composable get() = get("Statistiques", "Statistics", "Estatísticas")
    val dragToReorderHint @Composable get() = get("(Appui long et glissé pour déplacer)", "(Long press and drag to reorder)", "(Pressione e segure para arrastar)")
    val resetOrder @Composable get() = get("Réinitialiser l'ordre", "Reset order", "Redefinir ordem")
    @Composable
    fun downloadProgress(progress: Int) = get("Téléchargement : $progress %", "Downloading : $progress %", "A transferir : $progress %")

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
    val moveUp @Composable get() = get("Monter", "Move up", "Subir")
    val moveDown @Composable get() = get("Descendre", "Move down", "Descer")

    val mapMapLibre @Composable get() = get("MapLibre", "MapLibre", "MapLibre")
    val mapTopo @Composable get() = get("OpenTopoMap", "OpenTopoMap", "OpenTopoMap")
    val mapOfflineLayer @Composable get() = get("Hors-ligne", "Offline", "Offline")
    val noMapFileNotFound @Composable get() = get("Aucun fichier .map trouvé", "No .map file found", "Nenhum arquivo .map encontrado")
    // --- PERSONNALISATION DES PAGES ---
    val pagesCustomizationTitle @Composable get() = get("Personnalisation des pages", "Pages customization", "Personalização das páginas")
    val pagesCustomizationDesc @Composable get() = get("Personnalisez l'affichage des différentes pages de l'application", "Customize the display of the different pages of the application", "Personalize a exibição das diferentes páginas do aplicativo")

    val startupPageSettings @Composable get() = get("Page de démarrage", "Startup page", "Página de inicialização")
    val pageHomeSettings @Composable get() = get("Page d'accueil", "Home page", "Página inicial")
    val pageNearbySettings @Composable get() = get("Antennes à proximité", "Nearby antennas", "Antenas próximas")
    val pageMapSettings @Composable get() = get("Carte des antennes", "Antennas map", "Mapa de antenas")
    val pageCompassSettings @Composable get() = get("Boussole", "Compass", "Bússola")
    val statsGroupTitle @Composable get() = get("Statistiques", "Statistics", "Estatísticas")
    val nearbySearchOption @Composable get() = get("Barre de recherche", "Search bar", "Barra de pesquisa")
    val nearbySitesOption @Composable get() = get("Sites les plus proches", "Nearest sites", "Locais mais próximos")
    val searchRadiusTitle @Composable get() = get("Rayon de recherche", "Search radius", "Raio de pesquisa")
    val compassLocationOption @Composable get() = get("Lieu", "Location", "Local")
    val compassGpsOption @Composable get() = get("Localisation", "Coordinates", "Coordenadas")
    val compassAccuracyOption @Composable get() = get("Précision", "Accuracy", "Precisão")
    val mapLocationOption @Composable get() = get("Bouton de localisation", "Location button", "Botão de localização")
    val mapZoomOption @Composable get() = get("Boutons de zoom", "Zoom buttons", "Botões de zoom")
    val mapToolboxOption @Composable get() = get("Toolbox (Outils)", "Toolbox", "Ferramentas")
    val mapCompassOption @Composable get() = get("Boussole de la carte", "Map compass", "Bússola do mapa")
    val mapScaleOption @Composable get() = get("Échelle de la carte", "Map scale", "Escala do mapa")
    val mapAttributionOption @Composable get() = get("Crédits (Attribution)", "Credits (Attribution)", "Créditos (Atribuição)")
    val resetToDefault @Composable get() = get("Rétablir les paramètres par défaut", "Reset to default settings", "Restaurar configurações padrão")
    val pageSupportSettings @Composable get() = get("Détail du pylône (Support)", "Support details", "Detalhes do suporte")
    val pageSiteSettings @Composable get() = get("Détail de l'antenne (Site)", "Site details", "Detalhes do site")
    val supportMapOption @Composable get() = get("Mini-carte", "Mini-map", "Mini-mapa")
    val supportDetailsOption @Composable get() = get("Détails du pylône", "Support details", "Detalhes do suporte")
    val supportPhotosOption @Composable get() = get("Photos communautaires", "Community photos", "Fotos da comunidade")
    val supportNavOption @Composable get() = get("Bouton Naviguer", "Navigate button", "Botão Navegar")
    val supportShareOption @Composable get() = get("Bouton Partager", "Share button", "Botão Compartilhar")
    val supportOperatorsOption @Composable get() = get("Liste des opérateurs", "Operators list", "Lista de operadoras")
    val siteOperatorOption @Composable get() = get("Bandeau Opérateur", "Operator banner", "Banner da operadora")
    val siteBearingHeightOption @Composable get() = get("Cap et Hauteur", "Bearing and Height", "Rumo e Altura")
    val siteMapOption @Composable get() = get("Mini-carte", "Mini-map", "Mini-mapa")
    val siteSupportDetailsOption @Composable get() = get("Détails du pylône", "Support details", "Detalhes do suporte")
    val sitePhotosOption @Composable get() = get("Photos communautaires", "Community photos", "Fotos da comunidade")
    val sitePanelHeightsOption @Composable get() = get("Hauteurs des panneaux", "Panel heights", "Alturas dos painéis")
    val siteIdsOption @Composable get() = get("Identifiants", "Identifiers", "Identificadores")
    val siteNavOption @Composable get() = get("Bouton Naviguer", "Navigate button", "Botão Navegar")
    val siteShareOption @Composable get() = get("Bouton Partager", "Share button", "Botão Compartilhar")
    val siteDatesOption @Composable get() = get("Dates d'activation", "Activation dates", "Datas de ativação")
    val siteAddressOption @Composable get() = get("Adresse et Coordonnées", "Address & Coordinates", "Endereço e Coordenadas")
    val siteFreqsOption @Composable get() = get("Fréquences, Spectres et Azimuts", "Frequencies, Spectrum & Azimuths", "Frequências, Espectros e Azimutes")
    val siteLinksOption @Composable get() = get("Liens externes", "External links", "Links externos")
    val externalLinksSettingsTitle @Composable get() = get("Liens externes & Communautés", "External links & Communities", "Links externos e Comunidades")
    val externalLinksSettingsDesc @Composable get() = get("Gérer l'ordre et l'affichage des raccourcis", "Manage the order and display of shortcuts", "Gerir a ordem e apresentação dos atalhos")
    val resetSettings @Composable get() = get("Réinitialiser les paramètres", "Reset settings", "Redefinir configurações")
    val resetWarningTitle @Composable get() = get("Attention", "Warning", "Aviso")
    val resetWarningDesc @Composable get() = get("Êtes-vous sûr de vouloir rétablir les paramètres par défaut ? Cela supprimera tous les réglages que vous avez faits dans l'application.", "Are you sure you want to restore default settings? This will delete all settings you have made in the app.", "Tem certeza de que deseja restaurar as configurações padrão? Isso excluirá todas as configurações que você fez no aplicativo.")
    val yes @Composable get() = get("Oui", "Yes", "Sim")
    val no @Composable get() = get("Non", "No", "Não")
    val siteAnfrOption @Composable get() = get("Bouton data.gouv.fr", "data.gouv.fr Button", "Botão data.gouv.fr")
    val offlineMessage @Composable get() = get("Vous êtes hors ligne", "You are offline", "Você está offline")
    val pageHomeLogoSettings @Composable get() = get("Logo de l'application", "App logo", "Logótipo da aplicação")
    val homeLogoSettingTitle @Composable get() = get("Logo de la page d'accueil", "Home page logo", "Logótipo da página inicial")
    val logoApp @Composable get() = get("Application", "Application", "Aplicação")
    val logoOrange @Composable get() = get("Orange", "Orange", "Orange")
    val logoSfr @Composable get() = get("SFR", "SFR", "SFR")
    val logoBouygues @Composable get() = get("Bouygues", "Bouygues", "Bouygues")
    val logoFree @Composable get() = get("Free", "Free", "Free")
    val calcDbSize @Composable get() = get("Calcul de la taille...", "Calculating size...", "A calcular o tamanho...")
    val unknownSize @Composable get() = get("Taille inconnue", "Unknown size", "Tamanho desconhecido")

    val showSpeedometer @Composable get() = get("Compteur de vitesse", "Speedometer", "Velocímetro")
    val showSpeedometerDesc @Composable get() = get("Afficher la vitesse sur la carte", "Show speed on the map", "Mostrar a velocidade no mapa")
    val siteFreqFiltersTitle @Composable get() = get("Filtres des fréquences de l'antenne", "Antenna frequency filters", "Filtros de frequência da antena")
    val shareMapAzimuthsOption @Composable get() = get("Afficher les azimuts", "Show azimuths", "Mostrar azimutes")

    val liveNotificationTitle @Composable get() = get("Notification Live", "Live Notification", "Notificação ao vivo")
    val liveNotificationDesc @Composable get() = get("Activer les notifications en temps réel", "Enable real-time notifications", "Ativar notificações em tempo real")
    val liveNotificationPromotedDisabled @Composable get() = get("Live Updates désactivées dans Android", "Live Updates disabled in Android", "Live Updates desativadas no Android")
    val liveNotificationPromotedSettings @Composable get() = get("Activer dans Android", "Enable in Android", "Ativar no Android")
    val updateNotifSettingTitle @Composable get() = get("Notifications de mise à jour", "Update notifications", "Notificações de atualização")
    val updateNotifSettingDesc @Composable get() = get("Être alerté quand une nouvelle base est disponible", "Get alerted when a new database is available", "Ser alertado quando uma nova base de dados estiver disponível")
    val liveNotificationRequiresOp @Composable get() = get("Nécessite de choisir un opérateur par défaut", "Requires choosing a default operator", "Requer a escolha de uma operadora padrão")

    val liveTrackingTitle @Composable get() = get("Recherche d'antennes en direct", "Live antenna tracking", "Rastreamento de antenas ao vivo")
    val stopLiveTracking @Composable get() = get("Quitter", "Stop", "Sair")
    val searchingAntenna @Composable get() = get("Recherche de l'antenne la plus proche...", "Searching for nearest antenna...", "Buscando a antena mais próxima...")

    // ✅ NOUVEAU : Messages d'avertissement pour les filtres
    val minOneTechnoWarning @Composable get() = get("Vous devez garder au moins une technologie mobile (2G, 3G, 4G ou 5G).", "You must keep at least one mobile technology (2G, 3G, 4G, or 5G).", "Deve manter pelo menos uma tecnologia móvel (2G, 3G, 4G ou 5G).")
    val minOneFreqWarning @Composable get() = get("Vous devez garder au moins une fréquence.", "You must keep at least one frequency.", "Deve manter pelo menos uma frequência.")
    val anfrDatabaseFrom @Composable get() = get("Base de données hebdomadaire actuellement téléchargée :", "Weekly database currently being downloaded :", "Base de dados semanal atualmente a ser descarregada :")

    val deleteData @Composable get() = get("Supprimer les données", "Delete data", "Eliminar dados")
    val deleteDbWarningTitle @Composable get() = get("Attention", "Warning", "Atenção")
    val deleteDbWarningDesc @Composable get() = get("Êtes-vous sûr de vouloir supprimer la base de données ?", "Are you sure you want to delete the database?", "Tem certeza de que deseja excluir a base de dados?")

    val offlineMapsTitle @Composable get() = get("Cartes Hors-Ligne", "Offline Maps", "Mapas Offline")
    val offlineMapsDesc @Composable get() = get("Téléchargez des cartes de la France pour naviguer sans réseau.", "Download maps of France to navigate without an internet connection.", "Descarregue mapas de França para navegar sem rede.")
    val mapExtracting @Composable get() = get("Extraction en cours...", "Extracting...", "A extrair...")
    val mapDeleteWarningTitle @Composable get() = get("Supprimer la carte ?", "Delete map?", "Eliminar mapa?")
    val mapDeleteWarningDesc @Composable get() = get("Voulez-vous vraiment supprimer cette carte de votre appareil ?", "Do you really want to delete this map from your device?", "Tem a certeza de que pretende eliminar este mapa do seu dispositivo?")
    val downloadAll @Composable get() = get("Tout télécharger", "Download All", "Descarregar tudo")
    val deleteAllMaps @Composable get() = get("Tout supprimer", "Delete All", "Eliminar tudo")
    val deleteAllMapsWarningTitle @Composable get() = get("Supprimer toutes les cartes ?", "Delete all maps?", "Eliminar todos os mapas?")
    val deleteAllMapsWarningDesc @Composable get() = get("Voulez-vous vraiment supprimer toutes les cartes téléchargées ?", "Do you really want to delete all downloaded maps?", "Tem a certeza de que pretende eliminar todos os mapas descarregados?")

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

    val downloadFailed @Composable get() = get("Échec du téléchargement ❌", "Download failed ❌", "Falha na transferência ❌")

    // ==========================================
    // 📡 ANTENNES À PROXIMITÉ
    // ==========================================
    val nearEmittersTitle @Composable get() = get("Émetteurs à proximité", "Nearby Emitters", "Emissores próximos")
    val searchPlaceholder @Composable get() = get("Filtrer (ex: Orange, 75001...)", "Filter (e.g., Orange, 75001...)", "Filtrar (ex: Orange, 75001...)")
    @Composable
    fun sitesFound(count: Int) = get("$count sites trouvés", "$count sites found", "$count locais encontrados")
    val searchGps @Composable get() = get("Recherche position GPS...", "Searching for GPS position...", "À procura de posição GPS...")
    val noSitesFound @Composable get() = get("Aucun site trouvé.", "No sites found.", "Nenhum local encontrado.")
    val loadMoreSites @Composable get() = get("Afficher plus de sites", "Load more sites", "Carregar mais sites")
    val expandSearchArea @Composable get() = get("Élargir la zone de recherche", "Expand search area", "Expandir a área de pesquisa")
    val searchingNewSites @Composable get() = get("Recherche de nouveaux sites...", "Searching for new sites...", "Procurando novos sites...")
    val openApp @Composable get() = get("Ouvrir l'application", "Open application", "Abrir a aplicação")

    val supportDetailsTitle @Composable get() = get("Détails du support", "Support details", "Detalhes do suporte")
    val supportNature @Composable get() = get("Nature du support", "Support nature", "Natureza do suporte")
    val owner @Composable get() = get("Propriétaire", "Owner", "Proprietário")
    val antennaType @Composable get() = get("Type d'antenne", "Antenna type", "Tipo de antena")

    // ==========================================
    // 📸 PHOTOS COMMUNAUTAIRES (SIGNAL QUEST)
    // ==========================================
    val signalQuestUploadPrompt @Composable get() = get(
        "Vous pouvez envoyer vos photos directement depuis cette application",
        "You can send your photos directly from this app",
        "Pode enviar as suas fotos diretamente a partir desta aplicação"
    )
    val uploadPhotosPrompt @Composable get() = get("Envoyer des photos", "Upload photos", "Enviar fotos")
    val unknownAuthor @Composable get() = get("Auteur inconnu", "Unknown author", "Autor desconhecido")
    val previous @Composable get() = get("Précédent", "Previous", "Anterior")
    val next @Composable get() = get("Suivant", "Next", "Próximo")

    // ==========================================
    // 🧭 BOUSSOLE
    // ==========================================
    val searching @Composable get() = get("Recherche...", "Searching...", "A pesquisar...")
    val unknown @Composable get() = get("Inconnu", "Unknown", "Desconhecido")

    // --- NOUVEAUX AJOUTS ---
    val latShort @Composable get() = get("LAT", "LAT", "LAT")
    val lonShort @Composable get() = get("LONG", "LONG", "LONG")
    val accuracy @Composable get() = get("PRÉCISION", "ACCURACY", "PRECISÃO")
    @Composable
    fun pylonsInDirection(count: Int) = get("$count pylônes dans cette direction", "$count pylons in this direction", "$count pilões nesta direção")

    val nearbyAntennasAzimuth @Composable get() = get("Antennes à proximité", "Nearby antennas", "Antenas nas proximidades")
    val supportPrefix @Composable get() = get("Support", "Support", "Suporte")
    // ==========================================
    // 🆕 ONBOARDING
    // ==========================================
    val btnAuthorize @Composable get() = get("Autoriser", "Authorize", "Autorizar")
    val btnNext @Composable get() = get("Suivant", "Next", "Seguinte")
    val btnLetsGo @Composable get() = get("C'est parti !", "Let's go!", "Vamos lá!")

    val welcomeTitle @Composable get() = get("Bienvenue !", "Welcome!", "Bem-vindo!")
    val welcomeDesc @Composable get() = get("Pour fonctionner correctement, GeoTower a besoin de quelques autorisations :", "To work properly, GeoTower needs a few permissions :", "Para funcionar corretamente, a GeoTower precisa de algumas permissões :")
    val permLocation @Composable get() = get("Localisation", "Location", "Localização")
    val permLocationDesc @Composable get() = get("Nécessaire pour afficher votre position sur la carte et trouver les antennes autour de vous.", "Necessary to display your position on the map and find antennas around you.", "Necessário para apresentar a sua posição no mapa e encontrar antenas à sua volta.")
    val permNotifications @Composable get() = get("Notifications", "Notifications", "Notificações")
    val permNotificationsDesc @Composable get() = get("Pour vous prévenir lors de mises à jour importantes des données ou d'alertes.", "To notify you of important data updates or alerts.", "Para o notificar de atualizações de dados importantes ou alertas.")

    val themeDesc @Composable get() = get("Choisissez le style qui vous convient.", "Choose the style that suits you.", "Escolha o estilo que mais lhe convém.")
    val oledTitle @Composable get() = get("Mode OLED (Noir Pur)", "OLED Mode (Pure Black)", "Modo OLED (Preto Puro)")
    val oledSubtitle @Composable get() = get("Économise la batterie", "Saves battery", "Poupa bateria")
    val blurTitle @Composable get() = get("Flou de défilement", "Scroll Blur", "Desfoque de rolagem")
    val blurSubtitle @Composable get() = get("Activer ou désactiver le flou (plus énergivore)", "Enable or disable blur (consumes more battery)", "Ativar ou desativar o desfoque (consome mais bateria)")

    val mapDesc @Composable get() = get("Quel fournisseur de carte préférez-vous ?", "Which map provider do you prefer?", "Qual fornecedor de mapas prefere?")
    val osmFree @Composable get() = get("OSM (Libre)", "OSM (Free)", "OSM (Livre)")

    val prefDesc @Composable get() = get("Configurez votre opérateur principal pour faciliter l'utilisation des outils de mesure sur la carte.", "Configure your main operator to make it easier to use the measurement tools on the map.", "Configure a sua operadora principal para facilitar a utilização das ferramentas de medição no mapa.")
    val selectOperator @Composable get() = get("Sélection de votre opérateur principal", "Select your main operator", "Selecione a sua operadora principal")
    val oneUiSubtitle @Composable get() = get("Activer le design Samsung (bulles arrondies)", "Enable Samsung design (rounded bubbles)", "Ativar o design Samsung (bolhas arredondadas)")
    val chooseLanguage @Composable get() = get("Choisissez la langue de l'application.", "Choose the application language.", "Escolha o idioma do aplicativo.")

    val warningNoOpTitle @Composable get() = get("Aucun opérateur sélectionné", "No operator selected", "Nenhuma operadora selecionada")
    val warningNoOpDesc @Composable get() = get("Vous n'avez pas choisi d'opérateur par défaut. Les outils de filtrage sur la carte seront désactivés.\n\nVoulez-vous vraiment continuer ?", "You have not chosen a default operator. The filtering tools on the map will be disabled.\n\nDo you really want to continue?", "Não escolheu uma operadora por defeito. As ferramentas de filtragem no mapa serão desativadas.\n\nTem a certeza de que pretende continuar?")
    val warningContinue @Composable get() = get("Continuer quand même", "Continue anyway", "Continuar mesmo assim")
    val warningChooseOp @Composable get() = get("Choisir un opérateur", "Choose an operator", "Escolher uma operadora")

    // --- Style d'affichage ---
    val displayStyleTitle @Composable get() = get("Style d'affichage", "Display Style", "Estilo de exibição")
    val displayStyleFullScreen @Composable get() = get("Plein écran", "Full screen", "Ecrã inteiro")
    val displayStyleFullScreenDesc @Composable get() = get("Affichage du détail du support et du détail du site individuellement en plein écran", "Display support details and site details individually in full screen", "Exibição de detalhes de suporte e detalhes do site individualmente em tela cheia")
    val displayStyleSplit @Composable get() = get("Fractionné", "Split", "Dividido")
    val displayStyleSplitDesc @Composable get() = get("Affichage fractionné du détail du support et détail du site avec le détail du support à gauche et détail du site à droite.", "Split display of support details and site details with support details on the left and site details on the right", "Exibição dividida de detalhes de suporte e detalhes do site com detalhes de suporte à esquerda e detalhes do site à direita.")

    // ==========================================
    // 🎉 SUCCÈS TÉLÉCHARGEMENT (ONBOARDING)
    // ==========================================
    val dbSuccessTitle @Composable get() = get("Téléchargement terminé !", "Download finished!", "Transferência concluída!")
    val dbSuccessDesc @Composable get() = get(
        "La base de données hors-ligne a été installée avec succès. L'application est prête à fonctionner à pleine vitesse.",
        "The offline database has been successfully installed. The application is ready to run at full speed.",
        "A base de dados offline foi instalada com sucesso. A aplicação está pronta para funcionar a toda a velocidade."
    )
    val btnContinue @Composable get() = get("Continuer", "Continue", "Continuar")

    // ==========================================
    // ℹ️ À PROPOS
    // ==========================================
    val aboutPresentation @Composable get() = get("Présentation", "Presentation", "Apresentação")
    val aboutNew @Composable get() = get("Nouveautés", "What's New", "Novidades")
    val aboutSources @Composable get() = get("Sources de données", "Data Sources", "Fontes de dados")
    val aboutDev @Composable get() = get("Développement", "Development", "Desenvolvimento")
    val aboutIntro @Composable get() = get("GeoTower vous permet de localiser les antennes relais autour de vous et d'identifier les technologies disponibles.", "GeoTower allows you to locate cell towers around you and identify available technologies.", "A GeoTower permite-lhe localizar torres de celular à sua volta e identificar as tecnologias disponíveis.")
    val lastChanges @Composable get() = get("Dernières modifications", "Last changes", "Últimas modificações")
    val devCredit @Composable get() = get("Développé par Julien, Gemini et les contributeurs de GitHub 😉", "Developed by Julien, Gemini, and GitHub contributors 😉", "Desenvolvido por Julien, Gemini e os colaboradores do GitHub 😉")
    val srcAntennas @Composable get() = get("Données Antennes", "Antenna Data", "Dados de Antenas")
    val srcAntennasDesc @Composable get() = get("Agence Nationale des Fréquences (ANFR).\nDonnées issues de Cartoradio (Open Data).", "National Frequency Agency (ANFR).\nData from Cartoradio (Open Data).", "Agência Nacional de Frequências (ANFR).\nDados do Cartoradio (Open Data).")
    val srcIgn @Composable get() = get("Fond de carte IGN", "IGN Basemap", "Mapa base IGN")
    val srcIgnDesc @Composable get() = get("© IGN - Institut National de l'Information Géographique et Forestière.", "© IGN - National Institute of Geographic and Forest Information.", "© IGN - Instituto Nacional de Informação Geográfica e Florestal.")
    val srcOsm @Composable get() = get("Fond de carte OSM", "OSM Basemap", "Mapa base OSM")
    val srcOsmDesc @Composable get() = get("© les contributeurs d'OpenStreetMap.", "© OpenStreetMap contributors.", "© os colaboradores do OpenStreetMap.")
    val srcInspo @Composable get() = get("Inspiration & Sources Externes", "Inspiration & External Sources", "Inspiração e Fontes Externas")
    val srcInspoDesc @Composable get() = get("• © CellularFR développé par Luis Baker\n• © Signal Quest développé par Alexandre Germain\n• © RNC Mobile développé par Cédric\n• © eNB-Analytics développé par Tristan\n• © GeoRadio - L'icône alternative provient de l'application GéoRadio sur iOS développée par Hugo Martin.\n• Concept original basé sur l'application GéoRadio\n• Icône fun dessinée par Johan", "• © CellularFR developed by Luis Baker\n• © Signal Quest developed by Alexandre Germain\n• © RNC Mobile developed by Cédric\n• © eNB-Analytics developed by Tristan\n• © GeoRadio - The alternative icon comes from the GéoRadio iOS app developed by Hugo Martin.\n• Original concept based on the GéoRadio app\n• Fun icon designed by Johan", "• © CellularFR desenvolvido por Luis Baker\n• © Signal Quest desenvolvido por Alexandre Germain\n• © RNC Mobile desenvolvido por Cédric\n• © eNB-Analytics desenvolvido por Tristan\n• © GeoRadio - O ícone alternativo vem da aplicação GéoRadio para iOS desenvolvida por Hugo Martin.\n• Conceito original baseado na aplicação GéoRadio\n• Ícone divertido desenhado por Johan")

    // ==========================================
    // 🗺️ CRÉDITS CARTES (AboutScreen)
    // ==========================================
    val openAndroMapsTitle @Composable get() = get(
        "OpenAndroMaps",
        "OpenAndroMaps",
        "OpenAndroMaps"
    )
    val openAndroMapsDesc @Composable get() = get(
        "Cartes vectorielles hors-ligne et thème de rendu (Elevate).",
        "Offline vector maps and render theme (Elevate).",
        "Mapas vetoriais offline e tema de renderização (Elevate)."
    )

    // ==========================================
    // 🔒 CONFIDENTIALITÉ
    // ==========================================
    val privacyCategory @Composable get() = get("Confidentialité", "Privacy", "Privacidade")
    val yourDataTitle @Composable get() = get("Vos données", "Your data", "Os seus dados")
    val yourDataDesc @Composable get() = get(
        "GeoTower ne collecte aucune donnée personnelle. Vos réglages et favoris sont stockés uniquement sur votre appareil.",
        "GeoTower does not collect any personal data. Your settings and favorites are stored only on your device.",
        "O GeoTower não recolhe quaisquer dados pessoais. As suas definições e favoritos são guardados apenas no seu dispositivo."
    )

    // ==========================================
    // 🏢 DÉTAIL DU SUPPORT
    // ==========================================
    val supportDetailTitle @Composable get() = get("Détail du support", "Support Detail", "Detalhe do suporte")
    val noDataFound @Composable get() = get("Aucune donnée trouvée.", "No data found.", "Nenhum dado encontrado.")
    val idNumber @Composable get() = get("Numéro d'identification : ", "Identification number : ", "Número de identificação : ")
    val idCopied @Composable get() = get("Numéro d'identification copié", "Identification number copied", "Número de identificação copiado")
    val idUnavailable @Composable get() = get("Numéro indisponible pour le moment", "Number unavailable at the moment", "Número indisponível no momento")
    val comingSoon @Composable get() = get("À venir", "Coming soon", "Em breve")
    val addressLabel @Composable get() = get("Adresse : ", "Address : ", "Endereço : ")
    val notSpecified @Composable get() = get("Non spécifiée", "Not specified", "Não especificado")
    val addressCopied @Composable get() = get("Adresse copiée", "Address copied", "Endereço copiado")
    val gpsLabel @Composable get() = get("GPS : ", "GPS : ", "GPS : ")
    val coordsCopied @Composable get() = get("Coordonnées copiées", "Coordinates copied", "Coordenadas copiadas")
    val supportHeight @Composable get() = get("Hauteur du support : ", "Support height : ", "Altura do suporte : ")
    val distanceLabel @Composable get() = get("Distance : ", "Distance : ", "Distância : ")
    val fromMyPosition @Composable get() = get("de vous", "from you", "de si")
    val bearingLabel @Composable get() = get("Cap mesuré depuis l’antenne : ", "Bearing measured from the antenna : ", "Rumo medido a partir da antena : ")
    val navToSite @Composable get() = get("Naviguer vers ce site", "Navigate to this site", "Navegar para este local")
    val shareSite @Composable get() = get("Partager ce site", "Share this site", "Partilhar este local")
    @Composable
    fun operatorCount(count: Int) = get("↓ Nombre d'opérateurs : ( $count / 4 )", "↓ Number of operators : ( $count / 4 )", "↓ Número de operadoras : ( $count / 4 )")
    val shareAs @Composable get() = get("Partager en...", "Share as...", "Partilhar como...")
    val lightModeDesc @Composable get() = get("Idéal pour les emails ou messages", "Ideal for emails or messages", "Ideal para e-mails ou mensagens")
    val darkModeDesc @Composable get() = get("Idéal pour les réseaux sociaux (Twitter, Discord)", "Ideal for social media (Twitter, Discord)", "Ideal para redes sociais (Twitter, Discord)")
    val openRouteWith @Composable get() = get("Ouvrir l'itinéraire avec...", "Open route with...", "Abrir rota com...")
    val installedApp @Composable get() = get("Application installée", "Installed application", "Aplicação instalada")
    val installedAppDesc @Composable get() = get("Ouvrir avec Waze, Maps, OsmAnd...", "Open with Waze, Maps, OsmAnd...", "Abrir com Waze, Maps, OsmAnd...")
    val onInternet @Composable get() = get("Sur internet", "On the internet", "Na internet")
    val onInternetDesc @Composable get() = get("Ouvrir dans le navigateur web", "Open in web browser", "Abrir no navegador web")
    val noGpsApp @Composable get() = get("Aucune application GPS trouvée.", "No GPS application found.", "Nenhuma aplicação de GPS encontrada.")
    val navWith @Composable get() = get("Naviguer avec...", "Navigate with...", "Navegar com...")
    val shareSiteVia @Composable get() = get("Partager le site via...", "Share site via...", "Partilhar o local via...")
    val implementation @Composable get() = get("Implémentation : ", "Implementation : ", "Implementação : ")
    val lastModification @Composable get() = get("Dernière modification : ", "Last modification : ", "Última modificação : ")
    val generatedBy @Composable get() = get("Généré via l'application GeoTower", "Generated via the GeoTower app", "Gerado através da aplicação GeoTower")
    val operatorsTitle @Composable get() = get("Opérateurs", "Operators", "Operadoras")

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

    // Fréquences précises et Spectre
    val preciseFrequencies @Composable get() = get("Fréquences", "Frequencies", "Frequências")
    val totalspectrum @Composable get() = get("Spectre total", "Total spectrum", "Espectro total")
    val spectrumTitle @Composable get() = get("Spectre", "Spectrum", "Espectro")
    val spectrumByBand @Composable get() = get("Spectre par plage de fréquence", "Spectrum by frequency band", "Espectro por faixa de frequência")

    // ==========================================
    // 🗺️ CARTE
    // ==========================================
    val searchCityOrId @Composable get() = get("Ville, adresse ou ID de site...", "City, address or site ID...", "Cidade, endereço ou ID do local...")
    val siteNotInArea @Composable get() = get("n'est pas dans la zone affichée. Déplacez la carte vers sa ville d'abord.", "is not in the displayed area. Move the map to its city first.", "não está na área apresentada. Mova o mapa para a sua cidade primeiro.")
    val locationNotFound @Composable get() = get("Lieu introuvable", "Location not found", "Localização não encontrada")
    val networkErrorSearch @Composable get() = get("Erreur réseau lors de la recherche", "Network error during search", "Erro de rede durante a pesquisa")
    val deleteTraces @Composable get() = get("Supprimer les tracés", "Delete traces", "Eliminar traços")
    val closestSite @Composable get() = get("Site le plus proche", "Closest site", "Local mais próximo")
    val noSiteNearby @Composable get() = get("Aucun site", "No site", "Nenhum local")
    val nearby @Composable get() = get("à proximité", "nearby", "nas proximidades")
    val filter @Composable get() = get("Filtres", "Filters", "Filtros")
    val mapLayerTitle @Composable get() = get("Fond de carte", "Map layer", "Camada do mapa")
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
    val dateNotSpecifiedAnfr @Composable get() = get("Date non spécifiée par l'ANFR", "Date not specified by ANFR", "Data não especificada pela ANFR")
    val azimuthNotSpecified @Composable get() = get("Azimut non spécifié", "Azimuth not specified", "Azimute não especificado")

    val azimuthsLabel @Composable get() = get("Azimuts", "Azimuths", "Azimutes")

    // Ajouts pour la cohérence
    val orientationsTitle @Composable get() = get("Orientations (Azimuts)", "Orientations (Azimuths)", "Orientações (Azimutes)")
    val panelHeightsTitle @Composable get() = get("Hauteur des panneaux", "Panel heights", "Altura dos painéis")
    val idSupportLabel @Composable get() = get("ID Support : ", "Support ID : ", "ID do Suporte : ")
    val heightAbbr @Composable get() = get("hauteur", "height", "altura")

    // Titre de la section
    val azimuthsTitle @Composable get() = get("Azimuts", "Azimuths", "Azimutes")

    // Label du bouton
    val showAzimuthsLabel @Composable get() = get("Afficher les azimuts (direction de l'antenne)", "Show azimuths (antenna direction)", "Mostrar azimutes (direção da antena)")

    // ==========================================
    // 📍 BOUTONS DE SUIVI (CARTE)
    // ==========================================
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
    // 📊 STATISTIQUES DE VILLE
    // ==========================================
    val cityStatsTitle @Composable get() = get("Consulter les statistiques", "View statistics", "Ver estatísticas")
    val mobileTelephony @Composable get() = get("Téléphonie mobile", "Mobile telephony", "Telefonia móvel")
    val details @Composable get() = get("Détails", "Details", "Detalhes")
    val operatorDetailsTitle @Composable get() = get("Détail par opérateur", "Operator details", "Detalhe por operadora")
    @Composable
    fun sitesCount(count: Int) = get("$count sites", "$count sites", "$count locais")
    val frequenciesAndTechs @Composable get() = get("Technologies / Fréquences", "Technologies / Frequencies", "Tecnologias / Frequências")
    val sitesLabel @Composable get() = get("Sites", "Sites", "Locais")
    val others @Composable get() = get("Autres", "Others", "Outros")

    // ==========================================
    // 📊 STATISTIQUES
    // ==========================================
    val statsSupportsTitle @Composable get() = get("Supports (Pylônes)", "Supports (Pylons)", "Suportes (Pilões)")
    val statsSupportsDesc @Composable get() = get("Nombre de sites physiques par opérateur", "Number of physical sites per operator", "Número de locais físicos por operadora")
    val stats4GTitle @Composable get() = get("Sites 4G", "4G Sites", "Locais 4G")
    val stats4GDesc @Composable get() = get("Nombre de sites équipés en 4G par opérateur", "Number of 4G-equipped sites per operator", "Número de locais equipados com 4G por operadora")
    val stats5GTitle @Composable get() = get("Sites 5G", "5G Sites", "Locais 5G")
    val stats5GDesc @Composable get() = get("Nombre de sites équipés en 5G par opérateur", "Number of 5G-equipped sites per operator", "Número de locais equipados com 5G por operadora")

    // ==========================================
    // 📱 WIDGET
    // ==========================================
    val widgetTitle @Composable get() = get("📍 Antennes à proximité", "📍 Nearby antennas", "📍 Antenas próximas")
    val bgLocationPermTitle @Composable get() = get("Autorisation Widget", "Widget Permission", "Permissão do Widget")
    val bgLocationPermDesc @Composable get() = get("Autorisez \"Toujours\" pour que le widget s'actualise", "Allow \"Always\" for the widget to refresh", "Permita \"Sempre\" para o widget atualizar")
    val bgLocationToast @Composable get() = get("Allez dans Autorisations > Localisation > Toujours autoriser", "Go to Permissions > Location > Allow all the time", "Vá em Permissões > Localização > Permitir o tempo todo")

    // ==========================================
    // 📸 PHOTOS DE LA COMMUNAUTÉ
    // ==========================================
    @Composable
    fun communityPhotosTitle(count: Int, communityName: String): String {
        // 1. On remplace l'espace normal par un espace insécable (\u00A0)
        // Cela force "Signal Quest" à rester soudé sur une seule ligne.
        val cleanName = communityName.replace(" ", "\u00A0")

        // 2. On ajoute un retour à la ligne (\n) avant le nom pour l'isoler
        val fr = if (count > 1) "Photos de la communauté\n$cleanName" else "Photo de la communauté\n$cleanName"
        val en = if (count > 1) "Community photos from\n$cleanName" else "Community photo from\n$cleanName"
        val pt = if (count > 1) "Fotos da comunidade\n$cleanName" else "Foto da comunidade\n$cleanName"

        return get(fr, en, pt)
    }

    val communityPhotosOffline @Composable get() = get(
        "Vous êtes hors ligne.\nConnexion internet requise pour voir les photos.",
        "You are offline.\nInternet connection required to view photos.",
        "Você está offline.\nConexão à internet necessária para ver as fotos."
    )

    @Composable
    fun photoByAuthor(author: String) = get("Par $author", "By $author", "Por $author")

    @Composable
    fun photoOnDate(date: String) = get("le $date", "on $date", "em $date")

    @Composable
    fun communityPhotosTitleShort(count: Int): String {
        val fr = if (count > 1) "Photos de la communauté" else "Photo de la communauté"
        val en = if (count > 1) "Community photos" else "Community photo"
        val pt = if (count > 1) "Fotos da comunidade" else "Foto da comunidade"
        return get(fr, en, pt)
    }

    val sitePhotoDesc @Composable get() = get("Photo du site", "Site photo", "Foto do local")
    val fullScreenPhotoDesc @Composable get() = get("Photo en plein écran", "Full screen photo", "Foto em tela cheia")
    val close @Composable get() = get("Fermer", "Close", "Fechar")

    // ==========================================
    // 📤 PARTAGE (OPTIONS)
    // ==========================================
    val defaultShareContentTitle @Composable get() = get("Contenu du partage par défaut", "Default share content", "Conteúdo de partilha predefinido")
    val defaultShareContentDesc @Composable get() = get("Choisir les éléments à inclure sur l'image", "Choose the elements to include on the image", "Escolher os elementos a incluir na imagem")

    // --- NOUVEAUX AJOUTS POUR LA CARTE ---
    val shareMapDetailsTitle @Composable get() = get("Carte", "Map", "Mapa")
    val shareMapCompassOption @Composable get() = get("Boussole", "Compass", "Bússola")
    val shareMapSpeedometerOption @Composable get() = get("Compteur de vitesse", "Speedometer", "Velocímetro")
    val shareMapScaleOption @Composable get() = get("Échelle", "Scale", "Escala")
    val shareMapAttributionOption @Composable get() = get("Crédits (Attribution)", "Credits (Attribution)", "Créditos (Atribuição)")

    val shareMapOption @Composable get() = get("Afficher la carte", "Display the map", "Mostrar o mapa")
    val shareSupportOption @Composable get() = get("Détails du support", "Support details", "Detalhes do suporte")
    val shareFreqOption @Composable get() = get("Fréquences", "Frequencies", "Frequências")
    val shareDatesOption @Composable get() = get("Dates d'activation", "Activation dates", "Datas de ativação")
    val shareAddressOption @Composable get() = get("Adresse et Coordonnées", "Address and Coordinates", "Endereço e Coordenadas")
    val shareHeightsOption @Composable get() = get("Hauteurs des panneaux", "Panel heights", "Alturas dos painéis")
    val shareIdsOption @Composable get() = get("Identifiants de l'antenne", "Antenna identifiers", "Identificadores da antena")
    val shareConfidentialOption @Composable get() = get("Partage confidentiel", "Confidential share", "Partilha confidencial")
    val shareConfidentialDesc @Composable get() = get("Supprime les données permettant l'identification du lieu", "Removes data allowing location identification", "Remove dados que permitem a identificação do local")

    // ==========================================
    // 📸 GÉNÉRATION D'IMAGE ET PARTAGE
    // ==========================================
    val scanToOpen @Composable get() = get("Scannez pour ouvrir dans", "Scan to open in", "Escaneie para abrir no")
    val geoTowerApp @Composable get() = get("l'application GeoTower", "the GeoTower app", "aplicativo GeoTower")

    // ==========================================
    // 🌍 TRADUCTIONS AJOUTÉES POUR LE SUPPORT
    // ==========================================
    val back @Composable get() = get("Retour", "Back", "Voltar")
    val imageContent @Composable get() = get("Contenu de l'image", "Image content", "Conteúdo da imagem")
    val move @Composable get() = get("Déplacer", "Move", "Mover")
    val generateImage @Composable get() = get("Générer l'image", "Generate image", "Gerar imagem")
    val copy @Composable get() = get("Copier", "Copy", "Copiar")
    val distanceHidden @Composable get() = get("Distance masquée (Mode confidentiel)", "Distance hidden (Confidential mode)", "Distância oculta (Modo confidencial)")

    // Labels techniques pour le presse-papiers (invisibles la plupart du temps, mais plus propres si traduits)
    val idSupportCopy @Composable get() = get("ID Support", "Support ID", "ID do Suporte")
    val addressCopy @Composable get() = get("Adresse", "Address", "Endereço")
    val gpsCoordsCopy @Composable get() = get("Coordonnées GPS", "GPS Coordinates", "Coordenadas GPS")

    // ==========================================
    // ⚠️ AVERTISSEMENT COULEUR CARTE
    // ==========================================
    val warningTitle @Composable get() = get("Attention", "Warning", "Atenção")
    val lightColorWarning @Composable get() = get(
        "Votre couleur MaterialUi est trop claire. Pour plus de lisibilité, la couleur bleu foncé a été appliquée.",
        "Your MaterialUi color is too light. For better readability, dark blue has been applied.",
        "A sua cor MaterialUi é muito clara. Para melhor legibilidade, a cor azul escuro foi aplicada."
    )
    val doNotShowAgain @Composable get() = get("Ne plus afficher ce message", "Do not show this message again", "Não mostrar esta mensagem novamente")
    val understood @Composable get() = get("J'ai compris", "Understood", "Entendi")

    // ==========================================
    // UPLOAD SIGNAL QUEST
    // ==========================================
    val uploadSqTitle @Composable get() = get("Envoi vers Signal Quest", "Send to Signal Quest", "Enviar para Signal Quest")
    val uploadSqDescPlaceholder @Composable get() = get("Ajouter une description pour ce lot (optionnel)...", "Add a description for this batch (optional)...", "Adicionar uma descrição para este lote (opcional)...")
    val uploadSqTargetOperator @Composable get() = get("Opérateur cible", "Target operator", "Operadora destino")
    val uploadSqTargetSite @Composable get() = get("Support N°", "Site ID", "ID do Suporte")

    @Composable
    fun uploadSqButton(count: Int) = get(
        "Envoyer $count photo${if (count > 1) "s" else ""}",
        "Send $count photo${if (count > 1) "s" else ""}",
        "Enviar $count foto${if (count > 1) "s" else ""}"
    )

    // Textes pour les futures notifications du WorkManager
    val notifUploadInProgress @Composable get() = get("Envoi en cours vers Signal Quest...", "Uploading to Signal Quest...", "Enviando para Signal Quest...")
    val notifUploadSuccess @Composable get() = get("Photos envoyées avec succès !", "Photos sent successfully!", "Fotos enviadas com sucesso!")
    val notifUploadFailed @Composable get() = get("Échec de l'envoi des photos", "Failed to send photos", "Falha ao enviar fotos")

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
        "$success sur $total photo(s) envoyée(s) avec succès.",
        "$success out of $total photo(s) successfully sent.",
        "$success de $total foto(s) enviadas com sucesso."
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
    // 💾 NOTIFICATIONS BASE DE DONNÉES
    // ==========================================
    val notifDbDownloadTitle @Composable get() = get("Mise à jour de la base", "Database update", "Atualização da base de dados")
    val notifDbDownloadInProgress @Composable get() = get("Téléchargement en cours...", "Downloading...", "A transferir...")
    val notifDbDownloadSuccess @Composable get() = get("Téléchargement terminé", "Download finished", "Transferência concluída")
    val notifDbDownloadSuccessDesc @Composable get() = get("Appuyez pour ouvrir l'application.", "Tap to open the app.", "Toque para abrir o aplicativo.")
    val dbDownloadSuccessDesc @Composable get() = get("La base de données a été téléchargée avec succès !", "The database was successfully downloaded!", "A base de dados foi transferida com sucesso!")
    val dbDownloadTermine @Composable get() = get("Terminer", "Finish", "Terminar")

    // ==========================================
    // ⚠️ AVERTISSEMENTS TUTO
    // ==========================================
    val dbWarningTitle @Composable get() = get("Base de donnée non téléchargée", "Database not downloaded", "Base de dados não transferida")
    val dbWarningDesc @Composable get() = get("Vous n'avez pas téléchargé la base de donnée de l'application, vous n'aurez donc aucun élément affiché à l'écran.", "You haven't downloaded the app's database, so you won't have any items displayed on the screen.", "Você não transferiu a base de dados da aplicação, portanto não terá nenhum item exibido na tela.")
    val dbWarningQuestion @Composable get() = get("Êtes-vous sûr de vouloir continuer ?", "Are you sure you want to continue?", "Tem certeza que deseja continuar?")
    val continueAnyway @Composable get() = get("Continuer", "Continue", "Continuar")

    val missingDbBannerTitle @Composable get() = get("Base de données manquante", "Missing database", "Base de dados ausente")
    val updateDbBannerTitle @Composable get() = get("Mise à jour disponible", "Update available", "Atualização disponível")
    val missingDbBannerDesc @Composable get() = get("Téléchargez la base pour utiliser l'appli.", "Download the database to use the app.", "Baixe o banco de dados para usar o app.")
    val btnDownloadBanner @Composable get() = get("Télécharger", "Download", "Baixar")

    // ==========================================
    // 🗄️ TRADUCTIONS DYNAMIQUES DE LA BASE DE DONNÉES
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
            "Local technique" -> get("Local technique", "Technical room / equipment shelter", "Sala técnica / abrigo de equipamentos")
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
            else -> nature // Retourne le mot français original si non listé
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
            else -> owner // Ex: "ORANGE", "EDF", "SNCF" resteront tels quels
        }
    }

    @Composable
    fun getMapName(id: String): String {
        return when (id) {
            "france_north_oam" -> get("France (Moitié Nord)", "France (North)", "França (Norte)")
            "france_south_oam" -> get("France (Moitié Sud)", "France (South)", "França (Sul)")
            "corse_oam" -> get("Corse", "Corsica", "Córsega")
            "caribbean_oam" -> get("Caraïbes (Antilles)", "Caribbean", "Caraíbas")
            "guyana_oam" -> get("Guyane & Suriname", "Guyana & Suriname", "Guiana e Suriname")
            "madagascar_oam" -> get("Océan Indien (Réunion...)", "Indian Ocean", "Oceano Índico")
            "polynesia_oam" -> get("Polynésie Française", "French Polynesia", "Polinésia Francesa")
            else -> id // Retourne l'ID brut si pas de traduction trouvée
        }
    }

    // ==========================================
    // ⚙️ TRADUCTIONS HORS-COMPOSE (POUR LES SERVICES)
    // ==========================================

    // Fonction utilitaire spéciale pour lire la langue sans @Composable
    fun getForService(context: android.content.Context, fr: String, en: String, pt: String): String {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", android.content.Context.MODE_PRIVATE)
        val currentLang = prefs.getString("app_language", "Système") ?: "Système"
        val langToCheck = if (currentLang == "Système") java.util.Locale.getDefault().language else currentLang

        return when {
            langToCheck == "Français" || langToCheck == "fr" -> fr
            langToCheck == "Português" || langToCheck == "pt" -> pt
            else -> en
        }
    }

    fun newDbNotifTitle(ctx: android.content.Context) = getForService(ctx, "Nouvelle base de données", "New database", "Nova base de dados")
    fun newDbNotifDesc(ctx: android.content.Context) = getForService(ctx, "Une mise à jour des antennes est disponible ! Touchez pour l'installer.", "An antenna update is available! Tap to install.", "Uma atualização de antenas está disponível! Toque para instalar.")
    fun nearestAntennaTitle(ctx: android.content.Context) = getForService(ctx, "À proximité", "Nearby", "Nas proximidades")
    fun liveTrackingChannelDesc(ctx: android.content.Context) = getForService(ctx, "Suivi d'antennes en direct", "Live antenna tracking", "Rastreamento de antenas ao vivo")
    fun searchInProgress(ctx: android.content.Context) = getForService(ctx, "Recherche en cours...", "Searching...", "Buscando...")
    fun quitAction(ctx: android.content.Context) = getForService(ctx, "Quitter", "Stop", "Sair")
    fun noneOpService(ctx: android.content.Context) = getForService(ctx, "Aucun", "None", "Nenhum")

    fun noAntennaFound(ctx: android.content.Context, op: String) = getForService(ctx, "Aucune antenne $op trouvée à proximité.", "No $op antenna found nearby.", "Nenhuma antena $op encontrada nas proximidades.")
    fun antennaDistance(ctx: android.content.Context, op: String, dist: String) = getForService(ctx, "Antenne $op : $dist", "$op antenna : $dist", "Antena $op : $dist")

    fun widgetBgLocationWarning(ctx: android.content.Context) = getForService(ctx, "⚠️ Localisation arrière-plan requise", "⚠️ Background location required", "⚠️ Localização em segundo plano necessária")
    fun widgetBgLocationDesc(ctx: android.content.Context) = getForService(
        ctx,
        "Touchez ici, allez dans Autorisations > Localisation, puis choisissez \"Toujours autoriser\".",
        "Tap here, go to Permissions > Location, then select \"Allow all the time\".",
        "Toque aqui, vá em Permissões > Localização e selecione \"Permitir o tempo todo\"."
    )

    // ==========================================
    // ⚠️ PANNES SIGNAL QUEST (DÉTAILS)
    // ==========================================
    val outageAttentionDesc @Composable get() = get("Attention panne", "Outage warning", "Aviso de falha")
    val unknownOutageReason @Composable get() = get("Raison inconnue", "Unknown reason", "Motivo desconhecido")
    val outageReasonMaintenance @Composable get() = get("Maintenance", "Maintenance", "Manutenção")
    val outageReasonIncident @Composable get() = get("Incident", "Incident", "Incidente")
    val outageReasonTechnical @Composable get() = get("Intervention technique", "Technical intervention", "Intervenção técnica")

    // Statuts détaillés
    val outageVoice @Composable get() = get("Voix", "Voice", "Voz")
    val outageData @Composable get() = get("Data", "Data", "Dados")
    val outageStatusDegraded @Composable get() = get("Dégradé", "Degraded", "Degradado")
    val outageStatusHs @Composable get() = get("Hors Service", "Out of Service", "Fora de Serviço")
    val outageStatusOk @Composable get() = get("OK", "OK", "OK")
    val outageStatusNe @Composable get() = get("Non Équipé", "Not Equipped", "Não Equipado")
    val outageEndExpected @Composable get() = get("Fin prévue le", "Expected end on", "Fim previsto em")

    // ==========================================
    // 📊 BLOC STATUT (DÉTAIL SITE)
    // ==========================================
    val statusTitle @Composable get() = get("Statut du site", "Site Status", "Status do site")
    val statusFunctional @Composable get() = get("Fonctionnel", "Functional", "Funcional")
    val statusOutage @Composable get() = get("En panne", "Out of service", "Em manutenção")
    val statusProject @Composable get() = get("En projet", "Planned", "Em projeto")
    val showStatusOption @Composable get() = get("Afficher le statut", "Show status", "Mostrar status")
    val shareStatusOption @Composable get() = get("Partager le statut", "Share status", "Partilhar o status")

    // 🚨 NOUVELLES TRADUCTIONS
    val serviceVoice @Composable get() = get("Voix", "Voice", "Voz")
    val serviceSms @Composable get() = get("SMS", "SMS", "SMS")
    val serviceInternet @Composable get() = get("Internet", "Internet", "Internet")
    val lastUpdatedText @Composable get() = get("Dernière mise à jour à", "Last updated at", "Última atualização às")

    // 🚨 TRADUCTIONS DES RETOURS BRUTS DE L'API (JSON)
    val apiDetailIncident @Composable get() = get("Incident en cours", "Ongoing incident", "Incidente em curso")
    val apiDetailMaintenance @Composable get() = get("Travaux de maintenance", "Maintenance work", "Trabalhos de manutenção")
    val outageStart @Composable get() = get("Depuis le", "Since", "Desde")

    // ==========================================
    // ℹ️ ÉCRAN À PROPOS - VERSIONS
    // ==========================================
    val aboutVersionsTitle @Composable get() = get("Versions", "Versions", "Versões")
    val versionAppLabel @Composable get() = get("Version de l'application", "App version", "Versão do app")
    val versionDbLabel @Composable get() = get("Version de la base de données", "Database version", "Versão da base de dados")
    val versionWeeklyLabel @Composable get() = get("Mise à jour hebdomadaire", "Weekly update", "Atualização semanal")
    val versionMonthlyLabel @Composable get() = get("Mise à jour mensuelle", "Monthly update", "Atualização mensal")
    val versionHsLabel @Composable get() = get("Date des sites HS", "HS sites date", "Data dos sites HS")

    // ==========================================
    // 📅 FORMATAGE DE LA DATE MENSUELLE
    // ==========================================
    @Composable
    fun formatMonthlyVersion(rawName: String): String {
        // Si la chaîne est vide, un tiret, ou un message d'erreur, on la retourne telle quelle
        if (rawName.isBlank() || rawName == "-" || rawName == "Téléchargez la nouvelle base") return rawName

        // Le Regex cherche 4 chiffres (Année) suivis de 2 chiffres (Mois) au tout début du nom
        val regex = Regex("^(\\d{4})(\\d{2})\\d{2}.*")
        val match = regex.find(rawName)

        if (match != null) {
            val year = match.groupValues[1]
            val monthName = when (match.groupValues[2]) {
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
            if (monthName.isNotEmpty()) {
                return "$monthName $year"
            }
        }

        // Si le nom du fichier ne correspond pas au format attendu, on l'affiche brut par sécurité
        return rawName
    }

    // ==========================================
    // 📅 FORMATAGE DE LA DATE HEBDOMADAIRE (Avec Semaine)
    // ==========================================
    @Composable
    fun formatWeeklyVersionWithWeekNumber(dateStr: String): String {
        // On vérifie qu'on a bien une date au format attendu (ex: 15/04/2026)
        if (dateStr.isBlank() || dateStr == "-" || !dateStr.contains("/")) return dateStr

        // ✅ CORRECTION : On appelle la fonction Composable HORS du bloc try-catch !
        val weekWord = get("Semaine", "Week", "Semana")

        return try {
            val format = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val date = format.parse(dateStr)

            if (date != null) {
                val cal = java.util.Calendar.getInstance()
                // Norme ISO 8601 (Utilisée en Europe pour le calcul des semaines)
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.minimalDaysInFirstWeek = 4
                cal.time = date

                val weekNumber = cal.get(java.util.Calendar.WEEK_OF_YEAR)

                "$weekWord $weekNumber  -  $dateStr"
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }
}
