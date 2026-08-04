package fr.geotower.ui.screens.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex

/** Grossissement de l'élément saisi : assez pour qu'on le voie décollé, pas au point de masquer ses voisins. */
internal const val DRAG_SCALE = 1.06f
internal const val DRAG_ALPHA = 0.92f

/**
 * Déplacement des éléments de l'accueil par appui long, directement sur la page.
 *
 * L'ordre se réglait jusqu'ici depuis « Personnalisation des pages » uniquement, alors que le geste
 * que les gens tentent d'abord est l'appui long sur le bouton lui-même. Les deux chemins écrivent la
 * même préférence (`pages_order`, via `AppConfig.setPagesOrder`) et les emplacements possibles sont
 * les mêmes : ceux des éléments affichés, logo compris quand il est dans la liste.
 *
 * Chaque élément est rendu en deux couches, et c'est indispensable :
 *
 *  - l'**emplacement** (le Box extérieur) n'est jamais transformé. Il publie sa position par
 *    [onSlotPositioned] et porte la détection du geste : il sert donc à la fois de cible de dépôt et
 *    de repère pour convertir la position du doigt en coordonnées racine ;
 *  - le **rendu** (le Box intérieur) porte la translation. Mesurer la position sur le nœud
 *    justement translaté reviendrait à se mesurer soi-même : l'élément et le doigt se
 *    poursuivraient l'un l'autre.
 *
 * Le décalage n'est jamais cumulé : il est recalculé à chaque événement comme la distance entre le
 * doigt et son point de saisie. Une remise en page (l'élément change de rang en plein glissé) ne
 * peut donc pas introduire de dérive.
 */
@Stable
class HomeMenuReorderState internal constructor() {
    /** Éléments affichés, dans leur ordre courant. Réécrit à chaque composition et à chaque déplacement. */
    private var order: List<String> = emptyList()
    private var onReorder: (List<String>) -> Unit = {}
    private var haptic: HapticFeedback? = null

    /** Emplacements au repos, en coordonnées racine. */
    private val slots = mutableStateMapOf<String, Rect>()

    var draggedId: String? by mutableStateOf(null)
        private set

    /** Distance parcourue par le doigt depuis la saisie. */
    private var dragOffset: Offset by mutableStateOf(Offset.Zero)

    /** Emplacement et point de saisie d'origine : le rendu suit ce repère, décalé de [dragOffset]. */
    private var grabSlot: Rect = Rect.Zero
    private var grabPoint: Offset = Offset.Zero

    /**
     * Vrai entre un déplacement et la remise en page qui suit. Plusieurs événements tactiles peuvent
     * arriver dans la même image : sans cette garde, les suivants raisonneraient sur des
     * emplacements périmés et enchaîneraient un second déplacement non voulu.
     */
    private var awaitingRelayout = false

    internal fun update(
        order: List<String>,
        haptic: HapticFeedback,
        onReorder: (List<String>) -> Unit
    ) {
        this.order = order
        this.haptic = haptic
        this.onReorder = onReorder
    }

    fun isDragged(id: String): Boolean = draggedId == id

    internal fun onSlotPositioned(id: String, coordinates: LayoutCoordinates) {
        val rect = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
        if (slots[id] != rect) slots[id] = rect
        awaitingRelayout = false
    }

    /** Translation à appliquer au rendu de [id] pour qu'il reste collé au doigt. */
    fun dragTranslation(id: String): Offset {
        if (!isDragged(id)) return Offset.Zero
        val slot = slots[id] ?: return Offset.Zero
        return grabSlot.topLeft + dragOffset - slot.topLeft
    }

    internal fun startDrag(id: String, position: Offset): Boolean {
        if (order.size < 2) return false
        val slot = slots[id] ?: return false
        grabSlot = slot
        grabPoint = slot.topLeft + position
        dragOffset = Offset.Zero
        awaitingRelayout = false
        draggedId = id
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    internal fun onDrag(position: Offset) {
        val id = draggedId ?: return
        if (awaitingRelayout) return
        val slot = slots[id] ?: return

        // Position du doigt en coordonnées racine, relue à chaque fois sur l'emplacement courant :
        // c'est ce qui rend le calcul insensible aux changements de rang.
        dragOffset = slot.topLeft + position - grabPoint

        // Centre de l'élément tel qu'il est dessiné. La taille est celle de l'emplacement courant,
        // pas celle d'origine : en grille, le dernier élément d'une liste impaire est deux fois plus
        // large que les autres, et viser à côté ferait rater la cible juste après y être entré.
        val center = grabSlot.topLeft + dragOffset + Offset(slot.width / 2f, slot.height / 2f)
        val targetId = order.firstOrNull { it != id && slots[it]?.contains(center) == true } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return

        val reordered = order.toMutableList().apply { add(to, removeAt(from)) }
        // Tenu à jour tout de suite : la recomposition arrivera après, mais le geste continue.
        order = reordered
        awaitingRelayout = true
        // Une pichenette par emplacement franchi, pas la vibration de la saisie : le geste peut en
        // traverser cinq d'affilée.
        haptic?.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onReorder(reordered)
    }

    internal fun endDrag() {
        draggedId = null
        dragOffset = Offset.Zero
        awaitingRelayout = false
    }
}

@Composable
fun rememberHomeMenuReorderState(
    order: List<String>,
    onReorder: (List<String>) -> Unit
): HomeMenuReorderState {
    val haptic = LocalHapticFeedback.current
    return remember { HomeMenuReorderState() }.also { state ->
        state.update(order, haptic, onReorder)
    }
}

/**
 * Un emplacement de l'accueil : la couche qui mesure et qui écoute, autour de la couche qui bouge.
 *
 * Quand [enabled] est faux, on se contente d'une boîte transparente — aucun détecteur posé, donc
 * aucun risque de gêner un appui normal chez ceux qui ont coupé le réglage.
 */
@Composable
fun HomeMenuSlot(
    state: HomeMenuReorderState,
    id: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    Box(
        modifier = modifier
            .zIndex(if (state.isDragged(id)) 1f else 0f)
            .onGloballyPositioned { state.onSlotPositioned(id, it) }
            .then(state.dragModifier(id))
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val translation = state.dragTranslation(id)
                translationX = translation.x
                translationY = translation.y
                val lifted = state.isDragged(id)
                scaleX = if (lifted) DRAG_SCALE else 1f
                scaleY = if (lifted) DRAG_SCALE else 1f
                alpha = if (lifted) DRAG_ALPHA else 1f
            }
        ) {
            content()
        }
    }
}

@Composable
private fun HomeMenuReorderState.dragModifier(id: String): Modifier {
    val state = this
    // Le détecteur est mémorisé, et c'est important : un déplacement recompose la page, et une
    // lambda recréée à chaque passage peut faire repartir `pointerInput` de zéro — le geste en
    // cours s'arrêterait donc au premier élément franchi. Rien de périmé n'est capturé : tout ce
    // qui bouge (l'ordre, les emplacements) est relu dans l'état au moment du geste.
    val gesture: suspend PointerInputScope.() -> Unit = remember(state, id) {
        {
            detectLongPressDrag(
                onStart = { position -> state.startDrag(id, position) },
                onDrag = { position -> state.onDrag(position) },
                onFinish = { state.endDrag() }
            )
        }
    }
    return Modifier.pointerInput(key1 = id, block = gesture)
}

/**
 * Appui long puis glissé, détecté en passe [PointerEventPass.Initial].
 *
 * Les éléments de l'accueil sont des boutons : une détection classique n'en verrait jamais l'appui,
 * l'enfant cliquable le consommant avant nous. Rien n'est consommé tant que l'appui long n'a pas
 * abouti — le clic normal et le défilement de la page restent intacts ; une fois abouti, tout est
 * avalé pour que ni le bouton ni la page ne réagissent au reste du geste.
 *
 * Sert aux deux déplacements de l'accueil : les éléments du menu et le bouton « Aides ».
 */
internal suspend fun PointerInputScope.detectLongPressDrag(
    onStart: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onFinish: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var longPressed = false
        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                var pointer = down
                while (pointer.pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pointer = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeout
                    val travel = (pointer.position - down.position).getDistance()
                    if (travel > viewConfiguration.touchSlop) return@withTimeout
                }
            }
        } catch (_: PointerEventTimeoutCancellationException) {
            longPressed = true
        }
        if (!longPressed) return@awaitEachGesture
        if (!onStart(down.position)) return@awaitEachGesture

        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                onDrag(change.position)
            }
        } finally {
            onFinish()
        }
    }
}
