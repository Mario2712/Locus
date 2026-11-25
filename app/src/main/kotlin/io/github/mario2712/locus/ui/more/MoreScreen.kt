package io.github.mario2712.locus.ui.more

import androidx.compose.foundation.layout.Arrangement
import io.github.mario2712.locus.ui.common.SectionCard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.BuildConfig
import io.github.mario2712.locus.R

/** Ein Eintrag im Reiter „Mehr“. */
private data class MoreEntry(val icon: String, val label: Int, val onClick: () -> Unit)

/** Reiter „Mehr“: in Gruppen Auswerten · Verwalten · Daten, unten „Über Locus“. */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit = {},
) {
    val groups = listOf(
        R.string.more_group_analyze to listOf(
            MoreEntry("📊", R.string.stats_title, onOpenStats),
            MoreEntry("🗓️", R.string.timeline_title, onOpenTimeline),
            MoreEntry("🧳", R.string.trips_title, onOpenTrips),
        ),
        R.string.more_group_manage to listOf(
            MoreEntry("📍", R.string.places_title, onOpenPlaces),
            MoreEntry("👤", R.string.profile_title, onOpenProfile),
            MoreEntry("⚙️", R.string.settings_title, onOpenSettings),
        ),
        R.string.more_group_data to listOf(
            MoreEntry("💾", R.string.data_title, onOpenData),
        ),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium)
        groups.forEach { (title, entries) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Card(Modifier.fillMaxWidth()) {
                    entries.forEachIndexed { i, e ->
                        MoreRow(e.icon, stringResource(e.label), onClick = e.onClick)
                        if (i < entries.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            MoreRow("ⓘ", stringResource(R.string.about_title), stringResource(R.string.version_label, BuildConfig.VERSION_NAME), onOpenAbout)
        }
    }
}

@Composable
private fun MoreRow(icon: String, label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, Modifier.width(40.dp), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** „Über Locus“: Version, Build-Datum, Entwicklung, Datenschutz, Datenbestand, Lizenzen. */
@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: MoreViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    io.github.mario2712.locus.ui.places.BackScaffold(stringResource(R.string.about_title), onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painterResource(R.mipmap.ic_launcher), contentDescription = null,
                    modifier = Modifier.size(88.dp),
                )
                Text("Locus", style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(stringResource(R.string.about_version_title)) {
                AboutLine(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                if (BuildConfig.BUILD_DATE.isNotEmpty()) {
                    val date = runCatching { java.time.LocalDate.parse(BuildConfig.BUILD_DATE) }.getOrNull()
                    AboutLine(stringResource(R.string.about_build_date), date?.let(io.github.mario2712.locus.ui.common.Format::fullDate) ?: BuildConfig.BUILD_DATE)
                }
                AboutLine(stringResource(R.string.about_developer), "DI Mario Siller")
                Text("© 2026 DI Mario Siller", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(stringResource(R.string.about_privacy_title)) {
                Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(stringResource(R.string.data_status_title)) {
                Text(stringResource(R.string.data_status_persons, status.persons.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.data_status_stay_types, status.stayTypes), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.data_status_visit_categories, status.visitCategories), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.data_status_countries, status.countries), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.data_status_entries, status.places, status.stays), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.about_licenses_title)) {
                Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "github.com/mario2712/locus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
