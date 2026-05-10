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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.upload.SignalQuestUploadRules
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import fr.geotower.utils.OperatorColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import fr.geotower.ui.components.SpeedtestCard

private const val TAG_SITE_DETAIL = "GeoTower"
private const val TAG_SPEEDTEST = "GeoTowerUpload"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SiteDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: Long,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {},
    onOpenElevationProfile: ((String) -> Unit)? = null,
    onOpenThroughputCalculator: ((String) -> Unit)? = null
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
            } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "Site selection restore failed", e) }
        }
        isReady = true
    }

    if (!isReady) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val haptic = LocalHapticFeedback.current
    rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LocalView.current

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

    // --- ÉTATS POUR LE SPEEDTEST ---
    var speedtestData by remember { mutableStateOf<fr.geotower.data.api.SqSpeedtestData?>(null) }
    var isSpeedtestLoading by remember { mutableStateOf(false) }

    var technique by remember { mutableStateOf<TechniqueEntity?>(null) }
    var hsDataMap by remember { mutableStateOf<Map<String, fr.geotower.data.models.SiteHsEntity>>(emptyMap()) } // 🚨 AJOUT
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var communityPhotos by remember { mutableStateOf<List<CommunityPhoto>>(emptyList()) }

    var refreshPhotosTrigger by remember { mutableIntStateOf(0) }
    val completedWorkIds = remember { mutableSetOf<UUID>() }

    val currentSiteId = antenna?.idAnfr ?: ""
    val unknownText = AppStrings.unknown
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
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = SignalQuestUploadRules.MAX_PHOTOS),
        onResult = { uris ->
            if (uris.isNotEmpty() && antenna != null) {
                val draftId = SignalQuestUploadDraftStore.put(uris.map { it.toString() })
                val uploadSiteId = physique?.idSupport ?: antenna!!.idAnfr
                val safeOperator = Uri.encode(antenna!!.operateur ?: unknownText)
                val safeAzimuts = Uri.encode(antenna!!.azimuts ?: "")
                navController.navigate("sq_upload/${uploadSiteId}/${safeOperator}?draftId=$draftId&lat=${antenna!!.latitude}&lon=${antenna!!.longitude}&azimuts=$safeAzimuts")
            }
        }
    )

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCameraUri != null && antenna != null) {
            val draftId = SignalQuestUploadDraftStore.put(listOf(currentCameraUri.toString()))
            val uploadSiteId = physique?.idSupport ?: antenna!!.idAnfr
            val safeOperator = Uri.encode(antenna!!.operateur ?: unknownText)
            val safeAzimuts = Uri.encode(antenna!!.azimuts ?: "")
            navController.navigate("sq_upload/${uploadSiteId}/${safeOperator}?draftId=$draftId&lat=${antenna!!.latitude}&lon=${antenna!!.longitude}&azimuts=$safeAzimuts")
        }
    }

    fun createCameraUri(): Uri {
        return SignalQuestUploadQueue.createCameraUri(context)
    }

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)

    fun normalizeSiteOrder(order: List<String>): List<String> {
        val mutableOrder = order.filter { it.isNotBlank() }.toMutableList()
        if (!mutableOrder.contains("speedtest")) {
            val photosIndex = mutableOrder.indexOf("photos")
            if (photosIndex >= 0) mutableOrder.add(photosIndex + 1, "speedtest") else mutableOrder.add("speedtest")
        }
        if (!mutableOrder.contains("open_map")) {
            val elevationProfileIndex = mutableOrder.indexOf("elevation_profile")
            if (elevationProfileIndex >= 0) {
                mutableOrder.add(elevationProfileIndex + 1, "open_map")
            } else {
                val supportDetailsIndex = mutableOrder.indexOf("support_details")
                if (supportDetailsIndex >= 0) mutableOrder.add(supportDetailsIndex + 1, "open_map") else mutableOrder.add("open_map")
            }
        }
        if (!mutableOrder.contains("elevation_profile")) {
            val openMapIndex = mutableOrder.indexOf("open_map")
            if (openMapIndex >= 0) mutableOrder.add(openMapIndex, "elevation_profile") else mutableOrder.add("elevation_profile")
        }
        if (!mutableOrder.contains("throughput_calculator")) {
            val elevationProfileIndex = mutableOrder.indexOf("elevation_profile")
            val openMapIndex = mutableOrder.indexOf("open_map")
            when {
                elevationProfileIndex >= 0 -> mutableOrder.add(elevationProfileIndex + 1, "throughput_calculator")
                openMapIndex >= 0 -> mutableOrder.add(openMapIndex, "throughput_calculator")
                else -> mutableOrder.add("throughput_calculator")
            }
        }
        val openMapIndex = mutableOrder.indexOf("open_map")
        val elevationProfileIndex = mutableOrder.indexOf("elevation_profile")
        if (openMapIndex >= 0 && elevationProfileIndex >= 0 && openMapIndex < elevationProfileIndex) {
            mutableOrder.remove("elevation_profile")
            mutableOrder.remove("open_map")
            mutableOrder.add(openMapIndex, "elevation_profile")
            mutableOrder.add(openMapIndex + 1, "open_map")
        }
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
        if (isSplitScreen) onCloseSplitScreen()
        navController.navigate("map")
    }
    val openElevationProfile = onOpenElevationProfile ?: { id: String ->
        navController.navigate("elevation_profile/$id")
    }
    val openThroughputCalculator = onOpenThroughputCalculator ?: { id: String ->
        navController.navigate("throughput_calculator/$id")
    }

    // 🚨 MODIFICATION : L'ordre par défaut (photos, speedtest, nav, share...)
    val pageSiteOrder by remember {
        mutableStateOf(
            normalizeSiteOrder(
                (prefs.getString("page_site_order", "operator,bearing_height,map,support_details,elevation_profile,throughput_calculator,open_map,photos,speedtest,nav,share,panel_heights,ids,dates,address,status,freqs,links") ?: "operator,bearing_height,map,support_details,elevation_profile,throughput_calculator,open_map,photos,speedtest,nav,share,panel_heights,ids,dates,address,status,freqs,links")
                    .split(",")
            )
        )
    }
    val showOperator by remember { mutableStateOf(prefs.getBoolean("page_site_operator", true)) }
    val showBearingHeight by remember { mutableStateOf(prefs.getBoolean("page_site_bearing_height", true)) }
    val showMap by remember { mutableStateOf(prefs.getBoolean("page_site_map", true)) }
    val showSupportDetails by remember { mutableStateOf(prefs.getBoolean("page_site_support_details", true)) }
    val showPhotos by remember { mutableStateOf(prefs.getBoolean("page_site_photos", true)) }
    val showPanelHeights by remember { mutableStateOf(prefs.getBoolean("page_site_panel_heights", true)) }
    val showIds by remember { mutableStateOf(prefs.getBoolean("page_site_ids", true)) }
    val showOpenMap by remember { mutableStateOf(prefs.getBoolean("page_site_open_map", true)) }
    val showElevationProfile by remember { mutableStateOf(prefs.getBoolean("page_site_elevation_profile", true)) }
    val showThroughputCalculator by remember { mutableStateOf(prefs.getBoolean("page_site_throughput_calculator", true)) }
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
            } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "Outage data request failed", e) }
        }

        if (localData != null && localData.idAnfr.isNotBlank()) {
            val opName = localData.operateur ?: ""
            // ✅ CORRECTION MAJEURE : On utilise le numéro de support physique universel
            val trueSupportId = physique?.idSupport ?: localData.idAnfr

            val photosTemp = mutableListOf<CommunityPhoto>()

            // ✅ Séparation en deux blocs `if` distincts (Plus de `else if`)
            if (opName.contains("ORANGE", true)) {
                CellularFrApi.getCellularFrPhotos(trueSupportId).forEach { photo ->
                    photosTemp.add(CommunityPhoto(photo.url, "CellularFR", photo.author, photo.uploadedAt))
                }
            }

            if (opName.contains("SFR", true) || opName.contains("BOUYGUES", true)) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val response = fr.geotower.data.api.SignalQuestClient.api.getSitePhotos(
                            authHeader = "Bearer ${fr.geotower.BuildConfig.SQ_API_KEY}",
                            siteId = trueSupportId
                        )
                        response.body()?.data?.forEach {
                            photosTemp.add(CommunityPhoto(it.imageUrl, "Signal Quest", it.authorName, it.uploadedAt))
                        }
                    }
                } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "SignalQuest photos request failed", e) }
            }

            communityPhotos = photosTemp
        }
    }

    // 🚀 CHARGEMENT DU SPEEDTEST (Signal Quest) - Séparé pour plus de stabilité
    LaunchedEffect(antenna?.idAnfr, antenna?.operateur) {
        val currentAntenna = antenna
        if (currentAntenna == null || currentAntenna.idAnfr.isBlank()) return@LaunchedEffect

        val opName = currentAntenna.operateur ?: ""
        val isOpCompatible = opName.contains("SFR", true) || opName.contains("BOUYGUES", true)

        if (fr.geotower.utils.AppConfig.siteShowSpeedtest.value && isOpCompatible) {
            speedtestData = null
            isSpeedtestLoading = true
            try {
                val anfrCodeToSend = currentAntenna.idAnfr
                val apiOperator = when {
                    opName.contains("SFR", true) -> "SFR"
                    opName.contains("BOUYGUES", true) -> "BOUYGUES"
                    else -> null
                }

                AppLogger.d(TAG_SPEEDTEST, "Speedtest request anfr=$anfrCodeToSend operator=$apiOperator")

                val response = fr.geotower.data.api.SignalQuestClient.api.getSiteSpeedtests(
                    authHeader = "Bearer ${fr.geotower.BuildConfig.SQ_API_KEY}",
                    anfrCode = anfrCodeToSend,
                    operator = apiOperator,
                    bestOnly = true
                )

                AppLogger.d(TAG_SPEEDTEST, "Speedtest response code=${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    speedtestData = body?.data?.firstOrNull()
                    AppLogger.d(TAG_SPEEDTEST, "Speedtest data=$speedtestData")
                } else {
                    response.errorBody()?.close()
                    AppLogger.d(TAG_SPEEDTEST, "Speedtest API failure code=${response.code()}")
                    AppLogger.w(TAG_SPEEDTEST, "SignalQuest speedtest API failure")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG_SPEEDTEST, "SignalQuest speedtest request failed", e)
            } finally {
                isSpeedtestLoading = false
            }
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
            } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "Location updates could not start", e) }
        }
        onDispose { locationManager.removeUpdates(locationListener) }
    }

    val distanceUnit = AppConfig.distanceUnit.intValue
    val locationData = remember(userLocation, antenna, distanceUnit) {
        if (userLocation != null && antenna != null) {
            val res = FloatArray(2)
            Location.distanceBetween(userLocation!!.latitude, userLocation!!.longitude, antenna!!.latitude, antenna!!.longitude, res)
            val distance = formatSiteDistanceMeters(res[0].toDouble(), distanceUnit)
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
    AppStrings.distanceLabel
    AppStrings.fromMyPosition
    val txtBearingLabel = AppStrings.bearingLabel
    val txtSupportHeight = AppStrings.supportHeight
    val txtNavToSite = AppStrings.navToSite
    val txtOpen = AppStrings.open
    val txtInstallApp = AppStrings.installApp
    val txtMap4G = AppStrings.map4G
    val txtMap5G = AppStrings.map5G
    val txtUnavailable = AppStrings.unavailable
    val txtWhichMap = AppStrings.whichMap
    val txtIdSupportCopy = AppStrings.idSupportCopy

    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "support_detail/$antennaId")

    // ✅ 1. ON PLACE LA FONCTION ICI POUR QU'ELLE SOIT VISIBLE PAR TOUT L'ÉCRAN
    fun handleBackNavigation() {
        if (isSplitScreen) {
            onCloseSplitScreen()
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    // ✅ 2. ON GÈRE LE BOUTON RETOUR PHYSIQUE ICI
    androidx.activity.compose.BackHandler(enabled = isSplitScreen || !safeBackNavigation.isLocked) {
        handleBackNavigation()
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().background(mainBgColor).padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        handleBackNavigation() // ✅ 3. ON APPELLE NOTRE FONCTION DANS LA TOPBAR
                    },
                    enabled = isSplitScreen || !safeBackNavigation.isLocked,
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
                IconButton(
                    onClick = { safeClick { navController.navigate("settings?section=site") } },
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
        if (antenna == null) {
            Box(Modifier.fillMaxSize().padding(padding).background(mainBgColor), contentAlignment = Alignment.Center) { LoadingIndicator() }
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
                        AppLauncherButton(isInstalled = isEnbAppInstalled, appName = "eNB-Analytics", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; if (isEnbAppInstalled) launchApp(context, "fr.enb_analytics.enb4g") else uriHandler.openUri("https://play.google.com/store/apps/details?id=fr.enb_analytics.enb4g") }
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
                        AppLauncherButton(isInstalled = isCellularFrInstalled, appName = "CellularFR", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) {
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
                        AppLauncherButton(isInstalled = isRncMobileInstalled, appName = "RNC Mobile", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; if (isRncMobileInstalled) launchApp(context, "org.rncteam.rncfreemobile") else uriHandler.openUri("https://play.google.com/store/apps/details?id=org.rncteam.rncfreemobile")
                        }
                    }
                }
            }

            if (showNavigationSheet) {
                fr.geotower.ui.components.NavigationBottomSheet(latitude = info.latitude, longitude = info.longitude, onDismiss = { showNavigationSheet = false }, sheetState = sheetState, useOneUi = useOneUi)
            }

            Column(
                modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().background(mainBgColor).antennaFadingEdge(scrollState).verticalScroll(scrollState).padding(16.dp),
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
                                        val opNameDisplay = info.operateur ?: AppStrings.unknown
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
                                            Text(formatSiteHeightMeters(physique?.hauteur), fontWeight = FontWeight.Bold)
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
                            // 🚨 CORRECTION : On affiche toujours le composant (plus de condition de liste vide)
                            if (showPhotos && info.idAnfr.isNotBlank()) {
                                CommunityPhotosSectionShared(
                                    photos = communityPhotos,
                                    operatorName = opName,
                                    supportNature = physique?.natureSupport, // ✅ LE BON NOM DE VARIABLE
                                    supportOwner = physique?.proprietaire,
                                    bgColor = cardBgColor,
                                    shape = blockShape,
                                    onAddPhotoClick = { safeClick { showImageSourceDialog = true } }
                                )
                            }
                        }
                        "speedtest" -> {
                            SpeedtestCard(
                                operatorName = info.operateur,
                                speedtestData = speedtestData,
                                isLoading = isSpeedtestLoading,
                                shape = blockShape,
                                bgColor = cardBgColor,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
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
                        "open_map" -> {
                            if (showOpenMap) {
                                Button(
                                    onClick = { safeClick { openMapAt(info.latitude, info.longitude) } },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Map, null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(AppStrings.openMap, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        "elevation_profile" -> {
                            if (showElevationProfile) {
                                Button(
                                    onClick = { safeClick { openElevationProfile(info.idAnfr) } },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Terrain, null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(AppStrings.elevationProfileButton, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        "throughput_calculator" -> {
                            if (showThroughputCalculator) {
                                Button(
                                    onClick = { safeClick { openThroughputCalculator(info.idAnfr) } },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Speed, null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(AppStrings.throughputCalculatorButton, fontWeight = FontWeight.Bold)
                                }
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
                                    communityPhotosSize = communityPhotos.size,
                                    speedtestData = speedtestData // 🚨 NEW
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
                        }
                        "freqs" -> { if (showFreqs) fr.geotower.ui.components.SiteFrequenciesBlock(info = info, technique = technique, formattedAzimuths = formattedAzimuths, cardBgColor = cardBgColor, blockShape = blockShape) }
                        "links" -> {
                            if (showLinks && opNameUrl.isNotEmpty()) {
                                fr.geotower.ui.components.SiteExternalLinksBlock(
                                    info = info,
                                    idSupport = physique?.idSupport,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape,
                                    buttonShape = buttonShape,
                                    isSignalQuestInstalled = isSignalQuestInstalled,
                                    onShowCellularFr = { showCellularFrSheet = true },
                                    onShowRnc = { showRncSheet = true },
                                    onShowEnb = { showEnbSheet = true }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
            }

            if (showImageSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showImageSourceDialog = false },
                    shape = blockShape,
                    containerColor = sheetBgColor,
                    title = { Text(AppStrings.addPhotos, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
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
                                Text(AppStrings.camera, fontWeight = FontWeight.Bold)
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
                                Text(AppStrings.gallery, fontWeight = FontWeight.Bold)
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
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (isInstalled) Icons.AutoMirrored.Filled.Launch else Icons.Default.Download, null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(if (isInstalled) "$txtOpen $appName" else "$txtInstall $appName", fontWeight = FontWeight.Bold) }
    }
}

private fun isPackageInstalled(context: Context, pkg: String): Boolean = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
private fun launchApp(context: Context, pkg: String) { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } }

private fun formatSiteDistanceMeters(distanceMeters: Double, distanceUnit: Int = AppConfig.distanceUnit.intValue): String {
    return if (distanceUnit == 1) {
        val feet = distanceMeters * 3.28084
        val miles = distanceMeters / 1609.344
        if (miles >= 0.1) String.format(Locale.US, "%.2f mi", miles) else "${feet.roundToInt()} ft"
    } else {
        if (distanceMeters >= 1000.0) String.format(Locale.US, "%.3f km", distanceMeters / 1000.0) else "${distanceMeters.toInt()} m"
    }
}

private fun formatSiteHeightMeters(heightMeters: Double?): String {
    if (heightMeters == null) return "--"
    return if (AppConfig.distanceUnit.intValue == 1) {
        "${(heightMeters * 3.28084).roundToInt()} ft"
    } else {
        if (heightMeters % 1.0 == 0.0) "${heightMeters.toInt()} m" else String.format(Locale.US, "%.1f m", heightMeters)
    }
}

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

private fun getOperatorColor(name: String?): Color {
    return OperatorColors.keyFor(name)
        ?.let { Color(OperatorColors.colorArgbForKey(it)) }
        ?: Color.Gray
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
