package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.ui.components.geoTowerLazyListFadingEdge
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.NominatimApi
import fr.geotower.data.api.NominatimGeoPoint
import fr.geotower.ui.components.rememberSafeClick
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import fr.geotower.utils.OperatorColors
import fr.geotower.utils.OperatorLogos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import fr.geotower.data.models.LocalisationEntity
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.Normalizer
import java.util.Locale

private const val TAG_NEAR_EMITTERS = "GeoTower"
private const val NEARBY_RELOAD_DISTANCE_METERS = 100f

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
    Gps
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

    val safeClick = rememberSafeClick()

    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "home")

    BackHandler(enabled = !safeBackNavigation.isLocked) {
        safeBackNavigation.navigateBack()
    }

    var userLocation by remember { mutableStateOf<Location?>(null) }
    var searchCenter by remember { mutableStateOf<Location?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var sites by remember { mutableStateOf<List<UiSite>>(emptyList()) }
    var filteredSites by remember { mutableStateOf<List<UiSite>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var maxItemsToShow by rememberSaveable { mutableIntStateOf(100) }
    var searchRadiusMultiplier by remember { mutableIntStateOf(1) }
    var isSearchingRemote by remember { mutableStateOf(false) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val showSearchBar by remember { mutableStateOf(prefs.getBoolean("show_search_bar", true)) }
    val showSearchSuggestions by remember { mutableStateOf(prefs.getBoolean("show_search_suggestions", true)) }
    val showNearbySites by remember { mutableStateOf(prefs.getBoolean("show_nearby_sites", true)) }
    val nearbyOrder by remember { mutableStateOf(prefs.getString("nearby_order", "search,sites")!!.split(",")) }
    val searchedOperatorKey = remember(searchQuery) {
        val spec = parseNearbySearchQuery(searchQuery)
        if (spec.field == NearbySearchField.Operator) {
            OperatorColors.keyFor(spec.value)
        } else {
            null
        }
    }

    // --- GESTION DU GPS ---
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
            userLocation = getLastKnownLocation(context)
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, locationListener)
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

    LaunchedEffect(searchCenter, searchRadiusMultiplier) {
        val currentLoc = searchCenter ?: return@LaunchedEffect

        isLoading = true

        withContext(Dispatchers.IO) {
            // A. RÉCUPÉRATION DES DONNÉES
            val newAntennas = if (searchRadiusMultiplier > 1) {
                // Si on a cliqué sur "Plus de sites", on élargit la zone de recherche !
                val offset = 0.05 * searchRadiusMultiplier
                repository.getAntennasInBox(
                    latNorth = currentLoc.latitude + offset,
                    lonEast = currentLoc.longitude + offset,
                    latSouth = currentLoc.latitude - offset,
                    lonWest = currentLoc.longitude - offset
                )
            } else {
                // Chargement initial ultra-rapide des 100 plus proches
                repository.getNearest100(currentLoc.latitude, currentLoc.longitude)
            }

            // B. TRAITEMENT ET FORMATAGE
            val finalSites = if (newAntennas.isNotEmpty()) {
                mapAntennasToUiSites(
                    repository = repository,
                    antennas = newAntennas,
                    referenceLocation = currentLoc
                ).sortedBy { it.distance }
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                sites = finalSites
            }

            // C. ON ARRÊTE LE CHARGEMENT (Garanti de s'exécuter à 100%)
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    // ✅ 3. RECHERCHE LOCALE ET GLOBALE (Base de données, Coordonnées, Ville, Adresse, Code Postal)
    LaunchedEffect(sites, searchQuery, maxItemsToShow, searchRadiusMultiplier) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            filteredSites = sites.take(maxItemsToShow)
            isSearchingRemote = false
            return@LaunchedEffect
        }

        // A. RECHERCHE LOCALE (Rapide, filtre immédiat de ce qui est autour de l'utilisateur)
        val searchSpec = parseNearbySearchQuery(query)
        val searchValue = searchSpec.value.trim()
        val localMatches = sites.filter { siteMatchesSearch(it, searchSpec) }

        // On affiche immédiatement ce qu'on a trouvé localement pour éviter un écran vide
        filteredSites = localMatches.take(maxItemsToShow)

        // B. RECHERCHE DISTANTE INTELLIGENTE
        // On vérifie si c'est juste le nom d'un opérateur (auquel cas on ne déclenche pas le GPS)
        val shouldSearchRemote = searchSpec.latitude != null ||
                searchSpec.field != NearbySearchField.All ||
                searchValue.length >= 3

        // On ne lance le scan distant que si ce n'est pas un opérateur ET qu'il y a au moins 3 caractères
        if (shouldSearchRemote) {

            delay(800) // Petit délai pour ne pas spammer la recherche pendant la frappe
            isSearchingRemote = true

            val referenceLocation = searchCenter ?: userLocation
            withContext(Dispatchers.IO) {
                try {
                    val globalAntennas = mutableListOf<LocalisationEntity>()
                    var targetLat: Double? = null
                    var targetLon: Double? = null

                    val isNumeric = searchValue.all { it.isDigit() }
                    val isPostalCode = searchSpec.field == NearbySearchField.PostalCode || (isNumeric && searchValue.length == 5)
                    val isGps = searchSpec.latitude != null && searchSpec.longitude != null
                    val filtersOnWholeAddress = shouldFilterOnWholeAddress(searchSpec, searchValue, isPostalCode)
                    val textSearchLimit = if (filtersOnWholeAddress) 2000 else 200
                    val cityAreaIds = mutableSetOf<String>()
                    var hasCityAreaSearch = false
                    val fieldSearchesCurrentArea = searchSpec.field in setOf(
                        NearbySearchField.Operator,
                        NearbySearchField.Technology,
                        NearbySearchField.SupportType
                    )

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

                    if (searchValue.length >= 2 && !fieldSearchesCurrentArea && !isGps) {
                        nearbyDatabaseSearchVariants(searchValue).forEach { variant ->
                            if (filtersOnWholeAddress) {
                                globalAntennas.addAll(repository.searchAntennasByAddress(variant, limit = 5000))
                            } else {
                                globalAntennas.addAll(repository.searchAntennasByText(variant, limit = textSearchLimit))
                            }
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
                            val boxResults = repository.getAntennasInBox(
                                latNorth = nominatimArea.latNorth,
                                lonEast = nominatimArea.lonEast,
                                latSouth = nominatimArea.latSouth,
                                lonWest = nominatimArea.lonWest
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
                            }
                        }
                    }

                    // Si on a trouvé des coordonnées, on récupère le bloc d'antennes autour
                    if (fieldSearchesCurrentArea && referenceLocation != null) {
                        val offset = 0.05 * searchRadiusMultiplier
                        globalAntennas.addAll(
                            repository.getAntennasInBox(
                                latNorth = referenceLocation.latitude + offset,
                                lonEast = referenceLocation.longitude + offset,
                                latSouth = referenceLocation.latitude - offset,
                                lonWest = referenceLocation.longitude - offset
                            )
                        )
                    }

                    if (targetLat != null && targetLon != null) {
                        val offset = 0.05 * searchRadiusMultiplier
                        val boxResults = repository.getAntennasInBox(
                            latNorth = targetLat + offset,
                            lonEast = targetLon + offset,
                            latSouth = targetLat - offset,
                            lonWest = targetLon - offset
                        )
                        globalAntennas.addAll(boxResults)
                    }

                    // On supprime les doublons éventuels
                    val uniqueGlobalAntennas = globalAntennas.distinctBy { it.idAnfr }

                    // --- FORMATAGE DES RÉSULTATS POUR L'UI ---
                    if (uniqueGlobalAntennas.isNotEmpty()) {
                        val mappedGlobal = mapAntennasToUiSites(
                            repository = repository,
                            antennas = uniqueGlobalAntennas,
                            referenceLocation = referenceLocation
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
                        val combined = (localMatches + filteredGlobal).distinctBy { it.id }.sortedBy { it.distance }

                        withContext(Dispatchers.Main) {
                            filteredSites = combined.take(maxItemsToShow)
                            isSearchingRemote = false
                        }
                    } else {
                        // Rien trouvé à distance, on se contente des résultats locaux
                        withContext(Dispatchers.Main) {
                            filteredSites = localMatches.take(maxItemsToShow)
                            isSearchingRemote = false
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG_NEAR_EMITTERS, "Remote nearby search failed", e)
                    withContext(Dispatchers.Main) {
                        filteredSites = localMatches.take(maxItemsToShow)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mainBgColor)
                    .padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { safeBackNavigation.navigateBack() },
                    enabled = !safeBackNavigation.isLocked,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.back, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = AppStrings.nearEmittersTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(
                    onClick = { safeClick { navController.navigate("settings?section=nearby") } },
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
                                    placeholder = { Text(AppStrings.searchCityOrId) },
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
                                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, AppStrings.clear) }
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
                                    Text(
                                        text = AppStrings.sitesFound(filteredSites.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(start = 32.dp, top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    "sites" -> {
                        if (showNearbySites) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when {
                                    searchCenter == null -> {
                                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                            LoadingIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(AppStrings.searchGps, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    isLoading -> {
                                        LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                                    }
                                    filteredSites.isEmpty() -> {
                                        Text(AppStrings.noSitesFound, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.secondary)
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
                                            items(filteredSites, key = { it.id }) { site ->
                                                EmitterCard(
                                                    site = site,
                                                    useOneUi = useOneUi,
                                                    cardShape = cardShape,
                                                    cardColor = cardColor,
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
                                                        navController.navigate("support_detail/${site.id}$highlightedOperatorParam")
                                                    }
                                                }
                                            }

                                            item {
                                                if (sites.size > maxItemsToShow) {
                                                    OutlinedButton(
                                                        onClick = { safeClick { maxItemsToShow += 100 } },
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                                    ) {
                                                        Text(AppStrings.loadMoreSites, fontWeight = FontWeight.Bold)
                                                    }
                                                } else if (searchCenter != null && searchQuery.isEmpty()) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            safeClick {
                                                                searchRadiusMultiplier++
                                                                maxItemsToShow += 100
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                                                    ) {
                                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(AppStrings.showMoreSites, fontWeight = FontWeight.Bold)
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
                                                            contentDescription = AppStrings.top,
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
                                                            contentDescription = AppStrings.bottom,
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
}

private const val NEAR_EMITTERS_SCROLL_MIN_STEP_ITEMS = 12
private const val NEAR_EMITTERS_SCROLL_MAX_STEP_ITEMS = 32

private suspend fun LazyListState.animateScrollToItemSmoothly(targetIndex: Int) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return

    val boundedTargetIndex = targetIndex.coerceIn(0, lastIndex)
    val scrollingDown = boundedTargetIndex > firstVisibleItemIndex

    if (scrollingDown) {
        while (canScrollForward && firstVisibleItemIndex < boundedTargetIndex) {
            val startIndex = firstVisibleItemIndex
            val startOffset = firstVisibleItemScrollOffset
            val step = nearEmittersScrollStep(
                remainingItems = boundedTargetIndex - firstVisibleItemIndex
            )
            val nextIndex = minOf(
                firstVisibleItemIndex + step,
                boundedTargetIndex
            )

            animateScrollToItem(nextIndex)

            if (firstVisibleItemIndex == startIndex && firstVisibleItemScrollOffset == startOffset) break
        }
    } else {
        while (
            canScrollBackward &&
            (firstVisibleItemIndex > boundedTargetIndex || firstVisibleItemScrollOffset > 0)
        ) {
            val startIndex = firstVisibleItemIndex
            val startOffset = firstVisibleItemScrollOffset
            val step = nearEmittersScrollStep(
                remainingItems = firstVisibleItemIndex - boundedTargetIndex
            )
            val nextIndex = maxOf(
                firstVisibleItemIndex - step,
                boundedTargetIndex
            )

            animateScrollToItem(nextIndex)

            if (firstVisibleItemIndex == startIndex && firstVisibleItemScrollOffset == startOffset) break
        }
    }

    if (!scrollingDown || canScrollForward) {
        animateScrollToItem(boundedTargetIndex)
    }
}

private fun nearEmittersScrollStep(remainingItems: Int): Int {
    return (remainingItems / 4).coerceIn(
        NEAR_EMITTERS_SCROLL_MIN_STEP_ITEMS,
        NEAR_EMITTERS_SCROLL_MAX_STEP_ITEMS
    )
}


@Composable
private fun NearbyQuickSearchSuggestions(
    useOneUi: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    var showSearchHelp by remember { mutableStateOf(false) }
    val suggestions = listOf(
        NearbySearchSuggestion(AppStrings.nearbySearchSuggestionCity, "ville:"),
        NearbySearchSuggestion("Orange", "op:Orange"),
        NearbySearchSuggestion("SFR", "op:SFR"),
        NearbySearchSuggestion("Bouygues", "op:Bouygues"),
        NearbySearchSuggestion("Free", "op:Free"),
        NearbySearchSuggestion("5G", "tech:5G"),
        NearbySearchSuggestion("4G", "tech:4G"),
        NearbySearchSuggestion(AppStrings.nearbySearchSuggestionPylon, "type:pylone"),
        NearbySearchSuggestion(AppStrings.nearbySearchSuggestionRoof, "type:toit"),
        NearbySearchSuggestion("ID ANFR", "anfr:"),
        NearbySearchSuggestion("Support", "support:"),
        NearbySearchSuggestion(AppStrings.nearbySearchSuggestionPostalCode, "cp:"),
        NearbySearchSuggestion("GPS", "gps:")
    )

    if (showSearchHelp) {
        NearbySearchHelpDialog(onDismiss = { showSearchHelp = false })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { showSearchHelp = true },
            shape = if (useOneUi) RoundedCornerShape(50) else RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = AppStrings.nearbySearchHelpContentDescription,
                modifier = Modifier.size(16.dp)
            )
        }

        suggestions.forEach { suggestion ->
            OutlinedButton(
                onClick = { onSuggestionClick(suggestion.query) },
                shape = if (useOneUi) RoundedCornerShape(50) else RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = suggestion.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NearbySearchHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null
            )
        },
        title = {
            Text(
                text = AppStrings.nearbySearchHelpTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NearbySearchHelpLine("ville:Lyon", AppStrings.nearbySearchHelpCityDesc)
                NearbySearchHelpLine("adresse:rue de Paris", AppStrings.nearbySearchHelpAddressDesc)
                NearbySearchHelpLine("cp:75001", AppStrings.nearbySearchHelpPostalDesc)
                NearbySearchHelpLine("gps:48.8566,2.3522", AppStrings.nearbySearchHelpGpsDesc)
                NearbySearchHelpLine("anfr:123456", AppStrings.nearbySearchHelpAnfrDesc)
                NearbySearchHelpLine("support:123456", AppStrings.nearbySearchHelpSupportDesc)
                NearbySearchHelpLine("op:Orange", AppStrings.nearbySearchHelpOperatorDesc)
                NearbySearchHelpLine("tech:5G", AppStrings.nearbySearchHelpTechDesc)
                NearbySearchHelpLine("type:pylone", AppStrings.nearbySearchHelpTypeDesc)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.nearbySearchHelpOk)
            }
        }
    )
}

@Composable
private fun NearbySearchHelpLine(
    code: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
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
    priorityOperatorKey: String? = null,
    onClick: () -> Unit
) {
    val isMi = AppConfig.distanceUnit.intValue == 1

    val distanceStr = if (isMi) {
        val distMiles = site.distance / 1609.34f
        if (distMiles < 0.1f) {
            "${(site.distance * 3.28084f).toInt()} ft"
        } else {
            String.format(java.util.Locale.US, "%.2f mi", distMiles)
        }
    } else {
        if (site.distance >= 1000) String.format(java.util.Locale.US, "%.2f km", site.distance / 1000f) else "${site.distance} m"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
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
                    text = distanceStr,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
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

private suspend fun mapAntennasToUiSites(
    repository: AnfrRepository,
    antennas: List<LocalisationEntity>,
    referenceLocation: Location?
): List<UiSite> {
    val groupedSites = antennas.groupBy {
        "${String.format(Locale.US, "%.4f", it.latitude)}_${String.format(Locale.US, "%.4f", it.longitude)}"
    }
    val idAnfrs = antennas.map { it.idAnfr }.filter { it.isNotBlank() }.distinct()
    val techniquesById = repository.getTechniqueSummariesByIds(idAnfrs)
    val physiquesById = repository.getPhysiqueSummariesByIds(idAnfrs)

    return groupedSites.values.map { list ->
        val main = list.first()
        val dist = referenceLocation?.let {
            calculateDistance(it.latitude, it.longitude, main.latitude, main.longitude)
        } ?: 0f

        val operators = list.mapNotNull { it.operateur }
            .flatMap { OperatorColors.keysFor(it) }
            .distinct()

        val techniques = list.mapNotNull { techniquesById[it.idAnfr] }
        val physiques = list.flatMap { physiquesById[it.idAnfr].orEmpty() }
        val allAddresses = techniques.mapNotNull { it.adresse?.takeIf(String::isNotBlank) }.distinct()
        val fullAddress = allAddresses.firstOrNull()
            ?: "Adresse inconnue"
        val addressParts = splitNearbyAddress(fullAddress, main.idAnfr)
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
            technicalText
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
            searchText = searchText
        )
    }
}

private data class NearbyAddressParts(
    val title: String,
    val subtitle: String
)

private fun splitNearbyAddress(fullAddress: String, idAnfr: String): NearbyAddressParts {
    val lastCommaIndex = fullAddress.lastIndexOf(",")
    val title = if (lastCommaIndex != -1) fullAddress.substring(0, lastCommaIndex).trim() else fullAddress
    val subtitle = if (lastCommaIndex != -1) {
        fullAddress.substring(lastCommaIndex + 1).trim()
    } else {
        "Site ANFR: $idAnfr"
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
        normalized in setOf("2g", "3g", "4g", "5g", "gsm", "umts", "lte", "nr") -> NearbySearchField.Technology
        else -> NearbySearchField.All
    }
}

private fun siteMatchesSearch(site: UiSite, spec: NearbySearchSpec): Boolean {
    val needle = normalizeNearbySearch(spec.value)
    val compactNeedle = normalizeNearbySearchCompact(spec.value)
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
    return locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time }
}
