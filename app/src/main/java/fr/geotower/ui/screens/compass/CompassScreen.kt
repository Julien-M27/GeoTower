package fr.geotower.ui.screens.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.LiveDatabaseUsageWarningDialog
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.CompassSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.ui.screens.map.MapViewModel
import fr.geotower.utils.AppConfig
import fr.geotower.utils.OperatorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.res.stringResource
import fr.geotower.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    navController: NavController,
    viewModel: MapViewModel
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenRotation = currentDisplayRotation(context)
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val uiStyle = LocalGeoTowerUiStyle.current
    var showLocation by remember { mutableStateOf(prefs.getBoolean("show_compass_location", true)) }
    var showGps by remember { mutableStateOf(prefs.getBoolean("show_compass_gps", true)) }
    var showAccuracy by remember { mutableStateOf(prefs.getBoolean("show_compass_accuracy", true)) }
    var compassOrder by remember { mutableStateOf(prefs.getString("compass_order", "location,gps,accuracy")!!.split(",")) }
    var showCompassSettingsSheet by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val antennasList by viewModel.antennas.collectAsState()

    // --- MODE CLAIR / SOMBRE DYNAMIQUE ---
    val themeMode by AppConfig.themeMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val isOledMode by AppConfig.isOledMode

    // Couleurs de fond adaptatives
    val compassBg = if (isDark) {
        if (isOledMode) Color.Black else Color(0xFF121212)
    } else {
        MaterialTheme.colorScheme.background
    }

    // Couleurs de texte adaptatives
    val oncompassBg = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
    val oncompassBgVariant = if (isDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
    val compassTextGray = if (isDark) Color(0xFF9E9E9E) else MaterialTheme.colorScheme.outline

    val dynamicPrimaryColor = MaterialTheme.colorScheme.primary

    val defaultOp by AppConfig.defaultOperator

    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LiveDatabaseUsageWarningDialog(RemoteFeatureFlags.Features.LIVE_API_FR_BBOX)

    // --- VARIABLES BOUSSOLE ---
    var continuousAzimuth by remember { mutableFloatStateOf(0f) }
    var displayAzimuth by remember { mutableIntStateOf(0) }

    // --- LECTURE DES TRADUCTIONS (Dans le contexte sécurisé) ---
    val txtSearching = stringResource(R.string.appstrings_searching)
    val txtUnknown = stringResource(R.string.appstrings_unknown)

    // --- VARIABLES LOCALISATION ---
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    // CORRECTION : On utilise la variable texte simple !
    var city by remember { mutableStateOf(txtSearching) }
    var country by remember { mutableStateOf("...") }
    // --- NOUVELLES VARIABLES POUR LE REGROUPEMENT (CLUSTERING) ---
    var selectedClusterSites by remember { mutableStateOf<List<RadarSite>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // --- GESTION DES CAPTEURS (Boussole) ---
    DisposableEffect(screenRotation) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        @Suppress("DEPRECATION")
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        var lastIntAzimuth = -1

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                var newAzimuth = 0f
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    newAzimuth = azimuthFromRotationVector(event.values, screenRotation)
                } else if (isLegacyOrientationSensor(event.sensor)) {
                    newAzimuth = correctLegacyAzimuthForDisplay(event.values[0], screenRotation)
                }

                newAzimuth = (newAzimuth + 360) % 360

                // Chemin le plus court pour l'animation
                var delta = newAzimuth - (continuousAzimuth % 360f)
                if (delta < -180f) delta += 360f
                else if (delta > 180f) delta -= 360f

                continuousAzimuth += delta
                val currentIntAzimuth = ((continuousAzimuth % 360) + 360).toInt() % 360
                displayAzimuth = currentIntAzimuth

                // DÉTECTION DES POINTS CARDINAUX POUR VIBRATION
                if (lastIntAzimuth != -1 && currentIntAzimuth != lastIntAzimuth) {
                    val oldQuad = lastIntAzimuth / 90
                    val newQuad = currentIntAzimuth / 90
                    // Si on change de quadrant
                    if (oldQuad != newQuad) {
                        val diff = kotlin.math.abs(currentIntAzimuth - lastIntAzimuth)
                        // On vérifie que c'est un vrai mouvement avec un "Range check" (!in)
                        if (diff !in 20..340) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                lastIntAzimuth = currentIntAzimuth
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // --- GESTION DU GPS (Localisation) ---
    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasFinePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val enabledProviders = runCatching {
            locationManager.getProviders(true).toSet()
        }.getOrDefault(emptySet())

        val locationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                latitude = loc.latitude
                longitude = loc.longitude
                accuracy = loc.accuracy
            }
            @Deprecated("Deprecated in Java") override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
            override fun onProviderEnabled(p0: String) {}
            override fun onProviderDisabled(p0: String) {}
        }

        if (hasFinePermission || hasCoarsePermission) {
            val candidateProviders = buildList {
                if (hasFinePermission && LocationManager.GPS_PROVIDER in enabledProviders) {
                    add(LocationManager.GPS_PROVIDER)
                }
                if (LocationManager.NETWORK_PROVIDER in enabledProviders) {
                    add(LocationManager.NETWORK_PROVIDER)
                }
            }
            // On récupère d'abord la dernière position connue pour un affichage immédiat
            val lastLoc = candidateProviders.firstNotNullOfOrNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            if (lastLoc != null) {
                latitude = lastLoc.latitude
                longitude = lastLoc.longitude
                accuracy = lastLoc.accuracy
            }
            // Puis on s'abonne aux mises à jour
            if (hasFinePermission && LocationManager.GPS_PROVIDER in enabledProviders) {
                runCatching {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, locationListener)
                }
            }
            if (LocationManager.NETWORK_PROVIDER in enabledProviders) {
                runCatching {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, locationListener)
                }
            }
        }

        onDispose { runCatching { locationManager.removeUpdates(locationListener) } }
    }

    // --- RÉCUPÉRATION DE LA VILLE (Reverse Geocoding) ---
    LaunchedEffect(latitude, longitude) {
        if (latitude != 0.0 && longitude != 0.0) {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        city = address.locality ?: address.subAdminArea ?: txtUnknown
                        country = address.countryName ?: txtUnknown
                    }
                } catch (_: Exception) { // <-- Remplace "e" par "_"
                    // Ignorer les erreurs réseau temporaires
                }
            }
        }
    }

    // =====================================================================
    // NOUVEAU BLOC : FORCE LA LECTURE DE LA DB AUTOUR DE L'UTILISATEUR
    // =====================================================================
    LaunchedEffect(latitude, longitude) {
        if (latitude != 0.0 && longitude != 0.0) {
            // On crée un carré virtuel d'environ 6 à 7 km autour de l'utilisateur
            val latOffset = 0.06
            val lonOffset = 0.06 / Math.cos(Math.toRadians(latitude))

            // On force le ViewModel à interroger la Base de Données pour cette zone !
            // L'ordre est : Nord, Est, Sud, Ouest
            // ---> CORRECTION ICI : Ajout du zoom (15.0) en premier paramètre <---
            viewModel.loadAntennasInBox(
                15.0,                  // Zoom virtuel élevé pour forcer le mode détaillé
                latitude + latOffset,  // Nord
                longitude + lonOffset, // Est
                latitude - latOffset,  // Sud
                longitude - lonOffset  // Ouest
            )
        }
    }

    // --- MOTEUR RADAR : CALCUL DES 10 SITES LES PLUS PROCHES ---
    var radarSites by remember { mutableStateOf<List<RadarSite>>(emptyList()) }

    // NOUVEAU : On ajoute defaultOp pour que ça se mette à jour si tu changes de préféré !
    LaunchedEffect(latitude, longitude, antennasList, defaultOp) {
        while (true) {
            if (latitude != 0.0 && longitude != 0.0 && antennasList.isNotEmpty()) {
                withContext(Dispatchers.Default) {

                    // --- 1. DÉFINITION DE L'ORDRE DE PRIORITÉ ---
                    val priorityList = getOperatorPriorityList(defaultOp)

                    val groupedSites = antennasList.groupBy { "${it.latitude}_${it.longitude}" }.values

                    val results = groupedSites.map { siteAntennas ->
                        val mainAntenna = siteAntennas.first()

                        val res = FloatArray(2)
                        Location.distanceBetween(latitude, longitude, mainAntenna.latitude, mainAntenna.longitude, res)

                        var bearing = res[1]
                        if (bearing < 0) bearing += 360f

                        // --- 2. FUSION ET TRI DES OPÉRATEURS ---
                        val ops = siteAntennas.asSequence()
                            .mapNotNull { it.operateur }
                            .flatMap { OperatorColors.keysFor(it).asSequence() }
                            .distinct()
                            .sortedBy { op -> priorityList.indexOf(op).takeIf { it >= 0 } ?: 99 }
                            .toList()

                        RadarSite(
                            id = mainAntenna.idAnfr.toLongOrNull() ?: 0L,
                            operateurs = ops,
                            distance = res[0],
                            bearing = bearing,
                            latitude = mainAntenna.latitude,   // ✅ NOUVEAU
                            longitude = mainAntenna.longitude  // ✅ NOUVEAU
                        )
                    }

                    radarSites = results.sortedBy { it.distance }.take(10)
                }
            }
            kotlinx.coroutines.delay(3000L)
        }
    }

    val animatedAngle by animateFloatAsState(
        targetValue = -continuousAzimuth,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "compassAnim"
    )

    Scaffold(
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_compass_title),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = compassBg,
                contentColor = oncompassBg,
                backEnabled = !safeBackNavigation.isLocked,
                actions = {
                    IconButton(onClick = { showCompassSettingsSheet = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = oncompassBg
                        )
                    }
                }
            )
        }
    ) { padding ->
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        val compactLandscape = isLandscape && configuration.screenHeightDp < 430
        val dialSize = when {
            compactLandscape -> 190.dp
            isLandscape -> 230.dp
            else -> 280.dp
        }
        val contentScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(compassBg)
                // --- 1. AJOUT DU DÉFILEMENT ICI ---
                .then(if (!isLandscape) Modifier.verticalScroll(contentScrollState) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompassInfoPanel(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        compassOrder = compassOrder,
                        showLocation = showLocation,
                        showGps = showGps,
                        showAccuracy = showAccuracy,
                        city = city,
                        country = country,
                        latitude = latitude,
                        longitude = longitude,
                        accuracy = accuracy,
                        oncompassBg = oncompassBg,
                        oncompassBgVariant = oncompassBgVariant,
                        compassTextGray = compassTextGray,
                        alignStart = true,
                        compact = compactLandscape
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CompassRadarBlock(
                            dialSize = dialSize,
                            animatedAngle = animatedAngle,
                            radarSites = radarSites,
                            dynamicPrimaryColor = dynamicPrimaryColor,
                            isDark = isDark,
                            oncompassBg = oncompassBg,
                            compassTextGray = compassTextGray,
                            compassBg = compassBg,
                            displayAzimuth = displayAzimuth,
                            showInnerLogo = false,
                            compactCenter = true,
                            onSiteClick = { site ->
                                prefs.edit()
                                    .putFloat("clicked_lat", site.latitude.toFloat())
                                    .putFloat("clicked_lon", site.longitude.toFloat())
                                    .apply()
                                navController.navigate("support_detail/${site.id}")
                            },
                            onClusterClick = { sites -> selectedClusterSites = sites }
                        )
                    }
                }
            } else {

            // --- LE BLOC BOUSSOLE ---
            Box(contentAlignment = Alignment.Center) {
                CompassDialView(
                    modifier = Modifier.size(dialSize),
                    rotationAngle = animatedAngle,
                    // --- 2. ENVOI DES NOUVELLES COULEURS ---
                    oncompassBg = oncompassBg,
                    compassTextGray = compassTextGray
                )

                RadarSitesOverlay(
                    modifier = Modifier.size(dialSize + 100.dp).rotate(animatedAngle),
                    sites = radarSites,
                    currentRotation = animatedAngle,
                    accentColor = dynamicPrimaryColor,
                    isDark = isDark,
                    oncompassBg = oncompassBg,
                    compassBg = compassBg,
                    onSiteClick = { site ->
                        // ✅ CORRECTION : On sauvegarde les coordonnées avant d'ouvrir la page !
                        prefs.edit()
                            .putFloat("clicked_lat", site.latitude.toFloat())
                            .putFloat("clicked_lon", site.longitude.toFloat())
                            .apply()
                        navController.navigate("support_detail/${site.id}")
                    },
                    onClusterClick = { sites -> selectedClusterSites = sites }
                )

                FixedCompassOverlay(
                    modifier = Modifier.size(dialSize),
                    accentColor = dynamicPrimaryColor
                )

                CenterCompassContent(
                    azimuthInt = displayAzimuth,
                    accentColor = dynamicPrimaryColor,
                    // --- 2. ENVOI DES NOUVELLES COULEURS ---
                    oncompassBg = oncompassBg
                )
            }
            }

            // --- PANNEAU DE DÉTAILS (BULLLE DU BAS) ---
            if (selectedClusterSites.isNotEmpty()) {
                ModalBottomSheet(onDismissRequest = { selectedClusterSites = emptyList() }, sheetState = sheetState) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 48.dp)) {
                        Text(text = stringResource(R.string.appstrings_nearby_antennas_azimuth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        LazyColumn {
                            items(selectedClusterSites.sortedBy { it.distance }) { site ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedClusterSites = emptyList()
                                        // ✅ CORRECTION : On sauvegarde les coordonnées ici aussi !
                                        prefs.edit()
                                            .putFloat("clicked_lat", site.latitude.toFloat())
                                            .putFloat("clicked_lon", site.longitude.toFloat())
                                            .apply()
                                        navController.navigate("support_detail/${site.id}")
                                    }.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(24.dp)) {
                                        MiniOperatorGrid(operateurs = site.operateurs)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        // --- AFFICHAGE DES OPÉRATEURS DU SUPPORT ---
                                        val opsText = site.operateurs.joinToString(", ") { op ->
                                            OperatorColors.specForKey(op)?.label
                                                ?: op.lowercase().replaceFirstChar { it.titlecase() }
                                        }
                                        Text(
                                            text = "${stringResource(R.string.appstrings_support_prefix)} $opsText",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )

                                        // --- AFFICHAGE DE LA DISTANCE (En gris standard) ---
                                        val isMi = AppConfig.distanceUnit.intValue == 1
                                        val distStr = formatCompassDistance(site.distance, isMi)
                                        Text(
                                            text = distStr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant // <-- Couleur adaptative standard
                                        )
                                    }
                                    Text(text = "${site.bearing.toInt()}°", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            if (!isLandscape) {
                Spacer(modifier = Modifier.height(24.dp))

            // --- 4. AFFICHAGE DYNAMIQUE (LIEU, GPS, PRÉCISION) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp) // Met un bel espace régulier entre chaque bloc
            ) {
                compassOrder.forEach { block ->
                    when (block) {
                        "location" -> {
                            if (showLocation) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = "Position", tint = oncompassBg, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = city, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = oncompassBg)
                                    Text(text = country, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = oncompassBgVariant)
                                }
                            }
                        }
                        "gps" -> {
                            if (showGps) {
                                // J'ai réorganisé les coordonnées côte à côte pour que ça reste centré et harmonieux peu importe l'ordre !
                                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = stringResource(R.string.appstrings_lat_short), fontSize = 12.sp, color = compassTextGray, fontWeight = FontWeight.Bold)
                                        Text(text = formatCoordinate(latitude, isLat = true), fontSize = 18.sp, color = oncompassBg)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = stringResource(R.string.appstrings_lon_short), fontSize = 12.sp, color = compassTextGray, fontWeight = FontWeight.Bold)
                                        Text(text = formatCoordinate(longitude, isLat = false), fontSize = 18.sp, color = oncompassBg)
                                    }
                                }
                            }
                        }
                        "accuracy" -> {
                            if (showAccuracy) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = stringResource(R.string.appstrings_accuracy), fontSize = 12.sp, color = compassTextGray, fontWeight = FontWeight.Bold)
                                    // ✅ On utilise la même logique d'unité pour la précision
                                    val isMi = AppConfig.distanceUnit.intValue == 1
                                    Text(text = "+/- ${formatCompassDistance(accuracy, isMi)}", fontSize = 18.sp, color = oncompassBg)
                                }
                            }
                        }
                    }
                }
            }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showCompassSettingsSheet) {
        CompassSettingsSheet(
            compassOrder = compassOrder,
            onOrderChange = { newOrder ->
                compassOrder = newOrder
                prefs.edit().putString("compass_order", newOrder.joinToString(",")).apply()
            },
            showLocation = showLocation,
            onLocationChange = {
                showLocation = it
                prefs.edit().putBoolean("show_compass_location", it).apply()
            },
            showGps = showGps,
            onGpsChange = {
                showGps = it
                prefs.edit().putBoolean("show_compass_gps", it).apply()
            },
            showAccuracy = showAccuracy,
            onAccuracyChange = {
                showAccuracy = it
                prefs.edit().putBoolean("show_compass_accuracy", it).apply()
            },
            onDismiss = { showCompassSettingsSheet = false },
            onBack = { showCompassSettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@Composable
private fun CompassRadarBlock(
    dialSize: Dp,
    animatedAngle: Float,
    radarSites: List<RadarSite>,
    dynamicPrimaryColor: Color,
    isDark: Boolean,
    oncompassBg: Color,
    compassTextGray: Color,
    compassBg: Color,
    displayAzimuth: Int,
    showInnerLogo: Boolean = true,
    compactCenter: Boolean = false,
    onSiteClick: (RadarSite) -> Unit,
    onClusterClick: (List<RadarSite>) -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        CompassDialView(
            modifier = Modifier.size(dialSize),
            rotationAngle = animatedAngle,
            oncompassBg = oncompassBg,
            compassTextGray = compassTextGray,
            compact = compactCenter
        )

        RadarSitesOverlay(
            modifier = Modifier.size(dialSize + 100.dp).rotate(animatedAngle),
            sites = radarSites,
            currentRotation = animatedAngle,
            accentColor = dynamicPrimaryColor,
            isDark = isDark,
            oncompassBg = oncompassBg,
            compassBg = compassBg,
            onSiteClick = onSiteClick,
            onClusterClick = onClusterClick
        )

        FixedCompassOverlay(
            modifier = Modifier.size(dialSize),
            accentColor = dynamicPrimaryColor
        )

        CenterCompassContent(
            azimuthInt = displayAzimuth,
            accentColor = dynamicPrimaryColor,
            oncompassBg = oncompassBg,
            showLogo = showInnerLogo,
            compact = compactCenter
        )
    }
}

@Composable
private fun CompassInfoPanel(
    modifier: Modifier = Modifier,
    compassOrder: List<String>,
    showLocation: Boolean,
    showGps: Boolean,
    showAccuracy: Boolean,
    city: String,
    country: String,
    latitude: Double,
    longitude: Double,
    accuracy: Float,
    oncompassBg: Color,
    oncompassBgVariant: Color,
    compassTextGray: Color,
    alignStart: Boolean,
    compact: Boolean = false
) {
    val horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally
    val blockSpacing = when {
        compact -> 10.dp
        alignStart -> 18.dp
        else -> 24.dp
    }
    val iconSize = when {
        compact -> 24.dp
        alignStart -> 28.dp
        else -> 32.dp
    }
    val cityTextSize = when {
        compact -> 24.sp
        alignStart -> 28.sp
        else -> 32.sp
    }
    val countryTextSize = when {
        compact -> 17.sp
        alignStart -> 20.sp
        else -> 24.sp
    }
    val coordinateSpacing = if (compact) 18.dp else if (alignStart) 24.dp else 32.dp
    val labelTextSize = if (compact) 11.sp else 12.sp
    val valueTextSize = if (compact) 16.sp else 18.sp

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(blockSpacing)
    ) {
        compassOrder.forEach { block ->
            when (block) {
                "location" -> {
                    if (showLocation) {
                        Column(horizontalAlignment = horizontalAlignment) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = "Position",
                                tint = oncompassBg,
                                modifier = Modifier.size(iconSize)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = city,
                                fontSize = cityTextSize,
                                fontWeight = FontWeight.Bold,
                                color = oncompassBg
                            )
                            Text(
                                text = country,
                                fontSize = countryTextSize,
                                fontWeight = FontWeight.Medium,
                                color = oncompassBgVariant
                            )
                        }
                    }
                }

                "gps" -> {
                    if (showGps) {
                        Row(horizontalArrangement = Arrangement.spacedBy(coordinateSpacing)) {
                            Column(horizontalAlignment = horizontalAlignment) {
                                Text(text = stringResource(R.string.appstrings_lat_short), fontSize = labelTextSize, color = compassTextGray, fontWeight = FontWeight.Bold)
                                Text(text = formatCoordinate(latitude, isLat = true), fontSize = valueTextSize, color = oncompassBg)
                            }
                            Column(horizontalAlignment = horizontalAlignment) {
                                Text(text = stringResource(R.string.appstrings_lon_short), fontSize = labelTextSize, color = compassTextGray, fontWeight = FontWeight.Bold)
                                Text(text = formatCoordinate(longitude, isLat = false), fontSize = valueTextSize, color = oncompassBg)
                            }
                        }
                    }
                }

                "accuracy" -> {
                    if (showAccuracy) {
                        Column(horizontalAlignment = horizontalAlignment) {
                            Text(text = stringResource(R.string.appstrings_accuracy), fontSize = labelTextSize, color = compassTextGray, fontWeight = FontWeight.Bold)
                            val isMi = AppConfig.distanceUnit.intValue == 1
                            Text(text = "+/- ${formatCompassDistance(accuracy, isMi)}", fontSize = valueTextSize, color = oncompassBg)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun isLegacyOrientationSensor(sensor: Sensor): Boolean {
    return sensor.type == Sensor.TYPE_ORIENTATION
}

@Suppress("DEPRECATION")
private fun currentDisplayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display.rotation
    } else {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_Y,
            SensorManager.AXIS_MINUS_X,
            remappedMatrix
        )
        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_MINUS_X,
            SensorManager.AXIS_MINUS_Y,
            remappedMatrix
        )
        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_MINUS_Y,
            SensorManager.AXIS_X,
            remappedMatrix
        )
        else -> false
    }

    return if (remapped) remappedMatrix else rotationMatrix
}

private fun correctLegacyAzimuthForDisplay(azimuth: Float, displayRotation: Int): Float {
    return when (displayRotation) {
        Surface.ROTATION_90 -> azimuth + 90f
        Surface.ROTATION_180 -> azimuth + 180f
        Surface.ROTATION_270 -> azimuth - 90f
        else -> azimuth
    }
}

// Fonction utilitaire pour formater en Degrés Minutes Secondes (DMS)
fun formatCoordinate(value: Double, isLat: Boolean): String {
    if (value == 0.0) return "--°--'--\"-"
    val absVal = Math.abs(value)
    val deg = absVal.toInt()
    val minFloat = (absVal - deg) * 60
    val min = minFloat.toInt()
    val sec = ((minFloat - min) * 60).toInt()

    val direction = if (isLat) {
        if (value >= 0) "N" else "S"
    } else {
        if (value >= 0) "E" else "O" // Ouest = O en français
    }

    return String.format(Locale.US, "%03d°%02d'%02d\"%s", deg, min, sec, direction)
}

@Composable
fun CompassDialView(
    modifier: Modifier,
    rotationAngle: Float,
    oncompassBg: Color,
    compassTextGray: Color,
    compact: Boolean = false
) {
    val density = LocalDensity.current
    val textPaint = remember(compassTextGray, compact) {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { if (compact) 11.sp.toPx() else 14.sp.toPx() }
            color = compassTextGray.toArgb()
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val cardinalPaint = remember(oncompassBg, compact) {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { if (compact) 15.sp.toPx() else 18.sp.toPx() }
            color = oncompassBg.toArgb()
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    Canvas(modifier = modifier.rotate(rotationAngle)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2
        val textRadius = radius - if (compact) 24.dp.toPx() else 35.dp.toPx()

        for (degree in 0 until 360) {
            val angleRad = Math.toRadians((degree - 90).toDouble())
            val cosAngle = kotlin.math.cos(angleRad).toFloat()
            val sinAngle = kotlin.math.sin(angleRad).toFloat()

            val start = center + Offset(cosAngle * radius, sinAngle * radius)

            // On utilise les couleurs dynamiques
            val (tickLengthDp, tickColor, tickWidthDp) = when {
                degree % 90 == 0 -> Triple(20.dp, oncompassBg, 2.dp)
                degree % 10 == 0 -> Triple(15.dp, compassTextGray, 1.5.dp)
                else -> Triple(8.dp, compassTextGray.copy(alpha = 0.5f), 1.dp)
            }

            val end = center + Offset(cosAngle * (radius - tickLengthDp.toPx()), sinAngle * (radius - tickLengthDp.toPx()))
            drawLine(color = tickColor, start = start, end = end, strokeWidth = tickWidthDp.toPx(), cap = StrokeCap.Round)

            if (degree % 30 == 0) {
                val textPos = center + Offset(cosAngle * textRadius, sinAngle * textRadius)
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(textPos.x, textPos.y)
                    rotate(degree.toFloat())

                    val label = when (degree) {
                        0 -> "N"; 45 -> "NE"; 90 -> "E"; 135 -> "SE"; 180 -> "S"; 225 -> "SW"; 270 -> "O"; 315 -> "NW"
                        else -> degree.toString()
                    }
                    val paintToUse = if (degree % 90 == 0) cardinalPaint else textPaint
                    drawText(label, 0f, paintToUse.textSize / 3, paintToUse)
                    restore()
                }
            }
        }
    }
}

@Composable
fun FixedCompassOverlay(modifier: Modifier, accentColor: Color) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            color = accentColor.copy(alpha = 0.8f),
            radius = size.width / 2 * 0.65f,
            style = Stroke(width = 4.dp.toPx())
        )
        val topIndicatorLength = 25.dp.toPx()
        drawLine(
            color = accentColor,
            start = Offset(center.x, 0f),
            end = Offset(center.x, topIndicatorLength),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CenterCompassContent(
    azimuthInt: Int,
    accentColor: Color,
    oncompassBg: Color,
    showLogo: Boolean = true,
    compact: Boolean = false
) {
    val cardinal = when (azimuthInt) {
        in 338..360, in 0..22 -> "N"
        in 23..67 -> "NE"
        in 68..112 -> "E"
        in 113..157 -> "SE"
        in 158..202 -> "S"
        in 203..247 -> "SW"
        in 248..292 -> "O"
        in 293..337 -> "NW"
        else -> ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showLogo) {
        val logoColor = accentColor.copy(alpha = 0.7f)
        Canvas(modifier = Modifier.size(80.dp)) {
            val u = size.width / 100f
            val cx = size.width / 2
            val cy = size.height / 2 + (10f * u)
            val iconPaint = Stroke(width = 3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

            val tower = Path().apply {
                moveTo(cx - 20f * u, cy + 45f * u); lineTo(cx - 6f * u, cy - 5f * u)
                moveTo(cx + 20f * u, cy + 45f * u); lineTo(cx + 6f * u, cy - 5f * u)
                moveTo(cx - 6f * u, cy - 5f * u); lineTo(cx + 6f * u, cy - 5f * u)
                moveTo(cx - 13f * u, cy + 20f * u); lineTo(cx + 13f * u, cy + 20f * u)
                moveTo(cx - 20f * u, cy + 45f * u); lineTo(cx + 13f * u, cy + 20f * u)
                moveTo(cx + 20f * u, cy + 45f * u); lineTo(cx - 13f * u, cy + 20f * u)
            }
            drawPath(tower, logoColor, style = iconPaint)

            val dy = cy - 22f * u; val dr = 6.5f * u
            val diamond = Path().apply { moveTo(cx, dy - dr); lineTo(cx + dr, dy); lineTo(cx, dy + dr); lineTo(cx - dr, dy); close() }
            drawPath(diamond, logoColor, style = iconPaint)
            drawCircle(logoColor, radius = 2.5f * u, center = Offset(cx, dy))

            val rectInner = androidx.compose.ui.geometry.Rect(cx - 17f * u, dy - 17f * u, cx + 17f * u, dy + 17f * u)
            val rectOuter = androidx.compose.ui.geometry.Rect(cx - 29f * u, dy - 29f * u, cx + 29f * u, dy + 29f * u)
            drawArc(logoColor, -40f, 80f, false, topLeft = rectInner.topLeft, size = rectInner.size, style = iconPaint)
            drawArc(logoColor, -40f, 80f, false, topLeft = rectOuter.topLeft, size = rectOuter.size, style = iconPaint)
            drawArc(logoColor, 140f, 80f, false, topLeft = rectInner.topLeft, size = rectInner.size, style = iconPaint)
            drawArc(logoColor, 140f, 80f, false, topLeft = rectOuter.topLeft, size = rectOuter.size, style = iconPaint)
        }

        Spacer(modifier = Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = cardinal,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor, // Applique la couleur dynamique au NW, S, etc.
                modifier = Modifier.padding(bottom = if (compact) 5.dp else 6.dp, end = 4.dp)
            )
            Text(
                text = "$azimuthInt°",
                fontSize = if (compact) 34.sp else 40.sp,
                fontWeight = FontWeight.Light,
                color = oncompassBg
            )
        }
    }
}

data class RadarSite(
    val id: Long,
    val operateurs: List<String>,
    val distance: Float,
    val bearing: Float, // L'angle par rapport au Nord vrai
    val latitude: Double,  // ✅ NOUVEAU
    val longitude: Double  // ✅ NOUVEAU
)

@Composable
fun RadarSitesOverlay(
    modifier: Modifier,
    sites: List<RadarSite>,
    currentRotation: Float,
    accentColor: Color,
    isDark: Boolean,
    oncompassBg: Color,
    compassBg: Color,
    onSiteClick: (RadarSite) -> Unit,
    onClusterClick: (List<RadarSite>) -> Unit
) {
    val density = LocalDensity.current

    val distancePaint = remember(isDark, oncompassBg) {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 11.sp.toPx() }
            color = oncompassBg.toArgb()
            setShadowLayer(4f, 0f, 0f, if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }

    val clusterCountPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 14.sp.toPx() }
            color = android.graphics.Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    val colorMap = getOperatorColors()
    val defaultOp = AppConfig.defaultOperator.value
    val priorityList = getOperatorPriorityList(defaultOp)

    val clusteringThreshold = 15f
    val sortedSites = sites.sortedBy { it.bearing }
    val clusters = mutableListOf<MutableList<RadarSite>>()

    if (sortedSites.isNotEmpty()) {
        var currentCluster = mutableListOf(sortedSites[0])
        clusters.add(currentCluster)

        for (i in 1 until sortedSites.size) {
            val site = sortedSites[i]
            val prevSite = currentCluster.last()

            var bearingDiff = kotlin.math.abs(site.bearing - prevSite.bearing)
            bearingDiff = if (bearingDiff > 180f) 360f - bearingDiff else bearingDiff

            if (bearingDiff <= clusteringThreshold) {
                currentCluster.add(site)
            } else {
                currentCluster = mutableListOf(site)
                clusters.add(currentCluster)
            }
        }
    }

    val currentClusters by rememberUpdatedState(clusters)

    // ✅ NOUVEAU : Fonction mathématique pour calculer la vraie moyenne des angles (Même au Nord)
    fun getAverageBearing(cluster: List<RadarSite>): Float {
        if (cluster.size == 1) return cluster[0].bearing
        var sumSin = 0.0
        var sumCos = 0.0
        for (site in cluster) {
            val rad = Math.toRadians(site.bearing.toDouble())
            sumSin += kotlin.math.sin(rad)
            sumCos += kotlin.math.cos(rad)
        }
        return ((Math.toDegrees(kotlin.math.atan2(sumSin, sumCos)) + 360) % 360).toFloat()
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { tapOffset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val compassRadius = ((kotlin.math.min(size.width, size.height) - 100.dp.toPx()) / 2f)
                        .coerceAtLeast(1f)
                    val drawRadius = compassRadius + 24.dp.toPx()

                    var closestCluster: List<RadarSite>? = null
                    var minDistance = Float.MAX_VALUE

                    for (cluster in currentClusters) {
                        // ✅ CORRECTION : Utilisation de la moyenne circulaire
                        val avgBearing = getAverageBearing(cluster)
                        val angleRad = Math.toRadians((avgBearing - 90).toDouble())
                        val x = center.x + (kotlin.math.cos(angleRad) * drawRadius).toFloat()
                        val y = center.y + (kotlin.math.sin(angleRad) * drawRadius).toFloat()

                        val distanceToDoigt = kotlin.math.hypot(tapOffset.x - x, tapOffset.y - y)
                        if (distanceToDoigt < minDistance) {
                            minDistance = distanceToDoigt
                            closestCluster = cluster
                        }
                    }

                    // Rayon de clic généreux pour les doigts (45 pixels)
                    if (closestCluster != null && minDistance <= 45.dp.toPx()) {
                        if (closestCluster.size == 1) {
                            onSiteClick(closestCluster[0])
                        } else {
                            onClusterClick(closestCluster)
                        }
                    }
                }
            )
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val compassRadius = ((kotlin.math.min(size.width, size.height) - 100.dp.toPx()) / 2f)
            .coerceAtLeast(1f)
        val drawRadius = compassRadius + 24.dp.toPx()

        clusters.forEach { cluster ->
            // ✅ CORRECTION : Utilisation de la moyenne circulaire
            val avgBearing = getAverageBearing(cluster)
            val angleRad = Math.toRadians((avgBearing - 90).toDouble())
            val x = center.x + (kotlin.math.cos(angleRad) * drawRadius).toFloat()
            val y = center.y + (kotlin.math.sin(angleRad) * drawRadius).toFloat()

            if (cluster.size == 1) {
                val site = cluster[0]
                val dotRadius = 8.dp.toPx()
                val rect = androidx.compose.ui.geometry.Rect(x - dotRadius, y - dotRadius, x + dotRadius, y + dotRadius)

                val validOps = site.operateurs
                    .flatMap { OperatorColors.keysFor(it) }
                    .distinct()
                    .sortedBy { priorityList.indexOf(it).takeIf { index -> index >= 0 } ?: 99 }

                rotate(degrees = -currentRotation, pivot = Offset(x, y)) {
                    if (validOps.isEmpty()) drawCircle(color = Color.Gray, radius = dotRadius, center = Offset(x, y))
                    else {
                        drawOperatorSlices(rect, validOps, colorMap)
                    }
                    drawCircle(color = compassBg, radius = dotRadius, center = Offset(x, y), style = Stroke(width = 1.5.dp.toPx()))
                }

                val isMi = AppConfig.distanceUnit.intValue == 1
                val distStr = formatCompassDistance(site.distance, isMi)
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(x, y)
                    rotate(-currentRotation)
                    drawText(distStr, 0f, dotRadius + 14.dp.toPx(), distancePaint)
                    restore()
                }

            } else {
                val dotRadius = 12.dp.toPx()

                drawCircle(color = accentColor, radius = dotRadius, center = Offset(x, y))
                drawCircle(color = Color.White, radius = dotRadius, center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))

                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(x, y)
                    rotate(-currentRotation)
                    drawText("${cluster.size}", 0f, 4.dp.toPx(), clusterCountPaint)
                    restore()
                }
            }
        }
    }
}

@Composable
fun MiniOperatorGrid(operateurs: List<String>) {
    val defaultOp = AppConfig.defaultOperator.value
    val priorityList = getOperatorPriorityList(defaultOp)
    val colorMap = getOperatorColors()

    val emptyColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Canvas(modifier = Modifier.size(24.dp)) {
        val spacing = 2.dp.toPx()
        val cellSize = (size.width - spacing) / 2f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Les 4 positions de la grille
        val positions = listOf(
            Offset(0f, 0f),                               // 1. Haut Gauche
            Offset(cellSize + spacing, 0f),               // 2. Haut Droite
            Offset(0f, cellSize + spacing),               // 3. Bas Gauche
            Offset(cellSize + spacing, cellSize + spacing) // 4. Bas Droite
        )

        val displayOps = priorityList
            .filter { op -> operateurs.flatMap { OperatorColors.keysFor(it) }.contains(op) }
            .take(4)

        (0 until 4).forEach { index ->
            val op = displayOps.getOrNull(index)

            drawRoundRect(
                color = op?.let { colorMap[it] } ?: emptyColor,
                topLeft = positions[index],
                size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                cornerRadius = cornerRadius
            )
        }
    }
}
fun getOperatorPriorityList(defaultOp: String): List<String> {
    val baseOrder = OperatorColors.orderedKeys
    val priorityList = mutableListOf<String>()
    val defOpUpper = defaultOp.uppercase()

    OperatorColors.keyFor(defOpUpper)?.let { priorityList.add(it) }
    baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }
    return priorityList
}

fun getOperatorColors(): Map<String, Color> {
    return OperatorColors.orderedKeys.associateWith { key ->
        Color(OperatorColors.colorArgbForKey(key))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOperatorSlices(
    rect: androidx.compose.ui.geometry.Rect,
    operators: List<String>,
    colorMap: Map<String, Color>
) {
    val sweep = 360f / operators.size.coerceAtLeast(1)
    operators.forEachIndexed { index, op ->
        drawArc(
            color = colorMap[op] ?: Color.Gray,
            startAngle = -90f + index * sweep,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = rect.topLeft,
            size = rect.size
        )
    }
}

fun formatCompassDistance(distanceMeters: Float, isMi: Boolean): String {
    return if (isMi) {
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
}
