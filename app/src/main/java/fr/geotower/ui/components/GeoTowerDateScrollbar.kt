package fr.geotower.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.geotower.ui.theme.LocalGeoTowerUiStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val DATE_SCROLLBAR_THUMB_HEIGHT = 48.dp

/**
 * Barre de défilement latérale des listes datées (historiques) : pouce ancré au bord droit, bulle
 * affichant le jour de l'élément visible, et saut direct dans la liste en tirant le pouce. Elle
 * apparaît pendant le défilement et s'efface toute seule après une courte inactivité.
 *
 * [timestamps] doit suivre exactement l'ordre des éléments de la liste (un horodatage par ligne).
 */
@Composable
fun GeoTowerDateScrollbar(
    listState: LazyListState,
    timestamps: List<Long>,
    modifier: Modifier = Modifier
) {
    if (timestamps.isEmpty()) return

    val sizing = LocalGeoTowerUiStyle.current.sizing
    val thumbHeight = sizing.component(DATE_SCROLLBAR_THUMB_HEIGHT)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Nombre de crans de défilement réellement disponibles : les derniers éléments sont déjà
    // visibles, on ne peut pas les amener en haut. Sert à la fois à placer le pouce et à viser
    // un élément en tirant dessus, pour que les deux restent dans le même repère (sinon le bas
    // de la piste ne fait rien et le pouce saute au relâchement).
    val scrollableIndexRange by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            (layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
        }
    }

    // Position du pouce alignée sur le défilement réel (progression inter-élément comprise).
    val listFraction by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                0f
            } else {
                val denominator = (layoutInfo.totalItemsCount - visibleItems.size).coerceAtLeast(1)
                val firstItem = visibleItems.first()
                val firstItemProgress = -firstItem.offset.toFloat() / firstItem.size.coerceAtLeast(1)
                ((firstItem.index + firstItemProgress) / denominator).coerceIn(0f, 1f)
            }
        }
    }

    val isScrollable = listState.canScrollForward || listState.canScrollBackward
    val isActive = isDragging || listState.isScrollInProgress
    var isBarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isActive, isScrollable) {
        if (isActive && isScrollable) {
            isBarVisible = true
        } else {
            delay(1400)
            isBarVisible = false
        }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (isBarVisible) 1f else 0f,
        label = "geoTowerDateScrollbarAlpha"
    )
    if (barAlpha < 0.01f) return

    val fraction = if (isDragging) dragFraction else listFraction
    val labelIndex = if (isDragging) {
        (dragFraction * scrollableIndexRange).roundToInt()
    } else {
        listState.firstVisibleItemIndex
    }.coerceIn(0, timestamps.lastIndex)
    val dayLabel = formatHistoryDay(timestamps[labelIndex])

    BoxWithConstraints(modifier = modifier.alpha(barAlpha)) {
        val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }
        val maxOffsetPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(0f)
        // Position mesurée (repère racine) de la piste et du pouce : le pouce se déplace
        // pendant qu'on le tire, ses coordonnées locales ne suffisent donc pas à suivre le doigt.
        var trackTopPx by remember { mutableFloatStateOf(0f) }
        var handleTopPx by remember { mutableFloatStateOf(0f) }

        fun fractionForPointer(y: Float): Float {
            if (maxOffsetPx <= 0f) return 0f
            return ((y - thumbHeightPx / 2f) / maxOffsetPx).coerceIn(0f, 1f)
        }

        fun scrollToFraction(value: Float) {
            dragFraction = value
            // Interpolation intra-élément : sans elle, une liste courte n'offre que quelques
            // crans utiles et le défilement semble bloqué.
            val exactIndex = value * scrollableIndexRange
            val targetIndex = exactIndex.toInt().coerceIn(0, timestamps.lastIndex)
            val itemSizePx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val withinItemPx = ((exactIndex - targetIndex).coerceIn(0f, 1f) * itemSizePx).roundToInt()
            listState.requestScrollToItem(targetIndex, withinItemPx)
        }

        // Zone de prise en main : toute la hauteur du bord droit.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(sizing.component(24.dp))
                .onGloballyPositioned { trackTopPx = it.positionInRoot().y }
                .pointerInput(timestamps.size, maxOffsetPx) {
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            scrollToFraction(fractionForPointer(startOffset.y))
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollToFraction(fractionForPointer(change.position.y))
                        }
                    )
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sizing.spacing(10.dp)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * maxOffsetPx).roundToInt()) }
                .height(thumbHeight)
                .onGloballyPositioned { handleTopPx = it.positionInRoot().y }
                // La bulle de date est une poignée au même titre que le pouce : un simple appui
                // active la barre (elle reste affichée) et le glissement fait défiler la liste.
                // On raisonne en position absolue dans la piste, jamais en delta local : le pouce
                // se replace sous le doigt à chaque cran, donc son déplacement annulerait celui
                // du doigt. [grabOffsetY] mémorise le point de saisie pour éviter tout saut.
                .pointerInput(timestamps.size, maxOffsetPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        dragFraction = listFraction
                        val grabOffsetY = down.position.y
                        if (maxOffsetPx > 0f) {
                            drag(down.id) { change ->
                                change.consume()
                                val trackY = handleTopPx - trackTopPx + change.position.y
                                scrollToFraction(((trackY - grabOffsetY) / maxOffsetPx).coerceIn(0f, 1f))
                            }
                        }
                        isDragging = false
                    }
                }
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = dayLabel,
                    style = sizing.textStyle(MaterialTheme.typography.labelLarge),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = sizing.spacing(12.dp), vertical = sizing.spacing(6.dp))
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = sizing.spacing(3.dp))
                    .width(sizing.component(6.dp))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

fun formatHistoryDay(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("-")
}

fun formatHistoryDateTime(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("-")
}

/** Poids approximatif libéré par une purge d'historique, en unités binaires (o/Ko/Mo/Go). */
fun formatHistoryStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 o"
    val units = listOf("o", "Ko", "Mo", "Go")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$bytes ${units[unitIndex]}"
    } else {
        val pattern = if (value >= 10.0) "%.0f %s" else "%.1f %s"
        String.format(Locale.getDefault(), pattern, value, units[unitIndex])
    }
}
