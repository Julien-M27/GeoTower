package fr.geotower.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import fr.geotower.R
import fr.geotower.data.share.ShareHistoryStore
import fr.geotower.data.workers.UpdateCheckScheduler
import fr.geotower.services.LiveTrackingController
import fr.geotower.widget.WidgetUpdateScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
    ReplaceExisting,

    /**
     * Un nom déjà pris ici est laissé tel quel, et le profil importé est simplement ignoré.
     *
     * C'est le mode de la sauvegarde complète ([fr.geotower.data.backup.AppBackupManager]) :
     * [RenameImported] y créerait un « Profil 2 », puis un « Profil 3 » à chaque réapplication de
     * la même sauvegarde, et [ReplaceExisting] écraserait des réglages que l'utilisateur n'a pas
     * demandé à remplacer.
     */
    SkipExisting
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
    val personalizedNameRegex = Regex("^Personnalisé(\\s*\\d+)?$", RegexOption.IGNORE_CASE)

    private val explicitVisibleKeys = setOf(
        "theme_mode",
        "is_oled_mode",
        "is_blur_enabled",
        AppConfig.PREF_UI_SCALE_PERCENT,
        "app_language",
        "map_provider",
        "ign_style",
        "nav_mode",
        AppConfig.PREF_SETTINGS_SECTIONS_MODE,
        "display_style",
        "distance_unit",
        "speed_unit",
        "default_operator",
        AppNotifications.PREF_ENABLED,
        "enable_update_notifications",
        "enable_live_notifications",
        "nearby_order",
        "compass_order",
        "home_logo_choice",
        AppConfig.PREF_HOME_HELP_POSITION,
        "pages_order",
        AppConfig.PREF_HOME_LONG_PRESS_REORDER,
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
        // Ne commence pas par « show_ » : sans cette entrée explicite, les profils l'ignoreraient.
        AppConfig.PREF_SMOOTH_MAP_LOCATION,
        AppConfig.PREF_MAP_LOCATION_ZOOM,
        AppConfig.PREF_MAP_CLUSTER_STRENGTH,
        AppConfig.PREF_MAP_ROTATION_ENABLED,
        AppConfig.PREF_MAP_FOLLOW_ORIENTATION,
        AppConfig.PREF_SHOW_AZIMUTH_LINES,
        AppConfig.PREF_SHOW_AZIMUTH_CONES,
        AppConfig.PREF_KEEP_AZIMUTHS_WHEN_ZOOMED_OUT,
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
        AppConfig.PREF_SHOW_PROJECT_SITES,
        AppConfig.PREF_MOBILE_TECHNOLOGY_ONLY,
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
        // Rangé avec « Point GPS », qui tombe dans « Carte » par son préfixe show_.
        AppConfig.PREF_SMOOTH_MAP_LOCATION to "Carte",
        AppConfig.PREF_MAP_LOCATION_ZOOM to "Carte",
        AppConfig.PREF_MAP_CLUSTER_STRENGTH to "Carte",
        AppConfig.PREF_MAP_ROTATION_ENABLED to "Carte",
        AppConfig.PREF_MAP_FOLLOW_ORIENTATION to "Carte",
        AppConfig.PREF_KEEP_AZIMUTHS_WHEN_ZOOMED_OUT to "Azimuts au dézoom",
        "default_operator" to "Général",
        "app_language" to "Général",
        "distance_unit" to "Général",
        "speed_unit" to "Général",
        // Mise en page des réglages : même section que le reste de l'apparence.
        "nav_mode" to "Apparence",
        AppConfig.PREF_SETTINGS_SECTIONS_MODE to "Apparence",
        "display_style" to "Apparence",
        // Ce qui tourne hors de l'app, comme dans les réglages.
        AppNotifications.PREF_ENABLED to "Suivi et arrière-plan",
        "enable_update_notifications" to "Suivi et arrière-plan",
        "enable_live_notifications" to "Suivi et arrière-plan",
        "widget_sync_freq" to "Suivi et arrière-plan",
        "live_tracking_location_update_interval_seconds" to "Suivi et arrière-plan",
        AppConfig.PREF_LOW_POWER_LEVEL to "Système, batterie et permissions",
        AppConfig.PREF_LOW_POWER_FOLLOW_SYSTEM to "Système, batterie et permissions",
        "startup_page" to "Pages",
        "pages_order" to "Pages",
        AppConfig.PREF_HOME_LONG_PRESS_REORDER to "Pages",
        AppConfig.PREF_HOME_HELP_POSITION to "Pages",
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
        AppConfig.PREF_SMOOTH_MAP_LOCATION to "Déplacement fluide du repère",
        AppConfig.PREF_MAP_LOCATION_ZOOM to "Zoom du bouton de localisation",
        AppConfig.PREF_MAP_CLUSTER_STRENGTH to "Regroupement des antennes",
        AppConfig.PREF_MAP_ROTATION_ENABLED to "Rotation de la carte à deux doigts",
        AppConfig.PREF_MAP_FOLLOW_ORIENTATION to "Carte orientée selon la boussole",
        AppConfig.PREF_KEEP_AZIMUTHS_WHEN_ZOOMED_OUT to "Azimuts visibles en dézoomant",
        AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS to "Points de couverture SignalQuest",
        AppConfig.PREF_SIGNALQUEST_COVERAGE_OPERATOR_KEYS to "OpÃ©rateurs couverture SignalQuest",
        "default_operator" to "Opérateur par défaut",
        "app_language" to "Langue",
        "distance_unit" to "Unité de distance",
        "speed_unit" to "Unité de vitesse",
        "nav_mode" to "Navigation des paramètres",
        AppConfig.PREF_SETTINGS_SECTIONS_MODE to "Navigation des paramètres (téléphone)",
        "display_style" to "Style d'affichage",
        AppNotifications.PREF_ENABLED to "Notifications de l'app",
        "enable_update_notifications" to "Notifications de mise à jour",
        "enable_live_notifications" to "Suivi live",
        "widget_sync_freq" to "Fréquence des widgets",
        "live_tracking_location_update_interval_seconds" to "Rafraîchissement live",
        AppConfig.PREF_LOW_POWER_LEVEL to "Mode économie d'énergie",
        AppConfig.PREF_LOW_POWER_FOLLOW_SYSTEM to "Suivre l'économie d'énergie Android",
        "startup_page" to "Page de démarrage",
        "pages_order" to "Ordre des pages",
        AppConfig.PREF_HOME_LONG_PRESS_REORDER to "Déplacer par appui long (accueil)",
        AppConfig.PREF_HOME_HELP_POSITION to "Position du bouton Aides",
        "external_links_order" to "Ordre des liens externes",
        "page_site_external_links_order" to "Ordre des liens externes",
        "page_site_status_voice" to "Ligne Voix du statut",
        "page_site_status_data" to "Ligne Data du statut",
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
        val baseDefaults = factoryDefaultValues()
        if (store.any { it.id == DEFAULT_PROFILE_ID }) {
            val now = System.currentTimeMillis()
            val (spuriousProfiles, validProfiles) = store.partition { profile ->
                !profile.isDefault &&
                    profile.name.matches(personalizedNameRegex) &&
                    diffValues(baseDefaults, profile.values).isEmpty()
            }
            if (spuriousProfiles.isNotEmpty()) {
                spuriousProfiles.forEach { profile ->
                    profile.imagePath?.let { runCatching { File(it).delete() } }
                    clearProfileImages(context, profile.id)
                }
            }
            val normalizedStore = validProfiles.map { profile ->
                if (profile.id == DEFAULT_PROFILE_ID) {
                    val normalizedName = DEFAULT_PROFILE_NAME
                    if (profile.name != normalizedName || profile.values != baseDefaults) {
                        profile.copy(name = normalizedName, values = baseDefaults, updatedAt = now)
                    } else {
                        profile
                    }
                } else {
                    val normalizedName = normalizedProfileName(profile)
                    if (profile.name != normalizedName) {
                        profile.copy(name = normalizedName, updatedAt = now)
                    } else {
                        profile
                    }
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
            values = baseDefaults
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
        return diffValues(currentVisibleValues(appPrefs(context)), profile.values, context)
    }

    fun defaultProfile(context: Context): PreferenceProfile? {
        val default = ensureProfiles(context).firstOrNull { it.isDefault } ?: return null
        return default.copy(values = factoryDefaultValues())
    }

    fun profileDifferencesFromDefault(context: Context, profile: PreferenceProfile): List<PreferenceProfileChange> {
        val default = defaultProfile(context) ?: return emptyList()
        if (profile.isDefault) return emptyList()
        return diffValues(default.values, profile.values, context)
    }

    fun profileDifferencesCount(context: Context, profile: PreferenceProfile): Int {
        if (profile.isDefault) return 0
        return profileDifferencesFromDefault(context, profile).size
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
            } else if (resolution == PreferenceProfileImportResolution.SkipExisting) {
                return@forEach
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

    fun compressGzip(data: ByteArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { it.write(data) }
        return byteStream.toByteArray()
    }

    fun decompressGzip(compressed: ByteArray): String {
        val inputStream = GZIPInputStream(ByteArrayInputStream(compressed))
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    internal fun encodeBase64Url(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 2 < bytes.size) {
            val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
            out.append(BASE64_URL_ALPHABET[(chunk ushr 18) and 0x3F])
            out.append(BASE64_URL_ALPHABET[(chunk ushr 12) and 0x3F])
            out.append(BASE64_URL_ALPHABET[(chunk ushr 6) and 0x3F])
            out.append(BASE64_URL_ALPHABET[chunk and 0x3F])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val chunk = (bytes[index].toInt() and 0xFF) shl 16
                out.append(BASE64_URL_ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(chunk ushr 12) and 0x3F])
            }
            2 -> {
                val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8)
                out.append(BASE64_URL_ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(chunk ushr 12) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(chunk ushr 6) and 0x3F])
            }
        }
        return out.toString()
    }

    internal fun decodeBase64Url(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in text) {
            if (ch == '=') break
            val v = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in 'a'..'z' -> ch - 'a' + 26
                in '0'..'9' -> ch - '0' + 52
                '-', '+' -> 62
                '_', '/' -> 63
                else -> continue
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun generateProfileQrData(profile: PreferenceProfile, baseProfile: PreferenceProfile?): String {
        val exportedValues = if (baseProfile != null && !profile.isDefault) {
            profile.values.filter { (k, v) -> effectiveValue(baseProfile.values, k) != v }
        } else {
            profile.values
        }

        val json = JSONObject().apply {
            put("v", 1)
            put("diff", true)
            put("name", profile.name)
            put("color", profile.colorArgb)
            put("icon", profile.icon)
            put("values", JSONObject().also { vJson ->
                exportedValues.toSortedMap().forEach { (k, v) ->
                    vJson.put(k, valueToJson(v))
                }
            })
        }
        val compressed = compressGzip(json.toString().toByteArray(Charsets.UTF_8))
        return encodeBase64Url(compressed)
    }

    fun generateProfileQrDeepLink(context: Context, profile: PreferenceProfile): String {
        val default = defaultProfile(context)
        val data = generateProfileQrData(profile, default)
        return "geotower://profile?data=$data"
    }

    fun decodeProfileFromQrData(
        rawData: String,
        baseValues: Map<String, PreferenceProfileValue> = emptyMap(),
        existingNames: List<String> = emptyList()
    ): PreferenceProfile? {
        val jsonString = runCatching {
            val trimmed = rawData.trim()
            if (trimmed.startsWith("{")) {
                trimmed
            } else {
                val bytes = decodeBase64Url(trimmed)
                decompressGzip(bytes)
            }
        }.getOrNull() ?: return null

        return runCatching {
            val root = JSONObject(jsonString)
            val name = root.optString("name", "Profil importé")
            val color = root.optInt("color", 0xFF2563EB.toInt())
            val icon = root.optString("icon", "settings")
            val isDiff = root.optBoolean("diff", true)
            val valuesJson = root.optJSONObject("values") ?: JSONObject()
            val importedValues = buildMap {
                valuesJson.keys().forEach { key ->
                    val vJson = valuesJson.optJSONObject(key) ?: return@forEach
                    if (isVisiblePreferenceKey(key)) {
                        valueFromJson(vJson)?.let { put(key, it) }
                    }
                }
            }
            val finalValues = if (isDiff && baseValues.isNotEmpty()) {
                baseValues.toMutableMap().apply {
                    putAll(importedValues)
                }
            } else {
                importedValues
            }
            val now = System.currentTimeMillis()
            PreferenceProfile(
                id = UUID.randomUUID().toString(),
                name = uniqueName(name, existingNames),
                colorArgb = color,
                icon = icon,
                createdAt = now,
                updatedAt = now,
                values = finalValues
            )
        }.getOrNull()
    }

    fun decodeProfileFromQrData(context: Context, rawData: String): PreferenceProfile? {
        val default = defaultProfile(context)
        val existing = ensureProfiles(context)
        return decodeProfileFromQrData(rawData, default?.values ?: emptyMap(), existing.map { it.name })
    }

    fun parseProfileFromQrText(context: Context, qrText: String): PreferenceProfile? {
        val trimmed = qrText.trim()
        if (trimmed.startsWith("geotower://profile")) {
            val uri = Uri.parse(trimmed)
            val data = uri.getQueryParameter("data") ?: return null
            return decodeProfileFromQrData(context, data)
        }
        return decodeProfileFromQrData(context, trimmed)
    }

    fun generateProfileQrBitmap(deepLink: String, size: Int = 512): Bitmap? {
        return try {
            val hints = java.util.EnumMap<com.google.zxing.EncodeHintType, Any>(com.google.zxing.EncodeHintType::class.java).apply {
                put(com.google.zxing.EncodeHintType.MARGIN, 1)
                put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
            }
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                deepLink,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun decodeQrCodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            com.google.zxing.DecodeHintType.TRY_HARDER to true,
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)
        )
        val reader = com.google.zxing.qrcode.QRCodeReader()

        // 1. HybridBinarizer (standard)
        try {
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            return reader.decode(binaryBitmap, hints).text
        } catch (_: Exception) {}

        // 2. GlobalHistogramBinarizer (robuste aux ombres et contrastes d'appareil photo)
        try {
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
            return reader.decode(binaryBitmap, hints).text
        } catch (_: Exception) {}

        // 3. Luminance inversée (écran sombre / mode nuit)
        try {
            val inverted = source.invert()
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(inverted))
            return reader.decode(binaryBitmap, hints).text
        } catch (_: Exception) {}

        return null
    }

    fun decodeProfileFromImageUri(context: Context, uri: Uri): PreferenceProfile? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@runCatching null

            val qrText = decodeQrCodeFromBitmap(bitmap) ?: return@runCatching null
            parseProfileFromQrText(context, qrText)
        }.getOrNull()
    }

    fun createQrCameraCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "profile_shares").apply { mkdirs() }
        val file = File(dir, "qr_camera_capture.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun createProfileQrCardBitmap(
        context: Context,
        profile: PreferenceProfile,
        qrBitmap: Bitmap
    ): Bitmap {
        val width = 720
        val height = 960
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(android.graphics.Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.parseColor("#E2E8F0")
            strokeWidth = 4f
        }
        val cardRect = RectF(20f, 20f, width - 20f, height - 20f)
        canvas.drawRoundRect(cardRect, 32f, 32f, borderPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1E293B")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GeoTower", width / 2f, 80f, brandPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Profil de préférences", width / 2f, 115f, subtitlePaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = profile.colorArgb
            style = Paint.Style.FILL
        }
        canvas.drawCircle(width / 2f, 175f, 36f, badgePaint)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0F172A")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val displayName = if (profile.name.length > 28) profile.name.take(26) + "…" else profile.name
        canvas.drawText(displayName, width / 2f, 255f, namePaint)

        val diffCount = profileDifferencesCount(context, profile)
        val countText = if (profile.isDefault) {
            "Version de base"
        } else if (diffCount == 0) {
            "Identique à la version de base"
        } else if (diffCount == 1) {
            "1 préférence modifiée"
        } else {
            "$diffCount préférences modifiées"
        }
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(countText, width / 2f, 290f, countPaint)

        val qrSize = 460
        val qrLeft = (width - qrSize) / 2f
        val qrTop = 330f
        val qrDestRect = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
        val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val qrBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#CBD5E1")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(RectF(qrLeft - 10f, qrTop - 10f, qrLeft + qrSize + 10f, qrTop + qrSize + 10f), 24f, 24f, qrBgPaint)
        canvas.drawRoundRect(RectF(qrLeft - 10f, qrTop - 10f, qrLeft + qrSize + 10f, qrTop + qrSize + 10f), 24f, 24f, qrBorderPaint)
        canvas.drawBitmap(qrBitmap, null, qrDestRect, null)

        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#334155")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Scannez avec l'appareil photo ou GeoTower", width / 2f, 850f, hintPaint)

        val subHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("pour importer ce profil", width / 2f, 885f, subHintPaint)

        return bitmap
    }

    fun createProfileQrShareUri(context: Context, profile: PreferenceProfile): Uri? {
        val deepLink = generateProfileQrDeepLink(context, profile)
        val qrBitmap = generateProfileQrBitmap(deepLink, 512) ?: return null
        val cardBitmap = createProfileQrCardBitmap(context, profile, qrBitmap)
        val dir = File(context.cacheDir, "profile_shares").apply { mkdirs() }
        val file = File(dir, "geotower_profil_${profile.name.sanitizeFileName()}_qr.png")
        file.outputStream().use { stream ->
            cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareProfileQr(context: Context, profile: PreferenceProfile) {
        val deepLink = generateProfileQrDeepLink(context, profile)
        val uri = createProfileQrShareUri(context, profile) ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Profil GeoTower : ${profile.name}")
            putExtra(Intent.EXTRA_TEXT, "Profil GeoTower : ${profile.name}\n$deepLink")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, profile.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        ShareHistoryStore.record(
            context = context,
            kind = ShareHistoryStore.KIND_SETTINGS_PROFILE,
            destination = ShareHistoryStore.DEST_SHARE,
            label = profile.name,
            itemCount = 1
        )
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
        val mapProvider = MapProviderRules.sanitize(prefs.getInt("map_provider", 1))
        AppConfig.mapProvider.intValue = mapProvider
        prefs.edit().putInt("map_provider", mapProvider).apply()
        AppConfig.ignStyle.intValue = prefs.getInt("ign_style", 0)
        AppConfig.navMode.intValue = prefs.getInt(AppConfig.PREF_NAV_MODE, AppConfig.DEFAULT_NAV_MODE)
        AppConfig.settingsSectionsMode.value = prefs.getBoolean(AppConfig.PREF_SETTINGS_SECTIONS_MODE, true)
        AppConfig.defaultOperator.value = prefs.getString("default_operator", "Aucun") ?: "Aucun"
        AppConfig.uiScalePercent.intValue = AppConfig.readUiScalePercent(prefs)
        AppConfig.loadSavedFilters(prefs)
        // Un profil écrase toutes les clés : les états miroités en mémoire doivent être relus.
        // (l'ordre de l'accueil et ses réglages repassent par loadSavedFilters, juste au-dessus)
        PageCustomizationPrefs.load(prefs)

        UpdateCheckScheduler.onNotificationsPreferenceChanged(context, AppConfig.enableUpdateNotifications.value)
        if (AppConfig.enableLiveTracking.value) {
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
        val baseDefaults = factoryDefaultValues()
        val diffFromBase = diffValues(baseDefaults, currentValues)

        if (activeId == DEFAULT_PROFILE_ID && activeProfile != null) {
            if (diffFromBase.isEmpty()) {
                val updated = profiles.map { profile ->
                    if (profile.id == activeId) {
                        profile.copy(values = currentValues, updatedAt = now)
                    } else {
                        profile
                    }
                }
                writeProfiles(prefs, updated)
                return
            }

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

        if (activeProfile != null && !activeProfile.isDefault && activeProfile.name.matches(personalizedNameRegex)) {
            if (diffFromBase.isEmpty()) {
                activeProfile.imagePath?.let { runCatching { File(it).delete() } }
                val remaining = profiles.filterNot { it.id == activeId }
                writeProfiles(prefs, remaining)
                prefs.edit().putString(ACTIVE_PROFILE_ID_KEY, DEFAULT_PROFILE_ID).apply()
                return
            }
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

    fun factoryDefaultValue(key: String): PreferenceProfileValue? {
        return when (key) {
            "theme_mode" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            "is_oled_mode" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "is_blur_enabled" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_UI_SCALE_PERCENT -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 100)
            AppConfig.PREF_COLOR_PALETTE -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, AppConfig.DEFAULT_COLOR_PALETTE)
            AppConfig.PREF_UI_MODE -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, AppUiMode.Auto.storageKey)
            AppLogoDrawingResources.PREF_KEY -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            "map_provider" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 1) // OpenStreetMap
            "ign_style" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            AppConfig.PREF_SMOOTH_MAP_LOCATION -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_MAP_LOCATION_ZOOM -> PreferenceProfileValue(PreferenceProfileValue.TYPE_FLOAT, 16f)
            AppConfig.PREF_MAP_CLUSTER_STRENGTH -> PreferenceProfileValue(PreferenceProfileValue.TYPE_FLOAT, 1f)
            AppConfig.PREF_MAP_ROTATION_ENABLED -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_MAP_FOLLOW_ORIENTATION -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
            AppConfig.PREF_KEEP_AZIMUTHS_WHEN_ZOOMED_OUT -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
            AppConfig.PREF_SHOW_SIGNALQUEST_COVERAGE_POINTS -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
            AppConfig.PREF_SHOW_MAP_LOCATION_MARKER -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_SHOW_AZIMUTH_LINES -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_SHOW_AZIMUTH_CONES -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_SHOW_RADIO_SITES -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "default_operator" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "Aucun")
            "app_language" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, AppLocale.LANGUAGE_SYSTEM)
            "distance_unit" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            "speed_unit" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            "nav_mode" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, AppConfig.DEFAULT_NAV_MODE)
            AppConfig.PREF_SETTINGS_SECTIONS_MODE -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "display_style" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            AppNotifications.PREF_ENABLED -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "enable_update_notifications" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "enable_live_notifications" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "widget_sync_freq" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, WidgetPrefs.DEFAULT_SYNC_MINUTES)
            "live_tracking_location_update_interval_seconds" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 5)
            AppConfig.PREF_LOW_POWER_LEVEL -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            AppConfig.PREF_LOW_POWER_FOLLOW_SYSTEM -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "startup_page" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_STRING, "home")
            AppConfig.PREF_HOME_LONG_PRESS_REORDER -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            AppConfig.PREF_HOME_HELP_POSITION -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            "page_site_status_voice" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "page_site_status_data" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "link_cartoradio" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "link_cellularfr" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "link_signalquest" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "link_cellmapper" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "link_rncmobile" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "link_enbanalytics" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "show_anfr" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "home_logo_choice" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_INT, 0)
            "filter_default_show_sites_in_service", "show_sites_in_service" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "filter_default_show_sites_out_of_service", "show_sites_out_of_service" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "filter_default_show_project_sites", "show_project_sites" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            "filter_default_hide_underground_sites", "hide_underground_sites" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
            "filter_default_show_only_zb_sites", "show_only_zb_sites" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
            "filter_default_show_techno_2g", "show_techno_2g",
            "filter_default_show_techno_3g", "show_techno_3g",
            "filter_default_show_techno_4g", "show_techno_4g",
            "filter_default_show_techno_5g", "show_techno_5g" -> PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
            else -> when {
                key.startsWith("site_show_") ->
                    PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
                key.startsWith("f2g_") || key.startsWith("f3g_") || key.startsWith("f4g_") || key.startsWith("f5g_") ->
                    PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, true)
                key.startsWith("show_radio_") ->
                    PreferenceProfileValue(PreferenceProfileValue.TYPE_BOOLEAN, false)
                else -> null
            }
        }
    }

    fun factoryDefaultValues(): Map<String, PreferenceProfileValue> {
        val map = mutableMapOf<String, PreferenceProfileValue>()
        explicitVisibleKeys.forEach { key ->
            factoryDefaultValue(key)?.let { map[key] = it }
        }
        return map
    }

    fun effectiveValue(values: Map<String, PreferenceProfileValue>, key: String): PreferenceProfileValue? {
        return values[key] ?: factoryDefaultValue(key)
    }

    fun diffValues(
        oldValues: Map<String, PreferenceProfileValue>,
        newValues: Map<String, PreferenceProfileValue>,
        context: Context? = null
    ): List<PreferenceProfileChange> {
        val allKeys = (oldValues.keys + newValues.keys).distinct().filter { isVisiblePreferenceKey(it) }
        return allKeys.mapNotNull { key ->
            val oldVal = effectiveValue(oldValues, key)
            val newVal = effectiveValue(newValues, key)
            if (oldVal == newVal) return@mapNotNull null

            val oldDisplay = oldVal.displayValue(key, context)
            val newDisplay = newVal.displayValue(key, context)
            if (oldDisplay == newDisplay) return@mapNotNull null

            PreferenceProfileChange(
                key = key,
                section = sectionForKey(key, context),
                label = labelForKey(key, context),
                oldValue = oldDisplay,
                newValue = newDisplay
            )
        }.sortedWith(compareBy<PreferenceProfileChange> { it.section }.thenBy { it.label })
    }

    internal fun PreferenceProfileValue?.displayValue(key: String = "", context: Context? = null): String {
        if (this == null) {
            val fallback = factoryDefaultValue(key)
            if (fallback != null) {
                return fallback.displayValue(key, context)
            }
            return context?.getString(R.string.preference_profiles_base_version) ?: "Par défaut"
        }
        return when (type) {
            PreferenceProfileValue.TYPE_BOOLEAN -> {
                val b = value as Boolean
                if (b) {
                    context?.getString(R.string.appstrings_diagnostic_value_enabled) ?: "Activé"
                } else {
                    context?.getString(R.string.appstrings_diagnostic_value_disabled) ?: "Désactivé"
                }
            }
            PreferenceProfileValue.TYPE_STRING_SET -> (value as Set<*>).joinToString(", ")
            PreferenceProfileValue.TYPE_INT -> formatIntDisplayValue(key, value as Int, context)
            PreferenceProfileValue.TYPE_STRING -> formatStringDisplayValue(key, value as String, context)
            else -> value.toString()
        }
    }

    private fun formatIntDisplayValue(key: String, value: Int, context: Context? = null): String {
        return when (key) {
            "theme_mode" -> when (value) {
                1 -> context?.getString(R.string.appearance_theme_light) ?: "Clair"
                2 -> context?.getString(R.string.appearance_theme_dark) ?: "Sombre"
                else -> context?.getString(R.string.appearance_theme_system) ?: "Système"
            }
            "map_provider" -> when (value) {
                1 -> context?.getString(R.string.mapping_provider_osm) ?: "OpenStreetMap"
                2 -> "Google Maps"
                3 -> context?.getString(R.string.mapping_provider_ign) ?: "IGN"
                4 -> "Mapbox"
                5 -> "Mapsforge (hors-ligne)"
                else -> value.toString()
            }
            "ign_style" -> when (value) {
                0 -> "Plan IGN"
                1 -> "Scan 25"
                2 -> "Satellite"
                3 -> "Cadastre"
                else -> value.toString()
            }
            "nav_mode" -> when (value) {
                1 -> "Panneau latéral (One UI)"
                else -> "Onglets classiques"
            }
            "distance_unit" -> when (value) {
                1 -> "Impérial (ft, mi)"
                else -> "Métrique (m, km)"
            }
            "speed_unit" -> when (value) {
                1 -> "mph"
                else -> "km/h"
            }
            "display_style" -> when (value) {
                1 -> "Compact"
                2 -> "Aéré"
                else -> "Standard"
            }
            AppConfig.PREF_UI_SCALE_PERCENT -> "$value %"
            "widget_sync_freq" -> "$value min"
            "live_tracking_location_update_interval_seconds" -> "$value s"
            AppConfig.PREF_MAP_LOCATION_ZOOM -> "Zoom $value"
            AppConfig.PREF_MAP_CLUSTER_STRENGTH -> "Niveau $value"
            AppConfig.PREF_LOW_POWER_LEVEL -> when (value) {
                1 -> "Économie modérée"
                2 -> "Économie stricte"
                else -> context?.getString(R.string.appstrings_diagnostic_value_disabled) ?: "Désactivé"
            }
            else -> value.toString()
        }
    }

    private fun formatStringDisplayValue(key: String, value: String, context: Context? = null): String {
        return when (key) {
            "app_language" -> when (value) {
                "fr" -> context?.getString(R.string.language_french_name) ?: "Français"
                "en" -> context?.getString(R.string.language_english_name) ?: "English"
                "de" -> context?.getString(R.string.language_german_name) ?: "Deutsch"
                "es" -> context?.getString(R.string.language_spanish_name) ?: "Español"
                "it" -> context?.getString(R.string.language_italian_name) ?: "Italiano"
                "pt" -> context?.getString(R.string.language_portuguese_name) ?: "Português"
                else -> context?.getString(R.string.language_system) ?: "Système"
            }
            "default_operator" -> if (value == "Aucun") {
                context?.getString(R.string.common_none) ?: "Aucun"
            } else {
                value
            }
            "startup_page" -> when (value) {
                "nearby" -> "À proximité"
                "map" -> "Carte"
                "compass" -> "Boussole"
                "stats" -> "Statistiques"
                else -> "Accueil"
            }
            AppConfig.PREF_UI_MODE -> when (value) {
                "one_ui" -> "One UI"
                "material" -> "Material 3"
                else -> "Automatique"
            }
            else -> value
        }
    }

    fun sectionForKey(key: String, context: Context? = null): String {
        val sectionRes = when (keySections[key]) {
            "Apparence" -> R.string.settings_section_appearance
            "Cartographie", "Carte", "Azimuts au dézoom" -> R.string.settings_section_mapping
            "Général" -> R.string.settings_section_preferences
            "Suivi et arrière-plan" -> R.string.settings_section_background
            "Système, batterie et permissions" -> R.string.settings_section_system
            "Pages", "Liens externes" -> R.string.settings_pages_customization_title
            else -> when {
                key.startsWith("show_") || key.startsWith("f2g_") || key.startsWith("f3g_") ||
                    key.startsWith("f4g_") || key.startsWith("f5g_") -> R.string.settings_section_mapping
                key.startsWith("page_") -> R.string.settings_pages_customization_title
                else -> R.string.settings_section_preferences
            }
        }
        return context?.getString(sectionRes) ?: keySections[key] ?: "Général"
    }

    fun labelForKey(key: String, context: Context? = null): String {
        return when (key) {
            "theme_mode" -> context?.getString(R.string.appearance_theme_title) ?: "Thème"
            "default_operator" -> context?.getString(R.string.settings_default_operator) ?: "Opérateur par défaut"
            "display_style" -> context?.getString(R.string.settings_display_style_title) ?: "Style d'affichage"
            "nav_mode" -> context?.getString(R.string.settings_navigation_mode_title) ?: "Navigation des paramètres"
            "distance_unit", "speed_unit" -> context?.getString(R.string.settings_units_title) ?: keyLabels[key] ?: "Unités"
            else -> keyLabels[key] ?: key
                .removePrefix("page_")
                .removePrefix("share_")
                .removePrefix("site_")
                .removePrefix("community_")
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }
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
