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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.ui.components.SupportShareMenu
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import fr.geotower.utils.OperatorColors
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onAntennaClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentView = LocalView.current

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
    val scrollState = rememberScrollState()

    LaunchedEffect(siteId, effectiveHighlightedOperatorKey) {
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
            try {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val photosTemp = mutableListOf<CommunityPhoto>()
                val hasCellularFrPhotos = antennas.any { CommunityDataPreferences.isCellularFrPhotosEnabled(prefs, it.operateur) }
                val hasSignalQuestPhotos = antennas.any { CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, it.operateur) }
                val trueSupportId = physique?.idSupport ?: antennas.firstOrNull()?.idAnfr

                if (!trueSupportId.isNullOrBlank()) {
                    if (hasCellularFrPhotos) {
                        CellularFrApi.getCellularFrPhotos(trueSupportId).forEach { photo ->
                            photosTemp.add(CommunityPhoto(photo.url, "CellularFR", photo.author, photo.uploadedAt))
                        }
                    }

                    if (hasSignalQuestPhotos) {
                        try {
                            val response = fr.geotower.data.api.SignalQuestClient.api.getSitePhotos(
                                authHeader = "Bearer ${fr.geotower.BuildConfig.SQ_API_KEY}",
                                siteId = trueSupportId
                            )
                            if (response.isSuccessful) {
                                response.body()?.data?.forEach { photo ->
                                    photosTemp.add(CommunityPhoto(photo.imageUrl, "Signal Quest", photo.authorName, photo.uploadedAt))
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

    val txtIdCopied = AppStrings.idCopied
    val txtIdUnavailable = AppStrings.idUnavailable
    val txtAddressCopied = AppStrings.addressCopied
    val txtCoordsCopied = AppStrings.coordsCopied
    val txtNoGpsApp = AppStrings.noGpsApp

    val txtSupportDetailTitle = AppStrings.supportDetailTitle
    val txtAddressLabel = AppStrings.addressLabel
    val txtNotSpecified = AppStrings.notSpecified
    val txtGpsLabel = AppStrings.gpsLabel
    val txtSupportHeight = AppStrings.supportHeight
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel
    val txtOperatorsTitle = AppStrings.operatorsTitle
    val txtGeneratedBy = AppStrings.generatedBy
    val txtShareSiteVia = AppStrings.shareSiteVia

    val txtThemeLight = AppStrings.themeLight
    val txtLightModeDesc = AppStrings.lightModeDesc
    val txtThemeDark = AppStrings.themeDark
    val txtDarkModeDesc = AppStrings.darkModeDesc
    val txtIdNumber = AppStrings.idNumber
    val txtSupportNature = AppStrings.supportNature
    val txtBack = AppStrings.back
    val txtIdSupportCopy = AppStrings.idSupportCopy
    val txtAddressCopy = AppStrings.addressCopy
    val txtGpsCoordsCopy = AppStrings.gpsCoordsCopy
    val txtMove = AppStrings.move
    val txtShareConfidentialOption = AppStrings.shareConfidentialOption
    val txtShareConfidentialDesc = AppStrings.shareConfidentialDesc
    val txtGenerateImage = AppStrings.generateImage
    val txtUnknown = AppStrings.unknown

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    fun normalizeSupportOrder(order: List<String>): List<String> {
        val mutableOrder = order.filter { it.isNotBlank() }.toMutableList()
        mutableOrder.remove("open_map")
        val navIndex = mutableOrder.indexOf("nav")
        if (navIndex >= 0) mutableOrder.add(navIndex, "open_map") else mutableOrder.add("open_map")
        return mutableOrder
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

    val pageSupportOrder by remember { mutableStateOf(normalizeSupportOrder(prefs.getString("page_support_order", "map,details,photos,open_map,nav,share,operators")!!.split(","))) }
    val showMap by remember { mutableStateOf(prefs.getBoolean("page_support_map", true)) }
    val showDetails by remember { mutableStateOf(prefs.getBoolean("page_support_details", true)) }
    val showPhotos by remember { mutableStateOf(prefs.getBoolean("page_support_photos", true)) }
    val showOpenMap by remember { mutableStateOf(prefs.getBoolean("page_support_open_map", true)) }
    val showNav by remember { mutableStateOf(prefs.getBoolean("page_support_nav", true)) }
    val showShare by remember { mutableStateOf(prefs.getBoolean("page_support_share", true)) }
    val showOperators by remember { mutableStateOf(prefs.getBoolean("page_support_operators", true)) }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mainBgColor)
                    .padding(top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isSplitScreen) {
                            onCloseSplitScreen()
                        } else {
                            safeBackNavigation.navigateBack()
                        }
                    },
                    enabled = isSplitScreen || !safeBackNavigation.isLocked,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, txtBack, tint = MaterialTheme.colorScheme.onSurface)
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = AppStrings.supportDetailTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                IconButton(
                    onClick = { safeClick { navController.navigate("settings?section=support") } },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = AppStrings.settingsTitle,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { padding ->
        // 🚨 CORRECTION : On applique UNIQUEMENT le padding du haut (top) pour passer sous les boutons en bas !
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().background(mainBgColor)) {
            if (isLoading) {
                LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (antennas.isEmpty()) {
                Text(AppStrings.noDataFound, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
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
                                        focusOperator = effectiveHighlightedOperatorKey
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
                                if (showPhotos) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                                        CommunityPhotosSectionShared(
                                            photos = communityPhotos,
                                            operatorName = null,
                                            operatorNames = antennas.map { it.operateur },
                                            supportNature = physique?.natureSupport, // ✅ LE BON NOM DE VARIABLE
                                            supportOwner = physique?.proprietaire,
                                            bgColor = cardBgColor,
                                            shape = blockShape,
                                            onAddPhotoClick = null
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
                                                Text(AppStrings.openMap, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            "nav" -> {
                                if (showNav) {
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
                                                Text(AppStrings.navToSite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            "share" -> {
                                if (showShare) {
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
                                        priorityOperatorKey = effectiveHighlightedOperatorKey,
                                        onAntennaClick = { idAnfr ->
                                            safeClick {
                                                val cleanId = idAnfr.toLongOrNull() ?: 0L
                                                onAntennaClick(cleanId)
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

        if (showNavigationSheet && antennas.isNotEmpty()) {
            val mainInfo = antennas.first()
            fr.geotower.ui.components.NavigationBottomSheet(
                latitude = mainInfo.latitude,
                longitude = mainInfo.longitude,
                onDismiss = { showNavigationSheet = false },
                sheetState = sheetState,
                useOneUi = useOneUi
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun getLocalLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
}
