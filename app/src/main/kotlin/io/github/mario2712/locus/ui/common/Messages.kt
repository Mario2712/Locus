package io.github.mario2712.locus.ui.common

import android.content.Context
import io.github.mario2712.locus.R

fun UiMessage.text(context: Context): String = when (this) {
    is UiMessage.Conflict -> context.getString(R.string.msg_conflict, Format.mediumDate(date))
    is UiMessage.Filled -> context.resources.getQuantityString(R.plurals.msg_filled, count, count)
    UiMessage.Saved -> context.getString(R.string.msg_saved)
    UiMessage.Deleted -> context.getString(R.string.msg_deleted)
    UiMessage.DayNotEmpty -> context.getString(R.string.msg_day_not_empty)
    UiMessage.Undone -> context.getString(R.string.msg_undone)
    is UiMessage.Undoable -> inner.text(context) + (debugTiming?.let { " · $it" } ?: "")
}

/** Zeigt die Meldung; bei rückgängig machbaren Änderungen mit Knopf „Rückgängig“. */
suspend fun androidx.compose.material3.SnackbarHostState.showMessage(message: UiMessage, context: Context, onUndo: () -> Unit) {
    if (message is UiMessage.Undoable) {
        val result = showSnackbar(
            message.text(context), actionLabel = context.getString(R.string.action_undo),
            duration = androidx.compose.material3.SnackbarDuration.Long,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onUndo()
    } else {
        showSnackbar(message.text(context))
    }
}
