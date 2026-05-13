package fr.geotower.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.MapUtils
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.isNetworkAvailable
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.osmdroid.tileprovider.MapTileProviderBasic
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

private const val MINI_MAP_HS_OPERATOR_WILDCARD = "*"
private val MINI_MAP_OPERATOR_KEYS = listOf("ORANGE", "SFR", "BOUYGUES", "FREE")

@Composable
fun SharedMiniMapCard(
    modifier: Modifier = Modifier,
    centerLat: Double,
    centerLon: Double,
    mappedAntennas: List<LocalisationEntity>,
    sitesHs: List<SiteHsEntity> = emptyList(),
    blockShape: Shape,
    cardBorder: BorderStroke?,
    onMapReady: (MapView) -> Unit,
    focusOperator: String? = null,
    coneOverlay: MiniMapConeOverlayData? = null,
    initialZoom: Double = 17.5,
    onMapTap: ((Double, Double) -> Unit)? = null,
    allowGestures: Boolean = false,
    fitSelectedPointRequest: Int = 0
) {
    val context = LocalContext.current
    val currentOnMapTap by rememberUpdatedState(onMapTap)
    val currentAllowGestures by rememberUpdatedState(allowGestures)
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    val mapProvider by AppConfig.mapProvider

    // ✅ NOUVEAU : État calculé une seule fois
    var effectiveProvider by remember { mutableIntStateOf(AppConfig.mapProvider.intValue) }
    var mapFiles by remember { mutableStateOf(emptyArray<java.io.File>()) }

    LaunchedEffect(AppConfig.mapProvider.intValue) {
        effectiveProvider = AppConfig.mapProvider.intValue
    }

    LaunchedEffect(Unit) {
        val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
        val files = offlineDir.listFiles { file -> file.extension == "map" && file.length() > 0L } ?: emptyArray()
        mapFiles = files

        // Si hors-ligne ET présence de fichiers : on bascule.
        if (!isNetworkAvailable(context) && files.isNotEmpty()) {
            effectiveProvider = 4
        }
    }

    val ignStyle by AppConfig.ignStyle
    val shouldInvertColors = (mapProvider == 0 && ignStyle == 1)
    var currentZoom by remember(initialZoom) { mutableDoubleStateOf(initialZoom) }
    var lastFitSelectedPointRequest by remember { mutableIntStateOf(fitSelectedPointRequest) }

    // ✅ NOUVEAU : Récupération de la couleur du thème pour le marqueur par défaut
    val rawPrimaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val isColorTooLight = ColorUtils.calculateLuminance(rawPrimaryColor) > 0.85
    val safePrimaryColor = remember(rawPrimaryColor, isColorTooLight) {
        if (isColorTooLight) android.graphics.Color.parseColor("#2196F3") else rawPrimaryColor
    }

    Box(modifier = modifier.height(200.dp).clip(blockShape).border(cardBorder ?: BorderStroke(0.dp, Color.Transparent), blockShape)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setMultiTouchControls(allowGestures)
                    setOnTouchListener { view, event ->
                        if (!currentAllowGestures) return@setOnTouchListener true
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_POINTER_DOWN,
                            MotionEvent.ACTION_MOVE -> view.requestAncestorTouchInterception(false)
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> view.requestAncestorTouchInterception(true)
                        }
                        false
                    }
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(initialZoom)
                    controller.setCenter(GeoPoint(centerLat, centerLon))
                    setMinZoomLevel(14.0)

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean = false
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            currentZoom = zoomLevelDouble
                            return true
                        }
                    })

                    updateMiniMapConeOverlay(coneOverlay, safePrimaryColor)
                    overlays.add(MiniMapTapOverlay { currentOnMapTap })

                    // ✅ MODIFICATION : On utilise le nouveau marqueur personnalisé avec les azimuts
                    val marker = MiniMapAntennaMarker(this, mappedAntennas, safePrimaryColor, focusOperator).apply { // 👈 AJOUTEZ focusOperator
                        position = GeoPoint(centerLat, centerLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    overlays.add(marker)
                    mapRef = this
                    onMapReady(this)
                }
            },
            update = { map ->
                map.setMultiTouchControls(allowGestures)
                val selectedPoint = coneOverlay?.selectedPoint
                if (fitSelectedPointRequest != lastFitSelectedPointRequest && selectedPoint != null) {
                    val viewport = miniMapViewportForSelection(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        selectedLat = selectedPoint.latitude,
                        selectedLon = selectedPoint.longitude,
                        coneRadiusMeters = coneOverlay.radiusMeters,
                        fallbackZoom = initialZoom
                    )
                    map.controller.setZoom(viewport.zoom)
                    map.controller.setCenter(GeoPoint(viewport.centerLat, viewport.centerLon))
                    currentZoom = viewport.zoom
                    lastFitSelectedPointRequest = fitSelectedPointRequest
                } else if (!allowGestures) {
                    map.controller.setCenter(GeoPoint(centerLat, centerLon))
                }

                // 🗺️ LOGIQUE HORS-LIGNE
                if (effectiveProvider == 4) {
                    if (mapFiles.isNotEmpty()) {
                        if (map.tileProvider !is MapsForgeTileProvider) {
                            runCatching {
                                val forgeSource = MapsForgeTileSource.createFromFiles(
                                    mapFiles,
                                    InternalRenderTheme.OSMARENDER,
                                    "osmarender"
                                )
                                val forgeProvider = MapsForgeTileProvider(
                                    org.osmdroid.tileprovider.util.SimpleRegisterReceiver(context),
                                    forgeSource,
                                    null
                                )
                                map.tileProvider = forgeProvider
                            }.onFailure {
                                mapFiles = emptyArray()
                                effectiveProvider = 1
                                AppConfig.mapProvider.value = 1
                                if (map.tileProvider is MapsForgeTileProvider) {
                                    map.tileProvider = MapTileProviderBasic(context)
                                }
                                runCatching { map.setTileSource(MapUtils.OSM_Source) }
                            }
                        }
                    } else {
                        AppConfig.mapProvider.value = 1
                    }
                } else {
                    // 🌐 LOGIQUE EN LIGNE
                    if (map.tileProvider is MapsForgeTileProvider) {
                        map.tileProvider = MapTileProviderBasic(context)
                    }

                    // ⚠️ ATTENTION : on utilise "effectiveProvider" ici !
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
                    }
                }

                map.overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)
                map.updateMiniMapConeOverlay(coneOverlay, safePrimaryColor)

                // ✅ CORRECTION ICI : On cherche notre marqueur spécifique (MiniMapAntennaMarker)
                val marker = map.overlays.filterIsInstance<MiniMapAntennaMarker>().firstOrNull()

                if (marker != null) {
                    marker.siteAntennas = mappedAntennas
                    marker.focusOperator = focusOperator

                    // 1. On génère l'icône de base
                    val baseIcon = MapUtils.createAdaptiveMarker(
                        context,
                        mappedAntennas,
                        currentZoom >= 14.0 && AppConfig.showAzimuths.value,
                        focusOperator ?: AppConfig.defaultOperator.value
                    )

                    // 🚨 LE MÊME CODE QUE MAPSCREEN : LOGIQUE DE FUSION SITES HS
                    val hsOperatorMap = buildMiniMapHsOperatorMap(sitesHs)
                    val isHs = mappedAntennas.any { antenna ->
                        hasMiniMapHsOperator(antenna, hsOperatorMap)
                    }

                    val finalIcon = if (isHs) {
                        val badgeIcon = createHsBadge(context)
                        val combinedBitmap = android.graphics.Bitmap.createBitmap(
                            baseIcon.intrinsicWidth, baseIcon.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(combinedBitmap)
                        baseIcon.setBounds(0, 0, canvas.width, canvas.height)
                        baseIcon.draw(canvas)
                        val offsetX = (canvas.width - badgeIcon.intrinsicWidth) / 2
                        val offsetY = (canvas.height - badgeIcon.intrinsicHeight) / 2
                        badgeIcon.setBounds(offsetX, offsetY, offsetX + badgeIcon.intrinsicWidth, offsetY + badgeIcon.intrinsicHeight)
                        badgeIcon.draw(canvas)
                        android.graphics.drawable.BitmapDrawable(context.resources, combinedBitmap)
                    } else {
                        baseIcon
                    }

                    // On applique le scale sur l'icône finale
                    val scale = ((currentZoom - 11.0) / 6.5).coerceIn(0.5, 1.0).toFloat()
                    if (scale < 1f) {
                        val originalBitmap = finalIcon.bitmap
                        val scaledWidth = (originalBitmap.width * scale).toInt()
                        val scaledHeight = (originalBitmap.height * scale).toInt()
                        if (scaledWidth > 0 && scaledHeight > 0) {
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
                            marker.icon = android.graphics.drawable.BitmapDrawable(context.resources, scaledBitmap)
                        } else { marker.icon = finalIcon }
                    } else {
                        marker.icon = finalIcon
                    }
                }
                map.invalidate()
            }
        )
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { mapRef?.controller?.zoomIn() }, shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shadowElevation = 4.dp, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.padding(6.dp)) }
            Surface(onClick = { mapRef?.controller?.zoomOut() }, shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shadowElevation = 4.dp, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Remove, null, modifier = Modifier.padding(6.dp)) }
        }
    }
}

private fun android.view.View.requestAncestorTouchInterception(allowInterception: Boolean) {
    var currentParent = parent
    while (currentParent != null) {
        currentParent.requestDisallowInterceptTouchEvent(!allowInterception)
        currentParent = currentParent.parent
    }
}

private data class MiniMapViewport(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Double
)

private fun miniMapViewportForSelection(
    centerLat: Double,
    centerLon: Double,
    selectedLat: Double,
    selectedLon: Double,
    coneRadiusMeters: Double,
    fallbackZoom: Double
): MiniMapViewport {
    val distanceMeters = roughDistanceMeters(centerLat, centerLon, selectedLat, selectedLon)
    val usefulRadius = max(coneRadiusMeters, distanceMeters / 2.0)
    val zoom = miniMapZoomForRadius(usefulRadius).coerceAtMost(fallbackZoom)
    return MiniMapViewport(
        centerLat = (centerLat + selectedLat) / 2.0,
        centerLon = (centerLon + selectedLon) / 2.0,
        zoom = zoom
    )
}

private fun roughDistanceMeters(
    firstLat: Double,
    firstLon: Double,
    secondLat: Double,
    secondLon: Double
): Double {
    val middleLat = Math.toRadians((firstLat + secondLat) / 2.0)
    val latMeters = (secondLat - firstLat) * 111_320.0
    val lonMeters = (secondLon - firstLon) * 111_320.0 * cos(middleLat)
    return sqrt(latMeters * latMeters + lonMeters * lonMeters)
}

private fun miniMapZoomForRadius(radiusMeters: Double): Double {
    return when {
        radiusMeters >= 40_000.0 -> 9.0
        radiusMeters >= 20_000.0 -> 10.0
        radiusMeters >= 10_000.0 -> 11.0
        radiusMeters >= 5_000.0 -> 12.0
        radiusMeters >= 2_500.0 -> 13.0
        radiusMeters >= 1_200.0 -> 14.0
        radiusMeters >= 650.0 -> 15.0
        radiusMeters >= 300.0 -> 16.0
        else -> 17.0
    }
}

private fun normalizedMiniMapAnfrId(value: String): String {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.toString() ?: trimmed
}

private fun extractMiniMapOperatorKeys(value: String?): List<String> {
    val upper = value?.uppercase() ?: return emptyList()
    return MINI_MAP_OPERATOR_KEYS.filter { upper.contains(it) }
}

private fun buildMiniMapHsOperatorMap(sitesHs: List<SiteHsEntity>): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()

    sitesHs.forEach { hs ->
        val id = normalizedMiniMapAnfrId(hs.idAnfr)
        if (id.isBlank()) return@forEach

        val parsedOperators = extractMiniMapOperatorKeys(hs.operateur)
        val operators = if (parsedOperators.isEmpty()) {
            listOf(MINI_MAP_HS_OPERATOR_WILDCARD)
        } else {
            parsedOperators
        }
        result.getOrPut(id) { mutableSetOf() }.addAll(operators)
    }

    return result
}

private fun hasMiniMapHsOperator(
    antenna: LocalisationEntity,
    hsOperatorMap: Map<String, Set<String>>
): Boolean {
    val hsOperators = hsOperatorMap[normalizedMiniMapAnfrId(antenna.idAnfr)] ?: return false
    return extractMiniMapOperatorKeys(antenna.operateur).any { operatorKey ->
        MINI_MAP_HS_OPERATOR_WILDCARD in hsOperators || operatorKey in hsOperators
    }
}

private class MiniMapTapOverlay(
    private val onTapProvider: () -> ((Double, Double) -> Unit)?
) : Overlay() {
    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        val tap = onTapProvider() ?: return false
        val safeEvent = e ?: return false
        val safeMapView = mapView ?: return false
        val point = safeMapView.projection.fromPixels(safeEvent.x.toInt(), safeEvent.y.toInt())
        tap(point.latitude, point.longitude)
        return true
    }
}

// =====================================================================
// MARQUEUR MINI-CARTE (DESSINE LES AZIMUTS + L'ICÔNE)
// =====================================================================
data class MiniMapConeOverlayData(
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double,
    val strongPoints: List<MiniMapStrongPoint>,
    val selectedPoint: MiniMapStrongPoint? = null
)

data class MiniMapStrongPoint(
    val latitude: Double,
    val longitude: Double
)

private fun MapView.updateMiniMapConeOverlay(data: MiniMapConeOverlayData?, primaryColor: Int) {
    val current = overlays.filterIsInstance<MiniMapConeOverlay>().firstOrNull()
    if (data == null) {
        if (current != null) overlays.remove(current)
        return
    }

    if (current == null) {
        overlays.add(0, MiniMapConeOverlay(data, primaryColor))
    } else {
        current.data = data
        current.primaryColor = primaryColor
        current.refreshPaints()
    }
}

private class MiniMapConeOverlay(
    var data: MiniMapConeOverlayData,
    var primaryColor: Int
) : Overlay() {
    private val centerPoint = Point()
    private val radiusPoint = Point()
    private val strongPoint = Point()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        refreshPaints()
    }

    fun refreshPaints() {
        fillPaint.apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(
                32,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor)
            )
        }
        strokePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.argb(
                210,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor)
            )
        }
        pointFillPaint.apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(
                235,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor)
            )
        }
        pointStrokePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.WHITE
        }
        selectedFillPaint.apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(
                245,
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor)
            )
        }
        selectedStrokePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = android.graphics.Color.WHITE
        }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (data.radiusMeters <= 0.0) return

        val center = GeoPoint(data.centerLat, data.centerLon)
        projection.toPixels(center, centerPoint)
        val radiusLonOffset = data.radiusMeters / metersPerDegreeLongitude(data.centerLat)
        projection.toPixels(GeoPoint(data.centerLat, data.centerLon + radiusLonOffset), radiusPoint)
        val radiusPx = abs(radiusPoint.x - centerPoint.x).toFloat().coerceAtLeast(8f)

        canvas.drawCircle(centerPoint.x.toFloat(), centerPoint.y.toFloat(), radiusPx, fillPaint)
        canvas.drawCircle(centerPoint.x.toFloat(), centerPoint.y.toFloat(), radiusPx, strokePaint)

        data.strongPoints.forEach { point ->
            projection.toPixels(GeoPoint(point.latitude, point.longitude), strongPoint)
            canvas.drawCircle(strongPoint.x.toFloat(), strongPoint.y.toFloat(), 9f, pointStrokePaint)
            canvas.drawCircle(strongPoint.x.toFloat(), strongPoint.y.toFloat(), 6f, pointFillPaint)
        }

        data.selectedPoint?.let { point ->
            projection.toPixels(GeoPoint(point.latitude, point.longitude), strongPoint)
            canvas.drawCircle(strongPoint.x.toFloat(), strongPoint.y.toFloat(), 14f, selectedStrokePaint)
            canvas.drawCircle(strongPoint.x.toFloat(), strongPoint.y.toFloat(), 9f, selectedFillPaint)
        }
    }

    private fun metersPerDegreeLongitude(latitude: Double): Double {
        return (111_320.0 * cos(Math.toRadians(latitude))).coerceAtLeast(1.0)
    }
}

class MiniMapAntennaMarker(
    private val mapView: MapView,
    initialSiteAntennas: List<LocalisationEntity>,
    private val primaryColor: Int,
    initialFocusOperator: String? = null
) : Marker(mapView) {

    private val density = mapView.context.resources.displayMetrics.density
    private val ptCenter = android.graphics.Point()

    // Cache pour les pinceaux de couleur
    private val dotPaints = mutableMapOf<Int, android.graphics.Paint>()
    private fun getDotPaint(colorInt: Int): android.graphics.Paint {
        return dotPaints.getOrPut(colorInt) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = colorInt
            }
        }
    }

    private class GroupedAzimuthData(
        val cos: Float,
        val sin: Float,
        val linePaint: android.graphics.Paint,
        val dotColors: List<Int>
    )

    private val precalculatedMobileAzimuths = mutableListOf<GroupedAzimuthData>()
    private val precalculatedFhAzimuths = mutableListOf<GroupedAzimuthData>()

    // ✅ ASTUCE : Quand Compose met à jour ces variables, on recalcule les traits
    var siteAntennas: List<LocalisationEntity> = initialSiteAntennas
        set(value) {
            field = value
            recalculateAzimuths()
        }

    var focusOperator: String? = initialFocusOperator
        set(value) {
            field = value
            recalculateAzimuths()
        }

    init {
        recalculateAzimuths()
    }

    private fun recalculateAzimuths() {
        precalculatedMobileAzimuths.clear()
        precalculatedFhAzimuths.clear()

        val angleToColorsMobile = mutableMapOf<Float, MutableSet<Int>>()
        val angleToColorsFh = mutableMapOf<Float, MutableSet<Int>>()

        siteAntennas.forEach { antenna ->
            val opColorInt = getOpColorInt(antenna.operateur)

            if (!antenna.azimuts.isNullOrBlank()) {
                antenna.azimuts.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToColorsMobile.getOrPut(az) { mutableSetOf() }.add(opColorInt)
                }
            }

            if (AppConfig.showTechnoFH.value && !antenna.azimutsFh.isNullOrBlank()) {
                antenna.azimutsFh.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach { az ->
                    angleToColorsFh.getOrPut(az) { mutableSetOf() }.add(opColorInt)
                }
            }
        }

        // Sur la mini-carte, l'opérateur prioritaire est celui qu'on consulte, sinon le favori
        val defOpName = focusOperator ?: AppConfig.defaultOperator.value
        val defOpColorInt = getOpColorInt(defOpName)

        angleToColorsMobile.forEach { (az, colorsSet) ->
            val rad = Math.toRadians(az - 90.0)
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()

            // L'opérateur prioritaire donne sa couleur à la ligne
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
        return OperatorColors.colorInt(name, fallback = primaryColor)
    }

    override fun draw(canvas: android.graphics.Canvas, projection: org.osmdroid.views.Projection) {
        val zoom = mapView.zoomLevelDouble

        if (zoom >= 13.0 && AppConfig.showAzimuths.value) {
            projection.toPixels(mPosition, ptCenter)

            val scale = ((zoom - 11.0) / 6.5).coerceIn(0.5, 1.0).toFloat()

            val beamLengthPx = when {
                zoom >= 17.0 -> 50f * density
                zoom >= 15.0 -> 40f * density
                else -> 30f * density
            }

            val pointRadius = 3.5f * density
            val fhRadius = pointRadius * 0.7f

            val baseOffset = 18f * density
            val circleOffsetPx = baseOffset * scale
            val totalRadiusPx = circleOffsetPx + beamLengthPx

            // ✅ NOUVEAU : Gap parfait pour que les points se collent
            val gapMobile = pointRadius * 2.0f
            val gapFh = fhRadius * 2.0f

            // --- DESSIN DES MOBILES ---
            precalculatedMobileAzimuths.forEach { data ->
                val startX = ptCenter.x + circleOffsetPx * data.cos
                val startY = ptCenter.y + circleOffsetPx * data.sin
                val endX = ptCenter.x + totalRadiusPx * data.cos
                val endY = ptCenter.y + totalRadiusPx * data.sin

                canvas.drawLine(startX, startY, endX, endY, data.linePaint)

                // Alignement des points dans le prolongement
                data.dotColors.forEachIndexed { index, colorInt ->
                    val offsetMag = index * gapMobile
                    val dotX = endX + (data.cos * offsetMag)
                    val dotY = endY + (data.sin * offsetMag)

                    canvas.drawCircle(dotX, dotY, pointRadius, getDotPaint(colorInt))
                }
            }

            // --- DESSIN DES FAISCEAUX HERTZIENS ---
            if (AppConfig.showTechnoFH.value) {
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
}

// 🚨 LE MÊME CODE QUE MAPSCREEN : DESSINE LE POINT D'EXCLAMATION
fun createHsBadge(context: Context): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F5F5F5")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, maskPaint)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E53935")
        textSize = 24f * density
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText("!", size / 2f, size / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
