package io.github.mario2712.locus.ui.stats

import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import io.github.mario2712.locus.domain.stats.WorkFilter
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

    /** Gesamter Zeitraum ab dem ersten Eintrag. */
    data object All : StatsPeriod
    data class Custom(val from: LocalDate, val to: LocalDate) : StatsPeriod
}

/** Jahrestage für den Reiter „Besonderes“. */
data class SpecialStats(
    val birthdays: List<Pair<String, List<io.github.mario2712.locus.domain.stats.DayWhere>>> = emptyList(),
    val newYearsEve: List<io.github.mario2712.locus.domain.stats.DayWhere> = emptyList(),
    val christmas: List<io.github.mario2712.locus.domain.stats.DayWhere> = emptyList(),
    val firstVisitAges: List<io.github.mario2712.locus.domain.stats.FirstVisitAge> = emptyList(),
    val hasBirthDate: Boolean = false,
)

/** Wessen Statistik: ich, die zweite Person oder nur gemeinsame Einträge. */
enum class StatsPerson { ME, PARTNER, TOGETHER }

data class StatsUiState(
    val period: StatsPeriod,
    val person: StatsPerson = StatsPerson.ME,
    /** Gleich langer Zeitraum davor (Vorjahr bis zum selben Tag, Vormonat …) für ▲/▼. */
    val previous: YearStatsData? = null,
    val special: SpecialStats = SpecialStats(),
    /** Nur bei „Gesamt“: Kennzahlen je Jahr, neuestes zuerst. */
    val perYear: List<Pair<Int, YearStatsData>> = emptyList(),
    val years: List<Int> = emptyList(),
    val stats: YearStatsData? = null,
    val editor: EditorData = EditorData(),
    val workFilter: WorkFilter = WorkFilter.ALL,
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
    private val workFilter = MutableStateFlow(WorkFilter.ALL)
    private val person = MutableStateFlow(StatsPerson.ME)
    private val options = combine(workFilter, person) { w, who -> w to who }

    val state: StateFlow<StatsUiState> = combine(
        stays.observeAll(), visits.observeAll(), editorCatalog.data, period, options,
    ) { allStays, allVisits, editor, p, (wf, who) ->
        val me = Defaults.ME
        val partner = editor.persons.firstOrNull { !it.isMe }?.id
        // Gemeinsam: nur Einträge, bei denen beide dabei waren (aus meiner Sicht gerechnet).
        val (s, v, subject) = when {
            who == StatsPerson.PARTNER && partner != null -> Triple(allStays, allVisits, partner)
            who == StatsPerson.TOGETHER && partner != null -> Triple(
                allStays.filter { me in it.participants && partner in it.participants },
                allVisits.filter { me in it.participants && partner in it.participants },
                me,
            )
            else -> Triple(allStays, allVisits, me)
        }
        val years = ((s.map { it.startDate.year } + v.map { it.date.year }) + today.year).distinct().sortedDescending()
        val (from, to) = when (p) {
            is StatsPeriod.Year -> LocalDate.of(p.year, 1, 1) to LocalDate.of(p.year, 12, 31)
            is StatsPeriod.Month -> p.month.atDay(1) to p.month.atEndOfMonth()
            StatsPeriod.Last30 -> today.minusDays(29) to today
            is StatsPeriod.Custom -> p.from to p.to
            StatsPeriod.All -> {
                val first = (s.filter { it.precision != io.github.mario2712.locus.domain.model.DatePrecision.UNKNOWN }.map { it.startDate } +
                    v.filter { it.precision != io.github.mario2712.locus.domain.model.DatePrecision.UNKNOWN }.map { it.date }).minOrNull()
                (first ?: today) to today
            }
        }
        fun compute(a: LocalDate, b: LocalDate, allTime: Boolean = false) = YearStats.compute(
            a, b, subject, s, v, editor.placesById, editor.typesById, editor.categoriesById, editor.rank, editor.homebases, today, wf, allTime,
        )
        val stats = compute(from, to, allTime = p == StatsPeriod.All)
        val perYear = if (p == StatsPeriod.All) {
            (today.year downTo from.year).map { y -> y to compute(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)) }
        } else {
            emptyList()
        }
        // Vorzeitraum gleicher Länge; läuft der Zeitraum noch, nur bis zum selben Tag.
        val effectiveTo = minOf(to, today)
        val (prevFrom, prevTo) = when (p) {
            is StatsPeriod.Year -> from.minusYears(1) to effectiveTo.minusYears(1)
            is StatsPeriod.Month -> from.minusMonths(1) to effectiveTo.minusMonths(1)
            else -> {
                val length = java.time.temporal.ChronoUnit.DAYS.between(from, effectiveTo) + 1
                from.minusDays(length) to from.minusDays(1)
            }
        }
        val firstEntry = (allStays.map { it.startDate } + allVisits.map { it.date }).minOrNull()
        val previous = if (p != StatsPeriod.All && firstEntry != null && !prevTo.isBefore(firstEntry)) compute(prevFrom, prevTo) else null
        // Jahrestage (unabhängig vom Zeitraum, über alle Jahre)
        val resolvedAll = io.github.mario2712.locus.domain.timeline.Timeline.resolve(allStays, editor.homebases, today)
        val subjectPerson = editor.persons.firstOrNull { it.id == subject }
        fun annual(day: java.time.MonthDay, personId: io.github.mario2712.locus.domain.model.PersonId) =
            io.github.mario2712.locus.domain.stats.SpecialDays.annual(day, personId, resolvedAll, allVisits, editor.placesById, editor.homebases, editor.rank, today)
        val homeCountries = editor.homebases.placeIds.mapNotNull { id -> editor.placesById[id]?.let { ReferenceData.countableCountry(it.countryIso2)?.iso2 } }.toSet()
        val countryStats = io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator()
            .calculate(resolvedAll, allVisits, editor.placesById, editor.typesById, editor.categoriesById).byPerson[subject].orEmpty().values
        val special = SpecialStats(
            birthdays = editor.persons.mapNotNull { person ->
                person.birthDate?.let { b -> person.name to annual(java.time.MonthDay.from(b), person.id).filter { !it.date.isBefore(b) } }
            },
            newYearsEve = annual(java.time.MonthDay.of(12, 31), subject),
            christmas = annual(java.time.MonthDay.of(12, 24), subject),
            firstVisitAges = subjectPerson?.birthDate?.let { io.github.mario2712.locus.domain.stats.SpecialDays.firstVisitAges(it, countryStats, homeCountries) }.orEmpty(),
            hasBirthDate = subjectPerson?.birthDate != null,
        )
        StatsUiState(p, who, previous, special, perYear, years, stats, editor, wf)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), StatsUiState(StatsPeriod.Year(today.year)))

    fun select(value: StatsPeriod) {
        period.value = value
    }

    fun setPerson(value: StatsPerson) {
        person.value = value
    }

    fun setWorkFilter(value: WorkFilter) {
        workFilter.value = value
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenPlace: (String) -> Unit = {},
    onOpenReview: (Int) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.stats_tab_overview, R.string.stats_tab_visits, R.string.stats_tab_travel, R.string.stats_tab_work,
        R.string.stats_tab_records, R.string.stats_tab_special,
    )
    BackScaffold(stringResource(R.string.stats_title), onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodSelector(state, viewModel)
            PersonRow(state, viewModel::setPerson)
            WorkFilterRow(state.workFilter, viewModel::setWorkFilter)
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            val stats = state.stats ?: return@Column
            when (tab) {
                0 -> {
                    val reviewYear = (state.period as? StatsPeriod.Year)?.year ?: viewModel.today.year
                    androidx.compose.material3.OutlinedButton(onClick = { onOpenReview(reviewYear) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.review_open, reviewYear))
                    }
                    OverviewTab(state, stats, onOpenDay)
                }
                1 -> VisitsTab(stats, state.editor, onOpenPlace)
                2 -> TravelTab(stats, state.editor)
                3 -> WorkTab(stats, state.editor)
                4 -> RecordsTab(stats, state.editor, onOpenPlace)
                else -> SpecialTab(state.special, state.editor, onOpenDay)
            }
        }
    }
}

@Composable
private fun PersonRow(state: StatsUiState, onChange: (StatsPerson) -> Unit) {
    val partner = state.editor.persons.firstOrNull { !it.isMe } ?: return
    val me = state.editor.persons.firstOrNull { it.isMe }?.name ?: stringResource(R.string.stats_person_me)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.person == StatsPerson.ME, { onChange(StatsPerson.ME) }, label = { Text(me) })
        FilterChip(state.person == StatsPerson.PARTNER, { onChange(StatsPerson.PARTNER) }, label = { Text(partner.name) })
        FilterChip(state.person == StatsPerson.TOGETHER, { onChange(StatsPerson.TOGETHER) }, label = { Text(stringResource(R.string.stats_person_together)) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(state: StatsUiState, stats: YearStatsData, onOpenDay: (LocalDate) -> Unit) {
    val editor = state.editor
    val prev = state.previous
    SectionCard(periodLabel(state.period)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stats.recordedDays.toString(), stringResource(R.string.stats_recorded_days), prev?.let { stats.recordedDays - it.recordedDays })
            StatTile(stats.countries.size.toString(), stringResource(R.string.stats_countries), prev?.let { stats.countries.size - it.countries.size })
            StatTile(stats.newCountries.size.toString(), stringResource(R.string.stats_new_countries), prev?.let { stats.newCountries.size - it.newCountries.size })
            StatTile(stats.newCities.size.toString(), stringResource(R.string.stats_new_cities), prev?.let { stats.newCities.size - it.newCities.size })
            StatTile(stats.trips.toString(), stringResource(R.string.stats_trips), prev?.let { stats.trips - it.trips })
            StatTile(stats.nightsAway.toString(), stringResource(R.string.stats_nights_away), prev?.let { stats.nightsAway - it.nightsAway })
        }
        if (prev != null) {
            Text(stringResource(R.string.stats_compare_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    // Tage nach Art: Hauptart (jeder Tag einmal) oder alle Arten (geteilte Tage mehrfach)
    var allTypes by rememberSaveable { mutableStateOf(false) }
    val byType = if (allTypes) stats.daysWithType else stats.daysByMainType
    if (byType.isNotEmpty()) {
        SectionCard(stringResource(R.string.stats_days_by_type)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!allTypes, { allTypes = false }, label = { Text(stringResource(R.string.stats_main_type)) })
                FilterChip(allTypes, { allTypes = true }, label = { Text(stringResource(R.string.stats_all_types)) })
            }
            val max = byType.values.max().toFloat()
            byType.entries.sortedByDescending { it.value }.forEach { (typeId, days) ->
                BarRow(editor.typeName(typeId), days, max, editor.typeColor(typeId))
            }
            Text(
                stringResource(if (allTypes) R.string.stats_all_types_hint else R.string.stats_days_by_type_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    if (state.period == StatsPeriod.All) {
        // Statt einer Heatmap über viele Jahre: Kennzahlen je Jahr
        if (state.perYear.isNotEmpty()) {
            SectionCard(stringResource(R.string.stats_per_year)) {
                YearRow(stringResource(R.string.stats_year), stringResource(R.string.stats_recorded_days_short), stringResource(R.string.stats_countries),
                    stringResource(R.string.stats_trips), stringResource(R.string.stats_nights_short), header = true)
                state.perYear.forEach { (year, y) ->
                    YearRow(year.toString(), y.recordedDays.toString(), y.countries.size.toString(), y.trips.toString(), y.nightsAway.toString())
                }
            }
        }
    } else {
        SectionCard(stringResource(R.string.stats_heatmap)) {
            Heatmap(stats, editor, onOpenDay)
        }
    }
}

@Composable
private fun YearRow(c1: String, c2: String, c3: String, c4: String, c5: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(c1, Modifier.weight(0.8f), style = style)
        listOf(c2, c3, c4, c5).forEach { Text(it, Modifier.weight(1f), style = style) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisitsTab(stats: YearStatsData, editor: EditorData, onOpenPlace: (String) -> Unit) {
    val d = stats.details
    if (d.visitsByCategory.isEmpty()) {
        SectionCard(stringResource(R.string.stats_tab_visits)) { Text(stringResource(R.string.stats_no_visits)) }
        return
    }
    SectionCard(stringResource(R.string.stats_visits_by_category)) {
        val max = d.visitsByCategory.values.max().toFloat()
        d.visitsByCategory.entries.sortedByDescending { it.value }.forEach { (id, count) ->
            BarRow(editor.categoryName(id), count, max, 0xFF2A78D6)
        }
        Text(stringResource(R.string.stats_visits_by_category_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionCard(stringResource(R.string.stats_top_places)) {
        RankList(d.topPlaces, editor, onOpenPlace)
    }
    val categories = d.visitsByCategory.entries.sortedByDescending { it.value }.map { it.key }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val category = selected?.takeIf { it in categories } ?: categories.first()
    SectionCard(stringResource(R.string.stats_top_by_category)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { id ->
                FilterChip(id == category, { selected = id }, label = { Text(editor.categoryName(id)) })
            }
        }
        RankList(d.topPlacesByCategory[category].orEmpty(), editor, onOpenPlace)
    }
    if (d.newCityIds.isNotEmpty() || d.newLocationIds.isNotEmpty()) {
        SectionCard(stringResource(R.string.stats_new_discovered)) {
            if (d.newCityIds.isNotEmpty()) {
                Text(stringResource(R.string.stats_new_cities_n, d.newCityIds.size), style = MaterialTheme.typography.labelLarge)
                Text(namesList(d.newCityIds, editor), style = MaterialTheme.typography.bodySmall)
            }
            if (d.newLocationIds.isNotEmpty()) {
                Text(stringResource(R.string.stats_new_locations_n, d.newLocationIds.size), style = MaterialTheme.typography.labelLarge)
                Text(namesList(d.newLocationIds, editor), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun namesList(ids: List<String>, editor: EditorData): String {
    val shown = ids.take(25).joinToString(", ") { editor.placeName(it) }
    return if (ids.size > 25) shown + " " + stringResource(R.string.stats_more_n, ids.size - 25) else shown
}

@Composable
private fun RankList(items: List<Pair<String, Int>>, editor: EditorData, onOpenPlace: (String) -> Unit) {
    items.forEachIndexed { index, (placeId, count) ->
        Text(
            "${index + 1}. ${editor.placeName(placeId)} – ${stringResource(R.string.stats_times, count)}",
            Modifier.fillMaxWidth().clickable { onOpenPlace(placeId) }.padding(vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravelTab(stats: YearStatsData, editor: EditorData) {
    val d = stats.details
    SectionCard(stringResource(R.string.stats_tab_travel)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(formatKm(d.totalKm), stringResource(R.string.stats_km_total))
            StatTile(d.flights.toString(), stringResource(R.string.stats_flights))
            StatTile(stats.trips.toString(), stringResource(R.string.stats_trips))
        }
    }
    if (d.transportLegs.isNotEmpty()) {
        SectionCard(stringResource(R.string.stats_transport)) {
            d.transportLegs.entries.sortedByDescending { d.kmByTransport[it.key] ?: 0.0 }.forEach { (mode, legs) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${mode.emoji} ${mode.nameDe}", Modifier.weight(1f))
                    Text(stringResource(R.string.stats_legs, legs), Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                    Text(formatKm(d.kmByTransport[mode] ?: 0.0), Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.stats_km_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        SectionCard(stringResource(R.string.stats_transport)) { Text(stringResource(R.string.stats_no_transport)) }
    }
}

private fun formatKm(km: Double): String = String.format(Locale.GERMAN, "%,.0f km", km)

@Composable
private fun WorkTab(stats: YearStatsData, editor: EditorData) {
    val workMonths = stats.work.filter { it.homeofficeOnly + it.officeDays + it.businessTripDays > 0 }
    if (workMonths.isEmpty()) {
        SectionCard(stringResource(R.string.stats_work)) { Text(stringResource(R.string.stats_no_work)) }
        return
    }
    // Über mehr als ein Jahr: je Jahr zusammengefasst; über eine Jahresgrenze: Monat mit Jahr („Nov. 25“).
    val years = workMonths.map { it.month.year }.distinct()
    val byYear = workMonths.size > 13 || years.size > 2
    SectionCard(stringResource(if (byYear) R.string.stats_work_by_year else R.string.stats_work)) {
        WorkRow(stringResource(if (byYear) R.string.stats_year else R.string.stats_month), stringResource(R.string.stats_ho_only),
            stringResource(R.string.stats_office), stringResource(R.string.stats_hybrid), stringResource(R.string.stats_business), header = true)
        if (byYear) {
            workMonths.groupBy { it.month.year }.toSortedMap(compareByDescending { it }).forEach { (year, list) ->
                WorkRow(
                    year.toString(), list.sumOf { it.homeofficeOnly }.toString(), list.sumOf { it.officeDays }.toString(),
                    list.sumOf { it.hybridDays }.toString(), list.sumOf { it.businessTripDays }.toString(),
                )
            }
        } else {
            workMonths.forEach { m ->
                val name = m.month.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                WorkRow(
                    if (years.size > 1) "$name ${m.month.year % 100}" else name,
                    m.homeofficeOnly.toString(), m.officeDays.toString(), m.hybridDays.toString(), m.businessTripDays.toString(),
                )
            }
        }
        WorkRow(
            stringResource(R.string.stats_total),
            stats.work.sumOf { it.homeofficeOnly }.toString(), stats.work.sumOf { it.officeDays }.toString(),
            stats.work.sumOf { it.hybridDays }.toString(), stats.work.sumOf { it.businessTripDays }.toString(), header = true,
        )
        Text(stringResource(R.string.stats_work_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val weekday = stats.details.workByWeekday
    if (weekday.isNotEmpty()) {
        val types = weekday.values.flatMap { it.keys }.distinct().mapNotNull { editor.typesById[it] }.sortedBy { it.sortOrder }
        SectionCard(stringResource(R.string.stats_weekdays)) {
            Row(Modifier.fillMaxWidth()) {
                Text("", Modifier.weight(0.8f))
                types.forEach { Text(it.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium) }
            }
            java.time.DayOfWeek.entries.filter { it in weekday }.forEach { day ->
                Row(Modifier.fillMaxWidth()) {
                    Text(day.getDisplayName(TextStyle.SHORT, Locale.GERMAN), Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                    types.forEach { t -> Text((weekday[day]?.get(t.id) ?: 0).toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text(stringResource(R.string.stats_weekdays_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecordsTab(stats: YearStatsData, editor: EditorData, onOpenPlace: (String) -> Unit) {
    val r = stats.details.records
    SectionCard(stringResource(R.string.stats_tab_records)) {
        RecordRow("🧳", stringResource(R.string.stats_record_longest_trip),
            r.longestTrip?.let { "${io.github.mario2712.locus.ui.trips.tripTitle(it, editor)} · ${stringResource(R.string.trips_days, it.days)}" })
        RecordRow("🌍", stringResource(R.string.stats_record_most_countries),
            r.mostCountriesTrip?.let { t ->
                "${io.github.mario2712.locus.ui.trips.tripTitle(t, editor)} · " + t.countryIso2s.mapNotNull { ReferenceData.country(it)?.flag }.joinToString("")
            })
        RecordRow("📍", stringResource(R.string.stats_record_farthest),
            r.farthestPlaceId?.let { "${editor.placeName(it)} · ${formatKm(r.farthestMeters / 1000)}" },
            onClick = r.farthestPlaceId?.let { id -> { onOpenPlace(id) } })
        RecordRow("🏠", stringResource(R.string.stats_record_ho_streak),
            r.homeofficeStreak.takeIf { it > 0 }?.let { stringResource(R.string.stats_workdays, it) })
    }
}

@Composable
private fun SpecialTab(special: SpecialStats, editor: EditorData, onOpenDay: (LocalDate) -> Unit) {
    @Composable
    fun WhereList(list: List<io.github.mario2712.locus.domain.stats.DayWhere>) {
        val abroad = list.count { it.abroad }
        if (abroad > 0) Text(stringResource(R.string.special_abroad_n, abroad), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { w ->
            val flag = w.countryIso2?.let { ReferenceData.country(it)?.flag }.orEmpty()
            Row(Modifier.fillMaxWidth().clickable { onOpenDay(w.date) }.padding(vertical = 3.dp)) {
                Text(w.date.year.toString(), Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
                Text("$flag ${editor.placeName(w.placeId)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (!special.hasBirthDate) {
        SectionCard(stringResource(R.string.stats_tab_special)) { Text(stringResource(R.string.special_no_birthdate), style = MaterialTheme.typography.bodySmall) }
    }
    special.birthdays.forEach { (name, list) ->
        if (list.isNotEmpty()) SectionCard(stringResource(R.string.special_birthdays, name)) { WhereList(list) }
    }
    if (special.newYearsEve.isNotEmpty()) SectionCard(stringResource(R.string.special_nye)) { WhereList(special.newYearsEve) }
    if (special.christmas.isNotEmpty()) SectionCard(stringResource(R.string.special_christmas)) { WhereList(special.christmas) }
    val ages = special.firstVisitAges
    if (ages.isNotEmpty()) {
        SectionCard(stringResource(R.string.special_first_visit)) {
            val youngest = ages.first()
            val oldest = ages.maxBy { it.age }
            fun name(iso: String) = ReferenceData.country(iso)?.let { "${it.flag} ${it.nameDe}" } ?: iso
            Text(
                stringResource(R.string.special_first_visit_range, name(youngest.countryIso2), youngest.age, name(oldest.countryIso2), oldest.age),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ages.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(name(a.countryIso2), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.special_age, a.age) + " · " + a.firstDate.year, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(emoji: String, label: String, value: String?, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
    ) {
        Text("$emoji $label", style = MaterialTheme.typography.labelLarge)
        Text(value ?: "–", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = when (period) {
    is StatsPeriod.Year -> stringResource(R.string.stats_period_year, period.year)
    is StatsPeriod.Month -> Format.month(period.month.atDay(1))
    StatsPeriod.Last30 -> stringResource(R.string.stats_period_last30)
    StatsPeriod.All -> stringResource(R.string.stats_period_all)
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
        FilterChip(p == StatsPeriod.All, { viewModel.select(StatsPeriod.All) }, label = { Text(stringResource(R.string.stats_period_all)) })
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

/** „Ohne Arbeit“ und – nur dann – „Dienstreisen mitzählen“. */
@Composable
private fun WorkFilterRow(
    filter: WorkFilter,
    onChange: (WorkFilter) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            filter != WorkFilter.ALL,
            { onChange(if (filter == WorkFilter.ALL) WorkFilter.NO_WORK else WorkFilter.ALL) },
            label = { Text(stringResource(R.string.stats_filter_no_work)) },
        )
        if (filter != WorkFilter.ALL) {
            FilterChip(
                filter == WorkFilter.NO_WORK_EXCEPT_BUSINESS_TRIPS,
                { onChange(if (filter == WorkFilter.NO_WORK) WorkFilter.NO_WORK_EXCEPT_BUSINESS_TRIPS else WorkFilter.NO_WORK) },
                label = { Text(stringResource(R.string.stats_filter_business_trips)) },
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, delta: Int? = null) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            if (delta != null) {
                Text(
                    when {
                        delta > 0 -> "▲ $delta"
                        delta < 0 -> "▼ ${-delta}"
                        else -> "±0"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
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
