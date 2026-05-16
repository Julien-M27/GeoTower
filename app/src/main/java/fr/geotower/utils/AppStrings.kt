package fr.geotower.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import java.util.Locale

object AppStrings {
    const val LANGUAGE_SYSTEM = "Système"
    const val LANGUAGE_FRENCH = "Français"
    const val LANGUAGE_ENGLISH = "English"
    const val LANGUAGE_PORTUGUESE = "Português"

    // Lecture en temps réel de la langue choisie dans les paramètres
    private val language: String
        @Composable get() = AppConfig.appLanguage.value

    // Fonction utilitaire pour 3 langues
    @Composable
    fun get(fr: String, en: String, pt: String): String {
        val currentLang = AppConfig.appLanguage.value

        // Si "Système" est sélectionné, on récupère le code langue du téléphone
        val langToCheck = if (currentLang == LANGUAGE_SYSTEM) {
            currentSystemLanguage()
        } else {
            currentLang
        }

        return when {
            langToCheck == LANGUAGE_FRENCH || langToCheck == "fr" -> fr
            langToCheck == LANGUAGE_PORTUGUESE || langToCheck == "pt" -> pt
            // Anglais par défaut
            else -> en
        }
    }

    @Composable
    private fun currentSystemLanguage(): String = ComposeLocale.current.language

    @Composable
    private fun currentJavaLocale(): Locale = Locale.forLanguageTag(ComposeLocale.current.language.ifBlank { "en" })

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

    val systemLanguage @Composable get() = get("Langage système", "System language", "Idioma do sistema")

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
    val appLanguageLabel @Composable get() = get("Langue de l'application", "App Language", "Idioma da aplicação")
    val none @Composable get() = get("Aucun", "None", "Nenhum")
    val select @Composable get() = get("Sélectionner", "Select", "Selecionar")

    @Composable
    fun current(value: String) = get("Actuel : $value", "Current : $value", "Atual : $value")
    val validate @Composable get() = get("Valider", "Validate", "Validar")
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
        "La charge du réseau, le backhaul et les capacités exactes du téléphone ne sont pas connus.",
        "Network load, backhaul and the phone's exact capabilities are not known.",
        "A carga da rede, o backhaul e as capacidades exatas do telemóvel não são conhecidos."
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
        "Le débit montant est limité aux deux meilleures fréquences agrégées, une hypothèse plus réaliste pour les réseaux mobiles en France.",
        "Upload throughput is limited to the two best aggregated frequencies, which is a more realistic assumption for mobile networks in France.",
        "O débito ascendente é limitado às duas melhores frequências agregadas, uma hipótese mais realista para redes móveis em França."
    )
    val throughputWarningLowBandAggregation @Composable get() = get(
        "Agrégation 4G entre bandes basses 700/800/900 MHz limitée : beaucoup de téléphones ne cumulent pas ces porteuses.",
        "4G aggregation between low bands 700/800/900 MHz is limited: many phones do not combine these carriers.",
        "A agregação 4G entre bandas baixas 700/800/900 MHz é limitada: muitos telemóveis não combinam estas portadoras."
    )
    val throughputWarningLteAggregationLimit @Composable get() = get(
        "Limite d'agrégation 4G choisie : seules les meilleures porteuses sont comptées.",
        "Selected 4G aggregation limit: only the best carriers are counted.",
        "Limite de agregação 4G escolhida: apenas as melhores portadoras são contabilizadas."
    )
    val throughputWarningNrAggregationLimit @Composable get() = get(
        "Limite d'agrégation 5G du profil : seules les meilleures porteuses sont comptées.",
        "Profile 5G aggregation limit: only the best carriers are counted.",
        "Limite de agregação 5G do perfil: apenas as melhores portadoras são contabilizadas."
    )
    val throughputExcludedNoMetropolitanArcepAllocation @Composable get() = get(
        "Aucune allocation Arcep France métropolitaine compatible avec cette technologie et cette bande.",
        "No compatible Arcep allocation for metropolitan France was found for this technology and band.",
        "Não foi encontrada nenhuma alocação Arcep da França metropolitana compatível com esta tecnologia e esta banda."
    )
    val throughputExcludedDssShared @Composable get() = get(
        "Bande potentiellement partagée entre la 4G et la 5G : elle n'est pas additionnée deux fois.",
        "Band potentially shared between 4G and 5G: it is not counted twice.",
        "Banda potencialmente partilhada entre 4G e 5G: não é contabilizada duas vezes."
    )
    val throughputSourceSummaryEngine @Composable get() = get(
        "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, ETSI/3GPP TS 38.306 et TS 36.306/36.213 pour le modèle radio.",
        "ANFR/data.gouv for declared frequencies, Arcep for operator allocations, ETSI/3GPP TS 38.306 and TS 36.306/36.213 for the radio model.",
        "ANFR/data.gouv para as frequências declaradas, Arcep para as alocações das operadoras, ETSI/3GPP TS 38.306 e TS 36.306/36.213 para o modelo rádio."
    )
    val throughputSourceSummaryDefault @Composable get() = get(
        "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, 3GPP pour le modèle radio.",
        "ANFR/data.gouv for declared frequencies, Arcep for operator allocations, 3GPP for the radio model.",
        "ANFR/data.gouv para as frequências declaradas, Arcep para as alocações das operadoras, 3GPP para o modelo rádio."
    )
    val throughputProfilePrudentEngineDesc @Composable get() = get(
        "Profil prudent : 4G 64-QAM en descendant, 16-QAM en montant, 5G NR 64-QAM, agrégation limitée et DSS non compté deux fois.",
        "Conservative profile: 4G 64-QAM downlink, 16-QAM uplink, 5G NR 64-QAM, limited aggregation and DSS not counted twice.",
        "Perfil prudente: 4G 64-QAM em download, 16-QAM em upload, 5G NR 64-QAM, agregação limitada e DSS sem dupla contagem."
    )
    val throughputProfileStandardEngineDesc @Composable get() = get(
        "Profil standard : 4G 256-QAM en descendant avec MIMO 2x2, montant 64-QAM côté téléphone, 5G n78 256-QAM en descendant avec MIMO 4x4, montant 64-QAM sur 2 couches, DSS non compté deux fois.",
        "Standard profile: 4G 256-QAM downlink with 2x2 MIMO, 64-QAM phone-side uplink, 5G n78 256-QAM downlink with 4x4 MIMO, 64-QAM uplink on 2 layers, DSS not counted twice.",
        "Perfil padrão: 4G 256-QAM em download com MIMO 2x2, upload 64-QAM no telemóvel, 5G n78 256-QAM em download com MIMO 4x4, upload 64-QAM em 2 camadas, DSS sem dupla contagem."
    )
    val throughputProfileIdealEngineDesc @Composable get() = get(
        "Profil idéal : très bonnes conditions radio plausibles, 4G en descendant avec MIMO 4x4, 5G NR 256-QAM, agrégation plus ouverte et sans double comptage DSS.",
        "Ideal profile: plausible very good radio conditions, 4G downlink with 4x4 MIMO, 5G NR 256-QAM, more open aggregation and no DSS double counting.",
        "Perfil ideal: condições rádio muito boas e plausíveis, 4G em download com MIMO 4x4, 5G NR 256-QAM, agregação mais aberta e sem dupla contagem DSS."
    )
    val throughputProfileCustomEngineDesc @Composable get() = get(
        "Profil personnalisé : modulations descendantes et montantes choisies dans l'interface, débit montant traité comme celui d'un téléphone.",
        "Custom profile: downlink and uplink modulations chosen in the interface, with upload treated like a phone.",
        "Perfil personalizado: modulações de download e upload escolhidas na interface, com o upload tratado como o de um telemóvel."
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
        val profilePrefix = "Le MIMO et la modulation ne sont pas publiés au niveau du site : le profil "
        val allocationPrefix = "Bande "
        val allocationSuffix = " exclue : allocation opérateur introuvable."
        val dssPrefix = "Bande "
        val dssSuffix = " potentiellement partagée entre la 4G et la 5G : le débit n'est pas additionné intégralement."

        return when {
            warning == "La charge du réseau, le backhaul et les capacités exactes du téléphone ne sont pas connus." -> throughputWarningNetworkUnknown
            warning.startsWith(profilePrefix) && warning.endsWith(" est donc appliqué.") -> {
                val rawLabel = warning.removePrefix(profilePrefix).removeSuffix(" est donc appliqué.")
                throughputWarningProfileApplied(translateThroughputProfileLabel(rawLabel))
            }
            warning.startsWith(allocationPrefix) && warning.endsWith(allocationSuffix) -> {
                throughputWarningAllocationMissing(warning.removePrefix(allocationPrefix).removeSuffix(allocationSuffix))
            }
            warning.startsWith(dssPrefix) && warning.endsWith(dssSuffix) -> {
                throughputWarningDssShared(warning.removePrefix(dssPrefix).removeSuffix(dssSuffix))
            }
            warning == "Le débit montant est limité aux deux meilleures fréquences agrégées, une hypothèse plus réaliste pour les réseaux mobiles en France." -> throughputWarningUplinkAggregation
            warning == "Agrégation 4G entre bandes basses 700/800/900 MHz limitée : beaucoup de téléphones ne cumulent pas ces porteuses." -> throughputWarningLowBandAggregation
            warning == "Limite d'agrégation 4G choisie : seules les meilleures porteuses sont comptées." -> throughputWarningLteAggregationLimit
            warning == "Limite d'agrégation 5G du profil : seules les meilleures porteuses sont comptées." -> throughputWarningNrAggregationLimit
            else -> warning
        }
    }

    @Composable
    fun translateThroughputAssumption(assumption: String): String = when (assumption) {
        "Profil prudent : 4G 64-QAM en descendant, 16-QAM en montant, 5G NR 64-QAM, agrégation limitée et DSS non compté deux fois." -> throughputProfilePrudentEngineDesc
        "Profil standard : 4G 256-QAM en descendant avec MIMO 2x2, montant 64-QAM côté téléphone, 5G n78 256-QAM en descendant avec MIMO 4x4, montant 64-QAM sur 2 couches, DSS non compté deux fois." -> throughputProfileStandardEngineDesc
        "Profil idéal : très bonnes conditions radio plausibles, 4G en descendant avec MIMO 4x4, 5G NR 256-QAM, agrégation plus ouverte et sans double comptage DSS." -> throughputProfileIdealEngineDesc
        "Profil personnalisé : modulations descendantes et montantes choisies dans l'interface, débit montant traité comme celui d'un téléphone." -> throughputProfileCustomEngineDesc
        "Profil personnalisé : modulations DL/UL choisies dans l'interface, UL traité comme un téléphone." -> throughputProfileCustomEngineDesc
        else -> assumption
    }

    @Composable
    fun translateThroughputExcludedReason(reason: String): String = when (reason) {
        "5G désactivée" -> get("5G désactivée", "5G disabled", "5G desativado")
        "4G désactivée" -> get("4G désactivée", "4G disabled", "4G desativado")
        "Bande exclue" -> get("Bande exclue", "Band excluded", "Banda excluída")
        "Opérateur non reconnu pour les allocations Arcep" -> get("Opérateur non reconnu pour les allocations Arcep", "Operator not recognized for Arcep allocations", "Operadora não reconhecida nas alocações Arcep")
        "Allocation Arcep introuvable" -> get("Allocation Arcep introuvable", "Arcep allocation not found", "Alocação Arcep não encontrada")
        "Bande en projet" -> get("Bande en projet", "Planned band", "Banda em projeto")
        "Aucune allocation Arcep France métropolitaine compatible avec cette technologie et cette bande." -> throughputExcludedNoMetropolitanArcepAllocation
        "Bande potentiellement partagée entre la 4G et la 5G : elle n'est pas additionnée deux fois." -> throughputExcludedDssShared
        "Agrégation 4G entre bandes basses 700/800/900 MHz limitée : beaucoup de téléphones ne cumulent pas ces porteuses." -> throughputWarningLowBandAggregation
        "Limite d'agrégation 4G choisie : seules les meilleures porteuses sont comptées." -> throughputWarningLteAggregationLimit
        "Limite d'agrégation 5G du profil : seules les meilleures porteuses sont comptées." -> throughputWarningNrAggregationLimit
        else -> reason
    }

    @Composable
    fun translateThroughputSourceSummary(summary: String): String = when (summary) {
        "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, ETSI/3GPP TS 38.306 et TS 36.306/36.213 pour le modèle radio." -> throughputSourceSummaryEngine
        "ANFR/data.gouv pour les fréquences déclarées, Arcep pour les allocations opérateur, 3GPP pour le modèle radio." -> throughputSourceSummaryDefault
        else -> summary
    }

    @Composable
    private fun translateThroughputProfileLabel(label: String): String = when (label) {
        "Prudent" -> get("prudent", "conservative", "prudente")
        "Standard" -> get("standard", "standard", "padrão")
        "Profil idéal" -> get("idéal", "ideal", "ideal")
        "Personnalisé" -> get("personnalisé", "custom", "personalizado")
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
    val devCredit @Composable get() = get("Développé par Julien et les contributeurs de GitHub 😉", "Developed by Julien and GitHub contributors 😉", "Desenvolvido por Julien e os collaborateurs do GitHub 😉")
    val srcAntennas @Composable get() = get("Données Antennes", "Antenna Data", "Dados de Antenas")
    val srcAntennasDesc @Composable get() = get("Agence Nationale des Fréquences (ANFR).\nDonnées issues de Cartoradio (Open Data).", "National Frequency Agency (ANFR).\nData from Cartoradio (Open Data).", "Agência Nacional de Frequências (ANFR).\nDados do Cartoradio (Open Data).")
    val srcIgn @Composable get() = get("Fond de carte IGN", "IGN Basemap", "Mapa base IGN")
    val srcIgnDesc @Composable get() = get("© IGN - Institut National de l'Information Géographique et Forestière.", "© IGN - National Institute of Geographic and Forest Information.", "© IGN - Instituto Nacional de Informação Geográfica e Florestal.")
    val srcOsm @Composable get() = get("Fond de carte OSM", "OSM Basemap", "Mapa base OSM")
    val srcOsmDesc @Composable get() = get("© les contributeurs d'OpenStreetMap.", "© OpenStreetMap contributors.", "© os colaboradores do OpenStreetMap.")
    val srcInspo @Composable get() = get("Inspiration & Sources Externes", "Inspiration & External Sources", "Inspiração e Fontes Externas")
    val srcInspoDesc @Composable get() = get("• © CellularFR développé par Luis Baker\n• © Signal Quest développé par Alexandre Germain\n• © RNC Mobile développé par Cédric\n• © eNB-Analytics développé par Tristan\n• © GeoRadio - L'icône alternative provient de l'application GéoRadio sur iOS développée par Hugo Martin.\n• Concept original basé sur l'application GéoRadio\n• Icône fun dessinée par Johan", "• © CellularFR developed by Luis Baker\n• © Signal Quest developed by Alexandre Germain\n• © RNC Mobile developed by Cédric\n• © eNB-Analytics developed par Tristan\n• © GeoRadio - The alternative icon comes from the GéoRadio iOS app developed by Hugo Martin.\n• Original concept based on the GéoRadio app\n• Fun icon designed by Johan", "• © CellularFR desenvolvido por Luis Baker\n• © Signal Quest desenvolvido par Alexandre Germain\n• © RNC Mobile desenvolvido por Cédric\n• © eNB-Analytics desenvolvido par Tristan\n• © GeoRadio - O ícone alternativo vem da application GéoRadio para iOS desenvolvida por Hugo Martin.\n• Conceito original baseado na application GéoRadio\n• Ícone divertido desenhado por Johan")

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

    val widgetTitle @Composable get() = get("📍 Antennes à proximité", "📍 Nearby antennas", "📍 Antenas próximas")
    val bgLocationPermTitle @Composable get() = get("Autorisation Widget", "Widget Permission", "Permissão do Widget")
    val bgLocationPermDesc @Composable get() = get("Autorisez \"Toujours\" pour que le widget s'actualise", "Allow \"Always\" for the widget to refresh", "Permita \"Sempre\" para o widget atualizar")

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
    val close @Composable get() = get("Fermer", "Close", "Fechar")

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
    val latestChanges @Composable get() = get("Dernières modifications", "Latest changes", "Últimas alterações")
    val showMoreSites @Composable get() = get("Afficher plus de sites", "Show more sites", "Mostrar mais")
    val geoportailIgn @Composable get() = get("Géoportail (IGN)", "Geoportal (IGN)", "Geoportal (IGN)")
    val languageFrenchName @Composable get() = get("Français", "French", "Francês")
    val languageEnglishName @Composable get() = get("Anglais", "English", "Inglês")
    val languagePortugueseName @Composable get() = get("Portugais", "Portuguese", "Português")
    @Composable
    fun languageDisplayName(languageValue: String): String = when (languageValue) {
        LANGUAGE_SYSTEM -> systemLanguage
        LANGUAGE_FRENCH -> languageFrenchName
        LANGUAGE_ENGLISH -> languageEnglishName
        LANGUAGE_PORTUGUESE -> languagePortugueseName
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

    val uploadSqTitle @Composable get() = get("Envoi vers Signal Quest", "Send to Signal Quest", "Enviar para Signal Quest")
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

    fun signalQuestUploadChannelName(ctx: android.content.Context) = getForService(ctx, "Envoi Signal Quest", "Signal Quest upload", "Envio Signal Quest")
    fun signalQuestUploadProgress(ctx: android.content.Context, current: Int, total: Int) = getForService(ctx, "Envoi en cours ($current/$total)...", "Uploading ($current/$total)...", "A enviar ($current/$total)...")
    fun signalQuestUploadRetry(ctx: android.content.Context) = getForService(ctx, "Échec réseau, nouvel essai plus tard.", "Network error, retrying later.", "Erro de rede, nova tentativa mais tarde.")
    fun signalQuestUploadSuccess(ctx: android.content.Context, success: Int, total: Int) = getForService(ctx, "$success/$total photos envoyées avec succès vers Signal Quest !", "$success/$total photos sent successfully to Signal Quest!", "$success/$total fotos enviadas com sucesso para Signal Quest!")
    fun signalQuestUploadPartial(ctx: android.content.Context, success: Int, total: Int) = getForService(ctx, "$success/$total photos envoyées vers Signal Quest. Certaines ont échoué.", "$success/$total photos sent to Signal Quest. Some failed.", "$success/$total fotos enviadas para Signal Quest. Algumas falharam.")

    fun widgetTitle(ctx: android.content.Context) = getForService(ctx, "📍 Antennes à proximité", "📍 Nearby antennas", "📍 Antenas próximas")
    fun widgetUpdatedAt(ctx: android.content.Context, lastUpdate: String) = getForService(ctx, "Mis à jour à $lastUpdate", "Updated at $lastUpdate", "Atualizado às $lastUpdate")
    fun widgetWaitingGps(ctx: android.content.Context) = getForService(ctx, "En attente du GPS...", "Waiting for GPS...", "À espera do GPS...")
    fun widgetImmediateSearch(ctx: android.content.Context) = getForService(ctx, "Recherche immédiate...", "Searching...", "Pesquisa imediata...")

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
        "Toque aqui, vá em Permissões > Localização e selecione \"Permitir o tempo todo\"."
    )

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
    val showSpeedtestLabel @Composable get() = get("Afficher le meilleur Speedtest (SFR/Bouygues)", "Show best Speedtest (SFR/Bouygues)", "Mostrar o meilleur Speedtest (SFR/Bouygues)")
    val speedtestTitle @Composable get() = get("Meilleur Speedtest", "Best Speedtest", "Melhor Speedtest")
    val speedtestDownload @Composable get() = get("Descendant", "Download", "Download")
    val speedtestUpload @Composable get() = get("Montant", "Upload", "Upload")
    val speedtestPing @Composable get() = get("Ping", "Ping", "Ping")
    val speedtestNoData @Composable get() = get("Aucun test de débit répertorié pour ce site.", "No speedtest recorded for this site.", "Nenhum teste de velocidade registado para este site.")
}
