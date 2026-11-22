package io.github.mario2712.locus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Wie [androidx.compose.material3.AlertDialog], aber für Formulare mit Texteingabe: Das Fenster wird nicht bei
 * jeder Eingabe vom System neu eingepasst (sonst springt es mit offener Tastatur auf und ab), sondern rückt
 * selbst über die Tastatur. Antippen außerhalb schließt nicht – so gehen Eingaben nicht versehentlich verloren.
 */
@Composable
fun FormDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    text: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.weight(1f, fill = false)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}
