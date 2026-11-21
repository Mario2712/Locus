package io.github.mario2712.locus.ui.countries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.countrypoints.CountryRow
import io.github.mario2712.locus.domain.countrypoints.CountryStatus
import io.github.mario2712.locus.domain.model.UNKNOWN_DATE
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.places.BackScaffold
import io.github.mario2712.locus.ui.trips.tripDates
import io.github.mario2712.locus.ui.trips.tripTitle

enum class CountryFilter { VISITED_A, VISITED_B, TOGETHER, WISHES, ALL }

@Composable
fun statusLabel(status: CountryStatus, nameA: String, nameB: String): String = when (status) {
    CountryStatus.TOGETHER -> stringResource(R.string.countries_status_together)
    CountryStatus.BOTH_SEPARATELY -> stringResource(R.string.countries_status_both)
    CountryStatus.ONLY_A -> stringResource(R.string.countries_status_only, nameA)
    CountryStatus.ONLY_B -> stringResource(R.string.countries_status_only, nameB)
    CountryStatus.NONE -> ""
}

@Composable
private fun dateOrUnknown(date: java.time.LocalDate, documentedSince: Int): String =
    if (date == UNKNOWN_DATE) stringResource(R.string.countries_before_year, documentedSince) else Format.fullDate(date)

@Composable
fun CountriesScreen(onOpenCountry: (String) -> Unit, viewModel: CountriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wishes by viewModel.wishList.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(CountryFilter.VISITED_A) }
    val overview = state.overview ?: return
    val nameA = state.personA?.name ?: ""
    val nameB = state.personB?.name ?: ""

    val rows = overview.rows.filter {
        when (filter) {
            CountryFilter.VISITED_A -> it.visitedByA
            CountryFilter.VISITED_B -> it.visitedByB
            CountryFilter.TOGETHER -> it.status == CountryStatus.TOGETHER
            CountryFilter.WISHES -> it.wished
            CountryFilter.ALL -> true
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            SectionCard(stringResource(R.string.countries_title), Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.countries_summary, nameA, overview.countA, nameB, overview.countB, overview.together),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.countries_lead, nameA, overview.onlyA, nameB, overview.onlyB))
                Text(stringResource(R.string.countries_of_total, overview.total), style = MaterialTheme.typography.bodySmall)
                overview.continents.forEach { c ->
                    Column {
                        Text("${c.continent.nameDe}: ${c.visitedA}/${c.total}" + if (state.personB != null) " · $nameB ${c.visitedB}" else "",
                            style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { c.visitedA / c.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == CountryFilter.VISITED_A, { filter = CountryFilter.VISITED_A }, label = { Text(nameA) })
                if (state.personB != null) {
                    FilterChip(filter == CountryFilter.VISITED_B, { filter = CountryFilter.VISITED_B }, label = { Text(nameB) })
                    FilterChip(filter == CountryFilter.TOGETHER, { filter = CountryFilter.TOGETHER }, label = { Text(stringResource(R.string.countries_status_together)) })
                }
                FilterChip(filter == CountryFilter.WISHES, { filter = CountryFilter.WISHES }, label = { Text(stringResource(R.string.countries_wishes)) })
                FilterChip(filter == CountryFilter.ALL, { filter = CountryFilter.ALL }, label = { Text(stringResource(R.string.countries_all)) })
            }
        }
        if (rows.isEmpty()) item { Text(stringResource(R.string.countries_empty), Modifier.padding(vertical = 8.dp)) }
        rows.groupBy { it.country.continent }.forEach { (continent, list) ->
            item(key = "c-$continent") {
                Text("${continent.nameDe} (${list.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            items(list, key = { it.country.iso2 }) { row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenCountry(row.country.iso2) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(row.country.flag, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f)) {
                        Text(row.country.nameDe + if (row.wished) " ☆" else "")
                        val info = listOfNotNull(
                            statusLabel(row.status, nameA, nameB).takeIf { it.isNotEmpty() },
                            row.firstVisitA?.let {
                                if (it == UNKNOWN_DATE) stringResource(R.string.countries_before_year, state.editor.defaults.documentedSinceYear)
                                else stringResource(R.string.countries_since, it.year)
                            },
                        ).joinToString(" · ")
                        if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** Länder-Detailseite: Überblick, Städte (nach Tagen), Reisen, Wunschliste und „Datum unbekannt“. */
@Composable
fun CountryDetailScreen(
    iso2: String,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    viewModel: CountriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wishes by viewModel.wishList.collectAsStateWithLifecycle()
    val row = state.overview?.rows?.firstOrNull { it.country.iso2 == iso2 }
    val nameA = state.personA?.name ?: ""
    val nameB = state.personB?.name ?: ""
    var showAllCities by remember { mutableStateOf(false) }

    BackScaffold(row?.let { "${it.country.flag} ${it.country.nameDe}" } ?: iso2, onBack) { modifier ->
        if (row == null) return@BackScaffold
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val since = state.editor.defaults.documentedSinceYear
            SectionCard(statusLabel(row.status, nameA, nameB).ifEmpty { stringResource(R.string.countries_not_visited) }) {
                row.firstVisitA?.let { Text(stringResource(R.string.countries_first_visit, nameA, dateOrUnknown(it, since))) }
                row.firstVisitB?.let { Text(stringResource(R.string.countries_first_visit, nameB, dateOrUnknown(it, since))) }
                row.lastVisit?.takeIf { it != UNKNOWN_DATE }?.let { Text(stringResource(R.string.countries_last_visit, Format.fullDate(it))) }
                if (row.daysA > 0) Text(stringResource(R.string.countries_days, nameA, row.daysA))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.toggleWish(iso2, wishes) }) {
                        Text(stringResource(if (row.wished) R.string.countries_wish_remove else R.string.countries_wish_add))
                    }
                }
            }

            val cities = state.citiesByCountry[iso2].orEmpty()
            val unassigned = state.unassignedByCountry[iso2] ?: 0
            if (cities.isNotEmpty() || unassigned > 0) {
                SectionCard(stringResource(R.string.countries_cities, cities.size)) {
                    (if (showAllCities) cities else cities.take(8)).forEach { city ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenPlace(city.placeId) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(state.editor.placeName(city.placeId), Modifier.weight(1f))
                            Text(
                                listOfNotNull(
                                    stringResource(R.string.places_days, city.days),
                                    if (city.locations > 0) stringResource(R.string.places_locations_count, city.locations) else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                    if (!showAllCities && cities.size > 8) {
                        TextButton(onClick = { showAllCities = true }) { Text(stringResource(R.string.countries_all_cities, cities.size)) }
                    }
                    if (unassigned > 0) {
                        Text(
                            stringResource(R.string.countries_unassigned, unassigned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val trips = state.tripsByCountry[iso2].orEmpty()
            if (trips.isNotEmpty()) {
                SectionCard(stringResource(R.string.countries_trips, trips.size)) {
                    trips.forEach { trip ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenTrip(trip.id) }.padding(vertical = 8.dp)) {
                            Text("🧳 ${tripTitle(trip, state.editor)}")
                            Text(tripDates(trip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }

            SectionCard(stringResource(R.string.countries_undated_title)) {
                val undated = state.undatedByCountry[iso2].orEmpty()
                if (undated.isNotEmpty()) {
                    Text(
                        stringResource(R.string.countries_undated_entry, undated.joinToString(", ") { state.editor.participantsLabel(it.participants) }),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { viewModel.removeUndated(undated) }) { Text(stringResource(R.string.countries_undated_remove)) }
                } else {
                    Text(stringResource(R.string.countries_undated_question))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val a = state.personA
                        val b = state.personB
                        a?.let { OutlinedButton(onClick = { viewModel.markUndated(iso2, setOf(it.id)) }) { Text(it.name) } }
                        b?.let { OutlinedButton(onClick = { viewModel.markUndated(iso2, setOf(it.id)) }) { Text(it.name) } }
                        if (a != null && b != null) {
                            OutlinedButton(onClick = { viewModel.markUndated(iso2, setOf(a.id, b.id)) }) { Text(stringResource(R.string.map_both)) }
                        }
                    }
                    Text(stringResource(R.string.countries_undated_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
