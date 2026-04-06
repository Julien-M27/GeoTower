package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ResponsiveDualPaneLayout(
    modifier: Modifier = Modifier,
    sidebar: @Composable (width: Dp, onCloseSidebar: () -> Unit) -> Unit,
    content: @Composable (isExpanded: Boolean, isSidebarVisible: Boolean, onToggleSidebar: () -> Unit) -> Unit
) {
    val configuration = LocalConfiguration.current
    var isSidebarVisible by remember { mutableStateOf(true) }

    // La barre latérale fera 30%, MAIS jamais moins de 225dp
    val fullSidebarWidth = androidx.compose.ui.unit.max(225.dp, (configuration.screenWidthDp * 0.3f).dp)

    // ✅ CORRECTION : On utilise la configuration pour savoir si on est sur tablette (plus besoin de BoxWithConstraints)
    val isExpanded = configuration.screenWidthDp >= 600

    Box(modifier = modifier.fillMaxSize()) {
        if (isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 1. LA BARRE LATÉRALE AVEC ANIMATION
                AnimatedVisibility(
                    visible = isSidebarVisible,
                    enter = expandHorizontally(
                        expandFrom = Alignment.Start,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                ) {
                    sidebar(fullSidebarWidth) { isSidebarVisible = false }
                }

                // 2. LE CONTENU PRINCIPAL
                // Le weight(1f) est naturellement reconnu car nous sommes dans le bloc d'un Row !
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    content(
                        true,
                        isSidebarVisible,
                        { isSidebarVisible = !isSidebarVisible }
                    )
                }
            }
        } else {
            // MODE TÉLÉPHONE (Pas de barre latérale)
            content(
                false,
                false,
                {}
            )
        }
    }
}