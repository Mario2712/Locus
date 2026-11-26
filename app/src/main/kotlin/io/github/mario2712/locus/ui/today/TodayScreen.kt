package io.github.mario2712.locus.ui.today

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.ui.common.DayPanel
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.common.SectionCard
import io.github.mario2712.locus.domain.reference.ReferenceData
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import io.github.mario2712.locus.ui.common.TypeDot
import io.github.mario2712.locus.ui.common.showMessage
import io.github.mario2712.locus.ui.common.text
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(
    onOpenCalendar: () -> Unit,
    onOpenDay: (java.time.LocalDate) -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showMessage(it, context, viewModel::undo) }
    }

    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Format.longDate(state.day.date), style = MaterialTheme.typography.headlineSmall)
            state.day.holiday?.let {
                Text(stringResource(R.string.today_holiday, it), color = MaterialTheme.colorScheme.tertiary)
            }

            if (state.reminderEnabled && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SectionCard(stringResource(R.string.today_notifications_title)) {
                    Text(stringResource(R.string.today_notifications_text))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.today_notifications_allow))
                    }
                }
            }

            DayPanel(state.day, viewModel)

            if (state.memories.isNotEmpty()) {
                SectionCard(stringResource(R.string.today_memories)) {
                    state.memories.forEach { m ->
                        val flags = m.countries.mapNotNull { ReferenceData.country(it)?.flag }.joinToString("")
                        val where = (m.placeIds + m.visitPlaceIds).distinct().take(4).joinToString(" · ") { state.day.editor.placeName(it) }
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.fillMaxWidth().clickable { onOpenDay(m.date) }.padding(vertical = 6.dp),
                        ) {
                            Text(
                                pluralStringResource(R.plurals.today_years_ago, m.yearsAgo, m.yearsAgo) + " · " + m.date.year,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(listOf(flags, where).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.today_week)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    state.week.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, io.github.mario2712.locus.ui.common.Format.locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                val color = day.colorArgb
                                if (color != null) TypeDot(color, 16.dp) else Text("·", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                if (state.weekCounts.isNotEmpty()) {
                    Text(state.weekCounts.joinToString(" · ") { (name, count) -> "$count× $name" })
                }
                if (state.missingDays > 0) {
                    TextButton(onClick = onOpenCalendar) {
                        Text(context.resources.getQuantityString(R.plurals.today_missing, state.missingDays, state.missingDays))
                    }
                }
            }

            SectionCard(stringResource(R.string.today_country_points, state.countries.size)) {
                if (state.countries.isEmpty()) {
                    Text(stringResource(R.string.today_no_countries))
                } else {
                    Text(state.countries.joinToString(" ") { it.flag }, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
