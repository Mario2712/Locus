package io.github.mario2712.locus.ui.common

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.mario2712.locus.R

/** Öffnet die Orts-Akte eines Orts (von der Navigation bereitgestellt; `null` z. B. in Vorschauen). */
val LocalOpenPlace = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Link „Orts-Akte öffnen“ in Bearbeiten-Dialogen; schließt den Dialog und wechselt zur Ortsseite. */
@Composable
fun OpenPlaceRecordButton(placeId: String?, onDismiss: () -> Unit) {
    val open = LocalOpenPlace.current ?: return
    if (placeId == null) return
    TextButton(onClick = { onDismiss(); open(placeId) }) { Text(stringResource(R.string.editor_open_place_record)) }
}
