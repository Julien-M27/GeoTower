package fr.geotower.ui.screens.emitters

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.api.ElevationProfileApi
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
import fr.geotower.ui.components.geoTowerFadingEdge
import fr.geotower.ui.navigation.rememberSafeBackNavigation
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger
import fr.geotower.utils.AppStrings
import fr.geotower.utils.isNetworkAvailable
import fr.geotower.utils.rememberNetworkAvailableState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG_ELEVATION_PROFILE = "GeoTowerLocation"

@Composable
fun ElevationProfileScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: String,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeMode by AppConfig.themeMode
    val isOledMode by AppConfig.isOledMode
    val useOneUi = AppConfig.useOneUiDesign
    val isSystemDark = isSystemInDarkTheme()
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemDark)
    val mainBgColor = if (isDark && isOledMode) Color.Black else MaterialTheme.colorScheme.background
    val cardBgColor = if (useOneUi) {
        if (isDark) Color(0xFF212121) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val blockShape = if (useOneUi) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp)

    var site by remember { mutableStateOf<LocalisationEntity?>(null) }
    var physique by remember { mutableStateOf<PhysiqueEntity?>(null) }
    var technique by remember { mutableStateOf<TechniqueEntity?>(null) }
    var isSiteLoading by remember { mutableStateOf(true) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var profile by remember { mutableStateOf<ElevationProfileResult?>(null) }
    var isProfileLoading by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var loadedProfileKey by remember { mutableStateOf<String?>(null) }
    var lastProfileRecalculationAtMillis by remember(antennaId) { mutableStateOf(0L) }
    var selectedFrequencyMHz by remember { mutableStateOf<Int?>(null) }
    var pendingProfilePoint by remember(antennaId) { mutableStateOf(loadPendingElevationProfilePoint(context, antennaId)) }
    var profileCalculationInfo by remember { mutableStateOf<ProfileCalculationInfo?>(null) }
    var savedProfilePrompt by remember { mutableStateOf<SavedElevationProfile?>(null) }
    var hasAnsweredSavedProfilePrompt by remember(antennaId) { mutableStateOf(false) }
    var isUsingSavedProfile by remember(antennaId) { mutableStateOf(false) }
    val safeBackNavigation = rememberSafeBackNavigation(navController, fallbackRoute = "site_detail/$antennaId")

    fun handleBackNavigation() {
        if (isSplitScreen) {
            onCloseSplitScreen()
        } else {
            safeBackNavigation.navigateBack()
        }
    }

    BackHandler(enabled = isSplitScreen || !safeBackNavigation.isLocked) {
        handleBackNavigation()
    }

    val hasLocationPermission = remember {
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val isOnline = rememberNetworkAvailableState(context)

    LaunchedEffect(antennaId) {
        isSiteLoading = true
        val loaded = withContext(Dispatchers.IO) {
            loadProfileSite(context, repository, antennaId)
        }
        site = loaded.site
        physique = loaded.physique
        technique = loaded.technique
        isSiteLoading = false
    }

    val rawProfileFrequencies = remember(site, technique) {
        technique?.detailsFrequences?.takeIf { it.isNotBlank() } ?: site?.frequences
    }
    val availableFrequencies = remember(rawProfileFrequencies) {
        extractElevationProfileFrequencies(rawProfileFrequencies)
    }
    val antennaHeightsByFrequency = remember(rawProfileFrequencies) {
        extractElevationProfileAntennaHeightsByFrequency(rawProfileFrequencies)
    }

    LaunchedEffect(availableFrequencies) {
        if (selectedFrequencyMHz == null || selectedFrequencyMHz !in availableFrequencies) {
            selectedFrequencyMHz = availableFrequencies.firstOrNull()
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

        if (hasLocationPermission) {
            userLocation = getElevationLastKnownLocation(context)
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, ELEVATION_PROFILE_RECALC_INTERVAL_MS, 2f, locationListener)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, ELEVATION_PROFILE_RECALC_INTERVAL_MS, 2f, locationListener)
            } catch (e: Exception) {
                AppLogger.w(TAG_ELEVATION_PROFILE, "Elevation profile location updates could not start", e)
            }
        }

        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    LaunchedEffect(site, userLocation, isOnline) {
        val currentSite = site ?: return@LaunchedEffect

        if (!isOnline) {
            isProfileLoading = false

            if (isUsingSavedProfile && profile != null) {
                return@LaunchedEffect
            }

            val savedProfile = loadSavedElevationProfile(context, antennaId)
            if (savedProfile != null && !hasAnsweredSavedProfilePrompt && savedProfilePrompt == null) {
                profile = null
                profileCalculationInfo = null
                profileError = null
                savedProfilePrompt = savedProfile
                return@LaunchedEffect
            }

            profile = null
            profileCalculationInfo = null
            profileError = if (pendingProfilePoint != null) PROFILE_ERROR_OFFLINE_PENDING else PROFILE_ERROR_OFFLINE
            return@LaunchedEffect
        }

        hasAnsweredSavedProfilePrompt = false
        savedProfilePrompt = null
        isUsingSavedProfile = false

        val pendingPoint = pendingProfilePoint?.takeIf { it.antennaId == antennaId }
        val fromLatitude = pendingPoint?.fromLatitude ?: userLocation?.latitude ?: return@LaunchedEffect
        val fromLongitude = pendingPoint?.fromLongitude ?: userLocation?.longitude ?: return@LaunchedEffect
        val toLatitude = pendingPoint?.toLatitude ?: currentSite.latitude
        val toLongitude = pendingPoint?.toLongitude ?: currentSite.longitude

        val key = "${currentSite.idAnfr}:${(fromLatitude * 100000).roundToInt()}:${(fromLongitude * 100000).roundToInt()}"
        if (loadedProfileKey == key) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (
            pendingPoint == null &&
            lastProfileRecalculationAtMillis > 0L &&
            now - lastProfileRecalculationAtMillis < ELEVATION_PROFILE_RECALC_INTERVAL_MS
        ) {
            return@LaunchedEffect
        }

        loadedProfileKey = key
        lastProfileRecalculationAtMillis = now
        isProfileLoading = true
        profileError = null
        profile = null
        profileCalculationInfo = null

        val result = withContext(Dispatchers.IO) {
            runCatching {
                fetchIgnElevationProfile(
                    fromLatitude = fromLatitude,
                    fromLongitude = fromLongitude,
                    toLatitude = toLatitude,
                    toLongitude = toLongitude
                )
            }
        }

        result.onSuccess {
            profile = it
            val calculationInfo = ProfileCalculationInfo(
                calculatedAtMillis = System.currentTimeMillis(),
                fromLatitude = fromLatitude,
                fromLongitude = fromLongitude
            )
            profileCalculationInfo = calculationInfo
            saveElevationProfile(context, antennaId, it, calculationInfo)
            if (pendingPoint != null) {
                clearPendingElevationProfilePoint(context, antennaId)
                pendingProfilePoint = null
            }
        }.onFailure {
            profileError = if (isNetworkAvailable(context)) it.message ?: "" else PROFILE_ERROR_OFFLINE
        }
        isProfileLoading = false
    }

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
                    onClick = { handleBackNavigation() },
                    enabled = isSplitScreen || !safeBackNavigation.isLocked,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppStrings.back, tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = AppStrings.elevationProfileTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        val contentModifier = Modifier
            .padding(top = padding.calculateTopPadding())
            .fillMaxSize()
            .background(mainBgColor)
            .navigationBarsPadding()
            .padding(16.dp)

        val isOfflineProfileError = profileError == PROFILE_ERROR_OFFLINE
        val isPendingProfileError = profileError == PROFILE_ERROR_OFFLINE_PENDING
        val canSaveProfileForLater = isOfflineProfileError && hasLocationPermission && userLocation != null && site != null

        when {
            isSiteLoading || isProfileLoading -> {
                ProfileLoadingCard(
                    message = AppStrings.elevationProfileLoading,
                    detail = AppStrings.elevationProfileCalculationInProgress,
                    bgColor = cardBgColor,
                    shape = blockShape,
                    modifier = contentModifier
                )
            }
            site == null -> {
                ProfileMessageCard(
                    message = AppStrings.elevationProfileNoSite,
                    bgColor = cardBgColor,
                    shape = blockShape,
                    modifier = contentModifier,
                    icon = Icons.Default.Terrain
                )
            }
            isOfflineProfileError || isPendingProfileError -> {
                ProfileMessageCard(
                    message = if (isPendingProfileError) AppStrings.elevationProfilePendingSavedTitle else AppStrings.elevationProfileOfflineTitle,
                    bgColor = cardBgColor,
                    shape = blockShape,
                    modifier = contentModifier,
                    detail = when {
                        isPendingProfileError -> AppStrings.elevationProfilePendingSavedDetail
                        canSaveProfileForLater -> AppStrings.elevationProfileOfflineSaveDetail
                        else -> AppStrings.elevationProfileOfflineDetail
                    },
                    icon = Icons.Default.CloudOff,
                    actionLabel = if (canSaveProfileForLater) AppStrings.elevationProfileSaveForLater else null,
                    onActionClick = if (canSaveProfileForLater) {
                        {
                            val currentLocation = userLocation
                            val currentSite = site
                            if (currentLocation != null && currentSite != null) {
                                pendingProfilePoint = savePendingElevationProfilePoint(
                                    context = context,
                                    antennaId = antennaId,
                                    fromLocation = currentLocation,
                                    site = currentSite
                                )
                                profileError = PROFILE_ERROR_OFFLINE_PENDING
                            }
                        }
                    } else null
                )
            }
            !hasLocationPermission || userLocation == null -> {
                ProfileMessageCard(
                    message = AppStrings.elevationProfileNoLocation,
                    bgColor = cardBgColor,
                    shape = blockShape,
                    modifier = contentModifier,
                    icon = Icons.Default.Terrain
                )
            }
            profileError != null -> {
                ProfileMessageCard(
                    message = AppStrings.elevationProfileError,
                    bgColor = cardBgColor,
                    shape = blockShape,
                    modifier = contentModifier,
                    icon = Icons.Default.Terrain
                )
            }
            profile != null -> {
                Column(
                    modifier = contentModifier
                        .geoTowerFadingEdge(scrollState)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val frequency = selectedFrequencyMHz ?: DEFAULT_ELEVATION_PROFILE_FREQUENCY_MHZ
                    val selectedAntennaHeight = antennaHeightsByFrequency[frequency]
                    val supportHeight = selectedAntennaHeight ?: physique?.hauteur ?: 0.0
                    val obstruction = remember(profile, supportHeight) {
                        calculateLineObstruction(profile!!, supportHeight)
                    }
                    val fresnelObstruction = remember(profile, supportHeight, frequency) {
                        calculateFresnelObstruction(profile!!, supportHeight, frequency)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = blockShape,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Terrain, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "${AppStrings.elevationProfileDistance} ${formatElevationProfileDistance(profile!!.distanceMeters)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            FrequencySelector(
                                frequencies = availableFrequencies,
                                selectedFrequencyMHz = frequency,
                                onFrequencySelected = { selectedFrequencyMHz = it }
                            )
                            ElevationProfileChart(
                                profile = profile!!,
                                supportHeightMeters = supportHeight,
                                frequencyMHz = frequency,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            )
                        }
                    }

                    ProfileStatsCard(
                        profile = profile!!,
                        calculationInfo = profileCalculationInfo,
                        supportHeight = selectedAntennaHeight ?: physique?.hauteur,
                        obstructionMeters = obstruction,
                        fresnelObstructionMeters = fresnelObstruction,
                        frequencyMHz = frequency,
                        cardBgColor = cardBgColor,
                        blockShape = blockShape
                    )
                }
            }
            else -> {
                ProfileMessageCard(
                    message = AppStrings.elevationProfileError,
                    bgColor = cardBgColor,
                    shape = blockShape,
                    modifier = contentModifier,
                    icon = Icons.Default.Terrain
                )
            }
        }
    }

    savedProfilePrompt?.let { savedProfile ->
        AlertDialog(
            onDismissRequest = {
                hasAnsweredSavedProfilePrompt = true
                savedProfilePrompt = null
                profile = null
                profileCalculationInfo = null
                profileError = if (pendingProfilePoint != null) PROFILE_ERROR_OFFLINE_PENDING else PROFILE_ERROR_OFFLINE
            },
            title = { Text(AppStrings.elevationProfileSavedDialogTitle) },
            text = { Text(AppStrings.elevationProfileSavedDialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hasAnsweredSavedProfilePrompt = true
                        isUsingSavedProfile = true
                        profile = savedProfile.profile
                        profileCalculationInfo = savedProfile.calculationInfo
                        profileError = null
                        savedProfilePrompt = null
                    }
                ) {
                    Text(AppStrings.elevationProfileSavedDialogShow)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        hasAnsweredSavedProfilePrompt = true
                        isUsingSavedProfile = false
                        profile = null
                        profileCalculationInfo = null
                        profileError = if (pendingProfilePoint != null) PROFILE_ERROR_OFFLINE_PENDING else PROFILE_ERROR_OFFLINE
                        savedProfilePrompt = null
                    }
                ) {
                    Text(AppStrings.elevationProfileSavedDialogHide)
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ProfileLoadingCard(
    message: String,
    detail: String,
    bgColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingIndicator(modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileMessageCard(
    message: String,
    bgColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier.fillMaxWidth(),
    detail: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onActionClick != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsCard(
    profile: ElevationProfileResult,
    calculationInfo: ProfileCalculationInfo?,
    supportHeight: Double?,
    obstructionMeters: Double,
    fresnelObstructionMeters: Double,
    frequencyMHz: Int,
    cardBgColor: Color,
    blockShape: RoundedCornerShape
) {
    val context = LocalContext.current
    val gpsCopyLabel = AppStrings.gpsCoordsCopy
    val coordsCopied = AppStrings.coordsCopied

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = blockShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val startHeightMeters = profile.points.first().elevation + USER_EYE_HEIGHT_METERS
            val arrivalHeightMeters = supportHeight?.let { profile.points.last().elevation + it }
            ProfileInfoRow(
                label = AppStrings.elevationProfileSupportHeight,
                value = supportHeight?.let { "${it.roundToInt()} m" } ?: "--",
                detail = AppStrings.elevationProfileSupportHeightDetail
            )
            ProfileInfoRow(
                label = AppStrings.elevationProfileStartAltitude,
                value = "${startHeightMeters.roundToInt()} m",
                detail = AppStrings.elevationProfileStartAltitudeDetail
            )
            ProfileInfoRow(
                label = AppStrings.elevationProfileSiteAltitude,
                value = arrivalHeightMeters?.let { "${it.roundToInt()} m" } ?: "--",
                detail = AppStrings.elevationProfileSiteAltitudeDetail
            )
            ProfileInfoRow(AppStrings.elevationProfileFrequency, "$frequencyMHz MHz")
            calculationInfo?.let { info ->
                val gpsValue = formatProfileGps(info.fromLatitude, info.fromLongitude)
                ProfileInfoRow(AppStrings.elevationProfileCalculatedAt, formatProfileCalculationTime(info.calculatedAtMillis))
                ProfileInfoColumn(
                    label = AppStrings.elevationProfileUsedGps,
                    value = gpsValue,
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(gpsCopyLabel, gpsValue))
                        Toast.makeText(context, coordsCopied, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            val lineStatus = if (obstructionMeters <= 0.0) {
                AppStrings.elevationProfileLineClear
            } else {
                "${AppStrings.elevationProfileLineBlocked} (+${String.format(Locale.US, "%.1f", obstructionMeters)} m)"
            }
            ProfileInfoColumn(AppStrings.elevationProfileDirectLineLabel, lineStatus)

            val fresnelStatus = if (fresnelObstructionMeters <= 0.0) {
                AppStrings.elevationProfileFresnelClear
            } else {
                "${AppStrings.elevationProfileFresnelBlocked} (+${String.format(Locale.US, "%.1f", fresnelObstructionMeters)} m)"
            }
            ProfileInfoColumn(AppStrings.elevationProfileFresnelLabel, fresnelStatus)
            Text(
                text = AppStrings.elevationProfileIgnSource,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = AppStrings.elevationProfileFresnelExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String, detail: String? = null) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        }
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ProfileInfoColumn(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = AppStrings.copy,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FrequencySelector(
    frequencies: List<Int>,
    selectedFrequencyMHz: Int,
    onFrequencySelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        frequencies.forEach { frequency ->
            FilterChip(
                selected = frequency == selectedFrequencyMHz,
                onClick = { onFrequencySelected(frequency) },
                label = { Text(formatFrequencyLabel(frequency)) }
            )
        }
    }
}

@Composable
private fun ElevationProfileChart(
    profile: ElevationProfileResult,
    supportHeightMeters: Double,
    frequencyMHz: Int,
    modifier: Modifier = Modifier
) {
    val terrainColor = MaterialTheme.colorScheme.primary
    val terrainFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val directLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val fresnelFill = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        if (profile.points.size < 2) return@Canvas

        val left = 46.dp.toPx()
        val top = 18.dp.toPx()
        val right = 12.dp.toPx()
        val bottom = 30.dp.toPx()
        val chartWidth = size.width - left - right
        val chartHeight = size.height - top - bottom
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val startLineHeight = profile.points.first().elevation + USER_EYE_HEIGHT_METERS
        val endLineHeight = profile.points.last().elevation + supportHeightMeters
        val totalDistance = profile.distanceMeters.coerceAtLeast(1f)
        val terrainMin = profile.points.minOf { it.elevation }
        val terrainMax = profile.points.maxOf { it.elevation }
        val fresnelBounds = profile.points.map { point ->
            val lineHeight = lineHeightAt(
                distanceMeters = point.distanceMeters.toDouble(),
                totalDistanceMeters = totalDistance.toDouble(),
                startHeightMeters = startLineHeight,
                endHeightMeters = endLineHeight
            )
            val clearance = fresnelClearanceMeters(
                distanceMeters = point.distanceMeters.toDouble(),
                totalDistanceMeters = totalDistance.toDouble(),
                frequencyMHz = frequencyMHz
            )
            lineHeight - clearance to lineHeight + clearance
        }
        val rawMin = minOf(terrainMin, startLineHeight, endLineHeight, fresnelBounds.minOf { it.first })
        val rawMax = maxOf(terrainMax, startLineHeight, endLineHeight, fresnelBounds.maxOf { it.second })
        val rawRange = max(rawMax - rawMin, 10.0)
        val yMin = floor((rawMin - rawRange * 0.12) / 5.0) * 5.0
        val yMax = floor((rawMax + rawRange * 0.18) / 5.0 + 1.0) * 5.0
        val yRange = max(yMax - yMin, 1.0)

        fun x(distanceMeters: Float): Float = left + (distanceMeters / totalDistance) * chartWidth
        fun y(elevation: Double): Float = top + ((yMax - elevation) / yRange).toFloat() * chartHeight

        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 11.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        repeat(5) { index ->
            val fraction = index / 4f
            val gridY = top + chartHeight * fraction
            drawLine(gridColor, Offset(left, gridY), Offset(left + chartWidth, gridY), strokeWidth = 1.dp.toPx())
            val labelValue = yMax - yRange * fraction
            drawContext.canvas.nativeCanvas.drawText("${labelValue.roundToInt()}", left - 6.dp.toPx(), gridY + 4.dp.toPx(), textPaint)
        }

        repeat(5) { index ->
            val fraction = index / 4f
            val gridX = left + chartWidth * fraction
            drawLine(gridColor, Offset(gridX, top), Offset(gridX, top + chartHeight), strokeWidth = 1.dp.toPx())
        }

        val fresnelPath = Path().apply {
            profile.points.forEachIndexed { index, point ->
                val lineHeight = lineHeightAt(
                    distanceMeters = point.distanceMeters.toDouble(),
                    totalDistanceMeters = totalDistance.toDouble(),
                    startHeightMeters = startLineHeight,
                    endHeightMeters = endLineHeight
                )
                val clearance = fresnelClearanceMeters(
                    distanceMeters = point.distanceMeters.toDouble(),
                    totalDistanceMeters = totalDistance.toDouble(),
                    frequencyMHz = frequencyMHz
                )
                val px = x(point.distanceMeters)
                val py = y(lineHeight + clearance)
                if (index == 0) moveTo(px, py) else lineTo(px, py)
            }
            profile.points.asReversed().forEach { point ->
                val lineHeight = lineHeightAt(
                    distanceMeters = point.distanceMeters.toDouble(),
                    totalDistanceMeters = totalDistance.toDouble(),
                    startHeightMeters = startLineHeight,
                    endHeightMeters = endLineHeight
                )
                val clearance = fresnelClearanceMeters(
                    distanceMeters = point.distanceMeters.toDouble(),
                    totalDistanceMeters = totalDistance.toDouble(),
                    frequencyMHz = frequencyMHz
                )
                lineTo(x(point.distanceMeters), y(lineHeight - clearance))
            }
            close()
        }
        drawPath(fresnelPath, color = fresnelFill)

        val fillPath = Path().apply {
            moveTo(x(0f), y(yMin))
            profile.points.forEachIndexed { index, point ->
                val px = x(point.distanceMeters)
                val py = y(point.elevation)
                if (index == 0) lineTo(px, py) else lineTo(px, py)
            }
            lineTo(x(totalDistance), y(yMin))
            close()
        }
        drawPath(fillPath, color = terrainFill)

        val terrainPath = Path().apply {
            profile.points.forEachIndexed { index, point ->
                val px = x(point.distanceMeters)
                val py = y(point.elevation)
                if (index == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(
            terrainPath,
            color = terrainColor,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )

        drawLine(
            color = directLineColor,
            start = Offset(x(0f), y(startLineHeight)),
            end = Offset(x(totalDistance), y(endLineHeight)),
            strokeWidth = 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
        )

        drawCircle(terrainColor, radius = 4.5.dp.toPx(), center = Offset(x(0f), y(startLineHeight)))
        drawCircle(terrainColor, radius = 4.5.dp.toPx(), center = Offset(x(totalDistance), y(endLineHeight)))

        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("0", left, size.height - 8.dp.toPx(), textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(formatElevationProfileDistance(totalDistance), left + chartWidth, size.height - 8.dp.toPx(), textPaint)
    }
}

private suspend fun loadProfileSite(
    context: Context,
    repository: AnfrRepository,
    antennaId: String
): ProfileSiteData {
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
    val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()
    val antennas = repository.getAntennasByExactId(antennaId)
    val selected = when {
        antennas.isEmpty() -> null
        savedLat != 0.0 && savedLon != 0.0 -> antennas.minByOrNull {
            abs(it.latitude - savedLat) + abs(it.longitude - savedLon)
        }
        else -> antennas.first()
    }
    val physique = selected?.let { repository.getPhysiqueByAnfr(it.idAnfr).firstOrNull() }
    val technique = selected?.let { repository.getTechniqueByAnfr(it.idAnfr).firstOrNull() }
    return ProfileSiteData(site = selected, physique = physique, technique = technique)
}

private fun fetchIgnElevationProfile(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): ElevationProfileResult {
    val profile = ElevationProfileApi.getProfile(
        fromLatitude = fromLatitude,
        fromLongitude = fromLongitude,
        toLatitude = toLatitude,
        toLongitude = toLongitude
    )
    return ElevationProfileResult(
        points = profile.points.map { point ->
            ElevationProfilePoint(
                latitude = point.latitude,
                longitude = point.longitude,
                elevation = point.elevation,
                distanceMeters = point.distanceMeters
            )
        },
        distanceMeters = profile.distanceMeters
    )
}

private fun calculateLineObstruction(profile: ElevationProfileResult, supportHeightMeters: Double): Double {
    val total = profile.distanceMeters.coerceAtLeast(1f).toDouble()
    val startHeight = profile.points.first().elevation + USER_EYE_HEIGHT_METERS
    val endHeight = profile.points.last().elevation + supportHeightMeters
    return profile.points.maxOf { point ->
        val fraction = point.distanceMeters / total
        val lineHeight = startHeight + (endHeight - startHeight) * fraction
        point.elevation - lineHeight
    }
}

private fun calculateFresnelObstruction(
    profile: ElevationProfileResult,
    supportHeightMeters: Double,
    frequencyMHz: Int
): Double {
    val total = profile.distanceMeters.coerceAtLeast(1f).toDouble()
    val startHeight = profile.points.first().elevation + USER_EYE_HEIGHT_METERS
    val endHeight = profile.points.last().elevation + supportHeightMeters
    return profile.points.maxOf { point ->
        val lineHeight = lineHeightAt(
            distanceMeters = point.distanceMeters.toDouble(),
            totalDistanceMeters = total,
            startHeightMeters = startHeight,
            endHeightMeters = endHeight
        )
        val clearance = fresnelClearanceMeters(
            distanceMeters = point.distanceMeters.toDouble(),
            totalDistanceMeters = total,
            frequencyMHz = frequencyMHz
        )
        point.elevation - (lineHeight - clearance)
    }
}

private fun lineHeightAt(
    distanceMeters: Double,
    totalDistanceMeters: Double,
    startHeightMeters: Double,
    endHeightMeters: Double
): Double {
    val fraction = (distanceMeters / totalDistanceMeters.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
    return startHeightMeters + (endHeightMeters - startHeightMeters) * fraction
}

private fun fresnelClearanceMeters(
    distanceMeters: Double,
    totalDistanceMeters: Double,
    frequencyMHz: Int
): Double {
    val d1Km = distanceMeters / 1000.0
    val d2Km = (totalDistanceMeters - distanceMeters) / 1000.0
    val totalKm = totalDistanceMeters / 1000.0
    val frequencyGHz = frequencyMHz / 1000.0
    if (d1Km <= 0.0 || d2Km <= 0.0 || totalKm <= 0.0 || frequencyGHz <= 0.0) return 0.0
    return 0.6 * 17.32 * sqrt((d1Km * d2Km) / (frequencyGHz * totalKm))
}

private fun formatFrequencyLabel(frequencyMHz: Int): String {
    return if (frequencyMHz >= 10_000) {
        "${frequencyMHz / 1000} GHz"
    } else {
        "$frequencyMHz MHz"
    }
}

private fun formatProfileCalculationTime(timestampMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}

private fun formatProfileGps(latitude: Double, longitude: Double): String {
    return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

@SuppressLint("MissingPermission")
private fun getElevationLastKnownLocation(context: Context): Location? {
    val locManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        locManager.getProviders(true)
            .mapNotNull { locManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    } catch (e: Exception) {
        null
    }
}

private fun savePendingElevationProfilePoint(
    context: Context,
    antennaId: String,
    fromLocation: Location,
    site: LocalisationEntity
): PendingElevationProfilePoint {
    val point = PendingElevationProfilePoint(
        antennaId = antennaId,
        fromLatitude = fromLocation.latitude,
        fromLongitude = fromLocation.longitude,
        toLatitude = site.latitude,
        toLongitude = site.longitude
    )
    context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        .edit()
        .putString(PENDING_PROFILE_ANTENNA_ID, point.antennaId)
        .putDouble(PENDING_PROFILE_FROM_LAT, point.fromLatitude)
        .putDouble(PENDING_PROFILE_FROM_LON, point.fromLongitude)
        .putDouble(PENDING_PROFILE_TO_LAT, point.toLatitude)
        .putDouble(PENDING_PROFILE_TO_LON, point.toLongitude)
        .apply()
    return point
}

private fun loadPendingElevationProfilePoint(context: Context, antennaId: String): PendingElevationProfilePoint? {
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    if (prefs.getString(PENDING_PROFILE_ANTENNA_ID, null) != antennaId) return null
    if (
        !prefs.contains(PENDING_PROFILE_FROM_LAT) ||
        !prefs.contains(PENDING_PROFILE_FROM_LON) ||
        !prefs.contains(PENDING_PROFILE_TO_LAT) ||
        !prefs.contains(PENDING_PROFILE_TO_LON)
    ) return null

    return PendingElevationProfilePoint(
        antennaId = antennaId,
        fromLatitude = prefs.getDouble(PENDING_PROFILE_FROM_LAT, 0.0),
        fromLongitude = prefs.getDouble(PENDING_PROFILE_FROM_LON, 0.0),
        toLatitude = prefs.getDouble(PENDING_PROFILE_TO_LAT, 0.0),
        toLongitude = prefs.getDouble(PENDING_PROFILE_TO_LON, 0.0)
    )
}

private fun clearPendingElevationProfilePoint(context: Context, antennaId: String) {
    val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
    if (prefs.getString(PENDING_PROFILE_ANTENNA_ID, null) != antennaId) return

    prefs.edit()
        .remove(PENDING_PROFILE_ANTENNA_ID)
        .remove(PENDING_PROFILE_FROM_LAT)
        .remove(PENDING_PROFILE_FROM_LON)
        .remove(PENDING_PROFILE_TO_LAT)
        .remove(PENDING_PROFILE_TO_LON)
        .apply()
}

private fun saveElevationProfile(
    context: Context,
    antennaId: String,
    profile: ElevationProfileResult,
    calculationInfo: ProfileCalculationInfo
) {
    val points = JSONArray()
    profile.points.forEach { point ->
        points.put(
            JSONObject()
                .put("lat", point.latitude)
                .put("lon", point.longitude)
                .put("elevation", point.elevation)
                .put("distanceMeters", point.distanceMeters.toDouble())
        )
    }

    val json = JSONObject()
        .put("antennaId", antennaId)
        .put("calculatedAtMillis", calculationInfo.calculatedAtMillis)
        .put("fromLatitude", calculationInfo.fromLatitude)
        .put("fromLongitude", calculationInfo.fromLongitude)
        .put("distanceMeters", profile.distanceMeters.toDouble())
        .put("points", points)

    context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        .edit()
        .putString(savedElevationProfileKey(antennaId), json.toString())
        .apply()
}

private fun loadSavedElevationProfile(context: Context, antennaId: String): SavedElevationProfile? {
    val raw = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        .getString(savedElevationProfileKey(antennaId), null)
        ?: return null

    return runCatching {
        val json = JSONObject(raw)
        if (json.optString("antennaId") != antennaId) return@runCatching null

        val array = json.getJSONArray("points")
        val points = mutableListOf<ElevationProfilePoint>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            points.add(
                ElevationProfilePoint(
                    latitude = item.getDouble("lat"),
                    longitude = item.getDouble("lon"),
                    elevation = item.getDouble("elevation"),
                    distanceMeters = item.getDouble("distanceMeters").toFloat()
                )
            )
        }
        if (points.size < 2) return@runCatching null

        SavedElevationProfile(
            profile = ElevationProfileResult(
                points = points,
                distanceMeters = json.optDouble("distanceMeters", points.last().distanceMeters.toDouble()).toFloat()
            ),
            calculationInfo = ProfileCalculationInfo(
                calculatedAtMillis = json.getLong("calculatedAtMillis"),
                fromLatitude = json.getDouble("fromLatitude"),
                fromLongitude = json.getDouble("fromLongitude")
            )
        )
    }.getOrNull()
}

private fun savedElevationProfileKey(antennaId: String): String = "$SAVED_PROFILE_KEY_PREFIX$antennaId"

private fun android.content.SharedPreferences.Editor.putDouble(key: String, value: Double): android.content.SharedPreferences.Editor {
    return putLong(key, java.lang.Double.doubleToRawLongBits(value))
}

private fun android.content.SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
    return if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(defaultValue))) else defaultValue
}

private data class ElevationProfilePoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val distanceMeters: Float
)

private data class ElevationProfileResult(
    val points: List<ElevationProfilePoint>,
    val distanceMeters: Float
)

private data class ProfileSiteData(
    val site: LocalisationEntity?,
    val physique: PhysiqueEntity?,
    val technique: TechniqueEntity?
)

private data class PendingElevationProfilePoint(
    val antennaId: String,
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double
)

private data class ProfileCalculationInfo(
    val calculatedAtMillis: Long,
    val fromLatitude: Double,
    val fromLongitude: Double
)

private data class SavedElevationProfile(
    val profile: ElevationProfileResult,
    val calculationInfo: ProfileCalculationInfo
)

private const val USER_EYE_HEIGHT_METERS = 1.5
private const val ELEVATION_PROFILE_RECALC_INTERVAL_MS = 60_000L
private const val PROFILE_ERROR_OFFLINE = "offline"
private const val PROFILE_ERROR_OFFLINE_PENDING = "offline_pending"
private const val SAVED_PROFILE_KEY_PREFIX = "saved_elevation_profile_"
private const val PENDING_PROFILE_ANTENNA_ID = "pending_elevation_profile_antenna_id"
private const val PENDING_PROFILE_FROM_LAT = "pending_elevation_profile_from_lat"
private const val PENDING_PROFILE_FROM_LON = "pending_elevation_profile_from_lon"
private const val PENDING_PROFILE_TO_LAT = "pending_elevation_profile_to_lat"
private const val PENDING_PROFILE_TO_LON = "pending_elevation_profile_to_lon"
