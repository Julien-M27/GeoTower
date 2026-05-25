package fr.geotower.services

import android.content.Context
import android.content.SharedPreferences
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.api.SignalQuestClient
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.community.CommunityDataFeature
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.community.CommunityDataSource
import fr.geotower.utils.OperatorColors
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
    private const val CACHE_VERSION = "v2"

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

        var previousEnabledSourceHasPhotos = false
        val visiblePhotos = mutableListOf<SourcePhoto>()

        for (source in sources) {
            val sourceFavoriteId = favoritePhotoIdForSource(
                prefs = prefs,
                siteId = normalizedSiteId,
                source = source,
                operatorKey = operatorKey
            )
            val sourcePhotos = sourcePhotos(
                sourceId = source.id,
                siteId = normalizedSiteId,
                operator = operator,
                favoriteId = sourceFavoriteId
            )
            val sourceHasPhotos = sourcePhotos.isNotEmpty()
            val isVisibleSource = isVisibleSource(
                prefs = prefs,
                operatorKey = operatorKey,
                source = source,
                previousEnabledSourceHasPhotos = previousEnabledSourceHasPhotos
            )

            if (isVisibleSource) {
                visiblePhotos += sourcePhotos
            }

            if (sourceHasPhotos) {
                previousEnabledSourceHasPhotos = true
            }
        }

        val favoriteIdsByBucket = visiblePhotos
            .mapNotNull { photo ->
                favoriteIdForBucket(prefs, normalizedSiteId, photo.favoriteBucketId())
                    ?.let { favoriteId -> photo.favoriteBucketId() to favoriteId }
            }
            .toMap()
        val sourceRank = sources.withIndex().associate { (index, source) -> source.id to index }
        val signalQuestOperatorOrder = signalQuestOperatorOrder(operator)

        return visiblePhotos
            .sortedWith(
                compareBy<SourcePhoto>(
                    { photo ->
                        val bucketId = photo.favoriteBucketId()
                        if (bucketId != null && favoriteIdsByBucket[bucketId] == photo.stableId) 0 else 1
                    },
                    { photo -> sourceRank[photo.sourceId] ?: 99 },
                    { photo ->
                        if (photo.sourceId == CommunityDataPreferences.SOURCE_SIGNALQUEST) {
                            signalQuestOperatorRank(photo.operatorKey, signalQuestOperatorOrder)
                        } else {
                            0
                        }
                    }
                )
            )
            .firstOrNull()
            ?.toCandidate()
    }

    fun cacheKey(context: Context, operator: String, siteId: String): String {
        val normalizedSiteId = siteId.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val operatorKey = CommunityDataPreferences.operatorKeyFor(operator)
            ?: operator.trim().uppercase(Locale.US)
        return "$CACHE_VERSION:$operatorKey:$normalizedSiteId:${sourceSignature(prefs, operatorKey, normalizedSiteId)}"
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
            // CellularFR masqué — voir CellularFrApi.ENABLED
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
        val signalQuestOperatorKey = OperatorColors.keyFor(signalQuestOperator)
            ?: OperatorColors.keyFor(operator)

        val response = SignalQuestClient.api.getSitePhotos(
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
                val photoOperator = photo.operator
                val imageUrl = photo.imageUrl.takeIf { it.isNotBlank() }
                val thumbnailUrl = photo.thumbnailUrl?.takeIf { it.isNotBlank() }
                val stableId = photo.id?.takeIf { it.isNotBlank() }
                    ?: imageUrl
                    ?: thumbnailUrl
                    ?: return@mapNotNull null

                SourcePhoto(
                    sourceId = CommunityDataPreferences.SOURCE_SIGNALQUEST,
                    stableId = stableId,
                    imageUrl = imageUrl,
                    thumbnailUrl = thumbnailUrl,
                    operatorKey = SignalQuestOperators.operatorParamFor(photoOperator)
                        ?.let { OperatorColors.keyFor(it) }
                        ?: OperatorColors.keyFor(photoOperator)
                        ?: signalQuestOperatorKey
                )
            }
            .toList()
    }

    private suspend fun cellularFrPhotos(siteId: String): List<SourcePhoto> {
        return CellularFrApi.getCellularFrPhotos(siteId)
            .mapNotNull { photo ->
                val url = photo.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SourcePhoto(
                    sourceId = CommunityDataPreferences.SOURCE_CELLULARFR,
                    stableId = url,
                    imageUrl = url,
                    thumbnailUrl = null,
                    operatorKey = null
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
                val favoriteId = favoritePhotoIdForSource(
                    prefs = prefs,
                    siteId = siteId,
                    source = source,
                    operatorKey = operatorKey
                ).orEmpty()
                "${source.id}:${if (enabled) 1 else 0}:${if (fallback) 1 else 0}:$favoriteId"
            }
    }

    private fun favoritePhotoIdForSource(
        prefs: SharedPreferences,
        siteId: String,
        source: CommunityDataSource,
        operatorKey: String
    ): String? {
        if (source.id != CommunityDataPreferences.SOURCE_SIGNALQUEST) {
            return favoriteIdForBucket(prefs, siteId, source.id)
        }

        return favoriteIdForBucket(prefs, siteId, signalQuestFavoriteBucketId(operatorKey))
            ?: favoriteIdForBucket(prefs, siteId, CommunityDataPreferences.SOURCE_SIGNALQUEST)
    }

    private fun favoriteIdForBucket(
        prefs: SharedPreferences,
        siteId: String,
        bucketId: String?
    ): String? {
        return bucketId?.let { id ->
            CommunityDataPreferences.favoritePhotoIdForSource(prefs, siteId, id)
        }
    }

    private fun signalQuestFavoriteBucketId(operatorKey: String): String {
        return "${CommunityDataPreferences.SOURCE_SIGNALQUEST}_$operatorKey"
    }

    private fun signalQuestOperatorOrder(defaultOperator: String): List<String> {
        val defaultSignalQuestKey = SignalQuestOperators.operatorParamFor(defaultOperator)
            ?.let { OperatorColors.keyFor(it) }
            ?: OperatorColors.keyFor(defaultOperator)

        return (listOfNotNull(defaultSignalQuestKey) + listOf(
            OperatorColors.ORANGE_KEY,
            OperatorColors.BOUYGUES_KEY,
            OperatorColors.SFR_KEY,
            OperatorColors.FREE_KEY
        )).distinct()
    }

    private fun signalQuestOperatorRank(operatorKey: String?, order: List<String>): Int {
        val key = operatorKey ?: return 99
        val index = order.indexOf(key)
        return if (index >= 0) index else 99
    }

    private data class SourcePhoto(
        val sourceId: String,
        val stableId: String,
        val imageUrl: String?,
        val thumbnailUrl: String?,
        val operatorKey: String?
    ) {
        fun favoriteBucketId(): String? {
            return if (sourceId == CommunityDataPreferences.SOURCE_SIGNALQUEST) {
                val key = operatorKey?.takeIf { it.isNotBlank() } ?: return sourceId
                "${CommunityDataPreferences.SOURCE_SIGNALQUEST}_$key"
            } else {
                sourceId
            }
        }

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
