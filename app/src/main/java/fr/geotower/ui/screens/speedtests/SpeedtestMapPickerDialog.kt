package fr.geotower.ui.screens.speedtests

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PreferenceStores
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import java.util.Locale

@Composable
fun SpeedtestMapPickerDialog(
    initialLat: Double?,
    initialLon: Double?,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences(PreferenceStores.APP, Context.MODE_PRIVATE) }
    val savedLat = remember { prefs.getFloat("last_map_lat", 46.603354f).toDouble() }
    val savedLon = remember { prefs.getFloat("last_map_lon", 1.888334f).toDouble() }
    val hasSavedPos = remember { prefs.contains("last_map_lat") && prefs.contains("last_map_lon") }

    val startLat = initialLat ?: savedLat
    val startLon = initialLon ?: savedLon
    val startZoom = when {
        initialLat != null && initialLon != null -> 15.5
        hasSavedPos -> 13.0
        else -> 6.0
    }

    var currentLat by remember { mutableDoubleStateOf(startLat) }
    var currentLon by remember { mutableDoubleStateOf(startLon) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Si aucune position initiale n'est définie, on tente de centrer sur la position GPS actuelle
    LaunchedEffect(initialLat, initialLon) {
        if (initialLat == null || initialLon == null) {
            val loc = getLastLocationIfPermitted(context)
            if (loc != null) {
                currentLat = loc.first
                currentLon = loc.second
                mapViewRef?.controller?.setCenter(GeoPoint(loc.first, loc.second))
                mapViewRef?.controller?.setZoom(15.0)
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mv = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mv?.onResume()
                Lifecycle.Event.ON_PAUSE -> mv?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mv?.onDetach()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Carte borderless plein écran
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(startZoom)
                        controller.setCenter(GeoPoint(startLat, startLon))

                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                if (p != null) {
                                    controller.animateTo(p)
                                }
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        })
                        overlays.add(eventsOverlay)

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                val center = mapCenter
                                currentLat = center.latitude
                                currentLon = center.longitude
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                val center = mapCenter
                                currentLat = center.latitude
                                currentLon = center.longitude
                                return false
                            }
                        })

                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viseur central fixe
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
                    .padding(bottom = 20.dp)
            )

            // En-tête flottant cohérent avec les cartes de l'app
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = AppConfig.floatingUiAlpha),
                shadowElevation = AppConfig.floatingUiShadowElevation(6.dp),
                border = BorderStroke(1.dp, AppConfig.floatingUiBorderColor()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sizing.spacing(6.dp), vertical = sizing.spacing(6.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.appstrings_back)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.speedtests_pick_on_map),
                            style = sizing.textStyle(MaterialTheme.typography.titleMedium),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format(Locale.US, "%.6f, %.6f", currentLat, currentLon),
                            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Boutons zoom, dézoom et localisation — même style et placement que les cartes de l'app
            val mapControlButtonDiameter = sizing.component(52.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = sizing.spacing(16.dp),
                        bottom = sizing.spacing(84.dp)
                    ),
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
                                    contentDescription = null,
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
                                    contentDescription = null,
                                    modifier = Modifier.size(sizing.component(26.dp))
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        scope.launch {
                            val loc = getLastLocationIfPermitted(context)
                            if (loc != null) {
                                mapViewRef?.controller?.animateTo(GeoPoint(loc.first, loc.second))
                                mapViewRef?.controller?.setZoom(15.0)
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
                            contentDescription = stringResource(R.string.speedtests_use_current_location),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(sizing.component(24.dp))
                        )
                    }
                }
            }

            // Bouton flottant de validation en bas
            Button(
                onClick = { onConfirm(currentLat, currentLon) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(16.dp))
                    .height(sizing.component(50.dp))
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.speedtests_confirm_location))
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun getLastLocationIfPermitted(context: Context): Pair<Double, Double>? {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null
    return runCatching {
        val loc = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        if (loc != null && loc.latitude.isFinite() && loc.longitude.isFinite()) {
            loc.latitude to loc.longitude
        } else null
    }.getOrNull()
}
