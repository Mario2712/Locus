package io.github.mario2712.locus.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.MonthPickerDialog
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.common.themedColor
import io.github.mario2712.locus.ui.common.showMessage
import io.github.mario2712.locus.ui.common.text
import io.github.mario2712.locus.ui.editor.FillRangeDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    onOpenDay: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showFill by remember { mutableStateOf(false) }
    var pickMonth by remember { mutableStateOf(false) }
    val currentMonth = YearMonth.from(viewModel.today)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showMessage(it, context, viewModel::undo) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.calendar_previous))
                }
                Text(
                    Format.month(state.month.atDay(1)) + " ▾",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).clickable { pickMonth = true }.padding(vertical = 8.dp),
                )
                if (state.month != currentMonth) {
                    TextButton(onClick = { viewModel.setMonth(currentMonth) }) { Text(stringResource(R.string.calendar_today)) }
                }
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.calendar_next))
                }
            }

            // Wischen: nach links = nächster Monat, nach rechts = vorheriger Monat
            var dragTotal by remember { mutableStateOf(0f) }
            Box(
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { change, amount -> dragTotal += amount; change.consume() },
                        onDragEnd = {
                            val threshold = 80.dp.toPx()
                            if (dragTotal < -threshold) viewModel.nextMonth() else if (dragTotal > threshold) viewModel.previousMonth()
                            dragTotal = 0f
                        },
                    )
                },
            ) {
                MonthGrid(
                    days = state.days,
                    selection = state.selection,
                    onTap = { date -> if (!viewModel.extendSelection(date)) onOpenDay(date) },
                    onLongPress = viewModel::startSelection,
                )
            }

            val selection = state.selection
            if (selection != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                R.string.calendar_selection,
                                Format.shortDate(selection.start),
                                Format.shortDate(selection.endInclusive),
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showFill = true }) { Text(stringResource(R.string.calendar_fill)) }
                            OutlinedButton(onClick = viewModel::clearSelection) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.calendar_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.legend.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.legend.forEach { (name, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TypeDot(color, 10.dp)
                            Text(name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (pickMonth) {
        MonthPickerDialog(
            initial = state.month,
            latest = currentMonth.plusYears(1),
            onDismiss = { pickMonth = false },
            onSelect = { viewModel.setMonth(it); pickMonth = false },
        )
    }
    val selection = state.selection
    if (showFill && selection != null) {
        FillRangeDialog(
            from = selection.start,
            to = selection.endInclusive,
            editor = state.editor,
            onDismiss = { showFill = false },
            onConfirm = { viewModel.fill(selection, it); showFill = false },
            onCreatePlace = viewModel::createPlace,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    selection: ClosedRange<LocalDate>?,
    onTap: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
) {
    if (days.isEmpty()) return
    val leading = days.first().date.dayOfWeek.value - 1 // Montag = 0
    val cells: List<CalendarDay?> = List(leading) { null } + days
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, io.github.mario2712.locus.ui.common.Format.locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            day = day,
                            selected = selection?.contains(day.date) == true,
                            modifier = Modifier.weight(1f).combinedClickable(
                                onClick = { onTap(day.date) },
                                onLongClick = { onLongPress(day.date) },
                            ),
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val main = day.mainColor?.let { themedColor(it) }
    val background = main?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.surfaceVariant
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.onSurface
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(background, shape)
            .border(if (selected || day.isToday) 2.dp else 0.dp, borderColor, shape)
            .padding(4.dp),
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.holiday != null || day.date.dayOfWeek == DayOfWeek.SUNDAY) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            day.extraColors.take(3).forEach { TypeDot(it, 6.dp) }
        }
        if (day.flags.isNotEmpty()) {
            Text(
                day.flags.take(2).joinToString("") + if (day.flags.size > 2) "…" else "",
                modifier = Modifier.align(Alignment.TopEnd),
                fontSize = 10.sp,
                lineHeight = 10.sp,
            )
        }
        if (day.isGap) {
            Box(
                Modifier.align(if (day.flags.isEmpty()) Alignment.TopEnd else Alignment.BottomStart).size(6.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
    }
}
