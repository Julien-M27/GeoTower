package fr.geotower.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.navigation.NavController
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import fr.geotower.utils.MapUtils
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
        AppConfig.showAzimuths.value = prefs.getBoolean("show_azimuths", true)

        // ✅ LA LIGNE MANQUANTE EST ICI : On charge la préférence du compteur
        AppConfig.showSpeedometer.value = prefs.getBoolean("show_speedometer", true)
    }

    var lastClickTime by remember { mutableLongStateOf(0L) }
    var pauseMapUpdatesUntil by remember { mutableLongStateOf(0L) }
    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 700L) { lastClickTime = currentTime; action() }
    }

    // --TAILLE DE L'APP--

    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showLayerSheet by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var showZoomMessage by remember { mutableStateOf(false) }

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

    var myCurrentLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var currentSpeedKmH by remember { mutableIntStateOf(0) }
    var isToolboxExpanded by remember { mutableStateOf(false) }

    // ✅ CORRECTION : Gère le geste "Retour" physique du téléphone
    androidx.activity.compose.BackHandler {
        if (isMeasuringMode) {
            isMeasuringMode = false
            measuredSites.clear()
        } else {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                navController.navigate("home") {
                    popUpTo(0)
                }
            }
        }
    }

    val markersOverlay = remember {
        object : org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer(context) {
            override fun buildClusterMarker(cluster: org.osmdroid.bonuspack.clustering.StaticCluster, mapView: MapView): Marker {
                val m = Marker(mapView)

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

    val txtMapTitle = AppStrings.mapTitle
    val txtSearchCityOrId = AppStrings.searchCityOrId
    val txtSiteNotInArea = AppStrings.siteNotInArea
    val txtLocationNotFound = AppStrings.locationNotFound
    val txtNetworkErrorSearch = AppStrings.networkErrorSearch
    val txtDeleteTraces = AppStrings.deleteTraces
    val txtClosestSite = AppStrings.closestSite
    val txtNoSiteNearby = AppStrings.noSiteNearby
    val txtNearby = AppStrings.nearby
    val txtFilter = AppStrings.filter
    val txtMapLayerTitle = AppStrings.mapLayerTitle
    val txtMapIgnLayer = AppStrings.mapIgnLayer
    val txtMapOsmLayer = AppStrings.mapOsmLayer
    val txtMapLight = AppStrings.mapLight
    val txtMapDark = AppStrings.mapDark
    val txtMapSatellite = AppStrings.mapSatellite
    val txtMapMapLibre = AppStrings.mapMapLibre
    val txtMapTopo = AppStrings.mapTopo

    val txtWarningTitle = AppStrings.warningTitle
    val txtLightColorWarning = AppStrings.lightColorWarning
    val txtDoNotShowAgain = AppStrings.doNotShowAgain
    val txtUnderstood = AppStrings.understood

    var hideColorWarning by remember { mutableStateOf(prefs.getBoolean("hide_light_color_warning", false)) }
    var showColorWarningDialog by remember { mutableStateOf(false) }
    var dontShowAgainChecked by remember { mutableStateOf(false) }

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
        antennas, AppConfig.showOrange.value, AppConfig.showSfr.value, AppConfig.showBouygues.value, AppConfig.showFree.value,
        AppConfig.showTechnoFH.value, AppConfig.showTechno2G.value, AppConfig.showTechno3G.value, AppConfig.showTechno4G.value, AppConfig.showTechno5G.value,
        AppConfig.f2G_900.value, AppConfig.f2G_1800.value, AppConfig.f3G_900.value, AppConfig.f3G_2100.value,
        AppConfig.f4G_700.value, AppConfig.f4G_800.value, AppConfig.f4G_900.value, AppConfig.f4G_1800.value, AppConfig.f4G_2100.value, AppConfig.f4G_2600.value,
        AppConfig.f5G_700.value, AppConfig.f5G_2100.value, AppConfig.f5G_3500.value, AppConfig.f5G_26000.value, currentCityPolygons
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val sOrange = AppConfig.showOrange.value; val sSfr = AppConfig.showSfr.value
            val sBouygues = AppConfig.showBouygues.value; val sFree = AppConfig.showFree.value
            val sFh = AppConfig.showTechnoFH.value
            val s2G = AppConfig.showTechno2G.value; val s3G = AppConfig.showTechno3G.value
            val s4G = AppConfig.showTechno4G.value; val s5G = AppConfig.showTechno5G.value
            val f2_900 = AppConfig.f2G_900.value; val f2_1800 = AppConfig.f2G_1800.value
            val f3_900 = AppConfig.f3G_900.value; val f3_2100 = AppConfig.f3G_2100.value
            val f4_700 = AppConfig.f4G_700.value; val f4_800 = AppConfig.f4G_800.value; val f4_900 = AppConfig.f4G_900.value
            val f4_1800 = AppConfig.f4G_1800.value; val f4_2100 = AppConfig.f4G_2100.value; val f4_2600 = AppConfig.f4G_2600.value
            val f5_700 = AppConfig.f5G_700.value; val f5_2100 = AppConfig.f5G_2100.value; val f5_3500 = AppConfig.f5G_3500.value; val f5_26000 = AppConfig.f5G_26000.value

            val result = antennas.filter { antenna ->
                val op = antenna.operateur ?: ""
                if (antenna.idAnfr.startsWith("CLUSTER_")) return@filter true

                val matchOperator = (sOrange && op.contains("ORANGE", true)) || (sSfr && op.contains("SFR", true)) ||
                        (sBouygues && op.contains("BOUYGUES", true)) || (sFree && op.contains("FREE", true))

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

                // 🚨 LE CORRECTIF EST LÀ :
                // Si la base ne connait pas les fréquences de cette antenne,
                // on la cache directement, SAUF si le filtre est vierge (tout est coché par défaut)
                if (f.isBlank() && antenna.azimutsFh.isNullOrBlank()) {
                    matchTechno = (s2G && s3G && s4G && s5G && sFh)
                }

                matchOperator && matchFh && isInCityBounds && matchTechno
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

    // ✅ NOUVELLE FONCTION UPDATE MARKERS INTELLIGENTE
    fun updateMarkers(map: MapView, antennasList: List<LocalisationEntity>) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val isClusterMode = antennasList.isNotEmpty() && antennasList.first().idAnfr.startsWith("CLUSTER_")

            if (isClusterMode) {
                // ========================================================
                // 🚀 MODE MACRO : Affichage ultra-rapide des points SQL
                // ========================================================
                val clusterMarkers = antennasList.map { fakeAntenna ->
                    val count = fakeAntenna.idAnfr.removePrefix("CLUSTER_").toIntOrNull() ?: 1
                    Marker(map).apply {
                        position = GeoPoint(fakeAntenna.latitude, fakeAntenna.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                        // On extrait les 4 opérateurs qu'on a mis dans le ViewModel
                        // On extrait les 4 opérateurs qu'on a mis dans le ViewModel
                        val opsList = fakeAntenna.operateur?.split(",")?.map { it.trim() } ?: emptyList()

                        // ✅ CORRECTION : On passe l'opérateur préféré
                        val currentDefaultOp = AppConfig.defaultOperator.value
                        icon = MapUtils.createClusterIcon(context, opsList, count, currentDefaultOp)

                        setOnMarkerClickListener { clickedMarker, m ->
                            val targetPoint = org.osmdroid.util.GeoPoint(
                                clickedMarker.position.latitude,
                                clickedMarker.position.longitude
                            )
                            val targetZoom = m.zoomLevelDouble + 1.5

                            m.post {
                                m.controller.stopAnimation(false)
                                m.controller.setZoom(targetZoom)
                                m.controller.setCenter(targetPoint)
                            }
                            true
                        }
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    markersOverlay.items.clear()
                    macroOverlay.items.clear()
                    macroOverlay.items.addAll(clusterMarkers)
                    map.invalidate()
                }

            } else {
                // ========================================================
                // 🔍 MODE MICRO : Ton code actuel pour les vraies antennes
                // ========================================================
                val groupedSites = antennasList.groupBy { "${it.latitude}_${it.longitude}" }.values.take(6000)

                val newMarkers = groupedSites.map { siteAntennas ->
                    val mainAntenna = siteAntennas.first()
                    val operatorsOnSite = siteAntennas.mapNotNull { it.operateur }
                        .flatMap { it.split(Regex("[/,\\-]")) }
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }

                    AntennaMarker(map, siteAntennas, safePrimaryColor).apply {
                        position = GeoPoint(mainAntenna.latitude, mainAntenna.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        relatedObject = operatorsOnSite

                        val isZoomCloseEnough = map.zoomLevelDouble >= 13.0
                        val defaultOp = AppConfig.defaultOperator.value
                        icon = MapUtils.createAdaptiveMarker(context, siteAntennas, isZoomCloseEnough && AppConfig.showAzimuths.value, defaultOp)

                        setOnMarkerClickListener { _, _ ->
                            if (isMeasuringMode) {
                                val id = mainAntenna.idAnfr
                                if (measuredSites.containsKey(id)) {
                                    measuredSites.remove(id)
                                } else if (myCurrentLoc != null) {
                                    measuredSites[id] = mainAntenna
                                }
                                refreshMeasureLayers(map)
                            } else {
                                safeClick {
                                    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putFloat("clicked_lat", mainAntenna.latitude.toFloat())
                                        .putFloat("clicked_lon", mainAntenna.longitude.toFloat())
                                        .apply()
                                    navController.navigate("support_detail/${mainAntenna.idAnfr.toLongOrNull() ?: 0L}")
                                }
                            }
                            true
                        }
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    macroOverlay.items.clear() // On vide les faux clusters
                    markersOverlay.items.clear()
                    markersOverlay.items.addAll(newMarkers) // On réactive OSMBonusPack
                    markersOverlay.invalidate()
                    map.invalidate()
                }
            }
        }
    }

    LaunchedEffect(filteredAntennas, isMeasuringMode, safePrimaryColor, AppConfig.showAzimuths.value) {
        mapViewRef?.let { map ->
            updateMarkers(map, filteredAntennas)
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
                        val opQuery = AppConfig.defaultOperator.value.uppercase()
                        val nearestFav = currentFilteredAntennas
                            .filter { (it.operateur ?: "").uppercase().contains(opQuery) }
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
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    val prefs = ctx.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

                    controller.setCenter(GeoPoint(
                        prefs.getFloat("last_map_lat", 46.2276f).toDouble(),
                        prefs.getFloat("last_map_lon", 2.2137f).toDouble()
                    ))

                    controller.setZoom(prefs.getFloat("last_map_zoom", 6.0f).toDouble())
                    setMinZoomLevel(6.0)

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
                    locationOverlay.enableMyLocation()

                    locationOverlay.runOnFirstFix {
                        val initialLoc = locationOverlay.myLocation
                        if (initialLoc != null) {
                            myCurrentLoc = initialLoc
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
                            // Si l'utilisateur fait défiler la carte, on désactive le suivi continu
                            if (isTrackingActive) {
                                isTrackingActive = false
                                locationOverlayRef?.disableFollowLocation()
                            }
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

                                if (z >= 6.0) {
                                    showZoomMessage = false
                                    val box = boundingBox
                                    viewModel.loadAntennasInBox(z, box.latNorth, box.lonEast, box.latSouth, box.lonWest)
                                } else {
                                    showZoomMessage = true
                                }
                            }
                        }
                    })
                    mapViewRef = this
                }
            },
            update = { map ->
                (locationOverlayRef as? CustomLocationOverlay)?.let { overlay ->
                    overlay.currentCompassAzimuth = azimuth
                }

                val newSource = when (mapProvider) {
                    1 -> MapUtils.OSM_Source
                    2 -> if (ignStyle == 1) {
                        org.osmdroid.tileprovider.tilesource.XYTileSource("MapLibreDark", 1, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/dark_all/"))
                    } else {
                        org.osmdroid.tileprovider.tilesource.XYTileSource("MapLibre", 1, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/rastertiles/voyager/"))
                    }
                    3 -> org.osmdroid.tileprovider.tilesource.TileSourceFactory.OpenTopo
                    else -> if (ignStyle == 2) MapUtils.IgnSource.SATELLITE else MapUtils.IgnSource.PLAN_IGN
                }
                if (map.tileProvider.tileSource.name() != newSource.name()) { map.setTileSource(newSource) }
                map.overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)
                map.invalidate()
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
                            Toast.makeText(context, "Le site $cleanQuery $txtSiteNotInArea", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                // 3. Recherche de Ville / Adresse via internet (Nominatim)
                try {
                    val urlString = "https://nominatim.openstreetmap.org/search?q=${java.net.URLEncoder.encode(cleanQuery, "UTF-8")}&format=json&polygon_geojson=1&limit=1"
                    val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "GeoTowerApp")

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().readText()
                        val jsonArray = org.json.JSONArray(response)

                        if (jsonArray.length() > 0) {
                            val firstResult = jsonArray.getJSONObject(0)

                            val bboxArray = firstResult.getJSONArray("boundingbox")
                            val latMin = bboxArray.getDouble(0)
                            val latMax = bboxArray.getDouble(1)
                            val lonMin = bboxArray.getDouble(2)
                            val lonMax = bboxArray.getDouble(3)

                            var geoJsonString: String? = null
                            if (firstResult.has("geojson")) {
                                val geometryJson = firstResult.getJSONObject("geojson").toString()
                                geoJsonString = """
                                    {
                                      "type": "Feature",
                                      "properties": {},
                                      "geometry": $geometryJson
                                    }
                                """.trimIndent()
                            }

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                mapViewRef?.let { map ->
                                    searchBoundaryOverlay.items.clear()

                                    if (geoJsonString != null) {
                                        val kmlDocument = org.osmdroid.bonuspack.kml.KmlDocument()
                                        kmlDocument.parseGeoJSON(geoJsonString)
                                        val featureOverlay = kmlDocument.mKmlRoot.buildOverlay(map, null, null, kmlDocument)

                                        val holesList = mutableListOf<List<GeoPoint>>()
                                        val outlinesOverlay = org.osmdroid.views.overlay.FolderOverlay()

                                        fun extractHolesAndOutlines(overlay: org.osmdroid.views.overlay.Overlay) {
                                            when (overlay) {
                                                is org.osmdroid.views.overlay.Polygon -> {
                                                    holesList.add(overlay.points)
                                                    val outline = org.osmdroid.views.overlay.Polyline(map).apply {
                                                        setPoints(overlay.points)
                                                        outlinePaint.color = android.graphics.Color.RED
                                                        outlinePaint.strokeWidth = 4f
                                                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
                                                    }
                                                    outlinesOverlay.add(outline)
                                                }
                                                is org.osmdroid.views.overlay.Polyline -> {
                                                    val outline = org.osmdroid.views.overlay.Polyline(map).apply {
                                                        setPoints(overlay.points)
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
                                            latNorth = latMax,
                                            lonEast = lonMax,
                                            latSouth = latMin,
                                            lonWest = lonMin,
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

                                    val cityBounds = org.osmdroid.util.BoundingBox(latMax, lonMax, latMin, lonMin)
                                    map.zoomToBoundingBox(cityBounds, true, 100)
                                    map.invalidate()
                                }
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeoTower", "Erreur Nominatim : ${e.message}")
                }

                try {
                    val geocoder = android.location.Geocoder(context)
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
                            contentDescription = "Rechercher",
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
                useOneUi = fr.geotower.utils.AppConfig.forceOneUiTheme.value,
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
        val opColor = when {
            defaultOp.uppercase().contains("ORANGE") -> Color(0xFFFF7900)
            defaultOp.uppercase().contains("SFR") -> Color(0xFFE2001A)
            defaultOp.uppercase().contains("BOUYGUES") -> Color(0xFF00295F)
            defaultOp.uppercase().contains("FREE") -> Color(0xFF757575)
            else -> MaterialTheme.colorScheme.primary
        }

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
                    desc = "Retour",
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    safeClick {
                        if (navController.previousBackStackEntry != null) {
                            // Si on vient d'une autre page, retour classique
                            navController.popBackStack()
                        } else {
                            // Si on est sur la page de lancement (pas d'historique), on force l'accueil
                            navController.navigate("home") {
                                popUpTo(0)
                            }
                        }
                    }
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
                                viewModel.clearCityFilterAndReload(map.zoomLevelDouble, map.boundingBox.latNorth, map.boundingBox.lonEast, map.boundingBox.latSouth, map.boundingBox.lonWest)
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
                                viewModel.clearCityFilterAndReload(map.zoomLevelDouble, map.boundingBox.latNorth, map.boundingBox.lonEast, map.boundingBox.latSouth, map.boundingBox.lonWest)
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
                    onOpenSettings = { safeClick { navController.navigate("settings") } }
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
                            if (isTrackingActive) {
                                // Si déjà actif, on désactive
                                isTrackingActive = false
                                locationOverlayRef?.disableFollowLocation()
                            } else {
                                // Sinon, on active le suivi continu
                                isTrackingActive = true
                                locationOverlayRef?.enableFollowLocation()
                                locationOverlayRef?.myLocation?.let { loc ->
                                    mapViewRef?.controller?.setZoom(16.0)
                                    mapViewRef?.controller?.setCenter(loc)
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
                            contentDescription = "Localiser",
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
        ModalBottomSheet(
            onDismissRequest = { showLayerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
                // ✅ 1. ON CRÉE LA COLONNE GLOBALE QUI APPLIQUE LE PADDING À TOUT
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)
                ) {

                    // ✅ 2. LA COLONNE DES PREMIERS BOUTONS (Sans le padding)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                    }

                    // ✅ 3. L'ANIMATION EST MAINTENANT PROTÉGÉE DANS LA GRANDE COLONNE
                    AnimatedVisibility(
                        visible = mapProvider == 0 || mapProvider == 1 || mapProvider == 2,
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
    private val pulseStartTime = System.currentTimeMillis()

    // --- On prépare les objets de dessin une seule fois pour éviter les allocations dans draw() ---
    private val pt = android.graphics.Point()
    private val beamPath = android.graphics.Path()
    private val arrowHead = android.graphics.Path()
    private var isPathsInitialized = false

    private val themeFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = primaryColor }
    private val themeStrokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; color = primaryColor; strokeCap = android.graphics.Paint.Cap.ROUND }
    private val whiteFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL; color = android.graphics.Color.WHITE }
    private val pulsePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; color = primaryColor; strokeWidth = 3f }
    private val beamPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }

    override fun drawMyLocation(
        canvas: android.graphics.Canvas,
        projection: org.osmdroid.views.Projection,
        lastFix: android.location.Location
    ) {
        // On réutilise le point existant !
        projection.toPixels(org.osmdroid.util.GeoPoint(lastFix.latitude, lastFix.longitude), pt)

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

    // ✅ NOUVELLE STRUCTURE : On regroupe les couleurs par azimut !
    private class GroupedAzimuthData(
        val cos: Float,
        val sin: Float,
        val linePaint: android.graphics.Paint,
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

            precalculatedMobileAzimuths.add(GroupedAzimuthData(cos, sin, linePaint, sortedColors))
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

            precalculatedFhAzimuths.add(GroupedAzimuthData(cos, sin, dashedPaint, sortedColors))
        }
    }

    private fun getOpColorInt(name: String?): Int {
        return when {
            name?.contains("ORANGE", true) == true -> android.graphics.Color.parseColor("#FF7900")
            name?.contains("SFR", true) == true -> android.graphics.Color.parseColor("#E2001A")
            name?.contains("FREE", true) == true -> android.graphics.Color.parseColor("#757575")
            name?.contains("BOUYGUES", true) == true -> android.graphics.Color.parseColor("#00295F")
            else -> primaryColor
        }
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble
        if (zoom >= 14.0 && fr.geotower.utils.AppConfig.showAzimuths.value) {
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

            // ✅ NOUVEAU : On calcule l'écart parfait pour que les cercles se touchent (Rayon x 2 = Diamètre)
            // Astuce : tu peux mettre 1.8f au lieu de 2.0f si tu veux qu'ils se chevauchent très légèrement !
            val gapMobile = pointRadius * 2.0f
            val gapFh = fhRadius * 2.0f

            // --- DESSIN DES MOBILES ---
            precalculatedMobileAzimuths.forEach { data ->
                val startX = ptCenter.x + circleOffsetPx * data.cos
                val startY = ptCenter.y + circleOffsetPx * data.sin
                val endX = ptCenter.x + totalRadiusPx * data.cos
                val endY = ptCenter.y + totalRadiusPx * data.sin

                canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                data.dotColors.forEachIndexed { index, colorInt ->
                    val offsetMag = index * gapMobile // Utilise le gap des mobiles
                    val dotX = endX + (data.cos * offsetMag)
                    val dotY = endY + (data.sin * offsetMag)

                    canvas.drawCircle(dotX, dotY, pointRadius, getDotPaint(colorInt))
                }
            }

            // --- DESSIN DES FAISCEAUX HERTZIENS (FH) ---
            if (fr.geotower.utils.AppConfig.showTechnoFH.value) {
                precalculatedFhAzimuths.forEach { data ->
                    val startX = ptCenter.x + circleOffsetPx * data.cos
                    val startY = ptCenter.y + circleOffsetPx * data.sin
                    val endX = ptCenter.x + totalRadiusPx * data.cos
                    val endY = ptCenter.y + totalRadiusPx * data.sin

                    canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                    data.dotColors.forEachIndexed { index, colorInt ->
                        val offsetMag = index * gapFh // Utilise le gap réduit des FH
                        val dotX = endX + (data.cos * offsetMag)
                        val dotY = endY + (data.sin * offsetMag)

                        canvas.drawCircle(dotX, dotY, fhRadius, getDotPaint(colorInt))
                    }
                }
            }
        }
        super.draw(canvas, projection)
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

@Composable
fun MapDistanceIndicator(distanceMeters: Float?, label: String) {
    if (distanceMeters == null) return

    // On lit le choix de l'utilisateur (0 = km, 1 = miles)
    val isMi = fr.geotower.utils.AppConfig.distanceUnit.intValue == 1

    val displayDistance: String = if (isMi) {
        val distMiles = distanceMeters / 1609.34f
        if (distMiles < 0.1f) {
            "${(distanceMeters * 3.28084f).toInt()} ft"
        } else {
            String.format(java.util.Locale.US, "%.2f mi", distMiles)
        }
    } else {
        if (distanceMeters < 1000f) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.2f km", distanceMeters / 1000f)
        }
    }

    Surface(
        modifier = Modifier.padding(bottom = 4.dp, start = 6.dp),
        color = Color.White.copy(alpha = 0.8f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label : ", fontSize = 10.sp, color = Color.DarkGray)
            Text(text = displayDistance, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}