package io.github.mario2712.locus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.R
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Kompakter Monatswähler: Jahr mit ‹ › blättern, darunter 12 Monate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthPickerDialog(initial: YearMonth, latest: YearMonth, onDismiss: () -> Unit, onSelect: (YearMonth) -> Unit) {
    var year by remember { mutableStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { year-- }) { Text("‹") }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = year < latest.year, onClick = { year++ }) { Text("›") }
            }
        },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 3) {
                (1..12).forEach { m ->
                    val month = YearMonth.of(year, m)
                    FilterChip(
                        selected = month == initial,
                        enabled = !month.isAfter(latest),
                        onClick = { onSelect(month) },
                        label = { Text(month.month.getDisplayName(TextStyle.SHORT, io.github.mario2712.locus.ui.common.Format.locale)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

