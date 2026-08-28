package fr.geotower.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.hidden.HiddenSiteRecord
import fr.geotower.data.hidden.HiddenSitesStore
import fr.geotower.data.community.PhotoReportHistoryStore
import fr.geotower.data.notifications.NotificationHistoryStore
import fr.geotower.data.share.ShareHistoryStore
import fr.geotower.data.trip.TripPlanStore
import fr.geotower.data.upload.ExternalPhotoUploadHistoryEntry
import fr.geotower.data.upload.ExternalPhotoUploadHistoryStore
import fr.geotower.utils.PreferenceProfileImportResolution
import fr.geotower.utils.PreferenceProfileManager
import fr.geotower.utils.PreferenceStores
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sauvegarde et restauration des données personnelles de l'application : historiques, trajets,
 * photos favorites, compteurs et profils de réglages. Un seul fichier JSON, produit ici et relu
 * ici, que l'utilisateur transporte d'un téléphone à l'autre comme il l'entend.
 *
 * **L'import ne retire jamais rien.** Chaque rubrique n'ajoute que ce qui manque, en s'appuyant sur
 * la clé naturelle de ses éléments — identifiant d'entrée pour les historiques, photo signalée pour
 * les signalements, clé de préférence pour les favoris. Deux conséquences voulues :
 *
 * - importer deux fois la même sauvegarde ne change rien la seconde fois ;
 * - deux téléphones peuvent s'échanger leurs sauvegardes dans les deux sens, autant de fois qu'on
 *   veut, et convergent vers la réunion de ce qu'ils avaient chacun.
 *
 * Seuls les trajets font exception, et par nécessité : une tournée n'est pas un événement révolu
 * mais un document qu'on rouvre, donc la version la plus récemment modifiée gagne. Voir
 * [TripPlanStore.mergePlans].
 *
 * Ce qui reste volontairement **hors** de la sauvegarde : les caches serveur (statistiques
 * départementales), les bases de données, les cartes hors-ligne et les envois en cours. Les
 * exclusions de sites, elles, font partie des données personnelles et voyagent avec la sauvegarde.
 */
object AppBackupManager {
    /** Marqueur d'enveloppe : ce qu'on refuse d'ouvrir quand il manque. */
    const val FORMAT = "geotower.backup"

    const val SCHEMA_VERSION = 1

    const val MIME_TYPE = "application/json"

    /** Le seul compteur cumulé de l'application : le total de photos envoyées depuis l'installation. */
    private const val KEY_LIFETIME_UPLOADS = "total_lifetime_uploads"

    private const val EXPORT_FILE_PREFIX = "geotower_sauvegarde_"
    private const val EXPORT_DIR_NAME = "backup_exports"

    // --- Ce que cet appareil a à sauvegarder --------------------------------------------------

    /**
     * Pour [BackupSection.COUNTERS], [BackupSectionSize.itemCount] porte **la valeur du compteur**
     * et non un nombre d'éléments : la rubrique tient en une ligne, et ce qui intéresse
     * l'utilisateur est le total de photos qu'il emporte, pas le fait qu'il y ait une valeur.
     */
    fun deviceSizes(context: Context): List<BackupSectionSize> {
        val prefs = prefs(context)
        return listOf(
            BackupSectionSize(BackupSection.SHARE_HISTORY, ShareHistoryStore.read(context).size),
            BackupSectionSize(BackupSection.NOTIFICATION_HISTORY, NotificationHistoryStore.read(context).size),
            BackupSectionSize(BackupSection.PHOTO_UPLOADS, ExternalPhotoUploadHistoryStore.read(context).size),
            BackupSectionSize(BackupSection.PHOTO_REPORTS, PhotoReportHistoryStore.read(context).size),
            BackupSectionSize(BackupSection.TRIPS, TripPlanStore.read(context).size),
            BackupSectionSize(BackupSection.PHOTO_FAVORITES, CommunityDataPreferences.favoritePhotoEntries(prefs).size),
            BackupSectionSize(BackupSection.COUNTERS, prefs.getInt(KEY_LIFETIME_UPLOADS, 0)),
            BackupSectionSize(BackupSection.SETTINGS_PROFILES, PreferenceProfileManager.profiles(context).size),
            BackupSectionSize(BackupSection.HIDDEN_SITES, HiddenSitesStore.records(context).size)
        )
    }

    // --- Export -------------------------------------------------------------------------------

    /**
     * Construit le document de sauvegarde. [sections] dit quoi emporter ; une rubrique non demandée
     * est absente du fichier, elle n'y figure pas vide — c'est ce qui permet à l'import de
     * distinguer « rien à apporter » de « non sauvegardé ».
     */
    fun buildBackup(
        context: Context,
        sections: Set<String>,
        includeThumbnails: Boolean = true,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val prefs = prefs(context)
        val sectionsJson = JSONObject()

        if (BackupSection.SHARE_HISTORY in sections) {
            sectionsJson.put(
                BackupSection.SHARE_HISTORY,
                entriesSection(ShareHistoryStore.read(context).map(AppBackupCodec::shareEntryToJson))
            )
        }
        if (BackupSection.NOTIFICATION_HISTORY in sections) {
            sectionsJson.put(
                BackupSection.NOTIFICATION_HISTORY,
                entriesSection(NotificationHistoryStore.read(context).map(AppBackupCodec::notificationEntryToJson))
            )
        }
        if (BackupSection.PHOTO_UPLOADS in sections) {
            sectionsJson.put(
                BackupSection.PHOTO_UPLOADS,
                entriesSection(
                    ExternalPhotoUploadHistoryStore.read(context).map { entry ->
                        AppBackupCodec.uploadEntryToJson(
                            entry = entry,
                            thumbnailBase64 = if (includeThumbnails) encodedThumbnail(entry) else null
                        )
                    }
                )
            )
        }
        if (BackupSection.PHOTO_REPORTS in sections) {
            sectionsJson.put(
                BackupSection.PHOTO_REPORTS,
                entriesSection(PhotoReportHistoryStore.read(context).map(AppBackupCodec::reportEntryToJson))
            )
        }
        if (BackupSection.TRIPS in sections) {
            sectionsJson.put(
                BackupSection.TRIPS,
                entriesSection(TripPlanStore.read(context).map(AppBackupCodec::tripToJson))
            )
        }
        if (BackupSection.PHOTO_FAVORITES in sections) {
            val favorites = CommunityDataPreferences.favoritePhotoEntries(prefs).map { favorite ->
                JSONObject()
                    .put("siteId", favorite.siteId)
                    .put("bucketId", favorite.bucketId)
                    .put("photoId", favorite.photoId)
            }
            sectionsJson.put(BackupSection.PHOTO_FAVORITES, entriesSection(favorites))
        }
        if (BackupSection.COUNTERS in sections) {
            sectionsJson.put(
                BackupSection.COUNTERS,
                JSONObject().put(
                    "values",
                    JSONObject().put(KEY_LIFETIME_UPLOADS, prefs.getInt(KEY_LIFETIME_UPLOADS, 0))
                )
            )
        }
        if (BackupSection.SETTINGS_PROFILES in sections) {
            val profileIds = PreferenceProfileManager.profiles(context).map { it.id }.toSet()
            val exported = runCatching {
                JSONObject(PreferenceProfileManager.exportProfilesJson(context, profileIds))
            }.getOrNull()
            if (exported != null) sectionsJson.put(BackupSection.SETTINGS_PROFILES, exported)
        }
        if (BackupSection.HIDDEN_SITES in sections) {
            sectionsJson.put(
                BackupSection.HIDDEN_SITES,
                entriesSection(HiddenSitesStore.records(context).map(::hiddenSiteToJson))
            )
        }

        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAtMillis", exportedAtMillis)
            .put("appVersionName", appVersionName(context))
            .put("deviceLabel", deviceLabel())
            .put("sections", sectionsJson)
            .toString(2)
    }

    fun suggestedFileName(exportedAtMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(exportedAtMillis))
        return "$EXPORT_FILE_PREFIX$stamp.json"
    }

    /**
     * Écrit le document dans le cache et rend l'URI à partager. Le partage se fait par
     * `FLAG_ACTIVITY_NEW_TASK` : le contexte n'est pas forcément une Activity.
     */
    fun shareBackup(context: Context, json: String, fileName: String, chooserTitle: String) {
        val directory = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(json, Charsets.UTF_8)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // --- Aperçu -------------------------------------------------------------------------------

    /**
     * Lit le fichier et compte, rubrique par rubrique, ce qu'il apporterait ici. **N'importe rien**
     * — c'est [importBackup] qui écrit, et lui seul.
     *
     * @throws BackupFormatException si le document n'est pas une sauvegarde GeoTower.
     */
    fun preview(context: Context, text: String): BackupImportPreview {
        val root = runCatching { JSONObject(text) }.getOrElse {
            throw BackupFormatException("document illisible")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("format inconnu : ${root.optString("format")}")
        }

        val sectionsJson = root.optJSONObject("sections") ?: JSONObject()
        val previews = mutableListOf<BackupSectionPreview>()
        val unknown = mutableListOf<String>()
        sectionsJson.keys().forEach { key ->
            if (key !in BackupSection.ALL) unknown += key
        }

        BackupSection.ALL.forEach { section ->
            val sectionJson = sectionsJson.optJSONObject(section) ?: return@forEach
            previews += previewSection(context, section, sectionJson)
        }

        return BackupImportPreview(
            schemaVersion = root.optInt("schemaVersion", 1),
            exportedAtMillis = root.optLong("exportedAtMillis"),
            appVersionName = root.optString("appVersionName"),
            deviceLabel = root.optString("deviceLabel"),
            sections = previews,
            unknownSections = unknown,
            rawJson = text
        )
    }

    private fun previewSection(context: Context, section: String, json: JSONObject): BackupSectionPreview {
        val entries = json.objects("entries")
        return when (section) {
            BackupSection.SHARE_HISTORY -> {
                val known = ShareHistoryStore.read(context).mapTo(HashSet()) { it.id }
                countByKey(section, entries.mapNotNull { it.stringOrNull("id") }, known)
            }
            BackupSection.NOTIFICATION_HISTORY -> {
                val known = NotificationHistoryStore.read(context).mapTo(HashSet()) { it.id }
                countByKey(section, entries.mapNotNull { it.stringOrNull("id") }, known)
            }
            BackupSection.PHOTO_UPLOADS -> {
                val known = ExternalPhotoUploadHistoryStore.read(context).mapTo(HashSet()) { it.id }
                countByKey(section, entries.mapNotNull { it.stringOrNull("id") }, known)
            }
            BackupSection.PHOTO_REPORTS -> {
                val known = PhotoReportHistoryStore.read(context).mapTo(HashSet()) { it.photoId }
                countByKey(section, entries.mapNotNull { it.stringOrNull("photoId") }, known)
            }
            BackupSection.TRIPS -> {
                val local = TripPlanStore.read(context).associateBy { it.id }
                val incoming = entries.mapNotNull(AppBackupCodec::tripFromJson)
                    .mapNotNull { it.sanitized() }
                    .filterNot { it.isEmptyDraft() }
                BackupSectionPreview(
                    section = section,
                    incomingCount = incoming.size,
                    newCount = incoming.count { it.id !in local },
                    refreshableCount = incoming.count { plan ->
                        local[plan.id]?.let { plan.updatedAtMillis > it.updatedAtMillis } == true
                    }
                )
            }
            BackupSection.PHOTO_FAVORITES -> {
                val prefs = prefs(context)
                val incoming = entries.mapNotNull { favoritePrefKey(it) }.distinct()
                BackupSectionPreview(
                    section = section,
                    incomingCount = incoming.size,
                    newCount = incoming.count { !prefs.contains(it) }
                )
            }
            BackupSection.COUNTERS -> {
                val incoming = json.optJSONObject("values")?.optInt(KEY_LIFETIME_UPLOADS, 0) ?: 0
                val local = prefs(context).getInt(KEY_LIFETIME_UPLOADS, 0)
                BackupSectionPreview(
                    section = section,
                    incomingCount = if (incoming > 0) 1 else 0,
                    newCount = if (incoming > local) 1 else 0
                )
            }
            BackupSection.SETTINGS_PROFILES -> {
                val parsed = runCatching {
                    PreferenceProfileManager.parseImport(context, json.toString())
                }.getOrNull()
                BackupSectionPreview(
                    section = section,
                    incomingCount = parsed?.profiles?.size ?: 0,
                    newCount = (parsed?.profiles?.size ?: 0) - (parsed?.conflicts?.size ?: 0)
                )
            }
            BackupSection.HIDDEN_SITES -> {
                val incoming = entries.mapNotNull(::hiddenSiteFromJson).distinctBy(::hiddenSiteIdentity)
                val known = HiddenSitesStore.records(context).mapTo(HashSet(), ::hiddenSiteIdentity)
                BackupSectionPreview(
                    section = section,
                    incomingCount = incoming.size,
                    newCount = incoming.count { hiddenSiteIdentity(it) !in known }
                )
            }
            else -> BackupSectionPreview(section, entries.size, 0)
        }
    }

    private fun countByKey(section: String, incomingKeys: List<String>, knownKeys: Set<String>): BackupSectionPreview {
        val distinct = incomingKeys.distinct()
        return BackupSectionPreview(
            section = section,
            incomingCount = distinct.size,
            newCount = distinct.count { it !in knownKeys }
        )
    }

    // --- Import -------------------------------------------------------------------------------

    /**
     * Applique les rubriques retenues. Chacune est indépendante : une rubrique qui échoue laisse les
     * autres passer, plutôt que d'abandonner un import à moitié fait sans le dire.
     */
    fun importBackup(
        context: Context,
        preview: BackupImportPreview,
        sections: Set<String>
    ): BackupImportResult {
        val root = runCatching { JSONObject(preview.rawJson) }.getOrElse {
            throw BackupFormatException("document illisible")
        }
        val sectionsJson = root.optJSONObject("sections") ?: JSONObject()

        val outcomes = BackupSection.ALL.mapNotNull { section ->
            if (section !in sections) return@mapNotNull null
            val sectionJson = sectionsJson.optJSONObject(section) ?: return@mapNotNull null
            runCatching { importSection(context, section, sectionJson) }
                .getOrDefault(BackupSectionOutcome(section, 0))
        }
        return BackupImportResult(outcomes)
    }

    private fun importSection(context: Context, section: String, json: JSONObject): BackupSectionOutcome {
        val entries = json.objects("entries")
        return when (section) {
            BackupSection.SHARE_HISTORY -> BackupSectionOutcome(
                section,
                ShareHistoryStore.mergeEntries(context, entries.mapNotNull(AppBackupCodec::shareEntryFromJson))
            )
            BackupSection.NOTIFICATION_HISTORY -> BackupSectionOutcome(
                section,
                NotificationHistoryStore.mergeEntries(
                    context,
                    entries.mapNotNull(AppBackupCodec::notificationEntryFromJson)
                )
            )
            BackupSection.PHOTO_UPLOADS -> BackupSectionOutcome(section, importUploads(context, entries))
            BackupSection.PHOTO_REPORTS -> BackupSectionOutcome(
                section,
                PhotoReportHistoryStore.mergeEntries(context, entries.mapNotNull(AppBackupCodec::reportEntryFromJson))
            )
            BackupSection.TRIPS -> {
                val merged = TripPlanStore.mergePlans(context, entries.mapNotNull(AppBackupCodec::tripFromJson))
                BackupSectionOutcome(section, merged.added, merged.refreshed)
            }
            BackupSection.PHOTO_FAVORITES -> BackupSectionOutcome(section, importFavorites(context, entries))
            BackupSection.COUNTERS -> BackupSectionOutcome(section, importCounters(context, json))
            BackupSection.SETTINGS_PROFILES -> importProfiles(context, json)
            BackupSection.HIDDEN_SITES -> BackupSectionOutcome(
                section,
                HiddenSitesStore.merge(context, entries.mapNotNull(::hiddenSiteFromJson))
            )
            else -> BackupSectionOutcome(section, 0)
        }
    }

    /**
     * Les vignettes voyagent en base64 dans le fichier, jamais par leur chemin : celui de l'appareil
     * d'origine ne mène nulle part ici. On n'en réécrit que pour les entrées réellement nouvelles,
     * sans quoi un import répété sèmerait des fichiers orphelins dans `filesDir`.
     */
    private fun importUploads(context: Context, entries: List<JSONObject>): Int {
        val known = ExternalPhotoUploadHistoryStore.read(context).mapTo(HashSet()) { it.id }
        val toAdd = mutableListOf<ExternalPhotoUploadHistoryEntry>()
        entries.forEach { json ->
            val entry = AppBackupCodec.uploadEntryFromJson(json) ?: return@forEach
            if (!known.add(entry.id)) return@forEach
            val thumbnailPath = AppBackupCodec.uploadThumbnailBase64(json)
                ?.let { base64 -> runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() }
                ?.let { bytes -> ExternalPhotoUploadHistoryStore.writeThumbnail(context, entry.id, bytes) }
            toAdd += entry.copy(thumbnailPath = thumbnailPath)
        }
        return ExternalPhotoUploadHistoryStore.mergeEntries(context, toAdd)
    }

    /** Un favori déjà choisi ici n'est **jamais** remplacé : c'est le choix fait sur cet appareil. */
    private fun importFavorites(context: Context, entries: List<JSONObject>): Int {
        val prefs = prefs(context)
        val editor = prefs.edit()
        var added = 0
        entries.forEach { json ->
            val key = favoritePrefKey(json) ?: return@forEach
            val photoId = json.stringOrNull("photoId") ?: return@forEach
            if (prefs.contains(key)) return@forEach
            editor.putString(key, photoId)
            added++
        }
        if (added > 0) editor.apply()
        return added
    }

    /**
     * Le total de photos envoyées se fusionne par le **maximum**, et non par la somme : additionner
     * doublerait le compte à chaque réimport de la même sauvegarde. Le total est donc celui du
     * téléphone qui en a le plus — il ne redescend jamais.
     */
    private fun importCounters(context: Context, json: JSONObject): Int {
        val incoming = json.optJSONObject("values")?.optInt(KEY_LIFETIME_UPLOADS, 0) ?: 0
        val prefs = prefs(context)
        if (incoming <= prefs.getInt(KEY_LIFETIME_UPLOADS, 0)) return 0
        prefs.edit().putInt(KEY_LIFETIME_UPLOADS, incoming).apply()
        return 1
    }

    /**
     * Les profils s'ajoutent par leur nom, et un nom déjà pris est laissé tel quel — voir
     * [PreferenceProfileImportResolution.SkipExisting]. Renommer l'importé, comme le fait l'import
     * de profils depuis les réglages, créerait un « Profil 2 », puis un « Profil 3 » à chaque
     * réimport de la même sauvegarde.
     */
    private fun importProfiles(context: Context, json: JSONObject): BackupSectionOutcome {
        val parsed = runCatching {
            PreferenceProfileManager.parseImport(context, json.toString())
        }.getOrNull() ?: return BackupSectionOutcome(BackupSection.SETTINGS_PROFILES, 0)

        val result = PreferenceProfileManager.importProfiles(
            context = context,
            preview = parsed,
            resolution = PreferenceProfileImportResolution.SkipExisting
        )
        return BackupSectionOutcome(BackupSection.SETTINGS_PROFILES, result.addedCount)
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private fun entriesSection(entries: List<JSONObject>): JSONObject =
        JSONObject().put("entries", JSONArray().also { array -> entries.forEach { array.put(it) } })

    private fun hiddenSiteToJson(record: HiddenSiteRecord): JSONObject = JSONObject()
        .put("physicalSiteKey", record.physicalSiteKey)
        .put("operatorKey", record.operatorKey)
        .put("operatorLabel", record.operatorLabel)
        .put("idAnfr", record.idAnfr)
        .put("latitude", record.latitude)
        .put("longitude", record.longitude)
        .put("azimuts", record.azimuts)
        .put("azimutsFh", record.azimutsFh)
        .put("hiddenAtMillis", record.hiddenAtMillis)

    private fun hiddenSiteFromJson(json: JSONObject): HiddenSiteRecord? {
        val siteKey = json.stringOrNull("physicalSiteKey") ?: return null
        val operatorKey = json.stringOrNull("operatorKey") ?: return null
        if (siteKey.isBlank() || operatorKey.isBlank()) return null
        return HiddenSiteRecord(
            physicalSiteKey = siteKey,
            operatorKey = operatorKey,
            operatorLabel = json.stringOrNull("operatorLabel").orEmpty(),
            idAnfr = json.stringOrNull("idAnfr").orEmpty(),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            azimuts = json.stringOrNull("azimuts"),
            azimutsFh = json.stringOrNull("azimutsFh"),
            hiddenAtMillis = json.optLong("hiddenAtMillis", 0L)
        )
    }

    private fun hiddenSiteIdentity(record: HiddenSiteRecord): String =
        "${record.physicalSiteKey.trim()}|${record.operatorKey.trim().uppercase(Locale.ROOT)}"

    private fun favoritePrefKey(json: JSONObject): String? {
        val siteId = json.stringOrNull("siteId") ?: return null
        val bucketId = json.stringOrNull("bucketId") ?: return null
        if (json.stringOrNull("photoId") == null) return null
        return CommunityDataPreferences.sitePhotoFavoritePrefKey(siteId, bucketId)
    }

    private fun encodedThumbnail(entry: ExternalPhotoUploadHistoryEntry): String? {
        val bytes = ExternalPhotoUploadHistoryStore.readThumbnailBytes(entry) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)

    private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
