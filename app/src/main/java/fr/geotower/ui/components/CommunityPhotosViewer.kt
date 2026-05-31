package fr.geotower.ui.screens.emitters

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.components.SharedMiniMapCard
import fr.geotower.utils.AppConfig
import fr.geotower.utils.LocalizedDateLabels
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.isNetworkAvailable
import fr.geotower.data.api.SignalQuestOperators
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.ui.res.painterResource // 🚨 Pour régler "painterResource"
import fr.geotower.R // 🚨 Pour régler "R" (le lien vers vos dossiers res/)
import androidx.compose.foundation.Image
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.data.config.RemoteFeatureFlags
import kotlin.math.roundToInt

// Modèle de données unifié
data class CommunityPhoto(
    val url: String,
    val communityName: String,
    val author: String? = null,
    val date: String? = null,
    val exifMetadata: Map<String, Any?>? = null,
    val sourceId: String? = null,
    val stableId: String = url,
    val operatorKey: String? = null,
    val operatorLabel: String? = null
)

private val favoritePhotoColor = Color(0xFFE53935)
private const val PHOTO_VIEWER_MIN_SCALE = 1f
private const val PHOTO_VIEWER_MAX_SCALE = 5f
private const val PHOTO_VIEWER_DOUBLE_TAP_SCALE = 2.6f
private const val PHOTO_VIEWER_ZOOMED_THRESHOLD = 1.01f
private const val PHOTO_VIEWER_RESET_THRESHOLD = 1.03f

private fun CommunityPhoto.resolvedSourceId(): String? {
    return sourceId ?: CommunityDataPreferences.sourceIdForCommunityName(communityName)
}

private fun CommunityPhoto.favoriteBucketId(): String? {
    val resolvedSourceId = resolvedSourceId() ?: return null
    return if (resolvedSourceId == CommunityDataPreferences.SOURCE_SIGNALQUEST) {
        val key = operatorKey?.takeIf { it.isNotBlank() } ?: return resolvedSourceId
        "${CommunityDataPreferences.SOURCE_SIGNALQUEST}_$key"
    } else {
        resolvedSourceId
    }
}

private fun signalQuestOperatorOrder(defaultOperator: String?): List<String> {
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

private fun communityPhotoSourceLabel(sourceId: String): String? {
    return when (sourceId) {
        CommunityDataPreferences.SOURCE_CELLULARFR -> "CellularFR"
        CommunityDataPreferences.SOURCE_SIGNALQUEST -> "Signal Quest"
        else -> null
    }
}

private fun CommunityPhoto.operatorDisplayLabel(): String? {
    return operatorLabel
        ?.takeIf { it.isNotBlank() }
        ?: operatorKey?.let { key -> OperatorColors.specForKey(key)?.label ?: key }
}

private fun CommunityPhoto.displaySourceLabel(photoSourceOrder: List<String>): String {
    val resolvedSourceId = resolvedSourceId()
    val sourceLabel = resolvedSourceId
        ?.let(::communityPhotoSourceLabel)
        ?: communityName.takeIf { it.isNotBlank() }
        ?: communityPhotoSourcesLabel(listOf(this), photoSourceOrder)

    return if (resolvedSourceId == CommunityDataPreferences.SOURCE_SIGNALQUEST) {
        operatorDisplayLabel()?.let { label -> "$sourceLabel - $label" } ?: sourceLabel
    } else {
        sourceLabel
    }
}

private fun communityPhotoSourcesLabel(
    photos: List<CommunityPhoto>,
    photoSourceOrder: List<String>
): String {
    val sourceIds = photos
        .mapNotNull { photo -> photo.resolvedSourceId() }
        .distinct()
    val orderedSourceIds = photoSourceOrder.filter { sourceId -> sourceId in sourceIds } +
        sourceIds.filterNot { sourceId -> sourceId in photoSourceOrder }
    val sourceLabels = orderedSourceIds.mapNotNull(::communityPhotoSourceLabel)
    return sourceLabels
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" + ")
        ?: photos.firstOrNull()?.communityName.orEmpty()
}

private data class PhotoExifCoordinate(
    val latitude: Double,
    val longitude: Double
)

private data class PhotoExifDisplayItem(
    val key: String,
    val value: String
)

private val alwaysHiddenExifDisplayKeys = setOf(
    "takenMonth",
    "orientationDegrees"
)

private val exifPreferredDisplayOrder = listOf(
    "cameraModel",
    "distanceToSiteMeters",
    "takenDate",
    "takenDateLabel",
    "takenMonthLabel",
    "gpsImgDirectionDegrees",
    "gpsLatitude",
    "gpsLongitude",
    "latitude",
    "longitude",
    "lat",
    "lon",
    "lng"
)

private data class DisplayedPhotoFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

private fun fittedPhotoFrame(containerSize: IntSize, sourceSize: IntSize?): DisplayedPhotoFrame {
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()

    if (
        containerWidth <= 0f ||
        containerHeight <= 0f ||
        sourceSize == null ||
        sourceSize.width <= 0 ||
        sourceSize.height <= 0
    ) {
        return DisplayedPhotoFrame(0f, 0f, containerWidth, containerHeight)
    }

    val containerRatio = containerWidth / containerHeight
    val sourceRatio = sourceSize.width.toFloat() / sourceSize.height.toFloat()
    val fittedWidth: Float
    val fittedHeight: Float

    if (sourceRatio > containerRatio) {
        fittedWidth = containerWidth
        fittedHeight = containerWidth / sourceRatio
    } else {
        fittedHeight = containerHeight
        fittedWidth = containerHeight * sourceRatio
    }

    return DisplayedPhotoFrame(
        left = (containerWidth - fittedWidth) / 2f,
        top = (containerHeight - fittedHeight) / 2f,
        width = fittedWidth,
        height = fittedHeight
    )
}

private fun clampedPhotoOffset(
    offset: Offset,
    scale: Float,
    containerSize: IntSize,
    sourceSize: IntSize?
): Offset {
    if (
        scale <= PHOTO_VIEWER_ZOOMED_THRESHOLD ||
        containerSize.width <= 0 ||
        containerSize.height <= 0
    ) {
        return Offset.Zero
    }

    val frame = fittedPhotoFrame(containerSize, sourceSize)
    val maxX = maxOf(0f, (frame.width * scale - containerSize.width) / 2f)
    val maxY = maxOf(0f, (frame.height * scale - containerSize.height) / 2f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun normalizedSupportText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
}

private fun isRteOwner(owner: String?): Boolean {
    val ownerTokens = normalizedSupportText(owner)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    return "rte" in ownerTokens
}

private fun isTubularPylon(supportNature: String?): Boolean {
    val normalizedNature = normalizedSupportText(supportNature)
    return normalizedNature.contains("tubulaire")
}

fun formatPhotoDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank() || isoDate == "null") return ""
    return try {
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE)
        val date = inputFormat.parse(isoDate)
        if (date != null) outputFormat.format(date) else ""
    } catch (e: Exception) {
        ""
    }
}

private fun CommunityPhoto.hasExifInfo(): Boolean {
    return exifDisplayKeys(exifMetadata).any { key -> exifMetadata?.get(key).hasVisibleExifValue() } ||
        exifCoordinate(exifMetadata) != null
}

private fun exifDisplayKeys(metadata: Map<String, Any?>?): List<String> {
    if (metadata.isNullOrEmpty()) return emptyList()
    val hiddenKeys = alwaysHiddenExifDisplayKeys + when {
        metadata.hasVisibleExifValue("takenDate") -> setOf("takenDateLabel", "takenMonthLabel")
        metadata.hasVisibleExifValue("takenDateLabel") -> setOf("takenMonthLabel")
        else -> emptySet()
    }
    return exifPreferredDisplayOrder
        .filter { it in metadata && it !in hiddenKeys } +
        metadata.keys
            .filterNot { it in exifPreferredDisplayOrder || it in hiddenKeys }
            .sorted()
}

private fun Map<String, Any?>.hasVisibleExifValue(key: String): Boolean {
    return this[key].hasVisibleExifValue()
}

private fun Any?.hasVisibleExifValue(): Boolean {
    val cleanValue = this?.toString()?.trim()
    return !cleanValue.isNullOrBlank() && cleanValue != "null"
}

@Composable
private fun exifDisplayItems(metadata: Map<String, Any?>?): List<PhotoExifDisplayItem> {
    if (metadata.isNullOrEmpty()) return emptyList()
    return exifDisplayKeys(metadata).mapNotNull { key ->
        val value = metadata[key]
        val formattedValue = formatExifValue(key, value) ?: return@mapNotNull null
        PhotoExifDisplayItem(key = key, value = formattedValue)
    }
}

private fun exifCoordinate(metadata: Map<String, Any?>?): PhotoExifCoordinate? {
    if (metadata.isNullOrEmpty()) return null

    fun nestedCoordinate(key: String): PhotoExifCoordinate? {
        val nested = metadata[key].asMapOrNull() ?: return null
        val lat = firstExifDouble(nested, "latitude", "lat", "gpsLatitude")
        val lon = firstExifDouble(nested, "longitude", "lng", "lon", "gpsLongitude")
        return validExifCoordinate(lat, lon)
    }

    nestedCoordinate("coordinates")?.let { return it }
    nestedCoordinate("gps")?.let { return it }
    nestedCoordinate("gpsCoordinates")?.let { return it }
    nestedCoordinate("location")?.let { return it }

    val lat = firstExifDouble(metadata, "gpsLatitude", "latitude", "lat")
    val lon = firstExifDouble(metadata, "gpsLongitude", "longitude", "lng", "lon")
    return validExifCoordinate(lat, lon)
}

private fun firstExifDouble(metadata: Map<*, *>, vararg keys: String): Double? {
    return keys.firstNotNullOfOrNull { key -> metadata[key].asDoubleOrNull() }
}

private fun validExifCoordinate(latitude: Double?, longitude: Double?): PhotoExifCoordinate? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return if (lat in -90.0..90.0 && lon in -180.0..180.0) {
        PhotoExifCoordinate(lat, lon)
    } else {
        null
    }
}

private fun Any?.asMapOrNull(): Map<*, *>? = this as? Map<*, *>

private fun Any?.asDoubleOrNull(): Double? {
    return when (this) {
        is Number -> toDouble()
        is String -> replace(',', '.').toDoubleOrNull()
        else -> null
    }
}

@Composable
private fun formatExifValue(key: String, value: Any?): String? {
    val context = LocalContext.current
    val cleanValue = value?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
    val number = value.asDoubleOrNull()
    return when (key) {
        "cameraModel" -> formatCameraModel(cleanValue)
        "takenDate",
        "takenDateLabel" -> LocalizedDateLabels.formatPhotoExifDate(context, cleanValue)
        "takenMonthLabel" -> LocalizedDateLabels.formatPhotoExifMonth(context, cleanValue)
        "distanceToSiteMeters" -> number?.let { formatExifDistanceMeters(it) } ?: cleanValue
        "gpsImgDirectionDegrees",
        "orientationDegrees" -> number?.let { "${formatDecimal(it, maximumFractionDigits = 0)} deg" } ?: cleanValue
        "gpsLatitude",
        "gpsLongitude",
        "latitude",
        "longitude",
        "lat",
        "lon",
        "lng" -> number?.let { formatDecimal(it, maximumFractionDigits = 6) } ?: cleanValue
        else -> cleanValue
    }
}

@Composable
private fun photoExifLabel(key: String): String = when (key) {
    "cameraModel" -> stringResource(R.string.photo_exif_label_camera_model)
    "distanceToSiteMeters" -> stringResource(R.string.photo_exif_label_distance_to_site)
    "takenDate", "takenDateLabel", "takenMonthLabel" -> stringResource(R.string.photo_exif_label_capture_date)
    "takenMonth" -> stringResource(R.string.photo_exif_label_month)
    "gpsImgDirectionDegrees" -> stringResource(R.string.photo_exif_label_gps_direction)
    "orientationDegrees" -> stringResource(R.string.photo_exif_label_orientation)
    "gpsLatitude", "latitude", "lat" -> stringResource(R.string.photo_exif_label_gps_latitude)
    "gpsLongitude", "longitude", "lng", "lon" -> stringResource(R.string.photo_exif_label_gps_longitude)
    else -> key.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replaceFirstChar { it.uppercase() }
}

private fun formatCameraModel(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s+"), " ")
        .split(" ")
        .joinToString(" ") { token -> formatCameraModelToken(token) }
}

private fun formatCameraModelToken(token: String): String {
    if (token.isBlank()) return token
    if (token.matches(Regex("\\d+g", RegexOption.IGNORE_CASE))) return token.uppercase(Locale.getDefault())
    return token.replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
    }
}

private fun formatDecimal(value: Double, maximumFractionDigits: Int): String {
    return NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        minimumFractionDigits = 0
        this.maximumFractionDigits = maximumFractionDigits
    }.format(value)
}

private fun formatExifDistanceMeters(valueMeters: Double): String {
    return if (AppConfig.distanceUnit.intValue == 1) {
        val miles = valueMeters / 1609.344
        if (miles >= 0.1) {
            String.format(Locale.US, "%.2f mi", miles)
        } else {
            "${(valueMeters * 3.28084).roundToInt()} ft"
        }
    } else {
        if (valueMeters >= 1000.0) {
            "${formatDecimal(valueMeters / 1000.0, maximumFractionDigits = 2)} km"
        } else {
            "${formatDecimal(valueMeters, maximumFractionDigits = 1)} m"
        }
    }
}

@Composable
fun CommunityPhotosSectionShared(
    photos: List<CommunityPhoto>,
    operatorName: String?,
    operatorNames: List<String?> = listOf(operatorName),
    supportNature: String? = null, // 🚨 AJOUT DE LA NATURE DU SUPPORT
    supportOwner: String? = null,
    bgColor: Color,
    shape: Shape,
    onAddPhotoClick: (() -> Unit)? = null,
    favoriteScopeId: String? = null,
    favoriteSelectionEnabled: Boolean = false
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config

    val dataOperators = operatorNames.ifEmpty { listOf(operatorName) }
    val dataOperatorKeys = remember(dataOperators, AppConfig.defaultOperator.value) {
        val availableKeys = dataOperators
            .mapNotNull { CommunityDataPreferences.operatorKeyFor(it) }
            .distinct()
        val preferredKeys = CommunityDataPreferences
            .orderedOperators(AppConfig.defaultOperator.value)
            .map { it.key }
        preferredKeys.filter { it in availableKeys } + availableKeys.filterNot { it in preferredKeys }
    }
    val showCellularFr = CommunityDataPreferences.isPhotoSourceEnabledForAny(
        prefs,
        dataOperators,
        CommunityDataPreferences.SOURCE_CELLULARFR
    )
    val showSignalQuest = CommunityDataPreferences.isPhotoSourceEnabledForAny(
        prefs,
        dataOperators,
        CommunityDataPreferences.SOURCE_SIGNALQUEST
    )
    val photoSourceOrder = CommunityDataPreferences.orderedPhotoSourceIdsForOperatorKeys(prefs, dataOperatorKeys)
    val photoSourceRank = photoSourceOrder.withIndex().associate { (index, sourceId) -> sourceId to index }
    val signalQuestOperatorOrder = signalQuestOperatorOrder(AppConfig.defaultOperator.value)
    val canSelectFavoritePhoto = favoriteSelectionEnabled && !favoriteScopeId.isNullOrBlank()
    fun loadFavoritePhotoIdsByBucket(): Map<String, String> {
        if (!canSelectFavoritePhoto) return emptyMap()
        val favoritesByBucket = photos
            .mapNotNull { photo -> photo.favoriteBucketId() }
            .distinct()
            .mapNotNull { bucketId ->
                CommunityDataPreferences.favoritePhotoIdForSource(prefs, favoriteScopeId, bucketId)
                    ?.let { favoritePhotoId -> bucketId to favoritePhotoId }
            }
            .toMap()

        val legacySignalQuestFavorite = CommunityDataPreferences.favoritePhotoIdForSource(
            prefs,
            favoriteScopeId,
            CommunityDataPreferences.SOURCE_SIGNALQUEST
        )
        val legacySignalQuestBucket = legacySignalQuestFavorite?.let { favoritePhotoId ->
            photos.firstOrNull { photo ->
                photo.resolvedSourceId() == CommunityDataPreferences.SOURCE_SIGNALQUEST &&
                    photo.stableId == favoritePhotoId
            }?.favoriteBucketId()
        }

        return if (
            legacySignalQuestFavorite != null &&
            legacySignalQuestBucket != null &&
            legacySignalQuestBucket !in favoritesByBucket
        ) {
            favoritesByBucket + (legacySignalQuestBucket to legacySignalQuestFavorite)
        } else {
            favoritesByBucket
        }
    }
    var favoritePhotoIdsByBucket by remember(favoriteScopeId, favoriteSelectionEnabled, photoSourceOrder, photos) {
        mutableStateOf(loadFavoritePhotoIdsByBucket())
    }
    LaunchedEffect(favoriteScopeId, favoriteSelectionEnabled, photoSourceOrder, photos) {
        favoritePhotoIdsByBucket = loadFavoritePhotoIdsByBucket()
    }
    fun isFavoritePhoto(photo: CommunityPhoto): Boolean {
        val bucketId = photo.favoriteBucketId() ?: return false
        return favoritePhotoIdsByBucket[bucketId] == photo.stableId
    }
    fun toggleFavoritePhoto(photo: CommunityPhoto) {
        val scopeId = favoriteScopeId?.trim()?.takeIf { it.isNotBlank() } ?: return
        val bucketId = photo.favoriteBucketId() ?: return
        val nextFavoriteId = if (favoritePhotoIdsByBucket[bucketId] == photo.stableId) null else photo.stableId

        CommunityDataPreferences.setFavoritePhotoIdForSource(
            prefs = prefs,
            siteId = scopeId,
            sourceId = bucketId,
            photoId = nextFavoriteId
        )
        favoritePhotoIdsByBucket = if (nextFavoriteId == null) {
            favoritePhotoIdsByBucket - bucketId
        } else {
            favoritePhotoIdsByBucket + (bucketId to nextFavoriteId)
        }
    }
    val sourceEnabledById = mapOf(
        CommunityDataPreferences.SOURCE_CELLULARFR to showCellularFr,
        CommunityDataPreferences.SOURCE_SIGNALQUEST to showSignalQuest
    )
    val availablePhotoSourceIds = remember(photos) {
        photos.mapNotNull { photo -> photo.resolvedSourceId() }.toSet()
    }
    val fallbackOnlyBySource = photoSourceOrder.associateWith { sourceId ->
        CommunityDataPreferences.isPhotoSourceFallbackOnlyForOperatorKeys(prefs, dataOperatorKeys, sourceId)
    }
    val visiblePhotoSourceIds = photoSourceOrder.filterIndexed { index, sourceId ->
        val isEnabled = sourceEnabledById[sourceId] ?: true
        if (!isEnabled) {
            false
        } else if (fallbackOnlyBySource[sourceId] == true) {
            val previousSourceHasPhotos = photoSourceOrder
                .take(index)
                .any { previousSourceId ->
                    sourceEnabledById[previousSourceId] == true &&
                        previousSourceId in availablePhotoSourceIds
                }
            !previousSourceHasPhotos
        } else {
            true
        }
    }.toSet()

    fun isPhotoEnabledForItsOperator(photo: CommunityPhoto): Boolean {
        val sourceId = photo.resolvedSourceId() ?: return true
        if (sourceId !in visiblePhotoSourceIds) return false

        val photoOperatorKey = photo.operatorKey?.takeIf { it.isNotBlank() } ?: return true
        if (photoOperatorKey !in dataOperatorKeys) return false

        return CommunityDataPreferences.isEnabled(
            prefs = prefs,
            operatorKey = photoOperatorKey,
            featureId = CommunityDataPreferences.FEATURE_PHOTOS,
            sourceId = sourceId
        )
    }

    // FILTRAGE
    val filteredPhotos = remember(
        photos,
        visiblePhotoSourceIds,
        dataOperatorKeys,
        photoSourceOrder,
        favoritePhotoIdsByBucket,
        canSelectFavoritePhoto,
        signalQuestOperatorOrder
    ) {
        photos.filter { photo ->
            isPhotoEnabledForItsOperator(photo)
        }.sortedWith(
            compareBy<CommunityPhoto>(
                { photo ->
                    val sourceId = photo.resolvedSourceId()
                    if (sourceId != null && canSelectFavoritePhoto && isFavoritePhoto(photo)) 0 else 1
                },
                { photo ->
                    photoSourceRank[photo.resolvedSourceId()] ?: 99
                },
                { photo ->
                    if (photo.resolvedSourceId() == CommunityDataPreferences.SOURCE_SIGNALQUEST) {
                        signalQuestOperatorRank(photo.operatorKey, signalQuestOperatorOrder)
                    } else {
                        0
                    }
                }
            )
        )
    }

    // --- SÉCURITÉ : On vérifie bien la liste FILTRÉE ---
    // --- NOUVEAU : On vérifie si l'opérateur est supporté par SignalQuest ---
    val canUpload = SignalQuestOperators.supports(operatorName) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_UPLOAD) &&
            CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, operatorName)

    // ✅ NOUVEAU : On vérifie si on est en ligne en réutilisant ta fonction MapScreen
    val isOnline = isNetworkAvailable(context)

    val isRteTubularPylon = isTubularPylon(supportNature) && isRteOwner(supportOwner)

    // 🚨 NOUVEAU : On choisit la bonne image générique en fonction du mot-clé !
    val placeholderRes = if (AppConfig.siteShowSchemes.value) {
        when {
            isRteTubularPylon -> R.drawable.pylone_electrique
            supportNature != null && (supportNature.contains("chateau", true) || supportNature.contains("château", true)) -> R.drawable.chateau_deau
            supportNature != null && supportNature.contains("autostable", true) -> R.drawable.pylone_autostable
            supportNature != null && supportNature.contains("tubulaire", true) -> R.drawable.pylone_tubulaire
            supportNature != null && (supportNature.contains("haubane", true) || supportNature.contains("haubané", true)) -> R.drawable.pylone_haubane
            supportNature != null && (supportNature.contains("immeuble", true) || supportNature.contains("bâtiment", true) || supportNature.contains("toit", true)) -> R.drawable.immeuble
            supportNature != null && (supportNature.contains("religieux", true) || supportNature.contains("eglise", true) || supportNature.contains("église", true) || supportNature.contains("clocher", true) || supportNature.contains("chapelle", true)) -> R.drawable.monument_religieux
            supportNature != null && supportNature.contains("phare", true) -> R.drawable.phare
            supportNature != null && (supportNature.contains("semaphore", true) || supportNature.contains("sémaphore", true)) -> R.drawable.semaphore
            supportNature != null && supportNature.contains("silo", true) -> R.drawable.silo
            supportNature != null && (supportNature.contains("terrasse", true) || supportNature.contains("toit-terrasse", true)) -> R.drawable.immeuble
            supportNature != null && supportNature.contains("pylône", true) -> R.drawable.pylone_autostable
            else -> null // Si aucun mot-clé ne correspond
        }
    } else null
    val themeMode by AppConfig.themeMode
    val useOneUi = AppConfig.useOneUiDesign
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    val thumbnailShape = if (useOneUi) RoundedCornerShape(16.dp) else RoundedCornerShape(8.dp)
    val badgeShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val pillButtonShape = if (useOneUi) CircleShape else RoundedCornerShape(12.dp)

    val viewerBgBaseColor = if (isDark) Color.Black else Color.White
    val viewerContentColor = if (isDark) Color.White else Color.Black
    val overlayButtonBg = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f)

    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPhotosSnapshot by remember { mutableStateOf<List<CommunityPhoto>>(emptyList()) }
    var exifDialogPhoto by remember { mutableStateOf<CommunityPhoto?>(null) }
    val showPhotoExif = AppConfig.siteShowPhotoExif.value
    LaunchedEffect(showPhotoExif) {
        if (!showPhotoExif) exifDialogPhoto = null
    }
    // 🚨 AJOUT : Variable pour afficher le château d'eau en grand
    var showPlaceholderFullScreen by remember { mutableStateOf(false) }

    // 🚨 NOUVEAU : On gère le titre dynamiquement
    val showSchemaTitle = filteredPhotos.isEmpty() && placeholderRes != null

    val sectionTitle = if (showSchemaTitle) {
        stringResource(R.string.appstrings_support_diagram)
    } else {
        stringResource(R.string.appstrings_site_photos_and_schemes_option)
    }

    // 🚨 NOUVEAU : Si on n'a ni photos, ni schéma, ET qu'on ne peut pas uploader, on masque TOUT !
    if (filteredPhotos.isEmpty() && placeholderRes == null && (!canUpload || onAddPhotoClick == null)) {
        return // On ne dessine absolument rien, le bloc disparaît !
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(sectionTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!isOnline) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (placeholderRes != null) 16.dp else 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.appstrings_community_photos_offline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (placeholderRes != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Image(
                                painter = painterResource(id = placeholderRes),
                                contentDescription = stringResource(R.string.appstrings_support_image_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(thumbnailShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { showPlaceholderFullScreen = true }
                            )
                        }
                    }
                }
            } else {
                // 🌐 LOGIQUE EN LIGNE
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {

                    // 🚨 VÉRIFICATION : EST-CE QUE L'OPÉRATEUR EST FREE ?
                    if (operatorName != null && operatorName.contains("FREE", ignoreCase = true)) {

                        if (filteredPhotos.isNotEmpty()) {
                            itemsIndexed(filteredPhotos) { index, photo ->
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .clickable {
                                            selectedPhotosSnapshot = filteredPhotos
                                            selectedPhotoIndex = index
                                        }
                                ) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = stringResource(R.string.appstrings_site_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                        fallback = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                        placeholder = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        if (filteredPhotos.isNotEmpty() && placeholderRes != null) {
                            item {
                                Box(
                                    modifier = Modifier.height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(80.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }

                        // 1. POUR FREE : ON AFFICHE L'IMAGE (Si on en a trouvé une correspondante)
                        if (placeholderRes != null) {
                            item {
                                Image(
                                    painter = painterResource(id = placeholderRes), // 🪄 L'image s'adapte toute seule !
                                    contentDescription = stringResource(R.string.appstrings_support_image_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { showPlaceholderFullScreen = true }
                                )
                            }
                        }

                        if (canUpload && onAddPhotoClick != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), thumbnailShape)
                                        .clickable { onAddPhotoClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Outbox,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.appstrings_upload_photos_prompt),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                    } else {

                        // 2. POUR LES AUTRES OPÉRATEURS : ON AFFICHE D'ABORD LES VRAIES PHOTOS
                        if (filteredPhotos.isNotEmpty()) {
                            itemsIndexed(filteredPhotos) { index, photo ->
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .clickable {
                                            selectedPhotosSnapshot = filteredPhotos
                                            selectedPhotoIndex = index
                                        }
                                ) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = stringResource(R.string.appstrings_site_photo_desc),
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                        fallback = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                        placeholder = painterResource(id = placeholderRes ?: R.drawable.chateau_deau),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // 🚨 AJOUT : LA BARRE DE SÉPARATION VERTICALE
                        // Elle s'affiche uniquement si on a des vraies photos ET un schéma à montrer
                        if (filteredPhotos.isNotEmpty() && placeholderRes != null) {
                            item {
                                Box(
                                    modifier = Modifier.height(120.dp), // Même hauteur que le carrousel
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp) // Épaisseur de la ligne
                                            .height(80.dp) // Un peu plus petite que les photos pour faire élégant
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }

                        // 3. ENSUITE, ON AFFICHE L'IMAGE GÉNÉRIQUE (Si on en a trouvé une correspondante)
                        if (placeholderRes != null) {
                            item {
                                Image(
                                    painter = painterResource(id = placeholderRes), // 🪄 L'image s'adapte toute seule !
                                    contentDescription = stringResource(R.string.appstrings_support_image_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { showPlaceholderFullScreen = true }
                                )
                            }
                        }

                        // 4. ENFIN, LE BOUTON D'UPLOAD (opérateur supporté par SignalQuest)
                        // Il se mettra en tout dernier
                        if (canUpload && onAddPhotoClick != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(thumbnailShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), thumbnailShape)
                                        .clickable { onAddPhotoClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Outbox,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.appstrings_upload_photos_prompt),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 🚨 DIALOGUE PLEIN ÉCRAN POUR LE SUPPORT GÉNÉRIQUE
    // On l'affiche seulement si on a cliqué ET qu'on a bien une image à montrer
    if (showPlaceholderFullScreen && placeholderRes != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPlaceholderFullScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = viewerBgBaseColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // L'image au centre
                    Image(
                        painter = painterResource(id = placeholderRes), // 🪄 L'image en plein écran s'adapte !
                        contentDescription = stringResource(R.string.appstrings_support_image_full_screen_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { showPlaceholderFullScreen = false })
                            }
                    )

                    // La croix de fermeture
                    IconButton(
                        onClick = { showPlaceholderFullScreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 20.dp, end = 4.dp)
                            .background(overlayButtonBg, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.appstrings_close), tint = viewerContentColor)
                    }
                }
            }
        }
    }

    if (selectedPhotoIndex != null) {
        val viewerPhotos = selectedPhotosSnapshot.ifEmpty { filteredPhotos }
        if (viewerPhotos.isEmpty()) {
            LaunchedEffect(Unit) {
                selectedPhotoIndex = null
                selectedPhotosSnapshot = emptyList()
            }
        } else {
        // --- On utilise filteredPhotos ---
        val initialPhotoIndex = selectedPhotoIndex!!.coerceIn(0, viewerPhotos.lastIndex)
        val pagerState = rememberPagerState(initialPage = initialPhotoIndex, pageCount = { viewerPhotos.size })
        val currentPhoto = viewerPhotos[pagerState.currentPage]
        val currentPhotoSourceId = currentPhoto.resolvedSourceId()
        val currentPhotoSourceLabel = currentPhoto.displaySourceLabel(photoSourceOrder)
        val fullScreenTitle = pluralStringResource(
            R.plurals.community_photos_title,
            viewerPhotos.size,
            currentPhotoSourceLabel.replace(" ", "\u00A0")
        )
        val canFavoriteCurrentPhoto = canSelectFavoritePhoto && currentPhotoSourceId != null
        val isCurrentPhotoFavorite = canSelectFavoritePhoto && isFavoritePhoto(currentPhoto)

        val dismissOffset = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()
        var viewerContainerSize by remember { mutableStateOf(IntSize.Zero) }
        var photoSourceSizes by remember(viewerPhotos) { mutableStateOf<Map<Int, IntSize>>(emptyMap()) }
        var currentPhotoZoomed by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = {
                selectedPhotoIndex = null
                selectedPhotosSnapshot = emptyList()
                coroutineScope.launch { dismissOffset.snapTo(0f) }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val bgAlpha = (1f - (abs(dismissOffset.value) / 800f)).coerceIn(0f, 1f)
            val density = LocalDensity.current
            val currentPhotoSourceSize = photoSourceSizes[pagerState.currentPage]
            val currentPhotoFrame = fittedPhotoFrame(viewerContainerSize, currentPhotoSourceSize)
            val photoStartPadding = with(density) { currentPhotoFrame.left.coerceAtLeast(0f).toDp() + 16.dp }
            val photoEndPadding = with(density) { (viewerContainerSize.width - currentPhotoFrame.right).coerceAtLeast(0f).toDp() + 16.dp }
            val photoTopPadding = with(density) { currentPhotoFrame.top.coerceAtLeast(0f).toDp() + 16.dp }
            val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val photoBottomPadding = navigationBottomPadding + 12.dp

            Surface(modifier = Modifier.fillMaxSize(), color = viewerBgBaseColor.copy(alpha = bgAlpha)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewerContainerSize = it }
                        .graphicsLayer { translationY = dismissOffset.value }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !currentPhotoZoomed
                    ) { page ->
                        var scale by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }
                        var containerSize by remember { mutableStateOf(IntSize.Zero) }

                        val isZoomed = scale > PHOTO_VIEWER_ZOOMED_THRESHOLD

                        fun setZoomState(nextScale: Float, nextOffset: Offset) {
                            scale = nextScale
                            offset = if (nextScale > PHOTO_VIEWER_ZOOMED_THRESHOLD) {
                                clampedPhotoOffset(nextOffset, nextScale, containerSize, photoSourceSizes[page])
                            } else {
                                Offset.Zero
                            }
                            if (pagerState.currentPage == page) {
                                currentPhotoZoomed = nextScale > PHOTO_VIEWER_ZOOMED_THRESHOLD
                            }
                        }

                        LaunchedEffect(pagerState.currentPage) {
                            if (pagerState.currentPage != page) {
                                setZoomState(PHOTO_VIEWER_MIN_SCALE, Offset.Zero)
                            } else {
                                currentPhotoZoomed = isZoomed
                            }
                        }

                        LaunchedEffect(containerSize, photoSourceSizes[page]) {
                            if (isZoomed) {
                                setZoomState(scale, offset)
                            }
                        }

                        val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
                            val previousScale = scale
                            val rawScale = (previousScale * zoomChange).coerceIn(
                                PHOTO_VIEWER_MIN_SCALE,
                                PHOTO_VIEWER_MAX_SCALE
                            )
                            val nextScale = if (rawScale < PHOTO_VIEWER_RESET_THRESHOLD) {
                                PHOTO_VIEWER_MIN_SCALE
                            } else {
                                rawScale
                            }

                            if (nextScale <= PHOTO_VIEWER_ZOOMED_THRESHOLD) {
                                setZoomState(PHOTO_VIEWER_MIN_SCALE, Offset.Zero)
                            } else {
                                val scaleRatio = nextScale / previousScale.coerceAtLeast(PHOTO_VIEWER_MIN_SCALE)
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val centroidFromCenter = if (centroid == Offset.Unspecified) {
                                    Offset.Zero
                                } else {
                                    centroid - center
                                }
                                val transformedOffset =
                                    offset * scaleRatio + centroidFromCenter * (1f - scaleRatio) + panChange
                                setZoomState(nextScale, transformedOffset)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { containerSize = it }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { tapOffset ->
                                            if (scale > PHOTO_VIEWER_ZOOMED_THRESHOLD) {
                                                setZoomState(PHOTO_VIEWER_MIN_SCALE, Offset.Zero)
                                            } else {
                                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                                val tapFromCenter = tapOffset - center
                                                val focusedOffset = tapFromCenter * (1f - PHOTO_VIEWER_DOUBLE_TAP_SCALE)
                                                setZoomState(PHOTO_VIEWER_DOUBLE_TAP_SCALE, focusedOffset)
                                            }
                                        }
                                    )
                                }
                                .pointerInput(isZoomed) {
                                    if (!isZoomed) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (dismissOffset.value > 250f) {
                                                    selectedPhotoIndex = null
                                                    selectedPhotosSnapshot = emptyList()
                                                    coroutineScope.launch { dismissOffset.snapTo(0f) }
                                                } else {
                                                    coroutineScope.launch { dismissOffset.animateTo(0f) }
                                                }
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    val newOffset = (dismissOffset.value + dragAmount).coerceAtLeast(0f)
                                                    dismissOffset.snapTo(newOffset)
                                                }
                                            }
                                        )
                                    }
                                }
                                .transformable(
                                    state = transformState,
                                    canPan = { scale > PHOTO_VIEWER_ZOOMED_THRESHOLD },
                                    lockRotationOnZoomPan = true
                                )
                        ) {
                            // --- On utilise filteredPhotos ---
                            AsyncImage(
                                model = viewerPhotos[page].url,
                                contentDescription = stringResource(R.string.appstrings_full_screen_photo_desc),
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    val drawable = state.result.drawable
                                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                        photoSourceSizes = photoSourceSizes + (page to IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                            )
                        }
                    }

                    // --- FLÈCHE GAUCHE ---
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = photoStartPadding).size(28.dp).background(overlayButtonBg, CircleShape)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.appstrings_previous), tint = viewerContentColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    // --- FLÈCHE DROITE (On utilise filteredPhotos.size) ---
                    if (pagerState.currentPage < viewerPhotos.size - 1) {
                        IconButton(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = photoEndPadding).size(28.dp).background(overlayButtonBg, CircleShape)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.appstrings_next), tint = viewerContentColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    // --- TEXTES EN HAUT ---
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 28.dp, start = 56.dp, end = 56.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fullScreenTitle,
                            color = viewerContentColor,
                            style = MaterialTheme.typography.titleLarge.copy(shadow = if (isDark) androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 8f) else null),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (canFavoriteCurrentPhoto) {
                        PhotoFavoriteButton(
                            isFavorite = isCurrentPhotoFavorite,
                            modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 12.dp),
                            backgroundColor = overlayButtonBg,
                            contentColor = viewerContentColor,
                            onClick = { toggleFavoritePhoto(currentPhoto) }
                        )
                    }

                    IconButton(
                        onClick = {
                            selectedPhotoIndex = null
                            selectedPhotosSnapshot = emptyList()
                            coroutineScope.launch { dismissOffset.snapTo(0f) }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 4.dp).background(overlayButtonBg, shape = CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.appstrings_close), tint = viewerContentColor)
                    }

                    // --- AUTEUR ET DATE ---
                    val hasCurrentPhotoCaption = !currentPhoto.author.isNullOrBlank() || !currentPhoto.date.isNullOrBlank()
                    val hasCurrentPhotoInfo = showPhotoExif && currentPhoto.hasExifInfo() && currentPhotoSourceSize != null
                    if (hasCurrentPhotoCaption || hasCurrentPhotoInfo) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = photoBottomPadding, start = photoStartPadding),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (hasCurrentPhotoInfo) {
                                PhotoInfoButton(
                                    modifier = Modifier,
                                    backgroundColor = overlayButtonBg,
                                    contentColor = viewerContentColor,
                                    onClick = { exifDialogPhoto = currentPhoto }
                                )
                            }
                            if (hasCurrentPhotoCaption) {
                                if (hasCurrentPhotoInfo) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Column(
                                    modifier = Modifier.background(overlayButtonBg, badgeShape).padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    if (!currentPhoto.author.isNullOrBlank() && currentPhoto.author != "null") {
                                        Text(text = stringResource(R.string.photo_by_author, currentPhoto.author), color = viewerContentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                    val formattedDate = formatPhotoDate(currentPhoto.date)
                                    if (formattedDate.isNotEmpty()) {
                                        Text(text = stringResource(R.string.photo_on_date, formattedDate), color = viewerContentColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // --- COMPTEUR (On utilise filteredPhotos.size) ---
                    if (viewerPhotos.size > 1) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = photoBottomPadding, end = photoEndPadding).background(overlayButtonBg, pillButtonShape).padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Collections, contentDescription = null, tint = viewerContentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "${pagerState.currentPage + 1} / ${viewerPhotos.size}", color = viewerContentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- INDICATEURS "PILULE" (On utilise filteredPhotos.size) ---
                    if (viewerPhotos.size > 1) {
                        var containerWidth by remember { mutableStateOf(0) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = photoBottomPadding)
                                .onSizeChanged { containerWidth = it.width }
                                .background(color = if (isDark) Color(0xFF2C2C2C) else Color(0xFFF0F0F0), shape = CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .pointerInput(viewerPhotos.size) {
                                    detectDragGestures { change, _ ->
                                        if (containerWidth > 0) {
                                            val positionX = change.position.x.coerceIn(0f, containerWidth.toFloat())
                                            val progress = positionX / containerWidth.toFloat()
                                            val targetPage = (progress * (viewerPhotos.size - 1)).toInt()
                                            if (targetPage != pagerState.currentPage) {
                                                coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                                            }
                                        }
                                        change.consume()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val maxDots = 5
                                val startDot = (pagerState.currentPage - maxDots / 2).coerceIn(0, maxOf(0, viewerPhotos.size - maxDots))
                                val endDot = minOf(startDot + maxDots, viewerPhotos.size)

                                (startDot until endDot).forEach { iteration ->
                                    val isActive = pagerState.currentPage == iteration
                                    val dotColor = when {
                                        isActive && isDark -> Color.White
                                        isActive && !isDark -> Color(0xFF424242)
                                        !isActive && isDark -> Color.White.copy(alpha = 0.2f)
                                        else -> Color(0xFFC0C0C0)
                                    }

                                    val animatedWidth by animateDpAsState(targetValue = if (isActive) 18.dp else 8.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "pillWidth")

                                    Box(
                                        modifier = Modifier.height(24.dp).width(if (isActive) 24.dp else 12.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(iteration) }
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.size(width = animatedWidth, height = 7.dp).clip(CircleShape).background(dotColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    exifDialogPhoto?.let { photo ->
        PhotoExifDialog(
            photo = photo,
            onDismiss = { exifDialogPhoto = null }
        )
    }
}

@Composable
private fun PhotoFavoriteButton(
    isFavorite: Boolean,
    modifier: Modifier,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(32.dp)
            .background(backgroundColor, CircleShape)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) {
                    R.string.appstrings_photo_favorite_remove_desc
                } else {
                    R.string.appstrings_photo_favorite_set_desc
                }
            ),
            tint = if (isFavorite) favoritePhotoColor else contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PhotoInfoButton(
    modifier: Modifier,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(32.dp)
            .background(backgroundColor, CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = stringResource(R.string.appstrings_photo_exif_info_desc),
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PhotoExifDialog(
    photo: CommunityPhoto,
    onDismiss: () -> Unit
) {
    val items = exifDisplayItems(photo.exifMetadata)
    val coordinate = remember(photo.exifMetadata) { exifCoordinate(photo.exifMetadata) }
    val dialogShape = RoundedCornerShape(18.dp)
    val mapShape = RoundedCornerShape(12.dp)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = dialogShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.appstrings_photo_exif_metadata_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.appstrings_close))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    coordinate?.let { point ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.appstrings_photo_exif_gps_position),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SharedMiniMapCard(
                            modifier = Modifier.fillMaxWidth(),
                            centerLat = point.latitude,
                            centerLon = point.longitude,
                            mappedAntennas = emptyList(),
                            blockShape = mapShape,
                            cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            onMapReady = {},
                            initialZoom = 17.0,
                            allowGestures = true
                        )
                    }

                    if (items.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = photoExifLabel(item.key),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = item.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
