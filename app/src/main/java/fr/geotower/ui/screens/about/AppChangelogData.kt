package fr.geotower.ui.screens.about

import fr.geotower.R

enum class MilestoneBadge {
    NONE,
    CURRENT,
    PLAY_STORE,
    MAJOR,
    FOUNDATION
}

data class ChangelogMilestone(
    val versionName: String,
    val releaseDate: String,
    val isMajor: Boolean = false,
    val isCurrent: Boolean = false,
    val badge: MilestoneBadge = MilestoneBadge.NONE,
    val summary: String,
    val itemResIds: List<Int> = emptyList(),
    val directItems: List<String> = emptyList()
)

/**
 * Catalogue chronologique complet de l'évolution de GeoTower depuis ses débuts.
 */
object AppChangelogRepository {
    val milestones: List<ChangelogMilestone> = listOf(
        ChangelogMilestone(
            versionName = "2.1.10",
            releaseDate = "2026-11-05",
            isCurrent = true,
            badge = MilestoneBadge.CURRENT,
            summary = "Sélecteur de durée et décompte en direct pour la liaison montre Garmin écran éteint",
            itemResIds = listOf(R.string.appstrings_release_v2110_summary_garmin_duration_selector_and_countdown)
        ),
        ChangelogMilestone(
            versionName = "2.1.9",
            releaseDate = "2026-11-05",
            summary = "Accès direct aux réglages Garmin depuis la notification et fonctionnement continu",
            itemResIds = listOf(R.string.appstrings_release_v219_summary_garmin_notification_access_and_continuous_mode)
        ),
        ChangelogMilestone(
            versionName = "2.1.8",
            releaseDate = "2026-11-05",
            summary = "Harmonisation visuelle de l'interrupteur liaison montre Garmin",
            itemResIds = listOf(R.string.appstrings_release_v218_summary_garmin_switch_ui_styling)
        ),
        ChangelogMilestone(
            versionName = "2.1.7",
            releaseDate = "2026-11-05",
            summary = "Service d'arrière-plan optionnel pour la liaison Bluetooth Garmin écran éteint",
            itemResIds = listOf(R.string.appstrings_release_v217_summary_garmin_background_foreground_service)
        ),
        ChangelogMilestone(
            versionName = "2.1.6",
            releaseDate = "2026-11-04",
            summary = "Reception directe et deduplication des requetes Bluetooth montres Garmin",
            itemResIds = listOf(R.string.appstrings_release_v216_summary_garmin_direct_broadcast_and_deduplication)
        ),
        ChangelogMilestone(
            versionName = "2.1.5",
            releaseDate = "2026-11-04",
            summary = "Reveil en arriere-plan et communication ecran eteint pour montres Garmin",
            itemResIds = listOf(R.string.appstrings_release_v215_summary_garmin_background_wake_and_screen_off_sync)
        ),
        ChangelogMilestone(
            versionName = "2.1.4",
            releaseDate = "2026-11-03",
            summary = "Stabilite Garmin en release R8/Proguard et logs persistants dans le diagnostic",
            itemResIds = listOf(R.string.appstrings_release_v214_summary_garmin_r8_proguard_and_persisted_diagnostics)
        ),
        ChangelogMilestone(
            versionName = "2.1.3",
            releaseDate = "2026-11-03",
            summary = "Dialogue de diagnostic Garmin dans Parametres, journal BLE en direct et anti-crash",
            itemResIds = listOf(R.string.appstrings_release_v213_summary_garmin_diagnostic_dialog_and_live_logs)
        ),
        ChangelogMilestone(
            versionName = "2.1.2",
            releaseDate = "2026-11-03",
            summary = "Reconnexion automatique et detection fiabilisee des montres Garmin",
            itemResIds = listOf(R.string.appstrings_release_v212_summary_garmin_connection_and_reconnect_reliability)
        ),
        ChangelogMilestone(
            versionName = "2.1.1",
            releaseDate = "2026-11-03",
            summary = "Fiabilisation des transferts Bluetooth et segmentation multi-trames pour montres Garmin",
            itemResIds = listOf(R.string.appstrings_release_v211_summary_garmin_ble_chunks_and_transfer_reliability)
        ),
        ChangelogMilestone(
            versionName = "2.1.0",
            releaseDate = "2026-11-03",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Application compagnon Garmin Connect IQ et journal des versions",
            itemResIds = listOf(R.string.appstrings_release_v210_summary_garmin_companion_and_changelog_history)
        ),
        ChangelogMilestone(
            versionName = "2.0.99",
            releaseDate = "2026-10-31",
            summary = "Securisation et atomicite du cache des antennes de la carte",
            itemResIds = listOf(R.string.appstrings_release_v2099_summary_antenna_cache_proguard_and_atomicity)
        ),
        ChangelogMilestone(
            versionName = "2.0.98",
            releaseDate = "2026-10-31",
            summary = "Rapprochement geographique des identifiants eNB / gNB",
            itemResIds = listOf(R.string.appstrings_release_v2098_summary_enb_proximity_fallback)
        ),
        ChangelogMilestone(
            versionName = "2.0.97",
            releaseDate = "2026-10-30",
            summary = "Logos diffuseurs TDF et Towercast et recherche directe par identifiant #",
            itemResIds = listOf(R.string.appstrings_release_v2097_summary_tdf_towercast_logos_and_hash_id_search)
        ),
        ChangelogMilestone(
            versionName = "2.0.96",
            releaseDate = "2026-10-30",
            summary = "Recherche des sites radio et non mobiles sur la carte et fermetures clavier",
            itemResIds = listOf(R.string.appstrings_release_v2096_summary_radio_database_search_and_keyboard_handling)
        ),
        ChangelogMilestone(
            versionName = "2.0.95",
            releaseDate = "2026-10-29",
            summary = "Affichage plein ecran immersif des visionneurs de photos",
            itemResIds = listOf(R.string.appstrings_release_v2095_summary_fullscreen_photo_viewers)
        ),
        ChangelogMilestone(
            versionName = "2.0.94",
            releaseDate = "2026-10-29",
            summary = "Amelioration des azimuts et du contraste satellite dans la mini-carte",
            itemResIds = listOf(R.string.appstrings_release_v2094_summary_mini_map_azimuth_and_contrast)
        ),
        ChangelogMilestone(
            versionName = "2.0.93",
            releaseDate = "2026-10-28",
            summary = "Animation elastique de la barre de navigation et finitions visuelles",
            itemResIds = listOf(R.string.appstrings_release_v2093_summary_nav_animation_and_visual_refinements)
        ),
        ChangelogMilestone(
            versionName = "2.0.92",
            releaseDate = "2026-10-27",
            summary = "Bandeaux d etat partages entre Accueil et Plus",
            itemResIds = listOf(R.string.appstrings_release_v2092_summary_home_status_banners_in_more_menu)
        ),
        ChangelogMilestone(
            versionName = "2.0.91",
            releaseDate = "2026-10-27",
            summary = "Tableau comparatif des versions API et cache des antennes de la carte",
            itemResIds = listOf(R.string.appstrings_release_v2091_summary_api_versions_table_and_antenna_cache)
        ),
        ChangelogMilestone(
            versionName = "2.0.90",
            releaseDate = "2026-10-27",
            summary = "Affichage en volet lateral des supports sur grand ecran",
            itemResIds = listOf(R.string.appstrings_release_v2090_summary_map_split_screen)
        ),
        ChangelogMilestone(
            versionName = "2.0.89",
            releaseDate = "2026-10-27",
            summary = "Cache memoire persistant des tuiles cartographiques",
            itemResIds = listOf(R.string.appstrings_release_v2089_summary_persistent_map_cache)
        ),
        ChangelogMilestone(
            versionName = "2.0.88",
            releaseDate = "2026-10-26",
            summary = "Filtrage optimise des operateurs en base et style classique ameliore",
            itemResIds = listOf(R.string.appstrings_release_v2088_summary_sql_operator_filters)
        ),
        ChangelogMilestone(
            versionName = "2.0.87",
            releaseDate = "2026-10-26",
            summary = "Transparence unifiee, fluidite des statistiques et menu Plus enrichi",
            itemResIds = listOf(R.string.appstrings_release_v2087_summary_floating_ui_and_stats)
        ),
        ChangelogMilestone(
            versionName = "2.0.86",
            releaseDate = "2026-10-26",
            summary = "Balayage interactif entre les pages et suivi tactile sur la barre",
            itemResIds = listOf(R.string.appstrings_release_v2086_summary_interactive_tab_swipe)
        ),
        ChangelogMilestone(
            versionName = "2.0.85",
            releaseDate = "2026-10-26",
            summary = "Gestes fluides et reglages d onglets sur la barre de navigation",
            itemResIds = listOf(R.string.appstrings_release_v2085_summary_bottom_nav_gestures)
        ),
        ChangelogMilestone(
            versionName = "2.0.84",
            releaseDate = "2026-10-25",
            summary = "Personnalisation de la barre de navigation et du menu Plus",
            itemResIds = listOf(R.string.appstrings_release_v2084_summary_bottom_nav_customization)
        ),
        ChangelogMilestone(
            versionName = "2.0.81",
            releaseDate = "2026-10-24",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Mode de navigation par barre inferieure et curseur temporel",
            itemResIds = listOf(R.string.appstrings_release_v2081_summary_bottom_navigation_bar)
        ),
        ChangelogMilestone(
            versionName = "2.0.80",
            releaseDate = "2026-10-21",
            summary = "Inspection des pannes dans le visualiseur et sources configurables",
            itemResIds = listOf(R.string.appstrings_release_v2080_summary_database_viewer_outages_and_sources)
        ),
        ChangelogMilestone(
            versionName = "2.0.79",
            releaseDate = "2026-10-20",
            summary = "Recherche dans les frequences, commande vocale Assistant et alertes de generation",
            itemResIds = listOf(R.string.appstrings_release_v2079_summary_frequency_search_and_voice_assistant)
        ),
        ChangelogMilestone(
            versionName = "2.0.78",
            releaseDate = "2026-10-19",
            summary = "Indicateur visuel de chargement sur la carte",
            itemResIds = listOf(R.string.appstrings_release_v2078_summary_map_loading_indicator)
        ),
        ChangelogMilestone(
            versionName = "2.0.77",
            releaseDate = "2026-10-19",
            summary = "Journal des requetes reseau et personnalisation du tiroir lateral",
            itemResIds = listOf(R.string.appstrings_release_v2077_summary_network_log_and_drawer_order)
        ),
        ChangelogMilestone(
            versionName = "2.0.76",
            releaseDate = "2026-10-19",
            summary = "Ergonomie et disposition du dialogue de test de latence des serveurs API",
            itemResIds = listOf(R.string.appstrings_release_v2076_summary_ping_dialog_ergonomics)
        ),
        ChangelogMilestone(
            versionName = "2.0.75",
            releaseDate = "2026-10-18",
            summary = "Diagnostic memoire et systeme enrichi, controle du ping et regroupement",
            itemResIds = listOf(R.string.appstrings_release_v2075_summary_diagnostics_and_clustering)
        ),
        ChangelogMilestone(
            versionName = "2.0.74",
            releaseDate = "2026-10-07",
            summary = "Optimisation des QR Codes et reinitialisation des reglages",
            itemResIds = listOf(R.string.appstrings_release_v2074_summary_preference_profiles_qr_optimization)
        ),
        ChangelogMilestone(
            versionName = "2.0.73",
            releaseDate = "2026-10-07",
            summary = "Partage par QR Code et comparaison des profils",
            itemResIds = listOf(R.string.appstrings_release_v2073_summary_preference_profiles_qr_and_diff)
        ),
        ChangelogMilestone(
            versionName = "2.0.72",
            releaseDate = "2026-10-07",
            summary = "Selection multiple et suppression dans les signalements",
            itemResIds = listOf(R.string.appstrings_release_v2072_summary_photo_reports_selection_clear)
        ),
        ChangelogMilestone(
            versionName = "2.0.71",
            releaseDate = "2026-10-06",
            summary = "Raccourci vers les signalements depuis les historiques",
            itemResIds = listOf(R.string.appstrings_release_v2071_summary_photo_reports_histories_shortcut)
        ),
        ChangelogMilestone(
            versionName = "2.0.70",
            releaseDate = "2026-10-06",
            summary = "Personnalisation de l affichage des signalements de photos",
            itemResIds = listOf(R.string.appstrings_release_v2070_summary_photo_reports_customization)
        ),
        ChangelogMilestone(
            versionName = "2.0.69",
            releaseDate = "2026-10-06",
            summary = "Ciblage precis de l operateur pour les signalements de photos",
            itemResIds = listOf(R.string.appstrings_release_v2069_summary_photo_report_operator)
        ),
        ChangelogMilestone(
            versionName = "2.0.68",
            releaseDate = "2026-10-06",
            summary = "Mise en valeur visuelle du site et acces aux signalements",
            itemResIds = listOf(R.string.appstrings_release_v2068_summary_site_highlight, R.string.appstrings_release_v2068_summary_photo_reports, R.string.appstrings_release_v2068_summary_cluster_slider)
        ),
        ChangelogMilestone(
            versionName = "2.0.67",
            releaseDate = "2026-10-06",
            summary = "Divulgation de localisation en arriere-plan pour les widgets",
            itemResIds = listOf(R.string.appstrings_release_v2067_widget_disclosure)
        ),
        ChangelogMilestone(
            versionName = "2.0.66",
            releaseDate = "2026-10-06",
            summary = "Choix de la source des pannes et filtres avances",
            itemResIds = listOf(R.string.appstrings_release_v2066_summary_outage_sources, R.string.appstrings_release_v2066_summary_filter_effects, R.string.appstrings_release_v2066_summary_nearby_and_maps)
        ),
        ChangelogMilestone(
            versionName = "2.0.65",
            releaseDate = "2026-10-05",
            summary = "Sources eNB par operateur et optimisation des bases",
            itemResIds = listOf(R.string.appstrings_release_v2065_summary_enb_sources, R.string.appstrings_release_v2065_summary_database_updates, R.string.appstrings_release_v2065_summary_split_coverage)
        ),
        ChangelogMilestone(
            versionName = "2.0.64",
            releaseDate = "2026-10-02",
            summary = "Azimuts actifs et notifications de bases",
            itemResIds = listOf(R.string.appstrings_release_v2064_summary_frequency_azimuths, R.string.appstrings_release_v2064_summary_database_notifications)
        ),
        ChangelogMilestone(
            versionName = "2.0.63",
            releaseDate = "2026-09-28",
            summary = "Retablissement du chargement des antennes",
            itemResIds = listOf(R.string.appstrings_release_v2063_summary_map_loading)
        ),
        ChangelogMilestone(
            versionName = "2.0.62",
            releaseDate = "2026-09-27",
            summary = "Reglage de la carte et des azimuts",
            itemResIds = listOf(R.string.appstrings_release_v2062_summary_cluster_strength, R.string.appstrings_release_v2062_summary_marker_readability)
        ),
        ChangelogMilestone(
            versionName = "2.0.61",
            releaseDate = "2026-09-27",
            summary = "Reglage du regroupement des antennes.",
            itemResIds = listOf(R.string.appstrings_release_v2061_summary_cluster_strength, R.string.appstrings_release_v2061_summary_marker_readability)
        ),
        ChangelogMilestone(
            versionName = "2.0.60",
            releaseDate = "2026-09-23",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Visionneuse des bases et fiabilite des services.",
            itemResIds = listOf(R.string.appstrings_release_v2060_summary_database_viewer, R.string.appstrings_release_v2060_summary_local_build, R.string.appstrings_release_v2060_summary_live_photos)
        ),
        ChangelogMilestone(
            versionName = "2.0.59",
            releaseDate = "2026-09-19",
            summary = "Filtres de carte et mises a jour des bases.",
            itemResIds = listOf(R.string.appstrings_release_v2059_summary_map_filters, R.string.appstrings_release_v2059_summary_databases)
        ),
        ChangelogMilestone(
            versionName = "2.0.58",
            releaseDate = "2026-09-08",
            summary = "Estimateur de debit et logos personnalises.",
            itemResIds = listOf(R.string.appstrings_release_v2058_summary_throughput, R.string.appstrings_release_v2058_summary_logos)
        ),
        ChangelogMilestone(
            versionName = "2.0.57",
            releaseDate = "2026-09-05",
            summary = "Diagnostic des serveurs GeoTower.",
            itemResIds = listOf(R.string.appstrings_release_v2057_summary_ping, R.string.appstrings_release_v2057_summary_diagnostics)
        ),
        ChangelogMilestone(
            versionName = "2.0.43",
            releaseDate = "2026-09-01",
            summary = "Stabilite Android Auto et bases.",
            itemResIds = listOf(R.string.appstrings_release_v2043_database, R.string.appstrings_release_v2043_android_auto)
        ),
        ChangelogMilestone(
            versionName = "2.0.42",
            releaseDate = "2026-08-31",
            summary = "Correctif du compteur de bases.",
            itemResIds = listOf(R.string.appstrings_release_v2042_database_count)
        ),
        ChangelogMilestone(
            versionName = "2.0.41",
            releaseDate = "2026-08-31",
            summary = "Lisibilite, bases et Android Auto.",
            itemResIds = listOf(R.string.appstrings_release_v2041_accessible_themes, R.string.appstrings_release_v2041_theme_colors, R.string.appstrings_release_v2041_database_banner, R.string.appstrings_release_v2041_android_auto)
        ),
        ChangelogMilestone(
            versionName = "2.0.40",
            releaseDate = "2026-08-31",
            summary = "Statistiques de zones, filtres et Android Auto.",
            itemResIds = listOf(R.string.appstrings_release_v2040_city_stats, R.string.appstrings_release_v2040_map_filters, R.string.appstrings_release_v2040_android_auto)
        ),
        ChangelogMilestone(
            versionName = "2.0.39",
            releaseDate = "2026-08-31",
            summary = "Amelioration d'Android Auto et des bases eNB.",
            itemResIds = listOf(R.string.appstrings_release_v2039_android_auto, R.string.appstrings_release_v2039_enb_operator_counts, R.string.appstrings_release_v2039_onboarding_database_download)
        ),
        ChangelogMilestone(
            versionName = "2.0.38",
            releaseDate = "2026-08-30",
            summary = "Referentiel des frequences et trajets.",
            itemResIds = listOf(R.string.appstrings_release_v2038_frequency_reference, R.string.appstrings_release_v2038_trip_import, R.string.appstrings_release_v2038_map_readability, R.string.appstrings_release_v2038_page_customization, R.string.appstrings_release_v2038_logo_theme)
        ),
        ChangelogMilestone(
            versionName = "2.0.37",
            releaseDate = "2026-08-29",
            summary = "Ajout des sites masques.",
            itemResIds = listOf(R.string.appstrings_release_v2037_hidden_sites, R.string.appstrings_release_v2037_hidden_sites_scope, R.string.appstrings_release_v2037_hidden_sites_backup)
        ),
        ChangelogMilestone(
            versionName = "2.0.36",
            releaseDate = "2026-08-28",
            summary = "Amelioration des statuts et du partage.",
            itemResIds = listOf(R.string.appstrings_release_v2036_frequency_grid, R.string.appstrings_release_v2036_database_banner, R.string.appstrings_release_v2036_status_details, R.string.appstrings_release_v2036_global_outages, R.string.appstrings_release_v2036_share_export)
        ),
        ChangelogMilestone(
            versionName = "2.0.35",
            releaseDate = "2026-08-28",
            summary = "Mise a jour des bases et statuts de site.",
            itemResIds = listOf(R.string.appstrings_release_v2035_database_queue, R.string.appstrings_release_v2035_database_servers, R.string.appstrings_release_v2035_site_status_lines, R.string.appstrings_release_v2035_status_uncertainty, R.string.appstrings_release_v2035_operation_pause)
        ),
        ChangelogMilestone(
            versionName = "2.0.34",
            releaseDate = "2026-08-26",
            summary = "Mise a jour des bases et generation locale detaillee.",
            itemResIds = listOf(R.string.appstrings_release_v2034_database_updates, R.string.appstrings_release_v2034_local_build_progress, R.string.appstrings_release_v2034_database_storage)
        ),
        ChangelogMilestone(
            versionName = "2.0.33",
            releaseDate = "2026-08-17",
            summary = "Sauvegarde des donnees et photos a l'arrivee d'une etape.",
            itemResIds = listOf(R.string.appstrings_release_v2033_backup, R.string.appstrings_release_v2033_backup_additive, R.string.appstrings_release_v2033_backup_preview, R.string.appstrings_release_v2033_trip_arrival)
        ),
        ChangelogMilestone(
            versionName = "2.0.32",
            releaseDate = "2026-08-13",
            summary = "Journal des notifications et choix d'itineraire.",
            itemResIds = listOf(R.string.appstrings_release_v2032_notification_history, R.string.appstrings_release_v2032_notification_not_shown, R.string.appstrings_release_v2032_routing_choice)
        ),
        ChangelogMilestone(
            versionName = "2.0.31",
            releaseDate = "2026-08-13",
            summary = "Suivi du site le plus proche filtre comme la carte.",
            itemResIds = listOf(R.string.appstrings_release_v2031_nearest_filters, R.string.appstrings_release_v2031_nearest_refresh, R.string.appstrings_release_v2031_measure_search)
        ),
        ChangelogMilestone(
            versionName = "2.0.30",
            releaseDate = "2026-08-13",
            summary = "Repere de suivi ancre sur les boutons de zoom.",
            itemResIds = listOf(R.string.appstrings_release_v2030_marker_anchor, R.string.appstrings_release_v2030_nav_zoom, R.string.appstrings_release_v2030_no_jump)
        ),
        ChangelogMilestone(
            versionName = "2.0.29",
            releaseDate = "2026-08-13",
            summary = "Temps restant sur le rythme reel et main rendue a la carte.",
            itemResIds = listOf(R.string.appstrings_release_v2029_eta_real_pace, R.string.appstrings_release_v2029_camera_release, R.string.appstrings_release_v2029_compass_hidden, R.string.appstrings_release_v2029_measure_hint)
        ),
        ChangelogMilestone(
            versionName = "2.0.28",
            releaseDate = "2026-08-13",
            summary = "Cadrage de navigation et trajet d'approche.",
            itemResIds = listOf(R.string.appstrings_release_v2028_nav_camera, R.string.appstrings_release_v2028_nav_no_spin, R.string.appstrings_release_v2028_approach_route, R.string.appstrings_release_v2028_measure_first_point)
        ),
        ChangelogMilestone(
            versionName = "2.0.27",
            releaseDate = "2026-08-13",
            summary = "Signalement des photos communautaires.",
            itemResIds = listOf(R.string.appstrings_release_v2027_photo_report, R.string.appstrings_release_v2027_photo_report_honest, R.string.appstrings_release_v2027_photo_reports_page, R.string.appstrings_release_v2027_trip_save_prompt)
        ),
        ChangelogMilestone(
            versionName = "2.0.26",
            releaseDate = "2026-08-12",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Planificateur de trajet.",
            itemResIds = listOf(R.string.appstrings_release_v2026_trip_planner, R.string.appstrings_release_v2026_trip_steps, R.string.appstrings_release_v2026_trip_follow, R.string.appstrings_release_v2026_trip_reminders, R.string.appstrings_release_v2026_trip_export)
        ),
        ChangelogMilestone(
            versionName = "2.0.25",
            releaseDate = "2026-08-11",
            summary = "Sites tout juste declares.",
            itemResIds = listOf(R.string.appstrings_release_v2025_announced_banner, R.string.appstrings_release_v2025_announced_frequencies)
        ),
        ChangelogMilestone(
            versionName = "2.0.24",
            releaseDate = "2026-08-11",
            summary = "Suggestions de recherche et case « ? » des services.",
            itemResIds = listOf(R.string.appstrings_release_v2024_search_suggestions, R.string.appstrings_release_v2024_search_accents, R.string.appstrings_release_v2024_admin_area_no_limit, R.string.appstrings_release_v2024_route_searching, R.string.appstrings_release_v2024_status_uncertain)
        ),
        ChangelogMilestone(
            versionName = "2.0.23",
            releaseDate = "2026-08-11",
            summary = "Finitions de la carte : boussole, mesure et zones.",
            itemResIds = listOf(R.string.appstrings_release_v2023_compass_recenter, R.string.appstrings_release_v2023_search_releases_tracking, R.string.appstrings_release_v2023_measure_drawer, R.string.appstrings_release_v2023_route_icon, R.string.appstrings_release_v2023_area_mask)
        ),
        ChangelogMilestone(
            versionName = "2.0.22",
            releaseDate = "2026-08-10",
            summary = "Mesure : distance totale et suivi des routes.",
            itemResIds = listOf(R.string.appstrings_release_v2022_measure_total, R.string.appstrings_release_v2022_measure_follow_roads)
        ),
        ChangelogMilestone(
            versionName = "2.0.21",
            releaseDate = "2026-08-10",
            summary = "Recherche par departement, zoom de localisation et sources de donnees.",
            itemResIds = listOf(R.string.appstrings_release_v2021_search_admin_areas, R.string.appstrings_release_v2021_location_zoom, R.string.appstrings_release_v2021_compass_follow, R.string.appstrings_release_v2021_about_sources)
        ),
        ChangelogMilestone(
            versionName = "2.0.20",
            releaseDate = "2026-08-09",
            summary = "Technologies hors service et historique de tout ce qui sort de l'app.",
            itemResIds = listOf(R.string.appstrings_release_v2020_outage_tech_table, R.string.appstrings_release_v2020_share_history_full, R.string.appstrings_release_v2020_share_history_filter)
        ),
        ChangelogMilestone(
            versionName = "2.0.19",
            releaseDate = "2026-08-09",
            summary = "Relevé des pannes a la demande et base en ligne detaillee.",
            itemResIds = listOf(R.string.appstrings_release_v2019_outage_rebuild, R.string.appstrings_release_v2019_live_db_about, R.string.appstrings_release_v2019_about_refresh, R.string.appstrings_release_v2019_local_mode_renamed)
        ),
        ChangelogMilestone(
            versionName = "2.0.18",
            releaseDate = "2026-08-09",
            summary = "Rotation manuelle des photos et rayon a proximite par paliers.",
            itemResIds = listOf(R.string.appstrings_release_v2018_photo_rotate, R.string.appstrings_release_v2018_nearby_radius, R.string.appstrings_release_v2018_about_scrolling)
        ),
        ChangelogMilestone(
            versionName = "2.0.17",
            releaseDate = "2026-08-09",
            summary = "Affichage fractionne decide sur la taille de la fenetre.",
            itemResIds = listOf(R.string.appstrings_release_v2017_split_auto, R.string.appstrings_release_v2017_split_all_devices, R.string.appstrings_release_v2017_nav_style_removed)
        ),
        ChangelogMilestone(
            versionName = "2.0.16",
            releaseDate = "2026-08-09",
            summary = "Partage d'une station pagine et photos toujours droites.",
            itemResIds = listOf(R.string.appstrings_release_v2016_share_frequency_pages, R.string.appstrings_release_v2016_share_many_bands_hint, R.string.appstrings_release_v2016_photo_orientation)
        ),
        ChangelogMilestone(
            versionName = "2.0.15",
            releaseDate = "2026-08-08",
            summary = "Historique des partages de carte et credits du fond.",
            itemResIds = listOf(R.string.appstrings_release_v2015_share_history_map, R.string.appstrings_release_v2015_share_history_reopen, R.string.appstrings_release_v2015_map_attribution, R.string.appstrings_release_v2015_nearby_no_radius, R.string.appstrings_release_v2015_low_power_follow, R.string.appstrings_release_v2015_outage_none_declared)
        ),
        ChangelogMilestone(
            versionName = "2.0.14",
            releaseDate = "2026-08-08",
            summary = "Section « Pages », repere GPS fluide et generation locale sur budgets mesures.",
            itemResIds = listOf(R.string.appstrings_release_v2014_smooth_location, R.string.appstrings_release_v2014_announced_bands, R.string.appstrings_release_v2014_azimuth_placeholder, R.string.appstrings_release_v2014_pages_section, R.string.appstrings_release_v2014_pages_search, R.string.appstrings_release_v2014_local_build_budgets, R.string.appstrings_release_v2014_local_build_cost, R.string.appstrings_release_v2014_local_build_announced, R.string.appstrings_release_v2014_local_build_speed)
        ),
        ChangelogMilestone(
            versionName = "2.0.12",
            releaseDate = "2026-08-07",
            summary = "Correction du crash au demarrage.",
            itemResIds = listOf(R.string.appstrings_release_v2012_startup_crash)
        ),
        ChangelogMilestone(
            versionName = "2.0.6",
            releaseDate = "2026-08-04",
            summary = "Nouveau fond satellite et choix de mode à l'accueil",
            itemResIds = listOf(R.string.appstrings_release_v206_satellite, R.string.appstrings_release_v206_onboarding_display_mode, R.string.appstrings_release_v206_onboarding_local_mode, R.string.appstrings_release_v206_local_mode_coherence, R.string.appstrings_release_v206_max_autonomy_partial, R.string.appstrings_release_v206_outage_controls)
        ),
        ChangelogMilestone(
            versionName = "2.0.5",
            releaseDate = "2026-08-03",
            summary = "Compteurs d'antennes et régénération locale",
            itemResIds = listOf(R.string.appstrings_release_v205_db_row_counts, R.string.appstrings_release_v205_db_rebuild_offer, R.string.appstrings_release_v205_local_mode_db, R.string.appstrings_release_v205_home_long_press)
        ),
        ChangelogMilestone(
            versionName = "2.0.4",
            releaseDate = "2026-08-02",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Statistiques par département et technologies",
            itemResIds = listOf(R.string.appstrings_release_v204_department_stats, R.string.appstrings_release_v204_department_operators, R.string.appstrings_release_v204_department_current, R.string.appstrings_release_v204_department_source)
        ),
        ChangelogMilestone(
            versionName = "2.0.3",
            releaseDate = "2026-08-01",
            summary = "Cap et hauteur dissociés",
            itemResIds = listOf(R.string.appstrings_release_v203_bearing_height_split, R.string.appstrings_release_v203_bearing_height_migration)
        ),
        ChangelogMilestone(
            versionName = "2.0.2",
            releaseDate = "2026-07-31",
            summary = "Spectre et largeur de bande distingués",
            itemResIds = listOf(R.string.appstrings_release_v202_bandwidth_vs_spectrum, R.string.appstrings_release_v202_spectrum_column, R.string.appstrings_release_v202_bandwidth_warning, R.string.appstrings_release_v202_nonbreaking_frequencies, R.string.appstrings_release_v202_embedded_blocks_panel, R.string.appstrings_release_v202_share_spectrum)
        ),
        ChangelogMilestone(
            versionName = "2.0.0",
            releaseDate = "2026-07-29",
            isMajor = true,
            badge = MilestoneBadge.PLAY_STORE,
            summary = "Arrivée sur Google Play et bascule serveur miroir",
            itemResIds = listOf(R.string.appstrings_release_v20_play_store, R.string.appstrings_release_v20_api_mirror, R.string.appstrings_release_v20_api_choice, R.string.appstrings_release_v20_outage_card, R.string.appstrings_release_v20_outage_local_copy, R.string.appstrings_release_v20_outage_errors, R.string.appstrings_release_v20_db_generating_banner, R.string.appstrings_release_v20_debug_log, R.string.appstrings_release_v20_diagnostic_server, R.string.appstrings_release_v20_bg_location_disclosure)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6.6",
            releaseDate = "2026-07-28",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Identifiants réseau eNB/gNB et nouveau mode simplifié",
            itemResIds = listOf(R.string.appstrings_release_v1966_simple_mode, R.string.appstrings_release_v1966_simple_mode_banner, R.string.appstrings_release_v1966_notifications_master, R.string.appstrings_release_v1966_live_without_notif, R.string.appstrings_release_v1966_onboarding_notifications, R.string.appstrings_release_v1966_page_customization_discovery, R.string.appstrings_release_v1966_outage_source_merged, R.string.appstrings_release_v1966_operators_actions)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6.5",
            releaseDate = "2026-07-14",
            summary = "Génération locale des pannes réseau et écran source dédié",
            itemResIds = listOf(R.string.appstrings_release_v1965_outage_local_source, R.string.appstrings_release_v1965_outage_generation)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6.4",
            releaseDate = "2026-07-13",
            summary = "Filtres carte par défaut et durées de traitement des bases",
            itemResIds = listOf(R.string.appstrings_release_v1964_map_filters_defaults, R.string.appstrings_release_v1964_db_timings)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6.3",
            releaseDate = "2026-07-13",
            summary = "Catalogue embarqué des cartes hors-ligne et provenance des bases",
            itemResIds = listOf(R.string.appstrings_release_v1963_db_provenance, R.string.appstrings_release_v1963_offline_maps_catalog, R.string.appstrings_release_v1963_site_panel_table)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6.2",
            releaseDate = "2026-07-07",
            summary = "Photos favorites et notifications d'envoi enrichies",
            itemResIds = listOf(R.string.appstrings_release_v1962_favorite_photos_page, R.string.appstrings_release_v1962_favorite_star, R.string.appstrings_release_v1962_measure_delete_mode, R.string.appstrings_release_v1962_upload_notification)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6.1",
            releaseDate = "2026-07-06",
            summary = "Sélecteur de supports superposés et bannière de localisation",
            itemResIds = listOf(R.string.appstrings_release_v1961_location_banner, R.string.appstrings_release_v1961_support_picker, R.string.appstrings_release_v1961_upload_plurals, R.string.appstrings_release_v1961_upload_support_select)
        ),
        ChangelogMilestone(
            versionName = "1.9.9.6",
            releaseDate = "2026-07-03",
            summary = "Conditions d'utilisation, annulation et indexation SQLite",
            itemResIds = listOf(R.string.appstrings_release_v196_terms_screen, R.string.appstrings_release_v196_cancel_downloads, R.string.appstrings_release_v196_cancel_uploads, R.string.appstrings_release_v196_db_indexes, R.string.appstrings_release_v196_baseline_profile)
        ),
        ChangelogMilestone(
            versionName = "1.5.99",
            releaseDate = "2026-06-30",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Points SignalQuest, couverture théorique et profils de préférences",
            itemResIds = listOf(R.string.appstrings_release_v1599_map_coverage_points, R.string.appstrings_release_v1599_map_theoretical_coverage, R.string.appstrings_release_v1599_map_history_slider, R.string.appstrings_release_v1599_settings_search, R.string.appstrings_release_v1599_settings_theme_button, R.string.appstrings_release_v1599_settings_diagnostic, R.string.appstrings_release_v1599_settings_battery_opt, R.string.appstrings_release_v1599_live_gps_accuracy, R.string.appstrings_release_v1599_profiles_rename, R.string.appstrings_release_v1599_profiles_import_preview, R.string.appstrings_release_v1599_profiles_export_selective, R.string.appstrings_release_v1599_profiles_changes_count, R.string.appstrings_release_v1599_elevation_obstacles, R.string.appstrings_release_v1599_share_radio_report, R.string.appstrings_release_v1599_share_site_pdf, R.string.appstrings_release_v1599_share_community_photos, R.string.appstrings_release_v1599_site_potential_outage, R.string.appstrings_release_v1599_site_cellmapper, R.string.appstrings_release_v1599_site_aer_id, R.string.appstrings_release_v1599_site_throughput_height)
        ),
        ChangelogMilestone(
            versionName = "2.0.56",
            releaseDate = "2026-09-04",
            summary = "Affichage Android Auto fiabilisé et rendu optimisé",
            directItems = listOf(
                "Affichage et cadrage de la carte optimisés pour les écrans de bord Android Auto",
                "Stabilité et robustesse accrues de la projection automobile"
            )
        ),
        ChangelogMilestone(
            versionName = "2.0.50",
            releaseDate = "2026-09-01",
            summary = "Carte Android Auto fiabilisée et localisation améliorée",
            directItems = listOf(
                "Interactivité et navigation sur la carte embarquée dans les véhicules",
                "Réglages et détails adaptés à l'expérience conducteur"
            )
        ),
        ChangelogMilestone(
            versionName = "2.0.44",
            releaseDate = "2026-09-01",
            summary = "Intégration complète Android Auto et écran dédié",
            directItems = listOf(
                "Nouvel écran d'accueil dédié pour Android Auto avec carte temps réel",
                "Téléchargement et mise en cache des antennes optimisés pour la conduite"
            )
        ),
        ChangelogMilestone(
            versionName = "1.9.9.5",
            releaseDate = "2026-06-03",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Règle de mesure avancée sur la carte, identifiant panneau AER_ID et couverture Nominatim",
            directItems = listOf(
                "Mesure de distance enrichie avec segments successifs et cumul en mètres ou pieds",
                "Prise en charge de l'identifiant panneau d'antenne (AER_ID) avec copie en un clic",
                "Couverture officielle étendue pour la recherche géographique Nominatim"
            )
        ),
        ChangelogMilestone(
            versionName = "1.9.9.4",
            releaseDate = "2026-05-26",
            summary = "Sécurisation cryptographique ECDSA des manifestes et protection ProGuard",
            directItems = listOf(
                "Manifestes de téléchargement signés ECDSA pour les bases de données et les cartes hors-ligne",
                "Sécurisation des vérifications SHA-256 et isolation réseau",
                "Règles ProGuard et minification pour la variante release"
            )
        ),
        ChangelogMilestone(
            versionName = "1.9.9.3",
            releaseDate = "2026-05-18",
            summary = "Support multilingue (italien, allemand, espagnol) et photos de site arrondies",
            directItems = listOf(
                "Ajout de l'italien, de l'allemand et de l'espagnol dans toute l'application",
                "Rendu avec coins arrondis pour les photos de site dans le suivi live"
            )
        ),
        ChangelogMilestone(
            versionName = "1.9.9.0",
            releaseDate = "2026-05-15",
            summary = "Support des opérateurs d'Outre-mer et amélioration des statistiques",
            directItems = listOf(
                "Intégration de Free Caraïbe, Telco OI, SRR et Outremer Telecom",
                "Logos, couleurs et spectres adaptés aux territoires ultramarins"
            )
        ),
        ChangelogMilestone(
            versionName = "1.9.0",
            releaseDate = "2026-05-02",
            isMajor = true,
            badge = MilestoneBadge.MAJOR,
            summary = "Interface Material You 3 Expressive, suivi Samsung Now Bar et speedtests",
            directItems = listOf(
                "Nouveau design Material You 3 Expressive avec animations et thèmes dynamiques",
                "Intégration native dans la Now Bar des appareils Samsung One UI",
                "Affichage des records de débit Speedtest sur les fiches de sites"
            )
        ),
        ChangelogMilestone(
            versionName = "1.5.0",
            releaseDate = "2026-04-17",
            summary = "Statuts des sites (en service / en panne) et levée de limite de distance",
            directItems = listOf(
                "Visualisation en temps réel de l'état des émetteurs (en service, en panne)",
                "Suppression de la restriction de recherche limitée à 50 km"
            )
        ),
        ChangelogMilestone(
            versionName = "1.2.0",
            releaseDate = "2026-04-13",
            summary = "Téléchargement et consultation des cartes hors-ligne",
            directItems = listOf(
                "Gestionnaire de packs cartographiques hors-ligne (IGN, OpenStreetMap)",
                "Navigation cartographique autonome sans connexion Internet"
            )
        ),
        ChangelogMilestone(
            versionName = "1.1.0",
            releaseDate = "2026-04-10",
            summary = "Notifications de suivi en direct et intégration système",
            directItems = listOf(
                "Service d'arrière-plan pour le suivi continu du pylône le plus proche",
                "Notification active permanente avec distance, azimut et détails de la station"
            )
        ),
        ChangelogMilestone(
            versionName = "1.0.0",
            releaseDate = "2026-04-06",
            isMajor = true,
            badge = MilestoneBadge.FOUNDATION,
            summary = "Lancement initial de GeoTower — Observatoire cartographique ANFR",
            directItems = listOf(
                "Carte interactive complète des stations relais mobiles de France métropolitaine",
                "Filtres par opérateur (Orange, SFR, Bouygues Telecom, Free Mobile)",
                "Fiches techniques des antennes et bandes de fréquences (2G, 3G, 4G, 5G)",
                "Boussole d'orientation vers l'émetteur le plus proche"
            )
        )
    )
}
