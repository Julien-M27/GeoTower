package fr.geotower.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.geotower.data.api.RetrofitClient
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

private val OFFICIAL_ANNOUNCEMENT_URLS = listOf(
    OfficialAnnouncementUrl(
        host = "api.cajejuma.fr",
        pathPrefixes = listOf("/status", "/api/", "/geotower/")
    ),
    OfficialAnnouncementUrl(
        host = "cajejuma.fr",
        pathPrefixes = listOf("/geotower/")
    ),
    OfficialAnnouncementUrl(
        host = "www.cajejuma.fr",
        pathPrefixes = listOf("/geotower/")
    )
)

private data class OfficialAnnouncementUrl(
    val host: String,
    val pathPrefixes: List<String>
)

private fun isOfficialAnnouncementUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    val path = uri.path.orEmpty()
    return OFFICIAL_ANNOUNCEMENT_URLS.any { allowed ->
        host == allowed.host && allowed.pathPrefixes.any { prefix -> path.startsWith(prefix) }
    }
}

private val APP_VERSION_NUMBER_REGEX = Regex("\\d+")

private fun compareAppVersionNames(left: String, right: String): Int? {
    val leftParts = left.versionNumberParts()
    val rightParts = right.versionNumberParts()
    if (leftParts.isEmpty() || rightParts.isEmpty()) return null

    val maxSize = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    return 0
}

private fun String.versionNumberParts(): List<Int> {
    return APP_VERSION_NUMBER_REGEX.findAll(this)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
}

data class RemoteHomeAnnouncementText(
    val title: String = "",
    val message: String = "",
    val actionLabel: String = ""
) {
    fun hasContent(): Boolean = title.isNotBlank() || message.isNotBlank()
}

data class RemoteHomeAnnouncement(
    val enabled: Boolean = false,
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = "info",
    val actionLabel: String = "",
    val actionUrl: String = "",
    val dismissible: Boolean = true,
    val minAppVersionInclusive: String = "",
    val maxAppVersionExclusive: String = "",
    val translations: Map<String, RemoteHomeAnnouncementText> = emptyMap()
) {
    fun localizedText(languageTag: String?): RemoteHomeAnnouncementText {
        val fallback = RemoteHomeAnnouncementText(
            title = title,
            message = message,
            actionLabel = actionLabel
        )
        val normalizedTag = normalizeLanguageTag(languageTag)
        val languageOnly = normalizedTag.substringBefore("-")
        val translated = translations[normalizedTag]
            ?: translations[languageOnly]
            ?: translations["fr"]
            ?: translations["en"]
            ?: translations.values.firstOrNull { it.hasContent() }
        return RemoteHomeAnnouncementText(
            title = translated?.title?.ifBlank { fallback.title } ?: fallback.title,
            message = translated?.message?.ifBlank { fallback.message } ?: fallback.message,
            actionLabel = translated?.actionLabel?.ifBlank { fallback.actionLabel } ?: fallback.actionLabel
        )
    }

    fun hasContent(languageTag: String? = null): Boolean = localizedText(languageTag).hasContent()

    fun isVisibleForAppVersion(installedVersionName: String): Boolean {
        val minVersion = minAppVersionInclusive.trim()
        if (minVersion.isNotBlank()) {
            val comparison = compareAppVersionNames(installedVersionName, minVersion)
            if (comparison != null && comparison < 0) return false
        }

        val maxVersion = maxAppVersionExclusive.trim()
        if (maxVersion.isNotBlank()) {
            val comparison = compareAppVersionNames(installedVersionName, maxVersion)
            if (comparison != null && comparison >= 0) return false
        }

        return true
    }

    fun dismissKey(): String = id.ifBlank { "$title\n$message\n${translations.hashCode()}".hashCode().toString() }

    fun httpActionUrlOrNull(): String? {
        return actionUrl.takeIf { isOfficialAnnouncementUrl(it) }
    }

    private fun normalizeLanguageTag(languageTag: String?): String {
        return languageTag
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace("_", "-")
            .orEmpty()
    }
}

data class RemoteFeatureFlagConfig(
    val cacheTtlSeconds: Long,
    val screens: Map<String, Boolean>,
    val menus: Map<String, Boolean>,
    val features: Map<String, Boolean>,
    val actions: Map<String, Boolean>,
    val providers: Map<String, Boolean>,
    val workers: Map<String, Boolean>,
    val platform: Map<String, Boolean>,
    val limits: Map<String, Int>,
    val homeAnnouncement: RemoteHomeAnnouncement
) {
    fun isScreenEnabled(screenId: String): Boolean = screens[screenId] ?: true

    fun isMenuEnabled(menuId: String): Boolean = menus[menuId] ?: true

    fun isFeatureEnabled(featureId: String): Boolean = features[featureId] ?: true

    fun isActionEnabled(actionId: String): Boolean = actions[actionId] ?: true

    fun isProviderEnabled(providerId: String): Boolean = providers[providerId] ?: true

    fun isWorkerEnabled(workerId: String): Boolean = workers[workerId] ?: true

    fun isPlatformEnabled(platformId: String): Boolean = platform[platformId] ?: true

    fun limitOrDefault(limitId: String, defaultValue: Int): Int = limits[limitId] ?: defaultValue

    fun isCommunitySourceEnabled(featureId: String, sourceId: String): Boolean {
        return when {
            featureId == "photos" && sourceId == "signalquest" -> isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_PHOTOS)
            featureId == "photos" && sourceId == "cellularfr" -> isFeatureEnabled(RemoteFeatureFlags.Features.CELLULARFR_PHOTOS)
            featureId == "speedtest" && sourceId == "signalquest" -> isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_SPEEDTESTS)
            else -> true
        }
    }

    fun isSiteExternalLinkEnabled(linkId: String): Boolean {
        return when (linkId) {
            "signalquest" -> isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_EXTERNAL_LINKS)
            "cellularfr" -> isFeatureEnabled(RemoteFeatureFlags.Features.CELLULARFR_EXTERNAL_LINKS)
            else -> true
        }
    }
}

object RemoteFeatureFlags {
    private const val TAG = "GeoTowerFeatureFlags"
    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val PREF_CONFIG_JSON = "remote_feature_flags_json"
    private const val PREF_FETCHED_AT = "remote_feature_flags_fetched_at"
    private const val FEATURE_FLAGS_URL = "${RetrofitClient.BASE_URL}api/v2/app/features"
    private val validAnnouncementSeverities = setOf("info", "warning", "error", "success")

    object Screens {
        const val HOME = "home"
        const val NEARBY = "nearby"
        const val MAP = "map"
        const val COMPASS = "compass"
        const val STATS = "stats"
        const val SETTINGS = "settings"
        const val HELP = "help"
        const val ABOUT = "about"
        const val PHOTO_UPLOAD_HISTORY = "photoUploadHistory"
        const val SUPPORT_DETAIL = "supportDetail"
        const val SITE_DETAIL = "siteDetail"
        const val SITE_SPEEDTESTS = "siteSpeedtests"
        const val ELEVATION_PROFILE = "elevationProfile"
        const val THROUGHPUT_CALCULATOR = "throughputCalculator"
        const val SIGNALQUEST_UPLOAD = "signalQuestUpload"
        const val FIRST_START = "firstStart"
    }

    object Menus {
        const val PAGES_CUSTOMIZATION = "pagesCustomization"
        const val COMMUNITY_DATA_SETTINGS = "communityDataSettings"
        const val EXTERNAL_LINKS_SETTINGS = "externalLinksSettings"
        const val PHOTO_SETTINGS = "photoSettings"
        const val SHARE_SETTINGS = "shareSettings"
        const val MAP_SETTINGS = "mapSettings"
        const val SITE_SETTINGS = "siteSettings"
        const val SUPPORT_SETTINGS = "supportSettings"
        const val STATS_SETTINGS = "statsSettings"
        const val THROUGHPUT_SETTINGS = "throughputSettings"
    }

    object Features {
        const val DATABASE_DOWNLOAD = "database.download"
        const val DATABASE_UPDATE_CHECK = "database.updateCheck"
        const val APP_UPDATE_CHECK = "appUpdate.check"
        const val OUTAGES_DATA = "outages.data"
        const val OUTAGES_MAP_LAYER = "outages.mapLayer"
        const val OUTAGES_SITE_STATUS = "outages.siteStatus"
        const val OFFLINE_MAPS_CATALOG = "offlineMaps.catalog"
        const val OFFLINE_MAPS_DOWNLOAD = "offlineMaps.download"
        const val MAP_SEARCH_NOMINATIM = "map.search.nominatim"
        const val MAP_CITY_BOUNDARIES = "map.cityBoundaries"
        const val MAP_MEASURE = "map.measure"
        const val MAP_SHARE = "map.share"
        const val MAP_AZIMUTHS = "map.azimuths"
        const val MAP_LOCATION = "map.location"
        const val SITE_PHOTOS = "site.photos"
        const val SITE_PHOTO_UPLOAD = "site.photoUpload"
        const val SITE_PHOTO_CAMERA = "site.photoCamera"
        const val SITE_PHOTO_GALLERY = "site.photoGallery"
        const val SITE_PHOTO_EXIF = "site.photoExif"
        const val SITE_SCHEMES = "site.schemes"
        const val SITE_SPEEDTESTS = "site.speedtests"
        const val SITE_EXTERNAL_NAVIGATION = "site.externalNavigation"
        const val SITE_SHARE = "site.share"
        const val SITE_FREQUENCIES = "site.frequencies"
        const val SITE_SPECTRUM = "site.spectrum"
        const val SITE_ELEVATION_PROFILE = "site.elevationProfile"
        const val SITE_THROUGHPUT_CALCULATOR = "site.throughputCalculator"
        const val SUPPORT_PHOTOS = "support.photos"
        const val SUPPORT_EXTERNAL_NAVIGATION = "support.externalNavigation"
        const val SUPPORT_SHARE = "support.share"
        const val STATS_FREQUENCY_DETAILS = "stats.frequencyDetails"
        const val STATS_HISTORY = "stats.history"
        const val COMPASS_RADAR = "compass.radar"
        const val COMPASS_REVERSE_GEOCODING = "compass.reverseGeocoding"
        const val SIGNALQUEST_PHOTOS = "signalQuest.photos"
        const val SIGNALQUEST_UPLOAD = "signalQuest.upload"
        const val SIGNALQUEST_SPEEDTESTS = "signalQuest.speedtests"
        const val SIGNALQUEST_EXTERNAL_LINKS = "signalQuest.externalLinks"
        const val CELLULARFR_PHOTOS = "cellularFr.photos"
        const val CELLULARFR_EXTERNAL_LINKS = "cellularFr.externalLinks"
        const val EXTERNAL_LINKS_CARTORADIO = "externalLinks.cartoradio"
        const val EXTERNAL_LINKS_RNC_MOBILE = "externalLinks.rncMobile"
        const val EXTERNAL_LINKS_ENB_ANALYTICS = "externalLinks.enbAnalytics"
        const val EXTERNAL_LINKS_SIGNALQUEST = "externalLinks.signalQuest"
        const val EXTERNAL_LINKS_CELLULARFR = "externalLinks.cellularFr"
        const val EXTERNAL_LINKS_ANFR = "externalLinks.anfr"
    }

    object Actions {
        const val SHARE_SITE = "share.site"
        const val SHARE_SUPPORT = "share.support"
        const val SHARE_MAP = "share.map"
        const val OPEN_EXTERNAL_NAVIGATION = "externalNavigation.open"
        const val OPEN_EXTERNAL_LINK = "externalLink.open"
        const val START_DATABASE_DOWNLOAD = "databaseDownload.start"
        const val START_OFFLINE_MAP_DOWNLOAD = "offlineMapDownload.start"
        const val START_SIGNALQUEST_UPLOAD = "signalQuestUpload.start"
    }

    object Providers {
        const val MAP_IGN = "map.ign"
        const val MAP_OSM = "map.osm"
        const val MAP_MAPLIBRE = "map.mapLibre"
        const val MAP_OPEN_TOPO = "map.openTopo"
        const val MAP_OFFLINE = "map.offline"
        const val SEARCH_NOMINATIM = "search.nominatim"
        const val ELEVATION_IGN = "elevation.ign"
        const val OUTAGES_GEOTOWER = "outages.geotower"
        const val SIGNALQUEST = "signalQuest"
        const val CELLULARFR = "cellularFr"
        const val CARTORADIO = "cartoradio"
        const val RNC_MOBILE = "rncMobile"
        const val ENB_ANALYTICS = "enbAnalytics"
        const val ANFR = "anfr"
    }

    object Workers {
        const val DATABASE_DOWNLOAD = "databaseDownload"
        const val DATABASE_UPDATE_CHECK = "databaseUpdateCheck"
        const val APP_UPDATE_CHECK = "appUpdateCheck"
        const val OFFLINE_MAP_DOWNLOAD = "offlineMapDownload"
        const val SIGNALQUEST_UPLOAD = "signalQuestUpload"
        const val WIDGET_UPDATE = "widgetUpdate"
    }

    object Platform {
        const val WIDGETS = "widgets"
        const val ANDROID_AUTO = "androidAuto"
        const val LIVE_TRACKING = "liveTracking"
        const val NOTIFICATIONS = "notifications"
        const val PROMOTED_NOTIFICATIONS = "promotedNotifications"
        const val BACKGROUND_LOCATION_PROMPT = "backgroundLocationPrompt"
    }

    object Limits {
        const val NEARBY_MAX_RADIUS_KM = "nearbyMaxRadiusKm"
        const val PHOTO_UPLOAD_MAX_COUNT = "photoUploadMaxCount"
        const val PHOTO_UPLOAD_MAX_SIZE_MB = "photoUploadMaxSizeMb"
        const val OFFLINE_MAP_MAX_PARALLEL_DOWNLOADS = "offlineMapMaxParallelDownloads"
        const val MAP_SEARCH_MIN_QUERY_LENGTH = "mapSearchMinQueryLength"
    }

    val defaultConfig = RemoteFeatureFlagConfig(
        cacheTtlSeconds = 3600L,
        screens = mapOf(
            Screens.HOME to true,
            Screens.NEARBY to true,
            Screens.MAP to true,
            Screens.COMPASS to true,
            Screens.STATS to true,
            Screens.SETTINGS to true,
            Screens.HELP to true,
            Screens.ABOUT to true,
            Screens.PHOTO_UPLOAD_HISTORY to true,
            Screens.SUPPORT_DETAIL to true,
            Screens.SITE_DETAIL to true,
            Screens.SITE_SPEEDTESTS to true,
            Screens.ELEVATION_PROFILE to true,
            Screens.THROUGHPUT_CALCULATOR to true,
            Screens.SIGNALQUEST_UPLOAD to true,
            Screens.FIRST_START to true
        ),
        menus = mapOf(
            Menus.PAGES_CUSTOMIZATION to true,
            Menus.COMMUNITY_DATA_SETTINGS to true,
            Menus.EXTERNAL_LINKS_SETTINGS to true,
            Menus.PHOTO_SETTINGS to true,
            Menus.SHARE_SETTINGS to true,
            Menus.MAP_SETTINGS to true,
            Menus.SITE_SETTINGS to true,
            Menus.SUPPORT_SETTINGS to true,
            Menus.STATS_SETTINGS to true,
            Menus.THROUGHPUT_SETTINGS to true
        ),
        features = mapOf(
            Features.DATABASE_DOWNLOAD to true,
            Features.DATABASE_UPDATE_CHECK to true,
            Features.APP_UPDATE_CHECK to true,
            Features.OUTAGES_DATA to true,
            Features.OUTAGES_MAP_LAYER to true,
            Features.OUTAGES_SITE_STATUS to true,
            Features.OFFLINE_MAPS_CATALOG to true,
            Features.OFFLINE_MAPS_DOWNLOAD to true,
            Features.MAP_SEARCH_NOMINATIM to true,
            Features.MAP_CITY_BOUNDARIES to true,
            Features.MAP_MEASURE to true,
            Features.MAP_SHARE to true,
            Features.MAP_AZIMUTHS to true,
            Features.MAP_LOCATION to true,
            Features.SITE_PHOTOS to true,
            Features.SITE_PHOTO_UPLOAD to true,
            Features.SITE_PHOTO_CAMERA to true,
            Features.SITE_PHOTO_GALLERY to true,
            Features.SITE_PHOTO_EXIF to true,
            Features.SITE_SCHEMES to true,
            Features.SITE_SPEEDTESTS to true,
            Features.SITE_EXTERNAL_NAVIGATION to true,
            Features.SITE_SHARE to true,
            Features.SITE_FREQUENCIES to true,
            Features.SITE_SPECTRUM to true,
            Features.SITE_ELEVATION_PROFILE to true,
            Features.SITE_THROUGHPUT_CALCULATOR to true,
            Features.SUPPORT_PHOTOS to true,
            Features.SUPPORT_EXTERNAL_NAVIGATION to true,
            Features.SUPPORT_SHARE to true,
            Features.STATS_FREQUENCY_DETAILS to true,
            Features.STATS_HISTORY to true,
            Features.COMPASS_RADAR to true,
            Features.COMPASS_REVERSE_GEOCODING to true,
            Features.SIGNALQUEST_PHOTOS to true,
            Features.SIGNALQUEST_UPLOAD to true,
            Features.SIGNALQUEST_SPEEDTESTS to true,
            Features.SIGNALQUEST_EXTERNAL_LINKS to true,
            Features.CELLULARFR_PHOTOS to false,
            Features.CELLULARFR_EXTERNAL_LINKS to false,
            Features.EXTERNAL_LINKS_CARTORADIO to true,
            Features.EXTERNAL_LINKS_RNC_MOBILE to true,
            Features.EXTERNAL_LINKS_ENB_ANALYTICS to true,
            Features.EXTERNAL_LINKS_SIGNALQUEST to true,
            Features.EXTERNAL_LINKS_CELLULARFR to false,
            Features.EXTERNAL_LINKS_ANFR to true
        ),
        actions = mapOf(
            Actions.SHARE_SITE to true,
            Actions.SHARE_SUPPORT to true,
            Actions.SHARE_MAP to true,
            Actions.OPEN_EXTERNAL_NAVIGATION to true,
            Actions.OPEN_EXTERNAL_LINK to true,
            Actions.START_DATABASE_DOWNLOAD to true,
            Actions.START_OFFLINE_MAP_DOWNLOAD to true,
            Actions.START_SIGNALQUEST_UPLOAD to true
        ),
        providers = mapOf(
            Providers.MAP_IGN to true,
            Providers.MAP_OSM to true,
            Providers.MAP_MAPLIBRE to true,
            Providers.MAP_OPEN_TOPO to true,
            Providers.MAP_OFFLINE to true,
            Providers.SEARCH_NOMINATIM to true,
            Providers.ELEVATION_IGN to true,
            Providers.OUTAGES_GEOTOWER to true,
            Providers.SIGNALQUEST to true,
            Providers.CELLULARFR to false,
            Providers.CARTORADIO to true,
            Providers.RNC_MOBILE to true,
            Providers.ENB_ANALYTICS to true,
            Providers.ANFR to true
        ),
        workers = mapOf(
            Workers.DATABASE_DOWNLOAD to true,
            Workers.DATABASE_UPDATE_CHECK to true,
            Workers.APP_UPDATE_CHECK to true,
            Workers.OFFLINE_MAP_DOWNLOAD to true,
            Workers.SIGNALQUEST_UPLOAD to true,
            Workers.WIDGET_UPDATE to true
        ),
        platform = mapOf(
            Platform.WIDGETS to true,
            Platform.ANDROID_AUTO to true,
            Platform.LIVE_TRACKING to true,
            Platform.NOTIFICATIONS to true,
            Platform.PROMOTED_NOTIFICATIONS to true,
            Platform.BACKGROUND_LOCATION_PROMPT to true
        ),
        limits = mapOf(
            Limits.NEARBY_MAX_RADIUS_KM to 50,
            Limits.PHOTO_UPLOAD_MAX_COUNT to 20,
            Limits.PHOTO_UPLOAD_MAX_SIZE_MB to 10,
            Limits.OFFLINE_MAP_MAX_PARALLEL_DOWNLOADS to 1,
            Limits.MAP_SEARCH_MIN_QUERY_LENGTH to 2
        ),
        homeAnnouncement = RemoteHomeAnnouncement()
    )

    private val currentConfig = mutableStateOf(defaultConfig)
    val config: State<RemoteFeatureFlagConfig> get() = currentConfig

    fun isScreenEnabled(screenId: String): Boolean = currentConfig.value.isScreenEnabled(screenId)

    fun isMenuEnabled(menuId: String): Boolean = currentConfig.value.isMenuEnabled(menuId)

    fun isFeatureEnabled(featureId: String): Boolean = currentConfig.value.isFeatureEnabled(featureId)

    fun isActionEnabled(actionId: String): Boolean = currentConfig.value.isActionEnabled(actionId)

    fun isProviderEnabled(providerId: String): Boolean = currentConfig.value.isProviderEnabled(providerId)

    fun isWorkerEnabled(workerId: String): Boolean = currentConfig.value.isWorkerEnabled(workerId)

    fun isPlatformEnabled(platformId: String): Boolean = currentConfig.value.isPlatformEnabled(platformId)

    fun limitOrDefault(limitId: String, defaultValue: Int): Int = currentConfig.value.limitOrDefault(limitId, defaultValue)

    fun loadCached(context: Context) {
        val prefs = prefs(context)
        val cachedJson = prefs.getString(PREF_CONFIG_JSON, null) ?: return
        val cachedConfig = parseConfig(cachedJson) ?: return
        currentConfig.value = cachedConfig
    }

    suspend fun refreshIfNeeded(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val now = System.currentTimeMillis()
        val fetchedAt = prefs.getLong(PREF_FETCHED_AT, 0L)
        val ttlMillis = TimeUnit.SECONDS.toMillis(currentConfig.value.cacheTtlSeconds)
        if (!force && fetchedAt > 0L && now - fetchedAt < ttlMillis) return

        val request = Request.Builder()
            .url(FEATURE_FLAGS_URL)
            .header("Accept", "application/json")
            .build()

        val nextConfig = withContext(Dispatchers.IO) {
            try {
                RetrofitClient.currentClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val parsed = parseConfig(body) ?: return@withContext null
                    prefs.edit()
                        .putString(PREF_CONFIG_JSON, body)
                        .putLong(PREF_FETCHED_AT, System.currentTimeMillis())
                        .apply()
                    parsed
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Remote feature flags refresh failed", e)
                null
            }
        } ?: return

        withContext(Dispatchers.Main) {
            currentConfig.value = nextConfig
        }
    }

    internal fun parseConfig(rawJson: String): RemoteFeatureFlagConfig? {
        return runCatching {
            val root = JsonParser.parseString(rawJson).asJsonObjectOrNull() ?: return@runCatching null
            val cacheTtlSeconds = root.longOrNull("cacheTtlSeconds")
                ?.coerceIn(60L, 24L * 60L * 60L)
                ?: defaultConfig.cacheTtlSeconds

            RemoteFeatureFlagConfig(
                cacheTtlSeconds = cacheTtlSeconds,
                screens = mergeBooleanMap(defaultConfig.screens, root.booleanMap("screens")),
                menus = mergeBooleanMap(defaultConfig.menus, root.booleanMap("menus")),
                features = mergeBooleanMap(defaultConfig.features, root.booleanMap("features")),
                actions = mergeBooleanMap(defaultConfig.actions, root.booleanMap("actions")),
                providers = mergeBooleanMap(defaultConfig.providers, root.booleanMap("providers")),
                workers = mergeBooleanMap(defaultConfig.workers, root.booleanMap("workers")),
                platform = mergeBooleanMap(defaultConfig.platform, root.booleanMap("platform")),
                limits = mergeIntMap(defaultConfig.limits, root.intMap("limits")),
                homeAnnouncement = root.homeAnnouncementOrDefault()
            )
        }.getOrNull()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun mergeBooleanMap(defaults: Map<String, Boolean>, overrides: Map<String, Boolean>): Map<String, Boolean> {
        return defaults + overrides
    }

    private fun mergeIntMap(defaults: Map<String, Int>, overrides: Map<String, Int>): Map<String, Int> {
        return defaults + overrides
    }

    private fun JsonObject.longOrNull(memberName: String): Long? {
        return get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() }
    }

    private fun JsonObject.booleanOrDefault(memberName: String, defaultValue: Boolean): Boolean {
        return get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asBoolean }.getOrNull() }
            ?: defaultValue
    }

    private fun JsonObject.stringOrBlank(memberName: String, maxLength: Int): String {
        return get(memberName)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asString.trim().take(maxLength) }.getOrNull() }
            ?: ""
    }

    private fun JsonObject.homeAnnouncementOrDefault(): RemoteHomeAnnouncement {
        val obj = get("homeAnnouncement")?.asJsonObjectOrNull() ?: return defaultConfig.homeAnnouncement
        val severity = obj.stringOrBlank("severity", 24)
            .lowercase(Locale.ROOT)
            .takeIf { it in validAnnouncementSeverities }
            ?: defaultConfig.homeAnnouncement.severity
        val actionUrl = obj.stringOrBlank("actionUrl", 500).takeIf(::isOfficialAnnouncementUrl) ?: ""

        val announcement = RemoteHomeAnnouncement(
            enabled = obj.booleanOrDefault("enabled", defaultConfig.homeAnnouncement.enabled),
            id = obj.stringOrBlank("id", 80),
            title = obj.stringOrBlank("title", 100),
            message = obj.stringOrBlank("message", 1200),
            severity = severity,
            actionLabel = obj.stringOrBlank("actionLabel", 48),
            actionUrl = actionUrl,
            dismissible = obj.booleanOrDefault("dismissible", defaultConfig.homeAnnouncement.dismissible),
            minAppVersionInclusive = obj.stringOrBlank("minAppVersionInclusive", 64),
            maxAppVersionExclusive = obj.stringOrBlank("maxAppVersionExclusive", 64),
            translations = obj.announcementTranslations("translations")
        )
        return if (announcement.enabled && !announcement.hasContent()) {
            announcement.copy(enabled = false)
        } else {
            announcement
        }
    }

    private fun JsonObject.announcementTranslations(memberName: String): Map<String, RemoteHomeAnnouncementText> {
        val obj = get(memberName)?.asJsonObjectOrNull() ?: return emptyMap()
        return obj.entrySet().mapNotNull { (rawLanguage, rawTexts) ->
            val language = rawLanguage.normalizeLanguageKey()
            val textObj = rawTexts.asJsonObjectOrNull() ?: return@mapNotNull null
            val text = RemoteHomeAnnouncementText(
                title = textObj.stringOrBlank("title", 100),
                message = textObj.stringOrBlank("message", 1200),
                actionLabel = textObj.stringOrBlank("actionLabel", 48)
            )
            if (language.isBlank() || (!text.hasContent() && text.actionLabel.isBlank())) {
                null
            } else {
                language to text
            }
        }.toMap()
    }

    private fun String.normalizeLanguageKey(): String {
        val normalized = trim()
            .lowercase(Locale.ROOT)
            .replace("_", "-")
            .take(20)
        return normalized.takeIf { key ->
            key.isNotBlank() && key.all { it.isLetterOrDigit() || it == '-' }
        }.orEmpty()
    }

    private fun JsonObject.booleanMap(memberName: String): Map<String, Boolean> {
        val obj = get(memberName)?.asJsonObjectOrNull() ?: return emptyMap()
        return obj.entrySet().mapNotNull { (key, value) ->
            val booleanValue = value
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.let { runCatching { it.asBoolean }.getOrNull() }
            booleanValue?.let { key to it }
        }.toMap()
    }

    private fun JsonObject.intMap(memberName: String): Map<String, Int> {
        val obj = get(memberName)?.asJsonObjectOrNull() ?: return emptyMap()
        return obj.entrySet().mapNotNull { (key, value) ->
            val intValue = value
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.let { runCatching { it.asInt.coerceIn(0, 100_000) }.getOrNull() }
            intValue?.let { key to it }
        }.toMap()
    }

    private fun Any?.asJsonObjectOrNull(): JsonObject? {
        return (this as? com.google.gson.JsonElement)
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
    }
}
