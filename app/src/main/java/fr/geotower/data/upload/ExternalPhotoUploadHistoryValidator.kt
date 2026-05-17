package fr.geotower.data.upload

import android.content.Context
import fr.geotower.BuildConfig
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.api.SqPhotoData
import fr.geotower.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ExternalPhotoUploadHistoryValidator {
    private const val TAG = "GeoTowerUploadHistory"
    private const val VALIDATION_LOOKUP_LIMIT = 100
    private const val LEGACY_MATCH_WINDOW_MS = 10L * 60L * 1000L

    suspend fun refreshPendingSignalQuestPhotos(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val pendingEntries = ExternalPhotoUploadHistoryStore.read(appContext)
            .filter { entry ->
                entry.sourceName == ExternalPhotoUploadHistoryStore.SOURCE_SIGNALQUEST &&
                    entry.status == ExternalPhotoUploadHistoryStore.STATUS_AWAITING_VALIDATION &&
                    entry.supportId.isNotBlank()
            }

        if (pendingEntries.isEmpty()) return@withContext false

        var changed = false
        pendingEntries
            .groupBy { entry -> ValidationGroup(entry.supportId, entry.operator) }
            .forEach { (group, entries) ->
                val photos = fetchApprovedPhotos(group) ?: return@forEach
                val usedRemoteKeys = mutableSetOf<String>()

                entries.forEach { entry ->
                    val match = photos.firstOrNull { photo ->
                        val remoteKey = photo.id ?: photo.imageUrl
                        remoteKey !in usedRemoteKeys && photo.matches(entry)
                    }

                    if (match != null) {
                        usedRemoteKeys += match.id ?: match.imageUrl
                        ExternalPhotoUploadHistoryStore.markValidated(
                            context = appContext,
                            entryId = entry.id,
                            remotePhotoId = match.id,
                            remoteImageUrl = match.imageUrl,
                            remoteUploadedAt = match.uploadedAt
                        )
                        changed = true
                    } else {
                        ExternalPhotoUploadHistoryStore.recordValidationCheck(appContext, entry.id)
                    }
                }
            }

        changed
    }

    private suspend fun fetchApprovedPhotos(group: ValidationGroup): List<SqPhotoData>? {
        return runCatching {
            val response = SignalQuestClient.api.getSitePhotos(
                authHeader = "Bearer ${BuildConfig.SQ_API_KEY}",
                siteId = group.supportId,
                operator = group.operator,
                limit = VALIDATION_LOOKUP_LIMIT
            )
            if (response.isSuccessful) {
                response.body()?.data.orEmpty()
            } else {
                response.errorBody()?.close()
                null
            }
        }.onFailure { error ->
            AppLogger.w(TAG, "SignalQuest validation refresh failed", error)
        }.getOrNull()
    }

    private fun SqPhotoData.matches(entry: ExternalPhotoUploadHistoryEntry): Boolean {
        val knownRemoteId = entry.remotePhotoId
        if (!knownRemoteId.isNullOrBlank()) {
            return id == knownRemoteId
        }

        val uploadedAtMillis = uploadedAt?.toEpochMillisOrNull() ?: return false
        val isSameOperator = operator.equals(entry.operator, ignoreCase = true)
        val isGeoTowerUpload = authorName?.contains("GeoTower", ignoreCase = true) == true
        val isNearUploadTime = uploadedAtMillis in (entry.createdAtMillis - LEGACY_MATCH_WINDOW_MS)..(entry.createdAtMillis + LEGACY_MATCH_WINDOW_MS)

        return isSameOperator && isGeoTowerUpload && isNearUploadTime
    }

    private fun String.toEpochMillisOrNull(): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(this)?.time
            }.getOrNull()
        }
    }

    private data class ValidationGroup(
        val supportId: String,
        val operator: String
    )
}
