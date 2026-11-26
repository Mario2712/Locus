package io.github.mario2712.locus.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.ui.editor.DatePickerDialogFor
import io.github.mario2712.locus.ui.editor.ValueButtonRow
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editPerson by remember { mutableStateOf<Person?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.profiles.forEach { profile ->
                PersonCard(profile, state, onEditBirthDate = { editPerson = profile.person })
            }
            SectionCard(stringResource(R.string.profile_documented_since_title)) {
                val year = state.editor.defaults.documentedSinceYear
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.setDocumentedSince(year - 1) }) { Text("‹") }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                    androidx.compose.material3.TextButton(onClick = { viewModel.setDocumentedSince(year + 1) }) { Text("›") }
                }
                Text(
                    stringResource(R.string.profile_documented_since_hint, year),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.profile_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editPerson?.let { person ->
        DatePickerDialogFor(
            initial = person.birthDate ?: LocalDate.of(1995, 1, 1),
            onDismiss = { editPerson = null },
            onConfirm = { viewModel.setBirthDate(person, it); editPerson = null },
        )
    }
}

@Composable
private fun PersonCard(profile: PersonProfile, state: ProfileUiState, onEditBirthDate: () -> Unit) {
    val context = LocalContext.current
    val person = profile.person
    SectionCard(person.name) {
        ValueButtonRow(
            stringResource(R.string.profile_birth_date),
            person.birthDate?.let(Format::longDate) ?: stringResource(R.string.profile_set_birth_date),
            onEditBirthDate,
        )
        Text(stringResource(R.string.profile_countries, profile.countries))

        val age = profile.age
        val days = profile.daysToBirthday
        if (age == null || days == null) {
            Text(
                stringResource(R.string.profile_no_birth_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Text(
            if (days == 0L) {
                stringResource(R.string.profile_birthday_today, age)
            } else {
                context.resources.getQuantityString(R.plurals.profile_age_next, days.toInt(), age, days.toInt())
            },
        )

        HorizontalDivider()
        Text(stringResource(R.string.profile_milestones), style = MaterialTheme.typography.labelLarge)
        profile.milestones.forEach { milestone ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_milestone_reached, milestone.age))
                    if (!milestone.reached) {
                        Text(
                            stringResource(R.string.profile_milestone_next, Format.fullDate(milestone.date)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    context.resources.getQuantityString(R.plurals.profile_countries_count, milestone.countries, milestone.countries),
                    color = if (milestone.reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.profile_birthdays), style = MaterialTheme.typography.labelLarge)
        if (profile.birthdays.isEmpty()) {
            Text(stringResource(R.string.profile_birthdays_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.profile_birthdays_abroad, profile.birthdaysAbroad))
            profile.birthdays.forEach { entry ->
                val country = entry.countryIso2?.let(ReferenceData::country)
                Text(
                    stringResource(
                        R.string.profile_birthday_entry,
                        entry.date.year,
                        entry.age,
                        listOfNotNull(country?.flag, state.editor.placeName(entry.placeId)).joinToString(" "),
                    ),
                )
            }
        }
    }
}
