package fr.geotower.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import fr.geotower.data.workers.UpdateCheckScheduler
import fr.geotower.services.LiveTrackingController
import fr.geotower.widget.WidgetUpdateScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.UUID

data class PreferenceProfileValue(
    val type: String,
    val value: Any
) {
    fun put(editor: SharedPreferences.Editor, key: String): SharedPreferences.Editor {
        return when (type) {
            TYPE_BOOLEAN -> editor.putBoolean(key, value as Boolean)
            TYPE_INT -> editor.putInt(key, value as Int)
            TYPE_LONG -> editor.putLong(key, value as Long)
            TYPE_FLOAT -> editor.putFloat(key, value as Float)
            TYPE_STRING_SET -> editor.putStringSet(key, value as Set<String>)
            else -> editor.putString(key, value as String)
        }
    }

    companion object {
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_INT = "int"
        const val TYPE_LONG = "long"
        const val TYPE_FLOAT = "float"
        const val TYPE_STRING = "string"
        const val TYPE_STRING_SET = "string_set"
    }
}

data class PreferenceProfile(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val icon: String,
    val createdAt: Long,
    val updatedAt: Long,
    val values: Map<String, PreferenceProfileValue>,
    val imagePath: String? = null,
    val pendingImageBase64: String? = null,
    val pendingImageMimeType: String? = null
) {
    val isDefault: Boolean
        get() = id == PreferenceProfileManager.DEFAULT_PROFILE_ID
}

data class PreferenceProfileChange(
    val key: String,
    val section: String,
    val label: String,
    val oldValue: String,
    val newValue: String
)

data class PreferenceProfileImportPreview(
    val profiles: List<PreferenceProfile>,
    val conflicts: List<PreferenceProfileImportConflict>
)

data class PreferenceProfileImportConflict(
    val importedProfile: PreferenceProfile,
    val existingProfile: PreferenceProfile
)

enum class PreferenceProfileImportResolution {
    RenameImported,
    ReplaceExisting
}

data class PreferenceProfileImportResult(
    val addedCount: Int,
    val replacedCount: Int,
    val activeProfileChanged: Boolean
)

object PreferenceProfileManager {
    const val DEFAULT_PROFILE_ID = "default"
    const val DEFAULT_PROFILE_NAME = "Par défaut"
    const val STORE_KEY = "__preference_profiles_json"
    const val ACTIVE_PROFILE_ID_KEY = "__active_preference_profile_id"

    private const val STORE_SCHEMA_VERSION = 1
    // v2 : "menuSize" (petit/normal/large) remplace par "ui_scale_percent" (Int). Les exports v1
    // restent lisibles (conversion transparente dans profileFromJson).
    private const val EXPORT_SCHEMA_VERSION = 2
    private const val EXPORT_MIME_TYPE = "application/json"
    private const val PROFILE_EXPORT_FILE_PREFIX = "geotower_profil_"
    private const val PROFILES_EXPORT_FILE_PREFIX = "geotower_profils_"

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var suppressProfileSync = false
    private val compactPersonalizedNameRegex = Regex("^Personnalisé(\\d+)$")

    private val explicitVisibleKeys = setOf(
        "theme_mode",
        "is_oled_mode",
        "is_blur_enabled",
        AppConfig.PREF_UI_SCALE_PERCENT,
        "app_language",
        "map_provider",
        "ign_style",
        "nav_mode",
        "display_style",
        "distance_unit",
        "speed_unit",
        "default_operator",
        "enable_update_notifications",
        "enable_live_notifications",
        "nearby_search_radius",
        "nearby_order",
        "compass_order",
        "home_logo_choice",
        "pages_order",
        "startup_page",
        "external_links_order",
        "page_site_external_links_order",
        "link_cartoradio",
        "link_cellularfr",
        "link_signalquest",
        "link_cellmapper",
        "link_rncmobile",
        "link_enbanalytics",
        "show_anfr",
        "widget_sync_freq",
        "live_tracking_location_update_interval_seconds",
        AppConfig.PREF_COLOR_PALETTE,
        AppConfig.PREF_SELECTED_OPERATORS,
        AppConfig.PREF_UI_MODE,
        AppConfig.PREF_SHOW_MAP_LOCATION_MARKER,
        AppConfig.PREF_SHOW_AZIMUTH_LINES,
        AppConfig.PREF_SHOW_AZIMUTH_CONES,
        AppConfig.PREF_SHOW_RADIO_SITES,
        AppConfig.PREF_SHOW_RADIO_TV,
        AppConfig.PREF_SHOW_RADIO_BROADCAST,
        AppConfig.PREF_SHOW_RADIO_PRIVATE_MOBILE,
        AppConfig.PREF_SHOW_RADIO_FH,
        AppConfig.PREF_SHOW_RADIO_OTHER,
        AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS,
        AppConfig.PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS,
        AppConfig.PREF_HIDE_UNDERGROUND_SITES,
        AppConfig.PREF_SHOW_ONLY_ZB_SITES,
        AppLogoDrawingResources.PREF_KEY
    )

    private val visiblePrefixes = setOf(
        "page_",
        "share_",
        "site_show_",
        "site_freq_",
        "site_techno_",
        "community_",
        "show_",
        "f2g_",
        "f3g_",
        "f4g_",
        "f5g_",
        "throughput_"
    )

    private val excludedPrefixes = setOf(
        "__",
        "last_",
        "clicked_",
        "widget_last_",
        "widget_data_",
        "widget_map_",
        "site_photo_favorite_",
        "download_",
        "map_download_",
        "remote_feature_",
        "sq_",
        "pending_"
    )

    private val excludedKeys = setOf(
        STORE_KEY,
        ACTIVE_PROFILE_ID_KEY,
        "isFirstRun",
        "bg_loc_asked",
        "hide_light_color_warning",
        "total_lifetime_uploads",
        "last_notified_db_version",
        "last_notified_app_release",
        "app_update_baseline_initialized",
        "geotower_db_invalid_reason"
    )

    private val keySections = mapOf(
        "theme_mode" to "Apparence",
        "is_oled_mode" to "Apparence",
        "is_blur_enabled" to "Apparence",
        AppConfig.PREF_UI_SCALE_PERCENT to "Apparence",
        AppConfig.PREF_COLOR_PALETTE to "Apparence",
        AppConfig.PREF_UI_MODE to "Apparence",
        AppLogoDrawingResources.PREF_KEY to "Apparence",
        "map_provider" to "Cartographie",
        "ign_style" to "Cartographie",
        AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS to "Cartographie",
        AppConfig.PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS to "Cartographie",
        "default_operator" to "Préférences",
        "app_language" to "Préférences",
        "distance_unit" to "Préférences",
        "speed_unit" to "Préférences",
        "nav_mode" to "Préférences",
        "display_style" to "Préférences",
        "enable_update_notifications" to "Préférences",
        "enable_live_notifications" to "Préférences",
        "widget_sync_freq" to "Préférences",
        "live_tracking_location_update_interval_seconds" to "Préférences",
        "startup_page" to "Pages",
        "pages_order" to "Pages",
        "external_links_order" to "Liens externes",
        "page_site_external_links_order" to "Liens externes",
        "link_cartoradio" to "Liens externes",
        "link_cellularfr" to "Liens externes",
        "link_signalquest" to "Liens externes",
        "link_cellmapper" to "Liens externes",
        "link_rncmobile" to "Liens externes",
        "link_enbanalytics" to "Liens externes",
        "show_anfr" to "Liens externes"
    )

    private val keyLabels = mapOf(
        "theme_mode" to "Thème",
        "is_oled_mode" to "Mode OLED",
        "is_blur_enabled" to "Flou",
        AppConfig.PREF_UI_SCALE_PERCENT to "Taille de l'interface",
        AppConfig.PREF_COLOR_PALETTE to "Palette de couleurs",
        AppConfig.PREF_UI_MODE to "Style d'interface",
        AppLogoDrawingResources.PREF_KEY to "Logo dans l'app",
        "map_provider" to "Fond de carte",
        "ign_style" to "Style IGN",
        AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS to "Points de couverture SignalQuest",
        AppConfig.PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS to "OpÃ©rateurs couverture SignalQuest",
        "default_operator" to "Opérateur par défaut",
        "app_language" to "Langue",
        "distance_unit" to "Unité de distance",
        "speed_unit" to "Unité de vitesse",
        "nav_mode" to "Navigation des paramètres",
        "display_style" to "Style d'affichage",
        "enable_update_notifications" to "Notifications de mise à jour",
        "enable_live_notifications" to "Notification live",
        "widget_sync_freq" to "Fréquence des widgets",
        "live_tracking_location_update_interval_seconds" to "Rafraîchissement live",
        "startup_page" to "Page de démarrage",
        "pages_order" to "Ordre des pages",
        "external_links_order" to "Ordre des liens externes",
        "page_site_external_links_order" to "Ordre des liens externes",
        "link_cartoradio" to "Cartoradio",
        "link_cellularfr" to "CellularFR",
        "link_signalquest" to "Signal Quest",
        "link_cellmapper" to "CellMapper",
        "link_rncmobile" to "RNC Mobile",
        "link_enbanalytics" to "eNB-Analytics",
        "show_anfr" to "data.gouv.fr"
    )

    fun install(context: Context) {
        val prefs = appPrefs(context)
        ensureProfiles(context)
        if (listener != null) return
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == null || suppressProfileSync || !isVisiblePreferenceKey(key)) return@OnSharedPreferenceChangeListener
            updateActiveProfileFromCurrentPrefs(sharedPrefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun ensureProfiles(context: Context): List<PreferenceProfile> {
        val prefs = appPrefs(context)
        val store = readProfiles(prefs)
        if (store.any { it.id == DEFAULT_PROFILE_ID }) {
            val now = System.currentTimeMillis()
            val normalizedStore = store.map { profile ->
                val normalizedName = normalizedProfileName(profile)
                if (profile.name != normalizedName) {
                    profile.copy(name = normalizedName, updatedAt = now)
                } else {
                    profile
                }
            }
            if (normalizedStore != store) {
                writeProfiles(prefs, normalizedStore)
            }
            ensureActiveProfileId(prefs, normalizedStore)
            return normalizedStore
        }

        val now = System.currentTimeMillis()
        val defaultProfile = PreferenceProfile(
            id = DEFAULT_PROFILE_ID,
            name = DEFAULT_PROFILE_NAME,
            colorArgb = 0xFF2563EB.toInt(),
            icon = "settings",
            createdAt = now,
            updatedAt = now,
            values = currentVisibleValues(prefs)
        )
        writeProfiles(prefs, listOf(defaultProfile))
        prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID).apply()
        return listOf(defaultProfile)
    }

    fun profiles(context: Context): List<PreferenceProfile> {
        return ensureProfiles(context)
    }

    fun activeProfileId(context: Context): String {
        val prefs = appPrefs(context)
        val profiles = ensureProfiles(context)
        return prefs.getString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID)
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: DEFAULT_PROFILE_ID
    }

    fun activeProfile(context: Context): PreferenceProfile {
        val profiles = ensureProfiles(context)
        val activeId = activeProfileId(context)
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first { it.id == DEFAULT_PROFILE_ID }
    }

    fun createProfileFromCurrentSettings(
        context: Context,
        name: String,
        colorArgb: Int,
        icon: String
    ): PreferenceProfile {
        val prefs = appPrefs(context)
        val now = System.currentTimeMillis()
        val existingProfiles = ensureProfiles(context)
        val id = uniqueId(existingProfiles)
        val profile = PreferenceProfile(
            id = id,
            name = uniqueName(name.ifBlank { "Profil" }, existingProfiles.map { it.name }),
            colorArgb = colorArgb,
            icon = icon,
            createdAt = now,
            updatedAt = now,
            values = currentVisibleValues(prefs)
        )
        writeProfiles(prefs, existingProfiles + profile)
        prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, profile.id).apply()
        return profile
    }

    fun deleteProfile(context: Context, profileId: String): Boolean {
        if (profileId == DEFAULT_PROFILE_ID) return false
        val prefs = appPrefs(context)
        val profiles = ensureProfiles(context)
        if (profiles.none { it.id == profileId }) return false

        profiles.firstOrNull { it.id == profileId }?.imagePath?.let { path ->
            runCatching { File(path).delete() }
        }
        clearProfileImages(context, profileId)
        val remaining = profiles.filterNot { it.id == profileId }
        writeProfiles(prefs, remaining)
        if (activeProfileId(context) == profileId) {
            applyProfile(context, DEFAULT_PROFILE_ID)
        }
        return true
    }

    fun renameProfile(context: Context, profileId: String, name: String) {
        val prefs = appPrefs(context)
        val profiles = ensureProfiles(context)
        val updated = profiles.map { profile ->
            if (profile.id == profileId && !profile.isDefault) {
                profile.copy(
                    name = uniqueName(name.ifBlank { "Profil" }, profiles.filterNot { it.id == profileId }.map { it.name }),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                profile
            }
        }
        writeProfiles(prefs, updated)
    }

    fun updateProfileImage(context: Context, profileId: String, imageUri: Uri): Boolean {
        if (profileId == DEFAULT_PROFILE_ID) return false
        val prefs = appPrefs(context)
        val profiles = ensureProfiles(context)
        if (profiles.none { it.id == profileId }) return false

        val imagePath = copyImageToProfileStorage(context, imageUri, profileId) ?: return false
        val now = System.currentTimeMillis()
        val updated = profiles.map { profile ->
            if (profile.id == profileId) {
                profile.copy(imagePath = imagePath, updatedAt = now)
            } else {
                profile
            }
        }
        writeProfiles(prefs, updated)
        return true
    }

    fun profileChanges(context: Context, profile: PreferenceProfile): List<PreferenceProfileChange> {
        return diffValues(currentVisibleValues(appPrefs(context)), profile.values)
    }

    fun applyProfile(context: Context, profileId: String): List<PreferenceProfileChange> {
        val prefs = appPrefs(context)
        val profile = ensureProfiles(context).firstOrNull { it.id == profileId } ?: return emptyList()
        val changes = profileChanges(context, profile)
        suppressProfileSync = true
        try {
            val editor = prefs.edit()
            val currentKeys = currentVisibleValues(prefs).keys
            currentKeys
                .filterNot { it in profile.values.keys }
                .forEach { editor.remove(it) }
            profile.values.forEach { (key, value) -> value.put(editor, key) }
            editor.putString(ACTIVE_PROFILE_ID_KEY, profile.id).apply()
        } finally {
            suppressProfileSync = false
        }
        refreshRuntimePreferences(context)
        return changes
    }

    fun exportProfilesJson(context: Context, profileIds: Set<String>): String {
        val selectedProfiles = ensureProfiles(context).filter { it.id in profileIds }
        val root = JSONObject()
            .put("schemaVersion", EXPORT_SCHEMA_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("appVersionName", appVersionName(context))
            .put("profiles", JSONArray().also { array ->
                selectedProfiles.forEach { array.put(profileToJson(it, includeImageData = true)) }
            })
        return root.toString(2)
    }

    fun parseImport(context: Context, text: String): PreferenceProfileImportPreview {
        val root = JSONObject(text)
        val profilesArray = root.optJSONArray("profiles") ?: JSONArray().put(root.getJSONObject("profile"))
        val importedProfiles = buildList {
            for (index in 0 until profilesArray.length()) {
                add(profileFromJson(profilesArray.getJSONObject(index)))
            }
        }
        val existing = ensureProfiles(context)
        val conflicts = importedProfiles.mapNotNull { imported ->
            existing.firstOrNull { it.name.equals(imported.name, ignoreCase = true) }
                ?.let { existingProfile -> PreferenceProfileImportConflict(imported, existingProfile) }
        }
        return PreferenceProfileImportPreview(importedProfiles, conflicts)
    }

    fun importProfiles(
        context: Context,
        preview: PreferenceProfileImportPreview,
        resolution: PreferenceProfileImportResolution
    ): PreferenceProfileImportResult {
        val prefs = appPrefs(context)
        val activeId = activeProfileId(context)
        val now = System.currentTimeMillis()
        val importedByName = preview.profiles.associateBy { it.name.lowercase() }
        var profiles = ensureProfiles(context).toMutableList()
        var added = 0
        var replaced = 0
        var activeChanged = false

        preview.profiles.forEach { imported ->
            val conflict = profiles.firstOrNull { it.name.equals(imported.name, ignoreCase = true) }
            if (conflict == null) {
                val importedId = uniqueId(profiles)
                profiles += imported.forStorage(context, importedId).copy(
                    createdAt = now,
                    updatedAt = now
                )
                added++
            } else if (resolution == PreferenceProfileImportResolution.ReplaceExisting) {
                profiles = profiles.map { local ->
                    if (local.id == conflict.id) {
                        if (local.id == activeId) activeChanged = true
                        replaced++
                        imported.forStorage(context, local.id).copy(
                            name = local.name,
                            createdAt = local.createdAt,
                            updatedAt = now
                        )
                    } else {
                        local
                    }
                }.toMutableList()
            } else {
                val importedId = uniqueId(profiles)
                profiles += imported.forStorage(context, importedId).copy(
                    name = uniqueName(imported.name, profiles.map { it.name } + importedByName.keys),
                    createdAt = now,
                    updatedAt = now
                )
                added++
            }
        }

        writeProfiles(prefs, profiles)
        if (activeChanged) {
            applyProfile(context, activeId)
        }
        return PreferenceProfileImportResult(added, replaced, activeChanged)
    }

    fun createShareUri(context: Context, json: String, fileName: String): Uri {
        val dir = File(context.cacheDir, "profile_exports").apply { mkdirs() }
        val file = File(dir, fileName.sanitizeFileName())
        file.writeText(json, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareExport(context: Context, json: String, fileName: String) {
        val uri = createShareUri(context, json, fileName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = EXPORT_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun suggestedExportFileName(profiles: List<PreferenceProfile>): String {
        val baseName = exportFileStem(profiles)
        return "${baseName.sanitizeFileName()}.json"
    }

    fun refreshRuntimePreferences(context: Context) {
        val prefs = appPrefs(context)
        AppConfig.appLanguage.value = prefs.getString("app_language", AppLocale.LANGUAGE_SYSTEM)
            ?: AppLocale.LANGUAGE_SYSTEM
        AppLocale.applyApplicationLocale(context, AppConfig.appLanguage.value)
        AppConfig.themeMode.intValue = prefs.getInt("theme_mode", 0)
        AppConfig.isOledMode.value = prefs.getBoolean("is_oled_mode", true)
        AppConfig.isBlurEnabled.value = prefs.getBoolean("is_blur_enabled", true)
        AppConfig.uiMode.value = AppUiMode.fromStorageKey(
            prefs.getString(AppConfig.PREF_UI_MODE, AppUiMode.Auto.storageKey)
        )
        AppConfig.colorPalette.value = prefs.getString(AppConfig.PREF_COLOR_PALETTE, AppConfig.DEFAULT_COLOR_PALETTE)
            ?: AppConfig.DEFAULT_COLOR_PALETTE
        AppConfig.mapProvider.intValue = prefs.getInt("map_provider", 1)
        AppConfig.ignStyle.intValue = prefs.getInt("ign_style", 0)
        AppConfig.navMode.intValue = prefs.getInt("nav_mode", 0)
        AppConfig.defaultOperator.value = prefs.getString("default_operator", "Aucun") ?: "Aucun"
        AppConfig.uiScalePercent.intValue = AppConfig.readUiScalePercent(prefs)
        AppConfig.loadSavedFilters(prefs)

        UpdateCheckScheduler.onNotificationsPreferenceChanged(context, AppConfig.enableUpdateNotifications.value)
        if (AppConfig.enableLiveNotifications.value) {
            LiveTrackingController.startIfEligible(context)
        } else {
            LiveTrackingController.stop(context)
        }
        if (WidgetUpdateScheduler.hasAnyWidget(context)) {
            WidgetUpdateScheduler.schedulePeriodicUpdate(context, WidgetPrefs.syncFrequencyMinutes(prefs))
        }
    }

    private fun appPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
    }

    private fun ensureActiveProfileId(prefs: SharedPreferences, profiles: List<PreferenceProfile>) {
        val activeId = prefs.getString(ACTIVE_PROFILE_ID_KEY, null)
        if (activeId == null || profiles.none { it.id == activeId }) {
            prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID).apply()
        }
    }

    private fun updateActiveProfileFromCurrentPrefs(prefs: SharedPreferences) {
        val profiles = readProfiles(prefs)
        val activeId = prefs.getString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID
        val now = System.currentTimeMillis()
        val currentValues = currentVisibleValues(prefs)
        val activeProfile = profiles.firstOrNull { it.id == activeId }

        if (activeId == DEFAULT_PROFILE_ID && activeProfile != null && activeProfile.values != currentValues) {
            val customProfile = PreferenceProfile(
                id = uniqueId(profiles),
                name = uniquePersonalizedName(profiles.map { it.name }),
                colorArgb = activeProfile.colorArgb,
                icon = activeProfile.icon,
                createdAt = now,
                updatedAt = now,
                values = currentValues
            )
            writeProfiles(prefs, profiles + customProfile)
            prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, customProfile.id).apply()
            return
        }

        val updated = profiles.map { profile ->
            if (profile.id == activeId) {
                profile.copy(values = currentValues, updatedAt = now)
            } else {
                profile
            }
        }
        writeProfiles(prefs, updated)
    }

    private fun currentVisibleValues(prefs: SharedPreferences): Map<String, PreferenceProfileValue> {
        return prefs.all
            .asSequence()
            .filter { (key, _) -> isVisiblePreferenceKey(key) }
            .mapNotNull { (key, value) -> preferenceValueFromAny(value)?.let { key to it } }
            .sortedBy { it.first }
            .toMap()
    }

    private fun isVisiblePreferenceKey(key: String): Boolean {
        if (key in excludedKeys) return false
        if (excludedPrefixes.any { key.startsWith(it) }) return false
        return key in explicitVisibleKeys || visiblePrefixes.any { key.startsWith(it) }
    }

    private fun preferenceValueFromAny(value: Any?): PreferenceProfileValue? {
        return when (value) {
            is Boolean -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, value)
            is Int -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, value)
            is Long -> PreferenceProfileValue(PreferenceProfileValue.TYPE_LONG, value)
            is Float -> PreferenceProfileValue(PreferenceProfileValue.TYPE_FLOAT, value)
            is String -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, value)
            is Set<*> -> PreferenceProfileValue(
                PreferenceProfileValue.TYPE_STRING_SET,
                value.mapNotNull { it as? String }.toSet()
            )
            else -> null
        }
    }

    private fun diffValues(
        oldValues: Map<String, PreferenceProfileValue>,
        newValues: Map<String, PreferenceProfileValue>
    ): List<PreferenceProfileChange> {
        return (oldValues.keys + newValues.keys)
            .distinct()
            .filter { key -> oldValues[key] != newValues[key] }
            .map { key ->
                PreferenceProfileChange(
                    key = key,
                    section = sectionForKey(key),
                    label = labelForKey(key),
                    oldValue = oldValues[key].displayValue(),
                    newValue = newValues[key].displayValue()
                )
            }
            .sortedWith(compareBy<PreferenceProfileChange> { it.section }.thenBy { it.label })
    }

    private fun PreferenceProfileValue?.displayValue(): String {
        if (this == null) return "Par défaut"
        return when (type) {
            PreferenceProfileValue.TYPE_BOOLEAN -> if (value as Boolean) "Activé" else "Désactivé"
            PreferenceProfileValue.TYPE_STRING_SET -> (value as Set<*>).joinToString(", ")
            else -> value.toString()
        }
    }

    private fun sectionForKey(key: String): String {
        keySections[key]?.let { return it }
        return when {
            key.startsWith("share_") -> "Partage"
            key.startsWith("page_") -> "Pages"
            key.startsWith("site_") -> "Détails site"
            key.startsWith("community_") -> "Données communautaires"
            key.startsWith("throughput_") -> "Débit"
            key.startsWith("show_") || key.startsWith("f2g_") || key.startsWith("f3g_") ||
                key.startsWith("f4g_") || key.startsWith("f5g_") -> "Carte"
            else -> "Préférences"
        }
    }

    private fun labelForKey(key: String): String {
        keyLabels[key]?.let { return it }
        return key
            .removePrefix("page_")
            .removePrefix("share_")
            .removePrefix("site_")
            .removePrefix("community_")
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }

    private fun readProfiles(prefs: SharedPreferences): List<PreferenceProfile> {
        val raw = prefs.getString(STORE_KEY, null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("profiles") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    add(profileFromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeProfiles(prefs: SharedPreferences, profiles: List<PreferenceProfile>) {
        val root = JSONObject()
            .put("schemaVersion", STORE_SCHEMA_VERSION)
            .put("profiles", JSONArray().also { array ->
                profiles.forEach { array.put(profileToJson(it)) }
            })
        suppressProfileSync = true
        try {
            prefs.edit().putString(STORE_KEY, root.toString()).apply()
        } finally {
            suppressProfileSync = false
        }
    }

    private fun profileToJson(profile: PreferenceProfile, includeImageData: Boolean = false): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("colorArgb", profile.colorArgb)
            .put("icon", profile.icon)
            .put("createdAt", profile.createdAt)
            .put("updatedAt", profile.updatedAt)
            .put("imagePath", profile.imagePath ?: JSONObject.NULL)
            .put("values", JSONObject().also { valuesJson ->
                profile.values.toSortedMap().forEach { (key, value) ->
                    valuesJson.put(key, valueToJson(value))
                }
            })
            .also { profileJson ->
                if (includeImageData) {
                    profile.encodedImageForExport()?.let { (base64, mimeType) ->
                        profileJson.put("imageBase64", base64)
                        profileJson.put("imageMimeType", mimeType)
                    }
                }
            }
    }

    private fun profileFromJson(json: JSONObject): PreferenceProfile {
        val valuesJson = json.optJSONObject("values") ?: JSONObject()
        val values = buildMap {
            var legacyMenuSize: String? = null
            valuesJson.keys().forEach { key ->
                val valueJson = valuesJson.optJSONObject(key) ?: return@forEach
                if (key == AppConfig.PREF_LEGACY_MENU_SIZE) {
                    // Ancien reglage (petit/normal/large) : converti plus bas en ui_scale_percent.
                    legacyMenuSize = valueFromJson(valueJson)?.value as? String
                    return@forEach
                }
                if (isVisiblePreferenceKey(key)) {
                    valueFromJson(valueJson)?.let { put(key, it) }
                }
            }
            // Migration des profils anterieurs : menuSize -> ui_scale_percent (base 100 = ancien normal).
            if (!containsKey(AppConfig.PREF_UI_SCALE_PERCENT) && legacyMenuSize != null) {
                put(
                    AppConfig.PREF_UI_SCALE_PERCENT,
                    PreferenceProfileValue(
                        PreferenceProfileValue.TYPE_INT,
                        AppConfig.menuSizeLegacyToPercent(legacyMenuSize)
                    )
                )
            }
        }
        val now = System.currentTimeMillis()
        val id = json.optString("id")
            .takeIf { it.isNotBlank() && it != "null" }
            ?: UUID.randomUUID().toString()
        return PreferenceProfile(
            id = id,
            name = json.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "Profil importé",
            colorArgb = json.optInt("colorArgb", 0xFF2563EB.toInt()),
            icon = json.optString("icon").takeIf { it.isNotBlank() } ?: "settings",
            createdAt = json.optLong("createdAt", now),
            updatedAt = json.optLong("updatedAt", now),
            values = values,
            imagePath = json.optString("imagePath").takeIf { it.isNotBlank() && it != "null" },
            pendingImageBase64 = json.optString("imageBase64").takeIf { it.isNotBlank() && it != "null" },
            pendingImageMimeType = json.optString("imageMimeType").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun valueToJson(value: PreferenceProfileValue): JSONObject {
        return JSONObject()
            .put("type", value.type)
            .put(
                "value",
                if (value.type == PreferenceProfileValue.TYPE_STRING_SET) {
                    JSONArray((value.value as Set<String>).sorted())
                } else {
                    value.value
                }
            )
    }

    private fun valueFromJson(json: JSONObject): PreferenceProfileValue? {
        val type = json.optString("type")
        return when (type) {
            PreferenceProfileValue.TYPE_BOOLEAN -> PreferenceProfileValue(type, json.optBoolean("value"))
            PreferenceProfileValue.TYPE_INT -> PreferenceProfileValue(type, json.optInt("value"))
            PreferenceProfileValue.TYPE_LONG -> PreferenceProfileValue(type, json.optLong("value"))
            PreferenceProfileValue.TYPE_FLOAT -> PreferenceProfileValue(type, json.optDouble("value").toFloat())
            PreferenceProfileValue.TYPE_STRING -> PreferenceProfileValue(type, json.optString("value"))
            PreferenceProfileValue.TYPE_STRING_SET -> {
                val array = json.optJSONArray("value") ?: JSONArray()
                PreferenceProfileValue(
                    type,
                    buildSet {
                        for (index in 0 until array.length()) {
                            add(array.optString(index))
                        }
                    }
                )
            }
            else -> null
        }
    }

    private fun uniqueId(profiles: List<PreferenceProfile>): String {
        val existingIds = profiles.map { it.id }.toSet()
        while (true) {
            val id = UUID.randomUUID().toString()
            if (id !in existingIds) return id
        }
    }

    private fun uniqueName(baseName: String, existingNames: List<String>): String {
        val trimmed = baseName.trim().ifBlank { "Profil" }
        if (existingNames.none { it.equals(trimmed, ignoreCase = true) }) return trimmed
        var index = 2
        while (true) {
            val candidate = "$trimmed $index"
            if (existingNames.none { it.equals(candidate, ignoreCase = true) }) return candidate
            index++
        }
    }

    private fun uniquePersonalizedName(existingNames: List<String>): String {
        var index = 1
        while (true) {
            val candidate = "Personnalisé $index"
            val compactCandidate = "Personnalisé$index"
            if (existingNames.none { it.equals(candidate, ignoreCase = true) || it.equals(compactCandidate, ignoreCase = true) }) {
                return candidate
            }
            index++
        }
    }

    private fun normalizedProfileName(profile: PreferenceProfile): String {
        if (profile.id == DEFAULT_PROFILE_ID) return DEFAULT_PROFILE_NAME
        val compactPersonalizedName = compactPersonalizedNameRegex.matchEntire(profile.name) ?: return profile.name
        return "Personnalisé ${compactPersonalizedName.groupValues[1]}"
    }

    private fun exportFileStem(profiles: List<PreferenceProfile>): String {
        if (profiles.isEmpty()) return "${PROFILES_EXPORT_FILE_PREFIX}selection"
        profiles.singleOrNull()?.let { profile ->
            return PROFILE_EXPORT_FILE_PREFIX + profile.name
        }

        val joinedNames = profiles
            .map { it.name }
            .joinToString("_")
            .take(90)
            .trim('_', '-', '.', ' ')
        return PROFILES_EXPORT_FILE_PREFIX + joinedNames.ifBlank { "${profiles.size}_profils" }
    }

    private fun PreferenceProfile.forStorage(context: Context, profileId: String): PreferenceProfile {
        val storedImagePath = materializePendingImage(context, profileId)
            ?: imagePath?.takeIf { File(it).exists() }
        return copy(
            id = profileId,
            imagePath = storedImagePath,
            pendingImageBase64 = null,
            pendingImageMimeType = null
        )
    }

    private fun PreferenceProfile.materializePendingImage(context: Context, profileId: String): String? {
        val base64 = pendingImageBase64?.takeIf { it.isNotBlank() } ?: return null
        val imageBytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return null
        val file = profileImageFile(context, profileId, extensionForMimeType(pendingImageMimeType))
        return runCatching {
            clearProfileImages(context, profileId)
            file.parentFile?.mkdirs()
            file.writeBytes(imageBytes)
            file.absolutePath
        }.getOrNull()
    }

    private fun PreferenceProfile.encodedImageForExport(): Pair<String, String>? {
        val imageFile = imagePath?.let(::File)?.takeIf { it.exists() && it.isFile } ?: return null
        return runCatching {
            Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP) to mimeTypeForExtension(imageFile.extension)
        }.getOrNull()
    }

    private fun copyImageToProfileStorage(context: Context, imageUri: Uri, profileId: String): String? {
        val mimeType = context.contentResolver.getType(imageUri)
        val file = profileImageFile(context, profileId, extensionForMimeType(mimeType))
        return runCatching {
            clearProfileImages(context, profileId)
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    private fun clearProfileImages(context: Context, profileId: String) {
        val fileName = profileId.sanitizeFileName()
        profileImageDir(context).listFiles()
            ?.filter { it.isFile && it.nameWithoutExtension == fileName }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun profileImageDir(context: Context): File {
        return File(context.filesDir, "preference_profile_images")
    }

    private fun profileImageFile(context: Context, profileId: String, extension: String): File {
        return File(profileImageDir(context), "${profileId.sanitizeFileName()}.$extension")
    }

    private fun extensionForMimeType(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "jpg"
        }
    }

    private fun mimeTypeForExtension(extension: String): String {
        return when (extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> "image/jpeg"
        }
    }

    private fun String.sanitizeFileName(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-', '.')
            .ifBlank { "geotower_profil_export" }
    }

    private fun appVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }
}
