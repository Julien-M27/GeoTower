package fr.geotower.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.geotower.R
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PageScrollPrefs
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Marge basse par défaut de la pastille, au-dessus du bord inférieur du conteneur. */
private val DefaultBottomPadding = 24.dp

/**
 * Pastille flottante « haut de page / bas de page », pilotée par les réglages de la page
 * ([PageScrollPrefs.Aid.TOP] et [PageScrollPrefs.Aid.BOTTOM], réglables indépendamment).
 *
 * À appeler comme DERNIER enfant du [androidx.compose.foundation.layout.Box] qui contient le
 * conteneur défilant, pour qu'elle se dessine par-dessus :
 *
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Column(Modifier.verticalScroll(scrollState)) { … }
 *     PageScrollEdgeButtons(PageScrollPrefs.SITE, scrollState)
 * }
 * ```
 *
 * N'affiche rien si les deux boutons sont désactivés ou si le contenu tient dans l'écran.
 */
@Composable
fun BoxScope.PageScrollEdgeButtons(
    page: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
    bottomPadding: Dp = DefaultBottomPadding
) {
    val showTop = PageScrollPrefs.isEnabled(PageScrollPrefs.Aid.TOP, page)
    val showBottom = PageScrollPrefs.isEnabled(PageScrollPrefs.Aid.BOTTOM, page)
    if (!showTop && !showBottom) return

    val scope = rememberCoroutineScope()
    // Clé sur l'état : l'accueil change de conteneur défilant selon l'orientation.
    val canScrollUp by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    val canScrollDown by remember(scrollState) { derivedStateOf { scrollState.value < scrollState.maxValue } }
    val scrollable by remember(scrollState) { derivedStateOf { scrollState.maxValue > 0 } }

    ScrollEdgePill(
        visible = scrollable,
        showTop = showTop,
        showBottom = showBottom,
        topEnabled = canScrollUp,
        bottomEnabled = canScrollDown,
        onTop = { scope.launch { scrollState.animateScrollTo(0) } },
        onBottom = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
        modifier = modifier.align(alignment).padding(bottom = bottomPadding)
    )
}

/**
 * Variante `LazyColumn`. Le saut est « accéléré » (voir [animateScrollToItemSmoothly]) : sur une
 * longue liste, tout animer élément par élément prendrait plusieurs secondes.
 */
@Composable
fun BoxScope.PageScrollEdgeButtons(
    page: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
    bottomPadding: Dp = DefaultBottomPadding
) {
    val showTop = PageScrollPrefs.isEnabled(PageScrollPrefs.Aid.TOP, page)
    val showBottom = PageScrollPrefs.isEnabled(PageScrollPrefs.Aid.BOTTOM, page)
    if (!showTop && !showBottom) return

    val scope = rememberCoroutineScope()
    val canScrollUp by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val canScrollDown by remember(listState) { derivedStateOf { listState.canScrollForward } }

    ScrollEdgePill(
        visible = canScrollUp || canScrollDown,
        showTop = showTop,
        showBottom = showBottom,
        topEnabled = canScrollUp,
        bottomEnabled = canScrollDown,
        onTop = { scope.launch { listState.animateScrollToItemSmoothly(0) } },
        onBottom = {
            scope.launch {
                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                if (lastIndex > 0) listState.animateScrollToItemSmoothly(lastIndex)
            }
        },
        modifier = modifier.align(alignment).padding(bottom = bottomPadding)
    )
}

/**
 * Saute quasi instantanément jusqu'à quelques éléments de la cible, puis termine en animation
 * pour garder un ralenti lisible plutôt qu'un défilement interminable.
 */
internal suspend fun LazyListState.animateScrollToItemSmoothly(targetIndex: Int) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return

    val target = targetIndex.coerceIn(0, lastIndex)

    // Longueur du « ralenti » final, en nombre d'items animés jusqu'à la cible.
    val tailItems = 10
    val current = firstVisibleItemIndex

    if (abs(target - current) > tailItems) {
        val jumpTo = if (target > current) target - tailItems else target + tailItems
        scrollToItem(jumpTo.coerceIn(0, lastIndex))
    }

    animateScrollToItem(target)
}

@Composable
private fun ScrollEdgePill(
    visible: Boolean,
    showTop: Boolean,
    showBottom: Boolean,
    topEnabled: Boolean,
    bottomEnabled: Boolean,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizing = LocalGeoTowerUiSizing.current
    val themeMode by AppConfig.themeMode
    val isDark = (themeMode == 2) || (themeMode == 0 && isSystemInDarkTheme())
    val pillColor = if (isDark) {
        Color(0xFF2C2C2C).copy(alpha = 0.85f)
    } else {
        Color(0xFFF2F2F2).copy(alpha = 0.85f)
    }
    val iconColor = MaterialTheme.colorScheme.onSurface

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(sizing.component(32.dp)),
            color = pillColor,
            shadowElevation = 0.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = sizing.spacing(4.dp),
                    vertical = sizing.spacing(2.dp)
                ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showTop) {
                    IconButton(onClick = onTop, enabled = topEnabled) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.appstrings_top),
                            tint = iconColor
                        )
                    }
                }
                if (showBottom) {
                    IconButton(onClick = onBottom, enabled = bottomEnabled) {
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
