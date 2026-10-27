package fr.geotower.ui.screens.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.data.RadioRepository
import fr.geotower.ui.screens.emitters.AnimatedSplitPane
import fr.geotower.ui.screens.emitters.ElevationProfilePane
import fr.geotower.ui.screens.emitters.SiteDetailPane
import fr.geotower.ui.screens.emitters.SiteDetailSidePane
import fr.geotower.ui.screens.emitters.SupportSiteWrapperScreen
import fr.geotower.ui.screens.emitters.TheoreticalCoveragePane
import fr.geotower.ui.screens.emitters.ThroughputCalculatorPane
import fr.geotower.utils.AppConfig

@Composable
private fun splitDisplayEnabled(): Boolean {
    val configuration = LocalConfiguration.current
    return AppConfig.splitDisplayEnabled(
        style = AppConfig.effectiveDisplayStyle(),
        smallestDimensionDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    )
}

/**
 * Enveloppe [MapScreen] pour les grands écrans (tablette, fold déplié).
 * Quand l'utilisateur clique sur un site sur la carte, la carte se réduit sur la gauche
 * et la fiche du support s'ouvre dans un volet latéral sur la droite.
 */
@Composable
fun MapSupportWrapperScreen(
    navController: NavController,
    viewModel: MapViewModel,
    repository: AnfrRepository,
    radioRepository: RadioRepository,
    photoDraftId: String? = null,
    plannedTripId: String? = null,
    plannedTripMode: String? = null,
    focusLat: Double? = null,
    focusLon: Double? = null,
    onOpenSimpleModeMenu: (() -> Unit)? = null
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
        label = "map_support_split_anim"
    )

    fun closeSupportSelection() {
        selectedSupportId = null
        selectedSupportOperatorKey = null
        selectedSiteId = null
        selectedSidePane = null
    }

    // Gestion du bouton retour quand le volet latéral est ouvert
    BackHandler(enabled = isSplitActive) {
        when {
            selectedSidePane != null -> selectedSidePane = null
            selectedSiteId != null -> selectedSiteId = null
            else -> closeSupportSelection()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(leftWidthFraction)
                .fillMaxHeight()
        ) {
            MapScreen(
                navController = navController,
                viewModel = viewModel,
                photoDraftId = photoDraftId,
                plannedTripId = plannedTripId,
                plannedTripMode = plannedTripMode,
                focusLat = focusLat,
                focusLon = focusLon,
                onOpenSimpleModeMenu = onOpenSimpleModeMenu,
                onSupportClick = { supportId, operatorKey, _, _ ->
                    if (splitDisplay) {
                        selectedSupportId = supportId
                        selectedSupportOperatorKey = operatorKey
                        selectedSiteId = null
                        selectedSidePane = null
                        true
                    } else {
                        false
                    }
                }
            )
        }

        AnimatedSplitPane(visible = isSplitActive) {
            val supportId = selectedSupportId ?: return@AnimatedSplitPane
            val siteId = selectedSiteId
            when {
                siteId != null && selectedSidePane == SiteDetailSidePane.ElevationProfile -> ElevationProfilePane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                siteId != null && selectedSidePane == SiteDetailSidePane.ThroughputCalculator -> ThroughputCalculatorPane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                siteId != null && selectedSidePane == SiteDetailSidePane.TheoreticalCoverage -> TheoreticalCoveragePane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    onClose = { selectedSidePane = null }
                )
                siteId != null -> SiteDetailPane(
                    navController = navController,
                    repository = repository,
                    antennaId = siteId,
                    applyMapFilters = true,
                    onClose = {
                        selectedSiteId = null
                        selectedSidePane = null
                    },
                    onOpenElevation = { selectedSidePane = SiteDetailSidePane.ElevationProfile },
                    onOpenThroughput = { selectedSidePane = SiteDetailSidePane.ThroughputCalculator },
                    onOpenCoverage = { selectedSidePane = SiteDetailSidePane.TheoreticalCoverage }
                )
                else -> SupportSiteWrapperScreen(
                    navController = navController,
                    repository = repository,
                    radioRepository = radioRepository,
                    supportId = supportId,
                    highlightedOperatorKey = selectedSupportOperatorKey,
                    applyMapFilters = true,
                    photoDraftId = photoDraftId,
                    isSplitScreen = true,
                    onCloseSplitScreen = { closeSupportSelection() },
                    onOpenAntennaInHost = { antennaId ->
                        selectedSiteId = antennaId
                        selectedSidePane = null
                    }
                )
            }
        }
    }
}
