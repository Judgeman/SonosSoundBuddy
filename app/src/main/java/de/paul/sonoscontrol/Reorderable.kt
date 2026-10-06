package de.paul.sonoscontrol

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Verschieben per Drag & Drop in einer LazyColumn: Ein Eintrag wird an seinem Griff
 * gezogen, die anderen rücken nach. Erst beim Loslassen wird die neue Reihenfolge
 * gemeldet; bis die gespeicherte Reihenfolge zurückkommt, bleibt die gezogene stehen.
 *
 * Die Schlüssel müssen dieselben sein, die die LazyColumn für die Einträge nutzt.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onReorder: (List<Any>) -> Unit
) {
    /** Reihenfolge während und direkt nach dem Ziehen; null = wie die Quelle. */
    private var order by mutableStateOf<List<Any>?>(null)

    var draggedKey by mutableStateOf<Any?>(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset = 0
    private var startOrder: List<Any> = emptyList()

    // Zuletzt angezeigte Reihenfolge — kein State, nur für den Start des Ziehens
    private var shownKeys: List<Any> = emptyList()

    /** Die Einträge in der Reihenfolge, in der sie gerade zu sehen sein sollen. */
    fun <T> arrange(source: List<T>, keyOf: (T) -> Any): List<T> {
        val current = order
        val byKey = source.associateBy(keyOf)
        // Kam inzwischen etwas dazu oder ist weggefallen, gilt die Quelle
        val arranged = if (current == null || current.size != byKey.size || !current.all { it in byKey }) {
            source
        } else {
            current.map { byKey.getValue(it) }
        }
        shownKeys = arranged.map(keyOf)
        return arranged
    }

    internal fun onSourceChanged() {
        if (draggedKey == null) order = null
    }

    /** Wie weit der gezogene Eintrag gerade von seinem Platz in der Liste verschoben ist. */
    fun translationOf(key: Any): Float {
        if (key != draggedKey) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return initialOffset + draggedDistance - item.offset
    }

    internal fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        startOrder = shownKeys
        order = shownKeys
        draggedKey = key
        draggedDistance = 0f
        initialOffset = item.offset
    }

    internal fun drag(delta: Float) {
        val key = draggedKey ?: return
        val current = order ?: return
        draggedDistance += delta
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val top = initialOffset + draggedDistance
        val middle = top + dragged.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != key && it.key in current && middle >= it.offset && middle < it.offset + it.size
        }
        if (target != null) {
            // Die Liste hält ihre Position am ersten sichtbaren Eintrag fest — wandert der mit,
            // springt sie. Also die aktuelle Position ausdrücklich beibehalten.
            val firstIndex = listState.firstVisibleItemIndex
            if (dragged.index == firstIndex || target.index == firstIndex) {
                val firstOffset = listState.firstVisibleItemScrollOffset
                scope.launch { listState.scrollToItem(firstIndex, firstOffset) }
            }
            val from = current.indexOf(key)
            val to = current.indexOf(target.key)
            order = current.toMutableList().apply { add(to, removeAt(from)) }
        }
        // Am Rand der Liste weiterscrollen, damit auch weit entfernte Plätze erreichbar sind
        val bottom = top + dragged.size
        val overscroll = when {
            bottom > info.viewportEndOffset -> bottom - info.viewportEndOffset
            top < info.viewportStartOffset -> top - info.viewportStartOffset
            else -> 0f
        }
        if (overscroll != 0f) listState.dispatchRawDelta(overscroll)
    }

    internal fun end() {
        val result = order
        draggedKey = null
        draggedDistance = 0f
        if (result != null && result != startOrder) onReorder(result) else order = null
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    sourceKeys: List<Any>,
    onReorder: (List<Any>) -> Unit
): ReorderState {
    val scope = rememberCoroutineScope()
    val currentOnReorder by rememberUpdatedState(onReorder)
    val state = remember(listState) { ReorderState(listState, scope) { currentOnReorder(it) } }
    // Die gespeicherte Reihenfolge ist zurück (oder anders geändert) → wieder ihr folgen
    LaunchedEffect(sourceKeys) { state.onSourceChanged() }
    return state
}

/** Für den Eintrag selbst: Der gezogene liegt oben und folgt dem Finger. */
fun Modifier.draggedItem(state: ReorderState, key: Any): Modifier =
    zIndex(if (state.draggedKey == key) 1f else 0f)
        .graphicsLayer { translationY = state.translationOf(key) }

/** Griff zum Ziehen — sofort, ohne langes Drücken, damit die Liste daneben normal scrollt. */
@Composable
fun DragHandle(state: ReorderState, key: Any, description: String, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .pointerInput(state, key) {
                detectDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.start(key)
                    },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                    onDrag = { change, amount ->
                        change.consume()
                        state.drag(amount.y)
                    }
                )
            }
    ) {
        Icon(Icons.Rounded.DragIndicator, contentDescription = description)
    }
}

/**
 * Pfeile nach oben und unten: Tippen verschiebt um einen Platz,
 * langes Drücken ganz nach oben bzw. ganz nach unten.
 */
@Composable
fun MoveButtons(
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onMoveToEnd: (toTop: Boolean) -> Unit
) {
    MoveButton(
        up = true,
        description = "$name nach oben",
        longDescription = "$name ganz nach oben",
        enabled = canMoveUp,
        onClick = { onMove(-1) },
        onLongClick = { onMoveToEnd(true) }
    )
    MoveButton(
        up = false,
        description = "$name nach unten",
        longDescription = "$name ganz nach unten",
        enabled = canMoveDown,
        onClick = { onMove(1) },
        onLongClick = { onMoveToEnd(false) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoveButton(
    up: Boolean,
    description: String,
    longDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val color = LocalContentColor.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = longDescription,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick
            )
    ) {
        Icon(
            if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = description,
            tint = if (enabled) color else color.copy(alpha = 0.38f)
        )
    }
}
