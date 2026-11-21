package io.github.mario2712.locus.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.domain.countrypoints.CountryPointsCalculator
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.repository.PersonRepository
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.stats.AgeMilestone
import io.github.mario2712.locus.domain.stats.BirthdayEntry
import io.github.mario2712.locus.domain.stats.BirthdayStats
import io.github.mario2712.locus.domain.timeline.Timeline
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class PersonProfile(
    val person: Person,
    val age: Int? = null,
    val nextBirthday: LocalDate? = null,
    val daysToBirthday: Long? = null,
    val countries: Int = 0,
    val milestones: List<AgeMilestone> = emptyList(),
    val birthdays: List<BirthdayEntry> = emptyList(),
) {
    /** Geburtstage, die (zählbar) nicht im Heimatland Österreich verbracht wurden. */
    val birthdaysAbroad: Int get() = birthdays.count { it.countryIso2 != null && it.countryIso2 != "AT" }
}

data class ProfileUiState(
    val profiles: List<PersonProfile> = emptyList(),
    val editor: EditorData = EditorData(),
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    clock: Clock,
    private val persons: PersonRepository,
    private val settings: io.github.mario2712.locus.domain.repository.SettingsRepository,
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
    private val countryPoints: CountryPointsCalculator,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    val state: StateFlow<ProfileUiState> = combine(
        persons.observeAll(), stays.observeAll(), visits.observeAll(), editorCatalog.data,
    ) { people, allStays, allVisits, editor ->
        val resolved = Timeline.resolve(allStays, editor.homebase?.id, today)
        val points = countryPoints.calculate(resolved, allVisits, editor.placesById, editor.typesById, editor.categoriesById)
        val profiles = people.map { person ->
            val countries = points.byPerson[person.id].orEmpty().values
            val birth = person.birthDate
            if (birth == null) {
                PersonProfile(person, countries = countries.size)
            } else {
                val next = BirthdayStats.nextBirthday(birth, today)
                PersonProfile(
                    person = person,
                    age = BirthdayStats.age(birth, today),
                    nextBirthday = next,
                    daysToBirthday = ChronoUnit.DAYS.between(today, next),
                    countries = countries.size,
                    milestones = BirthdayStats.milestones(
                        birth, countries, today, undatedVisitedBefore = LocalDate.of(editor.defaults.documentedSinceYear, 1, 1),
                    ),
                    birthdays = BirthdayStats.birthdayPlaces(
                        birth, person.id, resolved, allVisits, editor.placesById, editor.rank, today,
                    ),
                )
            }
        }
        ProfileUiState(profiles, editor, today)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), ProfileUiState())

    fun setDocumentedSince(year: Int) = viewModelScope.launch {
        settings.setEntryDefaults(state.value.editor.defaults.copy(documentedSinceYear = year))
    }

    fun setBirthDate(person: Person, date: LocalDate?) = viewModelScope.launch {
        persons.upsert(person.copy(birthDate = date))
    }
}
