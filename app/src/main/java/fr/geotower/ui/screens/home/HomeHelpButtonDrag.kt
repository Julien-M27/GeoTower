package fr.geotower.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import fr.geotower.R
import fr.geotower.ui.theme.GeoTowerUiSizing
import fr.geotower.ui.theme.LocalGeoTowerUiStyle

/** Les quatre coins possibles du bouton « Aides », tels qu'ils sont enregistrés. */
private const val TOP_START = "top_start"
private const val TOP_END = "top_end"
private const val BOTTOM_START = "bottom_start"
private const val BOTTOM_END = "bottom_end"

internal val homeHelpCorners = listOf(TOP_START, TOP_END, BOTTOM_START, BOTTOM_END)

/**
 * Déplacement du bouton « Aides » par appui long, entre ses quatre coins.
 *
 * Il ne se déplace pas comme les éléments du menu, et pour une raison de fond : il n'a que quatre
 * emplacements, et changer son alignement en plein geste le repose ailleurs dans la page —
 * autrement dit le détruit et le recrée, emportant le glissé avec lui.
 *
 * Il reste donc à sa place dans la mise en page, n'est déplacé qu'à l'écran, et le réglage n'est
 * écrit qu'au relâchement. Les quatre cibles apparaissent pendant le geste ([HomeHelpCornerTargets])
 * et celle qui prendra le bouton s'allume : le coin retenu est celui du quart de page où se trouve
 * le doigt, pas une pastille à viser.
 */
@Stable
class HomeHelpDragState internal constructor() {
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /** Coin qui prendra le bouton au relâchement ; nul si le doigt est sorti de la page. */
    var hoveredCorner: String? by mutableStateOf(null)
        private set

    private var dragOffset: Offset by mutableStateOf(Offset.Zero)

    /** Zone d'accueil et emplacement du bouton, en coordonnées racine. */
    private var contentBounds: Rect = Rect.Zero
    private var slot: Rect = Rect.Zero
    private var grabPoint: Offset = Offset.Zero

    private var isRtl: Boolean = false
    private var haptic: HapticFeedback? = null
    private var onDrop: (String) -> Unit = {}

    internal fun update(isRtl: Boolean, haptic: HapticFeedback, onDrop: (String) -> Unit) {
        this.isRtl = isRtl
        this.haptic = haptic
        this.onDrop = onDrop
    }

    internal fun onContentPositioned(coordinates: LayoutCoordinates) {
        contentBounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
    }

    internal fun onSlotPositioned(coordinates: LayoutCoordinates) {
        slot = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
    }

    /** Décalage à appliquer au rendu du bouton pour qu'il suive le doigt. */
    fun dragTranslation(): Offset = if (isDragging) dragOffset else Offset.Zero

    internal fun startDrag(position: Offset): Boolean {
        if (contentBounds.isEmpty) return false
        grabPoint = position
        dragOffset = Offset.Zero
        hoveredCorner = cornerFor(slot.topLeft + position)
        isDragging = true
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    internal fun onDrag(position: Offset) {
        if (!isDragging) return
        // L'emplacement du bouton, lui, ne bouge pas : le réglage n'est écrit qu'au relâchement.
        dragOffset = position - grabPoint

        val corner = cornerFor(slot.topLeft + position)
        if (corner != hoveredCorner) {
            hoveredCorner = corner
            if (corner != null) haptic?.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    internal fun endDrag() {
        val corner = hoveredCorner
        isDragging = false
        hoveredCorner = null
        dragOffset = Offset.Zero
        if (corner != null) onDrop(corner)
    }

    private fun cornerFor(point: Offset): String? {
        if (contentBounds.isEmpty || !contentBounds.contains(point)) return null
        val onLeft = point.x < contentBounds.center.x
        val onTop = point.y < contentBounds.center.y
        val atStart = if (isRtl) !onLeft else onLeft
        return when {
            onTop && atStart -> TOP_START
            onTop -> TOP_END
            atStart -> BOTTOM_START
            else -> BOTTOM_END
        }
    }
}

@Composable
fun rememberHomeHelpDragState(onDrop: (String) -> Unit): HomeHelpDragState {
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember { HomeHelpDragState() }.also { state ->
        state.update(isRtl, haptic, onDrop)
    }
}

/**
 * Le bouton « Aides », déplaçable ou non.
 *
 * Comme pour les éléments du menu, la couche qui mesure et qui écoute n'est pas celle qui bouge :
 * mesurer le bouton déjà translaté reviendrait à le poursuivre.
 */
@Composable
fun HomeHelpFab(
    state: HomeHelpDragState?,
    draggable: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.appstrings_home_help_settings)

    if (state == null || !draggable) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            modifier = modifier
        ) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = label)
        }
        return
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { state.onSlotPositioned(it) }
            .then(state.dragModifier())
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            modifier = Modifier.graphicsLayer {
                val translation = state.dragTranslation()
                translationX = translation.x
                translationY = translation.y
                val lifted = state.isDragging
                scaleX = if (lifted) DRAG_SCALE else 1f
                scaleY = if (lifted) DRAG_SCALE else 1f
                alpha = if (lifted) DRAG_ALPHA else 1f
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = label)
        }
    }
}

@Composable
private fun HomeHelpDragState.dragModifier(): Modifier {
    val state = this
    // Mémorisé pour les mêmes raisons que le menu : une lambda recréée peut faire repartir
    // `pointerInput` en plein geste. Rien de périmé n'est capturé, tout est relu dans l'état.
    val gesture: suspend PointerInputScope.() -> Unit = remember(state) {
        {
            detectLongPressDrag(
                onStart = { position -> state.startDrag(position) },
                onDrag = { position -> state.onDrag(position) },
                onFinish = { state.endDrag() }
            )
        }
    }
    return Modifier.pointerInput(key1 = state, block = gesture)
}

/**
 * Les quatre coins où le bouton peut atterrir, montrés pendant le geste.
 *
 * À poser en premier dans la boîte de la page : les pastilles se dessinent alors sous tout le
 * reste, donc sous le bouton qu'on est en train de déplacer.
 */
@Composable
fun BoxScope.HomeHelpCornerTargets(state: HomeHelpDragState) {
    val sizing = LocalGeoTowerUiStyle.current.sizing
    val visibility by animateFloatAsState(if (state.isDragging) 1f else 0f, label = "helpTargets")
    if (visibility <= 0.01f) return

    val restingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val activeContainer = MaterialTheme.colorScheme.primaryContainer

    homeHelpCorners.forEach { corner ->
        val hovered = state.hoveredCorner == corner
        val scale by animateFloatAsState(if (hovered) 1.15f else 1f, label = "helpTarget_$corner")

        Box(
            modifier = Modifier
                .align(homeHelpAlignment(corner))
                .padding(homeHelpCornerPadding(corner, sizing))
                .graphicsLayer {
                    alpha = visibility
                    scaleX = scale
                    scaleY = scale
                }
                .size(sizing.component(56.dp))
                .background(
                    color = if (hovered) activeContainer else restingColor.copy(alpha = 0.10f),
                    shape = CircleShape
                )
                .border(
                    width = if (hovered) 2.dp else 1.dp,
                    color = if (hovered) activeColor else restingColor.copy(alpha = 0.35f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                tint = if (hovered) activeColor else restingColor.copy(alpha = 0.45f),
                modifier = Modifier.size(sizing.component(24.dp))
            )
        }
    }
}

internal fun homeHelpAlignment(corner: String): Alignment = when (corner) {
    TOP_START -> Alignment.TopStart
    TOP_END -> Alignment.TopEnd
    BOTTOM_START -> Alignment.BottomStart
    else -> Alignment.BottomEnd
}

/**
 * Écart au bord, partagé par le vrai bouton et par les cibles du glissé — en haut à droite il
 * descend sous le bouton « Quitter », qui occupe déjà le coin.
 *
 * Pas de `navigationBarsPadding()` en bas : l'accueil est déjà décalé de la barre système par le
 * `innerPadding` du Scaffold racine, l'ajouter ici le compterait deux fois.
 */
internal fun homeHelpCornerPadding(corner: String, sizing: GeoTowerUiSizing): PaddingValues = when {
    corner.startsWith("bottom") -> PaddingValues(
        start = sizing.spacing(20.dp),
        top = sizing.spacing(20.dp),
        end = sizing.spacing(20.dp),
        bottom = sizing.spacing(32.dp)
    )
    corner == TOP_END -> PaddingValues(
        start = sizing.spacing(20.dp),
        top = sizing.spacing(72.dp),
        end = sizing.spacing(20.dp),
        bottom = sizing.spacing(20.dp)
    )
    else -> PaddingValues(sizing.spacing(20.dp))
}
