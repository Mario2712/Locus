package io.github.mario2712.locus.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import io.github.mario2712.locus.domain.usecase.CatalogOrder

/**
 * Spalte, deren Einträge sich am Griff ziehen und umsortieren lassen (für kurze Listen, ohne Lazy-Layout).
 * [row] bekommt den Modifier für den Griff; [onReorder] meldet die neue Reihenfolge nach dem Loslassen.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    row: @Composable (item: T, handle: Modifier) -> Unit,
) {
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)
    var working by remember(items) { mutableStateOf(items) }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val dragColor = MaterialTheme.colorScheme.surfaceVariant

    Column {
        working.forEach { item ->
            val k = key(item)
            key(k) {
                val isDragging = dragging == k
                val handle = Modifier.pointerInput(k) {
                    detectDragGestures(
                        onDragStart = { dragging = k; offset = 0f },
                        onDragEnd = {
                            dragging = null; offset = 0f
                            if (working.map(key) != currentItems.map(key)) currentOnReorder(working)
                        },
                        onDragCancel = { dragging = null; offset = 0f; working = currentItems },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val index = working.indexOfFirst { key(it) == k }
                        val next = working.getOrNull(index + 1)?.let { heights[key(it)] ?: 0 }
                        val prev = working.getOrNull(index - 1)?.let { heights[key(it)] ?: 0 }
                        if (next != null && offset > next / 2f) {
                            offset -= next
                            working = CatalogOrder.move(working, index, index + 1)
                        } else if (prev != null && offset < -prev / 2f) {
                            offset += prev
                            working = CatalogOrder.move(working, index, index - 1)
                        }
                    }
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[k] = it.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) offset else 0f }
                        .background(if (isDragging) dragColor else Color.Transparent),
                ) { row(item, handle) }
            }
        }
    }
}
