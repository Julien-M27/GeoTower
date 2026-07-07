package fr.geotower.data.community

import android.content.SharedPreferences
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.utils.OperatorColors
import java.util.Locale

data class CommunityDataSource(
    val id: String,
    val label: String,
    val legacyPrefKey: String? = null
)

data class CommunityDataFeature(
    val id: String,
    val label: String,
    val sources: List<CommunityDataSource>
)

data class CommunityDataOperator(
    val key: String,
    val label: String,
    val features: List<CommunityDataFeature>
)

object CommunityDataPreferences {
    const val FEATURE_PHOTOS = "photos"
    const val FEATURE_SPEEDTEST = "speedtest"
    const val SOURCE_SIGNALQUEST = "signalquest"
    const val SOURCE_CELLULARFR = "cellularfr"

    private const val LEGACY_CELLULARFR_PHOTOS_KEY = "site_show_cellularfr_photos"
    private const val LEGACY_SIGNALQUEST_PHOTOS_KEY = "site_show_signalquest_photos"
    private const val SITE_PHOTO_FAVORITE_KEY_PREFIX = "site_photo_favorite_"

    private val signalQuestPhotos = CommunityDataSource(
        id = SOURCE_SIGNALQUEST,
        label = "SignalQuest",
        legacyPrefKey = LEGACY_SIGNALQUEST_PHOTOS_KEY
    )
    private val cellularFrPhotos = CommunityDataSource(
        id = SOURCE_CELLULARFR,
        label = "CellularFR",
        legacyPrefKey = LEGACY_CELLULARFR_PHOTOS_KEY
    )
    private val signalQuestSpeedtest = CommunityDataSource(
        id = SOURCE_SIGNALQUEST,
        label = "SignalQuest"
    )

    private fun photosFeature(sources: List<CommunityDataSource>) = CommunityDataFeature(
        id = FEATURE_PHOTOS,
        label = "Photos",
        sources = sources
    )

    private fun speedtestFeature() = CommunityDataFeature(
        id = FEATURE_SPEEDTEST,
        label = "Speedtest",
        sources = listOf(signalQuestSpeedtest)
    )

    val operators: List<CommunityDataOperator> = listOf(
        communityOperator(
            key = OperatorColors.ORANGE_KEY,
            label = "Orange (tous Orange)",
            // CellularFR masqué — voir CellularFrApi.ENABLED
            photoSources = listOf(cellularFrPhotos, signalQuestPhotos)
        ),
        communityOperator(OperatorColors.BOUYGUES_KEY),
        communityOperator(OperatorColors.SFR_KEY),
        communityOperator(OperatorColors.FREE_KEY),
        communityOperator(OperatorColors.OUTREMER_TELECOM_KEY, label = "Outremer Telecom / SFR Caraibe"),
        communityOperator(OperatorColors.SRR_KEY),
        communityOperator(OperatorColors.FREE_CARAIBE_KEY),
        communityOperator(OperatorColors.TELCO_OI_KEY)
    )

    private fun communityOperator(
        key: String,
        label: String = OperatorColors.specForKey(key)?.label ?: key,
        photoSources: List<CommunityDataSource> = listOf(signalQuestPhotos)
    ) = CommunityDataOperator(
        key = key,
        label = label,
        features = listOf(photosFeature(photoSources), speedtestFeature())
    )

    fun prefKey(operatorKey: String, featureId: String, sourceId: String): String {
        return "community_${operatorKey.lowercase(Locale.US)}_${featureId}_${sourceId}"
    }

    fun photosEnabledPrefKey(operatorKey: String): String {
        return "community_${operatorKey.lowercase(Locale.US)}_${FEATURE_PHOTOS}_enabled"
    }

    fun sourceOrderPrefKey(operatorKey: String, featureId: String): String {
        return "community_${operatorKey.lowercase(Locale.US)}_${featureId}_source_order"
    }

    fun sourceFallbackPrefKey(operatorKey: String, featureId: String, sourceId: String): String {
        return "community_${operatorKey.lowercase(Locale.US)}_${featureId}_${sourceId}_fallback"
    }

    fun sitePhotoFavoritePrefKey(siteId: String, sourceId: String): String {
        return "$SITE_PHOTO_FAVORITE_KEY_PREFIX${siteId.trim()}_${sourceId.lowercase(Locale.US)}"
    }

    fun operatorKeyFor(rawOperator: String?): String? {
        val key = OperatorColors.keyFor(rawOperator)
        return key?.takeIf { candidate -> operators.any { it.key == candidate } }
    }

    fun orderedOperators(defaultOperator: String?): List<CommunityDataOperator> {
        val defaultKey = operatorKeyFor(defaultOperator) ?: return operators
        val defaultCommunityOperator = operators.firstOrNull { it.key == defaultKey } ?: return operators
        return listOf(defaultCommunityOperator) + operators.filterNot { it.key == defaultKey }
    }

    fun isPhotosEnabled(prefs: SharedPreferences, operatorKey: String): Boolean {
        return prefs.getBoolean(photosEnabledPrefKey(operatorKey), true)
    }

    fun setPhotosEnabled(prefs: SharedPreferences, operatorKey: String, enabled: Boolean) {
        prefs.edit().putBoolean(photosEnabledPrefKey(operatorKey), enabled).apply()
    }

    fun orderedSources(
        prefs: SharedPreferences,
        operatorKey: String,
        feature: CommunityDataFeature
    ): List<CommunityDataSource> {
        val availableSources = feature.sources.filter { source ->
            RemoteFeatureFlags.config.value.isCommunitySourceEnabled(feature.id, source.id)
        }
        val sourcesById = availableSources.associateBy { it.id }
        val savedIds = prefs.getString(sourceOrderPrefKey(operatorKey, feature.id), null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it in sourcesById }
            ?.distinct()
            .orEmpty()
        val orderedIds = savedIds + availableSources.map { it.id }.filterNot { it in savedIds }
        return orderedIds.mapNotNull { sourcesById[it] }
    }

    fun setSourceOrder(
        prefs: SharedPreferences,
        operatorKey: String,
        featureId: String,
        sourceIds: List<String>
    ) {
        prefs.edit()
            .putString(sourceOrderPrefKey(operatorKey, featureId), sourceIds.distinct().joinToString(","))
            .apply()
    }

    fun isSourceFallbackOnly(
        prefs: SharedPreferences,
        operatorKey: String,
        featureId: String,
        sourceId: String
    ): Boolean {
        return prefs.getBoolean(sourceFallbackPrefKey(operatorKey, featureId, sourceId), false)
    }

    fun setSourceFallbackOnly(
        prefs: SharedPreferences,
        operatorKey: String,
        featureId: String,
        sourceId: String,
        fallbackOnly: Boolean
    ) {
        prefs.edit()
            .putBoolean(sourceFallbackPrefKey(operatorKey, featureId, sourceId), fallbackOnly)
            .apply()
    }

    fun favoritePhotoIdForSource(
        prefs: SharedPreferences,
        siteId: String?,
        sourceId: String
    ): String? {
        val normalizedSiteId = siteId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return prefs.getString(sitePhotoFavoritePrefKey(normalizedSiteId, sourceId), null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setFavoritePhotoIdForSource(
        prefs: SharedPreferences,
        siteId: String,
        sourceId: String,
        photoId: String?
    ) {
        val normalizedSiteId = siteId.trim()
        if (normalizedSiteId.isBlank()) return

        val editor = prefs.edit()
        if (photoId.isNullOrBlank()) {
            editor.remove(sitePhotoFavoritePrefKey(normalizedSiteId, sourceId))
        } else {
            editor.putString(sitePhotoFavoritePrefKey(normalizedSiteId, sourceId), photoId)
        }
        editor.apply()
    }

    /**
     * Une photo favorite enregistrée : le site (id_support ou id_anfr en repli), le « bucket »
     * (ex. "signalquest_orange" ou "cellularfr") et l'identifiant stable de la photo favorite.
     */
    data class FavoritePhotoEntry(
        val siteId: String,
        val bucketId: String,
        val photoId: String
    )

    /**
     * Énumère toutes les photos favorites enregistrées, tous sites et opérateurs confondus.
     * Le parsing reste ici pour rester cohérent avec [sitePhotoFavoritePrefKey] : la clé vaut
     * `site_photo_favorite_{siteId}_{bucketId}`, où `siteId` est numérique (sans underscore),
     * on coupe donc sur le premier underscore suivant le préfixe.
     */
    fun favoritePhotoEntries(prefs: SharedPreferences): List<FavoritePhotoEntry> {
        return prefs.all.entries
            .asSequence()
            .filter { it.key.startsWith(SITE_PHOTO_FAVORITE_KEY_PREFIX) }
            .mapNotNull { entry ->
                val photoId = (entry.value as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val remainder = entry.key.removePrefix(SITE_PHOTO_FAVORITE_KEY_PREFIX)
                val separator = remainder.indexOf('_')
                if (separator <= 0 || separator >= remainder.lastIndex) return@mapNotNull null
                FavoritePhotoEntry(
                    siteId = remainder.substring(0, separator),
                    bucketId = remainder.substring(separator + 1),
                    photoId = photoId
                )
            }
            .toList()
    }

    /** Source de base ("signalquest" ou "cellularfr") derrière un bucket de favori. */
    fun favoriteBucketBaseSourceId(bucketId: String): String {
        return if (bucketId.startsWith(SOURCE_CELLULARFR)) SOURCE_CELLULARFR else SOURCE_SIGNALQUEST
    }

    /** Clé opérateur encodée dans un bucket SignalQuest (ex. "signalquest_orange" -> "orange"), sinon null. */
    fun favoriteBucketOperatorKey(bucketId: String): String? {
        if (!bucketId.startsWith(SOURCE_SIGNALQUEST)) return null
        return bucketId.removePrefix(SOURCE_SIGNALQUEST)
            .removePrefix("_")
            .takeIf { it.isNotBlank() }
    }

    fun orderedPhotoSourceIdsForOperatorKeys(
        prefs: SharedPreferences,
        operatorKeys: Iterable<String>
    ): List<String> {
        val supportedKeys = supportedOperatorKeys(operatorKeys)
        val preferredKey = preferredPhotoOperatorKey(supportedKeys)
        val preferredFeature = preferredKey?.let { key ->
            operators.firstOrNull { it.key == key }
                ?.features
                ?.firstOrNull { it.id == FEATURE_PHOTOS }
        }
        val preferredOrder = if (preferredKey != null && preferredFeature != null) {
            orderedSources(prefs, preferredKey, preferredFeature).map { it.id }
        } else {
            emptyList()
        }
        val knownPhotoSources = operators
            .flatMap { operator -> operator.features.filter { it.id == FEATURE_PHOTOS } }
            .flatMap { it.sources }
            .map { it.id }
            .distinct()
        return preferredOrder + knownPhotoSources.filterNot { it in preferredOrder }
    }

    fun isPhotoSourceFallbackOnlyForOperatorKeys(
        prefs: SharedPreferences,
        operatorKeys: Iterable<String>,
        sourceId: String
    ): Boolean {
        val preferredKey = preferredPhotoOperatorKey(supportedOperatorKeys(operatorKeys)) ?: return false
        return sourceFor(preferredKey, FEATURE_PHOTOS, sourceId) != null &&
            isSourceFallbackOnly(prefs, preferredKey, FEATURE_PHOTOS, sourceId)
    }

    fun sourceIdForCommunityName(communityName: String?): String? {
        val normalized = communityName?.lowercase(Locale.US).orEmpty()
        return when {
            normalized.contains("cellular") -> SOURCE_CELLULARFR
            normalized.contains("signal") -> SOURCE_SIGNALQUEST
            else -> null
        }
    }

    fun isEnabled(
        prefs: SharedPreferences,
        operatorKey: String,
        featureId: String,
        sourceId: String
    ): Boolean {
        val source = sourceFor(operatorKey, featureId, sourceId) ?: return false
        if (!RemoteFeatureFlags.config.value.isCommunitySourceEnabled(featureId, sourceId)) return false
        if (featureId == FEATURE_PHOTOS && !isPhotosEnabled(prefs, operatorKey)) return false
        val key = prefKey(operatorKey, featureId, sourceId)
        return if (prefs.contains(key)) {
            prefs.getBoolean(key, true)
        } else {
            source.legacyPrefKey?.let { legacyKey -> prefs.getBoolean(legacyKey, true) } ?: true
        }
    }

    fun setEnabled(
        prefs: SharedPreferences,
        operatorKey: String,
        featureId: String,
        sourceId: String,
        enabled: Boolean
    ) {
        prefs.edit().putBoolean(prefKey(operatorKey, featureId, sourceId), enabled).apply()
    }

    fun reset(prefs: SharedPreferences) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(SITE_PHOTO_FAVORITE_KEY_PREFIX) }
            .forEach { editor.remove(it) }
        operators.forEach { operator ->
            editor.putBoolean(photosEnabledPrefKey(operator.key), true)
            operator.features.forEach { feature ->
                editor.putString(
                    sourceOrderPrefKey(operator.key, feature.id),
                    feature.sources.joinToString(",") { it.id }
                )
                feature.sources.forEach { source ->
                    editor.putBoolean(sourceFallbackPrefKey(operator.key, feature.id, source.id), false)
                    editor.putBoolean(prefKey(operator.key, feature.id, source.id), true)
                }
            }
        }
        editor
            .putBoolean(LEGACY_CELLULARFR_PHOTOS_KEY, true)
            .putBoolean(LEGACY_SIGNALQUEST_PHOTOS_KEY, true)
            .apply()
    }

    fun isPhotoSourceEnabled(prefs: SharedPreferences, rawOperator: String?, sourceId: String): Boolean {
        val operatorKey = operatorKeyFor(rawOperator) ?: return false
        return isEnabled(prefs, operatorKey, FEATURE_PHOTOS, sourceId)
    }

    fun isPhotoSourceEnabledForAny(
        prefs: SharedPreferences,
        rawOperators: Iterable<String?>,
        sourceId: String
    ): Boolean {
        return rawOperators.any { rawOperator -> isPhotoSourceEnabled(prefs, rawOperator, sourceId) }
    }

    fun isSignalQuestPhotosEnabled(prefs: SharedPreferences, rawOperator: String?): Boolean {
        return isPhotoSourceEnabled(prefs, rawOperator, SOURCE_SIGNALQUEST)
    }

    fun isCellularFrPhotosEnabled(prefs: SharedPreferences, rawOperator: String?): Boolean {
        return isPhotoSourceEnabled(prefs, rawOperator, SOURCE_CELLULARFR)
    }

    fun isSignalQuestSpeedtestEnabled(prefs: SharedPreferences, rawOperator: String?): Boolean {
        val operatorKey = operatorKeyFor(rawOperator) ?: return false
        return isEnabled(prefs, operatorKey, FEATURE_SPEEDTEST, SOURCE_SIGNALQUEST)
    }

    private fun sourceFor(
        operatorKey: String,
        featureId: String,
        sourceId: String
    ): CommunityDataSource? {
        if (!RemoteFeatureFlags.config.value.isCommunitySourceEnabled(featureId, sourceId)) return null
        return operators
            .firstOrNull { it.key == operatorKey }
            ?.features
            ?.firstOrNull { it.id == featureId }
            ?.sources
            ?.firstOrNull { it.id == sourceId }
    }

    private fun supportedOperatorKeys(operatorKeys: Iterable<String>): List<String> {
        return operatorKeys
            .filter { key -> operators.any { it.key == key } }
            .distinct()
    }

    private fun preferredPhotoOperatorKey(operatorKeys: List<String>): String? {
        return operatorKeys.firstOrNull { key ->
            sourceFor(key, FEATURE_PHOTOS, SOURCE_CELLULARFR) != null &&
                sourceFor(key, FEATURE_PHOTOS, SOURCE_SIGNALQUEST) != null
        } ?: operatorKeys.firstOrNull()
    }
}
