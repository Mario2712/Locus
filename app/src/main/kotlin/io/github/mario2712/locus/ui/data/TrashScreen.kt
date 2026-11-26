package io.github.mario2712.locus.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mario2712.locus.R
import io.github.mario2712.locus.data.trash.TrashItem
import io.github.mario2712.locus.data.trash.TrashStore
import io.github.mario2712.locus.ui.common.EditorCatalog
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.places.BackScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trash: TrashStore,
    editorCatalog: EditorCatalog,
) : ViewModel() {
    val items: StateFlow<List<TrashItem>?> = trash.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val catalog: StateFlow<EditorData> = editorCatalog.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorData())

    fun restore(item: TrashItem, onDone: () -> Unit) = viewModelScope.launch {
        trash.restore(item)
        onDone()
    }
}

/** Papierkorb: gelöschte Stationen und Besuche der letzten 30 Tage, gruppiert nach Löschtag. */
@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val time = remember { DateTimeFormatter.ofPattern("HH:mm", Format.locale) }
    val restoredText = stringResource(R.string.trash_restored, "%s")

    BackScaffold(stringResource(R.string.trash_title), onBack) { modifier ->
        Box(modifier.fillMaxSize()) {
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    stringResource(R.string.trash_summary_empty),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val byDay = list.groupBy { Instant.ofEpochMilli(it.deletedAtMillis).atZone(zone).toLocalDate() }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Text(
                                stringResource(R.string.trash_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        byDay.forEach { (day, entries) ->
                            item(key = "h$day") {
                                Text(
                                    stringResource(R.string.trash_deleted_on, Format.fullDate(day)),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(entries, key = { it.id }) { item ->
                                val label = describe(item, catalog)
                                TrashRow(
                                    title = label,
                                    subtitle = listOf(
                                        Format.mediumDate(item.entryDate()),
                                        time.format(Instant.ofEpochMilli(item.deletedAtMillis).atZone(zone)),
                                    ).joinToString(" · "),
                                    onRestore = {
                                        viewModel.restore(item) {
                                            scope.launch { snackbar.showSnackbar(restoredText.replace("%s", label)) }
                                        }
                                    },
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun TrashItem.entryDate(): LocalDate = when (this) {
    is TrashItem.StayItem -> stay.startDate
    is TrashItem.VisitItem -> visit.date
}

@Composable
private fun describe(item: TrashItem, catalog: EditorData): String {
    val place = catalog.placesById[item.placeId]?.displayName ?: stringResource(R.string.trash_unknown_place)
    val kind = when (item) {
        is TrashItem.StayItem -> catalog.typesById[item.stay.typeId]?.name ?: stringResource(R.string.trash_stay)
        is TrashItem.VisitItem -> catalog.categoriesById[item.visit.categoryId]?.name ?: stringResource(R.string.trash_visit)
    }
    return "$place · $kind"
}

@Composable
private fun TrashRow(title: String, subtitle: String, onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRestore) { Text(stringResource(R.string.trash_restore)) }
    }
}
