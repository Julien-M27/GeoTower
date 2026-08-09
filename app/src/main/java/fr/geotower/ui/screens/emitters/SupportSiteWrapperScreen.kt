package fr.geotower.ui.screens.emitters

import android.net.Uri
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogger

private enum class SiteDetailSidePane {
    ElevationProfile,
    ThroughputCalculator
}

/**
 * Ouvre-t-on les fiches en deux volets ? Deux conditions, jamais une seule : le style d'affichage
 * (le mode simplifié impose le plein écran, et sans choix explicite on est en AUTO) ET une fenêtre
 * assez large. La taille est relue à chaque composition, donc plier/déplier ou redimensionner en
 * multi-fenêtre bascule tout seul, sans redémarrage.
 */
@Composable
private fun splitDisplayEnabled(): Boolean {
    val configuration = LocalConfiguration.current
    return AppConfig.splitDisplayEnabled(
        style = AppConfig.effectiveDisplayStyle(),
        smallestDimensionDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportSiteWrapperScreen(
    navController: NavController,
    repository: AnfrRepository,
    radioRepository: RadioRepository,
    supportId: String,
    highlightedOperatorKey: String? = null,
    applyMapFilters: Boolean = false,
    photoDraftId: String? = null,
    isSplitScreen: Boolean = false,
    onCloseSplitScreen: () -> Unit = {},
    // Mode simplifié : opérateur à déplier d'emblée sur la fiche support.
    expandAntennaId: String? = null,
    onOpenAntennaInHost: ((String) -> Unit)? = null
) {
    // Pas de pré-chargement ici : [SupportDetailScreen] résout déjà l'antenne d'ancrage
    // (`selectSupportAnchor`, mêmes règles : coordonnées cliquées → position GPS → première) et
    // réécrit `clicked_lat`/`clicked_lon`. Le refaire ici doublait la requête la plus lourde de
    // l'ouverture d'une fiche et ajoutait un second écran de chargement.
    var selectedSiteId by remember { mutableStateOf<String?>(null) }
    var selectedSidePane by remember { mutableStateOf<SiteDetailSidePane?>(null) }
    val splitDisplay = splitDisplayEnabled()

    val canOpenSiteSplit = splitDisplay && !isSplitScreen
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
                    applyMapFilters = applyMapFilters,
                    onClose = { selectedSidePane = null },
                    onOpenElevation = { selectedSidePane = SiteDetailSidePane.ElevationProfile },
                    onOpenThroughput = { selectedSidePane = SiteDetailSidePane.ThroughputCalculator }
                )
            } else {
                SupportDetailScreen(
                    navController = navController,
                    repository = repository,
                    radioRepository = radioRepository,
                    siteId = supportId,
                    highlightedOperatorKey = highlightedOperatorKey,
                    applyMapFilters = applyMapFilters,
                    photoDraftId = photoDraftId,
                    isSplitScreen = isSplitScreen,
                    onCloseSplitScreen = onCloseSplitScreen,
                    expandAntennaId = expandAntennaId,
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
                            val route = if (applyMapFilters) {
                                "site_detail_from_map/${Uri.encode(id)}"
                            } else {
                                "site_detail/${Uri.encode(id)}"
                            }
                            navController.navigate(route)
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
                    applyMapFilters = applyMapFilters,
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
fun NearEmittersSupportWrapperScreen(
    navController: NavController,
    repository: AnfrRepository,
    radioRepository: RadioRepository
) {
    val splitDisplay = splitDisplayEnabled()
    var selectedSupportId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSupportOperatorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSiteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSidePane by remember { mutableStateOf<SiteDetailSidePane?>(null) }
    val isSplitActive = splitDisplay && selectedSupportId != null
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
                    radioRepository = radioRepository,
                    supportId = selectedSupportId!!,
                    highlightedOperatorKey = selectedSupportOperatorKey,
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
                    onSupportClick = { site, searchedOperatorKey ->
                        val supportId = site.idSupport?.takeIf { it.isNotBlank() } ?: site.id.toString()
                        if (splitDisplay) {
                            if (selectedSupportId == supportId && selectedSiteId == null) {
                                selectedSupportId = null
                                selectedSupportOperatorKey = null
                            } else {
                                selectedSupportId = supportId
                                selectedSupportOperatorKey = searchedOperatorKey
                            }
                            selectedSiteId = null
                            selectedSidePane = null
                        } else {
                            val highlightedOperatorParam = searchedOperatorKey?.let { "?operator=$it" }.orEmpty()
                            navController.navigate("support_detail/${Uri.encode(supportId)}$highlightedOperatorParam")
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
                    radioRepository = radioRepository,
                    supportId = supportId,
                    highlightedOperatorKey = selectedSupportOperatorKey,
                    isSplitScreen = true,
                    onCloseSplitScreen = {
                        selectedSupportId = null
                        selectedSupportOperatorKey = null
                    },
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
    antennaId: String,
    applyMapFilters: Boolean = false
) {
    val splitDisplay = splitDisplayEnabled()
    var selectedSidePane by remember(antennaId) { mutableStateOf<SiteDetailSidePane?>(null) }
    val isSplitActive = splitDisplay && selectedSidePane != null
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
                applyMapFilters = applyMapFilters,
                isSplitScreen = isSplitActive,
                onCloseSplitScreen = { selectedSidePane = null },
                onOpenElevationProfile = {
                    if (splitDisplay) selectedSidePane = SiteDetailSidePane.ElevationProfile
                    else navController.navigate("elevation_profile/$it")
                },
                onOpenThroughputCalculator = {
                    if (splitDisplay) selectedSidePane = SiteDetailSidePane.ThroughputCalculator
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
    antennaId: String,
    applyMapFilters: Boolean = false,
    onClose: () -> Unit,
    onOpenElevation: (String) -> Unit,
    onOpenThroughput: (String) -> Unit
) {
    SiteDetailScreen(
        navController = navController,
        repository = repository,
        antennaId = antennaId,
        applyMapFilters = applyMapFilters,
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
    antennaId: String,
    onClose: () -> Unit
) {
    ElevationProfileScreen(
        navController = navController,
        repository = repository,
        antennaId = antennaId,
        isSplitScreen = true,
        onCloseSplitScreen = onClose
    )
}

@Composable
private fun ThroughputCalculatorPane(
    navController: NavController,
    repository: AnfrRepository,
    antennaId: String,
    onClose: () -> Unit
) {
    ThroughputCalculatorScreen(
        navController = navController,
        repository = repository,
        antennaId = antennaId,
        isSplitScreen = true,
        onCloseSplitScreen = onClose
    )
}
