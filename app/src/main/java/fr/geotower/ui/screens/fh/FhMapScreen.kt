package fr.geotower.ui.screens.fh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.ui.components.MapScaleAndAttribution
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.changes.ChangesLocationOverlay
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.MapProviderRules
import fr.geotower.utils.MapUtils
import fr.geotower.utils.PersistentMapTileProvider
import fr.geotower.utils.PreferenceStores
import fr.geotower.utils.isNetworkAvailable
import fr.geotower.utils.location.FusedMyLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

const val FH_MAP_ROUTE = "fh_map"
private const val PREF_FH_EXCLUDED_OPERATORS = "fh_map_excluded_operators"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FhMapScreen(
    navController: NavController,
    repository: AnfrRepository
) {
    val context = LocalContext.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()
    val safeBackNavigation = rememberSafeBackNavigation(navController)
    val prefs = remember(context) { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    val showScale = remember { prefs.getBoolean("show_map_scale", true) }
    val showAttribution = remember { prefs.getBoolean("show_map_attribution", true) }

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val bgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

    BackHandler {
        safeBackNavigation.navigateBack()
    }

    var savedLat by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var savedLon by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var savedZoom by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    val scaleZoomState = remember { mutableDoubleStateOf(if (!savedZoom.isNaN()) savedZoom else 6.0) }
    val scaleLatState = remember { mutableDoubleStateOf(if (!savedLat.isNaN()) savedLat else 46.6) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<ChangesLocationOverlay?>(null) }
    val fhOverlay = remember(context) { FhLinksCanvasOverlay(context) }

    var preparedLinks by remember { mutableStateOf<List<FhPreparedLink>>(emptyList()) }
    var operatorBuckets by remember { mutableStateOf<List<FhOperatorBucket>>(emptyList()) }
    var excludedOperatorIds by remember {
        mutableStateOf(prefs.getStringSet(PREF_FH_EXCLUDED_OPERATORS, emptySet()).orEmpty().toSet())
    }
    var selectedLink by remember { mutableStateOf<FhPreparedLink?>(null) }
    var showOperatorSheet by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isViewportScoped by remember { mutableStateOf(false) }
    var hasFhTable by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var viewportVersion by remember { mutableIntStateOf(0) }

    fun updateExcludedOperators(next: Set<String>) {
        excludedOperatorIds = next
        prefs.edit().putStringSet(PREF_FH_EXCLUDED_OPERATORS, next).apply()
    }

    val filteredLinks = remember(preparedLinks, excludedOperatorIds) {
        if (excludedOperatorIds.isEmpty()) {
            preparedLinks
        } else {
            preparedLinks.filter { it.operatorId !in excludedOperatorIds }
        }
    }

    LaunchedEffect(fhOverlay) {
        fhOverlay.onLinkClick = { tapped ->
            selectedLink = tapped
        }
    }

    LaunchedEffect(filteredLinks, selectedLink) {
        fhOverlay.setLinks(filteredLinks)
        fhOverlay.setSelectedLink(selectedLink)
        mapViewRef?.invalidate()
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        val map = mapViewRef
        val bbox = map?.boundingBox
        val result = withContext(Dispatchers.IO) {
            repository.getAllFhLinksForMap(
                latNorth = bbox?.latNorth ?: 51.5,
                lonEast = bbox?.lonEast ?: 9.8,
                latSouth = bbox?.latSouth ?: 41.0,
                lonWest = bbox?.lonWest ?: -5.5
            )
        }
        val prepared = withContext(Dispatchers.Default) {
            FhOperatorResolver.prepare(result.links)
        }
        val buckets = withContext(Dispatchers.Default) {
            FhOperatorResolver.buildBuckets(prepared)
        }
        preparedLinks = prepared
        operatorBuckets = buckets
        isViewportScoped = result.isViewportScoped
        hasFhTable = result.hasFhTable
        isLoading = false
    }

    LaunchedEffect(viewportVersion, isViewportScoped) {
        if (!isViewportScoped || viewportVersion == 0) return@LaunchedEffect
        delay(350L)
        val map = mapViewRef ?: return@LaunchedEffect
        val bbox = map.boundingBox
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            repository.getAllFhLinksForMap(
                latNorth = bbox.latNorth,
                lonEast = bbox.lonEast,
                latSouth = bbox.latSouth,
                lonWest = bbox.lonWest
            )
        }
        val prepared = withContext(Dispatchers.Default) {
            FhOperatorResolver.prepare(result.links)
        }
        val buckets = withContext(Dispatchers.Default) {
            FhOperatorResolver.buildBuckets(prepared)
        }
        preparedLinks = prepared
        operatorBuckets = buckets
        isLoading = false
    }

    val mapProvider by AppConfig.mapProvider
    val ignStyle by AppConfig.ignStyle
    var effectiveProvider by remember { mutableIntStateOf(MapProviderRules.sanitize(AppConfig.mapProvider.intValue)) }
    var mapFiles by remember { mutableStateOf(emptyArray<File>()) }

    LaunchedEffect(AppConfig.mapProvider.intValue) {
        effectiveProvider = MapProviderRules.sanitize(AppConfig.mapProvider.intValue)
    }

    LaunchedEffect(Unit) {
        val offlineDir = File(context.getExternalFilesDir(null), "maps")
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
            mapViewRef?.let { mv ->
                runCatching {
                    if (mv.tileProvider is MapsForgeTileProvider) {
                        (mv.tileProvider as? MapsForgeTileProvider)?.detach()
                    }
                    mv.onDetach()
                }
            }
        }
    }

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val activeOperatorCount = remember(operatorBuckets, excludedOperatorIds) {
        operatorBuckets.count { it.id !in excludedOperatorIds }
    }

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                                tileProvider = MapsForgeTileProvider(
                                    SimpleRegisterReceiver(ctx),
                                    MapsForgeTileSource.createFromFiles(mapFiles),
                                    null
                                )
                            }
                        }
                        overlayManager.tilesOverlay.setColorFilter(
                            if (shouldInvertColors) MapUtils.getInvertFilter() else null
                        )
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        setHorizontalMapRepetitionEnabled(false)
                        setVerticalMapRepetitionEnabled(false)

                        val initialLat = if (!savedLat.isNaN()) savedLat else 46.6
                        val initialLon = if (!savedLon.isNaN()) savedLon else 2.4
                        val initialZoom = if (!savedZoom.isNaN()) savedZoom else 6.0
                        controller.setZoom(initialZoom)
                        controller.setCenter(GeoPoint(initialLat, initialLon))

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                mapCenter?.let {
                                    savedLat = it.latitude
                                    savedLon = it.longitude
                                    if (kotlin.math.abs(it.latitude - scaleLatState.doubleValue) >= 0.25) {
                                        scaleLatState.doubleValue = it.latitude
                                    }
                                }
                                if (isViewportScoped) {
                                    viewportVersion++
                                }
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                val z = event?.zoomLevel ?: zoomLevelDouble
                                savedZoom = z
                                if (kotlin.math.abs(z - scaleZoomState.doubleValue) >= 0.05) {
                                    scaleZoomState.doubleValue = z
                                }
                                if (isViewportScoped) {
                                    viewportVersion++
                                }
                                return false
                            }
                        })

                        val locOverlay = ChangesLocationOverlay(
                            FusedMyLocationProvider(ctx),
                            this,
                            primaryColorArgb
                        ).apply {
                            val hasLoc = ContextCompat.checkSelfPermission(
                                ctx,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            if (hasLoc) {
                                enableMyLocation()
                            }
                        }
                        overlays.add(fhOverlay)
                        overlays.add(locOverlay)
                        locationOverlayRef = locOverlay
                        mapViewRef = this
                    }
                },
                update = { view ->
                    mapViewRef = view
                    var shouldInvalidateMap = false
                    if (effectiveProvider == 4 && mapFiles.isNotEmpty()) {
                        if (view.tileProvider !is MapsForgeTileProvider) {
                            runCatching {
                                view.tileProvider = MapsForgeTileProvider(
                                    SimpleRegisterReceiver(context),
                                    MapsForgeTileSource.createFromFiles(mapFiles),
                                    null
                                )
                                shouldInvalidateMap = true
                            }
                        }
                    } else {
                        val targetSource = getTargetTileSource(effectiveProvider, ignStyle)
                        if (view.tileProvider is MapsForgeTileProvider) {
                            runCatching { (view.tileProvider as? MapsForgeTileProvider)?.detach() }
                            view.tileProvider = PersistentMapTileProvider(context, targetSource)
                            view.setTileSource(targetSource)
                            shouldInvalidateMap = true
                        } else if (view.tileProvider.tileSource.name() != targetSource.name()) {
                            view.setTileSource(targetSource)
                            shouldInvalidateMap = true
                        }
                    }

                    if (lastTilesColorFilterMap[0] !== view || lastTilesColorFilterInverted[0] != shouldInvertColors) {
                        view.overlayManager.tilesOverlay.setColorFilter(
                            if (shouldInvertColors) MapUtils.getInvertFilter() else null
                        )
                        lastTilesColorFilterMap[0] = view
                        lastTilesColorFilterInverted[0] = shouldInvertColors
                        shouldInvalidateMap = true
                    }

                    if (shouldInvalidateMap) {
                        view.invalidate()
                    }
                }
            )

            // En-tête + barre rapide des opérateurs FH
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(8.dp)),
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { safeBackNavigation.navigateBack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour"
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Carte des faisceaux FH",
                                    style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when {
                                    isLoading -> "Chargement des liaisons FH..."
                                    !hasFhTable -> "Table FH absente de la base locale"
                                    else -> {
                                        val vis = numberFormat.format(filteredLinks.size)
                                        val tot = numberFormat.format(preparedLinks.size)
                                        "$vis / $tot traits • $activeOperatorCount/${operatorBuckets.size} opérateurs"
                                    }
                                }
                                Text(
                                    text = subtitle,
                                    style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { refreshTrigger++ }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualiser"
                                )
                            }
                            IconButton(onClick = { showOperatorSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Choisir les opérateurs FH",
                                    tint = if (excludedOperatorIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                            )
                        }
                    }
                }

                // Barre horizontale rapide pour activer/désactiver les opérateurs FH en 1 clic
                if (operatorBuckets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = excludedOperatorIds.isEmpty()
                        FilterChip(
                            selected = allSelected,
                            onClick = {
                                if (!allSelected) {
                                    updateExcludedOperators(emptySet())
                                }
                            },
                            label = {
                                Text(
                                    text = "Tous (${numberFormat.format(preparedLinks.size)})",
                                    fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                        )

                        operatorBuckets.forEach { bucket ->
                            val isSelected = bucket.id !in excludedOperatorIds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val next = if (isSelected) {
                                        excludedOperatorIds + bucket.id
                                    } else {
                                        excludedOperatorIds - bucket.id
                                    }
                                    updateExcludedOperators(next)
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(bucket.colorInt))
                                    )
                                },
                                label = {
                                    Text(
                                        text = "${bucket.label} (${numberFormat.format(bucket.count)})",
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                )
                            )
                        }
                    }
                }
            }

            // Message si aucune liaison FH disponible
            if (!isLoading && preparedLinks.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (!hasFhTable) {
                                "Aucune table de faisceaux FH dans cette base"
                            } else {
                                "Aucune liaison FH dans cette zone"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!hasFhTable) {
                                "Mettez à jour la base de données locale ou activez l'API Live pour afficher les faisceaux hertziens."
                            } else {
                                "Dézoomez ou déplacez la carte pour afficher les traits FH."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Boutons flottants de contrôle de la carte (Zoom en HAUT, Localisation en BAS) identiques à la carte principale
            val mapControlButtonDiameter = sizing.component(52.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = sizing.spacing(16.dp), bottom = sizing.spacing(24.dp)),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
            ) {
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

                Surface(
                    onClick = {
                        mapViewRef?.let { map ->
                            val myLoc = locationOverlayRef?.myLocation
                            if (myLoc != null) {
                                map.controller.animateTo(myLoc)
                                map.controller.setZoom(14.0)
                            } else {
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

            // Échelle et crédits en bas à gauche (dans un scope dédié pour ne pas recomposer la carte au scroll)
            FhScaleAndAttributionBadge(
                scaleZoomState = scaleZoomState,
                scaleLatState = scaleLatState,
                effectiveProvider = effectiveProvider,
                ignStyle = ignStyle,
                showScale = showScale,
                showAttribution = showAttribution,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = sizing.spacing(16.dp), bottom = sizing.spacing(16.dp))
            )

            // Fiche du trait FH sélectionné
            selectedLink?.let { prepared ->
                val link = prepared.link
                val distText = if (link.distanceM >= 1000) {
                    String.format(Locale.getDefault(), "%.2f km", link.distanceM / 1000.0)
                } else {
                    "${link.distanceM} m"
                }
                val azAB = link.azimutAb.roundToInt()
                val azBA = (azAB + 180) % 360

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = sizing.spacing(12.dp),
                            end = sizing.spacing(72.dp),
                            bottom = sizing.spacing(24.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(sizing.spacing(14.dp)),
                        verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(prepared.colorInt))
                            )
                            Text(
                                text = "Liaison FH • ${prepared.operatorLabel}",
                                style = sizing.textStyle(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { selectedLink = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer")
                            }
                        }

                        Text(
                            text = "Distance : $distText  •  Azimut : $azAB° ↔ $azBA°",
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    mapViewRef?.controller?.animateTo(
                                        GeoPoint(link.latA, link.lonA),
                                        13.5,
                                        500L
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Centrer A (${link.idAnfrA})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    mapViewRef?.controller?.animateTo(
                                        GeoPoint(link.latB, link.lonB),
                                        13.5,
                                        500L
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Centrer B (${link.idAnfrB})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    prefs.edit()
                                        .putFloat("clicked_lat", link.latA.toFloat())
                                        .putFloat("clicked_lon", link.lonA.toFloat())
                                        .apply()
                                    navController.navigate("site_detail/${Uri.encode(link.idAnfrA)}") {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Fiche A", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    prefs.edit()
                                        .putFloat("clicked_lat", link.latB.toFloat())
                                        .putFloat("clicked_lon", link.lonB.toFloat())
                                        .apply()
                                    navController.navigate("site_detail/${Uri.encode(link.idAnfrB)}") {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Fiche B", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOperatorSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var searchQuery by remember { mutableStateOf("") }
        val hasNonMajor = remember(operatorBuckets) { operatorBuckets.any { !it.isMajorMobile } }
        val displayedBuckets = remember(operatorBuckets, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                operatorBuckets
            } else {
                operatorBuckets.filter { it.label.contains(q, ignoreCase = true) }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showOperatorSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choisir les opérateurs FH",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${numberFormat.format(filteredLinks.size)} liaisons affichées sur ${numberFormat.format(preparedLinks.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { updateExcludedOperators(emptySet()) }) {
                        Text("Tout sélectionner")
                    }
                    OutlinedButton(
                        onClick = {
                            updateExcludedOperators(operatorBuckets.map { it.id }.toSet())
                        }
                    ) {
                        Text("Tout masquer")
                    }
                    if (hasNonMajor) {
                        OutlinedButton(
                            onClick = {
                                updateExcludedOperators(
                                    operatorBuckets.filterNot { it.isMajorMobile }.map { it.id }.toSet()
                                )
                            }
                        ) {
                            Text("4 opérateurs mobiles")
                        }
                    }
                }

                if (operatorBuckets.size > 6) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Rechercher un opérateur FH...") }
                    )
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(displayedBuckets, key = { it.id }) { bucket ->
                        val isChecked = bucket.id !in excludedOperatorIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = if (isChecked) {
                                        excludedOperatorIds + bucket.id
                                    } else {
                                        excludedOperatorIds - bucket.id
                                    }
                                    updateExcludedOperators(next)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val next = if (checked) {
                                        excludedOperatorIds - bucket.id
                                    } else {
                                        excludedOperatorIds + bucket.id
                                    }
                                    updateExcludedOperators(next)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(bucket.colorInt))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bucket.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${numberFormat.format(bucket.count)} liaisons",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    updateExcludedOperators(
                                        operatorBuckets.map { it.id }.toSet() - bucket.id
                                    )
                                }
                            ) {
                                Text("Seul")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FhScaleAndAttributionBadge(
    scaleZoomState: androidx.compose.runtime.DoubleState,
    scaleLatState: androidx.compose.runtime.DoubleState,
    effectiveProvider: Int,
    ignStyle: Int,
    showScale: Boolean,
    showAttribution: Boolean,
    modifier: Modifier = Modifier
) {
    MapScaleAndAttribution(
        zoom = scaleZoomState.doubleValue,
        latitude = scaleLatState.doubleValue,
        effectiveProvider = effectiveProvider,
        ignStyle = ignStyle,
        showScale = showScale,
        showAttribution = showAttribution,
        modifier = modifier
    )
}
