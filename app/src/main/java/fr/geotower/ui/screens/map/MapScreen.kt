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
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import fr.geotower.data.api.NominatimApi
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.AppLogger
import fr.geotower.utils.MapUtils
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.isNetworkAvailable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
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
import android.os.Environment
import kotlin.math.log2
import kotlin.math.roundToInt

private const val HS_OPERATOR_WILDCARD = "*"
private const val INITIAL_LOCATION_ZOOM = 16.0
private const val MOUSE_WHEEL_ZOOM_STEP = 1.0
private const val MOUSE_WHEEL_ZOOM_ANIMATION_MS = 80L
private const val WEB_MERCATOR_WORLD_TILE_SIZE_PX = 256.0
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

private fun MapView.loadVisibleAntennas(viewModel: MapViewModel) {
    val box = boundingBox
    val (lonWest, lonEast) = visibleLongitudeBounds()
    viewModel.loadAntennasInBox(zoomLevelDouble, box.latNorth, lonEast, box.latSouth, lonWest)
}

private fun MapView.clearCityFilterAndReloadVisible(viewModel: MapViewModel) {
    val box = boundingBox
    val (lonWest, lonEast) = visibleLongitudeBounds()
    viewModel.clearCityFilterAndReload(zoomLevelDouble, box.latNorth, lonEast, box.latSouth, lonWest)
}

private fun normalizedAnfrId(value: String): String {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.toString() ?: trimmed
}

private fun extractOperatorKeys(value: String?): List<String> {
    return OperatorColors.keysFor(value)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current // ✅ AJOUT
    val isUltraCompact = configuration.screenWidthDp < 300 || configuration.screenHeightDp < 350 // ✅ AJOUT
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val antennas by viewModel.antennas.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sitesHs by viewModel.sitesHs.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    val rawPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val isColorTooLight = ColorUtils.calculateLuminance(rawPrimaryColor) > 0.85
    val safePrimaryColor = remember(rawPrimaryColor, isColorTooLight) {
        if (isColorTooLight) android.graphics.Color.parseColor("#2196F3") else rawPrimaryColor
    }

    // Mémorise les tracés de la ville sélectionnée pour le filtrage
    var currentCityPolygons by remember { mutableStateOf<List<List<GeoPoint>>?>(null) }

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        AppConfig.defaultOperator.value = prefs.getString("default_operator", "Aucun") ?: "Aucun"
        // ✅ AJOUT : On lit la préférence, mais le défaut est 'true' si la clé n'existe pas
        AppConfig.showAzimuths.value = prefs.getBoolean(
            AppConfig.PREF_SHOW_AZIMUTH_LINES,
            AppConfig.DEFAULT_SHOW_AZIMUTH_LINES
        )
        AppConfig.showAzimuthsCone.value = prefs.getBoolean(
            AppConfig.PREF_SHOW_AZIMUTH_CONES,
            AppConfig.DEFAULT_SHOW_AZIMUTH_CONES
        )
        AppConfig.showSitesInService.value = prefs.getBoolean("show_sites_in_service", true)
        AppConfig.showSitesOutOfService.value = prefs.getBoolean("show_sites_out_of_service", true)

        // ✅ LA LIGNE MANQUANTE EST ICI : On charge la préférence du compteur
        AppConfig.showSpeedometer.value = prefs.getBoolean("show_speedometer", true)
        AppConfig.showMapLocationMarker.value = prefs.getBoolean(AppConfig.PREF_SHOW_MAP_LOCATION_MARKER, true)
    }

    val safeClick = rememberSafeClick()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showLayerSheet by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    var currentZoom by remember { mutableDoubleStateOf(15.0) }
    var currentLat by remember { mutableDoubleStateOf(48.8584) }
    var isMeasuringMode by remember { mutableStateOf(false) }
    var trackNearestAll by remember { mutableStateOf(false) }
    var trackNearestFav by remember { mutableStateOf(false) }
    val measuredSites = remember { mutableStateMapOf<String, LocalisationEntity>() }

    var isClosestSiteExpanded by remember { mutableStateOf(true) }
    var isClosestFavSiteExpanded by remember { mutableStateOf(true) }

    // Rétablit l'auto-ouverture à l'activation du mode mesure
    LaunchedEffect(isMeasuringMode) {
        if (isMeasuringMode) {
            isClosestSiteExpanded = true
            isClosestFavSiteExpanded = true
        }
    }

    val measureOverlay = remember { FolderOverlay() }
    val searchBoundaryOverlay = remember { FolderOverlay() }
    // ✅ LE CALQUE MACRO POUR LA VUE DÉZOOMÉE
    val macroOverlay = remember { FolderOverlay() }

    val showLocationBtn by remember { mutableStateOf(prefs.getBoolean("show_map_location", true)) }
    val showZoomBtns by remember { mutableStateOf(prefs.getBoolean("show_map_zoom", true)) }
    val showToolbox by remember { mutableStateOf(prefs.getBoolean("show_map_toolbox", true)) }
    val showCompass by remember { mutableStateOf(prefs.getBoolean("show_map_compass", true)) }
    val showScale by remember { mutableStateOf(prefs.getBoolean("show_map_scale", true)) }
    val showAttribution by remember { mutableStateOf(prefs.getBoolean("show_map_attribution", true)) }
    val showLocationMarker by AppConfig.showMapLocationMarker

    var myCurrentLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var currentSpeedKmH by remember { mutableIntStateOf(0) }
    var isToolboxExpanded by remember { mutableStateOf(false) }
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    // ✅ CORRECTION : Gère le geste "Retour" physique du téléphone
    androidx.activity.compose.BackHandler {
        if (isMeasuringMode) {
            isMeasuringMode = false
            measuredSites.clear()
        } else {
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
    LaunchedEffect(AppConfig.mapProvider.intValue) {
        effectiveProvider = AppConfig.mapProvider.intValue
    }

    // Vérification réseau + fichiers au premier affichage
    LaunchedEffect(Unit) {
        val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
        val files = offlineDir.listFiles { file -> file.extension == "map" && file.length() > 0L } ?: emptyArray()
        mapFiles = files

        // Si on est hors ligne ET qu'on a bien téléchargé une carte
        if (!isNetworkAvailable(context) && files.isNotEmpty()) {
            effectiveProvider = 4 // On bascule silencieusement sur le hors-ligne
        }
    }

    val ignStyle by AppConfig.ignStyle
    val shouldInvertColors = ((mapProvider == 0 || mapProvider == 1) && ignStyle == 1)

    var azimuth by remember { mutableFloatStateOf(0f) }
    var continuousAzimuth by remember { mutableFloatStateOf(0f) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var showCityStatsPopup by remember { mutableStateOf(false) }
    var showCityStatsDetail by remember { mutableStateOf(false) }
    var isTrackingActive by remember { mutableStateOf(false) }
    var hasCenteredOnLocation by remember { mutableStateOf(false) }

    val txtMapTitle = AppStrings.mapTitle
    val txtSearchCityOrId = AppStrings.searchCityOrId
    val txtLocationNotFound = AppStrings.locationNotFound
    val txtNetworkErrorSearch = AppStrings.networkErrorSearch
    val txtDeleteTraces = AppStrings.deleteTraces
    val txtClosestSite = AppStrings.closestSite
    val txtFilter = AppStrings.filter
    val txtMapIgnLayer = AppStrings.mapIgnLayer
    val txtMapOsmLayer = AppStrings.mapOsmLayer
    val txtMapLight = AppStrings.mapLight
    val txtMapDark = AppStrings.mapDark
    val txtMapSatellite = AppStrings.mapSatellite
    val txtMapMapLibre = AppStrings.mapMapLibre
    val txtMapTopo = AppStrings.mapTopo
    val txtMapOfflineLayer = AppStrings.mapOfflineLayer

    val txtWarningTitle = AppStrings.warningTitle
    val txtLightColorWarning = AppStrings.lightColorWarning
    val txtDoNotShowAgain = AppStrings.doNotShowAgain
    val txtUnderstood = AppStrings.understood

    var hideColorWarning by remember { mutableStateOf(prefs.getBoolean("hide_light_color_warning", false)) }
    var showColorWarningDialog by remember { mutableStateOf(false) }
    var dontShowAgainChecked by remember { mutableStateOf(false) }
    val lastTilesColorFilterMap = remember { arrayOfNulls<MapView>(1) }
    val lastTilesColorFilterInverted = remember { arrayOfNulls<Boolean>(1) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rMatrix, event.values)

                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rMatrix, orientation)
                    var rawAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                    rawAzimuth = (rawAzimuth + 360) % 360

                    var delta = rawAzimuth - (continuousAzimuth % 360f)
                    if (delta < -180f) delta += 360f
                    else if (delta > 180f) delta -= 360f

                    continuousAzimuth += delta * 0.15f

                    if (Math.abs(continuousAzimuth - azimuth) > 0.5f) {
                        azimuth = continuousAzimuth
                    }
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
                        sensorManager.registerListener(sensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
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
            viewModel.resetCityLock()
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager?.unregisterListener(sensorEventListener)
            mapViewRef?.onPause()
            mapViewRef?.onDetach()
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
        AppConfig.f5G_700.value, AppConfig.f5G_2100.value, AppConfig.f5G_3500.value, AppConfig.f5G_26000.value,
        AppConfig.showSitesInService.value, AppConfig.showSitesOutOfService.value, sitesHs, currentCityPolygons
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val selectedOperators = AppConfig.selectedOperatorKeys.value
            val showSitesInService = AppConfig.showSitesInService.value
            val showSitesOutOfService = AppConfig.showSitesOutOfService.value
            val sFh = AppConfig.showTechnoFH.value
            val s2G = AppConfig.showTechno2G.value; val s3G = AppConfig.showTechno3G.value
            val s4G = AppConfig.showTechno4G.value; val s5G = AppConfig.showTechno5G.value
            val f2_900 = AppConfig.f2G_900.value; val f2_1800 = AppConfig.f2G_1800.value
            val f3_900 = AppConfig.f3G_900.value; val f3_2100 = AppConfig.f3G_2100.value
            val f4_700 = AppConfig.f4G_700.value; val f4_800 = AppConfig.f4G_800.value; val f4_900 = AppConfig.f4G_900.value
            val f4_1800 = AppConfig.f4G_1800.value; val f4_2100 = AppConfig.f4G_2100.value; val f4_2600 = AppConfig.f4G_2600.value
            val f5_700 = AppConfig.f5G_700.value; val f5_2100 = AppConfig.f5G_2100.value; val f5_3500 = AppConfig.f5G_3500.value; val f5_26000 = AppConfig.f5G_26000.value
            val hsOperatorMap = buildHsOperatorMap(sitesHs)

            val result = antennas.filter { antenna ->
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
                    return@filter visibleOperators.isNotEmpty()
                }

                // --- 3. POUR LES VRAIES ANTENNES, ON CONTINUE AVEC LE RESTE DES FILTRES ---
                val isInCityBounds = currentCityPolygons.isNullOrEmpty() || currentCityPolygons!!.any { poly -> isPointInPolygon(antenna.latitude, antenna.longitude, poly) }

                val isFhOnly = antenna.azimuts.isNullOrBlank() && !antenna.azimutsFh.isNullOrBlank()
                val matchFh = if (!sFh && isFhOnly) false else true

                val f = antenna.filtres ?: ""
                var matchTechno = false

                if (s2G && ((f2_900 && f.contains("2G900")) || (f2_1800 && f.contains("2G1800")))) matchTechno = true
                if (!matchTechno && s3G && ((f3_900 && f.contains("3G900")) || (f3_2100 && f.contains("3G2100")))) matchTechno = true
                if (!matchTechno && s4G && ((f4_700 && f.contains("4G700")) || (f4_800 && f.contains("4G800")) || (f4_900 && f.contains("4G900")) || (f4_1800 && f.contains("4G1800")) || (f4_2100 && f.contains("4G2100")) || (f4_2600 && f.contains("4G2600")))) matchTechno = true
                if (!matchTechno && s5G && ((f5_700 && f.contains("5G700")) || (f5_2100 && f.contains("5G2100")) || (f5_3500 && f.contains("5G3500")) || (f5_26000 && f.contains("5G26000")))) matchTechno = true
                if (!matchTechno && !antenna.azimutsFh.isNullOrBlank() && sFh) matchTechno = true

                // Si la base ne connait pas les fréquences de cette antenne,
                // on la cache directement, SAUF si le filtre est vierge (tout est coché par défaut)
                if (f.isBlank() && antenna.azimutsFh.isNullOrBlank()) {
                    matchTechno = (s2G && s3G && s4G && s5G && sFh)
                }

                visibleOperators.isNotEmpty() && matchFh && isInCityBounds && matchTechno
            }
            filteredAntennas = result
        }
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
        val myLoc = myCurrentLoc ?: locationOverlayRef?.myLocation ?: return

        measuredSites.values.forEach { antenna ->
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

    // ✅ AJOUT DU PARAMÈTRE sitesHsList
    suspend fun updateMarkers(map: MapView, antennasList: List<LocalisationEntity>, sitesHsList: List<SiteHsEntity> = emptyList()) {
        val selectedOperators = AppConfig.selectedOperatorKeys.value
        val showSitesInService = AppConfig.showSitesInService.value
        val showSitesOutOfService = AppConfig.showSitesOutOfService.value

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {

            if (antennasList.isEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    macroOverlay.items.clear()
                    markersOverlay.items.clear()
                    markersOverlay.invalidate()
                    map.invalidate()
                }
                return@withContext
            }

            // Table de correspondance ANFR -> operateurs declares HS.
            val hsOperatorMap = buildHsOperatorMap(sitesHsList)

            val isClusterMode = antennasList.first().idAnfr.startsWith("CLUSTER_")

            if (isClusterMode) {
                // ... (Ton code actuel MACRO reste identique)
                val clusterMarkers = antennasList.map { fakeAntenna ->
                    val count = fakeAntenna.idAnfr.removePrefix("CLUSTER_").toIntOrNull() ?: 1
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    markersOverlay.items.clear(); markersOverlay.invalidate(); macroOverlay.items.clear(); macroOverlay.items.addAll(clusterMarkers); map.invalidate()
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

                        if (activeOps.isEmpty()) null else antenna.copy(operateur = activeOps.joinToString(", "))
                    }
                    if (filteredSiteAntennas.isEmpty()) return@mapNotNull null

                    val mainAntenna = filteredSiteAntennas.first()

                    // Le marqueur UNIQUE (L'antenne)
                    AntennaMarker(map, filteredSiteAntennas, safePrimaryColor).apply {
                        position = GeoPoint(mainAntenna.latitude, mainAntenna.longitude)
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)

                        infoWindow = null // Pas de bulle grise par défaut

                        val operatorsOnSite = filteredSiteAntennas.mapNotNull { it.operateur }
                            .flatMap { OperatorColors.keysFor(it) }
                            .distinct()
                        relatedObject = operatorsOnSite

                        // 1. On génère l'icône de base (avec la bordure de couleur de l'opérateur)
                        val baseIcon = MapUtils.createAdaptiveMarker(context, filteredSiteAntennas, map.zoomLevelDouble >= 13.0 && AppConfig.showAzimuths.value, AppConfig.defaultOperator.value)

                        // 2. LOGIQUE DE FUSION : On vérifie TOUTES les antennes du pylône partagé !
                        val isHs = filteredSiteAntennas.any { antenna ->
                            hasVisibleHsOperator(antenna, hsOperatorMap)
                        }

                        if (isHs) {

                            val badgeIcon = createHsBadge(context) // Notre point d'exclamation

                            // A. Création d'une "toile" vide de la taille de l'icône de base
                            val combinedBitmap = android.graphics.Bitmap.createBitmap(
                                baseIcon.intrinsicWidth,
                                baseIcon.intrinsicHeight,
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(combinedBitmap)

                            // B. On dessine l'icône colorée de l'opérateur au fond
                            baseIcon.setBounds(0, 0, canvas.width, canvas.height)
                            baseIcon.draw(canvas)

                            // C. On dessine le point d'exclamation parfaitement centré par-dessus
                            val offsetX = (canvas.width - badgeIcon.intrinsicWidth) / 2
                            val offsetY = (canvas.height - badgeIcon.intrinsicHeight) / 2
                            badgeIcon.setBounds(offsetX, offsetY, offsetX + badgeIcon.intrinsicWidth, offsetY + badgeIcon.intrinsicHeight)
                            badgeIcon.draw(canvas)

                            // D. On applique l'image fusionnée au marqueur
                            icon = android.graphics.drawable.BitmapDrawable(context.resources, combinedBitmap)
                        } else {
                            // Si pas en panne, on applique l'icône normale
                            icon = baseIcon
                        }

                        // L'action de clic reste unique et propre !
                        setOnMarkerClickListener { _, _ ->
                            if (isMeasuringMode) {
                                val id = mainAntenna.idAnfr
                                if (measuredSites.containsKey(id)) measuredSites.remove(id) else if (myCurrentLoc != null) measuredSites[id] = mainAntenna
                                refreshMeasureLayers(map)
                            } else {
                                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                                prefs.edit().putFloat("clicked_lat", mainAntenna.latitude.toFloat()).putFloat("clicked_lon", mainAntenna.longitude.toFloat()).apply()
                                navController.navigate("support_detail/${mainAntenna.idAnfr.toLongOrNull() ?: 0L}")
                            }
                            true
                        }
                    } // Fin du apply (retourne 1 seul marqueur)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
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
        AppConfig.showSitesOutOfService.value
    ) {
        mapViewRef?.let { map ->
            updateMarkers(map, filteredAntennas, sitesHs)
        }
    }

    LaunchedEffect(AppConfig.showSitesInService.value, AppConfig.showSitesOutOfService.value) {
        mapViewRef?.let { map ->
            map.loadVisibleAntennas(viewModel)
        }
    }

    LaunchedEffect(myCurrentLoc) {
        if (isMeasuringMode && measuredSites.isNotEmpty()) {
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    overlays.add(measureOverlay)
                    overlays.add(searchBoundaryOverlay)
                    overlays.add(macroOverlay) // <-- Calque macro au fond
                    overlays.add(markersOverlay) // <-- Calque micro au milieu
                    overlays.add(locationOverlay) // <-- Curseur devant

                    locationOverlayRef = locationOverlay

                    var lastRadius = 250
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            // La poursuite GPS garde le controle pendant les scrolls generes par le suivi.
                            updateInfo()
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean { updateInfo(); return true }
                        private fun updateInfo() {
                            // ✅ 1. MISE À JOUR INSTANTANÉE POUR L'ÉCHELLE (Avant le delay !)
                            currentZoom = zoomLevelDouble
                            currentLat = mapCenter.latitude

                            // ✅ 2. LE RESTE DU CALCUL AVEC SON PETIT DÉLAI ANTI-LAG
                            searchJob?.cancel()
                            searchJob = scope.launch {
                                delay(100)

                                val z = zoomLevelDouble

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

                                this@apply.loadVisibleAntennas(viewModel)
                            }
                        }
                    })
                    post { this@apply.loadVisibleAntennas(viewModel) }
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
            if (cleanQuery.isBlank()) return

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
                            Toast.makeText(context, AppStrings.mapSiteNotInArea(context, cleanQuery), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                // 3. Recherche de Ville / Adresse via internet (Nominatim)
                val nominatimArea = NominatimApi.searchArea(cleanQuery)
                if (nominatimArea != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        mapViewRef?.let { map ->
                            searchBoundaryOverlay.items.clear()

                            nominatimArea.geoJsonFeature?.let { geoJsonString ->
                                val kmlDocument = org.osmdroid.bonuspack.kml.KmlDocument()
                                kmlDocument.parseGeoJSON(geoJsonString)
                                val featureOverlay = kmlDocument.mKmlRoot.buildOverlay(map, null, null, kmlDocument)

                                val holesList = mutableListOf<List<GeoPoint>>()
                                val outlinesOverlay = org.osmdroid.views.overlay.FolderOverlay()

                                fun extractHolesAndOutlines(overlay: org.osmdroid.views.overlay.Overlay) {
                                    when (overlay) {
                                        is org.osmdroid.views.overlay.Polygon -> {
                                            @Suppress("DEPRECATION")
                                            val overlayPoints = overlay.points
                                            holesList.add(overlayPoints)
                                            val outline = org.osmdroid.views.overlay.Polyline(map).apply {
                                                setPoints(overlayPoints)
                                                outlinePaint.color = android.graphics.Color.RED
                                                outlinePaint.strokeWidth = 4f
                                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
                                            }
                                            outlinesOverlay.add(outline)
                                        }
                                        is org.osmdroid.views.overlay.Polyline -> {
                                            @Suppress("DEPRECATION")
                                            val overlayPoints = overlay.points
                                            val outline = org.osmdroid.views.overlay.Polyline(map).apply {
                                                setPoints(overlayPoints)
                                                outlinePaint.color = android.graphics.Color.RED
                                                outlinePaint.strokeWidth = 4f
                                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
                                            }
                                            outlinesOverlay.add(outline)
                                        }
                                        is org.osmdroid.views.overlay.FolderOverlay -> {
                                            overlay.items.forEach { extractHolesAndOutlines(it) }
                                        }
                                    }
                                }

                                extractHolesAndOutlines(featureOverlay)

                                currentCityPolygons = holesList

                                viewModel.loadAntennasForCity(
                                    latNorth = nominatimArea.latNorth,
                                    lonEast = nominatimArea.lonEast,
                                    latSouth = nominatimArea.latSouth,
                                    lonWest = nominatimArea.lonWest,
                                    polygons = holesList
                                )

                                showCityStatsPopup = true

                                val worldMask = object : org.osmdroid.views.overlay.Overlay() {
                                    private val path = android.graphics.Path()

                                    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
                                        if (holesList.isEmpty()) return

                                        path.reset()
                                        holesList.forEach { geoPoints ->
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
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            canvas.clipOutPath(path)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE)
                                        }

                                        canvas.drawColor(android.graphics.Color.parseColor("#66000000"))
                                        canvas.restore()
                                    }
                                }

                                searchBoundaryOverlay.add(worldMask)
                                searchBoundaryOverlay.add(outlinesOverlay)
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
                            mapViewRef?.let { map ->
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
        if (!isUltraCompact) {

            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                .padding(start = 16.dp, end = 16.dp, top = 110.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
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
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = txtSearchCityOrId,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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
                                onSearch = {
                                    performSearch(searchQuery)
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    IconButton(
                        onClick = {
                            performSearch(searchQuery)
                            focusManager.clearFocus()
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = AppStrings.search,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        val compassTopPadding by animateDpAsState(
            targetValue = if (isSearchActive) 186.dp else 112.dp,
            label = "compassAnim"
        )
        val toolsTopPadding by animateDpAsState(
            targetValue = if (isSearchActive) 250.dp else 176.dp,
            label = "toolsAnim"
        )

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
                measureOverlay = measureOverlay
            )
        }

        if (showCompass && AppConfig.hasCompass.value) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = compassTopPadding)
                    .size(52.dp)
                    .clickable {
                        safeClick {
                            mapViewRef?.mapOrientation = 0f
                            mapViewRef?.invalidate()
                        }
                    },
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
                        "E",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp)
                    )
                    Text(
                        "O",
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
                            moveTo(w / 2f, topTipY); lineTo(
                            w / 2f,
                            h / 2f
                        ); lineTo(w / 4f, h / 2f); close()
                        }
                        val pathNorthRight = Path().apply {
                            moveTo(w / 2f, topTipY); lineTo(
                            w * 3 / 4f,
                            h / 2f
                        ); lineTo(w / 2f, h / 2f); close()
                        }
                        drawPath(pathNorthLeft, Color(0xFFD32F2F))
                        drawPath(pathNorthRight, Color(0xFFF44336))

                        val pathSouthLeft = Path().apply {
                            moveTo(w / 2f, bottomTipY); lineTo(
                            w / 2f,
                            h / 2f
                        ); lineTo(w / 4f, h / 2f); close()
                        }
                        val pathSouthRight = Path().apply {
                            moveTo(w / 2f, bottomTipY); lineTo(
                            w * 3 / 4f,
                            h / 2f
                        ); lineTo(w / 2f, h / 2f); close()
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

        val defaultOp by AppConfig.defaultOperator

        val darkMaterialColor = Color(0xFF37474F)
        val opColor = OperatorColors.keyFor(defaultOp)
            ?.let { Color(OperatorColors.colorArgbForKey(it)) }
            ?: MaterialTheme.colorScheme.primary

        // --- LES BOUTONS DE SUIVI (MODE MESURE) ---
        // --- LES BOUTONS DE SUIVI "A TIROIR" (MODE MESURE) ---
        AnimatedVisibility(
            visible = isMeasuringMode,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = toolsTopPadding)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ========================================================
                // 1. TIROIR SUIVI GLOBAL
                // ========================================================
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // La petite barre verticale cliquable (le toggle)
                    Box(
                        modifier = Modifier
                            .height(40.dp).width(12.dp)
                            // ✅ NOUVEAU : Bords arrondis de tous les côtés pour la poignée
                            .background(darkMaterialColor, RoundedCornerShape(6.dp))
                            .clickable { safeClick { isClosestSiteExpanded = !isClosestSiteExpanded } }
                    )

                    // ✅ NOUVEAU : Le petit espace qui détache élégamment le bouton
                    Spacer(modifier = Modifier.width(6.dp))

                    // Le contenu du tiroir (le bouton)
                    AnimatedVisibility(
                        visible = isClosestSiteExpanded,
                        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                    ) {
                        Button(
                            onClick = { safeClick { trackNearestAll = !trackNearestAll } },
                            modifier = Modifier.height(40.dp).width(210.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            // ✅ NOUVEAU : Forme de pilule parfaite
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = darkMaterialColor, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.NearMe, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (trackNearestAll) AppStrings.trackGlobalActive else txtClosestSite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ========================================================
                // 2. TIROIR SUIVI OPÉRATEUR PRÉFÉRÉ
                // ========================================================
                if (defaultOp != "Aucun") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // La petite barre verticale cliquable (le toggle)
                        Box(
                            modifier = Modifier
                                .height(40.dp).width(12.dp)
                                // ✅ NOUVEAU : Bords arrondis de tous les côtés
                                .background(opColor, RoundedCornerShape(6.dp))
                                .clickable { safeClick { isClosestFavSiteExpanded = !isClosestFavSiteExpanded } }
                        )

                        // ✅ NOUVEAU : Le petit espace
                        Spacer(modifier = Modifier.width(6.dp))

                        // Le contenu du tiroir (le bouton)
                        AnimatedVisibility(
                            visible = isClosestFavSiteExpanded,
                            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
                        ) {
                            Button(
                                onClick = { safeClick { trackNearestFav = !trackNearestFav } },
                                modifier = Modifier.height(40.dp).width(210.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                // ✅ NOUVEAU : Forme de pilule parfaite
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = opColor, contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (trackNearestFav) AppStrings.trackOpActive(defaultOp) else "$txtClosestSite $defaultOp",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )                            }
                        }
                    }
                }
            }
        }


        Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SmallFloatingButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    desc = AppStrings.back,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    safeBackNavigation.navigateBack()
                }
                Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text(txtMapTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }

                SmallFloatingButton(
                    icon = Icons.Default.Menu,
                    desc = txtFilter,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) { safeClick { showSettingsSheet = true } }
            }

            val deleteButtonSpacer by animateDpAsState(
                targetValue = if (isSearchActive) 93.dp else 19.dp,
                label = "deleteButtonAnim"
            )
            Spacer(modifier = Modifier.height(deleteButtonSpacer))

            AnimatedVisibility(visible = measuredSites.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Button(
                    onClick = {
                        // ✅ CORRECTION : On coupe tout !
                        trackNearestAll = false
                        trackNearestFav = false
                        measuredSites.clear()
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 32.dp, end = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showToolbox) {
                AntennaMapToolBox(
                    isToolboxExpanded = isToolboxExpanded,
                    onToggleToolbox = {
                        isToolboxExpanded = !isToolboxExpanded
                        if (!isToolboxExpanded) {
                            isMeasuringMode = false

                            isSearchActive = false
                            searchQuery = ""
                            currentCityPolygons = null
                            searchBoundaryOverlay.items.clear()

                            // ✅ CORRECTION DU NOM ET APPEL AVEC LE ZOOM
                            mapViewRef?.let { map ->
                                map.clearCityFilterAndReloadVisible(viewModel)
                            }

                            trackNearestAll = false
                            trackNearestFav = false
                            measuredSites.clear()
                            mapViewRef?.let { refreshMeasureLayers(it) }
                            mapViewRef?.invalidate()
                        }
                    },
                    isSearchActive = isSearchActive,
                    onToggleSearch = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            currentCityPolygons = null
                            searchBoundaryOverlay.items.clear()

                            // ✅ CORRECTION DU NOM ET APPEL AVEC LE ZOOM
                            mapViewRef?.let { map ->
                                map.clearCityFilterAndReloadVisible(viewModel)
                            }

                            mapViewRef?.invalidate()
                        }
                    },
                    isMeasuringMode = isMeasuringMode,
                    onToggleMeasure = {
                        isMeasuringMode = !isMeasuringMode
                        if (!isMeasuringMode) {
                            trackNearestAll = false
                            trackNearestFav = false
                            measuredSites.clear()
                            mapViewRef?.let { refreshMeasureLayers(it) }
                        }
                    },
                    onOpenLayers = { safeClick { showLayerSheet = true } },
                    onOpenSettings = { safeClick { navController.navigate("settings?section=map") } }
                )
            }

            if (showZoomBtns) {
                Surface(
                    modifier = Modifier.width(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = { mapViewRef?.controller?.zoomIn() },
                            modifier = Modifier.size(54.dp)
                        ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(26.dp)) }
                        HorizontalDivider(
                            modifier = Modifier.width(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                        IconButton(
                            onClick = { mapViewRef?.controller?.zoomOut() },
                            modifier = Modifier.size(54.dp)
                        ) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(26.dp)) }
                    }
                }
            }
            if (showLocationBtn) {
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
                    modifier = Modifier.size(56.dp)
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
                            contentDescription = AppStrings.locate,
                            tint = if (isTrackingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 32.dp)
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


        if (showLayerSheet) {
            // ✅ On vérifie l'état du réseau dès que le menu s'ouvre
            val isOnline = remember(showLayerSheet) { isNetworkAvailable(context) }

            // 🚀 NOUVEAU : On vérifie si au moins une carte est téléchargée
            val hasOfflineMaps = remember(showLayerSheet) {
                val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
                val mapFiles = offlineDir.listFiles { file -> file.extension == "map" }
                !mapFiles.isNullOrEmpty()
            }

            val txtOfflineMessage = AppStrings.offlineMessage

            ModalBottomSheet(
                onDismissRequest = { showLayerSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // ✅ 1. ON CRÉE LA COLONNE GLOBALE QUI APPLIQUE LE PADDING À TOUT
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)
                ) {

                    // ✅ 2. LA COLONNE DES PREMIERS BOUTONS
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 🌐 ON N'AFFICHE LES CARTES EN LIGNE QUE SI ON A INTERNET
                        if (isOnline) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MapLayerButton(txtMapOsmLayer, mapProvider == 1, Modifier.weight(1f)) {
                                    AppConfig.mapProvider.value = 1; prefs.edit().putInt("map_provider", 1).apply()
                                    if (ignStyle == 2) { AppConfig.ignStyle.value = 0; prefs.edit().putInt("ign_style", 0).apply() }
                                }
                                MapLayerButton(txtMapIgnLayer, mapProvider == 0, Modifier.weight(1f)) {
                                    AppConfig.mapProvider.value = 0; prefs.edit().putInt("map_provider", 0).apply()
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MapLayerButton(txtMapMapLibre, mapProvider == 2, Modifier.weight(1f)) {
                                    AppConfig.mapProvider.value = 2; prefs.edit().putInt("map_provider", 2).apply()
                                }
                                MapLayerButton(txtMapTopo, mapProvider == 3, Modifier.weight(1f)) {
                                    AppConfig.mapProvider.value = 3; prefs.edit().putInt("map_provider", 3).apply()
                                }
                            }
                        } else {
                            // 📵 MESSAGE HORS-LIGNE
                            Text(
                                text = "⚠️ $txtOfflineMessage",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp, top = 8.dp)
                            )
                        }

                        // 🗺️ LE BOUTON HORS-LIGNE NE S'AFFICHE QUE SI UNE CARTE EXISTE
                        if (hasOfflineMaps) {
                            MapLayerButton(txtMapOfflineLayer, mapProvider == 4, Modifier.fillMaxWidth()) {
                                AppConfig.mapProvider.value = 4
                                prefs.edit().putInt("map_provider", 4).apply()
                            }
                        } else if (!isOnline) {
                            // Optionnel : un petit message pour dire qu'aucune carte n'est dispo si on est hors ligne
                            Text(
                                text = AppStrings.noOfflineMapsInstalled,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
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
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Text(
                        text = AppStrings.cityStatsTitle,
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
                                    text = AppStrings.mobileTelephony,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            } else {
                                val uniqueSupportsCount = filteredAntennas.distinctBy { "${it.latitude}_${it.longitude}" }.size

                                Text(
                                    text = uniqueSupportsCount.toString(),
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showCityStatsDetail = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(AppStrings.details, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    TextButton(onClick = { showCityStatsPopup = false }) {
                        Text(AppStrings.close, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showCityStatsDetail) {
        fr.geotower.ui.components.CityStatsDetailSheet(
            antennas = antennas,
            onDismiss = { showCityStatsDetail = false }
        )
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
    val bgColor = if (isSelected) Color(0xFF3B5998) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(14.dp), color = bgColor) {
        Box(contentAlignment = Alignment.Center) { Text(text = text, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
    }
}

@Composable
private fun SmallFloatingButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc) }
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

    init {
        // 1. On prépare des dictionnaires pour regrouper : Angle -> Liste de Couleurs (Opérateurs)
        val angleToColorsMobile = mutableMapOf<Float, MutableSet<Int>>()
        val angleToColorsFh = mutableMapOf<Float, MutableSet<Int>>()

        siteAntennas.forEach { antenna ->
            val opColorInt = getOpColorInt(antenna.operateur)

            if (!antenna.azimuts.isNullOrBlank()) {
                antenna.azimuts.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToColorsMobile.getOrPut(az) { mutableSetOf() }.add(opColorInt)
                }
            }

            if (fr.geotower.utils.AppConfig.showTechnoFH.value && !antenna.azimutsFh.isNullOrBlank()) {
                antenna.azimutsFh.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToColorsFh.getOrPut(az) { mutableSetOf() }.add(opColorInt)
                }
            }
        }

        // L'opérateur par défaut qu'il faut prioriser pour la couleur du trait
        val defOpColorInt = getOpColorInt(fr.geotower.utils.AppConfig.defaultOperator.value.uppercase())

        // 2. On transforme ces groupes en données de dessin (Cos/Sin précalculés)
        angleToColorsMobile.forEach { (az, colorsSet) ->
            val rad = Math.toRadians(az - 90.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()

            // On trie pour que la couleur de l'opérateur favori soit en premier (prioritaire)
            val sortedColors = colorsSet.toList().sortedByDescending { it == defOpColorInt }
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
        angleToColorsFh.forEach { (az, colorsSet) ->
            val rad = Math.toRadians(az - 90.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()

            val sortedColors = colorsSet.toList().sortedByDescending { it == defOpColorInt }
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
    val size = (32 * density).toInt()
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

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
