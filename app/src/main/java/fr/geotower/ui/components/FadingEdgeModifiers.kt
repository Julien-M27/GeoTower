package fr.geotower.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.geotower.utils.AppConfig
import fr.geotower.utils.PowerProfile

/**
 * Hauteur estompée en haut et en bas d'une zone défilante quand le « flou au défilement » est
 * actif. Tout ce qui tombe dans cette bande est délavé : un défilement qui vise un élément précis
 * doit donc le poser SOUS cette bande, sinon son titre est illisible.
 */
val GeoTowerFadingEdgeHeight: Dp = 80.dp

/** Le fondu de bord est-il réellement dessiné ? (réglage « flou » + profil d'énergie). */
@Composable
fun isGeoTowerFadingEdgeActive(): Boolean =
    AppConfig.isBlurEnabled.value && PowerProfile.richAnimations

fun Modifier.geoTowerFadingEdge(
    scrollState: ScrollState,
    fadeHeight: Dp = GeoTowerFadingEdgeHeight,
    requireScrollableContent: Boolean = false
): Modifier {
    if (!AppConfig.isBlurEnabled.value || !PowerProfile.richAnimations) return this

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()

            val hasScrollableContent = scrollState.value > 0 || scrollState.maxValue > 0 || scrollState.canScrollForward
            if (requireScrollableContent && !hasScrollableContent) return@drawWithContent

            val heightPx = fadeHeight.toPx()
            val topAlpha = (scrollState.value.toFloat() / heightPx).coerceIn(0f, 1f)

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

            val remainingScroll = scrollState.maxValue - scrollState.value
            val bottomAlpha = (remainingScroll.toFloat() / heightPx).coerceIn(0f, 1f)

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

fun Modifier.geoTowerLazyListFadingEdge(
    lazyListState: LazyListState,
    fadeHeight: Dp = GeoTowerFadingEdgeHeight
): Modifier {
    if (!AppConfig.isBlurEnabled.value || !PowerProfile.richAnimations) return this

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()

            val heightPx = fadeHeight.toPx()
            val isFirstItemVisible = lazyListState.firstVisibleItemIndex == 0
            val topAlpha = if (!isFirstItemVisible) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset.toFloat() / heightPx).coerceIn(0f, 1f)
            }

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

            val bottomAlpha = if (lazyListState.canScrollForward) 1f else 0f
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

fun Modifier.settingsPopupFadingEdge(scrollState: ScrollState): Modifier {
    return geoTowerFadingEdge(scrollState, requireScrollableContent = true)
}

fun Modifier.colorPaletteFadingEdge(scrollState: ScrollState): Modifier {
    return geoTowerFadingEdge(scrollState)
}
