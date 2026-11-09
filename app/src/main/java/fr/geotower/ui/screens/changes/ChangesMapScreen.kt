package fr.geotower.ui.screens.changes

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import fr.geotower.data.api.RetrofitClient
import fr.geotower.data.models.ChangeMapPoint
import fr.geotower.data.models.ChangeType
import fr.geotower.data.models.ChangesWeekItem
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.screens.map.LocationMarkerPainter
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.MapProviderRules
import fr.geotower.utils.MapUtils
import fr.geotower.utils.PersistentMapTileProvider
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.isNetworkAvailable
import fr.geotower.utils.location.FusedMyLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log2
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Locale

const val CHANGES_MAP_ROUTE = "changes_map"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesMapScreen(
    navController: NavController,
    onOpenSupportDetail: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val bgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

    var weeks by remember { mutableStateOf<List<ChangesWeekItem>>(emptyList()) }
    var selectedWeek by remember { mutableStateOf<ChangesWeekItem?>(null) }
    var allPoints by remember { mutableStateOf<List<ChangeMapPoint>>(emptyList()) }
    var selectedPoint by remember { mutableStateOf<ChangeMapPoint?>(null) }

    // Mémorisation de la caméra et du support sélectionné pour restaurer la vue au retour de la fiche
    var savedLat by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var savedLon by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var savedZoom by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var savedSelectedSupportId by rememberSaveable { mutableStateOf<String?>(null) }

    // Restauration du point sélectionné au retour sur la carte quand les points sont prêts
    LaunchedEffect(allPoints, savedSelectedSupportId) {
        val targetId = savedSelectedSupportId
        if (selectedPoint == null && targetId != null && allPoints.isNotEmpty()) {
            val found = allPoints.firstOrNull {
                (it.supportId ?: it.changes.firstOrNull()?.stationId) == targetId
            }
            if (found != null) {
                selectedPoint = found
            }
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWeekMenu by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(ChangesFilterState()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Filtrage dynamique des points de changement selon les critères sélectionnés
    val filteredPoints = remember(allPoints, filterState) {
        allPoints.filter { ChangesFilterHelper.matches(it, filterState) }
    }

    // Référence vers MapView
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<ChangesLocationOverlay?>(null) }
    val folderOverlay = remember { FolderOverlay() }

    val mapProvider by AppConfig.mapProvider
    val ignStyle by AppConfig.ignStyle
    var effectiveProvider by remember { mutableIntStateOf(MapProviderRules.sanitize(AppConfig.mapProvider.intValue)) }
    var mapFiles by remember { mutableStateOf(emptyArray<java.io.File>()) }

    LaunchedEffect(AppConfig.mapProvider.intValue) {
        effectiveProvider = MapProviderRules.sanitize(AppConfig.mapProvider.intValue)
    }

    LaunchedEffect(Unit) {
        val offlineDir = java.io.File(context.getExternalFilesDir(null), "maps")
        val files = offlineDir.listFiles { file -> file.extension == "map" && file.length() > 0L } ?: emptyArray()
        mapFiles = files

        if (!isNetworkAvailable(context) && files.isNotEmpty()) {
            effectiveProvider = 4
        }
    }

    val shouldInvertColors = ((mapProvider == 0 || mapProvider == 1) && ignStyle == 1)
    val lastTilesColorFilterMap = remember { arrayOfNulls<MapView>(1) }
    val lastTilesColorFilterInverted = remember { arrayOfNulls<Boolean>(1) }

    fun getTargetTileSource(provider: Int, style: Int): ITileSource {
        return when (provider) {
            1 -> if (style == 2) MapUtils.EsriSource.SATELLITE else MapUtils.OSM_Source
            3 -> TileSourceFactory.OpenTopo
            else -> if (style == 2) MapUtils.IgnSource.SATELLITE else MapUtils.IgnSource.PLAN_IGN
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationOverlayRef?.disableMyLocation()
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    // Chargement initial des semaines disponibles
    fun loadWeeks() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.changesApi.getAvailableWeeks()
                }
                if (response.isSuccessful && response.body() != null) {
                    val loadedWeeks = response.body()!!.weeks
                    weeks = loadedWeeks
                    if (loadedWeeks.isNotEmpty()) {
                        selectedWeek = loadedWeeks.first()
                    } else {
                        errorMessage = "Aucune semaine d'historique disponible pour le moment."
                    }
                } else {
                    errorMessage = "Impossible de récupérer les semaines (erreur ${response.code()})"
                }
            } catch (e: Exception) {
                AppLogger.w("ChangesMap", "Erreur chargement semaines", e)
                errorMessage = "Erreur de connexion au serveur : ${e.localizedMessage ?: "serveur injoignable"}"
            } finally {
                isLoading = false
            }
        }
    }

    // Chargement des points de la semaine sélectionnée
    LaunchedEffect(selectedWeek?.date, refreshTrigger) {
        val weekDate = selectedWeek?.date ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.changesApi.getMapChanges(weekDate)
            }
            if (response.isSuccessful && response.body() != null) {
                allPoints = response.body()!!.points
            } else {
                errorMessage = "Impossible de charger la carte de cette semaine (code ${response.code()})"
            }
        } catch (e: Exception) {
            AppLogger.w("ChangesMap", "Erreur chargement points", e)
            errorMessage = "Erreur lors du téléchargement des points : ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadWeeks()
    }

    val effectiveZoom = if (!savedZoom.isNaN()) savedZoom else 5.0
    val clusterZoomBucket = (effectiveZoom * 2).toInt()

    // Mise à jour des marqueurs et du clustering sur la carte
    LaunchedEffect(filteredPoints, clusterZoomBucket, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect

        withContext(Dispatchers.Default) {
            val clusteredItems = clusterChangePoints(filteredPoints, effectiveZoom)
            val density = context.resources.displayMetrics.density
            val defaultOp = AppConfig.defaultOperator.value
            val isSatellite = ignStyle == 2
            val clusterStrength = AppConfig.mapClusterStrength.intValue

            val newMarkers = clusteredItems.map { item ->
                val isCluster = item.points.size > 1
                object : Marker(map) {
                    override fun hitTest(event: android.view.MotionEvent, mapView: MapView): Boolean {
                        val pj = mapView.projection ?: return false
                        val screenCoords = android.graphics.Point()
                        pj.toPixels(position, screenCoords)

                        val dx = event.x - screenCoords.x
                        val dy = event.y - screenCoords.y

                        val clickRadius = (if (isCluster) 26f else 22f) * density
                        return (dx * dx + dy * dy) <= (clickRadius * clickRadius)
                    }
                }.apply {
                    position = GeoPoint(item.latitude, item.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                    if (isCluster) {
                        icon = MapUtils.createClusterIcon(
                            context = context,
                            operators = item.operators,
                            count = item.points.size,
                            defaultOp = defaultOp,
                            satelliteContrast = isSatellite,
                            clusterStrength = clusterStrength
                        )
                        setOnMarkerClickListener { _, m ->
                            val targetPoint = GeoPoint(item.latitude, item.longitude)
                            val targetZoom = (m.zoomLevelDouble + 2.0).coerceAtMost(19.0)
                            m.post {
                                m.controller.stopAnimation(false)
                                m.controller.setZoom(targetZoom)
                                m.controller.setCenter(targetPoint)
                            }
                            true
                        }
                    } else {
                        val pt = item.points.first()
                        icon = ChangesMarkerPainter.createMarker(
                            context = context,
                            operators = pt.operators(),
                            changeType = pt.primaryChangeType(),
                            satelliteContrast = isSatellite
                        )
                        setOnMarkerClickListener { _, _ ->
                            selectedPoint = pt
                            savedLat = pt.latitude
                            savedLon = pt.longitude
                            savedSelectedSupportId = pt.supportId ?: pt.changes.firstOrNull()?.stationId
                            map.controller.animateTo(GeoPoint(pt.latitude, pt.longitude))
                            true
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                folderOverlay.items.clear()
                newMarkers.forEach { folderOverlay.add(it) }
                map.invalidate()
            }
        }
    }

    val mapControlButtonDiameter = sizing.component(52.dp)

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Vue Carte OSMDroid pleine page
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val initialTileSource = getTargetTileSource(effectiveProvider, ignStyle)
                    val initialProvider = if (effectiveProvider != 4) {
                        PersistentMapTileProvider(ctx, initialTileSource)
                    } else null

                    (if (initialProvider != null) MapView(ctx, initialProvider) else MapView(ctx)).apply {
                        if (effectiveProvider != 4) {
                            setTileSource(initialTileSource)
                        } else if (mapFiles.isNotEmpty()) {
                            runCatching {
                                val forgeProvider = MapsForgeTileProvider(
                                    SimpleRegisterReceiver(ctx),
                                    MapsForgeTileSource.createFromFiles(mapFiles),
                                    null
                                )
                                tileProvider = forgeProvider
                            }
                        }
                        overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        applyWorldMapBounds()
                        addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val newWidth = right - left
                            val oldWidth = oldRight - oldLeft
                            val newHeight = bottom - top
                            val oldHeight = oldBottom - oldTop
                            if (newWidth != oldWidth || newHeight != oldHeight) {
                                val map = view as? MapView
                                map?.applyWorldMapBounds()
                            }
                        }

                        // Centrage : position sauvegardée prioritaire (retour fiche), sinon vue d'ensemble France
                        val initialLat = if (!savedLat.isNaN()) savedLat else 46.5
                        val initialLon = if (!savedLon.isNaN()) savedLon else 2.4
                        val initialZoom = if (!savedZoom.isNaN()) savedZoom else 5.0

                        controller.setZoom(initialZoom)
                        controller.setCenter(GeoPoint(initialLat, initialLon))

                        addMapListener(object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                mapCenter?.let {
                                    savedLat = it.latitude
                                    savedLon = it.longitude
                                }
                                return false
                            }
                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                savedZoom = event?.zoomLevel ?: zoomLevelDouble
                                return false
                            }
                        })

                        val locOverlay = ChangesLocationOverlay(FusedMyLocationProvider(ctx), this, primaryColorArgb).apply {
                            enableMyLocation()
                        }
                        overlays.add(locOverlay)
                        locationOverlayRef = locOverlay

                        overlays.add(folderOverlay)
                        mapViewRef = this
                    }
                },
                update = { view ->
                    mapViewRef = view
                    var shouldInvalidateMap = false
                    if (effectiveProvider == 4) {
                        if (mapFiles.isNotEmpty()) {
                            if (view.tileProvider !is MapsForgeTileProvider) {
                                runCatching {
                                    val forgeProvider = MapsForgeTileProvider(
                                        SimpleRegisterReceiver(context),
                                        MapsForgeTileSource.createFromFiles(mapFiles),
                                        null
                                    )
                                    view.tileProvider = forgeProvider
                                    shouldInvalidateMap = true
                                }.onFailure {
                                    mapFiles = emptyArray()
                                    effectiveProvider = 1
                                    AppConfig.mapProvider.value = 1
                                    if (view.tileProvider is MapsForgeTileProvider) {
                                        runCatching { (view.tileProvider as? MapsForgeTileProvider)?.detach() }
                                        val newSource = if (ignStyle == 2) MapUtils.EsriSource.SATELLITE else MapUtils.OSM_Source
                                        view.tileProvider = PersistentMapTileProvider(context, newSource)
                                        view.setTileSource(newSource)
                                        shouldInvalidateMap = true
                                    }
                                }
                            }
                        } else {
                            effectiveProvider = 1
                            AppConfig.mapProvider.value = 1
                            if (view.tileProvider is MapsForgeTileProvider) {
                                runCatching { (view.tileProvider as? MapsForgeTileProvider)?.detach() }
                                val newSource = if (ignStyle == 2) MapUtils.EsriSource.SATELLITE else MapUtils.OSM_Source
                                view.tileProvider = PersistentMapTileProvider(context, newSource)
                                view.setTileSource(newSource)
                                shouldInvalidateMap = true
                            }
                        }
                    } else {
                        val newSource = getTargetTileSource(effectiveProvider, ignStyle)
                        if (view.tileProvider is MapsForgeTileProvider) {
                            runCatching { (view.tileProvider as? MapsForgeTileProvider)?.detach() }
                            view.tileProvider = PersistentMapTileProvider(context, newSource)
                            shouldInvalidateMap = true
                        }

                        if (view.tileProvider.tileSource.name() != newSource.name()) {
                            view.setTileSource(newSource)
                            shouldInvalidateMap = true
                        }
                    }

                    if (lastTilesColorFilterMap[0] !== view || lastTilesColorFilterInverted[0] != shouldInvertColors) {
                        view.overlayManager.tilesOverlay.setColorFilter(if (shouldInvertColors) MapUtils.getInvertFilter() else null)
                        lastTilesColorFilterMap[0] = view
                        lastTilesColorFilterInverted[0] = shouldInvertColors
                        shouldInvalidateMap = true
                    }

                    if (shouldInvalidateMap) {
                        view.invalidate()
                    }
                }
            )

            // Barre du haut flottante à 2 rangées :
            // Rangée 1 : [Retour] --- [Changements / Semaine du ...] --- [Actualiser]
            // Rangée 2 : [Filtres] --- [Nb de changements] --- [Calendrier / Semaines]
            val elementHeight = sizing.component(44.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(4.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
            ) {
                // Ligne 1 : [Retour] --- [Changements / Semaine du ...] --- [Actualiser]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallFloatingCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        desc = "Retour",
                        size = 44.dp,
                        onClick = { navController.popBackStack() }
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = sizing.spacing(8.dp))
                            .height(elementHeight),
                        shape = RoundedCornerShape(elementHeight / 2f),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = AppConfig.floatingUiAlpha),
                        shadowElevation = AppConfig.floatingUiShadowElevation(4.dp),
                        border = BorderStroke(1.dp, AppConfig.floatingUiBorderColor())
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = sizing.spacing(8.dp))
                        ) {
                            Text(
                                text = "Changements",
                                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            selectedWeek?.let { week ->
                                Text(
                                    text = "Semaine du ${formatDisplayDate(week.date)}",
                                    style = sizing.textStyle(MaterialTheme.typography.labelSmall),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    SmallFloatingCircleButton(
                        icon = Icons.Default.Refresh,
                        desc = "Actualiser",
                        size = 44.dp,
                        onClick = {
                            refreshTrigger++
                            loadWeeks()
                        }
                    )
                }

                // Ligne 2 : [Filtres] --- [Nb de changements] --- [Semaine]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallFloatingCircleButton(
                        icon = Icons.Default.Tune,
                        desc = "Filtres",
                        size = 44.dp,
                        isActive = !filterState.isDefault,
                        badgeCount = if (!filterState.isDefault) filterState.activeCount else null,
                        onClick = { showFilterSheet = true }
                    )

                    val isFiltered = !filterState.isDefault
                    Surface(
                        onClick = {
                            if (isFiltered) {
                                filterState = ChangesFilterState()
                            } else {
                                showFilterSheet = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = sizing.spacing(8.dp))
                            .height(elementHeight),
                        shape = RoundedCornerShape(elementHeight / 2f),
                        color = if (isFiltered) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = AppConfig.floatingUiAlpha)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = AppConfig.floatingUiAlpha)
                        },
                        shadowElevation = AppConfig.floatingUiShadowElevation(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isFiltered) MaterialTheme.colorScheme.primary else AppConfig.floatingUiBorderColor()
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = sizing.spacing(10.dp)),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFiltered) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Réinitialiser",
                                    modifier = Modifier.size(sizing.component(14.dp)),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(sizing.spacing(4.dp)))
                            }
                            val countText = if (!isFiltered) {
                                val tot = allPoints.size
                                if (tot > 1) "$tot changements" else "$tot changement"
                            } else {
                                val f = filteredPoints.size
                                val tot = allPoints.size
                                "$f / $tot changements"
                            }
                            Text(
                                text = countText,
                                style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFiltered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Box {
                        SmallFloatingCircleButton(
                            icon = Icons.Default.CalendarMonth,
                            desc = "Choisir la semaine",
                            size = 44.dp,
                            onClick = { showWeekMenu = true }
                        )
                        DropdownMenu(
                            expanded = showWeekMenu,
                            onDismissRequest = { showWeekMenu = false }
                        ) {
                            weeks.forEach { week ->
                                val isSelected = week.date == selectedWeek?.date
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "Semaine du ${formatDisplayDate(week.date)}",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            val ptsCount = week.pointsCount
                                            val ptsCountLabel = if (ptsCount > 1) "$ptsCount supports concernés" else "$ptsCount support concerné"
                                            Text(
                                                text = ptsCountLabel,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedWeek = week
                                        showWeekMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Boutons flottants de contrôle de la carte (Zoom en HAUT, Localisation en BAS) identiques à la carte principale
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = sizing.spacing(16.dp), bottom = sizing.spacing(24.dp)),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
                // 1. Segment de zoom (+ / -) identique à la carte principale (en HAUT)
                val zoomControlShape = RoundedCornerShape(mapControlButtonDiameter / 2f)
                Surface(
                    modifier = Modifier
                        .width(mapControlButtonDiameter)
                        .clip(zoomControlShape),
                    shape = zoomControlShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = AppConfig.floatingUiAlpha),
                    shadowElevation = AppConfig.floatingUiShadowElevation(6.dp),
                    border = BorderStroke(1.dp, AppConfig.floatingUiBorderColor())
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(zoomControlShape)
                    ) {
                        Surface(
                            onClick = { mapViewRef?.controller?.zoomIn() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(mapControlButtonDiameter)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = mapControlButtonDiameter / 2f,
                                        topEnd = mapControlButtonDiameter / 2f,
                                        bottomStart = 10.dp,
                                        bottomEnd = 10.dp
                                    )
                                ),
                            shape = RoundedCornerShape(
                                topStart = mapControlButtonDiameter / 2f,
                                topEnd = mapControlButtonDiameter / 2f,
                                bottomStart = 10.dp,
                                bottomEnd = 10.dp
                            ),
                            color = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoomer",
                                    modifier = Modifier.size(sizing.component(26.dp))
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.width(sizing.component(32.dp)),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                        Surface(
                            onClick = { mapViewRef?.controller?.zoomOut() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(mapControlButtonDiameter)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 10.dp,
                                        topEnd = 10.dp,
                                        bottomStart = mapControlButtonDiameter / 2f,
                                        bottomEnd = mapControlButtonDiameter / 2f
                                    )
                                ),
                            shape = RoundedCornerShape(
                                topStart = 10.dp,
                                topEnd = 10.dp,
                                bottomStart = mapControlButtonDiameter / 2f,
                                bottomEnd = mapControlButtonDiameter / 2f
                            ),
                            color = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Dézoomer",
                                    modifier = Modifier.size(sizing.component(26.dp))
                                )
                            }
                        }
                    }
                }

                // 2. Bouton de localisation identique à la carte normale (en BAS, fond primaryContainer)
                Surface(
                    onClick = {
                        mapViewRef?.let { map ->
                            val myLoc = locationOverlayRef?.myLocation
                            if (myLoc != null) {
                                map.controller.animateTo(myLoc)
                                map.controller.setZoom(14.0)
                            } else {
                                val prefs = context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE)
                                val lat = prefs.getFloat("last_map_lat", 46.603354f).toDouble()
                                val lon = prefs.getFloat("last_map_lon", 1.888334f).toDouble()
                                map.controller.animateTo(GeoPoint(lat, lon))
                                map.controller.setZoom(11.0)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = AppConfig.floatingUiAlpha),
                    shadowElevation = AppConfig.floatingUiShadowElevation(6.dp),
                    border = BorderStroke(1.dp, AppConfig.floatingUiBorderColor()),
                    modifier = Modifier.size(mapControlButtonDiameter)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Ma position",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(sizing.component(24.dp))
                        )
                    }
                }
            }

            // Indicateur de chargement
            if (isLoading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, AppConfig.floatingUiBorderColor())
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(text = "Chargement des changements...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Message d'erreur éventuel
            if (errorMessage != null && !isLoading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { loadWeeks() }) {
                            Text("Réessayer", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Bottom sheet au clic sur un marker
            selectedPoint?.let { pt ->
                ChangeDetailBottomSheet(
                    point = pt,
                    onDismiss = {
                        selectedPoint = null
                        savedSelectedSupportId = null
                    },
                    onOpenSupportDetail = { supId ->
                        savedLat = pt.latitude
                        savedLon = pt.longitude
                        mapViewRef?.let { map ->
                            savedZoom = map.zoomLevelDouble
                        }
                        savedSelectedSupportId = supId
                        if (onOpenSupportDetail != null) {
                            onOpenSupportDetail.invoke(supId)
                        } else {
                            navController.navigate("support_detail/$supId")
                        }
                    }
                )
            }

            // Bottom sheet de filtres complets (Technologies, Fréquences, Opérateurs, Actions, Statut)
            if (showFilterSheet) {
                ChangesFilterSheet(
                    currentFilter = filterState,
                    allPoints = allPoints,
                    onApply = { newFilter -> filterState = newFilter },
                    onDismiss = { showFilterSheet = false }
                )
            }
        }
    }
}

/**
 * Bouton de filtre rapide pour les changements : haute lisibilité sur la carte
 * avec fond opaque/givré, bordure nette et badge de comptage.
 */
@Composable
private fun ChangeFilterChipButton(
    selected: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        AppConfig.floatingUiBorderColor()
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = AppConfig.floatingUiShadowElevation(if (selected) 5.dp else 3.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.height(sizing.component(38.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = sizing.spacing(14.dp), vertical = sizing.spacing(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp))
        ) {
            Text(
                text = "$label ($count)",
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

private fun formatDisplayDate(rawDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val d = parser.parse(rawDate)
        if (d != null) {
            val formatter = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
            formatter.format(d)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

/**
 * Carte d'accès vers la carte des changements depuis l'écran Statistiques.
 */
@Composable
fun ChangesMapEntryCard(bgColor: Color, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val shape = if (fr.geotower.utils.AppConfig.useOneUiDesign) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sizing.spacing(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sizing.spacing(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(sizing.component(24.dp))
            )
            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Changements",
                    style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Nouveaux sites, allumages 5G et évolutions hebdomadaires",
                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(sizing.component(16.dp))
                    .rotate(180f)
            )
        }
    }
}

/**
 * Calque de localisation utilisant exactement le même repère (LocationMarkerPainter)
 * que la carte normale : anneau aux couleurs du thème, rond blanc/couleur et flèche de cap.
 */
class ChangesLocationOverlay(
    provider: org.osmdroid.views.overlay.mylocation.IMyLocationProvider,
    private val mapView: MapView,
    primaryColor: Int
) : MyLocationNewOverlay(provider, mapView), SensorEventListener {

    private val pt = android.graphics.Point()
    private val painter = LocationMarkerPainter(
        mapView.context.resources.displayMetrics.density,
        primaryColor
    )

    private val sensorManager = mapView.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    @Suppress("DEPRECATION")
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    var currentAzimuth: Float = 0f

    override fun enableMyLocation(): Boolean {
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        return super.enableMyLocation()
    }

    override fun disableMyLocation() {
        sensorManager?.unregisterListener(this)
        super.disableMyLocation()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val az = Math.toDegrees(orientation[0].toDouble()).toFloat()
            currentAzimuth = (az + 360f) % 360f
            mapView.postInvalidate()
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            currentAzimuth = (event.values[0] + 360f) % 360f
            mapView.postInvalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun drawMyLocation(
        canvas: android.graphics.Canvas,
        projection: org.osmdroid.views.Projection,
        lastFix: android.location.Location
    ) {
        projection.toPixels(org.osmdroid.util.GeoPoint(lastFix.latitude, lastFix.longitude), pt)
        val rotation = if (lastFix.hasBearing()) lastFix.bearing else currentAzimuth
        painter.draw(
            canvas = canvas,
            x = pt.x.toFloat(),
            y = pt.y.toFloat(),
            rotationDegrees = rotation,
            showDirection = true
        )
    }
}

private const val WEB_MERCATOR_WORLD_TILE_SIZE_PX = 256.0

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

@Composable
private fun SmallFloatingCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    isActive: Boolean = false,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val buttonSize = sizing.component(size)

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = AppConfig.floatingUiAlpha)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = AppConfig.floatingUiAlpha)
        },
        shadowElevation = AppConfig.floatingUiShadowElevation(4.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else AppConfig.floatingUiBorderColor()
        ),
        modifier = modifier.size(buttonSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                modifier = Modifier.size(sizing.component(22.dp)),
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (badgeCount != null && badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 9.sp
                    )
                }
            }
        }
    }
}

private data class ChangeClusterItem(
    val latitude: Double,
    val longitude: Double,
    val points: List<ChangeMapPoint>,
    val operators: List<String>
)

private class MutableCluster(firstPoint: ChangeMapPoint) {
    var count: Int = 1
    var sumLat: Double = firstPoint.latitude
    var sumLon: Double = firstPoint.longitude
    val points: ArrayList<ChangeMapPoint> = ArrayList<ChangeMapPoint>().apply { add(firstPoint) }
    val operators: LinkedHashSet<String> = LinkedHashSet<String>().apply { addAll(firstPoint.operators()) }

    val lat: Double get() = sumLat / count
    val lon: Double get() = sumLon / count

    fun add(pt: ChangeMapPoint) {
        count++
        sumLat += pt.latitude
        sumLon += pt.longitude
        points.add(pt)
        operators.addAll(pt.operators())
    }

    fun toClusterItem(): ChangeClusterItem = ChangeClusterItem(
        latitude = lat,
        longitude = lon,
        points = points,
        operators = operators.toList()
    )
}

private fun clusterChangePoints(points: List<ChangeMapPoint>, zoom: Double): List<ChangeClusterItem> {
    if (zoom >= 16.5 || points.size <= 1) {
        return points.map { pt ->
            ChangeClusterItem(
                latitude = pt.latitude,
                longitude = pt.longitude,
                points = listOf(pt),
                operators = pt.operators()
            )
        }
    }

    // Séparation minimale garantie sur l'écran : 60 dp pour éviter tout chevauchement
    val minSpacingDp = 60.0
    val radiusLat = minSpacingDp * 360.0 / (256.0 * (1 shl zoom.toInt().coerceIn(3, 18)))
    val radiusLon = radiusLat / kotlin.math.cos(Math.toRadians(46.5)).coerceAtLeast(0.5)
    val rLatSq = radiusLat * radiusLat
    val rLonSq = radiusLon * radiusLon

    // Grille spatiale pour indexation O(1)
    val grid = LinkedHashMap<Pair<Long, Long>, MutableList<MutableCluster>>()

    for (pt in points) {
        val cellX = (pt.longitude / radiusLon).toLong()
        val cellY = (pt.latitude / radiusLat).toLong()

        var bestCluster: MutableCluster? = null
        var bestDistSq = Double.MAX_VALUE

        for (dx in -1L..1L) {
            for (dy in -1L..1L) {
                val list = grid[Pair(cellX + dx, cellY + dy)] ?: continue
                for (cluster in list) {
                    val dLat = pt.latitude - cluster.lat
                    val dLon = pt.longitude - cluster.lon
                    val normDist = (dLat * dLat) / rLatSq + (dLon * dLon) / rLonSq
                    if (normDist <= 1.0 && normDist < bestDistSq) {
                        bestDistSq = normDist
                        bestCluster = cluster
                    }
                }
            }
        }

        if (bestCluster != null) {
            bestCluster.add(pt)
        } else {
            val newCluster = MutableCluster(pt)
            val list = grid.getOrPut(Pair(cellX, cellY)) { ArrayList(2) }
            list.add(newCluster)
        }
    }

    return grid.values.flatten().map { it.toClusterItem() }
}
