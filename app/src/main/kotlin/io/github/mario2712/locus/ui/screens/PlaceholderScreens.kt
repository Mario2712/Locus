package io.github.mario2712.locus.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mario2712.locus.BuildConfig
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.util.flagEmoji
import io.github.mario2712.locus.ui.theme.LocusTheme

// Platzhalter für M0 – werden in den folgenden Meilensteinen durch echte Screens ersetzt.

@Composable
fun TodayScreen() = Placeholder(R.string.today_title, R.string.today_placeholder) {
    Text(
        stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun CalendarScreen() = Placeholder(R.string.calendar_title, R.string.calendar_placeholder)

@Composable
fun MapScreen() = Placeholder(R.string.map_title, R.string.map_placeholder)

@Composable
fun CountriesScreen() = Placeholder(R.string.countries_title, R.string.countries_placeholder) {
    Text(
        listOf("AT", "DE", "SI", "IT", "HR").joinToString("  ") { flagEmoji(it) },
        style = MaterialTheme.typography.displaySmall,
    )
}

@Composable
fun MoreScreen() = Placeholder(R.string.more_title, R.string.more_placeholder)

@Composable
private fun Placeholder(
    @StringRes title: Int,
    @StringRes body: Int,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        extra()
    }
}

@Preview(showBackground = true)
@Composable
private fun CountriesPreview() {
    LocusTheme(dynamicColor = false) { CountriesScreen() }
}
