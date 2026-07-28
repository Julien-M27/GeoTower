package fr.geotower.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiSizing
import fr.geotower.utils.PageScrollPrefs
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// Repères visuels de la barre : fine au repos, épaissie pendant le glissé.
private val ThumbWidth = 4.dp
private val ThumbWidthDragged = 8.dp
private val ThumbMinHeight = 40.dp
private val TrackInset = 3.dp

/**
 * Largeur de la zone tactile collée au bord droit qui capte le glissé de la barre.
 * Sert aussi de marge de sécurité aux boutons « haut / bas de page » (voir
 * [PageScrollEdgeButtons]) pour qu'un appui dessus ne soit jamais volé par la barre.
 */
internal val ScrollbarTouchTargetWidth = 24.dp

private const val IDLE_ALPHA = 0.45f

/**
 * Barre latérale de défilement pour un conteneur `verticalScroll`.
 *
 * À placer AVANT [androidx.compose.foundation.verticalScroll] dans la chaîne de modificateurs
 * (comme [geoTowerFadingEdge]) : le dessin et la zone tactile restent ainsi dans le repère du
 * viewport au lieu de défiler avec le contenu.
 *
 * ```
 * Modifier.fillMaxSize().geoTowerScrollbar(scrollState).verticalScroll(scrollState)
 * ```
 */
@Composable
fun Modifier.geoTowerScrollbar(
    scrollState: ScrollState,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val sizing = LocalGeoTowerUiSizing.current
    val color = MaterialTheme.colorScheme.primary
    var dragging by remember { mutableStateOf(false) }

    val scrollable = scrollState.maxValue > 0
    val active = dragging || scrollState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (!scrollable) 0f else if (active) 1f else IDLE_ALPHA,
        animationSpec = tween(durationMillis = 180),
        label = "geoTowerScrollbarAlpha"
    )
    val thumbWidth by animateDpAsState(
        targetValue = sizing.component(if (dragging) ThumbWidthDragged else ThumbWidth),
        animationSpec = tween(durationMillis = 180),
        label = "geoTowerScrollbarWidth"
    )
    val inset = sizing.spacing(TrackInset)
    val minThumbHeight = sizing.component(ThumbMinHeight)
    val touchWidth = sizing.component(ScrollbarTouchTargetWidth)

    return this
        .drawWithContent {
            drawContent()
            // Lectures faites ici (phase de dessin) : le défilement n'invalide que le dessin.
            val max = scrollState.maxValue
            if (max <= 0 || alpha <= 0.01f) return@drawWithContent
            val geometry = thumbGeometry(
                viewport = size.height,
                contentLength = size.height + max,
                progress = scrollState.value.toFloat() / max,
                minThumbHeightPx = minThumbHeight.toPx()
            ) ?: return@drawWithContent
            drawThumb(geometry, thumbWidth.toPx(), inset.toPx(), color, alpha)
        }
        .pointerInput(scrollState, minThumbHeight, touchWidth) {
            val touchWidthPx = touchWidth.toPx()
            val minThumbPx = minThumbHeight.toPx()
            awaitPointerEventScope {
                while (true) {
                    val down = awaitRailDown(touchWidthPx) ?: continue
                    val max = scrollState.maxValue
                    if (max <= 0) continue
                    val geometry = thumbGeometry(
                        viewport = size.height.toFloat(),
                        contentLength = size.height + max.toFloat(),
                        progress = scrollState.value.toFloat() / max,
                        minThumbHeightPx = minThumbPx
                    ) ?: continue

                    down.consume()
                    dragging = true
                    var thumbTop = geometry.top
                    // Appui hors du curseur : on saute directement à cet endroit.
                    if (down.position.y < geometry.top || down.position.y > geometry.top + geometry.height) {
                        thumbTop = (down.position.y - geometry.height / 2f).coerceIn(0f, geometry.travel)
                        scrollState.dispatchRawDelta(
                            thumbTop / geometry.travel * max - scrollState.value
                        )
                    }

                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            change?.consume()
                            break
                        }
                        thumbTop = (thumbTop + change.positionChange().y).coerceIn(0f, geometry.travel)
                        change.consume()
                        scrollState.dispatchRawDelta(
                            thumbTop / geometry.travel * scrollState.maxValue - scrollState.value
                        )
                    }
                    dragging = false
                }
            }
        }
}

/**
 * Barre latérale de défilement pour une `LazyColumn`.
 *
 * À poser sur le `modifier` de la liste : la position est estimée à partir de la taille moyenne
 * des éléments visibles (une liste paresseuse ne connaît pas sa hauteur totale réelle).
 */
@Composable
fun Modifier.geoTowerScrollbar(
    lazyListState: LazyListState,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val sizing = LocalGeoTowerUiSizing.current
    val color = MaterialTheme.colorScheme.primary
    var dragging by remember { mutableStateOf(false) }
    val dragProgress = remember { mutableStateOf<Float?>(null) }

    val active = dragging || lazyListState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else IDLE_ALPHA,
        animationSpec = tween(durationMillis = 180),
        label = "geoTowerLazyScrollbarAlpha"
    )
    val thumbWidth by animateDpAsState(
        targetValue = sizing.component(if (dragging) ThumbWidthDragged else ThumbWidth),
        animationSpec = tween(durationMillis = 180),
        label = "geoTowerLazyScrollbarWidth"
    )
    val inset = sizing.spacing(TrackInset)
    val minThumbHeight = sizing.component(ThumbMinHeight)
    val touchWidth = sizing.component(ScrollbarTouchTargetWidth)

    // Les défilements demandés par le glissé sont sérialisés ici : collectLatest annule la
    // requête précédente, ce qui évite d'empiler une coroutine par événement de pointeur.
    LaunchedEffect(lazyListState) {
        snapshotFlow { dragProgress.value }.collectLatest { progress ->
            if (progress == null) return@collectLatest
            val metrics = lazyListState.scrollMetrics() ?: return@collectLatest
            val target = progress * (metrics.contentLength - metrics.viewport)
            val index = (target / metrics.averageItemSize).toInt().coerceIn(0, metrics.itemCount - 1)
            val offset = (target - index * metrics.averageItemSize).roundToInt().coerceAtLeast(0)
            lazyListState.scrollToItem(index, offset)
        }
    }

    return this
        .drawWithContent {
            drawContent()
            if (alpha <= 0.01f) return@drawWithContent
            val metrics = lazyListState.scrollMetrics() ?: return@drawWithContent
            val geometry = thumbGeometry(
                viewport = metrics.viewport,
                contentLength = metrics.contentLength,
                progress = metrics.offset / (metrics.contentLength - metrics.viewport),
                minThumbHeightPx = minThumbHeight.toPx()
            ) ?: return@drawWithContent
            drawThumb(geometry, thumbWidth.toPx(), inset.toPx(), color, alpha)
        }
        .pointerInput(lazyListState, minThumbHeight, touchWidth) {
            val touchWidthPx = touchWidth.toPx()
            val minThumbPx = minThumbHeight.toPx()
            awaitPointerEventScope {
                while (true) {
                    val down = awaitRailDown(touchWidthPx) ?: continue
                    val metrics = lazyListState.scrollMetrics() ?: continue
                    val geometry = thumbGeometry(
                        viewport = metrics.viewport,
                        contentLength = metrics.contentLength,
                        progress = metrics.offset / (metrics.contentLength - metrics.viewport),
                        minThumbHeightPx = minThumbPx
                    ) ?: continue

                    down.consume()
                    dragging = true
                    var thumbTop = geometry.top
                    if (down.position.y < geometry.top || down.position.y > geometry.top + geometry.height) {
                        thumbTop = (down.position.y - geometry.height / 2f).coerceIn(0f, geometry.travel)
                        dragProgress.value = thumbTop / geometry.travel
                    }

                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            change?.consume()
                            break
                        }
                        thumbTop = (thumbTop + change.positionChange().y).coerceIn(0f, geometry.travel)
                        change.consume()
                        dragProgress.value = thumbTop / geometry.travel
                    }
                    dragProgress.value = null
                    dragging = false
                }
            }
        }
}

/** Barre latérale pilotée par le réglage de la page (voir [PageScrollPrefs]). */
@Composable
fun Modifier.pageScrollbar(page: String, scrollState: ScrollState): Modifier =
    geoTowerScrollbar(scrollState, enabled = PageScrollPrefs.isEnabled(PageScrollPrefs.Aid.BAR, page))

/** Barre latérale pilotée par le réglage de la page (voir [PageScrollPrefs]). */
@Composable
fun Modifier.pageScrollbar(page: String, lazyListState: LazyListState): Modifier =
    geoTowerScrollbar(lazyListState, enabled = PageScrollPrefs.isEnabled(PageScrollPrefs.Aid.BAR, page))

// ---------------------------------------------------------------------------------------------
// Géométrie et dessin, partagés par les deux variantes.
// ---------------------------------------------------------------------------------------------

private class ThumbGeometry(val top: Float, val height: Float, val travel: Float)

/** `null` quand la barre n'a pas lieu d'être (viewport nul, contenu non défilable). */
private fun thumbGeometry(
    viewport: Float,
    contentLength: Float,
    progress: Float,
    minThumbHeightPx: Float
): ThumbGeometry? {
    if (viewport <= 0f || contentLength <= viewport) return null
    val height = (viewport * viewport / contentLength)
        .coerceAtLeast(minThumbHeightPx.coerceAtMost(viewport))
        .coerceAtMost(viewport)
    val travel = viewport - height
    if (travel <= 0f) return null
    return ThumbGeometry(travel * progress.coerceIn(0f, 1f), height, travel)
}

private fun ContentDrawScope.drawThumb(
    geometry: ThumbGeometry,
    widthPx: Float,
    insetPx: Float,
    color: Color,
    alpha: Float
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - insetPx - widthPx, geometry.top),
        size = Size(widthPx, geometry.height),
        cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f),
        alpha = alpha
    )
}

/**
 * Attend un appui dans la zone tactile du bord droit, sur la passe [PointerEventPass.Initial]
 * pour passer AVANT le conteneur défilant (qui, lui, écoute la passe principale).
 * Retourne `null` pour tout autre événement : l'appelant reboucle sans rien consommer.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitRailDown(
    touchWidthPx: Float
): androidx.compose.ui.input.pointer.PointerInputChange? {
    val down = awaitPointerEvent(PointerEventPass.Initial)
        .changes.firstOrNull { it.changedToDownIgnoreConsumed() } ?: return null
    if (down.position.x < size.width - touchWidthPx) return null
    return down
}

private class LazyScrollMetrics(
    val viewport: Float,
    val contentLength: Float,
    val offset: Float,
    val averageItemSize: Float,
    val itemCount: Int
)

/**
 * Estimation de la position/hauteur totale d'une liste paresseuse à partir du pas moyen des
 * éléments visibles. `null` si la liste est vide ou tient entièrement dans le viewport.
 */
private fun LazyListState.scrollMetrics(): LazyScrollMetrics? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) return null
    val itemCount = info.totalItemsCount
    if (itemCount <= 0) return null

    // Pas moyen = taille de l'élément + espacement, mesuré sur les éléments réellement posés.
    val averageItemSize = if (visible.size > 1) {
        (visible.last().offset - visible.first().offset).toFloat() / (visible.size - 1)
    } else {
        visible.first().size.toFloat()
    }
    if (averageItemSize <= 0f) return null

    val contentLength = averageItemSize * itemCount
    if (contentLength <= viewport) return null
    val offset = (firstVisibleItemIndex * averageItemSize + firstVisibleItemScrollOffset)
        .coerceIn(0f, contentLength - viewport)
    return LazyScrollMetrics(viewport, contentLength, offset, averageItemSize, itemCount)
}
