package io.github.mario2712.locus.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.BuildConfig
import io.github.mario2712.locus.R
import io.github.mario2712.locus.ui.theme.LocusTheme

@Composable
fun MoreScreen(onOpenSettings: () -> Unit, onOpenProfile: () -> Unit, viewModel: MoreViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    MoreContent(status, onOpenSettings, onOpenProfile)
}

@Composable
private fun MoreContent(status: DataStatus, onOpenSettings: () -> Unit, onOpenProfile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.more_placeholder), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_title))
        }
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_title))
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.data_status_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.data_status_persons, status.persons.joinToString(", ")))
                Text(stringResource(R.string.data_status_stay_types, status.stayTypes))
                Text(stringResource(R.string.data_status_visit_categories, status.visitCategories))
                Text(stringResource(R.string.data_status_countries, status.countries))
                Text(stringResource(R.string.data_status_entries, status.places, status.stays))
            }
        }
        Text(
            stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MorePreview() {
    LocusTheme(dynamicColor = false) {
        MoreContent(DataStatus(persons = listOf("Ich", "Johanna"), stayTypes = 9, visitCategories = 12), onOpenSettings = {}, onOpenProfile = {})
    }
}
