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
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    fun dismissKey(): String = id.ifBlank { "$title\n$message\n${translations.hashCode()}".hashCode().toString() }

    fun httpActionUrlOrNull(): String? {
        return actionUrl.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }
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
    val homeAnnouncement: RemoteHomeAnnouncement
) {
    fun isScreenEnabled(screenId: String): Boolean = screens[screenId] ?: true

    fun isMenuEnabled(menuId: String): Boolean = menus[menuId] ?: true

    fun isFeatureEnabled(featureId: String): Boolean = features[featureId] ?: true

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
        const val SUPPORT_DETAIL = "supportDetail"
        const val SITE_DETAIL = "siteDetail"
        const val THROUGHPUT_CALCULATOR = "throughputCalculator"
    }

    object Menus {
        const val PAGES_CUSTOMIZATION = "pagesCustomization"
        const val COMMUNITY_DATA_SETTINGS = "communityDataSettings"
        const val EXTERNAL_LINKS_SETTINGS = "externalLinksSettings"
        const val PHOTO_SETTINGS = "photoSettings"
        const val SHARE_SETTINGS = "shareSettings"
    }

    object Features {
        const val SIGNALQUEST_PHOTOS = "signalQuest.photos"
        const val SIGNALQUEST_UPLOAD = "signalQuest.upload"
        const val SIGNALQUEST_SPEEDTESTS = "signalQuest.speedtests"
        const val SIGNALQUEST_EXTERNAL_LINKS = "signalQuest.externalLinks"
        const val CELLULARFR_PHOTOS = "cellularFr.photos"
        const val CELLULARFR_EXTERNAL_LINKS = "cellularFr.externalLinks"
    }

    val defaultConfig = RemoteFeatureFlagConfig(
        cacheTtlSeconds = 3600L,
        screens = mapOf(
            Screens.HOME to true,
            Screens.NEARBY to true,
            Screens.MAP to true,
            Screens.COMPASS to true,
            Screens.STATS to true,
            Screens.SUPPORT_DETAIL to true,
            Screens.SITE_DETAIL to true,
            Screens.THROUGHPUT_CALCULATOR to true
        ),
        menus = mapOf(
            Menus.PAGES_CUSTOMIZATION to true,
            Menus.COMMUNITY_DATA_SETTINGS to true,
            Menus.EXTERNAL_LINKS_SETTINGS to true,
            Menus.PHOTO_SETTINGS to true,
            Menus.SHARE_SETTINGS to true
        ),
        features = mapOf(
            Features.SIGNALQUEST_PHOTOS to true,
            Features.SIGNALQUEST_UPLOAD to true,
            Features.SIGNALQUEST_SPEEDTESTS to true,
            Features.SIGNALQUEST_EXTERNAL_LINKS to true,
            Features.CELLULARFR_PHOTOS to false,
            Features.CELLULARFR_EXTERNAL_LINKS to false
        ),
        homeAnnouncement = RemoteHomeAnnouncement()
    )

    private val currentConfig = mutableStateOf(defaultConfig)
    val config: State<RemoteFeatureFlagConfig> get() = currentConfig

    fun isScreenEnabled(screenId: String): Boolean = currentConfig.value.isScreenEnabled(screenId)

    fun isMenuEnabled(menuId: String): Boolean = currentConfig.value.isMenuEnabled(menuId)

    fun isFeatureEnabled(featureId: String): Boolean = currentConfig.value.isFeatureEnabled(featureId)

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
        val actionUrl = obj.stringOrBlank("actionUrl", 500).takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        } ?: ""

        val announcement = RemoteHomeAnnouncement(
            enabled = obj.booleanOrDefault("enabled", defaultConfig.homeAnnouncement.enabled),
            id = obj.stringOrBlank("id", 80),
            title = obj.stringOrBlank("title", 100),
            message = obj.stringOrBlank("message", 1200),
            severity = severity,
            actionLabel = obj.stringOrBlank("actionLabel", 48),
            actionUrl = actionUrl,
            dismissible = obj.booleanOrDefault("dismissible", defaultConfig.homeAnnouncement.dismissible),
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

    private fun Any?.asJsonObjectOrNull(): JsonObject? {
        return (this as? com.google.gson.JsonElement)
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
    }
}
