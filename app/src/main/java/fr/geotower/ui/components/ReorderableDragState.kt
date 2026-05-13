package fr.geotower.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class ReorderableDragState<T> internal constructor(
    items: List<T>,
    stepPx: Float,
    onOrderChange: (List<T>) -> Unit
) {
    private var items: List<T> = items
    private var stepPx: Float = stepPx
    private var onOrderChange: (List<T>) -> Unit = onOrderChange

    var draggedItem: T? by mutableStateOf<T?>(null)
        private set

    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    internal fun update(
        items: List<T>,
        stepPx: Float,
        onOrderChange: (List<T>) -> Unit
    ) {
        this.items = items
        this.stepPx = stepPx
        this.onOrderChange = onOrderChange
    }

    fun isDragged(item: T): Boolean = draggedItem == item

    fun offsetFor(item: T): Float = if (isDragged(item)) dragOffset else 0f

    fun dragModifier(item: T): Modifier = Modifier.pointerInput(item) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                draggedItem = item
                dragOffset = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffset += dragAmount.y

                val currentItems = items
                val currentIndex = currentItems.indexOf(item)
                if (currentIndex < 0 || stepPx <= 0f) return@detectDragGesturesAfterLongPress

                var newIndex = currentIndex
                while (dragOffset > stepPx * 0.5f && newIndex < currentItems.size - 1) {
                    dragOffset -= stepPx
                    newIndex++
                }
                while (dragOffset < -stepPx * 0.5f && newIndex > 0) {
                    dragOffset += stepPx
                    newIndex--
                }

                if (newIndex != currentIndex) {
                    val newItems = currentItems.toMutableList()
                    val movedItem = newItems.removeAt(currentIndex)
                    newItems.add(newIndex, movedItem)
                    onOrderChange(newItems)
                }
            },
            onDragEnd = { clearDrag() },
            onDragCancel = { clearDrag() }
        )
    }

    private fun clearDrag() {
        draggedItem = null
        dragOffset = 0f
    }
}

@Composable
fun <T> rememberReorderableDragState(
    items: List<T>,
    itemHeight: Dp,
    itemSpacing: Dp = 0.dp,
    onOrderChange: (List<T>) -> Unit
): ReorderableDragState<T> {
    val stepPx = with(LocalDensity.current) { (itemHeight + itemSpacing).toPx() }
    return remember {
        ReorderableDragState(items, stepPx, onOrderChange)
    }.also { state ->
        state.update(items, stepPx, onOrderChange)
    }
}
