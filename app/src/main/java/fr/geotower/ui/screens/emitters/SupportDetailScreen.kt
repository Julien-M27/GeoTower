@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.components.SupportShareMenu
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.CommunityDataSettingsSheet
import fr.geotower.ui.screens.settings.MiniMapSettingsSheet
import fr.geotower.ui.screens.settings.SitePhotosSettingsSheet
import fr.geotower.ui.screens.settings.SupportSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.SupportPagePrefs
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val TAG_SUPPORT_DETAIL = "GeoTower"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    siteId: Long,
    highlightedOperatorKey: String? = null,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {},
    onAntennaClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentView = LocalView.current
    val uiStyle = LocalGeoTowerUiStyle.current

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign

    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi && isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant

    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val buttonShape = oneUiActionButtonShape(useOneUi)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    val safeClick = rememberSafeClick()
    val effectiveHighlightedOperatorKey = OperatorColors.keyFor(highlightedOperatorKey)
    val featureFlags by RemoteFeatureFlags.config
    val canUseSupportPhotos = featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SUPPORT_PHOTOS)
    val canUseSupportNavigation =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SUPPORT_EXTERNAL_NAVIGATION) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_NAVIGATION)
    val canUseSupportShare =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SUPPORT_SHARE) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.SHARE_SUPPORT)

    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "emitters")

    BackHandler(enabled = isSplitScreen || !safeBackNavigation.isLocked) {
        if (isSplitScreen) {
            onCloseSplitScreen()
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    var antennas by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }
    var physique by remember { mutableStateOf<PhysiqueEntity?>(null) }
    var techniquesMap by remember { mutableStateOf<Map<String, TechniqueEntity>>(emptyMap()) }
    var hsDataMap by remember { mutableStateOf<Map<String, fr.geotower.data.models.SiteHsEntity>>(emptyMap()) } // 🚨 NOUVEAU
    var communityPhotos by remember { mutableStateOf<List<CommunityPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var userLocation by remember { mutableStateOf<Location?>(null) }

    var showNavigationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSupportSettingsSheet by remember { mutableStateOf(false) }
    var showSupportMiniMapSettingsSheet by remember { mutableStateOf(false) }
    var showSupportPhotosSettingsSheet by remember { mutableStateOf(false) }
    var showCommunityDataSettingsSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(siteId, effectiveHighlightedOperatorKey, featureFlags) {
        // 1️⃣ CHARGEMENT RAPIDE (Base de données) -> Bloque l'écran une fraction de seconde
        try {
            isLoading = true
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
                val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()

                val initialSearch = repository.getAntennasByExactId(siteId.toString())

                var lat = 0.0
                var lon = 0.0

                if (initialSearch.isNotEmpty()) {
                    var site = initialSearch.find {
                        Math.abs(it.latitude - savedLat) < 0.005 && Math.abs(it.longitude - savedLon) < 0.005
                    }

                    if (site == null) {
                        val userLoc = getLocalLastKnownLocation(context)
                        site = if (userLoc != null) {
                            initialSearch.minByOrNull {
                                val dLat = it.latitude - userLoc.latitude
                                val dLon = it.longitude - userLoc.longitude
                                (dLat * dLat) + (dLon * dLon)
                            }
                        } else {
                            initialSearch.first()
                        }
                    }

                    lat = site!!.latitude
                    lon = site.longitude

                    prefs.edit()
                        .putFloat("clicked_lat", lat.toFloat())
                        .putFloat("clicked_lon", lon.toFloat())
                        .apply()
                } else {
                    lat = savedLat
                    lon = savedLon
                }

                val fetchedAntennas = if (lat != 0.0 && lon != 0.0) {
                    repository.getAntennasInBox(
                        latNorth = lat + 0.0005,
                        lonEast = lon + 0.0005,
                        latSouth = lat - 0.0005,
                        lonWest = lon - 0.0005
                    ).filter { it.latitude.toFloat() == lat.toFloat() && it.longitude.toFloat() == lon.toFloat() }
                } else {
                    emptyList()
                }

                val defaultOp = AppConfig.defaultOperator.value.uppercase()
                val baseOrder = OperatorColors.orderedKeys
                val priorityList = mutableListOf<String>()
                fun addPriorityOperator(key: String?) {
                    if (key != null && key !in priorityList) priorityList.add(key)
                }

                addPriorityOperator(effectiveHighlightedOperatorKey)
                addPriorityOperator(OperatorColors.keyFor(defaultOp))
                baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

                antennas = fetchedAntennas.sortedBy { antenna ->
                    val matchedOp = OperatorColors.keysFor(antenna.operateur)
                        .minByOrNull { key -> priorityList.indexOf(key).takeIf { it >= 0 } ?: 99 }
                    if (matchedOp != null) priorityList.indexOf(matchedOp) else 99
                }

                val techMap = mutableMapOf<String, TechniqueEntity>()
                if (antennas.isNotEmpty()) {
                    physique = repository.getPhysiqueByAnfr(antennas.first().idAnfr).firstOrNull()

                    antennas.forEach { ant ->
                        val tech = repository.getTechniqueByAnfr(ant.idAnfr).firstOrNull()
                        if (tech != null) techMap[ant.idAnfr] = tech
                    }
                }
                techniquesMap = techMap

                // TÉLÉCHARGEMENT DES PANNES
                try {
                    val allHs = repository.getSitesHs()
                    val tempOutageMap = mutableMapOf<String, fr.geotower.data.models.SiteHsEntity>()
                    antennas.forEach { ant ->
                        val hsData = allHs.firstOrNull { hs ->
                            val hsId = hs.idAnfr.toLongOrNull()
                            val antId = ant.idAnfr.toLongOrNull()
                            hsId != null && hsId == antId
                        }
                        if (hsData != null) {
                            tempOutageMap[ant.idAnfr] = hsData
                        }
                    }
                    hsDataMap = tempOutageMap
                } catch (e: Exception) {
                    AppLogger.w(TAG_SUPPORT_DETAIL, "Outage data request failed", e)
                }
            } // Fin du bloc IO Base de données
        } catch (e: Exception) {
            AppLogger.w(TAG_SUPPORT_DETAIL, "Support details request failed", e)
        } finally {
            isLoading = false // 🚨 L'ÉCRAN S'AFFICHE IMMÉDIATEMENT ICI !
        }

        // 2️⃣ CHARGEMENT RÉSEAU DES PHOTOS (En arrière-plan, ne bloque pas l'écran)
        launch(Dispatchers.IO) {
            if (!canUseSupportPhotos) {
                communityPhotos = emptyList()
                return@launch
            }
            try {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val photosTemp = mutableListOf<CommunityPhoto>()
                val hasCellularFrPhotos = antennas.any { CommunityDataPreferences.isCellularFrPhotosEnabled(prefs, it.operateur) }
                val hasSignalQuestPhotos = antennas.any { CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, it.operateur) }
                val trueSupportId = physique?.idSupport ?: antennas.firstOrNull()?.idAnfr

                if (!trueSupportId.isNullOrBlank()) {
                    // CellularFR masqué — voir CellularFrApi.ENABLED
                    if (hasCellularFrPhotos) {
                        CellularFrApi.getCellularFrPhotos(trueSupportId).forEach { photo ->
                            photosTemp.add(
                                CommunityPhoto(
                                    url = photo.url,
                                    communityName = "CellularFR",
                                    author = photo.author,
                                    date = photo.uploadedAt,
                                    sourceId = CommunityDataPreferences.SOURCE_CELLULARFR,
                                    stableId = photo.url
                                )
                            )
                        }
                    }

                    if (hasSignalQuestPhotos) {
                        try {
                            val response = fr.geotower.data.api.SignalQuestClient.api.getSitePhotos(
                                siteId = trueSupportId
                            )
                            if (response.isSuccessful) {
                                response.body()?.data?.forEach { photo ->
                                    val photoOperatorKey = OperatorColors.keyFor(photo.operator)
                                    val photoOperatorLabel = OperatorColors.specForKey(photoOperatorKey)?.label
                                    photosTemp.add(
                                        CommunityPhoto(
                                            url = photo.imageUrl,
                                            communityName = "Signal Quest",
                                            author = photo.authorName,
                                            date = photo.uploadedAt,
                                            exifMetadata = photo.publicMetadata,
                                            sourceId = CommunityDataPreferences.SOURCE_SIGNALQUEST,
                                            stableId = photo.id ?: photo.imageUrl,
                                            operatorKey = photoOperatorKey,
                                            operatorLabel = photoOperatorLabel ?: photo.operator
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) { AppLogger.w(TAG_SUPPORT_DETAIL, "SignalQuest photos request failed", e) }
                    }
                }
                communityPhotos = photosTemp
            } catch (e: Exception) {
                AppLogger.w(TAG_SUPPORT_DETAIL, "Support photos refresh failed", e)
            }
        }
    }

    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                userLocation = location
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            userLocation = getLocalLastKnownLocation(context)
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, locationListener)
            } catch (e: Exception) {
                AppLogger.w(TAG_SUPPORT_DETAIL, "Location updates could not start", e)
            }
        }
        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    val locationData = remember(userLocation, antennas) {
        if (userLocation != null && antennas.isNotEmpty()) {
            val mainInfo = antennas.first()
            val res = FloatArray(2)
            Location.distanceBetween(
                userLocation!!.latitude,
                userLocation!!.longitude,
                mainInfo.latitude,
                mainInfo.longitude,
                res
            )

            val distance = if (res[0] >= 1000) String.format(Locale.US, "%.3f km", res[0] / 1000f) else "${res[0].toInt()} m"
            var bearing = res[1]
            if (bearing < 0) bearing += 360f

            val bearingFormatted = String.format(Locale.US, "%.1f°", bearing)

            // ✅ On retourne maintenant 3 informations (Triple) au lieu de 2
            Triple(distance, bearingFormatted, res[0])
        } else {
            Triple("--", "--", null as Float?)
        }
    }

    val distanceStr = locationData.first
    val bearingStr = locationData.second
    val distanceMeters = locationData.third

    val txtIdCopied = stringResource(R.string.appstrings_id_copied)
    val txtIdUnavailable = stringResource(R.string.appstrings_id_unavailable)
    val txtAddressCopied = stringResource(R.string.appstrings_address_copied)
    val txtCoordsCopied = stringResource(R.string.appstrings_coords_copied)
    val txtNoGpsApp = stringResource(R.string.appstrings_no_gps_app)

    val txtSupportDetailTitle = stringResource(R.string.appstrings_support_detail_title)
    val txtAddressLabel = stringResource(R.string.appstrings_address_label)
    val txtNotSpecified = stringResource(R.string.appstrings_not_specified)
    val txtGpsLabel = stringResource(R.string.appstrings_gps_label)
    val txtSupportHeight = stringResource(R.string.appstrings_support_height)
    val txtDistanceLabel = stringResource(R.string.appstrings_distance_label)
    val txtFromMyPosition = stringResource(R.string.appstrings_from_my_position)
    val txtBearingLabel = stringResource(R.string.appstrings_bearing_label)
    val txtOperatorsTitle = stringResource(R.string.appstrings_operators_title)
    val txtGeneratedBy = stringResource(R.string.appstrings_generated_by)
    val txtShareSiteVia = stringResource(R.string.appstrings_share_site_via)

    val txtThemeLight = stringResource(R.string.appstrings_theme_light)
    val txtLightModeDesc = stringResource(R.string.appstrings_light_mode_desc)
    val txtThemeDark = stringResource(R.string.appstrings_theme_dark)
    val txtDarkModeDesc = stringResource(R.string.appstrings_dark_mode_desc)
    val txtIdNumber = stringResource(R.string.appstrings_id_number)
    val txtSupportNature = stringResource(R.string.appstrings_support_nature)
    val txtIdSupportCopy = stringResource(R.string.appstrings_id_support_copy)
    val txtAddressCopy = stringResource(R.string.appstrings_address_copy)
    val txtGpsCoordsCopy = stringResource(R.string.appstrings_gps_coords_copy)
    val txtMove = stringResource(R.string.appstrings_move)
    val txtShareConfidentialOption = stringResource(R.string.appstrings_share_confidential_option)
    val txtShareConfidentialDesc = stringResource(R.string.appstrings_share_confidential_desc)
    val txtGenerateImage = stringResource(R.string.appstrings_generate_image)
    val txtUnknown = stringResource(R.string.appstrings_unknown)

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    var miniMapDefaultMode by remember {
        mutableStateOf(MiniMapViewMode.fromStorageKey(prefs.getString(SupportPagePrefs.MINI_MAP_MODE, null)))
    }

    fun openMapAt(latitude: Double, longitude: Double) {
        prefs.edit()
            .putFloat("clicked_lat", latitude.toFloat())
            .putFloat("clicked_lon", longitude.toFloat())
            .putFloat("last_map_lat", latitude.toFloat())
            .putFloat("last_map_lon", longitude.toFloat())
            .putFloat("last_map_zoom", 18f)
            .apply()
        navController.navigate("map")
    }

    var pageSupportOrder by remember { mutableStateOf(SupportPagePrefs.order(prefs)) }
    var showMap by remember { mutableStateOf(SupportPagePrefs.map.read(prefs)) }
    var showDetails by remember { mutableStateOf(SupportPagePrefs.details.read(prefs)) }
    val showPhotos by AppConfig.siteShowPhotos
    var showOpenMap by remember { mutableStateOf(SupportPagePrefs.openMap.read(prefs)) }
    var showNav by remember { mutableStateOf(SupportPagePrefs.nav.read(prefs)) }
    var showShare by remember { mutableStateOf(SupportPagePrefs.share.read(prefs)) }
    var showOperators by remember { mutableStateOf(SupportPagePrefs.operators.read(prefs)) }
    val mapFrequencyFilter = FrequencyFilterSelection.fromMapConfig()
    val frequencyMatchedOperatorKeys = remember(antennas, mapFrequencyFilter) {
        if (mapFrequencyFilter.isFullyEnabled) {
            null
        } else {
            antennas
                .filter { mapFrequencyFilter.matchesAntenna(it) }
                .flatMap { OperatorColors.keysFor(it.operateur) }
                .toSet()
        }
    }
    val priorityOperatorKey = effectiveHighlightedOperatorKey ?: frequencyMatchedOperatorKeys?.singleOrNull()

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_support_detail_title),
                onBack = {
                    if (isSplitScreen) {
                        onCloseSplitScreen()
                    } else {
                        safeBackNavigation.navigateBack()
                    }
                },
                backgroundColor = mainBgColor,
                backEnabled = isSplitScreen || !safeBackNavigation.isLocked,
                actions = {
                    IconButton(
                        onClick = { safeClick { showSupportSettingsSheet = true } }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.appstrings_settings_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        // 🚨 CORRECTION : On applique UNIQUEMENT le padding du haut (top) pour passer sous les boutons en bas !
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().background(mainBgColor)) {
            if (isLoading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (antennas.isEmpty()) {
                Text(stringResource(R.string.appstrings_no_data_found), modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
            } else {
                val mainInfo = antennas.first()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState)
                        .verticalScroll(scrollState)
                        // 🚨 AJOUT : Ajoute un espace à la fin du défilement pour ne pas cacher le dernier élément sous les boutons
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    pageSupportOrder.forEach { block ->
                        when (block) {
                            "map" -> {
                                if (showMap) {
                                    fr.geotower.ui.components.SharedMiniMapCard(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        centerLat = mainInfo.latitude,
                                        centerLon = mainInfo.longitude,
                                        mappedAntennas = antennas,
                                        sitesHs = hsDataMap.values.toList(),
                                        blockShape = blockShape,
                                        cardBorder = cardBorder,
                                        onMapReady = { globalMapRef = it },
                                        focusOperator = effectiveHighlightedOperatorKey,
                                        userLocation = userLocation,
                                        defaultViewMode = miniMapDefaultMode,
                                        showViewModeToggle = true
                                    )
                                }
                            }
                            "details" -> {
                                if (showDetails) {
                                    fr.geotower.ui.components.SupportDetailsSection(
                                        mainInfo = mainInfo,
                                        physique = physique,
                                        technique = techniquesMap[mainInfo.idAnfr],
                                        distanceMeters = distanceMeters,
                                        bearingStr = bearingStr,
                                        cardBgColor = cardBgColor,
                                        blockShape = blockShape
                                    )
                                }
                            }
                            "photos" -> {
                                // 🚨 CORRECTION : On retire "&& communityPhotos.isNotEmpty()"
                                if (showPhotos && canUseSupportPhotos) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                                        CommunityPhotosSectionShared(
                                            photos = communityPhotos,
                                            operatorName = null,
                                            operatorNames = antennas.map { it.operateur },
                                            supportNature = physique?.natureSupport, // ✅ LE BON NOM DE VARIABLE
                                            supportOwner = physique?.proprietaire,
                                            bgColor = cardBgColor,
                                            shape = blockShape,
                                            onAddPhotoClick = null,
                                            favoriteScopeId = physique?.idSupport ?: mainInfo.idAnfr,
                                            favoriteSelectionEnabled = true
                                        )
                                    }
                                }
                            }
                            "open_map" -> {
                                if (showOpenMap) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Button(
                                            onClick = { safeClick { openMapAt(mainInfo.latitude, mainInfo.longitude) } },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            shape = buttonShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.appstrings_open_map), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            "nav" -> {
                                if (showNav && canUseSupportNavigation) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Button(
                                            onClick = { safeClick { showNavigationSheet = true } },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            shape = buttonShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(stringResource(R.string.appstrings_nav_to_site), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            "share" -> {
                                if (showShare && canUseSupportShare) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        SupportShareMenu(
                                            siteId = siteId,
                                            antennas = antennas,
                                            physique = physique,
                                            techniquesMap = techniquesMap,
                                            hsDataMap = hsDataMap, // 🚨 ON TRANSMET LA MAP DES PANNES
                                            distanceStr = distanceStr,
                                            bearingStr = bearingStr,
                                            useOneUi = useOneUi,
                                            buttonShape = buttonShape,
                                            globalMapRef = globalMapRef
                                        )
                                    }
                                }
                            }
                            "operators" -> {
                                if (showOperators) {
                                    fr.geotower.ui.components.OperatorsListSection(
                                        antennas = antennas,
                                        techniques = techniquesMap,
                                        hsDataMap = hsDataMap, // 🚨 NOUVEAU
                                        cardBgColor = cardBgColor,
                                        blockShape = blockShape,
                                        useOneUi = useOneUi,
                                        priorityOperatorKey = priorityOperatorKey,
                                        activeOperatorKeys = frequencyMatchedOperatorKeys,
                                        onAntennaClick = { idAnfr ->
                                            safeClick {
                                                onAntennaClick(idAnfr)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showNavigationSheet && antennas.isNotEmpty() && canUseSupportNavigation) {
            val mainInfo = antennas.first()
            fr.geotower.ui.components.NavigationBottomSheet(
                latitude = mainInfo.latitude,
                longitude = mainInfo.longitude,
                onDismiss = { showNavigationSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi
            )
        }

        if (showSupportSettingsSheet) {
            SupportSettingsSheet(
                supportOrder = pageSupportOrder,
                onOrderChange = {
                    pageSupportOrder = SupportPagePrefs.normalizeOrder(it)
                    prefs.edit().putString(SupportPagePrefs.ORDER, pageSupportOrder.joinToString(",")).apply()
                },
                showMap = showMap,
                onMapChange = {
                    showMap = it
                    prefs.edit().putBoolean(SupportPagePrefs.map.key, it).apply()
                },
                showDetails = showDetails,
                onDetailsChange = {
                    showDetails = it
                    prefs.edit().putBoolean(SupportPagePrefs.details.key, it).apply()
                },
                showPhotos = showPhotos,
                onPhotosChange = {
                    AppConfig.siteShowPhotos.value = it
                    prefs.edit().putBoolean("site_show_photos", it).apply()
                },
                showOpenMap = showOpenMap,
                onOpenMapChange = {
                    showOpenMap = it
                    prefs.edit().putBoolean(SupportPagePrefs.openMap.key, it).apply()
                },
                showNav = showNav,
                onNavChange = {
                    showNav = it
                    prefs.edit().putBoolean(SupportPagePrefs.nav.key, it).apply()
                },
                showShare = showShare,
                onShareChange = {
                    showShare = it
                    prefs.edit().putBoolean(SupportPagePrefs.share.key, it).apply()
                },
                showOperators = showOperators,
                onOperatorsChange = {
                    showOperators = it
                    prefs.edit().putBoolean(SupportPagePrefs.operators.key, it).apply()
                },
                onOpenMiniMapSettings = {
                    showSupportSettingsSheet = false
                    showSupportMiniMapSettingsSheet = true
                },
                onOpenPhotosSettings = {
                    showSupportSettingsSheet = false
                    showSupportPhotosSettingsSheet = true
                },
                onDismiss = { showSupportSettingsSheet = false },
                onBack = { showSupportSettingsSheet = false },
                sheetState = pageSettingsSheetState,
                useOneUi = uiStyle.useOneUi,
                bubbleColor = uiStyle.bubbleColor
            )
        }

        if (showSupportMiniMapSettingsSheet) {
            MiniMapSettingsSheet(
                selectedMode = miniMapDefaultMode,
                onModeChange = {
                    miniMapDefaultMode = it
                    prefs.edit().putString(SupportPagePrefs.MINI_MAP_MODE, it.storageKey).apply()
                },
                onDismiss = { showSupportMiniMapSettingsSheet = false },
                onBack = {
                    showSupportMiniMapSettingsSheet = false
                    showSupportSettingsSheet = true
                },
                sheetState = pageSettingsSheetState,
                useOneUi = uiStyle.useOneUi,
                bubbleColor = uiStyle.bubbleColor
            )
        }

        if (showSupportPhotosSettingsSheet) {
            SitePhotosSettingsSheet(
                onDismiss = { showSupportPhotosSettingsSheet = false },
                onBack = {
                    showSupportPhotosSettingsSheet = false
                    showSupportSettingsSheet = true
                },
                photosVisible = showPhotos,
                onPhotosVisibilityChange = {
                    AppConfig.siteShowPhotos.value = it
                    prefs.edit().putBoolean("site_show_photos", it).apply()
                },
                onOpenCommunityDataSettings = {
                    showSupportPhotosSettingsSheet = false
                    showCommunityDataSettingsSheet = true
                }
            )
        }

        if (showCommunityDataSettingsSheet) {
            CommunityDataSettingsSheet(
                onDismiss = { showCommunityDataSettingsSheet = false },
                sheetState = pageSettingsSheetState,
                useOneUi = uiStyle.useOneUi,
                featureId = CommunityDataPreferences.FEATURE_PHOTOS
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun getLocalLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
}
