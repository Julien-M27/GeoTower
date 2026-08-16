package fr.geotower.data.backup

import fr.geotower.data.community.PhotoReportHistoryEntry
import fr.geotower.data.notifications.NotificationHistoryEntry
import fr.geotower.data.share.ShareHistoryEntry
import fr.geotower.data.trip.TripLeg
import fr.geotower.data.trip.TripManeuver
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.TripStep
import fr.geotower.data.upload.ExternalPhotoUploadHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Traduction des données de l'application vers le JSON d'une sauvegarde, et retour.
 *
 * **Écrit à la main, et non délégué à Gson**, contrairement aux fichiers de `filesDir`. Gson tire
 * les noms de clés des noms de champs Kotlin, et R8 en mode complet renomme les champs des classes
 * qui ne sont pas `-keep` (voir proguard-rules.pro) : deux versions de l'app pourraient alors ne
 * plus se relire. Un fichier de sauvegarde traverse justement les versions et les appareils, il ne
 * peut pas reposer là-dessus. Les clés ci-dessous sont des littéraux, donc figés pour de bon —
 * en renommer un romprait la lecture des sauvegardes déjà produites.
 *
 * La relecture est **tolérante** : un champ absent prend sa valeur de repli, un champ inconnu est
 * ignoré, et une entrée illisible est écartée sans faire échouer tout le fichier.
 */
internal object AppBackupCodec {

    // --- Partages ---------------------------------------------------------------------------

    fun shareEntryToJson(entry: ShareHistoryEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("kind", entry.kind)
        .put("destination", entry.destination)
        .put("supportId", entry.supportId)
        .put("stationId", entry.stationId)
        .put("label", entry.label)
        .put("address", entry.address)
        .putFinite("latitude", entry.latitude)
        .putFinite("longitude", entry.longitude)
        .put("itemCount", entry.itemCount)
        .put("createdAtMillis", entry.createdAtMillis)
        .putOrNull("contents", entry.contents)
        .putOrNull("darkTheme", entry.darkTheme)
        .putFinite("mapZoom", entry.mapZoom)

    fun shareEntryFromJson(json: JSONObject): ShareHistoryEntry? {
        val id = json.stringOrNull("id") ?: return null
        return ShareHistoryEntry(
            id = id,
            kind = json.optString("kind"),
            destination = json.optString("destination"),
            supportId = json.optString("supportId"),
            stationId = json.optString("stationId"),
            label = json.optString("label"),
            address = json.optString("address"),
            latitude = json.doubleOrNull("latitude"),
            longitude = json.doubleOrNull("longitude"),
            itemCount = json.optInt("itemCount", 1).coerceAtLeast(1),
            createdAtMillis = json.optLong("createdAtMillis"),
            contents = json.stringOrNull("contents"),
            darkTheme = json.booleanOrNull("darkTheme"),
            mapZoom = json.doubleOrNull("mapZoom")
        )
    }

    // --- Notifications ----------------------------------------------------------------------

    fun notificationEntryToJson(entry: NotificationHistoryEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("type", entry.type)
        .put("status", entry.status)
        .put("label", entry.label)
        .put("detail", entry.detail)
        .put("itemCount", entry.itemCount)
        .put("target", entry.target)
        .put("posted", entry.posted)
        .put("createdAtMillis", entry.createdAtMillis)

    fun notificationEntryFromJson(json: JSONObject): NotificationHistoryEntry? {
        val id = json.stringOrNull("id") ?: return null
        return NotificationHistoryEntry(
            id = id,
            type = json.optString("type"),
            status = json.optString("status"),
            label = json.optString("label"),
            detail = json.optString("detail"),
            itemCount = json.optInt("itemCount").coerceAtLeast(0),
            target = json.optString("target"),
            posted = json.optBoolean("posted"),
            createdAtMillis = json.optLong("createdAtMillis")
        )
    }

    // --- Photos envoyées --------------------------------------------------------------------

    /**
     * [thumbnailBase64] porte la vignette elle-même, et non son chemin : celui de l'appareil
     * d'origine ne veut rien dire ici. `thumbnailPath` n'est donc jamais écrit dans la sauvegarde.
     */
    fun uploadEntryToJson(
        entry: ExternalPhotoUploadHistoryEntry,
        thumbnailBase64: String?
    ): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("uploadId", entry.uploadId)
        .put("sourceName", entry.sourceName)
        .put("supportId", entry.supportId)
        .put("operator", entry.operator)
        .put("createdAtMillis", entry.createdAtMillis)
        .put("status", entry.status)
        .put("stripExifBeforeUpload", entry.stripExifBeforeUpload)
        .putOrNull("remotePhotoId", entry.remotePhotoId)
        .putOrNull("remoteImageUrl", entry.remoteImageUrl)
        .putOrNull("remoteUploadedAt", entry.remoteUploadedAt)
        .putOrNull("lastValidationCheckAtMillis", entry.lastValidationCheckAtMillis)
        .put("validationCheckCount", entry.validationCheckCount)
        .putOrNull("thumbnailBase64", thumbnailBase64)

    /** L'entrée est rendue **sans vignette** : l'appelant la réécrit sur ce disque-ci s'il en veut. */
    fun uploadEntryFromJson(json: JSONObject): ExternalPhotoUploadHistoryEntry? {
        val id = json.stringOrNull("id") ?: return null
        return ExternalPhotoUploadHistoryEntry(
            id = id,
            uploadId = json.optString("uploadId"),
            sourceName = json.optString("sourceName"),
            supportId = json.optString("supportId"),
            operator = json.optString("operator"),
            createdAtMillis = json.optLong("createdAtMillis"),
            thumbnailPath = null,
            status = json.optString("status"),
            stripExifBeforeUpload = json.optBoolean("stripExifBeforeUpload"),
            remotePhotoId = json.stringOrNull("remotePhotoId"),
            remoteImageUrl = json.stringOrNull("remoteImageUrl"),
            remoteUploadedAt = json.stringOrNull("remoteUploadedAt"),
            lastValidationCheckAtMillis = json.longOrNull("lastValidationCheckAtMillis"),
            validationCheckCount = json.optInt("validationCheckCount").coerceAtLeast(0)
        )
    }

    fun uploadThumbnailBase64(json: JSONObject): String? = json.stringOrNull("thumbnailBase64")

    // --- Signalements de photos ---------------------------------------------------------------

    fun reportEntryToJson(entry: PhotoReportHistoryEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("photoId", entry.photoId)
        .put("siteId", entry.siteId)
        .put("reason", entry.reason)
        .putOrNull("description", entry.description)
        .putOrNull("photoUrl", entry.photoUrl)
        .putOrNull("operatorLabel", entry.operatorLabel)
        .put("createdAtMillis", entry.createdAtMillis)
        .put("status", entry.status)
        .putOrNull("lastCheckAtMillis", entry.lastCheckAtMillis)
        .put("checkCount", entry.checkCount)
        .putOrNull("resolvedAtMillis", entry.resolvedAtMillis)

    fun reportEntryFromJson(json: JSONObject): PhotoReportHistoryEntry? {
        val id = json.stringOrNull("id") ?: return null
        val photoId = json.stringOrNull("photoId") ?: return null
        return PhotoReportHistoryEntry(
            id = id,
            photoId = photoId,
            siteId = json.optString("siteId"),
            reason = json.optString("reason"),
            description = json.stringOrNull("description"),
            photoUrl = json.stringOrNull("photoUrl"),
            operatorLabel = json.stringOrNull("operatorLabel"),
            createdAtMillis = json.optLong("createdAtMillis"),
            status = json.optString("status"),
            lastCheckAtMillis = json.longOrNull("lastCheckAtMillis"),
            checkCount = json.optInt("checkCount").coerceAtLeast(0),
            resolvedAtMillis = json.longOrNull("resolvedAtMillis")
        )
    }

    // --- Trajets ------------------------------------------------------------------------------

    fun tripToJson(plan: TripPlan): JSONObject = JSONObject()
        .put("id", plan.id)
        .put("schemaVersion", plan.schemaVersion)
        .put("name", plan.name)
        .put("createdAtMillis", plan.createdAtMillis)
        .put("updatedAtMillis", plan.updatedAtMillis)
        .put("profile", plan.profile)
        .put("returnToStart", plan.returnToStart)
        .put("steps", JSONArray().also { array -> plan.steps.forEach { array.put(tripStepToJson(it)) } })
        .put("legs", JSONArray().also { array -> plan.legs.forEach { array.put(tripLegToJson(it)) } })
        .putOrNull("plannedAtMillis", plan.plannedAtMillis)
        .put("reminderOffsetsMinutes", JSONArray(plan.reminderOffsetsMinutes))
        .put("stopDurationMinutes", plan.stopDurationMinutes)
        .put("status", plan.status)
        .put("autoNamed", plan.autoNamed)

    /** Rendu **sans** `sanitized()` : c'est [fr.geotower.data.trip.TripPlanStore] qui l'applique. */
    fun tripFromJson(json: JSONObject): TripPlan? {
        val id = json.stringOrNull("id") ?: return null
        return TripPlan(
            id = id,
            schemaVersion = json.optInt("schemaVersion"),
            name = json.optString("name"),
            createdAtMillis = json.optLong("createdAtMillis"),
            updatedAtMillis = json.optLong("updatedAtMillis"),
            profile = json.optString("profile"),
            returnToStart = json.optBoolean("returnToStart"),
            steps = json.objects("steps").mapNotNull(::tripStepFromJson),
            legs = json.objects("legs").mapNotNull(::tripLegFromJson),
            plannedAtMillis = json.longOrNull("plannedAtMillis"),
            reminderOffsetsMinutes = json.ints("reminderOffsetsMinutes"),
            stopDurationMinutes = json.optInt("stopDurationMinutes"),
            status = json.optString("status"),
            autoNamed = json.optBoolean("autoNamed")
        )
    }

    private fun tripStepToJson(step: TripStep): JSONObject = JSONObject()
        .putFinite("latitude", step.latitude)
        .putFinite("longitude", step.longitude)
        .put("label", step.label)
        .put("kind", step.kind)
        .putOrNull("supportId", step.supportId)
        .putOrNull("visitedAtMillis", step.visitedAtMillis)
        .putOrNull("note", step.note)
        .putOrNull("profileToNext", step.profileToNext)
        .put("photosSentCount", step.photosSentCount)

    private fun tripStepFromJson(json: JSONObject): TripStep? {
        val latitude = json.doubleOrNull("latitude") ?: return null
        val longitude = json.doubleOrNull("longitude") ?: return null
        return TripStep(
            latitude = latitude,
            longitude = longitude,
            label = json.optString("label"),
            kind = json.optString("kind"),
            supportId = json.stringOrNull("supportId"),
            visitedAtMillis = json.longOrNull("visitedAtMillis"),
            note = json.stringOrNull("note"),
            profileToNext = json.stringOrNull("profileToNext"),
            // Absent des sauvegardes d'avant les photos de tournée : zéro est la bonne valeur.
            photosSentCount = json.optInt("photosSentCount")
        )
    }

    private fun tripLegToJson(leg: TripLeg): JSONObject = JSONObject()
        .put("fromIndex", leg.fromIndex)
        .put("toIndex", leg.toIndex)
        .put("profile", leg.profile)
        .putFinite("distanceMeters", leg.distanceMeters)
        .putFinite("durationSeconds", leg.durationSeconds)
        .put("encodedGeometry", leg.encodedGeometry)
        .putOrNull(
            "maneuvers",
            leg.maneuvers?.let { maneuvers ->
                JSONArray().also { array -> maneuvers.forEach { array.put(tripManeuverToJson(it)) } }
            }
        )
        .putOrNull("optimization", leg.optimization)

    private fun tripLegFromJson(json: JSONObject): TripLeg? {
        if (!json.has("fromIndex") || !json.has("toIndex")) return null
        return TripLeg(
            fromIndex = json.optInt("fromIndex", -1),
            toIndex = json.optInt("toIndex", -1),
            profile = json.optString("profile"),
            distanceMeters = json.optDouble("distanceMeters", 0.0).takeIf { it.isFinite() } ?: 0.0,
            durationSeconds = json.optDouble("durationSeconds", 0.0).takeIf { it.isFinite() } ?: 0.0,
            encodedGeometry = json.optString("encodedGeometry"),
            maneuvers = json.optJSONArray("maneuvers")
                ?.let { array -> array.objects().mapNotNull(::tripManeuverFromJson) },
            optimization = json.stringOrNull("optimization")
        )
    }

    private fun tripManeuverToJson(maneuver: TripManeuver): JSONObject = JSONObject()
        .put("type", maneuver.type)
        .putOrNull("modifier", maneuver.modifier)
        .putOrNull("roadName", maneuver.roadName)
        .putFinite("distanceMeters", maneuver.distanceMeters)
        .putFinite("durationSeconds", maneuver.durationSeconds)

    private fun tripManeuverFromJson(json: JSONObject): TripManeuver? {
        val type = json.stringOrNull("type") ?: return null
        return TripManeuver(
            type = type,
            modifier = json.stringOrNull("modifier"),
            roadName = json.stringOrNull("roadName"),
            distanceMeters = json.optDouble("distanceMeters", 0.0).takeIf { it.isFinite() } ?: 0.0,
            durationSeconds = json.optDouble("durationSeconds", 0.0).takeIf { it.isFinite() } ?: 0.0
        )
    }
}

// --- Aides de lecture et d'écriture -----------------------------------------------------------
//
// `JSONObject.put(clé, null)` écrit la chaîne "null" pour un String et lève pour un Double nul :
// ces aides écrivent simplement rien, et relisent `null` là où la clé manque ou vaut JSONObject.NULL.

internal fun JSONObject.putOrNull(key: String, value: Any?): JSONObject {
    if (value == null) return this
    return put(key, value)
}

/**
 * `JSONObject.put` **lève** sur un NaN ou un infini. Les distances viennent du service d'itinéraire
 * et les coordonnées d'un fichier relu : une seule valeur aberrante ne doit pas faire échouer tout
 * l'export. La clé est alors simplement omise, et la relecture la traitera comme absente.
 */
internal fun JSONObject.putFinite(key: String, value: Double?): JSONObject {
    if (value == null || !value.isFinite()) return this
    return put(key, value)
}

internal fun JSONObject.stringOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

internal fun JSONObject.doubleOrNull(key: String): Double? {
    if (isNull(key)) return null
    return optDouble(key).takeIf { it.isFinite() }
}

internal fun JSONObject.longOrNull(key: String): Long? {
    if (isNull(key)) return null
    return optLong(key)
}

internal fun JSONObject.booleanOrNull(key: String): Boolean? {
    if (isNull(key)) return null
    return optBoolean(key)
}

internal fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.objects().orEmpty()

internal fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.ints(key: String): List<Int> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optInt(it) }
}
