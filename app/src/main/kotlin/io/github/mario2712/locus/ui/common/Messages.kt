package io.github.mario2712.locus.ui.common

import android.content.Context
import io.github.mario2712.locus.R

fun UiMessage.text(context: Context): String = when (this) {
    is UiMessage.Conflict -> context.getString(R.string.msg_conflict, Format.mediumDate(date))
    is UiMessage.Filled -> context.resources.getQuantityString(R.plurals.msg_filled, count, count)
    UiMessage.Saved -> context.getString(R.string.msg_saved)
    UiMessage.Deleted -> context.getString(R.string.msg_deleted)
}
