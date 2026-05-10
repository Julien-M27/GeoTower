package fr.geotower.ui.screens.emitters

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger

private enum class SiteDetailSidePane {
    ElevationProfile,
    ThroughputCalculator
}

@Composable
fun SupportSiteWrapperScreen(
    navController: NavController,
    repository: AnfrRepository,
    supportId: Long,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {},
    onOpenAntennaInHost: ((Long) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isReady by remember { mutableStateOf(false) }
    var selectedSiteId by remember { mutableStateOf<Long?>(null) }
    var selectedSidePane by remember { mutableStateOf<SiteDetailSidePane?>(null) }
    val displayStyle by AppConfig.displayStyle

    LaunchedEffect(supportId) {
        isReady = false
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
                val savedLat = prefs.getFloat("clicked_lat", 0f).toDouble()
                val savedLon = prefs.getFloat("clicked_lon", 0f).toDouble()

                val antennas = repository.getAntennasByExactId(supportId.toString())
                if (antennas.isNotEmpty()) {
                    var site = antennas.find {
                        Math.abs(it.latitude - savedLat) < 0.005 && Math.abs(it.longitude - savedLon) < 0.005
                    }

                    if (site == null) {
                        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        val userLoc = if (hasPermission) {
                            try {
                                locManager.getProviders(true)
                                    .mapNotNull { locManager.getLastKnownLocation(it) }
                                    .maxByOrNull { it.time }
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }

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
                        .putFloat("clicked_lon", site!!.longitude.toFloat())
                        .apply()
                }
            } catch (e: Exception) {
                AppLogger.w(TAG_SUPPORT_WRAPPER, "Support selection restore failed", e)
            }
        }
        isReady = true
    }

    if (!isReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val canOpenSiteSplit = displayStyle == 1 && !isSplitScreen
    val isSplitActive = canOpenSiteSplit && selectedSiteId != null
    val isSiteToolSplitActive = isSplitActive && selectedSidePane != null
    val leftWidthFraction by animateFloatAsState(
        targetValue = if (isSplitActive) 0.5f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "split_screen_anim"
    )

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth(leftWidthFraction)) {
            if (isSiteToolSplitActive && selectedSiteId != null) {
                SiteDetailPane(
                    navController = navController,
                    repository = repository,
                    antennaId = selectedSiteId!!,
                    onClose = { selectedSidePane = null },
                    onOpenElevation = { selectedSidePane = SiteDetailSidePane.ElevationProfile },
                    onOpenThroughput = { selectedSidePane = SiteDetailSidePane.ThroughputCalculator }
                )
            } else {
                SupportDetailScreen(
                    navController = navController,
                    repository = repository,
                    siteId = supportId,
                    isSplitScreen = isSplitScreen,
                    onCloseSplitScreen = onCloseSplitScreen,
                    onAntennaClick = { id ->
                        if (onOpenAntennaInHost != null) {
                            onOpenAntennaInHost(id)
                        } else if (canOpenSiteSplit) {
                            if (selectedSiteId == id) {
                                selectedSiteId = null
                                selectedSidePane = null
                            } else {
                                selectedSiteId = id
                                selectedSidePane = null
                            }
                        } else {
                            navController.navigate("site_detail/$id")
                        }
                    }
                )
            }
        }

        AnimatedSplitPane(visible = isSplitActive) {
            val siteId = selectedSiteId ?: return@AnimatedSplitPane
            when (selectedSidePane) {
                SiteDetailSidePane.ElevationProfile -> ElevationProfilePane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                SiteDetailSidePane.ThroughputCalculator -> ThroughputCalculatorPane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                null -> SiteDetailPane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = {
                        selectedSiteId = null
                        selectedSidePane = null
                    },
                    onOpenElevation = { selectedSidePane = SiteDetailSidePane.ElevationProfile },
                    onOpenThroughput = { selectedSidePane = SiteDetailSidePane.ThroughputCalculator }
                )
            }
        }
    }
}

private const val TAG_SUPPORT_WRAPPER = "GeoTower"

@Composable
fun NearEmittersSupportWrapperScreen(
    navController: NavController,
    repository: AnfrRepository
) {
    val displayStyle by AppConfig.displayStyle
    var selectedSupportId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSiteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSidePane by remember { mutableStateOf<SiteDetailSidePane?>(null) }
    val isSplitActive = displayStyle == 1 && selectedSupportId != null
    val leftWidthFraction by animateFloatAsState(
        targetValue = if (isSplitActive) 0.5f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "near_support_split_anim"
    )

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth(leftWidthFraction)) {
            when {
                selectedSidePane != null && selectedSiteId != null -> SiteDetailPane(
                    navController = navController,
                    repository = repository,
                    antennaId = selectedSiteId!!,
                    onClose = { selectedSidePane = null },
                    onOpenElevation = { selectedSidePane = SiteDetailSidePane.ElevationProfile },
                    onOpenThroughput = { selectedSidePane = SiteDetailSidePane.ThroughputCalculator }
                )
                selectedSiteId != null && selectedSupportId != null -> SupportSiteWrapperScreen(
                    navController = navController,
                    repository = repository,
                    supportId = selectedSupportId!!,
                    isSplitScreen = true,
                    onCloseSplitScreen = {
                        selectedSiteId = null
                        selectedSidePane = null
                    },
                    onOpenAntennaInHost = { antennaId ->
                        selectedSiteId = antennaId
                        selectedSidePane = null
                    }
                )
                else -> NearEmittersScreen(
                    navController = navController,
                    repository = repository,
                    onSupportClick = { site ->
                        if (displayStyle == 1) {
                            if (selectedSupportId == site.id && selectedSiteId == null) {
                                selectedSupportId = null
                            } else {
                                selectedSupportId = site.id
                            }
                            selectedSiteId = null
                            selectedSidePane = null
                        } else {
                            navController.navigate("support_detail/${site.id}")
                        }
                    }
                )
            }
        }

        AnimatedSplitPane(visible = isSplitActive) {
            val supportId = selectedSupportId ?: return@AnimatedSplitPane
            val siteId = selectedSiteId
            when {
                siteId == null -> SupportSiteWrapperScreen(
                    navController = navController,
                    repository = repository,
                    supportId = supportId,
                    isSplitScreen = true,
                    onCloseSplitScreen = { selectedSupportId = null },
                    onOpenAntennaInHost = { antennaId ->
                        selectedSiteId = antennaId
                        selectedSidePane = null
                    }
                )
                selectedSidePane == SiteDetailSidePane.ElevationProfile -> ElevationProfilePane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                selectedSidePane == SiteDetailSidePane.ThroughputCalculator -> ThroughputCalculatorPane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                else -> SiteDetailPane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = {
                        selectedSiteId = null
                        selectedSidePane = null
                    },
                    onOpenElevation = { selectedSidePane = SiteDetailSidePane.ElevationProfile },
                    onOpenThroughput = { selectedSidePane = SiteDetailSidePane.ThroughputCalculator }
                )
            }
        }
    }
}

@Composable
fun SiteDetailToolWrapperScreen(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: Long
) {
    val displayStyle by AppConfig.displayStyle
    var selectedSidePane by remember(antennaId) { mutableStateOf<SiteDetailSidePane?>(null) }
    val isSplitActive = displayStyle == 1 && selectedSidePane != null
    val siteWidthFraction by animateFloatAsState(
        targetValue = if (isSplitActive) 0.5f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "site_tool_split_anim"
    )

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth(siteWidthFraction)) {
            SiteDetailScreen(
                navController = navController,
                repository = repository,
                antennaId = antennaId,
                isSplitScreen = isSplitActive,
                onCloseSplitScreen = { selectedSidePane = null },
                onOpenElevationProfile = {
                    if (displayStyle == 1) selectedSidePane = SiteDetailSidePane.ElevationProfile
                    else navController.navigate("elevation_profile/$it")
                },
                onOpenThroughputCalculator = {
                    if (displayStyle == 1) selectedSidePane = SiteDetailSidePane.ThroughputCalculator
                    else navController.navigate("throughput_calculator/$it")
                }
            )
        }

        AnimatedSplitPane(visible = isSplitActive) {
            when (selectedSidePane) {
                SiteDetailSidePane.ElevationProfile -> ElevationProfilePane(
                    navController = navController,
                    repository = repository,
                    antennaId = antennaId,
                    onClose = { selectedSidePane = null }
                )
                SiteDetailSidePane.ThroughputCalculator -> ThroughputCalculatorPane(
                    navController = navController,
                    repository = repository,
                    antennaId = antennaId,
                    onClose = { selectedSidePane = null }
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun RowScope.AnimatedSplitPane(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ),
        modifier = Modifier.weight(1f).fillMaxHeight()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
private fun SiteDetailPane(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: Long,
    onClose: () -> Unit,
    onOpenElevation: (String) -> Unit,
    onOpenThroughput: (String) -> Unit
) {
    SiteDetailScreen(
        navController = navController,
        repository = repository,
        antennaId = antennaId,
        isSplitScreen = true,
        onCloseSplitScreen = onClose,
        onOpenElevationProfile = onOpenElevation,
        onOpenThroughputCalculator = onOpenThroughput
    )
}

@Composable
private fun ElevationProfilePane(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: Long,
    onClose: () -> Unit
) {
    ElevationProfileScreen(
        navController = navController,
        repository = repository,
        antennaId = antennaId.toString(),
        isSplitScreen = true,
        onCloseSplitScreen = onClose
    )
}

@Composable
private fun ThroughputCalculatorPane(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: Long,
    onClose: () -> Unit
) {
    ThroughputCalculatorScreen(
        navController = navController,
        repository = repository,
        antennaId = antennaId.toString(),
        isSplitScreen = true,
        onCloseSplitScreen = onClose
    )
}
