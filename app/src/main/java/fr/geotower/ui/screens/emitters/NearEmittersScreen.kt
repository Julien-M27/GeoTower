package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.R
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import fr.geotower.data.models.LocalisationEntity
import androidx.compose.runtime.saveable.rememberSaveable

data class UiSite(
    val id: Long,
    val idSupport: String?,
    val distance: Int,
    val address: String,
    val description: String,
    val operators: List<String>,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearEmittersScreen(
    navController: NavController,
    repository: AnfrRepository
) {
    val context = LocalContext.current

    // --- LECTURE RÉACTIVE DU THÈME D'ABORD ---
    val forceOneUi by AppConfig.forceOneUiTheme
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)

    val useOneUi = forceOneUi
    val isOled = isOledMode

    val cardShape = if (useOneUi) RoundedCornerShape(28.dp) else RoundedCornerShape(12.dp)
    val mainBgColor = if (isDark && isOled) Color.Black else MaterialTheme.colorScheme.background
    val cardColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

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
            navController.navigate("home") { popUpTo(0) }
        }
    }

    BackHandler { handleBackNavigation() }

    var userLocation by remember { mutableStateOf<Location?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var sites by remember { mutableStateOf<List<UiSite>>(emptyList()) }
    var filteredSites by remember { mutableStateOf<List<UiSite>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var maxItemsToShow by rememberSaveable { mutableIntStateOf(100) }
    var searchRadiusMultiplier by remember { mutableIntStateOf(1) }
    var isSearchingRemote by remember { mutableStateOf(false) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val showSearchBar by remember { mutableStateOf(prefs.getBoolean("show_search_bar", true)) }
    val showNearbySites by remember { mutableStateOf(prefs.getBoolean("show_nearby_sites", true)) }
    val nearbyOrder by remember { mutableStateOf(prefs.getString("nearby_order", "search,sites")!!.split(",")) }


    // --- NOUVEAU : DÉMARRAGE DU SERVICE DE NOTIFICATION LIVE ---
    val liveNotifsEnabled by AppConfig.enableLiveNotifications

    LaunchedEffect(liveNotifsEnabled) {
        if (liveNotifsEnabled && AppConfig.defaultOperator.value != "Aucun") {
            // Vérifie si on a la permission des notifications sur Android 13+
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {

                val serviceIntent = android.content.Intent(context, fr.geotower.services.LiveTrackingService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
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
            } catch (e: Exception) { e.printStackTrace() }
        }
        onDispose { locationManager.removeUpdates(locationListener) }
    }

    var localAntennas by remember { mutableStateOf<List<LocalisationEntity>>(emptyList()) }

    // --- 1. CHARGEMENT LOCAL INSTANTANÉ ---
    LaunchedEffect(userLocation, searchRadiusMultiplier) {
        val currentLoc = userLocation ?: Location("").apply {
            latitude = 48.8566
            longitude = 2.3522
        }

        if (currentLoc != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                // On demande directement les 100 plus proches à SQLite (recherche mondiale)
                val boxAntennas = repository.getNearest100(currentLoc.latitude, currentLoc.longitude)
                localAntennas = boxAntennas
            }
        }
    }

    // --- 2. TRAITEMENT ET CARTOGRAPHIE POUR L'UI ---
    LaunchedEffect(localAntennas, userLocation) {
        if (localAntennas.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                val groupedSites = localAntennas.groupBy {
                    "${String.format(java.util.Locale.US, "%.4f", it.latitude)}_${String.format(java.util.Locale.US, "%.4f", it.longitude)}"
                }

                val mappedSites = mutableListOf<UiSite>()
                for ((_, list) in groupedSites) {
                    val main = list.first()
                    val dist = if (userLocation != null) {
                        calculateDistance(userLocation!!.latitude, userLocation!!.longitude, main.latitude, main.longitude)
                    } else 0f

                    val ops = list.mapNotNull { it.operateur }
                        .flatMap { it.split(Regex("[/,\\-]")) }
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }
                        .distinct()

                    val technique = repository.getTechniqueDetails(main.idAnfr)
                    val fullAddress = technique?.adresse ?: "Adresse inconnue"
                    val lastCommaIndex = fullAddress.lastIndexOf(",")

                    val titreHaut = if (lastCommaIndex != -1) fullAddress.substring(0, lastCommaIndex).trim() else fullAddress
                    val sousTitreGris = if (lastCommaIndex != -1) fullAddress.substring(lastCommaIndex + 1).trim() else "Site ANFR: ${main.idAnfr}"

                    mappedSites.add(UiSite(
                        id = main.idAnfr.toLongOrNull() ?: 0L,
                        idSupport = null,
                        distance = dist.toInt(),
                        address = titreHaut,
                        description = sousTitreGris,
                        operators = ops,
                        latitude = main.latitude,
                        longitude = main.longitude
                    ))
                }
                val finalSites = mappedSites.sortedBy { it.distance }

                withContext(Dispatchers.Main) {
                    sites = finalSites
                    isLoading = false
                }
            }
        } else {
            delay(500)
            isLoading = false
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
        val localMatches = sites.filter {
            it.address.contains(query, true) ||
                    it.description.contains(query, true) ||
                    it.id.toString().contains(query, true) ||
                    it.operators.any { op -> op.contains(query, true) }
        }

        // On affiche immédiatement ce qu'on a trouvé localement pour éviter un écran vide
        filteredSites = localMatches.take(maxItemsToShow)

        // B. RECHERCHE DISTANTE INTELLIGENTE
        // On vérifie si c'est juste le nom d'un opérateur (auquel cas on ne déclenche pas le GPS)
        val isOperatorSearch = listOf("ORANGE", "BOUYGUES", "SFR", "FREE").any { query.equals(it, ignoreCase = true) }

        // On ne lance le scan distant que si ce n'est pas un opérateur ET qu'il y a au moins 3 caractères
        if (!isOperatorSearch && query.length >= 3) {

            delay(800) // Petit délai pour ne pas spammer la recherche pendant la frappe
            isSearchingRemote = true

            withContext(Dispatchers.IO) {
                try {
                    val globalAntennas = mutableListOf<LocalisationEntity>()
                    var targetLat: Double? = null
                    var targetLon: Double? = null

                    val isNumeric = query.all { it.isDigit() }
                    val isPostalCode = isNumeric && query.length == 5
                    val coordRegex = Regex("""^([-+]?\d{1,2}[.,]\d+)\s*[,;\s]\s*([-+]?\d{1,3}[.,]\d+)$""")
                    val match = coordRegex.find(query)
                    val isGps = match != null

                    // --- 1. RECHERCHE PAR ID (Base de données) ---
                    // ⚠️ CORRECTION ICI : On ne cherche un ID que si ce n'est PAS un code postal,
                    // PAS un GPS, et que la requête fait au moins 5 caractères (les vrais ID sont longs).
                    if (!isPostalCode && !isGps && query.any { it.isDigit() } && query.length >= 5) {
                        val idResults = repository.searchAntennasById(query)
                        globalAntennas.addAll(idResults)
                    }

                    // --- 2. RECHERCHE GÉOGRAPHIQUE (GPS, Ville, Code Postal) ---
                    if (isGps) {
                        targetLat = match!!.groupValues[1].replace(',', '.').toDoubleOrNull()
                        targetLon = match.groupValues[2].replace(',', '.').toDoubleOrNull()
                    } else {
                        // On interroge Google/OSM seulement si c'est un code postal ou si ça contient des lettres (ville/rue)
                        if (isPostalCode || query.any { it.isLetter() }) {
                            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                            val geoQuery = if (isPostalCode) "$query France" else query

                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocationName(geoQuery, 1)
                            if (!addresses.isNullOrEmpty()) {
                                targetLat = addresses[0].latitude
                                targetLon = addresses[0].longitude
                            }
                        }
                    }

                    // Si on a trouvé des coordonnées, on récupère le bloc d'antennes autour
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
                        val groupedGlobal = uniqueGlobalAntennas.groupBy {
                            "${String.format(java.util.Locale.US, "%.4f", it.latitude)}_${String.format(java.util.Locale.US, "%.4f", it.longitude)}"
                        }

                        val mappedGlobal = mutableListOf<UiSite>()
                        for ((_, list) in groupedGlobal) {
                            val main = list.first()

                            // Distance toujours par rapport au GPS de l'utilisateur !
                            val dist = if (userLocation != null) {
                                calculateDistance(userLocation!!.latitude, userLocation!!.longitude, main.latitude, main.longitude)
                            } else 0f

                            val ops = list.mapNotNull { it.operateur }
                                .flatMap { it.split(Regex("[/,\\-]")) }
                                .map { it.trim().uppercase() }
                                .filter { it.isNotEmpty() }
                                .distinct()

                            val technique = repository.getTechniqueDetails(main.idAnfr)
                            val fullAddress = technique?.adresse ?: "Adresse inconnue"

                            val lastCommaIndex = fullAddress.lastIndexOf(",")
                            val titreHaut = if (lastCommaIndex != -1) fullAddress.substring(0, lastCommaIndex).trim() else fullAddress
                            val sousTitreGris = if (lastCommaIndex != -1) fullAddress.substring(lastCommaIndex + 1).trim() else "Site ANFR: ${main.idAnfr}"

                            mappedGlobal.add(UiSite(
                                id = main.idAnfr.toLongOrNull() ?: 0L,
                                idSupport = null,
                                distance = dist.toInt(),
                                address = titreHaut,
                                description = sousTitreGris,
                                operators = ops,
                                latitude = main.latitude,
                                longitude = main.longitude
                            ))
                        }

                        // ✅ On combine les antennes locales filtrées AVEC les antennes lointaines trouvées,
                        // on retire les doublons et on trie tout ça par distance !
                        val combined = (localMatches + mappedGlobal).distinctBy { it.id }.sortedBy { it.distance }

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
                    e.printStackTrace()
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
                    onClick = { safeClick { handleBackNavigation() } },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = AppStrings.nearEmittersTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            nearbyOrder.forEach { block ->
                when (block) {
                    "search" -> {
                        if (showSearchBar) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                OutlinedTextField(
                                    value = searchQuery, onValueChange = { searchQuery = it },
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
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                        } else if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Effacer") }
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
                                    userLocation == null -> {
                                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(AppStrings.searchGps, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    isLoading -> {
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                    }
                                    filteredSites.isEmpty() -> {
                                        Text(AppStrings.noSitesFound, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.secondary)
                                    }
                                    else -> {
                                        LazyColumn(
                                            state = lazyListState,
                                            modifier = Modifier.fillMaxSize().nearEmittersFadingEdge(lazyListState),
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(filteredSites, key = { it.id }) { site ->
                                                EmitterCard(site = site, useOneUi = useOneUi, cardShape = cardShape, cardColor = cardColor) {
                                                    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                                                    prefs.edit()
                                                        .putFloat("clicked_lat", site.latitude.toFloat())
                                                        .putFloat("clicked_lon", site.longitude.toFloat())
                                                        .apply()

                                                    navController.navigate("support_detail/${site.id}")
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
                                                } else if (userLocation != null && searchQuery.isEmpty()) {
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
                                                        Text(AppStrings.get("Afficher plus de sites", "Show more sites", "Mostrar mais"), fontWeight = FontWeight.Bold)
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
                                                        coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                                                    }) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowUp,
                                                            contentDescription = "Haut",
                                                            tint = iconColor
                                                        )
                                                    }

                                                    IconButton(onClick = {
                                                        coroutineScope.launch {
                                                            val lastIndex = lazyListState.layoutInfo.totalItemsCount - 1
                                                            if (lastIndex > 0) lazyListState.animateScrollToItem(lastIndex)
                                                        }
                                                    }) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = "Bas",
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


private fun Modifier.nearEmittersFadingEdge(lazyListState: LazyListState): Modifier {
    if (!AppConfig.isBlurEnabled.value) return this

    val fadeHeight = 80.dp

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val heightPx = fadeHeight.toPx()

            val isFirstItemVisible = lazyListState.firstVisibleItemIndex == 0
            val topAlpha = if (!isFirstItemVisible) 1f
            else (lazyListState.firstVisibleItemScrollOffset.toFloat() / heightPx).coerceIn(0f, 1f)

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 1f - topAlpha),
                        Color.Black
                    ),
                    startY = 0f,
                    endY = heightPx
                ),
                blendMode = BlendMode.DstIn
            )

            val canScrollForward = lazyListState.canScrollForward
            val bottomAlpha = if (canScrollForward) 1f else 0f

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 1f - bottomAlpha)
                    ),
                    startY = size.height - heightPx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
}

@Composable
fun EmitterCard(
    site: UiSite,
    useOneUi: Boolean,
    cardShape: Shape,
    cardColor: Color,
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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

            OperatorGrid(operators = site.operators)

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
fun OperatorGrid(operators: List<String>) {
    val defaultOp = AppConfig.defaultOperator.value.uppercase()
    val baseOrder = listOf("ORANGE", "BOUYGUES", "SFR", "FREE")
    val priorityList = mutableListOf<String>()

    if (defaultOp != "AUCUN" && baseOrder.any { defaultOp.contains(it) }) {
        priorityList.add(baseOrder.first { defaultOp.contains(it) })
    }
    baseOrder.forEach { if (!priorityList.contains(it)) priorityList.add(it) }

    val sortedOperators = priorityList.filter { priorityOp ->
        operators.any { op -> op.contains(priorityOp, ignoreCase = true) }
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
    val iconRes = when {
        opName == null -> null
        opName.contains("ORANGE", true) -> R.drawable.logo_orange
        opName.contains("BOUYGUES", true) -> R.drawable.logo_bouygues
        opName.contains("SFR", true) -> R.drawable.logo_sfr
        opName.contains("FREE", true) -> R.drawable.logo_free
        else -> null
    }

    val isPresent = iconRes != null
    val cornerShape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(cornerShape)
            .background(if (isPresent) Color.Transparent else Color.Gray.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        if (isPresent && iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cornerShape)
            )
        }
    }
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val res = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, res)
    return res[0]
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locManager.getProviders(true).mapNotNull { locManager.getLastKnownLocation(it) }.maxByOrNull { it.time }
}