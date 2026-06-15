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
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.api.SignalQuestSpeedtestSortMetric
import fr.geotower.data.api.SqSpeedtestData
import fr.geotower.data.api.bestSignalQuestSpeedtestByMetric
import fr.geotower.data.api.filterBySignalQuestPlmn
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioBroadcastProgram
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.data.upload.SignalQuestUploadRules
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerBreadcrumbItem
import fr.geotower.ui.components.GeoTowerLoadingMessage
import fr.geotower.ui.components.GeoTowerNavigationBreadcrumbBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.components.RadioShareMenu
import fr.geotower.ui.components.RadioUsageIcon
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.CommunityDataSettingsSheet
import fr.geotower.ui.screens.settings.MiniMapSettingsSheet
import fr.geotower.ui.screens.settings.SiteFreqFiltersSheet
import fr.geotower.ui.screens.settings.SitePhotosSettingsSheet
import fr.geotower.ui.screens.settings.SiteSpeedtestsPagePreferences
import fr.geotower.ui.screens.settings.SiteSpeedtestsSettingsSheet
import fr.geotower.ui.screens.settings.SiteSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.activeOperatorKeysForSiteStatusFilter
import fr.geotower.utils.combineOperatorKeyFilters
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.SitePagePrefs
import fr.geotower.utils.formatTechnologies
import fr.geotower.utils.formatSiteDistanceMeters
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import fr.geotower.ui.components.SpeedtestCard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG_SITE_DETAIL = "GeoTower"
private const val TAG_SPEEDTEST = "GeoTowerUpload"
private const val SIGNAL_QUEST_PACKAGE_NAME = "com.sfrmap.android"
private const val SIGNAL_QUEST_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.sfrmap.android"
private const val SIGNALQUEST_SPEEDTEST_PAGE_SIZE = 100
private const val ARCEP_ALERT_URL = "https://jalerte.arcep.fr/"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SiteDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: String,
    applyMapFilters: Boolean = false,
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
                val antennas = repository.getAntennasByExactId(antennaId)
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
            GeoTowerLoadingMessage(
                title = stringResource(R.string.appstrings_site_detail_loading_title),
                detail = stringResource(R.string.appstrings_site_detail_loading_desc)
            )
        }
        return
    }
    val haptic = LocalHapticFeedback.current
    val uiStyle = LocalGeoTowerUiStyle.current
    rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LocalView.current

    fun openWebsiteUrl(url: String) {
        openUrlInBrowser(context, url) {
            uriHandler.openUri(url)
        }
    }

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign

    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val isOled = isOledMode

    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val sheetBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val buttonShape = oneUiActionButtonShape(useOneUi)

    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    val safeClick = rememberSafeClick()

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
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val completedWorkIds = remember { mutableSetOf<UUID>() }

    val currentUploadSiteId = physique?.idSupport?.takeIf { it.isNotBlank() } ?: antenna?.idAnfr.orEmpty()
    val unknownText = stringResource(R.string.appstrings_unknown)

    fun navigateToUploadWithUris(uris: List<Uri>) {
        if (
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_UPLOAD) ||
            !RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_UPLOAD) ||
            !RemoteFeatureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_SIGNALQUEST_UPLOAD) ||
            !RemoteFeatureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.SIGNALQUEST_UPLOAD)
        ) {
            return
        }
        val maxPhotos = RemoteFeatureFlags
            .limitOrDefault(RemoteFeatureFlags.Limits.PHOTO_UPLOAD_MAX_COUNT, SignalQuestUploadRules.MAX_PHOTOS)
            .coerceIn(1, SignalQuestUploadRules.MAX_PHOTOS)
        val selectedUris = uris.take(maxPhotos)
        if (selectedUris.isNotEmpty() && antenna != null) {
            val draftId = SignalQuestUploadDraftStore.put(selectedUris.map { it.toString() })
            val uploadSiteId = physique?.idSupport?.takeIf { it.isNotBlank() } ?: antenna!!.idAnfr
            val safeOperator = Uri.encode(antenna!!.operateur ?: unknownText)
            val safeAzimuts = Uri.encode(antenna!!.azimuts ?: "")
            navController.navigate("sq_upload/${uploadSiteId}/${safeOperator}?draftId=$draftId&lat=${antenna!!.latitude}&lon=${antenna!!.longitude}&azimuts=$safeAzimuts")
        }
    }

    val workInfos by remember(currentUploadSiteId) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow("sq_upload_$currentUploadSiteId")
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
            kotlinx.coroutines.delay(1500L)
            refreshPhotosTrigger++
        }
    }

    var showCartoradioSheet by remember { mutableStateOf(false) }
    var showEnbSheet by remember { mutableStateOf(false) }
    var showCellularFrSheet by remember { mutableStateOf(false) }
    var showSignalQuestSheet by remember { mutableStateOf(false) }
    var showNavigationSheet by remember { mutableStateOf(false) }
    var showRncSheet by remember { mutableStateOf(false) }
    var showAnfrSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult<PickVisualMediaRequest, List<Uri>>(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = SignalQuestUploadRules.MAX_PHOTOS),
        onResult = { uris ->
            navigateToUploadWithUris(uris)
        }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            navigateToUploadWithUris(uris)
        }
    )

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var currentCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = currentCameraUriString?.let(Uri::parse)
        if (capturedUri != null) {
            SignalQuestUploadQueue.completeCameraCapture(context, capturedUri, success)
            if (success && antenna != null) {
                navigateToUploadWithUris(listOf(capturedUri))
            }
        }
        currentCameraUriString = null
    }

    fun createCameraUri(): Uri {
        return SignalQuestUploadQueue.createCameraUri(context)
    }

    fun launchCameraCapture() {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_CAMERA)) return
        val uri = createCameraUri()
        currentCameraUriString = uri.toString()
        cameraLauncher.launch(uri)
    }

    val legacyCameraStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        }
    }

    fun launchCameraCaptureWithStorageCheck() {
        if (!RemoteFeatureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_CAMERA)) return
        val needsLegacyStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyStoragePermission) {
            legacyCameraStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            launchCameraCapture()
        }
    }

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config
    val canUseSitePhotos = featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTOS)
    val canUploadSitePhotos =
        canUseSitePhotos &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_UPLOAD) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_UPLOAD) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_SIGNALQUEST_UPLOAD) &&
            featureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.SIGNALQUEST_UPLOAD)
    val canUseSiteSpeedtests =
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.SITE_SPEEDTESTS) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_SPEEDTESTS) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_SPEEDTESTS)
    val canUseElevationProfile =
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.ELEVATION_PROFILE) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_ELEVATION_PROFILE) &&
            featureFlags.isProviderEnabled(RemoteFeatureFlags.Providers.ELEVATION_IGN)
    val canUseThroughputCalculator =
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.THROUGHPUT_CALCULATOR) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_THROUGHPUT_CALCULATOR)
    val canUseExternalNavigation =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_EXTERNAL_NAVIGATION) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_NAVIGATION)
    val canUseSiteShare =
        featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_SHARE) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.SHARE_SITE)
    val canUseSiteFrequencies = featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_FREQUENCIES)
    val canUseSiteExternalLinks = featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.OPEN_EXTERNAL_LINK)
    var speedtestFilterMajorEnb by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.FILTER_MAJOR_ENB, SiteSpeedtestsPagePreferences.DEFAULT_FILTER_MAJOR_ENB))
    }
    var speedtestIncludeMissingEnb by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.INCLUDE_MISSING_ENB, SiteSpeedtestsPagePreferences.DEFAULT_INCLUDE_MISSING_ENB))
    }
    var speedtestShowCount by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_COUNT, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COUNT))
    }
    var speedtestShowRadio by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_RADIO, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_RADIO))
    }
    var speedtestShowNetwork by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_NETWORK, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_NETWORK))
    }
    var speedtestShowCoordinates by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SHOW_COORDINATES, SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COORDINATES))
    }
    var speedtestBestMetric by rememberSaveable {
        mutableStateOf(
            SiteSpeedtestsPagePreferences.normalizeSortMetric(
                prefs.getString(SiteSpeedtestsPagePreferences.BEST_METRIC, SiteSpeedtestsPagePreferences.DEFAULT_BEST_METRIC)
            )
        )
    }
    var speedtestSortMetric by rememberSaveable {
        mutableStateOf(
            SiteSpeedtestsPagePreferences.normalizeSortMetric(
                prefs.getString(SiteSpeedtestsPagePreferences.SORT_METRIC, SiteSpeedtestsPagePreferences.DEFAULT_SORT_METRIC)
            )
        )
    }
    var speedtestSortDescending by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SiteSpeedtestsPagePreferences.SORT_DESCENDING, SiteSpeedtestsPagePreferences.DEFAULT_SORT_DESCENDING))
    }

    fun updateSpeedtestPreference(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun updateSpeedtestStringPreference(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun resetSpeedtestPreferences() {
        SiteSpeedtestsPagePreferences.reset(prefs)
        speedtestFilterMajorEnb = SiteSpeedtestsPagePreferences.DEFAULT_FILTER_MAJOR_ENB
        speedtestIncludeMissingEnb = SiteSpeedtestsPagePreferences.DEFAULT_INCLUDE_MISSING_ENB
        speedtestShowCount = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COUNT
        speedtestShowRadio = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_RADIO
        speedtestShowNetwork = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_NETWORK
        speedtestShowCoordinates = SiteSpeedtestsPagePreferences.DEFAULT_SHOW_COORDINATES
        speedtestBestMetric = SiteSpeedtestsPagePreferences.DEFAULT_BEST_METRIC
        speedtestSortMetric = SiteSpeedtestsPagePreferences.DEFAULT_SORT_METRIC
        speedtestSortDescending = SiteSpeedtestsPagePreferences.DEFAULT_SORT_DESCENDING
    }

    var miniMapDefaultMode by remember {
        mutableStateOf(MiniMapViewMode.fromStorageKey(prefs.getString(SitePagePrefs.MINI_MAP_MODE, null)))
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
        if (canUseElevationProfile) {
            navController.navigate("elevation_profile/$id")
        }
    }
    val openThroughputCalculator = onOpenThroughputCalculator ?: { id: String ->
        if (canUseThroughputCalculator) {
            navController.navigate("throughput_calculator/$id")
        }
    }
    fun openSiteSpeedtests(site: LocalisationEntity, sitePhysique: PhysiqueEntity?) {
        if (!canUseSiteSpeedtests) return
        val plmn = SignalQuestOperators.speedtestPlmnFor(site.operateur)
        val params = buildList {
            sitePhysique?.idSupport?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add("siteId=${Uri.encode(it)}")
            }
            site.idAnfr.trim().takeIf { it.isNotEmpty() }?.let {
                add("anfrCode=${Uri.encode(it)}")
            }
            SignalQuestOperators.operatorParamFor(site.operateur)?.let {
                add("operator=${Uri.encode(it)}")
            }
            plmn?.mcc?.let {
                add("mcc=$it")
            }
            plmn?.let {
                add("mnc=${it.mnc}")
            }
            add("market=FR")
        }
        if (params.isNotEmpty()) {
            navController.navigate("site_speedtests?${params.joinToString("&")}")
        }
    }

    // 🚨 MODIFICATION : L'ordre par défaut (photos, speedtest, nav, share...)
    var pageSiteOrder by remember {
        mutableStateOf(SitePagePrefs.order(prefs))
    }
    var showOperator by remember { mutableStateOf(SitePagePrefs.operator.read(prefs)) }
    var showBearingHeight by remember { mutableStateOf(SitePagePrefs.bearingHeight.read(prefs)) }
    var showMap by remember { mutableStateOf(SitePagePrefs.map.read(prefs)) }
    var showSupportDetails by remember { mutableStateOf(SitePagePrefs.supportDetails.read(prefs)) }
    val showPhotos by AppConfig.siteShowPhotos
    var showPanelHeights by remember { mutableStateOf(SitePagePrefs.panelHeights.read(prefs)) }
    var showIds by remember { mutableStateOf(SitePagePrefs.ids.read(prefs)) }
    var showOpenMap by remember { mutableStateOf(SitePagePrefs.openMap.read(prefs)) }
    var showElevationProfile by remember { mutableStateOf(SitePagePrefs.elevationProfile.read(prefs)) }
    var showThroughputCalculator by remember { mutableStateOf(SitePagePrefs.throughputCalculator.read(prefs)) }
    var showNav by remember { mutableStateOf(SitePagePrefs.nav.read(prefs)) }
    var showShare by remember { mutableStateOf(SitePagePrefs.share.read(prefs)) }
    var showDates by remember { mutableStateOf(SitePagePrefs.dates.read(prefs)) }
    var showAddress by remember { mutableStateOf(SitePagePrefs.address.read(prefs)) }
    var showFreqs by remember { mutableStateOf(SitePagePrefs.freqs.read(prefs)) }
    var showLinks by remember { mutableStateOf(SitePagePrefs.links.read(prefs)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSiteSettingsSheet by remember { mutableStateOf(false) }
    var showSpeedtestsSettingsSheet by remember { mutableStateOf(false) }
    var showSiteMiniMapSettingsSheet by remember { mutableStateOf(false) }
    var showSiteFreqSettingsSheet by remember { mutableStateOf(false) }
    var showSitePhotosSettingsSheet by remember { mutableStateOf(false) }
    var showCommunityDataSettingsSheet by remember { mutableStateOf(false) }
    var communityDataSettingsFeatureId by remember { mutableStateOf<String?>(null) }

    val isEnbAppInstalled = remember { isPackageInstalled(context, "fr.enb_analytics.enb4g") }
    val isSignalQuestInstalled = remember { isPackageInstalled(context, SIGNAL_QUEST_PACKAGE_NAME) }
    val isCellularFrInstalled = remember { isPackageInstalled(context, "com.luisbaker.cellularfr") }
    val isRncMobileInstalled = remember { isPackageInstalled(context, "org.rncteam.rncfreemobile") }

    LaunchedEffect(antennaId, refreshPhotosTrigger, refreshTrigger, featureFlags) {
        try {
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
            localData = box.firstOrNull { it.latitude.toFloat() == lat.toFloat() && it.longitude.toFloat() == lon.toFloat() && it.idAnfr.matchesRequestedAnfrId(antennaId) }
                ?: box.firstOrNull { it.latitude.toFloat() == lat.toFloat() && it.longitude.toFloat() == lon.toFloat() }
        }
        if (localData != null || antenna == null) {
            antenna = localData
        }

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

        if (localData != null && localData.idAnfr.isNotBlank() && canUseSitePhotos) {
            val opName = localData.operateur ?: ""
            // ✅ CORRECTION MAJEURE : On utilise le numéro de support physique universel
            val supportSiteId = physique?.idSupport ?: localData.idAnfr
            val signalQuestOperator = SignalQuestOperators.operatorParamFor(opName)
            val signalQuestOperatorKey = signalQuestOperator?.let { OperatorColors.keyFor(it) }
            val signalQuestOperatorLabel = OperatorColors.specForKey(signalQuestOperatorKey)?.label

            val photosTemp = mutableListOf<CommunityPhoto>()

            // ✅ Séparation en deux blocs `if` distincts (Plus de `else if`)
            // CellularFR masqué — voir CellularFrApi.ENABLED
            if (CommunityDataPreferences.isCellularFrPhotosEnabled(prefs, opName)) {
                CellularFrApi.getCellularFrPhotos(supportSiteId).forEach { photo ->
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

            if (signalQuestOperator != null && CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, opName)) {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val response = fr.geotower.data.api.SignalQuestClient.api.getSitePhotos(
                            siteId = supportSiteId
                        )
                        response.body()?.data
                            ?.filter { photo ->
                                val photoOperator = photo.operator
                                photoOperator.isNullOrBlank() ||
                                    photoOperator.equals(signalQuestOperator, ignoreCase = true) ||
                                    SignalQuestOperators.operatorParamFor(photoOperator).equals(signalQuestOperator, ignoreCase = true)
                            }
                            ?.forEach {
                                photosTemp.add(
                                    CommunityPhoto(
                                        url = it.imageUrl,
                                        communityName = "Signal Quest",
                                        author = it.authorName,
                                        date = it.uploadedAt,
                                        exifMetadata = it.publicMetadata,
                                        sourceId = CommunityDataPreferences.SOURCE_SIGNALQUEST,
                                        stableId = it.id ?: it.imageUrl,
                                        operatorKey = signalQuestOperatorKey,
                                        operatorLabel = signalQuestOperatorLabel
                                    )
                                )
                            }
                    }
                } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "SignalQuest photos request failed", e) }
            }

            communityPhotos = photosTemp
        } else if (!canUseSitePhotos) {
            communityPhotos = emptyList()
        }
        } finally {
            isRefreshing = false
        }
    }

    // 🚀 CHARGEMENT DU SPEEDTEST (Signal Quest) - Séparé pour plus de stabilité
    LaunchedEffect(antenna?.idAnfr, antenna?.operateur, physique?.idSupport, speedtestBestMetric, refreshTrigger, featureFlags) {
        val currentAntenna = antenna
        val currentPhysique = physique
        if (currentAntenna == null || currentAntenna.idAnfr.isBlank()) return@LaunchedEffect

        val plmn = SignalQuestOperators.speedtestPlmnFor(currentAntenna.operateur)
        val apiOperator = SignalQuestOperators.operatorParamFor(currentAntenna.operateur)
        val fallbackOperator = apiOperator.takeIf { plmn == null }

        if (
            fr.geotower.utils.AppConfig.siteShowSpeedtest.value &&
            canUseSiteSpeedtests &&
            (plmn != null || fallbackOperator != null) &&
            CommunityDataPreferences.isSignalQuestSpeedtestEnabled(prefs, currentAntenna.operateur)
        ) {
            speedtestData = null
            isSpeedtestLoading = true
            try {
                val supportSiteId = currentPhysique?.idSupport?.trim()?.takeIf { it.isNotEmpty() }
                val anfrCodeToSend = currentAntenna.idAnfr.trim().takeIf { it.isNotEmpty() }

                AppLogger.d(TAG_SPEEDTEST, "Speedtest request siteId=$supportSiteId anfr=$anfrCodeToSend operator=$fallbackOperator mcc=${plmn?.mcc} mnc=${plmn?.mnc}")

                val allSpeedtests = mutableListOf<SqSpeedtestData>()
                var offset = 0
                var total: Int? = null
                while (true) {
                    val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        fr.geotower.data.api.SignalQuestClient.api.getSiteSpeedtests(
                            siteId = supportSiteId,
                            anfrCode = anfrCodeToSend,
                            nationalSiteCode = anfrCodeToSend,
                            sourceCode = anfrCodeToSend,
                            operator = fallbackOperator,
                            mcc = plmn?.mcc,
                            mnc = plmn?.mnc,
                            bestOnly = false,
                            limit = SIGNALQUEST_SPEEDTEST_PAGE_SIZE,
                            offset = offset
                        )
                    }

                    AppLogger.d(TAG_SPEEDTEST, "Speedtest response code=${response.code()} offset=$offset")

                    if (response.isSuccessful) {
                        val body = response.body()
                        val rawPage = body?.data.orEmpty()
                        val page = rawPage.filterBySignalQuestPlmn(plmn)
                        allSpeedtests += page
                        total = body?.meta?.total ?: total
                        val totalValue = total
                        val fetchedCount = offset + rawPage.size

                        AppLogger.d(TAG_SPEEDTEST, "Speedtest page raw=${rawPage.size} filtered=${page.size} mcc=${plmn?.mcc} mnc=${plmn?.mnc}")

                        if (
                            rawPage.size < SIGNALQUEST_SPEEDTEST_PAGE_SIZE ||
                            rawPage.isEmpty() ||
                            (totalValue != null && fetchedCount >= totalValue)
                        ) {
                            break
                        }
                        offset = fetchedCount
                    } else {
                        response.errorBody()?.close()
                        AppLogger.d(TAG_SPEEDTEST, "Speedtest API failure code=${response.code()}")
                        AppLogger.w(TAG_SPEEDTEST, "SignalQuest speedtest API failure")
                        break
                    }
                }
                speedtestData = allSpeedtests.bestSignalQuestSpeedtestByMetric(
                    SignalQuestSpeedtestSortMetric.fromStorageKey(speedtestBestMetric)
                )
                AppLogger.d(TAG_SPEEDTEST, "Speedtest data=$speedtestData")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG_SPEEDTEST, "SignalQuest speedtest request failed", e)
            } finally {
                isSpeedtestLoading = false
            }
        } else {
            speedtestData = null
            isSpeedtestLoading = false
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

    val txtHomeTitle = stringResource(R.string.help_topic_title_home)
    val txtNearbyTitle = stringResource(R.string.nav_near_antennas)
    val txtMapTitle = stringResource(R.string.nav_map)
    val txtSupportDetailTitle = stringResource(R.string.appstrings_support_detail_title)
    val txtSiteDetailsTitle = stringResource(R.string.appstrings_site_detail_title)
    val txtIdCopied = stringResource(R.string.appstrings_id_copied)
    stringResource(R.string.appstrings_distance_label)
    stringResource(R.string.appstrings_from_my_position)
    val txtBearingLabel = stringResource(R.string.appstrings_bearing_label)
    val txtSupportHeight = stringResource(R.string.appstrings_support_height)
    val txtNavToSite = stringResource(R.string.appstrings_nav_to_site)
    val txtOpen = stringResource(R.string.appstrings_open)
    val txtInstallApp = stringResource(R.string.appstrings_install_app)
    val txtMap4G = stringResource(R.string.appstrings_map4_g)
    val txtMap5G = stringResource(R.string.appstrings_map5_g)
    val txtUnavailable = stringResource(R.string.appstrings_unavailable)
    val txtWhichMap = stringResource(R.string.appstrings_which_map)
    val txtIdSupportCopy = stringResource(R.string.appstrings_id_support_copy)

    val supportDetailRoute = remember(
        physique?.idSupport,
        antenna?.idAnfr,
        antenna?.operateur,
        applyMapFilters,
        antennaId
    ) {
        val supportId = physique?.idSupport?.takeIf { it.isNotBlank() }
            ?: antenna?.idAnfr?.takeIf { it.isNotBlank() }
            ?: antennaId
        val queryParams = mutableListOf<String>()
        OperatorColors.keyFor(antenna?.operateur)?.let { operatorKey ->
            queryParams += "operator=${Uri.encode(operatorKey)}"
        }
        if (applyMapFilters) {
            queryParams += "fromMap=true"
        }

        buildString {
            append("support_detail/")
            append(Uri.encode(supportId))
            if (queryParams.isNotEmpty()) {
                append("?")
                append(queryParams.joinToString("&"))
            }
        }
    }

    fun navigateToBreadcrumbParent(route: String) {
        if (isSplitScreen) {
            onCloseSplitScreen()
        }
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = supportDetailRoute)

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
            Column(modifier = Modifier.background(mainBgColor)) {
                GeoTowerBackTopBar(
                    onBack = { handleBackNavigation() },
                    backgroundColor = mainBgColor,
                    backEnabled = isSplitScreen || !safeBackNavigation.isLocked,
                    actions = {
                        IconButton(onClick = { safeClick { showSiteSettingsSheet = true } }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.appstrings_settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                ) {
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
                GeoTowerNavigationBreadcrumbBar(
                    navController = navController,
                    currentItem = GeoTowerBreadcrumbItem(
                        label = txtSiteDetailsTitle,
                        icon = Icons.Default.Tag,
                        key = "site_detail"
                    ),
                    currentRouteKeys = setOf("site_detail", "site_detail_from_map"),
                    impliedParentItems = listOf(
                        GeoTowerBreadcrumbItem(
                            label = txtHomeTitle,
                            icon = Icons.Default.Home,
                            onClick = { navigateToBreadcrumbParent("home") },
                            key = "home"
                        ),
                        if (applyMapFilters) {
                            GeoTowerBreadcrumbItem(
                                label = txtMapTitle,
                                icon = Icons.Default.Map,
                                onClick = { navigateToBreadcrumbParent("map") },
                                key = "map"
                            )
                        } else {
                            GeoTowerBreadcrumbItem(
                                label = txtNearbyTitle,
                                icon = Icons.Default.MyLocation,
                                onClick = { navigateToBreadcrumbParent("emitters") },
                                key = "emitters"
                            )
                        },
                        GeoTowerBreadcrumbItem(
                            label = txtSupportDetailTitle,
                            icon = Icons.Default.VerticalAlignTop,
                            onClick = { navigateToBreadcrumbParent(supportDetailRoute) },
                            key = "support_detail"
                        )
                    ),
                    onBackStackItemClick = {
                        if (isSplitScreen) onCloseSplitScreen()
                    },
                    backgroundColor = if (useOneUi) cardBgColor else MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    ) { padding ->
        if (antenna == null) {
            Box(Modifier.fillMaxSize().padding(padding).background(mainBgColor), contentAlignment = Alignment.Center) {
                GeoTowerLoadingMessage(
                    title = stringResource(R.string.appstrings_site_detail_loading_title),
                    detail = stringResource(R.string.appstrings_site_detail_loading_desc)
                )
            }
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
            val operatorFilterKeys = if (applyMapFilters) {
                AppConfig.selectedOperatorKeys.value
                    .takeUnless { selectedKeys -> selectedKeys.containsAll(OperatorColors.defaultVisibleKeys) }
            } else {
                null
            }
            val siteStatusFilterKeys = if (applyMapFilters) {
                activeOperatorKeysForSiteStatusFilter(
                    antennas = listOf(info),
                    sitesHs = hsDataMap.values,
                    showSitesInService = AppConfig.showSitesInService.value,
                    showSitesOutOfService = AppConfig.showSitesOutOfService.value
                )
            } else {
                null
            }
            val activeOperatorKeys = combineOperatorKeyFilters(operatorFilterKeys, siteStatusFilterKeys)
            val isOperatorMutedByFilter = activeOperatorKeys != null &&
                OperatorColors.keysFor(info.operateur).none { operatorKey -> operatorKey in activeOperatorKeys }

            if (showCartoradioSheet) {
                ModalBottomSheet(onDismissRequest = { showCartoradioSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Cartoradio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cartoradio, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showCartoradioSheet = false
                                openWebsiteUrl("https://cartoradio.fr/index.html#/cartographie/lonlat/${info.longitude}/${info.latitude}")
                            }
                        }
                    }
                }
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
                                openWebsiteUrl("https://enb-analytics.fr/analytics_${opNameUrl}.html")
                            }
                            val has5G = listOfNotNull(
                                technique?.technologies,
                                info.filtres,
                                info.frequences
                            ).any { it.contains("5G", ignoreCase = true) }
                            CommunityCard(title = txtMap5G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_enbanalytics, isEnabled = has5G, modifier = Modifier.weight(1f)) {
                                if (has5G) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; openWebsiteUrl("https://enb-analytics.fr/analytics_nr_${opNameUrl}.html") }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppLauncherButton(isInstalled = isEnbAppInstalled, appName = "eNB-Analytics", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; if (isEnbAppInstalled) launchApp(context, "fr.enb_analytics.enb4g") else uriHandler.openUri("https://play.google.com/store/apps/details?id=fr.enb_analytics.enb4g") }
                    }
                }
            }

            // CellularFR masqué — voir CellularFrApi.ENABLED
            if (showCellularFrSheet) {
                val supportId = physique?.idSupport ?: info.idAnfr // ✅ VRAI ID
                ModalBottomSheet(onDismissRequest = { showCellularFrSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CellularFR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cellularfr, isEnabled = supportId.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); showCellularFrSheet = false; openWebsiteUrl("https://cellularfr.fr/site-details.html?siteId=$supportId")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppLauncherButton(isInstalled = isCellularFrInstalled, appName = "CellularFR", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); showCellularFrSheet = false; if (isCellularFrInstalled) launchApp(context, "com.luisbaker.cellularfr") else uriHandler.openUri("https://play.google.com/store/apps/details?id=com.luisbaker.cellularfr")
                        }
                    }
                }
            }

            if (showSignalQuestSheet) {
                val signalQuestOperator = SignalQuestOperators.operatorParamFor(info.operateur)
                if (signalQuestOperator != null) {
                    val websiteUrl = signalQuestWebsiteUrl(
                        anfrCode = info.idAnfr,
                        operator = signalQuestOperator,
                        latitude = info.latitude,
                        longitude = info.longitude
                    )
                    val appDeeplinkUrl = signalQuestAppDeeplinkUrl(
                        siteId = physique?.idSupport,
                        operator = signalQuestOperator,
                        latitude = info.latitude,
                        longitude = info.longitude
                    )

                    ModalBottomSheet(onDismissRequest = { showSignalQuestSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Signal Quest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.appstrings_open_on), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_signalquest, isEnabled = info.idAnfr.isNotBlank(), modifier = Modifier.weight(1f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSignalQuestSheet = false
                                    openWebsiteUrl(websiteUrl)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            AppLauncherButton(isInstalled = isSignalQuestInstalled, appName = "Signal Quest", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSignalQuestSheet = false
                                if (isSignalQuestInstalled) {
                                    openSignalQuestApp(context, appDeeplinkUrl) {
                                        uriHandler.openUri(appDeeplinkUrl)
                                    }
                                } else {
                                    uriHandler.openUri(SIGNAL_QUEST_PLAY_STORE_URL)
                                }
                            }
                        }
                    }
                }
            }

            if (showRncSheet) {
                ModalBottomSheet(onDismissRequest = { showRncSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RNC Mobile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_rncmobile, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; openWebsiteUrl("https://rncmobile.net/site/${info.latitude},${info.longitude}")
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        AppLauncherButton(isInstalled = isRncMobileInstalled, appName = "RNC Mobile", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; if (isRncMobileInstalled) launchApp(context, "org.rncteam.rncfreemobile") else uriHandler.openUri("https://play.google.com/store/apps/details?id=org.rncteam.rncfreemobile")
                        }
                    }
                }
            }

            if (showAnfrSheet) {
                ModalBottomSheet(onDismissRequest = { showAnfrSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("data.gouv.fr", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_anfr, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showAnfrSheet = false
                                openWebsiteUrl("https://data.anfr.fr/visualisation/map/?id=observatoire_2g_3g_4g&location=17,${info.latitude},${info.longitude}")
                            }
                        }
                    }
                }
            }

            if (showNavigationSheet && canUseExternalNavigation) {
                fr.geotower.ui.components.NavigationBottomSheet(latitude = info.latitude, longitude = info.longitude, onDismiss = { showNavigationSheet = false }, sheetState = sheetState, useOneUi = useOneUi)
            }

            GeoTowerPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        refreshTrigger++
                    }
                },
                enabled = antenna != null,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().background(mainBgColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().geoTowerFadingEdge(scrollState).verticalScroll(scrollState).padding(16.dp),
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
                            fun serviceStatus(hasTech: Boolean, rawStatus: String?): Boolean? {
                                return fr.geotower.ui.components.serviceAvailabilityFromOutageCode(
                                    hasTechnology = hasTech,
                                    outageCode = rawStatus,
                                    isOutage = isOutage
                                )
                            }

                            fun isOutageStatusCode(rawStatus: String?): Boolean {
                                val code = rawStatus
                                    ?.trim()
                                    ?.uppercase(Locale.ROOT)
                                return code == "HS" || code == "DE"
                            }

                            val is5gVoiceProject = is5gProject || isOutageStatusCode(hsEntity?.voix5g)
                            val is5gDataProject = is5gProject || (!has5G && isOutageStatusCode(hsEntity?.data5g))

                            val realTechStatus = mapOf(
                                "2G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = serviceStatus(has2G, hsEntity?.voix2g),
                                    isInternetOk = serviceStatus(has2G, hsEntity?.data2g),
                                    isProject = is2gProject
                                ),
                                "3G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = serviceStatus(has3G, hsEntity?.voix3g),
                                    isInternetOk = serviceStatus(has3G, hsEntity?.data3g),
                                    isProject = is3gProject
                                ),
                                "4G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = serviceStatus(has4G, hsEntity?.voix4g),
                                    isInternetOk = serviceStatus(has4G, hsEntity?.data4g),
                                    isProject = is4gProject
                                ),
                                "5G" to fr.geotower.ui.components.ServiceStatus(
                                    isVoixOk = serviceStatus(has5G, hsEntity?.voix5g),
                                    isInternetOk = serviceStatus(has5G, hsEntity?.data5g),
                                    isProject = is5gProject,
                                    isVoixProject = is5gVoiceProject,
                                    isInternetProject = is5gDataProject
                                )
                            )

                            fr.geotower.ui.components.SiteStatusCard(
                                isProjectSite = isEntirelyProject, // Ne s'affiche en jaune que si TOUT le site est en projet
                                isOutage = isOutage,
                                outageText = outageText,
                                outageStartDate = hsEntity?.dateDebut,
                                outageExpectedRestorationDate = hsEntity?.dateFin,
                                cardBgColor = cardBgColor,
                                blockShape = blockShape,
                                techStatus = realTechStatus,
                                outageDetails = hsEntity,
                                onAlertArcep = if (canUseSiteExternalLinks) {
                                    { safeClick("alert_arcep_${info.idAnfr}") { openWebsiteUrl(ARCEP_ALERT_URL) } }
                                } else {
                                    null
                                }
                            )
                        }
                        "operator" -> {
                            if (showOperator) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (isOperatorMutedByFilter) 0.42f else 1f),
                                    shape = blockShape,
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val opNameDisplay = info.operateur ?: stringResource(R.string.appstrings_unknown)
                                        val logoRes = getDetailLogoRes(opNameDisplay)

                                        if (logoRes != null) { Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))) }
                                        else { Box(modifier = Modifier.size(72.dp).background(getOperatorColor(opNameDisplay), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(text = opNameDisplay.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp) } }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = opNameDisplay, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: info.frequences
                                            val realTechs = formatTechnologies(rawTechs, stringResource(R.string.appstrings_unknown))
                                            Text(text = realTechs, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (info.isZb == 1) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 72.dp, height = 48.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "ZB",
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 18.sp
                                                )
                                            }
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
                                    focusOperator = info.operateur,
                                    userLocation = userLocation,
                                    defaultViewMode = miniMapDefaultMode,
                                    showViewModeToggle = true
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
                            if (showPhotos && canUseSitePhotos && info.idAnfr.isNotBlank()) {
                                CommunityPhotosSectionShared(
                                    photos = communityPhotos,
                                    operatorName = opName,
                                    supportNature = physique?.natureSupport, // ✅ LE BON NOM DE VARIABLE
                                    supportOwner = physique?.proprietaire,
                                    bgColor = cardBgColor,
                                    shape = blockShape,
                                    onAddPhotoClick = if (canUploadSitePhotos) {
                                        { safeClick { showImageSourceDialog = true } }
                                    } else {
                                        null
                                    },
                                    favoriteScopeId = physique?.idSupport ?: info.idAnfr,
                                    favoriteSelectionEnabled = true
                                )
                            }
                        }
                        "speedtest" -> {
                            if (canUseSiteSpeedtests) {
                                SpeedtestCard(
                                    operatorName = info.operateur,
                                    speedtestData = speedtestData,
                                    isLoading = isSpeedtestLoading,
                                    shape = blockShape,
                                    bgColor = cardBgColor,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    onClick = { safeClick("site_speedtests_${info.idAnfr}") { openSiteSpeedtests(info, physique) } }
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(stringResource(R.string.appstrings_open_map), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        "elevation_profile" -> {
                            if (showElevationProfile && canUseElevationProfile) {
                                Button(
                                    onClick = { safeClick { openElevationProfile(info.idAnfr) } },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(stringResource(R.string.appstrings_elevation_profile_button), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        "throughput_calculator" -> {
                            if (showThroughputCalculator && canUseThroughputCalculator) {
                                Button(
                                    onClick = { safeClick { openThroughputCalculator(info.idAnfr) } },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(stringResource(R.string.appstrings_throughput_calculator_button), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        "nav" -> {
                            if (showNav && canUseExternalNavigation) {
                                Button(onClick = { safeClick { showNavigationSheet = true } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = buttonShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(txtNavToSite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        "share" -> {
                            if (showShare && canUseSiteShare) {
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
                        "freqs" -> { if (showFreqs && canUseSiteFrequencies) fr.geotower.ui.components.SiteFrequenciesBlock(info = info, technique = technique, formattedAzimuths = formattedAzimuths, cardBgColor = cardBgColor, blockShape = blockShape, applyMapFilters = applyMapFilters) }
                        "links" -> {
                            if (showLinks && canUseSiteExternalLinks && opNameUrl.isNotEmpty()) {
                                fr.geotower.ui.components.SiteExternalLinksBlock(
                                    info = info,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape,
                                    buttonShape = buttonShape,
                                    onShowCartoradio = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_CARTORADIO)) showCartoradioSheet = true
                                    },
                                    onShowCellularFr = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.CELLULARFR_EXTERNAL_LINKS)) showCellularFrSheet = true
                                    },
                                    onShowSignalQuest = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_EXTERNAL_LINKS)) showSignalQuestSheet = true
                                    },
                                    onShowRnc = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_RNC_MOBILE)) showRncSheet = true
                                    },
                                    onShowEnb = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_ENB_ANALYTICS)) showEnbSheet = true
                                    },
                                    onShowAnfr = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_ANFR)) showAnfrSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
            }
            }

            if (showSiteSettingsSheet) {
                SiteSettingsSheet(
                    siteOrder = pageSiteOrder,
                    onOrderChange = {
                        pageSiteOrder = SitePagePrefs.normalizeOrder(it)
                        prefs.edit().putString(SitePagePrefs.ORDER, pageSiteOrder.joinToString(",")).apply()
                    },
                    showOperator = showOperator,
                    onOperatorChange = {
                        showOperator = it
                        prefs.edit().putBoolean(SitePagePrefs.operator.key, it).apply()
                    },
                    showBearingHeight = showBearingHeight,
                    onBearingHeightChange = {
                        showBearingHeight = it
                        prefs.edit().putBoolean(SitePagePrefs.bearingHeight.key, it).apply()
                    },
                    showMap = showMap,
                    onMapChange = {
                        showMap = it
                        prefs.edit().putBoolean(SitePagePrefs.map.key, it).apply()
                    },
                    showSupportDetails = showSupportDetails,
                    onSupportDetailsChange = {
                        showSupportDetails = it
                        prefs.edit().putBoolean(SitePagePrefs.supportDetails.key, it).apply()
                    },
                    showPhotos = showPhotos,
                    onPhotosChange = {
                        AppConfig.siteShowPhotos.value = it
                        prefs.edit().putBoolean("site_show_photos", it).apply()
                    },
                    showPanelHeights = showPanelHeights,
                    onPanelHeightsChange = {
                        showPanelHeights = it
                        prefs.edit().putBoolean(SitePagePrefs.panelHeights.key, it).apply()
                    },
                    showIds = showIds,
                    onIdsChange = {
                        showIds = it
                        prefs.edit().putBoolean(SitePagePrefs.ids.key, it).apply()
                    },
                    showOpenMap = showOpenMap,
                    onOpenMapChange = {
                        showOpenMap = it
                        prefs.edit().putBoolean(SitePagePrefs.openMap.key, it).apply()
                    },
                    showElevationProfile = showElevationProfile,
                    onElevationProfileChange = {
                        showElevationProfile = it
                        prefs.edit().putBoolean(SitePagePrefs.elevationProfile.key, it).apply()
                    },
                    showThroughputCalculator = showThroughputCalculator,
                    onThroughputCalculatorChange = {
                        showThroughputCalculator = it
                        prefs.edit().putBoolean(SitePagePrefs.throughputCalculator.key, it).apply()
                    },
                    showNav = showNav,
                    onNavChange = {
                        showNav = it
                        prefs.edit().putBoolean(SitePagePrefs.nav.key, it).apply()
                    },
                    showShare = showShare,
                    onShareChange = {
                        showShare = it
                        prefs.edit().putBoolean(SitePagePrefs.share.key, it).apply()
                    },
                    showDates = showDates,
                    onDatesChange = {
                        showDates = it
                        prefs.edit().putBoolean(SitePagePrefs.dates.key, it).apply()
                    },
                    showAddress = showAddress,
                    onAddressChange = {
                        showAddress = it
                        prefs.edit().putBoolean(SitePagePrefs.address.key, it).apply()
                    },
                    showStatus = AppConfig.siteShowStatus.value,
                    onStatusChange = {
                        AppConfig.siteShowStatus.value = it
                        prefs.edit().putBoolean("site_show_status", it).apply()
                    },
                    showSpeedtest = AppConfig.siteShowSpeedtest.value,
                    onSpeedtestChange = {
                        AppConfig.siteShowSpeedtest.value = it
                        prefs.edit().putBoolean("site_show_speedtest", it).apply()
                    },
                    showFreqs = showFreqs,
                    onFreqsChange = {
                        showFreqs = it
                        prefs.edit().putBoolean(SitePagePrefs.freqs.key, it).apply()
                    },
                    showLinks = showLinks,
                    onLinksChange = {
                        showLinks = it
                        prefs.edit().putBoolean(SitePagePrefs.links.key, it).apply()
                    },
                    onOpenMiniMapSettings = {
                        showSiteSettingsSheet = false
                        showSiteMiniMapSettingsSheet = true
                    },
                    onOpenFrequencies = {
                        showSiteSettingsSheet = false
                        showSiteFreqSettingsSheet = true
                    },
                    onOpenPhotosSettings = {
                        showSiteSettingsSheet = false
                        showSitePhotosSettingsSheet = true
                    },
                    onOpenSpeedtestSettings = {
                        showSiteSettingsSheet = false
                        showSpeedtestsSettingsSheet = true
                    },
                    onDismiss = { showSiteSettingsSheet = false },
                    onBack = { showSiteSettingsSheet = false },
                    sheetState = pageSettingsSheetState,
                    useOneUi = uiStyle.useOneUi,
                    bubbleColor = uiStyle.bubbleColor
                )
            }

            if (showSpeedtestsSettingsSheet) {
                SiteSpeedtestsSettingsSheet(
                    filterMajorEnb = speedtestFilterMajorEnb,
                    onFilterMajorEnbChange = {
                        speedtestFilterMajorEnb = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.FILTER_MAJOR_ENB, it)
                    },
                    includeMissingEnb = speedtestIncludeMissingEnb,
                    onIncludeMissingEnbChange = {
                        speedtestIncludeMissingEnb = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.INCLUDE_MISSING_ENB, it)
                    },
                    showSpeedtestsCount = speedtestShowCount,
                    onShowSpeedtestsCountChange = {
                        speedtestShowCount = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.SHOW_COUNT, it)
                    },
                    showRadioDetails = speedtestShowRadio,
                    onShowRadioDetailsChange = {
                        speedtestShowRadio = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.SHOW_RADIO, it)
                    },
                    showNetworkDetails = speedtestShowNetwork,
                    onShowNetworkDetailsChange = {
                        speedtestShowNetwork = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.SHOW_NETWORK, it)
                    },
                    showCoordinates = speedtestShowCoordinates,
                    onShowCoordinatesChange = {
                        speedtestShowCoordinates = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.SHOW_COORDINATES, it)
                    },
                    bestMetric = speedtestBestMetric,
                    onBestMetricChange = {
                        val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                        speedtestBestMetric = normalizedMetric
                        updateSpeedtestStringPreference(SiteSpeedtestsPagePreferences.BEST_METRIC, normalizedMetric)
                    },
                    sortMetric = speedtestSortMetric,
                    onSortMetricChange = {
                        val normalizedMetric = SiteSpeedtestsPagePreferences.normalizeSortMetric(it)
                        speedtestSortMetric = normalizedMetric
                        updateSpeedtestStringPreference(SiteSpeedtestsPagePreferences.SORT_METRIC, normalizedMetric)
                    },
                    sortDescending = speedtestSortDescending,
                    onSortDescendingChange = {
                        speedtestSortDescending = it
                        updateSpeedtestPreference(SiteSpeedtestsPagePreferences.SORT_DESCENDING, it)
                    },
                    onReset = { resetSpeedtestPreferences() },
                    onDismiss = { showSpeedtestsSettingsSheet = false },
                    onBack = {
                        showSpeedtestsSettingsSheet = false
                        showSiteSettingsSheet = true
                    },
                    sheetState = pageSettingsSheetState,
                    useOneUi = uiStyle.useOneUi,
                    bubbleColor = uiStyle.bubbleColor
                )
            }

            if (showSiteMiniMapSettingsSheet) {
                MiniMapSettingsSheet(
                    selectedMode = miniMapDefaultMode,
                    onModeChange = {
                        miniMapDefaultMode = it
                        prefs.edit().putString(SitePagePrefs.MINI_MAP_MODE, it.storageKey).apply()
                    },
                    onDismiss = { showSiteMiniMapSettingsSheet = false },
                    onBack = {
                        showSiteMiniMapSettingsSheet = false
                        showSiteSettingsSheet = true
                    },
                    sheetState = pageSettingsSheetState,
                    useOneUi = uiStyle.useOneUi,
                    bubbleColor = uiStyle.bubbleColor
                )
            }

            if (showSiteFreqSettingsSheet) {
                SiteFreqFiltersSheet(
                    onDismiss = { showSiteFreqSettingsSheet = false },
                    onBack = {
                        showSiteFreqSettingsSheet = false
                        showSiteSettingsSheet = true
                    }
                )
            }

            if (showSitePhotosSettingsSheet) {
                SitePhotosSettingsSheet(
                    onDismiss = { showSitePhotosSettingsSheet = false },
                    onBack = {
                        showSitePhotosSettingsSheet = false
                        showSiteSettingsSheet = true
                    },
                    photosVisible = showPhotos,
                    onPhotosVisibilityChange = {
                        AppConfig.siteShowPhotos.value = it
                        prefs.edit().putBoolean("site_show_photos", it).apply()
                    },
                    onOpenCommunityDataSettings = {
                        communityDataSettingsFeatureId = CommunityDataPreferences.FEATURE_PHOTOS
                        showSitePhotosSettingsSheet = false
                        showCommunityDataSettingsSheet = true
                    }
                )
            }

            if (showCommunityDataSettingsSheet) {
                CommunityDataSettingsSheet(
                    onDismiss = { showCommunityDataSettingsSheet = false },
                    sheetState = pageSettingsSheetState,
                    useOneUi = uiStyle.useOneUi,
                    featureId = communityDataSettingsFeatureId
                )
            }

            if (showImageSourceDialog && canUploadSitePhotos) {
                AlertDialog(
                    onDismissRequest = { showImageSourceDialog = false },
                    shape = blockShape,
                    containerColor = sheetBgColor,
                    title = { Text(stringResource(R.string.appstrings_add_photos), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_CAMERA)) {
                                Button(
                                    onClick = {
                                        safeClick {
                                            showImageSourceDialog = false
                                            launchCameraCaptureWithStorageCheck()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.appstrings_camera), fontWeight = FontWeight.Bold)
                                }
                            }
                            if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_GALLERY)) {
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
                                    Text(stringResource(R.string.appstrings_gallery), fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    safeClick {
                                        showImageSourceDialog = false
                                        documentPickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = buttonShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.FolderOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.appstrings_external_photo_files), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RadioSiteDetailScreen(
    navController: NavController,
    radioRepository: RadioRepository,
    stationId: String,
    supportId: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi && isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    val buttonShape = oneUiActionButtonShape(useOneUi)
    val safeClick = rememberSafeClick()
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "emitters")
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var marker by remember { mutableStateOf<RadioMapMarker?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var showNavigationSheet by remember { mutableStateOf(false) }
    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }

    LaunchedEffect(stationId, supportId) {
        isLoading = true
        marker = withContext(Dispatchers.IO) {
            radioRepository.getMarkerForSite(stationId, supportId)
        }
        isLoading = false
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
            } catch (e: Exception) {
                AppLogger.w(TAG_SITE_DETAIL, "Radio detail location updates could not start", e)
            }
        }

        onDispose { locationManager.removeUpdates(locationListener) }
    }

    val distanceUnit = AppConfig.distanceUnit.intValue
    val locationData = remember(userLocation, marker, distanceUnit) {
        val site = marker
        if (userLocation != null && site != null) {
            val res = FloatArray(2)
            Location.distanceBetween(userLocation!!.latitude, userLocation!!.longitude, site.latitude, site.longitude, res)
            val distance = formatSiteDistanceMeters(res[0].toDouble(), distanceUnit)
            var bearing = res[1]
            if (bearing < 0) bearing += 360f
            Triple(distance, String.format(Locale.US, "%.1f%s", bearing, "\u00B0"), res[0])
        } else {
            Triple("--", "--", null as Float?)
        }
    }
    val distanceStr = locationData.first
    val bearingStr = locationData.second
    val distanceMeters = locationData.third

    fun openMapAt(site: RadioMapMarker) {
        context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("clicked_lat", site.latitude.toFloat())
            .putFloat("clicked_lon", site.longitude.toFloat())
            .putFloat("last_map_lat", site.latitude.toFloat())
            .putFloat("last_map_lon", site.longitude.toFloat())
            .putFloat("last_map_zoom", 18f)
            .apply()
        navController.navigate("map")
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = "Détail radio ANFR",
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = mainBgColor,
                backEnabled = !safeBackNavigation.isLocked
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .background(mainBgColor)
        ) {
            val site = marker
            when {
                isLoading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                site == null -> Text(
                    text = stringResource(R.string.appstrings_no_data_found),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState)
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioSiteHeaderCard(site, cardBgColor, blockShape)

                    RadioSiteBearingHeightRow(
                        marker = site,
                        bearingStr = bearingStr,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape
                    )

                    fr.geotower.ui.components.SharedMiniMapCard(
                        modifier = Modifier.fillMaxWidth(),
                        centerLat = site.latitude,
                        centerLon = site.longitude,
                        mappedAntennas = emptyList(),
                        radioMarkers = listOf(site),
                        sitesHs = emptyList(),
                        blockShape = blockShape,
                        cardBorder = cardBorder,
                        onMapReady = { globalMapRef = it },
                        focusOperator = null,
                        userLocation = userLocation,
                        defaultViewMode = MiniMapViewMode.AntennaCentered,
                        showViewModeToggle = true
                    )

                    RadioSiteSupportDetailsCard(
                        marker = site,
                        distanceStr = distanceStr,
                        bearingStr = bearingStr,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape
                    )

                    RadioSiteActionButtons(
                        buttonShape = buttonShape,
                        onOpenMap = { safeClick { openMapAt(site) } },
                        onNavigate = { safeClick { showNavigationSheet = true } },
                        shareButton = {
                            RadioShareMenu(
                                marker = site,
                                distanceStr = distanceStr,
                                bearingStr = bearingStr,
                                useOneUi = useOneUi,
                                buttonShape = buttonShape,
                                globalMapRef = globalMapRef,
                                outlinedButton = true
                            )
                        }
                    )

                    RadioSiteIdentifiersCard(
                        marker = site,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape
                    )

                    RadioSiteAddressCard(
                        marker = site,
                        distanceStr = distanceStr,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape
                    )

                    RadioSiteInfoCard(
                        title = "Radio",
                        icon = Icons.Default.Info,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape,
                        leadingContent = {
                            RadioUsageIcon(
                                serviceMask = site.serviceMask,
                                systemMask = site.systemMask,
                                size = 22.dp
                            )
                        }
                    ) {
                        RadioSiteInfoLine("Categories", radioSiteUsageSummary(site))
                        RadioSiteInfoLine("Famille", RadioServiceMasks.labelFor(site.serviceMask))
                        RadioSiteInfoLine("Reseau", site.networkName)
                        RadioSiteInfoLine("Systemes", site.systemSummary)
                        RadioSiteInfoLine("Frequences", site.frequencySummary)
                        RadioSiteInfoLine("Emetteurs", site.emitterCount.takeIf { it > 0 }?.toString())
                        RadioSiteInfoLine("Antennes", site.antennaCount.takeIf { it > 0 }?.toString())
                    }

                    val broadcastPrograms = remember(site) { site.broadcastPrograms }
                    if (broadcastPrograms.isNotEmpty()) {
                        RadioSiteBroadcastProgramsCard(
                            marker = site,
                            programs = broadcastPrograms,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        )
                    }

                    if (site.antennaLines.isNotEmpty()) {
                        RadioSiteInfoCard(
                            title = "Antennes et azimuts",
                            icon = Icons.Default.Navigation,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        ) {
                            site.antennaLines.forEach { line ->
                                RadioAntennaInfoLine(line)
                            }
                        }
                    }

                    val extraDetails = remember(site) { radioSiteExtraDetailLines(site) }
                    if (extraDetails.isNotEmpty()) {
                        RadioSiteInfoCard(
                            title = "Details ANFR",
                            icon = Icons.Default.Info,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        ) {
                            extraDetails.forEach { (label, value) ->
                                RadioSiteInfoLine(label, value)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            if (showNavigationSheet && site != null) {
                fr.geotower.ui.components.NavigationBottomSheet(
                    latitude = site.latitude,
                    longitude = site.longitude,
                    onDismiss = { showNavigationSheet = false },
                    sheetState = sheetState,
                    useOneUi = useOneUi
                )
            }
        }
    }
}

@Composable
private fun RadioSiteHeaderCard(
    marker: RadioMapMarker,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                RadioUsageIcon(
                    serviceMask = marker.serviceMask,
                    systemMask = marker.systemMask,
                    size = 48.dp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker.networkName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = marker.systemSummary ?: RadioServiceMasks.labelFor(marker.serviceMask),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RadioSiteInfoCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardBgColor: Color,
    blockShape: RoundedCornerShape,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            content()
        }
    }
}

@Composable
private fun RadioSiteBroadcastProgramsCard(
    marker: RadioMapMarker,
    programs: List<RadioBroadcastProgram>,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    RadioSiteInfoCard(
        title = "Radios diffusées",
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape,
        leadingContent = {
            RadioUsageIcon(
                serviceMask = marker.serviceMask,
                systemMask = marker.systemMask,
                size = 22.dp
            )
        }
    ) {
        programs.forEach { program ->
            RadioSiteInfoLine(
                label = program.serviceName,
                value = program.detailLabel ?: "Radio diffusée"
            )
        }
    }
}

@Composable
private fun RadioSiteBearingHeightRow(
    marker: RadioMapMarker,
    bearingStr: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val rotation = bearingStr.removeSuffix("\u00B0").toFloatOrNull() ?: 0f
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = blockShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Cap mesure", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(bearingStr, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = blockShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hauteur support", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Icon(
                    Icons.Default.VerticalAlignTop,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(marker.supportHeightSummary ?: "--", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RadioSiteSupportDetailsCard(
    marker: RadioMapMarker,
    distanceStr: String,
    bearingStr: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    RadioSiteInfoCard(
        title = "Details du support",
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        RadioSiteInfoLine("Nature du support", marker.supportNatureSummary)
        RadioSiteInfoLine("Proprietaire", marker.supportOwnerSummary)
        RadioSiteInfoLine("Distance", "$distanceStr de vous")
        RadioSiteInfoLine("Cap mesure", bearingStr)
    }
}

@Composable
private fun RadioSiteActionButtons(
    buttonShape: androidx.compose.ui.graphics.Shape,
    onOpenMap: () -> Unit,
    onNavigate: () -> Unit,
    shareButton: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onOpenMap,
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

        Button(
            onClick = onNavigate,
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

        shareButton()
    }
}

@Composable
private fun RadioSiteIdentifiersCard(
    marker: RadioMapMarker,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    RadioSiteInfoCard(
        title = "Identifiants",
        icon = Icons.Default.Tag,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        RadioSiteInfoLine(
            label = "ID Support",
            value = marker.supportId,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = context.getString(R.string.appstrings_id_support_copy),
                    value = marker.supportId,
                    toastMessage = context.getString(R.string.appstrings_id_copied)
                )
            }
        )
        RadioSiteInfoLine(
            label = "Numero de station ANFR",
            value = marker.stationId,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = "Station ANFR",
                    value = marker.stationId,
                    toastMessage = context.getString(R.string.appstrings_id_copied)
                )
            }
        )
    }
}

@Composable
private fun RadioSiteAddressCard(
    marker: RadioMapMarker,
    distanceStr: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gpsCoords = formatRadioSiteGps(marker.latitude, marker.longitude)
    val cleanGpsCoords = String.format(Locale.US, "%.5f, %.5f", marker.latitude, marker.longitude)

    RadioSiteInfoCard(
        title = "Adresse",
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        RadioSiteInfoLine(
            label = "Adresse",
            value = marker.addressSummary,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = context.getString(R.string.appstrings_address_copy),
                    value = marker.addressSummary,
                    toastMessage = context.getString(R.string.appstrings_address_copied)
                )
            }
        )
        RadioSiteInfoLine(
            label = "GPS",
            value = gpsCoords,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = context.getString(R.string.appstrings_gps_coords_copy),
                    value = cleanGpsCoords,
                    toastMessage = context.getString(R.string.appstrings_coords_copied)
                )
            }
        )
        RadioSiteInfoLine("Distance", "$distanceStr de vous")
    }
}

@Composable
private fun RadioSiteInfoLine(label: String, value: String?, onCopy: (() -> Unit)? = null) {
    val cleanValue = value?.takeIf { it.isNotBlank() } ?: return
    fr.geotower.ui.components.InfoLine(label = "$label : ", value = cleanValue, onCopy = onCopy)
}

@Composable
private fun RadioAntennaInfoLine(line: String) {
    val label = line.substringBefore(":", missingDelimiterValue = "Antenne").trim()
    val value = line.substringAfter(":", missingDelimiterValue = line).trim()
    RadioSiteInfoLine(label.ifBlank { "Antenne" }, value)
}

private fun formatRadioSiteGps(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.5f%s, %.5f%s", latitude, "\u00B0", longitude, "\u00B0")
}

private fun copyRadioSiteValue(
    context: Context,
    label: String,
    value: String?,
    toastMessage: String
) {
    val cleanValue = value?.takeIf { it.isNotBlank() } ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, cleanValue))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

private fun radioSiteUsageSummary(marker: RadioMapMarker): String {
    return buildList {
        if ((marker.systemMask and RadioSystemMasks.TV) != 0) add("TV")
        if ((marker.systemMask and RadioSystemMasks.RADIO) != 0) add("Radio")
        if ((marker.serviceMask and (RadioServiceMasks.PRIVATE or RadioServiceMasks.RAIL or RadioServiceMasks.TRANSPORT)) != 0) {
            add("Reseaux mobiles prives")
        }
        if ((marker.serviceMask and RadioServiceMasks.FH) != 0) add("Faisceaux hertziens")
        if ((marker.serviceMask and (RadioServiceMasks.SATELLITE or RadioServiceMasks.RADAR or RadioServiceMasks.OTHER)) != 0 || isEmpty()) {
            add("Autres stations")
        }
    }.distinct().joinToString(", ")
}

private fun radioSiteExtraDetailLines(marker: RadioMapMarker): List<Pair<String, String>> {
    val alreadyDisplayed = setOf("adresse", "support", "systemes", "frequences", "programmes", "antennes")
    return marker.detailText
        ?.lineSequence()
        ?.mapNotNull { rawLine ->
            val label = rawLine.substringBefore(":", missingDelimiterValue = "").trim()
            val value = rawLine.substringAfter(":", missingDelimiterValue = "").trim()
            if (label.isBlank() || value.isBlank() || label.lowercase(Locale.ROOT) in alreadyDisplayed) {
                null
            } else {
                label to value
            }
        }
        ?.distinct()
        ?.toList()
        .orEmpty()
}

@Composable
private fun AppLauncherButton(isInstalled: Boolean, appName: String, txtOpen: String, txtInstall: String, useOneUi: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = oneUiActionButtonShape(useOneUi), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (isInstalled) Icons.AutoMirrored.Filled.Launch else Icons.Default.Download, null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(if (isInstalled) "$txtOpen $appName" else "$txtInstall $appName", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

private fun isPackageInstalled(context: Context, pkg: String): Boolean = try { context.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
private fun launchApp(context: Context, pkg: String) { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } }

private fun openUrlInBrowser(context: Context, url: String, fallback: () -> Unit) {
    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        selector = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_APP_BROWSER)
        }
    }
    try {
        context.startActivity(browserIntent)
    } catch (e: Exception) {
        fallback()
    }
}

private fun signalQuestWebsiteUrl(anfrCode: String, operator: String, latitude: Double, longitude: Double): String {
    return Uri.Builder()
        .scheme("https")
        .authority("signalquest.fr")
        .path("site")
        .appendQueryParameter("anfrCode", anfrCode)
        .appendQueryParameter("operator", operator)
        .appendQueryParameter("lat", latitude.toString())
        .appendQueryParameter("lng", longitude.toString())
        .appendQueryParameter("open", "antenna")
        .build()
        .toString()
}

private fun signalQuestAppDeeplinkUrl(siteId: String?, operator: String, latitude: Double, longitude: Double): String {
    return Uri.Builder()
        .scheme("https")
        .authority("signalquest.fr")
        .path("site")
        .appendQueryParameter("siteId", siteId.orEmpty())
        .appendQueryParameter("operator", operator)
        .appendQueryParameter("lat", latitude.toString())
        .appendQueryParameter("lng", longitude.toString())
        .appendQueryParameter("open", "antenna")
        .appendQueryParameter("autoOpen", "0")
        .build()
        .toString()
}

private fun openSignalQuestApp(context: Context, deeplinkUrl: String, fallback: () -> Unit) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(deeplinkUrl)).apply {
        setPackage(SIGNAL_QUEST_PACKAGE_NAME)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        fallback()
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

fun getDetailLogoRes(opName: String?): Int? = OperatorLogos.drawableRes(opName)

private fun String.matchesRequestedAnfrId(requested: String): Boolean {
    if (this == requested) return true
    val candidateLong = takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    val requestedLong = requested.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    return candidateLong != null && candidateLong == requestedLong
}

@SuppressLint("MissingPermission")
private fun getLocalLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
}

private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
