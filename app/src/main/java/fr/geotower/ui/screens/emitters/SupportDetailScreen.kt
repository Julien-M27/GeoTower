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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.collectAsState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.data.workers.SignalQuestUploadScheduler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.data.api.CellularFrApi
import fr.geotower.data.api.SignalQuestOperators
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.SecureScreenEffect
import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.RadioMapMarker
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.data.upload.SignalQuestUploadDraftStore
import fr.geotower.data.upload.SignalQuestUploadTarget
import fr.geotower.data.upload.SignalQuestUploadTargets
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerBreadcrumbItem
import fr.geotower.ui.components.GeoTowerLoadingMessage
import fr.geotower.ui.components.GeoTowerNavigationBreadcrumbBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.InfoLine
import fr.geotower.ui.components.MiniMapViewMode
import fr.geotower.ui.components.RadioShareMenu
import fr.geotower.ui.components.SupportShareMenu
import fr.geotower.ui.components.PageScrollEdgeButtons
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.components.pageScrollbar
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.components.oneUiActionButtonShape
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.settings.CommunityDataSettingsSheet
import fr.geotower.ui.screens.settings.EmbeddedSiteBlocksSettingsSheet
import fr.geotower.ui.screens.settings.MiniMapSettingsSheet
import fr.geotower.ui.screens.settings.SitePhotosSettingsSheet
import fr.geotower.ui.screens.settings.SupportSettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.activeOperatorKeysForSiteStatusFilter
import fr.geotower.utils.combineOperatorKeyFilters
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.PageScrollPrefs
import fr.geotower.utils.SupportPagePrefs
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.geotower.R

private const val TAG_SUPPORT_DETAIL = "GeoTower"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportDetailScreen(
    navController: NavController,
    repository: AnfrRepository,
    radioRepository: RadioRepository,
    siteId: String,
    highlightedOperatorKey: String? = null,
    applyMapFilters: Boolean = false,
    photoDraftId: String? = null,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {},
    // Mode simplifié : opérateur à déplier d'emblée (notification, widget, lien profond ou
    // historique qui visait auparavant la fiche site autonome). Sans lui, on ouvre le premier.
    expandAntennaId: String? = null,
    onAntennaClick: (String) -> Unit = {}
) {
    SecureScreenEffect(RemoteFeatureFlags.SecureScreens.SUPPORT_DETAIL)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiStyle = LocalGeoTowerUiStyle.current
    val sizing = uiStyle.sizing

    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign

    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    var globalMapRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }

    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    // Même source que la fiche site : une seule définition du fond des cartes de premier plan.
    val cardBgColor = uiStyle.cardColor

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
    var radioSupportMarkers by remember { mutableStateOf<List<RadioMapMarker>>(emptyList()) }
    var hsDataMap by remember { mutableStateOf<Map<String, fr.geotower.data.models.SiteHsEntity>>(emptyMap()) } // 🚨 NOUVEAU
    var communityPhotos by remember { mutableStateOf<List<CommunityPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var userLocation by remember { mutableStateOf<Location?>(null) }

    var showNavigationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSupportSettingsSheet by remember { mutableStateOf(false) }
    // Bloc visé par un appui long : le panneau défile jusqu'à sa ligne et la met en surbrillance.
    var settingsHighlightBlock by remember { mutableStateOf<String?>(null) }
    val onCustomizeBlock: (String) -> Unit = { blockId ->
        settingsHighlightBlock = blockId
        showSupportSettingsSheet = true
    }
    /**
     * Vrai pour les blocs qui, en mode simplifié, contiennent des fiches dépliées possédant leurs
     * propres blocs personnalisables. L'appui long y est laissé à l'enfant, sinon deux panneaux
     * s'ouvrent l'un après l'autre (la détection se fait en passe Initial, du parent vers l'enfant).
     */
    fun blockOwnsEmbeddedFiches(blockId: String): Boolean =
        blockId == "operators" && AppConfig.simpleModeActive()
    var showSupportMiniMapSettingsSheet by remember { mutableStateOf(false) }
    var showSupportPhotosSettingsSheet by remember { mutableStateOf(false) }
    var showCommunityDataSettingsSheet by remember { mutableStateOf(false) }
    // Mode simplifié : la page porte deux mises en page, celle du pylône et celle des sections
    // opérateur dépliées. Un seul bouton ne peut pas désigner les deux — il ouvre donc un choix.
    var showCustomizationTargetMenu by remember { mutableStateOf(false) }
    var showEmbeddedSiteSettingsSheet by remember { mutableStateOf(false) }
    val openPageCustomization: () -> Unit = {
        settingsHighlightBlock = null
        if (AppConfig.simpleModeActive()) {
            showCustomizationTargetMenu = true
        } else {
            showSupportSettingsSheet = true
        }
    }
    val scrollState = rememberScrollState()

    LaunchedEffect(siteId, effectiveHighlightedOperatorKey, featureFlags, refreshTrigger) {
        // Retenu hors du bloc IO pour alimenter les chargements réseau qui suivent, sans les
        // faire attendre l'affichage.
        var loadedAntennas: List<LocalisationEntity> = emptyList()
        val forceOutageRefresh = isRefreshing

        // 1️⃣ CHARGEMENT RAPIDE (Base de données) -> Bloque l'écran une fraction de seconde
        try {
            if (!isRefreshing) {
                isLoading = true
            }
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
                val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()

                val initialSearch = repository.getAntennasByExactId(siteId)

                var lat = savedLat
                var lon = savedLon

                val selectedSite = initialSearch.selectSupportAnchor(
                    savedLat = savedLat,
                    savedLon = savedLon,
                    userLocation = getLocalLastKnownLocation(context)
                )

                if (selectedSite != null) {
                    lat = selectedSite.latitude
                    lon = selectedSite.longitude

                    prefs.edit()
                        .putFloat("clicked_lat", lat.toFloat())
                        .putFloat("clicked_lon", lon.toFloat())
                        .apply()
                }

                val selectedPhysiques = selectedSite
                    ?.let { repository.getPhysiqueByAnfr(it.idAnfr) }
                    .orEmpty()
                val selectedPhysique = selectedPhysiques.firstOrNull { it.idSupport.matchesSupportRouteId(siteId) }
                    ?: selectedPhysiques.firstOrNull()
                val canonicalSupportId = selectedPhysique?.idSupport?.takeIf { it.isNotBlank() }
                val supportAntennas = canonicalSupportId
                    ?.let { supportId ->
                        // `initialSearch` est déjà le résultat de cette requête quand l'id de route
                        // est l'id de support canonique (cas de l'ouverture depuis la carte).
                        val bySupport = if (supportId == siteId.trim()) {
                            initialSearch
                        } else {
                            repository.getAntennasByExactId(supportId)
                        }
                        bySupport.filterBySupportId(repository, supportId)
                    }
                    .orEmpty()

                val coordinateAntennas = if (lat != 0.0 && lon != 0.0) {
                    repository.getAntennasInBox(
                        latNorth = lat + 0.0005,
                        lonEast = lon + 0.0005,
                        latSouth = lat - 0.0005,
                        lonWest = lon - 0.0005
                    ).filter { it.latitude.toFloat() == lat.toFloat() && it.longitude.toFloat() == lon.toFloat() }
                } else {
                    emptyList()
                }

                val fetchedAntennas = when {
                    supportAntennas.isNotEmpty() -> supportAntennas
                    initialSearch.size > 1 -> initialSearch
                    else -> coordinateAntennas
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

                val sortedAntennas = fetchedAntennas.distinctBy { it.idAnfr }.sortedBy { antenna ->
                    val matchedOp = OperatorColors.keysFor(antenna.operateur)
                        .minByOrNull { key -> priorityList.indexOf(key).takeIf { it >= 0 } ?: 99 }
                    if (matchedOp != null) priorityList.indexOf(matchedOp) else 99
                }
                antennas = sortedAntennas
                loadedAntennas = sortedAntennas

                val techMap = mutableMapOf<String, TechniqueEntity>()
                if (sortedAntennas.isNotEmpty()) {
                    val fetchedPhysique = selectedPhysique
                        ?: canonicalSupportId?.let { supportId ->
                            sortedAntennas.firstNotNullOfOrNull { antenna ->
                                repository.getPhysiqueByAnfr(antenna.idAnfr)
                                    .firstOrNull { it.idSupport.matchesSupportRouteId(supportId) }
                            }
                        }
                        ?: repository.getPhysiqueByAnfr(sortedAntennas.first().idAnfr).firstOrNull()
                    physique = fetchedPhysique
                    val supportRadioId = fetchedPhysique?.idSupport ?: canonicalSupportId ?: siteId
                    radioSupportMarkers = radioRepository.getMarkersForSupport(supportRadioId)

                    // Une seule requête `IN (…)` au lieu d'une par antenne du support.
                    techMap.putAll(repository.getTechniqueDetailsByIds(sortedAntennas.map { it.idAnfr }))
                } else {
                    radioSupportMarkers = radioRepository.getMarkersForSupport(siteId)
                }
                techniquesMap = techMap
            } // Fin du bloc IO Base de données
        } catch (e: Exception) {
            AppLogger.w(TAG_SUPPORT_DETAIL, "Support details request failed", e)
        } finally {
            isLoading = false // 🚨 L'ÉCRAN S'AFFICHE IMMÉDIATEMENT ICI !
        }

        // 2️⃣ PANNES (En arrière-plan : la source est un fichier national, potentiellement réseau)
        launch(Dispatchers.IO) {
            if (loadedAntennas.isEmpty()) {
                hsDataMap = emptyMap()
                return@launch
            }
            try {
                val allHs = repository.getSitesHs(forceRefresh = forceOutageRefresh)
                val tempOutageMap = mutableMapOf<String, fr.geotower.data.models.SiteHsEntity>()
                loadedAntennas.forEach { ant ->
                    val hsData = allHs.firstOrNull { hs ->
                        val hsId = hs.idAnfr.toLongOrNull()
                        val antId = ant.idAnfr.toLongOrNull()
                        hsId != null && hsId == antId
                    }
                    if (hsData != null) {
                        tempOutageMap[ant.idAnfr] = hsData
                    }
                }
                // Propagation « zone blanche » : si un opérateur du support ZB est déclaré HS,
                // les autres opérateurs ZB sans déclaration passent en « potentiellement en panne ».
                fr.geotower.utils.zbPotentialOutagesForSite(loadedAntennas, tempOutageMap.values.toList())
                    .forEach { potential -> tempOutageMap.putIfAbsent(potential.idAnfr, potential) }
                hsDataMap = tempOutageMap
            } catch (e: Exception) {
                AppLogger.w(TAG_SUPPORT_DETAIL, "Outage data request failed", e)
            }
        }

        // 3️⃣ CHARGEMENT RÉSEAU DES PHOTOS (En arrière-plan, ne bloque pas l'écran)
        launch(Dispatchers.IO) {
            if (!canUseSupportPhotos) {
                communityPhotos = emptyList()
                isRefreshing = false
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
            } finally {
                isRefreshing = false
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

    val locationData = remember(userLocation, antennas, radioSupportMarkers) {
        val target = antennas.firstOrNull()?.let { it.latitude to it.longitude }
            ?: radioSupportMarkers.firstOrNull { !it.isCluster }?.let { it.latitude to it.longitude }

        if (userLocation != null && target != null) {
            val res = FloatArray(2)
            Location.distanceBetween(
                userLocation!!.latitude,
                userLocation!!.longitude,
                target.first,
                target.second,
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
    val txtHomeTitle = stringResource(R.string.help_topic_title_home)
    val txtNearbyTitle = stringResource(R.string.nav_near_antennas)
    val txtMapTitle = stringResource(R.string.nav_map)
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
    val txtInitError = stringResource(R.string.appstrings_init_error)

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val pendingPhotoDraftId = photoDraftId?.takeIf { it.isNotBlank() }
    val pendingSharedPhotoUris = remember(pendingPhotoDraftId) {
        pendingPhotoDraftId?.let { SignalQuestUploadDraftStore.peek(it) } ?: emptyList()
    }
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
        val route = pendingPhotoDraftId
            ?.let { "map?photoDraftId=${Uri.encode(it)}" }
            ?: "map"
        navController.navigate(route)
    }

    var pageSupportOrder by remember { mutableStateOf(SupportPagePrefs.order(prefs)) }
    var showMap by remember { mutableStateOf(SupportPagePrefs.map.read(prefs)) }
    var showDetails by remember { mutableStateOf(SupportPagePrefs.details.read(prefs)) }
    val showPhotos by AppConfig.siteShowPhotos
    var showOpenMap by remember { mutableStateOf(SupportPagePrefs.openMap.read(prefs)) }
    var showNav by remember { mutableStateOf(SupportPagePrefs.nav.read(prefs)) }
    var showShare by remember { mutableStateOf(SupportPagePrefs.share.read(prefs)) }

    // « Partager tous les opérateurs » réutilise l'export PDF du bloc « Partager » : ce compteur le
    // déclenche à distance. Sans ce bloc à l'écran, SupportShareMenu n'est pas composé et le bouton
    // ne mène nulle part — d'où la garde côté liste des opérateurs.
    var supportPdfExportRequest by remember { mutableIntStateOf(0) }
    var showOperators by remember { mutableStateOf(SupportPagePrefs.operators.read(prefs)) }
    val mapFrequencyFilter = if (applyMapFilters) FrequencyFilterSelection.fromMapConfig() else null
    val frequencyMatchedOperatorKeys = remember(antennas, mapFrequencyFilter) {
        if (mapFrequencyFilter == null || mapFrequencyFilter.isFullyEnabled) {
            null
        } else {
            antennas
                .filter { mapFrequencyFilter.matchesAntenna(it) }
                .flatMap { OperatorColors.keysFor(it.operateur) }
                .toSet()
        }
    }
    val operatorMatchedKeys = if (applyMapFilters) {
        AppConfig.selectedOperatorKeys.value
            .takeUnless { selectedKeys -> selectedKeys.containsAll(OperatorColors.defaultVisibleKeys) }
    } else {
        null
    }
    val siteStatusMatchedOperatorKeys = if (applyMapFilters) {
        activeOperatorKeysForSiteStatusFilter(
            antennas = antennas,
            sitesHs = hsDataMap.values,
            showSitesInService = AppConfig.showSitesInService.value,
            showSitesOutOfService = AppConfig.showSitesOutOfService.value,
            showProjectSites = AppConfig.showProjectSites.value
        )
    } else {
        null
    }
    val activeOperatorKeys = combineOperatorKeyFilters(
        frequencyMatchedOperatorKeys,
        operatorMatchedKeys,
        siteStatusMatchedOperatorKeys
    )
    val priorityOperatorKey = effectiveHighlightedOperatorKey ?: activeOperatorKeys?.singleOrNull()
    val navigationTarget = antennas.firstOrNull()?.let { it.latitude to it.longitude }
        ?: radioSupportMarkers.firstOrNull { !it.isCluster }?.let { it.latitude to it.longitude }
    val sharedPhotoUploadOperators = remember(antennas, featureFlags) {
        supportSharedPhotoUploadOperators(antennas, prefs)
    }
    var selectedSharedPhotoOperatorKeys by remember(pendingPhotoDraftId, siteId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    val canUseSharedPhotoUpload =
        featureFlags.isScreenEnabled(RemoteFeatureFlags.Screens.SIGNALQUEST_UPLOAD) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SITE_PHOTO_UPLOAD) &&
            featureFlags.isFeatureEnabled(RemoteFeatureFlags.Features.SIGNALQUEST_UPLOAD) &&
            featureFlags.isActionEnabled(RemoteFeatureFlags.Actions.START_SIGNALQUEST_UPLOAD) &&
            featureFlags.isWorkerEnabled(RemoteFeatureFlags.Workers.SIGNALQUEST_UPLOAD)

    LaunchedEffect(
        pendingPhotoDraftId,
        sharedPhotoUploadOperators.map { it.key }
    ) {
        selectedSharedPhotoOperatorKeys = if (pendingPhotoDraftId != null && sharedPhotoUploadOperators.size == 1) {
            setOf(sharedPhotoUploadOperators.first().key)
        } else {
            emptySet()
        }
    }

    // Opérateurs de CE site ayant déjà un envoi en cours (RUNNING/ENQUEUED) : leur case sera
    // grisée pour empêcher un second envoi simultané vers la même cible.
    val uploadSiteTagId = physique?.idSupport?.takeIf { it.isNotBlank() } ?: siteId
    val activeUploadWorkInfos by remember(uploadSiteTagId) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow("sq_upload_$uploadSiteTagId")
    }.collectAsState(initial = emptyList())
    val uploadingOperatorParams = remember(activeUploadWorkInfos) {
        activeUploadWorkInfos
            .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            .mapNotNull { SignalQuestUploadScheduler.operatorParamFromTags(it.tags) }
            .toSet()
    }

    LaunchedEffect(uploadingOperatorParams) {
        if (uploadingOperatorParams.isNotEmpty()) {
            selectedSharedPhotoOperatorKeys = selectedSharedPhotoOperatorKeys.filterNot { key ->
                sharedPhotoUploadOperators.firstOrNull { it.key == key }?.uploadOperator in uploadingOperatorParams
            }.toSet()
        }
    }

    fun openSharedPhotoUpload() {
        val draftId = pendingPhotoDraftId ?: return
        val selectedOperators = sharedPhotoUploadOperators
            .filter { it.key in selectedSharedPhotoOperatorKeys }
            .filterNot { it.uploadOperator in uploadingOperatorParams }
        val firstOperator = selectedOperators.firstOrNull() ?: return
        val targetAntenna = firstOperator.antenna
        val supportUploadId = physique?.idSupport?.takeIf { it.isNotBlank() } ?: siteId
        // Chaque cible part avec SES azimuts : sur un pylone mutualise, deux operateurs n'ont pas les
        // memes, et l'ecran de confirmation les dessine tous. Deux operateurs locaux peuvent viser la
        // meme cible SignalQuest (SRR et SFR...) : une seule cible, mais tous leurs azimuts.
        val targetsByOperator = linkedMapOf<String, SignalQuestUploadTarget>()
        selectedOperators.forEach { operator ->
            val known = targetsByOperator[operator.uploadOperator]
            targetsByOperator[operator.uploadOperator] = SignalQuestUploadTarget(
                operator = operator.uploadOperator,
                azimuts = SignalQuestUploadTargets.mergeAzimuts(known?.azimuts, operator.azimuts)
            )
        }
        val encodedTargets = Uri.encode(SignalQuestUploadTargets.encode(targetsByOperator.values.toList()))
        val uploadAddress = techniquesMap[targetAntenna.idAnfr]?.adresse?.trim().orEmpty()

        navController.navigate(
            "sq_upload/${Uri.encode(supportUploadId)}/${Uri.encode(firstOperator.uploadOperator)}" +
                "?draftId=${Uri.encode(draftId)}" +
                "&lat=${targetAntenna.latitude}" +
                "&lon=${targetAntenna.longitude}" +
                "&azimuts=${Uri.encode(targetAntenna.azimuts ?: "")}" +
                "&operatorTargets=$encodedTargets" +
                "&address=${Uri.encode(uploadAddress)}" +
                "&keepDraft=true"
        )
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            Column(modifier = Modifier.background(mainBgColor)) {
                GeoTowerBackTopBar(
                    title = txtSupportDetailTitle,
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
                        fr.geotower.ui.components.PageCustomizationHint(
                            page = fr.geotower.utils.PageScrollPrefs.SUPPORT,
                            onOpenSettings = { safeClick { openPageCustomization() } }
                        ) {
                            Box {
                                IconButton(onClick = { safeClick { openPageCustomization() } }) {
                                    // En mode simplifié, une roue dentée se lit « réglages de l'app »
                                    // alors qu'elle ne règle que la mise en page de CETTE fiche. Des
                                    // blocs empilés le disent mieux.
                                    Icon(
                                        if (AppConfig.simpleModeActive()) {
                                            Icons.Outlined.ViewAgenda
                                        } else {
                                            Icons.Default.Settings
                                        },
                                        contentDescription = stringResource(R.string.settings_pages_customization_title),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCustomizationTargetMenu,
                                    onDismissRequest = { showCustomizationTargetMenu = false },
                                    // Le menu Material est presque carré (4 dp) : il détonne à côté
                                    // des cartes et des feuilles de l'app, qui sont franchement
                                    // arrondies. On lui donne la même rondeur qu'elles.
                                    shape = uiStyle.cardShape
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.appstrings_page_support_settings)) },
                                        leadingIcon = { Icon(Icons.Default.VerticalAlignTop, contentDescription = null) },
                                        onClick = {
                                            showCustomizationTargetMenu = false
                                            showSupportSettingsSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.appstrings_page_site_embedded_settings)) },
                                        leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null) },
                                        onClick = {
                                            showCustomizationTargetMenu = false
                                            showEmbeddedSiteSettingsSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                GeoTowerNavigationBreadcrumbBar(
                    navController = navController,
                    currentItem = GeoTowerBreadcrumbItem(
                        label = txtSupportDetailTitle,
                        icon = Icons.Default.VerticalAlignTop,
                        key = "support_detail"
                    ),
                    currentRouteKeys = setOf("support_detail", "site_detail", "site_detail_from_map"),
                    impliedParentItems = listOfNotNull(
                        // Le mode simplifié n'a pas d'accueil : le fil d'Ariane commence à la
                        // carte (ou à « À proximité »), pas sur une page qui n'existe plus.
                        if (AppConfig.simpleModeActive()) {
                            null
                        } else {
                            GeoTowerBreadcrumbItem(
                                label = txtHomeTitle,
                                icon = Icons.Default.Home,
                                onClick = {
                                    if (isSplitScreen) onCloseSplitScreen()
                                    navController.navigate("home") {
                                        launchSingleTop = true
                                    }
                                },
                                key = "home"
                            )
                        },
                        if (applyMapFilters) {
                            GeoTowerBreadcrumbItem(
                                label = txtMapTitle,
                                icon = Icons.Default.Map,
                                onClick = {
                                    if (isSplitScreen) onCloseSplitScreen()
                                    navController.navigate("map") {
                                        launchSingleTop = true
                                    }
                                },
                                key = "map"
                            )
                        } else {
                            GeoTowerBreadcrumbItem(
                                label = txtNearbyTitle,
                                icon = Icons.Default.MyLocation,
                                onClick = {
                                    if (isSplitScreen) onCloseSplitScreen()
                                    navController.navigate("emitters") {
                                        launchSingleTop = true
                                    }
                                },
                                key = "emitters"
                            )
                        }
                    ),
                    onBackStackItemClick = {
                        if (isSplitScreen) onCloseSplitScreen()
                    },
                    backgroundColor = if (useOneUi) cardBgColor else MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    ) { padding ->
        // 🚨 CORRECTION : On applique UNIQUEMENT le padding du haut (top) pour passer sous les boutons en bas !
        GeoTowerPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    isRefreshing = true
                    refreshTrigger++
                }
            },
            enabled = !isLoading,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().background(mainBgColor)
        ) {
            if (isLoading) {
                GeoTowerLoadingMessage(
                    title = stringResource(R.string.appstrings_support_detail_loading_title),
                    detail = stringResource(R.string.appstrings_support_detail_loading_desc),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (antennas.isEmpty() && radioSupportMarkers.isEmpty()) {
                Text(stringResource(R.string.appstrings_no_data_found), modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
            } else if (antennas.isEmpty()) {
                val mainRadio = radioSupportMarkers.firstOrNull { !it.isCluster } ?: radioSupportMarkers.first()
                val radioMiniMapAntenna = remember(mainRadio) {
                    LocalisationEntity(
                        idAnfr = mainRadio.supportId.ifBlank { mainRadio.stationId.ifBlank { mainRadio.id } },
                        operateur = mainRadio.networkName(context),
                        latitude = mainRadio.latitude,
                        longitude = mainRadio.longitude,
                        azimuts = null,
                        codeInsee = null,
                        azimutsFh = null,
                        techMask = 0,
                        bandMask = 0
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState)
                        .pageScrollbar(PageScrollPrefs.SUPPORT, scrollState)
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .padding(bottom = sizing.spacing(32.dp))
                ) {
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

                    pageSupportOrder.forEach { block ->
                        // spacing = 0 : sur cette page, chaque bloc porte déjà sa propre marge.
                        // Bloc « operators » en mode simplifié : son contenu est fait de fiches
                        // opérateur qui ont déjà leurs propres blocs personnalisables. Comme
                        // l'appui long est détecté en passe Initial (parent AVANT enfant), garder
                        // le sien ici ouvrait DEUX panneaux à la suite. Le bloc laisse donc la
                        // main : le panneau qui s'ouvre est celui du bloc réellement visé.
                        val blockCustomize = if (blockOwnsEmbeddedFiches(block)) null else onCustomizeBlock
                        fr.geotower.ui.components.CustomizableBlock(block, blockCustomize, spacing = 0.dp) {
                        when (block) {
                            "map" -> {
                                if (showMap) {
                                    fr.geotower.ui.components.SharedMiniMapCard(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)),
                                        centerLat = mainRadio.latitude,
                                        centerLon = mainRadio.longitude,
                                        mappedAntennas = listOf(radioMiniMapAntenna),
                                        radioMarkers = radioSupportMarkers,
                                        sitesHs = emptyList(),
                                        blockShape = blockShape,
                                        cardBorder = cardBorder,
                                        onMapReady = { globalMapRef = it },
                                        focusOperator = null,
                                        userLocation = userLocation,
                                        defaultViewMode = miniMapDefaultMode,
                                        showViewModeToggle = true,
                                        activeOperatorKeys = null
                                    )
                                }
                            }
                            "details" -> {
                                if (showDetails) {
                                    RadioOnlySupportDetailsSection(
                                        marker = mainRadio,
                                        distanceMeters = distanceMeters,
                                        bearingStr = bearingStr,
                                        cardBgColor = cardBgColor,
                                        blockShape = blockShape
                                    )
                                }
                            }
                            "open_map" -> {
                                if (showOpenMap) {
                                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
                                        Button(
                                            onClick = { safeClick { openMapAt(mainRadio.latitude, mainRadio.longitude) } },
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
                            }
                            "nav" -> {
                                if (showNav && canUseSupportNavigation) {
                                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
                                        Button(
                                            onClick = { safeClick { showNavigationSheet = true } },
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
                                    }
                                }
                            }
                            "share" -> {
                                if (showShare && canUseSupportShare) {
                                    RadioShareMenu(
                                        marker = mainRadio,
                                        markers = radioSupportMarkers,
                                        isSupportShare = true,
                                        distanceStr = distanceStr,
                                        bearingStr = bearingStr,
                                        useOneUi = useOneUi,
                                        buttonShape = buttonShape,
                                        globalMapRef = globalMapRef,
                                        modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))
                                    )
                                }
                            }
                        }
                        }
                    }

                    fr.geotower.ui.components.PageCustomizationFooter(
                        onClick = {
                            settingsHighlightBlock = null
                            showSupportSettingsSheet = true
                        },
                        modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))
                    )

                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
                        fr.geotower.ui.components.SupportRadioPresenceCard(
                            radioMarkers = radioSupportMarkers,
                            cardBgColor = cardBgColor,
                            blockShape = blockShape,
                            onRadioClick = { marker ->
                                safeClick {
                                    if (marker.stationId.isNotBlank() && marker.supportId.isNotBlank()) {
                                        navController.navigate(
                                            "radio_site_detail/${Uri.encode(marker.stationId)}/${Uri.encode(marker.supportId)}"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                val mainInfo = antennas.first()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .geoTowerFadingEdge(scrollState)
                        .pageScrollbar(PageScrollPrefs.SUPPORT, scrollState)
                        .verticalScroll(scrollState)
                        // 🚨 AJOUT : Ajoute un espace à la fin du défilement pour ne pas cacher le dernier élément sous les boutons
                        .navigationBarsPadding()
                        .padding(bottom = sizing.spacing(32.dp))
                ) {
                    Spacer(modifier = Modifier.height(sizing.spacing(8.dp)))

                    if (pendingPhotoDraftId != null && pendingSharedPhotoUris.isNotEmpty() && canUseSharedPhotoUpload) {
                        SupportSharedPhotoUploadCard(
                            photoCount = pendingSharedPhotoUris.size,
                            operators = sharedPhotoUploadOperators,
                            selectedOperatorKeys = selectedSharedPhotoOperatorKeys,
                            uploadingOperatorParams = uploadingOperatorParams,
                            onToggleOperator = { key ->
                                selectedSharedPhotoOperatorKeys = if (key in selectedSharedPhotoOperatorKeys) {
                                    selectedSharedPhotoOperatorKeys - key
                                } else {
                                    selectedSharedPhotoOperatorKeys + key
                                }
                            },
                            onUpload = { safeClick { openSharedPhotoUpload() } },
                            cardBgColor = cardBgColor,
                            blockShape = blockShape,
                            buttonShape = buttonShape
                        )
                    }

                    pageSupportOrder.forEach { block ->
                        // spacing = 0 : sur cette page, chaque bloc porte déjà sa propre marge.
                        // Bloc « operators » en mode simplifié : son contenu est fait de fiches
                        // opérateur qui ont déjà leurs propres blocs personnalisables. Comme
                        // l'appui long est détecté en passe Initial (parent AVANT enfant), garder
                        // le sien ici ouvrait DEUX panneaux à la suite. Le bloc laisse donc la
                        // main : le panneau qui s'ouvre est celui du bloc réellement visé.
                        val blockCustomize = if (blockOwnsEmbeddedFiches(block)) null else onCustomizeBlock
                        fr.geotower.ui.components.CustomizableBlock(block, blockCustomize, spacing = 0.dp) {
                        when (block) {
                            "map" -> {
                                if (showMap) {
                                    fr.geotower.ui.components.SharedMiniMapCard(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp)),
                                        centerLat = mainInfo.latitude,
                                        centerLon = mainInfo.longitude,
                                        mappedAntennas = antennas,
                                        radioMarkers = radioSupportMarkers,
                                        sitesHs = hsDataMap.values.toList(),
                                        blockShape = blockShape,
                                        cardBorder = cardBorder,
                                        onMapReady = { globalMapRef = it },
                                        focusOperator = effectiveHighlightedOperatorKey,
                                        userLocation = userLocation,
                                        defaultViewMode = miniMapDefaultMode,
                                        showViewModeToggle = true,
                                        activeOperatorKeys = activeOperatorKeys
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
                                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp)).padding(bottom = sizing.spacing(8.dp))) {
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
                                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
                                        Button(
                                            onClick = { safeClick { openMapAt(mainInfo.latitude, mainInfo.longitude) } },
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
                            }
                            "nav" -> {
                                if (showNav && canUseSupportNavigation) {
                                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
                                        Button(
                                            onClick = { safeClick { showNavigationSheet = true } },
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
                                    }
                                }
                            }
                            "share" -> {
                                if (showShare && canUseSupportShare) {
                                    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
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
                                            globalMapRef = globalMapRef,
                                            communityPhotos = communityPhotos,
                                            radioMarkers = radioSupportMarkers,
                                            pdfExportRequest = supportPdfExportRequest
                                        )
                                    }
                                }
                            }
                            "operators" -> {
                                if (showOperators) {
                                    // Mode simplifié : les opérateurs se déplient ici même au lieu
                                    // d'ouvrir la fiche site — c'est tout l'objet du mode.
                                    val expandOperators = AppConfig.simpleModeActive()
                                    fr.geotower.ui.components.OperatorsListSection(
                                        antennas = antennas,
                                        techniques = techniquesMap,
                                        hsDataMap = hsDataMap, // 🚨 NOUVEAU
                                        cardBgColor = cardBgColor,
                                        blockShape = blockShape,
                                        useOneUi = useOneUi,
                                        priorityOperatorKey = priorityOperatorKey,
                                        activeOperatorKeys = activeOperatorKeys,
                                        expandable = expandOperators,
                                        initialExpandedAntennaId = expandAntennaId,
                                        // Nul si le bloc « Partager » est masqué : l'export vit
                                        // dans SupportShareMenu, qui ne serait alors pas composé.
                                        onShareAllOperators = if (showShare && canUseSupportShare) {
                                            { safeClick { supportPdfExportRequest++ } }
                                        } else {
                                            null
                                        },
                                        expandedContent = { antenna ->
                                            SiteDetailScreen(
                                                navController = navController,
                                                repository = repository,
                                                antennaId = antenna.idAnfr,
                                                applyMapFilters = applyMapFilters,
                                                embedded = true
                                            )
                                        },
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

                    fr.geotower.ui.components.PageCustomizationFooter(
                        onClick = {
                            settingsHighlightBlock = null
                            showSupportSettingsSheet = true
                        },
                        modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))
                    )

                    if (radioSupportMarkers.isNotEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
                            // Mode simplifié : les stations non mobiles du pylône (TV, radio FM,
                            // faisceaux, réseaux privés) se déplient ici comme les opérateurs.
                            val expandRadioStations = AppConfig.simpleModeActive()
                            fr.geotower.ui.components.SupportRadioPresenceCard(
                                radioMarkers = radioSupportMarkers,
                                cardBgColor = cardBgColor,
                                blockShape = blockShape,
                                expandable = expandRadioStations,
                                expandedContent = { marker ->
                                    if (marker.stationId.isNotBlank() && marker.supportId.isNotBlank()) {
                                        RadioSiteDetailScreen(
                                            navController = navController,
                                            radioRepository = radioRepository,
                                            stationId = marker.stationId,
                                            supportId = marker.supportId,
                                            embedded = true
                                        )
                                    }
                                },
                                onRadioClick = { marker ->
                                    safeClick {
                                        if (marker.stationId.isNotBlank() && marker.supportId.isNotBlank()) {
                                            navController.navigate(
                                                "radio_site_detail/${Uri.encode(marker.stationId)}/${Uri.encode(marker.supportId)}"
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            PageScrollEdgeButtons(PageScrollPrefs.SUPPORT, scrollState)
        }

        if (showNavigationSheet && navigationTarget != null && canUseSupportNavigation) {
            fr.geotower.ui.components.NavigationBottomSheet(
                latitude = navigationTarget.first,
                longitude = navigationTarget.second,
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
                onDismiss = { showSupportSettingsSheet = false; settingsHighlightBlock = null },
                onBack = { showSupportSettingsSheet = false; settingsHighlightBlock = null },
                sheetState = pageSettingsSheetState,
                useOneUi = uiStyle.useOneUi,
                bubbleColor = uiStyle.bubbleColor,
                highlightBlockId = settingsHighlightBlock
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

        // Les blocs des sections opérateur dépliées : le même panneau que leur pied de section,
        // atteignable sans avoir à en déplier une.
        if (showEmbeddedSiteSettingsSheet) {
            EmbeddedSiteBlocksSettingsSheet(
                onDismiss = { showEmbeddedSiteSettingsSheet = false },
                onBack = { showEmbeddedSiteSettingsSheet = false },
                sheetState = pageSettingsSheetState,
                useOneUi = uiStyle.useOneUi,
                bubbleColor = uiStyle.bubbleColor
            )
        }
    }
}

private data class SupportSharedPhotoUploadOperator(
    val key: String,
    val label: String,
    val uploadOperator: String,
    val antenna: LocalisationEntity,
    // Azimuts de TOUTES les stations de cet operateur sur le support : `antenna` n'en porte qu'une,
    // et la confirmation d'envoi doit montrer tout ce qui appartient a l'operateur.
    val azimuts: String
)

private fun supportSharedPhotoUploadOperators(
    antennas: List<LocalisationEntity>,
    prefs: android.content.SharedPreferences
): List<SupportSharedPhotoUploadOperator> {
    val operatorsByKey = linkedMapOf<String, SupportSharedPhotoUploadOperator>()

    antennas.forEach { antenna ->
        OperatorColors.keysFor(antenna.operateur).forEach { key ->
            val known = operatorsByKey[key]
            if (known != null) {
                // Station supplementaire du meme operateur : on garde la premiere pour la position,
                // mais ses azimuts rejoignent ceux deja retenus.
                operatorsByKey[key] = known.copy(
                    azimuts = SignalQuestUploadTargets.mergeAzimuts(known.azimuts, antenna.azimuts)
                )
                return@forEach
            }

            val uploadOperator = SignalQuestOperators.operatorParamFor(key) ?: return@forEach
            if (!CommunityDataPreferences.isSignalQuestPhotosEnabled(prefs, key)) return@forEach

            operatorsByKey[key] = SupportSharedPhotoUploadOperator(
                key = key,
                label = OperatorColors.specForKey(key)?.label ?: uploadOperator,
                uploadOperator = uploadOperator,
                antenna = antenna,
                azimuts = SignalQuestUploadTargets.mergeAzimuts(antenna.azimuts)
            )
        }
    }

    return OperatorColors.orderedKeys.mapNotNull { operatorsByKey[it] } +
        operatorsByKey.values.filterNot { it.key in OperatorColors.orderedKeys }
}

private fun List<LocalisationEntity>.selectSupportAnchor(
    savedLat: Double,
    savedLon: Double,
    userLocation: Location?
): LocalisationEntity? {
    if (isEmpty()) return null

    return firstOrNull {
        kotlin.math.abs(it.latitude - savedLat) < 0.005 &&
            kotlin.math.abs(it.longitude - savedLon) < 0.005
    } ?: userLocation?.let { location ->
        minByOrNull {
            val dLat = it.latitude - location.latitude
            val dLon = it.longitude - location.longitude
            (dLat * dLat) + (dLon * dLon)
        }
    } ?: first()
}

private suspend fun List<LocalisationEntity>.filterBySupportId(
    repository: AnfrRepository,
    supportId: String
): List<LocalisationEntity> {
    if (isEmpty()) return emptyList()
    val filtered = filter { antenna ->
        repository.getPhysiqueByAnfr(antenna.idAnfr)
            .any { it.idSupport.matchesSupportRouteId(supportId) }
    }
    return filtered.ifEmpty { this }
}

private fun String.matchesSupportRouteId(other: String): Boolean {
    val candidate = trim()
    val requested = other.trim()
    if (candidate == requested) return true

    val candidateLong = candidate.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    val requestedLong = requested.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    return candidateLong != null && candidateLong == requestedLong
}

@Composable
private fun SupportSharedPhotoUploadCard(
    photoCount: Int,
    operators: List<SupportSharedPhotoUploadOperator>,
    selectedOperatorKeys: Set<String>,
    uploadingOperatorParams: Set<String>,
    onToggleOperator: (String) -> Unit,
    onUpload: () -> Unit,
    cardBgColor: Color,
    blockShape: RoundedCornerShape,
    buttonShape: androidx.compose.ui.graphics.Shape
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBgColor, blockShape)
                .padding(sizing.spacing(16.dp))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sizing.component(28.dp))
                )
                Spacer(modifier = Modifier.width(sizing.spacing(12.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shared_photo_support_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pluralStringResource(R.plurals.shared_photo_support_ready, photoCount, photoCount),
                        fontSize = sizing.text(13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            if (operators.isEmpty()) {
                Text(
                    text = stringResource(R.string.shared_photo_support_no_operator),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = sizing.text(14.sp)
                )
            } else {
                operators.forEach { operator ->
                    val isUploading = operator.uploadOperator in uploadingOperatorParams
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isUploading) Modifier
                                else Modifier.clickable { onToggleOperator(operator.key) }
                            )
                            .alpha(if (isUploading) 0.45f else 1f)
                            .padding(vertical = sizing.spacing(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = operator.key in selectedOperatorKeys && !isUploading,
                            onCheckedChange = if (isUploading) null else { { onToggleOperator(operator.key) } },
                            enabled = !isUploading
                        )
                        Box(
                            modifier = Modifier
                                .size(sizing.component(10.dp))
                                .background(Color(OperatorColors.colorArgbForKey(operator.key)), RoundedCornerShape(999.dp))
                        )
                        Spacer(modifier = Modifier.width(sizing.spacing(10.dp)))
                        Text(
                            text = operator.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isUploading) {
                            Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                            Text(
                                text = stringResource(R.string.shared_photo_operator_uploading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = sizing.text(12.sp)
                            )
                        }
                    }
                }

                if (selectedOperatorKeys.isEmpty()) {
                    Text(
                        text = stringResource(R.string.shared_photo_support_select_one),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = sizing.text(13.sp),
                        modifier = Modifier.padding(top = sizing.spacing(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(14.dp)))

            Button(
                onClick = onUpload,
                enabled = operators.isNotEmpty() && selectedOperatorKeys.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(sizing.component(54.dp)),
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(sizing.spacing(8.dp)))
                Text(stringResource(R.string.appstrings_upload_photos_prompt), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RadioOnlySupportDetailsSection(
    marker: RadioMapMarker,
    distanceMeters: Float?,
    bearingStr: String,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val context = LocalContext.current
    val txtNotSpecified = stringResource(R.string.appstrings_not_specified)
    val txtIdNumber = stringResource(R.string.appstrings_id_number)
    val txtIdSupportCopy = stringResource(R.string.appstrings_id_support_copy)
    val txtIdCopied = stringResource(R.string.appstrings_id_copied)
    val txtIdUnavailable = stringResource(R.string.appstrings_id_unavailable)
    val txtAddressLabel = stringResource(R.string.appstrings_address_label)
    val txtAddressCopy = stringResource(R.string.appstrings_address_copy)
    val txtAddressCopied = stringResource(R.string.appstrings_address_copied)
    val txtGpsLabel = stringResource(R.string.appstrings_gps_label)
    val txtGpsCoordsCopy = stringResource(R.string.appstrings_gps_coords_copy)
    val txtCoordsCopied = stringResource(R.string.appstrings_coords_copied)
    val txtSupportNature = stringResource(R.string.appstrings_support_nature)
    val txtSupportHeight = stringResource(R.string.appstrings_support_height)
    val txtOwner = stringResource(R.string.appstrings_owner)
    val txtDistanceLabel = stringResource(R.string.appstrings_distance_label)
    val txtFromMyPosition = stringResource(R.string.appstrings_from_my_position)
    val txtBearingLabel = stringResource(R.string.appstrings_bearing_label)
    val isMi = AppConfig.distanceUnit.intValue == 1
    val supportId = marker.supportId.ifBlank { txtNotSpecified }
    val fullAddress = marker.addressSummary ?: txtNotSpecified
    val gpsCoords = String.format(Locale.US, "%.5f\u00B0, %.5f\u00B0", marker.latitude, marker.longitude)
    val cleanGpsCoords = String.format(Locale.US, "%.5f, %.5f", marker.latitude, marker.longitude)
    val displayGpsCoords = String.format(
        Locale.US,
        "%.5f%s, %.5f%s",
        marker.latitude,
        "\u00B0",
        marker.longitude,
        "\u00B0"
    )
    val distanceText = if (distanceMeters != null) {
        if (isMi) {
            val miles = distanceMeters / 1609.34f
            if (miles < 0.1f) {
                "${(distanceMeters * 3.28084f).toInt()} ft"
            } else {
                String.format(Locale.US, "%.2f mi", miles)
            }
        } else {
            if (distanceMeters >= 1000f) {
                String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
            } else {
                "${distanceMeters.toInt()} m"
            }
        }
    } else {
        "--"
    }

    Box(modifier = Modifier.padding(horizontal = sizing.spacing(16.dp), vertical = sizing.spacing(8.dp))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBgColor, blockShape)
                .padding(sizing.spacing(16.dp))
        ) {
            InfoLine(
                label = txtIdNumber,
                value = supportId,
                onCopy = {
                    if (supportId != txtNotSpecified) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(txtIdSupportCopy, supportId))
                        Toast.makeText(context, txtIdCopied, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, txtIdUnavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Spacer(modifier = Modifier.height(sizing.spacing(4.dp)))
            InfoLine(
                label = txtAddressLabel,
                value = fullAddress,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(txtAddressCopy, fullAddress))
                    Toast.makeText(context, txtAddressCopied, Toast.LENGTH_SHORT).show()
                }
            )
            InfoLine(
                label = txtGpsLabel,
                value = displayGpsCoords,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(txtGpsCoordsCopy, cleanGpsCoords))
                    Toast.makeText(context, txtCoordsCopied, Toast.LENGTH_SHORT).show()
                }
            )
            InfoLine(label = txtSupportHeight, value = marker.supportHeightSummary ?: txtNotSpecified)
            InfoLine(label = "$txtSupportNature : ", value = marker.supportNatureSummary ?: txtNotSpecified)
            InfoLine(label = "$txtOwner : ", value = marker.supportOwnerSummary ?: txtNotSpecified)
            InfoLine(label = txtDistanceLabel, value = "$distanceText $txtFromMyPosition")
            InfoLine(label = txtBearingLabel, value = bearingStr)
        }
    }
}

@SuppressLint("MissingPermission")
private fun getLocalLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try { locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time } } catch (e: Exception) { null }
}
