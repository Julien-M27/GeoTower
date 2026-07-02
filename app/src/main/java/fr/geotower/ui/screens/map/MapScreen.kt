package fr.geotower.ui.screens.map

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import android.view.Surface as AndroidSurface
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.data.api.GeoTowerDataCoverage
import fr.geotower.data.api.NominatimApi
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.models.isDeclaredActive
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.ui.components.LiveDatabaseUsageWarningDialog
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.MapDisplayPrefs
import fr.geotower.utils.MapUtils
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.filteredAzimuthsForFrequencySelection
import fr.geotower.utils.isNetworkAvailable
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import androidx.compose.foundation.Canvas as ComposeCanvas
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme
import java.io.File
import java.text.Normalizer
import java.util.Locale
import android.os.Environment
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val HS_OPERATOR_WILDCARD = "*"
private const val INITIAL_LOCATION_ZOOM = 16.0
private const val MOUSE_WHEEL_ZOOM_STEP = 1.0
private const val MOUSE_WHEEL_ZOOM_ANIMATION_MS = 80L
private const val WEB_MERCATOR_WORLD_TILE_SIZE_PX = 256.0
private const val MAP_AZIMUTH_DETAIL_LIMIT = 6000
private const val RADIO_MAP_MARKER_LIMIT = 4500
private const val MAP_RELOAD_DEBOUNCE_MS = 180L
private const val MAP_RELOAD_MIN_ZOOM_DELTA = 0.08
private const val MAP_RELOAD_MIN_VIEWPORT_SHIFT_RATIO = 0.10
private const val MAP_MARKER_REDRAW_DEBOUNCE_MS = 40L
private const val MAP_COMPASS_UPDATE_INTERVAL_MS = 80L
private const val MAP_ACTIVE_FILTER_LIST_LIMIT = 3

private val hsBadgeDrawableCache = android.util.LruCache<Int, BitmapDrawable>(4)
private val hsMarkerIconCache = android.util.LruCache<String, BitmapDrawable>(500)

private data class DeclaredSiteStats(
    val activeCount: Int,
    val totalCount: Int
)

private data class MapViewportSnapshot(
    val zoom: Double,
    val latNorth: Double,
    val lonEast: Double,
    val latSouth: Double,
    val lonWest: Double,
    val centerLat: Double,
    val centerLon: Double
)

private data class SearchAreaBounds(
    val latNorth: Double,
    val lonEast: Double,
    val latSouth: Double,
    val lonWest: Double
)

private fun declaredSiteStats(antennas: List<LocalisationEntity>): DeclaredSiteStats {
    val siteGroups = antennas
        .asSequence()
        .filter { !it.idAnfr.startsWith("CLUSTER_") }
        .groupBy { it.physicalSiteKey() }

    return DeclaredSiteStats(
        activeCount = siteGroups.values.count { siteAntennas -> siteAntennas.any { it.isDeclaredActive() } },
        totalCount = siteGroups.size
    )
}

private fun hasSavedMapPosition(prefs: SharedPreferences): Boolean {
    if (!prefs.contains("last_map_lat") || !prefs.contains("last_map_lon") || !prefs.contains("last_map_zoom")) {
        return false
    }

    val lat = prefs.getFloat("last_map_lat", Float.NaN).toDouble()
    val lon = prefs.getFloat("last_map_lon", Float.NaN).toDouble()
    val zoom = prefs.getFloat("last_map_zoom", Float.NaN).toDouble()

    return lat in -90.0..90.0 &&
        lon in -180.0..180.0 &&
        zoom in 0.0..25.0
}

/**
 * Parse une date ANFR en entier yyyymmdd comparable, ou null si absente/invalide.
 * L'ANFR fournit "JJ/MM/AAAA" (ex: "01/06/2012") ; on accepte aussi l'ISO "AAAA-MM-JJ" par sécurité.
 */
private fun parseServiceDateInt(raw: String?): Int? {
    val s = raw?.trim() ?: return null
    if (s.length < 10) return null
    return when {
        (s[2] == '/' || s[2] == '-') && (s[5] == '/' || s[5] == '-') -> {
            val d = s.substring(0, 2).toIntOrNull() ?: return null
            val m = s.substring(3, 5).toIntOrNull() ?: return null
            val y = s.substring(6, 10).toIntOrNull() ?: return null
            if (m in 1..12 && d in 1..31) y * 10000 + m * 100 + d else null
        }
        s[4] == '-' && s[7] == '-' -> {
            val y = s.substring(0, 4).toIntOrNull() ?: return null
            val m = s.substring(5, 7).toIntOrNull() ?: return null
            val d = s.substring(8, 10).toIntOrNull() ?: return null
            if (m in 1..12 && d in 1..31) y * 10000 + m * 100 + d else null
        }
        else -> null
    }
}

private fun MapView.enableMouseWheelZoom() {
    setOnGenericMotionListener { _, event ->
        if (event.action != MotionEvent.ACTION_SCROLL) return@setOnGenericMotionListener false

        val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (scrollY == 0f) return@setOnGenericMotionListener false

        val zoomDirection = if (scrollY > 0f) 1.0 else -1.0
        val targetZoom = (zoomLevelDouble + zoomDirection * MOUSE_WHEEL_ZOOM_STEP)
            .coerceIn(minZoomLevel, maxZoomLevel)

        controller.stopAnimation(false)
        controller.zoomToFixing(
            targetZoom,
            event.x.roundToInt(),
            event.y.roundToInt(),
            MOUSE_WHEEL_ZOOM_ANIMATION_MS
        )
        true
    }
}

private fun MapView.applyWorldMapBounds() {
    if (isHorizontalMapRepetitionEnabled()) setHorizontalMapRepetitionEnabled(false)
    if (isVerticalMapRepetitionEnabled()) setVerticalMapRepetitionEnabled(false)

    val tileSystem = MapView.getTileSystem()
    setScrollableAreaLimitLatitude(tileSystem.maxLatitude, tileSystem.minLatitude, 0)
    setScrollableAreaLimitLongitude(tileSystem.minLongitude, tileSystem.maxLongitude, 0)

    val mapWidthPx = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val mapHeightPx = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val minZoom = log2((maxOf(mapWidthPx, mapHeightPx).toDouble() / WEB_MERCATOR_WORLD_TILE_SIZE_PX).coerceAtLeast(1.0))
    setMinZoomLevel(minZoom)
    if (zoomLevelDouble < minZoom) {
        controller.stopAnimation(false)
        controller.setZoom(minZoom)
    }
}

private fun MapView.visibleLongitudeBounds(): Pair<Double, Double> {
    val worldWidthPx = WEB_MERCATOR_WORLD_TILE_SIZE_PX * Math.pow(2.0, zoomLevelDouble)
    return if (width > 0 && width.toDouble() >= worldWidthPx) {
        -180.0 to 180.0
    } else {
        boundingBox.lonWest to boundingBox.lonEast
    }
}

private fun MapView.visibleViewportSnapshot(): MapViewportSnapshot {
    val box = boundingBox
    val (lonWest, lonEast) = visibleLongitudeBounds()
    return MapViewportSnapshot(
        zoom = zoomLevelDouble,
        latNorth = box.latNorth,
        lonEast = lonEast,
        latSouth = box.latSouth,
        lonWest = lonWest,
        centerLat = mapCenter.latitude,
        centerLon = mapCenter.longitude
    )
}

private fun longitudeDeltaDegrees(a: Double, b: Double): Double {
    return abs(((a - b + 540.0) % 360.0) - 180.0)
}

private fun longitudeSpanDegrees(lonEast: Double, lonWest: Double): Double {
    val rawSpan = lonEast - lonWest
    return if (rawSpan < 0.0) rawSpan + 360.0 else rawSpan
}

private fun MapViewportSnapshot.isCloseTo(other: MapViewportSnapshot): Boolean {
    if (abs(zoom - other.zoom) >= MAP_RELOAD_MIN_ZOOM_DELTA) return false

    val latSpan = abs(latNorth - latSouth).coerceAtLeast(0.001)
    val lonSpan = longitudeSpanDegrees(lonEast, lonWest).coerceAtLeast(0.001)
    val maxLatShift = latSpan * MAP_RELOAD_MIN_VIEWPORT_SHIFT_RATIO
    val maxLonShift = lonSpan * MAP_RELOAD_MIN_VIEWPORT_SHIFT_RATIO

    return abs(centerLat - other.centerLat) < maxLatShift &&
        longitudeDeltaDegrees(centerLon, other.centerLon) < maxLonShift
}

private fun MapView.loadVisibleAntennas(viewModel: MapViewModel) {
    val snapshot = visibleViewportSnapshot()
    viewModel.loadAntennasInBox(
        snapshot.zoom,
        snapshot.latNorth,
        snapshot.lonEast,
        snapshot.latSouth,
        snapshot.lonWest
    )
}

private fun MapView.loadVisibleSignalQuestCoverage(viewModel: MapViewModel, enabled: Boolean) {
    if (!enabled) {
        viewModel.clearSignalQuestCoveragePoints()
        return
    }

    val snapshot = visibleViewportSnapshot()
    viewModel.loadSignalQuestCoveragePointsInBox(
        snapshot.zoom,
        snapshot.latNorth,
        snapshot.lonEast,
        snapshot.latSouth,
        snapshot.lonWest
    )
}

private fun MapView.clearCityFilterAndReloadVisible(viewModel: MapViewModel) {
    val box = boundingBox
    val (lonWest, lonEast) = visibleLongitudeBounds()
    viewModel.clearCityFilterAndReload(zoomLevelDouble, box.latNorth, lonEast, box.latSouth, lonWest)
}

private fun encodeGeoPointPolygons(polygons: List<List<GeoPoint>>?): String? {
    return polygons?.takeIf { it.isNotEmpty() }?.joinToString("|") { polygon ->
        polygon.joinToString(";") { point -> "${point.latitude},${point.longitude}" }
    }
}

private fun decodeGeoPointPolygons(encoded: String?): List<List<GeoPoint>>? {
    if (encoded.isNullOrBlank()) return null
    return encoded.split("|")
        .mapNotNull { polygonText ->
            val points = polygonText.split(";").mapNotNull { pointText ->
                val parts = pointText.split(",", limit = 2)
                val latitude = parts.getOrNull(0)?.toDoubleOrNull()
                val longitude = parts.getOrNull(1)?.toDoubleOrNull()
                if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
            }
            points.takeIf { it.isNotEmpty() }
        }
        .takeIf { it.isNotEmpty() }
}

private fun encodeSearchAreaBounds(bounds: SearchAreaBounds?): String? {
    return bounds?.let { "${it.latNorth},${it.lonEast},${it.latSouth},${it.lonWest}" }
}

private fun decodeSearchAreaBounds(encoded: String?): SearchAreaBounds? {
    if (encoded.isNullOrBlank()) return null
    val parts = encoded.split(",", limit = 4)
    if (parts.size != 4) return null

    val latNorth = parts[0].toDoubleOrNull()
    val lonEast = parts[1].toDoubleOrNull()
    val latSouth = parts[2].toDoubleOrNull()
    val lonWest = parts[3].toDoubleOrNull()

    return if (latNorth != null && lonEast != null && latSouth != null && lonWest != null) {
        SearchAreaBounds(latNorth, lonEast, latSouth, lonWest)
    } else {
        null
    }
}

private fun encodeGeoPoints(points: List<GeoPoint>): String? {
    return points.takeIf { it.isNotEmpty() }
        ?.joinToString(";") { point -> "${point.latitude},${point.longitude}" }
}

private fun decodeGeoPoints(encoded: String?): List<GeoPoint> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(";").mapNotNull { pointText ->
        val parts = pointText.split(",", limit = 2)
        val latitude = parts.getOrNull(0)?.toDoubleOrNull()
        val longitude = parts.getOrNull(1)?.toDoubleOrNull()
        if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
    }
}

private fun encodeBooleanList(values: List<Boolean>): String? {
    return values.takeIf { it.isNotEmpty() }
        ?.joinToString(",") { value -> if (value) "1" else "0" }
}

private fun decodeBooleanList(encoded: String?): List<Boolean> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(",").map { value -> value == "1" }
}

private fun normalizedAnfrId(value: String): String {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.toString() ?: trimmed
}

private fun extractOperatorKeys(value: String?): List<String> {
    return OperatorColors.keysFor(value)
}

private val operatorSearchSplitRegex = Regex("\\s*(?:[,;/\\u2022]|\\bet\\b|\\+|&|\\|)\\s*", RegexOption.IGNORE_CASE)
private val operatorSearchCombiningMarksRegex = Regex("\\p{Mn}+")
private val operatorSearchNonWordRegex = Regex("[^A-Z0-9]+")
private val operatorSearchRepeatedSpacesRegex = Regex("\\s+")
private val MapControlButtonDiameter = 54.dp
private val MapSearchBarHeight = 54.dp

private fun normalizeOperatorSearchToken(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(operatorSearchCombiningMarksRegex, "")
        .uppercase(Locale.ROOT)
        .replace(operatorSearchNonWordRegex, " ")
        .trim()
        .replace(operatorSearchRepeatedSpacesRegex, " ")
}

/**
 * Alias d'opérateurs normalisés une seule fois (indépendants de la requête de recherche) :
 * évite de tout re-normaliser à chaque frappe dans la recherche « op: … ».
 */
private val operatorSearchAliasCandidates: List<Pair<String, String>> by lazy {
    OperatorColors.all
        .flatMap { spec ->
            (listOf(spec.key, spec.label) + spec.aliases).map { rawAlias ->
                spec.key to normalizeOperatorSearchToken(rawAlias)
            }
        }
        .filter { (_, alias) -> alias.isNotBlank() }
        .distinct()
        .sortedByDescending { (_, alias) -> alias.length }
}

private fun parseOperatorSearchKeys(query: String): List<String> {
    val trimmed = query.trim()
    val splitIndex = trimmed.indexOf(':')
    if (splitIndex <= 0) return emptyList()

    val prefix = normalizeOperatorSearchToken(trimmed.substring(0, splitIndex)).replace(" ", "")
    if (prefix !in setOf("OP", "OPERATEUR", "OPERATOR", "O")) return emptyList()

    val cleanQuery = trimmed.substring(splitIndex + 1).trim()
    if (cleanQuery.isBlank()) return emptyList()

    val separatedTokens = cleanQuery
        .split(operatorSearchSplitRegex)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val separatedKeys = separatedTokens.map { OperatorColors.keyFor(it) }
    if (separatedKeys.size > 1 && separatedKeys.all { it != null }) {
        return separatedKeys.filterNotNull().distinct()
    }

    val normalizedQuery = normalizeOperatorSearchToken(cleanQuery)
    if (normalizedQuery.isBlank()) return emptyList()

    val candidates = operatorSearchAliasCandidates

    var reducedQuery = " $normalizedQuery "
    val matches = mutableListOf<Pair<Int, String>>()

    candidates.forEach { (key, alias) ->
        val pattern = Regex("(?<![A-Z0-9])${Regex.escape(alias)}(?![A-Z0-9])")
        pattern.find(reducedQuery)?.let { match ->
            matches += match.range.first to key
            reducedQuery = reducedQuery.replaceRange(match.range, " ".repeat(match.value.length))
        }
    }

    val leftover = reducedQuery.replace(operatorSearchNonWordRegex, "")
    if (matches.isEmpty() || leftover.isNotEmpty()) return emptyList()

    return matches
        .sortedBy { it.first }
        .map { it.second }
        .distinct()
}

private fun buildHsOperatorMap(sitesHs: List<SiteHsEntity>): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()

    sitesHs.forEach { hs ->
        val id = normalizedAnfrId(hs.idAnfr)
        if (id.isBlank()) return@forEach

        val parsedOperators = extractOperatorKeys(hs.operateur)
        val operators = if (parsedOperators.isEmpty()) listOf(HS_OPERATOR_WILDCARD) else parsedOperators
        result.getOrPut(id) { mutableSetOf() }.addAll(operators)
    }

    return result
}

private fun isOperatorSelected(
    operatorKey: String,
    selectedOperatorKeys: Set<String>
): Boolean {
    return operatorKey in selectedOperatorKeys
}

private fun isOperatorDeclaredHs(
    antenna: LocalisationEntity,
    operatorKey: String,
    hsOperatorMap: Map<String, Set<String>>
): Boolean {
    val hsOperators = hsOperatorMap[normalizedAnfrId(antenna.idAnfr)] ?: return false
    return HS_OPERATOR_WILDCARD in hsOperators || operatorKey in hsOperators
}

private fun visibleOperatorKeysForAntenna(
    antenna: LocalisationEntity,
    hsOperatorMap: Map<String, Set<String>>,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    selectedOperatorKeys: Set<String>
): List<String> {
    return extractOperatorKeys(antenna.operateur).filter { operatorKey ->
        if (!isOperatorSelected(operatorKey, selectedOperatorKeys)) {
            false
        } else if (isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap)) {
            showSitesOutOfService
        } else {
            showSitesInService
        }
    }
}

private fun hasVisibleHsOperator(
    antenna: LocalisationEntity,
    hsOperatorMap: Map<String, Set<String>>
): Boolean {
    val operators = extractOperatorKeys(antenna.operateur)
    return operators.any { operatorKey -> isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap) }
}

private fun compactActiveFilterValues(
    values: List<String>,
    moreLabel: (Int) -> String
): String {
    val cleanedValues = values.filter { it.isNotBlank() }
    if (cleanedValues.size <= MAP_ACTIVE_FILTER_LIST_LIMIT) {
        return cleanedValues.joinToString(", ")
    }

    return (cleanedValues.take(MAP_ACTIVE_FILTER_LIST_LIMIT) +
        moreLabel(cleanedValues.size - MAP_ACTIVE_FILTER_LIST_LIMIT))
        .joinToString(", ")
}

private fun summarizedActiveFilterSelection(
    selectedValues: List<String>,
    hiddenValues: List<String>,
    noneLabel: String,
    exceptLabel: (String) -> String,
    moreLabel: (Int) -> String
): String {
    if (selectedValues.isEmpty()) return noneLabel

    return if (hiddenValues.isNotEmpty() && hiddenValues.size < selectedValues.size) {
        exceptLabel(compactActiveFilterValues(hiddenValues, moreLabel))
    } else {
        compactActiveFilterValues(selectedValues, moreLabel)
    }
}

private fun buildActiveMapFilterSummary(
    selectedOperatorKeys: Set<String>,
    frequencyFilter: FrequencyFilterSelection,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    hideUndergroundSites: Boolean,
    showOnlyZbSites: Boolean,
    showRadioTv: Boolean,
    showRadioBroadcast: Boolean,
    showRadioPrivateMobile: Boolean,
    showRadioFh: Boolean,
    showRadioOther: Boolean,
    showSignalQuestCoveragePoints: Boolean,
    selectedSignalQuestCoverageOperatorKeys: Set<String>,
    operatorsLabel: String,
    technologiesLabel: String,
    frequenciesLabel: String,
    siteDisplayLabel: String,
    radioLabel: String,
    signalQuestCoverageLabel: String,
    inServiceLabel: String,
    outOfServiceLabel: String,
    hideUndergroundLabel: String,
    onlyZbLabel: String,
    radioTvLabel: String,
    radioBroadcastLabel: String,
    radioPrivateMobileLabel: String,
    radioFhLabel: String,
    radioOtherLabel: String,
    noneLabel: String,
    exceptLabel: (String) -> String,
    moreLabel: (Int) -> String
): String? {
    val activeFilters = mutableListOf<String>()

    if (selectedOperatorKeys != OperatorColors.defaultVisibleKeys) {
        val selectedOperators = OperatorColors.all
            .filter { it.key in selectedOperatorKeys }
            .map { it.label }
        val hiddenOperators = OperatorColors.all
            .filter { it.key !in selectedOperatorKeys }
            .map { it.label }

        activeFilters += "$operatorsLabel: " + summarizedActiveFilterSelection(
            selectedValues = selectedOperators,
            hiddenValues = hiddenOperators,
            noneLabel = noneLabel,
            exceptLabel = exceptLabel,
            moreLabel = moreLabel
        )
    }

    if (!frequencyFilter.isFullyEnabled) {
        val technologyFilters = listOf(
            "2G" to frequencyFilter.show2G,
            "3G" to frequencyFilter.show3G,
            "4G" to frequencyFilter.show4G,
            "5G" to frequencyFilter.show5G,
            "FH" to frequencyFilter.showFh
        )
        val selectedTechnologies = technologyFilters.filter { it.second }.map { it.first }
        val hiddenTechnologies = technologyFilters.filterNot { it.second }.map { it.first }
        if (selectedTechnologies.size != technologyFilters.size) {
            activeFilters += "$technologiesLabel: " + summarizedActiveFilterSelection(
                selectedValues = selectedTechnologies,
                hiddenValues = hiddenTechnologies,
                noneLabel = noneLabel,
                exceptLabel = exceptLabel,
                moreLabel = moreLabel
            )
        }

        val frequencyBandFilters = mutableListOf<Pair<String, Boolean>>()
        fun addBands(showTechnology: Boolean, technology: String, bands: List<Pair<String, Boolean>>) {
            if (showTechnology) {
                bands.forEach { (label, isSelected) -> frequencyBandFilters += "$technology $label" to isSelected }
            }
        }

        addBands(
            frequencyFilter.show2G,
            "2G",
            listOf(
                "900 MHz" to frequencyFilter.f2G900,
                "1800 MHz" to frequencyFilter.f2G1800
            )
        )
        addBands(
            frequencyFilter.show3G,
            "3G",
            listOf(
                "900 MHz" to frequencyFilter.f3G900,
                "2100 MHz" to frequencyFilter.f3G2100
            )
        )
        addBands(
            frequencyFilter.show4G,
            "4G",
            listOf(
                "700 MHz" to frequencyFilter.f4G700,
                "800 MHz" to frequencyFilter.f4G800,
                "900 MHz" to frequencyFilter.f4G900,
                "1800 MHz" to frequencyFilter.f4G1800,
                "2100 MHz" to frequencyFilter.f4G2100,
                "2600 MHz" to frequencyFilter.f4G2600
            )
        )
        addBands(
            frequencyFilter.show5G,
            "5G",
            listOf(
                "700 MHz" to frequencyFilter.f5G700,
                "1400 MHz" to frequencyFilter.f5G1400,
                "2100 MHz" to frequencyFilter.f5G2100,
                "3500 MHz" to frequencyFilter.f5G3500,
                "4200 MHz" to frequencyFilter.f5G4200,
                "26 GHz" to frequencyFilter.f5G26000
            )
        )

        val selectedBands = frequencyBandFilters.filter { it.second }.map { it.first }
        val hiddenBands = frequencyBandFilters.filterNot { it.second }.map { it.first }
        if (frequencyBandFilters.isNotEmpty() && selectedBands.size != frequencyBandFilters.size) {
            activeFilters += "$frequenciesLabel: " + summarizedActiveFilterSelection(
                selectedValues = selectedBands,
                hiddenValues = hiddenBands,
                noneLabel = noneLabel,
                exceptLabel = exceptLabel,
                moreLabel = moreLabel
            )
        }
    }

    val radioFilters = listOfNotNull(
        radioTvLabel.takeIf { showRadioTv },
        radioBroadcastLabel.takeIf { showRadioBroadcast },
        radioPrivateMobileLabel.takeIf { showRadioPrivateMobile },
        radioFhLabel.takeIf { showRadioFh },
        radioOtherLabel.takeIf { showRadioOther }
    )
    if (radioFilters.isNotEmpty()) {
        activeFilters += "$radioLabel: ${compactActiveFilterValues(radioFilters, moreLabel)}"
    }

    if (showSignalQuestCoveragePoints) {
        val coverageOperators = OperatorColors.metro.filter { it.key in AppConfig.signalQuestCoverageOperatorKeys }
        val selectedCoverageOperators = coverageOperators
            .filter { it.key in selectedSignalQuestCoverageOperatorKeys }
            .map { it.label }
        val hiddenCoverageOperators = coverageOperators
            .filter { it.key !in selectedSignalQuestCoverageOperatorKeys }
            .map { it.label }

        activeFilters += "$signalQuestCoverageLabel: " + summarizedActiveFilterSelection(
            selectedValues = selectedCoverageOperators,
            hiddenValues = hiddenCoverageOperators,
            noneLabel = noneLabel,
            exceptLabel = exceptLabel,
            moreLabel = moreLabel
        )
    }

    val siteFilters = mutableListOf<String>()
    if (showSitesInService != showSitesOutOfService) {
        val selectedStatuses = listOfNotNull(
            inServiceLabel.takeIf { showSitesInService },
            outOfServiceLabel.takeIf { showSitesOutOfService }
        )
        val hiddenStatuses = listOfNotNull(
            inServiceLabel.takeIf { !showSitesInService },
            outOfServiceLabel.takeIf { !showSitesOutOfService }
        )
        siteFilters += summarizedActiveFilterSelection(
            selectedValues = selectedStatuses,
            hiddenValues = hiddenStatuses,
            noneLabel = noneLabel,
            exceptLabel = exceptLabel,
            moreLabel = moreLabel
        )
    }
    if (showOnlyZbSites) siteFilters += onlyZbLabel
    if (hideUndergroundSites) siteFilters += hideUndergroundLabel
    if (siteFilters.isNotEmpty()) {
        activeFilters += "$siteDisplayLabel: ${compactActiveFilterValues(siteFilters, moreLabel)}"
    }

    return activeFilters.takeIf { it.isNotEmpty() }?.joinToString(" | ")
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel,
    photoDraftId: String? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val screenRotation = currentDisplayRotation(context)
    val currentScreenRotation by androidx.compose.runtime.rememberUpdatedState(screenRotation)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current // ✅ AJOUT
    val isUltraCompact = configuration.screenWidthDp < 300 || configuration.screenHeightDp < 350 // ✅ AJOUT
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val antennas by viewModel.antennas.collectAsState()
    val oldestServiceDate by viewModel.oldestServiceDate.collectAsState()
    val radioMarkers by viewModel.radioMarkers.collectAsState()
    val signalQuestCoveragePoints by viewModel.signalQuestCoveragePoints.collectAsState()
    val theoreticalCoverage by viewModel.theoreticalCoverage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sitesHs by viewModel.sitesHs.collectAsState()
    val cityStatsTechniques by viewModel.cityStatsTechniques.collectAsState()
    val isCityStatsTechniquesLoading by viewModel.isCityStatsTechniquesLoading.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val featureFlags by RemoteFeatureFlags.config
    val canUseSignalQuestCoverage by androidx.compose.runtime.rememberUpdatedState(
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_COVERAGE)
    )

    LiveDatabaseUsageWarningDialog(RemoteFeatureFlags.Features.LIVE_API_FR_BBOX)

    val rawPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val isColorTooLight = ColorUtils.calculateLuminance(rawPrimaryColor) > 0.85
    val safePrimaryColor = remember(rawPrimaryColor, isColorTooLight) {
        if (isColorTooLight) android.graphics.Color.parseColor("#2196F3") else rawPrimaryColor
    }

    // Mémorise les tracés de la ville sélectionnée pour le filtrage
    var currentCityPolygonsEncoded by rememberSaveable { mutableStateOf<String?>(null) }
    var currentCityPolygons by remember { mutableStateOf(decodeGeoPointPolygons(currentCityPolygonsEncoded)) }
    var currentSearchAreaBoundsEncoded by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSearchAreaBounds by remember { mutableStateOf(decodeSearchAreaBounds(currentSearchAreaBoundsEncoded)) }
    var loadedCitySearchKey by remember { mutableStateOf<String?>(null) }
    fun setCurrentCitySearch(bounds: SearchAreaBounds?, polygons: List<List<GeoPoint>>?) {
        currentSearchAreaBounds = bounds
        currentSearchAreaBoundsEncoded = encodeSearchAreaBounds(bounds)
        currentCityPolygons = polygons
        currentCityPolygonsEncoded = encodeGeoPointPolygons(polygons)
        if (bounds == null || polygons.isNullOrEmpty()) {
            loadedCitySearchKey = null
        }
    }

    fun loadCurrentCitySearchIfNeeded(force: Boolean = false) {
        val bounds = currentSearchAreaBounds ?: return
        val polygons = currentCityPolygons?.takeIf { it.isNotEmpty() } ?: return
        val searchKey = "${currentSearchAreaBoundsEncoded.orEmpty()}|${currentCityPolygonsEncoded.orEmpty()}"
        if (!force && loadedCitySearchKey == searchKey) return

        loadedCitySearchKey = searchKey
        viewModel.loadAntennasForCity(
            latNorth = bounds.latNorth,
            lonEast = bounds.lonEast,
            latSouth = bounds.latSouth,
            lonWest = bounds.lonWest,
            polygons = polygons
        )
    }

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
    val pendingSharedPhotoDraftId = photoDraftId?.takeIf { it.isNotBlank() }
    val pendingSharedPhotoCount = remember(pendingSharedPhotoDraftId) {
        pendingSharedPhotoDraftId?.let { SignalQuestUploadDraftStore.peek(it).size } ?: 0
    }
    val isSharedPhotoSelectionMode = pendingSharedPhotoDraftId != null && pendingSharedPhotoCount > 0

    fun isMapProviderEnabled(providerId: Int): Boolean {
        return when (providerId) {
            0 -> featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.MAP_IGN)
            1 -> featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.MAP_OSM)
            2 -> featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.MAP_MAPLIBRE)
            3 -> featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.MAP_OPEN_TOPO)
            4 -> featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.MAP_OFFLINE)
            else -> true
        }
    }

    fun fallbackMapProvider(): Int {
        return listOf(1, 0, 2, 3, 4).firstOrNull(::isMapProviderEnabled) ?: 1
    }

    val canUseMapSearch =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.MAP_SEARCH_NOMINATIM) &&
            featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.SEARCH_NOMINATIM)
    val canUseMapMeasure = featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.MAP_MEASURE)
    val canUseMapLocation = featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.MAP_LOCATION)
    val canUseLayerSelector = listOf(0, 1, 2, 3, 4).any(::isMapProviderEnabled)

    LaunchedEffect(Unit) {
        AppConfig.loadMapDisplayPreferences(prefs)
    }

    val safeClick = rememberSafeClick()

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showMapPageSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showLayerSheet by rememberSaveable { mutableStateOf(false) }
    val pageSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val mapViewUsable = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    var currentZoom by remember { mutableDoubleStateOf(15.0) }
    var currentLat by remember { mutableDoubleStateOf(48.8584) }
    var isMeasuringMode by rememberSaveable { mutableStateOf(false) }
    var trackNearestAll by rememberSaveable { mutableStateOf(false) }
    var trackNearestFav by rememberSaveable { mutableStateOf(false) }
    var measuredMapPointsStartFromLocation by rememberSaveable { mutableStateOf(true) }
    var measuredMapPointsEncoded by rememberSaveable { mutableStateOf<String?>(null) }
    var measuredMapPointLocationLinksEncoded by rememberSaveable { mutableStateOf<String?>(null) }
    val measuredSites = remember { mutableStateMapOf<String, LocalisationEntity>() }
    val measuredMapPoints = remember {
        mutableStateListOf<GeoPoint>().apply {
            addAll(decodeGeoPoints(measuredMapPointsEncoded))
        }
    }
    val measuredMapPointLocationLinks = remember {
        mutableStateListOf<Boolean>().apply {
            addAll(decodeBooleanList(measuredMapPointLocationLinksEncoded))
        }
    }

    var isClosestSiteExpanded by rememberSaveable { mutableStateOf(true) }
    var isClosestFavSiteExpanded by rememberSaveable { mutableStateOf(true) }

    fun saveMeasureSelections() {
        measuredMapPointsEncoded = encodeGeoPoints(measuredMapPoints)
        measuredMapPointLocationLinksEncoded = encodeBooleanList(measuredMapPointLocationLinks)
    }

    fun clearMeasureSelections() {
        measuredSites.clear()
        measuredMapPoints.clear()
        measuredMapPointLocationLinks.clear()
        measuredMapPointsStartFromLocation = true
        saveMeasureSelections()
    }

    fun addManualMeasurePoint(point: GeoPoint, startFromLocationIfFirst: Boolean) {
        if (measuredMapPoints.isEmpty()) {
            measuredMapPointsStartFromLocation = startFromLocationIfFirst
        }
        measuredMapPoints.add(point)
        measuredMapPointLocationLinks.add(measuredMapPointsStartFromLocation)
        saveMeasureSelections()
    }

    fun removeManualMeasurePoint(index: Int) {
        if (index !in measuredMapPoints.indices) return
        measuredMapPoints.removeAt(index)
        if (index in measuredMapPointLocationLinks.indices) {
            measuredMapPointLocationLinks.removeAt(index)
        }
        if (measuredMapPoints.isEmpty()) {
            measuredMapPointsStartFromLocation = true
        }
        saveMeasureSelections()
    }

    // Rétablit l'auto-ouverture à l'activation du mode mesure
    LaunchedEffect(isMeasuringMode, canUseMapMeasure) {
        if (!canUseMapMeasure && isMeasuringMode) {
            isMeasuringMode = false
            clearMeasureSelections()
        }
        if (isMeasuringMode) {
            isClosestSiteExpanded = true
            isClosestFavSiteExpanded = true
        }
    }

    val measureOverlay = remember { FolderOverlay() }
    val searchBoundaryOverlay = remember { FolderOverlay() }
    // ✅ LE CALQUE MACRO POUR LA VUE DÉZOOMÉE
    val macroOverlay = remember { FolderOverlay() }
    val signalQuestCoverageOverlay = remember { SignalQuestCoverageOverlay(context) }
    val theoreticalCoverageOverlay = remember { TheoreticalCoverageOverlay(context) }
    var selectedCoveragePoint by remember { mutableStateOf<SignalQuestCoveragePoint?>(null) }
    signalQuestCoverageOverlay.onPointClick = { selectedCoveragePoint = it }
    selectedCoveragePoint?.let { coveragePoint ->
        CoveragePointDetailDialog(
            point = coveragePoint,
            onDismiss = { selectedCoveragePoint = null }
        )
    }
    val radioOverlay = remember { FolderOverlay() }

    fun refreshSearchBoundaryOverlay(map: MapView, polygons: List<List<GeoPoint>>?) {
        searchBoundaryOverlay.items.clear()
        val boundaryPolygons = polygons
            ?.map { polygon -> polygon.map { point -> GeoPoint(point.latitude, point.longitude) } }
            ?.filter { it.size >= 3 }
            .orEmpty()

        if (boundaryPolygons.isEmpty()) {
            map.invalidate()
            return
        }

        val worldMask = object : org.osmdroid.views.overlay.Overlay() {
            private val path = android.graphics.Path()

            override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
                path.reset()
                boundaryPolygons.forEach { geoPoints ->
                    var first = true
                    geoPoints.forEach { pt ->
                        val px = projection.toPixels(pt, null)
                        if (first) {
                            path.moveTo(px.x.toFloat(), px.y.toFloat())
                            first = false
                        } else {
                            path.lineTo(px.x.toFloat(), px.y.toFloat())
                        }
                    }
                    path.close()
                }

                canvas.save()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.clipOutPath(path)
                } else {
                    @Suppress("DEPRECATION")
                    canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE)
                }

                canvas.drawColor(android.graphics.Color.parseColor("#66000000"))
                canvas.restore()
            }
        }

        val outlinesOverlay = org.osmdroid.views.overlay.FolderOverlay()
        boundaryPolygons.forEach { polygon ->
            val outline = Polyline(map).apply {
                setPoints(polygon)
                outlinePaint.color = android.graphics.Color.RED
                outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
            }
            outlinesOverlay.add(outline)
        }

        searchBoundaryOverlay.add(worldMask)
        searchBoundaryOverlay.add(outlinesOverlay)
        map.invalidate()
    }

    var showLocationBtn by remember { mutableStateOf(prefs.getBoolean("show_map_location", true)) }
    var showZoomBtns by remember { mutableStateOf(prefs.getBoolean("show_map_zoom", true)) }
    var showToolbox by remember { mutableStateOf(prefs.getBoolean("show_map_toolbox", true)) }
    var showCompass by remember { mutableStateOf(prefs.getBoolean("show_map_compass", true)) }
    var showScale by remember { mutableStateOf(prefs.getBoolean("show_map_scale", true)) }
    var showAttribution by remember { mutableStateOf(prefs.getBoolean("show_map_attribution", true)) }
    val showLocationMarker by AppConfig.showMapLocationMarker

    var myCurrentLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var currentSpeedKmH by remember { mutableIntStateOf(0) }
    var isToolboxExpanded by rememberSaveable { mutableStateOf(false) }
    var isTimeSliderVisible by rememberSaveable { mutableStateOf(false) }
    var timeSliderThreshold by rememberSaveable { mutableStateOf<Int?>(null) }
    var timeSliderStats by remember { mutableStateOf(TimeSliderStats(emptyMap(), 0)) }
    val timeSliderLift = if (isTimeSliderVisible) 104.dp else 0.dp
    val todayDateInt = remember {
        val c = java.util.Calendar.getInstance()
        c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)
    }
    // Le slider temporel n'a de sens qu'avec la base ANFR locale (dates par site).
    // L'API live (fallback sans base) ne fournit pas les dates -> on masque le bouton.
    val timeSliderAvailable = AppConfig.localDatabaseState.value ==
        fr.geotower.data.db.GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")
    var operatorSearchPreviousOperatorKeys by rememberSaveable { mutableStateOf<List<String>?>(null) }

    fun applyOperatorSearchSelection(operatorKeys: Set<String>) {
        if (operatorSearchPreviousOperatorKeys == null) {
            operatorSearchPreviousOperatorKeys = AppConfig.selectedOperatorKeys.value.toList()
        }
        AppConfig.setSelectedOperatorKeys(operatorKeys)
    }

    fun restoreOperatorSearchSelection() {
        val previousOperatorKeys = operatorSearchPreviousOperatorKeys ?: return
        AppConfig.setSelectedOperatorKeys(previousOperatorKeys.toSet())
        operatorSearchPreviousOperatorKeys = null
    }

    fun cancelSharedPhotoSelection() {
        pendingSharedPhotoDraftId?.let { SignalQuestUploadDraftStore.discard(it) }
        restoreOperatorSearchSelection()
        safeBackNavigation.navigateBack()
    }

    // ✅ CORRECTION : Gère le geste "Retour" physique du téléphone
    androidx.activity.compose.BackHandler {
        if (isMeasuringMode) {
            isMeasuringMode = false
            clearMeasureSelections()
        } else if (isSharedPhotoSelectionMode) {
            cancelSharedPhotoSelection()
        } else {
            restoreOperatorSearchSelection()
            safeBackNavigation.navigateBack()
        }
    }

    val markersOverlay = remember {
        object : org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer(context) {
            override fun buildClusterMarker(cluster: org.osmdroid.bonuspack.clustering.StaticCluster, mapView: MapView): Marker {
                // 🚨 MODIFICATION : On écrase la zone de clic pour la forcer à être ronde !
                val m = object : Marker(mapView) {
                    override fun hitTest(event: android.view.MotionEvent, mapView: MapView): Boolean {
                        val pj = mapView.projection
                        val screenCoords = android.graphics.Point()
                        pj.toPixels(position, screenCoords)

                        val dx = event.x - screenCoords.x
                        val dy = event.y - screenCoords.y

                        // Rayon de clic mathématique de 22dp (parfait pour le doigt)
                        val clickRadius = 22f * mapView.context.resources.displayMetrics.density
                        return (dx * dx + dy * dy) <= (clickRadius * clickRadius)
                    }
                }

                m.position = GeoPoint(cluster.position.latitude, cluster.position.longitude)
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                val allOperators = mutableListOf<String>()
                for (i in 0 until cluster.size) {
                    val item = cluster.getItem(i)
                    @Suppress("UNCHECKED_CAST")
                    (item.relatedObject as? List<String>)?.let { allOperators.addAll(it) }
                }

                m.icon = MapUtils.createClusterIcon(context, allOperators.distinct(), cluster.size, AppConfig.defaultOperator.value)

                m.setOnMarkerClickListener { clickedMarker, map ->
                    // 1. On fige les coordonnées exactes AVANT toute autre action
                    val targetPoint = org.osmdroid.util.GeoPoint(
                        clickedMarker.position.latitude,
                        clickedMarker.position.longitude
                    )
                    // 2. On calcule le zoom souhaité (+1.5 est un bon compromis, modifiable !)
                    val targetZoom = map.zoomLevelDouble + 1.5

                    map.post {
                        // 3. ON TUE TOUTE ANIMATION EN COURS
                        map.controller.stopAnimation(false)

                        // 4. On utilise les setters purs (0% d'animation garantie)
                        map.controller.setZoom(targetZoom)
                        map.controller.setCenter(targetPoint)
                    }
                    true
                }
                return m
            }
        }.apply {
            setRadius(250)
        }
    }

    var searchJob by remember { mutableStateOf<Job?>(null) }
    val mapProvider by AppConfig.mapProvider

    // ✅ NOUVEAU : Fournisseur effectif calculé une seule fois au chargement
    var effectiveProvider by remember { mutableIntStateOf(AppConfig.mapProvider.intValue) }
    var mapFiles by remember { mutableStateOf(emptyArray<java.io.File>()) }

    // Synchronisation si l'utilisateur change la carte dans les paramètres
    LaunchedEffect(AppConfig.mapProvider.intValue, featureFlags) {
        val requestedProvider = AppConfig.mapProvider.intValue
        val nextProvider = if (isMapProviderEnabled(requestedProvider)) {
            requestedProvider
        } else {
            fallbackMapProvider()
        }
        effectiveProvider = nextProvider
        if (nextProvider != requestedProvider) {
            AppConfig.mapProvider.value = nextProvider
            prefs.edit().putInt("map_provider", nextProvider).apply()
        }
    }

    // Vérification réseau + fichiers au premier affichage
    LaunchedEffect(featureFlags) {
        val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
        val files = offlineDir.listFiles { file -> file.extension == "map" && file.length() > 0L } ?: emptyArray()
        mapFiles = files

        // Si on est hors ligne ET qu'on a bien téléchargé une carte
        if (!isNetworkAvailable(context) && files.isNotEmpty() && isMapProviderEnabled(4)) {
            effectiveProvider = 4 // On bascule silencieusement sur le hors-ligne
        }
    }

    val ignStyle by AppConfig.ignStyle
    val shouldInvertColors = ((mapProvider == 0 || mapProvider == 1) && ignStyle == 1)

    var azimuth by remember { mutableFloatStateOf(0f) }
    val continuousAzimuth = remember { floatArrayOf(0f) }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var showCityStatsPopup by rememberSaveable { mutableStateOf(false) }
    var showCityStatsDetail by rememberSaveable { mutableStateOf(false) }
    var isTrackingActive by rememberSaveable { mutableStateOf(false) }
    var hasCenteredOnLocation by rememberSaveable { mutableStateOf(false) }

    val txtMapTitle = stringResource(R.string.appstrings_map_title)
    val txtSearchCityOrId = stringResource(R.string.appstrings_search_city_or_id)
    val txtLocationNotFound = stringResource(R.string.appstrings_location_not_found)
    val txtNetworkErrorSearch = stringResource(R.string.appstrings_network_error_search)
    val txtSearchDataUnavailable = stringResource(R.string.appstrings_search_data_unavailable)
    val txtDeleteTraces = stringResource(R.string.appstrings_delete_traces)
    val txtClosestSite = stringResource(R.string.appstrings_closest_site)
    val txtFilter = stringResource(R.string.appstrings_filter)
    val txtMapIgnLayer = stringResource(R.string.appstrings_map_ign_layer)
    val txtMapOsmLayer = stringResource(R.string.appstrings_map_osm_layer)
    val txtMapLight = stringResource(R.string.appstrings_map_light)
    val txtMapDark = stringResource(R.string.appstrings_map_dark)
    val txtMapSatellite = stringResource(R.string.appstrings_map_satellite)
    val txtMapMapLibre = stringResource(R.string.appstrings_map_map_libre)
    val txtMapTopo = stringResource(R.string.appstrings_map_topo)
    val txtMapOfflineLayer = stringResource(R.string.appstrings_map_offline_layer)

    val txtOperatorsTitle = stringResource(R.string.appstrings_operators_title)
    val txtTechnologiesTitle = stringResource(R.string.appstrings_technologies_title)
    val txtFrequenciesTitle = stringResource(R.string.appstrings_frequencies_title)
    val txtSiteDisplayTitle = stringResource(R.string.appstrings_site_display_title)
    val txtRadioTitle = stringResource(R.string.appstrings_radio_share_radio_title)
    val txtInService = stringResource(R.string.appstrings_sites_in_service_label)
    val txtOutOfService = stringResource(R.string.appstrings_sites_out_of_service_label)
    val txtHideUndergroundSites = stringResource(R.string.appstrings_hide_underground_sites_label)
    val txtOnlyZbSites = stringResource(R.string.appstrings_show_only_zb_sites_label)
    val txtRadioTv = stringResource(R.string.appstrings_radio_category_tv)
    val txtRadioBroadcast = stringResource(R.string.appstrings_radio_category_radio)
    val txtRadioPrivateMobile = stringResource(R.string.appstrings_radio_category_private_mobile)
    val txtRadioFh = stringResource(R.string.appstrings_radio_category_fh)
    val txtRadioOther = stringResource(R.string.appstrings_radio_category_other)
    val txtSignalQuestCoverage = stringResource(R.string.appstrings_signalquest_coverage_title)
    val txtNoActiveFilterValue = stringResource(R.string.appstrings_map_active_filters_none)
    val activeMapFilterSummary = buildActiveMapFilterSummary(
        selectedOperatorKeys = AppConfig.selectedOperatorKeys.value,
        frequencyFilter = FrequencyFilterSelection.fromMapConfig(),
        showSitesInService = AppConfig.showSitesInService.value,
        showSitesOutOfService = AppConfig.showSitesOutOfService.value,
        hideUndergroundSites = AppConfig.hideUndergroundSites.value,
        showOnlyZbSites = AppConfig.showOnlyZbSites.value,
        showRadioTv = AppConfig.showRadioTv.value,
        showRadioBroadcast = AppConfig.showRadioBroadcast.value,
        showRadioPrivateMobile = AppConfig.showRadioPrivateMobile.value,
        showRadioFh = AppConfig.showRadioFh.value,
        showRadioOther = AppConfig.showRadioOther.value,
        showSignalQuestCoveragePoints = canUseSignalQuestCoverage && AppConfig.showSignalQuestCoveragePoints.value,
        selectedSignalQuestCoverageOperatorKeys = AppConfig.selectedSignalQuestCoverageOperatorKeys.value,
        operatorsLabel = txtOperatorsTitle,
        technologiesLabel = txtTechnologiesTitle,
        frequenciesLabel = txtFrequenciesTitle,
        siteDisplayLabel = txtSiteDisplayTitle,
        radioLabel = txtRadioTitle,
        signalQuestCoverageLabel = txtSignalQuestCoverage,
        inServiceLabel = txtInService,
        outOfServiceLabel = txtOutOfService,
        hideUndergroundLabel = txtHideUndergroundSites,
        onlyZbLabel = txtOnlyZbSites,
        radioTvLabel = txtRadioTv,
        radioBroadcastLabel = txtRadioBroadcast,
        radioPrivateMobileLabel = txtRadioPrivateMobile,
        radioFhLabel = txtRadioFh,
        radioOtherLabel = txtRadioOther,
        noneLabel = txtNoActiveFilterValue,
        exceptLabel = { value -> context.getString(R.string.appstrings_map_active_filters_except, value) },
        moreLabel = { count -> context.getString(R.string.appstrings_map_active_filters_more, count) }
    )

    val txtWarningTitle = stringResource(R.string.appstrings_warning_title)
    val txtLightColorWarning = stringResource(R.string.appstrings_light_color_warning)
    val txtDoNotShowAgain = stringResource(R.string.appstrings_do_not_show_again)
    val txtUnderstood = stringResource(R.string.appstrings_understood)

    var hideColorWarning by remember { mutableStateOf(prefs.getBoolean("hide_light_color_warning", false)) }
    var showColorWarningDialog by rememberSaveable { mutableStateOf(false) }
    var dontShowAgainChecked by rememberSaveable { mutableStateOf(false) }
    val lastTilesColorFilterMap = remember { arrayOfNulls<MapView>(1) }
    val lastTilesColorFilterInverted = remember { arrayOfNulls<Boolean>(1) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        @Suppress("DEPRECATION")
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        var lastAzimuthUiUpdateMs = 0L

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                var rawAzimuth = when {
                    event.sensor.type == Sensor.TYPE_ROTATION_VECTOR -> {
                        azimuthFromRotationVector(event.values, currentScreenRotation)
                    }
                    isLegacyOrientationSensor(event.sensor) -> {
                        correctLegacyAzimuthForDisplay(event.values[0], currentScreenRotation)
                    }
                    else -> return
                }

                rawAzimuth = (rawAzimuth + 360) % 360

                var delta = rawAzimuth - (continuousAzimuth[0] % 360f)
                if (delta < -180f) delta += 360f
                else if (delta > 180f) delta -= 360f

                val smoothedAzimuth = continuousAzimuth[0] + delta * 0.15f
                continuousAzimuth[0] = smoothedAzimuth

                val now = System.currentTimeMillis()
                if (abs(smoothedAzimuth - azimuth) > 0.75f &&
                    now - lastAzimuthUiUpdateMs >= MAP_COMPASS_UPDATE_INTERVAL_MS
                ) {
                    lastAzimuthUiUpdateMs = now
                    azimuth = smoothedAzimuth
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapViewRef?.onResume()
                    locationOverlayRef?.enableMyLocation()
                    if (rotationSensor != null) {
                        sensorManager?.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapViewRef?.let { map ->
                        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putFloat("last_map_lat", map.mapCenter.latitude.toFloat())
                            .putFloat("last_map_lon", map.mapCenter.longitude.toFloat())
                            .putFloat("last_map_zoom", map.zoomLevelDouble.toFloat())
                            .apply()
                    }

                    mapViewRef?.onPause()
                    locationOverlayRef?.disableMyLocation()
                    sensorManager?.unregisterListener(sensorEventListener)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            restoreOperatorSearchSelection()
            AppConfig.timeSliderActive.value = false
            viewModel.resetCityLock()
            lifecycleOwner.lifecycle.removeObserver(observer)
            searchJob?.cancel()
            searchJob = null
            sensorManager?.unregisterListener(sensorEventListener)
            mapViewUsable.set(false)
            locationOverlayRef?.disableMyLocation()
            mapViewRef?.onPause()
            mapViewRef?.onDetach()
            locationOverlayRef = null
            mapViewRef = null
        }
    }

    // ✅ 1. On déclare la liste filtrée comme une variable d'état
    var filteredAntennas by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }

    // ✅ 2. LaunchedEffect pour calculer en arrière-plan
    LaunchedEffect(
        antennas, AppConfig.selectedOperatorKeys.value,
        AppConfig.showTechnoFH.value, AppConfig.showTechno2G.value, AppConfig.showTechno3G.value, AppConfig.showTechno4G.value, AppConfig.showTechno5G.value,
        AppConfig.f2G_900.value, AppConfig.f2G_1800.value, AppConfig.f3G_900.value, AppConfig.f3G_2100.value,
        AppConfig.f4G_700.value, AppConfig.f4G_800.value, AppConfig.f4G_900.value, AppConfig.f4G_1800.value, AppConfig.f4G_2100.value, AppConfig.f4G_2600.value,
        AppConfig.f5G_700.value, AppConfig.f5G_1400.value, AppConfig.f5G_2100.value, AppConfig.f5G_3500.value, AppConfig.f5G_4200.value, AppConfig.f5G_26000.value,
        AppConfig.showSitesInService.value, AppConfig.showSitesOutOfService.value, AppConfig.hideUndergroundSites.value, AppConfig.showOnlyZbSites.value, sitesHs, currentCityPolygons,
        isTimeSliderVisible, timeSliderThreshold
    ) {
        val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val selectedOperators = AppConfig.selectedOperatorKeys.value
            val showSitesInService = AppConfig.showSitesInService.value
            val showSitesOutOfService = AppConfig.showSitesOutOfService.value
            val hideUndergroundSites = AppConfig.hideUndergroundSites.value
            val showOnlyZbSites = AppConfig.showOnlyZbSites.value
            val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
            val hsOperatorMap = buildHsOperatorMap(sitesHs)

            val base = antennas.filter { antenna ->
                val visibleOperators = visibleOperatorKeysForAntenna(
                    antenna = antenna,
                    hsOperatorMap = hsOperatorMap,
                    showSitesInService = showSitesInService,
                    showSitesOutOfService = showSitesOutOfService,
                    selectedOperatorKeys = selectedOperators
                )

                // ✅ 1. ON VÉRIFIE LES OPÉRATEURS TOUT DE SUITE
                // 🚨 2. LA CORRECTION : Si c'est un cluster, on vérifie au moins l'opérateur !
                if (antenna.idAnfr.startsWith("CLUSTER_")) {
                    return@filter frequencyFilter.isFullyEnabled &&
                        visibleOperators.isNotEmpty() &&
                        (!showOnlyZbSites || antenna.isZb == 1)
                }

                // --- 3. POUR LES VRAIES ANTENNES, ON CONTINUE AVEC LE RESTE DES FILTRES ---
                val isInCityBounds = currentCityPolygons.isNullOrEmpty() || currentCityPolygons!!.any { poly -> isPointInPolygon(antenna.latitude, antenna.longitude, poly) }
                val matchesUndergroundFilter = !hideUndergroundSites || antenna.hasUndergroundSupport != 1
                val matchesZbFilter = !showOnlyZbSites || antenna.isZb == 1

                val matchTechno = frequencyFilter.matchesAntenna(antenna)

                visibleOperators.isNotEmpty() && matchesUndergroundFilter && matchesZbFilter && isInCityBounds && matchTechno
            }

            // Slider temporel : on ne garde que les sites mis en service avant le seuil choisi,
            // et on compte les sites visibles par operateur (+ ceux sans date exploitable).
            if (!isTimeSliderVisible) {
                base to null
            } else {
                val threshold = timeSliderThreshold
                val counts = HashMap<String, Int>()
                var undated = 0
                var datedTotal = 0
                val visible = ArrayList<LocalisationEntity>(base.size)
                base.forEach { antenna ->
                    if (antenna.idAnfr.startsWith("CLUSTER_")) {
                        visible.add(antenna)
                        return@forEach
                    }
                    val serviceInt = parseServiceDateInt(antenna.dateService)
                    if (serviceInt == null) {
                        undated++
                        if (threshold == null) visible.add(antenna)
                        return@forEach
                    }
                    datedTotal++
                    if (threshold == null || serviceInt <= threshold) {
                        visible.add(antenna)
                        OperatorColors.keyFor(antenna.operateur)?.let { key ->
                            counts[key] = (counts[key] ?: 0) + 1
                        }
                    }
                }
                // Aucune date exploitable dans la zone (typiquement la base live qui ne renvoie pas
                // les dates) : on n'efface pas la carte, le slider reste sans effet ici.
                val finalVisible = if (datedTotal == 0) base else visible
                finalVisible to TimeSliderStats(counts, undated)
            }
        }
        filteredAntennas = computed.first
        computed.second?.let { timeSliderStats = it }
    }

    fun createDistanceLabel(text: String): BitmapDrawable {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 34f
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val width = (paint.measureText(text) + 40).toInt()
        val bitmap = Bitmap.createBitmap(width, 70, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rectPaint = Paint().apply { color = android.graphics.Color.parseColor("#3B5998"); style = Paint.Style.FILL }
        canvas.drawRoundRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), 20f, 20f, rectPaint)
        canvas.drawText(text, bitmap.width / 2f, bitmap.height / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun refreshMeasureLayers(map: MapView) {
        measureOverlay.items.clear()
        val myLoc = myCurrentLoc ?: locationOverlayRef?.myLocation

        fun formatMeasureDistance(dist: Double): String {
            val isMi = AppConfig.distanceUnit.intValue == 1
            return if (isMi) {
                val distMiles = dist / 1609.34
                if (distMiles < 0.1) {
                    "${(dist * 3.28084).toInt()} ft"
                } else {
                    String.format(java.util.Locale.US, "%.2f mi", distMiles)
                }
            } else {
                if (dist >= 1000) {
                    String.format(java.util.Locale.US, "%.3f km", dist / 1000)
                } else {
                    "${dist.toInt()} m"
                }
            }
        }

        fun addMeasureSegment(startPoint: GeoPoint, endPoint: GeoPoint, onRemove: () -> Unit) {
            val line = Polyline(map).apply {
                setPoints(listOf(startPoint, endPoint))
                outlinePaint.color = android.graphics.Color.parseColor("#3B5998")
                outlinePaint.strokeWidth = 10f
            }
            line.setOnClickListener { _, _, _ ->
                onRemove()
                refreshMeasureLayers(map)
                true
            }
            measureOverlay.add(line)

            val labelMarker = Marker(map).apply {
                position = GeoPoint(
                    (startPoint.latitude + endPoint.latitude) / 2,
                    (startPoint.longitude + endPoint.longitude) / 2
                )
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createDistanceLabel(formatMeasureDistance(startPoint.distanceToAsDouble(endPoint)))
                infoWindow = null
            }
            labelMarker.setOnMarkerClickListener { _, _ ->
                onRemove()
                refreshMeasureLayers(map)
                true
            }
            measureOverlay.add(labelMarker)
        }

        if (measuredMapPoints.isNotEmpty()) {
            if (myLoc != null) {
                measuredMapPoints.forEachIndexed { pointIndex, point ->
                    if (measuredMapPointLocationLinks.getOrNull(pointIndex) == true) {
                        addMeasureSegment(myLoc, point) {
                            if (measuredMapPoints.size > 1) {
                                measuredMapPointLocationLinks[pointIndex] = false
                            } else {
                                removeManualMeasurePoint(pointIndex)
                            }
                        }
                    }
                }
            }

            measuredMapPoints.zipWithNext().forEachIndexed { segmentIndex, (startPoint, endPoint) ->
                addMeasureSegment(startPoint, endPoint) {
                    removeManualMeasurePoint(segmentIndex + 1)
                }
            }
        }

        measuredSites.values.forEach { antenna ->
            if (myLoc == null) return@forEach
            val antLoc = GeoPoint(antenna.latitude, antenna.longitude)

            val line = Polyline(map).apply {
                setPoints(listOf(myLoc, antLoc))
                outlinePaint.color = android.graphics.Color.parseColor("#3B5998")
                outlinePaint.strokeWidth = 10f
            }
            line.setOnClickListener { _, _, _ ->
                measuredSites.remove(antenna.idAnfr)
                refreshMeasureLayers(map)
                true
            }
            measureOverlay.add(line)

            val dist = myLoc.distanceToAsDouble(antLoc)

            // ✅ LECTURE DES PARAMÈTRES (0 = km, 1 = miles)
            val isMi = AppConfig.distanceUnit.intValue == 1

            // ✅ CONVERSION SELON LE CHOIX
            val distStr = if (isMi) {
                val distMiles = dist / 1609.34f
                if (distMiles < 0.1f) {
                    // Pour les très courtes distances en miles, on affiche en pieds (ft)
                    "${(dist * 3.28084f).toInt()} ft"
                } else {
                    String.format(java.util.Locale.US, "%.2f mi", distMiles)
                }
            } else {
                // Système métrique classique (m / km)
                if (dist >= 1000) String.format("%.3f km", dist / 1000) else "${dist.toInt()} m"
            }

            val labelMarker = Marker(map).apply {
                position = GeoPoint((myLoc.latitude + antLoc.latitude) / 2, (myLoc.longitude + antLoc.longitude) / 2)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createDistanceLabel(distStr)
                infoWindow = null
            }
            labelMarker.setOnMarkerClickListener { _, _ ->
                measuredSites.remove(antenna.idAnfr)
                refreshMeasureLayers(map)
                true
            }
            measureOverlay.add(labelMarker)
        }
        map.invalidate()
    }

    val measureTapOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!isMeasuringMode) return false
                val map = mapViewRef ?: return true
                myCurrentLoc ?: locationOverlayRef?.myLocation ?: return true

                addManualMeasurePoint(
                    GeoPoint(p.latitude, p.longitude),
                    startFromLocationIfFirst = true
                )
                refreshMeasureLayers(map)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
    }

    // ✅ AJOUT DU PARAMÈTRE sitesHsList
    fun openSupportDetailFromMap(map: MapView, antenna: LocalisationEntity) {
        val supportId = antenna.idAnfr.takeIf { it.isNotBlank() }
        if (supportId == null) {
            AppLogger.w("GeoTowerMap", "Cannot open support detail for blank idAnfr")
            return
        }

        prefs.edit()
            .putFloat("clicked_lat", antenna.latitude.toFloat())
            .putFloat("clicked_lon", antenna.longitude.toFloat())
            .apply()

        map.post {
            try {
                val photoDraftParam = pendingSharedPhotoDraftId
                    ?.let { "&photoDraftId=${Uri.encode(it)}" }
                    .orEmpty()
                navController.navigate("support_detail/${Uri.encode(supportId)}?operator=&fromMap=true$photoDraftParam") {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                AppLogger.w("GeoTowerMap", "Map marker navigation failed for idAnfr=${antenna.idAnfr}", e)
            }
        }
    }

    fun openRadioSupportDetailFromMap(map: MapView, marker: RadioMapMarker) {
        if (marker.supportId.isBlank()) {
            AppLogger.w("GeoTowerMap", "Cannot open radio support detail for marker=${marker.id}")
            return
        }

        prefs.edit()
            .putFloat("clicked_lat", marker.latitude.toFloat())
            .putFloat("clicked_lon", marker.longitude.toFloat())
            .putFloat("last_map_lat", marker.latitude.toFloat())
            .putFloat("last_map_lon", marker.longitude.toFloat())
            .putFloat("last_map_zoom", 18f)
            .apply()

        map.post {
            try {
                val photoDraftParam = pendingSharedPhotoDraftId
                    ?.let { "&photoDraftId=${Uri.encode(it)}" }
                    .orEmpty()
                navController.navigate("support_detail/${Uri.encode(marker.supportId)}?operator=&fromMap=true$photoDraftParam") {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                AppLogger.w("GeoTowerMap", "Radio marker navigation failed for marker=${marker.id}", e)
            }
        }
    }

    fun updateRadioMarkers(map: MapView, markers: List<RadioMapMarker>) {
        radioOverlay.items.clear()

        if (AppConfig.radioMapCategoryMask() == 0 || markers.isEmpty()) {
            map.invalidate()
            return
        }

        val mobileSupportLocationKeys = filteredAntennas
            .asSequence()
            .filterNot { it.idAnfr.startsWith("CLUSTER_") }
            .filter { OperatorColors.keysFor(it.operateur).isNotEmpty() }
            .map { mapLocationKey(it.latitude, it.longitude) }
            .toSet()

        fun radioSupportGroupKey(marker: RadioMapMarker): String {
            return marker.supportId
                .takeIf { it.isNotBlank() }
                ?.let { "support:$it" }
                ?: "location:${mapLocationKey(marker.latitude, marker.longitude)}"
        }

        fun aggregateRadioSupportMarkers(group: List<RadioMapMarker>): RadioMapMarker {
            val primary = group.maxWithOrNull(
                compareBy<RadioMapMarker> { it.emitterCount }
                    .thenBy { it.antennaCount }
            ) ?: group.first()
            val actorLabels = group.mapNotNull { it.actorLabel?.takeIf { label -> label.isNotBlank() } }.distinct()
            return primary.copy(
                id = "RADIO_SUPPORT_${primary.supportId.ifBlank { mapLocationKey(primary.latitude, primary.longitude) }}",
                serviceMask = group.fold(0) { acc, marker -> acc or marker.serviceMask },
                systemMask = group.fold(0) { acc, marker -> acc or marker.systemMask },
                actorLabel = actorLabels.singleOrNull(),
                emitterCount = group.sumOf { it.emitterCount },
                antennaCount = group.sumOf { it.antennaCount },
                minFreqKhz = group.mapNotNull { it.minFreqKhz }.minOrNull(),
                maxFreqKhz = group.mapNotNull { it.maxFreqKhz }.maxOrNull(),
                clusterCount = 1,
                detailText = null
            )
        }

        fun addRadioAzimuthMarker(item: RadioMapMarker) {
            if (item.isCluster || item.azimuths.isEmpty()) return
            val azimuthMarker = RadioMarker(map, item, showCircle = false).apply {
                position = GeoPoint(item.latitude, item.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapUtils.createTransparentMarkerIcon(context)
                infoWindow = null
            }
            radioOverlay.add(azimuthMarker)
        }

        val displayEntries: List<Pair<RadioMapMarker, List<RadioMapMarker>>> = buildList {
            val limitedMarkers = markers.take(RADIO_MAP_MARKER_LIMIT)
            limitedMarkers
                .filter { it.isCluster }
                .forEach { cluster -> add(cluster to listOf(cluster)) }
            limitedMarkers
                .filterNot { it.isCluster }
                .groupBy(::radioSupportGroupKey)
                .values
                .forEach { group -> add(aggregateRadioSupportMarkers(group) to group) }
        }

        displayEntries.forEach { (item, members) ->
            val hasMobileOnSameSupport = !item.isCluster &&
                mapLocationKey(item.latitude, item.longitude) in mobileSupportLocationKeys
            val showRadioCircle = item.isCluster || !hasMobileOnSameSupport
            val hasRadioAzimuths = members.any { it.azimuths.isNotEmpty() }
            if (!showRadioCircle && !hasRadioAzimuths) return@forEach

            members.forEach(::addRadioAzimuthMarker)
            if (!showRadioCircle) return@forEach

            val marker = RadioMarker(map, item, showRadioCircle).apply {
                position = GeoPoint(item.latitude, item.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapUtils.createRadioMarkerIcon(context, item.serviceMask, item.systemMask, item.clusterCount)
                title = item.title(context)
                snippet = item.subtitle(context)
                setOnMarkerClickListener { clickedMarker, mapView ->
                    if (isMeasuringMode) {
                        addManualMeasurePoint(
                            GeoPoint(item.latitude, item.longitude),
                            startFromLocationIfFirst = myCurrentLoc != null || locationOverlayRef?.myLocation != null
                        )
                        refreshMeasureLayers(map)
                    } else if (item.isCluster) {
                        val targetPoint = GeoPoint(item.latitude, item.longitude)
                        mapView.post {
                            mapView.controller.stopAnimation(false)
                            mapView.controller.setZoom(mapView.zoomLevelDouble + 1.5)
                            mapView.controller.setCenter(targetPoint)
                        }
                    } else {
                        openRadioSupportDetailFromMap(map, item)
                    }
                    true
                }
            }
            radioOverlay.add(marker)
        }

        map.invalidate()
    }

    suspend fun updateMarkers(map: MapView, antennasList: List<LocalisationEntity>, sitesHsList: List<SiteHsEntity> = emptyList()) {
        val selectedOperators = AppConfig.selectedOperatorKeys.value
        val showSitesInService = AppConfig.showSitesInService.value
        val showSitesOutOfService = AppConfig.showSitesOutOfService.value
        val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
        val shouldFilterAzimuthsByFrequency =
            !frequencyFilter.isFullyEnabled && (AppConfig.showAzimuths.value || AppConfig.showAzimuthsCone.value)
        val azimuthTechniquesById = if (shouldFilterAzimuthsByFrequency) {
            val detailIds = antennasList.asSequence()
                .filterNot { it.idAnfr.startsWith("CLUSTER_") }
                .filter { !it.azimuts.isNullOrBlank() }
                .map { it.idAnfr }
                .distinct()
                .take(MAP_AZIMUTH_DETAIL_LIMIT)
                .toList()
            viewModel.getMapAzimuthTechniqueDetails(detailIds)
        } else {
            emptyMap()
        }
        fun ensureMapNotDisposed() {
            if (!mapViewUsable.get()) {
                throw java.util.concurrent.CancellationException("MapView disposed during marker refresh")
            }
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            ensureMapNotDisposed()

            if (antennasList.isEmpty()) {
                currentCoroutineContext().ensureActive()
                ensureMapNotDisposed()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!mapViewUsable.get() || mapViewRef !== map) return@withContext
                    macroOverlay.items.clear()
                    markersOverlay.items.clear()
                    markersOverlay.invalidate()
                    map.invalidate()
                }
                return@withContext
            }

            // Table de correspondance ANFR -> operateurs declares HS (sert au filtre de visibilité).
            val hsOperatorMap = buildHsOperatorMap(sitesHsList)
            // Propagation « zone blanche » : uniquement pour la COULEUR du marqueur (pas le filtre).
            // On ajoute les opérateurs ZB déduits HS parce qu'un voisin du même site est déclaré HS.
            val zbPotentialHs = fr.geotower.utils.zbPotentialOutages(antennasList, sitesHsList)
            val hsColorOperatorMap = if (zbPotentialHs.isEmpty()) {
                hsOperatorMap
            } else {
                buildHsOperatorMap(sitesHsList + zbPotentialHs)
            }

            fun visibleAntennaForMap(
                antenna: LocalisationEntity,
                activeOperatorKeys: List<String>
            ): LocalisationEntity {
                val filteredAzimuths = if (shouldFilterAzimuthsByFrequency) {
                    filteredAzimuthsForFrequencySelection(
                        detailsFrequences = azimuthTechniquesById[antenna.idAnfr]?.detailsFrequences,
                        filter = frequencyFilter
                    )
                } else {
                    null
                }

                return antenna.copy(
                    operateur = activeOperatorKeys.joinToString(", "),
                    azimuts = filteredAzimuths ?: antenna.azimuts
                )
            }

            fun buildAntennaMarkers(antennas: List<LocalisationEntity>): List<AntennaMarker> {
                val groupedSites = antennas.groupBy { "${it.latitude}_${it.longitude}" }.values.take(6000)

                return groupedSites.mapNotNull { siteAntennas ->
                    val filteredSiteAntennas = siteAntennas.mapNotNull { antenna ->
                        val activeOps = visibleOperatorKeysForAntenna(
                            antenna = antenna,
                            hsOperatorMap = hsOperatorMap,
                            showSitesInService = showSitesInService,
                            showSitesOutOfService = showSitesOutOfService,
                            selectedOperatorKeys = selectedOperators
                        )

                        if (activeOps.isEmpty()) null else visibleAntennaForMap(antenna, activeOps)
                    }
                    if (filteredSiteAntennas.isEmpty()) return@mapNotNull null

                    val mainAntenna = filteredSiteAntennas.first()
                    val isHs = filteredSiteAntennas.any { antenna ->
                        hasVisibleHsOperator(antenna, hsColorOperatorMap)
                    }

                    ensureMapNotDisposed()
                    AntennaMarker(map, filteredSiteAntennas, safePrimaryColor).apply {
                        position = GeoPoint(mainAntenna.latitude, mainAntenna.longitude)
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)

                        infoWindow = null // Pas de bulle grise par defaut

                        val operatorsOnSite = filteredSiteAntennas.mapNotNull { it.operateur }
                            .flatMap { OperatorColors.keysFor(it) }
                            .distinct()
                        relatedObject = operatorsOnSite

                        val baseIcon = MapUtils.createAdaptiveMarker(context, filteredSiteAntennas, false, AppConfig.defaultOperator.value)

                        if (isHs) {
                            icon = createHsMarkerIcon(context, baseIcon)
                        } else {
                            icon = baseIcon
                        }

                        setOnMarkerClickListener { _, _ ->
                            if (isMeasuringMode) {
                                addManualMeasurePoint(
                                    GeoPoint(mainAntenna.latitude, mainAntenna.longitude),
                                    startFromLocationIfFirst = myCurrentLoc != null || locationOverlayRef?.myLocation != null
                                )
                                refreshMeasureLayers(map)
                            } else {
                                openSupportDetailFromMap(map, mainAntenna)
                            }
                            true
                        }
                    }
                }
            }

            val clusterAntennas = antennasList.filter { it.idAnfr.startsWith("CLUSTER_") }
            val directAntennas = antennasList.filterNot { it.idAnfr.startsWith("CLUSTER_") }

            if (clusterAntennas.isNotEmpty()) {
                // ... (Ton code actuel MACRO reste identique)
                val clusterMarkers = clusterAntennas.map { fakeAntenna ->
                    val count = fakeAntenna.idAnfr.removePrefix("CLUSTER_").toIntOrNull() ?: 1
                    ensureMapNotDisposed()
                    org.osmdroid.views.overlay.Marker(map).apply {
                        position = GeoPoint(fakeAntenna.latitude, fakeAntenna.longitude)
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                        val activeOps = visibleOperatorKeysForAntenna(
                            antenna = fakeAntenna,
                            hsOperatorMap = hsOperatorMap,
                            showSitesInService = showSitesInService,
                            showSitesOutOfService = showSitesOutOfService,
                            selectedOperatorKeys = selectedOperators
                        )
                        icon = MapUtils.createClusterIcon(context, activeOps, count, AppConfig.defaultOperator.value)
                        setOnMarkerClickListener { clickedMarker, m ->
                            val targetPoint = org.osmdroid.util.GeoPoint(clickedMarker.position.latitude, clickedMarker.position.longitude)
                            m.post { m.controller.stopAnimation(false); m.controller.setZoom(m.zoomLevelDouble + 1.5); m.controller.setCenter(targetPoint) }
                            true
                        }
                    }
                }
                val directMarkers = buildAntennaMarkers(directAntennas)
                currentCoroutineContext().ensureActive()
                ensureMapNotDisposed()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!mapViewUsable.get() || mapViewRef !== map) return@withContext
                    markersOverlay.items.clear()
                    markersOverlay.items.addAll(directMarkers)
                    markersOverlay.invalidate()
                    macroOverlay.items.clear()
                    macroOverlay.items.addAll(clusterMarkers)
                    map.invalidate()
                }
            } else {
                // 🔍 MODE MICRO
                val groupedSites = antennasList.groupBy { "${it.latitude}_${it.longitude}" }.values.take(6000)

                // ✅ RETOUR À map : 1 seul marqueur définitif par pylône
                val newMarkers = groupedSites.mapNotNull { siteAntennas ->
                    val filteredSiteAntennas = siteAntennas.mapNotNull { antenna ->
                        val activeOps = visibleOperatorKeysForAntenna(
                            antenna = antenna,
                            hsOperatorMap = hsOperatorMap,
                            showSitesInService = showSitesInService,
                            showSitesOutOfService = showSitesOutOfService,
                            selectedOperatorKeys = selectedOperators
                        )

                        if (activeOps.isEmpty()) null else visibleAntennaForMap(antenna, activeOps)
                    }
                    if (filteredSiteAntennas.isEmpty()) return@mapNotNull null

                    val mainAntenna = filteredSiteAntennas.first()
                    val isHs = filteredSiteAntennas.any { antenna ->
                        hasVisibleHsOperator(antenna, hsColorOperatorMap)
                    }

                    // Le marqueur UNIQUE (L'antenne)
                    ensureMapNotDisposed()
                    AntennaMarker(map, filteredSiteAntennas, safePrimaryColor).apply {
                        position = GeoPoint(mainAntenna.latitude, mainAntenna.longitude)
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)

                        infoWindow = null // Pas de bulle grise par défaut

                        val operatorsOnSite = filteredSiteAntennas.mapNotNull { it.operateur }
                            .flatMap { OperatorColors.keysFor(it) }
                            .distinct()
                        relatedObject = operatorsOnSite

                        // 1. On génère l'icône de base (avec la bordure de couleur de l'opérateur)
                        val baseIcon = MapUtils.createAdaptiveMarker(context, filteredSiteAntennas, false, AppConfig.defaultOperator.value)

                        // 2. LOGIQUE DE FUSION : On vérifie TOUTES les antennes du pylône partagé !
                        if (isHs) {

                            val cachedHsIcon = createHsMarkerIcon(context, baseIcon)

                            // A. Création d'une "toile" vide de la taille de l'icône de base
                            icon = cachedHsIcon

                            // B. On dessine l'icône colorée de l'opérateur au fond
                            // C. On dessine le point d'exclamation parfaitement centré par-dessus
                            // D. On applique l'image fusionnée au marqueur
                        } else {
                            // Si pas en panne, on applique l'icône normale
                            icon = baseIcon
                        }

                        // L'action de clic reste unique et propre !
                        setOnMarkerClickListener { _, _ ->
                            if (isMeasuringMode) {
                                addManualMeasurePoint(
                                    GeoPoint(mainAntenna.latitude, mainAntenna.longitude),
                                    startFromLocationIfFirst = myCurrentLoc != null || locationOverlayRef?.myLocation != null
                                )
                                refreshMeasureLayers(map)
                            } else {
                                openSupportDetailFromMap(map, mainAntenna)
                            }
                            true
                        }
                    } // Fin du apply (retourne 1 seul marqueur)
                }

                currentCoroutineContext().ensureActive()
                ensureMapNotDisposed()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!mapViewUsable.get() || mapViewRef !== map) return@withContext
                    macroOverlay.items.clear()
                    markersOverlay.items.clear()
                    markersOverlay.items.addAll(newMarkers)
                    markersOverlay.invalidate()
                    map.invalidate()
                }
            }
        }
    }

    // On ajoute explicitement les 4 opérateurs dans les "déclencheurs" (keys)
    // Dès qu'une case est cochée/décochée, la carte sera forcée de se redessiner !
    LaunchedEffect(
        filteredAntennas,
        sitesHs, // ✅ AJOUT ICI
        isMeasuringMode,
        safePrimaryColor,
        AppConfig.showAzimuths.value,
        AppConfig.showAzimuthsCone.value,
        AppConfig.selectedOperatorKeys.value,
        AppConfig.showSitesInService.value,
        AppConfig.showSitesOutOfService.value,
        AppConfig.hideUndergroundSites.value,
        AppConfig.showOnlyZbSites.value,
        AppConfig.showTechnoFH.value,
        AppConfig.showTechno2G.value,
        AppConfig.showTechno3G.value,
        AppConfig.showTechno4G.value,
        AppConfig.showTechno5G.value,
        AppConfig.f2G_900.value,
        AppConfig.f2G_1800.value,
        AppConfig.f3G_900.value,
        AppConfig.f3G_2100.value,
        AppConfig.f4G_700.value,
        AppConfig.f4G_800.value,
        AppConfig.f4G_900.value,
        AppConfig.f4G_1800.value,
        AppConfig.f4G_2100.value,
        AppConfig.f4G_2600.value,
        AppConfig.f5G_700.value,
        AppConfig.f5G_1400.value,
        AppConfig.f5G_2100.value,
        AppConfig.f5G_3500.value,
        AppConfig.f5G_4200.value,
        AppConfig.f5G_26000.value
    ) {
        delay(MAP_MARKER_REDRAW_DEBOUNCE_MS)
        mapViewRef?.let { map ->
            updateMarkers(map, filteredAntennas, sitesHs)
        }
    }

    LaunchedEffect(radioMarkers, filteredAntennas, AppConfig.radioMapCategoryMask(), isMeasuringMode) {
        mapViewRef?.let { map ->
            updateRadioMarkers(map, radioMarkers)
        }
    }

    LaunchedEffect(signalQuestCoveragePoints) {
        signalQuestCoverageOverlay.setPoints(signalQuestCoveragePoints)
        mapViewRef?.invalidate()
    }

    LaunchedEffect(theoreticalCoverage, AppConfig.showTheoreticalCoverage.value) {
        if (AppConfig.showTheoreticalCoverage.value) {
            theoreticalCoverageOverlay.setCoverage(theoreticalCoverage)
        } else {
            theoreticalCoverageOverlay.clear()
        }
        mapViewRef?.invalidate()
    }

    // Déclenchement depuis une fiche site : calcule la couverture (le centrage se fait via last_map_* au montage).
    LaunchedEffect(AppConfig.pendingTheoreticalCoverage.value) {
        val pending = AppConfig.pendingTheoreticalCoverage.value ?: return@LaunchedEffect
        AppConfig.showTheoreticalCoverage.value = true
        android.widget.Toast.makeText(context, context.getString(R.string.appstrings_coverage_computing), android.widget.Toast.LENGTH_SHORT).show()
        viewModel.loadTheoreticalCoverageForSite(pending.idAnfr)
        AppConfig.pendingTheoreticalCoverage.value = null
    }

    // Diagnostic temporaire à l'écran (en attendant l'écran-outil du Lot 3) : résumé du calcul.
    LaunchedEffect(theoreticalCoverage) {
        val cov = theoreticalCoverage ?: return@LaunchedEffect
        val complete = cov.terrainValidFraction >= 0.9 && cov.terrainFailedChunks == 0
        val message = if (complete) {
            // Succès : rappel scientifique (ligne de visée, pas couverture RF) en attendant le disclaimer Lot 3.
            context.getString(R.string.appstrings_coverage_disclaimer_short)
        } else {
            "Couverture partielle : terrain ${(cov.terrainValidFraction * 100).toInt()}% · " +
                "${cov.terrainFailedChunks}/${cov.terrainTotalChunks} req KO" +
                (cov.terrainSampleError?.let { " — $it" } ?: "")
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(
        canUseSignalQuestCoverage,
        AppConfig.showSignalQuestCoveragePoints.value,
        AppConfig.selectedSignalQuestCoverageOperatorKeys.value
    ) {
        mapViewRef?.loadVisibleSignalQuestCoverage(viewModel, canUseSignalQuestCoverage)
    }

    LaunchedEffect(AppConfig.radioMapCategoryMask()) {
        mapViewRef?.let { map ->
            map.loadVisibleAntennas(viewModel)
        }
    }

    LaunchedEffect(mapViewRef, currentSearchAreaBoundsEncoded, currentCityPolygonsEncoded) {
        mapViewRef?.let { map ->
            refreshSearchBoundaryOverlay(map, currentCityPolygons)
            loadCurrentCitySearchIfNeeded()
        }
    }

    LaunchedEffect(
        sitesHs,
        AppConfig.showSitesInService.value,
        AppConfig.showSitesOutOfService.value,
        AppConfig.hideUndergroundSites.value,
        AppConfig.showOnlyZbSites.value,
        AppConfig.showTechnoFH.value,
        AppConfig.showTechno2G.value,
        AppConfig.showTechno3G.value,
        AppConfig.showTechno4G.value,
        AppConfig.showTechno5G.value,
        AppConfig.f2G_900.value,
        AppConfig.f2G_1800.value,
        AppConfig.f3G_900.value,
        AppConfig.f3G_2100.value,
        AppConfig.f4G_700.value,
        AppConfig.f4G_800.value,
        AppConfig.f4G_900.value,
        AppConfig.f4G_1800.value,
        AppConfig.f4G_2100.value,
        AppConfig.f4G_2600.value,
        AppConfig.f5G_700.value,
        AppConfig.f5G_1400.value,
        AppConfig.f5G_2100.value,
        AppConfig.f5G_3500.value,
        AppConfig.f5G_4200.value,
        AppConfig.f5G_26000.value
    ) {
        mapViewRef?.let { map ->
            map.loadVisibleAntennas(viewModel)
        }
    }

    LaunchedEffect(myCurrentLoc) {
        if (isMeasuringMode && (measuredSites.isNotEmpty() || measuredMapPoints.isNotEmpty())) {
            mapViewRef?.let { refreshMeasureLayers(it) }
        }
    }

    LaunchedEffect(isMeasuringMode, measuredMapPoints.size, measuredMapPointLocationLinks.size, mapViewRef) {
        if (isMeasuringMode && measuredMapPoints.isNotEmpty()) {
            mapViewRef?.let { refreshMeasureLayers(it) }
        }
    }

    val currentFilteredAntennas by androidx.compose.runtime.rememberUpdatedState(filteredAntennas)
    val currentLoc by androidx.compose.runtime.rememberUpdatedState(myCurrentLoc)

    // =====================================================================
    // ✅ CORRECTION : MOTEUR DE SUIVI SANS "TRAITS FANTÔMES"
    // =====================================================================
    LaunchedEffect(Unit) {
        var lastTrackedAllId: String? = null
        var lastTrackedFavId: String? = null

        while (true) {
            // On lit l'état actuel des boutons en temps réel à l'intérieur de la boucle
            val isAllActive = trackNearestAll
            val isFavActive = trackNearestFav

            if (isAllActive || isFavActive) {
                val myLoc = currentLoc ?: locationOverlayRef?.myLocation

                if (myLoc != null && currentFilteredAntennas.isNotEmpty()) {
                    var needsRefresh = false

                    // --- SUIVI 1 : LE PLUS PROCHE GLOBAL ---
                    if (isAllActive) {
                        val nearestAll = currentFilteredAntennas.minByOrNull {
                            myLoc.distanceToAsDouble(GeoPoint(it.latitude, it.longitude))
                        }
                        val targetId = nearestAll?.idAnfr

                        // Si la cible a changé
                        if (targetId != lastTrackedAllId) {
                            // On efface l'ancienne cible (SEULEMENT si l'autre suivi ne l'utilise pas !)
                            if (lastTrackedAllId != null && lastTrackedAllId != lastTrackedFavId) {
                                measuredSites.remove(lastTrackedAllId)
                            }
                            // On ajoute la nouvelle cible
                            nearestAll?.let { measuredSites[it.idAnfr] = it }
                            lastTrackedAllId = targetId
                            needsRefresh = true
                        }
                    } else if (lastTrackedAllId != null) {
                        // Si on vient de désactiver le suivi Global
                        if (lastTrackedAllId != lastTrackedFavId) {
                            measuredSites.remove(lastTrackedAllId)
                        }
                        lastTrackedAllId = null
                        needsRefresh = true
                    }

                    // --- SUIVI 2 : LE PLUS PROCHE OPÉRATEUR PRÉFÉRÉ ---
                    if (isFavActive) {
                        val opQuery = OperatorColors.keyFor(AppConfig.defaultOperator.value)
                        val nearestFav = currentFilteredAntennas
                            .filter { opQuery != null && OperatorColors.keysFor(it.operateur).contains(opQuery) }
                            .minByOrNull { myLoc.distanceToAsDouble(GeoPoint(it.latitude, it.longitude)) }

                        val targetId = nearestFav?.idAnfr

                        // Si la cible a changé
                        if (targetId != lastTrackedFavId) {
                            if (lastTrackedFavId != null && lastTrackedFavId != lastTrackedAllId) {
                                measuredSites.remove(lastTrackedFavId)
                            }
                            // On ajoute la nouvelle cible
                            nearestFav?.let { measuredSites[it.idAnfr] = it }
                            lastTrackedFavId = targetId
                            needsRefresh = true
                        }
                    } else if (lastTrackedFavId != null) {
                        // Si on vient de désactiver le suivi Fav
                        if (lastTrackedFavId != lastTrackedAllId) {
                            measuredSites.remove(lastTrackedFavId)
                        }
                        lastTrackedFavId = null
                        needsRefresh = true
                    }

                    // Si quelque chose a changé, on redessine !
                    if (needsRefresh) {
                        mapViewRef?.let { refreshMeasureLayers(it) }
                    }
                }
            } else {
                // --- NETTOYAGE COMPLET SI ON ÉTEINT TOUT ---
                if (lastTrackedAllId != null || lastTrackedFavId != null) {
                    if (lastTrackedAllId != null) measuredSites.remove(lastTrackedAllId)
                    if (lastTrackedFavId != null) measuredSites.remove(lastTrackedFavId)
                    lastTrackedAllId = null
                    lastTrackedFavId = null
                    mapViewRef?.let { refreshMeasureLayers(it) }
                }
            }
            delay(1000L) // Mise à jour toutes les secondes
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rawPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
        val isColorTooLight = ColorUtils.calculateLuminance(rawPrimaryColor) > 0.85

        val safePrimaryColor = if (isColorTooLight) {
            android.graphics.Color.parseColor("#2196F3")
        } else {
            rawPrimaryColor
        }

        LaunchedEffect(isColorTooLight) {
            if (isColorTooLight && !hideColorWarning) {
                showColorWarningDialog = true
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    mapViewUsable.set(true)
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            mapViewUsable.set(true)
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                            mapViewUsable.set(false)
                        }
                    })

                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    } else {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    }

                    setMultiTouchControls(true)
                    enableMouseWheelZoom()
                    applyWorldMapBounds()
                    addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                            (view as? MapView)?.applyWorldMapBounds()
                        }
                    }
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    val prefs = ctx.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                    val hasSavedPosition = hasSavedMapPosition(prefs)

                    controller.setCenter(GeoPoint(
                        prefs.getFloat("last_map_lat", 46.2276f).toDouble(),
                        prefs.getFloat("last_map_lon", 2.2137f).toDouble()
                    ))

                    controller.setZoom(prefs.getFloat("last_map_zoom", 6.0f).toDouble())

                    val locationOverlay = object : CustomLocationOverlay(GpsMyLocationProvider(ctx), this, safePrimaryColor) {
                        override fun onLocationChanged(location: android.location.Location?, source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?) {
                            super.onLocationChanged(location, source)
                            if (location != null) {
                                myCurrentLoc = GeoPoint(location.latitude, location.longitude)
                                if (location.hasSpeed()) {
                                    currentSpeedKmH = (location.speed * 3.6f).toInt()
                                } else {
                                    currentSpeedKmH = 0
                                }
                            }
                        }
                    }
                    locationOverlay.setEnableAutoStop(false)
                    locationOverlay.showLocationMarker = AppConfig.showMapLocationMarker.value
                    locationOverlay.enableMyLocation()

                    locationOverlay.runOnFirstFix {
                        val initialLoc = locationOverlay.myLocation
                        if (initialLoc != null) {
                            post {
                                myCurrentLoc = initialLoc
                                if (!hasSavedPosition) {
                                    controller.stopAnimation(false)
                                    controller.setZoom(INITIAL_LOCATION_ZOOM)
                                    controller.setCenter(initialLoc)
                                    currentZoom = INITIAL_LOCATION_ZOOM
                                    currentLat = initialLoc.latitude

                                    prefs.edit()
                                        .putFloat("last_map_lat", initialLoc.latitude.toFloat())
                                        .putFloat("last_map_lon", initialLoc.longitude.toFloat())
                                        .putFloat("last_map_zoom", INITIAL_LOCATION_ZOOM.toFloat())
                                        .apply()
                                }
                            }
                        }
                    }

                    // ✅ ORDONNANCEMENT DES CALQUES
                    overlays.add(measureTapOverlay)
                    overlays.add(measureOverlay)
                    overlays.add(searchBoundaryOverlay)
                    overlays.add(macroOverlay) // <-- Calque macro au fond
                    overlays.add(theoreticalCoverageOverlay) // <-- Enveloppe couverture théorique (sous les points/marqueurs)
                    overlays.add(signalQuestCoverageOverlay)
                    overlays.add(radioOverlay)
                    overlays.add(markersOverlay) // <-- Calque micro au milieu
                    overlays.add(locationOverlay) // <-- Curseur devant

                    locationOverlayRef = locationOverlay

                    var lastRadius = 250
                    var lastLoadedViewport: MapViewportSnapshot? = null
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            // La poursuite GPS garde le controle pendant les scrolls generes par le suivi.
                            updateInfo()
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean { updateInfo(); return true }
                        private fun updateInfo() {
                            // ✅ 1. MISE À JOUR INSTANTANÉE POUR L'ÉCHELLE (Avant le delay !)
                            // ✅ 2. LE RESTE DU CALCUL AVEC SON PETIT DÉLAI ANTI-LAG
                            searchJob?.cancel()
                            searchJob = scope.launch {
                                delay(MAP_RELOAD_DEBOUNCE_MS)

                                val snapshot = visibleViewportSnapshot()
                                currentZoom = snapshot.zoom
                                currentLat = snapshot.centerLat

                                val z = snapshot.zoom

                                // ---> AIMANT PLUS FORT POUR LES ZONES DENSES <---
                                val targetRadius = when {
                                    z < 14.0 -> 220 // Attraction très forte pour Paris quand on vient de passer en mode "Vraies antennes"
                                    z < 15.5 -> 150 // Attraction moyenne
                                    z < 17.0 -> 90  // Attraction faible
                                    else -> 60      // Pratiquement aucune attraction (on voit tous les pylônes distincts)
                                }
                                if (targetRadius != lastRadius) {
                                    lastRadius = targetRadius
                                    markersOverlay.setRadius(targetRadius)
                                    markersOverlay.invalidate()
                                    mapViewRef?.invalidate()
                                }

                                if (lastLoadedViewport?.isCloseTo(snapshot) == true) {
                                    return@launch
                                }
                                lastLoadedViewport = snapshot
                                this@apply.loadVisibleAntennas(viewModel)
                                this@apply.loadVisibleSignalQuestCoverage(viewModel, canUseSignalQuestCoverage)
                            }
                        }
                    })
                    post {
                        val snapshot = visibleViewportSnapshot()
                        lastLoadedViewport = snapshot
                        currentZoom = snapshot.zoom
                        currentLat = snapshot.centerLat
                        this@apply.loadVisibleAntennas(viewModel)
                        this@apply.loadVisibleSignalQuestCoverage(viewModel, canUseSignalQuestCoverage)
                    }
                    mapViewRef = this
                }
            },
            update = { map ->
                var shouldInvalidateMap = false

                // Mise à jour de la boussole (ton code actuel)
                (locationOverlayRef as? CustomLocationOverlay)?.let { overlay ->
                    overlay.currentCompassAzimuth = azimuth
                    if (overlay.showLocationMarker != showLocationMarker) {
                        overlay.showLocationMarker = showLocationMarker
                        shouldInvalidateMap = true
                    }
                }

                // 🗺️ LOGIQUE HORS-LIGNE
                if (effectiveProvider == 4) {
                    if (mapFiles.isNotEmpty()) {
                        if (map.tileProvider !is MapsForgeTileProvider) {

                            // On tente de charger le magnifique thème Elevate
                            runCatching {
                                val renderTheme = try {
                                AssetsRenderTheme(context.assets, "themes/", "freizeitkarte-v5.xml")
                            } catch (e: Exception) {
                                // S'il manque, on repasse sur le thème par défaut pour ne pas planter
                                InternalRenderTheme.OSMARENDER
                            }

                            val forgeSource = MapsForgeTileSource.createFromFiles(
                                mapFiles,
                                renderTheme,
                                "geotower_internal_theme"
                            )
                            val forgeProvider = MapsForgeTileProvider(
                                org.osmdroid.tileprovider.util.SimpleRegisterReceiver(context),
                                forgeSource,
                                null
                            )
                                map.tileProvider = forgeProvider
                                shouldInvalidateMap = true
                            }.onFailure {
                                mapFiles = emptyArray()
                                effectiveProvider = 1
                                AppConfig.mapProvider.value = 1
                                if (map.tileProvider is MapsForgeTileProvider) {
                                    map.tileProvider = MapTileProviderBasic(context)
                                    shouldInvalidateMap = true
                                }
                                runCatching {
                                    map.setTileSource(MapUtils.OSM_Source)
                                    shouldInvalidateMap = true
                                }
                            }
                        }
                    } else {
                        AppConfig.mapProvider.value = 1
                    }
                } else {
                    // 🌐 LOGIQUE EN LIGNE
                    if (map.tileProvider is MapsForgeTileProvider) {
                        map.tileProvider = MapTileProviderBasic(context)
                        shouldInvalidateMap = true
                    }

                    // ⚠️ ATTENTION : on utilise bien "effectiveProvider" ici !
                    val newSource = when (effectiveProvider) {
                        1 -> MapUtils.OSM_Source
                        2 -> if (ignStyle == 1) {
                            org.osmdroid.tileprovider.tilesource.XYTileSource("MapLibreDark", 1, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/dark_all/"))
                        } else {
                            org.osmdroid.tileprovider.tilesource.XYTileSource("MapLibre", 1, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"))
                        }
                        3 -> org.osmdroid.tileprovider.tilesource.TileSourceFactory.OpenTopo
                        else -> if (ignStyle == 2) MapUtils.IgnSource.SATELLITE else MapUtils.IgnSource.PLAN_IGN
                    }

                    if (map.tileProvider.tileSource.name() != newSource.name()) {
                        map.setTileSource(newSource)
                        shouldInvalidateMap = true
                    }
                }

                if (lastTilesColorFilterMap[0] !== map || lastTilesColorFilterInverted[0] != shouldInvertColors) {
                    map.overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)
                    lastTilesColorFilterMap[0] = map
                    lastTilesColorFilterInverted[0] = shouldInvertColors
                    shouldInvalidateMap = true
                }

                if (shouldInvalidateMap) {
                    map.invalidate()
                }
            }
        )

        fun performSearch(query: String) {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                restoreOperatorSearchSelection()
                return
            }

            val searchedOperatorKeys = parseOperatorSearchKeys(cleanQuery)
            if (searchedOperatorKeys.isNotEmpty()) {
                applyOperatorSearchSelection(searchedOperatorKeys.toSet())
                return
            }
            restoreOperatorSearchSelection()

            // 1. Recherche Rapide Locale (si l'antenne est déjà affichée à l'écran)
            val foundSite = antennas.find { it.idAnfr == cleanQuery }
            if (foundSite != null) {
                mapViewRef?.controller?.setZoom(18.0)
                mapViewRef?.controller?.setCenter(GeoPoint(foundSite.latitude, foundSite.longitude))
                return
            }

            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // 2. ✅ NOUVEAU : Recherche Globale d'ID (Base de données entière)
                val hasDigits = cleanQuery.any { it.isDigit() }
                if (hasDigits && cleanQuery.length >= 3) {
                    val globalSite = viewModel.searchSiteById(cleanQuery)

                    if (globalSite != null) {
                        // On a trouvé le site ! On déplace la caméra.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            mapViewRef?.controller?.setZoom(18.0)
                            mapViewRef?.controller?.setCenter(GeoPoint(globalSite.latitude, globalSite.longitude))
                        }
                        return@launch // On arrête ici, pas besoin de chercher une ville

                    } else if (cleanQuery.all { it.isDigit() }) {
                        // Si l'utilisateur n'a tapé QUE des chiffres et qu'on n'a rien trouvé en base,
                        // inutile d'aller chercher sur internet (Nominatim).
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, resources.getString(R.string.map_site_not_in_area, cleanQuery), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                // 3. Recherche de Ville / Adresse via internet (Nominatim)
                val nominatimArea = NominatimApi.searchArea(cleanQuery)
                if (nominatimArea != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        mapViewRef?.let { map ->
                            val searchBounds = SearchAreaBounds(
                                latNorth = nominatimArea.latNorth,
                                lonEast = nominatimArea.lonEast,
                                latSouth = nominatimArea.latSouth,
                                lonWest = nominatimArea.lonWest
                            )
                            val searchPolygons = nominatimArea.polygons.map { polygon ->
                                polygon.map { point -> GeoPoint(point.latitude, point.longitude) }
                            }

                            if (searchPolygons.isNotEmpty()) {
                                setCurrentCitySearch(searchBounds, searchPolygons)
                                refreshSearchBoundaryOverlay(map, searchPolygons)
                                loadCurrentCitySearchIfNeeded(force = true)
                                showCityStatsPopup = true
                            } else {
                                setCurrentCitySearch(null, null)
                                refreshSearchBoundaryOverlay(map, null)
                            }

                            val cityBounds = org.osmdroid.util.BoundingBox(
                                nominatimArea.latNorth,
                                nominatimArea.lonEast,
                                nominatimArea.latSouth,
                                nominatimArea.lonWest
                            )
                            map.zoomToBoundingBox(cityBounds, true, 100)
                            map.invalidate()
                        }
                    }
                    return@launch
                }

                try {
                    val geocoder = android.location.Geocoder(context)
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(cleanQuery, 1)

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!results.isNullOrEmpty()) {
                            val addr = results[0]
                            if (GeoTowerDataCoverage.isKnownUnsupportedCountryCode(addr.countryCode)) {
                                Toast.makeText(context, txtSearchDataUnavailable, Toast.LENGTH_LONG).show()
                                return@withContext
                            }
                            mapViewRef?.let { map ->
                                setCurrentCitySearch(null, null)
                                searchBoundaryOverlay.items.clear()
                                map.controller.setZoom(15.0)
                                map.controller.setCenter(GeoPoint(addr.latitude, addr.longitude))
                                map.invalidate()
                            }
                        } else {
                            Toast.makeText(context, txtLocationNotFound, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, txtNetworkErrorSearch, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // ✅ 1. OUVRE LA CONDITION ICI POUR PROTÉGER TA VRAIE INTERFACE
        val isLandscapeLayout = maxWidth > maxHeight ||
            configuration.screenWidthDp > configuration.screenHeightDp

        // En paysage, la toolbox ne se déplie à l'horizontale (avec la barre de
        // recherche à côté en bas) que sur les écrans courts type téléphone, où
        // une toolbox verticale dépliée ne tiendrait pas en hauteur. Dès qu'on a
        // la hauteur d'une tablette (≥ 600dp, breakpoint "large" Android), on
        // garde la disposition portrait : toolbox verticale et recherche en haut.
        val toolboxExpandsLeft = isLandscapeLayout && maxHeight < 600.dp

        if (!isUltraCompact) {

            AnimatedVisibility(
                visible = isSearchActive && !toolboxExpandsLeft,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                .padding(start = 16.dp, end = 16.dp, top = 110.dp)
        ) {
            MapSearchBar(
                query = searchQuery,
                placeholder = txtSearchCityOrId,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    performSearch(searchQuery)
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        val compassTopPadding by animateDpAsState(
            targetValue = if (isSearchActive) 186.dp else 112.dp,
            label = "compassAnim"
        )
        val toolsTopPadding by animateDpAsState(
            targetValue = if (isSearchActive) 250.dp else 176.dp,
            label = "toolsAnim"
        )
        val useCompactCompassPlacement = configuration.screenHeightDp < 600
        val showCompassInMapHeader = showCompass && AppConfig.hasCompass.value && isLandscapeLayout
        val compassEndPadding by animateDpAsState(
            targetValue = if (isLandscapeLayout && !useCompactCompassPlacement) {
                (maxWidth * 0.12f).coerceIn(144.dp, 320.dp)
            } else {
                16.dp
            },
            label = "compassEndAnim"
        )
        val showCompactCompass = showCompass && AppConfig.hasCompass.value &&
            useCompactCompassPlacement && !showCompassInMapHeader
        val compactCompassHeight = if (showCompactCompass) MapControlButtonDiameter else 0.dp
        val visibleToolboxActionCount = listOf(
            canUseMapSearch,
            canUseMapMeasure,
            timeSliderAvailable, // bouton historique / slider temporel
            canUseLayerSelector,
            true
        ).count { it }
        val toolboxHeight = when {
            !showToolbox -> 0.dp
            toolboxExpandsLeft -> MapControlButtonDiameter
            isToolboxExpanded -> MapControlButtonDiameter +
                7.dp +
                (MapControlButtonDiameter * visibleToolboxActionCount.toFloat()) +
                1.dp +
                (visibleToolboxActionCount * 12).dp
            else -> MapControlButtonDiameter
        }
        val zoomControlsHeight = if (showZoomBtns) 117.dp else 0.dp
        val locationButtonHeight = if (showLocationBtn && canUseMapLocation) MapControlButtonDiameter else 0.dp
        val defaultOp by AppConfig.defaultOperator
        val trackingButtonHeight = if (isLandscapeLayout) MapControlButtonDiameter else 40.dp
        val trackingButtonSpacing = if (isLandscapeLayout) 1.dp else 8.dp
        val trackingRowCount = if (defaultOp != "Aucun") 2 else 1
        val trackingDrawerHeight = (trackingButtonHeight * trackingRowCount.toFloat()) +
            (trackingButtonSpacing * (trackingRowCount - 1).toFloat())
        val zoomBottomPadding = 32.dp +
            if (showLocationBtn && canUseMapLocation) {
                MapControlButtonDiameter + 16.dp
            } else {
                0.dp
            }
        val trackingDrawerLandscapeBottomPadding = zoomBottomPadding +
            ((zoomControlsHeight - trackingDrawerHeight) / 2f).coerceAtLeast(0.dp)
        val visibleRightControlGroups = listOf(
            showCompactCompass,
            showToolbox,
            showZoomBtns,
            showLocationBtn && canUseMapLocation
        ).count { it }
        val rightControlsHeight = compactCompassHeight +
            toolboxHeight +
            zoomControlsHeight +
            locationButtonHeight +
            ((visibleRightControlGroups - 1).coerceAtLeast(0) * 16).dp
        val measureDrawerBottomPadding by animateDpAsState(
            targetValue = 40.dp + rightControlsHeight,
            label = "measureDrawerBottomAnim"
        )
        // Sur tablette en paysage, le tiroir de suivi (« site le plus proche »)
        // est déporté en haut à gauche, dans la zone dégagée sous les boutons
        // retour/partage, pour ne pas chevaucher la toolbox et le menu à droite.
        val trackingDrawerTopLeft = isLandscapeLayout && maxHeight >= 600.dp
        val measureDrawerModifier = if (trackingDrawerTopLeft) {
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = compassTopPadding + 70.dp)
        } else if (!useCompactCompassPlacement) {
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = measureDrawerBottomPadding)
                .navigationBarsPadding()
        } else if (isLandscapeLayout && showZoomBtns) {
            Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp + MapControlButtonDiameter + 12.dp,
                    bottom = trackingDrawerLandscapeBottomPadding
                )
                .navigationBarsPadding()
        } else {
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = toolsTopPadding)
        }

        // ✅ NOUVEAU : Bouton de Partage positionné sous le bouton Retour avec animation
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = compassTopPadding)
        ) {
            val isMi = fr.geotower.utils.AppConfig.distanceUnit.intValue == 1
            val speedText = if (isMi) "${(currentSpeedKmH / 1.60934).toInt()} mph" else "$currentSpeedKmH km/h"

            fr.geotower.ui.components.MapShareMenu(
                useOneUi = fr.geotower.utils.AppConfig.useOneUiDesign,
                globalMapRef = mapViewRef,
                currentSpeed = speedText,
                currentZoom = currentZoom,
                currentLat = currentLat,
                azimuth = azimuth,
                measureOverlay = measureOverlay,
                timeSliderDateLabel = if (isTimeSliderVisible) timeSliderThreshold?.let { timeSliderMonthLabel(it) } else null
            )
        }

        if (showCompass && AppConfig.hasCompass.value && !useCompactCompassPlacement && !showCompassInMapHeader) {
            MapCompassButton(
                azimuth = azimuth,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = compassEndPadding, top = compassTopPadding)
                    .size(MapControlButtonDiameter)
            ) {
                safeClick {
                    mapViewRef?.mapOrientation = 0f
                    mapViewRef?.invalidate()
                }
            }
        }

        val darkMaterialColor = Color(0xFF37474F)
        val opColor = OperatorColors.keyFor(defaultOp)
            ?.let { Color(OperatorColors.colorArgbForKey(it)) }
            ?: MaterialTheme.colorScheme.primary

        // --- LES BOUTONS DE SUIVI (MODE MESURE) ---
        // --- LES BOUTONS DE SUIVI "A TIROIR" (MODE MESURE) ---
        AnimatedVisibility(
            visible = isMeasuringMode,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (trackingDrawerTopLeft) -it else it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { if (trackingDrawerTopLeft) -it else it }),
            modifier = measureDrawerModifier
        ) {
            Column(
                horizontalAlignment = if (trackingDrawerTopLeft) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.spacedBy(trackingButtonSpacing)
            ) {
                // ========================================================
                // 1. TIROIR SUIVI GLOBAL
                // ========================================================
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // La petite barre verticale cliquable (le toggle / poignée)
                    val handle: @Composable () -> Unit = {
                        Box(
                            modifier = Modifier
                                .height(trackingButtonHeight).width(12.dp)
                                // ✅ Bords arrondis de tous les côtés pour la poignée
                                .background(darkMaterialColor, RoundedCornerShape(6.dp))
                                .clickable { safeClick { isClosestSiteExpanded = !isClosestSiteExpanded } }
                        )
                    }

                    // Le contenu du tiroir (le bouton)
                    val pill: @Composable () -> Unit = {
                        AnimatedVisibility(
                            visible = isClosestSiteExpanded,
                            enter = expandHorizontally(expandFrom = if (trackingDrawerTopLeft) Alignment.Start else Alignment.End) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = if (trackingDrawerTopLeft) Alignment.Start else Alignment.End) + fadeOut()
                        ) {
                            Button(
                                onClick = { safeClick { trackNearestAll = !trackNearestAll } },
                                modifier = Modifier.height(trackingButtonHeight).width(210.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                // ✅ NOUVEAU : Forme de pilule parfaite
                                shape = RoundedCornerShape(trackingButtonHeight / 2f),
                                colors = ButtonDefaults.buttonColors(containerColor = darkMaterialColor, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.NearMe, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (trackNearestAll) stringResource(R.string.appstrings_track_global_active) else txtClosestSite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Tiroir à gauche (tablette) : bouton collé au bord, poignée à
                    // l'intérieur. Ailleurs : poignée d'abord, bouton vers le bord droit.
                    if (trackingDrawerTopLeft) {
                        pill()
                        Spacer(modifier = Modifier.width(6.dp))
                        handle()
                    } else {
                        handle()
                        Spacer(modifier = Modifier.width(6.dp))
                        pill()
                    }
                }

                // ========================================================
                // 2. TIROIR SUIVI OPÉRATEUR PRÉFÉRÉ
                // ========================================================
                if (defaultOp != "Aucun") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // La petite barre verticale cliquable (le toggle / poignée)
                        val handle: @Composable () -> Unit = {
                            Box(
                                modifier = Modifier
                                    .height(trackingButtonHeight).width(12.dp)
                                    // ✅ Bords arrondis de tous les côtés
                                    .background(opColor, RoundedCornerShape(6.dp))
                                    .clickable { safeClick { isClosestFavSiteExpanded = !isClosestFavSiteExpanded } }
                            )
                        }

                        // Le contenu du tiroir (le bouton)
                        val pill: @Composable () -> Unit = {
                            AnimatedVisibility(
                                visible = isClosestFavSiteExpanded,
                                enter = expandHorizontally(expandFrom = if (trackingDrawerTopLeft) Alignment.Start else Alignment.End) + fadeIn(),
                                exit = shrinkHorizontally(shrinkTowards = if (trackingDrawerTopLeft) Alignment.Start else Alignment.End) + fadeOut()
                            ) {
                                Button(
                                    onClick = { safeClick { trackNearestFav = !trackNearestFav } },
                                    modifier = Modifier.height(trackingButtonHeight).width(210.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    // ✅ NOUVEAU : Forme de pilule parfaite
                                    shape = RoundedCornerShape(trackingButtonHeight / 2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = opColor, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (trackNearestFav) stringResource(R.string.track_operator_active, defaultOp) else "$txtClosestSite $defaultOp",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )                            }
                            }
                        }

                        // Tiroir à gauche (tablette) : bouton collé au bord, poignée à
                        // l'intérieur. Ailleurs : poignée d'abord, bouton vers le bord droit.
                        if (trackingDrawerTopLeft) {
                            pill()
                            Spacer(modifier = Modifier.width(6.dp))
                            handle()
                        } else {
                            handle()
                            Spacer(modifier = Modifier.width(6.dp))
                            pill()
                        }
                    }
                }
            }
        }


        Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SmallFloatingButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    desc = stringResource(R.string.appstrings_back),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    if (isSharedPhotoSelectionMode) {
                        cancelSharedPhotoSelection()
                    } else {
                        restoreOperatorSearchSelection()
                        safeBackNavigation.navigateBack()
                    }
                }
                Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text(txtMapTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCompassInMapHeader) {
                        MapCompassButton(
                            azimuth = azimuth,
                            modifier = Modifier.size(MapControlButtonDiameter)
                        ) {
                            safeClick {
                                mapViewRef?.mapOrientation = 0f
                                mapViewRef?.invalidate()
                            }
                        }
                    }

                    SmallFloatingButton(
                        icon = Icons.Default.Menu,
                        desc = txtFilter
                    ) { safeClick { showSettingsSheet = true } }
                }
            }

            AnimatedVisibility(
                visible = activeMapFilterSummary != null && !isSearchActive && !isSharedPhotoSelectionMode,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                ActiveMapFiltersBanner(
                    summary = activeMapFilterSummary.orEmpty(),
                    modifier = Modifier
                        .padding(start = 88.dp, top = 10.dp, end = 88.dp)
                        .fillMaxWidth()
                )
            }

            val deleteButtonSpacer by animateDpAsState(
                targetValue = if (isSearchActive) 93.dp else 19.dp,
                label = "deleteButtonAnim"
            )
            Spacer(modifier = Modifier.height(deleteButtonSpacer))

            AnimatedVisibility(
                visible = measuredSites.isNotEmpty() || measuredMapPoints.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = {
                        // ✅ CORRECTION : On coupe tout !
                        trackNearestAll = false
                        trackNearestFav = false
                        clearMeasureSelections()
                        mapViewRef?.let { refreshMeasureLayers(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(txtDeleteTraces, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isSharedPhotoSelectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 112.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shared_photo_map_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.shared_photo_map_desc, pendingSharedPhotoCount),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                    IconButton(onClick = { safeClick { cancelSharedPhotoSelection() } }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.appstrings_cancel),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 32.dp, end = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showCompactCompass) {
                MapCompassButton(
                    azimuth = azimuth,
                    modifier = Modifier.size(MapControlButtonDiameter)
                ) {
                    safeClick {
                        mapViewRef?.mapOrientation = 0f
                        mapViewRef?.invalidate()
                    }
                }
            }

            if (showToolbox) {
                val toolboxContent: @Composable () -> Unit = {
                AntennaMapToolBox(
                    isToolboxExpanded = isToolboxExpanded,
                    onToggleToolbox = {
                        // On se contente de replier/déplier la toolbox : les éléments
                        // actifs (recherche, filtre ville, mesure, suivi, time slider…)
                        // restent ouverts. Leur fermeture propre passe désormais
                        // uniquement par leurs boutons respectifs.
                        isToolboxExpanded = !isToolboxExpanded
                    },
                    isSearchActive = isSearchActive,
                    onToggleSearch = {
                        if (canUseMapSearch) {
                            isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            restoreOperatorSearchSelection()
                            searchQuery = ""
                            setCurrentCitySearch(null, null)
                            searchBoundaryOverlay.items.clear()

                            // ✅ CORRECTION DU NOM ET APPEL AVEC LE ZOOM
                            mapViewRef?.let { map ->
                                map.clearCityFilterAndReloadVisible(viewModel)
                            }

                            mapViewRef?.invalidate()
                        }
                        }
                    },
                    isMeasuringMode = isMeasuringMode,
                    onToggleMeasure = {
                        if (canUseMapMeasure) {
                            isMeasuringMode = !isMeasuringMode
                            if (!isMeasuringMode) {
                                trackNearestAll = false
                                trackNearestFav = false
                                clearMeasureSelections()
                                mapViewRef?.let { refreshMeasureLayers(it) }
                            }
                        }
                    },
                    isTimeSliderActive = isTimeSliderVisible,
                    onToggleTimeSlider = {
                        isTimeSliderVisible = !isTimeSliderVisible
                        AppConfig.timeSliderActive.value = isTimeSliderVisible
                        if (isTimeSliderVisible) {
                            viewModel.ensureOldestServiceDateLoaded()
                        } else {
                            timeSliderThreshold = null
                        }
                        mapViewRef?.loadVisibleAntennas(viewModel)
                    },
                    onOpenLayers = { safeClick { if (canUseLayerSelector) showLayerSheet = true } },
                    onOpenSettings = { safeClick { showMapPageSettingsSheet = true } },
                    showSearch = canUseMapSearch,
                    showMeasure = canUseMapMeasure,
                    showTimeSlider = timeSliderAvailable,
                    showLayers = canUseLayerSelector,
                    expandLeft = toolboxExpandsLeft
                )
                }

                if (toolboxExpandsLeft && isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MapSearchBar(
                            query = searchQuery,
                            placeholder = txtSearchCityOrId,
                            onQueryChange = { searchQuery = it },
                            onSearch = {
                                performSearch(searchQuery)
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        toolboxContent()
                    }
                } else {
                    toolboxContent()
                }
            }

            if (showZoomBtns) {
                val zoomControlShape = RoundedCornerShape(MapControlButtonDiameter / 2f)
                Surface(
                    modifier = Modifier
                        .width(MapControlButtonDiameter)
                        .clip(zoomControlShape),
                    shape = zoomControlShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(zoomControlShape)
                    ) {
                        ZoomControlSegmentButton(
                            icon = Icons.Default.Add,
                            shape = RoundedCornerShape(
                                topStart = MapControlButtonDiameter / 2f,
                                topEnd = MapControlButtonDiameter / 2f,
                                bottomStart = 10.dp,
                                bottomEnd = 10.dp
                            ),
                            onClick = { mapViewRef?.controller?.zoomIn() }
                        )
                        HorizontalDivider(
                            modifier = Modifier.width(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                        ZoomControlSegmentButton(
                            icon = Icons.Default.Remove,
                            shape = RoundedCornerShape(
                                topStart = 10.dp,
                                topEnd = 10.dp,
                                bottomStart = MapControlButtonDiameter / 2f,
                                bottomEnd = MapControlButtonDiameter / 2f
                            ),
                            onClick = { mapViewRef?.controller?.zoomOut() }
                        )
                    }
                }
            }
            if (showLocationBtn && canUseMapLocation) {
                FloatingActionButton(
                    onClick = {
                        safeClick {
                            val map = mapViewRef
                            val locationOverlay = locationOverlayRef

                            if (map == null || locationOverlay == null) {
                                Toast.makeText(context, txtLocationNotFound, Toast.LENGTH_SHORT).show()
                                return@safeClick
                            }

                            fun centerOnLocation(location: GeoPoint) {
                                map.controller.stopAnimation(false)
                                map.controller.setZoom(16.0)
                                map.controller.setCenter(location)
                                currentZoom = 16.0
                                currentLat = location.latitude
                                hasCenteredOnLocation = true
                            }

                            when {
                                isTrackingActive -> {
                                    isTrackingActive = false
                                    hasCenteredOnLocation = false
                                    locationOverlay.disableFollowLocation()
                                }
                                !hasCenteredOnLocation -> {
                                    locationOverlay.disableFollowLocation()
                                    val location = locationOverlay.myLocation ?: myCurrentLoc
                                    if (location != null) {
                                        centerOnLocation(location)
                                    } else {
                                        locationOverlay.enableMyLocation()
                                        locationOverlay.runOnFirstFix {
                                            locationOverlay.myLocation?.let { firstLocation ->
                                                map.post { centerOnLocation(firstLocation) }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    isTrackingActive = true
                                    locationOverlay.setEnableAutoStop(false)
                                    locationOverlay.enableMyLocation()
                                    locationOverlay.enableFollowLocation()
                                    (locationOverlay.myLocation ?: myCurrentLoc)?.let { centerOnLocation(it) }
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(MapControlButtonDiameter)
                ) {
                    // ✅ On ajoute le cercle très fin autour de l'icône si actif
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isTrackingActive) {
                                    Modifier.border(
                                        width = 1.5.dp, // Cercle très fin
                                        color = MaterialTheme.colorScheme.primary, // Couleur principale
                                        shape = CircleShape
                                    ).padding(2.dp) // Petit espacement pour ne pas coller au bord
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTrackingActive) Icons.Default.MyLocation else Icons.Outlined.MyLocation, // Change l'icône (plein/vide) pour plus de clarté si tu veux, ou garde Icons.Default.MyLocation
                            contentDescription = stringResource(R.string.appstrings_locate),
                            tint = if (isTrackingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (isTimeSliderVisible) {
            MapTimeSliderBar(
                oldestDateInt = oldestServiceDate?.toIntOrNull()?.coerceAtLeast(19910101) ?: 19910101,
                newestDateInt = todayDateInt,
                thresholdInt = timeSliderThreshold,
                countsByOperator = timeSliderStats.countsByOperator,
                undatedCount = timeSliderStats.undated,
                onThresholdChange = { timeSliderThreshold = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = MapControlButtonDiameter + 24.dp)
                    .navigationBarsPadding()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 32.dp + timeSliderLift)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (AppConfig.showSpeedometer.value) {
                fr.geotower.ui.components.MapSpeedometer(speedKmH = currentSpeedKmH)
            }

            if (showScale) {
                MapScaleBar(zoom = currentZoom, latitude = currentLat)
            }

            if (showAttribution) {
                val attributionText = when (mapProvider) {
                    0 -> "Leaflet | © IGN"
                    2 -> "Leaflet | © CartoDB, OSM"
                    3 -> "Leaflet | © OpenTopoMap"
                    else -> "Leaflet | © OSM"
                }
                val attributionUrl = when (mapProvider) {
                    0 -> "https://geoservices.ign.fr/"
                    2 -> "https://carto.com/attributions"
                    3 -> "https://opentopomap.org/about"
                    else -> "https://www.openstreetmap.org/copyright"
                }

                Surface(
                    color = Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { uriHandler.openUri(attributionUrl) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mapProvider == 0) {
                            Row(modifier = Modifier.size(width = 14.dp, height = 10.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .background(Color(0xFF002395))
                                )
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .background(Color(0xFFED2939))
                                )
                            }
                        } else {
                            Column(modifier = Modifier.size(width = 14.dp, height = 10.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                        .background(Color(0xFF005BBC))
                                )
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                        .background(Color(0xFFFFD600))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = attributionText,
                            fontSize = 11.sp,
                            color = Color(0xFF0078A8)
                        )
                    }
                }
            }
        }

    }


        if (showLayerSheet && canUseLayerSelector) {
            // ✅ On vérifie l'état du réseau dès que le menu s'ouvre
            val isOnline = remember(showLayerSheet) { isNetworkAvailable(context) }

            // 🚀 NOUVEAU : On vérifie si au moins une carte est téléchargée
            val hasOfflineMaps = remember(showLayerSheet) {
                val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
                val mapFiles = offlineDir.listFiles { file -> file.extension == "map" }
                !mapFiles.isNullOrEmpty()
            }

            val txtOfflineMessage = stringResource(R.string.appstrings_offline_message)

            ModalBottomSheet(
                onDismissRequest = { showLayerSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // ✅ 1. ON CRÉE LA COLONNE GLOBALE QUI APPLIQUE LE PADDING À TOUT
                Column(
                    modifier = Modifier.padding(horizontal = sizing.spacing(24.dp)).padding(bottom = sizing.spacing(24.dp))
                ) {

                    // ✅ 2. LA COLONNE DES PREMIERS BOUTONS
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))
                    ) {
                        // 🌐 ON N'AFFICHE LES CARTES EN LIGNE QUE SI ON A INTERNET
                        if (isOnline) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (isMapProviderEnabled(1)) {
                                    MapLayerButton(txtMapOsmLayer, mapProvider == 1, Modifier.weight(1f)) {
                                        AppConfig.mapProvider.value = 1; prefs.edit().putInt("map_provider", 1).apply()
                                        if (ignStyle == 2) { AppConfig.ignStyle.value = 0; prefs.edit().putInt("ign_style", 0).apply() }
                                    }
                                }
                                if (isMapProviderEnabled(0)) {
                                    MapLayerButton(txtMapIgnLayer, mapProvider == 0, Modifier.weight(1f)) {
                                        AppConfig.mapProvider.value = 0; prefs.edit().putInt("map_provider", 0).apply()
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))) {
                                if (isMapProviderEnabled(2)) {
                                    MapLayerButton(txtMapMapLibre, mapProvider == 2, Modifier.weight(1f)) {
                                        AppConfig.mapProvider.value = 2; prefs.edit().putInt("map_provider", 2).apply()
                                    }
                                }
                                if (isMapProviderEnabled(3)) {
                                    MapLayerButton(txtMapTopo, mapProvider == 3, Modifier.weight(1f)) {
                                        AppConfig.mapProvider.value = 3; prefs.edit().putInt("map_provider", 3).apply()
                                    }
                                }
                            }
                        } else {
                            // 📵 MESSAGE HORS-LIGNE
                            Text(
                                text = "⚠️ $txtOfflineMessage",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = sizing.spacing(4.dp), top = sizing.spacing(8.dp))
                            )
                        }

                        // 🗺️ LE BOUTON HORS-LIGNE NE S'AFFICHE QUE SI UNE CARTE EXISTE
                        if (hasOfflineMaps && isMapProviderEnabled(4)) {
                            MapLayerButton(txtMapOfflineLayer, mapProvider == 4, Modifier.fillMaxWidth()) {
                                AppConfig.mapProvider.value = 4
                                prefs.edit().putInt("map_provider", 4).apply()
                            }
                        } else if (!isOnline) {
                            // Optionnel : un petit message pour dire qu'aucune carte n'est dispo si on est hors ligne
                            Text(
                                text = stringResource(R.string.appstrings_no_offline_maps_installed),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = sizing.text(12.sp),
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = sizing.spacing(8.dp))
                            )
                        }
                    }

                    // ✅ 3. L'ANIMATION DES STYLES (Cachée si on est hors ligne !)
                    AnimatedVisibility(
                        visible = isOnline && (mapProvider == 0 || mapProvider == 1 || mapProvider == 2),
                        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }) + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }) + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Column {
                            Spacer(Modifier.height(sizing.spacing(12.dp)))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))) {
                                MapLayerButton(txtMapLight, ignStyle == 0, Modifier.weight(1f)) {
                                    AppConfig.ignStyle.value = 0; prefs.edit().putInt("ign_style", 0).apply()
                                }
                                MapLayerButton(txtMapDark, ignStyle == 1, Modifier.weight(1f)) {
                                    AppConfig.ignStyle.value = 1; prefs.edit().putInt("ign_style", 1).apply()
                                }
                                if (mapProvider == 0) {
                                    MapLayerButton(txtMapSatellite, ignStyle == 2, Modifier.weight(1f)) {
                                        AppConfig.ignStyle.value = 2; prefs.edit().putInt("ign_style", 2).apply()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showSettingsSheet) { MapSettingsSheet(onDismiss = { showSettingsSheet = false }) }
        if (showMapPageSettingsSheet) {
            fr.geotower.ui.screens.settings.MapSettingsSheet(
                showLocation = showLocationBtn,
                onLocationChange = {
                    showLocationBtn = it
                    prefs.edit().putBoolean("show_map_location", it).apply()
                },
                showLocationMarker = showLocationMarker,
                onLocationMarkerChange = {
                    AppConfig.showMapLocationMarker.value = it
                    prefs.edit().putBoolean(AppConfig.PREF_SHOW_MAP_LOCATION_MARKER, it).apply()
                },
                showAzimuths = AppConfig.showAzimuths.value,
                onAzimuthsChange = {
                    AppConfig.showAzimuths.value = it
                    prefs.edit().putBoolean(AppConfig.PREF_SHOW_AZIMUTH_LINES, it).apply()
                },
                showAzimuthsCone = AppConfig.showAzimuthsCone.value,
                onAzimuthsConeChange = {
                    AppConfig.showAzimuthsCone.value = it
                    prefs.edit().putBoolean(AppConfig.PREF_SHOW_AZIMUTH_CONES, it).apply()
                },
                showZoom = showZoomBtns,
                onZoomChange = {
                    showZoomBtns = it
                    prefs.edit().putBoolean("show_map_zoom", it).apply()
                },
                showToolbox = showToolbox,
                onToolboxChange = {
                    showToolbox = it
                    prefs.edit().putBoolean("show_map_toolbox", it).apply()
                },
                showCompass = showCompass,
                onCompassChange = {
                    showCompass = it
                    prefs.edit().putBoolean("show_map_compass", it).apply()
                },
                showScale = showScale,
                onScaleChange = {
                    showScale = it
                    prefs.edit().putBoolean("show_map_scale", it).apply()
                },
                showAttribution = showAttribution,
                onAttributionChange = {
                    showAttribution = it
                    prefs.edit().putBoolean("show_map_attribution", it).apply()
                },
                showSpeedometer = AppConfig.showSpeedometer.value,
                onSpeedometerChange = {
                    AppConfig.showSpeedometer.value = it
                    prefs.edit().putBoolean(MapDisplayPrefs.showSpeedometer.key, it).apply()
                },
                onDismiss = { showMapPageSettingsSheet = false },
                onBack = { showMapPageSettingsSheet = false },
                sheetState = pageSettingsSheetState,
                useOneUi = uiStyle.useOneUi,
                bubbleColor = uiStyle.bubbleColor
            )
        }
    }
    if (showColorWarningDialog) {
        AlertDialog(
            onDismissRequest = { showColorWarningDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = txtWarningTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = txtLightColorWarning)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgainChecked = !dontShowAgainChecked }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = dontShowAgainChecked,
                            onCheckedChange = { dontShowAgainChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = txtDoNotShowAgain, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dontShowAgainChecked) {
                            prefs.edit().putBoolean("hide_light_color_warning", true).apply()
                            hideColorWarning = true
                        }
                        showColorWarningDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = txtUnderstood, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }
    if (showCityStatsPopup) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCityStatsPopup = false }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val cityStats = if (isLoading) null else declaredSiteStats(filteredAntennas)

                    Text(
                        text = stringResource(R.string.appstrings_city_stats_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.appstrings_mobile_telephony),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isLoading) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                val stats = cityStats ?: DeclaredSiteStats(activeCount = 0, totalCount = 0)
                                val statsText = if (stats.totalCount == 0) "0" else "${stats.activeCount}/${stats.totalCount}"
                                val statsFontSize = when {
                                    statsText.length >= 11 -> 36.sp
                                    statsText.length >= 9 -> 42.sp
                                    statsText.length >= 7 -> 46.sp
                                    else -> 52.sp
                                }

                                Text(
                                    text = statsText,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = statsFontSize,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    softWrap = false,
                                    textAlign = TextAlign.Center
                                )
                                if (stats.totalCount > 0) {
                                    Text(
                                        text = stringResource(R.string.appstrings_active_declared_sites_label),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                    )
                                }
                            }

                            if ((cityStats?.totalCount ?: 0) > 0) {
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { showCityStatsDetail = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(stringResource(R.string.appstrings_details), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    TextButton(onClick = { showCityStatsPopup = false }) {
                        Text(stringResource(R.string.appstrings_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showCityStatsDetail) {
        fr.geotower.ui.components.CityStatsDetailSheet(
            antennas = filteredAntennas,
            techniques = cityStatsTechniques,
            isFrequencyStatusLoading = isCityStatsTechniquesLoading,
            onRequestFrequencyStatus = { idAnfrs ->
                viewModel.loadCityStatsTechniques(idAnfrs.toList())
            },
            onDismiss = { showCityStatsDetail = false }
        )
    } else {
        LaunchedEffect(showCityStatsDetail) {
            viewModel.clearCityStatsTechniques()
        }
    }
}

@Composable
private fun MapScaleBar(zoom: Double, latitude: Double) {
    val density = LocalDensity.current
    val maxBarWidthDp = 100.dp
    val maxBarWidthPx = with(density) { maxBarWidthDp.toPx() }
    val metersPerPx = 156543.03392 * Math.cos(latitude * Math.PI / 180.0) / Math.pow(2.0, zoom)

    val roundDistances = listOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 300000, 500000, 1000000)
    val chosenDistance = roundDistances.lastOrNull { (it / metersPerPx) <= maxBarWidthPx } ?: 1
    val actualBarWidthDp = with(density) { (chosenDistance / metersPerPx).toFloat().toDp() }
    val label = if (chosenDistance >= 1000) "${chosenDistance / 1000} km" else "$chosenDistance m"

    Surface(color = Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            ComposeCanvas(modifier = Modifier.width(actualBarWidthDp).height(6.dp)) {
                drawLine(Color.Black, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
                drawLine(Color.Black, Offset(0f, size.height), Offset(0f, 0f), strokeWidth = 2.dp.toPx())
                drawLine(Color.Black, Offset(size.width, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun MapLayerButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val bgColor = if (isSelected) Color(0xFF3B5998) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, modifier = modifier.height(sizing.component(56.dp)), shape = RoundedCornerShape(sizing.component(14.dp)), color = bgColor) {
        Box(contentAlignment = Alignment.Center) { Text(text = text, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = sizing.text(14.sp)) }
    }
}

@Composable
private fun MapSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(MapSearchBarHeight),
        shape = RoundedCornerShape(MapSearchBarHeight / 2f),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 12.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .size(MapControlButtonDiameter)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.appstrings_search),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun SmallFloatingButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.size(MapControlButtonDiameter)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc) }
    }
}

@Composable
private fun ZoomControlSegmentButton(
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MapControlButtonDiameter)
            .clip(shape),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}

private fun currentDisplayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: AndroidSurface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }
}

private fun azimuthFromRotationVector(values: FloatArray, displayRotation: Int): Float {
    val rotationMatrix = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(rotationMatrix, values)

    val adjustedMatrix = remapRotationMatrixForDisplay(rotationMatrix, displayRotation)
    val orientation = FloatArray(3)
    SensorManager.getOrientation(adjustedMatrix, orientation)
    return Math.toDegrees(orientation[0].toDouble()).toFloat()
}

private fun remapRotationMatrixForDisplay(rotationMatrix: FloatArray, displayRotation: Int): FloatArray {
    val remappedMatrix = FloatArray(9)
    val remapped = when (displayRotation) {
        AndroidSurface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_Y,
            SensorManager.AXIS_MINUS_X,
            remappedMatrix
        )
        AndroidSurface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_MINUS_X,
            SensorManager.AXIS_MINUS_Y,
            remappedMatrix
        )
        AndroidSurface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_MINUS_Y,
            SensorManager.AXIS_X,
            remappedMatrix
        )
        else -> false
    }

    return if (remapped) remappedMatrix else rotationMatrix
}

@Suppress("DEPRECATION")
private fun isLegacyOrientationSensor(sensor: Sensor): Boolean {
    return sensor.type == Sensor.TYPE_ORIENTATION
}

private fun correctLegacyAzimuthForDisplay(azimuth: Float, displayRotation: Int): Float {
    return when (displayRotation) {
        AndroidSurface.ROTATION_90 -> azimuth + 90f
        AndroidSurface.ROTATION_180 -> azimuth + 180f
        AndroidSurface.ROTATION_270 -> azimuth - 90f
        else -> azimuth
    }
}

@Composable
private fun ActiveMapFiltersBanner(
    summary: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = stringResource(R.string.appstrings_map_active_filters_message, summary),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MapCompassButton(
    azimuth: Float,
    modifier: Modifier = Modifier,
    onReset: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onReset),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "N",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
                modifier = Modifier.align(Alignment.TopCenter),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = "S",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.BottomCenter),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = "E",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp)
            )
            Text(
                text = "O",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 5.dp)
            )

            ComposeCanvas(
                modifier = Modifier
                    .size(30.dp)
                    .rotate(-azimuth)
            ) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)

                val padding = h * 0.12f
                val topTipY = padding
                val bottomTipY = h - padding

                val pathNorthLeft = Path().apply {
                    moveTo(w / 2f, topTipY)
                    lineTo(w / 2f, h / 2f)
                    lineTo(w / 4f, h / 2f)
                    close()
                }
                val pathNorthRight = Path().apply {
                    moveTo(w / 2f, topTipY)
                    lineTo(w * 3 / 4f, h / 2f)
                    lineTo(w / 2f, h / 2f)
                    close()
                }
                drawPath(pathNorthLeft, Color(0xFFD32F2F))
                drawPath(pathNorthRight, Color(0xFFF44336))

                val pathSouthLeft = Path().apply {
                    moveTo(w / 2f, bottomTipY)
                    lineTo(w / 2f, h / 2f)
                    lineTo(w / 4f, h / 2f)
                    close()
                }
                val pathSouthRight = Path().apply {
                    moveTo(w / 2f, bottomTipY)
                    lineTo(w * 3 / 4f, h / 2f)
                    lineTo(w / 2f, h / 2f)
                    close()
                }
                drawPath(pathSouthLeft, Color(0xFF9E9E9E))
                drawPath(pathSouthRight, Color(0xFFE0E0E0))

                drawCircle(Color.White, radius = w / 10f, center = center)
                drawCircle(
                    Color.Gray,
                    radius = w / 10f,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}


open class CustomLocationOverlay(
    provider: org.osmdroid.views.overlay.mylocation.IMyLocationProvider,
    private val mapView: MapView,
    private val primaryColor: Int
) : MyLocationNewOverlay(provider, mapView) {

    var currentCompassAzimuth = 0f
    var showLocationMarker = true

    // --- On prépare les objets de dessin une seule fois pour éviter les allocations dans draw() ---
    private val pt = android.graphics.Point()
    private val beamPath = android.graphics.Path()
    private val arrowHead = android.graphics.Path()
    private var isPathsInitialized = false

    private val themeFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = primaryColor }
    private val themeStrokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; color = primaryColor; strokeCap = android.graphics.Paint.Cap.ROUND }
    private val whiteFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = android.graphics.Color.WHITE }
    private val pulsePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; color = primaryColor; strokeWidth = 3f }

    override fun drawMyLocation(
        canvas: android.graphics.Canvas,
        projection: org.osmdroid.views.Projection,
        lastFix: android.location.Location
    ) {
        // On réutilise le point existant !
        projection.toPixels(org.osmdroid.util.GeoPoint(lastFix.latitude, lastFix.longitude), pt)
        if (!showLocationMarker) return

        val density = mapView.context.resources.displayMetrics.density
        val radius = 18f * density
        val stemLength = 35f * density

        // On ne calcule la forme des chemins qu'à la première frame
        if (!isPathsInitialized) {
            themeStrokePaint.strokeWidth = 5f * density

            arrowHead.apply {
                val tipY = -(radius + stemLength + 15f)
                val baseY = -(radius + stemLength - 5f)
                moveTo(0f, tipY); lineTo(-12f, baseY); lineTo(12f, baseY); close()
            }
            isPathsInitialized = true
        }

        canvas.save()
        canvas.translate(pt.x.toFloat(), pt.y.toFloat())
        canvas.rotate(currentCompassAzimuth)

        // ❌ Le cône (beamPath) et l'effet radar (pulsePaint) ont été retirés d'ici

        // ✅ On dessine uniquement le repère central fixe (le rond)
        canvas.drawCircle(0f, 0f, radius, themeFillPaint)
        canvas.drawCircle(0f, 0f, radius * 0.65f, whiteFillPaint)
        canvas.drawCircle(0f, 0f, radius * 0.35f, themeFillPaint)

        // ✅ On garde le trait et la flèche directionnelle si l'option est active
        if (AppConfig.hasCompass.value) {
            canvas.drawLine(0f, -radius, 0f, -(radius + stemLength), themeStrokePaint)
            canvas.drawPath(arrowHead, themeFillPaint)
        }

        canvas.restore()
    }
}

class AntennaMarker(
    private val mapView: org.osmdroid.views.MapView,
    private val siteAntennas: List<LocalisationEntity>,
    private val primaryColor: Int
) : org.osmdroid.views.overlay.Marker(mapView) {

    private val density = mapView.context.resources.displayMetrics.density
    private val ptCenter = android.graphics.Point()

    // 🚨 NOUVEAU : On redéfinit la HitBox pour qu'elle ignore les faisceaux et soit 100% ronde
    override fun hitTest(event: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
        val pj = mapView.projection
        val screenCoords = android.graphics.Point()
        pj.toPixels(position, screenCoords)

        val dx = event.x - screenCoords.x
        val dy = event.y - screenCoords.y

        // Rayon cliquable fixe de 22dp (englobe juste le rond central, ignore le carré transparent)
        val clickRadius = 22f * density
        return (dx * dx + dy * dy) <= (clickRadius * clickRadius)
    }

    // ✅ NOUVELLE STRUCTURE : On regroupe les couleurs par azimut !
    private class GroupedAzimuthData(
        val azimuth: Float,
        val cos: Float,
        val sin: Float,
        val linePaint: android.graphics.Paint,
        val conePaint: android.graphics.Paint?, // 🚨 NOUVEAU : Le pinceau translucide
        val coneEdgePaint: android.graphics.Paint?,
        val dotColors: List<Int>
    )

    private val precalculatedMobileAzimuths = mutableListOf<GroupedAzimuthData>()
    private val precalculatedFhAzimuths = mutableListOf<GroupedAzimuthData>()

    // Cache pour les pinceaux (pour éviter d'en recréer 60 fois par seconde)
    private val dotPaints = mutableMapOf<Int, android.graphics.Paint>()
    private fun getDotPaint(colorInt: Int): android.graphics.Paint {
        return dotPaints.getOrPut(colorInt) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = colorInt
            }
        }
    }

    private fun sortAzimuthOperatorKeys(
        operatorKeys: Set<String>,
        defaultOperatorKey: String?
    ): List<String> {
        val baseOrder = listOf(
            OperatorColors.ORANGE_KEY,
            OperatorColors.BOUYGUES_KEY,
            OperatorColors.SFR_KEY,
            OperatorColors.FREE_KEY
        )
        val ordered = mutableListOf<String>()
        if (defaultOperatorKey != null && defaultOperatorKey in operatorKeys) {
            ordered += defaultOperatorKey
        }
        baseOrder.forEach { key ->
            if (key in operatorKeys && key !in ordered) ordered += key
        }
        OperatorColors.orderedKeys.forEach { key ->
            if (key in operatorKeys && key !in ordered) ordered += key
        }
        return ordered.ifEmpty { operatorKeys.toList() }
    }

    init {
        // 1. On prépare des dictionnaires pour regrouper : Angle -> Liste de Couleurs (Opérateurs)
        val angleToOperatorsMobile = mutableMapOf<Float, MutableSet<String>>()
        val angleToOperatorsFh = mutableMapOf<Float, MutableSet<String>>()

        siteAntennas.forEach { antenna ->
            val operatorKeys = OperatorColors.keysFor(antenna.operateur)
            if (operatorKeys.isEmpty()) return@forEach

            if (!antenna.azimuts.isNullOrBlank()) {
                antenna.azimuts.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToOperatorsMobile.getOrPut(az) { mutableSetOf() }.addAll(operatorKeys)
                }
            }

            if (fr.geotower.utils.AppConfig.showTechnoFH.value && !antenna.azimutsFh.isNullOrBlank()) {
                antenna.azimutsFh.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToOperatorsFh.getOrPut(az) { mutableSetOf() }.addAll(operatorKeys)
                }
            }
        }

        // L'opérateur par défaut qu'il faut prioriser pour la couleur du trait
        val defaultOperatorKey = OperatorColors.keyFor(fr.geotower.utils.AppConfig.defaultOperator.value)

        // 2. On transforme ces groupes en données de dessin (Cos/Sin précalculés)
        angleToOperatorsMobile.forEach { (az, operatorKeys) ->
            val rad = Math.toRadians(az - 90.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()

            // On trie pour que la couleur de l'opérateur favori soit en premier (prioritaire)
            val sortedColors = sortAzimuthOperatorKeys(operatorKeys, defaultOperatorKey)
                .map { OperatorColors.colorIntForKey(it, fallback = primaryColor) }
            val mainColor = sortedColors.first()

            val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = mainColor
                strokeWidth = 3.5f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }

            // Le pinceau pour le cône (Alpha = 40/255, soit environ 15% d'opacité)
            val conePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = androidx.core.graphics.ColorUtils.setAlphaComponent(mainColor, 50)
            }

            val coneEdgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = androidx.core.graphics.ColorUtils.setAlphaComponent(mainColor, 170)
                strokeWidth = 2.2f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
            }

            precalculatedMobileAzimuths.add(GroupedAzimuthData(az, cos, sin, linePaint, conePaint, coneEdgePaint, sortedColors))
        }

        // Pareil pour les faisceaux hertziens (FH)
        angleToOperatorsFh.forEach { (az, operatorKeys) ->
            val rad = Math.toRadians(az - 90.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()

            val sortedColors = sortAzimuthOperatorKeys(operatorKeys, defaultOperatorKey)
                .map { OperatorColors.colorIntForKey(it, fallback = primaryColor) }
            val mainColor = sortedColors.first()

            val dashedPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.argb(200, android.graphics.Color.red(mainColor), android.graphics.Color.green(mainColor), android.graphics.Color.blue(mainColor))
                strokeWidth = 3f * density
                strokeCap = android.graphics.Paint.Cap.ROUND
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
            }

            // On passe bien "az" et "null" (Pas de cône pour les FH)
            precalculatedFhAzimuths.add(GroupedAzimuthData(az, cos, sin, dashedPaint, null, null, sortedColors))
        }
    }

    private fun getOpColorInt(name: String?): Int {
        return OperatorColors.colorInt(name, fallback = primaryColor)
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble

        // 🚨 NOUVEAU : On lit les préférences en direct
        val showLines = fr.geotower.utils.AppConfig.showAzimuths.value
        val showCones = fr.geotower.utils.AppConfig.showAzimuthsCone.value

        // On ne rentre dans le bloc que si au moins l'un des deux est activé
        if (zoom >= 14.0 && (showLines || showCones)) {
            projection.toPixels(mPosition, ptCenter)

            val beamLengthPx = when {
                zoom >= 18.0 -> 60f * density
                zoom >= 17.0 -> 50f * density
                zoom >= 16.0 -> 40f * density
                zoom >= 15.0 -> 30f * density
                else -> 25f * density
            }

            val pointRadius = 3.5f * density
            val fhRadius = pointRadius * 0.7f

            val circleOffsetPx = 17f * density
            val totalRadiusPx = circleOffsetPx + beamLengthPx

            val gapMobile = pointRadius * 2.0f
            val gapFh = fhRadius * 2.0f

            // 🚨 NOUVEAU : Rectangle de délimitation (Bounding Box) pour tracer les cônes
            val rectF = android.graphics.RectF(
                ptCenter.x - totalRadiusPx,
                ptCenter.y - totalRadiusPx,
                ptCenter.x + totalRadiusPx,
                ptCenter.y + totalRadiusPx
            )

            // --- DESSIN DES MOBILES ---
            precalculatedMobileAzimuths.forEach { data ->

                // 1. DESSIN DU CÔNE (Toujours en premier pour qu'il soit "au fond")
                if (showCones && data.conePaint != null) {
                    // L'angle 0 d'Android est à l'Est (3h), l'azimut 0 est au Nord (12h) -> On enlève 90°.
                    // Pour un cône de 70°, on doit reculer de 35° pour que le centre du cône pointe sur l'azimut exact.
                    val startAngle = data.azimuth - 90f - 35f
                    canvas.drawArc(rectF, startAngle, 70f, true, data.conePaint)
                    data.coneEdgePaint?.let { edgePaint ->
                        drawConeEdgeLines(canvas, data.azimuth, circleOffsetPx, totalRadiusPx, edgePaint)
                    }
                }

                // 2. DESSIN DE LA LIGNE ET DES PASTILLES D'OPÉRATEURS
                if (showLines) {
                    val startX = ptCenter.x + circleOffsetPx * data.cos
                    val startY = ptCenter.y + circleOffsetPx * data.sin
                    val endX = ptCenter.x + totalRadiusPx * data.cos
                    val endY = ptCenter.y + totalRadiusPx * data.sin

                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                    data.dotColors.forEachIndexed { index, colorInt ->
                        val offsetMag = index * gapMobile
                        val dotX = endX + (data.cos * offsetMag)
                        val dotY = endY + (data.sin * offsetMag)

                        canvas.drawCircle(dotX, dotY, pointRadius, getDotPaint(colorInt))
                    }
                }
            }

            // --- DESSIN DES FAISCEAUX HERTZIENS (FH) ---
            if (fr.geotower.utils.AppConfig.showTechnoFH.value && showLines) {
                precalculatedFhAzimuths.forEach { data ->
                    val startX = ptCenter.x + circleOffsetPx * data.cos
                    val startY = ptCenter.y + circleOffsetPx * data.sin
                    val endX = ptCenter.x + totalRadiusPx * data.cos
                    val endY = ptCenter.y + totalRadiusPx * data.sin

                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                    data.dotColors.forEachIndexed { index, colorInt ->
                        val offsetMag = index * gapFh
                        val dotX = endX + (data.cos * offsetMag)
                        val dotY = endY + (data.sin * offsetMag)

                        canvas.drawCircle(dotX, dotY, fhRadius, getDotPaint(colorInt))
                    }
                }
            }
        }
        super.draw(canvas, projection)
    }

    private fun drawConeEdgeLines(
        canvas: android.graphics.Canvas,
        azimuth: Float,
        startRadiusPx: Float,
        endRadiusPx: Float,
        paint: android.graphics.Paint
    ) {
        listOf(azimuth - 35f, azimuth + 35f).forEach { edgeAzimuth ->
            val edgeRad = Math.toRadians(edgeAzimuth - 90.0)
            val edgeCos = Math.cos(edgeRad).toFloat()
            val edgeSin = Math.sin(edgeRad).toFloat()
            canvas.drawLine(
                ptCenter.x + startRadiusPx * edgeCos,
                ptCenter.y + startRadiusPx * edgeSin,
                ptCenter.x + endRadiusPx * edgeCos,
                ptCenter.y + endRadiusPx * edgeSin,
                paint
            )
        }
    }
}

class RadioMarker(
    private val mapView: org.osmdroid.views.MapView,
    private val radioMarker: RadioMapMarker,
    private val showCircle: Boolean
) : org.osmdroid.views.overlay.Marker(mapView) {

    private data class RadioAzimuthLine(
        val cos: Float,
        val sin: Float
    )

    private val density = mapView.context.resources.displayMetrics.density
    private val ptCenter = android.graphics.Point()
    private val color = MapUtils.radioMarkerColor(radioMarker.serviceMask, radioMarker.systemMask)
    private val azimuthLines = radioMarker.azimuths.map { azimuth ->
        val rad = Math.toRadians(azimuth - 90.0)
        RadioAzimuthLine(
            cos = Math.cos(rad).toFloat(),
            sin = Math.sin(rad).toFloat()
        )
    }
    private val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = androidx.core.graphics.ColorUtils.setAlphaComponent(this@RadioMarker.color, 210)
        strokeWidth = 2.35f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = androidx.core.graphics.ColorUtils.setAlphaComponent(this@RadioMarker.color, 230)
    }

    override fun hitTest(event: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
        if (!showCircle) return false
        val pj = mapView.projection
        val screenCoords = android.graphics.Point()
        pj.toPixels(position, screenCoords)

        val dx = event.x - screenCoords.x
        val dy = event.y - screenCoords.y
        val clickRadius = 18f * density
        return (dx * dx + dy * dy) <= (clickRadius * clickRadius)
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble
        if (
            !radioMarker.isCluster &&
            zoom >= 14.0 &&
            AppConfig.showAzimuths.value &&
            azimuthLines.isNotEmpty()
        ) {
            projection.toPixels(mPosition, ptCenter)

            val beamLengthPx = when {
                zoom >= 18.0 -> 56f * density
                zoom >= 17.0 -> 47f * density
                zoom >= 16.0 -> 38f * density
                zoom >= 15.0 -> 29f * density
                else -> 23f * density
            }
            val circleOffsetPx = 17f * density
            val totalRadiusPx = circleOffsetPx + beamLengthPx
            val dotRadius = 2.8f * density

            azimuthLines.forEach { data ->
                val startX = ptCenter.x + circleOffsetPx * data.cos
                val startY = ptCenter.y + circleOffsetPx * data.sin
                val endX = ptCenter.x + totalRadiusPx * data.cos
                val endY = ptCenter.y + totalRadiusPx * data.sin

                canvas.drawLine(startX, startY, endX, endY, linePaint)
                canvas.drawCircle(endX, endY, dotRadius, dotPaint)
            }
        }
        super.draw(canvas, projection)
    }
}

private class SignalQuestCoverageOverlay(context: Context) : org.osmdroid.views.overlay.Overlay() {
    private val density = context.resources.displayMetrics.density
    private val point = android.graphics.Point()
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    private var points: List<SignalQuestCoveragePoint> = emptyList()

    /** Callback déclenché au tap sur un point. Affecté depuis la composition. */
    var onPointClick: ((SignalQuestCoveragePoint) -> Unit)? = null

    fun setPoints(nextPoints: List<SignalQuestCoveragePoint>) {
        points = nextPoints
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        if (points.isEmpty()) return

        val radius = 3.6f * density
        points.forEach { coveragePoint ->
            projection.toPixels(GeoPoint(coveragePoint.latitude, coveragePoint.longitude), point)
            fillPaint.color = rsrpColor(coveragePoint.signalStrength)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, fillPaint)
        }
    }

    override fun onSingleTapConfirmed(e: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
        val handler = onPointClick ?: return false
        if (points.isEmpty()) return false

        val projection = mapView.projection
        val touchRadiusPx = 18f * density
        val touchRadiusSq = touchRadiusPx * touchRadiusPx
        var best: SignalQuestCoveragePoint? = null
        var bestDistanceSq = Float.MAX_VALUE

        points.forEach { coveragePoint ->
            projection.toPixels(GeoPoint(coveragePoint.latitude, coveragePoint.longitude), point)
            val dx = e.x - point.x
            val dy = e.y - point.y
            val distanceSq = dx * dx + dy * dy
            if (distanceSq <= touchRadiusSq && distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                best = coveragePoint
            }
        }

        val tapped = best ?: return false
        handler(tapped)
        return true
    }
}

/** Couleur d'un point de couverture selon le RSRP (dBm), du vert (bon) au rouge (mauvais). */
private fun rsrpColor(signalStrength: Float?): Int {
    val rsrp = signalStrength ?: return android.graphics.Color.GRAY
    return when {
        rsrp >= -80f -> android.graphics.Color.parseColor("#1B7F2E")  // vert foncé
        rsrp >= -95f -> android.graphics.Color.parseColor("#66BB6A")  // vert clair
        rsrp >= -105f -> android.graphics.Color.parseColor("#FDD835") // jaune
        rsrp >= -115f -> android.graphics.Color.parseColor("#FB8C00") // orange
        else -> android.graphics.Color.parseColor("#E53935")          // rouge
    }
}

private fun coverageQualityLabelRes(signalStrength: Float): Int = when {
    signalStrength >= -80f -> R.string.appstrings_signalquest_coverage_quality_excellent
    signalStrength >= -95f -> R.string.appstrings_signalquest_coverage_quality_good
    signalStrength >= -105f -> R.string.appstrings_signalquest_coverage_quality_fair
    signalStrength >= -115f -> R.string.appstrings_signalquest_coverage_quality_poor
    else -> R.string.appstrings_signalquest_coverage_quality_bad
}

private fun formatCoverageTimestamp(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val parser = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            if (!pattern.endsWith("XXX")) {
                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(value) ?: continue
            return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(date)
        } catch (_: Exception) {
            // essaie le motif suivant
        }
    }
    return value
}

@Composable
private fun CoverageDetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CoveragePointDetailDialog(point: SignalQuestCoveragePoint, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.appstrings_signalquest_coverage_detail_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                CoverageDetailRow(
                    stringResource(R.string.appstrings_signalquest_coverage_detail_operator),
                    point.operatorLabel
                )
                point.technology?.takeIf { it.isNotBlank() }?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_technology),
                        it
                    )
                }
                point.networkType?.takeIf { it.isNotBlank() }?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_network_type),
                        it
                    )
                }
                point.signalStrength?.let { signal ->
                    CoverageDetailRow(
                        label = stringResource(R.string.appstrings_signalquest_coverage_detail_signal),
                        value = "${signal.roundToInt()} dBm · ${stringResource(coverageQualityLabelRes(signal))}",
                        valueColor = Color(rsrpColor(signal))
                    )
                }
                point.rsrq?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_rsrq),
                        "${it.roundToInt()} dB"
                    )
                }
                point.snr?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_snr),
                        "${it.roundToInt()} dB"
                    )
                }
                if (point.mcc != null || point.mnc != null) {
                    val plmn = buildString {
                        point.mcc?.let { append(it) }
                        if (point.mcc != null && point.mnc != null) append(" / ")
                        point.mnc?.let { append(it.toString().padStart(2, '0')) }
                    }
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_plmn),
                        plmn
                    )
                }
                point.cellId?.takeIf { it.isNotBlank() }?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_cell_id),
                        it
                    )
                }
                point.pci?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_pci),
                        it.toString()
                    )
                }
                point.enb?.takeIf { it.isNotBlank() }?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_enb),
                        it
                    )
                }
                point.gnb?.takeIf { it.isNotBlank() }?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_gnb),
                        it
                    )
                }
                CoverageDetailRow(
                    stringResource(R.string.appstrings_signalquest_coverage_detail_coordinates),
                    String.format(java.util.Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
                )
                formatCoverageTimestamp(point.timestamp)?.let {
                    CoverageDetailRow(
                        stringResource(R.string.appstrings_signalquest_coverage_detail_measured_at),
                        it
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.appstrings_signalquest_coverage_detail_close))
            }
        }
    )
}

private fun mapLocationKey(latitude: Double, longitude: Double): String {
    return "${(latitude * 1_000_000.0).roundToInt()}_${(longitude * 1_000_000.0).roundToInt()}"
}

private fun isPointInPolygon(lat: Double, lon: Double, polygon: List<GeoPoint>): Boolean {
    var isInside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        if ((polygon[i].latitude > lat) != (polygon[j].latitude > lat) &&
            (lon < (polygon[j].longitude - polygon[i].longitude) * (lat - polygon[i].latitude) /
                    (polygon[j].latitude - polygon[i].latitude) + polygon[i].longitude)
        ) {
            isInside = !isInside
        }
        j = i
    }
    return isInside
}

// ✅ NOUVEAU : Fonction pour vérifier si internet est disponible
// 🚨 DESSINE LE POINT D'EXCLAMATION DE PANNE AVEC UN CACHE
fun createHsBadge(context: Context): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density

    // ✅ ON AGRANDIT ENCORE : 32 au lieu de 26 pour être sûr de tout masquer !
    // (Vous pouvez ajuster ce chiffre librement : 30, 32, 34...)
    val size = (32 * density).roundToInt().coerceAtLeast(1)
    hsBadgeDrawableCache.get(size)?.let { return it }
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // 1. LE CACHE (Le fond pour effacer le logo de l'antenne)
    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F5F5F5")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, maskPaint)

    // 2. LE TEXTE (Le point d'exclamation)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E53935") // Rouge vif
        // ✅ On grossit aussi le point d'exclamation (de 20 à 24) pour qu'il reste proportionnel
        textSize = 24f * density
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    canvas.drawText("!", size / 2f, size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap).also { drawable ->
        hsBadgeDrawableCache.put(size, drawable)
    }
}

private fun createHsMarkerIcon(context: Context, baseIcon: BitmapDrawable): BitmapDrawable {
    val cacheKey = "${System.identityHashCode(baseIcon)}_${baseIcon.intrinsicWidth}x${baseIcon.intrinsicHeight}"
    hsMarkerIconCache.get(cacheKey)?.let { return it }

    val badgeIcon = createHsBadge(context)
    val combinedBitmap = android.graphics.Bitmap.createBitmap(
        baseIcon.intrinsicWidth,
        baseIcon.intrinsicHeight,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(combinedBitmap)

    baseIcon.setBounds(0, 0, canvas.width, canvas.height)
    baseIcon.draw(canvas)

    val offsetX = (canvas.width - badgeIcon.intrinsicWidth) / 2
    val offsetY = (canvas.height - badgeIcon.intrinsicHeight) / 2
    badgeIcon.setBounds(offsetX, offsetY, offsetX + badgeIcon.intrinsicWidth, offsetY + badgeIcon.intrinsicHeight)
    badgeIcon.draw(canvas)

    return android.graphics.drawable.BitmapDrawable(context.resources, combinedBitmap).also { drawable ->
        hsMarkerIconCache.put(cacheKey, drawable)
    }
}
