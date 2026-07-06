package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.ui.components.GeoTowerBackTopBar
import fr.geotower.ui.components.GeoTowerPullToRefreshBox
import fr.geotower.ui.components.LiveDatabaseUsageWarningDialog
import fr.geotower.ui.components.LocationUnavailableMessage
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.NominatimApi
import fr.geotower.data.api.NominatimGeoPoint
import fr.geotower.data.config.RemoteFeatureFlags
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.ui.screens.map.FilterToggleButton
import fr.geotower.ui.screens.map.FreqRow
import fr.geotower.ui.screens.map.SectionTitle
import fr.geotower.ui.screens.map.SelectableButton
import fr.geotower.ui.screens.settings.NearbySettingsSheet
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.FrequencyFilterSelection
import fr.geotower.utils.LocationHelper
import fr.geotower.utils.LocationReadiness
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.locationReadiness
import fr.geotower.utils.openAppLocationSettings
import fr.geotower.utils.openLocationSourceSettings
import fr.geotower.utils.rememberLocationReadinessState
import fr.geotower.utils.OperatorLogos
import fr.geotower.utils.formatNearbyDistanceLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.Normalizer
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

private const val TAG_NEAR_EMITTERS = "GeoTower"
private const val NEARBY_RELOAD_DISTANCE_METERS = 100f
private const val NEARBY_REMOTE_SEARCH_DEBOUNCE_MS = 450L
private const val NEARBY_ADDRESS_SEARCH_LIMIT = 1000
private const val NEARBY_GLOBAL_MAPPING_LIMIT = 800
private const val NEARBY_VISIBLE_PAGE_SIZE = 100
private const val NEARBY_RADIUS_UNBOUNDED = 0

data class UiSite(
    val id: Long,
    val idSupport: String?,
    val distance: Int,
    val address: String,
    val description: String,
    val operators: List<String>,
    val latitude: Double,
    val longitude: Double,
    val anfrIds: List<String> = emptyList(),
    val supportIds: List<String> = emptyList(),
    val supportTypes: List<String> = emptyList(),
    val technologies: List<String> = emptyList(),
    val postalCode: String? = null,
    val city: String? = null,
    val fullAddress: String = address,
    val allAddresses: List<String> = emptyList(),
    val technicalText: String = "",
    val supportText: String = "",
    val isZb: Boolean = false,
    val hasUnderground: Boolean = false,
    val searchText: String = ""
)

private data class NearbySearchSuggestion(
    val label: String,
    val query: String
)

private enum class NearbySearchField {
    All,
    Operator,
    Technology,
    SupportType,
    SupportId,
    AnfrId,
    Address,
    City,
    PostalCode,
    Gps,
    Zb
}

private data class NearbySearchSpec(
    val field: NearbySearchField,
    val value: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearEmittersScreen(
    navController: NavController,
    repository: AnfrRepository,
    onSupportClick: ((UiSite, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    // --- DÉTECTION RÉACTIVE DE LA DISPONIBILITÉ DE LA LOCALISATION ---
    // Réévaluée à chaque retour sur l'écran (et en temps réel pour le GPS) : si l'accès à la position
    // est coupé (permission OU services système), on ne peut pas chercher les antennes à proximité
    // → on affiche une alerte centrale plutôt qu'un spinner infini.
    val locationReadinessState = rememberLocationReadinessState()
    val readiness by locationReadinessState
    val isLocationReady = readiness == LocationReadiness.Ready
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // Recalcule tout de suite le verdict complet (permission + services).
        locationReadinessState.value = locationReadiness(context)
        if (!granted) {
            // Refus définitif (« Ne plus demander ») : la boîte système ne réapparaît plus,
            // on renvoie donc vers les réglages de l'app.
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false
            if (!canAskAgain) openAppLocationSettings(context)
        }
    }
    // Selon la cause : on demande la permission OU on ouvre les réglages de localisation (GPS).
    val onFixLocation: () -> Unit = {
        when (readiness) {
            LocationReadiness.PermissionMissing -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            LocationReadiness.ServicesOff -> openLocationSourceSettings(context)
            LocationReadiness.Ready -> {}
        }
    }

    // --- LECTURE RÉACTIVE DU THÈME D'ABORD ---
    val useOneUi = AppConfig.useOneUiDesign
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    val isOled = isOledMode

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val uiStyle = LocalGeoTowerUiStyle.current
    val cardBorder = if (!useOneUi && isDark) uiStyle.cardBorder else null

    val safeClick = rememberSafeClick()

    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    LiveDatabaseUsageWarningDialog(RemoteFeatureFlags.Features.LIVE_API_FR_NEARBY)

    var userLocation by remember { mutableStateOf<Location?>(null) }
    var searchCenter by remember { mutableStateOf<Location?>(null) }
    var isResolvingInitialLocation by remember { mutableStateOf(hasNearbyLocationPermission(context)) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var sites by remember { mutableStateOf<List<UiSite>>(emptyList()) }
    var hasMoreNearbySites by remember { mutableStateOf(false) }
    var hasMoreNearbySitesBeyondRadius by remember { mutableStateOf(false) }
    var remoteSearchQuery by remember { mutableStateOf("") }
    var remoteSearchSites by remember { mutableStateOf<List<UiSite>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var maxItemsToShow by rememberSaveable { mutableIntStateOf(100) }
    var isSearchingRemote by remember { mutableStateOf(false) }
    val unknownAddressText = stringResource(R.string.appstrings_unknown_address)
    val siteAnfrLabel = stringResource(R.string.appstrings_site_anfr_label)

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val featureFlags by RemoteFeatureFlags.config
    val nearbyMaxRadiusKm = featureFlags.limitOrDefault(RemoteFeatureFlags.Limits.NEARBY_MAX_RADIUS_KM, 50).coerceAtLeast(1)
    var showSearchBar by remember { mutableStateOf(prefs.getBoolean("show_search_bar", true)) }
    var showSearchSuggestions by remember { mutableStateOf(prefs.getBoolean("show_search_suggestions", true)) }
    var showNearbySites by remember { mutableStateOf(prefs.getBoolean("show_nearby_sites", true)) }
    var nearbyOrder by remember { mutableStateOf(prefs.getString("nearby_order", "search,sites")!!.split(",")) }
    var nearbySearchRadius by remember { mutableIntStateOf(prefs.getInt("nearby_search_radius", 5).coerceAtMost(nearbyMaxRadiusKm)) }
    var activeNearbySearchRadius by rememberSaveable { mutableIntStateOf(nearbySearchRadius) }
    var showNearbyFrequencyFiltersSheet by remember { mutableStateOf(false) }
    var showNearbySettingsSheet by remember { mutableStateOf(false) }
    val frequencyFiltersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(nearbyMaxRadiusKm) {
        if (nearbySearchRadius > nearbyMaxRadiusKm) {
            nearbySearchRadius = nearbyMaxRadiusKm
            activeNearbySearchRadius = nearbyMaxRadiusKm
            prefs.edit().putInt("nearby_search_radius", nearbyMaxRadiusKm).apply()
        }
    }
    val currentSearchSpec = remember(searchQuery) {
        parseNearbySearchQuery(searchQuery)
    }
    val nearbyFrequencyFilter = FrequencyFilterSelection.fromMapConfig()
    val isZbNearbySearch = currentSearchSpec.field == NearbySearchField.Zb

    // Filtres « Affichage des sites » partagés avec la carte (zone blanche, hors service, souterrains)
    val showSitesInService by AppConfig.showSitesInService
    val showSitesOutOfService by AppConfig.showSitesOutOfService
    val showOnlyZbSites by AppConfig.showOnlyZbSites
    val hideUndergroundSites by AppConfig.hideUndergroundSites

    // Base des sites hors service (HS), comme la carte, pour le filtre « En service / Hors service »
    var sitesHs by remember { mutableStateOf<List<SiteHsEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        sitesHs = withContext(Dispatchers.IO) {
            runCatching { repository.getSitesHs() }.getOrDefault(emptyList())
        }
    }
    val hsOperatorMap = remember(sitesHs) { buildNearbyHsOperatorMap(sitesHs) }
    val showOnlyOutOfServiceSites = showSitesOutOfService && !showSitesInService
    val searchedOperatorKey = remember(searchQuery) {
        if (currentSearchSpec.field == NearbySearchField.Operator) {
            OperatorColors.keyFor(currentSearchSpec.value)
        } else {
            null
        }
    }

    LaunchedEffect(isLocationReady) {
        if (!isLocationReady) {
            isResolvingInitialLocation = false
            return@LaunchedEffect
        }

        // La localisation vient (re)devenir disponible (permission accordée / GPS rallumé) : on relance la résolution.
        isResolvingInitialLocation = true
        try {
            val locationHelper = LocationHelper(context)
            val cachedLocation = locationHelper.getLastLocation()
                ?: withContext(Dispatchers.IO) { getLastKnownLocation(context) }
            if (cachedLocation != null) {
                userLocation = cachedLocation
            }

            val freshLocation = withTimeoutOrNull(2_500L) {
                locationHelper.getCurrentLocation()
            }
            if (freshLocation != null) {
                userLocation = freshLocation
            }
        } finally {
            isResolvingInitialLocation = false
        }
    }

    // --- GESTION DU GPS ---
    DisposableEffect(isLocationReady) {
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

        val hasFineLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            try {
                if (hasFineLocation) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, locationListener)
                }
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, locationListener)
            } catch (e: Exception) { AppLogger.w(TAG_NEAR_EMITTERS, "Location updates could not start", e) }
        }
        onDispose { locationManager.removeUpdates(locationListener) }
    }

    // --- 1 & 2. CHARGEMENT ET TRAITEMENT (Fusionnés pour un chargement fluide) ---
    LaunchedEffect(userLocation) {
        val location = userLocation ?: return@LaunchedEffect
        if (shouldRefreshNearbySearchCenter(searchCenter, location)) {
            searchCenter = Location(location)
        }
    }

    LaunchedEffect(searchCenter, maxItemsToShow, isZbNearbySearch, nearbyFrequencyFilter, showOnlyOutOfServiceSites, sitesHs, activeNearbySearchRadius, refreshTrigger) {
        val currentLoc = searchCenter ?: return@LaunchedEffect

        if (!isRefreshing) {
            isLoading = true
        }

        try {
        withContext(Dispatchers.IO) {
            // A. RÉCUPÉRATION DES DONNÉES
            val loadedSiteLimit = maxItemsToShow + NEARBY_VISIBLE_PAGE_SIZE
            val queryLimit = nearbyFrequencyQueryLimit(
                visibleLimit = loadedSiteLimit,
                frequencyFilter = nearbyFrequencyFilter,
                maxQueryLimit = Int.MAX_VALUE
            )
            val detailBackedBandMask = nearbyFrequencyFilter.detailBackedBandMaskForEnrichment()
            val newAntennas = when {
                // « Hors service uniquement » : on cible directement les sites HS proches (comme la carte),
                // sinon le filtre vide souvent la liste des N sites les plus proches.
                showOnlyOutOfServiceSites -> getNearestHsAntennas(
                    repository = repository,
                    sitesHs = sitesHs,
                    center = currentLoc,
                    limit = queryLimit,
                    detailBackedBandMask = detailBackedBandMask
                )
                isZbNearbySearch -> repository.getNearestZb(
                    lat = currentLoc.latitude,
                    lon = currentLoc.longitude,
                    limit = queryLimit,
                    detailBackedBandMask = detailBackedBandMask
                )
                else -> repository.getNearest(
                    lat = currentLoc.latitude,
                    lon = currentLoc.longitude,
                    limit = queryLimit,
                    detailBackedBandMask = detailBackedBandMask
                )
            }
            val frequencyFilteredAntennas = filterNearbyAntennasByFrequency(newAntennas, nearbyFrequencyFilter)
            val activeRadiusKm = activeNearbySearchRadius.takeIf { it != NEARBY_RADIUS_UNBOUNDED }
            val radiusMeters = activeRadiusKm?.coerceAtLeast(1)?.times(1000f)
            val radiusFilteredAntennas = radiusMeters?.let { maxDistanceMeters ->
                frequencyFilteredAntennas.filter { antenna ->
                    calculateDistance(currentLoc.latitude, currentLoc.longitude, antenna.latitude, antenna.longitude) <= maxDistanceMeters
                }
            } ?: frequencyFilteredAntennas
            val hasAntennasBeyondRadius = radiusMeters != null && frequencyFilteredAntennas.any { antenna ->
                calculateDistance(currentLoc.latitude, currentLoc.longitude, antenna.latitude, antenna.longitude) > radiusMeters
            }

            // B. TRAITEMENT ET FORMATAGE
            val finalSites = if (radiusFilteredAntennas.isNotEmpty()) {
                mapAntennasToUiSites(
                    repository = repository,
                    antennas = radiusFilteredAntennas,
                    referenceLocation = currentLoc,
                    unknownAddressText = unknownAddressText,
                    siteAnfrLabel = siteAnfrLabel,
                    siteLimit = loadedSiteLimit
                ).sortedBy { it.distance }
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                sites = finalSites
                hasMoreNearbySites = finalSites.size > maxItemsToShow ||
                    radiusFilteredAntennas.size >= queryLimit
                hasMoreNearbySitesBeyondRadius = hasAntennasBeyondRadius
            }

            // C. ON ARRÊTE LE CHARGEMENT (Garanti de s'exécuter à 100%)
            withContext(Dispatchers.Main) {
                isLoading = false
                isRefreshing = false
            }
        }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG_NEAR_EMITTERS, "Nearby search refresh failed", e)
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    // ✅ 3. RECHERCHE LOCALE ET GLOBALE (Base de données, Coordonnées, Ville, Adresse, Code Postal)
    // Recherche locale instantanee; les resultats globaux arrivent ensuite depuis un cache separe.
    val filteredSites = remember(
        sites, searchQuery, maxItemsToShow, remoteSearchQuery, remoteSearchSites,
        showSitesInService, showSitesOutOfService, showOnlyZbSites, hideUndergroundSites, hsOperatorMap
    ) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            return@remember sites
                .applyNearbyDisplayFilters(showSitesInService, showSitesOutOfService, showOnlyZbSites, hideUndergroundSites, hsOperatorMap)
                .take(maxItemsToShow)
        }

        val searchSpec = parseNearbySearchQuery(query)
        val localMatches = sites.filter { siteMatchesSearch(it, searchSpec) }
        val matchingRemoteSites = if (remoteSearchQuery == query) remoteSearchSites else emptyList()
        (localMatches + matchingRemoteSites)
            .distinctBy { it.id }
            .applyNearbyDisplayFilters(showSitesInService, showSitesOutOfService, showOnlyZbSites, hideUndergroundSites, hsOperatorMap)
            .sortedBy { it.distance }
            .take(maxItemsToShow)
    }

    // Recherche globale différée (base de données, coordonnées, ville, adresse, code postal).
    LaunchedEffect(searchCenter, searchQuery, nearbyFrequencyFilter, refreshTrigger) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            remoteSearchQuery = ""
            remoteSearchSites = emptyList()
            isSearchingRemote = false
            return@LaunchedEffect
        }

        remoteSearchQuery = ""
        remoteSearchSites = emptyList()

        // A. RECHERCHE LOCALE (Rapide, filtre immédiat de ce qui est autour de l'utilisateur)
        val searchSpec = parseNearbySearchQuery(query)
        val searchValue = searchSpec.value.trim()

        // On affiche immédiatement ce qu'on a trouvé localement pour éviter un écran vide

        // B. RECHERCHE DISTANTE INTELLIGENTE
        // On vérifie si c'est juste le nom d'un opérateur (auquel cas on ne déclenche pas le GPS)
        val fieldSearchesCurrentArea = searchSpec.field in setOf(
            NearbySearchField.Operator,
            NearbySearchField.Technology,
            NearbySearchField.SupportType
        )
        val shouldSearchRemote = !fieldSearchesCurrentArea && (
            searchSpec.latitude != null ||
                searchSpec.field in setOf(
                    NearbySearchField.AnfrId,
                    NearbySearchField.SupportId,
                    NearbySearchField.Address,
                    NearbySearchField.City,
                    NearbySearchField.PostalCode,
                    NearbySearchField.Gps
                ) ||
                (searchSpec.field == NearbySearchField.All && searchValue.length >= 3)
            )

        // On ne lance le scan distant que si ce n'est pas un opérateur ET qu'il y a au moins 3 caractères
        if (shouldSearchRemote) {

            delay(NEARBY_REMOTE_SEARCH_DEBOUNCE_MS) // Petit delai pour ne pas spammer la recherche pendant la frappe
            isSearchingRemote = true

            val referenceLocation = searchCenter ?: userLocation
            withContext(Dispatchers.IO) {
                try {
                    val globalAntennas = mutableListOf<LocalisationEntity>()
                    val detailBackedBandMask = nearbyFrequencyFilter.detailBackedBandMaskForEnrichment()
                    val nearbySearchLimit = nearbyFrequencyQueryLimit(NEARBY_GLOBAL_MAPPING_LIMIT, nearbyFrequencyFilter)
                    var targetLat: Double? = null
                    var targetLon: Double? = null
                    var resultSortLat: Double? = referenceLocation?.latitude
                    var resultSortLon: Double? = referenceLocation?.longitude

                    val isNumeric = searchValue.all { it.isDigit() }
                    val isPostalCode = searchSpec.field == NearbySearchField.PostalCode || (isNumeric && searchValue.length == 5)
                    val isGps = searchSpec.latitude != null && searchSpec.longitude != null
                    val filtersOnWholeAddress = shouldFilterOnWholeAddress(searchSpec, searchValue, isPostalCode)
                    val cityAreaIds = mutableSetOf<String>()
                    var hasCityAreaSearch = false

                    // --- 1. RECHERCHE PAR ID (Base de données) ---
                    // ⚠️ CORRECTION ICI : On ne cherche un ID que si ce n'est PAS un code postal,
                    // PAS un GPS, et que la requête fait au moins 5 caractères (les vrais ID sont longs).
                    if (
                        searchSpec.field == NearbySearchField.AnfrId ||
                        searchSpec.field == NearbySearchField.SupportId ||
                        (!isPostalCode && !isGps && searchValue.any { it.isDigit() } && searchValue.length >= 5)
                    ) {
                        val idResults = repository.searchAntennasById(searchValue)
                        globalAntennas.addAll(idResults)
                    }

                    if (searchSpec.field == NearbySearchField.Address && searchValue.length >= 2 && !isGps) {
                        nearbyDatabaseSearchVariants(searchValue).forEach { variant ->
                            globalAntennas.addAll(
                                repository.searchAntennasByAddress(
                                    query = variant,
                                    limit = NEARBY_ADDRESS_SEARCH_LIMIT
                                )
                            )
                        }
                    }

                    // --- 2. RECHERCHE GÉOGRAPHIQUE (GPS, Ville, Code Postal) ---
                    if (isGps) {
                        targetLat = searchSpec.latitude
                        targetLon = searchSpec.longitude
                    } else if (searchSpec.field in setOf(NearbySearchField.All, NearbySearchField.Address, NearbySearchField.City, NearbySearchField.PostalCode)) {
                        val nominatimArea = if (shouldUseNearbyNominatimCitySearch(searchSpec, searchValue, isPostalCode)) {
                            NominatimApi.searchArea(searchValue)
                        } else {
                            null
                        }

                        if (nominatimArea != null) {
                            hasCityAreaSearch = true
                            resultSortLat = (nominatimArea.latNorth + nominatimArea.latSouth) / 2.0
                            resultSortLon = (nominatimArea.lonEast + nominatimArea.lonWest) / 2.0
                            val boxResults = repository.getAntennasInBox(
                                latNorth = nominatimArea.latNorth,
                                lonEast = nominatimArea.lonEast,
                                latSouth = nominatimArea.latSouth,
                                lonWest = nominatimArea.lonWest,
                                detailBackedBandMask = detailBackedBandMask
                            )
                            val cityResults = if (nominatimArea.polygons.isNotEmpty()) {
                                boxResults.filter { isNearbyPointInPolygons(it.latitude, it.longitude, nominatimArea.polygons) }
                            } else {
                                boxResults
                            }
                            cityAreaIds.addAll(cityResults.map { it.idAnfr })
                            globalAntennas.addAll(cityResults)
                        }
                        // On interroge Google/OSM seulement si c'est un code postal ou si ça contient des lettres (ville/rue)
                        if (!hasCityAreaSearch && (isPostalCode || searchValue.any { it.isLetter() })) {
                            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                            val geoQuery = if (isPostalCode) "$searchValue France" else searchValue

                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocationName(geoQuery, 1)
                            if (!addresses.isNullOrEmpty()) {
                                targetLat = addresses[0].latitude
                                targetLon = addresses[0].longitude
                                resultSortLat = targetLat
                                resultSortLon = targetLon
                            }
                        }
                    }

                    // Si on a trouvé des coordonnées, on récupère le bloc d'antennes autour
                    if (fieldSearchesCurrentArea && referenceLocation != null) {
                        globalAntennas.addAll(
                            repository.getNearest(
                                lat = referenceLocation.latitude,
                                lon = referenceLocation.longitude,
                                limit = nearbySearchLimit,
                                detailBackedBandMask = detailBackedBandMask
                            )
                        )
                    }

                    if (targetLat != null && targetLon != null) {
                        resultSortLat = targetLat
                        resultSortLon = targetLon
                        val nearestResults = repository.getNearest(
                            lat = targetLat,
                            lon = targetLon,
                            limit = nearbySearchLimit,
                            detailBackedBandMask = detailBackedBandMask
                        )
                        globalAntennas.addAll(nearestResults)
                    }

                    // On supprime les doublons éventuels
                    val uniqueGlobalAntennas = globalAntennas
                        .distinctBy { it.idAnfr }
                        .let { antennas ->
                            val sortLat = resultSortLat
                            val sortLon = resultSortLon
                            if (antennas.size > NEARBY_GLOBAL_MAPPING_LIMIT && sortLat != null && sortLon != null) {
                                antennas
                                    .sortedBy { calculateDistance(sortLat, sortLon, it.latitude, it.longitude) }
                                    .take(NEARBY_GLOBAL_MAPPING_LIMIT)
                            } else {
                                antennas.take(NEARBY_GLOBAL_MAPPING_LIMIT)
                            }
                        }

                    // --- FORMATAGE DES RÉSULTATS POUR L'UI ---
                    val frequencyFilteredGlobalAntennas =
                        filterNearbyAntennasByFrequency(uniqueGlobalAntennas, nearbyFrequencyFilter)

                    if (frequencyFilteredGlobalAntennas.isNotEmpty()) {
                        val mappedGlobal = mapAntennasToUiSites(
                            repository = repository,
                            antennas = frequencyFilteredGlobalAntennas,
                            referenceLocation = referenceLocation,
                            unknownAddressText = unknownAddressText,
                            siteAnfrLabel = siteAnfrLabel
                        )

                        val keepsGeoAreaResults = targetLat != null && targetLon != null &&
                                !filtersOnWholeAddress &&
                                searchSpec.field in setOf(
                                    NearbySearchField.All,
                                    NearbySearchField.Address,
                                    NearbySearchField.City,
                                    NearbySearchField.PostalCode,
                                    NearbySearchField.Gps
                                )

                        val filteredGlobal = if (keepsGeoAreaResults) {
                            mappedGlobal
                        } else if (hasCityAreaSearch) {
                            mappedGlobal.filter { site ->
                                site.anfrIds.any { it in cityAreaIds } || siteAddressMatchesSearch(site, searchValue)
                            }
                        } else if (filtersOnWholeAddress) {
                            mappedGlobal.filter { siteAddressMatchesSearch(it, searchValue) }
                        } else {
                            mappedGlobal.filter { siteMatchesSearch(it, searchSpec) }
                        }

                        // ✅ On combine les antennes locales filtrées AVEC les antennes lointaines trouvées,
                        // on retire les doublons et on trie tout ça par distance !
                        withContext(Dispatchers.Main) {
                            remoteSearchSites = filteredGlobal
                            remoteSearchQuery = query
                            isSearchingRemote = false
                        }
                    } else {
                        // Rien trouvé à distance, on se contente des résultats locaux
                        withContext(Dispatchers.Main) {
                            remoteSearchSites = emptyList()
                            remoteSearchQuery = query
                            isSearchingRemote = false
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG_NEAR_EMITTERS, "Remote nearby search failed", e)
                    withContext(Dispatchers.Main) {
                        remoteSearchSites = emptyList()
                        remoteSearchQuery = query
                        isSearchingRemote = false
                    }
                }
            }
        } else {
            // Si la requête est trop courte ou que c'est un opérateur, on coupe le logo de chargement
            isSearchingRemote = false
        }
    }

    // ✅ NOUVEAU : On déclare le state ici
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope() // Si pas déjà déclaré plus haut

    // ✅ CORRECTION : On écoute UNIQUEMENT la barre de recherche, pas le GPS !
    LaunchedEffect(searchQuery) {
        // On remonte en haut seulement si l'utilisateur est en train de faire une recherche manuelle
        if (searchQuery.isNotEmpty() && filteredSites.isNotEmpty() && lazyListState.firstVisibleItemIndex > 0) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        containerColor = mainBgColor,
        topBar = {
            GeoTowerBackTopBar(
                title = stringResource(R.string.appstrings_near_emitters_title),
                onBack = { safeBackNavigation.navigateBack() },
                backgroundColor = mainBgColor,
                backEnabled = !safeBackNavigation.isLocked,
                actionsWidth = 48.dp,
                actions = {
                    IconButton(onClick = { safeClick { showNearbySettingsSheet = true } }) {
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
        // 🚨 CORRECTION : On applique uniquement le padding du haut pour glisser sous les boutons
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            nearbyOrder.forEach { block ->
                when (block) {
                    "search" -> {
                        if (showSearchBar) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        maxItemsToShow = 100
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    placeholder = { Text(stringResource(R.string.appstrings_search_city_or_id)) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onSearch = { focusManager.clearFocus() }
                                    ),
                                    trailingIcon = {
                                        if (isSearchingRemote) {
                                            LoadingIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                                        } else if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, stringResource(R.string.appstrings_clear)) }
                                        }
                                    },
                                    singleLine = true,
                                    shape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedContainerColor = if (useOneUi) cardColor else Color.Transparent,
                                        focusedContainerColor = if (useOneUi) cardColor else Color.Transparent
                                    )
                                )

                                if (showSearchSuggestions) {
                                    NearbyQuickSearchSuggestions(
                                        useOneUi = useOneUi,
                                        onSuggestionClick = { suggestion ->
                                            searchQuery = suggestion
                                            maxItemsToShow = 100
                                        }
                                    )
                                }

                                if (!isLoading && filteredSites.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 32.dp, end = 8.dp, top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pluralStringResource(R.plurals.sites_found, filteredSites.size, filteredSites.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { safeClick { showNearbyFrequencyFiltersSheet = true } }) {
                                            Icon(
                                                Icons.Default.FilterList,
                                                contentDescription = stringResource(R.string.appstrings_filter),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "sites" -> {
                        if (showNearbySites) {
                            GeoTowerPullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    if (!isRefreshing && searchCenter != null) {
                                        isRefreshing = true
                                        refreshTrigger++
                                    }
                                },
                                enabled = searchCenter != null && !isLoading,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                when {
                                    // Localisation indisponible (permission coupée OU GPS éteint) : impossible de
                                    // chercher les antennes à proximité. Une recherche par ville/adresse reste
                                    // possible → on laisse passer ses résultats.
                                    !isLocationReady && filteredSites.isEmpty() && !isSearchingRemote -> {
                                        LocationUnavailableMessage(
                                            readiness = readiness,
                                            onFixClick = onFixLocation,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                    searchCenter == null && filteredSites.isEmpty() && !isSearchingRemote -> {
                                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                            LoadingIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(stringResource(R.string.appstrings_search_gps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    filteredSites.isEmpty() && (isLoading || isSearchingRemote || isResolvingInitialLocation) -> {
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .padding(horizontal = 32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            LoadingIndicator()
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = stringResource(R.string.appstrings_nearby_loading_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(R.string.appstrings_nearby_loading_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    filteredSites.isEmpty() -> {
                                        Text(stringResource(R.string.appstrings_no_sites_found), modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.secondary)
                                    }
                                    else -> {
                                        LazyColumn(
                                            state = lazyListState,
                                            modifier = Modifier.fillMaxSize().geoTowerLazyListFadingEdge(lazyListState),
                                            // 🚨 CORRECTION : On ajoute l'espacement de la barre de navigation à la fin
                                            contentPadding = PaddingValues(
                                                start = 16.dp,
                                                top = 16.dp,
                                                end = 16.dp,
                                                bottom = 16.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(filteredSites, key = { it.idSupport ?: "${it.id}_${it.latitude}_${it.longitude}" }) { site ->
                                                EmitterCard(
                                                    site = site,
                                                    useOneUi = useOneUi,
                                                    cardShape = cardShape,
                                                    cardColor = cardColor,
                                                    cardBorder = cardBorder,
                                                    priorityOperatorKey = searchedOperatorKey
                                                ) {
                                                    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                                                    prefs.edit()
                                                        .putFloat("clicked_lat", site.latitude.toFloat())
                                                        .putFloat("clicked_lon", site.longitude.toFloat())
                                                        .apply()

                                                    if (onSupportClick != null) {
                                                        onSupportClick(site, searchedOperatorKey)
                                                    } else {
                                                        val highlightedOperatorParam = searchedOperatorKey?.let { "?operator=$it" }.orEmpty()
                                                        val supportId = site.idSupport?.takeIf { it.isNotBlank() } ?: site.id.toString()
                                                        navController.navigate("support_detail/${Uri.encode(supportId)}$highlightedOperatorParam")
                                                    }
                                                }
                                            }

                                            item {
                                                val canRemoveRadiusLimit = hasMoreNearbySitesBeyondRadius && (searchQuery.isEmpty() || isZbNearbySearch)
                                                val hasHiddenLoadedSites = sites.size > filteredSites.size
                                                val canShowMoreSites = hasMoreNearbySites ||
                                                    canRemoveRadiusLimit ||
                                                    filteredSites.size >= maxItemsToShow ||
                                                    remoteSearchSites.size >= maxItemsToShow
                                                if (canShowMoreSites) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            safeClick("nearby_load_more_sites") {
                                                                if (!hasHiddenLoadedSites && canRemoveRadiusLimit) {
                                                                    activeNearbySearchRadius = NEARBY_RADIUS_UNBOUNDED
                                                                }
                                                                maxItemsToShow += NEARBY_VISIBLE_PAGE_SIZE
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                                    ) {
                                                        Text(stringResource(R.string.appstrings_load_more_sites), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        val showScrollButtons by androidx.compose.runtime.remember {
                                            androidx.compose.runtime.derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
                                        }
                                        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                                        val buttonBgColor = if (isDark) {
                                            Color(0xFF2C2C2C).copy(alpha = 0.85f)
                                        } else {
                                            Color(0xFFF2F2F2).copy(alpha = 0.85f)
                                        }
                                        val iconColor = MaterialTheme.colorScheme.onSurface

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showScrollButtons,
                                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
                                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                                        ) {
                                            androidx.compose.material3.Surface(
                                                shape = RoundedCornerShape(32.dp),
                                                color = buttonBgColor,
                                                shadowElevation = 0.dp,
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(onClick = {
                                                        coroutineScope.launch { lazyListState.animateScrollToItemSmoothly(0) }
                                                    }) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowUp,
                                                            contentDescription = stringResource(R.string.appstrings_top),
                                                            tint = iconColor
                                                        )
                                                    }

                                                    IconButton(onClick = {
                                                        coroutineScope.launch {
                                                            val lastIndex = lazyListState.layoutInfo.totalItemsCount - 1
                                                            if (lastIndex > 0) lazyListState.animateScrollToItemSmoothly(lastIndex)
                                                        }
                                                    }) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = stringResource(R.string.appstrings_bottom),
                                                            tint = iconColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNearbyFrequencyFiltersSheet) {
        NearbyFrequencyFilterSheet(
            prefs = prefs,
            sheetState = frequencyFiltersSheetState,
            onDismiss = { showNearbyFrequencyFiltersSheet = false }
        )
    }

    if (showNearbySettingsSheet) {
        NearbySettingsSheet(
            nearbyOrder = nearbyOrder,
            onOrderChange = { newOrder ->
                nearbyOrder = newOrder
                prefs.edit().putString("nearby_order", newOrder.joinToString(",")).apply()
            },
            showSearch = showSearchBar,
            onSearchChange = {
                showSearchBar = it
                prefs.edit().putBoolean("show_search_bar", it).apply()
            },
            showSuggestions = showSearchSuggestions,
            onSuggestionsChange = {
                showSearchSuggestions = it
                prefs.edit().putBoolean("show_search_suggestions", it).apply()
            },
            showSites = showNearbySites,
            onSitesChange = {
                showNearbySites = it
                prefs.edit().putBoolean("show_nearby_sites", it).apply()
            },
            searchRadius = nearbySearchRadius,
            onRadiusChange = {
                val safeRadius = it.coerceAtMost(nearbyMaxRadiusKm)
                nearbySearchRadius = safeRadius
                activeNearbySearchRadius = safeRadius
                prefs.edit().putInt("nearby_search_radius", safeRadius).apply()
            },
            onDismiss = { showNearbySettingsSheet = false },
            onBack = { showNearbySettingsSheet = false },
            sheetState = settingsSheetState,
            useOneUi = uiStyle.useOneUi,
            bubbleColor = uiStyle.bubbleColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyFrequencyFilterSheet(
    prefs: SharedPreferences,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var show2G by AppConfig.showTechno2G
    var show3G by AppConfig.showTechno3G
    var show4G by AppConfig.showTechno4G
    var show5G by AppConfig.showTechno5G
    var showFH by AppConfig.showTechnoFH

    var showSitesInService by AppConfig.showSitesInService
    var showSitesOutOfService by AppConfig.showSitesOutOfService
    var showOnlyZbSites by AppConfig.showOnlyZbSites
    var hideUndergroundSites by AppConfig.hideUndergroundSites

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sizing.spacing(24.dp))
                .padding(bottom = sizing.spacing(48.dp))
        ) {
            SectionTitle(stringResource(R.string.appstrings_technologies_title))
            Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)), modifier = Modifier.fillMaxWidth()) {
                SelectableButton("5G", show5G, Modifier.weight(1f)) {
                    show5G = it
                    prefs.edit().putBoolean("show_techno_5g", it).apply()
                }
                SelectableButton("4G", show4G, Modifier.weight(1f)) {
                    show4G = it
                    prefs.edit().putBoolean("show_techno_4g", it).apply()
                }
                SelectableButton("3G", show3G, Modifier.weight(1f)) {
                    show3G = it
                    prefs.edit().putBoolean("show_techno_3g", it).apply()
                }
                SelectableButton("2G", show2G, Modifier.weight(1f)) {
                    show2G = it
                    prefs.edit().putBoolean("show_techno_2g", it).apply()
                }
                SelectableButton("FH", showFH, Modifier.weight(1f)) {
                    showFH = it
                    prefs.edit().putBoolean("show_techno_fh", it).apply()
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(28.dp)))
            SectionTitle(stringResource(R.string.appstrings_frequencies_title))

            androidx.compose.animation.AnimatedVisibility(visible = show5G) {
                Column {
                    FreqRow("5G") {
                        FilterToggleButton("700 MHz", "f5g_700", AppConfig.f5G_700, prefs)
                        FilterToggleButton("1400 MHz (exp)", "f5g_1400", AppConfig.f5G_1400, prefs)
                        FilterToggleButton("2100 MHz", "f5g_2100", AppConfig.f5G_2100, prefs)
                        FilterToggleButton("3500 MHz", "f5g_3500", AppConfig.f5G_3500, prefs)
                        FilterToggleButton("4200 MHz (exp)", "f5g_4200", AppConfig.f5G_4200, prefs)
                        FilterToggleButton("26 GHz (exp)", "f5g_26000", AppConfig.f5G_26000, prefs)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = show4G) {
                Column {
                    FreqRow("4G") {
                        FilterToggleButton("700 MHz", "f4g_700", AppConfig.f4G_700, prefs)
                        FilterToggleButton("800 MHz", "f4g_800", AppConfig.f4G_800, prefs)
                        FilterToggleButton("900 MHz", "f4g_900", AppConfig.f4G_900, prefs)
                        FilterToggleButton("1800 MHz", "f4g_1800", AppConfig.f4G_1800, prefs)
                        FilterToggleButton("2100 MHz", "f4g_2100", AppConfig.f4G_2100, prefs)
                        FilterToggleButton("2600 MHz", "f4g_2600", AppConfig.f4G_2600, prefs)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = show3G) {
                Column {
                    FreqRow("3G") {
                        FilterToggleButton("900 MHz", "f3g_900", AppConfig.f3G_900, prefs)
                        FilterToggleButton("2100 MHz", "f3g_2100", AppConfig.f3G_2100, prefs)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = show2G) {
                Column {
                    FreqRow("2G") {
                        FilterToggleButton("900 MHz", "f2g_900", AppConfig.f2G_900, prefs)
                        FilterToggleButton("1800 MHz", "f2g_1800", AppConfig.f2G_1800, prefs)
                    }
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(28.dp)))
            SectionTitle(stringResource(R.string.appstrings_site_display_title))
            Row(horizontalArrangement = Arrangement.spacedBy(sizing.spacing(12.dp)), modifier = Modifier.fillMaxWidth()) {
                SelectableButton(
                    label = stringResource(R.string.appstrings_sites_in_service_label),
                    isSelected = showSitesInService,
                    modifier = Modifier.weight(1f)
                ) {
                    showSitesInService = it
                    prefs.edit().putBoolean("show_sites_in_service", it).apply()
                }

                SelectableButton(
                    label = stringResource(R.string.appstrings_sites_out_of_service_label),
                    isSelected = showSitesOutOfService,
                    modifier = Modifier.weight(1f),
                    selectedColor = MaterialTheme.colorScheme.error
                ) {
                    showSitesOutOfService = it
                    prefs.edit().putBoolean("show_sites_out_of_service", it).apply()
                }
            }

            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            SelectableButton(
                label = stringResource(R.string.appstrings_show_only_zb_sites_label),
                isSelected = showOnlyZbSites,
                modifier = Modifier.fillMaxWidth(),
                selectedColor = MaterialTheme.colorScheme.tertiary
            ) {
                showOnlyZbSites = it
                prefs.edit().putBoolean(AppConfig.PREF_SHOW_ONLY_ZB_SITES, it).apply()
            }

            Spacer(modifier = Modifier.height(sizing.spacing(12.dp)))

            SelectableButton(
                label = stringResource(R.string.appstrings_hide_underground_sites_label),
                isSelected = hideUndergroundSites,
                modifier = Modifier.fillMaxWidth()
            ) {
                hideUndergroundSites = it
                prefs.edit().putBoolean(AppConfig.PREF_HIDE_UNDERGROUND_SITES, it).apply()
            }
        }
    }
}

private suspend fun LazyListState.animateScrollToItemSmoothly(targetIndex: Int) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return

    val target = targetIndex.coerceIn(0, lastIndex)

    // Longueur du « ralenti » final, en nombre d'items animés jusqu'à la cible.
    val tailItems = 10
    val current = firstVisibleItemIndex

    // 1) On saute quasi tout instantanément, jusqu'à quelques items avant la cible...
    if (kotlin.math.abs(target - current) > tailItems) {
        val jumpTo = if (target > current) target - tailItems else target + tailItems
        scrollToItem(jumpTo.coerceIn(0, lastIndex))
    }

    // 2) ...puis animation de ralenti (décélération) sur les derniers éléments jusqu'à la cible.
    animateScrollToItem(target)
}


@Composable
private fun NearbyQuickSearchSuggestions(
    useOneUi: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    var showSearchHelp by remember { mutableStateOf(false) }
    val suggestions = listOf(
        NearbySearchSuggestion(stringResource(R.string.appstrings_nearby_search_suggestion_city), "ville:"),
        NearbySearchSuggestion("Orange", "op:Orange"),
        NearbySearchSuggestion("SFR", "op:SFR"),
        NearbySearchSuggestion("Bouygues", "op:Bouygues"),
        NearbySearchSuggestion("Free", "op:Free"),
        NearbySearchSuggestion("ZB", "zb"),
        NearbySearchSuggestion("5G", "tech:5G"),
        NearbySearchSuggestion("4G", "tech:4G"),
        NearbySearchSuggestion(stringResource(R.string.appstrings_nearby_search_suggestion_pylon), "type:pylone"),
        NearbySearchSuggestion(stringResource(R.string.appstrings_nearby_search_suggestion_roof), "type:toit"),
        NearbySearchSuggestion("ID ANFR", "anfr:"),
        NearbySearchSuggestion("Support", "support:"),
        NearbySearchSuggestion(stringResource(R.string.appstrings_nearby_search_suggestion_postal_code), "cp:"),
        NearbySearchSuggestion("GPS", "gps:")
    )

    if (showSearchHelp) {
        NearbySearchHelpDialog(onDismiss = { showSearchHelp = false })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = sizing.spacing(16.dp), top = sizing.spacing(8.dp), end = sizing.spacing(16.dp)),
        horizontalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { showSearchHelp = true },
            shape = if (useOneUi) RoundedCornerShape(50) else RoundedCornerShape(sizing.component(16.dp)),
            contentPadding = PaddingValues(horizontal = sizing.spacing(10.dp), vertical = sizing.spacing(6.dp)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = stringResource(R.string.appstrings_nearby_search_help_content_description),
                modifier = Modifier.size(sizing.component(16.dp))
            )
        }

        suggestions.forEach { suggestion ->
            OutlinedButton(
                onClick = { onSuggestionClick(suggestion.query) },
                shape = if (useOneUi) RoundedCornerShape(50) else RoundedCornerShape(sizing.component(16.dp)),
                contentPadding = PaddingValues(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(6.dp)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = suggestion.label,
                    style = sizing.textStyle(MaterialTheme.typography.labelMedium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NearbySearchHelpDialog(onDismiss: () -> Unit) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(sizing.component(24.dp))
            )
        },
        title = {
            Text(
                text = stringResource(R.string.appstrings_nearby_search_help_title),
                style = sizing.textStyle(MaterialTheme.typography.headlineSmall),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(8.dp))) {
                NearbySearchHelpLine("ville:Lyon", stringResource(R.string.appstrings_nearby_search_help_city_desc))
                NearbySearchHelpLine("adresse:rue de Paris", stringResource(R.string.appstrings_nearby_search_help_address_desc))
                NearbySearchHelpLine("cp:75001", stringResource(R.string.appstrings_nearby_search_help_postal_desc))
                NearbySearchHelpLine("gps:48.8566,2.3522", stringResource(R.string.appstrings_nearby_search_help_gps_desc))
                NearbySearchHelpLine("anfr:123456", stringResource(R.string.appstrings_nearby_search_help_anfr_desc))
                NearbySearchHelpLine("support:123456", stringResource(R.string.appstrings_nearby_search_help_support_desc))
                NearbySearchHelpLine("op:Orange", stringResource(R.string.appstrings_nearby_search_help_operator_desc))
                NearbySearchHelpLine("zb", stringResource(R.string.appstrings_nearby_search_help_zb_desc))
                NearbySearchHelpLine("tech:5G", stringResource(R.string.appstrings_nearby_search_help_tech_desc))
                NearbySearchHelpLine("type:pylone", stringResource(R.string.appstrings_nearby_search_help_type_desc))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.appstrings_nearby_search_help_ok), style = sizing.textStyle(MaterialTheme.typography.labelLarge))
            }
        }
    )
}

@Composable
private fun NearbySearchHelpLine(
    code: String,
    description: String
) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    Column(verticalArrangement = Arrangement.spacedBy(sizing.spacing(2.dp))) {
        Text(
            text = code,
            style = sizing.textStyle(MaterialTheme.typography.labelLarge),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = sizing.textStyle(MaterialTheme.typography.bodySmall),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmitterCard(
    site: UiSite,
    useOneUi: Boolean,
    cardShape: Shape,
    cardColor: Color,
    cardBorder: BorderStroke? = null,
    priorityOperatorKey: String? = null,
    onClick: () -> Unit
) {
    val isMi = AppConfig.distanceUnit.intValue == 1
    val distanceLabel = formatNearbyDistanceLabel(site.distance, isMi)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(if (useOneUi) 0.dp else 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = distanceLabel.primaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                distanceLabel.secondaryText?.let { secondaryText ->
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            OperatorGrid(operators = site.operators, priorityOperatorKey = priorityOperatorKey)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = site.address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(text = site.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OperatorGrid(operators: List<String>, priorityOperatorKey: String? = null) {
    val defaultOp = AppConfig.defaultOperator.value.uppercase()
    val baseOrder = OperatorColors.orderedKeys
    val priorityList = mutableListOf<String>()
    fun addPriorityOperator(key: String?) {
        if (key != null && key !in priorityList) priorityList.add(key)
    }

    addPriorityOperator(priorityOperatorKey)
    addPriorityOperator(OperatorColors.keyFor(defaultOp))
    baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

    val parsedOperators = operators.flatMap { OperatorColors.keysFor(it) }.distinct()
    val sortedOperators = priorityList.filter { priorityOp ->
        priorityOp in parsedOperators
    }
    val displayOps = sortedOperators.take(4)

    Column(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GridCell(opName = displayOps.getOrNull(0), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(2.dp))
            GridCell(opName = displayOps.getOrNull(1), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GridCell(opName = displayOps.getOrNull(2), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(2.dp))
            GridCell(opName = displayOps.getOrNull(3), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun GridCell(opName: String?, modifier: Modifier = Modifier) {
    val iconRes = OperatorLogos.drawableRes(opName)
    val spec = OperatorColors.specForKey(opName)

    val cornerShape = RoundedCornerShape(6.dp)
    val fallbackColor = spec?.let { Color(it.colorArgb) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(cornerShape)
            .background(
                when {
                    iconRes != null -> Color.Transparent
                    fallbackColor != null -> fallbackColor.copy(alpha = 0.14f)
                    else -> Color.Gray.copy(alpha = 0.1f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(cornerShape)
            )
        } else if (spec != null && fallbackColor != null) {
            Text(
                text = spec.label.take(1).uppercase(),
                color = fallbackColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val res = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, res)
    return res[0]
}

private fun shouldRefreshNearbySearchCenter(current: Location?, next: Location): Boolean {
    if (current == null) return true
    return calculateDistance(
        current.latitude,
        current.longitude,
        next.latitude,
        next.longitude
    ) >= NEARBY_RELOAD_DISTANCE_METERS
}

private fun filterNearbyAntennasByFrequency(
    antennas: List<LocalisationEntity>,
    frequencyFilter: FrequencyFilterSelection
): List<LocalisationEntity> {
    if (frequencyFilter.isFullyEnabled) return antennas
    return antennas.filter { antenna -> frequencyFilter.matchesAntenna(antenna) }
}

// Une antenne = une ligne (opérateur/ANFR). Plusieurs lignes au même point se regroupent en
// UN site affiché. On sur-échantillonne donc pour qu'il reste assez de sites après regroupement.
private const val NEARBY_ANTENNAS_PER_SITE = 4

private fun nearbyFrequencyQueryLimit(
    visibleLimit: Int,
    frequencyFilter: FrequencyFilterSelection,
    maxQueryLimit: Int = NEARBY_GLOBAL_MAPPING_LIMIT
): Int {
    val safe = visibleLimit.coerceAtLeast(1)
    // 1) Compense le regroupement par site (plusieurs opérateurs sur un même pylône).
    val forGrouping = safe.toLong() * NEARBY_ANTENNAS_PER_SITE
    // 2) Le filtre fréquence retire encore des antennes : on élargit davantage.
    val withFrequency = if (frequencyFilter.isFullyEnabled) forGrouping else forGrouping * 5
    return withFrequency
        .coerceAtMost(maxQueryLimit.toLong())
        .coerceAtLeast(safe.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private suspend fun mapAntennasToUiSites(
    repository: AnfrRepository,
    antennas: List<LocalisationEntity>,
    referenceLocation: Location?,
    unknownAddressText: String,
    siteAnfrLabel: String,
    siteLimit: Int? = null
): List<UiSite> {
    val groupedSites = antennas.groupBy {
        "${String.format(Locale.US, "%.4f", it.latitude)}_${String.format(Locale.US, "%.4f", it.longitude)}"
    }

    // On regroupe d'abord (cheap), puis on ne garde que les N sites les plus proches AVANT
    // l'enrichissement : inutile d'enrichir les centaines d'antennes sur-échantillonnées.
    val selectedGroups = if (siteLimit != null && referenceLocation != null) {
        groupedSites.values
            .sortedBy { group ->
                val main = group.first()
                calculateDistance(referenceLocation.latitude, referenceLocation.longitude, main.latitude, main.longitude)
            }
            .take(siteLimit.coerceAtLeast(1))
    } else {
        groupedSites.values.toList()
    }

    val idAnfrs = selectedGroups.flatten().map { it.idAnfr }.filter { it.isNotBlank() }.distinct()
    val techniquesById = repository.getTechniqueSummariesByIds(idAnfrs)
    val physiquesById = repository.getPhysiqueSummariesByIds(idAnfrs)

    return selectedGroups.map { list ->
        val main = list.first()
        val dist = referenceLocation?.let {
            calculateDistance(it.latitude, it.longitude, main.latitude, main.longitude)
        } ?: 0f

        val operators = list.mapNotNull { it.operateur }
            .flatMap { OperatorColors.keysFor(it) }
            .distinct()
        val isZb = list.any { it.isZb == 1 }
        val hasUnderground = list.any { it.hasUndergroundSupport == 1 }

        val techniques = list.mapNotNull { techniquesById[it.idAnfr] }
        val physiques = list.flatMap { physiquesById[it.idAnfr].orEmpty() }
        val allAddresses = techniques.mapNotNull { it.adresse?.takeIf(String::isNotBlank) }.distinct()
        val fullAddress = allAddresses.firstOrNull()
            ?: unknownAddressText
        val addressParts = splitNearbyAddress(fullAddress, main.idAnfr, siteAnfrLabel)
        val anfrIds = list.map { it.idAnfr }.filter { it.isNotBlank() }.distinct()
        val supportIds = physiques.map { it.idSupport }.filter { it.isNotBlank() }.distinct()
        val supportTypes = physiques.mapNotNull { it.natureSupport?.takeIf(String::isNotBlank) }.distinct()
        val supportText = physiques.flatMap {
            listOfNotNull(it.idSupport, it.natureSupport, it.proprietaire, it.azimutsEtTypes)
        }.joinToString(" ")
        val technicalParts = list.flatMap {
            listOfNotNull(it.filtres, it.frequences, it.azimuts, it.azimutsFh)
        } + techniques.flatMap {
            listOfNotNull(it.technologies, it.statut)
        }
        val technologies = extractNearbyTechnologies(technicalParts)
        val technicalText = (technicalParts + technologies).joinToString(" ")
        val postalCode = extractNearbyPostalCode(fullAddress)
        val city = extractNearbyCity(fullAddress)
        val operatorSearchText = operators.flatMap { key ->
            val spec = OperatorColors.specForKey(key)
            listOfNotNull(key, spec?.label) + spec?.aliases.orEmpty()
        }.joinToString(" ")

        val searchText = listOf(
            fullAddress,
            allAddresses.joinToString(" "),
            addressParts.title,
            addressParts.subtitle,
            postalCode,
            city,
            main.idAnfr,
            main.latitude.toString(),
            main.longitude.toString(),
            operatorSearchText,
            anfrIds.joinToString(" "),
            supportIds.joinToString(" "),
            supportTypes.joinToString(" "),
            supportText,
            technicalText,
            if (isZb) "ZB zone blanche" else null
        ).filterNotNull().joinToString(" ")

        UiSite(
            id = main.idAnfr.toLongOrNull() ?: 0L,
            idSupport = supportIds.firstOrNull(),
            distance = dist.toInt(),
            address = addressParts.title,
            description = addressParts.subtitle,
            operators = operators,
            latitude = main.latitude,
            longitude = main.longitude,
            anfrIds = anfrIds,
            supportIds = supportIds,
            supportTypes = supportTypes,
            technologies = technologies,
            postalCode = postalCode,
            city = city,
            fullAddress = fullAddress,
            allAddresses = allAddresses,
            technicalText = technicalText,
            supportText = supportText,
            isZb = isZb,
            hasUnderground = hasUnderground,
            searchText = searchText
        )
    }
}

private const val NEARBY_HS_OPERATOR_WILDCARD = "*"

private fun normalizeNearbyAnfrId(value: String): String {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.toString() ?: trimmed
}

/** Construit la table ANFR -> opérateurs déclarés hors service, à partir de la base HS (comme la carte). */
private fun buildNearbyHsOperatorMap(sitesHs: List<SiteHsEntity>): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()
    sitesHs.forEach { hs ->
        val id = normalizeNearbyAnfrId(hs.idAnfr)
        if (id.isBlank()) return@forEach
        val parsedOperators = OperatorColors.keysFor(hs.operateur)
        val operators = if (parsedOperators.isEmpty()) listOf(NEARBY_HS_OPERATOR_WILDCARD) else parsedOperators
        result.getOrPut(id) { mutableSetOf() }.addAll(operators)
    }
    return result
}

/**
 * Récupère les antennes des sites HS les plus proches du centre de recherche.
 * On part des coordonnées de la base HS pour trouver les ANFR proches, puis on charge
 * leurs vraies localisations via [AnfrRepository.getAntennasByIdsInBox] (comme la carte).
 */
private suspend fun getNearestHsAntennas(
    repository: AnfrRepository,
    sitesHs: List<SiteHsEntity>,
    center: Location,
    limit: Int,
    detailBackedBandMask: Int
): List<LocalisationEntity> {
    val nearestHs = sitesHs
        .asSequence()
        .filter { it.idAnfr.isNotBlank() && it.latitude != 0.0 && it.longitude != 0.0 }
        .distinctBy { normalizeNearbyAnfrId(it.idAnfr) }
        .sortedBy { calculateDistance(center.latitude, center.longitude, it.latitude, it.longitude) }
        .take(limit.coerceAtLeast(1))
        .toList()
    if (nearestHs.isEmpty()) return emptyList()

    val ids = nearestHs.map { it.idAnfr }
    // Boîte englobant les sites HS retenus (petite marge pour absorber les écarts de coordonnées).
    val margin = 0.02
    return repository.getAntennasByIdsInBox(
        idAnfrs = ids,
        latNorth = nearestHs.maxOf { it.latitude } + margin,
        lonEast = nearestHs.maxOf { it.longitude } + margin,
        latSouth = nearestHs.minOf { it.latitude } - margin,
        lonWest = nearestHs.minOf { it.longitude } - margin,
        detailBackedBandMask = detailBackedBandMask
    )
}

/** Opérateurs du site déclarés hors service dans la base HS (le joker couvre tous les opérateurs du site). */
private fun nearbyHsOperatorsForSite(site: UiSite, hsOperatorMap: Map<String, Set<String>>): Set<String> {
    if (hsOperatorMap.isEmpty()) return emptySet()
    val matched = site.anfrIds.mapNotNull { hsOperatorMap[normalizeNearbyAnfrId(it)] }
    if (matched.isEmpty()) return emptySet()
    if (matched.any { NEARBY_HS_OPERATOR_WILDCARD in it }) return site.operators.toSet()
    return matched.flatten().toSet()
}

private fun List<UiSite>.applyNearbyDisplayFilters(
    showInService: Boolean,
    showOutOfService: Boolean,
    showOnlyZb: Boolean,
    hideUnderground: Boolean,
    hsOperatorMap: Map<String, Set<String>>
): List<UiSite> = filter { site ->
    val statusOk = when {
        showInService && showOutOfService -> true
        !showInService && !showOutOfService -> false
        else -> {
            val hsOperators = nearbyHsOperatorsForSite(site, hsOperatorMap)
            val hasInServiceOperator = site.operators.any { it !in hsOperators }
            val hasOutOfServiceOperator = site.operators.any { it in hsOperators }
            if (showInService) hasInServiceOperator else hasOutOfServiceOperator
        }
    }
    statusOk &&
        (!showOnlyZb || site.isZb) &&
        (!hideUnderground || !site.hasUnderground)
}

private data class NearbyAddressParts(
    val title: String,
    val subtitle: String
)

private fun splitNearbyAddress(fullAddress: String, idAnfr: String, siteAnfrLabel: String): NearbyAddressParts {
    val lastCommaIndex = fullAddress.lastIndexOf(",")
    val title = if (lastCommaIndex != -1) fullAddress.substring(0, lastCommaIndex).trim() else fullAddress
    val subtitle = if (lastCommaIndex != -1) {
        fullAddress.substring(lastCommaIndex + 1).trim()
    } else {
        "$siteAnfrLabel: $idAnfr"
    }
    return NearbyAddressParts(title = title, subtitle = subtitle)
}

private fun parseNearbySearchQuery(raw: String): NearbySearchSpec {
    val trimmed = raw.trim()
    val splitIndex = trimmed.indexOf(':')
    val prefix = if (splitIndex > 0) normalizeNearbySearchCompact(trimmed.substring(0, splitIndex)) else ""
    val value = if (splitIndex > 0) trimmed.substring(splitIndex + 1).trim() else trimmed

    val explicitField = when (prefix) {
        "op", "operateur", "operator", "o" -> NearbySearchField.Operator
        "tech", "technologie", "technology", "reseau", "network", "t" -> NearbySearchField.Technology
        "type", "nature" -> NearbySearchField.SupportType
        "support", "idsupport", "s" -> NearbySearchField.SupportId
        "anfr", "idanfr", "site", "id" -> NearbySearchField.AnfrId
        "cp", "postal", "codepostal", "zip" -> NearbySearchField.PostalCode
        "ville", "city", "commune" -> NearbySearchField.City
        "adresse", "address", "addr" -> NearbySearchField.Address
        "gps", "coord", "coords", "latlon", "latlng" -> NearbySearchField.Gps
        "zb", "zoneblanche" -> NearbySearchField.Zb
        else -> null
    }

    val coordinateMatch = nearbyCoordinateRegex.find(value)
    val lat = coordinateMatch?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
    val lon = coordinateMatch?.groupValues?.getOrNull(2)?.replace(',', '.')?.toDoubleOrNull()
    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
        return NearbySearchSpec(NearbySearchField.Gps, value, lat, lon)
    }

    val inferredField = explicitField ?: inferNearbySearchField(value)
    return NearbySearchSpec(inferredField, value)
}

private val nearbyCoordinateRegex =
    Regex("""^\s*([-+]?\d{1,2}(?:[.,]\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:[.,]\d+)?)\s*$""")

private fun inferNearbySearchField(value: String): NearbySearchField {
    val normalized = normalizeNearbySearchCompact(value)
    return when {
        OperatorColors.all.any { operator ->
            normalized == normalizeNearbySearchCompact(operator.key) ||
                normalized == normalizeNearbySearchCompact(operator.label) ||
                operator.aliases.any { alias -> normalized == normalizeNearbySearchCompact(alias) }
        } -> NearbySearchField.Operator
        normalized in setOf("zb", "zoneblanche") -> NearbySearchField.Zb
        normalized in setOf("2g", "3g", "4g", "5g", "gsm", "umts", "lte", "nr") -> NearbySearchField.Technology
        else -> NearbySearchField.All
    }
}

private fun siteMatchesSearch(site: UiSite, spec: NearbySearchSpec): Boolean {
    val needle = normalizeNearbySearch(spec.value)
    val compactNeedle = normalizeNearbySearchCompact(spec.value)
    if (spec.field == NearbySearchField.Zb) return site.isZb
    if (needle.isBlank()) return true

    fun containsNeedle(values: Iterable<String?>): Boolean {
        return values.any { value -> nearbyTextMatches(value.orEmpty(), needle, compactNeedle) }
    }

    return when (spec.field) {
        NearbySearchField.All -> nearbyTextMatches(site.searchText, needle, compactNeedle)
        NearbySearchField.Operator -> containsNeedle(site.operators)
        NearbySearchField.Technology -> containsNeedle(site.technologies + site.technicalText)
        NearbySearchField.SupportType -> containsNeedle(site.supportTypes + site.supportText)
        NearbySearchField.SupportId -> containsNeedle(site.supportIds + site.idSupport)
        NearbySearchField.AnfrId -> containsNeedle(site.anfrIds + site.id.toString())
        NearbySearchField.Address -> containsNeedle(site.allAddresses + listOf(site.fullAddress, site.address, site.description))
        NearbySearchField.City -> containsNeedle(site.allAddresses + listOf(site.city, site.description, site.fullAddress))
        NearbySearchField.PostalCode -> containsNeedle(site.allAddresses + listOf(site.postalCode, site.description, site.fullAddress))
        NearbySearchField.Zb -> site.isZb
        NearbySearchField.Gps -> containsNeedle(
            listOf(
                site.latitude.toString(),
                site.longitude.toString(),
                String.format(Locale.US, "%.6f,%.6f", site.latitude, site.longitude)
            )
        )
    }
}

private fun shouldFilterOnWholeAddress(
    spec: NearbySearchSpec,
    value: String,
    isPostalCode: Boolean
): Boolean {
    return when (spec.field) {
        NearbySearchField.Address,
        NearbySearchField.City,
        NearbySearchField.PostalCode -> true
        NearbySearchField.All -> !isPostalCode && value.any(Char::isLetter) && value.none(Char::isDigit)
        else -> false
    }
}

private fun siteAddressMatchesSearch(site: UiSite, value: String): Boolean {
    val needle = normalizeNearbySearch(value)
    val compactNeedle = normalizeNearbySearchCompact(value)
    if (needle.isBlank()) return true

    return nearbyTextMatches(
        value = listOf(site.fullAddress, site.address, site.description, site.postalCode, site.city)
            .plus(site.allAddresses)
            .filterNotNull()
            .joinToString(" "),
        needle = needle,
        compactNeedle = compactNeedle
    )
}

private fun shouldUseNearbyNominatimCitySearch(
    spec: NearbySearchSpec,
    value: String,
    isPostalCode: Boolean
): Boolean {
    if (isPostalCode || value.isBlank()) return false
    return when (spec.field) {
        NearbySearchField.City -> true
        NearbySearchField.All -> value.any(Char::isLetter) && value.none(Char::isDigit)
        else -> false
    }
}

private fun isNearbyPointInPolygons(
    lat: Double,
    lon: Double,
    polygons: List<List<NominatimGeoPoint>>
): Boolean {
    var isInside = false
    for (polygon in polygons) {
        var j = polygon.size - 1
        var polyInside = false
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if (((pi.longitude > lon) != (pj.longitude > lon)) &&
                (lat < (pj.latitude - pi.latitude) * (lon - pi.longitude) / (pj.longitude - pi.longitude) + pi.latitude)
            ) {
                polyInside = !polyInside
            }
            j = i
        }
        if (polyInside) isInside = true
    }
    return isInside
}

private fun nearbyDatabaseSearchVariants(value: String): List<String> {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return emptyList()

    val separatorFriendly = trimmed
        .replace(Regex("""[-_'.’]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val hyphenFriendly = separatorFriendly.replace(" ", "-")
    val accentFriendly = trimmed
        .replace("pylone", "pylône", ignoreCase = true)
        .replace("chateau", "château", ignoreCase = true)
        .replace("batiment", "bâtiment", ignoreCase = true)
        .replace("eglise", "église", ignoreCase = true)
        .replace("aerien", "aérien", ignoreCase = true)

    return listOf(trimmed, separatorFriendly, hyphenFriendly, accentFriendly)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun nearbyTextMatches(value: String, needle: String, compactNeedle: String): Boolean {
    val normalized = normalizeNearbySearch(value)
    if (normalized.contains(needle)) return true
    return compactNeedle.isNotBlank() && normalizeNearbySearchCompact(value).contains(compactNeedle)
}

private fun normalizeNearbySearch(value: String): String {
    val withoutAccents = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutAccents
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeNearbySearchCompact(value: String): String {
    val withoutAccents = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutAccents.replace(Regex("[^a-z0-9]"), "")
}

private fun extractNearbyPostalCode(fullAddress: String): String? {
    return Regex("""\b\d{5}\b""").find(fullAddress)?.value
}

private fun extractNearbyCity(fullAddress: String): String? {
    val candidate = fullAddress.split(",")
        .map { it.trim() }
        .lastOrNull { it.any(Char::isLetter) }
        ?: return null
    return candidate
        .replace(Regex("""\b\d{5}\b"""), "")
        .trim(' ', ',', '-', ';')
        .takeIf { it.isNotBlank() }
}

private fun extractNearbyTechnologies(values: List<String>): List<String> {
    val text = values.joinToString(" ").uppercase(Locale.ROOT)
    val technologies = mutableListOf<String>()
    listOf("5G", "4G", "3G", "2G").forEach { generation ->
        if (text.contains(generation)) technologies.add(generation)
    }
    if (Regex("""\bNR\b""").containsMatchIn(text) && "5G" !in technologies) technologies.add("5G")
    if (Regex("""\bLTE\b""").containsMatchIn(text) && "4G" !in technologies) technologies.add("4G")
    if (Regex("""\bUMTS\b""").containsMatchIn(text) && "3G" !in technologies) technologies.add("3G")
    if (Regex("""\bGSM\b""").containsMatchIn(text) && "2G" !in technologies) technologies.add("2G")
    return technologies
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        locManager.getProviders(true)
            .mapNotNull { provider -> runCatching { locManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    } catch (e: Exception) {
        AppLogger.w(TAG_NEAR_EMITTERS, "Last known location unavailable", e)
        null
    }
}

private fun hasNearbyLocationPermission(context: Context): Boolean {
    return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
