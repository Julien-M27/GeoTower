package fr.geotower.utils

import android.content.SharedPreferences

data class BooleanPreference(
    val key: String,
    val defaultValue: Boolean = true
) {
    fun read(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun write(editor: SharedPreferences.Editor, value: Boolean): SharedPreferences.Editor {
        return editor.putBoolean(key, value)
    }
}

data class StringPreference(
    val key: String,
    val defaultValue: String
) {
    fun read(prefs: SharedPreferences): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun write(editor: SharedPreferences.Editor, value: String): SharedPreferences.Editor {
        return editor.putString(key, value)
    }
}

data class IntPreference(
    val key: String,
    val defaultValue: Int
) {
    fun read(prefs: SharedPreferences): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun write(editor: SharedPreferences.Editor, value: Int): SharedPreferences.Editor {
        return editor.putInt(key, value)
    }
}

object PreferenceStores {
    const val APP = "GeoTowerPrefs"
    const val ANFR_SETTINGS = "settings"
    const val OSM_DROID = "osmdroid"
}

object MapDisplayPrefs {
    val defaultOperator = StringPreference("default_operator", "Aucun")
    val selectedOperatorKeys = StringPreference(AppConfig.PREF_SELECTED_OPERATORS, "")
    val showOrange = BooleanPreference("show_orange", true)
    val showSfr = BooleanPreference("show_sfr", true)
    val showBouygues = BooleanPreference("show_bouygues", true)
    val showFree = BooleanPreference("show_free", true)
    val showSpeedometer = BooleanPreference("show_speedometer", true)
    val showLocationMarker = BooleanPreference(AppConfig.PREF_SHOW_MAP_LOCATION_MARKER, true)
    val showRadioSites = BooleanPreference(AppConfig.PREF_SHOW_RADIO_SITES, false)
    val showAzimuthLines = BooleanPreference(AppConfig.PREF_SHOW_AZIMUTH_LINES, AppConfig.DEFAULT_SHOW_AZIMUTH_LINES)
    val showAzimuthCones = BooleanPreference(AppConfig.PREF_SHOW_AZIMUTH_CONES, AppConfig.DEFAULT_SHOW_AZIMUTH_CONES)
    val showSitesInService = BooleanPreference("show_sites_in_service", true)
    val showSitesOutOfService = BooleanPreference("show_sites_out_of_service", true)
    val hideUndergroundSites = BooleanPreference(AppConfig.PREF_HIDE_UNDERGROUND_SITES, false)
    val showOnlyZbSites = BooleanPreference(AppConfig.PREF_SHOW_ONLY_ZB_SITES, false)
    val showTechno2G = BooleanPreference("show_techno_2g", true)
    val showTechno3G = BooleanPreference("show_techno_3g", true)
    val showTechno4G = BooleanPreference("show_techno_4g", true)
    val showTechno5G = BooleanPreference("show_techno_5g", true)
    val showTechnoFh = BooleanPreference("show_techno_fh", true)
    val f2G900 = BooleanPreference("f2g_900", true)
    val f2G1800 = BooleanPreference("f2g_1800", true)
    val f3G900 = BooleanPreference("f3g_900", true)
    val f3G2100 = BooleanPreference("f3g_2100", true)
    val f4G700 = BooleanPreference("f4g_700", true)
    val f4G800 = BooleanPreference("f4g_800", true)
    val f4G900 = BooleanPreference("f4g_900", true)
    val f4G1800 = BooleanPreference("f4g_1800", true)
    val f4G2100 = BooleanPreference("f4g_2100", true)
    val f4G2600 = BooleanPreference("f4g_2600", true)
    val f5G700 = BooleanPreference("f5g_700", true)
    val f5G1400 = BooleanPreference("f5g_1400", true)
    val f5G2100 = BooleanPreference("f5g_2100", true)
    val f5G3500 = BooleanPreference("f5g_3500", true)
    val f5G4200 = BooleanPreference("f5g_4200", true)
    val f5G26000 = BooleanPreference("f5g_26000", true)
}

object SitePagePrefs {
    const val ORDER = "page_site_order"
    const val MINI_MAP_MODE = "page_site_mini_map_mode"
    const val DEFAULT_ORDER =
        "operator,bearing_height,map,support_details,elevation_profile,throughput_calculator,open_map,photos,speedtest,nav,share,panel_heights,ids,dates,address,status,freqs,links"

    val defaultOrder = DEFAULT_ORDER.split(",")
    val operator = BooleanPreference("page_site_operator", true)
    val bearingHeight = BooleanPreference("page_site_bearing_height", true)
    val map = BooleanPreference("page_site_map", true)
    val supportDetails = BooleanPreference("page_site_support_details", true)
    val photos = BooleanPreference("page_site_photos", true)
    val panelHeights = BooleanPreference("page_site_panel_heights", true)
    val ids = BooleanPreference("page_site_ids", true)
    val openMap = BooleanPreference("page_site_open_map", true)
    val elevationProfile = BooleanPreference("page_site_elevation_profile", true)
    val throughputCalculator = BooleanPreference("page_site_throughput_calculator", true)
    val nav = BooleanPreference("page_site_nav", true)
    val share = BooleanPreference("page_site_share", true)
    val dates = BooleanPreference("page_site_dates", true)
    val address = BooleanPreference("page_site_address", true)
    val freqs = BooleanPreference("page_site_freqs", true)
    val links = BooleanPreference("page_site_links", true)

    fun order(prefs: SharedPreferences): List<String> {
        return normalizeOrder((prefs.getString(ORDER, DEFAULT_ORDER) ?: DEFAULT_ORDER).split(","))
    }

    fun saveOrder(prefs: SharedPreferences, order: List<String>) {
        prefs.edit().putString(ORDER, normalizeOrder(order).joinToString(",")).apply()
    }

    fun normalizeOrder(order: List<String>): List<String> {
        val mutableOrder = order.filter { it.isNotBlank() }.toMutableList()
        if (!mutableOrder.contains("speedtest")) {
            val photosIndex = mutableOrder.indexOf("photos")
            if (photosIndex >= 0) mutableOrder.add(photosIndex + 1, "speedtest") else mutableOrder.add("speedtest")
        }
        if (!mutableOrder.contains("open_map")) {
            val elevationProfileIndex = mutableOrder.indexOf("elevation_profile")
            if (elevationProfileIndex >= 0) {
                mutableOrder.add(elevationProfileIndex + 1, "open_map")
            } else {
                val supportDetailsIndex = mutableOrder.indexOf("support_details")
                if (supportDetailsIndex >= 0) mutableOrder.add(supportDetailsIndex + 1, "open_map") else mutableOrder.add("open_map")
            }
        }
        if (!mutableOrder.contains("elevation_profile")) {
            val openMapIndex = mutableOrder.indexOf("open_map")
            if (openMapIndex >= 0) mutableOrder.add(openMapIndex, "elevation_profile") else mutableOrder.add("elevation_profile")
        }
        if (!mutableOrder.contains("throughput_calculator")) {
            val elevationProfileIndex = mutableOrder.indexOf("elevation_profile")
            val openMapIndex = mutableOrder.indexOf("open_map")
            when {
                elevationProfileIndex >= 0 -> mutableOrder.add(elevationProfileIndex + 1, "throughput_calculator")
                openMapIndex >= 0 -> mutableOrder.add(openMapIndex, "throughput_calculator")
                else -> mutableOrder.add("throughput_calculator")
            }
        }
        val openMapIndex = mutableOrder.indexOf("open_map")
        val elevationProfileIndex = mutableOrder.indexOf("elevation_profile")
        if (openMapIndex >= 0 && elevationProfileIndex >= 0 && openMapIndex < elevationProfileIndex) {
            mutableOrder.remove("elevation_profile")
            mutableOrder.remove("open_map")
            mutableOrder.add(openMapIndex, "elevation_profile")
            mutableOrder.add(openMapIndex + 1, "open_map")
        }
        return mutableOrder
    }
}

object SupportPagePrefs {
    const val ORDER = "page_support_order"
    const val MINI_MAP_MODE = "page_support_mini_map_mode"
    const val DEFAULT_ORDER = "map,details,photos,open_map,nav,share,operators"

    val defaultOrder = DEFAULT_ORDER.split(",")
    val map = BooleanPreference("page_support_map", true)
    val details = BooleanPreference("page_support_details", true)
    val photos = BooleanPreference("page_support_photos", true)
    val openMap = BooleanPreference("page_support_open_map", true)
    val nav = BooleanPreference("page_support_nav", true)
    val share = BooleanPreference("page_support_share", true)
    val operators = BooleanPreference("page_support_operators", true)

    fun order(prefs: SharedPreferences): List<String> {
        return normalizeOrder((prefs.getString(ORDER, DEFAULT_ORDER) ?: DEFAULT_ORDER).split(","))
    }

    fun saveOrder(prefs: SharedPreferences, order: List<String>) {
        prefs.edit().putString(ORDER, normalizeOrder(order).joinToString(",")).apply()
    }

    fun normalizeOrder(order: List<String>): List<String> {
        val mutableOrder = order.filter { it.isNotBlank() }.toMutableList()
        mutableOrder.remove("open_map")
        val navIndex = mutableOrder.indexOf("nav")
        if (navIndex >= 0) mutableOrder.add(navIndex, "open_map") else mutableOrder.add("open_map")
        return mutableOrder
    }
}

object SharePrefs {
    const val SITE_ORDER = "share_order"
    const val SUPPORT_ORDER = "share_sup_order"
    const val DEFAULT_SITE_ORDER = "map,elevation_profile,support,ids,dates,address,speedtest,throughput,status,freq"
    const val DEFAULT_SUPPORT_ORDER = "map,support,operators"

    val siteMapEnabled = BooleanPreference("share_map_enabled", true)
    val siteElevationProfileEnabled = BooleanPreference("share_elevation_profile_enabled", true)
    val siteSupportEnabled = BooleanPreference("share_support_enabled", true)
    val siteIdsEnabled = BooleanPreference("share_ids_enabled", true)
    val siteDatesEnabled = BooleanPreference("share_dates_enabled", true)
    val siteAddressEnabled = BooleanPreference("share_address_enabled", true)
    val siteSpeedtestEnabled = BooleanPreference("share_speedtest_enabled", true)
    val siteThroughputEnabled = BooleanPreference("share_throughput_enabled", true)
    val siteFrequencyEnabled = BooleanPreference("share_freq_enabled", true)
    val siteConfidentialEnabled = BooleanPreference("share_confidential_enabled", false)
    val siteQrEnabled = BooleanPreference("share_site_qr_enabled", true)
    val siteSplitImageEnabled = BooleanPreference("share_split_image_enabled", true)
    val supportMapEnabled = BooleanPreference("share_sup_map_enabled", true)
    val supportDetailsEnabled = BooleanPreference("share_sup_support_enabled", true)
    val supportOperatorsEnabled = BooleanPreference("share_sup_operators_enabled", true)
    val supportConfidentialEnabled = BooleanPreference("share_sup_confidential_enabled", false)
    val supportQrEnabled = BooleanPreference("share_sup_qr_enabled", true)
    val mapAzimuths = BooleanPreference("share_map_azimuths", true)
    val mapSpeedometer = BooleanPreference("share_map_speedometer", true)
    val mapScale = BooleanPreference("share_map_scale", true)
    val mapAttribution = BooleanPreference("share_map_attribution", true)
    val mapQrEnabled = BooleanPreference("share_map_qr_enabled", true)
    val mapConfidential = BooleanPreference("share_map_confidential", false)

    fun siteOrder(prefs: SharedPreferences): List<String> {
        return normalizeSiteOrder(prefs.getString(SITE_ORDER, DEFAULT_SITE_ORDER))
    }

    fun saveSiteOrder(prefs: SharedPreferences, order: List<String>) {
        prefs.edit().putString(SITE_ORDER, normalizeSiteOrder(order.joinToString(",")).joinToString(",")).apply()
    }

    fun supportOrder(prefs: SharedPreferences): List<String> {
        return (prefs.getString(SUPPORT_ORDER, DEFAULT_SUPPORT_ORDER) ?: DEFAULT_SUPPORT_ORDER)
            .split(",")
            .filter { it.isNotBlank() }
    }

    fun normalizeSiteOrder(rawOrder: String?): List<String> {
        return (rawOrder ?: DEFAULT_SITE_ORDER)
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableList()
            .apply {
                if (!contains("elevation_profile")) {
                    val mapIndex = indexOf("map")
                    if (mapIndex >= 0) add(mapIndex + 1, "elevation_profile") else add("elevation_profile")
                }
                if (!contains("speedtest")) {
                    val addressIndex = indexOf("address")
                    if (addressIndex >= 0) add(addressIndex + 1, "speedtest") else add("speedtest")
                }
                if (!contains("throughput")) {
                    val speedtestIndex = indexOf("speedtest")
                    if (speedtestIndex >= 0) add(speedtestIndex + 1, "throughput") else add("throughput")
                }
                removeAll { it == "heights" }
            }
            .distinct()
    }
}

object ThroughputPrefs {
    const val DEFAULT_PRESET = "throughput_default_preset"
    const val DEFAULT_PRESET_VALUE = "conservative"
    const val CUSTOM_LTE_DOWN = "throughput_custom_lte_down"
    const val CUSTOM_LTE_UP = "throughput_custom_lte_up"
    const val CUSTOM_NR_DOWN = "throughput_custom_nr_down"
    const val CUSTOM_NR_UP = "throughput_custom_nr_up"
    const val CUSTOM_LTE_RSRP = "throughput_custom_lte_rsrp"
    const val CUSTOM_LTE_SINR = "throughput_custom_lte_sinr"
    const val CUSTOM_NR_RSRP = "throughput_custom_nr_rsrp"
    const val CUSTOM_NR_SINR = "throughput_custom_nr_sinr"
    const val CUSTOM_ENVIRONMENT = "throughput_custom_environment"
    const val CUSTOM_POSITION = "throughput_custom_position"
    const val CUSTOM_NETWORK_LOAD = "throughput_custom_network_load"
    const val CUSTOM_BACKHAUL = "throughput_custom_backhaul"
    const val CUSTOM_LTE_AGGREGATION = "throughput_custom_lte_aggregation"
    const val CUSTOM_DEVICE = "throughput_custom_device"
    const val CUSTOM_SELECTED_LAT = "throughput_custom_selected_lat"
    const val CUSTOM_SELECTED_LON = "throughput_custom_selected_lon"
    val include4G = BooleanPreference("throughput_include_4g", true)
    val include5G = BooleanPreference("throughput_include_5g", true)
    val includePlanned = BooleanPreference("throughput_include_planned", false)

    const val BLOCK_ORDER = "page_throughput_order"
    const val BLOCK_HEADER = "header"
    const val BLOCK_SUMMARY = "summary"
    const val BLOCK_CONE = "cone"
    const val BLOCK_CONTROLS = "controls"
    const val BLOCK_BANDS = "bands"
    const val BLOCK_ASSUMPTIONS = "assumptions"
    const val BLOCK_HEADER_VISIBLE = "page_throughput_header"
    const val BLOCK_SUMMARY_VISIBLE = "page_throughput_summary"
    const val BLOCK_CONE_VISIBLE = "page_throughput_cone"
    const val BLOCK_CONTROLS_VISIBLE = "page_throughput_controls"
    const val BLOCK_BANDS_VISIBLE = "page_throughput_bands"
    const val BLOCK_ASSUMPTIONS_VISIBLE = "page_throughput_assumptions"

    val defaultBlockOrder = listOf(
        BLOCK_HEADER,
        BLOCK_SUMMARY,
        BLOCK_CONE,
        BLOCK_CONTROLS,
        BLOCK_BANDS,
        BLOCK_ASSUMPTIONS
    )

    fun blockOrder(prefs: SharedPreferences): List<String> {
        return normalizeBlockOrder(
            prefs.getString(BLOCK_ORDER, defaultBlockOrder.joinToString(","))
                ?.split(",")
                .orEmpty()
        )
    }

    fun normalizeBlockOrder(order: List<String>): List<String> {
        val knownBlocks = defaultBlockOrder.toSet()
        val normalized = order.map { it.trim() }.filter { it in knownBlocks }.distinct().toMutableList()
        defaultBlockOrder.forEach { block ->
            if (!normalized.contains(block)) normalized.add(block)
        }
        return normalized
    }

    fun bandVisiblePrefKey(generationPrefix: String, bandValue: Int): String {
        return "throughput_band_${generationPrefix}_${bandValue}"
    }

    fun bandVisiblePrefKey(prefSuffix: String): String {
        return "throughput_band_$prefSuffix"
    }
}

object StatsPrefs {
    const val PREF_DISPLAY_MODE = StatsPreferences.PREF_DISPLAY_MODE
    const val PREF_STATS_ORDER = StatsPreferences.PREF_STATS_ORDER
    val defaultStatsBlockOrder = StatsPreferences.defaultStatsBlockOrder
    val defaultTechOrder = StatsPreferences.defaultTechOrder

    fun displayMode(prefs: SharedPreferences): StatsDisplayMode = StatsPreferences.displayMode(prefs)
    fun statsBlockOrder(prefs: SharedPreferences): List<String> = StatsPreferences.statsBlockOrder(prefs)
    fun isStatsBlockVisible(prefs: SharedPreferences, blockId: String): Boolean = StatsPreferences.isStatsBlockVisible(prefs, blockId)
    fun statsFrequencyOrder(prefs: SharedPreferences, tech: String): List<String> = StatsPreferences.statsFrequencyOrder(prefs, tech)
    fun isStatsFrequencyVisible(prefs: SharedPreferences, tech: String, frequencyId: String): Boolean = StatsPreferences.isStatsFrequencyVisible(prefs, tech, frequencyId)
    fun statsBlockVisiblePrefKey(blockId: String): String = StatsPreferences.statsBlockVisiblePrefKey(blockId)
    fun statsFrequencyOrderPrefKey(tech: String): String = StatsPreferences.statsFrequencyOrderPrefKey(tech)
    fun statsFrequencyVisiblePrefKey(tech: String, frequencyId: String): String = StatsPreferences.statsFrequencyVisiblePrefKey(tech, frequencyId)
    fun defaultFrequencyOrder(tech: String): List<String> = StatsPreferences.defaultFrequencyOrder(tech)
    fun normalizeStatsBlockOrder(order: List<String>): List<String> = StatsPreferences.normalizeStatsBlockOrder(order)
    fun normalizeFrequencyOrder(tech: String, order: List<String>): List<String> = StatsPreferences.normalizeFrequencyOrder(tech, order)
    fun normalizeTech(tech: String): String = StatsPreferences.normalizeTech(tech)
    fun frequencyLabel(frequencyId: String): String = StatsPreferences.frequencyLabel(frequencyId)
}

object HomePrefs {
    const val STARTUP_PAGE = "startup_page"
    const val DEFAULT_STARTUP_PAGE = "home"
    const val PAGES_ORDER = "pages_order"
    const val DEFAULT_PAGES_ORDER = "nearby,map,compass,stats,settings"
    val showNearbyPage = BooleanPreference("show_nearby_page", true)
    val showMapPage = BooleanPreference("show_map_page", true)
    val showCompassPage = BooleanPreference("show_compass_page", true)
    val showStatsPage = BooleanPreference("show_stats_page", true)

    fun startupPage(prefs: SharedPreferences): String {
        return prefs.getString(STARTUP_PAGE, DEFAULT_STARTUP_PAGE) ?: DEFAULT_STARTUP_PAGE
    }

    fun pageOrder(prefs: SharedPreferences): List<String> {
        return (prefs.getString(PAGES_ORDER, DEFAULT_PAGES_ORDER) ?: DEFAULT_PAGES_ORDER)
            .split(",")
            .filter { it.isNotBlank() }
    }
}

object WidgetPrefs {
    const val SYNC_FREQUENCY_MINUTES = "widget_sync_freq"
    const val DEFAULT_SYNC_MINUTES = 60
    const val MIN_SYNC_MINUTES = 30
    const val LAST_UPDATE = "widget_last_update"
    const val DATA_API = "widget_data_api"
    const val MAP_IMAGE_PATH = "widget_map_image_path"
    const val MAP_IMAGE_WIDE_PATH = "widget_map_image_wide_path"
    const val MAP_IMAGE_SQUARE_PATH = "widget_map_image_square_path"
    const val MAP_IMAGE_WIDE_EXPANDED_PATH = "widget_map_image_wide_expanded_path"
    const val MAP_IMAGE_SQUARE_EXPANDED_PATH = "widget_map_image_square_expanded_path"
    const val MAP_SITE_COUNT = "widget_map_site_count"
    const val MAP_CENTER_LAT = "widget_map_center_lat"
    const val MAP_CENTER_LON = "widget_map_center_lon"

    fun syncFrequencyMinutes(prefs: SharedPreferences): Int {
        return prefs.getInt(SYNC_FREQUENCY_MINUTES, DEFAULT_SYNC_MINUTES).coerceAtLeast(MIN_SYNC_MINUTES)
    }
}

object LiveTrackingPrefs {
    const val LOCATION_UPDATE_INTERVAL_SECONDS = "live_tracking_location_update_interval_seconds"
    const val DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS = 5
    val LOCATION_UPDATE_INTERVAL_OPTIONS_SECONDS = listOf(5, 10, 15, 20)

    fun locationUpdateIntervalSeconds(prefs: SharedPreferences): Int {
        val rawValue = try {
            prefs.getInt(LOCATION_UPDATE_INTERVAL_SECONDS, DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS)
        } catch (_: ClassCastException) {
            DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS
        }
        return normalizeLocationUpdateIntervalSeconds(rawValue)
    }

    fun normalizeLocationUpdateIntervalSeconds(value: Int): Int {
        return if (value in LOCATION_UPDATE_INTERVAL_OPTIONS_SECONDS) {
            value
        } else {
            DEFAULT_LOCATION_UPDATE_INTERVAL_SECONDS
        }
    }
}
