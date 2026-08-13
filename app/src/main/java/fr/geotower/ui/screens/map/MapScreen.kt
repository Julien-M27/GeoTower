package fr.geotower.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.LocationDisabled
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.data.api.GeoTowerDataCoverage
import fr.geotower.data.api.NominatimApi
import fr.geotower.data.api.RouteApi
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.db.CommuneNameRow
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.AdminAreaExtent
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.data.trip.TripFollowStatus
import fr.geotower.data.trip.TripOrderOptimizer
import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.NAV_APPROACH_MIN_METERS
import fr.geotower.data.trip.NAV_APPROACH_REFRESH_METERS
import fr.geotower.data.trip.NAV_CAMERA_AHEAD_FRACTION
import fr.geotower.data.trip.NAV_CAMERA_MAX_AHEAD_FRACTION
import fr.geotower.data.trip.NAV_FOLLOW_ZOOM
import fr.geotower.data.trip.normalizeDegrees
import fr.geotower.data.trip.TripHeadingSmoother
import fr.geotower.data.trip.computeTripFollowStatus
import fr.geotower.data.trip.haversineMeters
import fr.geotower.data.trip.navigationCameraTarget
import fr.geotower.data.trip.tripDirectionArrows
import fr.geotower.data.workers.TripReminderScheduler
import fr.geotower.ui.screens.trips.TripScheduleDialog
import fr.geotower.ui.screens.trips.withSchedule
import fr.geotower.data.trip.TripPlanStore
import fr.geotower.data.trip.TripRouteCalculator
import fr.geotower.data.trip.TripStep
import fr.geotower.data.models.isDeclaredActive
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.ui.components.LiveDatabaseUsageWarningDialog
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.ROOT_FALLBACK_ROUTE
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PowerProfile
import fr.geotower.utils.AppLogger
import fr.geotower.utils.CommuneNameMatching
import fr.geotower.utils.FrenchAdminAreas
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.MapFilterDefaults
import fr.geotower.utils.LocationReadiness
import fr.geotower.utils.locationReadiness
import fr.geotower.utils.openAppLocationSettings
import fr.geotower.utils.openLocationSourceSettings
import fr.geotower.utils.rememberLocationReadinessState
import fr.geotower.utils.MapDisplayPrefs
import fr.geotower.utils.MapUtils
import fr.geotower.ui.screens.emitters.OperatorGrid
import fr.geotower.utils.OperatorColorSpec
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.filteredAzimuthsForFrequencySelection
import fr.geotower.utils.formatSiteDistanceMeters
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
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.nativeCanvas
import android.os.SystemClock
import fr.geotower.utils.location.FusedMyLocationProvider
import fr.geotower.utils.location.PedestrianDeadReckoning
import fr.geotower.utils.location.RawFix
import fr.geotower.utils.location.SmoothLocationEngine
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val HS_OPERATOR_WILDCARD = "*"
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
/**
 * Distance parcourue au-delà de laquelle le suivi du site le plus proche relit son voisinage en
 * base. Le plus petit palier de lecture couvre déjà ~1,5 km : on ne redemande donc rien tant qu'on
 * n'a pas entamé cette marge, la cible étant recalculée sur la position réelle à chaque tour.
 */
private const val TRACKING_NEARBY_REFRESH_METERS = 400.0
/** Repos de la boucle de rendu fluide quand il n'y a rien à animer (immobile ou sans position). */
private const val SMOOTH_LOCATION_IDLE_POLL_MS = 50L

/** Orientation de la carte au moment où on l'a quittée, pour la retrouver telle quelle. */
private const val PREF_LAST_MAP_ORIENTATION = "last_map_orientation"

/**
 * Alignement de la carte sur le cap de l'appareil.
 *
 * La carte rejoint la cible par un fondu : à chaque image, elle couvre une part de l'écart restant,
 * et au bout de cette durée elle en a couvert environ 63 %. Appliquer le cap tel quel ferait
 * trembler la carte au moindre frémissement du capteur, et l'appliquer par paliers la ferait sauter
 * d'un bloc — c'est bien un pas régulier, et non un gros pas rare, qui se lit comme une rotation
 * fluide.
 *
 * Le pas se calcule sur le temps écoulé et non sur le nombre d'images : la carte met le même temps à
 * rejoindre le cap que l'écran soit à 60 ou à 120 Hz, et une image sautée sur une vue chargée ne
 * fait pas prendre du retard à la rotation.
 */
private const val MAP_FOLLOW_ORIENTATION_TIME_CONSTANT_MS = 70f
/** Reprise après un cap stable : aucune image précédente à mesurer, on part d'une image « type ». */
private const val MAP_FOLLOW_ORIENTATION_REFERENCE_FRAME_MS = 16f
/** En deçà de cet écart, la carte est arrivée : on ne repeint pas pour du bruit de capteur. */
private const val MAP_FOLLOW_ORIENTATION_DEAD_ZONE_DEG = 0.2f
/** Cap stable : on rend la boucle d'images et on se contente de surveiller le capteur. */
private const val MAP_FOLLOW_ORIENTATION_IDLE_POLL_MS = 60L
/**
 * Amorce du geste : tant que les deux doigts n'ont pas tourné d'au moins autant, la carte ne bouge
 * pas. Écarter deux doigts pour zoomer fait toujours pivoter un peu la main — sans ce seuil, un
 * simple zoom fait tourner la carte de plusieurs dizaines de degrés sans qu'on l'ait demandé.
 */
private const val MAP_ROTATION_GESTURE_THRESHOLD_DEG = 18f

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

/**
 * Niveau de zoom du recentrage sur la position : réglable dans Réglages > Cartographie
 * (AppConfig.PREF_MAP_LOCATION_ZOOM). Il sert aussi bien au bouton de localisation qu'au tout
 * premier centrage automatique, qui sont le même geste vu par l'utilisateur.
 */
private fun preferredLocationZoom(): Double = AppConfig.mapLocationZoom.intValue
    .coerceIn(AppConfig.MIN_MAP_LOCATION_ZOOM, AppConfig.MAX_MAP_LOCATION_ZOOM)
    .toDouble()

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

/** Écart signé le plus court entre deux caps, ramené dans ]-180°, 180°]. */
private fun shortestAngleDelta(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta <= -180f) delta += 360f
    else if (delta > 180f) delta -= 360f
    return delta
}

/** Orientation ramenée dans [0°, 360°[, pour ne pas laisser filer l'angle au fil des gestes. */
private fun normalizeMapOrientation(degrees: Float): Float {
    val normalized = degrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

/**
 * Tourne la carte sans passer par le setter d'osmdroid.
 *
 * `mapOrientation = x` appelle `requestLayout()`, qui ne sert qu'à replacer les vues ancrées à une
 * position (les bulles d'info) : sans enfant, il ne fait que renvoyer TOUTE la hiérarchie Compose
 * dans une passe de mesure à chaque pas de rotation. C'est le principal responsable des à-coups —
 * la projection, elle, est reconstruite à chaque dessin, un simple `invalidate()` suffit donc.
 */
private fun MapView.applyOrientation(degrees: Float) {
    setMapOrientation(degrees, false)
    if (childCount > 0) requestLayout()
    invalidate()
}

/**
 * Angle auquel un cap géographique apparaît à l'écran, carte tournée comprise : plein nord n'est
 * en haut que tant que l'orientation vaut zéro.
 */
private fun MapView.screenAngleOf(bearingDegrees: Float): Float = bearingDegrees + mapOrientation

/**
 * Passe un point de la projection osmdroid (repère « carte au nord », celui que rend `toPixels`)
 * aux coordonnées réellement affichées.
 *
 * osmdroid tourne lui-même le canevas avant de dessiner ses calques : la conversion n'est utile
 * qu'aux dessins posés PAR-DESSUS la MapView (couche fluide du repère, capture de partage), qui
 * n'héritent d'aucune matrice.
 */
private fun MapView.projectedPointToScreen(point: android.graphics.Point) {
    val orientation = mapOrientation
    if (orientation % 360f == 0f) return
    val centerX = width / 2f
    val centerY = height / 2f
    val radians = Math.toRadians(orientation.toDouble())
    val cosAngle = cos(radians).toFloat()
    val sinAngle = sin(radians).toFloat()
    val dx = point.x - centerX
    val dy = point.y - centerY
    point.x = (centerX + dx * cosAngle - dy * sinAngle).roundToInt()
    point.y = (centerY + dx * sinAngle + dy * cosAngle).roundToInt()
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

/** `D:35`, `R:53` — le nom et les départements couverts se relisent dans [FrenchAdminAreas]. */
private fun encodeAdminArea(area: FrenchAdminAreas.Area): String {
    val kind = if (area.kind == FrenchAdminAreas.Kind.REGION) "R" else "D"
    return "$kind:${area.code}"
}

private fun decodeAdminArea(encoded: String?): FrenchAdminAreas.Area? {
    if (encoded.isNullOrBlank()) return null
    val parts = encoded.split(":", limit = 2)
    if (parts.size != 2) return null

    val prefix = if (parts[0] == "R") "region" else "dept"
    return FrenchAdminAreas.match("$prefix:${parts[1]}")
}

private fun encodeBooleanList(values: List<Boolean>): String? {
    return values.takeIf { it.isNotEmpty() }
        ?.joinToString(",") { value -> if (value) "1" else "0" }
}

private fun decodeBooleanList(encoded: String?): List<Boolean> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(",").map { value -> value == "1" }
}

/**
 * Un sommet de la chaîne de mesure : soit un point fixe posé sur la carte (ou une antenne),
 * soit la position courante de l'utilisateur (« ma position »), qui se recalcule en direct.
 */
sealed class MeasureVertex {
    data class Fixed(val point: GeoPoint) : MeasureVertex()
    object CurrentLocation : MeasureVertex()
}

private fun encodeMeasureVertices(vertices: List<MeasureVertex>): String? {
    return vertices.takeIf { it.isNotEmpty() }?.joinToString(";") { vertex ->
        when (vertex) {
            is MeasureVertex.CurrentLocation -> "L"
            is MeasureVertex.Fixed -> "${vertex.point.latitude},${vertex.point.longitude}"
        }
    }
}

private fun decodeMeasureVertices(encoded: String?): List<MeasureVertex> {
    if (encoded.isNullOrBlank()) return emptyList()
    return encoded.split(";").mapNotNull { token ->
        if (token == "L") {
            MeasureVertex.CurrentLocation
        } else {
            val parts = token.split(",", limit = 2)
            val latitude = parts.getOrNull(0)?.toDoubleOrNull()
            val longitude = parts.getOrNull(1)?.toDoubleOrNull()
            if (latitude != null && longitude != null) {
                MeasureVertex.Fixed(GeoPoint(latitude, longitude))
            } else {
                null
            }
        }
    }
}

/**
 * Un trait de la chaîne de mesure, une fois ses sommets résolus en coordonnées : de quoi le
 * dessiner, en calculer la longueur et lui demander un itinéraire, sans retoucher la chaîne.
 */
private data class MeasureSegment(
    val startVertex: MeasureVertex,
    val endVertex: MeasureVertex,
    val start: GeoPoint,
    val end: GeoPoint,
    /** Index du sommet d'arrivée dans la chaîne ; -1 pour le trait de fermeture de la boucle. */
    val toIndex: Int
)

/**
 * La chaîne peut être refermée en boucle si c'est un chemin ouvert unique d'au moins 3 sommets
 * (tous reliés de proche en proche, sans trou).
 */
private fun isMeasureChainClosable(vertices: List<MeasureVertex>, linkedToPrev: List<Boolean>): Boolean =
    vertices.size >= 3 && (1 until vertices.size).all { linkedToPrev.getOrNull(it) == true }

/**
 * Les traits de la chaîne, dans l'ordre : d'abord les segments reliant deux sommets consécutifs,
 * puis le trait de fermeture si la boucle est fermée. Un sommet « ma position » encore inconnu
 * (GPS pas prêt) fait simplement disparaître les traits qui le touchent.
 */
private fun measureSegments(
    vertices: List<MeasureVertex>,
    linkedToPrev: List<Boolean>,
    loopClosed: Boolean,
    myLocation: GeoPoint?
): List<MeasureSegment> {
    fun resolve(vertex: MeasureVertex): GeoPoint? = when (vertex) {
        is MeasureVertex.Fixed -> vertex.point
        MeasureVertex.CurrentLocation -> myLocation
    }

    val segments = mutableListOf<MeasureSegment>()
    for (index in 1 until vertices.size) {
        if (linkedToPrev.getOrNull(index) != true) continue
        val startVertex = vertices[index - 1]
        val endVertex = vertices[index]
        val start = resolve(startVertex) ?: continue
        val end = resolve(endVertex) ?: continue
        segments += MeasureSegment(startVertex, endVertex, start, end, toIndex = index)
    }

    if (loopClosed && isMeasureChainClosable(vertices, linkedToPrev)) {
        val startVertex = vertices.last()
        val endVertex = vertices.first()
        val start = resolve(startVertex)
        val end = resolve(endVertex)
        if (start != null && end != null) {
            segments += MeasureSegment(startVertex, endVertex, start, end, toIndex = -1)
        }
    }
    return segments
}

/**
 * Un itinéraire demandé pour un trait de mesure. Absent du cache = pas encore calculé (le trait
 * reste direct en attendant) ; [Unavailable] = calcul impossible, le trait direct est définitif.
 */
private sealed interface MeasureRoute {
    object Unavailable : MeasureRoute
    data class Ready(val points: List<GeoPoint>, val distanceMeters: Double) : MeasureRoute
}

/**
 * Deux clés, deux rôles.
 *
 * Celle-ci identifie le **trait** : les points posés à pleine précision (deux taps voisins doivent
 * donner deux itinéraires distincts), et « ma position » réduite à un simple marqueur, sans
 * coordonnées. C'est le casier du *dernier itinéraire connu* pour ce trait, et on ne le vide jamais :
 * un recalcul qui échoue — réseau perdu en route, ce qui est la règle sur le terrain — laisse donc le
 * tracé en place au lieu de rendre le trait à la ligne droite. [measureRouteAlignedOnSegment] le
 * recale sur la position du moment.
 */
private fun measureRouteCacheKey(segment: MeasureSegment, profile: String): String {
    fun vertexKey(vertex: MeasureVertex, point: GeoPoint): String = when (vertex) {
        is MeasureVertex.Fixed -> String.format(Locale.US, "%.6f,%.6f", point.latitude, point.longitude)
        MeasureVertex.CurrentLocation -> "L"
    }
    return "$profile|${vertexKey(segment.startVertex, segment.start)}>${vertexKey(segment.endVertex, segment.end)}"
}

/**
 * L'autre clé : la précédente plus « ma position » ramenée sur une grille d'environ 110 m. Elle ne
 * dit pas ce qu'on affiche mais ce qu'on a **déjà demandé** — c'est la cadence de rafraîchissement.
 * Sans cet arrondi, chaque point GPS relancerait une requête.
 */
private fun measureRouteRequestKey(segment: MeasureSegment, profile: String): String {
    fun gridKey(vertex: MeasureVertex, point: GeoPoint): String = when (vertex) {
        is MeasureVertex.Fixed -> ""
        MeasureVertex.CurrentLocation -> String.format(Locale.US, "@%.3f,%.3f", point.latitude, point.longitude)
    }
    return measureRouteCacheKey(segment, profile) +
        gridKey(segment.startVertex, segment.start) +
        gridKey(segment.endVertex, segment.end)
}

/**
 * Nombre de points au-delà duquel un tracé est dessiné en plusieurs morceaux. Confortablement bas :
 * le coût d'un calque de plus est négligeable devant un trait qui manque.
 */
private const val MEASURE_MAX_POINTS_PER_LINE = 2_000

/**
 * Découpe un tracé en morceaux dessinables. Chaque morceau **reprend le dernier point du précédent**,
 * sans quoi il resterait un vide à chaque jonction.
 */
private fun measurePathChunks(path: List<GeoPoint>): List<List<GeoPoint>> {
    if (path.size <= MEASURE_MAX_POINTS_PER_LINE) return listOf(path)
    val chunks = mutableListOf<List<GeoPoint>>()
    var start = 0
    while (start < path.size - 1) {
        val end = minOf(start + MEASURE_MAX_POINTS_PER_LINE - 1, path.size - 1)
        chunks += path.subList(start, end + 1)
        start = end
    }
    return chunks
}

/** Longueur réelle d'une suite de points, segment par segment. */
private fun measurePathLengthMeters(path: List<GeoPoint>): Double {
    var total = 0.0
    for (index in 1 until path.size) total += path[index - 1].distanceToAsDouble(path[index])
    return total
}

/**
 * Où une position tombe sur un tracé : [index] = arête concernée (`points[index]` → `points[index+1]`),
 * [point] = la projection sur cette arête, [gapMeters] = la distance qui les sépare.
 */
private data class MeasureRouteAnchor(val index: Int, val point: GeoPoint, val gapMeters: Double)

/**
 * Projette [target] sur le tracé, en ne considérant que les arêtes à partir de [fromIndex].
 *
 * On projette sur l'arête, et non sur le sommet le plus proche : un sommet fait partir le raccord de
 * biais — un coude net dès que les sommets sont espacés, ce qu'ils sont sur une ligne droite de
 * plusieurs centaines de mètres — et fait avancer la distance restante par paliers d'un sommet.
 */
private fun measureProjectOnRoute(
    points: List<GeoPoint>,
    target: GeoPoint,
    fromIndex: Int
): MeasureRouteAnchor {
    var best = MeasureRouteAnchor(fromIndex, points[fromIndex], Double.MAX_VALUE)
    // Repère local en degrés, un degré de longitude valant cos(latitude) degré de latitude : de quoi
    // projeter juste sur quelques dizaines de mètres, sans trigonométrie par arête.
    val longitudeScale = cos(Math.toRadians(target.latitude))
    for (index in fromIndex until points.size - 1) {
        val from = points[index]
        val to = points[index + 1]
        val fromX = (from.longitude - target.longitude) * longitudeScale
        val fromY = from.latitude - target.latitude
        val deltaX = (to.longitude - from.longitude) * longitudeScale
        val deltaY = to.latitude - from.latitude
        val lengthSquared = deltaX * deltaX + deltaY * deltaY
        val ratio = if (lengthSquared <= 0.0) {
            0.0
        } else {
            (-(fromX * deltaX + fromY * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
        }
        val projected = GeoPoint(
            from.latitude + (to.latitude - from.latitude) * ratio,
            from.longitude + (to.longitude - from.longitude) * ratio
        )
        val gap = projected.distanceToAsDouble(target)
        if (gap < best.gapMeters) best = MeasureRouteAnchor(index, projected, gap)
    }
    return best
}

/**
 * Au-delà, l'itinéraire gardé en mémoire ne décrit plus le trajet en cours : on a roulé loin sans
 * qu'aucun recalcul n'aboutisse, ou on a changé de route pour de bon. Le trait direct est alors plus
 * honnête qu'un tracé d'ailleurs raccordé par une longue barre.
 */
private const val MEASURE_ROUTE_REUSE_MAX_GAP_METERS = 20_000.0

/**
 * Recale un itinéraire du cache sur les extrémités **vivantes** du trait.
 *
 * Le tracé en cache a été calculé depuis une position d'il y a jusqu'à 110 m ([measureRouteRequestKey]),
 * et bien davantage si le réseau est tombé depuis. Rendu tel quel, ça se voit deux fois : le trait
 * repart en arrière vers le point de départ, et la distance affichée reste celle du calcul précédent,
 * figée par paliers au lieu de décompter. On coupe donc la part déjà parcourue — la projection de la
 * position sur le tracé devient la nouvelle amorce — et la distance est recalculée sur ce qui reste,
 * raccords aux deux pastilles compris. Le décompte repart ainsi à chaque point GPS, sans une requête
 * de plus, et sans réseau.
 *
 * Sans sommet mouvant il n'y a rien à recaler : tracé et distance du service sont rendus tels quels.
 */
private fun measureRouteAlignedOnSegment(
    segment: MeasureSegment,
    route: MeasureRoute.Ready?
): MeasureRoute.Ready? {
    if (route == null) return null
    val liveStart = segment.startVertex == MeasureVertex.CurrentLocation
    val liveEnd = segment.endVertex == MeasureVertex.CurrentLocation
    if (!liveStart && !liveEnd) return route
    val points = route.points
    if (points.size < 2) return route

    val head = if (liveStart) measureProjectOnRoute(points, segment.start, 0) else null
    // L'arrivée se cherche après le départ : sur un tracé qui repasse près de soi (aller-retour,
    // boucle), prendre le plus proche dans l'absolu retournerait le trait.
    val tail = if (liveEnd) measureProjectOnRoute(points, segment.end, head?.index ?: 0) else null
    if (maxOf(head?.gapMeters ?: 0.0, tail?.gapMeters ?: 0.0) > MEASURE_ROUTE_REUSE_MAX_GAP_METERS) {
        return null
    }

    // Sommets conservés entre les deux projections, celles-ci prises en pinces.
    val firstKept = head?.let { it.index + 1 } ?: 0
    val lastKept = tail?.index ?: points.lastIndex
    val kept = buildList {
        head?.let { add(it.point) }
        if (firstKept <= lastKept) addAll(points.subList(firstKept, lastKept + 1))
        tail?.let { add(it.point) }
    }
    if (kept.size < 2) return null

    val path = buildList {
        add(segment.start)
        addAll(kept)
        add(segment.end)
    }
    return MeasureRoute.Ready(points = kept, distanceMeters = measurePathLengthMeters(path))
}

/**
 * Point situé à mi-longueur du tracé : c'est là que se pose l'étiquette de distance. Sur un trait
 * direct cela revient au milieu géométrique ; sur un itinéraire, au milieu du parcours réel (et non
 * au milieu de la liste de points, qui se densifie dans les virages).
 */
private fun measureLabelPosition(path: List<GeoPoint>): GeoPoint {
    if (path.size < 2) return path.first()
    val lengths = DoubleArray(path.size - 1) { path[it].distanceToAsDouble(path[it + 1]) }
    val half = lengths.sum() / 2.0
    var walked = 0.0
    for (index in lengths.indices) {
        val length = lengths[index]
        if (walked + length >= half) {
            val ratio = if (length <= 0.0) 0.0 else (half - walked) / length
            val from = path[index]
            val to = path[index + 1]
            return GeoPoint(
                from.latitude + (to.latitude - from.latitude) * ratio,
                from.longitude + (to.longitude - from.longitude) * ratio
            )
        }
        walked += length
    }
    return path[path.size / 2]
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
// Tailles de reference (a 100 %) des controles flottants de la carte.
private val MapControlButtonDiameter = 54.dp
private val MapSearchBarHeight = 54.dp

// Versions mises a l'echelle du slider de taille d'interface : c'est ce que doivent utiliser
// tous les composables (boutons flottants, zoom, barre de recherche), sinon ils restent figes.
private val mapControlButtonDiameter: Dp
    @Composable get() = LocalGeoTowerUiSizing.current.component(MapControlButtonDiameter)

private val mapSearchBarHeight: Dp
    @Composable get() = LocalGeoTowerUiSizing.current.component(MapSearchBarHeight)

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

/** Nombre de suggestions ouvertes sous la barre de recherche. */
private const val MAP_SEARCH_SUGGESTION_COUNT = 3

/**
 * En paysage court, la barre est en bas et la liste s'ouvre au-dessus d'elle : trois lignes
 * arrivent à ras du bord haut de l'écran, et débordent sur un téléphone plus petit.
 */
private const val MAP_SEARCH_SUGGESTION_COMPACT_COUNT = 2

/** Repli du seuil de déclenchement quand le serveur ne dit rien (`mapSearchMinQueryLength`). */
private const val MAP_SEARCH_SUGGESTION_MIN_QUERY_LENGTH = 2

/** Temporisation entre la frappe et l'interrogation de la base. */
private const val MAP_SEARCH_SUGGESTION_DEBOUNCE_MS = 220L

/**
 * Une ligne de la liste ouverte sous la barre de recherche de la carte.
 *
 * Les quatre formes couvrent ce que la barre sait déjà résoudre à la validation : la suggestion
 * n'ouvre pas un chemin à part, elle donne juste le résultat à l'avance et sans ambiguïté.
 */
private sealed interface MapSearchSuggestion {

    /**
     * Commune du référentiel local. [name] est le nom présentable, pas celui stocké en majuscules ;
     * [departmentName] est le nom nu du département, pour lever l'ambiguïté des homonymes auprès du
     * géocodeur — l'affichage y ajoute le code.
     */
    data class Commune(
        val codeInsee: String,
        val name: String,
        val departmentCode: String?,
        val departmentName: String?
    ) : MapSearchSuggestion

    data class AdminArea(val area: FrenchAdminAreas.Area) : MapSearchSuggestion

    data class Site(val site: LocalisationEntity) : MapSearchSuggestion

    data class Operator(val spec: OperatorColorSpec) : MapSearchSuggestion
}

/**
 * Opérateurs dont un alias commence par la saisie, sans exiger le préfixe `op:`.
 *
 * [exactOnly] ne garde que la correspondance pleine : c'est ce qui décide si l'opérateur passe
 * devant les communes. Taper « orange » vise sans doute l'opérateur ; « ora » peut encore être le
 * début d'une commune, l'opérateur attend alors son tour.
 */
private fun matchOperatorSuggestions(query: String, exactOnly: Boolean): List<OperatorColorSpec> {
    val normalizedQuery = normalizeOperatorSearchToken(query)
    if (normalizedQuery.length < 2) return emptyList()

    return OperatorColors.all.filter { spec ->
        (listOf(spec.key, spec.label) + spec.aliases).any { rawAlias ->
            val alias = normalizeOperatorSearchToken(rawAlias)
            if (exactOnly) alias == normalizedQuery else alias.startsWith(normalizedQuery)
        }
    }
}

/**
 * Suggestions d'une saisie, dans l'ordre où [performSearch] les résoudrait à la validation : appuyer
 * sur une ligne doit donner exactement ce que la touche Entrée aurait donné, en levant juste
 * l'ambiguïté (quelle commune, quelle station) que la validation aurait tranchée toute seule.
 *
 * Tout est local — référentiel des communes en base, départements figés dans l'app — sauf le
 * cadrage final d'une commune, qui reste au géocodeur comme aujourd'hui.
 */
private suspend fun buildMapSearchSuggestions(
    viewModel: MapViewModel,
    query: String
): List<MapSearchSuggestion> {
    // Recherche explicite « op: … » : la barre filtre alors la carte au lieu de la déplacer, il n'y
    // a rien d'autre à proposer.
    val explicitOperatorKeys = parseOperatorSearchKeys(query)
    if (explicitOperatorKeys.isNotEmpty()) {
        return explicitOperatorKeys
            .mapNotNull(OperatorColors::specForKey)
            .map(MapSearchSuggestion::Operator)
            .take(MAP_SEARCH_SUGGESTION_COUNT)
    }

    val exactOperators = matchOperatorSuggestions(query, exactOnly = true)
        .map(MapSearchSuggestion::Operator)
    val adminAreas = FrenchAdminAreas.suggest(query, limit = 2)
        .map(MapSearchSuggestion::AdminArea)

    // Même garde que la recherche d'identifiant à la validation : sans chiffre, aucune station ne
    // peut correspondre, et on s'épargne un balayage de la table.
    val sites = if (query.any { it.isDigit() } && query.length >= 3) {
        viewModel.searchSiteSuggestions(query, limit = 2).map(MapSearchSuggestion::Site)
    } else {
        emptyList()
    }

    val communes = viewModel
        .searchCommuneSuggestions(query, limit = MAP_SEARCH_SUGGESTION_COUNT)
        .map(::toCommuneSuggestion)
    val looseOperators = matchOperatorSuggestions(query, exactOnly = false)
        .map(MapSearchSuggestion::Operator)

    return (exactOperators + adminAreas + sites + communes + looseOperators)
        .distinctBy { suggestion ->
            when (suggestion) {
                is MapSearchSuggestion.Commune -> "commune:${suggestion.codeInsee}"
                is MapSearchSuggestion.AdminArea -> "area:${suggestion.area.kind}:${suggestion.area.code}"
                is MapSearchSuggestion.Site -> "site:${suggestion.site.idAnfr}"
                is MapSearchSuggestion.Operator -> "operator:${suggestion.spec.key}"
            }
        }
        .take(MAP_SEARCH_SUGGESTION_COUNT)
}

private fun toCommuneSuggestion(row: CommuneNameRow): MapSearchSuggestion.Commune {
    val departmentCode = FrenchAdminAreas.departmentCodeForInsee(row.codeInsee)
    return MapSearchSuggestion.Commune(
        codeInsee = row.codeInsee,
        name = CommuneNameMatching.displayName(row.nom),
        departmentCode = departmentCode,
        departmentName = departmentCode?.let(FrenchAdminAreas::departmentName)
    )
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
    showProjectSites: Boolean,
    selectedOperatorKeys: Set<String>
): List<String> {
    // Un site sans aucune émission en service relève du statut « En projet », pas de
    // « En service » : les trois statuts sont exclusifs.
    val isProjectSite = !antenna.isDeclaredActive()
    return extractOperatorKeys(antenna.operateur).filter { operatorKey ->
        if (!isOperatorSelected(operatorKey, selectedOperatorKeys)) {
            false
        } else if (isProjectSite) {
            showProjectSites
        } else if (isOperatorDeclaredHs(antenna, operatorKey, hsOperatorMap)) {
            showSitesOutOfService
        } else {
            showSitesInService
        }
    }
}

/**
 * Filtres d'affichage d'un site réel : opérateurs visibles, sous-sol, zone blanche, technologies.
 *
 * Volontairement muet sur les regroupements, l'emprise d'une commune et le slider temporel, qui
 * disent ce qu'on regarde et non ce qui existe. Partagé par la liste de la carte et par le suivi du
 * site le plus proche, qui doit retenir exactement les mêmes sites — mais autour de la position.
 */
private fun passesSiteDisplayFilters(
    antenna: LocalisationEntity,
    hsOperatorMap: Map<String, Set<String>>,
    selectedOperatorKeys: Set<String>,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    showProjectSites: Boolean,
    hideUndergroundSites: Boolean,
    showOnlyZbSites: Boolean,
    frequencyFilter: FrequencyFilterSelection
): Boolean {
    val visibleOperators = visibleOperatorKeysForAntenna(
        antenna = antenna,
        hsOperatorMap = hsOperatorMap,
        showSitesInService = showSitesInService,
        showSitesOutOfService = showSitesOutOfService,
        showProjectSites = showProjectSites,
        selectedOperatorKeys = selectedOperatorKeys
    )
    if (visibleOperators.isEmpty()) return false
    if (hideUndergroundSites && antenna.hasUndergroundSupport == 1) return false
    if (showOnlyZbSites && antenna.isZb != 1) return false
    return frequencyFilter.matchesAntenna(antenna)
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
    reference: MapFilterDefaults.Reference,
    showSitesInService: Boolean,
    showSitesOutOfService: Boolean,
    showProjectSites: Boolean,
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
    projectLabel: String,
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

    if (selectedOperatorKeys != reference.operatorKeys) {
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

    if (frequencyFilter != reference.frequency) {
        val technologyFilters = listOf(
            "2G" to frequencyFilter.show2G,
            "3G" to frequencyFilter.show3G,
            "4G" to frequencyFilter.show4G,
            "5G" to frequencyFilter.show5G,
            "FH" to frequencyFilter.showFh
        )
        val selectedTechnologies = technologyFilters.filter { it.second }.map { it.first }
        val hiddenTechnologies = technologyFilters.filterNot { it.second }.map { it.first }
        val referenceTechnologies = listOf(
            reference.frequency.show2G,
            reference.frequency.show3G,
            reference.frequency.show4G,
            reference.frequency.show5G,
            reference.frequency.showFh
        )
        if (technologyFilters.map { it.second } != referenceTechnologies) {
            activeFilters += "$technologiesLabel: " + summarizedActiveFilterSelection(
                selectedValues = selectedTechnologies,
                hiddenValues = hiddenTechnologies,
                noneLabel = noneLabel,
                exceptLabel = exceptLabel,
                moreLabel = moreLabel
            )
        }

        val frequencyBandFilters = mutableListOf<Pair<String, Boolean>>()
        var bandsDifferFromDefault = false
        fun addBands(showTechnology: Boolean, technology: String, bands: List<Triple<String, Boolean, Boolean>>) {
            if (showTechnology) {
                bands.forEach { (label, isSelected, referenceSelected) ->
                    frequencyBandFilters += "$technology $label" to isSelected
                    if (isSelected != referenceSelected) bandsDifferFromDefault = true
                }
            }
        }

        addBands(
            frequencyFilter.show2G,
            "2G",
            listOf(
                Triple("900 MHz", frequencyFilter.f2G900, reference.frequency.f2G900),
                Triple("1800 MHz", frequencyFilter.f2G1800, reference.frequency.f2G1800)
            )
        )
        addBands(
            frequencyFilter.show3G,
            "3G",
            listOf(
                Triple("900 MHz", frequencyFilter.f3G900, reference.frequency.f3G900),
                Triple("2100 MHz", frequencyFilter.f3G2100, reference.frequency.f3G2100)
            )
        )
        addBands(
            frequencyFilter.show4G,
            "4G",
            listOf(
                Triple("700 MHz", frequencyFilter.f4G700, reference.frequency.f4G700),
                Triple("800 MHz", frequencyFilter.f4G800, reference.frequency.f4G800),
                Triple("900 MHz", frequencyFilter.f4G900, reference.frequency.f4G900),
                Triple("1800 MHz", frequencyFilter.f4G1800, reference.frequency.f4G1800),
                Triple("2100 MHz", frequencyFilter.f4G2100, reference.frequency.f4G2100),
                Triple("2600 MHz", frequencyFilter.f4G2600, reference.frequency.f4G2600)
            )
        )
        addBands(
            frequencyFilter.show5G,
            "5G",
            listOf(
                Triple("700 MHz", frequencyFilter.f5G700, reference.frequency.f5G700),
                Triple("1400 MHz", frequencyFilter.f5G1400, reference.frequency.f5G1400),
                Triple("2100 MHz", frequencyFilter.f5G2100, reference.frequency.f5G2100),
                Triple("3500 MHz", frequencyFilter.f5G3500, reference.frequency.f5G3500),
                Triple("4200 MHz", frequencyFilter.f5G4200, reference.frequency.f5G4200),
                Triple("26 GHz", frequencyFilter.f5G26000, reference.frequency.f5G26000)
            )
        )

        val selectedBands = frequencyBandFilters.filter { it.second }.map { it.first }
        val hiddenBands = frequencyBandFilters.filterNot { it.second }.map { it.first }
        if (frequencyBandFilters.isNotEmpty() && bandsDifferFromDefault) {
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
    val radioDiffersFromDefault = showRadioTv != reference.showRadioTv ||
        showRadioBroadcast != reference.showRadioBroadcast ||
        showRadioPrivateMobile != reference.showRadioPrivateMobile ||
        showRadioFh != reference.showRadioFh ||
        showRadioOther != reference.showRadioOther
    if (radioFilters.isNotEmpty() && radioDiffersFromDefault) {
        activeFilters += "$radioLabel: ${compactActiveFilterValues(radioFilters, moreLabel)}"
    }

    if (showSignalQuestCoveragePoints &&
        (!reference.showSignalQuestCoveragePoints ||
            selectedSignalQuestCoverageOperatorKeys != reference.signalQuestCoverageOperatorKeys)) {
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
    if (showSitesInService != reference.showSitesInService ||
        showSitesOutOfService != reference.showSitesOutOfService ||
        showProjectSites != reference.showProjectSites) {
        val selectedStatuses = listOfNotNull(
            inServiceLabel.takeIf { showSitesInService },
            outOfServiceLabel.takeIf { showSitesOutOfService },
            projectLabel.takeIf { showProjectSites }
        )
        val hiddenStatuses = listOfNotNull(
            inServiceLabel.takeIf { !showSitesInService },
            outOfServiceLabel.takeIf { !showSitesOutOfService },
            projectLabel.takeIf { !showProjectSites }
        )
        siteFilters += summarizedActiveFilterSelection(
            selectedValues = selectedStatuses,
            hiddenValues = hiddenStatuses,
            noneLabel = noneLabel,
            exceptLabel = exceptLabel,
            moreLabel = moreLabel
        )
    }
    if (showOnlyZbSites && !reference.showOnlyZbSites) siteFilters += onlyZbLabel
    if (hideUndergroundSites && !reference.hideUndergroundSites) siteFilters += hideUndergroundLabel
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
    photoDraftId: String? = null,
    // Non nul ⇒ la carte s'ouvre en mode planificateur sur ce trajet (depuis la liste des trajets).
    plannedTripId: String? = null,
    // Comment on ouvre ce trajet : consultation (défaut), édition ou suivi. Voir [TripMapMode].
    plannedTripMode: String? = null,
    // Mode simplifié : fourni par l'hôte qui porte le tiroir. Non nul ⇒ le bouton en haut à
    // gauche ouvre le menu au lieu de revenir en arrière (la carte est la racine du backstack).
    onOpenSimpleModeMenu: (() -> Unit)? = null
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.MAP)
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val screenRotation = currentDisplayRotation(context)

    // --- DISPONIBILITÉ DE LA LOCALISATION (permission + GPS) pour le bouton de recentrage ---
    val locationReadinessState = rememberLocationReadinessState()
    val readiness by locationReadinessState
    val isLocationReady = readiness == LocationReadiness.Ready
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationReadinessState.value = locationReadiness(context)
        if (!granted) {
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false
            if (!canAskAgain) openAppLocationSettings(context)
        }
    }
    // Selon la cause : demander la permission OU ouvrir les réglages de localisation (GPS).
    val onFixLocation: () -> Unit = {
        when (readiness) {
            LocationReadiness.PermissionMissing -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            LocationReadiness.ServicesOff -> openLocationSourceSettings(context)
            LocationReadiness.Ready -> {}
        }
    }
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
    val isLoading by viewModel.isLoading.collectAsState()
    val sitesHs by viewModel.sitesHs.collectAsState()
    val cityStatsTechniques by viewModel.cityStatsTechniques.collectAsState()
    val isCityStatsTechniquesLoading by viewModel.isCityStatsTechniquesLoading.collectAsState()
    val adminAreaOutline by viewModel.adminAreaOutline.collectAsState()
    val adminAreaStatsAntennas by viewModel.adminAreaStatsAntennas.collectAsState()
    val isAdminAreaStatsLoading by viewModel.isAdminAreaStatsLoading.collectAsState()
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
    // Recherche par département / région : seul le code est mémorisé, le référentiel étant figé
    // dans l'app. L'emprise, elle, réutilise `currentSearchAreaBounds` — les deux recherches de
    // zone s'excluent, il n'y en a jamais qu'une active.
    var currentAdminAreaCode by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedAdminAreaKey by remember { mutableStateOf<String?>(null) }
    val currentAdminArea = remember(currentAdminAreaCode) { decodeAdminArea(currentAdminAreaCode) }

    fun setCurrentCitySearch(bounds: SearchAreaBounds?, polygons: List<List<GeoPoint>>?) {
        currentSearchAreaBounds = bounds
        currentSearchAreaBoundsEncoded = encodeSearchAreaBounds(bounds)
        currentCityPolygons = polygons
        currentCityPolygonsEncoded = encodeGeoPointPolygons(polygons)
        currentAdminAreaCode = null
        loadedAdminAreaKey = null
        if (bounds == null || polygons.isNullOrEmpty()) {
            loadedCitySearchKey = null
        }
    }

    fun setCurrentAdminAreaSearch(area: FrenchAdminAreas.Area, bounds: SearchAreaBounds) {
        setCurrentCitySearch(bounds, null)
        currentAdminAreaCode = encodeAdminArea(area)
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

    /**
     * (Ré)applique le filtre de zone au chargement de la carte. Appelé aussi quand le contour
     * arrive du réseau : c'est lui qui permet de trier les regroupements, que le code INSEE ne
     * peut pas atteindre.
     */
    fun applyCurrentAdminAreaFilter(outlinePolygons: List<List<GeoPoint>>?): Boolean {
        val area = currentAdminArea
        val areaCode = currentAdminAreaCode
        if (area == null || areaCode == null) {
            viewModel.clearAdminAreaFilter()
            loadedAdminAreaKey = null
            return false
        }

        val filterKey = "$areaCode|${outlinePolygons?.size ?: 0}"
        if (loadedAdminAreaKey == filterKey) return false
        loadedAdminAreaKey = filterKey

        viewModel.setAdminAreaFilter(area.departmentCodes, outlinePolygons)
        viewModel.loadAdminAreaStats(areaCode, area.departmentCodes)
        return true
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
    val canUseTrips = featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.TRIPS)
    // Sans magnétomètre, la page Boussole n'a rien à montrer : même garde que l'accueil.
    val canUseCompassPage = AppConfig.hasCompass.value &&
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.COMPASS)
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
    var rotationOverlayRef by remember { mutableStateOf<MapRotationGestureOverlay?>(null) }
    val mapViewUsable = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // Supports superposés (mêmes coordonnées GPS) en attente de choix par l'utilisateur.
    var supportChoices by remember { mutableStateOf<List<SupportChoice>>(emptyList()) }

    var currentZoom by remember { mutableDoubleStateOf(15.0) }
    var currentLat by remember { mutableDoubleStateOf(48.8584) }
    var isMeasuringMode by rememberSaveable { mutableStateOf(false) }
    var trackNearestAll by rememberSaveable { mutableStateOf(false) }
    var trackNearestFav by rememberSaveable { mutableStateOf(false) }
    // Recherche demandée alors que le mode d'emploi de la mesure occupe la barre du haut : on
    // explique quoi faire au lieu d'empiler les deux (cf. `showMeasureFirstPointHint`).
    var showMeasureSearchBlockedDialog by remember { mutableStateOf(false) }
    // Chaîne de mesure : suite ordonnée de sommets (point carte, antenne ou « ma position »),
    // reliés deux à deux. measuredLinkedToPrev[i] = true si un trait relie le sommet i au i-1.
    // Le premier sommet n'est jamais relié (measuredLinkedToPrev[0] toujours false).
    var measuredVerticesEncoded by rememberSaveable { mutableStateOf<String?>(null) }
    var measuredLinkedToPrevEncoded by rememberSaveable { mutableStateOf<String?>(null) }
    val measuredSites = remember { mutableStateMapOf<String, LocalisationEntity>() }
    val measuredVertices = remember {
        mutableStateListOf<MeasureVertex>().apply {
            addAll(decodeMeasureVertices(measuredVerticesEncoded))
        }
    }
    val measuredLinkedToPrev = remember {
        mutableStateListOf<Boolean>().apply {
            val decoded = decodeBooleanList(measuredLinkedToPrevEncoded).toMutableList()
            // On garde les deux listes synchronisées et le premier sommet toujours détaché.
            while (decoded.size < measuredVertices.size) decoded.add(true)
            if (decoded.isNotEmpty()) decoded[0] = false
            addAll(decoded.take(measuredVertices.size))
        }
    }
    // Boucle fermée : un trait supplémentaire relie le dernier sommet au premier (chaîne unique
    // d'au moins 3 sommets, sans trou). Persisté séparément (booléen rememberSaveable).
    var measuredLoopClosed by rememberSaveable { mutableStateOf(false) }

    var isClosestSiteExpanded by rememberSaveable { mutableStateOf(true) }
    var isClosestFavSiteExpanded by rememberSaveable { mutableStateOf(true) }
    var isMeasureRouteExpanded by rememberSaveable { mutableStateOf(true) }

    // « Suivre les routes » : le dernier itinéraire connu de chaque trait (clé = measureRouteCacheKey).
    // Il n'est remplacé que par un calcul qui aboutit — jamais effacé par un échec, sans quoi la
    // moindre coupure réseau rendrait le trait à la ligne droite. Une clé absente = rien de calculé
    // encore ; Unavailable = rien n'a jamais abouti (hors couverture BD TOPO, service coupé), le trait
    // reste alors direct — jusqu'à un calcul qui passe, qui prend la place.
    val measureRoutes = remember { mutableStateMapOf<String, MeasureRoute>() }
    // Requêtes déjà lancées (clé = measureRouteRequestKey, qui grille « ma position » à ~110 m) : de
    // quoi ne pas redemander le même itinéraire à chaque point GPS. Pas un état observable — rien ne
    // s'affiche à partir de là, et un échec s'y retire pour laisser une seconde chance.
    val measureRouteRequests = remember { mutableSetOf<String>() }
    // Le service itinéraire IGN peut être coupé à distance ; le trait direct, lui, reste toujours
    // disponible puisqu'il ne demande rien au réseau.
    val canUseMeasureRouting = canUseMapMeasure &&
        featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ROUTING_IGN)
    // Profil d'itinéraire courant, ou null pour le trait direct (aucune requête réseau).
    fun measureRouteProfile(): String? {
        if (!canUseMeasureRouting) return null
        return when (AppConfig.measureFollowRoadsMode.intValue) {
            1 -> RouteApi.PROFILE_CAR
            2 -> RouteApi.PROFILE_PEDESTRIAN
            else -> null
        }
    }

    fun saveMeasureSelections() {
        measuredVerticesEncoded = encodeMeasureVertices(measuredVertices)
        measuredLinkedToPrevEncoded = encodeBooleanList(measuredLinkedToPrev)
    }

    fun clearMeasureSelections() {
        measuredSites.clear()
        measuredVertices.clear()
        measuredLinkedToPrev.clear()
        measuredLoopClosed = false
        // Les itinéraires gardés en cache peuvent peser plusieurs centaines de points chacun : plus
        // de traits, plus de raison de les garder.
        measureRoutes.clear()
        measureRouteRequests.clear()
        saveMeasureSelections()
    }

    // Ajoute un sommet à la fin de la chaîne. Le tout premier sommet n'est relié à rien : le trait
    // n'apparaît donc qu'à partir du 2e point (qui rejoint automatiquement le précédent). Ajouter un
    // point rouvre la boucle : on prolonge le chemin, l'utilisateur pourra la refermer ensuite.
    fun addMeasureVertex(vertex: MeasureVertex) {
        val isFirst = measuredVertices.isEmpty()
        measuredVertices.add(vertex)
        measuredLinkedToPrev.add(!isFirst)
        measuredLoopClosed = false
        saveMeasureSelections()
    }

    // Retire le sommet `index`. reconnect = true : les voisins se rejoignent (recalcule avec le
    // point d'avant). reconnect = false : on conserve le trou, les autres traits ne bougent pas.
    fun removeMeasureVertex(index: Int, reconnect: Boolean) {
        if (index !in measuredVertices.indices) return
        measuredVertices.removeAt(index)
        if (index in measuredLinkedToPrev.indices) measuredLinkedToPrev.removeAt(index)
        // Sans reconnexion, le sommet qui a glissé à `index` ne doit pas se raccrocher au précédent.
        if (!reconnect && index in measuredLinkedToPrev.indices) {
            measuredLinkedToPrev[index] = false
        }
        if (measuredLinkedToPrev.isNotEmpty()) measuredLinkedToPrev[0] = false
        saveMeasureSelections()
    }

    fun isMeasureLoopClosable(): Boolean = isMeasureChainClosable(measuredVertices, measuredLinkedToPrev)

    fun toggleMeasureLoop() {
        measuredLoopClosed = if (measuredLoopClosed) false else isMeasureLoopClosable()
    }

    // Retire les sommets devenus orphelins (plus aucun trait ne les touche) après une suppression,
    // afin de ne pas laisser un point isolé au bout d'un trait supprimé. Appelée uniquement après
    // une suppression : le tout premier point posé (isolé volontairement, avant le 2e tap) n'est
    // donc jamais concerné.
    fun pruneOrphanMeasureVertices() {
        var i = measuredVertices.size - 1
        while (i >= 0) {
            val hasIncoming = measuredLinkedToPrev.getOrNull(i) == true
            val hasOutgoing = measuredLinkedToPrev.getOrNull(i + 1) == true
            if (!hasIncoming && !hasOutgoing) {
                measuredVertices.removeAt(i)
                if (i in measuredLinkedToPrev.indices) measuredLinkedToPrev.removeAt(i)
            }
            i--
        }
        if (measuredLinkedToPrev.isNotEmpty()) measuredLinkedToPrev[0] = false
        // Une boucle n'a plus de sens si le chemin n'est plus une chaîne unique fermable.
        if (measuredLoopClosed && !isMeasureLoopClosable()) measuredLoopClosed = false
        saveMeasureSelections()
    }

    // Supprime le trait qui arrive sur le sommet `toIndex` (segment entre toIndex-1 et toIndex),
    // en respectant le réglage (indépendant vs reconnexion), puis retire les extrémités orphelines.
    fun deleteMeasureSegment(toIndex: Int) {
        // Cas boucle fermée : couper une arête « déplie » la boucle en un chemin ouvert qui conserve
        // toutes les autres arêtes (y compris l'ancienne arête de fermeture).
        if (measuredLoopClosed && isMeasureLoopClosable() && toIndex in 1 until measuredVertices.size) {
            val n = measuredVertices.size
            val rotatedVertices = (0 until n).map { measuredVertices[(toIndex + it) % n] }
            measuredVertices.clear()
            measuredVertices.addAll(rotatedVertices)
            measuredLinkedToPrev.clear()
            measuredLinkedToPrev.addAll(List(n) { it != 0 })
            measuredLoopClosed = false
            saveMeasureSelections()
            return
        }
        if (AppConfig.measureReconnectOnDelete.value) {
            removeMeasureVertex(toIndex, reconnect = true)
        } else if (toIndex in measuredLinkedToPrev.indices) {
            measuredLinkedToPrev[toIndex] = false
            saveMeasureSelections()
        }
        pruneOrphanMeasureVertices()
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
            isMeasureRouteExpanded = true
        }
    }

    // ================= PLANIFICATEUR DE TRAJET =================
    // Ouvert depuis la liste des trajets : la carte devient l'éditeur de la tournée, avec ses
    // marqueurs, ses filtres et sa recherche -- c'est tout l'intérêt d'en faire un mode plutôt
    // qu'un écran à part. Chaque geste écrit dans le trajet : pas de bouton « enregistrer » qu'on
    // puisse oublier en plein terrain.
    var plannerPlan by remember { mutableStateOf<TripPlan?>(null) }
    var plannerBusy by remember { mutableStateOf(false) }
    // Étape dont le menu d'actions est ouvert, et point d'insertion armé : tant qu'il est posé, le
    // prochain point atterrit APRÈS cette étape au lieu d'aller en fin de tournée.
    var plannerStepMenuIndex by remember { mutableStateOf<Int?>(null) }
    var plannerInsertAfterIndex by remember { mutableStateOf<Int?>(null) }
    // Consultation, édition ou suivi. Comme `plannerPlan`, c'est un délégué d'état : le lire dans
    // une lambda mémorisée rend bien la valeur du moment (cf. l'avertissement sur isPlannerMode).
    // Pas de booléen dérivé (`val following = tripMode == ...`) : il serait figé à la composition
    // et les lambdas mémorisées le captureraient périmé. On compare `tripMode` sur place.
    var tripMode by rememberSaveable { mutableStateOf(tripMapModeOrDefault(plannedTripMode)) }
    var plannerFollowStatus by remember { mutableStateOf<TripFollowStatus?>(null) }

    // Cap de navigation : tiré du déplacement, pas de la boussole. Voir TripHeadingSmoother.
    val tripHeadingSmoother = remember { TripHeadingSmoother() }
    var navHeadingDegrees by remember { mutableStateOf<Double?>(null) }

    /**
     * La caméra de suivi colle-t-elle à la position ?
     *
     * Elle lâche prise dès qu'on déplace la carte au doigt — on veut pouvoir regarder la suite du
     * trajet — et se rattache au bouton de recentrage, comme dans les applis de guidage. Sans ça,
     * chaque relevé GPS ramènerait la vue et le geste serait impossible.
     */
    var navCameraLocked by remember { mutableStateOf(true) }

    /**
     * Hauteur à l'écran où poser le repère de position pendant le suivi, mesurée sur la colonne des
     * boutons de zoom plutôt que devinée : le repère se retrouve ainsi à leur niveau, quel que soit
     * l'écran, la taille d'interface ou la hauteur de la barre du bas.
     */
    var navAnchorYPx by remember { mutableIntStateOf(0) }

    // Trajet d'approche : la route entre là où l'on se trouve et l'étape à rejoindre, quand on
    // démarre le suivi loin du départ. Transitoire, donc jamais enregistré dans la tournée.
    var plannerApproachPoints by remember { mutableStateOf<List<DoubleArray>?>(null) }
    var plannerApproachRouted by remember { mutableStateOf(false) }
    var plannerApproachForStep by remember { mutableStateOf<Int?>(null) }
    var plannerApproachAnchor by remember { mutableStateOf<DoubleArray?>(null) }
    // Hauteur réellement mesurée de la barre du trajet : les trois barres (consultation, édition,
    // suivi) n'ont pas la même, et c'est elle qui dit de combien remonter ce qui vit en bas.
    var tripBarHeightPx by remember { mutableIntStateOf(0) }
    // Sortie de l'édition : on demande s'il faut enregistrer, et on nomme/date la tournée si oui.
    var plannerAskToSave by remember { mutableStateOf(false) }
    var plannerSaving by remember { mutableStateOf(false) }

    /**
     * La tournée telle qu'elle était en entrant en édition.
     *
     * L'édition écrit au fil des gestes — il le faut, ne serait-ce que pour garder les segments
     * calculés et survivre à une rotation d'écran. Répondre « ne pas enregistrer » consiste donc à
     * **réécrire cet instantané**, pas à s'abstenir d'écrire. Pour une tournée qui vient d'être
     * créée, l'instantané est le brouillon vide : le restaurer revient bien à ne rien garder,
     * puisque la liste écarte les brouillons sans étape.
     */
    var plannerEditSnapshot by remember { mutableStateOf<TripPlan?>(null) }
    // Le trajet lui-même arrive par une lecture disque (LaunchedEffect), donc une image plus tard.
    // Faire dépendre l'INTERFACE de `plannerPlan` affichait donc une première image en « carte des
    // antennes » — bouton de partage présent, boussole à sa place habituelle — avant que tout ne se
    // reconfigure sous les yeux de l'utilisateur, ce qui se lit comme un bug.
    //
    // On décide donc à partir de l'argument de navigation, connu dès la première composition. Si le
    // trajet s'avère introuvable (supprimé entre-temps), on rend son interface normale à la carte
    // plutôt que de la laisser amputée sans barre.
    var plannerPlanMissing by remember { mutableStateOf(false) }
    val isPlannerMode = !plannedTripId.isNullOrBlank() && !plannerPlanMissing

    // ATTENTION : `isPlannerMode` et `tripMode` conviennent à l'interface, qui se recompose, mais
    // surtout PAS aux lambdas mémorisées -- calque de tap, écouteurs de marqueurs -- qui
    // captureraient la valeur de la toute première composition. Là-bas, on lit `plannerPlan` et
    // `tripMode` directement : ce sont des délégués d'état, donc la lecture rend la valeur du
    // moment. Ne pas « simplifier » en réutilisant un booléen dérivé dans une lambda.
    val tripOverlay = remember { FolderOverlay() }

    LaunchedEffect(plannedTripId) {
        val loaded = plannedTripId?.takeIf { it.isNotBlank() }?.let { id ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                TripPlanStore.readOne(context, id)
            }
        }
        plannerPlan = loaded
        // Identifiant fourni mais trajet absent : on relâche le mode trajet, sinon la carte reste
        // sans son interface et sans barre.
        plannerPlanMissing = !plannedTripId.isNullOrBlank() && loaded == null
        // Les deux modes posent des points : on n'entre pas dans le planificateur en laissant la
        // mesure active.
        if (plannerPlan != null) isMeasuringMode = false
    }

    // Capture l'état d'avant dès qu'on entre en édition, et le relâche en sortant.
    LaunchedEffect(tripMode, plannerPlan?.id) {
        if (tripMode == TRIP_MODE_EDIT) {
            if (plannerEditSnapshot == null) plannerEditSnapshot = plannerPlan
        } else {
            plannerEditSnapshot = null
        }
    }

    fun savePlan(next: TripPlan) {
        plannerPlan = next
        TripPlanStore.save(context, next)
    }

    fun addTripStep(
        latitude: Double,
        longitude: Double,
        label: String,
        kind: String,
        supportId: String? = null
    ) {
        val current = plannerPlan ?: return
        val step = TripStep(
            latitude = latitude,
            longitude = longitude,
            label = label,
            kind = kind,
            supportId = supportId,
            visitedAtMillis = null,
            note = null,
            profileToNext = null
        )
        val insertAt = plannerInsertAfterIndex?.let { (it + 1).coerceIn(0, current.steps.size) }
        plannerInsertAfterIndex = null

        if (insertAt == null) {
            // Ajout en fin de tournée : les segments déjà calculés gardent leurs indices.
            savePlan(current.copy(steps = current.steps + step))
        } else {
            // Insertion au milieu : tous les indices suivants glissent, les segments enregistrés ne
            // désignent plus les bonnes étapes. On repart d'un calcul propre.
            val steps = current.steps.toMutableList().apply { add(insertAt, step) }
            savePlan(current.copy(steps = steps, legs = emptyList()))
        }
    }

    /** Réordonne, supprime ou coche une étape. Tout remaniement d'ordre périme les segments. */
    fun mutateTripSteps(transform: (MutableList<TripStep>) -> Unit) {
        val current = plannerPlan ?: return
        val steps = current.steps.toMutableList().apply(transform)
        savePlan(current.copy(steps = steps, legs = emptyList()))
    }

    fun refreshTripLayers(map: MapView) {
        tripOverlay.items.clear()
        val plan = plannerPlan
        if (plan == null) {
            map.invalidate()
            return
        }

        // Trajet d'approche d'abord, sous la tournée : c'est le chemin pour aller la prendre, pas
        // la tournée elle-même, d'où sa couleur distincte.
        plannerApproachPoints?.takeIf { it.size >= 2 }?.let { approach ->
            tripOverlay.add(
                Polyline(map).apply {
                    outlinePaint.color = TRIP_APPROACH_COLOR
                    outlinePaint.strokeWidth = 7f
                    if (!plannerApproachRouted) {
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                    }
                    setPoints(approach.map { GeoPoint(it[0], it[1]) })
                }
            )
        }

        plan.legPairs().forEach { (fromIndex, toIndex) ->
            val computed = plan.legBetween(fromIndex, toIndex)?.points()?.takeIf { it.size >= 2 }
            // Segment pas encore calculé : trait direct en pointillés. Dire « je ne connais pas
            // encore la route » vaut mieux que d'en dessiner une fausse.
            val legPoints = computed ?: listOf(
                doubleArrayOf(plan.steps[fromIndex].latitude, plan.steps[fromIndex].longitude),
                doubleArrayOf(plan.steps[toIndex].latitude, plan.steps[toIndex].longitude)
            )

            tripOverlay.add(
                Polyline(map).apply {
                    outlinePaint.color = TRIP_STEP_COLOR
                    outlinePaint.strokeWidth = 8f
                    if (computed == null) {
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                    }
                    setPoints(legPoints.map { GeoPoint(it[0], it[1]) })
                }
            )

            // Flèches de sens, posées par-dessus le trait mais sous les pastilles d'étapes.
            tripDirectionArrows(legPoints).forEach { arrow ->
                tripOverlay.add(
                    Marker(map).apply {
                        position = GeoPoint(arrow.latitude, arrow.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createTripArrowIcon(context, arrow.bearingDegrees)
                        // « À plat » : la flèche tourne avec la carte, donc elle continue de
                        // désigner la bonne direction quand on oriente au cap.
                        isFlat = true
                        // `false` = clic non consommé : toucher une flèche doit rester un toucher
                        // de carte, qui ajoute une étape en édition.
                        setOnMarkerClickListener { _, _ -> false }
                    }
                )
            }
        }

        plan.steps.forEachIndexed { index, step ->
            val marker = Marker(map).apply {
                position = GeoPoint(step.latitude, step.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createTripStepIcon(context, index + 1, step.visitedAtMillis != null)
                title = step.label
                // Toucher une pastille ouvre ses actions (déplacer, insérer, supprimer, cocher),
                // jamais une bulle osmdroid.
                setOnMarkerClickListener { _, _ ->
                    // En consultation on ne propose pas d'actions : ce mode ne modifie rien.
                    if (tripMode != TRIP_MODE_VIEW) plannerStepMenuIndex = index
                    true
                }
            }
            tripOverlay.add(marker)
        }
        map.invalidate()
    }

    // Recalcul des segments manquants. La signature ne retient que ce qui invalide un tracé :
    // les positions des étapes, leur ordre, le profil et la fermeture de la boucle.
    val tripRouteSignature = plannerPlan?.let { plan ->
        buildString {
            append(plan.id).append('|').append(plan.profile).append('|').append(plan.returnToStart)
            plan.steps.forEach { append('|').append(it.latitude).append(',').append(it.longitude) }
        }
    }
    LaunchedEffect(tripRouteSignature) {
        val current = plannerPlan ?: return@LaunchedEffect
        if (current.steps.size < 2) {
            mapViewRef?.let { refreshTripLayers(it) }
            return@LaunchedEffect
        }
        plannerBusy = true
        val outcome = runCatching { TripRouteCalculator.computeRoute(current) }.getOrNull()
        plannerBusy = false
        if (outcome != null && outcome.plan.legs != current.legs) {
            savePlan(outcome.plan)
        }
        mapViewRef?.let { refreshTripLayers(it) }
    }

    val measureOverlay = remember { FolderOverlay() }
    val searchBoundaryOverlay = remember { FolderOverlay() }
    // ✅ LE CALQUE MACRO POUR LA VUE DÉZOOMÉE
    val macroOverlay = remember { FolderOverlay() }
    val signalQuestCoverageOverlay = remember { SignalQuestCoverageOverlay(context) }
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
                // Trait plein : l'extérieur est déjà assombri par le masque, donc les pointillés
                // n'apportaient rien et faisaient passer une limite exacte pour un tracé approximatif.
                outlinePaint.pathEffect = null
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
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

    // --- Orientation de la carte ---------------------------------------------------------------
    // Deux réglages distincts : le droit de tourner la carte à deux doigts, et l'alignement
    // automatique sur la boussole. Ce dernier se coupe aussi depuis la carte (appui long sur la
    // boussole) et dès qu'un doigt reprend la main sur la rotation.
    val mapRotationEnabled by AppConfig.mapRotationEnabled
    val followOrientation = PowerProfile.mapFollowOrientation && AppConfig.hasCompass.value
    val setFollowOrientation: (Boolean) -> Unit = remember(prefs) {
        { enabled ->
            AppConfig.mapFollowOrientation.value = enabled
            prefs.edit().putBoolean(AppConfig.PREF_MAP_FOLLOW_ORIENTATION, enabled).apply()
        }
    }
    // Orientation courante, reflétée dans la composition : la MapView n'est pas observable, or la
    // rose des vents du bouton boussole doit tourner avec la carte. Écrite à la cadence des images
    // pendant une rotation : ses lecteurs doivent la lire au dessin (rose des vents) ou dans une
    // couche graphique, jamais dans le corps d'un composable — sans quoi tourner la carte
    // recomposerait tout l'écran, image après image.
    val mapOrientationState = remember {
        mutableFloatStateOf(normalizeMapOrientation(prefs.getFloat(PREF_LAST_MAP_ORIENTATION, 0f)))
    }
    // Appui court sur la rose des vents : nord en haut, et on lâche le cap de l'appareil — sans quoi
    // le capteur reprendrait la main dans la foulée.
    val resetMapOrientation: () -> Unit = {
        if (AppConfig.mapFollowOrientation.value) setFollowOrientation(false)
        mapViewRef?.applyOrientation(0f)
        mapOrientationState.floatValue = 0f
    }

    var myCurrentLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var currentSpeedKmH by remember { mutableIntStateOf(0) }

    // Suivi de tournée : à chaque position reçue, on recalcule où on en est et on coche ce qu'on
    // vient d'atteindre. Placé ici et non dans le bloc du planificateur, parce que `myCurrentLoc`
    // n'est déclarée que maintenant.
    LaunchedEffect(myCurrentLoc, tripMode, plannerPlan?.steps?.size) {
        val plan = plannerPlan
        val location = myCurrentLoc
        if (tripMode != TRIP_MODE_FOLLOW || plan == null || location == null) {
            plannerFollowStatus = null
            return@LaunchedEffect
        }

        val status = computeTripFollowStatus(plan, location.latitude, location.longitude)
        plannerFollowStatus = status

        // Trajet d'approche : démarrer le suivi loin de l'étape à rejoindre est le cas normal --
        // on est chez soi, la tournée commence ailleurs. On calcule donc la route qui y mène depuis
        // là où l'on est, recalculée seulement si l'étape change ou si l'on s'est notablement
        // déplacé, pour ne pas solliciter le service à chaque position reçue.
        val nextIndex = status.nextStepIndex
        val nextStep = nextIndex?.let { plan.steps.getOrNull(it) }
        if (nextStep == null || (status.distanceToNextMeters ?: 0.0) <= NAV_APPROACH_MIN_METERS) {
            if (plannerApproachPoints != null) {
                plannerApproachPoints = null
                plannerApproachForStep = null
                plannerApproachAnchor = null
                mapViewRef?.let { refreshTripLayers(it) }
            }
        } else {
            val anchor = plannerApproachAnchor
            val movedFar = anchor == null || haversineMeters(
                anchor[0], anchor[1], location.latitude, location.longitude
            ) > NAV_APPROACH_REFRESH_METERS

            if (plannerApproachForStep != nextIndex || movedFar) {
                plannerApproachForStep = nextIndex
                plannerApproachAnchor = doubleArrayOf(location.latitude, location.longitude)
                val direct = listOf(
                    doubleArrayOf(location.latitude, location.longitude),
                    doubleArrayOf(nextStep.latitude, nextStep.longitude)
                )
                val routed = runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        RouteApi.getRoutePortions(direct, plan.profile)
                    }
                }.getOrNull()?.firstOrNull()?.points

                // Comme pour un segment de tournée : faute de route connue, trait direct en
                // pointillés plutôt qu'une route inventée.
                plannerApproachRouted = routed != null
                plannerApproachPoints = routed ?: direct
                mapViewRef?.let { refreshTripLayers(it) }
            }
        }

        if (status.reachedStepIndices.isNotEmpty()) {
            val reachedAt = System.currentTimeMillis()
            val steps = plan.steps.mapIndexed { index, step ->
                if (index in status.reachedStepIndices) step.copy(visitedAtMillis = reachedAt) else step
            }
            // Cocher ne change pas l'ordre : les segments calculés restent valables.
            savePlan(plan.copy(steps = steps))
            mapViewRef?.let { refreshTripLayers(it) }
        }
    }


    // --- Déplacement continu du repère de position -------------------------------------------
    // Le GPS ne donne qu'un point par seconde : sans lissage le repère se téléporte. Le moteur
    // interpole, extrapole sur la vitesse et le cap du dernier relevé, et laisse l'estime piétonne
    // prendre le relais quand le signal se tait. Coupé d'office en mode faible consommation.
    val smoothDeadReckoning = remember { PedestrianDeadReckoning() }
    val smoothEngine = remember { SmoothLocationEngine(smoothDeadReckoning) }
    // Compteur d'images : le lire depuis la phase de dessin force le repère à se redessiner avec la
    // projection de l'image courante (cf. la couche fluide, plus bas).
    var smoothFrameTick by remember { mutableIntStateOf(0) }
    val smoothLocationEnabled = PowerProfile.smoothLocation && canUseMapLocation
    // Pinceau partagé par la couche fluide (à l'écran) et par la capture de partage : quand le
    // lissage est actif, le calque osmdroid se tait, donc un map.draw() ne contient PAS le repère.
    // Sans ce second usage, l'image partagée ou copiée sortirait sans point de localisation.
    val locationMarkerPainter = remember(safePrimaryColor) {
        LocationMarkerPainter(context.resources.displayMetrics.density, safePrimaryColor)
    }
    var isToolboxExpanded by rememberSaveable { mutableStateOf(false) }
    var isTimeSliderVisible by rememberSaveable { mutableStateOf(false) }
    var timeSliderThreshold by rememberSaveable { mutableStateOf<Int?>(null) }
    var timeSliderStats by remember { mutableStateOf(TimeSliderStats(emptyMap(), 0)) }
    val timeSliderLift = if (isTimeSliderVisible) sizing.component(104.dp) else 0.dp

    // La barre du trajet occupe le bas de l'écran : sans ce relèvement, la colonne d'infos et les
    // boutons de zoom sont dessinés par-dessus, puisqu'ils viennent après dans le Box.
    //
    // Le calcul part de la hauteur mesurée de la barre, moins ce que ces éléments s'appliquent déjà
    // eux-mêmes (barre système + 32 dp), plus un jeu de 8 dp. Résultat : ils se posent JUSTE
    // au-dessus de la barre, quelle que soit celle des trois qui est affichée -- au lieu de flotter
    // très haut avec une constante taillée pour la plus grande.
    val plannerLift = when {
        !isPlannerMode -> 0.dp

        tripBarHeightPx > 0 -> {
            val barHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                tripBarHeightPx.toDp()
            }
            val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            (barHeight - systemBottom - sizing.spacing(32.dp) + sizing.spacing(8.dp))
                .coerceAtLeast(0.dp)
        }

        // Barre pas encore mesurée (première image) : on part d'une hauteur plausible plutôt que de
        // zéro, sinon les crédits et les boutons de zoom sautent visiblement à son apparition.
        else -> sizing.component(96.dp)
    }
    val todayDateInt = remember {
        val c = java.util.Calendar.getInstance()
        c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)
    }
    // Le slider temporel n'a de sens qu'avec la base ANFR locale (dates par site).
    // L'API live (fallback sans base) ne fournit pas les dates -> on masque le bouton.
    val timeSliderAvailable = AppConfig.localDatabaseState.value ==
        fr.geotower.data.db.GeoTowerDatabaseValidator.LocalDatabaseState.VALID
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = ROOT_FALLBACK_ROUTE)
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
    /**
     * Quitter l'édition d'une tournée : on demande s'il faut l'enregistrer.
     *
     * La question ne se pose que si quelque chose a bougé depuis l'entrée en édition — sinon on
     * sort directement, plutôt que de faire confirmer un travail qui n'a pas eu lieu.
     */
    val leaveTripEditing: () -> Unit = {
        val plan = plannerPlan
        val snapshot = plannerEditSnapshot
        val changed = plan != null && (snapshot == null || !plan.hasSameContentAs(snapshot))
        if (tripMode == TRIP_MODE_EDIT && changed) {
            plannerAskToSave = true
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    /** Renonce aux modifications : on repose l'état d'avant, puis on sort. */
    val discardTripEditing: () -> Unit = {
        plannerEditSnapshot?.let { snapshot ->
            TripPlanStore.save(context, snapshot)
            plannerPlan = snapshot
        }
        plannerAskToSave = false
        safeBackNavigation.navigateBack()
    }

    androidx.activity.compose.BackHandler {
        if (isMeasuringMode) {
            isMeasuringMode = false
            clearMeasureSelections()
        } else if (tripMode == TRIP_MODE_EDIT && plannerPlan != null) {
            leaveTripEditing()
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
    // Sur orthophoto seulement, les marqueurs sont cernés d'un liseré de contraste (cf. MapUtils).
    val satelliteMarkerContrast = MapUtils.isSatelliteBasemap(effectiveProvider, ignStyle)

    var azimuth by remember { mutableFloatStateOf(0f) }
    val continuousAzimuth = remember { floatArrayOf(0f) }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Ouverture de la barre en attente de focus. Volontairement `remember` et non
    // `rememberSaveable` : après une rotation la barre est toujours ouverte, mais relever le
    // clavier tout seul serait intrusif — on ne le lève qu'à l'appui sur le bouton.
    var searchAutoFocusPending by remember { mutableStateOf(false) }

    // Suggestions ouvertes sous la barre pendant la frappe. `submittedSearchQuery` retient la
    // dernière saisie déjà lancée : sans elle, la liste se rouvrirait aussitôt sur le texte resté
    // dans la barre après une recherche, et masquerait le résultat qu'on vient de cadrer.
    var searchSuggestions by remember { mutableStateOf<List<MapSearchSuggestion>>(emptyList()) }
    var submittedSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val showSearchSuggestions = isSearchActive &&
        searchSuggestions.isNotEmpty() &&
        searchQuery.trim() != submittedSearchQuery

    val searchSuggestionMinLength = featureFlags.limitOrDefault(
        RemoteFeatureFlags.Limits.MAP_SEARCH_MIN_QUERY_LENGTH,
        MAP_SEARCH_SUGGESTION_MIN_QUERY_LENGTH
    )
    LaunchedEffect(searchQuery, isSearchActive, canUseMapSearch, searchSuggestionMinLength) {
        val cleanQuery = searchQuery.trim()
        if (!isSearchActive || !canUseMapSearch || cleanQuery.length < searchSuggestionMinLength) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        // Saisie déjà lancée : la barre porte l'intitulé du résultat affiché, rien à proposer
        // dessus. Sans cette garde, choisir une suggestion relancerait aussitôt la recherche
        // complète pour une liste qui resterait fermée.
        if (cleanQuery == submittedSearchQuery) return@LaunchedEffect
        // Une frappe fait repartir cet effet : la temporisation évite d'interroger la base à chaque
        // lettre, et l'annulation de la coroutine se charge d'abandonner la requête précédente.
        delay(MAP_SEARCH_SUGGESTION_DEBOUNCE_MS)
        searchSuggestions = buildMapSearchSuggestions(viewModel, cleanQuery)
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var showCityStatsPopup by rememberSaveable { mutableStateOf(false) }
    var showCityStatsDetail by rememberSaveable { mutableStateOf(false) }
    var isTrackingActive by rememberSaveable { mutableStateOf(false) }

    // ================= CAMÉRA DE SUIVI DE TOURNÉE =================
    // Posé ici, après `isTrackingActive` : la poursuite d'osmdroid recentre la position au milieu
    // de l'écran à chaque image, et annulerait le cadrage « utilisateur en bas » posé plus bas.
    val currentView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(tripMode) {
        val following = tripMode == TRIP_MODE_FOLLOW
        // Une tournée se suit la carte sous les yeux : un écran qui s'éteint toutes les trente
        // secondes rend le suivi inutilisable.
        currentView.keepScreenOn = following

        // Pendant le suivi, l'orientation vient du DÉPLACEMENT et non de la boussole : on coupe
        // donc le suivi de cap magnétique, qui se battrait avec elle -- puis on rend son réglage à
        // l'utilisateur, suivre une tournée une fois ne devant pas changer durablement sa carte.
        val hadFollowOrientation = AppConfig.mapFollowOrientation.value
        if (following && hadFollowOrientation) setFollowOrientation(false)
        if (following) {
            isTrackingActive = false
            locationOverlayRef?.disableFollowLocation()
            mapViewRef?.controller?.setZoom(NAV_FOLLOW_ZOOM)
            navCameraLocked = true
        }

        onDispose {
            currentView.keepScreenOn = false
            if (following && hadFollowOrientation) setFollowOrientation(true)
            tripHeadingSmoother.reset()
            navHeadingDegrees = null
        }
    }

    // Cap de marche en haut, utilisateur en bas de l'écran : le cadrage des applis de guidage.
    LaunchedEffect(myCurrentLoc, navHeadingDegrees, tripMode, navCameraLocked) {
        if (tripMode != TRIP_MODE_FOLLOW || !navCameraLocked) return@LaunchedEffect
        val map = mapViewRef ?: return@LaunchedEffect
        val location = myCurrentLoc ?: return@LaunchedEffect

        val heading = navHeadingDegrees
        if (heading != null) {
            val orientation = normalizeMapOrientation(-heading.toFloat())
            map.mapOrientation = orientation
            mapOrientationState.floatValue = orientation
        }

        // Où poser le repère à l'écran : à la hauteur des boutons de zoom, mesurée et non devinée.
        val aheadFraction = if (navAnchorYPx > 0 && map.height > 0) {
            (navAnchorYPx.toDouble() / map.height - 0.5)
                .coerceIn(0.0, NAV_CAMERA_MAX_AHEAD_FRACTION)
        } else {
            NAV_CAMERA_AHEAD_FRACTION
        }

        // Sans cap fiable (à l'arrêt, au tout début), on vise dans l'axe de la carte : le repère se
        // pose au même endroit, au lieu de sauter au centre puis de redescendre au premier mètre
        // parcouru.
        val effectiveHeading = heading ?: normalizeDegrees(-map.mapOrientation.toDouble())

        val target = navigationCameraTarget(
            latitude = location.latitude,
            longitude = location.longitude,
            headingDegrees = effectiveHeading,
            zoom = map.zoomLevelDouble,
            screenHeightPixels = map.height,
            aheadFraction = aheadFraction
        )
        map.controller.setCenter(GeoPoint(target[0], target[1]))
    }

    // Appui long sur la rose des vents. Aligner la carte sur son cap depuis l'autre bout de la
    // France n'aurait aucun sens : en allumant le suivi, on ramène la carte sur la position, au
    // même cadrage que le bouton de localisation (zoom du réglage « Zoom du bouton GPS »).
    val applyFollowOrientation: (Boolean) -> Unit = { enabled ->
        setFollowOrientation(enabled)
        val map = mapViewRef
        if (enabled && map != null && isLocationReady && canUseMapLocation) {
            val locationOverlay = locationOverlayRef

            fun centerOn(location: GeoPoint) {
                val zoom = preferredLocationZoom()
                map.controller.stopAnimation(false)
                map.controller.setZoom(zoom)
                map.controller.setCenter(location)
                currentZoom = zoom
                currentLat = location.latitude
            }

            val known = locationOverlay?.myLocation ?: myCurrentLoc
            if (known != null) {
                centerOn(known)
            } else if (locationOverlay != null) {
                // Pas encore de relevé : on recentre au premier qui tombe plutôt que de ne rien faire.
                locationOverlay.enableMyLocation()
                locationOverlay.runOnFirstFix {
                    locationOverlay.myLocation?.let { first -> map.post { centerOn(first) } }
                }
            }
        }
    }
    val toggleFollowOrientation: () -> Unit = {
        applyFollowOrientation(!AppConfig.mapFollowOrientation.value)
    }

    /**
     * À appeler avant de cadrer la carte sur un résultat de recherche.
     *
     * La poursuite GPS recentre la carte à chaque image et avale le glissement à un doigt : sans
     * cette main levée, la ville ou le département cherché serait cadré puis aussitôt ramené sur la
     * position, et la recherche paraîtrait sans effet.
     */
    val releaseLocationFollowForSearch: () -> Unit = {
        if (isTrackingActive) {
            isTrackingActive = false
            locationOverlayRef?.disableFollowLocation()
        }
    }

    val txtMapTitle = stringResource(R.string.appstrings_map_title)
    val txtTripMapTitle = stringResource(R.string.trips_map_header)
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
    val txtProjectSites = stringResource(R.string.appstrings_sites_project_label)
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
        reference = MapFilterDefaults.reference(prefs),
        showSitesInService = AppConfig.showSitesInService.value,
        showSitesOutOfService = AppConfig.showSitesOutOfService.value,
        showProjectSites = AppConfig.showProjectSites.value,
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
        projectLabel = txtProjectSites,
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
                // L'estime piétonne oriente chaque pas sur ce cap : on lui donne la valeur lissée
                // complète, sans passer par l'état d'interface qui, lui, est bridé en cadence.
                smoothDeadReckoning.setHeading(smoothedAzimuth)

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
                            .putFloat(PREF_LAST_MAP_ORIENTATION, map.mapOrientation)
                            .apply()
                    }

                    mapViewRef?.onPause()
                    locationOverlayRef?.disableMyLocation()
                    sensorManager?.unregisterListener(sensorEventListener)
                    // Sans relevé pendant la mise en veille, l'ancre du lissage devient périmée :
                    // on repart d'une page blanche plutôt que de faire glisser le repère au retour.
                    smoothEngine.reset()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            smoothEngine.reset()
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

    // Accéléromètre : uniquement pour compter les pas quand le GPS décroche (estime piétonne).
    // Capteur à faible consommation, mais on ne l'allume que si le lissage est réellement actif.
    DisposableEffect(lifecycleOwner, smoothLocationEnabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = if (smoothLocationEnabled) {
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            null
        }

        if (sensorManager == null || accelerometer == null) {
            smoothDeadReckoning.reset()
            return@DisposableEffect onDispose { }
        }

        val stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Horloge volontairement identique à celle des relevés GPS : les pas et les points
                // sont comparés entre eux, la base de temps des capteurs ne l'est pas partout.
                smoothDeadReckoning.onAccelerometerSample(
                    SystemClock.elapsedRealtime(),
                    event.values[0],
                    event.values[1],
                    event.values[2]
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    sensorManager.registerListener(stepListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(stepListener)
                    smoothDeadReckoning.reset()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(stepListener)
            smoothDeadReckoning.reset()
        }
    }

    // ✅ 1. On déclare la liste filtrée comme une variable d'état
    var filteredAntennas by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }
    var filteredAdminAreaAntennas by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }

    // ✅ 2. LaunchedEffect pour calculer en arrière-plan
    LaunchedEffect(
        antennas, AppConfig.selectedOperatorKeys.value,
        AppConfig.showTechnoFH.value, AppConfig.showTechno2G.value, AppConfig.showTechno3G.value, AppConfig.showTechno4G.value, AppConfig.showTechno5G.value,
        AppConfig.f2G_900.value, AppConfig.f2G_1800.value, AppConfig.f3G_900.value, AppConfig.f3G_2100.value,
        AppConfig.f4G_700.value, AppConfig.f4G_800.value, AppConfig.f4G_900.value, AppConfig.f4G_1800.value, AppConfig.f4G_2100.value, AppConfig.f4G_2600.value,
        AppConfig.f5G_700.value, AppConfig.f5G_1400.value, AppConfig.f5G_2100.value, AppConfig.f5G_3500.value, AppConfig.f5G_4200.value, AppConfig.f5G_26000.value,
        AppConfig.showSitesInService.value, AppConfig.showSitesOutOfService.value, AppConfig.hideUndergroundSites.value, AppConfig.showOnlyZbSites.value,
        AppConfig.showProjectSites.value, sitesHs, currentCityPolygons,
        isTimeSliderVisible, timeSliderThreshold, adminAreaStatsAntennas
    ) {
        val computed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val selectedOperators = AppConfig.selectedOperatorKeys.value
            val showSitesInService = AppConfig.showSitesInService.value
            val showSitesOutOfService = AppConfig.showSitesOutOfService.value
            val hideUndergroundSites = AppConfig.hideUndergroundSites.value
            val showOnlyZbSites = AppConfig.showOnlyZbSites.value
            val showProjectSites = AppConfig.showProjectSites.value
            val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
            val hsOperatorMap = buildHsOperatorMap(sitesHs)

            fun applyDisplayFilters(source: List<LocalisationEntity>) = source.filter { antenna ->
                // ✅ 1. ON VÉRIFIE LES OPÉRATEURS TOUT DE SUITE
                // 🚨 2. LA CORRECTION : Si c'est un cluster, on vérifie au moins l'opérateur !
                if (antenna.idAnfr.startsWith("CLUSTER_")) {
                    val visibleOperators = visibleOperatorKeysForAntenna(
                        antenna = antenna,
                        hsOperatorMap = hsOperatorMap,
                        showSitesInService = showSitesInService,
                        showSitesOutOfService = showSitesOutOfService,
                        showProjectSites = showProjectSites,
                        selectedOperatorKeys = selectedOperators
                    )
                    return@filter frequencyFilter.isFullyEnabled &&
                        visibleOperators.isNotEmpty() &&
                        (!showOnlyZbSites || antenna.isZb == 1)
                }

                // --- 3. POUR LES VRAIES ANTENNES, ON CONTINUE AVEC LE RESTE DES FILTRES ---
                val isInCityBounds = currentCityPolygons.isNullOrEmpty() || currentCityPolygons!!.any { poly -> isPointInPolygon(antenna.latitude, antenna.longitude, poly) }

                isInCityBounds && passesSiteDisplayFilters(
                    antenna = antenna,
                    hsOperatorMap = hsOperatorMap,
                    selectedOperatorKeys = selectedOperators,
                    showSitesInService = showSitesInService,
                    showSitesOutOfService = showSitesOutOfService,
                    showProjectSites = showProjectSites,
                    hideUndergroundSites = hideUndergroundSites,
                    showOnlyZbSites = showOnlyZbSites,
                    frequencyFilter = frequencyFilter
                )
            }

            val base = applyDisplayFilters(antennas)
            // Les compteurs d'une zone administrative portent sur toute la zone, pas sur ce qui est
            // à l'écran : mêmes filtres d'affichage, autre source.
            val areaStats = applyDisplayFilters(adminAreaStatsAntennas)

            // Slider temporel : on ne garde que les sites mis en service avant le seuil choisi,
            // et on compte les sites visibles par operateur (+ ceux sans date exploitable).
            if (!isTimeSliderVisible) {
                Triple(base, null, areaStats)
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
                Triple(finalVisible, TimeSliderStats(counts, undated), areaStats)
            }
        }
        filteredAntennas = computed.first
        computed.second?.let { timeSliderStats = it }
        filteredAdminAreaAntennas = computed.third
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

    // Pastille bleue cerclée de blanc pour matérialiser un sommet posé par l'utilisateur.
    // highlight = true : sommet de départ d'une chaîne fermable, dessiné en « cible » creuse et
    // agrandie pour inviter à cliquer dessus afin de fermer la boucle.
    // Le bitmap est plus grand que le disque visible (marge transparente) : la zone tactile — donc
    // le clic sur le sommet — reste généreuse alors que le point affiché reste petit.
    fun createMeasurePointIcon(highlight: Boolean = false): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val visibleDiameter = (if (highlight) 24f else 15f) * density
        val touchDiameter = (if (highlight) 44f else 34f) * density
        val sizePx = touchDiameter.toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val ringWidth = visibleDiameter / 6f
        val radius = visibleDiameter / 2f - ringWidth
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#3B5998")
            style = Paint.Style.FILL
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
        }
        canvas.drawCircle(center, center, radius, fillPaint)
        if (highlight) {
            // Trou blanc central -> aspect anneau/cible qui distingue le point de départ.
            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, radius * 0.42f, holePaint)
        }
        canvas.drawCircle(center, center, radius, ringPaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun refreshMeasureLayers(map: MapView) {
        measureOverlay.items.clear()
        val myLoc = myCurrentLoc ?: locationOverlayRef?.myLocation
        val measurePointIcon = createMeasurePointIcon(highlight = false)
        val measureLoopAnchorIcon = createMeasurePointIcon(highlight = true)

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

        // route = itinéraire calculé pour ce trait (mode « par la route / par les chemins ») ; null
        // ⇒ trait direct, à vol d'oiseau, et distance à vol d'oiseau.
        fun addMeasureSegment(
            startPoint: GeoPoint,
            endPoint: GeoPoint,
            route: MeasureRoute.Ready?,
            onRemove: () -> Unit
        ) {
            // Le moteur d'itinéraire part du point du réseau le plus proche, pas du point posé : on
            // raccorde les extrémités réelles pour que le trait touche bien les deux pastilles.
            val path = if (route != null) {
                buildList {
                    add(startPoint)
                    addAll(route.points)
                    add(endPoint)
                }
            } else {
                listOf(startPoint, endPoint)
            }
            // Un itinéraire long compte des dizaines de milliers de points, et un chemin aussi
            // complexe finit par être rogné au dessin (le rendu matériel abandonne au-delà d'une
            // certaine taille) : c'est le trait qui manque par endroits. On le découpe donc en
            // tronçons qui se partagent leur point de jonction — raccord invisible, les bouts étant
            // ronds — pour que chaque chemin dessiné reste simple.
            measurePathChunks(path).forEach { chunk ->
                val line = Polyline(map).apply {
                    setPoints(chunk)
                    outlinePaint.color = android.graphics.Color.parseColor("#3B5998")
                    outlinePaint.strokeWidth = 10f
                    // Un itinéraire enchaîne des centaines de courts segments, et osmdroid ne touche
                    // ni aux jointures ni aux bouts de son Paint : restent les valeurs par défaut,
                    // jointure en pointe et bout coupé net. Passé la limite d'onglet, une pointe est
                    // rabattue en biseau — d'où ces angles coupés et ces creux dans les virages
                    // serrés. En rond, les segments se raccordent sans manque et la courbe est lisse.
                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                }
                line.setOnClickListener { _, _, _ ->
                    onRemove()
                    refreshMeasureLayers(map)
                    true
                }
                measureOverlay.add(line)
            }

            val labelMarker = Marker(map).apply {
                position = measureLabelPosition(path)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createDistanceLabel(
                    formatMeasureDistance(route?.distanceMeters ?: startPoint.distanceToAsDouble(endPoint))
                )
                infoWindow = null
            }
            labelMarker.setOnMarkerClickListener { _, _ ->
                onRemove()
                refreshMeasureLayers(map)
                true
            }
            measureOverlay.add(labelMarker)
        }

        // Pastille cliquable matérialisant un sommet. highlight = point de départ d'une chaîne
        // fermable (affiché en cible). onTap gère le clic (fermeture de boucle ou suppression).
        fun addMeasurePointMarker(point: GeoPoint, highlight: Boolean, onTap: () -> Unit) {
            val marker = Marker(map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = if (highlight) measureLoopAnchorIcon else measurePointIcon
                infoWindow = null
            }
            marker.setOnMarkerClickListener { _, _ ->
                onTap()
                refreshMeasureLayers(map)
                true
            }
            measureOverlay.add(marker)
        }

        fun resolveVertex(vertex: MeasureVertex): GeoPoint? = when (vertex) {
            is MeasureVertex.Fixed -> vertex.point
            MeasureVertex.CurrentLocation -> myLoc
        }

        // 1) et 2) Un trait + une étiquette de distance par segment de la chaîne, trait de fermeture
        //    de la boucle compris (toIndex = -1). Le trait épouse le réseau routier dès que son
        //    itinéraire est arrivé, sinon il reste direct.
        val routeProfile = measureRouteProfile()
        measureSegments(
            vertices = measuredVertices,
            linkedToPrev = measuredLinkedToPrev,
            loopClosed = measuredLoopClosed,
            myLocation = myLoc
        ).forEach { segment ->
            val route = measureRouteAlignedOnSegment(
                segment,
                routeProfile
                    ?.let { profile -> measureRoutes[measureRouteCacheKey(segment, profile)] }
                    ?.let { it as? MeasureRoute.Ready }
            )
            addMeasureSegment(segment.start, segment.end, route) {
                if (segment.toIndex >= 0) deleteMeasureSegment(segment.toIndex) else measuredLoopClosed = false
            }
        }

        // 3) Pastilles des sommets, ajoutées EN DERNIER pour être AU-DESSUS des traits : un tap sur un
        //    point atteint ainsi la pastille et non le trait qui passe dessous. Le tout premier point
        //    est compris (y compris « ma position »), ce qui montre que le tap a été pris en compte.
        //    Cliquer le point de départ ferme (ou rouvre) la boucle ; sinon un tap supprime le point.
        measuredVertices.forEachIndexed { index, vertex ->
            val point = resolveVertex(vertex) ?: return@forEachIndexed
            val isLoopAnchor = index == 0 && !measuredLoopClosed && isMeasureLoopClosable()
            addMeasurePointMarker(point, highlight = isLoopAnchor) {
                if (index == 0 && (measuredLoopClosed || isMeasureLoopClosable())) {
                    toggleMeasureLoop()
                } else {
                    removeMeasureVertex(index, reconnect = AppConfig.measureReconnectOnDelete.value)
                    pruneOrphanMeasureVertices()
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
                // Même finition que les traits de la chaîne, pour que les deux se ressemblent.
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
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
                // Le planificateur passe avant la mesure : les deux posent des points, mais on
                // n'entre pas dans l'un en laissant l'autre actif.
                // En suivi, toucher la carte ne pose plus rien : la tournée est arrêtée, on la
                // parcourt.
                if (plannerPlan != null && tripMode == TRIP_MODE_EDIT) {
                    val map = mapViewRef ?: return true
                    addTripStep(p.latitude, p.longitude, "", TripStep.KIND_MANUAL)
                    refreshTripLayers(map)
                    return true
                }
                if (!isMeasuringMode) return false
                val map = mapViewRef ?: return true

                // Si le tap tombe sur (ou tout près de) la position de l'utilisateur, on ajoute un
                // sommet « ma position » qui se recalcule en direct ; sinon un point fixe sur la carte.
                val myLoc = myCurrentLoc ?: locationOverlayRef?.myLocation
                val tappedOnMyLocation = myLoc != null && run {
                    val projection = map.projection
                    val tapPixel = projection.toPixels(p, null)
                    val locationPixel = projection.toPixels(myLoc, null)
                    val dx = (tapPixel.x - locationPixel.x).toDouble()
                    val dy = (tapPixel.y - locationPixel.y).toDouble()
                    val thresholdPx = 28f * context.resources.displayMetrics.density
                    dx * dx + dy * dy <= thresholdPx * thresholdPx
                }

                if (tappedOnMyLocation) {
                    addMeasureVertex(MeasureVertex.CurrentLocation)
                } else {
                    addMeasureVertex(MeasureVertex.Fixed(GeoPoint(p.latitude, p.longitude)))
                }
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

    // Décide, au clic sur un marqueur, s'il faut ouvrir directement le support ou
    // demander lequel regarder quand plusieurs supports physiques distincts partagent
    // exactement les mêmes coordonnées (erreurs de saisie ANFR).
    fun handleSupportTapFromMap(map: MapView, siteAntennas: List<LocalisationEntity>) {
        val distinct = siteAntennas.distinctBy { it.idAnfr }
        val firstAntenna = distinct.firstOrNull() ?: return
        // Un seul id_anfr => aucune ambiguïté possible, on évite toute requête.
        if (distinct.size == 1) {
            openSupportDetailFromMap(map, firstAntenna)
            return
        }
        scope.launch {
            val choices = try {
                viewModel.resolveSupportChoices(distinct)
            } catch (e: Exception) {
                AppLogger.w("GeoTowerMap", "Support choice resolution failed", e)
                emptyList()
            }
            // <=1 support physique => plusieurs opérateurs sur le même support : ouverture directe.
            if (choices.size <= 1) {
                openSupportDetailFromMap(map, firstAntenna)
            } else {
                supportChoices = choices
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
            val azimuthMarker = RadioMarker(map, item, showCircle = false, satelliteContrast = satelliteMarkerContrast).apply {
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

            val marker = RadioMarker(map, item, showRadioCircle, satelliteMarkerContrast).apply {
                position = GeoPoint(item.latitude, item.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MapUtils.createRadioMarkerIcon(context, item.serviceMask, item.systemMask, item.clusterCount)
                title = item.title(context)
                snippet = item.subtitle(context)
                setOnMarkerClickListener { clickedMarker, mapView ->
                    if (plannerPlan != null && tripMode == TRIP_MODE_EDIT) {
                        addTripStep(
                            latitude = item.latitude,
                            longitude = item.longitude,
                            label = item.title(context),
                            kind = TripStep.KIND_SITE
                        )
                        refreshTripLayers(map)
                    } else if (isMeasuringMode) {
                        addMeasureVertex(MeasureVertex.Fixed(GeoPoint(item.latitude, item.longitude)))
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
        val showProjectSites = AppConfig.showProjectSites.value
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
                val groupedSites = antennas.groupBy { "${it.latitude}_${it.longitude}" }.values.take(PowerProfile.mapMarkerCap)

                return groupedSites.mapNotNull { siteAntennas ->
                    val filteredSiteAntennas = siteAntennas.mapNotNull { antenna ->
                        val activeOps = visibleOperatorKeysForAntenna(
                            antenna = antenna,
                            hsOperatorMap = hsOperatorMap,
                            showSitesInService = showSitesInService,
                            showSitesOutOfService = showSitesOutOfService,
                            showProjectSites = showProjectSites,
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
                    AntennaMarker(map, filteredSiteAntennas, safePrimaryColor, satelliteMarkerContrast).apply {
                        position = GeoPoint(mainAntenna.latitude, mainAntenna.longitude)
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)

                        infoWindow = null // Pas de bulle grise par defaut

                        val operatorsOnSite = filteredSiteAntennas.mapNotNull { it.operateur }
                            .flatMap { OperatorColors.keysFor(it) }
                            .distinct()
                        relatedObject = operatorsOnSite

                        val baseIcon = MapUtils.createAdaptiveMarker(
                            context,
                            filteredSiteAntennas,
                            false,
                            AppConfig.defaultOperator.value,
                            satelliteContrast = satelliteMarkerContrast
                        )

                        if (isHs) {
                            icon = createHsMarkerIcon(context, baseIcon)
                        } else {
                            icon = baseIcon
                        }

                        setOnMarkerClickListener { _, _ ->
                            if (plannerPlan != null && tripMode == TRIP_MODE_EDIT) {
                                addTripStep(
                                    latitude = mainAntenna.latitude,
                                    longitude = mainAntenna.longitude,
                                    label = mainAntenna.operateur.orEmpty(),
                                    kind = TripStep.KIND_SITE
                                )
                                refreshTripLayers(map)
                            } else if (isMeasuringMode) {
                                addMeasureVertex(MeasureVertex.Fixed(GeoPoint(mainAntenna.latitude, mainAntenna.longitude)))
                                refreshMeasureLayers(map)
                            } else {
                                handleSupportTapFromMap(map, filteredSiteAntennas)
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
                            showProjectSites = showProjectSites,
                            selectedOperatorKeys = selectedOperators
                        )
                        icon = MapUtils.createClusterIcon(
                            context,
                            activeOps,
                            count,
                            AppConfig.defaultOperator.value,
                            satelliteMarkerContrast
                        )
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
                val groupedSites = antennasList.groupBy { "${it.latitude}_${it.longitude}" }.values.take(PowerProfile.mapMarkerCap)

                // ✅ RETOUR À map : 1 seul marqueur définitif par pylône
                val newMarkers = groupedSites.mapNotNull { siteAntennas ->
                    val filteredSiteAntennas = siteAntennas.mapNotNull { antenna ->
                        val activeOps = visibleOperatorKeysForAntenna(
                            antenna = antenna,
                            hsOperatorMap = hsOperatorMap,
                            showSitesInService = showSitesInService,
                            showSitesOutOfService = showSitesOutOfService,
                            showProjectSites = showProjectSites,
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
                    AntennaMarker(map, filteredSiteAntennas, safePrimaryColor, satelliteMarkerContrast).apply {
                        position = GeoPoint(mainAntenna.latitude, mainAntenna.longitude)
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)

                        infoWindow = null // Pas de bulle grise par défaut

                        val operatorsOnSite = filteredSiteAntennas.mapNotNull { it.operateur }
                            .flatMap { OperatorColors.keysFor(it) }
                            .distinct()
                        relatedObject = operatorsOnSite

                        // 1. On génère l'icône de base (avec la bordure de couleur de l'opérateur)
                        val baseIcon = MapUtils.createAdaptiveMarker(
                            context,
                            filteredSiteAntennas,
                            false,
                            AppConfig.defaultOperator.value,
                            satelliteContrast = satelliteMarkerContrast
                        )

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
                            if (plannerPlan != null && tripMode == TRIP_MODE_EDIT) {
                                addTripStep(
                                    latitude = mainAntenna.latitude,
                                    longitude = mainAntenna.longitude,
                                    label = mainAntenna.operateur.orEmpty(),
                                    kind = TripStep.KIND_SITE
                                )
                                refreshTripLayers(map)
                            } else if (isMeasuringMode) {
                                addMeasureVertex(MeasureVertex.Fixed(GeoPoint(mainAntenna.latitude, mainAntenna.longitude)))
                                refreshMeasureLayers(map)
                            } else {
                                handleSupportTapFromMap(map, filteredSiteAntennas)
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
        satelliteMarkerContrast, // bascule plan <-> satellite : les icônes changent de liseré
        AppConfig.showAzimuths.value,
        AppConfig.showAzimuthsCone.value,
        PowerProfile.level, // mode faible conso : reconstruit + invalide (cônes/plafond/repère)
        AppConfig.selectedOperatorKeys.value,
        AppConfig.showSitesInService.value,
        AppConfig.showSitesOutOfService.value,
        AppConfig.hideUndergroundSites.value,
        AppConfig.showOnlyZbSites.value,
        AppConfig.showProjectSites.value,
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

    LaunchedEffect(radioMarkers, filteredAntennas, AppConfig.radioMapCategoryMask(), isMeasuringMode, satelliteMarkerContrast) {
        mapViewRef?.let { map ->
            updateRadioMarkers(map, radioMarkers)
        }
    }

    LaunchedEffect(signalQuestCoveragePoints) {
        signalQuestCoverageOverlay.setPoints(signalQuestCoveragePoints)
        mapViewRef?.invalidate()
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

    // Le contour d'un département vient du réseau : il arrive après les sites, et seulement si la
    // zone dessinée est bien celle qui est sélectionnée.
    val searchBoundaryPolygons = currentCityPolygons
        ?: adminAreaOutline?.takeIf { it.areaCode == currentAdminAreaCode }?.polygons

    // Les compteurs d'une zone administrative ne peuvent pas venir de la carte : à cette échelle
    // elle n'affiche que des regroupements, qui ne se comptent pas.
    val statsAntennas = if (currentAdminArea != null) filteredAdminAreaAntennas else filteredAntennas
    val statsLoading = if (currentAdminArea != null) isAdminAreaStatsLoading else isLoading

    LaunchedEffect(currentAdminAreaCode) {
        val area = currentAdminArea
        val areaCode = currentAdminAreaCode
        if (area == null || areaCode == null) {
            viewModel.clearAdminAreaOutline()
        } else {
            viewModel.loadAdminAreaOutline(
                areaCode = areaCode,
                areaName = area.name,
                isRegion = area.kind == FrenchAdminAreas.Kind.REGION
            )
        }
    }

    LaunchedEffect(
        mapViewRef,
        currentSearchAreaBoundsEncoded,
        currentCityPolygonsEncoded,
        currentAdminAreaCode,
        searchBoundaryPolygons
    ) {
        mapViewRef?.let { map ->
            refreshSearchBoundaryOverlay(map, searchBoundaryPolygons)
            loadCurrentCitySearchIfNeeded()
        }
        val areaFilterChanged = applyCurrentAdminAreaFilter(
            adminAreaOutline?.takeIf { it.areaCode == currentAdminAreaCode }?.polygons
        )
        if (areaFilterChanged) {
            mapViewRef?.loadVisibleAntennas(viewModel)
        }
    }

    LaunchedEffect(
        sitesHs,
        AppConfig.showSitesInService.value,
        AppConfig.showSitesOutOfService.value,
        AppConfig.hideUndergroundSites.value,
        AppConfig.showOnlyZbSites.value,
        AppConfig.showProjectSites.value,
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
        if (isMeasuringMode && (measuredSites.isNotEmpty() || measuredVertices.isNotEmpty())) {
            mapViewRef?.let { refreshMeasureLayers(it) }
        }
    }

    LaunchedEffect(isMeasuringMode, measuredVertices.size, measuredLinkedToPrev.size, measuredLoopClosed, mapViewRef) {
        if (isMeasuringMode && measuredVertices.isNotEmpty()) {
            mapViewRef?.let { refreshMeasureLayers(it) }
        }
    }

    // Traits de la chaîne, extrémités résolues. Recalculé à chaque changement de sommet et à chaque
    // déplacement de « ma position », qui est un sommet mouvant.
    val currentMeasureSegments by remember {
        derivedStateOf {
            measureSegments(
                vertices = measuredVertices,
                linkedToPrev = measuredLinkedToPrev,
                loopClosed = measuredLoopClosed,
                myLocation = myCurrentLoc
            )
        }
    }
    val measureRouteProfileValue = measureRouteProfile()
    // Signature des itinéraires à obtenir. On relance les requêtes là-dessus et non sur les segments
    // eux-mêmes : la clé arrondit « ma position » (cf. measureRouteRequestKey), donc marcher quelques
    // mètres ne repart pas de zéro et n'interrompt pas un calcul en cours.
    val measureRouteSignature = measureRouteProfileValue?.let { profile ->
        currentMeasureSegments.joinToString("|") { measureRouteRequestKey(it, profile) }
    }
    // Un seul avertissement par activation : un itinéraire qui échoue échoue souvent pour tous les
    // traits (hors de France, réseau coupé), et autant de toasts serait insupportable.
    var measureRouteWarningShown by remember { mutableStateOf(false) }

    val measureFollowRoadsMode = AppConfig.measureFollowRoadsMode.intValue
    val measureFollowRoadsLabel = when (measureFollowRoadsMode) {
        1 -> stringResource(R.string.appstrings_measure_follow_roads_car)
        2 -> stringResource(R.string.appstrings_measure_follow_roads_walk)
        else -> stringResource(R.string.appstrings_measure_follow_roads_straight)
    }

    fun setMeasureFollowRoadsMode(mode: Int) {
        AppConfig.measureFollowRoadsMode.intValue = mode
        prefs.edit().putInt(MapDisplayPrefs.measureFollowRoadsMode.key, mode).apply()
        // Nouvelle tentative : on redonne le droit d'avertir si les itinéraires échouent encore.
        measureRouteWarningShown = false
        mapViewRef?.let { refreshMeasureLayers(it) }
    }

    // Mode « par la route / par les chemins » : on demande son itinéraire à chaque trait, un par un
    // — le service de la Géoplateforme est public et sans clé, on ne le sollicite pas en rafale. Le
    // trait reste direct tant qu'aucune réponse n'est arrivée ; ensuite l'itinéraire obtenu tient
    // jusqu'au suivant, réseau perdu compris.
    LaunchedEffect(isMeasuringMode, measureRouteProfileValue, measureRouteSignature) {
        val profile = measureRouteProfileValue ?: return@LaunchedEffect
        if (!isMeasuringMode) return@LaunchedEffect
        currentMeasureSegments.forEach { segment ->
            val requestKey = measureRouteRequestKey(segment, profile)
            if (!measureRouteRequests.add(requestKey)) return@forEach
            val cacheKey = measureRouteCacheKey(segment, profile)
            // Trait hors de portée d'un réseau routier commun : inutile d'appeler le service, mais
            // le trait direct qui subsiste est annoncé comme les autres échecs (toast plus bas).
            val tooFarToRoute =
                segment.start.distanceToAsDouble(segment.end) > RouteApi.MAX_ROUTABLE_DISTANCE_METERS
            val route = if (tooFarToRoute) null else try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RouteApi.getRoute(
                        fromLatitude = segment.start.latitude,
                        fromLongitude = segment.start.longitude,
                        toLatitude = segment.end.latitude,
                        toLongitude = segment.end.longitude,
                        profile = profile
                    )
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                // Chaîne modifiée en cours de route : on rend la clé pour un nouvel essai.
                measureRouteRequests.remove(requestKey)
                throw cancellation
            } catch (error: Throwable) {
                AppLogger.w("GeoTowerMap", "Measure route unavailable", error)
                null
            }

            if (route != null) {
                measureRoutes[cacheKey] = MeasureRoute.Ready(
                    points = route.points.map { GeoPoint(it[0], it[1]) },
                    distanceMeters = route.distanceMeters
                )
            } else {
                // Échec : le dernier itinéraire connu reste affiché, recalé sur la position — sortir
                // d'un tunnel ou traverser une zone blanche ne doit pas ramener la ligne droite. Et
                // on rend la clé, pour retenter dès que la chaîne ou la position bouge.
                measureRouteRequests.remove(requestKey)
                if (measureRoutes[cacheKey] !is MeasureRoute.Ready) {
                    // Là, rien n'a jamais abouti : le trait direct est ce qu'on affiche, et c'est le
                    // seul cas qui mérite d'être annoncé.
                    measureRoutes[cacheKey] = MeasureRoute.Unavailable
                    if (!measureRouteWarningShown) {
                        measureRouteWarningShown = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.appstrings_measure_route_unavailable),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            mapViewRef?.let { refreshMeasureLayers(it) }
        }
    }

    val currentFilteredAntennas by androidx.compose.runtime.rememberUpdatedState(filteredAntennas)
    val currentLoc by androidx.compose.runtime.rememberUpdatedState(myCurrentLoc)
    val currentSitesHs by androidx.compose.runtime.rememberUpdatedState(sitesHs)

    // Filtres d'affichage appliqués aux sites relus en base pour le suivi : la cible doit être un
    // site que la carte montrerait si on la ramenait dessus, ni plus ni moins.
    fun trackingDisplayFilter(): (LocalisationEntity) -> Boolean {
        val hsOperatorMap = buildHsOperatorMap(currentSitesHs)
        val selectedOperators = AppConfig.selectedOperatorKeys.value
        val showSitesInService = AppConfig.showSitesInService.value
        val showSitesOutOfService = AppConfig.showSitesOutOfService.value
        val showProjectSites = AppConfig.showProjectSites.value
        val hideUndergroundSites = AppConfig.hideUndergroundSites.value
        val showOnlyZbSites = AppConfig.showOnlyZbSites.value
        val frequencyFilter = FrequencyFilterSelection.fromMapConfig()
        return { antenna ->
            passesSiteDisplayFilters(
                antenna = antenna,
                hsOperatorMap = hsOperatorMap,
                selectedOperatorKeys = selectedOperators,
                showSitesInService = showSitesInService,
                showSitesOutOfService = showSitesOutOfService,
                showProjectSites = showProjectSites,
                hideUndergroundSites = hideUndergroundSites,
                showOnlyZbSites = showOnlyZbSites,
                frequencyFilter = frequencyFilter
            )
        }
    }

    // Signature des filtres : le voisinage est trié avec au moment de la lecture, il est donc à
    // relire dès qu'elle change — sinon un opérateur décoché resterait suivi jusqu'au prochain
    // déplacement.
    fun trackingFilterKey(): String = listOf(
        AppConfig.selectedOperatorKeys.value.sorted().joinToString(","),
        AppConfig.showSitesInService.value,
        AppConfig.showSitesOutOfService.value,
        AppConfig.showProjectSites.value,
        AppConfig.hideUndergroundSites.value,
        AppConfig.showOnlyZbSites.value,
        FrequencyFilterSelection.fromMapConfig(),
        currentSitesHs.size
    ).joinToString("|")

    // =====================================================================
    // ✅ CORRECTION : MOTEUR DE SUIVI SANS "TRAITS FANTÔMES"
    // =====================================================================
    LaunchedEffect(Unit) {
        var lastTrackedAllId: String? = null
        var lastTrackedFavId: String? = null
        // Voisinage relu en base autour de la position. Le suivi ne peut pas se contenter de la
        // liste affichée : la carte peut regarder ailleurs, être dézoomée (elle ne porte alors que
        // des regroupements) ou verrouillée sur une commune, et la cible n'y serait pas. Relu
        // seulement quand on s'est éloigné du point de lecture ou quand les filtres changent —
        // c'est une requête, pas un calcul.
        var nearbySites = emptyList<LocalisationEntity>()
        var nearbyFavSites = emptyList<LocalisationEntity>()
        var nearbyCenter: GeoPoint? = null
        var nearbyFilterKey: String? = null

        while (true) {
            // On lit l'état actuel des boutons en temps réel à l'intérieur de la boucle
            val isAllActive = trackNearestAll
            val isFavActive = trackNearestFav

            if (isAllActive || isFavActive) {
                val myLoc = currentLoc ?: locationOverlayRef?.myLocation

                if (myLoc != null) {
                    // Réseau préféré décoché dans les filtres de la carte : aucun de ses sites ne
                    // passerait, inutile de faire monter les paliers jusqu'au plus large pour rien.
                    val favOperatorKey = OperatorColors.keyFor(AppConfig.defaultOperator.value)
                        ?.takeIf { it in AppConfig.selectedOperatorKeys.value }
                    val filterKey = listOf(
                        trackingFilterKey(), isAllActive, isFavActive, favOperatorKey
                    ).joinToString("|")
                    val movedMeters = nearbyCenter?.distanceToAsDouble(myLoc) ?: Double.MAX_VALUE
                    if (nearbyFilterKey != filterKey || movedMeters > TRACKING_NEARBY_REFRESH_METERS) {
                        val displayFilter = trackingDisplayFilter()
                        nearbySites = if (isAllActive) {
                            viewModel.loadSitesAround(myLoc.latitude, myLoc.longitude, displayFilter)
                        } else {
                            emptyList()
                        }
                        // Le réseau préféré a besoin de sa propre montée en paliers : une boîte qui
                        // s'arrête sur le premier voisin venu, d'un autre opérateur, laisserait ce
                        // suivi-là sans cible alors que le site cherché est un peu plus loin.
                        nearbyFavSites = if (isFavActive && favOperatorKey != null) {
                            viewModel.loadSitesAround(myLoc.latitude, myLoc.longitude) { antenna ->
                                displayFilter(antenna) &&
                                    OperatorColors.keysFor(antenna.operateur).contains(favOperatorKey)
                            }
                        } else {
                            emptyList()
                        }
                        nearbyCenter = myLoc
                        nearbyFilterKey = filterKey
                    }

                    // La carte est ajoutée en appoint : elle peut porter des sites que les paliers
                    // n'ont pas atteints, jamais l'inverse. Les regroupements en sont écartés — ce
                    // sont des compteurs, pas des sites vers lesquels tirer un trait.
                    val candidates = (
                        nearbySites + nearbyFavSites +
                            currentFilteredAntennas.filterNot { it.idAnfr.startsWith("CLUSTER_") }
                        ).distinctBy { it.idAnfr }

                    var needsRefresh = false

                    // --- SUIVI 1 : LE PLUS PROCHE GLOBAL ---
                    if (isAllActive) {
                        val nearestAll = candidates.minByOrNull {
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
                        val nearestFav = candidates
                            .filter { favOperatorKey != null && OperatorColors.keysFor(it.operateur).contains(favOperatorKey) }
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
                    // On retrouve la carte comme on l'a laissée, sauf si la rotation a été coupée
                    // entre-temps : le `update` remettra alors le nord en haut.
                    mapOrientation = normalizeMapOrientation(prefs.getFloat(PREF_LAST_MAP_ORIENTATION, 0f))

                    val locationOverlay = object : CustomLocationOverlay(FusedMyLocationProvider(ctx), this, safePrimaryColor) {
                        override fun onLocationChanged(location: android.location.Location?, source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?) {
                            super.onLocationChanged(location, source)
                            if (location != null) {
                                myCurrentLoc = GeoPoint(location.latitude, location.longitude)
                                if (location.hasSpeed()) {
                                    currentSpeedKmH = (location.speed * 3.6f).toInt()
                                } else {
                                    currentSpeedKmH = 0
                                }
                                // Cap du suivi de tournée : le déplacement dit la direction bien
                                // mieux que le magnétomètre, faussé par un support métallique ou un
                                // moteur. Le lissage vit dans TripHeadingSmoother.
                                navHeadingDegrees = tripHeadingSmoother.update(
                                    bearingDegrees = if (location.hasBearing()) {
                                        location.bearing.toDouble()
                                    } else {
                                        null
                                    },
                                    speedMetersPerSecond = if (location.hasSpeed()) {
                                        location.speed.toDouble()
                                    } else {
                                        null
                                    }
                                )
                                smoothEngine.onFix(
                                    RawFix(
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        // Horloge monotone : l'heure système peut sauter en arrière,
                                        // ce qui ferait diverger l'extrapolation.
                                        timeMs = location.elapsedRealtimeNanos / 1_000_000L,
                                        speedMps = if (location.hasSpeed()) location.speed else null,
                                        bearingDeg = if (location.hasBearing()) location.bearing else null,
                                        accuracyM = if (location.hasAccuracy()) location.accuracy else null
                                    )
                                )
                            }
                        }
                    }
                    locationOverlay.setEnableAutoStop(false)
                    // Se déplacer à la main sur la carte rend la main : la poursuite s'arrête au
                    // lieu de ramener la vue sur la position à l'image suivante.
                    locationOverlay.onUserPan = {
                        isTrackingActive = false
                        // Même règle pour la caméra de suivi de tournée : le doigt reprend la main.
                        navCameraLocked = false
                    }
                    locationOverlay.showLocationMarker = AppConfig.showMapLocationMarker.value
                    locationOverlay.enableMyLocation()

                    locationOverlay.runOnFirstFix {
                        val initialLoc = locationOverlay.myLocation
                        if (initialLoc != null) {
                            post {
                                myCurrentLoc = initialLoc
                                if (!hasSavedPosition) {
                                    val initialZoom = preferredLocationZoom()
                                    controller.stopAnimation(false)
                                    controller.setZoom(initialZoom)
                                    controller.setCenter(initialLoc)
                                    currentZoom = initialZoom
                                    currentLat = initialLoc.latitude

                                    prefs.edit()
                                        .putFloat("last_map_lat", initialLoc.latitude.toFloat())
                                        .putFloat("last_map_lon", initialLoc.longitude.toFloat())
                                        .putFloat("last_map_zoom", initialZoom.toFloat())
                                        .apply()
                                }
                            }
                        }
                    }

                    // ✅ ORDONNANCEMENT DES CALQUES
                    overlays.add(measureTapOverlay)
                    overlays.add(measureOverlay)
                    overlays.add(tripOverlay)
                    overlays.add(searchBoundaryOverlay)
                    overlays.add(macroOverlay) // <-- Calque macro au fond
                    overlays.add(signalQuestCoverageOverlay)
                    overlays.add(radioOverlay)
                    overlays.add(markersOverlay) // <-- Calque micro au milieu
                    overlays.add(locationOverlay) // <-- Curseur devant

                    // Ajouté en dernier : les calques reçoivent les gestes dans l'ordre inverse du
                    // dessin, la rotation voit donc le pincement avant que quiconque puisse l'avaler.
                    val rotationOverlay = MapRotationGestureOverlay(this) { orientation ->
                        mapOrientationState.floatValue = orientation
                        // Le doigt reprend la main : l'alignement automatique se coupe, sinon le
                        // capteur ramènerait la carte sur le cap à l'image suivante.
                        if (AppConfig.mapFollowOrientation.value) {
                            AppConfig.mapFollowOrientation.value = false
                            prefs.edit().putBoolean(AppConfig.PREF_MAP_FOLLOW_ORIENTATION, false).apply()
                        }
                    }
                    rotationOverlay.isEnabled = AppConfig.mapRotationEnabled.value
                    overlays.add(rotationOverlay)

                    locationOverlayRef = locationOverlay
                    rotationOverlayRef = rotationOverlay

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
                                delay(PowerProfile.mapReloadDebounceMs)

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

                // Mode faible conso (Éco+) : coupe le téléchargement de tuiles (rendu cache/offline uniquement).
                map.setUseDataConnection(!PowerProfile.mapTilesOfflineOnly)

                // Mise à jour de la boussole (ton code actuel)
                (locationOverlayRef as? CustomLocationOverlay)?.let { overlay ->
                    overlay.currentCompassAzimuth = azimuth
                    if (overlay.showLocationMarker != showLocationMarker) {
                        overlay.showLocationMarker = showLocationMarker
                        shouldInvalidateMap = true
                    }
                    // Quand le lissage prend la main, le calque se tait : c'est la couche fluide qui
                    // peint le repère, et le suivi de position est piloté image par image.
                    if (overlay.smoothRenderingActive != smoothLocationEnabled) {
                        overlay.smoothRenderingActive = smoothLocationEnabled
                        shouldInvalidateMap = true
                    }
                }

                // --- Orientation de la carte ---------------------------------------------------
                // L'alignement sur le cap est piloté image par image, hors composition (voir plus
                // bas) : le suivre ici reviendrait à ne tourner la carte qu'aux recompositions,
                // donc par paliers.
                rotationOverlayRef?.isEnabled = mapRotationEnabled
                if (!followOrientation && !mapRotationEnabled && map.mapOrientation % 360f != 0f) {
                    // Plus aucun moyen de la redresser à la main : on remet le nord en haut.
                    map.applyOrientation(0f)
                    mapOrientationState.floatValue = 0f
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
                        1 -> if (ignStyle == 2) MapUtils.EsriSource.SATELLITE else MapUtils.OSM_Source
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

        // --- Alignement de la carte sur le cap de l'appareil ------------------------------------
        // Piloté image par image, et à partir du cap déjà lissé du capteur plutôt que de l'état
        // d'interface : ce dernier est bridé en cadence pour ne pas rejouer l'écran à chaque
        // relevé, et faire tourner la carte à ce rythme la faisait avancer par paliers.
        LaunchedEffect(followOrientation) {
            if (!followOrientation) return@LaunchedEffect
            // Le réglage est relu à chaque pas, et pas seulement à l'entrée : couper l'alignement
            // puis remettre le nord en haut se fait dans le même geste (appui sur la rose des
            // vents), or cet effet n'est annulé qu'à la recomposition suivante — un pas de plus et
            // la carte repartirait aussitôt sur le cap.
            var lastFrameNs = 0L
            while (PowerProfile.mapFollowOrientation && AppConfig.hasCompass.value) {
                val map = mapViewRef
                val delta = if (map == null) {
                    0f
                } else {
                    shortestAngleDelta(map.mapOrientation, normalizeMapOrientation(-continuousAzimuth[0]))
                }
                if (map == null || abs(delta) < MAP_FOLLOW_ORIENTATION_DEAD_ZONE_DEG) {
                    // Cap stable : on lâche le moteur d'images plutôt que de le réveiller à chaque
                    // image pour ne rien faire tourner.
                    lastFrameNs = 0L
                    delay(MAP_FOLLOW_ORIENTATION_IDLE_POLL_MS)
                    continue
                }

                // Un pas par image : c'est la régularité de la cadence qui se lit comme de la
                // fluidité, pas le nombre de degrés parcourus.
                val frameNs = withFrameNanos { it }
                val elapsedMs = if (lastFrameNs == 0L) {
                    MAP_FOLLOW_ORIENTATION_REFERENCE_FRAME_MS
                } else {
                    // Borné : une image sautée ne doit pas rattraper tout l'écart d'un coup.
                    ((frameNs - lastFrameNs) / 1_000_000L).toFloat().coerceIn(1f, 100f)
                }
                lastFrameNs = frameNs

                val easing = 1f - exp(-elapsedMs / MAP_FOLLOW_ORIENTATION_TIME_CONSTANT_MS)
                val next = normalizeMapOrientation(map.mapOrientation + delta * easing)
                map.applyOrientation(next)
                mapOrientationState.floatValue = next
            }
        }

        // --- Couche de rendu fluide du repère de position ---------------------------------------
        // Le repère est peint ICI, au-dessus de la MapView, et non dans un calque osmdroid : un
        // calque imposerait de réinvalider toute la carte — donc de redessiner les milliers de
        // marqueurs d'antennes — à chaque image, alors que cette couche ne repeint qu'un rond.
        if (smoothLocationEnabled) {
            val markerPainter = locationMarkerPainter
            val drawPixel = remember { android.graphics.Point() }
            val drawPoint = remember { GeoPoint(0.0, 0.0) }

            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                // Lire le compteur d'images force ce dessin à être rejoué à chaque image, APRÈS
                // celui de la MapView : la projection obtenue est donc celle de l'image courante, et
                // le repère reste collé à la carte même pendant une inertie ou un pincement.
                @Suppress("UNUSED_EXPRESSION")
                smoothFrameTick
                // Même raison pour l'orientation : la MapView n'est pas observable, donc sans cette
                // lecture une rotation au doigt laisserait le repère collé à son ancien pixel tant
                // que rien d'autre ne rejoue ce dessin.
                @Suppress("UNUSED_EXPRESSION")
                mapOrientationState.floatValue
                if (!showLocationMarker) return@ComposeCanvas
                val map = mapViewRef ?: return@ComposeCanvas
                val position = smoothEngine.sample(SystemClock.elapsedRealtime())
                    ?: return@ComposeCanvas

                drawPoint.setCoords(position.latitude, position.longitude)
                map.projection.toPixels(drawPoint, drawPixel)
                // Cette couche est peinte au-dessus de la MapView, donc hors du canevas que
                // osmdroid a tourné : la rotation de la carte, elle, est à appliquer à la main.
                map.projectedPointToScreen(drawPixel)
                markerPainter.draw(
                    canvas = drawContext.canvas.nativeCanvas,
                    x = drawPixel.x.toFloat(),
                    y = drawPixel.y.toFloat(),
                    rotationDegrees = map.screenAngleOf(if (PowerProfile.mapCompassRotation) azimuth else 0f),
                    showDirection = AppConfig.hasCompass.value
                )
            }

            // Boucle d'animation : une passe par image, mais uniquement tant qu'il y a quelque chose
            // à bouger — à l'arrêt le moteur se déclare au repos et plus rien n'est redessiné.
            LaunchedEffect(Unit) {
                val projected = android.graphics.Point()
                val target = GeoPoint(0.0, 0.0)
                var lastX = Int.MIN_VALUE
                var lastY = Int.MIN_VALUE

                while (true) {
                    val map = mapViewRef
                    if (map == null || smoothEngine.isIdle(SystemClock.elapsedRealtime())) {
                        // Rien à animer (immobile, ou pas encore de position) : on relâche la boucle
                        // plutôt que de réveiller le processeur à chaque image pour ne rien peindre.
                        // 50 ms de latence au démarrage d'un glissement, soit moins d'un pixel.
                        delay(SMOOTH_LOCATION_IDLE_POLL_MS)
                        continue
                    }

                    withFrameNanos { }
                    val now = SystemClock.elapsedRealtime()
                    val position = smoothEngine.sample(now) ?: continue

                    target.setCoords(position.latitude, position.longitude)
                    map.projection.toPixels(target, projected)

                    if (isTrackingActive) {
                        // Poursuite : la carte glisse sous un repère qui reste au centre. On ne
                        // recentre qu'au-delà du pixel, sinon on redessinerait la carte entière
                        // soixante fois par seconde pour un déplacement invisible.
                        val centerX = map.width / 2
                        val centerY = map.height / 2
                        if (abs(projected.x - centerX) >= 1 || abs(projected.y - centerY) >= 1) {
                            // Instance neuve à chaque fois : osmdroid GARDE la référence comme
                            // centre courant, lui passer notre point réutilisable le ferait muter
                            // dans son dos.
                            map.controller.setCenter(GeoPoint(position.latitude, position.longitude))
                        }
                    }

                    if (projected.x != lastX || projected.y != lastY) {
                        lastX = projected.x
                        lastY = projected.y
                        smoothFrameTick++
                    }
                }
            }
        }

        /**
         * Cadre la carte sur un département / une région et n'y laisse que ses sites. Le filtre
         * lui-même est posé par [applyCurrentAdminAreaFilter], que le changement d'état déclenche —
         * de la plus petite collectivité à la plus grande région, sans plafond : c'est le
         * chargement par zone visible qui borne le travail, plus la taille de la zone.
         */
        fun applyAdminAreaSearch(area: FrenchAdminAreas.Area, extent: AdminAreaExtent) {
            val map = mapViewRef ?: return
            searchBoundaryOverlay.items.clear()

            setCurrentAdminAreaSearch(
                area,
                SearchAreaBounds(
                    latNorth = extent.latNorth,
                    lonEast = extent.lonEast,
                    latSouth = extent.latSouth,
                    lonWest = extent.lonWest
                )
            )
            showCityStatsPopup = true

            releaseLocationFollowForSearch()
            map.zoomToBoundingBox(
                org.osmdroid.util.BoundingBox(
                    extent.latNorth,
                    extent.lonEast,
                    extent.latSouth,
                    extent.lonWest
                ),
                true,
                100
            )
            map.invalidate()
        }

        /**
         * Cadre la carte sur une zone géocodée (ville, adresse, département nommé) et pose son
         * contour, comme la validation au clavier l'a toujours fait.
         *
         * [fallbackInseeCode] est le repli d'une suggestion de commune : quand le géocodeur ne
         * répond pas — pas de réseau, fournisseur coupé — on cadre sur l'emprise de ses stations,
         * connue en base. Sans contour, donc sans découpe des sites ni encart de statistiques, mais
         * la carte va au bon endroit au lieu de ne rien faire.
         */
        suspend fun frameGeocodedArea(locationQuery: String, fallbackInseeCode: String? = null) {
            val nominatimArea = NominatimApi.searchArea(locationQuery)
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
                        releaseLocationFollowForSearch()
                        map.zoomToBoundingBox(cityBounds, true, 100)
                        map.invalidate()
                    }
                }
                return
            }

            val communeExtent = fallbackInseeCode?.let { viewModel.findCommuneExtent(it) }
            if (communeExtent != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    mapViewRef?.let { map ->
                        setCurrentCitySearch(null, null)
                        refreshSearchBoundaryOverlay(map, null)
                        releaseLocationFollowForSearch()
                        map.zoomToBoundingBox(
                            org.osmdroid.util.BoundingBox(
                                communeExtent.latNorth,
                                communeExtent.lonEast,
                                communeExtent.latSouth,
                                communeExtent.lonWest
                            ),
                            true,
                            100
                        )
                        map.invalidate()
                    }
                }
                return
            }

            try {
                val geocoder = android.location.Geocoder(context)
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(locationQuery, 1)

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
                            releaseLocationFollowForSearch()
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

        fun performSearch(query: String) {
            val cleanQuery = query.trim()
            // Saisie validée : la liste de suggestions se referme et ne se rouvrira pas sur ce
            // texte, sans quoi elle recouvrirait le résultat qu'on vient de cadrer.
            submittedSearchQuery = cleanQuery
            searchSuggestions = emptyList()
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
                releaseLocationFollowForSearch()
                mapViewRef?.controller?.setZoom(18.0)
                mapViewRef?.controller?.setCenter(GeoPoint(foundSite.latitude, foundSite.longitude))
                return
            }

            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // 2. Département / région (« 35 », « Ille-et-Vilaine », « region:Occitanie ») :
                // avant la recherche d'ID, sinon un code d'outre-mer comme « 974 » partirait en
                // « id_anfr LIKE %974% » et tomberait sur un site quelconque.
                val adminArea = FrenchAdminAreas.match(cleanQuery)
                if (adminArea != null) {
                    val extent = viewModel.findAdminAreaExtent(adminArea.departmentCodes)
                    if (extent != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            applyAdminAreaSearch(adminArea, extent)
                        }
                        return@launch
                    }
                }

                // Sans base locale (repli API live), la zone reste cadrable en ligne : on y va avec
                // son nom complet, un code nu comme « 35 » ne voudrait rien dire pour un géocodeur.
                val locationQuery = adminArea?.let { "${it.name}, France" } ?: cleanQuery

                // 3. ✅ NOUVEAU : Recherche Globale d'ID (Base de données entière)
                val hasDigits = cleanQuery.any { it.isDigit() }
                if (adminArea == null && hasDigits && cleanQuery.length >= 3) {
                    val globalSite = viewModel.searchSiteById(cleanQuery)

                    if (globalSite != null) {
                        // On a trouvé le site ! On déplace la caméra.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            releaseLocationFollowForSearch()
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

                // 4. Recherche de Ville / Adresse via internet (Nominatim), puis géocodeur système.
                frameGeocodedArea(locationQuery)
            }
        }

        /**
         * Applique une suggestion choisie sous la barre de recherche.
         *
         * Chaque forme rejoue exactement ce que [performSearch] aurait fait de cette saisie — le
         * gain est de sauter l'ambiguïté, pas d'ouvrir un autre chemin. La barre est mise à jour
         * avec l'intitulé retenu, et cette valeur est retenue comme « déjà cherchée » pour que la
         * liste ne se rouvre pas par-dessus le résultat.
         */
        fun applySearchSuggestion(suggestion: MapSearchSuggestion) {
            focusManager.clearFocus()
            searchSuggestions = emptyList()

            fun submit(query: String) {
                searchQuery = query
                submittedSearchQuery = query
            }

            when (suggestion) {
                is MapSearchSuggestion.Operator -> {
                    submit(suggestion.spec.label)
                    applyOperatorSearchSelection(setOf(suggestion.spec.key))
                }

                is MapSearchSuggestion.Site -> {
                    submit(suggestion.site.idAnfr)
                    restoreOperatorSearchSelection()
                    releaseLocationFollowForSearch()
                    mapViewRef?.controller?.setZoom(18.0)
                    mapViewRef?.controller?.setCenter(
                        GeoPoint(suggestion.site.latitude, suggestion.site.longitude)
                    )
                }

                is MapSearchSuggestion.AdminArea -> {
                    submit(suggestion.area.name)
                    restoreOperatorSearchSelection()
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val extent = viewModel.findAdminAreaExtent(suggestion.area.departmentCodes)
                        if (extent != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                applyAdminAreaSearch(suggestion.area, extent)
                            }
                        } else {
                            // Sans base locale (repli API live), la zone reste cadrable en ligne.
                            frameGeocodedArea("${suggestion.area.name}, France")
                        }
                    }
                }

                is MapSearchSuggestion.Commune -> {
                    submit(suggestion.name)
                    restoreOperatorSearchSelection()
                    // Le département lève l'ambiguïté des homonymes, nombreux en France : sans lui,
                    // le géocodeur choisirait pour nous une autre commune que celle proposée.
                    val locationQuery = listOfNotNull(
                        suggestion.name,
                        suggestion.departmentName,
                        "France"
                    ).joinToString(", ")
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        frameGeocodedArea(locationQuery, fallbackInseeCode = suggestion.codeInsee)
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
                .padding(start = sizing.spacing(16.dp), end = sizing.spacing(16.dp), top = sizing.spacing(110.dp))
        ) {
            MapSearchBar(
                query = searchQuery,
                placeholder = txtSearchCityOrId,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    performSearch(searchQuery)
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth(),
                autoFocus = searchAutoFocusPending,
                onAutoFocusHandled = { searchAutoFocusPending = false }
            )
        }

        // La liste descend sous la barre, pile là où vivent la boussole, le bouton de partage et le
        // tiroir de mesure : on les efface tant qu'elle est ouverte plutôt que de passer devant eux.
        // En paysage court, la barre est en bas avec la toolbox, la liste s'ouvre alors avec elle.
        val hideMapControlsForSuggestions = showSearchSuggestions && !toolboxExpandsLeft

        // En mode planificateur, l'interface de la carte des antennes s'efface au profit de celle
        // du trajet : ne restent que le zoom, le dézoom et le recentrage, plus la barre du bas.
        // L'attribution du fond de carte, elle, reste — c'est une obligation de licence, pas un
        // élément d'interface qu'on peut retirer par confort.
        val hideMapChrome = hideMapControlsForSuggestions || isPlannerMode

        // Mesure ouverte, chaîne encore vide : rien à l'écran ne dit qu'il faut toucher la carte
        // pour choisir d'où l'on part, ni que toucher son propre repère fait démarrer la mesure de
        // là. Le mode d'emploi prend donc le haut de l'écran en entier — barre du haut (retour,
        // titre, filtres) et bouton de partage s'effacent derrière lui, c'est la seule place qui se
        // lise d'un coup d'œil — et il rend le tout dès le premier point posé.
        //
        // Un suivi du site le plus proche le retire aussi : la carte trace déjà quelque chose, plus
        // personne n'attend qu'on lui explique comment commencer.
        val showMeasureFirstPointHint = isMeasuringMode && measuredVertices.isEmpty() &&
            !trackNearestAll && !trackNearestFav &&
            !hideMapControlsForSuggestions
        val showShareButton = !hideMapChrome && !showMeasureFirstPointHint

        AnimatedVisibility(
            visible = hideMapControlsForSuggestions,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Au-dessus du reste des calques de la carte : la liste est déclarée avant eux, et
                // la boussole passerait devant sans ça, même effacée en cours d'animation.
                .zIndex(2f)
                .padding(
                    start = sizing.spacing(16.dp),
                    end = sizing.spacing(16.dp),
                    top = sizing.spacing(110.dp) + mapSearchBarHeight + sizing.spacing(8.dp)
                )
        ) {
            MapSearchSuggestionList(
                suggestions = searchSuggestions,
                onSelect = { suggestion -> safeClick { applySearchSuggestion(suggestion) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        val compassTopPadding by animateDpAsState(
            targetValue = sizing.spacing(if (isSearchActive) 186.dp else 112.dp),
            label = "compassAnim"
        )
        val toolsTopPadding by animateDpAsState(
            targetValue = sizing.spacing(if (isSearchActive) 250.dp else 176.dp),
            label = "toolsAnim"
        )
        val useCompactCompassPlacement = configuration.screenHeightDp < 600
        val showCompassInMapHeader = showCompass && AppConfig.hasCompass.value &&
            isLandscapeLayout && !isPlannerMode
        val compassEndPadding by animateDpAsState(
            targetValue = if (isLandscapeLayout && !useCompactCompassPlacement) {
                (maxWidth * 0.12f).coerceIn(sizing.spacing(144.dp), sizing.spacing(320.dp))
            } else {
                sizing.spacing(16.dp)
            },
            label = "compassEndAnim"
        )
        val showCompactCompass = showCompass && AppConfig.hasCompass.value &&
            useCompactCompassPlacement && !showCompassInMapHeader && !isPlannerMode

        // Boîte à outils dépliée : elle prend tout le côté droit, jusqu'en haut de l'écran sur un
        // téléphone. La boussole s'efface alors, quelle que soit sa place — sinon elle passe derrière
        // la colonne d'outils, ou la pousse hors de l'écran quand elle est empilée au-dessus. Elle
        // revient dès qu'on referme la boîte. Le choix de l'emplacement, lui, n'est pas touché : ce
        // sont les trois points d'affichage qui se taisent, pas la logique qui les répartit.
        val hideCompassForToolbox = showToolbox && isToolboxExpanded && !isPlannerMode

        /**
         * Écran trajet : la boussole quitte ses trois emplacements habituels — bandeau en paysage,
         * flottant en haut à droite, compact au-dessus de la boîte à outils — pour se ranger sous
         * le bouton retour, dans le créneau que le bouton de partage y laisse libre.
         */
        val showCompassUnderBackButton = isPlannerMode && showCompass && AppConfig.hasCompass.value
        val zoomControlsHeight = if (showZoomBtns) sizing.component(117.dp) else 0.dp
        val defaultOp by AppConfig.defaultOperator
        // Mêmes pilules en portrait et en paysage : la hauteur ne suit plus celle des boutons
        // ronds de la carte, sinon le tiroir de suivi grossit d'un coup en tournant l'écran.
        val trackingButtonHeight = sizing.component(40.dp)
        val trackingButtonSpacing = sizing.spacing(8.dp)
        // Lignes du tiroir de mesure : suivi global, suivi de l'opérateur préféré, forme des traits.
        val trackingRowCount = 1 +
            (if (defaultOp != "Aucun") 1 else 0) +
            (if (canUseMeasureRouting) 1 else 0)
        val trackingDrawerHeight = (trackingButtonHeight * trackingRowCount.toFloat()) +
            (trackingButtonSpacing * (trackingRowCount - 1).toFloat())
        val zoomBottomPadding = sizing.spacing(32.dp) +
            if (showLocationBtn && canUseMapLocation) {
                mapControlButtonDiameter + sizing.spacing(16.dp)
            } else {
                0.dp
            }
        val trackingDrawerLandscapeBottomPadding = zoomBottomPadding +
            ((zoomControlsHeight - trackingDrawerHeight) / 2f).coerceAtLeast(0.dp)
        // Sur tablette en paysage, le tiroir de suivi (« site le plus proche »)
        // est déporté en haut à gauche, dans la zone dégagée sous les boutons
        // retour/partage, pour ne pas chevaucher la toolbox et le menu à droite.
        val trackingDrawerTopLeft = isLandscapeLayout && maxHeight >= 600.dp
        // En portrait, quand la toolbox est dépliée elle occupe tout le côté droit :
        // on renvoie alors le tiroir de mesure en haut à GAUCHE (sous le partage)
        // au lieu de le laisser chevaucher la boussole faute de place.
        val measureDrawerPortraitLeft = !useCompactCompassPlacement &&
            !trackingDrawerTopLeft && showToolbox && isToolboxExpanded
        val measureDrawerOnLeft = trackingDrawerTopLeft || measureDrawerPortraitLeft
        val measureDrawerModifier = if (measureDrawerOnLeft) {
            // Tablette paysage OU portrait toolbox dépliée : en haut à gauche,
            // dans la zone dégagée sous le bouton de partage.
            Modifier
                .align(Alignment.TopStart)
                .padding(start = sizing.spacing(16.dp), top = compassTopPadding + 70.dp)
        } else if (!useCompactCompassPlacement) {
            // Portrait, toolbox repliée : place libre en haut à droite, sous la boussole.
            val compassShownTopRight = showCompass && AppConfig.hasCompass.value &&
                !showCompassInMapHeader
            Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = sizing.spacing(16.dp),
                    top = if (compassShownTopRight) {
                        compassTopPadding + mapControlButtonDiameter + sizing.spacing(16.dp)
                    } else {
                        compassTopPadding
                    }
                )
        } else if (isLandscapeLayout && showZoomBtns) {
            Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = sizing.spacing(16.dp) + mapControlButtonDiameter + sizing.spacing(12.dp),
                    bottom = trackingDrawerLandscapeBottomPadding
                )
                .navigationBarsPadding()
        } else {
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = sizing.spacing(16.dp), top = toolsTopPadding)
        }
        // Seul le tiroir compact en paysage est ancré EN BAS : ailleurs il occupe le haut de l'écran,
        // là où s'affichent le total et le bouton « supprimer les tracés ».
        val measureDrawerAnchoredTop = measureDrawerOnLeft ||
            !(useCompactCompassPlacement && isLandscapeLayout && showZoomBtns)

        // Positions mesurées (px, repère racine) du bas du bouton partage et du haut
        // de la colonne d'infos, pour détecter en paysage un vrai chevauchement.
        var shareButtonBottomPx by remember { mutableFloatStateOf(0f) }
        var infoColumnTopPx by remember { mutableFloatStateOf(0f) }
        // Place naturelle du tiroir de mesure et emprise du bloc « total + supprimer les tracés » :
        // de quoi faire descendre le tiroir sous ce bloc quand il lui passe devant (cf. plus bas).
        var measureDrawerAnchorTopPx by remember { mutableFloatStateOf(0f) }
        var measureDrawerLeftPx by remember { mutableFloatStateOf(0f) }
        var measureDrawerRightPx by remember { mutableFloatStateOf(0f) }
        var measureInfoBottomPx by remember { mutableFloatStateOf(0f) }
        var measureInfoLeftPx by remember { mutableFloatStateOf(0f) }
        var measureInfoRightPx by remember { mutableFloatStateOf(0f) }

        // Le tiroir descend juste sous le total et le bouton de suppression quand les deux blocs se
        // croisent vraiment (emprises comparées, pas de seuil deviné). Les positions mesurées ici ne
        // dépendent pas de ce décalage — le repère du tiroir est son conteneur, qui ne bouge pas —
        // donc pas de mesure qui se mord la queue.
        val measureDrawerDrop by animateDpAsState(
            targetValue = run {
                if (!measureDrawerAnchoredTop) return@run 0.dp
                val crossesInfo = measureDrawerLeftPx < measureInfoRightPx &&
                    measureDrawerRightPx > measureInfoLeftPx
                if (!crossesInfo) return@run 0.dp
                val overlapPx = measureInfoBottomPx - measureDrawerAnchorTopPx
                if (overlapPx <= 0f) 0.dp else with(density) { overlapPx.toDp() } + sizing.spacing(12.dp)
            },
            label = "measureDrawerDropAnim"
        )

        // La boussole de l'écran trajet, à la place que le partage occupe le reste du temps.
        if (showCompassUnderBackButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sizing.spacing(16.dp), top = compassTopPadding)
            ) {
                MapCompassButton(
                    azimuth = azimuth,
                    mapOrientation = { mapOrientationState.floatValue },
                    followActive = followOrientation,
                    onToggleFollow = if (PowerProfile.isEco) null else toggleFollowOrientation,
                    modifier = Modifier.size(mapControlButtonDiameter)
                ) {
                    safeClick { resetMapOrientation() }
                }
            }
        }

        // ✅ NOUVEAU : Bouton de Partage positionné sous le bouton Retour avec animation
        AnimatedVisibility(
            visible = showShareButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = sizing.spacing(16.dp), top = compassTopPadding)
                .onGloballyPositioned {
                    shareButtonBottomPx = it.positionInRoot().y + it.size.height
                }
        ) {
            val isMi = fr.geotower.utils.AppConfig.distanceUnit.intValue == 1
            val speedText = if (isMi) "${(currentSpeedKmH / 1.60934).toInt()} mph" else "$currentSpeedKmH km/h"

            // En rendu fluide le repère de position vit dans une couche Compose au-dessus de la
            // MapView : la capture, qui ne dessine que la MapView, doit donc le repeindre elle-même
            // avec la même projection, sinon l'image partagée n'a pas de point de localisation.
            val drawLocationMarker: ((android.graphics.Canvas) -> Unit)? =
                if (smoothLocationEnabled && showLocationMarker) {
                    { canvas ->
                        val map = mapViewRef
                        val position = smoothEngine.sample(SystemClock.elapsedRealtime())
                        if (map != null && position != null) {
                            val pixel = android.graphics.Point()
                            map.projection.toPixels(
                                GeoPoint(position.latitude, position.longitude),
                                pixel
                            )
                            // La capture rend la MapView déjà tournée : le repère, repeint par-dessus,
                            // doit suivre la même rotation (cf. la couche fluide à l'écran).
                            map.projectedPointToScreen(pixel)
                            locationMarkerPainter.draw(
                                canvas = canvas,
                                x = pixel.x.toFloat(),
                                y = pixel.y.toFloat(),
                                rotationDegrees = map.screenAngleOf(if (PowerProfile.mapCompassRotation) azimuth else 0f),
                                showDirection = AppConfig.hasCompass.value
                            )
                        }
                    }
                } else {
                    null
                }

            fr.geotower.ui.components.MapShareMenu(
                useOneUi = fr.geotower.utils.AppConfig.useOneUiDesign,
                globalMapRef = mapViewRef,
                currentSpeed = speedText,
                currentZoom = currentZoom,
                currentLat = currentLat,
                azimuth = azimuth,
                measureOverlay = measureOverlay,
                timeSliderDateLabel = if (isTimeSliderVisible) timeSliderThreshold?.let { timeSliderMonthLabel(it) } else null,
                drawLocationMarker = drawLocationMarker
            )
        }

        AnimatedVisibility(
            visible = showCompass && AppConfig.hasCompass.value && !useCompactCompassPlacement &&
                !showCompassInMapHeader && !hideMapChrome && !hideCompassForToolbox,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = compassEndPadding, top = compassTopPadding)
        ) {
            MapCompassButton(
                azimuth = azimuth,
                mapOrientation = { mapOrientationState.floatValue },
                followActive = followOrientation,
                onToggleFollow = if (PowerProfile.isEco) null else toggleFollowOrientation,
                modifier = Modifier.size(mapControlButtonDiameter)
            ) {
                safeClick { resetMapOrientation() }
            }
        }

        val darkMaterialColor = Color(0xFF37474F)
        // Même bleu que les traits de mesure dessinés sur la carte (cf. refreshMeasureLayers).
        val measureLineColor = Color(0xFF3B5998)
        val opColor = OperatorColors.keyFor(defaultOp)
            ?.let { Color(OperatorColors.colorArgbForKey(it)) }
            ?: MaterialTheme.colorScheme.primary

        // --- LES BOUTONS DE SUIVI (MODE MESURE) ---
        // --- LES BOUTONS DE SUIVI "A TIROIR" (MODE MESURE) ---
        // Repère invisible (taille nulle) posé à la place naturelle du tiroir : il donne l'origine du
        // décalage et ne bouge jamais, puisque le décalage n'est appliqué qu'au tiroir lui-même.
        Box(
            modifier = measureDrawerModifier.onGloballyPositioned {
                measureDrawerAnchorTopPx = it.positionInRoot().y
            }
        )
        AnimatedVisibility(
            // Le tiroir occupe le haut de l'écran, sous la boussole : la liste de suggestions
            // l'efface avec le reste des contrôles le temps de la frappe.
            visible = isMeasuringMode && !hideMapControlsForSuggestions,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (measureDrawerOnLeft) -it else it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { if (measureDrawerOnLeft) -it else it }),
            modifier = measureDrawerModifier
                .padding(top = measureDrawerDrop)
                .onGloballyPositioned {
                    val position = it.positionInRoot()
                    measureDrawerLeftPx = position.x
                    measureDrawerRightPx = position.x + it.size.width
                }
        ) {
            Column(
                horizontalAlignment = if (measureDrawerOnLeft) Alignment.Start else Alignment.End,
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
                                .height(trackingButtonHeight).width(sizing.component(12.dp))
                                // ✅ Bords arrondis de tous les côtés pour la poignée
                                .background(darkMaterialColor, RoundedCornerShape(6.dp))
                                .clickable { safeClick { isClosestSiteExpanded = !isClosestSiteExpanded } }
                        )
                    }

                    // Le contenu du tiroir (le bouton)
                    val pill: @Composable () -> Unit = {
                        AnimatedVisibility(
                            visible = isClosestSiteExpanded,
                            enter = expandHorizontally(expandFrom = if (measureDrawerOnLeft) Alignment.Start else Alignment.End) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = if (measureDrawerOnLeft) Alignment.Start else Alignment.End) + fadeOut()
                        ) {
                            Button(
                                onClick = { safeClick { trackNearestAll = !trackNearestAll } },
                                modifier = Modifier.height(trackingButtonHeight).width(sizing.component(210.dp)),
                                contentPadding = PaddingValues(horizontal = sizing.spacing(14.dp)),
                                // ✅ NOUVEAU : Forme de pilule parfaite
                                shape = RoundedCornerShape(trackingButtonHeight / 2f),
                                colors = ButtonDefaults.buttonColors(containerColor = darkMaterialColor, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.NearMe, null, modifier = Modifier.size(sizing.component(18.dp)))
                                Spacer(Modifier.width(sizing.spacing(8.dp)))
                                Text(
                                    text = if (trackNearestAll) stringResource(R.string.appstrings_track_global_active) else txtClosestSite,
                                    fontSize = sizing.text(11.sp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Tiroir à gauche (tablette ou toolbox dépliée) : bouton collé au bord, poignée à
                    // l'intérieur. Ailleurs : poignée d'abord, bouton vers le bord droit.
                    if (measureDrawerOnLeft) {
                        pill()
                        Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                        handle()
                    } else {
                        handle()
                        Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
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
                                    .height(trackingButtonHeight).width(sizing.component(12.dp))
                                    // ✅ Bords arrondis de tous les côtés
                                    .background(opColor, RoundedCornerShape(6.dp))
                                    .clickable { safeClick { isClosestFavSiteExpanded = !isClosestFavSiteExpanded } }
                            )
                        }

                        // Le contenu du tiroir (le bouton)
                        val pill: @Composable () -> Unit = {
                            AnimatedVisibility(
                                visible = isClosestFavSiteExpanded,
                                enter = expandHorizontally(expandFrom = if (measureDrawerOnLeft) Alignment.Start else Alignment.End) + fadeIn(),
                                exit = shrinkHorizontally(shrinkTowards = if (measureDrawerOnLeft) Alignment.Start else Alignment.End) + fadeOut()
                            ) {
                                Button(
                                    onClick = { safeClick { trackNearestFav = !trackNearestFav } },
                                    modifier = Modifier.height(trackingButtonHeight).width(sizing.component(210.dp)),
                                    contentPadding = PaddingValues(horizontal = sizing.spacing(14.dp)),
                                    // ✅ NOUVEAU : Forme de pilule parfaite
                                    shape = RoundedCornerShape(trackingButtonHeight / 2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = opColor, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(sizing.component(18.dp)))
                                    Spacer(Modifier.width(sizing.spacing(8.dp)))
                                    Text(
                                        text = if (trackNearestFav) stringResource(R.string.track_operator_active, defaultOp) else "$txtClosestSite $defaultOp",
                                        fontSize = sizing.text(11.sp),
                                        fontWeight = FontWeight.Bold
                                    )                            }
                            }
                        }

                        // Tiroir à gauche (tablette ou toolbox dépliée) : bouton collé au bord, poignée à
                        // l'intérieur. Ailleurs : poignée d'abord, bouton vers le bord droit.
                        if (measureDrawerOnLeft) {
                            pill()
                            Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                            handle()
                        } else {
                            handle()
                            Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                            pill()
                        }
                    }
                }

                // ========================================================
                // 3. TIROIR « FORME DES TRAITS » : direct, par la route, par les chemins
                // ========================================================
                if (canUseMeasureRouting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Actif : la couleur des traits de mesure, pour relier le bouton à ce qu'il change.
                        val routeColor = if (measureFollowRoadsMode == 0) darkMaterialColor else measureLineColor
                        val handle: @Composable () -> Unit = {
                            Box(
                                modifier = Modifier
                                    .height(trackingButtonHeight).width(sizing.component(12.dp))
                                    .background(routeColor, RoundedCornerShape(6.dp))
                                    .clickable { safeClick { isMeasureRouteExpanded = !isMeasureRouteExpanded } }
                            )
                        }

                        val pill: @Composable () -> Unit = {
                            AnimatedVisibility(
                                visible = isMeasureRouteExpanded,
                                enter = expandHorizontally(expandFrom = if (measureDrawerOnLeft) Alignment.Start else Alignment.End) + fadeIn(),
                                exit = shrinkHorizontally(shrinkTowards = if (measureDrawerOnLeft) Alignment.Start else Alignment.End) + fadeOut()
                            ) {
                                Button(
                                    // Un seul bouton pour les trois formes : chaque appui passe à la
                                    // suivante, le libellé et l'icône disent laquelle est en cours.
                                    onClick = { safeClick { setMeasureFollowRoadsMode((measureFollowRoadsMode + 1) % 3) } },
                                    modifier = Modifier.height(trackingButtonHeight).width(sizing.component(210.dp)),
                                    contentPadding = PaddingValues(horizontal = sizing.spacing(14.dp)),
                                    shape = RoundedCornerShape(trackingButtonHeight / 2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = routeColor, contentColor = Color.White)
                                ) {
                                    Icon(
                                        when (measureFollowRoadsMode) {
                                            1 -> Icons.Default.DirectionsCar
                                            2 -> Icons.AutoMirrored.Filled.DirectionsWalk
                                            else -> Icons.Default.Timeline
                                        },
                                        null,
                                        modifier = Modifier.size(sizing.component(18.dp))
                                    )
                                    Spacer(Modifier.width(sizing.spacing(8.dp)))
                                    Text(
                                        text = measureFollowRoadsLabel,
                                        fontSize = sizing.text(11.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (measureDrawerOnLeft) {
                            pill()
                            Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                            handle()
                        } else {
                            handle()
                            Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                            pill()
                        }
                    }
                }
            }
        }


        Column(modifier = Modifier.fillMaxWidth().padding(top = sizing.spacing(48.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(targetState = showMeasureFirstPointHint, label = "mapHeaderMeasureHint") { showHint ->
            if (showHint) {
                // Le repère de position n'est proposé que s'il est réellement affiché et localisé :
                // sinon on désignerait quelque chose d'invisible.
                val canStartFromMyLocation = myCurrentLoc != null && showLocationMarker
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sizing.spacing(16.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = sizing.spacing(16.dp),
                            vertical = sizing.spacing(10.dp)
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TouchApp,
                            null,
                            tint = measureLineColor,
                            modifier = Modifier.size(sizing.component(20.dp))
                        )
                        Spacer(Modifier.width(sizing.spacing(10.dp)))
                        Column {
                            Text(
                                text = stringResource(R.string.appstrings_measure_first_point_title),
                                fontSize = sizing.text(13.sp),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    if (canStartFromMyLocation) {
                                        R.string.appstrings_measure_first_point_hint_location
                                    } else {
                                        R.string.appstrings_measure_first_point_hint
                                    }
                                ),
                                fontSize = sizing.text(11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp))) {
                // En mode simplifié la carte est la racine : le retour n'a plus de sens, le bouton
                // ouvre le tiroir. La sélection de photo partagée garde en revanche son « annuler ».
                val showSimpleModeMenuButton = onOpenSimpleModeMenu != null && !isSharedPhotoSelectionMode
                SmallFloatingButton(
                    icon = if (showSimpleModeMenuButton) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                    desc = if (showSimpleModeMenuButton) {
                        stringResource(R.string.simple_mode_open_menu)
                    } else {
                        stringResource(R.string.appstrings_back)
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    if (isSharedPhotoSelectionMode) {
                        cancelSharedPhotoSelection()
                    } else if (showSimpleModeMenuButton) {
                        safeClick { onOpenSimpleModeMenu.invoke() }
                    } else {
                        restoreOperatorSearchSelection()
                        safeBackNavigation.navigateBack()
                    }
                }
                Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    // Avec un trajet ouvert, la carte n'est plus « la carte des antennes » : elle
                    // sert la tournée, et son en-tête doit le dire.
                    Text(
                        text = if (isPlannerMode) txtTripMapTitle else txtMapTitle,
                        style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = sizing.spacing(24.dp), vertical = sizing.spacing(12.dp))
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCompassInMapHeader && !hideCompassForToolbox) {
                        MapCompassButton(
                            azimuth = azimuth,
                            mapOrientation = { mapOrientationState.floatValue },
                            followActive = followOrientation,
                            onToggleFollow = if (PowerProfile.isEco) null else toggleFollowOrientation,
                            modifier = Modifier.size(mapControlButtonDiameter)
                        ) {
                            safeClick { resetMapOrientation() }
                        }
                    }

                    // « Réglages/filtres » : icône Tune et non Menu, sinon deux hamburgers
                    // identiques encadrent la barre en mode simplifié.
                    //
                    // Avec un trajet ouvert, le bouton ne survit qu'en ÉDITION : c'est là qu'on
                    // clique des marqueurs d'antennes pour poser des étapes, donc là que choisir
                    // lesquelles voir a un sens. En consultation et en suivi, on ne clique plus
                    // d'antennes et le panneau n'aurait rien à régler d'utile.
                    if (!isPlannerMode || tripMode == TRIP_MODE_EDIT) {
                        SmallFloatingButton(
                            icon = Icons.Default.Tune,
                            desc = txtFilter
                        ) { safeClick { showSettingsSheet = true } }
                    }
                }
            } // fin de la barre du haut habituelle
            } // fin du else
            } // fin du Crossfade

            AnimatedVisibility(
                // Même règle que le bouton Filtres : annoncer des filtres actifs là où on ne peut
                // pas les régler serait une impasse.
                visible = activeMapFilterSummary != null && !isSearchActive &&
                    !isSharedPhotoSelectionMode && (!isPlannerMode || tripMode == TRIP_MODE_EDIT),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                ActiveMapFiltersBanner(
                    summary = activeMapFilterSummary.orEmpty(),
                    modifier = Modifier
                        .padding(start = sizing.spacing(88.dp), top = sizing.spacing(10.dp), end = sizing.spacing(88.dp))
                        .fillMaxWidth()
                )
            }

            val deleteButtonSpacer by animateDpAsState(
                targetValue = sizing.spacing(if (isSearchActive) 93.dp else 19.dp),
                label = "deleteButtonAnim"
            )
            Spacer(modifier = Modifier.height(deleteButtonSpacer))

            // Longueur totale de la chaîne : la somme des traits affichés (distance de l'itinéraire
            // quand il est arrivé, à vol d'oiseau sinon). Les traits de suivi vers le site le plus
            // proche n'y entrent pas : ils mesurent une portée radio, pas un parcours.
            val measureTotalDistanceMeters = currentMeasureSegments.sumOf { segment ->
                // Même recalage que le tracé, sinon le total et les étiquettes se contrediraient.
                val route = measureRouteAlignedOnSegment(
                    segment,
                    measureRouteProfileValue
                        ?.let { profile -> measureRoutes[measureRouteCacheKey(segment, profile)] }
                        ?.let { it as? MeasureRoute.Ready }
                )
                route?.distanceMeters ?: segment.start.distanceToAsDouble(segment.end)
            }
            // Itinéraires encore attendus : la clé n'est pas dans le cache. Le total affiché est
            // alors encore celui des traits directs, et la pilule le dit (animation + ligne d'état).
            val measureRoutesPending = measureRouteProfileValue?.let { profile ->
                currentMeasureSegments.any { !measureRoutes.containsKey(measureRouteCacheKey(it, profile)) }
            } == true

            // Total et suppression restent à leur place ; c'est le tiroir de mesure qui s'écarte.
            // Ce bloc mesure donc son emprise (largeur réelle et base) pour lui dire jusqu'où
            // descendre : quand rien n'est affiché, il est vide et le tiroir remonte tout seul.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.onGloballyPositioned {
                    val position = it.positionInRoot()
                    measureInfoLeftPx = position.x
                    measureInfoRightPx = position.x + it.size.width
                    measureInfoBottomPx = position.y + it.size.height
                }
            ) {
                AnimatedVisibility(
                    visible = currentMeasureSegments.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.padding(bottom = sizing.spacing(10.dp)),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .animateContentSize()
                                .padding(
                                    horizontal = sizing.spacing(16.dp),
                                    vertical = sizing.spacing(10.dp)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pendant le calcul, l'icône du mode cède la place à l'animation
                            // d'attente : le total affiché n'est pas encore le bon. Sinon l'icône
                            // suit ce qui est réellement tracé (et non le réglage) : si le service
                            // d'itinéraire est coupé, les traits restent directs.
                            if (measureRoutesPending) {
                                LoadingIndicator(
                                    modifier = Modifier.size(sizing.component(18.dp)),
                                    color = measureLineColor
                                )
                            } else {
                                Icon(
                                    when (measureRouteProfileValue) {
                                        RouteApi.PROFILE_CAR -> Icons.Default.DirectionsCar
                                        RouteApi.PROFILE_PEDESTRIAN -> Icons.AutoMirrored.Filled.DirectionsWalk
                                        else -> Icons.Default.Straighten
                                    },
                                    null,
                                    tint = measureLineColor,
                                    modifier = Modifier.size(sizing.component(18.dp))
                                )
                            }
                            Spacer(Modifier.width(sizing.spacing(8.dp)))
                            Column {
                                Text(
                                    text = stringResource(
                                        R.string.appstrings_measure_total_distance,
                                        formatSiteDistanceMeters(measureTotalDistanceMeters)
                                    ),
                                    fontSize = sizing.text(13.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                if (measureRoutesPending) {
                                    Text(
                                        text = stringResource(R.string.appstrings_measure_route_searching),
                                        fontSize = sizing.text(11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = measuredSites.isNotEmpty() || measuredVertices.isNotEmpty(),
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
                        modifier = Modifier.height(sizing.component(44.dp))
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(sizing.component(18.dp)))
                        Spacer(Modifier.width(sizing.spacing(8.dp)))
                        Text(txtDeleteTraces, fontSize = sizing.text(13.sp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isSharedPhotoSelectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = sizing.spacing(16.dp), end = sizing.spacing(16.dp), top = sizing.spacing(112.dp))
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shared_photo_map_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = pluralStringResource(R.plurals.shared_photo_map_ready, pendingSharedPhotoCount, pendingSharedPhotoCount),
                            fontSize = sizing.text(13.sp),
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
                // Même raison que la colonne d'infos : la barre du planificateur tient toute la
                // largeur du bas, ces boutons passeraient dessous.
                .padding(bottom = sizing.spacing(32.dp) + plannerLift, end = sizing.spacing(16.dp))
                .navigationBarsPadding()
                // Sert d'ancre au repère de position pendant le suivi : il se pose à la hauteur de
                // ces boutons plutôt qu'à une fraction d'écran choisie au hasard.
                .onGloballyPositioned {
                    navAnchorYPx = (it.positionInRoot().y + it.size.height / 2f).toInt()
                },
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
        ) {
            if (showCompactCompass && !hideCompassForToolbox) {
                MapCompassButton(
                    azimuth = azimuth,
                    mapOrientation = { mapOrientationState.floatValue },
                    followActive = followOrientation,
                    onToggleFollow = if (PowerProfile.isEco) null else toggleFollowOrientation,
                    modifier = Modifier.size(mapControlButtonDiameter)
                ) {
                    safeClick { resetMapOrientation() }
                }
            }

            if (showToolbox && !isPlannerMode) {
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
                        if (!isSearchActive && showMeasureFirstPointHint) {
                            // Le mode d'emploi de la mesure tient toute la barre du haut, juste où
                            // la recherche viendrait s'ouvrir. Plutôt que de les empiler, on dit
                            // quoi faire pour libérer la place. On ne bloque que l'OUVERTURE :
                            // refermer une recherche déjà ouverte reste possible.
                            showMeasureSearchBlockedDialog = true
                        } else if (canUseMapSearch) {
                            isSearchActive = !isSearchActive
                        // Ouvrir la barre pose le curseur dans le champ et lève le clavier ; la
                        // refermer le range, sinon il resterait devant la carte qu'on vient de
                        // dégager.
                        searchAutoFocusPending = isSearchActive
                        if (!isSearchActive) {
                            focusManager.clearFocus()
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
                    onOpenTrips = { safeClick { if (canUseTrips) navController.navigate("trips") } },
                    onOpenCompassPage = { safeClick { if (canUseCompassPage) navController.navigate("compass") } },
                    showSearch = canUseMapSearch,
                    // La mesure disparaît pendant l'édition d'un trajet : les deux posent des
                    // points, et le planificateur passe devant dans le gestionnaire de tap. Laisser
                    // le bouton donnerait un mode qui s'allume sans rien faire.
                    showMeasure = canUseMapMeasure && !isPlannerMode,
                    showTrips = canUseTrips,
                    showCompassPage = canUseCompassPage,
                    showTimeSlider = timeSliderAvailable && !isPlannerMode,
                    showLayers = canUseLayerSelector,
                    expandLeft = toolboxExpandsLeft
                )
                }

                if (toolboxExpandsLeft && isSearchActive) {
                    // Paysage court : la barre vit en bas, mais la liste s'ouvre SOUS elle comme
                    // partout ailleurs. Les deux tiennent dans une colonne à elles, sans
                    // espacement propre et avec l'écart porté par la liste : une fois celle-ci
                    // repliée, plus rien ne décolle la barre du bas de l'écran.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = sizing.spacing(16.dp)),
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
                                modifier = Modifier.weight(1f),
                                autoFocus = searchAutoFocusPending,
                                onAutoFocusHandled = { searchAutoFocusPending = false }
                            )
                            Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                            toolboxContent()
                        }

                        AnimatedVisibility(
                            visible = showSearchSuggestions,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            MapSearchSuggestionList(
                                suggestions = searchSuggestions.take(MAP_SEARCH_SUGGESTION_COMPACT_COUNT),
                                onSelect = { suggestion -> safeClick { applySearchSuggestion(suggestion) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = sizing.spacing(16.dp), top = sizing.spacing(8.dp))
                            )
                        }
                    }
                } else {
                    toolboxContent()
                }
            }

            if (showZoomBtns) {
                val zoomControlShape = RoundedCornerShape(mapControlButtonDiameter / 2f)
                Surface(
                    modifier = Modifier
                        .width(mapControlButtonDiameter)
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
                                topStart = mapControlButtonDiameter / 2f,
                                topEnd = mapControlButtonDiameter / 2f,
                                bottomStart = 10.dp,
                                bottomEnd = 10.dp
                            ),
                            onClick = { mapViewRef?.controller?.zoomIn() }
                        )
                        HorizontalDivider(
                            modifier = Modifier.width(sizing.component(32.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                        ZoomControlSegmentButton(
                            icon = Icons.Default.Remove,
                            shape = RoundedCornerShape(
                                topStart = 10.dp,
                                topEnd = 10.dp,
                                bottomStart = mapControlButtonDiameter / 2f,
                                bottomEnd = mapControlButtonDiameter / 2f
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
                            // Localisation indisponible (permission coupée OU GPS éteint) : au lieu de tenter
                            // un recentrage qui échouerait en silence, on invite à la réactiver.
                            if (!isLocationReady) {
                                onFixLocation()
                                return@safeClick
                            }

                            val map = mapViewRef
                            val locationOverlay = locationOverlayRef

                            if (map == null || locationOverlay == null) {
                                Toast.makeText(context, txtLocationNotFound, Toast.LENGTH_SHORT).show()
                                return@safeClick
                            }

                            fun centerOnLocation(location: GeoPoint) {
                                val zoom = preferredLocationZoom()
                                map.controller.stopAnimation(false)
                                map.controller.setZoom(zoom)
                                map.controller.setCenter(location)
                                currentZoom = zoom
                                currentLat = location.latitude
                            }

                            // Suivi de tournée : c'est notre caméra qui tient la vue (cap de marche
                            // en haut, position en bas). Le bouton se contente de la rattacher —
                            // lancer en plus la poursuite d'osmdroid recentrerait au milieu de
                            // l'écran et écraserait ce cadrage.
                            if (tripMode == TRIP_MODE_FOLLOW) {
                                navCameraLocked = true
                                map.controller.setZoom(NAV_FOLLOW_ZOOM)
                                return@safeClick
                            }

                            // Un seul effet : le bouton lance la poursuite, il ne l'arrête jamais.
                            // C'est le glissement du doigt sur la carte qui rend la main (cf.
                            // `onUserPan`) : on n'est plus bloqué sur sa position jusqu'à un second
                            // appui. Rappuyer pendant la poursuite se contente donc de recadrer.
                            isTrackingActive = true
                            locationOverlay.setEnableAutoStop(false)
                            locationOverlay.enableMyLocation()
                            // En mode fluide, le recentrage est piloté image par image par la couche
                            // de rendu : laisser en plus osmdroid animer la carte à chaque relevé
                            // ferait tourner deux poursuites concurrentes.
                            if (!smoothLocationEnabled) {
                                locationOverlay.enableFollowLocation()
                            }

                            val known = locationOverlay.myLocation ?: myCurrentLoc
                            if (known != null) {
                                centerOnLocation(known)
                            } else {
                                // Pas encore de relevé : on cadre au premier qui tombe plutôt que de
                                // laisser l'appui sans effet visible.
                                locationOverlay.runOnFirstFix {
                                    locationOverlay.myLocation?.let { firstLocation ->
                                        map.post { centerOnLocation(firstLocation) }
                                    }
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(mapControlButtonDiameter)
                ) {
                    // ✅ On ajoute le cercle très fin autour de l'icône si actif
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                // En suivi de tournée, c'est l'accrochage de NOTRE caméra qui compte.
                                if (isTrackingActive ||
                                    (tripMode == TRIP_MODE_FOLLOW && navCameraLocked)
                                ) {
                                    Modifier.border(
                                        width = 1.5.dp, // Cercle très fin
                                        color = MaterialTheme.colorScheme.primary, // Couleur principale
                                        shape = CircleShape
                                    ).padding(sizing.spacing(2.dp)) // Petit espacement pour ne pas coller au bord
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                !isLocationReady -> Icons.Default.LocationDisabled // Localisation coupée : icône barrée
                                isTrackingActive -> Icons.Default.MyLocation
                                else -> Icons.Outlined.MyLocation
                            },
                            contentDescription = stringResource(R.string.appstrings_locate),
                            tint = if (isTrackingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(sizing.component(24.dp))
                        )
                    }
                }
            }
        }

        // Le slider temporel s'efface pendant l'édition d'un trajet : les deux barres occupent le
        // même bas d'écran, et le planificateur est un mode focalisé.
        if (isTimeSliderVisible && !isPlannerMode) {
            MapTimeSliderBar(
                oldestDateInt = oldestServiceDate?.toIntOrNull()?.coerceAtLeast(19910101) ?: 19910101,
                newestDateInt = todayDateInt,
                thresholdInt = timeSliderThreshold,
                countsByOperator = timeSliderStats.countsByOperator,
                undatedCount = timeSliderStats.undated,
                onThresholdChange = { timeSliderThreshold = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sizing.spacing(12.dp), end = mapControlButtonDiameter + sizing.spacing(24.dp))
                    .navigationBarsPadding()
            )
        }

        plannerPlan?.let { plan ->
            when (tripMode) {
                TRIP_MODE_FOLLOW -> TripFollowBar(
                    plan = plan,
                    status = plannerFollowStatus,
                    distanceUnit = AppConfig.distanceUnit.intValue,
                    onCheckNext = {
                        val index = plannerFollowStatus?.nextStepIndex
                        if (index != null && index in plan.steps.indices) {
                            savePlan(
                                plan.copy(
                                    steps = plan.steps.toMutableList().apply {
                                        set(index, this[index].copy(visitedAtMillis = System.currentTimeMillis()))
                                    }
                                )
                            )
                            mapViewRef?.let { refreshTripLayers(it) }
                        }
                    },
                    // Arrêter le suivi ramène à la consultation, pas à l'édition : on vient de
                    // parcourir la tournée, pas de vouloir la redessiner.
                    onStop = { tripMode = TRIP_MODE_VIEW },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { tripBarHeightPx = it.size.height }
                )

                TRIP_MODE_VIEW -> TripViewBar(
                    plan = plan,
                    distanceUnit = AppConfig.distanceUnit.intValue,
                    onFollow = { tripMode = TRIP_MODE_FOLLOW },
                    onEdit = { tripMode = TRIP_MODE_EDIT },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { tripBarHeightPx = it.size.height }
                )

                else -> TripPlannerBar(
                plan = plan,
                busy = plannerBusy,
                distanceUnit = AppConfig.distanceUnit.intValue,
                onToggleProfile = {
                    val next = if (plan.profile == RouteApi.PROFILE_PEDESTRIAN) {
                        RouteApi.PROFILE_CAR
                    } else {
                        RouteApi.PROFILE_PEDESTRIAN
                    }
                    // Changer de profil périme TOUS les segments : un tracé voiture ne vaut rien
                    // pour un trajet à pied, et l'inverse encore moins.
                    savePlan(plan.copy(profile = next, legs = emptyList()))
                },
                onToggleReturnToStart = { savePlan(plan.copy(returnToStart = !plan.returnToStart)) },
                onOptimize = {
                    val outcome = TripOrderOptimizer.optimize(plan)
                    if (outcome.changed) savePlan(outcome.plan)
                    mapViewRef?.let { refreshTripLayers(it) }
                },
                onUndo = {
                    val remaining = plan.steps.dropLast(1)
                    savePlan(
                        plan.copy(
                            steps = remaining,
                            legs = plan.legs.filter { it.isWithin(remaining.size) }
                        )
                    )
                    mapViewRef?.let { refreshTripLayers(it) }
                },
                onFinish = leaveTripEditing,
                insertAfterNumber = plannerInsertAfterIndex?.plus(2),
                // Pas de `navigationBarsPadding` ici : la surface doit descendre jusqu'au bord de
                // l'écran, sans bande de carte visible dessous. C'est le contenu de la barre qui
                // s'écarte de la barre système, à l'intérieur.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { tripBarHeightPx = it.size.height }
            )
            }

            if (plannerAskToSave) {
                AlertDialog(
                    // Fermer sans choisir ramène à la carte : ni enregistrement ni perte.
                    onDismissRequest = { plannerAskToSave = false },
                    title = { Text(stringResource(R.string.trips_save_prompt_title)) },
                    text = { Text(stringResource(R.string.trips_save_prompt_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            plannerAskToSave = false
                            plannerSaving = true
                        }) {
                            Text(stringResource(R.string.trips_save_prompt_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = discardTripEditing) {
                            Text(stringResource(R.string.trips_save_prompt_discard))
                        }
                    }
                )
            }

            if (plannerSaving) {
                TripScheduleDialog(
                    plan = plan,
                    // Nom, date et heure d'un seul geste : c'est le moment où l'on décide ce que
                    // devient la tournée.
                    editableName = true,
                    onDismiss = { plannerSaving = false },
                    onConfirm = { name, plannedAtMillis, reminderOffsets, stopMinutes ->
                        val next = plan.withSchedule(
                            context = context,
                            plannedAtMillis = plannedAtMillis,
                            reminderOffsetsMinutes = reminderOffsets,
                            stopDurationMinutes = stopMinutes,
                            locale = configuration.locales[0],
                            editedName = name
                        )
                        savePlan(next)
                        TripReminderScheduler.reschedule(context, next)
                        plannerSaving = false
                        safeBackNavigation.navigateBack()
                    }
                )
            }

            plannerStepMenuIndex?.let { index ->
                val step = plan.steps.getOrNull(index)
                if (step == null) {
                    plannerStepMenuIndex = null
                } else {
                    TripStepActionsDialog(
                        stepNumber = index + 1,
                        label = step.label,
                        visited = step.visitedAtMillis != null,
                        canMoveUp = index > 0,
                        canMoveDown = index < plan.steps.lastIndex,
                        onMoveUp = {
                            mutateTripSteps { it.add(index - 1, it.removeAt(index)) }
                            plannerStepMenuIndex = null
                            mapViewRef?.let { refreshTripLayers(it) }
                        },
                        onMoveDown = {
                            mutateTripSteps { it.add(index + 1, it.removeAt(index)) }
                            plannerStepMenuIndex = null
                            mapViewRef?.let { refreshTripLayers(it) }
                        },
                        onInsertAfter = {
                            plannerInsertAfterIndex = index
                            plannerStepMenuIndex = null
                        },
                        onToggleVisited = {
                            val visitedAt = if (step.visitedAtMillis == null) {
                                System.currentTimeMillis()
                            } else {
                                null
                            }
                            // Cocher ne change pas l'ordre : les segments restent valables, on ne
                            // passe donc pas par mutateTripSteps.
                            savePlan(
                                plan.copy(
                                    steps = plan.steps.toMutableList().apply {
                                        set(index, step.copy(visitedAtMillis = visitedAt))
                                    }
                                )
                            )
                            plannerStepMenuIndex = null
                            mapViewRef?.let { refreshTripLayers(it) }
                        },
                        onDelete = {
                            mutateTripSteps { it.removeAt(index) }
                            plannerStepMenuIndex = null
                            mapViewRef?.let { refreshTripLayers(it) }
                        },
                        onDismiss = { plannerStepMenuIndex = null }
                    )
                }
            }
        }

        // En paysage, la colonne d'infos (vitesse / échelle / attribution) est
        // ancrée en bas à gauche mais remonte quand le slider temporel est ouvert
        // (ou sur un écran court). On compare les positions réellement mesurées :
        // si le haut de la colonne recouvre le bas du bouton de partage (haut
        // gauche), on la rend invisible pour ne pas le masquer.
        //
        // `showShareButton` d'abord : la dernière position mesurée survit à la disparition du
        // bouton (plus personne ne repositionne, donc plus personne ne remet à zéro). Sans cette
        // garde, s'effacer devant un bouton absent emporterait l'attribution du fond de carte.
        val infoColumnMasksShare = showShareButton &&
            isLandscapeLayout &&
            shareButtonBottomPx > 0f &&
            infoColumnTopPx > 0f &&
            infoColumnTopPx < shareButtonBottomPx

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = sizing.spacing(16.dp),
                    bottom = sizing.spacing(32.dp) + timeSliderLift + plannerLift
                )
                .navigationBarsPadding()
                .onGloballyPositioned { infoColumnTopPx = it.positionInRoot().y }
                .alpha(if (infoColumnMasksShare) 0f else 1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))
        ) {
            // Le compteur reste pendant le suivi : c'est une information de conduite, comme sur les
            // applis de guidage. Il ne disparaît qu'en consultation et en édition.
            if (AppConfig.showSpeedometer.value && (!isPlannerMode || tripMode == TRIP_MODE_FOLLOW)) {
                fr.geotower.ui.components.MapSpeedometer(speedKmH = currentSpeedKmH)
            }

            if (showScale && !isPlannerMode) {
                MapScaleBar(zoom = currentZoom, latitude = currentLat)
            }

            if (showAttribution) {
                // On crédite le fond RÉELLEMENT affiché (effectiveProvider) : la bascule
                // silencieuse en hors-ligne change les tuiles sans toucher à mapProvider.
                val attributionText = MapUtils.MapAttribution.text(effectiveProvider, ignStyle)
                val attributionUrl = MapUtils.MapAttribution.url(effectiveProvider, ignStyle)

                Surface(
                    color = Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .then(
                                if (infoColumnMasksShare) Modifier
                                else Modifier.clickable { uriHandler.openUri(attributionUrl) }
                            )
                            .padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(4.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drapeau uniquement pour l'IGN (service public français) : les autres
                        // fonds sont internationaux et n'ont pas de pays à afficher.
                        if (effectiveProvider == 0) {
                            Row(modifier = Modifier.size(width = sizing.component(14.dp), height = sizing.component(10.dp))) {
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

                            Spacer(modifier = Modifier.width(sizing.spacing(6.dp)))
                        }

                        Text(
                            text = attributionText,
                            fontSize = sizing.text(11.sp),
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))) {
                                if (isMapProviderEnabled(1)) {
                                    MapLayerButton(txtMapOsmLayer, mapProvider == 1, Modifier.weight(1f)) {
                                        AppConfig.mapProvider.value = 1; prefs.edit().putInt("map_provider", 1).apply()
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
                                        if (ignStyle == 2) { AppConfig.ignStyle.value = 0; prefs.edit().putInt("ign_style", 0).apply() }
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
                                if (mapProvider == 0 || mapProvider == 1) {
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
                mapRotation = mapRotationEnabled,
                onMapRotationChange = {
                    AppConfig.mapRotationEnabled.value = it
                    prefs.edit().putBoolean(AppConfig.PREF_MAP_ROTATION_ENABLED, it).apply()
                },
                followOrientation = AppConfig.mapFollowOrientation.value,
                onFollowOrientationChange = applyFollowOrientation,
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
                measureReconnectOnDelete = AppConfig.measureReconnectOnDelete.value,
                onMeasureReconnectChange = {
                    AppConfig.measureReconnectOnDelete.value = it
                    prefs.edit().putBoolean(MapDisplayPrefs.measureReconnectOnDelete.key, it).apply()
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
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
                    Text(text = txtLightColorWarning)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgainChecked = !dontShowAgainChecked }
                            .padding(vertical = sizing.spacing(4.dp))
                    ) {
                        Checkbox(
                            checked = dontShowAgainChecked,
                            onCheckedChange = { dontShowAgainChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                        Text(text = txtDoNotShowAgain, style = sizing.textStyle(MaterialTheme.typography.bodyMedium))
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
    if (showMeasureSearchBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showMeasureSearchBlockedDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.appstrings_measure_search_blocked_title),
                    fontWeight = FontWeight.Bold,
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge)
                )
            },
            text = { Text(text = stringResource(R.string.appstrings_measure_search_blocked_message)) },
            confirmButton = {
                Button(
                    onClick = { showMeasureSearchBlockedDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = txtUnderstood, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }
    if (supportChoices.isNotEmpty()) {
        SupportChoiceDialog(
            choices = supportChoices,
            onSelect = { choice ->
                supportChoices = emptyList()
                mapViewRef?.let { openSupportDetailFromMap(it, choice.representative) }
            },
            onDismiss = { supportChoices = emptyList() }
        )
    }
    if (showCityStatsPopup) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCityStatsPopup = false }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(8.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(sizing.spacing(24.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
                ) {
                    val cityStats = if (statsLoading) null else declaredSiteStats(statsAntennas)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(sizing.spacing(4.dp))
                    ) {
                        Text(
                            text = stringResource(R.string.appstrings_city_stats_title),
                            style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        // Une commune est identifiable d'un coup d'œil sur la carte, un département
                        // beaucoup moins : on rappelle la zone sur laquelle portent les compteurs.
                        currentAdminArea?.let { area ->
                            Text(
                                text = area.name,
                                style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(sizing.spacing(20.dp)).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                                Text(
                                    text = stringResource(R.string.appstrings_mobile_telephony),
                                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

                            if (statsLoading) {
                                LoadingIndicator(
                                    modifier = Modifier.size(sizing.component(48.dp)),
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
                                        style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                    )
                                }
                            }

                            if ((cityStats?.totalCount ?: 0) > 0) {
                                Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))

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
            antennas = statsAntennas,
            techniques = cityStatsTechniques,
            isFrequencyStatusLoading = isCityStatsTechniquesLoading,
            // Un département couvre des centaines de communes : garder la commune dominante
            // n'y compterait qu'une fraction des sites, alors que le total en compte la totalité.
            restrictToMainCity = currentAdminArea == null,
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
    val sizing = LocalGeoTowerUiSizing.current
    val density = LocalDensity.current
    val maxBarWidthDp = sizing.component(100.dp)
    val maxBarWidthPx = with(density) { maxBarWidthDp.toPx() }
    val metersPerPx = 156543.03392 * Math.cos(latitude * Math.PI / 180.0) / Math.pow(2.0, zoom)

    val roundDistances = listOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 300000, 500000, 1000000)
    val chosenDistance = roundDistances.lastOrNull { (it / metersPerPx) <= maxBarWidthPx } ?: 1
    val actualBarWidthDp = with(density) { (chosenDistance / metersPerPx).toFloat().toDp() }
    val label = if (chosenDistance >= 1000) "${chosenDistance / 1000} km" else "$chosenDistance m"

    Surface(color = Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(2.dp))) {
            Text(label, fontSize = sizing.text(10.sp), fontWeight = FontWeight.Bold, color = Color.Black)
            ComposeCanvas(modifier = Modifier.width(actualBarWidthDp).height(sizing.component(6.dp))) {
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
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onAutoFocusHandled: () -> Unit = {}
) {
    val sizing = LocalGeoTowerUiSizing.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        // La barre entre en fondu : sans attendre la première image, le nœud de focus n'est pas
        // encore posé et la demande partirait dans le vide.
        withFrameNanos { }
        focusRequester.requestFocus()
        // Le focus suffit d'ordinaire à lever le clavier, mais pas quand la vue vient d'apparaître :
        // on le demande explicitement.
        keyboardController?.show()
        onAutoFocusHandled()
    }

    Surface(
        modifier = modifier.height(mapSearchBarHeight),
        shape = RoundedCornerShape(mapSearchBarHeight / 2f),
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
                modifier = Modifier.padding(start = sizing.spacing(16.dp), end = sizing.spacing(12.dp))
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = sizing.text(16.sp),
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
                        fontSize = sizing.text(16.sp)
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .size(mapControlButtonDiameter)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.appstrings_search),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(sizing.component(26.dp))
                )
            }
        }
    }
}

/**
 * Liste des suggestions, ouverte sous la barre de recherche.
 *
 * Volontairement courte : elle se pose sur la boussole, le bouton de partage et le tiroir de mesure,
 * que la carte efface le temps qu'elle est ouverte plutôt que de les laisser derrière.
 */
@Composable
private fun MapSearchSuggestionList(
    suggestions: List<MapSearchSuggestion>,
    onSelect: (MapSearchSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiSizing.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(sizing.component(20.dp)),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(vertical = sizing.spacing(6.dp))) {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = sizing.spacing(16.dp)),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = sizing.component(0.5.dp)
                    )
                }
                MapSearchSuggestionRow(suggestion = suggestion) { onSelect(suggestion) }
            }
        }
    }
}

@Composable
private fun MapSearchSuggestionRow(suggestion: MapSearchSuggestion, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiSizing.current

    val title = when (suggestion) {
        is MapSearchSuggestion.Commune -> suggestion.name
        is MapSearchSuggestion.AdminArea -> suggestion.area.name
        is MapSearchSuggestion.Site -> suggestion.site.idAnfr
        is MapSearchSuggestion.Operator -> suggestion.spec.label
    }
    val subtitle = when (suggestion) {
        is MapSearchSuggestion.Commune -> when {
            suggestion.departmentName == null -> null
            suggestion.departmentCode == null -> suggestion.departmentName
            else -> "${suggestion.departmentName} (${suggestion.departmentCode})"
        }
        is MapSearchSuggestion.AdminArea -> stringResource(
            when (suggestion.area.kind) {
                FrenchAdminAreas.Kind.DEPARTMENT -> R.string.map_search_suggestion_department
                FrenchAdminAreas.Kind.REGION -> R.string.map_search_suggestion_region
            }
        )
        is MapSearchSuggestion.Site -> suggestion.site.operateur
        is MapSearchSuggestion.Operator -> stringResource(R.string.map_search_suggestion_operator)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSize = sizing.component(22.dp)
        if (suggestion is MapSearchSuggestion.Operator) {
            // Pastille aux couleurs de l'opérateur, comme partout ailleurs dans l'app : c'est le
            // repère le plus lisible, une icône générique ne dirait pas lequel.
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(Color(suggestion.spec.colorArgb), CircleShape)
            )
        } else {
            Icon(
                imageVector = when (suggestion) {
                    is MapSearchSuggestion.Commune -> Icons.Default.LocationCity
                    is MapSearchSuggestion.AdminArea -> Icons.Default.Flag
                    else -> Icons.Default.CellTower
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.width(sizing.spacing(14.dp)))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = sizing.text(15.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = sizing.text(12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SmallFloatingButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiSizing.current
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.size(mapControlButtonDiameter)) {
        // Taille explicite : sans ca l'icone garde le 24.dp par defaut de Material et ne suit
        // pas le bouton quand le slider grossit.
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, modifier = Modifier.size(sizing.component(24.dp))) }
    }
}

@Composable
private fun ZoomControlSegmentButton(
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(mapControlButtonDiameter)
            .clip(shape),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(sizing.component(26.dp)))
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
    val sizing = LocalGeoTowerUiSizing.current
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
            modifier = Modifier.padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(8.dp)),
            fontSize = sizing.text(12.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Rose des vents de la carte : appui court pour remettre le nord en haut, appui long pour que la
 * carte suive (ou non) l'orientation de l'appareil.
 *
 * La rose entière tourne avec la carte — c'est elle qui dit où est le nord — tandis que l'aiguille
 * garde son cap à l'écran. Carte au nord, l'ensemble est strictement identique à un compas figé.
 */
@Composable
private fun MapCompassButton(
    azimuth: Float,
    /**
     * Lu au moment de composer la couche graphique, et non dans le corps du composable : cette
     * valeur change à chaque image pendant une rotation, et la lire ici recomposerait tout l'écran
     * de la carte à la même cadence.
     */
    mapOrientation: () -> Float,
    followActive: Boolean,
    modifier: Modifier = Modifier,
    onToggleFollow: (() -> Unit)? = null,
    onReset: () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    val haptic = LocalHapticFeedback.current
    val longPress: (() -> Unit)? = onToggleFollow?.let { toggle ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            toggle()
        }
    }
    Surface(
        modifier = modifier.combinedClickable(onClick = onReset, onLongClick = longPress),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = if (followActive) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = mapOrientation() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "N",
                fontSize = sizing.text(10.sp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F),
                modifier = Modifier.align(Alignment.TopCenter),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = "S",
                fontSize = sizing.text(10.sp),
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.BottomCenter),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                text = "E",
                fontSize = sizing.text(10.sp),
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = sizing.spacing(5.dp))
            )
            Text(
                text = "O",
                fontSize = sizing.text(10.sp),
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = sizing.spacing(5.dp))
            )

            ComposeCanvas(
                modifier = Modifier
                    .size(sizing.component(30.dp))
                    // La rose tourne déjà avec la carte : on l'annule ici pour que l'aiguille garde
                    // le cap de l'appareil à l'écran, comme un compas posé sur la carte.
                    .graphicsLayer { rotationZ = -azimuth - mapOrientation() }
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

    /**
     * true quand la couche fluide peint elle-même le repère au-dessus de la carte : ce calque doit
     * alors se taire, sinon les deux repères se superposeraient (l'un figé sur le dernier relevé).
     */
    var smoothRenderingActive = false

    /**
     * Prévient qu'un glissement du doigt vient de déplacer la carte : la poursuite doit rendre la
     * main, sinon le geste de l'utilisateur et le recentrage se battraient à chaque image.
     */
    var onUserPan: (() -> Unit)? = null

    private val touchSlopPx =
        android.view.ViewConfiguration.get(mapView.context).scaledTouchSlop.toFloat()

    /** Point d'appui du glissement en cours. NaN = aucun doigt suivi pour l'instant. */
    private var dragAnchorX = Float.NaN
    private var dragAnchorY = Float.NaN
    private var dragReported = false

    // --- On prépare les objets de dessin une seule fois pour éviter les allocations dans draw() ---
    private val pt = android.graphics.Point()
    private val painter = LocationMarkerPainter(
        mapView.context.resources.displayMetrics.density,
        primaryColor
    )

    override fun drawMyLocation(
        canvas: android.graphics.Canvas,
        projection: org.osmdroid.views.Projection,
        lastFix: android.location.Location
    ) {
        if (!showLocationMarker || smoothRenderingActive) return

        // On réutilise le point existant !
        projection.toPixels(org.osmdroid.util.GeoPoint(lastFix.latitude, lastFix.longitude), pt)

        painter.draw(
            canvas = canvas,
            x = pt.x.toFloat(),
            y = pt.y.toFloat(),
            rotationDegrees = if (PowerProfile.mapCompassRotation) currentCompassAzimuth else 0f,
            showDirection = AppConfig.hasCompass.value
        )
    }

    override fun onTouchEvent(
        event: android.view.MotionEvent,
        mapView: org.osmdroid.views.MapView
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> anchorDrag(event.x, event.y)
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                if (dragAnchorX.isNaN()) {
                    // Sans ACTION_DOWN vu passer (avalé par un autre calque, ou fin de pincement),
                    // ce relevé fait référence : au pire on ignore le tout premier déplacement.
                    anchorDrag(event.x, event.y)
                } else if (!dragReported &&
                    // Distance et non écart par axe : les calques reçoivent des coordonnées déjà
                    // remises « carte au nord », un seuil par axe dépendrait donc de la rotation.
                    hypot(event.x - dragAnchorX, event.y - dragAnchorY) > touchSlopPx
                ) {
                    // Le doigt déplace vraiment la carte : un simple appui (marqueur, mesure) ne doit
                    // pas couper la poursuite, d'où le seuil de glissement du système plutôt que le
                    // `setEnableAutoStop` d'osmdroid, qui coupe dès qu'un doigt se pose.
                    dragReported = true
                    // Avant `super` : tant que le suivi osmdroid est actif, il avale le glissement à
                    // un doigt et la carte resterait collée à la position.
                    if (isFollowLocationEnabled) disableFollowLocation()
                    onUserPan?.invoke()
                }
            }
            // Doigt posé ou levé : les indices de pointeur sont rebattus, mesurer l'écart avec le
            // point d'appui d'un autre doigt ferait passer un pincement pour un déplacement.
            else -> anchorDrag(Float.NaN, Float.NaN)
        }
        return super.onTouchEvent(event, mapView)
    }

    private fun anchorDrag(x: Float, y: Float) {
        dragAnchorX = x
        dragAnchorY = y
        dragReported = false
    }
}

class AntennaMarker(
    private val mapView: org.osmdroid.views.MapView,
    private val siteAntennas: List<LocalisationEntity>,
    private val primaryColor: Int,
    private val satelliteContrast: Boolean = false
) : org.osmdroid.views.overlay.Marker(mapView) {

    private val density = mapView.context.resources.displayMetrics.density
    private val ptCenter = android.graphics.Point()

    // Débord du liseré de contraste, de part et d'autre du trait ou de la pastille (satellite only).
    private val outlineWidthPx = 1.2f * density
    private val thinOutlineWidthPx = 0.9f * density // pastilles FH et bords de cône : liseré plus fin

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
        val dotColors: List<Int>,
        val lineOutlinePaint: android.graphics.Paint? = null, // liseré de contraste (satellite)
        val coneEdgeOutlinePaint: android.graphics.Paint? = null
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

    private val dotOutlinePaints = mutableMapOf<Int, android.graphics.Paint>()
    private fun getDotOutlinePaint(colorInt: Int): android.graphics.Paint {
        return dotOutlinePaints.getOrPut(colorInt) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = MapUtils.contrastOutlineColor(colorInt)
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

            // Sur orthophoto, le trait est doublé d'un liseré peint dessous : sans lui, un azimut
            // Free (gris) se perd dans le bitume et les toitures (cf. MapUtils.contrastOutlineColor).
            val lineOutlinePaint = if (satelliteContrast) {
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = MapUtils.contrastOutlineColor(mainColor)
                    strokeWidth = 3.5f * density + 2f * outlineWidthPx
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }
            } else {
                null
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

            // Le remplissage du cône reste tel quel (c'est un voile à 20 %, on ne peut pas le
            // cerner sans le déformer) : ce sont ses deux bords qu'on souligne.
            val coneEdgeOutlinePaint = if (satelliteContrast) {
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = MapUtils.contrastOutlineColor(mainColor)
                    strokeWidth = 2.2f * density + 2f * thinOutlineWidthPx
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }
            } else {
                null
            }

            precalculatedMobileAzimuths.add(
                GroupedAzimuthData(
                    az, cos, sin, linePaint, conePaint, coneEdgePaint, sortedColors,
                    lineOutlinePaint, coneEdgeOutlinePaint
                )
            )
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

            // Le liseré reprend le MÊME pointillé, sinon il apparaîtrait comme un trait plein
            // sous les tirets de couleur.
            val dashedOutlinePaint = if (satelliteContrast) {
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = MapUtils.contrastOutlineColor(mainColor)
                    strokeWidth = 3f * density + 2f * outlineWidthPx
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
                }
            } else {
                null
            }

            // On passe bien "az" et "null" (Pas de cône pour les FH)
            precalculatedFhAzimuths.add(
                GroupedAzimuthData(az, cos, sin, dashedPaint, null, null, sortedColors, dashedOutlinePaint)
            )
        }
    }

    private fun getOpColorInt(name: String?): Int {
        return OperatorColors.colorInt(name, fallback = primaryColor)
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble

        // 🚨 NOUVEAU : On lit les préférences en direct
        val showLines = fr.geotower.utils.AppConfig.showAzimuths.value
        val showCones = PowerProfile.drawAzimuthCones

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
                    data.coneEdgeOutlinePaint?.let { outlinePaint ->
                        drawConeEdgeLines(canvas, data.azimuth, circleOffsetPx, totalRadiusPx, outlinePaint)
                    }
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

                    data.lineOutlinePaint?.let { canvas.drawLine(startX, startY, endX, endY, it) }
                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                    // Les pastilles se touchent (écart = un diamètre) : on pose TOUS les liserés
                    // d'abord, sinon celui d'une pastille rognerait la couleur de la précédente.
                    if (satelliteContrast) {
                        data.dotColors.forEachIndexed { index, colorInt ->
                            val offsetMag = index * gapMobile
                            canvas.drawCircle(
                                endX + (data.cos * offsetMag),
                                endY + (data.sin * offsetMag),
                                pointRadius + outlineWidthPx,
                                getDotOutlinePaint(colorInt)
                            )
                        }
                    }

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

                    data.lineOutlinePaint?.let { canvas.drawLine(startX, startY, endX, endY, it) }
                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                    if (satelliteContrast) {
                        data.dotColors.forEachIndexed { index, colorInt ->
                            val offsetMag = index * gapFh
                            canvas.drawCircle(
                                endX + (data.cos * offsetMag),
                                endY + (data.sin * offsetMag),
                                fhRadius + thinOutlineWidthPx,
                                getDotOutlinePaint(colorInt)
                            )
                        }
                    }

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
    private val showCircle: Boolean,
    private val satelliteContrast: Boolean = false
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

    // Liseré de contraste sur fond satellite : les couleurs radio les plus sombres (FH bleu nuit,
    // « autres » quasi noir) se perdent dans les ombres de l'orthophoto.
    private val outlineWidthPx = 1.2f * density
    private val lineOutlinePaint = if (satelliteContrast) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = MapUtils.contrastOutlineColor(this@RadioMarker.color)
            strokeWidth = 2.35f * density + 2f * outlineWidthPx
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    } else {
        null
    }
    private val dotOutlinePaint = if (satelliteContrast) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = MapUtils.contrastOutlineColor(this@RadioMarker.color)
        }
    } else {
        null
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

                lineOutlinePaint?.let { canvas.drawLine(startX, startY, endX, endY, it) }
                canvas.drawLine(startX, startY, endX, endY, linePaint)
                dotOutlinePaint?.let { canvas.drawCircle(endX, endY, dotRadius + outlineWidthPx, it) }
                canvas.drawCircle(endX, endY, dotRadius, dotPaint)
            }
        }
        super.draw(canvas, projection)
    }
}

/**
 * Rotation de la carte au pincement à deux doigts.
 *
 * osmdroid fournit bien un `RotationGestureOverlay`, mais il ne prévient pas quand l'utilisateur
 * tourne : on a besoin de le savoir pour couper l'alignement automatique sur la boussole, sans quoi
 * le doigt et le capteur se disputeraient la carte à chaque image.
 */
private class MapRotationGestureOverlay(
    private val map: MapView,
    private val onRotated: (Float) -> Unit
) : Overlay() {

    /** Angle courant entre les deux doigts. NaN = aucune paire suivie pour l'instant. */
    private var lastFingerAngle = Float.NaN
    /** Rotation accumulée depuis la pose des doigts, tant que l'amorce n'est pas franchie. */
    private var rotationSinceTouch = 0f
    private var engaged = false

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!isEnabled) {
            resetGesture()
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            onMove(event)
        } else {
            // Doigt posé ou levé : les indices de pointeur sont rebattus, et comparer l'ancien
            // angle au nouveau ferait sauter la carte d'un bloc. On repart d'une référence neuve.
            resetGesture()
        }
        // On ne consomme jamais : le pincement doit continuer à zoomer et le glissement à déplacer.
        return false
    }

    private fun onMove(event: MotionEvent) {
        if (event.pointerCount != 2) {
            resetGesture()
            return
        }

        val angle = fingerAngle(event)
        val previous = lastFingerAngle
        lastFingerAngle = angle
        if (previous.isNaN()) return // premier relevé du geste : rien à comparer

        val delta = shortestAngleDelta(previous, angle)
        if (!engaged) {
            rotationSinceTouch += delta
            if (abs(rotationSinceTouch) < MAP_ROTATION_GESTURE_THRESHOLD_DEG) return
            // Amorcé. On ne rattrape pas le seuil d'un coup : la carte repart d'où elle est, et
            // suivra le doigt au degré près à partir d'ici.
            engaged = true
            return
        }

        // Appliqué à chaque relevé, sans cadence bornée : la carte reste collée au doigt, et le
        // système ne repeint de toute façon qu'une fois par image, quel que soit le nombre
        // d'invalidations reçues entre deux.
        val orientation = normalizeMapOrientation(map.mapOrientation + delta)
        map.applyOrientation(orientation)
        onRotated(orientation)
    }

    private fun resetGesture() {
        lastFingerAngle = Float.NaN
        rotationSinceTouch = 0f
        engaged = false
    }

    /** Angle de la droite reliant les deux doigts, en degrés, sens horaire à l'écran. */
    private fun fingerAngle(event: MotionEvent): Float {
        val dx = (event.getX(1) - event.getX(0)).toDouble()
        val dy = (event.getY(1) - event.getY(0)).toDouble()
        return Math.toDegrees(atan2(dy, dx)).toFloat()
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
        // Culling viewport : on ne rasterise (drawCircle) que les points à l'écran (+ marge). Sur un
        // zoom serré, l'écrasante majorité des ~5000 points est hors cadre → autant d'appels évités.
        val margin = radius + 1f
        // Carte tournée : le canevas l'est aussi, et le cadre visible déborde alors du rectangle de
        // la vue dans le repère de dessin. On l'élargit à la diagonale, sinon les points des coins
        // seraient écartés à tort. Carte au nord, le cadre reste au plus juste.
        val halfWidth = canvas.width / 2f
        val halfHeight = canvas.height / 2f
        val halfSpan = if (projection.orientation % 360f == 0f) {
            null
        } else {
            hypot(halfWidth, halfHeight)
        }
        val minX = if (halfSpan == null) -margin else halfWidth - halfSpan - margin
        val minY = if (halfSpan == null) -margin else halfHeight - halfSpan - margin
        val maxX = if (halfSpan == null) canvas.width + margin else halfWidth + halfSpan + margin
        val maxY = if (halfSpan == null) canvas.height + margin else halfHeight + halfSpan + margin
        points.forEach { coveragePoint ->
            projection.toPixels(GeoPoint(coveragePoint.latitude, coveragePoint.longitude), point)
            val px = point.x
            val py = point.y
            if (px < minX || px > maxX || py < minY || py > maxY) return@forEach
            fillPaint.color = rsrpColor(coveragePoint.signalStrength)
            canvas.drawCircle(px.toFloat(), py.toFloat(), radius, fillPaint)
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
    val sizing = LocalGeoTowerUiSizing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
        Text(
            text = value,
            style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SupportChoiceDialog(
    choices: List<SupportChoice>,
    onSelect: (SupportChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.appstrings_support_picker_title, choices.size),
                fontWeight = FontWeight.Bold,
                style = sizing.textStyle(MaterialTheme.typography.titleLarge)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp)),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.appstrings_support_picker_message),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                choices.forEach { choice ->
                    SupportChoiceRow(choice = choice, onClick = { onSelect(choice) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.appstrings_cancel))
            }
        }
    )
}

@Composable
private fun SupportChoiceRow(choice: SupportChoice, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiSizing.current
    val subtitle = buildString {
        append(stringResource(R.string.appstrings_support_prefix))
        append(' ')
        append(choice.supportId)
        choice.nature?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append(" · ")
            append(it)
        }
    }
    val operatorLabel = choice.operatorKeys
        .mapNotNull { key -> OperatorColors.specForKey(key)?.label ?: key.takeIf { it.isNotBlank() } }
        .distinct()
        .joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(10.dp))
        ) {
            OperatorGrid(operators = choice.operatorKeys)
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                if (operatorLabel.isNotBlank()) {
                    Text(
                        text = operatorLabel,
                        style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(sizing.spacing(3.dp)))
                }
                Text(
                    text = subtitle,
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoveragePointDetailDialog(point: SignalQuestCoveragePoint, onDismiss: () -> Unit) {
    val sizing = LocalGeoTowerUiSizing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.appstrings_signalquest_coverage_detail_title),
                fontWeight = FontWeight.Bold,
                style = sizing.textStyle(MaterialTheme.typography.titleLarge)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp)),
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
