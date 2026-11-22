package fr.geotower.ui.screens.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.geotower.data.AnfrRepository
import fr.geotower.ui.screens.changes.CHANGES_MAP_ROUTE
import fr.geotower.ui.screens.changes.ChangesMapScreen
import fr.geotower.ui.screens.emitters.AnimatedSplitPane
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
 * Enveloppe [StatisticsScreen] pour les grands écrans (tablette, fold déplié).
 * Quand l'utilisateur clique sur la carte des évolutions depuis la page des statistiques,
 * la page des statistiques se réduit sur la gauche (50%) et la carte s'ouvre dans un
 * volet latéral animé sur la droite (50%).
 */
@Composable
fun StatisticsWrapperScreen(
    navController: NavController,
    repository: AnfrRepository,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val splitDisplay = splitDisplayEnabled()
    var isChangesMapSplitOpen by rememberSaveable { mutableStateOf(false) }

    val isSplitActive = splitDisplay && isChangesMapSplitOpen
    val leftWidthFraction by animateFloatAsState(
        targetValue = if (isSplitActive) 0.5f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "stats_split_anim"
    )
    val layoutDirection = LocalLayoutDirection.current

    BackHandler(enabled = isSplitActive) {
        isChangesMapSplitOpen = false
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(leftWidthFraction)
                .fillMaxHeight()
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = if (isSplitActive) 0.dp else contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding()
                )
        ) {
            StatisticsScreen(
                navController = navController,
                repository = repository,
                onOpenChangesMap = {
                    if (splitDisplay) {
                        isChangesMapSplitOpen = !isChangesMapSplitOpen
                    } else {
                        navController.navigate(CHANGES_MAP_ROUTE)
                    }
                },
                isChangesMapOpen = isSplitActive
            )
        }

        AnimatedSplitPane(visible = isSplitActive) {
            ChangesMapScreen(
                navController = navController,
                isSplitScreen = true,
                onCloseSplitScreen = { isChangesMapSplitOpen = false },
                onOpenSupportDetail = { supId ->
                    navController.navigate("support_detail/$supId")
                }
            )
        }
    }
}
