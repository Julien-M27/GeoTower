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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geotower.data.share.ShareHistoryStore
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.data.api.CellMapperLinks
import fr.geotower.data.api.CellMapperNetwork
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.api.SignalQuestSpeedtestSortMetric
import fr.geotower.data.api.fetchBestSignalQuestSpeedtest
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.physicalSiteKey
import fr.geotower.data.models.RadioBroadcastProgram
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.RadioServiceMasks
import fr.geotower.data.models.RadioSystemMasks
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.data.upload.SignalQuestUploadQueue
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerBreadcrumbItem
import fr.geotower.ui.components.GeoTowerLoadingMessage
import fr.geotower.ui.components.GeoTowerNavigationBreadcrumbBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.components.RadioShareMenu
import fr.geotower.ui.components.RadioUsageIcon
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.customizableBlock
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.CommunityDataSettingsSheet
import fr.geotower.ui.screens.settings.EmbeddedSiteBlocks
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
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.SitePagePrefs
import fr.geotower.utils.formatTechnologies
import fr.geotower.utils.formatSiteDistanceMeters
import fr.geotower.utils.isAnnouncedOnlyStation
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import fr.geotower.ui.components.SpeedtestCard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hauteur de l'espace réservé aux chargements en mode inséré (pas de `fillMaxSize` possible). */
private val EMBEDDED_PLACEHOLDER_HEIGHT = 160.dp

/**
 * Blocs de la fiche site retirés en mode inséré : la fiche support les affiche déjà une fois,
 * au-dessus de la liste des opérateurs. Les répéter sous chaque opérateur n'apporterait rien.
 */
private val EMBEDDED_HIDDEN_BLOCKS = setOf(
    "operator",        // la ligne dépliable porte déjà le nom et le logo de l'opérateur
    "map",             // mini-carte du support
    "open_map",        // bouton « ouvrir la carte »
    "support_details", // caractéristiques du pylône
    "nav",             // navigation vers le site
    "address"          // adresse du support
)

/**
 * Scaffold de la fiche site, sauf en mode inséré : un Scaffold imbriqué dans un contenu défilant
 * se mesure en `fillMaxSize` et ajouterait une seconde barre de titre.
 */
@Composable
private fun SiteDetailScaffold(
    embedded: Boolean,
    containerColor: Color,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    if (embedded) {
        content(PaddingValues(0.dp))
    } else {
        Scaffold(containerColor = containerColor, topBar = topBar, content = content)
    }
}

/**
 * Conteneur du contenu de la fiche site.
 *
 * En mode inséré : simple colonne, sans défilement propre — c'est la fiche support qui défile, et
 * deux défilements verticaux imbriqués font mesurer l'enfant avec une hauteur infinie (plantage).
 * Le tiré-pour-rafraîchir et les boutons de bord disparaissent avec lui, ils appartiennent à la
 * page hôte.
 */
@Composable
private fun SiteDetailScrollContainer(
    embedded: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
    scrollState: ScrollState,
    background: Color,
    topPadding: Dp,
    contentPadding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    if (embedded) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding),
            verticalArrangement = Arrangement.Top,
            content = content
        )
        return
    }

    GeoTowerPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        enabled = refreshEnabled,
        modifier = Modifier.padding(top = topPadding).fillMaxSize().background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .geoTowerFadingEdge(scrollState)
                .pageScrollbar(PageScrollPrefs.SITE, scrollState)
                .verticalScroll(scrollState)
                .padding(contentPadding),
            // L'espacement est porté par chaque CustomizableBlock, pour qu'un bloc masqué
            // ne laisse aucun trou (voir CustomizableBlock).
            verticalArrangement = Arrangement.Top,
            content = content
        )
        PageScrollEdgeButtons(PageScrollPrefs.SITE, scrollState)
    }
}

private const val TAG_SITE_DETAIL = "GeoTower"
private const val TAG_SPEEDTEST = "GeoTowerUpload"
private const val SIGNAL_QUEST_PACKAGE_NAME = "com.sfrmap.android"
private const val SIGNAL_QUEST_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.sfrmap.android"
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
    onOpenThroughputCalculator: ((String) -> Unit)? = null,
    // Mode « inséré » (accordéon de la fiche support en mode simplifié) : l'écran perd sa barre
    // de titre, son fil d'Ariane, son tiré-pour-rafraîchir et surtout son propre défilement —
    // deux défilements verticaux imbriqués font mesurer le contenu avec une hauteur infinie.
    // Les blocs de niveau support (adresse, GPS, propriétaire…) sont aussi retirés : la fiche
    // support les affiche déjà une fois au-dessus, les répéter par opérateur n'a pas de sens.
    embedded: Boolean = false,
    // Coupé par la fiche support quand elle porte déjà le bandeau « site tout juste déclaré » pour
    // le pylône entier : sinon il se répéterait sous chaque section opérateur.
    showWeeklyOnlyBanner: Boolean = true
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.SITE_DETAIL)
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

                    // Inséré : le dernier point cliqué appartient à la fiche support, qui s'en sert
                    // pour sa distance. Une section opérateur dépliée n'a pas à le réécrire.
                    if (!embedded) {
                        prefs.edit()
                            .putFloat("clicked_lat", site!!.latitude.toFloat())
                            .putFloat("clicked_lon", site.longitude.toFloat())
                            .apply()
                    }
                }
            } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "Site selection restore failed", e) }
        }
        isReady = true
    }

    if (!isReady) {
        Box(
            // Inséré : hauteur bornée, un fillMaxSize dans un parent défilant se mesure à l'infini.
            modifier = if (embedded) {
                Modifier.fillMaxWidth().height(EMBEDDED_PLACEHOLDER_HEIGHT)
            } else {
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            },
            contentAlignment = Alignment.Center
        ) {
            GeoTowerLoadingMessage(
                title = stringResource(R.string.appstrings_site_detail_loading_title),
                detail = stringResource(R.string.appstrings_site_detail_loading_desc)
            )
        }
        return
    }
    val haptic = LocalHapticFeedback.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing
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
    // Même gris que la fiche support. Hors One UI cette page lisait `surfaceContainerLow`, qui est
    // le fond des cartes SECONDAIRES (uiStyle.secondaryCardColor) : à peine détaché de
    // l'arrière-plan, les blocs photos et détails du site paraissaient plus sombres que les mêmes
    // blocs sur le support. `uiStyle.cardColor` est le fond des cartes de premier plan.
    val cardBgColor = uiStyle.cardColor
    val sheetBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)
    val buttonShape = oneUiActionButtonShape(useOneUi)

    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    val cardBorder = if (useOneUi) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    val safeClick = rememberSafeClick()

    var antenna by remember { mutableStateOf<LocalisationEntity?>(null) }
    // Zoom eNB-Analytics adapté à la densité locale (calculé au chargement du site). 15 = densité inconnue.
    var enbAnalyticsZoom by remember { mutableStateOf(15) }
    var physique by remember { mutableStateOf<PhysiqueEntity?>(null) }

    // --- ÉTATS POUR LE SPEEDTEST ---
    var speedtestData by remember { mutableStateOf<fr.geotower.data.api.SqSpeedtestData?>(null) }
    var isSpeedtestLoading by remember { mutableStateOf(false) }

    var technique by remember { mutableStateOf<TechniqueEntity?>(null) }
    // Identifiants eNB/gNB du pylône pour cet opérateur (base optionnelle geotower_fr_enb.db).
    var networkIds by remember { mutableStateOf(fr.geotower.data.EnbRepository.SiteNetworkIds()) }
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
        // Pas de limite de nombre : le serveur recoit et traite les photos une par une.
        val selectedUris = uris
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
    var showCellMapperSheet by remember { mutableStateOf(false) }
    var showNavigationSheet by remember { mutableStateOf(false) }
    var showRncSheet by remember { mutableStateOf(false) }
    var showAnfrSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult<PickVisualMediaRequest, List<Uri>>(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
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
    val canUseTheoreticalCoverage =
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.THEORETICAL_COVERAGE) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_THEORETICAL_COVERAGE) &&
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

    // Inséré : les sections opérateur sont plusieurs et leur panneau s'ouvre aussi depuis la fiche
    // du pylône et les réglages. Relire à chaque écriture est le prix d'un affichage cohérent — et
    // il n'y a rien à relire tant que personne ne touche à un réglage.
    val blocksRevision = if (embedded) EmbeddedSiteBlocks.revision.intValue else 0

    var miniMapDefaultMode by remember(blocksRevision) {
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

    // --- VISIBILITÉ DES BLOCS, PAR MODE ---
    // Inséré (section opérateur du mode simplifié) : chaque bloc a sa propre clé, suffixée
    // « _simple ». Cette section est un résumé, plusieurs blocs y sont masqués par défaut, et la
    // régler ne doit pas dérégler la fiche site autonome. Défaut d'une clé encore absente :
    // « masqué » pour les blocs de détail, sinon la valeur déjà choisie sur la fiche autonome.
    fun blockVisibilityKey(prefKey: String): String =
        if (embedded) SitePagePrefs.embeddedKey(prefKey) else prefKey

    fun readBlockVisibility(blockId: String, prefKey: String, standaloneValue: Boolean): Boolean {
        if (!embedded) return standaloneValue
        return SitePagePrefs.readEmbedded(prefs, blockId, prefKey, standaloneValue)
    }

    fun writeBlockVisibility(prefKey: String, value: Boolean) {
        prefs.edit().putBoolean(blockVisibilityKey(prefKey), value).apply()
        // Les sections opérateur frères lisent les mêmes clés : sans ce signal, seule celle d'où
        // vient le réglage bougerait.
        if (embedded) EmbeddedSiteBlocks.bumpRevision()
    }

    // 🚨 MODIFICATION : L'ordre par défaut (photos, speedtest, nav, share...)
    // L'ORDRE reste commun aux deux modes : seule la visibilité diffère.
    var pageSiteOrder by remember(blocksRevision) {
        mutableStateOf(SitePagePrefs.order(prefs))
    }
    var showOperator by remember(blocksRevision) { mutableStateOf(readBlockVisibility("operator", SitePagePrefs.operator.key, SitePagePrefs.operator.read(prefs))) }
    var showBearing by remember(blocksRevision) { mutableStateOf(readBlockVisibility("bearing", SitePagePrefs.bearing.key, SitePagePrefs.read(prefs, SitePagePrefs.bearing))) }
    var showHeight by remember(blocksRevision) { mutableStateOf(readBlockVisibility("height", SitePagePrefs.height.key, SitePagePrefs.read(prefs, SitePagePrefs.height))) }
    var showMap by remember(blocksRevision) { mutableStateOf(readBlockVisibility("map", SitePagePrefs.map.key, SitePagePrefs.map.read(prefs))) }
    var showSupportDetails by remember(blocksRevision) { mutableStateOf(readBlockVisibility("support_details", SitePagePrefs.supportDetails.key, SitePagePrefs.supportDetails.read(prefs))) }
    var showPanelHeights by remember(blocksRevision) { mutableStateOf(readBlockVisibility("panel_heights", SitePagePrefs.panelHeights.key, SitePagePrefs.panelHeights.read(prefs))) }
    var showIds by remember(blocksRevision) { mutableStateOf(readBlockVisibility("ids", SitePagePrefs.ids.key, SitePagePrefs.ids.read(prefs))) }
    var showNetworkIds by remember(blocksRevision) { mutableStateOf(readBlockVisibility("network_ids", SitePagePrefs.networkIds.key, SitePagePrefs.networkIds.read(prefs))) }
    var showOpenMap by remember(blocksRevision) { mutableStateOf(readBlockVisibility("open_map", SitePagePrefs.openMap.key, SitePagePrefs.openMap.read(prefs))) }
    var showElevationProfile by remember(blocksRevision) { mutableStateOf(readBlockVisibility("elevation_profile", SitePagePrefs.elevationProfile.key, SitePagePrefs.elevationProfile.read(prefs))) }
    var showTheoreticalCoverage by remember(blocksRevision) { mutableStateOf(readBlockVisibility("theoretical_coverage", SitePagePrefs.theoreticalCoverage.key, SitePagePrefs.theoreticalCoverage.read(prefs))) }
    var showThroughputCalculator by remember(blocksRevision) { mutableStateOf(readBlockVisibility("throughput_calculator", SitePagePrefs.throughputCalculator.key, SitePagePrefs.throughputCalculator.read(prefs))) }
    var showNav by remember(blocksRevision) { mutableStateOf(readBlockVisibility("nav", SitePagePrefs.nav.key, SitePagePrefs.nav.read(prefs))) }
    var showShare by remember(blocksRevision) { mutableStateOf(readBlockVisibility("share", SitePagePrefs.share.key, SitePagePrefs.share.read(prefs))) }
    var showDates by remember(blocksRevision) { mutableStateOf(readBlockVisibility("dates", SitePagePrefs.dates.key, SitePagePrefs.dates.read(prefs))) }
    var showAddress by remember(blocksRevision) { mutableStateOf(readBlockVisibility("address", SitePagePrefs.address.key, SitePagePrefs.address.read(prefs))) }
    var showFreqs by remember(blocksRevision) { mutableStateOf(readBlockVisibility("freqs", SitePagePrefs.freqs.key, SitePagePrefs.freqs.read(prefs))) }
    var showLinks by remember(blocksRevision) { mutableStateOf(readBlockVisibility("links", SitePagePrefs.links.key, SitePagePrefs.links.read(prefs))) }

    // Photos, statut et speedtest sont portés par des états globaux d'AppConfig (partagés avec le
    // partage et le rapport PDF) : en mode inséré on leur superpose un état local, pour ne pas
    // masquer ces blocs ailleurs dans l'app.
    val photosStandalone by AppConfig.siteShowPhotos
    val statusStandalone by AppConfig.siteShowStatus
    val speedtestStandalone by AppConfig.siteShowSpeedtest
    var showPhotosEmbedded by remember(blocksRevision) { mutableStateOf(readBlockVisibility("photos", "site_show_photos", photosStandalone)) }
    var showStatusEmbedded by remember(blocksRevision) { mutableStateOf(readBlockVisibility("status", "site_show_status", statusStandalone)) }
    var showSpeedtestEmbedded by remember(blocksRevision) { mutableStateOf(readBlockVisibility("speedtest", "site_show_speedtest", speedtestStandalone)) }
    val showPhotos = if (embedded) showPhotosEmbedded else photosStandalone
    val showStatus = if (embedded) showStatusEmbedded else statusStandalone
    val showSpeedtest = if (embedded) showSpeedtestEmbedded else speedtestStandalone

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSiteSettingsSheet by remember { mutableStateOf(false) }
    // Bloc visé par un appui long : le panneau défile jusqu'à sa ligne et la met en surbrillance.
    var settingsHighlightBlock by remember { mutableStateOf<String?>(null) }
    val onCustomizeBlock: (String) -> Unit = { blockId ->
        settingsHighlightBlock = blockId
        showSiteSettingsSheet = true
    }
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
        var siteSiblings: List<LocalisationEntity> = emptyList() // antennes voisines (mêmes coords) pour la propagation ZB
        if (lat != 0.0 && lon != 0.0) {
            val box = repository.getAntennasInBox(
                latNorth = lat + 0.0005,
                lonEast = lon + 0.0005,
                latSouth = lat - 0.0005,
                lonWest = lon - 0.0005
            )
            siteSiblings = box
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
                val anchor = localData
                // Cache mémoire côté repository ; le pull-to-refresh force un rechargement.
                val allHs = repository.getSitesHs(forceRefresh = isRefreshing)

                // Antennes du même site physique (autres opérateurs du support) pour la propagation ZB.
                val siteKey = anchor.physicalSiteKey()
                val siblings = (listOf(anchor) + siteSiblings)
                    .distinctBy { it.idAnfr }
                    .filter { it.physicalSiteKey() == siteKey }

                // Pannes réellement déclarées sur ce site physique (tous opérateurs confondus).
                val declaredOnSite = mutableMapOf<String, fr.geotower.data.models.SiteHsEntity>()
                siblings.forEach { sib ->
                    val sibId = sib.idAnfr.toLongOrNull()
                    val match = allHs.firstOrNull { hs -> sibId != null && hs.idAnfr.toLongOrNull() == sibId }
                    if (match != null) declaredOnSite[sib.idAnfr] = match
                }

                val tempOutageMap = declaredOnSite.toMutableMap()
                // Propagation « zone blanche » : marque les opérateurs ZB sans déclaration.
                fr.geotower.utils.zbPotentialOutagesForSite(siblings, declaredOnSite.values.toList())
                    .forEach { potential -> tempOutageMap.putIfAbsent(potential.idAnfr, potential) }

                // Cet écran n'affiche qu'un opérateur → on ne garde que son entrée (déclarée ou déduite).
                hsDataMap = tempOutageMap.filterKeys { it == anchor.idAnfr }
            } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "Outage data request failed", e) }

            // Densité locale → zoom eNB-Analytics : nb de sites physiques distincts dans ~1,5 km.
            try {
                val latDelta = 0.0135 // ~1,5 km en latitude
                val lonDelta = (latDelta / Math.cos(Math.toRadians(localData.latitude))).coerceIn(latDelta, 0.05)
                val nearbySiteCount = repository.getAntennasInBox(
                    latNorth = localData.latitude + latDelta,
                    lonEast = localData.longitude + lonDelta,
                    latSouth = localData.latitude - latDelta,
                    lonWest = localData.longitude - lonDelta
                ).asSequence().map { it.physicalSiteKey() }.distinct().count()
                enbAnalyticsZoom = enbAnalyticsZoomForSiteCount(nearbySiteCount)
            } catch (e: Exception) { AppLogger.w(TAG_SITE_DETAIL, "eNB zoom density query failed", e) }
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
    LaunchedEffect(antenna?.idAnfr, antenna?.operateur, physique?.idSupport, speedtestBestMetric, refreshTrigger, featureFlags, showSpeedtest) {
        val currentAntenna = antenna
        val currentPhysique = physique
        if (currentAntenna == null || currentAntenna.idAnfr.isBlank()) return@LaunchedEffect

        if (
            // Bloc masqué (défaut d'une section opérateur) : pas d'appel réseau inutile.
            showSpeedtest &&
            canUseSiteSpeedtests &&
            SignalQuestOperators.supportsSpeedtests(currentAntenna.operateur) &&
            CommunityDataPreferences.isSignalQuestSpeedtestEnabled(prefs, currentAntenna.operateur)
        ) {
            speedtestData = null
            isSpeedtestLoading = true
            try {
                speedtestData = fetchBestSignalQuestSpeedtest(
                    operator = currentAntenna.operateur,
                    supportId = currentPhysique?.idSupport,
                    anfrCode = currentAntenna.idAnfr,
                    metric = SignalQuestSpeedtestSortMetric.fromStorageKey(speedtestBestMetric)
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

    // Identifiants réseau : lecture locale d'une base optionnelle, hors du chemin bloquant du site.
    // Le bloc ne s'affiche pas si elle est absente ou si le pylône n'a aucun eNB/gNB rattaché.
    LaunchedEffect(physique?.idSupport, antenna?.operateur, showNetworkIds) {
        networkIds = if (showNetworkIds) {
            fr.geotower.data.EnbRepository(context).getIdentifiersForSupport(
                idSupport = physique?.idSupport,
                operator = antenna?.operateur
            )
        } else {
            fr.geotower.data.EnbRepository.SiteNetworkIds()
        }
    }

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
    // Inséré : c'est la fiche support qui possède le retour, pas chaque section opérateur.
    androidx.activity.compose.BackHandler(
        enabled = !embedded && (isSplitScreen || !safeBackNavigation.isLocked)
    ) {
        handleBackNavigation()
    }

    SiteDetailScaffold(
        embedded = embedded,
        containerColor = mainBgColor,
        topBar = {
            Column(modifier = Modifier.background(mainBgColor)) {
                GeoTowerBackTopBar(
                    onBack = { handleBackNavigation() },
                    backgroundColor = mainBgColor,
                    backEnabled = isSplitScreen || !safeBackNavigation.isLocked,
                    actions = {
                        fr.geotower.ui.components.PageCustomizationHint(
                            page = fr.geotower.utils.PageScrollPrefs.SITE,
                            onOpenSettings = { safeClick { settingsHighlightBlock = null; showSiteSettingsSheet = true } }
                        ) {
                            IconButton(onClick = { safeClick { settingsHighlightBlock = null; showSiteSettingsSheet = true } }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.appstrings_settings_title),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        text = txtSiteDetailsTitle,
                        style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clip(CircleShape).clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(txtIdSupportCopy, antennaId.toString()))
                            ShareHistoryStore.recordFieldCopy(
                                context = context,
                                field = ShareHistoryStore.FIELD_ID_ANFR,
                                value = antennaId.toString(),
                                stationId = antennaId.toString()
                            )
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, "$txtIdCopied : $antennaId", Toast.LENGTH_SHORT).show()
                        }.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(4.dp))
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
                    impliedParentItems = listOfNotNull(
                        // Le mode simplifié n'a pas d'accueil : le fil d'Ariane commence à la
                        // carte (ou à « À proximité »), pas sur une page qui n'existe plus.
                        if (AppConfig.simpleModeActive()) {
                            null
                        } else {
                            GeoTowerBreadcrumbItem(
                                label = txtHomeTitle,
                                icon = Icons.Default.Home,
                                onClick = { navigateToBreadcrumbParent("home") },
                                key = "home"
                            )
                        },
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
            Box(
                modifier = if (embedded) {
                    Modifier.fillMaxWidth().height(EMBEDDED_PLACEHOLDER_HEIGHT)
                } else {
                    Modifier.fillMaxSize().padding(padding).background(mainBgColor)
                },
                contentAlignment = Alignment.Center
            ) {
                GeoTowerLoadingMessage(
                    title = stringResource(R.string.appstrings_site_detail_loading_title),
                    detail = stringResource(R.string.appstrings_site_detail_loading_desc)
                )
            }
        } else {
            val info = antenna!!
            val scrollState = rememberScrollState()

            val opColor = getOperatorColor(info.operateur)
            // eNB-Analytics : table = code PLMN (MCC 208 + MNC) de l'opérateur métropolitain.
            // Orange 208-01, SFR 208-10, Free 208-15, Bouygues 208-20. null = opérateur non couvert.
            val enbAnalyticsTable: Int? = when {
                info.operateur?.contains("ORANGE", true) == true -> 20801
                info.operateur?.contains("FREE", true) == true -> 20815
                info.operateur?.contains("BOUYGUES", true) == true -> 20820
                info.operateur?.contains("SFR", true) == true -> 20810
                else -> null
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
                    showSitesOutOfService = AppConfig.showSitesOutOfService.value,
                    showProjectSites = AppConfig.showProjectSites.value
                )
            } else {
                null
            }
            val activeOperatorKeys = combineOperatorKeyFilters(operatorFilterKeys, siteStatusFilterKeys)
            val isOperatorMutedByFilter = activeOperatorKeys != null &&
                OperatorColors.keysFor(info.operateur).none { operatorKey -> operatorKey in activeOperatorKeys }

            if (showCartoradioSheet) {
                ModalBottomSheet(onDismissRequest = { showCartoradioSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    ExternalOpenOnSheetContent(
                        title = "Cartoradio",
                        subtitle = stringResource(R.string.appstrings_open_on),
                        cardRow = {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cartoradio, modifier = Modifier.weight(1f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCartoradioSheet = false
                                    openWebsiteUrl("https://cartoradio.fr/index.html#/cartographie/lonlat/${info.longitude}/${info.latitude}")
                                }
                        }
                    )
                }
            }

            if (showCellMapperSheet) {
                val cellMapperNetwork = CellMapperLinks.networkFor(info.operateur)
                if (cellMapperNetwork != null) {
                    val cellMapperTechnologies = listOfNotNull(
                        technique?.detailsFrequences,
                        technique?.technologies,
                        info.filtres,
                        info.frequences
                    ).joinToString("\n")
                    val hasCellMapper4G = cellMapperTechnologies.isBlank() ||
                        cellMapperTechnologies.contains("4G", ignoreCase = true) ||
                        cellMapperTechnologies.contains("LTE", ignoreCase = true)
                    val hasCellMapper5G = cellMapperTechnologies.contains("5G", ignoreCase = true) ||
                        cellMapperTechnologies.contains("NR", ignoreCase = true)
                    ModalBottomSheet(onDismissRequest = { showCellMapperSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                        ExternalOpenOnSheetContent(
                            title = "CellMapper",
                            subtitle = txtWhichMap,
                            cardRow = {
                                CommunityCard(title = txtMap4G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cellmapper, isEnabled = hasCellMapper4G, modifier = Modifier.weight(1f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCellMapperSheet = false
                                    openWebsiteUrl(
                                        cellMapperMapUrl(
                                            network = cellMapperNetwork,
                                            type = "LTE",
                                            latitude = info.latitude,
                                            longitude = info.longitude
                                        )
                                    )
                                }
                                CommunityCard(title = txtMap5G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cellmapper, isEnabled = hasCellMapper5G, modifier = Modifier.weight(1f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCellMapperSheet = false
                                    openWebsiteUrl(
                                        cellMapperMapUrl(
                                            network = cellMapperNetwork,
                                            type = "NR",
                                            latitude = info.latitude,
                                            longitude = info.longitude
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (showEnbSheet && enbAnalyticsTable != null) {
                ModalBottomSheet(onDismissRequest = { showEnbSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    ExternalOpenOnSheetContent(
                        title = "eNB-Analytics",
                        subtitle = txtWhichMap,
                        cardRow = {
                            CommunityCard(title = txtMap4G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_enbanalytics, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showEnbSheet = false
                                openWebsiteUrl(enbAnalyticsMapUrl(table = enbAnalyticsTable, generation = 4, latitude = info.latitude, longitude = info.longitude, zoom = enbAnalyticsZoom))
                            }
                            val has5G = listOfNotNull(
                                technique?.technologies,
                                info.filtres,
                                info.frequences
                            ).any { it.contains("5G", ignoreCase = true) }
                            CommunityCard(title = txtMap5G, txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_enbanalytics, isEnabled = has5G, modifier = Modifier.weight(1f)) {
                                if (has5G) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; openWebsiteUrl(enbAnalyticsMapUrl(table = enbAnalyticsTable, generation = 5, latitude = info.latitude, longitude = info.longitude, zoom = enbAnalyticsZoom)) }
                            }
                        },
                        appLauncher = {
                            AppLauncherButton(isInstalled = isEnbAppInstalled, appName = "eNB-Analytics", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showEnbSheet = false; if (isEnbAppInstalled) launchApp(context, "fr.enb_analytics.enb4g") else uriHandler.openUri("https://play.google.com/store/apps/details?id=fr.enb_analytics.enb4g") }
                        }
                    )
                }
            }

            // CellularFR masqué — voir CellularFrApi.ENABLED
            if (showCellularFrSheet) {
                val supportId = physique?.idSupport ?: info.idAnfr // ✅ VRAI ID
                ModalBottomSheet(onDismissRequest = { showCellularFrSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp), top = sizing.spacing(8.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CellularFR", style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = sizing.textStyle(MaterialTheme.typography.bodyMedium))
                        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_cellularfr, isEnabled = supportId.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); showCellularFrSheet = false; openWebsiteUrl("https://cellularfr.fr/site-details.html?siteId=$supportId")
                            }
                        }
                        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
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
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp), top = sizing.spacing(8.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Signal Quest", style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.appstrings_open_on), style = sizing.textStyle(MaterialTheme.typography.bodyMedium))
                            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
                                CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_signalquest, isEnabled = info.idAnfr.isNotBlank(), modifier = Modifier.weight(1f)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSignalQuestSheet = false
                                    openWebsiteUrl(websiteUrl)
                                }
                            }
                            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
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
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp), top = sizing.spacing(8.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RNC Mobile", style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = sizing.textStyle(MaterialTheme.typography.bodyMedium))
                        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
                            CommunityCard(title = stringResource(R.string.appstrings_website), txtUnavailable = txtUnavailable, opColor = opColor, iconRes = R.drawable.logo_rncmobile, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; openWebsiteUrl("https://rncmobile.net/site/${info.latitude},${info.longitude}")
                            }
                        }
                        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                        AppLauncherButton(isInstalled = isRncMobileInstalled, appName = "RNC Mobile", txtOpen = txtOpen, txtInstall = txtInstallApp, useOneUi = useOneUi) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); showRncSheet = false; if (isRncMobileInstalled) launchApp(context, "org.rncteam.rncfreemobile") else uriHandler.openUri("https://play.google.com/store/apps/details?id=org.rncteam.rncfreemobile")
                        }
                    }
                }
            }

            if (showAnfrSheet) {
                ModalBottomSheet(onDismissRequest = { showAnfrSheet = false }, sheetState = sheetState, containerColor = sheetBgColor) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = sizing.spacing(48.dp), start = sizing.spacing(24.dp), end = sizing.spacing(24.dp), top = sizing.spacing(8.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("data.gouv.fr", style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.appstrings_open_on), style = sizing.textStyle(MaterialTheme.typography.bodyMedium))
                        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
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

            SiteDetailScrollContainer(
                embedded = embedded,
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        refreshTrigger++
                    }
                },
                refreshEnabled = antenna != null,
                scrollState = scrollState,
                background = mainBgColor,
                topPadding = padding.calculateTopPadding(),
                contentPadding = sizing.spacing(16.dp)
            ) {
                val formattedAzimuths = remember(info.azimuts) {
                    if (info.azimuts.isNullOrBlank()) ""
                    else {
                        val angles = info.azimuts?.split(",")?.mapNotNull { it.substringBefore("°").trim().toIntOrNull() }?.map { if (it == 360) 0 else it }?.distinct()?.sorted() ?: emptyList()
                        if (angles.isNotEmpty()) angles.joinToString("° - ") + "°" else ""
                    }
                }

                // Le cap et la hauteur restent sur une même ligne tant qu'ils se suivent et sont
                // tous deux affichés : la carte du cap rend alors les deux (voir le bloc « bearing »).
                val bearingHeightPaired = showBearing && showHeight &&
                    pageSiteOrder.indexOf("height") == pageSiteOrder.indexOf("bearing") + 1

                // Station connue du seul relevé hebdomadaire de l'ANFR : presque tous les blocs qui
                // suivent sont vides, faute d'export mensuel. Le bandeau l'annonce avant qu'on les
                // lise, plutôt que de laisser croire à une station mal déclarée.
                // Clé sur la chaîne ENCODÉE : `detailsFrequences` décode à chaque lecture.
                val isWeeklyOnlyStation = remember(technique?.encodedDetailsFrequences, info.frequences) {
                    isAnnouncedOnlyStation(technique?.detailsFrequences ?: info.frequences)
                }
                if (isWeeklyOnlyStation && showWeeklyOnlyBanner) {
                    fr.geotower.ui.components.AnnouncedOnlyStationBanner(blockShape = blockShape)
                    // Hors de la boucle : le bandeau porte lui-même l'écart qui le sépare du premier
                    // bloc, que les CustomizableBlock posent sous eux.
                    Spacer(modifier = Modifier.height(sizing.spacing(16.dp)))
                }

                pageSiteOrder.forEach { block ->
                    // Inséré : on saute les blocs déjà rendus une fois par la fiche support.
                    if (embedded && block in EMBEDDED_HIDDEN_BLOCKS) return@forEach
                    // Appariés, les deux cartes portent chacune leur propre appui long : le bloc
                    // porteur n'en pose pas un troisième par-dessus, qui viserait le cap partout.
                    val blockCustomize = if (block == "bearing" && bearingHeightPaired) null else onCustomizeBlock
                    fr.geotower.ui.components.CustomizableBlock(block, blockCustomize) {
                    when (block) {
                        "status" -> if (showStatus) {
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
                            val realTechStatus = fr.geotower.ui.components.siteServiceStatusGrid(
                                hsEntity = hsEntity,
                                has2G = has2G,
                                has3G = has3G,
                                has4G = has4G,
                                has5G = has5G,
                                is2gProject = is2gProject,
                                is3gProject = is3gProject,
                                is4gProject = is4gProject,
                                is5gProject = is5gProject
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
    val sizing = LocalGeoTowerUiSizing.current
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
                                    Row(modifier = Modifier.fillMaxWidth().padding(sizing.spacing(16.dp)), verticalAlignment = Alignment.CenterVertically) {
                                        val opNameDisplay = info.operateur ?: stringResource(R.string.appstrings_unknown)
                                        val logoRes = getDetailLogoRes(opNameDisplay)

                                        if (logoRes != null) { Image(painter = painterResource(id = logoRes), contentDescription = null, modifier = Modifier.size(sizing.component(72.dp)).clip(RoundedCornerShape(8.dp))) }
                                        else { Box(modifier = Modifier.size(sizing.component(72.dp)).background(getOperatorColor(opNameDisplay), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(text = opNameDisplay.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = sizing.text(24.sp)) } }
                                        Spacer(modifier = Modifier.width(sizing.spacing(16.dp)))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = opNameDisplay, style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
                                            val rawTechs = technique?.technologies?.takeIf { it.isNotBlank() } ?: info.frequences
                                            val realTechs = formatTechnologies(rawTechs, stringResource(R.string.appstrings_unknown))
                                            Text(text = realTechs, style = sizing.textStyle(MaterialTheme.typography.bodyLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (info.isZb == 1) {
                                            Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
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
                                                    fontSize = sizing.text(18.sp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Deux blocs séparés, mais côte à côte tant qu'ils se suivent : c'est la mise
                        // en page d'origine, et deux cartes pleine largeur mangeraient le double de
                        // hauteur pour deux chiffres. Séparés dans l'ordre, chacun prend la largeur.
                        "bearing" -> {
                            if (showBearing) {
                                if (bearingHeightPaired) {
                                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
                                        SiteBearingCard(
                                            bearingStr = bearingStr,
                                            label = txtBearingLabel.replace(" : ", ""),
                                            blockShape = blockShape,
                                            cardBgColor = cardBgColor,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                                .customizableBlock("bearing", onCustomizeBlock)
                                        )
                                        SiteHeightCard(
                                            heightText = formatSiteHeightMeters(physique?.hauteur),
                                            label = txtSupportHeight.replace(" : ", ""),
                                            blockShape = blockShape,
                                            cardBgColor = cardBgColor,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                                .customizableBlock("height", onCustomizeBlock)
                                        )
                                    }
                                } else {
                                    SiteBearingCard(
                                        bearingStr = bearingStr,
                                        label = txtBearingLabel.replace(" : ", ""),
                                        blockShape = blockShape,
                                        cardBgColor = cardBgColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        "height" -> {
                            if (showHeight && !bearingHeightPaired) {
                                SiteHeightCard(
                                    heightText = formatSiteHeightMeters(physique?.hauteur),
                                    label = txtSupportHeight.replace(" : ", ""),
                                    blockShape = blockShape,
                                    cardBgColor = cardBgColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
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
                            // SpeedtestCard lit lui-même l'état global : le gardien local est ici,
                            // sinon le bloc resterait visible dans la section opérateur.
                            if (canUseSiteSpeedtests && showSpeedtest) {
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
                        "network_ids" -> {
                            if (showNetworkIds) {
                                fr.geotower.ui.components.SiteNetworkIdsBlock(
                                    identifiers = networkIds,
                                    cardBgColor = cardBgColor,
                                    blockShape = blockShape,
                                    stationId = antennaId.toString()
                                )
                            }
                        }
                        "open_map" -> {
                            if (showOpenMap) {
                                Button(
                                    onClick = { safeClick { openMapAt(info.latitude, info.longitude) } },
                                    modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                                        Text(stringResource(R.string.appstrings_open_map), fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
                                    }
                                }
                            }
                        }
                        "elevation_profile" -> {
                            if (showElevationProfile && canUseElevationProfile) {
                                Button(
                                    onClick = { safeClick { openElevationProfile(info.idAnfr) } },
                                    modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                                        Text(stringResource(R.string.appstrings_elevation_profile_button), fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
                                    }
                                }
                            }
                        }
                        "theoretical_coverage" -> {
                            if (showTheoreticalCoverage && canUseTheoreticalCoverage) {
                                Button(
                                    onClick = {
                                        safeClick {
                                            if (isSplitScreen) onCloseSplitScreen()
                                            navController.navigate("theoretical_coverage/${info.idAnfr}")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                                        Text(stringResource(R.string.appstrings_coverage_button), fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
                                    }
                                }
                            }
                        }
                        "throughput_calculator" -> {
                            if (showThroughputCalculator && canUseThroughputCalculator) {
                                Button(
                                    onClick = { safeClick { openThroughputCalculator(info.idAnfr) } },
                                    modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                                        Text(stringResource(R.string.appstrings_throughput_calculator_button), fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
                                    }
                                }
                            }
                        }
                        "nav" -> {
                            if (showNav && canUseExternalNavigation) {
                                Button(onClick = { safeClick { showNavigationSheet = true } }, modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)), shape = buttonShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                                        Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                                        Text(txtNavToSite, fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
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
                                    communityPhotos = communityPhotos,
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
                        "freqs" -> { if (showFreqs && canUseSiteFrequencies) fr.geotower.ui.components.SiteFrequenciesBlock(info = info, technique = technique, formattedAzimuths = formattedAzimuths, cardBgColor = cardBgColor, blockShape = blockShape, applyMapFilters = applyMapFilters, showAntennaTypeTable = true) }
                        "links" -> {
                            if (showLinks && canUseSiteExternalLinks && enbAnalyticsTable != null) {
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
                                    onShowCellMapper = {
                                        if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.EXTERNAL_LINKS_CELLMAPPER)) showCellMapperSheet = true
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
                }
                // Gardé même inséré : c'est le SEUL point d'entrée évident vers la personnalisation
                // des blocs opérateur. Le bouton de la barre du haut, lui, ne règle que la fiche
                // du pylône — sans ce pied, les blocs d'ici ne seraient réglables qu'à l'appui long.
                fr.geotower.ui.components.PageCustomizationFooter(
                    onClick = {
                        settingsHighlightBlock = null
                        showSiteSettingsSheet = true
                    }
                )
                // Inséré : la marge de barre système est portée par la fiche support.
                if (!embedded) {
                    Spacer(modifier = Modifier.height(sizing.component(24.dp)).navigationBarsPadding())
                }
            }

            if (showSiteSettingsSheet) {
                SiteSettingsSheet(
                    siteOrder = pageSiteOrder,
                    onOrderChange = {
                        pageSiteOrder = SitePagePrefs.normalizeOrder(it)
                        prefs.edit().putString(SitePagePrefs.ORDER, pageSiteOrder.joinToString(",")).apply()
                        if (embedded) EmbeddedSiteBlocks.bumpRevision()
                    },
                    // Chaque interrupteur écrit sur la clé du mode courant (cf. blockVisibilityKey) :
                    // régler une section opérateur ne touche pas la fiche site autonome.
                    showOperator = showOperator,
                    onOperatorChange = {
                        showOperator = it
                        writeBlockVisibility(SitePagePrefs.operator.key, it)
                    },
                    showBearing = showBearing,
                    onBearingChange = {
                        showBearing = it
                        writeBlockVisibility(SitePagePrefs.bearing.key, it)
                    },
                    showHeight = showHeight,
                    onHeightChange = {
                        showHeight = it
                        writeBlockVisibility(SitePagePrefs.height.key, it)
                    },
                    showMap = showMap,
                    onMapChange = {
                        showMap = it
                        writeBlockVisibility(SitePagePrefs.map.key, it)
                    },
                    showSupportDetails = showSupportDetails,
                    onSupportDetailsChange = {
                        showSupportDetails = it
                        writeBlockVisibility(SitePagePrefs.supportDetails.key, it)
                    },
                    showPhotos = showPhotos,
                    onPhotosChange = {
                        if (embedded) {
                            showPhotosEmbedded = it
                        } else {
                            AppConfig.siteShowPhotos.value = it
                        }
                        writeBlockVisibility("site_show_photos", it)
                    },
                    showPanelHeights = showPanelHeights,
                    onPanelHeightsChange = {
                        showPanelHeights = it
                        writeBlockVisibility(SitePagePrefs.panelHeights.key, it)
                    },
                    showIds = showIds,
                    onIdsChange = {
                        showIds = it
                        writeBlockVisibility(SitePagePrefs.ids.key, it)
                    },
                    showNetworkIds = showNetworkIds,
                    onNetworkIdsChange = {
                        showNetworkIds = it
                        writeBlockVisibility(SitePagePrefs.networkIds.key, it)
                    },
                    showOpenMap = showOpenMap,
                    onOpenMapChange = {
                        showOpenMap = it
                        writeBlockVisibility(SitePagePrefs.openMap.key, it)
                    },
                    showElevationProfile = showElevationProfile,
                    onElevationProfileChange = {
                        showElevationProfile = it
                        writeBlockVisibility(SitePagePrefs.elevationProfile.key, it)
                    },
                    showThroughputCalculator = showThroughputCalculator,
                    onThroughputCalculatorChange = {
                        showThroughputCalculator = it
                        writeBlockVisibility(SitePagePrefs.throughputCalculator.key, it)
                    },
                    showTheoreticalCoverage = showTheoreticalCoverage,
                    onTheoreticalCoverageChange = {
                        showTheoreticalCoverage = it
                        writeBlockVisibility(SitePagePrefs.theoreticalCoverage.key, it)
                    },
                    showNav = showNav,
                    onNavChange = {
                        showNav = it
                        writeBlockVisibility(SitePagePrefs.nav.key, it)
                    },
                    showShare = showShare,
                    onShareChange = {
                        showShare = it
                        writeBlockVisibility(SitePagePrefs.share.key, it)
                    },
                    showDates = showDates,
                    onDatesChange = {
                        showDates = it
                        writeBlockVisibility(SitePagePrefs.dates.key, it)
                    },
                    showAddress = showAddress,
                    onAddressChange = {
                        showAddress = it
                        writeBlockVisibility(SitePagePrefs.address.key, it)
                    },
                    showStatus = showStatus,
                    onStatusChange = {
                        if (embedded) {
                            showStatusEmbedded = it
                        } else {
                            AppConfig.siteShowStatus.value = it
                        }
                        writeBlockVisibility("site_show_status", it)
                    },
                    showSpeedtest = showSpeedtest,
                    onSpeedtestChange = {
                        if (embedded) {
                            showSpeedtestEmbedded = it
                        } else {
                            AppConfig.siteShowSpeedtest.value = it
                        }
                        writeBlockVisibility("site_show_speedtest", it)
                    },
                    showFreqs = showFreqs,
                    onFreqsChange = {
                        showFreqs = it
                        writeBlockVisibility(SitePagePrefs.freqs.key, it)
                    },
                    showLinks = showLinks,
                    onLinksChange = {
                        showLinks = it
                        writeBlockVisibility(SitePagePrefs.links.key, it)
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
                    onDismiss = { showSiteSettingsSheet = false; settingsHighlightBlock = null },
                    onBack = { showSiteSettingsSheet = false; settingsHighlightBlock = null },
                    sheetState = pageSettingsSheetState,
                    useOneUi = uiStyle.useOneUi,
                    bubbleColor = uiStyle.bubbleColor,
                    highlightBlockId = settingsHighlightBlock,
                    // Inséré, le panneau ne règle que cette section-là : le dire dans le titre, sinon
                    // il se confond avec celui de la fiche site autonome.
                    title = if (embedded) stringResource(R.string.appstrings_page_site_embedded_settings) else null
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
                        if (embedded) EmbeddedSiteBlocks.bumpRevision()
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
                        // Même règle que dans la feuille principale : en mode inséré, l'état est
                        // local à la section opérateur (clé suffixée), pas global à l'app.
                        if (embedded) {
                            showPhotosEmbedded = it
                        } else {
                            AppConfig.siteShowPhotos.value = it
                        }
                        writeBlockVisibility("site_show_photos", it)
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
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))) {
                            if (featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_CAMERA)) {
                                Button(
                                    onClick = {
                                        safeClick {
                                            showImageSourceDialog = false
                                            launchCameraCaptureWithStorageCheck()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, null)
                                    Spacer(Modifier.width(sizing.spacing(8.dp)))
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
                                    modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                    shape = buttonShape,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null)
                                    Spacer(Modifier.width(sizing.spacing(8.dp)))
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
                                modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
                                shape = buttonShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Icon(Icons.Default.FolderOpen, null)
                                Spacer(Modifier.width(sizing.spacing(8.dp)))
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
    supportId: String,
    // Mode « inséré » : la station est dépliée dans la fiche du pylône (mode simplifié). Mêmes
    // règles que pour la fiche site — ni barre de titre, ni défilement propre, ni mini-carte.
    embedded: Boolean = false
) {
    val sizing = LocalGeoTowerUiSizing.current
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

    SiteDetailScaffold(
        embedded = embedded,
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_radio_detail_title),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = mainBgColor,
                backEnabled = !safeBackNavigation.isLocked
            )
        }
    ) { padding ->
        val isPlaceholder = isLoading || marker == null
        Box(
            modifier = when {
                // Inséré : hauteur bornée pour l'attente, un fillMaxSize se mesurerait à l'infini
                // dans le défilement de la fiche du pylône.
                embedded && isPlaceholder -> Modifier.fillMaxWidth().height(EMBEDDED_PLACEHOLDER_HEIGHT)
                embedded -> Modifier.fillMaxWidth()
                else -> Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize()
                    .background(mainBgColor)
            }
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
                    modifier = (
                        if (embedded) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                                .fillMaxSize()
                                .geoTowerFadingEdge(scrollState)
                                .pageScrollbar(PageScrollPrefs.SITE, scrollState)
                                .verticalScroll(scrollState)
                                .navigationBarsPadding()
                        }
                        ).padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp))
                ) {
                    // Inséré : la ligne dépliable de la carte « Autres usages » porte déjà la même
                    // icône, le même nom de réseau et le même résumé — l'en-tête ferait doublon.
                    if (!embedded) {
                        RadioSiteHeaderCard(site, cardBgColor, blockShape)
                    }

                    RadioSiteBearingHeightRow(
                        marker = site,
                        bearingStr = bearingStr,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape
                    )

                    // Inséré : la fiche du pylône a déjà sa carte au-dessus, et une MapView par
                    // station dépliée coûterait cher pour montrer le même point.
                    if (!embedded) {
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
                    }

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
                        title = stringResource(R.string.appstrings_radio_share_radio_title),
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
                        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_share_categories), radioSiteUsageSummary(site))
                        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_family), stringResource(RadioServiceMasks.labelRes(site.serviceMask)))
                        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_share_network), site.networkName(context))
                        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_share_systems), site.systemSummary)
                        RadioSiteInfoLine(stringResource(R.string.appstrings_frequencies_title), site.frequencySummary)
                        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_share_emitters), site.emitterCount.takeIf { it > 0 }?.toString())
                        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_share_antennas), site.antennaCount.takeIf { it > 0 }?.toString())
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
                            title = stringResource(R.string.appstrings_radio_share_block_azimuths),
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
                            title = stringResource(R.string.appstrings_radio_share_block_extra),
                            icon = Icons.Default.Info,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape
                        ) {
                            extraDetails.forEach { (label, value) ->
                                RadioSiteInfoLine(label, value)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(sizing.spacing(20.dp)))
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
            // Inséré : les boutons de bord appartiennent à la page hôte, qui porte le défilement.
            if (!embedded) {
                PageScrollEdgeButtons(PageScrollPrefs.SITE, scrollState)
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
    val sizing = LocalGeoTowerUiSizing.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(sizing.component(72.dp))
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
            Spacer(modifier = Modifier.width(sizing.spacing(16.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker.networkName(context),
                    style = sizing.textStyle(MaterialTheme.typography.titleLarge),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(sizing.spacing(3.dp)))
                Text(
                    text = marker.systemSummary ?: stringResource(RadioServiceMasks.labelRes(marker.serviceMask)),
                    style = sizing.textStyle(MaterialTheme.typography.bodyMedium),
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
    val sizing = LocalGeoTowerUiSizing.current
    Card(
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = sizing.spacing(12.dp)),
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
        title = stringResource(R.string.appstrings_radio_share_block_programs),
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
                value = program.detailLabel ?: stringResource(R.string.appstrings_radio_share_program_fallback)
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
    val sizing = LocalGeoTowerUiSizing.current
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp))
    ) {
        val rotation = bearingStr.removeSuffix("\u00B0").toFloatOrNull() ?: 0f
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = blockShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.appstrings_radio_share_cap_measured_short), style = sizing.textStyle(MaterialTheme.typography.labelMedium), textAlign = TextAlign.Center)
                Spacer(Modifier.height(sizing.spacing(8.dp)))
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(sizing.component(40.dp)).rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(sizing.spacing(8.dp)))
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
                modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.appstrings_radio_share_support_height_short), style = sizing.textStyle(MaterialTheme.typography.labelMedium), textAlign = TextAlign.Center)
                Spacer(Modifier.height(sizing.spacing(8.dp)))
                Icon(
                    Icons.Default.VerticalAlignTop,
                    contentDescription = null,
                    modifier = Modifier.size(sizing.component(40.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(sizing.spacing(8.dp)))
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
        title = stringResource(R.string.appstrings_radio_support_details_title),
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        RadioSiteInfoLine(stringResource(R.string.appstrings_support_nature), marker.supportNatureSummary)
        RadioSiteInfoLine(stringResource(R.string.appstrings_owner), marker.supportOwnerSummary)
        RadioSiteInfoLine(stringResource(R.string.appstrings_report_label_distance), "$distanceStr ${stringResource(R.string.appstrings_from_my_position)}")
        RadioSiteInfoLine(stringResource(R.string.appstrings_radio_share_cap_measured_short), bearingStr)
    }
}

@Composable
private fun RadioSiteActionButtons(
    buttonShape: androidx.compose.ui.graphics.Shape,
    onOpenMap: () -> Unit,
    onNavigate: () -> Unit,
    shareButton: @Composable () -> Unit
) {
    val sizing = LocalGeoTowerUiSizing.current
    Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp))) {
        Button(
            onClick = onOpenMap,
            modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Text(stringResource(R.string.appstrings_open_map), fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
            }
        }

        Button(
            onClick = onNavigate,
            modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(sizing.component(24.dp)))
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Text(stringResource(R.string.appstrings_nav_to_site), fontWeight = FontWeight.Bold, fontSize = sizing.text(16.sp))
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
        title = stringResource(R.string.appstrings_site_ids_option),
        icon = Icons.Default.Tag,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        RadioSiteInfoLine(
            label = stringResource(R.string.appstrings_id_support_copy),
            value = marker.supportId,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = context.getString(R.string.appstrings_id_support_copy),
                    value = marker.supportId,
                    toastMessage = context.getString(R.string.appstrings_id_copied),
                    field = ShareHistoryStore.FIELD_ID_SUPPORT,
                    marker = marker
                )
            }
        )
        RadioSiteInfoLine(
            label = stringResource(R.string.appstrings_station_anfr_number),
            value = marker.stationId,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = context.getString(R.string.appstrings_station_anfr),
                    value = marker.stationId,
                    toastMessage = context.getString(R.string.appstrings_id_copied),
                    field = ShareHistoryStore.FIELD_ID_ANFR,
                    marker = marker
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
        title = stringResource(R.string.appstrings_address_copy),
        icon = Icons.Default.Info,
        cardBgColor = cardBgColor,
        blockShape = blockShape
    ) {
        RadioSiteInfoLine(
            label = stringResource(R.string.appstrings_address_copy),
            value = marker.addressSummary,
            onCopy = {
                copyRadioSiteValue(
                    context = context,
                    label = context.getString(R.string.appstrings_address_copy),
                    value = marker.addressSummary,
                    toastMessage = context.getString(R.string.appstrings_address_copied),
                    field = ShareHistoryStore.FIELD_ADDRESS,
                    marker = marker
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
                    toastMessage = context.getString(R.string.appstrings_coords_copied),
                    field = ShareHistoryStore.FIELD_GPS,
                    marker = marker
                )
            }
        )
        RadioSiteInfoLine(stringResource(R.string.appstrings_report_label_distance), "$distanceStr ${stringResource(R.string.appstrings_from_my_position)}")
    }
}

@Composable
private fun RadioSiteInfoLine(label: String, value: String?, onCopy: (() -> Unit)? = null) {
    val cleanValue = value?.takeIf { it.isNotBlank() } ?: return
    fr.geotower.ui.components.InfoLine(label = "$label : ", value = cleanValue, onCopy = onCopy)
}

@Composable
private fun RadioAntennaInfoLine(line: String) {
    val antennaFallback = stringResource(R.string.appstrings_antenna)
    val label = line.substringBefore(":", missingDelimiterValue = antennaFallback).trim()
    val value = line.substringAfter(":", missingDelimiterValue = line).trim()
    RadioSiteInfoLine(label.ifBlank { antennaFallback }, value)
}

private fun formatRadioSiteGps(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.5f%s, %.5f%s", latitude, "\u00B0", longitude, "\u00B0")
}

private fun copyRadioSiteValue(
    context: Context,
    label: String,
    value: String?,
    toastMessage: String,
    field: String,
    marker: RadioMapMarker
) {
    val cleanValue = value?.takeIf { it.isNotBlank() } ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, cleanValue))
    ShareHistoryStore.recordFieldCopy(
        context = context,
        field = field,
        value = cleanValue,
        stationId = marker.stationId,
        supportId = marker.supportId,
        latitude = marker.latitude,
        longitude = marker.longitude,
        kind = ShareHistoryStore.KIND_RADIO_FIELD_COPY
    )
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
private fun ExternalOpenOnSheetContent(
    title: String,
    subtitle: String,
    cardRow: @Composable RowScope.() -> Unit,
    appLauncher: (@Composable () -> Unit)? = null
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = sizing.spacing(48.dp),
                start = sizing.spacing(24.dp),
                end = sizing.spacing(24.dp),
                top = sizing.spacing(8.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = sizing.textStyle(MaterialTheme.typography.titleLarge), fontWeight = FontWeight.Bold)
        Text(subtitle, style = sizing.textStyle(MaterialTheme.typography.bodyMedium))
        Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(16.dp)),
            content = cardRow
        )
        if (appLauncher != null) {
            Spacer(modifier = Modifier.height(sizing.spacing(24.dp)))
            appLauncher()
        }
    }
}

@Composable
private fun AppLauncherButton(isInstalled: Boolean, appName: String, txtOpen: String, txtInstall: String, useOneUi: Boolean, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(sizing.component(56.dp)), shape = oneUiActionButtonShape(useOneUi), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (isInstalled) Icons.AutoMirrored.Filled.Launch else Icons.Default.Download, null, modifier = Modifier.size(sizing.component(20.dp))); Spacer(modifier = Modifier.width(sizing.spacing(8.dp))); Text(if (isInstalled) "$txtOpen $appName" else "$txtInstall $appName", style = sizing.textStyle(MaterialTheme.typography.labelLarge), fontWeight = FontWeight.Bold) }
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

private fun cellMapperMapUrl(network: CellMapperNetwork, type: String, latitude: Double, longitude: Double): String {
    return Uri.Builder()
        .scheme("https")
        .authority("www.cellmapper.net")
        .path("map")
        .appendQueryParameter("MCC", network.mcc.toString())
        .appendQueryParameter("MNC", network.mnc.toString())
        .appendQueryParameter("type", type)
        .appendQueryParameter("latitude", latitude.toString())
        .appendQueryParameter("longitude", longitude.toString())
        .appendQueryParameter("zoom", "18")
        .appendQueryParameter("showTowers", "true")
        .appendQueryParameter("showIcons", "true")
        .appendQueryParameter("showTowerLabels", "true")
        .appendQueryParameter("clusterEnabled", "true")
        .appendQueryParameter("showSectorColours", "true")
        .build()
        .toString()
}

// Zoom eNB-Analytics selon la densité locale (nb de sites physiques dans ~1,5 km). Plafonné à 17 (ville dense).
private fun enbAnalyticsZoomForSiteCount(physicalSiteCount: Int): Int {
    return when {
        physicalSiteCount >= 15 -> 17 // ville dense
        physicalSiteCount >= 7 -> 16  // urbain
        physicalSiteCount >= 3 -> 15  // bourg / péri-urbain
        physicalSiteCount >= 2 -> 14  // rural avec quelques sites
        else -> 13                    // site isolé
    }
}

// eNB-Analytics : page unique paramétrée centrée sur le site.
// table = code PLMN opérateur (20801/20810/20815/20820), xg = génération (4 = 4G, 5 = 5G),
// par1/par2 = latitude/longitude, zoom = niveau de zoom (max 17), par4 = fond coloré (« météo des données »).
private fun enbAnalyticsMapUrl(
    table: Int,
    generation: Int,
    latitude: Double,
    longitude: Double,
    zoom: Int,
    coloredBackground: Boolean = true
): String {
    return Uri.Builder()
        .scheme("https")
        .authority("enb-analytics.fr")
        .path("analytics_a.html")
        .appendQueryParameter("table", table.toString())
        .appendQueryParameter("xg", generation.toString())
        .appendQueryParameter("par1", latitude.toString())
        .appendQueryParameter("par2", longitude.toString())
        .appendQueryParameter("zoom", zoom.coerceIn(1, 17).toString())
        .appendQueryParameter("par4", coloredBackground.toString())
        .build()
        .toString()
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

/**
 * Cartes « Cap » et « Hauteur » de la fiche site. Ce sont deux blocs réglables séparément : la mise
 * en page (côte à côte ou pleine largeur) est décidée par l'appelant, pas par la carte.
 */
@Composable
private fun SiteBearingCard(
    bearingStr: String,
    label: String,
    blockShape: Shape,
    cardBgColor: Color,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val rotation = bearingStr.replace("°", "").toFloatOrNull() ?: 0f
    Card(modifier = modifier, shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = sizing.textStyle(MaterialTheme.typography.labelMedium), textAlign = TextAlign.Center)
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Icon(Icons.Default.Navigation, null, Modifier.size(sizing.component(40.dp)).rotate(rotation), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Text(bearingStr, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SiteHeightCard(
    heightText: String,
    label: String,
    blockShape: Shape,
    cardBgColor: Color,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Card(modifier = modifier, shape = blockShape, colors = CardDefaults.cardColors(containerColor = cardBgColor)) {
        Column(modifier = Modifier.padding(sizing.spacing(16.dp)).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = sizing.textStyle(MaterialTheme.typography.labelMedium), textAlign = TextAlign.Center)
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Icon(Icons.Default.VerticalAlignTop, null, Modifier.size(sizing.component(40.dp)), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(sizing.spacing(8.dp)))
            Text(heightText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CommunityCard(title: String, txtUnavailable: String, opColor: Color, iconRes: Int? = null, modifier: Modifier = Modifier, isEnabled: Boolean = true, onClick: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    OutlinedCard(modifier = modifier.height(sizing.component(120.dp)).clickable(enabled = isEnabled, onClick = onClick), shape = RoundedCornerShape(sizing.component(16.dp)), border = BorderStroke(sizing.component(1.dp), if (isEnabled) SolidColor(opColor) else SolidColor(Color.Gray.copy(0.3f)))) {
        Column(modifier = Modifier.fillMaxSize().padding(sizing.spacing(12.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (iconRes != null) {
                if (iconRes == R.drawable.logo_cellmapper) {
                    Box(
                        modifier = Modifier
                            .size(sizing.component(40.dp))
                            .clip(RoundedCornerShape(sizing.component(8.dp)))
                            .background(Color.White)
                            .padding(sizing.spacing(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(sizing.component(40.dp)).clip(RoundedCornerShape(sizing.component(8.dp))))
                }
            } else {
                Box(modifier = Modifier.size(sizing.component(40.dp)).clip(RoundedCornerShape(sizing.component(8.dp))).background(if (isEnabled) opColor else Color.Gray.copy(0.5f)), contentAlignment = Alignment.Center) { Text(title.takeLast(2), style = sizing.textStyle(MaterialTheme.typography.bodyMedium), color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(sizing.spacing(12.dp))); Text(if (isEnabled) title else txtUnavailable, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = sizing.text(14.sp))
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
