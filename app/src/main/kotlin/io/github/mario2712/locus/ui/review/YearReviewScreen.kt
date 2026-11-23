package io.github.mario2712.locus.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.stats.DayWhere
import io.github.mario2712.locus.domain.stats.YearReviewData
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.trips.tripTitle
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.TextStyle
import java.util.Locale

/** Hintergründe der Story-Seiten (aus der geprüften Palette, abgedunkelt für weißen Text). */
private val pageColors = listOf(
    0xFF1F4E8C, 0xFF8C3A1F, 0xFF0F6B4A, 0xFF7A5A00, 0xFF8C3A62, 0xFF005200, 0xFF3D2F8C, 0xFF8C2B2B, 0xFF5E4020,
).map { Color(it) }

/** Eine Story-Seite: Titel, Inhalt. */
private data class StoryPage(val key: String, val content: @Composable () -> Unit)

/**
 * Jahresrückblick als Story: wischen oder tippen (links zurück, rechts weiter), jede Seite als Bild teilbar.
 * Arbeit ist standardmäßig ausgeblendet (Dienstreisen zählen trotzdem als Reisen).
 */
@Composable
fun YearReviewScreen(onClose: () -> Unit, viewModel: YearReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }

    Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        if (data == null) {
            Text(stringResource(R.string.search_loading), color = Color.White, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val pages = buildPages(data, state.editor, state.includeWork, viewModel::setIncludeWork)
        val pager = rememberPagerState { pages.size }

        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            val layer = rememberGraphicsLayer()
            layers[index] = layer
            Box(
                Modifier.fillMaxSize()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(Brush.verticalGradient(listOf(pageColors[index % pageColors.size], Color(0xFF101010))))
                    .systemBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 72.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    pages[index].content()
                    Spacer(Modifier.height(8.dp))
                    Text("Locus · ${data.year}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Fortschritt, Schließen, Teilen
        Column(Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).background(
                            if (i <= pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp),
                        ),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("✕", color = Color.White, fontSize = 20.sp) }
                TextButton(onClick = {
                    val layer = layers[pager.currentPage] ?: return@TextButton
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap, "locus-rueckblick-${data.year}-${pager.currentPage + 1}.png")
                    }
                }) { Text(stringResource(R.string.review_share), color = Color.White) }
            }
        }
    }
}

private fun shareImage(context: Context, bitmap: Bitmap, name: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
private fun Big(text: String) = Text(text, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)

@Composable
private fun Title(text: String) = Text(text, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(text: String) = Text(text, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)

@Composable
private fun Small(text: String) = Text(text, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)

private fun flags(isos: List<String>) = isos.mapNotNull { ReferenceData.country(it)?.flag }.joinToString(" ")

@Composable
private fun whereText(where: DayWhere, editor: EditorData): String =
    listOfNotNull(where.countryIso2?.let { ReferenceData.country(it)?.flag }, editor.placeName(where.placeId)).joinToString(" ")

@Composable
private fun buildPages(data: YearReviewData, editor: EditorData, includeWork: Boolean, onWork: (Boolean) -> Unit): List<StoryPage> {
    val s = data.stats
    val d = s.details
    val partner = editor.persons.firstOrNull { !it.isMe }
    val pages = mutableListOf<StoryPage>()

    pages += StoryPage("intro") {
        Title(stringResource(R.string.review_intro_title, data.year))
        Big(s.recordedDays.toString())
        Body(stringResource(R.string.review_recorded_days))
        FilterChip(
            includeWork, { onWork(!includeWork) },
            label = { Text(stringResource(R.string.review_include_work)) },
            colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedLabelColor = Color.Black, selectedContainerColor = Color.White),
        )
    }
    pages += StoryPage("countries") {
        Title(stringResource(R.string.review_countries_title))
        Big(s.countries.size.toString())
        Text(flags(s.countries), fontSize = 32.sp, lineHeight = 40.sp)
        if (s.newCountries.isNotEmpty()) {
            Body(stringResource(R.string.review_new_countries, s.newCountries.mapNotNull { ReferenceData.country(it) }.joinToString(", ") { "${it.flag} ${it.nameDe}" }))
        }
    }
    pages += StoryPage("travel") {
        Title(stringResource(R.string.review_travel_title))
        Body(pluralStringResource(R.plurals.review_trips, s.trips, s.trips))
        Body(pluralStringResource(R.plurals.review_nights, s.nightsAway, s.nightsAway))
        if (d.totalKm > 0) Big(String.format(Locale.GERMAN, "%,.0f km", d.totalKm))
        if (d.flights > 0) Body(pluralStringResource(R.plurals.review_flights, d.flights, d.flights))
    }
    d.records.longestTrip?.let { trip ->
        pages += StoryPage("trip") {
            Title(stringResource(R.string.review_trip_title))
            Big(tripTitle(trip, editor))
            Body(pluralStringResource(R.plurals.review_days, trip.days, trip.days) + "  " + flags(trip.countryIso2s))
            d.records.farthestPlaceId?.let {
                Small(stringResource(R.string.review_farthest, editor.placeName(it), String.format(Locale.GERMAN, "%,.0f km", d.records.farthestMeters / 1000)))
            }
        }
    }
    if (s.topCities.isNotEmpty()) {
        pages += StoryPage("top") {
            Title(stringResource(R.string.review_top_title))
            s.topCities.forEachIndexed { i, (id, days) -> Body("${i + 1}. ${editor.placeName(id)} · ${pluralStringResource(R.plurals.review_days, days, days)}") }
            if (d.topPlaces.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Small(stringResource(R.string.review_top_places))
                d.topPlaces.take(3).forEach { (id, n) -> Small("• ${editor.placeName(id)} (${n}×)") }
            }
        }
    }
    if (d.newCityIds.isNotEmpty()) {
        pages += StoryPage("new") {
            Title(stringResource(R.string.review_new_title))
            Big(d.newCityIds.size.toString())
            Body(stringResource(R.string.review_new_cities))
            Small(d.newCityIds.take(10).joinToString(", ") { editor.placeName(it) })
        }
    }
    pages += StoryPage("months") {
        Title(stringResource(R.string.review_months_title))
        data.months.forEach { m ->
            val text = (listOf(flags(m.countries)) + m.trips.map { tripTitle(it, editor) }).filter { it.isNotBlank() }.joinToString(" · ")
            Row {
                Text(m.month.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(end = 12.dp))
                Text(text.ifBlank { "–" }, color = Color.White, maxLines = 1)
            }
        }
    }
    if (partner != null && data.togetherDays > 0) {
        pages += StoryPage("together") {
            Title(stringResource(R.string.review_together_title, partner.name))
            Big(data.togetherDays.toString())
            Body(stringResource(R.string.review_together_days))
            if (data.togetherTrips > 0) Body(pluralStringResource(R.plurals.review_trips, data.togetherTrips, data.togetherTrips))
        }
    }
    if (data.birthday != null || data.newYearsEve != null) {
        pages += StoryPage("special") {
            Title(stringResource(R.string.review_special_title))
            data.birthday?.let { Body(stringResource(R.string.review_birthday, whereText(it, editor))) }
            data.newYearsEve?.let { Body(stringResource(R.string.review_nye, whereText(it, editor))) }
        }
    }
    if (includeWork) {
        pages += StoryPage("work") {
            Title(stringResource(R.string.review_work_title))
            Body(stringResource(R.string.review_work_line, s.work.sumOf { it.homeofficeOnly }, s.work.sumOf { it.officeDays }, s.work.sumOf { it.businessTripDays }))
        }
    }
    data.previous?.let { p ->
        pages += StoryPage("compare") {
            Title(stringResource(R.string.review_compare_title, data.year - 1))
            fun delta(now: Int, before: Int) = (now - before).let { if (it > 0) "▲ $it" else if (it < 0) "▼ ${-it}" else "±0" }
            Body("${stringResource(R.string.stats_countries)}: ${s.countries.size} (${delta(s.countries.size, p.countries.size)})")
            Body("${stringResource(R.string.stats_trips)}: ${s.trips} (${delta(s.trips, p.trips)})")
            Body("${stringResource(R.string.stats_nights_away)}: ${s.nightsAway} (${delta(s.nightsAway, p.nightsAway)})")
            Body("${stringResource(R.string.stats_new_cities)}: ${s.newCities.size} (${delta(s.newCities.size, p.newCities.size)})")
        }
    }
    return pages
}
