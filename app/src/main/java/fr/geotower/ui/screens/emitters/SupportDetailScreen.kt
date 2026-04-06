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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.ui.components.SupportShareMenu
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.util.Locale
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    siteId: Long,
    onAntennaClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentView = LocalView.current

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val forceOneUi by AppConfig.forceOneUiTheme

    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }

    val useOneUi = forceOneUi
    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi && isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant

    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val buttonShape = if (useOneUi) CircleShape else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 700L

    fun safeClick(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }

    fun handleBackNavigation() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate("emitters") { popUpTo(0) }
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    var antennas by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }
    var physique by remember { mutableStateOf<PhysiqueEntity?>(null) }
    var techniquesMap by remember { mutableStateOf<Map<String, TechniqueEntity>>(emptyMap()) }
    var communityPhotos by remember { mutableStateOf<List<CommunityPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var userLocation by remember { mutableStateOf<Location?>(null) }

    var showNavigationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    LaunchedEffect(siteId) {
        try {
            isLoading = true

            val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
            val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
            val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()

            val initialSearch = repository.getAntennasByExactId(siteId.toString())

            var lat = 0.0
            var lon = 0.0

            if (initialSearch.isNotEmpty()) {
                // 1. On cherche d'abord via la mémoire (quand on vient de la carte)
                var site = initialSearch.find {
                    Math.abs(it.latitude - savedLat) < 0.005 && Math.abs(it.longitude - savedLon) < 0.005
                }

                // 2. ✅ INTELLIGENCE QR CODE : Si on ne vient pas de la carte, on prend l'antenne la plus proche du GPS !
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

                // On met à jour la mémoire
                prefs.edit()
                    .putFloat("clicked_lat", lat.toFloat())
                    .putFloat("clicked_lon", lon.toFloat())
                    .apply()
            } else {
                lat = savedLat
                lon = savedLon
            }

            var fetchedAntennas = if (lat != 0.0 && lon != 0.0) {
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
            val baseOrder = listOf("ORANGE", "BOUYGUES", "SFR", "FREE")
            val priorityList = mutableListOf<String>()

            if (defaultOp != "AUCUN" && baseOrder.any { defaultOp.contains(it) }) {
                priorityList.add(baseOrder.first { defaultOp.contains(it) })
            }
            baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

            antennas = fetchedAntennas.sortedBy { antenna ->
                val name = (antenna.operateur ?: "").uppercase()
                val matchedOp = priorityList.firstOrNull { name.contains(it) }
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

            val photosTemp = mutableListOf<CommunityPhoto>()
            val hasOrange = antennas.any { (it.operateur ?: "").contains("ORANGE", true) }
            val hasSfrOrBouygues = antennas.any { (it.operateur ?: "").contains("SFR", true) || (it.operateur ?: "").contains("BOUYGUES", true) }

            // ✅ CORRECTION MAJEURE : On utilise le VRAI ID du Support
            val trueSupportId = physique?.idSupport ?: antennas.firstOrNull()?.idAnfr

            if (!trueSupportId.isNullOrBlank()) {
                if (hasOrange) {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val apiUrl = java.net.URL("https://cellularfr.fr/api/photos?siteId=$trueSupportId")
                            val connection = apiUrl.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.connect()
                            if (connection.responseCode == 200) {
                                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                                val jsonObject = org.json.JSONObject(jsonString)
                                val photosArray = jsonObject.optJSONArray("photos")
                                if (photosArray != null) {
                                    for (i in 0 until photosArray.length()) {
                                        val photoObj = photosArray.getJSONObject(i)
                                        val relativeUrl = photoObj.optString("url", "")
                                        val author = if (photoObj.isNull("nickname")) null else photoObj.optString("nickname")
                                        val date = if (photoObj.isNull("uploadDate")) null else photoObj.optString("uploadDate")
                                        if (relativeUrl.isNotEmpty()) photosTemp.add(CommunityPhoto("https://cellularfr.fr$relativeUrl", "CellularFR", author, date))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                if (hasSfrOrBouygues) {
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
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            communityPhotos = photosTemp

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
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
                e.printStackTrace()
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
    val txtNavWith = AppStrings.navWith
    val txtUnknown = AppStrings.unknown

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    val pageSupportOrder by remember { mutableStateOf(prefs.getString("page_support_order", "map,details,photos,nav,share,operators")!!.split(",")) }
    val showMap by remember { mutableStateOf(prefs.getBoolean("page_support_map", true)) }
    val showDetails by remember { mutableStateOf(prefs.getBoolean("page_support_details", true)) }
    val showPhotos by remember { mutableStateOf(prefs.getBoolean("page_support_photos", true)) }
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
                    onClick = { safeClick { handleBackNavigation() } },
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

                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(mainBgColor)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (antennas.isEmpty()) {
                Text(AppStrings.noDataFound, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
            } else {
                val mainInfo = antennas.first()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .siteDetailFadingEdge(scrollState)
                        .verticalScroll(scrollState)
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
                                        blockShape = blockShape,
                                        cardBorder = cardBorder,
                                        onMapReady = { globalMapRef = it }
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
                                if (showPhotos && communityPhotos.isNotEmpty()) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                                        CommunityPhotosSectionShared(
                                            photos = communityPhotos,
                                            operatorName = null,
                                            bgColor = cardBgColor,
                                            shape = blockShape,
                                            onAddPhotoClick = null
                                        )
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
                                        cardBgColor = cardBgColor,
                                        blockShape = blockShape,
                                        useOneUi = useOneUi,
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

private fun Modifier.siteDetailFadingEdge(scrollState: ScrollState): Modifier {
    if (!AppConfig.isBlurEnabled.value) return this
    val fadeHeight = 80.dp
    return this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val hPx = fadeHeight.toPx()
        val topAlpha = (scrollState.value.toFloat() / hPx).coerceIn(0f, 1f)
        drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 1f - topAlpha), Color.Black), startY = 0f, endY = hPx), blendMode = BlendMode.DstIn)
        val bottomAlpha = ((scrollState.maxValue - scrollState.value).toFloat() / hPx).coerceIn(0f, 1f)
        drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomAlpha)), startY = size.height - hPx, endY = size.height), blendMode = BlendMode.DstIn)
    }
}

@SuppressLint("MissingPermission")
private fun getLocalLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
}