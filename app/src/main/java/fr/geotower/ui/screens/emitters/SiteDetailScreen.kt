@file:OptIn(ExperimentalMaterial3Api::class)
package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import androidx.activity.compose.BackHandler
import fr.geotower.ui.components.SiteStatusCard
import fr.geotower.ui.components.formatOutageDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: Long,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isReady by remember { mutableStateOf(false) } // ✅ NOUVEAU : État de chargement

    // ✅ LE FIX EST ICI AUSSI : On force la mise à jour GPS sécurisée pour l'antenne
    LaunchedEffect(antennaId) {
        isReady = false
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
                val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()

                // On utilise la recherche stricte
                val antennas = repository.getAntennasByExactId(antennaId.toString())
                if (antennas.isNotEmpty()) {
                    var site = antennas.find {
                        Math.abs(it.latitude - savedLat) < 0.005 && Math.abs(it.longitude - savedLon) < 0.005
                    }

                    // ✅ INTELLIGENCE QR CODE : Secours via GPS
                    if (site == null) {
                        val userLoc = getLocalLastKnownLocation(context)
                        site = if (userLoc != null) {
                            antennas.minByOrNull {
                                val dLat = it.latitude - userLoc.latitude
                                val dLon = it.longitude - userLoc.longitude
                                (dLat * dLat) + (dLon * dLon)
                            }
                        } else {
                            antennas.first()
                        }
                    }

                    prefs.edit()
                        .putFloat("clicked_lat", site!!.latitude.toFloat())
                        .putFloat("clicked_lon", site.longitude.toFloat())
                        .apply()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        isReady = true
    }

    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
        return
    }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val currentView = LocalView.current

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val forceOneUi by AppConfig.forceOneUiTheme

    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val useOneUi = forceOneUi
    val isOled = isOledMode

    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val sheetBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val buttonShape = if (useOneUi) CircleShape else RoundedCornerShape(12.dp)

    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
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

    var antenna by remember { mutableStateOf<LocalisationEntity?>(null) }
    var physique by remember { mutableStateOf<PhysiqueEntity?>(null) }
    var technique by remember { mutableStateOf<TechniqueEntity?>(null) }
    var hsDataMap by remember { mutableStateOf<Map<String, fr.geotower.data.models.SiteHsEntity>>(emptyMap()) } // 🚨 AJOUT
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var communityPhotos by remember { mutableStateOf<List<CommunityPhoto>>(emptyList()) }

    var refreshPhotosTrigger by remember { mutableIntStateOf(0) }
    val completedWorkIds = remember { mutableSetOf<UUID>() }

    val currentSiteId = antenna?.idAnfr ?: ""
    val workInfos by remember(currentSiteId) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow("sq_upload_$currentSiteId")
    }.collectAsState(initial = emptyList())

    LaunchedEffect(workInfos) {
        var needsRefresh = false
        workInfos.forEach { workInfo ->
            if (workInfo.state == WorkInfo.State.SUCCEEDED && !completedWorkIds.contains(workInfo.id)) {
                completedWorkIds.add(workInfo.id)
                needsRefresh = true
            }
        }
        if (needsRefresh) {
            refreshPhotosTrigger++
        }
    }

    var showEnbSheet by remember { mutableStateOf(false) }
    var showCellularFrSheet by remember { mutableStateOf(false) }
    var showNavigationSheet by remember { mutableStateOf(false) }
    var showRncSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult<PickVisualMediaRequest, List<Uri>>(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris ->
            if (uris.isNotEmpty() && antenna != null) {
                val encodedUris = uris.joinToString(",") { Uri.encode(it.toString()) }
                val uploadSiteId = physique?.idSupport ?: antenna!!.idAnfr
                val safeOperator = Uri.encode(antenna!!.operateur ?: "Inconnu")
                val safeAzimuts = Uri.encode(antenna!!.azimuts ?: "")
                navController.navigate("sq_upload/${uploadSiteId}/${safeOperator}?uris=$encodedUris&lat=${antenna!!.latitude}&lon=${antenna!!.longitude}&azimuts=$safeAzimuts")
            }
        }
    )

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCameraUri != null && antenna != null) {
            val encodedUri = Uri.encode(currentCameraUri.toString())
            val uploadSiteId = physique?.idSupport ?: antenna!!.idAnfr
            val safeOperator = Uri.encode(antenna!!.operateur ?: "Inconnu")
            val safeAzimuts = Uri.encode(antenna!!.azimuts ?: "")
            navController.navigate("sq_upload/${uploadSiteId}/${safeOperator}?uris=$encodedUri&lat=${antenna!!.latitude}&lon=${antenna!!.longitude}&azimuts=$safeAzimuts")
        }
    }

    fun createCameraUri(): Uri {
        val tempFile = java.io.File.createTempFile("sq_camera_${System.currentTimeMillis()}_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    // 🚨 MODIFICATION : On déplace "status" entre "address" et "freqs"
    val pageSiteOrder by remember { mutableStateOf(prefs.getString("page_site_order", "operator,bearing_height,map,support_details,photos,panel_heights,ids,nav,share,dates,address,status,freqs,links")!!.split(",")) }
    val showOperator by remember { mutableStateOf(prefs.getBoolean("page_site_operator", true)) }
    val showBearingHeight by remember { mutableStateOf(prefs.getBoolean("page_site_bearing_height", true)) }
    val showMap by remember { mutableStateOf(prefs.getBoolean("page_site_map", true)) }
    val showSupportDetails by remember { mutableStateOf(prefs.getBoolean("page_site_support_details", true)) }
    val showPhotos by remember { mutableStateOf(prefs.getBoolean("page_site_photos", true)) }
    val showPanelHeights by remember { mutableStateOf(prefs.getBoolean("page_site_panel_heights", true)) }
    val showIds by remember { mutableStateOf(prefs.getBoolean("page_site_ids", true)) }
    val showNav by remember { mutableStateOf(prefs.getBoolean("page_site_nav", true)) }
    val showShare by remember { mutableStateOf(prefs.getBoolean("page_site_share", true)) }
    val showDates by remember { mutableStateOf(prefs.getBoolean("page_site_dates", true)) }
    val showAddress by remember { mutableStateOf(prefs.getBoolean("page_site_address", true)) }
    val showFreqs by remember { mutableStateOf(prefs.getBoolean("page_site_freqs", true)) }
    val showLinks by remember { mutableStateOf(prefs.getBoolean("page_site_links", true)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isEnbAppInstalled = remember { isPackageInstalled(context, "fr.enb_analytics.enb4g") }
    val isSignalQuestInstalled = remember { isPackageInstalled(context, "com.sfrmap.android") }
    val isCellularFrInstalled = remember { isPackageInstalled(context, "com.luisbaker.cellularfr") }
    val isRncMobileInstalled = remember { isPackageInstalled(context, "org.rncteam.rncfreemobile") }

    LaunchedEffect(antennaId, refreshPhotosTrigger) {
        val lat = prefs.getFloat("clicked_lat", 0f).toDouble()
        val lon = prefs.getFloat("clicked_lon", 0f).toDouble()

        var localData: LocalisationEntity? = null
        if (lat != 0.0 && lon != 0.0) {
            val box = repository.getAntennasInBox(
                latNorth = lat + 0.0005,
                lonEast = lon + 0.0005,
                latSouth = lat - 0.0005,
                lonWest = lon - 0.0005
            )
            localData = box.firstOrNull { it.latitude.toFloat() == lat.toFloat() && it.longitude.toFloat() == lon.toFloat() && it.idAnfr.toLongOrNull() == antennaId }
                ?: box.firstOrNull { it.latitude.toFloat() == lat.toFloat() && it.longitude.toFloat() == lon.toFloat() }
        }
        antenna = localData

        if (localData != null) {
            physique = repository.getPhysiqueByAnfr(localData.idAnfr).firstOrNull()
            technique = repository.getTechniqueByAnfr(localData.idAnfr).firstOrNull()

            // 🚨 TÉLÉCHARGEMENT DES PANNES
            try {
                val allHs = repository.getSitesHs()
                val tempOutageMap = mutableMapOf<String, fr.geotower.data.models.SiteHsEntity>()
                val hsData = allHs.firstOrNull { hs ->
                    hs.idAnfr.toLongOrNull() == localData.idAnfr.toLongOrNull()
                }
                if (hsData != null) tempOutageMap[localData.idAnfr] = hsData
                hsDataMap = tempOutageMap
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (localData != null && localData.idAnfr.isNotBlank()) {
            val opName = localData.operateur ?: ""
            // ✅ CORRECTION MAJEURE : On utilise le numéro de support physique universel
            val trueSupportId = physique?.idSupport ?: localData.idAnfr

            val photosTemp = mutableListOf<CommunityPhoto>()

            // ✅ Séparation en deux blocs `if` distincts (Plus de `else if`)
            if (opName.contains("ORANGE", true)) {
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

                                    if (relativeUrl.isNotEmpty()) {
                                        photosTemp.add(CommunityPhoto("https://cellularfr.fr$relativeUrl", "CellularFR", author, date))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (opName.contains("SFR", true) || opName.contains("BOUYGUES", true)) {
                try {
                    val response = fr.geotower.data.api.SignalQuestClient.api.getSitePhotos(
                        authHeader = "Bearer ${fr.geotower.BuildConfig.SQ_API_KEY}",
                        siteId = trueSupportId
                    )
                    response.body()?.data?.forEach {
                        photosTemp.add(CommunityPhoto(it.imageUrl, "Signal Quest", it.authorName, it.uploadedAt))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            communityPhotos = photosTemp
        }
    }

    DisposableEffect(Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) { userLocation = location }
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
            } catch (e: Exception) { e.printStackTrace() }
        }
        onDispose { locationManager.removeUpdates(locationListener) }
    }

    val locationData = remember(userLocation, antenna) {
        if (userLocation != null && antenna != null) {
            val res = FloatArray(2)
            Location.distanceBetween(userLocation!!.latitude, userLocation!!.longitude, antenna!!.latitude, antenna!!.longitude, res)
            val distance = if (res[0] >= 1000) String.format(Locale.US, "%.3f km", res[0] / 1000f) else "${res[0].toInt()} m"
            var bearing = res[1]
            if (bearing < 0) bearing += 360f

            // ✅ MODIFICATION : On retourne 3 valeurs (le texte, l'azimut, et la valeur brute en mètres)
            Triple(distance, String.format(Locale.US, "%.1f°", bearing), res[0])
        } else {
            Triple("--", "--", null as Float?)
        }
    }

    val distanceStr = locationData.first
    val bearingStr = locationData.second
    val distanceMeters = locationData.third

    val txtSiteDetailsTitle = AppStrings.siteDetailTitle
    val txtIdCopied = AppStrings.idCopied
    val txtDistanceLabel = AppStrings.distanceLabel
    val txtFromMyPosition = AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel
    val txtSupportHeight = AppStrings.supportHeight
    val txtNavToSite = AppStrings.navToSite
    val txtOpenApp = AppStrings.openApp
    val txtInstallApp = AppStrings.installApp
    val txtMap4G = AppStrings.map4G
    val txtMap5G = AppStrings.map5G
    val txtUnavailable = AppStrings.unavailable
    val txtWhichMap = AppStrings.whichMap
    val txtIdSupportCopy = AppStrings.idSupportCopy

    // ✅ 1. ON PLACE LA FONCTION ICI POUR QU'ELLE SOIT VISIBLE PAR TOUT L'ÉCRAN
    fun handleBackNavigation() {
        if (isSplitScreen) {
            onCloseSplitScreen()
        } else {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                navController.navigate("support_detail/$antennaId") {
                    popUpTo(0)
                }
            }
        }
    }

    // ✅ 2. ON GÈRE LE BOUTON RETOUR PHYSIQUE ICI
    androidx.activity.compose.BackHandler {
        handleBackNavigation()
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(mainBgColor).padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        safeClick {
                            handleBackNavigation() // ✅ 3. ON APPELLE NOTRE FONCTION DANS LA TOPBAR
                        }
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppStrings.back, tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = txtSiteDetailsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clip(CircleShape).clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(txtIdSupportCopy, antennaId.toString()))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, "$txtIdCopied : $antennaId", Toast.LENGTH_SHORT).show()
                        }.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { padding ->
        if (antenna == null) {
            Box(Modifier.fillMaxSize().padding(padding).background(mainBgColor), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val info = antenna!!
            val scrollState = rememberScrollState()

            val opColor = getOperatorColor(info.operateur)
            val opNameUrl = when {
                info.operateur?.contains("ORANGE", true) == true -> "orange"
                info.operateur?.contains("FREE", true) == true -> "free"
                info.operateur?.contains("BOUYGUES", true) == true -> "bytel"
                info.operateur?.contains("SFR", true) == true -> "sfr"
                else -> ""
            }

            if (showEnbSheet && opNameUrl.isNotEmpty()) {
                ModalBottomSheet(onDismissRequest = { showEnbSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("eNB-Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(txtWhichMap, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = txtMap4G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_enbanalytics, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showEnbSheet = false
                                uriHandler.openUri("https://enb-analytics.fr/analytics_${opNameUrl}.html")
                            }
                            val has5G = info.frequences?.contains("5G", ignoreCase = true) == true
                            CommunityCard(title = txtMap5G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_enbanalytics, isEnabled = has5G, modifier = Modifier.weight(1f)) {
                                if (has5G) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; uriHandler.openUri("https://enb-analytics.fr/analytics_nr_${opNameUrl}.html") }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppLauncherButton(isInstalled = isEnbAppInstalled, appName = "eNB-Analytics", txtOpen = txtOpenApp, txtInstall = txtInstallApp, useOneUi = useOneUi) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; if (isEnbAppInstalled) launchApp(context, "fr.enb_analytics.enb4g") else uriHandler.openUri("https://play.google.com/store/apps/details?id=fr.enb_analytics.enb4g") }
                    }
                }
            }

            if (showCellularFrSheet) {
                val supportId = physique?.idSupport ?: info.idAnfr // ✅ VRAI ID
                ModalBottomSheet(onDismissRequest = { showCellularFrSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CellularFR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(AppStrings.openOn, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = AppStrings.website, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cellularfr, isEnabled = supportId.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); showCellularFrSheet = false; uriHandler.openUri("https://cellularfr.fr/site-details.html?siteId=$supportId")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppLauncherButton(isInstalled = isCellularFrInstalled, appName = "CellularFR", txtOpen = txtOpenApp, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); showCellularFrSheet = false; if (isCellularFrInstalled) launchApp(context, "com.luisbaker.cellularfr") else uriHandler.openUri("https://play.google.com/store/apps/details?id=com.luisbaker.cellularfr")
                        }
                    }
                }
            }

            if (showRncSheet) {
                ModalBottomSheet(onDismissRequest = { showRncSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RNC Mobile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(AppStrings.openOn, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = AppStrings.website, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_rncmobile, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; uriHandler.openUri("https://rncmobile.net/site/${info.latitude},${info.longitude}")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppLauncherButton(isInstalled = isRncMobileInstalled, appName = "RNC Mobile", txtOpen = txtOpenApp, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; if (isRncMobileInstalled) launchApp(context, "org.rncteam.rncfreemobile") else uriHandler.openUri("https://play.google.com/store/apps/details?id=org.rncteam.rncfreemobile")
                        }
                    }
                }
            }

            if (showNavigationSheet) {
                fr.geotower.ui.components.NavigationBottomSheet(latitude = info.latitude, longitude = info.longitude, onDismiss = { showNavigationSheet = false }, sheetState = sheetState, useOneUi = useOneUi)
            }

            Column(
                modifier = Modifier.padding(padding).fillMaxSize().background(mainBgColor).antennaFadingEdge(scrollState).verticalScroll(scrollState).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val formattedAzimuths = remember(info.azimuts) {
                    if (info.azimuts.isNullOrBlank()) ""
                    else {
                        val angles = info.azimuts?.split(",")?.mapNotNull { it.substringBefore("°").trim().toIntOrNull() }?.map { if (it == 360) 0 else it }?.distinct()?.sorted() ?: emptyList()
                        if (angles.isNotEmpty()) angles.joinToString("° - ") + "°" else ""
                    }
                }

                pageSiteOrder.forEach { block ->
                    when (block) {
                        "status" -> if (AppConfig.siteShowStatus.value) {
                            val hsEntity = hsDataMap.values.firstOrNull()
                            val isOutage = hsEntity != null
                            val outageText = hsEntity?.let { fr.geotower.ui.components.formatOutageDetails(it) }

                            // 1. Quelles technologies sont physiquement sur l'antenne ?
                            val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: info.frequences ?: ""
                            val has2G = rawTechs.contains("2G", ignoreCase = true)
                            val has3G = rawTechs.contains("3G", ignoreCase = true)
                            val has4G = rawTechs.contains("4G", ignoreCase = true)
                            val has5G = rawTechs.contains("5G", ignoreCase = true)

                            // 2. Lecture de l'état individuel précis dans la DB (details_frequences)
                            val detailsStr = technique?.detailsFrequences ?: ""
                            val globalStatut = technique?.statut ?: ""
                            val globalIsProject = globalStatut.contains("Projet", ignoreCase = true)

                            fun isTechPlanned(keywords: List<String>): Boolean {
                                if (detailsStr.isBlank()) return globalIsProject // Sécurité si base vide

                                val lines = detailsStr.split("\n").filter { line ->
                                    keywords.any { k -> line.contains(k, ignoreCase = true) }
                                }
                                if (lines.isEmpty()) return globalIsProject // Sécurité si techno introuvable

                                // 🚨 LA MAGIE OPÈRE ICI :
                                // La techno est en projet SI ET SEULEMENT SI TOUTES ses fréquences sont en projet
                                // (S'il y a au moins un "En service", elle est considérée comme fonctionnelle)
                                return lines.all { it.contains("Projet", ignoreCase = true) }
                            }

                            val is2gProject = has2G && isTechPlanned(listOf("GSM", "2G"))
                            val is3gProject = has3G && isTechPlanned(listOf("UMTS", "3G"))
                            val is4gProject = has4G && isTechPlanned(listOf("LTE", "4G"))
                            val is5gProject = has5G && isTechPlanned(listOf("NR", "5G"))

                            // 3. Le site entier est-il en projet ? (Seulement si TOUTES les technos présentes sont en projet)
                            val totalTechs = listOf(has2G, has3G, has4G, has5G).count { it }
                            val projectTechs = listOf(is2gProject, is3gProject, is4gProject, is5gProject).count { it }
                            val isEntirelyProject = totalTechs > 0 && totalTechs == projectTechs

                            // 4. On croise la présence avec l'état de la panne réelle ET le projet DB
                            val realTechStatus = mapOf(
                                "2G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = if (has2G) hsEntity?.let { it.voix2g != "HS" } ?: true else null,
                                    isSmsOk = if (has2G) hsEntity?.let { it.voix2g != "HS" } ?: true else null,
                                    isInternetOk = if (has2G) hsEntity?.let { it.data2g != "HS" } ?: true else null,
                                    isProject = is2gProject
                                ),
                                "3G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = if (has3G) hsEntity?.let { it.voix3g != "HS" } ?: true else null,
                                    isSmsOk = if (has3G) hsEntity?.let { it.voix3g != "HS" } ?: true else null,
                                    isInternetOk = if (has3G) hsEntity?.let { it.data3g != "HS" } ?: true else null,
                                    isProject = is3gProject
                                ),
                                "4G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = if (has4G) hsEntity?.let { it.voix4g != "HS" } ?: true else null,
                                    isSmsOk = if (has4G) hsEntity?.let { it.voix4g != "HS" } ?: true else null,
                                    isInternetOk = if (has4G) hsEntity?.let { it.data4g != "HS" } ?: true else null,
                                    isProject = is4gProject
                                ),
                                "5G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = if (has5G) hsEntity?.let { it.voix5g != "HS" } ?: true else null,
                                    isSmsOk = if (has5G) hsEntity?.let { it.voix5g != "HS" } ?: true else null,
                                    isInternetOk = if (has5G) hsEntity?.let { it.data5g != "HS" } ?: true else null,
                                    isProject = is5gProject
                                )
                            )

                            fr.geotower.ui.components.SiteStatusCard(
                                isProjectSite = isEntirelyProject, // Ne s'affiche en jaune que si TOUT le site est en projet
                                isOutage = isOutage,
                                outageText = outageText,
                                cardBgColor = cardBgColor,
                                blockShape = blockShape,
                                techStatus = realTechStatus
                            )
                        }
                        "operator" -> {
                            if (showOperator) {
                                Card(modifier = Modifier.fillMaxWidth(), shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val opNameDisplay = info.operateur ?: "Inconnu"
                                        val logoRes = getDetailLogoRes(opNameDisplay)

                                        if (logoRes != null) { Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))) }
                                        else { Box(modifier = Modifier.size(72.dp).background(getOperatorColor(opNameDisplay), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(text = opNameDisplay.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp) } }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(text = opNameDisplay, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: info.frequences
                                            val realTechs = formatTechnologies(rawTechs, AppStrings.unknown)
                                            Text(text = realTechs, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        "bearing_height" -> {
                            if (showBearingHeight) {
                                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    val rotation = bearingStr.replace("°", "").toFloatOrNull() ?: 0f
                                    Card(modifier = Modifier.weight(1f).fillMaxHeight(), shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
                                        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                            Text(txtBearingLabel.replace(" : ", ""), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                            Spacer(Modifier.height(8.dp))
                                            Icon(Icons.Default.Navigation, null, Modifier.size(40.dp).rotate(rotation), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(8.dp))
                                            Text(bearingStr, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Card(modifier = Modifier.weight(1f).fillMaxHeight(), shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
                                        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                            Text(txtSupportHeight.replace(" : ", ""), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                            Spacer(Modifier.height(8.dp))
                                            Icon(Icons.Default.VerticalAlignTop, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(8.dp))
                                            Text("${physique?.hauteur ?: "--"} m", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        "map" -> {
                            if (showMap) {
                                val mappedAntennas = remember(info) { listOf(info) }
                                fr.geotower.ui.components.SharedMiniMapCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    centerLat = info.latitude,
                                    centerLon = info.longitude,
                                    mappedAntennas = mappedAntennas,
                                    sitesHs = hsDataMap.values.toList(),
                                    blockShape = blockShape,
                                    cardBorder = cardBorder,
                                    onMapReady = { globalMapRef = it },
                                    focusOperator = info.operateur
                                )
                            }
                        }
                        "support_details" -> {
                            if (showSupportDetails) {
                                fr.geotower.ui.components.SiteSupportDetailsBlock(
                                    info = info,
                                    physique = physique,
                                    distanceMeters = distanceMeters,
                                    bearingStr = bearingStr,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape
                                )
                            }
                        }
                        "photos" -> {
                            val opName = info.operateur ?: ""
                            val canUpload = opName.contains("SFR", true) || opName.contains("BOUYGUES", true)
                            if (showPhotos && info.idAnfr.isNotBlank() && (communityPhotos.isNotEmpty() || canUpload)) {
                                CommunityPhotosSectionShared(
                                    photos = communityPhotos,
                                    operatorName = opName,
                                    bgColor = cardBgColor,
                                    shape = blockShape,
                                    onAddPhotoClick = { safeClick { showImageSourceDialog = true } }
                                )
                            }
                        }
                        "panel_heights" -> { if (showPanelHeights) fr.geotower.ui.components.SitePanelHeightsBlock(info = info, cardBgColor = cardBgColor, blockShape = blockShape) }
                        "ids" -> {
                            if (showIds) {
                                fr.geotower.ui.components.SiteIdentifiersBlock(
                                    info = info,
                                    idSupport = physique?.idSupport,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape
                                )
                            }
                        }
                        "nav" -> {
                            if (showNav) {
                                Button(onClick = { safeClick { showNavigationSheet = true } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = buttonShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(txtNavToSite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        "share" -> {
                            if (showShare) {
                                fr.geotower.ui.components.AntennaShareMenu(
                                    info = info,
                                    physique = physique,
                                    technique = technique,
                                    hsDataMap = hsDataMap,
                                    distanceStr = distanceStr,
                                    bearingStr = bearingStr,
                                    useOneUi = useOneUi,
                                    buttonShape = buttonShape,
                                    globalMapRef = globalMapRef,
                                    communityPhotosSize = communityPhotos.size
                                )
                            }
                        }
                        "dates" -> {
                            if (showDates) {
                                fr.geotower.ui.components.SiteDatesBlock(
                                    info = info,
                                    technique = technique,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape
                                )
                            }
                        }
                        "address" -> {
                            if (showAddress) {
                                fr.geotower.ui.components.SiteAddressBlock(
                                    info = info,
                                    technique = technique,
                                    distanceStr = distanceStr,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape
                                )
                            }
                        }                        "freqs" -> { if (showFreqs) fr.geotower.ui.components.SiteFrequenciesBlock(info = info, technique = technique, formattedAzimuths = formattedAzimuths, cardBgColor = cardBgColor, blockShape = blockShape) }
                        "links" -> {
                            if (showLinks && opNameUrl.isNotEmpty()) {
                                fr.geotower.ui.components.SiteExternalLinksBlock(info = info, cardBgColor = cardBgColor, blockShape = blockShape, buttonShape = buttonShape, isSignalQuestInstalled = isSignalQuestInstalled, onShowCellularFr = { showCellularFrSheet = true }, onShowRnc = { showRncSheet = true }, onShowEnb = { showEnbSheet = true })
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }

            if (showImageSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showImageSourceDialog = false },
                    shape = blockShape,
                    containerColor = sheetBgColor,
                    title = { Text(AppStrings.get("Ajouter des photos", "Add photos", "Añadir fotos"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    safeClick {
                                        showImageSourceDialog = false
                                        val uri = createCameraUri()
                                        currentCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = buttonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PhotoCamera, null)
                                Spacer(Modifier.width(8.dp))
                                Text(AppStrings.get("Appareil photo", "Camera", "Cámara"), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    safeClick {
                                        showImageSourceDialog = false
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = buttonShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null)
                                Spacer(Modifier.width(8.dp))
                                Text(AppStrings.get("Galerie", "Gallery", "Galería"), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }
}

@Composable
private fun AppLauncherButton(isInstalled: Boolean, appName: String, txtOpen: String, txtInstall: String, useOneUi: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = if (useOneUi) CircleShape else RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (isInstalled) Icons.Default.Launch else Icons.Default.Download, null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(if (isInstalled) "$txtOpen $appName" else "$txtInstall $appName", fontWeight = FontWeight.Bold) }
    }
}

private fun isPackageInstalled(context: Context, pkg: String): Boolean = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
private fun launchApp(context: Context, pkg: String) { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } }

private fun Modifier.antennaFadingEdge(scrollState: androidx.compose.foundation.ScrollState): Modifier {
    if (!AppConfig.isBlurEnabled.value) return this
    return this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent(); val hPx = 80.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - (scrollState.value / hPx).coerceIn(0f, 1f)), Color.Black), 0f, hPx), blendMode = BlendMode.DstIn)
        drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - ((scrollState.maxValue - scrollState.value) / hPx).coerceIn(0f, 1f))), size.height - hPx, size.height), blendMode = BlendMode.DstIn)
    }
}

@Composable
private fun CommunityCard(title: String, txtUnavailable: String, opColor: Color, iconRes: Int? = null, modifier: Modifier = Modifier, isEnabled: Boolean = true, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier.height(120.dp).clickable(enabled = isEnabled, onClick = onClick), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (isEnabled) SolidColor(opColor) else SolidColor(Color.Gray.copy(0.3f)))) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (iconRes != null) {
                Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (isEnabled) opColor else Color.Gray.copy(0.5f)), contentAlignment = Alignment.Center) { Text(title.takeLast(2), color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(12.dp)); Text(if (isEnabled) title else txtUnavailable, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun getOperatorColor(name: String?): Color = when {
    name?.contains("ORANGE", true) == true -> Color(0xFFFF6600)
    name?.contains("SFR", true) == true -> Color(0xFFE2001A)
    name?.contains("FREE", true) == true -> Color(0xFF757575)
    name?.contains("BOUYGUES", true) == true -> Color(0xFF00295F)
    else -> Color.Gray
}

fun getDetailLogoRes(opName: String?): Int? = when {
    opName?.contains("ORANGE", true) == true -> R.drawable.logo_orange
    opName?.contains("SFR", true) == true -> R.drawable.logo_sfr
    opName?.contains("BOUYGUES", true) == true -> R.drawable.logo_bouygues
    opName?.contains("FREE", true) == true -> R.drawable.logo_free
    else -> null
}

fun formatTechnologies(tech: String?, txtUnknown: String): String = tech?.split(Regex("[/,\\-]"))?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }?.sortedDescending()?.joinToString(" - ") ?: txtUnknown

@SuppressLint("MissingPermission")
private fun getLocalLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
}

fun formatDateToFrench(dateStr: String?): String {
    if (dateStr.isNullOrBlank() || dateStr == "-") return "-"
    return try {
        val cleanDate = dateStr.take(10)
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val date = inputFormat.parse(cleanDate)
        if (date != null) outputFormat.format(date) else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()