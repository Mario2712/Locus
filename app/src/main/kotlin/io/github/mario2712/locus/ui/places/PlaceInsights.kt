package io.github.mario2712.locus.ui.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.stats.PlaceRecord
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Kennzahlen: Häufigkeit, Wochentage, Jahresvergleich, mit wem. */
@Composable
fun PlaceFiguresCard(record: PlaceRecord, editor: EditorData, today: LocalDate) {
    SectionCard(stringResource(R.string.place_figures)) {
        val parts = buildList {
            record.averageIntervalDays?.let { avg ->
                add(
                    if (avg < 1.05) stringResource(R.string.place_figures_daily)
                    else stringResource(R.string.place_figures_interval, String.format(Format.locale, "%.1f", avg)),
                )
            }
            add(stringResource(R.string.place_figures_last30, record.daysSince(today.minusDays(29))))
        }
        Text(parts.joinToString(" · "))

        // Wochentage als kleine Balken
        val byWeekday = record.byWeekday
        val max = (byWeekday.values.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            DayOfWeek.entries.forEach { day ->
                val count = byWeekday[day] ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.padding(vertical = 2.dp).width(18.dp).height((4 + 44f * count / max).dp)
                            .background(
                                if (count == max && count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Text(day.getDisplayName(TextStyle.SHORT, Format.locale).take(2), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Jahresvergleich
        val years = record.byYear
        if (years.size > 1) {
            Text(
                years.take(5).joinToString(" · ") { (year, count) -> "$year: $count" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Mit wem
        record.daysWith.entries.sortedByDescending { it.value }.forEach { (person, days) ->
            val name = editor.persons.firstOrNull { it.id == person }?.name ?: return@forEach
            Text(stringResource(R.string.place_figures_with, name, days, record.totalDays))
        }
    }
}

/** Übernachtungs-Aufenthalte statt Einzeltage. */
@Composable
fun PlaceStaysCard(record: PlaceRecord, onOpenDay: (LocalDate) -> Unit) {
    var all by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.place_stays)) {
        Text(
            pluralStringResource(R.plurals.place_stays_count, record.stayPeriods.size, record.stayPeriods.size) + " · " +
                pluralStringResource(R.plurals.place_nights, record.nights, record.nights),
        )
        val shown = if (all) record.stayPeriods else record.stayPeriods.take(LIMIT)
        shown.forEach { p ->
            val range = if (p.start.year == p.end.year) "${SHORT.format(p.start)}–${Format.fullDate(p.end)}"
            else "${Format.fullDate(p.start)}–${Format.fullDate(p.end)}"
            Text(
                "$range · " + pluralStringResource(R.plurals.place_nights, p.nights, p.nights),
                Modifier.fillMaxWidth().clickable { onOpenDay(p.start) }.padding(vertical = 4.dp),
            )
        }
        if (record.stayPeriods.size > LIMIT) {
            TextButton(onClick = { all = !all }) {
                Text(if (all) stringResource(R.string.place_show_less) else stringResource(R.string.place_show_all, record.stayPeriods.size))
            }
        }
    }
}

/**
 * Jahres-Raster: 12 Zeilen (Monate) × 31 Spalten (Tage). Besuchte Tage farbig; Tipp öffnet den Tag.
 * Mit ‹ › zwischen den Jahren wechseln.
 */
@Composable
fun PlaceHeatmap(record: PlaceRecord, onOpenDay: (LocalDate) -> Unit) {
    val years = remember(record.dates) { record.byYear.map { it.first } }
    if (years.isEmpty()) return
    var year by rememberSaveable(years.first()) { mutableStateOf(years.first()) }
    val dates = remember(record.dates) { record.dates.toHashSet() }
    val count = record.byYear.firstOrNull { it.first == year }?.second ?: 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = years.any { it < year }, onClick = { year = years.filter { it < year }.max() }) { Text("‹") }
        Text("$year · " + pluralStringResource(R.plurals.place_days, count, count), fontWeight = FontWeight.Medium)
        TextButton(enabled = years.any { it > year }, onClick = { year = years.filter { it > year }.min() }) { Text("›") }
    }

    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = 14.dp
        val cell = (maxWidth - labelWidth) / 31
        Row {
            Column(Modifier.width(labelWidth)) {
                (1..12).forEach { m ->
                    Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                        Text(
                            YearMonth.of(year, m).month.getDisplayName(TextStyle.NARROW, Format.locale),
                            fontSize = 9.sp, lineHeight = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Canvas(
                Modifier.width(cell * 31).height(cell * 12).pointerInput(year, dates) {
                    detectTapGestures { pos ->
                        val c = cell.toPx()
                        val day = (pos.x / c).toInt() + 1
                        val month = (pos.y / c).toInt() + 1
                        if (month !in 1..12) return@detectTapGestures
                        val ym = YearMonth.of(year, month)
                        if (day in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(day)
                            if (date in dates) onOpenDay(date)
                        }
                    }
                },
            ) {
                val c = cell.toPx()
                val gap = c * 0.14f
                for (m in 1..12) {
                    val ym = YearMonth.of(year, m)
                    for (d in 1..ym.lengthOfMonth()) {
                        drawRoundRect(
                            color = if (ym.atDay(d) in dates) on else off,
                            topLeft = Offset((d - 1) * c + gap / 2, (m - 1) * c + gap / 2),
                            size = Size(c - gap, c - gap),
                            cornerRadius = CornerRadius(c * 0.2f),
                        )
                    }
                }
            }
        }
    }
}

/** Alle Tage nach Jahr gruppiert; das neueste Jahr ist aufgeklappt, ältere lassen sich öffnen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceDaysByYear(record: PlaceRecord, onOpenDay: (LocalDate) -> Unit) {
    val byYear = remember(record.dates) { record.dates.groupBy { it.year } }
    val newest = byYear.keys.maxOrNull() ?: return
    var open by rememberSaveable(newest) { mutableStateOf(listOf(newest)) }
    byYear.forEach { (year, days) ->
        val expanded = year in open
        Row(
            Modifier.fillMaxWidth().clickable { open = if (expanded) open - year else open + year }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$year · " + pluralStringResource(R.plurals.place_days, days.size, days.size),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { date ->
                    Text(
                        DAY.format(date),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onOpenDay(date) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private const val LIMIT = 10
private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.", Format.locale)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM.", Format.locale)
