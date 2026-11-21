package io.github.mario2712.locus.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.stats.YearStats
import io.github.mario2712.locus.domain.stats.YearStatsData
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.common.MonthPickerDialog
import io.github.mario2712.locus.ui.common.themedColor
import io.github.mario2712.locus.ui.editor.DatePickerDialogFor
import io.github.mario2712.locus.ui.places.BackScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Gewählter Zeitraum der Statistik. */
sealed interface StatsPeriod {
    data class Year(val year: Int) : StatsPeriod
    data class Month(val month: YearMonth) : StatsPeriod
    data object Last30 : StatsPeriod
    data class Custom(val from: LocalDate, val to: LocalDate) : StatsPeriod
}

data class StatsUiState(
    val period: StatsPeriod,
    val years: List<Int> = emptyList(),
    val stats: YearStatsData? = null,
    val editor: EditorData = EditorData(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    clock: Clock,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Year(today.year))

    val state: StateFlow<StatsUiState> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data, period) { s, v, editor, p ->
        val years = ((s.map { it.startDate.year } + v.map { it.date.year }) + today.year).distinct().sortedDescending()
        val (from, to) = when (p) {
            is StatsPeriod.Year -> LocalDate.of(p.year, 1, 1) to LocalDate.of(p.year, 12, 31)
            is StatsPeriod.Month -> p.month.atDay(1) to p.month.atEndOfMonth()
            StatsPeriod.Last30 -> today.minusDays(29) to today
            is StatsPeriod.Custom -> p.from to p.to
        }
        val stats = YearStats.compute(
            from, to, Defaults.ME, s, v, editor.placesById, editor.typesById, editor.categoriesById, editor.rank, editor.homebases, today,
        )
        StatsUiState(p, years, stats, editor)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), StatsUiState(StatsPeriod.Year(today.year)))

    fun select(value: StatsPeriod) {
        period.value = value
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = state.editor
    BackScaffold(stringResource(R.string.stats_title), onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodSelector(state, viewModel)
            val stats = state.stats ?: return@Column

            // Überblick als Kennzahlen (keine Grafik nötig)
            SectionCard(periodLabel(state.period)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stats.recordedDays.toString(), stringResource(R.string.stats_recorded_days))
                    StatTile(stats.countries.size.toString(), stringResource(R.string.stats_countries))
                    StatTile(stats.newCountries.size.toString(), stringResource(R.string.stats_new_countries))
                    StatTile(stats.newCities.size.toString(), stringResource(R.string.stats_new_cities))
                    StatTile(stats.trips.toString(), stringResource(R.string.stats_trips))
                    StatTile(stats.nightsAway.toString(), stringResource(R.string.stats_nights_away))
                }
                if (stats.countries.isNotEmpty()) {
                    Text(stats.countries.mapNotNull { ReferenceData.country(it)?.flag }.joinToString(" "), style = MaterialTheme.typography.titleLarge)
                }
                if (stats.newCountries.isNotEmpty()) {
                    Text(
                        stringResource(R.string.stats_new_countries_list, stats.newCountries.mapNotNull { ReferenceData.country(it) }.joinToString(", ") { "${it.flag} ${it.nameDe}" }),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Tage nach Hauptart: ein Balken je Art, Wert als Text daneben
            if (stats.daysByMainType.isNotEmpty()) {
                SectionCard(stringResource(R.string.stats_days_by_type)) {
                    val max = stats.daysByMainType.values.max().toFloat()
                    stats.daysByMainType.entries.sortedByDescending { it.value }.forEach { (typeId, days) ->
                        BarRow(editor.typeName(typeId), days, max, editor.typeColor(typeId))
                    }
                    Text(stringResource(R.string.stats_days_by_type_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Arbeitsorte je Monat als Tabelle
            val workMonths = stats.work.filter { it.homeofficeOnly + it.officeDays + it.businessTripDays > 0 }
            if (workMonths.isNotEmpty()) {
                SectionCard(stringResource(R.string.stats_work)) {
                    WorkRow(stringResource(R.string.stats_month), stringResource(R.string.stats_ho_only), stringResource(R.string.stats_office),
                        stringResource(R.string.stats_hybrid), stringResource(R.string.stats_business), header = true)
                    workMonths.forEach { m ->
                        WorkRow(
                            m.month.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                            m.homeofficeOnly.toString(), m.officeDays.toString(), m.hybridDays.toString(), m.businessTripDays.toString(),
                        )
                    }
                    WorkRow(
                        stringResource(R.string.stats_total),
                        stats.work.sumOf { it.homeofficeOnly }.toString(), stats.work.sumOf { it.officeDays }.toString(),
                        stats.work.sumOf { it.hybridDays }.toString(), stats.work.sumOf { it.businessTripDays }.toString(), header = true,
                    )
                    Text(stringResource(R.string.stats_work_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard(stringResource(R.string.stats_vacation)) {
                Text(stringResource(R.string.stats_vacation_split, stats.vacationDaysDomestic + stats.vacationDaysAbroad, stats.vacationDaysDomestic, stats.vacationDaysAbroad))
            }

            if (stats.topCities.isNotEmpty()) {
                SectionCard(stringResource(R.string.stats_top_cities)) {
                    stats.topCities.forEachIndexed { index, (placeId, days) ->
                        Text("${index + 1}. ${editor.placeName(placeId)} – ${stringResource(R.string.places_days, days)}")
                    }
                }
            }

            SectionCard(stringResource(R.string.stats_heatmap)) {
                Heatmap(stats, editor, onOpenDay)
            }
        }
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = when (period) {
    is StatsPeriod.Year -> stringResource(R.string.stats_period_year, period.year)
    is StatsPeriod.Month -> Format.month(period.month.atDay(1))
    StatsPeriod.Last30 -> stringResource(R.string.stats_period_last30)
    is StatsPeriod.Custom -> "${Format.fullDate(period.from)} – ${Format.fullDate(period.to)}"
}

/** Jahr ▾ · Monat ▾ · Letzte 30 Tage · Zeitraum … */
@Composable
private fun PeriodSelector(state: StatsUiState, viewModel: StatsViewModel) {
    var yearMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    val p = state.period
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilterChip(p is StatsPeriod.Year, { yearMenu = true }, label = {
                Text((p as? StatsPeriod.Year)?.year?.toString() ?: stringResource(R.string.stats_choose_year))
            })
            DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                state.years.forEach { y ->
                    DropdownMenuItem(text = { Text(y.toString()) }, onClick = { viewModel.select(StatsPeriod.Year(y)); yearMenu = false })
                }
            }
        }
        if (p is StatsPeriod.Month) {
            // Gewählter Monat: direkt vor/zurück blättern, Antippen öffnet den Monatswähler.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.select(StatsPeriod.Month(p.month.minusMonths(1))) }) { Text("‹") }
                FilterChip(true, { monthMenu = true }, label = { Text(Format.month(p.month.atDay(1))) })
                TextButton(
                    enabled = p.month < YearMonth.from(viewModel.today),
                    onClick = { viewModel.select(StatsPeriod.Month(p.month.plusMonths(1))) },
                ) { Text("›") }
            }
        } else {
            FilterChip(false, { monthMenu = true }, label = { Text(stringResource(R.string.stats_choose_month)) })
        }
        FilterChip(p == StatsPeriod.Last30, { viewModel.select(StatsPeriod.Last30) }, label = { Text(stringResource(R.string.stats_period_last30)) })
        FilterChip(p is StatsPeriod.Custom, { pickFrom = true }, label = { Text(stringResource(R.string.stats_period_custom)) })
    }
    if (monthMenu) {
        MonthPickerDialog(
            initial = (p as? StatsPeriod.Month)?.month ?: YearMonth.from(viewModel.today),
            latest = YearMonth.from(viewModel.today),
            onDismiss = { monthMenu = false },
            onSelect = { viewModel.select(StatsPeriod.Month(it)); monthMenu = false },
        )
    }
    if (pickFrom) {
        DatePickerDialogFor(viewModel.today.minusMonths(1), { pickFrom = false }) { from -> customFrom = from; pickFrom = false }
    }
    customFrom?.let { from ->
        DatePickerDialogFor(viewModel.today, { customFrom = null }) { to ->
            viewModel.select(StatsPeriod.Custom(minOf(from, to), maxOf(from, to))); customFrom = null
        }
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarRow(label: String, value: Int, max: Float, colorArgb: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).height(12.dp)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(value / max)
                    .background(themedColor(colorArgb), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
            )
        }
        Text(value.toString(), Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WorkRow(c1: String, c2: String, c3: String, c4: String, c5: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth()) {
        Text(c1, Modifier.weight(1.2f), style = style)
        listOf(c2, c3, c4, c5).forEach { Text(it, Modifier.weight(1f), style = style) }
    }
}

/** Jahr als Raster: eine Zeile je Monat, ein Feld je Tag, gefärbt nach Hauptart. */
@Composable
private fun Heatmap(stats: YearStatsData, editor: EditorData, onOpenDay: (LocalDate) -> Unit) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        stats.months.forEach { month ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(month.month.getDisplayName(TextStyle.NARROW, Locale.GERMAN), Modifier.width(14.dp), style = MaterialTheme.typography.labelSmall)
                (1..month.lengthOfMonth()).forEach { day ->
                    val date = month.atDay(day)
                    val type = stats.mainTypeByDate[date]
                    val label = Format.mediumDate(date) + (type?.let { ": " + editor.typeName(it) } ?: "")
                    Box(
                        Modifier.size(8.dp)
                            .background(type?.let { themedColor(editor.typeColor(it)) } ?: empty, RoundedCornerShape(2.dp))
                            .semantics { contentDescription = label }
                            .clickable { onOpenDay(date) },
                    )
                }
            }
        }
        val present = stats.mainTypeByDate.values.distinct().mapNotNull { editor.typesById[it] }.sortedBy { it.sortOrder }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            present.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeDot(type.colorArgb, 10.dp)
                    Text(type.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
