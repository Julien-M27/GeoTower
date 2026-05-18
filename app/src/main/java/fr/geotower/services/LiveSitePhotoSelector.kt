package fr.geotower.services

import android.content.Context
import android.content.SharedPreferences
import fr.geotower.BuildConfig
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.community.CommunityDataFeature
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.community.CommunityDataSource
import java.util.Locale

internal data class LiveSitePhotoCandidate(
    val url: String,
    val maxBytes: Int
)

internal object LiveSitePhotoSelector {
    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val DEFAULT_QUERY_LIMIT = 20
    private const val FAVORITE_QUERY_LIMIT = 50
    private const val MAX_THUMBNAIL_BYTES = 3 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

    suspend fun firstCandidate(
        context: Context,
        siteId: String,
        operator: String
    ): LiveSitePhotoCandidate? {
        val normalizedSiteId = siteId.trim().takeIf { it.isNotBlank() } ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val operatorKey = CommunityDataPreferences.operatorKeyFor(operator) ?: return null
        val photoFeature = photoFeature(operatorKey) ?: return null
        val sources = CommunityDataPreferences
            .orderedSources(prefs, operatorKey, photoFeature)
            .filter { source ->
                CommunityDataPreferences.isEnabled(
                    prefs,
                    operatorKey,
                    CommunityDataPreferences.FEATURE_PHOTOS,
                    source.id
                )
            }
        val favoriteIdsBySource = sources.mapNotNull { source ->
            CommunityDataPreferences.favoritePhotoIdForSource(prefs, normalizedSiteId, source.id)
                ?.let { favoriteId -> source.id to favoriteId }
        }.toMap()

        var previousEnabledSourceHasPhotos = false
        var fallbackPhoto: SourcePhoto? = null

        for (source in sources) {
            val sourcePhotos = sourcePhotos(
                sourceId = source.id,
                siteId = normalizedSiteId,
                operator = operator,
                favoriteId = favoriteIdsBySource[source.id]
            )
            val sourceHasPhotos = sourcePhotos.isNotEmpty()
            val isVisibleSource = isVisibleSource(
                prefs = prefs,
                operatorKey = operatorKey,
                source = source,
                previousEnabledSourceHasPhotos = previousEnabledSourceHasPhotos
            )

            if (isVisibleSource) {
                if (fallbackPhoto == null) {
                    fallbackPhoto = sourcePhotos.firstOrNull()
                }

                val favoriteId = favoriteIdsBySource[source.id]
                if (favoriteId != null) {
                    sourcePhotos.firstOrNull { photo -> photo.stableId == favoriteId }
                        ?.toCandidate()
                        ?.let { return it }
                }

                if (favoriteIdsBySource.isEmpty() && fallbackPhoto != null) {
                    return fallbackPhoto.toCandidate()
                }
            }

            if (sourceHasPhotos) {
                previousEnabledSourceHasPhotos = true
            }
        }

        return fallbackPhoto?.toCandidate()
    }

    fun cacheKey(context: Context, operator: String, siteId: String): String {
        val normalizedSiteId = siteId.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val operatorKey = CommunityDataPreferences.operatorKeyFor(operator)
            ?: operator.trim().uppercase(Locale.US)
        return "$operatorKey:$normalizedSiteId:${sourceSignature(prefs, operatorKey, normalizedSiteId)}"
    }

    private fun isVisibleSource(
        prefs: SharedPreferences,
        operatorKey: String,
        source: CommunityDataSource,
        previousEnabledSourceHasPhotos: Boolean
    ): Boolean {
        return !CommunityDataPreferences.isSourceFallbackOnly(
            prefs,
            operatorKey,
            CommunityDataPreferences.FEATURE_PHOTOS,
            source.id
        ) || !previousEnabledSourceHasPhotos
    }

    private suspend fun sourcePhotos(
        sourceId: String,
        siteId: String,
        operator: String,
        favoriteId: String?
    ): List<SourcePhoto> {
        return when (sourceId) {
            CommunityDataPreferences.SOURCE_SIGNALQUEST -> signalQuestPhotos(siteId, operator, favoriteId)
            CommunityDataPreferences.SOURCE_CELLULARFR -> cellularFrPhotos(siteId)
            else -> emptyList()
        }
    }

    private suspend fun signalQuestPhotos(
        siteId: String,
        operator: String,
        favoriteId: String?
    ): List<SourcePhoto> {
        val signalQuestOperator = SignalQuestOperators.operatorParamFor(operator) ?: return emptyList()
        if (BuildConfig.SQ_API_KEY.isBlank()) return emptyList()

        val response = SignalQuestClient.api.getSitePhotos(
            authHeader = "Bearer ${BuildConfig.SQ_API_KEY}",
            siteId = siteId,
            limit = if (favoriteId.isNullOrBlank()) DEFAULT_QUERY_LIMIT else FAVORITE_QUERY_LIMIT
        )
        if (!response.isSuccessful) return emptyList()

        return response.body()
            ?.data
            .orEmpty()
            .asSequence()
            .filter { photo ->
                val photoOperator = photo.operator
                photoOperator.isNullOrBlank() ||
                    photoOperator.equals(signalQuestOperator, ignoreCase = true) ||
                    SignalQuestOperators.operatorParamFor(photoOperator)
                        .equals(signalQuestOperator, ignoreCase = true)
            }
            .mapNotNull { photo ->
                val imageUrl = photo.imageUrl.takeIf { it.isNotBlank() }
                val thumbnailUrl = photo.thumbnailUrl?.takeIf { it.isNotBlank() }
                val stableId = photo.id?.takeIf { it.isNotBlank() }
                    ?: imageUrl
                    ?: thumbnailUrl
                    ?: return@mapNotNull null

                SourcePhoto(
                    stableId = stableId,
                    imageUrl = imageUrl,
                    thumbnailUrl = thumbnailUrl
                )
            }
            .toList()
    }

    private suspend fun cellularFrPhotos(siteId: String): List<SourcePhoto> {
        return CellularFrApi.getCellularFrPhotos(siteId)
            .mapNotNull { photo ->
                val url = photo.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SourcePhoto(
                    stableId = url,
                    imageUrl = url,
                    thumbnailUrl = null
                )
            }
    }

    private fun photoFeature(operatorKey: String): CommunityDataFeature? {
        return CommunityDataPreferences.operators
            .firstOrNull { it.key == operatorKey }
            ?.features
            ?.firstOrNull { it.id == CommunityDataPreferences.FEATURE_PHOTOS }
    }

    private fun sourceSignature(
        prefs: SharedPreferences,
        operatorKey: String,
        siteId: String
    ): String {
        val photoFeature = photoFeature(operatorKey) ?: return "none"

        return CommunityDataPreferences.orderedSources(prefs, operatorKey, photoFeature)
            .joinToString(">") { source ->
                val enabled = CommunityDataPreferences.isEnabled(
                    prefs,
                    operatorKey,
                    CommunityDataPreferences.FEATURE_PHOTOS,
                    source.id
                )
                val fallback = CommunityDataPreferences.isSourceFallbackOnly(
                    prefs,
                    operatorKey,
                    CommunityDataPreferences.FEATURE_PHOTOS,
                    source.id
                )
                val favoriteId = CommunityDataPreferences
                    .favoritePhotoIdForSource(prefs, siteId, source.id)
                    .orEmpty()
                "${source.id}:${if (enabled) 1 else 0}:${if (fallback) 1 else 0}:$favoriteId"
            }
    }

    private data class SourcePhoto(
        val stableId: String,
        val imageUrl: String?,
        val thumbnailUrl: String?
    ) {
        fun toCandidate(): LiveSitePhotoCandidate? {
            val thumbnail = thumbnailUrl?.takeIf { it.isNotBlank() }
            val image = imageUrl?.takeIf { it.isNotBlank() }
            return when {
                thumbnail != null -> LiveSitePhotoCandidate(
                    url = thumbnail,
                    maxBytes = MAX_THUMBNAIL_BYTES
                )
                image != null -> LiveSitePhotoCandidate(
                    url = image,
                    maxBytes = MAX_IMAGE_BYTES
                )
                else -> null
            }
        }
    }
}
