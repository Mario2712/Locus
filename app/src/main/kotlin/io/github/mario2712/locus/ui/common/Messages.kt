package io.github.mario2712.locus.ui.common

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R

fun UiMessage.text(context: Context): String = when (this) {
    is UiMessage.Conflict -> context.getString(R.string.msg_conflict, Format.mediumDate(date))
    is UiMessage.Filled -> context.resources.getQuantityString(R.plurals.msg_filled, count, count)
    UiMessage.Saved -> context.getString(R.string.msg_saved)
    UiMessage.Deleted -> context.getString(R.string.msg_deleted)
    UiMessage.DayNotEmpty -> context.getString(R.string.msg_day_not_empty)
    UiMessage.Undone -> context.getString(R.string.msg_undone)
    is UiMessage.Undoable -> inner.text(context)
}

/** Zeigt die Meldung; bei rückgängig machbaren Änderungen mit Knopf „Rückgängig“. */
suspend fun androidx.compose.material3.SnackbarHostState.showMessage(message: UiMessage, context: Context, onUndo: () -> Unit) {
    currentSnackbarData?.dismiss() // neue Meldung ersetzt die alte sofort
    if (message is UiMessage.Undoable) {
        val result = showSnackbar(
            message.text(context), actionLabel = context.getString(R.string.action_undo),
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onUndo()
    } else {
        showSnackbar(message.text(context))
    }
}

/**
 * Meldungen oben über der Titelleiste statt unten über den Knöpfen: so bleiben „Andere Station …“,
 * „Reise starten …“ usw. nach dem Speichern frei. Kompakt, verschwindet nach wenigen Sekunden
 * und lässt sich seitlich wegwischen.
 */
@androidx.compose.runtime.Composable
fun TopMessageHost(state: androidx.compose.material3.SnackbarHostState, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.SnackbarHost(
        state,
        modifier.statusBarsPadding().padding(horizontal = 12.dp),
    ) { data ->
        val swipe = androidx.compose.material3.rememberSwipeToDismissBoxState()
        androidx.compose.runtime.LaunchedEffect(swipe.currentValue) {
            if (swipe.currentValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) data.dismiss()
        }
        androidx.compose.material3.SwipeToDismissBox(swipe, backgroundContent = {}) {
            androidx.compose.material3.Snackbar(data)
        }
    }
}
